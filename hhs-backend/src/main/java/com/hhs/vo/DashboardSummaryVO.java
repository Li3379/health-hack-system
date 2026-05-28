package com.hhs.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 仪表盘汇总VO
 * 一次请求返回所有关键指标
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryVO {

    /**
     * 最新健康评分 (0-100)
     */
    private BigDecimal healthScore;

    /**
     * 健康评分等级: EXCELLENT, GOOD, FAIR, POOR, NO_DATA
     */
    private String healthScoreLevel;

    /**
     * 今日健康指标录入数量
     */
    private int todayHealthMetricsCount;

    /**
     * 今日保健指标录入数量
     */
    private int todayWellnessMetricsCount;

    /**
     * 本周健康指标录入总数
     */
    private int weekHealthMetricsCount;

    /**
     * 本周保健指标录入总数
     */
    private int weekWellnessMetricsCount;

    /**
     * 未读告警数量
     */
    private int unreadAlertsCount;

    /**
     * 高严重度告警数量
     */
    private int highSeverityAlertsCount;

    /**
     * 累计设备同步次数
     */
    private int totalDeviceSyncCount;

    /**
     * 累计AI识别次数
     */
    private int totalAiRecognizeCount;

    /**
     * 今日设备同步次数
     */
    private int todayDeviceSyncCount;

    /**
     * 今日AI识别次数
     */
    private int todayAiRecognizeCount;

    /**
     * 本周活跃天数
     */
    private int weekActiveDays;
}
