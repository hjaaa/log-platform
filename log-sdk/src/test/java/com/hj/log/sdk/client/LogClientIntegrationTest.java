package com.hj.log.sdk.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.hj.log.sdk.TestEvents;
import com.hj.log.sdk.config.LogSdkProperties;
import com.hj.log.sdk.model.LogEvent;
import com.hj.log.sdk.transport.RetryingSender;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 覆盖 detailed-design §6.5 测试矩阵 7 场景 + 磁盘上限 + write 耗时。
 *
 * <p>设计原则：
 * <ul>
 *   <li>Sleeper 注入 no-op → 退避不真实睡眠，测试在秒级完成</li>
 *   <li>flushIntervalMs / replayIntervalMs 压到 50–200ms，便于等待</li>
 *   <li>{@link LogClient#createWithoutShutdownHook}，避免 JVM hook 积压</li>
 * </ul>
 */
class LogClientIntegrationTest {

    private static final RetryingSender.Sleeper NO_SLEEP = millis -> {};

    private MockWebServer server;
    private LogClient client;
    @TempDir Path fallbackDir;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) {
            client.close();
        }
        server.shutdown();
    }

    private LogSdkProperties.Builder baseProps() {
        return LogSdkProperties.builder()
                .endpoint(server.url("/api/v1/logs").toString())
                .apiKey("test-key")
                .appCode("test-app")
                .flushBatchSize(5)
                .flushIntervalMs(50)
                .bufferCapacity(32)
                .httpConnectTimeoutMs(500)
                .httpRequestTimeoutMs(500)
                .httpRetryMaxAttempts(3)
                .fallbackDir(fallbackDir)
                .fallbackMaxDiskMb(1)
                .replayIntervalMs(60_000) // 默认关掉 replay；需要的用例各自覆盖
                .shutdownWaitMs(500);
    }

    // --- 用例 1：正常 flush -------------------------------------------------

    @Test
    void should_flush_batch_when_success() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client = LogClient.createForTest(baseProps().build(), NO_SLEEP);

        for (int i = 0; i < 5; i++) {
            client.write(TestEvents.of("msg-" + i));
        }
        client.flushNow();

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/v1/logs");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(req.getHeader("Content-Type")).startsWith("application/json");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"events\":");
        assertThat(body).contains("msg-0").contains("msg-4");
        assertThat(listFallbackFiles()).isEmpty();
    }

    // --- 用例 2：5xx 重试耗尽 → fallback -----------------------------------

    @Test
    void should_fallback_when_5xx_retry_exhausted() throws Exception {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setResponseCode(500));
        }
        client = LogClient.createForTest(baseProps().build(), NO_SLEEP);

        client.write(TestEvents.of("will-fallback"));
        client.flushNow();

        // 等 replay 被短暂触发前（用例 2 不测 replay 删除），断言 fallback 文件内容包含事件
        assertEventuallyHasFallback("will-fallback");
        // 4 次 attempt 都消耗了 enqueued MockResponse
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    // --- 用例 3：fallback replay --------------------------------------------

    @Test
    void should_replay_and_delete_file_when_success() throws Exception {
        // 预置一个 fallback 文件，然后启动 client，让 replay worker 读取并成功上传
        Path preFile = fallbackDir.resolve("log-sdk-20260101-00.ndjson");
        Files.writeString(
                preFile,
                "{\"event\":{\"logKind\":\"application\",\"level\":\"ERROR\",\"message\":\"replayed-msg\","
                        + "\"clientTs\":\"2026-01-01T00:00:00Z\"},\"ts\":\"2026-01-01T00:00:00Z\"}\n");

        server.enqueue(new MockResponse().setResponseCode(200));
        client = LogClient.createForTest(baseProps().replayIntervalMs(100).build(), NO_SLEEP);

        // 等 replay worker 扫一轮（100ms interval + 宽松 buffer）
        waitUntil(
                () -> {
                    try {
                        return listFallbackFiles().isEmpty();
                    } catch (IOException e) {
                        return false;
                    }
                },
                3_000);

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        assertThat(req.getBody().readUtf8()).contains("replayed-msg");
        assertThat(listFallbackFiles()).isEmpty();
    }

    // --- 用例 4：队列满丢弃 -------------------------------------------------

    @Test
    void should_drop_events_when_buffer_full() {
        // 策略：把 flush 触发阈值拉大到远超测试写入量，让 FlushWorker 在测试期间不触发 →
        // buffer 迅速被填满、后续 write 只能落入 droppedCount
        LogSdkProperties props =
                baseProps()
                        .bufferCapacity(16)
                        .flushBatchSize(10_000)
                        .flushIntervalMs(10_000)
                        .build();
        client = LogClient.createForTest(props, NO_SLEEP);

        for (int i = 0; i < 1_500; i++) {
            client.write(TestEvents.of("m-" + i));
        }

        assertThat(client.metrics().droppedCount())
                .isGreaterThan(0L)
                .isGreaterThanOrEqualTo(1_500L - 16L);
    }

    // --- 用例 5：shutdown hook 剩余 buffer 转 fallback ---------------------

    @Test
    void should_drain_to_fallback_when_shutdown_timeout() throws Exception {
        // 让所有请求都 500 → 0 重试 → 立即转 fallback。避免真实 HTTP 超时拖长 shutdown 时间
        server.setDispatcher(
                new okhttp3.mockwebserver.Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest req) {
                        return new MockResponse().setResponseCode(500);
                    }
                });
        LogSdkProperties props =
                baseProps()
                        .bufferCapacity(2_000)
                        .flushBatchSize(10_000) // 阻止周期触发
                        .flushIntervalMs(5_000)
                        .shutdownWaitMs(3_000)
                        .httpRetryMaxAttempts(0)
                        .build();
        client = LogClient.createForTest(props, NO_SLEEP);

        for (int i = 0; i < 1_500; i++) {
            client.write(TestEvents.of("bye-" + i));
        }

        client.close();
        client = null;

        // shutdown drain 可能在 close() 返回后仍有少量 IO 抖动，给 waitUntil 一个窗口
        waitUntil(
                () -> {
                    try {
                        return !listFallbackFiles().isEmpty();
                    } catch (IOException e) {
                        return false;
                    }
                },
                3_000);
        List<Path> files = listFallbackFiles();
        assertThat(files).isNotEmpty();
        long totalLines = 0L;
        for (Path f : files) {
            totalLines += countLines(f);
        }
        assertThat(totalLines).isGreaterThanOrEqualTo(1_500L);
    }

    // --- 用例 6：4xx 不重试 -------------------------------------------------

    @Test
    void should_not_retry_on_4xx() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400));
        client = LogClient.createForTest(baseProps().build(), NO_SLEEP);
        client.write(TestEvents.of("bad-req"));
        client.flushNow();

        // 仅 1 次 POST（无重试）
        Thread.sleep(200);
        assertThat(server.getRequestCount()).isEqualTo(1);
        // 不落 fallback
        assertThat(listFallbackFiles()).isEmpty();
    }

    // --- 用例 7：字段截断 ---------------------------------------------------

    @Test
    void should_truncate_long_fields_at_entry() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));
        client = LogClient.createForTest(baseProps().build(), NO_SLEEP);

        String huge = "x".repeat(3000);
        client.write(TestEvents.of(huge));
        client.flushNow();

        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req).isNotNull();
        String body = req.getBody().readUtf8();
        assertThat(body).contains("…[truncated]");
        // 服务端默认阈值 2048；事件内 message 长度应 ≤ 2048
        int start = body.indexOf("\"message\":\"") + "\"message\":\"".length();
        int end = body.indexOf("\"", start);
        String messageInJson = body.substring(start, end);
        // 注意：JSON 里会把 `…` 保留原字符（UTF-8 编码 3 字节）；length 是字符数
        // 但由于 JSON 转义，message 字段内的原文长度 = 阈值 2048
        // 判断包含截断后缀即可，避免 JSON 转义影响判断
        assertThat(messageInJson.endsWith("…[truncated]")).isTrue();
        // 粗略长度约束：截断后 message <= 2048
        assertThat(messageInJson.length()).isLessThanOrEqualTo(2048);

        assertThat(client.metrics().truncatedCount()).isEqualTo(1L);
    }

    // --- 额外：磁盘上限按字典序删最旧 -------------------------------------

    @Test
    void should_evict_oldest_file_when_disk_limit_exceeded() throws Exception {
        // 预置两个"超过阈值"的文件
        byte[] blob = new byte[600 * 1024];
        java.util.Arrays.fill(blob, (byte) 'x');
        Files.write(fallbackDir.resolve("log-sdk-20260101-00.ndjson"), blob);
        Files.write(fallbackDir.resolve("log-sdk-20260101-01.ndjson"), blob);

        LogSdkProperties props = baseProps().fallbackMaxDiskMb(1).build();
        // 让 5xx × 4 失败后触发写 fallback，从而先走 DiskGuard
        for (int i = 0; i < 4; i++) server.enqueue(new MockResponse().setResponseCode(500));
        client = LogClient.createForTest(props, NO_SLEEP);
        client.write(TestEvents.of("trigger"));
        client.flushNow();

        // 最老的 20260101-00 应已被删除
        assertThat(Files.exists(fallbackDir.resolve("log-sdk-20260101-00.ndjson"))).isFalse();
    }

    // --- 额外：write 非阻塞性能 --------------------------------------------

    @Test
    void should_write_non_blockingly_fast() {
        // 把 flush 关到极慢，让 buffer 填满然后丢；write 自身的耗时应仍极低
        LogSdkProperties props =
                baseProps()
                        .bufferCapacity(100)
                        .flushBatchSize(10_000)
                        .flushIntervalMs(10_000)
                        .build();
        client = LogClient.createForTest(props, NO_SLEEP);

        int iterations = 10_000;
        LogEvent e = TestEvents.of("perf");
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            client.write(e);
        }
        long elapsedNs = System.nanoTime() - start;
        double avgUs = elapsedNs / 1_000.0 / iterations;
        // 放宽至 100us，CI/慢机器容忍；实际本机 < 10us
        assertThat(avgUs).isLessThan(100.0);
    }

    // --- 辅助 ---------------------------------------------------------------

    private List<Path> listFallbackFiles() throws IOException {
        try (Stream<Path> s = Files.list(fallbackDir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".ndjson"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    private void assertEventuallyHasFallback(String expectedMarker) throws Exception {
        waitUntil(
                () -> {
                    try {
                        for (Path f : listFallbackFiles()) {
                            if (Files.readString(f).contains(expectedMarker)) {
                                return true;
                            }
                        }
                    } catch (IOException ignore) {
                    }
                    return false;
                },
                3_000);
    }

    private long countLines(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.filter(l -> !l.isBlank()).count();
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond, long timeoutMs)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Condition did not become true within " + timeoutMs + "ms");
    }
}
