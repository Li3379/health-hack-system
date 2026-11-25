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

    /**
     * Delivery retries already attempted by the compensation job.
     *
     * <p>0 on the original attempt. The job increments this before each retry and gives up at its
     * configured max, so a permanently broken channel (a revoked SMTP authorization code) stops
     * generating traffic instead of retrying forever.
     */
    private Integer retryCount;

    /**
     * When the compensation job may retry this row; {@code null} means "not scheduled".
     *
     * <p>Rows written before this column existed stay {@code null}, which is also what keeps the
     * job from re-litigating old failures — see {@code migration/v1.7_push_retry.sql}.
     */
    private LocalDateTime nextRetryAt;
}