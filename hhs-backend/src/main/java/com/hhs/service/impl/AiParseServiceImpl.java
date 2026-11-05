package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.common.PageResult;
import com.hhs.common.constant.ErrorCode;
import com.hhs.common.enums.MetricCategory;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.MetricParseRequest;
import com.hhs.dto.WellnessMetricRequest;
import com.hhs.entity.AiParseHistory;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.AiParseHistoryMapper;
import com.hhs.service.AiParseService;
import com.hhs.service.HealthMetricService;
import com.hhs.service.WellnessService;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.MetricDefinition;
import com.hhs.service.domain.MetricKeyNormalizer;
import com.hhs.vo.AiParseHistoryVO;
import com.hhs.vo.HealthMetricVO;
import com.hhs.vo.MetricParseResultVO;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AI解析服务实现
 *
 * Phase 2 实现: 使用LangChain4j调用AI模型解析健康指标
 * Phase 7 重构: 接入权威元数据注册中心动态生成提示词并统一 Key 规范化与四梯度生理预警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiParseServiceImpl implements AiParseService {

    private final AiParseHistoryMapper aiParseHistoryMapper;
    private final HealthMetricService healthMetricService;
    private final WellnessService wellnessService;
    private final ObjectMapper objectMapper;
    private final ChatLanguageModel chatModel;
    private final HealthMetricMetaRegistry metaRegistry;

    /**
     * SQL limit参数最大值
     */
    private static final int MAX_LIMIT = 100;

    /**
     * 临床急危重症阈值标准 (Panic Limits)
     */
    private static final Map<String, BigDecimal[]> PANIC_LIMITS = Map.of(
        "systolicBP", new BigDecimal[]{new BigDecimal("70"), new BigDecimal("180")},
        "diastolicBP", new BigDecimal[]{new BigDecimal("40"), new BigDecimal("110")},
        "bloodGlucose", new BigDecimal[]{new BigDecimal("2.8"), new BigDecimal("16.7")},
        "fastingGlucose", new BigDecimal[]{new BigDecimal("2.8"), new BigDecimal("16.7")},
        "postprandialGlucose", new BigDecimal[]{new BigDecimal("2.8"), new BigDecimal("16.7")},
        "heartRate", new BigDecimal[]{new BigDecimal("45"), new BigDecimal("140")},
        "bodyTemperature", new BigDecimal[]{new BigDecimal("35.0"), new BigDecimal("39.5")},
        "bloodOxygen", new BigDecimal[]{new BigDecimal("90"), null}
    );

    /**
     * 动态生成的 AI 系统提示词缓存
     */
    private String cachedSystemPrompt;

    @PostConstruct
    public void initSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的健康数据解析助手。请从用户的输入中提取健康指标信息。\n\n");
        sb.append("## 指标对照表\n\n### 健康指标 (HEALTH):\n");
        sb.append("| 中文名称 | metricKey | 单位 | 正常参考区间 | 生理极限 |\n");
        sb.append("|---------|-----------|------|-------------|----------|\n");

        if (metaRegistry != null && metaRegistry.getAllDefinitions() != null) {
            for (MetricDefinition def : metaRegistry.getAllDefinitions().values()) {
                if (def.category() == MetricCategory.HEALTH) {
                    sb.append(String.format("| %s | %s | %s | %s ~ %s | %s ~ %s |\n",
                            def.displayName(), def.canonicalKey(), def.unit(),
                            formatNumber(def.normalMin()), formatNumber(def.normalMax()),
                            formatNumber(def.hardMin()), formatNumber(def.hardMax())));
                }
            }

            sb.append("\n### 保健指标 (WELLNESS):\n");
            sb.append("| 中文名称 | metricKey | 单位 | 正常参考区间 | 生理极限 | 说明 |\n");
            sb.append("|---------|-----------|------|-------------|----------|------|\n");

            for (MetricDefinition def : metaRegistry.getAllDefinitions().values()) {
                if (def.category() == MetricCategory.WELLNESS) {
                    sb.append(String.format("| %s | %s | %s | %s ~ %s | %s ~ %s | %s |\n",
                            def.displayName(), def.canonicalKey(), def.unit(),
                            formatNumber(def.normalMin()), formatNumber(def.normalMax()),
                            formatNumber(def.hardMin()), formatNumber(def.hardMax()),
                            def.guideHint() != null ? def.guideHint() : "-"));
                }
            }
        }

        sb.append("""

            ## 解析规则

            1. 识别用户输入中的数值和对应指标
            2. 判断指标属于 HEALTH 还是 WELLNESS 类别
            3. **血压必须拆分**: 用户提到"血压"时，必须返回两个指标：systolicBP（收缩压）和 diastolicBP（舒张压）。例如用户说"血压120/80"，返回 systolicBP=120 和 diastolicBP=80。**如果用户只给了一个数值（如"血压120"），不要返回任何血压指标，在 warnings 中提示"血压数据不完整，请提供收缩压/舒张压格式，如120/80"**
            4. 置信度(confidence)根据输入的明确程度设置(0.5-1.0)
            5. 如果数值可能异常(超出正常参考区间或生理极限)，在 warnings 中记录友好提示
            6. 如果无法确定具体指标，置信度设为0.5
            7. **严禁返回指标对照表中不存在的 metricKey**，只能使用上表中列出的 metricKey

            ## 返回格式 (必须严格遵循JSON格式)

            ```json
            {
              "metrics": [
                {
                  "metricKey": "bloodGlucose",
                  "metricName": "血糖",
                  "value": 5.6,
                  "unit": "mmol/L",
                  "confidence": 0.95,
                  "category": "HEALTH"
                }
              ],
              "summary": "成功解析出1个健康指标",
              "warnings": []
            }
            ```

            注意：
            - value必须是数字，不要包含单位
            - 如果输入包含多个指标，全部解析出来
            - 如果无法解析出任何指标，metrics数组为空，并在summary中说明原因
            - warnings数组用于提醒数值异常、数据不完整或歧义
            """);

        this.cachedSystemPrompt = sb.toString();
    }

    public String getCachedSystemPrompt() {
        if (cachedSystemPrompt == null) {
            initSystemPrompt();
        }
        return cachedSystemPrompt;
    }

    private static String formatNumber(BigDecimal val) {
        if (val == null) {
            return "-";
        }
        if (val.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return val.stripTrailingZeros().toPlainString();
    }

    @Override
    @Transactional
    public MetricParseResultVO parseMetrics(Long userId, MetricParseRequest request) {
        log.info("AI解析请求: userId={}, input={}", userId, request.getInput());

        String input = request.getInput().trim();
        LocalDate recordDate = request.getRecordDate() != null ? request.getRecordDate() : LocalDate.now();
        String inputType = StringUtils.hasText(request.getInputType()) ? request.getInputType() : "text";

        MetricParseResultVO result = new MetricParseResultVO();

        try {
            // 调用AI解析
            String aiResponse = callAIForParsing(input);
            result = parseAIResponse(aiResponse, recordDate);

        } catch (Exception e) {
            log.error("AI解析失败: {}", e.getMessage(), e);
            result.setMetrics(new ArrayList<>());
            result.setSummary("AI解析失败: " + e.getMessage());
            result.setWarnings(List.of("解析过程中发生错误，请稍后重试"));
        }

        // 保存解析历史
        AiParseHistory history = new AiParseHistory();
        history.setUserId(userId);
        history.setInputText(input);
        history.setInputType(inputType);
        history.setConfirmed(false);

        try {
            history.setParseResult(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            log.error("序列化解析结果失败", e);
        }

        aiParseHistoryMapper.insert(history);
        result.setParseHistoryId(history.getId());

        return result;
    }

    /**
     * 调用AI模型进行解析
     */
    private String callAIForParsing(String userInput) {
        log.debug("调用AI模型解析: {}", userInput);

        if (cachedSystemPrompt == null) {
            initSystemPrompt();
        }

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
            SystemMessage.from(cachedSystemPrompt),
            UserMessage.from("请解析以下健康数据输入：\n\n" + userInput)
        );

        Response<dev.langchain4j.data.message.AiMessage> response = chatModel.generate(messages);
        return response.content().text();
    }

    /**
     * 解析AI响应
     */
    @SuppressWarnings("unchecked")
    private MetricParseResultVO parseAIResponse(String aiResponse, LocalDate recordDate) {
        MetricParseResultVO result = new MetricParseResultVO();
        result.setMetrics(new ArrayList<>());
        result.setWarnings(new ArrayList<>());

        // 提取JSON部分 - 使用改进的提取方法
        String jsonStr = extractJsonFromResponse(aiResponse);
        if (jsonStr == null) {
            result.setSummary("AI响应格式错误，无法解析");
            result.setWarnings(List.of("AI返回的数据格式不正确"));
            return result;
        }

        try {
            Map<String, Object> responseMap = objectMapper.readValue(jsonStr, Map.class);

            // 解析摘要
            result.setSummary((String) responseMap.getOrDefault("summary", "解析完成"));

            // 解析警告
            Object warningsObj = responseMap.get("warnings");
            if (warningsObj instanceof List) {
                result.setWarnings((List<String>) warningsObj);
            }

            // 解析指标列表
            Object metricsObj = responseMap.get("metrics");
            if (metricsObj instanceof List) {
                List<Map<String, Object>> metricsList = (List<Map<String, Object>>) metricsObj;

                for (Map<String, Object> metricMap : metricsList) {
                    String rawMetricKey = (String) metricMap.get("metricKey");
                    String metricName = (String) metricMap.get("metricName");

                    // 血压拆分与拦截：若返回 bloodPressure，尝试根据复合字符串（如 120/80）拆分，否则拦截提示格式不完整
                    if ("bloodPressure".equals(rawMetricKey) || "blood_pressure".equals(rawMetricKey)) {
                        Object valObj = metricMap.get("value");
                        if (valObj != null && valObj.toString().contains("/")) {
                            String[] parts = valObj.toString().split("/");
                            if (parts.length == 2) {
                                try {
                                    BigDecimal sbpVal = new BigDecimal(parts[0].trim());
                                    BigDecimal dbpVal = new BigDecimal(parts[1].trim());

                                    MetricParseResultVO.ParsedMetric sbpMetric = new MetricParseResultVO.ParsedMetric();
                                    sbpMetric.setMetricKey("systolicBP");
                                    sbpMetric.setMetricName("收缩压");
                                    sbpMetric.setValue(sbpVal);
                                    sbpMetric.setUnit("mmHg");
                                    sbpMetric.setConfidence(new BigDecimal("0.95"));
                                    sbpMetric.setCategory("HEALTH");
                                    sbpMetric.setRecordDate(recordDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                                    sbpMetric.setSelected(true);
                                    String sbpWarning = validateMetricRange("systolicBP", sbpVal, "收缩压", "mmHg");
                                    if (sbpWarning != null) {
                                        result.getWarnings().add(sbpWarning);
                                    }
                                    result.getMetrics().add(sbpMetric);

                                    MetricParseResultVO.ParsedMetric dbpMetric = new MetricParseResultVO.ParsedMetric();
                                    dbpMetric.setMetricKey("diastolicBP");
                                    dbpMetric.setMetricName("舒张压");
                                    dbpMetric.setValue(dbpVal);
                                    dbpMetric.setUnit("mmHg");
                                    dbpMetric.setConfidence(new BigDecimal("0.95"));
                                    dbpMetric.setCategory("HEALTH");
                                    dbpMetric.setRecordDate(recordDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                                    dbpMetric.setSelected(true);
                                    String dbpWarning = validateMetricRange("diastolicBP", dbpVal, "舒张压", "mmHg");
                                    if (dbpWarning != null) {
                                        result.getWarnings().add(dbpWarning);
                                    }
                                    result.getMetrics().add(dbpMetric);
                                    continue;
                                } catch (Exception ex) {
                                    log.warn("无法解析血压复合数值: {}", valObj);
                                }
                            }
                        }
                        result.getWarnings().add("血压数据不完整，请提供收缩压/舒张压格式（如：血压120/80）");
                        log.warn("AI返回了未拆分的 bloodPressure metricKey，已拦截");
                        continue;
                    }

                    // 统一通过 MetricKeyNormalizer 强制归一化为小驼峰 Key (如 glucose -> bloodGlucose)
                    String canonicalKey = MetricKeyNormalizer.toCanonicalKey(rawMetricKey);

                    MetricParseResultVO.ParsedMetric metric = new MetricParseResultVO.ParsedMetric();
                    metric.setMetricKey(canonicalKey);
                    metric.setMetricName(metricName);

                    Object valueObj = metricMap.get("value");
                    if (valueObj instanceof Number) {
                        metric.setValue(toBigDecimal((Number) valueObj));
                    }

                    metric.setUnit((String) metricMap.get("unit"));

                    Object confidenceObj = metricMap.get("confidence");
                    if (confidenceObj instanceof Number) {
                        metric.setConfidence(toBigDecimal((Number) confidenceObj));
                    } else {
                        metric.setConfidence(new BigDecimal("0.8"));
                    }

                    String category = (String) metricMap.get("category");
                    Optional<MetricDefinition> defOpt = metaRegistry != null ? metaRegistry.getDefinition(canonicalKey) : Optional.empty();
                    if (defOpt.isPresent()) {
                        MetricDefinition def = defOpt.get();
                        metric.setCategory(def.category().name());
                        if (!StringUtils.hasText(metric.getMetricName())) {
                            metric.setMetricName(def.displayName());
                        }
                        if (!StringUtils.hasText(metric.getUnit())) {
                            metric.setUnit(def.unit());
                        }
                    } else {
                        metric.setCategory(StringUtils.hasText(category) ? category : "HEALTH");
                    }

                    metric.setRecordDate(recordDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    metric.setSelected(true);

                    // 验证metricKey有效性（基于权威注册表）
                    if (!isValidMetricKey(metric.getMetricKey(), metric.getCategory())) {
                        result.getWarnings().add(
                            String.format("不支持的指标类型 '%s'，已忽略", metricName != null ? metricName : metric.getMetricKey()));
                        log.warn("无效的metricKey被AI返回: {}", metric.getMetricKey());
                        continue;
                    }

                    // 四梯度生理范围预警评估
                    if (metric.getValue() != null) {
                        String rangeWarning = validateMetricRange(
                                metric.getMetricKey(), metric.getValue(),
                                metric.getMetricName(), metric.getUnit());
                        if (rangeWarning != null) {
                            result.getWarnings().add(rangeWarning);
                        }
                    }

                    result.getMetrics().add(metric);
                }
            }

        } catch (JsonProcessingException e) {
            log.error("JSON解析失败: {}", e.getMessage());
            result.setSummary("解析结果格式错误");
            result.setWarnings(List.of("无法解析AI返回的数据"));
        }

        return result;
    }

    /**
     * 从AI响应中提取JSON
     * 改进版本：正确处理嵌套JSON结构
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }

        // 尝试找到第一个 { 和最后一个匹配的 }
        int start = response.indexOf('{');
        if (start == -1) {
            return null;
        }

        int braceCount = 0;
        int end = -1;

        for (int i = start; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '{') {
                braceCount++;
            } else if (c == '}') {
                braceCount--;
                if (braceCount == 0) {
                    end = i;
                    break;
                }
            }
        }

        if (end == -1) {
            return null;
        }

        return response.substring(start, end + 1);
    }

    /**
     * 将Number转换为BigDecimal，保持精度
     */
    private BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }
        if (number instanceof Double || number instanceof Float) {
            return new BigDecimal(number.toString(), MathContext.DECIMAL64);
        }
        return new BigDecimal(number.longValue());
    }

    /**
     * 验证metricKey是否有效（基于 HealthMetricMetaRegistry）
     */
    private boolean isValidMetricKey(String metricKey, String category) {
        if (!StringUtils.hasText(metricKey) || metaRegistry == null) {
            return false;
        }

        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(metricKey);
        Optional<MetricDefinition> defOpt = metaRegistry.getDefinition(canonicalKey);
        if (defOpt.isEmpty()) {
            return false;
        }

        if (StringUtils.hasText(category)) {
            return defOpt.get().category().name().equalsIgnoreCase(category);
        }
        return true;
    }

    /**
     * 对齐四梯度异常预警
     * Tier 1 (正常): 无需异常 warning (返回 null)
     * Tier 2 (预警偏差): 提示数值偏高或偏低
     * Tier 3 (急危重症): 提示数值严重偏离，建议防误触并在确诊后就医
     * Tier 4 (物理极限): 提示数值超出人类生理极限
     */
    private String validateMetricRange(String metricKey, BigDecimal value, String metricName, String unit) {
        if (metaRegistry == null || value == null) {
            return null;
        }

        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(metricKey);
        Optional<MetricDefinition> defOpt = metaRegistry.getDefinition(canonicalKey);
        if (defOpt.isEmpty()) {
            return null;
        }

        MetricDefinition def = defOpt.get();
        String name = metricName != null ? metricName : def.displayName();
        String displayUnit = unit != null ? unit : (def.unit() != null ? def.unit() : "");

        BigDecimal hardMin = def.hardMin();
        BigDecimal hardMax = def.hardMax();
        BigDecimal normalMin = def.normalMin();
        BigDecimal normalMax = def.normalMax();

        // Tier 4: 超出人类生理极限
        if ((hardMin != null && value.compareTo(hardMin) < 0) || (hardMax != null && value.compareTo(hardMax) > 0)) {
            return String.format("指标 %s 的数值 %s %s 超出人类生理极限（%s~%s %s），请核对输入",
                    name, formatNumber(value), displayUnit,
                    formatNumber(hardMin), formatNumber(hardMax), displayUnit);
        }

        // Tier 3: 急危重症 (Panic Limits)
        BigDecimal[] panic = PANIC_LIMITS.get(canonicalKey);
        if (panic != null) {
            boolean isOxygenCritical = "bloodOxygen".equals(canonicalKey) && value.compareTo(panic[0]) <= 0;
            boolean isCritical = (panic[0] != null && value.compareTo(panic[0]) <= 0) ||
                    (panic[1] != null && value.compareTo(panic[1]) >= 0);
            if (isOxygenCritical || isCritical) {
                return String.format("指标 %s 的数值 %s %s 严重偏离正常范围，疑似急危重症数值，建议防误触并在确诊后就医",
                        name, formatNumber(value), displayUnit);
            }
        }

        // Tier 2: 预警偏差
        if (normalMax != null && value.compareTo(normalMax) > 0) {
            return String.format("指标 %s 的数值 %s %s 偏高（正常参考范围：%s~%s %s）",
                    name, formatNumber(value), displayUnit,
                    formatNumber(normalMin), formatNumber(normalMax), displayUnit);
        }
        if (normalMin != null && value.compareTo(normalMin) < 0) {
            return String.format("指标 %s 的数值 %s %s 偏低（正常参考范围：%s~%s %s）",
                    name, formatNumber(value), displayUnit,
                    formatNumber(normalMin), formatNumber(normalMax), displayUnit);
        }

        // Tier 1: 正常
        return null;
    }

    @Override
    @Transactional
    public List<Long> confirmMetrics(Long userId, Long parseHistoryId,
                                     List<MetricParseResultVO.ParsedMetric> metrics) {
        log.info("确认录入指标: userId={}, parseHistoryId={}, metrics={}", userId, parseHistoryId, metrics.size());

        // 验证输入
        if (metrics == null || metrics.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "指标列表不能为空");
        }

        // 验证解析历史
        AiParseHistory history = aiParseHistoryMapper.selectById(parseHistoryId);
        if (history == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "解析记录不存在");
        }
        if (!history.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权操作此记录");
        }
        if (history.getConfirmed()) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "此记录已确认，请勿重复提交");
        }

        // 验证指标是否在原始解析结果中
        validateMetricsAgainstHistory(metrics, history);

        List<Long> savedIds = new ArrayList<>();
        List<String> failedMetrics = new ArrayList<>();

        // 保存选中的指标
        for (MetricParseResultVO.ParsedMetric metric : metrics) {
            if (!Boolean.TRUE.equals(metric.getSelected())) {
                continue;
            }

            String canonicalKey = MetricKeyNormalizer.toCanonicalKey(metric.getMetricKey());
            metric.setMetricKey(canonicalKey);

            // 验证metricKey在白名单中
            if (!isValidMetricKey(canonicalKey, metric.getCategory())) {
                failedMetrics.add(metric.getMetricName() != null ? metric.getMetricName() : metric.getMetricKey());
                log.warn("无效的metricKey: {}", metric.getMetricKey());
                continue;
            }

            try {
                Long savedId = saveMetric(userId, metric);
                if (savedId != null) {
                    savedIds.add(savedId);
                } else {
                    failedMetrics.add(metric.getMetricName() != null ? metric.getMetricName() : metric.getMetricKey());
                }
            } catch (Exception e) {
                log.error("保存指标失败: metricKey={}, error={}", metric.getMetricKey(), e.getMessage());
                failedMetrics.add(metric.getMetricName() != null ? metric.getMetricName() : metric.getMetricKey());
            }
        }

        // 如果有失败的指标，抛出异常告知用户
        if (!failedMetrics.isEmpty() && savedIds.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "所有指标保存失败: " + String.join(", ", failedMetrics));
        }

        // 标记为已确认
        markAsConfirmed(parseHistoryId, savedIds);

        if (!failedMetrics.isEmpty()) {
            log.warn("部分指标保存失败: {}", failedMetrics);
        }

        log.info("成功保存 {} 个指标", savedIds.size());
        return savedIds;
    }

    /**
     * 验证提交的指标是否与原始解析结果匹配
     */
    @SuppressWarnings("unchecked")
    private void validateMetricsAgainstHistory(List<MetricParseResultVO.ParsedMetric> metrics, AiParseHistory history) {
        if (history.getParseResult() == null) {
            return;
        }

        try {
            Map<String, Object> parseResult = objectMapper.readValue(history.getParseResult(), Map.class);
            Object metricsObj = parseResult.get("metrics");
            if (!(metricsObj instanceof List)) {
                return;
            }

            List<Map<String, Object>> originalMetrics = (List<Map<String, Object>>) metricsObj;

            // 获取原始解析的metricKey集合（归一化为canonicalKey）
            Set<String> originalKeys = originalMetrics.stream()
                    .map(m -> (String) m.get("metricKey"))
                    .filter(StringUtils::hasText)
                    .map(MetricKeyNormalizer::toCanonicalKey)
                    .collect(java.util.stream.Collectors.toSet());

            // 检查提交的指标是否都在原始解析结果中
            for (MetricParseResultVO.ParsedMetric metric : metrics) {
                String canonicalKey = MetricKeyNormalizer.toCanonicalKey(metric.getMetricKey());
                if (!originalKeys.contains(canonicalKey)) {
                    log.warn("指标不在原始解析结果中: metricKey={}, parseHistoryId={}",
                            metric.getMetricKey(), history.getId());
                    throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER,
                            "指标 " + metric.getMetricKey() + " 不在原始解析结果中");
                }
            }

        } catch (JsonProcessingException e) {
            log.error("解析原始结果失败", e);
        }
    }

    /**
     * 保存单个指标
     */
    private Long saveMetric(Long userId, MetricParseResultVO.ParsedMetric metric) {
        LocalDate recordDate = metric.getRecordDate() != null
            ? LocalDate.parse(metric.getRecordDate())
            : LocalDate.now();

        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(metric.getMetricKey());

        if ("WELLNESS".equals(metric.getCategory())) {
            // 保存保健指标
            WellnessMetricRequest request = new WellnessMetricRequest();
            request.setMetricKey(canonicalKey);
            request.setValue(metric.getValue());
            request.setRecordDate(recordDate);
            request.setUnit(metric.getUnit());

            HealthMetricVO saved = wellnessService.createWellnessMetric(userId, request);
            return saved != null ? saved.id() : null;

        } else {
            // 保存健康指标
            HealthMetricRequest request = new HealthMetricRequest();
            request.setUserId(userId);
            request.setMetricKey(canonicalKey);
            request.setValue(metric.getValue());
            request.setRecordDate(recordDate);
            request.setUnit(metric.getUnit());

            HealthMetricVO saved = healthMetricService.add(userId, request);
            return saved != null ? saved.id() : null;
        }
    }

    @Override
    public List<AiParseHistory> getParseHistory(Long userId, int limit) {
        // 验证limit参数，防止SQL注入
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));

        LambdaQueryWrapper<AiParseHistory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AiParseHistory::getUserId, userId);
        queryWrapper.orderByDesc(AiParseHistory::getCreateTime);
        queryWrapper.last("LIMIT " + safeLimit);
        return aiParseHistoryMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional
    public AiParseHistory saveParseHistory(AiParseHistory parseHistory) {
        if (parseHistory.getCreateTime() == null) {
            parseHistory.setCreateTime(java.time.LocalDateTime.now());
        }
        aiParseHistoryMapper.insert(parseHistory);
        return parseHistory;
    }

    @Override
    @Transactional
    public void markAsConfirmed(Long id, List<Long> metricIds) {
        AiParseHistory history = aiParseHistoryMapper.selectById(id);
        if (history != null) {
            history.setConfirmed(true);
            try {
                history.setMetricIds(objectMapper.writeValueAsString(metricIds));
            } catch (JsonProcessingException e) {
                log.error("序列化指标ID列表失败", e);
            }
            aiParseHistoryMapper.updateById(history);
        }
    }

    @Override
    public PageResult<AiParseHistoryVO> getHistoryPage(Long userId, int page, int size) {
        log.info("获取AI解析历史: userId={}, page={}, size={}", userId, page, size);

        // Build query
        LambdaQueryWrapper<AiParseHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiParseHistory::getUserId, userId)
               .orderByDesc(AiParseHistory::getCreateTime);

        // Get total count
        long total = aiParseHistoryMapper.selectCount(wrapper);

        // Get paginated records
        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + size + " OFFSET " + offset);
        List<AiParseHistory> records = aiParseHistoryMapper.selectList(wrapper);

        // Convert to VO
        List<AiParseHistoryVO> historyList = records.stream()
                .map(this::toHistoryVO)
                .toList();

        return PageResult.of(total, page, size, historyList);
    }

    @Override
    public MetricParseResultVO getHistoryDetail(Long userId, Long id) {
        log.info("获取AI解析详情: userId={}, id={}", userId, id);

        AiParseHistory history = aiParseHistoryMapper.selectById(id);
        if (history == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "记录不存在");
        }
        if (!history.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权访问此记录");
        }

        // Parse result from stored JSON
        MetricParseResultVO result = new MetricParseResultVO();
        result.setParseHistoryId(history.getId());

        if (history.getParseResult() != null) {
            try {
                result = objectMapper.readValue(history.getParseResult(), MetricParseResultVO.class);
                result.setParseHistoryId(history.getId());
            } catch (JsonProcessingException e) {
                log.error("解析历史记录失败: {}", id, e);
                result.setSummary("无法解析历史记录");
                result.setMetrics(new ArrayList<>());
            }
        } else {
            result.setSummary("无解析结果");
            result.setMetrics(new ArrayList<>());
        }

        return result;
    }

    @Override
    @Transactional
    public boolean deleteHistory(Long userId, Long id) {
        log.info("删除AI解析历史: userId={}, id={}", userId, id);

        AiParseHistory history = aiParseHistoryMapper.selectById(id);
        if (history == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "记录不存在");
        }
        if (!history.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权删除此记录");
        }

        int deleted = aiParseHistoryMapper.deleteById(id);
        return deleted > 0;
    }

    private AiParseHistoryVO toHistoryVO(AiParseHistory history) {
        // Count metrics from parseResult
        int metricsCount = 0;
        if (history.getParseResult() != null) {
            try {
                MetricParseResultVO result = objectMapper.readValue(history.getParseResult(), MetricParseResultVO.class);
                if (result.getMetrics() != null) {
                    metricsCount = result.getMetrics().size();
                }
            } catch (JsonProcessingException e) {
                log.error("解析历史记录失败: {}", history.getId(), e);
            }
        }

        return AiParseHistoryVO.builder()
                .id(history.getId())
                .inputText(history.getInputText())
                .inputType(history.getInputType())
                .metricsCount(metricsCount)
                .confirmed(history.getConfirmed())
                .createTime(history.getCreateTime())
                .build();
    }
}