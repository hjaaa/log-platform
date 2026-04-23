package com.hj.log.query.dto;

import com.hj.log.common.enums.LogKind;
import com.hj.log.common.enums.LogLevel;
import java.time.Instant;
import java.util.List;

/**
 * {@code GET /api/v1/logs/search} 的查询条件。来源于 query string，由 Controller 反序列化与归一化。
 *
 * <p>校验规则在 Service 层：appCode 与 traceId 至少传一个；keyword 不允许 % 前缀；pageSize 上限。
 */
public class LogSearchCriteria {

    private String appCode;
    private List<LogLevel> levels;
    private LogKind kind;
    private Instant from;
    private Instant to;
    private String keyword;
    private String traceId;
    private String requestId;
    private String cursor;
    private Integer pageSize;
    private List<String> fields;

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public List<LogLevel> getLevels() {
        return levels;
    }

    public void setLevels(List<LogLevel> levels) {
        this.levels = levels;
    }

    public LogKind getKind() {
        return kind;
    }

    public void setKind(LogKind kind) {
        this.kind = kind;
    }

    public Instant getFrom() {
        return from;
    }

    public void setFrom(Instant from) {
        this.from = from;
    }

    public Instant getTo() {
        return to;
    }

    public void setTo(Instant to) {
        this.to = to;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }
}
