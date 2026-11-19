package com.hhs.service.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.RiskAssessmentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pins {@link RiskScorer}'s use of the continuous {@code riskScore}.
 *
 * <p>Previously the scorer mapped {@code riskLevel} to three fixed values. Since the
 * level is itself derived from the score, that round trip destroyed the middle of the
 * range: 5 and 34 both returned 100, 60 and 100 both returned 40. These cases are
 * chosen so a return to the three-band mapping fails immediately — each pair shares a
 * level and differs only in score.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("风险评分 — 使用连续 riskScore 而非三档映射")
class RiskScorerTest {

    @Mock
    private RiskAssessmentMapper riskAssessmentMapper;

    private RiskScorer scorer() {
        return new RiskScorer(riskAssessmentMapper);
    }

    private void stubLatest(int riskScore) {
        RiskAssessment row = new RiskAssessment();
        row.setRiskScore(riskScore);
        when(riskAssessmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(row));
    }

    @Test
    @DisplayName("同属 LOW 的 5 分与 34 分不再返回同一个值")
    void lowBandKeepsContinuum() {
        stubLatest(5);
        assertEquals(95, scorer().calculate(1L));

        stubLatest(34);
        assertEquals(66, scorer().calculate(1L));
    }

    @Test
    @DisplayName("同属 HIGH 的 60 分与 100 分不再返回同一个值")
    void highBandKeepsContinuum() {
        stubLatest(60);
        assertEquals(40, scorer().calculate(1L));

        stubLatest(100);
        assertEquals(0, scorer().calculate(1L));
    }

    @Test
    @DisplayName("边界值 35 / 59 连续可辨")
    void boundaryScoresAreDistinct() {
        stubLatest(35);
        assertEquals(65, scorer().calculate(1L));

        stubLatest(59);
        assertEquals(41, scorer().calculate(1L));
    }

    @Test
    @DisplayName("无评估时保持中性 70 分")
    void noAssessmentIsNeutral() {
        when(riskAssessmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());
        assertEquals(70, scorer().calculate(1L));
    }

    @Test
    @DisplayName("riskScore 为 null 的行回落到中性值，不伪造惩罚")
    void nullScoreIsNeutral() {
        RiskAssessment row = new RiskAssessment();
        row.setRiskScore(null);
        when(riskAssessmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(row));
        assertEquals(70, scorer().calculate(1L));
    }
}
