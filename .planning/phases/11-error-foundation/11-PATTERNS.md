# Phase 11: 全站统一友好错误反馈地基 - Pattern Map

**Mapped:** 2026-11-06
**Files analyzed:** 20 (11 frontend source/test, 7 backend source/test, 2 style/config)
**Analogs found:** 18 / 20 (2 have no close analog — noted below)

> **Scope:** mechanism layer only (ERR-01/02/04/06). No per-page migration in this phase.
> **Tracked-source gate (#3645):** every analog path below was verified with `git ls-files -- <path>` (non-empty = tracked). No gitignored mirror paths are named.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/utils/request.ts` (MODIFY) | utility / HTTP interceptor | request-response | itself (current 109-line file) + `src/stores/metricMeta.ts` | exact (self) |
| `src/types/api.ts` (MODIFY) | type definition | none | itself (`ApiResponse<T>` lines 1-6) | exact (self) |
| `src/types/axios.d.ts` (NEW) | type augmentation | none | `src/vite-env.d.ts` | role-match |
| `src/utils/appError.ts` (NEW) | utility / domain pure logic | transform | `src/utils/metricValidation.ts` | exact |
| `src/utils/errorMessages.ts` (NEW) | config / data dictionary | transform (key→string) | `src/constants/metricMeta.ts` + `src/utils/format.ts` | exact |
| `src/utils/showError.ts` (NEW) | utility / presentation | event-driven (toast) | `src/stores/push.ts` (ElMessage usage) | partial (no utility does this) |
| `src/composables/useFormServerErrors.ts` (NEW) | composable / state | transform | `src/composables/useMetricValidation.ts` | exact |
| `src/components/common/ErrorState.vue` (NEW) | component / presentational | request-response failure | `src/views/data-input/components/ReferenceGuideCard.vue` + `src/components/device/PlatformStatusTag.vue` | role-match |
| `src/components/common/FormErrorSummary.vue` (NEW) | component / presentational | validation display | `src/components/device/PlatformStatusTag.vue` (el-alert block) + `src/components/metric/UniversalMetricDialog.vue:74-98` | role-match |
| `src/assets/styles/theme.css` (MODIFY) | config / style | none | itself (`:root` 13-114, EP overrides 500-630) | exact (self) |
| `src/App.vue` (MODIFY) | bootstrap | n/a | itself (`onMounted` 15-23) | exact (self) |
| `src/__tests__/utils/appError.spec.ts` (NEW) | test | unit | `src/__tests__/utils/metricValidation.spec.ts` | exact |
| `src/__tests__/utils/errorMessages.spec.ts` (NEW) | test | unit | `src/__tests__/utils/format.spec.ts` | exact |
| `src/__tests__/utils/showError.spec.ts` (NEW) | test | unit | `src/__tests__/stores/metricMeta.spec.ts` (mock + clearAllMocks) | role-match |
| `src/__tests__/composables/useFormServerErrors.spec.ts` (NEW) | test | unit | `src/__tests__/composables/useMetricValidation.spec.ts` | exact |
| `src/__tests__/components/FormErrorSummary.spec.ts` + `ErrorState.spec.ts` (NEW) | test | unit | `src/__tests__/components/PlatformStatusTag.spec.ts` | exact |
| `hhs-backend/.../common/Result.java` (MODIFY) | model / response envelope | CRUD | itself + `vo/HealthProfileVO.java` | exact (self) |
| `hhs-backend/.../vo/FieldErrorVO.java` (NEW) | model (record) | n/a | `vo/AIClassifyResponse.java` / `vo/HealthProfileVO.java` | exact |
| `hhs-backend/.../exception/GlobalExceptionHandler.java` (MODIFY) | controller-advice / handler | request-response | itself (lines 73-137) + `validation/MetricRecordValidator.java` | exact (self) |
| `hhs-backend/.../dto/*.java` (MODIFY: UserThresholdRequest + English-message sweep) | DTO / validation | n/a | `dto/HealthMetricRequest.java` | exact |
| `hhs-backend/.../service/impl/UserThresholdServiceImpl.java` + `HealthProfileServiceImpl.java` (MODIFY) | service | CRUD | themselves | exact (self) |
| `hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java` (NEW) | test | unit/WebMvc | `controller/PushConfigControllerTest.java` | role-match |

---

## Pattern Assignments

### `src/utils/request.ts` (MODIFY — utility, request-response)

**Analog:** itself. This is a rewrite of the two handlers, not a new shape.

**Imports pattern** (lines 1-4) — note the existing store import; the new version must **keep** `useAuthStore` (for the 401 latch/logout) but must **NOT** import `showError` (avoid a `request → appError → …` cycle; `appError` already must not import stores):

```typescript
import axios, { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import { useAuthStore } from '@/stores/auth'
import type { ApiResponse } from '@/types/api'
```

**Request interceptor — attach `Accept-Language`, preserve FormData handling** (current lines 14-31):

```typescript
service.interceptors.request.use(
  config => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    // D-14: always negotiate Chinese; backend resolveLanguage keeps its en branch.
    config.headers['Accept-Language'] = 'zh-CN'
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type']
    }
    return config
  },
  error => Promise.reject(error)
)
```

**Fulfilled branch — must classify HTTP-200 + `code != 200` (ARCHITECTURE Flow 1 item 1):**

```typescript
(response: AxiosResponse<ApiResponse>): any => {
  const res = response.data
  if (res.code === 200) return res
  // translate-only: reject a typed ApiError, never toast (D-01 / Anti-Pattern 1)
  return Promise.reject(normalizeApiError({ response: { status: res.code, data: res } }))
}
```

**Rejected branch — 401 latch (D-11), silent gate (D-09/D-10), cancel ignored (D-12):**

```typescript
async error => {
  const silent = (error.config as any)?.silent === true
  const apiError = normalizeApiError(error)      // handles cancel → kind 'cancel'
  if (apiError.kind === 'cancel') return Promise.reject(apiError)

  if (apiError.kind === 'auth') {                // 401
    if (!handling401) {                           // module-level boolean latch
      handling401 = true
      showErrorLatchOnce()                        // one toast only
      useAuthStore().logout()
    }
    return Promise.reject(apiError)
  }

  if (silent) return Promise.reject(apiError)     // D-10: still reject, no UI
  return Promise.reject(apiError)
}
```

> **Current lines 43-46, 50-79 are the code being removed.** The two `ElMessage.error` families there are the source of the duplicate-toast bug (PITFALLS 1/7). Do not keep any `ElMessage` import in this file after the rewrite.

---

### `src/utils/appError.ts` (NEW — utility, transform)

**Analog:** `src/utils/metricValidation.ts` — a stateless pure-function module exporting typed result shapes (no Vue, no store, no side effects). Same file-level contract.

**Module header / exports pattern** (`metricValidation.ts:1-26`):

```typescript
// metricValidation.ts:1-8
import type { MetricDefinitionVO } from '@/types/api'
export type MetricTier = 'NORMAL' | 'WARNING' | 'CRITICAL' | 'HARD_BOUND'
export interface MetricEvaluation { tier: MetricTier; ... }
export const PANIC_LIMITS: Record<string, { min?: number; max?: number }> = { ... }
```

**Core pattern to mirror** (ARCHITECTURE Pattern 1, lines 262-299 — definitive design; `kind` enum as specified by CONTEXT Claude's Discretion):

```typescript
export type ErrorKind =
  | 'validation' | 'business' | 'auth' | 'network' | 'server' | 'unknown'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly kind: ErrorKind,
    readonly status?: number,
    readonly code?: string,
    readonly fieldErrors: Record<string, string> = {},
    readonly formField?: string,          // blood-pressure dual-request attribution
    readonly retryable = false
  ) { super(message) }
}

export const CANCEL_SENTINELS = ['cancel', 'close'] as const

export function isCancel(raw: unknown): boolean {
  if (typeof raw === 'string' && CANCEL_SENTINELS.includes(raw as never)) return true
  const e = raw as any
  return e?.name === 'CanceledError' || e?.name === 'AbortError' || e?.code === 'ERR_CANCELED'
}

export function normalizeApiError(raw: unknown): ApiError { /* two branches */ }
```

**Ordered classification (PITFALLS 2 — dismissals first, aborts second, everything else):**

```typescript
if (isCancel(raw)) return new ApiError('', 'unknown') // never rendered; caller sees kind
const err = raw as any
const data = err?.response?.data
const status: number | undefined = err?.response?.status
if (data?.fieldErrors?.length) { …validation… }
if (status === 401) return new ApiError('登录已过期，请重新登录', 'auth', status)
if (!err?.response) return new ApiError('网络连接异常，请检查网络后重试', 'network')
if (status >= 500) return new ApiError('服务暂时不可用，请稍后重试', 'server', status, data?.errorCode)
if (data?.code && data.code !== 200) return new ApiError(data.message, 'business', status, data.errorCode)
return new ApiError(data?.message || '', 'server', status)
```

**Hard constraints for this file:**
- Must **not** import `pinia`, any store, `vue-router`, or `element-plus` (PITFALLS 12 / Performance Traps: eager store import into `request.ts` creates a cycle).
- `fieldErrors` map is capped at 20 server-side already; client should not re-cap.
- Preserve raw details for logging only — never surface `detail` (PITFALLS 9).

---

### `src/utils/errorMessages.ts` (NEW — config, flat dictionary)

**Analog:** `src/constants/metricMeta.ts` (a flat `Record<string, T>` literal dictionary) plus `src/utils/format.ts:7-22` (a fallback map used to translate unknown keys).

**Dictionary pattern** (`metricMeta.ts:7-10`, `format.ts:7-16`):

```typescript
// flat key → Chinese copy (D-06/D-07). Keys exactly as UI-SPEC 204-223.
export const ERROR_MESSAGES: Record<string, string> = {
  'threshold.saveFailed': '无法保存阈值，请检查高低值顺序后重试',
  'threshold.loadFailed': '无法加载阈值列表，请点击重试',
  'threshold.deleteFailed': '无法删除该条阈值，请稍后重试',
  'threshold.duplicate': '该指标已有阈值，是否改为编辑？',
  'profile.saveFailed': '无法保存健康档案，请检查填写内容后重试',
  'profile.loadFailed': '无法加载健康档案，请稍后重试',
  'metric.saveFailed': '无法保存指标记录，请检查指标类型与数值',
  'metric.deleteFailed': '无法删除该条指标记录，请稍后重试',
  'report.generateFailed': '报告生成未完成，请稍后重试',
  'ai.parseFailed': 'AI 解析未完成，请调整描述后重试',
  'ocr.uploadFailed': '报告上传失败，请检查文件格式与大小',
  'auth.expired': '登录已过期，请重新登录',
  'network.offline': '网络连接异常，请检查网络后重试',
  'server.unavailable': '服务暂时不可用，请稍后重试',
  'dialog.unsavedConfirm': '内容尚未保存，确定离开？'
}

// D-15: backend sends field name only; frontend owns the Chinese label.
export const FIELD_LABELS: Record<string, string> = {
  metricKey: '指标类型', value: '指标数值', diastolicValue: '舒张压',
  recordDate: '记录日期', warningHigh: '警告高值', warningLow: '警告低值',
  criticalHigh: '严重高值', criticalLow: '严重低值', birthDate: '出生日期',
  gender: '性别', heightCm: '身高', weightKg: '体重', bmi: 'BMI',
  lifestyleHabits: '生活习惯', familyHistory: '家族病史',
  allergyHistory: '过敏史', notes: '备注说明'
}

export function resolveMessage(key: string, backendMessage?: string): string {
  return backendMessage || ERROR_MESSAGES[key] || ERROR_MESSAGES['server.unavailable']
}
```

**Lookup/fallback pattern** (`format.ts` uses `FALLBACK_METRIC_META[key]` then a `LEGACY_*` map — same "known → fallback" shape, lines 7-22).

---

### `src/utils/showError.ts` (NEW — utility, presentation)

**Analog:** `src/stores/push.ts:52-60` and `src/stores/ai.ts:105` show the *only* current `ElMessage.error` shape. There is no utility-layer precedent, so this file is **role-match**; use those call sites for the call signature and UI-SPEC 431-502 for behavior.

**Current call site shape** (`stores/push.ts:44-61`):

```typescript
try {
  const res = await updatePushConfig(channelType, data)
  if (res.code === 200) { … ElMessage.success('配置已保存'); return true }
  else { ElMessage.error(res.message || '保存失败'); return false }
} catch (error) {
  ElMessage.error('保存失败')          // ← duplicate-toast pattern to eliminate
  return false
}
```

**Target signature (UI-SPEC 437-442, binding):**

```typescript
export function showError(
  raw: unknown,
  fallbackKey: string,
  opts?: {
    retry?: () => void | Promise<void>
    action?: { label: string; onClick: () => void }
    silent?: boolean
  }
): ApiError
```

**Behaviour guards to encode (UI-SPEC 446-501):**
- `kind === 'validation'` → **0 toasts** (inline + summary only).
- `kind === 'cancel'` → 0, return unchanged.
- `silent` → 0, return unchanged.
- Business without retry → `ElMessage({ type: 'error', message, grouping: true, duration: 4000, showClose: true })`.
- Retry/action → VNode message (`h('div', { class: 'error-toast' }, [...])`), `grouping: false` (EP does not support VNode grouping — `message.d.ts:62-63`), `duration: 0`.
- Module-level duplicate latch: keyed by `message + retry-token`, 2000 ms window; max 3 visible; close oldest when the 4th appears.
- Retry button accessible name is `重试：{message}`, **not** bare `重试`.
- This is the **only** file allowed to call `ElMessage.error`.

**Testability** (`src/__tests__/setup.ts:4-11`): the suite globally mocks `$message`; prefer a named export function that can be spied directly (`vi.mock('element-plus', …)`), not reliance on `config.global.mocks`.

---

### `src/composables/useFormServerErrors.ts` (NEW — composable, transform/state)

**Analog:** `src/composables/useMetricValidation.ts` — the canonical shape: takes `Ref`s in, returns a flat object of `ref`/`computed` + methods (lines 11-98).

**Signature/return pattern** (`useMetricValidation.ts:11-16`, 87-98):

```typescript
export function useMetricValidation(metricKeyRef: Ref<string | undefined>, …) {
  const metaStore = useMetricMetaStore()        // store usage inside a composable is fine
  const canonicalKey = computed<string>(() => { … })
  const swapBloodPressure = () => { … }
  return { canonicalKey, isBloodPressure, definition, ..., swapBloodPressure }
}
```

**Core pattern (ARCHITECTURE Pattern 2, lines 311-330 + UI-SPEC 414-415) — add `summaryItems`, `clearField`, `focusField`:**

```typescript
export interface SummaryItem { field: string; label: string; message: string }

export function useFormServerErrors(options: { fieldOrder?: string[] } = {}) {
  const fieldErrors = ref<Record<string, string>>({})
  const summaryItems = computed<SummaryItem[]>(() =>
    Object.entries(fieldErrors.value)
      .map(([field, message]) => ({
        field, message,
        label: FIELD_LABELS[field] ?? ''        // unknown → message alone (D-15)
      }))
      .sort((a, b) => orderOf(a.field) - orderOf(b.field))  // form order, not backend order
  )

  function apply(err: ApiError) {
    if (err.kind !== 'validation') return
    const next = { ...err.fieldErrors }
    if (err.formField && next.value) {          // blood pressure dual-request override
      next[err.formField] = next.value
      delete next.value
    }
    fieldErrors.value = next
    nextTick(() => summaryRef?.value?.focus())  // focus once per failed submit (UI-SPEC 405)
  }

  const clearField = (field: string) => { const n = { ...fieldErrors.value }; delete n[field]; fieldErrors.value = n }
  const reset = () => { fieldErrors.value = {} }
  function focusField(field: string) {
    const el = document.querySelector<HTMLElement>(`[data-field="${field}"]`)
    const target = el?.querySelector<HTMLElement>('input, textarea, .el-select__wrapper, .el-rate')
    target?.focus()
    target?.scrollIntoView({ block: 'center' })  // native only — no GSAP (UI-SPEC 523)
  }
  return { fieldErrors, summaryItems, apply, clearField, reset, focusField }
}
```

**Binding contract** (UI-SPEC 409-429): every participating `el-form-item` needs `prop` + `:data-field="prop"` + `:error="fieldErrors[prop]"`. `Thresholds.vue:74-92` currently has **no `prop`** on the four threshold items (`D-2`); that migration is Phase 12, but this composable's API must assume `prop` exists.

---

### `src/components/common/ErrorState.vue` (NEW — component, presentational)

**Analog:** `src/views/data-input/components/ReferenceGuideCard.vue` (props-only presentational component, no store, `:deep()` styling) and `src/components/device/PlatformStatusTag.vue` (typed `defineProps<{...}>`, icons from `@element-plus/icons-vue`, variant-driven styling).

**Script setup + props pattern** (`PlatformStatusTag.vue:93-109`):

```vue
<script setup lang="ts">
import { ref, computed } from 'vue'
import { Warning, Link } from '@element-plus/icons-vue'
const props = defineProps<{
  status: PlatformStatus
  platform?: string
  reason?: string
  guideUrl?: string
  supportedDataTypes?: string[]
}>()
</script>
```

**Target props** (UI-SPEC 270-279): `variant: 'network' | 'server' | 'forbidden' | 'notFound' | 'load'` (default `'load'`), `title` (required), `description`, `retry?`, `retryLabel?`, `retrying?`.

**Icon/variant mapping** (UI-SPEC 283-289):

```typescript
const ICONS = { load: WarningFilled, server: WarningFilled, network: WarningFilled,
                forbidden: Lock, notFound: DocumentDelete }
const icon = computed(() => ICONS[props.variant])
const tinted = computed(() => ['load','server','network'].includes(props.variant))
```

**Template/a11y** (UI-SPEC 293, 517-518):
- wrapper `role="status"` + `aria-live="polite"` + `:aria-busy="retrying"` — **not** `role="alert"`.
- retry button rendered only when `retry` provided (no disabled ghost).
- accessible name `retryLabel` or `重试：{title}`; visible text always `重试`.
- Do **not** steal focus on mount.

**Styling pattern** (`ReferenceGuideCard.vue:68-76` uses scoped styles + `:deep()` for EP internals; use project tokens not hardcoded colors):

```css
.error-state {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  text-align: center; min-height: 320px; max-width: 480px; margin: 0 auto;
  padding: 48px 24px;
  background: var(--surface-1); border: 1px dashed var(--border); border-radius: var(--radius-lg);
}
.error-state__icon-circle {
  width: 64px; height: 64px; border-radius: var(--radius-full);
  background: var(--hhs-color-error-tint); color: var(--hhs-color-error-text);
}
.error-state__title { font: 600 18px/1.3 var(--font-ui); color: var(--text-1); }
.error-state__desc  { font: 400 14px/1.5 var(--font-ui); color: var(--text-3); }
```

> New token `--hhs-color-error-tint` / `--hhs-color-error-text` (see theme.css assignment). Never use `--el-color-danger` or the theme's separate `--error: #F87171/#ef4444` (UI-SPEC 126).

---

### `src/components/common/FormErrorSummary.vue` (NEW — component, validation display)

**Analog:** `src/components/device/PlatformStatusTag.vue:31-59` (the project's existing `el-alert` block: `type`, `:closable="false"`, `show-icon`, `#title` slot) and `UniversalMetricDialog.vue:74-98` (el-alert with `#title` slot wrapping controls).

**el-alert usage pattern** (`PlatformStatusTag.vue:51-54`):

```vue
<el-alert v-else-if="status === 'NOT_CONFIGURED'" type="error" :closable="false" show-icon>
  <template #title>平台未配置</template>
  {{ reason || '该平台 OAuth 未配置，请联系管理员。' }}
</el-alert>
```

**Target structure (UI-SPEC 303-345 — binding, verbatim shape):**

```vue
<div v-if="summaryItems.length" ref="summaryRef" class="form-error-summary"
     tabindex="-1" role="group" :aria-labelledby="titleId">
  <el-alert type="error" :closable="false" show-icon>
    <template #title>
      <span :id="titleId">请修正以下 {{ summaryItems.length }} 项填写问题</span>
    </template>
  </el-alert>
  <ol class="form-error-summary__list">
    <li v-for="item in summaryItems" :key="item.field">
      <button type="button" class="form-error-summary__link"
              @click="emit('focus-field', item.field)">
        {{ item.label ? item.label + '：' : '' }}{{ item.message }}
      </button>
    </li>
  </ol>
</div>
```

**Hard constraints (UI-SPEC 332-345, 511-514):**
- List is a **sibling** of `<el-alert>`, never inside its default slot (EP renders that slot inside `<p>`; `<ol>` inside `<p>` is invalid HTML and the browser hoists it out).
- Focusable wrapper `<div tabindex="-1">` is required — `el-alert` hardcodes `role="alert"` and does not merge `$attrs`.
- Unique id via module counter, **not** `useId()` (`vue: ^3.4.15` floor):
  ```typescript
  let summaryIdSeq = 0
  const titleId = `form-error-summary-title-${++summaryIdSeq}`
  ```
- `reset()` before every submit; per-field `@input/@change` clear; alert unmounts at 0 entries.
- CSS per UI-SPEC 350-390 — tint wrapper, transparent inner alert, `max-height: 144px; overflow-y: auto`.

---

### `src/types/api.ts` (MODIFY — type definition)

**Analog:** itself, lines 1-6.

```typescript
// current
export interface ApiResponse<T = any> { code: number; message: string; data: T }

// target — additive only (do not alter existing fields)
export interface FieldError { field: string; message: string; code?: string }
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  errorCode?: string
  fieldErrors?: FieldError[]
}
```

> Must stay optional: existing call sites type-check against `ApiResponse<T>` and tests assert `code`/`message`/`data` (`MetricMetadataControllerTest:74-76`).

---

### `src/types/axios.d.ts` (NEW — type augmentation)

**Analog:** `src/vite-env.d.ts` (the project's only `.d.ts`, module-augmentation style header `/// <reference ... />`, included via `tsconfig.json` `include: ["src/**/*.d.ts"]`).

```typescript
// src/types/axios.d.ts
import 'axios'
declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean   // D-09: per-call-site explicit opt-out, no URL heuristic
  }
}
```

> `tsconfig.json` (line: `"include": ["src/**/*.ts", "src/**/*.d.ts", ...]`) picks this up automatically. No `tsconfig` edit needed.

---

### `src/assets/styles/theme.css` (MODIFY — style tokens + global EP overrides)

**Analog:** itself. Two insertion points are established:
- `:root` token block, lines 13-114 (add the dark values here).
- `[data-theme='light']` block, lines 119-174 (add light values here).
- Existing EP override convention, lines 500-630 (`.el-tag`, `.el-table`, `.el-pagination`, `.el-form-item__label`).

**Token insertion (UI-SPEC 128-141):**

```css
/* :root (dark) — after the Semantic block (line 49-53) */
--hhs-color-error-text: #f56c6c;             /* 6.45:1 on #111217 */
--hhs-color-error-tint: rgba(245, 108, 108, 0.12);

/* [data-theme='light'] — after its Semantic block (line 152-156) */
--hhs-color-error-text: #b91c1c;             /* 6.47:1 on #ffffff */
--hhs-color-error-tint: #fef0f0;
```

**Global toast overrides (UI-SPEC 473-494) — add near the other global EP blocks (~line 620). No `!important` needed** because `theme.css` loads after `element-plus/dist/index.css` (`main.ts:4` vs `:9`) — equal specificity, later wins:

```css
.el-message { --el-message-padding: 12px 16px; }                 /* normalize EP 11px 15px */
.el-message--error { --el-message-text-color: var(--hhs-color-error-text); }
.el-message__content { line-height: 1.5; }
```

**Inline form error override — add to the Form block (line 621-625):**

```css
.el-form-item__error {
  line-height: 1.4;
  padding-top: 4px;                              /* EP default/small ship 2px */
  color: var(--hhs-color-error-text);
}
```

> Note: `!important` is the *existing* convention for most EP overrides in this file, but the UI-SPEC explicitly requires these four rules **without** `!important` (later-wins is sufficient). Follow the spec for the new block; do not "fix" the surrounding file.

---

### `src/App.vue` (MODIFY — bootstrap)

**Analog:** itself, lines 15-23.

```typescript
onMounted(() => {
  // D-09: mark startup metadata fetch silent (currently catch-swallowed but interceptor toasts)
  metricMetaStore.fetchMetadata({ silent: true })
  if (authStore.isAuthenticated) {
    alertStore.fetchUnreadCount()   // store call must pass silent through (see below)
  }
})
```

> `metricMetaStore.fetchMetadata()` has no params today (`stores/metricMeta.ts:56`). Either add an options passthrough to the store method or set `silent` in `metricMetaApi.getMetadata()` — but the store's own catch (`metricMeta.ts:71-79`) is already silent, so the flag must reach the API call. `alertStore.fetchUnreadCount` similarly must mark its `alertApi.getUnreadCount()` silent. Both are **Phase 11 sample conversions**, not full migration.

---

### `hhs-backend/.../common/Result.java` (MODIFY — response envelope)

**Analog:** itself. Preserve `code` numeric semantics (ARCHITECTURE D-3 / existing test assertions).

**Current shape** (lines 8-16):

```java
@Data @NoArgsConstructor @AllArgsConstructor
public class Result<T> { private int code; private String message; private T data; }
```

**Target — additive optional fields, `@JsonInclude(NON_EMPTY)` so the wire format is unchanged for existing callers:**

```java
@Data @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private String errorCode;                 // e.g. "resource.exists" (REUSE ErrorCode.getCode())
    private List<FieldErrorVO> fieldErrors;   // null/empty omitted from JSON
}
```

**New factory methods** (mirror existing `Result.failure` overloads at lines 25-40; keep all existing signatures intact):

```java
public static <T> Result<T> failure(int code, String errorCode, String message) { … }
public static <T> Result<T> failure(ErrorCode errorCode, String messageOverride) {
    return new Result<>(errorCode.getHttpStatus(), messageOverride, null,
                        errorCode.getCode(), null);
}
public static <T> Result<T> invalidInput(String message, List<FieldErrorVO> fieldErrors) {
    return new Result<>(400, message, null, ErrorCode.VALIDATION_INVALID_PARAMETER.getCode(), fieldErrors);
}
```

> **Must not change** `parseCodeToInt` fallback semantics or the existing `Result.failure(int,String)` path — `PushConfigControllerTest` asserts `status().isOk()` + `$.code == 400` and `MetricMetadataControllerTest:74-75` asserts `$.code == 200` / `$.message == "success"`. The all-args constructor is used by existing factories; adding two fields means either updating those factory calls or adding a secondary constructor. Use a private 5-arg constructor and keep `success()` returning `new Result<>(200, "success", null)` via the 3-arg constructor.

---

### `hhs-backend/.../vo/FieldErrorVO.java` (NEW — record)

**Analog:** `src/main/java/com/hhs/vo/AIClassifyResponse.java` (minimal record) and `HealthProfileVO.java` (record with typed fields, package `com.hhs.vo`).

```java
package com.hhs.vo;

/** Field-level error payload replacing the current "field: msg" string concatenation. */
public record FieldErrorVO(String field, String message, String code) { }
```

> Jackson serializes records natively (project already returns records from controllers — `HealthProfileVO`). Field order `field, message, code` matches the frontend `FieldError` interface.

---

### `hhs-backend/.../exception/GlobalExceptionHandler.java` (MODIFY)

**Analog:** itself. Three validation branches currently return `ResponseEntity<Map<String,Object>>` with `errors: List<String>` (lines 73-137). Business branch lines 41-54.

**Current validation branch** (lines 73-96) and **constraint-violation branch** (101-121):

```java
List<String> errors = ex.getBindingResult().getFieldErrors().stream()
    .map(e -> e.getField() + ": " + e.getDefaultMessage()).collect(Collectors.toList());
Map<String, Object> response = new HashMap<>();
response.put("code", ErrorCode.VALIDATION_INVALID_PARAMETER.getCode());
response.put("message", getLocalizedMessage(ErrorCode.VALIDATION_INVALID_PARAMETER, request));
response.put("errors", errors);
return ResponseEntity.badRequest().body(response);
```

**Precedent for emitting field-level violations** (`validation/MetricRecordValidator.java:87-107` — `buildConstraintViolationWithTemplate(...).addPropertyNode("value").addConstraintViolation()`). This is the shape the handler must project into `fieldErrors`.

**Target structure (this is the definitive contract; ARCHITECTURE Flow 1 / Integration Points):**

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<FieldErrorVO> fieldErrors = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
        fieldErrors.add(new FieldErrorVO(fe.getField(), fe.getDefaultMessage(), null));
    }
    // Class-level constraints (@ValidMetricRecord / @ValidThresholdRange) land in getGlobalErrors,
    // with the property node attached (MetricRecordValidator adds .addPropertyNode("value")).
    for (ObjectError ge : ex.getBindingResult().getGlobalErrors()) {
        String field = ge instanceof FieldError f ? f.getField() : extractField(ge);
        fieldErrors.add(new FieldErrorVO(field, ge.getDefaultMessage(), null));
    }
    fieldErrors = fieldErrors.stream().limit(20).toList();   // ARCHITECTURE scalability cap
    Result<Void> body = Result.invalidInput(
        getLocalizedMessage(ErrorCode.VALIDATION_INVALID_PARAMETER, request), fieldErrors);
    return ResponseEntity.badRequest().body(body);
}
```

**Business branch — populate `errorCode`, keep message/detail behavior** (lines 41-54):

```java
return Result.failure(ec.getHttpStatus(), ec.getMessage(lang), message)
    .withErrorCode(ec.getCode());   // or a 4-arg factory — see Result assignment
```

**Constraints:**
- Keep `ResponseEntity.badRequest()` (HTTP 400) for validation — Axios routes it to the rejected branch (ARCHITECTURE Flow 1 item 1).
- Keep HTTP 200 + business `code` for `BusinessException` — Axios routes it to the fulfilled branch with `code != 200`.
- **Handle `getGlobalErrors()`** — class-level constraints otherwise fall through to the 500 catch-all (ARCHITECTURE Integration Points, explicit warning).
- Do not expose `ex.getDetail()` as raw English; D-16 converts the literals to Chinese at the service layer.

---

### `hhs-backend/.../dto/UserThresholdRequest.java` + English-message sweep (MODIFY)

**Analog:** `dto/HealthMetricRequest.java:26-40` (Jakarta constraint style: `@NotNull(message=...)`, `@Size`, `@DecimalMin`, class-level `@ValidMetricRecord` on the type).

**Current pattern to convert** (`UserThresholdRequest.java:15-32`):

```java
@NotNull(message = "User ID is required")           →  @NotNull(message = "用户 ID 不能为空")
@Size(min = 1, max = 64, message = "Metric key must be between 1 and 64 characters")
                                                    →  @Size(min = 1, max = 64, message = "指标类型长度需在 1 至 64 个字符之间")
@DecimalMin(value = "0.0", message = "Warning high must be non-negative")
                                                    →  @DecimalMin(value = "0.0", message = "警告高值不能为负数")
```

**Full English-message sweep (grep-verified — convert all of these):**
- `dto/UserThresholdRequest.java` (6 messages)
- `dto/HealthProfileRequest.java` (lines 16-33: `Date of birth is required`, `Height must be at least 50 cm`, …)
- `dto/HealthMetricRequest.java` (lines 29-53)
- `dto/RealtimeMetricRequest.java` (lines 14-43)
- `dto/AlertVO.java` (lines 21-49)
- `dto/AIClassifyRequest.java` (lines 13-14)
- `dto/ExportRequest.java` (lines 14-20)
- `dto/PushConfigRequest.java:17` (`Invalid channel type`)

> **Do NOT** introduce `messages.properties`/`MessageSource` (D-13). Direct literal replacement only.
> Keep Jackson `@JsonSetter` normalization hooks (e.g. `HealthMetricRequest` imports `MetricKeyNormalizer`) — those are unrelated.
> `AlertVO` is a DTO that doubles as a response VO — verify it is actually `@Valid`-ed before spending time; if not, still convert for consistency (D-16).

---

### `hhs-backend/.../service/impl/UserThresholdServiceImpl.java` (MODIFY)

**Analog:** itself. English `BusinessException` literals at lines 51, 74, 87.

```java
// line 51 / 87
throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "Threshold already exists for this metric");
// → throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "该指标已存在阈值，请直接编辑");
// line 74
throw new BusinessException(ErrorCode.HEALTH_THRESHOLD_INVALID, "Threshold not found");
// → throw new BusinessException(ErrorCode.HEALTH_THRESHOLD_INVALID, "未找到该条阈值设置");
```

**Surrounding pattern** (lines 45-65): direct mapper injection, `@Transactional`, `BeanUtils.copyProperties`, Chinese ownership messages already present (lines 80, 115) — follow that tone.

> `HealthProfileServiceImpl.java:37,75,80(-)` will be audited. Line 37 (`"该用户已存在健康档案，请使用更新接口"`) and line 75 (`"健康档案不存在"`) are already Chinese; no change unless the grep finds more. This file uses the **deprecated** `BusinessException(int, String)` constructor — leave it as-is (out of scope; changing it could shift `errorCode` inference) unless a literal is English.

---

## Shared Patterns

### Authentication / 401 handling
**Source:** `src/stores/auth.ts:36-42` (`logout()` clears token/user/storage and navigates to `/login`) and `src/utils/request.ts:54-58` (current in-interceptor logout).
**Apply to:** `request.ts` only (401 latch), and `showError`'s `auth` branch.
```typescript
// auth.ts — logout is safe to call repeatedly; the latch (not logout) is what prevents duplicates
const logout = () => { token.value = null; user.value = null; storage.clear(); router.push('/login'); ElMessage.info('已退出登录') }
```
> `logout()` itself emits `ElMessage.info('已退出登录')`. The 401 flow must not produce two messages — either call logout once under the latch (already the plan) or suppress logout's toast on the latch path. Document the choice in the plan.

### Error model / typed rejection
**Source:** `src/utils/appError.ts` (new) — the only exported error class.
**Apply to:** `request.ts` (produce), `showError` (consume), `useFormServerErrors` (consume), all new specs (construct fixtures).
Rule: never wrap in a bare `Error` — `request.ts:45` currently discards `code` (PITFALLS 3/14). Preserve `status` + `code`.

### Cancellation classification
**Source:** `PITFALLS.md:78-88` (ordered checks) + existing `Score.vue:483` abort handling.
**Apply to:** `appError.isCancel()` and therefore `request.ts` and `showError`.
Fixtures required: `'cancel'`, `'close'`, `{ name: 'CanceledError' }`, `{ name: 'AbortError' }`, `{ code: 'ERR_CANCELED' }`.

### Silent requests
**Source:** `src/stores/metricMeta.ts:56-79` (the degradation exemplar — static fallback + background fetch + catch silent).
**Apply to:** `App.vue` (`fetchMetadata`), `stores/alert.ts:11-18` (`fetchUnreadCount`), and the axios augmentation.
Rule: silent requests still **reject**; caller decides degradation (D-10). Never resolve a fake empty value.

### Envelope reuse (backend)
**Source:** `common/constant/ErrorCode.java:114-147` — already carries `httpStatus`, string `code`, `messageZh`, `messageEn`, `getMessage(lang)`.
**Apply to:** `Result` (`errorCode` = `ec.getCode()`) and `GlobalExceptionHandler` (`getLocalizedMessage`).
Rule: reuse, do not re-create. Business branch already resolves language via `resolveLanguage` (lines 201-207).

### Testing scaffolding
**Source:** `src/__tests__/setup.ts:4-11` (`config.global.mocks.$message`).
**Apply to:** all new component/utility specs. PITFALLS 12 warns: never let a new module trigger an unmocked HTTP call or unresolved EP component. New specs should `vi.mock('element-plus')` for `showError` and stub EP components locally (see `PlatformStatusTag.spec.ts:6-37`).

---

## No Analog Found

| File | Role | Data Flow | Reason / Planner guidance |
|---|---|---|---|
| `src/utils/showError.ts` | utility / presentation | event-driven | No utility-layer toast helper exists — every current `ElMessage.error` is inline in a store/view. Use `stores/push.ts` for the *call shape* and UI-SPEC 431-502 for the *behavior*. This is the single highest-risk new file; plan its unit spec first. |
| `hhs-backend/src/test/java/com/hhs/exception/GlobalExceptionHandlerTest.java` | test | unit/WebMvc | No existing `@RestControllerAdvice` test exists. Use `controller/PushConfigControllerTest.java` as the `@WebMvcTest` scaffold (lines 42-70) — but note it configures security; the handler test can use a minimal `@WebMvcTest` on a dummy controller or plain unit-call the handler methods (simpler, no Spring context). Prefer direct method invocation + `assertThat(result.getFieldErrors())` to avoid Testcontainers. |

---

## Metadata

**Analog search scope:** `hhs-frontend-v2/src/{utils,composables,stores,components,views,types,constants,__tests__}`, `hhs-backend/src/{main/java/com/hhs/{common,vo,dto,exception,validation,service},test/java/com/hhs/{controller,validation,service}}`.
**Files scanned (read or grep-verified):** ~35. **Strong analogs retained:** 18.
**Tracked-source gate:** all analog paths confirmed via `git ls-files --`. No mirror paths emitted.
**Pattern extraction date:** 2026-11-06.

**Carry-forward constraints for the planner (from required reading):**
1. `request.ts` must remain free of `showError`/store cycles; `appError.ts` must stay store-free.
2. Both Axios failure channels (HTTP 400 and HTTP-200 `code≠200`) must normalize to the same `ApiError`.
3. Do not modify numeric `code` semantics in `Result` — existing tests assert `code`.
4. The summary `<ol>` is a sibling of `el-alert`, never a slot child.
5. Use a module counter, not `useId()` (`vue ^3.4.15`).
6. Chinese-only copy; no i18n framework; field labels live in `FIELD_LABELS`.
7. Cancel/abort and silent requests produce **zero** UI.