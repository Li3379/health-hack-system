package com.hhs.service.impl;

import com.hhs.config.DeviceOAuthProperties;
import com.hhs.dto.PlatformMetadata;
import com.hhs.enums.PlatformCapability;
import com.hhs.enums.PlatformStatus;
import com.hhs.service.DeviceSyncOrchestrationService;
import com.hhs.service.PlatformAvailabilityService;
import com.hhs.service.TokenEncryptionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of Platform Availability Service.
 *
 * <p>Platform availability is determined by:
 * <ul>
 *   <li><strong>Huawei/Xiaomi:</strong> OAuth configuration + service implementation</li>
 *   <li><strong>WeChat:</strong> Always REQUIRES_MINI_PROGRAM (technical limitation)</li>
 *   <li><strong>Apple:</strong> Always REQUIRES_APP (technical limitation)</li>
 * </ul>
 */
@Slf4j
@Service
public class PlatformAvailabilityServiceImpl implements PlatformAvailabilityService {

    private final DeviceOAuthProperties oAuthProperties;
    private final TokenEncryptionService encryptionService;
    private final DeviceSyncOrchestrationService orchestrationService;

    // Static platform definitions
    private static final Map<String, PlatformDefinition> PLATFORM_DEFINITIONS = Map.of(
        "huawei", new PlatformDefinition(
            "华为运动健康",
            "huawei",
            List.of(PlatformCapability.WEB_OAUTH, PlatformCapability.REALTIME_SYNC, PlatformCapability.HISTORICAL_DATA),
            List.of("heart_rate", "steps", "sleep", "blood_pressure", "blood_glucose", "spo2"),
            null,
            "https://developer.huawei.com/consumer/cn/huaweihealthkit/"
        ),
        "xiaomi", new PlatformDefinition(
            "小米运动",
            "xiaomi",
            List.of(PlatformCapability.WEB_OAUTH, PlatformCapability.REALTIME_SYNC),
            List.of("heart_rate", "steps", "sleep"),
            null,
            "https://dev.mi.com/console/"
        ),
        "wechat", new PlatformDefinition(
            "微信运动",
            "wechat",
            List.of(PlatformCapability.MINI_PROGRAM),
            List.of("steps"),
            "微信运动需要通过小程序获取数据，不支持网页授权",
            "weixin://"
        ),
        "apple", new PlatformDefinition(
            "Apple Health",
            "apple",
            List.of(PlatformCapability.NATIVE_APP),
            List.of("heart_rate", "steps", "sleep", "blood_pressure", "blood_glucose"),
            "Apple Health 需要 iOS 应用获取数据，不支持网页授权",
            "https://developer.apple.com/healthkit/"
        )
    );

    /**
     * Internal record for static platform definitions.
     */
    private record PlatformDefinition(
        String displayName,
        String icon,
        List<PlatformCapability> capabilities,
        List<String> supportedDataTypes,
        String unavailableReason,
        String guideUrl
    ) {}

    public PlatformAvailabilityServiceImpl(
            DeviceOAuthProperties oAuthProperties,
            TokenEncryptionService encryptionService,
            DeviceSyncOrchestrationService orchestrationService) {
        this.oAuthProperties = oAuthProperties;
        this.encryptionService = encryptionService;
        this.orchestrationService = orchestrationService;
        log.info("PlatformAvailabilityService initialized with {} platform definitions", PLATFORM_DEFINITIONS.size());
    }

    @Override
    public List<PlatformMetadata> getAllPlatformMetadata() {
        return PLATFORM_DEFINITIONS.entrySet().stream()
                .map(entry -> buildMetadata(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(m -> m.displayName()))
                .collect(Collectors.toList());
    }

    @Override
    public PlatformMetadata getPlatformMetadata(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }

        PlatformDefinition definition = PLATFORM_DEFINITIONS.get(platform.toLowerCase());
        if (definition == null) {
            log.warn("Unknown platform requested: {}", platform);
            return null;
        }

        return buildMetadata(platform.toLowerCase(), definition);
    }

    @Override
    public boolean isPlatformAvailable(String platform) {
        if (platform == null || platform.isBlank()) {
            return false;
        }

        PlatformMetadata metadata = getPlatformMetadata(platform);
        return metadata != null && metadata.isConnectable();
    }

    @Override
    public String getUnavailableReason(String platform) {
        if (platform == null || platform.isBlank()) {
            return "未知平台";
        }

        PlatformMetadata metadata = getPlatformMetadata(platform);
        if (metadata == null) {
            return "未知平台: " + platform;
        }

        if (metadata.isConnectable()) {
            return null; // Platform is available
        }

        return metadata.unavailableReason() != null
            ? metadata.unavailableReason()
            : "该平台暂不可用";
    }

    @Override
    public List<String> getAvailablePlatforms() {
        return getAllPlatformMetadata().stream()
                .filter(PlatformMetadata::isConnectable)
                .map(PlatformMetadata::platform)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getPlatformsRequiringAction() {
        return getAllPlatformMetadata().stream()
                .filter(PlatformMetadata::requiresAction)
                .map(PlatformMetadata::platform)
                .collect(Collectors.toList());
    }

    /**
     * Build PlatformMetadata with dynamic status based on configuration.
     */
    private PlatformMetadata buildMetadata(String platform, PlatformDefinition definition) {
        PlatformStatus status = determineStatus(platform);

        return PlatformMetadata.builder()
                .platform(platform)
                .displayName(definition.displayName())
                .status(status)
                .capabilities(definition.capabilities())
                .supportedDataTypes(definition.supportedDataTypes())
                .unavailableReason(determineUnavailableReason(platform, status, definition))
                .guideUrl(definition.guideUrl())
                .icon(definition.icon())
                .build();
    }

    /**
     * Determine the status of a platform based on its type and configuration.
     */
    private PlatformStatus determineStatus(String platform) {
        return switch (platform.toLowerCase()) {
            case "huawei", "xiaomi" -> determineWebOAuthStatus(platform);
            case "wechat" -> PlatformStatus.REQUIRES_MINI_PROGRAM;
            case "apple" -> PlatformStatus.REQUIRES_APP;
            default -> PlatformStatus.UNAVAILABLE;
        };
    }

    /**
     * Determine status for platforms that support Web OAuth.
     */
    private PlatformStatus determineWebOAuthStatus(String platform) {
        // Check if service implementation exists
        boolean serviceRegistered = orchestrationService.isPlatformRegistered(platform);
        if (!serviceRegistered) {
            log.debug("Platform {} is not registered in orchestration service", platform);
            // For xiaomi, service might not be implemented yet
            return "xiaomi".equals(platform) && !serviceRegistered
                ? PlatformStatus.COMING_SOON
                : PlatformStatus.UNAVAILABLE;
        }

        // Check OAuth configuration
        boolean oauthConfigured = oAuthProperties.hasValidCredentials(platform);
        if (!oauthConfigured) {
            log.debug("Platform {} OAuth is not configured", platform);
            return PlatformStatus.NOT_CONFIGURED;
        }

        // Check encryption service
        boolean encryptionReady = encryptionService.isConfigured();
        if (!encryptionReady) {
            log.debug("Encryption service is not configured");
            return PlatformStatus.NOT_CONFIGURED;
        }

        return PlatformStatus.AVAILABLE;
    }

    /**
     * Determine the unavailable reason based on status.
     */
    private String determineUnavailableReason(String platform, PlatformStatus status, PlatformDefinition definition) {
        return switch (status) {
            case AVAILABLE -> null;
            case REQUIRES_MINI_PROGRAM -> definition.unavailableReason();
            case REQUIRES_APP -> definition.unavailableReason();
            case COMING_SOON -> "该平台服务正在开发中，敬请期待";
            case NOT_CONFIGURED -> {
                if (!encryptionService.isConfigured()) {
                    yield "系统加密服务未配置，请联系管理员";
                }
                yield "平台 OAuth 未配置，请联系管理员配置 " + platform.toUpperCase() + "_CLIENT_ID 和 " + platform.toUpperCase() + "_CLIENT_SECRET";
            }
            case UNAVAILABLE -> "该平台服务暂不可用";
        };
    }
}