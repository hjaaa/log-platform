package com.hj.log.query.cursor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hj.log.common.exception.BusinessException;
import com.hj.log.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class CursorCodecTest {

    @Test
    void encode_then_decode_should_round_trip() {
        String token = CursorCodec.encode(1735996300000L, 1024L);
        CursorCodec.Cursor c = CursorCodec.decode(token);
        assertThat(c.getServerTsMillis()).isEqualTo(1735996300000L);
        assertThat(c.getId()).isEqualTo(1024L);
    }

    @Test
    void decode_should_reject_null_or_empty() {
        assertCursorInvalid(null);
        assertCursorInvalid("");
    }

    @Test
    void decode_should_reject_invalid_base64() {
        assertCursorInvalid("!!!not_base64!!!");
    }

    @Test
    void decode_should_reject_missing_separator() {
        // "abcd" base64-encoded → no colon
        String bad = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("abcd".getBytes());
        assertCursorInvalid(bad);
    }

    @Test
    void decode_should_reject_non_numeric_components() {
        String bad = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("notanumber:1".getBytes());
        assertCursorInvalid(bad);
    }

    @Test
    void decode_should_reject_overflow() {
        String bad = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("99999999999999999999:1".getBytes());
        assertCursorInvalid(bad);
    }

    @Test
    void decode_should_reject_empty_id_part() {
        String bad = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("123:".getBytes());
        assertCursorInvalid(bad);
    }

    private void assertCursorInvalid(String input) {
        assertThatThrownBy(() -> CursorCodec.decode(input))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ErrorCode.QUERY_INVALID_CURSOR)
                .hasFieldOrPropertyWithValue("httpStatus", 400);
    }
}
