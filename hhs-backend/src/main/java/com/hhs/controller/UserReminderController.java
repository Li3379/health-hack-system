package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.entity.UserReminder;
import com.hhs.security.SecurityUtils;
import com.hhs.service.UserReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User Reminder Controller
 * Handles user reminder CRUD operations
 */
@Slf4j
@Tag(name = "User Reminders", description = "用户提醒管理")
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class UserReminderController {

    private final UserReminderService userReminderService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create reminder")
    public Result<UserReminder> create(@RequestBody CreateReminderRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Creating reminder for user: {}", userId);
        UserReminder reminder = userReminderService.createReminder(
                userId, request.getTitle(), request.getDescription(),
                request.getReminderType(), request.getCronExpression());
        return Result.success(reminder);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user reminders")
    public Result<List<UserReminder>> list() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Fetching reminders for user: {}", userId);
        List<UserReminder> reminders = userReminderService.getUserReminders(userId);
        return Result.success(reminders);
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Toggle reminder active status")
    public Result<UserReminder> toggle(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Toggling reminder id: {} for user: {}", id, userId);
        UserReminder reminder = userReminderService.toggleReminder(id, userId);
        return Result.success(reminder);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete reminder")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Deleting reminder id: {} for user: {}", id, userId);
        userReminderService.deleteReminder(id, userId);
        return Result.success(null);
    }

    /**
     * Request body for creating a reminder
     */
    @lombok.Data
    public static class CreateReminderRequest {
        private String title;
        private String description;
        private String reminderType;
        private String cronExpression;
    }
}
