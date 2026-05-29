package com.hhs.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.Result;
import com.hhs.dto.UserThresholdRequest;
import com.hhs.entity.UserThreshold;
import com.hhs.common.constant.ErrorCode;
import com.hhs.exception.BusinessException;
import com.hhs.security.SecurityUtils;
import com.hhs.service.UserThresholdService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User Threshold Controller
 * Handles user personalized threshold settings for health metrics.
 * All endpoints enforce ownership: users can only access their own thresholds.
 */
@Slf4j
@Tag(name = "User Thresholds", description = "用户阈值设置管理")
@RestController
@RequestMapping("/api/thresholds")
@RequiredArgsConstructor
public class UserThresholdController {

    private final UserThresholdService userThresholdService;

    @GetMapping
    @Operation(summary = "Get user thresholds list")
    public Result<Page<UserThreshold>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        @RequestParam(required = false) Long userId
    ) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        log.debug("Fetching thresholds for user: {}", currentUserId);
        Page<UserThreshold> result = userThresholdService.list(page, size, currentUserId);
        return Result.success(result);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all thresholds for current user")
    public Result<List<UserThreshold>> getByUserId(@PathVariable Long userId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        enforceOwnership(currentUserId, userId, "read thresholds");
        log.debug("Fetching all thresholds for user: {}", currentUserId);
        List<UserThreshold> result = userThresholdService.getByUserId(currentUserId);
        return Result.success(result);
    }

    @GetMapping("/by-key")
    @Operation(summary = "Get threshold by metric key")
    public Result<UserThreshold> getByUserAndMetricKey(
            @RequestParam Long userId,
            @RequestParam String metricKey
    ) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        enforceOwnership(currentUserId, userId, "read threshold");
        log.debug("Fetching threshold for user: {}, metricKey: {}", currentUserId, metricKey);
        UserThreshold result = userThresholdService.getByUserAndMetricKey(currentUserId, metricKey);
        return Result.success(result);
    }

    @PostMapping
    @Operation(summary = "Create user threshold")
    public Result<UserThreshold> create(@RequestBody @Valid UserThresholdRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Creating threshold for user: {}, metricKey: {}", currentUserId, request.getMetricKey());
        UserThreshold threshold = userThresholdService.create(currentUserId, request);
        return Result.success(threshold);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user threshold")
    public Result<UserThreshold> update(
            @PathVariable Long id,
            @RequestBody @Valid UserThresholdRequest request
    ) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Updating threshold id: {} for user: {}", id, currentUserId);
        UserThreshold threshold = userThresholdService.update(id, currentUserId, request);
        return Result.success(threshold);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user threshold")
    public Result<Void> delete(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Deleting threshold id: {} for user: {}", id, currentUserId);
        userThresholdService.delete(id, currentUserId);
        return Result.success(null);
    }

    private void enforceOwnership(Long currentUserId, Long requestedUserId, String action) {
        if (!currentUserId.equals(requestedUserId)) {
            log.warn("IDOR attempt: userId={} tried to {} for userId={}", currentUserId, action, requestedUserId);
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
    }
}
