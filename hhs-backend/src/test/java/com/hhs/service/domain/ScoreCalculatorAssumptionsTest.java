package com.hhs.service.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.ExaminationReport;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.HealthProfile;
import com.hhs.entity.RealtimeMetric;
import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.ExaminationReportMapper;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.HealthProfileMapper;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.mapper.RiskAssessmentMapper;
import com.hhs.vo.HealthScoreVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Assumption-disclosure tests for the score path.
 *
 * <p>The assertion subject is real aggregation, so the mappers are mocked and every scorer is
 * constructed by hand. Mocking the scorers would only prove that a stub was called.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("评分路径假设披露测试")
class ScoreCalculatorAssumptionsTest {

    private static final String NO_PROFILE = "未填写健康档案，风险得分按通用参考值估算";
    private static final String NO_METRICS = "缺少健康指标数据，指标维度按中性 70 分计算";
    private static final String NO_BMI_METRIC = "缺少 BMI 指标，体重维度按参考值估算";

    @Mock
    private HealthProfileMapper healthProfileMapper;

    @Mock
    private RealtimeMetricMapper realtimeMetricMapper;

    @Mock
    private RiskAssessmentMapper riskAssessmentMapper;

    @Mock
    private ExaminationReportMapper examinationReportMapper;

    @Mock
    private HealthMetricMapper healthMetricMapper;

    private ScoreCalculator scoreCalculator;
    private ProfileScorer profileScorer;

    @BeforeEach
    void setUp() {
        profileScorer = new ProfileScorer(healthProfileMapper);
        MetricsScorer metricsScorer = new MetricsScorer(realtimeMetricMapper);
        RiskScorer riskScorer = new RiskScorer(riskAssessmentMapper);
        ScreeningScorer screeningScorer = new ScreeningScorer(examinationReportMapper);
        WellnessScorer wellnessScorer = new WellnessScorer(healthMetricMapper);
        DimensionScoreCalculator dimensionScoreCalculator =
                new DimensionScoreCalculator(realtimeMetricMapper, wellnessScorer);
        ProfileCompletenessCalculator profileCompletenessCalculator =
                new ProfileCompletenessCalculator(new ProfileFieldCatalog());

        scoreCalculator = new ScoreCalculator(profileScorer, metricsScorer, riskScorer,
                screeningScorer, wellnessScorer, dimensionScoreCalculator,
                profileCompletenessCalculator);
    }

    @Test
    @DisplayName("无档案用户：披露通用参考值一条假设，完整度 0")
    void emptyProfileDisclosesGenericReferenceValue() {
        stubNoProfile();
        stubNoMetrics();
        stubNoRiskAssessment();
        stubNoScreeningReport();
        stubNoWellnessMetrics();

        HealthScoreVO vo = scoreCalculator.calculate(1L, new AssumptionCollector());

        List<String> assumptions = vo.getAssumptions();
        assertNotNull(assumptions);
        assertTrue(assumptions.contains(NO_PROFILE),
                "expected the generic-reference-value sentence, got " + assumptions);
        assertEquals(0, vo.getProfileCompleteness());
    }

    @Test
    @DisplayName("假设列表永不为 null 且不提血糖或血压")
    void assumptionsAreNeverNullAndNeverClaimLabData() {
        stubNoProfile();
        stubNoMetrics();
        stubNoRiskAssessment();
        stubNoScreeningReport();
        stubNoWellnessMetrics();

        List<String> assumptions = scoreCalculator.calculate(1L, new AssumptionCollector())
                .getAssumptions();

        assertNotNull(assumptions);
        assertFalse(assumptions.isEmpty());
        for (String assumption : assumptions) {
            assertFalse(assumption.contains("血糖") || assumption.contains("血压"),
                    "an assumption must never claim lab data was used: " + assumption);
        }
    }

    @Test
    @DisplayName("旧签名无档案用户仍返回 70 分中性值")
    void legacySignatureKeepsNeutralProfileScore() {
        stubNoProfile();

        assertEquals(70, profileScorer.calculate(1L));
    }

    @Test
    @DisplayName("传入 null 收集器不抛异常")
    void nullCollectorDoesNotThrow() {
        stubNoProfile();
        stubNoMetrics();
        stubNoRiskAssessment();
        stubNoScreeningReport();
        stubNoWellnessMetrics();

        HealthScoreVO vo = scoreCalculator.calculate(1L, null);

        assertNotNull(vo.getAssumptions());
        assertTrue(vo.getAssumptions().isEmpty());
    }

    @Test
    @DisplayName("有指标但无 BMI：披露体重维度参考值，且不重复报缺指标")
    void metricsWithoutBmiDiscloseWeightReferenceValue() {
        stubNoProfile();
        stubMetrics(metric("heartRate", "72"));
        stubNoRiskAssessment();
        stubNoScreeningReport();
        stubNoWellnessMetrics();

        List<String> assumptions = scoreCalculator.calculate(1L, new AssumptionCollector())
                .getAssumptions();

        assertTrue(assumptions.contains(NO_BMI_METRIC),
                "expected the missing-BMI sentence, got " + assumptions);
        assertFalse(assumptions.contains(NO_METRICS),
                "empty-metrics sentence must not appear when metrics exist: " + assumptions);
    }

    @Test
    @DisplayName("无指标：只披露缺指标，不额外报缺 BMI")
    void emptyMetricsDiscloseOnlyTheMetricsSentence() {
        stubNoProfile();
        stubNoMetrics();
        stubNoRiskAssessment();
        stubNoScreeningReport();
        stubNoWellnessMetrics();

        List<String> assumptions = scoreCalculator.calculate(1L, new AssumptionCollector())
                .getAssumptions();

        assertTrue(assumptions.contains(NO_METRICS),
                "expected the empty-metrics sentence, got " + assumptions);
        assertFalse(assumptions.contains(NO_BMI_METRIC),
                "BMI sentence must not double-report the empty metric set: " + assumptions);
    }

    @Test
    @DisplayName("指标含 BMI：两条评分路径假设都不出现")
    void metricsWithBmiDiscloseNeitherSentence() {
        stubNoProfile();
        stubMetrics(metric("bmi", "22.0"));
        stubNoRiskAssessment();
        stubNoScreeningReport();
        stubNoWellnessMetrics();

        List<String> assumptions = scoreCalculator.calculate(1L, new AssumptionCollector())
                .getAssumptions();

        assertFalse(assumptions.contains(NO_BMI_METRIC),
                "BMI sentence must not appear when the metric exists: " + assumptions);
        assertFalse(assumptions.contains(NO_METRICS),
                "empty-metrics sentence must not appear when metrics exist: " + assumptions);
    }

    @Test
    @DisplayName("档案齐全且有指标：假设列表为空，完整度 100")
    void fullyPopulatedUserDisclosesNothing() {
        stubCompleteProfile();
        stubMetrics(metric("bmi", "22.0"));
        stubNoRiskAssessment();
        stubNoScreeningReport();
        stubNoWellnessMetrics();

        HealthScoreVO vo = scoreCalculator.calculate(1L, new AssumptionCollector());

        assertNotNull(vo.getAssumptions());
        assertTrue(vo.getAssumptions().isEmpty(),
                "a fully populated user must disclose nothing, got " + vo.getAssumptions());
        assertEquals(100, vo.getProfileCompleteness());
    }

    @Test
    @DisplayName("收集器对同一句去重，按插入顺序保留一条")
    void collectorDeduplicatesRepeatedSentences() {
        AssumptionCollector collector = new AssumptionCollector();
        collector.add(NO_BMI_METRIC);
        collector.add(NO_BMI_METRIC);

        assertEquals(1, collector.toList().size());
        assertEquals(NO_BMI_METRIC, collector.toList().get(0));
    }

    @Test
    @DisplayName("收集器忽略空白与 null 输入")
    void collectorIgnoresBlankInput() {
        AssumptionCollector collector = new AssumptionCollector();
        collector.add(null);
        collector.add("");
        collector.add("   ");

        assertTrue(collector.isEmpty());
        assertTrue(collector.toList().isEmpty());
    }

    private RealtimeMetric metric(String key, String value) {
        RealtimeMetric metric = new RealtimeMetric();
        metric.setMetricKey(key);
        metric.setValue(new BigDecimal(value));
        return metric;
    }

    private HealthProfile completeProfile() {
        HealthProfile profile = new HealthProfile();
        profile.setGender("FEMALE");
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        profile.setHeightCm(new BigDecimal("165"));
        profile.setWeightKg(new BigDecimal("55"));
        profile.setBmi(new BigDecimal("20.2"));
        profile.setBloodType("A");
        profile.setAllergyHistory("青霉素");
        profile.setFamilyHistory("父亲有高血压");
        profile.setLifestyleHabits("每周跑步三次");
        return profile;
    }

    private void stubNoProfile() {
        when(healthProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.<HealthProfile>of());
    }

    private void stubCompleteProfile() {
        when(healthProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(completeProfile()));
    }

    private void stubNoMetrics() {
        when(realtimeMetricMapper.getLatestMetricsByUser(any(Long.class)))
                .thenReturn(List.<RealtimeMetric>of());
    }

    private void stubMetrics(RealtimeMetric... metrics) {
        when(realtimeMetricMapper.getLatestMetricsByUser(any(Long.class)))
                .thenReturn(List.of(metrics));
    }

    private void stubNoRiskAssessment() {
        when(riskAssessmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.<RiskAssessment>of());
    }

    private void stubNoScreeningReport() {
        when(examinationReportMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.<ExaminationReport>of());
    }

    private void stubNoWellnessMetrics() {
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.<HealthMetric>of());
    }
}