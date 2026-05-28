package com.hhs.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 周统计数据VO
 * 包含本周数据、上周数据及变化百分比
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyStatsVO {

    /**
     * 本周健康指标录入数量
     */
    private int currentHealthMetricsCount;

    /**
     * 上周健康指标录入数量
     */
    private int previousHealthMetricsCount;

    /**
     * 健康指标数量变化百分比
     */
    private BigDecimal healthMetricsChangePercent;

    /**
     * 本周保健指标录入数量
     */
    private int currentWellnessMetricsCount;

    /**
     * 上周保健指标录入数量
     */
    private int previousWellnessMetricsCount;

    /**
     * 保健指标数量变化百分比
     */
    private BigDecimal wellnessMetricsChangePercent;

    /**
     * 本周设备同步次数
     */
    private int currentDeviceSyncCount;

    /**
     * 上周设备同步次数
     */
    private int previousDeviceSyncCount;

    /**
     * 设备同步次数变化百分比
     */
    private BigDecimal deviceSyncChangePercent;

    /**
     * 本周AI识别次数
     */
    private int currentAiRecognizeCount;

    /**
     * 上周AI识别次数
     */
    private int previousAiRecognizeCount;

    /**
     * AI识别次数变化百分比
     */
    private BigDecimal aiRecognizeChangePercent;

    /**
     * 本周平均健康评分
     */
    private BigDecimal currentAvgScore;

    /**
     * 上周平均健康评分
     */
    private BigDecimal previousAvgScore;

    /**
     * 平均健康评分变化百分比
     */
    private BigDecimal avgScoreChangePercent;
}
