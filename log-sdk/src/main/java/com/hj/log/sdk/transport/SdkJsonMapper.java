package com.hj.log.sdk.transport;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * SDK 共享 ObjectMapper：注册 JavaTimeModule，把 {@link java.time.Instant} 序列化为 ISO8601
 * 字符串（与 log-ingestion {@code LogEventDto} 字段格式对齐）。
 */
public final class SdkJsonMapper {

    private SdkJsonMapper() {}

    public static ObjectMapper create() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return m;
    }
}
