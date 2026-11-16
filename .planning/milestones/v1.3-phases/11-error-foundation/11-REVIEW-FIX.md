---
phase: 11-error-foundation
fixed_at: 2026-11-07T00:40:00Z
review_path: .planning/phases/11-error-foundation/11-REVIEW.md
iteration: 1
findings_in_scope: 24
fixed: 23
skipped: 1
status: partial
---

# Phase 11: Code Review Fix Report

**Fixed at:** 2026-11-07
**Source review:** `.planning/phases/11-error-foundation/11-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 24 (3 Critical + 21 Warning; `fix_scope: critical_warning`)
- Fixed: 23 (3 Critical + 20 Warning)
- Skipped: 1 (WR-06 — would change `Result.code` numeric semantics, which the fix constraints forbid)

**Verification scope:** all gates ran in the main checkout
(`workflow.use_worktrees: false`, so no isolated worktree was created).

- Backend: `mvn -f hhs-backend/pom.xml clean verify -DskipITs` → **724 tests, 0 failures** (baseline 697).
- Frontend: `npm --prefix hhs-frontend-v2 run test:run` → **210 tests, 0 failures** (baseline 207).
- Frontend build: `npm --prefix hhs-frontend-v2 run build` → green (`vue-tsc` + `vite build`).
- Frontend lint: `npm --prefix hhs-frontend-v2 run lint:check` → **0 errors** (pre-existing warnings only).

## Fixed Issues

> Status note: WR-06 is the only skipped finding. The remaining 23 findings (CR-01..03, WR-01..05, WR-07..21) are fixed and committed individually.

**Files modified:** `hhs-frontend-v2/src/views/auth/Login.vue`, `hhs-frontend-v2/src/views/auth/Register.vue`, `hhs-frontend-v2/src/utils/errorMessages.ts`, `hhs-frontend-v2/src/__tests__/utils/errorMessages.spec.ts`, `hhs-frontend-v2/src/__tests__/views/LoginAuthError.spec.ts`
**Commit:** `223cecc`
**Applied fix:** `Login.vue` now routes failures through `showError(error, 'auth.loginFailed')`; `Register.vue` gained a `catch (error) { showError(error, 'auth.registerFailed') }`. Added the two flat dictionary keys (within the 30-char cap). Added `LoginAuthError.spec.ts` asserting a wrong-password rejection yields exactly one visible error message (backend Chinese preserved), no success toast, and loading reset — no double toast on the 401/`auth` path.

### CR-02: AI chat context/history not scoped to the requesting user (IDOR)

**Files modified:** `hhs-backend/src/main/java/com/hhs/service/domain/ConversationService.java`, `.../service/domain/MessageService.java`, `.../service/impl/AIChatServiceImpl.java`, `hhs-backend/src/test/java/com/hhs/service/AIChatServiceTest.java`
**Commit:** `408b0f8`
**Applied fix:** `getBySessionId(sessionId, userId)` now always applies a `user_id` predicate (visitors are scoped to `user_id IS NULL`, never to another user's rows). `getRecentHistory`/`buildChatContext` take `userId`. Added `sessionExists(sessionId)` so an empty caller-scoped read can be distinguished from "session does not exist": `getContext` and `chat` throw `RESOURCE_NOT_FOUND` when the session exists but is not owned by the caller. Regression tests: user 2 cannot read user 1's context, and `chat` never calls the model or persists onto another user's session.

### CR-03: SSRF via user-supplied push webhook URL

**Files modified:** `hhs-backend/src/main/java/com/hhs/security/WebhookUrlValidator.java` (new), `.../controller/PushConfigController.java`, `.../service/push/channel/FeishuPushChannel.java`, `.../service/push/channel/WeComPushChannel.java`, `.../dto/PushConfigRequest.java`, `hhs-backend/src/test/java/com/hhs/security/WebhookUrlValidatorTest.java` (new), `hhs-backend/src/test/java/com/hhs/controller/PushConfigControllerTest.java`
**Commit:** `190215e`
**Applied fix:** New `WebhookUrlValidator` (no new dependencies — `java.net.URI` + `InetAddress`): https-only, no userinfo, no non-443 port, rejects `localhost`/`*.localhost`, and rejects loopback / site-local / link-local / any-local / multicast addresses across all resolved addresses (fail-closed on resolution failure). Validation runs at WRITE time in `PushConfigController.updateConfig` and again at SEND time in both webhook channels (defense in depth; send-time rejection returns `PushResult.failed`, never a POST). Rejections raise `BusinessException(VALIDATION_INVALID_PARAMETER, <Chinese detail>)`. Tests cover acceptance of public https and rejection of `http://`, `http://localhost`, `http://127.0.0.1`, `http://169.254.169.254`, `http://10.x`, `file://`, `ftp://`, a userinfo URL, a non-standard port, and a relative path.

### WR-01: OAuth state-expiry compared UTC wall-clock with JVM-local now

**Files modified:** `hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java`, `.../XiaomiHealthServiceImpl.java`
**Commit:** `436bed4`
**Applied fix:** Replaced `ChronoUnit.MINUTES.between(LocalDateTime.ofEpochSecond(..., UTC), LocalDateTime.now())` with `Duration.between(Instant.ofEpochMilli(ts), Instant.now())` in both providers. Removed the now-unused `LocalDate`/`ZoneId`/`ChronoUnit` imports.

### WR-02: `handleBusinessException` appended raw detail after the fixed `SYSTEM_*` message

**Files modified:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java`, `hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java`
**Commit:** `36680f7`
**Applied fix:** Detail is appended only for non-`SYSTEM_*` codes; a `SYSTEM_*` detail is logged, never returned. Test updated to assert the fixed message and that the detail is absent.

### WR-04: `handleValidationException` returned `ex.getMessage()` (and could NPE)

**Files modified:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java`
**Commit:** `36680f7`
**Applied fix:** `_global` field message and legacy `errors` entry now use the fixed Chinese string `提交内容校验未通过，请检查后重试`; raw exception text stays in the log. NPE from `List.of(null)` is structurally removed.

### WR-05: Generic `Exception` handler swallowed 403/400-class exceptions

**Files modified:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java`
**Commit:** `36680f7`
**Applied fix:** Added `@ExceptionHandler(AccessDeniedException.class)` → HTTP 403 + `auth.forbidden` + `没有访问权限`, and `@ExceptionHandler({MethodArgumentTypeMismatchException, MissingServletRequestParameterException})` → HTTP 400 + `validation.invalid_parameter`. Both sit before the catch-all.

### WR-03: OCR failure detail wrote `e.getMessage()` into a user-visible field

**Files modified:** `hhs-backend/src/main/java/com/hhs/service/impl/ScreeningServiceImpl.java`, `.../OcrServiceImpl.java`
**Commit:** `55f98c9`
**Applied fix:** `ScreeningServiceImpl` stores `OCR 处理失败，请稍后重试`; `OcrServiceImpl` stores `识别失败，请稍后重试` (prefix retained so `getOcrStatus` still surfaces it). Internal exception text is logged only.

### WR-07: Every push save showed two success toasts

**Files modified:** `hhs-frontend-v2/src/stores/push.ts`, `hhs-frontend-v2/src/views/user/PushSettings.vue`, `hhs-frontend-v2/src/__tests__/views/ErrorLayerSample.spec.ts`
**Commit:** `bb9b039`
**Applied fix:** Removed `ElMessage.success('配置已保存')` from `saveConfig`; the caller's contextual message (toggle/save) is now the single success toast. Test updated: store success emits zero toasts.

### WR-08: Push save used `profile.saveFailed` fallback copy

**Files modified:** `hhs-frontend-v2/src/stores/push.ts`, `hhs-frontend-v2/src/utils/errorMessages.ts`, `hhs-frontend-v2/src/__tests__/utils/errorMessages.spec.ts`
**Commit:** `bb9b039`
**Applied fix:** Added `'push.saveFailed': '无法保存推送配置，请稍后重试'` and used it in `saveConfig`.

### WR-09: Push store silently swallowed all load failures

**Files modified:** `hhs-frontend-v2/src/stores/push.ts`, `hhs-frontend-v2/src/views/user/PushSettings.vue`
**Commit:** `bb9b039`
**Applied fix:** `loadConfigs` exposes `loadError` (and routes through `showError(..., { silent })`); the view renders `ErrorState` with retry when it is set. `loadHistory`/`loadStats` accept an explicit `{ silent }` (default silent for these secondary blocks) rather than a bare swallow.

### WR-10: Push config values returned in plaintext; frontend mask protocol dead

**Files modified:** `hhs-backend/src/main/java/com/hhs/vo/PushConfigVO.java`, `.../controller/PushConfigController.java`, `hhs-backend/src/test/java/com/hhs/controller/PushConfigControllerTest.java`
**Commit:** `e3aab6d`
**Applied fix:** `PushConfigVO.fromEntity` masks `configValue` (prefix + `****` + last 4). `updateConfig` treats an incoming value containing `****` as "unchanged", so a round-tripped masked value never overwrites the stored secret. Test asserts the masked GET response contains `****` and not the token.

### WR-11: Blood-type field disappeared whenever BMI could not be computed

**Files modified:** `hhs-frontend-v2/src/views/prevention/Profile.vue`
**Commit:** `3eeaf6e`
**Applied fix:** Moved `v-if="bmi"` onto the BMI `<el-col>` only; the blood-type column (with its `data-field`/`:error` bindings) is always rendered.

### WR-12: `fetchProfile` treated every failure as "profile does not exist"

**Files modified:** `hhs-frontend-v2/src/views/prevention/Profile.vue`
**Commit:** `3eeaf6e`
**Applied fix:** Branches on the normalized error — only 404 / `health.profile_not_found` / `resource.not_found` enters create mode; every other failure routes through `showError(err, 'profile.loadFailed')`. The 404 envelope case is handled because the backend returns `Result.failure(404, ...)` inside an HTTP-200 envelope.

### WR-13: Threshold dialog silently dropped server-side validation errors

**Files modified:** `hhs-frontend-v2/src/views/health/Thresholds.vue`, `hhs-frontend-v2/src/utils/showError.ts`, `hhs-frontend-v2/src/__tests__/utils/showError.spec.ts`
**Commit:** `54a9d08`, `6f504b5`
**Applied fix:** Rather than bypassing the single toast outlet with `ElMessage.error`, added an explicit `presentValidation` opt-in to `showError` (default stays validation → 0 toast per D-01). `Thresholds.vue` passes `presentValidation: true` because it has no inline bindings, so a backend 400 is now visible. Also routed its list-load/delete failures through `showError` (`threshold.loadFailed` / `threshold.deleteFailed`).

### WR-14: A 401 showed "已退出登录" instead of "登录已过期"

**Files modified:** `hhs-frontend-v2/src/stores/auth.ts`, `hhs-frontend-v2/src/utils/request.ts`
**Commit:** `7b3c542`
**Applied fix:** `logout({ reason })`; the 401 interceptor calls `logout({ reason: 'expired' })`, which shows `ElMessage.warning(ERROR_MESSAGES['auth.expired'])` instead of the generic info message. Manual logout keeps `已退出登录`. Still exactly one toast (showError stays silent for `kind === 'auth'`).

### WR-15: `focusField` threw on an invalid selector

**Files modified:** `hhs-frontend-v2/src/composables/useFormServerErrors.ts`
**Commit:** `6f504b5`
**Applied fix:** Uses `CSS.escape` when available (with the quote/backslash escape as fallback) and wraps `querySelector` in `try/catch` returning silently.

### WR-16: `ErrorState.handleRetry` did not catch a rejecting retry callback

**Files modified:** `hhs-frontend-v2/src/components/common/ErrorState.vue`
**Commit:** `6f504b5`
**Applied fix:** `try { await props.retry?.() } catch { /* parent owns the error surface */ }`.

### WR-17: Any non-Axios `Error` was classified as a retryable network failure

**Files modified:** `hhs-frontend-v2/src/utils/appError.ts`, `hhs-frontend-v2/src/__tests__/utils/appError.spec.ts`, `hhs-frontend-v2/src/__tests__/views/ErrorLayerSample.spec.ts`
**Commit:** `cf4415f`, `bb9b039`
**Applied fix:** The response-less branch now requires `isAxiosError === true` for `network`; other errors become `unknown` (no retry button). The `'unknown'` `ErrorKind` member is now reachable (aligns IN-02). Tests updated to the corrected contract, plus a new case proving a plain `Error` is not retryable.

### WR-18: Export date strings unvalidated, surfaced as a generic 500

**Files modified:** `hhs-backend/src/main/java/com/hhs/dto/ExportRequest.java`, `.../controller/ExportController.java`
**Commit:** `e3aab6d`
**Applied fix:** `@Pattern(^\d{4}-\d{2}-\d{2}$)` on both date fields, plus a `DateTimeParseException` catch turning shape-valid-but-calendar-invalid input (e.g. `2026-02-30`) into `BusinessException(VALIDATION_INVALID_PARAMETER, "导出日期格式不正确")`.

### WR-19: Rate-limit messages were duplicated

**Files modified:** `hhs-backend/src/main/java/com/hhs/controller/DeviceSyncController.java`
**Commit:** `61e8b44`
**Applied fix:** Both sync endpoints now throw `BusinessException(DEVICE_RATE_LIMIT_EXCEEDED)` with no detail, so the user sees exactly one message.

### WR-20: `PushConfigRequest.configValue` had no length validation

**Files modified:** `hhs-backend/src/main/java/com/hhs/dto/PushConfigRequest.java`, `hhs-backend/src/test/java/com/hhs/controller/PushConfigControllerTest.java`
**Commit:** `190215e`, `e3aab6d`
**Applied fix:** `@Size(max = 500, message = "配置内容不能超过 500 个字符")`, matching `VARCHAR(500)`, plus the CR-03 URL validation. Test asserts a 501-char value returns HTTP 400 and never reaches the mapper.

### WR-21: `handleSystemException` discarded the actionable Chinese detail

**Files modified:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java`, `hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java`
**Commit:** `36680f7`
**Applied fix:** For non-`SYSTEM_*` codes the Chinese detail is surfaced, but only when `isUserFacingChinese(detail)` is true (at least one CJK ideograph). Provider/HTTP-client English text such as `Failed to exchange authorization code: ...` therefore never reaches the user — a leak guard beyond the literal review suggestion. `SYSTEM_*` keeps the fixed message and logs the detail. Added a no-detail fallback test.

## Skipped Issues

### WR-06: Every business error serialized with numeric envelope `code: 500`

**File:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java:61`, `hhs-backend/src/main/java/com/hhs/common/Result.java:81-83`
**Reason:** The suggested fix (`Result.failure(ec.getHttpStatus(), message)` + `withErrorCode`) changes the numeric `code` produced for every business failure from 500 to the HTTP status. That is explicitly out of scope: the fix constraints forbid changing `Result.code` numeric semantics, and existing tests (`GlobalExceptionHandlerTest`, `appError.spec.ts`, `request.spec.ts`) assert the 500 fallback as the documented contract. Only the stale `appError.ts` comment claiming the contract was updated (commit `cf4415f`); the behavior change is flagged here for a dedicated follow-up.

**Note on the frontend half of WR-06:** the `appError.ts` comment that asserted business envelopes are always 500 was updated (commit `cf4415f`) to name WR-06 and explain why the numeric fallback is retained; no behavioral change was made because it depends on the backend decision above.

**Out of scope (Info tier):** the 19 Info findings are excluded by `fix_scope: critical_warning` and were not applied. Four Info items that overlapped fixed Warnings were folded in: IN-02 (`'unknown'` now reachable, via WR-17), IN-08 (`Locale.ROOT` in `DeviceSyncOrchestrationServiceImpl` and `PushConfigController`, commit `e3aab6d`), IN-12 (skip unchanged push saves, commit `bb9b039`), IN-18 (fixed OCR date message, commit `55f98c9`).

---

_Fixed: 2026-11-07_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_