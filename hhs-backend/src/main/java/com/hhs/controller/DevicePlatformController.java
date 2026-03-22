package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.PlatformMetadata;
import com.hhs.service.DeviceSyncOrchestrationService;
import com.hhs.service.PlatformAvailabilityService;
import com.hhs.service.TokenEncryptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Device Platform Controller.
 * Provides platform metadata and availability information for device integrations.
 */
@Slf4j
@RestController
@RequestMapping("/api/device/platforms")
@RequiredArgsConstructor
@Tag(name = "设备平台", description = "设备平台元数据和可用性接口")
public class DevicePlatformController {

    private final PlatformAvailabilityService availabilityService;
    private final DeviceSyncOrchestrationService orchestrationService;
    private final TokenEncryptionService encryptionService;

    /**
     * Get metadata for all supported platforms.
     */
    @GetMapping("/metadata")
    @Operation(summary = "获取所有平台元数据", description = "返回所有支持的平台及其可用性状态")
    public Result<List<PlatformMetadata>> getAllPlatformMetadata() {
        log.debug("Fetching metadata for all platforms");

        List<PlatformMetadata> metadata = availabilityService.getAllPlatformMetadata();

        log.debug("Returning {} platform metadata entries", metadata.size());
        return Result.success(metadata);
    }

    /**
     * Get metadata for a specific platform.
     */
    @GetMapping("/{platform}/availability")
    @Operation(summary = "获取平台可用性", description = "返回指定平台的详细可用性信息")
    public Result<PlatformAvailability> getPlatformAvailability(
            @Parameter(description = "平台标识 (huawei, xiaomi, wechat, apple)")
            @PathVariable String platform) {

        log.debug("Checking availability for platform: {}", platform);

        PlatformMetadata metadata = availabilityService.getPlatformMetadata(platform);
        if (metadata == null) {
            return Result.failure(404, "未知平台: " + platform);
        }

        PlatformAvailability availability = new PlatformAvailability(
            metadata.platform(),
            metadata.displayName(),
            metadata.status().name(),
            metadata.status().getDisplayName(),
            metadata.isConnectable(),
            metadata.unavailableReason(),
            metadata.guideUrl(),
            metadata.supportedDataTypes()
        );

        return Result.success(availability);
    }

    /**
     * Get system configuration status for device integrations.
     */
    @GetMapping("/config/status")
    @Operation(summary = "获取配置状态", description = "返回设备同步系统的配置状态")
    public Result<ConfigStatus> getConfigStatus() {
        log.debug("Fetching device sync configuration status");

        boolean encryptionReady = encryptionService.isConfigured();
        List<String> availablePlatforms = availabilityService.getAvailablePlatforms();
        List<String> platformsRequiringAction = availabilityService.getPlatformsRequiringAction();

        ConfigStatus status = new ConfigStatus(
            encryptionReady,
            !availablePlatforms.isEmpty(),
            availablePlatforms,
            platformsRequiringAction
        );

        return Result.success(status);
    }

    /**
     * Prepare OAuth connection for a platform.
     * Returns OAuth authorization URL and stores connection state.
     */
    @PostMapping("/connect/{platform}/prepare")
    @Operation(summary = "准备OAuth连接", description = "获取OAuth授权URL并准备连接状态")
    public Result<OAuthPrepareResponse> prepareOAuthConnection(
            @Parameter(description = "平台标识")
            @PathVariable String platform) {

        log.info("Preparing OAuth connection for platform: {}", platform);

        // Check platform availability
        if (!availabilityService.isPlatformAvailable(platform)) {
            String reason = availabilityService.getUnavailableReason(platform);
            log.warn("Platform {} is not available: {}", platform, reason);
            return Result.failure(400, reason != null ? reason : "平台不可用");
        }

        // Get OAuth URL from orchestration service
        // Note: This requires the user to be authenticated; the actual userId
        // will be extracted from security context in the orchestration service
        String authUrl = orchestrationService.getAuthorizationUrl(null, platform);

        if (authUrl == null) {
            log.error("Failed to generate OAuth URL for platform: {}", platform);
            return Result.failure(500, "无法生成授权链接");
        }

        OAuthPrepareResponse response = new OAuthPrepareResponse(
            platform,
            authUrl,
            "/oauth/callback/" + platform,
            300 // 5 minutes TTL for OAuth state
        );

        log.info("OAuth URL generated for platform: {}", platform);
        return Result.success(response);
    }

    /**
     * Platform availability details.
     */
    public record PlatformAvailability(
        String platform,
        String displayName,
        String status,
        String statusLabel,
        boolean connectable,
        String unavailableReason,
        String guideUrl,
        List<String> supportedDataTypes
    ) {}

    /**
     * System configuration status.
     */
    public record ConfigStatus(
        boolean encryptionReady,
        boolean anyPlatformAvailable,
        List<String> availablePlatforms,
        List<String> platformsRequiringAction
    ) {}

    /**
     * OAuth preparation response.
     */
    public record OAuthPrepareResponse(
        String platform,
        String authUrl,
        String callbackPath,
        int ttlSeconds
    ) {}
}