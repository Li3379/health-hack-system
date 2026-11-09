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
}