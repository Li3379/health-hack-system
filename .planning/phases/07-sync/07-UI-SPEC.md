---
phase: "07"
slug: "07-sync"
status: draft
shadcn_initialized: false
preset: none
created: "2026-11-05"
---

# Phase 07 — 全渠道校验对齐与跨端测试验证 — UI Design Contract

> Visual and interaction contract for frontend metric entry dialogs. Extends Phase 06 design contract across health and wellness management views.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none |
| Preset | not applicable |
| Component library | Element Plus 2.14.0 (Vue 3) |
| Icon library | @element-plus/icons-vue |
| Font | DM Sans, Noto Sans SC, JetBrains Mono |

---

## Target Views & Components

1. `src/views/health/Metrics.vue` (健康指标管理页 — 添加/编辑指标弹窗)
2. `src/views/prevention/Metrics.vue` (预防保健指标管理页 — 添加保健指标弹窗)
3. `src/views/wellness/Dashboard.vue` (生活方式看板 — 添加保健指标弹窗)

---

## Component Inventory

| Component | Source | Purpose |
|-----------|--------|---------|
| `ElDialog` | `element-plus` | 统一 560px 宽度弹窗容器 (`width="560px"`, `destroy-on-close`) |
| `ElForm` / `ElFormItem` | `element-plus` | 表单布局与校验触发，`label-position="top"` |
| `ElSelect` / `ElOption` | `element-plus` | 动态从 `useMetricMetaStore` 获取指标下拉选项（废除写死列表） |
| `ElInputNumber` | `element-plus` | 数值输入控件，绑定动态 `:step`、`:precision` 与 `:placeholder` |
| `ElRate` | `element-plus` | 心情专属 1-5 星级打分控件 (`show-text`, `['很差', '较差', '一般', '较好', '很好']`) |
| `ElRow` / `ElCol` | `element-plus` | 血压收缩压/舒张压 12:12 双字段协同栅格排版 |
| `ElAlert` | `element-plus` | 血压倒置警示横幅与“一键对调高低压数值”按钮 |
| `ElTag` | `element-plus` | 四梯度状态胶囊微标签 (`success`, `warning`, `danger`) |
| `ReferenceGuideCard` | `@/views/data-input/components/ReferenceGuideCard.vue` | 表单底部常态化展示健康参考标准解读卡片 |
| `CriticalConfirmDialog` | `@/views/data-input/components/CriticalConfirmDialog.vue` | 急危重症指标数值提交时的二次确认模态窗 |

---

## Interaction Contracts

### 1. Non-Destructive Input (非破坏性录入)
- 彻底废除任何失焦重置逻辑（严禁在 `@blur` 中清空为 0 或 `undefined`）。
- 无论用户输入处于警告区间、危急区间还是超限区间，输入框内数值均完整保留。

### 2. Four-Tier Status Badges (四梯度状态胶囊)
- **Tier 1 (Normal)**: 正常参考区间内，展示绿色胶囊及对号图标。
- **Tier 2 (Warning)**: 轻中度异常，展示黄色胶囊与警示文案，允许保存。
- **Tier 3 (Critical)**: 急危重症病理数据，展示橙色胶囊；点击保存时弹出 `CriticalConfirmDialog` 二次确认，确认后允许入库。
- **Tier 4 (Hard Bound)**: 超出生理极限数值，展示红色错误文案，阻止提交入库，但原样保留输入。

### 3. Blood Pressure Dual-Field Coordination (血压双字段协同)
- 选取“血压”指标时，表单展示收缩压 (高压) 与舒张压 (低压) 两个输入框。
- 当高压低于低压时，展示黄色警示条，并提供“对调高低压数值”按钮，点击后原子交换数值并重新校验。

### 4. Mood Special Input (心情星级评分)
- 保健指标中当 `metricKey === 'mood'` 时，隐藏普通数值框，无缝切换为 5 星级评分打分。

---

## Form Sizing & Spacing Scale

- Dialog Width: `560px`
- Form Gutter: `16px`
- Form Item Margin Bottom: `18px`
- Reference Card Margin Top: `16px`
- Dialog Footer: flex end layout, 取消 / 确定 按钮间距 `12px`

---

*Contract defined: 2026-11-05 for Phase 07*
