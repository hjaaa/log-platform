package com.hj.log.common.domain;

import com.hj.log.common.base.BaseEntity;
import com.hj.log.common.enums.KeyStatus;
import com.hj.log.common.enums.Scope;
import java.time.Instant;

/**
 * 对应表 {@code api_keys}。
 *
 * <p>明文 key 仅在签发时返回一次，不入库；本类只承载 {@code keyPrefix}（前 8 位脱敏展示）
 * 与 {@code keyHash}（SHA-256）。{@link #updatedAt} 列在该表上不存在，
 * {@link BaseEntity#getUpdatedAt()} 始终为 {@code null}。
 */
public class ApiKey extends BaseEntity {

    private Long appId;
    private String keyPrefix;
    private String keyHash;
    private Scope scope;
    private String label;
    private KeyStatus status;
    private Instant lastUsedAt;
    private Instant expiresAt;

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public KeyStatus getStatus() {
        return status;
    }

    public void setStatus(KeyStatus status) {
        this.status = status;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Instant lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
