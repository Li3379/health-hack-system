---
phase: 14-error-rollout
plan: 14
subsystem: ui
tags: [error-handling, form-validation, inline-errors, auth-forms, per-channel-projection, element-plus, vue3, vitest, present-states, ledger]

requires:
  - phase: 14-error-rollout
    plan: 01
    provides: "showError's presentStates option (zero-toast default for forbidden/notFound) and the duration-0 VNode branch both kinds reach"
  - phase: 14-error-rollout
    plan: 02
    provides: "the global .el-form-item.is-error border declaration — this plan writes no local error styling anywhere"
  - phase: 14-error-rollout
    plan: 03
    provides: "collectInvalidFieldMessages as the shared client-validation collector, and the FIELD_LABELS table that already carries username/password/confirmPassword/email/nickname"
  - phase: 14-error-rollout
    plan: 05
    provides: "the presentStates rule ('no ErrorState in this file → pass it at every showError call') and the canonical reset → validate → client-summary → const err = showError(...) → conditional apply submit shape both auth forms now mirror"

provides:
  - "auth/Login.vue wired: summary block as the form's first child, prop+data-field+:error on username and password, clearField on change, the generic warning toast replaced by a client-failure summary, and the server catch converted to the return-and-apply shape with presentStates"
  - "auth/Register.vue wired on the same shape across four items; the client-side validateConfirmPassword rule preserved untouched and the payload left exactly as it was (nickname still omitted, email still conditional)"
  - "user/PushSettings.vue per-channel projection: one composable instance whose error map is keyed by a channel-qualified composite, with a per-channel summary filtered by that prefix and a labels map that resolves each composite back to getConfigLabel's own wording"
  - "stores/push.ts saveError ref (mirroring loadError) carrying the normalised ApiError together with the channel that produced it, so the view can project field errors without a second showError; saveConfig now opts into presentStates"
  - "two specs (6 cases) pinning auth 400 → summary+inline+zero toast with the typed email preserved, fieldOrder ordering, business-vs-validation channel mutual exclusion, client-failure summarisation, and the push per-channel isolation plus its coexistence with the load-failure block"
  - ".planning/phases/14-error-rollout/14-LEDGER-14.md — the inline-coverage audit (distinct from the 87-row toast inventory), with the three-server-reachable / one-client-only / one-not-submitted registration breakdown and the login classification outcome recorded"

affects: [14-13]

actuals:
  tokens: 20039
  tasks: 2
  commits: 2
  plan_head_before: a70a7bdb5a3e4695a3517bd2044574a3ff11c649

tech-stack:
  added: []
  patterns:
    - "Composite-key namespacing for repeated forms: when a v-for renders one el-form per entity and every entity submits the same field name, the projection map must be keyed by `{entity}::{field}` — a bare field name puts one entity's error on another's control (mutation-verified)"
    - "Per-entity summary filtering: a shared projection map needs a per-entity filtered view of summaryItems, otherwise the summary of one channel renders inside every sibling"
    - "labels override for composite keys: fieldLabel() returns an empty string for a composite key, so the labels map must resolve each composite to the entity's own readable wording or the summary degenerates to a bare backend message"
    - "Store-owned presentation plus view-owned projection: when a store already calls showError and returns a boolean (D-02 / T-11-21), the way to add field-level projection without a second toast is a store ref that carries the normalised ApiError (and its entity key) for the view to read"
    - "Client-validation projection via the rejected value: Login.vue uses the `await validate()` form, so its catch receives Element Plus's invalidFields directly — no callback conversion was needed, which was the smaller diff the plan asked for"

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/AuthFormsInlineError.spec.ts
    - hhs-frontend-v2/src/__tests__/views/PushSettingsInlineError.spec.ts
    - .planning/phases/14-error-rollout/14-LEDGER-14.md
  modified:
    - hhs-frontend-v2/src/views/auth/Login.vue
    - hhs-frontend-v2/src/views/auth/Register.vue
    - hhs-frontend-v2/src/views/user/PushSettings.vue
    - hhs-frontend-v2/src/stores/push.ts
    - hhs-frontend-v2/src/__tests__/views/LoginAuthError.spec.ts

key-decisions:
  - "Register.vue's projection binds four keys but only three are server-reachable. `username` (@NotBlank+@Size), `password` (@NotBlank+@Size) and `email` (@Email) are the constraints RegisterRequest actually declares on fields this page submits. `confirmPassword` is not a field on RegisterRequest at all — it is a client-only defensive binding whose live path is the validateConfirmPassword rule comparing against password before any request. `nickname` is declared but unconstrained and is not submitted, so it can never appear in fieldErrors; it is neither bound nor added to the payload. Stated as three server-reachable + one client-only + one not submitted, never as four server-reachable"
  - "The login path classifies a wrong password as `business`, not `auth`, and therefore does toast. UserServiceImpl:88,91 throws BusinessException(USER_PASSWORD_INVALID) whose string code falls back to 500 through parseCodeToInt, and GlobalExceptionHandler returns it as HTTP 200 + envelope — so normalizeApiError's step 8 catches it. The plan's conditional (add an inline non-field error line only if it classifies as auth) does not fire, so no such line was added; adding one would double-present the failure against D-02. Recorded in the ledger so this is not rediscovered"
  - "PushSettings keeps one composable instance rather than one per channel. A composable must be created synchronously in setup, and the channel list is runtime v-for data, so per-channel instances would violate the setup-time rule. One instance keyed by a channel-qualified composite satisfies both constraints"
  - "The composite separator is a compile-time constant ('::'), never a backend string — so the data-field selector set stays closed (T-14-44) and focusField's CSS.escape path cannot be influenced by response content"
  - "The store gained saveError rather than the view wrapping the store call. push.ts already calls showError and returns a boolean; the plan left the shape to the executor and the boolean-only path made view-side projection impossible. saveError mirrors the existing loadError ref and additionally carries channelType, because PushConfigRequest's field name is always configValue and an error without its channel would land on a sibling"
  - "saveConfig now passes presentStates. A save is a user-initiated action and the store renders no state block, so a forbidden (no right to modify this config) or notFound (the config row was removed) would otherwise be silent. This is the same phase-wide rule 14-05 stated"
  - "The empty-value pre-check in handleToggle was left as ElMessage.warning. It guards before any request is issued — the server is never called — so it is not failure presentation, and routing it through showError would describe 'you have not filled this in' as 'saving failed'. Recorded as 保持 in the ledger"

patterns-established:
  - "Namespaced projection keys: `{entity}::{field}` is the answer whenever a repeated form submits the same field name per entity. Verified by mutation — collapsing the helper to return the bare field name fails both push cases"
  - "A shared projection map needs a filtered summary view per entity. Rendering the unfiltered summaryItems inside a v-for gives every sibling a copy of someone else's errors; filtering by the composite prefix is the fix and is also mutation-verified"
  - "Projection without a second presentation: when a store owns showError, expose the normalised ApiError (plus the key that scopes it) on a ref and let the view read it. The alternative — calling showError again from the view — is the double-toast defect D-02 exists to prevent"
  - "Assert the sibling, not just the target: the push spec's isolation case checks that the errored channel is marked AND that the sibling is clean AND that exactly one .is-error exists page-wide. A single-map implementation passes the first assertion and fails the other two"

requirements-completed: [ERR-03, ERR-05]

coverage:
  - id: D1
    description: "A rejected login renders inline errors for username and password with zero toasts, because both fields carry Jakarta constraints on the server and a 400 with fieldErrors is reachable"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#登录被 400 拒（两个字段）：两条内联、一个两行汇总、零 toast，顺序按 fieldOrder"
        status: pass
      - kind: unit
        ref: "hhs-backend/src/main/java/com/hhs/dto/LoginRequest.java — @NotBlank on both username and password, so the 400 path the spec mocks is real"
        status: pass
    human_judgment: false
  - id: D2
    description: "A rejected registration renders inline errors for username, password, confirmPassword and email with zero toasts; three of the four are server-side and reachable, while confirmPassword is a client-side-only binding because RegisterRequest does not declare it"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#注册被 400 拒：汇总含「用户名」，单条内联，零 toast，已输入的邮箱仍在输入框里 (one fieldErrors entry → exactly one is-error out of four bound items, one-entry summary, zero toasts)"
        status: pass
      - kind: grep
        ref: "grep -c 'data-field' Register.vue → 4 and ':error=' → 4; hhs-backend/.../RegisterRequest.java read in full: exactly four record components (username, password, nickname, email) and no confirmPassword — the client-only classification is source-verified, not inferred"
        status: pass
    human_judgment: false
  - id: D3
    description: "Both auth forms keep a summary block so a user who submits with several problems sees how many there are, not just one per field"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#登录被 400 拒（两个字段）：… ('请修正以下 2 项填写问题' plus exactly two li entries in fieldOrder order)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#客户端校验失败：走同一份汇总通道，零 toast，且不发请求 (two client-side failures → two summary rows, so the count is not server-only)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Both auth forms' showError calls pass presentStates true, because neither form page renders an ErrorState block: a forbidden or notFound classification yields exactly one sticky toast rather than silence"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c 'presentStates: true' equals grep -c 'showError(' → 1 = 1 in Login.vue and 1 = 1 in Register.vue; grep -c 'ErrorState' → 0 in both (the pre-task value, re-verified after removing two comments that had introduced false matches)"
        status: pass
    human_judgment: false
  - id: D5
    description: "The auth forms' existing client-side rules stay in place; the server projection supplements them rather than replacing them, so an obvious empty field is still caught before a round trip"
    requirement: ERR-03
    verification:
      - kind: grep
        ref: "grep -c 'validateConfirmPassword' Register.vue → 2, identical to its pre-task value (git show HEAD:…); Login.vue's rules object still holds required + min-length on both fields"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#客户端校验失败：走同一份汇总通道，零 toast，且不发请求 — registerMock is never called, proving the client rules short-circuit before the request"
        status: pass
    human_judgment: false
  - id: D6
    description: "A push-config save rejected for an over-long value renders an inline error on the config value field for that channel, with zero toasts"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts → PushSettingsInlineError.spec.ts#一个通道保存被 400 拒：只有该通道有内联与汇总，兄弟通道干净，零 toast，值保留 (one inline error reading 配置内容不能超过 500 个字符, zero toasts)"
        status: pass
      - kind: unit
        ref: "hhs-backend/.../PushConfigRequest.java:@Size(max = 500, message = \"配置内容不能超过 500 个字符\") and PushConfigController.java:102 @RequestBody @Valid — the 400 is reachable, correcting the research's earlier 'unconstrained' claim"
        status: pass
    human_judgment: false
  - id: D7
    description: "The push settings page's dynamic per-channel forms each get their own error projection keyed by channel, so saving one channel does not show an error on another"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/PushSettingsInlineError.spec.ts#一个通道保存被 400 拒：… (EMAIL's field carries is-error, WECOM's does not, exactly one .is-error page-wide, exactly one .form-error-summary) — collapsing channelField to return the bare field name fails this case (mutation-verified)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/PushSettingsInlineError.spec.ts#一个通道保存被 400 拒：… (removing the prefix filter from channelSummaryItems renders the summary in the sibling too and fails — mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -c 'data-field' PushSettings.vue → 1, and the expression reads channelField(config.channelType) rather than a literal 'configValue'"
        status: pass
    human_judgment: false
  - id: D8
    description: "The push settings page's existing load-failure state block keeps working unchanged; this plan adds field-level errors without disturbing the page-level one"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/PushSettingsInlineError.spec.ts#加载失败块与字段投影共存：保存失败不清 loadError，加载失败不清投影 (the .error-state block renders with its retry button while saveError survives, and loadError is null after a save failure)"
        status: pass
      - kind: grep
        ref: "grep -c 'loadError' PushSettings.vue → 2, identical to its pre-task value — the ErrorState block and its retry were not touched"
        status: pass
    human_judgment: false
  - id: D9
    description: "No catch block in these three files clears an input, closes nothing, or resets a form on failure"
    requirement: ERR-05
    verification:
      - kind: grep
        ref: "grep -n 'resetFields|resetField(' over Login.vue and Register.vue → no matches; PushSettings.vue's catch path calls only clearField (error text, never the input value)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#注册被 400 拒：… — inserting formRef.value?.resetFields() into the catch fails the value-snapshot assertion (mutation-verified)"
        status: pass
    human_judgment: false
  - id: D10
    description: "The registration form's typed email survives a failed submission"
    requirement: ERR-05
    verification:
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#注册被 400 拒：… (asserts the DOM input's .value is 'kept@example.com' AND that the whole reactive form toEqual its pre-submit snapshot, so a reset-then-refill cannot pass)"
        status: pass
    human_judgment: false
  - id: D11
    description: "UI Considerations · empty E8: an empty login or registration form stays submittable and produces summary plus inline errors"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "Login.vue and Register.vue — the submit buttons bind only :loading, never :disabled, so an empty form is still clickable"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#客户端校验失败：走同一份汇总通道，零 toast，且不发请求 (a form with two empty fields submits, gets a two-row summary, and never calls the API)"
        status: pass
    human_judgment: false
  - id: D12
    description: "UI Considerations · partial E4: a server rejection naming only email leaves the three password and username fields unmarked and populated"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#注册被 400 拒：… (a single username fieldErrors entry yields exactly 1 .is-error among 4 bound items; the email input still holds its value and no other item is marked)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/PushSettingsInlineError.spec.ts#一个通道保存被 400 拒：… (the sibling channel is entirely unmarked and keeps its own value)"
        status: pass
    human_judgment: false
  - id: D13
    description: "UI Considerations · zero-one-many E5: the auth summary count reflects only the fields the server named"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/AuthFormsInlineError.spec.ts#注册被 400 拒：… (one fieldErrors entry → '请修正以下 1 项填写问题' and exactly one li) and #登录被 400 拒（两个字段）：… (two entries → '2 项' and two li)"
        status: pass
    human_judgment: false
  - id: D14
    description: "The push save path adds no second showError beyond what the store already does"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -n 'showError' PushSettings.vue → four occurrences, all inside comments; the view contains no showError call. stores/push.ts owns the single call in saveConfig"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/PushSettingsInlineError.spec.ts#一个通道保存被 400 拒：… (toastCount() === 0 for the validation case, and the error path is reached through the store, so a view-side showError would add a toast)"
        status: pass
    human_judgment: false
  - id: D15
    description: "The ledger fragment records the inline-coverage decision rather than a toast migration, and its registration row separates the three server-reachable fields from the client-only confirmPassword binding and the unsubmitted nickname"
    requirement: ERR-03
    verification:
      - kind: manual
        ref: "14-LEDGER-14.md — header states the distinct purpose ('本片段与 87 行迁移台账是两件不同的事，不要合并计数'); three form rows plus the 保持 row; the 字段三分法 table names username/password/email as server-reachable, confirmPassword as client-only with the source citation, nickname as declared-but-not-submitted; the login business-vs-auth classification is recorded with file:line evidence"
        status: pass
    human_judgment: false
  - id: D16
    description: "Both files satisfy the zero-toast rule and the payload is unchanged"
    requirement: ERR-03
    verification:
      - kind: grep
        ref: "grep -c 'ElMessage.error' → 0 in Login.vue, Register.vue and PushSettings.vue"
        status: pass
      - kind: unit
        ref: "Register.vue's register call still passes exactly { username, password, email: form.email || undefined } — nickname omitted, email conditional, read in the committed diff"
        status: pass
    human_judgment: false

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Correctness] `saveConfig` needed a new store ref for the view to project from**

- **Found during:** Task 2, reading `stores/push.ts`
- **Issue:** The plan anticipated this branch explicitly: "If the store swallows the error and returns only a boolean, the field projection is impossible from the view — in that case the correct change is to have `saveConfig` also assign a `saveError` ref the view can read (mirroring `loadError`)." `push.ts:68-72` did exactly that — `showError(...)` then `return false`, discarding the normalised `ApiError`. Without a ref, the view could only re-call `showError` to obtain it, which would produce two toasts for one failure.
- **Fix:** Added `saveError: ref<{ channelType: string; error: ApiError } | null>`, cleared at the top of `saveConfig` and assigned in the catch. It carries `channelType` in addition to the error, because `PushConfigRequest`'s field name is always `configValue` and an unscoped error would land on a sibling channel's input. `loadError` and the `ErrorState` block are untouched.
- **Files modified:** `hhs-frontend-v2/src/stores/push.ts`
- **Commit:** `5325f63`

**2. [Rule 2 - Correctness] `saveConfig` was missing `presentStates`**

- **Found during:** Task 2, applying the phase-wide rule from 14-05
- **Issue:** `saveConfig` called `showError(error, 'push.saveFailed')` with no options. `push.ts` renders no state block, so a `forbidden` (no right to modify this config) or `notFound` (the config row was deleted elsewhere) classified as a zero-toast silent failure — the exact defect 14-05's rule exists to prevent.
- **Fix:** Third argument is now `{ presentStates: true }`. Behaviour for `validation` / `business` / `network` / `server` is unchanged; only the two state kinds gained their one sticky toast.
- **Files modified:** `hhs-frontend-v2/src/stores/push.ts`
- **Commit:** `5325f63`

**3. [Rule 1 - Bug] Two of my own comments broke acceptance greps**

- **Found during:** Task 1 acceptance verification
- **Issue:** `grep -c "ErrorState"` must be 0 in both auth files (it is the AC that proves `presentStates` is forced rather than optional), and `grep -c "validateConfirmPassword"` must be unchanged from its pre-task value. My explanatory comments in `Login.vue`, `Register.vue` contained the literal words `ErrorState` and `validateConfirmPassword`, moving both counts off their required values (1 and 3 instead of 0 and 2).
- **Fix:** Reworded the three comments to say "页级状态块" and "自定义的「两次密码一致」客户端规则" instead. Both greps are back to 0/0 and 2, which the summary's verification table records.
- **Files modified:** `hhs-frontend-v2/src/views/auth/Login.vue`, `hhs-frontend-v2/src/views/auth/Register.vue`
- **Commit:** `baf1cf1`

**4. [Rule 1 - Bug] `LoginAuthError.spec.ts` started emitting Vue resolve warnings**

- **Found during:** Task 2, full-suite run
- **Issue:** `Login.vue` now mounts `FormErrorSummary` as its first form child, and that component renders an `el-alert`. `LoginAuthError.spec.ts` stubs every Element Plus component it mounts but had no `el-alert` stub, so each of its two cases printed `[Vue warn]: Failed to resolve component: el-alert`. The cases still passed — but a warning-only failure is the kind of noise that later hides a real one.
- **Fix:** Added an `el-alert` stub matching the one the other specs already use. Two cases still pass, warnings gone.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/LoginAuthError.spec.ts`
- **Commit:** `5325f63`

### Deliberate-plan divergences (documented, not auto-fixes)

**1. No inline non-field error line was added above the login submit button.** The plan made this conditional: "If it classifies as `auth`, the toast is suppressed and the user sees nothing — in that case add an inline non-field error line … and record the decision in the ledger." It does not classify as `auth`. `UserServiceImpl:88,91` throws `BusinessException(USER_PASSWORD_INVALID)` with a string code, which `parseCodeToInt` falls back to 500, and `GlobalExceptionHandler.handleBusinessException` returns `Result<Void>` at HTTP 200 — so `normalizeApiError`'s step 8 classifies it `business` and `showError` emits one toast. The condition was not met, so nothing was added; the third spec case mocks this exact shape (HTTP 200 + `code: 500` + `errorCode: 'user.password_invalid'`) rather than a bare 500, and the ledger records the finding.

**2. `Login.vue`'s client-validation branch reads the rejection value rather than converting to the callback form.** The plan offered both ("convert … or wrap the rejected validate and read its `fields`. Read the file and pick whichever fits its existing shape with the smaller diff"). The existing shape was `await formRef.value.validate()` in a try/catch, and Element Plus's `validate` rejects with `invalidFields` itself (verified in `element-plus/es/components/form/src/form.vue_vue_type_script_setup_true_lang.mjs:115,128`), so the catch parameter already is what `collectInvalidFieldMessages` wants. Converting to the callback form would have been a larger diff for the same result. The generic `ElMessage.warning('请检查输入信息')` was removed in the process — the summary replaces it, and removing it is what keeps the file's `ElMessage` import unused and deleted.

**3. The push summary labels are `{channelLabel} {getConfigLabel(...)}` rather than `getConfigLabel(...)` alone.** The plan said to map each composite "back to the channel's own label from `getConfigLabel`", giving "企业微信 Webhook：配置内容不能超过 500 个字符". Including `channelLabel` as a prefix makes the entry unambiguous when two channels share the same `getConfigLabel` wording — `WECOM` and `FEISHU` both return `'Webhook URL'`, so the plan's literal example would have produced two indistinguishable summary rows if both ever errored. The rendered text is `邮件 邮箱地址：…` / `企业微信 Webhook URL：…`. The spec asserts the exact string, so this is pinned rather than incidental.

## Auth Gates

None. Zero packages installed, no credentials needed, no checkpoint reached.

## Known Stubs

None. All three forms call real endpoints; nothing renders placeholder or empty-to-UI data.

One **unreachable defence** is worth recording rather than calling a stub: `Register.vue`'s `confirmPassword` projection key has no server counterpart, because `RegisterRequest` declares no such field (verified by reading the DTO in full). The binding is correct and becomes live the day a matching constraint is added; the live path today is the client-side `validateConfirmPassword` rule. The ledger states this so the verifier does not treat the defensive path as proven-in-production — the same treatment 14-05 gave the reminder DTO.

## Threat Flags

None new. T-14-42 (password disclosure): neither spec writes a password into any toast, summary or inline text — they assert the input's presence and the model value only. T-14-43 (push secret disclosure): the inline error renders only the backend's message, never the submitted `configValue`, so the over-long paste is not echoed back into the DOM a second time. T-14-44 (XSS): all backend-derived text goes through `{{ }}`; no `v-html` in any of the three files; the composite key is built from a channel enum and a literal field name with a compile-time separator, so the `data-field` selector set stays closed. T-14-45 (login elevation): only presentation changed — the auth store's token handling is untouched, no retry re-submits credentials, and a 401 still routes through `showError`'s `auth` early return rather than being treated as success.

---

## What changed, in one paragraph

The last three server-validated forms in the rollout's inventory now project `fieldErrors` inline. A registration rejected for a taken username shows 用户名 in a summary above the fields and under the username input, fires no toast, and leaves the typed email sitting in its box; a login rejected on both fields shows two inline errors under one two-entry summary. The push settings page was the interesting one: four channels each render a form that submits the same field name `configValue`, so the projection map is keyed by `{channel}::{field}` — saving the email channel marks the email field and leaves the WeCom field clean, proven by asserting the sibling rather than only the target. The store keeps sole ownership of `showError` (a new `saveError` ref carries the normalised error back for projection, so no second toast appears), and the page-level load-failure block is untouched and coexists with the new field errors. All three files still contain zero bare `ElMessage.error` calls, so this plan adds no rows to the 87-call inventory — its ledger fragment records something different: which validated forms are wired and which are deliberately not, with the registration row stating three server-reachable fields, one client-only binding and one declared-but-not-submitted field.

## Verification evidence

| Check | Command | Result |
|-------|---------|--------|
| Auth spec | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/AuthFormsInlineError.spec.ts` | 4 passed |
| Push spec | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/PushSettingsInlineError.spec.ts` | 2 passed |
| Full suite | `npm --prefix hhs-frontend-v2 run test:run` | 45 files / 518 tests passed (pre-task 43 files / 512 → +2 files, +6 tests) |
| Build + type-check | `npm --prefix hhs-frontend-v2 run build` | `✓ built in 12.46s`, no TS errors |
| Bare toasts | `grep -c 'ElMessage.error'` | 0 / 0 / 0 (Login / Register / PushSettings) |
| `data-field` / `:error` | `grep -c` | Login 2 / 2; Register 4 / 4; PushSettings 1 / 1 |
| `collectInvalidFieldMessages` | `grep -c` | 2 in each auth file (import + call) |
| `validateConfirmPassword` | `grep -c` | 2 — identical to the pre-task value from `git show HEAD:…` |
| Blanket field reset | `grep -n 'resetFields\|resetField('` | no matches in either auth file |
| `presentStates` parity | `grep -c 'presentStates: true'` vs `grep -c 'showError('` | 1 = 1 (Login), 1 = 1 (Register) |
| `ErrorState` in auth files | `grep -c 'ErrorState'` | 0 / 0 — the option is forced, not optional |
| `loadError` in push view | `grep -c 'loadError'` | 2 — unchanged from the pre-task value |
| `showError` calls in push view | `grep -n 'showError'` | four matches, all inside comments; no call site |

Mutation checks performed (each broke exactly one case, then the file was restored):
dropped `:error` on the register username item, reordered `fieldOrder` to password-first,
inserted `formRef.value?.resetFields()` into the register catch, collapsed `channelField`
to return the bare field name, removed the prefix filter from `channelSummaryItems`,
replaced the `labels` map with `{}`, and emptied `handleSummaryClick`.

## Self-Check: PASSED

- All 6 declared files exist: `Login.vue`, `Register.vue`, `PushSettings.vue`,
  `AuthFormsInlineError.spec.ts`, `PushSettingsInlineError.spec.ts`, `14-LEDGER-14.md`
  (plus `stores/push.ts` and `LoginAuthError.spec.ts`, added under deviation rules).
- Both work commits present in history: `baf1cf1`, `5325f63`.
- Commit count measured as `git rev-list --count a70a7bd..HEAD` = 2 at the moment the
  summary was written, **excluding this summary's own `docs(14-14)` commit** — the same
  convention 14-04 and 14-05 used, so the number does not depend on when it is read.
