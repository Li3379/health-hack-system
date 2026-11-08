package com.hhs.service.domain;

import com.hhs.entity.UserThreshold;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ThresholdRangeChecker}.
 *
 * <p>Fixture models systolic blood pressure (mmHg): criticalLow 80, warningLow 90,
 * warningHigh 140, criticalHigh 160. Covers the FIX-01 regression (inverted warning
 * band) and the full D-18 boundary matrix: normal / warning both sides / critical
 * both sides / single-sided null / all null / exact boundary equality, plus the
 * complementarity invariant between the public predicates and {@code classify()}.</p>
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

    /**
     * Asserts the three public predicates agree with the expected band string —
     * the complementarity contract required by the plan's must-haves.
     */
    private void assertBandConsistency(double value, String expectedSeverity, UserThreshold t) {
        String severity = checker.getSeverityLevel(value, t);
        assertEquals(expectedSeverity, severity, "severity for value " + value);
        assertEquals("NORMAL".equals(expectedSeverity), checker.isNormalRange(value, t),
                "isNormalRange for value " + value);
        assertEquals("WARNING".equals(expectedSeverity), checker.isWarningRange(value, t),
                "isWarningRange for value " + value);
        assertEquals("CRITICAL".equals(expectedSeverity), checker.isCriticalRange(value, t),
                "isCriticalRange for value " + value);
    }

    // ------------------------------------------------------------------
    // Task 1: FIX-01 regression — warning band fires where it returned NORMAL
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // D-18 class 1+2: warning and critical bands, high side (parameterized)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "systolic {0} -> {1}")
    @CsvSource({
            "139, NORMAL",
            "140, WARNING",
            "141, WARNING",
            "159, WARNING",
            "160, CRITICAL",
            "161, CRITICAL"
    })
    @DisplayName("High band: criticalHigh±1 and warningHigh±1 land in the right band")
    void highBandBoundaries(Double value, String expectedSeverity) {
        assertBandConsistency(value, expectedSeverity, systolic);
    }

    // ------------------------------------------------------------------
    // D-18 class 1+2: warning and critical bands, low side (parameterized)
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "systolic {0} -> {1}")
    @CsvSource({
            "79, CRITICAL",
            "80, CRITICAL",
            "81, WARNING",
            "89, WARNING",
            "90, WARNING",
            "91, NORMAL"
    })
    @DisplayName("Low band: criticalLow±1 and warningLow±1 land in the right band")
    void lowBandBoundaries(Double value, String expectedSeverity) {
        assertBandConsistency(value, expectedSeverity, systolic);
    }

    // ------------------------------------------------------------------
    // D-18 class 4: single-sided null boundaries
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Null low boundaries are unbounded: unset side stays NORMAL, set side fires")
    void highBoundariesOnlyLowSidesNull() {
        UserThreshold highOnly = threshold(null, null,
                new BigDecimal("140"), new BigDecimal("160"));

        // Unset (low) side never produces a band on its own
        assertTrue(checker.isNormalRange(50.0, highOnly));
        assertFalse(checker.isWarningRange(50.0, highOnly));
        assertFalse(checker.isCriticalRange(50.0, highOnly));
        assertEquals("NORMAL", checker.getSeverityLevel(50.0, highOnly));
        assertEquals("NORMAL", checker.getSeverityLevel(139.0, highOnly));

        // Set (high) side still fires across the full band
        assertEquals("WARNING", checker.getSeverityLevel(140.0, highOnly));
        assertEquals("WARNING", checker.getSeverityLevel(159.0, highOnly));
        assertEquals("CRITICAL", checker.getSeverityLevel(160.0, highOnly));
        assertEquals("CRITICAL", checker.getSeverityLevel(200.0, highOnly));
    }

    @Test
    @DisplayName("Null high boundaries are unbounded: unset side stays NORMAL, set side fires")
    void lowBoundariesOnlyHighSidesNull() {
        UserThreshold lowOnly = threshold(new BigDecimal("80"), new BigDecimal("90"),
                null, null);

        // Unset (high) side never produces a band on its own
        assertTrue(checker.isNormalRange(100.0, lowOnly));
        assertFalse(checker.isWarningRange(100.0, lowOnly));
        assertFalse(checker.isCriticalRange(100.0, lowOnly));
        assertEquals("NORMAL", checker.getSeverityLevel(100.0, lowOnly));
        assertEquals("NORMAL", checker.getSeverityLevel(250.0, lowOnly));

        // Set (low) side still fires across the full band
        assertEquals("WARNING", checker.getSeverityLevel(90.0, lowOnly));
        assertEquals("WARNING", checker.getSeverityLevel(85.0, lowOnly));
        assertEquals("CRITICAL", checker.getSeverityLevel(80.0, lowOnly));
        assertEquals("CRITICAL", checker.getSeverityLevel(40.0, lowOnly));
    }

    // ------------------------------------------------------------------
    // D-18 class 5: all boundaries null
    // ------------------------------------------------------------------

    @Test
    @DisplayName("All four boundaries null yields NORMAL for every finite value")
    void allBoundariesNullYieldsNormal() {
        UserThreshold allNull = threshold(null, null, null, null);

        for (double value : new double[] {0.0, 1.0, 1000.0}) {
            assertTrue(checker.isNormalRange(value, allNull), "isNormalRange for " + value);
            assertFalse(checker.isWarningRange(value, allNull), "isWarningRange for " + value);
            assertFalse(checker.isCriticalRange(value, allNull), "isCriticalRange for " + value);
            assertEquals("NORMAL", checker.getSeverityLevel(value, allNull),
                    "severity for " + value);
        }
    }

    // ------------------------------------------------------------------
    // D-18 class 6: null value / null threshold public contracts
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Null value keeps pre-existing public return contracts")
    void nullValueKeepsPublicContracts() {
        assertTrue(checker.isNormalRange(null, systolic));
        assertFalse(checker.isWarningRange(null, systolic));
        assertFalse(checker.isCriticalRange(null, systolic));
        assertEquals("NORMAL", checker.getSeverityLevel(null, systolic));
    }

    @Test
    @DisplayName("Null threshold keeps pre-existing public return contracts")
    void nullThresholdKeepsPublicContracts() {
        assertTrue(checker.isNormalRange(120.0, null));
        assertFalse(checker.isWarningRange(120.0, null));
        assertFalse(checker.isCriticalRange(120.0, null));
        assertEquals("NORMAL", checker.getSeverityLevel(120.0, null));
    }

    // ------------------------------------------------------------------
    // Exact boundary equality on a non-integer scale
    // ------------------------------------------------------------------

    @Test
    @DisplayName("One-decimal boundary equality: blood glucose 7.0 vs warningHigh 7.0 is WARNING")
    void decimalBoundaryEqualityOnGlucose() {
        UserThreshold glucose = threshold(new BigDecimal("2.8"), new BigDecimal("3.9"),
                new BigDecimal("7.0"), new BigDecimal("15.0"));

        assertBandConsistency(Double.parseDouble("7.0"), "WARNING", glucose);
        assertBandConsistency(Double.parseDouble("6.9"), "NORMAL", glucose);
        assertBandConsistency(Double.parseDouble("7.1"), "WARNING", glucose);
        assertBandConsistency(Double.parseDouble("15.0"), "CRITICAL", glucose);
    }

    // ------------------------------------------------------------------
    // Complementarity invariant (Pitfall 1 canary)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Complementarity invariant: isNormalRange/isWarningRange/isCriticalRange "
            + "exactly mirror getSeverityLevel across all five bands and every exact boundary")
    void publicPredicatesAreComplementaryToSeverityLevel() {
        // Spans all five bands plus every exact boundary of the systolic fixture
        double[] allBandsAndBoundaries = {
                50.0, 79.0, 80.0, 81.0, 85.0, 89.0, 90.0, 91.0,
                120.0, 139.0, 140.0, 141.0, 150.0, 159.0, 160.0, 161.0, 200.0
        };

        for (double value : allBandsAndBoundaries) {
            String severity = checker.getSeverityLevel(value, systolic);
            assertTrue(severity.equals("NORMAL") == checker.isNormalRange(value, systolic),
                    "isNormalRange must equal severity==NORMAL for value " + value);
            assertTrue(severity.equals("WARNING") == checker.isWarningRange(value, systolic),
                    "isWarningRange must equal severity==WARNING for value " + value);
            assertTrue(severity.equals("CRITICAL") == checker.isCriticalRange(value, systolic),
                    "isCriticalRange must equal severity==CRITICAL for value " + value);
        }
    }
}
