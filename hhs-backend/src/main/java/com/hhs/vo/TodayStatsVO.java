package com.hhs.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 今日统计数据VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodayStatsVO {

    /**
     * 今日健康指标数量
     */
    private int healthMetricsCount;

    /**
     * 今日保健指标数量
     */
    private int wellnessMetricsCount;

    /**
     * 今日设备同步次数
     */
    private int deviceSyncCount;

    /**
     * 今日AI识别次数
     */
    private int aiRecognizeCount;
}