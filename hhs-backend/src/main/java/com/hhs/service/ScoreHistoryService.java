package com.hhs.service;

import com.hhs.entity.HealthScoreHistory;

import java.util.List;

/**
 * Score History Service
 * Manages health score history persistence and retrieval
 */
public interface ScoreHistoryService {

    /**
     * Save or update a score record for a user on a given date.
     * Uses upsert semantics: if a record for (userId, scoreDate) exists, it is updated.
     *
     * @param userId the user ID
     * @param score  the score history record to persist
     */
    void saveScore(Long userId, HealthScoreHistory score);

    /**
     * Get score history for a user within the last N days.
     *
     * @param userId the user ID
     * @param days   number of days to look back
     * @return list of score history records ordered by score_date ASC
     */
    List<HealthScoreHistory> getHistory(Long userId, int days);

    /**
     * Delete score history records older than the specified retention period.
     * Intended to be called by a scheduled task.
     *
     * @param retentionDays number of days to retain
     */
    void cleanupOldRecords(int retentionDays);
}
