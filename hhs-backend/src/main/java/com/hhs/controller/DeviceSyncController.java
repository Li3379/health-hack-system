package com.hhs.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.Result;
import com.hhs.security.SecurityUtils;
import com.hhs.service.DeviceConnectionService;
import com.hhs.service.HuaweiHealthService;
import com.hhs.service.SyncHistoryService;
import com.hhs.vo.DeviceConnectionVO;
import com.hhs.vo.SyncHistoryVO;
import com.hhs.vo.SyncResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备同步控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Tag(name = "设备同步", description = "穿戴设备数据同步接口")
public class DeviceSyncController {

    private final DeviceConnectionService deviceConnectionService;
    private final SyncHistoryService syncHistoryService;
    private final HuaweiHealthService huaweiHealthService;

    /**
     * 获取已连接设备列表
     */
    @GetMapping("/connections")
    @Operation(summary = "获取设备连接列表", description = "获取用户所有穿戴设备的连接状态")
    public Result<List<DeviceConnectionVO>> getConnections() {
        Long userId = SecurityUtils.getCurrentUserId();
        List<DeviceConnectionVO> connections = deviceConnectionService.getConnections(userId);

        return Result.success(connections);
    }

    /**
     * 连接设备（获取OAuth授权URL）
     */
    @PostMapping("/connect/{platform}")
    @Operation(summary = "连接设备", description = "获取OAuth授权URL，用于连接穿戴设备")
    public Result<String> connectDevice(@PathVariable String platform) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 请求连接设备: {}", userId, platform);

        String authUrl = switch (platform.toLowerCase()) {
            case "huawei" -> {
                String url = huaweiHealthService.getAuthorizationUrl(userId);
                if (url == null) {
                    yield null;  // OAuth not configured
                }
                yield url;
            }
            case "xiaomi" -> {
                // TODO: Phase 4/5 - Implement Xiaomi OAuth
                log.warn("Xiaomi OAuth not yet implemented");
                yield null;
            }
            case "wechat", "apple" -> {
                log.warn("Platform {} does not support OAuth connection", platform);
                yield null;
            }
            default -> {
                log.warn("Unknown platform: {}", platform);
                yield null;
            }
        };

        if (authUrl == null) {
            return Result.failure(400, platform + " 平台暂不支持或未配置");
        }

        return Result.success(authUrl);
    }

    /**
     * OAuth回调处理
     */
    @GetMapping("/callback/{platform}")
    @Operation(summary = "OAuth回调", description = "处理设备授权回调")
    public String handleCallback(
            @PathVariable String platform,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription) {

        log.info("OAuth回调: platform={}, code={}, state={}, error={}", platform,
                code != null ? "***" : null, state != null ? "***" : null, error);

        // Handle OAuth error from provider
        if (error != null) {
            log.error("OAuth error from {}: {} - {}", platform, error, errorDescription);
            return buildErrorPage(error, errorDescription);
        }

        // Validate required parameters
        if (code == null || code.isEmpty()) {
            log.error("Missing authorization code in callback");
            return buildErrorPage("invalid_request", "Missing authorization code");
        }

        if (state == null || state.isEmpty()) {
            log.error("Missing state parameter in callback");
            return buildErrorPage("invalid_request", "Missing state parameter");
        }

        try {
            // Process callback based on platform
            switch (platform.toLowerCase()) {
                case "huawei" -> {
                    huaweiHealthService.handleCallback(code, state);
                    return buildSuccessPage();
                }
                case "xiaomi" -> {
                    // TODO: Phase 4/5 - Implement Xiaomi OAuth callback
                    log.warn("Xiaomi OAuth callback not yet implemented");
                    return buildErrorPage("not_implemented", "Xiaomi integration coming soon");
                }
                default -> {
                    log.warn("Unknown platform in callback: {}", platform);
                    return buildErrorPage("invalid_platform", "Unknown platform: " + platform);
                }
            }
        } catch (Exception e) {
            log.error("Error processing OAuth callback for {}: {}", platform, e.getMessage(), e);
            return buildErrorPage("server_error", e.getMessage());
        }
    }

    /**
     * Build success HTML page for OAuth callback
     */
    private String buildSuccessPage() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>授权成功</title>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; text-align: center; padding: 50px; background: #f5f5f5; }
                    .container { background: white; border-radius: 12px; padding: 40px; max-width: 400px; margin: 0 auto; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .success { color: #52c41a; font-size: 48px; }
                    .message { margin-top: 20px; color: #333; font-size: 16px; }
                    .hint { margin-top: 10px; color: #999; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="success">✓</div>
                    <div class="message">授权成功！</div>
                    <div class="hint">请关闭此页面返回应用</div>
                </div>
                <script>setTimeout(() => window.close(), 2000);</script>
            </body>
            </html>
            """;
    }

    /**
     * Build error HTML page for OAuth callback
     */
    private String buildErrorPage(String error, String description) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>授权失败</title>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; text-align: center; padding: 50px; background: #f5f5f5; }
                    .container { background: white; border-radius: 12px; padding: 40px; max-width: 400px; margin: 0 auto; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
                    .error { color: #ff4d4f; font-size: 48px; }
                    .message { margin-top: 20px; color: #333; font-size: 16px; }
                    .detail { margin-top: 10px; color: #999; font-size: 14px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="error">✗</div>
                    <div class="message">授权失败</div>
                    <div class="detail">""" + (description != null ? description : error) + """
                    </div>
                </div>
            </body>
            </html>
            """;
    }

    /**
     * 断开设备连接
     */
    @DeleteMapping("/disconnect/{platform}")
    @Operation(summary = "断开设备", description = "断开与穿戴设备的连接")
    public Result<Void> disconnect(@PathVariable String platform) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean success = deviceConnectionService.disconnect(userId, platform);

        return success ? Result.success() : Result.failure(500, "断开连接失败");
    }

    /**
     * 手动同步指定设备
     */
    @PostMapping("/sync/{platform}")
    @Operation(summary = "同步设备数据", description = "手动触发指定设备的数据同步")
    public Result<SyncResultVO> syncNow(@PathVariable String platform) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 手动同步设备: {}", userId, platform);

        // TODO: Phase 4/5 - 实现实际的数据同步逻辑
        // 目前返回占位结果
        SyncResultVO result = SyncResultVO.success(platform, 0, 0);
        result.setErrorMessage("设备同步功能将在Phase 4/5实现");

        return Result.success(result);
    }

    /**
     * 批量同步所有设备
     */
    @PostMapping("/sync/all")
    @Operation(summary = "同步所有设备", description = "手动触发所有已连接设备的数据同步")
    public Result<List<SyncResultVO>> syncAll() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("用户 {} 批量同步所有设备", userId);

        // TODO: Phase 4/5 - 实现实际的批量同步逻辑
        List<SyncResultVO> results = List.of();

        return Result.success(results);
    }

    /**
     * 获取同步历史
     */
    @GetMapping("/sync/history")
    @Operation(summary = "获取同步历史", description = "获取设备同步历史记录")
    public Result<Page<SyncHistoryVO>> getSyncHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        Long userId = SecurityUtils.getCurrentUserId();
        Page<SyncHistoryVO> history = syncHistoryService.getHistory(userId, page, size);

        return Result.success(history);
    }
}