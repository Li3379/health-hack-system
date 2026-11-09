---
phase: 13-profile-guidance
plan: 06
subsystem: frontend-components
tags: [completeness-meter, accessibility, self-drawn-components, css-tokens, tdd, copywriting-contract]
requires:
  - phase: 13-profile-guidance
    provides: "ProfileCompletenessVO {score, level, filledCount, totalCount, hasProfile, assumptions, fields[]} and ProfileFieldStatusVO (13-05 types/api.ts)"
  - phase: 13-profile-guidance
    provides: "firstMissingKeyOf() / missingFieldsOf() / PROFILE_FIELDS in utils/profileFields.ts — the single priority-rule implementation (13-05)"
  - phase: 13-profile-guidance
    provides: "useProfileCompletenessStore data/dismissedAt for the banner's score gate and dismissal window (13-05)"
  - phase: 11-error-presentation
    provides: "Theme tokens (--text-1/--text-2/--accent-*/-soft/--surface-3/--radius-*/--dur-fast/--ease) and the Phase 11/12 typography discipline (400/600 only)"
provides:
  - "theme.css global families: .hhs-completeness-meter (+ __seg, --compact, .is-filled), .hhs-tier-pill (+ --basic/--full), .hhs-imp-badge (+ --required/--recommended/--optional), .hhs-estimate-badge (+ __action) — declared once for four scoped surfaces"
  - "components/common/ProfileCompleteness.vue — variant full|compact, null-safe, impactLine with a hasProfile-first branch chain"
  - "components/common/ProfileReminderBanner.vue — self-drawn div.profile-banner, score gate < 60, emits complete(focusKey) / dismiss"
  - "components/common/EstimateBadge.vue — self-drawn button.hhs-estimate-badge, emits complete, null-safe"
  - "3 specs: ProfileCompleteness (23 tests), ProfileReminderBanner (16), EstimateBadge (10)"
affects:
  - "13-07 (prevention/Profile.vue renders variant=full above the form and consumes ?focus= via firstMissingKey)"
  - "13-08 (dashboard/Index.vue renders the compact row after .score-display and is the only mount point for the banner)"
  - "13-09 (Risk.vue / Score.vue render EstimateBadge and route on its complete event)"
  - "13-10 (field-hint blocks reuse .hhs-imp-badge from the global families)"
tech-stack:
  added: []
  patterns:
    - "Global shared-class families for chrome that four scoped surfaces all need, so per-page copies cannot drift (UI-SPEC Contract 6)"
    - "Self-drawn control instead of an Element Plus primitive when the primitive's locked global CSS or DOM shape conflicts with the accessibility contract"
    - "Module-level id counter (not useId()) for aria-labelledby, because the declared Vue floor is ^3.4.15"
    - "Payload assumptions array as a copy-correctness signal, never a frontend re-derivation of a backend substitution rule"
key-files:
  created:
    - hhs-frontend-v2/src/components/common/ProfileCompleteness.vue
    - hhs-frontend-v2/src/components/common/ProfileReminderBanner.vue
    - hhs-frontend-v2/src/components/common/EstimateBadge.vue
    - hhs-frontend-v2/src/__tests__/components/ProfileCompleteness.spec.ts
    - hhs-frontend-v2/src/__tests__/components/ProfileReminderBanner.spec.ts
    - hhs-frontend-v2/src/__tests__/components/EstimateBadge.spec.ts
  modified:
    - hhs-frontend-v2/src/assets/styles/theme.css
key-decisions:
  - "The visual meter and both badges are self-drawn rather than ElProgress / el-alert / el-tag. ElProgress has no segment geometry and its .el-progress__text colours come from EP variables outside this token set; el-alert's default slot renders inside a <p> so two buttons get hoisted out and only one × is possible; el-tag is locked by theme.css:542-549 to 500 11px/1.4 mono with accent text at 3.74:1 (below AA)"
  - "Segment and legend counts come from fields.length in both variants — no literal 8 or 9 exists in the component. A 4-field fixture proves the count, and a 9-field fixture proves 已填 9 / 9"
  - "impactLine branches on hasProfile FIRST. The same missing-required state means two different things: a brand-new user's risk run takes the fixed 30/25/25 branch and never uses 35 岁, and the 35 岁 path is reachable only for legacy birth_date IS NULL rows because HealthProfileRequest.birthDate is @NotNull"
  - "Missing gender gets its own sentence instead of being folded into the 35 岁 one: gender is consumed by the health report and AI prompts, not by RiskAssessmentServiceImpl"
  - "The two 缺推荐 sentences are chosen by asking the payload's own assumptions array whether it contains the bmi entry's sentence, not by re-deriving the bmi rule. bmi.filled requires height AND weight AND bmi (13-01's staleness immunity), so it cannot distinguish 'never recorded' from 'height/weight cleared while the stored bmi survives' — the assumptions array is produced by the same function the risk engine's behaviour follows"
  - "The banner gate is score < 60 with 60 rendering nothing, because a gender + birthDate only user lands near 60% and ROADMAP success criterion 5 says not to nag them"
  - "The banner uses aria-labelledby pointing at its own title rather than a static aria-label, because the title carries {{ score }} and {{ missing.length }} and a frozen label would go stale"
  - "EstimateBadge treats null and undefined as 'say nothing' but 0 as a real value, so an old backend or a cached VO cannot fabricate a 完整度 0% claim while a genuine 0% still renders"
  - "firstMissingKeyOf is imported by the banner rather than re-implemented: the store, the banner and the ?focus= handoff must resolve the same field or they drift silently"
  - "The scoped .visually-hidden utility lives in ProfileCompleteness.vue because the repo had none (grep-verified: 0 occurrences repo-wide before this plan)"
patterns-established:
  - "Additive global CSS block headed with the phase, using only existing tokens — no new colour values introduced for a phase that claims zero new tokens"
  - "Copy that describes a backend substitution must be selected by a payload signal produced by that same backend function, not by frontend inference"
requirements-completed: [PRF-01, PRF-03, PRF-04, PRF-06, PRF-07]
actuals:
  tokens: 11424  # chars/4 over the realized diff (45,696 chars across hhs-frontend-v2)
  tasks: 3
  # MEASURED: git rev-list --count 5c63218..HEAD = 7
  # 6 task commits (test + feat per task) + this docs commit.
  commits: 7
plan_head_before: 5c63218d53fb41f21eb896669a9ffaf0f57d856d
status: complete
---

# Phase 13 Plan 06: 档案完整度三组件与共享视觉族 Summary

Three presentational components plus one additive global CSS block now render the completeness payload as a segmented meter and an accessible legend, gate the reminder banner on `score < 60`, and disclose an estimate with a keyboard-reachable button — every screen-reader symbol paired with a word, and every sentence about a backend substitution selected by a signal the backend itself produced.

## What Was Built

**Global CSS (`theme.css`, +106 lines).** One block headed `/* ---------- Phase 13: 档案完整度共享视觉族 ---------- */` declares the four families the plan named, with no colour value outside the existing token set:

- `.hhs-completeness-meter` (flex, `gap: 4px`, `width: 100%`) with `__seg` (`flex: 1`, height 8px, `--surface-3`, `--radius-full`) and `--compact` narrowing the bar to 4px; `.is-filled` paints `--accent-cool` (Light 3.02:1 / Dark 10.71:1 — past the 3:1 non-text floor); a `prefers-reduced-motion` rule drops the transition.
- `.hhs-tier-pill` (inline-flex, 24px, `--radius-sm`, `400 12px/1.4`, `--text-1` on `--surface-3`) with `--basic` (`--accent-warm-soft`) and `--full` (`--accent-cool-soft`). Shape and size are identical across the three tiers; only the tint differs, so WCAG 1.4.1 is satisfied by the pill's own text.
- `.hhs-imp-badge` with `--required` (filled), `--recommended` (1px solid) and `--optional` (1px dashed), all 20px tall with `flex: none`.
- `.hhs-estimate-badge` with a `1px solid var(--accent-warm)` border (Light 3.32:1 / Dark 8.70:1 — the `--accent-warm-soft` fill alone is 1.07:1 on white and would be invisible), `min-height: 32px`, an underlined `__action` fragment in `--text-1`, a hover fill, and a `:focus-visible` ring.

**`ProfileCompleteness.vue` (255 lines).** `defineProps<{ completeness: ProfileCompletenessVO | null; variant?: 'full' | 'compact' }>()`, `variant` defaulting to `'full'`. Both variants iterate `completeness.fields` for the segments, so a 4-item payload renders 4 segments and a 9-item payload renders 9 — no literal count exists anywhere in the file. `tierClass()` maps 基础档案 → `hhs-tier-pill--basic`, 标准档案 → no modifier, 完整档案 → `hhs-tier-pill--full`.

The meter is `aria-hidden="true"` (the score and the legend carry the same information); each legend row renders `✓`/`○` inside an `aria-hidden` span plus a sibling `.visually-hidden` holding 已填/未填, so a screen reader never hears an isolated glyph. The full variant is a `<section aria-labelledby>` whose label id comes from a module-level counter (not `useId()`, which needs Vue 3.5+); the compact variant is a plain `<div>` with no legend, no impact line and a `<router-link to="/prevention/profile">完善档案</router-link>`.

`impactLine` evaluates six cases, first match wins, with `hasProfile` first:

1. `!hasProfile` → `尚未填写健康档案，评分与风险结果将按通用参考值估算。`
2. `birthDate` unfilled → `缺少出生日期，风险评估将按 35 岁与通用模型估算。`
3. `birthDate` filled, `gender` unfilled → `缺少性别，健康报告与 AI 建议将按通用表述生成。`
4. a recommended field unfilled → `assumptions` contains the bmi entry's `assumption` sentence ? `缺少身高、体重或 BMI，风险评估将按 BMI 参考值 22.0 估算。` : `缺少身高或体重，BMI 无法随身体变化重算，风险评估沿用已记录的 BMI。`
5. only optional unfilled → `缺少血型、过敏史、家族病史或生活习惯，健康报告与 AI 建议的个性化程度会降低。`
6. nothing missing → `档案信息完整，评分与风险结果均基于真实数据。`

**`ProfileReminderBanner.vue` (185 lines).** A self-drawn `div.profile-banner` with `role="status"` and `:aria-labelledby="titleId"`, an `InfoFilled` icon in an `aria-hidden` circular chip, a title carrying the live score and remaining count, a body that names the missing labels in catalog order and ends with the consequence clause, and exactly three controls: `el-button type="primary"` 完善档案, `el-button` 稍后提醒, and a `button.profile-banner__close` with `aria-label="关闭提醒，7 天内不再显示"` whose visible text is `×`.

`show = completeness != null && completeness.score < 60 && !dismissed`, so 60 is silent. The primary action emits `complete` with `firstMissingKeyOf(completeness.fields)` imported from `@/utils/profileFields` — the component contains no `importance` comparison and no rank table. Both 稍后提醒 and × emit `dismiss`; the component never touches `localStorage`.

**`EstimateBadge.vue` (33 lines).** `show = completeness != null && completeness < 100`, rendering `button.hhs-estimate-badge` with `基于不完整档案估算`, an `aria-hidden` `·`, and a `span.hhs-estimate-badge__action` reading 完善档案. Click emits `complete`; no router import anywhere, so the parent decides the destination and the component mounts in a bare test.

## Verification Results

| Check | Result |
|---|---|
| `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/components/{ProfileCompleteness,ProfileReminderBanner,EstimateBadge}.spec.ts` | **49 tests, 0 failures** (23 + 16 + 10; plan floor was 14 + 9 + 6) |
| `npm --prefix hhs-frontend-v2 run test:run` (full suite) | **360 tests / 33 files, 0 failures** |
| `npm --prefix hhs-frontend-v2 run build` (`vue-tsc -b && vite build`) | green after every task |
| `npm --prefix hhs-frontend-v2 run lint:check` | 0 errors; 202 pre-existing warnings, none in the six new files (verified by linting them in isolation) |
| `grep -c "var(--text-3)"` on each of the three components | **0 / 0 / 0** |
| `grep -c "ElProgress\|el-progress"` on ProfileCompleteness.vue | **0** |
| `grep -c "hhs-completeness-meter"` on theme.css | **5** |
| `grep -c "firstMissingKeyOf"` on ProfileReminderBanner.vue | **2**; `grep -cE "importance\|rank"` on the same file | **0** — the priority rule is imported, not re-derived |
| `grep -cE "el-alert\|ElAlert"` on ProfileReminderBanner.vue | **0** |
| `grep -cE "el-tag\|ElTag"` on EstimateBadge.vue | **0** |
| Bare `取消` / `确定` / `关闭` as visible button text in any of the three components | **0** each |

## Must-Have Truth Coverage

| Must-have truth | Verification ref | Status |
|---|---|---|
| The meter renders exactly `fields.length` segments and the count text comes from the payload, so no literal 8 or 9 appears | `renders one segment and one legend item per payload field (9 of each)`; `drives the segment and legend count from fields.length, not a hard-coded 9` (4-field payload → 4); `never hard-codes the total` (已填 9 / 9) | covered |
| Each legend item renders a ✓/○ glyph plus a visually-hidden 已填/未填 word | `gives every legend item a visually-hidden 已填/未填 word next to the glyph` — asserts the word array, plus `aria-hidden` on the glyph span | covered |
| The tier pill applies `hhs-tier-pill--basic` below 60, no modifier for 60–84 and `hhs-tier-pill--full` at 85+ | `maps 基础档案 to hhs-tier-pill--basic`; `adds no modifier for 标准档案` (asserts neither modifier); `maps 完整档案 to hhs-tier-pill--full` | covered |
| The full variant renders the impact line; the compact variant renders a 完善档案 router-link instead and no legend | `renders the score, the pill and a 完善档案 link, with no legend and no impact line`; `does not render a section landmark in the compact variant` | covered |
| The impact line branches on `hasProfile` before the importance group; the two 缺推荐 sentences are selected by the payload's assumptions array | `tells a brand-new user about the generic model when hasProfile is false` (+ asserts no `35 岁`); `names the missing birthDate when a profile row exists`; `names the missing gender without claiming a risk-engine consequence`; `reports only the optional fields…`; `promises the 22.0 BMI substitution only when the payload assumptions carry the bmi sentence`; `uses the stale-bmi sentence and never promises 22.0…` (+ asserts no `22.0`); `reports completeness when all nine fields are filled` | covered |
| The banner renders only when `score < 60` and the dismissal window has expired; at exactly 60 it renders nothing | `renders nothing at exactly 60 — a gender + birthDate user is not nagged`; `renders nothing above the threshold (61 and 100)`; `renders below the threshold (0, 30 and 59)`; `renders nothing when the store reports the reminder dismissed`; `renders nothing for a null payload, never as a fabricated 0%` | covered |
| The banner has `role=status` plus `aria-labelledby` at its own title id, and its close control's accessible name is `关闭提醒，7 天内不再显示` | `carries role=status plus an aria-labelledby that resolves to its own title id`; `exposes exactly one primary 完善档案, one 稍后提醒 and one named close control` | covered |
| The banner is a self-drawn `div.profile-banner`, never `el-alert`; one primary 完善档案 and one 稍后提醒 | `is a self-drawn div, never an el-alert`; controls test above; `grep -cE "el-alert\|ElAlert"` == 0 | covered |
| The estimate badge is a self-drawn `button.hhs-estimate-badge`, never `el-tag`, and renders nothing when completeness is null | `is a button.hhs-estimate-badge carrying the state copy and the action fragment`; `is not an el-tag and does not import the router`; `renders nothing when completeness is null`; `renders nothing when completeness is undefined` | covered |
| The estimate badge renders for a number below 100 including 0, and emits `complete` on click and on Enter/Space | `renders at 0 — a real value, not a falsy miss`; `renders at 1 and at 99`; `renders nothing at exactly 100`; `emits complete on click`; `is a real button so Enter and Space activate it natively` | covered |
| Every explanatory small text uses `--text-2`; no component uses `--text-3` for copy | `grep -c "var(--text-3)"` == 0 on all three components | covered |
| The legend and the banner body wrap rather than truncate at narrow widths, and no element sets `overflow:hidden` on text | `.completeness__legend { flex-wrap: wrap; gap: 8px 16px }` + `.completeness__legend-item { white-space: nowrap }`; `.profile-banner__body { flex: 1; min-width: 0 }`; `@media (max-width: 768px)` wrap rule with `padding-left: 44px`. The only `overflow: hidden` in the three files is inside `.visually-hidden` (a clip utility, not text truncation) | covered |
| A null completeness payload renders no completeness block at all, rather than an empty meter or a 0% claim | `renders nothing at all for a null payload instead of a 0% claim` (also asserts the text contains no `0%`) | covered |
| The compact variant's head uses `flex-wrap` so `62%` and the pill never break mid-token, and the link takes its own line | `.completeness__head { flex-wrap: wrap }` shared by both variants; `.completeness__link { display: inline-flex; margin-top: 8px }` is a block-level sibling after the meter, so it occupies its own line | covered |
| The estimate badge's fixed 14-character label does not wrap at a 320px viewport | `min-height: 32px` + `padding: 4px 12px` + `.hhs-estimate-badge__action` underline; no explicit width. Verification is a backstop — the automated suite cannot measure layout in happy-dom | backstop (visual review at 320px) |
| The banner's two buttons plus the close control do not collide at 640px or 768px | `@media (max-width: 768px)` gives the actions row `width: 100%` and `padding-left: 44px`; the 640px case is covered by the same query | backstop (visual review at 640 / 768px) |
| No element sets `overflow: hidden` on text, so a long label is wrapped rather than clipped | see the wrap row above; the sole occurrence is the `.visually-hidden` clip utility | covered |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The compact fixture rendered 9 segments instead of 4**
- **Found during:** Task 1 (first GREEN run)
- **Issue:** The 4-field fixture was built with `makeFields(['gender','birthDate','heightCm','weightKg'])`, which returns all nine catalog entries marked filled — so the payload really had `fields.length === 9` and the assertion `toHaveLength(4)` failed at `expected - 4, + 9`. The test, not the component, was wrong: it never exercised a short payload.
- **Fix:** The fixture now slices `PROFILE_FIELDS.slice(0, 4)`, producing a genuine 4-entry array with `totalCount: 4`. The test then proves what it claims — the count follows `fields.length`.
- **Files modified:** `hhs-frontend-v2/src/__tests__/components/ProfileCompleteness.spec.ts`
- **Commit:** `31eadfd`

**2. [Rule 2 - Auto-add missing critical functionality] ProfileCompleteness needed a null payload guard**
- **Found during:** Task 1 (first GREEN run)
- **Issue:** The must-have truth "A null completeness payload renders no completeness block at all" was unenforceable: the component dereferenced `props.completeness.score` at render time, throwing `Cannot read properties of null` before any `v-if` could hide the block. A cached VO or an older backend response would have crashed the page rather than withholding the header — the exact failure mode UI-SPEC Contract 8 forbids.
- **Fix:** The prop type widened to `ProfileCompletenessVO | null`; both template branches gained `v-if="props.completeness && …"` / `v-else-if="props.completeness"`; `impactLine` returns `''` for null and reads `completeness?.fields ?? []`. The null test now passes.
- **Files modified:** `hhs-frontend-v2/src/components/common/ProfileCompleteness.vue`
- **Commit:** `31eadfd`

**3. [Rule 1 - Bug] A leading template comment turned both components into fragment roots**
- **Found during:** Task 2 and Task 3 (first GREEN runs)
- **Issue:** The banner's `wrapper.classes()` returned `[]` and the badge's `wrapper.element.tagName` was `div`, because a comment placed before the root element makes Vue render a multi-root fragment whose `wrapper.element` is an anchor comment node. The accessible-name and `role` assertions on the inner elements still passed, so the defect was in the component's root shape only — but a stray fragment root means `attrs` (including `class`, `role`, `id`) no longer fall through predictably from the parent.
- **Fix:** Both comments moved inside the root element (banner) or into the markup it annotates (badge), restoring a single real root node.
- **Files modified:** `hhs-frontend-v2/src/components/common/ProfileReminderBanner.vue`, `hhs-frontend-v2/src/components/common/EstimateBadge.vue`
- **Commit:** `f501079`, `5855681`

### Scope-boundary notes (no code deviation)

**4. Three acceptance greps matched their own explanatory comments.**
- **Found during:** Task 2 and Task 3 verification
- **Issue:** `grep -c "el-alert\|ElAlert"` on the banner returned 2 and `grep -c "el-tag\|ElTag"` on the badge returned 1, because the comments explaining *why not* to use those components named them literally. A mechanical check that a comment can satisfy measures nothing.
- **Fix:** Reworded the three comments to name "EP 的 Alert 组件" / "EP 的 Tag 组件" instead. Both counts are now 0, so the criterion again means "no Element Plus alert or tag is instantiated here".
- **Files modified:** `hhs-frontend-v2/src/components/common/ProfileReminderBanner.vue`, `hhs-frontend-v2/src/components/common/EstimateBadge.vue`
- **Commit:** `f501079`, `5855681`

**5. The banner spec's `el-button` stub must not re-emit `click`.**
- **Found during:** Task 2 (second GREEN run)
- **Issue:** The stub originally carried `@click="$emit('click')"`. Vue Test Utils attaches the parent's `@click` to the stub's `<button>` root as a native listener, so the emit fired the handler twice — `emitted('dismiss')` had length 2 after one click.
- **Fix:** Removed the redundant emit; the native click bubbles to the root listener exactly once. Recorded in the spec's stub comment so a future maintainer does not "restore" it.
- **Files modified:** `hhs-frontend-v2/src/__tests__/components/ProfileReminderBanner.spec.ts`
- **Commit:** `f501079`

**6. The required-over-optional priority test needed `score < 60`.**
- **Found during:** Task 2 (second GREEN run)
- **Issue:** The test asserted the focus key for a payload scored 78, but the banner intentionally does not render at 78, so `findAll(...)[0]` was `undefined`. The fixture was internally inconsistent rather than the component being wrong.
- **Fix:** Score set to 55 (a genuine 基础档案 state that still names birthDate first, since birthDate is required and bloodType optional). The test now verifies the imported priority rule through a rendered banner.
- **Files modified:** `hhs-frontend-v2/src/__tests__/components/ProfileReminderBanner.spec.ts`
- **Commit:** `f501079`

**7. The 7-day window itself is 13-05's tested responsibility, not this plan's.**
- **Found during:** Task 2 review
- **Issue:** The banner receives `dismissed` as a prop, so this plan's spec correctly asserts only the boolean gate. Recorded so the coverage table's silence on the time arithmetic is not mistaken for a gap.
- **Fix:** None needed; `13-05-SUMMARY.md` already covers `isDismissed()` at the `7d − 1ms` / exactly `7d` / `7d + 1ms` / `8d` boundaries.
- **Commit:** —

## Known Stubs

None. Every rendered value comes from the `ProfileCompletenessVO` payload or the backend-mirrored catalog in `utils/profileFields.ts`. There is no placeholder copy, no hardcoded empty array reaching a renderer, and no unwired prop. The two unrun visual checks (320px badge wrap, 640/768px banner control spacing) are **backstops**, not stubs: the layout rules that address them are implemented, they simply cannot be measured in happy-dom.

## Threat Flags

None beyond the plan's register. T-13-21 (EstimateBadge null handling) is asserted for `null`, `undefined` and `100`; T-13-22 (shared badge CSS) is structurally enforced — the four families exist only in `theme.css`, and no component redeclares them; T-13-23 (banner body copy) renders only the caller's own field labels from the payload, no internal key or id; T-13-24 (close control semantics) is covered by the exact `aria-label` assertion and the shared `dismiss` event. No new network endpoint, file-access pattern or schema change, and no package installs (T-13-SC: all icons come from the already-installed `@element-plus/icons-vue`).

## Decisions Made

1. **`impactLine` asks the payload, not the rules.** The `assumptions` array is produced by the same backend function the risk engine's behaviour follows, so a sentence about a substitution is correct exactly when the engine will perform it. Re-deriving "does the engine substitute 22.0" in the component is what R-1 identified as the failure mode.
2. **`hasProfile` is the first branch, unconditionally.** Two states that look identical field-by-field (`gender` missing) have different downstream meanings, and only the payload's `hasProfile` distinguishes them.
3. **Self-drawn over Element Plus for all three controls.** Each substitution is justified by a specific conflict (segment geometry, slot DOM shape, locked `!important` typography), not by preference; the risk-level `el-tag` and the backfill panel's `el-alert` title container stay untouched.
4. **The banner and the badge emit; the parent routes.** Neither imports the router, so both mount in isolation and the destination (including the `?focus=` query) stays a page-level decision — the same division 13-05 used for `localStorage`.
5. **`--text-3` is banned from copy, and the only `overflow: hidden` in these files belongs to a clip utility.** The contrast table (Light 3.97:1) and the no-truncation truth are both machine-checkable, so they are checked rather than reviewed.

## Self-Check: PASSED

- `hhs-frontend-v2/src/assets/styles/theme.css` — FOUND (106-line Phase 13 block appended)
- `hhs-frontend-v2/src/components/common/ProfileCompleteness.vue` — FOUND
- `hhs-frontend-v2/src/components/common/ProfileReminderBanner.vue` — FOUND
- `hhs-frontend-v2/src/components/common/EstimateBadge.vue` — FOUND
- `hhs-frontend-v2/src/__tests__/components/ProfileCompleteness.spec.ts` — FOUND (23 tests)
- `hhs-frontend-v2/src/__tests__/components/ProfileReminderBanner.spec.ts` — FOUND (16 tests)
- `hhs-frontend-v2/src/__tests__/components/EstimateBadge.spec.ts` — FOUND (10 tests)
- Commit `fec4d9f` (test: completeness RED) — FOUND
- Commit `31eadfd` (feat: completeness meter) — FOUND
- Commit `0ee4ad0` (test: banner RED) — FOUND
- Commit `f501079` (feat: banner) — FOUND
- Commit `2648698` (test: badge RED) — FOUND
- Commit `5855681` (feat: badge) — FOUND
- Commit `36a80a9`-lineage docs commit, squashed into `394c495` (docs: this summary + WINDOWS.md backstops) — FOUND
- Measured commit count `5c63218..HEAD` = 7 (6 task + 1 docs), matching the frontmatter `commits: 7`