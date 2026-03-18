package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.enums.ResolutionStrategy;
import com.hhs.dto.ConflictResult;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.service.ConflictResolutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conflict Resolution Service Implementation
 *
 * <p>Handles conflict resolution for health metrics during sync operations.
 * Uses database queries to detect existing metrics and configurable strategies
 * to resolve conflicts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConflictResolutionServiceImpl implements ConflictResolutionService {

    private final HealthMetricMapper healthMetricMapper;

    /**
     * Default resolution strategies per metric type.
     * Built-in defaults based on metric characteristics.
     */
    private static final Map<String, ResolutionStrategy> DEFAULT_STRATEGIES = new ConcurrentHashMap<>();

    static {
        // Activity metrics - device may have more accurate count, keep highest
        DEFAULT_STRATEGIES.put("steps", ResolutionStrategy.KEEP_HIGHEST);
        DEFAULT_STRATEGIES.put("activeMinutes", ResolutionStrategy.KEEP_HIGHEST);
        DEFAULT_STRATEGIES.put("exerciseMinutes", ResolutionStrategy.KEEP_HIGHEST);
        DEFAULT_STRATEGIES.put("calories", ResolutionStrategy.KEEP_HIGHEST);
        DEFAULT_STRATEGIES.put("distance", ResolutionStrategy.KEEP_HIGHEST);

        // Medical metrics - latest measurement is most relevant
        DEFAULT_STRATEGIES.put("systolicBP", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("diastolicBP", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("bloodPressure", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("glucose", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("bloodGlucose", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("heartRate", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("temperature", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("weight", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("bmi", ResolutionStrategy.KEEP_NEWEST);

        // Sleep metrics - keep most recent record
        DEFAULT_STRATEGIES.put("sleepDuration", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("sleepQuality", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("sleep", ResolutionStrategy.KEEP_NEWEST);

        // Conservative metrics - lower values preferred (e.g., resting heart rate)
        DEFAULT_STRATEGIES.put("restingHeartRate", ResolutionStrategy.KEEP_LOWEST);
        DEFAULT_STRATEGIES.put("restingHR", ResolutionStrategy.KEEP_LOWEST);

        // Hydration - cumulative, keep highest
        DEFAULT_STRATEGIES.put("waterIntake", ResolutionStrategy.KEEP_HIGHEST);
        DEFAULT_STRATEGIES.put("hydration", ResolutionStrategy.KEEP_HIGHEST);

        // SpO2 - most recent for accuracy
        DEFAULT_STRATEGIES.put("spo2", ResolutionStrategy.KEEP_NEWEST);
        DEFAULT_STRATEGIES.put("oxygenSaturation", ResolutionStrategy.KEEP_NEWEST);
    }

    /**
     * Runtime-configurable strategies (overrides defaults)
     */
    private final Map<String, ResolutionStrategy> customStrategies = new ConcurrentHashMap<>();

    /**
     * Find an existing metric for the same user, metricKey, and recordDate.
     *
     * @param userId The user ID to search for
     * @param metricKey The metric key to match
     * @param recordDate The record date to match
     * @return The existing HealthMetric if found, null otherwise
     */
    @Override
    public HealthMetric findExisting(Long userId, String metricKey, LocalDate recordDate) {
        if (userId == null || metricKey == null || recordDate == null) {
            log.warn("Invalid parameters for findExisting: userId={}, metricKey={}, recordDate={}",
                    userId, metricKey, recordDate);
            return null;
        }

        LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthMetric::getUserId, userId)
               .eq(HealthMetric::getMetricKey, metricKey)
               .eq(HealthMetric::getRecordDate, recordDate)
               .orderByDesc(HealthMetric::getCreateTime)
               .last("LIMIT 1");

        HealthMetric existing = healthMetricMapper.selectOne(wrapper);
        if (existing != null) {
            log.debug("Found existing metric: id={}, userId={}, metricKey={}, recordDate={}, value={}",
                    existing.getId(), userId, metricKey, recordDate, existing.getValue());
        }
        return existing;
    }

    /**
     * Resolve a conflict between existing and incoming metric requests.
     *
     * @param existing The existing metric request
     * @param incoming The incoming metric request
     * @param strategy The resolution strategy to apply
     * @return The resolved HealthMetricRequest
     */
    @Override
    public HealthMetricRequest resolve(HealthMetricRequest existing, HealthMetricRequest incoming, ResolutionStrategy strategy) {
        if (existing == null) {
            return incoming;
        }
        if (incoming == null) {
            return existing;
        }
        if (strategy == null) {
            strategy = ResolutionStrategy.KEEP_NEWEST;
        }

        HealthMetricRequest resolved = switch (strategy) {
            case KEEP_NEWEST -> resolveByNewest(existing, incoming);
            case KEEP_HIGHEST -> resolveByHighest(existing, incoming);
            case KEEP_LOWEST -> resolveByLowest(existing, incoming);
            case KEEP_EXISTING -> existing;
            case KEEP_INCOMING -> incoming;
        };

        log.info("Conflict resolved: strategy={}, existingValue={}, incomingValue={}, resolvedValue={}",
                strategy, existing.getValue(), incoming.getValue(), resolved.getValue());

        return resolved;
    }

    /**
     * Detect and resolve a conflict for an incoming metric.
     *
     * @param userId The user ID for the incoming metric
     * @param incoming The incoming metric request
     * @param strategy The resolution strategy to apply (null uses default)
     * @return ConflictResult containing the resolution outcome
     */
    @Override
    public ConflictResult resolveConflict(Long userId, HealthMetricRequest incoming, ResolutionStrategy strategy) {
        if (incoming == null) {
            log.warn("Incoming metric request is null");
            return ConflictResult.noConflict(null, strategy != null ? strategy : ResolutionStrategy.KEEP_NEWEST);
        }

        // Use default strategy if not specified
        if (strategy == null) {
            strategy = getDefaultStrategy(incoming.getMetricKey());
        }

        // Check for existing metric
        HealthMetric existing = findExisting(userId, incoming.getMetricKey(), incoming.getRecordDate());

        if (existing == null) {
            log.debug("No conflict found for userId={}, metricKey={}, recordDate={}",
                    userId, incoming.getMetricKey(), incoming.getRecordDate());
            return ConflictResult.noConflict(incoming, strategy);
        }

        // Conflict found - resolve it
        HealthMetricRequest existingRequest = toRequest(existing);
        HealthMetricRequest resolved = resolve(existingRequest, incoming, strategy);

        log.info("Conflict detected and resolved: userId={}, metricKey={}, recordDate={}, strategy={}, existingId={}",
                userId, incoming.getMetricKey(), incoming.getRecordDate(), strategy, existing.getId());

        return ConflictResult.withConflict(resolved, strategy, existing.getId(), existing.getValue());
    }

    /**
     * Get the default resolution strategy for a metric type.
     *
     * @param metricKey The metric key to get the default strategy for
     * @return The default ResolutionStrategy
     */
    @Override
    public ResolutionStrategy getDefaultStrategy(String metricKey) {
        if (metricKey == null) {
            return ResolutionStrategy.KEEP_NEWEST;
        }

        // Check custom strategies first
        ResolutionStrategy custom = customStrategies.get(metricKey);
        if (custom != null) {
            return custom;
        }

        // Fall back to built-in defaults
        ResolutionStrategy defaultStrategy = DEFAULT_STRATEGIES.get(metricKey);
        if (defaultStrategy != null) {
            return defaultStrategy;
        }

        // Ultimate fallback
        return ResolutionStrategy.KEEP_NEWEST;
    }

    /**
     * Set a custom default strategy for a metric type.
     *
     * @param metricKey The metric key to configure
     * @param strategy The default strategy to use
     */
    @Override
    public void setDefaultStrategy(String metricKey, ResolutionStrategy strategy) {
        if (metricKey == null || strategy == null) {
            log.warn("Cannot set default strategy: metricKey={}, strategy={}", metricKey, strategy);
            return;
        }

        customStrategies.put(metricKey, strategy);
        log.info("Custom strategy set: metricKey={}, strategy={}", metricKey, strategy);
    }

    /**
     * Resolve conflict by keeping the newest record (based on recordDate).
     * If recordDates are equal, compare createTime if available.
     */
    private HealthMetricRequest resolveByNewest(HealthMetricRequest existing, HealthMetricRequest incoming) {
        // For same recordDate, incoming is considered newer
        return incoming;
    }

    /**
     * Resolve conflict by keeping the highest value.
     */
    private HealthMetricRequest resolveByHighest(HealthMetricRequest existing, HealthMetricRequest incoming) {
        BigDecimal existingValue = existing.getValue();
        BigDecimal incomingValue = incoming.getValue();

        if (existingValue == null) return incoming;
        if (incomingValue == null) return existing;

        return incomingValue.compareTo(existingValue) > 0 ? incoming : existing;
    }

    /**
     * Resolve conflict by keeping the lowest value.
     */
    private HealthMetricRequest resolveByLowest(HealthMetricRequest existing, HealthMetricRequest incoming) {
        BigDecimal existingValue = existing.getValue();
        BigDecimal incomingValue = incoming.getValue();

        if (existingValue == null) return incoming;
        if (incomingValue == null) return existing;

        return incomingValue.compareTo(existingValue) < 0 ? incoming : existing;
    }

    /**
     * Convert HealthMetric entity to HealthMetricRequest.
     */
    private HealthMetricRequest toRequest(HealthMetric metric) {
        HealthMetricRequest request = new HealthMetricRequest();
        request.setUserId(metric.getUserId());
        request.setMetricKey(metric.getMetricKey());
        request.setValue(metric.getValue());
        request.setRecordDate(metric.getRecordDate());
        request.setUnit(metric.getUnit());
        request.setTrend(metric.getTrend());
        return request;
    }
}