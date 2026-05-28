package com.hhs.service;

import com.hhs.entity.GoalProgress;
import com.hhs.entity.HealthGoal;

import java.time.LocalDate;
import java.util.List;

/**
 * Goal Service
 * Manages health goals and progress tracking
 */
public interface GoalService {

    /**
     * Create a new health goal for a user
     *
     * @param userId      User ID
     * @param title       Goal title
     * @param description Goal description
     * @param metricKey   Metric key to track
     * @param targetValue Target value to achieve
     * @param unit        Unit of measurement
     * @param startDate   Goal start date
     * @param endDate     Goal end date (optional)
     * @return Created health goal
     */
    HealthGoal createGoal(Long userId, String title, String description,
                          String metricKey, double targetValue, String unit,
                          LocalDate startDate, LocalDate endDate);

    /**
     * Get all goals for a user
     *
     * @param userId User ID
     * @return List of health goals
     */
    List<HealthGoal> getUserGoals(Long userId);

    /**
     * Update a health goal (title, description, endDate)
     *
     * @param id          Goal ID
     * @param userId      User ID (for authorization)
     * @param title       New title (nullable)
     * @param description New description (nullable)
     * @param endDate     New end date (nullable)
     * @return Updated health goal
     */
    HealthGoal updateGoal(Long id, Long userId, String title, String description, LocalDate endDate);

    /**
     * Add progress to a goal, update currentValue, and auto-complete if target met
     *
     * @param goalId Goal ID
     * @param userId User ID (for authorization)
     * @param value  Progress value
     * @param note   Optional note
     * @return Created goal progress entry
     */
    GoalProgress addProgress(Long goalId, Long userId, double value, String note);

    /**
     * Check if a goal should be marked as completed based on currentValue vs targetValue
     *
     * @param goal Health goal to check
     */
    void checkGoalCompletion(HealthGoal goal);
}
