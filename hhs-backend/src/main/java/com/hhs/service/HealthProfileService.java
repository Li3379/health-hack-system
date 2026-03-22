package com.hhs.service;

import com.hhs.dto.HealthProfileRequest;
import com.hhs.vo.HealthProfileVO;

/**
 * Health Profile Service
 * Manages user health profiles including creation, retrieval, and updates
 */
public interface HealthProfileService {

    /**
     * Create a new health profile for a user
     *
     * @param userId User ID
     * @param request Health profile request containing gender, birth date, height, weight, etc.
     * @return HealthProfileVO containing the created profile
     * @throws BusinessException if user already has a health profile
     */
    HealthProfileVO create(Long userId, HealthProfileRequest request);

    /**
     * Get health profile by user ID
     *
     * @param userId User ID
     * @return HealthProfileVO if exists, null otherwise
     */
    HealthProfileVO getByUserId(Long userId);

    /**
     * Update an existing health profile
     *
     * @param id Profile ID to update
     * @param userId User ID (for authorization)
     * @param request Health profile request containing updated fields
     * @return HealthProfileVO containing the updated profile
     * @throws BusinessException if profile not found or user not authorized
     */
    HealthProfileVO update(Long id, Long userId, HealthProfileRequest request);
}
