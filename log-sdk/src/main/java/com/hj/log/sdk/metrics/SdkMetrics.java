package com.hj.log.sdk.metrics;

import com.hj.log.sdk.truncation.FieldTruncator;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SDK 自身指标（detailed-design §6.3 / §6.6）。
 *
 * <p>两个计数器：
 * <ul>
 *   <li>{@code droppedCount} — buffer 满 {@code offer} 失败</li>
 *   <li>{@code truncatedCount} — 入口字段超阈值被截断</li>
 * </ul>
 *
 * <p>打印策略：每累计到任何一次非零增量时，60s 窗口最多 WARN 一次，避免日志风暴。
 * 打印后当前窗口快照复位：只打"自上次打印以来"的增量，累计值另外可读。
 */
public final class SdkMetrics implements FieldTruncator.TruncationCounter {

    private static final Logger log = LoggerFactory.getLogger(SdkMetrics.class);
    private static final long REPORT_INTERVAL_MS = 60_000L;

    private final AtomicLong droppedTotal = new AtomicLong();
    private final AtomicLong truncatedTotal = new AtomicLong();
    private volatile long lastDroppedReported = 0L;
    private volatile long lastTruncatedReported = 0L;
    private volatile long nextDroppedLogAtMs = 0L;
    private volatile long nextTruncatedLogAtMs = 0L;

    public void incrementDropped() {
        droppedTotal.incrementAndGet();
        reportDroppedIfDue(System.currentTimeMillis());
    }

    @Override
    public void increment() {
        truncatedTotal.incrementAndGet();
        reportTruncatedIfDue(System.currentTimeMillis());
    }

    public long droppedCount() {
        return droppedTotal.get();
    }

    public long truncatedCount() {
        return truncatedTotal.get();
    }

    private synchronized void reportDroppedIfDue(long nowMs) {
        if (nowMs < nextDroppedLogAtMs) {
            return;
        }
        long total = droppedTotal.get();
        long delta = total - lastDroppedReported;
        if (delta <= 0) {
            return;
        }
        log.warn(
                "[log-sdk] buffer full, dropped events: +{} in last {}ms (total={})",
                delta,
                REPORT_INTERVAL_MS,
                total);
        lastDroppedReported = total;
        nextDroppedLogAtMs = nowMs + REPORT_INTERVAL_MS;
    }

    private synchronized void reportTruncatedIfDue(long nowMs) {
        if (nowMs < nextTruncatedLogAtMs) {
            return;
        }
        long total = truncatedTotal.get();
        long delta = total - lastTruncatedReported;
        if (delta <= 0) {
            return;
        }
        log.warn(
                "[log-sdk] truncated fields: +{} in last {}ms (total={})",
                delta,
                REPORT_INTERVAL_MS,
                total);
        lastTruncatedReported = total;
        nextTruncatedLogAtMs = nowMs + REPORT_INTERVAL_MS;
    }
}
