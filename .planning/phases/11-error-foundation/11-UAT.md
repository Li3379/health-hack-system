---
status: complete
phase: 11-error-foundation
source:
  - 11-01-SUMMARY.md
  - 11-02-SUMMARY.md
  - 11-03-SUMMARY.md
  - 11-04-SUMMARY.md
  - 11-05-SUMMARY.md
  - 11-06-SUMMARY.md
  - 11-07-SUMMARY.md
  - 11-08-SUMMARY.md
started: 2026-11-07T01:00:00Z
updated: 2026-11-07T09:25:00Z
mode: automated-browser
---

## Tests

### 1. 错误 toast 的四种渠道表现
expected: 业务失败 1 条中文 toast；网络失败 1 条带「重试」的常驻 toast；取消操作 0 条提示；令牌过期 1 条提示并跳登录页
result: [passed]
evidence: |
  浏览器自动化逐条验证（Playwright + 真实后端 localhost:8082 / 前端 localhost:5173）。

  - **业务失败**：阈值页重复添加「心率」→ 恰好 1 条 toast
    `资源已存在: 该指标已存在阈值，请直接编辑`，含「打开编辑」按钮，无英文、无「操作失败」。
  - **网络失败**：`page.route('**/api/**', abort)` 模拟断网后删除阈值 → 恰好 1 条
    `网络连接异常，请检查网络后重试` + 「重试」按钮；5 秒后仍在（`duration: 0` 常驻）；
    aria-label = `重试：网络连接异常，请检查网络后重试`。恢复网络后点「重试」→ 提示
    `删除成功` 且列表刷新为空，证明重试真正重发了请求。
  - **用户取消**：编辑弹窗「取消」与删除确认框「取消」各触发一次 → 未新增任何 toast。
  - **令牌过期**：写入无效 `hhs_token` 后进入阈值页 → 恰好 1 条 `登录已过期，请重新登录`
    并跳转 `/login`，1.2s 与 3.2s 两次采样均只有 1 条（401 闩锁生效，无重复）。

### 2. 双主题错误色与 Element Plus 覆盖
expected: 分别切换浅色/深色主题，错误文字与图标在两种主题下都清晰可读（浅色下不出现对比度不足的浅红）。toast 内边距为 12px 16px、行高 1.5；表单内联错误行高 1.4、上内边距 4px。成功/警告 toast 的颜色仍是 Element Plus 默认，未被染红。
result: [passed]
evidence: |
  通过 localStorage `hhs-theme` 切换主题后读取 computed style。

  | 观测点 | 浅色 | 深色 |
  |---|---|---|
  | `--hhs-color-error-text` | `#b91c1c` | `#f56c6c` |
  | 汇总标题色 | `rgb(185,28,28)` | `rgb(245,108,108)` |
  | 汇总条目链接色 | `rgb(185,28,28)` | `rgb(245,108,108)` |
  | 内联错误色 | `rgb(185,28,28)` | `rgb(245,108,108)` |
  | 字符串错误 toast 正文色 | `rgb(185,28,28)` | `rgb(245,108,108)` |

  - toast `padding: 12px 16px`，正文 `line-height: 21px`（14px × 1.5）。
  - 内联错误 `line-height: 16.8px`（12px × 1.4）、`padding-top: 4px`。
  - 成功 toast：`background rgb(240,249,235)`、文字 `rgb(26,28,35)` —— 保持 EP 绿色，未被染红。
  - 首次测量时标题/内联仍是 EP 的 `#f56c6c`，且内联 `padding-top` 仍为 2px；
    根因是 EP 组件样式被按需加载的路由 chunk 在 theme.css 之后再次注入。
    已改为 `body .类名`（特异性 0,1,1）与 `:deep()` 提升特异性修复，见 Issues I-02。

### 3. 表单错误汇总的视觉契约
expected: 提交一个有多项错误的表单后，顶部汇总标题为 14px 半粗体（600），行高 1.4；当错误超过 4 条时列表内部滚动（最大高度 144px），不把提交按钮挤出屏幕；点击汇总中的任一条目会滚动并聚焦到对应输入框，焦点环颜色与错误文字同色。
result: [passed]
evidence: |
  - 标题 computed style：`14px / 600 / 19.6px`（19.6 = 14 × 1.4）。
  - 列表 computed style：`max-height: 144px; overflow-y: auto`（超出 4 条即在内部滚动，
    不撑高页面）。当前页面表单最多只有 2 个必填字段，因此用样式契约 + 单元测试覆盖
    5 条以上场景，未构造 5 条错误的实时数据。
  - 点击「出生日期：请选择出生日期」→ `document.activeElement` 变为
    `input[placeholder="选择日期"]`；点击「性别：请选择性别」→ 聚焦到 radio。
  - 键盘 Tab 进入汇总条目时 `:focus-visible` 为 true，`outline: 2px solid rgb(185,28,28)`
    （浅色主题下与错误文字同色），`outline-offset: 2px`。

### 4. OAuth 回调失败页只显示固定中文
expected: 访问一个带非法 state 或未知平台的 OAuth 回调地址，页面只显示固定中文提示。页面源码中不出现 `Unknown platform`、`User denied access`、异常堆栈或原始平台字符串。
result: [passed]
evidence: |
  首次验证失败：`/oauth/callback/huawei?error=access_denied&error_description=User%20denied%20access`
  页面上原样显示了 `User denied access`（把 URL 查询参数直接塞进 `el-alert :description`），
  既是英文泄漏也是外部可控文本注入面。修复后复测 5 个用例全部干净：

  | 用例 | 页面文案 | 禁用串命中 |
  |---|---|---|
  | 未知平台 `weibo` | 无效的平台 / 该设备平台暂不支持授权连接 | 0 |
  | 已知平台 + 非法 state | 等待超时 / 授权处理时间过长 | 0 |
  | `error=access_denied` + 英文 description | 授权被拒绝 / 您在授权页面取消了授权 | 0 |
  | 未知 error 码 + 英文 description | 授权被拒绝 / 授权未能完成，请重新发起连接 | 0 |
  | 缺少 code | 参数缺失 / 授权回调缺少必要参数 | 0 |

  源码扫描 `Unknown platform` / `User denied access` / `denied access` / `Exception` /
  `at com.` / `undefined` / `Failed to` 均 0 命中。回归测试见
  `src/__tests__/views/OAuthCallbackError.spec.ts`（3 条）。

### 5. 登录/注册失败可见（代码审查 CR-01 回归）
expected: 输入错误密码点击登录 → 恰好出现 1 条中文提示（不静默、不重复）。提交一个已存在的用户名注册 → 恰好 1 条中文提示。两者都不出现英文原文。
result: [passed]
evidence: |
  - 错误密码登录 → 恰好 1 条 `密码不正确: 用户名或密码错误`，停留 `/login`，无第二条。
  - 已存在用户名注册 → 恰好 1 条 `用户信息冲突: 用户名已存在`，停留 `/register`。
  - 两处均无英文原文（唯一英文是页面页脚静态文案 `HHS Health Management System v1.0`）。
  - 备注：`分类: 后端消息` 的拼接形式读起来略重复（如「密码不正确: 用户名或密码错误」），
    属文案打磨项，不阻塞验收，留待 Phase 14 统一收敛。

### 6. 健康档案页 GOV.UK 双呈现
expected: 在健康档案页清空必填项（性别/出生日期）后保存 → 顶部出现常驻汇总（「请修正以下 N 项填写问题」），对应字段下方出现中文内联红字，**不出现 toast**。用户已输入的其他字段值不被清空，表单保持可编辑。修正一项后该条汇总消失；全部修正后汇总整体消失。
result: [passed]
evidence: |
  首次验证失败：只有内联红字、没有顶部汇总 —— 因为汇总只在 `err.kind === 'validation'`
  （服务端 400）时填充，客户端 `el-form` 校验失败直接 return。修复后复测：

  - 清空性别/出生日期后保存 → **0 条 toast**，顶部出现
    `请修正以下 2 项填写问题 / 性别：请选择性别 / 出生日期：请选择出生日期`，
    两个字段下方各有中文内联红字，表单仍处于编辑态。
  - 选择性别后 → 汇总变为 `请修正以下 1 项填写问题 / 出生日期：请选择出生日期`。
  - 再填入出生日期后 → 汇总整体卸载（DOM 中已无 `.form-error-summary`）。
  - 期间在「过敏史」输入的 `青霉素过敏` 在两次失败提交后仍然保留，未被清空。
  - 回归测试：`useFormServerErrors.spec.ts` 新增 `setErrors` 用例 2 条。

### 7. 阈值重复时的恢复动作（D-08）
expected: 在阈值页添加一个已存在的指标阈值并保存 → 出现 1 条中文提示，其中带「打开编辑」按钮；点击后关闭新增弹窗并直接进入该阈值的编辑态。不出现英文 `Threshold already exists`。
result: [passed]
evidence: |
  已存在「心率 120/100/50/40」后再添加同指标 → 恰好 1 条 toast
  `资源已存在: 该指标已存在阈值，请直接编辑` + 「打开编辑」按钮。
  点击后新增弹窗关闭、打开标题为「编辑阈值」的弹窗，四个数值预填
  `120.00 / 100.00 / 50.00 / 40.00`，指标类型显示「心率」。
  全流程未出现 `Threshold already exists`。

## Summary

total: 7
passed: 7
issues: 4
skipped: 0
blocked: 0

## Issues

### I-01（已修复）OAuth 回调页回显外部可控的 `error_description`
- 位置：`hhs-frontend-v2/src/views/oauth/OAuthCallback.vue`
- 现象：`error_description` 查询参数被直接渲染进 `el-alert :description`，
  出现英文 `User denied access`；同时未知平台不会报错而是落到 60s 等待超时。
- 修复：新增 `OAUTH_ERROR_MESSAGES` 白名单映射（8 个标准 OAuth 错误码）+
  `DEFAULT_OAUTH_ERROR_MESSAGE` 兜底；不再读取 `error_description`；
  平台白名单校验改为命中 `PLATFORM_NAMES` 才放行；`catch` 分支不再使用 `err.message`；
  `platformName` 的原始平台串回退改为「该设备」。
- 回归测试：`src/__tests__/views/OAuthCallbackError.spec.ts`（3 条）。

### I-02（已修复）浅色主题下错误色仍是 `#f56c6c`，内联错误间距未归一化
- 位置：`hhs-frontend-v2/src/assets/styles/theme.css`、
  `hhs-frontend-v2/src/components/common/FormErrorSummary.vue`
- 现象：浅色主题下汇总标题、内联错误、错误 toast 正文仍是 EP 默认 `#f56c6c`
  （白底 2.90:1 / tint 底 2.61:1，均不达 AA）；内联错误 `padding-top` 仍是 2px、
  `line-height` 仍是 1。
- 根因：Element Plus 组件样式被 `unplugin-vue-components` 按路由 chunk
  在 `theme.css` 之后再次注入，同特异性下反向覆盖了 theme.css 的归一化规则。
- 修复：四条全局归一化规则改用 `body .类名`（特异性 0,1,1）与注入顺序解耦；
  `FormErrorSummary.vue` 的 `:deep(.el-alert__title)` 补上 `color` 走错误 token。
- 复测：浅色 `#b91c1c`、深色 `#f56c6c`，标题/条目/内联/toast 四处一致。

### I-03（已修复）客户端 `el-form` 校验失败没有顶部汇总
- 位置：`hhs-frontend-v2/src/views/prevention/Profile.vue`、
  `hhs-frontend-v2/src/composables/useFormServerErrors.ts`
- 现象：清空必填项后保存只有内联红字，没有 GOV.UK 顶部汇总，违反 D-01 的双呈现契约。
- 修复：`useFormServerErrors` 新增 `setErrors(next)`（与服务端 `apply()` 共用同一份
  `fieldErrors`，因此汇总与内联天然同步）；`Profile.vue` 在 `validate()` 回调拿到
  `invalidFields` 后摊平为 `字段 → 首条中文消息` 并聚焦汇总块。
- 回归测试：`useFormServerErrors.spec.ts` 新增 2 条。

### I-04（已修复）网络/5xx 失败没有「重试」按钮（违反 D-03）
- 位置：`hhs-frontend-v2/src/utils/request.ts`、`src/utils/showError.ts`、
  `src/utils/appError.ts`、`src/views/health/Thresholds.vue`
- 现象：断网后删除阈值只出现一条 `网络连接异常，请检查网络后重试`，没有「重试」按钮。
  `ApiError.retryable` 已经标记了可重试，但 `showError` 只在调用方显式传 `retry` 时
  才渲染按钮，而 10 个生产调用点里只有 1 个传了。
- 修复：
  1. `ApiError` 新增 `retryRequest?: () => Promise<unknown>`；
  2. `request.ts` 在拦截器里把 `error.config` 包成 `retryRequest` 挂到 `ApiError`；
  3. `showError` 在调用方未给 `retry` 时自动使用 `retryRequest`，且只有
     `retryable === true`（网络 / 传输层 5xx）才渲染按钮 —— 业务失败不会出现死按钮；
  4. 重试再次失败会重新走 `showError`，用户不会失去反馈；
  5. `Thresholds.vue` 的删除重试改为重跑整个删除动作（含列表刷新与成功提示），
     否则重试成功后列表仍显示已删除的行。
- 回归测试：`showError.spec.ts` 新增 3 条。
- 备注：信封包装的系统异常（HTTP 200 + code 500）按既有契约仍归类 `business`，
  不带重试按钮 —— 该分类是 11-01 刻意保留的，改动会破坏 `Result.code` 数值语义。

## Gaps

无阻塞项。以下为已知但不在本阶段范围内的观察，记录给后续阶段：

1. **OAuth 非法 state 未校验**：已知平台 + 非法 `state` 仍会进入 60 秒等待超时，
   而不是立即报错。属功能缺口，与错误文案无关，建议 Phase 14 处理。
2. **`分类: 消息` 拼接读感重复**（如「密码不正确: 用户名或密码错误」）：
   文案打磨项，建议 Phase 14 统一收敛。
3. **阈值弹窗缺少填写引导**（无单位、无取值范围提示）：正是用户原始诉求之一，
   属 Phase 12（THR-GUIDE）范围。
4. **`retry` 仅接入阈值删除一个调用点**：其余调用点依赖拦截器自动注入的
   `retryRequest`（只重发 HTTP 请求，不触发页面级后续动作），Phase 14 收尾时
   对需要刷新的页面补齐显式回调。
