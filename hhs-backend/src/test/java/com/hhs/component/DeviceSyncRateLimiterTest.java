package com.hhs.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DeviceSyncRateLimiter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceSyncRateLimiterTest {

    @Mock
    private RedisTemplate<String, Integer> redisTemplate;

    @Mock
    private ValueOperations<String, Integer> valueOperations;

    @InjectMocks
    private DeviceSyncRateLimiter rateLimiter;

    private static final Long USER_ID = 1L;
    private static final String KEY_PREFIX = "device:sync:limit:";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("checkLimit Tests")
    class CheckLimitTests {

        @Test
        @DisplayName("Should allow first request and set counter to 1")
        void checkLimit_FirstRequest_Allowed() {
            // Given: No existing count in Redis
            when(valueOperations.get(KEY_PREFIX + USER_ID)).thenReturn(null);

            // When
            boolean result = rateLimiter.checkLimit(USER_ID);

            // Then
            assertTrue(result);
            verify(valueOperations).set(eq(KEY_PREFIX + USER_ID), eq(1), eq(60L), eq(TimeUnit.SECONDS));
        }

        @Test
        @DisplayName("Should allow request when count is below limit")
        void checkLimit_CountBelowLimit_Allowed() {
            // Given: Count is below limit
            when(valueOperations.get(KEY_PREFIX + USER_ID)).thenReturn(5);

            // When
            boolean result = rateLimiter.checkLimit(USER_ID);

            // Then
            assertTrue(result);
            verify(valueOperations).increment(KEY_PREFIX + USER_ID);
        }

        @Test
        @DisplayName("Should deny request when count reaches limit")
        void checkLimit_CountAtLimit_Denied() {
            // Given: Count at limit
            when(valueOperations.get(KEY_PREFIX + USER_ID)).thenReturn(10);

            // When
            boolean result = rateLimiter.checkLimit(USER_ID);

            // Then
            assertFalse(result);
            verify(valueOperations, never()).increment(anyString());
        }

        @Test
        @DisplayName("Should deny request when count exceeds limit")
        void checkLimit_CountExceedsLimit_Denied() {
            // Given: Count exceeds limit
            when(valueOperations.get(KEY_PREFIX + USER_ID)).thenReturn(15);

            // When
            boolean result = rateLimiter.checkLimit(USER_ID);

            // Then
            assertFalse(result);
            verify(valueOperations, never()).increment(anyString());
        }

        @Test
        @DisplayName("Should return false for null userId")
        void checkLimit_NullUserId_Denied() {
            // When
            boolean result = rateLimiter.checkLimit(null);

            // Then
            assertFalse(result);
            verify(redisTemplate, never()).opsForValue();
        }
    }

    @Nested
    @DisplayName("getRemainingCount Tests")
    class GetRemainingCountTests {

        @Test
        @DisplayName("Should return full limit when no count exists")
        void getRemainingCount_NoCount_ReturnsFullLimit() {
            // Given
            when(valueOperations.get(KEY_PREFIX + USER_ID)).thenReturn(null);

            // When
            int remaining = rateLimiter.getRemainingCount(USER_ID);

            // Then
            assertEquals(10, remaining);
        }

        @Test
        @DisplayName("Should return correct remaining count")
        void getRemainingCount_ExistingCount_ReturnsCorrect() {
            // Given
            when(valueOperations.get(KEY_PREFIX + USER_ID)).thenReturn(3);

            // When
            int remaining = rateLimiter.getRemainingCount(USER_ID);

            // Then
            assertEquals(7, remaining);
        }

        @Test
        @DisplayName("Should return 0 when limit exceeded")
        void getRemainingCount_LimitExceeded_ReturnsZero() {
            // Given
            when(valueOperations.get(KEY_PREFIX + USER_ID)).thenReturn(15);

            // When
            int remaining = rateLimiter.getRemainingCount(USER_ID);

            // Then
            assertEquals(0, remaining);
        }

        @Test
        @DisplayName("Should return 0 for null userId")
        void getRemainingCount_NullUserId_ReturnsZero() {
            // When
            int remaining = rateLimiter.getRemainingCount(null);

            // Then
            assertEquals(0, remaining);
        }
    }

    @Nested
    @DisplayName("getCurrentCount Tests")
    class GetCurrentCountTests {

        @Test
        @DisplayName("Should return current count from Redis")
        void getCurrentCount_ExistingCount_ReturnsCount() {
            // Given
            when(valueOperations.get(KEY_PREFIX + USER_ID)).thenReturn(5);

            // When
            int count = rateLimiter.getCurrentCount(USER_ID);

            // Then
            assertEquals(5, count);
        }

        @Test
        @DisplayName("Should return 0 when no count exists")
        void getCurrentCount_NoCount_ReturnsZero() {
            // Given
            when(valueOperations.get(KEY_PREFIX + USER_ID)).thenReturn(null);

            // When
            int count = rateLimiter.getCurrentCount(USER_ID);

            // Then
            assertEquals(0, count);
        }

        @Test
        @DisplayName("Should return 0 for null userId")
        void getCurrentCount_NullUserId_ReturnsZero() {
            // When
            int count = rateLimiter.getCurrentCount(null);

            // Then
            assertEquals(0, count);
        }
    }

    @Nested
    @DisplayName("resetLimit Tests")
    class ResetLimitTests {

        @Test
        @DisplayName("Should delete key from Redis")
        void resetLimit_ExistingKey_DeletesKey() {
            // When
            rateLimiter.resetLimit(USER_ID);

            // Then
            verify(redisTemplate).delete(KEY_PREFIX + USER_ID);
        }

        @Test
        @DisplayName("Should do nothing for null userId")
        void resetLimit_NullUserId_DoesNothing() {
            // When
            rateLimiter.resetLimit(null);

            // Then
            verify(redisTemplate, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should return correct limit")
        void getLimit_ReturnsCorrectLimit() {
            assertEquals(10, rateLimiter.getLimit());
        }

        @Test
        @DisplayName("Should return correct window seconds")
        void getWindowSeconds_ReturnsCorrectWindow() {
            assertEquals(60, rateLimiter.getWindowSeconds());
        }
    }
}