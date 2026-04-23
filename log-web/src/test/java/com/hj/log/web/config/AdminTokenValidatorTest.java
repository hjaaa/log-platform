package com.hj.log.web.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hj.log.source.config.AdminTokenProperties;
import org.junit.jupiter.api.Test;

class AdminTokenValidatorTest {

    /** openssl rand -hex 32 生成的 64 字符示例，仅用于测试。 */
    private static final String VALID_TOKEN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void should_throw_when_token_missing() {
        AdminTokenValidator validator = newValidator(null);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置");
    }

    @Test
    void should_throw_when_token_blank() {
        AdminTokenValidator validator = newValidator("   ");
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_throw_when_token_is_default_placeholder() {
        AdminTokenValidator validator = newValidator(AdminTokenValidator.DEFAULT_PLACEHOLDER);
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("占位");
    }

    @Test
    void should_throw_when_token_shorter_than_min_length() {
        AdminTokenValidator validator = newValidator("a".repeat(AdminTokenValidator.MIN_LENGTH - 1));
        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("长度");
    }

    @Test
    void should_pass_when_token_is_valid() {
        AdminTokenValidator validator = newValidator(VALID_TOKEN);
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private static AdminTokenValidator newValidator(String token) {
        AdminTokenProperties props = new AdminTokenProperties();
        props.setToken(token);
        return new AdminTokenValidator(props);
    }
}
