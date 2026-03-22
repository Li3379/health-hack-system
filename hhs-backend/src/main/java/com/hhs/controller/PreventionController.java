package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.HealthProfileRequest;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.security.SecurityUtils;
import com.hhs.service.HealthProfileService;
import com.hhs.service.HealthMetricService;
import com.hhs.service.RiskAssessmentService;
import com.hhs.vo.HealthProfileVO;
import com.hhs.vo.HealthMetricVO;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.RiskAssessmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Tag(name = "预防健康模块", description = "健康档案、健康指标、风险评估")
@RestController
@RequestMapping("/api/prevention")
public class PreventionController {

    private final HealthProfileService healthProfileService;
    private final HealthMetricService healthMetricService;
    private final RiskAssessmentService riskAssessmentService;

    public PreventionController(HealthProfileService healthProfileService,
                               HealthMetricService healthMetricService,
                               RiskAssessmentService riskAssessmentService) {
        this.healthProfileService = healthProfileService;
        this.healthMetricService = healthMetricService;
        this.riskAssessmentService = riskAssessmentService;
    }

    @Operation(summary = "创建健康档案", description = "为当前用户创建健康档案")
    @PostMapping("/health-profile")
    @PreAuthorize("isAuthenticated()")
    public Result<HealthProfileVO> createHealthProfile(@RequestBody @Valid HealthProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Create health profile request: userId={}", userId);
        return Result.success(healthProfileService.create(userId, request));
    }

    @Operation(summary = "获取当前用户健康档案", description = "获取当前登录用户的健康档案")
    @GetMapping("/health-profile")
    @PreAuthorize("isAuthenticated()")
    public Result<HealthProfileVO> getMyHealthProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get health profile request: userId={}", userId);
        HealthProfileVO vo = healthProfileService.getByUserId(userId);
        if (vo == null) {
            return Result.failure(404, "健康档案不存在，请先创建");
        }
        return Result.success(vo);
    }

    @Operation(summary = "按用户ID获取健康档案", description = "获取指定用户的健康档案（仅限本人）")
    @GetMapping("/health-profile/{userId}")
    @PreAuthorize("isAuthenticated()")
    public Result<HealthProfileVO> getHealthProfile(
            @Parameter(description = "用户ID", required = true) @PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        log.debug("Get health profile by userId: requestedUserId={}, currentUserId={}", userId, currentUserId);
        if (!currentUserId.equals(userId)) {
            return Result.failure(403, "无权查看他人档案");
        }
        HealthProfileVO vo = healthProfileService.getByUserId(userId);
        if (vo == null) {
            return Result.failure(404, "健康档案不存在，请先创建");
        }
        return Result.success(vo);
    }

    @Operation(summary = "更新健康档案", description = "按ID更新健康档案")
    @PutMapping("/health-profile/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<HealthProfileVO> updateHealthProfile(
            @Parameter(description = "档案ID", required = true) @PathVariable Long id,
            @RequestBody @Valid HealthProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Update health profile request: profileId={}, userId={}", id, userId);
        return Result.success(healthProfileService.update(id, userId, request));
    }

    @Operation(summary = "创建风险评估", description = "基于档案与指标执行风险评估并返回结果列表")
    @PostMapping("/risk-assessment")
    @PreAuthorize("isAuthenticated()")
    public Result<List<RiskAssessmentVO>> createRiskAssessment() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Create risk assessment request: userId={}", userId);
        return Result.success(riskAssessmentService.createAssessment(userId));
    }

    @Operation(summary = "获取风险评估详情", description = "按ID获取单条风险评估记录")
    @GetMapping("/risk-assessment/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<RiskAssessmentVO> getRiskAssessment(
            @Parameter(description = "评估记录ID", required = true) @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get risk assessment request: assessmentId={}, userId={}", id, userId);
        RiskAssessmentVO vo = riskAssessmentService.getById(id, userId);
        if (vo == null) return Result.failure(404, "记录不存在");
        return Result.success(vo);
    }

    @Operation(summary = "新增健康指标", description = "录入一条健康指标记录")
    @PostMapping("/health-metrics")
    @PreAuthorize("isAuthenticated()")
    public Result<HealthMetricVO> addHealthMetric(@RequestBody @Valid HealthMetricRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Add health metric request: userId={}, metricKey={}", userId, request.getMetricKey());
        return Result.success(healthMetricService.add(userId, request));
    }

    @Operation(summary = "健康指标列表", description = "查询健康指标列表，支持类型与日期范围")
    @GetMapping("/health-metrics")
    @PreAuthorize("isAuthenticated()")
    public Result<List<HealthMetricVO>> listHealthMetrics(
            @RequestParam(required = false) String metricKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("List health metrics request: userId={}, metricKey={}", userId, metricKey);
        return Result.success(healthMetricService.list(userId, metricKey, startDate, endDate));
    }

    @Operation(summary = "健康指标趋势", description = "获取指标趋势数据供图表展示")
    @GetMapping("/health-metrics/trend")
    @PreAuthorize("isAuthenticated()")
    public Result<List<HealthMetricTrendVO>> getHealthMetricsTrend(
            @RequestParam(required = false) String metricKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (startDate == null) startDate = LocalDate.now().minusMonths(6);
        if (endDate == null) endDate = LocalDate.now();
        return Result.success(healthMetricService.getTrend(userId, metricKey, startDate, endDate));
    }

    @Operation(summary = "删除健康指标", description = "按ID删除健康指标记录")
    @DeleteMapping("/health-metrics/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteHealthMetric(
            @Parameter(description = "指标ID", required = true) @PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        healthMetricService.delete(userId, id);
        return Result.success(null);
    }

    // Alias endpoints for API compatibility with frontend tests
    @Operation(summary = "获取健康档案 (别名)")
    @GetMapping("/profile")
    public Result<HealthProfileVO> getProfile() {
        Long userId = SecurityUtils.getCurrentUserId();
        HealthProfileVO vo = healthProfileService.getByUserId(userId);
        if (vo == null) {
            return Result.failure(404, "健康档案不存在，请先创建");
        }
        return Result.success(vo);
    }

    @Operation(summary = "创建健康档案 (别名)", description = "为当前用户创建健康档案")
    @PostMapping("/profile")
    public Result<HealthProfileVO> createProfileAlias(@RequestBody @Valid HealthProfileRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(healthProfileService.create(userId, request));
    }

    @Operation(summary = "获取健康指标 (别名)")
    @GetMapping("/metrics")
    public Result<List<HealthMetricVO>> getMetrics(
            @RequestParam(required = false) String metricKey,
            @RequestParam(required = false) Integer days) {
        Long userId = SecurityUtils.getCurrentUserId();
        LocalDate startDate = days != null ? LocalDate.now().minusDays(days) : LocalDate.now().minusDays(7);
        return Result.success(healthMetricService.list(userId, metricKey, startDate, LocalDate.now()));
    }

    @Operation(summary = "获取风险评估历史", description = "获取当前用户所有风险评估记录")
    @GetMapping("/risk-assessment")
    public Result<List<RiskAssessmentVO>> getRiskAssessment() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(riskAssessmentService.listByUserId(userId));
    }
}
