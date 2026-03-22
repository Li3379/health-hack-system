package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.RealtimeMetricRequest;
import com.hhs.dto.AlertVO;
import com.hhs.entity.AlertRule;
import com.hhs.entity.HealthAlert;
import com.hhs.entity.RealtimeMetric;
import com.hhs.entity.UserThreshold;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.mapper.UserMapper;
import com.hhs.security.SecurityUtils;
import com.hhs.service.AlertRuleEngine;
import com.hhs.service.AlertService;
import com.hhs.service.MetricDeduplicationService;
import com.hhs.vo.MetricTrendVO;
import com.hhs.vo.RealtimeMetricVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Real-time Metric Controller
 * Handles real-time health metric operations
 */
@Slf4j
@Tag(name = "Real-time Metrics", description = "实时健康指标")
@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final RealtimeMetricMapper realtimeMetricMapper;
    private final UserMapper userMapper;
    private final AlertService alertService;
    private final AlertRuleEngine alertRuleEngine;
    private final MetricDeduplicationService deduplicationService;

    @Operation(summary = "Add a real-time metric")
    @PostMapping("/metrics")
    public Result<RealtimeMetricVO> addMetric(@Valid @RequestBody RealtimeMetricRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Add metric request: userId={}, metricKey={}, value={}", userId, request.getMetricKey(), request.getValue());

        // 应用层数据一致性验证：验证用户存在（替代外键约束）
        if (userMapper.selectById(userId) == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // Create metric entity - support both metricKey and metricType for frontend compatibility
        String finalMetricKey = request.getMetricKey() != null ? request.getMetricKey() : request.getMetricType();
        RealtimeMetric metric = new RealtimeMetric();
        metric.setUserId(userId);
        metric.setMetricKey(finalMetricKey);
        metric.setValue(request.getValue());
        metric.setUnit(request.getUnit());
        metric.setSource(request.getSource());
        metric.setQualityScore(request.getQualityScore());
        metric.setCreatedAt(LocalDateTime.now());

        // Check deduplication
        if (!deduplicationService.shouldAcceptMetric(metric)) {
            log.debug("Metric rejected due to deduplication: userId={}, metricKey={}", userId, request.getMetricKey());
            return Result.failure(400, "该指标最近已录入，请稍后再试");
        }

        // Save metric
        realtimeMetricMapper.insert(metric);
        deduplicationService.recordMetric(metric);

        // Evaluate for alerts
        List<AlertVO> alerts = alertRuleEngine.evaluateMetric(metric);
        for (AlertVO alertVO : alerts) {
            HealthAlert alert = new HealthAlert();
            alert.setUserId(userId);
            alert.setAlertType(alertVO.getAlertType());
            alert.setAlertLevel(alertVO.getAlertLevel());
            alert.setTitle(alertVO.getTitle());
            alert.setMessage(alertVO.getMessage());
            alert.setMetricKey(alertVO.getMetricKey());
            alert.setCurrentValue(alertVO.getCurrentValue());
            alert.setThresholdValue(alertVO.getThresholdValue());
            alertService.createAlert(alert);
            log.info("Alert created: userId={}, alertType={}", userId, alertVO.getAlertType());
        }

        // Convert to VO
        RealtimeMetricVO vo = new RealtimeMetricVO();
        vo.setId(metric.getId());
        vo.setMetricKey(metric.getMetricKey());
        vo.setMetricDisplayName(getDisplayName(metric.getMetricKey()));
        vo.setValue(metric.getValue());
        vo.setUnit(metric.getUnit());
        vo.setSource(metric.getSource());
        vo.setCreatedAt(metric.getCreatedAt());

        return Result.success(vo);
    }

    @Operation(summary = "Get latest metrics for user")
    @GetMapping("/metrics")
    public Result<List<RealtimeMetricVO>> getLatestMetrics() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get latest metrics request: userId={}", userId);
        List<RealtimeMetric> metrics = realtimeMetricMapper.getLatestMetricsByUser(userId);

        List<RealtimeMetricVO> result = new ArrayList<>();
        for (RealtimeMetric metric : metrics) {
            RealtimeMetricVO vo = new RealtimeMetricVO();
            vo.setId(metric.getId());
            vo.setMetricKey(metric.getMetricKey());
            vo.setMetricDisplayName(getDisplayName(metric.getMetricKey()));
            vo.setValue(metric.getValue());
            vo.setUnit(metric.getUnit());
            vo.setSource(metric.getSource());
            vo.setCreatedAt(metric.getCreatedAt());
            result.add(vo);
        }

        return Result.success(result);
    }

    @Operation(summary = "Get metric trend data for charts")
    @GetMapping("/metrics/trend")
    public Result<MetricTrendVO> getMetricTrend(
            @RequestParam String metricKey,
            @RequestParam(defaultValue = "24") int hours) {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusHours(hours);

        List<RealtimeMetric> metrics = realtimeMetricMapper.getMetricsInRange(
                userId, metricKey, startTime, endTime);

        MetricTrendVO trend = new MetricTrendVO();
        trend.setMetricKey(metricKey);
        trend.setMetricDisplayName(getDisplayName(metricKey));
        trend.setUnit(getUnit(metricKey));

        List<MetricTrendVO.TrendPoint> dataPoints = new ArrayList<>();
        for (RealtimeMetric metric : metrics) {
            MetricTrendVO.TrendPoint point = new MetricTrendVO.TrendPoint();
            point.setTimestamp(metric.getCreatedAt());
            point.setValue(metric.getValue());
            dataPoints.add(point);
        }
        trend.setDataPoints(dataPoints);

        return Result.success(trend);
    }

    private String getDisplayName(String metricKey) {
        return switch (metricKey) {
            // 健康指标
            case "heartRate" -> "心率";
            case "systolicBP" -> "收缩压";
            case "diastolicBP" -> "舒张压";
            case "glucose" -> "血糖";
            case "weight" -> "体重";
            case "bmi" -> "BMI";
            case "temperature" -> "体温";
            // 保健指标
            case "sleepDuration" -> "睡眠时长";
            case "sleepQuality" -> "睡眠质量";
            case "steps" -> "步数";
            case "exerciseMinutes" -> "运动时长";
            case "waterIntake" -> "饮水量";
            case "mood" -> "心情";
            case "energy" -> "精力";
            default -> metricKey;
        };
    }

    private String getUnit(String metricKey) {
        return switch (metricKey) {
            // 健康指标
            case "heartRate" -> "次/分";
            case "systolicBP", "diastolicBP" -> "mmHg";
            case "glucose" -> "mmol/L";
            case "weight" -> "kg";
            case "temperature" -> "°C";
            case "bmi" -> "";
            // 保健指标
            case "sleepDuration" -> "小时";
            case "sleepQuality" -> "级";
            case "steps" -> "步";
            case "exerciseMinutes" -> "分钟";
            case "waterIntake" -> "杯";
            case "mood", "energy" -> "分";
            default -> "";
        };
    }

    // Alias endpoints for API compatibility with tests

    @Operation(summary = "Get latest metrics (alias)")
    @GetMapping("/metrics/latest")
    public Result<List<RealtimeMetricVO>> getLatestMetricsAlias() {
        return getLatestMetrics();
    }

    @Operation(summary = "Get metric history (alias)")
    @GetMapping("/metrics/history")
    public Result<MetricTrendVO> getMetricHistoryAlias(
            @RequestParam String metricKey,
            @RequestParam(defaultValue = "24") int hours) {
        return getMetricTrend(metricKey, hours);
    }
}
