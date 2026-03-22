package com.hhs.service;

import com.hhs.domain.event.ScoreUpdatedEvent;
import com.hhs.exception.BusinessException;
import com.hhs.service.domain.MetricValidator;
import com.hhs.service.domain.ScoreCalculator;
import com.hhs.service.impl.HealthScoreServiceImpl;
import com.hhs.vo.HealthScoreVO;
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
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Health Score Service Unit Tests
 *
 * Tests the HealthScoreService which calculates health scores
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("健康评分服务测试")
class HealthScoreServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ScoreCalculator scoreCalculator;

    @Mock
    private MetricValidator metricValidator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private HealthScoreServiceImpl healthScoreService;

    private HealthScoreVO testScore;
    private Long testUserId = 1L;

    @BeforeEach
    void setUp() {
        testScore = new HealthScoreVO();
        testScore.setScore(75);
        testScore.setCalculatedAt(LocalDateTime.now());
        testScore.setIsCached(false);
        testScore.setCalculationMethod("RULE_BASED");
    }

    @Test
    @DisplayName("测试1.1：计算健康评分 - 成功场景（无缓存）")
    void testCalculateScore_NoCache() {
        // Given: No cached score, user has data
        when(metricValidator.hasAnyData(testUserId)).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(scoreCalculator.calculate(testUserId)).thenReturn(testScore);
        doNothing().when(eventPublisher).publishEvent(any(ScoreUpdatedEvent.class));

        // When: Calculate score
        HealthScoreVO result = healthScoreService.calculateScore(testUserId);

        // Then: Verify result
        assertNotNull(result);
        assertEquals(75, result.getScore());
        assertFalse(result.getIsCached());
        verify(metricValidator, times(1)).hasAnyData(testUserId);
        verify(scoreCalculator, times(1)).calculate(testUserId);
    }

    @Test
    @DisplayName("测试1.2：计算健康评分 - 命中缓存")
    void testCalculateScore_CacheHit() {
        // Given: Cached score exists
        HealthScoreVO cachedScore = new HealthScoreVO();
        cachedScore.setScore(80);
        cachedScore.setIsCached(true);
        cachedScore.setCalculatedAt(LocalDateTime.now().minusMinutes(2));

        when(metricValidator.hasAnyData(testUserId)).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cachedScore);

        // When: Calculate score
        HealthScoreVO result = healthScoreService.calculateScore(testUserId);

        // Then: Verify cached result
        assertNotNull(result);
        assertEquals(80, result.getScore());
        assertTrue(result.getIsCached());
        verify(metricValidator, times(1)).hasAnyData(testUserId);
        verify(scoreCalculator, never()).calculate(any());
    }

    @Test
    @DisplayName("测试1.3：计算健康评分 - 用户无数据")
    void testCalculateScore_NoData() {
        // Given: User has no data
        when(metricValidator.hasAnyData(testUserId)).thenReturn(false);

        // When: Calculate score
        HealthScoreVO result = healthScoreService.calculateScore(testUserId);

        // Then: Should return default score
        assertNotNull(result);
        assertEquals(0, result.getScore());
        assertEquals("NO_DATA", result.getLevel());
        verify(scoreCalculator, never()).calculate(any());
    }

    @Test
    @DisplayName("测试1.4：强制重新计算 - 成功场景")
    void testForceRecalculate_Success() {
        // Given: User has data
        when(metricValidator.hasAnyData(testUserId)).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(scoreCalculator.calculate(testUserId)).thenReturn(testScore);
        doNothing().when(eventPublisher).publishEvent(any(ScoreUpdatedEvent.class));

        // When: Force recalculate
        HealthScoreVO result = healthScoreService.forceRecalculate(testUserId);

        // Then: Verify result
        assertNotNull(result);
        assertEquals(75, result.getScore());
        assertFalse(result.getIsCached());
        verify(redisTemplate, times(1)).delete(anyString());
        verify(scoreCalculator, times(1)).calculate(testUserId);
    }

    @Test
    @DisplayName("测试1.5：获取缓存评分 - 成功场景")
    void testGetCachedScore_Success() {
        // Given: Cached score exists
        HealthScoreVO cachedScore = new HealthScoreVO();
        cachedScore.setScore(85);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(cachedScore);

        // When: Get cached score
        HealthScoreVO result = healthScoreService.getCachedScore(testUserId);

        // Then: Verify result
        assertNotNull(result);
        assertEquals(85, result.getScore());
    }

    @Test
    @DisplayName("测试1.6：获取缓存评分 - 缓存不存在")
    void testGetCachedScore_NotFound() {
        // Given: No cached score
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        // When: Get cached score
        HealthScoreVO result = healthScoreService.getCachedScore(testUserId);

        // Then: Verify null result
        assertNull(result);
    }

    @Test
    @DisplayName("测试1.7：获取缓存评分 - Redis连接失败（优雅降级）")
    void testGetCachedScore_RedisFailure() {
        // Given: Redis connection fails
        when(redisTemplate.opsForValue()).thenThrow(new RedisSystemException("Connection failed", new RuntimeException()));

        // When: Get cached score
        HealthScoreVO result = healthScoreService.getCachedScore(testUserId);

        // Then: Verify graceful handling (returns null)
        assertNull(result);
    }

    @Test
    @DisplayName("测试1.8：计算评分 - Redis缓存失败（优雅降级）")
    void testCalculateScore_RedisCacheFailure() {
        // Given: Redis set fails, but calculation succeeds
        when(metricValidator.hasAnyData(testUserId)).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(scoreCalculator.calculate(testUserId)).thenReturn(testScore);
        doNothing().when(eventPublisher).publishEvent(any(ScoreUpdatedEvent.class));

        // When: Calculate score
        HealthScoreVO result = healthScoreService.calculateScore(testUserId);

        // Then: Verify calculation still succeeds
        assertNotNull(result);
        assertEquals(75, result.getScore());
        verify(scoreCalculator, times(1)).calculate(testUserId);
    }

    @Test
    @DisplayName("测试1.9：强制重新计算 - Redis清除失败（优雅降级）")
    void testForceRecalculate_RedisDeleteFailure() {
        // Given: Redis delete fails, but calculation continues
        when(metricValidator.hasAnyData(testUserId)).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenThrow(new RedisSystemException("Connection failed", new RuntimeException()));
        when(scoreCalculator.calculate(testUserId)).thenReturn(testScore);
        doNothing().when(eventPublisher).publishEvent(any(ScoreUpdatedEvent.class));

        // When: Force recalculate
        HealthScoreVO result = healthScoreService.forceRecalculate(testUserId);

        // Then: Verify calculation still succeeds
        assertNotNull(result);
        assertEquals(75, result.getScore());
        verify(scoreCalculator, times(1)).calculate(testUserId);
    }

    @Test
    @DisplayName("测试1.10：计算评分 - 设置过期时间")
    void testCalculateScore_SetsExpiry() {
        // Given: No cache, successful calculation
        when(metricValidator.hasAnyData(testUserId)).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(scoreCalculator.calculate(testUserId)).thenReturn(testScore);
        doNothing().when(eventPublisher).publishEvent(any(ScoreUpdatedEvent.class));

        // When: Calculate score
        HealthScoreVO result = healthScoreService.calculateScore(testUserId);

        // Then: Verify expiry is set (5 minutes from calculation time)
        assertNotNull(result.getExpiresAt());
        assertTrue(result.getExpiresAt().isAfter(result.getCalculatedAt()));
    }

    @Test
    @DisplayName("测试1.11：计算评分 - 发布事件")
    void testCalculateScore_PublishesEvent() {
        // Given: No cache, successful calculation
        when(metricValidator.hasAnyData(testUserId)).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(scoreCalculator.calculate(testUserId)).thenReturn(testScore);
        doNothing().when(eventPublisher).publishEvent(any(ScoreUpdatedEvent.class));

        // When: Calculate score
        healthScoreService.calculateScore(testUserId);

        // Then: Verify event published with correct score
        verify(eventPublisher, times(1)).publishEvent(any(ScoreUpdatedEvent.class));
    }

    @Test
    @DisplayName("测试1.12：强制重新计算 - 清除旧缓存")
    void testForceRecalculate_ClearsOldCache() {
        // Given: User has data
        when(metricValidator.hasAnyData(testUserId)).thenReturn(true);
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(scoreCalculator.calculate(testUserId)).thenReturn(testScore);
        doNothing().when(eventPublisher).publishEvent(any(ScoreUpdatedEvent.class));

        // When: Force recalculate
        healthScoreService.forceRecalculate(testUserId);

        // Then: Verify old cache was cleared
        verify(redisTemplate, times(1)).delete(anyString());
    }
}
