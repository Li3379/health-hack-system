package com.hhs.service.impl;

import com.hhs.dto.AlertVO;
import com.hhs.entity.HealthAlert;
import com.hhs.entity.RealtimeMetric;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.service.AlertRuleEngine;
import com.hhs.service.domain.AlertRateLimiter;
import com.hhs.service.domain.ThresholdEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alert Rule Engine Implementation
 * Orchestrates alert evaluation using decomposed components
 * Checks rate limits, evaluates thresholds, and prevents duplicate alerts
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleEngineImpl implements AlertRuleEngine {

    private final HealthAlertMapper healthAlertMapper;
    private final AlertRateLimiter rateLimiter;
    private final ThresholdEvaluator thresholdEvaluator;

    private static final int DEDUPLICATION_WINDOW_HOURS = 1;

    @Override
    public List<AlertVO> evaluateMetric(RealtimeMetric metric) {
        List<AlertVO> alerts = new ArrayList<>();

        // Check rate limiting first
        if (!rateLimiter.isWithinRateLimit(metric.getUserId())) {
            log.warn("Rate limit exceeded for user: {}", metric.getUserId());
            return alerts;
        }

        // Evaluate threshold and get alert details
        AlertVO alert = thresholdEvaluator.evaluate(
                metric.getUserId(),
                metric.getMetricKey(),
                metric.getValue()
        );

        if (alert != null) {
            // Check deduplication before creating alert
            if (shouldCreateAlert(metric.getUserId(), metric.getMetricKey(), alert.getAlertType())) {
                alerts.add(alert);
            }
        }

        return alerts;
    }

    @Override
    public com.hhs.entity.UserThreshold getApplicableThreshold(Long userId, String metricKey) {
        return thresholdEvaluator.getThresholdForUser(userId, metricKey);
    }

    @Override
    public boolean shouldCreateAlert(Long userId, String metricKey, String alertType) {
        LocalDateTime since = LocalDateTime.now().minusHours(DEDUPLICATION_WINDOW_HOURS);
        HealthAlert recentAlert = healthAlertMapper.findRecentSimilarAlert(userId, metricKey, alertType, since);
        return recentAlert == null;
    }

    @Override
    public boolean isWithinRateLimit(Long userId) {
        return rateLimiter.isWithinRateLimit(userId);
    }
}
