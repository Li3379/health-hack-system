package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Health goal entity
 * Tracks user-defined health goals with progress and completion
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("health_goals")
public class HealthGoal {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private String metricKey;

    private Double targetValue;

    private Double currentValue;

    private String unit;

    /**
     * Goal status: active, completed, abandoned
     */
    private String status;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDateTime completedAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
