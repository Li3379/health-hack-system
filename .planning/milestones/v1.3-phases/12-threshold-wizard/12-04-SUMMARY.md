---
phase: 12-threshold-wizard
plan: 04
subsystem: api
tags: [spring-boot, bean-validation, jakarta-validation, mybatis-plus, etag, metadata-registry]

# Dependency graph
requires:
  - phase: 12-01
    provides: MetricKeyNormalizer canonical key resolution used by the validator and the recommendation provider
  - phase: 11-error-foundation
    provides: fieldErrors projection contract (GlobalExceptionHandler) that the dot-free violation paths feed
provides:
  - "@ValidThresholdRange TYPE-targeted class-level constraint (Chinese default message) attached to UserThresholdRequest — authoritative server-side threshold gate, zero new controller call sites"
  - "ThresholdRangeValidator: three adjacent-pair order checks (null = unbounded, D-10) + metadata hardMin/hardMax checks (D-11), one violation per involved field, dot-free property paths"
  - "RecommendedThreshold record with normalMin/normalMax fallback factory (D-06)"
  - "MetricRecommendedThresholdProvider: enabled alert_rule rows (>=1 non-null boundary) canonicalized into the registry at startup; graceful degradation on DB failure"
  - "MetricDefinition.alertable + MetricDefinition.recommended; HealthMetricMetaRegistry.applyRecommendations(Map); VERSION_TAG v1.2-metric-meta"
  - "Test suites: ThresholdRangeValidatorTest (new), extended UserThresholdRequestValidationTest / MetricMetadataRegistryTest / MetricMetadataControllerTest"
affects: [12-05 (wizard frontend consumes alertable + recommended + dot-free fieldErrors), 12-verify-work]

# Actuals (#2632)
actuals:
  tokens: 13000
  tasks: 3
  commits: 3
  plan_head_before: d3988bec655d218a3dd392d28c5263441302a0ef

# Tech tracking
tech-stack:
  added: []   # zero new dependencies — jakarta validation + MyBatis-Plus already present
  patterns: [class-level constraint validator with Spring injection + no-arg fallback, startup DB-to-registry metadata merge]

key-files:
  created:
    - hhs-backend/src/main/java/com/hhs/validation/ValidThresholdRange.java
    - hhs-backend/src/main/java/com/hhs/validation/ThresholdRangeValidator.java
    - hhs-backend/src/main/java/com/hhs/service/domain/RecommendedThreshold.java
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricRecommendedThresholdProvider.java
    - hhs-backend/src/test/java/com/hhs/validation/ThresholdRangeValidatorTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/dto/UserThresholdRequest.java
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricDefinition.java
    - hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java
    - hhs-backend/src/test/java/com/hhs/validation/UserThresholdRequestValidationTest.java
    - hhs-backend/src/test/java/com/hhs/service/domain/MetricMetadataRegistryTest.java
    - hhs-backend/src/test/java/com/hhs/controller/MetricMetadataControllerTest.java

key-decisions:
  - "One violation PER involved field via two independent buildConstraintViolationWithTemplate calls — never chained addPropertyNode, which SpringValidatorAdapter dot-joins into an unmatchable field name (RESEARCH Pitfall 5, T-12-14)"
  - "Hard limits come from HealthMetricMetaRegistry hardMin/hardMax (D-11), not PANIC_LIMITS; unknown/custom metric keys pass through, consistent with MetricRecordValidator"
  - "alertable defined as an enabled alert_rule row with at least one non-null boundary (D-19): exactly heartRate, systolicBP, diastolicBP, bloodGlucose, bodyTemperature, bmi; weight (all-NULL seed) and bloodOxygen (no seed) excluded"
  - "Registry stays dependency-free: applyRecommendations(Map) is an explicit merge called from the provider @PostConstruct; DB failure at startup is logged and swallowed so public metadata keeps serving (all metrics alertable=false)"
  - "VERSION_TAG bumped v1.1 -> v1.2-metric-meta so the 24h Cache-Control clients revalidate and receive alertable/recommended (T-12-15)"

patterns-established:
  - "Class-level cross-field constraint: @Component ConstraintValidator with @Autowired registry constructor plus a no-arg fallback delegating to new HealthMetricMetaRegistry(), so plain ValidatorFactory tests exercise the exact production constraint path"
  - "Startup metadata enrichment: DB-derived fields merged into an otherwise immutable in-memory registry via an explicit applyRecommendations entry point instead of a registry constructor DB dependency"

requirements-completed: [THR-05, THR-01, THR-02]
# Portion notes: THR-05 backend authoritative interception is COMPLETE here (frontend pure-function mirror is 12-05 scope).
# THR-01/THR-02 backend data source (alertable + recommended in one metadata response) is complete; the frontend picker/prefill
# consumption lands in 12-05. Plan frontmatter also lists FIX-01 — not touched by this plan (ThresholdRangeChecker classify
# landed in the earlier 12-02 plan); no FIX-05 work belonged here (checker coverage is 12-02 scope).

# Coverage metadata (#1602) — one entry per must-have truth from 12-04-PLAN.md
coverage:
  - id: D1
    description: "criticalLow >= warningLow yields exactly two field errors, one on criticalLow and one on warningLow, neither field name containing a dot (HTTP 400 via Phase 11 fieldErrors projection)"
    requirement: THR-05
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/validation/ThresholdRangeValidatorTest.java#criticalLowEqualToWarningLow_YieldsTwoViolationsOnBothFields"
        status: pass
    human_judgment: false
  - id: D2
    description: "warningLow >= warningHigh and warningHigh >= criticalHigh each produce two violations per broken pair, each attached to a single field"
    requirement: THR-05
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/validation/ThresholdRangeValidatorTest.java#warningLowEqualToWarningHigh_YieldsTwoViolationsOnBothFields + #warningHighEqualToCriticalHigh_YieldsTwoViolationsOnBothFields"
        status: pass
    human_judgment: false
  - id: D3
    description: "A pair is skipped when either side is null; a request with only the two high boundaries set validates cleanly (D-10 null = unbounded)"
    requirement: THR-05
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/validation/ThresholdRangeValidatorTest.java#singleNullSide_SkipsThePair + #onlyHighBoundariesSet_ValidatesCleanly"
        status: pass
    human_judgment: false
  - id: D4
    description: "A boundary outside the metric's hardMin/hardMax is rejected server-side even without the UI (D-11 registry limits, not PANIC_LIMITS)"
    requirement: THR-05
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/validation/ThresholdRangeValidatorTest.java#warningHighBeyondHardMax_YieldsViolationOnWarningHigh (heartRate 400 vs hardMax 300)"
        status: pass
    human_judgment: false
  - id: D5
    description: "Class-level constraint attached to UserThresholdRequest so @Valid on the controller triggers it with no new call site"
    requirement: THR-05
    verification:
      - kind: unit
        ref: "grep -c '@ValidThresholdRange' UserThresholdRequest.java == 1; UserThresholdRequestValidationTest exercises the annotated DTO through the default ValidatorFactory (7/7 green); UserThresholdController create/update already carry @Valid"
        status: pass
    human_judgment: false
  - id: D6
    description: "GET /api/metrics/metadata reports alertable=true for exactly heartRate, systolicBP, diastolicBP, bloodGlucose, bodyTemperature, bmi; false for weight, bloodOxygen and all WELLNESS metrics (D-19)"
    requirement: THR-01
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/MetricMetadataRegistryTest.java#testAlertableMetrics_AreExactlyTheSixSeedMetrics (set equality) + #testNonAlertableMetrics; MetricMetadataControllerTest jsonPath heartRate/bloodGlucose true, weight false"
        status: pass
    human_judgment: false
  - id: D7
    description: "Every alertable=true metric carries a non-null recommended record with at least one non-null boundary, and every recommended value lies within hardMin/hardMax (D-06)"
    requirement: THR-02
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/MetricMetadataRegistryTest.java#testRecommendationCompletenessAndBounds + #testFallbackDerivation"
        status: pass
    human_judgment: false
  - id: D8
    description: "Metadata ETag version tag changed to v1.2-metric-meta so 24h-cached clients revalidate and receive the new fields (T-12-15)"
    requirement: THR-01
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/controller/MetricMetadataControllerTest.java ETag containsString('v1.2-metric-meta'); grep v1.1-metric-meta == 0"
        status: pass
    human_judgment: false

# Metrics
duration: 17min
completed: 2026-11-08
status: complete
---

# Phase 12 Plan 04: Server-Side Threshold Validation + alertable/recommended Metadata Summary

**Authoritative `@ValidThresholdRange` bean validation on `UserThresholdRequest` (two dot-free field errors per broken pair, registry hard limits) and metadata now carries `alertable` + backend-computed `recommended` for all 23 metrics under a bumped `v1.2-metric-meta` ETag.**

## Performance

- **Duration:** 17 min
- **Tasks:** 3/3 complete
- **Commits:** 3 (measured from ledger `gsd-plan-head-before-12-04` = d3988be)
- **Estimate vs actual:** plan estimated 66000 tokens (raw 33000, low confidence); realized diff 52087 chars = ~13000 tokens across 11 files (750 insertions, 28 deletions). Estimate ran ~5x high.

## Verification

| Command | Result |
|---------|--------|
| `mvn test -Dtest=ThresholdRangeValidatorTest,UserThresholdRequestValidationTest` | 7+7 tests, 0 failures |
| `mvn test -Dtest=MetricMetadataRegistryTest` | 45 tests, 0 failures |
| `mvn test -Dtest=MetricMetadataControllerTest` | 1 test, 0 failures |
| `mvn test -Dtest=ValidationTest` (baseline) | 38 tests, 0 failures |
| `mvn verify -DskipITs` (full suite + Checkstyle) | **778 tests, 0 failures, BUILD SUCCESS** |

## Key Implementation Points

- **Pitfall 5 honored literally:** each broken pair emits two *independent* `buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation()` chains — `grep 'addPropertyNode.*addPropertyNode'` finds no chained line, so `SpringValidatorAdapter.determineField` can never dot-join a field name.
- **Order + null semantics (D-10):** pairs compared only when both sides non-null; strict `<` required. All three pairs always evaluated (`&=` without short-circuit).
- **Hard limits (D-11):** per-boundary check against the metric definition's `hardMin`/`hardMax` from `HealthMetricMetaRegistry`; message is a static Chinese template naming display name + range (T-12-13, no request-derived interpolation).
- **Metadata merge:** `MetricRecommendedThresholdProvider` filters enabled `alert_rule` rows to those with ≥1 non-null boundary (excludes `weight` per Pitfall 10), canonicalizes keys (legacy `glucose` → `bloodGlucose`), keeps first on duplicates, and pushes into the registry via `applyRecommendations` at `@PostConstruct`; the registry itself stays `new`-able for tests.
- **Pure-Chinese message baseline:** `UserThresholdRequestValidationTest` now asserts 11 distinct messages (7 prior + 3 order + 1 heartRate hard-limit) with the no-Latin-word invariant intact.

## Deviations from Plan

None — plan executed exactly as written. One acceptance-criteria-driven touch-up: `MetricDefinition` javadoc now names `{@link RecommendedThreshold}` so the `grep -c "RecommendedThreshold" >= 2` criterion holds (doc-only change, same commit as Task 2).

## Requirements Notes

- **THR-05:** backend authoritative interception complete (this plan); the frontend pure-function mirror + inline rendering is 12-05 scope.
- **THR-01 / THR-02:** backend data source complete (`alertable` + `recommended` in one metadata response, no frontend whitelist per D-03/D-06); picker and prefill consumption land in 12-05.
- **FIX-01 / FIX-05** (listed in plan frontmatter): not part of this plan's file scope — `ThresholdRangeChecker` classify/coverage landed with 12-02.
- Requirements NOT marked complete in REQUIREMENTS.md: THR-01/THR-02 remain open until 12-05 ships the frontend; left for the orchestrator to reconcile.

## Self-Check: PASSED

- All 5 created files exist on disk; all 6 modified files committed.
- Commits verified in history: 3c19b7f, 084261b, 29441db.
- Full suite 778/778 green including Checkstyle gate.
