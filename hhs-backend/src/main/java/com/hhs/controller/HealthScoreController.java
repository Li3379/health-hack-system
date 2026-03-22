package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.security.SecurityUtils;
import com.hhs.service.HealthReportService;
import com.hhs.service.HealthScoreService;
import com.hhs.service.domain.MetricValidator;
import com.hhs.vo.HealthReportVO;
import com.hhs.vo.HealthScoreVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Health Score Controller
 * Provides health score calculations and AI-powered health reports
 */
@Slf4j
@Tag(name = "Health Score", description = "健康评分与报告")
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthScoreController {

    private final HealthScoreService healthScoreService;
    private final HealthReportService healthReportService;
    private final MetricValidator metricValidator;

    // ==================== Health Score APIs ====================

    @Operation(summary = "Get health score")
    @GetMapping("/score")
    public Result<HealthScoreVO> getHealthScore() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get health score request: userId={}", userId);
        HealthScoreVO score = healthScoreService.calculateScore(userId);
        return Result.success(score);
    }

    @Operation(summary = "Force recalculate health score")
    @PostMapping("/score/recalculate")
    public Result<HealthScoreVO> recalculateHealthScore() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Recalculate health score request: userId={}", userId);
        HealthScoreVO score = healthScoreService.forceRecalculate(userId);
        return Result.success(score);
    }

    @Operation(summary = "Get health score breakdown")
    @GetMapping("/score/breakdown")
    public Result<HealthScoreVO> getScoreBreakdown() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get score breakdown request: userId={}", userId);
        HealthScoreVO score = healthScoreService.calculateScore(userId);
        return Result.success(score);
    }

    @Operation(summary = "Calculate health score (alias for recalculate)")
    @PostMapping("/score/calculate")
    public Result<HealthScoreVO> calculateScore() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Calculate health score request: userId={}", userId);
        HealthScoreVO score = healthScoreService.forceRecalculate(userId);
        return Result.success(score);
    }

    @Operation(summary = "Get health score history")
    @GetMapping("/score/history")
    public Result<List<Map<String, Object>>> getScoreHistory(
            @RequestParam(defaultValue = "30") Integer days) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get score history request: userId={}, days={}", userId, days);

        // Check if user has data
        if (!metricValidator.hasAnyData(userId)) {
            log.info("User {} has no health data for history", userId);
            return Result.success(List.of());
        }

        HealthScoreVO currentScore = healthScoreService.calculateScore(userId);

        // Return current score as history (single entry)
        Map<String, Object> historyEntry = Map.of(
            "date", currentScore.getCalculatedAt(),
            "score", currentScore.getScore(),
            "level", currentScore.getLevel()
        );

        return Result.success(List.of(historyEntry));
    }

    // ==================== Health Report APIs ====================

    @Operation(summary = "Get latest health report (cached if available)")
    @GetMapping("/report")
    public Result<HealthReportVO> getHealthReport() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get health report request: userId={}", userId);
        HealthReportVO report = healthReportService.getOrGenerateReport(userId);
        return Result.success(report);
    }

    @Operation(summary = "Force regenerate health report")
    @PostMapping("/report/generate")
    public Result<HealthReportVO> generateHealthReport() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Force generate health report request: userId={}", userId);
        HealthReportVO report = healthReportService.regenerateReport(userId);
        return Result.success(report);
    }

    @Operation(summary = "Get health report history")
    @GetMapping("/report/history")
    public Result<List<HealthReportVO>> getReportHistory(
            @Parameter(description = "Maximum number of reports to return")
            @RequestParam(defaultValue = "10") Integer limit) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get report history request: userId={}, limit={}", userId, limit);
        List<HealthReportVO> reports = healthReportService.getReportHistory(userId, limit);
        return Result.success(reports);
    }

    @Operation(summary = "Get specific health report by ID")
    @GetMapping("/report/{reportId}")
    public Result<HealthReportVO> getReportById(
            @Parameter(description = "Report unique identifier")
            @PathVariable String reportId) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get report by ID request: userId={}, reportId={}", userId, reportId);
        HealthReportVO report = healthReportService.getReportById(userId, reportId);
        if (report == null) {
            return Result.failure(404, "报告不存在或无权访问");
        }
        return Result.success(report);
    }
}