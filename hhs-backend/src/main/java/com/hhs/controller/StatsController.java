package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.security.SecurityUtils;
import com.hhs.service.StatsService;
import com.hhs.vo.DashboardSummaryVO;
import com.hhs.vo.MonthlyStatsVO;
import com.hhs.vo.TodayStatsVO;
import com.hhs.vo.TrendDataVO;
import com.hhs.vo.WeeklyStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计数据控制器
 * 提供今日、周、月录入统计及趋势数据
 */
@Slf4j
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "统计数据", description = "数据统计相关接口")
public class StatsController {

    private final StatsService statsService;

    /**
     * 获取今日统计数据
     */
    @GetMapping("/today")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "今日统计", description = "获取今日健康指标、保健指标、设备同步、AI识别的统计数据")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<TodayStatsVO> getTodayStats() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("获取今日统计数据: userId={}", userId);

        TodayStatsVO stats = statsService.getTodayStats(userId);
        return Result.success(stats);
    }

    /**
     * 获取本周统计数据（含周同比）
     */
    @GetMapping("/weekly")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "周统计", description = "获取本周统计数据及与上周的对比变化百分比")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<WeeklyStatsVO> getWeeklyStats() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("获取周统计数据: userId={}", userId);

        WeeklyStatsVO stats = statsService.getWeeklyStats(userId);
        return Result.success(stats);
    }

    /**
     * 获取本月统计数据（含月同比）
     */
    @GetMapping("/monthly")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "月统计", description = "获取本月统计数据及与上月的对比变化百分比")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<MonthlyStatsVO> getMonthlyStats() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("获取月统计数据: userId={}", userId);

        MonthlyStatsVO stats = statsService.getMonthlyStats(userId);
        return Result.success(stats);
    }

    /**
     * 获取指标趋势数据（折线图/迷你图）
     */
    @GetMapping("/trends")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "趋势数据", description = "获取指定指标的时间序列数据，用于折线图/迷你图展示")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<List<TrendDataVO>> getTrends(
            @Parameter(description = "指标类型，如 health_score、metric_count 或具体 metricKey")
            @RequestParam(required = false) String metric,
            @Parameter(description = "天数，默认30")
            @RequestParam(defaultValue = "30") int days) {
        if (days < 1) days = 1;
        if (days > 365) days = 365;
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("获取趋势数据: userId={}, metric={}, days={}", userId, metric, days);

        List<TrendDataVO> trends = statsService.getTrends(userId, metric, days);
        return Result.success(trends);
    }

    /**
     * 获取仪表盘汇总数据
     */
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "仪表盘汇总", description = "一次请求返回所有关键指标：评分、录入量、告警、同步、AI识别等")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "成功"),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Result<DashboardSummaryVO> getDashboardSummary() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("获取仪表盘汇总: userId={}", userId);

        DashboardSummaryVO summary = statsService.getDashboardSummary(userId);
        return Result.success(summary);
    }
}