package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.entity.DevicePlatformConfig;
import com.hhs.service.DevicePlatformConfigService;
import com.hhs.vo.DevicePlatformConfigRequest;
import com.hhs.vo.DevicePlatformConfigStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备平台配置管理控制器
 * 管理员接口 - 用于配置各平台的 OAuth 凭据
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/device/config")
@RequiredArgsConstructor
@Tag(name = "设备配置管理", description = "管理员 - 设备平台配置管理接口")
public class DevicePlatformConfigAdminController {

    private final DevicePlatformConfigService configService;

    /**
     * 获取所有平台配置状态
     */
    @GetMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取配置状态", description = "获取所有设备平台的配置状态")
    public Result<List<DevicePlatformConfigStatusVO>> getAllPlatformStatus() {
        List<DevicePlatformConfigStatusVO> status = configService.getAllPlatformStatus();
        return Result.success(status);
    }

    /**
     * 保存平台配置
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "保存平台配置", description = "保存或更新设备平台的 OAuth 配置")
    public Result<DevicePlatformConfig> saveConfig(@Valid @RequestBody DevicePlatformConfigRequest request) {
        log.info("Admin saving device platform config for: {}", request.getPlatform());
        DevicePlatformConfig saved = configService.saveConfig(request);
        return Result.success(saved);
    }

    /**
     * 测试平台配置
     */
    @PostMapping("/test/{platform}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "测试配置", description = "测试设备平台的配置是否有效")
    public Result<DevicePlatformConfigStatusVO> testConfig(@PathVariable String platform) {
        log.info("Admin testing device platform config for: {}", platform);
        DevicePlatformConfigStatusVO result = configService.testConfig(platform);
        return Result.success(result);
    }

    /**
     * 删除平台配置
     */
    @DeleteMapping("/{platform}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除配置", description = "删除设备平台的配置")
    public Result<Void> deleteConfig(@PathVariable String platform) {
        log.info("Admin deleting device platform config for: {}", platform);
        configService.deleteConfig(platform);
        return Result.success();
    }

    /**
     * 初始化默认配置
     */
    @PostMapping("/init")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "初始化默认配置", description = "从 YAML 配置初始化数据库配置")
    public Result<Void> initializeDefaults() {
        log.info("Admin initializing default device platform configs");
        configService.initializeDefaultConfigs();
        return Result.success();
    }
}