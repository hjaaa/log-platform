package com.hj.log.sdk.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.sdk.buffer.LogEventBuffer;
import com.hj.log.sdk.config.LogSdkProperties;
import com.hj.log.sdk.fallback.FallbackReader;
import com.hj.log.sdk.fallback.FallbackWriter;
import com.hj.log.sdk.fallback.ReplayWorker;
import com.hj.log.sdk.metrics.SdkMetrics;
import com.hj.log.sdk.model.LogEvent;
import com.hj.log.sdk.transport.FlushWorker;
import com.hj.log.sdk.transport.HttpTransport;
import com.hj.log.sdk.transport.RetryingSender;
import com.hj.log.sdk.transport.SdkJsonMapper;
import com.hj.log.sdk.truncation.FieldTruncator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SDK 对外入口（detailed-design §6.1）。
 *
 * <p>三条铁律：
 * <ol>
 *   <li>{@link #write(LogEvent)} 非阻塞：入队失败即丢弃并计数，绝不抛异常</li>
 *   <li>HTTP 发送异步：业务线程仅摸 buffer，不触网络 IO</li>
 *   <li>失败不丢业务路径：重试耗尽转 fallback 文件；下周期 replay</li>
 * </ol>
 *
 * <p>生命周期：{@link #create(LogSdkProperties)} 启动两个 daemon 线程 + 注册 shutdown hook；
 * {@link #close()} 停止线程、把剩余 buffer 写 fallback、从 hook 注销。{@code close()} 幂等。
 */
public final class LogClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LogClient.class);

    private final LogSdkProperties props;
    private final LogEventBuffer buffer;
    private final SdkMetrics metrics;
    private final FieldTruncator truncator;
    private final FlushWorker flushWorker;
    private final ReplayWorker replayWorker;
    private final FallbackWriter fallbackWriter;
    private final Thread flushThread;
    private final Thread replayThread;

    private volatile Thread shutdownHook; // 构造完成后由 create() 设置一次
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private LogClient(
            LogSdkProperties props,
            LogEventBuffer buffer,
            SdkMetrics metrics,
            FieldTruncator truncator,
            FlushWorker flushWorker,
            ReplayWorker replayWorker,
            FallbackWriter fallbackWriter,
            Thread flushThread,
            Thread replayThread) {
        this.props = props;
        this.buffer = buffer;
        this.metrics = metrics;
        this.truncator = truncator;
        this.flushWorker = flushWorker;
        this.replayWorker = replayWorker;
        this.fallbackWriter = fallbackWriter;
        this.flushThread = flushThread;
        this.replayThread = replayThread;
    }

    /**
     * 装配并启动一个 LogClient 实例。宿主应用通常在启动期调用一次；
     * 实例应被复用，不要为每条日志重建。
     */
    public static LogClient create(LogSdkProperties props) {
        LogClient client = assemble(props, RetryingSender.defaultSleeper());
        Thread hook = new Thread(client::closeFromShutdownHook, "log-sdk-shutdown");
        client.shutdownHook = hook;
        try {
            Runtime.getRuntime().addShutdownHook(hook);
        } catch (IllegalStateException e) {
            // JVM 已在 shutdown 中——SDK 不再等，立即返回可用实例（用户会在逻辑完成后自己 close）
            log.warn("[log-sdk] JVM is shutting down; skip shutdown hook registration");
        }
        return client;
    }

    /** 仅供测试：跳过 shutdown hook 注册，避免反复创建/销毁时 JVM hook 积压。 */
    static LogClient createWithoutShutdownHook(LogSdkProperties props) {
        return assemble(props, RetryingSender.defaultSleeper());
    }

    /** 仅供测试：注入可替换 Sleeper，避免真实 1s/2s/4s 退避让 UT 变慢。 */
    static LogClient createForTest(LogSdkProperties props, RetryingSender.Sleeper sleeper) {
        return assemble(props, sleeper);
    }

    private static LogClient assemble(LogSdkProperties props, RetryingSender.Sleeper sleeper) {
        ObjectMapper mapper = SdkJsonMapper.create();
        SdkMetrics metrics = new SdkMetrics();
        FieldTruncator truncator =
                new FieldTruncator(
                        props.truncateMessageLength(),
                        props.truncateStackTraceLength(),
                        metrics);
        LogEventBuffer buffer = new LogEventBuffer(props.bufferCapacity(), metrics);

        HttpTransport transport = new HttpTransport(props, mapper);
        RetryingSender sender =
                new RetryingSender(transport, props.httpRetryMaxAttempts(), sleeper);

        FallbackWriter fallbackWriter =
                new FallbackWriter(props.fallbackDir(), props.fallbackMaxDiskMb(), mapper);
        FallbackReader fallbackReader = new FallbackReader(props.fallbackDir(), mapper);

        FlushWorker flushWorker =
                new FlushWorker(
                        buffer,
                        sender,
                        fallbackWriter,
                        props.flushBatchSize(),
                        props.flushIntervalMs());
        ReplayWorker replayWorker =
                new ReplayWorker(
                        fallbackReader,
                        sender,
                        props.replayIntervalMs(),
                        props.flushBatchSize());

        Thread flushThread = new Thread(flushWorker, "log-sdk-flush");
        flushThread.setDaemon(true);
        Thread replayThread = new Thread(replayWorker, "log-sdk-replay");
        replayThread.setDaemon(true);

        flushThread.start();
        replayThread.start();

        return new LogClient(
                props,
                buffer,
                metrics,
                truncator,
                flushWorker,
                replayWorker,
                fallbackWriter,
                flushThread,
                replayThread);
    }

    /**
     * 业务线程入口：截断 → 入 buffer。非阻塞、不抛异常。
     *
     * @param event 不可为 null。{@code null} 事件会被静默丢弃并记 ERROR（编程错误）
     */
    public void write(LogEvent event) {
        if (closed.get()) {
            // 已关闭：不接新事件（避免堆积后无人消费）
            return;
        }
        if (event == null) {
            log.error("[log-sdk] write(null) ignored (programming error)");
            return;
        }
        try {
            LogEvent truncated = truncator.apply(event);
            buffer.offer(truncated);
        } catch (Throwable t) {
            // 最终兜底：业务线程看到任何异常都要吞掉
            log.error("[log-sdk] write threw unexpectedly; swallowed", t);
        }
    }

    /** 测试辅助：同步把 buffer 抽干并发送（不等 replay 文件）。 */
    public void flushNow() {
        flushWorker.drainAndSendAll();
    }

    /** 快照指标。线程安全；可在外部 probe 端点暴露。 */
    public SdkMetrics metrics() {
        return metrics;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Thread hook = this.shutdownHook;
        if (hook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignore) {
                // JVM 正在 shutdown 中
            }
        }
        shutdown();
    }

    private void closeFromShutdownHook() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        shutdown();
    }

    private void shutdown() {
        log.info("[log-sdk] shutdown begin (bufferSize={})", buffer.size());
        flushWorker.stop();
        replayWorker.stop();
        replayThread.interrupt();

        long waitMs = props.shutdownWaitMs();
        try {
            flushThread.join(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (flushThread.isAlive()) {
            log.warn(
                    "[log-sdk] flush thread did not terminate within {} ms; draining remaining {} events to fallback",
                    waitMs,
                    buffer.size());
            drainRemainingToFallback();
        } else if (!buffer.isEmpty()) {
            drainRemainingToFallback();
        }
        log.info(
                "[log-sdk] shutdown complete (remaining buffer={}, dropped={}, truncated={})",
                buffer.size(),
                metrics.droppedCount(),
                metrics.truncatedCount());
    }

    private void drainRemainingToFallback() {
        List<LogEvent> remaining = new ArrayList<>();
        buffer.drainAll(remaining);
        if (!remaining.isEmpty()) {
            fallbackWriter.write(remaining);
        }
    }
}
