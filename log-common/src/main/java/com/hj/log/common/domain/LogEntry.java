package com.hj.log.common.domain;

import com.hj.log.common.base.BaseEntity;
import com.hj.log.common.enums.LogKind;
import com.hj.log.common.enums.LogLevel;
import java.time.Instant;

/**
 * 对应表 {@code logs}。
 *
 * <p>{@code logs} 表无 {@code updated_at} 列（一次写入永不更新），{@link BaseEntity#getUpdatedAt()}
 * 始终为 {@code null}。{@code clientTs} 由 SDK 填、{@code serverTs} 由 ingestion 服务填。
 */
public class LogEntry extends BaseEntity {

    private Long appId;
    private LogKind logKind;
    private LogLevel level;
    private String message;
    private String stackTrace;
    private String contextData;
    private String traceId;
    private String spanId;
    private String requestId;
    private String loggerName;
    private String threadName;
    private String sourceHost;
    private Instant clientTs;
    private Instant serverTs;

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public LogKind getLogKind() {
        return logKind;
    }

    public void setLogKind(LogKind logKind) {
        this.logKind = logKind;
    }

    public LogLevel getLevel() {
        return level;
    }

    public void setLevel(LogLevel level) {
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

    public Instant getServerTs() {
        return serverTs;
    }

    public void setServerTs(Instant serverTs) {
        this.serverTs = serverTs;
    }
}
