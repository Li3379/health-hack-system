# Phase 14: 全站收尾排查与回归验证 - Discussion Log

> **仅作审计留痕。** 不作为规划、研究或执行 agent 的输入。
> 决策已固化在 CONTEXT.md —— 本日志只保留当时考虑的备选项。

**Date:** 2026-11-10
**Phase:** 14-全站收尾排查与回归验证
**Areas discussed:** 迁移范围、403/404 呈现、回归基线口径、端到端验证方式

---

## 迁移范围（87 处裸 `ElMessage.error`）

| Option | Description | Selected |
|--------|-------------|----------|
| 全迁用户可见路径 | 按「提交/加载」两类分批迁移，保留静默路径静默语义 | ✓（默认采纳） |
| 87 处全量替换 | 无差别替换，可能让后台请求也弹提示 | |
| 只修两类真问题 | 只修「提交清空」与「取消误判」，其余保留 | |

**User's choice:** 用户未作答。
**Notes:** 按推荐默认继续（D-01）。关键判断：**静默路径（现有 16 处 `{ silent: true }`）不算「残留」** —— 它们本就不该弹提示，改成 `showError` 反而制造噪音。验收标准 2 的「裸残留归零」据此解释为**用户可见路径**归零。

---

## 403 / 404 呈现

| Option | Description | Selected |
|--------|-------------|----------|
| 403 全页 / 404 内容区 | 403 全页解释页（带返回首页）；404 内容区 ErrorState（带重试/返回）；均不弹 toast | ✓（默认采纳） |
| 都用内容区 ErrorState | 仅文案不同，不新增页面级载体 | |
| 仅靠 toast 文案区分 | 保持现状（与验收标准「解释性状态」冲突） | |

**User's choice:** 用户未作答。
**Notes:** 按推荐默认继续（D-03）。与 Phase 11 D-02 的分工一致：**403/404 是「状态」不是「动作失败」**，故走状态块而非 toast。侦察确认 `appError.ts` / `showError.ts` 中**当前完全没有** 403/404 区分。

---

## 回归基线口径

| Option | Description | Selected |
|--------|-------------|----------|
| 锁定当前实测值 | 后端 ≥845 / 前端 ≥445，下降即失败 | ✓（默认采纳） |
| 按 ROADMAP 的 90 基线 | 不低于 90（远低于现状，几乎不会失败） | |
| 在原值上再提高 5% | 逼出补充测试（可能为凑数） | |

**User's choice:** 用户未作答。
**Notes:** 按推荐默认继续（D-05）。ROADMAP 原文的「90 项基线」是 v1.0 时期陈旧口径；实测已是前端 445 / 后端 845。锁定实测值才能真正防回归。

---

## 端到端验证方式

| Option | Description | Selected |
|--------|-------------|----------|
| 浏览器实跑 + E2E | 停后端逐页点保存/加载、断网 60 秒用真实浏览器验证，结果入 UAT | ✓（默认采纳） |
| 仅自动化 | 只靠单测 + E2E（断网心跳行为单测无法模拟） | |
| 新增断网 E2E 用例 | route abort 模拟断网，可重复但需后端可达 | |

**User's choice:** 用户未作答。
**Notes:** 按推荐默认继续（D-06）。验收标准 3 依赖**真实心跳/轮询节奏**，单测无法覆盖；且本轮 UAT 已经证明浏览器实跑能抓到自动化看不见的问题（Phase 13 的 3 项视觉 backstop、Phase 12 的端口漂移）。

---

## Claude's Discretion

- 87 处迁移的分批顺序与批次边界
- 403 全页状态的具体实现载体（扩展 ErrorState variant vs 轻量包装）
- store 内 8 处错误的改造方式（抛错 vs 返回结果对象）
- 迁移台账的载体与字段
- 「恰好一条提示」的观测方法（DOM 计数 vs 网络拦截）

## Deferred Ideas

- 错误监控上报（Sentry 类）
- 错误文案 i18n 多语言
- 自动化视觉回归（截图 diff）
- 错误码文档化（运营/客服手册）
- Testcontainers Docker socket 修复（基础设施，WINDOWS.md #3）

## 侦察到的关键事实（供 planner 参考）

| 事实 | 数值 |
|------|------|
| 裸 `ElMessage.error` 总数 | **87 处**（stores 内 8 处） |
| 已接入 `showError` 的页面 | 7 处 |
| 已接入 `useFormServerErrors` 的页面 | 4 个（Thresholds / ThresholdWizard / prevention-Profile / Risk） |
| 已使用 `ErrorState` 的视图 | 2 处 |
| `{ silent: true }` 静默调用点 | 16 处 |
| `errorMessages.ts` 字典键数 | 23（`errorMessages.spec.ts` 有 18 键精确相等断言） |
| 403/404 区分处理 | **0**（grep 无结果） |
| 手写 cancel 判断（绕过统一短路） | 至少 2 处（`goals/Index.vue:157`、`health/Alerts.vue:224`） |

## 未作答的处理说明

四个灰区问题均返回空。按工作流规则「不视为拒绝、不臆造偏好」，全部按**推荐默认值**落定，并在 CONTEXT.md 的 `<open_questions>` 中显式列出 5 个可回退点，供用户在 plan-phase 阶段一键推翻。
