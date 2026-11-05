package com.hhs.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hhs.component.BaiduOcrClient;
import com.hhs.dto.ConfirmMetricsRequest;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.MetricParseRequest;
import com.hhs.dto.OcrConfirmRequest;
import com.hhs.dto.WellnessMetricRequest;
import com.hhs.entity.AiParseHistory;
import com.hhs.entity.OcrHealthRecord;
import com.hhs.mapper.AiParseHistoryMapper;
import com.hhs.mapper.ExaminationReportMapper;
import com.hhs.mapper.LabResultMapper;
import com.hhs.mapper.OcrHealthRecordMapper;
import com.hhs.mapper.OcrRecognizeLogMapper;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.LabRoutineMetricRegistry;
import com.hhs.service.domain.MetricDefinition;
import com.hhs.service.domain.MetricKeyNormalizer;
import com.hhs.service.impl.AiParseServiceImpl;
import com.hhs.service.impl.OcrServiceImpl;
import com.hhs.vo.MetricParseResultVO;
import com.hhs.vo.OcrHealthResultVO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Cross-channel consistency integration test suite.
 * Verifies that Manual Entry, AI Text Parsing, and OCR Image Recognition produce
 * 100% equivalent metric canonical keys, standard units, split blood pressure values,
 * and unified four-tier physiological validation boundaries (per D-01, D-02, D-03, D-04).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("跨渠道指标一致性自动化测试套件 (CrossChannelConsistencyTest)")
public class CrossChannelConsistencyTest {

    private static Validator validator;
    private static ObjectMapper objectMapper;

    @Mock
    private AiParseHistoryMapper aiParseHistoryMapper;

    @Mock
    private HealthMetricService healthMetricService;

    @Mock
    private WellnessService wellnessService;

    @Mock
    private ChatLanguageModel chatModel;

    @Mock
    private ExaminationReportMapper examinationReportMapper;

    @Mock
    private LabResultMapper labResultMapper;

    @Mock
    private OcrRecognizeLogMapper ocrRecognizeLogMapper;

    @Mock
    private OcrHealthRecordMapper ocrHealthRecordMapper;

    @Mock
    private BaiduOcrClient baiduOcrClient;

    @Mock
    private ReportStatusService reportStatusService;

    @TempDir
    private Path tempUploadDir;

    private HealthMetricMetaRegistry metaRegistry;
    private LabRoutineMetricRegistry labRegistry;

    private AiParseServiceImpl aiParseService;
    private OcrServiceImpl ocrService;

    private final Long testUserId = 1001L;

    @BeforeAll
    static void initGlobal() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @BeforeEach
    void setUp() {
        metaRegistry = new HealthMetricMetaRegistry();
        metaRegistry.init();

        labRegistry = new LabRoutineMetricRegistry();

        aiParseService = new AiParseServiceImpl(
                aiParseHistoryMapper,
                healthMetricService,
                wellnessService,
                objectMapper,
                chatModel,
                metaRegistry
        );

        ocrService = new OcrServiceImpl(
                examinationReportMapper,
                labResultMapper,
                ocrRecognizeLogMapper,
                ocrHealthRecordMapper,
                baiduOcrClient,
                reportStatusService,
                healthMetricService,
                wellnessService,
                objectMapper,
                metaRegistry,
                labRegistry
        );

        ReflectionTestUtils.setField(ocrService, "uploadPath", tempUploadDir.toString());

        when(aiParseHistoryMapper.insert(any(AiParseHistory.class))).thenAnswer(inv -> {
            AiParseHistory h = inv.getArgument(0);
            h.setId(501L);
            return 1;
        });

        when(ocrHealthRecordMapper.insert(any(OcrHealthRecord.class))).thenAnswer(inv -> {
            OcrHealthRecord r = inv.getArgument(0);
            r.setId(601L);
            return 1;
        });
    }

    private MetricParseRequest createParseRequest(String input) {
        MetricParseRequest req = new MetricParseRequest();
        req.setInput(input);
        req.setInputType("text");
        req.setRecordDate(LocalDate.now());
        return req;
    }

    @Test
    @DisplayName("测试1: 常见生命体征跨渠道 (Manual, AI, OCR) Canonical Key 100% 对齐断言")
    void testCrossChannelCanonicalKeyNormalization() throws Exception {
        // 1.1 手动录入渠道 (HealthMetricRequest 反序列化)
        String jsonPayload = """
            [
              {"metricKey": "glucose", "value": 5.6, "recordDate": "2026-11-05"},
              {"metricKey": "pulse", "value": 72, "recordDate": "2026-11-05"},
              {"metricKey": "sbp", "value": 120, "recordDate": "2026-11-05"},
              {"metricKey": "dbp", "value": 80, "recordDate": "2026-11-05"},
              {"metricKey": "temperature", "value": 36.5, "recordDate": "2026-11-05"},
              {"metricKey": "spo2", "value": 98, "recordDate": "2026-11-05"}
            ]
            """;
        List<HealthMetricRequest> manualRequests = objectMapper.readValue(
                jsonPayload,
                objectMapper.getTypeFactory().constructCollectionType(List.class, HealthMetricRequest.class)
        );

        Set<String> manualCanonicalKeys = manualRequests.stream()
                .map(HealthMetricRequest::getMetricKey)
                .collect(Collectors.toSet());

        // 1.2 AI 对话解析渠道 (模拟 LLM 返回未归一化的别名 Key)
        String aiMockResponse = """
            {
              "metrics": [
                {"metricKey": "glucose", "metricName": "血糖", "value": 5.6, "unit": "mmol/L", "confidence": 0.95, "category": "HEALTH"},
                {"metricKey": "pulse", "metricName": "心率", "value": 72, "unit": "次/分", "confidence": 0.95, "category": "HEALTH"},
                {"metricKey": "sbp", "metricName": "收缩压", "value": 120, "unit": "mmHg", "confidence": 0.95, "category": "HEALTH"},
                {"metricKey": "dbp", "metricName": "舒张压", "value": 80, "unit": "mmHg", "confidence": 0.95, "category": "HEALTH"},
                {"metricKey": "temperature", "metricName": "体温", "value": 36.5, "unit": "°C", "confidence": 0.95, "category": "HEALTH"},
                {"metricKey": "spo2", "metricName": "血氧", "value": 98, "unit": "%", "confidence": 0.95, "category": "HEALTH"}
              ],
              "summary": "解析出6项生命体征",
              "warnings": []
            }
            """;
        when(chatModel.generate(anyList())).thenReturn(Response.from(AiMessage.from(aiMockResponse)));

        MetricParseResultVO aiResult = aiParseService.parseMetrics(
                testUserId,
                createParseRequest("血糖5.6，心率72，收缩压120，舒张压80，体温36.5，血氧98")
        );

        Set<String> aiCanonicalKeys = aiResult.getMetrics().stream()
                .map(MetricParseResultVO.ParsedMetric::getMetricKey)
                .collect(Collectors.toSet());

        // 1.3 OCR 图像识别渠道 (根据中文名称匹配)
        String ocrMockText = "血糖 5.6 mmol/L\n心率 72 次/分\n收缩压 120 mmHg\n舒张压 80 mmHg\n体温 36.5 °C\n血氧 98 %";
        when(baiduOcrClient.recognize(any())).thenReturn(Optional.of(ocrMockText));

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.jpg", "image/jpeg", "dummy image binary".getBytes()
        );
        OcrHealthResultVO ocrResult = ocrService.recognizeReport(testUserId, file);

        Set<String> ocrCanonicalKeys = ocrResult.getMetrics().stream()
                .map(OcrHealthResultVO.RecognizedMetric::getMetricKey)
                .collect(Collectors.toSet());

        // 1.4 三渠道 100% 对齐校验
        Set<String> expectedCanonicalKeys = Set.of(
                "bloodGlucose", "heartRate", "systolicBP", "diastolicBP", "bodyTemperature", "bloodOxygen"
        );

        assertEquals(expectedCanonicalKeys, manualCanonicalKeys, "手动录入反序列化必须全部归一化为权威小驼峰 Key");
        assertEquals(expectedCanonicalKeys, aiCanonicalKeys, "AI 解析服务必须全部归一化为权威小驼峰 Key");
        assertEquals(expectedCanonicalKeys, ocrCanonicalKeys, "OCR 识别服务必须全部归一化为权威小驼峰 Key");
        assertEquals(aiCanonicalKeys, ocrCanonicalKeys, "AI 与 OCR 提取的生命体征 Key 必须 100% 相同");
    }

    @Test
    @DisplayName("测试2: 核心指标与血常规扩展指标的标准单位 100% 严格对齐断言")
    void testCrossChannelUnitAlignment() throws Exception {
        // 2.1 校验不可变元数据注册表的核心单位规范
        assertEquals("次/分", metaRegistry.getDefinition("heartRate").orElseThrow().unit());
        assertEquals("mmol/L", metaRegistry.getDefinition("bloodGlucose").orElseThrow().unit());
        assertEquals("mmHg", metaRegistry.getDefinition("systolicBP").orElseThrow().unit());
        assertEquals("mmHg", metaRegistry.getDefinition("diastolicBP").orElseThrow().unit());
        assertEquals("°C", metaRegistry.getDefinition("bodyTemperature").orElseThrow().unit());
        assertEquals("%", metaRegistry.getDefinition("bloodOxygen").orElseThrow().unit());
        assertEquals("kg", metaRegistry.getDefinition("weight").orElseThrow().unit());
        assertEquals("kg/m²", metaRegistry.getDefinition("bmi").orElseThrow().unit());
        assertEquals("小时", metaRegistry.getDefinition("sleepDuration").orElseThrow().unit());
        assertEquals("步", metaRegistry.getDefinition("steps").orElseThrow().unit());
        assertEquals("ml", metaRegistry.getDefinition("waterIntake").orElseThrow().unit());

        // 2.2 校验化验单血常规专用指标单位规范
        assertEquals("g/L", labRegistry.getDefinition("hemoglobin").orElseThrow().unit());
        assertEquals("10^9/L", labRegistry.getDefinition("wbc").orElseThrow().unit());
        assertEquals("10^12/L", labRegistry.getDefinition("rbc").orElseThrow().unit());
        assertEquals("10^9/L", labRegistry.getDefinition("platelet").orElseThrow().unit());

        // 2.3 验证 AI 动态系统提示词中包含的标准单位与注册表 100% 一致
        String systemPrompt = aiParseService.getCachedSystemPrompt();
        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("| heartRate | 次/分 |"));
        assertTrue(systemPrompt.contains("| bloodGlucose | mmol/L |"));
        assertTrue(systemPrompt.contains("| systolicBP | mmHg |"));
        assertTrue(systemPrompt.contains("| diastolicBP | mmHg |"));
        assertTrue(systemPrompt.contains("| bodyTemperature | °C |"));
        assertTrue(systemPrompt.contains("| sleepDuration | 小时 |"));
        assertTrue(systemPrompt.contains("| waterIntake | ml |"));

        // 2.4 验证 OCR 缺失单位时的默认补全与注册表 100% 一致
        String ocrWithoutUnits = "心率 75\n血糖 5.8\n收缩压 125\n舒张压 82";
        when(baiduOcrClient.recognize(any())).thenReturn(Optional.of(ocrWithoutUnits));

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.jpg", "image/jpeg", "dummy".getBytes()
        );
        OcrHealthResultVO ocrResult = ocrService.recognizeReport(testUserId, file);

        for (OcrHealthResultVO.RecognizedMetric m : ocrResult.getMetrics()) {
            MetricDefinition def = metaRegistry.getDefinition(m.getMetricKey()).orElse(null);
            assertNotNull(def, "OCR 返回指标必须能从注册表获取定义");
            assertEquals(def.unit(), m.getUnit(), "OCR 默认单位必须与元数据注册表严格对齐: " + m.getMetricKey());
        }
    }

    @Test
    @DisplayName("测试3: 血压拆分协同与杜绝非法的单一 bloodPressure 混合 Key")
    void testBloodPressureSplittingConsistency() throws Exception {
        // 3.1 AI 渠道: 输入复合血压（如 120/80），必须拆分为 systolicBP 与 diastolicBP，严禁保留 bloodPressure
        String aiResponse = """
            {
              "metrics": [
                {
                  "metricKey": "bloodPressure",
                  "metricName": "血压",
                  "value": "120/80",
                  "unit": "mmHg",
                  "confidence": 0.95,
                  "category": "HEALTH"
                }
              ],
              "summary": "血压120/80",
              "warnings": []
            }
            """;
        when(chatModel.generate(anyList())).thenReturn(Response.from(AiMessage.from(aiResponse)));

        MetricParseResultVO aiResult = aiParseService.parseMetrics(
                testUserId,
                createParseRequest("血压120/80")
        );

        assertEquals(2, aiResult.getMetrics().size(), "复合血压必须拆分为两个独立指标");
        assertTrue(aiResult.getMetrics().stream().anyMatch(m -> m.getMetricKey().equals("systolicBP") && m.getValue().compareTo(new BigDecimal("120")) == 0));
        assertTrue(aiResult.getMetrics().stream().anyMatch(m -> m.getMetricKey().equals("diastolicBP") && m.getValue().compareTo(new BigDecimal("80")) == 0));
        assertTrue(aiResult.getMetrics().stream().noneMatch(m -> m.getMetricKey().equalsIgnoreCase("bloodPressure")), "结果中绝对不能包含单一 bloodPressure key");

        // 3.2 AI 渠道: 不完整单数值血压（如 120），必须拦截并提示，不生成不完整血压指标
        String incompleteAiResponse = """
            {
              "metrics": [
                {
                  "metricKey": "bloodPressure",
                  "metricName": "血压",
                  "value": 120,
                  "unit": "mmHg",
                  "confidence": 0.5,
                  "category": "HEALTH"
                }
              ],
              "summary": "血压120",
              "warnings": []
            }
            """;
        when(chatModel.generate(anyList())).thenReturn(Response.from(AiMessage.from(incompleteAiResponse)));

        MetricParseResultVO incompleteResult = aiParseService.parseMetrics(
                testUserId,
                createParseRequest("血压120")
        );

        assertTrue(incompleteResult.getMetrics().isEmpty(), "单数值血压不能生成任何指标");
        assertTrue(incompleteResult.getWarnings().stream().anyMatch(w -> w.contains("血压数据不完整")), "必须输出友好格式说明警告");

        // 3.3 OCR 渠道: 复合血压文本必须拆分为收缩压与舒张压
        String ocrBpText = "血压 135/85 mmHg";
        when(baiduOcrClient.recognize(any())).thenReturn(Optional.of(ocrBpText));

        MockMultipartFile file = new MockMultipartFile(
                "file", "bp.jpg", "image/jpeg", "dummy".getBytes()
        );
        OcrHealthResultVO ocrBpResult = ocrService.recognizeReport(testUserId, file);

        assertEquals(2, ocrBpResult.getMetrics().size());
        assertTrue(ocrBpResult.getMetrics().stream().anyMatch(m -> m.getMetricKey().equals("systolicBP") && m.getValue().compareTo(new BigDecimal("135")) == 0));
        assertTrue(ocrBpResult.getMetrics().stream().anyMatch(m -> m.getMetricKey().equals("diastolicBP") && m.getValue().compareTo(new BigDecimal("85")) == 0));
        assertTrue(ocrBpResult.getMetrics().stream().noneMatch(m -> m.getMetricKey().equalsIgnoreCase("bloodPressure")));
    }

    @Test
    @DisplayName("测试4: 病理急重症数值放行与物理极限不可能数值在全部 DTO 均被拦截")
    void testPathologicalAndPhysicalLimitConsistencyAcrossAllDTOs() {
        // 4.1 病理急危重症数据 (Tier 2/3: 临床存在但异常，必须全渠道放行且不得因强校验被破坏)
        // 严重高血压收缩压 195 mmHg
        HealthMetricRequest severeSbp = new HealthMetricRequest();
        severeSbp.setMetricKey("systolicBP");
        severeSbp.setValue(new BigDecimal("195"));
        severeSbp.setRecordDate(LocalDate.now());
        assertTrue(validator.validate(severeSbp).isEmpty(), "收缩压 195 mmHg 必须放行");

        // 严重高血压舒张压 125 mmHg
        HealthMetricRequest severeDbp = new HealthMetricRequest();
        severeDbp.setMetricKey("diastolicBP");
        severeDbp.setValue(new BigDecimal("125"));
        severeDbp.setRecordDate(LocalDate.now());
        assertTrue(validator.validate(severeDbp).isEmpty(), "舒张压 125 mmHg 必须放行");

        // 严重心动过速 140 bpm
        ConfirmMetricsRequest.MetricItem severeHr = new ConfirmMetricsRequest.MetricItem();
        severeHr.setMetricKey("heartRate");
        severeHr.setValue(new BigDecimal("140"));
        assertTrue(validator.validate(severeHr).isEmpty(), "心率 140 bpm 必须放行");

        // 严重高血糖 18.0 mmol/L (酮症酸中毒风险)
        OcrConfirmRequest.MetricItem severeGlucose = new OcrConfirmRequest.MetricItem();
        severeGlucose.setMetricKey("bloodGlucose");
        severeGlucose.setValue(new BigDecimal("18.0"));
        assertTrue(validator.validate(severeGlucose).isEmpty(), "血糖 18.0 mmol/L 必须放行");

        // 高烧 39.8 °C
        HealthMetricRequest highFever = new HealthMetricRequest();
        highFever.setMetricKey("bodyTemperature");
        highFever.setValue(new BigDecimal("39.8"));
        highFever.setRecordDate(LocalDate.now());
        assertTrue(validator.validate(highFever).isEmpty(), "体温 39.8 °C 必须放行");

        // 4.2 物理不可能极限数值 (Tier 4: 违反生理学界限，必须在全部入库 DTO 100% 拦截)
        // 4.2.1 心率 450 bpm (界限: 20 ~ 300 次/分)
        HealthMetricRequest badHr1 = new HealthMetricRequest();
        badHr1.setMetricKey("heartRate");
        badHr1.setValue(new BigDecimal("450"));
        badHr1.setRecordDate(LocalDate.now());
        Set<ConstraintViolation<HealthMetricRequest>> v1 = validator.validate(badHr1);
        assertFalse(v1.isEmpty());
        assertEquals("心率有效生理区间为 20 ~ 300 次/分，请核对后重新输入", v1.iterator().next().getMessage());

        ConfirmMetricsRequest.MetricItem badHr2 = new ConfirmMetricsRequest.MetricItem();
        badHr2.setMetricKey("heartRate");
        badHr2.setValue(new BigDecimal("450"));
        Set<ConstraintViolation<ConfirmMetricsRequest.MetricItem>> v2 = validator.validate(badHr2);
        assertFalse(v2.isEmpty());
        assertEquals("心率有效生理区间为 20 ~ 300 次/分，请核对后重新输入", v2.iterator().next().getMessage());

        OcrConfirmRequest.MetricItem badHr3 = new OcrConfirmRequest.MetricItem();
        badHr3.setMetricKey("heartRate");
        badHr3.setValue(new BigDecimal("450"));
        Set<ConstraintViolation<OcrConfirmRequest.MetricItem>> v3 = validator.validate(badHr3);
        assertFalse(v3.isEmpty());
        assertEquals("心率有效生理区间为 20 ~ 300 次/分，请核对后重新输入", v3.iterator().next().getMessage());

        // 4.2.2 体温 55.0 °C (界限: 34 ~ 42.5 °C)
        HealthMetricRequest badTemp1 = new HealthMetricRequest();
        badTemp1.setMetricKey("bodyTemperature");
        badTemp1.setValue(new BigDecimal("55.0"));
        badTemp1.setRecordDate(LocalDate.now());
        Set<ConstraintViolation<HealthMetricRequest>> vt1 = validator.validate(badTemp1);
        assertFalse(vt1.isEmpty());
        assertEquals("体温有效生理区间为 34 ~ 42.5 °C，请核对后重新输入", vt1.iterator().next().getMessage());

        ConfirmMetricsRequest.MetricItem badTemp2 = new ConfirmMetricsRequest.MetricItem();
        badTemp2.setMetricKey("bodyTemperature");
        badTemp2.setValue(new BigDecimal("55.0"));
        Set<ConstraintViolation<ConfirmMetricsRequest.MetricItem>> vt2 = validator.validate(badTemp2);
        assertFalse(vt2.isEmpty());
        assertEquals("体温有效生理区间为 34 ~ 42.5 °C，请核对后重新输入", vt2.iterator().next().getMessage());

        // 4.2.3 收缩压 350 mmHg (界限: 40 ~ 300 mmHg)
        HealthMetricRequest badSbp1 = new HealthMetricRequest();
        badSbp1.setMetricKey("systolicBP");
        badSbp1.setValue(new BigDecimal("350"));
        badSbp1.setRecordDate(LocalDate.now());
        Set<ConstraintViolation<HealthMetricRequest>> vs1 = validator.validate(badSbp1);
        assertFalse(vs1.isEmpty());
        assertEquals("收缩压有效生理区间为 40 ~ 300 mmHg，请核对后重新输入", vs1.iterator().next().getMessage());

        OcrConfirmRequest.MetricItem badSbp2 = new OcrConfirmRequest.MetricItem();
        badSbp2.setMetricKey("systolicBP");
        badSbp2.setValue(new BigDecimal("350"));
        Set<ConstraintViolation<OcrConfirmRequest.MetricItem>> vs2 = validator.validate(badSbp2);
        assertFalse(vs2.isEmpty());
        assertEquals("收缩压有效生理区间为 40 ~ 300 mmHg，请核对后重新输入", vs2.iterator().next().getMessage());

        // 4.2.4 保健指标物理极限拦截 (睡眠 28 小时，界限: 0 ~ 24 小时)
        WellnessMetricRequest badSleep = new WellnessMetricRequest();
        badSleep.setMetricKey("sleepDuration");
        badSleep.setValue(new BigDecimal("28.0"));
        badSleep.setRecordDate(LocalDate.now());
        Set<ConstraintViolation<WellnessMetricRequest>> vSleep = validator.validate(badSleep);
        assertFalse(vSleep.isEmpty());
        assertEquals("睡眠时长有效生理区间为 0 ~ 24 小时，请核对后重新输入", vSleep.iterator().next().getMessage());

        // 4.2.5 饮水量 25000 ml (界限: 0 ~ 20000 ml)
        WellnessMetricRequest badWater = new WellnessMetricRequest();
        badWater.setMetricKey("waterIntake");
        badWater.setValue(new BigDecimal("25000"));
        badWater.setRecordDate(LocalDate.now());
        Set<ConstraintViolation<WellnessMetricRequest>> vWater = validator.validate(badWater);
        assertFalse(vWater.isEmpty());
        assertEquals("饮水量有效生理区间为 0 ~ 20000 ml，请核对后重新输入", vWater.iterator().next().getMessage());
    }

    @Test
    @DisplayName("测试5: 化验单血常规扩展指标独立性与隔离断言")
    void testLabRoutineExtensionMetricIsolation() throws Exception {
        // 5.1 验证血常规专有指标由 LabRoutineMetricRegistry 正确提供
        assertTrue(labRegistry.contains("hemoglobin"));
        assertTrue(labRegistry.contains("wbc"));
        assertTrue(labRegistry.contains("rbc"));
        assertTrue(labRegistry.contains("platelet"));

        // 别名支持
        assertTrue(labRegistry.contains("血红蛋白"));
        assertTrue(labRegistry.contains("白细胞计数"));
        assertTrue(labRegistry.contains("RBC"));
        assertTrue(labRegistry.contains("PLT"));

        // 5.2 验证日常指标注册中心 HealthMetricMetaRegistry 保持纯净，未受污染
        assertFalse(metaRegistry.getAllDefinitions().containsKey("hemoglobin"), "核心健康指标库严禁包含化验单专有血红蛋白");
        assertFalse(metaRegistry.getAllDefinitions().containsKey("wbc"), "核心健康指标库严禁包含白细胞");
        assertFalse(metaRegistry.getAllDefinitions().containsKey("rbc"), "核心健康指标库严禁包含红细胞");
        assertFalse(metaRegistry.getAllDefinitions().containsKey("platelet"), "核心健康指标库严禁包含血小板");

        // 5.3 验证 OCR 正确识别化验单血常规指标
        String cbcReport = "血红蛋白 145 g/L\n白细胞计数 6.5 10^9/L\n红细胞计数 4.8 10^12/L\n血小板计数 220 10^9/L";
        when(baiduOcrClient.recognize(any())).thenReturn(Optional.of(cbcReport));

        MockMultipartFile file = new MockMultipartFile(
                "file", "cbc.jpg", "image/jpeg", "dummy".getBytes()
        );
        OcrHealthResultVO cbcResult = ocrService.recognizeReport(testUserId, file);

        assertEquals(4, cbcResult.getMetrics().size());
        assertTrue(cbcResult.getMetrics().stream().anyMatch(m -> m.getMetricKey().equals("hemoglobin") && m.getUnit().equals("g/L")));
        assertTrue(cbcResult.getMetrics().stream().anyMatch(m -> m.getMetricKey().equals("wbc") && m.getUnit().equals("10^9/L")));
        assertTrue(cbcResult.getMetrics().stream().anyMatch(m -> m.getMetricKey().equals("rbc") && m.getUnit().equals("10^12/L")));
        assertTrue(cbcResult.getMetrics().stream().anyMatch(m -> m.getMetricKey().equals("platelet") && m.getUnit().equals("10^9/L")));

        // 5.4 验证化验单指标也受四梯度物理极限约束
        OcrConfirmRequest.MetricItem normalHb = new OcrConfirmRequest.MetricItem();
        normalHb.setMetricKey("hemoglobin");
        normalHb.setValue(new BigDecimal("145"));
        assertTrue(validator.validate(normalHb).isEmpty());

        OcrConfirmRequest.MetricItem impossibleHb = new OcrConfirmRequest.MetricItem();
        impossibleHb.setMetricKey("hemoglobin");
        impossibleHb.setValue(new BigDecimal("350"));
        Set<ConstraintViolation<OcrConfirmRequest.MetricItem>> hbViolations = validator.validate(impossibleHb);
        assertFalse(hbViolations.isEmpty());
        assertEquals("血红蛋白有效生理区间为 20 ~ 300 g/L，请核对后重新输入", hbViolations.iterator().next().getMessage());
    }
}
