package com.hhs.service.domain;

import com.hhs.entity.HealthProfile;
import com.hhs.mapper.HealthProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Profile scorer - handles health profile scoring
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileScorer {

    private final HealthProfileMapper healthProfileMapper;

    /**
     * Calculate score based on health profile completeness and quality
     *
     * @param userId the user ID
     * @return score from 0-100
     */
    public int calculate(Long userId) {
        List<HealthProfile> profiles = healthProfileMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthProfile>()
                        .eq(HealthProfile::getUserId, userId)
                        .orderByDesc(HealthProfile::getCreateTime)
                        .last("LIMIT 1")
        );

        if (profiles.isEmpty()) {
            return 70; // Neutral score
        }

        HealthProfile profile = profiles.get(0);
        int score = 100;

        // Basic profile completeness check
        if (profile.getGender() == null || profile.getBirthDate() == null) {
            score -= 10;
        }

        if (profile.getHeightCm() == null || profile.getWeightKg() == null) {
            score -= 15;
        }

        if (profile.getBmi() == null) {
            score -= 10;
        }

        // Lifestyle habits check (from text field)
        String lifestyle = profile.getLifestyleHabits();
        if (lifestyle == null || lifestyle.trim().isEmpty()) {
            score -= 15;
        }

        return Math.max(0, Math.min(100, score));
    }
}
