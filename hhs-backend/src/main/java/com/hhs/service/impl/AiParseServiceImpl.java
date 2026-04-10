package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.common.PageResult;
import com.hhs.common.constant.ErrorCode;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.MetricParseRequest;
import com.hhs.dto.WellnessMetricRequest;
import com.hhs.entity.AiParseHistory;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.AiParseHistoryMapper;
import com.hhs.service.AiParseService;
import com.hhs.service.HealthMetricService;
import com.hhs.service.WellnessService;
import com.hhs.vo.AiParseHistoryVO;
import com.hhs.vo.HealthMetricVO;
import com.hhs.vo.MetricParseResultVO;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
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
import java.util.Set;

import static java.util.Map.entry;

/**
 * AI解析服务实现
 *
 * Phase 2 实现: 使用LangChain4j调用AI模型解析健康指标
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

    /**
     * SQL limit参数最大值
     */
    private static final int MAX_LIMIT = 100;

    /**
     * AI解析系统提示词
     */
    private static final String PARSE_SYSTEM_PROMPT = """
        你是一个专业的健康数据解析助手。请从用户的输入中提取健康指标信息。

        ## 指标对照表

        ### 健康指标 (HEALTH):
        | 中文名称 | metricKey | 单位 | 正常范围 |
        |---------|-----------|------|---------|
        | 血糖 | glucose | mmol/L | 3.9-6.1 |
        | 空腹血糖 | fastingGlucose | mmol/L | 3.9-6.1 |
        | 餐后血糖 | postprandialGlucose | mmol/L | <7.8 |
        | 心率 | heartRate | 次/分 | 60-100 |
        | 收缩压 | systolicBP | mmHg | 90-140 |
        | 舒张压 | diastolicBP | mmHg | 60-90 |
        | 体重 | weight | kg | - |
        | 体温 | bodyTemperature | ℃ | 36.1-37.2 |
        | 血氧 | bloodOxygen | % | 95-100 |
        | BMI | bmi | - | 18.5-24 |
        | 总胆固醇 | totalCholesterol | mmol/L | <5.2 |
        | 甘油三酯 | triglycerides | mmol/L | 0.4-1.7 |

        ### 保健指标 (WELLNESS):
        | 中文名称 | metricKey | 单位 | 说明 |
        |---------|-----------|------|------|
        | 睡眠 | sleepDuration | 小时 | 睡眠时长 |
        | 睡眠质量 | sleepQuality | 分 | 1-10分 |
        | 步数 | steps | 步 | 今日步数 |
        | 运动 | exerciseMinutes | 分钟 | 运动时长 |
        | 饮水 | waterIntake | 毫升 | 饮水量 |
        | 心情 | mood | 分 | 1-10分 |
        | 精力 | energy | 分 | 1-10分 |

        ## 解析规则

        1. 识别用户输入中的数值和对应指标
        2. 判断指标属于 HEALTH 还是 WELLNESS 类别
        3. **血压必须拆分**: 用户提到"血压"时，必须返回两个指标：systolicBP（收缩压）和 diastolicBP（舒张压）。例如用户说"血压120/80"，返回 systolicBP=120 和 diastolicBP=80。**如果用户只给了一个数值（如"血压120"），不要返回任何血压指标，在 warnings 中提示"血压数据不完整，请提供收缩压/舒张压格式，如120/80"**
        4. 置信度(confidence)根据输入的明确程度设置(0.5-1.0)
        5. 如果数值可能异常(超出正常范围)，在warnings中提醒
        6. 如果无法确定具体指标，置信度设为0.5
        7. **严禁返回指标对照表中不存在的 metricKey**，只能使用上表中列出的 metricKey

        ## 返回格式 (必须严格遵循JSON格式)

        ```json
        {
          "metrics": [
            {
              "metricKey": "glucose",
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
        """;

    /**
     * 健康指标白名单（不含 bloodPressure，血压必须拆分为 systolicBP/diastolicBP）
     */
    private static final Set<String> HEALTH_KEYS = Set.of(
        "glucose", "fastingGlucose", "postprandialGlucose",
        "heartRate", "systolicBP", "diastolicBP",
        "weight", "bodyTemperature", "bloodOxygen", "bmi",
        "totalCholesterol", "triglycerides"
    );

    /**
     * 保健指标白名单
     */
    private static final Set<String> WELLNESS_KEYS = Set.of(
        "sleepDuration", "sleepQuality", "steps",
        "exerciseMinutes", "waterIntake", "mood", "energy"
    );

    /**
     * 逐指标数值合理范围（硬性上下限，超出则拒绝入库）
     */
    private static final Map<String, BigDecimal[]> METRIC_RANGES = Map.ofEntries(
        entry("glucose",             new BigDecimal[]{bd("1"),   bd("33.3")}),
        entry("fastingGlucose",      new BigDecimal[]{bd("1"),   bd("33.3")}),
        entry("postprandialGlucose", new BigDecimal[]{bd("1"),   bd("33.3")}),
        entry("heartRate",           new BigDecimal[]{bd("20"),  bd("300")}),
        entry("systolicBP",          new BigDecimal[]{bd("40"),  bd("300")}),
        entry("diastolicBP",         new BigDecimal[]{bd("20"),  bd("200")}),
        entry("weight",              new BigDecimal[]{bd("1"),   bd("500")}),
        entry("bodyTemperature",     new BigDecimal[]{bd("34"),  bd("42")}),
        entry("bloodOxygen",         new BigDecimal[]{bd("50"),  bd("100")}),
        entry("bmi",                 new BigDecimal[]{bd("5"),   bd("60")}),
        entry("totalCholesterol",    new BigDecimal[]{bd("1"),   bd("15")}),
        entry("triglycerides",       new BigDecimal[]{bd("0.1"), bd("10")}),
        entry("sleepDuration",       new BigDecimal[]{bd("0"),   bd("24")}),
        entry("sleepQuality",        new BigDecimal[]{bd("1"),   bd("10")}),
        entry("steps",               new BigDecimal[]{bd("0"),   bd("200000")}),
        entry("exerciseMinutes",     new BigDecimal[]{bd("0"),   bd("1440")}),
        entry("waterIntake",         new BigDecimal[]{bd("0"),   bd("20000")}),
        entry("mood",                new BigDecimal[]{bd("1"),   bd("10")}),
        entry("energy",              new BigDecimal[]{bd("1"),   bd("10")})
    );

    private static BigDecimal bd(String val) {
        return new BigDecimal(val);
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

        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(
            SystemMessage.from(PARSE_SYSTEM_PROMPT),
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
                    String metricKey = (String) metricMap.get("metricKey");
                    String metricName = (String) metricMap.get("metricName");

                    // 拦截 bloodPressure：不允许该 key 入库，要求拆分为 systolicBP/diastolicBP
                    if ("bloodPressure".equals(metricKey)) {
                        result.getWarnings().add("血压数据不完整，请提供收缩压/舒张压格式（如：血压120/80）");
                        log.warn("AI返回了 bloodPressure metricKey，已拦截");
                        continue;
                    }

                    MetricParseResultVO.ParsedMetric metric = new MetricParseResultVO.ParsedMetric();
                    metric.setMetricKey(metricKey);
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
                    metric.setCategory(StringUtils.hasText(category) ? category : "HEALTH");

                    metric.setRecordDate(recordDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                    metric.setSelected(true);

                    // 验证metricKey有效性
                    if (!isValidMetricKey(metric.getMetricKey(), metric.getCategory())) {
                        result.getWarnings().add(
                            String.format("不支持的指标类型 '%s'，已忽略", metricName != null ? metricName : metricKey));
                        log.warn("无效的metricKey被AI返回: {}", metricKey);
                        continue;
                    }

                    // 验证数值范围
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
     * 验证metricKey是否有效
     */
    private boolean isValidMetricKey(String metricKey, String category) {
        if (!StringUtils.hasText(metricKey)) {
            return false;
        }

        if ("WELLNESS".equals(category)) {
            return WELLNESS_KEYS.contains(metricKey);
        }
        return HEALTH_KEYS.contains(metricKey);
    }

    /**
     * 验证指标数值是否在合理范围内
     *
     * @return null 表示在范围内，否则返回警告消息
     */
    private String validateMetricRange(String metricKey, BigDecimal value, String metricName, String unit) {
        BigDecimal[] range = METRIC_RANGES.get(metricKey);
        if (range == null) {
            return null;
        }
        if (value.compareTo(range[0]) < 0 || value.compareTo(range[1]) > 0) {
            return String.format("指标 %s 的数值 %s %s 超出合理范围（%s~%s %s），请确认输入是否正确",
                    metricName != null ? metricName : metricKey,
                    value.toPlainString(), unit,
                    range[0].toPlainString(), range[1].toPlainString(), unit);
        }
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

            // 验证metricKey在白名单中
            if (!isValidMetricKey(metric.getMetricKey(), metric.getCategory())) {
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

            // 获取原始解析的metricKey集合
            Set<String> originalKeys = originalMetrics.stream()
                    .map(m -> (String) m.get("metricKey"))
                    .filter(StringUtils::hasText)
                    .collect(java.util.stream.Collectors.toSet());

            // 检查提交的指标是否都在原始解析结果中
            for (MetricParseResultVO.ParsedMetric metric : metrics) {
                if (!originalKeys.contains(metric.getMetricKey())) {
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

        if ("WELLNESS".equals(metric.getCategory())) {
            // 保存保健指标
            WellnessMetricRequest request = new WellnessMetricRequest();
            request.setMetricKey(metric.getMetricKey());
            request.setValue(metric.getValue());
            request.setRecordDate(recordDate);
            request.setUnit(metric.getUnit());

            HealthMetricVO saved = wellnessService.createWellnessMetric(userId, request);
            return saved != null ? saved.id() : null;

        } else {
            // 保存健康指标
            HealthMetricRequest request = new HealthMetricRequest();
            request.setUserId(userId);
            request.setMetricKey(metric.getMetricKey());
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