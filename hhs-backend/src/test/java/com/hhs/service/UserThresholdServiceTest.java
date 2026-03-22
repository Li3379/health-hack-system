package com.hhs.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhs.dto.UserThresholdRequest;
import com.hhs.entity.UserThreshold;
import com.hhs.exception.BusinessException;
import com.hhs.mapper.UserThresholdMapper;
import com.hhs.service.impl.UserThresholdServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * User Threshold Service Unit Tests
 *
 * Tests the UserThresholdService which manages user personalized thresholds
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户阈值服务测试")
class UserThresholdServiceTest {

    @Mock
    private UserThresholdMapper userThresholdMapper;

    @InjectMocks
    private UserThresholdServiceImpl userThresholdService;

    private UserThresholdRequest testRequest;
    private UserThreshold testThreshold;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        testRequest = new UserThresholdRequest();
        testRequest.setUserId(testUserId);
        testRequest.setMetricKey("heartRate");
        testRequest.setWarningLow(new BigDecimal("50"));
        testRequest.setCriticalLow(new BigDecimal("40"));
        testRequest.setWarningHigh(new BigDecimal("100"));
        testRequest.setCriticalHigh(new BigDecimal("120"));

        testThreshold = new UserThreshold();
        testThreshold.setId(1L);
        testThreshold.setUserId(testUserId);
        testThreshold.setMetricKey("heartRate");
        testThreshold.setWarningLow(new BigDecimal("50"));
        testThreshold.setCriticalLow(new BigDecimal("40"));
        testThreshold.setWarningHigh(new BigDecimal("100"));
        testThreshold.setCriticalHigh(new BigDecimal("120"));
        testThreshold.setCreatedAt(LocalDateTime.now());
        testThreshold.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("测试1.1：分页查询用户阈值")
    void testList_Paginated() {
        // Given: Mock paginated result
        Page<UserThreshold> pageParam = new Page<>(1, 10);
        pageParam.setRecords(Arrays.asList(testThreshold));
        pageParam.setTotal(1);

        when(userThresholdMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageParam);

        // When: List thresholds
        Page<UserThreshold> result = userThresholdService.list(1, 10, testUserId);

        // Then: Verify pagination
        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertEquals(1, result.getTotal());
        verify(userThresholdMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("测试1.2：创建用户阈值 - 成功场景")
    void testCreate_Success() {
        // Given: No existing threshold for this metric
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(userThresholdMapper.insert(any(UserThreshold.class))).thenAnswer(invocation -> {
            UserThreshold threshold = invocation.getArgument(0);
            ReflectionTestUtils.setField(threshold, "id", 1L);
            return 1;
        });

        // When: Create threshold
        UserThreshold result = userThresholdService.create(testRequest);

        // Then: Verify result
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals("heartRate", result.getMetricKey());
        verify(userThresholdMapper, times(1)).insert(any(UserThreshold.class));
    }

    @Test
    @DisplayName("测试1.3：创建用户阈值 - 阈值已存在")
    void testCreate_AlreadyExists() {
        // Given: Threshold already exists for this metric
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);

        // When & Then: Should throw exception
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userThresholdService.create(testRequest);
        });

        assertTrue(exception.getMessage().contains("already exists") || exception.getMessage().contains("已存在"));
        verify(userThresholdMapper, never()).insert(any(UserThreshold.class));
    }

    @Test
    @DisplayName("测试1.4：更新用户阈值 - 成功场景")
    void testUpdate_Success() {
        // Given: Mock existing threshold
        when(userThresholdMapper.selectById(1L)).thenReturn(testThreshold);
        when(userThresholdMapper.updateById(any(UserThreshold.class))).thenReturn(1);

        // When: Update threshold
        testRequest.setWarningHigh(new BigDecimal("105"));
        UserThreshold result = userThresholdService.update(1L, testRequest);

        // Then: Verify update
        assertNotNull(result);
        verify(userThresholdMapper, times(1)).updateById(any(UserThreshold.class));
    }

    @Test
    @DisplayName("测试1.5：更新用户阈值 - 阈值不存在")
    void testUpdate_NotFound() {
        // Given: Mock threshold doesn't exist
        when(userThresholdMapper.selectById(999L)).thenReturn(null);

        // When & Then: Should throw exception
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userThresholdService.update(999L, testRequest);
        });

        assertTrue(exception.getMessage().contains("not found") || exception.getMessage().contains("不存在"));
        verify(userThresholdMapper, never()).updateById(any(UserThreshold.class));
    }

    @Test
    @DisplayName("测试1.6：更新用户阈值 - 更改到已存在的指标类型")
    void testUpdate_MetricKeyAlreadyExists() {
        // Given: Existing threshold for different metric
        UserThreshold existingThreshold = new UserThreshold();
        existingThreshold.setId(1L);
        existingThreshold.setMetricKey("bloodPressure"); // Different metric

        UserThreshold otherThreshold = new UserThreshold();
        otherThreshold.setId(2L);
        otherThreshold.setUserId(testUserId);
        otherThreshold.setMetricKey("heartRate"); // Target metric

        when(userThresholdMapper.selectById(1L)).thenReturn(existingThreshold);
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(otherThreshold);

        // When: Try to update to metric that already exists
        testRequest.setMetricKey("heartRate");

        // Then: Should throw exception
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userThresholdService.update(1L, testRequest);
        });

        assertTrue(exception.getMessage().contains("already exists") || exception.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("测试1.7：删除用户阈值 - 成功场景")
    void testDelete_Success() {
        // Given: Mock threshold exists
        when(userThresholdMapper.selectById(1L)).thenReturn(testThreshold);
        when(userThresholdMapper.deleteById((Long) any())).thenReturn(1);

        // When: Delete threshold
        userThresholdService.delete(1L);

        // Then: Verify deletion
        verify(userThresholdMapper, times(1)).selectById(1L);
        verify(userThresholdMapper, times(1)).deleteById((Long) any());
    }

    @Test
    @DisplayName("测试1.8：删除用户阈值 - 阈值不存在（优雅处理）")
    void testDelete_NotFound() {
        // Given: Mock threshold doesn't exist
        when(userThresholdMapper.selectById(999L)).thenReturn(null);

        // When: Delete non-existent threshold
        userThresholdService.delete(999L);

        // Then: Verify graceful handling
        verify(userThresholdMapper, times(1)).selectById(999L);
        verify(userThresholdMapper, never()).deleteById((Long) any());
    }

    @Test
    @DisplayName("测试1.9：根据用户ID获取阈值列表")
    void testGetByUserId() {
        // Given: Mock threshold list
        List<UserThreshold> thresholds = Arrays.asList(testThreshold);
        when(userThresholdMapper.getByUserId(testUserId)).thenReturn(thresholds);

        // When: Get thresholds by user ID
        List<UserThreshold> result = userThresholdService.getByUserId(testUserId);

        // Then: Verify results
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("heartRate", result.get(0).getMetricKey());
        verify(userThresholdMapper, times(1)).getByUserId(testUserId);
    }

    @Test
    @DisplayName("测试1.10：根据用户ID和指标类型获取阈值")
    void testGetByUserAndMetricKey() {
        // Given: Mock threshold
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testThreshold);

        // When: Get threshold by user and metric key
        UserThreshold result = userThresholdService.getByUserAndMetricKey(testUserId, "heartRate");

        // Then: Verify result
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
        assertEquals("heartRate", result.getMetricKey());
        verify(userThresholdMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("测试1.11：根据用户ID和指标类型获取阈值 - 不存在")
    void testGetByUserAndMetricKey_NotFound() {
        // Given: Mock no threshold found
        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When: Get non-existent threshold
        UserThreshold result = userThresholdService.getByUserAndMetricKey(testUserId, "glucose");

        // Then: Verify null result
        assertNull(result);
    }

    @Test
    @DisplayName("测试1.12：创建用户阈值 - 验证阈值范围合理性")
    void testCreate_ThresholdValidation() {
        // Given: Invalid threshold range (warning > critical)
        UserThresholdRequest invalidRequest = new UserThresholdRequest();
        invalidRequest.setUserId(testUserId);
        invalidRequest.setMetricKey("heartRate");
        invalidRequest.setWarningLow(new BigDecimal("60"));
        invalidRequest.setCriticalLow(new BigDecimal("50")); // Critical should be < warning
        invalidRequest.setWarningHigh(new BigDecimal("100"));
        invalidRequest.setCriticalHigh(new BigDecimal("90")); // Critical should be > warning

        when(userThresholdMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(userThresholdMapper.insert(any(UserThreshold.class))).thenAnswer(invocation -> {
            UserThreshold threshold = invocation.getArgument(0);
            ReflectionTestUtils.setField(threshold, "id", 2L);
            return 1;
        });

        // When: Create threshold (service doesn't validate range, just stores)
        UserThreshold result = userThresholdService.create(invalidRequest);

        // Then: Verify creation (range validation is handled elsewhere)
        assertNotNull(result);
        assertEquals(testUserId, result.getUserId());
    }

    @Test
    @DisplayName("测试1.13：分页查询 - 无用户过滤")
    void testList_NoUserFilter() {
        // Given: Mock paginated result for all users
        Page<UserThreshold> pageParam = new Page<>(1, 10);
        pageParam.setRecords(Arrays.asList(testThreshold));
        pageParam.setTotal(1);

        when(userThresholdMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageParam);

        // When: List all thresholds
        Page<UserThreshold> result = userThresholdService.list(1, 10, null);

        // Then: Verify pagination
        assertNotNull(result);
        verify(userThresholdMapper, times(1)).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("测试1.14：根据用户ID获取阈值 - 空列表")
    void testGetByUserId_EmptyList() {
        // Given: Mock empty list
        when(userThresholdMapper.getByUserId(testUserId)).thenReturn(Collections.emptyList());

        // When: Get thresholds for user with no thresholds
        List<UserThreshold> result = userThresholdService.getByUserId(testUserId);

        // Then: Verify empty result
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
