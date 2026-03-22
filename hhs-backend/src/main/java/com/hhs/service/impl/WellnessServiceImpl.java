package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.enums.MetricCategory;
import com.hhs.common.constant.ErrorCode;
import com.hhs.dto.WellnessMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.RealtimeMetric;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.WellnessService;
import com.hhs.service.domain.MetricCategoryService;
import com.hhs.service.domain.MetricDisplayFormatter;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.HealthMetricVO;
import com.hhs.vo.WellnessMetricSummary;
import com.hhs.vo.WellnessSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Wellness Service Implementation
 * Provides wellness metric operations with category filtering
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WellnessServiceImpl implements WellnessService {

    private final HealthMetricMapper healthMetricMapper;
    private final RealtimeMetricMapper realtimeMetricMapper;
    private final MetricCategoryService metricCategoryService;
    private final MetricDisplayFormatter metricDisplayFormatter;

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public Page<HealthMetricVO> getWellnessMetrics(Long userId, String metricKey,
                                                    LocalDate startDate, LocalDate endDate,
                                                    int page, int size) {
        Page<HealthMetric> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<HealthMetric> wrapper = buildWellnessQueryWrapper(userId, metricKey, startDate, endDate);
        wrapper.orderByDesc(HealthMetric::getRecordDate);

        Page<HealthMetric> result = healthMetricMapper.selectPage(pageParam, wrapper);

        Page<HealthMetricVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList()));

        return voPage;
    }

    @Override
    @Transactional(timeout = 30)
    public HealthMetricVO createWellnessMetric(Long userId, WellnessMetricRequest request) {
        log.info("Creating wellness metric for user: {}, key: {}", userId, request.getMetricKey());

        // Validate metric key is a known wellness metric
        if (!metricCategoryService.isWellnessMetric(request.getMetricKey())) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "无效的保健指标类型: " + request.getMetricKey());
        }

        // Validate value range for the specific metric
        validateWellnessMetricRange(request.getMetricKey(), request.getValue());

        // Get normalized unit
        String unit = metricDisplayFormatter.getUnit(request.getMetricKey());
        if (request.getUnit() != null && !request.getUnit().isBlank()) {
            unit = request.getUnit();
        }

        HealthMetric metric = new HealthMetric();
        metric.setUserId(userId);
        metric.setMetricKey(request.getMetricKey());
        metric.setValue(request.getValue());
        metric.setUnit(unit);
        metric.setRecordDate(request.getRecordDate());
        metric.setTrend("normal");
        metric.setCategory(MetricCategory.WELLNESS);
        metric.setCreateTime(LocalDateTime.now());

        healthMetricMapper.insert(metric);

        // Also insert into realtime_metric table
        RealtimeMetric realtimeMetric = toRealtimeMetric(metric);
        realtimeMetricMapper.insert(realtimeMetric);

        log.info("Wellness metric created: id={}, userId={}, key={}, value={}",
                metric.getId(), userId, metric.getMetricKey(), metric.getValue());

        return toVO(metric);
    }

    @Override
    @Transactional(timeout = 30)
    public HealthMetricVO updateWellnessMetric(Long userId, Long metricId, WellnessMetricRequest request) {
        log.info("Updating wellness metric: id={}, userId={}", metricId, userId);

        HealthMetric metric = healthMetricMapper.selectById(metricId);
        if (metric == null) {
            throw new BusinessException(ErrorCode.HEALTH_METRIC_NOT_FOUND);
        }

        // Verify ownership
        if (!metric.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权修改他人记录");
        }

        // Validate metric key is a known wellness metric
        if (!metricCategoryService.isWellnessMetric(request.getMetricKey())) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "无效的保健指标类型: " + request.getMetricKey());
        }

        // Validate value range
        validateWellnessMetricRange(request.getMetricKey(), request.getValue());

        // Get normalized unit
        String unit = metricDisplayFormatter.getUnit(request.getMetricKey());
        if (request.getUnit() != null && !request.getUnit().isBlank()) {
            unit = request.getUnit();
        }

        // Update fields
        metric.setMetricKey(request.getMetricKey());
        metric.setValue(request.getValue());
        metric.setUnit(unit);
        metric.setRecordDate(request.getRecordDate());
        metric.setCategory(MetricCategory.WELLNESS);

        healthMetricMapper.updateById(metric);

        // Update realtime_metric table
        LocalDateTime startTime = metric.getRecordDate().atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        realtimeMetricMapper.deleteByHealthMetric(userId, metric.getMetricKey(), startTime, endTime);
        RealtimeMetric realtimeMetric = toRealtimeMetric(metric);
        realtimeMetricMapper.insert(realtimeMetric);

        log.info("Wellness metric updated: id={}, key={}", metricId, metric.getMetricKey());

        return toVO(metric);
    }

    @Override
    @Transactional(timeout = 30)
    public void deleteWellnessMetric(Long userId, Long metricId) {
        log.info("Deleting wellness metric: id={}, userId={}", metricId, userId);

        HealthMetric metric = healthMetricMapper.selectById(metricId);
        if (metric == null) {
            log.warn("Wellness metric not found for deletion: id={}", metricId);
            return;
        }

        // Verify ownership
        if (!metric.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权删除他人记录");
        }

        // Delete from realtime_metric table
        LocalDateTime startTime = metric.getRecordDate().atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        realtimeMetricMapper.deleteByHealthMetric(userId, metric.getMetricKey(), startTime, endTime);

        // Delete from health_metric table
        healthMetricMapper.deleteById(metricId);

        log.info("Wellness metric deleted: id={}", metricId);
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public HealthMetricTrendVO getTrend(Long userId, String metricKey,
                                         LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<HealthMetric> wrapper = buildWellnessQueryWrapper(userId, metricKey, startDate, endDate);
        wrapper.orderByAsc(HealthMetric::getRecordDate);

        List<HealthMetric> metrics = healthMetricMapper.selectList(wrapper);

        if (metrics.isEmpty()) {
            return new HealthMetricTrendVO(metricKey, List.of(), List.of());
        }

        List<LocalDate> dates = metrics.stream()
                .map(HealthMetric::getRecordDate)
                .collect(Collectors.toList());

        List<BigDecimal> values = metrics.stream()
                .map(HealthMetric::getValue)
                .collect(Collectors.toList());

        return new HealthMetricTrendVO(metricKey, dates, values);
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public WellnessSummaryVO getSummary(Long userId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthMetric::getUserId, userId);
        wrapper.eq(HealthMetric::getCategory, MetricCategory.WELLNESS);
        wrapper.ge(HealthMetric::getRecordDate, startDate);
        wrapper.le(HealthMetric::getRecordDate, endDate);
        wrapper.orderByDesc(HealthMetric::getRecordDate);

        List<HealthMetric> metrics = healthMetricMapper.selectList(wrapper);

        // Group metrics by key
        Map<String, List<HealthMetric>> metricsByKey = metrics.stream()
                .collect(Collectors.groupingBy(HealthMetric::getMetricKey));

        // Calculate aggregated values
        BigDecimal avgSleepDuration = calculateAverage(metricsByKey.get("sleepDuration"));
        Integer avgSleepQuality = calculateIntegerAverage(metricsByKey.get("sleepQuality"));
        Long totalSteps = calculateSum(metricsByKey.get("steps"));
        Integer totalExerciseMinutes = calculateIntegerSum(metricsByKey.get("exerciseMinutes"));
        Integer totalWaterIntake = calculateIntegerSum(metricsByKey.get("waterIntake"));
        Integer avgMood = calculateIntegerAverage(metricsByKey.get("mood"));
        Integer avgEnergy = calculateIntegerAverage(metricsByKey.get("energy"));

        // Build metric summaries
        List<WellnessMetricSummary> metricSummaries = buildMetricSummaries(metricsByKey);

        return new WellnessSummaryVO(
                LocalDate.now(),
                avgSleepDuration,
                avgSleepQuality,
                totalSteps,
                totalExerciseMinutes,
                totalWaterIntake,
                avgMood,
                avgEnergy,
                metricSummaries
        );
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public Map<String, HealthMetricVO> getLatestMetrics(Long userId) {
        Map<String, HealthMetricVO> latestMetrics = new LinkedHashMap<>();

        for (String metricKey : metricCategoryService.getWellnessMetrics()) {
            LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(HealthMetric::getUserId, userId);
            wrapper.eq(HealthMetric::getCategory, MetricCategory.WELLNESS);
            wrapper.eq(HealthMetric::getMetricKey, metricKey);
            wrapper.orderByDesc(HealthMetric::getRecordDate);
            wrapper.last("LIMIT 1");

            HealthMetric metric = healthMetricMapper.selectOne(wrapper);
            if (metric != null) {
                latestMetrics.put(metricKey, toVO(metric));
            }
        }

        return latestMetrics;
    }

    // ========================================
    // Private helper methods
    // ========================================

    private LambdaQueryWrapper<HealthMetric> buildWellnessQueryWrapper(Long userId, String metricKey,
                                                                        LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthMetric::getUserId, userId);
        wrapper.eq(HealthMetric::getCategory, MetricCategory.WELLNESS);

        if (metricKey != null && !metricKey.isBlank()) {
            wrapper.eq(HealthMetric::getMetricKey, metricKey);
        }
        if (startDate != null) {
            wrapper.ge(HealthMetric::getRecordDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(HealthMetric::getRecordDate, endDate);
        }

        return wrapper;
    }

    private void validateWellnessMetricRange(String metricKey, BigDecimal value) {
        // Wellness-specific validation ranges
        Map<String, BigDecimal[]> ranges = Map.of(
                "sleepDuration", new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("24")},
                "sleepQuality", new BigDecimal[]{new BigDecimal("1"), new BigDecimal("5")},
                "steps", new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("100000")},
                "exerciseMinutes", new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("1440")},
                "waterIntake", new BigDecimal[]{BigDecimal.ZERO, new BigDecimal("20")},
                "mood", new BigDecimal[]{new BigDecimal("1"), new BigDecimal("5")},
                "energy", new BigDecimal[]{new BigDecimal("1"), new BigDecimal("5")}
        );

        BigDecimal[] range = ranges.get(metricKey);
        if (range != null) {
            if (value.compareTo(range[0]) < 0 || value.compareTo(range[1]) > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER,
                        String.format("%s数值必须在 %.0f 到 %.0f 之间",
                                metricDisplayFormatter.getDisplayName(metricKey),
                                range[0], range[1]));
            }
        }
    }

    private RealtimeMetric toRealtimeMetric(HealthMetric metric) {
        RealtimeMetric rt = new RealtimeMetric();
        rt.setUserId(metric.getUserId());
        rt.setMetricKey(metric.getMetricKey());
        rt.setValue(metric.getValue());
        rt.setUnit(metric.getUnit());
        rt.setSource("wellness");
        rt.setCreatedAt(metric.getRecordDate().atStartOfDay());
        return rt;
    }

    private HealthMetricVO toVO(HealthMetric e) {
        return new HealthMetricVO(
                e.getId(), e.getUserId(), e.getProfileId(), e.getMetricKey(),
                metricDisplayFormatter.getDisplayName(e.getMetricKey()),
                e.getValue(), e.getUnit(), e.getRecordDate(), e.getTrend(),
                e.getCategory(), e.getCreateTime()
        );
    }

    private BigDecimal calculateAverage(List<HealthMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return null;
        }
        BigDecimal sum = metrics.stream()
                .map(HealthMetric::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(new BigDecimal(metrics.size()), 2, RoundingMode.HALF_UP);
    }

    private Integer calculateIntegerAverage(List<HealthMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return null;
        }
        BigDecimal avg = calculateAverage(metrics);
        return avg != null ? avg.intValue() : null;
    }

    private Long calculateSum(List<HealthMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0L;
        }
        return metrics.stream()
                .map(HealthMetric::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .longValue();
    }

    private Integer calculateIntegerSum(List<HealthMetric> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0;
        }
        return metrics.stream()
                .map(HealthMetric::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .intValue();
    }

    private List<WellnessMetricSummary> buildMetricSummaries(Map<String, List<HealthMetric>> metricsByKey) {
        List<WellnessMetricSummary> summaries = new ArrayList<>();

        for (String metricKey : metricCategoryService.getWellnessMetrics()) {
            List<HealthMetric> metrics = metricsByKey.get(metricKey);
            if (metrics == null || metrics.isEmpty()) {
                continue;
            }

            // Sort by date descending for latest value
            metrics.sort((a, b) -> b.getRecordDate().compareTo(a.getRecordDate()));

            HealthMetric latest = metrics.get(0);
            BigDecimal avgValue = calculateAverage(metrics);
            BigDecimal trend = calculateTrend(metrics);

            summaries.add(new WellnessMetricSummary(
                    metricKey,
                    metricDisplayFormatter.getDisplayName(metricKey),
                    latest.getValue(),
                    latest.getUnit(),
                    avgValue,
                    trend
            ));
        }

        return summaries;
    }

    private BigDecimal calculateTrend(List<HealthMetric> metrics) {
        if (metrics == null || metrics.size() < 2) {
            return BigDecimal.ZERO;
        }

        // Sort by date ascending for trend calculation
        metrics.sort((a, b) -> a.getRecordDate().compareTo(b.getRecordDate()));

        BigDecimal first = metrics.get(0).getValue();
        BigDecimal last = metrics.get(metrics.size() - 1).getValue();

        return last.subtract(first);
    }
}