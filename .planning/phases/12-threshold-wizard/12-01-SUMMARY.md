---
phase: 12-threshold-wizard
plan: 01
subsystem: alert-threshold
tags: [threshold, alert, classify, boundary-testing, fix01, fix05, java, junit5]

# Dependency graph
requires:
  - phase: pre-12 (existing codebase)
    provides: "ThresholdRangeChecker (broken isWarningRange), UserThreshold entity (four BigDecimal boundaries), inclusive helpers isBelowThreshold/isAboveThreshold"
provides:
  - "ThresholdRangeChecker.Band public enum (CRITICAL_LOW, WARNING_LOW, NORMAL, WARNING_HIGH, CRITICAL_HIGH)"
  - "Private ThresholdRangeChecker#classify(BigDecimal, UserThreshold) — single source of band semantics (D-16)"
  - "Fixed isWarningRange: WARNING now fires for warningHigh <= v < criticalHigh and criticalLow < v <= warningLow (FIX-01)"
  - "ThresholdRangeCheckerTest — first dedicated test class, 30 executed tests (FIX-05, D-18 matrix)"
  - "Complementarity invariant test guaranteeing isNormalRange/isWarningRange/isCriticalRange always agree with getSeverityLevel"
affects: [12-02 (metric-key normalization), 12-04 (wizard validation), 12-05 (ThresholdEvaluator alias-IN + ThresholdEvaluatorTest), alert engine consumers of getSeverityLevel, AlertTemplateService severity branch]

# Actuals (#2632) — pairs with the plan's estimate (estimate.tokens 44000, raw_tokens 22000).
actuals:
  tokens: 5340          # chars/4 over realized diff (21362 diff chars measured via git diff 66a53c6..HEAD)
  tasks: 2
  commits: 2            # MEASURED: git rev-list --count 66a53c6..HEAD
  plan_head_before: 66a53c63bf33484f2f8a14d5c055e14be162e3c3

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single-source classification (RESEARCH Pattern 1): one private classify() deriving every band from the two inclusive helpers only, evaluated critical-side first so later matches implicitly exclude earlier bands — no explicit two-sided inequalities"
    - "Thin-delegate public API: isNormalRange/isWarningRange/isCriticalRange/getSeverityLevel are one-decision delegates over classify(), null contracts preserved at the delegate layer"
    - "Complementarity invariant test (Pitfall 1 canary): public predicates asserted equal to getSeverityLevel string across all five bands + every exact boundary"

key-files:
  created:
    - hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/service/domain/ThresholdRangeChecker.java

key-decisions:
  - "D-16 enforced: all four public range methods delegate to one private classify(); no second comparison of the same boundaries remains in the class"
  - "Band evaluation order is critical-first (CRITICAL_LOW -> WARNING_LOW -> CRITICAL_HIGH -> WARNING_HIGH -> NORMAL) reusing the untouched inclusive helpers, per RESEARCH Pattern 1 — writing explicit two-sided inequalities was explicitly avoided"
  - "Null boundary = unbounded (D-10): a null side never produces a band and never suppresses the opposite side's band; null value/threshold contracts preserved verbatim (isNormalRange true, others false, severity NORMAL)"
  - "Plan action line '89 -> NORMAL' corrected to '89 -> WARNING / 91 -> NORMAL' — the plan's own must-haves and preserved isNormalRange semantics dictate it (see Deviations)"

patterns-established:
  - "Pattern: critical-first classification with exclusion-by-order — reusable for ThresholdRangeValidator (12-04) which must express the same band order"
  - "Pattern: fixture-driven boundary matrix (systolic 80/90/140/160) with parameterized +/-1 sweeps and exact-equality assertions, including a non-integer scale case (glucose 7.0)"

requirements-completed: [FIX-01, FIX-05]

# Coverage metadata (#1602) — one entry per must-have truth from the plan frontmatter.
coverage:
  - id: D1
    description: "Value equal to warningHigh classifies WARNING (not NORMAL, not CRITICAL); value one step below criticalHigh is still WARNING"
    requirement: FIX-01
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#value140AtWarningHighIsWarning"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#value159JustBelowCriticalHighIsWarning"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#highBandBoundaries (6 invocations: 139/140/141/159/160/161)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Value equal to warningLow classifies WARNING on the low side; value one step above criticalLow is still WARNING"
    requirement: FIX-01
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#value90AtWarningLowIsWarning"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#value89BelowWarningLowIsWarning"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#lowBandBoundaries (6 invocations: 79/80/81/89/90/91)"
        status: pass
    human_judgment: false
  - id: D3
    description: "Value at or below criticalLow is CRITICAL and value at or above criticalHigh is CRITICAL, on both sides, including beyond-boundary values"
    requirement: FIX-05
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#value80AtCriticalLowIsCritical"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#value160AtCriticalHighIsCritical"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#lowBoundariesOnlyHighSidesNull (40 -> CRITICAL) and #highBoundariesOnlyLowSidesNull (200 -> CRITICAL)"
        status: pass
    human_judgment: false
  - id: D4
    description: "isNormalRange is exactly the complement of classify()==NORMAL at every boundary value — no value simultaneously normal and warning, none neither"
    requirement: FIX-01
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#publicPredicatesAreComplementaryToSeverityLevel (17 values spanning all five bands + all four exact boundaries)"
        status: pass
      - kind: unit
        ref: "command: mvn -f hhs-backend/pom.xml test -Dtest=ThresholdRangeCheckerTest -> Tests run: 30, Failures: 0, BUILD SUCCESS"
        status: pass
    human_judgment: false
  - id: D5
    description: "A null boundary is unbounded: it never produces a band on its own and never suppresses the opposite side's band"
    requirement: FIX-05
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#highBoundariesOnlyLowSidesNull"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#lowBoundariesOnlyHighSidesNull"
        status: pass
    human_judgment: false
  - id: D6
    description: "All four boundaries null yields NORMAL for every finite value; null value or null threshold keeps pre-existing public contracts (isNormalRange true, isWarningRange/isCriticalRange false, severity NORMAL)"
    requirement: FIX-05
    verification:
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#allBoundariesNullYieldsNormal (0/1/1000)"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#nullValueKeepsPublicContracts"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#nullThresholdKeepsPublicContracts"
        status: pass
    human_judgment: false
  - id: D7
    description: "isWarningRange / isCriticalRange / getSeverityLevel all delegate to the single classify() — no second comparison of the same boundaries remains in the class"
    requirement: FIX-01
    verification:
      - kind: other
        ref: "command: grep -c 'classify(' ThresholdRangeChecker.java -> 6; grep -c 'Band\\.' -> 10; @Component count -> 1; no constructor — helpers isBelowThreshold/isAboveThreshold are now called only from classify()"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java#publicPredicatesAreComplementaryToSeverityLevel (canary that fails if a future edit re-forks the semantics)"
        status: pass
    human_judgment: false

# Metrics
duration: 8min
completed: 2026-11-08
status: complete
---

# Phase 12 Plan 01: ThresholdRangeChecker classify() Refactor Summary

**Single `classify()` now owns all four-band semantics: a configured warning threshold actually produces a WARNING (FIX-01), proven by a 30-test D-18 boundary matrix that previously could not exist because the class had no tests (FIX-05).**

## What Was Built

1. **`ThresholdRangeChecker` rewritten around `classify()`** — a private method returning the new public `Band` enum (`CRITICAL_LOW`, `WARNING_LOW`, `NORMAL`, `WARNING_HIGH`, `CRITICAL_HIGH`). Every band is derived from the two pre-existing inclusive helpers only (`isBelowThreshold` = v <= boundary, `isAboveThreshold` = v >= boundary), evaluated critical-side first so each later match implicitly excludes the earlier band. The four public methods are thin delegates that preserve the exact null contracts. The defect was real and inverted: `isWarningRange` required `v <= criticalLow && v >= warningLow` (low side) and `v <= warningHigh && v >= criticalHigh` (high side) — both bound-swapped, so **no value inside a warning band could ever alert**; the alert engine got `null` and silently dropped it.

2. **`ThresholdRangeCheckerTest`** — the class's first test suite, 30 executed tests:
   - FIX-01 regression assertions (140/159 high side, 90/89 low side -> WARNING where the old code returned NORMAL)
   - Parameterized high/low band sweeps: criticalHigh±1, warningHigh±1, criticalLow±1, warningLow±1
   - Single-sided-null fixtures (null low sides / null high sides behave as unbounded per D-10)
   - All-null fixture, null-value and null-threshold contract tests
   - Exact-equality on a non-integer scale (glucose 7.0 vs warningHigh 7.0 -> WARNING)
   - The complementarity invariant canary across all five bands plus every exact boundary

## TDD Evidence (tracer RED -> GREEN)

- **RED (before refactor):** `Tests run: 11, Failures: 5` — failures precisely on the warning-band values, with the expected signal from 12-VALIDATION.md FIX-01 row: `expected: <WARNING> but was: <NORMAL>` (plus `expected: <true> but was: <false>` on the predicate asserts).
- **GREEN (after refactor):** `Tests run: 11, Failures: 0, BUILD SUCCESS`.
- **Task 2:** `Tests run: 30, Failures: 0, BUILD SUCCESS`.
- Google Checkstyle (`checkstyle:check` bound to the `validate` phase) ran inside both `mvn test` invocations and passed — no `-Dcheckstyle.skip` needed.

## Key Links Verified

- `ThresholdEvaluator.evaluateValue` (~line 92) calls `isNormalRange` then `getSeverityLevel` — both now read the same `classify()`, so the two can never disagree (the pre-existing consumer needed no change, which is the point of D-16).
- `AlertTemplateService` severity branch consumes WARNING/CRITICAL strings from `getSeverityLevel` — as of this fix, WARNING (MEDIUM) alerts genuinely flow downstream; per D-21, real push volume/frequency observation is a UAT acceptance point for this phase.

## Verification

- `mvn -f hhs-backend/pom.xml test -Dtest=ThresholdRangeCheckerTest` — **green, Tests run: 30, Failures: 0** (>= 15 required).
- Plan's second verification line (`-Dtest=ThresholdRangeCheckerTest,ThresholdEvaluatorTest`) is **forward-gated by the plan itself to "once 12-05 lands"** — `ThresholdEvaluatorTest` is Wave 0 output of a later plan and does not exist yet; not runnable at this point by design.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Plan action line "89 -> NORMAL" contradicted the plan's binding semantics**
- **Found during:** Task 1 (while writing the RED test)
- **Issue:** Task 1's action asserted "89 -> NORMAL" for the systolic fixture (criticalLow 80, warningLow 90). The plan's own must-haves dictate the opposite: truth #2 ("a value equal to warningLow is classified WARNING on the low side"), truth #4 ("isNormalRange is exactly the complement of classify()==NORMAL at every boundary value"), and the same action's mandate to preserve `isNormalRange` verbatim — which computes `v > warningLow && v < warningHigh`, so `isNormalRange(89) = false` and 89 must classify WARNING. RESEARCH §Pattern 1's classify order (mandated verbatim by the plan) also returns WARNING_LOW for `89 <= 90`. D-18's "exactly equal to boundary value" class requires the inclusive-low semantics too.
- **Fix:** Implemented per the binding semantics: `89 -> WARNING` (low side) asserted, and the correct NORMAL low-side neighbor `91 -> NORMAL` added. All other Task 1 assertions implemented exactly as written.
- **Files modified:** hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java
- **Commit:** be3c8c4

Otherwise: none — plan executed as written.

## Authentication Gates

None.

## Known Stubs

None — no stubs, no skipped tests, no TODO/FIXME left behind.

## Threat Model Mitigations Delivered

- **T-12-01 (Repudiation, mitigate):** boundary ownership asserted at every exact boundary — 140/90 (WARNING), 80/160 (CRITICAL), plus ±1 sweeps and the glucose 7.0 decimal-equality case.
- **T-12-02 (Tampering, mitigate):** single `classify()` with four delegates + complementarity invariant test prevents future re-forking of the comparison basis (Pitfall 1 canary).
- **T-12-03 (DoS, accept):** classifier is null-safe by construction — explicitly proven by the null-value, null-threshold, and null-boundary tests.
- **T-12-SC:** no package installs occurred.

## Self-Check: PASSED

- FOUND: hhs-backend/src/main/java/com/hhs/service/domain/ThresholdRangeChecker.java (modified, committed)
- FOUND: hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java (created, committed)
- FOUND: commit be3c8c4 — feat(12-01): fix inverted warning band via single classify() (FIX-01)
- FOUND: commit e3836bd — test(12-01): add D-18 full boundary matrix and complementarity invariant (FIX-05)
- Commits measured from ledger: `git rev-list --count 66a53c6..HEAD` = 2
- Verify evidence: Tests run: 30, Failures: 0, BUILD SUCCESS
