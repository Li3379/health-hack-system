package com.hhs.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.dto.AlertVO;
import com.hhs.common.Result;
import com.hhs.security.SecurityUtils;
import com.hhs.service.AlertService;
import com.hhs.service.alert.TrendPredictor;
import com.hhs.service.alert.TrendResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Alert Controller
 * Handles health alert operations
 */
@Slf4j
@Tag(name = "Alert Management", description = "健康预警管理")
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;
    private final TrendPredictor trendPredictor;

    @Operation(summary = "Get user's alerts with pagination")
    @GetMapping
    public Result<Page<AlertVO>> getUserAlerts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) Boolean isRead) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get alerts request: userId={}, page={}, size={}", userId, page, size);
        Page<AlertVO> alerts = alertService.getUserAlerts(userId, page, size, alertType, isRead);
        return Result.success(alerts);
    }

    @Operation(summary = "Get unread alerts count")
    @GetMapping("/unread-count")
    public Result<Long> getUnreadCount() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get unread count request: userId={}", userId);
        Long count = alertService.getUnreadCount(userId);
        return Result.success(count);
    }

    @Operation(summary = "Get recent alerts for dashboard")
    @GetMapping("/recent")
    public Result<java.util.List<AlertVO>> getRecentAlerts(
            @RequestParam(defaultValue = "5") int limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get recent alerts request: userId={}, limit={}", userId, limit);
        java.util.List<AlertVO> alerts = alertService.getRecentAlerts(userId, limit);
        return Result.success(alerts);
    }

    @Operation(summary = "Mark alert as read")
    @PutMapping("/{id}/read")
    public Result<Boolean> markAsRead(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Mark alert as read: alertId={}, userId={}", id, userId);
        Boolean result = alertService.markAsRead(id, userId);
        return Result.success(result);
    }

    @Operation(summary = "Acknowledge an alert")
    @PutMapping("/{id}/acknowledge")
    public Result<Boolean> acknowledgeAlert(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Acknowledge alert: alertId={}, userId={}", id, userId);
        Boolean result = alertService.acknowledgeAlert(id, userId);
        return Result.success(result);
    }

    @Operation(summary = "Mark all alerts as read")
    @PutMapping("/read-all")
    public Result<Integer> markAllAsRead() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Mark all alerts as read: userId={}", userId);
        Integer count = alertService.markAllAsRead(userId);
        return Result.success(count);
    }

    @Operation(summary = "Get alert statistics")
    @GetMapping("/statistics")
    public Result<java.util.Map<String, Object>> getStatistics() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get alert statistics: userId={}", userId);
        java.util.Map<String, Object> stats = alertService.getStatistics(userId);
        return Result.success(stats);
    }

    @Operation(summary = "Get trend prediction for a metric")
    @GetMapping("/trend/{metricKey}")
    public Result<TrendResult> getTrendPrediction(
            @PathVariable String metricKey,
            @RequestParam(defaultValue = "7") int days) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Get trend prediction: userId={}, metricKey={}, days={}", userId, metricKey, days);
        TrendResult result = trendPredictor.predict(userId, metricKey, days);
        return Result.success(result);
    }

    @Operation(summary = "Check if early warning is needed for a metric")
    @GetMapping("/trend/{metricKey}/early-warning")
    public Result<java.util.Map<String, Object>> checkEarlyWarning(@PathVariable String metricKey) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Check early warning: userId={}, metricKey={}", userId, metricKey);
        boolean needsWarning = trendPredictor.shouldGenerateEarlyWarning(userId, metricKey);
        return Result.success(java.util.Map.of(
                "metricKey", metricKey,
                "earlyWarningNeeded", needsWarning
        ));
    }
}
