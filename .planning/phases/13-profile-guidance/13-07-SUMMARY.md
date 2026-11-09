---
phase: 13-profile-guidance
plan: 07
subsystem: frontend-views
tags: [profile-page, completeness-header, field-hints, focus-handoff, post-save-refresh, copywriting-contract, tdd]
requires:
  - phase: 13-profile-guidance
    provides: "ProfileCompletenessVO / ProfileFieldStatusVO with per-field reason + importance (13-05 types/api.ts)"
  - phase: 13-profile-guidance
    provides: "useProfileCompletenessStore — data, loadFailed, single-flight awaitable fetchCompleteness() (13-05)"
  - phase: 13-profile-guidance
    provides: "ProfileCompleteness.vue variant full|compact and the global .hhs-imp-badge* families in theme.css (13-06)"
  - phase: 11-error-presentation
    provides: "useFormServerErrors.focusField (CSS.escape + scrollIntoView) and the GOV.UK dual-presentation summary"
provides:
  - "prevention/Profile.vue: completeness header, 9 backend-sourced .field-hint rows with importance badges, one promise sentence, gutter 16, ?focus= consumption, hide-required-asterisk, matchMedia label-position, post-save completeness refetch, 放弃编辑 label"
  - "views/ProfileCompleteness.spec.ts — 18 tests covering hint count/copy/badges/promise/gutter/BMI/focus/post-save"
affects:
  - "13-08 (dashboard banner pushes ?focus= that this page now consumes)"
  - "13-09 (risk-page EstimateBadge pushes the same query contract)"
  - "13-10 (both-side regression gate reads this page's label normalisation)"
tech-stack:
  added: []
  patterns:
    - "Page copy is read from the backend payload at render time; the template holds zero explanation strings"
    - "Post-write refresh is a three-step await inside the try block (write → refetch profile → refetch completeness) so a displayed improvement is always server-derived"
    - "Query-param hand-off consumed once, validated against payload membership, then cleaned with router.replace"
key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/ProfileCompleteness.spec.ts
  modified:
    - hhs-frontend-v2/src/views/prevention/Profile.vue
    - hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts
key-decisions:
  - "The mount fetch lives in Task 1, not Task 2. Task 1's acceptance criteria assert 9 rendered hints and a rendered header, both of which need a resolved payload; deferring the fetch would have made every hint assertion unreachable at Task 1 end (plan-check W2). Task 2 only extends the same onMounted."
  - "The completeness refetch sits strictly inside handleSubmit's try block, after fetchProfile() and before editing.value = false. A failed save therefore issues no second getCompleteness and leaves the previous display intact — an unearned improvement would be a repudiation bug (T-13-29)."
  - "Consuming ?focus= opens edit mode first. The form is :disabled=\"!editing\", so focus() on a disabled control is a no-op; the 完善档案 link exists to fill a field, so painting an outline without an editable control would have satisfied the letter of the contract and failed its purpose. Added as Rule 1 after the spec showed read mode was the common arrival state."
  - "Hint copy comes from fields[].reason, never a local constant. A local copy would be a second source of truth for statements the risk engine makes; the spec proves the wiring with REASON-<key> sentinel strings so a hardcoded string fails immediately."
  - "The BMI form item always renders with :value=\"bmi ?? '—'\" and a v-if on the classification tag, so getBmiType('—') is never reachable while the hint keeps a stable home."
  - "`.hhs-imp-badge*` is not redeclared here — it is global in theme.css from 13-06, and a scoped copy would drift. The acceptance grep asserts zero occurrences of the CSS selector in this file while the template still builds the class-name strings."
  - "The isNarrow matchMedia listener is removed in onUnmounted, because the plan did not specify teardown and a leaked MediaQueryList listener would outlive the page."
patterns-established:
  - "Sentinel-string fixtures (REASON-<key>) to prove DOM text originates in the payload rather than a template literal"
  - "Read-only-aware focus hand-off: a navigation intent that implies input must unlock the input before focusing"
  - "Per-task TDD split (test commit then feat commit) for auto+tasks that carry a <behavior> block"
requirements-completed: [PRF-01, PRF-02, PRF-03]
actuals:
  tokens: 10390  # chars/4 over the realized diff (41,558 chars across the three files)
  tasks: 3
  # MEASURED: git rev-list --count 2f5fc36..HEAD = 7
  # 6 task commits + this docs commit.
  commits: 7
  plan_head_before: 2f5fc36ea11aaba2c5e174133ce3844295a89352
status: complete
---

# Phase 13 Plan 07: 档案页完整度头部、九条字段说明与聚焦交接 Summary

The health-profile page now opens with a server-derived completeness header and nine explained fields whose copy is read from the backend payload at render time, promises once that recommended and optional fields are not required, and re-derives its own score the instant a save succeeds — so the fill → see-improvement loop never appears frozen.

## What Was Built

**`views/prevention/Profile.vue` (+275 lines).**

Template, in reading order inside the card body: `ProfileCompleteness` (variant `full`, gated on `completeness`), then the single `.profile-promise` paragraph `推荐与可选字段不填也可保存，不影响核心功能使用。`, then the existing `FormErrorSummary`, then the form. Nine `el-form-item`s each carry a second row after their control:

```html
<p class="field-hint">
  <span class="hhs-imp-badge hhs-imp-badge--recommended">推荐</span>
  <span class="field-hint__text">{{ hintFor('heightCm') }}</span>
</p>
```

`hintFor(key)` returns `fields[].reason` from the store payload; `importanceClass(key)` builds `hhs-imp-badge hhs-imp-badge--<importance>`; `importanceLabel(key)` maps required/recommended/optional to 必需/推荐/可选. No explanation string exists in the template.

All three rows moved from `:gutter="20"` to `:gutter="16"` (EP halves the gutter per side, so 20 rendered 10px — off the 4-multiple grid). The BMI column lost its `v-if`, its readonly input became `:value="bmi ?? '—'"`, and the classification `el-tag` gained `v-if="bmi"` so `getBmiType('—')` is unreachable. The `el-form` gained `:hide-required-asterisk="true"` and `:label-position="isNarrow ? 'top' : 'right'"`; `isNarrow` comes from a module-scope `window.matchMedia('(max-width: 639px)')` with an `addEventListener('change')` handler removed in `onUnmounted`. The edit-mode secondary button's label changed from `取消` to `放弃编辑`; `cancelEdit` is untouched.

Script: `useProfileCompletenessStore()` plus `completeness = computed(() => completenessStore.data)`; `route`/`router` and `focusTarget`. `handleSubmit`'s success path is now `updateProfile/createProfile → fetchProfile() → completenessStore.fetchCompleteness() → editing = false`, all inside the `try` so a rejection skips the refetch entirely.

`onMounted` is async and does `await completenessStore.fetchCompleteness()` **before** `fetchProfile()` and the unchanged GSAP timeline, then `await nextTick(); await applyFocusTarget()` last. `applyFocusTarget()` requires a non-empty string query, a resolved payload, a `fields[]` entry with that key, and `filled === false`; on a hit it sets `focusTarget`, opens edit mode if the form is read-only (flushing the DOM), calls the already-destructured `focusField(key)`, and `void router.replace({ query: {} })`. `onFocusOut` clears `focusTarget` when focus leaves the target's `[data-field]` container.

Scoped CSS: the Contract 2 rules verbatim (600-weight labels, 16px item margin, 14px input text, `.field-hint` with `flex-basis: 100%`, `.field-hint__text` with `flex: 1; min-width: 0`, the `is-focus-target` 2px `--accent-cool` outline, `.profile-promise`), the Contract 6 button normalisation, and the 639px label rule. `.hhs-imp-badge*` is deliberately absent.

**`__tests__/views/ProfileCompleteness.spec.ts` (new, 18 tests).** Mocks `@/api/prevention` (four methods), `vue-router` (`useRoute` reading a mutable `routeQuery`, `useRouter` returning shared `replace`/`push` spies), `element-plus` (partial, `ElMessage` only) and `@/composables/useGsap`. The `ProfileCompleteness` stub exposes its props so the header's payload can be asserted directly; `el-row` renders `:data-gutter` so "gutter is 16" is provable from the DOM.

**`__tests__/views/ProfileErrorSample.spec.ts` (+29 lines).** `getCompleteness` added to the API factory, a `vue-router` mock added, `setActivePinia(createPinia())` added to `beforeEach` (the page now reads a store), and `getCompleteness` stubbed with `fields: []` plus the full VO shape. No existing assertion changed.

## Verification Results

| Check | Result |
|---|---|
| `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/ProfileCompleteness.spec.ts` | **18 tests, 0 failures** (plan floor was 8 for Task 1, 14 after Task 2) |
| `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/views/ProfileErrorSample.spec.ts` | **3 tests, 0 failures**, assertions byte-identical |
| Both specs together | 21 tests, 0 failures |
| `npm --prefix hhs-frontend-v2 run test:run` (full suite) | **378 tests / 34 files, 0 failures** (was 360 before this plan) |
| `npm --prefix hhs-frontend-v2 run build` (`vue-tsc -b && vite build`) | green after the final commit |
| `npm --prefix hhs-frontend-v2 run lint:check` | 0 errors, 202 pre-existing warnings; the three files lint clean except three pre-existing `no-explicit-any` warnings on untouched lines of `ProfileErrorSample.spec.ts` (diff is insert-only) |
| `grep -c 'gutter="20"'` on Profile.vue | **0** |
| `grep -c ':gutter="16"'` on Profile.vue | **3** |
| `grep -c '\.hhs-imp-badge--'` on Profile.vue | **0** (the badge CSS stays global; the page only builds class-name strings) |
| `grep -Ec '>(取消\|确定\|关闭)<'` on Profile.vue | **0** |
| `grep -c '推荐与可选字段不填也可保存，不影响核心功能使用。'` on Profile.vue | **1** |
| `grep -c 'class="field-hint"'` on Profile.vue | **9** |
| `grep -c "is-focus-target': focusTarget"` on Profile.vue | **9** |
| `grep -c 'v-html'` on Profile.vue | **0** (T-13-26: reasons are interpolated as text) |
| `grep -c 'required: true'` on Profile.vue | **2** (gender, birthDate — unchanged) |
| `grep -c "vi.mock('vue-router'"` on ProfileErrorSample.spec.ts | **1** |
| `grep -c 'getCompleteness'` on ProfileErrorSample.spec.ts | **3** (factory + accessor + stub) |

## Must-Have Truth Coverage

| Must-have truth | Verification ref | Status |
|---|---|---|
| Exactly 9 `.field-hint` elements, one per backend `fields[]` entry, each text sourced from that entry's `reason` | `renders exactly nine field hints, one per payload entry`; `sources every hint text from the payload reason, never from a local constant` (loops all 9 keys asserting `REASON-<key>` inside its own `[data-field]`) | covered |
| Badge class per importance: required for gender/birthDate, recommended for height/weight/BMI, optional for the rest | `maps each field importance to its badge class and Chinese label` — asserts `--required` + 必需 on gender and birthDate, `--recommended` + 推荐 on heightCm, `--optional` + 可选 on bloodType | covered |
| The promise sentence appears exactly once, after the completeness block and before the error summary | `renders the promise sentence exactly once, right after the completeness block` — asserts length 1, exact text, and the index ordering stub-completeness → profile-promise → el-form | covered |
| Every `el-row` uses gutter 16; no gutter 20 remains | `uses gutter 16 on every row, never 20` (three `data-gutter` values all `'16'`) plus the two greps above | covered |
| Form sets `hide-required-asterisk` and switches `label-position` below 639px via matchMedia, without altering `el-form-item` display | `hides the required asterisk and keeps the form-item display untouched` asserts `hideRequiredAsterisk === true` and `labelPosition === 'right'`; the 639px switch is the `isNarrow` ref driven by matchMedia, and no CSS rule in the file sets `el-form-item { display: … }` | covered (narrow branch is a backstop: happy-dom has no layout, so the `matchMedia` `matches` value cannot be flipped meaningfully) |
| The BMI item always renders (no `v-if`); when not computable it shows the placeholder and renders no classification tag | `keeps the BMI item always rendered and shows the placeholder instead of classifying it` — asserts the item and its hint exist with no `.el-tag` | covered |
| The completeness block renders only after data arrives; no skeleton; not in any GSAP timeline | `renders no completeness block at all before the payload arrives` (pending promise → no stub, zero hints); the timeline still targets `.el-card`, `.header-actions`, `.el-form .el-row` only | covered |
| `?focus=heightCm` applies `is-focus-target`, calls `focusField('heightCm')` then `router.replace({ query: {} })`; unknown or already-filled keys are silently ignored | `applies is-focus-target to the queried field when it is still unfilled`; `cleans the query exactly once after a successful focus`; `ignores a queried key that is already filled`; `ignores an unknown key without throwing`; `does nothing at all when no focus query is present`; `applies no class and no replace when the completeness load failed` | covered |
| A successful save re-derives completeness from the server before edit mode closes, so header/tier/meter/badges change immediately | `refetches completeness after a successful save so the header moves 40 → 70` — asserts `getCompleteness` called twice, rendered score `70%`, and the three filled keys | covered |
| The refetch is success-path only; a failed save leaves the prior display and does not claim an improvement | `skips the refetch entirely on a failed save and leaves the previous header intact` — asserts one `getCompleteness` call and the header still `40%` | covered |
| The edit-mode secondary button is `放弃编辑`; no bare 取消/确定/关闭 label anywhere | `labels the secondary edit-mode button 放弃编辑, with no bare single-word label` (asserts every header button label is not one of the three) plus the acceptance grep | covered |
| The three ProfileErrorSample tests stay green with the router and getCompleteness mocks; inline-error count stays 2 | `ProfileErrorSample.spec.ts` → 3/3 green, the `toHaveLength(2)` assertions untouched (`git diff` shows insert-only) | covered |
| `.field-hint` uses `flex-basis: 100%` with `flex: 1; min-width: 0` on its text, so a 24-character explanation wraps instead of sharing the control's row | `.field-hint { flex-basis: 100% }` + `.field-hint__text { flex: 1; min-width: 0 }` in the scoped block; the 9 hints render under their controls in the DOM. Wrapping itself is a backstop — happy-dom does not compute layout | backstop (visual review) |
| Narrow layout switches labels via `label-position`; `el-form-item` display is never changed | `isNarrow` + `:label-position` binding asserted above; grep confirms no `el-form-item { display:` rule | covered |
| An unfilled field still renders its hint, so no control is left unexplained | `renders exactly nine field hints…` runs against an all-unfilled fixture; the badge test reads the same unfilled state | covered |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Consuming `?focus=` without opening edit mode left the field unfocusable**
- **Found during:** Task 2 (spec run against the real component)
- **Issue:** `el-form` carries `:disabled="!editing"`, so a profile that already exists mounts read-only. `focusField` finds the container and calls `control.focus()`, but a disabled input silently refuses focus — the page would have drawn the `is-focus-target` outline around a control the user cannot type into. That is the common arrival state for every 完善档案 link, so the plan's `<done>` ("visibly outlined, focused") would have been met only in name.
- **Fix:** `applyFocusTarget()` is now async: on a hit it sets `focusTarget`, flips `editing = true` when the form is read-only, `await nextTick()`, then focuses and cleans the query. `onMounted` awaits it.
- **Files modified:** `hhs-frontend-v2/src/views/prevention/Profile.vue`, `hhs-frontend-v2/src/__tests__/views/ProfileCompleteness.spec.ts`
- **Commit:** `7a96338`

**2. [Rule 3 - Blocking] `ProfileErrorSample.spec.ts` threw on the missing active Pinia**
- **Found during:** Task 3 (first run after adding the router and API mocks)
- **Issue:** All three tests failed with `[🍍]: "getActivePinia()" was called but there was no active Pinia`, thrown from `useProfileCompletenessStore()` at setup. The page previously had no store, so the spec never needed one. The plan listed the router mock and the `getCompleteness` mock but not the Pinia bootstrap — without it the suite could not even mount.
- **Fix:** Added `setActivePinia(createPinia())` as the first line of `beforeEach`, matching every other view spec in the repo. No assertion changed.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts`
- **Commit:** `1ff4dcf`

**3. [Rule 3 - Blocking] A named stub was needed for `findComponent({ name: 'el-form' })`**
- **Found during:** Task 1 (form-props test)
- **Issue:** The `el-form` stub declared its props but no `name`, so `findComponent({ name: 'el-form' })` returned an empty wrapper and `props()` threw `Cannot call props on an empty VueWrapper`. The assertion — not the component — was unreachable.
- **Fix:** Gave the stub `name: 'el-form'`.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/ProfileCompleteness.spec.ts`
- **Commit:** `27ccd03`

### Scope-boundary notes (no code deviation)

**4. The narrow-label branch cannot be exercised in happy-dom.**
- **Found during:** Task 1 review
- **Issue:** `window.matchMedia` exists in happy-dom but its `matches` value is not tied to a layout viewport, so a test cannot prove the `label-position` flip at 639px. The rule is implemented exactly as Contract 2 specifies and the wide-viewport value is asserted; the narrow value is a visual backstop, recorded in the coverage table rather than silently omitted.
- **Fix:** None; no test was loosened to paper over it.

**5. Three `no-explicit-any` warnings remain in `ProfileErrorSample.spec.ts`.**
- **Found during:** final lint
- **Issue:** `wrapper.vm as any` appears three times on lines this plan never touched (the diff is insert-only). The repo-wide count is unchanged at 202.
- **Fix:** None — pre-existing, out of scope per the scope boundary. Recorded so the warning count is not mistaken for a regression.

## Known Stubs

None. Every rendered string comes from the `ProfileCompletenessVO` payload or from a fixed UI-SPEC label that this plan was explicitly told to type (`放弃编辑`, `—`, the promise sentence). No placeholder copy, no hardcoded empty array reaching a renderer, no unwired prop. The two unrun visual checks (hint wrapping at narrow widths, the 639px label flip) are **backstops**: the CSS rules that address them are implemented, they simply cannot be measured without a layout engine.

## Threat Flags

None beyond the plan's register. T-13-25 is enforced structurally — `applyFocusTarget` requires the key to exist in `fields[]` and be `filled === false` before touching anything, and the selector is built inside `useFormServerErrors.focusField` (CSS.escape, invalid selectors swallowed), so an attacker-supplied key is a no-op. T-13-26 holds: `grep -c 'v-html'` is 0 and every reason renders through mustache interpolation. T-13-27 is asserted (one `.profile-promise`). T-13-29 is asserted in both directions (refetch on success, none on failure). T-13-SC: no package installs; `matchMedia` is a platform API.

## Decisions Made

1. **The mount fetch belongs to Task 1.** Task 1's acceptance criteria assert 9 rendered hints and a rendered header; both need a resolved payload, so deferring the fetch to Task 2 would have made them unreachable at Task 1 end. Task 2 only extends the same `onMounted` — no second fetch exists.
2. **The post-save refetch is inside the `try`, not after it.** `await fetchProfile(); await completenessStore.fetchCompleteness(); editing.value = false` in that order means the user sees the new score the instant the form returns to read mode, and a rejection leaves the old display untouched.
3. **A focus intent that implies input must unlock the input.** Opening edit mode before focusing is the difference between honouring the contract's letter and its purpose; recorded as a Rule 1 fix rather than a silent improvement.
4. **Hint copy is payload-owned.** The sentinel-string fixture (`REASON-<key>`) makes the ownership machine-checkable: hardcoding a reason in the template breaks nine assertions, not zero.
5. **`.hhs-imp-badge*` stays global.** The acceptance grep asserts the selector is absent from this file while the template still composes the class names — so a future scoped copy is caught rather than tolerated.

## Self-Check: PASSED

- `hhs-frontend-v2/src/views/prevention/Profile.vue` — FOUND (275 lines added)
- `hhs-frontend-v2/src/__tests__/views/ProfileCompleteness.spec.ts` — FOUND (18 tests)
- `hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts` — FOUND (3 tests, insert-only diff)
- Commit `660b13e` (feat: header, hints, post-save refresh) — FOUND
- Commit `5031f2b` (test: RED for ?focus=) — FOUND
- Commit `e25a83d` (feat: ?focus= consumption) — FOUND
- Commit `1ff4dcf` (test: ProfileErrorSample mocks) — FOUND
- Commit `7a96338` (fix: edit mode before focus) — FOUND
- Commit `27ccd03` (test: form props) — FOUND
- Measured commit count `2f5fc36..HEAD` = 7 (6 task + 1 docs), matching the frontmatter `commits: 7`
