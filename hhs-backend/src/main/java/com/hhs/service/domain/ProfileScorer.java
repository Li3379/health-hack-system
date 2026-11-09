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
        return calculate(userId, AssumptionCollector.noop());
    }

    /**
     * Calculate score based on health profile completeness and quality, disclosing substitutions.
     *
     * <p>The empty-profile branch returns the legacy neutral 70 and names that substitution.
     * The four deductions below describe score loss, not value substitution, so they add no
     * assumption. Do not return the weighted completeness score here: it would drop an
     * existing user's overall health score by roughly 17.5 points.</p>
     *
     * @param userId    the user ID
     * @param collector receives the substitution sentence, ignored when {@code null}
     * @return score from 0-100
     */
    public int calculate(Long userId, AssumptionCollector collector) {
        List<HealthProfile> profiles = latestProfiles(userId);

        if (profiles.isEmpty()) {
            if (collector != null) {
                collector.add("未填写健康档案，风险得分按通用参考值估算");
            }
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

    /**
     * Fetch the newest stored profile row for a user.
     *
     * <p>Extracted so the score path reads the row once for scoring and once for completeness,
     * with each caller owning its own single-row indexed lookup instead of sharing an instance.</p>
     *
     * @param userId the user ID
     * @return the newest profile, or {@code null} when the user has none
     */
    public HealthProfile latestProfile(Long userId) {
        List<HealthProfile> profiles = latestProfiles(userId);
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    private List<HealthProfile> latestProfiles(Long userId) {
        return healthProfileMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<HealthProfile>()
                        .eq(HealthProfile::getUserId, userId)
                        .orderByDesc(HealthProfile::getCreateTime)
                        .last("LIMIT 1")
        );
    }
}