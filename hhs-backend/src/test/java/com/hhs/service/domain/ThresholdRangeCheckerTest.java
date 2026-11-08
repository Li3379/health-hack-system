package com.hhs.service.domain;

import com.hhs.entity.UserThreshold;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ThresholdRangeChecker}.
 *
 * <p>Fixture models systolic blood pressure (mmHg): criticalLow 80, warningLow 90,
 * warningHigh 140, criticalHigh 160. Covers the FIX-01 regression (inverted warning
 * band) and the D-18 boundary matrix.</p>
 */
class ThresholdRangeCheckerTest {

    private ThresholdRangeChecker checker;
    private UserThreshold systolic;

    @BeforeEach
    void setUp() {
        checker = new ThresholdRangeChecker();
        systolic = threshold(new BigDecimal("80"), new BigDecimal("90"),
                new BigDecimal("140"), new BigDecimal("160"));
    }

    private UserThreshold threshold(BigDecimal criticalLow, BigDecimal warningLow,
                                    BigDecimal warningHigh, BigDecimal criticalHigh) {
        UserThreshold t = new UserThreshold();
        t.setCriticalLow(criticalLow);
        t.setWarningLow(warningLow);
        t.setWarningHigh(warningHigh);
        t.setCriticalHigh(criticalHigh);
        return t;
    }

    @Test
    @DisplayName("Mid-range value 120 is NORMAL")
    void value120MidRangeIsNormal() {
        assertTrue(checker.isNormalRange(120.0, systolic));
        assertFalse(checker.isWarningRange(120.0, systolic));
        assertFalse(checker.isCriticalRange(120.0, systolic));
        assertEquals("NORMAL", checker.getSeverityLevel(120.0, systolic));
    }

    @Test
    @DisplayName("FIX-01: value equal to warningHigh (140) is WARNING, not NORMAL")
    void value140AtWarningHighIsWarning() {
        assertFalse(checker.isNormalRange(140.0, systolic));
        assertTrue(checker.isWarningRange(140.0, systolic));
        assertFalse(checker.isCriticalRange(140.0, systolic));
        assertEquals("WARNING", checker.getSeverityLevel(140.0, systolic));
    }

    @Test
    @DisplayName("Value 139 just below warningHigh is NORMAL")
    void value139JustBelowWarningHighIsNormal() {
        assertTrue(checker.isNormalRange(139.0, systolic));
        assertFalse(checker.isWarningRange(139.0, systolic));
        assertEquals("NORMAL", checker.getSeverityLevel(139.0, systolic));
    }

    @Test
    @DisplayName("Value 159 just below criticalHigh is still WARNING")
    void value159JustBelowCriticalHighIsWarning() {
        assertFalse(checker.isNormalRange(159.0, systolic));
        assertTrue(checker.isWarningRange(159.0, systolic));
        assertFalse(checker.isCriticalRange(159.0, systolic));
        assertEquals("WARNING", checker.getSeverityLevel(159.0, systolic));
    }

    @Test
    @DisplayName("Value equal to warningLow (90) is WARNING on the low side")
    void value90AtWarningLowIsWarning() {
        assertFalse(checker.isNormalRange(90.0, systolic));
        assertTrue(checker.isWarningRange(90.0, systolic));
        assertFalse(checker.isCriticalRange(90.0, systolic));
        assertEquals("WARNING", checker.getSeverityLevel(90.0, systolic));
    }

    @Test
    @DisplayName("Value 89 below warningLow is WARNING on the low side")
    void value89BelowWarningLowIsWarning() {
        assertFalse(checker.isNormalRange(89.0, systolic));
        assertTrue(checker.isWarningRange(89.0, systolic));
        assertFalse(checker.isCriticalRange(89.0, systolic));
        assertEquals("WARNING", checker.getSeverityLevel(89.0, systolic));
    }

    @Test
    @DisplayName("Value 91 just above warningLow is NORMAL")
    void value91JustAboveWarningLowIsNormal() {
        assertTrue(checker.isNormalRange(91.0, systolic));
        assertFalse(checker.isWarningRange(91.0, systolic));
        assertEquals("NORMAL", checker.getSeverityLevel(91.0, systolic));
    }

    @Test
    @DisplayName("Value equal to criticalLow (80) is CRITICAL")
    void value80AtCriticalLowIsCritical() {
        assertFalse(checker.isNormalRange(80.0, systolic));
        assertFalse(checker.isWarningRange(80.0, systolic));
        assertTrue(checker.isCriticalRange(80.0, systolic));
        assertEquals("CRITICAL", checker.getSeverityLevel(80.0, systolic));
    }

    @Test
    @DisplayName("Value equal to criticalHigh (160) is CRITICAL")
    void value160AtCriticalHighIsCritical() {
        assertFalse(checker.isNormalRange(160.0, systolic));
        assertFalse(checker.isWarningRange(160.0, systolic));
        assertTrue(checker.isCriticalRange(160.0, systolic));
        assertEquals("CRITICAL", checker.getSeverityLevel(160.0, systolic));
    }

    @Test
    @DisplayName("getSeverityLevel returns the matching string for every band")
    void severityLevelMatchesEveryBand() {
        assertEquals("CRITICAL", checker.getSeverityLevel(79.0, systolic));
        assertEquals("WARNING", checker.getSeverityLevel(85.0, systolic));
        assertEquals("NORMAL", checker.getSeverityLevel(120.0, systolic));
        assertEquals("WARNING", checker.getSeverityLevel(150.0, systolic));
        assertEquals("CRITICAL", checker.getSeverityLevel(161.0, systolic));
    }

    @Test
    @DisplayName("Alert level priority maps severity strings correctly")
    void alertLevelMapsSeverityStrings() {
        assertEquals("HIGH", checker.getAlertLevel("CRITICAL"));
        assertEquals("MEDIUM", checker.getAlertLevel("WARNING"));
        assertEquals("LOW", checker.getAlertLevel("NORMAL"));
    }
}
