package com.hhs.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 健康报告实体
 * 存储用户健康报告历史记录
 */
@Data
@TableName("health_report")
public class HealthReport {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 报告唯一标识
     */
    private String reportId;

    /**
     * 综合评分 (0-100)
     */
    private Integer overallScore;

    /**
     * 评分等级 (EXCELLENT, GOOD, FAIR, POOR)
     */
    private String scoreLevel;

    /**
     * 维度分析数据 (JSON)
     */
    private String dimensions;

    /**
     * 风险提示数据 (JSON)
     */
    private String riskAlerts;

    /**
     * 改善建议数据 (JSON)
     */
    private String suggestions;

    /**
     * 健康总结
     */
    private String summary;

    /**
     * 用户信息快照 (JSON)
     */
    private String userInfo;

    /**
     * 报告生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 记录创建时间
     */
    private LocalDateTime createdAt;
}