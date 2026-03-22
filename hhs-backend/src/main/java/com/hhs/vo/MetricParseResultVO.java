package com.hhs.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI解析结果VO
 */
@Data
public class MetricParseResultVO {

    /**
     * 解析出的指标列表
     */
    private List<ParsedMetric> metrics;

    /**
     * 解析摘要
     */
    private String summary;

    /**
     * 警告信息（如数值异常）
     */
    private List<String> warnings;

    /**
     * 解析历史ID（用于确认录入）
     */
    private Long parseHistoryId;

    /**
     * 今日剩余解析次数
     */
    private Integer remainingCount;

    /**
     * 解析出的单个指标
     */
    @Data
    public static class ParsedMetric {

        /**
         * 指标标识
         */
        private String metricKey;

        /**
         * 指标名称（中文）
         */
        private String metricName;

        /**
         * 数值
         */
        private BigDecimal value;

        /**
         * 单位
         */
        private String unit;

        /**
         * 置信度 (0-1)
         */
        private BigDecimal confidence;

        /**
         * 指标分类: HEALTH, WELLNESS
         */
        private String category;

        /**
         * 记录日期
         */
        private String recordDate;

        /**
         * 是否选中（用于前端确认）
         */
        private Boolean selected = true;
    }
}