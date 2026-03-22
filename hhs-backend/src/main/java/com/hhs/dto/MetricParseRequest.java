package com.hhs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * AI解析健康指标请求DTO
 */
@Data
@Schema(description = "AI解析健康指标请求")
public class MetricParseRequest {

    /**
     * 用户输入内容
     */
    @NotBlank(message = "输入内容不能为空")
    @Size(min = 2, max = 2000, message = "输入内容长度需在2-2000字符之间")
    @Schema(description = "用户输入内容", example = "今天血糖5.6，心率72，走了8000步", requiredMode = Schema.RequiredMode.REQUIRED)
    private String input;

    /**
     * 输入类型: text, voice
     */
    @Schema(description = "输入类型", example = "text", allowableValues = {"text", "voice"})
    private String inputType = "text";

    /**
     * 记录日期（可选，默认当天）
     */
    @Schema(description = "记录日期", example = "2026-03-17")
    private LocalDate recordDate;
}