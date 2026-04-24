package com.hj.log.sdk.fallback;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hj.log.sdk.model.LogEvent;
import com.hj.log.sdk.transport.SdkJsonMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FallbackReaderTest {

    private final ObjectMapper mapper = SdkJsonMapper.create();

    @Test
    void should_parse_all_valid_lines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("log-sdk-20260101-00.ndjson");
        Files.writeString(
                file,
                "{\"event\":{\"logKind\":\"application\",\"level\":\"ERROR\","
                        + "\"message\":\"a\",\"clientTs\":\"2026-01-01T00:00:00Z\"},"
                        + "\"ts\":\"2026-01-01T00:00:00Z\"}\n"
                        + "{\"event\":{\"logKind\":\"application\",\"level\":\"WARN\","
                        + "\"message\":\"b\",\"clientTs\":\"2026-01-01T00:00:01Z\"},"
                        + "\"ts\":\"2026-01-01T00:00:01Z\"}\n");

        FallbackReader reader = new FallbackReader(dir, mapper);
        List<LogEvent> events = reader.readAll(file);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).message()).isEqualTo("a");
        assertThat(events.get(1).message()).isEqualTo("b");
    }

    @Test
    void should_skip_blank_and_malformed_lines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("log-sdk-20260101-00.ndjson");
        Files.writeString(
                file,
                "not-json\n"
                        + "\n"
                        + "{\"event\":{\"logKind\":\"application\",\"level\":\"ERROR\","
                        + "\"message\":\"ok\",\"clientTs\":\"2026-01-01T00:00:00Z\"},"
                        + "\"ts\":\"2026-01-01T00:00:00Z\"}\n");
        FallbackReader reader = new FallbackReader(dir, mapper);
        List<LogEvent> events = reader.readAll(file);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).message()).isEqualTo("ok");
    }

    @Test
    void should_list_files_in_lexical_order(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("log-sdk-20260101-03.ndjson"));
        Files.createFile(dir.resolve("log-sdk-20260101-01.ndjson"));
        Files.createFile(dir.resolve("log-sdk-20260101-02.ndjson"));
        Files.createFile(dir.resolve("should-ignore.txt"));

        FallbackReader reader = new FallbackReader(dir, mapper);
        List<Path> files = reader.listPendingFiles();
        assertThat(files).extracting(p -> p.getFileName().toString())
                .containsExactly(
                        "log-sdk-20260101-01.ndjson",
                        "log-sdk-20260101-02.ndjson",
                        "log-sdk-20260101-03.ndjson");
    }
}
