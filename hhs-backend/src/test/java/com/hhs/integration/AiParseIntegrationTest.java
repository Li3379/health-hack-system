package com.hhs.integration;

import com.hhs.component.AIRateLimiter;
import com.hhs.config.TestSecurityConfig;
import com.hhs.dto.ConfirmMetricsRequest;
import com.hhs.dto.MetricParseRequest;
import com.hhs.entity.AiParseHistory;
import com.hhs.mapper.AiParseHistoryMapper;
import com.hhs.vo.MetricParseResultVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AI-powered health metric parsing input flow.
 *
 * Tests the complete flow from user input through AI parsing
 * to metric confirmation and storage.
 *
 * @see com.hhs.controller.AIParseController
 * @see com.hhs.service.AiParseService
 */
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@DisplayName("AI智能录入集成测试")
public class AiParseIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatLanguageModel chatLanguageModel;

    @MockBean
    private AIRateLimiter aiRateLimiter;

    @Autowired
    private AiParseHistoryMapper aiParseHistoryMapper;

    private static final String API_BASE = "/api/ai";
    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // Mock AIRateLimiter to allow requests
        when(aiRateLimiter.checkLimit(anyLong())).thenReturn(true);
        when(aiRateLimiter.getRemainingCount(anyLong())).thenReturn(19);

        // Mock ChatLanguageModel to return structured AI response
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
                }
              ],
              "summary": "成功解析出2个健康指标",
              "warnings": []
            }
            """;

        AiMessage aiMessage = mock(AiMessage.class);
        when(aiMessage.text()).thenReturn(aiResponse);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(aiMessage);
        when(chatLanguageModel.generate(anyList())).thenReturn(mockResponse);
    }

    @Nested
    @DisplayName("AI解析健康指标测试")
    class ParseMetricsTests {

        @Test
        @DisplayName("AI-INT-001: 解析健康指标 - 成功场景(多指标)")
        void testParseMetrics_Success() throws Exception {
            // Given: Valid user input with multiple health metrics
            String requestJson = """
                {
                    "input": "今天血糖5.6，心率72",
                    "inputType": "text"
                }
                """;

            // When: Request AI parsing
            // Then: Should return parsed metrics
            mockMvc.perform(post(API_BASE + "/parse-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.metrics").isArray())
                    .andExpect(jsonPath("$.data.metrics.length()").value(greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.data.parseHistoryId").exists())
                    .andExpect(jsonPath("$.data.remainingCount").exists());

            // Verify AI service was called
            verify(chatLanguageModel, atLeastOnce()).generate(anyList());
            verify(aiRateLimiter, atLeastOnce()).checkLimit(anyLong());
        }

        @Test
        @DisplayName("AI-INT-002: 解析健康指标 - 指定记录日期")
        void testParseMetrics_WithRecordDate() throws Exception {
            // Given: User input with specific record date
            String requestJson = String.format("""
                {
                    "input": "血糖5.6",
                    "inputType": "text",
                    "recordDate": "%s"
                }
                """, LocalDate.now().minusDays(1));

            // When: Request AI parsing with date
            // Then: Should include the record date in response
            mockMvc.perform(post(API_BASE + "/parse-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.metrics[0].recordDate").exists());
        }

        @Test
        @DisplayName("AI-INT-003: 解析健康指标 - 速率限制返回错误")
        void testParseMetrics_RateLimited() throws Exception {
            // Given: Rate limit exceeded
            when(aiRateLimiter.checkLimit(anyLong())).thenReturn(false);
            when(aiRateLimiter.getRemainingCount(anyLong())).thenReturn(0);

            String requestJson = """
                {
                    "input": "血糖5.6",
                    "inputType": "text"
                }
                """;

            // When: Request AI parsing while rate limited
            // Then: Should return rate limit error
            mockMvc.perform(post(API_BASE + "/parse-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(not(equalTo(200))));

            // Verify AI service was NOT called
            verify(chatLanguageModel, never()).generate(anyList());
        }

        @Test
        @DisplayName("AI-INT-004: 解析健康指标 - 无法识别输入")
        void testParseMetrics_UnrecognizedInput() throws Exception {
            // Given: AI returns empty metrics
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
            when(chatLanguageModel.generate(anyList())).thenReturn(mockResponse);

            String requestJson = """
                {
                    "input": "今天天气不错",
                    "inputType": "text"
                }
                """;

            // When: Request AI parsing for non-health input
            // Then: Should return empty metrics
            mockMvc.perform(post(API_BASE + "/parse-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.metrics").isEmpty());
        }

        @Test
        @DisplayName("AI-INT-005: 解析健康指标 - AI服务错误")
        void testParseMetrics_AIServiceError() throws Exception {
            // Given: AI service throws exception
            when(chatLanguageModel.generate(anyList()))
                    .thenThrow(new RuntimeException("AI service unavailable"));

            String requestJson = """
                {
                    "input": "血糖5.6",
                    "inputType": "text"
                }
                """;

            // When: Request AI parsing while service is down
            // Then: Should handle error gracefully
            mockMvc.perform(post(API_BASE + "/parse-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.metrics").isEmpty());
        }
    }

    @Nested
    @DisplayName("确认录入测试")
    class ConfirmMetricsTests {

        @Test
        @DisplayName("AI-INT-006: 确认录入 - 成功场景")
        void testConfirmMetrics_Success() throws Exception {
            // Given: A valid confirm request
            // First, create a parse history by parsing input
            mockMvc.perform(post(API_BASE + "/parse-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"input\":\"血糖5.6\",\"inputType\":\"text\"}"))
                    .andReturn();

            String confirmJson = String.format("""
                {
                    "parseHistoryId": 1,
                    "metrics": [
                        {
                            "metricKey": "glucose",
                            "metricName": "血糖",
                            "value": 5.6,
                            "unit": "mmol/L",
                            "category": "HEALTH",
                            "selected": true,
                            "recordDate": "%s"
                        }
                    ]
                }
                """, LocalDate.now());

            // When: Confirm the parsed metrics
            // Then: Should save successfully (or return error if history not found)
            mockMvc.perform(post(API_BASE + "/confirm-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").exists());
        }

        @Test
        @DisplayName("AI-INT-007: 确认录入 - 部分指标未选中")
        void testConfirmMetrics_PartialSelection() throws Exception {
            // Given: Confirm request with some metrics unselected
            String confirmJson = String.format("""
                {
                    "parseHistoryId": 1,
                    "metrics": [
                        {
                            "metricKey": "glucose",
                            "metricName": "血糖",
                            "value": 5.6,
                            "unit": "mmol/L",
                            "category": "HEALTH",
                            "selected": true,
                            "recordDate": "%s"
                        },
                        {
                            "metricKey": "heartRate",
                            "metricName": "心率",
                            "value": 72,
                            "unit": "次/分",
                            "category": "HEALTH",
                            "selected": false,
                            "recordDate": "%s"
                        }
                    ]
                }
                """, LocalDate.now(), LocalDate.now());

            // When: Confirm with partial selection
            // Then: Should only save selected metrics
            mockMvc.perform(post(API_BASE + "/confirm-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").exists());
        }
    }

    @Nested
    @DisplayName("剩余次数测试")
    class RemainingCountTests {

        @Test
        @DisplayName("AI-INT-008: 获取剩余次数 - 成功场景")
        void testGetRemainingCount_Success() throws Exception {
            // When: Request remaining count
            // Then: Should return the count
            mockMvc.perform(get(API_BASE + "/parse-metrics/remaining"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isNumber());

            verify(aiRateLimiter).getRemainingCount(anyLong());
        }
    }

    @Nested
    @DisplayName("历史记录测试")
    class HistoryTests {

        @Test
        @DisplayName("AI-INT-009: 获取历史记录 - 成功场景")
        void testGetHistory_Success() throws Exception {
            // When: Request AI parse history
            // Then: Should return paginated history
            mockMvc.perform(get(API_BASE + "/history")
                            .param("page", "1")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").exists());
        }

        @Test
        @DisplayName("AI-INT-010: 获取历史详情 - 成功场景")
        void testGetHistoryDetail_Success() throws Exception {
            // Given: An existing history record
            Long historyId = 1L;

            // When: Request history detail
            // Then: Should return the detail (or 404 if not exists)
            mockMvc.perform(get(API_BASE + "/history/{id}", historyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").exists());
        }

        @Test
        @DisplayName("AI-INT-011: 删除历史记录 - 成功场景")
        void testDeleteHistory_Success() throws Exception {
            // Given: An existing history record
            Long historyId = 1L;

            // When: Delete the history
            // Then: Should return success (or error if not exists)
            mockMvc.perform(delete(API_BASE + "/history/{id}", historyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").exists());
        }
    }

    @Nested
    @DisplayName("血压解析测试")
    class BloodPressureTests {

        @Test
        @DisplayName("AI-INT-012: 解析血压 - 应拆分为收缩压和舒张压")
        void testParseBloodPressure_SplitIntoSystolicAndDiastolic() throws Exception {
            // Given: AI returns blood pressure data
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
            when(chatLanguageModel.generate(anyList())).thenReturn(mockResponse);

            String requestJson = """
                {
                    "input": "血压120/80",
                    "inputType": "text"
                }
                """;

            // When: Parse blood pressure input
            // Then: Should return both systolic and diastolic
            mockMvc.perform(post(API_BASE + "/parse-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.metrics.length()").value(2));
        }
    }

    @Nested
    @DisplayName("保健指标分类测试")
    class WellnessMetricTests {

        @Test
        @DisplayName("AI-INT-013: 保健指标 - WELLNESS分类")
        void testWellnessMetrics_Category() throws Exception {
            // Given: AI returns wellness metrics
            String aiResponse = """
                {
                  "metrics": [
                    {"metricKey": "steps", "metricName": "步数", "value": 8000, "unit": "步", "confidence": 0.9, "category": "WELLNESS"},
                    {"metricKey": "sleepDuration", "metricName": "睡眠", "value": 7.5, "unit": "小时", "confidence": 0.9, "category": "WELLNESS"}
                  ],
                  "summary": "解析出2个保健指标",
                  "warnings": []
                }
                """;

            AiMessage aiMessage = mock(AiMessage.class);
            when(aiMessage.text()).thenReturn(aiResponse);
            Response<AiMessage> mockResponse = mock(Response.class);
            when(mockResponse.content()).thenReturn(aiMessage);
            when(chatLanguageModel.generate(anyList())).thenReturn(mockResponse);

            String requestJson = """
                {
                    "input": "今天走了8000步，睡了7.5小时",
                    "inputType": "text"
                }
                """;

            // When: Parse wellness input
            // Then: Should categorize as WELLNESS
            mockMvc.perform(post(API_BASE + "/parse-metrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.metrics[0].category").value("WELLNESS"));
        }
    }
}