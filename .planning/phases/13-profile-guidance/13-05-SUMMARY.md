---
phase: 13-profile-guidance
plan: 05
subsystem: frontend-state
tags: [completeness, pinia-store, single-flight, dismissal-window, offline-catalog, tdd]
requires:
  - phase: 13-profile-guidance
    provides: "GET /api/prevention/profile/completeness returning {score, level, filledCount, totalCount, hasProfile, assumptions, fields[]} (13-01, 13-02)"
  - phase: 13-profile-guidance
    provides: "ProfileFieldCatalog 9-key catalog, labels, importance tiers, reason copy and NO_PROFILE_ASSUMPTION (13-01)"
  - phase: 13-profile-guidance
    provides: "RiskAssessmentVO#profileCompleteness/#assumptions wire fields (13-04)"
  - phase: prevention-module
    provides: "preventionApi.getProfile(config?) silent-passthrough convention; utils/appError normalizeApiError"
provides:
  - "types/api.ts: ProfileFieldStatusVO, ProfileCompletenessVO; HealthScoreVO#profileCompleteness/#assumptions; RiskAssessmentVO#profileCompleteness/#assumptions"
  - "preventionApi.getCompleteness(config?: AxiosRequestConfig)"
  - "utils/profileFields.ts: PROFILE_FIELDS (9 entries), NO_PROFILE_ASSUMPTION, firstMissingKeyOf(), missingFieldsOf(), synthesizeEmptyCompleteness()"
  - "stores/profileCompleteness.ts: useProfileCompletenessStore (data, loading, loadFailed, dismissedAt, fetchCompleteness, isDismissed, dismiss, firstMissingKey, missingFields)"
  - "__tests__/stores/profileCompleteness.spec.ts (48 tests)"
affects:
  - "13-06 (ProfileReminderBanner and ProfileCompleteness render this store; firstMissingKey/missingFields come from utils/profileFields.ts)"
  - "13-07 (Profile.vue awaits fetchCompleteness in onMounted and again after a successful save)"
  - "13-08 (dashboard banner gates on score < 60 && !isDismissed(); compact row reads the same payload)"
  - "13-09 (Risk.vue awaits fetchCompleteness at mount and re-awaits at click time; renders data.assumptions)"
tech-stack:
  added: []
  patterns:
    - "Single-flight + awaitable store fetch: the in-flight promise is returned, never an early void return during the loading window"
    - "Three distinguishable states (success / 404-synthesis / transport failure) instead of one collapsed error branch"
    - "One priority-rule implementation in a pure util, imported by every consumer, instead of per-component re-derivation"
    - "Static catalog mirrored from the backend constant and pinned by a parity test, with reason-copy assertions as drift guards"
key-files:
  created:
    - hhs-frontend-v2/src/utils/profileFields.ts
    - hhs-frontend-v2/src/stores/profileCompleteness.ts
    - hhs-frontend-v2/src/__tests__/stores/profileCompleteness.spec.ts
  modified:
    - hhs-frontend-v2/src/types/api.ts
    - hhs-frontend-v2/src/api/prevention.ts
key-decisions:
  - "fetchCompleteness returns the in-flight promise (`if (inFlight) return inFlight`) rather than mirroring metricMeta's `if (loading) return` guard: the early return makes `await` a no-op during the busy window, so 13-07/13-09 callers would re-read a pre-settlement null. The test asserts BOTH halves — exactly one HTTP call and no resolution before the deferred response is delivered"
  - "The single-flight variable is store-scoped (inside the setup factory), not module-global, so two Pinia instances cannot share a promise"
  - "Pinia wraps setup actions, so each call returns a distinct derived promise even when the underlying work is shared — the spec therefore asserts call count and settlement ordering rather than promise reference identity"
  - "The dismissal boundary is inclusive (`Date.now() - parsed <= SILENCE_MS`): the test at exactly 7 days must stay silent and 7 days + 1 ms must render again"
  - "The spec recomputes SILENCE_MS from the literal 7*24*60*60*1000 instead of importing it, so a silent change to the production window fails the boundary tests"
  - "404 synthesis delegates to synthesizeEmptyCompleteness() which reproduces hasProfile false and [NO_PROFILE_ASSUMPTION] exactly: an empty assumptions array would silently disable 13-09's backfill panel for a brand-new user"
  - "A later-refresh failure keeps the previously loaded payload and only flips loadFailed, so a transient blip never blanks a good header"
  - "The 风险 reason guard mirrors the backend's rule (five risk-chain fields may mention it, four may not) rather than the stricter one-key reading of the plan prose — the backend test is the authoritative statement of which fields honestly reach the risk engine"
patterns-established:
  - "Awaitable dedupe: coalescing concurrent fetches by returning the shared pending promise keeps both the request-count guarantee and the read-after-await guarantee"
  - "Distinguishable failure states at the state layer (loadFailed) so renderers can withhold rather than fabricate a 0%"
  - "Mutation-verified drift guards: rewriting a reason string or flipping the priority tiebreak turns the parity/ordering tests red"
requirements-completed: [PRF-01, PRF-04, PRF-06, PRF-07]
actuals:
  tokens: 10049  # chars/4 over the realized diff (40,198 chars across hhs-frontend-v2)
  tasks: 3
  commits: 4     # MEASURED: git rev-list --count plan_head_before..HEAD (feat + test x2 + this docs commit)
plan_head_before: 934e87f3eea3fb17939067ed518f63b36e59bf7b
status: complete
---

# Phase 13 Plan 05: 前端档案完整度 Store·单源契约 Summary

One Pinia store is now the only fetcher of `GET /api/prevention/profile/completeness`, and it distinguishes three states a renderer must never collapse: the backend's authoritative payload, a 404-synthesized row-absent 0% that matches the backend's own 0% semantics, and an explicit `loadFailed` that renders nothing rather than a fabricated `完整度 0%`.

## What Was Built

**Type contracts (`types/api.ts`).** `ProfileFieldStatusVO` (`key`, `label`, `importance`, `filled`, `reason`, optional `assumption`) and `ProfileCompletenessVO` (`score`, `level`, `filledCount`, `totalCount`, `hasProfile`, `assumptions`, `fields`). `hasProfile` and `assumptions` are **required**, not optional, because the backend has sent both since 13-01: `hasProfile` is what lets 13-07's header and 13-08's impact line distinguish "no profile row" from "sparse profile", and `assumptions` is what 13-09's backfill panel renders. `HealthScoreVO` and `RiskAssessmentVO` each gained optional `profileCompleteness?: number | null` and `assumptions?: string[]`, so every pre-existing consumer still compiles.

**API method (`api/prevention.ts`).** `getCompleteness(config?: AxiosRequestConfig)` mirrors `getProfile`'s shape exactly, with a comment recording the truth that the endpoint never 404s for a logged-in user (a missing row is `200 + score 0 + hasProfile false`), so `{ silent: true }` is passed only to keep a genuine transport failure from toasting in a guidance-only surface.

**Offline catalog (`utils/profileFields.ts`).** Nine entries reproducing `ProfileFieldCatalog` key for key, label for label, importance for importance, reason for reason, plus the three backend assumption sentences and `NO_PROFILE_ASSUMPTION`. It also holds the **single** implementation of the priority rule:

- `firstMissingKeyOf(fields)` ranks `required → recommended → optional` and uses array index as the within-tier tiebreak, returning `null` when nothing is missing.
- `missingFieldsOf(fields)` returns the unfilled entries in catalog order so the banner can name them without re-sorting.
- `synthesizeEmptyCompleteness()` returns `{ score: 0, level: '基础档案', filledCount: 0, totalCount: fields.length, hasProfile: false, assumptions: [NO_PROFILE_ASSUMPTION], fields }` with **fresh** field objects and a fresh assumptions array, so a consumer mutating either cannot corrupt the module constant.

The file's header states plainly that it is a rendering fallback only and must never be used to compute a score.

**Store (`stores/profileCompleteness.ts`).** State `data` / `loading` / `loadFailed` / `dismissedAt`, plus a store-scoped `let inFlight: Promise<void> | null`. `fetchCompleteness()` is three statements:

```ts
if (inFlight) return inFlight
inFlight = (async () => { /* work */ })()
return inFlight
```

The IIFE starts synchronously, so `loading.value = true` is set before the assignment completes, and its `finally` clears both `loading` and `inFlight` — which can only happen after the awaited call settles, so it can never clear `inFlight` before the assignment stored it. On success the payload is stored verbatim, `hasProfile` and `assumptions` included, with no transformation and no recomputed score. On `status === 404`, `health.profile_not_found` or `resource.not_found` the store assigns `synthesizeEmptyCompleteness()` and leaves `loadFailed` false. Any other failure leaves `data` as-is and sets `loadFailed = true`. The function never throws and never toasts.

`isDismissed()` reads `hhs_profile_reminder_dismissed_at`, returns false when absent, false when `Date.parse` yields `NaN`, else `Date.now() - parsed <= SILENCE_MS`. `dismiss()` writes an ISO string and bumps `dismissedAt` so `showBanner`-style computeds re-evaluate. Nothing in the file ever removes the key — the documented shared-silence limitation for multi-account browsers is recorded in the constant's comment.

## Verification Results

| Check | Result |
|---|---|
| `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/stores/profileCompleteness.spec.ts` | **48 tests, 0 failures** (target was ≥ 20) |
| `npm --prefix hhs-frontend-v2 run test:run` (full suite) | **311 tests / 30 files, 0 failures** |
| `npm --prefix hhs-frontend-v2 run build` (`vue-tsc -b && vite build`) | green — the optional-field additions type-check |
| `npm --prefix hhs-frontend-v2 run lint:check` | 0 errors; the 2 remaining warnings are pre-existing `no-explicit-any` on `api.ts:9` and `api.ts:175`, untouched by this plan |
| `grep -c "hhs_profile_reminder_dismissed_at" profileCompleteness.ts` | **1** — declared once, removed nowhere |
| `grep -c "score:" profileCompleteness.ts` | **0** — no arithmetic on score anywhere in the store |
| Mutation: heightCm reason rewritten to `影响体重维度评分` | 3 tests red (verbatim copy, 体重维度 ban, 风险 distribution) |
| Mutation: priority tiebreak flipped to `rank <= bestRank` | 7 ordering tests red |

## Must-Have Truth Coverage

| Must-have truth | Verification ref | Status |
|---|---|---|
| The store is the only fetcher of the completeness endpoint; no component calls the API directly | `preventionApi.getCompleteness` is imported **only** by `stores/profileCompleteness.ts`; the spec mocks the API module and asserts `toHaveBeenCalledTimes(1)` per fetch | covered |
| A successful fetch stores score/level/filledCount/totalCount/fields verbatim; the store never recomputes | `stores score, level, filledCount, totalCount and fields verbatim`; `derives totalCount from the payload field list`; `grep -c "score:"` == 0 | covered |
| A failed fetch leaves data null and sets loadFailed, so failure never renders as 0% | `leaves data null and sets loadFailed on a network error — never a fabricated 0%`; `keeps a previously loaded payload when a later refresh fails` | covered |
| 404 / `health.profile_not_found` synthesizes score 0, 基础档案, filledCount 0 and the 9-entry catalog with hasProfile false | `synthesizes 0% / 基础档案 / 9 fields on a 404 and keeps loadFailed false`; `treats the health.profile_not_found business code as a 404` | covered |
| fetchCompleteness is single-flight AND awaitable: one HTTP call, and no caller resolves before the request settles | `dedupes a concurrent burst to one HTTP call and resolves only after the request settles` — asserts one call, neither `.then` fired after microtask drain, `data` still null, `loading` true, then both resolve over the same settled payload | covered |
| isDismissed() is true for exactly 7 days and false from the 8th day; an unparsable value is not dismissed | `is still dismissed at exactly 7 days`; `is still dismissed 1 ms before the 7-day boundary`; `is no longer dismissed at exactly the 7-day boundary plus 1 ms`; `is no longer dismissed after 8 days`; `fails open on a corrupt timestamp` | covered |
| dismiss() writes an ISO string to localStorage and makes isDismissed() true immediately | `dismiss() writes a parseable ISO string under hhs_profile_reminder_dismissed_at`; `is dismissed immediately after dismiss()`; `dismiss() bumps the reactive dismissedAt mirror` | covered |
| firstMissingKey prefers required over recommended over optional and returns null when complete | `返回 gender / birthDate / heightCm / bloodType / null`; `still prefers heightCm over bloodType when bloodType sits earlier in a shuffled list` | covered |
| missingFields returns unfilled entries in catalog order | `lists gender before bloodType when both are missing and length equals totalCount - filledCount`; `returns a new array, not the input` | covered |
| The store never clears the dismissal key on logout — a preference, not session state | `grep -c "hhs_profile_reminder_dismissed_at"` == 1 and that single occurrence is the `const DISMISS_KEY` declaration; `storage.clear()` in `utils/storage.ts` touches only `hhs_token`/`hhs_user` | covered |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The inclusive 7-day boundary in `isDismissed()`**
- **Found during:** Task 2 (RED run)
- **Issue:** Task 1 wrote `Date.now() - parsed < SILENCE_MS`. At exactly `base + SILENCE_MS` the comparison is `false`, so the store resumed the reminder one millisecond earlier than the orchestrator invariant requires ("exactly 7 days → still silenced; 7 days + 1 ms → render again"). The new boundary test failed exactly as designed.
- **Fix:** Changed to `<=`. Both boundary tests now pass (`7d - 1ms` → true, exactly `7d` → true, `7d + 1ms` → false).
- **Files modified:** `hhs-frontend-v2/src/stores/profileCompleteness.ts`
- **Commit:** `ff2ffc5`

### Scope-boundary notes (no code deviation)

**2. The "only birthDate mentions 风险" acceptance line is stricter than the backend truth.**
- **Found during:** Task 3 (first green attempt failed)
- **Issue:** Plan acceptance reads "only birthDate's mentions 风险". `ProfileFieldCatalogTest.onlyRiskChainFieldsMentionRisk` asserts the actual rule: five fields may mention 风险 (`birthDate`, `heightCm`, `weightKg`, `bmi`, `familyHistory` — all reach `RiskAssessmentServiceImpl`), and exactly four may not (`gender`, `bloodType`, `allergyHistory`, `lifestyleHabits`). The plan's own `<action>` copy for heightCm/weightKg/BMI contains 风险, so a strict one-key rule would contradict the catalog it mandates.
- **Fix:** The guard mirrors the backend rule — it asserts the five-key risk-chain list and then explicitly asserts each of the four non-risk fields does **not** contain 风险. The protective intent (gender cannot claim a risk-engine consumer) is fully preserved and is the assertion that matters for W4.
- **Files modified:** `hhs-frontend-v2/src/__tests__/stores/profileCompleteness.spec.ts`
- **Commit:** `3c59560`

**3. The spec does not assert promise reference identity for the concurrent burst.**
- **Found during:** Task 1 (first run)
- **Issue:** `expect(first).toBe(second)` failed. Pinia wraps setup-store actions, so each call returns a distinct derived promise around the same underlying work. Asserting identity would test a Pinia implementation detail, not the contract.
- **Fix:** The test asserts the two contract-visible invariants instead: exactly one HTTP call, and neither promise resolves (and `data` stays null) until the deferred response is delivered. Both are what 13-07/13-09 actually depend on.
- **Files modified:** `hhs-frontend-v2/src/__tests__/stores/profileCompleteness.spec.ts`
- **Commit:** `246916c`

## Known Stubs

None. Every value the store exposes is sourced from the backend payload, the backend-mirrored catalog, or `localStorage`. There is no placeholder copy, no hardcoded empty array reaching a renderer, and no unwired prop — `synthesizeEmptyCompleteness()` is a deliberate, tested, backend-equivalent state rather than a stub.

## Threat Flags

None beyond the plan's register. T-13-17 (localStorage tampering) fails open by construction and is covered by the corrupt/empty-string tests; T-13-18 (concurrent fetch DoS) is asserted by the one-call test; T-13-19 (repudiation of the synthesized 0%) is covered by the failure-vs-404 pair and the "keeps a previously loaded payload" case; T-13-20 stays accepted (the payload is the caller's own labels plus backend constant copy). No new network, file-access or schema surface; no package installs.

## Decisions Made

1. **Return the in-flight promise; never early-return void.** This is the whole point of the revision: an `await` on a short-circuited call hands the caller a pre-settlement `null`, which would make 13-09's click-time re-read skip the backfill panel.
2. **Keep `data` on a later-refresh failure** and only flip `loadFailed`, so a transient error cannot blank a header that was correct a second ago.
3. **`synthesizeEmptyCompleteness()` carries `hasProfile: false` and `[NO_PROFILE_ASSUMPTION]`**, not an empty assumptions array — a brand-new user's backfill panel depends on that one sentence.
4. **One priority-rule implementation**, in `utils/profileFields.ts`. The store is a thin delegator; 13-06's banner imports the same functions.
5. **Pinia action return values are derived promises** — recorded so a future maintainer does not "fix" the spec back to a reference assertion.

## Self-Check: PASSED

- `hhs-frontend-v2/src/utils/profileFields.ts` — FOUND
- `hhs-frontend-v2/src/stores/profileCompleteness.ts` — FOUND
- `hhs-frontend-v2/src/__tests__/stores/profileCompleteness.spec.ts` — FOUND
- `.planning/phases/13-profile-guidance/13-05-SUMMARY.md` — FOUND
- Commit `246916c` (feat: store/API/catalog) — FOUND
- Commit `ff2ffc5` (test: 7-day window) — FOUND
- Commit `3c59560` (test: ordering/parity) — FOUND
- Measured commit count `934e87f..HEAD` = 4 (feat + 2 test + docs), matching the frontmatter `commits: 4`