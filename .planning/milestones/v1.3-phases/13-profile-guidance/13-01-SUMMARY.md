---
phase: 13-profile-guidance
plan: 01
subsystem: backend-domain
tags: [completeness, profile, catalog, assumptions, tdd]
requires: []
provides:
  - "com.hhs.service.domain.ProfileFieldCatalog (9-entry immutable catalog + FieldSpec record)"
  - "ProfileFieldCatalog#fields()"
  - "ProfileFieldCatalog.NO_PROFILE_ASSUMPTION"
  - "ProfileFieldCatalog.FAMILY_HISTORY_ABSENT"
  - "ProfileFieldCatalog.FAMILY_HISTORY_SUPPLIED_NO_DIABETES"
  - "ProfileFieldCatalog#assumptionsFor(HealthProfile) (static, single substitution-sentence source)"
  - "ProfileFieldCatalog#mentionsDiabetes(String) (static, single keyword predicate)"
  - "com.hhs.service.domain.ProfileCompletenessCalculator#calculate(HealthProfile)"
  - "ProfileCompletenessCalculator#levelFor(int)"
  - "com.hhs.vo.ProfileCompletenessVO (record)"
  - "com.hhs.vo.ProfileFieldStatusVO (record)"
affects:
  - "13-02 (GET /api/prevention/profile/completeness)"
  - "13-03 (ScoreCalculator assumptions)"
  - "13-04 (RiskAssessmentServiceImpl.toVO assumptions + hasFamilyHistory delegation)"
  - "13-09 (risk-page backfill panel renders ProfileCompletenessVO.assumptions)"
tech-stack:
  added: []
  patterns:
    - "Pure domain @Component with no injected dependencies, `new`-instantiable in tests"
    - "Catalog as the single source of weighting, tier metadata and disclosed sentences"
    - "Static assumptionsFor mirroring engine branches rather than joining per-field metadata"
key-files:
  created:
    - hhs-backend/src/main/java/com/hhs/service/domain/ProfileFieldCatalog.java
    - hhs-backend/src/main/java/com/hhs/service/domain/ProfileCompletenessCalculator.java
    - hhs-backend/src/main/java/com/hhs/vo/ProfileCompletenessVO.java
    - hhs-backend/src/main/java/com/hhs/vo/ProfileFieldStatusVO.java
    - hhs-backend/src/test/java/com/hhs/service/domain/ProfileCompletenessCalculatorTest.java
    - hhs-backend/src/test/java/com/hhs/service/domain/ProfileFieldCatalogTest.java
  modified: []
key-decisions:
  - "ProfileScorer left byte-identical: the new calculator is additive and never delegates to the legacy -10/-15/-10/-15 scheme or its 70-neutral empty value"
  - "totalCount derived from fields.size() (the status list size), never from a literal 8 or 9"
  - "bmi.filled requires heightCm AND weightKg AND bmi, immunising completeness against the stale bmi column left by NOT_NULL update strategy"
  - "ProfileFieldCatalog.assumptionsFor(HealthProfile) is static and reads each sentence from its own FieldSpec, so every sentence exists exactly once and 13-04's engine branches can reference the same constants"
  - "A non-blank non-diabetic familyHistory yields FAMILY_HISTORY_SUPPLIED_NO_DIABETES, never 缺少家族病史, because the engine's keyword predicate treats it as familyDiabetes == false"
  - "Risk-mention guard scoped to the four fields with no path into the engine (gender, bloodType, allergyHistory, lifestyleHabits); the plan's mandated copy for heightCm/weightKg/bmi/familyHistory legitimately names 风险评估"
patterns-established:
  - "Pre-create promise and post-create disclosure share one function: assumptionsFor drives both the completeness payload and (from 13-04) RiskAssessmentVO.assumptions"
  - "Per-field consumer assertions in tests, never a shared keyword, so a wrong claim cannot ride on an assertion written for another field"
requirements-completed: [PRF-01, PRF-02, PRF-03]
actuals:
  tokens: 9832
  tasks: 3
  commits: 4
plan_head_before: 6b3c74dec0ecb6fc6afd859bfccb1866fc546134
status: complete
---

# Phase 13 Plan 01: 健康档案完善度权威计算 Summary

Weighted backend completeness source: a 9-field catalog with 60/30/10 tier weights, a calculator returning score / 三等级 / filledCount / totalCount / hasProfile / assumptions / fields[], and one static `assumptionsFor` function that mirrors the risk engine's substitution branches so the pre-create promise and post-create disclosure cannot diverge.

## What Was Built

**`ProfileFieldCatalog`** holds the immutable nine-entry `FieldSpec` list (`key`, `label`, `importance`, `weight`, `reason`, `assumption`) in entity and form order: `gender, birthDate, heightCm, weightKg, bmi, bloodType, allergyHistory, familyHistory, lifestyleHabits`. Weights are 2×30 (required), 3×10 (recommended), 4×2.5 (optional) for exactly 100.0. Each `reason` names a consumer verified in the backend in this session:

| field | reason | verified consumer |
|---|---|---|
| gender | 用于健康报告与 AI 建议中的性别化解读 | `AlertAIAnalysisService:148-149`, `AIChatServiceImpl:88-89`, `HealthReportServiceImpl:372-373` |
| birthDate | 用于计算年龄，影响心血管与糖尿病风险评估 | `RiskAssessmentServiceImpl:118-119, 123, 129` |
| heightCm | 用于生成 BMI，影响风险评估与健康报告 | `HealthProfileServiceImpl:94-98` derives stored `bmi` |
| weightKg | 用于生成 BMI，影响风险评估与健康报告 | same |
| bmi | 用于风险评估的 BMI 代入；缺失按 22.0 估算 | `RiskAssessmentServiceImpl:120, 123, 133` |
| bloodType | 记录在健康报告与 AI 建议中，供就医与用血场景参考 | report / AI prompts |
| allergyHistory | 让 AI 建议避开与过敏史冲突的饮食与用药提醒 | `AIChatServiceImpl`, report prompts |
| familyHistory | 家族糖尿病史直接影响糖尿病风险评估得分 | `RiskAssessmentServiceImpl:121, 124` (+25) |
| lifestyleHabits | 用于健康评分与 AI 建议的针对性 | `ProfileScorer:56`, `AIChatServiceImpl:113` |

Three constants carry the substitution sentences (`NO_PROFILE_ASSUMPTION`, `FAMILY_HISTORY_ABSENT`, `FAMILY_HISTORY_SUPPLIED_NO_DIABETES`). `assumptionsFor(HealthProfile)` branches exactly as `RiskRuleEngine.evaluate` does: null profile → the single generic sentence (matching the fixed 30/25/25 branch at `RiskAssessmentServiceImpl:135-139`); `birthDate == null` → the age-35 sentence; `bmi == null` → the BMI-22.0 sentence; `familyHistory` null or blank → the absent sentence; supplied but keywordless → the supplied-no-diabetes sentence; otherwise empty. `mentionsDiabetes(String)` is the one keyword predicate so 13-04's `hasFamilyHistory` can delegate instead of duplicating `contains("糖尿病")`.

**`ProfileCompletenessCalculator`** produces the VO in one pass: `filled = isFilled(key, profile)`, `earned += weight` when filled, `score = Math.round(earned)` clamped 0..100, `levelFor(score)`, `totalCount = statuses.size()`, `hasProfile = profile != null`, `assumptions = ProfileFieldCatalog.assumptionsFor(profile)`, and per-field `assumption` kept only when the field is unfilled. Text fields are filled when non-null and non-blank; `bmi` requires height, weight and bmi together.

## Tests

`ProfileCompletenessCalculatorTest` (22 methods) and `ProfileFieldCatalogTest` (11 methods) — 33 total, plain JUnit 5, no Spring context and no Mockito.

RED evidence was real, not narrated:
- Before the production types existed, the calculator test failed at compilation on the missing `ProfileCompletenessVO` / `ProfileFieldStatusVO` / `ProfileFieldCatalog` / `ProfileCompletenessCalculator` symbols.
- Two mutation checks confirm the new assertions are load-bearing: setting `STANDARD_TIER_MIN = 61` turned `requiredFieldsAloneReachStandardTier` and `tierBoundaryOwnership` red (`expected: <标准档案> but was: <基础档案>`); rewriting heightCm's reason to 体重维度评分 and gender's to 风险评估 turned the two negative guards red.

## Verification Results

| Check | Result |
|---|---|
| `mvn -f hhs-backend/pom.xml test -Dtest=ProfileCompletenessCalculatorTest,ProfileFieldCatalogTest` | 33 tests, 0 failures |
| `mvn -f hhs-backend/pom.xml test -Dtest="com.hhs.service.domain.*Test"` | 253 tests, 0 failures (includes the pre-existing `WellnessScorerTest` 15 and `ScreeningScorerTest` 4 baselines) |
| `mvn -f hhs-backend/pom.xml checkstyle:check` | 0 violations |
| `grep "profileScorer\|ProfileScorer" ProfileCompletenessCalculator.java` | no matches |
| `grep -c assumptionsFor` catalog / calculator | 1 / 1 |
| `git diff --stat ProfileScorer.java` | empty — byte-identical |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test helper NPE on a null assumption**
- **Found during:** Task 3 first run
- **Issue:** `assumptionOf` used `Optional.map(...)` around a nullable component; `Optional` cannot hold null, so the lookup threw `NullPointerException` in `labelsAndAssumptionsAreNonBlank` instead of returning null.
- **Fix:** resolve the `FieldSpec` first, then return `spec.assumption()` directly.
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/domain/ProfileFieldCatalogTest.java`

**2. [Rule 4 - Architectural / plan contradiction] "only birthDate may mention 风险" contradicts the plan's own mandated copy**
- **Found during:** Task 3 first run (`onlyBirthDateMentionsRisk` failed on heightCm)
- **Issue:** The plan requires two mutually exclusive things. Its `<action>` copy table, its `must_haves` truth, and `13-UI-SPEC.md:995-996` all mandate `用于生成 BMI，影响风险评估与健康报告` for heightCm/weightKg, and the bmi/familyHistory reasons name 风险 as well. But the behavior block demands that "no reason other than birthDate's contains 风险".
- **Resolution:** honored the copy contract (which is anchored to real consumers — height/weight do reach the risk engine via the stored bmi) and scoped the guard to its stated intent: the four fields with no path into the engine (`gender`, `bloodType`, `allergyHistory`, `lifestyleHabits`) must not mention 风险. Test renamed to `onlyRiskChainFieldsMentionRisk`.
- **Impact:** the plan's literal wording for that one assertion is not implemented; its purpose (gender cannot silently claim a risk-engine consumer) is fully enforced. Nothing in the UI-SPEC copy changed.
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/domain/ProfileFieldCatalogTest.java`

### Scope-boundary note
`13-VALIDATION.md` lists `ScoreCalculatorAssumptionsTest`, `RiskAssessmentAssumptionsTest`, `ProfileCompletenessControllerTest` and the frontend specs under Wave 0. Those belong to plans 13-02 through 13-10 and were not touched here; this plan's own success criteria cover only the two domain test classes.

## Known Stubs

None. Every reason, label and assumption string is a final backend constant; no field is left unwired and no placeholder text ships.

## Threat Flags

None. The plan's `<threat_model>` entries T-13-01 (staleness inflation, mitigated by the three-way bmi predicate), T-13-02 (identifier disclosure, mitigated by the camelCase test), T-13-03 (weight/level integrity, mitigated by the sum-to-100 and boundary tests) and T-13-05 (disclosure-vs-engine drift, mitigated by the single `assumptionsFor` source) are all covered by passing tests. No new network, auth, file-access or schema surface was introduced.

## Coverage of Must-Have Truths

| Must-have truth | Verification ref | Status |
|---|---|---|
| Empty profile → 0 / 基础档案 / filledCount 0 / totalCount 9 / 9 entries, never the legacy 70 | `ProfileCompletenessCalculatorTest.absentProfileScoresZero`, `.emptyProfileRowIsPresent` | covered |
| gender + birthDate only → score 60, 标准档案 (PRF-07 non-trigger boundary) | `ProfileCompletenessCalculatorTest.requiredFieldsAloneReachStandardTier` | covered |
| Level boundaries exact: 59→基础, 60→标准, 84→标准, 85→完整 | `ProfileCompletenessCalculatorTest.tierBoundaryOwnership` (0/59/60/84/85/100) | covered |
| `totalCount == fields.size()` with no literal 8 or 9 | `ProfileCompletenessCalculatorTest.totalCountTracksCatalogSize` | covered |
| `bmi.filled` true only when height, weight and bmi are all non-null | `ProfileCompletenessCalculatorTest.staleBmiDoesNotCount` | covered |
| All 9 reasons non-blank and naming their real consumer | `ProfileFieldCatalogTest.reasonsAreNonBlankAndShort`, `.eachReasonNamesItsOwnConsumer` | covered |
| `importance` = required×2 (gender/birthDate), recommended×3, optional×4 | `ProfileFieldCatalogTest.importanceDistribution` | covered |
| `fields[]` order matches entity and form | `ProfileFieldCatalogTest.catalogKeysInOrder`, `ProfileCompletenessCalculatorTest.catalogOrderMatchesEntity` | covered |
| VO carries `hasProfile` and `assumptions` | `ProfileCompletenessCalculatorTest.emptyProfileRowIsPresent`, `.absentProfileHasSingleGenericAssumption` | covered |
| `assumptionsFor` is the single sentence source; one generic sentence for absent profile; only substitutions the engine performs | `ProfileCompletenessCalculatorTest.absentProfileAssumptionsExcludeFieldSentences`, `.sparseProfileDisclosesThreeSentencesInOrder` | covered |
| Family sentence fires only on null/blank; supplied non-diabetic never claims 缺少家族病史 | `ProfileCompletenessCalculatorTest.suppliedNonDiabeticHistoryUsesKeywordlessSentence` | covered |
| Fully populated profile → empty assumptions list | `ProfileCompletenessCalculatorTest.completeProfileDisclosesNothing`, `.fullyPopulatedProfileScoresOneHundred` | covered |
| `ProfileScorer` untouched | `git diff --stat ProfileScorer.java` empty | covered |
| Weight-dimension and risk-engine false claims rejected | `ProfileFieldCatalogTest.noReasonClaimsTheWeightDimension`, `.onlyRiskChainFieldsMentionRisk` | covered |

## Self-Check: PASSED

- FOUND: hhs-backend/src/main/java/com/hhs/service/domain/ProfileFieldCatalog.java
- FOUND: hhs-backend/src/main/java/com/hhs/service/domain/ProfileCompletenessCalculator.java
- FOUND: hhs-backend/src/main/java/com/hhs/vo/ProfileCompletenessVO.java
- FOUND: hhs-backend/src/main/java/com/hhs/vo/ProfileFieldStatusVO.java
- FOUND: hhs-backend/src/test/java/com/hhs/service/domain/ProfileCompletenessCalculatorTest.java
- FOUND: hhs-backend/src/test/java/com/hhs/service/domain/ProfileFieldCatalogTest.java
- FOUND: 2fe2ad5 (feat 13-01), 27f3d4c (test 13-01 boundary matrix), b091e87 (test 13-01 catalog contract), plus this `docs(13-01)` commit