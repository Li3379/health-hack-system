package com.hhs.service;

import com.hhs.dto.HealthProfileRequest;
import com.hhs.vo.HealthProfileVO;
import com.hhs.vo.ProfileCompletenessVO;

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
     * Get the authoritative completeness of a user's health profile.
     *
     * <p>A missing profile row is a normal state, not an error: it yields a 0% payload with
     * {@code hasProfile == false} rather than an exception or {@code null}. Callers therefore
     * never need a null check, and the guidance surfaces can render the 0% case directly.</p>
     *
     * @param userId User ID
     * @return the completeness payload; never {@code null}
     */
    ProfileCompletenessVO getCompleteness(Long userId);

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
