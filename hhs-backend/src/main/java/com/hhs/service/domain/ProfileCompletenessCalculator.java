package com.hhs.service.domain;

import com.hhs.entity.HealthProfile;
import com.hhs.vo.ProfileCompletenessVO;
import com.hhs.vo.ProfileFieldStatusVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative health-profile completeness calculator.
 *
 * <p>The completeness score is decided here and nowhere else: every surface (profile page
 * header, dashboard compact row, reminder banner, risk badge, backfill panel) reads the
 * returned {@link ProfileCompletenessVO}, so the 60/30/10 weighting, the three level
 * thresholds and the per-field copy cannot drift between surfaces.</p>
 *
 * <p>This is deliberately not a thin wrapper over the legacy deduction-based profile scorer.
 * That scheme subtracts fixed penalties and returns a neutral 70 for an empty profile, which
 * would make a brand-new user look half-complete; it also exposes no field list and no level.
 * Its legacy semantics stay untouched, and nothing here delegates to it.</p>
 */
@Component
@RequiredArgsConstructor
public class ProfileCompletenessCalculator {

    /** Inclusive lower bound of the 标准档案 tier. */
    private static final int STANDARD_TIER_MIN = 60;

    /** Inclusive lower bound of the 完整档案 tier. */
    private static final int COMPLETE_TIER_MIN = 85;

    private static final int SCORE_MAX = 100;

    private final ProfileFieldCatalog catalog;

    /**
     * Calculate completeness for a stored profile.
     *
     * @param profile the profile row, or {@code null} when the user has none yet
     * @return the authoritative completeness payload; never {@code null}
     */
    public ProfileCompletenessVO calculate(HealthProfile profile) {
        List<ProfileFieldStatusVO> statuses = new ArrayList<>();
        double earned = 0;
        int filledCount = 0;

        for (ProfileFieldCatalog.FieldSpec spec : catalog.fields()) {
            boolean filled = isFilled(spec.key(), profile);
            if (filled) {
                earned += spec.weight();
                filledCount++;
            }
            String assumption = filled ? null : spec.assumption();
            statuses.add(new ProfileFieldStatusVO(spec.key(), spec.label(), spec.importance(),
                    filled, spec.reason(), assumption));
        }

        int score = clamp((int) Math.round(earned));
        return new ProfileCompletenessVO(score, levelFor(score), filledCount, statuses.size(),
                profile != null, ProfileFieldCatalog.assumptionsFor(profile), List.copyOf(statuses));
    }

    /**
     * Map a completeness score onto its tier label.
     *
     * <p>Boundary ownership is explicit: 60 and 84 belong to 标准档案, 85 belongs to
     * 完整档案, and anything below 60 is 基础档案. A user who supplied only gender and
     * birth date therefore lands exactly on 标准档案 and keeps the dashboard banner silent.</p>
     *
     * @param score completeness score, expected in 0..100
     * @return the tier label
     */
    public String levelFor(int score) {
        if (score >= COMPLETE_TIER_MIN) {
            return "完整档案";
        }
        if (score >= STANDARD_TIER_MIN) {
            return "标准档案";
        }
        return "基础档案";
    }

    /**
     * Decide whether a single field holds a usable value.
     *
     * <p>{@code bmi} requires height, weight and bmi together: MyBatis-Plus uses a NOT_NULL
     * update strategy, so clearing height or weight leaves the previous bmi column behind.
     * Reading bmi alone would let that stale value inflate a user's reported completeness.</p>
     *
     * @param key     the catalog field identifier
     * @param profile the profile row, or {@code null}
     * @return true when the field holds a usable value
     */
    private boolean isFilled(String key, HealthProfile profile) {
        if (profile == null) {
            return false;
        }
        return switch (key) {
            case "gender" -> hasText(profile.getGender());
            case "birthDate" -> profile.getBirthDate() != null;
            case "heightCm" -> profile.getHeightCm() != null;
            case "weightKg" -> profile.getWeightKg() != null;
            case "bmi" -> profile.getHeightCm() != null
                    && profile.getWeightKg() != null
                    && profile.getBmi() != null;
            case "bloodType" -> hasText(profile.getBloodType());
            case "allergyHistory" -> hasText(profile.getAllergyHistory());
            case "familyHistory" -> hasText(profile.getFamilyHistory());
            case "lifestyleHabits" -> hasText(profile.getLifestyleHabits());
            default -> false;
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(SCORE_MAX, score));
    }
}