package com.hj.log.common.base;

import java.time.Instant;

/**
 * DO 公共字段。所有数据库实体继承此类。
 *
 * <p>{@code createdAt} / {@code updatedAt} 由 DB 默认值与 {@code ON UPDATE} 触发器维护，
 * 应用层通常无需主动赋值。
 */
public abstract class BaseEntity {

    private Long id;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
