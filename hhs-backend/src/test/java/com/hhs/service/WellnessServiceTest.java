package com.hhs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.constant.ErrorCode;
import com.hhs.common.enums.MetricCategory;
import com.hhs.dto.WellnessMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.RealtimeMetric;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.domain.MetricCategoryService;
import com.hhs.service.domain.MetricDisplayFormatter;
import com.hhs.service.impl.WellnessServiceImpl;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.HealthMetricVO;
import com.hhs.vo.WellnessSummaryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Wellness Service Unit Tests
 *
 * Tests the WellnessService which manages wellness metric data with category filtering
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("保健指标服务测试")
class WellnessServiceTest {

    @Mock
    private HealthMetricMapper healthMetricMapper;

    @Mock
    private RealtimeMetricMapper realtimeMetricMapper;

    @Mock
    private MetricCategoryService metricCategoryService;

    @Mock
    private MetricDisplayFormatter metricDisplayFormatter;

    @InjectMocks
    private WellnessServiceImpl wellnessService;

    private WellnessMetricRequest testRequest;
    private HealthMetric testMetric;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        testRequest = new WellnessMetricRequest();
        testRequest.setMetricKey("sleepDuration");
        testRequest.setValue(new BigDecimal("7.5"));
        testRequest.setRecordDate(LocalDate.now());
        testRequest.setUnit("小时");

        testMetric = new HealthMetric();
        testMetric.setId(1L);
        testMetric.setUserId(testUserId);
        testMetric.setMetricKey("sleepDuration");
        testMetric.setValue(new BigDecimal("7.5"));
        testMetric.setUnit("小时");
        testMetric.setRecordDate(LocalDate.now());
        testMetric.setCategory(MetricCategory.WELLNESS);
        testMetric.setCreateTime(LocalDateTime.now());
    }

    @Test
    @DisplayName("测试1.1：创建保健指标 - 成功场景")
    void testCreateWellnessMetric_Success() {
        // Given
        when(metricCategoryService.isWellnessMetric("sleepDuration")).thenReturn(true);
        when(metricDisplayFormatter.getUnit("sleepDuration")).thenReturn("小时");
        when(healthMetricMapper.insert(any(HealthMetric.class))).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(1L);
            return 1;
        });
        when(realtimeMetricMapper.insert(any(RealtimeMetric.class))).thenReturn(1);

        // When
        HealthMetricVO result = wellnessService.createWellnessMetric(testUserId, testRequest);

        // Then
        assertNotNull(result);
        assertEquals("sleepDuration", result.metricKey());
        assertEquals(new BigDecimal("7.5"), result.value());
        verify(healthMetricMapper, times(1)).insert(any(HealthMetric.class));
    }

    @Test
    @DisplayName("测试1.2：创建保健指标 - 无效指标类型")
    void testCreateWellnessMetric_InvalidMetricKey() {
        // Given
        when(metricCategoryService.isWellnessMetric("invalidKey")).thenReturn(false);
        testRequest.setMetricKey("invalidKey");

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            wellnessService.createWellnessMetric(testUserId, testRequest);
        });

        assertTrue(exception.getMessage().contains("无效的保健指标类型"));
        verify(healthMetricMapper, never()).insert(any());
    }

    @Test
    @DisplayName("测试1.3：创建保健指标 - 数值超出范围")
    void testCreateWellnessMetric_ValueOutOfRange() {
        // Given
        when(metricCategoryService.isWellnessMetric("sleepDuration")).thenReturn(true);
        testRequest.setValue(new BigDecimal("30")); // Sleep can't be 30 hours

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            wellnessService.createWellnessMetric(testUserId, testRequest);
        });

        assertTrue(exception.getMessage().contains("必须在"));
    }

    @Test
    @DisplayName("测试2.1：查询保健指标列表 - 成功场景")
    void testGetWellnessMetrics_Success() {
        // Given
        Page<HealthMetric> pageResult = new Page<>(1, 10);
        pageResult.setRecords(Arrays.asList(testMetric));
        pageResult.setTotal(1);

        when(healthMetricMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);

        // When
        Page<HealthMetricVO> result = wellnessService.getWellnessMetrics(testUserId, null, null, null, 1, 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("测试2.2：查询保健指标列表 - 带筛选条件")
    void testGetWellnessMetrics_WithFilters() {
        // Given
        Page<HealthMetric> pageResult = new Page<>(1, 10);
        pageResult.setRecords(Arrays.asList(testMetric));
        pageResult.setTotal(1);

        when(healthMetricMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);

        // When
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        Page<HealthMetricVO> result = wellnessService.getWellnessMetrics(
                testUserId, "sleepDuration", startDate, endDate, 1, 10);

        // Then
        assertNotNull(result);
        verify(healthMetricMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("测试3.1：更新保健指标 - 成功场景")
    void testUpdateWellnessMetric_Success() {
        // Given
        when(healthMetricMapper.selectById(1L)).thenReturn(testMetric);
        when(metricCategoryService.isWellnessMetric("sleepDuration")).thenReturn(true);
        when(metricDisplayFormatter.getUnit("sleepDuration")).thenReturn("小时");
        when(healthMetricMapper.updateById(any(HealthMetric.class))).thenReturn(1);
        when(realtimeMetricMapper.deleteByHealthMetric(any(), any(), any(), any())).thenReturn(1);
        when(realtimeMetricMapper.insert(any(RealtimeMetric.class))).thenReturn(1);

        testRequest.setValue(new BigDecimal("8.0"));

        // When
        HealthMetricVO result = wellnessService.updateWellnessMetric(testUserId, 1L, testRequest);

        // Then
        assertNotNull(result);
        verify(healthMetricMapper, times(1)).updateById(any(HealthMetric.class));
    }

    @Test
    @DisplayName("测试3.2：更新保健指标 - 无权限更新他人记录")
    void testUpdateWellnessMetric_Unauthorized() {
        // Given
        HealthMetric otherUserMetric = new HealthMetric();
        otherUserMetric.setId(2L);
        otherUserMetric.setUserId(999L); // Different user
        otherUserMetric.setMetricKey("sleepDuration");

        when(healthMetricMapper.selectById(2L)).thenReturn(otherUserMetric);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            wellnessService.updateWellnessMetric(testUserId, 2L, testRequest);
        });

        assertTrue(exception.getMessage().contains("无权"));
        verify(healthMetricMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("测试4.1：删除保健指标 - 成功场景")
    void testDeleteWellnessMetric_Success() {
        // Given
        when(healthMetricMapper.selectById(1L)).thenReturn(testMetric);
        when(realtimeMetricMapper.deleteByHealthMetric(any(), any(), any(), any())).thenReturn(1);
        when(healthMetricMapper.deleteById(1L)).thenReturn(1);

        // When
        wellnessService.deleteWellnessMetric(testUserId, 1L);

        // Then
        verify(healthMetricMapper, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("测试4.2：删除保健指标 - 无权限删除他人记录")
    void testDeleteWellnessMetric_Unauthorized() {
        // Given
        HealthMetric otherUserMetric = new HealthMetric();
        otherUserMetric.setId(2L);
        otherUserMetric.setUserId(999L);
        otherUserMetric.setMetricKey("sleepDuration");

        when(healthMetricMapper.selectById(2L)).thenReturn(otherUserMetric);

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            wellnessService.deleteWellnessMetric(testUserId, 2L);
        });

        assertTrue(exception.getMessage().contains("无权"));
        verify(healthMetricMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("测试5.1：获取趋势数据 - 有数据")
    void testGetTrend_WithData() {
        // Given
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(testMetric));

        // When
        HealthMetricTrendVO result = wellnessService.getTrend(
                testUserId, "sleepDuration", LocalDate.now().minusDays(7), LocalDate.now());

        // Then
        assertNotNull(result);
        assertEquals("sleepDuration", result.metricKey());
        assertEquals(1, result.dates().size());
    }

    @Test
    @DisplayName("测试5.2：获取趋势数据 - 空结果")
    void testGetTrend_EmptyResult() {
        // Given
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // When
        HealthMetricTrendVO result = wellnessService.getTrend(
                testUserId, "sleepDuration", LocalDate.now().minusDays(7), LocalDate.now());

        // Then
        assertNotNull(result);
        assertTrue(result.dates().isEmpty());
        assertTrue(result.values().isEmpty());
    }

    @Test
    @DisplayName("测试6.1：获取汇总数据 - 成功场景")
    void testGetSummary_Success() {
        // Given
        HealthMetric stepsMetric = new HealthMetric();
        stepsMetric.setUserId(testUserId);
        stepsMetric.setMetricKey("steps");
        stepsMetric.setValue(new BigDecimal("10000"));
        stepsMetric.setRecordDate(LocalDate.now());
        stepsMetric.setCategory(MetricCategory.WELLNESS);

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(testMetric, stepsMetric));
        when(metricCategoryService.getWellnessMetrics()).thenReturn(Set.of("sleepDuration", "steps"));
        when(metricDisplayFormatter.getDisplayName(any())).thenReturn("显示名称");

        // When
        WellnessSummaryVO result = wellnessService.getSummary(testUserId, 7);

        // Then
        assertNotNull(result);
        assertEquals(LocalDate.now(), result.summaryDate());
    }

    @Test
    @DisplayName("测试7.1：获取最新指标 - 成功场景")
    void testGetLatestMetrics_Success() {
        // Given
        when(metricCategoryService.getWellnessMetrics()).thenReturn(Set.of("sleepDuration", "steps"));
        when(healthMetricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testMetric);

        // When
        Map<String, HealthMetricVO> result = wellnessService.getLatestMetrics(testUserId);

        // Then
        assertNotNull(result);
        assertTrue(result.containsKey("sleepDuration"));
    }

    @Test
    @DisplayName("测试7.2：获取最新指标 - 无数据")
    void testGetLatestMetrics_NoData() {
        // Given
        when(metricCategoryService.getWellnessMetrics()).thenReturn(Set.of("sleepDuration", "steps"));
        when(healthMetricMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When
        Map<String, HealthMetricVO> result = wellnessService.getLatestMetrics(testUserId);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("测试8.1：验证睡眠时长范围 (0-24小时)")
    void testValidateRange_SleepDuration() {
        // Given
        when(metricCategoryService.isWellnessMetric("sleepDuration")).thenReturn(true);
        when(metricDisplayFormatter.getDisplayName("sleepDuration")).thenReturn("睡眠时长");
        testRequest.setValue(new BigDecimal("25")); // Invalid: > 24

        // When & Then
        assertThrows(BusinessException.class, () -> {
            wellnessService.createWellnessMetric(testUserId, testRequest);
        });
    }

    @Test
    @DisplayName("测试8.2：验证步数范围 (0-100000)")
    void testValidateRange_Steps() {
        // Given
        when(metricCategoryService.isWellnessMetric("steps")).thenReturn(true);
        when(metricDisplayFormatter.getDisplayName("steps")).thenReturn("步数");
        testRequest.setMetricKey("steps");
        testRequest.setValue(new BigDecimal("150000")); // Invalid: > 100000

        // When & Then
        assertThrows(BusinessException.class, () -> {
            wellnessService.createWellnessMetric(testUserId, testRequest);
        });
    }

    @Test
    @DisplayName("测试8.3：验证心情范围 (1-5级)")
    void testValidateRange_Mood() {
        // Given
        when(metricCategoryService.isWellnessMetric("mood")).thenReturn(true);
        when(metricDisplayFormatter.getDisplayName("mood")).thenReturn("心情");
        testRequest.setMetricKey("mood");
        testRequest.setValue(new BigDecimal("6")); // Invalid: > 5

        // When & Then
        assertThrows(BusinessException.class, () -> {
            wellnessService.createWellnessMetric(testUserId, testRequest);
        });
    }
}