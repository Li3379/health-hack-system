package com.hhs.service.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.AIConversation;
import com.hhs.mapper.AIConversationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * AI conversation session management component.
 * Handles session lifecycle: creation, retrieval, deletion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationService {

    private final AIConversationMapper conversationMapper;

    /**
     * Generate a new unique session ID.
     */
    public String createSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Get conversation history for a session.
     */
    @Transactional(timeout = 30, readOnly = true)
    public List<AIConversation> getHistory(String sessionId, Long userId) {
        log.debug("Fetching conversation history: sessionId={}, userId={}", sessionId, userId);
        return conversationMapper.selectList(
                new LambdaQueryWrapper<AIConversation>()
                        .eq(AIConversation::getSessionId, sessionId)
                        .eq(userId != null, AIConversation::getUserId, userId)
                        .orderByAsc(AIConversation::getCreateTime)
                        .last("LIMIT 50")
        );
    }

    /**
     * Delete all conversations in a session.
     */
    @Transactional(timeout = 30)
    public void deleteSession(String sessionId, Long userId) {
        log.debug("Deleting conversation session: sessionId={}, userId={}", sessionId, userId);
        conversationMapper.delete(
                new LambdaQueryWrapper<AIConversation>()
                        .eq(AIConversation::getSessionId, sessionId)
                        .eq(Objects.nonNull(userId), AIConversation::getUserId, userId)
        );
    }

    /**
     * Get recent conversations for a user (for session listing).
     */
    @Transactional(timeout = 30, readOnly = true)
    public List<AIConversation> getRecentConversations(Long userId, int limit) {
        log.debug("Fetching recent conversations: userId={}, limit={}", userId, limit);
        return conversationMapper.selectList(
                new LambdaQueryWrapper<AIConversation>()
                        .eq(AIConversation::getUserId, userId)
                        .orderByDesc(AIConversation::getCreateTime)
                        .last("LIMIT " + limit)
        );
    }

    /**
     * Get conversations by session ID (for context building).
     */
    @Transactional(timeout = 30, readOnly = true)
    public List<AIConversation> getBySessionId(String sessionId) {
        log.debug("Fetching conversations by session: sessionId={}", sessionId);
        return conversationMapper.selectList(
                new LambdaQueryWrapper<AIConversation>()
                        .eq(AIConversation::getSessionId, sessionId)
                        .orderByAsc(AIConversation::getCreateTime)
        );
    }

    /**
     * Save a new conversation.
     */
    @Transactional(timeout = 30)
    public void saveConversation(AIConversation conversation) {
        conversationMapper.insert(conversation);
        log.debug("Saved conversation: id={}, sessionId={}", conversation.getId(), conversation.getSessionId());
    }
}
