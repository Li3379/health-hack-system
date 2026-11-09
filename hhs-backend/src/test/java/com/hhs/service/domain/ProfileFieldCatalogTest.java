package com.hhs.service.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link ProfileFieldCatalog} content.
 *
 * <p>Every {@code reason} is asserted against its OWN backend consumer, one assertion per
 * field, because a shared keyword such as 评分 would let a reason that names an entirely
 * different consumer pass. Two negative guards make a wrong claim fail loudly: no reason may
 * contain 体重维度 (that dimension reads a realtime {@code bmi} metric from
 * {@code RealtimeMetricMapper}, never the profile), and only the birth-date reason may
 * mention 风险 (gender feeds the AI and report prompts, not the risk engine).</p>
 */
class ProfileFieldCatalogTest {

    private static final List<String> EXPECTED_KEYS = List.of(
            "gender", "birthDate", "heightCm", "weightKg", "bmi",
            "bloodType", "allergyHistory", "familyHistory", "lifestyleHabits");

    private static final List<String> RAW_IDENTIFIERS = EXPECTED_KEYS;

    private static final int MAX_REASON_LENGTH = 30;

    private ProfileFieldCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new ProfileFieldCatalog();
    }

    @Test
    @DisplayName("The catalog enumerates the nine fields in entity order")
    void catalogKeysInOrder() {
        List<String> keys = catalog.fields().stream().map(ProfileFieldCatalog.FieldSpec::key).toList();

        assertEquals(EXPECTED_KEYS, keys);
        assertEquals(9, catalog.fields().size());
    }

    @Test
    @DisplayName("Every reason is non-blank and short enough for the hint line")
    void reasonsAreNonBlankAndShort() {
        for (ProfileFieldCatalog.FieldSpec spec : catalog.fields()) {
            String reason = spec.reason();
            assertNotNull(reason, spec.key() + " reason");
            assertFalse(reason.isBlank(), spec.key() + " reason must not be blank");
            assertTrue(reason.length() <= MAX_REASON_LENGTH,
                    spec.key() + " reason is " + reason.length() + " chars, max "
                            + MAX_REASON_LENGTH + ": " + reason);
        }
    }

    @Test
    @DisplayName("Each reason names its own real downstream consumer")
    void eachReasonNamesItsOwnConsumer() {
        assertTrue(reasonOf("birthDate").contains("年龄"),
                "birthDate feeds the age calculation: " + reasonOf("birthDate"));
        assertTrue(reasonOf("bmi").contains("22.0"),
                "bmi is substituted with 22.0 by the risk engine: " + reasonOf("bmi"));
        assertTrue(reasonOf("familyHistory").contains("糖尿病"),
                "familyHistory drives the diabetes risk score: " + reasonOf("familyHistory"));
        assertTrue(reasonOf("heightCm").contains("BMI") && reasonOf("heightCm").contains("生成"),
                "heightCm is an input that generates the stored BMI: " + reasonOf("heightCm"));
        assertTrue(reasonOf("weightKg").contains("BMI") && reasonOf("weightKg").contains("生成"),
                "weightKg is an input that generates the stored BMI: " + reasonOf("weightKg"));
        assertTrue(reasonOf("allergyHistory").contains("过敏"),
                "allergyHistory steers AI advice away from conflicting items: "
                        + reasonOf("allergyHistory"));
        assertTrue(reasonOf("gender").contains("AI") || reasonOf("gender").contains("健康报告"),
                "gender is read by the AI and report prompts: " + reasonOf("gender"));
        assertTrue(reasonOf("lifestyleHabits").contains("健康评分"),
                "lifestyleHabits feeds the health score: " + reasonOf("lifestyleHabits"));
        assertTrue(reasonOf("bloodType").contains("用血") || reasonOf("bloodType").contains("就医"),
                "bloodType is recorded for care and transfusion contexts: " + reasonOf("bloodType"));
    }

    @Test
    @DisplayName("No reason claims the weight dimension, which reads a realtime metric")
    void noReasonClaimsTheWeightDimension() {
        for (ProfileFieldCatalog.FieldSpec spec : catalog.fields()) {
            assertFalse(spec.reason().contains("体重维度"),
                    spec.key() + " claims a consumer that reads realtime metrics, not the profile: "
                            + spec.reason());
        }
    }

    @Test
    @DisplayName("No reason outside the risk chain mentions the risk engine")
    void onlyRiskChainFieldsMentionRisk() {
        // The fields whose value really reaches RiskAssessmentServiceImpl may say 风险:
        // birthDate feeds age, heightCm/weightKg feed the stored bmi, bmi is substituted
        // directly and familyHistory drives the diabetes score. The four fields below have
        // no path into the risk engine, so claiming it would be a false consumer.
        assertTrue(reasonOf("birthDate").contains("风险"),
                "birthDate drives the risk engine: " + reasonOf("birthDate"));
        for (String key : List.of("gender", "bloodType", "allergyHistory", "lifestyleHabits")) {
            assertFalse(reasonOf(key).contains("风险"),
                    key + " must not claim a risk-engine consumer: " + reasonOf(key));
        }
    }

    @Test
    @DisplayName("Only the substitution fields carry an assumption sentence")
    void assumptionsBelongToSubstitutionFieldsOnly() {
        Set<String> withAssumption = catalog.fields().stream()
                .filter(spec -> spec.assumption() != null)
                .map(ProfileFieldCatalog.FieldSpec::key)
                .collect(Collectors.toSet());

        assertEquals(Set.of("birthDate", "bmi", "familyHistory"), withAssumption);
        assertEquals(ProfileFieldCatalog.FAMILY_HISTORY_ABSENT, assumptionOf("familyHistory"));
    }

    @Test
    @DisplayName("Importance distribution is required two, recommended three, optional four")
    void importanceDistribution() {
        var counts = catalog.fields().stream()
                .collect(Collectors.groupingBy(ProfileFieldCatalog.FieldSpec::importance,
                        Collectors.counting()));

        assertEquals(2L, counts.get("required"));
        assertEquals(3L, counts.get("recommended"));
        assertEquals(4L, counts.get("optional"));
        assertEquals(Set.of("required", "recommended", "optional"), counts.keySet());
    }

    @Test
    @DisplayName("No reason leaks a raw camelCase field identifier")
    void noReasonLeaksRawIdentifiers() {
        for (ProfileFieldCatalog.FieldSpec spec : catalog.fields()) {
            for (String identifier : RAW_IDENTIFIERS) {
                assertFalse(spec.reason().contains(identifier),
                        spec.key() + " reason leaks the internal identifier " + identifier
                                + ": " + spec.reason());
            }
        }
    }

    @Test
    @DisplayName("The user-facing acronym BMI is permitted in copy")
    void userFacingAcronymIsPermitted() {
        assertTrue(reasonOf("bmi").contains("BMI"));
        assertTrue(reasonOf("heightCm").contains("BMI"));
    }

    @Test
    @DisplayName("mentionsDiabetes is the one keyword predicate")
    void mentionsDiabetesHandlesAllInputs() {
        assertFalse(ProfileFieldCatalog.mentionsDiabetes(null));
        assertFalse(ProfileFieldCatalog.mentionsDiabetes(""));
        assertFalse(ProfileFieldCatalog.mentionsDiabetes("父亲有高血压"));
        assertTrue(ProfileFieldCatalog.mentionsDiabetes("父亲有糖尿病"));
    }

    @Test
    @DisplayName("Field labels and assumptions are non-blank where present")
    void labelsAndAssumptionsAreNonBlank() {
        for (ProfileFieldCatalog.FieldSpec spec : catalog.fields()) {
            assertFalse(spec.label().isBlank(), spec.key() + " label");
            assertTrue(spec.weight() > 0, spec.key() + " weight");
            if (spec.assumption() != null) {
                assertFalse(spec.assumption().isBlank(), spec.key() + " assumption");
            }
        }
        assertNull(assumptionOf("gender"));
    }

    private String reasonOf(String key) {
        return catalog.fields().stream()
                .filter(spec -> spec.key().equals(key))
                .map(ProfileFieldCatalog.FieldSpec::reason)
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing catalog key: " + key));
    }

    private String assumptionOf(String key) {
        ProfileFieldCatalog.FieldSpec spec = catalog.fields().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing catalog key: " + key));
        return spec.assumption();
    }
}