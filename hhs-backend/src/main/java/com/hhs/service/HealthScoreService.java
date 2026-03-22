package com.hhs.service;

import com.hhs.vo.HealthScoreVO;

/**
 * Health Score Service
 * Calculates overall health score using hybrid strategy:
 * - Rule-based fast calculation (default)
 * - AI-based calculation (once daily, or high-risk indicators)
 * - Redis caching (5 minutes)
 */
public interface HealthScoreService {

    /**
     * Calculate health score for a user
     *
     * @param userId User ID
     * @return Health score with breakdown
     */
    HealthScoreVO calculateScore(Long userId);

    /**
     * Force recalculation (bypass cache)
     *
     * @param userId User ID
     * @return Health score with breakdown
     */
    HealthScoreVO forceRecalculate(Long userId);

    /**
     * Get cached score if available
     *
     * @param userId User ID
     * @return Cached score or null
     */
    HealthScoreVO getCachedScore(Long userId);
}
