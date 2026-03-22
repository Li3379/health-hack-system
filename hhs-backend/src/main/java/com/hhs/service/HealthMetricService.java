package com.hhs.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.HealthMetricVO;

import java.time.LocalDate;
import java.util.List;

/**
 * Health Metric Service
 * Manages health metric data including adding, listing, trending, and deleting metrics
 */
public interface HealthMetricService {

    /**
     * Add a new health metric record for a user
     *
     * @param userId User ID
     * @param request Health metric request containing metric type, value, unit, record date
     * @return HealthMetricVO containing the created metric
     * @throws BusinessException if validation fails
     */
    HealthMetricVO add(Long userId, HealthMetricRequest request);

    /**
     * List health metrics with optional filters
     *
     * @param userId User ID
     * @param metricKey Filter by metric key (e.g., "血糖", "血压")
     * @param startDate Filter by start date (inclusive)
     * @param endDate Filter by end date (inclusive)
     * @return List of HealthMetricVO matching the criteria
     */
    List<HealthMetricVO> list(Long userId, String metricKey, LocalDate startDate, LocalDate endDate);

    /**
     * Get trend data for a specific metric type within a date range
     *
     * @param userId User ID
     * @param metricKey Metric key to get trend for
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of HealthMetricTrendVO containing dates and values
     */
    List<HealthMetricTrendVO> getTrend(Long userId, String metricKey, LocalDate startDate, LocalDate endDate);

    /**
     * Delete a health metric record
     *
     * @param userId User ID (for authorization)
     * @param id Metric ID to delete
     * @return true if deletion successful, true if record not found (idempotent)
     * @throws BusinessException if user is not authorized to delete the record
     */
    boolean delete(Long userId, Long id);

    // ========================================
    // Simplified CRUD methods (direct Mapper injection pattern)
    // ========================================

    /**
     * List health metrics with pagination
     *
     * @param page Page number
     * @param size Page size
     * @param userId Optional user ID filter
     * @return Paginated health metrics
     */
    Page<HealthMetricVO> list(Integer page, Integer size, Long userId);

    /**
     * Create a new health metric
     *
     * @param request Health metric request
     * @return Created health metric entity
     */
    HealthMetric create(HealthMetricRequest request);

    /**
     * Update an existing health metric
     *
     * @param id Metric ID
     * @param request Health metric request
     * @return Updated health metric entity
     */
    HealthMetric update(Long id, HealthMetricRequest request);

    /**
     * Delete a health metric by ID
     *
     * @param id Metric ID to delete
     */
    void delete(Long id);
}
