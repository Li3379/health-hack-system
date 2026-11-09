---
phase: 13-profile-guidance
plan: 08
subsystem: frontend-views
tags: [dashboard, banner-mount, compact-completeness, dismissal, focus-handoff, gsap-exclusion, tdd]
requires:
  - phase: 13-profile-guidance
    provides: "useProfileCompletenessStore — data / loadFailed / dismissedAt, single-flight awaitable fetchCompleteness(), dismiss() as the only writer of hhs_profile_reminder_dismissed_at (13-05)"
  - phase: 13-profile-guidance
    provides: "ProfileReminderBanner.vue — self-drawn div.profile-banner, score gate < 60, emits complete(focusKey) / dismiss (13-06)"
  - phase: 13-profile-guidance
    provides: "ProfileCompleteness.vue variant full|compact plus the global .hhs-completeness-meter / .hhs-tier-pill families (13-06)"
  - phase: 13-profile-guidance
    provides: "firstMissingKeyOf() / missingFieldsOf() — the single required→recommended→optional priority implementation (13-05)"
provides:
  - "dashboard/Index.vue is the single mount point for ProfileReminderBanner in the whole codebase (D-05 / ROADMAP success criterion 4)"
  - "Banner placement: first child of .dashboard after .welcome-section, before the stats skeleton"
  - "ProfileCompleteness variant=compact inside the health-score card, below .score-display"
  - "goProfileWithFocus(focusKey) → router.push({ path: '/prevention/profile', query: { focus } }) without writing the dismissal timestamp"
  - "Mount-time completenessStore.fetchCompleteness() as the first unawaited statement of onMounted"
  - "Contract 6 dashboard button normalisation (600 14px/1.5 primary, 400 secondary)"
  - "DashboardProfileBanner.spec.ts — 24 tests: single instance, placement, score gate, focus handoff, dismissal, no-refetch, failure scoping, score-60 asymmetry, GSAP exclusion, aria-labelledby"
affects:
  - "13-09 (risk/Score pages push the same ?focus= query contract this plan consumes on the dashboard side)"
  - "13-10 (both-side regression gate reads this page's button normalisation and banner gate)"
tech-stack:
  added: []
  patterns:
    - "Single mount point as a structural guarantee: 'at most one prompt across pages' is true because only one view imports the component, not because every other page remembers to omit it"
    - "Computed reactivity over non-reactive storage: `void store.dismissedAt` inside a computed is the documented trick that makes a localStorage-backed isDismissed() re-evaluate after dismiss()"
    - "Deliberate gate asymmetry between an informational surface (compact row: `completeness != null`) and a prompting surface (banner: `score < 60 && !dismissed`), pinned by a test at exactly 60"
    - "New UI classes are kept out of every existing GSAP selector string by construction, so a late-mounting element cannot be frozen at autoAlpha: 0 in a hidden tab"
    - "Real component over a stub when the assertion IS the component's own gate — a stub implementing `< 60` would make the success criterion an assertion about the stub"
key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts
  modified:
    - hhs-frontend-v2/src/views/dashboard/Index.vue
key-decisions:
  - "The banner is mounted with a real ProfileReminderBanner rather than a stub. The plan suggested a stub that renders only when score < 60, but then 'score 60 does not nag' (ROADMAP success criterion 5) would be a test of the stub's own condition — proving nothing about the shipped gate. 13-06 already unit-tests the component in isolation (16 tests); the dashboard spec needs to prove the page wires it and that the page-level gate holds."
  - "The compact row is a stub in the spec because its internals are covered by 13-06's 23 tests; the page-level contract is only 'variant is compact' plus 'it is gated on completeness != null', both of which the stub proves."
  - "`.score-content` was changed from a single-line centered flex row to a column with gap 16 so the compact row sits BELOW the score display instead of competing with the score ring. UI-SPEC §视觉焦点 names the ring as the card's primary anchor and the cross-surface rule says the banner/row yields to it. A test asserts the row is a later sibling of `.score-display` under the same parent."
  - "The mount fetch is deliberately NOT awaited and is the first statement of onMounted: awaiting it would delay the existing GSAP entrance timeline behind a guidance-only request. The store's single-flight guard already collapses a concurrent 13-07/13-09 mount into one HTTP call, so nothing is lost by not awaiting."
  - "goProfileWithFocus never calls dismiss(). The user may abandon the profile page; the reminder must survive so it reappears next visit — a test asserts localStorage stays null after the CTA click."
  - "bannerDismissed touches `void completenessStore.dismissedAt` inside the computed. isDismissed() reads localStorage, which Pinia cannot track; without the touch, a dismissal would not invalidate the computed and the banner would linger until some unrelated state changed."
  - "The spec asserts the dismissal path leaves getCompleteness at exactly 1 call (T-13-30). Dismissal is pure local state, so repeated closing cannot amplify requests."
  - "Failure scoping is asserted positively: on a rejected getCompleteness the banner and compact row vanish while .welcome-title, .stats-grid, .content-grid and .quick-entry survive. T-13-31 (a fabricated 0% telling a complete user they have nothing filled) is also covered by the null-payload test, while a genuine 0% payload still renders the banner — 404 is a legitimate state, failure is not."
  - "A GSAP-exclusion test walks every mocked animation helper's call arguments and fails if any selector string contains 'profile-banner' or 'completeness'. This is stronger than a grep: it also catches a future helper being wired to the new class names."
patterns-established:
  - "Structural single-instance: one import site in src/views, verified by a directory-wide grep, makes a cross-page UX promise true by construction"
  - "Gate-asymmetry test: a score value where two co-located surfaces must disagree (60 → row yes, banner no) pins the intentional difference and blocks a future 'unify the gates' refactor"
  - "Selector-argument inspection as an animation-participation proof, complementing grep-based class checks"
requirements-completed: [PRF-01, PRF-04, PRF-07]
coverage:
  - id: D1
    description: "dashboard/Index.vue is the only view that renders ProfileReminderBanner; no other page imports it"
    requirement: PRF-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#renders exactly one banner for a genuinely incomplete profile"
        status: pass
      - kind: other
        ref: "grep -rln ProfileReminderBanner hhs-frontend-v2/src/views/ → exactly one file (dashboard/Index.vue)"
        status: pass
    human_judgment: false
  - id: D2
    description: "The banner sits after the welcome section and before the stats grid, so it is the first content block on the page"
    requirement: PRF-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#places the banner after the welcome section and before the stats grid"
        status: pass
    human_judgment: false
  - id: D3
    description: "The compact completeness row renders inside the health-score card after the score display and links to /prevention/profile with no focus query"
    requirement: PRF-01
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#renders the compact row inside .score-content after the score display, not beside it"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#renders the compact row with variant=\"compact\" and the same payload as the banner"
        status: pass
      - kind: other
        ref: "ProfileCompleteness.vue:147 router-link to=\"/prevention/profile\" carries no query"
        status: pass
    human_judgment: false
  - id: D4
    description: "完善档案 navigates to /prevention/profile with query.focus equal to the required-first missing key, and does not write the dismissal timestamp"
    requirement: PRF-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#pushes to /prevention/profile with query.focus set to the required-first missing key"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#does not write the dismissal timestamp when the user abandons the profile page"
        status: pass
    human_judgment: false
  - id: D5
    description: "稍后提醒 and the close × both dismiss through the store, hiding the banner immediately for seven days without a refetch"
    requirement: PRF-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#writes a parseable timestamp and hides the banner immediately"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#dismisses through the close × as the same action"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#does not refetch completeness on dismissal (T-13-30: no request amplification)"
        status: pass
    human_judgment: false
  - id: D6
    description: "A completeness fetch failure renders neither the banner nor the compact row — no 0% is fabricated — while the rest of the dashboard is unaffected"
    requirement: PRF-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#hides both guidance surfaces on failure but keeps the welcome, stats and content grid"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#never renders a fabricated 0% when the payload is still null"
        status: pass
    human_judgment: false
  - id: D7
    description: "The banner is outside every GSAP timeline and is not an el-alert; its role=status region is named by aria-labelledby against its own dynamic title"
    requirement: PRF-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#never hands a banner or completeness selector to a GSAP helper"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#renders the banner as a self-drawn div, never as el-alert"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#labels the status region by its own title, not a static aria-label"
        status: pass
    human_judgment: false
  - id: D8
    description: "The page still renders its existing welcome, stats, content grid and quick-entry blocks unchanged alongside the new guidance surfaces"
    requirement: PRF-01
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#keeps the existing score card content alongside the compact row"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#hides both guidance surfaces on failure but keeps the welcome, stats and content grid"
        status: pass
      - kind: integration
        ref: "npm --prefix hhs-frontend-v2 run test:run → 35 files / 402 tests passed (whole-suite regression)"
        status: pass
    human_judgment: false
  - id: D9
    description: "The banner body wraps its field list instead of truncating, and its action area wraps to a full-width row with a 44px left inset below 768px"
    requirement: PRF-07
    verification:
      - kind: other
        ref: "ProfileReminderBanner.vue:170 flex-wrap: wrap and :176 padding-left: 44px under @media (max-width: 768px); body has min-width: 0 with no ellipsis/clamp"
        status: pass
    human_judgment: true
    rationale: "Wrapping and narrow-viewport reflow are layout outcomes that only a rendered viewport can confirm; the source declarations are verified but the visual result at 767px is not machine-checked by the unit suite."
  - id: D10
    description: "The banner never carries a high-saturation fill — surface-1 with a single border — so it does not compete with the health-score circle"
    requirement: PRF-07
    verification:
      - kind: other
        ref: "ProfileReminderBanner.vue:96-97 background: var(--surface-1); border: 1px solid var(--border)"
        status: pass
    human_judgment: true
    rationale: "Visual precedence against the HealthScoreCircle is a judgment about the composed page, not a token check; the declaration proves no saturated fill is used but not that the ring still reads as the focal point."
  - id: D11
    description: "The compact row's head wraps so the score figure and tier pill never break mid-token"
    requirement: PRF-01
    verification:
      - kind: other
        ref: "ProfileCompleteness.vue:157-162 .completeness__head uses display:flex + flex-wrap: wrap + gap: 8px; .hhs-tier-pill sets white-space: nowrap (theme.css:1322)"
        status: pass
    human_judgment: false
  - id: D12
    description: "The banner gate is score < 60 while the informational compact row has no score gate — at exactly 60 the row renders and the banner does not"
    requirement: PRF-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#keeps the compact row at score 60 while the banner is gone — the deliberate asymmetry"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts#renders nothing at exactly 60"
        status: pass
    human_judgment: false
actuals:
  tokens: 6847  # chars/4 over the realized diff (27,386 chars across the two files)
  tasks: 2
  commits: 2  # MEASURED: git rev-list --count 2dca6cb..HEAD
  plan_head_before: 2dca6cb81799e67c79f0df1ea0b1b8a63acbf026
duration: 12min
completed: 2026-11-09
status: complete
---

# Phase 13 Plan 08: Dashboard reminder banner and compact completeness row

The dashboard became the codebase's single home for the profile reminder, gated at `score < 60` and wired to a focus-carrying hand-off, while the health-score card gained a compact completeness row that informs without prompting.

## Performance

- **Duration:** 12 min
- **Started:** 2026-11-09T13:30:31Z
- **Completed:** 2026-11-09T13:42:00Z
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments

- `dashboard/Index.vue` is now the only file in `src/views` that imports `ProfileReminderBanner`, so "refresh several pages, at most one prompt" holds by construction rather than by discipline.
- The banner mounts as the first child after `.welcome-section` and reads the same store payload as the compact row, so the two surfaces can never disagree about the score.
- 完善档案 pushes `/prevention/profile?focus=<required-first missing key>` and deliberately leaves the dismissal timestamp unwritten, so an abandoned visit still gets reminded; 稍后提醒 and × both go through `store.dismiss()` and produce zero extra HTTP calls.
- Failure is scoped: a rejected completeness fetch hides both guidance surfaces and leaves the welcome heading, stats grid, content grid and quick-entry block untouched — no fabricated 0% anywhere.
- The compact row sits below the score ring in a column layout, keeping the ring as the card's focal point per the UI-SPEC cross-surface rule, and its gate is `completeness != null` rather than `< 60` — pinned by a test at exactly 60.

## Task Commits

Each task was committed atomically:

1. **Task 1: dashboard mount → banner → 完善档案 navigates with focus** - `3f90481` (feat)
2. **Task 2: failure-state and no-refetch guarantees, plus GSAP non-participation** - `e52fa39` (test)

_Note: Task 1 is a `type="tracer"` task; auto mode was active, so the tracer feedback gate re-ran the task's `<verify>` end-to-end before expanding. It passed (15/15), so no checkpoint was raised._

## Files Created/Modified

- `hhs-frontend-v2/src/views/dashboard/Index.vue` — banner mount, compact row, `goProfileWithFocus`, `bannerDismissed`, unawaited mount fetch, Contract 6 button normalisation, `.score-content` column layout, animation-exclusion comment.
- `hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts` — 24 tests across single-instance, placement, score gate, compact row, focus handoff, dismissal, failure scoping and animation exclusion.

## Decisions Made

- **Real banner component in the spec, not the stub the plan suggested.** A stub implementing `score < 60` would turn ROADMAP success criterion 5 into an assertion about the stub. 13-06 already unit-tests the component's gate; this spec must prove the page wires it correctly.
- **Compact row is a stub** because its internals carry 23 tests from 13-06; the page-level contract is only `variant="compact"` plus the `completeness != null` gate.
- **`.score-content` changed to a column** (was a single-line centered row) so the compact row lands below the ring instead of beside it — the UI-SPEC declares the ring the primary anchor and says the banner/row yields to it.
- **Mount fetch is unawaited and first**, so a guidance-only request never delays the existing GSAP entrance timeline. The store's single-flight guard already collapses a concurrent 13-07/13-09 mount into one HTTP call.
- **`goProfileWithFocus` never dismisses.** An abandoned profile visit must not burn the seven-day window; a test asserts the key stays null after the CTA click.
- **`void completenessStore.dismissedAt` inside `bannerDismissed`** is load-bearing: `isDismissed()` reads localStorage, which Pinia cannot track, so without the touch the computed would not invalidate on dismissal.

## Deviations from Plan

None - plan executed exactly as written. The one judgment call the plan left open ("prefer not awaiting" for the mount fetch) was resolved toward not awaiting, as the plan's stated preference.

## Issues Encountered

The plan's suggested stub for `ProfileReminderBanner` would have made the score-gate assertions self-referential. Resolved inside Task 1's action by mounting the real component (documented above), which is why Task 1 landed 15 tests rather than the suggested 6 and the banner-gate assertions actually exercise shipped code.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 13-09 can push the same `?focus=` query contract from the risk/score pages; this plan's CTA already produces it and 13-07 already consumes it.
- The dashboard's Contract 6 button normalisation is in place for 13-10's both-side regression gate.
- No blockers. The known limitation stands as documented: `hhs_profile_reminder_dismissed_at` is not user-scoped and `logout()` does not clear it.

## Known Stubs

None. The spec's `ProfileCompleteness` stub is a test double for a component fully covered by 13-06, not a production stub.

## Self-Check: PASSED

- `hhs-frontend-v2/src/views/dashboard/Index.vue` — FOUND
- `hhs-frontend-v2/src/__tests__/views/DashboardProfileBanner.spec.ts` — FOUND
- commit `3f90481` — FOUND
- commit `e52fa39` — FOUND

---
*Phase: 13-profile-guidance*
*Completed: 2026-11-09*
