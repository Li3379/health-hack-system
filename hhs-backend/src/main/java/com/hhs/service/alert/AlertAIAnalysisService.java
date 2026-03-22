package com.hhs.service.alert;

import com.hhs.dto.AlertVO;
import com.hhs.entity.HealthProfile;
import com.hhs.entity.RealtimeMetric;
import com.hhs.mapper.HealthProfileMapper;
import com.hhs.mapper.RealtimeMetricMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI-powered alert analysis service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertAIAnalysisService {

    private final HealthProfileMapper healthProfileMapper;
    private final RealtimeMetricMapper realtimeMetricMapper;

    @Autowired(required = false)
    private ChatLanguageModel chatLanguageModel;

    @Value("${hhs.alert.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${hhs.alert.ai.timeout-seconds:10}")
    private int aiTimeoutSeconds;

    private static final String SYSTEM_PROMPT = """
            你是专业的健康顾问"小健"，负责为用户的健康预警提供个性化的分析和建议。

            你的职责：
            1. 分析用户健康指标异常的可能原因
            2. 提供针对性的健康建议
            3. 在必要时提醒用户就医

            回答要求：
            - 语气亲切、专业
            - 建议具体、可操作
            - 避免引起恐慌
            - 控制在100字以内
            - 不要使用markdown格式，直接输出纯文本
            """;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Enhance alert with AI analysis
     *
     * @param userId the user ID
     * @param alert  the alert to enhance
     * @return the enhanced alert with AI analysis
     */
    public AlertVO enhanceWithAIAnalysis(Long userId, AlertVO alert) {
        if (!aiEnabled || chatLanguageModel == null) {
            log.debug("AI analysis is disabled or model not available");
            return alert;
        }

        try {
            String analysis = generateAIAnalysis(userId, alert);
            if (analysis != null && !analysis.isEmpty()) {
                alert.setAiAnalysis(analysis);
                log.info("AI analysis generated for alert: userId={}, metricKey={}", userId, alert.getMetricKey());
            }
        } catch (Exception e) {
            log.warn("AI analysis failed for user {}: {}", userId, e.getMessage());
            // Don't fail the alert if AI analysis fails
        }

        return alert;
    }

    /**
     * Generate AI analysis for the alert
     */
    private String generateAIAnalysis(Long userId, AlertVO alert) {
        // Build context
        String userProfile = buildUserProfileContext(userId);
        String recentMetrics = buildRecentMetricsContext(userId, alert.getMetricKey());
        String alertContext = buildAlertContext(alert);

        // Build user prompt
        String userPrompt = String.format("""
                用户健康预警信息：

                %s

                用户健康档案：
                %s

                近期健康指标：
                %s

                请为这条健康预警提供个性化的分析和建议。
                """,
                alertContext,
                userProfile,
                recentMetrics
        );

        try {
            // Call AI model
            var response = chatLanguageModel.generate(
                    List.of(
                            new SystemMessage(SYSTEM_PROMPT),
                            new UserMessage(userPrompt)
                    )
            );
            return response != null ? response.content().text() : null;
        } catch (Exception e) {
            log.error("Failed to call AI model: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build user profile context for AI
     */
    private String buildUserProfileContext(Long userId) {
        Optional<HealthProfile> profileOpt = Optional.ofNullable(healthProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthProfile>()
                        .eq(HealthProfile::getUserId, userId)
        ));

        if (profileOpt.isEmpty()) {
            return "用户档案：暂无信息";
        }

        HealthProfile profile = profileOpt.get();
        StringBuilder sb = new StringBuilder();

        if (profile.getGender() != null) {
            sb.append("性别：").append(profile.getGender()).append("；");
        }
        if (profile.getBirthDate() != null) {
            int age = java.time.Period.between(profile.getBirthDate(), java.time.LocalDate.now()).getYears();
            sb.append("年龄：").append(age).append("岁；");
        }
        if (profile.getHeightCm() != null && profile.getWeightKg() != null) {
            sb.append("身高：").append(profile.getHeightCm()).append("cm；");
            sb.append("体重：").append(profile.getWeightKg()).append("kg；");
        }
        if (profile.getBmi() != null) {
            sb.append("BMI：").append(profile.getBmi()).append("；");
        }
        if (profile.getAllergyHistory() != null && !profile.getAllergyHistory().isEmpty()) {
            sb.append("过敏史：").append(profile.getAllergyHistory()).append("；");
        }
        if (profile.getFamilyHistory() != null && !profile.getFamilyHistory().isEmpty()) {
            sb.append("家族病史：").append(profile.getFamilyHistory()).append("；");
        }

        return sb.length() > 0 ? sb.toString() : "用户档案：暂无详细信息";
    }

    /**
     * Build recent metrics context for AI
     */
    private String buildRecentMetricsContext(Long userId, String metricKey) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        List<RealtimeMetric> recentMetrics = realtimeMetricMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RealtimeMetric>()
                        .eq(RealtimeMetric::getUserId, userId)
                        .eq(RealtimeMetric::getMetricKey, metricKey)
                        .ge(RealtimeMetric::getCreatedAt, since)
                        .orderByDesc(RealtimeMetric::getCreatedAt)
                        .last("LIMIT 5")
        );

        if (recentMetrics == null || recentMetrics.isEmpty()) {
            return "近期" + getMetricLabel(metricKey) + "数据：暂无记录";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("近期").append(getMetricLabel(metricKey)).append("记录：\n");

        for (RealtimeMetric metric : recentMetrics) {
            sb.append(String.format("- %s: %s %s\n",
                    metric.getCreatedAt().format(DATE_FORMATTER),
                    metric.getValue(),
                    metric.getUnit() != null ? metric.getUnit() : ""
            ));
        }

        // Add trend analysis
        if (recentMetrics.size() >= 2) {
            BigDecimal latest = recentMetrics.get(0).getValue();
            BigDecimal previous = recentMetrics.get(recentMetrics.size() - 1).getValue();
            String trend = latest.compareTo(previous) > 0 ? "上升" :
                          latest.compareTo(previous) < 0 ? "下降" : "稳定";
            sb.append("趋势：").append(trend);
        }

        return sb.toString();
    }

    /**
     * Build alert context for AI
     */
    private String buildAlertContext(AlertVO alert) {
        StringBuilder sb = new StringBuilder();
        sb.append("预警类型：").append(alert.getAlertType()).append("\n");
        sb.append("预警级别：").append(getAlertLevelLabel(alert.getAlertLevel())).append("\n");
        sb.append("标题：").append(alert.getTitle()).append("\n");
        sb.append("详细信息：").append(alert.getMessage()).append("\n");

        if (alert.getCurrentValue() != null) {
            sb.append("当前值：").append(alert.getCurrentValue()).append("\n");
        }
        if (alert.getThresholdValue() != null) {
            sb.append("阈值：").append(alert.getThresholdValue()).append("\n");
        }

        return sb.toString();
    }

    /**
     * Get metric label in Chinese
     */
    private String getMetricLabel(String metricKey) {
        return switch (metricKey) {
            case "heartRate" -> "心率";
            case "systolicBP" -> "收缩压";
            case "diastolicBP" -> "舒张压";
            case "glucose" -> "血糖";
            case "temperature" -> "体温";
            case "bmi" -> "BMI";
            case "weight" -> "体重";
            default -> metricKey;
        };
    }

    /**
     * Get alert level label in Chinese
     */
    private String getAlertLevelLabel(String alertLevel) {
        return switch (alertLevel) {
            case "HIGH" -> "严重";
            case "MEDIUM" -> "警告";
            case "LOW" -> "提示";
            default -> alertLevel;
        };
    }

    /**
     * Check if AI analysis is available
     */
    public boolean isAIAvailable() {
        return aiEnabled && chatLanguageModel != null;
    }
}