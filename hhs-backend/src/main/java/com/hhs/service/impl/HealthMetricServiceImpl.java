package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.constant.ErrorCode;
import com.hhs.common.enums.MetricCategory;
import com.hhs.domain.event.MetricRecordedEvent;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.RealtimeMetric;
import com.hhs.entity.UserThreshold;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.HealthMetricService;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.MetricCategoryService;
import com.hhs.service.domain.MetricDefinition;
import com.hhs.service.domain.MetricDisplayFormatter;
import com.hhs.service.domain.MetricKeyNormalizer;
import com.hhs.service.domain.ThresholdEvaluator;
import com.hhs.service.domain.ThresholdRangeChecker;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.HealthMetricVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HealthMetricServiceImpl implements HealthMetricService {

    private final HealthMetricMapper healthMetricMapper;
    private final RealtimeMetricMapper realtimeMetricMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final MetricDisplayFormatter metricDisplayFormatter;
    private final MetricCategoryService metricCategoryService;
    private final HealthMetricMetaRegistry metricRegistry;
    private final ThresholdEvaluator thresholdEvaluator;
    private final ThresholdRangeChecker rangeChecker;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Upper bound for {@link #listRecent}: above this the "recent records" list stops being recent. */
    private static final int MAX_RECENT_LIMIT = 50;

    /**
     * Alternative spellings of units that mean the same thing. Keys and values are lower-cased
     * so {@link #canonicalizeUnit(String)} can compare case-insensitively.
     */
    private static final Map<String, String> UNIT_ALIASES = Map.of(
            "bpm", "次/分",
            "beats/min", "次/分",
            "bpm(次/分)", "次/分",
            "次/min", "次/分"
    );

    public HealthMetricServiceImpl(
            HealthMetricMapper healthMetricMapper,
            RealtimeMetricMapper realtimeMetricMapper,
            ApplicationEventPublisher eventPublisher,
            MetricDisplayFormatter metricDisplayFormatter,
            MetricCategoryService metricCategoryService,
            HealthMetricMetaRegistry metricRegistry) {
        this(healthMetricMapper, realtimeMetricMapper, eventPublisher, metricDisplayFormatter,
                metricCategoryService, metricRegistry, null, null);
    }

    @Autowired
    public HealthMetricServiceImpl(
            HealthMetricMapper healthMetricMapper,
            RealtimeMetricMapper realtimeMetricMapper,
            ApplicationEventPublisher eventPublisher,
            MetricDisplayFormatter metricDisplayFormatter,
            MetricCategoryService metricCategoryService,
            @Autowired(required = false) HealthMetricMetaRegistry metricRegistry,
            @Autowired(required = false) ThresholdEvaluator thresholdEvaluator,
            @Autowired(required = false) ThresholdRangeChecker rangeChecker) {
        this.healthMetricMapper = healthMetricMapper;
        this.realtimeMetricMapper = realtimeMetricMapper;
        this.eventPublisher = eventPublisher;
        this.metricDisplayFormatter = metricDisplayFormatter;
        this.metricCategoryService = metricCategoryService;
        this.metricRegistry = metricRegistry != null ? metricRegistry : new HealthMetricMetaRegistry();
        this.thresholdEvaluator = thresholdEvaluator;
        this.rangeChecker = rangeChecker;
    }

    /**
     * Add a new health metric record for a user
     *
     * <p>There is no {@code @CacheEvict} here on purpose. This used to carry
     * {@code @CacheEvict(value={"metrics:latest","metrics:latest:all"}, allEntries=true)}, but no
     * {@code @Cacheable} in the code base ever wrote those names and nothing calls
     * {@code cacheManager.getCache("metrics:latest")} — the eviction could not remove anything.
     * The annotation was worse than dead: it implied the read path was cached and steered
     * diagnosis away from the cache that actually goes stale after a write
     * ({@code HealthScoreServiceImpl}'s plain Redis key {@code health:score:v2:{userId}}, which is
     * invalidated explicitly by {@code CacheInvalidationListener} / {@code WellnessServiceImpl}).
     *
     * @param userId User ID
     * @param request Health metric request containing metric type, value, unit, record date
     * @return HealthMetricVO containing the created metric
     * @throws BusinessException if validation fails
     */
    @Override
    @Transactional(timeout = 30)
    public HealthMetricVO add(Long userId, HealthMetricRequest request) {
        HealthMetric persisted = persistMetric(userId, request);
        return toVO(persisted);
    }

    /**
     * Shared write path for both entry points ({@link #add} and {@link #create}).
     *
     * <p>These two used to diverge: {@code add()} skipped the {@code realtime_metric} write,
     * relied on the database default for {@code create_time}, and carried its own duplicate
     * guard; {@code create()} did the opposite. The consequence was that metrics recorded
     * through the AI/OCR/prevention entry points never reached {@code realtime_metric} and
     * therefore never influenced the health score.
     *
     * <p>The duplicate guard is intentionally "same user + same key + same date + same value
     * → overwrite" rather than a time-window check. A wall-clock window compared
     * {@code create_time} against {@code LocalDateTime.now()}, but rows written through the
     * database default were stored in UTC while application-written rows used the JVM zone,
     * so the comparison never matched and the guard silently did nothing.
     */
    private HealthMetric persistMetric(Long userId, HealthMetricRequest request) {
        String requestedKey = request.getMetricKey();
        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(requestedKey);
        request.setMetricKey(canonicalKey);

        // Reject keys no dictionary knows about. MetricKeyNormalizer passes unknown keys through
        // unchanged, so without this gate a typo like "sleep" or "sleepTime" was accepted and then
        // classified HEALTH by determineCategory's backward-compatible default: the reading would
        // land in the health series and never appear in the wellness list or wellness summary
        // (both filter category=WELLNESS). 400 is the honest answer for "I don't know this metric".
        if (!metricCategoryService.isWritableMetric(canonicalKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER,
                    "无效的指标类型: " + canonicalKey);
        }

        String normalizedUnit = normalizeUnit(canonicalKey, request.getUnit());
        MetricCategory category = metricCategoryService.determineCategory(canonicalKey);

        HealthMetric existing = findSameDayEntry(userId, canonicalKey, request);
        if (existing != null) {
            String trend = determineTrend(userId, canonicalKey, request.getValue(),
                    request.getRecordDate(), request.getTrend(), existing.getId());

            existing.setValue(request.getValue());
            existing.setUnit(normalizedUnit);
            existing.setTrend(trend);
            existing.setCategory(category);
            existing.setCreateTime(LocalDateTime.now());
            healthMetricMapper.updateById(existing);

            RealtimeMetric updated = toRealtimeMetric(existing);
            replaceRealtimeMetric(existing);
            eventPublisher.publishEvent(new MetricRecordedEvent(userId, updated));

            log.info("Metric re-recorded (overwrote same-day entry): id={}, userId={}, key={}, value={}, category={}",
                    existing.getId(), userId, canonicalKey, request.getValue(), category);
            return existing;
        }

        String trend = determineTrend(userId, canonicalKey, request.getValue(),
                request.getRecordDate(), request.getTrend(), null);

        HealthMetric metric = new HealthMetric();
        metric.setUserId(userId);
        metric.setProfileId(null);
        metric.setMetricKey(canonicalKey);
        metric.setValue(request.getValue());
        metric.setUnit(normalizedUnit);
        metric.setRecordDate(request.getRecordDate());
        metric.setTrend(trend);
        metric.setCategory(category);
        // Set explicitly instead of relying on the column default: the MySQL container runs in
        // UTC while the JVM runs in Asia/Shanghai, so the default wrote a timestamp 8 hours
        // behind every application-written row in the same table.
        metric.setCreateTime(LocalDateTime.now());
        healthMetricMapper.insert(metric);

        RealtimeMetric realtimeMetric = toRealtimeMetric(metric);
        realtimeMetricMapper.insert(realtimeMetric);
        eventPublisher.publishEvent(new MetricRecordedEvent(userId, realtimeMetric));

        log.info("Metric recorded for user: {}, key: {}, value: {}, category: {}, realtimeMetricId: {}",
                userId, canonicalKey, metric.getValue(), category, realtimeMetric.getId());
        return metric;
    }

    /**
     * Find an existing entry for the same user/metric/date combination.
     *
     * <p>Only the value decides whether this is a re-record of the same reading; a different
     * value on the same date is a legitimate second measurement and must be inserted.
     */
    private HealthMetric findSameDayEntry(Long userId, String canonicalKey, HealthMetricRequest request) {
        if (userId == null || request.getRecordDate() == null || request.getValue() == null) {
            return null;
        }
        List<HealthMetric> matches = healthMetricMapper.selectList(
                new LambdaQueryWrapper<HealthMetric>()
                        .eq(HealthMetric::getUserId, userId)
                        .in(HealthMetric::getMetricKey, MetricKeyNormalizer.getAliases(canonicalKey))
                        .eq(HealthMetric::getRecordDate, request.getRecordDate())
                        .eq(HealthMetric::getValue, request.getValue())
                        .orderByDesc(HealthMetric::getId)
                        .last("LIMIT 1")
        );
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Drop the realtime row that mirrors an existing health metric before it is replaced.
     */
    private void replaceRealtimeMetric(HealthMetric metric) {
        if (metric.getRecordDate() == null) {
            return;
        }
        LocalDateTime startTime = metric.getRecordDate().atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        realtimeMetricMapper.deleteByHealthMetric(
                metric.getUserId(), metric.getMetricKey(), startTime, endTime);
        realtimeMetricMapper.insert(toRealtimeMetric(metric));
    }

    /**
     * Normalize unit based on metric key - ensures consistent units.
     *
     * <p>Legacy rows carry English abbreviations (`bpm`) while the registry declares Chinese
     * units (`次/分`). Both describe the same measurement, so a client echoing the legacy value
     * back would otherwise fork the history into two unit groups and split trend charts.
     */
    private String normalizeUnit(String metricKey, String providedUnit) {
        String standardUnit = metricRegistry != null
                ? metricRegistry.getDefinition(metricKey)
                        .map(MetricDefinition::unit)
                        .filter(u -> !u.isBlank())
                        .orElseGet(() -> metricDisplayFormatter != null ? metricDisplayFormatter.getUnit(metricKey) : "")
                : (metricDisplayFormatter != null ? metricDisplayFormatter.getUnit(metricKey) : "");
        if (providedUnit == null || providedUnit.isBlank()) {
            return standardUnit;
        }
        String canonicalUnit = canonicalizeUnit(providedUnit);
        if (!canonicalUnit.isEmpty() && canonicalUnit.equals(canonicalizeUnit(standardUnit))) {
            return standardUnit;
        }
        // Allow user-provided unit but log warning if different from standard
        if (!providedUnit.equals(standardUnit) && !standardUnit.isEmpty()) {
            log.warn("User provided unit '{}' for metric '{}', standard unit is '{}'. Using provided unit.",
                    providedUnit, metricKey, standardUnit);
        }
        return providedUnit;
    }

    /**
     * Map alternative spellings of the same unit onto one comparable form.
     */
    private String canonicalizeUnit(String unit) {
        if (unit == null) {
            return "";
        }
        String trimmed = unit.trim();
        return UNIT_ALIASES.getOrDefault(trimmed.toLowerCase(Locale.ROOT), trimmed.toLowerCase(Locale.ROOT));
    }

    // Helper method to convert HealthMetric to RealtimeMetric for event publishing
    private RealtimeMetric toRealtimeMetric(HealthMetric metric) {
        RealtimeMetric rt = new RealtimeMetric();
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
            Set<String> aliases = MetricKeyNormalizer.getAliases(metricKey);
            if (aliases.size() > 1) {
                wrapper.in(HealthMetric::getMetricKey, aliases);
            } else {
                wrapper.eq(HealthMetric::getMetricKey, metricKey);
            }
        }
        if (startDate != null) {
            wrapper.ge(HealthMetric::getRecordDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(HealthMetric::getRecordDate, endDate);
        }
        wrapper.orderByDesc(HealthMetric::getRecordDate);

        List<HealthMetric> entities = healthMetricMapper.selectList(wrapper);
        Map<String, UserThreshold> thresholds = loadThresholdsFor(entities);
        return entities.stream()
                .map(entity -> toVO(entity, thresholds))
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
        String type = metricKey != null && !metricKey.isBlank()
                ? MetricKeyNormalizer.toCanonicalKey(metricKey)
                : MetricKeyNormalizer.toCanonicalKey(list.get(0).metricKey());
        return List.of(new HealthMetricTrendVO(type, dates, values));
    }

    /**
     * Delete a health metric record — the only delete entry point.
     *
     * <p>Ownership is verified before the cascade delete, and idempotent for missing rows so a
     * retried DELETE does not surface as an error. An unchecked {@code delete(Long id)} overload
     * used to exist and was what {@code DELETE /api/metrics/{id}} called: any logged-in user could
     * delete another user's row by guessing the auto-increment id. It has been removed from the
     * interface so there is no un-guarded variant left to call by accident.
     *
     * <p>No {@code @CacheEvict} either — see {@link #add(Long, HealthMetricRequest)}: the
     * {@code metrics:latest} cache names it used to evict were never written by anything.
     *
     * @param userId User ID (for authorization)
     * @param id Metric ID to delete
     * @return true if deletion successful, true if record not found (idempotent)
     * @throws BusinessException if user is not authorized to delete the record
     */
    @Override
    @Transactional
    public boolean delete(Long userId, Long id) {
        // Idempotent: return true if record doesn't exist
        HealthMetric metric = healthMetricMapper.selectById(id);
        if (metric == null) {
            log.info("Metric already deleted or not found: id={}", id);
            return true;
        }
        if (userId == null || !metric.getUserId().equals(userId)) {
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
        return toVO(e, null);
    }

    /**
     * Convert a metric to its VO.
     *
     * @param prefetchedThresholds thresholds already loaded for a batch, or {@code null} to
     *                              resolve this single row's threshold on demand
     */
    private HealthMetricVO toVO(HealthMetric e, Map<String, UserThreshold> prefetchedThresholds) {
        String rawTrend = e.getTrend();
        String trend = (rawTrend == null || rawTrend.isBlank() || "NORMAL".equalsIgnoreCase(rawTrend) || "normal".equalsIgnoreCase(rawTrend) || "stable".equalsIgnoreCase(rawTrend))
                ? "STABLE"
                : rawTrend.toUpperCase();

        String status = "NORMAL";
        String statusText = "正常";
        String alertLevel = "NONE";

        if (e.getValue() != null && e.getUserId() != null && e.getMetricKey() != null) {
            UserThreshold threshold;
            if (prefetchedThresholds != null) {
                threshold = prefetchedThresholds.get(MetricKeyNormalizer.toCanonicalKey(e.getMetricKey()));
                // Fall back to the single-row lookup when the batch could not resolve this key,
                // so a partial prefetch never silently downgrades an abnormal value to "normal".
                if (threshold == null && thresholdEvaluator != null) {
                    threshold = thresholdEvaluator.getThresholdForUser(e.getUserId(), e.getMetricKey());
                }
            } else {
                threshold = thresholdEvaluator != null
                        ? thresholdEvaluator.getThresholdForUser(e.getUserId(), e.getMetricKey())
                        : null;
            }
            if (threshold != null && rangeChecker != null) {
                ThresholdRangeChecker.Band band = rangeChecker.bandOf(e.getValue().doubleValue(), threshold);
                switch (band) {
                    case CRITICAL_HIGH -> {
                        status = "CRITICAL_HIGH";
                        statusText = "危急偏高";
                        alertLevel = "HIGH";
                    }
                    case CRITICAL_LOW -> {
                        status = "CRITICAL_LOW";
                        statusText = "危急偏低";
                        alertLevel = "HIGH";
                    }
                    case WARNING_HIGH -> {
                        status = "WARNING_HIGH";
                        statusText = "偏高";
                        alertLevel = "MEDIUM";
                    }
                    case WARNING_LOW -> {
                        status = "WARNING_LOW";
                        statusText = "偏低";
                        alertLevel = "MEDIUM";
                    }
                    default -> {
                        status = "NORMAL";
                        statusText = "正常";
                        alertLevel = "NONE";
                    }
                }
            } else if (metricRegistry != null) {
                var defOpt = metricRegistry.getDefinition(e.getMetricKey());
                if (defOpt.isPresent()) {
                    MetricDefinition def = defOpt.get();
                    if (def.normalMax() != null && e.getValue().compareTo(def.normalMax()) > 0) {
                        status = "WARNING_HIGH";
                        statusText = "偏高";
                        alertLevel = "MEDIUM";
                    } else if (def.normalMin() != null && e.getValue().compareTo(def.normalMin()) < 0) {
                        status = "WARNING_LOW";
                        statusText = "偏低";
                        alertLevel = "MEDIUM";
                    }
                }
            }
        }

        return new HealthMetricVO(
                e.getId(), e.getUserId(), e.getProfileId(), e.getMetricKey(),
                metricDisplayFormatter != null ? metricDisplayFormatter.getDisplayName(e.getMetricKey()) : e.getMetricKey(),
                e.getValue(), e.getUnit(), e.getRecordDate(), trend,
                e.getCategory(), e.getCreateTime(),
                status, statusText, alertLevel
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
            Set<String> aliases = MetricKeyNormalizer.getAliases(metricKey);
            if (aliases.size() > 1) {
                wrapper.in(HealthMetric::getMetricKey, aliases);
            } else {
                wrapper.eq(HealthMetric::getMetricKey, metricKey);
            }
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

        // Prefetch every threshold this page needs in two queries instead of two per row.
        Map<String, UserThreshold> thresholds = loadThresholdsFor(result.getRecords());

        // Convert to VO page
        Page<HealthMetricVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<HealthMetricVO> records = result.getRecords().stream()
                .map(entity -> toVO(entity, thresholds))
                .collect(Collectors.toList());
        voPage.setRecords(records);

        return voPage;
    }

    /**
     * List the most recently created metrics across both categories.
     *
     * <p>Neither {@code /api/metrics} nor {@code /api/wellness} can answer this: both pin
     * {@code category}, both cap the page and they order by different columns
     * ({@code create_time} vs {@code record_date}), so merging two ten-row pages client-side
     * could drop a freshly recorded health row behind ten wellness rows. This single query
     * orders every category by {@code create_time}, which is the column the UI displays.
     */
    @Override
    @Transactional(timeout = 30, readOnly = true)
    public List<HealthMetricVO> listRecent(Long userId, Integer limit) {
        int safeLimit = limit == null ? 10 : Math.max(1, Math.min(limit, MAX_RECENT_LIMIT));

        LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(HealthMetric::getUserId, userId);
        // id 作为稳定次序的兜底：同秒写入的多条记录（一次血压录两条）不会每次刷新换位置
        wrapper.orderByDesc(HealthMetric::getCreateTime).orderByDesc(HealthMetric::getId);
        wrapper.last("LIMIT " + safeLimit);

        List<HealthMetric> entities = healthMetricMapper.selectList(wrapper);
        Map<String, UserThreshold> thresholds = loadThresholdsFor(entities);
        return entities.stream()
                .map(entity -> toVO(entity, thresholds))
                .collect(Collectors.toList());
    }

    /**
     * Resolve the thresholds for a batch of metrics with a single batched lookup.
     */
    private Map<String, UserThreshold> loadThresholdsFor(List<HealthMetric> metrics) {
        if (thresholdEvaluator == null || metrics == null || metrics.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> keys = metrics.stream()
                .map(HealthMetric::getMetricKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        return thresholdEvaluator.getThresholdsForUser(metrics.get(0).getUserId(), keys);
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
        return persistMetric(request.getUserId(), request);
    }

    /**
     * Update an existing health metric using direct Mapper injection
     *
     * <p>Ownership is checked against {@code userId} (from the JWT) before anything is read or
     * written. The previous signature took the caller's id only inside the request body — which
     * the service never read — so any logged-in user could rewrite another user's value, metric
     * key or record date through {@code PUT /api/metrics/{id}}.
     *
     * @param userId Caller's user ID (from JWT)
     * @param id Metric ID
     * @param request Health metric request
     * @return Updated health metric entity
     */
    @Override
    @Transactional(timeout = 30)
    public HealthMetric update(Long userId, Long id, HealthMetricRequest request) {
        log.info("Updating metric id: {} for user: {}", id, userId);
        HealthMetric metric = healthMetricMapper.selectById(id);
        if (metric == null) {
            throw new BusinessException(ErrorCode.HEALTH_METRIC_NOT_FOUND);
        }
        // 403 instead of 404: the record exists, the caller simply is not its owner. Same wording
        // as the other ownership guards in this codebase (WellnessServiceImpl, delete below).
        if (userId == null || !metric.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN, "无权修改他人记录");
        }

        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(request.getMetricKey());
        request.setMetricKey(canonicalKey);

        // Same gate as persistMetric: an editing request must not be able to move a row onto a key
        // no dictionary knows, which would silently reclassify it as HEALTH.
        if (!metricCategoryService.isWritableMetric(canonicalKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER,
                    "无效的指标类型: " + canonicalKey);
        }

        // Auto-correct unit based on metric key
        String normalizedUnit = normalizeUnit(canonicalKey, request.getUnit());

        // Auto-assign category based on metric key
        MetricCategory category = metricCategoryService.determineCategory(canonicalKey);

        String trend = determineTrend(metric.getUserId(), canonicalKey, request.getValue(), request.getRecordDate(), request.getTrend(), id);

        // Update fields from request (userId should not be changed)
        metric.setMetricKey(canonicalKey);
        metric.setValue(request.getValue());
        metric.setUnit(normalizedUnit);
        metric.setRecordDate(request.getRecordDate());
        metric.setTrend(trend);
        metric.setCategory(category);

        healthMetricMapper.updateById(metric);

        // 同时更新实时指标表
        LocalDateTime startTime = metric.getRecordDate().atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        RealtimeMetric rt = new RealtimeMetric();
        rt.setUserId(metric.getUserId());
        rt.setMetricKey(canonicalKey);
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
     * Determine trend direction: respect explicit valid UP/DOWN/STABLE,
     * otherwise auto-calculate against the previous most recent measurement.
     */
    private String determineTrend(Long userId, String canonicalKey, BigDecimal currentValue,
                                  LocalDate recordDate, String requestedTrend, Long excludeId) {
        if (requestedTrend != null && !requestedTrend.isBlank()) {
            String upper = requestedTrend.trim().toUpperCase();
            if ("UP".equals(upper) || "DOWN".equals(upper) || "STABLE".equals(upper)) {
                return upper;
            }
        }

        if (userId == null || canonicalKey == null || currentValue == null) {
            return "STABLE";
        }

        Set<String> aliases = MetricKeyNormalizer.getAliases(canonicalKey);
        LambdaQueryWrapper<HealthMetric> wrapper = new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .in(HealthMetric::getMetricKey, aliases);
        if (excludeId != null) {
            wrapper.ne(HealthMetric::getId, excludeId);
        }
        if (recordDate != null) {
            wrapper.le(HealthMetric::getRecordDate, recordDate);
        }
        wrapper.orderByDesc(HealthMetric::getRecordDate)
                .orderByDesc(HealthMetric::getId)
                .last("LIMIT 1");

        List<HealthMetric> previousList = healthMetricMapper.selectList(wrapper);
        if (previousList.isEmpty()) {
            return "STABLE";
        }

        HealthMetric previous = previousList.get(0);
        if (previous.getValue() == null) {
            return "STABLE";
        }

        int comp = currentValue.compareTo(previous.getValue());
        if (comp > 0) {
            return "UP";
        } else if (comp < 0) {
            return "DOWN";
        } else {
            return "STABLE";
        }
    }
}
