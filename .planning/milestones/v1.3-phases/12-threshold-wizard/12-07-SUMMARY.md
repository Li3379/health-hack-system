---
phase: 12-threshold-wizard
plan: 07
subsystem: hhs-frontend-v2/health-thresholds-ui
tags: [threshold-wizard, vue3, element-plus, thr01, thr02, thr03, thr05, thr06, d09, d12]
status: complete
requires:
  - 12-04 (UserThresholdRequest @ValidThresholdRange backend gate; recommended values in MetricDefinitionVO)
  - 12-05 (thresholdValidation.ts pure validator; useFormServerErrors + FormErrorSummary Phase 11 chain)
  - 12-06 (ThresholdZoneBar five-band preview component)
provides:
  - ThresholdWizard.vue — self-contained three-step ElSteps dialog (props: modelValue, editing; emits: update:modelValue, saved, error)
  - Internal methods handleMetricSelect / restoreRecommended / handleBeforeClose / goToStep / handleSubmit for 12-08 integration
  - src/__tests__/views/ThresholdWizard.spec.ts — 13-case component contract suite
affects:
  - 12-08 (Thresholds.vue page rewrite consumes <ThresholdWizard> and its emits)
tech-stack:
  added: []
  patterns:
    - popper-class scoped styling for teleported dropdown panels (34px items, 264px panel)
    - defineExpose + typed WizardVM interface for component tests without `as any`
    - validation-kind rejection navigates back to the fields step so GOV.UK summary is visible
key-files:
  created:
    - hhs-frontend-v2/src/views/health/components/ThresholdWizard.vue
    - hhs-frontend-v2/src/__tests__/views/ThresholdWizard.spec.ts
  modified: []
key-decisions:
  - "userId is read from the auth store inside the wizard (authStore.user?.id), not added as a prop: the plan's artifact contract fixes Props at modelValue+editing, and T-12-26's operative requirement is only that the user never types it and the backend re-derives ownership from the token"
  - "On a validation-kind save rejection the wizard returns to step 2 (goToStep(1)) so the summary block and both inline errors are actually visible — submitting from step 3 would otherwise render the failure nowhere (must-have truth: the wizard shows the summary plus inline errors)"
  - "Recommend badge guarded by non-null equality: a field whose recommended value is null never shows 推荐, avoiding a badge over an empty input"
  - "The 推荐 badge and field labels are self-drawn spans (ElTag forbidden — theme.css locks .el-tag to 11px/500 !important); hints use --text-2 not --text-3 (3.97:1 in light)"
  - "Dropdown panel styling goes through popper-class 'threshold-wizard-options' because the select popper teleports to body where scoped styles cannot reach"
patterns-established:
  - "Step-transition focus rule via plain DOM refs (wrapper.querySelector('input').focus()) so it works identically with real Element Plus and test stubs"
  - "Component-test stub convention extended from ErrorLayerSample.spec.ts: bare boolean attrs arrive as '' in type-less stubs, so link buttons are distinguished via link !== ''"
  - "Confirmation sentence built as a {text, strong}[] segment computed rendered through interpolation — never v-html (T-12-23)"
requirements-completed:
  - "THR-03 (four plain-Chinese explanations with unit interpolation — spec case 4, exact strings)"
  - "THR-06 (confirmation sentence omits null clauses, degrades all-null — spec cases 6/7)"
  - "THR-01 wizard-portion (6 alertable options, grouped, 显示名 (单位), no 血氧/体重 — spec cases 1/2; page-level empty state remains 12-08)"
  - "THR-02 wizard-portion (recommended prefill + badges + restore — spec cases 3/11; page-level presentation remains 12-08)"
  - "THR-05 wizard-portion (frontend order check disables nav + dual inline errors + summary, zero toast — spec cases 5/13; backend @ValidThresholdRange gate was 12-04, its validator tests are not this plan's scope)"
actuals:
  tokens: 10918
  tasks: 3
  commits: 5
  plan_head_before: 0ca4f319ba826f972a19f201928f866ae79d4d59
  duration_minutes: 25
---

# Phase 12 Plan 07: ThresholdWizard Three-Step Dialog Summary

Three-step guided threshold dialog (选择指标 → 设置阈值 → 确认保存): a metadata-driven alertable-only picker that pre-fills backend recommended values with 推荐 badges and a restore action, four self-explained inputs bound to metric precision/step/hard limits with a live zone bar, live order validation with dual inline errors + GOV.UK summary and zero toasts, a natural-language confirmation sentence omitting null boundaries, edit mode opening on the locked-metric step, and an unsaved-changes close guard — pinned by a 13-case component spec.

## What Was Built

- **Dialog shell** (Contract 1): ElDialog 760px, `:close-on-click-modal="false"`, `destroy-on-close`, `:before-close` unsaved guard sharing `dialog.unsavedConfirm` between X/Esc/放弃设置 with a zero-toast cancel branch; ElSteps with `finish-status="finish"`, Contract 1 CSS verbatim (step title/weight/color normalization, `is-skipped` dimming), body scrolls vertically while header/footer stay pinned, min-height 360px prevents step-switch height jumps.
- **Step 1 picker** (Contract 2): filterable el-select over `store.alertableHealthMetrics` / `alertableWellnessMetrics`, option text exactly `显示名 (单位)` with half-width parens, wellness group hidden when empty, `暂无可配置的告警指标` inline state + disabled 下一步 when nothing is alertable, no loading skeleton (store is synchronously available from fallback; the component never calls fetchMetadata).
- **Step 2 fields** (Contract 3): four explicit `.threshold-field` blocks in order 严重高值/警告高值/警告低值/严重低值 — self-drawn 600-weight labels, `recommend-badge` spans, el-input-number bound to `meta.hardMin/hardMax/precision/step` (never the hardcoded precision 2), exact hint sentences with `（unit）` interpolation, mandatory CSS block incl. `--text-2` hints and el-form-item margin normalization; ThresholdZoneBar under the fields; inputs disabled with hints visible before a metric is chosen.
- **Live validation** (D-09/D-10/D-12): `clientViolations` via `validateThresholdOrder(draft, meta)`, merged map spreads the same message onto BOTH offending fields and mixes in server `fieldErrors`; FormErrorSummary at the top of step 2 with `fieldLabel` labels and focus-field forwarding; primary disabled on violations or all-null draft; the draft object is never mutated by a validation outcome.
- **Submit** (Contract 8 / T-12-24/26): `reset()` then create/update via thresholdApi with payload `{ userId: authStore.user?.id, metricKey: selectedKey, four boundaries }`; validation-kind rejection → `apply()` + back to step 2, dialog open, values intact, zero toast; any other rejection → `emit('error', raw)` so the parent's single showError exit stays the only toast; saving latch + `:loading` prevents duplicate submission.
- **Step 3** (Contract 5): `confirmationSentence` computed builds only non-null clauses (当 {displayName} ≥ warningHigh → ≥ criticalHigh → ≤ warningLow → ≤ criticalLow), numbers in `<strong>` via segment interpolation (no v-html), all-null degrades to 尚未设置任何有效阈值, container is `role="status"` + `tabindex="-1"` with accent-cool focus ring.
- **Edit mode** (D-02): opens on step 2 with the metric locked (picker disabled on return), first step dimmed via `is-skipped` and titled 指标已锁定, draft from the existing row.
- **Focus rule** (Contract 1): after `await nextTick()` every step change focuses the new step's first control — picker input, first input-number input, confirmation container.

## Commits

- 19958dc feat(12-07): add ThresholdWizard three-step dialog shell with metadata picker and explained fields
- 286d08e feat(12-07): wire live threshold-order validation with GOV.UK summary and zero-toast submit
- a51f3bf test(12-07): add confirmation sentence computed and pin wizard contract in 10-case spec (THR-06)
- 3da6ff9 feat(12-07): reveal inline errors on step 2 after step-3 rejection; pin dropdown and submit paths
- fe90f1e fix(12-07): replace Array.prototype.at with index access for the project's TS lib target

## Verification (all green at plan end)

- `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/ThresholdWizard.spec.ts` — 13/13 passed (≥10 required)
- `npm --prefix hhs-frontend-v2 run test:run` — 28 files, 257/257 passed (includes ErrorLayerSample THR-08 cases 6-8 still green)
- `npm --prefix hhs-frontend-v2 run build` — exit 0 (vue-tsc + vite)
- `npm --prefix hhs-frontend-v2 run lint:check` — exit 0; both plan files lint warning-free
- Acceptance greps: `alertable*` refs 4, `el-tag` 0, `recommend-badge` 5, `dialog.unsavedConfirm` 1, `:precision="meta?.precision"` ×4 with no `:precision="2"`, four `:error` bindings, `showError|ElMessage.error` 0, `v-html` 0

## Must-Have Coverage

| # | Must-have truth | Verification ref | Status |
|---|-----------------|------------------|--------|
| 1 | Step 1 lists only alertable metrics, grouped, `显示名 (单位)` half-width parens | ThresholdWizard.spec.ts case 1 (+2) | verified |
| 2 | Empty wellness group not rendered at all | ThresholdWizard.spec.ts case 2 | verified |
| 3 | Selection writes four recommended values, badges, enables 下一步 | ThresholdWizard.spec.ts case 3 | verified |
| 4 | 恢复推荐值 restores all four after user edits | ThresholdWizard.spec.ts case 11 | verified |
| 5 | Four inputs each explained with unit, bound to precision/step/hardMin/hardMax | spec case 4 + template `:min/:max/:precision/:step` ×4 (build) | verified |
| 6 | Four explanations read exactly 高于此值…（unit）×4 | ThresholdWizard.spec.ts case 4 (`toEqual` exact) | verified |
| 7 | Broken adjacency disables 下一步/保存, marks both fields, renders FormErrorSummary, zero toasts | spec cases 5 + 13; `showError\|ElMessage.error` grep 0 | verified |
| 8 | No validation failure clears an input; draft never reset by outcome | spec cases 5 (draft intact) + 13 (draft intact after 400) | verified |
| 9 | Step 3 sentence only non-null clauses, numbers in strong, null clause omitted entirely | spec case 6 (clauses + `<strong` + count) | verified |
| 10 | All-null degrades to 尚未设置任何有效阈值 | spec case 7 | verified |
| 11 | 760px, no backdrop close, resets on open, asks 内容尚未保存，确定离开？ when dirty | template attrs (build) + spec case 9 (confirm called with exact copy) | verified |
| 12 | Switching steps moves focus to first control of new step | spec case 10 (HTMLElement.prototype.focus contexts) | verified |
| 13 | Edit opens step 2, metric locked, step 1 dimmed 指标已锁定 | spec case 8 | verified |
| 14 | Copy wraps naturally; body scrolls, footer pinned | `.threshold-wizard` flex + `__body` overflow CSS; visual confirmation listed in 12-VALIDATION Manual-Only | verified (static) |
| 15 | No loading skeleton — metadata synchronously from fallback | `fetchMetadata` count in component = 0 (grep) | verified (code) |
| 16 | Failed metadata fetch degrades silently, zero toasts | stores/metricMeta.spec.ts (pre-existing) + wizard never toasts | verified (suite) |
| 17 | Saving shows loading state and prevents duplicate submission | spec case 12 (latch + disabled while pending) | verified |
| 18 | No validation/server error clears an input; dialog stays open | spec case 13 | verified |
| 19 | Empty step never appears; wizard opens with content | v-if/v-else panel structure + `min-height: 360px` (code) | verified (code) |
| 20 | No metric yet: inputs disabled+empty, explanation text visible | spec case 7 prelude | verified |
| 21 | Summary block absent with no errors; leave-confirm absent with no input | spec case 3 (absent) + case 9 clean branch | verified |
| 22 | Partial draft allowed; null never in validation/summary; only all-null disables | spec case 6 (primary enabled after nulling one) + src/__tests__/utils/thresholdValidation.spec.ts (12-05) | verified |
| 23 | Confirmation is pure display, role=status, only state handling is null-omission/degradation | template `role="status" tabindex="-1"` + spec cases 6/7 | verified |
| 24 | Steps never clickable-jump; backwards only via 上一步 | no click handlers on el-steps (code) | verified (code) |
| 25 | Long option names ellipsized with full text in title attr; hints wrap | `:title` on options + popper CSS (code) | verified (static) |
| 26 | Sentence wraps on narrow dialogs; numbers bold at same font size | `<strong>` markup, no font-size override; spec case 6 strong assertion | verified |
| 27 | Duplicate-threshold prompt is a single toast (parent scope) | ErrorLayerSample.spec.ts cases 6-8 green in full suite | verified (suite) |
| 28 | Validation failure = summary+inline+no toast; business failure = exactly one toast via parent, dialog open, values intact | spec case 13 + `emit('error', raw)` path (code) | verified |
| 29 | Hints are long-form wrapping sentences, no tooltip-only fallback | hints always rendered; ElTooltip intentionally unused | verified (code) |
| 30 | el-input-number values scroll horizontally, never widen the column | EP input default behavior (code) | verified (EP default) |
| 31 | Option text nowrap ellipsis + title attr + fixed 34px items | `popper-class` CSS block (code) | verified (static) |
| 32 | Dropdown panel scrolls beyond 264px | `.threshold-wizard-options .el-select-dropdown__wrap { max-height: 264px }` | verified (static) |
| K1 | fieldErrors map lights the same inputs as client-side check | mergedErrors binding ×4 + spec case 13 (backend 400 lights input) | verified |
| K2 | Recommendations come from meta.recommended, never recomputed (D-06) | handleMetricSelect copies `store.getDefinition(key)?.recommended` (code) | verified (code) |
| K3 | ElTag not used for the badge | `el-tag` grep = 0; badge is `span.recommend-badge` (spec case 3) | verified |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical error handling] Validation rejection on step 3 now returns to step 2**
- **Found during:** Task 3 (case 13)
- **Issue:** Submit happens from step 3; a backend 400 applied inline errors to the step-2 panel, which is not rendered on step 3 — the failure would be invisible (no toast by design, no inline errors visible), violating the must-have truth "the wizard shows the summary plus inline errors".
- **Fix:** `handleSubmit` validation branch calls `goToStep(1)` after `apply(err)`, so the summary block + both inline errors are visible and focus lands on the first field per the step rule.
- **Files modified:** hhs-frontend-v2/src/views/health/components/ThresholdWizard.vue
- **Commit:** 3da6ff9

**2. [Rule 3 - Blocking] `Array.prototype.at` unavailable at the project's TS lib target**
- **Found during:** Final build (`vue-tsc` TS2550)
- **Fix:** replaced `.at(-1)` with explicit index access in the spec.
- **Commit:** fe90f1e

### Documented Interpretations

- **userId source:** Task 2 action says "userId supplied by the parent" while the plan's artifact contract fixes Props at `modelValue` + `editing`. Resolved by reading `useAuthStore()` inside the wizard — T-12-26's operative requirement (never user-typed, from the authenticated store, backend re-derives ownership from the token) is satisfied and 12-08's integration surface stays exactly as the artifacts list defines it.
- **Component length:** 704 lines vs the task's 400-line target. The four explicit field blocks (required verbatim by the acceptance greps) plus the mandatory Contract 1/3/5 CSS cannot fit 400 lines without extracting a new component, which the task explicitly forbids; CLAUDE.md's 800-line ceiling holds.
- **Contract 9 (button weight normalization in global theme.css) not implemented:** theme.css is outside `files_modified` for this plan; the page-level button rules belong to 12-08's page shell. Wizard buttons render with existing global styles; no functional impact.

## Auth Gates

None.

## Known Stubs

None — no placeholder text, no unwired data, no skipped tests; all `<verify>` commands ran green (13-case spec, 257-test full suite, build, lint).

## Self-Check: PASSED

- FOUND: hhs-frontend-v2/src/views/health/components/ThresholdWizard.vue
- FOUND: hhs-frontend-v2/src/__tests__/views/ThresholdWizard.spec.ts
- FOUND: commit 19958dc; FOUND: commit 286d08e; FOUND: commit a51f3bf; FOUND: commit 3da6ff9; FOUND: commit fe90f1e
- Commits measured from ledger `gsd-plan-head-before-12-07` (base 0ca4f31): 5
