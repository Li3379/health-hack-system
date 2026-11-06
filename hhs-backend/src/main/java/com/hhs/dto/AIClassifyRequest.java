package com.hhs.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * AI Classify Request DTO
 * Used for content classification requests
 */
@Data
public class AIClassifyRequest {

    @NotBlank(message = "内容不能为空")
    @Size(min = 1, max = 10000, message = "内容长度需在 1 至 10000 个字符之间")
    private String content;

    @Size(max = 100, message = "分类长度不能超过 100 个字符")
    private String category;

    @Size(max = 500, message = "上下文长度不能超过 500 个字符")
    private String context;
}
