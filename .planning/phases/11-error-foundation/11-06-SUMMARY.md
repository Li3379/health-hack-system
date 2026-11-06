---
phase: 11-error-foundation
plan: 06
subsystem: frontend-error-presentation
tags: [error-state, silent-requests, d-02, d-09, d-10, accessibility]
status: complete
dependency_graph:
  requires: [11-03, 11-04]
  provides:
    - ErrorState.vue（页面级加载失败块，5 变体）
    - metricMetaApi.getMetadata(config?)
    - alertApi.getUnreadCount(config?)
    - useMetricMetaStore().fetchMetadata({ silent? })
    - useAlertStore().fetchUnreadCount({ silent? })
  affects: [11-07, 12, 13, 14]
tech-stack:
  added: []
  patterns:
    - 变体只驱动图标与配色，文案由 props 传入
    - silent 只抑制展示，不改变 store 降级与 reject
key-files:
  created:
    - hhs-frontend-v2/src/components/common/ErrorState.vue
    - hhs-frontend-v2/src/__tests__/components/ErrorState.spec.ts
  modified:
    - hhs-frontend-v2/src/api/metric-meta.ts
    - hhs-frontend-v2/src/api/alert.ts
    - hhs-frontend-v2/src/stores/metricMeta.ts
    - hhs-frontend-v2/src/stores/alert.ts
    - hhs-frontend-v2/src/App.vue
    - hhs-frontend-v2/src/__tests__/stores/metricMeta.spec.ts
decisions:
  - "load/server/network 共用 WarningFilled，靠文案区分，避免 29 页图标大杂烩"
  - "forbidden/notFound 走 --text-3 + rgba(141,144,156,0.12) 中性色，不渲染成故障"
  - "说明→按钮的 24px 由父级 gap 8px + .error-state__actions margin-top 16px 组成，避免单一 gap 把 24px 压成 8px 或叠成 32px"
  - "silent 作为调用点参数透传，不做 store 状态、不改变 catch 降级"
metrics:
  duration: 约 8 分钟
  completed: 2026-11-06
  tasks: 2
  commits: 2
  plan_head_before: 09be721
  plan_head_after: 14e011a
actuals:
  tokens: 4400
  tasks: 2
  commits: 2
---

# Phase 11 Plan 06: 页面级错误状态块与启动期静默请求 Summary

补齐错误体系的第三块呈现面：`ErrorState.vue` 承担页面级加载失败（D-02），并把启动期元数据预加载与未读数轮询真正标成 `silent`，消除"后台静默降级却弹红条"的存量噪音。

## What Was Built

### Task 1 — 页面级加载失败的完整呈现（type=tracer）

`src/components/common/ErrorState.vue`（新建，129 行）:

- **Props**：`variant`（`load` 默认 / `server` / `network` / `forbidden` / `notFound`）、`title`（required）、`description`（默认 `''`）、`retry`、`retryLabel`、`retrying`。
- **变体映射**：`ICONS` 常量把三档故障变体指向同一个 `WarningFilled`，`forbidden → Lock`、`notFound → DocumentDelete`；`tinted` 计算属性只让前三者走 `--hhs-color-error-tint` / `--hhs-color-error-text`，其余落到 `--muted` 修饰类（`rgba(141,144,156,0.12)` + `var(--text-3)`）。
- **无障碍**：根节点 `role="status"` + `aria-live="polite"` + `:aria-busy="retrying ? 'true' : 'false'"`；明确不用 `role="alert"`（常驻块非紧急，不应打断）。组件不调用 `focus()`，挂载不抢焦点。
- **无死按钮**：`div.error-state__actions` 由 `v-if="retry"` 控制，`retry` 缺省时整块不渲染。
- **可访问名**：`accessibleRetryName = retryLabel || 重试：${title}`，绑在 `:aria-label`；可见文本恒为 `重试`。`handleRetry` 只 `await props.retry?.()`，不维护内部 loading、不自动重试第二次（重试节奏由父组件 `retrying` 控制）。
- **间距**：`.error-state { gap: 8px }` 承载图标圆→标题→说明的两段 8px；说明→按钮的 24px 由 `.error-state__actions { margin-top: 16px }` 叠加得到（8 + 16）。这与简单写 `margin-top: 24px` 得到 32px、或依赖单一 `gap` 得到 8px 都不等价，因此显式拆开。
- **容器**：`min-height: 320px; max-width: 480px; margin: 0 auto; padding: 48px 24px; background: var(--surface-1); border: 1px dashed var(--border); border-radius: var(--radius-lg)`；图标圆 64px + `border-radius: var(--radius-full)` + glyph `:size="28"`；标题 600 18px/1.3 `--text-1`；说明 400 14px/1.5 `--text-3`。无 `!important`、无入场动画、无 GSAP。

`src/__tests__/components/ErrorState.spec.ts`（新建，14 用例）:

- el-button stub 采用**写法 A**：`props: ['type','size','loading','ariaLabel']` + 模板 `:aria-label="ariaLabel"` 显式把归一化后的 prop 绑回属性。若只声明 prop 而不绑回模板，Vue 会把值当 prop 吞掉，`attributes('aria-label')` 永远 `undefined` —— 属性丢失这个真实缺陷就会被测试掩盖。
- 断言全部作用在 DOM 属性（`attributes('aria-label')`），未使用 `props('ariaLabel')`。
- 覆盖：默认 `role=status` / `aria-live=polite`；不出现 `role=alert`；无 retry 时 `.error-state__actions` 与 `.el-button` 均不存在；有 retry 时可见文本 `重试`；`retryLabel` 优先、缺省组合 `重试：无法加载阈值列表`；点击回调恰好 1 次；`retrying` → `aria-busy=true` 且按钮 `disabled`；`forbidden` → `--muted` 修饰类，`load` → 无该修饰类；五个变体各自渲染对应 glyph；空 description → 无 `.error-state__desc`；挂载后 `document.activeElement` 不落在组件内。

**Tracer feedback gate**：Task 1 提交后、扩展任务前重跑 `<verify>`，14/14 通过，基础层无缺陷，得以继续 Task 2（自动模式：`workflow.auto_advance: true`）。

### Task 2 — 启动期与未读数请求标 silent

- **`src/api/metric-meta.ts`**：`getMetadata(config?: AxiosRequestConfig)`，透传至 `request.get<MetricMetaMap>('/api/metrics/metadata', config)`。
- **`src/api/alert.ts`**：`getUnreadCount(config?: AxiosRequestConfig)`，透传至 `request.get<number>('/api/alerts/unread-count', config)`。其余 6 个方法未改动。
- **`src/stores/metricMeta.ts`**：`fetchMetadata(options: { silent?: boolean } = {})`，内部 `metricMetaApi.getMetadata(options.silent ? { silent: true } : undefined)`。`catch` 的静态字典降级与 DEV-only `console.warn` 守卫原样保留；`silent` 不入 store 状态。
- **`src/stores/alert.ts`**：`fetchUnreadCount(options: { silent?: boolean } = {})` 同款透传；空 catch 换成带意图的注释 `// silent: 后台计数失败保持原值，由调用方决定是否提示`。`fetchRecentAlerts` / `markAsRead` / `markAllAsRead` 三个 action **未改**（Phase 14 逐页迁移范围）。
- **`src/App.vue`**：`onMounted` 中 `fetchMetadata({ silent: true })`、`fetchUnreadCount({ silent: true })`；保留 `authStore.isAuthenticated` 守卫；不加 `await`（保持非阻塞启动）。
- **`src/__tests__/stores/metricMeta.spec.ts`**：追加 2 条用例 —— silent 时 API 收到 `{ silent: true }` 且 store 仍回落静态字典（锁定 D-10「只抑制展示」），无参时收到 `undefined`（不复用旧行为误传 silent）。既有 5 条用例未删改。

## Verification

| Gate | Result |
|------|--------|
| `grep -c 'role="status"' ErrorState.vue` | 1（≥1） |
| 代码行 `grep -c 'role="alert"'` | 0 |
| `grep -c "min-height: 320px"` | 1（≥1） |
| `grep -c "WarningFilled"` | 4（≥1） |
| `grep -c "DocumentDelete"` | 2（≥1） |
| 代码行 `grep -c "!important"` | 0 |
| spec 中断言 `attributes('aria-label')` | 2 处；`props('ariaLabel')` 0 处 |
| `test:run -- ErrorState.spec.ts` | 14 passed（≥8） |
| `grep -c "silent: true" App.vue` | 2（≥2） |
| `grep -c "options.silent ? { silent: true } : undefined"` metricMeta / alert | 各 1（≥1） |
| `grep -c "AxiosRequestConfig"` metric-meta / alert | 各 2（≥1） |
| `test:run` 全量 | 21 files / **196 tests passed**（基线 180，未回归） |
| `npm run build`（vue-tsc -b + vite） | 通过，无 TS 错误 |
| `grep -rn "ElMessage"` metricMeta/alert stores + App.vue | 无输出 |
| `grep -rn "showError"` metricMeta/alert stores | 无输出（store 不 import 展示层） |
| eslint 触碰文件 | 0 errors / 6 warnings（4 处既有未用 catch 绑定、1 处既有 `any`、1 处 DEV 守卫 console） |

## Deviations from Plan

### 计划内调整

**1. `.error-state__actions` 的 24px 间距实现方式**

- **Found during:** Task 1 样式编写
- **Issue:** 计划的 `<ui_spec_bindings>` 与 acceptance 要求「说明与按钮之间是 24px」，同时样式段要求 `.error-state { gap: 8px }` + `.error-state__actions { margin-top: 24px }`。但父级 `gap: 8px` 与 `margin-top: 24px` 会叠加成 **32px**，与 UI-SPEC 结构图的 24px 不符。
- **Fix:** 保持 `gap: 8px`，把 `.error-state__actions` 的 `margin-top` 写成 **16px**（8 视觉 + 16 自身 = 24），并在代码注释中写明为何不写 24px、也不用单一 gap。语义结果与 UI-SPEC 一致：图标圆—标题 8px、标题—说明 8px、说明—按钮 24px。
- **Files modified:** `hhs-frontend-v2/src/components/common/ErrorState.vue`
- **Commit:** 390ebe1

**2. ESLint `no-unused-vars` 警告未处理**

- `stores/alert.ts` 的 4 处 `catch (error)` 绑定未使用，属既有代码形态；本计划只替换其中一条注释，未扩大改动面，故保留（0 errors）。

无 Rule 1–4 类偏差需自动修复；未新增任何 npm 依赖（T-11-SC 保持 mitigated）；未修改 `vitest.config.ts` / `setup.ts`。

## Auth Gates

无。本计划不涉及认证凭据、外部服务或手工登录步骤。

## Known Stubs

无。`ErrorState` 的五种变体全部实现（`forbidden` / `notFound` 的页面接线属 Phase 14 ERR-07，是计划显式划定的阶段边界，非桩）。两个 `silent` 调用点均已接到真实 API 透传链路，并有测试锁定参数。

## Threat Surface Scan

计划 `<threat_model>` 已覆盖本计划引入的表面，未发现新增端点、加密面或信任边界：

- **T-11-17（Information Disclosure, medium → mitigate）**：silent 路径未新增任何 `console` 输出；`metricsMeta` 的 `console.warn` 仍受 `import.meta.env.DEV && !import.meta.env.VITEST` 守卫，生产构建无日志。
- **T-11-18（Repudiation, high → mitigate）**：silent 仅抑制展示。新增用例断言 `fetchMetadata({ silent: true })` 被拒绝时 `isLoaded === false` 且仍回落静态字典，防止将来把 silent 改成"吞错"或 resolve 假空值。
- **T-11-19（Tampering / XSS, medium → mitigate）**：`title` / `description` 全部走 `{{ }}` 文本插值；`aria-label` 为 computed 模板字符串拼纯文本，不注入 HTML。
- **T-11-20（DoS, low → mitigate）**：`retrying` 期间按钮 `loading`（stub 断言 `disabled` 存在），组件自身不维护计时器。
- **T-11-SC（Tampering, high → mitigate）**：零新增依赖，仅用已安装的 `@element-plus/icons-vue@2.3.2` 与 `@vue/test-utils@2.4.6`。

## Self-Check

- 8 个目标文件全部存在（FOUND）。
- 两个任务提交均为 HEAD 祖先：`390ebe1`、`14e011a`。
- `npm run test:run`：21 files / 196 tests passed（基线 180）。
- `npm run build`：通过。

## Self-Check: PASSED