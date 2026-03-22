package com.hhs.domain.handler;

import com.hhs.domain.event.MetricRecordedEvent;
import com.hhs.domain.event.ScoreUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Async event listener for cache invalidation.
 * Keeps caches consistent when data changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheInvalidationListener {

    private final CacheManager cacheManager;

    @Async("eventExecutor")
    @EventListener
    public void onMetricRecorded(MetricRecordedEvent event) {
        try {
            // Invalidate health score cache when new metric recorded
            invalidateUserCache(event.getUserId(), "scores");
            log.debug("Invalidated score cache for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Error invalidating cache for user: {}", event.getUserId(), e);
        }
    }

    @Async("eventExecutor")
    @EventListener
    public void onScoreUpdated(ScoreUpdatedEvent event) {
        try {
            // Also invalidate on score update (defensive)
            invalidateUserCache(event.getUserId(), "scores");
            log.debug("Invalidated score cache for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Error invalidating cache for user: {}", event.getUserId(), e);
        }
    }

    private void invalidateUserCache(Long userId, String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(userId);
        }
    }
}
