# Project Research Summary

**Project:** Health Hack System (HHS)
**Milestone:** v1.3 — 引导式配置与友好错误反馈 (Guided Configuration & Friendly Error Feedback)
**Domain:** Retrofitting configuration guidance and error UX into a live Spring Boot 3.2 + Vue 3 consumer health platform
**Researched:** 2026-11-05
**Confidence:** HIGH

> This file SUPERSEDES the previous SUMMARY.md, which described the already-shipped v1.1 milestone. All content below reflects the four v1.3 research documents dated 2026-11-05.

---

## Executive Summary

HHS is an already-deployed, feature-complete health platform (metric entry, AI advice, preventive risk, alerting). v1.3 is **not greenfield**: it is an incremental UX/correctness retrofit into three specific surfaces — the threshold configuration page (`views/health/Thresholds.vue`), health-profile completeness guidance across risk/score/AI, and a site-wide unified friendly-error layer. The research converges on a single conclusion: **zero new runtime dependencies, zero new database objects, zero new Maven artifacts are required.** Everything needed (Element Plus 2.14.0 already installed, `useMetricMetaStore`, `thresholdApi`, `preventionApi.getProfile()`, `spring-boot-starter-validation`, the existing `GlobalExceptionHandler`) is already in the tree. The work is integration, refactoring, and copy — not acquisition.

The recommended approach is a strictly ordered build. **Error feedback must land first** because the other two features add new call sites; shipping them first would multiply the duplicate-toast debt the milestone exists to remove. The error layer is a "translate-don't-display" refactor: `request.ts` stops toasting and instead rejects a typed `ApiError`; a single `showError()` owns non-form presentation, and `useFormServerErrors` projects backend `fieldErrors[]` onto `el-form-item :error`. Second is the threshold wizard, built on the proven `DeviceConfigWizard.vue` pattern with options sourced from `useMetricMetaStore` — and, critically, **server-side canonical-key normalization plus a data repair**, because the existing hardcoded options (`glucose`, `temperature`) and the seeded `alert_rule` rows are non-canonical, meaning those thresholds are **already silently dead today**. Third is profile completeness, which must be **non-blocking guidance, never a route gate**. Fourth is a sitewide sweep of ~105 error call sites with a migration ledger.

The key risks are all correctness risks, not UX risks. (1) Silent dead alerts from non-canonical `metric_key` values — highest severity, and the wizard would create more dead rows if normalization is deferred. (2) An inverted condition in `ThresholdRangeChecker.isWarningRange` that makes warnings between `warningHigh` and `criticalHigh` evaluate as NORMAL. (3) A centralized error handler that toasts on user cancellation (`'cancel'` sentinel strings, `AbortError`) or swallows caller contracts that stores depend on (`stores/push.ts` returning `false`). (4) Hard-gating features on optional profile fields, which would regress existing users. (5) Breaking the 90-test frontend suite via eager store fetches or new Element Plus components without stubs. Each has a concrete, verified prevention in the architecture and pitfalls research.

---

## Key Findings

### Recommended Stack

Every capability is covered by the installed tree; the milestone's installation delta is **zero packages, zero Maven artifacts, zero migrations**.

**Core technologies (verify, do not install):**
- **Element Plus 2.14.0 installed** (declared `^2.5.6`) — `ElSteps`/`ElStep` for the wizard, `ElSlider range` + `marks` for zone editing, `ElProgress` + `ElResult` + `ElAlert` for completeness/empty states, `ElMessage` (`grouping`, `max`) and `ElMessageBox` for feedback, `ElTour` if first-run guidance is ever needed. All verified present at the declared 2.5.6 floor except `ElSegmented` (2.7.0+) — do not use it without bumping the floor.
- **Vue 3.5.34 (declared `^3.4.15`)** — wizard step state is `ref<number>` + `canNextStep`; no state-machine library is justified.
- **Pinia 2.3.1** — `useMetricMetaStore` is the SSOT for the threshold picker, units, precision, and `hardMin`/`hardMax`. Stay on v2; do not jump to Pinia 4.
- **Axios 1.16.1** — `request.ts` is the single error seam. Add a `silent?: boolean` module augmentation on `AxiosRequestConfig`; do not add a second client.
- **Spring Boot Validation 3.2.0 / Hibernate Validator 8** — class-level constraint (`@ValidThresholdRange`) mirrors the existing `@ValidMetricRecord` → `MetricRecordValidator` precedent; no new validation framework.

**Explicit do-not-add list:** a second UI library, `vue-i18n` (Chinese-only product; `ErrorCode` already carries zh/en), VeeValidate/Zod/Yup (fights `ElForm`'s async-validator), `driver.js`/`intro.js` (`ElTour` is bundled), `xstate`, `axios-retry` (would risk duplicate POSTs), `pinia-plugin-persistedstate`, a `threshold_template` table or Flyway (migration strategy is explicitly deferred), a new `CompletenessController`, and **direct imports of `@vueuse/core`** (it is transitive via Element Plus only; a direct import breaks on lockfile re-resolve).

### Expected Features

Feature IDs below are stable and intended for direct use in REQUIREMENTS/ROADMAP.

**Must have (launch this milestone, P1):**
- **THR-01** metadata-driven metric picker from `useMetricMetaStore` (replaces 6 hardcoded options; restrict to alert-capable metrics, not all 23)
- **THR-02** recommended values pre-filled with a `推荐` tag / "使用推荐值"
- **THR-03** plain-language definition + unit per input ("警告高值：高于此值发出黄色提醒")
- **THR-05** ordering/physiological guardrails on both client and server
- **THR-06** generated confirmation sentence stating the behavioral outcome
- **THR-07** teaching empty state ("当前使用系统默认规则" + defaults + CTA)
- **PRF-01** completeness score/tier on Profile and dashboard
- **PRF-02** "why we ask" helper per field
- **PRF-03** 必需/推荐/可选 criticality labels (no new required fields)
- **PRF-04** missing-data impact banner naming concrete downstream effects
- **PRF-05** backend discloses applied defaults (additive `assumptions[]` / completeness signal)
- **PRF-06** "基于不完整档案估算" badge on Risk and Score
- **PRF-07** dismissible dashboard prompt with snooze
- **ERR-01** single error presentation (foundation; `silent` + typed error)
- **ERR-02** all user-facing messages Chinese and human
- **ERR-03** inline field errors bound from backend `fieldErrors[]`
- **ERR-04** specific copy replacing "操作失败"
- **ERR-06** duplicate suppression (`grouping: true`)

**Should have (v1.x, P2):**
- **THR-04** live 4-zone preview bar; **THR-08** duplicate-threshold recovery action; **THR-09** 宽松/标准/严格 preset mode
- **PRF-08** feature-gate explanation; **PRF-09** completion checklist card; **PRF-10** just-in-time field prompt
- **ERR-07** distinct network/auth/validation presentation; **ERR-08** error summary block; **ERR-09** recovery-action buttons; **ERR-10** `ErrorState.vue`

**Defer (v2+):**
- **THR-10** condition-aware presets (needs clinical sign-off); **THR-11/12** explanatory disclosure + reset; **PRF-11** before/after impact receipt; **PRF-12** completeness-aware AI advice; **ERR-11** repeated-error analytics; **ERR-12** formalized shared copy patterns

**Anti-features (do not build):** single target range replacing the four-tier model (breaks the alert engine contract); auto-creating thresholds silently (health alerts must be consensual); exposing all 23 metrics in the picker (dead settings for non-alertable metrics); a blocking profile modal at login; making free-text medical history required; re-prompting on every page load; removing silent defaults in favor of errors; toast-only validation; collapsing everything into "操作失败，请稍后重试"; surfacing raw English/field names/stack traces.

### Architecture Approach

The milestone is an **integration question, not a greenfield design**. Three cross-cutting flows must be introduced without breaking v1.0–v1.2 behavior:

1. **Translate-don't-display error flow.** `request.ts` normalizes both failure channels (HTTP-400 validation and HTTP-200-with-`code!=200` business errors) into one `ApiError {kind, message, status, code, fieldErrors, formField}` and rejects — it no longer toasts (401 logout is the sole exception). `showError()` is the only non-form toast outlet; `useFormServerErrors` maps `fieldErrors` to `el-form-item :error`. The backend envelope gains `errorCode` and structured `fieldErrors: [{field,message,code?}]` (replacing `"field: msg"` strings), and `Result.code`'s existing numeric semantics are preserved to protect existing tests.
2. **Derived-readiness flow.** One `GET /api/prevention/profile/readiness` (new endpoint on the existing `PreventionController`) reads the profile once, runs a pure `ProfileCompletenessCalculator`, and returns `{completeness, dependencies[]}` from a static dependency matrix. A session-scoped `useProfileReadinessStore` serves a single global `ProfileReadinessBanner` plus Risk/Score/Dashboard consumers — no N+1, no per-page probing.
3. **Threshold ordering flow.** `@ValidThresholdRange` class-level constraint on `UserThresholdRequest` (authoritative) plus a mirror pure frontend function for instant UX feedback. No DB CHECK constraint (no migration framework; existing rows may violate ordering).

**Major components (new):** `ApiError`/`normalizeApiError`/`showError` (`utils/appError.ts`), `useFormServerErrors` composable, `@ValidThresholdRange` + `ThresholdRangeValidator`, `utils/thresholdValidation.ts`, `ProfileCompletenessCalculator` (pure domain), `ProfileReadinessService(Impl)` + `ProfileReadinessVO` + `GET /profile/readiness`, `useProfileReadinessStore`, `ProfileReadinessBanner.vue`, `FieldErrorVO`.

**Major components (modified):** `Result.java`, `GlobalExceptionHandler`, `request.ts`, `types/api.ts`, `UserThresholdRequest`, `ProfileScorer` (delegates to the new calculator so weights exist in exactly one place), `ThresholdRangeChecker` (defect fix), `Thresholds.vue`, `Profile.vue`, `Risk.vue`, `Score.vue`, `MainLayout.vue`.

**Verified defects that must be fixed (the foundation is not sound without them):**
- **D-1 `ThresholdRangeChecker.isWarningRange` inverted conditions (HIGH)** — both intervals are empty for legal data, so values between `warningHigh` and `criticalHigh` (e.g. HR 110 with 100/120) classify as NORMAL and **alerts are lost**. Fix by extracting a single mutually-exclusive `classify()`.
- **D-2 `Thresholds.vue` form items lack `prop` (HIGH)** — blocks any field-level error injection or `validateField`.
- **D-5 risk assessment fabricates scores with no profile (HIGH, needs product sign-off)** — `RiskAssessmentServiceImpl` returns 30/25/25 from thin air when `profile == null`; should become BLOCKED with a CTA.
- **D-3 business error codes degrade to 500**, **D-4 background requests still toast**, **D-6 unreachable `res.code` checks in 4 stores**.

### Critical Pitfalls

1. **Duplicate toasts — interceptor toasts, page toasts again.** ~105 `ElMessage.error` sites across 29 files; `request.ts` already toasts every failure. Make the interceptor transport-only, add `silent`, provide one `useErrorFeedback().show()`, enable `ElMessage({grouping:true})`, and leave success toasts in callers. This must land *before* new features add call sites.
2. **Cancellation/abort misclassified as failure.** `ElMessageBox.confirm` rejects with the string `'cancel'`/`'close'` at 11 hand-guarded sites, and `Score.vue` deliberately ignores `CanceledError`/`AbortError`. Classify dismissals → aborts → real errors, in that order, before rendering.
3. **Centralized layer swallows caller contracts.** `stores/push.ts` returns `false` on failure but `PushSettings.vue` shows "已启用" anyway; `stores/ai.ts`, `Score.vue` (`response.data.message`), `AIInput.vue` (429) all depend on failure detail. Always propagate a typed envelope, toast **and** rethrow; never `return undefined`.
4. **Non-canonical `metric_key` → thresholds silently never fire.** The wizard's hardcoded options and the seeded `alert_rule` rows use `glucose`/`temperature`/`bmi`, while evaluation matches exact-equal canonical keys (`bloodGlucose`/`bodyTemperature`). Normalize server-side on write, repair existing rows, align seeds, and add a contract test — the UI looks healthy while alerts are dead.
5. **First-visit profile 404 becomes a red toast.** `PreventionController` returns HTTP 200 with body `code=404`; the interceptor shows "请求的资源不存在" for every new user, and completeness guidance multiplies the callers. Treat absence as a normal state (`silent: true` or `success(null)`).

Additional critical items: ordering/partial-set validation gaps (including the "only criticalHigh set → nothing fires" case), toast storms from 10s polling and 401 cascades (needs a session latch + `silent`), hard-gating optional profile fields (never gate the action, gate result quality), raw English/field-name leakage, wizard state loss (`destroy-on-close` + step-local state), over-nagging/banner blindness, and breaking the 90-test suite via eager fetches, changed exports, or unstubbed components.

---

## Implications for Roadmap

The research independently converges (ARCHITECTURE's Waves 0–5 and PITFALLS' Phases 11–14) on the same order. Recommended: **a defect-fix task that can start immediately, then four phases, error layer first, with wizard and profile independent after the helper API freezes.**

### Phase 11a / Task 0: Threshold Evaluation Correctness (can start immediately)
**Rationale:** Independent of everything else, and it is the foundation of the threshold feature. Fixing the wrong alert behavior must precede adding configuration UI on top of it.
**Delivers:** `ThresholdRangeChecker.classify()` refactor + new `ThresholdRangeCheckerTest` covering warning/critical bands, null bounds, and inverted inputs.
**Addresses:** Groundwork for THR-05.
**Avoids:** Pitfall 5 (partial-set gaps); D-1. Note this changes alert output (previously lost alerts now fire) — needs a UAT entry and a check of existing threshold data distribution.

### Phase 11: Unified Friendly Error Feedback Foundation
**Rationale:** Both other features write error-handling code. Without the helper API frozen first, Phases 12–13 create new double-toast debt and get re-broken piecemeal. Lowest-numbered foundation in the error workstream.
**Delivers:** Backend `Result.errorCode` + `FieldErrorVO` + `GlobalExceptionHandler` producing structured `fieldErrors` (including `GlobalError` handling); frontend `ApiError` + `normalizeApiError` + `showError`; `request.ts` translate-only with `silent` and fixed `Accept-Language: zh-CN`; cancel/abort classification; Chinese copy whitelist (no English tokens); 401 session latch; `ElMessage` grouping/max; `useFormServerErrors`; test scaffolding (lazy stores, shared stubs, `vi.mock` for `ElMessage`).
**Addresses:** ERR-01, ERR-02, ERR-04, ERR-06 (and enables ERR-03, ERR-07, ERR-09).
**Avoids:** Pitfalls 1, 2, 3, 7, 9, 12, 14, 19; D-3, D-4, D-6.

### Phase 12: Threshold Configuration Wizard
**Rationale:** Highest correctness risk and highest data-integrity cost — silent dead alerts and inverted ranges. Needs the `fieldErrors` contract from Phase 11 for inline feedback. Must ship backend normalization and data repair *in the same phase*, or users create more dead rows.
**Delivers:** Parent-owned reactive draft + `sessionStorage` persistence + dirty-close guard (NOT the `destroy-on-close`/step-local anti-pattern); metadata-driven picker with canonical keys; recommended-value prefill; plain-language labels + units; ordering/partial validation on both tiers; server-side key normalization in `UserThresholdServiceImpl` + `metric_key` repair + `alert_rule` seed alignment; post-save confirmation summary; duplicate-key mapping to "打开编辑"; `:loading` guards.
**Addresses:** THR-01, THR-02, THR-03, THR-05, THR-06, THR-07 (THR-04/08/09 as stretch).
**Uses:** `useMetricMetaStore`, `ElSteps`, `ElSlider`, `ReferenceGuideCard`, `thresholdApi` (upsert via `getByMetricKey`; the unique key makes `create` throw on an existing row).
**Avoids:** Pitfalls 4, 5, 10, 15, 16; D-2.

### Phase 13: Health-Profile Completeness Guidance
**Rationale:** Depends on Phase 11's `silent` flag and on the profile-404 decision. Independent of Phase 12, so it may run in parallel once the helper API is frozen. Must be non-blocking throughout.
**Delivers:** `ProfileCompletenessCalculator` + `ProfileScorer` delegation (lock existing score output with a regression test) → `ProfileReadinessVO` + `GET /profile/readiness` + service/impl → `useProfileReadinessStore` (single fetch, essential vs. enhanced levels) → global dismissible banner in `MainLayout` with 7-day suppression → `Risk.vue` BLOCKED/DEGRADED CTA, `Score.vue` "基于不完整档案估算" badge, `Profile.vue` missing-field list + "why we ask" copy + save-triggered refresh → backend `assumptions[]` disclosure (additive/backward-compatible).
**Addresses:** PRF-01, PRF-02, PRF-03, PRF-04, PRF-05, PRF-06, PRF-07 (PRF-08/09/10 as stretch).
**Avoids:** Pitfalls 6, 8, 11, 17; D-5 (product sign-off required — this is a behavior change, flag it explicitly).

### Phase 14: Sitewide Submit-Path Sweep & Regression
**Rationale:** Mechanical but semantic work across 29 files; needs the frozen helper and the two feature surfaces in place. Doing it earlier means doing it twice.
**Delivers:** Complete migration ledger (file:line | double-toasted today | silent-by-design | success copy | new behavior | verification); removal of residual raw `ElMessage.error` outside the helper; failure-injection matrix per surface; prompt-inventory UX audit; full `npm run test:run` (90 + new) and `mvn clean verify -DskipITs`; E2E `quick-input.spec.ts` success-copy contract preserved.
**Addresses:** ERR-03/ERR-07/ERR-09 adoption across all pages; remaining ERR copy quality.
**Avoids:** Pitfalls 11, 13, 16, 17.

### Phase Ordering Rationale

- **Error envelope before any consumer.** Phases 12–13 require `fieldErrors`, `silent`, and typed `ApiError`; building them first produces zero-feedback or re-broken UI and rework.
- **Calculator before readiness service.** `ProfileCompletenessCalculator` must precede `ProfileReadinessService`, or completeness weights exist in two places and drift ("banner says 85, score computes 70").
- **BLOCKED behavior last.** The `RiskAssessmentServiceImpl` change must follow the banner/annotation work, or users see an error with no guidance.
- **Wizard normalization bundled with the wizard.** Shipping UI first lets users create dead threshold rows that require a second migration.
- **Grouping:** error contract → feature surfaces → sweep. This matches the architecture's "契约 → 消费 → 领域" and PITFALLS' explicit phase mapping.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 12 (Threshold Wizard):** canonical-key migration scope is unknown — the existing `user_threshold` and `alert_rule` rows must be audited for aliases and impossible orderings before the repair script is written; historical alert impact needs assessment.
- **Phase 14 (Sitewide Sweep):** 105 call sites with heterogeneous semantics; a per-site decision ledger is required, and some stores' `boolean` contracts may need redesign.
- **Phase 13 (product decision):** whether `riskAssessment` should be BLOCKED vs. disclosed-and-degraded is a product call, not a research gap.

Phases with standard patterns (skip research-phase):
- **Phase 11:** the error envelope/`ApiError`/`useFormServerErrors` patterns are fully specified in ARCHITECTURE.md with code examples; Element Plus APIs verified in local typings.
- **Phase 11a / Task 0:** the `classify()` bands are fully specified; pure TDD against the existing `ThresholdRangeChecker` interface.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | **HIGH** | Installed versions read from `node_modules` + `package-lock.json`, not providers. Component availability at the declared 2.5.6 floor verified per-component via unpkg. "Latest version" claims are MEDIUM/advisory and justify no change. |
| Features | **HIGH** | Interaction standards from Apple HIG, GOV.UK, and NN/g (fetched directly) plus repo-verified gaps. Competitor flow details are MEDIUM (several vendor pages JS-gated/404); anti-features are grounded in engine contracts, not competitor inference. |
| Architecture | **HIGH** | Every integration point located to file and line by direct audit; Element Plus `FormItem.error` confirmed in local typings. No external sources used or needed. |
| Pitfalls | **HIGH** | All 19 pitfalls verified against cited files; baseline measured (13 test files / 90 tests passing; 105 error sites across 29 files; 39 generic messages; 11 `'cancel'` guards; 10s polling). Library behavior checked in the local install. |
| **Overall** | **HIGH** | Internal retrofit with first-hand code evidence for every claim; no speculative technology bets. |

### Gaps to Address

- **Canonical-key repair scope:** unknown how many `user_threshold` rows use aliases and how many `alert_rule` seeds are non-canonical. Audit before Phase 12 planning; decide whether to notify affected users.
- **Class-level constraint routing:** whether `@ValidThresholdRange` violations land in `getFieldErrors` or `getGlobalErrors` must be confirmed at implementation time; the handler must consume both.
- **Risk-engine accuracy caveat:** `RiskAssessmentServiceImpl` looks metrics up by Chinese labels against canonical English keys, so metric contributions are effectively always null today. Guidance copy must not promise accuracy it cannot deliver; fixing this is adjacent scope and should be explicitly decided.
- **i18n depth:** the decision is Chinese-only for this milestone with `Accept-Language: zh-CN` fixed; DTO Bean Validation messages should be switched to Chinese values rather than building a message catalog.
- **Element Plus floor drift:** `package.json` declares `^2.5.6`, CI runs `npm install`, the installed tree is 2.14.0. Either stay within floor-verified components or bump the declaration in the same commit that uses newer ones — decide explicitly.
- **Existing test suite blind spots:** the 90 tests cannot detect duplicate toasts or alert-loss regressions; manual failure-injection and UAT walkthroughs are mandatory, not optional.

---

## Sources

### Primary (HIGH confidence)
- **Frontend code (direct read):** `src/utils/request.ts`, `src/views/health/Thresholds.vue`, `src/views/health/Score.vue`, `src/views/prevention/{Profile,Risk}.vue`, `src/views/user/PushSettings.vue`, `src/views/realtime/Monitor.vue`, `src/stores/{metricMeta,push,ai,alert}.ts`, `src/types/api.ts`, `src/constants/metricMeta.ts`, `src/utils/metricValidation.ts`, `src/views/data-input/components/{DeviceConfigWizard,ReferenceGuideCard}.vue`, `src/components/metric/UniversalMetricDialog.vue`, `src/__tests__/**`, `e2e/quick-input.spec.ts`
- **Backend code (direct read):** `exception/GlobalExceptionHandler.java`, `common/Result.java`, `common/constant/ErrorCode.java`, `controller/PreventionController.java`, `service/impl/UserThresholdServiceImpl.java`, `service/impl/HealthMetricServiceImpl.java`, `service/domain/{ThresholdRangeChecker,ThresholdEvaluator,ProfileScorer,ScoreCalculator}.java`, `service/impl/{RiskAssessmentServiceImpl,HealthReportServiceImpl,AIChatServiceImpl}.java`, `dto/{UserThresholdRequest,HealthProfileRequest}.java`, `resources/sql/schema.sql`
- **Installed artifacts:** `hhs-frontend-v2/package-lock.json`, `node_modules/{element-plus,vue,pinia,axios}/package.json`, Element Plus component typings (`form-item.d.ts`, `message.d.ts`, `slider.d.ts`)
- **Design guidance:** Apple Human Interface Guidelines (Onboarding, Privacy, Entering data); GOV.UK Design System (Error message, Error summary, Check answers); Nielsen Norman Group (Errors in forms, Empty-state design, Progressive disclosure, Required fields)
- **Project docs:** `.planning/PROJECT.md` (stack constraints, manual-migration decision)

### Secondary (MEDIUM confidence)
- npm registry dist-tags for "latest version" claims (advisory only; no change justified)
- MyFitnessPal FAQ and Bearable site for competitor onboarding signals (partial content)

### Tertiary (LOW confidence — not used to justify decisions)
- Vendor help pages (Apple Health Checklist, Fitbit zones, Samsung Health) were JS-gated or 404; competitor flow claims are limited accordingly.

---

*Research completed: 2026-11-05*
*Supersedes: prior SUMMARY.md content for v1.1*
*Ready for roadmap: yes*
