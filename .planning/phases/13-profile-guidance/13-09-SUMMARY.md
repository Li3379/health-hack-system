---
phase: 13-profile-guidance
plan: 09
subsystem: frontend-views
tags: [risk-page, backfill-panel, estimate-badge, three-order-rule, tdd, d-06a, read-time-recompute]
requires:
  - phase: 13-profile-guidance
    provides: "ProfileCompletenessVO {score, hasProfile, assumptions[], fields[]} + RiskAssessmentVO {profileCompleteness, assumptions[]} (13-05 types/api.ts)"
  - phase: 13-profile-guidance
    provides: "useProfileCompletenessStore.fetchCompleteness() single-flight AND awaitable — returns the pending promise instead of short-circuiting while loading (13-05)"
  - phase: 13-profile-guidance
    provides: "firstMissingKeyOf() via the store's firstMissingKey() — the single priority-rule implementation (13-05)"
  - phase: 13-profile-guidance
    provides: "components/common/EstimateBadge.vue — prop completeness: number|null|undefined, emits complete, null-safe (13-06)"
  - phase: 13-profile-guidance
    provides: "RiskAssessmentVO.assumptions filled by ProfileFieldCatalog.assumptionsFor on the read path (13-04)"
  - phase: 11-error-presentation
    provides: "FormErrorSummary + useFormServerErrors (summaryItems/apply/setErrors/clearField/reset/focusField) and showError's four-channel toast contract"
provides:
  - "prevention/Risk.vue — handleCreateClick three-order evaluation over a mount-loaded store"
  - "prevention/Risk.vue — .risk-estimate panel: alert title + assumption list + 2 required + 2 optional inputs + 3 actions"
  - "prevention/Risk.vue — .risk-disclosure EstimateBadge placement, hidden while the panel is open"
  - "prevention/Risk.vue — dialog 本次评估采用的假设 list rendered from currentAssessment.assumptions"
  - "prevention/Risk.vue — loadProfile() with getProfile({ silent: true }) for optional prefill"
  - "src/__tests__/views/RiskBackfill.spec.ts — 33 tests (mount-fetch, trigger order, actions, save path, failure retention, badge gating, dialog list)"
affects:
  - "13-10 (Score.vue mirrors the badge placement and the same read-time assumption disclosure)"
  - "Phase 13 verification: PRF-06 and PRF-08 frontend evidence now exist for the risk page"
tech-stack:
  added: []
  patterns:
    - "Awaitable single-flight store: the click-time refetch is a fall-through, not a loop, and its await genuinely waits for the mount request"
    - "Panel copy rendered from the payload's aggregated assumptions array, never joined per field"
    - "Optional inputs carried through a coalescing chain (user input → stored value → omitted) so a prefill can never be silently wiped"
key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts
  modified:
    - hhs-frontend-v2/src/views/prevention/Risk.vue
key-decisions:
  - "The mount fetch is awaited before fetchAssessments, and handleCreateClick awaits the same store promise when data is still null. Both are needed: the first makes a first visit correct, the second makes a fast click during mount correct. Because 13-05's fetch returns the pending promise rather than short-circuiting, the second await is real — a mutation to `void fetchCompleteness()` makes two spec cases fail, which was verified by reverting it."
  - "pendingAssumptions is completeness.assumptions verbatim (computed, 3 occurrences, grep for f.assumption is 0). A per-field join would emit the birthDate/bmi/familyHistory sentences for a null profile while the engine applies one generic sentence — the panel would promise three substitutions the engine never makes."
  - "Step 1 of the three-order rule is linear, not a loop: it runs at most once per click by construction, and the re-read is a fall-through into step 2. That is what keeps 'no API call while the fetch is pending' and 'creates exactly once when the payload turns out empty' both true."
  - "The optional 身高/体重 pair carries no :rules entry and no :disabled, and the payload coalesces backfill.heightCm ?? existing?.heightCm ?? undefined. Omitting the key rather than sending null keeps HealthProfileRequest's number|undefined shape honest; the server treats both as absent (toEntity calls request.getHeightCm() directly)."
  - "bmi is never computed on the client (D-01): the backend derives it in HealthProfileServiceImpl.toEntity, so supplying both optional inputs retires the BMI assumption on the next completeness refetch with no arithmetic here."
  - "collectInvalidFieldMessages is duplicated locally (8 lines) rather than extracted, because extracting it would require editing prevention/Profile.vue, which this plan's file list forbids."
  - "The badge receives completeness?.score ?? null and renders only for a non-null number below 100, so a failed load shows nothing rather than a fabricated 0%. It sits in .risk-disclosure outside .el-table, which is the structural guarantee behind the 'never shares a row with the risk-level el-tag' rule."
  - "The dialog heading is 本次评估采用的假设 with no claim of a frozen snapshot, matching 13-04's read-time-recompute decision (RESEARCH open question 2 RESOLVED)."
patterns-established:
  - "A page-level gate on a store payload proves its own precondition with a deferred-promise test that fails when the await is removed, rather than a 'creates eventually' assertion"
  - "Spec-level el-form stub that interprets only the rules the page actually passes, so 'optional field does not block' tests the page's rule table instead of the stub"
requirements-completed: [PRF-06, PRF-08]
actuals:
  tokens: 13173  # chars/4 over the realized diff (52,693 chars across hhs-frontend-v2)
  tasks: 3
  # MEASURED: git rev-list --count plan_head_before..HEAD = 8
  # 7 task commits (test + feat per task, plus one follow-up coverage test) + this docs commit.
  commits: 8
plan_head_before: cd2ce749a0ff7419d6fcafc324dbe8055fd1fa1d
status: complete
---

# Phase 13 Plan 09: 风险评估就地补填与估算披露 Summary

The risk page now discloses what the engine will substitute, explains it in the user's own vocabulary, and lets the user repair the minimum required fields without ever blocking assessment creation — with the disclosure list and the result's disclosure list produced by the same backend function, so they cannot disagree.

## What Was Built

**`prevention/Risk.vue` (+433/−6 lines).** The page grew four things it did not have before: a mount-time completeness fetch, a three-order trigger evaluation, an in-place backfill panel, and a disclosure badge plus a read-time assumption list in the detail dialog.

`onMounted` is now `async` and ordered deliberately: `loadProfile()` (fire-and-forget), then `await completenessStore.fetchCompleteness()`, then `fetchAssessments()`, then the pre-existing GSAP timeline. The completeness await comes first among awaited calls because both the badge and the three-order rule read it, and a half-loaded page would decide on stale data.

`handleCreateClick` implements Contract 5's table, first match wins:

| Order | Condition | Result |
|---|---|---|
| 0 | `completeness === null && !loadFailed` | `await fetchCompleteness()` once, then fall through |
| 1 | `pendingAssumptions.length === 0` (incl. genuine load failure) | create directly, panel stays closed |
| 2 | assumptions non-empty AND `score < 100` | open panel, prefill optional height/weight, `scrollIntoView({ block: 'center' })`, `focusField('gender')`, **no API call** |
| 3 | assumptions non-empty AND `score === 100` (defensive) | create directly, no error chrome |

Step 0 is plain linear code, so it executes at most once per click; the "re-evaluate from step 1" is a fall-through rather than a loop. Because `fetchCompleteness()` is single-flight **and awaitable** (13-05), the await genuinely waits for the mount request instead of observing a pre-settlement `null`.

`pendingAssumptions` is `completeness.value?.assumptions ?? []` — a two-line computed, with `grep -c "f.assumption"` reporting 0. The panel renders that array verbatim, so a null-profile payload renders one generic sentence, exactly the one `RiskAssessmentVO.assumptions` will later carry.

The panel follows the Contract 5 skeleton: an `el-alert` whose only content is the `#title` slot; the assumption list and the form as **siblings** of that alert; `FormErrorSummary` inside the form; two `el-row :gutter="16"` rows (gender radio group + date picker with the locally copied `disabledDate`; then 身高（可选）/体重（可选） with `:min`/`:max` from the DTO bounds, `:precision="1"` on weight only, `placeholder="可不填"`, no `:rules`, no `:disabled`); and three actions — 保存档案并创建 (`:loading="saving"`), 直接创建（按估算）, 放弃创建 (text).

`backfillAndCreate` validates, snapshots `const existing = profileData.value`, builds the payload with `heightCm: backfill.heightCm ?? existing?.heightCm ?? undefined`, writes via `updateProfile(existing.id, …)` or `createProfile(…)`, refetches completeness, closes the panel, then creates. Validation failures go to `setErrors` + `focusSummary()` with zero toast; other failures take one `showError` toast; **neither clears `backfill` and neither closes the panel**.

`cancel()` sets `panelOpen = false`, `reset()`s the error projection and blanks the draft — zero toast, zero API call (Phase 11 D-12).

The badge lives in `div.risk-disclosure` between the panel and the table, with `v-if="!panelOpen"` and `:completeness="completeness?.score ?? null"`. `goProfileWithFocus()` pushes `/prevention/profile` with `query.focus` from `completenessStore.firstMissingKey()` — the same rule the dashboard banner uses. The dialog gained a `本次评估采用的假设` block after `</el-descriptions>`, rendered only when `currentAssessment?.assumptions?.length`.

**`RiskBackfill.spec.ts` (1,036 lines, 33 tests).** Three describe blocks mirror the three tasks. The store's fetch is mocked at the API boundary (`getCompleteness`), so the real store, its single-flight guard and its 404-synthesis branch all execute. The `el-form` stub interprets only the `required` rules the page actually passes, which is what makes "a blank optional pair does not block the save" a statement about the page's rule table rather than about the stub. `mountRisk` uses `attachTo: document.body` because the panel locates itself with `document.querySelector('.risk-estimate')`.

## Verification Results

| Check | Result |
|---|---|
| `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/RiskBackfill.spec.ts` | **33 tests, 0 failures** (plan floors were 10 / 19 / 26 per task) |
| `npm --prefix hhs-frontend-v2 run test:run` (full suite) | **435 tests / 36 files, 0 failures** (was 422 / 36 before this plan) |
| `npm --prefix hhs-frontend-v2 run build` (`vue-tsc -b && vite build`) | green after every task |
| `npm --prefix hhs-frontend-v2 run lint:check` | 0 errors; 216 warnings, all pre-existing patterns — the two new-file warnings are the `catch (error)` unused bindings that match `Profile.vue` |
| `grep -c "pendingAssumptions" Risk.vue` | **3** (≥ 3 required) |
| `grep -c "f\.assumption" Risk.vue` | **0** (the list is not rebuilt from fields) |
| `grep -c "el-tag" Risk.vue` | **4**, identical to the pre-plan count (the risk-level tag is untouched) |
| `grep -c 'gutter="20"' Risk.vue` | **0** (both rows are 16) |
| Mutation check: `await fetchCompleteness()` → `void fetchCompleteness()` | **2 tests fail** (both deferred-payload cases), confirming the await is load-bearing; reverted immediately |

## Must-Have Truth Coverage

| Must-have truth | Verification ref | Status |
|---|---|---|
| On mount, before any user interaction, the page fetches the completeness payload | `fetches the completeness payload on mount, before any click` (asserts one call with `{ silent: true }` plus the silent `getProfile`); `renders the badge on a first-visit mount with no interaction at all` | covered |
| When the payload is absent at click time the page awaits one fetch and re-evaluates rather than creating | `waits for the in-flight mount fetch instead of creating while the store is still empty` (asserts no create and no panel while pending, panel after resolve, still no create); `creates exactly once when the deferred payload turns out to have no assumptions`; mutation check above | covered |
| Empty `pendingAssumptions` → create immediately, panel never opens | `creates immediately and opens no panel when the assumption list is empty` | covered |
| Non-empty list and score < 100 → panel opens, no API call, scrolls into view, focuses gender | `opens the panel and creates nothing when assumptions exist and score is below 100`; `scrolls the panel into view and focuses the gender control on open` (spy on `scrollIntoView` with `{ block: 'center' }`, plus the focused radio's value) | covered |
| Each pending assumption is its own `li` sourced from `completeness.assumptions` | `renders one li per backend assumption with the exact sentence` (array equality against the payload) | covered |
| `pendingAssumptions` is exactly `completeness.assumptions` — one generic sentence for a new user | `renders exactly one li for a no-profile payload — never three per-field sentences` (also asserts the text contains neither `35 岁` nor `22.0`); `grep -c "f\.assumption"` == 0 | covered |
| Panel renders 保存档案并创建 / 直接创建（按估算） / 放弃创建, none disabled except the primary's loading state | `marks only the primary action as loading while the save is in flight` (primary `data-loading="true"`, both others have no `disabled` attribute) | covered |
| 直接创建 calls the create API without saving; 放弃创建 closes with zero toast and zero call | `creates once and closes the panel on 直接创建（按估算）`; `closes the panel on 放弃创建 with zero create call and zero toast` | covered |
| 保存档案并创建 validates, saves, refetches, closes, then creates | `saves the two required values, refetches completeness, then creates — in that order` (asserted via `invocationCallOrder`: write < refetch < create); `updates the existing profile by id instead of creating a second one` | covered |
| A save failure keeps the panel open, keeps every typed value, renders errors inline plus in the summary, zero toast | `keeps both typed values, keeps the panel open and shows no toast on a 400 save failure`; `shows exactly one toast on a 500 save failure and still keeps the panel and the input` | covered |
| Badge hidden while the panel is open; otherwise renders from the store score when non-null and below 100 | `hides the badge while the panel is open — one prompt at a time`; `renders the badge at score 40 with no completed assessments`; `renders no badge at score 100`; `renders no badge when the completeness load failed` | covered |
| Badge click navigates with `query.focus`; the dialog shows 本次评估采用的假设 | `pushes to the profile page with query.focus set to the first missing key`; `lists the read-time assumptions in the detail dialog, one li per sentence` | covered |
| The risk-level `el-tag` stays untouched and never shares a row with the badge | `never renders the badge inside the table — the risk-level tag keeps its own region` (asserts `.el-table .hhs-estimate-badge` absent and `.risk-disclosure .hhs-estimate-badge` present); `grep -c "el-tag"` == 4 both before and after | covered |
| Each assumption `li` is a real list item with a disc bullet and hanging indent | `.risk-estimate__list { list-style: disc; padding-left: 24px; display: flex; flex-direction: column; gap: 4px }` — the `list-style` re-declaration is required because `theme.css` globally resets `ul` | backstop (CSS is not measurable in happy-dom; visual review at 320px) |
| The panel's alert carries only a title; list and form are siblings, never children | `keeps the assumption list and the form as siblings of the alert, never its children` (asserts no `li`/`form` inside `.el-alert` and that the alert element does not contain the list or form nodes) | covered |
| One assumption renders one item and no count copy; many render a vertical list | `renders one li for a single assumption and a 4px-gap vertical list for many` | covered |
| The panel's save button stays enabled with a partially filled form and surfaces inline validation | `keeps the panel save button enabled with a partially filled form` (one inline error, no save call) | covered |
| The panel offers optional 身高/体重, prefilled from the profile, submitted only when supplied; blank is valid | `renders the optional 身高/体重 inputs with no disabled state and no validation rule`; `sends typed 身高/体重 alongside the required fields`; `still saves and still creates when both optional inputs are left blank`; `falls back to the stored profile height/weight when the prefill never ran` | covered |
| The dialog list and the panel list render identical text for the same payload | `renders identical text in the dialog and the panel for the same payload` (one generic sentence in both) | covered |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The spec's `el-row` stub swallowed `gutter`, so the 16px contract was unassertable**
- **Found during:** Task 1 (first GREEN run)
- **Issue:** `expect(rows.map(r => r.attributes('gutter'))).toEqual(['16', '16'])` received `[undefined, undefined]` — the stub declared `props: ['gutter']` but never bound it to the rendered `div`, so the assertion was checking the stub rather than the page.
- **Fix:** bound `:gutter="gutter"` in the stub template. The assertion now reads the value the page actually passed.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts`
- **Commit:** `d9c862c`

**2. [Rule 3 - Blocking] A whole-module `vue-router` mock broke the transitive `@/router` import**
- **Found during:** Task 1 (first run, `Failed Suites`)
- **Issue:** `vi.mock('vue-router', () => ({ useRoute, useRouter }))` failed with `No "createRouter" export is defined on the "vue-router" mock`, because `@/stores/auth` imports `@/router`, which calls `createRouter` at module evaluation.
- **Fix:** switched to `importOriginal` and spread the real module, overriding only `useRoute` / `useRouter`.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts`
- **Commit:** `d9c862c`

**3. [Rule 1 - Bug] Optional height/weight were typed `number | null` into a `number | undefined` DTO field**
- **Found during:** Task 2 (`vue-tsc` rejected the build)
- **Issue:** `heightCm: backfill.heightCm ?? existing?.heightCm ?? null` produced `TS2322: Type 'number | null' is not assignable to type 'number | undefined'`. `HealthProfileRequest` declares optional `number` fields, and the blank-pair spec case asserted `toBeUndefined()`, so the payload shape and the test disagreed.
- **Fix:** the coalescing chain now ends in `undefined` rather than `null`. Behaviour is unchanged server-side — `toEntity` calls `request.getHeightCm()` directly, so an absent key and an explicit null both arrive as `null`.
- **Files modified:** `hhs-frontend-v2/src/views/prevention/Risk.vue`
- **Commit:** `a660ece`

**4. [Rule 1 - Bug] Task 3's markup and styles were initially folded into the Task 1 commit**
- **Found during:** Task 2 planning (self-review of commit scope)
- **Issue:** the first GREEN implementation included the badge, the dialog list and their CSS, so `3d735e9` carried work that belongs to Task 3 — a violation of one-task-per-commit and a misleading history for the badge's provenance.
- **Fix:** reduced `Risk.vue` to the Task 1+2 surface and amended the commit (`bde874a` → `3d735e9`), then re-added the badge and dialog markup in `183df59` where the Task 3 tests demand them.
- **Files modified:** `hhs-frontend-v2/src/views/prevention/Risk.vue`
- **Commit:** `3d735e9` (amended), `183df59`

### Scope-boundary notes (no code deviation)

**5. A stray `vite.config.js` is a `vue-tsc -b` artifact, not a source change.**
- **Found during:** every `vue-tsc -b` run
- **Issue:** `tsconfig.node.json` is `composite: true`, so each build re-emits `vite.config.js` beside the TS source; it is untracked and `eslint` fails on it with `'process' is not defined`. It was already untracked before this plan.
- **Fix:** deleted before each commit so it never enters the diff. Not added to `.gitignore` (out of scope; the file is a pre-existing artifact of the build setup).
- **Commit:** —

**6. `collectInvalidFieldMessages` is duplicated rather than shared.**
- **Found during:** Task 2
- **Issue:** the helper lives in `prevention/Profile.vue` un-exported. Extracting it would require editing that file, which is outside this plan's `files_modified`.
- **Fix:** an 8-line local copy with a comment pointing at `Profile.vue`, as the plan permits.
- **Commit:** `a660ece`

**7. One must-have truth is a CSS backstop, not an automated assertion.**
- **Found during:** Task 1 review
- **Issue:** "each assumption `li` is a real list item with a disc bullet and hanging indent" cannot be measured in happy-dom; the `list-style: disc` re-declaration is nonetheless load-bearing because `theme.css` globally resets `ul`.
- **Fix:** the rule is implemented verbatim from Contract 5 and recorded here as a visual backstop (320px review).
- **Commit:** `3d735e9`

## Known Stubs

None. Every rendered string comes from a backend payload (`completeness.assumptions`, `currentAssessment.assumptions`, `fieldErrors`) or from the page's own form state; there is no placeholder copy, no hardcoded empty array reaching a renderer, and no unwired prop. The one manual check carried over from `13-VALIDATION.md` — "徽标与风险等级 `el-tag` 在同一视区的视觉共存" — is a **backstop**, not a stub: the structural guarantee (`badge outside .el-table`, `margin-bottom: 16px` on `.risk-disclosure`) is implemented and asserted, and only the final visual judgement needs a human.

## Threat Flags

None beyond the plan's register. T-13-33 (forged profile id on the backfill write) is mitigated by reusing the unchanged `updateProfile` path — the backend checks `existing.getId().equals(id)` and queries by the JWT userId — and by the fact that the page passes only `existing.id` from its own `getProfile` response. T-13-34 (assumption rendering) is mitigated structurally: sentences use mustache interpolation only (no `v-html` anywhere in the file), and the specs assert the exact backend sentence text, so a front-end paraphrase fails a test. T-13-35 (abandon path DoS) is asserted by `closes the panel on 放弃创建 with zero create call and zero toast`. T-13-36 (dialog wording vs semantics) is covered by the `本次评估采用的假设` heading plus an assertion that the block does not claim a frozen record. T-13-37 (badge on a null payload) is covered by `renders no badge when the completeness load failed`. T-13-38 (panel promise vs engine behaviour) is covered by the one-generic-sentence test and `grep -c "f\.assumption"` == 0. T-13-39 (empty store on first visit) is covered by the two deferred-payload tests and the mutation check. No new network endpoint, file-access pattern or schema change, and no package installs (T-13-SC: `EstimateBadge`, `FormErrorSummary` and the icon were already present).

## Decisions Made

1. **Two awaits, two different failures.** The mount await fixes a first visit; the click-time await fixes a fast click during mount. Removing either one leaves a real hole, and the second one is only real because 13-05 made the store's fetch return the pending promise — verified by mutation rather than by inspection.
2. **The panel and the result must quote the same function.** Rendering `completeness.assumptions` makes overstatement structurally impossible; a per-field join would have produced three sentences for a user whose engine applies one.
3. **Optional means absent, not disabled.** 身高/体重 have no rule, no `:disabled`, and no asterisk, and the payload omits them when blank. A disabled or required optional field would have contradicted D-06's 可选补 in the one place the user can act on it.
4. **The badge is a page-level placement, not a component concern.** `EstimateBadge` still takes a number and emits; the page decides when to hide it (panel open) and where to send the user (`firstMissingKey`), which keeps the component mountable in isolation and the routing rule single-sourced.
5. **Failure never costs the user their typing.** Both failure paths keep `backfill` untouched and the panel open; only the success path closes it, and only after the assumption refetch has landed.

## Self-Check: PASSED

- `hhs-frontend-v2/src/views/prevention/Risk.vue` — FOUND (433 insertions, 6 deletions)
- `hhs-frontend-v2/src/__tests__/views/RiskBackfill.spec.ts` — FOUND (1,036 lines, 33 tests)
- Commit `d9c862c` (test: mount-load + three-order trigger) — FOUND
- Commit `3d735e9` (feat: risk page panel and three-order rule) — FOUND
- Commit `a77727b` (test: backfill save path) — FOUND
- Commit `a660ece` (feat: backfill save path) — FOUND
- Commit `b2c3171` (test: badge gating + dialog list) — FOUND
- Commit `183df59` (feat: badge + dialog assumption list) — FOUND
- Commit `276d3bd` (test: alert siblings, list density, half-filled save) — FOUND
- This docs commit (the summary itself) — the measured count `cd2ce74..HEAD` = 8 covers it, so the frontmatter `commits: 8` matches
- Full suite 435 tests / 36 files green; `vue-tsc -b` and `vite build` green
