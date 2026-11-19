package com.hhs.service.domain;

import com.hhs.entity.RiskAssessment;
import com.hhs.mapper.RiskAssessmentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Risk scorer - handles risk assessment scoring
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskScorer {

    private final RiskAssessmentMapper riskAssessmentMapper;

    /**
     * Calculate the risk dimension's contribution to the overall health score.
     *
     * <p>Reads {@code riskScore} directly instead of mapping {@code riskLevel}. The
     * level is itself derived from the score (>=60 HIGH, >=35 MEDIUM), so mapping it
     * back collapsed the whole range onto three values: a user at 5 and a user at 34
     * both returned 100, and 60 and 100 both returned 40. Inverting the score keeps
     * the continuum the assessment actually produced.</p>
     *
     * @param userId the user ID
     * @return score from 0-100; 70 when the user has no assessment (neutral)
     */
    public int calculate(Long userId) {
        List<RiskAssessment> risks = riskAssessmentMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RiskAssessment>()
                        .eq(RiskAssessment::getUserId, userId)
                        .orderByDesc(RiskAssessment::getCreateTime)
                        .last("LIMIT 1")
        );

        if (risks.isEmpty()) {
            return 70;
        }

        RiskAssessment risk = risks.get(0);
        Integer score = risk.getRiskScore();
        if (score == null) {
            // A row with no score cannot be inverted; fall back to the neutral value
            // rather than inventing a penalty.
            return 70;
        }
        return 100 - Math.max(0, Math.min(100, score));
    }
}
