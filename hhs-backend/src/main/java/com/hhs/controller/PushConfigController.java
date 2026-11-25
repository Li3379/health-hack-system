package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.PushConfigRequest;
import com.hhs.entity.PushHistory;
import com.hhs.entity.UserPushConfig;
import com.hhs.mapper.UserPushConfigMapper;
import com.hhs.security.EmailAddressValidator;
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
     * How far back to look for the most recent delivery attempt.
     *
     * <p>Wide on purpose: a channel that last failed yesterday is still suspect today, and the
     * point of surfacing it is to stop the page from claiming delivery works. A stale failure
     * that has since been fixed disappears as soon as one attempt succeeds (only the newest row
     * per channel is read).
     */
    private static final java.time.Duration DELIVERY_LOOKBACK = java.time.Duration.ofDays(7);

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

        // 每个渠道最近一次投递结果。用历史而不是现场探测：SMTP 探测是一次真实 AUTH，
        // 服务商会对反复失败的 AUTH 限频 —— 每次读配置页都探一次，反而会制造它所报告的故障。
        Map<String, PushHistory> latestOutcomes = loadLatestOutcomes(userId);

        // Build response with all channel types
        List<PushConfigVO> result = new ArrayList<>();
        for (ChannelType channelType : ChannelType.values()) {
            UserPushConfig config = configMap.get(channelType.name());
            boolean serverAvailable = pushChannelManager.hasChannel(channelType);
            PushConfigVO vo = config != null
                    ? PushConfigVO.fromEntity(config, serverAvailable)
                    : PushConfigVO.defaultForChannel(channelType, serverAvailable);
            result.add(withDeliveryOutcome(vo, latestOutcomes.get(channelType.name())));
        }

        return Result.success(result);
    }

    /**
     * Newest delivery attempt per channel within the lookback window.
     *
     * <p>Failure to read history must not break the settings page — an empty map degrades to
     * "outcome unknown", which the UI renders as no claim rather than a false reassurance.
     */
    private Map<String, PushHistory> loadLatestOutcomes(Long userId) {
        var history = pushHistoryService.getLatestPerChannelSince(userId,
                LocalDateTime.now().minus(DELIVERY_LOOKBACK));
        if (history == null || history.isEmpty()) {
            return Map.of();
        }
        return history.stream()
                .filter(h -> h.getChannelType() != null)
                .collect(java.util.stream.Collectors.toMap(
                        PushHistory::getChannelType,
                        h -> h,
                        // 已按 pushed_at DESC 排序，保留第一条即最新
                        (first, ignored) -> first));
    }

    private PushConfigVO withDeliveryOutcome(PushConfigVO vo, PushHistory latest) {
        if (latest == null) {
            return vo;
        }
        return vo.withDeliveryOutcome(latest.getStatus(), latest.getPushedAt());
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
        // 单渠道读取与列表读取必须给出同一套 availability/投递结果，否则刷新单条会
        // 「看起来好了」，刷新整页又变回失败。
        withDeliveryOutcome(vo, loadLatestOutcomes(userId).get(type.name()));

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

        // EMAIL 的 configValue 会被 EmailPushChannel 直接当成收件人（To: 头）。不在这里校验的话，
        // 写错（如 foo@qq）照样保存成功、页面显示「已配置」，直到真实告警或点测试推送才由 SMTP
        // 报一个英文错误 —— 定位要跨过整个推送链路。
        // 掩码值（含 ****）是客户端读回来的占位符，不代表真实地址，跳过校验（同下面的保存逻辑）。
        if (type == ChannelType.EMAIL && request.getConfigValue() != null
                && !request.getConfigValue().isBlank()
                && !request.getConfigValue().contains(PushConfigVO.MASK_MARKER)) {
            validateEmailAddress(request.getConfigValue());
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

        PushConfigVO saved = PushConfigVO.fromEntity(config, pushChannelManager.hasChannel(type));
        // 保存后立即返回的 VO 也要带同一份投递结果：否则前端把响应写回本地状态时，
        // 会把刚读到的失败标记抹掉，页面重新变回「支持离线推送」。
        withDeliveryOutcome(saved, loadLatestOutcomes(userId).get(type.name()));
        return Result.success(saved);
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
        // AlertVO 的值域是 ^(CRITICAL|WARNING|NORMAL)$（见 AlertVO:23），而这里手工 new、
        // 不走 Bean Validation，写错值不会被拦下。用 INFO 会让测试消息落在值域外：真实告警
        // 的 alertType 只可能由 ThresholdRangeChecker 产出 CRITICAL/WARNING/NORMAL，
        // 任何将来按 alertType 分支的逻辑（模板选择、统计）都会在测试消息上走 default。
        testAlert.setAlertType("NORMAL");
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

    /**
     * Validate a recipient address at write time.
     *
     * <p>{@link EmailAddressValidator} raises a {@code BusinessException} with the Chinese detail
     * that {@code GlobalExceptionHandler} projects into the response envelope — same channel as
     * {@link #validateWebhookUrl}.
     *
     * @param configValue the user-supplied address
     */
    private void validateEmailAddress(String configValue) {
        EmailAddressValidator.validate(configValue);
    }
}