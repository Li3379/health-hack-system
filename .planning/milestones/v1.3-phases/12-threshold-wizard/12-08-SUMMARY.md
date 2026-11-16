---
phase: 12-threshold-wizard
plan: 08
subsystem: hhs-frontend-v2/health-thresholds-page
tags: [thresholds-page, teaching-empty-state, duplicate-recovery, error-state, thr07, thr08, d02, d20]
status: complete
requires:
  - 12-07 (ThresholdWizard.vue — props modelValue/editing, emits saved/error, exposed selectedKey)
  - 12-04 (recommended four-value set on MetricDefinitionVO; fallback mirror of alert_rule seeds)
  - Phase 11 (showError single exit, ERROR_CODE_ACTIONS['resource.exists'] → 打开编辑, ErrorState.vue, D-02/D-12)
provides:
  - Thresholds.vue hosting <ThresholdWizard> — the two wizard entry points (添加阈值, 使用推荐阈值), saved/error wiring, loadFailed ErrorState branch
  - Teaching empty state with six metadata-derived default rules (defaultRuleRows computed over store.alertableHealthMetrics)
  - Canonicalized duplicate recovery (presentDuplicateError + pendingMetricKey) shared by the wizard path and the Phase 11 contract path
  - src/__tests__/views/Thresholds.spec.ts — 6-case page contract suite
affects:
  - Phase 12 verification / UAT (visual empty-state walkthrough with emptied user_threshold is the remaining manual check)
  - A future theme.css-scoped change (WINDOWS.md open deviation: Contract 9 global dialog button-weight rules)
tech-stack:
  added: []
  patterns:
    - Phase 11 contract surface kept alive through a component swap (defineExpose'd form/handleSubmit/aliases)
    - el-table test stub extracting column template slots per row for genuinely clickable row buttons
    - mutually exclusive page states behind one v-loading wrapper (error > table > empty guarded by !loading)
key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/Thresholds.spec.ts
  modified:
    - hhs-frontend-v2/src/views/health/Thresholds.vue
key-decisions:
  - "The Phase 11 contract surface (form, handleSubmit, showAddDialog, dialogVisible, editingId) is retained and defineExpose'd: ErrorLayerSample.spec.ts cases 6/7/8 drive vm.showAddDialog/vm.form.metricKey/vm.handleSubmit and must stay green unmodified (plan must_haves + RESEARCH Pitfall 8) — this overrides Task 1's letter 'delete the local form'"
  - "openExistingThreshold closes the wizard, awaits nextTick, then reopens in edit mode: the wizard only re-runs resetForOpen on a true-going modelValue edge, so a synchronous close+open (true→true) would leave add-mode state on screen"
  - "v-loading moved from el-table to the content wrapper: during the first load neither the table branch (0 rows) nor the empty state (!loading guard) renders, so the overlay must live on an element that always exists; el-table's built-in 暂无数据 is structurally unreachable because the table only renders when thresholds.length > 0"
  - "defaultRuleRows degrades missing recommended values to normalMax so the sentence never renders 高于 null; content is generated from the same store the wizard reads (T-12-29), never a second hardcoded table"
  - "handleWizardError sources pendingMetricKey from the wizard's exposed selectedKey because ThresholdWizard emits error without a payload and ThresholdWizard.vue is outside this plan's files_modified"
  - "Delete confirmation retitled 提示 → 删除阈值 to match the must-have destructive wording 删除阈值：确定要删除这个阈值设置吗？; the cancel branch still returns silently (D-12)"
patterns-established:
  - "Contract-surface pattern: when a page's internals are rewritten, keep the exact names unmodifiable cross-phase specs touch, expose them via defineExpose, and route legacy and new paths through one shared presentation function (presentDuplicateError)"
  - "el-table stub that pulls each el-table-column's #default slot out of the table's default slot and invokes it per row — row buttons become really clickable without mounting real el-table in happy-dom"
  - "Page state exclusivity: a single v-loading wrapper plus a v-if chain (ErrorState / table+pagination / empty-with-!loading) so loading, empty and error can never coexist"
requirements-completed:
  - "THR-07 (teaching empty state: title 尚未设置个性化阈值, six metadata-derived default rules, 使用推荐阈值 CTA, zero 暂无数据 — Thresholds.spec.ts cases 1/2/3)"
  - "THR-08 (duplicate save → one toast with 打开编辑 recovery; legacy alias rows still match via toCanonicalKey; no dead button when the row is absent — ErrorLayerSample.spec.ts cases 6/7/8 green unmodified)"
  - "THR-01 page-level portion (list page owns no metric whitelist — el-option count 0; both entry points open the wizard; 12-07 delivered the wizard portion)"
  - "THR-02 page-level portion (使用推荐阈值 CTA enters the wizard whose recommended prefill is 12-07's delivery; page presents the entry, wizard owns the numbers)"
actuals:
  tokens: 8826
  tasks: 3
  commits: 2
  plan_head_before: 19d79a0d0b32219ef715df0fe87081d31f268533
  duration_minutes: 10
---

# Phase 12 Plan 08: Thresholds Page — Wizard Hosting, Teaching Empty State, Duplicate Recovery Summary

`Thresholds.vue` now hosts the 12-07 three-step wizard as its only editor, teaches the six metadata-derived default rules through a teaching empty state (never ElEmpty/暂无数据), recovers duplicate saves through a canonicalized-key lookup into edit mode, and reports list-load failures through a single ErrorState with zero toasts — pinned by a 6-case page spec with the Phase 11 contract file untouched.

## What Was Built

- **Wizard hosting** (Task 1): the 600px `el-dialog` with its six hardcoded `el-option` entries is gone (grep `el-option` = 0, D-03). `<ThresholdWizard v-model="wizardVisible" :editing="editingRow" @saved="handleSaved" @error="handleWizardError" />` is the sole editor. Entry points: header 添加阈值 (`showAddDialog`) and empty-state CTA 使用推荐阈值 (`openWizard`) — both verb+object. `handleSaved` owns success toasts (更新成功/添加成功) and refreshes the list; row 编辑 passes the whole row (D-20: non-alertable metrics like bloodOxygen remain listed, editable, deletable — no filtering anywhere).
- **Teaching empty state** (Task 1, Contract 6): `v-if="thresholds.length"` / `v-else-if="!loading"` split; empty state renders InfoFilled at 48, the 尚未设置个性化阈值 heading, the default-rules body copy, an `el-descriptions :column="1" border` whose rows are `defaultRuleRows` — a computed over `store.alertableHealthMetrics` generating `正常 {normalMin}–{normalMax} {unit}，高于 {warningHigh} 黄色提醒，高于 {criticalHigh} 红色提醒` — and the primary 使用推荐阈值 CTA. Contract 6 CSS verbatim (48/24 padding, icon 32px margin `--text-3`, 18px/600 title, `--text-2` body max-width 480, table max-width 560, cell/label/content font normalizations, `word-break: break-word` on content). Contract 9 page-level button weights (primary `600 14px/1.5`, non-primary `400/1.5`, both `!important`).
- **List presentation** (must-have sourced): metric column `show-overflow-tooltip`, four numeric columns `align="right"` + `white-space: nowrap`, `td.el-table__cell` height 48px, fixed 操作 column, `el-pagination` (`total, prev, pager, next` → 共 N 条) under the table, existing `|| '-'` null rendering and gsap entrance untouched.
- **Duplicate recovery + ErrorState** (Task 2): `presentDuplicateError` looks the row up with `toCanonicalKey(t.metricKey) === toCanonicalKey(pendingMetricKey)` and calls `showError(err, 'threshold.duplicate', { presentValidation: true, ...(existing ? { action: { onClick: () => openExistingThreshold(existing) } } : {}) })` — label auto-supplied by `ERROR_CODE_ACTIONS`, no dead button when the row is absent. `fetchThresholds`'s catch only sets `loadFailed` and returns (no toast, D-02); the page then renders `ErrorState variant="server"` with title 无法加载阈值列表, description 可能是网络波动或服务暂时不可用，请重试。, retry aria-label 重新加载阈值列表. Delete flow (confirm wording, zero-toast cancel, whole-action retry) carried over unchanged.
- **Page spec** (Task 3): `Thresholds.spec.ts` — 6 cases reusing the ErrorLayerSample mounting recipe (threshold api / useGsap / auth mocks + ElMessage spy), ThresholdWizard stubbed to expose `modelValue`/`editing` as data attributes, and an el-table stub that invokes each column's template slot per row so 编辑/删除 are really clickable.

## Coverage — must-have truths

| # | Truth (abbreviated) | Verification | Status |
|---|---------------------|--------------|--------|
| 1 | Empty list → teaching state (title 尚未设置个性化阈值, body, CTA 使用推荐阈值) | Thresholds.spec.ts case 1; `grep -c threshold-empty` = 13 | PASS |
| 2 | Default-rule table: all six alertable metrics, generated descriptions from the wizard's metadata store | spec case 2 (labels exactly 心率/收缩压/舒张压/血糖/体温/BMI; content[0] exactly 正常 60–100 次/分，高于 100 黄色提醒，高于 120 红色提醒); `defaultRuleRows` computed over store | PASS |
| 3 | No ElEmpty, no 暂无数据 | spec case 1 (`html()` not containing 暂无数据); `grep -c "ElEmpty\|暂无数据"` = 0 | PASS |
| 4 | No hardcoded el-option / metric whitelist on the page | `grep -c el-option` = 0; selection lives in the wizard | PASS |
| 5 | Duplicate submit → exactly one toast with backend message + 打开编辑 action | ErrorLayerSample.spec.ts case 6 (errorToastCount 1; VNode button text/aria-label 打开编辑) | PASS |
| 6 | 打开编辑 closes add, reopens same metric in edit | ErrorLayerSample case 7 (editingId===3, dialogVisible===true); close→nextTick→edit implementation | PASS |
| 7 | Row absent → plain string toast, no dead action | ErrorLayerSample case 8 (string message, no VNode) | PASS |
| 8 | Canonicalized key comparison (legacy glucose rows match) | cases 6/7 green with `glucose` fixtures; `toCanonicalKey` count = 2 in the lookup | PASS |
| 9 | Non-alertable row (bloodOxygen) still listed, editable, deletable | spec case 4; edit/delete buttons unconditional in template | PASS |
| 10 | Every stored row rendered regardless of alertable flag | spec case 4 (`data-row-id="4"` bloodOxygen row present; no filter in code) | PASS |
| 11 | Load failure → ErrorState variant server, 无法加载阈值列表, retry 重新加载阈值列表 | spec case 5 (title, description, aria-label) | PASS |
| 12 | Delete still confirmed; failure retries the whole delete action | `handleDelete`/`deleteThresholdConfirmed` carried over unchanged incl. `retry: () => deleteThresholdConfirmed(row)` (232eda6) | PASS |
| 13 | Metric column show-overflow-tooltip; numerics right-aligned, non-wrapping | template attrs + `.threshold-value { white-space: nowrap }`; build green | PASS |
| 14 | Default table word-break on content; `<640px` column count stays 1 | `word-break: break-word` in content rule; static `:column="1"` | PASS |
| 15 | 1 row → table; N rows → table + pagination; 条 measure word, no singular/plural variation | `el-pagination layout="total, prev, pager, next"` renders 共 N 条 (structural — stubbed in spec) | PASS |
| 16 | Null boundary renders hyphen | existing `{{ row.criticalHigh || '-' }}` rendering preserved | PASS |
| 17 | Exactly two wizard entry points, both verb+object | spec cases 3 & 6 (添加阈值, 使用推荐阈值 both open the wizard) | PASS |
| 18 | Empty only on success + zero rows; failure → ErrorState; never together | spec cases 4/5; `v-else-if="!loading"` guard + loadFailed branch | PASS |
| 19 | Delete confirm wording 删除阈值：确定要删除这个阈值设置吗？; zero toasts on cancel | title 删除阈值 + message 确定要删除这个阈值设置吗？; catch returns silently (D-12) | PASS |
| 20 | Default table always lists six metrics, no 0/1/N variation | store-derived static set; spec case 2 exact 6 | PASS |
| 21 | Six data columns + fixed action column at 48px row height | template (6 columns + 操作 `fixed="right"`); CSS `td.el-table__cell { height: 48px }` | PASS |
| 22 | In-flight shows v-loading overlay, never the empty state | v-loading on the always-present `.thresholds-body` wrapper; empty guarded by `!loading` | PASS |
| 23 | Loading / empty / error mutually exclusive | single v-if chain (ErrorState / table / empty) + spec cases 4/5 | PASS |

## Deviations from Plan

### 1. [Deviation - process] Tasks 1 and 2 share one commit

- **Found during:** Task 1 execution
- **Issue:** Both tasks modify only `Thresholds.vue`, and Task 1's letter ("delete the local form") contradicts the plan's own must_haves + RESEARCH Pitfall 8, which require ErrorLayerSample cases 6/7/8 (`vm.showAddDialog` / `vm.form.metricKey` / `vm.handleSubmit` / `vm.editingId` / `vm.dialogVisible`) to stay green unmodified. A "Task 1 only" intermediate state could not satisfy them.
- **Fix:** One coherent page rewrite committed as `232eda6` covering Task 1 + Task 2 content (wizard hosting + empty state + canonical duplicate recovery + ErrorState wiring). Task 2's `<verify>` and acceptance greps were run independently and are recorded above.
- **Commit:** 232eda6

### 2. [Rule 2 - Missing critical functionality] Compat surface retained despite "delete the local form"

- **Found during:** Task 1
- **Issue:** Deleting `form`/`handleSubmit` outright breaks the unmodifiable Phase 11 contract tests (see above).
- **Fix:** Kept a minimal, documented, `defineExpose`d contract surface routed through the same `presentDuplicateError` as the wizard path; `rules`, `formRef` and `dialogTitle` were deleted as instructed. `dialogVisible`/`editingId` are computed aliases over `wizardVisible`/`editingRow`.
- **Files modified:** Thresholds.vue
- **Commit:** 232eda6

### 3. [Rule 2 - Missing critical functionality] Must-have truths implemented beyond task action text

- **Found during:** Task 1/2
- **Issue:** Several must_have truths have no corresponding step in any task's `<action>`: pagination with 共 N 条 (truth 15 / UI-SPEC zero-one-many), delete-confirm title 删除阈值 (truth 19), table column geometry — show-overflow-tooltip, right-aligned nowrap numerics, 48px rows (truths 13/21).
- **Fix:** Implemented minimally inside `Thresholds.vue` only: `el-pagination` bound to page/size/total, ElMessageBox title 删除阈值, column attrs + CSS.
- **Files modified:** Thresholds.vue
- **Commit:** 232eda6

### 4. [Scope boundary] Contract 9 global theme.css rules not applied

- **Found during:** Task 1
- **Issue:** UI-SPEC Contract 9 also defines global `.el-dialog .el-button--primary` rules for the teleported wizard dialog; `theme.css` is outside this plan's `files_modified` (explicit coordinator constraint: do not touch files outside the plan).
- **Fix:** Applied only the page-level portion in Thresholds.vue scoped styles; the global portion is recorded as an open deviation in `.planning/WINDOWS.md` (id 1) for a future theme.css-scoped change.
- **Commit:** n/a (deferral)

## Verification

- `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/Thresholds.spec.ts src/__tests__/views/ErrorLayerSample.spec.ts` — 15 tests green (6 new + 9 contract, spec file unmodified)
- `npm --prefix hhs-frontend-v2 run test:run` — 29 files / 263 tests green (baseline + 6)
- `npm --prefix hhs-frontend-v2 run build` — green (pre-existing chunk-size warning only)
- `npm --prefix hhs-frontend-v2 run lint:check` — 0 errors (202 project-wide warnings pre-date this plan; the two touched files add only 2 `vue/one-component-per-file` stub warnings, 0 errors)

## Deferred Issues

- **Contract 9 global dialog button weights** (WINDOWS.md id 1, open): the wizard's buttons still render at theme.css `500 14px/1` because `ElDialog` teleports to body and the normalization lives in `theme.css`, outside this plan's file scope. One CSS block, ready to land in any theme.css-touching change.
- **UAT (from 12-VALIDATION):** the teaching empty state in a real browser with the user's `user_threshold` rows actually emptied, and the wizard's three-step flow under both themes, remain manual checks — DOM specs cannot cover cross-theme contrast.

## Self-Check: PASSED

- `hhs-frontend-v2/src/views/health/Thresholds.vue` — FOUND (modified, commit 232eda6)
- `hhs-frontend-v2/src/__tests__/views/Thresholds.spec.ts` — FOUND (created, commit 458ba30)
- Commits `232eda6`, `458ba30` — FOUND in `git log`
- Ledger `gsd-plan-head-before-12-08` = 19d79a0d0b32219ef715df0fe87081d31f268533; `git rev-list --count 19d79a0..HEAD` = 2 — matches `actuals.commits`
