package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Alert merge log entity for intelligent deduplication
 */
@Data
@TableName("alert_merge_log")
public class AlertMergeLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * Primary alert ID
     */
    private Long primaryAlertId;

    /**
     * Merged alert ID list (JSON array)
     */
    private String mergedAlertIds;

    /**
     * Merge count
     */
    private Integer mergeCount;

    /**
     * Metric type
     */
    private String metricKey;

    /**
     * First occurrence time
     */
    private LocalDateTime firstOccurrenceAt;

    /**
     * Last occurrence time
     */
    private LocalDateTime lastOccurrenceAt;

    private LocalDateTime createdAt;
}