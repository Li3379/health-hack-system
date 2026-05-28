package com.hhs.service;

import com.hhs.entity.MoodEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Mood Service
 * Manages mood tracking entries and provides insights
 */
public interface MoodService {

    /**
     * Record or update a mood entry for a given date (upsert by userId + entryDate)
     *
     * @param userId       User ID
     * @param moodScore    Mood score (1-10)
     * @param energyLevel  Energy level (1-10, nullable)
     * @param stressLevel  Stress level (1-10, nullable)
     * @param sleepQuality Sleep quality (1-10, nullable)
     * @param notes        Optional notes
     * @param entryDate    Date of the entry
     * @return Created or updated mood entry
     */
    MoodEntry recordMood(Long userId, int moodScore, Integer energyLevel,
                         Integer stressLevel, Integer sleepQuality,
                         String notes, LocalDate entryDate);

    /**
     * Get mood history for the last N days
     *
     * @param userId User ID
     * @param days   Number of days to look back
     * @return List of mood entries ordered by date descending
     */
    List<MoodEntry> getMoodHistory(Long userId, int days);

    /**
     * Get mood insights including averages and trend
     *
     * @param userId User ID
     * @return Map containing avgMood, avgEnergy, avgStress, avgSleep, trend
     */
    Map<String, Object> getMoodInsights(Long userId);

    /**
     * Delete a mood entry by ID (only if owned by the user)
     *
     * @param userId  User ID
     * @param entryId Entry ID
     */
    void deleteMoodEntry(Long userId, Long entryId);
}
