# Phase 10: 指标录入通用交互组件封装与全站验证 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-05  
**Phase:** 10-指标录入通用交互组件封装与全站验证  
**Areas discussed:** 组件封装粒度, 编辑提交路由, 全站回归验收  

---

## 1. 组件封装粒度 (REUSE-01)

| Option | Description | Selected |
|--------|-------------|----------|
| 完整通用弹窗组件（推荐） | 提炼通用的 UniversalMetricDialog 组件，通过 category 属性支持健康、保健或全量指标，各大页面完全替换为该通用弹窗，彻底消除三个页面的重复表单代码。 | ✓ |
| 仅提取表单内部表单域 | 仅提炼表单内部字段核心（MetricFormContent.vue），各业务页面依然保留自有的 el-dialog 弹窗外壳。（各页面依然需要重复编写弹窗、按钮与二次确认控制器，代码消除不彻底）。 | |

**User's choice:** 完整通用弹窗组件（推荐）  
**Notes:** 最大程度消灭重复代码，实现真正的单一组件权威源（Single Source of Truth in Component Architecture）。

---

## 2. 编辑提交路由 (REUSE-02)

| Option | Description | Selected |
|--------|-------------|----------|
| 组件内部自适应 API（推荐） | 通用组件内部自动判定属于健康指标（HEALTH）还是保健指标（WELLNESS），自动分流调用 healthApi 或 wellnessApi 的新增/更新接口，父页面零心智负担。 | ✓ |
| 由各父页面处理提交 | 通用组件只负责表单验证与数据导出，具体调用哪个 API 的 update 接口完全由各父页面自定义处理。（导致每个页面依然需要维护繁琐的提交逻辑）。 | |

**User's choice:** 组件内部自适应 API（推荐）  
**Notes:** 开箱即用，父页面只需挂载组件并监听 `@success` 刷新列表。

---

## 3. 全站回归验收 (REUSE-01, REUSE-02)

| Option | Description | Selected |
|--------|-------------|----------|
| 全量录入入口回归矩阵（推荐） | 构建 UniversalMetricDialog 综合测试矩阵，并对全站录入入口（快捷录入、健康管理、预防保健、生活方式）执行全量回归，确保重构零破坏、零回归。 | ✓ |
| 仅测试通用组件 | 仅针对新提炼的通用组件进行单元测试。（缺乏对各大业务页面替换后的端到端行为保障）。 | |

**User's choice:** 全量录入入口回归矩阵（推荐）  
**Notes:** 确保重构后全站稳定性达到 100% 生产级标准。

---

## Claude's Discretion

- `UniversalMetricDialog.vue` 的目录结构与向后兼容导出机制。
- 弹窗针对不同调用场景的标题（title）智能自适应展示。

## Deferred Ideas

- v1.3+: 将 AI 智能文本解析与 OCR 报告识别对齐到权威元数据注册中心（SYNC-01）。
- v1.3+: 慢性病个性化基线与动态阈值自适应配置。
