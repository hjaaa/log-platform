package com.hj.log.source.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine 缓存：API key hash → 上次写入 last_used_at 的时间。
 *
 * <p>命中即跳过 UPDATE，缺失或 TTL 过期 → 触发异步 UPDATE 后回写缓存。
 * TTL 是节流的<b>单一来源</b>，不在代码层叠加"距今 > 60s"判定。
 */
@Configuration
public class AuthCacheConfig {

    public static final String LAST_USED_CACHE_BEAN = "authLastUsedCache";

    @Bean(LAST_USED_CACHE_BEAN)
    public Cache<String, Boolean> authLastUsedCache(AuthProperties props) {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(props.getLastUsed().getCacheTtlSeconds()))
                .maximumSize(props.getLastUsed().getCacheMaxSize())
                .build();
    }
}
