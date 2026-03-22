package com.hhs.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI对话请求
 */
public record AIChatRequest(
    
    @NotBlank(message = "会话ID不能为空")
    String sessionId,
    
    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题最多500个字符")
    String question
) {
}
