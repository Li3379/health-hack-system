package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.domain.event.AlertCreatedEvent;
import com.hhs.dto.AlertVO;
import com.hhs.entity.HealthAlert;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.service.AlertService;
import com.hhs.service.domain.MetricDisplayFormatter;
import com.hhs.service.domain.MetricKeyNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Alert Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final HealthAlertMapper healthAlertMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final MetricDisplayFormatter displayFormatter;

    /**
     * Create a new health alert
     *
     * @param alert The alert to create
     * @return Created alert ID
     * @throws BusinessException if validation fails
     */
    @Override
    @Transactional(timeout = 30)
    public Long createAlert(HealthAlert alert) {
        // Write side for the suggestion column. Until this ran, nothing ever assigned it, so
        // health_alert.suggestion stayed NULL and every push channel fell through to its
        // "请关注您的健康状况" default. Only fills when the caller left it empty — a real
        // suggestion (once an AI/template writer exists) must win.
        if (alert.getSuggestion() == null || alert.getSuggestion().isBlank()) {
            alert.setSuggestion(defaultSuggestionFor(alert.getMetricKey()));
        }
        alert.setCreatedAt(LocalDateTime.now());
        alert.setIsRead(false);
        alert.setIsAcknowledged(false);
        healthAlertMapper.insert(alert);

        // Push is published, not called: AlertPushListener fans the alert out to every
        // configured channel after this transaction commits. Sending here as well would
        // double-deliver the frame to online users (WebSocket is one of those channels).
        eventPublisher.publishEvent(new AlertCreatedEvent(alert.getUserId(), convertToVO(alert)));

        log.info("Created alert: userId={}, type={}, metricKey={}",
            alert.getUserId(), alert.getAlertType(), alert.getMetricKey());

        return alert.getId();
    }

    /**
     * Get user's alerts with pagination and optional filters
     *
     * @param userId User ID
     * @param page Page number
     * @param size Page size
     * @param alertType Filter by alert type (CRITICAL, WARNING, NORMAL)
     * @param alertLevel Filter by alert level (HIGH, MEDIUM, LOW)
     * @param isRead Filter by read status
     * @return Paginated alerts
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public Page<AlertVO> getUserAlerts(Long userId, int page, int size, String alertType, String alertLevel, Boolean isRead) {
        Page<HealthAlert> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<HealthAlert> queryWrapper = new LambdaQueryWrapper<HealthAlert>()
            .eq(HealthAlert::getUserId, userId)
            .orderByDesc(HealthAlert::getCreatedAt);

        // Apply filters if provided
        if (alertType != null && !alertType.isEmpty()) {
            queryWrapper.eq(HealthAlert::getAlertType, alertType);
        }
        if (alertLevel != null && !alertLevel.isEmpty()) {
            queryWrapper.eq(HealthAlert::getAlertLevel, alertLevel);
        }
        if (isRead != null) {
            queryWrapper.eq(HealthAlert::getIsRead, isRead);
        }

        Page<HealthAlert> result = healthAlertMapper.selectPage(pageParam, queryWrapper);

        // Convert to VO
        Page<AlertVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<AlertVO> records = result.getRecords().stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());
        voPage.setRecords(records);

        return voPage;
    }

    /**
     * Get unread alerts count
     *
     * @param userId User ID
     * @return Unread count
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public Long getUnreadCount(Long userId) {
        return healthAlertMapper.getUnreadCount(userId);
    }

    /**
     * Mark alert as read
     *
     * @param alertId Alert ID
     * @param userId User ID (for authorization)
     * @return true if successful
     */
    @Override
    @Transactional(timeout = 30)
    public Boolean markAsRead(Long alertId, Long userId) {
        int updated = healthAlertMapper.update(null,
            new LambdaUpdateWrapper<HealthAlert>()
                .eq(HealthAlert::getId, alertId)
                .eq(HealthAlert::getUserId, userId)
                .set(HealthAlert::getIsRead, true)
        );
        return updated > 0;
    }

    /**
     * Acknowledge an alert
     *
     * @param alertId Alert ID
     * @param userId User ID (for authorization)
     * @return true if successful
     */
    @Override
    @Transactional(timeout = 30)
    public Boolean acknowledgeAlert(Long alertId, Long userId) {
        int updated = healthAlertMapper.update(null,
            new LambdaUpdateWrapper<HealthAlert>()
                .eq(HealthAlert::getId, alertId)
                .eq(HealthAlert::getUserId, userId)
                .set(HealthAlert::getIsAcknowledged, true)
                .set(HealthAlert::getAcknowledgedAt, LocalDateTime.now())
        );
        return updated > 0;
    }

    /**
     * Mark all alerts as read for a user
     *
     * @param userId User ID
     * @return Number of alerts marked
     */
    @Override
    @Transactional(timeout = 30)
    public Integer markAllAsRead(Long userId) {
        return healthAlertMapper.update(null,
            new LambdaUpdateWrapper<HealthAlert>()
                .eq(HealthAlert::getUserId, userId)
                .set(HealthAlert::getIsRead, true)
        );
    }

    /**
     * Get recent alerts for dashboard
     *
     * @param userId User ID
     * @param limit Max number of alerts
     * @return Recent alerts
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public List<AlertVO> getRecentAlerts(Long userId, int limit) {
        List<HealthAlert> alerts = healthAlertMapper.getRecentAlerts(userId, limit);
        return alerts.stream()
            .map(this::convertToVO)
            .collect(Collectors.toList());
    }

    /**
     * Get alert statistics for a user
     *
     * @param userId User ID
     * @return Statistics map containing total, pending, critical, warning, normal counts
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public java.util.Map<String, Object> getStatistics(Long userId) {
        Long total = healthAlertMapper.selectCount(
            new LambdaQueryWrapper<HealthAlert>().eq(HealthAlert::getUserId, userId)
        );

        Long pending = healthAlertMapper.selectCount(
            new LambdaQueryWrapper<HealthAlert>()
                .eq(HealthAlert::getUserId, userId)
                .eq(HealthAlert::getIsAcknowledged, false)
        );

        Long critical = healthAlertMapper.selectCount(
            new LambdaQueryWrapper<HealthAlert>()
                .eq(HealthAlert::getUserId, userId)
                .eq(HealthAlert::getAlertType, "CRITICAL")
        );

        Long warning = healthAlertMapper.selectCount(
            new LambdaQueryWrapper<HealthAlert>()
                .eq(HealthAlert::getUserId, userId)
                .eq(HealthAlert::getAlertType, "WARNING")
        );

        Long normal = healthAlertMapper.selectCount(
            new LambdaQueryWrapper<HealthAlert>()
                .eq(HealthAlert::getUserId, userId)
                .eq(HealthAlert::getAlertType, "NORMAL")
        );

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("critical", critical);
        stats.put("warning", warning);
        stats.put("normal", normal);
        stats.put("unread", getUnreadCount(userId));

        return stats;
    }

    /**
     * Delete an alert
     *
     * @param alertId Alert ID
     * @param userId User ID (for authorization)
     * @return true if successful
     */
    @Override
    @Transactional(timeout = 30)
    public Boolean deleteAlert(Long alertId, Long userId) {
        int deleted = healthAlertMapper.delete(
            new LambdaQueryWrapper<HealthAlert>()
                .eq(HealthAlert::getId, alertId)
                .eq(HealthAlert::getUserId, userId)
        );
        if (deleted > 0) {
            log.info("Deleted alert: alertId={}, userId={}", alertId, userId);
        } else {
            log.warn("Alert not found or not owned by user: alertId={}, userId={}", alertId, userId);
        }
        return deleted > 0;
    }

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
        vo.setIsRead(alert.getIsRead());
        vo.setIsAcknowledged(alert.getIsAcknowledged());
        vo.setAcknowledgedAt(alert.getAcknowledgedAt());
        vo.setCreatedAt(alert.getCreatedAt());
        vo.setAiAnalysis(alert.getAiAnalysis());
        vo.setSuggestion(alert.getSuggestion());
        return vo;
    }

    /**
     * Build a generic follow-up suggestion for an alert that arrived without one.
     *
     * <p>Deliberately returns {@code null} for an unknown metric key. {@link
     * MetricDisplayFormatter#getDisplayName(String)} passes unrecognised keys through verbatim,
     * so naively interpolating it would emit "请复查 heartRateXyz 并咨询医生" — an English
     * identifier leaking into user-facing Chinese copy. A null suggestion keeps the push
     * channels on their existing fallback text instead.</p>
     *
     * @param metricKey the alert's metric key, may be null
     * @return the suggestion sentence, or null when the key is not a recognised metric
     */
    private String defaultSuggestionFor(String metricKey) {
        if (metricKey == null || metricKey.isBlank()) {
            return null;
        }
        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(metricKey);
        if (!MetricKeyNormalizer.isCanonicalKey(canonicalKey)) {
            return null;
        }
        return "请复查" + displayFormatter.getDisplayName(canonicalKey) + "并咨询医生";
    }
}
