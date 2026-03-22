package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.service.DevicePlatformConfigService;
import com.hhs.vo.DevicePlatformConfigStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备平台配置状态控制器
 * 提供配置状态查询 API（不暴露敏感信息）
 */
@Slf4j
@RestController
@RequestMapping("/api/device/config")
@RequiredArgsConstructor
@Tag(name = "设备配置状态", description = "设备平台配置状态查询接口")
public class DevicePlatformConfigController {

    private final DevicePlatformConfigService configService;

    /**
     * 获取所有平台的配置状态
     */
    @GetMapping("/status")
    @Operation(summary = "获取配置状态", description = "获取所有设备平台的配置状态")
    public Result<Map<String, Object>> getConfigStatus() {
        List<DevicePlatformConfigStatusVO> platforms = configService.getAllPlatformStatus();
        boolean encryptionKeyConfigured = configService.isEncryptionKeyConfigured();

        Map<String, Object> result = new HashMap<>();
        result.put("platforms", platforms);
        result.put("encryptionKeyConfigured", encryptionKeyConfigured);

        return Result.success(result);
    }

    /**
     * 获取指定平台的配置状态
     */
    @GetMapping("/status/{platform}")
    @Operation(summary = "获取平台配置状态", description = "获取指定设备平台的配置状态")
    public Result<DevicePlatformConfigStatusVO> getPlatformStatus(@PathVariable String platform) {
        DevicePlatformConfigStatusVO status = configService.getPlatformStatus(platform);
        return Result.success(status);
    }

    /**
     * 检查系统是否可以连接设备
     */
    @GetMapping("/ready")
    @Operation(summary = "检查设备同步就绪状态", description = "检查系统是否已配置好设备同步所需的凭据")
    public Result<Map<String, Object>> checkReady() {
        List<DevicePlatformConfigStatusVO> platforms = configService.getAllPlatformStatus();

        // Check if at least one platform is ready
        boolean anyReady = platforms.stream()
                .anyMatch(p -> Boolean.TRUE.equals(p.getOauthReady()));

        // Check if encryption key is configured
        boolean encryptionReady = configService.isEncryptionKeyConfigured();

        Map<String, Object> result = new HashMap<>();
        result.put("ready", anyReady && encryptionReady);
        result.put("encryptionReady", encryptionReady);
        result.put("anyPlatformReady", anyReady);

        // Add guidance for unconfigured platforms
        List<String> unconfiguredPlatforms = platforms.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getConfigured()))
                .map(DevicePlatformConfigStatusVO::getPlatform)
                .toList();
        result.put("unconfiguredPlatforms", unconfiguredPlatforms);

        return Result.success(result);
    }
}