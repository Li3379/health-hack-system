package com.hhs.service;

import com.hhs.dto.AlertVO;
import com.hhs.vo.RealtimeMetricVO;
import com.hhs.entity.HealthAlert;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * Alert Service
 * Manages health alerts and notifications
 */
public interface AlertService {

    /**
     * Create a new alert
     *
     * @param alert The alert to create
     * @return Created alert ID
     */
    Long createAlert(HealthAlert alert);

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
    Page<AlertVO> getUserAlerts(Long userId, int page, int size, String alertType, Boolean isRead);

    /**
     * Get unread alerts count
     *
     * @param userId User ID
     * @return Unread count
     */
    Long getUnreadCount(Long userId);

    /**
     * Mark alert as read
     *
     * @param alertId Alert ID
     * @param userId User ID (for authorization)
     * @return true if successful
     */
    Boolean markAsRead(Long alertId, Long userId);

    /**
     * Acknowledge an alert
     *
     * @param alertId Alert ID
     * @param userId User ID (for authorization)
     * @return true if successful
     */
    Boolean acknowledgeAlert(Long alertId, Long userId);

    /**
     * Mark all alerts as read for a user
     *
     * @param userId User ID
     * @return Number of alerts marked
     */
    Integer markAllAsRead(Long userId);

    /**
     * Get recent alerts for dashboard
     *
     * @param userId User ID
     * @param limit Max number of alerts
     * @return Recent alerts
     */
    java.util.List<AlertVO> getRecentAlerts(Long userId, int limit);

    /**
     * Get alert statistics for a user
     *
     * @param userId User ID
     * @return Statistics map containing total, pending, critical, warning counts
     */
    java.util.Map<String, Object> getStatistics(Long userId);

    /**
     * Delete an alert
     *
     * @param alertId Alert ID
     * @param userId User ID (for authorization)
     * @return true if successful
     */
    Boolean deleteAlert(Long alertId, Long userId);
}
