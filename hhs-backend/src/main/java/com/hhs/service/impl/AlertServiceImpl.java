package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.dto.AlertVO;
import com.hhs.entity.HealthAlert;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.service.AlertService;
import com.hhs.websocket.HealthWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final HealthWebSocketHandler webSocketHandler;

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
        alert.setCreatedAt(LocalDateTime.now());
        alert.setIsRead(false);
        alert.setIsAcknowledged(false);
        healthAlertMapper.insert(alert);

        // Push to WebSocket
        webSocketHandler.sendAlertToUser(alert.getUserId(), convertToVO(alert));

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
     * @param alertType Filter by alert type (CRITICAL, WARNING, INFO)
     * @param isRead Filter by read status
     * @return Paginated alerts
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public Page<AlertVO> getUserAlerts(Long userId, int page, int size, String alertType, Boolean isRead) {
        Page<HealthAlert> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<HealthAlert> queryWrapper = new LambdaQueryWrapper<HealthAlert>()
                .eq(HealthAlert::getUserId, userId)
                .orderByDesc(HealthAlert::getCreatedAt);

        // Apply filters if provided
        if (alertType != null && !alertType.isEmpty()) {
            queryWrapper.eq(HealthAlert::getAlertType, alertType);
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
     * @return Statistics map containing total, pending, critical, warning counts
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
                .eq(HealthAlert::getAlertLevel, "CRITICAL")
        );

        Long warning = healthAlertMapper.selectCount(
            new LambdaQueryWrapper<HealthAlert>()
                .eq(HealthAlert::getUserId, userId)
                .eq(HealthAlert::getAlertLevel, "WARNING")
        );

        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("critical", critical);
        stats.put("warning", warning);
        stats.put("unread", getUnreadCount(userId));

        return stats;
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
        return vo;
    }
}
