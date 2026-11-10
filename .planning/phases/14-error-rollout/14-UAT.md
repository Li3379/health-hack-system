---
status: testing
phase: 14-error-rollout
source:
  - 14-VERIFICATION.md
  - 14-UAT-SCRIPT.md（13 步可执行脚本原件，保留备查）
started: 2026-11-11T01:10:00Z
updated: 2026-11-11T01:40:00Z
---

## 采样口径

**`.el-message` 有 300ms 入场过渡** —— 动作后立刻查询读到 0，300ms 后读到 1。所有数 toast 的步骤统一等 300ms 再采样。
**判定基线**（D-02）：一次失败恰好一条提示。读到 `2` 是重复呈现，读到 `0` 是静默失败 —— 两者都算失败。

环境：后端 `localhost:8082`（UAT 期间按步骤启停）、前端 5173（HHS）、MySQL 3307 + Redis 6380 healthy。
账号：`admin` / `123456`（实测可登录）。

---

## Current Test

number: 4
name: 断网 60 秒、轮询活跃：全程零 toast
expected: |
  1. 打开 `/realtime/monitor`（轮询/WS 活跃页）；
  2. 停后端或 DevTools 切 Offline；
  3. 停留 60 秒不做任何操作；
  4. 采样 `document.querySelectorAll('.el-message').length` **全程 ≤ 1**（去重生效，不堆叠）；
  5. 恢复后状态块消失或提供重试，仍不产生重复 toast。
awaiting: user response

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
result: pending

### 5. 服务端拒绝某字段的表单：汇总 + N 条内联 + 零 toast + 值保留
expected: 提交一个会被 `@Valid` 拒绝的字段 → 顶部汇总块 + 该字段内联错误 + **零 toast** + 输入保留
result: pending

### 6. 普通账号访问设备平台页：页级 403 块、单个「返回首页」
expected: 访问 `/settings/device-platform`（无侧边栏入口，需手输）→ **页级** 403 解释块（非 toast）、单个「返回首页」动作
result: pending

### 7. 不存在的筛查记录详情：内容区块、两个动作、零 toast
expected: `/screening/detail/<不存在id>` → **内容区** 404 块、两个动作（返回列表 / 重新加载该记录）、零 toast
result: pending

### 8. 手输未知 URL：页级 404 块，不是白屏
expected: 手输 `/no-such-page` → **页级** 404 块（route-level catch-all），不是白屏也非跳首页
result: pending

### 9. 取消删除确认：零 toast
expected: 点删除 → 确认框 → 点「取消」→ **零 toast**（取消不是失败）
result: pending

### 10. 导航离开取消在途请求：零 toast
expected: 发起加载后立刻切换路由 → **零 toast**（AbortError 被识别为 cancel）
result: pending

### 11. 必填留空提交：输入框有红**描边**，不只是红字
expected: 必填字段留空提交 → 该输入框出现**红色描边**（14-02 全局规则），而非仅 12px 红字
result: pending

### 12. 日期范围控件出错并聚焦：边框仍红，且有可见焦点光晕
expected: daterange 出错并聚焦 → 边框保持红 **且** 有可见焦点光晕（`.is-active` 通道生效，WCAG 2.4.7）
result: pending

### 13. 两个视口兜底：320px 与 640px
expected: 320px 与 640px 下错误状态块/描边/按钮均不溢出、不重叠
result: pending

### N-1. 无档案用户打开个人档案页：create 模式仍成立
expected: 无档案账号打开 `/prevention/profile` → create 模式可用，不被引导逻辑挡住
result: pending

### N-2. 阈值页重复指标恢复动作仍可用
expected: 对已有阈值的指标再保存 → 1 条 toast + 「打开编辑」可跳转编辑
result: pending

### 附-14-02. 四条 computed-style 自检（WINDOWS.md #7 结转）
expected: ① 描边色 resolves 到错误 token；② textarea 用 inset box-shadow；③ 三族控件聚焦光晕 3px 且边框仍红；④ 失焦后光晕消失
result: pending

## 附：403 可达性说明

`/settings/device-platform` **无侧边栏入口**，只能手输 URL；`LoginUser.getAuthorities()` 无条件返回空列表且 `sys_user` 无 role 列，故所有认证用户访问 `/api/admin/device/config/**` 都 403 —— 这使 403 可稳定复现，也确认 A-3 禁止加路由角色守卫是唯一正确选择。

## Summary

total: 16
passed: 3
issues: 0
pending: 13
skipped: 0
blocked: 0

## Gaps

<!-- None yet -->
---

## 🔴 UAT 发现缺陷 #1 — 全局错误描边未生效（步骤 11 失败）

**严重度**: major（err-03 的视觉验收项之一）
**步骤**: 11（必填留空提交：输入框应有红描边，不只是红字）

### 症状

`prevention/Profile.vue` 提交后，`.el-form-item.is-error` 正确添加（2 个），内联错误文案正确显示（「请选择性别」「请选择出生日期」），但出错控件的边框**仍是默认灰** `rgba(0,0,0,0.08)`，`box-shadow: none`。用户看到的只有 12px 红字，没有描边。

### 已排除

| 假设 | 结论 |
|------|------|
| CSS 未加载 / Vite 缓存 | **已排除**：重启 dev server 后 dev 输出 `is-error` 从 1 次变 7 次，CSSOM 确认 6 条规则已解析 |
| Token 未定义 | **已排除**：`--hhs-color-error-text` 在元素上解析为 `#b91c1c`（light） |
| 选择器不匹配 | **已排除**：`wr.matches('body .el-form-item.is-error .el-form-item__content .el-input__wrapper')` = **true** |
| 特异性不足 | **已排除**：我的规则 401 vs 基线 100，两者均 `!important` |
| 过渡未完成 | **已排除**：0/200/500/1000/2000ms 五次采样全部灰色 |
| 页面 scoped 覆盖 | **已排除**：`Profile.vue` 无 `border`/`border-color` 声明 |
| `@layer` 层级 | **已排除**：全站无 `@layer` |
| 样式表被 disabled | **已排除**：含规则的 197 条样式表 `disabled: false` |
| 手写注入同样规则 | **也无效**——注入 `border-color: rgb(185,28,28) !important` 后计算值仍为灰色 |

### 关键矛盾

**连字面色值的 `!important` 长写属性都无法覆盖同表中的 `border` 简写 `!important`** —— 这与 CSS 层叠规范不符，除非浏览器把两者视为**同一条规则的不同部分**。

同表位置：
- 基线：`theme.css:502` `.el-input__wrapper { border: 1px solid var(--border) !important }`
- 我的：`theme.css:1413` `body .el-form-item.is-error .el-form-item__content .el-input__wrapper { border-color: ... !important }`

### 修复方向（待验证）

1. **把基线规则也改成 `border-color`** ——让两条规则操作同一长写属性，特异性 401 > 100 必然生效
2. **或提高选择器特异性**（如加 `:where()` 或重复类名），但这不解决"同表简写 vs 长写"的核心问题
3. **或在基线规则上加 `:not(.el-form-item.is-error *)`** 排除法

方案 1 最小且最可能有效。

### 待办

- [ ] 修复后重跑步骤 11 与步骤 12（描边 + 焦点光晕）
- [ ] 补充单测/结构测试覆盖（当前 14-02 的 spec 只验证 CSS 文本存在，未验证计算值——happy-dom 无法算样式，属已知限制 WINDOWS #7）
