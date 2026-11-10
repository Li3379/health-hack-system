---
phase: 14-error-rollout
plan: 03
subsystem: ui
tags: [error-handling, i18n-copy, field-labels, element-plus, vue3, vitest, form-validation, deduplication]

requires:
  - phase: 11-error-foundation
    provides: "the 18-key ERROR_MESSAGES dictionary, the 17-entry FIELD_LABELS table, the resolveMessage/fieldLabel resolution pair, and the useFormServerErrors composable this plan extends"
  - phase: 12-remaining-surfaces
    provides: "the FormErrorSummary component and the GOV.UK double-presentation wiring that consumes summaryItems"
  - phase: 14-error-rollout
    plan: 02
    provides: "the global .el-form-item.is-error border declaration — the visual anchor these labels sit inside"

provides:
  - "ERROR_MESSAGES at 46 keys (18 existing + 28 new) — the complete vocabulary every one of the 87 migration call sites in 14-05..14-13 draws from"
  - "FIELD_LABELS at 44 entries (17 + 22 contract + 5 mood) — no form in the rollout can render a raw camelCase key"
  - "UseFormServerErrorsOptions.labels — the precedence escape hatch for ambiguous field names (caller -> global -> empty string)"
  - "collectInvalidFieldMessages exported once from the composable module, consumed by both reference implementations, ready for the thirteen remaining forms"
  - "errorMessages.spec.ts / useFormServerErrors.spec.ts guards that make a future key deletion or label regression fail loudly"

affects: [14-04, 14-05, 14-06, 14-07, 14-08, 14-09, 14-10, 14-11, 14-12, 14-13, 14-14, all-87-migrations]

actuals:
  tokens: 3006
  tasks: 2
  commits: 2
  plan_head_before: 2a52eee22fb8d63735816a3b3cc1b64eb0ac584f

tech-stack:
  added: []
  patterns:
    - "Decide the vocabulary once, before the first migration wave: the key set and label set are fixed in one commit so no later wave edits the spec's count assertion again"
    - "Two-tier label resolution: a caller-supplied map beats a global table, and an empty label renders the backend message alone rather than falling back to the field name"
    - "Shared stateless helper exported at module level (not from the composable return) for logic consumed inside validate callbacks"

key-files:
  created: []
  modified:
    - hhs-frontend-v2/src/utils/errorMessages.ts
    - hhs-frontend-v2/src/composables/useFormServerErrors.ts
    - hhs-frontend-v2/src/views/prevention/Profile.vue
    - hhs-frontend-v2/src/views/prevention/Risk.vue
    - hhs-frontend-v2/src/__tests__/utils/errorMessages.spec.ts
    - hhs-frontend-v2/src/__tests__/composables/useFormServerErrors.spec.ts

key-decisions:
  - "The dictionary stops at 46, not 44: the contract's 26 keys plus mood.loadFailed and mood.deleteFailed. Research's other flagged gaps are resolved by reuse rather than addition (reminders/Index.vue:181 -> alert.updateFailed, Score.vue:457 -> score.loadFailed, Risk.vue:310,335 -> profile.*, DevicePlatformConfig.vue:355 -> device.saveFailed, OcrHistory -> ai.*), and each decision is written into a code comment so no later task re-opens it."
  - "ai.sendFailed is deliberately NOT added. Research's resolved open question 2 puts send failures in an in-place retry bubble, so a dictionary key would be a second vocabulary with zero consumers — the exact duplicate-vocabulary failure this plan exists to prevent."
  - "FIELD_LABELS stops at 44 = 17 + 22 + 5 mood, not the spec's 39. The five mood keys are load-bearing rather than optional polish: RecordMoodRequest constrains moodScore, energyLevel, stressLevel, sleepQuality (and entryDate) with @NotNull/@Min/@Max, so a mood 400 is reachable and would otherwise render `moodScore：心情评分不能为空` in the user's summary. notes already existed, so the batch is five and not six."
  - "description / startDate / endDate / note / the six device-config fields / avatar get no labels on purpose. Contract 4.5 rule 2 (render the backend message alone) is the correct fallback there because those backend messages are already complete Chinese sentences; a label would add noise. The omission is documented in a comment above the table so it reads as a decision, not an oversight."
  - "The dictionary count assertion keeps reading DICTIONARY_KEYS.length rather than a literal 46. The assertion's job is to catch an accidental key leaking in without a spec update; hardcoding 46 would duplicate the count and turn a guard into a maintenance chore. Only the labels assertion is a literal (44), because there is no array to derive it from."
  - "The labels override is wired at exactly one place — the summaryItems label computation. Inline errors keep showing only the backend message, which is the existing behaviour and the right division: the label is the summary's job, and duplicating it under every field would repeat the same word four times in a mood form."
  - "collectInvalidFieldMessages is a module-level export, not a composable return value. It is stateless and is called inside el-form validate callbacks, so binding it to a form instance would force callers to destructure a helper they never needed from that instance."
  - "The extraction is character-for-character the duplicated body: first-error-wins, no trimming, no array join, no widened parameter type. Both call sites already passed the matching shape, so nothing needed reconciling."

patterns-established:
  - "Vocabulary-before-rollout: fix the complete key and label set in one foundation plan so N later migration tasks are mechanical edits rather than N spec edits"
  - "Reuse-over-addition with the decision recorded inline: when an existing key's wording fits, reuse it and write the call site into a comment, so the omission is auditable"
  - "Raw-key-leak guard: `expect(fieldLabel(field)).not.toBe(field)` over the constrained-field list — the assertion that catches a deleted label instead of merely a changed one"

requirements-completed: [ERR-03, ERR-05]

coverage:
  - id: D1
    description: "ERROR_MESSAGES holds 46 entries covering every module in the rollout, each at or under 30 characters with no generic or English wording"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/utils/errorMessages.spec.ts#contains all scenario keys with non-empty copy"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/errorMessages.spec.ts#caps every entry at 30 characters"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/errorMessages.spec.ts#never contains generic or English wording"
        status: pass
    human_judgment: false
  - id: D2
    description: "The dictionary count assertion still derives from DICTIONARY_KEYS.length, so extending the array is the only way to raise it — no literal 46 exists in the spec"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/utils/errorMessages.spec.ts:67 — toHaveLength(DICTIONARY_KEYS.length); grep for a literal 46 in that file returns 0"
        status: pass
    human_judgment: false
  - id: D3
    description: "FIELD_LABELS holds 44 entries, and all five constrained mood fields resolve to a Chinese label so a mood 400 can never surface a raw camelCase key"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/utils/errorMessages.spec.ts#maps all 44 backend field names to Chinese labels"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/errorMessages.spec.ts#never resolves a mood field back to its raw key (the mood-400 raw-key leak)"
        status: pass
    human_judgment: false
  - id: D4
    description: "The contract's named labels resolve to their exact copy: moodScore 心情评分, cronExpression Cron 表达式, targetValue 目标值, clientSecret 客户端密钥"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/utils/errorMessages.spec.ts#covers every field the contract calls out as newly labelled"
        status: pass
    human_judgment: false
  - id: D5
    description: "A caller-supplied labels map beats the global table, and the same field falls back to the global label when no map is supplied"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/composables/useFormServerErrors.spec.ts#lets a caller-supplied label override the global field label table"
        status: pass
      - kind: unit
        ref: "src/__tests__/composables/useFormServerErrors.spec.ts#falls back to the global field label when no labels map is supplied"
        status: pass
    human_judgment: false
  - id: D6
    description: "collectInvalidFieldMessages is exported exactly once and drops fields whose error list is empty, returning {} for undefined input"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/composables/useFormServerErrors.spec.ts#collectInvalidFieldMessages keeps only fields that carry a message"
        status: pass
      - kind: unit
        ref: "src/__tests__/composables/useFormServerErrors.spec.ts#collectInvalidFieldMessages returns an empty object for undefined input"
        status: pass
    human_judgment: false
  - id: D7
    description: "Both reference implementations import the shared collector and carry no local copy, with their specs passing at unchanged case counts (ProfileErrorSample 3, RiskBackfill 33)"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/views/ProfileErrorSample.spec.ts — 3 tests passed, unchanged"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/RiskBackfill.spec.ts — 33 tests passed, unchanged"
        status: pass
      - kind: unit
        ref: "grep -c 'function collectInvalidFieldMessages' returns 0 in each view; grep -c 'export function collectInvalidFieldMessages' returns 1 codebase-wide"
        status: pass
    human_judgment: false
  - id: D8
    description: "Zero-entry summaries still render no block at all, and a partial rejection marks only the rejected fields while preserving every input value"
    requirement: ERR-05
    verification:
      - kind: unit
        ref: "src/__tests__/components/FormErrorSummary.spec.ts#renders nothing when there are zero entries"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ProfileErrorSample.spec.ts#A. 400 字段校验失败：零 toast、顶部汇总 2 项、中文字段标签、内联错误、值保留"
        status: pass
    human_judgment: false

metrics:
  duration: 4min
  completed: 2026-11-10
status: complete
---

# Phase 14 Plan 03: Rollout vocabulary and shared field-error collector

**The rollout's entire error vocabulary is fixed at 46 dictionary keys and 44 field labels before the first migration, and `collectInvalidFieldMessages` now exists once instead of twice.**

## Performance

- **Duration:** 4 min
- **Tasks:** 2 of 2
- **Commits:** 2
- **Files changed:** 6 modified, 0 created; 226 insertions, 39 deletions

## Accomplishments

- `ERROR_MESSAGES` grew 18 → 46: the contract's 26 keys plus `mood.loadFailed` and `mood.deleteFailed`, grouped by module with a one-line comment per group. Every entry is ≤30 code points (longest is 19), zero collisions, no banned wording.
- `FIELD_LABELS` grew 17 → 44: the contract's 22 verbatim plus the five constrained mood fields. The five are the difference between a usable mood form and one that shows `moodScore：心情评分不能为空`.
- `useFormServerErrors` gained an optional `labels` map resolved as `options.labels?.[field] ?? fieldLabel(field)` — one line, and both the summary label and the ordering behaviour follow from it.
- `collectInvalidFieldMessages` is exported once from the composable module with the byte-identical body the two views carried, and both views now import it.
- Six new assertions carry the load: four label spot checks, an explicit `fieldLabel('moodScore') !== 'moodScore'` guard, a loop over the five mood keys, plus four composable cases for the labels precedence and the collector's empty/undefined inputs.

## What was verified about the arithmetic

The plan's numbers were re-derived from source rather than trusted, because a wrong count here fails `errorMessages.spec.ts` in a way that looks like a code bug:

| Source | Count |
|---|---|
| `ERROR_MESSAGES` before | 18 |
| Contract keys added | 26 |
| Mood read/delete keys added | 2 |
| **`ERROR_MESSAGES` after** | **46** |
| `FIELD_LABELS` before | 17 |
| Contract labels added | 22 |
| Mood labels added (`notes` already present) | 5 |
| **`FIELD_LABELS` after** | **44** |

A parsed read of the two object literals confirms 46 and 44 keys with no duplicate identifiers and no key appearing in both tables.

## The reuse decisions, recorded so they are not re-litigated

The plan resolved Research's other dictionary gaps by reuse. Each is written into a comment above the closing brace of `ERROR_MESSAGES`:

| Call site | Reuses | Why |
|---|---|---|
| `reminders/Index.vue:181` | `alert.updateFailed` | Generic status-update wording fits a reminder toggle |
| `health/Score.vue:457` | `score.loadFailed` | The recalc failure is a load failure from the user's view |
| `prevention/Risk.vue:310,335` | `profile.loadFailed` / `profile.saveFailed` | The risk form *is* the profile form |
| `settings/DevicePlatformConfig.vue:355` | `device.saveFailed` | The test-connection failure is a save-path failure |
| `OcrHistory` | `ai.loadFailed` / `ai.deleteFailed` | Same session list |
| `ai.sendFailed` | — (not added) | Resolved open question 2: in-place retry bubble, so a key would have no consumer |

## Precedence, stated once

`labels` → `FIELD_LABELS` → `''`. An empty label renders the backend message alone; it never falls back to the field name. Both ends of that chain are now asserted rather than assumed.

## Deviations from Plan

None — the plan executed exactly as written. Both tasks' `<automated>` verifies passed on the first run, and every grep acceptance criterion matched its stated expectation.

## Auth gates

None. No credential, external service or `user_setup` step was involved — this plan is two data objects, one optional parameter, one extracted helper and their specs.

## Verification evidence

| Command | Result |
|---|---|
| `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/utils/errorMessages.spec.ts` | 14 passed (was 12 → +2) |
| `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/composables/useFormServerErrors.spec.ts` | 18 passed (was 14 → +4) |
| `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/ProfileErrorSample.spec.ts src/__tests__/views/RiskBackfill.spec.ts` | 36 passed (3 + 33, counts unchanged) |
| `npm --prefix hhs-frontend-v2 run test:run` | 487 passed / 39 files (baseline 481 / 39 → +6) |
| `npm --prefix hhs-frontend-v2 run build` | built in 9.75s |
| `npx eslint` on the four Task-1 files | 0 errors, 0 warnings |
| `grep -c "toHaveLength(44)" errorMessages.spec.ts` | 1 |
| `grep -c "46" errorMessages.spec.ts` | 0 — the dictionary assertion reads `DICTIONARY_KEYS.length` |
| `grep -c "moodScore\|energyLevel\|stressLevel\|sleepQuality\|entryDate" errorMessages.ts` | 7 (5 entries + 2 comment lines), all inside `FIELD_LABELS` |
| `grep -c "export function collectInvalidFieldMessages"` (codebase) | 1 |
| `grep -c "labels?.\[field\]" useFormServerErrors.ts` | 1 |
| `grep -c "function collectInvalidFieldMessages"` (both views) | 0 each |
| `grep -c "collectInvalidFieldMessages"` (both views) | 2 each — 1 import + 1 call site, call sites intact at Profile.vue:421 / Risk.vue:399 |

## Note on the 14-02 error tokens

Confirmed, not assumed: `--hhs-color-error-text` and `--hhs-color-error-tint` already existed in `theme.css` before 14-02 (declared in both theme blocks, `theme.css:56-59` dark / `:173-174` light, from Phase 11). 14-02's commit `4aac540` added six lines that *consume* them and declared neither. So there is no undefined-token defect to flag and nothing for 14-02 to own here.

## Known Stubs

None. Both objects are complete data with a call site or a documented reuse for every entry; the helper is fully implemented and consumed by two callers. No `TODO`, no placeholder copy, no unwired field.

No `format:check` gate was added anywhere — the plan forbids it, and `package.json` is untouched by both commits.

## Self-Check: PASSED

All six changed source files plus this SUMMARY exist on disk; both task commits (`f53fcaf`, `b0e9a16`) are present in history.
