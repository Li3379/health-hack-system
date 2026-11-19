---
phase: 16-risk-engine-inputs
plan: 05
subsystem: risk-assessment-same-source
tags: [shared-formatter, precision-split, threshold-classification, dual-source-bmi, copy-revision, teeth-verified]
status: complete
dependency-graph:
  requires:
    - 16-01 RiskThresholdRegistry (12 rules, bmi first-listed as 心血管) and RiskThresholdRegistryTest toPlainString guards
    - 16-02 coverageRows fixture (6 rows) and the panel's state vocabulary
    - 16-03 riskNote spanning 8 keys (BMI + four lipids), cardiovascular recompute per D-01
    - 16-04 partition invariant and summary segment 3 wording
  provides:
    - MetricCoverageAssembler.format — one public static scaling formula for both surfaces
    - classify graded by the registry rule (strict > / strict <), reference range only as fallback
    - assemble(HealthProfile, List<HealthMetric>) with bmi derived from the profile alone
    - RiskRuleEngine.METRICS local registry instance with a same-source rationale
    - Risk.vue summary segment 2「{n} 项超出评估阈值」(D-09)
  affects:
    - 16-VALIDATION AC-5 row (A(i) for bmi) is now executable, not aspirational
    - /gsd:verify-work — five gates green in one batch; viewport readings recorded below
tech-stack:
  added: []
  patterns:
    - One formatting formula called from both surfaces; two copies would make the equality assertion a tautology
    - Full-string equality only where both sides are unit-less; numeric prefix where the engine appends a unit
    - Threshold-equality fixture asserted on both surfaces in one case, so a shared `>=` slip cannot pass
    - Teeth check by deliberate break then restore, recorded per assertion
key-files:
  created: []
  modified:
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricCoverageAssembler.java
    - hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java
    - hhs-backend/src/test/java/com/hhs/service/domain/MetricCoverageAssemblerTest.java
    - hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java
    - hhs-frontend-v2/src/views/prevention/Risk.vue
    - hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts
    - hhs-frontend-v2/src/__tests__/views/OcrMigration.spec.ts
decisions:
  - Chose the static-constant registry over threading the bean through evaluateDetailed — same-source beats one fewer instance, and the constructor has no side effects
  - A(i)'s fixture supplies both sources with the same value, because the case must be able to execute before Task 2 lands; source selection is pinned separately by the 31-no-fallback case
  - Kept the reference-range branch of classify for metrics with no rule — inventing a threshold there would be a second source of truth
  - Fixed a pre-existing no-useless-escape lint error in OcrMigration.spec.ts rather than relaxing the gate; it was one character and semantics-preserving
metrics:
  duration: "~95 min"
  completed: "2026-11-18T15:20:00Z"
  tasks: 3
  files_changed: 7
  lines_added: 495
  lines_removed: 44
  commits: 4
  plan_head_before: 668c331fa639f65885f3d5df6e50f7355a458250
actuals:
  tokens: 12806
  tasks: 3
  commits: 4
---

# Phase 16 Plan 05: 同源闭合 —— 一个 formatter、一处分类依据、一个取值源

## One-liner

Closed the last three same-source gaps in one batch: the panel and the dialog now call one `public static` formatter (so `22.86` is `22.9` on both surfaces instead of `22.9` here and `22.86` there), `classify` grades by the same registry rule the engine scores by (systolic 135 is 偏高 on both), and the panel reads `bmi` from the profile the engine actually uses — never from a metric row the engine ignores.

## What Was Built

### One formatter, called from both surfaces

`MetricCoverageAssembler.format(BigDecimal, MetricDefinition)` is now the single scaling formula. `formatValue` is a one-line delegation and all **11** engine render sites call `format` directly. The engine resolved a `MetricDefinition` by adding one static constant:

```java
private static final HealthMetricMetaRegistry METRICS = new HealthMetricMetaRegistry();
```

with a comment recording the trade-off: the constructor only runs `registerAllMetrics()`, `@PostConstruct` is `isEmpty()`-guarded, and the startup recommendation pass copies `precision` unchanged — so a locally built registry yields byte-identical precision while leaving the constructor signature (which the tests depend on) untouched.

Measured baselines and results:

| Site | Before | After |
|---|---|---|
| `stripTrailingZeros` in `RiskAssessmentServiceImpl` | **11** (4 from 16-01 + 7 cardiovascular added by 16-03) | **0** |
| `MetricCoverageAssembler.format` call sites | — | **11** (matches the pre-change `stripTrailingZeros` count exactly) |
| `setScale` in `MetricCoverageAssembler` | 3 | **3** (format 1 + formatRange min/max 2) |
| `public static String format(` declarations | — | **1** |

The plan's "about 11" was exact; its "9 assemble calls" was not (see Deviations).

### `classify` grades by the risk threshold

```java
List<RiskThresholdRegistry.RiskThresholdRule> rules = riskThresholds.rulesFor(def.canonicalKey());
if (!rules.isEmpty()) {
    RiskThresholdRegistry.RiskThresholdRule rule = rules.get(0);
    if (rule.direction() == RiskThresholdRegistry.Direction.BELOW) {
        return value.compareTo(rule.threshold()) < 0 ? STATE_LOW : STATE_NORMAL;
    }
    return value.compareTo(rule.threshold()) > 0 ? STATE_HIGH : STATE_NORMAL;
}
// metrics with no rule keep the reference range
```

Only the first rule is consulted — the same choice `riskNoteFor` makes, so the pill and its own note always describe the same rule. Comparison is strict on both sides; `>=` would show 偏高 to a BMI of exactly 24.00 while the engine awards 0.

### `bmi` reads the profile, and only the profile

`valueFor` holds the entire dual-source decision in one place (`grep -c "profile.getBmi()"` → **1**, `grep -c 'latest.get("bmi")'` → **0**):

| Profile | Metric table | Panel row | Why |
|---|---|---|---|
| 22.86 | no row | `NORMAL` / `22.9` | The engine reads the profile; so does the panel |
| null | `bmi = 31` | `MISSING` / `null` | Falling back would show 31.0/偏高 against the engine's 22.0（估算） |

`getMetricCoverage` reads the profile through the same mapper and the same `userId` parameter — no externally supplied identity (`getProfileByUserId` count 4 → 5, one new call, zero new parameters).

### Summary segment 2 follows the classification basis (D-09)

`{abnormal} 项超出参考范围` → `{abnormal} 项超出评估阈值`, with an inline comment naming D-09 as an explicit Copywriting Contract revision and stating the contradiction it removes. `coverageSummary.abnormal` is byte-for-byte unchanged (`state === 'HIGH' || state === 'LOW'`) — it now measures the risk threshold by construction rather than by rewrite.

### Assertions, and what each one falsifies

| Fixture | Asserts | Failure mode it catches |
|---|---|---|
| bmi 22.86 | panel `value` **equals** contributor `value` equals `"22.9"`, and neither equals `"22.86"` | formatter removed / copied |
| glucose 5.55 | panel `"5.6"`; contributor `startsWith("5.6")`; neither contains `"5.55"` | integer-only fixture would pass both implementations |
| lipids 5.80 / 2.10 / 0.90 / 4.00 | contributor value `startsWith(coverage value)`; contributor ≠ panel; contributor ≠ `"5.8 mmol/L"` | a missed lipid site — the only probe for 16-03's seven new values |
| lipids ×4 | `riskNote.contains(contributor.threshold())` | threshold drift between note and field (wrong decimals, missing separator space) |
| systolic 135 | panel `HIGH` | reading `normalMax` 139 |
| profile 22.86, no metric row | panel not `MISSING`, value `"22.9"` | panel reading the metric table |
| profile null, metric `bmi=31` | `MISSING` **and** `value == null` | silent fallback to the metric row |
| bmi 23.96 | `NORMAL` | reading reference max 23.9 |
| bmi 24.00 + systolic 130 | panel `NORMAL` **and** engine 0 points, same case | `>=` on either side |
| summary | contains 超出评估阈值, not 超出参考范围; number equals HIGH/LOW count (1, then 2 with a derived LOW step row) | stale wording, or a count from another source |

### Teeth checks (each guard deliberately broken, then restored)

| Deliberate break | Red assertions |
|---|---|
| `bmiValue` reverted to `stripTrailingZeros` | `bmiRendersIdenticallyOnBothSurfacesForThisRun` — `22.86` vs `22.9` |
| one lipid site reverted (`totalCholesterol`) | `lipidsShareTheNumericPrefixWithTheirCoverageRows` — `5.8 mmol/L` vs panel `5.80` |
| rule branch disabled (`if (false && ...)`) | 3 red: systolic-135, bmi-23.96, and the 24.00 equality point |
| bmi falls back to the metric row | `bmiRowNeverFallsBackToTheMetricTable` — `HIGH` instead of `MISSING` |
| `>` relaxed to `>=` | `valuesExactlyOnTheThresholdAreNormal` **and** `thresholdEqualityPointAgreesOnBothSurfaces` |
| segment 2 wording reverted | both summary assertions |
| `abnormal` narrowed to HIGH only | the "number tracks panel state" assertion |

Every new assertion was observed red before restore.

## Verification

Five gates, one batch:

| Gate | Result |
|---|---|
| `mvn -f hhs-backend/pom.xml clean verify -DskipITs` | **BUILD SUCCESS — 702 tests, 0 failures, 0 errors** (692 baseline + 10 new) |
| `mvn -f hhs-backend/pom.xml checkstyle:check` | **0 Checkstyle violations**, BUILD SUCCESS |
| `npm --prefix hhs-frontend-v2 run test:run` | **54 files, 623 tests, all pass** (621 baseline + 2 new) |
| `npm --prefix hhs-frontend-v2 run build` | exit 0, `✓ built in 18.39s`, no `error TS` |
| `npm --prefix hhs-frontend-v2 run lint:check` | **0 errors** (218 warnings, unchanged in kind) |

Per-class: `RiskAssessmentAssumptionsTest` 46 (was 26 at phase start, 42 before this plan), `MetricCoverageAssemblerTest` 16 (was 9), `RiskBackfill.spec.ts` 42 (was 40).

`git diff -- sql/ | grep -c "UPDATE.*contributors"` → **0**: no historical snapshot was rewritten (T-16-25).

### Viewport readings (real browser, current build)

Measured with Playwright against a second backend instance (port 8099) built from this working tree, because the long-running 8082 process was serving pre-16-01 classes — its coverage API returned `riskNote: null` and `bmi` MISSING, which would have produced readings for code not under test. Verified the new instance serves `bmi {state: NORMAL, value: "22.9", riskNote: "心血管评估：超过 24.0 kg/m² 计入 20 分"}` before measuring. The backend instance and the temporary dev server were stopped afterwards, and the three assessment rows the measurement created were deleted (user 8 back to zero rows).

| Viewport | `.risk-coverage` scroll/client | overflow | items | max item height | note heights | dialog items | dialog max height | dialog width | dialog overflow |
|---|---|---|---|---|---|---|---|---|---|
| 320 px | 236 / 236 | **no** | 23 | 111.89 px | 36 px | 4 | 36.5 px | 600 px | no |
| 767 px | 683 / 683 | **no** | 23 | 93.89 px | 18 px | 4 | 36.5 px | 600 px | no |
| 1024 px | 700 / 700 | **no** | 23 | 34.50 px | 18 px | 4 | 36.5 px | 600 px | no |
| 1280 px | 956 / 956 | **no** | 23 | 34.50 px | 18 px | 4 | 36.5 px | 600 px | no |

(b) The 1024 px and 1280 px pair was measured separately as required: item height is identical (34.50 px) and `riskNote` stays one line (18 px) at both — the `1fr` shrink from ~528 px to ~272 px does not fold this note set. Folding appears only below 767 px, where the existing media query gives the note its own row (36 px at 320 px, one extra line, no clipping).

(c) Pill text: coverage panel rendered `正常` / `未测`; dialog rendered `已计入` / `正常` / `缺数据`. **No ASCII uppercase enum appeared at any viewport** — the `?? state` fallback did not fire, and both vocabularies stayed on their own surface.

Real summary text at 1024 px: `已测 5 / 23 项 · 8 项纳入评估范围（其中 6 项未测）`. `abnormal` was 0 for this user (bmi 22.86 < 24.0, systolic 118 < 130), so segment 2 was correctly absent — the `v-if` guard is the honest rendering, not a missing assertion. Segment 2's own number was verified by the fixture-based spec instead.

### Interface-500 observation (error backstop)

Intercepted `metric-coverage` and returned HTTP 500 while leaving other calls live:

| Observed | Value |
|---|---|
| Panel section present | yes |
| Summary | `已测 0 / 0 项` |
| Rows rendered | 0 |
| Groups | 「其他已测指标（有数据，但不参与本次风险评估）」 only |
| Empty copy | 「暂无其他已测指标。录入指标后，未被风险评估使用的项目会显示在这里。」 |
| Risk cards alongside | still rendered with real scores |

**Confirmed defect, unchanged by this plan:** a coverage failure is indistinguishable from "you have measured nothing" — and the empty-state copy actively suggests the next step is 录入指标. The risk scores beside it remain correct, so the failure does not contaminate the assessment itself. Already registered as Deferred (UI-SPEC → Phase 18); T-16-27 stays `accept`.

### VALIDATION.md comparison

`16-VALIDATION.md` was not modified. Its gate lists match what ran, with one difference worth recording: the file lists the backend gate as `clean verify -DskipITs` + `checkstyle:check` and the frontend gate as `test:run` + `build`, while this plan's Task 3 `<automated>` also runs `lint:check`. That is an addition, not a conflict — `lint:check` is how the pre-existing escape error surfaced. Its AC-5 row for `bmi` string equality is now backed by an executable test rather than a plan intention.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `assemble` call sites were 11, not the plan's 9**

- **Found during:** Task 2
- **Issue:** The plan and the critical-context note both said `MetricCoverageAssemblerTest` had nine `assembler.assemble(` call sites, becoming `assemble(null, ...)`. Measured: **11**.
- **Fix:** Migrated all 11; the post-change `assemble(null,` count is **12** (11 migrated + the systolic-135 case), i.e. baseline + 1 as the plan intended. The plan's own wording ("assert equal to baseline + 1") is what was followed; only its quoted baseline was stale.
- **Files modified:** `MetricCoverageAssemblerTest.java`
- **Verification:** `grep -c 'assembler.assemble(null,'` → 12; `grep -c 'assembler.assemble('` → 16 (12 null-profile + 4 real-profile cases)
- **Committed in:** `684c7eb`

**2. [Rule 1 - Bug] Pre-existing lint error blocked the Task 3 lint gate**

- **Found during:** Task 3, first `lint:check` run
- **Issue:** `hhs-frontend-v2/src/__tests__/views/OcrMigration.spec.ts:470` — `no-useless-escape` on `\{` inside a `RegExp` template. It arrived in commit `668c331` (a UI commit, not this plan) and made `lint:check` exit non-zero before any of my files were involved.
- **Fix:** Replaced `[^\n]*\{` with `[^\n]*[{]` — a character class, so the brace is unambiguous without Annex B's tolerant parsing, and the escape lint objects to is gone. Semantics identical; the helper's callers see the same pattern.
- **Files modified:** `OcrMigration.spec.ts`
- **Verification:** full suite still 623/623 (the `functionBody` helper feeds 13 source-contract tests); `lint:check` → 0 errors
- **Committed in:** `33469ea`

**3. [Rule 1 - Bug] Stale `target/` produced a phantom compilation failure**

- **Found during:** Task 3, first `clean verify`
- **Issue:** `javac` failed with `NoSuchFileException` for `RiskScorer.class`, `DimensionScoreCalculator.class`, `AssumptionCollector.class` — classes that exist in source. A concurrent IDE build was writing `target/classes` while Maven read it.
- **Fix:** Re-ran the identical command; the clean rebuild succeeded. No source change was involved, and none was made.
- **Verification:** 702/702 green on the retry, and again on the subsequent `verify` run
- **Committed in:** n/a (transient, no change)

---

**Total deviations:** 3 auto-fixed (2 Rule 1, 1 Rule 3)
**Impact on plan:** None on behaviour. #1 is a stale number in the plan text, not a scope change. #2 was required to reach the gate the plan demands. #3 was an environment race.

## Issues Encountered

**Measuring the UI required a second backend.** The long-running process on 8082 was serving classes from before 16-01: its coverage API returned `riskNote: null` and `bmi: MISSING` for a profile whose `bmi` column reads 22.86. Measuring against it would have recorded readings for code not in this plan and — worse — would have silently validated the very defect 16-03 fixed. A second instance was started on 8099 from `target/classes` built by this plan's `clean verify`, its coverage response was checked against expectations first, and measurements were taken through it with the frontend's API calls redirected at the network layer. Both temporary servers were stopped and the three assessment rows created for the measurement were deleted.

`hhs-frontend-v2`'s dev server also needed a free port: 5173 is occupied by an unrelated project (`OAISS CHAIN`), and 5174 is this repo's own instance. The measurement used a dedicated server on 5199.

Running a single spec file directly (`npx vitest run src/__tests__/views/OcrMigration.spec.ts`) fails with `ENOENT … /src/views/data-input/components/OcrInput.vue`, because that spec resolves paths against `process.cwd()`. The full `npm run test:run` invocation sets the workspace root correctly and passes. Not a defect — a note for anyone tempted to run that file alone.

## Known Stubs

None. Every rendered value comes from the live API or the fixture. The lipid rows read `未测` because the database genuinely holds zero lipid records — the honest state, per CONTEXT.

## Threat Flags

None. No new endpoint, request parameter, auth path, or file-access pattern; no schema change; `package.json` and `pom.xml` untouched, so T-16-SC did not fire. All new copy is mustache interpolation; `grep -c "v-html" Risk.vue` remains 0.

| Threat | Disposition | Evidence |
|---|---|---|
| T-16-21 (reading another user's profile) | mitigate | `getMetricCoverage` uses one `userId` for both reads; no new parameter (`getProfileByUserId` count 5 = 4 prior + 1 call). Controller auth unchanged; T-16-21 relies on the existing `@PreAuthorize` test |
| T-16-22 (panel grades by reference range) | mitigate | systolic-135 case is red when the rule branch is disabled |
| T-16-23 (engine keeps a second formatter) | mitigate | `setScale` = 3, `public static format` = 1, `stripTrailingZeros` = 0, plus A(i) equality and lipid prefix probes |
| T-16-24 (panel falls back to the metric row) | mitigate | `latest.get("bmi")` = 0; the 31 fixture is red under fallback |
| T-16-25 (rewriting history to make A(i) pass) | mitigate | `git diff -- sql/` has zero contributors UPDATE; the A(i) comment records the boundary |
| T-16-26 (one extra DB read) | accept | single `selectOne … LIMIT 1`, same mapper and userId as the metrics read |
| T-16-27 (failure rendered as "no metrics") | accept | Measured and recorded above; unchanged by this plan |

## Self-Check: PASSED

- `MetricCoverageAssembler.java`, `RiskAssessmentServiceImpl.java`, both backend test classes, `Risk.vue`, `RiskBackfill.spec.ts`, `OcrMigration.spec.ts` modified — FOUND (495 insertions, 44 deletions across 7 files)
- Commits `8fce648` (Task 1), `684c7eb` (Task 2), `33469ea` (Task 3), `83525d0` (docs) — FOUND, all on `chore/remove-unused-modules`
- Measured from the on-disk ledger `668c331..HEAD`: **4 commits** (3 tasks + 1 metadata), matching `commits: 4`
- `stripTrailingZeros` in the engine = 0 — PASSED
- `setScale` in the assembler = 3; `public static String format(` = 1 — PASSED
- `classify` grades by the registry rule; 135 → HIGH, 23.96 → NORMAL, 24.00 → NORMAL, 130 → NORMAL — PASSED
- bmi asserts full-string equality; glucose and all four lipids assert numeric prefix — PASSED
- Summary says 超出评估阈值 (count 1), 超出参考范围 count 0, `abnormal` expression unchanged — PASSED
- 702/702 backend, 623/623 frontend, checkstyle 0 errors, build exit 0, lint 0 errors — PASSED
- Four viewport readings and the 500 observation recorded above — PASSED
- Every new assertion observed red under a deliberate break, then restored — PASSED
