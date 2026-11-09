package com.hhs.service.domain;

import com.hhs.entity.HealthProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable catalog of the health-profile fields that completeness is measured over.
 *
 * <p>This class is the single decision point for three things that every downstream surface
 * shares: which fields count, how each field is weighted inside its importance tier, and
 * which substitution sentences the risk engine actually applies. The wording of each
 * {@code reason} is anchored to a consumer that really reads the field in this backend, so
 * the profile page cannot explain a field with a plausible-sounding claim that is false.</p>
 *
 * <p>The catalog includes the derived {@code bmi} column even though the user never types it,
 * because {@code HealthProfileServiceImpl} computes it from height and weight and the risk
 * engine substitutes it. The weights sum to exactly {@code 100.0}: 2 x 30 (required),
 * 3 x 10 (recommended), 4 x 2.5 (optional).</p>
 */
@Component
public class ProfileFieldCatalog {

    /**
     * Disclosed when no {@code health_profile} row exists at all.
     *
     * <p>The risk engine's null-profile branch returns fixed 30/25/25 and performs no age,
     * bmi or family-history substitution, so this single generic sentence is the only honest
     * disclosure for that state — emitting the three per-field sentences would promise
     * substitutions the engine will not make.</p>
     */
    public static final String NO_PROFILE_ASSUMPTION = "未填写健康档案，风险得分按通用参考值估算";

    /** Disclosed when {@code familyHistory} is absent, and also the field's catalog assumption. */
    public static final String FAMILY_HISTORY_ABSENT = "缺少家族病史，糖尿病风险评估按「无家族史」计算";

    /**
     * Disclosed when {@code familyHistory} was supplied but names no diabetes keyword.
     *
     * <p>The engine predicate treats such an input as "no family diabetes", so claiming
     * {@link #FAMILY_HISTORY_ABSENT} would assert a missing field the user filled in.</p>
     */
    public static final String FAMILY_HISTORY_SUPPLIED_NO_DIABETES =
            "家族病史未提及糖尿病，糖尿病风险评估按「无家族史」计算";

    /** Keyword the risk engine tests for; the one predicate both consumers share. */
    private static final String DIABETES_KEYWORD = "糖尿病";

    private static final List<FieldSpec> SPECS = List.of(
            new FieldSpec("gender", "性别", "required", 30.0,
                    "用于健康报告与 AI 建议中的性别化解读", null),
            new FieldSpec("birthDate", "出生日期", "required", 30.0,
                    "用于计算年龄，影响心血管与糖尿病风险评估",
                    "缺少出生日期，年龄按 35 岁估算"),
            new FieldSpec("heightCm", "身高", "recommended", 10.0,
                    "用于生成 BMI，影响风险评估与健康报告", null),
            new FieldSpec("weightKg", "体重", "recommended", 10.0,
                    "用于生成 BMI，影响风险评估与健康报告", null),
            new FieldSpec("bmi", "BMI", "recommended", 10.0,
                    "用于风险评估的 BMI 代入；缺失按 22.0 估算",
                    "缺少 BMI，按 22.0 估算"),
            new FieldSpec("bloodType", "血型", "optional", 2.5,
                    "记录在健康报告与 AI 建议中，供就医与用血场景参考", null),
            new FieldSpec("allergyHistory", "过敏史", "optional", 2.5,
                    "让 AI 建议避开与过敏史冲突的饮食与用药提醒", null),
            new FieldSpec("familyHistory", "家族病史", "optional", 2.5,
                    "家族糖尿病史直接影响糖尿病风险评估得分", FAMILY_HISTORY_ABSENT),
            new FieldSpec("lifestyleHabits", "生活习惯", "optional", 2.5,
                    "用于健康评分与 AI 建议的针对性", null)
    );

    /**
     * The catalog entries in entity and form order.
     *
     * @return an immutable copy of the nine field specifications
     */
    public List<FieldSpec> fields() {
        return List.copyOf(SPECS);
    }

    /**
     * Decide which substitution sentences describe the risk engine's behaviour for a profile.
     *
     * <p>The rules mirror {@code RiskAssessmentServiceImpl.RiskRuleEngine} branch for branch,
     * so the pre-create promise and the post-create disclosure are the same list produced by
     * the same function. Each sentence is read from the catalog's own {@code FieldSpec} rather
     * than restated, so it exists exactly once in the codebase.</p>
     *
     * @param profile the stored profile, or {@code null} when the user has no row yet
     * @return the disclosed sentences in age, bmi, family-history order; empty when complete
     */
    public static List<String> assumptionsFor(HealthProfile profile) {
        if (profile == null) {
            return List.of(NO_PROFILE_ASSUMPTION);
        }
        List<String> sentences = new ArrayList<>(3);
        if (profile.getBirthDate() == null) {
            sentences.add(assumptionOf("birthDate"));
        }
        if (profile.getBmi() == null) {
            sentences.add(assumptionOf("bmi"));
        }
        String familyHistory = profile.getFamilyHistory();
        if (familyHistory == null || familyHistory.isBlank()) {
            sentences.add(assumptionOf("familyHistory"));
        } else if (!mentionsDiabetes(familyHistory)) {
            sentences.add(FAMILY_HISTORY_SUPPLIED_NO_DIABETES);
        }
        return List.copyOf(sentences);
    }

    /**
     * Test whether a family-history text names diabetes.
     *
     * <p>This is the one keyword predicate: the catalog's sentence choice and the risk
     * engine's {@code familyDiabetes} boolean cannot disagree because the engine delegates
     * here.</p>
     *
     * @param familyHistory the free-text family history, possibly {@code null}
     * @return true when the text is non-null and contains the diabetes keyword
     */
    public static boolean mentionsDiabetes(String familyHistory) {
        return familyHistory != null && familyHistory.contains(DIABETES_KEYWORD);
    }

    private static String assumptionOf(String key) {
        return SPECS.stream()
                .filter(spec -> spec.key().equals(key))
                .map(FieldSpec::assumption)
                .findFirst()
                .orElse(null);
    }

    /**
     * One catalog entry: identity, tier, weight and the two user-facing sentences.
     *
     * @param key        stable field identifier matching the entity property name
     * @param label      user-facing Chinese field name
     * @param importance one of {@code required}, {@code recommended} or {@code optional}
     * @param weight     share of the 0-100 completeness total
     * @param reason     why the field matters, naming its real backend consumer
     * @param assumption the absent-case substitution sentence, or {@code null}
     */
    public record FieldSpec(
            String key,
            String label,
            String importance,
            double weight,
            String reason,
            String assumption
    ) {
    }
}