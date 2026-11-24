package com.hhs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.enums.MetricCategory;
import com.hhs.domain.event.MetricRecordedEvent;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.entity.UserThreshold;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.MetricCategoryService;
import com.hhs.service.domain.MetricDefinition;
import com.hhs.service.domain.MetricDisplayFormatter;
import com.hhs.service.domain.ThresholdEvaluator;
import com.hhs.service.domain.ThresholdRangeChecker;
import com.hhs.service.impl.HealthMetricServiceImpl;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.HealthMetricVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Health Metric Service Unit Tests
 *
 * Tests the HealthMetricService which manages health metric data
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("健康指标服务测试")
class HealthMetricServiceTest {

    @Mock
    private HealthMetricMapper healthMetricMapper;

    @Mock
    private RealtimeMetricMapper realtimeMetricMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private MetricDisplayFormatter metricDisplayFormatter;

    @Mock
    private MetricCategoryService metricCategoryService;

    @Mock
    private HealthMetricMetaRegistry metricRegistry;

    @Mock
    private ThresholdEvaluator thresholdEvaluator;

    @Mock
    private ThresholdRangeChecker rangeChecker;

    @InjectMocks
    private HealthMetricServiceImpl healthMetricService;

    private HealthMetricRequest testRequest;
    private HealthMetric testMetric;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        // Set up lenient mocks for optional dependencies
        lenient().when(metricDisplayFormatter.getDisplayName(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(metricDisplayFormatter.getUnit(anyString())).thenReturn("bpm");
        lenient().when(metricCategoryService.determineCategory(anyString()))
                .thenReturn(MetricCategory.HEALTH);
        lenient().when(metricRegistry.getDefinition(anyString())).thenReturn(Optional.empty());

        testRequest = new HealthMetricRequest();
        testRequest.setUserId(testUserId);
        testRequest.setMetricKey("heart_rate");
        testRequest.setValue(new BigDecimal("75.0"));
        testRequest.setUnit("bpm");
        testRequest.setRecordDate(LocalDate.now());
        testRequest.setTrend("normal");

        testMetric = new HealthMetric();
        testMetric.setId(1L);
        testMetric.setUserId(testUserId);
        testMetric.setMetricKey("heart_rate");
        testMetric.setValue(new BigDecimal("75.0"));
        testMetric.setUnit("bpm");
        testMetric.setRecordDate(LocalDate.now());
        testMetric.setTrend("normal");
        testMetric.setCreateTime(LocalDateTime.now());
    }

    @Test
    @DisplayName("测试1.1：添加健康指标 - 成功场景")
    void testAdd_Success() {
        // Given: Mapper will successfully insert
        when(healthMetricMapper.insert(any(HealthMetric.class))).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(1L);
            return 1;
        });
        doNothing().when(eventPublisher).publishEvent(any(MetricRecordedEvent.class));

        // When: Add metric
        HealthMetricVO result = healthMetricService.add(testUserId, testRequest);

        // Then: Verify result
        assertNotNull(result);
        assertEquals("heartRate", result.metricKey());
        assertEquals(new BigDecimal("75.0"), result.value());
        verify(healthMetricMapper, times(1)).insert(any(HealthMetric.class));
        verify(eventPublisher, times(1)).publishEvent(any(MetricRecordedEvent.class));
    }

    @Test
    @DisplayName("测试1.2：添加健康指标 - 带默认值")
    void testAdd_WithDefaults() {
        // Given: Request with null optional fields
        HealthMetricRequest request = new HealthMetricRequest();
        request.setUserId(testUserId);
        request.setMetricKey("blood_pressure");
        request.setValue(new BigDecimal("120"));
        request.setUnit(null); // Will default to empty string
        request.setRecordDate(LocalDate.now());
        request.setTrend(null); // Will default to "NORMAL"

        when(healthMetricMapper.insert(any(HealthMetric.class))).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(2L);
            return 1;
        });
        doNothing().when(eventPublisher).publishEvent(any(MetricRecordedEvent.class));

        // When: Add metric
        HealthMetricVO result = healthMetricService.add(testUserId, request);

        // Then: Verify defaults applied
        assertNotNull(result);
        assertEquals("blood_pressure", result.metricKey());
        verify(eventPublisher, times(1)).publishEvent(any(MetricRecordedEvent.class));
    }

    @Test
    @DisplayName("测试1.3：查询健康指标 - 无筛选条件")
    void testList_NoFilters() {
        // Given: Mock metric list
        List<HealthMetric> metrics = Arrays.asList(testMetric);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);

        // When: List metrics
        List<HealthMetricVO> result = healthMetricService.list(testUserId, null, null, null);

        // Then: Verify results
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("heart_rate", result.get(0).metricKey());
        verify(healthMetricMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("测试1.4：查询健康指标 - 带类型筛选")
    void testList_WithTypeFilter() {
        // Given: Mock metric list
        List<HealthMetric> metrics = Arrays.asList(testMetric);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);

        // When: List metrics by type
        List<HealthMetricVO> result = healthMetricService.list(testUserId, "heart_rate", null, null);

        // Then: Verify results
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(healthMetricMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("测试1.5：查询健康指标 - 带日期范围筛选")
    void testList_WithDateRange() {
        // Given: Mock metric list
        List<HealthMetric> metrics = Arrays.asList(testMetric);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);

        // When: List metrics by date range
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        List<HealthMetricVO> result = healthMetricService.list(testUserId, null, startDate, endDate);

        // Then: Verify results
        assertNotNull(result);
        verify(healthMetricMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("测试1.6：查询健康指标 - 空结果")
    void testList_EmptyResult() {
        // Given: Mock empty list
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // When: List metrics
        List<HealthMetricVO> result = healthMetricService.list(testUserId, "glucose", null, null);

        // Then: Verify empty result
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("测试1.7：获取趋势数据 - 有数据")
    void testGetTrend_WithData() {
        // Given: Mock metric list
        List<HealthMetric> metrics = Arrays.asList(testMetric);
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(metrics);

        // When: Get trend
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        List<HealthMetricTrendVO> result = healthMetricService.getTrend(testUserId, "heart_rate", startDate, endDate);

        // Then: Verify trend
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("heartRate", result.get(0).metricKey());
    }

    @Test
    @DisplayName("测试1.8：获取趋势数据 - 空结果")
    void testGetTrend_EmptyResult() {
        // Given: Mock empty list
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // When: Get trend
        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();
        List<HealthMetricTrendVO> result = healthMetricService.getTrend(testUserId, "glucose", startDate, endDate);

        // Then: Verify empty result
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("测试1.9：删除健康指标 - 成功场景")
    void testDelete_Success() {
        // Given: Mock metric exists and user owns it
        when(healthMetricMapper.selectById(1L)).thenReturn(testMetric);
        when(realtimeMetricMapper.deleteByHealthMetric(eq(testUserId), eq("heart_rate"), any(), any())).thenReturn(1);
        when(healthMetricMapper.deleteById(1L)).thenReturn(1);

        // When: Delete metric
        boolean result = healthMetricService.delete(testUserId, 1L);

        // Then: Verify deletion
        assertTrue(result);
        verify(healthMetricMapper, times(1)).selectById(1L);
        verify(realtimeMetricMapper, times(1)).deleteByHealthMetric(eq(testUserId), eq("heart_rate"), any(), any());
        verify(healthMetricMapper, times(1)).deleteById((Serializable) any());
    }

    @Test
    @DisplayName("测试1.10：删除健康指标 - 指标不存在（幂等）")
    void testDelete_NotFound() {
        // Given: Mock metric doesn't exist
        when(healthMetricMapper.selectById(999L)).thenReturn(null);

        // When: Delete non-existent metric
        boolean result = healthMetricService.delete(testUserId, 999L);

        // Then: Verify idempotent behavior
        assertTrue(result);
        verify(healthMetricMapper, times(1)).selectById(999L);
        verify(healthMetricMapper, never()).deleteById((Serializable) any());
    }

    @Test
    @DisplayName("测试1.11：删除健康指标 - 无权限删除他人记录")
    void testDelete_Unauthorized() {
        // Given: Mock metric belongs to different user
        HealthMetric otherUserMetric = new HealthMetric();
        otherUserMetric.setId(2L);
        otherUserMetric.setUserId(999L); // Different user
        otherUserMetric.setMetricKey("heart_rate");
        otherUserMetric.setRecordDate(LocalDate.now());

        when(healthMetricMapper.selectById(2L)).thenReturn(otherUserMetric);

        // When & Then: Should throw exception
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            healthMetricService.delete(testUserId, 2L);
        });

        assertTrue(exception.getMessage().contains("无权删除"));
        verify(healthMetricMapper, never()).deleteById((Serializable) any());
    }

    @Test
    @DisplayName("测试1.12：分页查询健康指标")
    void testList_Paginated() {
        // Given: Mock paginated result
        Page<HealthMetric> pageParam = new Page<>(1, 10);
        pageParam.setRecords(Arrays.asList(testMetric));
        pageParam.setTotal(1);

        when(healthMetricMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageParam);

        // When: List with pagination
        Page<HealthMetricVO> result = healthMetricService.list(1, 10, testUserId, null, null, null);

        // Then: Verify pagination
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("测试1.13：创建健康指标（简化CRUD）")
    void testCreate() {
        // Given: Mock insert
        when(healthMetricMapper.insert(any(HealthMetric.class))).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(3L);
            return 1;
        });
        doNothing().when(eventPublisher).publishEvent(any(MetricRecordedEvent.class));

        // When: Create metric
        HealthMetric result = healthMetricService.create(testRequest);

        // Then: Verify creation
        assertNotNull(result);
        assertEquals(3L, result.getId());
        verify(healthMetricMapper, times(1)).insert(any(HealthMetric.class));
    }

    @Test
    @DisplayName("测试1.14：更新健康指标（简化CRUD）")
    void testUpdate() {
        // Given: Mock existing metric
        when(healthMetricMapper.selectById(1L)).thenReturn(testMetric);
        when(healthMetricMapper.updateById(any(HealthMetric.class))).thenReturn(1);

        // When: Update metric
        testRequest.setValue(new BigDecimal("80.0"));
        HealthMetric result = healthMetricService.update(1L, testRequest);

        // Then: Verify update
        assertNotNull(result);
        verify(healthMetricMapper, times(1)).selectById(1L);
        verify(healthMetricMapper, times(1)).updateById(any(HealthMetric.class));
    }

    @Test
    @DisplayName("测试1.15：更新健康指标 - 指标不存在")
    void testUpdate_NotFound() {
        // Given: Mock metric doesn't exist
        when(healthMetricMapper.selectById(999L)).thenReturn(null);

        // When & Then: Should throw exception
        assertThrows(BusinessException.class, () -> {
            healthMetricService.update(999L, testRequest);
        });

        verify(healthMetricMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("测试1.16：删除健康指标（简化CRUD，直接删除）")
    void testDeleteById() {
        // Given: Mock metric exists
        when(healthMetricMapper.selectById(1L)).thenReturn(testMetric);
        when(realtimeMetricMapper.deleteByHealthMetric(eq(testUserId), eq("heart_rate"), any(), any())).thenReturn(1);
        when(healthMetricMapper.deleteById(1L)).thenReturn(1);

        // When: Delete by ID
        healthMetricService.delete(1L);

        // Then: Verify deletion
        verify(healthMetricMapper, times(1)).selectById(1L);
        verify(realtimeMetricMapper, times(1)).deleteByHealthMetric(eq(testUserId), eq("heart_rate"), any(), any());
        verify(healthMetricMapper, times(1)).deleteById((Serializable) any());
    }

    @Test
    @DisplayName("测试1.17：删除健康指标（简化CRUD，指标不存在）")
    void testDeleteById_NotFound() {
        // Given: Mock metric doesn't exist
        when(healthMetricMapper.selectById(999L)).thenReturn(null);

        // When: Delete non-existent metric (should not throw)
        healthMetricService.delete(999L);

        // Then: Verify graceful handling
        verify(healthMetricMapper, times(1)).selectById(999L);
        verify(healthMetricMapper, never()).deleteById((Serializable) any());
    }

    @Test
    @DisplayName("测试1.18：自动趋势计算 - 无历史记录时默认 STABLE")
    void testAutoTrend_NoHistory_ReturnsStable() {
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(healthMetricMapper.insert(any(HealthMetric.class))).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(10L);
            return 1;
        });
        HealthMetricRequest req = new HealthMetricRequest();
        req.setMetricKey("heartRate");
        req.setValue(new BigDecimal("75.0"));
        req.setRecordDate(LocalDate.now());

        HealthMetricVO vo = healthMetricService.add(testUserId, req);
        assertEquals("STABLE", vo.trend());
    }

    @Test
    @DisplayName("测试1.19：自动趋势计算 - 高于前值判定为 UP")
    void testAutoTrend_HigherValue_ReturnsUp() {
        HealthMetric previous = new HealthMetric();
        previous.setId(5L);
        previous.setUserId(testUserId);
        previous.setMetricKey("heartRate");
        previous.setValue(new BigDecimal("70.0"));
        previous.setRecordDate(LocalDate.now().minusDays(1));

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(previous));

        when(healthMetricMapper.insert(any(HealthMetric.class))).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(11L);
            return 1;
        });

        HealthMetricRequest req = new HealthMetricRequest();
        req.setMetricKey("heartRate");
        req.setValue(new BigDecimal("80.0"));
        req.setRecordDate(LocalDate.now());

        HealthMetricVO vo = healthMetricService.add(testUserId, req);
        assertEquals("UP", vo.trend());
    }

    @Test
    @DisplayName("测试1.20：自动趋势计算 - 低于前值判定为 DOWN")
    void testAutoTrend_LowerValue_ReturnsDown() {
        HealthMetric previous = new HealthMetric();
        previous.setId(5L);
        previous.setUserId(testUserId);
        previous.setMetricKey("heartRate");
        previous.setValue(new BigDecimal("80.0"));
        previous.setRecordDate(LocalDate.now().minusDays(1));

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList())
                .thenReturn(List.of(previous));

        when(healthMetricMapper.insert(any(HealthMetric.class))).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(12L);
            return 1;
        });

        HealthMetricRequest req = new HealthMetricRequest();
        req.setMetricKey("heartRate");
        req.setValue(new BigDecimal("72.0"));
        req.setRecordDate(LocalDate.now());

        HealthMetricVO vo = healthMetricService.add(testUserId, req);
        assertEquals("DOWN", vo.trend());
    }

    @Test
    @DisplayName("测试1.21：显式传入有效趋势参数时予以保留")
    void testAutoTrend_ExplicitTrendRespected() {
        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(healthMetricMapper.insert(any(HealthMetric.class))).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(13L);
            return 1;
        });

        HealthMetricRequest req = new HealthMetricRequest();
        req.setMetricKey("heartRate");
        req.setValue(new BigDecimal("80.0"));
        req.setRecordDate(LocalDate.now());
        req.setTrend("DOWN");

        HealthMetricVO vo = healthMetricService.add(testUserId, req);
        assertEquals("DOWN", vo.trend());
    }

    @Test
    @DisplayName("测试1.22：指标状态计算 - 危急偏高判定")
    void testStatus_CriticalHigh() {
        UserThreshold threshold = new UserThreshold();
        threshold.setMetricKey("systolicBloodPressure");
        when(thresholdEvaluator.getThresholdForUser(testUserId, "systolicBloodPressure"))
                .thenReturn(threshold);
        when(rangeChecker.bandOf(eq(185.0), eq(threshold)))
                .thenReturn(ThresholdRangeChecker.Band.CRITICAL_HIGH);

        HealthMetric metric = new HealthMetric();
        metric.setId(20L);
        metric.setUserId(testUserId);
        metric.setMetricKey("systolicBloodPressure");
        metric.setValue(new BigDecimal("185.0"));
        metric.setUnit("mmHg");
        metric.setRecordDate(LocalDate.now());
        metric.setTrend("UP");

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(metric));

        List<HealthMetricVO> list =
                healthMetricService.list(testUserId, "systolicBloodPressure", null, null);
        assertFalse(list.isEmpty());
        HealthMetricVO vo = list.get(0);
        assertEquals("CRITICAL_HIGH", vo.status());
        assertEquals("危急偏高", vo.statusText());
        assertEquals("HIGH", vo.alertLevel());
    }

    @Test
    @DisplayName("测试1.23：指标状态计算 - 元数据默认范围警告偏高")
    void testStatus_MetaRegistryFallback_WarningHigh() {
        when(thresholdEvaluator.getThresholdForUser(testUserId, "bloodGlucose")).thenReturn(null);
        MetricDefinition def = mock(MetricDefinition.class);
        when(def.normalMax()).thenReturn(new BigDecimal("6.1"));
        when(metricRegistry.getDefinition("bloodGlucose")).thenReturn(Optional.of(def));

        HealthMetric metric = new HealthMetric();
        metric.setId(21L);
        metric.setUserId(testUserId);
        metric.setMetricKey("bloodGlucose");
        metric.setValue(new BigDecimal("8.5"));
        metric.setUnit("mmol/L");
        metric.setRecordDate(LocalDate.now());
        metric.setTrend("STABLE");

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(metric));

        List<HealthMetricVO> list =
                healthMetricService.list(testUserId, "bloodGlucose", null, null);
        assertFalse(list.isEmpty());
        HealthMetricVO vo = list.get(0);
        assertEquals("WARNING_HIGH", vo.status());
        assertEquals("偏高", vo.statusText());
        assertEquals("MEDIUM", vo.alertLevel());
    }

    @Test
    @DisplayName("测试1.24：指标状态计算 - 元数据默认范围警告偏低")
    void testStatus_MetaRegistryFallback_WarningLow() {
        when(thresholdEvaluator.getThresholdForUser(testUserId, "bloodGlucose")).thenReturn(null);
        MetricDefinition def = mock(MetricDefinition.class);
        when(def.normalMax()).thenReturn(new BigDecimal("6.1"));
        when(def.normalMin()).thenReturn(new BigDecimal("3.9"));
        when(metricRegistry.getDefinition("bloodGlucose")).thenReturn(Optional.of(def));

        HealthMetric metric = new HealthMetric();
        metric.setId(22L);
        metric.setUserId(testUserId);
        metric.setMetricKey("bloodGlucose");
        metric.setValue(new BigDecimal("3.2"));
        metric.setUnit("mmol/L");
        metric.setRecordDate(LocalDate.now());
        metric.setTrend("STABLE");

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(metric));

        List<HealthMetricVO> list =
                healthMetricService.list(testUserId, "bloodGlucose", null, null);
        assertFalse(list.isEmpty());
        HealthMetricVO vo = list.get(0);
        assertEquals("WARNING_LOW", vo.status());
        assertEquals("偏低", vo.statusText());
        assertEquals("MEDIUM", vo.alertLevel());
    }
}
