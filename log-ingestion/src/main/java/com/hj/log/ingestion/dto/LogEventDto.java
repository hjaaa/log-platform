package com.hj.log.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * SDK 上报的单条日志事件（与 detailed-design §2.3.1 请求体字段一致）。
 *
 * <p>字段全部可读写：日志写入是高频路径，避免反射与构造器开销。
 * 校验由 {@code LogIngestionService} 手动执行——不合法的单条入 rejected[]，不抛 4xx。
 */
public class LogEventDto {

    private String logKind;
    private String level;
    private String message;
    private String stackTrace;
    private String contextData;
    private String traceId;
    private String spanId;
    private String requestId;
    private String loggerName;
    private String threadName;
    private String sourceHost;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant clientTs;

    public String getLogKind() {
        return logKind;
    }

    public void setLogKind(String logKind) {
        this.logKind = logKind;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getContextData() {
        return contextData;
    }

    public void setContextData(String contextData) {
        this.contextData = contextData;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public String getSourceHost() {
        return sourceHost;
    }

    public void setSourceHost(String sourceHost) {
        this.sourceHost = sourceHost;
    }

    public Instant getClientTs() {
        return clientTs;
    }

    public void setClientTs(Instant clientTs) {
        this.clientTs = clientTs;
    }
}
