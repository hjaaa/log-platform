package com.hj.log.sdk;

import com.hj.log.sdk.model.LogEvent;
import java.time.Instant;

/** 测试事件工厂：减少每个 test 的重复样板。 */
public final class TestEvents {

    private TestEvents() {}

    public static LogEvent of(String message) {
        return of(message, "ERROR");
    }

    public static LogEvent of(String message, String level) {
        return new LogEvent(
                "application",
                level,
                message,
                null,
                null,
                "trace-" + message.hashCode(),
                "span-1",
                "req-1",
                "com.example.Demo",
                Thread.currentThread().getName(),
                "test-host",
                Instant.parse("2026-04-24T00:00:00Z"));
    }
}
