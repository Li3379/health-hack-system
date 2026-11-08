---
phase: 12-threshold-wizard
plan: 05
subsystem: hhs-frontend-v2 metadata contract + threshold validation
tags: [thresholds, metadata, wizard-contract, pure-functions, validation]
requires:
  - 12-04 (backend @ValidThresholdRange + alertable/recommended metadata on the wire)
provides:
  - RecommendedThresholdVO interface in types/api.ts
  - MetricDefinitionVO.alertable / MetricDefinitionVO.recommended (required fields)
  - alertable + recommended on all 23 FALLBACK_METRIC_META entries (6 alertable, seed-mirrored)
  - useMetricMetaStore().alertableHealthMetrics / .alertableWellnessMetrics computeds
  - src/utils/thresholdValidation.ts exporting ThresholdDraft, PairViolation, validateThresholdOrder(draft, def?), hasAnyBoundary(draft)
  - test suites for all of the above
affects:
  - 12-06 ThresholdWizard.vue (consumes store computeds + validateThresholdOrder + hasAnyBoundary)
  - 12-07 ThresholdZoneBar.vue (consumes ThresholdDraft null semantics)
  - 12-08 Thresholds.vue rewrite (consumes store computeds)
tech-stack:
  added: []
  patterns:
    - pure-function validation module mirroring a backend class-level constraint (non-authoritative UX mirror, D-09)
    - store computed filtering pattern for backend-authoritative flags (no frontend whitelist, D-03)
key-files:
  created:
    - hhs-frontend-v2/src/utils/thresholdValidation.ts
    - hhs-frontend-v2/src/__tests__/utils/thresholdValidation.spec.ts
  modified:
    - hhs-frontend-v2/src/types/api.ts
    - hhs-frontend-v2/src/constants/metricMeta.ts
    - hhs-frontend-v2/src/stores/metricMeta.ts
    - hhs-frontend-v2/src/__tests__/stores/metricMeta.spec.ts
    - hhs-frontend-v2/src/__tests__/utils/errorMessages.spec.ts
    - hhs-frontend-v2/src/__tests__/utils/format.spec.ts
decisions:
  - hard-limit violations carry a single-element `fields` array (one boundary per violation, mirroring the backend's per-field violations); the plan's "two-element fields array" acceptance line applies to the three ordering violations, whose type the plan itself declared as `Array<keyof ThresholdDraft>` rather than a fixed tuple
  - recommended values hardcoded in the fallback mirror the alert_rule seeds (not normalMin/normalMax derivation) so offline-first paint shows exactly what the backend would send (D-06 seed source)
  - non-alertable entries get `recommended: {}` (no fabricated values), matching D-19's "non-null only when alertable"
  - format.spec.ts dynamic-metric fixture extended with the now-required fields rather than weakening the type to optional (no optional-field escape hatch per must-have truth 1)
metrics:
  duration: ~10 min
  completed: 2026-11-08
status: complete
actuals:
  tokens: 6688        # chars/4 over the realized diff (26750 diff chars), vs estimate 52000
  tasks: 3
  commits: 3          # measured: git rev-list --count b87c0eb..HEAD
  plan_head_before: b87c0eb236a9f04bda80570432f5be087dd35ed9
requirements-completed:
  - THR-05 (frontend pure-function interception portion — wizard wiring/disabled Save lands in 12-06/12-08)
  - THR-01 + THR-02 (frontend-contract portion — alertable + recommended types/constants/store; wizard UI lands in 12-06/12-07/12-08)
patterns-established:
  - backend-authoritative boolean flags ride the metadata VO and are mirrored offline by seed-mirrored fallback constants asserted by set-equality tests (T-12-17)
  - UX mirrors of backend validators are pure modules with zero framework imports, tested for exact message strings and null semantics
---

# Phase 12 Plan 05: Frontend Threshold Contract — Metadata + Pure Validation Summary

**One-liner:** Typed metadata contract (alertable + seed-mirrored recommended on all 23 metrics, grouped store computeds) plus a tested pure `thresholdValidation` module mirroring the backend's ordering and hard-limit rules.

## What Was Built

### Task 1 — Types + fallback constants + store computeds (tracer) — `79a5c79`
- `types/api.ts`: new `RecommendedThresholdVO` (four optional `number | null` boundaries); `MetricDefinitionVO` gains required `alertable: boolean` and `recommended: RecommendedThresholdVO` (the backend always sends both since 12-04).
- `constants/metricMeta.ts`: all 23 `FALLBACK_METRIC_META` entries carry both fields. Exactly six are `alertable: true` with recommended values mirroring the `alert_rule` seeds — heartRate (50/100/40/120), systolicBP (90/140/80/160), diastolicBP (60/90/50/100), bloodGlucose (3.9/7.0/3.0/11.1), bmi (18.5/28/16/35), bodyTemperature (36.0/37.5/35.5/38.5). Every recommended boundary verified inside its entry's hardMin/hardMax (systolicBP 160 ≤ 300, bodyTemperature 35.5 ≥ 34.0, bmi 16 ≥ 5.0). The other 17 entries are `alertable: false, recommended: {}`.
- `stores/metricMeta.ts`: `alertableHealthMetrics` / `alertableWellnessMetrics` computeds filter the grouped arrays on `alertable` and are exported from the store.
- `metricMeta.spec.ts`: new tests assert the six canonical keys by set equality (drift guard T-12-17), `alertableWellnessMetrics.length === 0`, `getDefinition('glucose')?.alertable === true` (alias resolution), seed-mirrored heartRate recommendation, and `weight`/`bloodOxygen` not alertable (D-19/D-20). Existing 23/16/7 assertions untouched.

⚡ Tracer gate: store spec + build re-run end-to-end after the task — green before any expansion.

### Task 2 — thresholdValidation.ts pure order + hard-limit mirror — `0920730`
- Exports exactly `ThresholdDraft`, `PairViolation`, `validateThresholdOrder(draft, definition?)`, `hasAnyBoundary(draft)`. Single import is a type-only `MetricDefinitionVO` — no `vue`/`pinia`/`element-plus` (usable from tests and the wizard alike).
- Three ordered adjacency pairs, one violation per broken pair, each naming BOTH fields so the wizard marks both inputs; messages are the exact UI-SPEC Contract 3 strings (`严重低值必须小于警告低值，请调整其中一个` / `警告低值必须小于警告高值，请调整其中一个` / `警告高值必须小于严重高值，请调整其中一个`).
- D-10 semantics: a pair is skipped when either side is `null`/`undefined` — null is unbounded and never itself an error. Pair violates when low ≥ high (strict ordering coexists with the inclusive-bound runtime classifier per 12-RESEARCH).
- D-11: with a definition, each non-null boundary outside inclusive `[hardMin, hardMax]` gains a hard-limit violation whose message interpolates only definition values: `该值超出该指标的生理极限范围（{hardMin} ~ {hardMax} {unit}）` (T-12-19: no user text in templates).
- Spec: 13 cases including two-broken-pairs → two violations, null-skip, inclusive bounds at exactly hardMin/hardMax, and stacked order + hard-limit violations.

### Task 3 — Threshold field-label pins — `87f4216`
- `errorMessages.ts` already mapped the four labels correctly (unchanged, verified). `errorMessages.spec.ts` gains a dedicated block pinning `warningHigh → 警告高值`, `warningLow → 警告低值`, `criticalHigh → 严重高值`, `criticalLow → 严重低值`; the 17-key count, `metricKey`/`warningHigh`/`notes`, and banned-copy assertions are intact.

## Coverage — must-have truths

| # | Must-have truth | Verification | Status |
|---|-----------------|--------------|--------|
| 1 | MetricDefinitionVO gains required `alertable`/`recommended`; all 23 fallback entries supply both; vue-tsc passes with no optional escape hatch | `npm run build` green (vue-tsc -b) after Task 1 and again at final verification; `src/types/api.ts` fields non-optional | verified |
| 2 | Exactly heartRate, systolicBP, diastolicBP, bloodGlucose, bodyTemperature, bmi are alertable true (D-19) | `grep -c "alertable: true" constants/metricMeta.ts` = 6; `metricMeta.spec.ts` six-key set-equality test; backend mirror test `MetricMetadataControllerTest` (12-04) asserts same set | verified |
| 3 | Store exposes `alertableHealthMetrics`/`alertableWellnessMetrics`; wellness array empty on fallback data | `metricMeta.spec.ts`: lengths 6 / 0; grep count 4 in store | verified |
| 4 | One violation per broken adjacent pair, both fields named, both sides non-null | `thresholdValidation.spec.ts`: three single-pair cases + two-broken-pairs case (`toHaveLength(2)`, fields pairs) | verified |
| 5 | No violation for a pair where either side is null — null = unbounded, never an error | `thresholdValidation.spec.ts`: only-criticalHigh case; null-boundary hard-limit case | verified |
| 6 | `hasAnyBoundary` false only when all four boundaries are null or undefined | `thresholdValidation.spec.ts` `hasAnyBoundary` describe (all-null, mixed null/undefined, one-set variants) | verified |
| 7 | The three messages are the exact UI-SPEC strings | `thresholdValidation.spec.ts`: three `toBe('…请调整其中一个')` character-exact assertions | verified |
| 8 | Hard-limit violations reported when a definition is supplied (frontend rejects same inputs as backend) | `thresholdValidation.spec.ts` D-11 describe (above hardMax, below hardMin, inclusive-at-bounds, stacked with order violations) | verified |
| 9 | Partially filled draft valid; null boundaries never in violation lists; only all-four-null rejected by `hasAnyBoundary` | `thresholdValidation.spec.ts`: only-criticalHigh + partial `hasAnyBoundary` cases | verified |

## Verification (plan `<verification>` block)

- `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/utils/thresholdValidation.spec.ts src/__tests__/stores/metricMeta.spec.ts src/__tests__/utils/errorMessages.spec.ts` — green (13 + 9 + 12)
- `npm --prefix hhs-frontend-v2 run test:run` (full suite) — 26 files / 235 tests green
- `npm --prefix hhs-frontend-v2 run build` — green (vue-tsc validates required fields on all 23 entries)
- `npm --prefix hhs-frontend-v2 run lint:check` — exit 0 (0 errors; the 3 remaining warnings are pre-existing on lines this plan did not author: `types/api.ts:9/175` any, `stores/metricMeta.ts:90` console)
- Acceptance greps: `alertable` = 23, `alertable: true` = 6 (exactly), `recommended` = 23, store computeds = 4, spec `it(` blocks = 13

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Extended `format.spec.ts` dynamic-metric fixture with the required new fields**
- **Found during:** Task 1 (acceptance build)
- **Issue:** Making `alertable`/`recommended` required on `MetricDefinitionVO` left a `MetricDefinitionVO` literal in `format.spec.ts:157` without them — `vue-tsc` build error TS2739 (tsconfig includes `src/**` so test fixtures are type-checked)
- **Fix:** added `alertable: false, recommended: {}` to the fixture (type unchanged — no optional escape hatch)
- **Files modified:** hhs-frontend-v2/src/__tests__/utils/format.spec.ts
- **Commit:** 79a5c79

**2. [Rule 1 - Bug] `isUnset` changed from boolean helper to type predicate**
- **Found during:** Task 2 (acceptance build)
- **Issue:** TS18049 — control-flow analysis cannot narrow through a plain boolean helper, so `low >= high` errored under `strict`
- **Fix:** `function isUnset(v): v is null | undefined` type predicate
- **Files modified:** hhs-frontend-v2/src/utils/thresholdValidation.ts
- **Commit:** 0920730

### Interpretation note (not a deviation)
The acceptance line "Every violation carries a two-element `fields` array" is applied to the three ordering violations (tested). Hard-limit violations carry a single-element `fields` array naming the one offending boundary — the plan's own `PairViolation` type is `Array<keyof ThresholdDraft>` (not a tuple), and hard limits are per-boundary, mirroring the backend validator's per-field violations.

## Known Stubs

None. Every exported surface is fully wired and tested; no placeholder data paths.

## Threat Flags

None — no new network endpoints, auth paths, file access patterns, or schema changes. T-12-17 mitigated (set-equality store test), T-12-19 mitigated (hard-limit message interpolates only numeric definition values), T-12-18 accepted (module is explicitly non-authoritative; backend `@ValidThresholdRange` from 12-04 is the authority), T-12-SC respected (zero npm installs; `@vueuse/core` not imported).

## Self-Check: PASSED
