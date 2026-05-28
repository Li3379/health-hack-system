package com.hhs.service;

import com.hhs.entity.UserReminder;

import java.util.List;

/**
 * User Reminder Service
 * Manages user reminders including creation, listing, toggling, and deletion
 */
public interface UserReminderService {

    /**
     * Create a new reminder for a user
     *
     * @param userId User ID
     * @param title Reminder title
     * @param description Reminder description
     * @param reminderType Reminder type: medication|exercise|water|sleep|custom
     * @param cronExpression Cron expression for scheduling
     * @return Created UserReminder
     */
    UserReminder createReminder(Long userId, String title, String description,
                                String reminderType, String cronExpression);

    /**
     * Get all reminders for a user
     *
     * @param userId User ID
     * @return List of user reminders
     */
    List<UserReminder> getUserReminders(Long userId);

    /**
     * Toggle the active status of a reminder
     *
     * @param id Reminder ID
     * @param userId User ID (for ownership verification)
     * @return Updated UserReminder with toggled isActive
     */
    UserReminder toggleReminder(Long id, Long userId);

    /**
     * Delete a reminder
     *
     * @param id Reminder ID
     * @param userId User ID (for ownership verification)
     */
    void deleteReminder(Long id, Long userId);

    /**
     * Get all active reminders for scheduler processing
     *
     * @return List of active UserReminders
     */
    List<UserReminder> getActiveRemindersDue();
}
