package com.hhs.service.alert;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.HealthAlert;
import com.hhs.entity.UserThreshold;
import com.hhs.mapper.HealthAlertMapper;
import com.hhs.mapper.UserThresholdMapper;
import com.hhs.service.push.PushResult;
import com.hhs.service.push.PushStrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Recovery Notifier Unit Tests
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("恢复通知服务测试")
class RecoveryNotifierTest {

    @Mock
    private HealthAlertMapper healthAlertMapper;

    @Mock
    private UserThresholdMapper userThresholdMapper;

    @Mock
    private PushStrategyService pushStrategyService;

    @InjectMocks
    private RecoveryNotifier recoveryNotifier;

    private Long testUserId = 1L;
    private String testMetricKey = "heartRate";
    private HealthAlert unresolvedAlert;
    private UserThreshold testThreshold;

    @BeforeEach
    void setUp() {
        unresolvedAlert = new HealthAlert();
        unresolvedAlert.setId(1L);
        unresolvedAlert.setUserId(testUserId);
        unresolvedAlert.setMetricKey(testMetricKey);
        unresolvedAlert.setAlertType("WARNING");
        unresolvedAlert.setAlertLevel("HIGH");
        unresolvedAlert.setCurrentValue(new BigDecimal("120"));
        unresolvedAlert.setThresholdValue(new BigDecimal("100"));
        unresolvedAlert.setCreatedAt(LocalDateTime.now().minusHours(1));

        testThreshold = new UserThreshold();
        testThreshold.setUserId(testUserId);
        testThreshold.setMetricKey(testMetricKey);
        testThreshold.setWarningHigh(new BigDecimal("100"));
        testThreshold.setWarningLow(new BigDecimal("60"));
    }

    @Test
    @DisplayName("测试1.1：生成恢复通知 - 指标恢复到正常范围")
    void testRecovery_ValueReturnsToNormal() {
        // Given: Value is now within normal range
        BigDecimal normalValue = new BigDecimal("85");

        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(unresolvedAlert);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);
        when(healthAlertMapper.insert(any(HealthAlert.class))).thenAnswer(invocation -> {
            HealthAlert alert = invocation.getArgument(0);
            alert.setId(2L);
            return 1;
        });
        when(healthAlertMapper.updateById(any(HealthAlert.class))).thenReturn(1);
        when(pushStrategyService.pushWithStrategy(any(), any())).thenReturn(Collections.emptyList());

        // When
        HealthAlert result = recoveryNotifier.checkAndNotifyRecovery(testUserId, testMetricKey, normalValue, "bpm");

        // Then
        assertNotNull(result);
        assertEquals("RECOVERY", result.getAlertType());
        assertEquals("LOW", result.getAlertLevel());
        assertTrue(result.getTitle().contains("恢复正常"));
        verify(healthAlertMapper, times(1)).insert(any(HealthAlert.class));
        verify(healthAlertMapper, times(1)).updateById(unresolvedAlert);
    }

    @Test
    @DisplayName("测试1.2：不生成恢复通知 - 无未解决预警")
    void testNoRecovery_NoUnresolvedAlert() {
        // Given: No unresolved alert
        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When
        HealthAlert result = recoveryNotifier.checkAndNotifyRecovery(testUserId, testMetricKey, new BigDecimal("85"), "bpm");

        // Then
        assertNull(result);
        verify(healthAlertMapper, never()).insert(any());
    }

    @Test
    @DisplayName("测试1.3：不生成恢复通知 - 值仍异常（偏高）")
    void testNoRecovery_ValueStillHigh() {
        // Given: Value still above threshold
        BigDecimal highValue = new BigDecimal("110"); // Above warning high of 100

        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(unresolvedAlert);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);

        // When
        HealthAlert result = recoveryNotifier.checkAndNotifyRecovery(testUserId, testMetricKey, highValue, "bpm");

        // Then
        assertNull(result);
        verify(healthAlertMapper, never()).insert(any());
    }

    @Test
    @DisplayName("测试1.4：不生成恢复通知 - 值仍异常（偏低）")
    void testNoRecovery_ValueStillLow() {
        // Given: Value still below threshold
        BigDecimal lowValue = new BigDecimal("50"); // Below warning low of 60

        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(unresolvedAlert);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);

        // When
        HealthAlert result = recoveryNotifier.checkAndNotifyRecovery(testUserId, testMetricKey, lowValue, "bpm");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("测试1.5：生成恢复通知 - 无阈值配置时默认正常")
    void testRecovery_NoThreshold() {
        // Given: No threshold configured
        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(unresolvedAlert);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(healthAlertMapper.insert(any(HealthAlert.class))).thenAnswer(invocation -> {
            HealthAlert alert = invocation.getArgument(0);
            alert.setId(2L);
            return 1;
        });
        when(healthAlertMapper.updateById(any(HealthAlert.class))).thenReturn(1);
        when(pushStrategyService.pushWithStrategy(any(), any())).thenReturn(Collections.emptyList());

        // When
        HealthAlert result = recoveryNotifier.checkAndNotifyRecovery(testUserId, testMetricKey, new BigDecimal("85"), "bpm");

        // Then: Should treat as recovery since no threshold
        assertNotNull(result);
        assertEquals("RECOVERY", result.getAlertType());
    }

    @Test
    @DisplayName("测试1.6：不生成恢复通知 - 空值")
    void testNoRecovery_NullValue() {
        // When
        HealthAlert result = recoveryNotifier.checkAndNotifyRecovery(testUserId, testMetricKey, null, "bpm");

        // Then
        assertNull(result);
        verify(healthAlertMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("测试1.7：恢复通知标记原预警为已解决")
    void testRecovery_MarksOriginalAlertResolved() {
        // Given
        BigDecimal normalValue = new BigDecimal("85");

        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(unresolvedAlert);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);
        when(healthAlertMapper.insert(any(HealthAlert.class))).thenReturn(1);
        when(healthAlertMapper.updateById(any(HealthAlert.class))).thenReturn(1);
        when(pushStrategyService.pushWithStrategy(any(), any())).thenReturn(Collections.emptyList());

        // When
        recoveryNotifier.checkAndNotifyRecovery(testUserId, testMetricKey, normalValue, "bpm");

        // Then
        assertNotNull(unresolvedAlert.getResolvedAt());
        verify(healthAlertMapper, times(1)).updateById(unresolvedAlert);
    }

    @Test
    @DisplayName("测试1.8：恢复通知消息包含正确信息")
    void testRecovery_CorrectMessageFormat() {
        // Given
        BigDecimal normalValue = new BigDecimal("85");

        when(healthAlertMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(unresolvedAlert);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);
        when(healthAlertMapper.insert(any(HealthAlert.class))).thenAnswer(invocation -> {
            HealthAlert alert = invocation.getArgument(0);
            alert.setId(2L);
            return 1;
        });
        when(healthAlertMapper.updateById(any(HealthAlert.class))).thenReturn(1);
        when(pushStrategyService.pushWithStrategy(any(), any())).thenReturn(Collections.emptyList());

        // When
        HealthAlert result = recoveryNotifier.checkAndNotifyRecovery(testUserId, testMetricKey, normalValue, "bpm");

        // Then
        assertNotNull(result);
        assertTrue(result.getMessage().contains("恢复到正常范围"));
        assertTrue(result.getMessage().contains("85"));
        assertTrue(result.getSuggestion().contains("继续保持"));
    }
}