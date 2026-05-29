package com.hhs.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.dto.UserThresholdRequest;
import com.hhs.entity.UserThreshold;

import java.util.List;

/**
 * User Threshold Service
 * Manages user personalized threshold settings for health metrics
 */
public interface UserThresholdService {

    /**
     * List user thresholds with pagination
     *
     * @param page Page number
     * @param size Page size
     * @param userId Optional user ID filter
     * @return Paginated user thresholds
     */
    Page<UserThreshold> list(Integer page, Integer size, Long userId);

    /**
     * Create a new user threshold
     *
     * @param userId User ID (enforced ownership)
     * @param request User threshold request
     * @return Created user threshold
     */
    UserThreshold create(Long userId, UserThresholdRequest request);

    /**
     * Update an existing user threshold
     *
     * @param id Threshold ID
     * @param userId User ID (enforced ownership)
     * @param request User threshold request
     * @return Updated user threshold
     */
    UserThreshold update(Long id, Long userId, UserThresholdRequest request);

    /**
     * Delete a user threshold
     *
     * @param id Threshold ID
     * @param userId User ID (enforced ownership)
     */
    void delete(Long id, Long userId);

    /**
     * Get threshold by user ID
     *
     * @param userId User ID
     * @return List of thresholds for the user
     */
    List<UserThreshold> getByUserId(Long userId);

    /**
     * Get threshold by user ID and metric key
     *
     * @param userId User ID
     * @param metricKey Metric key
     * @return User threshold if found
     */
    UserThreshold getByUserAndMetricKey(Long userId, String metricKey);
}
