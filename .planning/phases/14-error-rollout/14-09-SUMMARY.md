---
phase: 14-error-rollout
plan: 09
subsystem: ui
tags: [error-handling, cancel-semantics, err-08, err-07, present-states, split-confirm, dead-code-retirement, vue3, element-plus, vitest, health-module]

requires:
  - phase: 14-error-rollout
    plan: 01
    provides: "showError's presentStates option (default zero toast for forbidden/notFound), the cancel short-circuit via isCancel, and the duration-0 VNode branch"
  - phase: 14-error-rollout
    plan: 05
    provides: "the presentation-channel rule (no ErrorState in file → every showError passes presentStates true) and the canonical split confirm-then-api shape with a bare silent return"
  - phase: 11
    provides: "appError.ts's five-form cancel recognition (CANCEL_SENTINELS, CanceledError, AbortError, ERR_CANCELED) and the ApiError envelope classification; Phase 11's ApiError has no `response` property, which is what made the two peeks dead"

provides:
  - "health/Alerts.vue fully converted: five call sites on showError, two split-confirm handlers, the three banned 操作失败 copies eliminated"
  - "health/Metrics.vue fully converted: two call sites, the split-confirm delete, the dead 'close' half of the guard removed"
  - "health/Score.vue fully converted: five call sites including both report handlers, the hand-rolled cancel-name comparison replaced by shared recognition, both dead error.response peeks retired, and the abandoned abort controller's inertness documented in-code"
  - "four of the phase's nine hand-written cancel judgements retired (Alerts.vue:225, :240, Metrics.vue:200, Score.vue:505), replaced by one shared rule"
  - "HealthCancelSilence.spec.ts — 5 cases pinning cancel → zero toasts AND no request, plus failure → exactly one toast"
  - "HealthModuleToasts.spec.ts — 7 cases pinning one-toast-on-failure, backend-message-wins, the presentStates path, and zero toasts for a real ApiError cancel"
  - "14-LEDGER-09.md — twelve health rows with per-row Contract 6.1 rationale, four of them naming cancel in kind 覆盖"

affects: [14-12, 14-13, 14-14]

actuals:
  tokens: 12659
  tasks: 2
  commits: 2
  plan_head_before: 76bc65da863b0a47dbdd344e8f1a244d556d474f

tech-stack:
  added: []
  patterns:
    - "Split confirm-then-api applied four more times (see patterns-established — the shape is now the module-wide convention, cited from 14-05)"
    - "Inert-abort documentation rather than silent removal: an AbortController whose signal was never wired to the request keeps both its declaration and its .abort() call, with a comment stating the abort is a no-op and that wiring a signal is a behaviour change needing its own verification"
    - "Dead-peek retirement measured by copy improvement: the spec gives the mock a backend message distinct from the dictionary fallback, so the assertion fails against the old error.response expression — the improvement is proven, not asserted"
    - "The cancel case must use the post-Phase-11 shape: a real ApiError('', 'cancel') or an AbortError, never the bare string 'cancel', because the old hand-written guard happens to handle the string correctly and would leave the case vacuous"

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/HealthCancelSilence.spec.ts
    - hhs-frontend-v2/src/__tests__/views/HealthModuleToasts.spec.ts
    - .planning/phases/14-error-rollout/14-LEDGER-09.md
  modified:
    - hhs-frontend-v2/src/views/health/Alerts.vue
    - hhs-frontend-v2/src/views/health/Metrics.vue
    - hhs-frontend-v2/src/views/health/Score.vue

key-decisions:
  - "presentStates: true at every showError call in all three files. None renders an ErrorState (grep -c ErrorState is 0 in each), so a forbidden or notFound has no block to land on. HealthMetricServiceImpl:219 and WellnessServiceImpl:169 both throw AUTH_FORBIDDEN on these delete paths, so a bare call would turn a real permission failure into silence. The per-file equality between `presentStates: true` and `showError(` (5=5, 2=2, 5=5) is the correct shape of the assertion"
  - "Score.vue's regenerateReport name check was removed rather than repaired. Every failure reaching that catch is an ApiError whose name is 'ApiError', so `name === 'CanceledError' || name === 'AbortError'` was always false and a genuine cancel toasted. The comparison is redundant AND incorrect; showError's isCancel already returns early for all five cancel forms"
  - "The abandoned reportAbortController was deliberately NOT wired. scoreApi.generateReport() takes no signal parameter, so .abort() flips a flag nobody subscribes to. Wiring it would start actually cancelling in-flight report generation — a behaviour change requiring its own verification. Both the declaration and the .abort() calls are kept, with a comment at the declaration so no later actor mistakes the no-op for working cancellation protection. Recorded in the ledger's 理由 for both report rows"
  - "Score.vue:457 (recalculate) and :544 (fetchHistory) reuse score.loadFailed. No score.calcFailed or score.historyFailed key exists, and '无法加载健康评分，请点击重试' is apt for a recalculation failure (its whole purpose is to obtain a new score). A near-duplicate key would require changing the dictionary and its count assertion in the same commit for a wording-only gain. Both reuses are recorded in the ledger"
  - "Alerts.vue:200/210/225 share alert.updateFailed. All three are 'a status write against one alert'; the backend's own Chinese message distinguishes the details. Recorded in the ledger"
  - "The 500-response failure cases in the specs use an envelope business failure with an empty backend message (HTTP 200 + code 500 + message '') rather than a transport-level 500. A transport 500 classifies as `server` and carries its own message ('服务暂时不可用，请稍后重试'), which would pre-empt the dictionary lookup the assertion is meant to pin. The envelope form reaches resolveMessage, so the dictionary key is what gets asserted"
  - "Comment text was reworded to avoid the literal grep patterns. The ACs require `grep -c \"!== 'cancel'\\|!== 'close'\\|=== 'CanceledError'\\|=== 'AbortError'\"` and `grep -c 'error.response'` to report 0, and a comment quoting the old code would keep them red. The rationale survives in prose ('the second sentinel value', 'read the response body's message field') without the greppable literals"

patterns-established:
  - "Cancel-silence is proven by two assertions, not one: zero toasts AND the API was not called. The second half is what distinguishes a genuinely silent cancel from a cancel that silently swallowed a request. Every cancel case in HealthCancelSilence.spec.ts carries it"
  - "A cancel case must feed the shape the old guard mishandled. The string 'cancel' passes against the old `e !== 'cancel'` guard, so a string-based case proves nothing. AbortError (for confirm dialogs) and ApiError('', 'cancel') (for normalised request failures) are the two shapes that make the reinstated guard fail"
  - "Retiring a dead expression is proven by making its absence observable: give the mock a backend message that differs from the dictionary fallback and assert the toast contains it. Against the old `error.response` expression the fallback always wins, so the assertion fails — the copy improvement is measured, not claimed"
  - "An inert mechanism is documented, not deleted. Deleting only the variable would leave a dangling .abort() call; deleting both would erase the evidence that someone intended cancellation. Keeping both with a comment that names the missing signal wiring preserves the intent while preventing a false belief that it works"

requirements-completed: [ERR-08, ERR-07]

coverage:
  - id: D1
    description: "Cancelling a delete confirmation on the alerts or metrics page produces zero toasts and issues no delete request — the hand-written cancel comparison is gone, replaced by a split confirm-then-api shape"
    requirement: ERR-08
    verification:
      - kind: integration
        ref: "src/__tests__/views/HealthCancelSilence.spec.ts#取消删除确认框：零提示，且一个 DELETE 都没发 (asserts toastCount() === 0 AND deleteAlert not called; moving the request before the confirm fails it — mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -c \"!== 'cancel'\" hhs-frontend-v2/src/views/health/Alerts.vue → 0; grep -c \"!== 'cancel'\\|!== 'close'\" hhs-frontend-v2/src/views/health/Metrics.vue → 0"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/views/health/Alerts.vue:214-232 and :234-252; src/views/health/Metrics.vue:189-208 — the ElMessageBox await sits in its own try whose catch is a bare return with a comment stating the silence is deliberate (Contract 7 §7.2)"
        status: pass
    human_judgment: false
  - id: D2
    description: "Cancelling the mark-all-read confirmation produces zero toasts; a genuine failure of the same action produces exactly one toast — the same handler proves both halves"
    requirement: ERR-08
    verification:
      - kind: integration
        ref: "src/__tests__/views/HealthCancelSilence.spec.ts#取消「全部标记已读」：零提示，且 markAllAsRead 没被调用 and #确认后 markAllAsRead 失败：恰好一条提示，成功确认仍然只有成功路径才有 (restoring the old single-try guard fails both — mutation-verified)"
        status: pass
    human_judgment: false
  - id: D3
    description: "The score page's report regeneration no longer compares an error's name against cancelled-request names; a user-initiated abort and a real failure are distinguished by the shared cancel recognition instead"
    requirement: ERR-08
    verification:
      - kind: integration
        ref: "src/__tests__/views/HealthModuleToasts.spec.ts#报告生成被取消（真实 ApiError('', 'cancel')）：零提示（ERR-08） — restoring the old name comparison fails it (mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -c \"=== 'CanceledError'\\|=== 'AbortError'\" hhs-frontend-v2/src/views/health/Score.vue → 0"
        status: pass
    human_judgment: false
  - id: D4
    description: "The score page's two report handlers no longer read a response object off the error; the backend message now wins where previously the fallback always won"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/HealthModuleToasts.spec.ts#报告生成失败：toast 里是后端下发的消息，不是字典兜底（回归护栏） — the mock message differs from the dictionary copy, so the old error.response expression fails it (mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -c 'error.response' hhs-frontend-v2/src/views/health/Score.vue → 0"
        status: pass
    human_judgment: false
  - id: D5
    description: "Every one of the twelve converted call sites in this module keeps a success toast intact — Alerts, Metrics and Score each still confirm their successful actions"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "ElMessage.success count unchanged from before the plan: Alerts.vue 4, Metrics.vue 1, Score.vue 2 (compared against git show HEAD~2 for each file)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/HealthModuleToasts.spec.ts#报告生成成功：成功提示仍在（happy path 未被迁移删掉）; #指标删除失败：恰好一条提示，成功路径的成功提示没有误弹"
        status: pass
    human_judgment: false
  - id: D6
    description: "A list load failure on Alerts or Metrics renders exactly one actionable toast; no page renders a state block and a toast for the same load"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/HealthModuleToasts.spec.ts#指标列表加载失败：恰好一条提示，文案是字典里可执行的下一句 (text is '无法加载指标记录，请点击重试')"
        status: pass
      - kind: grep
        ref: "grep -c 'ErrorState' → 0 in Alerts.vue, Metrics.vue and Score.vue, so no state block can co-exist with a toast"
        status: pass
    human_judgment: false
  - id: D7
    description: "No converted handler clears a value or closes anything on failure"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/health/Alerts.vue and Metrics.vue — no catch calls resetFields/resetField/Object.assign(form, {}); the table rows stay as they were, and the only dialog close on the score page is the pre-existing reportVisible = false when a cached report cannot be fetched (it never had content to preserve)"
        status: pass
    human_judgment: false
  - id: D8
    description: "Every showError call in all three files passes presentStates true, because none of them renders an ErrorState block: a forbidden or notFound classification yields exactly one sticky toast rather than silence — HealthMetricServiceImpl:219 and WellnessServiceImpl:169 provably return AUTH_FORBIDDEN on these delete paths"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c 'presentStates: true' equals grep -c 'showError(' per file: Alerts 5=5, Metrics 2=2, Score 5=5; grep -c 'ErrorState' is 0 in each"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/HealthCancelSilence.spec.ts#删除一条已被别人删掉的预警（notFound 信封）：仍恰好一条提示，不是静默 — dropping the third argument fails it (mutation-verified)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/HealthModuleToasts.spec.ts#指标列表加载遇到 forbidden（AUTH_FORBIDDEN 信封）：仍恰好一条提示，不是静默 and #评分加载失败（notFound 信封）：仍恰好一条提示，不是静默 — dropping presentStates on either file fails its case (mutation-verified)"
        status: pass
    human_judgment: false
  - id: D9
    description: "The three '操作失败' copies are gone from the alert page, replaced by copies that name what failed and what to do next"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c '操作失败' hhs-frontend-v2/src/views/health/Alerts.vue → 0 (was 3)"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/views/health/Alerts.vue — markRead/acknowledge/markAllRead now resolve through alert.updateFailed ('无法更新预警状态，请稍后重试'); handleDelete through alert.deleteFailed ('无法删除该预警，请稍后重试')"
        status: pass
    human_judgment: false
  - id: D10
    description: "UI Considerations · error E7 list load: a failed alerts or metrics load produces one toast and the table keeps whatever it had; it does not silently blank"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/health/Alerts.vue:164-183 and src/views/health/Metrics.vue:140-161 — the catch only calls showError; `alerts.value` / `metrics.value` are assigned inside the try, so a rejection leaves the previous array untouched"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/HealthModuleToasts.spec.ts#指标列表加载失败：恰好一条提示，文案是字典里可执行的下一句 (the page mounts, load rejects, one toast appears and the component stays mounted)"
        status: pass
    human_judgment: false
  - id: D11
    description: "UI Considerations · populated E7: a successful action still shows its success confirmation; the error migration does not remove the happy path"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/HealthModuleToasts.spec.ts#报告生成成功：成功提示仍在（happy path 未被迁移删掉） (ElMessage.success called exactly once, zero error toasts)"
        status: pass
      - kind: grep
        ref: "ElMessage.success counts unchanged: Alerts 4, Metrics 1, Score 2"
        status: pass
    human_judgment: false
  - id: D12
    description: "UI Considerations · zero-one-many E6: repeating the same failed action within two seconds yields one toast plus a repeat marker, not three stacked toasts"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/utils/showError.ts:55 (DUPLICATE_WINDOW_MS = 2000) and :212 (isDuplicateVNodeToast latch) plus :200 (grouping: true for the string branch) — inherited from 14-01; this plan adds no new presentation channel and passes no opts that bypass the latch"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/ErrorLayerSample.spec.ts#4. 连续两次网络失败：VNode 闩锁把重复提示压到 1 条（<= 1） (pre-existing coverage of the same latch, unchanged by this plan)"
        status: pass
    human_judgment: false
  - id: D13
    description: "The abandoned abort controller is left in place with an explicit note that its abort is a no-op, so nobody later believes cancellation protection is proven"
    requirement: ERR-08
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/health/Score.vue:425-433 — the declaration carries a comment naming the missing signal wiring, the fact that .abort() flips an unsubscribed flag, and the requirement that wiring a signal needs its own verification"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/api/score.ts:generateReport() takes no parameters — the evidence that no signal can reach the request"
        status: pass
    human_judgment: false

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The dictionary-fallback assertions were masked by the transport-500 message**

- **Found during:** Task 1, first run of `HealthCancelSilence.spec.ts`
- **Issue:** Two cases rejected with `{ response: { status: 500, data: {} } }` and asserted the toast text equalled `alert.deleteFailed` / `alert.updateFailed`. Both failed with `服务暂时不可用，请稍后重试`. A transport 500 classifies as `server` (`appError.ts:165-175`), which carries its own message; `resolveMessage` therefore never falls back to the dictionary, so the assertion was testing the wrong layer.
- **Fix:** Both cases now reject with an envelope business failure whose backend message is empty (`{ status: 200, data: { code: 500, message: '' } }`). That reaches step 8, classifies as `business` with an empty message, and lets `resolveMessage` pick the dictionary key — so the assertion pins the key rather than the `server` copy.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/HealthCancelSilence.spec.ts`
- **Commit:** `c11bf83`

**2. [Rule 2 - Test quality] A presentStates mutation passed silently until a state-classified case existed**

- **Found during:** Task 1, mutation pass (dropping `{ presentStates: true }` from `alert.deleteFailed` left all 4 cases green)
- **Issue:** Every case used a failure that classifies as `business` or `server`, both of which toast regardless of `presentStates`. Nothing exercised a `forbidden` or `notFound`, so the third argument — the whole reason it is there — was unpinned.
- **Fix:** Added `删除一条已被别人删掉的预警（notFound 信封）` to `HealthCancelSilence.spec.ts` and `指标列表加载遇到 forbidden（AUTH_FORBIDDEN 信封）` / `评分加载失败（notFound 信封）` to `HealthModuleToasts.spec.ts`. Dropping `presentStates` now fails exactly one case per file, verified.
- **Files modified:** both spec files
- **Commit:** `c11bf83`, `eb527cc`

**3. [Rule 2 - Correctness] The AC greps matched comment text, not just code**

- **Found during:** Task 2, acceptance grep
- **Issue:** `grep -c "!== 'close'"` returned 1 for `Metrics.vue` and `grep -c 'error.response'` returned 2 for `Score.vue` — all three hits were inside explanatory comments quoting the old expressions. The plan's acceptance criterion requires 0.
- **Fix:** Reworded the comments to preserve the rationale in prose without the greppable literals ("the second sentinel value", "read the response body's message field", "compares two cancelled-request names by hand"). The explanation is intact; the greps are clean.
- **Files modified:** `hhs-frontend-v2/src/views/health/Metrics.vue`, `hhs-frontend-v2/src/views/health/Score.vue`
- **Commit:** `eb527cc`

### Deliberate-plan divergences (documented, not auto-fixes)

**1. The ledger's `原文案` cells for the two `Score.vue` report rows quote the full expression, not just the literal.** Contract 6.1 asks for a verbatim copy of the pre-migration literal. Those two sites had no single literal — the toast argument was `error.response?.data?.message || '<fallback>'`. The cell quotes both halves with `或` between them rather than inventing a literal that never existed. The escaped pipes were also dropped so every row stays a clean seven-column markdown row.

**2. `recalculate` and `fetchHistory` reuse `score.loadFailed`; `markRead`/`acknowledge`/`markAllRead` share `alert.updateFailed`.** The plan's action text specified these keys, so this is execution as written, recorded here only so the merge step in 14-13 sees the reuses called out in the ledger's `理由` column rather than discovering them.

## Auth Gates

None. Zero packages installed, no credentials needed, no checkpoint reached.

## Known Stubs

None. All three pages call real APIs; nothing renders placeholder or empty-to-UI data.

One **inert mechanism** is recorded rather than fixed: `Score.vue`'s `reportAbortController`. Its `.abort()` calls never interrupt anything because `scoreApi.generateReport()` accepts no `signal`. This is documented at the declaration and in the ledger's `理由` for both report rows. It is not a stub — no user-visible surface depends on it — but it is a claim a reader could wrongly infer ("cancellation works"), which is why the comment names it explicitly. Wiring it is a behaviour change with its own verification burden, deliberately out of scope.

## Threat Flags

None new. T-14-26 (cancel vs failure conflation) is mitigated exactly as planned: the split shape branches the two at the source, each cancel branch carries an explicit comment, and the specs assert the API was not called so a "silent" cancel cannot hide a dropped request. T-14-27 (score/report information disclosure) holds — copy comes from the shared resolver, no stack, no response body, no raw `error.message` passthrough; the two removed `error.response` peeks were the only places that could have leaked a response body and both are gone. T-14-28 (alert copy XSS) holds — `showError` builds text nodes, no `v-html` added. T-14-SC is n/a: zero packages installed.

---

## What changed, in one paragraph

The health module's twelve bare error toasts are gone. Four of the phase's nine hand-written cancel judgements lived in these same handlers, so they went in the same pass: `Alerts.vue`'s two `if (error !== 'cancel')` wrappers and `Metrics.vue`'s `!== 'cancel' && !== 'close'` are replaced by the split confirm-then-api shape, where the confirmation's own catch is a bare silent return and the request's catch goes straight to `showError` — which already recognises all five cancel forms. `Score.vue`'s guard was worse than redundant: it compared an `ApiError`'s name against two cancelled-request names that could never match, so a real cancel toasted and a real failure looked identical. That comparison is gone, and so are the module's last two `error.response?.data?.message` peeks, which had always fallen through because `ApiError` has no `response` — the backend's message now wins for the first time. Both report handlers keep their success toast; the abandoned abort controller stays in place with a comment saying its abort is a no-op, so no one later reads this file as proof that report generation can be cancelled.

## Verification evidence

| Check | Command | Result |
|-------|---------|--------|
| Cancel-silence spec | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/HealthCancelSilence.spec.ts` | 5 passed |
| Module-toasts spec | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/HealthModuleToasts.spec.ts` | 7 passed |
| Full suite | `npm --prefix hhs-frontend-v2 run test:run` | 52 files / 551 tests passed (baseline 50 files / 539 tests → +2 files, +12 tests) |
| Build + type-check | `npm --prefix hhs-frontend-v2 run build` | `✓ built in 9.98s`, no TS errors (the >500 kB chunk warning is pre-existing) |
| Bare toasts | `grep -c 'ElMessage.error'` on Alerts / Metrics / Score | 0 / 0 / 0 |
| Hand-written cancel guards | `grep -c "!== 'cancel'\|!== 'close'\|=== 'CanceledError'\|=== 'AbortError'"` | 0 / 0 / 0 |
| Dead response peeks | `grep -c 'error.response'` on Score.vue | 0 |
| presentStates parity | `grep -c 'presentStates: true'` vs `grep -c 'showError('` | 5=5, 2=2, 5=5 |
| ErrorState absence | `grep -c 'ErrorState'` on all three | 0 / 0 / 0 |
| Banned copy | `grep -c '操作失败'` on Alerts.vue | 0 (was 3) |
| Success toasts preserved | `grep -c 'ElMessage.success'` vs `git show` baseline | 4=4, 1=1, 2=2 |
| Phase-wide grep | `grep -rn "!== 'cancel'\|!== 'close'\|=== 'CanceledError'" hhs-frontend-v2/src/views/health/` | no matches |
| Ledger | row count / column count / non-empty `spec 断言` | 12 rows, 7 columns each, all `spec 断言` non-empty, 4 cancel rows name `cancel` |

Mutation checks performed (each broke exactly the intended case, then the file was restored):
restored the old single-`try` guard in `markAllRead` (2 cases failed), moved `deleteAlert` before the
confirm (2 cases failed), dropped `presentStates` on `alert.deleteFailed` (1), on `metrics` load (1),
on `score` load (1), restored the old `regenerateReport` catch with the name comparison and the
`error.response` peek (2 cases failed — the backend-message case and the cancel case).

## Self-Check: PASSED

- All 6 declared files exist (3 views, 2 specs, 1 ledger) plus this summary.
- Both work commits present in history: `c11bf83`, `eb527cc`.
- Commit count measured as `git rev-list --count 76bc65d..HEAD` at the moment the summary was
  written, **excluding this summary's own `docs(14-09)` commit** — the same convention 14-05 used.
- Token actual is `chars/4` over `git diff 76bc65d..HEAD` (50,636 chars → 12,659), on the same
  scale the plan's 80,000-token estimate used.
