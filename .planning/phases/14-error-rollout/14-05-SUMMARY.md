---
phase: 14-error-rollout
plan: 05
subsystem: ui
tags: [error-handling, form-validation, inline-errors, labels-override, dialog, element-plus, vue3, vitest, present-states]

requires:
  - phase: 14-error-rollout
    plan: 01
    provides: "showError's presentStates option with its zero-toast default for forbidden/notFound, and the duration-0 VNode branch both kinds now reach"
  - phase: 14-error-rollout
    plan: 02
    provides: "the global .el-form-item.is-error border declaration — this plan writes no local error styling because that block already covers input, select and daterange"
  - phase: 14-error-rollout
    plan: 03
    provides: "collectInvalidFieldMessages as the shared client-validation collector, and the 46-key ERROR_MESSAGES / 44-label FIELD_LABELS tables both pages resolve through"

provides:
  - "the established presentation-channel rule: a file with no ErrorState passes presentStates true at every showError call; a file with one passes it nowhere"
  - "the labels-override pattern demonstrated on two pages: goals title→目标标题 and value→当前值/进度值, reminders title→提醒标题, description→提醒描述, all per-call and never via the global table"
  - "goals/Index.vue fully wired: two composable instances with distinct fieldOrder/labels, FormErrorSummary as each el-form's first child, five-plus-one form items carrying prop+data-field+:error+clearField, reset before submit, and the canonical showError submit shape on create/progress/delete/load"
  - "goals/Index.vue delete split into confirm-then-api with a bare silent return on cancel; the hand-written cancel comparison removed"
  - "reminders/Index.vue fully wired on the same shape, plus the toggle handler's second-request defect removed (its catch no longer calls fetchReminders)"
  - "two specs (10 cases) that pin 400 → summary+inline+zero toast with values intact and the dialog open, the labels override by exact string, focus-to-summary, reset between submits, single-request-on-toggle-failure, deleted-row notFound still toasting, and AbortError cancel staying silent"
  - ".planning/phases/14-error-rollout/14-LEDGER-05.md — the ledger fragment with the seven-column header and eight complete rows"

affects: [14-06, 14-07, 14-09, 14-10, 14-11, 14-12, 14-13, 14-14]

actuals:
  tokens: 16543
  tasks: 2
  commits: 3
  plan_head_before: 2fa3d750c14ccfb97c3e54c16a2a4e39597a193e

tech-stack:
  added: []
  patterns:
    - "Presentation-channel rule by file, not by call site (see patterns-established — later plans cite it by reference)"
    - "Per-call labels override for ambiguous field names: a `title` that means 目标标题 on one page and 提醒标题 on another is fixed by passing `labels` to useFormServerErrors, because editing FIELD_LABELS would mislabel the profile and threshold pages too"
    - "Two composable instances on one page: a page with two forms needs one instance per form when their fieldOrder and labels differ; wrapped in reactive() so the template reads fieldErrors[prop] as a string rather than a Ref"
    - "reset() before validate, apply(err) only on kind === 'validation': the toast channel owns every other kind, so the projection never doubles up with one"
    - "Confirm-then-api split: the ElMessageBox await lives in its own try whose catch is a bare return, so cancel is silent by control flow rather than by comparing the rejection value — the comparison was the defect (it misreads AbortError and a normalised ApiError('', 'cancel'))"

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/GoalFormInlineError.spec.ts
    - hhs-frontend-v2/src/__tests__/views/RemindersInlineError.spec.ts
    - .planning/phases/14-error-rollout/14-LEDGER-05.md
  modified:
    - hhs-frontend-v2/src/views/goals/Index.vue
    - hhs-frontend-v2/src/views/reminders/Index.vue

key-decisions:
  - "presentStates: true is passed at every showError call in both files because neither renders an ErrorState. The option is not a per-handler judgement call here: it is the file-wide consequence of having no block, so the grep equality between `presentStates: true` and `showError(` is the correct shape of the assertion"
  - "A forbidden on a list load yields one sticky toast rather than a page block on these pages. That is the correct degradation for a page with no block — adding a block is another plan's work, and silence is never acceptable (Contract 1.3)"
  - "The submit path had to convert to showError in this plan even though it is a form-wiring plan: apply(err) needs the normalised ApiError, and showError's return value is the only supported way to obtain it while also presenting the failure. A submit handler cannot be inline-wired and still toast through ElMessage.error without breaking the zero-toast-on-validation rule"
  - "reminders handleToggle reuses alert.updateFailed ('无法更新预警状态') instead of adding reminder.updateFailed. The key already exists and its wording is the generic 'a state update failed'; a near-duplicate key would need the dictionary and its count assertion changed in the same commit. Recorded in the ledger so a reviewer can see the trade-off was deliberate"
  - "goals handleAddProgress and handleCreate share goal.saveFailed. Both are 'a write related to some goal', and the backend's own Chinese message distinguishes the details — one key per (page, operation) would multiply the dictionary for no user-visible gain. The action-type GOAL_NOT_FOUND at GoalServiceImpl:103 is what makes the presentStates flag load-bearing there"
  - "The reminder DTO has no @Valid, so a server-side field error on /api/reminders is currently unreachable. The inline wiring is defensive (it activates the day a constraint is added) and the ledger's kind-coverage column says so explicitly rather than implying a live 400 path exists"
  - "Neither dialog's showCreateDialog reset was touched. It is dialog-open initialisation, not a failure path, and moving it into a catch block is exactly the ERR-05 regression the research flagged as highest-risk"

patterns-established:
  - "The state-pick rule, stated once for the whole phase: If the file renders no ErrorState for the failing path, every showError call in it passes { presentStates: true }. If the file does render one, no call passes it — never both."
  - "Labels override over table edit: an ambiguous field name (title, value) is resolved at the call site, so the global table stays a single source that cannot be wrong for the other pages"
  - "Non-destructive is asserted, not commented: the spec snapshots the form before the rejected submit and compares every field after it, plus the dialog's visible flag. A comment saying 'we do not reset' would have been satisfied by a later edit that reset elsewhere"
  - "Mutation-verified assertions: every load-bearing case was confirmed by temporarily breaking the implementation (dropping reset(), focusSummary(), presentStates, labels; re-adding the refetch and the cancel guard) and observing exactly one failure. Two cases were rewritten after this check showed they passed with the defect present — the cancel case had to move from the string 'cancel' to an AbortError, and the reset case from a success-path submit to a second-failure submit"

requirements-completed: [ERR-03, ERR-05, ERR-07]

coverage:
  - id: D1
    description: "A goal submission rejected with fieldErrors renders the summary block plus one inline error per rejected field, and produces zero toasts"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#400：汇总首条是「目标标题：…」（覆盖值而非全局「标题」），两项内联，零 toast"
        status: pass
    human_judgment: false
  - id: D2
    description: "The goal dialog's title field error reads 目标标题, not the global 标题; the resolution is a per-call labels override"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#400：… (items[0].text() === '目标标题：目标标题不能为空' by exact equality; removing the labels option yields '标题：…' and fails — mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -c 'labels:' hhs-frontend-v2/src/views/goals/Index.vue → 2 (one per composable instance); src/utils/errorMessages.ts FIELD_LABELS unchanged"
        status: pass
    human_judgment: false
  - id: D3
    description: "The goal progress dialog's value field reads 进度值 via its own override, while the same key elsewhere still reads 指标数值"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/goals/Index.vue:266-269 (progressErrors = useFormServerErrors({ fieldOrder: ['value'], labels: { value: '进度值' } })) against src/utils/errorMessages.ts:101 (value: '指标数值') — the global entry is untouched"
        status: pass
    human_judgment: false
  - id: D4
    description: "Both dialogs stay open with every entered value intact after a failed submit; nothing in either catch resets the form or closes the dialog"
    requirement: ERR-05
    verification:
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#400 之后：已填值一字未变，弹窗仍然打开（ERR-05 值快照）"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/RemindersInlineError.spec.ts#400：汇总首条是「提醒标题：…」（覆盖值而非全局「标题」），一项内联，零 toast，值保留"
        status: pass
      - kind: grep
        ref: "grep -n 'resetFields|resetField(' hhs-frontend-v2/src/views/goals/Index.vue hhs-frontend-v2/src/views/reminders/Index.vue → no matches"
        status: pass
    human_judgment: false
  - id: D5
    description: "Each dialog is preceded by a reset() so a previous attempt's errors cannot leak into the next"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#两次提交之间 reset()：上一轮投影不残留（第二次失败走 toast，汇总必须消失） — dropping the reset() call leaves the stale two-row summary and fails the case (mutation-verified)"
        status: pass
    human_judgment: false
  - id: D6
    description: "Client-side validation failures also populate the summary, so a user sees how many fields are wrong before scrolling"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#客户端校验失败（未发出请求）：同样填充汇总、聚焦汇总、零 toast"
        status: pass
    human_judgment: false
  - id: D7
    description: "Focus moves to the summary block once after a validation failure — never to the first field, and never while typing"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#400：… (document.activeElement === the .form-error-summary wrapper; removing focusSummary() fails it — mutation-verified)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/RemindersInlineError.spec.ts#400：… (same assertion on the reminder dialog)"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/components/common/FormErrorSummary.vue:26-30 — focusSummary is called by the caller, never by a props watcher, so typing cannot move focus"
        status: pass
    human_judgment: false
  - id: D8
    description: "The goal and reminder delete handlers no longer contain a hand-written cancel comparison; a cancel produces zero toasts and a real failure produces exactly one"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/RemindersInlineError.spec.ts#确认框以 AbortError 取消：零提示（手写 cancel 判断已删除，取消路径是裸 return） — re-adding `if (error !== 'cancel') ElMessage.error(...)` toasts once and fails it (mutation-verified)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#删除确认框以 AbortError 取消：零提示，且不发 DELETE（ERR-08）"
        status: pass
      - kind: grep
        ref: "grep -c \"if (error !== 'cancel')\" hhs-frontend-v2/src/views/reminders/Index.vue → 0; the phrase survives nowhere in either file"
        status: pass
    human_judgment: false
  - id: D9
    description: "A goal or reminder list load failure renders one actionable Chinese toast; a failed create renders one actionable toast plus inline errors when the failure is a validation failure"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/goals/Index.vue:276-286 and src/views/reminders/Index.vue:118-130 — both catches call showError(error, '<module>.loadFailed', { presentStates: true }) and nothing else"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#400：… (loading the page with a 200 list leaves zero toasts, so the load path adds no noise; the create-path 400 leaves zero toasts because validation is the summary's job)"
        status: pass
    human_judgment: false
  - id: D10
    description: "Every showError call in the two files passes presentStates true, because neither page renders an ErrorState: a forbidden or notFound classification yields exactly one sticky toast rather than silence"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c 'presentStates: true' equals grep -c 'showError(' in each file → 4 and 4 (goals), 4 and 4 (reminders)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#删除已被删除的行（RESOURCE_NOT_FOUND 信封）：恰好一条提示，且文案是后端消息（B1 回归） — dropping the third argument yields 0 toasts and fails it (mutation-verified)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/RemindersInlineError.spec.ts#切换已被删除的提醒（RESOURCE_NOT_FOUND 信封）：恰好一条提示，文案是后端消息（B1 回归）"
        status: pass
    human_judgment: false
  - id: D11
    description: "Nothing in either file renders an ErrorState component, so presentStates true is the only presentation channel available for a state-classified failure"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c 'ErrorState' hhs-frontend-v2/src/views/goals/Index.vue → 0; hhs-frontend-v2/src/views/reminders/Index.vue → 0"
        status: pass
    human_judgment: false
  - id: D12
    description: "The reminder toggle handler no longer issues a second list request inside its failure branch"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/RemindersInlineError.spec.ts#切换失败只发一次请求：失败分支不再重取列表 — re-adding fetchReminders() to the catch makes the list call count 2 and fails it (mutation-verified)"
        status: pass
    human_judgment: false
  - id: D13
    description: "Both files end this plan with zero bare ElMessage.error calls in the submit and delete paths, while any success toasts they own are untouched"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c 'ElMessage.error' → 0 for both files; ElMessage.success remains in goals (3 sites) and reminders (3 sites), unchanged"
        status: pass
    human_judgment: false
  - id: D14
    description: "UI Considerations · empty E8 form: an empty dialog is a legal input state; the submit button stays clickable and produces summary plus inline errors rather than a greyed-out button"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/goals/Index.vue:147 and src/views/reminders/Index.vue:78 — the footer button binds only :loading, never :disabled"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#客户端校验失败（未发出请求）：同样填充汇总、聚焦汇总、零 toast (the submit proceeds with only one field filled and returns errors rather than being blocked)"
        status: pass
    human_judgment: false
  - id: D15
    description: "UI Considerations · partial E4: only rejected fields are marked, every other field keeps its value and its normal border"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#400：… (exactly 2 .el-form-item.is-error out of 6 submitted items; exactly 2 .el-form-item__error nodes)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/RemindersInlineError.spec.ts#400：… (exactly 1 of 3)"
        status: pass
    human_judgment: false
  - id: D16
    description: "UI Considerations · zero-one-many E5: N=1 reads 请修正以下 1 项填写问题; N greater than 1 inserts N; N=0 renders no summary block at all"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/RemindersInlineError.spec.ts#400：… (contains '请修正以下 1 项填写问题')"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#400：… (contains '请修正以下 2 项填写问题')"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/GoalFormInlineError.spec.ts#两次提交之间 reset()：… (.form-error-summary does not exist once the projection is cleared — N=0 renders nothing)"
        status: pass
    human_judgment: false
  - id: D17
    description: "UI Considerations · overflow E5: the summary list caps at 144px with internal scrolling, so the title and first entry stay visible past four entries"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/components/common/FormErrorSummary.vue:102-104 (max-height: 144px; overflow-y: auto; overscroll-behavior: contain) — inherited from 14-01, this plan adds no geometry"
        status: pass
    human_judgment: false
  - id: D18
    description: "UI Considerations · long-text E4: inline messages keep Element Plus's absolute positioning and 1.4 line height, so a long message wraps and may overlap the next field top — accepted existing behaviour"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/assets/styles/theme.css:1238-1242 (line-height 1.4, padding-top 4px, error colour) and EP's position:absolute top:100% — delivered in 14-02; neither page writes a local error style (grep for el-form-item__error | is-error | --el-color-danger in the two .vue files → 0)"
        status: pass
    human_judgment: false
  - id: D19
    description: "UI Considerations · loading E8: submission uses the existing button-level loading state; no skeleton and no second spinner is introduced"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/goals/Index.vue:146-149 (submitting / submittingProgress on the two footers) and src/views/reminders/Index.vue:77-79 (submitting) — unchanged from before the plan; no new loading element was added"
        status: pass
    human_judgment: false
  - id: D20
    description: "Both forms satisfy all seven conditions of Contract 4.6"
    requirement: ERR-03
    verification:
      - kind: grep
        ref: "The seven-point grep set: (1) el-form ref present, (2) data-field 6/3 + :error= 6/3, (3) clearField( 6/3 matching every :error-bound control, (4) FormErrorSummary is the first child of each el-form (read in the template; goals has two, reminders one), (5) reset() appears once per handler before validate, (6) ElMessage.error 0 in both, (7) no hand-written cancel comparison in either"
        status: pass
    human_judgment: false

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The plan's cancel-guard spec case was vacuous as first written**

- **Found during:** Task 2, while mutation-testing the new spec
- **Issue:** The plan asked for a delete-cancel case asserting zero toasts. Written with `confirm` rejecting the string `'cancel'`, it passed — but it also passed with the *old* hand-written guard `if (error !== 'cancel') ElMessage.error(...)` restored, because that guard happens to handle the string correctly. The case therefore proved nothing about the deletion, which was the whole point of the change.
- **Fix:** Both specs now reject with an `AbortError` (`Object.assign(new Error('canceled'), { name: 'AbortError' })`). That is the shape the old guard mishandled, as RESEARCH §"Hand-written cancel guards" records: `e.name === 'AbortError'`, `e !== 'cancel'` is true → toast. With the AbortError the case fails exactly one assertion when the guard is reinstated.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/GoalFormInlineError.spec.ts`, `hhs-frontend-v2/src/__tests__/views/RemindersInlineError.spec.ts`
- **Commit:** `958fc57`

**2. [Rule 2 - Test quality] Two more load-bearing assertions were silently vacuous and were rewritten**

- **Found during:** Task 2, same mutation pass
- **Issue:** (a) The focus-to-summary assertion read `document.activeElement` on a component mounted without `attachTo`, so it always read `<body>` and never observed `focusSummary()`. (b) The reset-between-submits case used a *successful* second submit, which closes the dialog — the summary block disappeared because its host was unmounted, not because `reset()` cleared it. Dropping `reset()` left the case green.
- **Fix:** (a) `mount(..., { attachTo: document.body })` in both specs, so `focusSummary()` is observable; dropping `focusSummary()` now fails. (b) The second submit is now a transport-level 500, which keeps the dialog open (ERR-05) while being a non-validation kind that `apply()` ignores — so the only thing that can clear the stale summary is `reset()`. Dropping `reset()` now fails.
- **Files modified:** both spec files
- **Commit:** `958fc57`

**3. [Rule 2 - Correctness] A helper was introduced so the client-validation branch could be exercised at all**

- **Found during:** Task 1/2 spec design
- **Issue:** The `el-form` stub returned `cb(true)` unconditionally, so `collectInvalidFieldMessages` never ran in any test — the client-failure half of Contract 4.3 would have been asserted by reading code rather than by execution.
- **Fix:** The stub's `validate` now consults a per-case `nextClientInvalid` probe and replays Element Plus's real `(valid, invalidFields)` signature. `GoalFormInlineError.spec.ts` has a case that fills the summary from client failures, asserts the `fieldOrder` sort, asserts focus lands on the summary, and asserts the API was never called.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/GoalFormInlineError.spec.ts`
- **Commit:** `ab46bc1`, refined in `958fc57`

### Deliberate-plan divergences (documented, not auto-fixes)

**1. `reminders handleToggle` reuses `alert.updateFailed` rather than `reminder.updateFailed`.** The plan's action text explicitly left this to the executor with a recorded rationale. Chosen: reuse. The key already exists (`errorMessages.ts:57`), its wording ("无法更新预警状态") is the generic shape, and a near-duplicate key would require changing the dictionary *and* its count assertion in the same commit for no user-visible gain. Recorded in the ledger row for `reminders/Index.vue:181` so a reviewer sees the trade-off; switching later is a self-contained two-file change.

**2. `goals handleAddProgress` reuses `goal.saveFailed` rather than a distinct progress key.** The plan specifies `goal.saveFailed` for both, and the backend's own message distinguishes the details. Kept as specified.

**3. The duplicate `collectInvalidFieldMessages` call in `goals/Index.vue` was folded into one shared `projectClientErrors` helper.** The plan required the call count to be 2 (import + one call). With two forms the natural implementation has two call sites; the helper keeps it at one while still projecting each form's errors into its own instance. The helper is local to the file and delegates to the shared export — it is not a second implementation of the collector, which is what 14-03 removed.

## Auth Gates

None. Zero packages installed, no credentials needed, no checkpoint reached.

## Known Stubs

None. Both pages wire real API calls; nothing renders placeholder or empty-to-UI data.

One **unreachable defence** is worth recording rather than calling a stub: `reminders/Index.vue`'s inline-error wiring has no live server counterpart today, because `UserReminderController.CreateReminderRequest` (a plain `@lombok.Data` class) carries no `@Valid` and no constraint annotations. A HTTP 400 with `fieldErrors` cannot arrive from `/api/reminders`. The wiring is correct and becomes active the moment a constraint is added; the ledger's `kind 覆盖` cell states the situation so the verifier does not treat the defensive path as proven-in-production.

## Threat Flags

None new. Both pages render every backend-derived string through `{{ }}` (T-14-13 mitigated: no `v-html`, no `dangerouslyUseHTMLString`); `focusField`'s targets are the static `prop` names this plan authored, never backend-supplied (T-14-14 mitigated: the selector set is closed); and no catch block calls `resetFields`/`resetField`/`Object.assign(form, {})` (T-14-15 mitigated, and pinned by the value snapshot).

---

## What changed, in one paragraph

Both dialogs now show a GOV.UK-shaped double presentation: a summary block above the fields counting the problems, and one inline message under each rejected field. Every submitted field carries the four contract attributes, so a 400 names `目标标题` and `提醒标题` in the user's own vocabulary rather than the global `标题`. The forms never lose what the user typed, and neither dialog closes on a failure. Outside the dialogs, four list-and-action paths per file moved onto `showError`: the reminder toggle stopped issuing a second request after a failure, and both delete handlers stopped comparing rejection values by hand — a cancel is now a bare silent return, and a row that vanished under the user still says so exactly once.

## Verification evidence

| Check | Command | Result |
|-------|---------|--------|
| Goal spec | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/GoalFormInlineError.spec.ts` | 5 passed |
| Reminder spec | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/RemindersInlineError.spec.ts` | 5 passed |
| Full suite | `npm --prefix hhs-frontend-v2 run test:run` | 43 files / 512 tests passed (baseline 41 files / 502 tests → +2 files, +10 tests) |
| Build + type-check | `npm --prefix hhs-frontend-v2 run build` | `✓ built in 9.62s`, no TS errors |
| Bare toasts in the two files | `grep -c 'ElMessage.error'` | 0 and 0 |
| presentStates parity | `grep -c 'presentStates: true'` vs `grep -c 'showError('` | 4 = 4 (goals), 4 = 4 (reminders) |
| Blanket field reset | `grep -n 'resetFields\|resetField('` | no matches in either file |

Mutation checks performed (each broke exactly one case, then the file was restored):
dropped `labels` override (both files), dropped `presentStates` on the delete path,
re-added `fetchReminders()` to the toggle catch, restored the hand-written cancel
guard, dropped `reset()`, dropped `focusSummary()`.

## Self-Check: PASSED

- All 5 declared files exist (2 views, 2 specs, 1 ledger) plus this summary.
- All 3 work commits present in history: `ab46bc1`, `2373b99`, `958fc57`.
- Commit count measured as `git rev-list --count 2fa3d75..HEAD` at the moment the
  summary was written, **excluding this summary's own `docs(14-05)` commit** — the
  same convention 14-04 used (its `commits: 3` likewise excludes `2fa3d75`, its own
  docs commit). Including it would make the number depend on when it is read.