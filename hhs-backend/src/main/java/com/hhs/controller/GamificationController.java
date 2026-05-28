package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.entity.Achievement;
import com.hhs.entity.UserStreak;
import com.hhs.security.SecurityUtils;
import com.hhs.service.GamificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 游戏化控制器
 * 提供连续记录、成就和积分查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/gamification")
@RequiredArgsConstructor
@Tag(name = "游戏化", description = "连续记录、成就、积分相关接口")
public class GamificationController {

    private final GamificationService gamificationService;

    @GetMapping("/streak")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取连续记录", description = "获取当前用户的连续记录天数信息")
    public Result<UserStreak> getStreak() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("获取连续记录: userId={}", userId);
        return Result.success(gamificationService.getStreak(userId));
    }

    @GetMapping("/achievements")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取所有成就", description = "获取系统中所有成就定义")
    public Result<List<Achievement>> getAllAchievements() {
        log.debug("获取所有成就定义");
        return Result.success(gamificationService.getAllAchievements());
    }

    @GetMapping("/achievements/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取我的成就", description = "获取当前用户已解锁的成就列表")
    public Result<List<Achievement>> getUserAchievements() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("获取用户已解锁成就: userId={}", userId);
        return Result.success(gamificationService.getUserAchievements(userId));
    }

    @GetMapping("/points")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "获取积分", description = "获取当前用户的总积分")
    public Result<Integer> getUserPoints() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("获取用户积分: userId={}", userId);
        return Result.success(gamificationService.getUserPoints(userId));
    }
}
