package com.hhs.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OCR识别历史记录VO
 * 用于展示用户的OCR识别历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OCR识别历史记录")
public class OcrHistoryVO {

    /**
     * 记录ID
     */
    @Schema(description = "记录ID", example = "1")
    private Long id;

    /**
     * 识别类型: report, medicine, nutrition
     */
    @Schema(description = "识别类型", example = "report")
    private String ocrType;

    /**
     * 识别类型显示名称
     */
    @Schema(description = "识别类型显示名称", example = "体检报告")
    private String ocrTypeDisplay;

    /**
     * 识别状态: success, failed
     */
    @Schema(description = "识别状态", example = "success")
    private String status;

    /**
     * 识别出的指标数量
     */
    @Schema(description = "识别出的指标数量", example = "5")
    private Integer metricsCount;

    /**
     * 是否已确认录入
     */
    @Schema(description = "是否已确认录入", example = "true")
    private Boolean confirmed;

    /**
     * 原始文件名
     */
    @Schema(description = "原始文件名", example = "report.jpg")
    private String originalFilename;

    /**
     * 识别耗时(毫秒)
     */
    @Schema(description = "识别耗时(毫秒)", example = "1500")
    private Integer durationMs;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-03-17T10:30:00")
    private LocalDateTime createTime;

    /**
     * 错误信息（失败时）
     */
    @Schema(description = "错误信息")
    private String errorMessage;

    /**
     * 获取OCR类型显示名称
     */
    public String getOcrTypeDisplay() {
        if (ocrType == null) return null;
        return switch (ocrType) {
            case "report" -> "体检报告";
            case "medicine" -> "药品标签";
            case "nutrition" -> "营养标签";
            default -> ocrType;
        };
    }
}