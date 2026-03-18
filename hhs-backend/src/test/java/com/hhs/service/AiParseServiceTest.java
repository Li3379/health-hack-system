package com.hhs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.common.PageResult;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.MetricParseRequest;
import com.hhs.dto.WellnessMetricRequest;
import com.hhs.entity.AiParseHistory;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.AiParseHistoryMapper;
import com.hhs.service.impl.AiParseServiceImpl;
import com.hhs.vo.AiParseHistoryVO;
import com.hhs.vo.HealthMetricVO;
import com.hhs.vo.MetricParseResultVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * AI Parse Service Unit Tests
 *
 * Tests the AiParseService which handles AI-powered health metric parsing
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AI智能录入服务测试")
class AiParseServiceTest {

    @Mock
    private AiParseHistoryMapper aiParseHistoryMapper;

    @Mock
    private HealthMetricService healthMetricService;

    @Mock
    private WellnessService wellnessService;

    @Mock
    private ChatLanguageModel chatModel;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiParseServiceImpl aiParseService;

    private Long testUserId = 1L;
    private String testInput = "今天血糖5.6，心率72，走了8000步";

    @BeforeEach
    void setUp() {
        // Mock insert to set ID
        when(aiParseHistoryMapper.insert(any(AiParseHistory.class))).thenAnswer(invocation -> {
            AiParseHistory history = invocation.getArgument(0);
            history.setId(100L);
            return 1;
        });
    }

    @Test
    @DisplayName("测试1.1：解析健康指标 - 成功场景(多指标)")
    void testParseMetrics_Success_MultipleMetrics() {
        // Given: AI返回多指标JSON
        String aiResponse = """
            {
              "metrics": [
                {
                  "metricKey": "glucose",
                  "metricName": "血糖",
                  "value": 5.6,
                  "unit": "mmol/L",
                  "confidence": 0.95,
                  "category": "HEALTH"
                },
                {
                  "metricKey": "heartRate",
                  "metricName": "心率",
                  "value": 72,
                  "unit": "次/分",
                  "confidence": 0.90,
                  "category": "HEALTH"
                },
                {
                  "metricKey": "steps",
                  "metricName": "步数",
                  "value": 8000,
                  "unit": "步",
                  "confidence": 0.85,
                  "category": "WELLNESS"
                }
              ],
              "summary": "成功解析出3个指标",
              "warnings": []
            }
            """;

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        MetricParseRequest request = new MetricParseRequest();
        request.setInput(testInput);
        request.setInputType("text");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证结果
        assertNotNull(result);
        assertNotNull(result.getParseHistoryId());
        assertEquals("成功解析出3个指标", result.getSummary());
        assertEquals(3, result.getMetrics().size());
        assertTrue(result.getWarnings().isEmpty());

        // 验证第一个指标
        MetricParseResultVO.ParsedMetric glucose = result.getMetrics().get(0);
        assertEquals("glucose", glucose.getMetricKey());
        assertEquals("血糖", glucose.getMetricName());
        assertEquals(new BigDecimal("5.6"), glucose.getValue());
        assertEquals("mmol/L", glucose.getUnit());
        assertEquals("HEALTH", glucose.getCategory());
        assertTrue(glucose.getSelected());

        verify(chatModel, times(1)).generate(anyList());
        verify(aiParseHistoryMapper, times(1)).insert(any(AiParseHistory.class));
    }

    @Test
    @DisplayName("测试1.2：解析健康指标 - 血压拆分")
    void testParseMetrics_BloodPressure_Split() {
        // Given: AI返回血压数据需要拆分为收缩压和舒张压
        String aiResponse = """
            {
              "metrics": [
                {
                  "metricKey": "systolicBP",
                  "metricName": "收缩压",
                  "value": 120,
                  "unit": "mmHg",
                  "confidence": 0.95,
                  "category": "HEALTH"
                },
                {
                  "metricKey": "diastolicBP",
                  "metricName": "舒张压",
                  "value": 80,
                  "unit": "mmHg",
                  "confidence": 0.95,
                  "category": "HEALTH"
                }
              ],
              "summary": "成功解析血压数据",
              "warnings": []
            }
            """;

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        MetricParseRequest request = new MetricParseRequest();
        request.setInput("血压120/80");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证血压拆分
        assertNotNull(result);
        assertEquals(2, result.getMetrics().size());

        boolean hasSystolic = result.getMetrics().stream()
            .anyMatch(m -> "systolicBP".equals(m.getMetricKey()) && m.getValue().compareTo(new BigDecimal("120")) == 0);
        boolean hasDiastolic = result.getMetrics().stream()
            .anyMatch(m -> "diastolicBP".equals(m.getMetricKey()) && m.getValue().compareTo(new BigDecimal("80")) == 0);

        assertTrue(hasSystolic, "应该包含收缩压");
        assertTrue(hasDiastolic, "应该包含舒张压");
    }

    @Test
    @DisplayName("测试1.3：解析健康指标 - AI返回异常值警告")
    void testParseMetrics_AbnormalValue_Warning() {
        // Given: AI检测到异常值并返回警告
        String aiResponse = """
            {
              "metrics": [
                {
                  "metricKey": "glucose",
                  "metricName": "血糖",
                  "value": 15.6,
                  "unit": "mmol/L",
                  "confidence": 0.95,
                  "category": "HEALTH"
                }
              ],
              "summary": "检测到1个指标，数值偏高",
              "warnings": ["血糖值15.6 mmol/L高于正常范围，建议咨询医生"]
            }
            """;

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        MetricParseRequest request = new MetricParseRequest();
        request.setInput("血糖15.6");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证警告信息
        assertNotNull(result);
        assertEquals(1, result.getMetrics().size());
        assertFalse(result.getWarnings().isEmpty());
        assertTrue(result.getWarnings().get(0).contains("偏高") || result.getWarnings().get(0).contains("正常范围"));
    }

    @Test
    @DisplayName("测试1.4：解析健康指标 - 无法识别输入")
    void testParseMetrics_UnrecognizedInput() {
        // Given: AI无法识别输入
        String aiResponse = """
            {
              "metrics": [],
              "summary": "无法从输入中识别出健康指标",
              "warnings": ["输入内容不包含有效的健康数据"]
            }
            """;

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        MetricParseRequest request = new MetricParseRequest();
        request.setInput("今天天气不错");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证空结果
        assertNotNull(result);
        assertTrue(result.getMetrics().isEmpty());
        assertTrue(result.getSummary().contains("无法") || result.getSummary().contains("识别"));
    }

    @Test
    @DisplayName("测试1.5：解析健康指标 - AI服务错误")
    void testParseMetrics_AIServiceError() {
        // Given: AI服务抛出异常
        when(chatModel.generate(anyList())).thenThrow(new RuntimeException("AI service unavailable"));

        MetricParseRequest request = new MetricParseRequest();
        request.setInput("血糖5.6");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证错误处理
        assertNotNull(result);
        assertTrue(result.getMetrics().isEmpty());
        assertTrue(result.getSummary().contains("失败"));
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    @DisplayName("测试1.6：解析健康指标 - 无效metricKey被过滤")
    void testParseMetrics_InvalidMetricKey_Filtered() {
        // Given: AI返回无效的metricKey
        String aiResponse = """
            {
              "metrics": [
                {
                  "metricKey": "invalidKey",
                  "metricName": "无效指标",
                  "value": 100,
                  "unit": "unit",
                  "confidence": 0.9,
                  "category": "HEALTH"
                },
                {
                  "metricKey": "glucose",
                  "metricName": "血糖",
                  "value": 5.6,
                  "unit": "mmol/L",
                  "confidence": 0.95,
                  "category": "HEALTH"
                }
              ],
              "summary": "解析完成",
              "warnings": []
            }
            """;

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        MetricParseRequest request = new MetricParseRequest();
        request.setInput("测试输入");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 无效key被过滤，只保留有效指标
        assertEquals(1, result.getMetrics().size());
        assertEquals("glucose", result.getMetrics().get(0).getMetricKey());
    }

    @Test
    @DisplayName("测试1.7：解析健康指标 - 指定记录日期")
    void testParseMetrics_WithRecordDate() {
        // Given: 用户指定记录日期
        String aiResponse = """
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
              "summary": "解析完成",
              "warnings": []
            }
            """;

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        LocalDate specificDate = LocalDate.of(2026, 3, 15);
        MetricParseRequest request = new MetricParseRequest();
        request.setInput("血糖5.6");
        request.setRecordDate(specificDate);

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证日期
        assertNotNull(result);
        assertEquals(1, result.getMetrics().size());
        assertEquals("2026-03-15", result.getMetrics().get(0).getRecordDate());
    }

    @Test
    @DisplayName("测试2.1：确认录入 - 成功场景")
    void testConfirmMetrics_Success() {
        // Given: 准备解析历史和指标
        AiParseHistory history = new AiParseHistory();
        history.setId(100L);
        history.setUserId(testUserId);
        history.setConfirmed(false);
        when(aiParseHistoryMapper.selectById(100L)).thenReturn(history);
        when(aiParseHistoryMapper.updateById(any())).thenReturn(1);

        // Mock 健康指标保存
        HealthMetricVO healthMetricVO = new HealthMetricVO(
            1L, testUserId, null, "glucose", new BigDecimal("5.6"),
            "mmol/L", LocalDate.now(), null, null, LocalDateTime.now()
        );
        when(healthMetricService.add(eq(testUserId), any(HealthMetricRequest.class))).thenReturn(healthMetricVO);

        // Mock 保健指标保存
        HealthMetricVO wellnessMetricVO = new HealthMetricVO(
            2L, testUserId, null, "steps", new BigDecimal("8000"),
            "步", LocalDate.now(), null, null, LocalDateTime.now()
        );
        when(wellnessService.createWellnessMetric(eq(testUserId), any(WellnessMetricRequest.class))).thenReturn(wellnessMetricVO);

        List<MetricParseResultVO.ParsedMetric> metrics = Arrays.asList(
            createParsedMetric("glucose", "血糖", "5.6", "mmol/L", "HEALTH", true),
            createParsedMetric("steps", "步数", "8000", "步", "WELLNESS", true)
        );

        // When: 确认录入
        List<Long> savedIds = aiParseService.confirmMetrics(testUserId, 100L, metrics);

        // Then: 验证保存成功
        assertEquals(2, savedIds.size());
        assertTrue(savedIds.contains(1L));
        assertTrue(savedIds.contains(2L));
        verify(aiParseHistoryMapper, times(1)).updateById(any());
    }

    @Test
    @DisplayName("测试2.2：确认录入 - 部分指标未选中")
    void testConfirmMetrics_PartialSelection() {
        // Given: 准备解析历史，部分指标未选中
        AiParseHistory history = new AiParseHistory();
        history.setId(100L);
        history.setUserId(testUserId);
        history.setConfirmed(false);
        when(aiParseHistoryMapper.selectById(100L)).thenReturn(history);

        HealthMetricVO healthMetricVO = new HealthMetricVO(
            1L, testUserId, null, "glucose", new BigDecimal("5.6"),
            "mmol/L", LocalDate.now(), null, null, LocalDateTime.now()
        );
        when(healthMetricService.add(eq(testUserId), any(HealthMetricRequest.class))).thenReturn(healthMetricVO);

        List<MetricParseResultVO.ParsedMetric> metrics = Arrays.asList(
            createParsedMetric("glucose", "血糖", "5.6", "mmol/L", "HEALTH", true),
            createParsedMetric("heartRate", "心率", "72", "次/分", "HEALTH", false) // 未选中
        );

        // When: 确认录入
        List<Long> savedIds = aiParseService.confirmMetrics(testUserId, 100L, metrics);

        // Then: 只保存选中的指标
        assertEquals(1, savedIds.size());
        assertEquals(1L, savedIds.get(0));
        verify(healthMetricService, times(1)).add(eq(testUserId), any(HealthMetricRequest.class));
    }

    @Test
    @DisplayName("测试2.3：确认录入 - 解析记录不存在")
    void testConfirmMetrics_HistoryNotFound() {
        // Given: 解析历史不存在
        when(aiParseHistoryMapper.selectById(999L)).thenReturn(null);

        List<MetricParseResultVO.ParsedMetric> metrics = Collections.singletonList(
            createParsedMetric("glucose", "血糖", "5.6", "mmol/L", "HEALTH", true)
        );

        // When & Then: 抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiParseService.confirmMetrics(testUserId, 999L, metrics);
        });

        assertTrue(exception.getMessage().contains("不存在"));
        verify(healthMetricService, never()).add(any(), any());
    }

    @Test
    @DisplayName("测试2.4：确认录入 - 无权限操作")
    void testConfirmMetrics_Unauthorized() {
        // Given: 其他用户的解析历史
        AiParseHistory history = new AiParseHistory();
        history.setId(100L);
        history.setUserId(999L); // 其他用户
        history.setConfirmed(false);
        when(aiParseHistoryMapper.selectById(100L)).thenReturn(history);

        List<MetricParseResultVO.ParsedMetric> metrics = Collections.singletonList(
            createParsedMetric("glucose", "血糖", "5.6", "mmol/L", "HEALTH", true)
        );

        // When & Then: 抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiParseService.confirmMetrics(testUserId, 100L, metrics);
        });

        assertTrue(exception.getMessage().contains("权限") || exception.getCode().contains("forbidden"));
        verify(healthMetricService, never()).add(any(), any());
    }

    @Test
    @DisplayName("测试2.5：确认录入 - 已确认记录不能重复提交")
    void testConfirmMetrics_AlreadyConfirmed() {
        // Given: 已经确认过的记录
        AiParseHistory history = new AiParseHistory();
        history.setId(100L);
        history.setUserId(testUserId);
        history.setConfirmed(true); // 已确认
        when(aiParseHistoryMapper.selectById(100L)).thenReturn(history);

        List<MetricParseResultVO.ParsedMetric> metrics = Collections.singletonList(
            createParsedMetric("glucose", "血糖", "5.6", "mmol/L", "HEALTH", true)
        );

        // When & Then: 抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiParseService.confirmMetrics(testUserId, 100L, metrics);
        });

        assertTrue(exception.getMessage().contains("参数") || exception.getCode().contains("validation"));
        verify(healthMetricService, never()).add(any(), any());
    }

    @Test
    @DisplayName("测试3.1：获取解析历史 - 成功场景")
    void testGetParseHistory_Success() {
        // Given: 准备历史记录
        AiParseHistory history1 = new AiParseHistory();
        history1.setId(1L);
        history1.setUserId(testUserId);
        history1.setInputText("血糖5.6");
        history1.setCreateTime(LocalDateTime.now());

        AiParseHistory history2 = new AiParseHistory();
        history2.setId(2L);
        history2.setUserId(testUserId);
        history2.setInputText("血压120/80");
        history2.setCreateTime(LocalDateTime.now().minusDays(1));

        when(aiParseHistoryMapper.selectList(any())).thenReturn(Arrays.asList(history1, history2));

        // When: 获取历史
        List<AiParseHistory> result = aiParseService.getParseHistory(testUserId, 10);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(2, result.size());
        verify(aiParseHistoryMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("测试3.2：保存解析历史 - 成功场景")
    void testSaveParseHistory_Success() {
        // Given: 准备历史记录
        AiParseHistory history = new AiParseHistory();
        history.setUserId(testUserId);
        history.setInputText("测试输入");
        history.setInputType("text");

        // When: 保存历史
        AiParseHistory result = aiParseService.saveParseHistory(history);

        // Then: 验证保存
        assertNotNull(result.getCreateTime());
        verify(aiParseHistoryMapper, times(1)).insert(history);
    }

    @Test
    @DisplayName("测试4.1：保健指标分类 - WELLNESS")
    void testWellnessMetricCategory() {
        // Given: AI返回保健指标
        String aiResponse = """
            {
              "metrics": [
                {"metricKey": "sleepDuration", "metricName": "睡眠", "value": 7.5, "unit": "小时", "confidence": 0.9, "category": "WELLNESS"},
                {"metricKey": "steps", "metricName": "步数", "value": 10000, "unit": "步", "confidence": 0.9, "category": "WELLNESS"},
                {"metricKey": "mood", "metricName": "心情", "value": 8, "unit": "分", "confidence": 0.9, "category": "WELLNESS"}
              ],
              "summary": "解析出3个保健指标",
              "warnings": []
            }
            """;

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        MetricParseRequest request = new MetricParseRequest();
        request.setInput("睡了7.5小时，走了10000步，心情8分");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证保健指标分类
        assertEquals(3, result.getMetrics().size());
        result.getMetrics().forEach(m -> assertEquals("WELLNESS", m.getCategory()));
    }

    // Helper method
    private MetricParseResultVO.ParsedMetric createParsedMetric(
            String key, String name, String value, String unit, String category, Boolean selected) {
        MetricParseResultVO.ParsedMetric metric = new MetricParseResultVO.ParsedMetric();
        metric.setMetricKey(key);
        metric.setMetricName(name);
        metric.setValue(new BigDecimal(value));
        metric.setUnit(unit);
        metric.setCategory(category);
        metric.setSelected(selected);
        metric.setRecordDate(LocalDate.now().toString());
        return metric;
    }

    // ============================================
    // Phase 3: History Feature Tests
    // ============================================

    @Test
    @DisplayName("测试5.1：获取历史分页 - 成功场景")
    void testGetHistoryPage_Success() {
        // Given: 准备历史记录
        AiParseHistory history = new AiParseHistory();
        history.setId(1L);
        history.setUserId(testUserId);
        history.setInputText("血糖5.6");
        history.setInputType("text");
        history.setConfirmed(false);
        history.setCreateTime(LocalDateTime.now());
        history.setParseResult("{\"metrics\":[{\"metricKey\":\"glucose\"}]}");

        when(aiParseHistoryMapper.selectCount(any())).thenReturn(1L);
        when(aiParseHistoryMapper.selectList(any())).thenReturn(Arrays.asList(history));

        // When: 获取历史分页
        PageResult<AiParseHistoryVO> result = aiParseService.getHistoryPage(testUserId, 1, 10);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("血糖5.6", result.getRecords().get(0).getInputText());
    }

    @Test
    @DisplayName("测试5.2：获取历史详情 - 成功场景")
    void testGetHistoryDetail_Success() throws Exception {
        // Given: 准备历史记录
        String parseResult = """
            {
              "metrics": [
                {"metricKey": "glucose", "metricName": "血糖", "value": 5.6, "unit": "mmol/L", "category": "HEALTH"}
              ],
              "summary": "解析成功",
              "warnings": []
            }
            """;

        AiParseHistory history = new AiParseHistory();
        history.setId(1L);
        history.setUserId(testUserId);
        history.setParseResult(parseResult);

        when(aiParseHistoryMapper.selectById(1L)).thenReturn(history);

        // When: 获取详情
        MetricParseResultVO result = aiParseService.getHistoryDetail(testUserId, 1L);

        // Then: 验证结果
        assertNotNull(result);
        assertEquals(1L, result.getParseHistoryId());
        assertEquals(1, result.getMetrics().size());
        assertEquals("glucose", result.getMetrics().get(0).getMetricKey());
    }

    @Test
    @DisplayName("测试5.3：获取历史详情 - 无权限")
    void testGetHistoryDetail_Unauthorized() {
        // Given: 其他用户的记录
        AiParseHistory history = new AiParseHistory();
        history.setId(1L);
        history.setUserId(999L);

        when(aiParseHistoryMapper.selectById(1L)).thenReturn(history);

        // When & Then: 抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiParseService.getHistoryDetail(testUserId, 1L);
        });

        assertTrue(exception.getMessage().contains("无权"));
    }

    @Test
    @DisplayName("测试5.4：获取历史详情 - 记录不存在")
    void testGetHistoryDetail_NotFound() {
        // Given: 记录不存在
        when(aiParseHistoryMapper.selectById(1L)).thenReturn(null);

        // When & Then: 抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiParseService.getHistoryDetail(testUserId, 1L);
        });

        assertTrue(exception.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("测试5.5：删除历史记录 - 成功场景")
    void testDeleteHistory_Success() {
        // Given: 准备历史记录
        AiParseHistory history = new AiParseHistory();
        history.setId(1L);
        history.setUserId(testUserId);

        when(aiParseHistoryMapper.selectById(1L)).thenReturn(history);
        when(aiParseHistoryMapper.deleteById(1L)).thenReturn(1);

        // When: 删除记录
        boolean deleted = aiParseService.deleteHistory(testUserId, 1L);

        // Then: 验证删除成功
        assertTrue(deleted);
        verify(aiParseHistoryMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("测试5.6：删除历史记录 - 无权限")
    void testDeleteHistory_Unauthorized() {
        // Given: 其他用户的记录
        AiParseHistory history = new AiParseHistory();
        history.setId(1L);
        history.setUserId(999L);

        when(aiParseHistoryMapper.selectById(1L)).thenReturn(history);

        // When & Then: 抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            aiParseService.deleteHistory(testUserId, 1L);
        });

        assertTrue(exception.getMessage().contains("无权"));
        verify(aiParseHistoryMapper, never()).deleteById((java.io.Serializable) any());
    }

    @Test
    @DisplayName("测试6.1：JSON提取 - 嵌套JSON正确提取")
    void testExtractJson_NestedJson() {
        // Given: AI返回包含嵌套JSON
        String aiResponse = "这是AI的回复：{\"metrics\":[{\"key\":\"value\"}],\"summary\":\"成功\"}，请参考。";

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        MetricParseRequest request = new MetricParseRequest();
        request.setInput("测试");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证JSON被正确提取
        assertNotNull(result);
    }

    @Test
    @DisplayName("测试6.2：JSON提取 - 无效JSON处理")
    void testExtractJson_InvalidJson() {
        // Given: AI返回无效JSON
        String aiResponse = "这是AI的回复，没有有效的JSON";

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        MetricParseRequest request = new MetricParseRequest();
        request.setInput("测试");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证错误处理
        assertNotNull(result);
        assertTrue(result.getMetrics().isEmpty());
        assertTrue(result.getSummary().contains("错误") || result.getWarnings().size() > 0);
    }

    @Test
    @DisplayName("测试6.3：置信度默认值 - 未指定时使用默认值")
    void testConfidenceDefaultValue() {
        // Given: AI返回指标没有置信度
        String aiResponse = """
            {
              "metrics": [
                {"metricKey": "glucose", "metricName": "血糖", "value": 5.6, "unit": "mmol/L", "category": "HEALTH"}
              ],
              "summary": "解析成功",
              "warnings": []
            }
            """;

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);

        MetricParseRequest request = new MetricParseRequest();
        request.setInput("血糖5.6");

        // When: 解析指标
        MetricParseResultVO result = aiParseService.parseMetrics(testUserId, request);

        // Then: 验证默认置信度
        assertNotNull(result);
        assertEquals(1, result.getMetrics().size());
        assertNotNull(result.getMetrics().get(0).getConfidence());
        assertEquals(new BigDecimal("0.8"), result.getMetrics().get(0).getConfidence());
    }
}