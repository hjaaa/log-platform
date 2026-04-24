package com.hj.log.sdk.truncation;

import com.hj.log.sdk.model.LogEvent;

/**
 * SDK 入口字段截断（detailed-design §6.6）。
 *
 * <p>为什么在入口截：buffer 内一律已截断，下游序列化 / 重试 / replay 都无需重复处理。
 *
 * <p>度量单位：{@link String#length()}（UTF-16 code units），与服务端 §2.3.1、§7.1 阈值对齐。
 *
 * <p>截断形态：超长字段尾部追加 {@value #TRUNCATED_SUFFIX}（长度 {@value #SUFFIX_LENGTH}
 * 字符），且整串长度严格 ≤ 阈值——因此保留的原文部分为 {@code threshold - suffixLength}。
 */
public final class FieldTruncator {

    public static final String TRUNCATED_SUFFIX = "…[truncated]";
    public static final int SUFFIX_LENGTH = TRUNCATED_SUFFIX.length();

    private final int messageLimit;
    private final int stackTraceLimit;
    private final TruncationCounter counter;

    public FieldTruncator(int messageLimit, int stackTraceLimit, TruncationCounter counter) {
        if (messageLimit <= SUFFIX_LENGTH) {
            throw new IllegalArgumentException(
                    "messageLimit must be > " + SUFFIX_LENGTH + ", got " + messageLimit);
        }
        if (stackTraceLimit <= SUFFIX_LENGTH) {
            throw new IllegalArgumentException(
                    "stackTraceLimit must be > " + SUFFIX_LENGTH + ", got " + stackTraceLimit);
        }
        this.messageLimit = messageLimit;
        this.stackTraceLimit = stackTraceLimit;
        this.counter = counter;
    }

    /** 入口统一截断：事件内 message / stackTrace 各自超阈值则截，其余字段原样透传。 */
    public LogEvent apply(LogEvent event) {
        LogEvent out = event;
        String msg = event.message();
        if (msg != null && msg.length() > messageLimit) {
            out = out.withMessage(truncate(msg, messageLimit));
            counter.increment();
        }
        String stack = event.stackTrace();
        if (stack != null && stack.length() > stackTraceLimit) {
            out = out.withStackTrace(truncate(stack, stackTraceLimit));
            counter.increment();
        }
        return out;
    }

    static String truncate(String s, int limit) {
        int keep = limit - SUFFIX_LENGTH;
        return s.substring(0, keep) + TRUNCATED_SUFFIX;
    }

    /** 计数口子，实际实现由 {@link com.hj.log.sdk.metrics.SdkMetrics} 提供。 */
    public interface TruncationCounter {
        void increment();
    }
}
