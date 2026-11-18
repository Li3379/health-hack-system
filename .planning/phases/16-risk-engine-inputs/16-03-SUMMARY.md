---
phase: 16-risk-engine-inputs
plan: 03
subsystem: risk-assessment
tags: [risk-engine, cardiovascular, threshold-registry, direction-aware-copy, alias-matching]
status: complete
dependency-graph:
  requires:
    - RiskThresholdRegistry (16-01: 4 rules, consumedKeys, constructor-time canonical guard)
    - HealthMetricMetaRegistry (lipid definitions: precision 2, HDL normalMin 1.0)
    - MetricKeyNormalizer (alias matching used by getLatestMetric)
  provides:
    - RiskThresholdRegistry 12 rules / 8 consumed keys (8 cardiovascular rules added)
    - RiskContributorVO.below(...) lower-bound attribution factory
    - Assessment-threshold wording in both VO factories (0 occurrences of the old term)
    - systolicBloodPressure / diastolicBloodPressure aliases in MetricKeyNormalizer
    - Engine cardiovascular branch rebuilt as 9 independent attributions (no halve)
  affects:
    - 16-04 (classify reads the same 12 rules; coverage panel group predicates)
    - 16-05 (shared formatter replaces the 7 new inline render sites; riskNote gains threshold)
    - Coverage panel riskNote now spans 8 keys instead of 4
tech-stack:
  added: []
  patterns:
    - (metricKey, disease) rule tuple proven by two differing point values on one metric
    - Direction-aware attribution factories that perform no comparison (direction is rule data)
    - Boundary-equality fixtures to prove strict > / strict < (open-interval fixtures cannot)
key-files:
  created: []
  modified:
    - hhs-backend/src/main/java/com/hhs/service/domain/RiskThresholdRegistry.java
    - hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java
    - hhs-backend/src/main/java/com/hhs/vo/RiskContributorVO.java
    - hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java
    - hhs-backend/src/test/java/com/hhs/service/domain/RiskThresholdRegistryTest.java
    - hhs-backend/src/test/java/com/hhs/service/domain/MetricCoverageAssemblerTest.java
    - hhs-backend/src/test/java/com/hhs/service/domain/MetricKeyNormalizerTest.java
    - hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java
decisions:
  - 心血管权重表逐字采用 16-RESEARCH Q2.3（上限 150），使 scaleTo 封顶路径真实可达而非死代码
  - bmi 的心血管规则声明在糖尿病规则之前 —— 面板只描述第一条，顺序即用户可见文案
  - 年龄基线 5 分写在引擎而非注册表：年龄不是 health_metric，没有 canonical key
  - 血脂阈值按 registry precision 渲染（5.20/1.70/1.00/3.40），避免一行内两种小数位
  - below(...) 不做比较：方向由注册表的 Direction 承担，否则会出现第三份阈值比较实现
  - 只加两个血压别名，其余疑似别名一律不做（每次别名扩张都会让一批历史行开始参与评估）
metrics:
  duration: "~30 min"
  completed: "2026-11-18T14:20:00Z"
  tasks: 3
  files_changed: 8
  lines_added: 659
  lines_removed: 64
  commits: 3
  plan_head_before: 9af127ddedfd49767f957a494b43b1d1fbb15a30
actuals:
  tokens: 14480
  tasks: 3
  commits: 3
---

# Phase 16 Plan 03: 心血管独立评估、方向感知措辞与两个缺失别名

## One-liner

Rebuilt cardiovascular risk as a genuine standalone assessment with nine inputs of its own — replacing `diabetesScore/2 + bpScore/2 + bmiBonus` — so opening that card no longer shows "来自糖尿病：年龄 +5" for a patient whose cholesterol, BMI and age were never read at all.

## What Was Built

### Registry: 4 rules → 12 rules, 4 keys → 8 keys

| metricKey | disease | direction | threshold | points |
|---|---|---|---|---|
| bloodGlucose | 糖尿病 | ABOVE | 6.1 | 30 |
| bloodGlucose | 心血管 | ABOVE | 6.1 | 10 |
| systolicBP | 高血压 | ABOVE | 130 | 35 |
| systolicBP | 心血管 | ABOVE | 130 | 30 |
| diastolicBP | 高血压 | ABOVE | 85 | 30 |
| diastolicBP | 心血管 | ABOVE | 85 | 15 |
| bmi | **心血管** | ABOVE | 24.0 | 20 |
| bmi | 糖尿病 | ABOVE | 24.0 | 25 |
| totalCholesterol | 心血管 | ABOVE | 5.2 | 20 |
| triglycerides | 心血管 | ABOVE | 1.7 | 10 |
| hdlCholesterol | 心血管 | **BELOW** | 1.0 | 15 |
| ldlCholesterol | 心血管 | ABOVE | 3.4 | 15 |

Declaration order is hand-picked, not incidental: the coverage panel describes only the **first** rule of each key, so `bmi` lists 心血管 before 糖尿病 (the panel must read `心血管评估：超过 24.0 kg/m² 计入 20 分`), while the other three shared keys keep the long-standing disease first. A javadoc on `registerAllRules` records that reasoning; `RiskThresholdRegistryTest` pins the first-rule disease per key so a future "tidy the sort order" commit fails immediately.

Cardiovascular ceiling is **150** (15+20+30+15+10+20+10+15+15), so the `scaleTo(target=100)` path is reachable in production rather than dead code.

### Engine: 9 independent attributions, `halve()` deleted

Deleted `halve(...)`, `riskStateOf(...)`, `bmiVeryHigh`, `bmiExtra` and the two `for (RiskContributorVO part : …)` halving loops. The new block reads each rule through the existing `requiredRule(...)` helper (which throws on a missing rule instead of silently awarding zero) and emits, in order:

`age` (55/15-5) → `bmi` (24.0/20) → `systolicBP` (130/30) → `diastolicBP` (85/15) → `bloodGlucose` (6.1/10) → `totalCholesterol` (5.20/20) → `triglycerides` (1.70/10) → `hdlCholesterol` (BELOW 1.00/15) → `ldlCholesterol` (3.40/15).

That order is UI-SPEC Contract 3.1's nine-row enumeration and the snapshot's array order; a test asserts it positionally, because a reshuffle would misalign nine frontend rows while every set-based assertion stayed green.

Lipids are read through `getLatestMetric` (hence through `MetricKeyNormalizer.getAliases`), and when absent go to `RiskContributorVO.missing` — state MISSING, 0 points, `此项未计入评分`. No population default is substituted anywhere: `ASSUMED` remains reachable only via the age/BMI profile fallbacks.

The diabetes and hypertension blocks and the no-profile early return were not touched.

### `RiskContributorVO`: direction-aware copy

`below(String, String, String, String, boolean, int)` joins `threshold(...)`; `threshold(...)` keeps its signature. Both now say **评估阈值** instead of **参考值** (4 code occurrences of the new term, 0 of the old). The rationale is recorded in Javadoc: 130/85/24.0 are preventive thresholds below the 139/89/23.9 reference maxima, and the same screen renders the reference *range* column — calling both "reference" contradicts itself. For HDL the direction matters more: a 0.90 reading was previously told it "exceeded" the bound it undershot.

Neither factory compares anything. `Direction` on the rule decides which factory is called; putting a comparison in the VO would have produced a third threshold implementation able to disagree with the other two (`grep -c compareTo` on the VO = 0).

### `MetricKeyNormalizer`: two aliases, nothing more

`systolicBloodPressure` added to the `systolicBP` group and `diastolicBloodPressure` to `diastolicBP`. Pure addition — the `simplify()` path would already normalise `systolicbloodpressure`, but only as lenient passthrough returning the *raw* key, which is exactly why the live row was ignored. Removing the new alias fails three tests, so the fix is load-bearing rather than cosmetic.

## Verification

- Full backend suite: **692 tests, 0 failures, 0 errors** (baseline was 674; +18 net)
- `checkstyle:check`: **0 violations**
- `RiskAssessmentAssumptionsTest` 27 → **41** (+14); `RiskThresholdRegistryTest` 8 → 10; `MetricKeyNormalizerTest` 120 → 122
- `halve` = 0, `riskStateOf` = 0, `BigDecimal.valueOf(28)` = 0, `bmiExtra` = 0, `int cardioRaw` = 1
- `grep -c '心血管", Direction'` on the registry = **8**; `Direction.BELOW` = **1**, on the `hdlCholesterol` line
- Cardiovascular-region cross-references to `diabetesParts|bpParts|diabetesScore|bpScore` = **0**
- `MetricCoverageAssemblerTest`: legacy 4-key expectation gone (0), `consumedKeys()` = 1, reverse list still names `heartRate` / `uricAcid`
- Plan-level: `git diff` on `MetricDefinition.java` empty (D-04), on `pom.xml` empty (T-16-SC), no `sql/` change (D-06)

### Teeth checks (each guard was falsified, then restored)

| Deliberate break | Test that caught it |
|---|---|
| Cardiovascular systolic threshold 130 → 139 (reference max) | `eachRuleCarriesTheMigratedValues` |
| `systolicBloodPressure` alias removed | 3 failures: `testToCanonicalKeyKnownVariants[12]`, `testGetAliases`, `systolicBloodPressureAliasRowStartsScoring` |
| Cardiovascular systolic comparison `> 0` → `>= 0` | `systolicExactlyAtThresholdScoresNothing` (and, after also breaking the hypertension block, 3 boundary tests red) |
| HDL comparison `< 0` → `<= 0` | `hdlExactlyAtThresholdScoresNothing` |
| Cardiovascular BMI comparison `> 0` → `>= 0` | `bmiExactlyAtThresholdScoresNothing` |
| `scaleTo(...)` call removed from the cardiovascular branch | `cappingScalesTheBreakdownToTheCappedScore` |

The two interval fixtures (135, 87) and the four equality fixtures (24.00, 130, 85, 1.00) are complementary by construction: the former prove *which* threshold is used, the latter prove *which* comparison operator is used. Neither can substitute for the other — that is why both exist.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `stubMetrics` truncated decimals before Task 3 could use them**
- **Found during:** Task 2 (the HDL fixture needed `0.90`)
- **Issue:** The helper did `new BigDecimal((Integer) keysAndValues[i + 1])`, so `0.90` would have thrown a `ClassCastException` — or, with an integer literal, silently become `0`.
- **Fix:** One conversion for both shapes: `new BigDecimal(String.valueOf(v))`. No second overload was added, because a coexistence of two conversions keeps the truncating call sites alive. Pulled into Task 2 rather than Task 3 because the Task 2 HDL test is the first caller; all pre-existing integer call sites are bit-for-bit unchanged (verified by re-running the whole class).
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java`
- **Commit:** 777421f (used) / documented again under 9d11523 where the plan assigned it

**2. [Rule 3 - Blocking] Acceptance gate counted my own Javadoc prose**
- **Found during:** Task 1 and Task 2 acceptance checks
- **Issue:** Three `grep -c` gates (`参考值` = 0, `评估阈值` = 4, cardiovascular region cross-references = 0) were tripped by the explanatory prose I had written — the comment quoted `参考值` and the threshold arithmetic quoted `diabetesScore/2 + bpScore/2`. The plan itself warns that comment literals invalidate source-count gates.
- **Fix:** Rewrote the rationale in English / paraphrased the arithmetic (「两种病的分数各取一半」), so the gates now measure code only. No behaviour changed.
- **Files modified:** `RiskContributorVO.java`, `RiskAssessmentServiceImpl.java`
- **Commit:** 777421f

**3. [Rule 1 - Bug] MISSING rows cannot start with the metric label**
- **Found during:** Task 1 test authoring
- **Issue:** The "self-sufficient sentence starts with the metric name" assertion failed on the four absent lipids, because the shared `missing(...)` factory produces `缺少{label}数据，此项未计入评分`. That wording is Phase 15's and is shared by all three diseases — rewriting it to satisfy one new test would have changed diabetes and hypertension copy.
- **Fix:** Scoped the start-with check to non-MISSING rows; MISSING rows assert that the explanation names its label. The prefix wording stays untouched, which the plan requires (「糖尿病与高血压两段一行不动」).
- **Files modified:** `hhs-backend/src/test/java/com/hhs/service/RiskAssessmentAssumptionsTest.java`
- **Commit:** 2411d22

### Notes, not deviations

- The plan's step 4 listed seven new inline render sites (`raw.stripTrailingZeros().toPlainString() + " " + unit`). Only three were actually new: systolic, diastolic and glucose already existed in the diabetes/hypertension blocks, so the file now has 4 such sites for the unit-bearing metrics plus the four lipid rows (added) and the `bmi` row (reuses the pre-rendered `bmiValue`, as instructed). 16-05 will replace all of them with the shared `format(...)`.

## Deferred Issues

- **`classify` in `MetricCoverageAssembler` still uses `normalMin`/`normalMax`.** As of this plan the note spans 8 keys but the HIGH/LOW pill still grades against the reference range, so systolic 130–139 renders a `超过 130 mmHg 计入 30 分` note next to a NORMAL pill. 16-04 owns the fix (already listed in its file scope and marked with a comment at the method).
- **`riskNote` threshold string for the lipids renders `5.20` while the contributor `threshold` field also says `5.20`** — consistent by construction here, but the two are still rendered by two separate expressions (`MetricCoverageAssembler.formatValue` vs the inline string in the engine). 16-05 collapses them into one `format(...)`; the assertion that these two strings are equal does not exist yet, which is the point of that plan.
- **`heartRate` remains outside every assessment** (D-08). Pinned by two tests (`heartRateIsNotConsumed`, plus the reverse list in `onlyEngineInputsCarryRiskNote`), so "just add heart rate" cannot land without editing a test first.

## Known Stubs

None. Every attribution is computed from a registry rule and the user's actual data; the four lipid rows render MISSING because the database genuinely has zero lipid records (CONTEXT 数据现实: 「这是预期的诚实状态，不是缺陷」), not because a placeholder was wired in.

## Threat Flags

None. No new endpoints, request parameters, auth paths, or schema changes; `pom.xml` untouched (T-16-SC did not fire); no `sql/` migration, so no historical `contributors` were rewritten (T-16-15 mitigation is the freeze assertion, which passes).

| Threat | Disposition | Evidence |
|---|---|---|
| T-16-10 (lipids defaulted) | mitigate | `lipidsAbsentAreMissingAndNeverSubstituted`: 4 × MISSING/0/null, zero ASSUMED in the whole list |
| T-16-11 (cap unreachable) | mitigate | `cappingScalesTheBreakdownToTheCappedScore`: hand-computed 150 → 100/100/按比例折算 |
| T-16-12 (lower bound says 超过) | mitigate | `lowerBoundMetricIsWordedAsBelowNotAbove`: 0.90 → CONTRIBUTED 15, contains 低于, excludes 超过 |
| T-16-13 (reference max used) | mitigate | 135 → 35 + 30, 87 → 30 + 15, both CONTRIBUTED |
| T-16-14 (alias expansion) | accept | Exactly two aliases added; no others |
| T-16-28 (non-strict comparison) | mitigate | Four equality fixtures, each red under `>=` / `<=` |
| T-16-29 (panel key drift) | mitigate | 8-key literal **and** `consumedKeys()`-derived assertion in the same test, run in Task 1's `-Dtest` |

## Self-Check: PASSED

- All 8 modified files exist and are committed — FOUND
- Commits `2411d22` (Task 1), `777421f` (Task 2), `9d11523` (Task 3) — FOUND on `chore/remove-unused-modules`
- 692 tests / 0 failures; checkstyle 0 violations — PASSED
- Cardiovascular attribution contains no `来自` / `折半` — PASSED (both factories and the engine)
- Lipid four render MISSING, never ASSUMED — PASSED
- `systolicBloodPressure` alias resolves and scores 35 on 135 — PASSED
