---
phase: 17-alert-semantics
verified: 2026-11-19T03:05:00Z
status: passed
score: 5/5 success criteria verified
covered_files:
  - .planning/ROADMAP.md
  - .planning/phases/17-alert-semantics/17-SUMMARY.md
  - hhs-backend/src/main/java/com/hhs/service/domain/ThresholdRangeChecker.java
  - hhs-backend/src/main/java/com/hhs/service/domain/ThresholdEvaluator.java
  - hhs-backend/src/test/java/com/hhs/service/domain/ThresholdEvaluatorTest.java
  - hhs-backend/src/test/java/com/hhs/service/domain/ThresholdRangeCheckerTest.java
covered_digest: "v1:sha256:4e7109ba5b0b0538815041c006a85d80b438ce8c9c301d3280f3a58bac4144ef"
behavior_unverified: 0
overrides_applied: 0
gaps: []
advisory: []
---

# Phase 17: 告警语义收敛 Verification Report

**Phase Goal:** 全站只有一套阈值区间语义 —— `ThresholdRangeChecker.classify` 是唯一判定源，无第二份比较实现。

**Verified:** 2026-11-19T03:05:00Z
**Status:** passed
**Method note:** This phase was executed through the autoresearch loop rather than
`plan-phase` → `execute-phase`. The evidence below is therefore drawn from a
mechanical metric plus mutation-tested guards, not from a PLAN.md task list. That
is a procedural deviation, not an evidence gap — the verification it permits is
in some ways stronger than a normal one, because the metric is adversarial
(fixture-validated against three rewrite-the-comparison cheats).

## The premise this phase had to discard

ROADMAP defined Phase 17 as "eliminate the four independent range comparisons
inside `IntelligentAlertService`". **That class no longer exists.** Commit
`19db185` deleted the entire `com.hhs.service.alert` package:

```
git cat-file -e HEAD:.../service/alert/IntelligentAlertService.java   → absent
git ls-tree -r --name-only HEAD -- .../service/alert/                 → empty
```

The phase goal was salvaged by checking the **success criterion** it served
(ROADMAP v1.4 SC3), which was still violated by three parallel implementations.

## Goal Achievement

| # | Success Criterion | Status | Evidence |
|---|---|---|---|
| 1 | 全库不存在「边界 getter + 关系比较」组合（`ThresholdRangeChecker` 自身原语除外） | ✓ VERIFIED | `.auto/measure.sh` reports `dup_sites=0`, `fork_files=0`. Pattern covers three spellings: `getter … compareTo\|><`, `compareTo(getter)`, `>< … getter` |
| 2 | `ThresholdComparator` 整类删除 | ✓ VERIFIED | `ls .../ThresholdComparator.java` → No such file; `grep -rn "ThresholdComparator" hhs-backend/src` → 0 hits. 81 lines removed; its only live method had exactly one caller |
| 3 | severity、direction、被超越边界值三者同源派生 | ✓ VERIFIED | `ThresholdEvaluator` reads `ThresholdRangeChecker.Band` once and derives all three from it; `findExceededThreshold` switched to `switch (band)`. Regression test `evaluate_crossedWarningBand_directionAgreesWithSeverity` pins it |
| 4 | 合法阈值序下逐点等价；非法阈值序下不再自相矛盾 | ✓ VERIFIED | Differential test over 1500 sampled values on a legal ordering: **0 divergences**. On `warningLow > warningHigh`, the old code emitted severity `WARNING_LOW` with direction `HIGH` (rendered 「警告: X偏低」 pointing at the high boundary); both now derive from one band. Live DB check: `user_threshold` crossed rows = **0**, `alert_rule` crossed rows = **0** |
| 5 | 后端门禁全绿 | ✓ VERIFIED | `mvn -B -o test` → **708 tests, 0 failures, 0 errors**; `checkstyle:check` → 0 violations |

## Gate integrity

The correctness gate was proven to have teeth before it was trusted:

- Injecting a single band inversion into `ThresholdRangeChecker.classify`
  (`CRITICAL_LOW` → `WARNING_LOW`) turned **5 tests red**, exit code 1.
- The metric was proven ungameable: three cheat fixtures — `v.compareTo(t.getWarningHigh()) >= 0`,
  `v > t.getWarningHigh().doubleValue()`, and `t.getCriticalLow().doubleValue() >= v` —
  are all counted; a null-check-only fixture is not. The first metric version
  counted only `compareTo(` and Cheat2 slipped through at 0. That hole was found
  and closed before any iteration was logged.
- The gate initially swallowed its own totals line (`| tail -20` consumed the
  `Tests run:` summary), so zero executed tests could have passed. Fixed by
  asserting `tests_run >= 700` explicitly.

## Iteration ledger

| run | status | dup_sites | change |
|---|---|---|---|
| 1 | noop | 10 | baseline (`ThresholdComparator` 8 + `ThresholdEvaluator` 2) |
| 2 | keep | **0** | delete `ThresholdComparator`; all decisions via `bandOf` |
| 3 | noop | 0 | crossed-band regression guard (metric unchanged → noop per audit rule) |

Net **-60 lines**.

## Deferred / out of scope (deliberate, not missed)

- **`ThresholdRangeValidator`** (4 comparisons) — compares boundaries *to each other*
  (`criticalLow < warningLow < warningHigh < criticalHigh`), not a value to a band.
  Different operation; it does not reference the `UserThreshold` entity.
- **`AiParseServiceImpl` `PANIC_LIMITS`** (2 comparisons) — the four-tier record
  validation ladder (physiological hard limits / panic / reference range), not
  user-specific threshold classification. Measured all eight metrics: every panic
  range sits strictly inside its physiological hard range, so the two sets are
  complementary, not competing.

## Verdict

All five success criteria hold. The one behavioural change (crossed-band alerts no
longer contradict themselves) is a fix, mutation-guarded.

**Status: passed.**