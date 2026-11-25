package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.common.enums.MetricCategory;
import com.hhs.domain.event.MetricRecordedEvent;
import com.hhs.dto.HealthMetricRequest;
import com.hhs.entity.HealthMetric;
import com.hhs.mapper.HealthMetricMapper;
import com.hhs.mapper.RealtimeMetricMapper;
import com.hhs.service.domain.HealthMetricMetaRegistry;
import com.hhs.service.domain.MetricCategoryService;
import com.hhs.service.domain.MetricDisplayFormatter;
import com.hhs.vo.HealthMetricTrendVO;
import com.hhs.vo.HealthMetricVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration and unit tests for {@link HealthMetricServiceImpl} query alias expansion,
 * canonical camelCase storage on write, and unit resolution via {@link HealthMetricMetaRegistry}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("健康指标服务Key规范化与别名查询展开测试")
class HealthMetricServiceNormalizationTest {

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

    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<HealthMetric>> wrapperCaptor;

    @Captor
    private ArgumentCaptor<HealthMetric> metricCaptor;

    private HealthMetricMetaRegistry registry;
    private HealthMetricServiceImpl service;

    private final Long testUserId = 100L;

    @BeforeAll
    static void initMyBatisPlus() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), HealthMetric.class);
    }

    @BeforeEach
    void setUp() {
        registry = new HealthMetricMetaRegistry();

        lenient().when(metricDisplayFormatter.getDisplayName(anyString()))
                .thenAnswer(inv -> registry.getDefinition(inv.getArgument(0))
                        .map(def -> def.displayName())
                        .orElse(inv.getArgument(0)));
        lenient().when(metricDisplayFormatter.getUnit(anyString()))
                .thenAnswer(inv -> registry.getDefinition(inv.getArgument(0))
                        .map(def -> def.unit())
                        .orElse(""));
        lenient().when(metricCategoryService.determineCategory(anyString()))
                .thenReturn(MetricCategory.HEALTH);
        // 本类检的是 key 规范化与别名展开；写入白名单另有专用用例。
        lenient().when(metricCategoryService.isWritableMetric(anyString())).thenReturn(true);

        service = new HealthMetricServiceImpl(
                healthMetricMapper,
                realtimeMetricMapper,
                eventPublisher,
                metricDisplayFormatter,
                metricCategoryService,
                registry
        );
    }

    @Test
    @DisplayName("Query with 'heart_rate' should expand aliases and retrieve both canonical and legacy records")
    void testListWithSnakeCaseAliasExpandsToInQuery() {
        HealthMetric canonicalRecord = new HealthMetric();
        canonicalRecord.setId(1L);
        canonicalRecord.setUserId(testUserId);
        canonicalRecord.setMetricKey("heartRate");
        canonicalRecord.setValue(new BigDecimal("72"));
        canonicalRecord.setUnit("次/分");
        canonicalRecord.setRecordDate(LocalDate.now());

        HealthMetric legacyRecord = new HealthMetric();
        legacyRecord.setId(2L);
        legacyRecord.setUserId(testUserId);
        legacyRecord.setMetricKey("heart_rate");
        legacyRecord.setValue(new BigDecimal("78"));
        legacyRecord.setUnit("bpm");
        legacyRecord.setRecordDate(LocalDate.now().minusDays(1));

        when(healthMetricMapper.selectList(wrapperCaptor.capture()))
                .thenReturn(Arrays.asList(canonicalRecord, legacyRecord));

        List<HealthMetricVO> results = service.list(testUserId, "heart_rate", null, null);

        assertNotNull(results);
        assertEquals(2, results.size());

        // Verify captured wrapper includes alias set in SQL segment
        LambdaQueryWrapper<HealthMetric> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertNotNull(sqlSegment);
        assertTrue(sqlSegment.contains("IN") || sqlSegment.contains("in"), "Query should use IN clause for aliases");
        assertFalse(wrapper.getParamNameValuePairs().isEmpty());
    }

    @Test
    @DisplayName("Query with 'heartRate' should expand aliases and retrieve both canonical and legacy records")
    void testListWithCamelCaseKeyExpandsToInQuery() {
        HealthMetric canonicalRecord = new HealthMetric();
        canonicalRecord.setId(1L);
        canonicalRecord.setUserId(testUserId);
        canonicalRecord.setMetricKey("heartRate");
        canonicalRecord.setValue(new BigDecimal("75"));
        canonicalRecord.setUnit("次/分");
        canonicalRecord.setRecordDate(LocalDate.now());

        when(healthMetricMapper.selectList(wrapperCaptor.capture()))
                .thenReturn(Collections.singletonList(canonicalRecord));

        List<HealthMetricVO> results = service.list(testUserId, "heartRate", null, null);

        assertEquals(1, results.size());
        LambdaQueryWrapper<HealthMetric> wrapper = wrapperCaptor.getValue();
        String sqlSegment = wrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("IN") || sqlSegment.contains("in"));
    }

    @Test
    @DisplayName("Writing with alias 'systolic_bp' must persist canonical key 'systolicBP'")
    void testAddPersistsCanonicalKeyForSystolicBP() {
        when(healthMetricMapper.insert(metricCaptor.capture())).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(10L);
            return 1;
        });
        doNothing().when(eventPublisher).publishEvent(any(MetricRecordedEvent.class));

        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey("systolic_bp");
        request.setValue(new BigDecimal("125"));
        request.setRecordDate(LocalDate.now());
        // Leave unit blank to test auto-population from registry
        request.setUnit(null);

        HealthMetricVO vo = service.add(testUserId, request);

        assertNotNull(vo);
        assertEquals("systolicBP", vo.metricKey(), "Returned VO must use canonical key systolicBP");
        assertEquals("mmHg", vo.unit(), "Unit should auto-populate from registry as mmHg");

        HealthMetric inserted = metricCaptor.getValue();
        assertEquals("systolicBP", inserted.getMetricKey(), "Database entity must store canonical key systolicBP");
        assertEquals("mmHg", inserted.getUnit());
        verify(healthMetricMapper, times(1)).insert(any(HealthMetric.class));
    }

    @Test
    @DisplayName("Writing with alias 'heart_rate' must persist canonical key 'heartRate' and unit '次/分'")
    void testAddPersistsCanonicalKeyForHeartRate() {
        when(healthMetricMapper.insert(metricCaptor.capture())).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(11L);
            return 1;
        });
        doNothing().when(eventPublisher).publishEvent(any(MetricRecordedEvent.class));

        HealthMetricRequest request = new HealthMetricRequest();
        request.setMetricKey("heart_rate");
        request.setValue(new BigDecimal("80"));
        request.setRecordDate(LocalDate.now());
        request.setUnit("");

        HealthMetricVO vo = service.add(testUserId, request);

        assertEquals("heartRate", vo.metricKey());
        assertEquals("次/分", vo.unit());

        HealthMetric inserted = metricCaptor.getValue();
        assertEquals("heartRate", inserted.getMetricKey());
        assertEquals("次/分", inserted.getUnit());
    }

    @Test
    @DisplayName("Create method must normalize metricKey to canonical camelCase")
    void testCreateNormalizesMetricKey() {
        when(healthMetricMapper.insert(metricCaptor.capture())).thenAnswer(invocation -> {
            HealthMetric metric = invocation.getArgument(0);
            metric.setId(12L);
            return 1;
        });
        doNothing().when(eventPublisher).publishEvent(any(MetricRecordedEvent.class));

        HealthMetricRequest request = new HealthMetricRequest();
        request.setUserId(testUserId);
        request.setMetricKey("glucose");
        request.setValue(new BigDecimal("5.8"));
        request.setRecordDate(LocalDate.now());

        HealthMetric created = service.create(request);

        assertNotNull(created);
        assertEquals("bloodGlucose", created.getMetricKey(), "glucose should normalize to bloodGlucose");
        HealthMetric inserted = metricCaptor.getValue();
        assertEquals("bloodGlucose", inserted.getMetricKey());
    }

    @Test
    @DisplayName("Trend query must return canonical camelCase metricKey")
    void testGetTrendReturnsCanonicalMetricKey() {
        HealthMetric metric = new HealthMetric();
        metric.setId(1L);
        metric.setUserId(testUserId);
        metric.setMetricKey("heart_rate");
        metric.setValue(new BigDecimal("75"));
        metric.setUnit("bpm");
        metric.setRecordDate(LocalDate.now());

        when(healthMetricMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(metric));

        // When query with snake_case "heart_rate"
        List<HealthMetricTrendVO> trends = service.getTrend(testUserId, "heart_rate", null, null);

        assertNotNull(trends);
        assertEquals(1, trends.size());
        assertEquals("heartRate", trends.get(0).metricKey(), "Trend metricKey must be canonical heartRate");

        // When query with "temperature"
        List<HealthMetricTrendVO> tempTrends = service.getTrend(testUserId, "temperature", null, null);
        assertEquals(1, tempTrends.size());
        assertEquals("bodyTemperature", tempTrends.get(0).metricKey(), "Trend metricKey must be canonical bodyTemperature");
    }

    @Test
    @DisplayName("Paginated list must also expand aliases via IN clause")
    void testPaginatedListExpandsAliases() {
        Page<HealthMetric> pageResult = new Page<>(1, 10);
        pageResult.setRecords(Collections.emptyList());
        pageResult.setTotal(0);

        when(healthMetricMapper.selectPage(any(Page.class), wrapperCaptor.capture()))
                .thenReturn(pageResult);

        Page<HealthMetricVO> result = service.list(1, 10, testUserId, "systolic_bp", null, null);

        assertNotNull(result);
        LambdaQueryWrapper<HealthMetric> wrapper = wrapperCaptor.getValue();
        String sql = wrapper.getSqlSegment();
        assertTrue(sql.contains("IN") || sql.contains("in"), "Paginated query should use IN clause for aliases");
    }
}
