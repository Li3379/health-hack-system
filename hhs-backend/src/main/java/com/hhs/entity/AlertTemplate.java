package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Alert message template entity
 */
@Data
@TableName("alert_template")
public class AlertTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Template unique key (e.g., heartRate.high.mild)
     */
    private String templateKey;

    /**
     * Metric type
     */
    private String metricKey;

    /**
     * Severity level: CRITICAL, WARNING, INFO
     */
    private String severityLevel;

    /**
     * Trigger condition expression
     */
    private String conditionExpr;

    /**
     * Title template (supports {value} placeholder)
     */
    private String titleTemplate;

    /**
     * Message template (supports {value}, {threshold} placeholders)
     */
    private String messageTemplate;

    /**
     * Suggestion template
     */
    private String suggestionTemplate;

    /**
     * Priority (higher value = higher priority)
     */
    private Integer priority;

    /**
     * Is enabled: 0-disabled, 1-enabled
     */
    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}