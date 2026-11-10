---
phase: 14-error-rollout
plan: 02
subsystem: ui
tags: [error-handling, css-specificity, element-plus, vue3, vitest, wcag, form-validation]

requires:
  - phase: 11-error-foundation
    provides: "the body-prefixed normalization block and the --hhs-color-error-text / --hhs-color-error-tint tokens this block reuses"
  - phase: 13-remaining-surfaces
    provides: "the control-surface baseline (theme.css:504-506 input wrapper, :672-677 select wrapper) whose !important declarations this block has to out-specify"

provides:
  - "the Phase 14 field-error block appended to theme.css: border-color for errored el-input / el-select, inset shadow for errored el-textarea, tinted halo for all three focus markers"
  - "FormItemErrorBorder.spec.ts: the .is-error anchor proven against real element-plus, plus source-level selector-inventory assertions"
  - "the four computed-style checks recorded as UAT debt for 14-14"

affects: [14-05, 14-06, 14-07, 14-09, 14-10, 14-11, 14-12, 14-13, 14-14, all-87-migrations]

actuals:
  tokens: 3560
  tasks: 2
  commits: 2
  plan_head_before: 78ca1be1f2f56453c187d3a64ba4a7ff4f23c2a7

tech-stack:
  added: []
  patterns:
    - "Specificity over load order: every error selector carries the `body` prefix and `!important`, because unplugin-vue-components re-injects EP CSS after theme.css per route chunk"
    - "Two declarations per visual state: border-color where the project already owns the border, inset box-shadow where Element Plus owns it"
    - "Class-anchor-first testing: assert the real component emits the class, then assert the stylesheet's selector inventory from source, and hand every computed-style claim to UAT"

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/components/FormItemErrorBorder.spec.ts
  modified:
    - hhs-frontend-v2/src/assets/styles/theme.css

key-decisions:
  - "Errored input and select switch border-color instead of box-shadow: theme.css:504-506 and :672-677 already give both wrappers a real 1px border plus `box-shadow: none !important`, so a shadow-based error ring is silently discarded — a border-colour change is the only declaration that survives."
  - "Errored textarea keeps an inset box-shadow because Element Plus gives it `border: none`; a border-color rule would be a no-op there. The reverse of the input/select choice is required, not a stylistic inconsistency."
  - "The daterange picker gets its own focus marker (.is-active) alongside .is-focus and .is-focused: its root node IS an .el-input__wrapper but it never passes through ElInput, so .is-focus is unreachable and rule 1's `box-shadow: none !important` would otherwise strip its only focus indicator."
  - "The focus rules declare the error border and the tint halo in the same rule rather than halo-only: a halo alone would let border-color fall back to the healthy style, so an errored-and-focused field would read as healthy. WCAG 2.4.7 is then satisfied by the presence of a halo, not by a hue difference."
  - "No --el-color-danger anywhere: #f56c6c is 2.90:1 on white, under the 3:1 non-text floor. Pinned by an assertion over the whole stylesheet, not just the new block."
  - "No .el-input-number selector added: it renders an .el-input__wrapper internally and rule 1 already covers it; a redundant selector would suggest it is a separate control."
  - "The spec's debounce flush waits 150ms because `.is-error` is driven by validateState (immediate) while the message node is driven by refDebounced(validateState, 100) — asserting the message without crossing that window fails for a timing reason unrelated to the contract."

patterns-established:
  - "Three-rule control-family split: one rule per visual mechanism (border-colour / inset-shadow / focus-halo) rather than one rule per component name"
  - "Table-driven marker inventory: a `{ family, wrapper, marker }` table makes a future fourth control family fail loudly with its own name instead of being forgotten"
  - "Explicit automation boundary: claims happy-dom cannot resolve are written into the spec header as UAT items, never faked as a getComputedStyle assertion that passes vacuously"

requirements-completed: [ERR-03, ERR-07]

coverage:
  - id: D1
    description: "`:error` on a real el-form-item yields `.el-form-item.is-error`, and clearing the error removes the class — the sole anchor the global border rules hang off"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/components/FormItemErrorBorder.spec.ts#`:error` 为非空串时渲染 .el-form-item.is-error 并显示消息；清空后类消失"
        status: pass
    human_judgment: false
  - id: D2
    description: "The stylesheet declares a real error border for input and select (border-color), an inset error ring for textarea (box-shadow), with every wrapper selector body-prefixed and !important"
    requirement: ERR-03
    verification:
      - kind: unit
        ref: "src/__tests__/components/FormItemErrorBorder.spec.ts#新增块里每条 wrapper 选择器都以 body 前缀开头"
        status: pass
      - kind: unit
        ref: "src/__tests__/components/FormItemErrorBorder.spec.ts#textarea 两条规则都在（inset 描边 + 聚焦组合）"
        status: pass
    human_judgment: false
  - id: D3
    description: "All three control families carry their matching focus marker in the stylesheet: el-input .is-focus, el-select .is-focused, daterange wrapper .is-active"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/components/FormItemErrorBorder.spec.ts#三个控件族各自都有匹配的聚焦标记选择器"
        status: pass
    human_judgment: false
  - id: D4
    description: "An errored-and-focused field keeps the error border and gains a tinted halo in the same rule, so focus never masks the error state"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/components/FormItemErrorBorder.spec.ts#规则 ③ 在同一条规则里同时声明错误描边色和 tint 光晕"
        status: pass
    human_judgment: false
  - id: D5
    description: "The four UI-SPEC 4.2 computed-style self-checks resolve correctly in a real browser: border-colour token value, textarea shadow containing inset, 3px halo with border still red for each of the three families, halo gone after blur"
    requirement: ERR-07
    verification:
      - kind: manual_procedural
        ref: "14-14 UAT — recorded in .planning/WINDOWS.md as an unrun-verify entry for this plan"
        status: unknown
    human_judgment: true
    rationale: "getComputedStyle in happy-dom returns empty strings for border-color and box-shadow, so any assertion here would pass vacuously. The halo-present/absent transition and the three-family rendering can only be observed in a real browser with the real theme loaded."

metrics:
  duration: 5min
  completed: 2026-11-10
status: complete
---

# Phase 14 Plan 02: Global field-error border declaration

**One appended stylesheet block gives errored input, select and textarea a visible error marking, with all three focus markers covered — the single declaration the 87-site inline rollout builds on.**

## Performance

- **Duration:** 5 min
- **Tasks:** 2 of 2
- **Commits:** 2
- **Files changed:** 2 (1 created, 1 modified), 282 insertions, 0 deletions

## Accomplishments

- `theme.css` gains a 59-line appending block with three rule groups: `border-color` for errored `.el-input__wrapper` / `.el-select__wrapper`, `inset box-shadow` for errored `.el-textarea__inner`, and a `3px` tint halo for `.is-focus`, `.is-active` and `.is-focused` — each selector `body`-prefixed with `!important`.
- `FormItemErrorBorder.spec.ts` proves against real Element Plus that `:error` produces `.el-form-item.is-error` and that clearing it removes the class, then pins the stylesheet's selector inventory from source.
- The table-driven marker inventory makes a future fourth control family fail with its own name rather than silently lacking a focus indicator.
- The four computed-style checks that happy-dom cannot resolve are recorded as explicit UAT debt instead of being faked.

## What the block had to work around

The reason the fix is three rules rather than one is the project stylesheet's own suppression:

| Control family | What theme.css already declares | What EP's error visual is | Consequence |
|---|---|---|---|
| `el-input` / `el-select` | real `1px` border + `box-shadow: none !important` (`:504-506`, `:672-677`) | `0 0 0 1px danger inset`, no `!important` | EP's ring is discarded → error must switch `border-color` |
| `el-textarea` | nothing — EP owns it | `border: none` + inset shadow | `border-color` is a no-op → error must recolour the inset shadow |
| `el-date-picker type="daterange"` | root IS `.el-input__wrapper` | `.el-range-editor.is-active:hover` inset halo, no `!important` | `.is-focus` unreachable; rule 1 strips the halo → `.is-active` must be listed |

Specificity, not load order, is what makes the block survive: `theme.css:1208-1216` documents that `unplugin-vue-components` re-injects EP component CSS after `theme.css` per route chunk, so "this file loads later" is a trap the project has already recorded. The new selectors reach `0,4,1` (and `0,5,1` for the focus rules) and keep `!important`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Error-message assertion failed against a 100ms debounce, not against the contract**
- **Found during:** Task 1 (first spec run)
- **Issue:** `.is-error` on the form item is computed from `validateState` directly (`form-item.vue:51`), but the `.el-form-item__error` message node is gated on `shouldShowError`, which reads `validateStateDebounced = refDebounced(validateState, 100)` (`form-item.vue:31/114`). Asserting the message text right after mount found an empty wrapper.
- **Fix:** Added a `flushErrorDebounce()` helper awaiting 150ms before the message assertion and before the post-clear assertion, with a comment naming the source lines so the wait is not mistaken for a flaky sleep.
- **Files modified:** `hhs-frontend-v2/src/__tests__/components/FormItemErrorBorder.spec.ts`
- **Commit:** 4aac540

**2. [Rule 1 - Bug] Comment-stripping helper let header prose satisfy its own assertions**
- **Found during:** Task 1 (second spec run)
- **Issue:** The first version of `readErrorBlock()` filtered lines starting with `/*`, `*` or containing `*/`. The new block's own header comments name `.is-focus`, `.is-active` and `.is-focused` in prose, so the marker assertions were satisfied by the comment rather than by a selector — exactly the tautology the plan warned against. Two tests failed on the `body`-prefix check for the same reason (comment fragments were being parsed as selectors).
- **Fix:** Rewrote the helper to slice from the block marker to the end of its own header comment, then to the next `/* ====` section boundary, and strip block comments with a regex before returning.
- **Files modified:** `hhs-frontend-v2/src/__tests__/components/FormItemErrorBorder.spec.ts`
- **Commit:** 4aac540

### Out-of-scope observations (not fixed)

- `npm --prefix hhs-frontend-v2 run lint:check` reports 215 pre-existing warnings across views outside this plan's files, and 0 errors. Pre-existing; untouched per the scope boundary.
- `npx eslint <file>` cannot lint a single file in this repo: the project uses the flat `eslint.config.js` and the v9 CLI reports the legacy-config notice. The project's own `lint:check` script is the working entry point and reports 0 errors.

## Auth gates

None. No external service, credential or `user_setup` step was required — this plan is a CSS append plus a spec.

## Verification evidence

| Command | Result |
|---|---|
| `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/components/FormItemErrorBorder.spec.ts` | 7 passed |
| `npm --prefix hhs-frontend-v2 run test:run` | 481 passed / 39 files (baseline was 474 / 38 → +7) |
| `npm --prefix hhs-frontend-v2 run build` | built in 9.88s |
| `npm --prefix hhs-frontend-v2 run lint:check` | 0 errors (215 pre-existing warnings) |
| `git diff --stat theme.css` | 59 insertions, 0 deletions |
| `grep -c "el-textarea__inner" theme.css` | 3 (≥2 required) |
| `grep -c "el-color-danger" theme.css` | 0 — unchanged from baseline |
| `grep -n "is-active"` / `"is-focused"` | new selectors at :1435 / :1436 (prior occurrences at :609/:635 are pagination/menu, :681 is the being-overridden rule) |

## Known Stubs

None. The block is complete as declared; the only deferred items are the four browser-observable computed-style checks, which are recorded in `.planning/WINDOWS.md` as an `unrun-verify` entry and handed to 14-14's UAT. They are not stubs — nothing in the delivered CSS is placeholder or unwired.

## Threat Flags

None. The block adds no network, auth, file-access or schema surface. Every selector is scoped by `.el-form-item.is-error .el-form-item__content`, so it can only reach a control Element Plus has itself marked errored (T-14-05 mitigation delivered as specified); no bare `.el-input__wrapper` override was added, so healthy fields keep their existing border. Zero packages installed, so T-14-SC is n/a as planned.

## Self-Check: PASSED

- `hhs-frontend-v2/src/assets/styles/theme.css` — FOUND
- `hhs-frontend-v2/src/__tests__/components/FormItemErrorBorder.spec.ts` — FOUND
- Commit `4aac540` — FOUND
- Commit `762b2ab` — FOUND