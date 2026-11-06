---
phase: 11-error-foundation
plan: 07
subsystem: frontend-error-presentation
tags: [error-handling, form-validation, gov-uk, store-contract, recovery-action, d-08, silent]
status: complete
dependency_graph:
  requires: [11-04, 11-05, 11-06]
  provides:
    - 全页表单迁移模板（Profile.vue：reset 前置 → showError → apply + focusSummary）
    - store 副作用迁移模板（push.ts：catch 内单次 showError + 布尔返回）
    - 调用方契约模板（PushSettings.vue：const ok = await store.xxx() + if (!ok) 早退）
    - RESOURCE_ALREADY_EXISTS 恢复动作的生产调用点（Thresholds.vue）
    - preventionApi.getProfile(config?)（silent 透传）
  affects: [12, 13, 14]
tech-stack:
  added: []
  patterns:
    - GOV.UK 双呈现：FormErrorSummary 首个子节点 + el-form-item :error 内联，零 toast
    - store 层单一提示出口（showError）与布尔返回值契约
    - 后端错误码驱动的恢复动作（调用点只传 onClick，标签由 ERROR_CODE_ACTIONS 补全）
    - 缺档案首载 silent + 不调 showError（Pitfall 6）
key-files:
  created:
    - hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts
    - hhs-frontend-v2/src/__tests__/views/ErrorLayerSample.spec.ts
  modified:
    - hhs-frontend-v2/src/views/prevention/Profile.vue
    - hhs-frontend-v2/src/api/prevention.ts
    - hhs-frontend-v2/src/stores/push.ts
    - hhs-frontend-v2/src/views/user/PushSettings.vue
    - hhs-frontend-v2/src/views/health/Thresholds.vue
decisions:
  - "testChannel 的 data.success===false 分支可到达（HTTP 200 + 业务失败），因此不以删除收尾，而是用 ApiError('business') 走同一个 showError 出口"
  - "profile 与 push 的 catch 都取 'profile.saveFailed' / 'server.unavailable'，未新增字典 key（11-04 冻结契约）"
  - "ErrorLayerSample 的 VNode 闩锁跨用例存活，测试用单调前进的 Date.now spy 而非用不同文案规避"
  - "分支 B（列表无对应行）用不同后端文案，同时验证 action 无标签时退化为字符串分支"
metrics:
  duration: 约 12 分钟
  completed: 2026-11-06
  tasks: 3
  commits: 3
  plan_head_before: 0e10e36cc1d95768c66ea629d4f7e6bbf700a623
  plan_head_after: e8eeb630bf604ffaa22f8cb692f6b0235f151e5d
actuals:
  tokens: 9932
  tasks: 3
  commits: 3
requirements-completed: [ERR-01, ERR-02, ERR-04, ERR-06]
coverage:
  - id: D1
    description: "健康档案保存的字段校验失败：内联红字 + 顶部汇总，零 toast，用户输入保留"
    requirement: ERR-01
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts#A"
        status: pass
    human_judgment: false
  - id: D2
    description: "健康档案首次加载缺档案（业务 404）不产生红色提示，进入创建模式"
    requirement: ERR-01
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/ProfileErrorSample.spec.ts#B"
        status: pass
    human_judgment: false
  - id: D3
    description: "push 保存失败：恰好一条错误提示、返回 false；成功：一条成功提示、零错误提示"
    requirement: ERR-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/ErrorLayerSample.spec.ts#1/#2"
        status: pass
    human_judgment: false
  - id: D4
    description: "取消操作零提示，且 store 仍返回 false"
    requirement: ERR-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/ErrorLayerSample.spec.ts#3"
        status: pass
    human_judgment: false
  - id: D5
    description: "RESOURCE_ALREADY_EXISTS：按钮文本由 ERROR_CODE_ACTIONS 补为『打开编辑』，点击打开已存在阈值编辑态；无对应行时不渲染按钮"
    requirement: ERR-06
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/views/ErrorLayerSample.spec.ts#6/#7/#8"
        status: pass
    human_judgment: false
---

# Phase 11 Plan 07: 三个真实调用点证明错误地基成立 Summary

三个样本把前面四层机制真正接上生产代码：全页表单的双呈现、store 层的单一提示出口、以及后端错误码驱动的恢复动作。Phase 12/13/14 的 105 处迁移直接照抄这三种形状。

## What Was Built

### Task 1 — 全页表单双呈现（type="tracer"）

**`src/api/prevention.ts`**

- `getProfile(config?: AxiosRequestConfig)`，配置透传至 `request.get`。顶部新增 `import type { AxiosRequestConfig } from 'axios'`；其余 10 个方法未动。

**`src/views/prevention/Profile.vue`**

- `<el-form>` 首个子元素插入 `<FormErrorSummary ref="summaryRef" :summary-items="summaryItems" @focus-field="focusField" />`（在"性别"行之前）。
- 8 个已有 `prop` 的 `el-form-item` 各补 `data-field="<prop>"` 与 `:error="fieldErrors['<prop>']"`：`gender` / `birthDate` / `heightCm` / `weightKg` / `bloodType` / `allergyHistory` / `familyHistory` / `lifestyleHabits`。BMI 项按计划保持无 `prop`、不加 `:error`。
- 输入事件追加 `clearField(...)`：`el-radio-group` / `el-select` / `el-date-picker` / `el-input-number` 用 `@change`，三个文本域用 `@input`。原控件没有同名处理器，因此是新增而非覆盖。
- `handleSubmit`：`if (!formRef.value) return` 之后、`validate` 之前 `reset()`；catch 改为 `const err = showError(error, 'profile.saveFailed')`，仅当 `err.kind === 'validation'` 时 `apply(err)` → `await nextTick()` → `summaryRef.value?.focusSummary()`。失败分支不清表单模型、不重置 `editing`（T-11-22）。
- `fetchProfile`：`getProfile({ silent: true })`；catch 只 `editing.value = true`，加注释说明"档案不存在是正常状态，silent 已抑制提示"。
- 文件顶部 import 区后加 `// Phase 14: 其余 toast 调用点待迁移（ERR-03/05/07/08）`。改造后本文件 `ElMessage.error` 为 0（基线 1）。`ElMessage.success` 两条与 `ElMessage` import 保持不变。

**`src/__tests__/views/ProfileErrorSample.spec.ts`（新建，3 用例）**

- 用例 A（核心）：`updateProfile` reject 一个 400 形状 Result（`fieldErrors` 含 `heightCm` / `weightKg`）后断言四通道总计 0 次 toast、`.form-error-summary` 存在、标题含 `请修正以下 2 项填写问题`、两条 `li` 分别含 `身高：…` 与 `体重：…`、`.el-form-item__error` 恰好 2 条且文案对应、`vm.form.heightCm/weightKg` 保持提交前的值。
- 用例 B：`getProfile` reject 一个 404 形状响应 → `editing === true` 且 toast 0 次。
- 用例 C：`updateProfile` resolve → `ElMessage.success` 1 次、总数 1、无汇总、无内联错误。

### Task 2 — store 层单一出口与调用方契约

**`src/stores/push.ts`**

- `saveConfig`：删除不可达的 `res.code !== 200` else（拦截器先 reject），成功路径只保留 `ElMessage.success('配置已保存')` + `return true`；catch → `showError(error, 'profile.saveFailed')` + `return false`。
- `testChannel`：`data.success === false` 是**可到达**的分支（HTTP 200 + 业务失败），因此保留该分支但改走错误出口 —— `showError(new ApiError(res.message || '', 'business'), 'server.unavailable')` + `return false`；catch → `showError(error, 'server.unavailable')` + `return false`。
- 顶部新增 `import { showError }` 与 `import { ApiError }`。全文件 `ElMessage.error` 归零（基线 4）。

**`src/views/user/PushSettings.vue`**

- `handleToggle`：`const ok = await pushStore.saveConfig(...)`；`if (!ok) { config.enabled = !config.enabled; return }`（回滚开关，不追加第二条提示）；只有 `ok` 为真才发 success / info。死 catch 删除。
- `handleSaveConfig`：同款 `const ok = ...` + `if (!ok) return`，只有成功才 `ElMessage.success('配置已保存')`。`****` 遮罩的本地前置 warning 早退保留。
- 两个函数的 `try/catch` 删除（store 从不 throw）。

**`src/__tests__/views/ErrorLayerSample.spec.ts`（新建，本任务先落 5 用例）**

- 500 拒绝 → `false` + 错误类 toast 恰好 1 + `ElMessage.success` 0 + 函数式 `ElMessage.error` 0。
- 200 resolve → `true` + success 1 + 错误类 0。
- reject `'cancel'` → `false` + 任意 toast 0（D-12）。
- 连续两次网络失败 → 错误 toast `<= 1`（2000ms VNode 闩锁）。
- 读源码断言 `push.ts` 代码行中不含 `ElMessage.error`。

### Task 3 — RESOURCE_ALREADY_EXISTS 恢复动作接线

**`src/views/health/Thresholds.vue`**

- 新增 `openExistingThreshold(row)`：关闭对话框后 `handleEdit(row)`。
- `handleSubmit` catch 替换 `ElMessage.error('操作失败')` 为：
  ```ts
  const existing = thresholds.value.find(t => t.metricKey === form.metricKey)
  showError(
    error,
    'threshold.duplicate',
    existing ? { action: { onClick: () => openExistingThreshold(existing) } } : undefined
  )
  ```
- **只传 `onClick`、不传 `label`** —— 标签由 `showError` 的 `ERROR_CODE_ACTIONS['resource.exists']` 自动补成「打开编辑」。页面**不写任何错误码分支**（`resource.exists` 在文件中出现 0 次），判定完全收在 `showError` 内。
- `existing` 未命中时不传 action，退化为普通字符串 toast（不渲染死按钮，T-11-24）。
- 失败分支保持对话框打开；成功分支的关闭逻辑未动。
- `handleDelete` / `fetchThresholds` 两处 `ElMessage.error` 保留（Phase 14），文件加 `// Phase 14: 其余 toast 调用点待迁移`。

**`src/__tests__/views/ErrorLayerSample.spec.ts` 追加 3 用例**

- 用例 6：列表含 `glucose` → 一条对象式错误 toast；遍历 VNode 找到 `.error-toast__action`，按钮文本与 `aria-label` 都是「打开编辑」。
- 用例 7：点击该按钮 → `editingId === 3` 且 `dialogVisible === true`。
- 用例 8：列表为空 → toast `message` 为字符串且含后端文案，不渲染按钮。
- 整个 Thresholds describe 的 `afterEach` 断言函数式 `ElMessage.error` 全程 0 次。

## Verification

| Gate | Result |
|------|--------|
| `grep -c "useFormServerErrors"` Profile.vue | 2（≥2） |
| `grep -c "FormErrorSummary"` Profile.vue | 3（≥2） |
| `grep -c "showError(error, 'profile.saveFailed')"` Profile.vue | 1（≥1） |
| `grep -c ':error="fieldErrors'` Profile.vue | 8（≥4） |
| `grep -c "clearField("` Profile.vue | 8（≥4） |
| `grep -c "silent: true"` Profile.vue | 1（≥1） |
| `grep -c "reset()"` Profile.vue | 1（≥1） |
| `grep -c "ElMessage.error"` Profile.vue | 0（基线 1） |
| `grep -c "ElMessage.error"` push.ts（代码行） | 0（基线 4） |
| `grep -c "showError"` push.ts | 4（≥2） |
| `grep -c "ElMessage.error"` PushSettings.vue | 0（基线 2） |
| `grep -c "if (!ok)"` PushSettings.vue | 2（≥2） |
| `grep -c "const ok = await pushStore.saveConfig"` PushSettings.vue | 2（≥2） |
| `grep -c "showError"` Thresholds.vue | 3（≥2） |
| `grep -c "threshold.duplicate"` Thresholds.vue | 1（=1） |
| `grep -c "resource.exists"` Thresholds.vue | 0（=0） |
| `grep -c "action: { onClick"` Thresholds.vue | 1（=1，无 label） |
| `grep -c "ElMessage.error"` Thresholds.vue | 2（=2，基线 3） |
| `test:run -- ProfileErrorSample.spec.ts` | 3 passed（≥3） |
| `test:run -- ErrorLayerSample.spec.ts` | 8 passed（≥7） |
| `test:run` 全量 | 23 files / **207 tests passed**（基线 21/196，+11，未回归） |
| `npm run build`（vue-tsc -b + vite） | 通过，无 TS 错误 |
| eslint 本计划触碰文件 | 0 errors / 4 warnings（3 处测试内 `any`、1 处既有未用 catch 绑定） |
| `.planning/STATE.md` / `.planning/ROADMAP.md` | 未修改（编排器负责） |

## Tracer Feedback Gate

Task 1 为 `type="tracer"`。自动模式判定：`.planning/config.json` 中 `workflow.auto_advance: true`、`mode: yolo`。

- 扩展任务前重跑端到端 `<verify>`：`ProfileErrorSample.spec.ts` 3/3 + 全量 199 tests 通过、build 通过 → 继续扩展。
- 网关未发现基础层缺陷：`useFormServerErrors` 的 `apply`/`summaryItems`/`focusField`、`FormErrorSummary` 的 `focusSummary`、`showError` 的 validation 零 toast 分支，均由 11-04/11-05 的已验证实现直接支撑，未做补丁式修补。

## Deviations from Plan

### 计划内调整

**1. `testChannel` 的 `else` 分支保留而非删除**

- **Found during:** Task 2
- **Issue:** 计划要求"else 分支（`res.code !== 200`）不可达，删除"。但 `testChannel` 的 else 条件是 `res.code === 200 && res.data.success`；`res.data.success === false` 时 HTTP 仍然 200、信封 code 仍是 200，拦截器不会 reject —— 该分支**可到达**，直接删除会把"通道测试失败"变成静默成功。
- **Fix:** 删除 `saveConfig` 里真正不可达的 `else`（那里只判 `res.code`），`testChannel` 保留该分支但改为 `showError(new ApiError(res.message || '', 'business'), 'server.unavailable')` + `return false`，接入同一出口。
- **Files modified:** `hhs-frontend-v2/src/stores/push.ts`
- **Commit:** `a4972fc`

**2. 恢复动作抽成具名 `openExistingThreshold()`**

- **Found during:** Task 3
- **Issue:** 内联箭头函数写成多行后 Prettier 会展开，导致验收 grep `action: { onClick` 命中 0。
- **Fix:** 抽成 `const openExistingThreshold = (row: UserThreshold) => { dialogVisible.value = false; handleEdit(row) }`，调用点保持单行 `existing ? { action: { onClick: () => openExistingThreshold(existing) } } : undefined`。语义不变（用例 6/7 通过）。
- **Files modified:** `hhs-frontend-v2/src/views/health/Thresholds.vue`
- **Commit:** `e8eeb63`

**3. VNode 去重闩锁在测试间的串味处理**

- **Found during:** Task 3 用例 7
- **Issue:** `showError` 的 2000ms 去重闩锁是模块级 Map，跨用例存活。用例 7 与用例 6 使用同一后端文案，在真实时钟下会被判为重复而完全吞掉第二条 toast，断言取不到 `ElMessageMock.mock.calls[0]`。
- **Fix:** 在 Thresholds describe 的 `beforeEach` 中 `vi.spyOn(Date, 'now')` 返回单调前进 5 秒的时钟（不随用例回拨），`afterEach` `vi.restoreAllMocks()`。用例 8 另外用不同文案，同时验证"action 无标签 → 字符串分支"。
- **Files modified:** `hhs-frontend-v2/src/__tests__/views/ErrorLayerSample.spec.ts`
- **Commit:** `e8eeb63`

无 Rule 1 / Rule 4 类偏差；未新增任何 npm 依赖（T-11-SC 保持 mitigated）；未修改 `vitest.config.ts` / `setup.ts`。

## Auth Gates

无。本计划不涉及认证凭据、外部服务或手工登录步骤。

## Known Stubs

无。`Thresholds.vue` 剩余两处 `ElMessage.error`（`handleDelete` / `fetchThresholds`）与 `PushSettings.vue` 的其余 toast 均属 Phase 14 显式划定的迁移范围（已在文件顶部标注检索锚点），不是桩实现。`profile.duplicate` 一类状态没有任何前端硬编码兜底文案。

## Threat Surface Scan

计划 `<threat_model>` 已覆盖本计划引入的全部表面，未发现新增信任边界：

- **T-11-21（Tampering / false success, high → mitigate）**：`PushSettings` 的两个写操作都改为读取 `saveConfig` 的布尔返回，失败时回滚开关 / 早退；spec 断言失败路径下 `ElMessage.success` 为 0、成功路径下错误 toast 为 0。
- **T-11-22（Repudiation, medium → mitigate）**：`Profile.vue` 的 catch 分支不清空 `form`、不重置 `editing`；`reset()` 只清 `fieldErrors` 映射。spec A 断言 `form.heightCm/weightKg` 保持提交前值。
- **T-11-23（Information Disclosure, low → accept）**：档案 404 走 `silent: true`，catch 内不调用 `showError`、不输出 `console`；spec B 断言零 toast。
- **T-11-24（Tampering / dead action, medium → mitigate）**：`action` 只在 `thresholds` 中确实找到 `metricKey` 时提供；spec 8 断言列表为空时 `message` 为字符串、无按钮。toast 文案取后端中文 message（D-05），不由前端码表覆盖。
- **T-11-SC（Tampering, high → mitigate）**：零新增依赖，仅用已安装的 `element-plus`、`@vue/test-utils`。

新增可见面为 `data-field` 约定属性与 `openExistingThreshold` 这一内部函数；均不引入新的信任边界，也没有新增网络端点。

## Self-Check

- 7 个目标文件全部存在（FOUND）：Profile.vue、api/prevention.ts、stores/push.ts、PushSettings.vue、Thresholds.vue、ProfileErrorSample.spec.ts、ErrorLayerSample.spec.ts。
- 三个任务提交均为 HEAD 祖先：`2496fd0`（Task 1）、`a4972fc`（Task 2）、`e8eeb63`（Task 3）。
- `npm run test:run`：23 files / 207 tests passed（基线 21/196，未回归）。
- `npm run build`：通过（vue-tsc 无错误）。
- `.planning/STATE.md`、`.planning/ROADMAP.md` 未修改（由编排器负责）。

## Self-Check: PASSED