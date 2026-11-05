# Phase 8: 健康指标管理页录入与编辑对齐 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-05  
**Phase:** 8-健康指标管理页录入与编辑对齐  
**Areas discussed:** 指标下拉数据源, 血压编辑模式, 列表状态呈现与弹窗布局  

---

## 1. 指标下拉数据源 (HEALTH-01)

| Option | Description | Selected |
|--------|-------------|----------|
| 全量元数据动态生成（推荐） | 顶部筛选栏与添加/编辑弹窗完全由 `useMetricMetaStore().healthMetrics` 动态循环渲染全量 16 项医学指标，彻底清除类内写死的 7 项 `el-option` 与私有 `metricUnitMap`。 | ✓ |
| 仅弹窗动态，筛选栏保留简版 | 仅弹窗改为动态，筛选栏保留写死的 7 项常用选项。（导致全量历史指标无法通过筛选栏检索）。 | |

**User's choice:** 全量元数据动态生成（推荐）  
**Notes:** 彻底保障全系统 16 项医学指标均可在主管理页查看、筛选与录入。

---

## 2. 血压编辑模式 (HEALTH-02)

| Option | Description | Selected |
|--------|-------------|----------|
| 新增协同+编辑单项精准适配（推荐） | 新增时支持选择“血压”进行高低压双字段并排协同录入与一键对调；编辑已有单条历史记录时，根据当前记录类型精准加载对应数值并绑定该项元数据与四梯度模型。 | ✓ |
| 编辑时也强制双向协同绑定 | 编辑单条记录时强制查询关联的另一条记录并双向修改。（实现复杂度高且历史记录中可能存在非成对录入数据）。 | |

**User's choice:** 新增协同+编辑单项精准适配（推荐）  
**Notes:** 兼顾录入便捷性与历史单项修改的稳健性。

---

## 3. 列表状态呈现与弹窗布局 (HEALTH-03, UI-SPEC)

| Option | Description | Selected |
|--------|-------------|----------|
| 弹窗四梯度+全局平铺+表格保持简洁（推荐） | 弹窗内完整集成四梯度状态胶囊、`ReferenceGuideCard` 与 `CriticalConfirmDialog` 二次确认；弹窗添加 `append-to-body` 脱离卡片层叠上下文，标签置顶排版；数据表格保持既有趋势与数值排版。 | ✓ |
| 表格与弹窗全量呈现四梯度胶囊 | 数据表格的每一行数值也打上四梯度颜色微胶囊。（可能导致长表格视觉过载，影响阅读效率）。 | |

**User's choice:** 弹窗四梯度+全局平铺+表格保持简洁（推荐）  
**Notes:** 保持主表格整洁易读，将精细化医学引导集中在填报与编辑聚焦区。

---

## Claude's Discretion

- 列表表格列宽自适应。
- 趋势选择与四梯度校验的协同排版。

## Deferred Ideas

- Phase 9: 保健指标管理页（`prevention/Metrics.vue` 与 `wellness/Dashboard.vue`）录入对齐。
- Phase 10: 通用指标表单组件抽取与全站体验一致性验证。
