package com.hhs.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI解析历史记录VO
 * 用于展示用户的AI解析历史
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI解析历史记录")
public class AiParseHistoryVO {

    /**
     * 记录ID
     */
    @Schema(description = "记录ID", example = "1")
    private Long id;

    /**
     * 用户输入文本
     */
    @Schema(description = "用户输入文本", example = "今天血糖5.6，心率72")
    private String inputText;

    /**
     * 输入类型: text, voice
     */
    @Schema(description = "输入类型", example = "text")
    private String inputType;

    /**
     * 输入类型显示名称
     */
    @Schema(description = "输入类型显示名称", example = "文本输入")
    private String inputTypeDisplay;

    /**
     * 解析出的指标数量
     */
    @Schema(description = "解析出的指标数量", example = "2")
    private Integer metricsCount;

    /**
     * 是否已确认录入
     */
    @Schema(description = "是否已确认录入", example = "true")
    private Boolean confirmed;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2026-03-17T10:30:00")
    private LocalDateTime createTime;

    /**
     * 获取输入类型显示名称
     */
    public String getInputTypeDisplay() {
        if (inputType == null) return null;
        return switch (inputType) {
            case "text" -> "文本输入";
            case "voice" -> "语音输入";
            default -> inputType;
        };
    }
}