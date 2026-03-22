package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Alert rule entity defining threshold-based alert triggers
 */
@Data
@TableName("alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String metricKey;

    /**
     * Warning high threshold
     */
    private BigDecimal warningHigh;

    /**
     * Critical high threshold
     */
    private BigDecimal criticalHigh;

    /**
     * Warning low threshold
     */
    private BigDecimal warningLow;

    /**
     * Critical low threshold
     */
    private BigDecimal criticalLow;

    private Boolean enabled;

    private String description;

    private LocalDateTime createdAt;
}
