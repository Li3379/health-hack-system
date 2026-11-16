---
phase: 14-error-rollout
plan: 07
subsystem: ui
tags: [error-handling, form-validation, inline-errors, present-states, dialog, element-plus, vue3, vitest, dead-branch, silent-catch]

requires:
  - phase: 14-error-rollout
    plan: 01
    provides: "showError's presentStates option (zero-toast default for forbidden/notFound, duration-0 VNode toast when opted in) and the page-scope ErrorState block on the device-platform load path"
  - phase: 14-error-rollout
    plan: 02
    provides: "the global .el-form-item.is-error border/textarea declarations — this plan writes no local error styling in either file"
  - phase: 14-error-rollout
    plan: 03
    provides: "collectInvalidFieldMessages as the shared client-validation collector and the FIELD_LABELS table both pages resolve through (including clientId 客户端 ID / clientSecret 客户端密钥)"
  - phase: 14-error-rollout
    plan: 05
    provides: "the by-file presentation-channel rule, the canonical submit shape (showError first, apply only on validation), and the confirm-then-api split pattern this plan applies to a third page"

provides:
  - "UniversalMetricDialog.vue fully wired: one useFormServerErrors instance across all three submission modes, FormErrorSummary as the form's first child, eight items carrying prop+data-field+:error+clear-on-change, reset before validate, the canonical submit shape, and showError(..., { presentStates: true }) on the catch"
  - "the five spec files mounting the dialog repaired at the stub rather than weakened: el-form-item now declares error in its props and renders data-error, so the inline assertion is observable (RESEARCH Pitfall 1 closed)"
  - "DevicePlatformConfig.vue fully wired on the action channel: save/test/delete/init all pass presentStates true, loadConfigs alone omits it (grep 4 of 5), the save dialog renders summary + inline for clientId/clientSecret"
  - "the unreachable else after if (res.code === 200) in handleSaveConfig deleted, with request.ts:45-52 named as the evidence and the ElMessage.error count dropping to 0"
  - "both confirm-then-api handlers (delete, init-defaults) split so a cancel is a bare silent return and a real API failure is presented — three silent catches in this file became two deliberate-silence comments plus two showError calls"
  - "DevicePlatformConfigForms.spec.ts (4 cases) pinning a save-time 403 to exactly one toast with the page block ABSENT and the dialog open, plus a 400 case, a cancel case and a swallowed-failure case"
  - ".planning/phases/14-error-rollout/14-LEDGER-07.md — five rows with the seven-column header, four presentStates-toast and one 状态块"

affects: [14-09, 14-10, 14-11, 14-12, 14-13, 14-14]

actuals:
  tokens: 13523
  tasks: 2
  commits: 2
  plan_head_before: b8070c9d589aff6784313a3715d41ca116ca6af0

tech-stack:
  added: []
  patterns:
    - "One projection instance for a form with mutually exclusive modes: blood pressure, wellness and standard share the field names value/diastolicValue/recordDate, so three useFormServerErrors instances would leave the inactive modes' stale projections in the summary; the modalities are exclusive per dialog opening, which is what makes one instance correct"
    - "Stub repair before wiring, verified separately: the el-form-item stubs were given :error and a data-error attribute and all five specs were run green BEFORE the component changed, so any later failure is attributable to the wiring rather than the stub (Pitfall 1's explicit instruction)"
    - "A mutation-verified action-channel spec: the 403 case asserts one toast AND the page block's absence in the same test, so dropping presentStates fails it in one direction and rendering a block fails it in the other — the two channels cannot collapse into each other unnoticed"
    - "Delete-by-removal for a dead branch: an else after `if (res.code === 200)` is deleted rather than migrated, because a catch-only migration would leave a second always-false presentation path that a future reader would 'fix'"

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/DevicePlatformConfigForms.spec.ts
    - .planning/phases/14-error-rollout/14-LEDGER-07.md
  modified:
    - hhs-frontend-v2/src/components/metric/UniversalMetricDialog.vue
    - hhs-frontend-v2/src/views/settings/DevicePlatformConfig.vue
    - hhs-frontend-v2/src/__tests__/components/UniversalMetricDialog.spec.ts
    - hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts
    - hhs-frontend-v2/src/__tests__/views/HealthMetrics.spec.ts
    - hhs-frontend-v2/src/__tests__/views/PreventionAndWellness.spec.ts
    - hhs-frontend-v2/src/__tests__/views/WellnessMetrics.spec.ts

key-decisions:
  - "The dialog's submit catch passes presentStates true although the file renders no failing path at all: it is a modal, so there is no ErrorState block, and HealthMetricServiceImpl:219 throws AUTH_FORBIDDEN on the write path this dialog drives. The file-wide rule from 14-05 applies unchanged — the option is a consequence of having no block, not a per-handler judgement"
  - "DevicePlatformConfig passes presentStates at exactly four of its five showError calls. The omission is loadConfigs, which owns the page block; opting in there would present one 403 twice (block plus toast). The grep assertion is therefore 'equals min the count minus 1', not 'equals the count'"
  - "The save handler no longer inspects res: adminSaveConfig is awaited bare and any non-200 envelope arrives as a rejection. This is what makes the else deletion behaviour-preserving rather than a lost feedback path — request.ts:45-52 rejects before the try can see a non-200 body"
  - "delete and init-defaults share device.saveFailed rather than gaining device.deleteFailed / device.initFailed. Both are writes against the same platform config, the backend's own Chinese message distinguishes the detail, and each new key would require touching errorMessages.ts and its count assertion in the same commit for no user-visible gain. Recorded in the ledger row"
  - "The dialog's catch drops the old `error.message` read entirely instead of forwarding it. showError resolves the backend copy (and only falls back to the dictionary when the backend said nothing), so passing ApiError.message through would hand the user the already-normalised copy — the Pitfall 3 shape in reverse"
  - "handleValueChange / handleDiastolicValueChange are named helpers rather than inline clearField calls because three distinct items bind value-family keys (systolic, mood, standard) and one of them is inside a v-if branch; a single named handler keeps the clear-on-change contract greppable at every one of those three controls"
  - "Given the plan's 'presentStates: true count at least 4' floor, the four sites are enumerated explicitly in the acceptance check (save catch, test catch, handleDeleteConfig api catch, handleInitDefaults api catch). An 'at least 3' floor enumerating four sites would have been satisfied by a file missing one of them"
  - "Neither file's open/reset path was touched. openConfigDialog resets the form on dialog OPEN and initForm does the same for the metric dialog; both are initialisation, not failure paths, and moving either into a catch would be the ERR-05 regression the research flags as highest-risk"

patterns-established:
  - "Dead-branch deletion is asserted by a counter, not a comment: the else is gone AND grep -c ElMessage.error is 0. A reviewer can verify the second without reading the function, and a future re-introduction of the else would restore the count"
  - "Silent-catch splitting is verified from the failure side, not the cancel side: the cancel case would pass under the old swallowed shape too (it swallowed everything), so the load-bearing case is the one where the API fails and exactly one toast must appear. Both were written and both were mutation-checked"
  - "Action-channel vs state-channel is pinned in ONE test that asserts both directions: a save-time 403 must produce a toast and must NOT produce the page block. Splitting these into two tests would let a bug satisfy each separately"

requirements-completed: [ERR-03, ERR-05]

coverage:
  - id: D1
    description: "The shared metric dialog renders a summary plus inline field errors on a server 400, with zero toasts, and the dialog stays open with the entered value intact"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/components/UniversalMetricDialog.spec.ts#Dim 11: server 400 should render summary + inline error with zero toast, dialog open, value intact (toastCount()===0, .form-error-summary present with '请修正以下 1 项填写问题', one .el-form-item__error, form.value still 5.5, .el-dialog still rendered)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Every one of the five spec files that mounts this shared dialog still passes; none is deleted, skipped or weakened to accommodate the wiring"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "npm run test:run -- the five paths → 5 files / 28 tests passed (baseline 26, +2 new cases); grep -c '\\.skip\\|\\.todo' → 0 in each of the five"
        status: pass
    human_judgment: false
  - id: D3
    description: "The dialog's stubbed el-form-item forwards an :error prop in each spec that mounts it, so the inline-error assertion is meaningful rather than vacuous"
    requirement: ERR-03
    verification:
      - kind: grep
        ref: "props: ['label', 'prop', 'error'] in all five files (UniversalMetricDialog.spec.ts, Metrics.spec.ts, HealthMetrics.spec.ts, PreventionAndWellness.spec.ts, WellnessMetrics.spec.ts); each stub template also renders :data-error and a .el-form-item__error span. The five specs were re-run GREEN after the stub change and BEFORE the component change"
        status: pass
    human_judgment: false
  - id: D4
    description: "On a non-validation failure the dialog produces exactly one actionable toast and still keeps the entered value — the failure path never closes the dialog and never resets the form"
    requirement: ERR-05
    verification:
      - kind: integration
        ref: "src/__tests__/components/UniversalMetricDialog.spec.ts#Dim 12: a forbidden submit yields exactly one toast and keeps the value (presentStates) (errorToastCount()===1, no summary block, form.value still 82, dialog still rendered)"
        status: pass
    human_judgment: false
  - id: D5
    description: "The device-platform config save dialog renders inline errors for clientId and clientSecret with zero toasts and stays open"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/DevicePlatformConfigForms.spec.ts#保存时 400：汇总 + 内联一项，零 toast，弹窗仍开 (toastCount()===0, summary item text '客户端 ID：客户端 ID 不能为空', exactly one .el-form-item.is-error, .el-dialog present, .error-state--page absent)"
        status: pass
    human_judgment: false
  - id: D6
    description: "The device-platform config save and test handlers route failures through showError with presentStates true, because they are action-type operations on a page whose state block belongs to the load path — a 403 on save must not blank the page"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/DevicePlatformConfigForms.spec.ts#保存时 403：恰好一条 toast、页级块缺席、弹窗仍开、clientId 原样保留 — dropping presentStates on the save catch fails exactly this case (mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -c 'presentStates: true' = 4 against grep -c 'showError(' = 5 in DevicePlatformConfig.vue; the four handlers read are handleSaveConfig, handleTestConfig, handleDeleteConfig, handleInitDefaults. The single omission is loadConfigs"
        status: pass
    human_judgment: false
  - id: D7
    description: "The device-platform save handler's unreachable else branch is gone: the interceptor rejects on any non-200 envelope, so the else could never execute"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/settings/DevicePlatformConfig.vue:410-417 — adminSaveConfig is awaited bare under `try`, with no `if (res.code === 200)` and therefore no else; the comment names request.ts:45-52 as the reason. Reading the function shows no else after the awaited call"
        status: pass
      - kind: grep
        ref: "grep -c 'ElMessage.error' hhs-frontend-v2/src/views/settings/DevicePlatformConfig.vue → 0 (was 4 before this plan; the deleted else is the first unit of that drop)"
        status: pass
    human_judgment: false
  - id: D8
    description: "The device-platform delete and init-defaults confirm dialogs are split from their api calls so a cancel is silent and a real failure produces exactly one toast"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/DevicePlatformConfigForms.spec.ts#删除确认框取消：零提示且不发 DELETE (confirm rejects with AbortError → toastCount()===0, adminDeleteConfig not called)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/DevicePlatformConfigForms.spec.ts#删除的 API 真失败：不再被当成「用户取消」吞掉，恰好一条提示 — restoring `catch { /* User cancelled */ }` fails exactly this case (mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -n 'User cancelled' hhs-frontend-v2/src/views/settings/DevicePlatformConfig.vue → no matches; the two confirm-only catches carry an explicit deliberate-silence comment instead"
        status: pass
    human_judgment: false
  - id: D9
    description: "No catch block in either file resets a form or closes a dialog on failure"
    requirement: ERR-05
    verification:
      - kind: grep
        ref: "grep -n 'resetFields|resetField(' → no matches in UniversalMetricDialog.vue or DevicePlatformConfig.vue; the dialog's showConfigDialog.value = false sits only inside the success branch after the awaited save"
        status: pass
      - kind: integration
        ref: "The two 403/400 dialog cases above both assert the dialog element still exists and the typed values are unchanged after the rejection"
        status: pass
    human_judgment: false
  - id: D10
    description: "UI Considerations · empty E8: the shared dialog with only a required value empty stays submittable and yields summary plus inline errors"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/components/metric/UniversalMetricDialog.vue:250-258 — the footer button binds only :loading and :disabled=\"isSaveBlocked\" (a Tier-4 hard-limit flag), never an emptiness check; submitting an empty value reaches el-form validate and projects via collectInvalidFieldMessages"
        status: pass
      - kind: integration
        ref: "src/__tests__/components/UniversalMetricDialog.spec.ts#Dim 11 (the submit proceeds with a populated value and returns server errors rather than being blocked by a disabled button); the client-failure half shares the same setErrors channel as goals/reminders did in 14-05"
        status: pass
    human_judgment: false
  - id: D11
    description: "UI Considerations · partial E4: the metric dialog's blood-pressure mode shows two errored fields independently, with no cross-contamination of their messages"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/components/metric/UniversalMetricDialog.vue:47-49 (systolic keeps prop=\"value\", :error=\"formErrors.fieldErrors['value']\", clear via handleValueChange) vs :73-75 (diastolic keeps prop=\"diastolicValue\", its own key, clear via handleDiastolicValueChange) — the two items bind disjoint keys, so a projection naming one leaves the other clean"
        status: pass
    human_judgment: false
  - id: D12
    description: "UI Considerations · zero-one-many E5: each form's summary count reflects only its own rejected fields"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "UniversalMetricDialog.spec.ts#Dim 11 asserts '请修正以下 1 项填写问题' for a single-field rejection; DevicePlatformConfigForms.spec.ts#保存时 400 likewise asserts '请修正以下 1 项填写问题' with exactly one li. N is props.summaryItems.length, i.e. the projecting instance's own fieldErrors"
        status: pass
    human_judgment: false
  - id: D13
    description: "The 14-LEDGER-07 fragment holds five rows with the seven-column header; four carry presentStates-toast with a one-sentence reason and one carries 状态块 for the load path; the deletion names the interceptor as evidence"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c '^| `' → 5; of those rows 4 contain presentStates-toast and 1 contains 状态块 (the other 状态块 match is inside the test catch row's prose, not its form column). Header is | file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |"
        status: pass
    human_judgment: false

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The `data-field` assertion in the new dialog case counted source sites, not rendered ones**

- **Found during:** Task 1, first run of the new `Dim 11` case
- **Issue:** The plan asks for "at least 6" `data-field` occurrences, which is true of the *source* (eight items) but not of the *rendered* DOM in any single mode. In `bloodGlucose` (standard) mode the `diastolicValue` and `notes` items are behind `v-if` branches, so only four `[data-field]` nodes exist. The assertion failed with "expected 4 to be greater than or equal to 6" — correctly, because it was measuring the wrong thing.
- **Fix:** The case now asserts the exact rendered set in that mode — `['metricKey','value','recordDate','trend']` — which is a stronger assertion than a count (it would catch a renamed or duplicated anchor) and is still derived from the plan's intent that `focusField()` have a DOM target per rejected field. The source-level count (8) remains verifiable by grep and is recorded under D3.
- **Files modified:** `hhs-frontend-v2/src/__tests__/components/UniversalMetricDialog.spec.ts`
- **Commit:** `ac39437`

**2. [Rule 1 - Bug] A typo in the new spec's `beforeEach` (`adminDeleteConfigMock.reset`) masked all four cases**

- **Found during:** Task 2, first run of `DevicePlatformConfigForms.spec.ts`
- **Issue:** `adminDeleteConfigMock.reset()` is not a Vitest mock method — the correct one is `mockReset()`. The TypeError fired in `beforeEach`, so all four cases reported FAIL with an error that pointed at the harness rather than at a behaviour.
- **Fix:** `mockReset()`. All four then ran, exposing the real issues below.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/DevicePlatformConfigForms.spec.ts`
- **Commit:** `c328e86`

**3. [Rule 1 - Bug] The 400 case asserted the wrong field label**

- **Found during:** Task 2, second run
- **Issue:** The case expected the summary's first entry to read `Client ID：客户端 ID 不能为空`, but the global `FIELD_LABELS` table already maps `clientId` to `客户端 ID` (`errorMessages.ts:120`). The rendered text was `客户端 ID：客户端 ID 不能为空`. The implementation was right and the assertion was wrong — exactly the direction the plan forbids fixing by loosening.
- **Fix:** The assertion now uses the actual global label, and the case comment records that this page passes no `labels` override (unlike goals, where `title` needed one). Using the label verbatim as the expected string keeps the case non-vacuous: any change to the global table that made this page mislabel would fail it.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/DevicePlatformConfigForms.spec.ts`
- **Commit:** `c328e86`

### Deliberate-plan divergences (documented, not auto-fixes)

**1. The ledger's save row merges the deleted `else` with the save catch.** The plan lists five rows as "the shared dialog's submit catch plus the four settings rows — the save catch, the test catch, the save else, and the load catch", which would be five settings rows plus one dialog row = six. The acceptance criterion says five rows total with four `presentStates-toast` and one `状态块`. Those two statements can only both hold if the deleted `else` shares a row with the catch that replaced it — which is also the more honest shape, since the `else` produced no presentation path of its own. The row's `迁移后形态` cell says `presentStates-toast` and its rationale text states plainly that the `:332` half is a deletion; a separate prose section ("删除记录") repeats the interceptor evidence so the deletion is not buried inside a table cell. Net: 5 rows, 4 `presentStates-toast`, 1 `状态块`.

**2. `handleTestConfig` reuses `device.saveFailed`, not a new key.** The plan specifies `device.saveFailed` explicitly, and the rationale (test-config is a write-operation check on the same config; adding `device.testFailed` would need the dictionary plus its count assertion changed in the same commit) is recorded in the ledger row. Kept as specified.

**3. The dialog's `handleSwap` and `handleMetricKeyChange` clear projections too.** Not in the plan's action list, but both mutate values that may carry a server projection: a swap changes both pressure values, and a metric-key change replaces the metric entirely. Both call the existing `clearField` — they do not reset the form and do not touch input values, so ERR-05 is unaffected. Without it, a user could swap the two pressures and keep reading a message about the old ordering.

## Auth Gates

None. Zero packages installed, no credentials needed, no checkpoint reached.

## Known Stubs

None. Both files call real APIs; no placeholder or empty-to-UI data was introduced.

One **unreachable defence** worth recording: `DevicePlatformConfig.vue`'s `clientId` / `clientSecret` inline wiring is defensive in the same sense 14-05 recorded for reminders. A 400 with `fieldErrors` naming `clientId` is not something the current backend controller is observed to produce; the wiring activates the day a constraint is enforced. The 400 case in the spec constructs the envelope directly, so it proves the projection path rather than a live production 400.

## Threat Flags

None new. T-14-19 mitigated and pinned by the save-403 case, which asserts both the toast's presence and the page block's absence; T-14-20 mitigated — `clientSecret` appears in error copy nowhere, and the spec only asserts its value for *presence* (`toBe('super-secret-value')`), never prints it into the summary or into a toast; T-14-21 mitigated — every message goes through `{{ }}` interpolation or `FormErrorSummary`'s existing interpolation, and no `v-html` was introduced in either file; T-14-22 mitigated and pinned by the value snapshots in both the dialog and the settings cases, with `resetFields`/`resetField` grep-clean in both files. T-14-SC: zero packages installed.

---

## What changed, in one paragraph

The dialog mounted by five spec files now shows the same double presentation the rest of the phase uses: a summary above the fields and one inline message under each rejected one, with the toast reserved for everything that is not a field problem. Eight items carry the contract's four attributes, a single projection instance serves all three submission modes because the modes are exclusive while the field names overlap, and the submit path resets before validating, projects client failures through the shared collector, and calls `showError` before branching on the kind — so a 403 on the metric write path (where the backend really does throw `AUTH_FORBIDDEN`) produces one sticky toast instead of silence, without closing the dialog or touching what the user typed. On the settings page the action channel and the state channel were separated deliberately: the load path keeps the page block and opts out of toasts, while save, test, delete and init all opt in, so a denied save no longer risks replacing the populated page with a blocked state. The save handler's else branch is gone rather than migrated, because the interceptor rejects before the try can ever see a non-200 envelope, and both confirm-then-api handlers were split so a cancel is a bare silent return while a real failure finally says so.

## Verification evidence

| Check | Command | Result |
|-------|---------|--------|
| Five dialog-mounting specs | `npm run test:run -- <the five paths>` | 5 files / 28 tests passed (baseline 26, +2 new) |
| Device forms spec | `npm run test:run -- src/__tests__/views/DevicePlatformConfigForms.spec.ts` | 4 passed |
| Both device specs together | `npm run test:run -- DevicePlatformConfigForms.spec.ts DevicePlatformConfigForbidden.spec.ts` | 2 files / 15 tests passed |
| Full suite | `npm run test:run` | 48 files / 530 tests passed (baseline 47 / 524 → +1 file, +6 tests) |
| Build + type-check | `npm run build` | `✓ built in 9.71s`, no TS errors |
| Bare toasts, dialog | `grep -c 'ElMessage.error'` | 0 |
| Bare toasts, settings | `grep -c 'ElMessage.error'` | 0 (was 4) |
| presentStates parity, dialog | `grep -c 'presentStates: true'` vs `grep -c 'showError('` | 1 = 1 |
| presentStates parity, settings | same | 4 vs 5 — the omission is `loadConfigs` |
| `data-field`, dialog / settings | `grep -c 'data-field'` | 8 / 2 |
| Blanket field reset | `grep -n 'resetFields\|resetField('` | no matches in either file |
| Swallowed catches | `grep -n 'User cancelled'` | no matches |
| Ledger shape | `grep -c '^\| \`'` | 5 rows: 4 `presentStates-toast`, 1 `状态块` |

Mutation checks performed (each broke exactly one case, then the file was restored):

| Mutation | Case that failed |
|----------|------------------|
| Dropped `presentStates` from the settings save catch | `保存时 403：恰好一条 toast、页级块缺席、弹窗仍开、clientId 原样保留` |
| Restored `catch { /* User cancelled */ }` around delete | `删除的 API 真失败：不再被当成「用户取消」吞掉，恰好一条提示` |

## Self-Check: PASSED

- All 9 declared files exist: 2 views/components, 6 specs, 1 ledger — plus this summary.
- Both work commits present in history: `ac39437`, `c328e86`.
- Commit count measured as `git rev-list --count b8070c9..HEAD` at the moment this summary
  was written, **excluding this summary's own `docs(14-07)` commit** — the same convention
  14-05 and 14-06 used. Including it would make the number depend on when it is read.
- **Disclosure:** the `docs(14-07)` commit also carries one correction to `14-LEDGER-07.md`
  — the extraction of the deletion note out of a second table so the main table reads as
  exactly five rows (the acceptance criterion's shape). That is an artifact fix, not a
  behaviour change, but a reader diffing the ledger's two commits should know why the file
  changed twice. It is inside the docs commit because the count above was measured before
  it, and re-counting after would count the docs commit via the back door.
- Actuals recorded on the plan's own scale: `tokens: 13523` is chars/4 over the realized
  diff (`git diff b8070c9..HEAD | wc -c` → 54093), not a harness token count. It is far
  below the plan's 82000 estimate; that is a real undershoot (the two files were smaller
  than their line counts suggested and the dialog's three branches needed no restructuring),
  not a rounding toward the estimate.