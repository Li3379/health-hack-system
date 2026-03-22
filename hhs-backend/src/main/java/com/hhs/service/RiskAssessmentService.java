package com.hhs.service;

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
}
