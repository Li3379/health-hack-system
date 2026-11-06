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
     * Get conversations by session ID, scoped to the caller.
     *
     * <p>The {@code user_id} predicate is always applied so a session can never be read across
     * users. A visitor ({@code userId == null}) is scoped to rows whose {@code user_id} is null,
     * never to another user's rows.
     *
     * @param sessionId the session ID
     * @param userId    the caller's user ID ({@code null} for visitors)
     * @return conversations owned by the caller, oldest first
     */
    @Transactional(timeout = 30, readOnly = true)
    public List<AIConversation> getBySessionId(String sessionId, Long userId) {
        log.debug("Fetching conversations by session: sessionId={}, userId={}", sessionId, userId);
        LambdaQueryWrapper<AIConversation> query = new LambdaQueryWrapper<>();
        query.eq(AIConversation::getSessionId, sessionId);
        if (userId != null) {
            query.eq(AIConversation::getUserId, userId);
        } else {
            query.isNull(AIConversation::getUserId);
        }
        query.orderByAsc(AIConversation::getCreateTime);
        return conversationMapper.selectList(query);
    }

    /**
     * Check whether any conversation row exists for the session, regardless of owner.
     *
     * <p>Used to distinguish "session does not exist yet" from "session belongs to another
     * user" after an ownership-scoped read came back empty.
     *
     * @param sessionId the session ID
     * @return true when at least one row exists for the session
     */
    @Transactional(timeout = 30, readOnly = true)
    public boolean sessionExists(String sessionId) {
        return conversationMapper.selectCount(
                new LambdaQueryWrapper<AIConversation>()
                        .eq(AIConversation::getSessionId, sessionId)
        ) > 0;
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
