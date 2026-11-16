package com.hhs.controller;

import com.hhs.config.TestSecurityConfig;
import com.hhs.entity.UserReminder;
import com.hhs.service.UserReminderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validation tests for {@link UserReminderController}.
 *
 * <p>Regression guard: a body missing {@code title} used to reach the persistence layer and
 * fail on a NOT NULL constraint, surfacing as an opaque {@code system.error} 500. Validation
 * must reject it at the boundary with a 400 instead.
 */
@WebMvcTest(UserReminderController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class UserReminderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserReminderService userReminderService;

    @Test
    @DisplayName("missing title is rejected as a 400 validation error and never persisted")
    void create_missingTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"description":"d","reminderType":"custom","cronExpression":"0 0 9 * * ?"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='title')]").exists());

        verify(userReminderService, never())
            .createReminder(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("unsupported reminderType is rejected as a 400 validation error")
    void create_invalidType_returns400() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"t","reminderType":"bogus","cronExpression":"0 0 9 * * ?"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(userReminderService, never())
            .createReminder(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a well-formed reminder is created and returned")
    void create_validRequest_returns200() throws Exception {
        UserReminder saved = new UserReminder();
        saved.setId(1L);
        saved.setTitle("喝水");
        when(userReminderService.createReminder(anyLong(), anyString(), any(), anyString(), anyString()))
            .thenReturn(saved);

        mockMvc.perform(post("/api/reminders")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"title":"喝水","reminderType":"water","cronExpression":"0 0 9 * * ?"}
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1));
    }
}