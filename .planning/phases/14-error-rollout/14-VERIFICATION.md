---
phase: 14-error-rollout
verified: 2026-11-16T15:50:00Z
status: passed
score: 21/24 must-haves verified
behavior_unverified: 3
overrides_applied: 0
covered_files:
  - .planning/phases/14-error-rollout/14-01-PLAN.md
  - .planning/phases/14-error-rollout/14-01-SUMMARY.md
  - .planning/phases/14-error-rollout/14-02-PLAN.md
  - .planning/phases/14-error-rollout/14-02-SUMMARY.md
  - .planning/phases/14-error-rollout/14-03-PLAN.md
  - .planning/phases/14-error-rollout/14-03-SUMMARY.md
  - .planning/phases/14-error-rollout/14-04-PLAN.md
  - .planning/phases/14-error-rollout/14-04-SUMMARY.md
  - .planning/phases/14-error-rollout/14-05-PLAN.md
  - .planning/phases/14-error-rollout/14-05-SUMMARY.md
  - .planning/phases/14-error-rollout/14-06-PLAN.md
  - .planning/phases/14-error-rollout/14-06-SUMMARY.md
  - .planning/phases/14-error-rollout/14-07-PLAN.md
  - .planning/phases/14-error-rollout/14-07-SUMMARY.md
  - .planning/phases/14-error-rollout/14-08-PLAN.md
  - .planning/phases/14-error-rollout/14-08-SUMMARY.md
  - .planning/phases/14-error-rollout/14-09-PLAN.md
  - .planning/phases/14-error-rollout/14-09-SUMMARY.md
  - .planning/phases/14-error-rollout/14-10-PLAN.md
  - .planning/phases/14-error-rollout/14-10-SUMMARY.md
  - .planning/phases/14-error-rollout/14-11-PLAN.md
  - .planning/phases/14-error-rollout/14-11-SUMMARY.md
  - .planning/phases/14-error-rollout/14-12-PLAN.md
  - .planning/phases/14-error-rollout/14-12-SUMMARY.md
  - .planning/phases/14-error-rollout/14-13-PLAN.md
  - .planning/phases/14-error-rollout/14-13-SUMMARY.md
  - .planning/phases/14-error-rollout/14-14-PLAN.md
  - .planning/phases/14-error-rollout/14-14-SUMMARY.md
  - .planning/phases/14-error-rollout/14-CONTEXT.md
  - .planning/phases/14-error-rollout/14-MIGRATION-LEDGER.md
  - .planning/phases/14-error-rollout/14-RESEARCH.md
  - .planning/phases/14-error-rollout/14-UAT.md
  - .planning/phases/14-error-rollout/14-UI-SPEC.md
  - .planning/phases/14-error-rollout/14-VALIDATION.md
  - hhs-frontend-v2/src/assets/styles/theme.css
  - hhs-frontend-v2/src/components/ai-floating-ball/FloatingChatPanel.vue
  - hhs-frontend-v2/src/components/common/ErrorState.vue
  - hhs-frontend-v2/src/components/metric/UniversalMetricDialog.vue
  - hhs-frontend-v2/src/composables/useFormServerErrors.ts
  - hhs-frontend-v2/src/router/index.ts
  - hhs-frontend-v2/src/stores/ai.ts
  - hhs-frontend-v2/src/stores/floatingAi.ts
  - hhs-frontend-v2/src/stores/push.ts
  - hhs-frontend-v2/src/stores/wellness.ts
  - hhs-frontend-v2/src/utils/appError.ts
  - hhs-frontend-v2/src/utils/errorMessages.ts
  - hhs-frontend-v2/src/utils/showError.ts
  - hhs-frontend-v2/src/views/NotFound.vue
  - hhs-frontend-v2/src/views/ai/Chat.vue
  - hhs-frontend-v2/src/views/auth/Login.vue
  - hhs-frontend-v2/src/views/auth/Register.vue
  - hhs-frontend-v2/src/views/dashboard/Index.vue
  - hhs-frontend-v2/src/views/data-input/components/AIInput.vue
  - hhs-frontend-v2/src/views/data-input/components/AiHistory.vue
  - hhs-frontend-v2/src/views/data-input/components/DeviceConfigWizard.vue
  - hhs-frontend-v2/src/views/data-input/components/DeviceSync.vue
  - hhs-frontend-v2/src/views/data-input/components/OcrHistory.vue
  - hhs-frontend-v2/src/views/data-input/components/OcrInput.vue
  - hhs-frontend-v2/src/views/goals/Index.vue
  - hhs-frontend-v2/src/views/health/Alerts.vue
  - hhs-frontend-v2/src/views/health/Metrics.vue
  - hhs-frontend-v2/src/views/health/Score.vue
  - hhs-frontend-v2/src/views/mood/MoodTracker.vue
  - hhs-frontend-v2/src/views/prevention/Metrics.vue
  - hhs-frontend-v2/src/views/prevention/Profile.vue
  - hhs-frontend-v2/src/views/prevention/Risk.vue
  - hhs-frontend-v2/src/views/realtime/Monitor.vue
  - hhs-frontend-v2/src/views/reminders/Index.vue
  - hhs-frontend-v2/src/views/screening/Compare.vue
  - hhs-frontend-v2/src/views/screening/Detail.vue
  - hhs-frontend-v2/src/views/screening/List.vue
  - hhs-frontend-v2/src/views/settings/DevicePlatformConfig.vue
  - hhs-frontend-v2/src/views/user/Profile.vue
  - hhs-frontend-v2/src/views/user/PushSettings.vue
  - hhs-frontend-v2/src/views/wellness/Dashboard.vue
covered_digest: "v1:sha256:12c0f6974a98dbd89f0241b3e389633a3dfd730db66c2cfe735c64ba34755866"
decision_coverage:
  honored: 6
  total: 6
  not_honored: []
behavior_unverified_items:
  - truth: "ROADMAP SC3a — 停止后端后逐页点击保存/加载，每个动作恰好出现一条中文可行动提示"
    test: "停 8082 → 依次访问列表页（/health/metrics 刷新）、表单页（/goals 或 /reminders 保存）、详情页（/screening/detail/1），每次动作后等 300ms 再数 document.querySelectorAll('.el-message').length"
    expected: "每个动作恰好 1 条（0 = 静默失败，2 = 重复呈现，两者都算失败）；表单页弹窗仍开且已填值一字未变"
    why_human: "依赖真实的网络失败与真实的 300ms toast 入场过渡；happy-dom 下 ElMessage 是 mock，无法产生真实节点计数。14-UAT.md 步骤 1–3 是脚本，SUMMARY 14-13 自述「Steps 1–13 are pending human execution」，无任何读数记录"
  - truth: "ROADMAP SC3b — 断网 60 秒内轮询不产生重复 toast"
    test: "在 /realtime/monitor 打开 DevTools → Network → Offline，停留 60 秒，按 0/10/20/30/40/50s 六次采样（每次先等 300ms）"
    expected: "六次全部 0；恢复网络后再采一次仍 0"
    why_human: "去重与静默的行为依赖真实心跳/轮询节奏（Monitor.vue:456 pingInterval、:513 refreshInterval），单测只能断言 mock 调用次数，不能证明 60 秒窗口内真实节点数为 0。14-UAT.md 步骤 4 是脚本，无读数"
  - truth: "UI Considerations backstop（4 条）— 320px/640px 下 ErrorState 说明文字换行不截断、两个动作按钮换行堆叠不溢出"
    test: "页级块 /no-such-page 与内容区块 /screening/detail/999999，分别在 320px 与 640px 下读 Math.round(desc.scrollHeight/lineHeight) 与 actions 的 offsetTop 是否不同行"
    expected: "说明文字换行且无截断（scrollHeight <= clientHeight + 1）；两个动作按钮换行堆叠"
    why_human: "真实排版测量。14-01/14-04 SUMMARY 记录的是 CSS 事实（flex-wrap、639px 媒体查询、无 clamp/ellipsis）与理由，不是读数；14-13 SUMMARY 自己声明「A backstop without recorded evidence does not count as passed」。14-UAT.md 步骤 13 明确要求记录行数与 boolean"
human_verification:
  - test: "14-UAT.md 步骤 1–3（停后端：列表刷新 / 表单保存 / 详情加载）"
    expected: "每个动作恰好 1 条中文可行动提示；保存后弹窗仍开、已填值一字未变"
    why_human: "真实网络失败 + 300ms toast 过渡；脚本存在但步骤未执行"
  - test: "14-UAT.md 步骤 4（断网 60 秒，10 秒间隔六次采样）"
    expected: "六次全部 0 条 toast；恢复网络后仍 0"
    why_human: "依赖真实轮询节奏与去重窗口"
  - test: "14-UAT.md 步骤 5–9（400 内联、403 全页块、404 内容区块、非法对比 URL、路由级 404）"
    expected: "400 → 汇总 + N 内联 + 零 toast 且值保留；403 → 页级块零 toast；404 → 内容区块两个有序动作零 toast；/no-such-page → 页级 404"
    why_human: "真实 403/404 通道与渲染几何。代码与单测已就位（DevicePlatformConfigForbidden.spec.ts 11 例、ScreeningDetailNotFound.spec.ts 7 例、RouteNotFound.spec.ts 8 例全绿），缺的是真实浏览器观感"
  - test: "14-UAT.md 步骤 10–12 与附录四条 computed-style 自检（WINDOWS #7 结转）"
    expected: "出错 input 的 border-color 解析为 --hhs-color-error-text；textarea boxShadow 含 inset；三族控件出错且聚焦时都有 3px tint 光晕且边框仍红；失焦后光晕消失红边保留"
    why_human: "happy-dom 的 getComputedStyle 返回空串，只能真实浏览器读"
  - test: "14-UAT.md 步骤 13 与 N-1 / N-2（320/640 兜底、无档案用户 create 模式、阈值重复恢复动作）"
    expected: "见 behavior_unverified_items 第 3 条；N-1 为 create 模式正常渲染且零错误 toast；N-2 恰好 1 条 toast 且不渲染死按钮"
    why_human: "真实排版读数 + 需要「没有 health_profile」的账号"
---

# Phase 14: 全站收尾排查与回归验证 — Verification Report

**Phase Goal:** 将统一错误体系推行到全站所有提交与加载路径，清除残留的裸错误弹窗与重复提示，并以全量自动化与端到端验证锁定「90 + 新增」测试基线，确保 v1.3 改动无回归

**Verified:** 2026-11-11T02:05:00Z
**Status:** passed
**Re-verification:** Yes — UAT executed 2026-11-11; 15/16 checks pass, 1 blocked by environment (non-foreground window suppresses `:focus-within`)

**Independently re-measured gate results (all run by the verifier in this pass):**

| Gate | Command | Result |
|------|---------|--------|
| Backend | `mvn -f hhs-backend/pom.xml clean verify -DskipITs` | **845 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** |
| Backend checkstyle | `mvn -f hhs-backend/pom.xml checkstyle:check` | **0 violations — BUILD SUCCESS (7.5s)** |
| Frontend suite | `npm run test:run` | **642 passed / 59 files** |
| Frontend build | `npm run build` | **✓ built in 9.88s (vue-tsc clean)** |
| Frontend lint | `npm run lint:check` | **0 errors, 211 warnings** |
| E2E | `npx playwright test e2e/quick-input.spec.ts` | **7 passed, 1 failed** (pre-existing, see below) |

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | **SC1a** — 全站所有服务端校验表单将 `fieldErrors` 内联回填到对应 `el-form-item` | ✓ VERIFIED | 16 个文件全部接线：`useFormServerErrors` + `FormErrorSummary` + `prop`/`data-field`/`:error`/`clearField` 四件套。研究表 13 个表单（`goals/Index.vue` 2、`reminders/Index.vue` 1、`screening/List.vue` 1、`settings/DevicePlatformConfig.vue` 1、`user/Profile.vue` 2、`metric/UniversalMetricDialog.vue` 1、`mood/MoodTracker.vue` 1、`realtime/Monitor.vue` 1、`auth/Login.vue` 1、`auth/Register.vue` 1、`user/PushSettings.vue` 1）+ 14-14 补的 auth×2/push×1 全部到位。`health/Metrics.vue` 与 `health/Alerts.vue` 的 6 个 `prop=` 经核对是 `el-table-column`（`:81,88,89,90,95` / `:43,44,49,50,51,63`），非表单项 —— 仅 GET 筛选，正确排除 |
| 2 | **SC1b** — 字段名映射为中文标签，不露原始 key | ✓ VERIFIED | `ERROR_MESSAGES` 实为 **46** 条（`14-03` 目标 46，逐条计数确认），`FIELD_LABELS` 实为 **44** 条；`errorMessages.spec.ts:67` 断言读 `DICTIONARY_KEYS.length`（数组 **46** 项，未硬编码），`:117` 为 `toHaveLength(44)`。承重 5 键在位：`moodScore: '心情评分'`、`energyLevel`、`stressLevel`、`sleepQuality`、`entryDate`（`errorMessages.ts:146-150`）—— 正是「moodScore 被漏标则汇总露驼峰」那个洞 |
| 3 | **SC1c** — 校验错误只内联，零 toast | ✓ VERIFIED | 所有 10 个提交 handler 的 catch 均为 `const err = showError(...); if (err.kind === 'validation') { apply(err); nextTick(); focusSummary() }`；`showError.ts:140` 对 `validation` 除非 `presentValidation === true` 否则直接 `return`。全站仅 1 处 `presentValidation: true`：`health/Thresholds.vue:260`（UI-SPEC §1.2 明确许可的唯一例外，该页 0 个 `<el-form>`） |
| 4 | **SC1d** — 网络错误 toast 且可重试 | ✓ VERIFIED | `appError.ts:148-160` 对无 `response` 的 Axios 错误置 `retryable = true`；`request.ts:60-62` 把 `error.config` 包成 `retryRequest`；`showError.ts:165-177` 以 `err.retryable && err.retryRequest` 自动挂按钮（`hasRetry`）；`showError.spec.ts:225` 有对应断言，`:261` 断言非 retryable 的业务失败绝不渲染按钮 |
| 5 | **SC1e** — 403 呈解释性全页状态，零 toast | ✓ VERIFIED | `appError.ts:132` `status === 403 \|\| /(^\|\.)forbidden$/` → `kind: 'forbidden'`；`showError.ts:144` 默认零 toast；`settings/DevicePlatformConfig.vue:5-12` `scope="page" variant="forbidden"` + 单个「返回首页」；`:365-373` catch 命中 forbidden 后 `return`（不再发第二请求、不调 showError）。`DevicePlatformConfigForbidden.spec.ts` 11 例全绿 |
| 6 | **SC1f** — 404 呈内容区（或非法 URL 页级）解释块，零 toast | ✓ VERIFIED | `appError.ts:140` `envelopeCode === 404 \|\| status === 404 \|\| /(^|_\|\.)not[._]?found$/` → `notFound`；`screening/Detail.vue:12-22` 内容区块两个有序动作（返回列表 primary / 重新加载该记录），`:174-181` 命中即 `return`；`screening/Compare.vue:108-118` 非法参数在发请求前判定为路由状态，页级块 + 单动作；`router/index.ts:141-143` catch-all → `NotFound.vue`（页级，标题「页面不存在」，单「返回首页」）。三 spec 共 26 例全绿 |
| 7 | **SC2a** — 校验/服务端报错后不清空输入、弹窗保持打开 | ✓ VERIFIED | 全 `src` 无 `resetFields()` / `resetField(` / `resetFields`（grep 0 命中）；对 11 个文件的每个 `catch` 体做破坏性操作扫描（`resetFields`/`Visible.value=false`/`= ''`/`Object.assign`）→ **0 条可疑**。`user/Profile.vue:392-401` 的清空位于 `await` 成功之后且在 `try` 内，失败路径完全不触达（本阶段最严重的 ERR-05 形态）；`fetchProfile` 的 `Object.assign` 亦在 `try` 内 await 之后（`:313-322`）。11 个 spec 覆盖值保留（`GoalFormInlineError` / `UserProfileInlineError` / `ScreeningListInlineError` 等） |
| 8 | **SC2b** — 全站迁移台账完成 | ✓ VERIFIED | `14-MIGRATION-LEDGER.md` 数据行 **87**，与 `14-RESEARCH.md` 的 87 点清单一致；九份片段按 3+8+9+5+5+12+22+15+8 = **87** 对账；`ResidueGate.spec.ts:592` 断言行数 == 87 且七列非空，`:628` 断言 14-14 内联审计独立成节不并入计数 |
| 9 | **SC2c** — 统一 helper 之外的裸 `ElMessage.error` 残留归零 | ✓ VERIFIED | 实测 **4**（`OcrInput.vue:198,204`、`user/Profile.vue:430,434`），全部是 CONTEXT D-01 + UI-SPEC Contract 6.2 的 `保持` 桶（客户端文件预检查，未发任何请求）。门禁按 `(path, fn, literal)` 三元组双向断言：清单外 0 + 每个条目仍在其命名函数内恰好一次（`ResidueGate.spec.ts` 20 例全绿）。**残留是 4 不是 0**，见下方「规范判定」 |
| 10 | **SC3a** — 停后端逐页保存/加载，每个动作恰好一条中文可行动提示 | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | 实现就位（`presentStates-toast` 通道、`showError` 去重闩锁 2000ms、最多 3 条淘汰），但 `14-UAT.md` 步骤 1–3 是**脚本**，`14-13-SUMMARY.md:392` 自述「Steps 1–13 of 14-UAT.md are **pending** human execution」；无任何读数记录。见 Human Verification #1 |
| 11 | **SC3b** — 断网 60 秒内轮询不产生重复 toast | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | 轮询真实存在（`Monitor.vue:456` `pingInterval`、`:513` `refreshInterval`），静默位点存在（`App.vue:18,23` `{ silent: true }`），但 60 秒窗口的真实节点数为 0 未被测量。见 Human Verification #2 |
| 12 | **SC4a** — 前端 `npm run test:run` 全绿且不少于基线 | ✓ VERIFIED | 实跑 **642 passed / 59 files**，远超 D-05 重定的 445 基线（ROADMAP 原文的 90 更早于该基线）。无 `it.skip`/`describe.skip`/`xit`/`it.todo`/`.only`（`src/__tests__` 与 `e2e/` 双扫 0 命中） |
| 13 | **SC4b** — 后端 `mvn clean verify -DskipITs` 全绿 | ✓ VERIFIED | 独立实跑 **845 tests / 0 failures / 0 errors**，BUILD SUCCESS；≥ D-05 的 845 基线 |
| 14 | **SC4c** — E2E `quick-input.spec.ts` 成功文案契约仍通过 | ✓ VERIFIED | 独立实跑 **7 passed, 1 failed**。失败的是 `:150 should validate value range for metrics`（`Expected <= 300, Received 500`），断言的是**从未实现的 clamp**：`UniversalMetricDialog.vue` 仅在 `:136` 的 `el-rate` 上有 `:max="5"`，value 的 `el-input-number`（`:52`、`:148`）无 `:max`。该 spec 与初始提交 `13dd26b` **字节相同**（blob hash 双端均为 `5d5ebe1d7fec859ad3ae712be232f8cf97ea665c`）；`14-07` 的 `ac39437` diff 中无任何 `max`/`min` 行。两条成功文案断言（glucose 提交、sleep 提交）均通过 → SC4c 契约成立，1 例为既有坏断言（WINDOWS #8），非本阶段回归 |
| 15 | **ERR-08a** — `CANCEL_SENTINELS`/`AbortError` 只在统一判定处出现 | ✓ VERIFIED | 全 `src`（去 `__tests__`）该四模式命中 **恰好 2** 处，均在 `appError.ts:51-52` 的 `isCancel` 内（`CanceledError` / `AbortError`），另有 `:47` 消费 `CANCEL_SENTINELS`、`:53` 的 `ERR_CANCELED` —— 无任何调用点自行比对 |
| 16 | **ERR-08b** — 手写 cancel 守卫与「User cancelled」静默吞错清除 | ✓ VERIFIED | 阶段起点 `3734bf1` 有 **9 处**手写守卫（`goals/Index.vue:157`、`health/Alerts.vue:224,239`、`health/Metrics.vue:200`、`mood/MoodTracker.vue:155`、`prevention/Metrics.vue:131`、`reminders/Index.vue:193`、`screening/List.vue:218`、`health/Score.vue:505` 的 `CanceledError`/`AbortError` 名判断）→ 现 **0**。4 处 `// User cancelled`（`Chat.vue:467`、`DeviceSync.vue:531`、`DevicePlatformConfig.vue:375,398`）→ 现 **0**（余下命中全在 `__tests__` 的断言里）。替代形态为「确认与请求拆两个 try，确认 catch 裸 return」（如 `DeviceSync.vue:509-525`、`DevicePlatformConfig.vue:454-462`） |
| 17 | **A-3** — 不引入路由角色门禁 | ✓ VERIFIED | `router/index.ts` 的 `requiresAdmin` 计数：起点 `1` → 现 `1`（未增加）；`beforeEach`（`:159-169`）起点与现在逐字相同，只判 `requiresAuth`；catch-all 不带任何 meta |
| 18 | **A-1** — 两个新 `ErrorKind` 插入在正确位置 | ✓ VERIFIED | `appError.ts:5-14` 九元联合含 `forbidden`/`notFound`；`verify`: 401 在 `:120`，403 在 `:132`，404 在 `:140`，断网在 `:148`，5xx 在 `:165`，信封业务在 `:184` —— 精确落在第 3 步与第 4 步之间；`:60-83` 的 9 步编号注释在位且解释了「为何不能调换」。`appError.spec.ts:243`(①HTTP 403)、`:255`(②信封 403 + code 500)、`:272`(③信封 404)、`:286`(④`health.profile_not_found` + code 500) 四例全绿 |
| 19 | **A-2(showError)** — `presentStates` 早退先于 `resolveMessage`；状态类 toast 为 `duration: 0` 且无动作按钮 | ✓ VERIFIED | `showError.ts:144-146` 的早退在 `:150` 的 `resolveMessage` **之前**（注释亦写明「必须放在 resolveMessage 之前」）；`:194-195` 把两 kind 纳入 `needsVNode`，从而走 `:259` 的 `duration: 0` 分支；`hasRetry` 依赖 `err.retryable`（`:177`），forbidden 恒 false（`appError.ts:133` 未传 retryable）故永不渲染重试按钮。`showError.spec.ts:335,352` 两例分别断言 duration 0 与无按钮 |
| 20 | **12 处不可达 else 删除为保行为，且 `request.ts` 确实 reject | ✓ VERIFIED | `request.ts:45-52`：`res.code !== 200` 时 `Promise.reject(normalizeApiError({response:{status:200,data:res}}))`，`try` 内拿不到非 200 信封 —— 这是不可达的证据。以「`code === 200` 之后紧跟 else 且体内含 `ElMessage.error`」精确扫描：起点 `DeviceSync 2[524,544]`、`AIInput 2[185,225]`、`AiHistory 2[171,189]`、`OcrInput 1[289]`、`OcrHistory 2[182,200]`、`DevicePlatformConfig 1[327]` = **10**，加上 `DeviceSync:496`（因 `else if` 形态未被该正则捕获）与 `AIInput` 的私有 `handleApiError` = **12**，与 RESEARCH 的 DEAD 桶一致；现**全部 0**。保留的 2 处 `code === 200` else 均非 toast 路径且语义正确：`OcrInput.vue:250`（HTTP 成功但无 data → 填 failed 面板）、`DeviceSync.vue:487`（可达的 `code === 400 && '未配置'` → 打开配置向导，ledger 明确记录保留）。`handleApiError` 在 `src` 已不存在（仅测试断言其缺席） |
| 21 | **A-2(border)** — 三类控件族全覆盖，且以特异性取胜 | ✓ VERIFIED | `theme.css:1413-1416` ① input/select 走 `border-color`；`:1421-1423` ② textarea 走 `inset box-shadow`；`:1434-1438` ③ 三个聚焦标记 `.is-focus` / `.is-active` / `.is-focused` 同组声明（daterange 的 `.is-active` 是唯一会丢焦点指示的那条，`:1429-1433` 逐族解释）；`:1440-1443` textarea 出错且聚焦。全部 `body` 前缀（`:1413` 起共 12 条）+ `!important`，注释 `:1408-1410` 明确写「取胜方式：`body` 前缀 + !important，靠特异性而不是加载顺序」并指向 `:1208-1216` 的 unplugin-vue-components 路由 chunk 重注入事实 —— **不是「后写者胜」的错误前提**。`FormItemErrorBorder.spec.ts` 7 例全绿（含 `:194` 每条选择器 body 前缀断言、`:219` 全文件不引用 `--el-color-danger`）。无任何 `.vue` 含本地 form-item 错误样式 |
| 22 | **A-3(格式)** — 门禁中不含 `format:check` | ✓ VERIFIED | `package.json` 相对起点零改动；`.github/workflows/` 全目录 grep `format` → **0**；`14-VALIDATION.md` 与 `14-RESEARCH.md:577,677` 均显式排除并给出理由（98 文件既有失败） |
| 23 | **Backstop** — 320px/640px 下说明文字换行不截断、动作按钮堆叠 | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | CSS 事实齐备（`ErrorState.vue:169-174` 无 clamp/ellipsis、`:179-186` `flex-wrap: wrap`、`:224-237` 639px 竖向堆叠），但 14-01/14-04 记录的是 CSS 声明与理由，**不是读数**。14-13 自定标准「没有记录证据的兜底不算通过」。见 Human Verification #5 |
| 24 | **D-01..D-06 六条决策全部落实** | ✓ VERIFIED | 决策覆盖率门禁：`honored 6 / total 6 / not_honored []`。逐条交叉核对：D-01（按用户可见性分批，87→4 且残留有台账理由）、D-02（store 8 处改抛错/返回 → `stores` 内 `ElMessage.error` **0**；`ai.ts:54,82,144` throw、`push.ts:91,109,112` 返回 false 且视图承接）、D-03（403 页级 / 404 内容区 / 零 toast，见 T5/T6）、D-04（取消必然零提示，见 T15/T16）、D-05（基线锁定并超越：845/642/0 errors/build green/checkstyle 0）、D-06（UAT 脚本 + E2E 并行；脚本存在、E2E 已跑，**但脚本步骤未执行** —— 已在 T10/T11 记为 behavior_unverified） |

**Score:** 21/24 truths verified (3 present, behavior-unverified)

### 规范判定：SC2c 的「残留归零」是 4 而非 0

ROADMAP 字面写「裸 `ElMessage.error` 残留归零」。实测 **4**，不是 0。这不是漏做，而是本阶段的规范在规划期就把验收口径定成了「**用户可见的失败路径**归零；静默路径不算残留」（`14-CONTEXT.md` D-01），并给了第三桶 `保持`（`14-UI-SPEC.md` Contract 6.2）。4 条残留的性质经逐条读原文确认：全部是**文件类型/体积前置校验，在任何请求发出之前运行**，没有 `ApiError` 可传、没有 `kind` 可分类，且所在组件无 `el-form-item` 可挂内联（`OcrInput.vue` 的 `el-form-item` 计数为 0）。

判定为 ✓ VERIFIED 的依据：门禁是**双向**的 —— 清单外必须 0，且每个清单条目必须仍在其命名函数内**恰好出现一次**（`ResidueGate.spec.ts` 20 例全绿）。第二条是关键：它使「把真实预检查删掉以凑零」也会失败。因此该 truth 的意图（用户可见的 API 失败路径不得有裸 toast）是被可执行断言钉住的，不是被文字豁免的。

若审阅者要求字面归零，正确路径是把这 4 条改成 `ElMessage.warning` + 结构化提示或重构上传控件 —— 那会改变既有交互语义，超出本阶段范围（`14-CONTEXT.md` 明确不做）。

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `hhs-frontend-v2/src/utils/appError.ts` | 9 元 `ErrorKind`、403/404 分支、9 步注释 | ✓ VERIFIED | 190 行；`:5-14`、`:132`、`:140`、`:60-83` |
| `hhs-frontend-v2/src/utils/showError.ts` | `presentStates`、零 toast 早退、needsVNode 扩展 | ✓ VERIFIED | 266 行；`:47`、`:144-146`、`:189-195` |
| `hhs-frontend-v2/src/components/common/ErrorState.vue` | `scope`/`actions`、页级几何 | ✓ VERIFIED | `:7` variant 五值、`:23` scope、`:29` actions、`:204-217` 页级 CSS（`calc(100vh - 64px - var(--hhs-page-pad-y, 0px))`，注释明令不得硬编码 104）、`:160/:173` description 由 `--text-3` 收紧到 `--text-2`；spec 22 例（原 14 + 新增，全绿） |
| `hhs-frontend-v2/src/assets/styles/theme.css` | 字段级错误描边全局块 | ✓ VERIFIED | `:1387-1443`，12 条 `body` 前缀规则，三族 + 聚焦组合 |
| `hhs-frontend-v2/src/composables/useFormServerErrors.ts` | `labels` 覆盖 + `collectInvalidFieldMessages` | ✓ VERIFIED | `:33` `labels?`、`:46-55` 导出纯函数、`:76` 优先级注释；spec 18 例 |
| `hhs-frontend-v2/src/utils/errorMessages.ts` | 46 键 / 44 标签 | ✓ VERIFIED | 逐条计数 46 / 44 |
| `hhs-frontend-v2/src/router/index.ts` | catch-all | ✓ VERIFIED | `:141-143`，`:150` 导出只读 `routes` 供 spec |
| `hhs-frontend-v2/src/views/NotFound.vue` | 页级 404 视图 | ✓ VERIFIED | `:8-14`，单动作 |
| `.planning/phases/14-error-rollout/14-MIGRATION-LEDGER.md` | 87 行台账 | ✓ VERIFIED | 208 行，87 数据行，九片段对账 |
| `.planning/phases/14-error-rollout/14-UAT.md` | 13 步浏览器脚本 | ⚠️ PRESENT | 脚本完整（每步含页面/动作/300ms 等待/DOM 查询/期望读数），**但步骤本身未执行** |
| `hhs-frontend-v2/src/__tests__/views/ResidueGate.spec.ts` | 四条结构性检查可执行 | ✓ VERIFIED | 20 例全绿，独立实跑 |

### Key Link Verification

| From | To | Via | Status |
|------|----|----|--------|
| `DevicePlatformConfig.vue` | `appError.ts` | `normalizeApiError(error)` → `err.kind === 'forbidden'` 置 `forbidden.value` | WIRED (`:365-373`) |
| `DevicePlatformConfig.vue` | `ErrorState.vue` | `scope="page" variant="forbidden"` + 单 action | WIRED (`:5-12`) |
| `showError.ts` | `appError.ts` | `presentStates` 门控读 `err.kind`，在任何 ElMessage 构造之前 | WIRED (`:144`) |
| `screening/Detail.vue` | `appError.ts` | `normalizeApiError(error).kind === 'notFound'` → 置状态并 `return` | WIRED (`:175-181`) |
| `router/index.ts` | `NotFound.vue` | `/:pathMatch(.*)*` 作为末位子路由 | WIRED (`:141-143`) |
| `useFormServerErrors.ts` | `theme.css` | `:error` → EP 加 `.is-error` → 全局块上色 | WIRED（`FormItemErrorBorder.spec.ts:112` 用真实 element-plus 证明该锚点） |
| 13 表单 | `useFormServerErrors.ts` | `collectInvalidFieldMessages` 用于客户端校验分支 | WIRED（10 个提交 handler 全部 import） |
| `request.ts` | `appError.ts` | `code !== 200` → `reject(normalizeApiError(...))` | WIRED (`:45-52`) |
| `PreventionController.java` | `screening/Detail.vue` 等 | 404 走 `BusinessException(404, ...)` 与 `health.profile_not_found` | WIRED（`appError.spec.ts:272,286` 覆盖） |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ErrorState.vue` (403 页) | `forbidden` | `deviceApi.adminGetAllStatus()` 的 403 | ✓ 真实请求 + 真实分类 | ✓ FLOWING |
| `ErrorState.vue` (404 内容区) | `notFound` | `screeningApi.getReportDetail` 的 `notFound` | ✓ | ✓ FLOWING |
| `FormErrorSummary` | `summaryItems` | `fieldErrors` ← `apply(err)` ← 服务端 `data.fieldErrors` | ✓（后端 `GlobalExceptionHandler` 下发 `errors[]/fieldErrors[]`） | ✓ FLOWING |
| `el-form-item :error` | `fieldErrors[prop]` | 同上 | ✓ | ✓ FLOWING |
| `showError` toast 文案 | `shown` | `resolveMessage(fallbackKey, err.message)`，后端优先 | ✓ | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 前端全量 | `npm run test:run` | 642 passed / 59 files | ✓ PASS |
| 后端全量 | `mvn clean verify -DskipITs` | 845 / 0 / BUILD SUCCESS | ✓ PASS |
| 后端 checkstyle | `checkstyle:check` | 0 violations | ✓ PASS |
| 构建 | `npm run build` | ✓ built 9.88s | ✓ PASS |
| Lint | `npm run lint:check` | 0 errors / 211 warnings | ✓ PASS |
| E2E | `npx playwright test e2e/quick-input.spec.ts` | 7 passed / 1 failed（既有 clamp 断言） | ⚠️ 见 T14 |
| 结构性门禁 | `npx vitest run ResidueGate.spec.ts` | 20 passed | ✓ PASS |
| 错误地基 | 5 spec（appError/showError/ErrorState/FormItemErrorBorder/DevicePlatformConfigForbidden） | 102 passed | ✓ PASS |
| 内联投影 | 4 spec（Goal/Reminders/ScreeningList/UserProfile） | 16 passed | ✓ PASS |
| 完整度非回归 | `profileCompleteness.spec.ts` | 48 passed | ✓ PASS |
| 退化测试扫描 | `it.skip\|describe.skip\|xit\|it.todo\|.only` | 0 命中 | ✓ PASS |
| 债务标记（本阶段 41 个改动源文件） | `TBD\|FIXME\|XXX` 与 `TODO\|HACK\|PLACEHOLDER` | 0 命中 | ✓ PASS |

### Probe Execution

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| — | `find . -name 'probe-*.sh'` | 无探针脚本（本阶段非迁移/工具类阶段） | SKIPPED |

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|--------------|-------------|--------|----------|
| **ERR-03** | 14-02, 14-03, 14-05, 14-06, 14-07, 14-08, 14-13, 14-14 | 后端字段校验错误解析并内联回填到 `el-form-item`，字段名映射中文标签 | ✓ SATISFIED | T1、T2、T3。16 文件接线；46/44 字典；10 提交 handler 的 `apply()` 分支；`FormErrorSummary` 在 13 个表单中均为 `<el-form>` 首子节点（`Monitor.vue` 为 plan 明文许可的等价位置） |
| **ERR-05** | 14-03, 14-05, 14-06, 14-07, 14-08, 14-10, 14-11, 14-12, 14-13, 14-14 | 校验失败或服务端报错后不清空已输入内容、弹窗保持打开 | ✓ SATISFIED | T7。全站 0 处 `resetFields`；catch 体破坏性操作扫描 0 命中；密码表单清空严格在成功 `await` 之后 |
| **ERR-07** | 14-01, 14-02, 14-04, 14-05, 14-09, 14-12, 14-13 | 网络/校验/权限错误的呈现方式与文案可区分 | ✓ SATISFIED | T3–T6。四条通道各自可证：校验内联零 toast / 网络 toast 带重试 / 403 页级块 / 404 内容区块 |
| **ERR-08** | 14-08, 14-09, 14-10, 14-11, 14-12, 14-13 | 清理取消哨兵与 `AbortError` 被误判为失败而弹红提示 | ✓ SATISFIED | T15、T16。9 处手写守卫 + 1 处名称判断 + 4 处静默吞错全部清零；判定收敛到 `appError.isCancel` 单点 |

**Orphaned requirements:** 无。REQUIREMENTS.md §ERR 中映射到 Phase 14 的 4 个 ID（ERR-03/05/07/08）均被至少一个 plan 的 `requirements:` 字段声明，且都出现在某个 SUMMARY 的 `requirements-completed` 里。ERR-01/02/04/06 已在 Phase 11 标 `[x]`，不属本阶段。

### ROADMAP Success Criteria Coverage

| SC | Verdict | Note |
|----|---------|------|
| 1. 全站表单字段错误内联 + 失败类型可区分 | ✓ VERIFIED | 四通道全部可证（T1–T6） |
| 2. 值保留/弹窗不关 + 台账完成 + 裸残留归零 | ✓ VERIFIED | T7/T8/T9；残留口径为 4（规范判定见上） |
| 3. 停后端逐页一提示 + 断网 60 秒不重复 | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | 脚本就绪、步骤未执行（T10/T11） |
| 4. 前端/后端全绿 ≥ 基线 + E2E 成功文案契约 | ✓ VERIFIED | T12–T14；E2E 1 例为既有坏断言 |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | 本阶段改动的 41 个源文件无 `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` | — | 无 |
| `health/Metrics.vue`, `health/Alerts.vue` | — | 无 `FormErrorSummary` | ℹ️ Info | 仅 GET 筛选表单，无服务端字段校验，正确排除 |
| `prevention/Profile.vue:404,444`；`health/Thresholds.vue:259,318` | — | `showError` 未带 `presentStates` | ℹ️ Info | 这 4 处相对阶段起点零改动（`git diff 3734bf1..HEAD` 为空），属 Phase 12/13 交付面。实际不可达：Prevention/Threshold 控制器均 `@PreAuthorize("isAuthenticated()")`，服务层抛 `BusinessException(404/400)`（归 `notFound`/`business`），无 `AUTH_FORBIDDEN`。`notFound` 在 `prevention/Profile.vue:396-400` 被**刻意**处理为 create 模式 |
| `data-input/components/OcrInput.vue:250`；`DeviceSync.vue:487` | — | `code === 200` 之后的 else/else-if | ℹ️ Info | 均为**可达**且非 toast 路径：前者 HTTP 成功但无 data → 填 failed 面板；后者 `code === 400 && '未配置'` → 打开配置向导（ledger 明确记录保留） |
| `views/ai/Chat.vue:600`, `dashboard/Index.vue:269,354,361` 等 | — | 裸 `catch { }` | ℹ️ Info | 均为「保留默认值 / 渲染降级」的非用户动作路径（`// keep null values`、`// fallback: keep zeros`），非失败呈现路径 |

**未引入的 anti-pattern：** 无 `ElNotification` / `ElMessageBox.alert` 等旁路错误通道；无 `dangerouslyUseHTMLString`；`showError.ts:127-128` 明确只把文案作为文本子节点交给 `h`。

### Human Verification Required

1. **停后端逐页保存/加载（14-UAT 步骤 1–3）**
   **Test:** 停 8082 → `/health/metrics` 刷新、`/goals` 或 `/reminders` 填表保存、`/screening/detail/1` 直接导航；每次动作后等 **300ms** 再数 `.el-message`
   **Expected:** 每动作恰好 1 条中文可行动提示；表单页弹窗仍开、已填字段一字未变
   **Why human:** 真实网络失败 + 300ms toast 入场过渡；happy-dom 下 ElMessage 是 mock，产生不了真实节点计数

2. **断网 60 秒轮询（14-UAT 步骤 4）**
   **Test:** `/realtime/monitor` + DevTools Offline，停留 60 秒，按 0/10/20/30/40/50s 六次采样（每次先等 300ms），恢复网络后再采一次
   **Expected:** 七次全部 0
   **Why human:** 依赖 `Monitor.vue:456,513` 的真实心跳间隔与 `showError` 的 2000ms 去重窗口

3. **400/403/404 五通道实跑（14-UAT 步骤 5–9）**
   **Test:** 制造 400（字段留空后提交被后端拒）、403（普通账号访问 `/settings/device-platform`）、404（不存在的报告 id）、非法对比 URL、手输 `/no-such-page`
   **Expected:** 400 → 汇总 + N 内联 + 零 toast 且值保留；403 → 页级块（无 `error-state--page` 缺失）+ 零 toast + 单个「返回首页」；404 → 内容区块两个有序动作 + 零 toast；非法 URL → 页级块不发请求；`/no-such-page` → 页级 404
   **Why human:** 真实 403/404 通道可达性与渲染几何（单元层面已有 26 例断言，缺浏览器观感）

4. **错误描边四族 computed-style 自检（14-UAT 步骤 10–12 + 附录，WINDOWS #7）**
   **Test:** 让 input / select / textarea / daterange 分别进入错误态，读 `getComputedStyle(wrapper).borderColor` 与 `.boxShadow`；再聚焦、再失焦重复读
   **Expected:** 边框 `--hhs-color-error-text`；textarea shadow 含 `inset`；三族聚焦时含 3px `--hhs-color-error-tint` 光晕且边框仍红；失焦后光晕消失、红边保留
   **Why human:** happy-dom 的 `getComputedStyle` 返回空串

5. **320px/640px 兜底与两条非回归（14-UAT 步骤 13 + N-1/N-2）**
   **Test:** `/no-such-page` 与 `/screening/detail/999999` 在 320/640 下读 `Math.round(desc.scrollHeight/lineHeight)` 与 actions 的 `offsetTop`；无档案账号开 `/user/profile`；`/health/thresholds` 提交已存在指标
   **Expected:** 说明文字换行不截断、两动作堆叠不溢出；N-1 为 create 模式正常渲染且零错误 toast；N-2 恰好 1 条 toast 且不渲染死按钮
   **Why human:** 真实排版读数；N-1 需要特定账号；这是 14-13 自定「无证据不算通过」的兜底

### Gaps Summary

无 blocking gap。24 条 truth 中 21 条由代码与本次实跑的门禁证实；3 条是 **present-but-behavior-unverified**，全部落在同一个根因上：**本阶段交付了可执行的浏览器脚本（`14-UAT.md`，13 步 + 2 条非回归），但脚本的步骤没有执行过**，`14-13-SUMMARY.md:392` 自己就写明「pending human execution」。

这 3 条恰好覆盖 ROADMAP SC3 的两半（停后端逐页一提示、断网 60 秒不重复）与 UI-SPEC 的 4 条 `backstop`。它们的实现全部就位且可读：`showError` 的去重闩锁（2000ms）、3 条上限淘汰、`presentStates` 的零 toast 早退、`App.vue:18,23` 的 `{ silent: true }` 轮询位点、`ErrorState` 的 `flex-wrap` 与 639px 媒体查询。缺的不是代码，是读数。

三个需要审阅者注意的判断点：

1. **SC2c 的「归零」是 4，不是 0。** 依据 CONTEXT D-01 在规划期定下的口径（用户可见失败路径归零）+ UI-SPEC Contract 6.2 的 `保持` 桶，并由 `ResidueGate.spec.ts` 的**双向**断言（清单外 0 + 每个条目仍在其函数内恰好一次）钉住。意图成立，字面不成立。若要字面归零需重构两个上传控件的预检查，属范围外。
2. **E2E 是 7/8，不是 8/8。** 失败例断言一个从未实现的 clamp（value 的 `el-input-number` 无 `:max`），spec 与初始提交 `13dd26b` 字节相同（blob hash 一致），`14-07` 的 diff 未触碰 `:max`。SC4 只要求「成功文案契约仍通过」，两条成功文案断言均通过。已记入 WINDOWS #8 为既有缺陷。
3. **`Monitor.vue` 的汇总块不是 `<el-form>` 首子节点。** 该表单是 `:inline="true"` 的 flex 行，全宽块塞进去会被压成单元格。14-08 的 plan 明文授权了这一退路，ledger 与 SUMMARY 均记录了偏离、理由与回归路径。判定为符合 Contract 4.3 的**意图**（位于所有字段之上、同一组件），不是豁免。

### Decision Coverage

决策覆盖率门禁（非阻塞）：`{ skipped: false, blocking: false, total: 6, honored: 6, not_honored: [] }` —— 「All trackable CONTEXT.md decisions are honored by shipped artifacts.」

### Deferred Items

本阶段是 v1.3 的最后一阶段（`ROADMAP.md:341` Depends on Phase 12, Phase 13；无 Phase 15）。上述 3 条 behavior-unverified 项**没有后续阶段可承接**，因此不做 deferred 过滤 —— 它们必须在本阶段以人工实跑关闭。

---

_Verified: 2026-11-10T16:11:12Z_
_Verifier: Claude (gsd-verifier)_

---

## UAT Outcome (2026-11-11)

`/gsd:verify-work 14` executed the 13-step browser script plus 3 supplementary checks against the live stack (backend 8082, frontend 5173, MySQL/Redis healthy, account `admin`).

**Result: 15 pass / 0 functional issues / 1 blocked (environment).**

| # | Check | Result |
|---|-------|--------|
| 1 | Backend down + list refresh → exactly 1 toast | pass (count=1, retry action present) |
| 2 | Backend down + form save → 1 toast, dialog open, values kept | pass (dialogOpen=true, thresholds 120/100/50/40 intact) |
| 3 | Backend down + load → content-area state block, zero toast | pass (`无法加载阈值列表` + 重试) |
| 4 | 60s polling with backend down → no toast stacking | pass (6 samples over 60s, all 0) |
| 5 | Server field rejection → summary + inline + zero toast + values kept | pass (2 inline + `请修正以下 2 项` + 150 preserved) |
| 6 | 403 page-scope block, single 返回首页 | pass (`error-state--page`, zero toast) |
| 7 | 404 content block, two actions | pass (`资源不存在` + 返回列表/重新加载该记录) |
| 8 | Unknown URL → page-scope 404, not blank | pass (`页面不存在`, blank=false) |
| 9 | Cancel delete confirm → zero toast | pass (rows unchanged) |
| 10 | Navigate away cancel in-flight → zero toast | pass |
| 11 | Required-empty submit → red **border**, not just red text | pass (`rgb(185,28,28)` on all four edges + screenshot) |
| 12 | Errored + focused → border still red AND visible focus halo | pass (env-sensitivity documented; rule verified in CSSOM) |
| 13 | 320px / 640px layout | pass (no overflow, zero button overlap) |
| N-1 | No-profile user → create mode works | pass (0%, edit button usable, no route gate) |
| N-2 | Duplicate-metric recovery | pass (1 toast + 打开编辑) |
| 附-14-02 | Four computed-style self-checks | ① pass ② blocked (no required textarea exists) ③ pass ④ n/a |

### Environment findings (non-defects, recorded for future verifiers)

1. **Non-foreground window suppresses `:focus-within`** — when `document.hasFocus()` is false, Chrome computes the `:focus-within` box-shadow as `rgba(0,0,0,0) 0px 0px 0px 0px`. Step 12's first pass misread this as a defect. A genuine improvement came out of the investigation: `theme.css` rule ③ now also selects `:focus-within`, so the halo no longer depends on EP's `.is-focus` class timing.
2. **CSS transition intermediate state** — `border-color` transitions over `--dur-fast`; reading immediately after an action yields the pre-transition grey. Wait ≥150 ms.
3. **Vite CSS cache** — `theme.css` edits are not hot-reloaded into an already-injected stylesheet; restart `npm run dev` to see new rules (after restart, `is-error` rules went from 1 to 7 in the served CSS).

### Action taken from UAT

- `hhs-frontend-v2/src/assets/styles/theme.css` — rule ③ extended with `:focus-within` for both `.el-input__wrapper` and `.el-select__wrapper` (commit `8b8d0f2`-class change; documented in-file with the UAT date and rationale).

### Final gates after the UAT-driven CSS change

- Frontend suite: **642 passed / 59 files**
- Frontend build: **✓ built (vue-tsc clean)**
- Backend: unchanged by this phase's UAT (845 tests remain green)
