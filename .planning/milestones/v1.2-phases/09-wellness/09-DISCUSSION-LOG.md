# Phase 9: 保健指标管理页录入对齐 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-05  
**Phase:** 9-保健指标管理页录入对齐  
**Areas discussed:** 心情交互形态, 保健危急确认, 两页面弹窗排版  

---

## 1. 心情交互形态 (WELL-02)

| Option | Description | Selected |
|--------|-------------|----------|
| 1-5星级打分+描述文本（推荐） | 保留并优化 1-5 星级打分（`el-rate`），辅助呈现心情描述文字（很差/较差/一般/较好/很好）及四梯度状态胶囊。 | ✓ |
| 改为统一普通数值框 | 将心情改为普通数值输入框（1-5分），与其它生活方式指标统一输入控件。（丢失了星级评价的人性化与直观性）。 | |

**User's choice:** 1-5星级打分+描述文本（推荐）  
**Notes:** 兼顾主观心理指标特质与四梯度模型的一致性。

---

## 2. 保健危急确认 (WELL-03)

| Option | Description | Selected |
|--------|-------------|----------|
| 保留危急确认防误触（推荐） | 极度异常数值（如严重超负荷饮水、极度睡眠不足等超出安全边界时）在保存前弹出二次确认窗防误触并提示健康风险，确认后正常入库。 | ✓ |
| 仅状态胶囊，不弹窗确认 | 由于生活方式指标无突发生命危险，完全不设危急确认模态窗，仅通过橙色状态胶囊提示。 | |

**User's choice:** 保留危急确认防误触（推荐）  
**Notes:** 有效防止用户多输一个 0 导致的极端生活方式数据污染。

---

## 3. 两页面弹窗排版 (WELL-01, UI-SPEC)

| Option | Description | Selected |
|--------|-------------|----------|
| 统一全局平铺+置顶排版（推荐） | 两个页面弹窗均采用 `append-to-body` 脱离动画包含块，启用 `label-position="top"` 顶置标签排版并常态化挂载 `ReferenceGuideCard`。 | ✓ |
| 保持原有局部排版 | 保持原有局部内联弹窗，不修改排版与挂载位置。（会导致弹窗被卡片层叠上下文局限裁剪）。 | |

**User's choice:** 统一全局平铺+置顶排版（推荐）  
**Notes:** 彻底统一全站所有录入窗口的全局层叠与全宽视觉标准。

---

## Claude's Discretion

- 仪表盘活动环与折线图数据在录入后的平滑刷新体验。
- 历史记录表格列宽的自适应排版。

## Deferred Ideas

- Phase 10: 通用指标表单交互组件抽象与全站编辑模式终极统一。
