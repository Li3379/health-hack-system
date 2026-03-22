package com.hhs.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.Result;
import com.hhs.dto.UserThresholdRequest;
import com.hhs.entity.UserThreshold;
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
 * Handles user personalized threshold settings for health metrics
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
        log.debug("Fetching thresholds for user: {}", userId);
        Page<UserThreshold> result = userThresholdService.list(page, size, userId);
        return Result.success(result);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get all thresholds for a user")
    public Result<List<UserThreshold>> getByUserId(@PathVariable Long userId) {
        log.debug("Fetching all thresholds for user: {}", userId);
        List<UserThreshold> result = userThresholdService.getByUserId(userId);
        return Result.success(result);
    }

    @GetMapping("/by-key")
    @Operation(summary = "Get threshold by user ID and metric key")
    public Result<UserThreshold> getByUserAndMetricKey(
        @RequestParam Long userId,
        @RequestParam String metricKey
    ) {
        log.debug("Fetching threshold for user: {}, metricKey: {}", userId, metricKey);
        UserThreshold result = userThresholdService.getByUserAndMetricKey(userId, metricKey);
        return Result.success(result);
    }

    @PostMapping
    @Operation(summary = "Create user threshold")
    public Result<UserThreshold> create(@RequestBody @Valid UserThresholdRequest request) {
        log.info("Creating threshold for user: {}, metricKey: {}", request.getUserId(), request.getMetricKey());
        UserThreshold threshold = userThresholdService.create(request);
        return Result.success(threshold);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user threshold")
    public Result<UserThreshold> update(
        @PathVariable Long id,
        @RequestBody @Valid UserThresholdRequest request
    ) {
        log.info("Updating threshold id: {}", id);
        UserThreshold threshold = userThresholdService.update(id, request);
        return Result.success(threshold);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete user threshold")
    public Result<Void> delete(@PathVariable Long id) {
        log.info("Deleting threshold id: {}", id);
        userThresholdService.delete(id);
        return Result.success(null);
    }
}
