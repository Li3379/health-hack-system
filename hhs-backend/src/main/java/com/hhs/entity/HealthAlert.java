package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Health alert entity for real-time warnings and notifications
 */
@Data
@TableName("health_alert")
public class HealthAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * Alert type: CRITICAL, WARNING, INFO, TREND, RECOVERY
     */
    private String alertType;

    /**
     * Alert severity: HIGH, MEDIUM, LOW
     */
    private String alertLevel;

    private String title;

    private String message;

    private String metricKey;

    private BigDecimal currentValue;

    private BigDecimal thresholdValue;

    /**
     * Occurrence count for merged alerts
     */
    private Integer occurrenceCount;

    /**
     * Last occurrence time for merged alerts
     */
    private LocalDateTime lastOccurrenceAt;

    /**
     * AI analysis result
     */
    private String aiAnalysis;

    /**
     * Health suggestion
     */
    private String suggestion;

    /**
     * Pushed channels (JSON array)
     */
    private String pushChannels;

    private Boolean isRead;

    private Boolean isAcknowledged;

    private LocalDateTime acknowledgedAt;

    /**
     * Resolution time
     */
    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;
}
