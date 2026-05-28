package com.hhs.domain.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhs.domain.event.ScoreUpdatedEvent;
import com.hhs.entity.HealthScoreHistory;
import com.hhs.service.ScoreHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Event listener for score history persistence.
 * Runs after transaction commit to ensure score data is durable before persisting history.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScoreUpdatedEventListener {

    private final ScoreHistoryService scoreHistoryService;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onScoreUpdated(ScoreUpdatedEvent event) {
        try {
            String factorsJson = null;
            if (event.getFactors() != null) {
                try {
                    factorsJson = objectMapper.writeValueAsString(event.getFactors());
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize score factors for user {}: {}", event.getUserId(), e.getMessage());
                }
            }

            HealthScoreHistory history = HealthScoreHistory.builder()
                    .userId(event.getUserId())
                    .scoreDate(LocalDate.now())
                    .overallScore(BigDecimal.valueOf(event.getNewScore()))
                    .cardiovascularScore(event.getCardiovascularScore())
                    .metabolicScore(event.getMetabolicScore())
                    .weightScore(event.getWeightScore())
                    .lifestyleScore(event.getLifestyleScore())
                    .factorsSnapshot(factorsJson)
                    .build();

            scoreHistoryService.saveScore(event.getUserId(), history);
            log.debug("Persisted score history for user: {}, score: {}", event.getUserId(), event.getNewScore());
        } catch (Exception e) {
            log.error("Error persisting score history for user: {}", event.getUserId(), e);
        }
    }
}
