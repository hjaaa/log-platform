package com.hj.log.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

    @Test
    void should_default_http_status_to_400() {
        BusinessException ex = new BusinessException(ErrorCode.AUTH_INVALID_KEY, "bad key");
        assertEquals(400, ex.getHttpStatus());
        assertEquals("AUTH_INVALID_KEY", ex.getCode());
        assertEquals("bad key", ex.getMessage());
    }

    @Test
    void should_carry_custom_http_status() {
        BusinessException ex = new BusinessException(ErrorCode.QUERY_LOG_NOT_FOUND, "not found", 404);
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void should_be_throwable() {
        assertThrows(BusinessException.class, () -> {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "boom", 500);
        });
    }
}
