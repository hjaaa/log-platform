package com.hj.log.source.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.hj.log.source.config.AuthCacheConfig;
import com.hj.log.source.config.AuthExecutorConfig;
import com.hj.log.source.mapper.ApiKeyMapper;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 触发 {@code api_keys.last_used_at} 的异步更新；调用方零阻塞。
 *
 * <p>节流逻辑：Caffeine TTL=60s 是<b>唯一周期来源</b>，命中即跳过；
 * 失效（首次或过期被驱逐）即触发 UPDATE 并回写缓存。
 *
 * <p>UPDATE 失败不影响业务请求——业务请求线程已经返回；这里只 WARN，
 * 下个 60s 周期可重试。
 */
@Component
public class LastUsedTracker {

    private static final Logger log = LoggerFactory.getLogger(LastUsedTracker.class);

    private final ApiKeyMapper apiKeyMapper;
    private final Cache<String, Boolean> cache;

    public LastUsedTracker(
            ApiKeyMapper apiKeyMapper,
            @Qualifier(AuthCacheConfig.LAST_USED_CACHE_BEAN) Cache<String, Boolean> cache) {
        this.apiKeyMapper = apiKeyMapper;
        this.cache = cache;
    }

    /**
     * 节流入口：同步检查缓存命中，未命中则提交异步 UPDATE。
     *
     * @param keyHash SHA-256 16 进制小写
     * @param keyId   api_keys.id
     * @return 是否触发了异步 UPDATE（仅供测试 / 监控用）
     */
    public boolean touch(String keyHash, Long keyId) {
        if (cache.getIfPresent(keyHash) != null) {
            return false;
        }
        cache.put(keyHash, Boolean.TRUE);
        asyncUpdate(keyId);
        return true;
    }

    @Async(AuthExecutorConfig.AUTH_EXECUTOR_BEAN)
    public void asyncUpdate(Long keyId) {
        try {
            apiKeyMapper.touchLastUsed(keyId, Instant.now());
        } catch (RuntimeException ex) {
            log.warn("[auth] touchLastUsed 失败 keyId={} cause={}", keyId, ex.toString());
        }
    }
}
