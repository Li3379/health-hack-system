package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.domain.event.MetricRecordedEvent;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.exception.BusinessException;
import com.hhs.common.constant.ErrorCode;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.HealthMetricService;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.HealthMetricVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HealthMetricServiceImpl implements HealthMetricService {

    private final HealthMetricMapper healthMetricMapper;
    private final RealtimeMetricMapper realtimeMetricMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final com.hhs.service.domain.MetricDisplayFormatter metricDisplayFormatter;
    private final com.hhs.service.domain.MetricCategoryService metricCategoryService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Add a new health metric record for a user
     *
     * @param userId User ID
     * @param request Health metric request containing metric type, value, unit, record date
     * @return HealthMetricVO containing the created metric
     * @throws BusinessException if validation fails
     */
    @Override
    @Transactional(timeout = 30)
    @CacheEvict(value = {"metrics:latest", "metrics:latest:all"}, allEntries = true)
    public HealthMetricVO add(Long userId, HealthMetricRequest request) {
        // Auto-correct unit based on metric key
        String normalizedUnit = normalizeUnit(request.getMetricKey(), request.getUnit());

        // Auto-assign category based on metric key
        com.hhs.common.enums.MetricCategory category = metricCategoryService.determineCategory(request.getMetricKey());

        HealthMetric e = new HealthMetric();
        e.setUserId(userId);
        e.setProfileId(null);
        e.setMetricKey(request.getMetricKey());
        e.setValue(request.getValue());
        e.setUnit(normalizedUnit);
        e.setRecordDate(request.getRecordDate());
        e.setTrend(request.getTrend() != null ? request.getTrend() : "NORMAL");
        e.setCategory(category);
        healthMetricMapper.insert(e);

        // Publish event for alert generation and cache invalidation
        com.hhs.entity.RealtimeMetric realtimeMetric = toRealtimeMetric(e);
        eventPublisher.publishEvent(new MetricRecordedEvent(userId, realtimeMetric));

        log.info("Metric recorded for user: {}, key: {}, value: {}, category: {}", userId, e.getMetricKey(), e.getValue(), category);
        return toVO(e);
    }

    /**
     * Normalize unit based on metric key - ensures consistent units
     */
    private String normalizeUnit(String metricKey, String providedUnit) {
        String standardUnit = metricDisplayFormatter.getUnit(metricKey);
        if (providedUnit == null || providedUnit.isBlank()) {
            return standardUnit;
        }
        // Allow user-provided unit but log warning if different from standard
        if (!providedUnit.equals(standardUnit) && !standardUnit.isEmpty()) {
            log.warn("User provided unit '{}' for metric '{}', standard unit is '{}'. Using provided unit.",
                    providedUnit, metricKey, standardUnit);
        }
        return providedUnit;
    }

    // Helper method to convert HealthMetric to RealtimeMetric for event publishing
    private com.hhs.entity.RealtimeMetric toRealtimeMetric(HealthMetric metric) {
        com.hhs.entity.RealtimeMetric rt = new com.hhs.entity.RealtimeMetric();
        rt.setUserId(metric.getUserId());
        rt.setMetricKey(metric.getMetricKey());
        rt.setValue(metric.getValue());
        rt.setUnit(metric.getUnit());
        rt.setSource("manual");
        rt.setCreatedAt(metric.getRecordDate().atStartOfDay());
        return rt;
    }

    /**
     * List health metrics with optional filters
     *
     * @param userId User ID
     * @param metricKey Filter by metric key (e.g., "血糖", "血压")
     * @param startDate Filter by start date (inclusive)
     * @param endDate Filter by end date (inclusive)
     * @return List of HealthMetricVO matching the criteria
     */
    @Override
    public List<HealthMetricVO> list(Long userId, String metricKey, LocalDate startDate, LocalDate endDate) {
        // Use mapper for data access - filter by user ID
        LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthMetric::getUserId, userId);
        if (metricKey != null && !metricKey.isBlank()) {
            wrapper.eq(HealthMetric::getMetricKey, metricKey);
        }
        if (startDate != null) {
            wrapper.ge(HealthMetric::getRecordDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(HealthMetric::getRecordDate, endDate);
        }
        wrapper.orderByDesc(HealthMetric::getRecordDate);

        return healthMetricMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * Get trend data for a specific metric type within a date range
     *
     * @param userId User ID
     * @param metricKey Metric key to get trend for
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @return List of HealthMetricTrendVO containing dates and values
     */
    @Override
    public List<HealthMetricTrendVO> getTrend(Long userId, String metricKey, LocalDate startDate, LocalDate endDate) {
        List<HealthMetricVO> list = list(userId, metricKey, startDate, endDate);
        if (list.isEmpty()) return List.of();

        List<LocalDate> dates = new ArrayList<>();
        List<java.math.BigDecimal> values = new ArrayList<>();
        for (int i = list.size() - 1; i >= 0; i--) {
            HealthMetricVO v = list.get(i);
            dates.add(v.recordDate());
            values.add(v.value());
        }
        String type = metricKey != null && !metricKey.isBlank() ? metricKey : list.get(0).metricKey();
        return List.of(new HealthMetricTrendVO(type, dates, values));
    }

    /**
     * Delete a health metric record
     *
     * @param userId User ID (for authorization)
     * @param id Metric ID to delete
     * @return true if deletion successful, true if record not found (idempotent)
     * @throws BusinessException if user is not authorized to delete the record
     */
    @Override
    @Transactional
    @CacheEvict(value = {"metrics:latest", "metrics:latest:all"}, allEntries = true)
    public boolean delete(Long userId, Long id) {
        // Idempotent: return true if record doesn't exist
        HealthMetric metric = healthMetricMapper.selectById(id);
        if (metric == null) {
            log.info("Metric already deleted or not found: id={}", id);
            return true;
        }
        if (!metric.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权删除他人记录");
        }

        // Cascade delete: also remove from realtime_metric table
        // The realtime metric was created with createdAt = recordDate.atStartOfDay()
        LocalDateTime startTime = metric.getRecordDate().atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        int realtimeDeleted = realtimeMetricMapper.deleteByHealthMetric(
            userId, metric.getMetricKey(), startTime, endTime
        );

        healthMetricMapper.deleteById(id);
        log.info("Metric deleted for user: {}, id: {}, also deleted {} realtime_metric records",
                 userId, id, realtimeDeleted);
        return true;
    }

    private HealthMetricVO toVO(HealthMetric e) {
        return new HealthMetricVO(
                e.getId(), e.getUserId(), e.getProfileId(), e.getMetricKey(),
                metricDisplayFormatter.getDisplayName(e.getMetricKey()),
                e.getValue(), e.getUnit(), e.getRecordDate(), e.getTrend(),
                e.getCategory(), e.getCreateTime()
        );
    }

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
     * @param startDateStr Optional start date filter (yyyy-MM-dd)
     * @param endDateStr Optional end date filter (yyyy-MM-dd)
     * @return Paginated health metrics
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public Page<HealthMetricVO> list(Integer page, Integer size, Long userId,
                                      String metricKey, String startDateStr, String endDateStr) {
        Page<HealthMetric> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<>();

        // Filter by user ID (always required from JWT)
        wrapper.eq(HealthMetric::getUserId, userId);

        // Filter by metric key if provided
        if (metricKey != null && !metricKey.isBlank()) {
            wrapper.eq(HealthMetric::getMetricKey, metricKey);
        }

        // Filter by date range if provided
        if (startDateStr != null && !startDateStr.isBlank()) {
            LocalDate startDate = LocalDate.parse(startDateStr, DATE_FORMATTER);
            wrapper.ge(HealthMetric::getRecordDate, startDate);
        }
        if (endDateStr != null && !endDateStr.isBlank()) {
            LocalDate endDate = LocalDate.parse(endDateStr, DATE_FORMATTER);
            wrapper.le(HealthMetric::getRecordDate, endDate);
        }

        // 只查询 HEALTH 类型的指标（排除 WELLNESS 保健指标）
        wrapper.eq(HealthMetric::getCategory, com.hhs.common.enums.MetricCategory.HEALTH);
        wrapper.orderByDesc(HealthMetric::getCreateTime);

        Page<HealthMetric> result = healthMetricMapper.selectPage(pageParam, wrapper);

        // Convert to VO page
        Page<HealthMetricVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<HealthMetricVO> records = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        voPage.setRecords(records);

        return voPage;
    }

    /**
     * Create a new health metric using direct Mapper injection
     *
     * @param request Health metric request
     * @return Created health metric entity
     */
    @Override
    @Transactional(timeout = 30)
    public HealthMetric create(HealthMetricRequest request) {
        log.info("Creating metric for user: {}", request.getUserId());
        // Auto-correct unit based on metric key
        String normalizedUnit = normalizeUnit(request.getMetricKey(), request.getUnit());

        // Auto-assign category based on metric key
        com.hhs.common.enums.MetricCategory category = metricCategoryService.determineCategory(request.getMetricKey());

        HealthMetric metric = new HealthMetric();
        BeanUtils.copyProperties(request, metric, "profileId");
        metric.setProfileId(null);
        metric.setUnit(normalizedUnit);
        metric.setTrend(request.getTrend() != null ? request.getTrend() : "NORMAL");
        metric.setCategory(category);
        metric.setCreateTime(LocalDateTime.now());

        healthMetricMapper.insert(metric);

        // 同时写入实时指标表，供健康评分使用
        com.hhs.entity.RealtimeMetric realtimeMetric = toRealtimeMetric(metric);
        realtimeMetricMapper.insert(realtimeMetric);

        // Publish event for alert generation and cache invalidation
        eventPublisher.publishEvent(new MetricRecordedEvent(request.getUserId(), realtimeMetric));

        log.info("Metric created: id={}, userId={}, type={}, value={}, category={}, realtimeMetricId={}",
                metric.getId(), request.getUserId(), metric.getMetricKey(), metric.getValue(), category, realtimeMetric.getId());
        return metric;
    }

    /**
     * Update an existing health metric using direct Mapper injection
     *
     * @param id Metric ID
     * @param request Health metric request
     * @return Updated health metric entity
     */
    @Override
    @Transactional(timeout = 30)
    public HealthMetric update(Long id, HealthMetricRequest request) {
        log.info("Updating metric id: {}", id);
        HealthMetric metric = healthMetricMapper.selectById(id);
        if (metric == null) {
            throw new BusinessException(ErrorCode.HEALTH_METRIC_NOT_FOUND);
        }

        // Auto-correct unit based on metric key
        String normalizedUnit = normalizeUnit(request.getMetricKey(), request.getUnit());

        // Auto-assign category based on metric key
        com.hhs.common.enums.MetricCategory category = metricCategoryService.determineCategory(request.getMetricKey());

        // Update fields from request (userId should not be changed)
        metric.setMetricKey(request.getMetricKey());
        metric.setValue(request.getValue());
        metric.setUnit(normalizedUnit);
        metric.setRecordDate(request.getRecordDate());
        metric.setTrend(request.getTrend() != null ? request.getTrend() : "NORMAL");
        metric.setCategory(category);

        healthMetricMapper.updateById(metric);

        // 同时更新实时指标表
        LocalDateTime startTime = metric.getRecordDate().atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        com.hhs.entity.RealtimeMetric rt = new com.hhs.entity.RealtimeMetric();
        rt.setUserId(metric.getUserId());
        rt.setMetricKey(metric.getMetricKey());
        rt.setValue(metric.getValue());
        rt.setUnit(metric.getUnit());
        rt.setSource("manual");
        rt.setCreatedAt(startTime);
        // 删除旧的，插入新的
        realtimeMetricMapper.deleteByHealthMetric(metric.getUserId(), metric.getMetricKey(), startTime, endTime);
        realtimeMetricMapper.insert(rt);

        log.info("Metric updated: id={}, userId={}, type={}, category={}", id, metric.getUserId(), metric.getMetricKey(), category);
        return metric;
    }

    /**
     * Delete a health metric by ID using direct Mapper injection
     *
     * @param id Metric ID to delete
     */
    @Override
    @Transactional(timeout = 30)
    public void delete(Long id) {
        log.info("Deleting metric id: {}", id);
        HealthMetric metric = healthMetricMapper.selectById(id);
        if (metric == null) {
            log.warn("Metric not found for deletion: id={}", id);
            return;
        }

        // Cascade delete: also remove from realtime_metric table
        LocalDateTime startTime = metric.getRecordDate().atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        realtimeMetricMapper.deleteByHealthMetric(
                metric.getUserId(), metric.getMetricKey(), startTime, endTime
        );

        healthMetricMapper.deleteById(id);
        log.info("Metric deleted: id={}, userId={}", id, metric.getUserId());
    }
}
