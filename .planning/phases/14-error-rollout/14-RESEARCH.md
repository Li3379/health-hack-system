# Phase 14: 全站收尾排查与回归验证 - Research

**Researched:** 2026-11-10
**Domain:** Frontend error-presentation rollout (Vue 3 + Element Plus 2.14.0) + regression baseline lock
**Confidence:** HIGH on inventory/baseline/feasibility; MEDIUM on three contract-internal contradictions flagged below

## Summary

Phase 14 is a **migration + verification** phase, not a design phase. The UI-SPEC is approved (7/7) and binding; this research verifies its feasibility against the actual code and produces the concrete inventory the planner needs.

Three findings dominate planning:

1. **The 87 figure is exactly right** — `grep -rn "ElMessage\.error" src --include=*.vue --include=*.ts | grep -v "__tests__" | wc -l` returns **87** `[VERIFIED: grep on working tree 2026-11-10]`. 8 of them are in stores, matching D-02. Baselines are also exactly as D-05 states: backend **845** tests / 0 failures, frontend **445** tests across **37** files, both green `[VERIFIED: mvn surefire-reports sum; npm run test:run]`.

2. **The 403 page is reachable, but by a narrower path than the UI-SPEC assumes.** The UI-SPEC says `DevicePlatformConfig.vue:285-303` receives real HTTP 403 from `@PreAuthorize("hasRole('ADMIN')")`. That is correct *as a code path* — but `LoginUser.getAuthorities()` returns `Collections.emptyList()` unconditionally, and there is no `role` column in `sys_user`, so **no user can ever satisfy `hasRole('ADMIN')`**. Every authenticated user hitting `/api/admin/device/config/**` gets 403. Additionally `/settings/device-platform` has **no sidebar entry and no link anywhere in the app** — it is reachable only by typing the URL. This makes the 403 surface *more* reliably triggerable for verification (any logged-in user, any time) while making it *less* likely to be hit in normal navigation. Both facts are load-bearing for the UAT script.

3. **Three UI-SPEC internal inconsistencies must be resolved by the planner, not silently inherited.** (a) Contract 6 check ② as written expects "only `stores/auth.ts` success/info/warning" but `ai.ts:123`, `wellness.ts:58`, `push.ts:83` also legitimately emit `ElMessage.success` and are *not* in scope for removal; (b) Contract 6 check ④ as written expects 0 hits of `--el-color-danger` in `.vue` files, but `CriticalConfirmDialog.vue` has 4 legitimate hits for a non-form-item purpose; (c) `prevention/Metrics.vue:131` and `health/Metrics.vue:200` use `error !== 'cancel' && error !== 'close'` — the "close" half is dead (EP's `ElMessageBox` rejects with the string `'cancel'` only), and Contract 6 check ③'s pattern does not match these two sites, so the check would report 0 while 8 hand-written guards still exist.

**Primary recommendation:** Land A-1's three pre-changes first (they are genuinely necessary and the ordering argument is sound), then migrate in **five module-scoped waves** (data-input → health → screening → goals/mood/reminders/realtime → stores+user+settings), each wave gated by `npm run test:run`. Treat the three inconsistencies above as explicit plan tasks with recorded decisions rather than as "follow the spec literally".

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01 (按「用户可见性」分批迁移，而非无差别全量替换):**
- 现状实测：全站 **87 处**裸 `ElMessage.error`（其中 stores 内 8 处），而接入统一 `showError` 的页面仅 **7 处**。
- 迁移原则：**用户可见的失败路径必须走 `showError`**（表单提交、列表拉取、详情加载、删除/导出等主动操作）；**内部静默路径保持静默**（现有 16 处 `{ silent: true }` 调用点、后台预加载、轮询心跳）——这些本就不该弹提示，改成 `showError` 反而制造噪音。
- **验收口径**：验收标准 2 的「裸残留归零」解释为**用户可见路径**裸调用归零；静默路径不算残留（若其确实主动弹了 toast，则它不属静默路径，须迁移）。
- 迁移后必须保留 Phase 11 D-01 的语义：一次失败恰好一条提示（`showError` 内部已有去重）。

**D-02 (stores 内的 8 处按调用方契约处理):**
- Pinia store 不在 UI 层，不应直接弹 toast。store 内的 `ElMessage.error` 一律改为**抛错/返回错误**，由调用它的组件用 `showError` 呈现。
- 例外：store 内确实承担"用户主动动作"且无合理调用方承接的场景，可保留但须在台账注明理由。

**D-03 (403 全页级、404 内容区级，均不弹 toast):**
- **403（无权限）** → 全页级解释页：标题「无权访问此页面」+ 说明当前账号缺少哪个权限 + 「返回首页」动作。语义：整个页面都不可用。
- **404（资源不存在）** → 内容区 `ErrorState` 解释块（复用 Phase 11 已有组件）：标题「资源不存在」+ 说明可能已被删除 + 「返回列表」/「重试」动作。语义：页面壳可用，仅该资源缺失。
- **与 Phase 11 D-02 的分工保持一致**：错误状态块占内容区，toast 只用于瞬时动作失败。403/404 是**状态**不是**动作失败**，故走状态块。
- **不新建全页组件**：403 复用 `ErrorState` 的容器能力扩展一个 `variant="forbidden"`，或用一个轻量的页面级包装，具体形式留给 planner（关键约束是「全页 vs 内容区」的层级差异与「零 toast」）。

**D-04 (取消不是失败，必然零提示):**
- `'cancel'` / `'close'` 哨兵（`appError.ts:41` 的 `CANCEL_SENTINELS`）与 `AbortError`（`:50`）已能识别为 `kind: 'cancel'`，`request.ts:65` 与 `showError.ts:129` 也已短路。
- **本轮要做的**：清理**绕过**这两个短路、自行 `catch (e) { if (e !== 'cancel') ElMessage.error(...) }` 的手写判断（实测至少 `goals/Index.vue:157`、`health/Alerts.vue:224` 这类写法）。这类手写判断在 `ElMessageBox` 取消时能用，但遇到 `AbortError` 或归一化后的 `ApiError('', 'cancel')` 就会漏判 → 误弹红提示。
- **统一做法**：这类地方改用 `showError`（它已识别 cancel）或直接 `catch` 后不做任何提示。

**D-05 (锁定当前实测值为基线，而非 ROADMAP 原文的 90):**
- ROADMAP 写的「不少于既有 90 项基线」是 v1.0 时期的陈旧口径。本阶段实测：**后端 845 项、前端 445 项 / 37 文件**。
- **基线锁定为**：后端 `mvn clean verify -DskipITs` 全绿且 ≥ 845；前端 `npm run test:run` 全绿且 ≥ 445（新增测试只能加不能减）。
- 同时锁定：`npm run build` 绿、`lint:check` 0 errors、`checkstyle:check` 0 violations。
- **E2E**：`quick-input.spec.ts` 的成功文案契约仍通过（需后端运行）。

**D-06 (浏览器实跑 + E2E 并行):**
- 验收标准 3（停后端逐页点保存/加载、断网 60 秒轮询不重复 toast）**用真实浏览器实跑**，结果写入 UAT——这类行为依赖真实心跳/轮询节奏，单测无法覆盖。
- 同时保留 `quick-input.spec.ts` 等既有 E2E 作为自动化回归。
- 实跑清单：至少覆盖 3 类页面（列表页加载、表单提交、详情页），每类验证「恰好一条中文可行动提示」。

### Claude's Discretion

- 87 处迁移的具体分批顺序与批次边界
- 403 全页状态的具体实现形式（扩展 `ErrorState` variant vs 轻量包装组件）
- store 内 8 处错误的具体改造方式（抛错 vs 返回错误结果）
- 台账（migration ledger）的载体与字段（Markdown 表格 vs JSON）
- 「恰好一条提示」的观测方法（toast 计数用 DOM 查询还是网络请求计数）

### Deferred Ideas (OUT OF SCOPE)

- **错误监控上报**（Sentry 类）：本阶段只做用户可见呈现，不上报后端
- **错误文案国际化**（i18n 多语言）：当前只做中文
- **自动化视觉回归**（截图对比）：本阶段用实跑 + 断言，不引入视觉 diff 工具
- **错误码文档化**（给运营/客服的错误码手册）：独立文档工作，非本阶段
- **Testcontainers 集成测试环境修复**：WINDOWS.md #3 记录的 Docker socket 限制，属基础设施，不在本阶段
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ERR-03 | 后端字段校验错误（`GlobalExceptionHandler` 返回的 `errors[]`）解析并内联回填到对应 `el-form-item`，字段名映射为中文标签 | §Forms Requiring `useFormServerErrors` — 16 `<el-form>` files inventoried, 3 wired today (not 4), 13 remaining with `prop` naming per file; `FIELD_LABELS` 17→39 key map; `labels` override table for ambiguous fields |
| ERR-05 | 校验失败或服务端报错后不清空用户已输入内容，弹窗保持打开且值保留 | §Non-destructive Audit — every migrated `catch` must not call `resetFields`; `apply()` never writes back to the model; `clearField` only clears error text |
| ERR-07 | 网络错误、校验错误、权限错误在呈现方式与文案上可区分（校验内联、网络 toast 可重试、403/404 解释性状态） | §403/404 Trigger Paths — real 403 confirmed reachable (and confirmed to be the *only* reachable path for every user); 404 envelope codes enumerated from `ErrorCode`; insertion point in `appError.ts` verified at line 110/116 boundary |
| ERR-08 | 清理用户取消操作（`'cancel'` 哨兵值、`AbortError`）被误判为失败并弹出红色提示的问题 | §Cancel-Misjudgement Sites — 8 hand-written guards (not 2) with exact replacement forms; 1 additional `CanceledError` name check in `Score.vue` |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Error classification (`ErrorKind` derivation) | Browser / Client (`utils/appError.ts`, pure function) | — | Pure translation module; forbidden to import pinia/router/EP (module header lines 2-3) so it stays testable and cycle-free |
| Error presentation (toast) | Browser / Client (`utils/showError.ts`) | — | Single legal `ElMessage` caller; `request.ts` is translate-only by Phase 11 D-01 |
| Server field-error → inline form binding | Browser / Client (`composables/useFormServerErrors.ts` + `el-form-item :error`) | — | EP's `:error` prop is the only anchor that also sets `.is-error` (verified in EP 2.14.0 source) |
| 403 full-page state | Browser / Client (`ErrorState` `scope="page"`) | — | Server owns the *decision* (403); client owns the *rendering*. Client has no role data, so it must never pre-judge |
| 404 content-area state | Browser / Client (`ErrorState` content variant) | — | Same split: server returns 404/envelope code, client renders |
| Error-border visual spec | Browser / Client (`theme.css` global block) | — | Must be global: `!important` overrides from `theme.css:504-506`/`:672-677` can only be beaten globally with `body`-prefixed higher specificity |
| Polling / heartbeat silence | Browser / Client (`App.vue` `{ silent: true }`) | API / Backend | Silence is declared at the call site (Phase 11 D-09); backend never knows |
| Role gating (403 *cause*) | API / Backend (`@PreAuthorize`) | — | Client cannot gate: `UserVO` has no role, `meta.requiresAdmin` is unread dead code |

## Standard Stack

No new dependencies. Everything this phase needs is already installed.

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `element-plus` | **2.14.0** (installed; `package.json` declares `^2.5.6`) | `ElMessage`, `ElMessageBox`, `el-form-item :error`, `.is-error` | Already the sole UI kit; UI-SPEC Contract 4 is written against 2.14.0 source `[VERIFIED: node_modules/element-plus/package.json → 2.14.0]` |
| `vue` | 3.x (`^3.4.15` per `FormErrorSummary.vue` note) | Composition API, `nextTick`, `h()` | Existing; `useId()` unavailable (3.5+) so `FormErrorSummary` uses a module counter |
| `vitest` | `^4.1.0` | Frontend unit tests | Existing; `happy-dom` environment, `globals: true` `[VERIFIED: vitest.config.ts]` |
| `axios` | existing | HTTP transport | Existing; `request.ts` wraps it, interceptor is translate-only |
| `pinia` | existing | Stores (8 error sites) | Existing |
| `@playwright/test` | installed, browsers cached (`chromium-1217`, `chromium-1223`) | E2E `quick-input.spec.ts` | Existing; `webServer` auto-starts `npm run dev` on 5173 `[VERIFIED: playwright.config.ts]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `@element-plus/icons-vue` | existing | `WarningFilled`, `Lock`, `DocumentDelete` | Already imported by `ErrorState.vue:3`; no new icons needed |
| `unplugin-vue-components` + `ElementPlusResolver` | existing | Auto-imports EP components + **re-injects EP CSS per route chunk after `theme.css`** | This is *why* Contract 4 uses `body`-prefixed selectors; never rely on load order `[VERIFIED: theme.css:1207-1217 comment + vite.config.ts]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Extending `ErrorState` with `scope`/`actions` (UI-SPEC choice) | New lightweight page-level wrapper component | UI-SPEC §2.1 already decided: one component, one spec file. Research agrees — a second component doubles the test surface for zero semantic gain |
| `body .el-form-item.is-error ...` (specificity `0,4,1`) | `!important` on bare `.el-form-item.is-error` | Would lose to `theme.css`'s `!important` at equal-or-higher specificity in some injection orders; `body` prefix is the project's documented antidote |
| Throw from stores (D-02 option A) | Return `{ ok, error }` result objects | Both viable; see §Store-Internal 8 for per-call recommendation based on existing caller shape |

**Installation:** none — `npm ci` is the only install step, and it is already satisfied.

**Version verification:**
```
element-plus  installed: 2.14.0   (package.json declares ^2.5.6)
vitest        installed: 4.x      (package.json declares ^4.1.0)
vue           declared:  ^3.4.15
```
`[VERIFIED: node_modules + package.json read this session]`

## Package Legitimacy Audit

**Not applicable — this phase installs zero external packages.** All work uses already-installed `element-plus@2.14.0`, existing project modules (`utils/appError.ts`, `utils/showError.ts`, `utils/errorMessages.ts`, `composables/useFormServerErrors.ts`, `components/common/ErrorState.vue`, `components/common/FormErrorSummary.vue`), and `theme.css`. UI-SPEC `## Registry Safety` confirms: "本阶段**不引入任何外部 registry 组件**".

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## A-1 Ordering Constraint — Verified Necessary

The UI-SPEC's A-1 claim is that three changes must land **before** the 87 migrations. Research confirms each is genuinely blocking, and identifies the exact files touched.

| # | Pre-change | Files touched | Why it is truly blocking (verified) |
|---|-----------|---------------|--------------------------------------|
| 1 | `ErrorKind` gains `'forbidden' \| 'notFound'` + detection rules | `src/utils/appError.ts` (type union lines 5-12; insert detection between line 110 and 116; update the "分类顺序即契约" doc comment at lines 55-74) **and** `src/__tests__/utils/appError.spec.ts` (add 4 anti-regression cases) | Verified: `normalizeApiError` today has **no** branch that can ever produce a 403/404 kind. Grep for `403`/`404` in `appError.ts` returns nothing. If migration lands first, every 403/404 becomes `business` (envelope) or `server` (real 403, since `status >= 500` is false → falls to step 7 `server` with `retryable` unset) — so `showError` would render a **retryable server toast** for a permission denial. That is worse than a plain business toast: it invites the user to retry something that can never succeed. |
| 2 | `theme.css` error-border global block | `src/assets/styles/theme.css` (append after the Phase 11 error block that ends at line 1242) | Verified: `.el-input__wrapper { border: 1px solid var(--border) !important; box-shadow: none !important }` at lines 502-508 and `.el-select__wrapper { … box-shadow: none !important }` at 672-677. EP's error inset shadow (`.el-form-item.is-error … { box-shadow: 0 0 0 1px var(--el-color-danger) inset }`, **no** `!important`) is therefore fully suppressed for input and select. Textarea is *not* overridden in `theme.css` (grep `el-textarea` in theme.css → 0 hits), so EP's inset shadow still applies there — confirming the UI-SPEC's "two control families, two declarations" split. |
| 3 | `showError` `presentStates` option + `ErrorState` `scope`/`actions` props + `needsVNode` extension | `src/utils/showError.ts` (`ShowErrorOptions` interface lines 24-39; `needsVNode` line 171), `src/components/common/ErrorState.vue` (props lines 5-18; template; scoped style), `src/__tests__/utils/showError.spec.ts`, `src/__tests__/components/ErrorState.spec.ts` | Verified: `needsVNode = hasRetry \|\| hasAction \|\| err.kind === 'network' \|\| err.kind === 'server'` at **line 171**; the string branch at 172-184 hardcodes `duration: 4000` at **line 178**. `presentStates` does not exist anywhere in `src` (grep → 0 hits). Without change 3, an action-type `forbidden`/`notFound` has nowhere to go, and executors will reach for `ElMessage.error` — re-introducing exactly what the phase clears. |

**Additional ordering fact not in the UI-SPEC:** change 1 must also update the doc comment block at `appError.ts:55-74`, which explicitly says "分类顺序即契约，不可调换" and lists steps 0–7. The UI-SPEC mentions this; research confirms the comment is a real numbered list (0 ApiError passthrough, 1 cancel, 2 validation, 3 401, 4 no-response, 5 real 5xx, 6 envelope, 7 fallback) and must become a 9-step list with the new steps inserted **between 3 and 4**.

## A-2 Four Anti-Regression Tests — Exact Insertion Point Verified

**Insertion point:** in `normalizeApiError`, after the 401 guard (line 110) and before the `if (!err?.response)` block (line 116). Verified structure:

```
 90-105  step 2  validation (data.fieldErrors)
110      step 3  401                 ← new kinds go AFTER this
116-130  step 4  no-response         ← and BEFORE this
133-143  step 5  real 5xx
152-154  step 6  envelope business
157      step 7  fallback
```
`[VERIFIED: src/utils/appError.ts:90-157 read this session]`

**Do the four tests pin the real risk?** Yes — and research confirms the UI-SPEC's C3 revision is the accurate one.

| Test | Risk pinned | Verified mechanism |
|------|-------------|--------------------|
| ① real HTTP 403 → `forbidden` | Real 403 currently falls through step 5 (`403 >= 500` false) to step 7 → `new ApiError(data?.message \|\| '服务暂时不可用', 'server', 403)`. So a permission denial today renders a **server** toast, and `server` is in `needsVNode`, so it renders a **sticky toast with a retry button** for an action that can never succeed. | `GlobalExceptionHandler.handleAccessDenied` returns `ResponseEntity.status(403)` with body `Result.failure(403, 'auth.forbidden', '没有访问权限')` `[VERIFIED: GlobalExceptionHandler.java:202-206]` |
| ② envelope `errorCode: 'auth.forbidden'` + `code: 500` → `forbidden` (**not** `business`) | Envelope-form 403. `Result.failure(String code, String message)` calls `parseCodeToInt(code)`, which returns **500** for any non-numeric code `[VERIFIED: Result.java:81-83, 140-146]`. So `data.code` is 500 → step 6 matches (`typeof data?.code === 'number' && data.code !== 200`) → `business`. The new branch must therefore match on `data.errorCode`, not `data.code`. | `parseCodeToInt` catches `NumberFormatException` → `return 500` |
| ③ envelope `code: 404` → `notFound` | `handleNoResourceFoundException` returns `Result.failure(404, 'resource.not_found', '请求的资源不存在')` with **HTTP 200** envelope `[VERIFIED: GlobalExceptionHandler.java:235-240]`. Step 6 would catch it as `business` (404 !== 200). New branch must test `data?.code === 404` **before** step 6. | Envelope code is numeric 404 here (unlike business exceptions) |
| ④ `errorCode: 'health.profile_not_found'` → `notFound` (**not** `business`) | `BusinessException(HEALTH_PROFILE_NOT_FOUND)` → `Result.failure(ec.getCode(), message).withErrorCode(ec.getCode())` where `ec.getCode()` is the **string** `'health.profile_not_found'` → numeric code 500, `errorCode` string set `[VERIFIED: GlobalExceptionHandler.java:57-73 + ErrorCode.java:44]`. Again must match `errorCode`. | Same string-code path as ② |

**Confirmed: the UI-SPEC's C3 revision is correct and the original worry was overstated.** `prevention/Profile.vue:395-400` and `stores/profileCompleteness.ts:67` read `err.status` / `err.code`, and the existing `business` branch at `appError.ts:152-154` passes both through unchanged (`new ApiError(data.message || '', 'business', status, data.errorCode)`). So those two branches will **not** silently break when `notFound` is added. The real risk is narrower and exactly as C3 says: the state-block channel becomes dead code, and envelope 403/404 render as business toasts instead of state blocks.

**Exact predicate shapes that satisfy all four tests** (the planner should specify these, since `/(^|\.)forbidden$/` alone fails test ④'s sibling case `goal.forbidden` — it passes, but `auth.forbidden` also passes only because `.` matches; the UI-SPEC's `/(^|\.)forbidden$/` works for both, but the 404 rule `/(^|_|\.)not[._]?found$/` must be checked against `health.profile_not_found` (underscore form), `resource.not_found` (dot form), `goal.not_found`, `mood.entry_not_found`, `user.not_found`, `resource.file_not_found` — all six exist in `ErrorCode.java:35-65`).

**Ordering caveat the planner must state explicitly:** the 404 predicate must be evaluated **before** step 6, and it must accept both `data?.code === 404` (numeric, from `handleNoResourceFoundException`) **and** the `errorCode` regex (string, from `BusinessException`). The UI-SPEC's table says exactly this; research confirms both paths exist.

## The 87 Bare `ElMessage.error` Calls — Classified Inventory

Count verified: **87** `[VERIFIED: grep 2026-11-10]`. Distribution matches CONTEXT exactly (8 in stores). The full classified table follows. "Bucket" uses D-01's two buckets: **U** = user-visible action (must migrate to `showError`), **S** = internal/silent (must stay silent or be deleted).

### Stores (8) — D-02 applies

| # | file:line | Method | Original copy | Bucket | Recommended form | Caller / rationale |
|---|-----------|--------|---------------|--------|------------------|--------------------|
| 1 | `stores/ai.ts:70` | `loadSessions` catch | `加载会话失败` | U (via caller) | **throw** `normalizeApiError(error)` | Only caller `Chat.vue:624` in `onMounted` — wrap in `showError(error, 'ai.loadFailed')` |
| 2 | `stores/ai.ts:105` | `sendMessage` catch | `error.message \|\| '发送失败'` | U (via caller) | **return `false`** + rethrow is not viable (caller branches on boolean) — keep `return false`, add `lastError` ref OR have caller catch. Simplest: keep boolean, caller shows nothing (send failure already pops the optimistic message). **Recommend: return `false`, no toast, caller stays silent** — see note below |
| 3 | `stores/ai.ts:126` | `deleteSession` catch | `删除会话失败` | U | **throw** | Caller `Chat.vue:465` is inside `try { await … } catch { /* User cancelled */ }` — that catch currently swallows *both* cancel and failure. Must become `catch (e) { showError(e, 'ai.deleteFailed') }` |
| 4 | `stores/ai.ts:137` | `getHistory` catch | `获取历史记录失败` | U | **throw** | Caller `Chat.vue:496` `showHistory()` — no try/catch today; add `showError(error, 'ai.loadFailed')` |
| 5 | `stores/wellness.ts:22` | `fetchSummary` catch | `获取保健数据摘要失败` | U | **throw** | Callers `Dashboard.vue:276,349` — page-level load, prefer `ErrorState` over toast |
| 6 | `stores/wellness.ts:43` | `fetchTrend` catch | `获取趋势数据失败` | U | **throw** | Callers `Dashboard.vue:333,341` — chart-area load |
| 7 | `stores/wellness.ts:64` | `addMetric` catch | `添加失败` | U | **return `false`** (already does) + caller presents | No `addMetric` caller found in `views/` — the actual add path goes through `UniversalMetricDialog`. Verify before migrating; if truly uncalled, **delete the branch** (dead code) |
| 8 | `stores/floatingAi.ts:120` | `sendMessage` catch | `error.message \|\| 'AI 对话失败'` | U (via caller) | **return `false`** (already does); caller `FloatingChatPanel.vue:215` reads the boolean | Caller currently shows nothing on `false`; add `showError` in caller or leave the in-ball message surface (the floating panel has its own inline error area — verify) |

**Note on #2 and #8 (send-message paths):** these are *not* silently failing today — the store pops the optimistic user message and returns `false`, and the UI removes the bubble. Migrating them to a toast is a **behaviour change** (adds a toast where the user previously saw the message vanish). The planner should decide explicitly: either (a) keep them toast-free and instead render an inline "发送未完成，请重试" bubble, or (b) accept a `showError` toast. UI-SPEC Contract 5 §5.2 assigns `ai.loadFailed`/`ai.deleteFailed` keys but has **no key for send failure** — a gap the planner must close (either reuse `server.unavailable` or add `ai.sendFailed`).

### Views (79)

Grouped by module for wave planning. Every entry is user-visible unless marked otherwise.

**`views/data-input/components/` (36 sites — largest module)**

| file:line | Function | Original copy | Bucket | Recommended form |
|-----------|----------|---------------|--------|------------------|
| `DeviceSync.vue:496` | `handleConnect` else | `res.message \|\| '连接失败'` | **DEAD** | `res.code !== 200` already rejects in the interceptor (`request.ts:45-52`), so the `else` at 496 is unreachable → **delete**, migrate the `catch` only |
| `DeviceSync.vue:501` | `handleConnect` catch (offline) | `offlineMessage` | U | `showError(error, 'network.offline')` — `isOnline` guard becomes unnecessary (normalizer detects `isAxiosError`) |
| `DeviceSync.vue:503` | `handleConnect` catch | `error.message \|\| '连接失败'` | U | `showError(error, 'device.loadFailed')` |
| `DeviceSync.vue:528` | `handleDisconnect` else | `res.message \|\| '断开失败'` | **DEAD** | delete (same reason) |
| `DeviceSync.vue:556` | `handleSync` inner else (`data.status !== 'success'`) | `data.errorMessage \|\| '同步失败'` | U — **but not an HTTP failure** | This is a *successful* HTTP call reporting a failed sync. `showError` would misclassify it (no `response` → `unknown`). Keep a dedicated toast but **must not** use `ElMessage.error` → use `showError(new ApiError(data.errorMessage, 'business'), 'device.syncFailed')` |
| `DeviceSync.vue:565` | `handleSync` else | `res.message \|\| '同步失败'` | **DEAD** | delete |
| `DeviceSync.vue:576` | `handleSync` catch (offline) | `offlineMessage` | U | `showError(error, 'network.offline')` |
| `DeviceSync.vue:583` | `handleSync` catch | `error.message \|\| '同步失败'` | U | `showError(error, 'device.syncFailed')` |
| `DeviceSync.vue:629` | `handleSyncAll` catch (offline) | `offlineMessage` | U | `showError(error, 'network.offline')` |
| `DeviceSync.vue:631` | `handleSyncAll` catch | `error.message \|\| '同步失败'` | U | `showError(error, 'device.syncFailed')` |
| `DeviceSync.vue:655` | `loadSyncHistory` catch | `加载同步历史失败` | U (dialog sub-panel) | `showError(error, 'device.loadFailed')` |
| `AIInput.vue:194` | `handleParse` else | `res.message \|\| '解析失败'` | **DEAD** | delete |
| `AIInput.vue:231` | `handleConfirm` else | `res.message \|\| '录入失败'` | **DEAD** | delete |
| `AIInput.vue:247` | `handleApiError` | `axiosError.response.data.message` | U | **replace whole helper** with `showError(error, defaultKey)`; `handleApiError` exists only because the pre-Phase-11 code peeked at `error.response`, which `ApiError` does not have |
| `AIInput.vue:251` | `handleApiError` (429) | `请求过于频繁，请稍后再试` | U | 429 is `status 429` → falls to step 7 `server`. Keep the special copy by passing a distinct key, or drop the special-case (backends returns Chinese anyway) |
| `AIInput.vue:256` | `handleApiError` | `error.message \|\| defaultMessage` | U | collapsed into the replacement |
| `AIInput.vue:258` | `handleApiError` | `defaultMessage` | U | collapsed into the replacement |
| `OcrInput.vue:196` | `handleFileChange` | `文件大小不能超过10MB` | **NOT a failure** | Client-side pre-validation. Keep a toast, but it is **not** an API error → `showError(new ApiError('文件大小不能超过10MB','validation'), 'ocr.uploadFailed')` or keep as-is and ledger it as "保持（客户端校验，非失败路径）" |
| `OcrInput.vue:202` | `handleFileChange` | `只能上传图片文件` | **NOT a failure** | same as 196 |
| `OcrInput.vue:261` | `handleRecognize` catch | `errorMessage` | U | `showError(error, 'ocr.uploadFailed')`; note it also sets `recognizeResult.status='failed'` — **keep that**, it is the content-area state |
| `OcrInput.vue:295` | `handleConfirm` else | `res.message \|\| '录入失败'` | **DEAD** | delete |
| `OcrInput.vue:299` | `handleConfirm` catch | `errorMessage` | U | `showError(error, 'metric.saveFailed')` |
| `AiHistory.vue:155` | `loadHistory` catch | `加载历史记录失败` | U | `showError(error, 'ai.loadFailed')` |
| `AiHistory.vue:174` | `viewDetail` else | `res.message \|\| '获取详情失败'` | **DEAD** | delete |
| `AiHistory.vue:177` | `viewDetail` catch | `获取详情失败` | U | `showError(error, 'ai.loadFailed')` |
| `AiHistory.vue:193` | `handleDelete` else | `res.message \|\| '删除失败'` | **DEAD** | delete |
| `AiHistory.vue:196` | `handleDelete` catch | `删除失败` | U | `showError(error, 'ai.deleteFailed')` |
| `OcrHistory.vue:166` | `loadHistory` catch | `加载历史记录失败` | U | `showError(error, 'ai.loadFailed')` (or a new `ocr.loadFailed`) |
| `OcrHistory.vue:185` | `viewDetail` else | `res.message \|\| '获取详情失败'` | **DEAD** | delete |
| `OcrHistory.vue:188` | `viewDetail` catch | `获取详情失败` | U | `showError(error, 'ai.loadFailed')` |
| `OcrHistory.vue:204` | `handleDelete` else | `res.message \|\| '删除失败'` | **DEAD** | delete |
| `OcrHistory.vue:207` | `handleDelete` catch | `删除失败` | U | `showError(error, 'ai.deleteFailed')` |
| `DeviceConfigWizard.vue:343` | `loadStatus` catch | `加载配置状态失败` | U | `showError(error, 'device.loadFailed')` |

**`views/health/` (14 sites)**

| file:line | Function | Original copy | Bucket | Recommended form |
|-----------|----------|---------------|--------|------------------|
| `Alerts.vue:179` | `fetchAlerts` catch | `获取预警失败` | U | `showError(error, 'alert.loadFailed')` (or `ErrorState`) |
| `Alerts.vue:200` | `markRead` catch | `操作失败` | U | `showError(error, 'alert.updateFailed')` — **banned copy** `操作失败` |
| `Alerts.vue:210` | `acknowledge` catch | `操作失败` | U | `showError(error, 'alert.updateFailed')` — **banned copy** |
| `Alerts.vue:225` | `markAllRead` catch | `操作失败` | U + **cancel guard** | `catch (e) { showError(e, 'alert.updateFailed') }` — drop `if (error !== 'cancel')` |
| `Alerts.vue:240` | `handleDelete` catch | `删除失败` | U + **cancel guard** | `catch (e) { showError(e, 'alert.deleteFailed') }` |
| `Metrics.vue:157` | `fetchMetrics` catch | `获取数据失败` | U | `showError(error, 'metric.loadFailed')` (or `ErrorState`) |
| `Metrics.vue:201` | `handleDelete` catch | `删除失败` | U + **cancel guard (`!== 'cancel' && !== 'close'`)** | `catch (e) { showError(e, 'metric.deleteFailed') }` |
| `Score.vue:444` | `fetchScore` catch | `获取评分失败` | U | `showError(error, 'score.loadFailed')` |
| `Score.vue:457` | `recalculate` catch | `计算失败` | U | `showError(error, 'score.loadFailed')` — no `score.calcFailed` key exists |
| `Score.vue:483` | `generateReport` catch | `error.response?.data?.message \|\| '获取报告失败…'` | U | `showError(error, 'report.generateFailed')` — **`error.response` is always `undefined` on an `ApiError`**, so this branch always takes the fallback today |
| `Score.vue:508` | `regenerateReport` catch (non-cancel) | `error.response?.data?.message \|\| '生成报告失败'` | U + **cancel guard (`name === 'CanceledError' \|\| 'AbortError'`)** | `catch (e) { showError(e, 'report.generateFailed') }` — hand-rolled name check is exactly what `isCancel` already does. **Also note:** `reportAbortController` is created but **never passed as `signal`** to `scoreApi.generateReport()` (verified: no `signal` in `Score.vue` or `api/score.ts`), so abort is a no-op today — the guard is dead defensive code |
| `Alerts.vue:179,200,210,225,240` | (5 rows above) | — | — | — |

**`views/screening/` (6 sites)**

| file:line | Function | Original copy | Bucket | Recommended form |
|-----------|----------|---------------|--------|------------------|
| `List.vue:154` | `fetchReports` catch | `获取报告列表失败` | U | `showError(error, 'screening.loadFailed')` (or `ErrorState`) |
| `List.vue:198` | upload catch | `上传失败` | U | `showError(error, 'ocr.uploadFailed')` |
| `List.vue:219` | `handleDelete` catch | `删除失败` | U + **cancel guard** | `catch (e) { showError(e, 'screening.deleteFailed') }` |
| `Detail.vue:152` | `fetchReportDetail` catch | `获取报告详情失败` | U → **404 target** | This is the primary S2 (404 content-area) site: catch must branch on `err.kind === 'notFound'` → render `ErrorState`; otherwise `showError(error, 'screening.loadFailed')` |
| `Compare.vue:82` | `fetchComparison` early return | `参数错误` | **NOT a failure** | Invalid route params — a *routing* problem. Should render `ErrorState variant="notFound"` (route-level) rather than a toast; at minimum keep it toast-free and set a state flag |
| `Compare.vue:88` | `fetchComparison` catch | `获取对比数据失败` | U | `showError(error, 'screening.loadFailed')` |

**`views/goals/` (4 sites)**

| file:line | Function | Original copy | Bucket | Recommended form |
|-----------|----------|---------------|--------|------------------|
| `Index.vue:152` | `fetchGoals` catch | `获取目标列表失败` | U | `showError(error, 'goal.loadFailed')` (or `ErrorState`) |
| `Index.vue:154` | `handleCreate` catch | `创建目标失败` | U + form | `useFormServerErrors` + `showError(error, 'goal.saveFailed')`; `apply(err)` on `validation` |
| `Index.vue:156` | `handleAddProgress` catch | `更新进度失败` | U + form | same, `'goal.saveFailed'` |
| `Index.vue:157` | `handleDelete` catch | `删除失败` + **`if (e !== 'cancel')`** | U + **cancel guard** | `catch (e) { showError(e, 'goal.deleteFailed') }` |

**`views/reminders/` (4 sites)** — all in `Index.vue`

| line | Function | Original copy | Bucket | Recommended form |
|------|----------|---------------|--------|------------------|
| 139 | `fetchReminders` catch | `获取提醒列表失败` | U | `showError(error, 'reminder.loadFailed')` |
| 169 | `handleCreate` catch | `创建提醒失败` | U + form | `useFormServerErrors` + `showError(error, 'reminder.saveFailed')` |
| 181 | `handleToggle` catch | `更新状态失败` | U | `showError(error, 'alert.updateFailed')` → better: add `reminder.updateFailed`. **Note the existing bug:** it calls `fetchReminders()` inside the catch, i.e. a second request after a failure |
| 194 | `handleDelete` catch | `删除失败` + cancel guard | U + **cancel guard** | `catch (e) { showError(e, 'reminder.deleteFailed') }` |

**`views/mood/` (3 sites)** — all in `MoodTracker.vue`

| line | Function | Original copy | Bucket | Recommended form |
|------|----------|---------------|--------|------------------|
| 127 | `fetchData` catch | `获取数据失败` | U | `showError(error, 'mood.loadFailed')` — **key does not exist in the UI-SPEC's 26**; planner must add `mood.loadFailed` or reuse `server.unavailable` |
| 149 | `handleSubmit` catch | `记录失败` | U | `showError(error, 'mood.saveFailed')` |
| 155 | `handleDelete` catch | `删除失败` + cancel guard | U + **cancel guard** | `catch (e) { showError(e, 'mood.deleteFailed') }` — key missing from spec; add or reuse |

**`views/realtime/` (2 sites)** — both in `Monitor.vue`

| line | Function | Original copy | Bucket | Recommended form |
|------|----------|---------------|--------|------------------|
| 328 | `fetchTrend` catch | `获取趋势数据失败` | U | `showError(error, 'realtime.loadFailed')` |
| 351 | `handleAdd` catch | `添加失败` | U | `showError(error, 'realtime.saveFailed')` |

**`views/prevention/` (4 sites)**

| file:line | Function | Original copy | Bucket | Recommended form |
|-----------|----------|---------------|--------|------------------|
| `Metrics.vue:100` | `fetchMetrics` catch | `获取数据失败` | U | `showError(error, 'metric.loadFailed')` (or `ErrorState`) |
| `Metrics.vue:132` | `handleDelete` catch | `删除失败` + `!== 'cancel' && !== 'close'` | U + **cancel guard** | `catch (e) { showError(e, 'metric.deleteFailed') }` |
| `Risk.vue:310` | `fetchAssessments` catch | `获取评估列表失败` | U | `showError(error, 'profile.loadFailed')` — no `risk.loadFailed` key; add or reuse |
| `Risk.vue:335` | `createRiskAssessment` catch | `创建评估失败` | U | `showError(error, 'profile.saveFailed')` — same gap |

**`views/settings/` (4 sites)** — all in `DevicePlatformConfig.vue`

| line | Function | Original copy | Bucket | Recommended form |
|------|----------|---------------|--------|------------------|
| 299 | `loadConfigs` catch | `加载配置失败` | U → **403 target** | **This is the S1 site.** `catch` must set a `forbidden` state flag and render `<ErrorState scope="page" variant="forbidden">`; zero toast. Non-403 → `showError(error, 'device.loadFailed')` |
| 332 | `handleSaveConfig` else | `res.message \|\| '保存失败'` | **DEAD** | delete |
| 335 | `handleSaveConfig` catch | `error.message \|\| '保存失败'` | U + form | `useFormServerErrors` + `showError(error, 'device.saveFailed', { presentStates: true })` |
| 355 | `handleTestConfig` catch | `error.message \|\| '测试失败'` | U | `showError(error, 'device.saveFailed', { presentStates: true })` — action-type, `presentStates` per Contract 1.2 |

**`views/user/` (6 sites)** — all in `Profile.vue`

| line | Function | Original copy | Bucket | Recommended form |
|------|----------|---------------|--------|------------------|
| 223 | `fetchProfile` catch | `获取个人信息失败` | U | `showError(error, 'user.loadFailed')` |
| 242 | `handleSubmit` catch | `更新失败` | U + form | `useFormServerErrors` + `showError(error, 'user.saveFailed')` |
| 282 | `handleChangePassword` catch | `密码修改失败` | U + form | `useFormServerErrors` + `showError(error, 'user.saveFailed')` — no password key; reuse |
| 294 | `beforeAvatarUpload` | `只能上传图片文件` | **NOT a failure** | client-side validation — keep, ledger as 保持 |
| 298 | `beforeAvatarUpload` | `图片大小不能超过 2MB` | **NOT a failure** | same |
| 317 | `handleAvatarUpload` catch | `头像上传失败` | U | `showError(error, 'user.saveFailed')` |

**`components/` (1 site)**

| file:line | Function | Original copy | Bucket | Recommended form |
|-----------|----------|---------------|--------|------------------|
| `metric/UniversalMetricDialog.vue:746` | `handleSubmit` catch | `error.message \|\| (isEditMode ? '修改失败' : '录入失败')` | U + form (shared by ≥4 pages) | `useFormServerErrors` + `showError(error, 'metric.saveFailed')`. **Highest-risk file:** shared by `health/Metrics.vue`, `prevention/Metrics.vue`, `wellness/Dashboard.vue`, `dashboard/Index.vue` and covered by 3 specs |

### Inventory summary

| Bucket | Count | Notes |
|--------|-------|-------|
| **U** — user-visible action, migrate to `showError` | 58 | Includes 8 cancel-guard sites and 2 `presentStates` sites |
| **U + form** — needs `useFormServerErrors` too | 8 | goals ×2, reminders ×1, user/Profile ×2, DevicePlatformConfig ×1, UniversalMetricDialog ×1, plus one more in wave detail |
| **DEAD** — unreachable `else` after interceptor reject, **delete** | 12 | `DeviceSync` ×3, `AIInput` ×2, `OcrInput` ×1, `AiHistory` ×2, `OcrHistory` ×2, `DevicePlatformConfig` ×1, `AIInput` helper ×1 |
| **NOT a failure** — client-side validation, keep with ledger reason | 5 | `OcrInput` ×2, `user/Profile` ×2, `screening/Compare:82` |
| **Stores** — D-02, throw/return | 8 | overlaps with U |
| **Total** | **87** | |

> **Dead-branch evidence:** `request.ts:45-52` rejects with `normalizeApiError(...)` whenever `res.code !== 200`, so any `if (res.code === 200) {…} else { ElMessage.error(res.message || …) }` has an unreachable `else`. 12 of the 87 are exactly this shape. Deleting them is not a shortcut — it is required, because a `catch`-only migration would leave a second, always-false presentation path that a future reader would "fix".

## Forms Requiring `useFormServerErrors` (ERR-03)

**Correction to the brief and to CONTEXT.md:** only **3** files import `useFormServerErrors`, not 4. `health/Thresholds.vue` does **not** — it contains **zero** `<el-form>` tags (`grep -c "<el-form"` → 0) and uses `presentValidation: true` at line 259 precisely *because* it has no inline binding. `[VERIFIED: grep useFormServerErrors src → ThresholdWizard.vue:214, Risk.vue:220, Profile.vue:264 only; Thresholds.vue grep → 0]`

Corrected wiring state:

| File | `<el-form>` | `useFormServerErrors` | `FormErrorSummary` | `data-field` | Verdict |
|------|-------------|----------------------|--------------------|--------------|---------|
| `health/components/ThresholdWizard.vue` | yes (dialog) | ✅ line 214 | ✅ line 58 | ✅ 4 fields | **Wired (reference)** |
| `prevention/Profile.vue` | yes (page) | ✅ line 264 | ✅ line 33 | ✅ 9 fields | **Wired (reference)** |
| `prevention/Risk.vue` | yes (inline panel) | ✅ line 220 | ✅ line 37 | ✅ 4 fields | **Wired (reference)** |
| `health/Thresholds.vue` | **none** | ❌ | ❌ | ❌ | **Not applicable** — no form. `presentValidation: true` exception stays (UI-SPEC §1.2) |

### Remaining forms that submit to the server (13 files)

| # | File | Kind | Submits to | `prop` names present | Needs `data-field`? | `labels` override needed |
|---|------|------|-----------|----------------------|---------------------|--------------------------|
| 1 | `views/goals/Index.vue` | page + 2 dialogs | `POST/PUT/DELETE /api/goals/**` | `title`, `metricKey`, `targetValue`, `unit`, `dateRange`; progress: `value` | yes | **yes**: `title`→目标标题, `value`→当前值 / 进度值, `description`→目标描述 |
| 2 | `views/reminders/Index.vue` | page + dialog | `/api/reminders` (no `@Valid` — see note) | `title`, `reminderType`, `cronExpression` | yes | **yes**: `title`→提醒标题, `description`→提醒描述 |
| 3 | `views/screening/List.vue` | page + dialog | `/api/screening/**` | `reportName`, `institution`, `reportDate`, `file` | yes | no (all unique) |
| 4 | `views/settings/DevicePlatformConfig.vue` | page + dialog | `/api/admin/device/config` | `clientId`, `clientSecret` (only 2 of 7 fields have `prop`) | yes | no |
| 5 | `views/user/Profile.vue` | page (2 forms) | `/api/users/profile`, `/change-password` | `nickname`, `email`, `phone`, `oldPassword`, `newPassword`, `confirmPassword` | yes | no |
| 6 | `components/metric/UniversalMetricDialog.vue` | dialog (shared) | `/api/health`, `/api/wellness` | `metricKey`, `value`, `diastolicValue` | yes | `notes` keeps global |
| 7 | `views/health/Metrics.vue` | page (filter form only) | `GET` only | none | **no** — filter form, no server validation | n/a |
| 8 | `views/health/Alerts.vue` | page (filter form only) | `GET` only | none | **no** | n/a |
| 9 | `views/mood/MoodTracker.vue` | page (no `:model`!) | `POST /api/mood` | **none** | needs full retrofit or `presentValidation` | `moodScore`,`energyLevel`,`stressLevel`,`sleepQuality`,`notes` all missing from `FIELD_LABELS` |
| 10 | `views/realtime/Monitor.vue` | page (inline) | `POST` metric | **none** | needs retrofit | `metricKey`,`value`,`unit` |
| 11 | `views/auth/Login.vue` | page | `/api/users/login` | `username`, `password` | yes | yes (both missing from `FIELD_LABELS`) |
| 12 | `views/auth/Register.vue` | page | `/api/users/register` | `username`, `password`, `confirmPassword`, `email` | yes | `username`/`password`/`confirmPassword`/`email` all in the 22-key addition |
| 13 | `views/user/PushSettings.vue` | inline forms | `/api/push/config` | **none** (dynamic `:label` only) | n/a — no server field validation (no `@Valid` on push DTO) | n/a |

### Non-`prop` field identifiers — the Thresholds exception and its siblings

The UI-SPEC's Thresholds exception exists because that page's metric selector is a wizard control with no `prop`/`data-field`/`:error` triple. Research finds **three more** files with the same shape:

| File | Why no `prop` | Recommended handling |
|------|---------------|----------------------|
| `views/health/Thresholds.vue` | no `<el-form>` at all | keep `presentValidation: true` (UI-SPEC §1.2 sanctioned) |
| `views/mood/MoodTracker.vue` | `<el-form label-width="80px">` with **no `:model` and no `prop`** — the form is decorative; values are bound to a `reactive` and the mood score uses raw `<button>`s | either retrofit `:model` + `prop` + `data-field` (5 fields) or ledger it as `presentValidation`. **Recommend retrofit** — `RecordMoodRequest` has `@Valid` constraints (`moodScore`, `energyLevel`, `stressLevel`, `sleepQuality` all `@NotNull`-class per `RecordMoodRequest.java:17-36`), so a 400 is reachable |
| `views/realtime/Monitor.vue` | `el-form :inline` with no `prop` | retrofit 3 fields or ledger as exception |
| `views/user/PushSettings.vue` | dynamic `:label` from `getConfigLabel()`, no `prop` | no `@Valid` on the push DTO → no server field errors reachable → **out of scope, ledger as n/a** |

### `FIELD_LABELS` gap analysis

Current 17 keys `[VERIFIED: errorMessages.ts:38-56]`: `metricKey, value, diastolicValue, recordDate, warningHigh, warningLow, criticalHigh, criticalLow, birthDate, gender, heightCm, weightKg, bmi, lifestyleHabits, familyHistory, allergyHistory, notes`.

UI-SPEC adds 22 → 39. Research verifies the 22 are exactly the UI-SPEC list (count re-derived from the table = 22, `institution` appears once).

**Fields the UI-SPEC's 22 misses but that the 13 remaining forms will emit** (backend DTO field names read from source):

| Field | Source DTO | Needed by | In UI-SPEC 22? |
|-------|-----------|-----------|----------------|
| `moodScore` | `RecordMoodRequest.java:17` | `MoodTracker.vue` | ❌ |
| `energyLevel` | `RecordMoodRequest.java:21` | `MoodTracker.vue` | ❌ |
| `stressLevel` | `RecordMoodRequest.java:25` | `MoodTracker.vue` | ❌ |
| `sleepQuality` | `RecordMoodRequest.java:29` | `MoodTracker.vue` | ❌ |
| `entryDate` | `RecordMoodRequest.java:36` | `MoodTracker.vue` | ❌ |
| `description` | `CreateGoalRequest.java:19` | goals | ❌ (spec says use `labels`) |
| `startDate` / `endDate` | `CreateGoalRequest.java:33,35` | goals | ❌ (covered by `dateRange` prop but backend emits `startDate`/`endDate`) |
| `note` | `AddProgressRequest.java:19` | goals progress | ❌ |
| `platform`, `authUrl`, `tokenUrl`, `redirectUri`, `scopes`, `enabled` | `DevicePlatformConfigRequest.java:19-56` | DevicePlatformConfig | ❌ |
| `avatar` | `UpdateProfileRequest.java:12` | user/Profile | ❌ |

**Consequence for the planner:** the 39-key target is a *floor*, not the true set. Either (a) extend the list in the same task that wires each form (and update `DICTIONARY_KEYS`/`FIELD_LABELS` count assertions), or (b) accept empty labels for these fields — which UI-SPEC §4.5 rule 2 explicitly permits ("空串时汇总条目与内联**只渲染后端消息**"). **Recommend (b) for the DTO fields whose Chinese copy is already good in the backend message, and (a) only where the message is unreadable without a label.** Either way the ledger must record the choice per field, because `errorMessages.spec.ts:84` asserts `toHaveLength(17)` and will need `39` or higher.

### Dictionary key gaps in the UI-SPEC's 26

The UI-SPEC's 26 keys cover goals/reminders/screening/mood/realtime/alert/ai/wellness/device/user/metric/score/resource. Research finds these **missing** for call sites in the 87:

| Missing key | Needed by | Recommended action |
|-------------|-----------|--------------------|
| `mood.loadFailed` | `MoodTracker.vue:127` | add (spec has only `mood.saveFailed`) |
| `mood.deleteFailed` | `MoodTracker.vue:155` | add |
| `ocr.loadFailed` | `OcrHistory.vue:166,188,204` (spec routes these to `ai.*`) | add, or explicitly reuse `ai.loadFailed` and record it |
| `ocr.deleteFailed` | `OcrHistory.vue:207` | add or reuse |
| `ai.sendFailed` | `ai.ts:105`, `floatingAi.ts:120` | add, or decide inline-bubble instead of toast |
| `reminder.updateFailed` | `reminders/Index.vue:181` | add (spec routes to `alert.updateFailed` — semantically wrong) |
| `score.calcFailed` | `Score.vue:457` | add, or reuse `score.loadFailed` |
| `risk.loadFailed` / `risk.saveFailed` | `prevention/Risk.vue:310,335` | add, or reuse `profile.*` |
| `device.testFailed` | `DevicePlatformConfig.vue:355` | add, or reuse `device.saveFailed` |

Because every addition must be mirrored into `DICTIONARY_KEYS`, the planner should decide the final key set **once, before the first migration wave**, and put the complete list in the ledger — otherwise each wave touches `errorMessages.spec.ts` again.

## 403 / 404 Real Trigger Paths (ERR-07)

### 403 — verified reachable, and reachable by *every* user

| Fact | Evidence |
|------|----------|
| Five endpoints require `ROLE_ADMIN` | `DevicePlatformConfigAdminController.java:35,46,58,70,82` — all `@PreAuthorize("hasRole('ADMIN')")` on `/api/admin/device/config/**` `[VERIFIED: read]` |
| Method security is on | `SecurityConfig.java:34` `@EnableMethodSecurity` `[VERIFIED: grep]` |
| **No user can satisfy it** | `LoginUser.getAuthorities()` returns `Collections.emptyList()` unconditionally — `LoginUser.java:22-24`. `sys_user` has no `role` column (`schema.sql:29-44`). No `SimpleGrantedAuthority`/`AuthorityUtils` anywhere in `src/main/java` (`grep` → only the `LoginUser` import and signature). `UserVO.java` has no role field either. `[VERIFIED: all read this session]` |
| So: 403 is **guaranteed** for any authenticated user | Any request to `/api/admin/device/config/{status,save,test,delete,init}` → `AccessDeniedException` → `GlobalExceptionHandler.handleAccessDenied` → `ResponseEntity.status(403)` with `Result.failure(403, "auth.forbidden", "没有访问权限")` `[VERIFIED: GlobalExceptionHandler.java:202-206]` |
| The only frontend page that calls those endpoints | `views/settings/DevicePlatformConfig.vue:292` (`adminGetAllStatus`), `:329` (`adminSaveConfig`), `:350` (`adminTestConfig`), `:370` (`adminDeleteConfig`), `:390` (`adminInitDefaults`). API paths confirmed in `api/device.ts:117-140` |
| **But the page is unreachable via UI** | `router/index.ts:110-114` defines `/settings/device-platform` with `meta: { requiresAdmin: true }`. `router.beforeEach` (lines 145-155) checks **only** `requiresAuth` — `requiresAdmin` is never read. `MainLayout.vue` sidebar menu (lines 32-87) has **no** entry for it, and `grep "device-platform" src` finds only the route definition and the component itself. `[VERIFIED: all three read]` |

**What this means for the plan:**

- The 403 state block **is** reachable in normal use — but only by manually navigating to `/settings/device-platform`. A UAT step "log in as a normal user, open the device-platform page" will show the 403 page. That is a valid and reliable verification path.
- The UI-SPEC's §2.5 claim "页面首个主查询返回 `forbidden`" is exactly right for this page.
- **`loadConfigs` issues two requests in parallel-ish sequence** (`adminGetAllStatus` then `checkConfigReady`). Both will 403? No — `checkConfigReady` calls `/api/device/config/ready` (`api/device.ts:110-112`), which is on the **non-admin** controller `DevicePlatformConfigController` (`:22,:58`) with no `@PreAuthorize` beyond authentication. So the first request 403s, the second succeeds. **The page must render the S1 block on the first 403 and not wait for the second request.** The planner should note this two-request shape explicitly.
- A-3's prohibition is verified as the only correct option: there is no role data on the client to gate on.

### 403 trigger inventory (complete)

| Backend endpoint | Guard | Frontend caller | Reachable? |
|------------------|-------|-----------------|-----------|
| `GET /api/admin/device/config/status` | `hasRole('ADMIN')` | `DevicePlatformConfig.vue:292` | yes (always 403) |
| `POST /api/admin/device/config` | `hasRole('ADMIN')` | `:329` | yes (always 403) |
| `POST /api/admin/device/config/test/{platform}` | `hasRole('ADMIN')` | `:350` | yes (always 403) |
| `DELETE /api/admin/device/config/{platform}` | `hasRole('ADMIN')` | `:370` | yes (always 403) |
| `POST /api/admin/device/config/init` | `hasRole('ADMIN')` | `:390` | yes (always 403) |
| **Envelope 403 (business)** — `AUTH_FORBIDDEN`, `GOAL_FORBIDDEN` | thrown by services | `GoalController` ownership checks, etc. | yes — arrives as HTTP 200 + `code: 500` + `errorCode: 'auth.forbidden' \| 'goal.forbidden'` |

`ErrorCode.java:28` `AUTH_FORBIDDEN(403, "auth.forbidden", …)`, `:53` `GOAL_FORBIDDEN(403, "goal.forbidden", …)` `[VERIFIED: read]`.

### 404 — verified, with two distinct arrival shapes

| Shape | Backend source | Envelope | Frontend sites |
|-------|---------------|----------|----------------|
| **HTTP-200 envelope, numeric code 404** | `GlobalExceptionHandler.handleNoResourceFoundException` (static resources only) | `Result.failure(404, 'resource.not_found', '请求的资源不存在')` — HTTP **200** `[VERIFIED: :235-240]` | `DeviceSync` avatar loads, any missing static file |
| **HTTP-200 envelope, code 500 + string `errorCode`** | `BusinessException(ErrorCode.XXX_NOT_FOUND)` | `Result.failure(ec.getCode(), …).withErrorCode(ec.getCode())` → numeric 500, `errorCode` = e.g. `'health.profile_not_found'` `[VERIFIED: :57-73 + Result.java:81-83]` | **All the real ones.** 18 call sites across services `[VERIFIED: grep RESOURCE_NOT_FOUND / *_NOT_FOUND in src/main/java]` |

The 18 `BusinessException(...NOT_FOUND)` throw sites span: `AiParseServiceImpl` (3), `ScreeningServiceImpl` (5), `AIChatServiceImpl` (2), `OcrServiceImpl` (3), `UserReminderServiceImpl` (1), `ExportServiceImpl` (1), `ScreeningComparisonServiceImpl` (1), plus `RESOURCE_NOT_FOUND` (2). `ErrorCode` declares 6 distinct not-found codes: `USER_NOT_FOUND`, `HEALTH_METRIC_NOT_FOUND`, `HEALTH_PROFILE_NOT_FOUND`, `HEALTH_ALERT_NOT_FOUND`, `GOAL_NOT_FOUND`, `MOOD_ENTRY_NOT_FOUND`, `RESOURCE_NOT_FOUND`, `RESOURCE_FILE_NOT_FOUND` `[VERIFIED: ErrorCode.java:35-65]`.

### 404 frontend render sites (S2 candidates)

| View | Endpoint | Why it is the right S2 site |
|------|----------|-----------------------------|
| `screening/Detail.vue:152` | `screeningApi.getReportDetail(reportId)` → `ScreeningServiceImpl` throws `RESOURCE_NOT_FOUND` "报告不存在" | Route carries `:id`; the page shell (nav, breadcrumb) stays useful |
| `data-input/components/AiHistory.vue:177` | `aiParseApi.getHistoryDetail(record.id)` → "解析记录不存在" | Dialog detail — **use a content-area block inside the dialog**, not the page |
| `data-input/components/OcrHistory.vue:188` | `ocrApi.getRecordDetail(record.id)` → "记录不存在" | same |
| `goals/Index.vue` single-item ops | `GOAL_NOT_FOUND` | Action-type → `presentStates: true`, **not** S2 (page is a list) |
| `prevention/Profile.vue:395-400` | `health.profile_not_found` | **Already correct** — treats not-found as "create mode", not an error. **Must not regress** |
| `stores/profileCompleteness.ts:67` | `health.profile_not_found` | **Already correct** — `NOT_FOUND_CODES = ['health.profile_not_found','resource.not_found']` |

**Critical non-regression:** `prevention/Profile.vue` and `profileCompleteness.ts` read `err.status`/`err.code` and rely on `business` passing them through. After `notFound` is added, `err.code` will still be `'health.profile_not_found'` (the new branch must construct `new ApiError(msg, 'notFound', status, data.errorCode)` — i.e. **preserve `errorCode`**). If the new branch drops `code`, both files silently change behaviour: Profile would stop entering create mode and start showing an error toast on first visit. This is the single highest-risk regression in the phase and needs its own test.

### Route-level 404

Confirmed: `router/index.ts` has **no** `pathMatch` catch-all (`grep pathMatch` → 0 hits), and there is no `NotFound` view in `src/views/`. Navigating to an unknown URL renders an empty `MainLayout` content area. Contract 3 §3.6 defines the spec but explicitly leaves enabling it to the planner's discretion. **Recommendation: include it** — it is ~10 lines, reuses the same `ErrorState scope="page"` geometry, and closes the last unexplained blank page in the app. But it is a routing change and must be listed explicitly in the PLAN.

## Cancel-Misjudgement Sites (ERR-08)

**The count is 8, not 2.** CONTEXT cites `goals/Index.vue:157` and `health/Alerts.vue:224`; research finds 8 hand-written guards plus 1 hand-rolled error-name check.

| # | file:line | Guard form | Uses `ElMessageBox.confirm`? | Would `AbortError` slip through? | Would `ApiError('', 'cancel')` slip through? | Exact replacement |
|---|-----------|-----------|------------------------------|----------------------------------|----------------------------------------------|-------------------|
| 1 | `goals/Index.vue:157` | `if (e !== 'cancel') ElMessage.error('删除失败')` | yes | **yes** — `e.name === 'AbortError'`, `e !== 'cancel'` is true → toast | **yes** — object `!==` string → toast | `catch (e) { showError(e, 'goal.deleteFailed') }` |
| 2 | `mood/MoodTracker.vue:155` | same | yes | yes | yes | `catch (e) { showError(e, 'mood.deleteFailed') }` |
| 3 | `health/Alerts.vue:224` (`markAllRead`) | `if (error !== 'cancel') { … }` | yes | yes | yes | `catch (e) { showError(e, 'alert.updateFailed') }` |
| 4 | `health/Alerts.vue:239` (`handleDelete`) | same | yes | yes | yes | `catch (e) { showError(e, 'alert.deleteFailed') }` |
| 5 | `reminders/Index.vue:193` | same | yes | yes | yes | `catch (e) { showError(e, 'reminder.deleteFailed') }` |
| 6 | `screening/List.vue:218` | same | yes | yes | yes | `catch (e) { showError(e, 'screening.deleteFailed') }` |
| 7 | `health/Metrics.vue:200` | `if (error !== 'cancel' && error !== 'close')` | yes | yes | yes | `catch (e) { showError(e, 'metric.deleteFailed') }` — the `'close'` half is **dead**: `ElMessageBox.confirm` rejects with the string `'cancel'` only, never `'close'` |
| 8 | `prevention/Metrics.vue:131` | same as #7 | yes | yes | yes | `catch (e) { showError(e, 'metric.deleteFailed') }` |
| 9 | `health/Score.vue:505` | `if (error.name === 'CanceledError' \|\| error.name === 'AbortError') { /* silent */ } else { ElMessage.error(…) }` | **no** (`AbortController`) | correctly silent | **yes** — `ApiError` has `name === 'ApiError'`, so both checks fail → toast | `catch (e) { showError(e, 'report.generateFailed') }` |

**Answers to the brief's specific questions:**

- **Does each use `ElMessageBox.confirm`?** Sites 1–8 yes; site 9 uses `AbortController` (and the controller is never wired to a `signal`, so the abort is a no-op today — the guard is unreachable defensive code).
- **Would `AbortError` slip through?** Yes for all 8 (`ElMessageBox` sites) — but note that in practice a `confirm` dialog never produces an `AbortError`. The realistic slip-through is **site 9** and any future site where a route change cancels an in-flight request.
- **Would `ApiError('', 'cancel')` slip through?** **Yes for all 9**, and this is the *real* bug: after Phase 11, every API failure reaching these catches is an `ApiError` instance, so `e !== 'cancel'` is **always true**. These guards are effectively dead code that always shows the toast — which means a genuine cancel of an *API* request (not the dialog) would toast. The dialog-cancel path happens to work only because `ElMessageBox` rejects with a raw string before any API call starts.

**One more hand-written cancel shape the UI-SPEC does not mention:**

`views/settings/DevicePlatformConfig.vue:372-374` and `views/ai/Chat.vue:465-467` and `views/data-input/components/DeviceSync.vue:530` use a **bare `catch { /* User cancelled */ }`** around a `confirm` + API call. These are *more* dangerous than the 8 above because they swallow **real API failures** silently — a failed delete shows nothing. Contract 6 check ③ will not catch them (they contain no `!== 'cancel'`). The planner must add a ledger row for each:

| file:line | Shape | Fix |
|-----------|-------|-----|
| `settings/DevicePlatformConfig.vue:372` | `catch { /* User cancelled */ }` around confirm+delete | split: `try { await confirm } catch { return }` then `try { await api } catch (e) { showError(e, …, { presentStates: true }) }` |
| `ai/Chat.vue:465` | `catch { /* User cancelled */ }` around confirm+`aiStore.deleteSession` | same split (and after D-02, `deleteSession` throws → this catch would swallow it) |
| `data-input/components/DeviceSync.vue:530` | same around confirm+disconnect | same split |

## Store-Internal 8 Calls (D-02)

| # | file:line | Method | Existing return contract | Callers (file:line) | Caller has catch/error path? | Recommendation |
|---|-----------|--------|--------------------------|---------------------|------------------------------|----------------|
| 1 | `ai.ts:70` | `loadSessions` | `void` (no return) | `Chat.vue:624` (onMounted) | ❌ no try/catch | **throw** `normalizeApiError(error)`; caller wraps in `try/catch` → `showError(error,'ai.loadFailed')` |
| 2 | `ai.ts:105` | `sendMessage` | `Promise<boolean>` (false on fail) | `Chat.vue:489`, `FloatingChatPanel.vue:215` | ✅ both read the boolean, neither toasts | **keep `false`**, remove the toast. Both callers already handle `false`; adding a toast is a behaviour change the planner must decide on explicitly (see §87 #2) |
| 3 | `ai.ts:126` | `deleteSession` | `Promise<boolean>` | `Chat.vue:465` | ⚠️ catch exists but swallows everything as "cancelled" | **throw**; caller splits confirm-catch from api-catch → `showError(e,'ai.deleteFailed')` |
| 4 | `ai.ts:137` | `getHistory` | `Promise<ConversationVO[]>` (`[]` on fail) | `Chat.vue:496` | ❌ none | **throw**; caller wraps → `showError(error,'ai.loadFailed')`. Note: returning `[]` today makes a failed history indistinguishable from an empty history — a real defect |
| 5 | `wellness.ts:22` | `fetchSummary` | `void` | `Dashboard.vue:276,349` | ❌ none | **throw**; caller renders `ErrorState` (page-level) |
| 6 | `wellness.ts:43` | `fetchTrend` | `void` | `Dashboard.vue:333,341` | ❌ none | **throw**; caller renders a chart-area `ErrorState` |
| 7 | `wellness.ts:64` | `addMetric` | `Promise<boolean>` | **none found in `views/`** | n/a | **Verify first.** If truly uncalled, delete the method or leave `false` with no toast and ledger "dead store action". Do **not** spend a wave on it |
| 8 | `floatingAi.ts:120` | `sendMessage` | `Promise<boolean>` | `FloatingChatPanel.vue:215` | ✅ reads boolean | **keep `false`**, remove toast, present inside the floating panel (it has its own message surface) |

**Pattern recommendation:** D-02 says "抛错/返回错误". Research recommends **throw for the 4 `void`-returning loaders** (#1, #4, #5, #6) and **keep the boolean for the 3 boolean-returning action methods** (#2, #3, #8 — with #3 changed to throw because its caller's catch is broken). Rationale: changing a `Promise<boolean>` to throwing forces every caller to change, and two of the three callers already branch on the boolean correctly. Throwing where the signature is `void` costs nothing.

**Reference implementation already in the repo:** `stores/push.ts` is the model — `loadConfigs()` calls `showError(error, 'server.unavailable', { silent: true })` (line 43) to *classify without presenting*, stores the message in `loadError`, and lets `PushSettings.vue:18-25` render `ErrorState`. `saveConfig` calls `showError(error, 'push.saveFailed')` (line 70) because it is a user action. This is exactly the shape D-02 asks for, and `ErrorLayerSample.spec.ts:214-219` already asserts `push.ts` contains no `ElMessage.error`.

## Non-Destructive Audit (ERR-05)

ERR-05 is largely satisfied by the existing infrastructure; the risk is **regression during migration**. Verified invariants and the specific traps:

| Rule | Verified status | Trap to avoid during migration |
|------|----------------|-------------------------------|
| `apply()` never writes back to the form model | ✅ `useFormServerErrors.ts:77-87` only assigns `fieldErrors.value` | Don't "helpfully" add `Object.assign(form, err.fieldErrors)` |
| `clearField()` clears only the error text | ✅ `:101-106` deletes from `fieldErrors` only | Don't bind it to `@blur` in a way that also resets the value |
| `reset()` is called before each submit | ✅ `Profile.vue:429` calls `reset()` before `validate()` | Forgetting `reset()` leaves a stale summary from the previous attempt |
| No `resetFields()` in failure paths | ✅ none of the 4 reference files call it on failure | **Highest risk:** `goals/Index.vue:153` `showCreateDialog()` does `createForm.title = ''` etc. — that is *open-dialog* init, correct; but `reminders/Index.vue:145-150` `showCreateDialog()` does the same, and `handleCreate`'s catch must not accidentally call it |
| Dialogs stay open on failure | ✅ all reference impls only set `visible = false` in the success branch | `goals/Index.vue:154` currently sets `createVisible.value = false` **after** `await goalsApi.create(...)` and before `fetchGoals()` — inside `try`, so it is correct today. Preserve that ordering |
| `monitor`-style inline forms wipe inputs on success | ⚠️ `realtime/Monitor.vue:352` `Object.assign(form, { metricKey: '', value: 0, unit: '' })` runs **only in `try`** — correct. Must not move | — |

**Testable criterion the planner should reuse** (UI-SPEC §7.1): snapshot `form` before, call `apply(err)`, assert `toEqual`. `prevention/Profile.vue` and `ThresholdWizard.vue` already have this shape in `ProfileErrorSample.spec.ts` / `ThresholdWizard.spec.ts`.

## Baseline + Regression Gates

### Measured baselines — both confirmed exactly

| Side | Command | Result | Source |
|------|---------|--------|--------|
| Frontend | `npm --prefix hhs-frontend-v2 run test:run` | **445 passed / 445, 37 files passed** | `[VERIFIED: ran this session, 2026-11-10]` |
| Backend | `mvn -f hhs-backend/pom.xml test -DskipITs` | **845 tests, 0 failures, 0 errors, 0 skipped** across 87 surefire report files | `[VERIFIED: summed `target/surefire-reports/*.txt`]` |

Both match D-05's locked values (845 / 445-37). **ROADMAP's "90" is confirmed stale.**

### Additional gate states measured today (important — two are already red)

| Gate | Command | Today | Note |
|------|---------|-------|------|
| Frontend build | `npm run build` (`vue-tsc -b && vite build`) | not run (assumed green) | Must run in phase gate; `noUnusedLocals`/`noUnusedParameters` are on, so any leftover import after migration **fails the build** — this is the real safety net for the 87 migrations |
| Frontend lint | `npm run lint:check` | **0 errors, 216 warnings** `[VERIFIED: ran]` | D-05's "0 errors" holds; warnings are pre-existing (`@typescript-eslint/no-explicit-any`). Do not add errors |
| Frontend format | `npm run format:check` | **FAILS — 98 files** `[VERIFIED: ran]` | ⚠️ **Not in D-05's gate list, and it is already red.** Do NOT add `format:check` to the phase gate without first fixing or explicitly waiving it. This is a pre-existing condition, not a Phase 14 regression |
| Backend checkstyle | `mvn checkstyle:check` | **clean (exit 0, no output)** `[VERIFIED: ran]` | Matches D-05 |
| E2E | `npx playwright test e2e/quick-input.spec.ts` | not run (needs backend + DB) | `quick-input.spec.ts:80` asserts the success toast contains `录入成功`; `:197` asserts no success toast in the invalid case. **Both survive the migration** — `ElMessage.success` is not in the ban (UI-SPEC §5.1) |
| Docker | `docker info` | client 29.8.1, context `desktop-linux` — daemon reachability unverified | `WINDOWS.md` #3 records Testcontainers cannot reach the daemon; `-DskipITs` avoids it |

### `errorMessages.spec.ts` auto-raise — confirmed

`errorMessages.spec.ts:34` reads `expect(Object.keys(ERROR_MESSAGES)).toHaveLength(DICTIONARY_KEYS.length)` `[VERIFIED: read line 34]`. Because the assertion compares against `DICTIONARY_KEYS.length` (a local array), **adding keys to the array automatically raises the expected count** — no hardcoded number to update. UI-SPEC A-2's instruction ("只扩数组，不改断言") is verified correct and must be followed literally: **do not** replace with a literal `44`.

But note the **second** assertion is *not* auto-raising: `errorMessages.spec.ts:84` is `expect(Object.keys(FIELD_LABELS)).toHaveLength(17)` — a **hardcoded 17** `[VERIFIED: read line 84]`. That one must be edited to the final count. UI-SPEC A-2 says "改为 39"; research says use whatever the final count is after the gap analysis above.

Also: `errorMessages.spec.ts:52-58` iterates `DICTIONARY_KEYS` for the ≤30-char cap, so new keys are auto-covered — but only if they are added to `DICTIONARY_KEYS`. A key added to `ERROR_MESSAGES` but not to `DICTIONARY_KEYS` would silently escape both the count check and the length check (the count check would then *fail*, which is the desired alarm).

### Phase gate — complete command list

```bash
# 0) pre-flight (must be green BEFORE any migration, to prove no pre-existing regression)
npm --prefix hhs-frontend-v2 run test:run          # expect 445 / 37 files
mvn -f hhs-backend/pom.xml test -DskipITs          # expect 845 / 0 failures

# 1) per-wave gate
npm --prefix hhs-frontend-v2 run test:run          # >= 445, all green
npm --prefix hhs-frontend-v2 run build             # vue-tsc must pass (catches unused imports)
npm --prefix hhs-frontend-v2 run lint:check        # 0 errors

# 2) phase gate
npm --prefix hhs-frontend-v2 run test:run          # >= 445
mvn -f hhs-backend/pom.xml clean verify -DskipITs  # >= 845, green
mvn -f hhs-backend/pom.xml checkstyle:check        # 0 violations
npm --prefix hhs-frontend-v2 run build             # green
npm --prefix hhs-frontend-v2 run lint:check        # 0 errors
npx --prefix hhs-frontend-v2 playwright test e2e/quick-input.spec.ts   # needs backend

# 3) the four machine checks (Contract 6.3) — see contradictions below
```

### Contract 6.3's four checks — three need correction

| Check | As written | Today's actual output | Problem | Corrected form |
|-------|-----------|----------------------|---------|----------------|
| ① bare toast residue | `grep -rn "ElMessage\.error" src --include=*.vue --include=*.ts \| grep -v "__tests__" \| wc -l` → 0 | **87** | none — this one is correct and is the phase's headline metric | keep |
| ② no toast in stores | `grep -rn "ElMessage" src/stores --include=*.ts` → "only `stores/auth.ts` success/info/warning" | `ai.ts` (5), `wellness.ts` (4), `push.ts` (1), `floatingAi.ts` (1) | **Contradiction.** `ai.ts:123` `ElMessage.success('会话已删除')`, `wellness.ts:58` `ElMessage.success('添加成功')`, `push.ts:83` `ElMessage.success(...推送测试成功)` are **success** toasts, which §5.1 explicitly permits ("`ElMessage.success` / `.warning` / `.info` 不在本禁令内"). Only the **8 error** calls must go | `grep -rn "ElMessage\.error" src/stores --include=*.ts` → **0**. Keep the success/info/warning ones (or decide separately to move them; §5.1 says don't) |
| ③ hand-written cancel guards | `grep -rn "!== 'cancel'\|=== 'cancel'\|!== \"cancel\"" src --include=*.vue --include=*.ts \| grep -v "utils/appError.ts"` → 0 | **10 hits** (2 legitimate `=== 'cancel'` in `request.ts:65` / `showError.ts:129` + 8 real guards) | **Two problems.** (a) The two legitimate `kind === 'cancel'` comparisons in `request.ts` and `showError.ts` are **required** and will always hit — the exclusion list must include them. (b) The pattern misses `error !== 'close'` and `name === 'CanceledError'`, i.e. it would report 0 while `health/Metrics.vue:200`, `prevention/Metrics.vue:131`, `Score.vue:505` still exist | exclude `utils/appError.ts`, `utils/request.ts`, `utils/showError.ts`; add patterns `!== 'close'`, `=== 'CanceledError'`, `=== 'AbortError'`; expect 0 |
| ④ no local error styles | `grep -rn "el-form-item__error\|is-error\|--el-color-danger" src --include=*.vue \| grep -v "__tests__"` → 0 | **4 hits** — all in `data-input/components/CriticalConfirmDialog.vue:92,93,101,124`, which uses `--el-color-danger` for a **danger-zone confirm panel**, not for `el-form-item` error styling | **Contradiction.** The check's stated intent ("错误样式的局部覆盖") is satisfied; the regex is too broad. Those 4 hits are a legitimate use of a semantic token for a different surface | narrow the pattern to `el-form-item__error\|el-form-item.is-error\|el-form-item\.is-error`; expect 0 |

**Planner action:** rewrite Contract 6.3 checks ②③④ into the corrected forms above and put the exact commands + expected outputs in the plan. Do not ship a check that is red for legitimate reasons — it trains people to ignore the gate.

## Pitfalls

### Pitfall 1: Shared component `UniversalMetricDialog.vue` breaks 3 test files at once
**What goes wrong:** migrating its single `ElMessage.error` at line 746 looks like a one-line change, but the component is mounted by `UniversalMetricDialog.spec.ts`, `HealthMetrics.spec.ts`, `Metrics.spec.ts`, `PreventionAndWellness.spec.ts`, `WellnessMetrics.spec.ts` — five spec files, ~50 `it()` cases. If the migration introduces `useFormServerErrors` with `prop`/`data-field`/`:error`, the shallow stubs in those specs (`el-form-item` is not stubbed, but `el-input-number`/`el-select` are) may not forward the `:error` prop, and `data-field` lookups will find nothing.
**Why:** the specs stub EP controls with minimal templates (`Metrics.spec.ts:97-148`).
**Avoid:** migrate the toast only in the first pass (`showError`), and add the inline-error wiring in a **separate** task with its own spec update. Verify with `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/components/UniversalMetricDialog.spec.ts` after each step.
**Warning signs:** `[Vue warn]: Failed to resolve component` noise already present in the suite; any new `el-form-item` assertion failing.

### Pitfall 2: Tests that count toasts via `ElMessageMock.error` will silently pass when they should fail
**What goes wrong:** `ErrorLayerSample.spec.ts:317-318` and `Thresholds.spec.ts` assert `expect(ElMessageMock.error).toHaveBeenCalledTimes(0)` in `afterEach`. After migration, that assertion becomes **trivially true everywhere** because nothing calls `ElMessage.error` anymore. The suites keep passing but stop protecting anything.
**Why:** the assertion was written to prove convergence *to* `showError`; once converged it is vacuous.
**Avoid:** keep the assertion (it guards against re-introduction) **and** add a positive counterpart: `expect(objectStyleErrorToasts()).toBe(expected)` using the existing `errorToastCount()` helper (`ErrorLayerSample.spec.ts:95-102`), which already counts object-form `ElMessage({type:'error'})` calls.
**Warning signs:** a migrated page whose failure test passes without ever checking that exactly one toast appeared.

### Pitfall 3: `error.response` peeks silently degrade to fallback copy
**What goes wrong:** `Score.vue:483,508` and `AIInput.vue:247` read `error.response?.data?.message`. `normalizeApiError` returns an `ApiError` **instance** which has **no `response` property** (verified: `appError.ts:14-38` declares only `message/kind/status/code/fieldErrors/formField/retryable/retryRequest`). So these three lines **always** fall back today. Migrating them to `showError(error, key)` actually *improves* copy (backend message wins via `resolveMessage`).
**Why:** pre-Phase-11 code; nobody noticed because the fallback copy was acceptable.
**Avoid:** when migrating, do not preserve the `error.response` access "for safety" — it is dead and TS-strict will not catch it (`error: any`).
**Warning signs:** a migrated line still containing `error.response`.

### Pitfall 4: `Object.assign(form, {})` in dialog-open handlers looks like a reset-on-failure
**What goes wrong:** `goals/Index.vue:153`, `reminders/Index.vue:145`, `Monitor.vue:352` all reset the form. A reviewer (or a careless edit) can move the reset into the `catch` block, breaking ERR-05.
**Why:** the reset is one line above the `try` in some files.
**Avoid:** add the ERR-05 snapshot test per form (Pitfall 5) and a grep-based ledger column "catch 内是否出现 reset/assign".
**Warning signs:** `git diff` showing `Object.assign` or `resetFields` inside a `catch`.

### Pitfall 5: `apply(err)` on a non-`validation` error is a silent no-op that hides bugs
**What goes wrong:** `useFormServerErrors.apply()` returns early unless `err.kind === 'validation'` (`:79`). A migrated submit handler that calls `apply(err)` **and** `showError(err, key)` will show the toast for a `business` error and show nothing inline — correct — but for a `validation` error it shows inline **and** `showError` returns early (0 toast) — also correct. The trap is calling `apply()` **without** `showError` for non-validation kinds: the user then gets no feedback at all.
**Avoid:** the canonical submit shape (copy from `Profile.vue:455-465`):
```ts
catch (error) {
  const err = showError(error, 'x.saveFailed')   // always call first
  if (err.kind === 'validation') { apply(err); await nextTick(); summaryRef.value?.focusSummary() }
}
```
**Warning signs:** a `catch` that calls `apply()` and nothing else.

### Pitfall 6: New `ErrorKind`s break exhaustive `switch` nowhere, but break `retryable` assumptions
**What goes wrong:** no `switch` over `ErrorKind` exists in `src` (verified: only `===` comparisons), so adding kinds is type-safe. But `forbidden`/`notFound` must have `retryable === false` (UI-SPEC §5.3) — if the new branch constructs `new ApiError(msg, 'forbidden', 403, code)` it defaults `retryable` to `false` (constructor default at `appError.ts:22`), which is correct. If someone passes `true`, `showError` would render a retry button on a permission denial.
**Avoid:** construct without the `retryable` argument; add a test asserting `err.retryable === false` for both new kinds.

### Pitfall 7: `errorMessages.spec.ts` count assertions fail mid-wave and look like a regression
**What goes wrong:** adding one dictionary key without adding it to `DICTIONARY_KEYS` makes `toHaveLength(DICTIONARY_KEYS.length)` fail with a confusing message. Adding a `FIELD_LABELS` key makes the hardcoded `17` fail.
**Avoid:** every task that touches `errorMessages.ts` must also touch `errorMessages.spec.ts` in the same commit. Put both paths in the task's `files_modified`.

### Pitfall 8: `vi.mock('element-plus', …)` shapes differ across specs — a `showError` change can break one and not others
**What goes wrong:** three distinct mock shapes exist: `setup.ts:5-11` (global `$message`), `showError.spec.ts:4-11` (bare `vi.fn()` with `.error` attached), and `ErrorLayerSample.spec.ts:53-68` / `ScoreEstimateBadge.spec.ts:57` / `LoginAuthError.spec.ts:29` / `ProfileErrorSample.spec.ts:28` / `ProfileCompleteness.spec.ts:33` / `Thresholds.spec.ts:48` / `RiskBackfill.spec.ts:48` (all `importOriginal` + `Object.assign`). If `showError` starts calling `ElMessage.info` or `ElMessage.closeAll` (e.g. for the max-3 eviction), the bare mock at `showError.spec.ts:4-11` **lacks those methods** and will throw.
**Avoid:** when extending `showError`, only use `ElMessage(...)` (object form), `.close()` on the returned handler, and the four `.success/.warning/.info/.error` already present in every mock. `showError` currently uses only object-form `ElMessage` + `handler.close()` — preserve that.

### Pitfall 9: `error !== 'close'` is dead and will be "preserved" by a literal-minded migration
**What goes wrong:** `health/Metrics.vue:200` and `prevention/Metrics.vue:131` check `!== 'close'`. `ElMessageBox.confirm` rejects with `'cancel'`; `'close'` comes from `ElMessageBox`'s **`before-close`/close button**, which these call sites do not use. Keeping the `'close'` half means the migrated guard still contains a hand-written cancel check and check ③ stays red.
**Avoid:** delete both halves; `showError` handles all five cancel forms via `isCancel` (`appError.ts:43-53`).

### Pitfall 10: `format:check` is already red — do not add it to the gate
**What goes wrong:** D-05 lists `lint:check` but not `format:check`. If someone adds `format:check` "for completeness", the phase fails on 98 pre-existing files.
**Avoid:** keep the gate to D-05's list; if formatting is desired, do it as a separate, explicitly-scoped commit with `npm run format` and verify the diff is whitespace-only.

### Pitfall 11: HMR / watch noise during a 87-site migration
**What goes wrong:** editing 24 files while `npm run dev` is running produces cascading `[vite] hmr update` logs and, with `unplugin-vue-components`, occasional "component not resolved" flashes that look like failures.
**Avoid:** run migrations with the dev server stopped; use `npm run test:run` (single-shot) for feedback, and `npm run build` for the type check. Only start `npm run dev` for the D-06 manual UAT.

### Pitfall 12: the 403 page issues two requests and the second one succeeds
**What goes wrong:** `DevicePlatformConfig.loadConfigs()` (`:288-303`) awaits `adminGetAllStatus()` (403) inside a `try`, so the throw skips `checkConfigReady()`. Good — but if a migration restructures it into `Promise.all`, both run, one 403s and one succeeds, and the state logic gets confusing.
**Avoid:** keep the two calls sequential inside one `try`; on `forbidden`, set the page-level state flag and `return` immediately. Do not add `Promise.all` here.

## Code Examples

### A-1 change 1 — `appError.ts` new kinds and detection (insert after line 110, before line 116)

```ts
// Source: UI-SPEC Contract 1.1 + verified GlobalExceptionHandler/Result/ErrorCode paths
export type ErrorKind =
  | 'validation'
  | 'business'
  | 'auth'
  | 'forbidden'   // 403 或 *.forbidden 错误码
  | 'notFound'    // 404 或 *_not_found 错误码
  | 'network'
  | 'server'
  | 'cancel'
  | 'unknown'

// … after the 401 guard (currently line 110) and before `if (!err?.response)` (line 116):

// 3.5 403：真实 HTTP 403 走 GlobalExceptionHandler.handleAccessDenied；
//     信封 403 走 BusinessException(AUTH_FORBIDDEN/GOAL_FORBIDDEN)，其数值码被
//     parseCodeToInt 回退成 500，只能靠 errorCode 字符串识别（A-2 用例 ②）。
const envelopeCode = typeof data?.code === 'number' ? data.code : undefined
const errorCode = typeof data?.errorCode === 'string' ? data.errorCode : ''
if (status === 403 || /(^|\.)forbidden$/.test(errorCode)) {
  // 必须保留 status / errorCode：prevention/Profile.vue:395-400 与
  // profileCompleteness.ts:67 读它们区分「档案不存在」与真实故障。
  return new ApiError(data?.message || '没有访问权限', 'forbidden', status, errorCode)
}

// 3.6 404：handleNoResourceFoundException 下发数值 404（HTTP 200 信封）；
//     BusinessException(*_NOT_FOUND) 下发数值 500 + 字符串 errorCode。
if (envelopeCode === 404 || status === 404 || /(^|_|\.)not[._]?found$/.test(errorCode)) {
  return new ApiError(data?.message || '资源不存在', 'notFound', status, errorCode)
}
```

`retryable` is omitted → defaults to `false` (constructor default, `appError.ts:22`), which is required by UI-SPEC §5.3.

### A-1 change 3 — `showError` `presentStates` + `needsVNode` extension

```ts
// Source: UI-SPEC Contract 1.2 / F4 clarification; line numbers verified against showError.ts
export interface ShowErrorOptions {
  retry?: () => void | Promise<void>
  action?: { label?: string; onClick: () => void }
  silent?: boolean
  presentValidation?: boolean
  /** 动作型 403/404 放行一条常驻提示；默认零 toast（状态块承担呈现）。 */
  presentStates?: boolean
}

// inside showError, after the validation/silent early-returns:
if ((err.kind === 'forbidden' || err.kind === 'notFound') && opts?.presentStates !== true) {
  return err
}

// replace line 171:
const needsVNode =
  hasRetry ||
  hasAction ||
  err.kind === 'network' ||
  err.kind === 'server' ||
  err.kind === 'forbidden' ||
  err.kind === 'notFound'
```

With `needsVNode === true` and neither retry nor action, the VNode branch renders text-only at `duration: 0` (line 235) — exactly the F4 requirement.

### A-1 change 2 — `theme.css` error-border block (append after line 1242)

```css
/* ---------- Phase 14: 字段级错误描边 ----------
   前提（已在 element-plus@2.14.0 theme-chalk/el-form-item.css 核对）：
   EP 的 `.el-form-item.is-error … { box-shadow: 0 0 0 1px var(--el-color-danger) inset }`
   无 !important；本文件 :502-508 与 :672-677 用 `box-shadow: none !important` 压掉了它。
   因此 input/select 改走 border-color，textarea 未被本文件覆盖、仍走 inset shadow。 */

/* ① input / select：本文件已给真实 border */
body .el-form-item.is-error .el-form-item__content .el-input__wrapper,
body .el-form-item.is-error .el-form-item__content .el-select__wrapper {
  border-color: var(--hhs-color-error-text) !important;
  box-shadow: none !important;
}

/* ② textarea：EP 用 inset shadow，没有 border */
body .el-form-item.is-error .el-form-item__content .el-textarea__inner {
  box-shadow: 0 0 0 1px var(--hhs-color-error-text) inset !important;
}

/* ③ 出错且聚焦：三类控件三个类名，缺一即该类控件出错时无焦点指示 */
body .el-form-item.is-error .el-form-item__content .el-input__wrapper.is-focus,
body .el-form-item.is-error .el-form-item__content .el-input__wrapper.is-active,
body .el-form-item.is-error .el-form-item__content .el-select__wrapper.is-focused {
  border-color: var(--hhs-color-error-text) !important;
  box-shadow: 0 0 0 3px var(--hhs-color-error-tint) !important;
}
body .el-form-item.is-error .el-form-item__content .el-textarea__inner:focus {
  box-shadow:
    0 0 0 1px var(--hhs-color-error-text) inset,
    0 0 0 3px var(--hhs-color-error-tint) !important;
}
```

`.is-active` is verified real: `element-plus/es/components/time-picker/src/common/picker.vue_vue_type_script_setup_true_lang.mjs:77-85` builds `rangeInputKls` with `nsInput.e("wrapper")` + `nsDate.is("active", pickerVisible.value)`, and line 449 applies it to the range editor root. `[VERIFIED: read EP source this session]` The two `daterange` sites are `goals/Index.vue:83` and `health/Metrics.vue:18` — both in the migration set.

`:error` → `.is-error` is verified real: `form-item.vue_vue_type_script_setup_true_lang.mjs:187-190` watches `props.error` and calls `setValidationState(val ? "error" : "")` with `immediate: true`. `[VERIFIED: read EP source this session]`

### Canonical migrated submit handler (ERR-03 + ERR-05 + ERR-07)

```vue
<!-- Source: prevention/Profile.vue:30-33 + 455-465 (verified reference implementation) -->
<el-form ref="formRef" :model="form" :rules="rules">
  <FormErrorSummary ref="summaryRef" :summary-items="summaryItems" @focus-field="focusField" />
  <el-form-item label="目标标题" prop="title" data-field="title" :error="fieldErrors['title']">
    <el-input v-model="form.title" @update:model-value="clearField('title')" />
  </el-form-item>
</el-form>
```
```ts
const { fieldErrors, summaryItems, apply, reset, clearField, focusField } =
  useFormServerErrors({ fieldOrder: ['title', 'metricKey', 'targetValue', 'unit', 'dateRange'],
                         labels: { title: '目标标题', description: '目标描述' } })

const handleCreate = async () => {
  if (!createFormRef.value) return
  reset()                                        // ① 清上一轮残留
  await createFormRef.value.validate(async (valid, invalidFields) => {
    if (!valid) {
      setErrors(collectInvalidFieldMessages(invalidFields))   // ② 客户端失败也进汇总
      await nextTick(); summaryRef.value?.focusSummary(); return
    }
    submitting.value = true
    try {
      await goalsApi.create({ /* … */ })          // ③ 成功才关弹窗
      ElMessage.success('目标创建成功')
      createVisible.value = false
      fetchGoals()
    } catch (error) {
      const err = showError(error, 'goal.saveFailed')          // ④ 单一呈现出口
      if (err.kind === 'validation') { apply(err); await nextTick(); summaryRef.value?.focusSummary() }
      // ⑤ 不 reset、不 assign、不关弹窗 —— ERR-05
    } finally { submitting.value = false }
  })
}
```

`collectInvalidFieldMessages` is currently **duplicated verbatim** in `prevention/Profile.vue:415-424` and `prevention/Risk.vue:390-399` (the Risk copy's comment says so). With 13 forms needing it, the planner should extract it once (e.g. into `useFormServerErrors.ts` as an exported helper) rather than paste it 13 more times. This is a small, safe refactor that reduces the migration's diff surface.

### Canonical cancel handling (ERR-08)

```ts
// Source: UI-SPEC Contract 7.2 option A
const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确定要删除这个目标吗？', '提示', { type: 'warning' })
  } catch {
    return                    // 用户取消：零提示。这一行注释必须留下
  }
  try {
    await goalsApi.remove(id)
    ElMessage.success('删除成功')
    fetchGoals()
  } catch (error) {
    showError(error, 'goal.deleteFailed')      // showError 内部识别 cancel/AbortError
  }
}
```
This split-confirm shape also fixes the three bare `catch { /* User cancelled */ }` sites (§Cancel sites 10-12) which currently swallow real API failures.

### Canonical 403 page (S1)

```vue
<!-- Source: UI-SPEC Contract 2.2; DevicePlatformConfig.vue -->
<ErrorState
  v-if="forbidden"
  scope="page"
  variant="forbidden"
  title="无权访问此页面"
  :description="deniedDescription"
  :actions="[{ label: '返回首页', onClick: goHome, variant: 'primary' }]"
/>
<div v-else> <!-- existing page content --> </div>
```
```ts
const forbidden = ref(false)
const deniedDescription = ref('当前账号没有访问该页面的权限，请返回首页，或联系管理员申请开通。')

const loadConfigs = async () => {
  loading.value = true
  forbidden.value = false
  try {
    const res = await deviceApi.adminGetAllStatus()
    if (res.code === 200) platforms.value = res.data
    // 只有拿到数据后才继续第二个请求（Pitfall 12）
    const readyRes = await deviceApi.checkConfigReady()
    if (readyRes.code === 200) encryptionKeyConfigured.value = readyRes.data.encryptionReady
  } catch (error) {
    const err = normalizeApiError(error)
    if (err.kind === 'forbidden') {
      forbidden.value = true
      // 后端点名了被拒动作就用原话，否则保留兜底句（UI-SPEC §2.6）
      if (err.message && err.message !== '没有访问权限') deniedDescription.value = err.message
      return                        // 零 toast
    }
    showError(err, 'device.loadFailed')
  } finally { loading.value = false }
}
```

### Canonical 404 content block (S2)

```vue
<!-- Source: UI-SPEC Contract 3.1; screening/Detail.vue -->
<ErrorState
  v-if="notFound"
  variant="notFound"
  title="资源不存在"
  description="该资源可能已被删除，请返回列表查看最新数据。"
  :actions="[
    { label: '返回列表', onClick: goList, variant: 'primary' },
    { label: '重新加载该记录', onClick: fetchReportDetail, variant: 'default' }
  ]"
/>
```
```ts
const fetchReportDetail = async () => {
  loading.value = true
  try {
    report.value = (await screeningApi.getReportDetail(reportId)).data
  } catch (error) {
    const err = normalizeApiError(error)
    if (err.kind === 'notFound') { notFound.value = true; return }   // 零 toast
    showError(err, 'screening.loadFailed')
  } finally { loading.value = false }
}
```

## Runtime State Inventory

> This phase is **not** a rename/refactor/migration of identifiers, but it does rewrite 24 files' error paths. The relevant "runtime state" is browser-held, not DB-held.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | **None.** No DB column, collection name, or user_id key changes. `FIELD_LABELS`/`ERROR_MESSAGES` are compile-time constants. | none |
| Live service config | **None.** No external service config references error copy. | none |
| OS-registered state | **None.** | none |
| Secrets/env vars | **None.** `DASH_SCOPE_API_KEY`, `JWT_SECRET` etc. untouched. | none |
| Build artifacts / installed packages | `hhs-frontend-v2/dist/` (stale after migration — regenerated by `npm run build`); `hhs-backend/target/` untouched. Playwright `test-results/` + `playwright-report/` accumulate; not committed. | run `npm run build` in the phase gate; optionally clear `dist/` |
| Browser-held state (the real one) | `localStorage` holds `token` + cached `user` (no role field — this is *why* A-3 holds). `sessionStorage` holds `oauth_platform` / `oauth_timestamp` (`DeviceSync.vue:487-488`). Neither is affected by error-path changes. | none |
| Module-level runtime latches | `showError`'s `openToasts` array, `recentToasts` Map (2000ms), and the 401 latch in `utils/authSession.ts`. These persist across a page session. Tests reset them via `vi.resetModules()` (`showError.spec.ts:24-27`) and a monotonic `Date.now` spy (`ErrorLayerSample.spec.ts:309-310`). | Any new test that asserts toast counts **must** do the same, or it will flake against a prior test's latch |

## Validation Architecture

`workflow.nyquist_validation` is **true** in `.planning/config.json` — this section is required.

### Test Framework

| Side | Property | Value |
|------|----------|-------|
| Frontend | Framework | Vitest `^4.1.0` + `@vue/test-utils`, environment `happy-dom`, `globals: true` |
| Frontend | Config file | `hhs-frontend-v2/vitest.config.ts` |
| Frontend | Setup file | `hhs-frontend-v2/src/__tests__/setup.ts` (global `$message` mock only) |
| Frontend | Quick run (one file) | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/utils/appError.spec.ts` |
| Frontend | Full suite | `npm --prefix hhs-frontend-v2 run test:run` → 445 / 37 files |
| Frontend | Type gate | `npm --prefix hhs-frontend-v2 run build` (`vue-tsc -b`) |
| Backend | Framework | JUnit 5 + Mockito (Surefire excludes `*IntegrationTest`, `*PerformanceTest`) |
| Backend | Config file | `hhs-backend/pom.xml` |
| Backend | Quick run (one class) | `mvn -f hhs-backend/pom.xml test -Dtest=GlobalExceptionHandlerTest` |
| Backend | Full suite | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` → 845 |
| E2E | Framework | Playwright, `chromium`, `baseURL http://localhost:5173`, auto-`webServer` |
| E2E | Config file | `hhs-frontend-v2/playwright.config.ts` |
| E2E | Run | `npx --prefix hhs-frontend-v2 playwright test e2e/quick-input.spec.ts` (needs backend on 8082 + MySQL/Redis) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| ERR-03 | Server `fieldErrors` project into `fieldErrors` ref; summary sorted by `fieldOrder` | unit | `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/composables/useFormServerErrors.spec.ts` | ✅ (14 `it()`; add 1 for `labels` override) |
| ERR-03 | `:error` binding produces `.el-form-item.is-error` | unit (component) | new `src/__tests__/views/GoalFormInlineError.spec.ts` | ❌ **Wave 0** |
| ERR-03 | Each migrated form renders `FormErrorSummary` + N inline errors, 0 toasts on 400 | unit | per-wave spec (e.g. `RemindersInlineError.spec.ts`) | ❌ **Wave 0** (one per migrated form, or one shared parametrized spec) |
| ERR-05 | `apply(err)` leaves `form` deep-equal to its pre-call snapshot | unit | extend `ProfileErrorSample.spec.ts` pattern to each migrated form | ⚠️ pattern exists in `ProfileErrorSample.spec.ts` / `ThresholdWizard.spec.ts`; new forms need their own |
| ERR-05 | Failure does not close the dialog | unit | assert `emitted('update:modelValue')` absent / `visible` still true | ❌ **Wave 0** |
| ERR-07 | real HTTP 403 → `kind === 'forbidden'`, `retryable === false` | unit | `appError.spec.ts` (A-2 test ①) | ❌ **Wave 0** |
| ERR-07 | envelope `errorCode:'auth.forbidden'` + `code:500` → `forbidden` not `business` | unit | `appError.spec.ts` (A-2 test ②) | ❌ **Wave 0** |
| ERR-07 | envelope `code:404` → `notFound` | unit | `appError.spec.ts` (A-2 test ③) | ❌ **Wave 0** |
| ERR-07 | `errorCode:'health.profile_not_found'` → `notFound` not `business` | unit | `appError.spec.ts` (A-2 test ④) | ❌ **Wave 0** |
| ERR-07 | `notFound` preserves `err.code` so `Profile.vue` create-mode still triggers | unit (regression) | new case in `appError.spec.ts` **and** `ProfileErrorSample.spec.ts` | ❌ **Wave 0** — highest-value single test in the phase |
| ERR-07 | `forbidden` + `presentStates` → exactly 1 sticky toast, no retry button | unit | `showError.spec.ts` | ❌ **Wave 0** |
| ERR-07 | `notFound` + `presentStates` → 1 toast with `duration: 0` | unit | `showError.spec.ts` (guards the F4 `needsVNode` change) | ❌ **Wave 0** |
| ERR-07 | 403 page renders `ErrorState scope="page" variant="forbidden"`, 0 toasts | unit (component) | new `DevicePlatformConfigForbidden.spec.ts` | ❌ **Wave 0** |
| ERR-07 | 404 page renders content `ErrorState`, 0 toasts | unit (component) | new `ScreeningDetailNotFound.spec.ts` | ❌ **Wave 0** |
| ERR-08 | `showError` returns without toasting for `'cancel'`, `'close'`, `AbortError`, `CanceledError`, `ERR_CANCELED`, `ApiError('','cancel')` | unit | `showError.spec.ts` / `appError.spec.ts` | ✅ mostly (cancel coverage exists); add `ApiError('','cancel')` case |
| ERR-08 | Migrated delete handlers toast 0 times on cancel | unit | per-form spec asserting `errorToastCount() === 0` after a rejected confirm | ❌ **Wave 0** |
| ERR-08 | Machine check: no hand-written cancel guards remain | shell | corrected Contract 6.3 check ③ | ❌ **Wave 0** (as a documented command + expected output, optionally a `*.spec.ts` that greps source like `ErrorLayerSample.spec.ts:214-219` does) |
| D-05 | Frontend ≥ 445 green | shell | `npm run test:run` | ✅ |
| D-05 | Backend ≥ 845 green | shell | `mvn clean verify -DskipITs` | ✅ |
| D-05 | `lint:check` 0 errors | shell | `npm run lint:check` | ✅ (currently 0 errors / 216 warnings) |
| D-05 | `checkstyle:check` 0 violations | shell | `mvn checkstyle:check` | ✅ |
| D-05 | `build` green | shell | `npm run build` | ✅ |
| D-06 | `quick-input.spec.ts` success copy contract | e2e | `npx playwright test e2e/quick-input.spec.ts` | ✅ (`:80` asserts `录入成功`) |
| D-06 | Stop backend → 1 toast per action; list page → `ErrorState` + 0 toast; 60s offline → 0 toast; 400 → summary+inline+0 toast; 403 → page block + 0 toast; 404 → content block + 0 toast; cancel → 0; AbortError → 0 | **manual UAT** (browser) | n/a — DOM sampling with 300ms wait per UI-SPEC §6.4 | ❌ **Wave 0** (write the UAT script) |

### Sampling Rate

- **Per task commit:** the single spec file the task touches, e.g. `npm --prefix hhs-frontend-v2 run test:run -- src/__tests__/utils/appError.spec.ts` (target < 30 s).
- **Per wave merge:** `npm --prefix hhs-frontend-v2 run test:run` + `npm --prefix hhs-frontend-v2 run build` (full suite ≈ 13 s per the measured run; build adds `vue-tsc`).
- **Phase gate:** the full command list in §Baseline + Regression Gates, including backend `clean verify -DskipITs`, `checkstyle:check`, `lint:check`, `build`, the four corrected machine checks, `quick-input.spec.ts`, and the manual UAT.

### Wave 0 Gaps

Test files that must exist **before** the migration waves start (all new, in `hhs-frontend-v2/src/__tests__/`):

- [ ] `utils/appError.spec.ts` — **extend** with the 4 A-2 cases + the `code`-preservation regression case
- [ ] `utils/showError.spec.ts` — **extend** with `forbidden`/`notFound` × `presentStates` (2 cases, asserting `duration: 0` and absence of a retry button)
- [ ] `components/ErrorState.spec.ts` — **extend** with the 5 UI-SPEC-mandated cases (`scope="page"` class, `actions` suppresses `retry`, `actions` order, `label` is both visible text and `aria-label`, `forbidden` stays muted)
- [ ] `composables/useFormServerErrors.spec.ts` — **extend** with the `labels`-override case
- [ ] `utils/errorMessages.spec.ts` — **extend** `DICTIONARY_KEYS` + change the hardcoded `17` at line 84 to the final `FIELD_LABELS` count + 3–4 spot checks
- [ ] `views/DevicePlatformConfigForbidden.spec.ts` — **new**: first request 403 → `ErrorState scope="page"` rendered, `errorToastCount() === 0`, no second request issued
- [ ] `views/ScreeningDetailNotFound.spec.ts` — **new**: `notFound` → content `ErrorState` with both actions, `errorToastCount() === 0`
- [ ] `views/ProfileNotFoundPreserved.spec.ts` — **new**: `health.profile_not_found` still enters create mode after the `notFound` kind lands (regression guard for the highest-risk change)
- [ ] A per-form inline-error spec (or one parametrized spec covering goals / reminders / screening / user-Profile / device-config / UniversalMetricDialog) asserting the UI-SPEC §4.6 seven-condition checklist mechanically
- [ ] A source-grep spec for the corrected Contract 6.3 checks ②③④ (following the `ErrorLayerSample.spec.ts:214-219` `readFileSync` precedent), so "0 residue" is enforced by `test:run` rather than by a human remembering to grep
- [ ] No framework install needed — Vitest/Playwright/JUnit all present

*(If the planner prefers to keep the source-grep checks as shell steps in the plan rather than specs, that is acceptable — but they must then be listed verbatim with expected output, because a human-run grep is the only thing standing between this phase and a re-introduced `ElMessage.error`.)*

## Security Domain

`security_enforcement` is not set to `false` in `.planning/config.json`, so this section is required. Phase 14 is a presentation-layer phase; it changes no authentication, authorization, or transport behaviour. The security-relevant surface is narrow but real:

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (unchanged) | JWT filter untouched; `request.ts` interceptor untouched except that it must keep its translate-only contract |
| V3 Session Management | no (unchanged) | 401 latch + `authStore.logout({reason:'expired'})` untouched. **Do not** add a second 401 toast — `showError` returns early for `kind === 'auth'` (`showError.ts:145`) and must keep doing so |
| V4 Access Control | **yes — read-only** | The phase *renders* 403. It must **not** add client-side authorization (A-3). The 403 must come from the server response, never from a client role check |
| V5 Input Validation | yes | `useFormServerErrors` displays server-supplied messages. **Must render as text, never HTML** — see threat table |
| V6 Cryptography | no | No crypto touched |

### Known Threat Patterns for Vue 3 + Element Plus error surfaces

| Pattern | STRIDE | Standard Mitigation | Where it applies here |
|---------|--------|---------------------|------------------------|
| **XSS via backend error message** | Tampering / Elevation | Render as text node, never `v-html` / `dangerouslyUseHTMLString` | `showError` already uses `h('span', …, shown)` as a **text child** (`showError.ts:199`) and explicitly avoids `dangerouslyUseHTMLString` (comment at :117-118, threat T-11-10). `FormErrorSummary.vue:65` uses `{{ item.label }}：{{ item.message }}` interpolation — safe. `ErrorState.vue:65,67` uses `{{ }}` — safe. **The 87 migrations must not introduce `v-html` anywhere**, and the new `presentStates` VNode path must reuse the existing `h('span', …)` shape |
| **Sensitive data in error copy** | Information Disclosure | `SYSTEM_*` details are suppressed server-side; client must not echo stack traces | Backend already suppresses `SYSTEM_*` detail (`GlobalExceptionHandler.java:63-69`). Client-side: `AIInput.handleApiError` currently surfaces `axiosError.response.data.message` raw — the replacement with `showError` inherits backend sanitisation. **Do not** add `err.stack` or `err.constructor.name` to any copy |
| **Client-side authorization as a second source of truth** | Elevation of Privilege | Never gate on client state; render what the server returns | A-3 verified: `meta.requiresAdmin` is unread dead code; `UserVO` has no role. Adding a `router.beforeEach` role gate would create a false sense of enforcement **and** diverge from the backend (which currently denies *everyone*). Explicitly forbidden |
| **Error-message reflection in `data-field` selector** | Tampering | Escape before building a CSS selector | `useFormServerErrors.focusField` already uses `CSS.escape` with a fallback and try/catch (`:118-134`, threat T-11-15). New forms must route through `focusField`, never build their own selector |
| **Toast flooding as a DoS-ish nuisance** | Denial of Service (UX) | Cap visible toasts, dedupe | `showError` caps at 3 and latches 2000 ms (`:44-46, 71-77`). Contract 5 §5.4 forbids adding per-call-site throttles — respect that |
| **Unbounded field-error rendering** | Denial of Service (UX) | Cap server list | Backend caps at `MAX_FIELD_ERRORS = 20` (`GlobalExceptionHandler.java:43`). Client renders whatever arrives; the 20 cap is the control. Do not add a second cap |

**No new attack surface is introduced.** The one behavioural security change is that 403 now produces an explanatory page instead of a retryable-looking server toast — which *reduces* a mild information-disclosure/UX-confusion risk (it stops telling users "service unavailable, retry" when the truth is "you lack permission").

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Per-page `ElMessage.error('…失败')` | Single `showError(err, key)` exit | Phase 11 (2026-11-07) | 87 sites remain to be converged; the API is stable |
| `catch (e) { if (e !== 'cancel') … }` | `showError` handles all 5 cancel forms | Phase 11 D-12 | 8 hand-written guards remain |
| `error.response.data.message` peeking | `normalizeApiError` → `ApiError` with `message/kind/status/code` | Phase 11 ERR-01 | 3 dead peeks remain (`Score.vue:483,508`, `AIInput.vue:247`) |
| 403 rendered as a server toast (implicitly) | 403 → full-page `ErrorState`; 404 → content block | **This phase** | New `forbidden`/`notFound` kinds |
| `meta.requiresAdmin` as an implied gate | Dead code, explicitly not to be implemented | Phase 14 A-3 | Documented; `router.beforeEach` untouched |
| Route-level 404 → blank `MainLayout` | Optional catch-all → `ErrorState scope="page" variant="notFound"` | **This phase (planner's call)** | ~10 lines, reuses page geometry |

**Deprecated / outdated:**
- ROADMAP's "≥ 90 tests" baseline — stale v1.0 figure. Superseded by D-05's measured 845/445.
- `error !== 'close'` cancel checks — `'close'` is not produced by `ElMessageBox.confirm`. Dead condition; `isCancel` already covers `'close'` for other sources.
- `Score.vue`'s `reportAbortController` — created and `.abort()`ed but never passed as a `signal`, so it does nothing. Either wire it or delete it; do not "preserve" it as if it worked.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `npm run build` is currently green (not measured this session — only `lint:check` and `format:check` were run) | Baseline | If build is already red, the phase gate's `build` step would be a pre-existing failure and the planner must add a "fix pre-existing build" task. Low risk: `vue-tsc` is in the build script and CI runs it, but this working tree was not verified |
| A2 | `stores/wellness.ts` `addMetric` has no live caller (no `addMetric` reference found in `src/views` or `src/components`) | Store-Internal 8 #7 | If it *is* called via a dynamic path, deleting the branch would remove user feedback. Verify with a full `grep -rn "addMetric" src` before deciding |
| A3 | `floatingAi`'s panel has its own inline error surface, so removing the store toast will not leave the user with zero feedback | Store-Internal 8 #8 | If it has none, the send-failure would become silent — a regression. Must be checked by reading `FloatingChatPanel.vue` around line 215 |
| A4 | The exact final `FIELD_LABELS` count (39 vs higher) — depends on whether the planner adopts the gap-analysis additions | Forms §FIELD_LABELS gap | Wrong count → `errorMessages.spec.ts:84` fails. Low cost to fix, but it must be a deliberate number, not a guess |
| A5 | `docker`/Testcontainers remain unavailable, so `-DskipITs` is the correct backend gate | Baseline | If the daemon *is* reachable, `mvn clean verify` (without `-DskipITs`) would be a stronger gate. `docker info` returned client info but daemon reachability was not confirmed |
| A6 | E2E can run in this environment (Playwright browsers are cached; backend needs MySQL 3307 + Redis 6380 via `docker compose up`) | Baseline | If the stack cannot be brought up, D-06's automated half cannot run and the UAT must be manual-only. This would be a **blocking** environment gap, not a code problem |

## Open Questions (RESOLVED)

1. **Should the 3 success toasts in non-auth stores be moved?** (`ai.ts:123`, `wellness.ts:58`, `push.ts:83`)
   - What we know: UI-SPEC §5.1 permits them; Contract 6.3 check ② as written forbids them.
   - What's unclear: whether the spec author intended check ② to be "no `ElMessage.error` in stores" (consistent with §5.1) or "no `ElMessage` at all in stores" (inconsistent).
   - Recommendation: read it as "no `ElMessage.error` in stores" and **correct check ②** accordingly. Moving 3 success toasts is scope creep with real test churn.

   - **RESOLVED (2026-11-10): 不移动，修正 Contract 6.3 检查②。** 检查② 解读为「store 内不得出现 `ElMessage.error`」，与 §5.1 一致；错误仍一律上抛给组件层。
2. **How should send-message failures be surfaced?** (`ai.ts:105`, `floatingAi.ts:120`)
   - What we know: today the optimistic bubble is removed and `false` is returned; the chat page shows nothing.
   - What's unclear: toast vs inline retry bubble; and there is no `ai.sendFailed` dictionary key.
   - Recommendation: **inline bubble** ("发送未完成，请重试" with a retry affordance), because a transient toast for a chat message the user is watching disappear is worse than a persistent inline marker. If the planner prefers a toast, add `ai.sendFailed` to the dictionary and to `DICTIONARY_KEYS`.

   - **RESOLVED (2026-11-10): 就地 retry 气泡**（「发送未完成，请重试」+ 重试入口）。不新增 `ai.sendFailed` 字典键，避免额外动键数断言。
3. **Is the route-level 404 catch-all in scope?**
   - What we know: Contract 3 §3.6 defines it and explicitly defers the decision to the planner.
   - Recommendation: **include it.** It is ~10 lines, reuses the page geometry, and closes the only remaining blank-screen failure mode. Requires an explicit PLAN line item because it is a routing change.

   - **RESOLVED (2026-11-10): 纳入本阶段。** 复用 Contract 2 的页面几何，约 10 行。
4. **Should `collectInvalidFieldMessages` be extracted before or during the migration?**
   - What we know: it is duplicated verbatim in 2 files and needed by ~13 more.
   - Recommendation: extract it into `useFormServerErrors.ts` as an exported helper **in the same task as the first form migration**, so the 13 later forms import it. Doing it first avoids 13 copies; doing it later means a 13-file cleanup.

5. **Which `FIELD_LABELS` additions beyond the spec's 22?**
   - What we know: mood (5 fields), goals `description`/`startDate`/`endDate`/`note`, device-config (6 fields), user `avatar` are emitted by real DTOs but absent from the spec's 22.
   - Recommendation: add only those whose absence makes the summary unreadable (mood's 5 are the clearest case — `moodScore：...` is meaningless). For the rest, rely on UI-SPEC §4.5 rule 2 (render backend message alone). Record the decision per field in the ledger.

6. **Does `FloatingChatPanel.vue` have an inline error surface?**
   - What we know: not read in this session.
   - Recommendation: read `src/components/ai-floating-ball/FloatingChatPanel.vue` around line 215 before deciding store call #8's fate.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Frontend build/test | ✓ | v24.15.0 | — |
| npm | Frontend deps | ✓ | 11.12.1 | — |
| Java | Backend build/test | ✓ | 17.0.1 (2021-09-14 LTS) | — |
| Maven | Backend build/test | ✓ | 3.8.4 | — |
| `element-plus` | All rendering | ✓ | 2.14.0 installed (`^2.5.6` declared) | — |
| Vitest | Frontend tests | ✓ | 4.x | — |
| Playwright + chromium | E2E | ✓ | browsers cached (`chromium-1217`, `chromium-1223`) | — |
| Docker daemon | Testcontainers ITs | ⚠️ unverified (client 29.8.1 present; `WINDOWS.md` #3 says unreachable) | — | `-DskipITs` (the D-05 gate already uses it) |
| MySQL 3307 + Redis 6380 | Backend runtime, E2E | ✗ not running (not probed as a service) | — | `docker compose up` — required for `quick-input.spec.ts` and the manual UAT |
| `format:check` cleanliness | — | ✗ **98 files fail today** | — | not part of D-05's gate; do not add it |

**Missing dependencies with no fallback:**
- **MySQL + Redis for the E2E / UAT half of D-06.** `quick-input.spec.ts` needs a live backend on 8082, which needs MySQL and Redis. If `docker compose up` cannot run in this environment, the E2E step and the manual UAT both degrade to "not run" — which would leave success criterion 3 unverified. **This is the phase's only potentially blocking environment risk** and the planner should include an explicit early task: "bring up `docker compose up -d mysql redis backend`, confirm `curl localhost:8082/actuator/health`, then run E2E".

**Missing dependencies with fallback:**
- Docker daemon for Testcontainers → `-DskipITs` (already the D-05 baseline; `WINDOWS.md` #3/#4 record this as pre-existing).
- `format:check` → omit from the gate (pre-existing failure, out of scope).

## Sources

### Primary (HIGH confidence)
- `hhs-frontend-v2/src/utils/appError.ts` — read in full. `ErrorKind` union (5-12), `ApiError` (14-38), `CANCEL_SENTINELS` (41), `isCancel` (43-53), `normalizeApiError` classification order (55-158). Insertion point for new kinds: between line 110 and 116.
- `hhs-frontend-v2/src/utils/showError.ts` — read in full. `ShowErrorOptions` (24-39), cancel early-return (129), validation early-return (130), auth early-return (145), `needsVNode` (171), `duration: 4000` (178), VNode `duration: 0` (235).
- `hhs-frontend-v2/src/utils/errorMessages.ts` — read in full. 18 `ERROR_MESSAGES` keys, 17 `FIELD_LABELS` keys, `resolveMessage`, `fieldLabel`.
- `hhs-frontend-v2/src/utils/request.ts` — read in full. Envelope reject (52), cancel short-circuit (65), 401 latch (71-75).
- `hhs-frontend-v2/src/composables/useFormServerErrors.ts` — read in full. `apply` validation-only guard (79), `clearField` (101-106), `focusField` CSS.escape (118-146).
- `hhs-frontend-v2/src/components/common/ErrorState.vue` — read in full. Props (5-18), `ICONS` incl. `Lock`/`DocumentDelete` (21-27), `tinted` (32), scoped styles (83-135).
- `hhs-frontend-v2/src/components/common/FormErrorSummary.vue` — read in full. `focusSummary` expose, `{{ }}` interpolation (safe), scoped styles.
- `hhs-frontend-v2/src/assets/styles/theme.css` — `.el-input__wrapper` (502-508), `.is-focus` (512-515), `.el-select__wrapper` (672-677), `.is-focused` (681-684), Phase 11 error block (1207-1242), tokens (58-59, 173-174).
- `hhs-frontend-v2/node_modules/element-plus/es/components/form/src/form-item.vue_vue_type_script_setup_true_lang.mjs:187-190` — `watch(() => props.error, …)` → `setValidationState(val ? "error" : "")`, `immediate: true`. Confirms `:error` yields `.is-error`.
- `hhs-frontend-v2/node_modules/element-plus/theme-chalk/el-form-item.css` — `.el-form-item.is-error … { box-shadow: 0 0 0 1px var(--el-color-danger) inset }` with **no** `!important`; `.el-form-item__error { line-height: 1; padding-top: 2px; position: absolute; top: 100%; left: 0 }`.
- `hhs-frontend-v2/node_modules/element-plus/es/components/time-picker/src/common/picker.vue_vue_type_script_setup_true_lang.mjs:77-85, 449` — `rangeInputKls` includes `nsInput.e("wrapper")` + `nsDate.is("active", pickerVisible)`, applied to the range editor root. Confirms `.is-active`.
- `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java` — `handleBusinessException` (57-73), `MAX_FIELD_ERRORS = 20` (43), `handleAccessDenied` → `ResponseEntity.status(403)` (202-206), `handleNoResourceFoundException` → `Result.failure(404, …)` HTTP 200 (235-240), `handleException` → `Result.failure(500, …)` (245-249).
- `hhs-backend/src/main/java/com/hhs/common/Result.java` — `failure(String code, …)` → `parseCodeToInt` (81-83), `invalidInput` sets `code: 400` + `fieldErrors` (118-123), `parseCodeToInt` returns **500** for non-numeric (140-146).
- `hhs-backend/src/main/java/com/hhs/common/constant/ErrorCode.java` — `AUTH_FORBIDDEN(403,"auth.forbidden")` (28), 6 not-found codes (35-65), `GOAL_FORBIDDEN(403,"goal.forbidden")` (53).
- `hhs-backend/src/main/java/com/hhs/security/LoginUser.java:22-24` — `getAuthorities()` returns `Collections.emptyList()`.
- `hhs-backend/src/main/java/com/hhs/vo/UserVO.java` — no `role` field (id/username/nickname/avatar/email/phone/createdAt/updatedAt).
- `hhs-backend/src/main/resources/sql/schema.sql:29-44` — `sys_user` has no `role` column.
- `hhs-backend/src/main/java/com/hhs/controller/DevicePlatformConfigAdminController.java:35,46,58,70,82` — five `@PreAuthorize("hasRole('ADMIN')")`.
- `hhs-backend/src/main/java/com/hhs/config/SecurityConfig.java:34` — `@EnableMethodSecurity`.
- `hhs-frontend-v2/src/router/index.ts:110-114, 145-155` — `/settings/device-platform` route with unread `meta.requiresAdmin`; guard checks only `requiresAuth`; no catch-all.
- `hhs-frontend-v2/src/views/health/Thresholds.vue` — read lines 119-340. Zero `<el-form>`, zero `useFormServerErrors`, `presentValidation: true` at 259, `ErrorState` at 18.
- `hhs-frontend-v2/src/views/prevention/Profile.vue` — read 30-60, 385-410, 415-470. Reference implementation; not-found branch at 395-400.
- `hhs-frontend-v2/src/views/prevention/Risk.vue` — read 300-345, 385-400, 440-460.
- `hhs-frontend-v2/src/stores/push.ts` — read 20-60, 100-130. The D-02 reference pattern.
- Measured: `npm --prefix hhs-frontend-v2 run test:run` → 445/445, 37 files, exit 0.
- Measured: `mvn -f hhs-backend/pom.xml test -DskipITs` → 845 tests, 0 failures (summed `target/surefire-reports/*.txt`, 87 files).
- Measured: `npm run lint:check` → 0 errors, 216 warnings.
- Measured: `npm run format:check` → 98 files fail.
- Measured: `mvn checkstyle:check` → clean, exit 0.
- Measured: `grep -rn "ElMessage\.error" src --include=*.vue --include=*.ts | grep -v "__tests__" | wc -l` → 87.

### Secondary (MEDIUM confidence)
- UI-SPEC `.planning/phases/14-error-rollout/14-UI-SPEC.md` — read Contracts 1-7, Copywriting, UI Considerations, Appendix A-1..A-4. Treated as binding; three internal contradictions identified and corrected in §Contract 6.3.
- `11-UI-SPEC.md:15, 274, 288-295` — confirms `forbidden`/`notFound` variants exist in the Phase 11 component API with page wiring explicitly deferred to Phase 14, and that the ErrorState description color is `--text-3` (Phase 14 tightens to `--text-2`).
- `.planning/WINDOWS.md` — 4 open broken windows, all pre-existing and unrelated to this phase (Testcontainers ×1, backstop visual checks ×3).

### Tertiary (LOW confidence)
- None. Every claim above is either read from source this session or measured by running a command. The six items in §Assumptions Log are explicitly tagged `[ASSUMED]` where they could not be verified (notably A1 `build` green, A2 `addMetric` uncalled, A3 `FloatingChatPanel` inline surface, A5 Docker daemon).

## Metadata

**Confidence breakdown:**
- **Standard stack: HIGH** — versions read from `node_modules` and `package.json`; zero new dependencies; EP 2.14.0 source read directly for the two behavioural claims the whole visual contract rests on (`:error` → `.is-error`; `.is-active` on daterange).
- **Architecture: HIGH** — every tier assignment is backed by a file read; the one non-obvious claim (client cannot gate by role) is proven three ways (`UserVO` no role, `sys_user` no column, `getAuthorities()` empty list).
- **87-site inventory: HIGH** — count reproduced exactly; every row's line number came from a single `grep -n` pass; each row's bucket is justified by reading the surrounding function. The DEAD bucket (12 rows) rests on one verified fact (`request.ts:45-52` rejects on `code !== 200`).
- **403/404 reachability: HIGH** — real 403 proven reachable and proven to be *unavoidable* for every user; 404 proven to arrive in two distinct envelope shapes with the exact numeric/string split.
- **Cancel sites: HIGH** — 8 guards + 1 name-check + 3 bare catches enumerated with line numbers and exact replacement forms.
- **Contract 6.3 checks: HIGH on the contradiction, MEDIUM on the correction** — the contradictions are measured; the corrected commands are a recommendation the planner should validate once after the migration.
- **Pitfalls: MEDIUM-HIGH** — pitfalls 1-3, 7-10 are evidenced by file reads; pitfalls 4-6, 11 are reasoned from the code's shape rather than observed failing.

**Research date:** 2026-11-10
**Valid until:** 2026-12-10 (30 days — stable codebase, locked dependency versions, no fast-moving external API)
