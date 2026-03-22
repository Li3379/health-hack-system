package com.hhs.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.common.constant.ErrorCode;
import com.hhs.entity.HealthReport;
import com.hhs.entity.RealtimeMetric;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.HealthReportMapper;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.HealthProfileService;
import com.hhs.service.HealthReportService;
import com.hhs.service.HealthScoreService;
import com.hhs.vo.HealthProfileVO;
import com.hhs.vo.HealthReportVO;
import com.hhs.vo.HealthScoreVO;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Health Report Service Implementation
 * Generates AI-powered comprehensive health reports with Redis + MySQL caching
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthReportServiceImpl implements HealthReportService {

    private final ChatLanguageModel chatModel;
    private final HealthProfileService healthProfileService;
    private final HealthScoreService healthScoreService;
    private final RealtimeMetricMapper realtimeMetricMapper;
    private final HealthReportMapper healthReportMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "health:report:";
    private static final int CACHE_TTL_HOURS = 24;
    private static final int DEFAULT_HISTORY_LIMIT = 10;

    private static final String REPORT_PROMPT_TEMPLATE = """
        你是一位专业的健康管理师，请根据以下用户健康数据生成一份详细的健康报告。

        ## 用户健康档案
        %s

        ## 最近健康指标
        %s

        ## 健康评分
        - 综合评分: %d 分
        - 评级: %s

        请生成一份包含以下内容的健康报告，严格按照JSON格式返回：

        {
          "dimensions": [
            {"name": "心血管健康", "score": 85, "status": "良好", "description": "描述"},
            {"name": "代谢健康", "score": 78, "status": "一般", "description": "描述"},
            {"name": "体重管理", "score": 90, "status": "优秀", "description": "描述"},
            {"name": "生活方式", "score": 72, "status": "需改善", "description": "描述"}
          ],
          "riskAlerts": [
            {"level": "中", "category": "心血管风险", "description": "描述", "recommendation": "建议"}
          ],
          "suggestions": [
            {"category": "饮食", "priority": "高", "suggestion": "建议内容", "action": "具体行动"}
          ],
          "summary": "总体健康评估总结"
        }

        要求：
        1. dimensions 必须包含4个维度：心血管健康、代谢健康、体重管理、生活方式
        2. riskAlerts 根据实际数据判断，如果没有风险可以返回空数组
        3. suggestions 至少提供3条建议
        4. 只返回JSON，不要其他文字
        """;

    @Override
    public HealthReportVO getOrGenerateReport(Long userId) {
        log.info("Getting health report for user: {}", userId);

        // 1. Try Redis cache first
        HealthReportVO cached = getCachedReport(userId);
        if (cached != null) {
            log.debug("Returning cached report for user: {}", userId);
            return cached;
        }

        // 2. Try MySQL for existing report
        HealthReport existingReport = healthReportMapper.getLatestByUserId(userId);
        if (existingReport != null) {
            log.debug("Found existing report in MySQL for user: {}", userId);
            HealthReportVO vo = convertToVO(existingReport);
            cacheReport(userId, vo);
            return vo;
        }

        // 3. Generate new report via AI
        log.info("No existing report found, generating new report for user: {}", userId);
        return generateAndSaveReport(userId);
    }

    @Override
    public HealthReportVO regenerateReport(Long userId) {
        log.info("Force regenerating health report for user: {}", userId);

        // Clear cache
        clearCache(userId);

        // Generate new report
        return generateAndSaveReport(userId);
    }

    @Override
    public List<HealthReportVO> getReportHistory(Long userId, int limit) {
        List<HealthReport> reports = healthReportMapper.listByUserIdWithLimit(userId, limit);
        return reports.stream()
            .map(this::convertToVO)
            .toList();
    }

    @Override
    public HealthReportVO getReportById(Long userId, String reportId) {
        HealthReport report = healthReportMapper.getByReportId(reportId);
        if (report == null) {
            return null;
        }
        // Authorization check
        if (!report.getUserId().equals(userId)) {
            log.warn("User {} attempted to access report {} belonging to user {}",
                userId, reportId, report.getUserId());
            return null;
        }
        return convertToVO(report);
    }

    @Override
    @Deprecated
    @Transactional(timeout = 120)
    public HealthReportVO generateReport(Long userId) {
        return getOrGenerateReport(userId);
    }

    /**
     * Generate new report via AI and save to MySQL + Redis
     */
    @Transactional(timeout = 120)
    protected HealthReportVO generateAndSaveReport(Long userId) {
        log.info("Generating new health report for user: {}", userId);

        // 1. Get health profile
        HealthProfileVO profile = healthProfileService.getByUserId(userId);
        if (profile == null) {
            throw new BusinessException(ErrorCode.HEALTH_PROFILE_NOT_FOUND, "请先完善健康档案");
        }

        // 2. Get latest health metrics
        List<RealtimeMetric> metrics = realtimeMetricMapper.getLatestMetricsByUser(userId);

        // 3. Get health score
        HealthScoreVO scoreVO = healthScoreService.calculateScore(userId);

        // 4. Build AI prompt
        String profileInfo = buildProfileInfo(profile);
        String metricsInfo = buildMetricsInfo(metrics);

        String prompt = String.format(REPORT_PROMPT_TEMPLATE,
            profileInfo,
            metricsInfo,
            scoreVO.getScore(),
            scoreVO.getLevel()
        );

        // 5. Call AI to generate report
        String aiResponse;
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from("你是一位专业的健康管理师，擅长生成健康报告。"));
            messages.add(UserMessage.from(prompt));

            Response<dev.langchain4j.data.message.AiMessage> response = chatModel.generate(messages);
            aiResponse = response.content().text();
            log.debug("AI response: {}", aiResponse);
        } catch (Exception e) {
            log.error("Failed to generate report: {}", e.getMessage());
            // Fallback: generate basic report
            HealthReportVO basicReport = generateBasicReport(profile, metrics, scoreVO);
            saveReport(userId, basicReport);
            return basicReport;
        }

        // 6. Parse AI response
        HealthReportVO report;
        try {
            report = parseAIResponse(aiResponse, profile, scoreVO);
        } catch (Exception e) {
            log.warn("Failed to parse AI response, using basic report: {}", e.getMessage());
            report = generateBasicReport(profile, metrics, scoreVO);
        }

        // 7. Save to MySQL and cache to Redis
        saveReport(userId, report);

        return report;
    }

    // ==================== Redis Cache Methods ====================

    private HealthReportVO getCachedReport(Long userId) {
        try {
            String key = CACHE_KEY_PREFIX + userId + ":latest";
            Object cached = redisTemplate.opsForValue().get(key);
            return cached != null ? (HealthReportVO) cached : null;
        } catch (RedisSystemException e) {
            log.warn("Redis connection failed, returning null for cached report: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Redis operation failed, returning null for cached report: {}", e.getMessage());
            return null;
        }
    }

    private void cacheReport(Long userId, HealthReportVO report) {
        try {
            String key = CACHE_KEY_PREFIX + userId + ":latest";
            redisTemplate.opsForValue().set(key, report, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (RedisSystemException e) {
            log.warn("Redis connection failed, skipping cache: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Redis operation failed, skipping cache: {}", e.getMessage());
        }
    }

    private void clearCache(Long userId) {
        try {
            String key = CACHE_KEY_PREFIX + userId + ":latest";
            redisTemplate.delete(key);
        } catch (RedisSystemException e) {
            log.warn("Redis connection failed, skipping cache clear: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Redis operation failed, skipping cache clear: {}", e.getMessage());
        }
    }

    // ==================== MySQL Persistence Methods ====================

    private void saveReport(Long userId, HealthReportVO report) {
        try {
            HealthReport entity = new HealthReport();
            entity.setUserId(userId);
            entity.setReportId(report.reportId());
            entity.setOverallScore(report.overallScore());
            entity.setScoreLevel(report.scoreLevel());
            entity.setDimensions(objectMapper.writeValueAsString(report.dimensions()));
            entity.setRiskAlerts(report.riskAlerts() != null ? objectMapper.writeValueAsString(report.riskAlerts()) : "[]");
            entity.setSuggestions(objectMapper.writeValueAsString(report.suggestions()));
            entity.setSummary(report.summary());
            entity.setUserInfo(objectMapper.writeValueAsString(report.userInfo()));
            entity.setGeneratedAt(report.generatedAt());
            entity.setCreatedAt(LocalDateTime.now());

            healthReportMapper.insert(entity);
            log.info("Saved health report {} to MySQL for user: {}", report.reportId(), userId);

            // Cache to Redis
            cacheReport(userId, report);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize report data: {}", e.getMessage());
        }
    }

    private HealthReportVO convertToVO(HealthReport entity) {
        try {
            List<HealthReportVO.DimensionAnalysis> dimensions = parseJsonToList(
                entity.getDimensions(),
                node -> new HealthReportVO.DimensionAnalysis(
                    node.get("name").asText(),
                    node.get("score").asInt(),
                    node.get("status").asText(),
                    node.get("description").asText()
                )
            );

            List<HealthReportVO.RiskAlert> riskAlerts = parseJsonToList(
                entity.getRiskAlerts(),
                node -> new HealthReportVO.RiskAlert(
                    node.get("level").asText(),
                    node.get("category").asText(),
                    node.get("description").asText(),
                    node.get("recommendation").asText()
                )
            );

            List<HealthReportVO.ImprovementSuggestion> suggestions = parseJsonToList(
                entity.getSuggestions(),
                node -> new HealthReportVO.ImprovementSuggestion(
                    node.get("category").asText(),
                    node.get("priority").asText(),
                    node.get("suggestion").asText(),
                    node.get("action").asText()
                )
            );

            JsonNode userInfoNode = objectMapper.readTree(entity.getUserInfo());
            HealthReportVO.UserInfo userInfo = new HealthReportVO.UserInfo(
                userInfoNode.get("gender").asText(),
                userInfoNode.get("age").asInt(),
                userInfoNode.get("heightCm").asDouble(),
                userInfoNode.get("weightKg").asDouble(),
                userInfoNode.get("bmi").asDouble()
            );

            return new HealthReportVO(
                entity.getReportId(),
                entity.getGeneratedAt(),
                userInfo,
                entity.getOverallScore(),
                entity.getScoreLevel(),
                dimensions,
                riskAlerts,
                suggestions,
                entity.getSummary()
            );
        } catch (Exception e) {
            log.error("Failed to convert entity to VO: {}", e.getMessage());
            throw new RuntimeException("Failed to convert report data", e);
        }
    }

    private <T> List<T> parseJsonToList(String json, JsonParser<T> parser) throws JsonProcessingException {
        List<T> result = new ArrayList<>();
        JsonNode array = objectMapper.readTree(json);
        if (array.isArray()) {
            for (JsonNode node : array) {
                result.add(parser.parse(node));
            }
        }
        return result;
    }

    @FunctionalInterface
    private interface JsonParser<T> {
        T parse(JsonNode node);
    }

    // ==================== AI Response Parsing Methods ====================

    private String buildProfileInfo(HealthProfileVO profile) {
        StringBuilder sb = new StringBuilder();
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
        return sb.toString();
    }

    private String buildMetricsInfo(List<RealtimeMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return "暂无健康指标数据";
        }

        java.util.Map<String, String> metricNames = java.util.Map.of(
            "heartRate", "心率",
            "systolicBP", "收缩压",
            "diastolicBP", "舒张压",
            "bloodGlucose", "血糖",
            "bloodOxygen", "血氧",
            "bodyTemperature", "体温"
        );

        StringBuilder sb = new StringBuilder();
        for (RealtimeMetric metric : metrics) {
            String name = metricNames.getOrDefault(metric.getMetricKey(), metric.getMetricKey());
            String unit = metric.getUnit() != null ? metric.getUnit() : "";
            sb.append("- ").append(name).append(": ").append(metric.getValue()).append(unit).append("\n");
        }
        return sb.toString();
    }

    private HealthReportVO parseAIResponse(String aiResponse, HealthProfileVO profile, HealthScoreVO scoreVO) {
        // Extract JSON part
        String json = extractJSON(aiResponse);

        // Parse using Jackson
        List<HealthReportVO.DimensionAnalysis> dimensions = parseDimensions(json);
        List<HealthReportVO.RiskAlert> riskAlerts = parseRiskAlerts(json);
        List<HealthReportVO.ImprovementSuggestion> suggestions = parseSuggestions(json);
        String summary = parseSummary(json);

        // Build user info
        int age = profile.birthDate() != null
            ? Period.between(profile.birthDate(), LocalDate.now()).getYears()
            : 0;

        HealthReportVO.UserInfo userInfo = new HealthReportVO.UserInfo(
            profile.gender() != null ? ("male".equals(profile.gender()) ? "男" : "女") : "未知",
            age,
            profile.heightCm() != null ? profile.heightCm().doubleValue() : 0,
            profile.weightKg() != null ? profile.weightKg().doubleValue() : 0,
            profile.bmi() != null ? profile.bmi().doubleValue() : 0
        );

        return new HealthReportVO(
            "RPT-" + System.currentTimeMillis(),
            LocalDateTime.now(),
            userInfo,
            scoreVO.getScore(),
            scoreVO.getLevel(),
            dimensions,
            riskAlerts,
            suggestions,
            summary
        );
    }

    private String extractJSON(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private List<HealthReportVO.DimensionAnalysis> parseDimensions(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode dimensionsNode = root.get("dimensions");

            if (dimensionsNode != null && dimensionsNode.isArray()) {
                List<HealthReportVO.DimensionAnalysis> result = new ArrayList<>();
                for (JsonNode node : dimensionsNode) {
                    result.add(new HealthReportVO.DimensionAnalysis(
                        node.has("name") ? node.get("name").asText() : "未知维度",
                        node.has("score") ? node.get("score").asInt() : 0,
                        node.has("status") ? node.get("status").asText() : "未知",
                        node.has("description") ? node.get("description").asText() : ""
                    ));
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse dimensions from AI response: {}", e.getMessage());
        }
        return getDefaultDimensions();
    }

    private List<HealthReportVO.DimensionAnalysis> getDefaultDimensions() {
        List<HealthReportVO.DimensionAnalysis> result = new ArrayList<>();
        result.add(new HealthReportVO.DimensionAnalysis("心血管健康", 80, "良好", "心血管功能正常"));
        result.add(new HealthReportVO.DimensionAnalysis("代谢健康", 75, "一般", "需关注血糖血脂"));
        result.add(new HealthReportVO.DimensionAnalysis("体重管理", 85, "良好", "BMI在正常范围"));
        result.add(new HealthReportVO.DimensionAnalysis("生活方式", 70, "需改善", "建议增加运动"));
        return result;
    }

    private List<HealthReportVO.RiskAlert> parseRiskAlerts(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode alertsNode = root.get("riskAlerts");

            if (alertsNode != null && alertsNode.isArray()) {
                List<HealthReportVO.RiskAlert> result = new ArrayList<>();
                for (JsonNode node : alertsNode) {
                    result.add(new HealthReportVO.RiskAlert(
                        node.has("level") ? node.get("level").asText() : "中",
                        node.has("category") ? node.get("category").asText() : "未知风险",
                        node.has("description") ? node.get("description").asText() : "",
                        node.has("recommendation") ? node.get("recommendation").asText() : ""
                    ));
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to parse riskAlerts from AI response: {}", e.getMessage());
        }
        return new ArrayList<>();
    }

    private List<HealthReportVO.ImprovementSuggestion> parseSuggestions(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode suggestionsNode = root.get("suggestions");

            if (suggestionsNode != null && suggestionsNode.isArray()) {
                List<HealthReportVO.ImprovementSuggestion> result = new ArrayList<>();
                for (JsonNode node : suggestionsNode) {
                    result.add(new HealthReportVO.ImprovementSuggestion(
                        node.has("category") ? node.get("category").asText() : "生活方式",
                        node.has("priority") ? node.get("priority").asText() : "中",
                        node.has("suggestion") ? node.get("suggestion").asText() : "",
                        node.has("action") ? node.get("action").asText() : ""
                    ));
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse suggestions from AI response: {}", e.getMessage());
        }
        return getDefaultSuggestions();
    }

    private List<HealthReportVO.ImprovementSuggestion> getDefaultSuggestions() {
        List<HealthReportVO.ImprovementSuggestion> result = new ArrayList<>();
        result.add(new HealthReportVO.ImprovementSuggestion("饮食", "高", "控制碳水化合物摄入", "每日主食不超过250g"));
        result.add(new HealthReportVO.ImprovementSuggestion("运动", "高", "增加有氧运动", "每周至少150分钟中等强度运动"));
        result.add(new HealthReportVO.ImprovementSuggestion("睡眠", "中", "保证充足睡眠", "每晚7-8小时睡眠"));
        return result;
    }

    private String parseSummary(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode summaryNode = root.get("summary");
            if (summaryNode != null && !summaryNode.asText().isBlank()) {
                return summaryNode.asText();
            }
        } catch (Exception e) {
            log.warn("Failed to parse summary from AI response: {}", e.getMessage());
        }
        return "您的整体健康状况良好，建议继续保持健康的生活方式，定期监测健康指标。";
    }

    private HealthReportVO generateBasicReport(HealthProfileVO profile, List<RealtimeMetric> metrics, HealthScoreVO scoreVO) {
        int age = profile.birthDate() != null
            ? Period.between(profile.birthDate(), LocalDate.now()).getYears()
            : 0;

        HealthReportVO.UserInfo userInfo = new HealthReportVO.UserInfo(
            profile.gender() != null ? ("male".equals(profile.gender()) ? "男" : "女") : "未知",
            age,
            profile.heightCm() != null ? profile.heightCm().doubleValue() : 0,
            profile.weightKg() != null ? profile.weightKg().doubleValue() : 0,
            profile.bmi() != null ? profile.bmi().doubleValue() : 0
        );

        List<HealthReportVO.DimensionAnalysis> dimensions = new ArrayList<>();
        dimensions.add(new HealthReportVO.DimensionAnalysis("综合健康", scoreVO.getScore(), getLevelText(scoreVO.getLevel()), "基于现有数据的综合评估"));

        List<HealthReportVO.ImprovementSuggestion> suggestions = new ArrayList<>();
        suggestions.add(new HealthReportVO.ImprovementSuggestion("生活方式", "中", "保持健康生活方式", "均衡饮食、适量运动、充足睡眠"));

        return new HealthReportVO(
            "RPT-" + System.currentTimeMillis(),
            LocalDateTime.now(),
            userInfo,
            scoreVO.getScore(),
            scoreVO.getLevel(),
            dimensions,
            new ArrayList<>(),
            suggestions,
            "您的健康评分为" + scoreVO.getScore() + "分，" + getLevelText(scoreVO.getLevel()) + "。"
        );
    }

    private String getLevelText(String level) {
        return switch (level) {
            case "EXCELLENT" -> "优秀";
            case "GOOD" -> "良好";
            case "FAIR" -> "一般";
            case "POOR" -> "需改善";
            default -> "未知";
        };
    }

    private String formatDecimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).toString();
    }
}