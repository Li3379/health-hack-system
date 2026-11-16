---
phase: 13-profile-guidance
plan: 04
subsystem: backend-domain
tags: [assumptions, risk-assessment, disclosure, read-recompute, zero-ddl, tdd]
requires:
  - phase: 13-profile-guidance
    provides: "ProfileFieldCatalog#assumptionsFor, NO_PROFILE_ASSUMPTION, FAMILY_HISTORY_ABSENT, FAMILY_HISTORY_SUPPLIED_NO_DIABETES, mentionsDiabetes (13-01)"
  - phase: 13-profile-guidance
    provides: "ProfileCompletenessCalculator#calculate (13-01) and AssumptionCollector (13-03)"
provides:
  - "RiskAssessmentVO#profileCompleteness (Integer, after suggestion)"
  - "RiskAssessmentVO#assumptions (List<String>, last; record now 10 components)"
  - "RiskAssessmentServiceImpl.RiskRuleEngine#evaluate(Long, HealthProfile, List<HealthMetric>, AssumptionCollector)"
  - "RiskAssessmentServiceImpl#disclosedAssumptions(HealthProfile) (static, package-visible)"
  - "RiskAssessmentServiceImpl#toVO(RiskAssessment, int, List<String>)"
  - "com.hhs.service.RiskAssessmentAssumptionsTest (17 tests)"
affects:
  - "13-05 (frontend RiskAssessment interface adds optional profileCompleteness + assumptions)"
  - "13-08 (risk page badge consumes profileCompleteness < 100 && assumptions non-empty)"
  - "13-09 (backfill panel renders completeness.assumptions — the same function this plan consumed)"
tech-stack:
  added: []
  patterns:
    - "Collector at the create-time substitution site plus assumptionsFor on the read path, with parity tests proving both entry points yield one list"
    - "Presence-aware disclosure: null-or-blank vs supplied-but-keywordless are separate branches, never one 'missing' claim"
    - "Read-time recomputation instead of persisted disclosure when the schema cannot carry it (zero DDL)"
key-files:
  created:
    - hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/vo/RiskAssessmentVO.java
    - hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java
key-decisions:
  - "Read paths recompute from the current profile through ProfileFieldCatalog.assumptionsFor; risk_assessment gains no columns and no ALTER script is authored, so the badge means 当前档案下该评估所依赖的默认假设 (RESEARCH open question 2)"
  - "The create path keeps the AssumptionCollector mechanism (D-03) but its sentences are the catalog's constants and its keyword test is ProfileFieldCatalog.mentionsDiabetes, so collector and assumptionsFor are wired to one vocabulary and one predicate"
  - "The two FieldSpec-only sentences (birthDate 35, bmi 22.0) are restated as file constants at the capture site because the catalog exposes them only through assumptionsFor; the parity tests deep-equal the collected list with assumptionsFor for every reachable profile shape, so drift fails a test rather than shipping"
  - "Family history splits into FAMILY_HISTORY_ABSENT (null or blank) and FAMILY_HISTORY_SUPPLIED_NO_DIABETES (supplied, keywordless); a 父亲有高血压 profile is never told the field is missing (plan-check W2)"
  - "The age-35 sentence fires only on the profile != null branch with a null birthDate (legacy rows); the null-profile branch keeps exactly one generic sentence and the fixed 30/25/25"
  - "toVO keeps a one-argument overload delegating with null completeness, but no read path uses it — the plan gate requires zero toVO(r); and zero map(this::toVO) occurrences, which holds"
  - "listByUserId resolves the profile once for the scoped user and short-circuits an empty result set before any profile query, so the history endpoint costs one profile read, not one per row"
patterns-established:
  - "Two entry points, one sentence source: create captures at the branch, read delegates to assumptionsFor, and a parity assertion in each path pins them together"
  - "Additive record extension with an explicit canonical component order so two list-shaped components cannot be silently swapped"
requirements-completed: [PRF-05, PRF-08]
actuals:
  tokens: 7000   # chars/4 over the realized diff (27,967 chars)
  tasks: 3
  commits: 4     # MEASURED: git rev-list --count plan_head_before..HEAD (feat x3 + this docs commit)
plan_head_before: f6b1054d41279a73f91c7825107a7b1913b5adfe
status: complete
---

# Phase 13 Plan 04: 风险评估响应的假设披露 Summary

The risk-assessment response now names the defaults the engine actually substituted: a user with no profile row gets exactly one sentence — `未填写健康档案，风险得分按通用参考值估算` — beside the unchanged fixed 30/25/25, while a stored profile discloses the age-35, BMI-22.0 and family-history sentences from the same `ProfileFieldCatalog.assumptionsFor` function the backfill panel renders. Every read recomputes that list from the current profile, so backfilling refreshes the disclosure immediately and `risk_assessment` gained no columns.

## What Was Built

**`RiskAssessmentVO`** — extended from 8 to 10 components. `Integer profileCompleteness` was inserted immediately after `suggestion` and `List<String> assumptions` appended last, leaving `createTime` second-to-last and the original eight names and order untouched, so every existing JSON consumer keeps working. One Javadoc `@param` per component states the units and the never-null contract.

**`RiskAssessmentServiceImpl`** — the constructor now takes `ProfileCompletenessCalculator` as a fourth argument. `RiskRuleEngine.evaluate(Long, HealthProfile, List<HealthMetric>, AssumptionCollector)` is the collector-aware overload, and the old three-argument signature delegates to it with `AssumptionCollector.noop()` so no caller changes. The substitution branches were restructured:

| Branch | Condition | Recorded sentence |
|---|---|---|
| null profile | `profile == null` | `ProfileFieldCatalog.NO_PROFILE_ASSUMPTION` (one sentence, fixed 30/25/25 unchanged) |
| birth date | `birthDate == null` (legacy row only) | `缺少出生日期，年龄按 35 岁估算` |
| BMI | `bmi == null` | `缺少 BMI，按 22.0 估算` |
| family history | `null` or `isBlank()` | `ProfileFieldCatalog.FAMILY_HISTORY_ABSENT` |
| family history | supplied, no diabetes keyword | `ProfileFieldCatalog.FAMILY_HISTORY_SUPPLIED_NO_DIABETES` |
| family history | mentions 糖尿病 | nothing — the engine substitutes nothing |

The age and BMI ternaries became explicit `if/else` blocks so `collector.add(...)` sits in the very branch that assigns the default (D-03). `hasFamilyHistory(String, String)` was deleted; the engine's `familyDiabetes` boolean now reads `ProfileFieldCatalog.mentionsDiabetes(familyHistory)` directly, so the sentence choice and the score computation share one predicate and no local `contains("糖尿病")` remains in the file.

**`toVO(RiskAssessment, int, List<String>)`** is now the only VO construction point and passes the ten components in the stated order. `disclosedAssumptions(HealthProfile)` is the package-visible static hook the read paths call; its Javadoc states it is a delegation, not a second derivation, so a future edit cannot re-fork it. `getById` resolves the caller's current profile and rebuilds both disclosure fields; `listByUserId` resolves the profile once for the scoped user, short-circuits an empty result set, and maps every row through the same three-argument `toVO`. The `getById` ownership check is unchanged.

**`RiskAssessmentAssumptionsTest`** — 17 Mockito tests over the real service and real rule engine (mappers mocked, nothing between the substitution branch and the response stubbed). Expected strings are read from the catalog (`ProfileFieldCatalog.assumptionsFor`, and `FieldSpec.assumption()` for the two field-only sentences) rather than restated, so a reworded catalog entry fails a test instead of silently shipping two copies.

## Verification Results

| Check | Result |
|---|---|
| `mvn -f hhs-backend/pom.xml test -Dtest=RiskAssessmentAssumptionsTest` | 17 tests, 0 failures |
| Full Surefire suite | 845 tests, 0 failures |
| `mvn -f hhs-backend/pom.xml checkstyle:check` | BUILD SUCCESS (repo-wide warnings are pre-existing, including untouched files) |
| `tr -d '\n' < RiskAssessmentServiceImpl.java \| grep -c 'r.getCreateTime(), assumptions)'` | 1 (single construction site) |
| `grep -c "ProfileFieldCatalog.mentionsDiabetes"` | 2 (≥ 1 required) |
| `grep -c "disclosedAssumptions"` | 3 (declaration + both read paths) |
| `grep -cE "toVO\(r\)\s*; \| map\(this::toVO\)"` | 0 (neither non-recomputing shape survives) |
| `grep -c "hasFamilyHistory"` / `grep -c 'contains("糖尿病")'` | 0 / 0 (delegated to the catalog) |
| `git diff --stat RiskAnalyzer.java` | empty — byte-identical |
| `git diff --name-only plan_head_before..HEAD` | the 3 planned files only — zero DDL, no `schema.sql`, no ALTER |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Test compile failure on a missing `HealthProfile` import**
- **Found during:** Task 2
- **Issue:** The Task 2 behaviour block requires building `HealthProfile` fixtures, but the Task 1 test file had no `com.hhs.entity.HealthProfile` import; `mvn test` failed at `testCompile` with 找不到符号: 类 HealthProfile.
- **Fix:** added the import.
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java`

**2. [Rule 1 - Bug] One-argument `toVO` would have produced a VO with `null` completeness**
- **Found during:** Task 3 wiring
- **Issue:** Task 1's create path needed a `toVO` that already carried the disclosure fields, while the read paths were still on the old signature. Leaving a single one-argument `toVO` that silently dropped to `null` would have made a `toVO(r)` call compile while emitting an incomplete VO.
- **Fix:** the one-argument overload exists only as a delegation for compile compatibility and is never called; both read paths were moved to the three-argument form, and the plan's gate `grep -cE "toVO\(r\)\s*;|map\(this::toVO\)"` reports 0.
- **Files modified:** `hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java`

### Pre-existing environment limitation (not caused by this plan)

`PreventionIntegrationTest` could not be executed: `AbstractIntegrationTest.<clinit>` starts Testcontainers MySQL/Redis unconditionally and Failsafe reports `Could not find a valid Docker environment`, even though `docker info` answers with server 29.8.1. This is the same Testcontainers-to-daemon discovery failure already recorded for 13-02 and already logged as `unrun-verify` in `.planning/WINDOWS.md` (entry 3). Confirmed by direct run: `mvn verify -Dit.test=PreventionIntegrationTest` → `Tests run: 4, Errors: 4` from container initialisation, none from assertions. To be re-run in a Docker-capable environment.

## Known Stubs

None. Every disclosed sentence is a backend constant or a catalog `FieldSpec` value, both disclosure components are populated on every path (`assumptions` is never null; an empty list means no substitution), and no placeholder text ships.

## Threat Flags

No new surface. The plan's `<threat_model>` entries are covered by passing tests: T-13-13 (identifier disclosure in `getById`/`listByUserId`) by `detailKeepsOwnershipCheck`; T-13-14 (assumption accuracy drift) by `everyProfileShapeMatchesCatalogSource` and `nullProfileAssumptionsMatchCatalogSource`; T-13-15 (dead-branch lab claims) by `nullProfileAssumptionNeverClaimsLabData`; T-13-16 (component reorder) by the single canonical constructor order and the unchanged original eight; T-13-16b (supplied-but-non-diabetic family history) by `suppliedNonDiabeticFamilyHistoryDisclosesKeywordlessSentence`.

## Coverage of Must-Have Truths

| Must-have truth | Verification ref | Status |
|---|---|---|
| VO gains `profileCompleteness` after `suggestion` and `assumptions` last; original 8 undisturbed | `RiskAssessmentVO` source; single construction site gate = 1 | covered |
| Null profile → exactly one generic assumption and the fixed 30/25/25 | `nullProfileReturnsFixedScoresAndOneGenericAssumption` | covered |
| birthDate + bmi present, family history lacking 糖尿病 → family sentence only, no age/bmi sentence | `suppliedNonDiabeticFamilyHistoryDisclosesKeywordlessSentence`, `everyProfileShapeMatchesCatalogSource` | covered |
| Supplied but non-diabetic family history yields `…未提及糖尿病…` and NEVER 缺少家族病史 | `suppliedNonDiabeticFamilyHistoryDisclosesKeywordlessSentence` (+ `assertFalse`) | covered |
| bmi null → `缺少 BMI，按 22.0 估算`; legacy birthDate null → `缺少出生日期，年龄按 35 岁估算` | `missingBmiDisclosesReferenceValueAndScoreUsesIt`, `legacyRowWithoutBirthDateDisclosesAgeAndBmiSentences` | covered |
| Read paths recompute from the current profile; zero DDL; badge means 当前档案下的默认假设 | `listRecomputesAssumptionsFromCurrentProfile`, `listDisclosesNothingForCompleteProfile`, `disclosedAssumptions` Javadoc, `git diff --name-only` (3 files, no SQL) | covered |
| `profileCompleteness` computed by `ProfileCompletenessCalculator`; no second weighting | service reads `profileCompletenessCalculator.calculate(profile).score()`; `listDisclosesNothingForCompleteProfile` asserts 100 | covered |
| `assumptions` produced by `ProfileFieldCatalog.assumptionsFor`, identical to the panel's list | `nullProfileAssumptionsMatchCatalogSource`, `everyProfileShapeMatchesCatalogSource` | covered |
| `hasFamilyHistory` delegates to `ProfileFieldCatalog.mentionsDiabetes`; one keyword predicate | helper deleted; `grep -c mentionsDiabetes` = 2; `grep -c hasFamilyHistory` = 0 | covered |
| `assumptions` never null; empty means no substitution | `nullProfileSharesSingleDisclosureOnEveryRow`, `listDisclosesNothingForCompleteProfile`, `blankAllergyHistoryDisclosesNothing` | covered |
| Age-35 sentence only on the legacy `birth_date IS NULL` row path | `legacyRowWithoutBirthDateDisclosesAgeAndBmiSentences`, `nullProfileReturnsFixedScoresAndOneGenericAssumption` (no age sentence) | covered |
| No assumption based on the dead `case "glucose"` / `"血糖"` branches | `nullProfileAssumptionNeverClaimsLabData`; the `getLatestMetric(recentMetrics, "血糖")` line is untouched | covered |
| Blank familyHistory counts as absent | `blankFamilyHistoryDisclosesAbsentSentence` | covered |
| Blank allergyHistory yields no assumption | `blankAllergyHistoryDisclosesNothing` | covered |
| Diabetes-mentioning familyHistory yields no family sentence | `diabeticFamilyHistoryDisclosesNothing` | covered |

## Self-Check: PASSED

- FOUND: hhs-backend/src/main/java/com/hhs/vo/RiskAssessmentVO.java
- FOUND: hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java
- FOUND: hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java
- FOUND: commits 98acab2 (feat 13-04 null-profile disclosure), f9bc1c9 (feat 13-04 substitution branches), 6e8baee (feat 13-04 read-path recompute), plus this `docs(13-04)` commit