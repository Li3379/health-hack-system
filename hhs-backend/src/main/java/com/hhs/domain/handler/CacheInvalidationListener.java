package com.hhs.domain.handler;

import com.hhs.domain.event.MetricRecordedEvent;
import com.hhs.service.HealthScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final HealthScoreService healthScoreService;

    /**
     * A recorded metric changes the inputs the score is computed from, so the cached score for
     * that user must go.
     *
     * <p>This used to evict a Spring Cache named {@code "scores"} — but nothing in the code base
     * declares {@code @Cacheable("scores")}, so the call was a no-op while the real score cache
     * (the Redis key {@code health:score:v2:{userId}}, 5-minute TTL) stayed stale. Going through
     * the service keeps the key prefix and TTL owned by one class instead of duplicating the
     * string here.
     *
     * <p>There is deliberately no {@code ScoreUpdatedEvent} handler. That event is published by
     * {@code HealthScoreServiceImpl.calculateScore} <em>after</em> the fresh score has been cached,
     * so evicting on it would delete the value just written and every later read would miss. The
     * old {@code "scores"} eviction was harmless only because that cache name does not exist;
     * re-adding it against the real key would be actively wrong.
     */
    @Async("eventExecutor")
    @EventListener
    public void onMetricRecorded(MetricRecordedEvent event) {
        try {
            healthScoreService.evictCachedScore(event.getUserId());
            log.debug("Invalidated score cache for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Error invalidating cache for user: {}", event.getUserId(), e);
        }
    }
}
