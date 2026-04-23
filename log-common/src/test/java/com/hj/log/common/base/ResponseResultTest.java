package com.hj.log.common.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hj.log.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ResponseResultTest {

    @Test
    void ok_should_carry_data_and_OK_code() {
        ResponseResult<String> r = ResponseResult.ok("hello");
        assertEquals(ErrorCode.OK, r.getCode());
        assertEquals("", r.getMessage());
        assertEquals("hello", r.getData());
    }

    @Test
    void ok_no_arg_should_have_null_data() {
        ResponseResult<Object> r = ResponseResult.ok();
        assertEquals(ErrorCode.OK, r.getCode());
        assertNull(r.getData());
    }

    @Test
    void fail_should_use_provided_code_and_message() {
        ResponseResult<Object> r = ResponseResult.fail(ErrorCode.AUTH_INVALID_KEY, "bad key");
        assertEquals(ErrorCode.AUTH_INVALID_KEY, r.getCode());
        assertEquals("bad key", r.getMessage());
        assertNull(r.getData());
    }
}
