package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Health score history entity
 * Stores daily health score snapshots for trend tracking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("health_score_history")
public class HealthScoreHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private LocalDate scoreDate;

    private BigDecimal overallScore;

    private BigDecimal cardiovascularScore;

    private BigDecimal metabolicScore;

    private BigDecimal weightScore;

    private BigDecimal lifestyleScore;

    private String factorsSnapshot;

    private LocalDateTime createdAt;
}
