package com.hhs.service.alert;

import com.hhs.dto.AlertVO;
import com.hhs.entity.AlertTemplate;
import com.hhs.entity.HealthAlert;
import com.hhs.entity.UserThreshold;
import com.hhs.mapper.AlertRuleMapper;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.mapper.UserThresholdMapper;
import com.hhs.service.push.PushResult;
import com.hhs.service.push.PushStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Intelligent alert service that integrates template matching, AI analysis, and multi-channel push
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntelligentAlertService {

    private final AlertTemplateService alertTemplateService;
    private final AlertAIAnalysisService alertAIAnalysisService;
    private final PushStrategyService pushStrategyService;
    private final HealthAlertMapper healthAlertMapper;
    private final UserThresholdMapper userThresholdMapper;
    private final AlertRuleMapper alertRuleMapper;
    private final TrendPredictor trendPredictor;
    private final AlertDeduplicator alertDeduplicator;
    private final AlertFrequencyStrategy alertFrequencyStrategy;
    private final RecoveryNotifier recoveryNotifier;

    /**
     * Process a health metric and generate intelligent alert if needed
     *
     * @param userId     the user ID
     * @param metricKey  the metric type
     * @param value      the metric value
     * @param unit       the unit
     * @return the generated alert, or null if no alert needed
     */
    @Transactional
    public HealthAlert processMetricAndAlert(Long userId, String metricKey, BigDecimal value, String unit) {
        // Get threshold for user
        BigDecimal thresholdHigh = getThresholdHigh(userId, metricKey);
        BigDecimal thresholdLow = getThresholdLow(userId, metricKey);

        // Check if alert is needed
        if (!isAlertNeeded(value, thresholdHigh, thresholdLow)) {
            // Check for recovery notification
            HealthAlert recoveryAlert = recoveryNotifier.checkAndNotifyRecovery(userId, metricKey, value, unit);
            if (recoveryAlert != null) {
                return recoveryAlert;
            }

            // Check for early warning based on trend prediction
            HealthAlert earlyWarning = checkAndGenerateEarlyWarning(userId, metricKey, value, unit);
            if (earlyWarning != null) {
                return earlyWarning;
            }
            log.debug("No alert needed for user {}, metric {}, value {}", userId, metricKey, value);
            return null;
        }

        // Check for similar recent alert (deduplication with value deviation)
        Optional<HealthAlert> recentAlert = findRecentSimilarAlert(userId, metricKey);
        if (recentAlert.isPresent()) {
            HealthAlert existing = recentAlert.get();
            // Use enhanced deduplicator to check if should merge
            if (alertDeduplicator.shouldMerge(existing, value, determineAlertLevel(value, thresholdHigh, thresholdLow))) {
                log.info("Merging alert for user {}, metric {}, value {}", userId, metricKey, value);
                return alertDeduplicator.merge(existing, value);
            }
        }

        // Find matching template
        Optional<AlertTemplate> templateOpt = alertTemplateService.findMatchingTemplate(
                metricKey, value, thresholdHigh, thresholdLow);

        AlertVO alertVO;
        if (templateOpt.isPresent()) {
            // Generate alert from template
            alertVO = alertTemplateService.generateAlertFromTemplate(
                    templateOpt.get(), value, thresholdHigh != null ? thresholdHigh : thresholdLow);
            alertVO.setMetricKey(metricKey);
        } else {
            // Generate basic alert
            alertVO = generateBasicAlert(metricKey, value, thresholdHigh, thresholdLow);
        }

        // Check if AI analysis is needed
        int recentAlertCount = countRecentAlerts(userId, metricKey);
        if (alertTemplateService.needsAIAnalysis(userId, metricKey, alertVO.getAlertLevel(), recentAlertCount)) {
            alertVO = alertAIAnalysisService.enhanceWithAIAnalysis(userId, alertVO);
        }

        // Create alert entity
        HealthAlert alert = createAlertEntity(userId, alertVO, unit);

        // Save alert
        healthAlertMapper.insert(alert);
        log.info("Created alert {} for user {}, metric {}, level {}",
                alert.getId(), userId, metricKey, alert.getAlertLevel());

        // Set the ID for the VO
        alertVO.setId(alert.getId());

        // Check frequency strategy before pushing
        if (alertFrequencyStrategy.shouldPush(alert)) {
            pushAlertAsync(userId, alertVO);
        } else {
            log.info("Push skipped due to frequency limit for user {}, metric {}", userId, metricKey);
        }

        return alert;
    }

    /**
     * Determine alert level based on value and thresholds
     */
    private String determineAlertLevel(BigDecimal value, BigDecimal thresholdHigh, BigDecimal thresholdLow) {
        if (value == null) {
            return "MEDIUM";
        }

        BigDecimal threshold = null;
        boolean isHigh = false;

        if (thresholdHigh != null && value.compareTo(thresholdHigh) >= 0) {
            threshold = thresholdHigh;
            isHigh = true;
        } else if (thresholdLow != null && value.compareTo(thresholdLow) <= 0) {
            threshold = thresholdLow;
            isHigh = false;
        }

        if (threshold == null) {
            return "MEDIUM";
        }

        BigDecimal diff = isHigh ? value.subtract(threshold) : threshold.subtract(value);
        BigDecimal percentage = diff.divide(threshold.abs(), 2, BigDecimal.ROUND_HALF_UP)
                .multiply(new BigDecimal("100"));

        return percentage.compareTo(new BigDecimal("20")) >= 0 ? "HIGH" : "MEDIUM";
    }

    /**
     * Check for early warning based on trend prediction
     * Generates an early warning alert if the predicted value is approaching threshold
     *
     * @param userId    the user ID
     * @param metricKey the metric key
     * @param value     the current value
     * @param unit      the unit
     * @return the generated early warning alert, or null if not needed
     */
    private HealthAlert checkAndGenerateEarlyWarning(Long userId, String metricKey, BigDecimal value, String unit) {
        try {
            TrendResult trendResult = trendPredictor.predict(userId, metricKey);

            if (!trendResult.hasSufficientData() || !Boolean.TRUE.equals(trendResult.earlyWarningNeeded())) {
                return null;
            }

            // Check if there's already a recent early warning for this metric
            Optional<HealthAlert> recentEarlyWarning = findRecentEarlyWarning(userId, metricKey);
            if (recentEarlyWarning.isPresent()) {
                log.debug("Recent early warning already exists for user {}, metric {}", userId, metricKey);
                return null;
            }

            // Generate early warning alert
            AlertVO alertVO = generateEarlyWarningAlert(metricKey, value, trendResult);
            HealthAlert alert = createAlertEntity(userId, alertVO, unit);
            alert.setAlertType("EARLY_WARNING");
            alert.setAlertLevel("LOW");

            healthAlertMapper.insert(alert);
            log.info("Created early warning alert {} for user {}, metric {}, trend {}",
                    alert.getId(), userId, metricKey, trendResult.trend());

            alertVO.setId(alert.getId());
            alertVO.setAlertType("EARLY_WARNING");
            alertVO.setAlertLevel("LOW");

            pushAlertAsync(userId, alertVO);

            return alert;
        } catch (Exception e) {
            log.warn("Failed to check early warning for user {}, metric {}: {}",
                    userId, metricKey, e.getMessage());
            return null;
        }
    }

    /**
     * Find recent early warning for a metric (within 24 hours)
     */
    private Optional<HealthAlert> findRecentEarlyWarning(Long userId, String metricKey) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);

        HealthAlert recentAlert = healthAlertMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthAlert>()
                        .eq(HealthAlert::getUserId, userId)
                        .eq(HealthAlert::getMetricKey, metricKey)
                        .eq(HealthAlert::getAlertType, "EARLY_WARNING")
                        .ge(HealthAlert::getCreatedAt, oneDayAgo)
                        .orderByDesc(HealthAlert::getCreatedAt)
                        .last("LIMIT 1"));

        return Optional.ofNullable(recentAlert);
    }

    /**
     * Generate early warning alert based on trend prediction
     */
    private AlertVO generateEarlyWarningAlert(String metricKey, BigDecimal currentValue, TrendResult trendResult) {
        AlertVO alert = new AlertVO();

        String metricLabel = getMetricLabel(metricKey);
        String trendLabel = trendResult.trend() != null ? trendResult.trend().getLabel() : "未知";

        alert.setMetricKey(metricKey);
        alert.setCurrentValue(currentValue);
        alert.setThresholdValue(trendResult.thresholdValue());
        alert.setTitle(String.format("%s趋势预警", metricLabel));
        alert.setMessage(String.format("您的%s近期呈%s趋势，已接近预警线(%.0f%%)，建议提前关注",
                metricLabel, trendLabel, trendResult.distanceToThreshold() * 100));
        alert.setSuggestion(String.format("建议持续监测%s变化，如有异常及时就医", metricLabel));
        alert.setAlertType("EARLY_WARNING");
        alert.setAlertLevel("LOW");

        return alert;
    }

    /**
     * Push alert asynchronously through configured channels
     */
    @Async("pushExecutor")
    public void pushAlertAsync(Long userId, AlertVO alert) {
        try {
            List<PushResult> results = pushStrategyService.pushWithStrategy(userId, alert);
            log.debug("Push results for alert {}: {}", alert.getId(), results);

            // Update alert with push channels
            updatePushChannels(alert.getId(), results);
        } catch (Exception e) {
            log.error("Failed to push alert {}: {}", alert.getId(), e.getMessage());
        }
    }

    /**
     * Get high threshold for user (user-specific or default)
     */
    private BigDecimal getThresholdHigh(Long userId, String metricKey) {
        // Check user-specific threshold first
        UserThreshold userThreshold = userThresholdMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserThreshold>()
                        .eq(UserThreshold::getUserId, userId)
                        .eq(UserThreshold::getMetricKey, metricKey));

        if (userThreshold != null && userThreshold.getCriticalHigh() != null) {
            return userThreshold.getCriticalHigh();
        }
        if (userThreshold != null && userThreshold.getWarningHigh() != null) {
            return userThreshold.getWarningHigh();
        }

        // Fall back to default rule
        var rule = alertRuleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hhs.entity.AlertRule>()
                        .eq(com.hhs.entity.AlertRule::getMetricKey, metricKey)
                        .eq(com.hhs.entity.AlertRule::getEnabled, true));

        if (rule != null) {
            return rule.getCriticalHigh() != null ? rule.getCriticalHigh() : rule.getWarningHigh();
        }

        return null;
    }

    /**
     * Get low threshold for user (user-specific or default)
     */
    private BigDecimal getThresholdLow(Long userId, String metricKey) {
        // Check user-specific threshold first
        UserThreshold userThreshold = userThresholdMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserThreshold>()
                        .eq(UserThreshold::getUserId, userId)
                        .eq(UserThreshold::getMetricKey, metricKey));

        if (userThreshold != null && userThreshold.getCriticalLow() != null) {
            return userThreshold.getCriticalLow();
        }
        if (userThreshold != null && userThreshold.getWarningLow() != null) {
            return userThreshold.getWarningLow();
        }

        // Fall back to default rule
        var rule = alertRuleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.hhs.entity.AlertRule>()
                        .eq(com.hhs.entity.AlertRule::getMetricKey, metricKey)
                        .eq(com.hhs.entity.AlertRule::getEnabled, true));

        if (rule != null) {
            return rule.getCriticalLow() != null ? rule.getCriticalLow() : rule.getWarningLow();
        }

        return null;
    }

    /**
     * Check if alert is needed based on value vs thresholds
     */
    private boolean isAlertNeeded(BigDecimal value, BigDecimal thresholdHigh, BigDecimal thresholdLow) {
        if (value == null) {
            return false;
        }

        if (thresholdHigh != null && value.compareTo(thresholdHigh) >= 0) {
            return true;
        }

        if (thresholdLow != null && value.compareTo(thresholdLow) <= 0) {
            return true;
        }

        return false;
    }

    /**
     * Find recent similar alert for deduplication
     */
    private Optional<HealthAlert> findRecentSimilarAlert(Long userId, String metricKey) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        HealthAlert recentAlert = healthAlertMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthAlert>()
                        .eq(HealthAlert::getUserId, userId)
                        .eq(HealthAlert::getMetricKey, metricKey)
                        .ge(HealthAlert::getCreatedAt, oneHourAgo)
                        .isNull(HealthAlert::getResolvedAt)
                        .orderByDesc(HealthAlert::getCreatedAt)
                        .last("LIMIT 1"));

        return Optional.ofNullable(recentAlert);
    }

    /**
     * Count recent alerts for this metric
     */
    private int countRecentAlerts(Long userId, String metricKey) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        Long count = healthAlertMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthAlert>()
                        .eq(HealthAlert::getUserId, userId)
                        .eq(HealthAlert::getMetricKey, metricKey)
                        .ge(HealthAlert::getCreatedAt, oneHourAgo));

        return count != null ? count.intValue() : 0;
    }

    /**
     * Generate basic alert when no template is available
     */
    private AlertVO generateBasicAlert(String metricKey, BigDecimal value,
                                        BigDecimal thresholdHigh, BigDecimal thresholdLow) {
        AlertVO alert = new AlertVO();

        boolean isHigh = thresholdHigh != null && value.compareTo(thresholdHigh) >= 0;
        boolean isLow = thresholdLow != null && value.compareTo(thresholdLow) <= 0;
        BigDecimal threshold = isHigh ? thresholdHigh : thresholdLow;

        String metricLabel = getMetricLabel(metricKey);
        String direction = isHigh ? "偏高" : "偏低";

        alert.setMetricKey(metricKey);
        alert.setCurrentValue(value);
        alert.setThresholdValue(threshold);
        alert.setTitle(String.format("%s%s", metricLabel, direction));
        alert.setMessage(String.format("您的%s为%s，超过正常范围", metricLabel, value));

        // Determine alert level based on how much the value exceeds threshold
        if (threshold != null) {
            BigDecimal diff = isHigh
                    ? value.subtract(threshold)
                    : threshold.subtract(value);
            BigDecimal percentage = diff.divide(threshold.abs(), 2, BigDecimal.ROUND_HALF_UP)
                    .multiply(new BigDecimal("100"));

            if (percentage.compareTo(new BigDecimal("20")) >= 0) {
                alert.setAlertType("CRITICAL");
                alert.setAlertLevel("HIGH");
            } else {
                alert.setAlertType("WARNING");
                alert.setAlertLevel("MEDIUM");
            }
        } else {
            alert.setAlertType("WARNING");
            alert.setAlertLevel("MEDIUM");
        }

        return alert;
    }

    /**
     * Create alert entity from VO
     */
    private HealthAlert createAlertEntity(Long userId, AlertVO alertVO, String unit) {
        HealthAlert alert = new HealthAlert();
        alert.setUserId(userId);
        alert.setAlertType(alertVO.getAlertType());
        alert.setAlertLevel(alertVO.getAlertLevel());
        alert.setTitle(alertVO.getTitle());
        alert.setMessage(alertVO.getMessage());
        alert.setMetricKey(alertVO.getMetricKey());
        alert.setCurrentValue(alertVO.getCurrentValue());
        alert.setThresholdValue(alertVO.getThresholdValue());
        alert.setSuggestion(alertVO.getSuggestion());
        alert.setAiAnalysis(alertVO.getAiAnalysis());
        alert.setOccurrenceCount(1);
        alert.setLastOccurrenceAt(LocalDateTime.now());
        alert.setIsRead(false);
        alert.setIsAcknowledged(false);
        alert.setCreatedAt(LocalDateTime.now());
        return alert;
    }

    /**
     * Update alert with push channels
     */
    private void updatePushChannels(Long alertId, List<PushResult> results) {
        if (results == null || results.isEmpty()) {
            return;
        }

        // Collect successful channels
        List<String> successChannels = results.stream()
                .filter(PushResult::isSuccess)
                .map(r -> r.getChannelType().getCode())
                .toList();

        if (!successChannels.isEmpty()) {
            try {
                String channelsJson = "[" + String.join(",",
                        successChannels.stream().map(c -> "\"" + c + "\"").toList()) + "]";

                HealthAlert update = new HealthAlert();
                update.setId(alertId);
                update.setPushChannels(channelsJson);
                healthAlertMapper.updateById(update);
            } catch (Exception e) {
                log.warn("Failed to update push channels for alert {}: {}", alertId, e.getMessage());
            }
        }
    }

    /**
     * Get metric label in Chinese
     */
    private String getMetricLabel(String metricKey) {
        return switch (metricKey) {
            case "heartRate" -> "心率";
            case "systolicBP" -> "收缩压";
            case "diastolicBP" -> "舒张压";
            case "glucose" -> "血糖";
            case "temperature" -> "体温";
            case "bmi" -> "BMI";
            case "weight" -> "体重";
            default -> metricKey;
        };
    }
}