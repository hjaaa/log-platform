package com.hj.log.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EnumsTest {

    @Test
    void should_parse_scope_case_insensitive() {
        assertEquals(Scope.WRITE, Scope.fromCode("write"));
        assertEquals(Scope.READ, Scope.fromCode("READ"));
        assertEquals(Scope.ADMIN, Scope.fromCode("Admin"));
        assertNull(Scope.fromCode(null));
        assertNull(Scope.fromCode("nope"));
    }

    @Test
    void should_parse_log_level_case_insensitive() {
        assertEquals(LogLevel.ERROR, LogLevel.fromCode("error"));
        assertEquals(LogLevel.DEBUG, LogLevel.fromCode("DEBUG"));
        assertNull(LogLevel.fromCode("trace"));
    }

    @Test
    void should_parse_log_kind() {
        assertEquals(LogKind.application, LogKind.fromCode("application"));
        assertEquals(LogKind.test, LogKind.fromCode("TEST"));
        assertNull(LogKind.fromCode("biz"));
    }

    @Test
    void should_parse_key_status() {
        assertEquals(KeyStatus.active, KeyStatus.fromCode("active"));
        assertEquals(KeyStatus.revoked, KeyStatus.fromCode("REVOKED"));
        assertNull(KeyStatus.fromCode("expired"));
    }

    @Test
    void should_parse_environment() {
        assertEquals(Environment.dev, Environment.fromCode("dev"));
        assertEquals(Environment.prod, Environment.fromCode("PROD"));
        assertNull(Environment.fromCode("uat"));
    }

    @Test
    void should_parse_app_status() {
        assertEquals(AppStatus.active, AppStatus.fromCode("active"));
        assertEquals(AppStatus.disabled, AppStatus.fromCode("Disabled"));
        assertNull(AppStatus.fromCode(""));
    }
}
