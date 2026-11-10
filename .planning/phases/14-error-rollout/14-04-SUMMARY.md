---
phase: 14-error-rollout
plan: 04
subsystem: ui
tags: [error-handling, 404, routing, vue-router, element-plus, vue3, vitest, catch-all, not-found]

requires:
  - phase: 14-error-rollout
    plan: 01
    provides: "ErrorState's scope='content'|'page' and actions[] channels, and showError's presentStates option with its zero-toast default for state kinds"
  - phase: 14-error-rollout
    plan: 02
    provides: "the global .el-form-item.is-error declaration — not consumed here, but it is why no page in this plan needs any local error styling"
  - phase: 14-error-rollout
    plan: 03
    provides: "screening.loadFailed as a live dictionary key (46 keys total), which both fallbacks in this plan resolve through"

provides:
  - "the S2 content-area 404 block on screening/Detail.vue: notFound ref, a branch that returns before showError, and the ordered action pair 返回列表(primary) + 重新加载该记录(default)"
  - "the route-invalid page-scope block on screening/Compare.vue, replacing the 参数错误 toast, issued before any request"
  - "src/views/NotFound.vue — the single page-scope notFound view (页面不存在, one 返回首页 action, 26 lines)"
  - "the pathMatch catch-all route as the last layout child, the app's first non-blank response to an unknown URL"
  - "the routes array exported from router/index.ts so the table is assertable without instantiating createWebHistory"
  - "two spec files that pin the block/toast split on both pages and prove no existing route is shadowed"

affects: [14-12, 14-13, 14-14]

actuals:
  tokens: 9204
  tasks: 2
  commits: 3
  plan_head_before: 63fb83ac175b590af15f14f7b0f1c0f708aa861e

tech-stack:
  added: []
  patterns:
    - "One catch, two mutually exclusive channels: the notFound branch sets a flag and returns before the fallback showError, so a block and a toast can never describe the same failure"
    - "Routing state is knowable before the request: a malformed URL is classified from route params, so the invalid-param branch returns without calling the API at all"
    - "Catch-all placement as the shadowing guard: vue-router matches equally-ranked routes in declaration order, so last-position is the correctness mechanism, not a style choice"
    - "Table-not-history assertions: the spec drives createMemoryHistory over the exported routes array, keeping route coverage free of browser-history and pinia side effects"

key-files:
  created:
    - hhs-frontend-v2/src/views/NotFound.vue
    - hhs-frontend-v2/src/__tests__/views/ScreeningDetailNotFound.spec.ts
    - hhs-frontend-v2/src/__tests__/views/RouteNotFound.spec.ts
    - .planning/phases/14-error-rollout/14-LEDGER-04.md
  modified:
    - hhs-frontend-v2/src/views/screening/Detail.vue
    - hhs-frontend-v2/src/views/screening/Compare.vue
    - hhs-frontend-v2/src/router/index.ts

key-decisions:
  - "Detail.vue uses the content scope and Compare.vue the page scope for the same component: the detail page has a shell worth keeping (header, back button, other data), while a malformed comparison URL leaves nothing on the page worth preserving. Both compose ErrorState — no second wrapper was introduced"
  - "The notFound branch returns before showError rather than calling it with presentStates: false, so the two channels are mutually exclusive by control flow instead of by option value. A reader cannot reorder the lines without the spec failing"
  - "The non-404 fallback on both pages carries presentStates: true because neither page renders a block for a forbidden kind. Without it a 403 on either page would be silent, which is the failure mode presentStates exists to prevent"
  - "The catch-all gets no meta at all — not requiresAuth, not requiresAdmin. An unauthenticated visitor to a nonexistent URL should see the 404, not be bounced to login, and a role gate here would be the second source of truth A-3 forbids"
  - "router.beforeEach was not touched. Its existing else { next() } branch already handles an unmatched path whose meta.requiresAuth is undefined"
  - "The routes array is exported read-only for the spec. Importing the production router singleton would run createWebHistory and drag in the auth store under happy-dom, which would test the harness rather than the table"
  - "NotFound.vue is a thin view, not a page-level component: all page geometry stays in ErrorState so the S1/S2/S3 page surfaces share one carrier (UI-SPEC Contract 3 §3.6 'do not create a third page-level component')"

patterns-established:
  - "Zero-toast by control flow: a state branch returns before the toast call, so 'exactly zero' is a structural property rather than a flag the next editor must remember to set"
  - "Pre-request classification: when a failure is determinable from the route alone, the handler returns before the API call, which the spec pins by asserting the API mock was never invoked"
  - "Order-as-correctness in routing: a catch-all's position in the children array is the mechanism that prevents shadowing, so the spec asserts the array tail rather than only asserting the route exists"
  - "Dedup-aware toast counting: showError's module-level 2000ms latch is real production behaviour, so a spec with several same-message cases advances Date.now per call instead of weakening the counter"

requirements-completed: [ERR-07]

coverage:
  - id: D1
    description: "A missing screening report renders the content-area 404 block inside the page shell: the header stays rendered and only the report area is replaced"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#404：渲染内容区块、两个有序动作、零 toast，且页头仍在"
        status: pass
    human_judgment: false
  - id: D2
    description: "The 404 block is content scope, not page scope — .error-state exists without the error-state--page modifier, and its title is 18px-class 资源不存在 rather than the 24px page title"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#404：渲染内容区块、两个有序动作、零 toast，且页头仍在 (asserts .error-state present and .error-state--page absent)"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/components/common/ErrorState.vue:163-167 (content title 600 18px/1.3) vs :220-222 (page title 600 24px/1.2)"
        status: pass
    human_judgment: false
  - id: D3
    description: "The block shows exactly two actions in fixed order — 返回列表 primary, 重新加载该记录 default — and neither is a bare 重试"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#404：渲染内容区块、两个有序动作、零 toast，且页头仍在 (buttons[0] 返回列表, buttons[1] 重新加载该记录, aria-label parity, zero 重试)"
        status: pass
    human_judgment: false
  - id: D4
    description: "Rendering the 404 block produces exactly zero toasts on every channel — object-form ElMessage and the function-form ElMessage.error"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#404：渲染内容区块、两个有序动作、零 toast，且页头仍在 (errorToastCount() === 0 and toastCount() === 0)"
        status: pass
    human_judgment: false
  - id: D5
    description: "A genuine screening load failure that is not a 404 produces exactly one toast and no state block, so the two channels never both fire"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#非 404（传输层 500）：不渲染状态块，且恰好一条 toast"
        status: pass
    human_judgment: false
  - id: D6
    description: "A forbidden classification on the detail page yields one toast rather than silence, proving the fallback carries presentStates"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#forbidden：不渲染状态块，且恰好一条 toast（证明 fallback 带了 presentStates）"
        status: pass
    human_judgment: false
  - id: D7
    description: "An invalid comparison route (non-numeric or missing id param) renders the page-scope notFound geometry with a single 返回列表, produces zero toasts, and issues no request"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#非数字 id：页级 notFound 块、单个 返回列表、零 toast，且绝不发请求"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#缺失 id 参数：同样渲染页级块，且不发请求"
        status: pass
    human_judgment: false
  - id: D8
    description: "A comparison load failure on a valid URL stays a toast: 500 and 403 each produce exactly one toast and no state block"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#参数合法但查询 500：不渲染状态块，且恰好一条 toast"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#参数合法但查询 403：不渲染状态块，且恰好一条 toast（证明 presentStates）"
        status: pass
    human_judgment: false
  - id: D9
    description: "Navigating to any unmatched URL renders the page-scope 404 block: the route table holds exactly one pathMatch catch-all named NotFound"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/RouteNotFound.spec.ts#路由表末尾存在 pathMatch catch-all，且名为 NotFound"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/RouteNotFound.spec.ts#未匹配 URL 解析到 NotFound"
        status: pass
    human_judgment: false
  - id: D10
    description: "The catch-all shadows no existing route: /dashboard, a nested layout child, two parameterised routes, and layout-external routes all still resolve to their own names"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/RouteNotFound.spec.ts#既有路由未被遮蔽：/dashboard 仍解析到 Dashboard"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/RouteNotFound.spec.ts#既有路由未被遮蔽：嵌套布局子路由仍解析到自身"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/RouteNotFound.spec.ts#既有路由未被遮蔽：参数化路由仍解析到自身并带上参数"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/RouteNotFound.spec.ts#既有路由未被遮蔽：布局外路由仍解析到自身"
        status: pass
    human_judgment: false
  - id: D11
    description: "The catch-all is the last element of the layout children array — declaration order is what prevents shadowing"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/RouteNotFound.spec.ts#catch-all 是布局 children 数组的最后一项（声明顺序即防遮蔽手段）"
        status: pass
    human_judgment: false
  - id: D12
    description: "The catch-all introduces no role gate and no auth gate: it carries no meta at all, and router.beforeEach still checks only requiresAuth (A-3)"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/RouteNotFound.spec.ts#catch-all 不带 requiresAuth / requiresAdmin —— 未知 URL 不得成为导航绕过"
        status: pass
      - kind: grep
        ref: "grep -c 'requiresAdmin|roles|hasRole' hhs-frontend-v2/src/router/index.ts → 1, unchanged from the pre-task baseline of 1 (the dead meta.requiresAdmin on DevicePlatformConfig)"
        status: pass
    human_judgment: false
  - id: D13
    description: "UI Considerations · error E2 404: the block is a content-area surface with zero toast while the rest of the page stays operable"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#404：渲染内容区块、两个有序动作、零 toast，且页头仍在"
        status: pass
    human_judgment: false
  - id: D14
    description: "UI Considerations · empty E2 404: 404 is itself the content-missing presentation — no ElEmpty renders alongside the block"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#404：渲染内容区块、两个有序动作、零 toast，且页头仍在 (v-if/v-else means the report body — including any empty state inside it — is not rendered)"
        status: pass
      - kind: unit
        ref: "hhs-frontend-v2/src/views/screening/Detail.vue:12-23 (the ErrorState v-if and the report body's v-else are siblings, so they cannot co-render)"
        status: pass
    human_judgment: false
  - id: D15
    description: "UI Considerations · zero-one-many E2 action pair: a resource 404 always has exactly two actions; a page with no parent list uses the page-scope block with one action instead of degrading"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#404：渲染内容区块、两个有序动作、零 toast，且页头仍在 (exactly 2)"
        status: pass
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#非数字 id：页级 notFound 块、单个 返回列表、零 toast，且绝不发请求 (exactly 1 on the page-scope block)"
        status: pass
    human_judgment: false
  - id: D16
    description: "UI Considerations · loading E1/E2: the block renders only after a completed failed request; in-flight remains the existing v-loading"
    requirement: ERR-07
    verification:
      - kind: integration
        ref: "src/__tests__/views/ScreeningDetailNotFound.spec.ts#404：渲染内容区块、两个有序动作、零 toast，且页头仍在 (the block appears only after the rejected promise settles and flushPromises runs)"
        status: pass
    human_judgment: false
  - id: D17
    description: "UI Considerations · overflow E1/E2: the content block caps at 480px and centres with margin auto, shrinking with a narrower parent"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/components/common/ErrorState.vue:130-144 (max-width: 480px, margin: 0 auto, min-height: 320px, padding 48px 24px) — inherited unchanged; this plan adds no geometry"
        status: pass
    human_judgment: false
  - id: D18
    description: "UI Considerations · long-text E1/E2 description: a backend 404 message longer than 40 chars wraps instead of truncating"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/components/common/ErrorState.vue:169-174 (.error-state__desc uses line-height 1.5 with no max-height, no ellipsis, no clamp) — backstop: the copy is a fixed authored sentence that never interpolates err.message, so no runtime path can produce an overlong string here"
        status: pass
    human_judgment: false
  - id: D19
    description: "UI Considerations · long-text E1/E2 action labels: 返回列表 and 重新加载该记录 wrap instead of overflowing the 480px block at 320px"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/components/common/ErrorState.vue:179-186 (flex-wrap: wrap on the actions row) and :224-237 (max-width 639px stacks the actions vertically at full width) — backstop: labels are 4 and 8 characters, within Contract 3 §3.3's ceiling"
        status: pass
    human_judgment: false
  - id: D20
    description: "The two files the plan must not touch are untouched: prevention/Profile.vue and stores/profileCompleteness.ts"
    requirement: ERR-07
    verification:
      - kind: grep
        ref: "git status --short hhs-frontend-v2/src/views/prevention/Profile.vue hhs-frontend-v2/src/stores/profileCompleteness.ts → empty; neither path appears in the plan's diffstat"
        status: pass
    human_judgment: false
  - id: D21
    description: "The full suite is at or above its pre-plan count and the production build succeeds"
    requirement: ERR-07
    verification:
      - kind: unit
        ref: "npm --prefix hhs-frontend-v2 run test:run → 41 files / 502 tests passed (baseline before this plan: 39 files / 487 tests; +2 files, +15 tests)"
        status: pass
      - kind: unit
        ref: "npm --prefix hhs-frontend-v2 run build → vue-tsc -b && vite build succeeded"
        status: pass
    human_judgment: false
---

# Phase 14 Plan 04: S2 content-area 404, the route-invalid state, and the route-level catch-all

## One-liner

A missing screening report now renders a content-area block with 返回列表 + 重新加载该记录 and zero toasts, a malformed comparison URL renders a page-scope block instead of a toast, and any unknown URL finally renders a 页面不存在 page instead of an empty layout.

## What Was Built

Three surfaces, one shared carrier.

**`screening/Detail.vue` — S2 content block.** `notFound` is a state ref, reset at the top of `fetchReportDetail` so a successful retry clears the block. The catch normalises the error and branches: `kind === 'notFound'` sets the flag and **returns before** `showError`, so the block owns that kind exclusively. Every other kind reaches `showError(error, 'screening.loadFailed', { presentStates: true })` — the option is required because this page has no page-scope treatment for a forbidden, and without it a 403 here would be silent. The template puts the block under `v-if="notFound"` and the entire report body (header card, lab results, file preview) under `v-else`, so the two cannot co-render. The page header and back button sit outside both, which is what makes this content scope rather than page scope.

**`screening/Compare.vue` — route-invalid state.** The old `if (!idA || !idB) { ElMessage.error('参数错误'); return }` became a `routeInvalid` flag set before any request, with `Number.isFinite` and `> 0` checks. This is a routing state, not a failed action: the state is knowable from the route alone, so no classification is involved and no doomed call is issued. It renders the page-scope block with a single 返回列表, because a malformed URL leaves nothing on the page worth preserving. The comparison's own load failure still goes to `showError` with `presentStates: true`.

**`views/NotFound.vue` + the catch-all.** 26 lines: one `ErrorState` with `scope="page"`, `variant="notFound"`, title 页面不存在, and one 返回首页 action. No card, no breadcrumb, no wrapper — the page geometry lives in `ErrorState` and nowhere else. The route is `/:pathMatch(.*)*` appended as the **last** child of the layout, which is the correctness mechanism: vue-router matches equally-ranked routes in declaration order, so every existing path being more specific is what keeps the catch-all from swallowing them. It carries no meta at all, and `router.beforeEach` was not touched.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `router/index.ts` did not export the routes array**
- **Found during:** Task 2
- **Issue:** The plan's spec instructions allow "import the exported routes array if one exists" — it did not. Importing the production router singleton would run `createWebHistory` and pull in the auth store under happy-dom, testing the harness rather than the table.
- **Fix:** Added `export { routes }` with a comment explaining why. The spec imports it and drives `createMemoryHistory` itself.
- **Files modified:** `hhs-frontend-v2/src/router/index.ts`
- **Commit:** `50f7cbb`

**2. [Rule 1 - Bug] Cross-case toast dedup would have made two spec cases pass vacuously**
- **Found during:** Task 1, first run of the extended spec
- **Issue:** `showError` has a module-level 2000ms latch keyed on `shown|action` (EP's grouping cannot handle VNodes). Four cases in one file emit the same message, so the second and later cases read `errorToastCount() === 0` — the latch working, not the channel missing. Two Compare cases failed for exactly this reason.
- **Fix:** The spec advances `Date.now` by 60s per read, putting each case outside the latch window while leaving within-case dedup intact. The counter was not weakened.
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/ScreeningDetailNotFound.spec.ts`
- **Commit:** `cf39d4d`

**3. [Rule 1 - Bug] Unreadable indentation inside the new `v-else` block**
- **Found during:** Task 1 post-commit review
- **Issue:** Wrapping the report body in `<template v-else>` left it at the old indent level, so the template read as if the cards were siblings of the `ErrorState` rather than of it.
- **Fix:** Re-indented lines 24-113 by one level. Whitespace only; 502 tests and the build unchanged.
- **Files modified:** `hhs-frontend-v2/src/views/screening/Detail.vue`
- **Commit:** `327fe94`

### Criteria adjusted

**`grep -c "ErrorState" hhs-frontend-v2/src/views/NotFound.vue` is 2, not the plan's stated 1.** The criterion's stated intent is "the view composes the existing block rather than reimplementing geometry," and 2 is the minimum a Vue SFC can achieve: one `import ErrorState from '@/components/common/ErrorState.vue'` line and one `<ErrorState` usage tag. The same pattern holds in every other composing view (`Thresholds.vue` reads 4). The intent is satisfied — the view contains no geometry, no card, no wrapper. The literal count is unreachable without dropping the import.

**`grep -c "requiresAdmin\|roles\|hasRole" hhs-frontend-v2/src/router/index.ts` is 1, unchanged.** The plan asks that this "does not increase" — it did not. The one hit is the pre-existing `meta: { requiresAdmin: true }` on `settings/device-platform` (line 113), which is dead code (no guard reads it) and stays dead: this plan added no role gate and did not touch `beforeEach`.

## Known Stubs

None. The two blocks render authored strings; nothing is wired to an empty value. `NotFound.vue`'s description is a literal sentence and never interpolates the requested path, which is also the T-14-12 XSS mitigation.

## Threat Surface

| Threat | Disposition | Evidence |
|--------|-------------|----------|
| T-14-10 (Information Disclosure, detail 404) | mitigated | The block renders a fixed authored sentence; it never surfaces the report id, the raw response body, or `err.stack`. The `normalizeApiError(error)` result is read only for `.kind`. |
| T-14-11 (Elevation of Privilege, catch-all) | mitigated | The route has no `meta` at all and `beforeEach` is untouched. Pinned by `RouteNotFound.spec.ts#catch-all 不带 requiresAuth / requiresAdmin`. |
| T-14-12 (Tampering / XSS, NotFound view) | mitigated | All copy is a literal; the description does not interpolate URL or query string. |
| T-14-SC (package installs) | n/a | Zero packages installed. |

No new surface beyond the plan's register: the catch-all renders a static block that reads no data.

## Ledger

`.planning/phases/14-error-rollout/14-LEDGER-04.md` — 3 rows, one per original call site (Contract 6.1 defines a row as a call site, not a branch):

| Call site | Form | Why one row |
|-----------|------|-------------|
| `screening/Detail.vue:152` | `状态块` | One catch, two mutually exclusive channels. Recording it once and not twice is what keeps the phase total at 87. |
| `screening/Compare.vue:82` | `状态块` | A routing state reached before any request. |
| `screening/Compare.vue:88` | `presentStates-toast` | No block exists for a forbidden kind on this page. |

## Verification

| Check | Result |
|-------|--------|
| `npm run test:run -- src/__tests__/views/ScreeningDetailNotFound.spec.ts` | 7 passed |
| `npm run test:run -- src/__tests__/views/RouteNotFound.spec.ts` | 8 passed |
| `npm run test:run` | 41 files / 502 tests passed (baseline 39 / 487) |
| `npm run build` | succeeded |
| `grep -c "ElMessage.error"` on Detail.vue / Compare.vue | 0 / 0 |
| `grep -c "presentStates: true"` on Detail.vue / Compare.vue | 1 / 1 |
| `grep -c "pathMatch"` on router/index.ts | 1 |
| `grep -c "requiresAdmin\|roles\|hasRole"` on router/index.ts | 1 (unchanged) |
| `git status` on prevention/Profile.vue, stores/profileCompleteness.ts | empty |

## Self-Check: PASSED

Files verified present:
- `hhs-frontend-v2/src/views/NotFound.vue`
- `hhs-frontend-v2/src/__tests__/views/ScreeningDetailNotFound.spec.ts`
- `hhs-frontend-v2/src/__tests__/views/RouteNotFound.spec.ts`
- `.planning/phases/14-error-rollout/14-LEDGER-04.md`

Commits verified present: `cf39d4d`, `50f7cbb`, `327fe94`.

## Commits

1. `cf39d4d` — feat(14-04): render S2 content-area 404 on the screening detail page with zero toast
2. `50f7cbb` — feat(14-04): add the route-level 404 catch-all and its page-scope NotFound view
3. `327fe94` — style(14-04): indent the screening detail body inside its new v-else block
