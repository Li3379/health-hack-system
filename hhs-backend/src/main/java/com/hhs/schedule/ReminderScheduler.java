package com.hhs.schedule;

import com.hhs.entity.UserReminder;
import com.hhs.service.UserReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled task for processing user reminders.
 * Runs every 60 seconds to check for active reminders and trigger notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final UserReminderService userReminderService;

    /**
     * Check and log active reminders every 60 seconds.
     * Cron expression matching and actual push notification delivery
     * can be added in a future phase.
     */
    @Scheduled(fixedRate = 60000)
    public void processReminders() {
        log.debug("Checking active reminders...");
        try {
            List<UserReminder> activeReminders = userReminderService.getActiveRemindersDue();
            if (activeReminders.isEmpty()) {
                return;
            }
            log.info("Found {} active reminders due for processing", activeReminders.size());
            for (UserReminder reminder : activeReminders) {
                log.info("Reminder due: id={}, userId={}, type={}, title={}",
                        reminder.getId(), reminder.getUserId(),
                        reminder.getReminderType(), reminder.getTitle());
                // TODO: evaluate cron expression against current time
                // TODO: push notification via PushChannel
            }
        } catch (Exception e) {
            log.error("Failed to process reminders", e);
        }
    }
}
