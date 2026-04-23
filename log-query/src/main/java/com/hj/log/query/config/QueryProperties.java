package com.hj.log.query.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 查询相关配置；前缀 {@code log.platform.query}。详见 detailed-design §7。 */
@ConfigurationProperties(prefix = "log.platform.query")
public class QueryProperties {

    /** {@code /search} 默认 pageSize。 */
    private int defaultPageSize = 100;

    /** {@code /search} pageSize 上限。 */
    private int maxPageSize = 500;

    /** {@code /trace/{id}} 默认 limit。 */
    private int traceDefaultLimit = 500;

    /** {@code /trace/{id}} limit 上限。 */
    private int traceMaxLimit = 1000;

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getTraceDefaultLimit() {
        return traceDefaultLimit;
    }

    public void setTraceDefaultLimit(int traceDefaultLimit) {
        this.traceDefaultLimit = traceDefaultLimit;
    }

    public int getTraceMaxLimit() {
        return traceMaxLimit;
    }

    public void setTraceMaxLimit(int traceMaxLimit) {
        this.traceMaxLimit = traceMaxLimit;
    }
}
