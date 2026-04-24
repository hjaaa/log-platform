package com.hj.log.sdk.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiskGuardTest {

    @Test
    void should_not_evict_when_under_limit(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("log-sdk-00.ndjson"), new byte[100]);
        Files.write(dir.resolve("log-sdk-01.ndjson"), new byte[100]);
        new DiskGuard(dir, 1).ensureUnderLimit();
        assertThat(Files.exists(dir.resolve("log-sdk-00.ndjson"))).isTrue();
        assertThat(Files.exists(dir.resolve("log-sdk-01.ndjson"))).isTrue();
    }

    @Test
    void should_evict_oldest_files_in_lexical_order(@TempDir Path dir) throws IOException {
        byte[] big = new byte[700 * 1024];
        Files.write(dir.resolve("log-sdk-20260101-00.ndjson"), big);
        Files.write(dir.resolve("log-sdk-20260101-01.ndjson"), big);
        Files.write(dir.resolve("log-sdk-20260101-02.ndjson"), big);

        new DiskGuard(dir, 1).ensureUnderLimit();

        // 1 MB 阈值：3 × 700KB 超阈值，按字典序删最旧（00 → 01 直到 ≤ 1MB）
        assertThat(Files.exists(dir.resolve("log-sdk-20260101-00.ndjson"))).isFalse();
        assertThat(Files.exists(dir.resolve("log-sdk-20260101-01.ndjson"))).isFalse();
        assertThat(Files.exists(dir.resolve("log-sdk-20260101-02.ndjson"))).isTrue();
    }

    @Test
    void should_ignore_missing_dir(@TempDir Path parent) throws IOException {
        Path missing = parent.resolve("never-created");
        // 不应抛异常
        new DiskGuard(missing, 1).ensureUnderLimit();
    }
}
