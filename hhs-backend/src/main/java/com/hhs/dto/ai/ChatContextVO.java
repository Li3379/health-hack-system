package com.hhs.dto.ai;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前会话的最近 N 轮上下文（供前端或 RAG 展示）
 */
public record ChatContextVO(
        String sessionId,
        List<ContextTurn> turns
) {
    public record ContextTurn(String question, String answer, LocalDateTime createTime) {}
}
