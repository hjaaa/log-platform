package com.hj.log.sdk.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.sdk.TestEvents;
import com.hj.log.sdk.transport.SdkJsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FallbackWriterTest {

    private final ObjectMapper mapper = SdkJsonMapper.create();

    @Test
    void should_write_ndjson_line_per_event(@TempDir Path dir) throws IOException {
        FallbackWriter writer = new FallbackWriter(dir, 512, mapper);
        int written = writer.write(List.of(TestEvents.of("hello"), TestEvents.of("world")));
        assertThat(written).isEqualTo(2);

        try (Stream<Path> s = Files.list(dir)) {
            Path file = s.findFirst().orElseThrow();
            List<String> lines = Files.readAllLines(file).stream().filter(l -> !l.isBlank()).toList();
            assertThat(lines).hasSize(2);
            for (String line : lines) {
                assertThat(line).startsWith("{").endsWith("}").contains("\"event\":").contains("\"ts\":");
            }
        }
    }

    @Test
    void should_create_dir_on_write(@TempDir Path parent) throws IOException {
        Path dir = parent.resolve("nested/fallback");
        FallbackWriter writer = new FallbackWriter(dir, 512, mapper);
        int written = writer.write(List.of(TestEvents.of("m")));
        assertThat(written).isEqualTo(1);
        assertThat(Files.exists(dir)).isTrue();
    }

    @Test
    void should_append_to_same_hour_file(@TempDir Path dir) throws IOException {
        FallbackWriter writer = new FallbackWriter(dir, 512, mapper);
        writer.write(List.of(TestEvents.of("first")));
        writer.write(List.of(TestEvents.of("second")));

        try (Stream<Path> s = Files.list(dir)) {
            // 同一小时内应只生成 1 个文件
            assertThat(s.count()).isEqualTo(1L);
        }
    }
}
