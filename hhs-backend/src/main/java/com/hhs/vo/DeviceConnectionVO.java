package com.hhs.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备连接信息VO
 */
@Data
public class DeviceConnectionVO {

    /**
     * 平台标识
     */
    private String platform;

    /**
     * 平台名称
     */
    private String platformName;

    /**
     * 连接状态
     */
    private String status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 最后同步时间
     */
    private LocalDateTime lastSyncAt;

    /**
     * 是否启用自动同步
     */
    private Boolean syncEnabled;

    /**
     * 平台用户标识
     */
    private String platformUserId;
}