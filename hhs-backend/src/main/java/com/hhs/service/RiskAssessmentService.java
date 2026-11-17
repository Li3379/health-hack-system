package com.hhs.service;

import com.hhs.vo.MetricCoverageVO;
import com.hhs.vo.RiskAssessmentVO;

import java.util.List;

/**
 * Risk Assessment Service
 * Performs health risk assessments based on user profile and recent metrics
 */
public interface RiskAssessmentService {

    /**
     * Execute risk assessment and persist results, returning multiple assessment records
     *
     * @param userId User ID
     * @return List of risk assessment results (diabetes, hypertension, cardiovascular)
     * @throws BusinessException if user data is insufficient for assessment
     */
    List<RiskAssessmentVO> createAssessment(Long userId);

    /**
     * Get a specific risk assessment by ID
     *
     * @param id Assessment ID
     * @param userId User ID (for authorization)
     * @return Risk assessment result or null if not found
     */
    RiskAssessmentVO getById(Long id, Long userId);

    /**
     * Get all risk assessment records for a user (history)
     *
     * @param userId User ID
     * @return List of risk assessments sorted by creation time (descending)
     */
    List<RiskAssessmentVO> listByUserId(Long userId);

    /**
     * Get the per-metric coverage view for a user.
     *
     * <p>Answers "which of my parameters did the system actually look at, and what did each
     * one say" — the question a bare risk score cannot answer. Every metric the registry knows
     * appears exactly once, including the ones with no reading, so a missing measurement is
     * never mistaken for a normal one.</p>
     *
     * @param userId User ID
     * @return one row per registry metric, HEALTH first then WELLNESS
     */
    List<MetricCoverageVO> getMetricCoverage(Long userId);
}
