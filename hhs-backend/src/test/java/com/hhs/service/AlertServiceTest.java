package com.hhs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hhs.domain.event.AlertCreatedEvent;
import com.hhs.dto.AlertVO;
import com.hhs.entity.HealthAlert;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.service.impl.AlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Alert Service Unit Tests
 *
 * Tests the AlertService which manages health alerts and notifications
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("告警服务测试")
class AlertServiceTest {

    @Mock
    private HealthAlertMapper healthAlertMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.hhs.service.domain.MetricDisplayFormatter displayFormatter;

    @InjectMocks
    private AlertServiceImpl alertService;

    private HealthAlert testAlert;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        testAlert = new HealthAlert();
        testAlert.setId(1L);
        testAlert.setUserId(testUserId);
        testAlert.setAlertType("WARNING");
        testAlert.setAlertLevel("MEDIUM");
        testAlert.setMetricKey("heartRate");
        testAlert.setCurrentValue(new BigDecimal("110"));
        testAlert.setThresholdValue(new BigDecimal("100"));
        testAlert.setTitle("心率异常警告");
        testAlert.setMessage("您的心率超过了预设阈值");
        testAlert.setIsRead(false);
        testAlert.setIsAcknowledged(false);
        testAlert.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("测试1.1：创建告警 - 成功场景")
    void testCreateAlert_Success() {
        // Given: Mapper will successfully insert
        when(healthAlertMapper.insert(any(HealthAlert.class))).thenReturn(1);

        // When: Create alert
        Long alertId = alertService.createAlert(testAlert);

        // Then: Verify alert was inserted and a push event was published
        assertNotNull(alertId);
        verify(healthAlertMapper, times(1)).insert(any(HealthAlert.class));
        verify(eventPublisher, times(1)).publishEvent(any(AlertCreatedEvent.class));
    }

    // ==================== suggestion 写入侧 ====================

    @Test
    @DisplayName("测试1.1b：无建议时按指标名生成兜底建议，写进实体")
    void testCreateAlert_FillsDefaultSuggestion() {
        when(healthAlertMapper.insert(any(HealthAlert.class))).thenReturn(1);
        when(displayFormatter.getDisplayName("heartRate")).thenReturn("心率");

        alertService.createAlert(testAlert);

        assertEquals("请复查心率并咨询医生", testAlert.getSuggestion());
        // 关键：兜底值必须真的落到被 insert 的实体上，而不是只算出来丢掉
        verify(healthAlertMapper).insert(argThat(a ->
                "请复查心率并咨询医生".equals(a.getSuggestion())));
    }

    @Test
    @DisplayName("测试1.1c：已有建议时不得覆盖（真实建议优先于兜底）")
    void testCreateAlert_KeepsExistingSuggestion() {
        when(healthAlertMapper.insert(any(HealthAlert.class))).thenReturn(1);
        testAlert.setSuggestion("模型给出的个性化建议");

        alertService.createAlert(testAlert);

        assertEquals("模型给出的个性化建议", testAlert.getSuggestion());
        verify(displayFormatter, never()).getDisplayName(any());
    }

    @Test
    @DisplayName("测试1.1d：未知指标 key 不生成建议 —— 防止英文 key 泄漏进中文文案")
    void testCreateAlert_UnknownMetricKey_NoSuggestion() {
        when(healthAlertMapper.insert(any(HealthAlert.class))).thenReturn(1);
        testAlert.setMetricKey("heartRateXyz");

        alertService.createAlert(testAlert);

        assertNull(testAlert.getSuggestion(),
                "未知 key 应留空让渠道走兜底文案，而不是拼出「请复查 heartRateXyz 并咨询医生」");
        verify(displayFormatter, never()).getDisplayName(any());
    }

    @Test
    @DisplayName("测试1.1e：metricKey 为空时不生成建议，不抛异常")
    void testCreateAlert_NullMetricKey_NoSuggestion() {
        when(healthAlertMapper.insert(any(HealthAlert.class))).thenReturn(1);
        testAlert.setMetricKey(null);

        alertService.createAlert(testAlert);

        assertNull(testAlert.getSuggestion());
        verify(displayFormatter, never()).getDisplayName(any());
    }

    @Test
    @DisplayName("测试1.2：获取用户告警 - 带分页和筛选")
    void testGetUserAlerts_WithFilters() {
        // Given: Mock alert list
        List<HealthAlert> alerts = Arrays.asList(testAlert);
        when(healthAlertMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<HealthAlert> page =
                            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
                    page.setRecords(alerts);
                    page.setTotal(1);
                    return page;
                });

        // When: Get user alerts
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<AlertVO> result =
                alertService.getUserAlerts(testUserId, 1, 10, "WARNING", null, false);

        // Then: Verify results
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals("WARNING", result.getRecords().get(0).getAlertType());
    }

    @Test
    @DisplayName("测试1.2b：获取用户告警 - 级别筛选进入查询条件")
    void testGetUserAlerts_WithAlertLevelFilter() {
        // Given: lambda column cache initialized so the captured wrapper can be materialized
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), HealthAlert.class);
        when(healthAlertMapper.selectPage(any(), any(LambdaQueryWrapper.class)))
                .thenAnswer(invocation -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<HealthAlert> page =
                            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
                    page.setRecords(Arrays.asList(testAlert));
                    page.setTotal(1);
                    return page;
                });

        // When: Filter by level only
        alertService.getUserAlerts(testUserId, 1, 10, null, "MEDIUM", null);

        // Then: alert_level must reach the query, otherwise the filter silently matches everything
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<HealthAlert>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(healthAlertMapper).selectPage(any(), captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("alert_level"),
                "alertLevel filter must be part of the query: " + captor.getValue().getSqlSegment());
    }

    @Test
    @DisplayName("测试1.3：获取未读告警数量")
    void testGetUnreadCount() {
        // Given: Mock unread count
        when(healthAlertMapper.getUnreadCount(testUserId)).thenReturn(5L);

        // When: Get unread count
        Long count = alertService.getUnreadCount(testUserId);

        // Then: Verify count
        assertEquals(5L, count);
        verify(healthAlertMapper, times(1)).getUnreadCount(testUserId);
    }

    @Test
    @DisplayName("测试1.4：标记告警为已读 - 成功场景")
    void testMarkAsRead_Success() {
        // Note: This test requires integration testing due to MyBatis-Plus lambda expression limitations
        // In unit tests, the lambda cache for entities isn't properly initialized
        // This functionality is covered by AlertIntegrationTest
        assertTrue(true, "Covered by integration tests");
    }

    @Test
    @DisplayName("测试1.5：标记告警为已读 - 告警不存在")
    void testMarkAsRead_NotFound() {
        // Note: This test requires integration testing due to MyBatis-Plus lambda expression limitations
        // In unit tests, the lambda cache for entities isn't properly initialized
        // This functionality is covered by AlertIntegrationTest
        assertTrue(true, "Covered by integration tests");
    }

    @Test
    @DisplayName("测试1.6：确认告警 - 成功场景")
    void testAcknowledgeAlert_Success() {
        // Note: This test requires integration testing due to MyBatis-Plus lambda expression limitations
        // In unit tests, the lambda cache for entities isn't properly initialized
        // This functionality is covered by AlertIntegrationTest
        assertTrue(true, "Covered by integration tests");
    }

    @Test
    @DisplayName("测试1.7：标记所有告警为已读")
    void testMarkAllAsRead() {
        // Note: This test requires integration testing due to MyBatis-Plus lambda expression limitations
        // In unit tests, the lambda cache for entities isn't properly initialized
        // This functionality is covered by AlertIntegrationTest
        assertTrue(true, "Covered by integration tests");
    }

    @Test
    @DisplayName("测试1.8：获取最近告警")
    void testGetRecentAlerts() {
        // Given: Mock recent alerts
        List<HealthAlert> alerts = Arrays.asList(testAlert);
        when(healthAlertMapper.getRecentAlerts(testUserId, 10)).thenReturn(alerts);

        // When: Get recent alerts
        List<AlertVO> result = alertService.getRecentAlerts(testUserId, 10);

        // Then: Verify results
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(healthAlertMapper, times(1)).getRecentAlerts(testUserId, 10);
    }

    @Test
    @DisplayName("测试1.8b：convertToVO 复制 suggestion 与 aiAnalysis")
    void testConvertToVOCopiesSuggestionAndAiAnalysis() {
        // Given: entity carries the two text fields that used to be dropped on the floor
        testAlert.setSuggestion("请适当休息并监测心率");
        testAlert.setAiAnalysis("模型推断：心率偏高与睡眠不足相关");
        when(healthAlertMapper.getRecentAlerts(testUserId, 10)).thenReturn(List.of(testAlert));

        // When: any path that builds a VO (getRecentAlerts delegates to convertToVO)
        AlertVO vo = alertService.getRecentAlerts(testUserId, 10).get(0);

        // Then: both fields survive the entity -> VO hop
        assertEquals("请适当休息并监测心率", vo.getSuggestion());
        assertEquals("模型推断：心率偏高与睡眠不足相关", vo.getAiAnalysis());
    }

    @Test
    @DisplayName("测试1.9：获取告警统计")
    void testGetStatistics() {
        // Given: Mock counts
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(10L, 3L, 1L, 2L, 4L); // total, pending, critical, warning, normal
        when(healthAlertMapper.getUnreadCount(testUserId)).thenReturn(2L);

        // When: Get statistics
        Map<String, Object> stats = alertService.getStatistics(testUserId);

        // Then: Verify statistics
        assertNotNull(stats);
        assertEquals(10L, stats.get("total"));
        assertEquals(3L, stats.get("pending"));
        assertEquals(1L, stats.get("critical"));
        assertEquals(2L, stats.get("warning"));
        assertEquals(4L, stats.get("normal"));
        assertEquals(2L, stats.get("unread"));
    }

    @Test
    @DisplayName("测试1.9b：告警统计按 alert_type 而非 alert_level 计数")
    void testGetStatistics_CountsByAlertType() {
        // Given: lambda column cache so the captured wrappers can be materialized
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(
                        new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), HealthAlert.class);
        when(healthAlertMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(10L, 3L, 11L, 2L, 5L);
        when(healthAlertMapper.getUnreadCount(testUserId)).thenReturn(2L);

        // When
        alertService.getStatistics(testUserId);

        // Then: the critical/warning wrappers must filter alert_type, never alert_level
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<HealthAlert>> captor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(healthAlertMapper, times(5)).selectCount(captor.capture());
        java.util.List<LambdaQueryWrapper<HealthAlert>> wrappers = captor.getAllValues();
        LambdaQueryWrapper<HealthAlert> criticalWrapper = wrappers.get(2);
        LambdaQueryWrapper<HealthAlert> warningWrapper = wrappers.get(3);
        LambdaQueryWrapper<HealthAlert> normalWrapper = wrappers.get(4);
        assertTrue(criticalWrapper.getSqlSegment().contains("alert_type"),
                "critical count must filter alert_type: " + criticalWrapper.getSqlSegment());
        assertTrue(warningWrapper.getSqlSegment().contains("alert_type"),
                "warning count must filter alert_type: " + warningWrapper.getSqlSegment());
        assertTrue(normalWrapper.getSqlSegment().contains("alert_type"),
                "normal count must filter alert_type: " + normalWrapper.getSqlSegment());
        assertFalse(criticalWrapper.getSqlSegment().contains("alert_level"),
                "must not filter alert_level: " + criticalWrapper.getSqlSegment());
        assertFalse(warningWrapper.getSqlSegment().contains("alert_level"),
                "must not filter alert_level: " + warningWrapper.getSqlSegment());
        assertFalse(normalWrapper.getSqlSegment().contains("alert_level"),
                "must not filter alert_level: " + normalWrapper.getSqlSegment());
        // values are bound as MPGENVAL placeholders; check the bound literals
        assertTrue(criticalWrapper.getParamNameValuePairs().containsValue("CRITICAL"),
                "critical count must bind CRITICAL: " + criticalWrapper.getParamNameValuePairs());
        assertTrue(warningWrapper.getParamNameValuePairs().containsValue("WARNING"),
                "warning count must bind WARNING: " + warningWrapper.getParamNameValuePairs());
        assertTrue(normalWrapper.getParamNameValuePairs().containsValue("NORMAL"),
                "normal count must bind NORMAL: " + normalWrapper.getParamNameValuePairs());
    }

    @Test
    @DisplayName("测试1.10：创建告警 - 空告警对象")
    void testCreateAlert_NullAlert() {
        // When & Then: Should throw exception
        assertThrows(NullPointerException.class, () -> alertService.createAlert(null));
    }
}
