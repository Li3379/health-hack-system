---
phase: 13-profile-guidance
plan: 10
subsystem: frontend-views
tags: [score-page, estimate-badge, null-vs-zero, no-data-contract, copywriting-contract, phase-gate, tdd]
requires:
  - phase: 13-profile-guidance
    provides: "HealthScoreVO {profileCompleteness?: number | null, assumptions?: string[]} — null on the NO_DATA path by deliberate backend contract (13-03)"
  - phase: 13-profile-guidance
    provides: "useProfileCompletenessStore.fetchCompleteness() single-flight + awaitable, and firstMissingKey() over the single priority-rule implementation (13-05)"
  - phase: 13-profile-guidance
    provides: "components/common/EstimateBadge.vue — prop completeness: number|null|undefined, emits complete, self-drawn button, null-safe (13-06)"
  - phase: 13-profile-guidance
    provides: "the goProfileWithFocus() shape proven on prevention/Risk.vue (13-09)"
  - phase: 11-error-presentation
    provides: "theme.css global .hhs-estimate-badge family and the 400/600-only typography discipline"
provides:
  - "health/Score.vue — EstimateBadge mounted in .score-info between .score-tips and .score-meta, gated on profileCompleteness != null && < 100"
  - "health/Score.vue — goProfileWithFocus() carrying the store's firstMissingKey() as query.focus, and the mount-time single-flight completeness fetch"
  - "health/Score.vue — report dialog secondary label 取消 -> 放弃生成 with a machine-checkable guard (bare 取消/确定/关闭 count = 0)"
  - "ScoreEstimateBadge.spec.ts — 10 tests over gating, DOM placement, navigation, the NO_DATA branch and the label"
  - "the both-side phase gate result table (backend 845 tests, frontend 445 tests, build, lint, checkstyle)"
affects:
  - "/gsd:verify-work for phase 13 — the second of PRF-06's two result surfaces is now wired, so the phase gate has a green both-side baseline to sample"
  - "none downstream — 13-10 is the final plan of the phase"
tech-stack:
  added: []
  patterns:
    - "`?? null` (not `|| 0`) at the mount boundary so a null payload stays null and the badge's null suppression — not the page — owns the no-fabrication rule"
    - "Real component under test for a gate whose contract is a null distinction; stubbing it would assert the stub's behaviour"
    - "Machine-checkable copy guard (`grep -Ec '>(取消|确定|关闭)<'` == 0) beside the prose contract, so a removed label cannot silently return"
key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/ScoreEstimateBadge.spec.ts
  modified:
    - hhs-frontend-v2/src/views/health/Score.vue
key-decisions:
  - "The badge is bound with `scoreData.profileCompleteness ?? null` rather than passed through raw. `?? null` normalises `undefined` (an older payload that never had the field) and `null` (the NO_DATA contract) to the same value, which is what EstimateBadge's `!= null` gate reads. A `|| 0` would have painted a fabricated 完整度 0% on the no-data screen — the exact failure T-13-38 exists to prevent"
  - "EstimateBadge is mounted, not inlined or re-implemented. 13-06 already proves the null/undefined/100/0 matrix in 10 tests; a second copy on this page would drift from Risk.vue's copy silently"
  - "The NO_DATA branch was left untouched instead of being additionally guarded. It renders `.no-data-content` instead of `.score-content`, so `.score-info` — the badge's only mount point — does not exist. The spec asserts this with a payload that carries profileCompleteness: 40 on a NO_DATA level, proving the structural isolation holds even if the payload were wrong"
  - "The completeness fetch is fire-and-forget in onMounted, placed before `await fetchScore()`. It is not a rendering dependency: unlike Risk.vue's three-order rule, nothing on this page branches on it. The store's single-flight guard means a concurrent caller shares the same promise, so the ordering cannot produce a second HTTP request"
  - "goProfileWithFocus mirrors Risk.vue's implementation verbatim, including the `query: key ? { focus: key } : {}` shape. Both surfaces must resolve the same field, and the key comes from the store's firstMissingKey() — the one implementation in utils/profileFields.ts — never from the score payload, which carries no field list at all"
  - "Only the visible text of the dialog button changed (取消 -> 放弃生成); the @click=\"cancelGenerate\" binding and handler were not touched, because cancelGenerate already means 'abandon the in-flight generation' — zero toast, abort, close"
  - "The placement rule is scoped (`.score-info .hhs-estimate-badge { margin-bottom: 12px }`) while colour, border and focus ring stay global in theme.css. Contract 4 puts the spacing on the host page and the appearance in the shared family, so no second copy of the badge's visual definition exists"
  - "The spec mocks echarts at module scope (echarts/core, charts, components, renderers) because Score.vue calls `echarts.use([...])` at import time; leaving it unmocked makes the suite fail before a single test runs. vue-router is mocked with importOriginal so `@/router` still resolves createRouter for `@/stores/auth`"
patterns-established:
  - "A page-level badge inherits the gating decision from a shared component and passes `?? null`, so the 'never fabricate a value' rule has exactly one owner"
  - "DOM-order assertions compare child indexes of the real host element (`Array.from(info.children)`) rather than asserting a CSS sibling selector, so a reordering that keeps the selector valid still fails"
requirements-completed: [PRF-06]
actuals:
  tokens: 4615  # chars/4 over the realized diff (18,460 chars via git diff plan_head_before..HEAD)
  tasks: 2
  # MEASURED: git rev-list --count plan_head_before..HEAD = 1 at SUMMARY write time
  # (the single task commit). This docs commit is the 2nd, giving 2 total.
  commits: 2
plan_head_before: 7b05121e5426b2f75e1e35fb5a41668dcc2cb3fc
status: complete
---

# Phase 13 Plan 10: 健康评分页估算徽标与全阶段回归门 Summary

The score page now discloses when its number leans on profile defaults, sends the user to the exact field that would sharpen it, and stays silent when it has nothing honest to say — including on the no-data screen, where the backend deliberately reports "unknown" rather than "0%".

## What Was Built

**`health/Score.vue` (+32/−1 lines).** Four additions and one rename, all inside the normal-score branch and the report dialog.

The badge sits inside `.score-info`, immediately after `<p class="score-tips">` and immediately before `<div class="score-meta">`, bound as `:completeness="scoreData.profileCompleteness ?? null"` with `@complete="goProfileWithFocus"`. The comment above it records why the `?? null` is load-bearing: `HealthScoreServiceImpl` returns `createDefaultScore()` with `profileCompleteness` deliberately null for a user with no metrics, so on that path the field must reach the badge as null and the badge's `!= null` gate must suppress it. `?? null` collapses `undefined` (an older payload) and `null` (the contract) into the same value; a `|| 0` would have rendered 完整度 0% on a screen where no score was ever computed.

`goProfileWithFocus()` is a five-line copy of the post-13-09 Risk.vue helper:

```ts
const key = completenessStore.firstMissingKey()
router.push({ path: '/prevention/profile', query: key ? { focus: key } : {} })
```

It reads the store, never the payload: the score response carries `assumptions` but no field list, so only the dedicated completeness endpoint can name the highest-priority gap.

`onMounted` gained one line, placed first and not awaited: `completenessStore.fetchCompleteness()`. Nothing on this page branches on the result, so awaiting it would have delayed the score render for no benefit; the store's single-flight guard means any concurrent caller shares the same promise rather than issuing a second request. `await fetchScore()` and every pre-existing GSAP call after it are unchanged.

The report-generation dialog's secondary button became `放弃生成` (UI-SPEC §Appendix R-1). Only the visible text changed — `@click="cancelGenerate"` and the handler stay, because that handler already means "abandon the in-flight generation" (abort the controller, clear the loading flag, close, reset). The pre-existing `--text-3` usages at lines 696/778 were deliberately left alone: they are out of this plan's scope and fixing them would have widened the change.

The style block gained exactly one rule, `.score-info .hhs-estimate-badge { margin-bottom: 12px }`. Colour, border and focus ring remain global in `theme.css`, per Contract 4's split between host-page spacing and the shared family.

**`ScoreEstimateBadge.spec.ts` (424 lines, 10 tests).** Mounts the real `EstimateBadge` — stubbing it would have turned the null contract into an assertion about a stub — while stubbing `HealthScoreCircle` and the Element Plus set. echarts is mocked at all four module entry points because `Score.vue` calls `echarts.use([...])` at module scope; vue-router is partially mocked via `importOriginal` so `@/stores/auth` can still resolve `createRouter` through `@/router`. The store is exercised for real against a mocked `preventionApi.getCompleteness`, so the single-flight path and `firstMissingKey()` are the production implementations.

The tests cover: the mount fetch (`{ silent: true }`, exactly once); badge presence at 40 and at 0; absence at null, absent-field and 100; containment in `.score-info` plus child-index ordering tips → badge → meta; navigation with `query: {}` when nothing is missing and with `focus: 'birthDate'` when only gender is filled; the NO_DATA branch with a payload that carries `profileCompleteness: 40` yet still renders no badge; and the dialog's `放弃生成` text with an explicit `not.toContain('取消')`.

## Phase Gate Results

Run in the plan's prescribed order. Frontend baseline was 435 tests; this plan adds 10.

| Leg | Command | Result |
|---|---|---|
| Backend checkstyle | `mvn -f hhs-backend/pom.xml checkstyle:check` | **PASS** — 0 violations, BUILD SUCCESS (the `[WARN]` lines are pre-existing informational indentation reports from the `sun_checks` config) |
| Backend tests + package | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` | **PASS** — 845 tests, 0 failures, 0 errors, 0 skipped; jacoco coverage checks met; BUILD SUCCESS |
| Frontend tests | `npm --prefix hhs-frontend-v2 run test:run` | **PASS** — 445 tests / 37 files, 0 failures (baseline 435 + 10 new) |
| Frontend build | `npm --prefix hhs-frontend-v2 run build` (`vue-tsc -b && vite build`) | **PASS** — built in 10.94s; the >500 kB chunk warning is pre-existing |
| Frontend lint | `npm --prefix hhs-frontend-v2 run lint:check` | **PASS** — 0 errors, 216 warnings (all pre-existing patterns); exit 0 |
| Dictionary guard | `git diff --stat hhs-frontend-v2/src/utils/errorMessages.ts errorMessages.spec.ts` | **PASS** — empty; the 18-key exact-length assertion still passes |
| Regression guard | `npm ... test:run -- errorMessages.spec.ts ProfileErrorSample.spec.ts` | **PASS** — 15 tests, 0 failures |

No gate leg failed, so nothing was fixed under gate pressure and no pre-existing assertion was loosened. The Testcontainers/Docker integration-test limitation recorded in `.planning/WINDOWS.md` entry 3 was not re-triggered: `-DskipITs` keeps Failsafe out of the run, as designed.

## Task 1 Acceptance Criteria

| Criterion | Result |
|---|---|
| Spec runs ≥ 6 tests with zero failures | **10 passed** |
| Badge present at 40 and 0, absent at null and 100 | covered by four separate tests (0 also asserts a genuine value is not a falsy miss) |
| Badge is a descendant of `.score-info` and follows `.score-tips` in DOM order | covered — child-index comparison, not just a CSS selector |
| NO_DATA response renders no badge | covered — payload carries `profileCompleteness: 40` and still renders none |
| Dialog secondary button text is 放弃生成 | covered, plus `not.toContain('取消')` |
| `grep -c "EstimateBadge"` ≥ 2 | **2** (import + usage) |
| `grep -c "el-tag"` unchanged | **9 before, 9 after** |
| `grep -Ec '>(取消\|确定\|关闭)<'` == 0 | **0** |

## Must-Have Truth Coverage

| Must-have truth | Verification ref | Status |
|---|---|---|
| The badge renders inside `.score-info`, after `.score-tips` and before `.score-meta`, when `profileCompleteness` is a number below 100 | `renders the badge inside .score-info, after .score-tips and before .score-meta`; `renders the badge for a genuine 0% profile` | covered |
| The badge is absent when `profileCompleteness` is null, undefined or exactly 100 — the NO_DATA path never shows a fabricated estimate claim | `renders no badge when profileCompleteness is null`; `renders no badge when the field is absent entirely`; `renders no badge at exactly 100` | covered |
| Clicking the badge navigates to `/prevention/profile` with `query.focus` from `firstMissingKey()` when one exists, and with no query otherwise | `pushes with query.focus set to the store first missing key` (`{ focus: 'birthDate' }`); `pushes to the profile page with no query when nothing is missing` (`query: {}`) | covered |
| The badge never renders on the NO_DATA branch even if `profileCompleteness` were non-null, because that branch renders the empty state | `renders the empty state and no badge on the NO_DATA branch` — asserts `.no-data-content` present, `.score-content` absent, badge absent with `profileCompleteness: 40` in the payload | covered |
| The badge is not an `el-tag` and does not appear inside any table | `renders the badge inside .score-info…` asserts `.el-table .hhs-estimate-badge` is absent; the component's own 13-06 spec asserts `tagName === 'button'`; `grep -c "el-tag"` unchanged at 9 | covered |
| The report dialog's secondary button is labelled 放弃生成; no visible button text is exactly 取消 / 确定 / 关闭 | `labels the report dialog secondary action 放弃生成, never the bare 取消`; `grep -Ec '>(取消\|确定\|关闭)<'` == 0 | covered |

## Deviations from Plan

**1. [Rule 3 - Blocking] vue-router needed `importOriginal` rather than a full replacement**
- **Found during:** Task 1 (first spec run)
- **Issue:** The plan sketch said to mock `vue-router` with `useRouter: () => ({ push: vi.fn() })`. A full replacement made the suite fail at import time with `No "createRouter" export is defined on the "vue-router" mock`, because `@/utils/request` pulls in `@/stores/auth`, which imports `@/router`, whose module body calls `createRouter({...})`.
- **Fix:** Partial mock via `importOriginal`, preserving the real exports and overriding only `useRouter`. This is the same shape `RiskBackfill.spec.ts` already uses.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/ScoreEstimateBadge.spec.ts`
- **Commit:** `0eb0879`

**2. [Rule 3 - Blocking] `getReport` and the extra `scoreApi` methods had to be mocked**
- **Found during:** Task 1
- **Issue:** `Score.vue` imports `scoreApi` as a whole object and calls `getReport`, `generateReport` and `getScoreHistory` in addition to `getScore`. The plan named only `getScore` and `recalculate`, so the mock object would have thrown on the dialog test.
- **Fix:** Mocked the full `scoreApi` surface with `vi.fn()` per method. No production code changed.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/ScoreEstimateBadge.spec.ts`
- **Commit:** `0eb0879`

**3. [Rule 1 - Bug] Removed a non-null assertion the lint rules flag**
- **Found during:** Task 1 lint pass
- **Issue:** `reportButton!.trigger('click')` produced a new `@typescript-eslint/no-non-null-assertion` warning, which would have been the only new warning introduced by this plan.
- **Fix:** Replaced with `expect(reportButton).toBeDefined()` plus an explicit `if (!reportButton) throw new Error(...)` narrow, so the spec adds zero warnings.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/ScoreEstimateBadge.spec.ts`
- **Commit:** `0eb0879`

No architectural (Rule 4) change was required. No package installs were attempted (T-13-SC holds: echarts and gsap are existing dependencies, mocked only inside the test).

## Auth Gates

None. No task required credentials, logins or manual setup.

## Threat Surface

T-13-38 (badge spoofing on NO_DATA) — mitigated and test-proven twice over: the `?? null` normalisation keeps the contract value intact, the badge's `!= null` gate suppresses it, and the structural isolation of `.no-data-content` provides a second barrier asserted with an adversarial payload.

T-13-39 (copy disclosure) — the badge text is the fixed constant owned by `EstimateBadge`; the focus key is a field key from the static 9-field catalogue, matched against `fields[]` on the destination page before use.

T-13-40 (test-count baseline) — recorded in the gate table: 445 frontend tests, empty diff on both dictionary files, 18-key assertion intact.

T-13-41 (bare label regression) — the guard is mechanical, not prose: `grep -Ec '>(取消|确定|关闭)<'` reports 0 and the spec asserts the visible text is `放弃生成`.

No new security-relevant surface was introduced beyond the badge placement this plan was scoped to add. No `## Threat Flags` entries.

## Known Stubs

None. The badge is fed by the real `HealthScoreVO.profileCompleteness` and navigates with the real store key; the label rename is complete. No placeholder text, no hardcoded empty values flowing to the UI, no test skipped.

## Self-Check: PASSED

- `hhs-frontend-v2/src/views/health/Score.vue` — modified, present
- `hhs-frontend-v2/src/__tests__/views/ScoreEstimateBadge.spec.ts` — created, present
- Commit `0eb0879` — present in `git log`