package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 风险评估记录实体
 */
@Data
@TableName("risk_assessment")
public class RiskAssessment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long profileId;
    private String diseaseName;
    private String riskLevel;
    private Integer riskScore;
    private String suggestion;
    private LocalDateTime createTime;
}
