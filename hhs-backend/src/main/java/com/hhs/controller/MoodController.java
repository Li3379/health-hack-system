package com.hhs.controller;

import com.hhs.common.Result;
import com.hhs.dto.RecordMoodRequest;
import com.hhs.entity.MoodEntry;
import com.hhs.security.SecurityUtils;
import com.hhs.service.MoodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Mood Controller
 * Manages mood tracking entries and insights
 */
@Slf4j
@Tag(name = "Mood Tracking", description = "心情追踪")
@RestController
@RequestMapping("/api/mood")
@RequiredArgsConstructor
public class MoodController {

    private final MoodService moodService;

    @Operation(summary = "记录心情")
    @PostMapping("/entries")
    @PreAuthorize("isAuthenticated()")
    public Result<MoodEntry> recordMood(@Valid @RequestBody RecordMoodRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Record mood request: userId={}, date={}, score={}", userId, request.getEntryDate(), request.getMoodScore());
        MoodEntry entry = moodService.recordMood(
                userId,
                request.getMoodScore(),
                request.getEnergyLevel(),
                request.getStressLevel(),
                request.getSleepQuality(),
                request.getNotes(),
                request.getEntryDate()
        );
        return Result.success(entry);
    }

    @Operation(summary = "获取心情历史")
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public Result<List<MoodEntry>> getMoodHistory(
            @Parameter(description = "查询天数") @RequestParam(defaultValue = "30") int days) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get mood history request: userId={}, days={}", userId, days);
        List<MoodEntry> entries = moodService.getMoodHistory(userId, days);
        return Result.success(entries);
    }

    @Operation(summary = "获取心情洞察")
    @GetMapping("/insights")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> getMoodInsights() {
        Long userId = SecurityUtils.getCurrentUserId();
        log.debug("Get mood insights request: userId={}", userId);
        Map<String, Object> insights = moodService.getMoodInsights(userId);
        return Result.success(insights);
    }

    @Operation(summary = "删除心情记录")
    @DeleteMapping("/entries/{id}")
    @PreAuthorize("isAuthenticated()")
    public Result<Void> deleteMoodEntry(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        log.info("Delete mood entry request: userId={}, entryId={}", userId, id);
        moodService.deleteMoodEntry(userId, id);
        return Result.success(null);
    }
}
