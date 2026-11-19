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

    /**
     * Persist the score snapshot once the publishing transaction has settled.
     *
     * <p>{@code fallbackExecution = true} is load-bearing. The publisher,
     * {@code HealthScoreServiceImpl.getScore()}, is a pure read and carries no
     * {@code @Transactional}; without this flag Spring silently skips an
     * {@code AFTER_COMMIT} listener when no transaction is active, so the history
     * row was never written and {@code health_score_history} stayed empty however
     * often the score endpoint was called. With the flag, a transactional caller
     * still gets the post-commit guarantee, and a non-transactional caller runs
     * immediately — correct here, because the score has already been computed and
     * cached by the time the event is published.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
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
