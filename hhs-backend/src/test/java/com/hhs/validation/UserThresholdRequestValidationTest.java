package com.hhs.validation;

import com.hhs.dto.UserThresholdRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation baseline for {@link UserThresholdRequest}.
 *
 * <p>Locks down the Chinese constraint messages so that no English literal can reach the
 * frontend when a threshold request fails bean validation (ERR-02 / D-16).
 */
class UserThresholdRequestValidationTest {

    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]{3,}");

    private static final String USER_ID_REQUIRED = "用户标识不能为空";
    private static final String METRIC_KEY_REQUIRED = "指标类型不能为空";
    private static final String METRIC_KEY_LENGTH = "指标类型长度需在 1 至 64 个字符之间";
    private static final String WARNING_HIGH_NEGATIVE = "警告高值不能为负数";
    private static final String CRITICAL_HIGH_NEGATIVE = "严重高值不能为负数";
    private static final String WARNING_LOW_NEGATIVE = "警告低值不能为负数";
    private static final String CRITICAL_LOW_NEGATIVE = "严重低值不能为负数";
    private static final String CRITICAL_LOW_VS_WARNING_LOW = "严重低值必须小于警告低值，请调整其中一个";
    private static final String WARNING_LOW_VS_WARNING_HIGH = "警告低值必须小于警告高值，请调整其中一个";
    private static final String WARNING_HIGH_VS_CRITICAL_HIGH = "警告高值必须小于严重高值，请调整其中一个";
    private static final String HEART_RATE_HARD_RANGE = "心率有效生理区间为 20 ~ 300 次/分，请核对后重新输入";

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    private UserThresholdRequest validRequest() {
        UserThresholdRequest request = new UserThresholdRequest();
        request.setUserId(1L);
        request.setMetricKey("heartRate");
        request.setWarningHigh(new BigDecimal("100"));
        request.setCriticalHigh(new BigDecimal("120"));
        request.setWarningLow(new BigDecimal("50"));
        request.setCriticalLow(new BigDecimal("40"));
        return request;
    }

    @Test
    void validRequest_HasNoViolations() {
        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(validRequest());
        assertTrue(violations.isEmpty(), "Valid threshold request should have no violations");
    }

    @Test
    void nullUserId_ReportsChineseMessageOnUserIdProperty() {
        UserThresholdRequest request = validRequest();
        request.setUserId(null);

        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());

        ConstraintViolation<UserThresholdRequest> violation = violations.stream()
                .filter(v -> USER_ID_REQUIRED.equals(v.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Chinese message: " + USER_ID_REQUIRED));

        assertEquals("userId", violation.getPropertyPath().toString());
    }

    @Test
    void blankMetricKey_ReportsChineseLengthMessage() {
        UserThresholdRequest request = validRequest();
        request.setMetricKey("");

        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains(METRIC_KEY_LENGTH)));
    }

    @Test
    void negativeWarningHigh_ReportsChineseMessageOnWarningHighProperty() {
        UserThresholdRequest request = validRequest();
        request.setWarningHigh(new BigDecimal("-1"));

        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());

        ConstraintViolation<UserThresholdRequest> violation = violations.stream()
                .filter(v -> WARNING_HIGH_NEGATIVE.equals(v.getMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing Chinese message: " + WARNING_HIGH_NEGATIVE));

        assertEquals("warningHigh", violation.getPropertyPath().toString());
    }

    @Test
    void allFourDecimalMinMessages_AreDistinct() {
        UserThresholdRequest request = validRequest();
        request.setWarningHigh(new BigDecimal("-1"));
        request.setCriticalHigh(new BigDecimal("-1"));
        request.setWarningLow(new BigDecimal("-1"));
        request.setCriticalLow(new BigDecimal("-1"));

        Set<String> messages = validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .filter(m -> m.endsWith("不能为负数"))
                .collect(Collectors.toSet());

        assertEquals(4, messages.size(), "Four @DecimalMin messages must be distinct, got: " + messages);
        assertTrue(messages.contains(WARNING_HIGH_NEGATIVE));
        assertTrue(messages.contains(CRITICAL_HIGH_NEGATIVE));
        assertTrue(messages.contains(WARNING_LOW_NEGATIVE));
        assertTrue(messages.contains(CRITICAL_LOW_NEGATIVE));
    }

    @Test
    void nullMetricKey_ReportsChineseRequiredMessage() {
        UserThresholdRequest request = validRequest();
        request.setMetricKey(null);

        assertTrue(validator.validate(request).stream()
                .anyMatch(v -> METRIC_KEY_REQUIRED.equals(v.getMessage())));
    }

    @Test
    void everyConstraintMessage_IsPureChineseWithoutLatinWords() {
        UserThresholdRequest request = new UserThresholdRequest();
        request.setUserId(null);
        request.setMetricKey(null);
        request.setWarningHigh(new BigDecimal("-1"));
        request.setCriticalHigh(new BigDecimal("-1"));
        request.setWarningLow(new BigDecimal("-1"));
        request.setCriticalLow(new BigDecimal("-1"));

        Set<String> messages = validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());

        UserThresholdRequest blankMetricKeyRequest = validRequest();
        blankMetricKeyRequest.setMetricKey("");
        validator.validate(blankMetricKeyRequest).stream()
                .map(ConstraintViolation::getMessage)
                .forEach(messages::add);

        // @ValidThresholdRange hard-limit message (heartRate unit 次/分 keeps the message
        // pure Chinese; boundary 400 exceeds heartRate hardMax 300)
        UserThresholdRequest hardLimitRequest = new UserThresholdRequest();
        hardLimitRequest.setUserId(1L);
        hardLimitRequest.setMetricKey("heartRate");
        hardLimitRequest.setWarningHigh(new BigDecimal("400"));
        validator.validate(hardLimitRequest).stream()
                .map(ConstraintViolation::getMessage)
                .forEach(messages::add);

        assertTrue(messages.contains(USER_ID_REQUIRED));
        assertTrue(messages.contains(METRIC_KEY_REQUIRED));
        assertTrue(messages.contains(METRIC_KEY_LENGTH));
        assertTrue(messages.contains(CRITICAL_LOW_VS_WARNING_LOW));
        assertTrue(messages.contains(WARNING_LOW_VS_WARNING_HIGH));
        assertTrue(messages.contains(WARNING_HIGH_VS_CRITICAL_HIGH));
        assertTrue(messages.contains(HEART_RATE_HARD_RANGE));
        assertEquals(11, messages.size(), "Expected 11 distinct messages, got: " + messages);

        for (String message : messages) {
            assertFalse(LATIN_WORD.matcher(message).find(),
                    "Constraint message must not contain an English word: " + message);
        }
    }
}