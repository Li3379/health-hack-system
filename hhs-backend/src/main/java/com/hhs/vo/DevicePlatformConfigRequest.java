package com.hhs.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 设备平台配置请求 VO
 * 用于管理员更新平台配置
 */
@Data
public class DevicePlatformConfigRequest {

    /**
     * 平台标识
     */
    @NotBlank(message = "平台标识不能为空")
    private String platform;

    /**
     * OAuth 客户端 ID
     */
    @NotBlank(message = "客户端ID不能为空")
    private String clientId;

    /**
     * OAuth 客户端密钥
     */
    @NotBlank(message = "客户端密钥不能为空")
    private String clientSecret;

    /**
     * OAuth 授权 URL
     */
    private String authUrl;

    /**
     * Token 交换 URL
     */
    private String tokenUrl;

    /**
     * 回调 URL
     */
    private String redirectUri;

    /**
     * 权限范围
     */
    private List<String> scopes;

    /**
     * 是否启用
     */
    private Boolean enabled;
}