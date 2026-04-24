package com.hj.log.sdk.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * SDK 侧所有配置项集合（detailed-design §7.2）。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不可变：字段 final；一旦 build 完成，运行期 SDK 内部不得修改</li>
 *   <li>Builder：避免 18 参构造器；保留默认值在常量区，便于宿主只覆盖必要项</li>
 *   <li>必填校验：{@code endpoint} / {@code apiKey} / {@code appCode} 三项缺失直接 IllegalArgumentException</li>
 * </ul>
 */
public final class LogSdkProperties {

    public static final int DEFAULT_FLUSH_BATCH_SIZE = 100;
    public static final long DEFAULT_FLUSH_INTERVAL_MS = 2_000L;
    public static final int DEFAULT_BUFFER_CAPACITY = 1024;
    public static final long DEFAULT_HTTP_CONNECT_TIMEOUT_MS = 3_000L;
    public static final long DEFAULT_HTTP_REQUEST_TIMEOUT_MS = 3_000L;
    public static final int DEFAULT_HTTP_RETRY_MAX_ATTEMPTS = 3;
    public static final long DEFAULT_FALLBACK_MAX_DISK_MB = 512L;
    public static final long DEFAULT_REPLAY_INTERVAL_MS = 30_000L;
    public static final long DEFAULT_SHUTDOWN_WAIT_MS = 5_000L;
    public static final int DEFAULT_TRUNCATE_MESSAGE_LENGTH = 2048;
    public static final int DEFAULT_TRUNCATE_STACK_TRACE_LENGTH = 16384;

    private final String endpoint;
    private final String apiKey;
    private final String appCode;
    private final int flushBatchSize;
    private final long flushIntervalMs;
    private final int bufferCapacity;
    private final long httpConnectTimeoutMs;
    private final long httpRequestTimeoutMs;
    private final int httpRetryMaxAttempts;
    private final Path fallbackDir;
    private final long fallbackMaxDiskMb;
    private final long replayIntervalMs;
    private final long shutdownWaitMs;
    private final int truncateMessageLength;
    private final int truncateStackTraceLength;

    private LogSdkProperties(Builder b) {
        this.endpoint = requireNonBlank(b.endpoint, "endpoint");
        this.apiKey = requireNonBlank(b.apiKey, "apiKey");
        this.appCode = requireNonBlank(b.appCode, "appCode");
        this.flushBatchSize = requirePositive(b.flushBatchSize, "flushBatchSize");
        this.flushIntervalMs = requirePositive(b.flushIntervalMs, "flushIntervalMs");
        this.bufferCapacity = requirePositive(b.bufferCapacity, "bufferCapacity");
        this.httpConnectTimeoutMs = requirePositive(b.httpConnectTimeoutMs, "httpConnectTimeoutMs");
        this.httpRequestTimeoutMs = requirePositive(b.httpRequestTimeoutMs, "httpRequestTimeoutMs");
        this.httpRetryMaxAttempts = requireNonNegative(b.httpRetryMaxAttempts, "httpRetryMaxAttempts");
        this.fallbackDir = Objects.requireNonNull(b.fallbackDir, "fallbackDir");
        this.fallbackMaxDiskMb = requirePositive(b.fallbackMaxDiskMb, "fallbackMaxDiskMb");
        this.replayIntervalMs = requirePositive(b.replayIntervalMs, "replayIntervalMs");
        this.shutdownWaitMs = requireNonNegative(b.shutdownWaitMs, "shutdownWaitMs");
        this.truncateMessageLength = requirePositive(b.truncateMessageLength, "truncateMessageLength");
        this.truncateStackTraceLength =
                requirePositive(b.truncateStackTraceLength, "truncateStackTraceLength");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String endpoint() {
        return endpoint;
    }

    public String apiKey() {
        return apiKey;
    }

    public String appCode() {
        return appCode;
    }

    public int flushBatchSize() {
        return flushBatchSize;
    }

    public long flushIntervalMs() {
        return flushIntervalMs;
    }

    public int bufferCapacity() {
        return bufferCapacity;
    }

    public long httpConnectTimeoutMs() {
        return httpConnectTimeoutMs;
    }

    public long httpRequestTimeoutMs() {
        return httpRequestTimeoutMs;
    }

    public int httpRetryMaxAttempts() {
        return httpRetryMaxAttempts;
    }

    public Path fallbackDir() {
        return fallbackDir;
    }

    public long fallbackMaxDiskMb() {
        return fallbackMaxDiskMb;
    }

    public long replayIntervalMs() {
        return replayIntervalMs;
    }

    public long shutdownWaitMs() {
        return shutdownWaitMs;
    }

    public int truncateMessageLength() {
        return truncateMessageLength;
    }

    public int truncateStackTraceLength() {
        return truncateStackTraceLength;
    }

    public static final class Builder {
        private String endpoint;
        private String apiKey;
        private String appCode;
        private int flushBatchSize = DEFAULT_FLUSH_BATCH_SIZE;
        private long flushIntervalMs = DEFAULT_FLUSH_INTERVAL_MS;
        private int bufferCapacity = DEFAULT_BUFFER_CAPACITY;
        private long httpConnectTimeoutMs = DEFAULT_HTTP_CONNECT_TIMEOUT_MS;
        private long httpRequestTimeoutMs = DEFAULT_HTTP_REQUEST_TIMEOUT_MS;
        private int httpRetryMaxAttempts = DEFAULT_HTTP_RETRY_MAX_ATTEMPTS;
        private Path fallbackDir =
                Paths.get(System.getProperty("user.home", "."), ".log-sdk", "fallback");
        private long fallbackMaxDiskMb = DEFAULT_FALLBACK_MAX_DISK_MB;
        private long replayIntervalMs = DEFAULT_REPLAY_INTERVAL_MS;
        private long shutdownWaitMs = DEFAULT_SHUTDOWN_WAIT_MS;
        private int truncateMessageLength = DEFAULT_TRUNCATE_MESSAGE_LENGTH;
        private int truncateStackTraceLength = DEFAULT_TRUNCATE_STACK_TRACE_LENGTH;

        public Builder endpoint(String v) {
            this.endpoint = v;
            return this;
        }

        public Builder apiKey(String v) {
            this.apiKey = v;
            return this;
        }

        public Builder appCode(String v) {
            this.appCode = v;
            return this;
        }

        public Builder flushBatchSize(int v) {
            this.flushBatchSize = v;
            return this;
        }

        public Builder flushIntervalMs(long v) {
            this.flushIntervalMs = v;
            return this;
        }

        public Builder bufferCapacity(int v) {
            this.bufferCapacity = v;
            return this;
        }

        public Builder httpConnectTimeoutMs(long v) {
            this.httpConnectTimeoutMs = v;
            return this;
        }

        public Builder httpRequestTimeoutMs(long v) {
            this.httpRequestTimeoutMs = v;
            return this;
        }

        public Builder httpRetryMaxAttempts(int v) {
            this.httpRetryMaxAttempts = v;
            return this;
        }

        public Builder fallbackDir(Path v) {
            this.fallbackDir = v;
            return this;
        }

        public Builder fallbackMaxDiskMb(long v) {
            this.fallbackMaxDiskMb = v;
            return this;
        }

        public Builder replayIntervalMs(long v) {
            this.replayIntervalMs = v;
            return this;
        }

        public Builder shutdownWaitMs(long v) {
            this.shutdownWaitMs = v;
            return this;
        }

        public Builder truncateMessageLength(int v) {
            this.truncateMessageLength = v;
            return this;
        }

        public Builder truncateStackTraceLength(int v) {
            this.truncateStackTraceLength = v;
            return this;
        }

        public LogSdkProperties build() {
            return new LogSdkProperties(this);
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("log-sdk config `" + name + "` must not be blank");
        }
        return value;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "log-sdk config `" + name + "` must be > 0, got " + value);
        }
        return value;
    }

    private static long requirePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(
                    "log-sdk config `" + name + "` must be > 0, got " + value);
        }
        return value;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    "log-sdk config `" + name + "` must be >= 0, got " + value);
        }
        return value;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(
                    "log-sdk config `" + name + "` must be >= 0, got " + value);
        }
        return value;
    }
}
