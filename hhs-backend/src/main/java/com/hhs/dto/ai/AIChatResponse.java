package com.hhs.dto.ai;

/**
 * AI对话响应
 */
public record AIChatResponse(
    /**
     * AI回答
     */
    String answer,
    
    /**
     * 会话ID
     */
    String sessionId,
    
    /**
     * 剩余次数
     */
    Integer remainingCount
) {
}
