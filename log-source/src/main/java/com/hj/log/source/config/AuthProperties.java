package com.hj.log.source.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 鉴权相关配置项。前缀 {@code log.platform.auth}。
 *
 * <p>{@code admin.token} 单独读取（前缀 {@code log.platform.admin}），由
 * {@link AdminTokenProperties} 承载——保留独立绑定以便 fail-fast 校验。
 */
@ConfigurationProperties(prefix = "log.platform.auth")
public class AuthProperties {

    private final LastUsed lastUsed = new LastUsed();

    public LastUsed getLastUsed() {
        return lastUsed;
    }

    public static class LastUsed {

        /** Caffeine TTL（秒）。命中即跳过 last_used_at 的 UPDATE。 */
        private long cacheTtlSeconds = 60L;

        /** Caffeine 最大容量。10 应用 × 20 key 的兜底估算。 */
        private long cacheMaxSize = 200L;

        public long getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(long cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }

        public long getCacheMaxSize() {
            return cacheMaxSize;
        }

        public void setCacheMaxSize(long cacheMaxSize) {
            this.cacheMaxSize = cacheMaxSize;
        }
    }
}
