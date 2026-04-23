package com.hj.log.source.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.hj.log.common.domain.AppRegistration;
import java.time.Instant;

/** {@code GET /apps} / {@code POST /apps} 响应单元。 */
public class AppView {

    private Long id;
    private String code;
    private String name;
    private String owner;
    private String environment;
    private String status;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    public static AppView from(AppRegistration app) {
        AppView v = new AppView();
        v.id = app.getId();
        v.code = app.getCode();
        v.name = app.getName();
        v.owner = app.getOwner();
        v.environment = app.getEnvironment() == null ? null : app.getEnvironment().name();
        v.status = app.getStatus() == null ? null : app.getStatus().name();
        v.createdAt = app.getCreatedAt();
        return v;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getOwner() {
        return owner;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
