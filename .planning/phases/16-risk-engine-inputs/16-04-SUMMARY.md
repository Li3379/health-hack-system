---
phase: 16-risk-engine-inputs
plan: 04
subsystem: risk-assessment-presentation
tags: [coverage-panel, partition-invariants, copy-writing, two-vocabularies, test-teeth]
status: complete
dependency-graph:
  requires:
    - 16-02 loose `>=` assertion and the coverageRows fixture (6 rows, 2 of them note+MISSING)
    - 16-03 riskNote spanning 8 keys (BMI + four lipids added)
    - 16-UI-SPEC Contract 1.2 (mutually exclusive grouping) and Contract 6.1 (two word tables)
  provides:
    - missingMetrics predicate now excludes rows carrying a riskNote
    - usedMissing computed derived from usedMetrics, not from the global coverage list
    - Summary segment 3 reading「{n} 项纳入评估范围」with conditional「（其中 N 项未测）」
    - Empty-state copy for 其他已测指标 that names the next step
    - 6 new frontend assertions; 16-02's loose `>=` tightened to strict equality
  affects:
    - 16-05 (summary segment 2「超出参考范围」→「超出评估阈值」lands with the classify change)
tech-stack:
  added: []
  patterns:
    - Partition invariant asserted as a sum, never as per-item non-duplication
    - Region-scoped negative assertion to stop a sum from being satisfied by emptying groups
key-files:
  created: []
  modified:
    - hhs-frontend-v2/src/views/prevention/Risk.vue
    - hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts
decisions:
  - Fixed missingMetrics, not usedMetrics — the lipids genuinely participate (D-02); removing them from 「参与」 would be the lie
  - usedMissing derives from usedMetrics so it cannot count absent metrics that are outside the assessment
  - Summary segment 2 left untouched by design; an inline comment points at 16-05 rather than leaving a silent skip
  - Rationale prose written without quoting the counted literals, so the exact-count gates measure code only
metrics:
  duration: "~35 min"
  completed: "2026-11-18T14:40:00Z"
  tasks: 2
  files_changed: 2
  lines_added: 192
  lines_removed: 8
  commits: 2
  plan_head_before: 47b59c3374fca2be5c91f25bd8e95b060d090bbb
actuals:
  tokens: 3049
  tasks: 2
  commits: 2
---

# Phase 16 Plan 04: 分组互斥与摘要措辞

## One-liner

Made the coverage panel's three groups a real partition — one predicate condition closing the note+MISSING overlap that 16-03 turned into five duplicated rows for every user — and reworded the summary from "used" to "in scope" so 8 metrics in range over 3 with values no longer reads as "we scored 8".

## What Was Built

### The partition defect, and why `missingMetrics` was the one to change

As of 16-03, `riskNote` spans 8 keys, and the four lipids plus BMI are `MISSING` for every user in the current database. With the old predicates:

| Group | Predicate | Rows it wrongly claimed |
|---|---|---|
| 参与风险评估的指标 | `!!m.riskNote` (ignores `state`) | the note+MISSING rows — correctly, they do participate |
| 其他已测指标 | `!m.riskNote && m.state !== 'MISSING'` | none |
| 尚未采集 | `m.state === 'MISSING'` (ignores `riskNote`) | **the same note+MISSING rows a second time** |

A metric with both a `riskNote` and `state === 'MISSING'` landed in two groups. On the 6-row fixture that produced **8** `.risk-coverage__item` elements; the group heading's count and `coverageSummary.measured` were inflated by the same two.

Only one condition was added:

```ts
const missingMetrics = computed(() =>
  coverage.value.filter(m => m.state === 'MISSING' && !m.riskNote)
)
```

`usedMetrics` and `otherMeasured` are byte-for-byte unchanged, and the reasoning is in a comment above the computed: the lipids genuinely participate in the cardiovascular assessment (D-02), so moving them out of 「参与」 would be the lie — they already render there honestly with a `未测` pill. `missingMetrics` now means only "the engine does not read it, and you do not have it".

### `usedMissing` derives from `usedMetrics`, not from `coverage`

```ts
const usedMissing = computed(
  () => usedMetrics.value.filter(m => m.state === 'MISSING').length
)
```

Filtering the global list would have counted `uricAcid` — absent but outside the assessment — and printed "9 项未测" against 8 in scope. The source of the count is the whole point of the assertion.

### Summary wording: in scope, not used

| Slot | Before | After |
|---|---|---|
| Segment 1 | `已测 {measured} / {total} 项` | unchanged |
| Segment 2 | `{abnormal} 项超出参考范围` | **unchanged by design** — comment points at 16-05 |
| Segment 3 | `· {used} 项参与风险评估` | `· {used} 项纳入评估范围` |
| Segment 3 (new) | — | `（其中 {usedMissing} 项未测）`, only when `usedMissing > 0` |

The bracketed clause uses `未测`, the S1 word — the same word the state column renders for those very rows. `缺数据` is S2's attribution word ("this did not count"); in S1 it would read as "scoring is missing 3 items", which is a different claim. The clause is `v-if`-guarded, so a zero count renders nothing at all rather than "（其中 0 项未测）".

### Segment 2 deliberately left alone

`{abnormal} 项超出参考范围` stays, with a comment stating that its wording changes together with the classification source in 16-05. Right now `abnormal` counts by reference range; relabelling it "评估阈值" while `classify` still grades against `normalMax` would make the sentence claim a stricter basis than the number uses — and between systolic 130 and 139 those two bases disagree. Not fixing it is correct; leaving it unremarked is not, hence the comment.

### Empty state gives a next step

`暂无其他已测指标` → `暂无其他已测指标。录入指标后，未被风险评估使用的项目会显示在这里。`

No button, no link: metric entry is already reachable from existing navigation, and this panel adds no jump target.

### Rationale prose rewritten to keep the count gates honest

The first pass of my comments quoted `缺数据` and `未测` verbatim, which pushed those two exact-count gates to 3 and 8 against the plan's 2 and 5. That is the same trap 16-03 recorded (`RiskContributorVO.java` prose tripping its own `参考值` gate). The comments now paraphrase instead of quoting, so the gates measure code:

| Gate | Required | Measured |
|---|---|---|
| `grep -c "缺数据"` Risk.vue | 2 | **2** (line 844 `stateLabel` map, line 1442 CSS comment) |
| `grep -c "未测"` Risk.vue | 5 | **5** |
| `grep -c "项纳入评估范围"` Risk.vue | 1 | **1** |
| `grep -c "项参与风险评估"` Risk.vue | 0 | **0** |
| `usedMissing > 0` | 1 | **1** |
| `m.state === 'MISSING' && !m.riskNote` | 1 | **1** |
| `usedMetrics.value.filter` | 1 | **1** |
| `toBeGreaterThanOrEqual(coverageRows.length)` in spec | 0 | **0** |
| `var(--border-1)` / `var(--text-3)` | 0 / 2 | **0 / 2** |

## Verification

- `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/RiskBackfill.spec.ts`: **40 tests, 0 failed** (34 baseline + 6 new)
- Full suite: **54 files, 621 tests, all pass** (615 baseline + 6)
- `npm --prefix hhs-frontend-v2 run build`: exit 0, `✓ built in 35.64s`, no `error TS`
- `git diff -- hhs-backend/` → **empty** (pure frontend plan)
- Pre-existing 34 assertions: zero `expect(...)` lines deleted; the one changed assertion was 16-02's `>=`, tightened as instructed

### Teeth checks (each guard falsified, then restored)

| Deliberate break | Test that caught it |
|---|---|
| `missingMetrics` predicate reverted to `m.state === 'MISSING'` | 3 red: `覆盖面板渲染出…`, `有 riskNote 且 MISSING 的指标只渲染一次`, `三组之和等于覆盖行数…` |
| Segment 3 reverted to `项参与风险评估` | `摘要用「纳入评估范围」与「未测」…` — red |
| `stateLabel` MISSING merged to `未测` (one word table) | 2 red: the Phase 15 `四种状态各自渲染成可读标签` **and** the new `S1 与 S2 对同一个 bmi 各自渲染自己的词` |

The third check is the interesting one: merging the vocabularies breaks a pre-existing Phase 15 assertion as well as the new one, so Contract 6.1 is now guarded from two directions.

### Why the sum assertion is not enough on its own

`groups.reduce((sum, g) => sum + g.count, 0) === coverageRows.length` would also pass if two groups were emptied and one absorbed everything. The companion region-scoped negative assertion closes that: 「尚未采集」 must contain none of 总胆固醇 / 高密度脂蛋白 / BMI / 血糖. Together they pin both "nothing is lost" and "nothing is filed under the wrong heading" (T-16-18).

## Deviations from Plan

None. Both tasks executed as written; no auto-fix was required.

## Deferred Issues

- **Summary segment 2 still says「超出参考范围」** while `classify` in `MetricCoverageAssembler` still grades against `normalMin`/`normalMax`. Deferred to **16-05 by design** (D-09 plus the classify change must land in the same commit, else the sentence contradicts the pill). Systolic 130–139 renders a NORMAL pill beside a `超过 130 mmHg 计入 30 分` note until then — 16-03 flagged this and it is 16-05's file scope.
- **Coverage load failure renders as "you have no metrics"** (`catch { coverage.value = [] }`). Accepted in the plan as out of scope (UI-SPEC Deferred → Phase 18). Unchanged by this plan.
- **Dialog `width="600px"` overflows horizontally at 375px.** Pre-existing, not touched.
- **No backfill entry point for the four lipids.** Adding a jump button would be new component behaviour; the plan explicitly excludes it.
- **`usedMissing` is exercised only through the template.** No test asserts the computed's value directly — the summary text is the contract, and asserting both would pin an implementation detail. Worth noting rather than "fixing".

## Known Stubs

None. Every value rendered comes from the fixture or the live API. The four lipid rows show `未测` because the database genuinely has zero lipid records (CONTEXT 数据现实: 「这是预期的诚实状态，不是缺陷」) — that is the honest state the panel is supposed to display, not a placeholder.

## Threat Flags

None. No new endpoints, request parameters, auth paths, or file access patterns; no schema change; `package.json` untouched, so T-16-SC did not fire. All new text is Vue mustache interpolation — no `v-html` (`grep -c "v-html" Risk.vue` → 0), so T-16-19 stayed at `accept`.

| Threat | Disposition | Evidence |
|---|---|---|
| T-16-16 (predicate overlap returns) | mitigate | Sum-equals-rows gate + region negative assertion + 16-02's `>=` tightened to `==` |
| T-16-17 (scope read as usage) | mitigate | Segment 3 reworded; `usedMissing` derived from `usedMetrics` and asserted textually |
| T-16-18 (missing participants moved out to shrink the number) | mitigate | `usedMetrics`/`otherMeasured` untouched; sum assertion fails if a row is removed rather than re-filed |
| T-16-20 (segment 2 changed early) | mitigate | Wording and `abnormal` both unchanged; inline comment names 16-05 |

## Self-Check: PASSED

- `Risk.vue` and `RiskBackfill.spec.ts` modified — FOUND (192 insertions, 8 deletions across both)
- Commits `57f7040` (Task 1) and `869104a` (Task 2) — FOUND, both on `chore/remove-unused-modules`
- Measured from the on-disk ledger `47b59c3..HEAD`: **2 commits**, matching `commits: 2`
- 40/40 spec tests, 621/621 full suite, `build` exit 0 — PASSED
- A metric with riskNote AND MISSING renders exactly once — PASSED (teeth check confirms the assertion fails when the predicate is reverted)
- Three groups sum to `coverage.length` with strict equality — PASSED
- Summary says 纳入评估范围, not 参与风险评估 — PASSED (count 0 for the old phrase)
- `git diff -- hhs-backend/` empty — PASSED
