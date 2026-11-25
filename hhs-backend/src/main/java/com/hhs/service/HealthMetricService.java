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
     * <p>This is the only delete entry point: the unchecked {@code delete(Long id)} overload used
     * to sit on this interface and {@code DELETE /api/metrics/{id}} used it, so any logged-in user
     * could delete another user's metric by guessing the auto-increment id.
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
     * List health metrics with pagination and filters
     *
     * @param page Page number
     * @param size Page size
     * @param userId User ID (required, from JWT)
     * @param metricKey Optional metric key filter
     * @param startDate Optional start date filter (yyyy-MM-dd)
     * @param endDate Optional end date filter (yyyy-MM-dd)
     * @return Paginated health metrics
     */
    Page<HealthMetricVO> list(Integer page, Integer size, Long userId, String metricKey, String startDate, String endDate);

    /**
     * List the most recently created metrics for a user across <em>both</em> categories.
     *
     * <p>The "recent records" surfaces used to call {@code /api/metrics} and {@code /api/wellness}
     * separately and merge in the browser. Both endpoints filter to one category, cap the page at
     * N rows and order by different columns (create_time vs record_date), so ten wellness rows
     * could push a brand-new health row out of the merged list entirely. One query with one
     * ordering removes that truncation.
     *
     * @param userId User ID (required, from JWT)
     * @param limit Maximum number of rows to return; clamped to 1..50
     * @return Recently created metrics, newest first, health and wellness interleaved
     */
    List<HealthMetricVO> listRecent(Long userId, Integer limit);

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
     * <p>{@code userId} is the caller from the JWT and is checked against the row's owner. It is
     * an explicit parameter rather than {@code request.getUserId()} because an authorization
     * decision must not depend on a field the caller fills in — the old signature silently
     * ignored it and any logged-in user could rewrite anyone's metric.
     *
     * @param userId Caller's user ID (from JWT)
     * @param id Metric ID
     * @param request Health metric request
     * @return Updated health metric entity
     * @throws com.hhs.exception.BusinessException if the metric is missing or belongs to someone else
     */
    HealthMetric update(Long userId, Long id, HealthMetricRequest request);
}
