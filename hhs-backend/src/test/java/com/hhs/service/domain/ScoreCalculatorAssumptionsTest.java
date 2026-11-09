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

    private void stubNoProfile() {
        when(healthProfileMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.<HealthProfile>of());
    }

    private void stubNoMetrics() {
        when(realtimeMetricMapper.getLatestMetricsByUser(any(Long.class)))
                .thenReturn(List.<RealtimeMetric>of());
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