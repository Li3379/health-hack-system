# Pitfalls Research

**Domain:** Retrofitting guidance, profile-completeness prompting, and a centralized friendly-error layer into a live Vue 3 + Spring Boot health app  
**Milestone:** v1.3 (proposed) — Threshold Configuration Wizard + Health-Profile Completeness Guidance + Unified Friendly Error Feedback  
**Researched:** 2026-11-05  
**Confidence:** HIGH for all repo-specific claims (each verified by reading the cited file). MEDIUM for library behavior, verified against the local `node_modules` install where possible.

> **Scope discipline:** this document covers **only** mistakes specific to *adding* these three capabilities to an already-deployed system. It deliberately does not re-cover the v1.1 metric-input pitfalls (four-tier grading, unit mismatch, non-destructive blur); that research is preserved at `.planning/research/PITFALLS-v1.1.md`. Where a v1.1 decision interacts with this milestone, it is called out explicitly.

---

## Baseline Established Before Writing

Measured, not assumed:

| Fact | Evidence |
|------|----------|
| Frontend unit suite = **13 files / 90 tests, all passing** | `npx vitest run` → `Test Files 13 passed (13) / Tests 90 passed (90)`, 18.45 s |
| ~105 `ElMessage.error` call sites across **29 files** | `grep -rn` over `hhs-frontend-v2/src` |
| 39 of them are generic bucket messages (`操作失败`, `保存失败`, `录入失败`, …) | regex count |
| 11 delete/confirm paths compare the rejection to the string `'cancel'` | `grep -rn "!== 'cancel'"` |
| Response interceptor **already toasts every failure** | `src/utils/request.ts:44, 55, 61, 64, 67, 70, 73, 75` |
| Realtime page polls every **10 s** | `src/views/realtime/Monitor.vue:415` |
| Health-profile GET returns **business code 404** when absent | `PreventionController.java:60-62` |
| Only `birthDate` is `@NotNull` on the profile DTO | `dto/HealthProfileRequest.java:16-18` |
| `user_threshold` has `UNIQUE (user_id, metric_key)` | `sql/schema.sql:205` |
| Risk engine looks metrics up by **Chinese labels** | `RiskAssessmentServiceImpl.java:122-128, 147-149` |

---

## Critical Pitfalls

### Pitfall 1: Duplicate Toasts — Interceptor Toasts, Page Toasts Again

**What goes wrong:**  
`request.ts` already displays a red toast for every failed business response (`:44`) and every HTTP error (`:55-75`). Every page-level `catch` then displays a second toast:

```ts
// request.ts:44  → toast #1 ("资源已存在: ...")
// Thresholds.vue:189-191
} catch (error) {
  ElMessage.error('操作失败')   // toast #2
}
```

The user sees two stacked red messages: one carrying the real backend reason, one generic. In `AiHistory.vue:171-177` and `OcrInput.vue:295-299` this happens *and* the `else` branch is unreachable, because the interceptor rejects non-200 before the caller can inspect `res.code`. Roughly 60 of the 105 existing sites have this shape.

**Why it happens:**  
The interceptor was written as a "safety net" and never de-duplicated against callers. Adding a *centralized* layer on top without first deciding the ownership rule multiplies the problem instead of solving it.

**How to avoid:**
1. Make the interceptor **transport-only** by default: normalize the failure into a typed object, do not toast.
2. Add a `silent?: boolean` flag to `AxiosRequestConfig` so a caller can opt out when it renders inline errors (form fields, OCR preview cards).
3. Provide exactly one presentation entry point — `useErrorFeedback().show(error, fallback)` — and let the interceptor call it once. Pages never call `ElMessage.error` directly.
4. Pass `{ grouping: true }` to `ElMessage` so identical messages collapse into a badge instead of stacking. Verified available in `element-plus/es/components/message/src/message.mjs:36,144-149`.
5. Leave **success** toasts in callers; only the error path is centralized. Success copy is context-specific (`删除成功` vs `录入成功`).

**Warning signs:**
- Any `catch` that calls `ElMessage.error(...)` while also awaiting `request`.
- Two `.el-message--error` nodes visible at once during a manual click-through.
- The 90-test suite still green after the refactor — proving it cannot detect this. Manual/E2E verification is mandatory.

**Phase to address:** Phase 11 (Error Feedback Foundation), and it must be *enforced* before Phases 12–13 add new call sites.

---

### Pitfall 2: Cancellation and Abort Misclassified as Failure

**What goes wrong:**  
`ElMessageBox.confirm` rejects with the **string** `'cancel'` (or `'close'`), and eleven sites guard it by hand (`Thresholds.vue:203-207`, `Alerts.vue:223-227`, `health/Metrics.vue:199-201`, `prevention/Metrics.vue:131`, `reminders/Index.vue:193`, `screening/List.vue:218`, `goals/Index.vue:157`, `mood/MoodTracker.vue:155`, …). A centralized helper that treats every `catch` as an error will fire a red toast **every time the user cancels a delete**. Worse, `Score.vue:483` deliberately ignores `CanceledError`/`AbortError` from the `AbortController` used to cancel report generation. A generic `handleError()` will resurrect both canceled operations as alarming errors. This is the fastest way to make a "friendly" layer feel hostile.

**Why it happens:**  
Cancel rejections are sentinel strings, not `Error` instances; aborts *are* `Error` instances. `instanceof Error` checks therefore misfire in opposite directions.

**How to avoid:**  
Classify before rendering, in this order:

```ts
// 1. dismissals
if (typeof err === 'string' && (err === 'cancel' || err === 'close')) return
// 2. aborts
if (err?.name === 'CanceledError' || err?.name === 'AbortError' ||
    err?.code === 'ERR_CANCELED') return
// 3. everything else → friendly toast
```

Replace the 11 hand-written comparisons with the helper's classification. Add unit fixtures for `'cancel'`, `'close'`, `CanceledError`, `AbortError`.

**Warning signs:**
- Users report "red errors when I just close the dialog".
- Report-generation cancellation shows "生成报告失败".
- A diff that deletes `!== 'cancel'` guards without adding them to the helper.

**Phase to address:** Phase 11 (first behavior the helper must implement).

---

### Pitfall 3: The Centralized Layer Swallows Errors Callers Depend On

**What goes wrong:**  
The codebase has deliberate caller contracts on failure, and centralizing unwisely destroys them:

| Caller | Contract | What breaks if genericized |
|---|---|---|
| `stores/push.ts:55-62` | catches, toasts, **returns `false`** | `PushSettings.vue:210-230` ignores the `false` and shows **"已启用" success after a failed save** — the bug shape already exists |
| `stores/ai.ts:105-110` | catches, toasts, pops the optimistic message, returns `false` | Chat silently keeps a failed turn, or removes it twice |
| `stores/push.ts:71-81` | distinguishes "API success but channel failed" (`res.data.success`) | A uniform toast loses the per-channel reason |
| `AIInput.vue:243-259` | reads `response.status === 429` | Generic "请求失败" hides actionable retry guidance |
| `Score.vue:461,486` | reads `error.response.data.message` | If the helper rejects a *new* `Error(message)`, `response` is gone and the fallback text always wins |
| `DevicePlatformConfig.vue:332-355` | branches on `res.message` vs `error.message` | Loses the server's specific misconfiguration hint |

Additionally, `request.ts:45` rejects with `new Error(res.message)`, **discarding `code`**. Any layer that maps `code → friendly copy` cannot do so for business errors — a limitation this milestone would inherit unless fixed.

**Why it happens:**  
"Centralize error handling" gets read as "catch everything and toast", converting a typed failure into a UI side effect and removing it from the caller's control flow.

**How to avoid:**  
Define a typed envelope and always propagate it:

```ts
export interface AppError extends Error {
  kind: 'business' | 'http' | 'network' | 'timeout' | 'cancel' | 'abort' | 'unknown'
  code?: number | string         // preserve backend code from HTTP-200 bodies
  status?: number
  field?: string
  userMessage: string            // friendly, curated Chinese
  raw?: unknown                  // server detail for logs only
  silent: boolean
}
```

Rules:
- The helper **toasts and rethrows** (or returns a discriminated result). Never `return undefined` on error.
- Stores may keep returning `boolean`, but callers must use it. Fix `PushSettings.handleToggle` to check the result before showing success.
- Preserve `response.status` and `response.data.code`; never wrap in a bare `new Error`.
- Explicitly mark expected-and-silent failures: background polls (`Monitor.vue:310-314`), all of `stores/alert.ts`, and `metricMeta.ts:71-75`.

**Warning signs:**
- A store `catch` returns `false` while its page shows a success message.
- `error.response` still referenced after migration (means the original leaked past the layer).
- New tests asserting "no throw" on API failure.
- `ElMessage.success` in the same function whose `await` can reject.

**Phase to address:** Phase 11 (envelope + contract); Phase 14 audits every `await store.*` site for ignored booleans.

---

### Pitfall 4: Threshold Wizard Persists Non-Canonical `metricKey` → Thresholds Silently Never Fire

**What goes wrong:**  
This is the highest-severity, most HHS-specific trap, and the existing page already contains the bug seed:

- `Thresholds.vue:64-69` offers values `glucose`, `temperature`, `spo2`.
- `UserThresholdServiceImpl.create:54-60` stores whatever string arrives, with no normalization.
- `ThresholdEvaluator.getThresholdForUser:56-80` and both lookups in `IntelligentAlertService.java:266-320` match with **exact equality** on `metric_key`.
- Writes normalize to canonical (`HealthMetricServiceImpl.java:80` → `bloodGlucose`, `bodyTemperature`, `bloodOxygen`), while `RealtimeController.java:63-69` does not normalize at all.

Result: a user configures a "血糖" threshold in the wizard; it is saved as `glucose`; alert evaluation looks up `bloodGlucose`; **no alert ever fires and nothing tells the user**. The same defect hits the seeded defaults (`schema.sql:518-524` uses `glucose`, `temperature`, `bmi`, of which two are non-canonical), so default rules for glucose and temperature are dead too. Because `UNIQUE (user_id, metric_key)` is alias-blind, the user can also create both `glucose` and `bloodGlucose` rows for one real metric without tripping the duplicate guard.

**Why it happens:**  
The v1.1 canonicalization work was applied to the metric-write path only. Thresholds are a parallel write path that was never included, and the frontend's seven hardcoded `<el-option>` values predate the registry.

**How to avoid:**
1. Source wizard options from `useMetricMetaStore().healthMetrics` and bind **`canonicalKey`**, never an alias.
2. Normalize **server-side** in `UserThresholdServiceImpl.create/update` via `MetricKeyNormalizer.toCanonicalKey(...)`; treat `glucose` and `bloodGlucose` as the same unique key.
3. Add a repair migration for existing rows (`UPDATE user_threshold SET metric_key='bloodGlucose' WHERE metric_key='glucose'`, and the temperature/SpO2 equivalents) and align `alert_rule` seeds.
4. Add a contract test: create a threshold with `glucose`, then evaluate a metric stored as `bloodGlucose` → alert must fire.
5. Note that `Thresholds.vue:17` already renders labels correctly via `getMetricLabel`, so the UI looks healthy while alerts are dead. Test it; do not eyeball it.

**Warning signs:**
- Threshold list shows Chinese labels but no alerts arrive.
- `SELECT metric_key FROM user_threshold` contains `glucose` / `temperature` / `spo2`.
- A user reports "I set my threshold but never got a warning".
- `alert_rule` and `user_threshold` key sets differ from the canonical registry.

**Phase to address:** Phase 12 (wizard). Backend normalization and the data repair belong in the same phase — shipping the wizard first lets users create more dead rows.

---

### Pitfall 5: Threshold Ordering and Partial-Set Validation Gaps

**What goes wrong:**  
Four independent thresholds (`criticalLow`, `warningLow`, `warningHigh`, `criticalHigh`) can be entered in any combination. Nothing validates ordering or completeness today:

- `dto/UserThresholdRequest.java:22-32` only enforces `@DecimalMin("0.0")`.
- `ThresholdRangeChecker.isNormalRange:20-28` treats `null` as "no bound" and returns normal.
- `isWarningRange:33-46` requires `value <= criticalLow && value >= warningLow`; if `criticalLow > warningLow` the interval is empty and **warnings never fire**.
- Comparisons use `<=`/`>=` on both sides, so a value exactly on `warningHigh` is simultaneously "not normal" and potentially critical when `criticalHigh == warningHigh`.
- If only `criticalHigh` is set and `warningHigh` is null, `isNormalRange` returns `true` (`isAboveThreshold(..., null)` → false) and evaluation stops before the critical branch — **nothing fires at all**.

The wizard makes this worse by encouraging partial entry ("just set the high bound").

**Why it happens:**  
Ordering is a cross-field invariant; field-level Bean Validation cannot express it, and the DTO has no class-level constraint or service check.

**How to avoid:**
1. Frontend: validate the full chain in one computed function and block save with inline guidance: `criticalLow ≤ warningLow < warningHigh ≤ criticalHigh`; reject equal high/low pairs unless the user explicitly confirms a zero-width normal band.
2. Backend: add a class-level constraint or service guard throwing `BusinessException(ErrorCode.HEALTH_THRESHOLD_INVALID, "临界低值必须小于警告低值，警告低值必须小于警告高值")`. Clients are not a security boundary.
3. Decide and document partial semantics. Recommended: require at least one complete side (low pair or high pair), otherwise reject with a friendly explanation rather than saving an inert rule.
4. Keep `null` as "unbounded" only for one side, and make `isNormalRange` skip a side only when that side is genuinely unset. Test: only-high-set, only-low-set, inverted, equal, all-null.
5. Prefill from `useMetricMetaStore` normal ranges so the wizard is not four empty boxes.

**Warning signs:**
- Saving reversed bounds succeeds silently.
- A "success" toast followed by an alerts page with zero rows.
- Tests cover only the happy path with all four fields in order.

**Phase to address:** Phase 12 (wizard), with the backend guard reviewed in Phase 14.

---

### Pitfall 6: First-Visit Profile 404 Becomes a Red "Resource Does Not Exist" Toast

**What goes wrong:**  
`PreventionController.getMyHealthProfile:56-64` returns `Result.failure(404, "健康档案不存在，请先创建")` — HTTP 200 with business code 404. The interceptor shows **"请求的资源不存在"** (`request.ts:63-65`) and rejects. `Profile.vue:190-192` then swallows it and switches to create mode:

```ts
} catch (error) {
  // 如果没有档案，允许创建
  editing.value = true
}
```

So every first-time user is greeted on the prevention profile page by a red toast saying a resource does not exist, directly above an empty create form. Once completeness guidance adds more callers of `getProfile` (dashboard banner, score page, risk page, AI chat), the toast fires repeatedly across the app.

**Why it happens:**  
Absence is modeled as an error, and the global interceptor cannot distinguish "expected empty state" from "real failure". The page's silent catch hides intent from the layer.

**How to avoid:**
1. Treat absence as a normal state. Preferred: return `Result.success(null)` — or a `ProfileCompletenessVO` with `complete:false` — for GET-when-absent; keep 404 only on the `/{userId}` authorization paths.
2. If the backend cannot change in this milestone, mark the call `silent: true` and let the caller decide:
   ```ts
   preventionApi.getProfile({ silent: true })   // absence is expected
   ```
3. Create one `useProfileCompletenessStore` owning `{ exists, level, missingFields, loading }`, fetched once and reused by every gate/banner. Do not let five pages each call `getProfile` and each render their own absence toast.
4. Test explicitly: profile absent → no error toast, `exists=false`, guidance visible.

**Warning signs:**
- A fresh account sees any red toast on `/prevention/profile`, `/health/score`, or `/prevention/risk`.
- Network tab shows N identical `GET /api/prevention/health-profile` on one page load.
- `catch` blocks containing only a comment plus a state flip.

**Phase to address:** Phase 13 (completeness guidance); the `silent` capability ships in Phase 11.

---

### Pitfall 7: Toast Storms from Background Polling and 401 Cascades

**What goes wrong:**  
Several high-frequency calls fail silently *by design* today:

- `Monitor.vue:310-314` — `fetchLatestMetrics` every **10 s** (`:415`), catch does nothing.
- `App.vue:21` — `fetchUnreadCount` on mount.
- `stores/alert.ts:11-49` — every method swallows errors.
- `stores/metricMeta.ts:71-75` — metadata fetch degrades to static fallback.

If the centralized layer toasts by default, a backend restart or expired token produces a red toast every 10 seconds, forever, on every page. Compounding this, the 401 branch (`request.ts:54-58`) calls `authStore.logout()` inside the interceptor; ten parallel 401s produce ten "未授权，请重新登录" toasts and ten `router.push('/login')` calls.

**Why it happens:**  
Silent-by-design call sites were never annotated, so the new layer cannot distinguish intentional quiet from oversight. And 401 handling has no once-per-session latch.

**How to avoid:**
1. Make background calls explicit: `{ silent: true }` on realtime polling, unread count, metadata preload, WebSocket reconnect, and anything called from `setInterval`. Document the rule.
2. Add a 401 latch: `let handling401 = false`; first 401 → logout + one toast + navigate; reset on successful login. `grouping: true` is a second line of defense.
3. After 3 consecutive poll failures, stop polling and surface one persistent inline banner ("实时数据连接已断开，正在重试") instead of per-tick toasts.
4. Never toast on `timeout` for background requests; a 30 s timeout (`request.ts:8`) on a 10 s poll guarantees overlap on slow networks.

**Warning signs:**
- Repeated identical toasts while the dev server is stopped.
- Dozens of `/login` entries in browser history.
- Backend access logs showing overlapping polling requests.

**Phase to address:** Phase 11 (silent + 401 latch); verified in Phase 14 by killing the backend for 60 s.

---

### Pitfall 8: Hard-Gating Features on Optional Profile Fields

**What goes wrong:**  
Only `birthDate` is required (`HealthProfileRequest.java:16-18`); `gender`, `heightCm`, `weightKg`, `bloodType`, allergies, family history, and lifestyle are all optional. A naive "profile completeness gates dependent features" implementation blocks:

- **Health score** — currently always returns a number; `ProfileScorer.java:35-61` returns neutral **70** with no profile and deducts 10/15/10/15 for missing pieces.
- **Risk assessment** — returns default scores 30/25/25 when no profile exists (`RiskAssessmentServiceImpl.RiskRuleEngine`, else-branch).
- **AI advice** — works with a partial or absent profile; `AiChatServiceImpl.java:85-120` appends whatever fields exist.

Blocking these for a missing optional allergy note is a product regression: existing users with partial profiles lose features they use today, and it will read as a bug, not a nudge. Worse, the underlying signals are already approximate — see Integration Gotcha 1: the risk engine looks up metrics by Chinese labels against canonical English keys, so those contributions are **always null today**. Promising "fill your profile for accurate risk" on a broken evaluator is a credibility trap.

**Why it happens:**  
"Completeness" is conflated with "eligibility", and teams treat a profile form as mandatory onboarding, applying a route-level block.

**How to avoid:**  
Adopt a tiered, non-blocking policy:

| Completeness | Behavior |
|---|---|
| No profile / no birthDate | Features still work. Dismissible inline `el-alert` with "去完善". Scores/risk labeled "基于默认值估算" |
| Partial (missing height/weight/lifestyle) | Inline hint on the specific result card ("补充身高体重可提升评分精度"). No toast, no modal, no block |
| Complete | Hint disappears; optional one-time acknowledgement |

Implementation rules:
- Gate at **result quality**, never at the **action**. Always allow "创建评估" and "计算评分"; annotate output with a confidence/limitations note.
- Use route-level `beforeEach` **only** for hard requirements (nothing in this milestone qualifies). Guidance belongs in-page.
- Centralize in `useProfileCompletenessStore().level: 'none' | 'partial' | 'complete'` and derive all wording from it.
- Fix or explicitly caveat the risk engine before citing it in copy (Integration Gotcha 1).

**Warning signs:**
- A `beforeEach` added for `/prevention/risk` or `/health/score` "if profile incomplete".
- A disabled "创建评估" button with no explanation.
- Reports from existing users saying "it used to work".
- Copy promising accuracy while `getLatestMetric(...)` returns `null`.

**Phase to address:** Phase 13. No blocking gate may be sequenced before it.

---

### Pitfall 9: Friendly Layer Passes Raw Backend Text Through — English, Field Names, Internal Detail

**What goes wrong:**  
Backend failure payloads are **not** user-safe:

- `GlobalExceptionHandler.handleMethodArgumentNotValid:76-85` returns `errors: ["heightCm: Height must be at least 50 cm", ...]` — English plus camelCase field name.
- `handleBusinessException:48-50` appends `": " + ex.getDetail()`, e.g. `"健康阈值设置无效: Threshold not found"` (`UserThresholdServiceImpl:74`) or `"资源已存在: Threshold already exists for this metric"` (`:51`).
- `handleSystemException:66` returns raw `ex.getMessage()` — can expose SQL/exception internals.
- The 500 default is `"System error, please try again later"` (`:194`); `HttpMessageNotReadable` is `"Request body format error"` (`:175`); `handleAuthenticationCredentialsNotFound` is English (`:148`).

A "friendly" layer that displays `error.message || res.message` will therefore show English, technical field names, and internal detail to Chinese users — the exact defect class v1.1 eliminated for metric keys.

**Why it happens:**  
The frontend assumes `message` is user-facing; the backend uses it as a developer/diagnostic channel. No contract separates the two.

**How to avoid:**
1. Add a **presentation boundary**: map by `kind` + `code` to curated Chinese copy, and use server `message` only when it passes a whitelist (no ASCII run > 2, no `snake_case`/`camelCase` tokens, no `Exception`, no stack markers).
2. For all three new surfaces, never surface `detail`. Log it.
3. Fix the highest-traffic leaks where cheap: have `GlobalExceptionHandler` return localized field labels or a `fieldErrors` map the frontend localizes, instead of `field + ": " + englishMessage`.
4. Add a unit test on the mapper: feed each backend shape (validation map, business detail, 500, English auth) and assert no `[A-Za-z]{3,}` tokens beyond an allowlist.
5. Preserve the backend `code` — the only stable mapping key. Fix `request.ts:45` to keep it.

**Warning signs:**
- A screenshot with "Threshold already exists for this metric" or "heightCm: ...".
- Any toast containing `Exception`, `SQL`, `null`, `undefined`, or `Failed to`.
- Mapper tests that only assert non-empty strings.

**Phase to address:** Phase 11 (mapper + whitelist); backend copy fixes scheduled per surface in Phase 12/14.

---

### Pitfall 10: Threshold Wizard Loses State on Step Navigation, Close, Refresh

**What goes wrong:**  
The one existing wizard demonstrates the anti-pattern: `DeviceConfigWizard.vue` keeps `currentStep = ref(0)` (`:302`), renders steps with plain `v-if` (`:14, 60, 99, 242`), and is mounted inside an `el-dialog` with `destroy-on-close` (`DeviceSync.vue:314`). Closing at step 3 and reopening resets to step 0 with selections gone; `handleClose:378-380` just emits without persisting or confirming. The threshold wizard has more to lose — four numbers per metric, possibly several metrics in one sitting.

Naive implementations add three more loss modes:
- A step change that swaps a child component's `key` destroys its local `ref` form state.
- A successful save that closes the dialog before the list refresh resolves.
- Browser refresh mid-wizard loses everything (users often configure thresholds on mobile and switch apps).

**Why it happens:**  
Wizard state is placed in step-local components or `v-if` branches instead of one parent-owned reactive object. `destroy-on-close` is copied from the other dialog without considering draft recovery.

**How to avoid:**
1. **One parent-owned reactive draft**, one object per metric, passed down as props. Steps are presentation only; never store form state inside a step component.
2. Keep the draft alive across `v-if` by hoisting it; avoid `destroy-on-close`, or rehydrate from the store on open.
3. Persist the draft to `sessionStorage` (key `hhs:threshold-wizard:draft:v1`) on every change (debounced) and restore on mount. Clear only on successful save or explicit "放弃".
4. Guard close: if dirty, `ElMessageBox.confirm('配置尚未保存，确定离开？')` and classify cancellation per Pitfall 2.
5. After save, refresh the list **before** closing, or land on a summary step showing exactly what was written.
6. Support per-metric save plus "save all", and show a progress checklist (5 of 7 configured) so resumption is meaningful.

**Warning signs:**
- Opening after a failed save shows an empty step 1.
- `Object.assign(form, ...)` inside `nextStep()`.
- `<component :is="stepComponent" />` with step-local forms.
- Manual test: fill step 2 → Back → Next → values gone.

**Phase to address:** Phase 12 (wizard).

---

### Pitfall 11: Over-Nagging Turns Guidance into Banner Blindness

**What goes wrong:**  
This milestone adds three prompt surfaces at once (completeness banner, per-card hints, wizard entry points) on top of an existing alert badge (`MainLayout.vue:45`) and floating AI ball. Common failure modes:

- A modal on every login until the profile is "complete".
- A banner on every page, including where it is irrelevant (`/health/alerts`, `/user/profile`).
- The same hint repeated on Dashboard + Score + Risk in one session.
- Counting optional fields so the bar never reaches 100%, making the prompt permanent and therefore invisible.
- Nagging mid-task (an `ElMessage` each time a metric dialog opens).

Users learn to dismiss without reading, and the *real* alerts (critical health warnings) lose salience — a safety issue in this domain.

**Why it happens:**  
Each feature team adds its own prompt; nobody owns the total prompt budget. Completeness is computed as "all fields", so it is structurally unsatisfiable.

**How to avoid:**
1. **One global slot.** A single dismissible `el-alert` in `MainLayout.vue` above `<router-view>`, driven by `useProfileCompletenessStore`. No page renders its own duplicate banner.
2. Define completeness in **two levels**: `essential` (birthDate + gender + height + weight — the fields that actually alter scores) and `enhanced` (blood type, history, lifestyle). Only `essential` drives the top banner; `enhanced` is an inline hint on the profile page.
3. **Dismissal with expiry**: `localStorage` `profile-nudge-dismissed-at`; suppress for 7 days or until the score changes. Never re-show within a session.
4. **Frequency budget**: max one completeness prompt per route per session; no toasts at all — inline, dismissible UI only.
5. Context routing: CTA deep-links to `/prevention/profile?focus=height,weight`; after saving, the banner disappears without a success toast if inline confirmation is already shown.
6. Suppress all guidance while a critical-value `ElMessageBox` / `CriticalConfirmDialog` is open.

**Warning signs:**
- More than one completeness prompt visible at once.
- Completeness never reaches 100% for real users.
- Dismissal does not persist across reload.
- QA/users stop mentioning the banner within a day ("I just close it").

**Phase to address:** Phase 13, with a Phase 14 prompt-inventory UX audit.

---

### Pitfall 12: Breaking the 90 Passing Frontend Tests (and Not Noticing Until CI)

**What goes wrong:**  
The suite passes today because every view spec mocks the API module and stubs GSAP/ECharts/router. Four changes in this milestone can silently break that isolation:

1. **New store fetch on mount.** If the completeness or threshold store calls an API in `setup()` / `onMounted`, specs mounting `Metrics.vue`, `Dashboard.vue`, `prevention/Metrics.vue`, or `UniversalMetricDialog.vue` make an **unmocked** HTTP call. Axios under happy-dom rejects asynchronously; some specs log unhandled rejections and a few assertions (call counts, `isSaveBlocked`) can race.
2. **Changing exported symbols.** `format.ts` (`getMetricLabel`, `formatDateTime`) and `metricNormalizer.ts` are imported directly by specs (`format.spec.ts` = 45 tests, `metricNormalizer.spec.ts` = 13). Renaming or moving them while "centralizing" breaks imports.
3. **Interposing the error layer in the import graph.** If `request.ts` eagerly imports the auth store/router/error UI, specs that transitively import API modules may pull `vue-router` without the existing mock, or `ElMessage` without a stub — and a `request → store → api → request` cycle can break the build outright.
4. **Stub drift.** Specs hand-stub every Element Plus component (`el-dialog`, `el-form`, `el-input-number`, `el-alert`, `el-rate`, …). Adding `el-steps` / `el-result` / `el-collapse` to a tested dialog produces `Failed to resolve component` warnings; `WellnessMetrics.spec.ts` already emits this for `el-alert`. Non-fatal today, red under any stricter CI flag.
5. **Assertion coupling to toast copy.** E2E `quick-input.spec.ts:78-80` asserts the literal `录入成功`. Normalizing success copy during the refactor fails that E2E while unit tests stay green.

**Why it happens:**  
"Centralized error handling" feels like a pure cross-cutting concern, so teams assume it cannot affect component tests. In practice it changes imports, mount side effects, and copy.

**How to avoid:**
1. **Run the suite before and after every task**, not at phase end. `npm run test:run` must stay at 90 until new tests are added, then 90 + N.
2. **Make new stores lazy**: no fetch in `setup()`/`onMounted` of shared components; expose `ensureLoaded()` with `if (loading || loaded) return`, called only by pages or the banner.
3. **Add a global test helper** in `src/__tests__/setup.ts` mocking the new modules (`@/stores/profileCompleteness`, the error helper) so individual specs need no edits. Extend the existing `config.global.mocks.$message` approach for `ElMessage`.
4. **Never toast in tests**: have the helper no-op when `import.meta.env.MODE === 'test'`, or inject the toast function.
5. **Add stubs for new Element Plus components** used by tested views, in one shared map.
6. Keep public exports stable; if `format.ts` must be reorganized, re-export the old names.
7. Add tests for the new layer (error classification, cancel/abort, no-English mapper, threshold ordering, completeness levels) — cheap, and the only guard where the old suite is blind.

**Warning signs:**
- Test count drops from 90, or a spec starts emitting `ECONNREFUSED` / unhandled-rejection logs.
- `Failed to resolve component: el-steps` in test output.
- `vitest` duration jumps (new eager router/pinia imports).
- 90 → 87 with no spec deleted.

**Phase to address:** Phase 11 (test scaffolding + helper testability), enforced continuously through 12–14, final clean-run gate in Phase 14.

---

## Moderate Pitfalls

### Pitfall 13: Success/Failure Toast Ownership Drift During Migration

**What goes wrong:**  
While converting 105 call sites it is easy to (a) delete a `catch` toast but forget the interceptor no longer toasts (`silent: true`), leaving a failure with **no feedback at all**, or (b) keep both, reintroducing Pitfall 1. The most dangerous variant is a swallowed `await` followed by a success toast — `PushSettings.vue:210-230` already does this (store returns `false`, page shows "已启用").

**Why it happens:**  
Migration is mechanical and each site has slightly different semantics. There is no per-site decision record.

**How to avoid:**  
Keep a migration ledger with columns: file:line | operation | double-toasted today? | silent-by-design? | success copy | new behavior | verified by. Migrate in small batches and run each surface with the backend stopped to confirm exactly one understandable message. Final grep gate: `ElMessage.error` appears only in the helper and in `silent` callers that render inline UI.

**Warning signs:**
- A button spins and stops with no message.
- Both success and error toasts on one click.
- Remaining page-level `ElMessage.error` count not near zero.

**Phase to address:** Phase 14 (sitewide sweep).

---

### Pitfall 14: Business Errors Carried in HTTP 200 Are Missed by Status-Only Handling

**What goes wrong:**  
`GlobalExceptionHandler` returns `Result<Void>` with HTTP 200 for `BusinessException`, `SystemException`, and the catch-all; `UserController.login:56-67` returns 200 with `code=401/429`; `PreventionController` returns 200 with `code=404`. A layer keyed only on Axios `error.response.status` classifies all of these as success and the page proceeds with `undefined` data. Today this reaches `Profile.vue` as a thrown `Error`; future wrappers may drop the rejection entirely.

**How to avoid:**  
Handle **both channels** in one place: `res.code !== 200` in the fulfilled handler and HTTP failures in the rejected handler must yield the same `AppError`. Preserve `code` (fix `request.ts:45`). Add a contract test for a 200-with-`code=404` payload.

**Warning signs:**
- `res.data` undefined after an `await` that "succeeded".
- New code reading `error.response.status` — the envelope was bypassed.

**Phase to address:** Phase 11.

---

### Pitfall 15: Wizard Bypasses Duplicate/Ownership Semantics and Surfaces Raw 500s

**What goes wrong:**  
`user_threshold` has `UNIQUE (user_id, metric_key)` (`schema.sql:205`). `UserThresholdServiceImpl.create:49-52` pre-checks duplicates but is alias-blind (Pitfall 4); a duplicate race falls through to a DB constraint violation, which the catch-all turns into **500 "System error, please try again later"**. A wizard "add all missing metrics" batch also risks creating thresholds the user never reviewed, and `update:84-89` permits changing `metricKey` even though the existing UI disables that selector (`Thresholds.vue:62`).

**How to avoid:**
- Normalize keys before both the pre-check and the insert; catch `DuplicateKeyException` and map to "该指标阈值已存在，请直接编辑".
- "Quick fill" stages proposals and requires explicit confirmation; never blind-save.
- Keep `metricKey` immutable after creation in both UI and service.
- Preserve service-level ownership checks; add a test that a batch save cannot write another user's row.

**Warning signs:**
- 500s on a rapid double-click of Save.
- Two rows for one metric after the alias fix.
- Batch create writes metrics the user never opened.

**Phase to address:** Phase 12.

---

### Pitfall 16: Rapid Clicks and Parallel Requests Stack Messages and Double-Submit

**What goes wrong:**  
The threshold save button has no `:loading` state (`Thresholds.vue:96`), so a double-click issues two POSTs: one succeeds, one returns the duplicate error, producing a success toast immediately followed by a red error toast. Batch wizard saves fire several requests whose failures each toast. Element Plus stacks messages with no default grouping.

**How to avoid:**
- `:loading="submitting"` plus an early `if (submitting.value) return` on every new submit path.
- Use `ElMessage({ grouping: true })` inside the helper.
- Batch saves aggregate into one summary ("成功 5 项，失败 1 项：血糖") instead of N toasts.
- Disable Save while validation is unresolved.

**Warning signs:**
- Duplicate-key errors or double rows in backend logs.
- Stacked identical toasts after a single click.

**Phase to address:** Phase 12 (wizard), Phase 14 (sweep).

---

## Minor Pitfalls

### Pitfall 17: Guidance Copy Drifts Between Surfaces
The same "profile incomplete" condition produces different wording on the banner, the score card, and the risk page. Centralize strings beside `level` in the completeness store and derive all copy from one map.

**Phase to address:** Phase 13.

### Pitfall 18: Locale Not Negotiated
No frontend code sets `Accept-Language`, and `GlobalExceptionHandler.resolveLanguage:201-207` defaults to Chinese, so mapped messages are consistently Chinese today. Any future English client would receive Chinese while the "no-English" assertion still passes. Record the decision (Chinese-only for this milestone) and keep frontend copy authoritative.

**Phase to address:** Phase 11 (document only).

### Pitfall 19: Diagnostic Logging Noise and Token Exposure
`request.ts:28,48` logs every failure, including expected 404s and aborts, and Axios error objects carry request headers (including `Authorization: Bearer …`). After the layer exists, move detail to a debug-gated, redacted logger with a correlation id, so support can trace a friendly message to a server log without exposing tokens or internals.

**Phase to address:** Phase 11.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-Term Cost | When Acceptable |
|---|---|---|---|
| Put the new toast logic inside `request.ts` only | One-file change | No way for callers to opt into silent/inline; forces duplicate toasts or silent failures | Never — expose a helper, keep the interceptor transport-focused |
| Gate dependent features with `router.beforeEach` | Trivial to write | Locks existing users out; cannot be contextualized | Never for optional fields |
| Drop `res.code` from the rejected error | Smaller diff | Cannot map codes to friendly copy later | Never |
| Hardcode threshold options in the wizard | Faster UI | Reintroduces alias drift (Pitfall 4) | Never — use `useMetricMetaStore` |
| Persist the wizard draft only in memory | Simpler | Multi-metric config lost on refresh | Only for a single-metric, single-screen wizard |
| Compute completeness from "all fields" | Simple boolean | Never reaches 100%; banner becomes permanent | Never — split essential/enhanced |
| Leave all 105 sites untouched and only add a wrapper | Lowest risk today | Two error systems coexist; duplicate toasts persist | One release only, with a tracked ledger |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|---|---|---|
| `request.ts` interceptor ↔ page `catch` | Both toast | Interceptor neutral by default; helper owns presentation; caller opts in/out |
| `stores/push.ts` ↔ `PushSettings.vue` | Store returns `false`; page ignores it and shows success | Return `{ ok, error }`; page branches on `ok` before any success toast |
| `stores/ai.ts` ↔ `Chat.vue` | Store pops the optimistic message; UI assumes success | Preserve the `false` contract; assert the failed message is removed once |
| `Score.vue` `AbortController` ↔ helper | Helper toasts canceled report generation | Classify `AbortError` / `CanceledError` as non-errors |
| `PreventionController` 404-on-absent ↔ profile page | Red "resource not found" for every new user | Return null/success or mark the call `silent`; absence is a state |
| `UserThresholdServiceImpl` ↔ `ThresholdEvaluator` | Alias keys saved, canonical keys queried; alerts dead | Normalize on write; repair rows; contract test |
| `alert_rule` seed ↔ canonical registry | Seeds use `glucose`, `temperature`, `bmi` | Align seeds to canonical keys in the same migration |
| `HealthMetricServiceImpl` (normalizes) ↔ `RealtimeController` (does not) | One metric stored under two keys from two paths | Normalize at the realtime write boundary too, or at evaluation time |
| `RiskAssessmentServiceImpl` `getLatestMetric("血糖")` ↔ canonical `bloodGlucose` | Metric contributions always `null`; risk scores look plausible but ignore real data | Fix key comparison before citing risk accuracy in guidance copy |
| `ProfileScorer` ↔ guidance copy | Neutral 70 for a missing profile is presented as a real score | Label the score as estimated until essential fields exist |
| `App.vue` / `Monitor.vue` timers ↔ helper | Toast every 10 s while the backend is down | Timer-driven calls silent; retry threshold + one inline banner |
| `metricMetaStore` fallback ↔ wizard | Offline fallback has 23 metrics; backend may differ | Render from the store (union) and re-validate keys server-side |
| `DeviceConfigWizard` pattern ↔ new wizard | `destroy-on-close` + step-local state resets | Parent-owned draft + `sessionStorage` + dirty-close guard |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|---|---|---|---|
| Completeness fetch added to `App.vue` + each page + each dialog | 4–6 identical `GET /health-profile` per navigation | Single store with `ensureLoaded()` and in-flight dedupe | Immediately on the dashboard |
| New Pinia store imported eagerly into `request.ts` | Circular import (`request → store → api → request`), blank app or slow tests | Keep `request.ts` dependency-free; inject behavior from callers | At dev start, or when tests import stores |
| Wizard loads metadata per step | Repeated `/api/metrics/metadata` calls | Reuse `useMetricMetaStore` (cached + static fallback) | Every step transition |
| Batch threshold save as N sequential POSTs | Slow wizard; partial writes on failure | Bulk endpoint, or `Promise.allSettled` with an aggregated report | At ~6–10 metrics |
| Grouping disabled + polling failures | DOM grows with dozens of toast nodes | `grouping: true` + silent background calls | Within minutes of an outage |
| Draft restored on every keystroke | `sessionStorage` write per input | Debounce 300–500 ms | Low-end Android devices |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---|---|---|
| New threshold bulk endpoint trusts a client-supplied `userId` | Reads/writes another user's thresholds | Bind to `SecurityUtils.getCurrentUserId()`; ignore body `userId` (existing controller does this — preserve it) |
| Error mapper surfaces backend `detail` / stack / SQL | Information disclosure (table/column names, library versions) | Whitelist mapping; log raw detail server-side only |
| Free-text profile fields injected into the AI system prompt | Prompt injection via `allergyHistory` / `familyHistory` | Keep free text out of the system prompt or delimit it; do not nudge users to dump arbitrary text |
| Client-side-only ordering check | Crafted request writes inverted thresholds, silently disabling alerts (safety issue) | Server-side class-level validation (Pitfall 5) |
| Error helper logs the full Axios error | `Authorization: Bearer …` leaked to console/log aggregation | Redact headers; never log request config |
| Success toast rendered without checking the result | Users believe a privacy-relevant setting (push channel) saved when it did not | Branch on `ok` (Pitfall 13) |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---|---|---|
| Red error toast on first visit to an empty profile | New users think something is broken | Absence is normal; show a friendly "create your profile" card |
| Generic `操作失败` after a specific validation failure | User cannot fix the input | Show the mapped reason next to the field; reserve toasts for unrecoverable failures |
| Blocking risk/score behind optional fields | Existing users lose functionality | Non-blocking banner + "estimated" labeling |
| Modal completeness prompt at login | Feels like onboarding purgatory; reflex dismissal | Inline dismissible banner with 7-day suppression |
| Wizard as four bare number boxes | Users do not know sensible values | Prefill from normal ranges; show the resulting behavior ("高于 7.0 将触发警告") |
| No confirmation that a threshold took effect | Silent dead thresholds erode trust | Post-save summary, plus optional dry-run against the latest value |
| Wizard closes before list refresh | User cannot verify saved rows | Keep open on a summary step; refresh before close |
| Guidance competes with critical health confirmations | Safety-critical dialogs lose prominence | Suppress guidance while `CriticalConfirmDialog` is open |

---

## "Looks Done But Isn't" Checklist

- [ ] **Duplicate toasts:** stop the backend, click Save on Thresholds, Profile, and a metric dialog — exactly **one** message each, in Chinese, actionable.
- [ ] **Cancel is silent:** cancel every delete confirmation and a report generation — **zero** red toasts.
- [ ] **Caller contract preserved:** `PushSettings` shows success **only** when `saveConfig` returns `ok`; force a failure and confirm it does not claim success.
- [ ] **Business-code errors:** an HTTP-200 body with `code≠200` produces a friendly message, not `undefined` data.
- [ ] **Canonical keys:** create a threshold from the wizard; the DB row uses `bloodGlucose` / `bodyTemperature` / `bloodOxygen` and a matching metric triggers an alert.
- [ ] **Ordering:** inverted, equal, partial, and all-null thresholds are all rejected with inline guidance; the server rejects them too.
- [ ] **First-visit profile:** a brand-new account sees **no** red toast on `/prevention/profile`, `/health/score`, `/prevention/risk`.
- [ ] **No lockout:** a profile with only gender + birthDate can still open Score, Risk, and AI chat.
- [ ] **No prompt storm:** reload on 5 routes — at most one completeness prompt; dismissal persists for 7 days.
- [ ] **Wizard resilience:** fill step 2 → Back → Next → refresh → close/reopen; values restored; dirty close prompts.
- [ ] **No English:** search every new toast/screen for `[A-Za-z]{3,}`; none originate from backend payloads.
- [ ] **Polling quiet:** stop the backend for 60 s — no repeated toasts; one inline disconnect banner.
- [ ] **401 latch:** expire the token with several parallel requests — one toast, one redirect.
- [ ] **Test suite:** `npm run test:run` = 90 + new tests, zero unhandled rejections, no unresolved-component warnings for tested views.
- [ ] **E2E contract:** `e2e/quick-input.spec.ts` success-copy assertion still passes.
- [ ] **Silent-degradation list:** every `setInterval` fetch and every store-level `catch` has an explicit `silent` decision recorded.
- [ ] **Risk-engine caveat:** guidance copy does not promise accuracy that `getLatestMetric("血糖")` currently cannot deliver.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---|---|---|
| Duplicate toasts shipped | LOW | Disable interceptor toasts behind a flag; keep the helper; revert page `catch` in one batch |
| Cancel/abort toast | LOW | Add classification; hotfix deploy |
| Swallowed caller error | MEDIUM | Restore the typed envelope; re-audit `await store.*` sites; add tests |
| Dead thresholds from alias keys | HIGH | Data migration + seed fix + server normalization + user notification; alert history for affected users is lost |
| Inverted thresholds accepted | MEDIUM | Server guard; audit existing rows for impossible orderings and flag or repair |
| Profile 404 toast | LOW | Mark the call silent or return success(null); ship frontend-only first |
| Background toast storm | LOW–MEDIUM | Silent timers + grouping + 401 latch |
| Feature lockout on optional fields | MEDIUM | Remove the gate, restore access immediately, reintroduce as guidance; communicate |
| English/stack leak | MEDIUM | Add the mapper whitelist; fix backend copy; rotate any leaked identifiers if security-relevant |
| Wizard state loss | LOW | Add draft persistence; users re-enter small amounts |
| 90 tests broken | LOW–MEDIUM | Revert per task; restore exports; add shared test mocks instead of editing every spec |
| Over-nagging | LOW | Remove page-level banners, centralize in the layout, widen suppression |

---

## Pitfall-to-Phase Mapping

Proposed sequencing (assuming the roadmap continues at Phase 11; the planner may renumber). The error layer must land **first**, because both other features add call sites that would otherwise be born double-toasting.

| Pitfall | Prevention Phase | Verification |
|---|---|---|
| 1. Duplicate toasts | Phase 11 — Error Feedback Foundation | Backend-down click-through: one toast per action; grep shows `ElMessage.error` only in the helper |
| 2. Cancel/abort misclassification | Phase 11 | Unit fixtures for `'cancel'`, `'close'`, `CanceledError`, `AbortError`; manual cancel sweep |
| 3. Caller contract loss | Phase 11 (+ Phase 14 audit) | Rethrow/typed-envelope tests; `PushSettings` failure injection |
| 4. Non-canonical threshold keys | Phase 12 — Threshold Wizard (incl. backend normalization + data repair) | Contract test `glucose` write → `bloodGlucose` evaluation fires; DB key audit |
| 5. Ordering / partial-set gaps | Phase 12 | Inverted/equal/partial tests client and server; friendly inline copy |
| 6. Profile 404 toast | Phase 13 (+ Phase 11 silent flag) | Fresh-account walkthrough; no red toast; one `getProfile` call in the network tab |
| 7. Polling / 401 toast storms | Phase 11 | Kill the backend 60 s; expire the token with parallel calls |
| 8. Hard-gating optional fields | Phase 13 | Partial-profile account still uses Score/Risk/AI; UX review of gates |
| 9. Raw English/stack leakage | Phase 11 (+ Phase 12/14 backend copy) | Mapper tests for each backend shape; regex scan of rendered copy |
| 10. Wizard state loss | Phase 12 | Back/Next/refresh/close-reopen matrix; dirty-close prompt |
| 11. Over-nagging | Phase 13 (+ Phase 14 prompt audit) | Prompt inventory; dismissal persists 7 days; ≤1 prompt per session |
| 12. Breaking 90 tests | Phase 11 scaffolding + continuous in 12–14 | `npm run test:run` count monotonic; no unhandled rejections in output |
| 13. Toast ownership drift | Phase 14 — Sitewide Sweep & Regression | Ledger complete; per-surface failure-injection walkthrough |
| 14. HTTP-200 business errors missed | Phase 11 | Contract tests for 200/`code=404` and 200/`code=401` |
| 15. Wizard duplicate/ownership bypass | Phase 12 | Double-click and batch-save tests; friendly duplicate-key mapping |
| 16. Rapid-click stacking / double submit | Phase 12 + Phase 14 | `:loading` guards; one aggregated batch result |
| 17. Copy drift | Phase 13 | Single copy map; snapshot of all guidance strings |
| 18. Locale decision | Phase 11 | Document Chinese-only; no `Accept-Language` assumptions |
| 19. Log noise / token exposure | Phase 11 | Redaction test; debug-gated logging |

### Recommended phase order and rationale

1. **Phase 11 — Unified Friendly Error Feedback Foundation.** Error envelope, `silent` flag, cancel/abort classification, Chinese mapper with English whitelist, 401 latch, `grouping: true`, and test scaffolding. Everything else in the milestone writes error-handling code; without this, Phases 12–13 create new duplicate-toast debt.
2. **Phase 12 — Threshold Configuration Wizard.** Parent-owned draft wizard, canonical-key binding from `useMetricMetaStore`, ordering/partial validation on both tiers, server-side normalization plus data repair, and a post-save summary. Highest correctness risk (silent dead alerts) and highest data-integrity cost, so it needs its own phase.
3. **Phase 13 — Health-Profile Completeness Guidance.** One `useProfileCompletenessStore` with essential/enhanced levels, a single layout-level dismissible banner, absence-as-normal handling, and non-blocking annotations on Score/Risk/AI. Depends on Phase 11's `silent` and on the risk-engine caveat being documented or fixed.
4. **Phase 14 — Sitewide Submit-Path Sweep & Regression.** Execute the migration ledger across all 29 files, remove residual raw error toasts, run the failure-injection matrix, verify the 90+test suite and the E2E success-copy contract, and perform the prompt-inventory UX audit.

Phases 12 and 13 are independent of each other and may run in parallel **only after** Phase 11's helper API is frozen.

---

## Sources

**Codebase (HIGH confidence — read directly):**
- `hhs-frontend-v2/src/utils/request.ts` — interceptor toasts, status branches, `new Error(res.message)` loses `code`, 401 logout
- `hhs-frontend-v2/src/views/health/Thresholds.vue` — hardcoded alias options (`:64-69`), generic catch (`:190`), `'cancel'` guard (`:204`), no submit loading guard (`:96`)
- `hhs-frontend-v2/src/views/prevention/Profile.vue` — silent first-load catch (`:190-192`), generic save catch (`:216`)
- `hhs-backend/src/main/java/com/hhs/controller/PreventionController.java:54-64` — 404 business code for an absent profile
- `hhs-backend/src/main/java/com/hhs/service/impl/UserThresholdServiceImpl.java:49-98` — alias-blind duplicate check, no normalization
- `hhs-backend/src/main/java/com/hhs/service/domain/ThresholdEvaluator.java:56-80` — exact-key threshold lookup
- `hhs-backend/src/main/java/com/hhs/service/domain/ThresholdRangeChecker.java:20-57` — null/inverted threshold behavior
- `hhs-backend/src/main/java/com/hhs/service/impl/HealthMetricServiceImpl.java:80` vs `controller/RealtimeController.java:63-69` — divergent normalization
- `hhs-backend/src/main/resources/sql/schema.sql:195-208, 514-524` — unique constraint and non-canonical seeded keys
- `hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java:112-155` — Chinese-label lookup against canonical English keys; default scores
- `hhs-backend/src/main/java/com/hhs/service/domain/ProfileScorer.java:35-61` — neutral 70 and per-field deductions
- `hhs-backend/src/main/java/com/hhs/service/impl/AiChatServiceImpl.java:85-120` — optional-profile prompt assembly
- `hhs-backend/src/main/java/com/hhs/dto/HealthProfileRequest.java` — only `birthDate` required
- `hhs-backend/src/main/java/com/hhs/dto/UserThresholdRequest.java` — no cross-field ordering constraint
- `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java:41-96, 191-195` — English field errors, detail concatenation, raw system message
- `hhs-frontend-v2/src/stores/{push,ai,alert,metricMeta}.ts` — swallow/return-false contracts
- `hhs-frontend-v2/src/views/user/PushSettings.vue:210-230` — success toast after a `false` return
- `hhs-frontend-v2/src/views/health/Score.vue:461,483-487` — `response.data.message` and abort handling
- `hhs-frontend-v2/src/views/realtime/Monitor.vue:310-314,415` — 10 s polling with a silent catch
- `hhs-frontend-v2/src/App.vue:21` — silent unread-count fetch
- `hhs-frontend-v2/src/views/data-input/components/DeviceConfigWizard.vue:4,302,378` + `DeviceSync.vue:314` — wizard state reset pattern
- `hhs-frontend-v2/src/__tests__/**` and `vitest.config.ts` — mock/stub isolation model; measured 13 files / 90 tests
- `hhs-frontend-v2/e2e/quick-input.spec.ts:78-80` — literal `录入成功` assertion

**Library behavior (MEDIUM confidence, verified in the local install):**
- Element Plus `ElMessage` `grouping` option — `node_modules/element-plus/es/components/message/src/message.mjs:36,144-149`
- Axios cancellation semantics (`CanceledError`, `ERR_CANCELED`) — matches the runtime check already present in `Score.vue`

**Not consulted:** external web providers were unavailable in this environment, so no third-party post-mortems are cited. Every claim above is either code-verified in this repository or a directly observable consequence of the cited code.

---

*Pitfalls research for: retrofitting guidance, completeness prompting, and unified error feedback into HHS*  
*Researched: 2026-11-05*
