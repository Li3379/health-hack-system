package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备连接配置实体
 * 用于存储用户与穿戴设备平台的连接信息
 *
 * 安全说明：
 * accessToken 和 refreshToken 字段需要加密存储。
 * Phase 4/5 实现设备同步时，应实现以下安全措施：
 * 1. 使用 AES-256 加密令牌
 * 2. 加密密钥通过环境变量配置或使用密钥管理服务
 * 3. 令牌在传输过程中使用 HTTPS 保护
 * 4. 实现令牌定期轮换机制
 *
 * 参考实现方案：
 * - Spring Security Crypto: TextEncryptor
 * - Jasypt: @EncryptProperty
 * - 自定义 TypeHandler: 自动加解密
 */
@Data
@TableName("device_connection")
public class DeviceConnection {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 平台标识: huawei, xiaomi, wechat, apple
     */
    private String platform;

    /**
     * 平台用户标识
     */
    private String platformUserId;

    /**
     * 访问令牌（加密存储）
     */
    private String accessToken;

    /**
     * 刷新令牌（加密存储）
     */
    private String refreshToken;

    /**
     * 令牌过期时间
     */
    private LocalDateTime tokenExpireAt;

    /**
     * 最后同步时间
     */
    private LocalDateTime lastSyncAt;

    /**
     * 是否启用自动同步
     */
    private Boolean syncEnabled;

    /**
     * 连接状态: connected, expired, disconnected
     */
    private String status;

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
        HUAWEI("华为运动健康"),
        XIAOMI("小米运动"),
        WECHAT("微信运动"),
        APPLE("Apple Health");

        private final String displayName;

        Platform(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 连接状态枚举
     */
    public enum Status {
        CONNECTED("已连接"),
        EXPIRED("已过期"),
        DISCONNECTED("已断开");

        private final String displayName;

        Status(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}