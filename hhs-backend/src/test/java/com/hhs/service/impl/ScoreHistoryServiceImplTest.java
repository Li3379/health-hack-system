package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.HealthScoreHistory;
import com.hhs.mapper.HealthScoreHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScoreHistoryServiceImpl
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Score History Service Tests")
class ScoreHistoryServiceImplTest {

    @Mock
    private HealthScoreHistoryMapper healthScoreHistoryMapper;

    @InjectMocks
    private ScoreHistoryServiceImpl scoreHistoryService;

    private Long testUserId = 1L;
    private HealthScoreHistory testRecord;

    @BeforeEach
    void setUp() {
        testRecord = HealthScoreHistory.builder()
                .userId(testUserId)
                .scoreDate(LocalDate.now())
                .overallScore(BigDecimal.valueOf(85.50))
                .cardiovascularScore(BigDecimal.valueOf(90.00))
                .metabolicScore(BigDecimal.valueOf(80.00))
                .weightScore(BigDecimal.valueOf(75.00))
                .lifestyleScore(BigDecimal.valueOf(88.00))
                .factorsSnapshot("{\"healthProfile\":{\"score\":90}}")
                .build();
    }

    @Test
    @DisplayName("saveScore - insert new record")
    void testSaveScore_InsertNew() {
        // Given
        when(healthScoreHistoryMapper.insertOrUpdate(any(HealthScoreHistory.class))).thenReturn(1);

        // When
        scoreHistoryService.saveScore(testUserId, testRecord);

        // Then
        ArgumentCaptor<HealthScoreHistory> captor = ArgumentCaptor.forClass(HealthScoreHistory.class);
        verify(healthScoreHistoryMapper, times(1)).insertOrUpdate(captor.capture());
        HealthScoreHistory captured = captor.getValue();
        assertEquals(testUserId, captured.getUserId());
        assertEquals(LocalDate.now(), captured.getScoreDate());
        assertEquals(BigDecimal.valueOf(85.50), captured.getOverallScore());
    }

    @Test
    @DisplayName("saveScore - upsert same day, latest wins")
    void testSaveScore_UpsertSameDay() {
        // Given: First save
        when(healthScoreHistoryMapper.insertOrUpdate(any(HealthScoreHistory.class))).thenReturn(1);
        scoreHistoryService.saveScore(testUserId, testRecord);

        // Given: Second save on same day with updated score
        HealthScoreHistory updatedRecord = HealthScoreHistory.builder()
                .userId(testUserId)
                .scoreDate(LocalDate.now())
                .overallScore(BigDecimal.valueOf(92.00))
                .factorsSnapshot("{\"healthProfile\":{\"score\":95}}")
                .build();
        when(healthScoreHistoryMapper.insertOrUpdate(any(HealthScoreHistory.class))).thenReturn(2);

        // When
        scoreHistoryService.saveScore(testUserId, updatedRecord);

        // Then: insertOrUpdate called twice (MySQL ON DUPLICATE KEY UPDATE handles upsert)
        verify(healthScoreHistoryMapper, times(2)).insertOrUpdate(any(HealthScoreHistory.class));
    }

    @Test
    @DisplayName("getHistory - returns records in date range")
    void testGetHistory_ReturnsRecordsInRange() {
        // Given
        HealthScoreHistory record1 = HealthScoreHistory.builder()
                .id(1L).userId(testUserId).scoreDate(LocalDate.now().minusDays(2))
                .overallScore(BigDecimal.valueOf(80.00)).build();
        HealthScoreHistory record2 = HealthScoreHistory.builder()
                .id(2L).userId(testUserId).scoreDate(LocalDate.now().minusDays(1))
                .overallScore(BigDecimal.valueOf(85.00)).build();
        HealthScoreHistory record3 = HealthScoreHistory.builder()
                .id(3L).userId(testUserId).scoreDate(LocalDate.now())
                .overallScore(BigDecimal.valueOf(90.00)).build();

        when(healthScoreHistoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(record1, record2, record3));

        // When
        List<HealthScoreHistory> result = scoreHistoryService.getHistory(testUserId, 7);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(LocalDate.now().minusDays(2), result.get(0).getScoreDate());
        assertEquals(LocalDate.now(), result.get(2).getScoreDate());
        verify(healthScoreHistoryMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("getHistory - returns empty list when no records")
    void testGetHistory_EmptyResult() {
        // Given
        when(healthScoreHistoryMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        // When
        List<HealthScoreHistory> result = scoreHistoryService.getHistory(testUserId, 30);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("cleanupOldRecords - deletes old records")
    void testCleanupOldRecords_DeletesOld() {
        // Given
        when(healthScoreHistoryMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(5);

        // When
        scoreHistoryService.cleanupOldRecords(365);

        // Then
        verify(healthScoreHistoryMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("cleanupOldRecords - handles zero deletions")
    void testCleanupOldRecords_ZeroDeletions() {
        // Given
        when(healthScoreHistoryMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(0);

        // When
        scoreHistoryService.cleanupOldRecords(365);

        // Then
        verify(healthScoreHistoryMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("saveScore - handles mapper exception gracefully")
    void testSaveScore_HandlesException() {
        // Given
        when(healthScoreHistoryMapper.insertOrUpdate(any(HealthScoreHistory.class)))
                .thenThrow(new RuntimeException("DB error"));

        // When & Then: Should not throw
        assertDoesNotThrow(() -> scoreHistoryService.saveScore(testUserId, testRecord));
        verify(healthScoreHistoryMapper, times(1)).insertOrUpdate(any(HealthScoreHistory.class));
    }
}
