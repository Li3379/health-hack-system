package com.hhs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.HealthProfileMapper;
import com.hhs.mapper.RiskAssessmentMapper;
import com.hhs.service.domain.ProfileCompletenessCalculator;
import com.hhs.service.domain.ProfileFieldCatalog;
import com.hhs.service.impl.RiskAssessmentServiceImpl;
import com.hhs.vo.RiskAssessmentVO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Assumption-disclosure tests for the risk-assessment path.
 *
 * <p>The subject is the real service with its real rule engine, so the mappers are mocked and
 * nothing between the substitution branch and the response is stubbed. Every expected sentence
 * is read from {@link ProfileFieldCatalog} rather than restated here: a reworded catalog entry
 * must fail these tests instead of silently shipping a second copy of the sentence.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("风险评估路径假设披露测试")
class RiskAssessmentAssumptionsTest {

    @Mock
    private RiskAssessmentMapper riskAssessmentMapper;

    @Mock
    private HealthProfileMapper healthProfileMapper;

    @Mock
    private HealthMetricMapper healthMetricMapper;

    private RiskAssessmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RiskAssessmentServiceImpl(riskAssessmentMapper, healthProfileMapper,
                healthMetricMapper, new ProfileCompletenessCalculator(new ProfileFieldCatalog()));
    }

    @Test
    @DisplayName("无档案：固定 30/25/25 且只披露一条通用假设，完整度 0")
    void nullProfileReturnsFixedScoresAndOneGenericAssumption() {
        stubMissingProfile();
        stubNoMetrics();

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        assertEquals(3, vos.size());
        assertEquals(List.of("糖尿病", "高血压", "心血管疾病"),
                vos.stream().map(RiskAssessmentVO::diseaseName).toList());
        assertEquals(List.of(30, 25, 25), vos.stream().map(RiskAssessmentVO::riskScore).toList());
        for (RiskAssessmentVO vo : vos) {
            assertEquals(0, vo.profileCompleteness());
            assertEquals(List.of(ProfileFieldCatalog.NO_PROFILE_ASSUMPTION), vo.assumptions());
        }
    }

    @Test
    @DisplayName("无档案：披露列表与档案目录同源，逐字相等")
    void nullProfileAssumptionsMatchCatalogSource() {
        stubMissingProfile();
        stubNoMetrics();

        List<String> disclosed = service.createAssessment(1L).get(0).assumptions();

        assertEquals(ProfileFieldCatalog.assumptionsFor(null), disclosed,
                "the create path must disclose the catalog's own list, not a second literal");
    }

    @Test
    @DisplayName("无档案：假设不提血糖或血压")
    void nullProfileAssumptionNeverClaimsLabData() {
        stubMissingProfile();
        stubNoMetrics();

        for (RiskAssessmentVO vo : service.createAssessment(1L)) {
            List<String> assumptions = vo.assumptions();
            assertNotNull(assumptions);
            for (String assumption : assumptions) {
                assertFalse(assumption.contains("血糖") || assumption.contains("血压"),
                        "an assumption must never claim lab data was used: " + assumption);
            }
        }
    }

    @Test
    @DisplayName("无档案：三条评估共享同一份非空披露列表")
    void nullProfileSharesSingleDisclosureOnEveryRow() {
        stubMissingProfile();
        stubNoMetrics();

        List<RiskAssessmentVO> vos = service.createAssessment(1L);

        assertNotNull(vos.get(0).assumptions());
        assertEquals(vos.get(0).assumptions(), vos.get(1).assumptions());
        assertEquals(vos.get(0).assumptions(), vos.get(2).assumptions());
    }

    private void stubMissingProfile() {
        when(healthProfileMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
    }

    private void stubNoMetrics() {
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.<HealthMetric>of());
    }
}