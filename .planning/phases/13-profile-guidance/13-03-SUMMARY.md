---
phase: 13-profile-guidance
plan: 03
subsystem: backend-domain
tags: [assumptions, score, disclosure, cache-versioning, overload-pattern]
requires:
  - phase: 13-profile-guidance
    provides: "ProfileCompletenessCalculator, ProfileFieldCatalog (13-01)"
  - phase: 4-health-score
    provides: "ProfileScorer / MetricsScorer / DimensionScoreCalculator / ScoreCalculator chain and their 19 Spring-free tests"
provides:
  - "com.hhs.service.domain.AssumptionCollector#noop(), #add(String), #toList(), #isEmpty()"
  - "ProfileScorer#calculate(Long, AssumptionCollector), ProfileScorer#latestProfile(Long)"
  - "MetricsScorer#calculate(Long, AssumptionCollector)"
  - "DimensionScoreCalculator#calculate(Long, AssumptionCollector)"
  - "ScoreCalculator#calculate(Long, AssumptionCollector)"
  - "HealthScoreVO#profileCompleteness, HealthScoreVO#assumptions"
  - "HealthScoreServiceImpl.CACHE_KEY_PREFIX = health:score:v2:"
  - "com.hhs.service.domain.ScoreCalculatorAssumptionsTest (10 tests)"
affects:
  - "13-07 (Score.vue EstimateBadge reads profileCompleteness != null && < 100)"
  - "13-08 (risk page badge decision relies on the same never-null assumptions contract)"
  - "HealthReportServiceImpl (non-request calculateScore caller benefits from the collector-free legacy signature)"
tech-stack:
  added: []
  patterns:
    - "Overload-preserving refactor: the old signature is one line delegating to the collector-aware overload with AssumptionCollector.noop(), so 19 pre-existing tests pass unedited"
    - "Explicit collector parameter instead of request scope or ThreadLocal, because service/domain is Spring-free and HealthReportServiceImpl calls the chain off the request thread"
    - "collector.add(...) written inside the branch that assigns the default, never reconstructed afterwards (D-03)"
    - "Test asserts real aggregation: mappers mocked, all six scorers constructed by hand, so a broken wiring fails the test rather than a stub"
key-files:
  created:
    - hhs-backend/src/main/java/com/hhs/service/domain/AssumptionCollector.java
    - hhs-backend/src/test/java/com/hhs/service/domain/ScoreCalculatorAssumptionsTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/service/domain/ProfileScorer.java
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricsScorer.java
    - hhs-backend/src/main/java/com/hhs/service/domain/DimensionScoreCalculator.java
    - hhs-backend/src/main/java/com/hhs/service/domain/ScoreCalculator.java
    - hhs-backend/src/main/java/com/hhs/vo/HealthScoreVO.java
    - hhs-backend/src/main/java/com/hhs/service/impl/HealthScoreServiceImpl.java
    - hhs-backend/src/test/java/com/hhs/service/HealthScoreServiceTest.java
key-decisions:
  - "ProfileScorer keeps its neutral-70 semantics for an empty profile; completeness is set separately by ScoreCalculator from ProfileCompletenessCalculator, so no existing user's overall score drops ~17.5 points and health_score_history gets no continuity break"
  - "Substitution sentences are emitted only at the three branches that actually assign a default (no profile, empty metric set, metric set without a bmi key). The four profile deductions describe score loss, not value substitution, and stay silent"
  - "The BMI sentence is decided after the metric loop via a sawBmi flag, and suppressed when the metric set is empty: an empty set is already reported by MetricsScorer, so both scorers cannot double-report the same absence"
  - "ProfileScorer.latestProfile(Long) was extracted so the score path and the completeness path each own one ordered single-row indexed read; no shared HealthProfile instance and no third overload whose only justification would be saving one query"
  - "CACHE_KEY_PREFIX bumped to health:score:v2: because Redis uses activateDefaultTyping: a pre-Phase-13 entry deserializes into the new class with both fields null, and a 5-minute TTL lets the old namespace expire without every reader null-checking for a shape it never wrote"
  - "NO_DATA sets profileCompleteness to null (not 0) and assumptions to List.of() explicitly in code: null means unknown so the frontend hides the badge, whereas 0 would assert a possibly complete profile is empty"
  - "ScoreCalculatorAssumptionsTest mocks mappers and hand-constructs the real scorer chain instead of mocking the scorers, so the assertions cover aggregation rather than stub invocation"
patterns-established:
  - "Additive collector threading: new disclosure capability arrives as an extra parameter on an overload, and every old signature becomes a one-line noop delegation — the pattern any future domain disclosure need should copy"
  - "Dead-code discipline: the case \"glucose\" / \"血糖\" branches stay untouched and no assumption may mention 血糖 or 血压, enforced by a test rather than by review"
  - "Cache-shape changes are shipped with a key-version bump, mirroring the Phase 12 VERSION_TAG precedent"
requirements-completed: [PRF-05]
actuals:
  tokens: 12300  # chars/4 over the realized diff (49,239 chars incl. SUMMARY; 32,264 chars of code alone)
  tasks: 3
  commits: 4     # MEASURED: git rev-list --count plan_head_before..HEAD (feat x3 + this docs commit)
plan_head_before: 3c7cf26a85206a2a87a880a8b0dabfac4f2b1e69
status: complete
---

# Phase 13 Plan 03: 评分响应的假设披露 Summary

The health-score response now names the substitutions it actually makes: a score VO for a user with no profile carries exactly `未填写健康档案，风险得分按通用参考值估算`, an empty metric set yields `缺少健康指标数据，指标维度按中性 70 分计算`, and a metric set without `bmi` yields `缺少 BMI 指标，体重维度按参考值估算` — each recorded on the line that applied the default, aggregated by an explicit `AssumptionCollector`, and returned alongside a weighted `profileCompleteness`, with the Redis namespace versioned to `health:score:v2:` so no pre-Phase-13 cached payload can deserialize into the new shape.

## What Was Built

**`AssumptionCollector`** — a `public final class` holding a `LinkedHashSet<String>`, so entries are de-duplicated and insertion-ordered. `noop()` returns an empty instance for legacy callers, `add(String)` silently drops null and blank input, `toList()` returns `List.copyOf(...)`, and `isEmpty()` reports the empty case. No Spring annotation, no static mutable state, no `ThreadLocal` — the collector is only ever a method parameter.

**`ProfileScorer`** — `calculate(Long userId)` is now a single line, `return calculate(userId, AssumptionCollector.noop());`. The collector-aware overload holds the previous body with one addition: inside `profiles.isEmpty()` it records the generic-reference-value sentence before returning the unchanged neutral `70`. The four deductions (`-10` / `-15` / `-10` / `-15`) and the final `Math.max(0, Math.min(100, score))` are byte-identical. The ordered single-row lookup moved into `latestProfile(Long)`, used by `calculate` and by `ScoreCalculator`.

**`MetricsScorer`** — the same overload split; the `metrics.isEmpty()` branch records the neutral-70 sentence before returning 70. `getMetricPenalty` and every `case` label are untouched, including the dead `case "glucose"`.

**`DimensionScoreCalculator`** — overload split plus a `sawBmi` flag set only in the `case "bmi"` arm. After the loop, a non-empty metric set with no `bmi` key records the weight-dimension sentence; an empty metric set records nothing, because `MetricsScorer` already discloses that absence. The `bmi` arm became a block (the only `case` line the diff touches); `heartRate`, both blood-pressure arms and `glucose` are unchanged.

**`ScoreCalculator`** — new `final ProfileCompletenessCalculator` picked up by `@RequiredArgsConstructor`; `calculate(Long)` delegates with `noop()`; the collector-aware overload passes the collector to all three collecting scorers and then sets `vo.setProfileCompleteness(profileCompletenessCalculator.calculate(profileScorer.latestProfile(userId)).score())` and `vo.setAssumptions(collector == null ? List.of() : collector.toList())`. The 25/35/20/10/10 weighting is unchanged.

**`HealthScoreVO`** — additive `Integer profileCompleteness` (0-100, null only on the NO_DATA path) and `List<String> assumptions` (never null on the calculated path); `isCached` and `message` are untouched.

**`HealthScoreServiceImpl`** — `CACHE_KEY_PREFIX` is `health:score:v2:` with a Javadoc explaining the `activateDefaultTyping` hazard and the Phase 12 `VERSION_TAG` precedent; `CACHE_TTL_MINUTES` stays 5. `createDefaultScore()` now states the contract in code — `setProfileCompleteness(null)` and `setAssumptions(List.of())` — with a comment that null means "unknown" (badge hidden) and 0 would falsely claim an empty profile.

## Verification Results

| Check | Result |
|---|---|
| `mvn -f hhs-backend/pom.xml test -Dtest=ScoreCalculatorAssumptionsTest` (after Task 1) | 4 tests, 0 failures |
| same, after Task 2 | 10 tests, 0 failures |
| `mvn -f hhs-backend/pom.xml test -Dtest=HealthScoreServiceTest,ScoreCalculatorAssumptionsTest,WellnessScorerTest,ScreeningScorerTest` | 43 tests (14 + 10 + 15 + 4), 0 failures, BUILD SUCCESS |
| `mvn -f hhs-backend/pom.xml test` (full Surefire suite) | **828 tests, 0 failures, BUILD SUCCESS** |
| `mvn -f hhs-backend/pom.xml checkstyle:check` | 0 violations |
| `mvn -f hhs-backend/pom.xml test-compile` | clean (no constructor-arity break anywhere in main or test sources) |
| `grep -c "AssumptionCollector.noop()" ProfileScorer.java` | 1 |
| `grep -c "public int calculate(Long userId)" ProfileScorer.java` | 1 |
| `grep -c "latestProfile" ProfileScorer.java` | 4 (javadoc, declaration, use in `calculate`, use from `ScoreCalculator`) |
| `git diff` of `ProfileScorer` deduction lines | empty — `-10` / `-15` / `-10` / `-15` / `return 70` absent from the diff |
| `git diff` of `MetricsScorer` / `DimensionScoreCalculator` `case` lines | only the `bmi` arm changed (`glucose` untouched in both files) |
| `grep -c "health:score:v2:" HealthScoreServiceImpl.java` | 1 |
| `grep -c '"health:score:"' HealthScoreServiceImpl.java` | 0 |
| `git status` on `WellnessScorerTest.java` / `ScreeningScorerTest.java` | clean — zero edits, 19 tests green |
| `git diff --numstat HealthScoreServiceTest.java` | 41 insertions, 0 deletions — additions only |

## Deviations from Plan

**1. [Scope boundary] `ScoreCalculatorAssumptionsTest` ran 4 tests after Task 1, not the plan's 2.** The plan required "at least 2 executed test methods"; the extra two (`legacySignatureKeepsNeutralProfileScore`, `nullCollectorDoesNotThrow`) pin the two invariants the task text states but does not require as tests — the legacy `70` and the null-collector null-guard. No behaviour differs from the plan; the acceptance floor was a minimum.

**2. [Clarification] `ProfileScorer` reads its profile row through a private `latestProfiles(Long)` helper, with the public `latestProfile(Long)` returning its first element.** The plan asked for the existing lookup block to be extracted "into that method". Extracting the one-row query into `latestProfile` would have made `calculate` fetch a single `HealthProfile` and lose the `isEmpty()` shape that distinguishes "no row" from "row present"; keeping the list shape preserves the branch verbatim, so the `isEmpty()` check and `return 70` are unchanged.

**3. [Clarification] `DimensionScoreCalculator` records the BMI sentence after the loop, gated on `!metrics.isEmpty() && !sawBmi`.** The plan said "after the loop, if no metric with key `bmi` was present AND the user had at least one metric". Implemented literally, with the null-guard on the collector applied at the same site. The `bmi` case arm necessarily became a block to set the flag; no threshold, penalty or branch order changed.

## Auth Gates

None. No task required credentials, a login, or an external service.

## Known Stubs

None. Every assumption string is a backend constant emitted from the branch that applies the default; `profileCompleteness` is computed by the 13-01 calculator from real catalog weighting; `assumptions` is `List.of()` only where nothing was substituted. No placeholder copy, no unwired field.

## Threat Flags

None beyond the plan's register.

- **T-13-09** (information disclosure) — mitigated: every sentence is a backend constant carrying no field name, class name, SQL or exception text, and `assumptionsAreNeverNullAndNeverClaimLabData` fails the suite if any sentence mentions 血糖 or 血压.
- **T-13-10** (Redis tampering) — mitigated: `CACHE_KEY_PREFIX` is `health:score:v2:` (grep-verified, old form count 0), so a pre-Phase-13 payload cannot be read into the new class; `testCalculateScore_CacheHitRetainsNewFields` proves a current-shape entry round-trips with both fields intact.
- **T-13-11** (DoS via scope) — mitigated: the collector is a method parameter; `grep` over the touched files finds no request-scope annotation and no `ThreadLocal`, so the off-request `HealthReportServiceImpl` path cannot throw.
- **T-13-12** (repudiation) — mitigated: every `collector.add` sits in the same `if` that returns or assigns the default.
- **T-13-SC** — not applicable: no package install performed.

No new network endpoint, auth path, file-access pattern or schema change.

## Coverage of Must-Have Truths

| Must-have truth | Verification ref | Status |
|---|---|---|
| An existing `calculate(Long userId)` caller observes byte-identical behaviour | `ScoreCalculatorAssumptionsTest.legacySignatureKeepsNeutralProfileScore` (asserts 70); `WellnessScorerTest` 15 + `ScreeningScorerTest` 4 green with those files unmodified | covered |
| Empty profile → `未填写健康档案，风险得分按通用参考值估算` and profile factor stays 70 | `ScoreCalculatorAssumptionsTest.emptyProfileDisclosesGenericReferenceValue`; `legacySignatureKeepsNeutralProfileScore` pins the 70; `ScoreCalculator` sets completeness separately, never the factor | covered |
| No realtime metrics → `缺少健康指标数据，指标维度按中性 70 分计算` | `ScoreCalculatorAssumptionsTest.emptyMetricsDiscloseOnlyTheMetricsSentence` | covered |
| Metric set without `bmi` → `缺少 BMI 指标，体重维度按参考值估算`, captured at the skipped weight penalty | `ScoreCalculatorAssumptionsTest.metricsWithoutBmiDiscloseWeightReferenceValue`; the emission site is the post-loop `!sawBmi` branch inside `DimensionScoreCalculator` | covered |
| `HealthScoreVO` carries `profileCompleteness` (0-100) and `assumptions` (never null) | `emptyProfileDisclosesGenericReferenceValue` asserts `getProfileCompleteness() == 0`; `fullyPopulatedUserDisclosesNothing` asserts 100 and an empty list; `nullCollectorDoesNotThrow` asserts `List.of()` for a null collector | covered |
| NO_DATA path leaves `profileCompleteness` null and assumptions empty | `HealthScoreServiceTest.testCalculateScore_NoDataLeavesCompletenessNull` (`assertNull` + non-null empty list + level `NO_DATA`) | covered |
| Redis prefix is `health:score:v2:` | `grep -c "health:score:v2:"` == 1, old-form count == 0; `testCalculateScore_CacheHitRetainsNewFields` exercises the new-shape read path | covered |
| `assumptions` is de-duplicated and insertion-ordered | `collectorDeduplicatesRepeatedSentences` (one entry, position 0); `collectorIgnoresBlankInput` for the null/blank guard | covered |
| No assumption claims blood-glucose or blood-pressure data | `assumptionsAreNeverNullAndNeverClaimLabData` rejects 血糖/血压 across the produced list; `git diff` confirms both dead `case "glucose"` lines are untouched | covered |

## Self-Check: PASSED

- FOUND: hhs-backend/src/main/java/com/hhs/service/domain/AssumptionCollector.java
- FOUND: hhs-backend/src/main/java/com/hhs/service/domain/ProfileScorer.java
- FOUND: hhs-backend/src/main/java/com/hhs/service/domain/MetricsScorer.java
- FOUND: hhs-backend/src/main/java/com/hhs/service/domain/DimensionScoreCalculator.java
- FOUND: hhs-backend/src/main/java/com/hhs/service/domain/ScoreCalculator.java
- FOUND: hhs-backend/src/main/java/com/hhs/vo/HealthScoreVO.java
- FOUND: hhs-backend/src/main/java/com/hhs/service/impl/HealthScoreServiceImpl.java
- FOUND: hhs-backend/src/test/java/com/hhs/service/domain/ScoreCalculatorAssumptionsTest.java
- FOUND: hhs-backend/src/test/java/com/hhs/service/HealthScoreServiceTest.java
- FOUND: a58dc38 (feat 13-03 collector + ProfileScorer + ScoreCalculator + VO + test), 7384c19 (feat 13-03 metrics + weight dimension), 5e58b7f (feat 13-03 cache prefix + NO_DATA contract)