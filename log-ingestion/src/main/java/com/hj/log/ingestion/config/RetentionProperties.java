package com.hj.log.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 保留期清理配置；前缀 {@code log.platform.retention}。 */
@ConfigurationProperties(prefix = "log.platform.retention")
public class RetentionProperties {

    /** 保留天数；落库时间早于 {@code now - days} 的日志会被异步清理。 */
    private int days = 7;

    /** 单批 DELETE 行数上限。 */
    private int batchSize = 500;

    /** Quartz cron 表达式（Spring 6 位）；默认每小时第 17 分错峰。 */
    private String cron = "0 17 * * * ?";

    /** 每批 DELETE 之间的暂停（毫秒），避免长事务挤占连接。 */
    private long batchPauseMs = 10L;

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public long getBatchPauseMs() {
        return batchPauseMs;
    }

    public void setBatchPauseMs(long batchPauseMs) {
        this.batchPauseMs = batchPauseMs;
    }
}
