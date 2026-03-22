package com.hhs.service.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.dto.AlertVO;
import com.hhs.entity.AlertRule;
import com.hhs.entity.UserThreshold;
import com.hhs.mapper.AlertRuleMapper;
import com.hhs.mapper.UserThresholdMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Threshold evaluation orchestrator.
 * Coordinates threshold lookup, comparison, range checking, and alert generation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdEvaluator {

    private final UserThresholdMapper userThresholdMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final ThresholdComparator comparator;
    private final ThresholdRangeChecker rangeChecker;
    private final MetricDisplayFormatter displayFormatter;

    /**
     * Evaluate metric against applicable thresholds.
     *
     * @param userId the user ID
     * @param metricKey the metric key
     * @param value the metric value
     * @return AlertVO if threshold exceeded, null otherwise
     */
    public AlertVO evaluate(Long userId, String metricKey, BigDecimal value) {
        UserThreshold threshold = getThresholdForUser(userId, metricKey);
        if (threshold == null) {
            log.debug("No threshold configured for metric: {}", metricKey);
            return null;
        }

        return evaluateValue(metricKey, value, threshold);
    }

    /**
     * Get applicable threshold for user (personalized or default).
     *
     * @param userId the user ID
     * @param metricKey the metric key
     * @return UserThreshold or null if none configured
     */
    public UserThreshold getThresholdForUser(Long userId, String metricKey) {
        // First check for user's personalized threshold
        List<UserThreshold> userThresholds = userThresholdMapper.selectList(
                new LambdaQueryWrapper<UserThreshold>()
                        .eq(UserThreshold::getUserId, userId)
                        .eq(UserThreshold::getMetricKey, metricKey)
        );

        if (!userThresholds.isEmpty()) {
            return userThresholds.get(0);
        }

        // Fall back to default alert rule
        List<AlertRule> rules = alertRuleMapper.selectList(
                new LambdaQueryWrapper<AlertRule>()
                        .eq(AlertRule::getMetricKey, metricKey)
                        .eq(AlertRule::getEnabled, true)
        );

        if (rules.isEmpty()) {
            return null;
        }

        return convertToUserThreshold(rules.get(0));
    }

    /**
     * Evaluate value against threshold and generate alert if exceeded.
     *
     * @param metricKey the metric key
     * @param value the metric value
     * @param threshold the threshold to compare against
     * @return AlertVO with evaluation details, or null if within normal range
     */
    public AlertVO evaluateValue(String metricKey, BigDecimal value, UserThreshold threshold) {
        // Check if value exceeds any threshold using range checker
        if (rangeChecker.isNormalRange(value.doubleValue(), threshold)) {
            return null;
        }

        // Determine alert details
        String severityLevel = rangeChecker.getSeverityLevel(value.doubleValue(), threshold);
        String alertLevel = rangeChecker.getAlertLevel(severityLevel);

        // Determine direction (high or low)
        String direction = comparator.isHigh(value.doubleValue(), threshold) ? "HIGH" : "LOW";

        // Find which threshold was exceeded
        BigDecimal exceededValue = findExceededThreshold(value, threshold, direction);

        return createAlert(metricKey, value, exceededValue, severityLevel, alertLevel, direction);
    }

    /**
     * Find the specific threshold value that was exceeded.
     */
    private BigDecimal findExceededThreshold(BigDecimal value, UserThreshold threshold, String direction) {
        if ("HIGH".equals(direction)) {
            // Prefer critical high, fall back to warning high
            if (threshold.getCriticalHigh() != null && value.compareTo(threshold.getCriticalHigh()) >= 0) {
                return threshold.getCriticalHigh();
            }
            return threshold.getWarningHigh();
        } else {
            // Prefer critical low, fall back to warning low
            if (threshold.getCriticalLow() != null && value.compareTo(threshold.getCriticalLow()) <= 0) {
                return threshold.getCriticalLow();
            }
            return threshold.getWarningLow();
        }
    }

    /**
     * Create alert VO from threshold evaluation.
     */
    private AlertVO createAlert(String metricKey, BigDecimal value,
                                BigDecimal thresholdValue, String alertType,
                                String alertLevel, String direction) {
        AlertVO alert = new AlertVO();
        alert.setAlertType(alertType);
        alert.setAlertLevel(alertLevel);
        alert.setTitle(formatTitle(metricKey, alertType, direction));
        alert.setMessage(displayFormatter.formatAlertMessage(
                metricKey, value, thresholdValue, alertType, direction));
        alert.setMetricKey(metricKey);
        alert.setCurrentValue(value);
        alert.setThresholdValue(thresholdValue);
        alert.setIsRead(false);
        alert.setIsAcknowledged(false);
        return alert;
    }

    /**
     * Format alert title.
     */
    private String formatTitle(String metricKey, String alertType, String direction) {
        String displayName = displayFormatter.getDisplayName(metricKey);
        String severity = "CRITICAL".equals(alertType) ? "严重警告" : "警告";
        String dirText = "HIGH".equals(direction) ? "过高" : "偏低";
        return severity + ": " + displayName + dirText;
    }

    /**
     * Convert AlertRule to UserThreshold format.
     */
    private UserThreshold convertToUserThreshold(AlertRule rule) {
        UserThreshold threshold = new UserThreshold();
        threshold.setMetricKey(rule.getMetricKey());
        threshold.setWarningHigh(rule.getWarningHigh());
        threshold.setCriticalHigh(rule.getCriticalHigh());
        threshold.setWarningLow(rule.getWarningLow());
        threshold.setCriticalLow(rule.getCriticalLow());
        return threshold;
    }
}
