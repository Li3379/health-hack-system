package com.hhs.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 趋势数据VO
 * 用于折线图/迷你图展示
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendDataVO {

    /**
     * 指标类型 (metricKey)
     */
    private String metricKey;

    /**
     * 指标显示名称
     */
    private String metricDisplayName;

    /**
     * 单位
     */
    private String unit;

    /**
     * 日期列表
     */
    private List<LocalDate> dates;

    /**
     * 数值列表 (与dates一一对应)
     */
    private List<BigDecimal> values;
}
