---
phase: 11-error-foundation
reviewed: 2026-11-06T00:00:00Z
depth: standard
files_reviewed: 41
files_reviewed_list:
  - hhs-backend/src/main/java/com/hhs/common/Result.java
  - hhs-backend/src/main/java/com/hhs/controller/DeviceSyncController.java
  - hhs-backend/src/main/java/com/hhs/controller/PushConfigController.java
  - hhs-backend/src/main/java/com/hhs/dto/AIClassifyRequest.java
  - hhs-backend/src/main/java/com/hhs/dto/AlertVO.java
  - hhs-backend/src/main/java/com/hhs/dto/ExportRequest.java
  - hhs-backend/src/main/java/com/hhs/dto/HealthMetricRequest.java
  - hhs-backend/src/main/java/com/hhs/dto/HealthProfileRequest.java
  - hhs-backend/src/main/java/com/hhs/dto/PushConfigRequest.java
  - hhs-backend/src/main/java/com/hhs/dto/RealtimeMetricRequest.java
  - hhs-backend/src/main/java/com/hhs/dto/UserThresholdRequest.java
  - hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java
  - hhs-backend/src/main/java/com/hhs/service/impl/AIChatServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/DeviceSyncOrchestrationServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/ScreeningServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/UserThresholdServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/service/impl/XiaomiHealthServiceImpl.java
  - hhs-backend/src/main/java/com/hhs/vo/FieldErrorVO.java
  - hhs-frontend-v2/src/api/alert.ts
  - hhs-frontend-v2/src/api/metric-meta.ts
  - hhs-frontend-v2/src/api/prevention.ts
  - hhs-frontend-v2/src/App.vue
  - hhs-frontend-v2/src/assets/styles/theme.css
  - hhs-frontend-v2/src/components/common/ErrorState.vue
  - hhs-frontend-v2/src/components/common/FormErrorSummary.vue
  - hhs-frontend-v2/src/composables/useFormServerErrors.ts
  - hhs-frontend-v2/src/stores/alert.ts
  - hhs-frontend-v2/src/stores/auth.ts
  - hhs-frontend-v2/src/stores/metricMeta.ts
  - hhs-frontend-v2/src/stores/push.ts
  - hhs-frontend-v2/src/types/api.ts
  - hhs-frontend-v2/src/types/axios.d.ts
  - hhs-frontend-v2/src/utils/appError.ts
  - hhs-frontend-v2/src/utils/authSession.ts
  - hhs-frontend-v2/src/utils/errorMessages.ts
  - hhs-frontend-v2/src/utils/request.ts
  - hhs-frontend-v2/src/utils/showError.ts
  - hhs-frontend-v2/src/views/health/Thresholds.vue
  - hhs-frontend-v2/src/views/prevention/Profile.vue
  - hhs-frontend-v2/src/views/user/PushSettings.vue
findings:
  critical: 3
  warning: 21
  info: 19
  total: 43
status: issues_found
---

# Phase 11: Code Review Report

**Reviewed:** 2026-11-06T00:00:00Z
**Depth:** standard
**Files Reviewed:** 41
**Status:** issues_found

## Summary

The phase's core architecture is largely sound: `Result` uses per-field `@JsonInclude` (so `data: null` survives success responses), `normalizeApiError` is genuinely idempotent on `ApiError` and orders its branches cancel → validation → 401 → network → real-5xx → envelope → fallback, `request.ts` is free of `ElMessage`, `showError` is the single toast exit and never uses `dangerouslyUseHTMLString`, and `FormErrorSummary` keeps its `<ol>` outside the `ElAlert` slot. `theme.css` correctly scopes `--el-message-text-color` to `.el-message--error` only.

However, the phase introduced or exposed three ship-blocking defects. The most severe is a **regression on the authentication path**: `request.ts` stopped displaying anything, but `Login.vue` still has an empty `catch` (and `Register.vue` has no `catch` at all), so a wrong password or registration failure now produces **no user-visible feedback whatsoever**. Two security defects were also confirmed in reviewed files: AI chat session context is not user-scoped and session IDs are guessable millisecond timestamps, and push webhook configuration is a free-form URL POSTed to by the server (SSRF).

Several secondary issues match the review focus: `handleBusinessException` still appends `ex.getDetail()` verbatim after the fixed `SYSTEM_*` message, `ScreeningServiceImpl` writes `e.getMessage()` into the user-visible OCR error field, the generic `Exception` handler swallows `AccessDeniedException` (403 becomes "服务暂时不可用"), and the push store emits duplicate success toasts and uses a category-wrong fallback key.

A note on one self-corrected finding: the OAuth state-expiry code mixes a UTC wall-clock timestamp with `LocalDateTime.now()`, but that comparison lives inside an `instanceof String` branch that `storeState` (which writes a `Long`) can never reach. It is therefore reported as a Warning (dead code with latent incorrect time math), not as a live "device binding always fails" bug.

**XSS check (review focus 7): verified clean in the error path.** There is no `v-html`, `innerHTML`, `dangerouslyUseHTMLString`, or string-concatenated markup anywhere in `showError.ts`, `appError.ts`, `errorMessages.ts`, `useFormServerErrors.ts`, `FormErrorSummary.vue`, `ErrorState.vue`, or `theme.css`. VNode text is passed as a text child (`h('span', ..., shown)`) and rendered by Element Plus inside `<p class="el-message__content">` (single-element slot, not an attribute-context injection). Vue interpolation escapes the summary items. The only `v-html` sites in the app (`views/ai/Chat.vue:209,328`, `components/ai-floating-ball/FloatingChatPanel.vue:65`) are outside this phase's scope and do sanitize through `DOMPurify.sanitize` after `marked.parse` — no finding.

**Necessity assessment not performed:** `$HOME/.claude/gsd-core/references/necessity-review.md` could not be read (file absent), so `necessity:` is omitted from the frontmatter per the review contract. Dead-code observations below are reported under Info/Warning only.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: Auth failures are now completely silent — the interceptor stopped displaying but login/register callers swallow the error

**File:** `hhs-frontend-v2/src/utils/request.ts:54-73` (with `hhs-frontend-v2/src/views/auth/Login.vue:210-216`, `hhs-frontend-v2/src/views/auth/Register.vue:268-281`)
**Issue:** The phase rewrote `request.ts` to "translate, don't display": every rejection path now does `Promise.reject(apiError)` with no `ElMessage` and no toast. `Login.vue` still contains:

```ts
try { await authStore.login(form) } catch {
  // authStore.login 和 Axios 拦截器已处理错误提示
}
```

The comment is now false — no interceptor displays anything, so a wrong password, a rate-limited login, or a disabled account produces *zero* feedback; the spinner simply stops. `Register.vue` has no `catch` at all (`try { await authStore.register(...) } finally { ... }`), so the `ApiError` becomes an unhandled promise rejection (no `window.onunhandledrejection` handler exists in the app). This is a user-facing regression introduced by the phase's change to `request.ts`, and it breaks the most security-sensitive flow in the app.
**Fix:** Make the call sites the display owner, e.g. in `Login.vue`/`Register.vue`:
```ts
try {
  await authStore.login(form)
} catch (error) {
  showError(error, 'auth.expired')
}
```
Add a `catch (error) { showError(error, ...) }` to `Register.vue` as well, and add an `auth.*` dictionary key for registration failures. Do not restore display inside `request.ts`.

### CR-02: AI chat session context and history are not scoped to the requesting user (IDOR / cross-user disclosure)

**File:** `hhs-backend/src/main/java/com/hhs/service/impl/AIChatServiceImpl.java:179, 288-290`
**Issue:** `getContext(String sessionId, Long userId)` documents `userId` as "for authorization" but never uses it:

```java
public ChatContextVO getContext(String sessionId, Long userId) {
    return messageService.buildChatContext(sessionId);   // no user filter
}
```

`MessageService.buildChatContext` and `ConversationService.getBySessionId` filter only by `sessionId`. `AIController.getSessionContext` (`/api/ai/chat/sessions/{sessionId}/context`, `@PreAuthorize("isAuthenticated()")`) therefore returns any user's questions and answers to any authenticated caller who supplies a session ID. Session IDs are generated client-side as `session_${Date.now()}` (`stores/ai.ts:33`, `stores/floatingAi.ts:56/130`), so they are guessable/brute-forceable across a narrow timestamp range.

The same gap exists in the chat path at line 179: `messageService.getRecentHistory(sessionId, 5)` is not user-scoped. If user A sends a message with user B's session ID, A's request is seeded with B's conversation history as AI context and A's turn is persisted onto B's session row (`createConversationEntity` stores A's `userId` under B's `sessionId`), causing both data disclosure and session corruption.
**Fix:** Thread the caller identity through every lookup:
```java
// ConversationService.getBySessionId(sessionId, userId)
.eq(AIConversation::getSessionId, sessionId)
.eq(AIConversation::getUserId, userId)   // always, not `userId != null`
```
and change `getContext` to pass `userId`, `MessageService.getRecentHistory(sessionId, userId, rounds)`, and `buildChatContext(sessionId, userId)`. Reject with `RESOURCE_NOT_FOUND` when the session belongs to another user.

### CR-03: Server-side request forgery via user-supplied push webhook URL

**File:** `hhs-backend/src/main/java/com/hhs/controller/PushConfigController.java:97-151, 180-227` (sink: `service/push/channel/FeishuPushChannel.java:43-49, 69-70`, `WeComPushChannel.java:43-49, 68-69`)
**Issue:** `updateConfig` accepts an arbitrary `configValue` string (no URL validation, no scheme/host allowlist) and persists it. `getWebhookUrl` returns it verbatim and `pushRestTemplate.postForObject(webhookUrl, ...)` issues a server-side POST. `POST /api/push/config/{channelType}/test` triggers this from an authenticated session. Any authenticated user can point the backend at `http://169.254.169.254/latest/meta-data/...`, an internal admin service, or a metadata endpoint. The endpoint performs a blind SSRF (the response body is only partially reflected as success/failure), but it is still an internal network reachability primitive usable for port scanning / internal POSTs. This sink predates the phase, but the reviewed controller is the write path that admits it and the phase explicitly hardened this file's error messages without addressing it.
**Fix:** Validate the URL at write time and again at send time:
```java
URI uri = URI.create(configValue);
if (!"https".equalsIgnoreCase(uri.getScheme())) throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "仅支持 https 推送地址");
InetAddress addr = InetAddress.getByName(uri.getHost());
if (addr.isLoopbackAddress() || addr.isSiteLocalAddress() || addr.isLinkLocalAddress()) {
    throw new BusinessException(ErrorCode.VALIDATION_INVALID_PARAMETER, "推送地址不可用");
}
```
Prefer an allowlist of known webhook hosts (`qyapi.weixin.qq.com`, `open.feishu.cn`, ...), disable redirects on `pushRestTemplate`, and add `@Size` on `configValue`.

## Warnings

### WR-01: OAuth state-expiry compares a UTC wall-clock value with the JVM-local `now` — latent, currently in a dead branch

**File:** `hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java:664-691` (esp. 679-686), `hhs-backend/src/main/java/com/hhs/service/impl/XiaomiHealthServiceImpl.java:542-567` (esp. 557-564)
**Issue:** If the `value instanceof String` branch ever runs, it computes:

```java
long ageMinutes = ChronoUnit.MINUTES.between(
        LocalDateTime.ofEpochSecond(timestamp / 1000, 0, java.time.ZoneOffset.UTC),
        LocalDateTime.now());
if (ageMinutes > STATE_EXPIRATION_MINUTES) { return null; }
```

`ofEpochSecond(..., UTC)` produces a UTC *wall time* as a `LocalDateTime`, while `LocalDateTime.now()` uses the JVM default zone; on `Asia/Shanghai` the age computes as ~480 minutes and every callback would be rejected. Today this is unreachable: `storeState` writes a `Long`, and `GenericJackson2JsonRedisSerializer` (configured with `DefaultTyping.NON_FINAL`, which excludes the final `Long` type) deserializes it to a `Number`, so the `instanceof Number` branch at line 664 wins. The bug is latent — and it is dead code worth deleting either way.
**Fix:** Either delete the `String` branch (the JSON timestamp check is not needed because Redis TTL already expires the key), or compare instants:
```java
Instant issuedAt = Instant.ofEpochMilli(timestamp);
if (Duration.between(issuedAt, Instant.now()).toMinutes() > STATE_EXPIRATION_MINUTES) { return null; }
```
Apply identically to both providers.

### WR-02: `handleBusinessException` appends the raw `detail` after the fixed `SYSTEM_*` message

**File:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java:50-63`
**Issue:** The handler computes a fixed message with `systemFallbackMessage`, then unconditionally appends `": " + ex.getDetail()`. For `SYSTEM_*` codes this defeats the "fixed Chinese message" guarantee: any `BusinessException(ErrorCode.SYSTEM_*, "<technical detail>")` returns the technical text to the client (e.g. `AiParseServiceImpl.java:590` embeds failed metric names, and `OcrServiceImpl.java:371` does the same), so detail meant for logs becomes user-visible copy.
**Fix:** Only append detail for non-`SYSTEM_*` codes:
```java
if (ex.getDetail() != null && !ec.name().startsWith("SYSTEM_")) {
    message = message + ": " + ex.getDetail();
}
```

### WR-03: OCR failure detail writes `e.getMessage()` into a user-visible field

**File:** `hhs-backend/src/main/java/com/hhs/service/impl/ScreeningServiceImpl.java:145-149` (rendered at `hhs-frontend-v2/src/views/data-input/components/OcrHistory.vue:97-99`)
**Issue:** The maintenance path stores `"调度OCR处理失败: " + e.getMessage()` into `abnormalSummary`. `getOcrStatus` (line 237) surfaces anything starting with `识别失败`; the sibling `OcrServiceImpl.java:193` stores `"识别失败: " + e.getMessage()` into the same column. The frontend renders that value verbatim, so internal exception text (file paths from `Files.copy`, JDBC/Redis errors, upstream OCR provider text) is shown to the end user.
**Fix:** Store a fixed Chinese string and log the detail:
```java
log.error("Failed to schedule OCR for report {}", reportId, e);
reportStatusService.updateOcrStatus(reportId, "FAILED", "OCR 处理失败，请稍后重试");
```

### WR-04: `handleValidationException` returns `ex.getMessage()` to the user

**File:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java:138-149`
**Issue:** The `_global` field message and the legacy `errors` entry are both `ex.getMessage()`. `ValidationException` messages are developer-facing (e.g. `HV000028: Unexpected exception during isValid call`), not user-facing Chinese. This is the one remaining branch that ships raw exception text. Additionally, `List.of(ex.getMessage())` throws NPE if `getMessage()` is null, which then escapes the handler.
**Fix:** Use a fixed user-facing message for `_global` (e.g. `"提交内容校验未通过，请检查后重试"`) and log the detail.

### WR-05: Generic `Exception` handler swallows Spring MVC / security exceptions — 403 and 400-class errors become "服务暂时不可用"

**File:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java:197-201`
**Issue:** `@ExceptionHandler(Exception.class)` is the only match for `AuthorizationDeniedException` (thrown by `@PreAuthorize`, e.g. `ExportController`, `DevicePlatformConfigAdminController`), `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException`, and `HttpRequestMethodNotSupportedException`. These client/permission errors are returned as envelope `code: 500` with "服务暂时不可用，请稍后重试". `RestAccessDeniedHandler` (configured in `SecurityConfig`) never runs for method-security denials, because MVC handles the exception before it escapes the servlet. A user without `ROLE_ADMIN` is told the service is down.
**Fix:** Add explicit handlers before the catch-all:
```java
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(403).body(Result.failure(403, "security.violation", "没有访问权限"));
}
@ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
public Result<Void> handleBadRequest(Exception ex) {
    return Result.failure(400, "validation.invalid_parameter", "请求参数有误");
}
```
or rethrow `AccessDeniedException` from the catch-all so the security filter handles it.

### WR-06: Every business error is serialized with numeric envelope `code: 500`

**File:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java:61`; `hhs-backend/src/main/java/com/hhs/common/Result.java:81-83, 140-146`
**Issue:** `Result.failure(ec.getCode(), message)` selects the `failure(String, String)` overload; `ec.getCode()` is a non-numeric string (`"auth.forbidden"`, `"resource.exists"`, ...), so `parseCodeToInt` always returns `500`. A 400 validation business error, a 403 ownership violation, and a 409 duplicate all arrive as `code: 500` + `errorCode`. The frontend survives because `appError.ts` only checks `code !== 200`, but the contract is broken and any caller checking `res.code === 403`/`409` (several legacy views do, e.g. `DeviceSync.vue:492`) silently misfires. `handleHttpMessageNotReadableException` (line 181) uses a String literal code and likewise collapses to 500.
**Fix:** Use the numeric status overload and keep the string code separate:
```java
Result<Void> result = Result.failure(ec.getHttpStatus(), message);
return result.withErrorCode(ec.getCode());
```
Then update the `appError.ts` comment that asserts business envelopes are always 500, and pass `400` explicitly in `handleHttpMessageNotReadableException`.

### WR-07: Push store and view both emit a success toast — every save shows two

**File:** `hhs-frontend-v2/src/stores/push.ts:54`, `hhs-frontend-v2/src/views/user/PushSettings.vue:220-224, 236-238`
**Issue:** `saveConfig` emits `ElMessage.success('配置已保存')`; `handleToggle` then emits `${label}已启用/已禁用`, and `handleSaveConfig` emits `配置已保存` again. Toggling a channel produces two stacked success toasts.
**Fix:** Let exactly one layer own success feedback. Simplest: remove the `ElMessage.success` from `saveConfig` and keep the caller's contextual message; `handleSaveConfig` should then not duplicate it.

### WR-08: Push save uses the wrong fallback copy key

**File:** `hhs-frontend-v2/src/stores/push.ts:58`
**Issue:** `showError(error, 'profile.saveFailed')` falls back to "无法保存健康档案，请检查填写内容后重试" when the backend message is empty. The user is on the push settings page saving a webhook, and is told their health profile failed. There is no push-specific dictionary key.
**Fix:** Add `'push.saveFailed': '无法保存推送配置，请稍后重试'` to `errorMessages.ts` and use it here.

### WR-09: Push store silently swallows all load failures

**File:** `hhs-frontend-v2/src/stores/push.ts:32-36, 95-97, 109-111`
**Issue:** `loadConfigs`, `loadHistory`, and `loadStats` use bare `catch (error) { /* ... failed */ }`. On failure the config list renders empty with no error state and no toast, and the `v-loading` mask simply disappears. The page's `el-empty description="加载中..."` also wrongly implies loading. This is the class of silent failure the phase exists to eliminate.
**Fix:** Surface load failures through the single outlet (`showError(error, 'server.unavailable')`) or expose a `loadError` ref the view renders as `ErrorState`. For background refreshes pass `{ silent: true }` deliberately rather than swallowing.

### WR-10: Push config values (webhook URLs / email addresses) are returned in plaintext; the frontend mask protocol is dead

**File:** `hhs-backend/src/main/java/com/hhs/controller/PushConfigController.java:61` (`PushConfigVO.fromEntity` returns `entity.getConfigValue()` raw); `hhs-frontend-v2/src/views/user/PushSettings.vue:211-213, 229-231`
**Issue:** Feishu/WeCom webhook URLs embed secret tokens; the GET endpoints hand them back verbatim, and the client renders them into an editable input. The frontend contains a masking protocol (`configValue.includes('****')`) that can never trigger, because the backend never masks.
**Fix:** Mask on the server (`PushConfigVO` should return e.g. `"https://open.feishu.cn/****abcd"` when a value exists) and add a separate "value provided" boolean; only accept a new value when the client sends a non-masked one.

### WR-11: Blood-type field disappears whenever BMI cannot be computed

**File:** `hhs-frontend-v2/src/views/prevention/Profile.vue:101-131`
**Issue:** `<el-row v-if="bmi">` wraps **both** the read-only BMI column and the editable 血型 column. `bmi` is `null` until both `heightCm` and `weightKg` are set, so a user with height/weight not yet filled in cannot see or select a blood type. If the backend returns a `bloodType` validation error for such a profile, `fieldErrors['bloodType']` is set but there is no form item to display it, so the summary links to a nonexistent `data-field="bloodType"` target.
**Fix:** Move `v-if="bmi"` onto the BMI `<el-col>` only:
```html
<el-row :gutter="20">
  <el-col v-if="bmi" :xs="24" :md="12"> <!-- BMI --> </el-col>
  <el-col :xs="24" :md="12"> <!-- bloodType --> </el-col>
</el-row>
```

### WR-12: `fetchProfile` treats every failure as "profile does not exist" and enters create mode

**File:** `hhs-frontend-v2/src/views/prevention/Profile.vue:262-268`
**Issue:** The `catch` sets `editing.value = true` regardless of the error kind. A network outage or a 500 (which `silent` only suppresses display for, never classifies away) puts the user into "create profile" mode with an empty form. Submitting then fails with "该用户已存在健康档案", or on a partial outage may create a duplicate. The intent (404 → create) is correct; the implementation catches too broadly.
**Fix:** Branch on the normalized kind/status:
```ts
const err = normalizeApiError(error)
if (err.kind === 'business' && err.status === 404) editing.value = true
else showError(err, 'profile.loadFailed')
```
Note the backend currently returns `Result.failure(404, ...)` inside an HTTP-200 envelope, so also normalize the backend to a real 404 (see WR-06) or match on `err.code`.

### WR-13: Threshold dialog silently drops server-side validation errors

**File:** `hhs-frontend-v2/src/views/health/Thresholds.vue:198-209`
**Issue:** `handleSubmit` calls `showError(error, 'threshold.duplicate', ...)`. `showError` returns immediately for `kind === 'validation'` by design (inline rendering is the caller's job), but this page does not use `useFormServerErrors` and has no `:error` bindings or summary. A backend 400 (e.g. `metricKey` exceeds 64 chars, or the `UserThresholdRequest` constraints fire) therefore shows **nothing** and the dialog stays open with no explanation.
**Fix:** Adopt `useFormServerErrors` here (`data-field`/`:error`/`clearField` plus `FormErrorSummary`), or at minimum special-case validation in the catch and show `ElMessage.error(err.message)`.

### WR-14: A 401 shows "已退出登录", not "登录已过期"

**File:** `hhs-frontend-v2/src/stores/auth.ts:40-46`, `hhs-frontend-v2/src/utils/showError.ts:134-136`, `hhs-frontend-v2/src/utils/errorMessages.ts:24`
**Issue:** `showError` deliberately emits nothing for `kind === 'auth'`, delegating to `authStore.logout()`. But `logout()` always shows `ElMessage.info('已退出登录')`, including when invoked by the 401 interceptor for an expired session. The user is told they logged out, not that their session expired. The dictionary entry `'auth.expired': '登录已过期，请重新登录'` is defined but never consumed anywhere.
**Fix:** Give the interceptor path a dedicated message without double-toasting, e.g. `logout({ reason: 'expired' })` that shows `ElMessage.warning(ERROR_MESSAGES['auth.expired'])`, suppressing the generic info message in that case.

### WR-15: `focusField` throws instead of "silently returning" on an invalid selector

**File:** `hhs-frontend-v2/src/composables/useFormServerErrors.ts:106-124`
**Issue:** The JSDoc and the T-11-15 note claim illegal selectors return silently, but the code only escapes `"` and `\` and then calls `document.querySelector(...)` with no `try/catch`. Any field name containing a newline, tab, or other control character produces a `DOMException: SyntaxError` that propagates out of the click handler. (Quotes/backslashes are handled, so CSS injection is not achievable — the attribute value stays quoted — but the crash is.)
**Fix:**
```ts
let container: HTMLElement | null = null
try { container = document.querySelector<HTMLElement>(`[data-field="${CSS.escape(field)}"]`) } catch { return }
```
with the existing quote/backslash escape retained as a fallback when `CSS.escape` is unavailable.

### WR-16: `ErrorState.handleRetry` does not catch a rejecting retry callback

**File:** `hhs-frontend-v2/src/components/common/ErrorState.vue:38-40`
**Issue:** `async function handleRetry() { await props.retry?.() }` — Vue does not await an event handler's returned promise, so a rejection becomes an unhandled promise rejection with no feedback. `showError` guards this path (`Promise.resolve(retryFn()).catch(() => undefined)`); `ErrorState` does not.
**Fix:**
```ts
async function handleRetry(): Promise<void> {
  try { await props.retry?.() } catch { /* parent owns the error surface */ }
}
```

### WR-17: Any non-Axios `Error` is classified as a retryable network failure

**File:** `hhs-frontend-v2/src/utils/appError.ts:102-112`
**Issue:** Step 4 is `if (!err?.response) return new ApiError(..., 'network', ..., true)`. A plain `TypeError`/`RangeError` thrown by application code inside a `try` block has no `response`, so `showError(error, ...)` presents "网络连接异常，请检查网络后重试" **with a Retry button**, masking a real programming bug as connectivity. The `'unknown'` member of `ErrorKind` is never produced by any branch.
**Fix:** Distinguish Axios errors:
```ts
const isAxios = err?.isAxiosError === true
if (isAxios && !err.response) { return new ApiError('网络连接异常，请检查网络后重试', 'network', undefined, undefined, {}, undefined, true) }
```
and route other errors to `'unknown'` (fallback) without a retry button.

### WR-18: Export date strings are unvalidated and surface as a generic server error

**File:** `hhs-backend/src/main/java/com/hhs/dto/ExportRequest.java:24-27`, `hhs-backend/src/main/java/com/hhs/controller/ExportController.java:88-93`
**Issue:** `startDate`/`endDate` are plain `String` with no `@Pattern`/`@DateTimeFormat`; `ExportController.parseDate` calls `LocalDate.parse` and lets `DateTimeParseException` escape. That exception is not a `ValidationException`, so it lands in `@ExceptionHandler(Exception.class)` → envelope 500 "服务暂时不可用，请稍后重试". A malformed user input is reported as an outage.
**Fix:** Validate in the DTO:
```java
@Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "日期格式应为 yyyy-MM-dd")
private String startDate;
```
and/or catch `DateTimeParseException` in `ExportController` and return `Result.failure(400, "validation.invalid_parameter", "导出日期格式不正确")`.

### WR-19: Rate-limit messages are duplicated because the handler prefixes the code message

**File:** `hhs-backend/src/main/java/com/hhs/controller/DeviceSyncController.java:270-271, 299-300` (`AIChatServiceImpl.java:165-168` has the same pattern)
**Issue:** The exception detail already restates the rate-limit condition ("同步请求过于频繁，请 60 秒后再试"), and `handleBusinessException` prepends `ec.getMessage()` ("同步请求过于频繁，请稍后再试"). The user sees "同步请求过于频繁，请稍后再试: 同步请求过于频繁，请 60 秒后再试".
**Fix:** Choose one source of truth — pass only the parameterized fragment as `detail`, or make the handler's base message generic for these codes.

### WR-20: `PushConfigRequest.configValue` has no length validation against `VARCHAR(500)`

**File:** `hhs-backend/src/main/java/com/hhs/dto/PushConfigRequest.java:24-28` (column: `schema.sql:216` `config_value VARCHAR(500)`)
**Issue:** No `@Size`/URL constraint. A pasted value longer than 500 chars is accepted by validation and then fails at the mapper with a `DataIntegrityViolationException`, which the generic handler converts to "服务暂时不可用". The user cannot tell they exceeded a limit.
**Fix:** Add `@Size(max = 500, message = "配置内容不能超过 500 个字符")` and share the URL validation from CR-03.

### WR-21: `handleSystemException` discards the Chinese detail the phase added

**File:** `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java:69-78`
**Issue:** The handler always returns `systemFallbackMessage(errorCode, "zh")` and never uses `ex.getMessage()`, even though the phase carefully replaced English provider errors with actionable Chinese details (`HuaweiHealthServiceImpl.java:159-160`, `XiaomiHealthServiceImpl.java:146-147`: "授权凭证换取失败，请重新发起连接"). Any caller that surfaces `SystemException` therefore shows the bare `ErrorCode` message ("认证失败") instead.
**Fix:** For non-`SYSTEM_*` codes, return `ex.getMessage()` after verifying it contains no internal text; for `SYSTEM_*`, keep the fixed message and log the detail.

## Info

### IN-01: `Result.failure(ErrorCode, String)` is unused and does not set `errorCode`

**File:** `hhs-backend/src/main/java/com/hhs/common/Result.java:89-92`
**Issue:** The overload concatenates `errorCode.getMessage() + ": " + detail` but leaves `errorCode` unset, producing an envelope whose message has a prefix but whose machine code is absent. No production caller uses it (`UserController` only uses `failure(ErrorCode.AUTH_RATE_LIMITED)`).
**Fix:** Remove it or route it through `failure(int, String, String)`.

### IN-02: `ErrorKind` declares `'unknown'` but no branch produces it

**File:** `hhs-frontend-v2/src/utils/appError.ts:5-12, 137-138`
**Issue:** The fallback step returns `'server'`, so `'unknown'` is unreachable. Delete the member or use it for the non-Axios shape described in WR-17.

### IN-03: The validation branch ignores HTTP status despite the stated contract

**File:** `hhs-frontend-v2/src/utils/appError.ts:79-94`
**Issue:** The comment says "HTTP 400 + fieldErrors", but the code only checks `Array.isArray(data?.fieldErrors)`. A 500/401 body that happened to include `fieldErrors` would be reclassified as validation (zero toast + inline fields that may not exist). Tests do not cover a non-400 status carrying `fieldErrors`.
**Fix:** Add `status === 400 &&` (or `status >= 400 && status < 500`) to the condition.

### IN-04: `dto/AIClassifyRequest` is dead code

**File:** `hhs-backend/src/main/java/com/hhs/dto/AIClassifyRequest.java:11`
**Issue:** The controller uses `com.hhs.dto.ai.AIClassifyRequest` (a record with `title`/`content`). The older class in `dto/` has different fields (`content`/`category`/`context`) and is referenced only by `ValidationTest`. The phase modified its validation messages, spending effort on an unreachable class.

### IN-05: `RealtimeMetricRequest.metricType` "alias" is unreachable

**File:** `hhs-backend/src/main/java/com/hhs/dto/RealtimeMetricRequest.java:19-22` (`RealtimeController.java:62-64`)
**Issue:** `metricKey` is `@NotBlank`, so a request supplying only `metricType` fails Bean Validation before the controller's `metricKey != null ? metricKey : metricType` fallback can run. The compatibility shim never executes.
**Fix:** Relax `metricKey` and add a class-level "one of" constraint, or delete `metricType`.

### IN-06: `UserThresholdRequest.userId` is mandatory but ignored

**File:** `hhs-backend/src/main/java/com/hhs/dto/UserThresholdRequest.java:15-16`
**Issue:** `@NotNull userId` forces clients to send a user identifier that `UserThresholdController` discards (it uses `SecurityUtils.getCurrentUserId()`), and invites clients to believe they can write to another user.
**Fix:** Remove the field or stop validating it.

### IN-07: Unused imports in `HuaweiHealthServiceImpl`

**File:** `hhs-backend/src/main/java/com/hhs/service/impl/HuaweiHealthServiceImpl.java:39, 41`
**Issue:** `java.time.LocalDate` and `java.time.ZoneId` are imported but never referenced (the code uses `toLocalDate()` and `ZoneOffset.UTC`). Google Checkstyle reports these as warnings (does not fail `checkstyle:check` at default severity).

### IN-08: Locale-sensitive case conversion for enum lookup

**File:** `hhs-backend/src/main/java/com/hhs/controller/PushConfigController.java:80`, `hhs-backend/src/main/java/com/hhs/service/impl/DeviceSyncOrchestrationServiceImpl.java:54`
**Issue:** `channelType.toUpperCase()` / `platform.toLowerCase()` use the default locale. Under a Turkish locale `"email".toUpperCase()` yields `"EMAİL"` and `ChannelType.valueOf` throws → "推送渠道类型无效".
**Fix:** Use `toUpperCase(Locale.ROOT)` / `toLowerCase(Locale.ROOT)`.

### IN-09: `theme.css` message overrides — color correctly scoped, padding/line-height global

**File:** `hhs-frontend-v2/src/assets/styles/theme.css:1190-1205`
**Issue:** `.el-message--error` correctly scopes `--el-message-text-color`, so success/warning toasts keep Element Plus colors (verified against `element-plus/dist/index.css`). `.el-message` padding and `.el-message__content` line-height apply to all types, which is intended but worth noting: a future success-toast redesign must account for the 12/16 padding and 1.5 line height. No defect.

### IN-10: `uploadReport` leaves an orphaned file when post-copy validation fails

**File:** `hhs-backend/src/main/java/com/hhs/service/impl/ScreeningServiceImpl.java:107, 119-125`
**Issue:** The file is copied to disk before the `reportDateStr` parse; the parse throws inside the transaction, so the DB row rolls back but the file remains. Repeated attempts accumulate unreferenced files.
**Fix:** Delete `target` in the catch, or parse the date before the copy.

### IN-11: `FormErrorSummary` id counter collides under HMR

**File:** `hhs-frontend-v2/src/components/common/FormErrorSummary.vue:4, 19`
**Issue:** The module-level `summaryIdSeq` resets when Vite hot-reloads the module while already-mounted instances keep their `titleId`, producing duplicate `form-error-summary-title-N` ids on the page. Production is unaffected.
**Fix:** Derive the id from `useId()` when the Vue floor allows (3.5+), or add a random suffix.

### IN-12: PushSettings saves on every blur, including unchanged values

**File:** `hhs-frontend-v2/src/views/user/PushSettings.vue:46, 227-239`
**Issue:** `@blur="handleSaveConfig(config)"` fires every time the input loses focus, even if the value is unchanged, producing a redundant PUT and (per WR-07) extra toasts. The `****` branch at lines 229-231 is dead because the backend never masks (WR-10).
**Fix:** Track the last-saved value and skip when unchanged.

### IN-13: `HealthProfileRequest.gender` annotations disagree on empty strings

**File:** `hhs-backend/src/main/java/com/hhs/dto/HealthProfileRequest.java:20-22`
**Issue:** `@Size(min = 1, max = 16)` rejects `""`, while `@Pattern(regexp = "^(male|female|other|男|女|其他)?$")` explicitly allows empty. The frontend initializes `gender: ''`, so a blank gender submitted with other edits returns "性别长度需在 1 至 16 个字符之间" instead of being treated as optional.
**Fix:** Drop `min = 1` if blank is legal.

### IN-14: The `silent` flag is now inert in `request.ts`

**File:** `hhs-frontend-v2/src/utils/request.ts:69-72`, `hhs-frontend-v2/src/types/axios.d.ts:6-9`
**Issue:** The interceptor no longer displays anything, so `silent` has no effect there, yet callers still pass it and it remains typed. It is effectively a reserved flag that only becomes meaningful if `showError` is later wired into the interceptor.
**Fix:** Document it as reserved until then, or remove the flag and the `silent` plumbing from the API modules and stores.

### IN-15: The push `testChannel` failure branch is unreachable

**File:** `hhs-frontend-v2/src/stores/push.ts:74-77` (`PushConfigController.java:220-226`)
**Issue:** The backend returns `Result.failure(400, "推送发送失败，请稍后重试")` on a failed test, which the interceptor rejects (non-200 envelope). The resolved path therefore always has `data.success === true`, so the `else` branch and its `showError(new ApiError(...))` never execute.
**Fix:** Remove the dead branch, or make the backend always return `Result.success` with `success: false` if the client is expected to inspect it.

### IN-16: `focusField('_global')` is always a no-op

**File:** `hhs-frontend-v2/src/composables/useFormServerErrors.ts:106-124`
**Issue:** `_global` entries render in the summary, and clicking one emits `focus-field="_global"`; no form item carries `data-field="_global"`, so the click does nothing. Either skip emitting for `_global` or render it as a non-navigating summary entry.

### IN-17: `ErrorState.vue` has no consumers yet

**File:** `hhs-frontend-v2/src/components/common/ErrorState.vue`
**Issue:** The component is created but never imported by any view (verified across `src/views`). It is presumably a Phase 12 deliverable; if not wired in this phase, it should be listed as a forward-looking artifact so it isn't mistaken for an active error surface.

### IN-18: `ScreeningServiceImpl` reflects user input into the error detail

**File:** `hhs-backend/src/main/java/com/hhs/service/impl/ScreeningServiceImpl.java:124`
**Issue:** `"报告日期格式错误: " + reportDateStr` echoes the caller-supplied string into the user-visible message. Vue interpolation escapes it (no XSS), but echoing untrusted input into error copy is unnecessary and can smuggle misleading text.
**Fix:** Use a fixed message.

### IN-19: `formField` remapping is an unused feature; `apply` replaces rather than merges

**File:** `hhs-frontend-v2/src/composables/useFormServerErrors.ts:76-86`
**Issue:** `err.formField` is never set by `normalizeApiError` and no call site constructs it, so the "blood pressure dual request" remap documented at lines 71-75 and tested in isolation cannot execute in the app. Separately, `apply` overwrites the whole map, so two sequential validation responses would lose the first response's fields.

---

_Reviewed: 2026-11-06T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_