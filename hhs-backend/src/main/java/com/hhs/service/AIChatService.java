package com.hhs.service;

import com.hhs.dto.ai.AIChatResponse;
import com.hhs.dto.ai.ChatContextVO;
import com.hhs.dto.ai.ChatSessionVO;
import com.hhs.dto.ai.ConversationVO;

import java.util.List;

/**
 * AI Chat Service
 * Handles AI-powered health咨询 conversations with context management
 */
public interface AIChatService {

    /**
     * Send a chat message and get AI response
     *
     * @param userId User ID (null for visitor)
     * @param sessionId Chat session ID
     * @param question User's question
     * @return AIChatResponse containing answer, session ID, and remaining quota
     * @throws BusinessException if rate limit exceeded or content is inappropriate
     */
    AIChatResponse chat(Long userId, String sessionId, String question);

    /**
     * Get conversation history for a session
     *
     * @param sessionId Chat session ID
     * @param userId User ID (for authorization)
     * @return List of conversation entries
     */
    List<ConversationVO> getHistory(String sessionId, Long userId);

    /**
     * Clear all messages in a session
     *
     * @param userId User ID (for authorization)
     * @param sessionId Chat session ID to clear
     */
    void clearSession(Long userId, String sessionId);

    /**
     * Get list of user's chat sessions sorted by last message time (descending)
     *
     * @param userId User ID
     * @return List of chat sessions
     */
    List<ChatSessionVO> listSessions(Long userId);

    /**
     * Get recent N rounds of context for a session
     *
     * @param sessionId Chat session ID
     * @param userId User ID (for authorization)
     * @return Chat context containing recent messages
     */
    ChatContextVO getContext(String sessionId, Long userId);
}
