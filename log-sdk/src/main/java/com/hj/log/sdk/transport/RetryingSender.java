package com.hj.log.sdk.transport;

import com.hj.log.sdk.model.LogEvent;
import java.util.List;

/**
 * 在 {@link HttpTransport} 之上叠加"指数退避重试"的发送器（detailed-design §6.2 / §6.3）。
 *
 * <p>重试语义：
 * <ul>
 *   <li>首次 + 最多 {@code maxRetries} 次重试（默认 3）——与测试矩阵 "500 ×4 → fallback" 吻合</li>
 *   <li>每次重试前 sleep {@code min(4000, 1000 << attempt)} ms</li>
 *   <li>遇 4xx 立即返回 {@link Result#DROPPED}，不重试（服务端已明确拒收）</li>
 * </ul>
 *
 * <p>线程中断：sleep 被中断时返回 {@link Result#INTERRUPTED}，调用方应放弃当前批；
 * 业务语义上由上层（FlushWorker/ReplayWorker）决定是否把该批转 fallback。
 */
public class RetryingSender {

    public enum Result {
        /** 发送成功，上层可从队列 / 文件移除该批。 */
        SENT,
        /** 4xx 被拒；上层应 drop 不再重试；若来自 replay 亦删文件。 */
        DROPPED,
        /** 瞬时错误重试耗尽；上层决定转 fallback（flush 路径）或留待下次（replay 路径）。 */
        EXHAUSTED,
        /** sleep 被中断；通常发生在 shutdown，调用方直接转 fallback。 */
        INTERRUPTED
    }

    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    static final Sleeper DEFAULT_SLEEPER = Thread::sleep;

    public static Sleeper defaultSleeper() {
        return DEFAULT_SLEEPER;
    }

    private final HttpTransport transport;
    private final int maxRetries;
    private final Sleeper sleeper;

    public RetryingSender(HttpTransport transport, int maxRetries, Sleeper sleeper) {
        this.transport = transport;
        this.maxRetries = maxRetries;
        this.sleeper = sleeper;
    }

    public Result send(List<LogEvent> batch) {
        int attempt = 0;
        while (true) {
            TransportOutcome outcome = transport.send(batch);
            if (outcome == TransportOutcome.SUCCESS) {
                return Result.SENT;
            }
            if (outcome == TransportOutcome.CLIENT_ERROR) {
                return Result.DROPPED;
            }
            // TRANSIENT_ERROR
            if (attempt >= maxRetries) {
                return Result.EXHAUSTED;
            }
            long backoffMs = Math.min(4_000L, 1_000L << attempt);
            try {
                sleeper.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.INTERRUPTED;
            }
            attempt++;
        }
    }
}
