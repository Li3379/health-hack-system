package com.hhs.service.impl;

import com.hhs.common.constant.ErrorCode;
import com.hhs.component.AIRateLimiter;
import com.hhs.component.ContentFilter;
import com.hhs.dto.ai.AIChatResponse;
import com.hhs.dto.ai.ChatContextVO;
import com.hhs.dto.ai.ChatSessionVO;
import com.hhs.dto.ai.ConversationVO;
import com.hhs.entity.AIConversation;
import com.hhs.entity.RealtimeMetric;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.AIChatService;
import com.hhs.service.HealthProfileService;
import com.hhs.service.domain.ConversationService;
import com.hhs.service.domain.MessageService;
import com.hhs.vo.HealthProfileVO;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI对话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIChatServiceImpl implements AIChatService {

    private final ChatLanguageModel chatModel;
    private final AIRateLimiter rateLimiter;
    private final ContentFilter contentFilter;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final HealthProfileService healthProfileService;
    private final RealtimeMetricMapper realtimeMetricMapper;

    private static final String BASE_SYSTEM_PROMPT = """
        你是"小健"，HHS平台的AI健康顾问助手。

        你的职责：
        1. 提供准确、专业、易懂的健康建议
        2. 基于科学依据，不传播伪科学
        3. 语气友好、专业、鼓励性
        4. 回答简洁，控制在200字以内

        重要原则：
        - 不提供疾病诊断（建议就医）
        - 不推荐具体药品
        - 强调个体差异
        - 涉及严重健康问题时，建议咨询专业医生

        你擅长的领域：
        - 饮食营养建议
        - 运动健身指导
        - 睡眠质量改善
        - 心理健康调节
        - 日常保健知识
        """;

    /**
     * Build personalized system prompt with user health context
     */
    private String buildPersonalizedPrompt(Long userId) {
        if (userId == null) {
            return BASE_SYSTEM_PROMPT;
        }

        StringBuilder sb = new StringBuilder(BASE_SYSTEM_PROMPT);
        sb.append("\n\n## 当前用户健康档案\n");

        // 1. 健康档案信息
        HealthProfileVO profile = healthProfileService.getByUserId(userId);
        if (profile != null) {
            sb.append("**基本信息**:\n");
            if (profile.gender() != null) {
                sb.append("- 性别: ").append("male".equals(profile.gender()) ? "男" : "女").append("\n");
            }
            if (profile.birthDate() != null) {
                int age = Period.between(profile.birthDate(), LocalDate.now()).getYears();
                sb.append("- 年龄: ").append(age).append("岁\n");
            }
            if (profile.heightCm() != null) {
                sb.append("- 身高: ").append(profile.heightCm()).append("cm\n");
            }
            if (profile.weightKg() != null) {
                sb.append("- 体重: ").append(profile.weightKg()).append("kg\n");
            }
            if (profile.bmi() != null) {
                sb.append("- BMI: ").append(formatDecimal(profile.bmi())).append("\n");
            }
            if (profile.bloodType() != null) {
                sb.append("- 血型: ").append(profile.bloodType()).append("\n");
            }
            if (profile.allergyHistory() != null && !profile.allergyHistory().isBlank()) {
                sb.append("- 过敏史: ").append(profile.allergyHistory()).append("\n");
            }
            if (profile.familyHistory() != null && !profile.familyHistory().isBlank()) {
                sb.append("- 家族病史: ").append(profile.familyHistory()).append("\n");
            }
            if (profile.lifestyleHabits() != null && !profile.lifestyleHabits().isBlank()) {
                sb.append("- 生活习惯: ").append(profile.lifestyleHabits()).append("\n");
            }
        }

        // 2. 最近健康指标
        List<RealtimeMetric> latestMetrics = realtimeMetricMapper.getLatestMetricsByUser(userId);
        if (latestMetrics != null && !latestMetrics.isEmpty()) {
            sb.append("\n**最近健康指标**:\n");
            Map<String, String> metricNames = Map.of(
                "heartRate", "心率",
                "systolicBP", "收缩压",
                "diastolicBP", "舒张压",
                "bloodGlucose", "血糖",
                "bloodOxygen", "血氧",
                "bodyTemperature", "体温",
                "weight", "体重"
            );
            for (RealtimeMetric metric : latestMetrics) {
                String displayName = metricNames.getOrDefault(metric.getMetricKey(), metric.getMetricKey());
                String unit = metric.getUnit() != null ? metric.getUnit() : "";
                sb.append("- ").append(displayName).append(": ")
                  .append(metric.getValue()).append(unit).append("\n");
            }
        }

        sb.append("\n**重要**: 请根据以上用户健康档案信息，提供个性化的健康建议。如果用户的问题与健康档案相关，请结合用户的具体数据给出建议。\n");

        return sb.toString();
    }

    private String formatDecimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toString();
    }

    /**
     * Send a chat message and get AI response
     *
     * @param userId User ID (null for visitor)
     * @param sessionId Chat session ID
     * @param question User's question
     * @return AIChatResponse containing answer, session ID, and remaining quota
     * @throws BusinessException if rate limit exceeded or content is inappropriate
     */
    @Override
    @Transactional(timeout = 30)
    public AIChatResponse chat(Long userId, String sessionId, String question) {
        log.info("AI对话请求: userId={}, sessionId={}, question={}",
            userId, sessionId, question);

        // 1. 限流检查
        if (!rateLimiter.checkLimit(userId)) {
            String message = userId != null
                ? "今日咨询次数已用完（20次），请明天再来"
                : "访客每天只能咨询3次，登录后可享更多次数";
            throw new BusinessException(ErrorCode.AI_RATE_LIMIT_EXCEEDED, message);
        }

        // 2. 内容安全检查
        String cleanQuestion = contentFilter.cleanInput(question);
        if (contentFilter.containsSensitive(cleanQuestion)) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER,
                "问题包含敏感内容，请重新表述");
        }

        // 3. 获取对话历史（最近5轮，支持更深入的对话）
        List<ChatMessage> history = messageService.getRecentHistory(sessionId, 5);

        // 4. 构建完整消息列表（包含用户健康上下文）
        String personalizedPrompt = buildPersonalizedPrompt(userId);
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(personalizedPrompt));
        messages.addAll(history);
        messages.add(UserMessage.from(cleanQuestion));

        // 5. 调用AI
        String answer;
        int tokensUsed = 0;
        try {
            log.info("开始调用AI模型: messages size={}", messages.size());
            Response<AiMessage> response = chatModel.generate(messages);
            answer = response.content().text();
            tokensUsed = response.tokenUsage() != null
                ? response.tokenUsage().totalTokenCount()
                : 0;
            log.info("AI对话响应成功: tokens={}", tokensUsed);
        } catch (Exception e) {
            log.error("AI对话调用失败，详细错误信息: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.AI_ERROR, "AI服务暂时不可用: " + e.getMessage());
        }

        // 6. 答案安全检查和过滤
        answer = contentFilter.filterResponse(answer);

        // 7. 保存对话记录
        AIConversation conversation = messageService.createConversationEntity(
            userId, sessionId, cleanQuestion, answer, tokensUsed
        );
        conversationService.saveConversation(conversation);

        // 8. 返回结果
        int remaining = rateLimiter.getRemainingCount(userId);
        log.info("AI对话完成: remaining={}", remaining);

        return new AIChatResponse(answer, sessionId, remaining);
    }

    /**
     * Get conversation history for a session
     *
     * @param sessionId Chat session ID
     * @param userId User ID (for authorization)
     * @return List of conversation entries
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public List<ConversationVO> getHistory(String sessionId, Long userId) {
        log.info("查询对话历史: sessionId={}, userId={}", sessionId, userId);
        List<AIConversation> conversations = conversationService.getHistory(sessionId, userId);
        return conversations.stream()
            .map(this::toVO)
            .collect(Collectors.toList());
    }

    /**
     * Clear all messages in a session
     *
     * @param userId User ID (for authorization)
     * @param sessionId Chat session ID to clear
     */
    @Override
    @Transactional(timeout = 30)
    public void clearSession(Long userId, String sessionId) {
        log.info("清空会话: userId={}, sessionId={}", userId, sessionId);
        conversationService.deleteSession(sessionId, userId);
    }

    /**
     * Get list of user's chat sessions sorted by last message time (descending)
     *
     * @param userId User ID
     * @return List of chat sessions
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public List<ChatSessionVO> listSessions(Long userId) {
        if (userId == null) return List.of();

        List<AIConversation> all = conversationService.getRecentConversations(userId, 200);
        Set<String> seen = new LinkedHashSet<>();
        List<ChatSessionVO> result = new ArrayList<>();

        for (AIConversation c : all) {
            if (seen.add(c.getSessionId())) {
                String summary = c.getQuestion();
                if (summary != null && summary.length() > 40) {
                    summary = summary.substring(0, 40) + "...";
                }
                result.add(new ChatSessionVO(c.getSessionId(), c.getCreateTime(), summary));
            }
        }

        return result;
    }

    /**
     * Get recent N rounds of context for a session
     *
     * @param sessionId Chat session ID
     * @param userId User ID (for authorization)
     * @return Chat context containing recent messages
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public ChatContextVO getContext(String sessionId, Long userId) {
        return messageService.buildChatContext(sessionId);
    }

    /**
     * Entity转VO
     */
    private ConversationVO toVO(AIConversation conversation) {
        return new ConversationVO(
            conversation.getId(),
            conversation.getQuestion(),
            conversation.getAnswer(),
            conversation.getCreateTime()
        );
    }
}
