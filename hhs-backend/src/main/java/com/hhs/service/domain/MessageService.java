package com.hhs.service.domain;

import com.hhs.dto.ai.ChatContextVO;
import com.hhs.entity.AIConversation;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI message processing logic component.
 * Handles message history retrieval and context building.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageService {

    private final ConversationService conversationService;

    /**
     * Get recent N rounds of chat history for AI context.
     *
     * @param sessionId the session ID
     * @param rounds number of conversation rounds to retrieve
     * @return list of ChatMessage in chronological order (oldest first)
     */
    public List<ChatMessage> getRecentHistory(String sessionId, int rounds) {
        List<AIConversation> records = conversationService.getBySessionId(sessionId);

        // Each round includes Q&A, so we need rounds * 2 records
        int limit = Math.min(records.size(), rounds * 2);
        int startIndex = Math.max(0, records.size() - limit);

        // Extract the most recent records and reverse to chronological order
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = records.size() - 1; i >= startIndex; i--) {
            AIConversation record = records.get(i);
            messages.add(UserMessage.from(record.getQuestion()));
            if (record.getAnswer() != null) {
                messages.add(AiMessage.from(record.getAnswer()));
            }
        }

        log.debug("Built message history: sessionId={}, messages={}", sessionId, messages.size());
        return messages;
    }

    /**
     * Build chat context from conversation records.
     *
     * @param sessionId the session ID
     * @return ChatContextVO with conversation turns in reverse chronological order
     */
    public ChatContextVO buildChatContext(String sessionId) {
        List<AIConversation> records = conversationService.getBySessionId(sessionId);
        List<ChatContextVO.ContextTurn> turns = new ArrayList<>();

        // Build turns in reverse order (newest first)
        for (int i = records.size() - 1; i >= 0; i--) {
            AIConversation record = records.get(i);
            turns.add(new ChatContextVO.ContextTurn(
                record.getQuestion(),
                record.getAnswer(),
                record.getCreateTime()
            ));
        }

        log.debug("Built chat context: sessionId={}, turns={}", sessionId, turns.size());
        return new ChatContextVO(sessionId, turns);
    }

    /**
     * Create a new AI conversation entity.
     *
     * @param userId the user ID
     * @param sessionId the session ID
     * @param question the user's question
     * @param answer the AI's answer
     * @param tokensUsed tokens consumed by AI
     * @return AIConversation entity ready to save
     */
    public AIConversation createConversationEntity(Long userId, String sessionId,
                                                   String question, String answer, int tokensUsed) {
        AIConversation conversation = new AIConversation();
        conversation.setUserId(userId);
        conversation.setSessionId(sessionId);
        conversation.setQuestion(question);
        conversation.setAnswer(answer);
        conversation.setTokensUsed(tokensUsed);
        conversation.setCreateTime(java.time.LocalDateTime.now());

        log.debug("Created conversation entity: userId={}, sessionId={}", userId, sessionId);
        return conversation;
    }
}
