---
phase: "12"
slug: "threshold-wizard"
status: approved
shadcn_initialized: false
preset: none
created: "2026-11-07"
reviewed_at: "2026-11-08"
---

# Phase 12 — UI Design Contract

> 阈值配置向导的视觉与交互契约。由 gsd-ui-researcher 生成，gsd-ui-checker 校验。
> **本契约在 Phase 11 的错误呈现契约之上扩展，不重新定义错误色与错误间距。**

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none（非 shadcn 项目 — 项目已有自成体系的 Element Plus + CSS token 系统） |
| Preset | not applicable |
| Component library | `element-plus@2.14.0`（已安装，本阶段零新增依赖） |
| Icon library | `@element-plus/icons-vue@2.3.2` |
| Font | `--font-ui`: `'DM Sans', 'Noto Sans SC', system-ui, …`（theme.css:68） |

**为什么不是 shadcn：** REQUIREMENTS.md `## Out of Scope` 明确禁止引入第二套 UI 库。本契约只使用已安装的 Element Plus，并复用 `theme.css` 中已有的 CSS token。

---

## Component Inventory

> 从**已安装的包**枚举，不凭记忆。

`node -e "const fs=require('fs');const b='node_modules/element-plus/es/components';console.log(fs.readdirSync(b).filter(n=>fs.existsSync(b+'/'+n+'/index.mjs')).length)"` — **91 components** — element-plus@2.14.0 — 2026-11-07.

（`readdirSync` 直接返回 124 个目录，其中只有 91 个含 `index.mjs` 组件入口；上面记录的是过滤后的可复现命令。`@element-plus/icons-vue` 版本 **2.3.2**，由 `element-plus/package.json` 的 peer 约束解析。）

下面只列出本阶段会用到的子集，**不是封闭白名单**；执行者可继续使用该库导出的任何组件。

| Component | Import path | 本阶段用途 |
|-----------|-------------|-----------|
| `ElSteps` / `ElStep` | `element-plus`（auto-import） | 三步向导的步骤指示器。范式照抄 `DeviceConfigWizard.vue:4-9` |
| `ElDialog` | `element-plus` | 向导容器（`width="760px"`，见 Component Contract 1） |
| `ElForm` / `ElFormItem` | `element-plus` | 第 2 步的四项输入；`prop` + `:error` 接 Phase 11 的 `fieldErrors` |
| `ElInputNumber` | `element-plus` | 四项阈值输入；`:min/:max` 来自元数据 `hardMin/hardMax`，`:precision/:step` 来自元数据 |
| `ElSelect` / `ElOption` / `ElOptionGroup` | `element-plus` | 第 1 步的指标选择器（HEALTH / WELLNESS 分组） |
| `ElTag` | `element-plus` | **不得用于「推荐」徽标** —— `theme.css:515-521` 用 `!important` 把 `.el-tag` 锁成 `500 11px/1.4 var(--font-mono)` + 全圆角 + accent 文字色，会引入第 3 种字重、第 5 种字号，且 accent 文字在浅色只有 3.74:1。徽标改用自绘 `<span class="recommend-badge">`（Component Contract 3）。`ElTag` 仅可用于无文字语义的状态点（本阶段实际不用）。 |
| `ElButton` | `element-plus` | 向导导航（上一步 / 下一步 / 保存）、「恢复推荐值」、「打开编辑」 |
| `ElAlert` | `element-plus` | 仅作为 `FormErrorSummary` 内部的标题容器（Phase 11 既有用法） |
| `ElDivider` | `element-plus` | 第 2 步的高值区 / 低值区分隔（沿用现有页面） |
| `ElTooltip` | `element-plus` | 四项输入的解释文案在窄屏下的折叠提示 |
| `ElDescriptions` / `ElDescriptionsItem` | `element-plus` | 空状态中展示系统默认规则的实际数值 |
| `ElEmpty` | `element-plus` | **不使用** —— 空状态必须是教学型，见 THR-07 决策与 Component Contract 6 |
| `ElResult` | `element-plus` | **不使用** —— 错误态统一走 Phase 11 的 `ErrorState.vue` |
| `ElSegmented` | `element-plus`（2.7.0+，2.14.0 已具备） | **不使用** —— 单一推荐值档位，无分段控件需求 |

**复用而非重建的既有组件：**

| Component | 路径 | 用途 |
|-----------|------|------|
| `FormErrorSummary` | `src/components/common/FormErrorSummary.vue` | 第 2 步顶部的 GOV.UK 汇总块（零修改复用） |
| `ErrorState` | `src/components/common/ErrorState.vue` | 页面级加载失败（元数据拉取失败等） |
| `showError` | `src/utils/showError.ts` | 唯一 toast 出口；`ERROR_CODE_ACTIONS` 提供「打开编辑」标签 |
| `useFormServerErrors` | `src/composables/useFormServerErrors.ts` | `setErrors()` 投影客户端校验；`apply()` 投影服务端 `fieldErrors` |
| `useMetricMetaStore` | `src/stores/metricMeta.ts` | `healthMetrics` / `wellnessMetrics` / `getDefinition()` |
| `ReferenceGuideCard` | `src/views/data-input/components/ReferenceGuideCard.vue` | 视觉语言参照（`el-card shadow="never"` + 图标 + 标签 + 提示文本） |

---

## Spacing Scale

声明的间距值**全部是 4 的倍数**，且每个 token 都绑定到本契约中某个具体 CSS 声明。

| Token | Value | Usage（绑定到具体声明） |
|-------|-------|-------|
| xs | 4px | 四项输入的说明文字与输入框之间的垂直间距（`.threshold-field__hint { margin-top: 4px }`）；「推荐」徽标与输入框的水平间距（`.threshold-field__header { gap: 4px }`） |
| sm | 8px | 四区预览条各色带之间的间隙（`.zone-bar { gap: 8px }`）；步骤指示器到内容的间距（`.wizard-steps { margin-bottom: 8px }`）；徽标内边距（`.recommend-badge { padding: 0 8px }`） |
| sm-3 | 12px | 四区预览条的上下留白（`.zone-bar { margin: 12px 0 }`）；四项输入之间的行间距（`.threshold-field + .threshold-field { margin-top: 12px }`）；确认语句块的内部 padding（`.confirm-sentence { padding: 12px 16px }`）；确认块标题到语句的间距（`.confirm-block__title { margin-bottom: 12px }`） |
| md | 16px | 向导内容区左右内边距（`.wizard-body { padding: 0 16px 32px }` 的水平分量）；底部按钮栏的上间距（`.wizard-footer { margin-top: 16px }`）；`FormErrorSummary` 到第一个字段的间距（沿用 Phase 11 的 `margin-bottom: 16px`）；确认语句块的左右 padding（`.confirm-sentence { padding: 12px 16px }`） |
| lg | 24px | 步骤指示器与第一步内容之间的上方留白（`.wizard-steps :deep(.el-steps) { padding: 24px 0 0 }`）；空状态卡片的左右 padding（`.threshold-empty { padding: 48px 24px }` 的水平分量）；空状态正文到默认规则表的间距（`.threshold-empty__body { margin-bottom: 24px }`）；默认规则表到 CTA 的间距（`.threshold-empty__defaults { margin-bottom: 24px }`）；四区图例与色带之间的间距（`.zone-legend { margin-top: 24px }`） |
| xl | 32px | 空状态图标与标题之间的间距（`.threshold-empty__icon { margin-bottom: 32px }`）；向导内容区底部留白（`.wizard-body { padding: 0 16px 32px }`） |
| 2xl | 40px | 四区预览条的色带高度（`.zone-bar__band { height: 40px }`） |
| 3xl | 48px | 教学型空状态在无数据时的垂直留白（`.threshold-empty { padding: 48px 24px }` 的垂直分量） |

**No exceptions.** 所有间距值都是 4 的倍数。

**两条必须归一化的 Element Plus 默认间距**（否则运行时会渲染 off-grid 值，与上句矛盾）：

| EP 默认 | 归一化目标 | 声明位置 |
|---|---|---|
| `.el-form-item { margin-bottom: 18px }`（`theme-chalk/el-form-item.css`）—— 四项字段用 `el-form-item` 承载 `:error`，默认会额外撑出 18px | `margin-bottom: 0`，垂直节奏完全交给 `.threshold-field` 的 12px | Component Contract 3 |
| `.el-descriptions__table.is-bordered .el-descriptions__cell { padding: 8px 11px }` —— 空状态用 `border` | `padding: 8px 12px` | Component Contract 6 |

> 与 Phase 11 的差异说明：Phase 11 的 `2xl=40px` 用于汇总列表缩进，本阶段把 `40px` 用于色带高度、`3xl=48px` 用于空状态留白。两个契约共用同一套 4 倍数刻度，只是用途不同，**刻度本身不冲突**。

无 off-grid 例外 —— 但有两条**必须覆盖的 Element Plus 默认值**，它们不是间距而是组件几何：

| EP 默认 | 归一化目标 | 声明位置 |
|---------|-----------|---------|
| `.el-steps` 默认无上下留白，紧贴内容 | `padding: 24px 0 0` | Component Contract 1 |
| `.el-step__title` 默认 `font-size: 16px; line-height: 38px` | 改为 `font-size: 14px; line-height: 1.4`（38px 固定行高会撑破步骤条与内容的间距；16px 不在本契约的 4 号字号表内） | Component Contract 1 |
| `.el-step__title.is-process` 默认 `font-weight: 700` | 改为 `600`（本契约只有 400/600 两档字重） | Component Contract 1 |
| `.el-step__title.is-finish` 默认 `color: var(--el-color-primary)`（EP 蓝） | 改为 `var(--text-2)`（Dark 11.30:1 / Light 8.38:1）。**不用 `--accent-cool`** —— 它是文字，浅色主题下仅 3.74:1，不达 AA 正文 4.5:1 | Component Contract 1 |
| `.el-dialog` 默认 `margin-top: 15vh` | 保持（不覆盖） | — |

---

## Typography

**恰好 4 个字号、恰好 2 个字重**，与 Phase 11 / Phase 10 契约一致。

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 14px | 400 (regular) | 1.5 |
| Label | 12px | 400 (regular) | 1.4 |
| Heading | 18px | 600 (semibold) | 1.3 |
| Display | 24px | 600 (semibold) | 1.2 |

> **必须覆盖的 `theme.css` 全局 `!important`** —— 以下三条既有规则会绕过本契约的字号/字重表，必须在本阶段样式里显式处理，否则运行时会出现第 5 种字号与第 3 种字重：

| theme.css 规则 | 实际值 | 影响本阶段的表面 | 处理方式 |
|---|---|---|---|
| `.el-input__inner { font: 400 15px/1.5 … !important }`（`theme.css:489-492`） | 15px | 四项 `el-input-number` 的数值 | 在组件 `<style scoped>` 用同等 `!important` 覆盖为 `400 14px/1.5`（Component Contract 3） |
| `.el-form-item__label { font: 500 14px/1.5 … !important }`（`theme.css:634-637`） | **500 字重** | 若四项用 `el-form-item` 的 `label` 属性则命中 | 四项字段**不使用 `el-form-item` 的 `label` 属性**；标签由自绘 `.threshold-field__label` 承担（600 字重）。`el-form-item` 只用于承载 `:error` 内联错误 |
| `.el-tag { font: 500 11px/1.4 … !important }`（`theme.css:515-521`） | 11px / 500 | 「推荐」徽标 | **不使用 `ElTag`**；徽标自绘 `.recommend-badge`（Component Contract 3） |
| `.el-button--primary { font: 500 14px/1 … !important }`（`theme.css:437-445`） | **500 字重 / 行高 1** | 全部主按钮（`添加阈值` / `使用推荐阈值` / `下一步` / `保存阈值`） | 在页面 `<style scoped>` 用同等 `!important` 覆盖为 `600 14px/1.5`（Component Contract 9） |
| EP `.el-button { --el-button-font-weight: 500 }` | **500 字重** | 次按钮（`上一步` / `放弃设置` / `恢复推荐值` / `取消`） | 同上覆盖为 `400`（Component Contract 9） |
| `.el-step__icon-inner { font-weight: 700 }` | **700 字重** | 步骤条序号数字 | 覆盖为 `600`（Component Contract 1） |
| `.el-input-number__decrease / __increase { font-size: 13px }` | **13px** | 四项数值输入的加减按钮字形 | 覆盖为 `14px`（Component Contract 3） |
| `.el-descriptions__cell { font-size: 14px; line-height: 23px }` + `.is-bordered-label { font-weight: 700 }` | 14px / 700 / 23px | 空状态默认规则表的标签与内容 | 覆盖标签为 `12px/400/1.4`、内容为 `14px/400/1.5`（Component Contract 6） |

**本阶段各表面的应用：**

| Surface | Size / Weight / LH | 依据 |
|---------|--------------------|------|
| 步骤条标题（`el-step__title`） | 14px / 400 未激活、600 当前步 / 1.4 | Body + 字重区分状态 |
| 第 1 步分组标题（健康指标 / 保健指标） | 14px / 600 / 1.4 | Body + semibold |
| 指标选项文本 `收缩压 (mmHg)` | 14px / 400 / 1.5 | Body |
| 四项字段标签 `警告高值` | 14px / 600 / 1.4 | Body + semibold（比说明重一档，形成层级） |
| 四项字段说明 `高于此值发出黄色提醒（mmHg）` | 12px / 400 / 1.4，色 `--text-2`（AA 达标；`--text-3` 浅色仅 3.97:1 不可用） | Label |
| 「推荐」徽标 | 12px / 400 / 1.4 | Label |
| 四区预览条的区段标签 `严重低` | 12px / 400 / 1.4 | Label |
| 确认语句 `当收缩压 ≥ 140 时发黄色提醒…` | 14px / 400 / 1.5 | Body |
| 确认语句中的数值强调 | 14px / 600 / 1.5 | 与正文同尺寸，只加粗 |
| 教学型空状态标题 | 18px / 600 / 1.3 | Heading |
| 教学型空状态正文 | 14px / 400 / 1.5，色 `--text-2` | Body |
| 空状态中默认规则表格的标签 | 12px / 400 / 1.4，色 `--text-2` | Label |
| 空状态中默认规则表格的数值 | 14px / 400 / 1.5，色 `--text-1`，等宽字体 `--font-mono` | Body |
| 内联校验错误 | 12px / 400 / **1.4**（Phase 11 全局覆盖已生效） | Label |
| GOV.UK 汇总标题与条目 | 14px / 600 / 1.4 与 14px / 400 / 1.5 | 沿用 Phase 11，**不重定义** |

**字重只有 400 与 600 两档**，不引入 500/700。

---

## Color

所有色值均来自 `theme.css` 既有 token 或本阶段新增 token；**对比度全部实测**（WCAG 2.1 相对亮度公式）。

### 60 / 30 / 10 分配（本阶段表面）

| 占比 | 角色 | Dark | Light | 本阶段占用位置 |
|------|------|------|-------|---------------|
| 60% | 主导 / 背景 | `--bg` `#0a0b0e` | `--bg` `#f5f7fa` | 对话框外部背景、色带之间的 8px 间隙、页面底色 |
| 30% | 次主导 / 表面 | `--surface-1` `#111217`、`--surface-2` `#171921` | `#ffffff`、`#f0f2f5` | `ElDialog` 内容面、`el-table`、确认语句块底、空状态默认规则表底 |
| 10% | 强调 | `--accent-cool` `#5EEAD4` | `--accent-cool` `#0d9488` | **仅限**：步骤条当前步骤的圆形序号图标（`el-step__head.is-process` 的 border/color）、输入框聚焦边框与焦点环、`.recommend-badge` 的背景（经 `--accent-cool-soft`，非直接色值）、主按钮（`el-button type="primary"` 由 EP 主题变量驱动） |

**强调色绝不用于：** 正文、标签文字、字段说明、步骤标题文字、「推荐」徽标文字、四区色带填充（色带有自己的三色板）。

### 语义色（沿用，不新增）

| Role | Dark value | Light value | 对比度（实测） |
|------|-----------|-------------|---------------|
| **破坏性操作（Destructive）** | `#f56c6c` | `#b91c1c` | **仅用于**：删除按钮文字、校验失败的内联错误与汇总标题、删除确认框图标。Dark 6.45:1 on `#111217`；Light 6.47:1 on `#ffffff` |
| 破坏性操作底纹 | `rgba(245,108,108,.12)` | `#fef0f0` | 四区预览条的 CRITICAL 色带、`FormErrorSummary` 整块底色 |
| 正文主色 | `#ebecef` | `#1a1c23` | Dark 15.83:1；Light 17.01:1 |
| 正文次色 | `#c6c9d2` | `#4a4d5c` | Dark 11.30:1；Light 8.38:1 |
| 辅助文字 | `#8d909c` | `#7c7f8e` | Dark 5.88:1；Light 3.97:1 —— **Light 不达 AA 正文 4.5:1**，本阶段只允许用于纯装饰（如空状态大图标着色），**禁止承载说明文字**；所有说明文字改用 `--text-2` |
| 强调（accent-cool） | `#5EEAD4` | `#0d9488` | Dark 12.64:1；Light 3.74:1（**仅用于非文字图形**，见下方约束） |
| 表面 1（卡片） | `#111217` | `#ffffff` | — |
| 背景 | `#0a0b0e` | `#f5f7fa` | — |

**accent-cool 的使用约束（实测得出）：** Light 主题下 `#0d9488` 在白底只有 **3.74:1**，不达 AA 正文的 4.5:1。因此 accent-cool **不得用于文字**，只允许用于：
- 步骤条当前步的**圆形序号图标边框与数字**（`el-step__head.is-process` 的 `border-color` / `color`）—— 这是图形 + 数字，≥3:1 通过
- 输入框聚焦边框与焦点环（`theme.css:485-488` 已用 `!important` 绑定，无需本阶段声明）
- 「推荐」徽标的**背景**（经 `--accent-cool-soft`），文字用 `--text-1`

**步骤高亮的实际 CSS 绑定（对应上面第一条）：**

```css
.wizard-steps :deep(.el-step__head.is-process) {
  color: var(--accent-cool);           /* 当前步序号数字 */
  border-color: var(--accent-cool);    /* 序号圆圈边框 */
}
```

### 新增：四区预览条色板

四区预览条是本阶段唯一新增的色板。**文字一律不用彩色，彩色只用于色带填充**，这样可绕开「绿色在白底只有 2.54:1」的问题。

| Zone | Dark band fill | Light band fill | 对表面对比度 |
|------|---------------|-----------------|-------------|
| 严重低 / 严重高（CRITICAL） | `rgba(245,108,108,0.12)` → 实算 `#2c1d21` | `#fef0f0` | Dark 1.28:1 / Light 1.13:1（**仅作底色，不单独承载信息**） |
| 警告低 / 警告高（WARNING） | `rgba(251,146,60,0.12)` → `#2d211b` | `#fdf6ec` | Dark 1.20:1 / Light 1.08:1（同上） |
| 正常（NORMAL） | `rgba(94,234,212,0.12)` → `#1a2c2e` | `#f0f9eb` | Dark 1.35:1 / Light 1.12:1（同上） |

**关键的可用性设计（因色带本身对比度不足 3:1）：**
色带**不靠颜色单独传达含义**。每个色带内部必须有**文字标签**，且标签用高对比文字色：

| Zone label 文字色 | Dark | Light | 实测对比度 |
|------------------|------|-------|-----------|
| 正文主色 | `#ebecef` | `#1a1c23` | Dark 13.62 / 13.22 / 12.31:1；Light 15.34 / 15.86 / 15.76:1 |

这满足 WCAG 1.4.1（不依赖单一颜色传达信息）与 1.4.3（文字对比度）。

**四区色带之间的分隔：** 相邻色带之间用 `8px` 间隙（`gap: 8px`）而非边框，间隙露出页面背景 `--bg`，形成明确的视觉分隔，不依赖色带间对比度（实测相邻色带对比度仅 1.00–1.36:1，靠颜色区分不可行）。

**当前值标记：** 每个色带下方显示该档位当前的边界值（如警告高值色带显示 `≥ 140`），用 `--text-1` 14px / 400 / 1.5，等宽字体。这样即使用户分不清颜色，也能通过数值确认意图。

### 推荐徽标

| Property | Value |
|----------|-------|
| 背景 | `--accent-cool-soft`（Dark `rgba(94,234,212,.12)` / Light `rgba(13,148,136,.08)`） |
| 文字 | `--text-1`（Dark `#ebecef` 13.62:1 / Light `#1a1c23` 15.34:1 on 该底纹） |
| 边框 | 无 |
| 圆角 | `--radius-sm`（6px） |

**不用** `--accent-cool` 作徽标文字色 —— Light 主题下仅 3.74:1，不达 AA。

---

## Component Contract 1 — 向导容器与步骤条

```
容器：ElDialog，width="760px"（当前 600px 放不下四项解释 + 预览条）
       :close-on-click-modal="false"  ← 防误点遮罩丢失输入
       :before-close="handleBeforeClose"  ← 头部 X 与 Esc 都走这里
       destroy-on-close               ← 每次打开重置状态

步骤条：ElSteps :active="currentStep" finish-status="finish" align-center
        <el-step title="选择指标" /> <el-step title="设置阈值" /> <el-step title="确认保存" />
        编辑模式跳过第 1 步：currentStep 初始为 1，且第 1 个 el-step 加
        class="is-skipped"，视觉上置灰（opacity: .45），title 改为「指标已锁定」
```

**必须声明的 CSS**（`<style scoped>` 内，用 `:deep()` 穿透 EP 内部类）：

```css
.wizard-steps {
  margin-bottom: 8px;           /* sm */
}
.wizard-steps :deep(.el-steps) {
  padding: 24px 0 0;            /* lg — 覆盖 EP 无上下留白的默认 */
}
.wizard-steps :deep(.el-step__title) {
  font-size: 14px;              /* 覆盖 EP 的 16px → Body */
  line-height: 1.4;             /* 覆盖 EP 的固定 38px 行高 */
}
.wizard-steps :deep(.el-step__title.is-process) {
  font-weight: 600;             /* 覆盖 EP 的 700 → 本契约只有 400/600 两档 */
}
/* 已完成步骤：finish-status="finish" 使 EP 输出 is-finish 类（EP 默认值是 "finish"；
   写成 "success" 会得到 is-success 并套用 EP 的 #67c23a，在浅色白底仅 2.2:1）。
   这里同时覆盖 is-finish 与 is-success 两个选择器，防止将来有人改回 success。 */
.wizard-steps :deep(.el-step__title.is-finish),
.wizard-steps :deep(.el-step__title.is-success) {
  color: var(--text-2);         /* 文字必须 ≥4.5:1；accent-cool 浅色仅 3.74:1 不可用 */
}
.wizard-steps :deep(.el-step__head.is-process) {
  color: var(--accent-cool);           /* 当前步序号数字（图形，≥3:1 通过） */
  border-color: var(--accent-cool);    /* 序号圆圈边框 */
}
/* EP 的步骤序号默认 700 字重，会引入本契约之外的第 3 种字重。 */
.wizard-steps :deep(.el-step__icon-inner) {
  font-weight: 600;
}
.wizard-steps :deep(.el-step.is-skipped) {
  opacity: 0.45;
}
.wizard-body {
  padding: 0 16px 32px;         /* md 左右 / xl 底部 */
  min-height: 360px;            /* 防步骤切换时对话框高度跳动 */
}
.wizard-footer {
  margin-top: 16px;             /* md */
  display: flex;
  justify-content: flex-end;
  gap: 8px;                     /* sm */
}
```

**步骤可回退：** 「上一步」按钮常驻（第 1 步隐藏）。**不允许点击步骤条直接跳转** —— 第 1 步之后指标已锁定，跨步跳转会绕过表单校验。步骤条仅作进度指示。

**未保存内容保护（`handleBeforeClose`）：**

```ts
function handleBeforeClose(done: () => void) {
  // 只在「用户真的输入过东西」时拦截，避免每次打开就弹确认。
  const dirty =
    form.metricKey !== '' &&
    (editingId !== null || !isEqualToRecommended(form))
  if (!dirty || confirmedLeave) return done()
  ElMessageBox.confirm(ERROR_MESSAGES['dialog.unsavedConfirm'], '提示', {
    type: 'warning',
    confirmButtonText: '放弃设置',
    cancelButtonText: '继续编辑'
  })
    .then(() => { confirmedLeave = true; done() })
    .catch(() => { /* 用户选择继续编辑：零提示，不关闭 */ })
}
```

- 底部「放弃设置」按钮与头部 X / Esc **共用** `handleBeforeClose`
- `ElMessageBox` 的取消走 `catch`，**不产生任何 toast**（Phase 11 D-12：取消零提示）

**焦点管理：** 每次步骤切换后，把焦点移到该步第一个可聚焦控件：
- 进入第 1 步 → `el-select` 内部 input
- 进入第 2 步 → 第一个 `el-input-number` 的 input
- 进入第 3 步 → 确认语句容器 `<div tabindex="-1" role="status">`（读屏播报确认语句）
实现方式：`await nextTick()` 后调用 `ref.focus()`，不自动滚动（对话框内已在视口）。

---

## Component Contract 2 — 指标选择器（第 1 步）

```html
<el-select v-model="form.metricKey" placeholder="请选择指标" filterable>
  <el-option-group label="健康指标">
    <el-option
      v-for="m in alertableHealthMetrics"
      :key="m.canonicalKey"
      :label="`${m.displayName} (${m.unit})`"
      :value="m.canonicalKey"
    />
  </el-option-group>
  <el-option-group label="保健指标" v-if="alertableWellnessMetrics.length" />
</el-select>
```

- **选项文案严格为 `显示名 (单位)`**，例：`收缩压 (mmHg)`、`心率 (次/分)`。括号为**半角** `(` `)`，与元数据 `unit` 字段直接拼接，中间一个半角空格。
- **空组隐藏**：`v-if` 控制 `el-option-group` 是否渲染（当前 WELLNESS 无可告警指标，实际只显示 HEALTH 组）。
- `filterable` 允许键盘输入过滤——6 个以上选项时必要。
- **加载态**：元数据 store 首次加载时用 `FALLBACK_METRIC_META` 立即渲染（store 初始化即深拷贝该常量），因此**不出现 loading 骨架**。`fetchMetadata()` 静默失败也自动降级到 fallback（Phase 5 既定行为），**不弹 toast、不显示错误态**。
- **若 alertable 列表为空**（后端标记全 false 或 fallback 未标注）：显示内联提示 `暂无可配置的告警指标` + 禁用「下一步」，不用 ErrorState（这不是失败，是数据状态）。

**选定指标后的行为：**
1. 四项阈值立即写入推荐值（来自后端下发；缺失时为 `null`）
2. 四项各自显示「推荐」徽标
3. 出现「恢复推荐值」按钮（`el-button link type="primary"`）
4. 「下一步」变为可用

---

## Component Contract 3 — 四项阈值输入（第 2 步）

每项输入的结构（以警告高值为例）：

```html
<div class="threshold-field">
  <div class="threshold-field__header">
    <span class="threshold-field__label">警告高值</span>
    <span v-if="form.warningHigh === recommended.warningHigh" class="recommend-badge">推荐</span>
  </div>
  <el-input-number
    v-model="form.warningHigh"
    :min="meta.hardMin" :max="meta.hardMax"
    :precision="meta.precision" :step="meta.step"
    controls-position="right"
    style="width: 100%"
  />
  <p class="threshold-field__hint">高于此值发出黄色提醒（{{ meta.unit }}）</p>
</div>
```

**必须声明的 CSS：**

```css
.threshold-field + .threshold-field {
  margin-top: 12px;             /* sm-3 */
}
/* EP 的 .el-form-item 默认 margin-bottom: 18px（off-grid），且会叠加在 12px 之上。
   垂直节奏统一交给 .threshold-field，form-item 只保留 :error 的内联错误职责。 */
.threshold-field :deep(.el-form-item) {
  margin-bottom: 0;
}
/* EP 的加减按钮字形是 13px（第 5 种字号），归一到 14px Body。 */
.threshold-field :deep(.el-input-number__decrease),
.threshold-field :deep(.el-input-number__increase) {
  font-size: 14px;
}
.threshold-field__header {
  display: flex;
  align-items: center;
  gap: 4px;                     /* xs */
  margin-bottom: 4px;           /* xs */
}
.threshold-field__label {
  font-size: 14px;              /* Body */
  font-weight: 600;             /* semibold，比说明重一档；theme.css 的 500 只作用于 el-form-item__label，此处是自绘 span，不受影响 */
  line-height: 1.4;
  color: var(--text-1);
}
/* theme.css:489-492 用 !important 把 .el-input__inner 锁成 15px，
   会让四项数值脱离本契约的 14px Body 角色。必须用同等优先级覆盖。 */
.threshold-field :deep(.el-input__inner) {
  font: 400 14px/1.5 var(--font-ui) !important;
}
.recommend-badge {
  padding: 0 8px;               /* sm 水平 */
  font-size: 12px;              /* Label */
  line-height: 1.4;
  color: var(--text-1);
  background: var(--accent-cool-soft);
  border-radius: var(--radius-sm);
}
.threshold-field__hint {
  margin-top: 4px;              /* xs */
  font-size: 12px;              /* Label */
  line-height: 1.4;
  /* 说明文字是本阶段回应用户「不知道怎么填」的核心内容，必须达 AA 正文标准。
     --text-3 浅色主题实测仅 3.97:1（不达 4.5:1），因此改用 --text-2：
     Dark #c6c9d2 11.30:1 / Light #4a4d5c 8.38:1。 */
  color: var(--text-2);
}
```

**四项的通俗说明文案（单位来自元数据 `unit`，此处以 mmHg 为例，实现时模板插值）：**

| 字段 | 说明文案 |
|------|---------|
| 严重高值 | `高于此值发出红色紧急提醒（{unit}）` |
| 警告高值 | `高于此值发出黄色提醒（{unit}）` |
| 警告低值 | `低于此值发出黄色提醒（{unit}）` |
| 严重低值 | `低于此值发出红色紧急提醒（{unit}）` |

这直接回答用户的原始诉求「我点击添加后不知道怎么填」——不再是裸的临床术语。

**数值精度：** `el-input-number` 的 `:precision` 使用元数据的 `precision` 字段（心率 `0`、血压 `0`、血糖 `1`、体温 `1`），而非当前硬编码的 `2`。`:step` 同理。

**层级校验的呈现（Component Contract 3 的一部分）：**

```css
/* 违反层级的两个字段同时标红，复用 Phase 11 的错误 token */
.threshold-field.is-invalid .threshold-field__label {
  color: var(--hhs-color-error-text);
}
/* 内联错误由 el-form-item :error 渲染，Phase 11 全局样式已设
   line-height: 1.4 + padding-top: 4px + 错误色，无需重复声明 */
```

- 校验在每次输入时（`@change` 后 debounce 0，即时）执行
- 违反 `criticalLow < warningLow < warningHigh < criticalHigh` 时：
  - 「下一步」/「保存」**disabled**
  - **两个**涉及字段下各挂一条中文内联错误
  - 第 2 步顶部出现 `FormErrorSummary` 汇总块（零 toast）
- 违反 `hardMin`/`hardMax` 时：`el-input-number` 的 `:min/:max` 已在输入层拦截，无需额外提示（用户无法输入越界值）
- **不清空任何已输入内容**（D-12）

**校验错误文案（成对出现，同一句挂到两个字段上）：**

| 违反条件 | 文案 |
|---------|------|
| `warningLow >= warningHigh` | `警告低值必须小于警告高值，请调整其中一个` |
| `criticalLow >= warningLow` | `严重低值必须小于警告低值，请调整其中一个` |
| `warningHigh >= criticalHigh` | `警告高值必须小于严重高值，请调整其中一个` |

---

## Component Contract 4 — 四区预览条

```
┌─────────┬─────────┬─────────┬─────────┬─────────┐
│ 严重低  │ 警告低  │  正常   │ 警告高  │ 严重高  │
│  ≤ 40   │ ≤ 50    │ 50–100  │ ≥ 100   │  ≥ 120  │
└─────────┴─────────┴─────────┴─────────┴─────────┘
（色带之间 8px 间隙，露出页面背景形成分隔）
```

```html
<div class="zone-bar" role="img" :aria-label="zoneBarAriaLabel">
  <div class="zone-bar__band zone-bar__band--critical">
    <span class="zone-bar__name">严重低</span>
    <span class="zone-bar__value">{{ fmt(form.criticalLow) }}</span>
  </div>
  <div class="zone-bar__band zone-bar__band--warning">…</div>
  <div class="zone-bar__band zone-bar__band--normal">…</div>
  <div class="zone-bar__band zone-bar__band--warning">…</div>
  <div class="zone-bar__band zone-bar__band--critical">…</div>
</div>
```

**必须声明的 CSS：**

```css
.zone-bar {
  display: flex;
  gap: 8px;                     /* sm — 色带间隙，露出 --bg 形成分隔 */
  margin: 12px 0;               /* sm-3 上下 */
}
.zone-bar__band {
  flex: 1;
  height: 40px;                 /* 2xl */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  /* 色带内文字一律 --text-1，实测 Dark 12.31–13.62:1 / Light 15.34–15.86:1 */
  color: var(--text-1);
}
.zone-bar__band--critical {
  background: var(--hhs-color-error-tint);
}
.zone-bar__band--warning {
  background: var(--hhs-color-warning-tint);   /* 新增 token，见下 */
}
.zone-bar__band--normal {
  background: var(--hhs-color-normal-tint);    /* 新增 token，见下 */
}
.zone-bar__name {
  font-size: 12px;              /* Label */
  line-height: 1.4;
}
.zone-bar__value {
  font-size: 14px;              /* Body */
  line-height: 1.5;
  font-family: var(--font-mono);
}
.zone-legend {
  margin-top: 24px;             /* lg */
}
```

**新增 token（必须写进 `theme.css` 的 `:root` 与 `[data-theme='light']` 两处）：**

```css
:root {
  /* 四区预览条色带底纹（Phase 12） */
  --hhs-color-warning-tint: rgba(251, 146, 60, 0.12);   /* → 实算 #2d211b on #111217 */
  --hhs-color-normal-tint:  rgba(94, 234, 212, 0.12);   /* → 实算 #1a2c2e on #111217 */
}
[data-theme='light'] {
  --hhs-color-warning-tint: #fdf6ec;
  --hhs-color-normal-tint:  #f0f9eb;
}
```

> `--hhs-color-error-tint` 已存在（Phase 11），直接复用。

**可访问性（关键，因色带本身对比度仅 1.08–1.35:1）：**

- 每个色带**必须**同时显示 `名称` + `数值`，二者都用 `--text-1`（高对比），**不允许只靠颜色区分四区**
- `role="img"` + `aria-label` 提供完整朗读文本，格式：
  `严重低：低于 40；警告低：40 到 50；正常：50 到 100；警告高：100 到 120；严重高：高于 120（mmHg）`
- 未填写的边界（`null`）在 `aria-label` 中读作 `未设置`，色带对应位置显示 `—`

**实时性：** 输入变化时色带数值同步更新（纯 computed，无请求）。

---

## Component Contract 5 — 确认语句（第 3 步）

```html
<div class="confirm-block">
  <h4 class="confirm-block__title">请确认以下告警行为</h4>
  <p ref="confirmRef" tabindex="-1" role="status" class="confirm-sentence">
    当 <strong>{{ meta.displayName }}</strong>
    <template v-if="form.warningHigh != null"> ≥ <strong>{{ form.warningHigh }}</strong> 时发出黄色提醒；</template>
    <template v-if="form.criticalHigh != null"> ≥ <strong>{{ form.criticalHigh }}</strong> 时发出红色紧急提醒；</template>
    <template v-if="form.warningLow != null"> ≤ <strong>{{ form.warningLow }}</strong> 时发出黄色提醒；</template>
    <template v-if="form.criticalLow != null"> ≤ <strong>{{ form.criticalLow }}</strong> 时发出红色紧急提醒。</template>
  </p>
</div>
```

**必须声明的 CSS：**

```css
.confirm-block__title {
  font-size: 18px;              /* Heading */
  font-weight: 600;
  line-height: 1.3;
  color: var(--text-1);
  margin-bottom: 12px;          /* sm-3 */
}
.confirm-sentence {
  padding: 12px 16px;           /* sm-3 / md */
  font-size: 14px;              /* Body */
  line-height: 1.5;
  color: var(--text-1);
  background: var(--surface-2);
  border-radius: var(--radius-md);
}
.confirm-sentence:focus {
  outline: 2px solid var(--accent-cool);   /* 焦点环，非文字，3.74:1 ≥ 3:1 通过 */
  outline-offset: 2px;
}
```

**句式规则：**
- 只用**已填写**的边界生成分句，`null` 边界整个分句省略
- 边界全部为 `null` 时（理论上被校验拦截，不应到达第 3 步），退化为 `尚未设置任何有效阈值`
- 数值用 `<strong>` 加粗（同字号，只加字重）
- 四个边界全填时完整示例：
  `当收缩压 ≥ 140 时发出黄色提醒；≥ 160 时发出红色紧急提醒；≤ 90 时发出黄色提醒；≤ 80 时发出红色紧急提醒。`

---

## Component Contract 6 — 教学型空状态（THR-07）

**明确不用 `ElEmpty`** —— `ElEmpty` 是通用空态（图片 + 「暂无数据」），本阶段要的是**教学**：解释当前生效的默认规则，并给出入口。

```html
<div v-else class="threshold-empty">
  <el-icon :size="48" class="threshold-empty__icon"><InfoFilled /></el-icon>
  <h3 class="threshold-empty__title">尚未设置个性化阈值</h3>
  <p class="threshold-empty__body">
    系统正在使用默认告警规则。设置个性化阈值后，将达到您设定的标准时才发出提醒。
  </p>
  <el-descriptions :column="1" border class="threshold-empty__defaults">
    <el-descriptions-item label="心率">
      正常 60–100 次/分，高于 100 黄色提醒，高于 120 红色提醒
    </el-descriptions-item>
    …
  </el-descriptions>
  <el-button type="primary" @click="openWizard()">使用推荐阈值</el-button>
</div>
```

**必须声明的 CSS：**

```css
.threshold-empty {
  padding: 48px 24px;           /* 3xl 上下 / lg 左右 */
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.threshold-empty__icon {
  margin-bottom: 32px;          /* xl */
  color: var(--text-3);         /* 纯装饰图标，不承载信息，允许 3.97:1 */
}
.threshold-empty__title {
  font-size: 18px;              /* Heading */
  font-weight: 600;
  line-height: 1.3;
  color: var(--text-1);
  margin-bottom: 8px;           /* sm */
}
.threshold-empty__body {
  font-size: 14px;              /* Body */
  line-height: 1.5;
  color: var(--text-2);
  margin-bottom: 24px;          /* lg */
  max-width: 480px;
}
.threshold-empty__defaults {
  margin-bottom: 24px;          /* lg */
  text-align: left;
  width: 100%;
  max-width: 560px;
}
/* EP 的 bordered 单元格 padding 是 8px 11px（off-grid），标签还是 700 字重 14px。
   两处都归一化到本契约的刻度与字号表。 */
.threshold-empty__defaults :deep(.el-descriptions__table.is-bordered .el-descriptions__cell) {
  padding: 8px 12px;            /* sm / sm-3 */
}
.threshold-empty__defaults :deep(.el-descriptions__label.is-bordered-label) {
  font-size: 12px;              /* Label */
  font-weight: 400;
  line-height: 1.4;
  color: var(--text-2);
}
.threshold-empty__defaults :deep(.el-descriptions__content) {
  font-size: 14px;              /* Body */
  font-weight: 400;
  line-height: 1.5;
  color: var(--text-1);
  font-family: var(--font-mono);
}
```

**CTA 文案：** `使用推荐阈值`（不是「添加阈值」—— 空态下推荐值是更具体的行动）。

**与 ErrorState 的分工：** 空态 = 数据为空（成功请求但无记录）；ErrorState = 请求失败。两者互斥，**不混用**（Phase 11 D-02 的同一原则）。

---

## Component Contract 7 — 重复指标提示（THR-08）

复用 Phase 11 已实现的恢复动作机制，**不新建组件**：

```ts
showError(error, 'threshold.duplicate', {
  presentValidation: true,
  action: { onClick: () => openExistingThreshold(existing) }
  // label 由 ERROR_CODE_ACTIONS['resource.exists'] 自动补为「打开编辑」
})
```

**后端文案（`UserThresholdServiceImpl:51` 与 `:87`）：**
`该指标已有阈值，是否改为编辑？`

**TOAST 上呈现为：** `资源已存在: 该指标已有阈值，是否改为编辑？` + 「打开编辑」按钮
（`资源已存在: ` 前缀来自 `GlobalExceptionHandler.handleBusinessException` 的 `errorCode` 中文映射，Phase 11 已定，不属本阶段改动范围。）

**切换主题（dark/light）时：** toast 样式由 Phase 11 的全局覆盖承载，本阶段不新增 toast 样式。

---

## Component Contract 8 — 加载与失败态

| 场景 | 表现 |
|------|------|
| 元数据 store 首次加载 | **无 loading 骨架** —— store 初始化即从 `FALLBACK_METRIC_META` 深拷贝，立即可用 |
| 元数据异步拉取失败 | 静默降级到 fallback（Phase 5 既定），**零 toast、零错误态** |
| 阈值列表加载中 | `el-table v-loading`（沿用现有） |
| 阈值列表加载失败 | `ErrorState variant="server"`，标题 `无法加载阈值列表`，重试按钮 `重新加载阈值列表`（Phase 11 既有契约） |
| 保存失败（业务） | 1 条中文 toast（Phase 11 单一出口） |
| 保存失败（校验） | 0 条 toast → 汇总 + 内联（D-12） |
| alertable 列表为空 | 第 1 步内联提示 `暂无可配置的告警指标`，禁用「下一步」 |

---

## Component Contract 9 — 按钮字重归一化

`theme.css:437-445` 用 `!important` 把主按钮锁成 `500 14px/1`，EP 又把默认按钮设为
`--el-button-font-weight: 500`。两者都会引入本契约字重表之外的第 3 种字重（500），
且主按钮的 `line-height: 1` 会裁切中文字形下缘。

```css
/* 页面级（列表页与空状态） */
.thresholds-page :deep(.el-button--primary) {
  font: 600 14px/1.5 var(--font-ui) !important;   /* 600 + Body 行高 */
}
.thresholds-page :deep(.el-button:not(.el-button--primary)) {
  font-weight: 400 !important;                    /* EP 默认 500 → 400 */
  line-height: 1.5 !important;
}
```

**向导内按钮**在 `ElDialog` 里，而 `ElDialog` 默认 teleport 到 `document.body`，
scoped 样式无法触达。因此这两条必须写进**全局** `theme.css`：

```css
/* theme.css — 全局，紧邻已有的 .el-button--primary !important 块 */
.el-dialog .el-button--primary { font: 600 14px/1.5 var(--font-ui) !important; }
.el-dialog .el-button:not(.el-button--primary) {
  font-weight: 400 !important;
  line-height: 1.5 !important;
}
```

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| 主 CTA（列表页） | `添加阈值` |
| 主 CTA（空状态） | `使用推荐阈值` |
| 主 CTA（向导第 2 步） | `下一步` |
| 主 CTA（向导第 3 步） | `保存阈值` |
| 次 CTA（向导第 2/3 步） | `上一步` |
| 次 CTA（第 2 步） | `恢复推荐值` |
| 取消 | `放弃设置`（动词 + 宾语；底部次按钮） |
| 关闭向导（有未保存内容） | `内容尚未保存，确定离开？`（复用 Phase 11 已有的字典键 `dialog.unsavedConfirm`，见 `utils/errorMessages.ts:30`） |
| 关闭向导（无任何输入） | 直接关闭，不确认 |
| 空状态标题 | `尚未设置个性化阈值` |
| 空状态正文 | `系统正在使用默认告警规则。设置个性化阈值后，将达到您设定的标准时才发出提醒。` |
| 空状态表格标签 | `心率` / `收缩压` / `舒张压` / `血糖` / `体温` / `BMI` |
| 空状态表格内容 | `正常 60–100 次/分，高于 100 黄色提醒，高于 120 红色提醒`（按指标生成） |
| 错误态（列表加载失败） | `无法加载阈值列表` + `可能是网络波动或服务暂时不可用，请重试。` + `重新加载阈值列表` |
| 错误态（指标不可用） | `暂无可配置的告警指标`（内联提示，非错误块） |
| 破坏性操作确认 | `删除阈值：确定要删除这个阈值设置吗？`（沿用现有 `ElMessageBox`） |
| 重复指标 | `该指标已有阈值，是否改为编辑？` + `打开编辑` |
| 字段说明：严重高值 | `高于此值发出红色紧急提醒（{unit}）` |
| 字段说明：警告高值 | `高于此值发出黄色提醒（{unit}）` |
| 字段说明：警告低值 | `低于此值发出黄色提醒（{unit}）` |
| 字段说明：严重低值 | `低于此值发出红色紧急提醒（{unit}）` |
| 推荐徽标 | `推荐` |
| 确认标题 | `请确认以下告警行为` |
| 校验：低值不升序 | `警告低值必须小于警告高值，请调整其中一个` |
| 校验：严重低边界 | `严重低值必须小于警告低值，请调整其中一个` |
| 校验：严重高边界 | `警告高值必须小于严重高值，请调整其中一个` |
| 编辑模式第 1 步 | `指标已锁定` |
| 未填写边界（色带） | `—` |

**文案原则：** 每条失败都说明「什么失败了 + 下一步怎么做」，不出现「操作失败」「请求失败」这类泛化文案（Phase 11 既定纪律）。

---

## UI Considerations

> 由 ui-phase 的 UI-consideration probe 在 Step 9.5 生成（11 个表面 / 48 项适用考量）。
> 此处覆盖形状驱动的 UI *状态*；空态/错误态的具体文案见上方 `## Copywriting Contract`（不重复）。

Applicable state considerations resolved: 48 covered (0 backstop, 0 unresolved)

### 长文本与溢出（8 项 → 统一策略）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| long-text | E1 向导、E3 四项字段、E5 校验、E8 重复提示、E10 离开确认、E11 步骤切换 | ✅ covered | 提示文案与校验文案**自然换行、不设 max-height、不截断**。`el-dialog` 内容区在内容超出时纵向滚动（EP 默认行为），按钮栏固定底部。 |
| long-text | E2 指标选择器选项 | ✅ covered | 选项文本 `white-space: nowrap; overflow: hidden; text-overflow: ellipsis`，同时 `:title` 给完整 `显示名 (单位)`。下拉项高度固定 34px。 |
| long-text | E4 四区色带标签 | ✅ covered | 色带内标签 `overflow: hidden; text-overflow: ellipsis; white-space: nowrap`；数值超出时以 `…` 结尾，完整值由 `aria-label` 承载。 |
| long-text | E7 空状态默认规则表 | ✅ covered | 表格内容列 `word-break: break-word`，不截断；窄屏（<640px）时 `el-descriptions` 的 `:column` 仍为 1，自然纵向堆叠。 |
| long-text | E9 阈值列表 | ✅ covered | 指标列 `show-overflow-tooltip`（EP 内置）；数值列右对齐不换行。 |
| overflow | E2 指标选择器 | ✅ covered | 下拉面板 `max-height: 264px`，超出滚动（EP 默认 `popper` 行为）。 |
| overflow | E3 四项数值输入 | ✅ covered | `el-input-number` 数值横向滚动，不换行、不撑破容器。 |
| overflow | E4 色带、E7 空状态表、E9 列表 | ✅ covered | 色带 `flex: 1` 等分，窄屏（<640px）改为 `flex-wrap: wrap` 两行堆叠（每行 3 + 2）；空状态表与列表宽度 `100%`，不横向溢出。 |

### 空 / 加载 / 错误（12 项 → 沿用 Phase 11 契约）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| empty | E9 阈值列表 | ✅ covered | 0 条时渲染教学型空状态（Component Contract 6），**不用 `ElEmpty`**。 |
| empty | E3 四项字段 | ✅ covered | 指标未选时四项为空且禁用（`el-input-number` 的 `:disabled`），提示文案仍显示，用户能读到"这里将来填什么"。 |
| empty | E5 校验、E10 离开确认 | ✅ covered | 无错误时不渲染汇总块（`v-if="summaryItems.length"`）；无输入时关闭不弹确认。 |
| empty | E1 向导、E7 空状态、E11 步骤切换 | ✅ covered | 向导本身不出现空态（打开即有内容）；空状态是向导的入口；步骤切换无空态。 |
| loading | E1/E3/E5/E7/E8/E9/E10/E11 | ✅ covered | **向导内部无加载态** —— 元数据 store 初始化即从 `FALLBACK_METRIC_META` 深拷贝，零延迟可用。列表加载用 `el-table v-loading`。保存中按钮 `:loading` 并禁用重复提交。 |
| error | E1/E3/E5/E7/E8/E9/E10/E11 | ✅ covered | 元数据拉取失败 → 静默降级到 fallback，**零 toast、零错误态**。列表加载失败 → `ErrorState variant="server"` + 重试。保存失败 → 校验走汇总 + 内联（零 toast）；业务失败走 `showError` 单条 toast。重复指标 → `showError` + 「打开编辑」（Component Contract 7）。 |

### 部分数据（11 项 → null 合法）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| partial | E1 向导、E3 四项字段、E5 校验、E10 离开确认、E11 步骤切换 | ✅ covered | 四项阈值**允许部分为 `null`**（= 无界），与项目既有 `null = 无界` 语义一致。`null` **不触发**层级校验，也不进入汇总。仅当四项全为 `null` 时禁用「下一步 / 保存」。 |
| partial | E4 四区色带 | ✅ covered | 未设置的边界在色带中显示 `—`（Copywriting Contract 已定义），区段**不隐藏**（隐藏会导致色带错位）。`aria-label` 该边界读作 `未设置`。 |
| partial | E6 确认语句 | ✅ covered | 只为**已填写**的边界生成分句，`null` 边界整个分句省略；全部 `null` 时（被校验拦截，不应到达）退化为 `尚未设置任何有效阈值`。 |
| partial | E7 空状态、E8 重复提示、E9 列表 | ✅ covered | 空状态无部分数据概念；重复提示是单一 toast；列表行内某列为 `null` 时显示 `-`（沿用现有渲染）。 |

### 零 / 一 / 多（2 项）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| zero-one-many | E9 阈值列表 | ✅ covered | 0 条 → 教学型空状态；1 条 → 表格正常渲染；N 条 → 表格 + 分页（`thresholdApi.getThresholds({ page, size })` 已支持）。中文量词「条」，不做单复数变形。 |
| zero-one-many | E7 空状态默认规则表 | ✅ covered | 默认规则表固定列出全部可告警指标（当前 6 项），不随用户数据变化；因此无 0/1/N 之分。 |

### 其他（3 项）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| populated | E7 空状态、E9 列表 | ✅ covered | E7 的 populated 即列表有数据时的常规表格态（见 E9）；E9 的 populated 为 6 列 + 操作列的标准表格，行高 48px。 |
| unclassified | E6 确认语句 | ✅ covered | 人工复核结论：确认语句是**纯展示文本**（`role="status"`），无交互、无数据加载，仅需处理"部分边界"降级（已在上方 partial 行覆盖）。无需额外状态。 |

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not required |
| 第三方 registry | none | not required |

本阶段**不引入任何外部 registry 组件**。所有组件来自已安装的 `element-plus@2.14.0`。

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS
- [ ] Dimension 7 Inventory Provenance: PASS

**Approval:** pending