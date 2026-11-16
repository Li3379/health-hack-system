---
phase: 11-error-foundation
plan: 03
subsystem: frontend-error-infrastructure
tags: [error-handling, axios, interceptors, auth-latch, api-types]
dependency_graph:
  requires: [11-01]
  provides:
    - ApiError / normalizeApiError / isCancel / ErrorKind
    - AxiosRequestConfig.silent 模块增强
    - 401 闩锁（isHandling401 / markHandling401 / resetAuthLatch）
    - ApiResponse.errorCode / fieldErrors
  affects: [11-04, 11-05, 11-06, 11-07, 14]
tech-stack:
  added: []
  patterns:
    - Translate-Don't-Display（拦截器只翻译不展示）
    - 幂等归一化（ApiError 直通）
    - 模块级布尔闩锁 + 零依赖模块解环
key-files:
  created:
    - hhs-frontend-v2/src/utils/appError.ts
    - hhs-frontend-v2/src/utils/authSession.ts
    - hhs-frontend-v2/src/types/axios.d.ts
    - hhs-frontend-v2/src/__tests__/utils/appError.spec.ts
    - hhs-frontend-v2/src/__tests__/utils/request.spec.ts
    - hhs-frontend-v2/src/__tests__/stores/auth.spec.ts
  modified:
    - hhs-frontend-v2/src/types/api.ts
    - hhs-frontend-v2/src/utils/request.ts
    - hhs-frontend-v2/src/stores/auth.ts
decisions:
  - "normalizeApiError 第 0 步对 ApiError 幂等直通，分类顺序锁死：cancel → validation → 401 → network → 真实 5xx → 信封业务 → 兜底"
  - "fulfilled 分支合成假 response 且 status 固定为 200，使信封 code=500 不被 5xx 分支截胡"
  - "401 闩锁状态放在零依赖模块 utils/authSession.ts，由 authStore.login() 复位"
  - "rejected 分支不声明 silent 局部变量：拦截器已不展示，声明会触发 TS6133"
metrics:
  duration: 约 12 分钟
  completed: 2026-11-06
  tasks: 2
  commits: 2
  plan_head_before: 451b146c7dbb5d3ee9e1b5eb9813a3c8b54f5b7b
  plan_head_after: 34dbf03
actuals:
  tokens: 8000
  tasks: 2
  commits: 2
status: complete
---

# Phase 11 Plan 03: 前端唯一错误模型与「只翻译不展示」拦截器 Summary

前端建立起单一错误模型：`normalizeApiError` 把校验 400、信封业务失败、真实 5xx、断网、401 与取消六种形态翻译为同一个 `ApiError`，`request.ts` 的两个拦截器分支统一 reject 该类型化错误且零 toast、零 console 噪音；401 闩锁落在零依赖模块并由 `authStore.login()` 复位。

## What Was Built

### Task 1 — 端到端一次失败：校验 400 与业务 200 都归一化为 ApiError

- **`src/utils/appError.ts`（新建，零 import）**：`ErrorKind`（7 值含 `cancel`）、`ApiError` 类（`message` / `kind` / `status` / `code` / `fieldErrors` / `formField` / `retryable`）、`CANCEL_SENTINELS`、`isCancel`、`normalizeApiError`。
- **分类顺序（契约）**：`ApiError` 幂等直通 → 取消 → 字段校验 → 401 → 断网 → 真实 HTTP 5xx → 信封业务失败（HTTP 200 + 信封 `code !== 200`）→ 兜底。
  - 第 0 步 `if (raw instanceof ApiError) return raw` 必须是第一分支：拦截器抛出的就是 `ApiError`，缺这道直通会让 `!err?.response` 把每条错误改判成 `network`，导致校验内联永不出现、`resource.exists` 丢失、取消弹出本应忽略的提示。
  - 第 6 步只读信封 `data.code`（`typeof === 'number' && !== 200`），不读 `status`、不看数值大小。后端 `BusinessException` 经 `parseCodeToInt` 回退后信封 code 恒为 500，仍判为 `business` 并原样透出后端中文 message。
  - 代码注释记录了刻意语义边界：通用 500 经 HTTP-200 信封下发归为 `business`（文案仍为后端中文，只是不带重试按钮），`server` 保留给真实传输层 5xx。
- **`src/types/api.ts`（改造）**：新增 `FieldError` 接口；`ApiResponse` 追加**可选** `errorCode?` / `fieldErrors?`，既有三字段与全部调用点不受影响。
- **`src/types/axios.d.ts`（新建）**：`declare module 'axios'` 增加 `AxiosRequestConfig.silent?: boolean`；注释写明 D-09（调用点显式标记，无 URL 模式表）与 D-10（只抑制展示，不改变 reject）。
- **`src/utils/request.ts`（重写两个拦截器）**：
  - 删除 `ElMessage` 导入、全部 8 处 toast 与 `console.error`；文件内 `ElMessage` 引用数 0（含注释）。
  - 请求拦截器新增 `Accept-Language: zh-CN`（D-14），保留 token 注入与 FormData 的 Content-Type 删除。
  - fulfilled：`code === 200` 原样返回；否则 `reject(normalizeApiError({ response: { status: 200, data: res } }))` —— 假 response 的 `status` 固定为真实 HTTP 状态 200。
  - rejected：`normalizeApiError(error)` → cancel 直接 reject → `kind === 'auth' && !isHandling401()` 时 `markHandling401()` + `useAuthStore().logout()` → 无条件 reject。分支内用注释说明为何不声明 `silent` 局部变量。
  - 从 `@/utils/authSession` 导入闩锁函数，并 `export { resetAuthLatch } from '@/utils/authSession'` 保持既有对外契约。
- **`src/utils/authSession.ts`（新建，零 import）**：`handling401` 布尔 + `isHandling401` / `markHandling401` / `resetAuthLatch`。零依赖是解环前提（`request.ts` 已 import `@/stores/auth`）。
- **`src/__tests__/utils/appError.spec.ts`（新建，27 用例）**：逐 kind（6 个）的 `toBe` 幂等直通、取消五形态、校验/业务/真实 5xx/断网/401 各通道、字符串信封码守卫、同字段 `；` 合并、非 business 分支文案语言门禁。

### Task 2 — 401 闩锁复位接线

- **`src/stores/auth.ts`（改造）**：顶部引入 `resetAuthLatch`；`login()` 在写入 token/user 之后、`ElMessage.success` / `router.push` 之前调用 `resetAuthLatch()`。`logout()` / `register()` 行为未改动（禁止项）。
- **`src/__tests__/utils/request.spec.ts`（新建，8 用例）**：直接取 `service.interceptors.response.handlers[0]` 的 `fulfilled` / `rejected`（axios 1.16 稳定内部结构，未发真实网络请求、未新增依赖）。覆盖：200 直通、信封 404 → business、信封 500 → business（非 server）、`silent: true` 仍 reject、`'cancel'` 零 auth 副作用、并发 401 恰好 1 次 logout、复位后再 401 计为 2、请求拦截器发送 `Accept-Language` 并注入 Bearer。`afterEach` 断言 `ElMessage.error` 调用 0 次。
- **`src/__tests__/stores/auth.spec.ts`（新建，2 用例）**：端到端复位契约 —— `markHandling401()` → `login()` 成功 → `isHandling401() === false`；登录 reject → 闩锁仍为 `true` 且未跳转。

## Verification

| Gate | Result |
|------|--------|
| `grep -c ElMessage request.ts` | 0 |
| `grep -c console.error request.ts` | 0 |
| `grep -c Accept-Language request.ts` | 1 |
| `grep -c normalizeApiError request.ts` | 3（≥2，含注释） |
| `grep -c "^import" appError.ts` | 0 |
| `grep -c "fieldErrors?" api.ts` | 1 |
| `grep -c "silent?: boolean" axios.d.ts` | 1 |
| `grep -c "instanceof ApiError" appError.ts` | 1 |
| `grep -cE "^\s*const silent" request.ts` | 0 |
| appError 幂等 `toBe` 断言覆盖 6 kind + cancel 断言 | 通过（52 处 `toBe(`） |
| `grep -c resetAuthLatch authSession.ts` | 1 |
| `grep -c resetAuthLatch request.ts` | 2（导入 + 再导出） |
| `grep -c "resetAuthLatch()" stores/auth.ts` | 1 |
| `grep -c "^import" authSession.ts` | 0 |
| `toHaveBeenCalledTimes(1)` in request.spec.ts | 2 |
| `test:run -- appError.spec.ts` | 27 passed（≥14） |
| `test:run -- request.spec.ts auth.spec.ts` | 10 passed（≥9） |
| `test:run` 全量 | 16 files / 127 tests passed（基线 90，未回归） |
| `npm run build`（`vue-tsc -b` + vite） | 通过，无 TS6133 |
| `eslint` 触碰文件 | 0 errors（仅既有 `no-explicit-any` 风格 warning） |

## Tracer Feedback Gate

Task 1 为 `type="tracer"`。自动模式下在扩展任务（Task 2）之前按序执行：

- 自动模式判定：`.planning/config.json` 中 `workflow.auto_advance: true`。
- 端到端重跑 `<verify>`：`appError.spec.ts` 27/27 通过 → 继续扩展。
- 网关未发现基础层缺陷，Task 2 得以在其上扩展。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] request.ts 注释中含 `ElMessage` 字面量，违反零引用门禁**
- **Found during:** Task 1 acceptance 检查（`grep -c "ElMessage"` 返回 1 而非 0）
- **Issue:** 我写的开头注释「本文件对 ElMessage 的引用数为 0」自身包含该字面量，使门禁失败。
- **Fix:** 注释改为「本文件不引用任何 UI 组件」。
- **Files modified:** `hhs-frontend-v2/src/utils/request.ts`
- **Commit:** 10a4be6

**2. [Rule 1 - Bug] appError.spec.ts 的兜底用例输入 `{}` 被正确判为 network**
- **Found during:** Task 1 首次运行 spec（1 failed）
- **Issue:** 计划要求兜底分支可达，但用空对象 `{}` 测试时命中第 4 步 `!err?.response`（正确行为），用例断言 `server` 失败。
- **Fix:** 改用 `axiosError(400, {})`（有 response、无可用字段）以真正触达第 7 步兜底，并额外断言 `status === 400`。
- **Files modified:** `hhs-frontend-v2/src/__tests__/utils/appError.spec.ts`
- **Commit:** 10a4be6

### 计划内调整

**3. 测试内 mock token 通过 `vi.hoisted` 常量注入**
- request.spec.ts 原先在 `vi.mock` 工厂里直接使用字符串字面量，导致请求拦截器用例无法与 mock 值对齐。改为 `vi.hoisted` 导出 `MOCK_TOKEN`，消除硬编码重复、保持 mock 与断言同源。

无 Rule 3 / Rule 4 类偏差；未新增任何 npm 依赖（T-11-SC 保持 mitigated）。

## Auth Gates

无。本计划不涉及认证凭据、外部服务或手工登录步骤。

## Known Stubs

无。`silent` 定义的落实位置按计划留给 11-06（API 层透传）与 11-04（呈现层），不属于本计划的桩，而是分阶段交付的自然边界，已在本 SUMMARY 与 plan 中显式记录。

## Threat Surface Scan

计划 `<threat_model>` 已覆盖本计划引入的全部新表面，未发现新增加密面、端点或信任边界：

- **T-11-06（Information Disclosure, high → mitigate）**：`normalizeApiError` 只读取 `message` / `errorCode` / `fieldErrors` 三个白名单字段，不暴露 `detail`、堆栈或原始 Axios 对象。已由 `appError.spec.ts` 的字段断言固定。
- **T-11-07（DoS, medium → mitigate）**：模块级布尔闩锁使并发 401 只触发一次 `logout()`；`login()` 成功后复位。`request.spec.ts` 断言 `toHaveBeenCalledTimes(1)` 与复位后为 2，`auth.spec.ts` 断言失败登录不复位。
- **T-11-08（Repudiation, medium → mitigate）**：`silent` 只抑制展示。`request.spec.ts` 的 `silent: true` 用例断言仍是 reject 且 `kind === 'server'`，锁定不会被将来改成 resolve。
- **T-11-09（Tampering, low → accept）**：`silent` 为编译期类型，无运行时攻击面。
- **T-11-SC（Tampering, high → mitigate）**：未新增 npm 依赖，只用已安装的 axios 1.16.1 与 vitest 4.1.7。

## Self-Check

- 9 个目标文件全部存在（FOUND）。
- 两个任务提交均为 HEAD 祖先：`10a4be6`、`34dbf03`。
- `npm run test:run`：16 files / 127 tests passed。
- `npm run build`：通过（vue-tsc 无错误）。

## Self-Check: PASSED