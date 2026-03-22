package com.hhs.service.alert;

import com.hhs.dto.AlertVO;
import com.hhs.entity.HealthAlert;
import com.hhs.entity.UserThreshold;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.mapper.UserThresholdMapper;
import com.hhs.service.push.PushResult;
import com.hhs.service.push.PushStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Recovery Notifier
 * Detects when health metrics return to normal range and generates recovery notifications
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecoveryNotifier {

    private final HealthAlertMapper healthAlertMapper;
    private final UserThresholdMapper userThresholdMapper;
    private final PushStrategyService pushStrategyService;

    /**
     * Check if a metric has returned to normal range and generate recovery notification
     *
     * @param userId    the user ID
     * @param metricKey the metric key
     * @param value     the current metric value
     * @param unit      the unit
     * @return the recovery alert if generated, null otherwise
     */
    @Transactional
    public HealthAlert checkAndNotifyRecovery(Long userId, String metricKey, BigDecimal value, String unit) {
        if (value == null) {
            return null;
        }

        // Find the latest unresolved alert for this metric
        Optional<HealthAlert> lastAlertOpt = findLatestUnresolvedAlert(userId, metricKey);
        if (lastAlertOpt.isEmpty()) {
            log.debug("No unresolved alert found for user={}, metric={}", userId, metricKey);
            return null;
        }

        HealthAlert lastAlert = lastAlertOpt.get();

        // Get threshold
        UserThreshold threshold = userThresholdMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserThreshold>()
                        .eq(UserThreshold::getUserId, userId)
                        .eq(UserThreshold::getMetricKey, metricKey)
        );

        // Check if value is within normal range
        if (!isWithinNormalRange(value, threshold)) {
            log.debug("Value {} still outside normal range for user={}, metric={}", value, userId, metricKey);
            return null;
        }

        // Create recovery notification
        HealthAlert recoveryAlert = createRecoveryAlert(userId, metricKey, value, unit, lastAlert);
        healthAlertMapper.insert(recoveryAlert);

        // Mark original alert as resolved
        lastAlert.setResolvedAt(LocalDateTime.now());
        healthAlertMapper.updateById(lastAlert);

        log.info("Created recovery alert {} for user={}, metric={}, resolved alert {}",
                recoveryAlert.getId(), userId, metricKey, lastAlert.getId());

        // Push recovery notification
        pushRecoveryNotification(userId, recoveryAlert);

        return recoveryAlert;
    }

    /**
     * Find the latest unresolved alert for a user and metric
     */
    private Optional<HealthAlert> findLatestUnresolvedAlert(Long userId, String metricKey) {
        HealthAlert alert = healthAlertMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthAlert>()
                        .eq(HealthAlert::getUserId, userId)
                        .eq(HealthAlert::getMetricKey, metricKey)
                        .isNull(HealthAlert::getResolvedAt)
                        .ne(HealthAlert::getAlertType, "RECOVERY")
                        .ne(HealthAlert::getAlertType, "EARLY_WARNING")
                        .orderByDesc(HealthAlert::getCreatedAt)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(alert);
    }

    /**
     * Check if a value is within normal range based on threshold
     */
    private boolean isWithinNormalRange(BigDecimal value, UserThreshold threshold) {
        if (threshold == null) {
            // No threshold configured, consider any value as normal
            return true;
        }

        BigDecimal warningLow = threshold.getWarningLow();
        BigDecimal warningHigh = threshold.getWarningHigh();

        // Check against warning thresholds (more lenient than critical)
        boolean aboveLow = warningLow == null || value.compareTo(warningLow) > 0;
        boolean belowHigh = warningHigh == null || value.compareTo(warningHigh) < 0;

        return aboveLow && belowHigh;
    }

    /**
     * Create a recovery alert
     */
    private HealthAlert createRecoveryAlert(Long userId, String metricKey, BigDecimal value,
                                            String unit, HealthAlert originalAlert) {
        String metricLabel = getMetricLabel(metricKey);

        HealthAlert alert = new HealthAlert();
        alert.setUserId(userId);
        alert.setAlertType("RECOVERY");
        alert.setAlertLevel("LOW");
        alert.setTitle(String.format("%s恢复正常", metricLabel));
        alert.setMessage(buildRecoveryMessage(originalAlert, value, metricLabel));
        alert.setMetricKey(metricKey);
        alert.setCurrentValue(value);
        alert.setThresholdValue(originalAlert.getThresholdValue());
        alert.setSuggestion(String.format("您的%s已恢复到正常范围内，请继续保持健康的生活方式", metricLabel));
        alert.setOccurrenceCount(1);
        alert.setLastOccurrenceAt(LocalDateTime.now());
        alert.setIsRead(false);
        alert.setIsAcknowledged(false);
        alert.setCreatedAt(LocalDateTime.now());

        return alert;
    }

    /**
     * Build recovery message
     */
    private String buildRecoveryMessage(HealthAlert originalAlert, BigDecimal currentValue, String metricLabel) {
        return String.format("您的%s已从%s恢复到正常范围(当前值: %s)。",
                metricLabel,
                originalAlert.getCurrentValue() != null ? originalAlert.getCurrentValue() : "异常值",
                currentValue);
    }

    /**
     * Push recovery notification asynchronously
     */
    @Async("pushExecutor")
    public void pushRecoveryNotification(Long userId, HealthAlert alert) {
        try {
            AlertVO alertVO = convertToVO(alert);
            List<PushResult> results = pushStrategyService.pushWithStrategy(userId, alertVO);
            log.debug("Recovery notification push results: {}", results);
        } catch (Exception e) {
            log.error("Failed to push recovery notification: {}", e.getMessage());
        }
    }

    /**
     * Convert HealthAlert to AlertVO
     */
    private AlertVO convertToVO(HealthAlert alert) {
        AlertVO vo = new AlertVO();
        vo.setId(alert.getId());
        vo.setAlertType(alert.getAlertType());
        vo.setAlertLevel(alert.getAlertLevel());
        vo.setTitle(alert.getTitle());
        vo.setMessage(alert.getMessage());
        vo.setMetricKey(alert.getMetricKey());
        vo.setCurrentValue(alert.getCurrentValue());
        vo.setThresholdValue(alert.getThresholdValue());
        vo.setSuggestion(alert.getSuggestion());
        return vo;
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