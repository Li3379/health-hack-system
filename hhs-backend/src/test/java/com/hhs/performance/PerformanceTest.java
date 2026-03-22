package com.hhs.performance;

import com.hhs.component.BaiduOcrClient;
import com.hhs.config.TestSecurityConfig;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.MetricParseRequest;
import com.hhs.mapper.*;
import com.hhs.service.*;
import com.hhs.vo.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Performance Test Suite for HHS Backend
 *
 * <p>Benchmarks key operations with target performance thresholds:
 * <ul>
 *   <li>OCR recognition: < 5 seconds</li>
 *   <li>AI parsing: < 3 seconds</li>
 *   <li>Quick input save: < 500ms</li>
 *   <li>Device sync: < 10 seconds</li>
 *   <li>Statistics query: < 200ms</li>
 * </ul>
 *
 * @author HHS Team
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("Performance Test Suite")
@Tag("integration")
@Tag("performance")
class PerformanceTest {

    // Performance targets (in milliseconds)
    private static final long OCR_TARGET_MS = 5000;
    private static final long AI_PARSE_TARGET_MS = 3000;
    private static final long QUICK_INPUT_TARGET_MS = 500;
    private static final long DEVICE_SYNC_TARGET_MS = 10000;
    private static final long STATS_QUERY_TARGET_MS = 200;

    // Number of iterations for averaging
    private static final int WARMUP_ITERATIONS = 2;
    private static final int MEASUREMENT_ITERATIONS = 5;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BaiduOcrClient baiduOcrClient;

    @MockBean
    private ChatLanguageModel chatModel;

    @Autowired
    private OcrService ocrService;

    @Autowired
    private AiParseService aiParseService;

    @Autowired
    private HealthMetricService healthMetricService;

    @Autowired
    private StatsService statsService;

    @Autowired
    private DeviceSyncOrchestrationService deviceSyncService;

    @Autowired
    private OcrHealthRecordMapper ocrHealthRecordMapper;

    @Autowired
    private AiParseHistoryMapper aiParseHistoryMapper;

    @Autowired
    private HealthMetricMapper healthMetricMapper;

    @Autowired
    private UserMapper userMapper;

    private static final Long TEST_USER_ID = 1L;
    private static final String API_BASE = "/api";

    // Store test results for reporting
    private static final List<TestResult> testResults = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // Setup mock OCR client
        when(baiduOcrClient.recognize(any(byte[].class)))
                .thenReturn(Optional.of("血糖 5.6 mmol/L\n心率 72 次/分\n血压 120/80 mmHg"));

        // Setup mock AI chat model
        AiMessage mockAiMessage = mock(AiMessage.class);
        when(mockAiMessage.text()).thenReturn("""
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
            """);
        Response<AiMessage> mockResponse = mock(Response.class);
        when(mockResponse.content()).thenReturn(mockAiMessage);
        when(chatModel.generate(anyList())).thenReturn(mockResponse);
    }

    // ========================================
    // OCR Recognition Performance Tests
    // ========================================

    @Nested
    @DisplayName("OCR Recognition Performance")
    @TestMethodOrder(OrderAnnotation.class)
    class OcrPerformanceTests {

        @Test
        @Order(1)
        @DisplayName("PERF-OCR-001: OCR recognition time should be under 5 seconds")
        void testOcrRecognitionPerformance() throws Exception {
            List<Long> measurements = new ArrayList<>();

            // Warmup iterations
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runOcrRecognition();
            }

            // Measurement iterations
            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                measurements.add(runOcrRecognition());
            }

            long avgTime = calculateAverage(measurements);
            long maxTime = Collections.max(measurements);

            testResults.add(new TestResult(
                "OCR Recognition",
                avgTime, maxTime, OCR_TARGET_MS,
                measurements
            ));

            System.out.printf("[OCR] Avg: %dms, Max: %dms, Target: %dms%n",
                avgTime, maxTime, OCR_TARGET_MS);

            assertTrue(avgTime < OCR_TARGET_MS,
                String.format("OCR recognition average time %dms exceeds target %dms", avgTime, OCR_TARGET_MS));
        }

        @Test
        @Order(2)
        @DisplayName("PERF-OCR-002: OCR with multiple metrics parsing")
        void testOcrMultiMetricPerformance() throws Exception {
            // Setup OCR to return many metrics
            when(baiduOcrClient.recognize(any(byte[].class)))
                    .thenReturn(Optional.of("""
                        血糖 5.6 mmol/L
                        心率 72 次/分
                        血压 120/80 mmHg
                        总胆固醇 4.2 mmol/L
                        甘油三酯 1.5 mmol/L
                        高密度脂蛋白 1.5 mmol/L
                        低密度脂蛋白 2.8 mmol/L
                        肌酐 85 μmol/L
                        尿酸 320 μmol/L
                        """));

            List<Long> measurements = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                measurements.add(runOcrRecognition());
            }

            long avgTime = calculateAverage(measurements);
            testResults.add(new TestResult(
                "OCR Multi-Metric Recognition",
                avgTime, Collections.max(measurements), OCR_TARGET_MS,
                measurements
            ));

            System.out.printf("[OCR Multi] Avg: %dms, Target: %dms%n", avgTime, OCR_TARGET_MS);
            assertTrue(avgTime < OCR_TARGET_MS, "Multi-metric OCR should still be under target");
        }

        private long runOcrRecognition() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file", "report.jpg", "image/jpeg",
                "test image content for performance test".getBytes()
            );

            long start = System.currentTimeMillis();

            mockMvc.perform(multipart(API_BASE + "/ocr/report")
                    .file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            return System.currentTimeMillis() - start;
        }
    }

    // ========================================
    // AI Parsing Performance Tests
    // ========================================

    @Nested
    @DisplayName("AI Parsing Performance")
    @TestMethodOrder(OrderAnnotation.class)
    class AiParsePerformanceTests {

        @Test
        @Order(1)
        @DisplayName("PERF-AI-001: AI parsing time should be under 3 seconds")
        void testAiParsingPerformance() throws Exception {
            List<Long> measurements = new ArrayList<>();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runAiParsing();
            }

            // Measurement
            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                measurements.add(runAiParsing());
            }

            long avgTime = calculateAverage(measurements);
            long maxTime = Collections.max(measurements);

            testResults.add(new TestResult(
                "AI Parsing",
                avgTime, maxTime, AI_PARSE_TARGET_MS,
                measurements
            ));

            System.out.printf("[AI Parse] Avg: %dms, Max: %dms, Target: %dms%n",
                avgTime, maxTime, AI_PARSE_TARGET_MS);

            assertTrue(avgTime < AI_PARSE_TARGET_MS,
                String.format("AI parsing average time %dms exceeds target %dms", avgTime, AI_PARSE_TARGET_MS));
        }

        @Test
        @Order(2)
        @DisplayName("PERF-AI-002: AI parsing with complex input")
        void testAiComplexInputPerformance() throws Exception {
            String complexInput = """
                今天早上测了血糖5.6，心率72，血压120/80，
                昨天晚上睡眠8小时，走了12000步，喝水2000毫升，
                还有一些指标：体重68kg，体温36.5度
                """;

            List<Long> measurements = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                long start = System.currentTimeMillis();

                MetricParseRequest request = new MetricParseRequest();
                request.setInput(complexInput);
                request.setRecordDate(LocalDate.now());

                aiParseService.parseMetrics(TEST_USER_ID, request);

                measurements.add(System.currentTimeMillis() - start);
            }

            long avgTime = calculateAverage(measurements);
            testResults.add(new TestResult(
                "AI Complex Parsing",
                avgTime, Collections.max(measurements), AI_PARSE_TARGET_MS,
                measurements
            ));

            System.out.printf("[AI Complex] Avg: %dms, Target: %dms%n", avgTime, AI_PARSE_TARGET_MS);
            assertTrue(avgTime < AI_PARSE_TARGET_MS, "Complex AI parsing should be under target");
        }

        private long runAiParsing() throws Exception {
            String jsonRequest = """
                {
                    "input": "血糖5.6 mmol/L，心率72次/分",
                    "recordDate": "%s"
                }
                """.formatted(LocalDate.now());

            long start = System.currentTimeMillis();

            mockMvc.perform(post(API_BASE + "/ai/parse-metrics")
                    .contentType("application/json")
                    .content(jsonRequest))
                    .andExpect(status().isOk());

            return System.currentTimeMillis() - start;
        }
    }

    // ========================================
    // Quick Input Save Performance Tests
    // ========================================

    @Nested
    @DisplayName("Quick Input Save Performance")
    @TestMethodOrder(OrderAnnotation.class)
    class QuickInputPerformanceTests {

        @Test
        @Order(1)
        @DisplayName("PERF-INPUT-001: Quick input save should be under 500ms")
        void testQuickInputSavePerformance() throws Exception {
            List<Long> measurements = new ArrayList<>();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runQuickInputSave();
            }

            // Measurement
            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                measurements.add(runQuickInputSave());
            }

            long avgTime = calculateAverage(measurements);
            long maxTime = Collections.max(measurements);

            testResults.add(new TestResult(
                "Quick Input Save",
                avgTime, maxTime, QUICK_INPUT_TARGET_MS,
                measurements
            ));

            System.out.printf("[Quick Input] Avg: %dms, Max: %dms, Target: %dms%n",
                avgTime, maxTime, QUICK_INPUT_TARGET_MS);

            assertTrue(avgTime < QUICK_INPUT_TARGET_MS,
                String.format("Quick input save average time %dms exceeds target %dms", avgTime, QUICK_INPUT_TARGET_MS));
        }

        @Test
        @Order(2)
        @DisplayName("PERF-INPUT-002: Batch metric save performance")
        void testBatchMetricSavePerformance() throws Exception {
            List<Long> measurements = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                long start = System.currentTimeMillis();

                // Simulate batch save of 5 metrics
                for (int j = 0; j < 5; j++) {
                    HealthMetricRequest request = new HealthMetricRequest();
                    request.setUserId(TEST_USER_ID);
                    request.setMetricKey("glucose");
                    request.setValue(new BigDecimal("5.6"));
                    request.setUnit("mmol/L");
                    request.setRecordDate(LocalDate.now());

                    healthMetricService.add(TEST_USER_ID, request);
                }

                measurements.add(System.currentTimeMillis() - start);
            }

            long avgTime = calculateAverage(measurements);
            long batchTargetMs = QUICK_INPUT_TARGET_MS * 3; // Allow 3x for batch

            testResults.add(new TestResult(
                "Batch Metric Save (5 items)",
                avgTime, Collections.max(measurements), batchTargetMs,
                measurements
            ));

            System.out.printf("[Batch Save] Avg: %dms, Target: %dms%n", avgTime, batchTargetMs);
            assertTrue(avgTime < batchTargetMs, "Batch save should be under adjusted target");
        }

        private long runQuickInputSave() throws Exception {
            String jsonRequest = """
                {
                    "metricKey": "glucose",
                    "value": 5.6,
                    "unit": "mmol/L",
                    "recordDate": "%s"
                }
                """.formatted(LocalDate.now());

            long start = System.currentTimeMillis();

            mockMvc.perform(post(API_BASE + "/metrics")
                    .contentType("application/json")
                    .content(jsonRequest))
                    .andExpect(status().isOk());

            return System.currentTimeMillis() - start;
        }
    }

    // ========================================
    // Statistics Query Performance Tests
    // ========================================

    @Nested
    @DisplayName("Statistics Query Performance")
    @TestMethodOrder(OrderAnnotation.class)
    class StatsQueryPerformanceTests {

        @Test
        @Order(1)
        @DisplayName("PERF-STATS-001: Statistics query should be under 200ms")
        void testStatsQueryPerformance() throws Exception {
            List<Long> measurements = new ArrayList<>();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runStatsQuery();
            }

            // Measurement
            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                measurements.add(runStatsQuery());
            }

            long avgTime = calculateAverage(measurements);
            long maxTime = Collections.max(measurements);

            testResults.add(new TestResult(
                "Statistics Query",
                avgTime, maxTime, STATS_QUERY_TARGET_MS,
                measurements
            ));

            System.out.printf("[Stats Query] Avg: %dms, Max: %dms, Target: %dms%n",
                avgTime, maxTime, STATS_QUERY_TARGET_MS);

            assertTrue(avgTime < STATS_QUERY_TARGET_MS,
                String.format("Statistics query average time %dms exceeds target %dms", avgTime, STATS_QUERY_TARGET_MS));
        }

        @Test
        @Order(2)
        @DisplayName("PERF-STATS-002: Today stats aggregation performance")
        void testTodayStatsPerformance() throws Exception {
            List<Long> measurements = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                long start = System.currentTimeMillis();

                TodayStatsVO stats = statsService.getTodayStats(TEST_USER_ID);

                measurements.add(System.currentTimeMillis() - start);
                assertNotNull(stats, "Stats should not be null");
            }

            long avgTime = calculateAverage(measurements);
            testResults.add(new TestResult(
                "Today Stats Aggregation",
                avgTime, Collections.max(measurements), STATS_QUERY_TARGET_MS,
                measurements
            ));

            System.out.printf("[Today Stats] Avg: %dms, Target: %dms%n", avgTime, STATS_QUERY_TARGET_MS);
            assertTrue(avgTime < STATS_QUERY_TARGET_MS, "Today stats should be under target");
        }

        private long runStatsQuery() throws Exception {
            long start = System.currentTimeMillis();

            mockMvc.perform(get(API_BASE + "/stats/today"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            return System.currentTimeMillis() - start;
        }
    }

    // ========================================
    // Device Sync Performance Tests
    // ========================================

    @Nested
    @DisplayName("Device Sync Performance")
    @TestMethodOrder(OrderAnnotation.class)
    class DeviceSyncPerformanceTests {

        @Test
        @Order(1)
        @DisplayName("PERF-SYNC-001: Device sync time should be under 10 seconds")
        void testDeviceSyncPerformance() throws Exception {
            List<Long> measurements = new ArrayList<>();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                runDeviceSync();
            }

            // Measurement
            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                measurements.add(runDeviceSync());
            }

            long avgTime = calculateAverage(measurements);
            long maxTime = Collections.max(measurements);

            testResults.add(new TestResult(
                "Device Sync",
                avgTime, maxTime, DEVICE_SYNC_TARGET_MS,
                measurements
            ));

            System.out.printf("[Device Sync] Avg: %dms, Max: %dms, Target: %dms%n",
                avgTime, maxTime, DEVICE_SYNC_TARGET_MS);

            assertTrue(avgTime < DEVICE_SYNC_TARGET_MS,
                String.format("Device sync average time %dms exceeds target %dms", avgTime, DEVICE_SYNC_TARGET_MS));
        }

        @Test
        @Order(2)
        @DisplayName("PERF-SYNC-002: Multi-platform sync performance")
        void testMultiPlatformSyncPerformance() throws Exception {
            List<Long> measurements = new ArrayList<>();

            for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
                long start = System.currentTimeMillis();

                List<SyncResultVO> results = deviceSyncService.syncAllPlatforms(TEST_USER_ID);

                measurements.add(System.currentTimeMillis() - start);
                assertNotNull(results, "Sync results should not be null");
            }

            long avgTime = calculateAverage(measurements);
            testResults.add(new TestResult(
                "Multi-Platform Sync",
                avgTime, Collections.max(measurements), DEVICE_SYNC_TARGET_MS,
                measurements
            ));

            System.out.printf("[Multi-Sync] Avg: %dms, Target: %dms%n", avgTime, DEVICE_SYNC_TARGET_MS);
            assertTrue(avgTime < DEVICE_SYNC_TARGET_MS, "Multi-platform sync should be under target");
        }

        private long runDeviceSync() throws Exception {
            long start = System.currentTimeMillis();

            mockMvc.perform(post(API_BASE + "/device/sync/huawei"))
                    .andExpect(status().isOk());

            return System.currentTimeMillis() - start;
        }
    }

    // ========================================
    // Combined Load Tests
    // ========================================

    @Nested
    @DisplayName("Combined Load Tests")
    @TestMethodOrder(OrderAnnotation.class)
    class CombinedLoadTests {

        @Test
        @Order(1)
        @DisplayName("PERF-LOAD-001: Concurrent user operations")
        void testConcurrentOperations() throws Exception {
            int concurrentUsers = 10;
            List<Thread> threads = new ArrayList<>();
            List<Long> times = Collections.synchronizedList(new ArrayList<>());
            List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < concurrentUsers; i++) {
                Thread t = new Thread(() -> {
                    try {
                        long start = System.currentTimeMillis();

                        // Simulate typical user flow
                        TodayStatsVO stats = statsService.getTodayStats(TEST_USER_ID);

                        times.add(System.currentTimeMillis() - start);
                    } catch (Exception e) {
                        errors.add(e);
                    }
                });
                threads.add(t);
            }

            // Start all threads
            for (Thread t : threads) {
                t.start();
            }

            // Wait for all threads
            for (Thread t : threads) {
                t.join(5000);
            }

            assertTrue(errors.isEmpty(), "No errors should occur during concurrent operations: " + errors);
            assertEquals(concurrentUsers, times.size(), "All operations should complete");

            long avgTime = calculateAverage(times);
            testResults.add(new TestResult(
                "Concurrent Operations (10 users)",
                avgTime, Collections.max(times), STATS_QUERY_TARGET_MS * 2,
                new ArrayList<>(times)
            ));

            System.out.printf("[Concurrent] Avg: %dms, Users: %d%n", avgTime, concurrentUsers);
        }
    }

    // ========================================
    // Summary Report
    // ========================================

    @AfterAll
    static void printSummaryReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PERFORMANCE TEST SUMMARY REPORT");
        System.out.println("=".repeat(80));
        System.out.printf("%-35s %10s %10s %10s %10s%n",
            "Test Name", "Avg (ms)", "Max (ms)", "Target", "Status");
        System.out.println("-".repeat(80));

        for (TestResult result : testResults) {
            String status = result.avgTime < result.targetMs ? "PASS" : "FAIL";
            System.out.printf("%-35s %10d %10d %10d %10s%n",
                result.testName, result.avgTime, result.maxTime, result.targetMs, status);
        }

        System.out.println("=".repeat(80));

        long passCount = testResults.stream()
            .filter(r -> r.avgTime < r.targetMs)
            .count();
        long failCount = testResults.size() - passCount;

        System.out.printf("Total: %d tests, %d passed, %d failed%n",
            testResults.size(), passCount, failCount);
        System.out.println("=".repeat(80));
    }

    // ========================================
    // Helper Methods
    // ========================================

    private long calculateAverage(List<Long> values) {
        return (long) values.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);
    }

    /**
     * Record to store test results
     */
    private static class TestResult {
        final String testName;
        final long avgTime;
        final long maxTime;
        final long targetMs;
        final List<Long> measurements;

        TestResult(String testName, long avgTime, long maxTime, long targetMs, List<Long> measurements) {
            this.testName = testName;
            this.avgTime = avgTime;
            this.maxTime = maxTime;
            this.targetMs = targetMs;
            this.measurements = new ArrayList<>(measurements);
        }
    }
}