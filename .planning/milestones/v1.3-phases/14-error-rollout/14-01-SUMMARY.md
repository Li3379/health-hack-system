---
phase: 14-error-rollout
plan: 01
subsystem: ui
tags: [error-handling, classification, axios, element-plus, vue3, vitest, wcag]

requires:
  - phase: 11-error-foundation
    provides: "ApiError model, normalizeApiError's ordered classification, showError's single toast outlet, ErrorState's content-scope block"
  - phase: 12-form-error-surfaces
    provides: "FormErrorSummary and the validation channel this plan leaves untouched"
  - phase: 13-remaining-surfaces
    provides: "the per-page ErrorState adoption pattern and the --text-2/--text-3 contrast finding"

provides:
  - "ErrorKind with 9 members: forbidden and notFound inserted after auth"
  - "normalizeApiError's 403 and 404 branches, placed between the 401 guard and the no-response guard"
  - "ShowErrorOptions.presentStates with a zero-toast default for both new kinds"
  - "ErrorState scope='content'|'page' and actions[] (mutually exclusive with retry)"
  - "the --hhs-page-pad-y host-relative page-height convention"
  - "the S1 page-scope forbidden block on settings/DevicePlatformConfig"
  - "A-2's four anti-regression cases plus the errorCode-preservation case"

affects: [14-02, 14-03, 14-04, 14-05, 14-07, 14-10, 14-11, 14-12, 14-13, all-87-migrations]

actuals:
  tokens: 7527
  tasks: 2
  commits: 2
  plan_head_before: 56498f8fe89f177a85bbd31b2d4dee8a83c888ef

tech-stack:
  added: []
  patterns:
    - "Classification-order-as-contract: the two new steps sit between 401 and no-response, documented as a numbered 9-step list at the code site"
    - "Zero-toast-by-default state channel: state kinds produce nothing unless presentStates is explicitly set"
    - "Host-relative page geometry: the block subtracts a per-host --hhs-page-pad-y instead of baking a constant"
    - "Mutually exclusive action sources: actions[] supersedes retry rather than coexisting"

key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/DevicePlatformConfigForbidden.spec.ts
  modified:
    - hhs-frontend-v2/src/utils/appError.ts
    - hhs-frontend-v2/src/utils/showError.ts
    - hhs-frontend-v2/src/components/common/ErrorState.vue
    - hhs-frontend-v2/src/views/settings/DevicePlatformConfig.vue
    - hhs-frontend-v2/src/views/dashboard/Index.vue
    - hhs-frontend-v2/src/__tests__/utils/appError.spec.ts
    - hhs-frontend-v2/src/__tests__/utils/showError.spec.ts
    - hhs-frontend-v2/src/__tests__/utils/request.spec.ts
    - hhs-frontend-v2/src/__tests__/components/ErrorState.spec.ts

key-decisions:
  - "Envelope 403/404 are detected by errorCode, never by data.code: Result.failure(String code, ...) routes through parseCodeToInt, which returns 500 for every non-numeric code, so the numeric field cannot distinguish permission from absence"
  - "The 404 predicate accepts both separators /(^|_|\\.)not[._]?found$/ because ErrorCode.java carries health.profile_not_found (underscore) and resource.not_found (dot) side by side"
  - "Both branches pass status and errorCode and omit the seventh constructor argument, so retryable keeps its false default and prevention/Profile.vue + profileCompleteness.ts keep their branch inputs"
  - "The presentStates early return sits before resolveMessage, so a state that renders nothing performs no message resolution at all"
  - "ErrorState was extended rather than wrapped (UI-SPEC Contract 2.1): one component, one spec file covering both geometries"
  - "The page block's title upgrades to Display 24px/1.2 while the content-scope block stays at Heading 18px/1.3, giving the S1/S2 pair a non-colour differentiator"
  - "The el-button stub gained a :type binding so primary/default action grading is observable from the DOM; no existing assertion changed"

patterns-established:
  - "State-vs-toast separation: a kind that represents a page state renders zero toasts by default; only an action-type failure opts in with presentStates"
  - "Negative verification as evidence: each new pin was confirmed to fail for the intended reason (server for a real 403, business for an envelope, 4000-vs-0 for notFound) before the implementation landed"
  - "Source-level CSS assertions for contracts jsdom cannot compute (height expression, colour token, absence of the 104px literal)"

requirements-completed: [ERR-07]

coverage:
  - id: D1
    description: "A real HTTP 403 classifies as forbidden with retryable === false, never as server"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/utils/appError.spec.ts#① 真实 HTTP 403 判为 forbidden 且不可重试（不是 server）"
        status: pass
    human_judgment: false
  - id: D2
    description: "An envelope 403 (HTTP 200, errorCode auth.forbidden, numeric code 500) classifies as forbidden, not business"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/utils/appError.spec.ts#② 信封 403（数值 code 500 + errorCode auth.forbidden）判为 forbidden，不是 business"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#分类层到呈现层的串联：信封 403 经 showError 默认零 toast"
        status: pass
    human_judgment: false
  - id: D3
    description: "Envelope numeric code 404 and every *_not_found errorCode (dot and underscore forms) classify as notFound, never as business"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/utils/appError.spec.ts#③ 信封数值 code 404 判为 notFound"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/appError.spec.ts#接受点号与下划线两种分隔符的 not_found 错误码族"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/request.spec.ts#rejects a code=404 envelope as a notFound ApiError, preserving message and errorCode"
        status: pass
    human_judgment: false
  - id: D4
    description: "The notFound branch preserves err.code and err.status so create-mode and NOT_FOUND_CODES keep working"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/utils/appError.spec.ts#④ 信封 health.profile_not_found（数值 code 500）判为 notFound 并保留 errorCode"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/ProfileErrorSample.spec.ts#B. 档案不存在（业务 404）：进入创建模式且零 toast（Pitfall 6）"
        status: pass
      - kind: unit
        ref: "src/__tests__/stores/profileCompleteness.spec.ts#synthesizes 0% / 基础档案 / 9 fields on a 404 and keeps loadFailed false"
        status: pass
    human_judgment: false
  - id: D5
    description: "normalizeApiError's doc comment is a readable 9-step list with the two new steps between 401 and no-response"
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/utils/appError.ts:57-84 (numbered steps 0-9, '分类顺序即契约，不可调换（共 9 步）')"
        status: pass
    human_judgment: false
  - id: D6
    description: "showError emits zero toasts for forbidden and notFound unless presentStates is true, and still returns the classified ApiError"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#默认零 toast：forbidden 不弹任何提示，但仍返回已分类的 ApiError"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#默认零 toast：notFound 同样不弹提示"
        status: pass
    human_judgment: false
  - id: D7
    description: "presentStates: true renders exactly one sticky toast each: forbidden with no retry and no action button, notFound at duration 0"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#presentStates: true 时 forbidden 只弹一条常驻 toast，且绝不渲染重试按钮"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#presentStates: true 时 notFound 只弹一条 duration 0 的 toast，无动作按钮"
        status: pass
    human_judgment: false
  - id: D8
    description: "showError never renders a retry button for forbidden/notFound even when a retry callback is supplied"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#presentStates: true 时 forbidden 只弹一条常驻 toast，且绝不渲染重试按钮 (retry callback passed deliberately)"
        status: pass
    human_judgment: false
  - id: D9
    description: "ErrorState accepts scope and actions while all 14 pre-existing it() cases still pass unchanged; actions supersede retry; each label is both visible text and aria-label"
    verification:
      - kind: unit
        ref: "src/__tests__/components/ErrorState.spec.ts (22 tests: 14 unchanged + 8 new)"
        status: pass
      - kind: unit
        ref: "src/__tests__/components/ErrorState.spec.ts#uses each action-label as both the visible text and the aria-label"
        status: pass
    human_judgment: false
  - id: D10
    description: "scope='page' drops the card surface, uses the host-relative height, and the description uses --text-2 rather than --text-3"
    verification:
      - kind: unit
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#页级高度扣掉页头 64px 与宿主声明的内边距变量"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#说明句用次级文字色而不是三级色"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#组件内不出现硬编码的 104px 高度"
        status: pass
    human_judgment: false
  - id: D11
    description: "The page geometry is host-relative: two hosts declare --hhs-page-pad-y at 40px and 64px, and the padding:0 host declares nothing"
    verification:
      - kind: unit
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#两个宿主各自声明 --hhs-page-pad-y，值不同（40px / 64px）"
        status: pass
      - kind: unit
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#padding:0 的宿主（health/Metrics.vue）无需声明该变量"
        status: pass
    human_judgment: false
  - id: D12
    description: "Opening /settings/device-platform against a 403 renders the S1 page block with 返回首页, produces zero toasts, and never issues checkConfigReady"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#首个查询返回 403：渲染页级块、零 toast、且不再发第二个请求"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#页级块以 返回首页 作为唯一动作，且标签同时是可见文本与可访问名"
        status: pass
    human_judgment: false
  - id: D13
    description: "UI Considerations · error E1 403: the page-level block replaces page content with zero toast while the header title stays"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#首个查询返回 403：渲染页级块、零 toast、且不再发第二个请求"
        status: pass
    human_judgment: true
    rationale: "That the surrounding MainLayout header, sidebar and breadcrumb remain visually intact and usable in a real browser is not expressible in a component spec that mounts the page in isolation. The spec proves the block renders and no toast fires; only a real session can confirm the page shell is still usable."
  - id: D14
    description: "UI Considerations · loading E1/E2: the state block appears only after a completed failed request; in-flight stays v-loading"
    verification:
      - kind: unit
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#非 403 失败仍走 toast 而不是页级块（页级块只属于权限语义）"
        status: pass
    human_judgment: true
    rationale: "The spec pins that the block is gated on a classified forbidden outcome rather than on loading, but the absence of a state-block flash during a slow in-flight request is a perceptual property requiring real network timing."
  - id: D15
    description: "UI Considerations · populated E3 nav: sidebar and header stay usable while the page block renders — no route jump, no full-screen mask"
    verification: []
    human_judgment: true
    rationale: "No automated assertion covers the global navigation chrome; the component spec mounts the page without MainLayout. Requires opening the page as a non-admin in a real session."
  - id: D16
    description: "UI Considerations · error E6 network/5xx: one sticky toast with 重试, unchanged by this plan"
    verification:
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#emits a sticky VNode toast for a real 5xx with grouping disabled"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#renders an accessible retry button when a retry callback is supplied"
        status: pass
    human_judgment: false
  - id: D17
    description: "UI Considerations · error E6 action-type: a permission toast carries no 重试 because forbidden.retryable is false"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#presentStates: true 时 forbidden 只弹一条常驻 toast，且绝不渲染重试按钮"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/appError.spec.ts#① 真实 HTTP 403 判为 forbidden 且不可重试（不是 server）"
        status: pass
    human_judgment: false
  - id: D18
    description: "UI Considerations · zero-one-many E6 toast: 0 → no node, 1 → single, >3 → the oldest non-retry toast is evicted"
    verification:
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#closes the oldest string toast when a 4th distinct business toast appears"
        status: pass
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#applies the same cap to VNode toasts"
        status: pass
    human_judgment: false
  - id: D19
    description: "UI Considerations · overflow E1/E2: the block caps at 720px (page) / 480px (content) and centres with margin auto"
    verification:
      - kind: unit
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#页级高度扣掉页头 64px 与宿主声明的内边距变量 (720px present in the same source block)"
        status: pass
    human_judgment: true
    rationale: "The width caps are asserted at source level only; that the block actually shrinks with a narrower parent rather than overflowing requires visual measurement in a browser."
  - id: D20
    description: "UI Considerations · long-text E6: backend messages are never truncated"
    verification:
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#never truncates a long backend message (long-text backstop)"
        status: pass
    human_judgment: false
  - id: D21
    description: "UI Considerations · long-text E1/E2 description: the 40-char ceiling wraps without truncation, verified at 320px and 640px"
    verification: []
    human_judgment: true
    rationale: "Explicitly a backstop: wrapping at two viewport widths is a rendering measurement. The description text is interpolated whole (no slice) and the page block caps at 720px, but the wrap itself must be measured."
  - id: D22
    description: "UI Considerations · long-text E1/E2 action labels: 返回列表 + 重新加载该记录 wrap (flex-wrap) rather than overflow at 320px"
    verification:
      - kind: unit
        ref: "src/__tests__/views/DevicePlatformConfigForbidden.spec.ts#页级高度扣掉页头 64px 与宿主声明的内边距变量 (the actions rule is asserted present in the same component source)"
        status: pass
    human_judgment: true
    rationale: "Explicitly a backstop: flex-wrap and the 639px stacking media query are declared, but whether the two labels actually fit without overflow at 320px is a real measurement, not an assumption."
  - id: D23
    description: "UI Considerations · loading E6 action-type: clicking 重试 closes the toast immediately and re-issues the request once; there is no automatic retry"
    verification:
      - kind: unit
        ref: "src/__tests__/utils/showError.spec.ts#closes the toast and invokes the retry callback exactly once on click (D-03)"
        status: pass
    human_judgment: false

# Metrics
duration: 11min
completed: 2026-11-10
status: complete
---

# Phase 14 Plan 01: Error Classification Foundation Summary

**403 and 404 are now first-class error kinds with a zero-toast state channel, and one real page proves the whole chain from axios response to rendered page block.**

## Performance

- **Duration:** 11 min
- **Started:** 2026-11-10T12:05:04Z
- **Completed:** 2026-11-10T12:16:23Z
- **Tasks:** 2
- **Files modified:** 10 (9 modified, 1 created)

## Accomplishments

- `ErrorKind` grew from 7 to 9 members, and `normalizeApiError` gained the two branches that make 403 and 404 expressible at all. Before this plan no code path could produce either kind: a real HTTP 403 fell past the 5xx check into the fallback as `server` with `retryable` unset, which `showError` rendered as a **sticky toast with a retry button** inviting the user to retry a permission denial; envelope 403/404 landed on the envelope branch as `business`. Both destroyed the channel separation ERR-07 requires.
- The two branches are inserted **between the 401 guard (line 120) and the no-response guard (line 148)** — the ordering contract. Before 401 and a session expiry would be misclassified as a permission failure; after no-response and neither could ever fire, since every `ApiError` short-circuits at step 0 and every non-`ApiError` without a `response` is already handled at step 6.
- Both predicates read `errorCode`, never `data.code`, because `Result.failure(String code, …)` routes through `parseCodeToInt`, which returns **500 for every non-numeric code**. The numeric field genuinely cannot distinguish permission from absence; the string field can. The 404 regex accepts both separators because `health.profile_not_found` and `resource.not_found` coexist in `ErrorCode`.
- `showError` gained `presentStates` with a **zero-toast default**, returning before `resolveMessage` so a state that presents nothing does no message work. `needsVNode` was extended so both kinds reach the `duration: 0` VNode branch instead of the string branch whose `duration` is hardcoded 4000.
- `ErrorState` gained `scope` and `actions` as additions, not a new component (Contract 2.1): one carrier, one spec file, both geometries. The page block drops the card surface and dashed border, upgrades the title to Display 24px/1.2, and computes its height as `calc(100vh - 64px - var(--hhs-page-pad-y, 0px))` — the `104` that would only hold for a `padding: 20px` host is deliberately absent.
- `settings/DevicePlatformConfig.vue` renders the S1 block from the first query's 403 alone: no role check, no `meta.requiresAdmin` read, no client-side gate (T-14-01, A-3). Zero toast, and the sequential `checkConfigReady` never fires after the denial.
- The four A-2 anti-regression cases are the load-bearing pin, and each was confirmed to fail **for the right reason** before the implementation landed: `expected 'server' to be 'forbidden'` for the real 403, `expected 'business' to be 'forbidden'` / `'notFound'` for the envelope shapes, and `expected 4000 to be +0` for the `notFound` toast when `needsVNode` is not extended.

## Task Commits

Each task was committed atomically:

1. **Task 1: End-to-end "envelope 403 → forbidden → page-scope state block, zero toast"** — `f1345e8` (feat)
2. **Task 2: Pin presentStates toast shapes, retryable=false, and the page-scope geometry contract** — `115e828` (test)

**Plan metadata:** `docs(14-01): complete ...` (separate plan-metadata commit)

## Files Created/Modified

- `hhs-frontend-v2/src/utils/appError.ts` — 9-member `ErrorKind`; `envelopeCode` / `errorCode` locals; the 403 branch (status 403 or `/(^|\.)forbidden$/`); the 404 branch (numeric 404, status 404, or `/(^|_|\.)not[._]?found$/`); the renumbered 9-step classification doc comment. Both branches pass four and five constructor arguments respectively, so `retryable` keeps its `false` default.
- `hhs-frontend-v2/src/utils/showError.ts` — `ShowErrorOptions.presentStates` with its doc comment; the early return placed after the `silent` check and before `resolveMessage`; `needsVNode` extended with both kinds; the header comment's channel list updated.
- `hhs-frontend-v2/src/components/common/ErrorState.vue` — `scope` and `actions` props; `isPage` / `hasActions` computeds; `handleAction` with the same rejection-swallowing shape as `handleRetry`; the `v-if="hasActions"` / `v-else-if="retry"` pair; `--text-2` for the description; the page-scope block, the merged actions rule, the two `:deep` weight normalisations, the `margin-left: 0` correction and the 639px stacking query.
- `hhs-frontend-v2/src/views/settings/DevicePlatformConfig.vue` — `forbidden` / `deniedDescription` refs, `goHome`, the `forbidden` reset at the top of `loadConfigs`, the rewritten `catch` (normalise → forbidden sets the state and returns; otherwise `showError(err, 'device.loadFailed')`), the `v-if="forbidden"` page block with the page body moved to a `v-else` template, and `--hhs-page-pad-y: 40px` on the page root.
- `hhs-frontend-v2/src/views/dashboard/Index.vue` — `--hhs-page-pad-y: 64px` on `.dashboard`, the second host that proves the variable is per-host.
- `hhs-frontend-v2/src/__tests__/views/DevicePlatformConfigForbidden.spec.ts` — **created.** Six integration cases (block renders, zero toast, no second request; single 返回首页 action; backend message honoured; generic message not honoured; non-403 still toasts; refresh clears the block) plus five source-level geometry assertions (height expression, no `104px`, `--text-2` not `--text-3`, two hosts at 40px/64px, the `padding: 0` host declares nothing).
- `hhs-frontend-v2/src/__tests__/utils/appError.spec.ts` — a new `describe` with the four A-2 cases, the separator-family case, and the `errorCode`-preservation assertion. 28 → 33 tests.
- `hhs-frontend-v2/src/__tests__/utils/showError.spec.ts` — five new cases under a state-channel `describe`, plus the `findNodeByClass` traversal helper copied from `ErrorLayerSample.spec.ts` rather than adding a dependency. 24 → 29 tests.
- `hhs-frontend-v2/src/__tests__/components/ErrorState.spec.ts` — eight new cases (page class; actions supersede retry; declaration order; label dual identity; muted circle in page scope; same live-region semantics; both action variants; rejected callback does not leak). 14 → 22 tests, the original 14 byte-identical.
- `hhs-frontend-v2/src/__tests__/utils/request.spec.ts` — the envelope-404 case now pins `notFound` instead of `business` (see deviation 2).

## Decisions Made

- **`errorCode` is the discriminator, `data.code` is not.** The plan required this; the research verified why (`parseCodeToInt` → 500 for every non-numeric code). The numeric field would have made both new branches unreachable for the exact shapes the backend actually sends.
- **`retryable` stays at its constructor default.** No seventh argument in either branch. This is what makes `hasRetry` false for permission errors, which in turn is what makes a supplied retry callback render nothing — UI-SPEC §5.3's "权限 toast 不带重试" has exactly one enforcement point and this is it.
- **The state kinds render nothing by default, not a quiet toast.** D-03's rule is that 403 is a page-level state and 404 a content-level one; a toast alongside a block would be the same failure presented twice. `presentStates` exists only for action-type failures that have no block to land in.
- **`ErrorState` extended rather than wrapped.** Contract 2.1 permits either; extension keeps the testable surface to one file and one spec, and the two geometries are pure CSS plus one conditional class.
- **The page block's title is Display 24px.** Of the five S1/S2 separation channels (surface, title size, height, icon, action count), the title size is the one a user perceives fastest, and none of the five relies on colour.
- **`--hhs-page-pad-y` declared by two hosts with two values.** Declaring only 40px would have made it a constant with a second name. The `padding: 0` host is left untouched precisely so the `0px` fallback is exercised by a real host rather than by comment.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The `el-button` stub in `ErrorState.spec.ts` swallowed `type`, making primary/default unobservable**
- **Found during:** Task 2 (action-variant case)
- **Issue:** The existing stub declared `type` in its `props` array but never bound it back to the template — the same defect the file's own comment warns about for `aria-label`. The new `renders both action variants` case failed with `expected undefined to be 'primary'` not because the component was wrong but because the stub discarded the prop.
- **Fix:** Added `:type="type"` to the stub template and extended the comment to explain both bindings.
- **Files modified:** `hhs-frontend-v2/src/__tests__/components/ErrorState.spec.ts`
- **Verification:** The variant case passes; all 14 original cases still pass unchanged.
- **Committed in:** `115e828`

**2. [Rule 1 - Bug] A Phase 11 test pinned the old envelope-404 classification, which this plan reverses by design**
- **Found during:** Task 2 (full-suite run)
- **Issue:** `request.spec.ts` asserted `expect(err.kind).toBe('business')` for `{ code: 404, message: '健康档案不存在', errorCode: 'health.profile_not_found' }`. That is precisely the payload A-2 case ④ requires to become `notFound`. The plan's must-have truth supersedes the stale pin, so leaving it would have meant shipping a red suite — or, worse, weakening the new branch to keep it green.
- **Fix:** Rewrote that single case to pin `notFound`, `not.be('business')`, the preserved message and `errorCode`, and `retryable === false` with no `retryRequest` attached. Its sibling `code=500 → business` anchor was left untouched, so the envelope-business channel is still pinned.
- **Files modified:** `hhs-frontend-v2/src/__tests__/utils/request.spec.ts`
- **Verification:** Full suite green at 474/38; the case fails if the 404 branch is removed.
- **Committed in:** `115e828`

**3. [Rule 1 - Bug] Two self-inflicted test errors during authoring, both fixed before commit**
- **Found during:** Tasks 1 and 2
- **Issue:** (a) The `el-table-column` stub rendered its default slot with `row: {}`, so `getPlatformIcon` threw on `platform[0]`; the table is irrelevant to a 403 spec. (b) A `not.toContain('重试')` assertion on `wrapper.text()` matched the default description's "请重试".
- **Fix:** (a) the stub no longer renders the column slot; (b) the assertion now inspects the button set rather than the whole text.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/DevicePlatformConfigForbidden.spec.ts`, `hhs-frontend-v2/src/__tests__/components/ErrorState.spec.ts`
- **Committed in:** `f1345e8`

---

**Total deviations:** 3 auto-fixed (all Rule 1 — bugs in test scaffolding and one stale pin).
**Impact on plan:** No production behaviour diverges from the plan. Deviation 2 changes one pre-existing assertion's expected value, which is the plan's explicit intent rather than scope creep; deviations 1 and 3 are test-only.

## Issues Encountered

- **`device.loadFailed` does not exist in the dictionary yet.** `loadConfigs`' non-403 path calls `showError(err, 'device.loadFailed')` per the plan, and the key is scheduled by UI-SPEC's Copywriting Contract as a Phase-14 addition owned by plan 14-03 (which is the plan that touches `errorMessages.ts`). Until then `resolveMessage` falls through to `server.unavailable` — a correct Chinese sentence, so nothing is broken or English; the wording is just generic. Deliberately not added here: the plan assigns the dictionary to 14-03, and each added key must be mirrored in `errorMessages.spec.ts`'s count assertion.
- **`ElMessage` remains imported in `DevicePlatformConfig.vue`.** The plan requires this: the save/test/delete/init handlers are migrated in plan 14-05, so removing the import now would break `vue-tsc`.

## User Setup Required

None — no external service configuration required. Zero packages installed (the plan's package-legitimacy check is not applicable).

## Threat Surface Notes

- **T-14-01 (Elevation of Privilege) — mitigated as designed.** The block is gated on `normalizeApiError(error).kind === 'forbidden'`, i.e. on a server response only. `grep` confirms no `requiresAdmin` read and no role check was added anywhere in this plan; `router/index.ts` is untouched, so `meta.requiresAdmin` stays dead code as A-3 requires.
- **T-14-02 (Tampering/XSS) — mitigated as designed.** The description reaches the DOM only through `{{ }}` interpolation in `ErrorState.vue`; `showError`'s existing `h('span', …, shown)` text-child construction is byte-identical. No `v-html` and no `dangerouslyUseHTMLString` appears in any touched file.
- **T-14-03 (Information Disclosure).** No new detail surfaced: the 403 description prefers the backend's already-sanitised message and falls back to one fixed generic sentence; no stack, `name`, or constructor text is appended.
- **No new threat surface.** No endpoint, no request, no header, no credential, no schema change.

## Deferred Issues

- **`--text-3` remains on `.error-state__icon-circle--muted`** (line 160). UI-SPEC only mandates tightening the *description* to `--text-2`; the icon colour is a decorative glyph at 28px with a paired `Lock`/`DocumentDelete` shape, so it is not a text-contrast failure. Left as-is to keep the diff inside the plan's scope; worth a look if a later accessibility pass sweeps decorative glyph contrast.

## Next Phase Readiness

- `ErrorKind`, the two branches, `presentStates`, and `ErrorState`'s `scope`/`actions` are all in place, so every one of the 87 migrations now has a channel to migrate *to*. Plans 14-02 through 14-14 are unblocked.
- `settings/DevicePlatformConfig.vue`'s non-forbidden `catch` is already on `showError`; 14-05/14-07 only need to touch the remaining handlers and confirm the `presentStates` decision for `loadConfigs` (it must stay without it — the block owns that presentation).
- The four A-2 pins plus `request.spec.ts`'s `notFound` case mean a future "simplification" that moves the new branches past step 8, or collapses them onto `data.code`, fails immediately with a message that names the actual misclassification.
- The `--hhs-page-pad-y` convention is documented and test-guarded; 14-04's 404 blocks and `NotFound.vue` can adopt `scope="page"` without re-deriving the geometry, and any new host only needs to declare its own vertical padding.
- Baseline is now 474 tests across 38 files (was 445/37). `npm run build` and `npx eslint` are clean; the three remaining warnings are pre-existing `no-explicit-any` sites untouched by this plan.

---

## Self-Check: PASSED

- All 10 code files present on disk (9 modified, 1 created); `14-01-SUMMARY.md` written.
- Both task commits exist: `f1345e8`, `115e828`.
- `git rev-list --count 56498f8..HEAD` = 2, matching the measured `commits:` ledger.
- Full suite 474 passed / 38 files; `npm run build` succeeded; `npx eslint` clean on every touched file (3 pre-existing `no-explicit-any` warnings only).

---
*Phase: 14-error-rollout*
*Completed: 2026-11-10*
