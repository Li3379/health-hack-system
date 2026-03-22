package com.hhs.service.impl;

import com.hhs.domain.event.ScoreUpdatedEvent;
import com.hhs.exception.BusinessException;
import com.hhs.service.HealthScoreService;
import com.hhs.service.domain.MetricValidator;
import com.hhs.service.domain.ScoreCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Health Score Service Implementation
 * Orchestrates health score calculation using decomposed components
 * Caches results in Redis for improved performance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthScoreServiceImpl implements HealthScoreService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ScoreCalculator scoreCalculator;
    private final MetricValidator metricValidator;
    private final ApplicationEventPublisher eventPublisher;

    private static final int CACHE_TTL_MINUTES = 5;
    private static final String CACHE_KEY_PREFIX = "health:score:";

    /**
     * Calculate health score for a user
     *
     * @param userId User ID
     * @return Health score with breakdown, or default score if user has no data
     */
    @Override
    public com.hhs.vo.HealthScoreVO calculateScore(Long userId) {
        // Check if user has data, return default score if not
        if (!metricValidator.hasAnyData(userId)) {
            log.info("User {} has no health data, returning default score", userId);
            return createDefaultScore();
        }

        // Try cache first
        com.hhs.vo.HealthScoreVO cached = getCachedScore(userId);
        if (cached != null) {
            cached.setIsCached(true);
            return cached;
        }

        // Calculate new score
        com.hhs.vo.HealthScoreVO score = scoreCalculator.calculate(userId);
        score.setIsCached(false);
        score.setCalculationMethod("RULE_BASED");
        score.setExpiresAt(score.getCalculatedAt().plusMinutes(CACHE_TTL_MINUTES));

        // Cache the result
        cacheScore(userId, score);

        // Publish event for cache invalidation
        eventPublisher.publishEvent(new ScoreUpdatedEvent(userId, score.getScore()));

        return score;
    }

    /**
     * Force recalculation (bypass cache)
     *
     * @param userId User ID
     * @return Health score with breakdown, or default score if user has no data
     */
    @Override
    public com.hhs.vo.HealthScoreVO forceRecalculate(Long userId) {
        // Check if user has data, return default score if not
        if (!metricValidator.hasAnyData(userId)) {
            log.info("User {} has no health data, returning default score", userId);
            return createDefaultScore();
        }

        // Clear cache
        clearCache(userId);

        // Calculate new score
        com.hhs.vo.HealthScoreVO score = scoreCalculator.calculate(userId);
        score.setIsCached(false);
        score.setCalculationMethod("RULE_BASED");
        score.setExpiresAt(score.getCalculatedAt().plusMinutes(CACHE_TTL_MINUTES));

        // Cache the result
        cacheScore(userId, score);

        // Publish event for cache invalidation
        eventPublisher.publishEvent(new ScoreUpdatedEvent(userId, score.getScore()));

        return score;
    }

    /**
     * Create default health score for users with no data
     */
    private com.hhs.vo.HealthScoreVO createDefaultScore() {
        com.hhs.vo.HealthScoreVO score = new com.hhs.vo.HealthScoreVO();
        score.setScore(0);
        score.setLevel("NO_DATA");
        score.setIsCached(false);
        score.setCalculationMethod("DEFAULT");
        score.setCalculatedAt(java.time.LocalDateTime.now());
        score.setExpiresAt(score.getCalculatedAt().plusMinutes(CACHE_TTL_MINUTES));
        score.setMessage("暂无健康数据，请先在\"健康数据\"页面添加您的健康记录（如血糖、血压、心率等）");
        score.setFactors(java.util.Map.of(
            "hasData", false,
            "requiredMetrics", java.util.List.of("血糖", "血压", "心率"),
            "tip", "添加至少一项健康数据后，即可获得健康评分"
        ));
        return score;
    }

    /**
     * Get cached score if available
     *
     * @param userId User ID
     * @return Cached score or null if not in cache or Redis unavailable
     */
    @Override
    public com.hhs.vo.HealthScoreVO getCachedScore(Long userId) {
        try {
            String key = CACHE_KEY_PREFIX + userId;
            Object cached = redisTemplate.opsForValue().get(key);
            return cached != null ? (com.hhs.vo.HealthScoreVO) cached : null;
        } catch (RedisSystemException e) {
            log.warn("Redis connection failed, returning null for cached score: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Redis operation failed, returning null for cached score: {}", e.getMessage());
            return null;
        }
    }

    private void cacheScore(Long userId, com.hhs.vo.HealthScoreVO score) {
        try {
            String key = CACHE_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(key, score, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (RedisSystemException e) {
            log.warn("Redis connection failed, skipping cache: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Redis operation failed, skipping cache: {}", e.getMessage());
        }
    }

    private void clearCache(Long userId) {
        try {
            String key = CACHE_KEY_PREFIX + userId;
            redisTemplate.delete(key);
        } catch (RedisSystemException e) {
            log.warn("Redis connection failed, skipping cache clear: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Redis operation failed, skipping cache clear: {}", e.getMessage());
        }
    }
}
