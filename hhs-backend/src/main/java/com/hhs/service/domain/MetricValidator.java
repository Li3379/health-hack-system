package com.hhs.service.domain;

import com.hhs.entity.RealtimeMetric;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.RealtimeMetricMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Health metric validation rules
 * Responsible for validating user has sufficient health data for score calculation
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricValidator {

    private final RealtimeMetricMapper metricMapper;

    /**
     * Validate that user has health data
     *
     * @param userId the user ID
     * @throws BusinessException if user has no health data
     */
    public void validateUserHasData(Long userId) {
        Long count = metricMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RealtimeMetric>()
                        .eq(RealtimeMetric::getUserId, userId)
        );

        if (count == 0) {
            log.warn("User {} has no health metrics", userId);
            throw new BusinessException(com.hhs.common.constant.ErrorCode.HEALTH_METRIC_NOT_FOUND, "用户无健康数据");
        }
    }

    /**
     * Check if user has any health data
     *
     * @param userId the user ID
     * @return true if user has at least one health metric
     */
    public boolean hasAnyData(Long userId) {
        Long count = metricMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RealtimeMetric>()
                        .eq(RealtimeMetric::getUserId, userId)
        );
        return count > 0;
    }

    /**
     * Validate metric completeness for calculation
     *
     * @param userId the user ID
     * @return true if user has sufficient metrics for accurate calculation
     */
    public boolean hasSufficientMetrics(Long userId) {
        Long count = metricMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RealtimeMetric>()
                        .eq(RealtimeMetric::getUserId, userId)
        );

        // Consider 3+ metrics as sufficient for reasonable accuracy
        return count >= 3;
    }
}
