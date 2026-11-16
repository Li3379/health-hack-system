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
updated: 2026-11-16T15:35:00Z
mode: automated-browser
reverified: 2026-11-16
reverified_reason: ee4d534 changed the envelope code contract — business failures now carry the semantic HTTP status, so 9 not-found codes reclassify from business to notFound
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

## 复验会话（2026-11-11）：验证指纹漂移后的重新确认

**触发原因**：`verification.status` 报 `stale`。根因不是代码退化，而是 **Phase 14 的迁移提交改动了本阶段 VERIFICATION 的 `covered_files`**（19 个受覆盖文件被 14-01/14-02/14-03/14-09/14-11/14-14 触及），摘要比验证报告新，指纹失配。

**复验方式**：只重测受漂移影响的呈现面，其余真值以 diff 范围 + 全量门禁重新确认（不做全量重跑，因为原 7 项检查覆盖的场景未被修改）。

### R-1. 双呈现契约在漂移后仍成立（`Profile.vue` 被 Phase 14 改过 4 次）
result: pass
evidence: |
  实时读者：`/prevention/profile` 保存空必填项后——
  - `toastCount = 0`（零 toast，D-02 分工未破）
  - `.form-error-summary` 恰 1 个，标题「请修正以下 2 项填写问题」
  - 汇总条目 2 条：「性别：请选择性别」「出生日期：请选择出生日期」
  - 内联错误 2 条，`.el-form-item.is-error` 计数 = 2
  - 点击汇总第 1 条 → `document.activeElement` 落在 `.el-form-item` 标签为「性别」的 `input[type=radio]`
  - 已输入的「过敏史 = 青霉素过敏」在失败提交后保留（ERR-05 非破坏性）

### R-2. 视觉契约数值未漂移（`FormErrorSummary.vue` 未被 Phase 14 修改，但 `theme.css` 改了 5 次）
result: pass
evidence: |
  汇总标题 computed style：`14px / 600 / 19.6px`（= 14 × 1.4），色 `rgb(185,28,28)`（= `#b91c1c` token）。
  列表 `max-height: 144px; overflow-y: auto`。

### R-3. 四通道 toast 分类在 9 步分类器插入 403/404 后未回归
result: pass
evidence: |
  - **业务失败**：错误密码登录 → 恰 1 条「密码不正确: 用户名或密码错误」，停留 `/login`，零英文。
  - **网络失败**：劫持 `XMLHttpRequest.send` 制造真实 `error` 事件 → 删除阈值 → 恰 1 条「网络连接异常，请检查网络后重试」+「重试」按钮。
  - **用户取消**：删除 → 确认框点「取消」→ 零 toast、行数仍 6。
  - **令牌过期**：写入无效 token → 恰 1 条「登录已过期，请重新登录」并跳 `/login`，1.2s 与 3.2s 两次采样均为 1（401 闩锁仍先于新增的 403/404 分支）。

### R-4. OAuth 回调页仍只显示固定中文
result: pass
evidence: |
  - `/oauth/callback/weibo` → 「无效的平台 / 该设备平台暂不支持授权连接」，禁用串 0 命中。
  - `/oauth/callback/huawei?error=access_denied&error_description=User%20denied%20access` → 「授权被拒绝 / 您在授权页面取消了授权」，`User denied access` 0 命中（外部可控参数仍不回显）。

### R-5. 双主题错误 token 未漂移
result: pass
evidence: |
  `data-theme` 切换实测：light → `--hhs-color-error-text: #b91c1c`、`--hhs-color-error-tint: #fef0f0`；
  dark → `#f56c6c`、`rgba(245,108,108,0.12)`。与 Phase 11 原记录逐字一致。
  `theme.css` 四条 `body .el-*` 归一化规则（`:1220/:1226/:1232/:1238`）仍在，无 `!important` 回退成加载顺序依赖。

### R-6. 结构性真值（不重测，改以门禁 + diff 范围确认）
result: pass
evidence: |
  - `utils/` 内 `ElMessage` 唯一出口仍是 `showError.ts`（`grep` 确认）。
  - `src/stores` 内 `ElMessage.error` = **0**。
  - 生产裸 `ElMessage.error` 残留 **87 → 4**（Phase 14 按 D-01 迁移，比本阶段基准更好，非退化）。
  - `normalizeApiError` 幂等直通 `raw instanceof ApiError` 仍在 `:91`，位于 `isCancel` 之前。
  - `roles=status` / `aria-live=polite` / `aria-busy` 在 `ErrorState.vue` 未变。
  - 全量门禁：后端 **845 tests / 0 failures**（BUILD SUCCESS）；前端 **642 tests / 59 files** 全绿。

**环境说明（复验者必读）**：IAB 面板非前台时（`document.hasFocus() === false`）Chrome **冻结全部 CSS 动画时间轴**（实测 8 个动画 `currentTime` 恒为 0），导致 `el-message-box` 的 leave 过渡永不结束、遮罩滞留。这是与 Phase 12 同一类环境假阳性，**不是代码缺陷**——R-3 的取消判定因此以「零 toast + 行数不变」为准，不以遮罩消失为准。

## 复验会话（2026-11-16）：信封语义变更后的重新确认

**触发原因**：`verification.status` 再次报 `stale`。这次不是指纹滞后，而是 **`ee4d534` 主动推翻了本阶段 truth 7 的前提** —— `handleBusinessException` 从 `Result.failure(ec.getCode(), message)` 改为 `Result.failure(ec.getHttpStatus(), ec.getCode(), message)`，业务失败的信封 `code` 不再恒为 500 回退值。

**为什么必须重测而不是刷摘要**：9 个 `404` 族 ErrorCode 的分类结果在改动前后**从 `business` 变成了 `notFound`**（`appError.ts` 第 5 步 `envelopeCode === 404` 命中）。而 `showError` 对 `notFound` **默认零 toast**。如果某处调用点原本依赖 404 触发一条提示，这次改动会让它变成静默失败 —— 这是必须实测确认的风险面。

### R-7. 档案 404 端到端：仍进入可编辑、零 toast
result: pass
evidence: |
  先确认后端确实在走新语义：`GET /api/prevention/profile`（无档案）返回
  `{"code":404,"message":"健康档案不存在，请先创建","data":null}`，HTTP 200 信封。
  **注意该响应没有 `errorCode`** —— 因为它来自 `PreventionController.java:62` 的 `Result.failure(404, msg)`，
  不走 `handleBusinessException`。这是既有行为，与 `ee4d534` 无关，已单独记录。

  `/prevention/profile` 实测：`toastCount = 0`、页面停在只读态并显示「编辑」按钮；
  点「编辑」后出现「保存 / 放弃编辑」、零 toast、`.el-form-item.is-required` = 2、无「无权访问」类门禁文案。
  与 N-1 的原记录（「编辑」按钮可用并成功进入编辑态）一致，**不是回归**。

  说明：`editing` 不会自动置 true 是既有行为 —— `Profile.vue:397-399` 的 notFound 判定读
  `err.status === 404 || err.code === 'health.profile_not_found' || err.code === 'resource.not_found'`，
  而该 404 的 `err.status` 是 200（信封）且无 `errorCode`，三条全不命中。改动前后完全一致
  （`git show ee4d534^:...appError.ts` 的第 5 步表达式逐字相同）。

### R-8. 409 重复资源：仍归 business，未被新语义吞成静默
result: pass
evidence: |
  后端实测：`POST /api/thresholds` 首次成功（`id: 17`），重复提交返回
  `{"code":409,"message":"资源已存在: 该指标已有阈值，是否改为编辑？","data":null,"errorCode":"resource.exists"}`。
  前端分类不变：`appError.spec.ts:133`「classifies envelope code 409 as business with backend message」
  断言 `kind === 'business'`、`code === 'resource.exists'`、`retryable === false` —— 该用例在复验中通过。
  原因：分类第 4/5 步只匹配 403 与 404，409 落到第 8 步的 business 分支，**没有被新语义改成 notFound**。

### R-9. 四条展示通道的分工未被破坏
result: pass
evidence: |
  - **403 页级块**：`/settings/device-platform` → `.error-state--page`、「无权访问此页面」、单个「返回首页」、**零 toast**。
  - **404 内容区块**：`/screening/detail/999999` → `.error-state`（无 `--page` 后缀）、
    两个动作（返回列表 / 重新加载该记录）、**零 toast**。与 403 形成预期对比。
  - **business（409）**：见 R-8，仍走一条可重试文案通道。
  - **auth（401）**：本会话未重测，其分类路径（第 3 步）位于新增的 403/404 之前，逻辑上不可能被影响；
    `request.spec.ts` / `auth.spec.ts` 全绿。

### R-10. 全量门禁
result: pass
evidence: |
  - 后端 `mvn -f hhs-backend/pom.xml test`：**849 tests / 0 failures / 0 errors**（比原基线 845 多 4 个新测例，
    全部来自 `GlobalExceptionHandlerTest` 与 `UserReminderControllerTest`）。
  - 前端 `npm --prefix hhs-frontend-v2 run test:run`：**642 tests / 59 files** 全绿。
  - 错误路径专项：`appError.spec.ts` + `showError.spec.ts` + `ProfileErrorSample.spec.ts` +
    `ErrorLayerSample.spec.ts` 共 **74 tests** 全绿。

### R-11. 发现并记录一个既有缺陷（非本阶段、非本次改动引入）
result: pass
evidence: |
  `PreventionController.java:62/79/167` 三处使用 `Result.failure(404, "健康档案不存在，请先创建")`，
  该重载**不设置 `errorCode`**（`Result.java:77` 只填 `code`/`message`）。因此档案 404 响应缺少
  `errorCode` 字段，`Profile.vue` 无法靠 `err.code` 识别「不存在」，只能靠用户手动点「编辑」。
  改动前同样如此（`parseCodeToInt` 会把它压成 500，更糟）。记为 **WINDOWS 观察项**，
  建议后续统一为 `Result.failure(ErrorCode.HEALTH_PROFILE_NOT_FOUND, ...)`。

## Current Test

[testing complete]

## Summary

total: 7
passed: 7
issues: 0
issues_found_and_fixed: 4
skipped: 0
blocked: 0

### 复验会话（2026-11-11）

total: 6
passed: 6
issues: 0
skipped: 0
blocked: 0

复验 6 项全部通过，无新增缺陷。验证指纹已刷新；本阶段结论维持 `passed`。

## Issues Found and Fixed In-Session

以下 4 项均在本次 UAT 会话中定位、修复、加回归测试并复测通过，无遗留。



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
