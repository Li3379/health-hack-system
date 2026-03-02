package com.hhs.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.Result;
import com.hhs.dto.WellnessMetricRequest;
import com.hhs.security.SecurityUtils;
import com.hhs.service.WellnessService;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.HealthMetricVO;
import com.hhs.vo.WellnessSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Wellness Controller
 * Handles wellness metric CRUD operations with category filtering
 */
@Slf4j
@Tag(name = "Wellness Metrics", description = "保健指标管理")
@RestController
@RequestMapping("/api/wellness")
@RequiredArgsConstructor
public class WellnessController {

    private final WellnessService wellnessService;

    @GetMapping
    @Operation(summary = "获取保健指标列表", description = "分页查询用户的保健指标，支持按类型和日期筛选")
    public Result<Page<HealthMetricVO>> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "指标类型") @RequestParam(required = false) String metricKey,
            @Parameter(description = "开始日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Fetching wellness metrics for user: {}", userId);
        Page<HealthMetricVO> result = wellnessService.getWellnessMetrics(userId, metricKey, startDate, endDate, page, size);
        return Result.success(result);
    }

    @PostMapping
    @Operation(summary = "创建保健指标", description = "记录新的保健指标数据")
    public Result<HealthMetricVO> create(@Valid @RequestBody WellnessMetricRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Creating wellness metric for user: {}, key: {}", userId, request.getMetricKey());
        HealthMetricVO metric = wellnessService.createWellnessMetric(userId, request);
        return Result.success(metric);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新保健指标", description = "更新指定的保健指标记录")
    public Result<HealthMetricVO> update(
            @Parameter(description = "指标ID") @PathVariable Long id,
            @Valid @RequestBody WellnessMetricRequest request
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Updating wellness metric id: {} for user: {}", id, userId);
        HealthMetricVO metric = wellnessService.updateWellnessMetric(userId, id, request);
        return Result.success(metric);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除保健指标", description = "删除指定的保健指标记录")
    public Result<Void> delete(@Parameter(description = "指标ID") @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Deleting wellness metric id: {} for user: {}", id, userId);
        wellnessService.deleteWellnessMetric(userId, id);
        return Result.success(null);
    }

    @GetMapping("/trend/{metricKey}")
    @Operation(summary = "获取指标趋势", description = "获取指定保健指标在日期范围内的趋势数据")
    public Result<HealthMetricTrendVO> getTrend(
            @Parameter(description = "指标类型") @PathVariable String metricKey,
            @Parameter(description = "开始日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "结束日期") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Fetching trend for user: {}, metric: {}", userId, metricKey);
        HealthMetricTrendVO trend = wellnessService.getTrend(userId, metricKey, startDate, endDate);
        return Result.success(trend);
    }

    @GetMapping("/summary")
    @Operation(summary = "获取保健指标汇总", description = "获取指定天数内的保健指标汇总数据，默认7天")
    public Result<WellnessSummaryVO> getSummary(
            @Parameter(description = "统计天数") @RequestParam(defaultValue = "7") int days
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Fetching wellness summary for user: {}, days: {}", userId, days);
        WellnessSummaryVO summary = wellnessService.getSummary(userId, days);
        return Result.success(summary);
    }

    @GetMapping("/latest")
    @Operation(summary = "获取最新保健指标", description = "获取用户每种保健指标的最近一次记录")
    public Result<Map<String, HealthMetricVO>> getLatest() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Fetching latest wellness metrics for user: {}", userId);
        Map<String, HealthMetricVO> latest = wellnessService.getLatestMetrics(userId);
        return Result.success(latest);
    }
}