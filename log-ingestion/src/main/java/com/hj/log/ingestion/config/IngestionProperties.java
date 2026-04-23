package com.hj.log.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 写入相关配置；前缀 {@code log.platform.ingestion}。 */
@ConfigurationProperties(prefix = "log.platform.ingestion")
public class IngestionProperties {

    /** 单批日志条数上限。 */
    private int maxBatchSize = 200;

    /** 单条 message 最大长度（字符数，UTF-16 code unit）。 */
    private int maxMessageLength = 2048;

    /** 单条 stackTrace 最大长度（字符数）。 */
    private int maxStackTraceLength = 16384;

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(int maxMessageLength) {
        this.maxMessageLength = maxMessageLength;
    }

    public int getMaxStackTraceLength() {
        return maxStackTraceLength;
    }

    public void setMaxStackTraceLength(int maxStackTraceLength) {
        this.maxStackTraceLength = maxStackTraceLength;
    }
}
