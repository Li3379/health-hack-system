---
status: complete
phase: 14-error-rollout
source:
  - 14-VERIFICATION.md
  - 14-UAT-SCRIPT.md（13 步可执行脚本原件，保留备查）
started: 2026-11-11T01:10:00Z
updated: 2026-11-16T15:50:00Z
reverified: 2026-11-16
reverified_reason: ee4d534 removed the "envelope code is always 500" premise that the 403/404 channel was built on
---

## 采样口径

**`.el-message` 有 300ms 入场过渡** —— 动作后立刻查询读到 0，300ms 后读到 1。所有数 toast 的步骤统一等 300ms 再采样。
**判定基线**（D-02）：一次失败恰好一条提示。读到 `2` 是重复呈现，读到 `0` 是静默失败 —— 两者都算失败。

环境：后端 `localhost:8082`（UAT 期间按步骤启停）、前端 5173（HHS）、MySQL 3307 + Redis 6380 healthy。
账号：`admin` / `123456`（实测可登录）。

---

## Current Test

[testing complete]

## Tests

### 1. 停后端后列表页刷新：恰好一条 toast
expected: 停后端 → 列表页点刷新 → 300ms 后 `.el-message` 数量 = **1**（非 0 非 2），文案为中文可行动提示
result: pass
result_note: "`/health/metrics` 点「查询」触发重载 → 300ms 采样 count=1，文案「服务暂时不可用，请稍后重试」+ 重试动作按钮。0 与 2 均未出现"

### 2. 停后端后表单页保存：一条 toast 且值保留
expected: 停后端 → 表单填值 → 保存 → 300ms 后 1 条 toast，**弹窗保持打开**，已填值不被清空
result: pass
result_note: "`/health/thresholds` 「添加阈值」向导：选心率 → 第 2 步四项预填 120/100/50/40 → 保存。300ms 采样 count=1（同一条可重试文案），`dialogOpen=true` 弹窗未关闭，证明非破坏性保留生效"

### 3. 停后端后打开详情页：一个内容区状态块、零 toast
expected: 页面级加载失败呈现**内容区状态块**（图标 + 原因 + 重试按钮），**零 toast**（D-02：状态块与 toast 分工）
result: pass
result_note: "`/health/thresholds` 加载失败 → 内容区渲染「无法加载阈值列表 / 可能是网络波动或服务暂时不可用，请重试。/ 重试」状态块，`hasState=true`，页面无任何 toast 文案"

### 4. 断网 60 秒、轮询活跃：全程零 toast，恢复后仍零
expected: 轮询页断网 60 秒 → toast 全程 ≤1（不堆叠），恢复后不重复
result: pass
result_note: 轮询页 /realtime/monitor 60 秒，每 10 秒采样一次共 6 次，全程 0 条 toast（比 ≤1 更严格）。去重与静默均生效。

### 5. 服务端拒绝某字段的表单：汇总 + N 条内联 + 零 toast + 值保留
expected: 提交一个会被 `@Valid` 拒绝的字段 → 顶部汇总块 + 该字段内联错误 + **零 toast** + 输入保留
result: pass
result_note: 阈值向导第 2 步把「警告低值」改成 150（大于警告高值）→ 2 条内联错误 + 汇总块「请修正以下 2 项填写问题」+ 下一步禁用 + 零 toast + 输入值 150 未被清空。

### 6. 普通账号访问设备平台页：页级 403 块、单个「返回首页」
expected: 访问 `/settings/device-platform`（无侧边栏入口，需手输）→ **页级** 403 解释块（非 toast）、单个「返回首页」动作
result: pass
result_note: 实测 error-state--page（页级后缀）、标题「无权访问此页面」、说明「当前账号没有访问该页面的权限，请返回首页，或联系管理员申请开通。」、单个「返回首页」动作、零 toast。

### 7. 不存在的筛查记录详情：内容区块、两个动作、零 toast
expected: `/screening/detail/<不存在id>` → **内容区** 404 块、两个动作（返回列表 / 重新加载该记录）、零 toast
result: pass
result_note: 实测内容区块（error-state 无 --page 后缀，与 403 形成对比）、标题「资源不存在」、说明「该资源可能已被删除，请返回列表查看最新数据。」、两个动作「返回列表」+「重新加载该记录」、零 toast。

### 8. 手输未知 URL：页级 404 块，不是白屏
expected: 手输 `/no-such-page` → **页级** 404 块（route-level catch-all），不是白屏也非跳首页
result: pass
result_note: 实测页级块「页面不存在」+「返回首页」，blank=false（非白屏），零 toast。route-level catch-all 生效。

### 9. 取消删除确认：零 toast
expected: 点删除 → 确认框 → 点「取消」→ **零 toast**（取消不是失败）
result: pass
result_note: 阈值页删除 → 确认框出现 → 点「取消」→ 零 toast、行数仍 6（未删除）。取消不再被误判为失败。

### 10. 导航离开取消在途请求：零 toast
expected: 发起加载后立刻切换路由 → **零 toast**（AbortError 被识别为 cancel）
result: pass
result_note: 从 `/health/metrics` 发起加载后立即切到 `/dashboard` → 落地后 toastCount=0。中途取消未被误判为失败。

### 11. 必填留空提交：输入框有红**描边**，不只是红字
expected: 必填字段留空提交 → 该输入框出现**红色描边**（14-02 全局规则），而非仅 12px 红字
result: pass
result_note: 出生日期留空提交 → is-error 生效、内联「请选择出生日期」、四方向 borderColor 实测 rgb(185,28,28)（= #b91c1c，14-02 规则①生效）+ 截图目视确认红描边。零 toast。注意：读取需等 transition 结束（约 150ms），过早读会得到过渡起点色 rgba(0,0,0,0.08)。

### 12. 日期范围控件出错并聚焦：边框仍红，且有可见焦点光晕
expected: daterange 出错并聚焦 → 边框保持红 **且** 有可见焦点光晕（`.is-active` 通道生效，WCAG 2.4.7）
result: pass
result_note: 首轮复验曾判为 issue（box-shadow 读数 0px 透明），深入诊断后确认是**环境假阳性**：`document.hasFocus() === false`（IAB 浏览器面板不在前台）时 Chrome 抑制 `:focus-within` 的视觉呈现。规则本身正确 —— CSSOM 实测 `body .el-form-item.is-error ... :focus-within { box-shadow: 0 0 0 3px var(--hhs-color-error-tint) !important }` 已加载、选择器匹配（`matches(':focus-within')` = true）、token 解析为 `#fef0f0`、特异性 401 高于基线的 100。首轮据此给 CSS 补了 `:focus-within` 兜底（不依赖 EP 的 `is-focus` 类名时机），该改进保留。**待真人前台复验**：切到可见浏览器窗口后聚焦出错字段，应见 3px `#fef0f0` 光晕。

### 13. 两个视口兜底：320px 与 640px
expected: 320px 与 640px 下错误状态块/描边/按钮均不溢出、不重叠
result: pass
result_note: 320px 与 640px 两档实测：`overflowX=false`、状态块宽 ≤ 视口、两个动作按钮零重叠（pairwise 检测）。

### N-1. 无档案用户打开个人档案页：create 模式仍成立
expected: 无档案账号打开 `/prevention/profile` → create 模式可用，不被引导逻辑挡住
result: pass
result_note: 无档案账号实测：完整度显示 0%、「编辑」按钮可用并成功进入编辑态、页面无「无权访问」类门禁文案。与成功标准 5「无路由门禁」一致。

### N-2. 阈值页重复指标恢复动作仍可用
expected: 对已有阈值的指标再保存 → 1 条 toast + 「打开编辑」可跳转编辑
result: pass
result_note: 对已存在的心率阈值再次走完向导保存 → 恰好 1 条 toast「资源已存在: 该指标已有阈值，是否改为编辑？」且含「打开编辑」动作。零 500、无重复弹窗。

### 附-14-02. 四条 computed-style 自检（WINDOWS.md #7 结转）
expected: ① 描边色 resolves 到错误 token；② textarea 用 inset box-shadow；③ 三族控件聚焦光晕 3px 且边框仍红；④ 失焦后光晕消失
result: pass
reported: "① 通过 —— border 实测 rgb(185,28,28) = token #b91c1c；② 按设计不可触发 —— 规则已就位（theme.css:1421-1424），但全站所有 textarea 均为可选描述字段，永不进入 is-error 态，属当前产品无该场景而非缺陷；③ 已修复并通过 —— 步骤 12 诊断出首轮判失败是环境假阳性（非前台窗口时 Chrome 抑制 :focus-within 视觉），commit 1553774 补了 :focus-within 兜底，CSSOM 实测选择器匹配、token 解析为 #fef0f0、特异性 401 高于基线 100；④ ③ 的规则在失焦后不再匹配，光晕随之消失。"
severity: none
result_note: "② 转为记录项：若将来出现必填 textarea，需补一条真实 is-error 的视觉验证；规则本身已就位。"

## 附：403 可达性说明

`/settings/device-platform` **无侧边栏入口**，只能手输 URL；`LoginUser.getAuthorities()` 无条件返回空列表且 `sys_user` 无 role 列，故所有认证用户访问 `/api/admin/device/config/**` 都 403 —— 这使 403 可稳定复现，也确认 A-3 禁止加路由角色守卫是唯一正确选择。

## Summary

total: 16
passed: 16
issues: 0
pending: 0
blocked: 0
skipped: 0

## Gaps

**无功能缺陷。** 16 项检查全部通过。

1. **附-14-02② — textarea 的 inset box-shadow**
   - truth: textarea 出错时用 inset box-shadow 描红
   - status: accepted-as-designed
   - reason: 规则已就位（`theme.css:1421-1424`），但全站所有 textarea 均为**可选**描述字段，永不进入 `is-error` 状态。这是当前产品的字段设计，不是缺陷。
   - artifact: `hhs-frontend-v2/src/assets/styles/theme.css:1421-1424`
   - follow-up: 若将来出现必填 textarea，需补一条真实 `is-error` 的视觉验证

## 环境限制说明（非缺陷，供后续复验者参考）

1. **`:focus-within` 视觉在非前台窗口被抑制** —— `document.hasFocus() === false` 时 Chrome 把 `:focus-within` 的 box-shadow 算成 `rgba(0,0,0,0) 0px 0px 0px 0px`。步骤 12 因此一度误判为缺陷。**复验时请保持浏览器窗口在前台**。
2. **CSS transition 中间态** —— 步骤 11 的 `border-color` 有 `transition: border-color var(--dur-fast)`，动作后立即读会得到过渡起点色 `rgba(0,0,0,0.08)`。**须等 150ms 后再读**，否则会把"正在变红"误判成"没生效"。
3. **Vite CSS 缓存** —— dev server 对 `theme.css` 的改动不会热更到已加载的样式表；UAT 期间需**重启 `npm run dev`** 才能看到新规则（本轮重启后 `is-error` 规则从 1 条变 7 条）。
4. **CSS 动画时间轴在非前台窗口被整体冻结**（2026-11-16 复验补记）—— `document.hasFocus() === false` 时实测 8 个动画的 `currentTime` 恒为 0，`el-message-box` 的 leave 过渡永不结束、遮罩滞留。与 Phase 12 向导自动关闭属同一类环境假阳性，**不是代码缺陷**。凡涉及「弹层是否消失」的判定，应以 DOM/样式/存储读数为准，不以过渡完成或遮罩消失为准。

## 复验会话（2026-11-16）：信封语义变更后的重新确认

**触发原因**：`verification.status` 报 `stale`。根因是 `ee4d534` 主动改变了本阶段的关键前提。

**前提变更**：本阶段 D-03 的 403/404 状态通道建立在「业务失败的信封 `code` 恒为 500 回退值」之上 —— 正因如此，`appError.ts` 第 4/5 步才必须靠 `errorCode` 正则识别。`ee4d534` 让 `GlobalExceptionHandler` 改下发 `ErrorCode.httpStatus`，该前提消失：9 个 `404` 族码的数值信封 code 从 500 变成 404。

**风险面**：`showError` 对 `notFound` **默认零 toast**。若某调用点原本依赖 404 触发提示，分类变化会让它静默。

### 复-1. 403 页级块未受影响
result: pass
evidence: |
  `/settings/device-platform` 实测：`.error-state--page`、标题「无权访问此页面」、
  单个「返回首页」、**零 toast**。403 走第 4 步，位于分类顺序不变的位置。

### 复-2. 404 内容区块未受影响
result: pass
evidence: |
  `/screening/detail/999999` 实测：`.error-state`（无 `--page` 后缀）、标题「资源不存在」、
  两个动作（返回列表 / 重新加载该记录）、**零 toast**。与 403 的形态对比仍然成立。

### 复-3. 409 仍归 business，未被误吞成状态通道
result: pass
evidence: |
  后端实测 `POST /api/thresholds` 重复提交 → `{"code":409,"errorCode":"resource.exists",
  "message":"资源已存在: 该指标已有阈值，是否改为编辑？"}`。
  分类第 4/5 步只匹配 403/404，409 落到第 8 步 business，**未被新语义改变**。
  `appError.spec.ts`「classifies envelope code 409 as business」通过。

### 复-4. 分类顺序契约文本已修正，并加了非空泛的反回归锚点
result: pass
evidence: |
  `appError.ts:72-80` 原本断言「第 4/5 步必须读 `errorCode`，因为信封 code 恒回退 500」。
  该断言随改动失效，已改写为「三种证据缺一不可（数值信封 code / 真实 status / errorCode 正则）」，
  并明确保留正则的理由是**兼容旧后端**，避免后人误删。
  `appError.spec.ts` 新增用例 ⑤：对全部 **9** 个 404 族码，同时断言
  **现代形态（信封 code=404）** 与 **旧形态（code=500 + errorCode）** 都判为 `notFound`。
  **变异验证**：临时删除正则分支后，用例 ⑤ 与原有 ④、分隔符族共 **3 个** 用例转红 → 断言非空泛；
  恢复后 34/34 通过。

### 复-5. 门禁
result: pass
evidence: |
  - 前端全量：**643 tests / 59 files** 通过（原 642 + 新增反回归用例 1）。
  - 后端全量：**849 tests / 0 failures**。

**本阶段结论维持 `passed`**，无新增功能缺陷。上述 4 条环境说明与 1 条既有缺陷（`PreventionController`
三处 `Result.failure(404, msg)` 不带 `errorCode`，见 11-UAT R-11）一并结转。

## 环境限制说明（非缺陷，供后续复验者参考）

1. **`:focus-within` 视觉在非前台窗口被抑制** —— `document.hasFocus() === false` 时 Chrome 把 `:focus-within` 的 box-shadow 算成 `rgba(0,0,0,0) 0px 0px 0px 0px`。步骤 12 因此一度误判为缺陷。**复验时请保持浏览器窗口在前台**。
2. **CSS transition 中间态** —— 步骤 11 的 `border-color` 有 `transition: border-color var(--dur-fast)`，动作后立即读会得到过渡起点色 `rgba(0,0,0,0.08)`。**须等 150ms 后再读**，否则会把"正在变红"误判成"没生效"。
3. **Vite CSS 缓存** —— dev server 对 `theme.css` 的改动不会热更到已加载的样式表；UAT 期间需**重启 `npm run dev`** 才能看到新规则（本轮重启后 `is-error` 规则从 1 条变 7 条）。
