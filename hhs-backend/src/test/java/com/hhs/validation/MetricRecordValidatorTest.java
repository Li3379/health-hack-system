package com.hhs.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hhs.dto.ConfirmMetricsRequest;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.OcrConfirmRequest;
import com.hhs.dto.WellnessMetricRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MetricRecordValidator} and {@link HealthMetricRequest} validation pipeline.
 * Tests physical limit enforcement, pathological value tolerance, and DTO key normalization.
 */
class MetricRecordValidatorTest {

    private static Validator validator;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private HealthMetricRequest createRequest(String metricKey, BigDecimal value) {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey(metricKey);
        request.setValue(value);
        request.setRecordDate(LocalDate.now());
        return request;
    }

    @Test
    @DisplayName("Normal physiological values should pass validation")
    void testNormalValuesPass() {
        // Heart rate: 75 bpm (physiological range: 20 ~ 300)
        HealthMetricRequest hrRequest = createRequest("heartRate", new BigDecimal("75"));
        Set<ConstraintViolation<HealthMetricRequest>> hrViolations = validator.validate(hrRequest);
        assertTrue(hrViolations.isEmpty(), "Normal heart rate should have no violations");

        // Body temperature: 36.5 °C (physiological range: 34.0 ~ 42.5)
        HealthMetricRequest tempRequest = createRequest("bodyTemperature", new BigDecimal("36.5"));
        Set<ConstraintViolation<HealthMetricRequest>> tempViolations = validator.validate(tempRequest);
        assertTrue(tempViolations.isEmpty(), "Normal temperature should have no violations");

        // Steps: 8000 steps (previously capped at 1000)
        HealthMetricRequest stepsRequest = createRequest("steps", new BigDecimal("8000"));
        Set<ConstraintViolation<HealthMetricRequest>> stepsViolations = validator.validate(stepsRequest);
        assertTrue(stepsViolations.isEmpty(), "8000 steps should pass validation without 1000 ceiling");

        // Water intake: 2000 ml
        HealthMetricRequest waterRequest = createRequest("waterIntake", new BigDecimal("2000"));
        Set<ConstraintViolation<HealthMetricRequest>> waterViolations = validator.validate(waterRequest);
        assertTrue(waterViolations.isEmpty(), "2000 ml water intake should pass validation");
    }

    @Test
    @DisplayName("Pathological abnormal clinical values must pass validation without rejection")
    void testPathologicalAbnormalValuesPass() {
        // Severe tachycardia: 140 bpm (pathological but physiologically possible)
        HealthMetricRequest tachyRequest = createRequest("heartRate", new BigDecimal("140"));
        Set<ConstraintViolation<HealthMetricRequest>> tachyViolations = validator.validate(tachyRequest);
        assertTrue(tachyViolations.isEmpty(), "Tachycardia 140 bpm should pass physiological validation");

        // Severe hypertension: SBP 195 mmHg (hypertensive crisis, physiologically possible)
        HealthMetricRequest sbpRequest = createRequest("systolicBP", new BigDecimal("195"));
        Set<ConstraintViolation<HealthMetricRequest>> sbpViolations = validator.validate(sbpRequest);
        assertTrue(sbpViolations.isEmpty(), "Hypertensive crisis 195 mmHg should pass physiological validation");

        // Diastolic hypertension: DBP 125 mmHg
        HealthMetricRequest dbpRequest = createRequest("diastolicBP", new BigDecimal("125"));
        Set<ConstraintViolation<HealthMetricRequest>> dbpViolations = validator.validate(dbpRequest);
        assertTrue(dbpViolations.isEmpty(), "Severe DBP 125 mmHg should pass physiological validation");

        // Severe hyperglycemia: 18.0 mmol/L (diabetic ketoacidosis risk, clinically possible)
        HealthMetricRequest glucoseRequest = createRequest("bloodGlucose", new BigDecimal("18.0"));
        Set<ConstraintViolation<HealthMetricRequest>> glucoseViolations = validator.validate(glucoseRequest);
        assertTrue(glucoseViolations.isEmpty(), "Severe hyperglycemia 18.0 mmol/L should pass physiological validation");

        // High fever: 39.8 °C
        HealthMetricRequest feverRequest = createRequest("bodyTemperature", new BigDecimal("39.8"));
        Set<ConstraintViolation<HealthMetricRequest>> feverViolations = validator.validate(feverRequest);
        assertTrue(feverViolations.isEmpty(), "High fever 39.8 °C should pass physiological validation");
    }

    @Test
    @DisplayName("Physically impossible heart rate (400 bpm) must be intercepted with exact error message")
    void testPhysicallyImpossibleHeartRateIntercepted() {
        HealthMetricRequest request = createRequest("heartRate", new BigDecimal("400"));
        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Heart rate 400 bpm must be rejected");
        assertEquals(1, violations.size());

        ConstraintViolation<HealthMetricRequest> violation = violations.iterator().next();
        assertEquals("value", violation.getPropertyPath().toString(), "Violation node should be bound to 'value'");
        assertEquals("心率有效生理区间为 20 ~ 300 次/分，请核对后重新输入", violation.getMessage());
    }

    @Test
    @DisplayName("Physically impossible body temperature (80.0 °C) must be intercepted with exact error message")
    void testPhysicallyImpossibleTemperatureIntercepted() {
        HealthMetricRequest request = createRequest("bodyTemperature", new BigDecimal("80.0"));
        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty(), "Temperature 80.0 °C must be rejected");
        assertEquals(1, violations.size());

        ConstraintViolation<HealthMetricRequest> violation = violations.iterator().next();
        assertEquals("value", violation.getPropertyPath().toString(), "Violation node should be bound to 'value'");
        assertEquals("体温有效生理区间为 34 ~ 42.5 °C，请核对后重新输入", violation.getMessage());
    }

    @Test
    @DisplayName("Under physiological lower bound should be intercepted")
    void testUnderPhysiologicalBoundIntercepted() {
        // Heart rate 10 bpm (hardMin is 20)
        HealthMetricRequest request = createRequest("heartRate", new BigDecimal("10"));
        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        ConstraintViolation<HealthMetricRequest> violation = violations.iterator().next();
        assertEquals("value", violation.getPropertyPath().toString());
        assertEquals("心率有效生理区间为 20 ~ 300 次/分，请核对后重新输入", violation.getMessage());
    }

    @Test
    @DisplayName("Unregistered / custom metrics should pass validation leniently")
    void testUnknownMetricLenientPass() {
        HealthMetricRequest request = createRequest("unknownCustomMetric", new BigDecimal("99999"));
        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Unregistered metric should pass without hard limit check");
    }

    @Test
    @DisplayName("Null fields should be handled gracefully by validator leaving check to @NotNull")
    void testNullFieldsHandledGracefully() {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setRecordDate(LocalDate.now());

        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);
        // Expect @NotNull violations for metricKey and value
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("metricKey")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("value")));
    }

    @Test
    @DisplayName("Jackson JSON deserialization should automatically normalize aliases and snake_case to canonical camelCase")
    void testJacksonKeyNormalization() throws Exception {
        // heart_rate -> heartRate
        String hrJson = "{\"metricKey\": \"heart_rate\", \"value\": 75, \"recordDate\": \"2026-11-04\"}";
        HealthMetricRequest hrReq = objectMapper.readValue(hrJson, HealthMetricRequest.class);
        assertEquals("heartRate", hrReq.getMetricKey(), "heart_rate should deserialize to heartRate");

        // systolic_bp -> systolicBP
        String sbpJson = "{\"metricKey\": \"systolic_bp\", \"value\": 120, \"recordDate\": \"2026-11-04\"}";
        HealthMetricRequest sbpReq = objectMapper.readValue(sbpJson, HealthMetricRequest.class);
        assertEquals("systolicBP", sbpReq.getMetricKey(), "systolic_bp should deserialize to systolicBP");

        // temperature -> bodyTemperature
        String tempJson = "{\"metricKey\": \"temperature\", \"value\": 36.5, \"recordDate\": \"2026-11-04\"}";
        HealthMetricRequest tempReq = objectMapper.readValue(tempJson, HealthMetricRequest.class);
        assertEquals("bodyTemperature", tempReq.getMetricKey(), "temperature should deserialize to bodyTemperature");

        // spo2 -> bloodOxygen
        String spo2Json = "{\"metricKey\": \"spo2\", \"value\": 98, \"recordDate\": \"2026-11-04\"}";
        HealthMetricRequest spo2Req = objectMapper.readValue(spo2Json, HealthMetricRequest.class);
        assertEquals("bloodOxygen", spo2Req.getMetricKey(), "spo2 should deserialize to bloodOxygen");
    }

    @Test
    @DisplayName("WellnessMetricRequest physical limit enforcement via MetricValueProvider")
    void testWellnessMetricPhysicalLimitInterception() {
        // Normal wellness metric passes
        WellnessMetricRequest normalReq = new WellnessMetricRequest();
        normalReq.setMetricKey("sleepDuration");
        normalReq.setValue(new BigDecimal("7.5"));
        normalReq.setRecordDate(LocalDate.now());
        Set<ConstraintViolation<WellnessMetricRequest>> normalViolations = validator.validate(normalReq);
        assertTrue(normalViolations.isEmpty(), "Normal sleepDuration 7.5 hours should pass");

        // Physically impossible sleepDuration (28 hours in a day, limit: 0 ~ 24)
        WellnessMetricRequest impossibleReq = new WellnessMetricRequest();
        impossibleReq.setMetricKey("sleepDuration");
        impossibleReq.setValue(new BigDecimal("28.0"));
        impossibleReq.setRecordDate(LocalDate.now());
        Set<ConstraintViolation<WellnessMetricRequest>> violations = validator.validate(impossibleReq);
        assertFalse(violations.isEmpty(), "28 hours sleep should be rejected");
        ConstraintViolation<WellnessMetricRequest> v = violations.iterator().next();
        assertEquals("value", v.getPropertyPath().toString());
        assertEquals("睡眠时长有效生理区间为 0 ~ 24 小时，请核对后重新输入", v.getMessage());

        // Physically impossible waterIntake (25000 ml, limit: 0 ~ 20000)
        WellnessMetricRequest waterReq = new WellnessMetricRequest();
        waterReq.setMetricKey("waterIntake");
        waterReq.setValue(new BigDecimal("25000"));
        waterReq.setRecordDate(LocalDate.now());
        Set<ConstraintViolation<WellnessMetricRequest>> waterViolations = validator.validate(waterReq);
        assertFalse(waterViolations.isEmpty(), "25000 ml water should be rejected");
        assertEquals("饮水量有效生理区间为 0 ~ 20000 ml，请核对后重新输入", waterViolations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("ConfirmMetricsRequest.MetricItem physical limit enforcement")
    void testConfirmMetricsItemPhysicalLimitInterception() {
        // Normal AI confirmed item
        ConfirmMetricsRequest.MetricItem normalItem = new ConfirmMetricsRequest.MetricItem();
        normalItem.setMetricKey("bloodGlucose");
        normalItem.setValue(new BigDecimal("5.6"));
        Set<ConstraintViolation<ConfirmMetricsRequest.MetricItem>> normalViolations = validator.validate(normalItem);
        assertTrue(normalViolations.isEmpty(), "Normal glucose should pass");

        // Impossible heartRate (450 bpm)
        ConfirmMetricsRequest.MetricItem impossibleHr = new ConfirmMetricsRequest.MetricItem();
        impossibleHr.setMetricKey("heartRate");
        impossibleHr.setValue(new BigDecimal("450"));
        Set<ConstraintViolation<ConfirmMetricsRequest.MetricItem>> hrViolations = validator.validate(impossibleHr);
        assertFalse(hrViolations.isEmpty(), "Heart rate 450 bpm must be rejected");
        assertEquals("心率有效生理区间为 20 ~ 300 次/分，请核对后重新输入", hrViolations.iterator().next().getMessage());

        // Impossible temperature (55.0 °C)
        ConfirmMetricsRequest.MetricItem impossibleTemp = new ConfirmMetricsRequest.MetricItem();
        impossibleTemp.setMetricKey("bodyTemperature");
        impossibleTemp.setValue(new BigDecimal("55.0"));
        Set<ConstraintViolation<ConfirmMetricsRequest.MetricItem>> tempViolations = validator.validate(impossibleTemp);
        assertFalse(tempViolations.isEmpty(), "Temperature 55.0 °C must be rejected");
        assertEquals("体温有效生理区间为 34 ~ 42.5 °C，请核对后重新输入", tempViolations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("OcrConfirmRequest.MetricItem physical limit and CBC lab registry enforcement")
    void testOcrConfirmItemPhysicalLimitInterception() {
        // Normal OCR item passes
        OcrConfirmRequest.MetricItem normalItem = new OcrConfirmRequest.MetricItem();
        normalItem.setMetricKey("systolicBP");
        normalItem.setValue(new BigDecimal("120"));
        Set<ConstraintViolation<OcrConfirmRequest.MetricItem>> normalViolations = validator.validate(normalItem);
        assertTrue(normalViolations.isEmpty(), "Normal SBP 120 should pass");

        // Impossible systolicBP (400 mmHg)
        OcrConfirmRequest.MetricItem impossibleSbp = new OcrConfirmRequest.MetricItem();
        impossibleSbp.setMetricKey("systolicBP");
        impossibleSbp.setValue(new BigDecimal("400"));
        Set<ConstraintViolation<OcrConfirmRequest.MetricItem>> sbpViolations = validator.validate(impossibleSbp);
        assertFalse(sbpViolations.isEmpty(), "SBP 400 mmHg must be rejected");
        assertEquals("收缩压有效生理区间为 40 ~ 300 mmHg，请核对后重新输入", sbpViolations.iterator().next().getMessage());

        // Lab routine CBC metric: impossible hemoglobin (350 g/L, limit: 20 ~ 300)
        OcrConfirmRequest.MetricItem impossibleHb = new OcrConfirmRequest.MetricItem();
        impossibleHb.setMetricKey("hemoglobin");
        impossibleHb.setValue(new BigDecimal("350"));
        Set<ConstraintViolation<OcrConfirmRequest.MetricItem>> hbViolations = validator.validate(impossibleHb);
        assertFalse(hbViolations.isEmpty(), "Hemoglobin 350 g/L must be rejected");
        assertEquals("血红蛋白有效生理区间为 20 ~ 300 g/L，请核对后重新输入", hbViolations.iterator().next().getMessage());

        // Lab routine CBC metric: normal hemoglobin passes
        OcrConfirmRequest.MetricItem normalHb = new OcrConfirmRequest.MetricItem();
        normalHb.setMetricKey("hemoglobin");
        normalHb.setValue(new BigDecimal("145"));
        Set<ConstraintViolation<OcrConfirmRequest.MetricItem>> normalHbViolations = validator.validate(normalHb);
        assertTrue(normalHbViolations.isEmpty(), "Hemoglobin 145 g/L should pass");
    }
}
