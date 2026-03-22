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
        @RequestParam(required = false) Long userId
    ) {
        log.debug("Fetching metrics for user: {}", userId);
        Page<com.hhs.vo.HealthMetricVO> result = healthMetricService.list(page, size, userId);
        return Result.success(result);
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
        log.info("Updating metric id: {}", id);
        request.setUserId(userId);
        HealthMetric metric = healthMetricService.update(id, request);
        return Result.success(metric);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete health metric")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("Deleting metric id: {}", id);
        healthMetricService.delete(id);
        return Result.success(null);
    }
}
