package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * User personalized threshold entity
 * Allows users to override default alert rules
 */
@Data
@TableName("user_threshold")
public class UserThreshold {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String metricKey;

    private BigDecimal warningHigh;

    private BigDecimal criticalHigh;

    private BigDecimal warningLow;

    private BigDecimal criticalLow;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
