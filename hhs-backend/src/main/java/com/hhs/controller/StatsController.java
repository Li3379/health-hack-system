package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.security.SecurityUtils;
import com.hhs.service.StatsService;
import com.hhs.vo.TodayStatsVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计数据控制器
 * 提供今日录入统计等数据
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
}