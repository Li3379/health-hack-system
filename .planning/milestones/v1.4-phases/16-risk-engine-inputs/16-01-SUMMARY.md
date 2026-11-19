---
phase: 16-risk-engine-inputs
plan: 01
subsystem: risk-assessment
tags: [risk-engine, threshold-registry, coverage-panel, ssot]
status: complete
dependency-graph:
  requires:
    - HealthMetricMetaRegistry (metric metadata SSOT: unit/precision/displayName)
    - MetricKeyNormalizer (canonical key validation)
    - Phase 15 (contributor attribution and coverage panel)
  provides:
    - RiskThresholdRegistry (4 rules: bloodGlucose, systolicBP, diastolicBP, bmi)
    - riskNote rendered from the same declaration the engine reads
  affects:
    - 16-03 (extends registry to 8 keys + 12 rules)
    - 16-04 (classify reads registry; bmi dual-source)
    - 16-05 (shared formatter; riskNote contains threshold)
tech-stack:
  added: []
  patterns:
    - Component + no-arg constructor + @PostConstruct isEmpty guard (mirrors HealthMetricMetaRegistry)
    - Constructor-time canonical-key self-check (IllegalStateException on misspelt key)
    - (metricKey, disease) rule tuple, not per-metric threshold
key-files:
  created:
    - hhs-backend/src/main/java/com/hhs/service/domain/RiskThresholdRegistry.java
    - hhs-backend/src/test/java/com/hhs/service/domain/RiskThresholdRegistryTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricCoverageAssembler.java
    - hhs-backend/src/test/java/com/hhs/service/domain/MetricCoverageAssemblerTest.java
    - hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java
decisions:
  - Registry stays dependency-free; spelling guarded by isCanonicalKey, "every key is a registered metric" guarded by tests
  - bmi threshold kept at 24.0 (looser than reference max 23.9) to avoid an unauthorised score change; not claimed as "stricter than reference"
  - riskNote describes only the first declared rule (UI-SPEC 5.2 forbids joining diseases)
  - Threshold rendered at MetricDefinition.precision(), reusing formatValue (no second setScale)
  - Unhit BMI branch keeps its unconditional 10-point baseline in the engine (not a threshold reward)
metrics:
  duration: "~32 min"
  completed: "2026-11-18T13:32:00Z"
  tasks: 2
  files_changed: 6
  lines_added: 528
  lines_removed: 45
  commits: 2
  plan_head_before: 19db185bc4ba92175b7d95258a434b1c8ec2ae98
actuals:
  tokens: 7275
  tasks: 2
  commits: 2
---

# Phase 16 Plan 01: 风险阈值竖切 —— 声明 → 引擎判定 → 面板文案

## One-liner

Extracted the four hard-coded risk thresholds into a `RiskThresholdRegistry` that both the rule engine and the coverage panel now read, so the panel can no longer claim a metric matters while the engine stopped reading it.

## What Was Built

**`RiskThresholdRegistry`** (new, `com.hhs.service.domain`) — a dependency-free `@Component` holding four `(metricKey, disease)` rules:

| metricKey | disease | direction | threshold | points |
|---|---|---|---|---|
| bloodGlucose | 糖尿病 | ABOVE | 6.1 | 30 |
| systolicBP | 高血压 | ABOVE | 130 | 35 |
| diastolicBP | 高血压 | ABOVE | 85 | 30 |
| bmi | 糖尿病 | ABOVE | 24.0 | 25 |

Public surface: `rulesFor`, `ruleFor(key, disease)`, `consumedKeys`, `consumes`, `allRules`. Construction runs `MetricKeyNormalizer.isCanonicalKey` on every declared key and throws `IllegalStateException` on a misspelt one — the failure mode that shipped twice in this project (a typo yields an empty rule set, the engine silently awards zero, the panel silently renders no note).

**Engine** (`RiskAssessmentServiceImpl`) — the four `BigDecimal.valueOf(...)` literals are gone; each site reads its rule through a single private `requiredRule(...)` helper that throws when the rule is absent instead of returning zero. `riskThresholds` is the 6th constructor parameter, threaded through `RiskRuleEngine.evaluateDetailed` as its 5th. `BigDecimal.valueOf(22)` (the BMI-missing substitute) deliberately stays — it is a default, not a threshold.

**Coverage panel** (`MetricCoverageAssembler`) — `RISK_CONSUMERS` and `buildRiskConsumers()` deleted. `riskNoteFor(def)` returns `null` for metrics with no rule and otherwise renders the first rule through `formatRiskNote`. The template is the only place that can supply the space between threshold and unit, because the registry's unit literals carry no leading space.

## Verification

- `mvn -o -DskipITs test`: **674 tests, 0 failures, 0 errors**, BUILD SUCCESS
- Targeted: `MetricCoverageAssemblerTest` 11, `RiskThresholdRegistryTest` 8, `RiskAssessmentAssumptionsTest` 27 — all green
- `mvn validate` (checkstyle): **0 violations**, BUILD SUCCESS
- `git diff` for `MetricDefinition.java` and `HealthMetricMetaRegistry.java`: empty (D-04 held)
- `git diff -- hhs-frontend-v2/`: empty (backend-only plan)

### Teeth checks (each guard was falsified, then restored)

| Deliberate break | Test that caught it |
|---|---|
| Template loses the separator space | `expected: <糖尿病评估：超过 6.1 mmol/L 计入 30 分> but was: <糖尿病评估：超过 6.1mmol/L 计入 30 分>` |
| Extra `heartRate` rule added | 4 failures: rule count, exact `consumedKeys()` set, `heartRate` exclusion, declaration order |
| Systolic threshold changed 130 → 139 (reference max) | 2 failures naming 130 vs 139 |

The shape regex and the 24-character cap both pass `6.1mmol/L` (the `.+` swallows it, and one fewer space is shorter) — only the byte-exact `assertEquals` catches it, which is why all three migrated notes are asserted as whole strings.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Test compilation failed on unrelated device-sync classes**
- **Found during:** Task 1 verification
- **Issue:** `HuaweiHealthServiceTest`, `XiaomiHealthServiceTest`, `DeviceSyncOrchestrationServiceTest`, `DeviceSyncIntegrationTest`, `PerformanceTest`, `PlatformAvailabilityServiceTest` referenced classes that no longer existed, so `mvn test` could not compile any test.
- **Fix:** Not a code fix — a concurrent process on the same working tree was removing the device-sync module while this plan ran. Once it settled and deleted those stale tests, compilation succeeded. No files in this plan were touched to work around it.
- **Files modified:** none (out of scope)

**2. [Rule 3 - Blocking] `setScale` count was 4, expected 3**
- **Found during:** Task 2 acceptance check
- **Issue:** My javadoc said the class "re-derives {@code setScale}", which the acceptance gate counts as a fourth occurrence.
- **Fix:** Reworded to "re-deriving the scaling" so the gate measures code, not prose.
- **Commit:** 7374055

## Concurrency Incident (must be read before trusting the commit log)

Another process was writing to this working tree throughout execution, and it committed files this plan had authored:

- `7925113 refactor(backend): remove device sync module` **includes** `RiskThresholdRegistry.java`, `RiskThresholdRegistryTest.java`, `RiskAssessmentServiceImpl.java` and `RiskAssessmentAssumptionsTest.java` — all Task 1 output. The commit message describes none of them.
- `c52c754 refactor(frontend): remove device sync module` landed between my Task 1 and Task 2 work.

Consequences:

1. **Task 1 has no commit of its own.** Its content is on `7925113` verbatim (verified: `class RiskThresholdRegistry` = 1, `riskThresholds` = 5 in the service), but the commit boundary the plan requires does not exist. Task 2 is `7374055`.
2. **The backend baseline moved from 788 to 674 tests.** The 114-test drop is the deleted device-sync suite, not this plan. Phase-gate expectations quoting 788 need re-basing.
3. **Task 1 and Task 2 are not independently revertable.** Reverting `7374055` restores `RISK_CONSUMERS`, but `MetricCoverageAssembler` would then be constructed with two arguments while `7925113`'s engine expects a registry — the intermediate state is not buildable.

I did not rewrite history to fix this: the other writer's commits contain 9,000 lines of unrelated deletions, and rebasing or `update-ref` on a shared branch while another process is active would have destroyed work. The correct repair is a follow-up decision by whoever owns the device-sync removal.

## Deferred Issues

- **`systolicBloodPressure` alias (D-11) still missing.** The database holds one row with `metric_key='systolicBloodPressure'` (user 8, 118 mmHg) that `MetricKeyNormalizer` does not recognise, so both the engine and the panel silently ignore it. Out of this plan's file scope; belongs to 16-03, which already lists `MetricKeyNormalizer.java` in `files_modified`.
- **`classify` still uses `normalMin`/`normalMax`.** Only the note is same-sourced as of this plan; the panel's HIGH/LOW pill and the abnormal count still disagree with the risk threshold for systolic 130–139. Scheduled for 16-04, and marked with a comment at the method so it is not mistaken for an oversight.

## Known Stubs

None. Every risk note is rendered from a live registry rule; there are no placeholder strings, no empty collections flowing to the UI, and no hard-coded fallback text.

## Threat Flags

None. No new endpoints, request parameters, auth paths, or schema changes. `getMetricCoverage` still derives its `userId` from `SecurityUtils.getCurrentUserId()`. Zero new dependencies (`pom.xml` untouched).

## Self-Check: PASSED

- `RiskThresholdRegistry.java` exists — FOUND
- `RiskThresholdRegistryTest.java` exists — FOUND
- Commits `7925113` (carrying Task 1) and `7374055` (Task 2) — FOUND
- 674 tests, 0 failures; checkstyle 0 violations — PASSED
- `MetricDefinition` / `HealthMetricMetaRegistry` diffs empty — PASSED
