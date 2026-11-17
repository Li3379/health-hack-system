package com.hhs.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Risk assessment result with the disclosure fields for the defaults it relied on.
 *
 * <p>{@code profileCompleteness} and {@code assumptions} were appended by Phase 13. The first
 * eight components keep their original names and order, so every existing JSON consumer keeps
 * working; {@code assumptions} is last and {@code createTime} stays second-to-last.</p>
 *
 * @param id                 assessment row ID, {@code null} on a not-yet-persisted result
 * @param userId             owner of the assessment
 * @param profileId          profile the assessment was built from, {@code null} when absent
 * @param diseaseName        assessed disease name
 * @param riskLevel          {@code HIGH}, {@code MEDIUM} or {@code LOW}
 * @param riskScore          risk score in 0..100
 * @param suggestion         user-facing advice for this level
 * @param profileCompleteness current profile completeness in 0..100
 * @param createTime         row creation timestamp
 * @param assumptions        defaults substituted for this assessment; never {@code null}
 * @param contributors       scored inputs whose points sum to {@code riskScore}; never {@code null}
 */
public record RiskAssessmentVO(
        Long id,
        Long userId,
        Long profileId,
        String batchId,
        String diseaseName,
        String riskLevel,
        Integer riskScore,
        String suggestion,
        Integer profileCompleteness,
        LocalDateTime createTime,
        List<String> assumptions,
        List<RiskContributorVO> contributors
) {
}