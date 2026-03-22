package com.hhs.service;

import com.hhs.dto.AlertVO;
import com.hhs.entity.AlertRule;
import com.hhs.entity.RealtimeMetric;
import com.hhs.entity.UserThreshold;

import java.util.List;

/**
 * Alert Rule Engine
 * Evaluates metrics against rules and generates alerts
 * Handles deduplication and rate limiting
 */
public interface AlertRuleEngine {

    /**
     * Evaluate a metric and generate alerts if thresholds are exceeded
     *
     * @param metric The metric to evaluate
     * @return List of generated alerts (empty if no alerts triggered)
     */
    List<AlertVO> evaluateMetric(RealtimeMetric metric);

    /**
     * Get applicable rules for a user (personal + default)
     *
     * @param userId The user ID
     * @param metricKey The metric key
     * @return Applicable threshold rules (user threshold overrides default)
     */
    UserThreshold getApplicableThreshold(Long userId, String metricKey);

    /**
     * Check if an alert should be created (deduplication check)
     *
     * @param userId User ID
     * @param metricKey Metric key
     * @param alertType Alert type
     * @return true if alert should be created, false if duplicate
     */
    boolean shouldCreateAlert(Long userId, String metricKey, String alertType);

    /**
     * Check rate limiting (max alerts per hour)
     *
     * @param userId User ID
     * @return true if within rate limit, false if exceeded
     */
    boolean isWithinRateLimit(Long userId);
}
