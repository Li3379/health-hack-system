package com.hhs.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * OCR健康图片识别结果VO
 * 用于体检报告、药品标签、营养标签等图片识别
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OCR健康图片识别结果")
public class OcrHealthResultVO {

    /**
     * 识别状态: success, failed
     */
    @Schema(description = "识别状态", example = "success")
    private String status;

    /**
     * 识别类型: report(体检报告), medicine(药品标签), nutrition(营养标签)
     */
    @Schema(description = "识别类型", example = "report")
    private String ocrType;

    /**
     * 识别出的健康指标列表
     */
    @Schema(description = "识别出的健康指标列表")
    @Builder.Default
    private List<RecognizedMetric> metrics = new ArrayList<>();

    /**
     * 原始识别文本
     */
    @Schema(description = "原始识别文本")
    private String rawText;

    /**
     * 识别摘要
     */
    @Schema(description = "识别摘要", example = "成功识别出3个健康指标")
    private String summary;

    /**
     * 错误信息（识别失败时）
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    /**
     * OCR记录ID（用于确认录入）
     */
    @Schema(description = "OCR记录ID")
    private Long ocrRecordId;

    /**
     * 是否使用了模拟数据（OCR服务未配置时为true）
     */
    @Schema(description = "是否使用了模拟数据", example = "false")
    private Boolean usedMockData;

    /**
     * 警告信息（如OCR服务未配置等）
     */
    @Schema(description = "警告信息")
    private String warning;

    /**
     * 识别出的单个指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "识别出的单个指标")
    public static class RecognizedMetric {

        /**
         * 指标标识
         */
        @Schema(description = "指标标识", example = "glucose")
        private String metricKey;

        /**
         * 指标名称（中文）
         */
        @Schema(description = "指标名称", example = "血糖")
        private String name;

        /**
         * 数值
         */
        @Schema(description = "数值", example = "5.6")
        private BigDecimal value;

        /**
         * 单位
         */
        @Schema(description = "单位", example = "mmol/L")
        private String unit;

        /**
         * 参考范围
         */
        @Schema(description = "参考范围", example = "3.9-6.1")
        private String referenceRange;

        /**
         * 是否异常
         */
        @Schema(description = "是否异常", example = "false")
        private Boolean abnormal;

        /**
         * 置信度 (0-1)
         */
        @Schema(description = "置信度", example = "0.95")
        private BigDecimal confidence;

        /**
         * 指标分类: HEALTH, WELLNESS
         */
        @Schema(description = "指标分类", example = "HEALTH")
        private String category;

        /**
         * 记录日期
         */
        @Schema(description = "记录日期", example = "2026-03-17")
        private String recordDate;

        /**
         * 是否选中（用于前端确认）
         */
        @Schema(description = "是否选中", example = "true")
        @Builder.Default
        private Boolean selected = true;
    }

    /**
     * 创建成功结果
     */
    public static OcrHealthResultVO success(String ocrType, List<RecognizedMetric> metrics, String rawText) {
        return OcrHealthResultVO.builder()
                .status("success")
                .ocrType(ocrType)
                .metrics(metrics)
                .rawText(rawText)
                .summary(String.format("成功识别出%d个健康指标", metrics.size()))
                .build();
    }

    /**
     * 创建成功结果（带模拟数据标识）
     */
    public static OcrHealthResultVO successWithMockData(String ocrType, List<RecognizedMetric> metrics, String rawText) {
        return OcrHealthResultVO.builder()
                .status("success")
                .ocrType(ocrType)
                .metrics(metrics)
                .rawText(rawText)
                .summary(String.format("成功识别出%d个健康指标", metrics.size()))
                .usedMockData(true)
                .warning("OCR服务未配置，当前显示的是模拟数据。请联系管理员配置百度OCR服务以使用真实识别功能。")
                .build();
    }

    /**
     * 创建失败结果
     */
    public static OcrHealthResultVO failed(String errorMessage) {
        return OcrHealthResultVO.builder()
                .status("failed")
                .errorMessage(errorMessage)
                .summary("识别失败: " + errorMessage)
                .metrics(new ArrayList<>())
                .build();
    }
}