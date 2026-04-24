package com.hj.log.sdk.buffer;

import com.hj.log.sdk.metrics.SdkMetrics;
import com.hj.log.sdk.model.LogEvent;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * SDK 内部事件缓冲（detailed-design §6.2 / §6.3）。
 *
 * <p>核心语义：
 * <ul>
 *   <li>业务线程 {@link #offer(LogEvent)} 永不阻塞——容量满即丢弃并计数</li>
 *   <li>消费者（FlushWorker）走 {@link #poll(long)} 或 {@link #drainTo(List, int)}</li>
 * </ul>
 *
 * <p>实现选择 {@link LinkedBlockingDeque}（而非 ArrayBlockingQueue）便于 shutdown 阶段
 * {@code drainTo(Integer.MAX_VALUE)} 一次拉干，不用分批。
 */
public final class LogEventBuffer {

    private final BlockingDeque<LogEvent> queue;
    private final SdkMetrics metrics;

    public LogEventBuffer(int capacity, SdkMetrics metrics) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.queue = new LinkedBlockingDeque<>(capacity);
        this.metrics = metrics;
    }

    /** 非阻塞入队；容量满返回 false 并在 {@link SdkMetrics} 记录。 */
    public boolean offer(LogEvent event) {
        boolean accepted = queue.offer(event);
        if (!accepted) {
            metrics.incrementDropped();
        }
        return accepted;
    }

    public LogEvent poll(long timeoutMs) throws InterruptedException {
        if (timeoutMs <= 0L) {
            return queue.poll();
        }
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public int drainTo(List<LogEvent> out, int maxElements) {
        return queue.drainTo(out, maxElements);
    }

    public int drainAll(List<LogEvent> out) {
        return queue.drainTo(out);
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
