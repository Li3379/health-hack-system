package com.hhs.util;

import com.hhs.dto.*;
import com.hhs.entity.*;
import com.hhs.vo.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for creating test data
 */
public class TestDataUtil {

    private TestDataUtil() {}

    /**
     * Create a test health profile request
     */
    public static HealthProfileRequest createTestProfileRequest() {
        HealthProfileRequest request = new HealthProfileRequest();
        request.setGender("male");                                           // gender
        request.setBirthDate(java.time.LocalDate.of(1994, 1, 1));           // birthDate (30 years old)
        request.setHeightCm(new BigDecimal("175"));                         // height
        request.setWeightKg(new BigDecimal("70"));                          // weight
        request.setBloodType("A+");                                          // bloodType
        request.setAllergyHistory("None");                                  // allergyHistory
        request.setFamilyHistory("None");                                   // familyHistory
        request.setLifestyleHabits("Regular exercise, balanced diet");     // lifestyleHabits
        return request;
    }

    /**
     * Create a test health metric request
     */
    public static HealthMetricRequest createTestMetricRequest() {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey("heart_rate");              // metricKey
        request.setValue(new BigDecimal("75.0"));         // value
        request.setUnit("bpm");                           // unit
        request.setRecordDate(java.time.LocalDate.now()); // recordDate
        request.setTrend("normal");                       // trend
        return request;
    }

    /**
     * Create a test real-time metric request
     */
    public static RealtimeMetricRequest createTestRealtimeRequest() {
        RealtimeMetricRequest request = new RealtimeMetricRequest();
        request.setMetricKey("heartRate");
        request.setValue(new BigDecimal("75.0"));
        request.setUnit("次/分");
        request.setSource("manual");
        request.setQualityScore(new BigDecimal("0.95"));
        return request;
    }

    /**
     * Create a test user threshold
     */
    public static UserThreshold createTestUserThreshold(Long userId) {
        UserThreshold threshold = new UserThreshold();
        threshold.setUserId(userId);
        threshold.setMetricKey("heartRate");
        threshold.setWarningLow(new BigDecimal("50"));
        threshold.setCriticalLow(new BigDecimal("40"));
        threshold.setWarningHigh(new BigDecimal("100"));
        threshold.setCriticalHigh(new BigDecimal("120"));
        threshold.setCreatedAt(LocalDateTime.now());
        return threshold;
    }

    /**
     * Create a test health alert
     */
    public static HealthAlert createTestHealthAlert(Long userId) {
        HealthAlert alert = new HealthAlert();
        alert.setUserId(userId);
        alert.setAlertType("WARNING");
        alert.setAlertLevel("MEDIUM");
        alert.setMetricKey("heartRate");
        alert.setCurrentValue(new BigDecimal("110"));
        alert.setThresholdValue(new BigDecimal("100"));
        alert.setTitle("心率异常警告");
        alert.setMessage("您的心率超过了预设阈值");
        alert.setIsRead(false);
        alert.setIsAcknowledged(false);
        alert.setCreatedAt(LocalDateTime.now());
        return alert;
    }

    /**
     * Create a test examination report
     */
    public static ExaminationReport createTestExamReport(Long userId) {
        ExaminationReport report = new ExaminationReport();
        report.setUserId(userId);
        report.setReportName("Annual Health Checkup");
        report.setReportType("ROUTINE");
        report.setInstitution("Test Hospital");
        report.setReportDate(java.time.LocalDate.now());
        report.setFileUrl("/uploads/test-report.pdf");
        report.setOcrStatus("COMPLETED");
        report.setAbnormalSummary("No significant abnormalities found");
        report.setCreateTime(LocalDateTime.now());
        return report;
    }

    /**
     * Create a test risk assessment
     */
    public static RiskAssessment createTestRiskAssessment(Long userId) {
        RiskAssessment assessment = new RiskAssessment();
        assessment.setUserId(userId);
        assessment.setProfileId(1L);
        assessment.setDiseaseName("Hypertension");
        assessment.setRiskLevel("LOW");
        assessment.setRiskScore(20);
        assessment.setSuggestion("Maintain healthy lifestyle and regular monitoring");
        assessment.setCreateTime(LocalDateTime.now());
        return assessment;
    }

    /**
     * Create test JWT headers
     */
    public static Map<String, String> createTestAuthHeaders(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Content-Type", "application/json");
        return headers;
    }
}
