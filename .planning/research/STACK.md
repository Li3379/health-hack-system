# Stack Research

**Domain:** Guided configuration & friendly validation UX for an existing health platform (HHS v1.3)
**Researched:** 2026-11-05
**Confidence:** HIGH on installed-artifact facts (read from `node_modules` + `package-lock.json`); MEDIUM on "latest version" claims (npm registry, tier MEDIUM per `classify-confidence`); the `context7` provider classified as MEDIUM and `npm`/`codebase` as LOW, so every version claim below was cross-checked against the actual installed tree rather than trusted from a provider alone.

---

## Headline Answer

**Every one of the three new features is already covered by the installed stack. Zero new runtime dependencies are required.**

| New feature | Already-covered by | Net addition |
|---|---|---|
| (a) Threshold configuration wizard on `views/health/Thresholds.vue` | `ElSteps` + `ElForm` + `ElInputNumber` + `ElSlider` (`range`, `marks`) + existing `thresholdApi` + `useMetricMetaStore` | One `.vue` file rewrite; **no packages** |
| (b) Health-profile completeness guidance | `ElProgress` + `ElResult` + `ElAlert` + `preventionApi.getProfile()` + existing `HealthProfileVO` (already carries every graded field) | One pure TS function + one card component; **no backend endpoint required** |
| (c) Site-wide unified friendly error layer | `ElMessage` (`grouping`, `max`) + `ElMessageBox` + existing `request.ts` interceptor + `GlobalExceptionHandler` already emitting `{code, message, errors[]}` | `errors?: string[]` on the TS `ApiResponse` type, a field-label map, and a per-request opt-out flag in `request.ts`; **no packages** |

The rest of this document justifies each line, names the exact integration point, and then lists what must **not** be added.

---

## Recommended Stack

### Core Technologies (all pre-installed — verify, do not install)

| Technology | Declared | **Actually installed** | Purpose in this milestone | Why this choice |
|---|---|---|---|---|
| **Element Plus** | `^2.5.6` (`package.json`) | **2.14.0** (lockfile + `node_modules`) | Wizard (`ElSteps`), range editing (`ElSlider`), completeness meter (`ElProgress`), empty/error states (`ElResult`, `ElEmpty`), toasts (`ElMessage`), confirms (`ElMessageBox`) | Every component needed already ships and is already bundled. `ElTour` (since 2.x, verified present at the 2.5.6 floor) removes any argument for `driver.js`/`intro.js`. |
| **Vue** | `^3.4.15` | **3.5.34** | Wizard step state, completeness computed, error-presentation state | Already the app runtime. `<script setup>` + `computed` is sufficient; a state machine is not. |
| **Pinia** | `^2.1.7` | **2.3.1** | `useMetricMetaStore` as the single source for the wizard's metric picker, units, precision, `hardMin`/`hardMax` | Store already exists at `src/stores/metricMeta.ts` with `defineStore('metricMeta', () => …)` setup syntax, `getDefinition()`, `healthMetrics`, `wellnessMetrics`. |
| **Axios** | `^1.6.5` | **1.16.1** | Single choke point for error normalization in `src/utils/request.ts` | Interceptor is already the only network seam. Adding a second client would create two divergent error behaviours. |
| **Spring Boot Validation (`spring-boot-starter-validation`)** | 3.2.0 (parent) | 3.2.0 (parent) | `@Valid` on `UserThresholdRequest`; `MethodArgumentNotValidException` handler already shapes `{code, message, errors[]}` | Handler exists in `GlobalExceptionHandler.java:73-96`. Extending it is a ~10-line change; a custom exception framework is not justified. |
| **Jakarta Bean Validation 3.0 / Hibernate Validator 8** | via starter | via starter | Cross-field ordering rule for `warningLow < warningHigh < criticalHigh` | `@DecimalMin` alone cannot express ordering. A single class-level constraint (same pattern already used by `@ValidMetricRecord` → `MetricRecordValidator`) expresses it with zero new deps. |

### Supporting Libraries (in-tree, not new)

| Library | Installed | Purpose | When to Use |
|---|---|---|---|
| `@element-plus/icons-vue` | 2.3.2 | Step icons, status glyphs, warning markers | Any icon in the wizard/completeness card |
| `async-validator` | 4.2.5 (transitive via Element Plus) | Backs `ElForm` `rules` / `FormRules` custom `validator` callbacks | Do **not** import it directly — go through `ElForm` rules so validation state and message rendering stay consistent |
| `dayjs` | 1.11.21 (transitive) | Date formatting if the wizard shows age-derived context | Prefer existing `src/utils/format.ts` helpers first |
| `vitest` + `@vue/test-utils` | 4.1.0 / 2.4.6 | Unit tests for the pure completeness function and the error normalizer | Required by `rules/common/testing.md` 80% target; pure functions are the cheapest coverage |

> **Transitive-dependency warning:** `@vueuse/core@14.3.0` is present in `node_modules` **only because Element Plus depends on it**. It is not in `package.json`. Do not `import { useX } from '@vueuse/core'` unless you first add it as a direct dependency — otherwise a lockfile re-resolve or a change in Element Plus' dependency graph will break the build in CI (`npm install`, `.github/workflows/frontend-ci.yml:33`).

---

## Feature-by-Feature: Existing Capability → Exact Integration Point

### (a) Threshold configuration wizard — `hhs-frontend-v2/src/views/health/Thresholds.vue`

**What is missing today (verified in the file):**
- The metric picker is **hardcoded to 6 `el-option`s** (`glucose`, `systolicBP`, `diastolicBP`, `heartRate`, `temperature`, `bloodOxygen`) — lines 62-70. It bypasses the 23-metric registry entirely.
- Values use `:min="0"` only (lines 74-92) — no `hardMin`/`hardMax`, no `precision`/`step` from metadata, no placeholder guidance.
- No ordering rule: `warningLow < warningHigh < criticalHigh` is unenforced on **both** client and server (`UserThresholdRequest.java` has only `@DecimalMin("0.0")`; `UserThresholdServiceImpl.create/update` copies properties without comparison).
- `ElMessage.error('获取阈值失败')` / `'操作失败'` double-toasts on top of the interceptor (lines 138-141, 189-191).
- Each dialog opens as a single flat form — no guided progression, no "apply recommended defaults" path.

**Use these existing components — no additions:**

| Need | Component | Verified capability | Prior art in repo |
|---|---|---|---|
| Step progression | `el-steps` + `el-step` | `:active`, `finish-status="success"`, `align-center` | `src/views/data-input/components/DeviceConfigWizard.vue:4-10` — copy this exact pattern (steps + `step-panel` + `canNextStep` + prev/next buttons at lines 273-278) |
| Metric selection | `el-select` driven by `metricMetaStore.healthMetrics` / `.wellnessMetrics` | computed arrays already exported from the store | `src/views/health/Metrics.vue:6-11` already does the metadata-driven variant |
| Numeric entry | `el-input-number` with `:precision`, `:step`, `:min="def.hardMin"`, `:max="def.hardMax"` | all four props exist in 2.5.6 and 2.14.0 | already used at `Thresholds.vue:75-90` — just bind to metadata |
| Visual band editing (optional 3rd step) | `el-slider` with `range` + `marks` + `show-stops` | `range` and `marks` verified in `slider.d.ts` at 2.5.6 | `src/views/mood/MoodTracker.vue:18-24` uses `:marks` + `show-stops` |
| Guidance card | **reuse** `ReferenceGuideCard.vue` (`src/views/data-input/components/`) | takes `definition` + optional `diastolicDef` | already consumed by `UniversalMetricDialog.vue:182` |
| Final confirmation | `el-result` (success/error icon + title + subTitle) or `el-message-box` | `ElResult` verified exported at 2.5.6 | no current use — first adoption here |
| Inline step validation | `el-form` `:scroll-to-error="true"`, `:inline-message`, `status-icon` | all three verified in `form.d.ts` at 2.5.6 | none yet; adopt on the wizard form |

**Recommended wizard shape (3 steps, matches `ElSteps` prior art):**
1. **选择指标** — `el-select` bound to `metricMetaStore`, shows `displayName`; on select, prefill the four thresholds from `normalMin`/`normalMax` + the `alert_rule` seed defaults.
2. **设置阈值** — four `el-input-number`s with metadata `precision`/`step`/`hardMin`/`hardMax`, an ordering validator, and a live `ReferenceGuideCard`.
3. **确认保存** — diff vs. defaults, `el-result` on success, upsert via `thresholdApi`.

**Backend: no new endpoint.** `thresholdApi` already exposes `getThresholds`, `getByUserId`, `getByMetricKey`, `createThreshold`, `updateThreshold`, `deleteThreshold` (`src/api/threshold.ts`). The table has `UNIQUE KEY uk_user_metric (user_id, metric_key)` (`schema.sql:205`) so the wizard must **upsert** — detect existing via `getByMetricKey` and call `update` instead of `create` (the service throws `RESOURCE_ALREADY_EXISTS` otherwise, `UserThresholdServiceImpl:49-52`).

**One localization detail:** the wizard must submit `canonicalKey` (e.g. `bloodGlucose`), not the legacy alias `glucose`. `MetricKeyNormalizer` maps `glucose → bloodGlucose` (`MetricKeyNormalizer.java:58`), but the `user_threshold` rows written today by the hardcoded select use `glucose`, and `getByUserAndMetricKey` does an **exact string match** (`UserThresholdServiceImpl:133-138`) — so an existing `glucose` row will not be found when the wizard queries `bloodGlucose`. Decide explicitly: normalize on write, and (optionally) teach `getByUserAndMetricKey` to resolve aliases.

---

### (b) Health-profile completeness guidance

**Existing backend capability (do not rebuild):**
- `HealthProfile` entity carries exactly the gradeable fields: `gender`, `birthDate`, `heightCm`, `weightKg`, `bmi`, `bloodType`, `allergyHistory`, `familyHistory`, `lifestyleHabits` (`entity/HealthProfile.java`).
- `HealthProfileVO` already returns **all** of them including computed `bmi` (`types/api.ts:235-248`).
- `ProfileScorer.java` already computes a 0-100 completeness-derived score with explicit penalties (missing gender/birthDate −10, height/weight −15, bmi −10, lifestyle −15) and is surfaced through `HealthScoreVO.factors.healthProfile.score` (`ScoreCalculator.java:54-60`).
- Endpoint: `GET /api/prevention/health-profile` via `preventionApi.getProfile()`.

**Recommendation — reuse `ProfileScorer`'s rubric, compute field-level detail on the client:**
- Add one pure function, e.g. `src/utils/profileCompleteness.ts`, returning `{ percent, missing: Array<{ field, label, weight, hint }> }`.
- Display it with **`el-progress`** (`:percentage`, `:status` = `warning` under threshold, `:stroke-width`) plus an `el-collapse` or `el-timeline` listing missing fields, each linking to `views/prevention/Profile.vue` with the relevant field focused.
- Mount it on the dashboard (`views/dashboard/Index.vue`) and at the top of `views/prevention/Profile.vue`.

**Why not add a backend endpoint?** `ProfileScorer` is coarse (a weighted deduction, not field-level). A faithful "what's missing" list needs the same weights in one place. Duplicating a 5-line rubric on the client is cheaper and avoids a round-trip on every dashboard load; the rubric is stable clinical/admin policy, not user data. If the rubric must later be admin-editable, promote it to a `GET /api/prevention/health-profile/completeness` returning `{percent, missing[]}` — that is a contained, reversible decision.

**Critical landmine — the 404-as-200 trap.** `PreventionController.getMyHealthProfile()` returns `Result.failure(404, "健康档案不存在，请先创建")` (`PreventionController.java:59-62`). Because the method returns `Result` (not `ResponseEntity`), the **HTTP status is 200** and only the body `code` is 404. `request.ts:39-46` treats any `code !== 200` as a business error → **`ElMessage.error` fires on the dashboard for every brand-new user**. Today `views/prevention/Profile.vue:196-200` swallows this in `catch`, but the red toast has already appeared. The completeness feature makes this surface worse (dashboard + profile both probe the endpoint). Fixing it is part of feature (c) — a per-request `silent` flag — not a reason to add a new endpoint.

---

### (c) Site-wide unified friendly error layer

**What exists and is reusable as-is:**

| Layer | File | Current behaviour |
|---|---|---|
| Transport error normalization | `src/utils/request.ts:34-80` | Business `code !== 200` → `ElMessage.error(res.message)`; HTTP switch for 401/403/404/500; network/`error.request` fallback. Rejects with `new Error(...)` so callers can `catch`. |
| Backend validation shape | `GlobalExceptionHandler.java:73-96` | `MethodArgumentNotValidException` → `{ code, message, errors: ["field: message", …] }` with HTTP 400. Also covers `ConstraintViolationException` and generic `ValidationException`. |
| Bilingual catalog | `ErrorCode.java` | Every code has `messageZh` + `messageEn`; `getMessage(lang)` resolves via `Accept-Language`. **No `messages*.properties` bundles exist** — i18n is enum-inline. |
| Field-level messages | `MetricRecordValidator.java:87-107` | Already emits friendly Chinese: `"心率有效生理区间为 20 ~ 300 次/分，请核对后重新输入"` |

**The three real gaps (all small, all in existing files):**

1. **Double toasts.** Verified: 118 `catch` blocks, 69 of which call `ElMessage.error` — each on top of the interceptor's own toast. The target files are offenders (`Thresholds.vue:138,189,203`; `Metrics.vue:156,199`). Fix: tag the rejected error as already-surfaced.
   ```ts
   // src/utils/request.ts — module augmentation, no dependency
   declare module 'axios' {
     interface AxiosRequestConfig { silent?: boolean }
   }
   ```
   Then: skip the toast when `config.silent`; attach `err.__surfaced = true`; export a typed `ApiError` so call sites can `if (!isSurfaced(e)) showFriendlyError(e)`. This is the **only** change needed to kill the 69 duplicate toasts without touching every view in one pass.

2. **`errors[]` invisible to the frontend.** `ApiResponse` in `src/types/api.ts:2-6` is `{ code, message, data }` — the `errors` array that `GlobalExceptionHandler` already produces is dropped. Add `errors?: string[]`. Then a `ElNotification`/`ElMessageBox`/inline `el-form-item :error` can show all field violations at once instead of the generic `message`.

3. **Raw field names would leak.** The handler formats `field + ": " + message` (`GlobalExceptionHandler:76-86`), so a user would see `heightCm: Height must be at least 50 cm`. Two acceptable fixes, both dependency-free:
   - **Preferred:** a `src/constants/fieldLabels.ts` map (`heightCm → 身高`, `birthDate → 出生日期`, …) applied when rendering `errors[]`; and switch the Bean Validation `message = "…"` strings in `HealthProfileRequest` / `UserThresholdRequest` / `HealthMetricRequest` to Chinese (they are currently **English-only**, which is inconsistent with every other user-facing string in the app).
   - Alternative: keep English messages and rely on `ErrorCode`'s zh text — rejected because `@DecimalMin` messages are hardcoded per-field, not `ErrorCode`-backed.

**Toast hygiene with zero config files:** `ElMessage` supports `grouping` and `max` at both the 2.5.6 floor and the installed 2.14.0 (verified in `message.d.ts`). Set it once in `src/main.ts` where `app.use(ElementPlus, { locale: zhCn })` already lives:
```ts
app.use(ElementPlus, { locale: zhCn, message: { max: 3, grouping: true } })
```
No `ElConfigProvider` wrapper component is needed — and none exists today.

**Do not centralize "friendly" text in a new abstraction layer.** The app already has three tiers of message (transport noise, backend `ErrorCode`, and domain-friendly copy from validators/composables). A fourth "error presenter" indirection over `ElMessage` would duplicate `request.ts` and fight the existing composables (`useMetricValidation.ts` already produces the friendliest copy in the codebase via `evaluateMetricValue`). Extend `request.ts` in place; export helpers from it; stop there.

---

## Alternatives Considered

| Recommended | Alternative | When the alternative would be right | Why not here |
|---|---|---|---|
| **Element Plus `ElSteps` wizard** | `xstate` / `vueuse` `useStepper` / custom state machine | Multi-branch, resumable, persistence-backed flows (e.g. KYC with server-side drafts) | The wizard is 3 linear steps with a cancel path. `DeviceConfigWizard.vue` already proves `ref(0)` + `canNextStep` is enough; a machine adds concepts without removing code. |
| **`ElSlider range` for visual threshold bands** | Custom SVG band editor | Users must drag non-contiguous, unequal-width multi-band regions | Two low/high pairs is exactly `range` semantics; `marks` shows normal-range boundaries. |
| **Client-side completeness rubric** | New `GET …/completeness` endpoint | Rubric becomes admin-configurable or is shared by mobile clients | One consumer, stable rubric, already-computed value in `HealthScoreVO.factors` — a round trip buys nothing. |
| **Extend `request.ts` with `silent` + `ApiError`** | `axios-retry`, `axios-error-normalizer`, or a second "raw" axios instance | Genuinely need retries/backoff, or must bypass interceptors for third-party APIs | Retries change failure semantics for POST thresholds (duplicate-write risk). A second instance duplicates auth-token attachment and the 401 logout path. |
| **`ElMessage` + `ElMessageBox` + inline `el-form-item :error`** | `vue-toastification`, `sonner`, `notyf` | Non-Element UI shell, or toasts needing rich actions/queues | A parallel toast system inherits `theme.css` dark/light variables poorly and duplicates teleport/z-index management. |
| **`ElTour` for first-run guidance** | `driver.js`, `intro.js`, `shepherd.js` | Guidance spans multiple routes with async-loaded anchors and cross-session resume | `ElTour` is verified present at the declared 2.5.6 floor and ships `target`, `placement`, `mask`, `scrollIntoViewOptions`. A new library adds ~10-30 KB and its own CSS.
| **Existing `user_threshold` table + `alert_rule` seed defaults** | New `threshold_template` table / Flyway migration | Templates become user- or org-scoped and versioned | `alert_rule` already holds the platform defaults (`schema.sql:514-527`) and `ThresholdEvaluator.getThresholdForUser` already falls back to them. No migration needed — important because the project has **no** Flyway/Liquibase yet (a known deferred decision in `PROJECT.md`). |

---

## What NOT to Add (explicit do-not list)

| Avoid | Why | Use Instead |
|---|---|---|
| **Any second UI component library** (Naive UI, TDesign, Ant Design Vue, Vuetify) | Two design systems = two theming systems. `theme.css` already customizes Element Plus dark/light via `[data-theme]`; a second library would not inherit it. Bundle cost for zero net capability. | Element Plus 2.14.0 (already installed) |
| **`vue-i18n`** | The product ships Chinese-only UI. Server-side bilingualism is already handled by `ErrorCode.messageZh/messageEn` + `Accept-Language`. There is **no** `messages*.properties` bundle to migrate. Adding i18n means externalizing every hardcoded string in ~80 views for no current requirement. | Hardcoded Chinese + `ErrorCode` (existing). If English UI is ever required, add `vue-i18n` then — it is a greenfield refactor either way. |
| **`VeeValidate`, `Zod`, `Yup`, or `vuelidate`** | Competing validation lifecycles with `ElForm`'s internal `async-validator`. Produces duplicate error text and unreliable `trigger: 'blur'/'change'` behaviour. Already rejected in the v1.1 research for the same reason. | `ElForm` `:rules` with custom `validator` callbacks; pure predicates in `src/utils/metricValidation.ts` |
| **`driver.js` / `intro.js` / `shepherd.js`** | `ElTour` is already in the bundle (verified at the 2.5.6 floor and in 2.14.0). | `el-tour` + `el-tour-step` |
| **`xstate` or any wizard/flow library** | 3 linear steps; a `ref<number>` plus a `canNextStep` computed is the entire state model. | `ref` + `el-steps` (pattern proven in `DeviceConfigWizard.vue`) |
| **Form-builder libraries** (`form-create`, `form-generator`, `vue-form-making`) | The forms are metadata-driven already; adding a schema DSL means the metadata must be translated into a second schema language. | `v-for` over `metricMetaStore` definitions inside a normal `el-form` |
| **A new toast/notification library** | `ElMessage` + `ElMessageBox` + `ElNotification` all ship and are used in 26 sites. | `ElMessage.error/success`, `ElMessageBox.confirm` |
| **`axios-retry` or an axios wrapper library** | Retrying `POST /api/thresholds` risks duplicate writes (`uk_user_metric` would throw 409); the current single interceptor is 80 readable lines. | Local `silent` flag + `ApiError` in `src/utils/request.ts` |
| **`pinia-plugin-persistedstate`** | `useMetricMetaStore` intentionally starts from bundled `FALLBACK_METRIC_META` for zero-latency first paint and fetches live data in `App.vue`; nothing in the new features needs persisted client state (wizard draft state can live in the component). Note: the store's docstring claims a `localStorage` fallback that the code does **not** implement — do not "fix" that by adding a persistence plugin. | In-memory store + static fallback (existing) |
| **A new `threshold_template` table or Flyway/Liquibase** | `alert_rule` already stores platform defaults; `ThresholdEvaluator` already falls back to it. Schema versioning is an explicitly deferred project decision (`PROJECT.md` Key Decisions). | `alert_rule` defaults + `user_threshold` overrides |
| **A new `CompletenessController` / endpoint** | Completeness is derived from data the client already holds via `preventionApi.getProfile()`; `ProfileScorer` already surfaces the score through `HealthScoreVO.factors`. | Pure client function + `el-progress` |
| **Importing `@vueuse/core` directly** | It is a **transitive** Element Plus dependency, not declared in `package.json`. Direct imports break when the dependency graph shifts or lockfile is re-resolved (`npm install` in CI). | Native Vue APIs (`ref`, `computed`, `watch`, `onMounted`) — all auto-imported via `AutoImport({ imports: ['vue', 'vue-router', 'pinia'] })` |
| **Upgrading Pinia to v4 or Vue to 3.6+** | Pinia latest is **4.1.0** (major bump) vs installed 2.3.1; Vue latest is 3.5.45 vs 3.5.34. Neither is required by any feature and both widen the regression surface on a working app. | Stay on `pinia@^2.1.7` → 2.3.1, `vue@^3.4.15` → 3.5.34 |
| **New Chart/ECharts work** | The wizard is numeric, not trend-visualized. `echarts` is already bundled if a preview sparkline is later desired. | `el-slider` marks; existing `SparklineChart.vue` if needed |
| **`ElSegmented`** | Introduced **after** the declared floor: absent in 2.6.3, present in 2.7.0 (verified against unpkg). Installed 2.14.0 has it, but `package.json` says `^2.5.6`, so any environment that resolves against the floor would break. Not worth the risk. | `el-radio-group` + `el-radio-button` (already used in `OcrInput.vue:11-15`) |

---

## Stack Patterns by Variant

### Variant: raise the declared Element Plus floor (recommended, one-line change)

`package.json` declares `"element-plus": "^2.5.6"` but the lockfile and `node_modules` are **2.14.0**, and CI runs `npm install` (not `npm ci`) — so the floor is effectively aspirational. Two consistent options:

**If you use only floor-available components (everything above except `ElSegmented`):** leave `^2.5.6`. Verified available at 2.5.6: `ElTour`, `ElTourStep`, `ElSteps`, `ElStep`, `ElResult`, `ElProgress`, `ElSlider` (`range`, `marks`, `show-stops`), `ElForm` (`scrollToError`, `scrollIntoViewOptions`, `inlineMessage`, `statusIcon`), `ElMessage` (`grouping`, `max`), `ElDescriptions`, `ElTimeline`.

**If you want `ElSegmented` or newer message placement props:** bump the declaration to `"element-plus": "^2.14.0"` in the same commit that uses it, so the declared contract matches the installed tree. Do this consciously — `npm ci` (used by the Docker build, `hhs-frontend-v2/Dockerfile`) resolves from the lockfile, while CI's `npm install` may drift.

### Variant: wizard persists a draft across accidental dialog close

Keep the draft in the component (`reactive`), not the store. If resume is required, `utils/storage.ts` already wraps `localStorage` with `getToken/setUser/clear` — extend it with namespaced `getItem/setItem` rather than adding a persistence plugin.

### Variant: backend ordering validation for thresholds

Follow the existing pattern rather than inventing one:
```java
// mirrors @ValidMetricRecord -> MetricRecordValidator
@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ThresholdOrderingValidator.class)
public @interface ValidThresholdOrdering { … }
```
Apply to `UserThresholdRequest`, implement `ThresholdOrderingValidator` using `BigDecimal.compareTo` against the registry's `hardMin`/`hardMax` from `HealthMetricMetaRegistry`. This gives the frontend a friendly `errors[]` entry from the existing handler — no new exception type, no new response shape.

### Variant: HTTP status correctness for "record does not exist"

`PreventionController.getMyHealthProfile()` returns a body-code 404 inside HTTP 200 (`Result.failure(404, …)`). Do **not** "fix" this globally by making `request.ts` treat body-code 404 specially — that would mask real failures. Instead, mark that one call `{ silent: true }` and branch on `error.code === 404` in the view (create-mode). Same for the completeness probe.

---

## Version Compatibility

| Package A | Compatible with | Notes |
|---|---|---|
| `element-plus@2.14.0` (installed) | `vue@3.5.34` (installed) | Element Plus 2.x requires Vue 3.2+. All components used here are stable APIs present since 2.5.x (except `ElSegmented`, 2.7.0+). |
| `element-plus@2.5.6` (declared floor) | `vue@^3.4.15` | Verified by fetching per-component `index.mjs`/`.d.ts` from unpkg at exactly 2.5.6: `tour` 200, `steps` 200, `result` 200, `progress` 200, `descriptions` 200, `timeline` 200, `segmented` 404. |
| `pinia@2.3.1` (installed) | `vue@3.5.34` | Setup-store syntax as used in `stores/metricMeta.ts` is unchanged. Do **not** jump to Pinia 4. |
| `axios@1.16.1` (installed) | `typescript@5.5` | `AxiosRequestConfig` module augmentation is the supported way to add `silent`; augment in `src/utils/request.ts` or a `src/types/axios.d.ts`. |
| `spring-boot-starter-validation@3.2.0` | `hibernate-validator@8.0.1.Final` | `jakarta.validation.*`, not `javax.*`. Class-level constraints are supported (already used by `@ValidMetricRecord`). |
| `async-validator@4.2.5` (transitive) | `element-plus@2.x` | Import via `FormRules` types only; never construct validators directly. |
| `vitest@4.1.0` + `happy-dom@20.8.4` | `@vue/test-utils@2.4.6` | `src/__tests__/setup.ts` mocks only `$message` on `config.global.mocks` — new tests that exercise `request.ts` must `vi.mock('element-plus')` for `ElMessage` since the interceptor imports it directly. |

---

## Installation

```bash
# Backend (hhs-backend) — nothing to add.
# spring-boot-starter-validation and the existing custom-constraint pattern
# (@ValidMetricRecord -> MetricRecordValidator) already cover ordering rules.
cd hhs-backend && mvn dependency:resolve

# Frontend (hhs-frontend-v2) — nothing to add.
# Verify the installed tree matches the declared contract before planning:
cd hhs-frontend-v2
node -e "console.log(require('./node_modules/element-plus/package.json').version)"   # → 2.14.0
npm list element-plus vue pinia axios

# Optional, only if a floor-verified component is later needed:
# none required — every component in this research exists at 2.5.6.
```

**Installation delta for this milestone: zero packages, zero Maven artifacts, zero migrations.**

---

## Confidence Assessment

| Area | Confidence | Basis |
|---|---|---|
| Installed versions | **HIGH** | Read from `node_modules/<pkg>/package.json` and `package-lock.json` `packages[]` entries, not from a provider. |
| Component availability at the 2.5.6 floor | **HIGH** | Per-component `index.mjs` / `.d.ts` fetched from `unpkg.com/element-plus@2.5.6/...` and matched against the installed 2.14.0 typings. |
| "Latest version" claims (Pinia 4.1.0, Vue 3.5.45, Axios 1.21.0, Element Plus 2.14.7) | **MEDIUM** | npm registry metadata; `classify-confidence --provider npm --verified` returns LOW, so these are advisory only and are **not** used to justify any change. |
| Existing backend error shape (`{code, message, errors[]}`) | **HIGH** | Read directly from `GlobalExceptionHandler.java:73-137` and `Result.java`. |
| Double-toast prevalence (69 of 118 catch blocks) | **HIGH** | Counted by grep over `src/**/*.{ts,vue}`. |
| `ProfileScorer` rubric reusability as a client mirror | **MEDIUM** | Logic is simple and readable, but it is a deduction model, not a per-field contract; a future admin-editable rubric would invalidate the client copy. |
| `ElSegmented` introduction at 2.7.0 | **HIGH** | Binary probe: absent at 2.6.1 and 2.6.3, present at 2.7.0. |
| `@vueuse/core` being transitive-only | **HIGH** | Present in `element-plus` `dependencies` (14.3.0) and absent from root `package.json`. |

---

## Sources

- **Installed artifacts (primary, HIGH):** `hhs-frontend-v2/package-lock.json` (`packages[]` versions), `hhs-frontend-v2/node_modules/{element-plus,vue,pinia,axios,async-validator,dayjs}/package.json`, `node_modules/element-plus/es/components/{tour,steps,result,progress,slider,form,message,config-provider}/src/*.d.ts`.
- **Codebase files read (HIGH):** `src/utils/request.ts`, `src/views/health/Thresholds.vue`, `src/stores/metricMeta.ts`, `src/api/{threshold,prevention,user,score,alert,metric-meta}.ts`, `src/views/data-input/components/{DeviceConfigWizard.vue,ReferenceGuideCard.vue}`, `src/components/metric/UniversalMetricDialog.vue`, `src/composables/useMetricValidation.ts`, `src/utils/metricValidation.ts`, `src/constants/metricMeta.ts`, `src/views/prevention/Profile.vue`, `src/views/user/Profile.vue`, `src/views/dashboard/Index.vue`, `src/main.ts`, `src/App.vue`, `src/router/index.ts`, `src/components/layout/MainLayout.vue`, `src/types/api.ts`, `src/__tests__/setup.ts`, `vite.config.ts`, `vitest.config.ts`, `package.json`.
- **Backend files read (HIGH):** `exception/GlobalExceptionHandler.java`, `exception/BusinessException.java`, `common/Result.java`, `common/constant/ErrorCode.java`, `controller/{UserThresholdController,PreventionController,MetricMetadataController,HealthScoreController,AlertController,UserController}.java`, `service/domain/{HealthMetricMetaRegistry,MetricDefinition,MetricKeyNormalizer,ThresholdEvaluator,ThresholdRangeChecker,ProfileScorer,ScoreCalculator,MetricValidator}.java`, `service/impl/UserThresholdServiceImpl.java`, `validation/{MetricRecordValidator,MetricRangeValidator}.java`, `dto/{UserThresholdRequest,HealthProfileRequest,HealthMetricRequest}.java`, `entity/{HealthProfile,UserThreshold,AlertRule,User}.java`, `vo/HealthScoreVO.java`, `resources/sql/schema.sql`, `resources/application.yml`, `pom.xml`.
- **Upstream verification (MEDIUM):** `https://unpkg.com/element-plus@<version>/es/components/...` binary and typing probes for 2.4.0, 2.4.4, 2.5.0, 2.5.6, 2.6.0, 2.6.1, 2.6.3, 2.7.0; `https://registry.npmjs.org/element-plus`, `/vue`, `/pinia`, `/axios` dist-tags.
- **Seam outputs:** `gsd-tools query classify-confidence --provider {context7,npm,codebase} [--verified]` → context7 MEDIUM, npm LOW, codebase LOW; `gsd-tools query research-plan` routed the Element Plus doc questions to the `context7` provider (MEDIUM).

---

*Stack research for: guided threshold wizard, profile completeness guidance, and unified friendly error feedback (HHS v1.3)*
*Researched: 2026-11-05*
