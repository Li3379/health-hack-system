package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.AddProgressRequest;
import com.hhs.dto.CreateGoalRequest;
import com.hhs.dto.UpdateGoalRequest;
import com.hhs.entity.GoalProgress;
import com.hhs.entity.HealthGoal;
import com.hhs.security.SecurityUtils;
import com.hhs.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Goal Controller
 * Manages health goals and progress tracking
 */
@Slf4j
@Tag(name = "Health Goals", description = "健康目标管理")
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @Operation(summary = "创建健康目标")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public Result<HealthGoal> createGoal(@Valid @RequestBody CreateGoalRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Create goal request: userId={}, title={}", userId, request.getTitle());
        HealthGoal goal = goalService.createGoal(
                userId,
                request.getTitle(),
                request.getDescription(),
                request.getMetricKey(),
                request.getTargetValue(),
                request.getUnit(),
                request.getStartDate(),
                request.getEndDate()
        );
        return Result.success(goal);
    }

    @Operation(summary = "获取用户健康目标列表")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Result<List<HealthGoal>> listGoals() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("List goals request: userId={}", userId);
        List<HealthGoal> goals = goalService.getUserGoals(userId);
        return Result.success(goals);
    }

    @Operation(summary = "更新健康目标")
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<HealthGoal> updateGoal(
            @Parameter(description = "目标ID") @PathVariable Long id,
            @Valid @RequestBody UpdateGoalRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Update goal request: goalId={}, userId={}", id, userId);
        HealthGoal goal = goalService.updateGoal(
                id, userId,
                request.getTitle(),
                request.getDescription(),
                request.getEndDate()
        );
        return Result.success(goal);
    }

    @Operation(summary = "添加目标进度")
    @PostMapping("/{id}/progress")
    @PreAuthorize("isAuthenticated()")
    public Result<GoalProgress> addProgress(
            @Parameter(description = "目标ID") @PathVariable Long id,
            @Valid @RequestBody AddProgressRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Add progress request: goalId={}, userId={}, value={}", id, userId, request.getValue());
        GoalProgress progress = goalService.addProgress(id, userId, request.getValue(), request.getNote());
        return Result.success(progress);
    }
}
