package com.hhs.validation;

import com.hhs.dto.UserThresholdRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THR-05 backend proof for {@link ValidThresholdRange} on {@link UserThresholdRequest}.
 *
 * <p>Locks the D-09 contract: one broken pair produces exactly TWO violations whose property
 * paths are the two involved fields, and no field name ever contains a dot (RESEARCH §Pitfall 5 —
 * SpringValidatorAdapter joins chained PROPERTY nodes with a dot, which no frontend prop matches).
 * Runs against the plain {@code Validation.buildDefaultValidatorFactory()} so the validator's
 * no-arg constructor (registry without database) is exercised, exactly like production's
 * default validator factory would.
 */
class ThresholdRangeValidatorTest {

    private static final String CRITICAL_LOW_VS_WARNING_LOW = "严重低值必须小于警告低值，请调整其中一个";
    private static final String WARNING_LOW_VS_WARNING_HIGH = "警告低值必须小于警告高值，请调整其中一个";
    private static final String WARNING_HIGH_VS_CRITICAL_HIGH = "警告高值必须小于严重高值，请调整其中一个";

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    private UserThresholdRequest request(String metricKey, BigDecimal criticalLow, BigDecimal warningLow,
                                         BigDecimal warningHigh, BigDecimal criticalHigh) {
        UserThresholdRequest request = new UserThresholdRequest();
        request.setUserId(1L);
        request.setMetricKey(metricKey);
        request.setCriticalLow(criticalLow);
        request.setWarningLow(warningLow);
        request.setWarningHigh(warningHigh);
        request.setCriticalHigh(criticalHigh);
        return request;
    }

    private Set<String> propertyPaths(Set<ConstraintViolation<UserThresholdRequest>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private void assertNoDotInAnyPropertyPath(Set<ConstraintViolation<UserThresholdRequest>> violations) {
        for (ConstraintViolation<UserThresholdRequest> violation : violations) {
            String path = violation.getPropertyPath().toString();
            assertTrue(!path.contains("."),
                    "Field name must be dot-free so el-form-item prop matches, got: " + path);
        }
    }

    @Test
    @DisplayName("Valid ordering within hard limits yields zero violations")
    void validOrdering_YieldsZeroViolations() {
        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(
                request("heartRate", new BigDecimal("40"), new BigDecimal("50"),
                        new BigDecimal("100"), new BigDecimal("120")));
        assertTrue(violations.isEmpty(), "Valid ordering should produce no violations: " + violations);
    }

    @Test
    @DisplayName("criticalLow == warningLow yields exactly 2 violations on criticalLow and warningLow")
    void criticalLowEqualToWarningLow_YieldsTwoViolationsOnBothFields() {
        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(
                request("heartRate", new BigDecimal("50"), new BigDecimal("50"),
                        new BigDecimal("100"), new BigDecimal("120")));

        assertEquals(2, violations.size(), "One broken pair must produce exactly two violations");
        assertEquals(Set.of("criticalLow", "warningLow"), propertyPaths(violations),
                "Both involved fields must carry one violation each");
        assertTrue(violations.stream().allMatch(v -> CRITICAL_LOW_VS_WARNING_LOW.equals(v.getMessage())));
        assertNoDotInAnyPropertyPath(violations);
    }

    @Test
    @DisplayName("warningLow == warningHigh yields exactly 2 violations on warningLow and warningHigh")
    void warningLowEqualToWarningHigh_YieldsTwoViolationsOnBothFields() {
        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(
                request("heartRate", new BigDecimal("40"), new BigDecimal("80"),
                        new BigDecimal("80"), new BigDecimal("120")));

        assertEquals(2, violations.size(), "One broken pair must produce exactly two violations");
        assertEquals(Set.of("warningLow", "warningHigh"), propertyPaths(violations),
                "Both involved fields must carry one violation each");
        assertTrue(violations.stream().allMatch(v -> WARNING_LOW_VS_WARNING_HIGH.equals(v.getMessage())));
        assertNoDotInAnyPropertyPath(violations);
    }

    @Test
    @DisplayName("warningHigh == criticalHigh yields exactly 2 violations on warningHigh and criticalHigh")
    void warningHighEqualToCriticalHigh_YieldsTwoViolationsOnBothFields() {
        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(
                request("heartRate", new BigDecimal("40"), new BigDecimal("50"),
                        new BigDecimal("120"), new BigDecimal("120")));

        assertEquals(2, violations.size(), "One broken pair must produce exactly two violations");
        assertEquals(Set.of("warningHigh", "criticalHigh"), propertyPaths(violations),
                "Both involved fields must carry one violation each");
        assertTrue(violations.stream().allMatch(v -> WARNING_HIGH_VS_CRITICAL_HIGH.equals(v.getMessage())));
        assertNoDotInAnyPropertyPath(violations);
    }

    @Test
    @DisplayName("A pair with one side null is skipped (null = unbounded, D-10)")
    void singleNullSide_SkipsThePair() {
        // criticalLow null: pair 1 skipped; remaining pairs are strictly increasing
        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(
                request("heartRate", null, new BigDecimal("50"),
                        new BigDecimal("100"), new BigDecimal("120")));
        assertTrue(violations.isEmpty(), "Null side must skip the pair, got: " + violations);
    }

    @Test
    @DisplayName("A request with only the two high boundaries set validates cleanly")
    void onlyHighBoundariesSet_ValidatesCleanly() {
        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(
                request("heartRate", null, null, new BigDecimal("100"), new BigDecimal("120")));
        assertTrue(violations.isEmpty(), "Only-high-boundaries request must validate cleanly: " + violations);
    }

    @Test
    @DisplayName("Boundary beyond metadata hardMax is rejected server-side (D-11)")
    void warningHighBeyondHardMax_YieldsViolationOnWarningHigh() {
        // heartRate hardMax = 300: warningHigh 400 violates the physiological limit even
        // though the ordering itself is fine (criticalHigh left null so only this boundary trips)
        Set<ConstraintViolation<UserThresholdRequest>> violations = validator.validate(
                request("heartRate", null, new BigDecimal("50"), new BigDecimal("400"), null));

        assertEquals(1, violations.size(), "Exactly one hard-limit violation expected: " + violations);
        ConstraintViolation<UserThresholdRequest> violation = violations.iterator().next();
        assertEquals("warningHigh", violation.getPropertyPath().toString());
        assertTrue(violation.getMessage().contains("心率"),
                "Hard-limit message must name the metric display name: " + violation.getMessage());
        assertTrue(violation.getMessage().contains("300"),
                "Hard-limit message must name the allowed range: " + violation.getMessage());
        assertNoDotInAnyPropertyPath(violations);
    }
}
