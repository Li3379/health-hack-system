package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.enums.MetricCategory;
import com.hhs.entity.AiParseHistory;
import com.hhs.entity.HealthAlert;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.HealthScoreHistory;
import com.hhs.entity.SyncHistory;
import com.hhs.mapper.AiParseHistoryMapper;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.HealthScoreHistoryMapper;
import com.hhs.mapper.SyncHistoryMapper;
import com.hhs.service.StatsService;
import com.hhs.vo.DashboardSummaryVO;
import com.hhs.vo.MonthlyStatsVO;
import com.hhs.vo.TodayStatsVO;
import com.hhs.vo.TrendDataVO;
import com.hhs.vo.WeeklyStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final HealthMetricMapper healthMetricMapper;
    private final AiParseHistoryMapper aiParseHistoryMapper;
    private final HealthAlertMapper healthAlertMapper;
    private final SyncHistoryMapper syncHistoryMapper;
    private final HealthScoreHistoryMapper healthScoreHistoryMapper;

    @Override
    public TodayStatsVO getTodayStats(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.atTime(LocalTime.MAX);

        // Count health metrics (category = HEALTH)
        Long healthMetricsCount = healthMetricMapper.selectCount(
            new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .eq(HealthMetric::getRecordDate, today)
                .eq(HealthMetric::getCategory, MetricCategory.HEALTH)
        );

        // Count wellness metrics (category = WELLNESS)
        Long wellnessMetricsCount = healthMetricMapper.selectCount(
            new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .eq(HealthMetric::getRecordDate, today)
                .eq(HealthMetric::getCategory, MetricCategory.WELLNESS)
        );

        // Count device syncs
        Long deviceSyncCount = syncHistoryMapper.selectCount(
            new LambdaQueryWrapper<SyncHistory>()
                .eq(SyncHistory::getUserId, userId)
                .between(SyncHistory::getCreateTime, startOfDay, endOfDay)
        );

        // Count AI recognitions
        Long aiRecognizeCount = aiParseHistoryMapper.selectCount(
            new LambdaQueryWrapper<AiParseHistory>()
                .eq(AiParseHistory::getUserId, userId)
                .between(AiParseHistory::getCreateTime, startOfDay, endOfDay)
        );

        return TodayStatsVO.builder()
                .healthMetricsCount(toInt(healthMetricsCount))
                .wellnessMetricsCount(toInt(wellnessMetricsCount))
                .deviceSyncCount(toInt(deviceSyncCount))
                .aiRecognizeCount(toInt(aiRecognizeCount))
                .build();
    }

    @Override
    public WeeklyStatsVO getWeeklyStats(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate currentWeekEnd = today;
        LocalDate previousWeekStart = currentWeekStart.minusWeeks(1);
        LocalDate previousWeekEnd = currentWeekStart.minusDays(1);

        // Current week counts
        int currentHealth = countMetrics(userId, MetricCategory.HEALTH, currentWeekStart, currentWeekEnd);
        int currentWellness = countMetrics(userId, MetricCategory.WELLNESS, currentWeekStart, currentWeekEnd);
        int currentSync = countSyncHistory(userId, currentWeekStart.atStartOfDay(), currentWeekEnd.atTime(LocalTime.MAX));
        int currentAi = countAiParseHistory(userId, currentWeekStart.atStartOfDay(), currentWeekEnd.atTime(LocalTime.MAX));
        BigDecimal currentAvgScore = getAvgScore(userId, currentWeekStart, currentWeekEnd);

        // Previous week counts
        int previousHealth = countMetrics(userId, MetricCategory.HEALTH, previousWeekStart, previousWeekEnd);
        int previousWellness = countMetrics(userId, MetricCategory.WELLNESS, previousWeekStart, previousWeekEnd);
        int previousSync = countSyncHistory(userId, previousWeekStart.atStartOfDay(), previousWeekEnd.atTime(LocalTime.MAX));
        int previousAi = countAiParseHistory(userId, previousWeekStart.atStartOfDay(), previousWeekEnd.atTime(LocalTime.MAX));
        BigDecimal previousAvgScore = getAvgScore(userId, previousWeekStart, previousWeekEnd);

        return WeeklyStatsVO.builder()
                .currentHealthMetricsCount(currentHealth)
                .previousHealthMetricsCount(previousHealth)
                .healthMetricsChangePercent(calcChangePercent(currentHealth, previousHealth))
                .currentWellnessMetricsCount(currentWellness)
                .previousWellnessMetricsCount(previousWellness)
                .wellnessMetricsChangePercent(calcChangePercent(currentWellness, previousWellness))
                .currentDeviceSyncCount(currentSync)
                .previousDeviceSyncCount(previousSync)
                .deviceSyncChangePercent(calcChangePercent(currentSync, previousSync))
                .currentAiRecognizeCount(currentAi)
                .previousAiRecognizeCount(previousAi)
                .aiRecognizeChangePercent(calcChangePercent(currentAi, previousAi))
                .currentAvgScore(currentAvgScore)
                .previousAvgScore(previousAvgScore)
                .avgScoreChangePercent(calcChangePercent(currentAvgScore, previousAvgScore))
                .build();
    }

    @Override
    public MonthlyStatsVO getMonthlyStats(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        LocalDate currentMonthEnd = today;
        LocalDate previousMonthStart = currentMonthStart.minusMonths(1);
        LocalDate previousMonthEnd = currentMonthStart.minusDays(1);

        // Current month counts
        int currentHealth = countMetrics(userId, MetricCategory.HEALTH, currentMonthStart, currentMonthEnd);
        int currentWellness = countMetrics(userId, MetricCategory.WELLNESS, currentMonthStart, currentMonthEnd);
        int currentSync = countSyncHistory(userId, currentMonthStart.atStartOfDay(), currentMonthEnd.atTime(LocalTime.MAX));
        int currentAi = countAiParseHistory(userId, currentMonthStart.atStartOfDay(), currentMonthEnd.atTime(LocalTime.MAX));
        BigDecimal currentAvgScore = getAvgScore(userId, currentMonthStart, currentMonthEnd);

        // Previous month counts
        int previousHealth = countMetrics(userId, MetricCategory.HEALTH, previousMonthStart, previousMonthEnd);
        int previousWellness = countMetrics(userId, MetricCategory.WELLNESS, previousMonthStart, previousMonthEnd);
        int previousSync = countSyncHistory(userId, previousMonthStart.atStartOfDay(), previousMonthEnd.atTime(LocalTime.MAX));
        int previousAi = countAiParseHistory(userId, previousMonthStart.atStartOfDay(), previousMonthEnd.atTime(LocalTime.MAX));
        BigDecimal previousAvgScore = getAvgScore(userId, previousMonthStart, previousMonthEnd);

        // Active days this month
        int activeDays = countActiveDays(userId, currentMonthStart, currentMonthEnd);

        return MonthlyStatsVO.builder()
                .currentHealthMetricsCount(currentHealth)
                .previousHealthMetricsCount(previousHealth)
                .healthMetricsChangePercent(calcChangePercent(currentHealth, previousHealth))
                .currentWellnessMetricsCount(currentWellness)
                .previousWellnessMetricsCount(previousWellness)
                .wellnessMetricsChangePercent(calcChangePercent(currentWellness, previousWellness))
                .currentDeviceSyncCount(currentSync)
                .previousDeviceSyncCount(previousSync)
                .deviceSyncChangePercent(calcChangePercent(currentSync, previousSync))
                .currentAiRecognizeCount(currentAi)
                .previousAiRecognizeCount(previousAi)
                .aiRecognizeChangePercent(calcChangePercent(currentAi, previousAi))
                .currentAvgScore(currentAvgScore)
                .previousAvgScore(previousAvgScore)
                .avgScoreChangePercent(calcChangePercent(currentAvgScore, previousAvgScore))
                .activeDays(activeDays)
                .build();
    }

    @Override
    public List<TrendDataVO> getTrends(Long userId, String metricKey, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        log.debug("获取趋势数据: userId={}, metricKey={}, days={}, range=[{}, {}]",
                userId, metricKey, days, startDate, endDate);

        List<TrendDataVO> result = new ArrayList<>();

        if ("health_score".equals(metricKey)) {
            // Return health score trend from score history
            result.add(buildScoreTrend(userId, startDate, endDate));
        } else if ("metric_count".equals(metricKey)) {
            // Return daily metric count trend
            result.add(buildMetricCountTrend(userId, startDate, endDate));
        } else if (metricKey != null && !metricKey.isEmpty()) {
            // Return trend for a specific metric key
            TrendDataVO trend = buildMetricTrend(userId, metricKey, startDate, endDate);
            if (trend != null) {
                result.add(trend);
            }
        } else {
            // Return trends for all metric keys the user has recorded
            List<String> metricKeys = getUserMetricKeys(userId, startDate, endDate);
            for (String key : metricKeys) {
                TrendDataVO trend = buildMetricTrend(userId, key, startDate, endDate);
                if (trend != null) {
                    result.add(trend);
                }
            }
        }

        return result;
    }

    @Override
    public DashboardSummaryVO getDashboardSummary(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);

        // Today counts
        int todayHealth = countMetrics(userId, MetricCategory.HEALTH, today, today);
        int todayWellness = countMetrics(userId, MetricCategory.WELLNESS, today, today);
        int todaySync = countSyncHistory(userId, todayStart, todayEnd);
        int todayAi = countAiParseHistory(userId, todayStart, todayEnd);

        // Week counts
        int weekHealth = countMetrics(userId, MetricCategory.HEALTH, weekStart, today);
        int weekWellness = countMetrics(userId, MetricCategory.WELLNESS, weekStart, today);

        // Week active days
        int weekActiveDays = countActiveDays(userId, weekStart, today);

        // Latest health score
        BigDecimal latestScore = getLatestScore(userId);
        String scoreLevel = getScoreLevel(latestScore);

        // Alert counts
        int unreadAlerts = countUnreadAlerts(userId);
        int highSeverityAlerts = countHighSeverityAlerts(userId);

        // Total counts
        Long totalSync = syncHistoryMapper.selectCount(
            new LambdaQueryWrapper<SyncHistory>()
                .eq(SyncHistory::getUserId, userId)
        );
        Long totalAi = aiParseHistoryMapper.selectCount(
            new LambdaQueryWrapper<AiParseHistory>()
                .eq(AiParseHistory::getUserId, userId)
        );

        return DashboardSummaryVO.builder()
                .healthScore(latestScore)
                .healthScoreLevel(scoreLevel)
                .todayHealthMetricsCount(todayHealth)
                .todayWellnessMetricsCount(todayWellness)
                .weekHealthMetricsCount(weekHealth)
                .weekWellnessMetricsCount(weekWellness)
                .unreadAlertsCount(unreadAlerts)
                .highSeverityAlertsCount(highSeverityAlerts)
                .totalDeviceSyncCount(toInt(totalSync))
                .totalAiRecognizeCount(toInt(totalAi))
                .todayDeviceSyncCount(todaySync)
                .todayAiRecognizeCount(todayAi)
                .weekActiveDays(weekActiveDays)
                .build();
    }

    // ==================== Private helper methods ====================

    /**
     * Count health metrics by category within a date range
     */
    private int countMetrics(Long userId, MetricCategory category, LocalDate startDate, LocalDate endDate) {
        Long count = healthMetricMapper.selectCount(
            new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .eq(HealthMetric::getCategory, category)
                .ge(HealthMetric::getRecordDate, startDate)
                .le(HealthMetric::getRecordDate, endDate)
        );
        return toInt(count);
    }

    /**
     * Count device sync records within a time range
     */
    private int countSyncHistory(Long userId, LocalDateTime start, LocalDateTime end) {
        Long count = syncHistoryMapper.selectCount(
            new LambdaQueryWrapper<SyncHistory>()
                .eq(SyncHistory::getUserId, userId)
                .ge(SyncHistory::getCreateTime, start)
                .le(SyncHistory::getCreateTime, end)
        );
        return toInt(count);
    }

    /**
     * Count AI parse history within a time range
     */
    private int countAiParseHistory(Long userId, LocalDateTime start, LocalDateTime end) {
        Long count = aiParseHistoryMapper.selectCount(
            new LambdaQueryWrapper<AiParseHistory>()
                .eq(AiParseHistory::getUserId, userId)
                .ge(AiParseHistory::getCreateTime, start)
                .le(AiParseHistory::getCreateTime, end)
        );
        return toInt(count);
    }

    /**
     * Get average health score for a date range
     */
    private BigDecimal getAvgScore(Long userId, LocalDate startDate, LocalDate endDate) {
        List<HealthScoreHistory> scores = healthScoreHistoryMapper.selectList(
            new LambdaQueryWrapper<HealthScoreHistory>()
                .eq(HealthScoreHistory::getUserId, userId)
                .ge(HealthScoreHistory::getScoreDate, startDate)
                .le(HealthScoreHistory::getScoreDate, endDate)
                .orderByAsc(HealthScoreHistory::getScoreDate)
        );

        if (scores.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (HealthScoreHistory s : scores) {
            if (s.getOverallScore() != null) {
                sum = sum.add(s.getOverallScore());
            }
        }
        return sum.divide(BigDecimal.valueOf(scores.size()), 1, RoundingMode.HALF_UP);
    }

    /**
     * Get latest health score
     */
    private BigDecimal getLatestScore(Long userId) {
        HealthScoreHistory latest = healthScoreHistoryMapper.selectOne(
            new LambdaQueryWrapper<HealthScoreHistory>()
                .eq(HealthScoreHistory::getUserId, userId)
                .orderByDesc(HealthScoreHistory::getScoreDate)
                .last("LIMIT 1")
        );
        return latest != null && latest.getOverallScore() != null
                ? latest.getOverallScore()
                : BigDecimal.ZERO;
    }

    /**
     * Get score level from numeric score
     */
    private String getScoreLevel(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) == 0) {
            return "NO_DATA";
        }
        if (score.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "EXCELLENT";
        }
        if (score.compareTo(BigDecimal.valueOf(70)) >= 0) {
            return "GOOD";
        }
        if (score.compareTo(BigDecimal.valueOf(50)) >= 0) {
            return "FAIR";
        }
        return "POOR";
    }

    /**
     * Count unread alerts
     */
    private int countUnreadAlerts(Long userId) {
        Long count = healthAlertMapper.selectCount(
            new LambdaQueryWrapper<HealthAlert>()
                .eq(HealthAlert::getUserId, userId)
                .eq(HealthAlert::getIsRead, false)
        );
        return toInt(count);
    }

    /**
     * Count high-severity unread alerts
     */
    private int countHighSeverityAlerts(Long userId) {
        Long count = healthAlertMapper.selectCount(
            new LambdaQueryWrapper<HealthAlert>()
                .eq(HealthAlert::getUserId, userId)
                .eq(HealthAlert::getAlertLevel, "HIGH")
                .eq(HealthAlert::getIsRead, false)
        );
        return toInt(count);
    }

    /**
     * Count active days (days with at least one metric record) in a date range
     */
    private int countActiveDays(Long userId, LocalDate startDate, LocalDate endDate) {
        List<HealthMetric> metrics = healthMetricMapper.selectList(
            new LambdaQueryWrapper<HealthMetric>()
                .select(HealthMetric::getRecordDate)
                .eq(HealthMetric::getUserId, userId)
                .ge(HealthMetric::getRecordDate, startDate)
                .le(HealthMetric::getRecordDate, endDate)
                .groupBy(HealthMetric::getRecordDate)
        );
        return metrics.size();
    }

    /**
     * Build health score trend data
     */
    private TrendDataVO buildScoreTrend(Long userId, LocalDate startDate, LocalDate endDate) {
        List<HealthScoreHistory> scores = healthScoreHistoryMapper.selectList(
            new LambdaQueryWrapper<HealthScoreHistory>()
                .eq(HealthScoreHistory::getUserId, userId)
                .ge(HealthScoreHistory::getScoreDate, startDate)
                .le(HealthScoreHistory::getScoreDate, endDate)
                .orderByAsc(HealthScoreHistory::getScoreDate)
        );

        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (HealthScoreHistory s : scores) {
            dates.add(s.getScoreDate());
            values.add(s.getOverallScore() != null ? s.getOverallScore() : BigDecimal.ZERO);
        }

        return TrendDataVO.builder()
                .metricKey("health_score")
                .metricDisplayName("健康评分")
                .unit("分")
                .dates(dates)
                .values(values)
                .build();
    }

    /**
     * Build daily metric count trend
     */
    private TrendDataVO buildMetricCountTrend(Long userId, LocalDate startDate, LocalDate endDate) {
        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            Long count = healthMetricMapper.selectCount(
                new LambdaQueryWrapper<HealthMetric>()
                    .eq(HealthMetric::getUserId, userId)
                    .eq(HealthMetric::getRecordDate, current)
            );
            dates.add(current);
            values.add(BigDecimal.valueOf(toInt(count)));
            current = current.plusDays(1);
        }

        return TrendDataVO.builder()
                .metricKey("metric_count")
                .metricDisplayName("每日指标录入数")
                .unit("条")
                .dates(dates)
                .values(values)
                .build();
    }

    /**
     * Build trend for a specific metric key
     */
    private TrendDataVO buildMetricTrend(Long userId, String metricKey, LocalDate startDate, LocalDate endDate) {
        List<HealthMetric> metrics = healthMetricMapper.selectList(
            new LambdaQueryWrapper<HealthMetric>()
                .eq(HealthMetric::getUserId, userId)
                .eq(HealthMetric::getMetricKey, metricKey)
                .ge(HealthMetric::getRecordDate, startDate)
                .le(HealthMetric::getRecordDate, endDate)
                .orderByAsc(HealthMetric::getRecordDate)
        );

        if (metrics.isEmpty()) {
            return null;
        }

        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        String unit = null;
        for (HealthMetric m : metrics) {
            dates.add(m.getRecordDate());
            values.add(m.getValue());
            if (unit == null && m.getUnit() != null) {
                unit = m.getUnit();
            }
        }

        return TrendDataVO.builder()
                .metricKey(metricKey)
                .metricDisplayName(metricKey)
                .unit(unit != null ? unit : "")
                .dates(dates)
                .values(values)
                .build();
    }

    /**
     * Get distinct metric keys for a user in a date range
     */
    private List<String> getUserMetricKeys(Long userId, LocalDate startDate, LocalDate endDate) {
        List<HealthMetric> metrics = healthMetricMapper.selectList(
            new LambdaQueryWrapper<HealthMetric>()
                .select(HealthMetric::getMetricKey)
                .eq(HealthMetric::getUserId, userId)
                .ge(HealthMetric::getRecordDate, startDate)
                .le(HealthMetric::getRecordDate, endDate)
                .groupBy(HealthMetric::getMetricKey)
        );

        List<String> keys = new ArrayList<>();
        for (HealthMetric m : metrics) {
            if (m.getMetricKey() != null) {
                keys.add(m.getMetricKey());
            }
        }
        return keys;
    }

    /**
     * Calculate percentage change between current and previous values.
     * Returns null if previous is zero (avoid division by zero).
     */
    private BigDecimal calcChangePercent(int current, int previous) {
        if (previous == 0) {
            return current == 0 ? BigDecimal.ZERO : null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP);
    }

    /**
     * Calculate percentage change for BigDecimal values.
     */
    private BigDecimal calcChangePercent(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return (current == null || current.compareTo(BigDecimal.ZERO) == 0)
                    ? BigDecimal.ZERO : null;
        }
        if (current == null) {
            current = BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    /**
     * Safely convert Long to int, handling null
     */
    private int toInt(Long value) {
        return value != null ? value.intValue() : 0;
    }
}