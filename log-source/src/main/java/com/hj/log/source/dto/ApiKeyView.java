package com.hj.log.source.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hj.log.common.domain.ApiKey;
import java.time.Instant;

/**
 * 列表 / 撤销响应里的 key 视图。**绝不**包含 {@code keyHash} 或明文。
 */
public class ApiKeyView {

    private Long id;
    private Long appId;
    private String keyPrefix;
    private String scope;
    private String label;
    private String status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant lastUsedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    public static ApiKeyView from(ApiKey k) {
        ApiKeyView v = new ApiKeyView();
        v.id = k.getId();
        v.appId = k.getAppId();
        v.keyPrefix = k.getKeyPrefix();
        v.scope = k.getScope() == null ? null : k.getScope().name();
        v.label = k.getLabel();
        v.status = k.getStatus() == null ? null : k.getStatus().name();
        v.lastUsedAt = k.getLastUsedAt();
        v.expiresAt = k.getExpiresAt();
        v.createdAt = k.getCreatedAt();
        return v;
    }

    public Long getId() {
        return id;
    }

    public Long getAppId() {
        return appId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getScope() {
        return scope;
    }

    public String getLabel() {
        return label;
    }

    public String getStatus() {
        return status;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
