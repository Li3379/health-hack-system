package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.PushConfigRequest;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserPushConfigMapper;
import com.hhs.security.SecurityUtils;
import com.hhs.security.WebhookUrlValidator;
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
import java.util.Locale;
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
            boolean serverAvailable = pushChannelManager.hasChannel(channelType);
            if (config != null) {
                result.add(PushConfigVO.fromEntity(config, serverAvailable));
            } else {
                result.add(PushConfigVO.defaultForChannel(channelType, serverAvailable));
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
            type = ChannelType.valueOf(channelType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "推送渠道类型无效");
        }

        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, type.name());

        boolean serverAvailable = pushChannelManager.hasChannel(type);
        PushConfigVO vo = config != null
                ? PushConfigVO.fromEntity(config, serverAvailable)
                : PushConfigVO.defaultForChannel(type, serverAvailable);

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
            type = ChannelType.valueOf(channelType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "推送渠道类型无效");
        }

        // Validate config value for offline channels
        if (type.isOfflineSupported() && Boolean.TRUE.equals(request.getEnabled())) {
            if (request.getConfigValue() == null || request.getConfigValue().isBlank()) {
                return Result.failure(400, "开启该推送渠道前，请先填写配置内容");
            }
        }

        // Server capability gate: enabling a channel that was never registered (e.g. EMAIL when
        // spring.mail.host is blank) can only produce silent non-delivery, so refuse it up front.
        // Turning a channel *off* stays allowed — otherwise an existing enabled row would be
        // impossible to disable on an environment that has since lost the capability.
        if (Boolean.TRUE.equals(request.getEnabled()) && !pushChannelManager.hasChannel(type)) {
            return Result.failure(400, "服务端未开通该推送渠道，无法开启");
        }

        // SSRF hardening: webhook channels receive a server-issued POST, so the URL is validated
        // before it is persisted. EMAIL holds an address, not a URL, and needs no such check.
        if (isWebhookChannel(type) && request.getConfigValue() != null
                && !request.getConfigValue().isBlank()) {
            validateWebhookUrl(request.getConfigValue());
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

        // Update fields.
        // A masked value is the placeholder the client received on read — it is not a real
        // secret, so storing it would destroy the configured webhook. Treat it as "no change".
        if (request.getConfigKey() != null) {
            config.setConfigKey(request.getConfigKey());
        }
        if (request.getConfigValue() != null
                && !request.getConfigValue().contains(PushConfigVO.MASK_MARKER)) {
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

        return Result.success(PushConfigVO.fromEntity(config, pushChannelManager.hasChannel(type)));
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
            type = ChannelType.valueOf(channelType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "推送渠道类型无效");
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
            type = ChannelType.valueOf(channelType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Result.failure(400, "推送渠道类型无效");
        }

        // Check if channel is available
        if (!pushChannelManager.hasChannel(type)) {
            return Result.failure(400, "该推送渠道尚未在服务端配置，请联系管理员");
        }

        // Check if user has enabled this channel
        UserPushConfig config = userPushConfigMapper.findByUserIdAndChannelType(userId, type.name());
        if (config == null || config.getEnabled() != 1) {
            return Result.failure(400, "请先启用该推送渠道后再发送测试");
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
            return Result.failure(400, "推送失败: " + (result.getMessage() != null ? result.getMessage() : "请检查配置后重试"));
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

    /**
     * Whether this channel receives a server-issued POST to a user-supplied URL.
     *
     * <p>Webhook channels are the SSRF sinks; EMAIL stores an address and WEBSOCKET is a
     * connection keyed on the authenticated session, so neither needs URL validation here.
     *
     * @param type channel type
     * @return true for {@link ChannelType#FEISHU} and {@link ChannelType#WECOM}
     */
    private boolean isWebhookChannel(ChannelType type) {
        return type == ChannelType.FEISHU || type == ChannelType.WECOM;
    }

    /**
     * Validate a webhook URL at write time.
     *
     * <p>{@link WebhookUrlValidator} raises a {@code BusinessException} with the Chinese detail
     * that {@code GlobalExceptionHandler} projects into the response envelope.
     *
     * @param configValue the user-supplied URL
     */
    private void validateWebhookUrl(String configValue) {
        WebhookUrlValidator.validate(configValue);
    }
}