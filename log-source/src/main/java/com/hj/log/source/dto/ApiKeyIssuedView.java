package com.hj.log.source.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * {@code POST /apps/{id}/keys} 响应：仅在签发瞬间使用，含明文 {@code plaintext}。
 *
 * <p>明文不入库，调用方必须保存（detailed-design §2.2.3）。任何后续接口都不再返回明文。
 */
public class ApiKeyIssuedView {

    private Long id;
    private Long appId;
    private String scope;
    private String label;
    private String keyPrefix;
    private String plaintext;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getPlaintext() {
        return plaintext;
    }

    public void setPlaintext(String plaintext) {
        this.plaintext = plaintext;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
