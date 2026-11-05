# Features Research — Guided Health Data Entry

**Domain:** Consumer / patient-facing digital health platform (metric entry, personalized alerting, preventive assessment)
**Researched:** 2026-11-05
**Confidence:** HIGH for interaction standards and HHS codebase facts; MEDIUM for competitor-specific flows (several vendor help pages were unreachable or JS-gated; see Sources)

**Scope.** Three new capability areas for HHS, building on already-shipped capabilities (four-tier metric validation, `UniversalMetricDialog`, 23-metric backend metadata SSOT, alert engine):

- **(A) Threshold configuration guidance** — `views/health/Thresholds.vue` currently offers 6 hardcoded metric options and 4 bare number inputs.
- **(B) Health-profile completeness guidance** — risk assessment, health score and AI advice silently consume defaults (age 35, BMI 22, neutral profile score 70) when profile data is missing.
- **(C) Site-wide friendly error feedback** — raw red toasts, duplicate toasts, English backend messages.

Feature IDs (`THR-*`, `PRF-*`, `ERR-*`) are stable and intended for direct use in REQUIREMENTS / ROADMAP.

---

## Current State (verified in code)

| Area | What exists | What is missing |
|------|-------------|-----------------|
| Metric metadata | `HealthMetricMetaRegistry` (23 metrics: `normalMin/Max`, `hardMin/Max`, `unit`, `precision`, `step`, `placeholder`, `guideHint`) exposed at `/api/metrics/metadata`; `useMetricMetaStore` preloaded in `App.vue`; `useMetricValidation` + `evaluateMetricValue` four-tier engine | `Thresholds.vue` does not consume it; hardcodes 6 options |
| Thresholds | `user_threshold` table, CRUD API, `ThresholdEvaluator` / `ThresholdRangeChecker` in the alert path; seeded `alert_rule` defaults for 7 metrics (heartRate, systolicBP, diastolicBP, glucose, bmi, temperature, weight) | `alert_rule` has **no API**; no cross-field validation; no recommended-value UI; no ordering guardrails; no zone preview |
| Profile | `HealthProfileServiceImpl` + `preventionApi.getProfile/create/update`; `Profile.vue` requires only gender + birthDate | No completeness score, no "why we ask", no feature-level disclosure, no re-prompt |
| Silent defaults | `RiskAssessmentServiceImpl`: missing birthDate → age 35, missing BMI → 22, missing profile → fixed disease scores 30/25/25. `ProfileScorer`: missing profile → 70; missing gender/birthDate −10, height/weight −15, BMI −10, lifestyle −15. `WellnessScorer`: no data → 70 | Defaults are invisible to the user and to the API response |
| Errors | Global Axios interceptor toasts every non-200; views also toast in `catch` (84 `ElMessage.error` call sites in `views/`) | Duplicate toasts; English DTO messages (`"User ID is required"`), English `BusinessException` literals (`"Threshold already exists for this metric"`, `"System error, please try again later"`); `errors[]` payload never bound to fields |

---

## A. Threshold Configuration Guidance

### Table Stakes

| ID | Feature (observable user capability) | Why Expected | Complexity | Dependencies / Notes |
|----|--------------------------------------|--------------|------------|----------------------|
| THR-01 | The metric picker lists **all alert-capable metrics from the metadata SSOT**, grouped 健康指标 / 保健指标, each option labeled `显示名 (单位)` | Users cannot understand why only 6 of the 23 recorded metrics can be alerted on; hardcoded lists drift from the registry | LOW | `useMetricMetaStore.allDefinitions` / `healthMetrics` / `wellnessMetrics` already exist. Restrict to metrics the alert engine can evaluate (see AF-THR-1) |
| THR-02 | Selecting a metric **pre-fills all four thresholds with recommended values** (or offers "使用推荐值" one-click) | Bare empty number boxes are the core complaint; leading products ship a defensible default the user can accept | LOW–MEDIUM | Derive from `normalMin/normalMax` + `PANIC_LIMITS` (`utils/metricValidation.ts`); optionally expose `alert_rule` seeds via a read-only endpoint. Label as 推荐, not fact |
| THR-03 | Each of the four inputs carries a **plain-language definition and unit**, e.g. "警告高值：高于此值发出黄色提醒（mmHg）" | "严重高值 / 警告高值 / 警告低值 / 严重低值" is clinical jargon; users guess direction and severity | LOW | Pure UI; reuse the `ReferenceGuideCard` visual language |
| THR-04 | A **live 4-zone preview bar** renders the configured ranges as colored bands (严重低 / 警告低 / 正常 / 警告高 / 严重高) with entered values labeled | Users verify intent visually instead of mentally ordering four numbers | MEDIUM | Computed client-side from the four values plus metric `normalMin/Max`; keep thresholds consistent with `evaluateMetricValue` |
| THR-05 | **Ordering and physiological guardrails block save with an explanatory message**: `criticalLow ≤ warningLow < warningHigh ≤ criticalHigh`; all values within `hardMin…hardMax` | Without this, users create unusable thresholds (e.g. warning high below warning low) and the alert engine silently misbehaves | LOW–MEDIUM | No frontend rules exist; backend `UserThresholdRequest` only enforces `@DecimalMin(0)`. Backend cross-field validation is required too |
| THR-06 | A **generated sentence confirming intent** before save: "当收缩压 ≥ 140 时发黄色提醒，≥ 160 时发红色提醒；≤ 90 时发黄色提醒，≤ 80 时发红色提醒" | Translates four numbers into the actual behavioral outcome and catches reversed intent | LOW | Pure frontend string builder |
| THR-07 | **Empty state teaches instead of being blank**: when no user thresholds exist, show "当前使用系统默认规则" plus the effective defaults and a CTA "使用推荐阈值" | This is exactly where users currently see nothing and do not know what to fill | LOW–MEDIUM | Needs effective-defaults source (seed `alert_rule` or derived). NN/g empty-state pattern: state what would appear + link directly to the action |
| THR-08 | Human-readable duplicate handling: an existing threshold shows "该指标已有阈值，是否改为编辑？" with an **打开编辑** action, never an English error | Backend currently returns `Threshold already exists for this metric` | LOW | `UserThresholdServiceImpl.create` + `ErrorCode.RESOURCE_ALREADY_EXISTS`; frontend already has `handleEdit` |

### Differentiators

| ID | Feature | Value Proposition | Complexity | Notes |
|----|---------|-------------------|------------|-------|
| THR-09 | **Guided preset mode** — instead of typing four numbers, user picks 宽松 / 标准 / 严格 (or 临床指南 / 个性化); the four values are computed and shown for review | Removes the numeric-literacy barrier; matches how Fitbit/Apple present zones rather than raw boundaries | MEDIUM | Each preset = `{criticalLow, warningLow, warningHigh, criticalHigh}` per metric; "标准" from registry normal range + panic limits; "严格" tightens toward normal |
| THR-10 | **Condition-aware presets** — e.g. "我已确诊高血压" produces BP thresholds aligned to guideline | Personalized clinical relevance; profile already stores `familyHistory` / `lifestyleHabits` | MEDIUM–HIGH | Catalog keyed by metric + optional condition tag; never auto-apply from profile without explicit user choice (privacy) |
| THR-11 | **"Why this matters" disclosure per metric** explaining the consequence, e.g. "未设置时使用系统默认：收缩压 ≥140 黄色、≥160 红色" | Turns configuration from abstract to outcome-based | LOW | Reuses registry `guideHint` + seed `alert_rule` values |
| THR-12 | **Reset to recommended** action inside edit mode | Users who over-tightened thresholds can recover without deleting and recreating | LOW | One button + confirmation |

### Anti-Features

| ID | Anti-Feature | Why Requested | Why Problematic | Alternative |
|----|--------------|---------------|-----------------|-------------|
| AF-THR-1 | Expose **all 23 metrics** in the threshold picker | "The registry has 23, show them all" | Thresholds only make sense for metrics the alert engine evaluates (`ThresholdEvaluator` / `IntelligentAlertService`); offering them for `mood`, `steps`, `energy` creates dead settings | THR-01: alert-capable subset, with a short note for non-alertable metrics |
| AF-THR-2 | Keep **four free-form number inputs** as the only path | Least implementation work | The exact failure being fixed; competitors provide defaults, labels, or zones | THR-02, THR-03, THR-09 |
| AF-THR-3 | **Auto-create** thresholds silently from profile/metrics | "Reduce user effort" | Users do not know alerts exist until one fires; health alerts must be consensual | Offer recommended values (THR-02) and require explicit save; show defaults in empty state (THR-07) |
| AF-THR-4 | Replace the four-tier model with a **single target range** | Simpler UI | Breaks the engine contract (`warningLow/High` + `criticalLow/High` consumed by `ThresholdEvaluator`, `ThresholdRangeChecker`, `RecoveryNotifier`) and loses severity distinction | Keep the four values; make them legible via zones (THR-04) and presets (THR-09) |
| AF-THR-5 | Allow thresholds **outside physiological hard limits** | "Power users want full control" | A heart-rate critical high of 500 never fires meaningfully and undermines alert credibility | THR-05 guardrails with clear copy |

---

## B. Health-Profile Completeness Guidance

### Table Stakes

| ID | Feature (observable user capability) | Why Expected | Complexity | Dependencies / Notes |
|----|--------------------------------------|--------------|------------|----------------------|
| PRF-01 | **Completeness score and tier** shown persistently on `Profile.vue` (e.g. "档案完整度 60% · 标准档案") with per-field status | Users cannot tell whether their profile is usable; Apple Health's Health Checklist and MyFitnessPal's signup make completion state visible | LOW–MEDIUM | Frontend can compute from `getProfile()`; a backend-provided score avoids duplicated rules |
| PRF-02 | **"Why we ask" helper under each field**, e.g. 出生日期 → "用于计算年龄，影响心血管风险评估"; 身高/体重 → "用于计算 BMI 与体重维度评分"; 家族病史 → "用于糖尿病/高血压风险模型" | Every defaulted field has a hidden consumer; Apple HIG requires purpose strings that are specific and state the benefit | LOW | Pure copy; align with actual consumers in `RiskAssessmentServiceImpl`, `ProfileScorer`, `ScoreCalculator` |
| PRF-03 | **Field criticality labeling** (必需 / 推荐 / 可选) with an explicit note that 过敏史/家族病史/生活习惯 are optional but improve accuracy | `Profile.vue` only requires gender + birthDate yet presents all fields identically | LOW | Copy + tag component; do not add new required fields |
| PRF-04 | **Missing-data banner listing concrete downstream effects**: "缺少身高/体重，BMI 与体重评分将按参考值估算" | Replaces invisibility with a specific, honest statement (NN/g: explain what depends on the data) | LOW–MEDIUM | Needs agreed field → feature mapping, derivable from verified call sites |
| PRF-05 | **Backend discloses applied defaults** in API responses (e.g. `assumptions: [{field:"age", value:35, source:"default"}]` or `profileCompleteness`), instead of silently substituting | Root cause: `RiskAssessmentServiceImpl` uses 35 / 22 / fixed scores with no signal to the client | MEDIUM | Touches `RiskAssessmentServiceImpl` and `ProfileScorer` / `ScoreCalculator`; must be additive and backward-compatible |
| PRF-06 | **"Estimated, not personalized" badge** on `views/prevention/Risk.vue` and `views/health/Score.vue` when defaults were used, linking to `Profile.vue` | Users will otherwise trust a risk score derived from placeholder age/BMI; health apps consistently mark estimated values | MEDIUM | Depends on PRF-05; badge copy: "基于不完整档案估算 · 完善档案" |
| PRF-07 | **Persistent but dismissible dashboard prompt** when completeness is below a threshold: "完善健康档案，让评分更准确" with `完善档案` CTA and `稍后提醒` | Nothing currently prompts completion; a passive page is never visited | MEDIUM | Dashboard already aggregates score-adjacent data; snooze timestamp in `localStorage` (`utils/storage.ts` exists) |
| PRF-08 | **Feature-gate explanation instead of silent degradation**: opening risk assessment with no profile shows what will be estimated and offers to complete the two required fields first | Matches Apple HIG "request only when the feature needs it" and NN/g just-in-time guidance | MEDIUM | Inline confirm/dialog on `Risk.vue` `createAssessment`; reuses profile API |

### Differentiators

| ID | Feature | Value Proposition | Complexity | Notes |
|----|---------|-------------------|------------|-------|
| PRF-09 | **Completion checklist card** (mirrors Apple Health Checklist): grouped 基础信息 / 健康背景, each row with a status icon and jump-to-field action, dismissible at 100% | Makes a multi-field form feel like a finite, achievable set | LOW–MEDIUM | Dashboard or Profile header; no new backend |
| PRF-10 | **Just-in-time field prompt** — ask only the missing field at the moment a dependent feature is used (e.g. height/weight inside the risk-assessment flow) | Removes the upfront-form tax; HIG says postpone nonessential setup and request at point of need | MEDIUM | Requires field → feature map (PRF-02) and a small reusable `ProfileFieldPrompt` dialog |
| PRF-11 | **Before/after impact receipt** after completing the profile: "健康评分从估算 70 更新为 82" or "风险等级从 中 降为 低" | Converts completion into a visible reward and builds trust in the score | MEDIUM–HIGH | Capture pre-completion values; score cache key `health:score:{userId}` exists |
| PRF-12 | **Completeness-aware AI advice** — AI responses state which profile attributes were used or missing | AI advice currently consumes profile data opaquely; transparency differentiates health AI | MEDIUM | `AIChatServiceImpl` already injects `HealthProfileService`; add a footnote in response metadata |

### Anti-Features

| ID | Anti-Feature | Why Requested | Why Problematic | Alternative |
|----|--------------|---------------|-----------------|-------------|
| AF-PRF-1 | **Mandatory blocking profile modal at login** until all fields are filled | "Guarantees complete data" | Hostile onboarding; contradicts Apple HIG (postpone nonessential setup), NN/g progressive disclosure, and Bearable's deliberate data minimization; drives abandonment | PRF-07 dismissible prompt + PRF-10 just-in-time prompts |
| AF-PRF-2 | Make **all fields required** (including allergy/family history/lifestyle free text) | "Improve model accuracy" | Free-text medical history cannot responsibly be mandatory; users may fabricate data, which is worse than a disclosed default | PRF-03 criticality labeling; keep gender/birthDate required only |
| AF-PRF-3 | **Re-prompt on every page load** | "Maximum visibility" | Becomes nagging; users dismiss reflexively | Snooze + single surface (dashboard) with a frequency cap (e.g. 7 days) |
| AF-PRF-4 | **Remove the silent defaults** and return errors instead | "Force completeness" | Health score and risk assessment become unusable for new users; current `NO_DATA` handling beats a hard failure | Keep defaults but disclose them (PRF-05/06) and invite completion |
| AF-PRF-5 | Collect **age directly** instead of birth date | Fewer fields | Age goes stale immediately; store birth date once (already in schema) | Keep `birthDate`; show derived age read-only |

---

## C. Site-wide Friendly Error Feedback

### Table Stakes

| ID | Feature (observable user capability) | Why Expected | Complexity | Dependencies / Notes |
|----|--------------------------------------|--------------|------------|----------------------|
| ERR-01 | **One error message per failure** — the global interceptor stops toasting when the caller handles the error (opt-out flag or centralized presentation), eliminating duplicate red toasts | 84 view-level `ElMessage.error` sites plus a global interceptor guarantee duplicates today | MEDIUM | Foundation feature. Add `{ silent: true }` to `utils/request.ts` or route all errors through a `useErrorHandler` composable; migrate high-traffic pages first |
| ERR-02 | **All user-facing error text is Chinese and human** — no `"User ID is required"`, no `"Threshold already exists for this metric"`, no `"System error, please try again later"` | Backend DTO annotations and several `BusinessException` literals are English; users see raw English | MEDIUM | `GlobalExceptionHandler` already localizes `ErrorCode`; DTO Jakarta messages need Chinese values; replace literal English in `UserThresholdServiceImpl`, `HealthProfileServiceImpl`, generic handler |
| ERR-03 | **Field-level inline errors** bound from the backend `errors[]` payload (e.g. `warningHigh: 警告高值必须大于警告低值`) rendered under the matching `el-form-item` | Current behavior drops `errors[]` and shows a generic toast; GOV.UK and NN/g both require inline messages beside the field | MEDIUM | `GlobalExceptionHandler` returns `field: message` strings; needs a stable field-name → form-path map. `el-form` supports programmatic field errors |
| ERR-04 | **Copy dictionary replaces generic fallbacks**: every catch says what failed and what to do ("无法保存阈值，请检查高值与低值的顺序"), never "操作失败" / "请求失败" | 84 call sites use generic strings; users cannot act on them | LOW | Build `constants/errorMessages.ts`; migrate page by page |
| ERR-05 | **User input is never cleared** on validation or server error; the dialog stays open with values intact | GOV.UK explicitly forbids wiping entries; the metric dialog already preserves values, but threshold/profile forms need an audit | LOW | Audit all `catch` blocks for form resets |
| ERR-06 | **Duplicate suppression**: identical messages within a short window collapse to one toast | Retry loops and multi-request pages produce stacked toasts | LOW | Wrapper around `ElMessage` with key + timestamp |
| ERR-07 | **Network vs. validation vs. authorization errors are visually and verbally distinct**: inline red for validation, toast for transient network, explanatory state for 403/404 | Today all collapse to "请求失败"/"操作失败" | LOW–MEDIUM | `request.ts` already branches on status; keep branching but improve copy and presentation channel |

### Differentiators

| ID | Feature | Value Proposition | Complexity | Notes |
|----|---------|-------------------|------------|-------|
| ERR-08 | **Error summary block** at the top of long dialogs/forms listing all problems, each focusing the offending field ("There is a problem" pattern) | Materially reduces failure loops on multi-field forms like `Thresholds.vue` and `Profile.vue` | MEDIUM | GOV.UK-proven; pair with focus management |
| ERR-09 | **Recovery-action buttons inside errors** — duplicate threshold → `打开编辑`; missing profile → `去完善档案`; rate limited → `稍后重试` countdown | Turns dead ends into next steps; matches NN/g "constructive advice" | MEDIUM | Requires typed error codes surfaced to the client, not just strings |
| ERR-10 | **Error states as designed components** (`ErrorState.vue`: icon, cause, next step, retry) instead of bare toasts for page-level load failures | Fixes blank pages when list fetches fail | LOW | Reusable across Alerts, Risk, Goals, Reminders |
| ERR-11 | **Repeated-error insight**: if the same validation fires repeatedly, log a client event and surface a "填写提示" at the field | NN/g: 3+ identical errors indicates a design problem, not user error | LOW–MEDIUM | Analytics endpoint + hint content; can ship as a local counter first |
| ERR-12 | **Reuse the metric domain explanation style in errors** — threshold and profile validation reuse the range-aware, friendly copy of `evaluateMetricValue` | Consistent voice across entry, thresholds, and profile | LOW | Copy/pattern reuse; `metricValidation.ts` templates are the model |

### Anti-Features

| ID | Anti-Feature | Why Requested | Why Problematic | Alternative |
|----|--------------|---------------|-----------------|-------------|
| AF-ERR-1 | **Toast-only errors** for form validation | One-line implementation | Toasts disappear, cannot be acted on, and are easily duplicated | ERR-03 inline + ERR-08 summary |
| AF-ERR-2 | Collapse every failure into **"操作失败，请稍后重试"** | Simple and "safe" | Removes all actionable information; users retry blindly | ERR-04 specific copy + ERR-09 recovery |
| AF-ERR-3 | **Modal dialogs** for validation errors | High visibility | Disruptive; forces remembering instructions after dismissal (NN/g warns against modals for field errors) | Inline messages |
| AF-ERR-4 | **Auto-dismiss errors containing instructions** after 2s | "Less clutter" | Users cannot finish reading; instructions are lost | Persistent inline / sticky summary |
| AF-ERR-5 | Surface raw backend strings **including English field names or stack traces** | "Fastest integration" | Leaks internals, breaks trust, and is the current defect | ERR-02 + `GlobalExceptionHandler` mapping |
| AF-ERR-6 | **Disable save silently** when the form is invalid | "Prevent bad data" | No explanation for why the button does nothing; users are stuck | Keep save enabled, validate on click, show errors (the metric dialog correctly blocks only hard-limit cases) |

---

## Feature Dependencies

```
ERR-01 (single error presentation)  ──requires──>  utils/request.ts change
    ├──enhances──> ERR-03 (field-level binding)
    ├──enhances──> ERR-09 (recovery actions)
    └──enhances──> ERR-08 (error summary)

THR-01 (metadata-driven picker) ──requires──> useMetricMetaStore (EXISTS)
THR-02 (recommended values)     ──requires──> THR-01
                                ──requires──> default-value source
                                              ├─ PANIC_LIMITS (EXISTS)
                                              ├─ registry normal ranges (EXISTS)
                                              └─ alert_rule seeds (EXISTS in schema, NO API)  ← gap
THR-04 (zone preview)           ──requires──> THR-01, metric definitions (EXISTS)
THR-05 (guardrails)             ──requires──> backend cross-field validation  ← does not exist
THR-09 (presets)                ──requires──> THR-02

PRF-01 (completeness score)     ──requires──> preventionApi.getProfile (EXISTS)
PRF-05 (disclose defaults)      ──requires──> RiskAssessmentServiceImpl + ProfileScorer changes
PRF-06 (estimated badge)        ──requires──> PRF-05
PRF-07 (dashboard prompt)       ──requires──> PRF-01
PRF-10 (just-in-time prompt)    ──requires──> PRF-02 (field → feature map)
PRF-11 (impact receipt)         ──requires──> PRF-05, score cache (EXISTS)

ERR-02 (Chinese messages) ──enhances──> THR-08 (duplicate metric copy)
                          ──enhances──> PRF-08 (gate explanation)
```

### Dependency notes

- **THR-02 needs a default-value source that is not yet exposed.** `alert_rule` is seeded with sensible defaults (heartRate 100/120/50/40; systolic 140/160/90/80; diastolic 90/100/60/50; glucose 7.0/11.1/3.9/3.0; bmi 28/35/18.5/16; temperature 37.5/38.5/36.0/35.5) but has no controller. Either add a read-only `GET /api/alert-rules` or derive presets in the frontend from `normalMin/Max` + `PANIC_LIMITS`. The backend `IntelligentAlertService` already falls back to these rules when no user threshold exists, so exposing them is consistent.
- **THR-05 requires backend work.** The alert engine compares all four fields through `ThresholdEvaluator` / `ThresholdRangeChecker`; an inverted or impossible set produces silent non-firing alerts. Frontend-only validation is insufficient.
- **PRF-05 must be additive.** `HealthProfileVO` and `RiskAssessmentVO` are consumed by existing pages and tests; new fields must default to null/absent so nothing breaks.
- **ERR-01 must land before page migrations**, otherwise duplicate-toast removal will be re-broken piecemeal. It is the lowest-numbered foundation in the error workstream.

---

## Concrete UX Patterns (planner-ready)

Specific interaction shapes the research supports — not "improve UX" placeholders.

1. **Recommended-first input** (Apple/Fitbit/Withings pattern): the form opens with values populated and a `推荐` tag plus `恢复推荐值`; the user edits rather than authors.
2. **Zone band preview** (Fitbit heart-rate-zone pattern): a horizontal bar with five labeled segments and the four user values marked; updates on every keystroke.
3. **Preset selector over raw numbers** (Fitbit zones, Apple notification presets): 宽松 / 标准 / 严格 radio group that writes the four values; advanced users can still edit.
4. **Checklist with completion states** (Apple Health Checklist pattern): grouped rows each with ✓ / ○ and a `去填写` link; the card dismisses at 100%.
5. **Just-in-time prompt** (Apple HIG "request only when needed", NN/g pull revelations): a small inline dialog asking only the one missing field at the moment a feature needs it, with a short purpose sentence.
6. **Estimated-value badge** (health-app convention): `基于不完整档案估算` chip next to any computed score/risk, linking to the profile.
7. **Inline error + summary** (GOV.UK pattern): red message under the field, matching wording repeated in a top-of-form summary, focus moved to the summary on submit failure, user input preserved.
8. **Recovery action in the error** (NN/g constructive advice): typed error → next-step button, never a dead-end toast.
9. **Empty state that teaches** (NN/g): "当前使用系统默认规则" + the defaults + a direct CTA, not an empty table.

---

## MVP Definition

### Launch With (v1)

- [ ] **THR-01** metadata-driven metric picker — removes the hardcoded 6-option limitation
- [ ] **THR-02** recommended values pre-filled with a `推荐` tag
- [ ] **THR-03** plain-language field definitions + units
- [ ] **THR-05** ordering/physiological guardrails (frontend + backend)
- [ ] **THR-06** generated confirmation sentence
- [ ] **THR-07** teaching empty state
- [ ] **PRF-01** completeness score on Profile
- [ ] **PRF-02** "why we ask" helpers
- [ ] **PRF-03** required/recommended/optional labeling
- [ ] **PRF-04** missing-data impact banner
- [ ] **PRF-05** backend discloses applied defaults
- [ ] **PRF-06** estimated badge on Risk and Score
- [ ] **PRF-07** dismissible dashboard prompt with snooze
- [ ] **ERR-01** single error presentation (foundation)
- [ ] **ERR-02** Chinese, human user-facing messages
- [ ] **ERR-03** inline field errors from backend `errors[]`
- [ ] **ERR-04** specific copy replacing "操作失败"
- [ ] **ERR-06** duplicate suppression

### Add After Validation (v1.x)

- [ ] **THR-04** live zone preview bar — add when users report uncertainty about ordering
- [ ] **THR-08** duplicate-threshold recovery action — add with typed error codes
- [ ] **THR-09** preset mode — add after THR-02 telemetry shows users leave defaults unchanged
- [ ] **PRF-08** feature-gate explanation — add when PRF-05 data confirms estimation frequency
- [ ] **PRF-09** checklist card — add when completeness telemetry shows a long tail below 100%
- [ ] **PRF-10** just-in-time field prompt — add after PRF-07 shows low prompt conversion
- [ ] **ERR-07** distinct network/auth/validation presentation
- [ ] **ERR-08** error summary block in long dialogs
- [ ] **ERR-09** recovery-action buttons
- [ ] **ERR-10** `ErrorState.vue` for page-level failures

### Future Consideration (v2+)

- [ ] **THR-10** condition-aware presets — needs clinical sign-off and a preset catalog
- [ ] **THR-11 / THR-12** explanatory disclosure and reset action — low urgency
- [ ] **PRF-11** before/after impact receipt — requires cached pre-completion values
- [ ] **PRF-12** completeness-aware AI advice — coordinate with AI prompt work
- [ ] **ERR-11** repeated-error insight — needs analytics plumbing
- [ ] **ERR-12** shared error copy patterns — formalize once ERR-04 lands

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| THR-01 metadata-driven picker | HIGH | LOW | P1 |
| THR-02 recommended values | HIGH | LOW–MEDIUM | P1 |
| THR-03 field definitions | HIGH | LOW | P1 |
| THR-05 guardrails | HIGH | LOW–MEDIUM | P1 |
| THR-06 confirmation sentence | MEDIUM | LOW | P1 |
| THR-07 teaching empty state | HIGH | LOW–MEDIUM | P1 |
| THR-04 zone preview | MEDIUM | MEDIUM | P2 |
| THR-08 duplicate recovery | MEDIUM | LOW | P2 |
| THR-09 preset mode | HIGH | MEDIUM | P2 |
| THR-10 condition presets | MEDIUM | HIGH | P3 |
| PRF-01 completeness score | HIGH | LOW–MEDIUM | P1 |
| PRF-02 why-we-ask | HIGH | LOW | P1 |
| PRF-03 criticality labels | MEDIUM | LOW | P1 |
| PRF-04 impact banner | HIGH | LOW–MEDIUM | P1 |
| PRF-05 disclose defaults | HIGH | MEDIUM | P1 |
| PRF-06 estimated badge | HIGH | MEDIUM | P1 |
| PRF-07 dashboard prompt | HIGH | MEDIUM | P1 |
| PRF-08 gate explanation | MEDIUM | MEDIUM | P2 |
| PRF-09 checklist card | MEDIUM | LOW–MEDIUM | P2 |
| PRF-10 just-in-time prompt | HIGH | MEDIUM | P2 |
| PRF-11 impact receipt | MEDIUM | MEDIUM–HIGH | P3 |
| ERR-01 single presentation | HIGH | MEDIUM | P1 |
| ERR-02 Chinese messages | HIGH | MEDIUM | P1 |
| ERR-03 field-level inline | HIGH | MEDIUM | P1 |
| ERR-04 specific copy | HIGH | LOW | P1 |
| ERR-06 dedupe | MEDIUM | LOW | P1 |
| ERR-07 distinct error classes | MEDIUM | LOW–MEDIUM | P2 |
| ERR-08 error summary | MEDIUM | MEDIUM | P2 |
| ERR-09 recovery actions | HIGH | MEDIUM | P2 |
| ERR-10 ErrorState component | MEDIUM | LOW | P2 |

**Priority key:** P1 = launch this milestone; P2 = next iteration; P3 = future.

---

## Competitor Feature Analysis

| Capability | Apple Health / Watch | Fitbit / Google | MyFitnessPal | Bearable | HHS approach |
|------------|----------------------|-----------------|--------------|----------|--------------|
| Threshold setup | Heart-rate notifications ship with defaults and an explainer; users adjust from a recommended baseline | Heart-rate zones derived from age (max HR) and presented as named zones, not raw boundaries | Signup collects height/weight/age/sex because BMR needs them; safe-goal guardrails | Users define custom metrics and goals from scratch; no age/sex by design | THR-02 recommended values + THR-03 definitions + THR-09 presets; four-tier model retained |
| Profile completeness | Health Checklist surfaces setup tasks with completion states; details editable in one place | Profile collected during device setup | Profile collected at signup with a stated purpose | Deliberately minimizes personal data; lets users hide metrics | PRF-01 score + PRF-09 checklist + PRF-02 purpose copy |
| Missing-data behavior | Personalization degrades but the app stays usable | Uses defaults; goal suggestions are pre-filled | Explains what the data is for | N/A (data minimization) | PRF-05 disclose defaults + PRF-06 estimated badge + PRF-07 prompt |
| Error copy | Dynamic validation, feedback as soon as a problem is detected, input preserved (HIG) | — | — | — | ERR-02/03/04 + ERR-08 inline+summary |
| Error presentation | Inline field-level | — | — | — | GOV.UK pattern: inline + summary, never toast-only |

---

## Sources

### Directly fetched, HIGH confidence

- Apple Human Interface Guidelines — **Onboarding** (`developer.apple.com/design/human-interface-guidelines/onboarding.md`): postpone nonessential setup, provide reasonable defaults, context-specific tips over one long flow.
- Apple Human Interface Guidelines — **Privacy** (`.../privacy.md`): request permission only when the feature needs it; purpose strings must be specific active sentences with a stated benefit.
- Apple Human Interface Guidelines — **Entering data** (`.../entering-data.md`): pre-gather data, prefill reasonable defaults, dynamically validate and give feedback as soon as a problem is detected, make required data understood before proceeding.
- GOV.UK Design System — **Error message**: explain what went wrong and how to fix it; plain language; echo the label; inline plus summary; never wipe the entry; avoid "invalid"/"please"/"sorry".
- GOV.UK Design System — **Error summary**: "There is a problem" heading, linked entries, matching wording, focus moved to the summary.
- GOV.UK Design System — **Check answers**: shows completeness before submit, `Change` links, "Not provided" for optional blanks.
- Nielsen Norman Group — **Errors in forms**: validate on blur, inline below the field, explicit/actionable/polite, avoid tooltips and modals, 3+ identical errors indicates a design problem.
- Nielsen Norman Group — **Empty-state interface design**: never leave blank; explain what would appear and how to populate it; link directly to the action; avoid misleading "no records" while loading.
- Nielsen Norman Group — **Progressive disclosure**: two levels max; defer secondary material; label the control clearly.
- Nielsen Norman Group — **Required fields**: per-field marking beats an upfront "all fields required" note.

### Fetched, MEDIUM confidence (partial content)

- MyFitnessPal official FAQ — signup collects height, weight, age, sex for BMR; safe weight-goal guardrails. Onboarding order and progress indicators not stated on the page.
- Bearable official site — deliberately does not ask for name/age/sex; custom metrics; reminders for re-engagement. Full onboarding flow not available.

### Attempted but unavailable (LOW confidence — do not over-claim)

- Apple Health "Health Checklist" and "edit health details" user-guide pages (JS-gated; only CSS returned).
- Apple Watch high/low heart-rate notification thresholds pages (JS-gated).
- Fitbit heart-rate-zone and activity-goal help pages (404 / redirect).
- Samsung Health goal/threshold support pages (404 / redirect).
- Withings, Omron Connect, Oura profile pages (404 / redirect).

### Verified HHS codebase facts (HIGH confidence)

- `hhs-frontend-v2/src/views/health/Thresholds.vue` — 6 hardcoded options, 4 unlabeled numeric inputs, no cross-field rules.
- `hhs-frontend-v2/src/constants/metricMeta.ts` + `hhs-backend/.../HealthMetricMetaRegistry.java` — 23 metrics with normal/hard ranges, units, guidance.
- `hhs-backend/src/main/resources/sql/schema.sql` lines 514–521 — seeded `alert_rule` defaults for 7 metrics; no controller exposes them.
- `hhs-backend/.../RiskAssessmentServiceImpl.java` — age default 35, BMI default 22, fixed scores 30/25/25 when profile absent.
- `hhs-backend/.../ProfileScorer.java` — neutral 70; −10 gender/birthDate, −15 height/weight, −10 BMI, −15 lifestyle.
- `hhs-backend/.../UserThresholdRequest.java` — only `@DecimalMin(0)`; no ordering constraints.
- `hhs-backend/.../GlobalExceptionHandler.java` — localizes `ErrorCode`, but DTO messages and several literals remain English.
- `hhs-frontend-v2/src/utils/request.ts` — global toast on every non-200; 84 view-level `ElMessage.error` sites create duplicates.
- `hhs-frontend-v2/src/views/prevention/Profile.vue` — only gender + birthDate required; all fields presented identically.
- `hhs-frontend-v2/src/App.vue` — `useMetricMetaStore().fetchMetadata()` is already globally preloaded.

---

*Feature research for: HHS v1.1+ guided metric input, threshold configuration, profile completeness, and friendly validation*
*Researched: 2026-11-05*
*Status: ready for roadmap consumption; P1 items are directly implementable against existing HHS APIs and stores*
