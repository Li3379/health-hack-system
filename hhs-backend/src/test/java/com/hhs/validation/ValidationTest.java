package com.hhs.validation;

import com.hhs.dto.AIClassifyRequest;
import com.hhs.dto.AlertVO;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.dto.HealthProfileRequest;
import com.hhs.dto.RealtimeMetricRequest;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive validation tests for DTOs and custom validators.
 * Tests Jakarta Bean Validation annotations and custom constraint validators.
 */
public class ValidationTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    // ==================== HealthMetricRequest Tests ====================

    @Test
    public void testHealthMetricRequest_ValidData_Passes() {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setUserId(1L);
        request.setMetricKey("heart_rate");
        request.setValue(new BigDecimal("72"));
        request.setRecordDate(LocalDate.now());
        request.setUnit("bpm");
        request.setTrend("stable");

        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    public void testHealthMetricRequest_NullMetricType_Fails() {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey(null);
        request.setValue(new BigDecimal("72"));
        request.setRecordDate(LocalDate.now());

        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("Metric key is required")));
    }

    @Test
    public void testHealthMetricRequest_InvalidMetricTypePattern_Fails() {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey("heart rate!"); // Invalid characters
        request.setValue(new BigDecimal("72"));
        request.setRecordDate(LocalDate.now());

        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("letters, numbers, hyphens, and underscores")));
    }

    @Test
    public void testHealthMetricRequest_NegativeValue_Fails() {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey("heart_rate");
        request.setValue(new BigDecimal("-10"));
        request.setRecordDate(LocalDate.now());

        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("non-negative")));
    }

    @Test
    public void testHealthMetricRequest_ValueTooHigh_Fails() {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey("heart_rate");
        request.setValue(new BigDecimal("1001"));
        request.setRecordDate(LocalDate.now());

        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("not exceed 1000")));
    }

    @Test
    public void testHealthMetricRequest_FutureDate_Fails() {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey("heart_rate");
        request.setValue(new BigDecimal("72"));
        request.setRecordDate(LocalDate.now().plusDays(1));

        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("future")));
    }

    @Test
    public void testHealthMetricRequest_InvalidTrend_Fails() {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey("heart_rate");
        request.setValue(new BigDecimal("72"));
        request.setRecordDate(LocalDate.now());
        request.setTrend("invalid_trend");

        Set<ConstraintViolation<HealthMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("up, down, stable, normal")));
    }

    // ==================== HealthProfileRequest Tests ====================

    @Test
    public void testHealthProfileRequest_ValidData_Passes() {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setBirthDate(LocalDate.of(1990, 1, 1));
        request.setGender("male");
        request.setHeightCm(new BigDecimal("175"));
        request.setWeightKg(new BigDecimal("70"));
        request.setBloodType("A+");

        Set<ConstraintViolation<HealthProfileRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void testHealthProfileRequest_InvalidGender_Fails() {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setBirthDate(LocalDate.of(1990, 1, 1));
        request.setGender("invalid");

        Set<ConstraintViolation<HealthProfileRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("male, female, other")));
    }

    @Test
    public void testHealthProfileRequest_InvalidBloodType_Fails() {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setBirthDate(LocalDate.of(1990, 1, 1));
        request.setBloodType("invalid");

        Set<ConstraintViolation<HealthProfileRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("valid type")));
    }

    @Test
    public void testHealthProfileRequest_HeightTooLow_Fails() {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setBirthDate(LocalDate.of(1990, 1, 1));
        request.setHeightCm(new BigDecimal("40")); // Below 50

        Set<ConstraintViolation<HealthProfileRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("at least 50")));
    }

    @Test
    public void testHealthProfileRequest_WeightTooHigh_Fails() {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setBirthDate(LocalDate.of(1990, 1, 1));
        request.setWeightKg(new BigDecimal("600")); // Above 500

        Set<ConstraintViolation<HealthProfileRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("not exceed 500")));
    }

    @Test
    public void testHealthProfileRequest_FutureBirthDate_Fails() {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setBirthDate(LocalDate.now().plusDays(1));

        Set<ConstraintViolation<HealthProfileRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("past")));
    }

    // ==================== AIClassifyRequest Tests ====================

    @Test
    public void testAIClassifyRequest_ValidData_Passes() {
        AIClassifyRequest request = new AIClassifyRequest();
        request.setContent("This is valid content for classification");
        request.setCategory("health");

        Set<ConstraintViolation<AIClassifyRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void testAIClassifyRequest_EmptyContent_Fails() {
        AIClassifyRequest request = new AIClassifyRequest();
        request.setContent("");

        Set<ConstraintViolation<AIClassifyRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    public void testAIClassifyRequest_ContentTooLong_Fails() {
        AIClassifyRequest request = new AIClassifyRequest();
        request.setContent("a".repeat(10001));

        Set<ConstraintViolation<AIClassifyRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("10000")));
    }

    @Test
    public void testAIClassifyRequest_CategoryTooLong_Fails() {
        AIClassifyRequest request = new AIClassifyRequest();
        request.setContent("Valid content");
        request.setCategory("a".repeat(101));

        Set<ConstraintViolation<AIClassifyRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("100")));
    }

    // ==================== RealtimeMetricRequest Tests ====================

    @Test
    public void testRealtimeMetricRequest_ValidData_Passes() {
        RealtimeMetricRequest request = new RealtimeMetricRequest();
        request.setMetricKey("heart_rate");
        request.setValue(new BigDecimal("72"));
        request.setSource("manual");

        Set<ConstraintViolation<RealtimeMetricRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void testRealtimeMetricRequest_NegativeValue_Fails() {
        RealtimeMetricRequest request = new RealtimeMetricRequest();
        request.setMetricKey("blood_pressure");
        request.setValue(new BigDecimal("-10"));

        Set<ConstraintViolation<RealtimeMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("non-negative")));
    }

    @Test
    public void testRealtimeMetricRequest_InvalidMetricKeyPattern_Fails() {
        RealtimeMetricRequest request = new RealtimeMetricRequest();
        request.setMetricKey("heart rate!");
        request.setValue(new BigDecimal("72"));

        Set<ConstraintViolation<RealtimeMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("letters, numbers, hyphens, and underscores")));
    }

    @Test
    public void testRealtimeMetricRequest_InvalidSource_Fails() {
        RealtimeMetricRequest request = new RealtimeMetricRequest();
        request.setMetricKey("heart_rate");
        request.setValue(new BigDecimal("72"));
        request.setSource("invalid_source");

        Set<ConstraintViolation<RealtimeMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("manual, device, api")));
    }

    @Test
    public void testRealtimeMetricRequest_QualityScoreOutOfRange_Fails() {
        RealtimeMetricRequest request = new RealtimeMetricRequest();
        request.setMetricKey("heart_rate");
        request.setValue(new BigDecimal("72"));
        request.setQualityScore(new BigDecimal("1.5")); // Above 1.0

        Set<ConstraintViolation<RealtimeMetricRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("not exceed 1")));
    }

    // ==================== AlertVO Tests ====================

    @Test
    public void testAlertVO_ValidData_Passes() {
        AlertVO alert = new AlertVO();
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("Test Alert");
        alert.setMessage("This is a test alert message");

        Set<ConstraintViolation<AlertVO>> violations = validator.validate(alert);
        assertTrue(violations.isEmpty());
    }

    @Test
    public void testAlertVO_InvalidAlertType_Fails() {
        AlertVO alert = new AlertVO();
        alert.setAlertType("INVALID");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("Test Alert");
        alert.setMessage("This is a test alert message");

        Set<ConstraintViolation<AlertVO>> violations = validator.validate(alert);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("CRITICAL, WARNING, INFO, TREND, or RECOVERY")));
    }

    @Test
    public void testAlertVO_InvalidAlertLevel_Fails() {
        AlertVO alert = new AlertVO();
        alert.setAlertType("WARNING");
        alert.setAlertLevel("INVALID");
        alert.setTitle("Test Alert");
        alert.setMessage("This is a test alert message");

        Set<ConstraintViolation<AlertVO>> violations = validator.validate(alert);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("HIGH, MEDIUM, or LOW")));
    }

    @Test
    public void testAlertVO_EmptyTitle_Fails() {
        AlertVO alert = new AlertVO();
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("");
        alert.setMessage("This is a test alert message");

        Set<ConstraintViolation<AlertVO>> violations = validator.validate(alert);
        assertFalse(violations.isEmpty());
    }

    @Test
    public void testAlertVO_TitleTooLong_Fails() {
        AlertVO alert = new AlertVO();
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("a".repeat(201));
        alert.setMessage("This is a test alert message");

        Set<ConstraintViolation<AlertVO>> violations = validator.validate(alert);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("200")));
    }

    @Test
    public void testAlertVO_CurrentValueTooHigh_Fails() {
        AlertVO alert = new AlertVO();
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setTitle("Test Alert");
        alert.setMessage("This is a test alert message");
        alert.setCurrentValue(new BigDecimal("1001"));

        Set<ConstraintViolation<AlertVO>> violations = validator.validate(alert);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
            .anyMatch(v -> v.getMessage().contains("not exceed 1000")));
    }

    // ==================== Custom Validator Tests ====================

    @Test
    public void testProfileDataValidator_InvalidKey_Fails() {
        Map<String, Object> data = new HashMap<>();
        data.put("invalid key with spaces", "value");

        ProfileDataValidator validator = new ProfileDataValidator();
        validator.initialize(null);
        assertFalse(validator.isValid(data, null));
    }

    @Test
    public void testProfileDataValidator_TooManyKeys_Fails() {
        Map<String, Object> data = new HashMap<>();
        for (int i = 0; i < 51; i++) {
            data.put("key" + i, "value");
        }

        ProfileDataValidator validator = new ProfileDataValidator();
        validator.initialize(null);
        assertFalse(validator.isValid(data, null));
    }

    @Test
    public void testProfileDataValidator_ValidData_Passes() {
        Map<String, Object> data = new HashMap<>();
        data.put("valid_key-1", "value");
        data.put("anotherKey", "another value");

        ProfileDataValidator validator = new ProfileDataValidator();
        validator.initialize(null);
        assertTrue(validator.isValid(data, null));
    }

    @Test
    public void testProfileDataValidator_EmptyData_Passes() {
        Map<String, Object> data = new HashMap<>();

        ProfileDataValidator validator = new ProfileDataValidator();
        validator.initialize(null);
        assertTrue(validator.isValid(data, null));
    }

    @Test
    public void testProfileDataValidator_NullData_Passes() {
        ProfileDataValidator validator = new ProfileDataValidator();
        validator.initialize(null);
        assertTrue(validator.isValid(null, null));
    }

    @Test
    public void testProfileDataValidator_StringValueTooLong_Fails() {
        Map<String, Object> data = new HashMap<>();
        data.put("key", "a".repeat(1001));

        ProfileDataValidator validator = new ProfileDataValidator();
        validator.initialize(null);
        assertFalse(validator.isValid(data, null));
    }

    @Test
    public void testMetricRangeValidator_ValidValue_Passes() {
        MetricRangeValidator validator = new MetricRangeValidator();
        validator.initialize(null);
        assertTrue(validator.isValid(new BigDecimal("72"), null));
        assertTrue(validator.isValid(new BigDecimal("0"), null));
        assertTrue(validator.isValid(new BigDecimal("1000"), null));
    }

    @Test
    public void testMetricRangeValidator_NegativeValue_Fails() {
        MetricRangeValidator validator = new MetricRangeValidator();
        validator.initialize(null);
        assertFalse(validator.isValid(new BigDecimal("-1"), null));
    }

    @Test
    public void testMetricRangeValidator_ValueTooHigh_Fails() {
        MetricRangeValidator validator = new MetricRangeValidator();
        validator.initialize(null);
        assertFalse(validator.isValid(new BigDecimal("1001"), null));
    }

    @Test
    public void testMetricRangeValidator_NullValue_Passes() {
        MetricRangeValidator validator = new MetricRangeValidator();
        validator.initialize(null);
        assertTrue(validator.isValid(null, null));
    }
}
