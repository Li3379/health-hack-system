package com.hhs.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 同步历史VO
 */
@Data
public class SyncHistoryVO {

    /**
     * 历史ID
     */
    private Long id;

    /**
     * 平台标识
     */
    private String platform;

    /**
     * 平台名称
     */
    private String platformName;

    /**
     * 同步类型
     */
    private String syncType;

    /**
     * 同步类型名称
     */
    private String syncTypeName;

    /**
     * 同步的指标数量
     */
    private Integer metricsCount;

    /**
     * 同步状态
     */
    private String status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 同步时间
     */
    private LocalDateTime syncTime;

    /**
     * 耗时（毫秒）
     */
    private Integer durationMs;
}