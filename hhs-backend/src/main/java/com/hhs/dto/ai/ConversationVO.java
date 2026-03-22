package com.hhs.dto.ai;

import java.time.LocalDateTime;

/**
 * 对话历史VO
 */
public record ConversationVO(
    Long id,
    String question,
    String answer,
    LocalDateTime createTime
) {
}
