package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.PushConfigRequest;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserPushConfigMapper;
import com.hhs.security.SecurityUtils;
import com.hhs.service.PushHistoryService;
import com.hhs.service.push.ChannelType;
import com.hhs.service.push.PushChannelManager;
import com.hhs.service.push.PushResult;
import com.hhs.vo.PushConfigVO;
import com.hhs.vo.PushHistoryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Push configuration REST controller
 */
@Slf4j
@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushConfigController {

    private final UserPushConfigMapper userPushConfigMapper;
    private final PushChannelManager pushChannelManager;
    private final PushHistoryService pushHistoryService;

    /**
     * Get all push configurations for current user
     */
    @GetMapping("/config")
    public Result<List<PushConfigVO>> getAllConfigs() {
        Long userId = SecurityUtils.getCurrentUserId();

        // Get all existing configs for user
        List<UserPushConfig> userConfigs = userPushConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPushConfig>()
                        .eq(UserPushConfig::getUserId, userId));

        // Create a map for quick lookup
        Map<String, UserPushConfig> configMap = userConfigs.stream()
                .collect(java.util.stream.Collectors.toMap(
                        UserPushConfig::getChannelType,
                        c -> c,
                        (a, b) -> a));

        // Build response with all channel types
        List<PushConfigVO> result = new ArrayList<>();
        for (ChannelType channelType : ChannelType.values()) {
            UserPushConfig config = configMap.get(channelType.name());
            if (config != null) {
                result.add(PushConfigVO.fromEntity(config));
            } else {
                result.add(PushConfigVO.defaultForChannel(channelType));
            }
        }

        return Result.success(result);
    }

    /**
     * Get specific channel configuration
     */
    @GetMapping("/config/{channelType}")
    public Result<PushConfigVO> getConfig(@PathVariable String channelType) {
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate channel type
        ChannelType type;
        try {
            type = ChannelType.valueOf(channelType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "Invalid channel type: " + channelType);
        }

        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, type.name());

        PushConfigVO vo = config != null
                ? PushConfigVO.fromEntity(config)
                : PushConfigVO.defaultForChannel(type);

        return Result.success(vo);
    }

    /**
     * Update push configuration
     */
    @PutMapping("/config/{channelType}")
    public Result<PushConfigVO> updateConfig(
            @PathVariable String channelType,
            @RequestBody @Valid PushConfigRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate channel type
        ChannelType type;
        try {
            type = ChannelType.valueOf(channelType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "Invalid channel type: " + channelType);
        }

        // Validate config value for offline channels
        if (type.isOfflineSupported() && Boolean.TRUE.equals(request.getEnabled())) {
            if (request.getConfigValue() == null || request.getConfigValue().isBlank()) {
                return Result.failure(400, "Configuration value is required for " + type.getLabel());
            }
        }

        // Find existing or create new
        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, type.name());

        boolean isNew = config == null;
        if (isNew) {
            config = new UserPushConfig();
            config.setUserId(userId);
            config.setChannelType(type.name());
            config.setCreatedAt(LocalDateTime.now());
        }

        // Update fields
        if (request.getConfigKey() != null) {
            config.setConfigKey(request.getConfigKey());
        }
        if (request.getConfigValue() != null) {
            config.setConfigValue(request.getConfigValue());
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled() ? 1 : 0);
        }
        config.setUpdatedAt(LocalDateTime.now());

        // Save
        if (isNew) {
            userPushConfigMapper.insert(config);
            log.info("Created push config for user {} channel {}", userId, type);
        } else {
            userPushConfigMapper.updateById(config);
            log.info("Updated push config for user {} channel {}", userId, type);
        }

        return Result.success(PushConfigVO.fromEntity(config));
    }

    /**
     * Delete push configuration
     */
    @DeleteMapping("/config/{channelType}")
    public Result<Void> deleteConfig(@PathVariable String channelType) {
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate channel type
        ChannelType type;
        try {
            type = ChannelType.valueOf(channelType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "Invalid channel type: " + channelType);
        }

        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, type.name());
        if (config != null) {
            userPushConfigMapper.deleteById(config.getId());
            log.info("Deleted push config for user {} channel {}", userId, type);
        }

        return Result.success();
    }

    /**
     * Test push channel connectivity
     */
    @PostMapping("/config/{channelType}/test")
    public Result<Map<String, Object>> testChannel(@PathVariable String channelType) {
        Long userId = SecurityUtils.getCurrentUserId();

        // Validate channel type
        ChannelType type;
        try {
            type = ChannelType.valueOf(channelType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "Invalid channel type: " + channelType);
        }

        // Check if channel is available
        if (!pushChannelManager.hasChannel(type)) {
            return Result.failure(400, "Channel " + type.getLabel() + " is not configured on server");
        }

        // Check if user has enabled this channel
        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, type.name());
        if (config == null || config.getEnabled() != 1) {
            return Result.failure(400, "Please enable " + type.getLabel() + " channel first");
        }

        // Create test alert
        com.hhs.dto.AlertVO testAlert = new com.hhs.dto.AlertVO();
        testAlert.setTitle("推送测试");
        testAlert.setMessage("这是一条测试消息，用于验证推送通道是否正常工作。");
        testAlert.setAlertLevel("LOW");
        testAlert.setAlertType("INFO");
        testAlert.setMetricKey("test");

        // Try to push
        PushResult result = pushChannelManager.push(userId, testAlert, type);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", result.isSuccess());
        response.put("channel", type.getLabel());
        response.put("message", result.getMessage());
        response.put("timestamp", LocalDateTime.now());

        if (result.isSuccess()) {
            log.info("Test push succeeded for user {} channel {}", userId, type);
            return Result.success(response);
        } else {
            log.warn("Test push failed for user {} channel {}: {}", userId, type, result.getMessage());
            return Result.failure(400, "Push failed: " + result.getMessage());
        }
    }

    /**
     * Get push history for current user
     */
    @GetMapping("/history")
    public Result<List<PushHistoryVO>> getHistory(@RequestParam(defaultValue = "20") int limit) {
        Long userId = SecurityUtils.getCurrentUserId();

        List<com.hhs.entity.PushHistory> history = pushHistoryService.getRecentHistory(userId, limit);

        List<PushHistoryVO> vos = history.stream()
                .map(PushHistoryVO::fromEntity)
                .toList();

        return Result.success(vos);
    }

    /**
     * Get push statistics for current user
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Long userId = SecurityUtils.getCurrentUserId();

        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Map<String, Object>> channelStats = pushHistoryService.getChannelStats(userId, since);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("period", "24 hours");
        response.put("channels", channelStats);

        return Result.success(response);
    }
}