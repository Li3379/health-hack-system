package com.hhs.service;

import com.hhs.entity.RealtimeMetric;

/**
 * Metric Deduplication Service
 * Prevents duplicate metric entries within a time window
 */
public interface MetricDeduplicationService {

    /**
     * Check if a metric should be accepted (not a duplicate)
     *
     * @param metric The metric to check
     * @return true if metric should be accepted, false if duplicate
     */
    boolean shouldAcceptMetric(RealtimeMetric metric);

    /**
     * Record a metric for deduplication tracking
     *
     * @param metric The metric that was accepted
     */
    void recordMetric(RealtimeMetric metric);
}
