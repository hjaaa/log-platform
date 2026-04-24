package com.hj.log.sdk.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LogSdkPropertiesTest {

    @Test
    void should_build_with_defaults() {
        LogSdkProperties p =
                LogSdkProperties.builder()
                        .endpoint("http://localhost:9000/api/v1/logs")
                        .apiKey("write-key")
                        .appCode("demo")
                        .build();
        assertThat(p.flushBatchSize()).isEqualTo(LogSdkProperties.DEFAULT_FLUSH_BATCH_SIZE);
        assertThat(p.bufferCapacity()).isEqualTo(LogSdkProperties.DEFAULT_BUFFER_CAPACITY);
        assertThat(p.fallbackDir()).isNotNull();
    }

    @Test
    void should_reject_blank_endpoint() {
        assertThatThrownBy(
                        () ->
                                LogSdkProperties.builder()
                                        .endpoint(" ")
                                        .apiKey("k")
                                        .appCode("c")
                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void should_reject_zero_capacity() {
        assertThatThrownBy(
                        () ->
                                LogSdkProperties.builder()
                                        .endpoint("x")
                                        .apiKey("k")
                                        .appCode("c")
                                        .bufferCapacity(0)
                                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bufferCapacity");
    }

    @Test
    void should_allow_zero_retries() {
        LogSdkProperties p =
                LogSdkProperties.builder()
                        .endpoint("x")
                        .apiKey("k")
                        .appCode("c")
                        .httpRetryMaxAttempts(0)
                        .build();
        assertThat(p.httpRetryMaxAttempts()).isZero();
    }

    @Test
    void should_carry_custom_fallback_dir() {
        Path dir = Path.of("/tmp/log-sdk-abc");
        LogSdkProperties p =
                LogSdkProperties.builder()
                        .endpoint("x")
                        .apiKey("k")
                        .appCode("c")
                        .fallbackDir(dir)
                        .build();
        assertThat(p.fallbackDir()).isEqualTo(dir);
    }
}
