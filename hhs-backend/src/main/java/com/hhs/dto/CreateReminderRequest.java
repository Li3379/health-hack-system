package com.hhs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for creating a user reminder.
 *
 * <p>Validation lives here rather than in the controller so a malformed body is
 * rejected with a 400 by {@code @Valid} before it reaches the persistence layer.
 * Without it a missing title fell through to a NOT NULL constraint and surfaced
 * as an opaque 500.
 */
@Data
public class CreateReminderRequest {

    @NotBlank(message = "提醒标题不能为空")
    @Size(max = 100, message = "提醒标题不能超过100个字符")
    private String title;

    @Size(max = 500, message = "提醒描述不能超过500个字符")
    private String description;

    @NotBlank(message = "提醒类型不能为空")
    @Pattern(regexp = "^(medication|exercise|water|sleep|custom)$",
             message = "提醒类型只能是 medication、exercise、water、sleep 或 custom")
    private String reminderType;

    @NotBlank(message = "提醒时间规则不能为空")
    @Size(max = 64, message = "提醒时间规则不能超过64个字符")
    private String cronExpression;
}