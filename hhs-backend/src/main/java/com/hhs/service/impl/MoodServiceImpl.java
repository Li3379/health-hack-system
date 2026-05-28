package com.hhs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hhs.entity.MoodEntry;
import com.hhs.mapper.MoodEntryMapper;
import com.hhs.service.MoodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Mood Service Implementation
 * Manages mood tracking with upsert support and trend analysis
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoodServiceImpl implements MoodService {

    private final MoodEntryMapper moodEntryMapper;

    @Override
    @Transactional(timeout = 30)
    public MoodEntry recordMood(Long userId, int moodScore, Integer energyLevel,
                                Integer stressLevel, Integer sleepQuality,
                                String notes, LocalDate entryDate) {
        log.info("Recording mood for user: {}, date: {}, score: {}", userId, entryDate, moodScore);

        // Manual upsert: check if entry exists for this user+date
        LambdaQueryWrapper<MoodEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MoodEntry::getUserId, userId);
        wrapper.eq(MoodEntry::getEntryDate, entryDate);
        MoodEntry existing = moodEntryMapper.selectOne(wrapper);

        if (existing != null) {
            // Update existing entry
            existing.setMoodScore(moodScore);
            existing.setEnergyLevel(energyLevel);
            existing.setStressLevel(stressLevel);
            existing.setSleepQuality(sleepQuality);
            existing.setNotes(notes);
            moodEntryMapper.updateById(existing);
            log.info("Mood entry updated: id={}", existing.getId());
            return existing;
        } else {
            // Create new entry
            MoodEntry entry = MoodEntry.builder()
                    .userId(userId)
                    .moodScore(moodScore)
                    .energyLevel(energyLevel)
                    .stressLevel(stressLevel)
                    .sleepQuality(sleepQuality)
                    .notes(notes)
                    .entryDate(entryDate)
                    .createdAt(LocalDateTime.now())
                    .build();
            moodEntryMapper.insert(entry);
            log.info("Mood entry created: id={}", entry.getId());
            return entry;
        }
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public List<MoodEntry> getMoodHistory(Long userId, int days) {
        LocalDate startDate = LocalDate.now().minusDays(days);

        LambdaQueryWrapper<MoodEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MoodEntry::getUserId, userId);
        wrapper.ge(MoodEntry::getEntryDate, startDate);
        wrapper.orderByDesc(MoodEntry::getEntryDate);

        return moodEntryMapper.selectList(wrapper);
    }

    @Override
    @Transactional(timeout = 30, readOnly = true)
    public Map<String, Object> getMoodInsights(Long userId) {
        // Get last 30 days of entries for insights
        List<MoodEntry> entries = getMoodHistory(userId, 30);

        Map<String, Object> insights = new LinkedHashMap<>();
        if (entries.isEmpty()) {
            insights.put("avgMood", null);
            insights.put("avgEnergy", null);
            insights.put("avgStress", null);
            insights.put("avgSleep", null);
            insights.put("trend", "stable");
            insights.put("entryCount", 0);
            return insights;
        }

        // Calculate averages
        double avgMood = entries.stream()
                .mapToInt(MoodEntry::getMoodScore)
                .average().orElse(0.0);

        Double avgEnergy = entries.stream()
                .filter(e -> e.getEnergyLevel() != null)
                .mapToInt(MoodEntry::getEnergyLevel)
                .average().orElse(Double.NaN);
        if (Double.isNaN(avgEnergy)) avgEnergy = null;

        Double avgStress = entries.stream()
                .filter(e -> e.getStressLevel() != null)
                .mapToInt(MoodEntry::getStressLevel)
                .average().orElse(Double.NaN);
        if (Double.isNaN(avgStress)) avgStress = null;

        Double avgSleep = entries.stream()
                .filter(e -> e.getSleepQuality() != null)
                .mapToInt(MoodEntry::getSleepQuality)
                .average().orElse(Double.NaN);
        if (Double.isNaN(avgSleep)) avgSleep = null;

        // Calculate trend: compare recent half vs older half
        String trend = calculateTrend(entries);

        insights.put("avgMood", Math.round(avgMood * 10.0) / 10.0);
        insights.put("avgEnergy", avgEnergy != null ? Math.round(avgEnergy * 10.0) / 10.0 : null);
        insights.put("avgStress", avgStress != null ? Math.round(avgStress * 10.0) / 10.0 : null);
        insights.put("avgSleep", avgSleep != null ? Math.round(avgSleep * 10.0) / 10.0 : null);
        insights.put("trend", trend);
        insights.put("entryCount", entries.size());

        return insights;
    }

    /**
     * Calculate mood trend by comparing the average mood of the recent half
     * of entries against the older half.
     */
    private String calculateTrend(List<MoodEntry> entries) {
        if (entries.size() < 4) {
            return "stable";
        }

        // Entries are ordered by date descending, so index 0 is most recent
        int mid = entries.size() / 2;
        List<MoodEntry> recentHalf = entries.subList(0, mid);
        List<MoodEntry> olderHalf = entries.subList(mid, entries.size());

        double recentAvg = recentHalf.stream()
                .mapToInt(MoodEntry::getMoodScore).average().orElse(0.0);
        double olderAvg = olderHalf.stream()
                .mapToInt(MoodEntry::getMoodScore).average().orElse(0.0);

        double diff = recentAvg - olderAvg;
        if (diff > 0.5) {
            return "improving";
        } else if (diff < -0.5) {
            return "declining";
        }
        return "stable";
    }

    @Override
    @Transactional(timeout = 30)
    public void deleteMoodEntry(Long userId, Long entryId) {
        MoodEntry entry = moodEntryMapper.selectById(entryId);
        if (entry == null || !entry.getUserId().equals(userId)) {
            throw new IllegalArgumentException("心情记录不存在或无权删除");
        }
        moodEntryMapper.deleteById(entryId);
        log.info("Mood entry deleted: id={}, userId={}", entryId, userId);
    }
}
