package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.common.enums.MetricCategory;
import com.hhs.entity.AiParseHistory;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.OcrHealthRecord;
import com.hhs.entity.SyncHistory;
import com.hhs.mapper.AiParseHistoryMapper;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.OcrHealthRecordMapper;
import com.hhs.mapper.SyncHistoryMapper;
import com.hhs.service.StatsService;
import com.hhs.vo.TodayStatsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 统计服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final HealthMetricMapper healthMetricMapper;
    private final AiParseHistoryMapper aiParseHistoryMapper;
    private final OcrHealthRecordMapper ocrHealthRecordMapper;
    private final SyncHistoryMapper syncHistoryMapper;

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
                .healthMetricsCount(healthMetricsCount != null ? healthMetricsCount.intValue() : 0)
                .wellnessMetricsCount(wellnessMetricsCount != null ? wellnessMetricsCount.intValue() : 0)
                .deviceSyncCount(deviceSyncCount != null ? deviceSyncCount.intValue() : 0)
                .aiRecognizeCount(aiRecognizeCount != null ? aiRecognizeCount.intValue() : 0)
                .build();
    }
}