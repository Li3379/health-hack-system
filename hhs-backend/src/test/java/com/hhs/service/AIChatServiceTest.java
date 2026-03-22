package com.hhs.service;

import com.hhs.component.AIRateLimiter;
import com.hhs.component.ContentFilter;
import com.hhs.dto.ai.AIChatResponse;
import com.hhs.dto.ai.ChatContextVO;
import com.hhs.dto.ai.ChatSessionVO;
import com.hhs.dto.ai.ConversationVO;
import com.hhs.entity.AIConversation;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.AIChatService;
import com.hhs.service.domain.ConversationService;
import com.hhs.service.domain.MessageService;
import com.hhs.service.impl.AIChatServiceImpl;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AI Chat Service Unit Tests
 *
 * Tests the AIChatService which handles AI-powered health咨询 conversations
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI对话服务测试")
class AIChatServiceTest {

    @Mock
    private ChatLanguageModel chatModel;

    @Mock
    private AIRateLimiter rateLimiter;

    @Mock
    private ContentFilter contentFilter;

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageService messageService;

    @Mock
    private HealthProfileService healthProfileService;

    @Mock
    private RealtimeMetricMapper realtimeMetricMapper;

    @InjectMocks
    private AIChatServiceImpl aiChatService;

    private Response<AiMessage> mockResponse;
    private Long testUserId = 1L;
    private String testSessionId = "test-session-123";
    private String testQuestion = "How can I improve my health?";

    @BeforeEach
    void setUp() {
        // Mock HealthProfileService for personalized prompts
        lenient().when(healthProfileService.getByUserId(anyLong())).thenReturn(null);

        // Mock AI response
        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn("Based on your question, here are some health tips...");

        TokenUsage tokenUsage = mock(TokenUsage.class);
        when(tokenUsage.totalTokenCount()).thenReturn(100);

        mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(mockResponse.tokenUsage()).thenReturn(tokenUsage);
    }

    @Test
    @DisplayName("测试1.1：发送聊天消息 - 成功场景")
    void testChat_Success() {
        // Given: Rate limit check passes, content is safe
        when(rateLimiter.checkLimit(testUserId)).thenReturn(true);
        when(contentFilter.cleanInput(testQuestion)).thenReturn(testQuestion);
        when(contentFilter.containsSensitive(testQuestion)).thenReturn(false);
        when(messageService.getRecentHistory(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(chatModel.generate(any(java.util.List.class))).thenReturn(mockResponse);
        when(contentFilter.filterResponse(anyString())).thenReturn("Based on your question, here are some health tips...");
        when(messageService.createConversationEntity(any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(mock(AIConversation.class));
        doNothing().when(conversationService).saveConversation(any());
        when(rateLimiter.getRemainingCount(testUserId)).thenReturn(19);

        // When: Send chat message
        AIChatResponse result = aiChatService.chat(testUserId, testSessionId, testQuestion);

        // Then: Verify response
        assertNotNull(result);
        assertNotNull(result.answer());
        assertEquals(testSessionId, result.sessionId());
        assertEquals(19, result.remainingCount());
        verify(rateLimiter, times(1)).checkLimit(testUserId);
        verify(chatModel, times(1)).generate(any(java.util.List.class));
    }

    @Test
    @DisplayName("测试1.2：发送聊天消息 - 超出限流")
    void testChat_RateLimitExceeded() {
        // Given: Rate limit exceeded
        when(rateLimiter.checkLimit(testUserId)).thenReturn(false);

        // When & Then: Should throw exception
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiChatService.chat(testUserId, testSessionId, testQuestion);
        });

        assertTrue(exception.getMessage().contains("已用完") || exception.getMessage().contains("limit"));
        verify(chatModel, never()).generate(any(java.util.List.class));
    }

    @Test
    @DisplayName("测试1.3：发送聊天消息 - 包含敏感内容")
    void testChat_SensitiveContent() {
        // Given: Rate limit passes but content is sensitive
        when(rateLimiter.checkLimit(testUserId)).thenReturn(true);
        when(contentFilter.cleanInput(testQuestion)).thenReturn(testQuestion);
        when(contentFilter.containsSensitive(testQuestion)).thenReturn(true);

        // When & Then: Should throw exception
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiChatService.chat(testUserId, testSessionId, testQuestion);
        });

        assertTrue(exception.getMessage().contains("敏感内容") || exception.getMessage().contains("sensitive"));
        verify(chatModel, never()).generate(any(java.util.List.class));
    }

    @Test
    @DisplayName("测试1.4：发送聊天消息 - 访客用户限流")
    void testChat_VisitorRateLimit() {
        // Given: Visitor user (userId = null)
        when(rateLimiter.checkLimit(null)).thenReturn(false);

        // When & Then: Should throw exception with visitor message
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiChatService.chat(null, testSessionId, testQuestion);
        });

        assertTrue(exception.getMessage().contains("访客") || exception.getMessage().contains("visitor"));
        verify(chatModel, never()).generate(any(java.util.List.class));
    }

    @Test
    @DisplayName("测试1.5：发送聊天消息 - AI服务错误")
    void testChat_AIServiceError() {
        // Given: Rate limit passes, but AI service fails
        when(rateLimiter.checkLimit(testUserId)).thenReturn(true);
        when(contentFilter.cleanInput(testQuestion)).thenReturn(testQuestion);
        when(contentFilter.containsSensitive(testQuestion)).thenReturn(false);
        when(messageService.getRecentHistory(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(chatModel.generate(any(java.util.List.class))).thenThrow(new RuntimeException("AI service unavailable"));

        // When & Then: Should throw exception
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiChatService.chat(testUserId, testSessionId, testQuestion);
        });

        assertTrue(exception.getMessage().contains("不可用") || exception.getMessage().contains("unavailable"));
    }

    @Test
    @DisplayName("测试1.6：获取对话历史 - 成功场景")
    void testGetHistory_Success() {
        // Given: Mock conversation list
        AIConversation conv1 = new AIConversation();
        conv1.setId(1L);
        conv1.setQuestion("What is healthy eating?");
        conv1.setAnswer("Healthy eating includes...");
        conv1.setCreateTime(java.time.LocalDateTime.now());

        List<AIConversation> conversations = Arrays.asList(conv1);
        when(conversationService.getHistory(testSessionId, testUserId)).thenReturn(conversations);

        // When: Get history
        List<ConversationVO> result = aiChatService.getHistory(testSessionId, testUserId);

        // Then: Verify results
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("What is healthy eating?", result.get(0).question());
        verify(conversationService, times(1)).getHistory(testSessionId, testUserId);
    }

    @Test
    @DisplayName("测试1.7：获取对话历史 - 空列表")
    void testGetHistory_Empty() {
        // Given: Mock empty conversation list
        when(conversationService.getHistory(testSessionId, testUserId)).thenReturn(Collections.emptyList());

        // When: Get history
        List<ConversationVO> result = aiChatService.getHistory(testSessionId, testUserId);

        // Then: Verify empty result
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("测试1.8：清空会话 - 成功场景")
    void testClearSession_Success() {
        // Given: Mock deletion
        doNothing().when(conversationService).deleteSession(testSessionId, testUserId);

        // When: Clear session
        aiChatService.clearSession(testUserId, testSessionId);

        // Then: Verify deletion
        verify(conversationService, times(1)).deleteSession(testSessionId, testUserId);
    }

    @Test
    @DisplayName("测试1.9：列出用户会话 - 成功场景")
    void testListSessions_Success() {
        // Given: Mock conversation list
        AIConversation conv1 = new AIConversation();
        conv1.setId(1L);
        conv1.setSessionId("session-1");
        conv1.setQuestion("How to lose weight?");
        conv1.setCreateTime(java.time.LocalDateTime.now());

        List<AIConversation> conversations = Arrays.asList(conv1);
        when(conversationService.getRecentConversations(testUserId, 200)).thenReturn(conversations);

        // When: List sessions
        List<ChatSessionVO> result = aiChatService.listSessions(testUserId);

        // Then: Verify results
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("session-1", result.get(0).sessionId());
        verify(conversationService, times(1)).getRecentConversations(testUserId, 200);
    }

    @Test
    @DisplayName("测试1.10：列出用户会话 - 访客用户")
    void testListSessions_Visitor() {
        // Given: Visitor user
        when(conversationService.getRecentConversations(null, 200)).thenReturn(Collections.emptyList());

        // When: List sessions for visitor
        List<ChatSessionVO> result = aiChatService.listSessions(null);

        // Then: Verify empty result
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(conversationService, never()).getRecentConversations(any(), anyInt());
    }

    @Test
    @DisplayName("测试1.11：获取会话上下文 - 成功场景")
    void testGetContext_Success() {
        // Given: Mock context
        ChatContextVO mockContext = mock(ChatContextVO.class);
        when(messageService.buildChatContext(testSessionId)).thenReturn(mockContext);

        // When: Get context
        ChatContextVO result = aiChatService.getContext(testSessionId, testUserId);

        // Then: Verify result
        assertNotNull(result);
        verify(messageService, times(1)).buildChatContext(testSessionId);
    }

    @Test
    @DisplayName("测试1.12：发送聊天消息 - 带对话历史")
    void testChat_WithHistory() {
        // Given: Rate limit passes, has conversation history
        when(rateLimiter.checkLimit(testUserId)).thenReturn(true);
        when(contentFilter.cleanInput(testQuestion)).thenReturn(testQuestion);
        when(contentFilter.containsSensitive(testQuestion)).thenReturn(false);

        // Mock conversation history
        dev.langchain4j.data.message.ChatMessage historyMessage = mock(dev.langchain4j.data.message.ChatMessage.class);
        List<dev.langchain4j.data.message.ChatMessage> history = Arrays.asList(historyMessage);
        when(messageService.getRecentHistory(anyString(), anyInt())).thenReturn(history);

        when(chatModel.generate(any(java.util.List.class))).thenReturn(mockResponse);
        when(contentFilter.filterResponse(anyString())).thenReturn("Based on your question, here are some health tips...");
        when(messageService.createConversationEntity(any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(mock(AIConversation.class));
        doNothing().when(conversationService).saveConversation(any());
        when(rateLimiter.getRemainingCount(testUserId)).thenReturn(18);

        // When: Send chat message
        AIChatResponse result = aiChatService.chat(testUserId, testSessionId, testQuestion);

        // Then: Verify response includes context from history
        assertNotNull(result);
        assertEquals(18, result.remainingCount());
        verify(messageService, times(1)).getRecentHistory(anyString(), anyInt());
    }

    @Test
    @DisplayName("测试1.13：发送聊天消息 - 答案过滤")
    void testChat_ResponseFiltering() {
        // Given: Rate limit passes, AI response needs filtering
        when(rateLimiter.checkLimit(testUserId)).thenReturn(true);
        when(contentFilter.cleanInput(testQuestion)).thenReturn(testQuestion);
        when(contentFilter.containsSensitive(testQuestion)).thenReturn(false);
        when(messageService.getRecentHistory(anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(chatModel.generate(any(java.util.List.class))).thenReturn(mockResponse);
        when(contentFilter.filterResponse(anyString())).thenReturn("[Filtered] Health advice here");
        when(messageService.createConversationEntity(any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(mock(AIConversation.class));
        doNothing().when(conversationService).saveConversation(any());
        when(rateLimiter.getRemainingCount(testUserId)).thenReturn(17);

        // When: Send chat message
        AIChatResponse result = aiChatService.chat(testUserId, testSessionId, testQuestion);

        // Then: Verify filtered response
        assertNotNull(result);
        assertTrue(result.answer().contains("[Filtered]"));
        verify(contentFilter, times(1)).filterResponse(anyString());
    }

    @Test
    @DisplayName("测试1.14：列出用户会话 - 多个会话去重")
    void testListSessions_Deduplication() {
        // Given: Multiple conversations from same session
        AIConversation conv1 = new AIConversation();
        conv1.setId(1L);
        conv1.setSessionId("session-1");
        conv1.setQuestion("First question");
        conv1.setCreateTime(java.time.LocalDateTime.now());

        AIConversation conv2 = new AIConversation();
        conv2.setId(2L);
        conv2.setSessionId("session-1");
        conv2.setQuestion("Follow-up question");
        conv2.setCreateTime(java.time.LocalDateTime.now().plusMinutes(5));

        AIConversation conv3 = new AIConversation();
        conv3.setId(3L);
        conv3.setSessionId("session-2");
        conv3.setQuestion("Another session");
        conv3.setCreateTime(java.time.LocalDateTime.now());

        List<AIConversation> conversations = Arrays.asList(conv1, conv2, conv3);
        when(conversationService.getRecentConversations(testUserId, 200)).thenReturn(conversations);

        // When: List sessions
        List<ChatSessionVO> result = aiChatService.listSessions(testUserId);

        // Then: Verify deduplication (should have 2 unique sessions)
        assertNotNull(result);
        assertEquals(2, result.size()); // session-1 and session-2
    }
}
