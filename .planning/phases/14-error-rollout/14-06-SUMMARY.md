---
phase: 14-error-rollout
plan: 06
subsystem: ui
tags: [error-handling, form-validation, inline-errors, two-forms, dialog, element-plus, vue3, vitest, present-states, err-05-preservation]

requires:
  - phase: 14-error-rollout
    plan: 01
    provides: "showError's presentStates option with its zero-toast default for forbidden/notFound, and the duration-0 VNode branch both kinds now reach"
  - phase: 14-error-rollout
    plan: 02
    provides: "the global .el-form-item.is-error border declaration — this plan writes no local error styling because that block already covers input and daterange"
  - phase: 14-error-rollout
    plan: 03
    provides: "collectInvalidFieldMessages as the shared client-validation collector, and the 46-key ERROR_MESSAGES / 44-label FIELD_LABELS tables both pages resolve through (nickname/email/phone/oldPassword/newPassword/confirmPassword/reportName/institution/reportDate/file all present, so no labels override is needed here)"
  - phase: 14-error-rollout
    plan: 05
    provides: "the presentation-channel rule (a file with no ErrorState passes presentStates true at every showError call) and the two-instance-on-one-page precedent from goals/Index.vue, applied here to two cards instead of two dialogs"

provides:
  - "user/Profile.vue fully wired with two independent useFormServerErrors instances (profile: nickname/email/phone, password: oldPassword/newPassword/confirmPassword), each form carrying its own FormErrorSummary as its first child and its own summary ref"
  - "the two-form independence property, asserted in both directions: a rejected password change renders one summary (the password card's) with the profile card's absent, and vice versa — a single shared instance renders two and fails the case"
  - "ERR-05's most damaging failure mode guarded by value snapshot: the password clear stays inside the try block after the await, so a rejected change leaves all three inputs populated"
  - "screening/List.vue upload dialog wired over reportName/institution/reportDate/file, with the file item cleared from the upload control's on-change/on-remove rather than a model-value update (its bound value is a file list)"
  - "screening/List.vue delete split into confirm-then-api with a bare commented silent return; the hand-written cancel comparison removed"
  - "two specs (6 cases) with three B1 presentStates regression guards: an upload notFound, a delete notFound, and — the residual-allowlist counterpart — a spec-level assertion that no path outside beforeAvatarUpload calls ElMessage.error"
  - ".planning/phases/14-error-rollout/14-LEDGER-06.md — the nine-row fragment plus the two-row residue allowlist keyed by (path, enclosing function, literal) rather than line number"

affects: [14-10, 14-11, 14-12, 14-13, 14-14]

actuals:
  tokens: 15377
  tasks: 2
  commits: 2
  plan_head_before: 1a08871d59749c06d1b202f8fafa3876896289d7

tech-stack:
  added: []
  patterns:
    - "Two forms on one page need two composable instances, not one. A shared instance renders the same summaryItems in both cards, so each summary answers 'how many fields are wrong on this page' instead of 'in this form' — the spec asserts the other form's summary is absent, which is the only assertion that discriminates"
    - "The clear-on-success-only rule for secret inputs: an Object.assign that blanks a password form lives inside the try block after the await, never in a finally and never on the failure path. Written so a reviewer can see the placement, and pinned by a value snapshot of all three inputs"
    - "File-list-valued form items clear their error from the control's own on-change/on-remove, because a v-model update is not what changes when a file is replaced"
    - "The residue allowlist is keyed by (path, enclosing function, literal), never by line number: this plan's own import and template insertions move those lines, so a line-number assertion would be false the moment the task completes"

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/UserProfileInlineError.spec.ts
    - hhs-frontend-v2/src/__tests__/views/ScreeningListInlineError.spec.ts
    - .planning/phases/14-error-rollout/14-LEDGER-06.md
  modified:
    - hhs-frontend-v2/src/views/user/Profile.vue
    - hhs-frontend-v2/src/views/screening/List.vue

key-decisions:
  - "user/Profile.vue gets two useFormServerErrors instances rather than one with a merged fieldOrder. Merging would make the password card's summary list nickname/email/phone too, and the personal-info card's summary list oldPassword — each block has to answer 'how many problems are in THIS form'. The spec proves the choice by asserting the sibling summary does not exist in either direction; a single instance makes that count 2 and fails"
  - "presentStates: true at all four user/Profile showError calls and all three screening/List calls, because neither file renders an ErrorState. The option is not a per-handler judgement here — it is the file-wide consequence of having no block, so the grep equality between `presentStates: true` and `showError(` (4=4 and 3=3) is the correct shape of the assertion"
  - "The two beforeAvatarUpload pre-checks stay as function-level ElMessage.error and are ledgered as 保持. They validate a file the user just picked, before any request exists: routing them through showError would require fabricating an ApiError to satisfy the signature and would claim a server failure that did not happen. With 14-11's two OcrInput.vue checks these are the phase's four documented residue rows"
  - "handleChangePassword reuses user.saveFailed rather than adding user.passwordFailed. Both are 'save a form on this page'; the backend's own Chinese distinguishes the details, and a new key would need the dictionary AND its count assertion changed in the same commit. Deliberately identical reasoning to 14-05's alert.updateFailed reuse, so the dictionary stays a fixed set"
  - "screening handleUpload keeps ElMessage.warning('请选择文件') as a client-side precondition rather than routing it through showError. It is not a response to any request; converting it would misclassify a local check as an API failure and it is a warning, not an error, so it is not part of the residue count"
  - "handleFileRemove was added so removing the picked file also clears a stale 报告文件 error. The plan requires the clear to hang off the control's own events; on-change alone would leave the error visible after a removal, which is a state the user can reach with the limit-1 uploader"
  - "The screening delete's notFound comes from ScreeningServiceImpl:197, which throws RESOURCE_NOT_FOUND for both a missing report and someone else's — so 'delete a report already deleted in another tab' is an ordinary race, and without the third argument the user would see nothing"

patterns-established:
  - "Multi-form page rule: when one page hosts N forms with different field sets, instantiate the projection N times. Share nothing but the collector (a stateless pure function). Assert independence by requiring the sibling summaries to be absent — not merely by counting error classes on the rejected form"
  - "Secret-preservation is asserted as a value snapshot, never as a comment. 'We do not clear the password' written in a comment survives an edit that clears it elsewhere; a pre/post toEqual of all three inputs does not"
  - "A residue allowlist entry is a (path, enclosing function, literal) triple. Line numbers are unusable by construction, because the same commit that creates the residue also shifts it"
  - "Mutation-verified assertions: every load-bearing case was checked by temporarily breaking the implementation (shared summary instance, dropped presentStates, password clear moved to the failure path, upload apply() dropped, dialog closed on failure, delete reverted to one try with the old guard, delete issued before confirm) and observing exactly one failure each. Two mutations initially passed and were replaced with faithful ones — see Deviations"

requirements-completed: [ERR-03, ERR-05]

coverage:
  - id: D1
    description: "The personal-info form and the change-password form each render their own summary plus inline field errors on a server 400, with zero toasts"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts#密码表单被拒：只有密码汇总出现，三项保留值，零 toast"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts#个人信息表单被拒：只有个人信息汇总出现，邮箱值保留，零 toast"
        status: pass
    human_judgment: false
  - id: D2
    description: "The two summaries are independent: each of the two forms has its own summary count and neither inherits the other's entries (UI Considerations · zero-one-many E5)"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts#密码表单被拒：… (findAll('.form-error-summary') has length 1, and it is the password card's — pointing the profile summary at passwordErrors makes it 2 and fails; mutation-verified)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts#个人信息表单被拒：… (length 1 in the other direction, plus not.toContain('原密码')/('新密码') on the surviving summary)"
        status: pass
      - kind: grep
        ref: "grep -c 'useFormServerErrors(' hhs-frontend-v2/src/views/user/Profile.vue → 2; grep -c 'FormErrorSummary' → 6 (1 import + 2 template + 2 ref-type + 1 type ref), template read confirms one per el-form"
        status: pass
    human_judgment: false
  - id: D3
    description: "Changing the password and having it rejected leaves all three password inputs populated; a user must not retype a password because the server said the old one was wrong (Contract 7.1)"
    requirement: ERR-05
    verification:
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts#密码表单被拒：… (a pre-submit snapshot of all three inputs compared with toEqual after the rejection, plus the literal assertion vm.passwordForm.oldPassword === 'wrong-old-pw'; moving the Object.assign above the showError call makes it {} and fails; mutation-verified)"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/views/user/Profile.vue handleChangePassword — the Object.assign that blanks the three fields sits inside the try block after `await userApi.changePassword(...)`, so the catch block cannot reach it"
        status: pass
    human_judgment: false
  - id: D4
    description: "A server rejection naming only oldPassword marks only that field; the other two keep their values and normal borders (UI Considerations · partial E4)"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts#密码表单被拒：… (exactly 1 .el-form-item.is-error, on [data-field=oldPassword]; newPassword and confirmPassword both assert not.toContain('is-error'))"
        status: pass
    human_judgment: false
  - id: D5
    description: "The screening upload dialog renders inline field errors for reportName, institution, reportDate and file when the server rejects them, with zero toasts and the dialog open"
    requirement: ERR-03
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningListInlineError.spec.ts#上传被 400 拒：汇总 + 内联，零 toast，弹窗仍开，报告名称保留 (fromItems 1, [data-field=reportName].is-error, inline text 报告名称不能为空, uploadVisible true; closing the dialog in the failure path fails it; mutation-verified)"
        status: pass
      - kind: grep
        ref: "grep -c 'data-field' hhs-frontend-v2/src/views/screening/List.vue → 4 (reportName, institution, reportDate, file)"
        status: pass
    human_judgment: false
  - id: D6
    description: "The screening list's delete handler no longer contains a hand-written cancel comparison; cancel is silent and a real failure produces exactly one toast"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningListInlineError.spec.ts#删除：取消时零提示且不发 DELETE；确认后失败恰好一条提示 (first half: an AbortError from confirm yields zero toasts AND deleteReport not called; second half: confirm then a 500 yields exactly one)"
        status: pass
      - kind: grep
        ref: "grep -c \"if (error !== 'cancel')\" hhs-frontend-v2/src/views/screening/List.vue → 0; grep -c 'ElMessage.error' → 0; the phrase survives nowhere in the file"
        status: pass
    human_judgment: false
  - id: D7
    description: "Every showError call in both files passes presentStates true, because neither page renders an ErrorState: a forbidden or notFound classification yields exactly one sticky toast rather than silence"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c 'presentStates: true' equals grep -c 'showError(' → 4 and 4 (user/Profile), 3 and 3 (screening/List)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts#头像上传的 RESOURCE_NOT_FOUND 信封：… (dropping the third argument yields 0 toasts and fails; mutation-verified)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/ScreeningListInlineError.spec.ts#删除已被删除的报告（RESOURCE_NOT_FOUND 信封）：… (same; mutation-verified)"
        status: pass
    human_judgment: false
  - id: D8
    description: "Nothing in either file renders an ErrorState component, so presentStates true is the only presentation channel available for a state-classified failure"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c 'ErrorState' → 0 in hhs-frontend-v2/src/views/user/Profile.vue and in hhs-frontend-v2/src/views/screening/List.vue"
        status: pass
    human_judgment: false
  - id: D9
    description: "Both files end this plan with zero bare ElMessage.error calls outside the two documented client-side pre-validation toasts in user/Profile.vue (the phase's 4-row residue allowlist, this plan owning 2)"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "grep -c 'ElMessage.error' → exactly 2 in user/Profile.vue and 0 in screening/List.vue; both survivors localized by (function, literal): beforeAvatarUpload contains 只能上传图片文件 once and 图片大小不能超过 2MB once, each occurring exactly once in the whole file"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts and ScreeningListInlineError.spec.ts afterEach: expect(ElMessageMock.error).toHaveBeenCalledTimes(0) across all 6 cases — no failure path in either file reaches the function-level API"
        status: pass
      - kind: grep
        ref: ".planning/phases/14-error-rollout/14-LEDGER-06.md holds the two-row allowlist table keyed by (path, beforeAvatarUpload, literal)"
        status: pass
    human_judgment: false
  - id: D10
    description: "No catch block in either file resets a form field or closes a dialog"
    requirement: ERR-05
    verification:
      - kind: grep
        ref: "grep -n 'resetFields|resetField(' → no matches in either file"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/ScreeningListInlineError.spec.ts#上传被 400 拒：… (uploadVisible stays true and the three pre-submit values are unchanged after the rejection)"
        status: pass
    human_judgment: false
  - id: D11
    description: "UI Considerations · empty E8: the password form with empty inputs stays submittable and produces summary plus inline errors rather than a disabled button"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/user/Profile.vue — the password submit button binds only :loading (passwordLoading), never :disabled; the personal-info card's save button likewise. Both forms remain submittable with empty inputs; EP's rules then produce invalidFields through the client path"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts#密码表单被拒：… exercises handleChangePassword through the validate callback without any disabled gate"
        status: pass
    human_judgment: false
  - id: D12
    description: "UI Considerations · overflow E5: a five-entry summary scrolls internally past four rows while keeping its title visible"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/components/common/FormErrorSummary.vue:102-104 (max-height: 144px; overflow-y: auto; overscroll-behavior: contain) with the ElAlert title outside the scrolling <ol> — inherited from 14-01; this plan adds no geometry and no local error styling"
        status: pass
    human_judgment: false
  - id: D13
    description: "Every form on both pages satisfies all seven conditions of Contract 4.6"
    requirement: ERR-03
    verification:
      - kind: grep
        ref: "The seven-point set per form: (1) el-form ref present (formRef/passwordFormRef, formRef), (2) data-field 6 and 4 with matching :error bindings, (3) clearField( on every :error-bound control including the upload's on-change/on-remove, (4) FormErrorSummary is the first child of each el-form (1 per user form ×2, 1 in the screening dialog), (5) reset() before every validate, (6) ElMessage.error 2 (residue allowlist) and 0, (7) no hand-written cancel comparison in either"
        status: pass
    human_judgment: false
  - id: D14
    description: "A screening report list load failure renders one actionable toast; the upload failure renders one actionable toast plus inline errors when it is a validation failure"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/screening/List.vue fetchReports (showError(error, 'screening.loadFailed', { presentStates: true })) and handleUpload (showError(error, 'ocr.uploadFailed', { presentStates: true }) then apply on validation only)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/ScreeningListInlineError.spec.ts#上传被 400 拒：… (mounting the page with a 200 list leaves zero toasts, so the load path adds no noise; the 400 leaves zero toasts because validation is the summary's job)"
        status: pass
    human_judgment: false
  - id: D15
    description: "A screening report delete that is already gone (DELETE returns RESOURCE_NOT_FOUND) produces exactly one toast, not silence"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningListInlineError.spec.ts#删除已被删除的报告（RESOURCE_NOT_FOUND 信封）：恰好一条提示，文案是后端消息 (asserts the toast's error-toast__text child equals 报告不存在, not merely a count)"
        status: pass
    human_judgment: false
  - id: D16
    description: "The avatar upload failure produces exactly one actionable toast; the two client-side pre-checks keep their existing toasts because they are not API failures and are recorded in the ledger as 保持"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/UserProfileInlineError.spec.ts#头像上传的 RESOURCE_NOT_FOUND 信封：… (errorToastCount 1, text 用户不存在)"
        status: pass
      - kind: grep
        ref: "The two pre-checks remain at function level inside beforeAvatarUpload and are ledgered as 保持 with the 'no ApiError exists yet' rationale in 14-LEDGER-06.md"
        status: pass
    human_judgment: false
  - id: D17
    description: "The screening upload dialog separates its own client-side validation from API fieldErrors: the no-file precondition is a warning, not an error-channel call"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/screening/List.vue handleUpload — the `if (!selectedFile.value)` branch returns ElMessage.warning('请选择文件') before any request and never reaches the showError channel; the catch block is the only place a server fieldError lands"
        status: pass
    human_judgment: false
  - id: D18
    description: "A stale 报告文件 error clears when a file is replaced or removed, because the clear hangs off the upload control's own events rather than a model-value update"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/views/screening/List.vue handleFileChange and handleFileRemove both call uploadErrors.clearField('file'); the el-upload binds :on-change and :on-remove"
        status: pass
    human_judgment: true

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The Task 1 spec could not mount the page at all**

- **Found during:** Task 1, first run of the new spec
- **Issue:** `user/Profile.vue` reads `authStore.user!.username` and `authStore.user!.createdAt` in the template with a non-null assertion. With an empty Pinia the store's `user` is `null`, so mounting threw `TypeError: Cannot read properties of null (reading 'username')` and all three cases failed at render.
- **Fix:** The spec seeds the session user before mounting: `useAuthStore().updateUser(PROFILE_RES.data.profile)`. That is the real shape — the template's assertion is correct for any authenticated route visit.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/UserProfileInlineError.spec.ts`
- **Commit:** `521b417`

**2. [Rule 1 - Bug] The screening spec emitted a Vue "reserved prop" warning**

- **Found during:** Task 2, first run
- **Issue:** The `el-upload` stub declared `ref` among its `props`, which Vue rejects (`Invalid prop name: "ref" is a reserved property`). Noise in a spec that is supposed to model the real control.
- **Fix:** Dropped `ref` from the stub's prop list, and routed the router through an initial `push('/')` so `No match found for location with path ""` stops appearing. Both were warnings only, but a spec that prints warnings trains readers to ignore its output.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/ScreeningListInlineError.spec.ts`
- **Commit:** `b32f41e`

**3. [Rule 2 - Missing critical functionality] Removing the picked file left a stale field error**

- **Found during:** Task 2, wiring the `file` item
- **Issue:** The plan requires the `file` clear to hang off the upload control's events because its bound value is a file list. `on-change` alone does not fire on removal, so a user who picked a file, got a 报告文件 rejection, then removed the file would keep the error on an empty control.
- **Fix:** Added `handleFileRemove` and bound it as `:on-remove`, clearing both `selectedFile`/`form.file` and `uploadErrors.clearField('file')`.
- **Files modified:** `hhs-frontend-v2/src/views/screening/List.vue`
- **Commit:** `b32f41e`

**4. [Rule 2 - Test quality] Two mutation attempts did not discriminate and were replaced with faithful ones**

- **Found during:** Task 2's mutation pass
- **Issue:** (a) Reverting the delete split to a single try with `if (error !== 'cancel') { showError(...) }` still passed every case — because `showError` normalizes an `AbortError` to `cancel` and returns silently anyway, so that particular mutation is not observable through behaviour. (b) The reverted guard had to be the *pre-plan* shape.
- **Fix:** Re-ran the same mutation with the original `ElMessage.error('删除失败')` inside the guard. That fails both halves (a toast on cancel, and the DELETE-asset ordering assertions), confirming the split is what the case measures. A second mutation moving the DELETE above the confirm also failed the cancel half, which is the direct proof that confirm-then-api is real rather than renamed.
- **Files modified:** none (verification only)
- **Commit:** n/a

### Deliberate-plan divergences (documented, not auto-fixes)

**1. `user/Profile.vue` projects client validation through one small local helper (`projectClientErrors`).** The plan asks for `setErrors(collectInvalidFieldMessages(invalidFields))` plus a focus in each of two handlers. With two forms that is four near-identical lines; the helper takes the target instance and its summary ref. It delegates to the 14-03 export and is not a second implementation of the collector — the same call-count discipline 14-05 established for `goals/Index.vue`.

**2. `showUploadDialog` also calls `uploadErrors.reset()`.** The plan mentions `reset()` before `validate`. Adding it on dialog-open as well means a dialog re-opened after a failed attempt does not show the previous attempt's summary before the user has typed anything. It clears projection only; `showUploadDialog`'s existing `Object.assign` that blanks the draft stays where it was (it is dialog initialisation, not a failure path — moving it into a catch would be the ERR-05 regression).

**3. `handleChangePassword`'s success path reorders the success toast after the field clear.** The original toasted first, then cleared. Both are inside the try after the await, so behaviour is equivalent; the new order reads as "clear, then announce", which makes the clear-on-success-only property visible at a glance — the plan asks for exactly that readability.

## Auth Gates

None. Zero packages installed, no credentials needed, no checkpoint reached.

## Known Stubs

None. Both pages call real APIs; nothing renders placeholder or empty-to-UI data.

Two notes for the verifier, neither a stub:

- **`screening/List.vue`'s `institution` item is defensively wired.** It carries `prop`/`data-field`/`:error`/`clearField`, but `institution` has no `@NotBlank` on the backend upload request, so a 400 naming it is not reachable today. The wiring activates the day a constraint is added. This mirrors 14-05's record for `/api/reminders`.
- **The `file` item's server-side error path is likewise defensive.** The current pre-upload check for the screening dialog is "did the user pick anything" (a warning), not a type/size rejection; the server's own file rejection lands through `ocr.uploadFailed` on the catch, not on `fieldErrors.file`. The item is wired so that a future server-side `field` error renders inline.

## Threat Flags

None. No new network endpoint, auth path or schema change. The threat register's three mitigations are honoured: T-14-16 (the password case asserts the inputs survive, never that their values are printed — no value reaches a toast, a log or an error string), T-14-17 (all summary and inline text is interpolated via `{{ }}`, never HTML; the report name reaches the DOM through `v-model` only), T-14-18 (the avatar type/size pre-checks remain client conveniences; a bypass reaches the server, whose rejection now routes through `showError`).

## Self-Check: PASSED

- `hhs-frontend-v2/src/__tests__/views/UserProfileInlineError.spec.ts` — FOUND (3 cases pass)
- `hhs-frontend-v2/src/__tests__/views/ScreeningListInlineError.spec.ts` — FOUND (3 cases pass)
- `.planning/phases/14-error-rollout/14-LEDGER-06.md` — FOUND (9 rows, 7 columns)
- Commit `521b417` — FOUND (Task 1)
- Commit `b32f41e` — FOUND (Task 2)
- Full suite: 47 files / 524 tests passed (baseline 45 / 518)
- `npm run build`: succeeded
