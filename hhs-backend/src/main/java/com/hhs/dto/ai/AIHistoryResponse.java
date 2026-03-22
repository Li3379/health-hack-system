package com.hhs.dto.ai;

import java.util.List;

/**
 * AI对话历史响应
 */
public record AIHistoryResponse(
    /**
     * 对话历史列表
     */
    List<ConversationVO> history,
    
    /**
     * 剩余次数
     */
    Integer remainingCount
) {
}
