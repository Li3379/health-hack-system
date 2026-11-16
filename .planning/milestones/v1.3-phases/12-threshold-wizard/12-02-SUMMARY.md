---
phase: 12-threshold-wizard
plan: 02
subsystem: backend-thresholds
tags: [FIX-02, FIX-03, alias-in, metric-key-normalization, mybatis-plus, alert-engine]

requires:
  - 12-01 (classify() fix landed — ThresholdEvaluatorTest Case 5 asserts evaluate() returns WARNING AlertVO, which only passes post-FIX-01)
  - MetricKeyNormalizer.toCanonicalKey / getAliases (authoritative alias set, unchanged)
provides:
  - UserThresholdServiceImpl#create canonicalizes metricKey before pre-check and persistence
  - UserThresholdServiceImpl#update canonicalizes before the metric-change comparison
  - UserThresholdServiceImpl#getByUserAndMetricKey alias-IN + selectList + orderByDesc(updatedAt, id)
  - ThresholdEvaluator#getThresholdForUser alias-IN for user_threshold and alert_rule fallback
  - IntelligentAlertService#getThresholdHigh / #getThresholdLow alias-IN (shared findUserThreshold helper)
  - RecoveryNotifier#findRecoveryOpportunity threshold lookup alias-IN
  - TrendPredictor#getThresholdValue alias-IN
  - Test class com.hhs.service.domain.ThresholdEvaluatorTest (6 tests)
affects:
  - 12-03 (SQL repair script: alias-IN code must deploy BEFORE the script runs — Pitfall 4 ordering)
  - 12-04 (@ValidThresholdRange validator: unknown-key lenient passthrough stays in the write path; registry contains-check belongs to the validator)
  - Frontend wizard save flow (THR-08 duplicate handling now receives the new copy 该指标已有阈值，是否改为编辑？)

tech-stack:
  added: []            # zero new dependencies
  patterns:
    - Alias-IN read with deterministic pick: getAliases(key) IN + selectList + orderByDesc(updatedAt, id), first element or null — never selectOne on an aliased query
    - Write-path canonicalization as first statement, before pre-check AND persistence (RESEARCH Pitfall 3)
    - Wrapper-sensitive test stubs: thenAnswer that inspects the captured wrapper's materialized params, so a regression to scalar eq fails the alias-hit tests
    - LambdaQueryWrapper materialization in unit tests via TableInfoHelper.initTableInfo(MapperBuilderAssistant(new MybatisConfiguration(), ""), Entity.class)

key-files:
  created:
    - hhs-backend/src/test/java/com/hhs/service/domain/ThresholdEvaluatorTest.java
  modified:
    - hhs-backend/src/main/java/com/hhs/service/impl/UserThresholdServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/service/domain/ThresholdEvaluator.java
    - hhs-backend/src/main/java/com/hhs/service/alert/IntelligentAlertService.java
    - hhs-backend/src/main/java/com/hhs/service/alert/RecoveryNotifier.java
    - hhs-backend/src/main/java/com/hhs/service/alert/TrendPredictor.java
    - hhs-backend/src/test/java/com/hhs/service/UserThresholdServiceTest.java
    - hhs-backend/src/test/java/com/hhs/service/alert/RecoveryNotifierTest.java
    - hhs-backend/src/test/java/com/hhs/service/alert/TrendPredictorTest.java

key-decisions:
  - D-17 write-path: toCanonicalKey applied once at the top of create() and update(), before the duplicate pre-check / metric-change comparison, so pre-check and insert always observe the same value (closes the unique-index 500 window, T-12-07)
  - D-17 read-path: all five metric-key query sites use getAliases() IN + selectList with orderByDesc(updatedAt, id) (D-14 latest-intent-first); selectOne banned on aliased queries (Pitfall 2, TooManyResultsException)
  - alert_rule fallbacks are alias-IN too (rule seeds may carry legacy keys until FIX-04 runs); deterministic order on AlertRule is orderByDesc(id) because AlertRule has no updatedAt column
  - Duplicate-exists copy changed to 该指标已有阈值，是否改为编辑？ (D-08 / UI-SPEC Copywriting Contract) in both create and update
  - Unknown metric keys stay lenient passthrough (D-17) — no contains-rejection added here; the registry check belongs to the 12-04 validator
  - healthAlertMapper.selectOne sites (RecoveryNotifier:92, IntelligentAlertService:212/347) intentionally untouched — they read health_alert rows, not thresholds; out of plan scope

patterns-established:
  - "Alias-IN + selectList + orderByDesc(updatedAt,id) + first-element pick — template now in all five threshold read sites"
  - "Wrapper-sensitive Mockito stubs (thenAnswer + param-map inspection) for pinning query conditions in wrapper-level unit tests"
  - "TableInfoHelper/MybatisConfiguration/MapperBuilderAssistant idiom for materializing LambdaQueryWrapper in plain Mockito tests"

requirements-completed: [FIX-02, FIX-03]

actuals:
  tokens: 13124          # chars/4 over the realized diff (52498 chars, 9 files, +498/-58); estimate was 62000
  tasks: 3
  commits: 3             # MEASURED: git rev-list --count f0e1c154f94bfdbc827c954a3e20d4ce7754f538..HEAD
plan_head_before: f0e1c154f94bfdbc827c954a3e20d4ce7754f538
metrics:
  duration: ~15 min
  completed: 2026-11-08
status: complete
---

# Phase 12 Plan 02: Threshold Write-Path Canonicalization + Alias-IN Reads Summary

**One-liner:** metricKey aliases canonicalized on both write paths and every threshold/alert-rule read converted to alias-set IN + deterministic selectList pick, so a threshold saved as `glucose` fires for the engine querying `bloodGlucose` — without the `selectOne` TooManyResultsException landmine.

## What Was Built

**Task 1 (tracer, commit 01d8e44) — canonical write path.** `UserThresholdServiceImpl.create()` calls `MetricKeyNormalizer.toCanonicalKey(request.getMetricKey())` as the first statement after the log line and writes it back onto the request, so the duplicate pre-check, `BeanUtils.copyProperties`, and the insert all observe the same canonical value. `update()` gets the identical treatment before the `threshold.getMetricKey().equals(request.getMetricKey())` comparison, so an alias request for the same metric is not treated as a metric change and the persisted value is always canonical. Duplicate-exists copy changed to 「该指标已有阈值，是否改为编辑？」in both methods. `UserThresholdServiceTest` gained three normalization cases: alias-`glucose` captor asserting persisted key `bloodGlucose`, alias-collision business error (never a unique-constraint 500), and canonical-key idempotent passthrough.

**Task 2 (commit 487716f) — alias-IN reads at all five query sites.** `getByUserAndMetricKey`, `ThresholdEvaluator.getThresholdForUser` (user_threshold read + alert_rule fallback), `IntelligentAlertService.getThresholdHigh/getThresholdLow` (via a new shared `findUserThreshold` helper, plus alias-IN `alert_rule` fallbacks), `RecoveryNotifier`'s threshold lookup, and `TrendPredictor.getThresholdValue` all now query `getAliases(metricKey)` with `.eq(userId)` ownership filtering intact (T-12-04) and order by `updatedAt` desc, `id` desc (D-14), taking the first element or null. Zero `userThresholdMapper.selectOne` / `alertRuleMapper.selectOne` remain in main sources. Test stubs in all three affected suites converted from `selectOne` to `selectList`; two new service-level cases pin the two-row alias window (deterministic first element, no exception) and the wrapper's alias-set params.

**Task 3 (commit f26553d) — `ThresholdEvaluatorTest`.** First test class for the evaluator, 6 tests under `@ExtendWith(MockitoExtension.class)` with real `ThresholdComparator`/`ThresholdRangeChecker`/`MetricDisplayFormatter(HealthMetricMetaRegistry)` collaborators. Cases: (1) legacy `glucose` row hit by a `bloodGlucose` query, (2) coexisting alias/canonical rows → first element without `TooManyResultsException`, (3) alias-aware `alert_rule` fallback converting boundary values intact, (4) nothing configured → null, (5) `evaluate(userId, "systolicBP", 145)` against warningHigh 140 / criticalHigh 160 returns a non-null `WARNING`/`MEDIUM` `AlertVO` — the cross-check that 12-01's `classify()` fix and this plan's alias read compose into a real alert (the 12-01→12-02 coupling requirement), and (6) an `ArgumentCaptor<LambdaQueryWrapper>` pinning the `.in()` behaviour and the surviving `userId` param.

## Coverage of Must-Have Truths

| # | Must-have truth | Verification refs | Status |
|---|-----------------|-------------------|--------|
| 1 | POSTing `glucose` persists `metric_key` exactly `bloodGlucose` | `UserThresholdServiceTest` 测试2.1 (`ArgumentCaptor` asserts `bloodGlucose`); suite green (19/0) | verified-green |
| 2 | POSTing `glucose` against an existing `bloodGlucose` row returns 该指标已有阈值，是否改为编辑？, not a 500 | `UserThresholdServiceTest` 测试2.2 (`BusinessException` message asserted, `insert` never called); canonicalization precedes the pre-check (`grep -c toCanonicalKey` = 2 in `UserThresholdServiceImpl`) | verified-green |
| 3 | PUT alias→alias of the same canonical metric is no metric change; no spurious duplicate check | `UserThresholdServiceImpl.update` canonicalizes before the equals comparison (impl inspection; `toCanonicalKey` call sits above the comparison); `update` suite tests 1.4/1.6 green — alias-on-both-sides collapses to equal canonical keys and skips the check | verified-green |
| 4 | Stored `glucose` row found by `bloodGlucose` query and vice versa | `ThresholdEvaluatorTest` 测试1 (wrapper-sensitive stub: only answers when the wrapper's materialized params carry both `bloodGlucose` and `glucose` — a scalar-eq regression fails with "expected not null"); `UserThresholdServiceTest` 测试2.5 (service-level wrapper params span the alias set) | verified-green |
| 5 | No aliased threshold read uses selectOne; every alias query is selectList with deterministic order | `grep -rn "userThresholdMapper.selectOne\|alertRuleMapper.selectOne" hhs-backend/src/main/java/` → zero matches; every converted site carries `orderByDesc` clauses (inspection); no `TooManyResultsException` in any surefire output | verified-green |
| 6 | Canonical+alias rows coexisting → deterministic pick: latest updatedAt, then highest id | Order clauses present at all five sites (inspection; DB-side sort — `orderByDesc(UserThreshold::getUpdatedAt).orderByDesc(UserThreshold::getId)`); first-element selection asserted by `UserThresholdServiceTest` 测试2.4 and `ThresholdEvaluatorTest` 测试2 (`assertSame` on supplied first row, `assertDoesNotThrow`) | verified-green |
| 7 | Every rewritten query still filters by userId | `grep -c "getUserId, userId"` non-zero in all five files (4/2/2/2/1); `ThresholdEvaluatorTest` 测试6 asserts `userId` present in the captured wrapper's param map (T-12-04 / ASVS V4) | verified-green |

## Verification

- `mvn -f hhs-backend/pom.xml test -Dtest=UserThresholdServiceTest,ThresholdEvaluatorTest,RecoveryNotifierTest,TrendPredictorTest,ThresholdRangeCheckerTest` → **Tests run: 77, Failures: 0, Errors: 0, Skipped: 0, BUILD SUCCESS** (includes the plan's classifier-regression check: `ThresholdRangeCheckerTest` 30/0)
- Checkstyle (google_checks, bound to `validate`) passed on every run — all edits are main-source style-checked
- Task-level verifies each ran green before their commits (17/0, 41/0, 6/0)

## Deviations from Plan

**1. [Rule 3 - Blocking] Updated two legacy message assertions in `UserThresholdServiceTest` during Task 1**
- **Found during:** Task 1
- **Issue:** Tests 1.3 and 1.6 asserted `contains("该指标已存在阈值")`, which is not a substring of the mandated D-08 copy 「该指标已有阈值，是否改为编辑？」— Task 1's own verify (green suite) was impossible without touching them.
- **Fix:** Both assertions updated to `contains("该指标已有阈值")` inside Task 1's commit. The plan's "leave existing tests alone in this task" instruction was honored for the `selectOne` stubs it referred to; those were converted in Task 2 as planned.
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/UserThresholdServiceTest.java`
- **Commit:** 01d8e44

**2. [Verified adaptation] Task 3 wrapper assertion shape differs from the plan's wording**
- **Found during:** Task 3
- **Issue:** The plan said to assert "the parameter map contains a collection-valued metric-key entry rather than a scalar". Bytecode inspection of `mybatis-plus-core-3.5.5` `AbstractWrapper.inExpression(Collection)` (sources jar, line 550-556) shows `.in(collection)` registers **one scalar param per element, lazily** (`lambda$inExpression$… → formatParam`), so a collection-valued entry never exists in the param map.
- **Fix:** The assertion pins the same `.in()` behaviour against actual library semantics: the materialized SQL segment contains ` IN `, and the param map spans the full alias set (`bloodGlucose` **and** `glucose` present) plus the `userId` scalar — impossible under a scalar `.eq` rewrite.
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/domain/ThresholdEvaluatorTest.java` (helper `wrapperQueriesAliasSet` + Case 6)
- **Commit:** f26553d

**3. [Implementation note] AlertRule ordering and IntelligentAlertService helper**
- The `alert_rule` wrappers order by `orderByDesc(AlertRule::getId)` — `AlertRule` has no `updatedAt` column, so the `updatedAt`-first order from Pattern 2 is physically inapplicable there; `id`-desc preserves the required determinism.
- `IntelligentAlertService` gained one private `findUserThreshold(userId, metricKey)` helper shared by `getThresholdHigh`/`getThresholdLow` instead of duplicating the alias-IN wrapper twice. Ownership filter retained.
- **Commits:** 487716f

## Auth Gates

None encountered.

## Known Stubs

None. All five read sites are fully wired to `MetricKeyNormalizer.getAliases`; no placeholder paths, no unwired data sources.

## Threat Model Compliance (T-12-04..07)

- **T-12-04 (EoP):** every rewritten wrapper retains `.eq(userId)` — grep-verified per file, plus `ThresholdEvaluatorTest` Case 6 param assertion.
- **T-12-05 (Tampering):** write path canonicalizes before persistence in both `create` and `update` — a caller cannot plant a non-canonical key.
- **T-12-06 (DoS):** all alias reads are `selectList` + deterministic order; forbidden `selectOne` enforced by acceptance grep (zero matches in main sources).
- **T-12-07 (Info Disclosure):** pre-check runs on the canonical key, so alias collisions yield the business error 「该指标已有阈值，是否改为编辑？」rather than a raw constraint message.
- No new security surface outside the plan's `<threat_model>`; no package installs (T-12-SC n/a).

## Self-Check: PASSED

- [x] `hhs-backend/src/test/java/com/hhs/service/domain/ThresholdEvaluatorTest.java` exists (created, committed f26553d)
- [x] All 8 modified/created files committed; `git status` clean for plan scope
- [x] Commits exist: 01d8e44, 487716f, f26553d (`git log` verified)
- [x] Commits measured from ledger: 3 (= `rev-list --count f0e1c154..HEAD`)
- [x] Overall verification green: 77 tests, 0 failures across the five suites
