package com.hhs.dto.ai;

import java.time.LocalDateTime;

/**
 * 对话会话摘要（用于会话列表）
 */
public record ChatSessionVO(
        String sessionId,
        LocalDateTime lastMessageAt,
        String summary
) {
}
