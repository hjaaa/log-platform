package com.hj.log.source.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/apps} 入参。
 *
 * <p>{@code code} 仅允许小写字母、数字、连字符；其他字段约束见各注解。
 */
public class CreateAppRequest {

    @NotBlank
    @Size(min = 1, max = 64)
    @Pattern(regexp = "[a-z0-9-]+")
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 64)
    private String owner;

    @NotBlank
    @Pattern(regexp = "dev|staging|prod")
    private String environment;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
