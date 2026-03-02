package com.hhs.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.dto.WellnessMetricRequest;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.HealthMetricVO;
import com.hhs.vo.WellnessSummaryVO;

import java.time.LocalDate;
import java.util.Map;

/**
 * Wellness Service
 * Manages wellness metric data with category-based filtering
 */
public interface WellnessService {

    /**
     * Get wellness metrics with pagination and filters
     *
     * @param userId User ID
     * @param metricKey Filter by metric key (optional)
     * @param startDate Filter by start date (optional)
     * @param endDate Filter by end date (optional)
     * @param page Page number (1-based)
     * @param size Page size
     * @return Paginated wellness metrics
     */
    Page<HealthMetricVO> getWellnessMetrics(Long userId, String metricKey,
                                             LocalDate startDate, LocalDate endDate,
                                             int page, int size);

    /**
     * Create a new wellness metric
     *
     * @param userId User ID
     * @param request Wellness metric request
     * @return Created wellness metric
     */
    HealthMetricVO createWellnessMetric(Long userId, WellnessMetricRequest request);

    /**
     * Update an existing wellness metric
     *
     * @param userId User ID (for authorization)
     * @param metricId Metric ID to update
     * @param request Updated metric data
     * @return Updated wellness metric
     */
    HealthMetricVO updateWellnessMetric(Long userId, Long metricId, WellnessMetricRequest request);

    /**
     * Delete a wellness metric
     *
     * @param userId User ID (for authorization)
     * @param metricId Metric ID to delete
     */
    void deleteWellnessMetric(Long userId, Long metricId);

    /**
     * Get trend data for a specific wellness metric
     *
     * @param userId User ID
     * @param metricKey Metric key to get trend for
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return Trend data for the metric
     */
    HealthMetricTrendVO getTrend(Long userId, String metricKey,
                                  LocalDate startDate, LocalDate endDate);

    /**
     * Get aggregated wellness summary for dashboard
     *
     * @param userId User ID
     * @param days Number of days to include in summary (default 7)
     * @return Aggregated wellness summary
     */
    WellnessSummaryVO getSummary(Long userId, int days);

    /**
     * Get latest values for all wellness metric types
     *
     * @param userId User ID
     * @return Map of metricKey to latest HealthMetricVO
     */
    Map<String, HealthMetricVO> getLatestMetrics(Long userId);
}