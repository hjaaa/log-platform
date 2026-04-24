package com.hj.log.sdk.model;

import java.time.Instant;

/**
 * 业务线程一次 {@code logger.xxx()} 调用落成的单条事件。
 *
 * <p>字段与服务端 {@code POST /api/v1/logs} 单条请求体对齐（detailed-design §2.3.1）；
 * 不含 {@code appId} / {@code serverTs}，由服务端补齐。
 *
 * <p>record 原因：
 * <ul>
 *   <li>业务线程仅调用一次；后续全是消费（异步 flush / 序列化 / 落盘），不可变更天然安全</li>
 *   <li>Jackson 已内建 record 支持，无需 getter/setter 样板</li>
 * </ul>
 */
public record LogEvent(
        String logKind,
        String level,
        String message,
        String stackTrace,
        String contextData,
        String traceId,
        String spanId,
        String requestId,
        String loggerName,
        String threadName,
        String sourceHost,
        Instant clientTs) {

    public LogEvent withMessage(String newMessage) {
        return new LogEvent(
                logKind,
                level,
                newMessage,
                stackTrace,
                contextData,
                traceId,
                spanId,
                requestId,
                loggerName,
                threadName,
                sourceHost,
                clientTs);
    }

    public LogEvent withStackTrace(String newStackTrace) {
        return new LogEvent(
                logKind,
                level,
                message,
                newStackTrace,
                contextData,
                traceId,
                spanId,
                requestId,
                loggerName,
                threadName,
                sourceHost,
                clientTs);
    }
}
