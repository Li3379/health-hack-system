---
phase: 14-error-rollout
plan: 13
subsystem: frontend
tags: [vue3, error-handling, structural-gate, residue-spec, migration-ledger, uat, phase-gate, regression-baseline]

# Dependency graph
requires:
  - phase: 14-error-rollout
    provides: "the four corrected structural checks as measured by 14-RESEARCH §'Contract 6.3's four checks — three need correction' (the originals are red for legitimate reasons)"
  - phase: 14-error-rollout
    provides: "plan 14-06's two retained pre-checks in user/Profile.vue::beforeAvatarUpload and plan 14-11's two in OcrInput.vue::handleFileChange — the four `保持` rows this gate allowlists"
  - phase: 14-error-rollout
    provides: "plan 14-12's per-file store evidence (ai 1, push 1, wellness 0 after addMetric's deletion, floatingAi 0) — the numbers check ② asserts rather than a summed total"
  - phase: 14-error-rollout
    provides: "the nine ledger fragments 14-LEDGER-04..12 that this plan merges and reconciles to 87"
provides:
  - "ResidueGate.spec.ts — 20 cases making all four structural checks part of `test:run`, so residue cannot return without a failing test rather than a remembered grep"
  - "check ① as a two-part allowlist assertion keyed by (path, fn, literal) with zero line numbers, so it still resolves after 14-06 and 14-11 inserted imports above both functions"
  - "14-MIGRATION-LEDGER.md — the 87-row merged ledger with a per-fragment reconciliation table and 14-14's inline-coverage audit kept as its own section"
  - "14-UAT.md — the 13-step browser script with per-sample wait times, expected readings, an evidence requirement on the backstop, and the two non-regression states"
  - "a recorded phase-gate run: backend 845/0, frontend 642/59, build green, lint 0 errors, checkstyle 0 violations, E2E 7 of 8 with the eighth's pre-existing cause identified"
affects: [14-14]

# Actuals (#2632) — pairs with the plan's estimate (78000 tokens, 2 tasks), same chars/4 scale
actuals:
  tokens: 19652
  tasks: 2
  commits: 2
  plan_head_before: 5f50bcb451228478814bef9df20c7b12c62dbfc2

tech-stack:
  added: []
  patterns:
    - "A gate that has never been red is a gate nobody has tested. Each structural check was mutation-tested by introducing the exact regression it claims to catch and confirming the spec fails, then restoring"
    - "Pin a source-level assertion to (path, enclosing function, literal), never to a line number, when an earlier wave in the same phase inserts imports above the target. A line-number pin recorded today is stale before the gate runs"
    - "A residue allowlist must be asserted in both directions: zero matches outside it, and every entry still present exactly once inside its named function. The second half is what stops 'zero residue' from being satisfied by deleting the real pre-checks"
    - "Correct a check whose literal expected value is unsatisfiable by the plan set that produces it, and write the authority for the correction into the gate itself, so the relaxation is a documented decision rather than gate erosion"

key-files:
  created:
    - "hhs-frontend-v2/src/__tests__/views/ResidueGate.spec.ts"
    - ".planning/phases/14-error-rollout/14-MIGRATION-LEDGER.md"
    - ".planning/phases/14-error-rollout/14-UAT.md"
  modified:
    - ".planning/WINDOWS.md"

key-decisions:
  - "Check ① asserts 87 → 4, not 87 → 0, and the correction is documented inside the spec rather than only in the summary. RESEARCH's inventory places all four survivors in its 'NOT a failure — client-side validation, keep with ledger reason' bucket, and Contract 6.2's `保持` disposition exists for exactly the case where converting would misreport a non-API failure. A gate demanding zero would be unsatisfiable by 14-06 and 14-11, and shipping it would train the team to ignore the gate. Only check ①'s literal expected value is relaxed; the intent — no bare toast on a user-visible API failure path — is preserved by the second half, which pins each survivor to its client-check literal inside its named function"
  - "The allowlist is keyed by (path, fn, literal) with no line numbers anywhere, because 14-06 and 14-11 both insert imports above the two functions before this wave-5 gate runs. Every line number recorded today is stale by the time the spec executes. The spec additionally asserts that no allowlist entry contains a `:NNN` suffix, so the constraint is enforced rather than merely observed"
  - "Filtering by enclosing function and literal, not by file, means adding a fifth bare call to Profile.vue — even inside beforeAvatarUpload with a different literal — still fails check ①. A file-level exclusion would have absorbed it. This was verified by mutation: injecting `ElMessage.error('文件为空')` into the already-allowlisted function turned the spec red"
  - "Check ② asserts per file (ai 1, push 1, wellness 0, floatingAi 0) and asserts auth.ts's 4 separately, rather than a summed 'exactly 3'. A summed assertion is simultaneously true of the live population of 7 and false of the phase's stated claim, and it would let someone 'clean up' a legitimate success toast to make check ① easier. wellness.ts is asserted as 0 because 14-12 deleted the dead addMetric along with its success toast; the spec also asserts the ElMessage import is gone, since a residual import is the next misuse's entry point"
  - "Check ③ matches `!== 'cancel'`, `!== 'close'`, `name === 'CanceledError'` and `name === 'AbortError'`, and deliberately does not match `=== 'cancel'`. The two `=== 'cancel'` sites (request.ts:65, showError.ts:139) consume an already-classified `kind` and are legitimate. Putting them in the pattern would make the exclusion list the only safety net, so a fourth file's equality comparison would surface as a false positive instead of a real residue. Three literal-path exclusions with a comment naming each one's responsibility; no wildcards"
  - "Check ④ matches form-item error selectors only and deliberately does not match `--el-color-danger`. CriticalConfirmDialog.vue uses the danger token four times on a non-form surface — a danger-zone confirmation — which is correct code. Broadening the pattern would make the check red for correct code, which is how a gate loses its authority. A second assertion proves the border block exists in theme.css, so the check proves the styling lives in its one permitted place rather than only proving absence elsewhere"
  - "The ledger reconciliation is asserted inside the spec, not left to the summary: 87 data rows, every row complete in all seven columns, all nine fragments present, and 14-14's audit rows excluded from the count. A silently truncated ledger is exactly the failure this plan exists to prevent, so it must fail a test rather than a review"
  - "14-14's inline-coverage audit is carried as a distinct section, not merged into the migration table. Its four forms already had zero bare toasts and never contributed to the 87; merging its rows would inflate the count past 87 and break the reconciliation it is meant to support. Its heading was demoted one level so it nests under the section rather than competing with the document title"
  - "The E2E failure is recorded as a pre-existing bad assertion in WINDOWS.md #8, with the evidence, rather than either masked or reported as a phase regression. The failing case asserts that the heart-rate input clamps 500 down to <=300; UniversalMetricDialog.vue has never bound `:max` on the value `el-input-number` (only `el-rate` carries `:max=\"5\"`), and the test is byte-identical to its form in the initial commit 13dd26b. `git show ac39437` confirms 14-07 changed only form-item/summary wiring, never that input block. The other 7 cases pass, both success-copy assertions among them, so the phase's contract is intact"

patterns-established:
  - "Mutation-test the gate: for each structural check, introduce the exact regression it claims to catch, confirm the spec fails, restore. Seven mutations were run (fifth bare toast, deleted pre-check, duplicated literal, store error toast, `!== 'cancel'`, `name === 'AbortError'`, local form-item selector) and all seven were caught"
  - "When a check's literal expected value conflicts with the plan set producing it, correct the value, cite the authority in the spec's own comments, and keep the intent pinned by a second assertion — rather than weakening the check to a range or deleting it"
  - "A backstop without recorded evidence does not count as passed. The UAT script states the measurement (line count, whether buttons wrapped) rather than an impression, because 'looks fine' is not a reading"
  - "Sampling rules belong in the script, not in the reader's memory. The 300ms `.el-message` entry transition is stated once up front and repeated in every toast-counting step, because reading at t=0 reports a false zero — the most likely way this UAT would produce a wrong answer"

requirements-completed: [ERR-03, ERR-05, ERR-07, ERR-08]

# Coverage metadata (#1602)
coverage:
  - id: G1
    description: "The bare-error-toast grep returns exactly four across frontend source excluding tests, and those four are the documented client-side pre-check allowlist — 87 → 4 with every survivor named by path, enclosing function and literal"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#①a. 允许清单之外零残留"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#①a2. 允许清单的四个三元组是本阶段已知的那四个（失败信息说明期望残留是 4，不是 0）"
        status: pass
      - kind: grep
        ref: "grep -rn 'ElMessage\\.error' hhs-frontend-v2/src --include=*.vue --include=*.ts | grep -v __tests__ | wc -l → 4"
        status: pass
    human_judgment: false
  - id: G2
    description: "Each of the four allowlisted survivors still matches its exact literal copy inside its named enclosing function, so a silent removal of a pre-check fails the gate rather than passing it, and no entry depends on a line number"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#①b. 四个允许条目各自仍在其命名函数内、且字面量恰好出现一次（删掉预检查也必须失败）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#①c. 允许清单与断言里不含任何行号（定位只能靠 path/fn/literal）"
        status: pass
      - kind: manual
        ref: "mutation harness: deleting `ElMessage.error('图片大小不能超过 2MB')` turned ①a2 and ①b red; duplicating the literal inside the same function turned ①b red"
        status: pass
    human_judgment: false
  - id: G3
    description: "The store grep returns zero matches for the error toast form, while the permitted success call sites survive per file (ai 1, push 1, wellness 0, floatingAi 0) and auth.ts's 4 are asserted separately as out-of-remit auth-flow messages"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#②a. src/stores 下所有 .ts 的 ElMessage.error 计数为 0"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#②b–②e. ai 1 / push 1 / wellness 0 / floatingAi 0，逐文件断言"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#②f. auth.ts —— 恰好 4 条，且这是认证流程提示（本阶段范围之外，单独断言、不并入上面四个文件）"
        status: pass
      - kind: grep
        ref: "grep -rn 'ElMessage\\.error' hhs-frontend-v2/src/stores --include=*.ts | wc -l → 0"
        status: pass
    human_judgment: false
  - id: G4
    description: "No view or component file compares an error against a cancel sentinel or an abort by name; the only such comparisons left are inside the three utility modules that own the recognition"
    requirement: ERR-08
    verification:
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#③a. 三个排除文件之外，四条取消模式全部归零"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#③b. 排除清单恰好三个字面路径，每个都注明职责（无通配）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#③c. 两处合法的 === 'cancel' 比较仍存在（它们是消费 kind，不是手写判断）"
        status: pass
    human_judgment: false
  - id: G5
    description: "No view file contains a local form-item error style; the only error styling lives in the global stylesheet, and the shared components' danger-token use on non-form surfaces stays legal"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#④a. .vue 文件（排除 __tests__）里没有 form-item 错误选择器"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#④b. 全局样式表确实包含字段错误描边块（证明样式存在，而不只是「别处没有」）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#④c. 危险区确认对话框仍合法使用 danger token（这是 ④ 不匹配该 token 的原因）"
        status: pass
    human_judgment: false
  - id: G6
    description: "The migration ledger has one row per bare call site and its total equals the pre-migration count of 87, with every row complete in all seven columns and all nine contributing fragments present"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#① 迁移台账的数据行数等于迁移前记录的 87"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#② 每一行七列都非空（完整性是机械断言，不是判断）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#③ 九份片段全部存在且被合并（缺一份要大声失败，不能只合并在场的那几份）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ResidueGate.spec.ts#④ 14-14 的内联覆盖审计独立成节，其行不并入 87 行计数"
        status: pass
    human_judgment: false
  - id: G7
    description: "The backend suite is green with at least 845 tests and zero failures"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "mvn -f hhs-backend/pom.xml clean verify -DskipITs → Tests run: 845, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS"
        status: pass
    human_judgment: false
  - id: G8
    description: "The frontend suite is green at or above the locked baseline, with build, lint and checkstyle passing"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "npm --prefix hhs-frontend-v2 run test:run → Test Files 59 passed, Tests 642 passed"
        status: pass
      - kind: integration
        ref: "npm --prefix hhs-frontend-v2 run build → built in 14.81s, exit 0 (vue-tsc -b + vite build)"
        status: pass
      - kind: integration
        ref: "npm --prefix hhs-frontend-v2 run lint:check → 211 problems (0 errors, 211 warnings)"
        status: pass
      - kind: integration
        ref: "mvn -f hhs-backend/pom.xml checkstyle:check → You have 0 Checkstyle violations; BUILD SUCCESS"
        status: pass
    human_judgment: false
  - id: G9
    description: "Formatting is deliberately not part of the gate, because it already fails on 98 files and adding it would make the gate red for a pre-existing reason"
    requirement: ERR-07
    verification:
      - kind: manual
        ref: "no gate command in this run invoked format:check; 14-VALIDATION.md and this plan both state the exclusion and its reason"
        status: pass
    human_judgment: false
  - id: G10
    description: "The E2E success-copy contract still passes"
    requirement: ERR-07
    verification:
      - kind: e2e
        ref: "npx playwright test e2e/quick-input.spec.ts → 7 passed, 1 failed. Both success-copy cases pass ('录入成功' at :52 and :108), as do the cancel case (:175) and the stats case (:200)"
        status: pass
      - kind: e2e
        ref: "the single failure (:150 'should validate value range for metrics') asserts a clamp UniversalMetricDialog has never implemented; byte-identical to commit 13dd26b and untouched by Phase 14 — recorded as WINDOWS #8"
        status: blocked
    human_judgment: true
  - id: G11
    description: "The cleanup UAT script covers every state the UI-SPEC requires, with a 300ms observation delay per sample so the toast transition is not misread as silence"
    requirement: ERR-07
    verification:
      - kind: manual
        ref: "14-UAT.md — 13 numbered steps each with page, action, wait time, DOM query and expected reading; the 300ms rule stated up front and repeated per toast-counting step; step 4 fixes a 10s interval over 60s; step 13 carries an evidence requirement"
        status: pending
    human_judgment: true
  - id: G12
    description: "A user-visible error added by this phase is never a retryable toast for a permission or a missing resource"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "ResidueGate.spec.ts#②a + ③a: no store error toast survives and no view hand-writes a cancel judgement, so 403/404 reach the state-block or presentStates channel rather than a generic retry toast"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#presentStates: true 时 notFound 只弹一条 duration 0 的 toast，无动作按钮 (14-01, still green)"
        status: pass
    human_judgment: false

# Phase-gate results (#T-14-41: real output, never an expectation)
phase_gate:
  - leg: "Backend full suite"
    command: "mvn -f hhs-backend/pom.xml clean verify -DskipITs"
    actual: "Tests run: 845, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS (1:04 min)"
    baseline: "≥845, 0 failures"
    status: pass
  - leg: "Backend checkstyle"
    command: "mvn -f hhs-backend/pom.xml checkstyle:check"
    actual: "You have 0 Checkstyle violations — BUILD SUCCESS (7.3s)"
    baseline: "0 violations"
    status: pass
  - leg: "Frontend suite"
    command: "npm --prefix hhs-frontend-v2 run test:run"
    actual: "Test Files 59 passed (59), Tests 642 passed (642), 14.73s"
    baseline: "≥622 tests (measured baseline after 14-11), 0 failures"
    status: pass
  - leg: "Frontend lint"
    command: "npm --prefix hhs-frontend-v2 run lint:check"
    actual: "211 problems (0 errors, 211 warnings)"
    baseline: "0 errors"
    status: pass
  - leg: "Frontend build"
    command: "npm --prefix hhs-frontend-v2 run build"
    actual: "vue-tsc -b clean, vite build ✓ built in 14.81s, exit 0"
    baseline: "exit 0"
    status: pass
  - leg: "E2E success-copy contract"
    command: "npx --prefix hhs-frontend-v2 playwright test e2e/quick-input.spec.ts"
    actual: "7 passed, 1 failed (23.0s). Passing: :24 metric buttons, :52 glucose submit, :83 blood pressure, :108 sleep submit, :130 required-field validation, :175 cancel, :200 today stats. Failing: :150 value-range — asserts a clamp never implemented; identical to commit 13dd26b, untouched by 14-07 (git show ac39437)"
    baseline: "success-copy contract preserved"
    status: partial
  - leg: "Structural check ① (residue)"
    command: "grep -rn 'ElMessage\\.error' hhs-frontend-v2/src --include=*.vue --include=*.ts | grep -v __tests__ | wc -l"
    actual: "4 — all four on the allowlist, each occurring exactly once inside its named function"
    baseline: "4 (87 → 4)"
    status: pass
  - leg: "Structural check ② (stores)"
    command: "grep -rn 'ElMessage\\.error' hhs-frontend-v2/src/stores --include=*.ts | wc -l"
    actual: "0"
    baseline: "0"
    status: pass
  - leg: "Ledger reconciliation"
    command: "row count over .planning/phases/14-error-rollout/14-MIGRATION-LEDGER.md"
    actual: "87 data rows, 0 incomplete, 9/9 fragments present, 14-14 audit excluded"
    baseline: "87"
    status: pass
  - leg: "format:check"
    command: "deliberately not run as a gate"
    actual: "n/a — fails on 98 pre-existing files; adding it would make the gate red for a reason this phase did not introduce"
    baseline: "excluded by 14-VALIDATION.md"
    status: not-applicable
---

# Phase 14 Plan 13: Structural gate and phase-gate run Summary

**The phase's four structural checks now fail tests instead of requiring a remembered grep, and the full gate is recorded as real output: backend 845/0, frontend 642 across 59 files, build and lint and checkstyle green, E2E success-copy contract intact.**

## What this plan did

Two tasks. Task 1 turned the four corrected structural checks into `ResidueGate.spec.ts` (20 cases) and merged nine ledger fragments into the 87-row `14-MIGRATION-LEDGER.md`. Task 2 ran every gate leg, recorded each command's real output, and wrote the 13-step `14-UAT.md`.

The headline number is **87 → 4**, and the arithmetic is stated in the spec so the per-plan retention decisions and the phase-end count describe one number:

| Stage | Count | Evidence |
|-------|-------|----------|
| live before the phase | 87 | RESEARCH's classified inventory |
| retained as `保持` | 4 | `Profile.vue::beforeAvatarUpload` ×2, `OcrInput.vue::handleFileChange` ×2 |
| **live after the phase** | **4** | each still matching its original literal inside its named function |

## The four checks, and why three of them are corrected

Three of Contract 6.3's originals are red for legitimate reasons. Shipping them unchanged would train the team to ignore the gate, so each was corrected and the correction is documented in the spec itself.

| Check | Original | Corrected | Why |
|-------|----------|-----------|-----|
| ① residue | `== 0` | `== 4` outside a 4-entry `{path, fn, literal}` allowlist, **plus** each survivor present exactly once in its named function | 14-06 and 14-11 deliberately retained four client-side file pre-checks; a zero-demanding gate is unsatisfiable by the plan set that produces it |
| ② stores | "only auth.ts messages" | zero error toasts, permitted survivors asserted **per file** (ai 1, push 1, wellness 0, floatingAi 0) with auth's 4 named separately | a strict no-message rule forbids success toasts the contract permits; a summed "exactly 3" is true of the live 7 and false of the claim |
| ③ cancel | `!== 'cancel'\|=== 'cancel'\|!== "cancel"` | four patterns (`!== 'cancel'`, `!== 'close'`, `name === 'CanceledError'`, `name === 'AbortError'`), three literal-path exclusions, `=== 'cancel'` **not** matched | the two `=== 'cancel'` sites consume an already-classified kind; an unexcluded pattern matches the utilities that must keep their comparisons |
| ④ style | includes `--el-color-danger` | form-item error selectors only, plus a positive assertion that the `theme.css` border block exists | `CriticalConfirmDialog.vue` uses the danger token four times on a non-form surface — correct code |

**No line numbers anywhere in check ①.** 14-06 and 14-11 inserted imports above both functions before this wave-5 gate runs, so every line number recorded today is stale. Pinning by `(path, fn, literal)` survives any insertion, and the spec asserts that no entry carries a `:NNN` suffix.

## The gate was mutation-tested, not assumed

A check that has never been red is a check nobody has tested. Seven mutations were introduced one at a time, the spec run, and the file restored:

| Mutation | Result |
|----------|--------|
| a fifth bare toast inside the already-allowlisted `beforeAvatarUpload` | caught (①a, ①a2) |
| delete a real pre-check (`图片大小不能超过 2MB`) | caught (①a2, ①b) |
| duplicate a pre-check literal inside the same function | caught (①b) |
| an `ElMessage.error` inside `stores/ai.ts` | caught (①a, ②a) |
| a `!== 'cancel'` comparison in `goals/Index.vue` | caught (③a) |
| a `name === 'AbortError'` comparison in `goals/Index.vue` | caught (③a) |
| a local `.el-form-item__error` rule in a view | caught (④a) |

**7 of 7 caught.** The first is the load-bearing one: it proves a file-level exclusion was not what made check ① pass.

## Ledger

`14-MIGRATION-LEDGER.md` merges fragments 04–12 in plan order: one title, one purpose paragraph, one seven-column header, then every fragment's rows with a source comment making the origin unambiguous.

| Fragment | Rows |
|----------|------|
| 14-04 | 3 |
| 14-05 | 8 |
| 14-06 | 9 |
| 14-07 | 5 |
| 14-08 | 5 |
| 14-09 | 12 |
| 14-10 | 22 |
| 14-11 | 15 |
| 14-12 | 8 |
| **Total** | **87** |

The reconciliation, the seven-column completeness check, the nine-fragment presence check and the 14-14 separation are all asserted **inside the spec**, because a silently truncated ledger is exactly the failure this plan exists to prevent and must fail a test rather than a review. 14-14's inline-coverage audit is carried as its own section under a demoted heading — its four forms already had zero bare toasts and never contributed to the 87, so merging them would break the reconciliation they support.

## Phase gate — recorded output

Every leg is in the frontmatter's `phase_gate` table with the real command output. Summary:

| Leg | Result |
|-----|--------|
| Backend suite | **845 tests, 0 failures, 0 errors** — BUILD SUCCESS |
| Backend checkstyle | **0 violations** — BUILD SUCCESS |
| Frontend suite | **642 tests / 59 files**, 0 failures |
| Frontend lint | **0 errors**, 211 warnings (pre-existing) |
| Frontend build | **green**, exit 0 |
| E2E success-copy | **7 of 8 pass**, both success-copy assertions among them |
| `format:check` | deliberately **not** a gate — fails on 98 pre-existing files |

Baselines moved as expected: the frontend grew from the plan's locked 445 to **642 across 59 files** (the plan's `≥622` post-14-11 figure is exceeded), and the backend holds at exactly **845**.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] E2E fixture account absent from the database**
- **Found during:** Task 2, first E2E run
- **Issue:** All 8 E2E cases failed at `e2e/quick-input.spec.ts:21` — the `beforeEach` login redirect — before any success-copy assertion ran. The spec hardcodes `admin`/`123456`, but `sys_user` contained only `demo_hhs`, `demo_hhs2`, `xiaojunhao`, `uat_v13`, `uat_phase12`. `POST /api/auth/login` returned `user.password_invalid`.
- **Fix:** Created the fixture account via `POST /api/auth/register` (`{"username":"admin","password":"123456"}` → `code: 200`), then confirmed login returns a token. Reran: 7 of 8 pass. This is environment bring-up, not a code change — no source file was modified.
- **Files modified:** none (database state only)

### Not fixed — recorded as a defect

**2. [Out of scope] E2E value-range case asserts an unimplemented clamp**
- **Found during:** Task 2, second E2E run
- **Issue:** `should validate value range for metrics` (:150) fills the heart-rate input with `500` and expects it to clamp to `<=300`. `UniversalMetricDialog.vue` has never bound `:max` on the value `el-input-number` — only the mood `el-rate` carries `:max="5"`.
- **Evidence it is not a phase regression:** the test is byte-identical to its form in the initial commit `13dd26b` (`git show 13dd26b:hhs-frontend-v2/e2e/quick-input.spec.ts`), and `git show ac39437` confirms 14-07 changed only form-item/summary wiring, never that input block.
- **Action:** recorded as **WINDOWS.md #8** (`unrun-verify`, open) with the full evidence, rather than masked or misreported as a phase regression. The phase's actual contract — both success-copy assertions — passes.
- **Why not fixed here:** the plan's scope is verification and documentation; the plan states "Verification work and documentation; no production code changes." Implementing a clamp would be a behaviour change outside this plan's remit and outside Phase 14's error-presentation scope.

### Intentional design deviation, documented in the plan

**3. Check ①'s expected value is 4, not Contract 6.3's literal 0.** Authority: RESEARCH's inventory places all four survivors in its "NOT a failure — client-side validation, keep with ledger reason" bucket, and Contract 6.2's `保持` disposition exists for the case where converting would misreport a non-API failure. Only the literal expected value is relaxed; the intent is preserved by the presence half of the assertion. This is not licence to add a fifth — half one fails on any non-allowlisted match, including a new one in an already-listed file, which was mutation-verified.

## Auth gates

None. No credential prompts were required; the E2E fixture account was created through the public register endpoint rather than by asking for input.

## Known Stubs

None. No stub patterns were introduced: the spec reads real source files and asserts on real content, the ledger carries real per-plan rows, and the UAT script contains no placeholder steps.

## Deferred Issues

- **WINDOWS.md #8** — the E2E value-range case asserts an unimplemented clamp (pre-existing, `open`). Not a phase regression; the success-copy contract passes.
- **WINDOWS.md #7** — 14-02's four computed-style checks remain browser-only; `14-UAT.md` now carries them as a dedicated appendix so the deferral has a destination.
- **Steps 1–13 of `14-UAT.md` are `pending` human execution.** They cannot run in CI by construction — they need a stopped backend, a real 60-second offline window, and real computed styles.

## Threat surface

No new security-relevant surface. The spec reads source files read-only; no `.env` was created or staged (the compose stack was already running from a prior session's environment). T-14-40 (gate erosion) is mitigated as designed: every exclusion is a literal triple or literal path with a naming comment, each check carries a positive assertion, and no wildcard, directory-prefix or line-number exclusion exists. T-14-41 (repudiation) is mitigated by recording real command output in the `phase_gate` table, including the one leg that did not fully pass.

## Self-Check: PASSED

Verified after writing:
- `hhs-frontend-v2/src/__tests__/views/ResidueGate.spec.ts` — exists, 20 cases green
- `.planning/phases/14-error-rollout/14-MIGRATION-LEDGER.md` — exists, 87 data rows
- `.planning/phases/14-error-rollout/14-UAT.md` — exists, 13 steps
- commits `89f5cae` and `408fde5` — both present on master
