---
phase: 14-error-rollout
plan: 10
subsystem: frontend
tags: [vue3, error-handling, showError, dead-code-deletion, data-input, device-sync, ai-parse]

# Dependency graph
requires:
  - phase: 14-error-rollout
    provides: "plan 14-05's presentation-channel rule — a file with no ErrorState passes presentStates true at every showError call"
  - phase: 14-error-rollout
    provides: "plan 14-01's showError with the zero-toast default for forbidden/notFound and the duration-0 VNode branch"
provides:
  - "DeviceSync converted: three unreachable elses deleted, seven catches plus the panel-plus-toast sync failure routed through showError"
  - "the bare 'User cancelled' catch at DeviceSync:530 split into a silent confirm and an api call that reports real failures"
  - "AIInput's private handleApiError retired along with its dead error.response peek and its 429 special case"
  - "AiHistory's load, detail and delete handlers routed through showError with presentStates, keeping the success toast and the dialog state"
  - "two source-level specs pinning the deletions as behaviour-preserving and the helper retirement as permanent"
  - "14-LEDGER-10.md with all 22 rows and per-row Contract 6.1 rationale"
affects: [14-11, 14-12, 14-13]

# Actuals (#2632) — pairs with the plan's estimate (92000 tokens, 2 tasks), same chars/4 scale
actuals:
  tokens: 22287
  tasks: 2
  commits: 3
  plan_head_before: 99eba6dd31a2921a8da8c07f8ca1e0e74a950df3

tech-stack:
  added: []
  patterns:
    - "A 'successful response reports a failed operation' branch is not an HTTP failure: construct an explicit business ApiError instead of passing the raw payload to showError, which would classify it as unknown"
    - "Delete an unreachable else by removing the else only and keeping the if body; the raw `} else {` count is not the acceptance metric because reachable if/else pairs share the literal"

key-files:
  created:
    - "hhs-frontend-v2/src/__tests__/views/DeviceSyncMigration.spec.ts"
    - "hhs-frontend-v2/src/__tests__/views/AiParseMigration.spec.ts"
    - ".planning/phases/14-error-rollout/14-LEDGER-10.md"
  modified:
    - "hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue"
    - "hhs-frontend-v2/src/views/data-input/components/AIInput.vue"
    - "hhs-frontend-v2/src/views/data-input/components/AiHistory.vue"

key-decisions:
  - "The eight deletions are behaviour-preserving, and the evidence is mechanical rather than argued: request.ts:45-52 rejects the promise whenever the envelope code is not 200, so an else after `if (res.code === 200)` can never execute. Deleting it is mandatory rather than cosmetic, because a catch-only migration would leave a permanently dead presentation path that a future reader would reasonably 'fix' by reinstating the toast"
  - "The acceptance metric for Task 1 is the ElMessage.error delta (11 to 0), not the `} else {` delta. The plan predicted 8 to 5; the realised count is 2, because action step 'collapse the offline branches' also removes the three offline if/else pairs whose `else` shared that literal. Three dead presentation branches plus three offline pairs is exactly 8 to 2. The two survivors are legitimate and unrelated: the sync result discriminator at :543 and the partial-failure discriminator at :600"
  - "The sync panel and the toast coexist deliberately. The panel is content-area state for this device's last sync result (persists until dismissed, carries a per-device retry); the toast confirms this action did not complete. They serve different lifetimes. If a reviewer judges it a duplicate, the correct resolution is to drop the toast (the panel already carries the retry), not the panel"
  - "DeviceSync:556 is a successful HTTP call reporting a failed sync, so it does not go through showError with the raw object — that would classify as unknown. It constructs `new ApiError(data.errorMessage || '同步失败', 'business')` and keeps the panel assignment. Recorded in the ledger as its own presentation path"
  - "AIInput's handleApiError existed only because pre-Phase-11 code peeked at error.response, which an ApiError does not have. All three of its behaviours were dead (the peek) or redundant (the 429 copy, since the backend already sends Chinese). Retiring it with two showError calls removes the helper, its four call sites and its special case at once. The two former call sites had different default messages, so they map to two keys (ai.parseFailed, metric.saveFailed) rather than one — no dictionary keys added"
  - "The offline branches collapse into the normaliser's network classification. isOnline stays because it still drives the offline alert banner and the online/offline listeners; what was removed is its use as a second source of truth for choosing failure copy. The sync panel's title now discriminates on the normalised kind rather than on a string match against 'Network Error'"
  - "AiHistory's detail dialog deliberately stays a toast rather than gaining a 404 content block. The dialog is 600px wide; a 480px block would dominate it. This is a recorded deferral, not an omission — a content-block treatment for dialogs belongs in a follow-up"
  - "The `} else {` assertion in the DeviceSync spec is stated as 'the removed shape is absent' via `res.code !== 200`, not by counting the literal. Counting the literal would also match the two reachable if/else pairs that legitimately remain"

patterns-established:
  - "Deletion evidence belongs in a source-level assertion plus a reachable-path assertion in the same spec: the first proves the dead shape is gone, the second proves nothing reachable was lost. Either alone is insufficient — a deletion-only spec cannot fail if someone breaks the catch, and a behaviour-only spec cannot tell deletion from migration"
  - "Assert total toast count, not just error toast count, in an offline-branch regression test. My first version counted only errors and passed against a deliberately reintroduced page-level branch that emitted a warning; switching to the total is what made the assertion bite"

requirements-completed: [ERR-05, ERR-07, ERR-08]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "DeviceSync's three unreachable else branches (:496, :528, :565) are deleted, not migrated — the interceptor rejects on any non-200 envelope"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/DeviceSyncMigration.spec.ts#不再有「非 200」兜底呈现：那个 else 在拦截器 reject 之后不可达"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/DeviceSyncMigration.spec.ts#连接受阻且无 response：即使页面 isOnline 为 false，仍只有一条提示（旧的页面级分支已删除）"
        status: pass
    human_judgment: false
  - id: D2
    description: "A failed device sync reports into the per-device sync panel and produces exactly one toast — the coexistence, with the panel carrying the retry and the toast confirming the action"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/views/DeviceSyncMigration.spec.ts#同步失败：恰好一条提示，且按设备面板同时被填充（面板与 toast 并存）"
        status: pass
    human_judgment: false
  - id: D3
    description: "The disconnect handler no longer reports a real API failure as a user cancellation — the confirm and the api call are separate, and only the confirm's catch is silent"
    requirement: ERR-08
    verification:
      - kind: unit
        ref: "src/__tests__/views/DeviceSyncMigration.spec.ts#断开连接的真实失败不再被当成「用户取消」：恰好一条提示"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/DeviceSyncMigration.spec.ts#断开连接的确认框被取消：零提示，且没有发出请求"
        status: pass
    human_judgment: false
  - id: D4
    description: "AIInput has no bespoke error helper, no error.response peek and no 429 special case; both its paths produce one toast whose copy comes from the backend"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/views/AiParseMigration.spec.ts#私有 handleApiError 已不存在"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/AiParseMigration.spec.ts#解析被 429 拒绝：恰好一条提示，且文案不是被退役的「请求过于频繁」静态句"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/AiParseMigration.spec.ts#解析失败且后端下发中文：toast 里是后端消息，不是字典兜底（回归护栏）"
        status: pass
    human_judgment: false
  - id: D5
    description: "Fetching the detail of a deleted AI-parse record (RESOURCE_NOT_FOUND) or deleting one (AUTH_FORBIDDEN) produces exactly one toast, not silence — the B1 case, with the dialog left open"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/views/AiParseMigration.spec.ts#查看一条已被删除的记录（RESOURCE_NOT_FOUND 信封）：恰好一条提示，弹窗保持打开"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/AiParseMigration.spec.ts#删除一条已被别人删掉的记录（AUTH_FORBIDDEN 信封）：恰好一条提示，不是静默"
        status: pass
    human_judgment: false
  - id: D6
    description: "UI Considerations · error E7 list load: a failed history load produces one toast and leaves the table's existing rows rather than blanking them"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/views/AiParseMigration.spec.ts#列表加载失败：一条提示，且表格保留原有行（不清空）"
        status: pass
    human_judgment: false
  - id: D7
    description: "Every showError call in the three files passes presentStates true, because none renders an ErrorState — the per-file grep equality between `presentStates: true` and `showError(`"
    requirement: ERR-07
    verification:
      - kind: other
        ref: "grep -c 'presentStates: true' equals grep -c 'showError(': 6=6 (DeviceSync), 2=2 (AIInput), 3=3 (AiHistory); grep -c 'ErrorState' is 0 in each"
        status: pass
    human_judgment: false
  - id: D8
    description: "The full frontend suite passes at or above the pre-task count and the production build succeeds"
    verification:
      - kind: integration
        ref: "npm --prefix hhs-frontend-v2 run test:run → 54 files / 571 tests passed (baseline was 52 / 551)"
        status: pass
      - kind: other
        ref: "npm --prefix hhs-frontend-v2 run build → vue-tsc -b && vite build succeeded"
        status: pass
    human_judgment: false
  - id: D9
    description: "14-LEDGER-10.md holds exactly 22 rows (DeviceSync 11, AIInput 6, AiHistory 5) with eight recorded as deletions and a Contract 6.1 rationale on every converted row"
    requirement: ERR-07
    verification:
      - kind: other
        ref: "grep -c '^| `' .planning/phases/14-error-rollout/14-LEDGER-10.md → 22; per-file row counts 11 / 6 / 5"
        status: pass
    human_judgment: false
  - id: D10
    description: "UI Considerations · populated E6: the sync panel and the toast coexist for a per-device sync failure without duplicating the same sentence"
    verification:
      - kind: unit
        ref: "src/__tests__/views/DeviceSyncMigration.spec.ts#同步失败：恰好一条提示，且按设备面板同时被填充（面板与 toast 并存）"
        status: pass
    human_judgment: true
    rationale: "The test proves both channels fire, not that the two sentences read as complementary rather than redundant to a human. The panel title names the device ('华为运动健康 同步失败') while the toast carries the failure copy, so they do not repeat each other — but judging whether that pairing reads well in the rendered page is a visual call the spec cannot make."

# Metrics
duration: 26min
completed: 2026-11-10
status: complete
---

# Phase 14 Plan 10: data-input module — DeviceSync, AIInput and AiHistory converted, eight unreachable branches deleted

**DeviceSync, AIInput and AiHistory now have zero bare `ElMessage.error` calls (down from 11, 6 and 5), eight unreachable branches are gone with the interceptor-reject reason recorded per row, and a bare catch that swallowed real disconnect failures as user cancellations is fixed.**

## Performance

- **Duration:** 26 min
- **Started:** 2026-11-10T22:22:00Z (approx)
- **Completed:** 2026-11-10T22:48:00Z (approx)
- **Tasks:** 2
- **Files modified:** 6 (3 components, 2 specs, 1 ledger)

## Accomplishments

- **The deletion claim is mechanical, not argued.** `hhs-frontend-v2/src/utils/request.ts:45-52` rejects the promise whenever the response envelope's `code` is not 200, so `if (res.code === 200) { … } else { … }` can never reach its `else`. The 12 dead `else` branches across the phase are unreachable code, and deleting them changes no reachable behaviour. This plan removed eight of them.
- **DeviceSync: 11 bare calls to 0.** Three dead elses deleted (`:496`, `:528`, `:565`), seven catches converted, and the offline branches collapsed into the normaliser's `network` classification. The `isOnline` flag survives because it drives the offline alert banner and the online/offline listeners — what was removed is its use as a second source of truth for choosing failure copy.
- **The silent catch is fixed.** `DeviceSync.vue:530`'s bare `catch { /* User cancelled */ }` wrapped both the confirm dialog and the API call, so a failed disconnect showed nothing. It is now split: the confirm's catch is a bare `return` (the one deliberate silence), and the API's catch reports through `showError`. This is the class of defect RESEARCH flagged as more dangerous than the eight `!== 'cancel'` guards, and the one Contract 6 check ③ could not find.
- **AIInput's helper is retired.** `handleApiError` peeked at `error.response.data.message`, but `normalizeApiError` returns an `ApiError` instance with no `response` property (`appError.ts:16-39`), so that branch never fired and the backend message was never once read. Its 429 special case duplicated a message the backend already sends in Chinese. Two `showError` calls replace the helper, its four call sites and its special case. Copy does not get worse — it gets better, because the backend message now wins for the first time.
- **Both B1 guards hold.** `AiParseServiceImpl:749` (detail) and `:783` (delete) throw `RESOURCE_NOT_FOUND`; `:776` throws `AUTH_FORBIDDEN`. Neither file renders an `ErrorState`, so without `presentStates: true` these would render nothing at all. Both paths are pinned by mutation-verified tests.
- **Panel and toast coexist on purpose.** A sync failure fills `lastSyncError` (content-area state, per-device retry, persists until dismissed) and emits one toast (this action did not complete). Different lifetimes, different jobs. The ledger records the reason and, if a reviewer disagrees, the correct fix: drop the toast, not the panel.
- **All 22 ledger rows written** with a Contract 6.1 rationale per row, including the one `DeviceSync:556` row that is a *successful* HTTP call reporting a failed sync.

## Task Commits

Each task was committed atomically:

1. **Task 1: DeviceSync deletions and conversion** — `ae979d4` (feat)
2. **Task 2: AIInput helper retirement and both history components** — `09e8188` (feat)
3. **Test hardening: total-toast assertion for the offline branch** — `b98b4bd` (test)

**Plan metadata:** `0826ac5` (docs: complete plan)

## Files Created/Modified

- `hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue` — three dead elses deleted; seven catches plus the panel-plus-toast path routed through `showError`; the `checkNetwork` gate and the offline branch removed; the disconnect confirm split from its API call; the now-unused `offlineMessage` constant removed.
- `hhs-frontend-v2/src/views/data-input/components/AIInput.vue` — `handleApiError` and its four call sites deleted; two dead elses deleted; `ai.parseFailed` and `metric.saveFailed` used at the two former call sites.
- `hhs-frontend-v2/src/views/data-input/components/AiHistory.vue` — two dead elses deleted; `loadHistory`, `viewDetail` and `handleDelete` routed through `showError` with `presentStates`; success toast and refetch intact.
- `hhs-frontend-v2/src/__tests__/views/DeviceSyncMigration.spec.ts` — 7 cases: panel-plus-toast coexistence, the offline-flag regression, cancel silence, real-failure reporting, and three source-level deletion assertions.
- `hhs-frontend-v2/src/__tests__/views/AiParseMigration.spec.ts` — 13 cases: the 429 copy retirement, backend-message precedence, confirm failure, both B1 guards, the list-preservation case, and four source-level helper-retirement assertions.
- `.planning/phases/14-error-rollout/14-LEDGER-10.md` — 22 rows plus a reconciliation section.

## Decisions Made

See `key-decisions` in the frontmatter. The three worth restating here:

1. **The `} else {` delta is 8 → 2, not 8 → 5.** The plan's acceptance criterion predicted 5, reasoning that only the three dead presentation branches would disappear. It missed that the action's own "collapse the offline branches" step removes three more `} else {` literals — the three offline `if/else` pairs at `:502`, `:577`, `:630`. Those pairs had to become single calls (the normaliser owns the classification), so their `else` keywords go with them. Eight minus three dead branches minus three collapsed pairs is two. Both survivors are legitimate and independent of this plan: the sync-result discriminator at `:543` and the partial-failure discriminator at `:600`. The meaningful assertion — `ElMessage.error` 11 → 0 — holds, and that is what proves both the deletions and the conversions landed.
2. **`res.message` fell by 4, not 3.** The same effect: the deleted outer `else` in `handleSync` also contained a panel assignment reading `res.message`, so it went with the branch. Only the reachable 400-not-configured check remains.
3. **The dialog keeps a toast, not a content block.** Deferred deliberately, with the reason recorded on the ledger row: a 480px block would dominate a 600px dialog.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `checkNetwork()` was a second source of truth that made offline failures unreachable-by-design**

- **Found during:** Task 1
- **Issue:** `handleConnect`, `handleSync` and `handleSyncAll` each began with `if (!checkNetwork()) return`, which fired a `ElMessage.warning` and returned early. That meant an offline attempt never reached the catch, so the catch's own offline branch was mostly dead — and if the flag raced, the user could see two messages for one attempt. The plan called for removing the page-level online check from the *copy selection*, which requires removing the early gate too, otherwise the normaliser's `network` classification could never be exercised.
- **Fix:** Removed `checkNetwork` and its three call sites, so an offline attempt now rejects inside the handler and reaches `showError`, which classifies `network` and attaches a retry. `isOnline` itself stays — it still drives the offline alert banner (`:21`) and the `online`/`offline` listeners.
- **Files modified:** `hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue`
- **Verification:** `DeviceSyncMigration.spec.ts#连接受阻且无 response：即使页面 isOnline 为 false，仍只有一条提示` — the flag is set false before the call and the total toast count must be 1. Mutation-verified: reintroducing `if (!isOnline.value) ElMessage.warning(…)` in the catch fails this case.
- **Committed in:** `ae979d4`

**2. [Rule 1 - Bug] The `offlineMessage` constant became unused and would fail the build**

- **Found during:** Task 1
- **Issue:** `tsconfig.json` sets `noUnusedLocals: true`, so removing the last read of `offlineMessage` would break `vue-tsc -b`.
- **Fix:** Removed the constant; its text lives in the dictionary as `network.offline` / `device.*Failed`, which is where the copy now comes from.
- **Files modified:** `hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue`
- **Verification:** `npm run build` succeeds.
- **Committed in:** `ae979d4`

**3. [Rule 1 - Bug] A test helper (`toastCount`) was unused in the AiParse spec and broke `vue-tsc`**

- **Found during:** Task 2, caught by `npm run build`
- **Issue:** I wrote a `toastCount()` helper mirroring the existing specs, but no case in the AiParse spec used it. `noUnusedLocals` applies to test files too.
- **Fix:** Deleted the helper rather than adding a gratuitous assertion just to read it.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/AiParseMigration.spec.ts`
- **Verification:** `npm run build` succeeds; spec still 13/13.
- **Committed in:** `09e8188`

**4. [Rule 1 - Bug] Two of my own comments contained the literals the acceptance greps must count to zero**

- **Found during:** Task 2, during acceptance verification
- **Issue:** Comments explaining "this file renders no `ErrorState`" and "pass `presentStates: true`" made `grep -c 'ErrorState'` and `grep -c 'presentStates: true'` return 1 and 4 instead of 0 and 3, breaking the intended per-file equality assertion. The same trap the plan warns about ("comment echoing a banned literal"), just with different literals.
- **Fix:** Reworded both comments to describe the rule without spelling the counted tokens ("状态块", "显式 opt-in 状态类呈现").
- **Files modified:** `hhs-frontend-v2/src/views/data-input/components/AIInput.vue`, `AiHistory.vue`
- **Verification:** `grep -c 'ErrorState'` → 0/0/0; `presentStates: true` equals `showError(` → 6=6, 2=2, 3=3.
- **Committed in:** `09e8188`

---

**Total deviations:** 4 auto-fixed (4 × Rule 1)
**Impact on plan:** All four were necessary for correctness or for the build to pass. No scope creep: no new features, no new dictionary keys, no changed behaviour on any reachable path. Deviation 1 is the one worth reviewing — removing the `checkNetwork` gate changes *when* the classification happens (at catch, not before the request), which is what the plan intended but did not spell out in the action text.

## Issues Encountered

- **My first version of the DeviceSync offline-regression test passed against a deliberate defect.** I mutation-tested it by reintroducing a page-level branch, and it stayed green — because I was counting only *error* toasts and the reintroduced branch emitted a *warning*. Switched the assertion to total toast count, re-mutated, and it failed as intended. Worth recording because the same mistake is easy to repeat: an offline branch can legitimately use `warning`, so `errorToastCount` is the wrong instrument for proving it is gone.
- **Two acceptance counts in the plan do not match reality, and reality is correct.** `} else {` lands at 2 (predicted 5) and `res.message` falls by 4 (predicted 3). Both explained under Decisions. The plan's own warning about this metric chose the right instrument — `ElMessage.error` 11 → 0 — which holds exactly.
- **Three pre-existing lint warnings remain** (`DeviceSync.vue:403`, `:419`, `AIInput.vue:167` — empty `catch (error)` blocks). Verified present at HEAD, so out of scope per the scope boundary; not fixed. Zero lint *errors* on all five changed files.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- **14-11 can proceed.** It owns the remaining three DEAD rows (`OcrInput` ×1, `OcrHistory` ×2) and will find the same interceptor-reject pattern; the DeviceSync and AIInput deletions here are its precedent, including the reasoning wording.
- **14-12's ledger-completeness check** should find 22 rows here, 8 of which are deletions, reconciling as 8 (this plan) + 1 (14-07) + 3 (14-11) = 12 = RESEARCH's DEAD bucket.
- **14-13's concatenation** should expect kebab-cased `file:line` refs against pre-migration line numbers, which have all shifted.
- **One item is deliberately deferred, not dropped:** a content-block treatment for the AI-parse detail dialog. It is recorded on the ledger row with its reason; a reviewer who wants it should open a follow-up rather than treat this as an oversight.

## Self-Check

Files verified present: `DeviceSyncMigration.spec.ts`, `AiParseMigration.spec.ts`, `14-LEDGER-10.md`, and the three components. Commits verified: `ae979d4`, `09e8188`, `b98b4bd`. Working tree clean for every file this plan touched.

## Self-Check: PASSED

---
*Phase: 14-error-rollout*
*Completed: 2026-11-10*
