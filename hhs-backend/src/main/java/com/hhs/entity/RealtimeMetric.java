package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Real-time health metric entity
 * Optimized for high-frequency writes with partitioning
 *
 * Note: This table uses a composite primary key (id, created_at) to support MySQL partitioning.
 * The id field is auto-increment, and created_at is included in the primary key to satisfy
 * MySQL's requirement that ALL unique keys (including the primary key) in a partitioned table
 * must include all columns used in the partitioning function. This design does not affect
 * normal CRUD operations through MyBatis-Plus.
 */
@Data
@TableName("realtime_metric")
public class RealtimeMetric implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Primary key component 1: Auto-increment ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * Metric type: heartRate, systolicBP, diastolicBP, weight, glucose, bmi, temperature
     */
    private String metricKey;

    private BigDecimal value;

    private String unit;

    /**
     * Data source: manual, device, api
     */
    private String source;

    /**
     * Data quality score: 0.0 - 1.0
     * Used for device-entered metrics to indicate reliability
     */
    private BigDecimal qualityScore;

    /**
     * Primary key component 2: Creation timestamp (included for partitioning support)
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * Equality check based on composite primary key
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RealtimeMetric that = (RealtimeMetric) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(createdAt, that.createdAt);
    }

    /**
     * Hash code based on composite primary key
     */
    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt);
    }
}
