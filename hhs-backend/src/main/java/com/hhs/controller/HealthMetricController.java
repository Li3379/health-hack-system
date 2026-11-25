package com.hhs.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.Result;
import com.hhs.entity.HealthMetric;
import com.hhs.service.HealthMetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Health Metric Controller
 * Handles health metric CRUD operations
 */
@Slf4j
@Tag(name = "Health Metrics", description = "健康指标管理")
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class HealthMetricController {

    private final HealthMetricService healthMetricService;

    @GetMapping
    @Operation(summary = "Get health metrics list")
    public Result<Page<com.hhs.vo.HealthMetricVO>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        @RequestParam(required = false) String metricKey,
        @RequestParam(required = false) String startDate,
        @RequestParam(required = false) String endDate
    ) {
        Long userId = com.hhs.security.SecurityUtils.getCurrentUserId();
        log.debug("Fetching metrics for user: {}, metricKey: {}, startDate: {}, endDate: {}",
                userId, metricKey, startDate, endDate);
        Page<com.hhs.vo.HealthMetricVO> result = healthMetricService.list(
                page, size, userId, metricKey, startDate, endDate);
        return Result.success(result);
    }

    @GetMapping("/recent")
    @Operation(summary = "Get recently recorded metrics across health and wellness")
    public Result<java.util.List<com.hhs.vo.HealthMetricVO>> recent(
        @RequestParam(defaultValue = "10") Integer limit
    ) {
        Long userId = com.hhs.security.SecurityUtils.getCurrentUserId();
        log.debug("Fetching recent metrics for user: {}, limit: {}", userId, limit);
        return Result.success(healthMetricService.listRecent(userId, limit));
    }

    @PostMapping
    @Operation(summary = "Create health metric")
    public Result<HealthMetric> create(@RequestBody @Valid com.hhs.dto.HealthMetricRequest request) {
        Long userId = com.hhs.security.SecurityUtils.getCurrentUserId();
        log.info("Creating metric for user: {}", userId);
        request.setUserId(userId);
        HealthMetric metric = healthMetricService.create(request);
        return Result.success(metric);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update health metric")
    public Result<HealthMetric> update(
        @PathVariable Long id,
        @RequestBody @Valid com.hhs.dto.HealthMetricRequest request
    ) {
        Long userId = com.hhs.security.SecurityUtils.getCurrentUserId();
        log.info("Updating metric id: {} for user: {}", id, userId);
        request.setUserId(userId);
        // userId 显式传给 service 做归属校验：只把它塞进请求体时，service 完全不读，
        // 任何登录用户都能改他人指标的数值 / 类型 / 记录日期。
        HealthMetric metric = healthMetricService.update(userId, id, request);
        return Result.success(metric);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete health metric")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = com.hhs.security.SecurityUtils.getCurrentUserId();
        log.info("Deleting metric id: {} for user: {}", id, userId);
        // 走带归属校验的重载；无校验的 delete(Long id) 已从接口移除。
        healthMetricService.delete(userId, id);
        return Result.success(null);
    }
}
