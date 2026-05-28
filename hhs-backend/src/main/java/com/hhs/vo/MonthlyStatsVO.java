package com.hhs.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 月统计数据VO
 * 包含本月数据、上月数据及变化百分比
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyStatsVO {

    /**
     * 本月健康指标录入数量
     */
    private int currentHealthMetricsCount;

    /**
     * 上月健康指标录入数量
     */
    private int previousHealthMetricsCount;

    /**
     * 健康指标数量变化百分比
     */
    private BigDecimal healthMetricsChangePercent;

    /**
     * 本月保健指标录入数量
     */
    private int currentWellnessMetricsCount;

    /**
     * 上月保健指标录入数量
     */
    private int previousWellnessMetricsCount;

    /**
     * 保健指标数量变化百分比
     */
    private BigDecimal wellnessMetricsChangePercent;

    /**
     * 本月设备同步次数
     */
    private int currentDeviceSyncCount;

    /**
     * 上月设备同步次数
     */
    private int previousDeviceSyncCount;

    /**
     * 设备同步次数变化百分比
     */
    private BigDecimal deviceSyncChangePercent;

    /**
     * 本月AI识别次数
     */
    private int currentAiRecognizeCount;

    /**
     * 上月AI识别次数
     */
    private int previousAiRecognizeCount;

    /**
     * AI识别次数变化百分比
     */
    private BigDecimal aiRecognizeChangePercent;

    /**
     * 本月平均健康评分
     */
    private BigDecimal currentAvgScore;

    /**
     * 上月平均健康评分
     */
    private BigDecimal previousAvgScore;

    /**
     * 平均健康评分变化百分比
     */
    private BigDecimal avgScoreChangePercent;

    /**
     * 本月活跃天数
     */
    private int activeDays;
}
