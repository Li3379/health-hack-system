package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备平台配置实体
 * 存储各穿戴设备平台的 OAuth 配置信息
 *
 * <p>安全说明：
 * clientId 和 clientSecret 字段需要加密存储。
 * 只有管理员可以配置和查看这些凭据。
 */
@Data
@TableName("device_platform_config")
public class DevicePlatformConfig {

    @TableId(type = IdType.INPUT)
    private String platform;

    /**
     * OAuth 客户端 ID（加密存储）
     */
    private String clientId;

    /**
     * OAuth 客户端密钥（加密存储）
     */
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
     * 权限范围 (JSON 数组)
     */
    private String scopes;

    /**
     * 是否已配置完成
     */
    private Boolean configured;

    /**
     * 最后测试时间
     */
    private LocalDateTime lastTestTime;

    /**
     * 测试结果: success, failed, pending
     */
    private String testResult;

    /**
     * 测试错误信息
     */
    private String testErrorMessage;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 平台名称枚举
     */
    public enum Platform {
        HUAWEI("huawei", "华为运动健康"),
        XIAOMI("xiaomi", "小米运动"),
        WECHAT("wechat", "微信运动"),
        APPLE("apple", "Apple Health");

        private final String code;
        private final String displayName;

        Platform(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static Platform fromCode(String code) {
            for (Platform p : values()) {
                if (p.code.equalsIgnoreCase(code)) {
                    return p;
                }
            }
            return null;
        }
    }
}