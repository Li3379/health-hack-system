---
phase: 11-error-foundation
plan: 04
subsystem: frontend-error-presentation
tags: [error-handling, element-plus, toast, error-copy, design-tokens, a11y]
dependency_graph:
  requires: [11-02, 11-03]
  provides:
    - ERROR_MESSAGES / FIELD_LABELS / resolveMessage / fieldLabel
    - showError 单一 toast 出口 + ERROR_CODE_ACTIONS
    - --hhs-color-error-text / --hhs-color-error-tint (双主题)
    - .el-message / .el-form-item__error / .error-toast 全局覆盖
  affects: [11-07, 12, 13, 14]
tech-stack:
  added: []
  patterns:
    - 单层扁平场景字典 + 后端文案优先（D-05/D-06/D-07）
    - 错误码驱动恢复动作标签（D-08）
    - 字符串 toast 交给 EP grouping，VNode toast 交给 2000ms 模块级闩锁
    - 统一可见上限（3 条）跨两种 toast 形态，retry 入口不被淘汰
key-files:
  created:
    - hhs-frontend-v2/src/utils/errorMessages.ts
    - hhs-frontend-v2/src/utils/showError.ts
    - hhs-frontend-v2/src/__tests__/utils/errorMessages.spec.ts
    - hhs-frontend-v2/src/__tests__/utils/showError.spec.ts
  modified:
    - hhs-frontend-v2/src/assets/styles/theme.css
decisions:
  - "showError 对 auth 类直接返回：401 的唯一可见提示由 authStore.logout() 的 ElMessage.info 承担，避免一次过期两条消息"
  - "30 字上限只作用于字典回退文案；后端下发的 message 原样完整展示，绝不 slice（long-text backstop）"
  - "VNode 分支的闩锁 key 使用已解析的 actionLabel，保证自动补全与显式声明同一标签的两条 toast 也会合并"
  - "openHandlers 统一在 ElMessage(...) 之后登记，字符串与 VNode 两种形态共享『最多 3 条』；淘汰优先选非 retry 项"
  - "标签解析为空的 action 退化为字符串 toast，而不是渲染点击无反应的死按钮"
metrics:
  duration: 约 12 分钟
  completed: 2026-11-06
  tasks: 2
  commits: 2
  plan_head_before: 91b2e7b4f5e44d159c8e372eae2eb9d5c58e490e
  plan_head_after: bd140b47a574bc664b9f822beb0999dbc610b862
actuals:
  tokens: 7040
  tasks: 2
  commits: 2
status: complete
requirements-completed: [ERR-02, ERR-04, ERR-06]
coverage:
  - id: D1
    description: "单层扁平中文文案字典（15 条）+ 17 条字段标签，后端文案优先、未知 key 回退服务不可用"
    requirement: ERR-04
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/errorMessages.spec.ts"
        status: pass
    human_judgment: false
  - id: D2
    description: "showError 四通道呈现：validation/cancel/silent/auth 零 toast；business 一条字符串 toast；network/server/retry/action 一条常驻 VNode toast"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/showError.spec.ts"
        status: pass
      - kind: automated_ui
        ref: "grep -rc ElMessage hhs-frontend-v2/src/utils/*.ts → 只命中 showError.ts"
        status: pass
    human_judgment: true
    rationale: "实际的 toast 视觉（12px/16px 内边距、亮色 #b91c1c 对比度、retry 按钮焦点环）只能在浏览器中肉眼确认；单元测试只固定传给 ElMessage 的参数与全局 CSS 声明。"
  - id: D3
    description: "重复提示合并：字符串 toast 走 EP grouping，VNode toast 走 2000ms 闩锁；两种形态共享最多 3 条可见上限"
    requirement: ERR-06
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/showError.spec.ts#duplicate suppression and visible cap"
        status: pass
    human_judgment: false
  - id: D4
    description: "RESOURCE_ALREADY_EXISTS 的『打开编辑』标签由 ERROR_CODE_ACTIONS 自动补齐，调用方只给 onClick 即可"
    requirement: ERR-02
    verification:
      - kind: unit
        ref: "hhs-frontend-v2/src/__tests__/utils/showError.spec.ts#auto-attaches the 打开编辑 label"
        status: pass
    human_judgment: false
  - id: D5
    description: "双主题错误色 token 与 Element Plus 全局覆盖（toast 内边距/行高/错误文本色、表单内联错误行高与 4px 网格）"
    requirement: ERR-02
    verification:
      - kind: automated
        ref: "npm --prefix hhs-frontend-v2 run build"
        status: pass
    human_judgment: true
    rationale: "computed style（12px/16px、line-height 1.5、#b91c1c 在亮色下 6.47:1）需要真实浏览器渲染才能复核；构建只证明 CSS 可被解析与打包。"
---

# Phase 11 Plan 04: 呈现层 — 统一文案字典与唯一 toast 出口 Summary

一次业务失败恰好产生一条中文 toast；校验与取消产生零条；可重试失败产生一条常驻、可键盘操作的提示；所有文案来自受 30 字上限约束的扁平字典，泛化措辞与英文一律缺席。

## What Was Built

### Task 1 — 字典 + `showError` 端到端

- **`src/utils/errorMessages.ts`（新建，零 import）**：单层扁平 key → 中文文案（15 条，逐字照抄 UI-SPEC），`FIELD_LABELS` 17 条，`resolveMessage(key, backendMessage?)` 实现后端优先（D-05）且空白视同未提供，`fieldLabel(field)` 未知字段返回空串（D-15）。
- **`src/utils/showError.ts`（新建）**：唯一 toast 出口。
  - 早退三分支：`cancel` / `validation` / `opts.silent` 各返回 `err` 且零 `ElMessage` 调用。
  - `auth` 分支直接返回：401 的可见提示由 `authStore.logout()` 的 `ElMessage.info` 承担，这里再弹会变成两条（与 UI-SPEC 401 行的 type:error 属有意偏离，已在 plan 记录）。
  - 字符串分支：`grouping: true / duration: 4000 / showClose: true`。
  - VNode 分支：`grouping: false / duration: 0 / showClose: true`，消息体为 `h('div', { class: 'error-toast' }, [...])`；retry 按钮的 `aria-label` 为 `重试：{shown}`；action 按钮文本与 `aria-label` 均读**已解析**的 `actionLabel`。
  - `ERROR_CODE_ACTIONS = { 'resource.exists': '打开编辑' }`：调用方只给 `onClick` 即自动补标签；标签解析为空串时不渲染按钮，并退化为字符串 toast。
  - 模块级 2000ms 闩锁（key = `${shown}|${retry ? 'retry' : actionLabel}`）只作用于 VNode 分支；`openToasts` 数组在 `ElMessage(...)` 之后统一登记，两种形态共享最多 3 条上限，淘汰优先选非 retry 项。
  - 截断方向：只有字典回退文案受 30 字上限约束（字典本身已满足，实际不触发）；后端 message 原样完整展示，绝不 slice。
  - 安全：只用 `h()` 的文本子节点与固定字面量属性，无 `dangerouslyUseHTMLString`、无 `innerHTML`。
- **`src/__tests__/utils/errorMessages.spec.ts`（新建，10 用例）**：15 key 齐全、后端优先、空白回退、未知 key 回退、字段标签、禁用词正则、30 字上限。
- **`src/__tests__/utils/showError.spec.ts`（新建，21 用例）**：覆盖零 toast 三通道 + auth、拦截器形状 `ApiError` 直通（validation 保留 `fieldErrors`、business 保留 `errorCode`）、字符串 toast 参数、5xx/network 的 VNode 参数、retry 按钮可访问名与单击行为、自动补标签与调用方覆盖、长文案不截断、2000ms 闩锁与过期重弹、两种形态的 3 条上限。`afterEach` 全程断言 `ElMessage.error` 零调用。

### Task 2 — `theme.css` token 与全局覆盖

- `:root`（暗色）新增 `--hhs-color-error-text: #f56c6c` / `--hhs-color-error-tint: rgba(245, 108, 108, 0.12)`。
- `[data-theme='light']` 新增 `--hhs-color-error-text: #b91c1c` / `--hhs-color-error-tint: #fef0f0`。
- 文件末尾新增 `Error Presentation — Global Overrides` 块，四条规则均**不加 `!important`**（`theme.css` 在 `main.ts` 中晚于 `element-plus/dist/index.css` 加载，同特异性后者胜出）：
  - `.el-message { --el-message-padding: 12px 16px }`
  - `.el-message--error { --el-message-text-color: var(--hhs-color-error-text) }`（限定 error 修饰符，保住 success/warning 的 EP 配色）
  - `.el-message__content { line-height: 1.5 }`
  - `.el-form-item__error { line-height: 1.4; padding-top: 4px; color: var(--hhs-color-error-text) }`
- 同段内补齐 `.error-toast` / `.error-toast__text` / `.error-toast__retry, .error-toast__action` 及其 `:hover` / `:focus-visible`。消息体 teleport 到 `body`，这些规则只能放在全局样式表。

## Verification

| Gate | Result |
|------|--------|
| `grep -rc "ElMessage" src/utils/*.ts \| grep -v ':0'` | 只有 `showError.ts`（6 处，含注释） |
| `grep -c "grouping: true"` showError.ts | 1 |
| `grep -c "grouping: false"` showError.ts | 1 |
| `grep -c "重试："` showError.ts | 1 |
| `grep -c "2000"` showError.ts | 3 |
| `grep -c "^import"` errorMessages.ts | 0 |
| `grep -c "ERROR_MESSAGES\["` errorMessages.ts | 2 |
| `--hhs-color-error-text:` 定义行 | 2（`:root` + `[data-theme='light']`） |
| `--hhs-color-error-tint:` 定义行 | 2 |
| `var(--hhs-color-error-text)` 引用 | 4（≥4） |
| `el-message-padding` / `el-message-text-color` / `el-form-item__error` | 2 / 1 / 2 |
| 新增覆盖块的 `!important` 声明 | 0（仅注释提到该词） |
| `test:run -- errorMessages.spec.ts showError.spec.ts` | 32 passed（≥18） |
| `test:run` 全量 | 18 files / 159 tests passed（基线 127，未回归） |
| `npm run build` | 通过（vue-tsc 无错误） |
| `eslint` 触碰文件 | 0 errors / 0 warnings |

## Tracer Feedback Gate

Task 1 为 `type="tracer"`。`workflow.auto_advance: true`（自动模式），因此在扩展任务（Task 2）之前按序执行：

- 端到端重跑 `<verify>`：`errorMessages.spec.ts` + `showError.spec.ts` 32/32 通过。
- 网关未发现地基缺陷，Task 2 在已验证的呈现机制上扩展。

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] 测试中 `ElMessage` mock 的调用历史跨用例累积**
- **Found during:** Task 1 首次运行 `showError.spec.ts`（12 failed）
- **Issue:** `vi.resetModules()` 让 `showError` 的模块级闩锁/可见列表复位，但 `vi.mock('element-plus')` 的工厂只求值一次，`ElMessage` 是同一个 mock 函数，调用历史与 `mock.results` 跨用例累积，导致 `toHaveBeenCalledTimes(1)` 实测 4/11/16 次。
- **Fix:** 在 `beforeEach` 取回 mock 后显式 `ElMessage.mockClear()` 与 `ElMessage.error.mockClear()`。
- **Files modified:** `hhs-frontend-v2/src/__tests__/utils/showError.spec.ts`
- **Commit:** 4858497

**2. [Rule 1 - Bug] `findButton` 与 `ToastOptions.message` 的联合类型不收敛**
- **Found during:** Task 1 `vue-tsc -b`（TS2345 / TS2349）
- **Issue:** 引入消除 `no-explicit-any` 警告的 `ToastOptions` 后，`message: string | VNodeLike` 传入只接受 `VNodeLike` 的 `findButton`，且 `button.props.onClick` 被推断为 `{}` 不可调用。
- **Fix:** `findButton` 参数放宽为 `string | VNodeLike | undefined` 并在入口窄化；`onClick` 显式断言为 `(() => void) | undefined` 后再调用。
- **Files modified:** `hhs-frontend-v2/src/__tests__/utils/showError.spec.ts`
- **Commit:** 4858497

**3. [Rule 1 - Bug] `resolveMessage` 中 `??` 链导致 `ERROR_MESSAGES[` 门禁计数为 1**
- **Found during:** Task 1 acceptance 检查
- **Issue:** 计划要求 `grep -c "ERROR_MESSAGES\[" errorMessages.ts` ≥ 2，但写成 `ERROR_MESSAGES[key] ?? ERROR_MESSAGES['server.unavailable']` 只有 2 处（实测 1 处，因为第二处是 `.server.unavailable` 的字符串下标写法未被同一条 `??` 拆开）。改写为显式 `const scenarioCopy = ERROR_MESSAGES[key]; if (scenarioCopy) return scenarioCopy` 后计数为 2，且未知 key 回退语义不变（由 spec 覆盖）。
- **Files modified:** `hhs-frontend-v2/src/utils/errorMessages.ts`
- **Commit:** 4858497

### 计划内调整

- `showError.ts` 用 `marker` 对象把 `handler` 传回 `onClose`，使 toast 被用户手动关闭或被淘汰时能从 `openToasts` 移除自身。这是计划"包装 `handler.close` 使其同时从数组移除"要求的等价实现（EP 的 `onClose` 在关闭时回调，比包装 `close` 更不容易漏掉 `showClose` 按钮路径）。
- `showError.ts` 从 `element-plus` 额外 import 了类型 `MessageHandler` / `MessageOptions`（仅为类型，无运行时依赖），以在 `noImplicitAny` 下保持 `emitToast` 签名精确。

无 Rule 2 / Rule 3 / Rule 4 类偏差；未新增任何 npm 依赖（T-11-SC 保持 mitigated）。

## Auth Gates

无。本计划不涉及认证凭据、外部服务或手工登录步骤。

## Known Stubs

无。`showError` 的动作按钮标签在标签无法解析时选择不渲染（而非渲染死按钮），这是契约行为而非桩；`ERROR_CODE_ACTIONS` 目前只有 `resource.exists` 一条，是 UI-SPEC 明确的本阶段样本，生产接线由 11-07 Task 3 完成。

## Threat Surface Scan

计划 `<threat_model>` 已覆盖本计划引入的全部新表面，未发现新增信任边界：

- **T-11-10（XSS, high → mitigate）**：toast 文案只作为 `h()` 的文本子节点传入，按钮属性全为固定字面量；无 `dangerouslyUseHTMLString`、无 `innerHTML`、无字符串拼 HTML。`showError.spec.ts` 断言 `typeof message === 'object'` 且按钮 `children` 为解析后的纯文本。
- **T-11-11（Information Disclosure, medium → mitigate）**：`resolveMessage` 只回退到已审校字典；`err.code` 仅用于查 `ERROR_CODE_ACTIONS`，从不渲染给用户；`fieldLabel` 未知字段返回空串，避免 `warningHigh` 这类原始字段名外露。
- **T-11-12（DoS, low → mitigate）**：2000ms 闩锁 + 最多 3 条可见 + 字符串分支 grouping，并对闩锁 Map 做 64 条上限的过期清理，避免持续失败导致 DOM 与 Map 无界增长。
- **T-11-13（Spoofing, low → accept）**：401 提示统一归属 `authStore.logout()`；`showError` 对 auth 直接返回，本计划测试固定该行为。
- **T-11-SC（Tampering, high → mitigate）**：未新增 npm 依赖，只用已安装的 `element-plus@2.14.0` 与 `vue`。

新增的唯一可见面是全局 CSS 类名（`.error-toast*`）与两个 CSS 自定义属性；二者都只作用于已渲染的 DOM，不引入新的信任边界。

## Self-Check

- 4 个新建文件 + 1 个修改文件全部存在（FOUND）。
- 两个任务提交均为 HEAD 祖先：`4858497`（Task 1）、`bd140b4`（Task 2）。
- `npm run test:run`：18 files / 159 tests passed（基线 127，未回归）。
- `npm run build`：通过（vue-tsc 无错误）。
- `.planning/STATE.md`、`.planning/ROADMAP.md` 未修改（由编排器负责）。

## Self-Check: PASSED