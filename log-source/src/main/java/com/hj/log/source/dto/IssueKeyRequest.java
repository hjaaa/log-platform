package com.hj.log.source.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** {@code POST /api/v1/apps/{id}/keys} 入参。 */
public class IssueKeyRequest {

    /** 契约（openapi.yaml Scope）声明大小写不敏感，Service 层经 {@code Scope.fromCode} 转换。 */
    @NotBlank
    @Pattern(regexp = "write|read", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String scope;

    @Size(max = 64)
    private String label;

    /** 可空；为 {@code null} 视为永不过期。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant expiresAt;

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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
