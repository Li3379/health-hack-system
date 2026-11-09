package com.hhs.service.domain;

import com.hhs.entity.HealthProfile;
import com.hhs.vo.ProfileCompletenessVO;
import com.hhs.vo.ProfileFieldStatusVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ProfileCompletenessCalculator} and the catalog metadata it exposes.
 *
 * <p>A plain JUnit 5 test with no Spring context and no Mockito: the calculator is a pure
 * domain component that only needs the catalog. The first group of cases pins the
 * end-to-end slice (absent profile, empty profile row, the nine catalog entries) so a
 * delegation to the legacy {@code ProfileScorer} scheme fails here with
 * {@code expected: <0> but was: <70>}.</p>
 */
class ProfileCompletenessCalculatorTest {

    private static final List<String> EXPECTED_KEYS = List.of(
            "gender", "birthDate", "heightCm", "weightKg", "bmi",
            "bloodType", "allergyHistory", "familyHistory", "lifestyleHabits");

    private ProfileFieldCatalog catalog;
    private ProfileCompletenessCalculator calculator;

    @BeforeEach
    void setUp() {
        catalog = new ProfileFieldCatalog();
        calculator = new ProfileCompletenessCalculator(catalog);
    }

    @Test
    @DisplayName("An absent profile scores 0 at 基础档案 with no filled field")
    void absentProfileScoresZero() {
        ProfileCompletenessVO vo = calculator.calculate(null);

        assertEquals(0, vo.score());
        assertEquals("基础档案", vo.level());
        assertEquals(0, vo.filledCount());
        assertFalse(vo.hasProfile());
        assertEquals(catalog.fields().size(), vo.totalCount());
        assertEquals(catalog.fields().size(), vo.fields().size());
        vo.fields().forEach(field -> assertFalse(field.filled(), field.key() + " must be unfilled"));
    }

    @Test
    @DisplayName("An empty profile row is distinguishable from an absent one")
    void emptyProfileRowIsPresent() {
        ProfileCompletenessVO absent = calculator.calculate(null);
        ProfileCompletenessVO empty = calculator.calculate(new HealthProfile());

        assertFalse(absent.hasProfile());
        assertTrue(empty.hasProfile());
        assertEquals(absent.score(), empty.score());
        assertEquals(absent.level(), empty.level());
        assertEquals(absent.filledCount(), empty.filledCount());
        assertEquals(absent.totalCount(), empty.totalCount());
    }

    @Test
    @DisplayName("Every field carries a reason; only substitution fields carry an assumption")
    void reasonsAndAssumptionsArePopulated() {
        ProfileCompletenessVO vo = calculator.calculate(null);

        for (ProfileFieldStatusVO field : vo.fields()) {
            assertNotNull(field.reason(), field.key() + " reason");
            assertFalse(field.reason().isBlank(), field.key() + " reason must not be blank");
            assertNotNull(field.label(), field.key() + " label");
            assertNotNull(field.importance(), field.key() + " importance");
        }

        Set<String> withAssumption = vo.fields().stream()
                .filter(field -> field.assumption() != null)
                .map(ProfileFieldStatusVO::key)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("birthDate", "bmi", "familyHistory"), withAssumption);
    }

    @Test
    @DisplayName("Catalog keys follow the entity field order")
    void catalogOrderMatchesEntity() {
        List<String> actual = catalog.fields().stream().map(ProfileFieldCatalog.FieldSpec::key).toList();

        assertEquals(EXPECTED_KEYS, actual);
    }

    @Test
    @DisplayName("totalCount is derived from the catalog, never hardcoded")
    void totalCountTracksCatalogSize() {
        ProfileCompletenessVO vo = calculator.calculate(new HealthProfile());

        assertEquals(catalog.fields().size(), vo.totalCount());
        assertEquals(vo.fields().size(), vo.totalCount());
    }

    @Test
    @DisplayName("Catalog weights sum to one hundred")
    void catalogWeightsSumToOneHundred() {
        double total = catalog.fields().stream().mapToDouble(ProfileFieldCatalog.FieldSpec::weight).sum();

        assertEquals(100.0, total, 0.0001);
        assertEquals(EXPECTED_KEYS.size(), catalog.fields().size());
    }

    @Test
    @DisplayName("An absent profile discloses exactly the one generic assumption")
    void absentProfileHasSingleGenericAssumption() {
        List<String> assumptions = ProfileFieldCatalog.assumptionsFor(null);

        assertEquals(1, assumptions.size());
        assertEquals(ProfileFieldCatalog.NO_PROFILE_ASSUMPTION, assumptions.get(0));
        assertTrue(Objects.equals(calculator.calculate(null).assumptions(), assumptions));
    }

    @Test
    @DisplayName("A fully populated profile discloses no assumption")
    void completeProfileDisclosesNothing() {
        HealthProfile profile = new HealthProfile();
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        profile.setBmi(new java.math.BigDecimal("22.5"));
        profile.setFamilyHistory("父亲有糖尿病");

        assertEquals(List.of(), ProfileFieldCatalog.assumptionsFor(profile));
        assertEquals(List.of(), calculator.calculate(profile).assumptions());
    }

    @Test
    @DisplayName("An unfilled field renders its assumption as null when the catalog has none")
    void unfilledFieldWithoutAssumptionStaysNull() {
        ProfileCompletenessVO vo = calculator.calculate(new HealthProfile());

        ProfileFieldStatusVO gender = vo.fields().stream()
                .filter(field -> "gender".equals(field.key())).findFirst().orElseThrow();
        assertFalse(gender.filled());
        assertNull(gender.assumption());
    }

    @Test
    @DisplayName("A filled field never renders an assumption sentence")
    void filledFieldDropsItsAssumption() {
        HealthProfile profile = new HealthProfile();
        profile.setFamilyHistory("父亲有高血压");

        ProfileFieldStatusVO family = calculator.calculate(profile).fields().stream()
                .filter(field -> "familyHistory".equals(field.key())).findFirst().orElseThrow();

        assertTrue(family.filled());
        assertNull(family.assumption());
    }

    // ---------------------------------------------------------------- weight groups

    @Test
    @DisplayName("Only gender and birth date lands exactly on 60 and 标准档案")
    void requiredFieldsAloneReachStandardTier() {
        ProfileCompletenessVO vo = calculator.calculate(profile("gender", "birthDate"));

        assertEquals(60, vo.score());
        assertEquals("标准档案", vo.level());
        assertEquals(2, vo.filledCount());
    }

    @Test
    @DisplayName("Required plus one recommended scores 70")
    void requiredPlusOneRecommendedScoresSeventy() {
        ProfileCompletenessVO vo = calculator.calculate(profile("gender", "birthDate", "heightCm"));

        assertEquals(70, vo.score());
        assertEquals(3, vo.filledCount());
        assertEquals("标准档案", vo.level());
    }

    @Test
    @DisplayName("All required and all recommended scores 90 and 完整档案")
    void requiredAndRecommendedReachCompleteTier() {
        ProfileCompletenessVO vo = calculator.calculate(
                profile("gender", "birthDate", "heightCm", "weightKg", "bmi"));

        assertEquals(90, vo.score());
        assertEquals("完整档案", vo.level());
        assertEquals(5, vo.filledCount());
    }

    @Test
    @DisplayName("All nine fields score 100 with nothing left to disclose")
    void fullyPopulatedProfileScoresOneHundred() {
        ProfileCompletenessVO vo = calculator.calculate(profile(EXPECTED_KEYS.toArray(String[]::new)));

        assertEquals(100, vo.score());
        assertEquals("完整档案", vo.level());
        assertEquals(EXPECTED_KEYS.size(), vo.filledCount());
        assertEquals(EXPECTED_KEYS.size(), vo.totalCount());
        assertEquals(List.of(), vo.assumptions());
        assertTrue(vo.hasProfile());
        vo.fields().forEach(field -> {
            assertTrue(field.filled(), field.key() + " must be filled");
            assertNull(field.assumption(), field.key() + " must drop its assumption when filled");
        });
    }

    @Test
    @DisplayName("Optional fields alone score 10 and stay 基础档案")
    void optionalFieldsAloneStayBasic() {
        ProfileCompletenessVO vo = calculator.calculate(
                profile("bloodType", "allergyHistory", "familyHistory", "lifestyleHabits"));

        assertEquals(10, vo.score());
        assertEquals("基础档案", vo.level());
        assertEquals(4, vo.filledCount());
    }

    // ---------------------------------------------------------------- tier boundaries

    @Test
    @DisplayName("Tier boundary ownership is exact at 0, 59, 60, 84, 85 and 100")
    void tierBoundaryOwnership() {
        assertEquals("基础档案", calculator.levelFor(0));
        assertEquals("基础档案", calculator.levelFor(59));
        assertEquals("标准档案", calculator.levelFor(60));
        assertEquals("标准档案", calculator.levelFor(84));
        assertEquals("完整档案", calculator.levelFor(85));
        assertEquals("完整档案", calculator.levelFor(100));
    }

    // ---------------------------------------------------------------- staleness

    @Test
    @DisplayName("A stale bmi column without height and weight never counts")
    void staleBmiDoesNotCount() {
        HealthProfile stale = new HealthProfile();
        stale.setBmi(new java.math.BigDecimal("22.5"));

        ProfileCompletenessVO staleVo = calculator.calculate(stale);
        ProfileCompletenessVO clearedVo = calculator.calculate(new HealthProfile());

        ProfileFieldStatusVO bmi = staleVo.fields().stream()
                .filter(field -> "bmi".equals(field.key())).findFirst().orElseThrow();
        assertFalse(bmi.filled());
        assertEquals(clearedVo.score(), staleVo.score());
        assertEquals(0, staleVo.score());
    }

    // ---------------------------------------------------------------- assumptionsFor branches

    @Test
    @DisplayName("An absent profile discloses the generic sentence only")
    void absentProfileAssumptionsExcludeFieldSentences() {
        List<String> assumptions = ProfileFieldCatalog.assumptionsFor(null);

        assertEquals(List.of(ProfileFieldCatalog.NO_PROFILE_ASSUMPTION), assumptions);
        assertFalse(assumptions.contains("缺少出生日期，年龄按 35 岁估算"));
        assertFalse(assumptions.contains("缺少 BMI，按 22.0 估算"));
        assertFalse(assumptions.contains(ProfileFieldCatalog.FAMILY_HISTORY_ABSENT));
    }

    @Test
    @DisplayName("An empty row discloses the three per-field sentences in age, bmi, family order")
    void sparseProfileDisclosesThreeSentencesInOrder() {
        List<String> assumptions = ProfileFieldCatalog.assumptionsFor(new HealthProfile());

        assertEquals(List.of(
                "缺少出生日期，年龄按 35 岁估算",
                "缺少 BMI，按 22.0 估算",
                ProfileFieldCatalog.FAMILY_HISTORY_ABSENT), assumptions);
        assertFalse(assumptions.contains(ProfileFieldCatalog.NO_PROFILE_ASSUMPTION));
    }

    @Test
    @DisplayName("A supplied non-diabetic family history never claims the field is missing")
    void suppliedNonDiabeticHistoryUsesKeywordlessSentence() {
        HealthProfile profile = new HealthProfile();
        profile.setBirthDate(LocalDate.of(1990, 1, 1));
        profile.setBmi(new java.math.BigDecimal("22.5"));
        profile.setFamilyHistory("父亲有高血压");

        List<String> assumptions = ProfileFieldCatalog.assumptionsFor(profile);

        assertEquals(List.of(ProfileFieldCatalog.FAMILY_HISTORY_SUPPLIED_NO_DIABETES), assumptions);
        assertFalse(assumptions.contains(ProfileFieldCatalog.FAMILY_HISTORY_ABSENT));
        assertFalse(assumptions.contains("缺少家族病史"));
        assertEquals(assumptions, calculator.calculate(profile).assumptions());
    }

    @Test
    @DisplayName("The catalog's two family-history sentences are distinct and non-blank")
    void familyHistorySentencesAreDistinct() {
        assertFalse(ProfileFieldCatalog.FAMILY_HISTORY_ABSENT.isBlank());
        assertFalse(ProfileFieldCatalog.FAMILY_HISTORY_SUPPLIED_NO_DIABETES.isBlank());
        assertFalse(ProfileFieldCatalog.FAMILY_HISTORY_ABSENT
                .equals(ProfileFieldCatalog.FAMILY_HISTORY_SUPPLIED_NO_DIABETES));
        assertFalse(ProfileFieldCatalog.NO_PROFILE_ASSUMPTION.isBlank());
    }

    @Test
    @DisplayName("A filled keyed field drops its catalog assumption")
    void filledKeyedFieldDropsCatalogAssumption() {
        ProfileFieldStatusVO birthDate = calculator.calculate(profile("birthDate")).fields().stream()
                .filter(field -> "birthDate".equals(field.key())).findFirst().orElseThrow();

        assertTrue(birthDate.filled());
        assertNull(birthDate.assumption());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Build a profile that fills exactly the named catalog keys and nothing else.
     *
     * @param keys the catalog keys to populate
     * @return a profile with only those fields set
     */
    private HealthProfile profile(String... keys) {
        HealthProfile profile = new HealthProfile();
        for (String key : keys) {
            switch (key) {
                case "gender" -> profile.setGender("男");
                case "birthDate" -> profile.setBirthDate(LocalDate.of(1990, 1, 1));
                case "heightCm" -> profile.setHeightCm(new java.math.BigDecimal("175"));
                case "weightKg" -> profile.setWeightKg(new java.math.BigDecimal("70"));
                case "bmi" -> profile.setBmi(new java.math.BigDecimal("22.9"));
                case "bloodType" -> profile.setBloodType("A");
                case "allergyHistory" -> profile.setAllergyHistory("青霉素过敏");
                case "familyHistory" -> profile.setFamilyHistory("父亲有糖尿病");
                case "lifestyleHabits" -> profile.setLifestyleHabits("每周跑步三次");
                default -> throw new IllegalArgumentException("unknown catalog key: " + key);
            }
        }
        return profile;
    }
}