---
phase: 12-threshold-wizard
plan: 09
type: gap_closure
status: complete
tags: [gap-closure, uat, wizard-dialog-binding, sql-acceptance-counters, fix04, thr06]
requires:
  - 12-07 (ThresholdWizard.vue — the defective one-way el-dialog binding, guard, handleSubmit success emits)
  - 12-UAT §Gaps (gap 1 test 1 major: overlay stuck display:block; gap 2 test 5 minor: AFTER stuck 3/2/7)
  - UniversalMetricDialog.vue / CriticalConfirmDialog.vue — the proven v-model + writable computed precedents
  - fix-threshold-metric-key.sql v1.1 (12-05/12-06 delivery, UAT test 5 already normalized the dev DB)
provides:
  - ThresholdWizard.vue on the repo-standard el-dialog visibility binding — save/cancel/ESC/X/guard all close through one writable computed exit; no overlay residue under destroy-on-close
  - fix-threshold-metric-key.sql counting predicates case-exact (BINARY) — the operator acceptance criterion (AFTER 计数必须全部为 0) is now truthful on utf8mb4_*_ci databases
affects:
  - Phase 12 UAT re-test of test 1 (wizard save→auto-close) and test 5 (script acceptance counters)
  - Production deployment runbook: the script header's zero-count acceptance instruction no longer misjudges a successful run as failed
tech-stack:
  added: []
  patterns:
    - el-dialog visibility: v-model + writable computed (get props.modelValue / set emit update:modelValue) — never one-way :model-value + explicit update listener under destroy-on-close
    - acceptance-measurement predicates (COUNT subqueries) use BINARY case-exact comparison while data-manipulation predicates stay collation-bound
key-files:
  created: []
  modified:
    - hhs-frontend-v2/src/views/health/components/ThresholdWizard.vue
    - hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql
key-decisions:
  - "Task 1 adopts the exact UniversalMetricDialog.vue:250-253 writable-computed binding rather than any alternative (append-to-body, dropping destroy-on-close, manual overlay surgery): UAT root cause is one-way binding desync from EP internals under destroy-on-close, and the two dialogs that demonstrably close correctly both use this pattern — replicating a proven in-repo pattern beats inventing a third mechanism"
  - "handleSubmit success path kept byte-identical (emit('saved') + emit('update:modelValue', false), NOT visible.value = false): the fix makes EP consume the same writable exit every other close route uses — one close mechanism, not a second one"
  - "Superseded handleVisibleChange deleted entirely and unmentioned in comments: leaving it would trip no-unused-vars and break lint/build (plan action 3)"
  - "Task 2 chooses BINARY exact comparison over deleting the three lowercase concatenation variants from the counting lists: keeps the counter able to detect a literal bloodglucose row on any collation, preserving the full alias-coverage semantics of the acceptance check while canonical rows fall out (AFTER = 0)"
  - "Only the eight COUNT subqueries gain BINARY; the two DELETE JOINs and four UPDATEs keep their CASE expressions and plain CI WHERE lists byte-for-byte (counting is an acceptance measurement, not data manipulation — D-13/D-14 drift prohibition, threat T-12-27)"
patterns-established:
  - "Dialog visibility binding rule for this repo: el-dialog must bind v-model + writable computed; a one-way :model-value plus an explicit @update:model-value handler desyncs from Element Plus internals under destroy-on-close and leaves the .el-overlay at display:block"
  - "Acceptance counters vs data predicates split in one-shot SQL scripts: measurement SELECTs compare case-exactly (BINARY), data statements keep CI collation semantics — both halves stay byte-stable under review (git-diff confined to counting blocks)"
requirements-completed:
  - "THR-06 (wizard save→auto-close via repo-standard binding; guard and 继续编辑 retention zero-change — ThresholdWizard.spec 13/13 unmodified, binding greps, build/lint green)"
  - "FIX-04 (script BEFORE/AFTER counting predicates case-exact; AFTER reaches 0 on the normalized dev DB with alias detection intact — live double-run verified, data statements byte-identical)"
actuals:
  tokens: 1841
  tasks: 2
  commits: 2
  plan_head_before: 73774ebb29062c7d58c8215942a3415d1c9d56c9
  duration_minutes: 5
estimate:
  tokens: 16000
  tasks: 2
---

# Phase 12 Plan 09: UAT Gap Closure — Wizard Auto-Close Binding + SQL Counting Predicates Summary

The threshold wizard now closes itself after a successful save through the repo-standard `v-model` + writable computed dialog binding (closing UAT gap 1, test 1 major), and the SQL repair script's BEFORE/AFTER acceptance counters finally reach 0 thanks to case-exact `BINARY` counting predicates that leave every data-manipulation statement byte-identical (closing UAT gap 2, test 5 minor).

## What Was Built

### Task 1 — wizard el-dialog → repo-standard v-model + writable computed (THR-06)

`hhs-frontend-v2/src/views/health/components/ThresholdWizard.vue` (commit `da40d6f`):

- Template: the root `el-dialog` binds `v-model="visible"`; the one-way `:model-value="modelValue"` and the paired `@update:model-value="handleVisibleChange"` listener are gone. `title`, `width="760px"`, `class`, `:close-on-click-modal="false"`, `destroy-on-close`, `:before-close="handleBeforeClose"` untouched.
- Script: writable computed added next to `isEditMode`, mirroring `UniversalMetricDialog.vue:250-253` byte-for-byte in shape — `get: () => props.modelValue`, `set: val => emit('update:modelValue', val)`.
- The superseded 4-line `handleVisibleChange` function deleted (would have tripped no-unused-vars).
- Byte-identical per plan: `handleBeforeClose` / `compareDraft` / `isDirty()` / `confirmedLeave` / `ElMessageBox.confirm`(放弃设置/继续编辑, empty `.catch` zero-toast D-12), `handleCancel` routing through the guard, and `handleSubmit` success path (`emit('saved')` + `emit('update:modelValue', false)`). The open-reset `watch(() => props.modelValue, ...)`, steps, picker, fields, zone bar, styles, props/emits signatures all untouched — D-09 dual-direction validation out of scope as planned.

### Task 2 — SQL counting predicates case-exact via BINARY (FIX-04)

`hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql` (commit `2022f00`):

- Eight counting subqueries (Step 0 BEFORE ×4, Step 4 AFTER ×4) changed from `WHERE metric_key IN (...)` to `WHERE BINARY metric_key IN (...)`; the 19-entry alias lists byte-identical. The 4 UPDATE data lists (column-0 `WHERE metric_key IN`) and both DELETE JOIN CASE expressions untouched — `git diff` shows exactly 2 hunks confined to the two counting blocks.
- One explanatory `--` comment line added above each counting block (no SQL predicate text inside): under utf8mb4_*_ci the lowercase concatenation variants are case-insensitively equal to the canonical keys, so without case-exact comparison canonical rows are misclassified and the acceptance counters can never reach 0.
- Header docs, transaction structure, COMMIT, execution-order notes all as-is.

## Coverage — must-have truths

| # | Truth (from 12-09-PLAN must_haves) | Verification | Status |
|---|-------------------------------------|--------------|--------|
| 1 | 保存成功（新增与编辑）后向导自动关闭（v-model 可写计算属性绑定生效） | Acceptance greps: `v-model="visible"` = 1, `:model-value="modelValue"` = 0, `handleVisibleChange` = 0; writable computed present (get props.modelValue / set emit update:modelValue); ThresholdWizard.spec case 12 pins the save-success emits green; binding shape is byte-parity with UniversalMetricDialog.vue:250-253 and CriticalConfirmDialog.vue — the two in-repo dialogs that demonstrably close without overlay residue (UAT runtime root cause was one-way binding desync under destroy-on-close). Browser click-through re-confirmed at next UAT re-test of test 1 | PASS |
| 2 | 未保存守卫与「继续编辑」保留输入行为不回归 | Guard code untouched (diff hunks confined to template line + computed insertion + handler deletion); greps: `handleBeforeClose` = 4, `dialog.unsavedConfirm` = 1, `emit('update:modelValue', false)` = 2 (handleCancel + handleSubmit), `emit('saved')` = 1; 13-case spec unmodified and green | PASS |
| 3 | 重复指标恢复动作（1 条 toast + 打开编辑）不回归 | Error path (`emit('error', raw)`) untouched; full suite 263/263 green including the THR-08 contract cases (ErrorLayerSample.spec 6/7/8) | PASS |
| 4 | 修复后脚本在含规范行的库上打印 AFTER 非标准计数为 0 | Live on health-hack-system-mysql-1: script double run exits 0; script's own AFTER block prints 0/0/0/0 with `ut_total` stable at 5 (previously stuck 3/2/7 per UAT test 5); summed four-table BINARY non-canonical count = 0 and is non-vacuous (tables hold canonical rows: user_threshold 5, alert_rule 7, alert_template 19, health_alert 4); canonical probe `bloodGlucose` = 0, alias probe `glucose` = 1; greps: non-comment `WHERE BINARY metric_key IN` = 8, plain `WHERE metric_key IN` = 4; `git diff` confined to the two counting blocks + two comment lines (D-13/D-14 zero drift) | PASS |
| 5 | 前端全量套件 263+ 绿灯，ThresholdWizard.spec 13 用例不回归，构建零报错 | `test:run -- ThresholdWizard.spec.ts` 13/13; `test:run` 29 files / 263 tests green; `run build` green (pre-existing chunk-size warning only, no "error TS"); `run lint:check` exit 0 (0 errors; 202 project-wide warnings pre-date this plan) | PASS |

## Verification

- `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/ThresholdWizard.spec.ts` — 13 tests green
- `npm --prefix hhs-frontend-v2 run test:run` — 29 files / 263 tests green
- `npm --prefix hhs-frontend-v2 run build` — green
- `npm --prefix hhs-frontend-v2 run lint:check` — exit 0
- `docker exec -i health-hack-system-mysql-1 mysql -uroot -pdev_password hhs < fix-threshold-metric-key.sql` — exit 0 twice (idempotent double-run); summed BINARY non-canonical count = 0; canonical probe 0; alias probe 1; script AFTER block 0/0/0/0
- `git diff` on both files — changes confined exactly to the planned lines (wizard: 3 edits; SQL: 8 predicates + 2 comment lines)

## Deviations from Plan

None — plan executed exactly as written. (One comment line per counting block, as specified; comment length kept to a single line.)

## Threat Model Compliance

- **T-12-27** (SQL counting predicates): only the eight counting SELECTs gained BINARY; the git-diff acceptance criterion confirms the two DELETE JOINs and four UPDATEs are byte-identical; idempotent double-run re-verified live.
- **T-12-28** (wizard close path): the binding replicates the pattern proven in `UniversalMetricDialog.vue` and `CriticalConfirmDialog.vue`; the unsaved-changes guard (T-12-25) is retained byte-identical — no new data-loss path.
- **T-12-SC**: no package installs, no new dependencies.

## Self-Check: PASSED

- `hhs-frontend-v2/src/views/health/components/ThresholdWizard.vue` — FOUND (modified, commit `da40d6f`)
- `hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql` — FOUND (modified, commit `2022f00`)
- Commits `da40d6f`, `2022f00` — FOUND in `git log`
- Ledger `gsd-plan-head-before-12-09` = `73774ebb29062c7d58c8215942a3415d1c9d56c9`; `git rev-list --count 73774eb..HEAD` = 2 — matches `actuals.commits`
