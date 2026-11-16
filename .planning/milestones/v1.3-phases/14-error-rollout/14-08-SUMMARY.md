---
phase: 14-error-rollout
plan: 08
subsystem: ui
tags: [error-handling, form-validation, inline-errors, structural-retrofit, element-plus, vue3, vitest, present-states, err-05]

requires:
  - phase: 14-error-rollout
    plan: 01
    provides: "showError's presentStates option with its zero-toast default for forbidden/notFound"
  - phase: 14-error-rollout
    plan: 02
    provides: "the global .el-form-item.is-error border declaration that gives the new props their visible error stroke"
  - phase: 14-error-rollout
    plan: 03
    provides: "the 5 mood FIELD_LABELS additions (moodScore/energyLevel/stressLevel/sleepQuality/entryDate) and the 46-key ERROR_MESSAGES table both files resolve through"
  - phase: 14-error-rollout
    plan: 05
    provides: "the presentation-channel rule (no ErrorState → presentStates true everywhere), the canonical reset/validate/apply submit shape, and the split confirm-then-api delete"

provides:
  - "the mood form is no longer structurally unable to show a server field error: it now has :model, ref, rules and five prop-bearing items"
  - "the provable pin that plan 14-03's 5 mood label additions were the right decision — a 400 naming moodScore renders 心情评分 and the camelCase key appears nowhere in the rendered text (two explicit not.toContain calls)"
  - "the realtime inline form's per-field props plus the success-only partial reset, pinned by a value assertion rather than a comment"
  - "the cancel-guard removal on the mood delete path, now a bare silent return"
  - ".planning/phases/14-error-rollout/14-LEDGER-08.md — 5 rows (3 mood + 2 realtime) with the moodScore focus limitation and the realtime summary-placement deviation recorded"
  - "the reusable shape for retrofitting a non-prop form: bind model → add props → delete the pre-request warning → route through showError(…, { presentStates: true })"

affects: [14-12, 14-13, 14-14]

actuals:
  tokens: 15300
  tasks: 2
  commits: 2
  plan_head_before: 422d46e9c39575e611bd0a492cde8210fdcfa129

tech-stack:
  added: []
  patterns:
    - "Structural retrofit order, not attribute addition: a form with no :model cannot host :error at all, so the model/ref/rules binding must land before any prop is written — otherwise the prop renders nowhere and every test passes vacuously"
    - "Raw-key leak assertions are two explicit not.toContain calls, never one combined regex and never 'the label is non-empty': the failure message names the offending key"
    - "Success-only reset is proven by an adjacent success-path case: 'values survive a failure' alone is satisfied by code that never resets at all"
    - "A full-width summary cannot be the first child of an :inline el-form; place it immediately before the form inside the same card and record the deviation, because the rule's intent is 'above all fields, same component'"

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/MoodTrackerInlineError.spec.ts
    - hhs-frontend-v2/src/__tests__/views/MonitorInlineError.spec.ts
    - .planning/phases/14-error-rollout/14-LEDGER-08.md
  modified:
    - hhs-frontend-v2/src/views/mood/MoodTracker.vue
    - hhs-frontend-v2/src/views/realtime/Monitor.vue

key-decisions:
  - "The mood score button group was re-wrapped in an el-form-item with prop=moodScore rather than left outside the form. Leaving it outside would make the plan's headline case — a 400 on moodScore — land only in the summary with no inline item, which is half of Contract 4.1's five attributes. The button markup and the .mood-hint line are byte-identical; only the wrapper and one click-handler clause are new."
  - "focusField('moodScore') intentionally does nothing visible. A bare button group contains no input/textarea/.el-select__wrapper/.el-rate, so the narrow selector list in useFormServerErrors.ts:167-168 finds the [data-field] container and returns. Adding a custom selector would break the 'focus moves to the summary exactly once' discipline. Contract 4.4 assigns the first-invalid-field signal to the is-error stroke, and the visible label plus the summary entry already carry it. Recorded in the ledger's 理由 cell."
  - "The realtime summary sits immediately before the el-form, not as its first child. The form is :inline='true', and a full-width block as the first child of an inline flex container is compressed into one cell and wrecks the row. Contract 4.3's rule is about position relative to the fields ('above all el-form-items, in the same component'); placing it before the form in the same card satisfies that intent. Recorded as a deviation in both the ledger and section below, with the migration path (move it back if the form ever becomes vertical)."
  - "The pre-request ElMessage.warning('请填写完整信息') was deleted rather than converted. Keeping it alongside validate() means an empty submit shows a vague warning AND a summary — two presentations of one failure, which Contract 6.4 forbids. Deleting it also makes the empty-submit experience match UI-SPEC's E8 empty-form rule."
  - "Realtime value keeps both a required and a numeric-min rule. RealtimeMetricRequest requires value (@NotNull) and bounds it (@DecimalMin 0.0), so an empty form must reject it; a single required rule would let a cleared number through as 0 and hide the bound."
  - "notes reuses the existing global label 备注说明 rather than gaining a mood-specific key. The key already exists with the right meaning, and Contract 4.5 rule 5 makes every new key a dictionary-plus-count-assertion change for no user-visible gain."

patterns-established:
  - "Retrofit gate order, stated once: confirm the form has :model+ref before writing a single prop, because a prop without a model renders no :error and every assertion about inline errors becomes vacuous."
  - "The reset-position invariant is verified by an adjacent success case: a spec that only asserts 'values survive a failure' passes against code that never resets, so the pair (failure keeps values, success clears them) is what actually pins Object.assign's location."
  - "Where a control cannot be focused by the shared selector list, record the limitation in the ledger rather than widening the selector — the selector set is closed by contract (T-11-15 / Contract 4.4)."

requirements-completed: [ERR-03, ERR-05, ERR-08]

coverage:
  - id: D1
    description: "The mood form's four server-constrained fields render Chinese-labelled inline errors on a 400, not raw camelCase keys"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#400：汇总读作「心情评分」与「睡眠质量」，两个驼峰键一个都不许露出来"
        status: pass
    human_judgment: false
  - id: D2
    description: "The mood form now has a bound model and per-field props, so its errors have somewhere to land"
    requirement: ERR-03
    verification:
      - kind: grep
        ref: "grep -c ':model=' hhs-frontend-v2/src/views/mood/MoodTracker.vue → 1; grep -c 'data-field' → 5; grep -c 'prop=\"moodScore\"' → 1"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#400：… (mounting the page and driving handleSubmit through the el-form stub only reaches the API because the form is bound; the two is-error items prove :error is attached)"
        status: pass
    human_judgment: false
  - id: D3
    description: "The mood score selector participates in the error surface: an unfilled moodScore produces a summary entry, and the button group is inside its own form item"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#400：… (items[0].text() === '心情评分：心情评分不能为空' by exact equality; 2 of the 5 items carry is-error)"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/views/mood/MoodTracker.vue:23-45 — the 10-button group sits inside <el-form-item label=\"心情评分\" prop=\"moodScore\" data-field=\"moodScore\" :error=…>; the button markup and .mood-hint are unchanged from HEAD"
        status: pass
    human_judgment: false
  - id: D4
    description: "The realtime manual-add form gains per-field props and an error projection, so a rejected metricKey/value/unit renders inline rather than as one generic toast"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/MonitorInlineError.spec.ts#400：汇总读作「指标数值」、一项内联、零 toast，且表单值原样保留"
        status: pass
      - kind: grep
        ref: "grep -c 'data-field' hhs-frontend-v2/src/views/realtime/Monitor.vue → 3; grep -c \"ElMessage.warning('请填写完整信息')\" → 0"
        status: pass
    human_judgment: false
  - id: D5
    description: "The realtime add's partial reset stays exactly where it is: it runs only after a successful add, never on failure"
    requirement: ERR-05
    verification:
      - kind: integration
        ref: "src/__tests__/views/MonitorInlineError.spec.ts#500：恰好一条 toast，且已选的指标与已输入的数值都还在（成功-only 重置护栏） — moving Object.assign into the catch fails this case (mutation-verified)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/MonitorInlineError.spec.ts#成功路径：重置仍然发生 — proves the case above is not satisfied by code that never resets"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/views/realtime/Monitor.vue:432 (Object.assign inside the try after the api await) — read in the committed handler; git diff shows no changes below handleAdd"
        status: pass
    human_judgment: false
  - id: D6
    description: "Both pages' delete handlers lose their hand-written cancel comparison; cancel is silent and a real failure produces exactly one toast"
    requirement: ERR-08
    verification:
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#删除确认框以 AbortError 取消：零提示，且不发 DELETE（ERR-08） — reinstating the hand-written comparison fails it (mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -c \"if (e !== 'cancel')\" hhs-frontend-v2/src/views/mood/MoodTracker.vue → 0 (the phrase survives in no comment either)"
        status: pass
    human_judgment: false
  - id: D7
    description: "A mood list load failure and a realtime trend load failure each produce one actionable toast; the mood page's paired load produces exactly one toast for the whole pair"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/mood/MoodTracker.vue:255-280 — one catch wraps the Promise.all, so a failure of either half enters the branch once and calls showError once; the pair is deliberately not split"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/MonitorInlineError.spec.ts#趋势加载失败：恰好一条 toast（本页无状态块，presentStates 承重） — dropping presentStates reads 0 toasts (mutation-verified)"
        status: pass
    human_judgment: false
  - id: D8
    description: "Neither page's success path clears user input on failure, and neither dialog/panel closes on failure"
    requirement: ERR-05
    verification:
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#400：… (form.energyLevel and form.stressLevel still equal their pre-submit values)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/MonitorInlineError.spec.ts#400：… (form.metricKey stays 'heartRate' and form.value stays 72.5)"
        status: pass
      - kind: grep
        ref: "grep -n 'resetFields|resetField(' in both views → no matches; no catch block calls Object.assign or a form reset"
        status: pass
    human_judgment: false
  - id: D9
    description: "Every showError call in both files passes presentStates true, because neither page renders an ErrorState: a forbidden or notFound classification yields exactly one sticky toast rather than silence"
    requirement: ERR-08
    verification:
      - kind: grep
        ref: "grep -c 'presentStates: true' equals grep -c 'showError(' → 3 = 3 (mood), 2 = 2 (realtime); grep -c 'ErrorState' → 0 and 0"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#删除已被删除的行（FORBIDDEN 信封）：恰好一条提示，且文案是后端消息（presentStates 护栏） — dropping the third argument reads 0 toasts (mutation-verified)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/MonitorInlineError.spec.ts#趋势加载失败：… (forbidden envelope → one toast whose text is the backend message)"
        status: pass
    human_judgment: false
  - id: D10
    description: "UI Considerations · empty E8: an unfilled mood or realtime form stays submittable and produces summary plus inline errors"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/MonitorInlineError.spec.ts#空表单提交（客户端校验失败）：汇总 + 内联、零 toast，取代原来那句含糊的 warning (the empty form is a legal input state; the submit button binds only :loading, never :disabled)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#客户端校验失败（未发出请求）：同样填充汇总、聚焦汇总、零 toast"
        status: pass
    human_judgment: false
  - id: D11
    description: "UI Considerations · partial E4: a rejection naming only sleepQuality marks only that slider's item; the other three keep their values"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#400：… (exactly 2 .el-form-item.is-error out of 5; exactly 2 .el-form-item__error nodes; energyLevel/stressLevel values untouched)"
        status: pass
    human_judgment: false
  - id: D12
    description: "UI Considerations · zero-one-many E5: the mood summary counts only the rejected fields among its four"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#400：… (contains '请修正以下 2 项填写问题'; the two rejected fields are the only entries)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/MonitorInlineError.spec.ts#400：… (contains '请修正以下 1 项填写问题' — N=1)"
        status: pass
    human_judgment: false
  - id: D13
    description: "UI Considerations · loading E8: the existing button-level loading state is reused; no skeleton or second spinner is added"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/mood/MoodTracker.vue:95 (:loading=\"submitting\" on the existing submit button) and src/views/realtime/Monitor.vue:224 (:loading absent by design — the add button never had one; no new loading element was introduced in either file)"
        status: pass
    human_judgment: false
  - id: D14
    description: "No camelCase key can reach a user through the mood form"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/MoodTrackerInlineError.spec.ts#400：… (two explicit not.toContain('moodScore') / not.toContain('sleepQuality') on the rendered summary text, plus the same pair on the joined inline text; deleting moodScore from FIELD_LABELS makes the exact-equality assertion read '心情评分不能为空' instead of '心情评分：心情评分不能为空' — mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -c 'moodScore' src/utils/errorMessages.ts → 1 (the FIELD_LABELS entry added by 14-03); no label removal was needed here"
        status: pass
    human_judgment: false
  - id: D15
    description: "Both forms satisfy Contract 4.6's seven conditions"
    requirement: ERR-03
    verification:
      - kind: grep
        ref: "The seven-point set: (1) el-form ref present in both, (2) data-field 5/3, (3) clearField wired on every :error-bound control (change for sliders/select, update:model-value for textarea/input/number, click for the mood button group), (4) FormErrorSummary renders above all items (first child in mood; immediately before the inline form in realtime — documented deviation), (5) reset() once per submit handler before validate, (6) ElMessage.error 0 in both, (7) no hand-written cancel comparison in either"
        status: pass
    human_judgment: false
  - id: D16
    description: "The realtime reset remains success-only, proven by assertion rather than by comment"
    requirement: ERR-05
    verification:
      - kind: integration
        ref: "src/__tests__/views/MonitorInlineError.spec.ts#500：… + #成功路径：重置仍然发生 — the pair fails together if Object.assign moves (mutation-verified: relocating it into the catch failed three cases at once)"
        status: pass
    human_judgment: false
  - id: D17
    description: "Both delete/confirm paths are split, with no hand-written cancel comparison remaining"
    requirement: ERR-08
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/mood/MoodTracker.vue:292-307 — first try catches the confirm rejection with a bare return and a comment; the second try owns the api call and its showError. Same shape as goals/Index.vue:377-392 from 14-05."
        status: pass
    human_judgment: false

## Deviations from Plan

### Deliberate-plan divergences (documented, not auto-fixes)

**1. `realtime/Monitor.vue` renders the summary immediately before the `el-form` instead of as its first child.**

The plan asked for the first-child placement and allowed a fallback "only if it breaks the inline row". It does break it: the form is `:inline="true"` (an Element Plus flex row), and a full-width block as its first child is compressed into one cell alongside the fields, which visually scrambles the row. The summary now sits inside the same `el-card`, above the form. Contract 4.3's intent — above all fields, same component, same position for dialogs and pages — is preserved; only the DOM parent differs. Recorded in `14-LEDGER-08.md` under "两处结构性改造与两处显式偏离" item 3, with the migration path noted (move it back if the form ever becomes vertical). Task 2's `<action>` explicitly sanctioned this.

**2. The mood score form item is labelled 心情评分 and its button group kept byte-identical.**

The plan said to wrap the existing 10-button group and keep "the button markup and the `.mood-hint` line exactly as they are". Done — the only change inside the group is the click handler, which now also calls `moodErrors.clearField('moodScore')` (Contract 4.1's fourth attribute requires a clear-on-change on every `:error`-bound control, and a button group has no `change` event to hang it on). The `git diff` confirms the re-wrap, not a rewrite.

### Auto-fixed Issues

**1. [Rule 2 - Test quality] The realtime spec's `el-alert` stub swallowed the summary title**

- **Found during:** Task 2, first run of `MonitorInlineError.spec.ts`
- **Issue:** The stub rendered only `{{ title }}`. `FormErrorSummary` passes its heading through the `#title` **slot**, while the page's connection-status banner passes it as the `:title` **prop**. The assertion `summary.text()).toContain('请修正以下 1 项填写问题')` therefore read only the list item text and failed — the summary was rendering correctly; the stub was hiding the heading.
- **Fix:** The stub now renders `<slot name="title" />{{ title }}`, so it serves both call sites. Verified by the case passing while the list-item assertions stayed exact.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/MonitorInlineError.spec.ts`
- **Commit:** `f4f7791`

**2. [Rule 2 - Test quality] A success-path case was added because the reset guard alone was vacuous**

- **Found during:** Task 2, mutation pass on `Object.assign`
- **Issue:** "`form.value` survives a 500" is satisfied by code that never resets at all — the guard would have been green against a broken implementation that simply dropped the reset.
- **Fix:** Added `#成功路径：重置仍然发生`, which asserts all three fields are cleared after a successful add. The pair fails together if the reset moves into the catch (confirmed: relocating `Object.assign` failed three cases at once) and fails alone if the reset is deleted.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/MonitorInlineError.spec.ts`
- **Commit:** `f4f7791`

**3. [Rule 1 - Bug] Two comments contained the exact literals the acceptance greps must find zero of**

- **Found during:** Task 1 and Task 2 grep runs
- **Issue:** The mood delete doc comment quoted `if (e !== 'cancel')` verbatim, and two realtime comments quoted `ElMessage.warning('请填写完整信息')`. Both made `grep -c` return a non-zero count for criteria that require 0 — a future reviewer (or the 14-13 reconciliation) would read that as a residual defect.
- **Fix:** Rephrased in prose ("旧实现把确认框与请求塞进同一个 try、再用「拒绝值是否等于字符串 cancel」来判断"), keeping the explanation while removing the searchable literal. Both greps now read 0.
- **Files modified:** `hhs-frontend-v2/src/views/mood/MoodTracker.vue`, `hhs-frontend-v2/src/views/realtime/Monitor.vue`
- **Commit:** `772f7c7`, `f4f7791`

## Auth Gates

None. Zero packages installed, no credentials needed, no checkpoint reached.

## Known Stubs

None. Both pages wire real API calls; nothing renders placeholder or empty-to-UI data.

One **unreachable control target** is worth recording rather than calling a stub: `focusField('moodScore')` scrolls nowhere, because a bare button group has none of `input` / `textarea` / `.el-select__wrapper` / `.el-rate`. This is Contract 4.4's designed degradation (the first-invalid-field signal lives in the `is-error` stroke, not in focus), recorded in the ledger's 理由 cell for the moodScore row. It is not a defect and does not block the plan's goal: the inline error and the summary entry both render.

## Threat Flags

None new. Both files render every backend-derived string through `{{ }}` (T-14-23 / T-14-24 mitigated: no `v-html`, no `dangerouslyUseHTMLString`, and the new `prop`/`data-field` values are static string literals the plan authored, so no backend value reaches the selector path). No ledger entry or spec assertion prints a mood or realtime value — the specs assert survival by equality, never by logging content. T-14-25 is mitigated by construction and pinned by `MonitorInlineError.spec.ts`'s two reset cases.

---

## What changed, in one paragraph

Two forms that could not display a server-side field error now can. The mood page needed a structural retrofit, not an attribute addition: its `<el-form>` had no `:model` and not one `prop`, so `validate()` could not run and `:error` had nowhere to attach. It now has a bound model, five `prop`/`data-field`/`:error`/clearField items (including the previously bare ten-button mood group, re-wrapped with its markup untouched), a summary block as the form's first child, and Chinese inline copy that proves plan 14-03's five `FIELD_LABELS` additions were the right call — a 400 naming `moodScore` reads 心情评分, and the camelCase key appears nowhere in the rendered text. The realtime page's inline form kept its `:model` but gained the same five-attribute treatment on three fields; its pre-request «请填写完整信息» warning is gone, so an empty submit now yields a summary and inline errors instead of a vague single toast, and its partial reset stayed inside the success branch where a value assertion now holds it. Outside the forms, five bare toasts moved onto `showError` with `presentStates: true` (neither page renders an `ErrorState`), the mood `Promise.all` load presents once for the pair, and the mood delete handler no longer compares the rejection value by hand — cancelling is a bare silent return.

## Verification evidence

| Check | Command | Result |
|-------|---------|--------|
| Mood spec | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/MoodTrackerInlineError.spec.ts` | 4 passed |
| Realtime spec | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/MonitorInlineError.spec.ts` | 5 passed |
| Full suite | `npm --prefix hhs-frontend-v2 run test:run` | 50 files / 539 tests passed (baseline 48 files / 530 tests → +2 files, +9 tests) |
| Build + type-check | `npm --prefix hhs-frontend-v2 run build` | `✓ built in 9.91s`, no TS errors |
| Bare toasts in the two files | `grep -c 'ElMessage.error'` | 0 and 0 |
| presentStates parity | `grep -c 'presentStates: true'` vs `grep -c 'showError('` | 3 = 3 (mood), 2 = 2 (realtime) |
| ErrorState blocks | `grep -c 'ErrorState'` | 0 and 0 |
| Bound model present | `grep -c ':model='` (mood) | 1 |
| Field anchors | `grep -c 'data-field'` | 5 (mood), 3 (realtime) |
| Mood score item | `grep -c 'prop="moodScore"'` | 1 |
| Cancel guard | `grep -c "if (e !== 'cancel')"` (mood) | 0 |
| Deleted pre-check | `grep -c "ElMessage.warning('请填写完整信息')"` (realtime) | 0 |
| Blanket field reset | `grep -n 'resetFields\|resetField('` | no matches in either view |
| Ledger rows | `grep -c '^\| \`' 14-LEDGER-08.md` | 5 |

Mutation checks performed (each broke exactly the expected cases, then the file was restored):
deleted `moodScore` from `FIELD_LABELS` (broke the mood 400 and client-validation
cases on the exact-equality assertion), dropped `presentStates` on the mood delete
path (broke the forbidden-envelope case, 1 toast → 0), reinstated the hand-written
cancel comparison (broke the AbortError case, 0 toasts → 1), dropped
`presentStates` on the realtime trend load (broke that case, 1 → 0), and moved
`Object.assign(form, …)` from the try into the catch (broke three realtime cases).

## Self-Check: PASSED

- All 5 declared files exist (2 views, 2 specs, 1 ledger) plus this summary.
- All 2 work commits present in history: `772f7c7`, `f4f7791`.
- Commit count measured as `git rev-list --count 422d46e..HEAD` at the moment this
  summary was written, **excluding this summary's own `docs(14-08)` commit** — the
  same convention 14-05 used.