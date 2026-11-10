---
phase: 14-error-rollout
plan: 11
subsystem: frontend
tags: [vue3, error-handling, showError, dead-code-deletion, data-input, ocr, prevention, cancel-silence]

# Dependency graph
requires:
  - phase: 14-error-rollout
    provides: "plan 14-05's presentation-channel rule — a file with no ErrorState passes presentStates true at every showError call"
  - phase: 14-error-rollout
    provides: "plan 14-01's showError with the zero-toast default for forbidden/notFound and the duration-0 VNode branch"
  - phase: 14-error-rollout
    provides: "plan 14-10's data-input precedent — the interceptor-reject deletion pattern and its ledger wording"
provides:
  - "OcrInput converted: one unreachable else deleted, the recognise/confirm catches routed through showError, the content-area failed state kept and built by one helper"
  - "OcrInput's two client-side pre-checks retained verbatim and ledgered as 保持 — 2 of the phase's 4-row residue allowlist"
  - "OcrHistory converted: both unreachable elses deleted, three handlers routed through showError reusing ai.* keys"
  - "DeviceConfigWizard's status load routed through showError with its panel values left untouched"
  - "prevention/Metrics' delete split into confirm-then-api, removing the phase's ninth and last hand-written cancel judgement including its dead close half"
  - "prevention/Risk converted with the profile.* keys reused; the plan-13 backfill call gains presentStates"
  - "OcrMigration.spec.ts (13 cases) and PreventionModuleMigration.spec.ts (10 cases) pinning the retention, the panel survival, the B1 toasts and the cancel silence"
  - "14-LEDGER-11.md with all 15 rows and per-row Contract 6.1 rationale"
affects: [14-12, 14-13]

# Actuals (#2632) — pairs with the plan's estimate (88000 tokens, 2 tasks), same chars/4 scale
actuals:
  tokens: 16539
  tasks: 2
  commits: 2
  plan_head_before: a320a9c7b3be9fe6f3327cdb377ff254677650ee

tech-stack:
  added: []
  patterns:
    - "A content-area failed state and a toast can be the same event with different lifetimes: derive both from one normalised error so they cannot disagree, rather than deleting the panel assignment"
    - "A retention decision is a deliverable. When a toast precedes any request, the ledger row with its reason — not the conversion — is what the phase needs"
    - "A phase-wide grep criterion is only satisfiable if historical comments stop quoting the old code. Reword the rationale in prose; do not delete the explanation"

key-files:
  created:
    - "hhs-frontend-v2/src/__tests__/views/OcrMigration.spec.ts"
    - "hhs-frontend-v2/src/__tests__/views/PreventionModuleMigration.spec.ts"
    - ".planning/phases/14-error-rollout/14-LEDGER-11.md"
  modified:
    - "hhs-frontend-v2/src/views/data-input/components/OcrInput.vue"
    - "hhs-frontend-v2/src/views/data-input/components/OcrHistory.vue"
    - "hhs-frontend-v2/src/views/data-input/components/DeviceConfigWizard.vue"
    - "hhs-frontend-v2/src/views/prevention/Metrics.vue"
    - "hhs-frontend-v2/src/views/prevention/Risk.vue"
    - "hhs-frontend-v2/src/views/goals/Index.vue"
    - "hhs-frontend-v2/src/views/mood/MoodTracker.vue"
    - "hhs-frontend-v2/src/views/screening/List.vue"

key-decisions:
  - "The two OCR pre-checks stay as raw ElMessage.error calls, and that is the correct outcome rather than an unfinished migration. They validate a File object the user just selected, before any request exists; routing them through showError would require fabricating an ApiError to satisfy a signature whose entire purpose is classifying server responses — mislabelling 'this file is too big' as 'the server rejected your request'. There is no el-form-item in OcrInput (grep -c is 0), so an inline field error would mean restructuring the upload control. They are pinned as (path, enclosing function, literal) triples because this plan's own import insertions shift their line numbers"
  - "OcrInput's recognise catch keeps its `recognizeResult.status = 'failed'` assignment. The panel is a persistent failed result users can read while deciding whether to retry; the toast confirms this one action did not complete. Deleting the assignment would replace a structured result panel with a bare transient banner. Only the message path changed"
  - "The panel shape is now built by one `failedResult()` helper, and that is what makes the plan's `grep -c \"status: 'failed'\" == 1` criterion true rather than requiring an AC amendment. Before the extraction the literal appeared twice (the reachable no-data else and the catch); both paths now share one definition. A side benefit: the panel message and the toast message are derived from the same normalised error, so T-14-32 ('panel and toast must not diverge') holds by construction rather than by discipline"
  - "Four historical comments were reworded to remove the greppable cancel literals. `goals/Index.vue`, `screening/List.vue`, `mood/MoodTracker.vue` and this plan's own `prevention/Metrics.vue` all quoted the retired comparison verbatim inside explanatory comments, which kept `grep -rnF \"!== 'cancel'\"` non-zero after the code was already correct. This follows the 14-08 and 14-09 precedent: preserve the rationale in prose ('compares two sentinel values', 'matches a cancelled request's error name'), drop the searchable literal"
  - "prevention/Metrics' delete is split rather than guarded. The single-try form could not distinguish a dialog rejection from an API failure, so the guard had to inspect the rejection value by hand — and the second sentinel it checked ('close') is never produced by ElMessageBox.confirm. Splitting makes the cancel a bare silent return (Contract 7 §7.2's second legal shape) and lets showError, which already recognises all five cancel forms, own the rest. The dead half disappears with the guard"
  - "Risk reuses profile.loadFailed / profile.saveFailed rather than inventing risk.*. The page is the risk surface of the health profile and shares one backend aggregate with prevention/Profile.vue; a risk.* pair would be a near-duplicate vocabulary. Recorded on both ledger rows"
  - "Risk's plan-13 showError call in backfillAndCreate gained presentStates. It is not a migration row (plan 13 delivered the call), but the page has no state block and backfill saving is a user-initiated action: a 403 or a 404 would have produced nothing. The validation → inline + summary split is unchanged"

patterns-established:
  - "One normalisation, two consumers: when a failure feeds both a content-area panel and a toast, resolve the message once and pass it to both. Recomputing it in each place is how the two drift apart"
  - "A retention spec asserts the absence of a request, not just the presence of a toast. `ElMessage.error` called once proves a message appeared; only `request.upload` never called proves the pre-check still short-circuits"
  - "When an AC demands a literal-grep of zero, the comments are part of the surface under test. Reword them before declaring the criterion met, or the grep reports a defect that no longer exists"

requirements-completed: [ERR-05, ERR-08]

# Coverage metadata (#1602)
coverage:
  - id: D1
    description: "The OCR file picker's size and type pre-checks keep their toasts and produce no request — client-side validation, not an API failure"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#12MB 文件：恰好一条提示，且一个识别请求都没发"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#非图片类型：恰好一条提示，且一个识别请求都没发"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#OcrInput 的两条前置校验仍按「函数 + 字面量」原位留在 handleFileChange 内"
        status: pass
    human_judgment: false
  - id: D2
    description: "A failed OCR recognition produces one toast while the panel keeps a failed state carrying the same message"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#识别失败：恰好一条提示，且面板仍是 failed 并带非空错误文案"
        status: pass
    human_judgment: false
  - id: D3
    description: "OcrInput's one unreachable else and OcrHistory's two are gone, completing the phase's twelve deletions"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#三个文件不再有「非 200」的不可达 else 兜底"
        status: pass
      - kind: grep
        ref: "grep -c 'res.message ||' hhs-frontend-v2/src/views/data-input/components/{OcrHistory,DeviceConfigWizard}.vue → 0 each"
        status: pass
    human_judgment: false
  - id: D4
    description: "OcrHistory's load, detail and delete handlers produce exactly one toast on failure and keep their success behaviour, reusing the ai.* keys"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#列表加载失败：一条提示，且表格保留原有行（不清空）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#OcrHistory 复用 ai.* 键而不是新造 ocr.* 近重复键"
        status: pass
    human_judgment: false
  - id: D5
    description: "Every showError call in these five files passes presentStates true, so a forbidden or notFound on a detail or delete action yields one sticky toast rather than silence"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#三个文件的每个 showError 都带 presentStates: true（无状态块 → 不许静默）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/PreventionModuleMigration.spec.ts#两个文件的每个 showError 都带 presentStates: true（无状态块 → 不许静默）"
        status: pass
    human_judgment: false
  - id: D6
    description: "Viewing or deleting an already-gone OCR record (RESOURCE_NOT_FOUND at OcrServiceImpl:691 and :727, AUTH_FORBIDDEN for a foreign record) produces exactly one toast, not silence"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#查看一条已被删除的 OCR 记录（RESOURCE_NOT_FOUND 信封）：恰好一条提示，弹窗保持打开"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#删除一条已被别人删掉的 OCR 记录（AUTH_FORBIDDEN 信封）：恰好一条提示，不是静默"
        status: pass
    human_judgment: false
  - id: D7
    description: "The device config wizard's status load produces one actionable toast on failure, and its panel values are not blanked"
    requirement: ERR-05
    verification:
      - kind: grep
        ref: "grep -c 'ElMessage.error' hhs-frontend-v2/src/views/data-input/components/DeviceConfigWizard.vue → 0; grep -c 'showError(' = grep -c 'presentStates: true' = 1"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#OcrHistory 与 DeviceConfigWizard 的 showError 计数与文件匹配"
        status: pass
    human_judgment: false
  - id: D8
    description: "prevention/Metrics loses its hand-written cancel comparison and the dead close half; its delete produces zero toasts on cancel and one on failure"
    requirement: ERR-08
    verification:
      - kind: unit
        ref: "src/__tests__/views/PreventionModuleMigration.spec.ts#取消删除确认框：零提示，且一个 DELETE 都没发"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/PreventionModuleMigration.spec.ts#确认后 DELETE 失败：恰好一条提示，文案是字典的 metric.deleteFailed"
        status: pass
      - kind: grep
        ref: "grep -c \"!== 'cancel'\" / \"!== 'close'\" hhs-frontend-v2/src/views/prevention/Metrics.vue → 0 / 0"
        status: pass
    human_judgment: false
  - id: D9
    description: "prevention/Risk's two handlers route through the shared exit using the profile keys, with the reuse recorded rather than a near-duplicate key invented"
    requirement: ERR-08
    verification:
      - kind: unit
        ref: "src/__tests__/views/PreventionModuleMigration.spec.ts#评估列表加载失败：恰好一条提示，文案解析到 profile.loadFailed 的字典条文"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/PreventionModuleMigration.spec.ts#风险页复用 profile.* 而非新造 risk.*"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/PreventionModuleMigration.spec.ts#风险页的 useFormServerErrors 接线仍然在（plan 13 交付，本 plan 只验证）"
        status: pass
    human_judgment: false
  - id: D10
    description: "No converted handler in this plan clears a value, closes a dialog or resets a form on failure"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#确认录入失败：恰好一条提示，且选中结果不被清空（ERR-05）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/PreventionModuleMigration.spec.ts#列表失败保留已有评估行（不清空）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/OcrMigration.spec.ts#列表加载失败：一条提示，且表格保留原有行（不清空）"
        status: pass
    human_judgment: false
  - id: D11
    description: "UI Considerations · error E7 list load: a failed prevention metrics load produces one toast and leaves the existing rows rather than blanking the table"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/PreventionModuleMigration.spec.ts#列表加载失败：一条提示，且表格保留原有行（不清空）"
        status: pass
    human_judgment: false
  - id: D12
    description: "UI Considerations · populated E7: every success confirmation in these files survives the conversion"
    requirement: ERR-05
    verification:
      - kind: grep
        ref: "ElMessage.success/warning still present: OcrInput 2 (请至少选择一个指标 warning + 成功录入 N 个指标), OcrHistory 1 (删除成功), DeviceConfigWizard 1 (配置已更新), prevention/Metrics 1 (删除成功), prevention/Risk 1 (评估创建成功)"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/PreventionModuleMigration.spec.ts#确认后 DELETE 失败：恰好一条提示，文案是字典的 metric.deleteFailed (asserts success not called on the failure path)"
        status: pass
    human_judgment: false
  - id: D13
    description: "Phase-wide: all nine hand-written cancel judgements are gone"
    requirement: ERR-08
    verification:
      - kind: grep
        ref: "grep -rnF \"!== 'cancel'\" | grep -rnF \"!== 'close'\" | grep -rnF \"=== 'CanceledError'\" | grep -rnF \"=== 'AbortError'\" hhs-frontend-v2/src --include=*.vue → no matches"
        status: pass
    human_judgment: false

# Threat surface
threat_flags: []

status: complete
---

# Phase 14 Plan 11: Data-input completion and the prevention module Summary

The data-input module closes with exactly two deliberate survivors, and the phase's last hand-written cancel judgement disappears with its dead half.

## Performance

- **Duration:** ~48 min
- **Started:** 2026-11-10T23:00:00Z (approx, first command)
- **Completed:** 2026-11-10T23:48:00Z
- **Tasks:** 2 of 2
- **Files modified:** 8 (5 source, 2 specs created, 1 new ledger; 3 comment-only rewords elsewhere)

## Accomplishments

The OCR surface is where D-01's "user-visible path" test finds its edge case, and the resolution is a documented retention rather than a forced conversion. `OcrInput.handleFileChange` checks the size and MIME type of the file the user just picked, before any request exists. Those two `ElMessage.error` calls stay, both of them, and their ledger rows say why in one sentence: a server-error classifier cannot honestly describe a client-side selection problem. They are the first two entries of the phase's four-row residue allowlist.

The same file's recognise handler keeps its failed-state assignment. `recognizeResult.status = 'failed'` gives the panel a persistent failed result with the error text inside it; the toast confirms that this attempt did not complete. Both survive because the migration changed the message path only. The panel's text is now derived from the same normalised error the toast uses, so the two cannot diverge — and extracting the panel shape into one `failedResult()` helper also made the plan's `status: 'failed'` count of 1 true rather than requiring an AC amendment.

`prevention/Metrics.vue` closes the phase's cancel count. Its guard compared two sentinels, the second of which a confirmation dialog never produces. Splitting confirm from request makes the cancel a bare silent return and removes the whole comparison, so all nine judgements from the research inventory are gone and Contract 6 check ③ can actually reach zero. `prevention/Risk.vue` converts with the `profile.*` keys reused rather than a `risk.*` pair invented, since it is the risk page of the profile and shares one backend aggregate.

The two specs assert behaviours a source-level grep cannot: zero requests behind each retained toast, the panel's `status` and message surviving alongside the toast, the exactly-one-toast outcome for a notFound detail and a forbidden delete, and the cancel path committing no DELETE at all.

## Task Commits

1. **Task 1: OCR components and the config wizard** — `5a8087a` (feat)
2. **Task 2: prevention/Metrics split and Risk conversion** — `13c455b` (feat)

## Files Created/Modified

- `hhs-frontend-v2/src/views/data-input/components/OcrInput.vue` — one unreachable else deleted, two catches converted, `failedResult()` helper added, both pre-checks untouched
- `hhs-frontend-v2/src/views/data-input/components/OcrHistory.vue` — two unreachable elses deleted, three handlers converted onto `ai.loadFailed` / `ai.deleteFailed`
- `hhs-frontend-v2/src/views/data-input/components/DeviceConfigWizard.vue` — status-load catch converted, panel values left alone on failure
- `hhs-frontend-v2/src/views/prevention/Metrics.vue` — load converted, delete split into confirm-then-api, two-sentinel guard gone
- `hhs-frontend-v2/src/views/prevention/Risk.vue` — two handlers converted onto `profile.*`, the backfill call gained `presentStates`
- `hhs-frontend-v2/src/views/goals/Index.vue`, `mood/MoodTracker.vue`, `screening/List.vue` — comment-only rewords (see Deviations 1)
- `hhs-frontend-v2/src/__tests__/views/OcrMigration.spec.ts` — new, 13 cases
- `hhs-frontend-v2/src/__tests__/views/PreventionModuleMigration.spec.ts` — new, 10 cases
- `.planning/phases/14-error-rollout/14-LEDGER-11.md` — new, 15 rows

## Decisions Made

See `key-decisions` in the frontmatter. The two that shaped the work most: retaining the pre-checks with recorded reasons instead of fabricating an `ApiError`, and extracting `failedResult()` so the panel shape is defined once rather than duplicated across two failure paths.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Four historical comments quoted the retired cancel literals, keeping the phase-wide grep red**

- **Found during:** Task 2, running the `<acceptance_criteria>` grep for `!== 'cancel'` / `!== 'close'`
- **Issue:** `goals/Index.vue:372`, `screening/List.vue:296` and `mood/MoodTracker.vue:290` each quoted the old comparison verbatim inside an explanatory comment, and this plan's own `prevention/Metrics.vue` comment did the same. The code in all four files was already correct, but the grep the AC requires to return no matches still hit them. Precedent exists in this phase: 14-08 and 14-09 hit exactly this and reworded.
- **Fix:** Reworded all four to prose that keeps the rationale without the searchable literal ("compares two sentinel values", "matches a cancelled request's error name", "the second sentinel a confirmation dialog never produces"). No behaviour touched; these are comment-only edits.
- **Files modified:** `goals/Index.vue`, `screening/List.vue`, `mood/MoodTracker.vue`, `prevention/Metrics.vue`
- **Commit:** `13c455b`

**2. [Rule 1 - Bug] The plan's `status: 'failed'` count of 1 was false until the panel shape was extracted**

- **Found during:** Task 1, first run of the OcrMigration spec
- **Issue:** The reachable no-data `else` and the catch each constructed the full panel object, so the literal appeared twice. The AC demands exactly 1 while also demanding the panel assignment survive — the two are only compatible if the shape is defined once.
- **Fix:** Extracted `failedResult(errorMessage)` and called it from both paths. The catch derives its argument via `resolveMessage('ocr.uploadFailed', normalized.message)`, which also makes panel-vs-toast divergence impossible (T-14-32). Count is now 1, and a spec asserts it.
- **Files modified:** `OcrInput.vue`, `OcrMigration.spec.ts`
- **Commit:** `5a8087a`

**3. [Rule 1 - Bug] My first source-level helper matched the template's event binding, not the function**

- **Found during:** Task 1, spec authoring
- **Issue:** `functionBody()` located a symbol by first occurrence, but `handleFileChange` also appears as `:on-change="handleFileChange"` in the template, so the extracted "body" was the template text and the assertion failed for the wrong reason.
- **Fix:** Anchored the search on the declaration shape (`const NAME = … => {`) and terminated at the next top-level `}`. The function now asserts the two literals inside the real function range, which is what "never by line number" was asking for.
- **Files modified:** `OcrMigration.spec.ts`
- **Commit:** `5a8087a`

**4. [Rule 1 - Bug] A bare HTTP 500 made the key-reuse assertion unfalsifiable**

- **Found during:** Task 2, first run of the PreventionModuleMigration spec
- **Issue:** The Risk list-failure case mocked `{ response: { status: 500 } }`, which the normaliser classifies as `server` at step 7 with its own message, so the toast read "服务暂时不可用，请稍后重试" regardless of the key passed. The test passed only by accident and could not detect a wrong key.
- **Fix:** Changed the mock to `{ response: { status: 200, data: { code: 500, message: '' } } }` — an envelope business failure with no backend message — so `resolveMessage` actually falls back to the dictionary. The assertion now reads the exact `profile.loadFailed` text and would fail if the key were wrong or invented.
- **Files modified:** `PreventionModuleMigration.spec.ts`
- **Commit:** `13c455b`

**5. [Rule 3 - Blocking] The ledger's supplementary row pushed the count to 16**

- **Found during:** Task 2, after writing `14-LEDGER-11.md`
- **Issue:** The `Risk.vue` backfill `presentStates` top-up is a real change but not one of the 15 inventory call sites; written as a table row it made the ledger 16 rows and broke the phase's reconciliation to 87.
- **Fix:** Converted it to prose under its own heading with an explicit "does not count toward the 15" note. The table is exactly 15 data rows, and the phase total reconciles: 3 + 8 + 9 + 5 + 5 + 12 + 22 + 8 + 15 = 87.
- **Files modified:** `14-LEDGER-11.md`
- **Commit:** `13c455b`

**Total deviations:** 5 auto-fixed (3 × Rule 1, 2 × Rule 3)
**Impact on plan:** No scope creep, no new dictionary keys, no behaviour change on any reachable path. Deviations 1 and 5 are bookkeeping the ACs themselves demanded; 2, 3 and 4 are corrections that made assertions actually bite. Deviation 4 is the one worth reviewing: the original mock would have hidden a wrong key, which is exactly the class of test that looks green and proves nothing.

## Issues Encountered

- **The plan's Residue count is 4 phase-wide, and after this plan exactly 2 remain outside these files.** `src/views/data-input/` sums to 2 (both allowlisted `OcrInput` pre-checks), down from 33. The other two are `user/Profile.vue` — owned by another plan, not touched here. Phase-wide bare `ElMessage.error` outside tests is now 4, and every one of them is an allowlisted client-side file validation.
- **`DeviceConfigWizard` has no dedicated migration spec.** The plan did not ask for one, and its single converted catch is covered by source-level count assertions plus the mount-time coverage it already receives through `DeviceSyncMigration.spec.ts`. Recorded here rather than silently omitted.
- **The `prevention/Risk` conversion needed one extra change the plan only implied.** The plan's read_first asked to "grep it and either add the argument or record why the existing call cannot receive forbidden/notFound". The plan-13 backfill call did receive both, so it got the argument. Documented in the ledger's prose section and in Decisions.

## User Setup Required

None — no external service configuration required. Zero packages installed.

## Next Phase Readiness

- **14-12 can proceed.** It owns the store-side presentation moves; this plan's data-input and prevention views now take no store-supplied toasts, so its conversion of `ai.ts` / `wellness.ts` / `push.ts` / `floatingAi.ts` has no remaining consumer in these files.
- **14-13's reconciliation should find 15 rows here**: 2 `保持`, 3 deletions, 10 `presentStates-toast`. The phase total is 87, and the deletion count closes at 12 = 14-10's 8 + 14-07's 1 + this plan's 3.
- **14-13's check ① should expect exactly 4 survivors**, pinned as triples: `(OcrInput.vue, handleFileChange, 文件大小不能超过10MB)`, `(OcrInput.vue, handleFileChange, 只能上传图片文件)`, and the two in `user/Profile.vue`. A line-number-based check will fail after this plan's import insertions.
- **14-13's check ③ should expect zero** for `!== 'cancel'`, `!== 'close'`, `=== 'CanceledError'` and `=== 'AbortError'` across `.vue` files — verified clean after the comment rewords.

## Known Stubs

None. Every converted path is wired to the real API and every retention is deliberate and ledgered.

## Self-Check

Files verified present: `14-LEDGER-11.md`, `OcrMigration.spec.ts`, `PreventionModuleMigration.spec.ts`, and all five converted views. Commits verified: `5a8087a`, `13c455b`. Full suite 58 files / 622 tests passing (baseline was 56 / 599). `npm run build` exits 0.

## Self-Check: PASSED

---
*Phase: 14-error-rollout*
*Completed: 2026-11-10*