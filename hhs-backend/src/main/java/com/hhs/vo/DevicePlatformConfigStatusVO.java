package com.hhs.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备平台配置状态 VO
 * 用于前端展示配置状态（不包含敏感信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePlatformConfigStatusVO {

    /**
     * 平台标识
     */
    private String platform;

    /**
     * 平台名称
     */
    private String platformName;

    /**
     * 是否已配置
     */
    private Boolean configured;

    /**
     * OAuth 是否就绪
     */
    private Boolean oauthReady;

    /**
     * 最后测试时间
     */
    private LocalDateTime lastTestTime;

    /**
     * 测试结果
     */
    private String testResult;

    /**
     * 缺失的配置项
     */
    private List<String> missingConfig;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 支持的健康数据类型
     */
    private List<String> supportedDataTypes;

    /**
     * 获取指南链接
     */
    private String guideUrl;
}