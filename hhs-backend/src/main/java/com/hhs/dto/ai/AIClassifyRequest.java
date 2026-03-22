package com.hhs.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI内容分类请求
 */
public record AIClassifyRequest(
    
    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题最多128个字符")
    String title,
    
    @NotBlank(message = "内容不能为空")
    @Size(max = 5000, message = "内容最多5000个字符")
    String content
) {
}
