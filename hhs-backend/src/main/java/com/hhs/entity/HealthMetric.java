package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.hhs.common.enums.MetricCategory;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康指标记录实体
 */
@Data
@TableName("health_metric")
public class HealthMetric {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long profileId;
    private String metricKey;  // Changed from metricType to metricKey for consistency
    private BigDecimal value;
    private String unit;
    private LocalDate recordDate;

    /**
     * Precise timestamp for the measurement.
     * Used for conflict resolution when multiple records have the same recordDate.
     */
    private LocalDateTime recordTime;

    private String trend;
    private LocalDateTime createTime;

    /**
     * Metric category: HEALTH (medical) or WELLNESS (lifestyle)
     * Defaults to HEALTH for backward compatibility
     */
    @TableField(value = "category")
    private MetricCategory category = MetricCategory.HEALTH;
}
