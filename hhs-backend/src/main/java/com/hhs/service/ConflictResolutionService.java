package com.hhs.service;

import com.hhs.common.enums.ResolutionStrategy;
import com.hhs.dto.ConflictResult;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.entity.HealthMetric;

import java.time.LocalDate;

/**
 * Conflict Resolution Service
 *
 * <p>Handles conflict resolution when syncing health metrics from multiple devices or sources.
 * When duplicate entries exist for the same user, metric type, and date, this service
 * determines how to resolve the conflict based on configurable strategies.
 *
 * <p>Conflict resolution is essential for:
 * <ul>
 *   <li>Multi-device sync scenarios (phone, watch, tablet)</li>
 *   <li>Manual entry reconciliation with device data</li>
 *   <li>API integration with third-party health platforms</li>
 * </ul>
 *
 * <p>Default strategies are configured per metric type:
 * <ul>
 *   <li>steps, activeMinutes -> KEEP_HIGHEST (device may have more accurate count)</li>
 *   <li>blood pressure, glucose -> KEEP_NEWEST (latest measurement is most relevant)</li>
 *   <li>sleep duration -> KEEP_NEWEST (most recent record)</li>
 * </ul>
 */
public interface ConflictResolutionService {

    /**
     * Find an existing metric for the same user, metricKey, and recordDate.
     *
     * <p>This method checks for duplicate entries that would cause a conflict
     * during metric creation or sync operations.
     *
     * @param userId The user ID to search for
     * @param metricKey The metric key (e.g., "glucose", "steps", "systolicBP")
     * @param recordDate The record date to match
     * @return The existing HealthMetric if found, null otherwise
     */
    HealthMetric findExisting(Long userId, String metricKey, LocalDate recordDate);

    /**
     * Resolve a conflict between existing and incoming metric requests.
     *
     * <p>Applies the specified resolution strategy to determine which value to keep.
     * This method does not modify any data; it only returns the resolved request.
     *
     * @param existing The existing metric request (converted from stored entity)
     * @param incoming The incoming metric request from sync
     * @param strategy The resolution strategy to apply
     * @return The resolved HealthMetricRequest
     */
    HealthMetricRequest resolve(HealthMetricRequest existing, HealthMetricRequest incoming, ResolutionStrategy strategy);

    /**
     * Detect and resolve a conflict for an incoming metric.
     *
     * <p>This is the main entry point for conflict resolution. It:
     * <ol>
     *   <li>Checks if an existing metric exists for the same user, metricKey, and date</li>
     *   <li>If no conflict, returns the incoming request unchanged</li>
     *   <li>If conflict exists, applies the specified strategy and returns the resolved request</li>
     * </ol>
     *
     * @param userId The user ID for the incoming metric
     * @param incoming The incoming metric request
     * @param strategy The resolution strategy to apply (null uses default for metric type)
     * @return ConflictResult containing the resolution outcome
     */
    ConflictResult resolveConflict(Long userId, HealthMetricRequest incoming, ResolutionStrategy strategy);

    /**
     * Get the default resolution strategy for a metric type.
     *
     * <p>Default strategies are configured based on metric characteristics:
     * <ul>
     *   <li>Activity metrics (steps, activeMinutes) -> KEEP_HIGHEST</li>
     *   <li>Medical metrics (blood pressure, glucose) -> KEEP_NEWEST</li>
     *   <li>Sleep metrics -> KEEP_NEWEST</li>
     *   <li>Default -> KEEP_NEWEST</li>
     * </ul>
     *
     * @param metricKey The metric key to get the default strategy for
     * @return The default ResolutionStrategy for this metric type
     */
    ResolutionStrategy getDefaultStrategy(String metricKey);

    /**
     * Set a custom default strategy for a metric type.
     *
     * <p>This allows runtime configuration of resolution strategies
     * without code changes. Strategies are persisted in memory and
     * override the built-in defaults.
     *
     * @param metricKey The metric key to configure
     * @param strategy The default strategy to use
     */
    void setDefaultStrategy(String metricKey, ResolutionStrategy strategy);
}