package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * User push channel configuration entity
 */
@Data
@TableName("user_push_config")
public class UserPushConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * Channel type: EMAIL, WECOM, FEISHU, WEBSOCKET
     */
    private String channelType;

    /**
     * Config key (e.g., webhook, email)
     */
    private String configKey;

    /**
     * Config value (webhook URL, email address, etc.)
     */
    private String configValue;

    /**
     * Is enabled: 0-disabled, 1-enabled
     */
    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}