package com.hj.log.source.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hj.log.source.mapper.ApiKeyMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LastUsedTrackerTest {

    @Mock private ApiKeyMapper apiKeyMapper;

    private Cache<String, Boolean> cache;
    private LastUsedTracker tracker;

    @BeforeEach
    void setUp() {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(60))
                .maximumSize(100)
                .build();
        tracker = new LastUsedTracker(apiKeyMapper, cache);
        when(apiKeyMapper.touchLastUsed(any(), any())).thenReturn(1);
    }

    @Test
    void touch_should_trigger_update_on_first_call() {
        boolean fired = tracker.touch("hash-A", 1L);
        assertThat(fired).isTrue();
        verify(apiKeyMapper, times(1)).touchLastUsed(eq(1L), any(Instant.class));
        assertThat(cache.getIfPresent("hash-A")).isTrue();
    }

    @Test
    void touch_should_skip_update_when_cached() {
        tracker.touch("hash-B", 2L);
        tracker.touch("hash-B", 2L);
        tracker.touch("hash-B", 2L);
        verify(apiKeyMapper, times(1)).touchLastUsed(eq(2L), any(Instant.class));
    }

    @Test
    void touch_should_isolate_different_hashes() {
        tracker.touch("hash-C", 3L);
        tracker.touch("hash-D", 4L);
        verify(apiKeyMapper, times(1)).touchLastUsed(eq(3L), any(Instant.class));
        verify(apiKeyMapper, times(1)).touchLastUsed(eq(4L), any(Instant.class));
    }

    @Test
    void async_update_should_swallow_exception() {
        when(apiKeyMapper.touchLastUsed(any(), any())).thenThrow(new RuntimeException("DB down"));
        // 不应抛出
        tracker.asyncUpdate(99L);
        verify(apiKeyMapper, times(1)).touchLastUsed(eq(99L), any(Instant.class));
    }

    @Test
    void touch_returns_false_when_already_cached() {
        boolean first = tracker.touch("hash-E", 5L);
        boolean second = tracker.touch("hash-E", 5L);
        assertThat(first).isTrue();
        assertThat(second).isFalse();
        verify(apiKeyMapper, times(1)).touchLastUsed(eq(5L), any(Instant.class));
    }
}
