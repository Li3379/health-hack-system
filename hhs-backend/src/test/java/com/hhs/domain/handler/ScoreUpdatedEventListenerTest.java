package com.hhs.domain.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.domain.event.ScoreUpdatedEvent;
import com.hhs.entity.HealthScoreHistory;
import com.hhs.service.ScoreHistoryService;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScoreUpdatedEventListener
 * Verifies that dimension scores are correctly persisted to health_score_history.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Score Updated Event Listener Tests")
class ScoreUpdatedEventListenerTest {

    @Mock
    private ScoreHistoryService scoreHistoryService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ScoreUpdatedEventListener listener;

    private static final Long USER_ID = 42L;

    @Test
    @DisplayName("onScoreUpdated - persists dimension scores from event")
    void testDimensionScoresPersisted() throws Exception {
        // Given: factors serialization succeeds
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":1}");

        // Given: event with dimension scores
        Map<String, Integer> dimensionScores = Map.of(
                "cardiovascular", 90,
                "metabolic", 80,
                "weight", 75,
                "lifestyle", 85
        );
        ScoreUpdatedEvent event = new ScoreUpdatedEvent(
                USER_ID, 82, "GOOD",
                Map.of("healthProfile", Map.of("score", 90, "weight", 0.25)),
                dimensionScores
        );

        // When
        listener.onScoreUpdated(event);

        // Then: dimension scores are passed to saveScore
        ArgumentCaptor<HealthScoreHistory> captor = ArgumentCaptor.forClass(HealthScoreHistory.class);
        verify(scoreHistoryService).saveScore(eq(USER_ID), captor.capture());

        HealthScoreHistory saved = captor.getValue();
        assertEquals(BigDecimal.valueOf(82), saved.getOverallScore());
        assertEquals(BigDecimal.valueOf(90), saved.getCardiovascularScore());
        assertEquals(BigDecimal.valueOf(80), saved.getMetabolicScore());
        assertEquals(BigDecimal.valueOf(75), saved.getWeightScore());
        assertEquals(BigDecimal.valueOf(85), saved.getLifestyleScore());
    }

    @Test
    @DisplayName("onScoreUpdated - handles event without dimension scores (backward compatible)")
    void testBackwardCompatibleWithoutDimensionScores() throws Exception {
        // Given: factors serialization succeeds
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":1}");

        // Given: event without dimension scores (legacy constructor)
        ScoreUpdatedEvent event = new ScoreUpdatedEvent(
                USER_ID, 75, "GOOD",
                Map.of("latestMetrics", Map.of("score", 75))
        );

        // When
        listener.onScoreUpdated(event);

        // Then: overall score persisted, dimension scores are null
        ArgumentCaptor<HealthScoreHistory> captor = ArgumentCaptor.forClass(HealthScoreHistory.class);
        verify(scoreHistoryService).saveScore(eq(USER_ID), captor.capture());

        HealthScoreHistory saved = captor.getValue();
        assertEquals(BigDecimal.valueOf(75), saved.getOverallScore());
        assertEquals(null, saved.getCardiovascularScore());
        assertEquals(null, saved.getMetabolicScore());
        assertEquals(null, saved.getWeightScore());
        assertEquals(null, saved.getLifestyleScore());
    }

    @Test
    @DisplayName("onScoreUpdated - gracefully handles serialization failure")
    void testHandlesSerializationFailure() throws Exception {
        // Given: JSON serialization fails
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON error"));

        Map<String, Integer> dimensionScores = Map.of(
                "cardiovascular", 95,
                "metabolic", 88,
                "weight", 82,
                "lifestyle", 90
        );
        ScoreUpdatedEvent event = new ScoreUpdatedEvent(
                USER_ID, 90, "EXCELLENT", null, dimensionScores
        );

        // When
        listener.onScoreUpdated(event);

        // Then: still saves with null factors snapshot, but dimension scores preserved
        ArgumentCaptor<HealthScoreHistory> captor = ArgumentCaptor.forClass(HealthScoreHistory.class);
        verify(scoreHistoryService).saveScore(eq(USER_ID), captor.capture());

        HealthScoreHistory saved = captor.getValue();
        assertEquals(BigDecimal.valueOf(90), saved.getOverallScore());
        assertEquals(BigDecimal.valueOf(95), saved.getCardiovascularScore());
        assertEquals(null, saved.getFactorsSnapshot());
    }
}
