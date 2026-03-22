package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Push history entity for tracking all push attempts
 */
@Data
@TableName("push_history")
public class PushHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long alertId;

    /**
     * Channel type: WEBSOCKET, EMAIL, WECOM, FEISHU
     */
    private String channelType;

    /**
     * Push status: SUCCESS, FAILED, SKIPPED
     */
    private String status;

    /**
     * Result message or error description
     */
    private String message;

    /**
     * Push timestamp
     */
    private LocalDateTime pushedAt;
}