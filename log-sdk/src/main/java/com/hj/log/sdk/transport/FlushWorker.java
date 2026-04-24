package com.hj.log.sdk.transport;

import com.hj.log.sdk.buffer.LogEventBuffer;
import com.hj.log.sdk.fallback.FallbackWriter;
import com.hj.log.sdk.model.LogEvent;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消费 {@link LogEventBuffer}，按"满 N 条 OR 距上次 flush 达到 intervalMs"触发一次整批发送
 * （detailed-design §6.2）。独立 daemon 线程 "log-sdk-flush" 运行。
 */
public class FlushWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(FlushWorker.class);
    private static final long LOOP_SLICE_MS = 50L;

    private final LogEventBuffer buffer;
    private final RetryingSender sender;
    private final FallbackWriter fallback;
    private final int batchSize;
    private final long flushIntervalMs;

    private volatile boolean running = true;
    private volatile long lastFlushAt = System.currentTimeMillis();

    public FlushWorker(
            LogEventBuffer buffer,
            RetryingSender sender,
            FallbackWriter fallback,
            int batchSize,
            long flushIntervalMs) {
        this.buffer = buffer;
        this.sender = sender;
        this.fallback = fallback;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
    }

    @Override
    public void run() {
        log.info("[log-sdk] flush worker started (batchSize={}, intervalMs={})",
                batchSize, flushIntervalMs);
        while (running) {
            try {
                long now = System.currentTimeMillis();
                boolean sizeTrigger = buffer.size() >= batchSize;
                boolean timeTrigger = (now - lastFlushAt) >= flushIntervalMs && !buffer.isEmpty();
                if (sizeTrigger || timeTrigger) {
                    drainAndSendAll();
                }
            } catch (Throwable t) {
                // 防御：循环体内任何异常都不许把 worker 线程干崩
                log.error("[log-sdk] flush loop iteration failed", t);
            }
            if (!running) {
                break;
            }
            try {
                Thread.sleep(LOOP_SLICE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        // 收尾：把剩下的事件处理掉，不能丢
        try {
            drainAndSendAll();
        } catch (Throwable t) {
            log.error("[log-sdk] flush worker shutdown drain failed", t);
        }
        log.info("[log-sdk] flush worker stopped");
    }

    /**
     * 同步把 buffer 抽干并逐批发送。为 {@code flushNow()} 测试辅助以及 shutdown drain 复用。
     *
     * <p>synchronized 保证 worker 线程与外部 {@code flushNow} 不会并发进入，避免乱序批次。
     */
    public synchronized void drainAndSendAll() {
        List<LogEvent> batch = new ArrayList<>(batchSize);
        while (true) {
            batch.clear();
            int n = buffer.drainTo(batch, batchSize);
            if (n <= 0) {
                break;
            }
            dispatchOne(new ArrayList<>(batch));
        }
        lastFlushAt = System.currentTimeMillis();
    }

    private void dispatchOne(List<LogEvent> batch) {
        RetryingSender.Result result;
        try {
            result = sender.send(batch);
        } catch (RuntimeException e) {
            log.error("[log-sdk] unexpected sender error, falling back {} events", batch.size(), e);
            fallback.write(batch);
            return;
        }
        switch (result) {
            case SENT, DROPPED -> {
                // SENT: 服务端已收；DROPPED: 4xx 已记 WARN，上层直接丢
            }
            case EXHAUSTED, INTERRUPTED -> fallback.write(batch);
        }
    }

    public void stop() {
        running = false;
    }
}
