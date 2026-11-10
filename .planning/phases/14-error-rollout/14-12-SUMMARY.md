---
phase: 14-error-rollout
plan: 12
subsystem: ui
tags: [error-handling, pinia, d-02, store-contract, error-state, inline-retry, element-plus, vue3, vitest]

requires:
  - phase: 14-error-rollout
    plan: 01
    provides: "showError's presentStates option (zero-toast default for forbidden/notFound, duration-0 VNode when opted in) — the option every Chat.vue call site now needs"
  - phase: 14-error-rollout
    plan: 05
    provides: "the presentation-channel rule: a file with no ErrorState passes presentStates true at every showError call; a file with one passes it nowhere"

provides:
  - "stores/ai.ts with no error toast: loadSessions/switchSession/getHistory/deleteSession throw normalizeApiError, sendMessage keeps its boolean and loses its toast, and the 会话已删除 success toast survives"
  - "stores/floatingAi.ts with no ElMessage at all (the element-plus import is gone), keeping the boolean and the optimistic rollback"
  - "stores/wellness.ts with no ElMessage at all: fetchSummary/fetchTrend throw, and addMetric is deleted on tree-wide search evidence"
  - "Chat.vue owning four presentation decisions: session load, session switch, split delete, history load — all with presentStates true because the file renders no ErrorState"
  - "the in-place send-failure affordance on both chat surfaces: text restored to the composer, a literal 发送未完成，请重试 line, zero toasts, no new dictionary key"
  - "wellness/Dashboard.vue with three ErrorState blocks (summary region plus one per chart container) and zero toasts on all four load paths, degrading per chart rather than per page"
  - "two specs (28 cases) pinning the store contract, the send-failure experience, the split delete, and the dashboard's block-vs-toast split"
  - ".planning/phases/14-error-rollout/14-LEDGER-12.md — the fragment with the seven-column header and all eight store rows"

affects: [14-13, 14-14]

actuals:
  tokens: 18523
  tasks: 2
  commits: 2
  plan_head_before: fbb227728ca950c7796112554e6580d652890d59

tech-stack:
  added: []
  patterns:
    - "Per-method store decision driven by the existing return contract, not a uniform rule: a void loader (or one returning a plausible empty value) throws because it costs nothing; a Promise<boolean> whose callers already branch keeps the boolean, because converting it would force every caller to change for no user-visible gain"
    - "Throw only where a catching caller lands in the same commit (T-14-37): each of the five throw sites is paired with a handler in this plan, so no store throw becomes an unhandled rejection inside an event handler"
    - "One failure flag per independent request, not per failure class: the two fetchTrend calls are separate requests, so sharing one flag would hang 'failed to load activity trend' over a chart that actually loaded"
    - "v-show on the chart container, v-if on the state block: the ECharts mount point must survive the failure, because useECharts.init returns early when the ref is empty and a retry would find nothing to mount into"
    - "Inline retry over toast for a watched-content failure: a disappearing chat bubble plus a transient banner loses the user's text, so the text goes back into the composer and a persistent line explains the state"

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/stores/StoreErrorContract.spec.ts
    - hhs-frontend-v2/src/__tests__/views/ChatSendFailure.spec.ts
    - .planning/phases/14-error-rollout/14-LEDGER-12.md
  modified:
    - hhs-frontend-v2/src/stores/ai.ts
    - hhs-frontend-v2/src/stores/floatingAi.ts
    - hhs-frontend-v2/src/stores/wellness.ts
    - hhs-frontend-v2/src/views/ai/Chat.vue
    - hhs-frontend-v2/src/views/wellness/Dashboard.vue
    - hhs-frontend-v2/src/components/ai-floating-ball/FloatingChatPanel.vue

key-decisions:
  - "The three permitted success toasts stay exactly where they are, and the store check asserts them per file instead of summing to 'exactly 3'. The live population is 7 lines across 5 files (ai 1, push 1, wellness 1-or-0, floatingAi 0, auth 4), so a bare 'exactly 3' assertion fails against the tree. wellness is asserted at 0 because addMetric was deleted; auth's 4 are asserted separately under their own test name as out-of-remit auth-flow messages"
  - "addMetric was deleted on evidence rather than left as a dead action. The tree-wide search returns only the store's own definition and export, realtimeApi.addMetric (a different module, called from Monitor.vue), and MonitorInlineError.spec.ts's mocks of that other API. Deleting it also removes the last ElMessage from wellness.ts, so that file's count is 0 and its success toast goes with the method"
  - "deleteSession throws rather than keeping its boolean. Its only caller wrapped confirm-and-delete in one bare catch commented 'User cancelled', so a real failure was reported as a cancellation — that caller had to be rewritten regardless, and a swallowed delete leaves a session in the list that the server already removed"
  - "sendMessage keeps its boolean on both stores and never throws. Both callers read the return value, and a throw inside an event handler would surface as an unhandled rejection; the user-visible gain from throwing is zero because the caller already knows it failed"
  - "No ai.sendFailed dictionary key was added. A new key would force the 46-key count assertion to change in the same commit, and the resolved research question forbids it: the send failure is presented in place, so the key would have no consumer"
  - "The dashboard uses three flags, not two. Splitting the trend flag per chart costs four lines and prevents the concrete falsehood of showing '无法加载活动趋势' over a chart whose request succeeded — test 19 pins the asymmetric case"
  - "Chat.vue's handleSwitchSession also gained a try/catch even though the plan's file list did not name it explicitly: switchSession's toast was one of the eight store sites, and leaving it throwing with no catching caller would have been exactly the T-14-37 unhandled rejection this plan exists to prevent"

patterns-established:
  - "Store-as-classifier, view-as-presenter: the store calls normalizeApiError and throws (or returns falsy); it never imports a presentation path for errors. push.ts was already this shape and is cited as the reference rather than refactored"
  - "Per-file message-budget assertions: never sum ElMessage counts across stores. Assert each file's permitted survivors by name, and assert auth.ts's four auth-flow messages in a separately named test so a stray cleanup of auth cannot be mistaken for the store check passing"

requirements-completed: [ERR-05, ERR-07, ERR-08]

coverage:
  - truth: "No Pinia store contains an error toast: the eight store call sites are resolved by throwing or by returning a falsy result, and the legitimate success toasts remain"
    status: covered
    verification: "grep -rn 'ElMessage.error' hhs-frontend-v2/src/stores --include=*.ts → no matches; per-file ElMessage counts ai 1 / push 1 / wellness 0 / floatingAi 0 / auth 4; StoreErrorContract.spec.ts tests 1, 7, 9, 10, 11, 12"
  - truth: "Every showError call in Chat.vue passes presentStates true, because the chat page renders no ErrorState block: a forbidden or notFound on a session delete or a history load yields exactly one sticky toast rather than silence"
    status: covered
    verification: "grep -c 'showError(' = 4 and grep -c 'presentStates: true' = 4 in Chat.vue; ChatSendFailure.spec.ts tests 5 and 7 assert one toast on a 500 delete and one on a 500 history load"
  - truth: "The wellness dashboard's two load paths use state blocks and pass no presentStates, because the block owns presentation for those kinds — the two channels never both fire"
    status: covered
    verification: "grep -c 'showError' = 0 and grep -c 'ErrorState' = 5 in Dashboard.vue; StoreErrorContract.spec.ts tests 17-20 assert .error-state present and toastCount() 0 in every case"
  - truth: "The four void-returning store loaders throw instead of swallowing, so a failed session load, history load, wellness summary or wellness trend is distinguishable from an empty result — today a failed history returns an empty array, which is indistinguishable from a user having no history"
    status: covered
    verification: "StoreErrorContract.spec.ts tests 2, 3, 14, 15 assert each loader rejects; grep -c 'return \\[\\]' in ai.ts is 0"
  - truth: "The store boolean contracts are preserved: the send-message method still returns false rather than throwing, because both of its callers branch on the boolean and a throw would force a caller rewrite for no user-visible gain"
    status: covered
    verification: "StoreErrorContract.spec.ts tests 4 and 8 assert resolves.toBe(false) with zero toasts on both stores"
  - truth: "The session-delete store method throws, and its caller splits the confirm from the api call so a cancel is silent and a real failure shows one toast"
    status: covered
    verification: "ChatSendFailure.spec.ts test 4 (cancel: clearHistory not called, zero toasts) and test 5 (failure: one toast, session still listed); grep -c 'User cancelled' in Chat.vue is 0"
  - truth: "A send-message failure is surfaced as an in-place retry affordance, not a toast: the typed text is restored to the composer and the user sees that the send did not complete without losing what they wrote"
    status: covered
    verification: "ChatSendFailure.spec.ts test 1 asserts the textarea value is the typed text, .send-failed is visible with 发送未完成，请重试, and errorToastCount() is 0; tests 2 and 3 assert the marker clears on retry success and on edit"
  - truth: "The floating panel surfaces the same send failure in place, using its own message area rather than a toast"
    status: covered
    verification: "FloatingChatPanel.vue's handleSend sets a local sendFailed ref rendered as .panel-send-failed above the input row (no inline failure element existed before — resolved question 6); StoreErrorContract.spec.ts test 8 asserts the store resolves false with zero toasts, and the panel's own ref is the only presentation"
  - truth: "The wellness dashboard renders a state block for a failed summary load and a chart-area state block for a failed trend load, each with zero toasts"
    status: covered
    verification: "StoreErrorContract.spec.ts test 17 (summary failure: exactly one .error-state, zero toasts, no zero-valued cards) and test 18 (trend failure: two chart-area blocks, summary cards still rendered)"
  - truth: "The chat page's session list load failure produces one toast; its history load failure produces one toast rather than a silently empty history dialog"
    status: covered
    verification: "Chat.vue onMounted wraps loadSessions in try/catch → showError(ai.loadFailed, presentStates); showHistory opens the dialog only on success. ChatSendFailure.spec.ts test 7 asserts one toast and no .history-dialog on failure; test 8 asserts zero toasts and the dialog on success"
  - truth: "No store imports a UI component for presentation; classification may use the shared error utilities but presentation stays at the call site"
    status: covered
    verification: "StoreErrorContract.spec.ts test 7 asserts floatingAi.ts contains no ElMessage token and does not import it; test 12 asserts the same for wellness.ts; no store imports a .vue component"
  - truth: "UI Considerations · error E7 list load: a failed wellness summary renders a state block, not a toast, and not a false zero-valued dashboard"
    status: covered
    verification: "StoreErrorContract.spec.ts test 17 asserts .metric-card count 0 alongside the block — the false-zero rendering is what that assertion forbids"
  - truth: "UI Considerations · loading E7: the dashboard's existing loading indicator is reused; the state block appears only after the load completes and fails"
    status: covered
    verification: "Dashboard.vue's ErrorState is v-if='summaryFailed' and the skeleton is v-else with :loading='wellnessStore.loading'; the flag is set only in the catch, so the two states are mutually exclusive by construction"
  - truth: "UI Considerations · partial E7: a failed trend load leaves the already-rendered summary intact and only the chart area degrades"
    status: covered
    verification: "StoreErrorContract.spec.ts test 18 asserts .metric-card length > 0 and the summary text still present while the chart-area blocks render; test 19 asserts exactly one block when only one of the two trend requests fails"
  - truth: "UI Considerations · empty E7: an empty-but-successful history list renders the empty state, never the error state — the distinction this plan creates in the store is what makes that possible"
    status: covered
    verification: "StoreErrorContract.spec.ts test 20 asserts zero .error-state and zero toasts for a successful load with empty data; ChatSendFailure.spec.ts test 8 asserts the dialog opens with zero toasts on a successful empty history"
---

# Phase 14 Plan 12: Store Error Presentation Moved to Its Callers Summary

D-02 executed: the eight store-internal error calls are gone, replaced by throws for the four loaders and by falsy returns for the two send paths, with the wellness dashboard adopting D-03's state-block channel and both chat surfaces gaining an in-place send retry instead of a toast.

## What Was Built

### `stores/ai.ts` — four throw sites, one success toast kept

`loadSessions` and `switchSession` now throw `normalizeApiError(error)` instead of swallowing or toasting. `getHistory` throws instead of returning `[]` — that `[]` was the plan's central defect, because a failed request and an empty conversation rendered identically. `deleteSession` throws while keeping its `ElMessage.success('会话已删除')`; `sendMessage` keeps `return false` and loses only the toast.

`loadSessions` was not one of the eight toast sites (it had a bare empty catch), but the plan explicitly requires it to throw, and the two are the same defect in different clothes: silence makes a load failure look like an empty list.

### `stores/floatingAi.ts` — no `ElMessage` at all

The toast is gone and so is the `element-plus` import, since `ElMessage` was its only consumer. The boolean and the optimistic `messages.value.slice(0, -1)` rollback are untouched.

### `stores/wellness.ts` — two throws, one deletion

`fetchSummary` and `fetchTrend` throw. `addMetric` was **deleted on evidence**: a tree-wide search finds no caller of this store action. The three matches belong to `realtimeApi.addMetric` (a different module, invoked from `Monitor.vue:428`) and to `MonitorInlineError.spec.ts`'s mocks of it. Deleting it also removed the file's last `ElMessage`, so `wellness.ts` now has zero message calls and its success toast left with the method.

### `views/ai/Chat.vue` — four presentation decisions, one in-place retry

The session load, the session switch, the delete and the history load each present through `showError`, all four with `presentStates: true` because this file renders no `ErrorState` (4 `showError(` = 4 `presentStates: true`). The delete is split into confirm-then-api: the confirm's catch is a bare silent `return`, the api's catch toasts. `showHistory` opens the dialog only on success.

A failed send restores `inputMessage` and sets `sendFailed`, which renders one literal line — `发送未完成，请重试` — above the composer. No toast, no `showError`, no dictionary key.

### `components/ai-floating-ball/FloatingChatPanel.vue` — the resolved question 6 answer

The read settled it: the panel had **no** inline failure surface. Its message area renders bubbles and typing dots only, and its input row is an input plus a button; `handleSend` already restored `inputText` but said nothing. So this plan adds the line rather than wiring into an existing element, using the panel's own scoped styles and a local ref.

### `views/wellness/Dashboard.vue` — three blocks, zero toasts

`summaryFailed`, `sleepTrendFailed` and `activityTrendFailed` drive three `ErrorState` blocks: one in the summary region and one inside each chart container. `showError` count is 0 — the block owns presentation, which is the other half of the channel rule 14-05 established.

Two details worth naming: the flags are split per chart because the two `fetchTrend` calls are independent requests (a shared flag would hang "无法加载活动趋势" over a chart that loaded); and the chart containers use `v-show` while the blocks use `v-if`, because `useECharts.init` returns early when its ref is empty, so a retry would otherwise find nothing to mount into.

## Verification

| Gate | Result |
|------|--------|
| `test:run -- StoreErrorContract.spec.ts ChatSendFailure.spec.ts` | 28 passed (20 + 8) |
| `test:run` (full) | **599 passed / 56 files** — up from the 571/54 baseline |
| `build` (`vue-tsc -b && vite build`) | green |
| `lint:check` | 0 errors, 216 warnings — identical to the pre-task baseline |
| `grep -rn "ElMessage.error" src/stores` | no matches |
| Per-file `ElMessage` | ai 1, push 1, wellness 0, floatingAi 0, auth 4 |
| Chat.vue `showError(` / `presentStates: true` | 4 / 4 |
| Dashboard `showError` / `ErrorState` | 0 / 5 |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The plan's shared `trendFailed` flag would misreport a successful chart**

- **Found during:** Task 2, while wiring the two chart containers
- **Issue:** The plan specifies `summaryFailed` and `trendFailed` — two flags. But the two `fetchTrend` calls are independent requests. With one flag, a failure on the sleep feed renders "无法加载活动趋势" over the activity chart, whose request may have succeeded. That reports a success as a failure, which is worse than silence and directly contradicts the plan's own "each chart's container" wording.
- **Fix:** Split into `sleepTrendFailed` and `activityTrendFailed`. Four lines; test 19 pins the asymmetric case (one request fails, one succeeds → exactly one block, and it names the failed chart).
- **Files modified:** `hhs-frontend-v2/src/views/wellness/Dashboard.vue`
- **Commit:** `a051553`

**2. [Rule 2 - Missing critical functionality] `switchSession`'s throw had no catching caller in the plan's file list**

- **Found during:** Task 1, after converting `switchSession`'s toast to a throw
- **Issue:** The plan's `<action>` for Chat.vue names `onMounted`, `handleDeleteSession`, `showHistory` and `handleSendMessage` — not `handleSwitchSession`. But `switchSession` is one of the eight store sites, and leaving it throwing with no catcher produces exactly the T-14-37 unhandled rejection (inside a click handler) that the plan's threat register says to prevent.
- **Fix:** Wrapped `handleSwitchSession` in try/catch → `showError(error, 'ai.loadFailed', { presentStates: true })`, bringing the file's counts to 4/4.
- **Files modified:** `hhs-frontend-v2/src/views/ai/Chat.vue`
- **Commit:** `752623b`

**3. [Rule 1 - Bug] Test stub fired the click handler twice, doubling the toast count**

- **Found during:** Task 1, while debugging an unexplained extra toast
- **Issue:** The `el-button` stub declared neither `emits` nor passed `$event`. Without `emits`, the parent's `onClick` fell through as a native listener on the stub's root element *in addition* to the explicit `$emit`, so one click ran the handler twice; without `$event`, the parent's `@click.stop` threw on `stopPropagation`. Both were test-infrastructure defects, not product defects.
- **Fix:** Declared `emits: ['click']` and emitted `$event`. Also added the monotonic `Date.now` spy that `ErrorLayerSample.spec.ts:306-308` uses, because `showError`'s 2000ms VNode latch is module-level and cross-test, so two tests toasting the same text would silently zero the second one.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/ChatSendFailure.spec.ts`
- **Commit:** `752623b`

**4. [Rule 1 - Bug] `grep -c` acceptance criteria matched my own explanatory comments**

- **Found during:** Task 1's acceptance grep, and again in Task 2
- **Issue:** Three criteria are literal greps: `ElMessage.error` = 0 in `ai.ts`/`floatingAi.ts`, `return []` = 0 in `ai.ts`, `User cancelled` = 0 in `Chat.vue`. My comments explaining *why* those forms were removed quoted them, so the greps reported 1. Likewise `showError` = 0 in `Dashboard.vue` was matched by a comment saying the file has zero `showError`.
- **Fix:** Reworded the comments to describe the removed forms without reproducing the literal token. The criteria are checked literally by 14-13's gate, so the source has to satisfy the grep as written rather than be excused by a comment-stripping reading.
- **Files modified:** `stores/ai.ts`, `stores/floatingAi.ts`, `views/ai/Chat.vue`, `views/wellness/Dashboard.vue`
- **Commits:** `752623b`, `a051553`

**5. [Rule 3 - Blocking] Build failed on an unused import in the new spec**

- **Found during:** Task 1's `npm run build` (`noUnusedLocals` is on)
- **Issue:** `usePushStore` was imported in `StoreErrorContract.spec.ts` but never called — the push assertions are source-level only.
- **Fix:** Removed the import.
- **Files modified:** `hhs-frontend-v2/src/__tests__/stores/StoreErrorContract.spec.ts`
- **Commit:** `752623b`

## Auth Gates

None.

## Known Stubs

None. The two send-failure markers are deliberate in-place affordances, not placeholders: each is wired to a real `sendFailed` ref that the handler sets on `false` and clears on success or edit, and both are asserted in `ChatSendFailure.spec.ts`.

## Deferred Issues

- `stores/wellness.ts`'s `fetchLatest` still swallows silently (a pre-existing empty catch, not one of the eight sites). It feeds `latestMetrics`, which no view currently reads — `Dashboard.vue` calls it but derives everything from `summary`. Out of this plan's scope; logged here so the next reader does not mistake it for an oversight.
- `stores/ai.ts`'s `fetchRemainingCount` likewise swallows silently, and `Chat.vue`'s `fetchHealthInsights` has a bare `catch { /* keep null values */ }`. Both are pre-existing and outside the eight sites; the insight cards degrade to 暂无数据, which is a truthful reading rather than a false zero.

## Self-Check: PASSED

Files verified present: `stores/ai.ts`, `stores/floatingAi.ts`, `stores/wellness.ts`, `views/ai/Chat.vue`, `views/wellness/Dashboard.vue`, `components/ai-floating-ball/FloatingChatPanel.vue`, `__tests__/stores/StoreErrorContract.spec.ts`, `__tests__/views/ChatSendFailure.spec.ts`, `.planning/phases/14-error-rollout/14-LEDGER-12.md`. Commits verified: `752623b` (Task 1), `a051553` (Task 2).
