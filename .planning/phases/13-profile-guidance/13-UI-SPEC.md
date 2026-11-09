---
phase: "13"
slug: "profile-guidance"
status: approved
shadcn_initialized: false
preset: none
created: "2026-11-09"
reviewed_at: "2026-11-09"
---

# Phase 13 — UI Design Contract

> 健康档案完善引导（非阻塞）的视觉与交互契约。由 gsd-ui-researcher 生成，gsd-ui-checker 校验。
> **本契约在 Phase 11（错误呈现）与 Phase 12（阈值向导）之上扩展，不重新定义错误色、错误间距与按钮字重归一化规则。**

**本阶段四个表面：**

| # | 表面 | 落点 |
|---|------|------|
| S1 | 完整度展示 + 逐字段状态 | `prevention/Profile.vue` 卡片体顶部、`dashboard/Index.vue` 健康评分卡 |
| S2 | 字段级解释块 + 重要度徽标 + 不填承诺 | `prevention/Profile.vue` 的 9 个 `el-form-item` |
| S3 | 假设披露徽标 | `prevention/Risk.vue` 表格上方 + 详情对话框、`health/Score.vue` 评分卡 |
| S4 | 提醒横幅 + 就地补填面板 | `dashboard/Index.vue` 顶部、`prevention/Risk.vue` 表格上方 |

**全程无弹窗、无固定宽度面板、无路由门禁。** Phase 12 已知的「760px 固定宽弹窗在 <640px 溢出」不会在本阶段复现 —— 四个表面全部是文档流内布局，宽度 `100%` 自适应。

### 视觉焦点（每个表面只有一个主焦点）

每个表面必须能让执行者一眼判断「用户先看什么」，**不得多于一个同级主焦点**。焦点由字号 + 字重 + 对比度三者共同建立，不靠颜色（accent 不用于文字，见 Color 节）。

| 表面 | 主焦点（第一视点） | 建立手段 | 次级层次（明确降一档） |
|------|-------------------|---------|----------------------|
| S1 档案页完整度头部 | **`62%` 数值** | 唯一 24px/600 字号；`--text-1`；`tabular-nums` | 等级 pill、`已填 5 / 9`、图例项（12px/400 `--text-2`）；影响句（14px/400 `--text-2`） |
| S1 仪表盘紧凑完整度行 | **`62%` 数值** | 24px/600 `--text-1` | pill、`完善档案` 链接（12px/400 下划线） |
| S2 字段行 × 9 | **输入控件本身** | 唯一可交互元素 + `width: 100%` 占满内容列；标签 14px/600 `--text-1`（17.01:1）把视线引到控件，说明行降到 12px/400 `--text-2` | 说明行（12px/400 `--text-2` 8.38:1）、重要度徽标（20px 高 chip）；**不靠控件描边对比度建立层次** —— `--border-strong` 仅 1.41:1（Light），它是既有 EP 输入框边界，本阶段不改 |
| S3 估算徽标 | **动作片段 `完善档案`**（徽标整体是一个控件时，焦点落在可点击动作上） | 下划线 + `--text-1`（17.01:1）；状态片段 `--text-2` 降一档 | 状态片段 `基于不完整档案估算`（8.38:1，仍达 AA） |
| S3 风险页假设列表（对话框） | **假设条目列表** | 14px/400 `--text-2` 带项目符号的实列表 | 区块标题 `本次评估采用的假设`（14px/600 `--text-1`）—— 标题是入口，条目是内容 |
| S4a 仪表盘横幅 | **`完善档案` 主按钮** | 唯一 primary 按钮；`600 14px/1.5` | 标题 `档案完整度 62%，还缺 N 项`（14px/600）、正文（14px/400 `--text-2`）、`稍后提醒` 次按钮、× 关闭 |
| S4b 补填面板 | **`保存档案并创建` 主按钮** | 唯一 primary 按钮 | `el-alert` 标题（14px/600）、假设列表、表单、`直接创建（按估算）` 次按钮、`放弃创建` text 按钮 |

**跨表面优先级（同一屏内多于一个表面时）：**
1. 仪表盘（S4a + S1 compact）：横幅在页面最上方且带边框 + 图标，是首屏第一焦点；完整度紧凑行紧随健康评分数字之后，是第二层次。**横幅不与健康评分圆环争夺焦点** —— 二者由 `.stats-grid` 分隔，横幅用 `surface-1` 底 + 单条边框（不是高饱和填充）。
2. 风险页（S4b + S3）：面板展开时 S4b 是焦点，表格上方的估算徽标 `v-if="!panelOpen"` **隐藏**（Contract 5 已声明），避免两个同级提示并列。
3. 档案页（S1 + S2）：完整度头部在表单之上，`62%` 是页面第一视点；9 个字段行平级，不设「最重要字段」的视觉强调（重要度已由徽标承担，不再叠加颜色或字号差异）。

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none（非 shadcn 项目 — 项目已有自成体系的 Element Plus + CSS token 系统） |
| Preset | not applicable |
| Component library | `element-plus@2.14.0`（已安装；本阶段零新增依赖） |
| Icon library | `@element-plus/icons-vue@2.3.2`（`InfoFilled` / `Select` / `CircleCheck` / `ArrowRight`） |
| Font | `--font-ui`: `'DM Sans', 'Noto Sans SC', system-ui, …`（theme.css:74）；数值用 `--font-mono` 仅限既有 `.el-tag` |

**为什么不是 shadcn：** REQUIREMENTS.md `## Out of Scope` 明确禁止引入第二套 UI 库。`components.json` 不存在，且技术栈是 Vue 3（不是 React/Next.js），shadcn 初始化门禁不适用。本契约只使用已安装的 Element Plus 与 `theme.css` 既有 token。

**关于 D-04「徽标复用现有 `el-tag`」的显式覆盖：** `theme.css:542-549` 用 `!important` 把 `.el-tag` 锁死为 `500 11px/1.4 var(--font-mono)` + 全圆角 + accent 文字色。本阶段的估算徽标是**可点击的披露控件**（需要 12px 可读正文、明确的焦点环与下划线动作线索），若沿用 `el-tag` 会：引入第 3 种字重（500）、与同页风险等级标签在视觉上无法区分、且 accent 文字在浅色仅 3.74:1 不达 AA。因此改为自绘 `button.hhs-estimate-badge`（Phase 12 Contract 3 的 `span.recommend-badge` 先例）。风险等级的 `el-tag` 保持原样不动。

---

## Component Inventory

Enumerated by `node -e "const fs=require('fs');const b='node_modules/element-plus/es/components';console.log(fs.readdirSync(b).filter(n=>fs.existsSync(b+'/'+n+'/index.mjs')).length)"` — 91 components — element-plus@2.14.0 — 2026-11-09.

（`readdirSync` 直接返回 124 个目录，其中 91 个含 `index.mjs` 组件入口；上方记录的是过滤后的可复现命令。`@element-plus/icons-vue` 版本 **2.3.2**，由 `element-plus/package.json` 的 peer 约束解析。）

下表只列出本阶段会用到的子集，**不是封闭白名单**；执行者可继续使用该库导出的任何组件。

| Component | Import path | 本阶段用途 |
|-----------|-------------|-----------|
| `ElForm` / `ElFormItem` | `element-plus`（auto-import） | 档案页 9 个字段；补填面板的 2 个字段；`prop` + `:error` 接 Phase 11 的 `fieldErrors` |
| `ElInputNumber` | `element-plus` | 身高 / 体重 |
| `ElDatePicker` | `element-plus` | 出生日期（档案页 + 补填面板） |
| `ElRadioGroup` / `ElRadio` | `element-plus` | 性别（档案页 + 补填面板） |
| `ElSelect` / `ElOption` | `element-plus` | 血型 |
| `ElInput` | `element-plus` | 过敏史 / 家族病史 / 生活习惯（`type="textarea"`）；BMI 只读回显 |
| `ElAlert` | `element-plus` | **仅**补填面板的假设标题容器（`#title` 插槽，Vacuous 用法同 `FormErrorSummary`）—— 列表与表单**不得**放进默认插槽（EP 渲染进 `<p>`） |
| `ElButton` | `element-plus` | 横幅两个动作、补填面板三个动作、档案页既有编辑/保存 |
| `ElIcon` | `element-plus` | 横幅图标、图例状态图标 |
| `ElSkeleton` / `ElSkeletonItem` | `element-plus` | **不使用** —— 完整度头部不引入骨架屏（见 Contract 8） |
| `ElProgress` | `element-plus` | **不使用** —— 完整度条必须是逐字段分段，`ElProgress` 无法表达分段与三档分组；其 `.el-progress__text` 颜色走 EP 变量而非本站 token。（`ElProgress` 在 `goals/Index.vue`、`health/Score.vue` 既有用法保持不动） |
| `ElTag` | `element-plus` | **不得用于**完整度三档 pill、重要度徽标、估算徽标（`theme.css:542-549` 的 `!important` 锁定）。仅风险等级与 BMI 分类沿用既有 `el-tag` |
| `ElTooltip` | `element-plus` | **不使用** —— 「为何需要」按 D-02 必须常驻可见，不能折叠进 tooltip |
| `ElEmpty` / `ElResult` | `element-plus` | **不使用** —— 空态与错误态沿用 Phase 11 既有载体 |

**复用而非重建的既有资产：**

| Asset | 路径 | 用途 |
|-------|------|------|
| `FormErrorSummary` | `src/components/common/FormErrorSummary.vue` | 档案页与补填面板的服务端/客户端校验汇总（零修改） |
| `ErrorState` | `src/components/common/ErrorState.vue` | 档案加载失败的页面级状态块 |
| `useFormServerErrors` | `src/composables/useFormServerErrors.ts` | `setErrors()` / `apply()` / `clearField()` / `reset()` / `focusField()` |
| `showError` | `src/utils/showError.ts` | 唯一 toast 出口 |
| `ERROR_MESSAGES` / `FIELD_LABELS` | `src/utils/errorMessages.ts` | 新增文案键；`FIELD_LABELS` 已含全部 9 个档案字段中文名 |
| `el-alert` 错误归一化 | `theme.css:1236-1242`（`body .el-form-item__error`） | 内联错误样式，**不重定义** |

**本阶段新建（三个组件 + 一个 store + 全局样式族）：**

| New asset | 路径 | 职责 |
|-----------|------|------|
| `ProfileCompleteness` | `src/components/common/ProfileCompleteness.vue` | 完整度头部块，`variant: 'full' \| 'compact'`。档案页用 `full`（含逐字段图例），仪表盘用 `compact`（含跳转链接） |
| `ProfileReminderBanner` | `src/components/common/ProfileReminderBanner.vue` | 仪表盘可关闭横幅（3 个动作） |
| `EstimateBadge` | `src/components/common/EstimateBadge.vue` | 估算披露徽标（`button`，`label` + `@click`） |
| `useProfileCompletenessStore` | `src/stores/profileCompleteness.ts` | 完整度单一数据源 + 7 天静默时间戳（`localStorage`） |
| 共享视觉族 | `theme.css` 新增 `.hhs-completeness-meter` / `.hhs-tier-pill` / `.hhs-imp-badge` / `.hhs-estimate-badge` | 四个表面跨三个页面复用同一套徽标与分段条几何 |

---

## Spacing Scale

声明的间距值**全部是 4 的倍数**（含组件属性：`el-row :gutter` 必须是 4 的倍数 —— Element Plus 把 gutter 对半分给左右两侧，`gutter="16"` 每侧渲染 8px，`gutter="20"` 每侧渲染 **10px** 的 off-grid 值），且每个 token 绑定到本契约中某个具体 CSS 声明。

| Token | Value | Usage（绑定到具体声明） |
|-------|-------|-------|
| xs | 4px | 完整度条分段间隙（`.hhs-completeness-meter { gap: 4px }`）；紧凑条分段高度（`.hhs-completeness-meter--compact .hhs-completeness-meter__seg { height: 4px }`）；说明行与输入框的垂直间距（`.field-hint { margin-top: 4px }`）；徽标与说明文字的水平间距（`.field-hint { gap: 4px }`）；图例项内图标与文字间距（`.completeness__legend-item { gap: 4px }`）；补填面板假设条目间距（`.risk-estimate__list { gap: 4px }`）；徽标内水平内边距（`.hhs-imp-badge { padding: 0 4px }`）；估算徽标上下内边距（`.hhs-estimate-badge { padding: 4px 12px }`） |
| sm | 8px | 完整度头部各行元素的水平间距（`.completeness__head { gap: 8px }`）；图例项之间的行间距（`.completeness__legend { gap: 8px 16px }`）；按钮组间距（`.profile-banner__actions { gap: 8px }` / `.risk-estimate__actions { gap: 8px }`）；完整度条高度（`.hhs-completeness-meter__seg { height: 8px }`）；tier pill 水平内边距（`.hhs-tier-pill { padding: 0 8px }`）；仪表盘紧凑条到链接的间距（`.completeness__link { margin-top: 8px }`） |
| sm-3 | 12px | 完整度头部到分段条的间距（`.completeness__meter { margin-top: 12px }`）；分段条到图例的间距（`.completeness__legend { margin-top: 12px }`）；图例到影响句的间距（`.completeness__impact { margin-top: 12px }`）；影响句的分隔线上内边距（`.completeness__impact { padding-top: 12px }`）；横幅图标与正文间距（`.profile-banner { gap: 12px }`）；补填面板 `el-alert` 内边距的垂直分量（`--el-alert-padding: 12px 16px`）；假设列表上外边距（`.risk-estimate__list { margin: 12px 0 0 }`）；估算徽标水平内边距（`.hhs-estimate-badge { padding: 4px 12px }`）；估算徽标到评分提示的间距（`.score-info .hhs-estimate-badge { margin-bottom: 12px }`） |
| md | 16px | 完整度块底部留白（`.completeness { margin-bottom: 16px }`）；不填承诺句底部留白（`.profile-promise { margin-bottom: 16px }`）；`el-form-item` 归一化行距（`.profile-page :deep(.el-form-item) { margin-bottom: 16px }`）；横幅内边距（`.profile-banner { padding: 16px }`）；补填面板内边距（`.risk-estimate { padding: 16px }`）；补填面板到表格的间距（`.risk-estimate { margin-bottom: 16px }`）；假设列表到表单（`.risk-estimate__form { margin-top: 16px }`）；表单到按钮组（`.risk-estimate__actions { margin-top: 16px }`）；`el-alert` 水平内边距（`--el-alert-padding: 12px 16px`）；估算披露块底部间距（`.risk-disclosure { margin-bottom: 16px }`）；估算徽标与风险等级标签共存时的最小垂直间距（16px） |
| lg | 24px | 仪表盘横幅与后续内容的间距（`.profile-banner { margin-bottom: 24px }`）；tier pill 高度（`.hhs-tier-pill { height: 24px }`）；假设列表的悬挂缩进（`.risk-estimate__list { padding-left: 24px }`） |
| xl | 32px | 横幅图标圆直径（`.profile-banner__icon { width/height: 32px }`）；横幅关闭按钮直径（`.profile-banner__close { width/height: 32px }`）；估算徽标最小点击高度（`.hhs-estimate-badge { min-height: 32px }`）；窄屏横幅动作区左缩进（`.profile-banner__actions { padding-left: 44px }` = 32 + 12） |

**No exceptions.** 所有间距值都是 4 的倍数。

**四条必须覆盖的 Element Plus 默认间距（否则运行时渲染 off-grid 值）：**

| EP 默认 | 归一化目标 | 声明位置 |
|---|---|---|
| `.el-form-item { margin-bottom: 18px }` | `16px` | Contract 2、Contract 5 |
| `.el-alert { --el-alert-padding: 8px 16px }` | `12px 16px` | Contract 5 |
| `.el-button + .el-button { margin-left: 12px }` | `0`（间距交给父级 `gap: 8px`） | Contract 6 |
| `el-row :gutter="20"`（EP 对半分给两列 → 每侧 10px） | `:gutter="16"`（每侧 8px） | Contract 2（既有档案页栅格）、Contract 5（补填面板栅格） |

**两条必须覆盖的 Element Plus 组件几何：**

| EP 默认 | 归一化目标 | 声明位置 |
|---|---|---|
| `.el-form-item` 必填星号 `:before` 用 `--el-color-danger`（`#f56c6c` 在纯白仅 2.90:1） | **不渲染星号**（`:hide-required-asterisk="true"`）—— 三档重要度徽标已承担该语义，二元星号会与之重复且颜色不达 AA | Contract 2 |
| `.el-tag { font: 500 11px/1.4 mono }` | 沿用（风险等级与 BMI 标签保持不动），但本阶段新增徽标**一律不用 `el-tag`** | Contract 6 |

---

## Typography

**恰好 4 个字号、恰好 2 个字重**，与 Phase 11 / Phase 12 契约一致。

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 14px | 400 (regular) | 1.5 |
| Label | 12px | 400 (regular) | 1.4 |
| Heading | 18px | 600 (semibold) | 1.3 |
| Display | 24px | 600 (semibold) | 1.2 |

> **必须覆盖的 `theme.css` 全局 `!important`** —— 以下规则会绕过本契约的字号/字重表：

| theme.css 规则 | 实际值 | 影响本阶段的表面 | 处理方式 |
|---|---|---|---|
| `.el-form-item__label { font: 500 14px/1.5 … !important }`（`theme.css:660-664`） | **500 字重** | 档案页 9 个标签、补填面板 2 个标签 | 页面级 `:deep()` 覆盖为 `600 14px/1.5`（Contract 6） |
| `.el-input__inner { font: 400 15px/1.5 … !important }`（`theme.css:516-519`） | 15px | 身高 / 体重 / 文本域 / 日期框 | 覆盖为 `400 14px/1.5`（Contract 6） |
| `.el-button--primary { font: 500 14px/1 … !important }`（`theme.css:449-459`） | **500 字重 / 行高 1** | 横幅 `完善档案`、补填面板 `保存档案并创建` | 页面级覆盖为 `600 14px/1.5`（Contract 6） |
| EP `.el-button { --el-button-font-weight: 500 }` | **500 字重** | 横幅 `稍后提醒`、补填面板 `直接创建（按估算）` / `放弃创建` | 覆盖为 `400`（Contract 6） |
| `.el-tag { font: 500 11px/1.4 mono !important }`（`theme.css:542-549`） | 11px / 500 | 三档 pill、重要度徽标、估算徽标 | **不使用 `el-tag`**，全部自绘（Contract 1 / 2 / 4） |
| `.el-alert__title { font-size: 14px; line-height: 24px }`（EP 默认） | 行高 24px | 补填面板的假设标题 | 覆盖为 `600 14px/1.4`（Contract 5） |

**本阶段各表面的应用：**

| Surface | Size / Weight / LH | 依据 |
|---------|--------------------|------|
| 完整度标签「档案完整度」 | 14px / 400 / 1.5，色 `--text-2` | Body |
| 完整度数值「62%」 | 24px / 600 / 1.2，色 `--text-1`，`font-variant-numeric: tabular-nums` | Display |
| 三档等级 pill「标准档案」 | 12px / 400 / 1.4，色 `--text-1` | Label |
| 计数「已填 5 / 9」 | 12px / 400 / 1.4，色 `--text-2` | Label |
| 逐字段图例项「✓ 身高」 | 12px / 400 / 1.4，色 `--text-2` | Label |
| 影响句「缺少身高、体重或 BMI…」 | 14px / 400 / 1.5，色 `--text-2` | Body |
| 字段标签「身高(cm)」 | 14px / 600 / 1.5，色 `--text-1` | Body + semibold（比说明重一档） |
| 字段说明「用于生成 BMI…」 | 12px / 400 / 1.4，色 `--text-2`（**`--text-3` 浅色仅 3.97:1 不可用**） | Label |
| 重要度徽标「必需 / 推荐 / 可选」 | 12px / 400 / 1.4 | Label |
| 不填承诺句 | 14px / 400 / 1.5，色 `--text-1` | Body |
| 横幅标题 | 14px / 600 / 1.5，色 `--text-1` | Body + semibold |
| 横幅正文 | 14px / 400 / 1.5，色 `--text-2` | Body |
| 估算徽标文案 | 12px / 400 / 1.4（动作片段 `--text-1` + 下划线；状态片段 `--text-2`） | Label |
| 补填面板 `el-alert` 标题 | 14px / 600 / 1.4，色 `--text-1` | Body + semibold |
| 假设条目 | 14px / 400 / 1.5，色 `--text-2` | Body |
| 内联校验错误 | 12px / 400 / **1.4**（Phase 11 全局覆盖已生效） | Label，**不重定义** |

**字重只有 400 与 600 两档**，不引入 500/700。

---

## Color

所有色值均来自 `theme.css` 既有 token；**对比度全部实测**（WCAG 2.1 相对亮度公式）。

### 60 / 30 / 10 分配（本阶段表面）

| 占比 | 角色 | Dark | Light | 本阶段占用位置 |
|------|------|------|-------|---------------|
| 60% | 主导 / 背景 | `--bg` `#0a0b0e` | `--bg` `#f5f7fa` | 仪表盘页底、档案页页底、横幅之间的留白 |
| 30% | 次主导 / 表面 | `--surface-1` `#111217`、`--surface-2` `#171921`、`--surface-3` `#1f222d` | `#ffffff`、`#f0f2f5`、`#e4e7ed` | `el-card` 卡片面、横幅底色、补填面板底色、未填分段与「标准档案」pill 底色 |
| 10% | 强调 | `--accent-cool` `#5EEAD4` | `--accent-cool` `#0d9488` | **仅限**：已填分段填充、图例 ✓ 图标、「完整档案」pill 的底纹（经 `--accent-cool-soft`）、焦点环（`:focus-visible` 与 `.is-focus-target` 两种状态） |

**强调色绝不用于文字。** Light 主题下 `#0d9488` 在白底仅 **3.74:1**，不达 AA 正文 4.5:1。已填分段的填充 vs 未填分段（`#e4e7ed`）实测 **3.02:1**，满足非文字图形 3:1；Dark 为 **10.71:1**。

**次要语义色 `--accent-warm`（暖橙）**：Light `#ea580c` 在白底仅 **3.56:1**，**同样禁止用于文字**。保留给：「基础档案」pill 的底纹（经 `--accent-warm-soft`）、「必需」徽标的底纹、横幅图标、估算徽标的边框与焦点环。

### 语义色（沿用，不新增）

| Role | Dark value | Light value | 对比度（实测） | 本阶段用途 |
|------|-----------|-------------|---------------|-----------|
| **破坏性操作（Destructive）** | `#f56c6c` | `#b91c1c` | Dark 6.45:1 / Light 6.47:1 | **本阶段无破坏性操作**（`稍后提醒` 与关闭 × 都不是破坏性动作，不用此色） |
| 正文主色 | `--text-1` `#ebecef` | `#1a1c23` | Dark 15.83:1 / Light 17.01:1 | 完整度数值、字段标签、横幅标题、pill 文字 |
| 正文次色 | `--text-2` `#c6c9d2` | `#4a4d5c` | Dark 11.30:1 / Light **8.38:1** | **全部说明性小字**（字段说明、影响句、图例、假设条目、横幅正文） |
| 辅助文字 | `--text-3` `#8d909c` | `#7c7f8e` | Dark 5.88:1 / Light **3.97:1** | **Light 不达 AA**，本阶段只允许用于纯装饰（无）；本阶段所有说明文字一律 `--text-2` |
| 表面 3（未填分段 / 标准 pill） | `#1f222d` | `#e4e7ed` | 其上 `--text-1`：Dark 13.42:1 / Light 13.73:1 | 分段底、`标准档案` pill 底 |

### 三档等级色映射（**颜色只是辅助，文案「基础/标准/完整档案」才是信息载体**）

| 等级 | 判定 | pill 背景 | pill 文字 | 实测对比度 |
|------|------|----------|-----------|-----------|
| 基础档案 | `< 60%` | `--accent-warm-soft`（Dark `rgba(251,146,60,.12)` → `#2d211b`；Light `#fdf2ec`） | `--text-1` | Dark **13.22:1** / Light **15.47:1** |
| 标准档案 | `60–84%` | `--surface-3`（Dark `#1f222d`；Light `#e4e7ed`） | `--text-1` | Dark **13.42:1** / Light **13.73:1** |
| 完整档案 | `≥ 85%` | `--accent-cool-soft`（Dark `rgba(94,234,212,.12)` → `#1a2c2e`；Light `#ecf6f5`） | `--text-1` | Dark **12.31:1** / Light **15.45:1** |

三档 pill 的**形状与尺寸完全一致**（`height: 24px`、`radius-sm`、同一字号字重），差异只在底纹；WCAG 1.4.1 由 pill 内文字（基础/标准/完整档案）满足，不依赖颜色。

### 重要度三档（**文字标签是唯一信息载体**，形状与颜色仅作辅助）

| 重要度 | 徽标几何 | 背景 | 边框 | 文字色 | 辅助手段 |
|--------|----------|------|------|--------|---------|
| 必需 | 实心 chip，`height: 20px`、`padding: 0 4px`、`radius-sm` | `--accent-warm-soft` | 无 | `--text-1` | 底纹 + 文字「必需」 |
| 推荐 | 空心 chip，同几何 | 透明 | `1px solid var(--border-strong)` | `--text-2` | 实线描边 + 文字「推荐」 |
| 可选 | 空心 chip，同几何 | 透明 | `1px dashed var(--border-strong)` | `--text-2` | 虚线描边 + 文字「可选」 |

**WCAG 1.4.1 由文字标签满足，不依赖形状或颜色。** 徽标内的「必需 / 推荐 / 可选」三个中文词是唯一的语义载体：它们在 `--text-1`（15.83:1）或 `--text-2`（8.38:1 Light / 11.30:1 Dark）上均远超 AA 正文 4.5:1，任何色觉或低视力用户读到的都是完整语义。

**明确记录一项已知限制（不试图消除）：** 三档之间的**形状**差异（实心 / 实线 / 虚线）仅作辅助线索，**不作为可达性依赖项**。原因：`--border-strong` 在 Light 为 `rgba(0,0,0,0.15)`（与白卡面合成 ≈ `#d9d9d9`，**1.41:1**）、Dark 为 `rgba(67,70,81,0.9)`（**1.84:1**），实线与虚线在 1px 描边上的视觉差异对低视力用户难以分辨；「必需」chip 的 `--accent-warm-soft` 底色与白卡面仅 **≈1.10:1**（叠 `--bg` 约 1.02:1），同样近乎不可见。本契约**不接受**「即使完全失明于颜色也能分辨」这类过度声明 —— 正确的说法是：「文字标签承担全部语义；描边样式只服务正常视力用户的快速扫读」。

**为什么不把边框提到 ≥3:1：** 三档徽标是**非交互、非必需的装饰性标识**，不是 UI 组件边界（WCAG 1.4.11 不适用），也不是信息的必要载体（1.4.1 已由文字满足）。把 `--border-strong` 全局提到 3:1 会改变全站既有卡片/表格边界的分量（`theme.css:24` 是共享 token），属于本阶段之外的全局视觉变更，不得在此处夹带。若未来要提升，应在独立的 token 演进阶段改 `--border-strong` 并同步核对全站。

### 估算徽标

| Property | Value |
|----------|-------|
| 背景 | 透明（hover 时 `--accent-warm-soft`） |
| 边框 | `1px solid var(--accent-warm)` —— Light 3.32:1 / Dark 8.70:1，满足非文字控件边界 3:1 |
| 状态片段文字 | `--text-2`（Light 8.38:1 / Dark 11.30:1 on 卡片面） |
| 动作片段文字 | `--text-1` + `text-decoration: underline` + `text-underline-offset: 2px` |
| 圆角 | `--radius-sm`（6px） |
| 焦点环 | `outline: 2px solid var(--accent-warm)` + `offset: 2px` |

**为什么必须带边框：** `--accent-warm-soft` 的浅色值 `#fdf2ec` 与白卡面仅 **1.07:1**，无边框的浅色 chip 在 Light 主题下不可见；这是可点击控件，1.4.11 要求其边界 ≥3:1。

### 新增 token（必须写进 `theme.css` 的 `:root` 与 `[data-theme='light']` 两处）

本阶段**不新增颜色 token** —— 全部复用 Phase 11/12 已有的 `--hhs-color-warning-tint`、`--accent-*-soft`、`--surface-*`。只新增四个**共享类**（见 Contract 6）。

---

## Component Contract 1 — 完整度头部块（S1）

档案页用 `variant="full"`，仪表盘用 `variant="compact"`。两处共用 `ProfileCompleteness.vue` 与 `theme.css` 的 `.hhs-completeness-meter` / `.hhs-tier-pill`。

```html
<!-- full：档案页 -->
<section class="completeness" aria-labelledby="completeness-label">
  <div class="completeness__head">
    <span class="completeness__label" id="completeness-label">档案完整度</span>
    <span class="completeness__score">{{ completeness.score }}%</span>
    <span class="hhs-tier-pill" :class="tierClass(completeness.level)">{{ completeness.level }}</span>
    <span class="completeness__count">已填 {{ completeness.filledCount }} / {{ completeness.totalCount }}</span>
  </div>

  <!-- 视觉条：不承载信息（数字与图例已承载），对读屏隐藏 -->
  <div class="hhs-completeness-meter completeness__meter" aria-hidden="true">
    <span
      v-for="f in completeness.fields"
      :key="f.key"
      class="hhs-completeness-meter__seg"
      :class="{ 'is-filled': f.filled }"
    ></span>
  </div>

  <ul class="completeness__legend">
    <li v-for="f in completeness.fields" :key="f.key" class="completeness__legend-item">
      <span class="completeness__state" :class="f.filled ? 'completeness__state--filled' : 'completeness__state--missing'"
            aria-hidden="true">{{ f.filled ? '✓' : '○' }}</span>
      <span class="visually-hidden">{{ f.filled ? '已填' : '未填' }}</span>
      <span>{{ f.label }}</span>
    </li>
  </ul>

  <p class="completeness__impact">{{ impactLine }}</p>
</section>
```

```html
<!-- compact：仪表盘健康评分卡，位于 .score-display 之后 -->
<div class="completeness completeness--compact">
  <div class="completeness__head">
    <span class="completeness__label">档案完整度</span>
    <span class="completeness__score">{{ completeness.score }}%</span>
    <span class="hhs-tier-pill" :class="tierClass(completeness.level)">{{ completeness.level }}</span>
  </div>
  <div class="hhs-completeness-meter hhs-completeness-meter--compact completeness__meter" aria-hidden="true">…</div>
  <router-link class="completeness__link" to="/prevention/profile">完善档案</router-link>
</div>
```

**必须声明的 CSS（`theme.css` 全局，四个表面共用）：**

```css
/* ---------- Phase 13: 档案完整度共享视觉族 ---------- */
.hhs-completeness-meter {
  display: flex;
  gap: 4px;                       /* xs */
  width: 100%;
}
.hhs-completeness-meter__seg {
  flex: 1;
  height: 8px;                    /* sm */
  border-radius: var(--radius-full);
  background: var(--surface-3);
  transition: background var(--dur-fast) var(--ease);
}
.hhs-completeness-meter--compact .hhs-completeness-meter__seg {
  height: 4px;                    /* xs */
}
.hhs-completeness-meter__seg.is-filled {
  background: var(--accent-cool); /* 填充 vs 底纹：Light 3.02:1 / Dark 10.71:1 */
}
.hhs-tier-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;                   /* lg */
  padding: 0 8px;                 /* sm */
  border-radius: var(--radius-sm);
  font: 400 12px/1.4 var(--font-ui);
  color: var(--text-1);
  background: var(--surface-3);
  white-space: nowrap;
}
.hhs-tier-pill--basic { background: var(--accent-warm-soft); }
.hhs-tier-pill--full  { background: var(--accent-cool-soft); }
@media (prefers-reduced-motion: reduce) {
  .hhs-completeness-meter__seg { transition: none; }
}
```

**必须声明的 CSS（`ProfileCompleteness.vue` `<style scoped>`）：**

```css
.completeness { display: block; }
.completeness__head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 8px;                       /* sm */
}
.completeness__label {
  font: 400 14px/1.5 var(--font-ui);   /* Body */
  color: var(--text-2);
}
.completeness__score {
  font: 600 24px/1.2 var(--font-ui);   /* Display */
  color: var(--text-1);
  font-variant-numeric: tabular-nums;  /* 数值变化时不抖动 */
}
.completeness__count {
  font: 400 12px/1.4 var(--font-ui);   /* Label */
  color: var(--text-2);
}
.completeness__meter { margin-top: 12px; }         /* sm-3 */
.completeness__legend {
  margin-top: 12px;                                /* sm-3 */
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;                                   /* sm 行 / md 列 */
}
.completeness__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;                                        /* xs */
  font: 400 12px/1.4 var(--font-ui);               /* Label */
  color: var(--text-2);
}
.completeness__state {
  width: 12px;
  text-align: center;
  line-height: 1.4;
}
/* 图形色：Light #0d9488 on #ffffff = 3.74:1（≥3:1 通过）；Dark 12.64:1 */
.completeness__state--filled { color: var(--accent-cool); }
.completeness__state--missing { color: var(--text-2); }   /* Light 8.38:1 / Dark 11.30:1 */
.completeness__impact {
  margin-top: 12px;                                /* sm-3 */
  padding-top: 12px;                               /* sm-3 */
  border-top: 1px solid var(--border);
  font: 400 14px/1.5 var(--font-ui);               /* Body */
  color: var(--text-2);
}
.completeness__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;                                 /* sm */
  font: 400 12px/1.4 var(--font-ui);
  color: var(--text-1);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.completeness__link:focus-visible {
  outline: 2px solid var(--accent-cool);
  outline-offset: 2px;
}
.visually-hidden {
  position: absolute;
  width: 1px; height: 1px;
  padding: 0; margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}
```

**分段数量必须由 `fields.length` 决定，不得硬编码 8 或 9。** 后端 `fields[]` 是唯一权威（`HealthProfile` 实体含 9 个业务字段：`gender` / `birthDate` / `heightCm` / `weightKg` / `bmi` / `bloodType` / `allergyHistory` / `familyHistory` / `lifestyleHabits`；CONTEXT 中的「8 个字段」指 8 个**可输入**字段，`bmi` 为派生只读）。分段顺序 = `fields[]` 顺序 = 表单字段顺序。

**无障碍：** 视觉分段条 `aria-hidden="true"`（数字与图例已承载同一信息）；图例每项的 ✓/○ 配 `.visually-hidden` 的「已填/未填」文本，读屏不读孤立符号。

**不使用 `el-progress`：** 它无法表达逐字段分段，且 `.el-progress__text` 的颜色走 `--el-text-color-regular`（EP 变量），不在本站 token 表内。

**不做数字滚动动画：** 仪表盘 `stat-value` 的 `countUp` 不套用到完整度 —— 完整度是解释性数据，不是成就数据；且 `countUp` 依赖 `requestAnimationFrame`，在隐藏标签页会冻结（Phase 12 `wizard-not-auto-close` 同类风险）。

**数据来源与降级（Phase 5 `FALLBACK_METRIC_META` 先例）：** `useProfileCompletenessStore` 从后端取 `{ score, level, filledCount, totalCount, fields[] }`；档案不存在（404）或离线时，用前端静态目录 `src/utils/profileFields.ts` 合成 `score: 0 / level: '基础档案' / filled: false` 的完整 `fields[]`，保证图例与说明文案仍然完整可读。**后端返回的 `score`/`level` 优先，前端不做独立计算**（D-01）。

---

## Component Contract 2 — 字段级说明块与重要度徽标（S2）

**每个可输入字段的结构（标签行 → 输入控件 → 说明行）：**

```html
<el-form-item
  label="身高(cm)"
  prop="heightCm"
  data-field="heightCm"
  :class="{ 'is-focus-target': focusTarget === 'heightCm' }"
  :error="fieldErrors['heightCm']"
>
  <el-input-number v-model="form.heightCm" :min="50" :max="300" style="width: 100%" @change="clearField('heightCm')" />

  <p class="field-hint">
    <span class="hhs-imp-badge hhs-imp-badge--recommended">推荐</span>
    <span class="field-hint__text">用于生成 BMI，影响风险评估与健康报告</span>
  </p>
</el-form-item>
```

**排布规格（强制）：**

| 行 | 内容 | 对齐 |
|----|------|------|
| 第 1 行 | `el-form-item` 的 `label`（`label-width="120px"`，右对齐） + 输入控件 | 2 列 `el-row :gutter="16"` 栅格（既有档案页当前是 `gutter="20"`，**必须改为 16** —— EP 将 gutter 对半分给左右两侧，20 会渲染成每侧 10px 的 off-grid 值；16 → 每侧 8px = sm token）：性别｜出生日期 / 身高｜体重 / BMI｜血型 / 过敏史（整行）/ 家族病史（整行）/ 生活习惯（整行） |
| 第 2 行 | `.field-hint`：重要度徽标 + 「为何需要」说明 | 徽标在左，说明在右，`align-items: flex-start`，`gap: 4px`；说明占满剩余宽度，自然换行 |

**徽标放说明行首位而非标签行** —— 标签列只有 120px 宽，塞入「身高(cm)」+ 徽标会右对齐错位；放在说明行首位时徽标紧邻它所限定的文字，且 9 个徽标在左边缘形成可扫读的竖列。

**必须声明的 CSS（`prevention/Profile.vue` `<style scoped>`）：**

```css
/* theme.css 全局把标签锁成 500 字重（第 3 种字重）。本契约只有 400/600。 */
.profile-page :deep(.el-form-item__label) {
  font: 600 14px/1.5 var(--font-ui) !important;   /* Body + semibold */
}
/* EP 默认 18px 是 off-grid 值。 */
.profile-page :deep(.el-form-item) {
  margin-bottom: 16px;                            /* md */
}
/* theme.css:516-519 把输入框正文锁成 15px，归一到 14px Body。 */
.profile-page :deep(.el-input__inner),
.profile-page :deep(.el-textarea__inner) {
  font: 400 14px/1.5 var(--font-ui) !important;
}

/* EP 的 .el-form-item__content 是 flex + wrap + align-items:center；
   说明行必须以 100% 基准独占下一行，否则会和输入框挤在一行。 */
.field-hint {
  flex-basis: 100%;
  display: flex;
  align-items: flex-start;
  gap: 4px;                                       /* xs */
  margin-top: 4px;                                /* xs */
  font: 400 12px/1.4 var(--font-ui);              /* Label */
  /* 说明文字是本阶段的核心教育内容，必须达 AA。
     --text-3 浅色仅 3.97:1 不达 4.5:1，因此用 --text-2（Light 8.38:1 / Dark 11.30:1）。 */
  color: var(--text-2);
}
.field-hint__text {
  flex: 1;
  min-width: 0;
}

/* 自动聚焦目标（Contract 7）：程序化 focus 不触发 :focus-visible，
   因此用显式类给出可见环，失焦即移除。 */
.profile-page :deep(.el-form-item.is-focus-target) {
  outline: 2px solid var(--accent-cool);          /* 图形，Light 3.74:1 / Dark 12.64:1 */
  outline-offset: 2px;
  border-radius: var(--radius-sm);
}

/* 三档重要度徽标（与 theme.css 的全局族同源，此处只声明页面内的一致性） */
.hhs-imp-badge {
  display: inline-flex;
  align-items: center;
  height: 20px;                                   /* lg − xs */
  padding: 0 4px;
  border-radius: var(--radius-sm);
  font: 400 12px/1.4 var(--font-ui);
  white-space: nowrap;
  flex: none;
}
.hhs-imp-badge--required    { color: var(--text-1); background: var(--accent-warm-soft); }
.hhs-imp-badge--recommended { color: var(--text-2); background: transparent; border: 1px solid var(--border-strong); }
.hhs-imp-badge--optional    { color: var(--text-2); background: transparent; border: 1px dashed var(--border-strong); }

/* 窄屏：标签从右对齐改为上方堆叠，否则 120px 标签列挤掉输入宽度。
   用 EP 原生的 label-position，不要改 el-form-item 的 display —— 它是 flex 容器，
   改成 block 会连带破坏 .el-form-item__content 的 flex-wrap（说明行的 flex-basis: 100% 依赖它）。 */
```

```html
<el-form :label-position="isNarrow ? 'top' : 'right'" label-width="120px" :hide-required-asterisk="true">
```

```ts
// 与 <style scoped> 的 639px 断点保持一致；用 matchMedia 而不是 resize 轮询。
const isNarrow = ref(false)
const mql = window.matchMedia('(max-width: 639px)')
isNarrow.value = mql.matches
mql.addEventListener('change', (e) => { isNarrow.value = e.matches })
```

```css
@media (max-width: 639px) {
  .profile-page :deep(.el-form-item__label) {
    justify-content: flex-start;
    padding: 0 0 4px 0;                           /* 底部 xs */
  }
}
```

**必填星号：** `<el-form :hide-required-asterisk="true">`。三档徽标已表达重要度，EP 的二元红星会重复该语义，且 `--el-color-danger` `#f56c6c` 在纯白仅 2.90:1。`required: true` 校验规则**保留**（`gender` / `birthDate`）—— 见 Copywriting Contract 的承诺范围说明。

**「不填也可保存」承诺句的位置（**只出现一次**）：** 紧贴 `ProfileCompleteness` 之后、`FormErrorSummary` 之前，作为一条常驻说明：

```html
<p class="profile-promise">
  推荐与可选字段不填也可保存，不影响核心功能使用。
</p>
```
```css
.profile-promise {
  margin-bottom: 16px;                            /* md */
  font: 400 14px/1.5 var(--font-ui);              /* Body */
  color: var(--text-1);                           /* Light 17.01:1 / Dark 15.83:1 */
}
```

> **为什么不在 9 个字段下各重复一遍：** D-02 的意图是「用户必须明确知道推荐/可选不阻断保存」。同一句话重复 9 次会稀释信号并把信息密度推到人无法扫读（本阶段信息密度最高的部分已经是指明行 × 9）。放在完整度头部之后是**首屏必读位**，且与三档图例相邻，一次读完即完成认知。承诺句**不覆盖** `gender`/`birthDate`，这两个字段按 open_question 1 的建议保留 `required`。

---

## Component Contract 3 — 仪表盘提醒横幅（S4a）

**不使用 `el-alert`** —— EP 的默认插槽渲染进 `<p>`，两个按钮塞进去会被浏览器提升出去（Phase 11 `FormErrorSummary.vue:47-48` 记录了同一陷阱），且 `el-alert` 只提供一个关闭 ×，无法承载「完善档案 / 稍后提醒」双动作。改为自绘 `div.profile-banner`。

**位置：** `.dashboard` 的第一个子块，紧跟 `.welcome-section` 之后、`.stats-grid` 之前，`margin-bottom: 24px`。

```html
<div v-if="showBanner" class="profile-banner" role="status" aria-labelledby="profile-banner-title">
  <span class="profile-banner__icon" aria-hidden="true">
    <el-icon :size="18"><InfoFilled /></el-icon>
  </span>
  <div class="profile-banner__body">
    <p class="profile-banner__title" id="profile-banner-title">档案完整度 {{ score }}%，还缺 {{ missing.length }} 项</p>
    <p class="profile-banner__desc">{{ missingNames }}，健康评分与风险结果会按参考值估算。</p>
  </div>
  <div class="profile-banner__actions">
    <el-button type="primary" @click="goProfileWithFocus">完善档案</el-button>
    <el-button @click="dismiss">稍后提醒</el-button>
    <button type="button" class="profile-banner__close" aria-label="关闭提醒，7 天内不再显示" @click="dismiss">×</button>
  </div>
</div>
```

**可访问名（必须声明）：** `role="status"` 只提供 live region 语义，**本身不提供可访问名**；读屏用户跳转至该区域时会得到一个无名 region。因此 `aria-labelledby="profile-banner-title"` 指向横幅标题（`档案完整度 62%，还缺 3 项`），让区域在 landmarks / regions 列表中有可辨识名称。`id` 必须是**页面内唯一**（横幅全站单实例，此处无冲突）。**不要**用 `aria-label` 硬编码一句静态文本 —— 标题含 `{{ score }}` 与 `{{ missing.length }}` 动态值，`aria-labelledby` 才能随数据更新。

**必须声明的 CSS（`dashboard/Index.vue` `<style scoped>`）：**

```css
.profile-banner {
  display: flex;
  align-items: flex-start;
  gap: 12px;                                      /* sm-3 */
  padding: 16px;                                  /* md */
  margin-bottom: 24px;                            /* lg */
  background: var(--surface-1);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}
.profile-banner__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;                                    /* xl */
  height: 32px;
  flex: none;
  border-radius: var(--radius-full);
  background: var(--accent-warm-soft);            /* Light #fdf2ec / Dark #2d211b */
  color: var(--accent-warm);                      /* 图形：Light 3.32:1 / Dark 8.70:1 */
}
.profile-banner__body { flex: 1; min-width: 0; }
.profile-banner__title {
  font: 600 14px/1.5 var(--font-ui);              /* Body + semibold */
  color: var(--text-1);
}
.profile-banner__desc {
  margin-top: 4px;                                /* xs */
  font: 400 14px/1.5 var(--font-ui);              /* Body */
  color: var(--text-2);
}
.profile-banner__actions {
  display: flex;
  align-items: center;
  gap: 8px;                                       /* sm */
  flex: none;
}
.profile-banner__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;                                    /* xl */
  height: 32px;
  border-radius: var(--radius-full);
  font: 400 18px/1 var(--font-ui);                /* Heading 字号，符号非文本 */
  color: var(--text-2);
}
.profile-banner__close:hover { background: var(--surface-2); color: var(--text-1); }
.profile-banner__close:focus-visible {
  outline: 2px solid var(--accent-cool);
  outline-offset: 2px;
}
/* EP 给相邻按钮加 margin-left: 12px，与 gap 叠加后变成 20px。 */
.profile-banner__actions :deep(.el-button + .el-button) { margin-left: 0; }

@media (max-width: 768px) {
  .profile-banner { flex-wrap: wrap; }
  .profile-banner__actions { width: 100%; justify-content: flex-start; padding-left: 44px; }
}

@media (prefers-reduced-motion: reduce) {
  .profile-banner { transition: none; }
}
```

**触发与静默（D-05）：**

| 条件 | 结果 |
|------|------|
| `score >= 60` | 不渲染横幅（仅填性别+出生日期的用户约 60%，按成功标准 5 不打扰） |
| `score < 60` 且 `dismissedAt` 距今 < 7 天 | 不渲染 |
| `score < 60` 且未被静默 | 渲染 |
| 档案加载失败 | 不渲染（**绝不把失败渲染成 0%**） |

- **`完善档案`** → `router.push({ path: '/prevention/profile', query: { focus: firstMissingKey } })`；**不写入静默时间戳**（用户可能中途放弃，下次仍应看到提醒）。
- **`稍后提醒` 与关闭 × 是同一个动作** —— 写入 `localStorage.hhs_profile_reminder_dismissed_at = new Date().toISOString()` 并隐藏。本阶段**不发明「永久关闭」状态**（CONTEXT 未定义，静默期已足够）。
- **全站单实例：** 横幅只挂在 `.dashboard` 一处；档案页、风险页、评分页一律不渲染该横幅。
- **动画：** 横幅**不进入任何 GSAP 时间线** —— `dashboard/Index.vue` 的 `onMounted` 时间线只针对 `.welcome-section` / `.stat-card` / `.content-card` / `.entry-item` / `.alert-item` 等既有类名，新类名天然不在其中。横幅只用 `v-if` 切换，不新增入场动画（`gsap` 在隐藏标签页会冻结，Phase 12 已踩坑）。

---

## Component Contract 4 — 假设披露徽标（S3）

**共用组件** `EstimateBadge.vue`（`button` 元素，非 `span` —— 它是可点击控件，需要键盘可达与焦点语义）：

```html
<button type="button" class="hhs-estimate-badge" @click="$emit('complete')">
  <span>基于不完整档案估算</span>
  <span aria-hidden="true">·</span>
  <span class="hhs-estimate-badge__action">完善档案</span>
</button>
```

**必须声明的 CSS（`theme.css` 全局）：**

```css
.hhs-estimate-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;                                       /* xs */
  min-height: 32px;                               /* xl */
  padding: 4px 12px;                              /* xs / sm-3 */
  border: 1px solid var(--accent-warm);           /* Light 3.32:1 / Dark 8.70:1 */
  border-radius: var(--radius-sm);
  background: transparent;
  font: 400 12px/1.4 var(--font-ui);              /* Label */
  color: var(--text-2);                           /* 状态片段 Light 8.38:1 / Dark 11.30:1 */
  text-align: left;
  cursor: pointer;
}
.hhs-estimate-badge__action {
  color: var(--text-1);                           /* Light 17.01:1 / Dark 15.83:1 */
  text-decoration: underline;
  text-underline-offset: 2px;
}
.hhs-estimate-badge:hover { background: var(--accent-warm-soft); }
.hhs-estimate-badge:focus-visible {
  outline: 2px solid var(--accent-warm);
  outline-offset: 2px;
}
```

**落点与共存规则（必须遵守，否则会与风险等级标签打架）：**

| 落点 | 位置 | 渲染条件 |
|------|------|---------|
| `Risk.vue` 表格上方 | `.risk-disclosure` 块，`margin-bottom: 16px`，位于 `el-table` 之前、`el-card` 体顶部 | 最近一条评估的 `profileCompleteness != null && < 100` |
| `Risk.vue` 详情对话框 | `el-descriptions` 之后，`margin-top: 16px`；紧随其后是「本次评估采用的假设」列表 | `currentAssessment.profileCompleteness < 100 && assumptions?.length` |
| `Score.vue` 评分卡 | `.score-info` 内，`.score-tips` 之后、`.score-meta` 之前，`margin-bottom: 12px` | `scoreData.profileCompleteness != null && < 100` |

**与风险等级 `el-tag` 的三条隔离规则：**

1. **不同区域** —— 风险等级 `el-tag` 只出现在 `.el-table__cell` 与 `el-descriptions__content` 内；估算徽标只出现在表格**之外**的披露块、评分卡的 `.score-info`、对话框的 descriptions **之后**。徽标**永不**进入 `.el-table`。
2. **不同语汇** —— 风险等级：11px / 500 / 等宽字体 / 全圆角 / 实心填充；估算徽标：12px / 400 / 正文无衬线 / 6px 圆角 / 透明底 + 1px 实线边框 + 下划线动作。同屏出现时靠**字体族 + 圆角 + 填充方式**三重区分，不会被误认成第四种风险等级。
3. **不同行** —— 两者**永不同处一水平行**；同卡片内共存时最小垂直间距 **16px**（md）。

**条件收紧：** `profileCompleteness == null`（旧后端或字段缺失）时**不渲染徽标** —— 宁可不说，也不显示一个假的「完整度 0%」。

---

## Component Contract 5 — 风险评估就地补填面板（S4b）

**触发（判定顺序强制，先假设后分数）：** 点击 `创建评估` 时按下表**从上到下**求值，命中即停：

| 序 | 条件 | 结果 |
|----|------|------|
| 1 | `pendingAssumptions.length === 0`（含 `completeness` 为 null 或不可用） | **直接创建**，不展开面板 —— 没有要披露的假设就没有要补填的动机（Contract 8 末行，优先级最高） |
| 2 | `pendingAssumptions.length > 0` 且 `completeness.score < 100` | **不立即调用 API**：表格上方展开本面板，`scrollIntoView({ block: 'center' })`，聚焦第一个输入 |
| 3 | `pendingAssumptions.length > 0` 且 `completeness.score === 100`（理论上不应出现，仅作防御） | 直接创建；同时 `showError` 不触发（数据自相矛盾时不打扰用户，留待后端修正） |

**为什么假设优先于分数：** 面板的唯一内容就是假设列表（`pendingAssumptions`）。假设为空时展开面板会得到一个「标题 + 空列表 + 表单」的界面，用户无从理解为何要补填 —— 这违反 Contract 8「没有假设就没有要披露的内容」。`score < 100` 只描述完整度，不描述「本次评估实际代入了什么」。Contract 8 的末行与本表第 1 序是同一条规则的两种表述，执行时任一处命中均按第 1 序处理。

**结构（`el-alert` 只承载标题，列表与表单是兄弟节点）：**

```html
<section v-if="panelOpen" class="risk-estimate" aria-labelledby="risk-estimate-title">
  <el-alert type="warning" :closable="false" show-icon>
    <template #title>
      <span id="risk-estimate-title">系统将按以下假设估算本次风险评估</span>
    </template>
  </el-alert>

  <ul class="risk-estimate__list">
    <li v-for="item in pendingAssumptions" :key="item">{{ item }}</li>
  </ul>

  <div class="risk-estimate__form">
    <el-form ref="backfillRef" :model="backfill" :rules="backfillRules"
             label-width="120px" :hide-required-asterisk="true">
      <FormErrorSummary :summary-items="summaryItems" @focus-field="focusField" />
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="性别" prop="gender" data-field="gender" :error="fieldErrors['gender']">
            <el-radio-group v-model="backfill.gender" @change="clearField('gender')">
              <el-radio value="male">男</el-radio>
              <el-radio value="female">女</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="出生日期" prop="birthDate" data-field="birthDate" :error="fieldErrors['birthDate']">
            <el-date-picker v-model="backfill.birthDate" type="date" value-format="YYYY-MM-DD"
                            placeholder="选择日期" :disabled-date="disabledDate" style="width: 100%"
                            @change="clearField('birthDate')" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 第 2 行：可选补（D-06a）。没有 :rules 条目，留空是合法提交。 -->
      <el-row :gutter="16">
        <el-col :xs="24" :sm="12">
          <el-form-item label="身高（可选）" prop="heightCm" data-field="heightCm" :error="fieldErrors['heightCm']">
            <el-input-number v-model="backfill.heightCm" :min="50" :max="300"
                             placeholder="可不填" style="width: 100%"
                             @change="clearField('heightCm')" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="体重（可选）" prop="weightKg" data-field="weightKg" :error="fieldErrors['weightKg']">
            <el-input-number v-model="backfill.weightKg" :min="20" :max="500" :precision="1"
                             placeholder="可不填" style="width: 100%"
                             @change="clearField('weightKg')" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </div>

  <div class="risk-estimate__actions">
    <el-button type="primary" :loading="saving" @click="backfillAndCreate">保存档案并创建</el-button>
    <el-button @click="createAnyway">直接创建（按估算）</el-button>
    <el-button text @click="cancel">放弃创建</el-button>
  </div>
</section>
```

**必须声明的 CSS（`prevention/Risk.vue` `<style scoped>`）：**

```css
.risk-estimate {
  margin-bottom: 16px;                            /* md */
  padding: 16px;                                  /* md */
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

/* EP 的 alert 变量：warning 类型默认用 #e6a23c 作标题色，在 tint 上仅 2.04:1（AA 严重不达）。
   本契约把标题与正文一律压到 --text-1，图标保留暖橙（图形，3:1 达标）。 */
.risk-estimate :deep(.el-alert) {
  --el-alert-padding: 12px 16px;                  /* sm-3 / md */
  --el-alert-bg-color: var(--hhs-color-warning-tint);
  border-radius: var(--radius-md);
  color: var(--text-1);                           /* Light 15.86:1 / Dark 13.22:1 */
}
.risk-estimate :deep(.el-alert__title) {
  font: 600 14px/1.4 var(--font-ui);              /* 覆盖 EP 的 line-height: 24px */
  color: var(--text-1);
}
.risk-estimate :deep(.el-alert__icon) {
  color: var(--accent-warm);                      /* Light 3.32:1 / Dark 6.90:1 */
}

/* theme.css 全局 `ul { list-style: none; margin: 0; padding: 0 }`，
   假设列表必须重新声明为带悬挂缩进的真实列表。 */
.risk-estimate__list {
  margin: 12px 0 0;                               /* sm-3 */
  padding-left: 24px;                             /* lg */
  list-style: disc;
  display: flex;
  flex-direction: column;
  gap: 4px;                                       /* xs */
  font: 400 14px/1.5 var(--font-ui);              /* Body */
  color: var(--text-2);
}

.risk-estimate__form { margin-top: 16px; }        /* md */
.risk-estimate__form :deep(.el-form-item__label) {
  font: 600 14px/1.5 var(--font-ui) !important;
}
.risk-estimate__form :deep(.el-form-item) { margin-bottom: 16px; }
.risk-estimate__form :deep(.el-input__inner) {
  font: 400 14px/1.5 var(--font-ui) !important;
}

.risk-estimate__actions {
  margin-top: 16px;                               /* md */
  display: flex;
  align-items: center;
  gap: 8px;                                       /* sm */
  flex-wrap: wrap;
}
.risk-estimate__actions :deep(.el-button + .el-button) { margin-left: 0; }

@media (max-width: 639px) {
  .risk-estimate__actions { flex-direction: column; align-items: stretch; }
  .risk-estimate__actions :deep(.el-button) { width: 100%; }
}
```

**行为规格：**

| 动作 | 行为 |
|------|------|
| `保存档案并创建` | 校验补填表单 → `createProfile`/`updateProfile` → 重新拉取完整度（刷新假设列表）→ 关闭面板 → `createRiskAssessment()` |
| `直接创建（按估算）` | 不保存档案，直接 `createRiskAssessment()`；面板关闭 |
| `放弃创建` | 关闭面板，不创建评估，**零 toast**（Phase 11 D-12：用户主动放弃不是失败）；`cancel()` 复位 `panelOpen = false` 与补填草稿 |
| 面板打开时 | 表格上方的估算徽标**隐藏**（`v-if="!panelOpen"`），避免「提示 + 提示」叠两层 |
| 保存失败 | 校验错误走 `FormErrorSummary` + 内联（零 toast）；网络/服务错误走 `showError` 单条 toast；**输入不清空**（D-12） |

**假设列表的来源（R-2 修订，2026-11-09）：** `completeness.assumptions` —— 后端 `ProfileFieldCatalog.assumptionsFor(HealthProfile)` 产出的**聚合数组**，与结果页 `RiskAssessmentVO.assumptions` 由**同一个函数**填充，因此补填前面板说的、与创建后结果页显示的，逐字相同，前端**逐条原样渲染**，不做二次措辞。

> **为什么不再是 `fields[].assumption` 拼接（原草案）：** 逐字段拼接有两个不可修复的失真 —— ① 完全无档案时不存任何 `fields` 行，但风险引擎在该分支返回固定 30/25/25 并**只披露一条通用句**，拼接会产出三条引擎不会执行的代入；② 逐字段拼接无法区分「家族病史为空」与「已填但未提及糖尿病」，会把用户已填的字段说成缺失。`assumptionsFor` 以「档案是否存在」和「字段是否为空」为分支键，与引擎分支一一对应，故面板必须渲染它。
>
> **`fields[].assumption` 的保留用途：** 字段级元数据，供 `assumptionsFor` 内部引用、供档案页字段提示使用；它**不是**风险页面板的数据来源。`.risk-estimate__list` 的样式对两种来源都适用（视觉不变）。

---

## Component Contract 6 — 按钮/标签归一化与共享类（跨三页）

**页面级归一化（`profile-page` / `risk-page` / `dashboard` 各自 `<style scoped>`）：**

```css
/* 主按钮：theme.css 锁成 500 14px/1 → 600 14px/1.5（行高 1 会裁切中文字形下缘） */
.profile-page :deep(.el-button--primary),
.risk-page :deep(.el-button--primary),
.dashboard :deep(.el-button--primary) {
  font: 600 14px/1.5 var(--font-ui) !important;
}
/* 次按钮：EP 默认 --el-button-font-weight: 500 → 400 */
.profile-page :deep(.el-button:not(.el-button--primary)),
.risk-page :deep(.el-button:not(.el-button--primary)),
.dashboard :deep(.el-button:not(.el-button--primary)) {
  font-weight: 400 !important;
  line-height: 1.5 !important;
}
/* 表单标签：theme.css 锁成 500 → 600（本契约只有 400/600） */
.profile-page :deep(.el-form-item__label),
.risk-page :deep(.el-form-item__label) {
  font: 600 14px/1.5 var(--font-ui) !important;
}
```

> 三条规则分散写进三个页面的 scoped 块（`ElMessage` / 对话框这类 teleport 场景本轮不涉及，因此**不需要**往 `theme.css` 加全局按钮规则 —— 与 Phase 12 Contract 9 的 `.el-dialog` 全局规则不冲突、不复用）。

**共享徽标类（`theme.css` 全局，四处共用）：** `.hhs-completeness-meter`、`.hhs-tier-pill`、`.hhs-imp-badge`、`.hhs-estimate-badge` —— 完整声明见 Contract 1 / 2 / 4。放在全局的理由：同一徽标出现在 `prevention/Profile.vue`、`dashboard/Index.vue`、`prevention/Risk.vue`、`health/Score.vue` 四个 scoped 作用域，逐页复制会立刻漂移。

---

## Component Contract 7 — 跳转与自动聚焦（D-04）

| 入口 | 目标 URL | 说明 |
|------|---------|------|
| 仪表盘横幅 `完善档案` | `/prevention/profile?focus={firstMissingKey}` | `firstMissingKey` = `fields[]` 中第一个 `filled === false` 且 `importance` 优先 `required` → `recommended` → `optional` 的 `key` |
| 仪表盘紧凑完整度行 `完善档案` | `/prevention/profile`（无 focus） | 用户已在主动查看，不需要强制聚焦 |
| 估算徽标 | `/prevention/profile?focus={firstMissingKey}` | 同上 |
| 补填面板 | 不跳页 | 就地补填（D-06） |

**档案页消费（`onMounted`，数据到达之后）：**
1. 读 `route.query.focus`；
2. 该 key 必须存在于 `fields[]` 且 `filled === false`，否则**静默忽略**（不报错、不聚焦）；
3. 命中时给对应 `el-form-item` 加 `is-focus-target` 类，调用 `focusField(key)`（复用 Phase 11 `useFormServerErrors` 的选择器，内部已做 `scrollIntoView({ block: 'center' })`）；
4. 立刻 `router.replace({ query: {} })` —— 刷新页面不会重复滚动；
5. 目标字段 `blur` 时移除 `is-focus-target` 类。

**实现方式选定为 query 参数**（`open_question 3` 的两个候选之一）：可分享、刷新仍在、不需要新增一次性 store 状态位。

**只聚焦，不加动画。** `is-focus-target` 是静态 outline，不是过渡或脉冲；`prefers-reduced-motion` 下无任何差异。

---

## Component Contract 8 — 加载 / 失败 / 空态

| 场景 | 表现 |
|------|------|
| 完整度数据加载中 | **不渲染头部、不渲染骨架屏** —— 档案页已有的 `v-loading` 掩码覆盖表单区域，叠加骨架屏会出现两套加载指示（Phase 11 D-02 的同一纪律：一种状态一种指示） |
| 完整度数据加载失败 | **不渲染头部 / 横幅 / 徽标**，只走 `showError(err, 'profile.loadFailed')` 一条 toast。**绝不把失败渲染成「完整度 0%」** —— 0% 是新用户的合法值，失败与 0% 必须可区分 |
| 档案不存在（404） | 完整度按前端静态目录合成 `0% / 基础档案 / 全部未填`；档案页进入既有创建模式；横幅按 `< 60%` 规则触发 |
| 档案完整度 100% | 不渲染横幅、不渲染任何估算徽标；完整度头部保留（分段全填），影响句替换为「档案信息完整…」 |
| 风险评估列表加载失败 | `ElMessage.error` → 本阶段不改（Phase 14 迁移台账）；面板与表格各自独立 |
| 补填保存进行中 | `保存档案并创建` 按钮 `:loading`；`直接创建` 与 `放弃创建` **不禁用**（用户可以放弃补填） |
| 假设列表为空（`pendingAssumptions.length === 0`） | 面板**不展开**，直接创建 —— 没有假设就没有要披露的内容。**与 Contract 5 的判定顺序表第 1 序是同一条规则**：假设判空先于完整度判分。 |

---

## Copywriting Contract

**承诺范围（解决 `open_question 1` 的张力）：** `gender` / `birthDate` 保留 `required: true`（它们是档案的基线定义）；PRF-03 的「不填也可保存」承诺**只覆盖推荐与可选字段**，与其字面表述一致。

| Element | Copy |
|---------|------|
| 完整度标签 | `档案完整度` |
| 完整度数值 | `62%`（后端下发，前端只格式化） |
| 等级 · 基础 | `基础档案`（`< 60%`） |
| 等级 · 标准 | `标准档案`（`60–84%`） |
| 等级 · 完整 | `完整档案`（`≥ 85%`） |
| 完整度计数 | `已填 5 / 9`（`filledCount` / `totalCount`，模板插值） |
| 字段状态（已填） | `✓` + 读屏文本 `已填` |
| 字段状态（未填） | `○` + 读屏文本 `未填` |
| 不填承诺 | `推荐与可选字段不填也可保存，不影响核心功能使用。` |
| 影响句 · 缺必需（有档案，缺出生日期） | `缺少出生日期，风险评估将按 35 岁与通用模型估算。` |
| 影响句 · 缺必需（有档案，仅缺性别） | `缺少性别，健康报告与 AI 建议将按通用表述生成。` |
| 影响句 · 缺必需（无档案） | `尚未填写健康档案，评分与风险结果将按通用参考值估算。` |
| 影响句 · 缺推荐（BMI 未记录） | `缺少身高、体重或 BMI，风险评估将按 BMI 参考值 22.0 估算。` |
| 影响句 · 缺推荐（BMI 已记录但身高体重不全） | `缺少身高或体重，BMI 无法随身体变化重算，风险评估沿用已记录的 BMI。` |
| 影响句 · 仅缺可选 | `缺少血型、过敏史、家族病史或生活习惯，健康报告与 AI 建议的个性化程度会降低。` |
| 影响句 · 完整 | `档案信息完整，评分与风险结果均基于真实数据。` |
| 重要度 · 必需 | `必需` |
| 重要度 · 推荐 | `推荐` |
| 重要度 · 可选 | `可选` |
| 字段说明 · 性别 | `用于健康报告与 AI 建议中的性别化解读` |
| 字段说明 · 出生日期 | `用于计算年龄，影响心血管与糖尿病风险评估` |
| 字段说明 · 身高 | `用于生成 BMI，影响风险评估与健康报告` |
| 字段说明 · 体重 | `用于生成 BMI，影响风险评估与健康报告` |
| 字段说明 · BMI | `用于风险评估的 BMI 代入；缺失按 22.0 估算` |
| 字段说明 · 血型 | `记录在健康报告与 AI 建议中，供就医与用血场景参考` |
| 字段说明 · 过敏史 | `让 AI 建议避开与过敏史冲突的饮食与用药提醒` |
| 字段说明 · 家族病史 | `家族糖尿病史直接影响糖尿病风险评估得分` |
| 字段说明 · 生活习惯 | `用于健康评分与 AI 建议的针对性` |
| 横幅标题 | `档案完整度 {score}%，还缺 {N} 项` |
| 横幅正文 | `缺少{字段列表}，健康评分与风险结果会按参考值估算。` |
| 横幅主 CTA | `完善档案` |
| 横幅次 CTA | `稍后提醒` |
| 横幅关闭（可访问名） | `关闭提醒，7 天内不再显示` |
| 估算徽标（全站同一句） | `基于不完整档案估算 · 完善档案` |
| 假设区块标题（详情对话框） | `本次评估采用的假设` |
| 补填面板 alert 标题 | `系统将按以下假设估算本次风险评估` |
| 补填面板主 CTA | `保存档案并创建` |
| 补填面板次 CTA | `直接创建（按估算）` |
| 补填面板放弃 | `放弃创建`（动词 + 宾语；底部 text 按钮，与 Phase 12 `12-UI-SPEC.md` 的 `放弃设置` 同构；**禁止退化为单字通用词** —— 这是 Dimension 1 的硬性回归点） |
| 补填字段标签 | `性别` / `出生日期` / `身高（可选）` / `体重（可选）` |
| 假设条目 · 缺出生日期 | `缺少出生日期，年龄按 35 岁估算` |
| 假设条目 · 缺 BMI | `缺少 BMI，按 22.0 估算` |
| 假设条目 · 缺家族病史（字段为空） | `缺少家族病史，糖尿病风险评估按「无家族史」计算` |
| 假设条目 · 家族病史已填但未提及糖尿病 | `家族病史未提及糖尿病，糖尿病风险评估按「无家族史」计算` |
| 假设条目 · 完全无档案 | `未填写健康档案，风险得分按通用参考值估算` |
| 假设条目 · 评分路径无实时指标 | `缺少健康指标数据，指标维度按中性 70 分计算`（`HealthScoreVO.assumptions`，后端 `MetricsScorer` 空指标分支产出；当前无前端表面渲染该数组，但它是 API 可见文案，故仍受本契约约束） |
| 假设条目 · 评分路径无 BMI 实时指标 | `缺少 BMI 指标，体重维度按参考值估算`（`HealthScoreVO.assumptions`，后端 `DimensionScoreCalculator` 在体重惩罚因缺 `bmi` 键而跳过时产出；同上，API 可见文案） |
| 档案不存在时的引导 | `填写性别与出生日期即可启用风险评估与健康评分，其余字段可随时补充。` |
| 保存档案成功 | `档案已保存`（沿用既有 `ElMessage.success` 措辞习惯） |
| 错误态 · 加载失败 | `无法加载健康档案，请稍后重试`（字典键 `profile.loadFailed`，已存在，**不新增**） |

**文案纪律（沿用 Phase 11/12）：** 每条失败说明「什么失败了 + 下一步怎么做」；不出现「操作失败」「请求失败」；不出现英文原始字段名（`heightCm` 之类一律走 `FIELD_LABELS`）。**每个交互标签必须是「动词 + 宾语」**——本阶段全部可见动作标签清单：`完善档案` / `稍后提醒` / `保存档案并创建` / `直接创建（按估算）` / `放弃创建`。通用单字词（`取消` / `确定` / `关闭`）**一律不得作为可见标签出现**；横幅的 `×` 关闭控件通过 `aria-label="关闭提醒，7 天内不再显示"` 获得完整的动词+宾语可访问名。

**既有单字标签的整改（本阶段顺带收口，见 Appendix 修订记录 R-1）：** 本阶段重排字体的两个文件里存在既有的 `取消` 可见标签，与上述纪律冲突，必须一并改为「动词 + 宾语」：

| 文件 | 位置 | 现行 | 改为 |
|------|------|------|------|
| `views/prevention/Profile.vue` | 编辑态第二按钮（`cancelEdit`） | `取消` | `放弃编辑` |
| `views/health/Score.vue` | 报告生成中对话框的取消按钮（`cancelGenerate`） | `取消` | `放弃生成` |

`aria-label` 与 `title` 属性不受此约束（它们本就该是完整动词 + 宾语，例如 `关闭提醒，7 天内不再显示`）。新增的机器可检查判据：重写后的两个文件**不得出现**以 `>取消<` / `>确定<` / `>关闭<` 形式出现的可见按钮文本。

**假设句的格式规则（后端必须遵守）：** `缺少{字段中文名}，{替代方式}估算` 或当替代方式是一个具体数值时 `缺少{字段中文名}，{参数}按 {数值} 估算`。一句话说清「缺什么 + 用什么替代」，前端**逐条原样渲染**，不做二次解释（D-03）。

---

## UI Considerations

> 由 ui-phase 的 UI-consideration probe 在 Step 9.5 生成（8 个表面 / 26 项适用考量）。
> 此处覆盖形态驱动的 UI *状态*；空态/错误态的具体文案见上方 `## Copywriting Contract`（不重复）。

Applicable state considerations resolved: 26 covered (24 explicit, 2 backstop, 0 unresolved)

表面编号：E1 完整度头部块（档案页）· E2 字段说明块 × 9 · E3 仪表盘提醒横幅 · E4 估算徽标 · E5 风险评估假设列表 · E6 就地补填表单 · E7 档案页表单整体 · E8 仪表盘紧凑完整度行

### 长文本与溢出（10 项）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| long-text | E2 字段说明（每条 1–2 行） | ✅ covered | `flex-basis: 100%` + `flex: 1; min-width: 0`，自然换行、不截断、不设 max-height。9 条说明最长 24 个中文字符（「让 AI 建议避开与过敏史冲突的饮食与用药提醒」），半宽列（≥640px）下占 2 行。 |
| long-text | E1 图例（9 个字段名） | ✅ covered | `.completeness__legend { flex-wrap: wrap; gap: 8px 16px }`，窄屏自动折行；每项 `white-space: nowrap` 保证「✓ 家族病史」不被拆断。 |
| long-text | E3 横幅标题与正文 | ✅ covered | 正文 `flex: 1; min-width: 0` 自然换行；标题不截断；最长的缺失字段枚举（9 项全缺）在 640px 下约 3 行，`flex-wrap: wrap` 让动作区整体下移。 |
| long-text | E4 估算徽标（窄屏） | 🧪 backstop | 文案固定 14 个中文字符，`min-height: 32px` + `padding: 4px 12px`，320px 下不折行（实测字宽 ≈ 174px）。需在 320px 视口做一次视觉复核；若折行则允许两行并移除 `min-height` 约束。 |
| long-text | E5 假设条目 | ✅ covered | 每条 `flex-direction: column; gap: 4px` 的 `<li>`，`list-style: disc` + `padding-left: 24px` 悬挂缩进，长句自然折行且第二行左对齐于首行文字（非项目符号）。 |
| long-text | E6 补填表单标签 | ✅ covered | 标签 `性别` / `出生日期` ≤4 字，`身高（可选）` / `体重（可选）` 6 字，`label-width="120px"` 均充裕；<640px 改为上置标签（Contract 2 的同一媒体查询）。 |
| long-text | E8 仪表盘紧凑行 | ✅ covered | 头部 `flex-wrap: wrap`，`62%` 与 pill 不折行；`完善档案` 链接独占一行。 |
| long-text | E3 横幅动作区 | 🧪 backstop | 3 个控件（2 按钮 + ×）在 640–768px 之间最紧。已声明 `flex-wrap` + `padding-left: 44px`；需在 640px / 768px 两个断点做视觉复核。 |
| overflow | E1 图例 | ✅ covered | 容器 `width: 100%`，`flex-wrap: wrap`，永不横向溢出；无 `overflow: hidden`（不裁字）。 |
| overflow | E5 假设列表 | ✅ covered | `padding-left: 24px` 后仍可折行，超长无空格英文（异常数据）用 `word-break: break-word`。 |

### 空 / 加载 / 错误（10 项 → 沿用 Phase 11 契约）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| empty | E1 完整度（无档案） | ✅ covered | 合成 `0% / 基础档案 / 9 项全 ○`，不是空白块 —— 空档案本身就是要被引导的状态。 |
| empty | E2 字段说明 | ✅ covered | 说明文案来自静态目录，与是否有数据无关，**永不为空**；字段控件为空值是正常输入态。 |
| empty | E5 假设列表 | ✅ covered | `pendingAssumptions.length === 0` → 面板整块不渲染（Contract 8），不出现空列表。 |
| empty | E6 补填表单 | ✅ covered | 空值即空表单；`保存档案并创建` 保持可点击，点击后走校验给出内联提示（不是灰色死按钮）。 |
| loading | E1 / E7 档案页 | ✅ covered | 单一 `v-loading` 掩码；不叠加骨架屏；完整度头部 `v-if` 延迟渲染。 |
| loading | E3 / E4 / E8（仪表盘与结果页） | ✅ covered | 数据未到达前**不渲染** —— 显示「0%」或「基于不完整档案」都比不显示更误导。 |
| loading | E6 补填保存 | ✅ covered | 仅主按钮 `:loading`；另外两个动作保持可用。 |
| error | E2 / E6 / E7 校验失败 | ✅ covered | `FormErrorSummary` + 内联 `:error`，**零 toast**（Phase 11 D-01/D-12）。 |
| error | E1 / E3 / E4 / E8 加载失败 | ✅ covered | 整块不渲染 + `showError` 单条中文 toast；不把失败画成 0%。 |
| error | E6 网络/服务失败 | ✅ covered | 单条 toast（`showError`），表单保持打开、输入保留。 |

### 部分数据（3 项 → null 合法）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| partial | E1 / E8 分段条 | ✅ covered | 分段数 = `fields.length`，已填/未填逐段着色；`totalCount` 由数组长度得出，不硬编码 8 或 9。 |
| partial | E2 字段状态 | ✅ covered | 逐字段 ✓ / ○ 独立判定；`bmi` 为派生字段，`filled` 由后端决定，前端不自行推算。 |
| partial | E6 只填一项 | ✅ covered | 主按钮保持可用，点击后校验缺项并内联提示；**不禁用**（禁用会让人以为被阻断，违反「绝不硬性阻断」）。 |

### 零 / 一 / 多（2 项）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| zero-one-many | E5 假设列表 | ✅ covered | 0 条 → 面板不渲染；1 条 → 单行列表；N 条 → 纵向列表，`gap: 4px`，不出现「1 条假设」这类计数文案（中文无单复数，且计数无价值）。 |
| zero-one-many | E1 分段与图例 | ✅ covered | 项数固定为 `fields.length`（后端权威，当前 9），随用户数据变化的是 `filled` 而非项数；不出现 0 项分支。 |

### 其他（1 项）

| Category | Element(s) | Status | Resolution |
|----------|------------|--------|------------|
| populated | E1 / E2 / E4 | ✅ covered | 正常态：分段全填、图例全 ✓、影响句切换为「档案信息完整，评分与风险结果均基于真实数据。」；横幅与徽标全部不渲染。 |

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not required |
| 第三方 registry | none | not required |

本阶段**不引入任何外部 registry 组件**。所有组件来自已安装的 `element-plus@2.14.0`，三个新组件为本仓库自写。

---

## Checker Sign-Off

- [x] Dimension 1 Copywriting: PASS
- [x] Dimension 2 Visuals: PASS
- [x] Dimension 3 Color: PASS
- [x] Dimension 4 Typography: PASS
- [x] Dimension 5 Spacing: PASS
- [x] Dimension 6 Registry Safety: PASS
- [x] Dimension 7 Inventory Provenance: PASS

**Approval:** approved — 2026-11-09 (7/7 PASS)

---

## Appendix — 修订记录

### R-1（2026-11-09，plan-check 反馈修订）

本契约在 plan-check 阶段被发现多处**文案与真实行为不一致**，以及一处既存单字标签漏网。逐条修订如下（修订后契约仍是唯一设计权威，plan 与契约同步）：

| 条目 | 问题 | 修订 |
|------|------|------|
| 影响句 · 缺必需（有档案，缺出生日期） | 原句 `缺少性别或出生日期，风险评估将按 35 岁与通用模型估算。` 对**完全无档案**的新用户为假：`RiskAssessmentServiceImpl` 的 null-profile 分支返回固定 30/25/25，不使用 35 岁。35 岁路径只对 `birth_date IS NULL` 的历史行可达（`HealthProfileRequest.birthDate` 是 `@NotNull`）。另一处错配：把 `性别` 折进「35 岁」句也说不通 —— `gender` 只被健康报告/AI 提示词消费，不被风险引擎消费 | 拆为三行：「有档案，缺出生日期」保留 35 岁措辞；新增「有档案，仅缺性别」行 `缺少性别，健康报告与 AI 建议将按通用表述生成。`；新增「无档案」行 `尚未填写健康档案，评分与风险结果将按通用参考值估算。`。分支依据是 `ProfileCompletenessVO.hasProfile`（13-01 新增）与两个必需字段各自的 filled 状态 |
| 影响句 · 缺推荐 | 原句称「BMI 与体重维度评分将按参考值估算」—— 体重维度读的是实时指标 `bmi`，不读档案；且当 `bmi` 列已记录（身高体重被清空）时引擎**沿用已记录值**而非 22.0，句子同样失真 | 拆为两行：BMI 未记录 → `缺少身高、体重或 BMI，风险评估将按 BMI 参考值 22.0 估算。`；BMI 已记录但身高体重不全 → `缺少身高或体重，BMI 无法随身体变化重算，风险评估沿用已记录的 BMI。`。分支依据是payload 的 `assumptions` 数组是否含 bmi 条目的句子（与 `RiskAssessmentServiceImpl:120` 的 `bmi != null ? bmi : 22` 同源），而非前端重算 bmi 规则 |
| 字段说明 · 身高/体重/BMI | 原句称「用于计算 BMI，影响体重维度评分」「体重维度评分的直接输入」—— 体重维度只消费 `RealtimeMetric` | 身高/体重改为 `用于生成 BMI，影响风险评估与健康报告`；BMI 改为 `用于风险评估的 BMI 代入；缺失按 22.0 估算` |
| 字段说明 · 性别 | 原句称「用于健康报告与 AI 建议的个性化解读」尚可，但需要排除风险引擎 | 收紧为 `用于健康报告与 AI 建议中的性别化解读` —— `gender` 只被 `AlertAIAnalysisService` / `AIChatServiceImpl` / `HealthReportServiceImpl` 读取，**不被** `RiskAssessmentServiceImpl` 读取 |
| 字段说明 · 生活习惯 | 原句「用于档案完整度与 AI 建议的针对性」点名了一个不消费它的模块 | 改为 `用于健康评分与 AI 建议的针对性`（真实消费方：`ProfileScorer` 扣分 + `AIChatServiceImpl`） |
| 假设条目 · 缺家族病史 | 原句在**已填但未提及糖尿病**（如 `父亲有高血压`）时也说「缺少家族病史」，声称一个用户已经填了的字段缺失 | 拆为两行：字段为空 → `缺少家族病史，…`；已填但关键词缺失 → `家族病史未提及糖尿病，糖尿病风险评估按「无家族史」计算`。两行由后端 `ProfileFieldCatalog.assumptionsFor` 按 `familyHistory` 是否为空选择 |
| 既有单字标签 | `prevention/Profile.vue` 与 `health/Score.vue` 各有以一个 `取消` 为可见文本的按钮，与本契约「禁止单字通用标签」冲突，且两文件本阶段都要重排字体 | 见 Copywriting Contract 的「既有单字标签的整改」表：改为 `放弃编辑` / `放弃生成`，并加机器可检查判据 |

### R-2（假设列表来源，2026-11-09）

| 条目 | 问题 | 修订 |
|------|------|------|
| Contract 5 假设列表来源 | 原契约写 `completeness.fields.filter(f => !f.filled && f.assumption).map(f => f.assumption)`。逐字段拼接在「完全无档案」时产出三条引擎不会执行的代入（引擎该分支只披露一条通用句），且无法区分「家族病史为空」与「已填但未提及糖尿病」 | 改为渲染 `completeness.assumptions`（后端 `ProfileFieldCatalog.assumptionsFor` 产出），与 `RiskAssessmentVO.assumptions` 同函数、逐字相同。视觉（`.risk-estimate__list`）不变 |
| Contract 5 补填字段集 | 原契约表只列 `性别` / `出生日期` 两个字段，而 CONTEXT D-06 括注了「身高体重可选补」 | 补齐为 4 个输入：性别/出生日期必需，身高/体重可选（无校验规则，留空合法，`:min`/`:max` 对齐 DTO 边界，打开面板时按已有档案预填）。落实 D-06a |

### R-3（未受影响的裁定，2026-11-09）

本契约的 Color / Typography / Spacing / Registry Safety 四节未做任何改动；Checker Sign-Off 的 7/7 PASS 结论维持。修订仅涉及 Copywriting Contract 的文案与 Contract 5 的两个结构性说明。

**未修订项说明：** 假设条目与影响句中「体重维度」以外的其余「维度」措辞均未改动，因为 `MetricsScorer` 确实按实时指标产出维度分。本契约的 Color / Typography / Spacing / Registry 四节不受 R-1/R-2 影响，7/7 PASS 结论维持（见 R-3）。

### R-4（骨架与文案同步，2026-11-09，最终修订轮）

R-1/R-2 改的是**文字说明**，但两处 HTML 骨架仍保留了修订前的形态。执行者照抄骨架会直接回退修订，因此本轮把骨架本身同步：

| 条目 | 问题 | 修订 |
|------|------|------|
| Contract 2 骨架示例 | L456 的 `<span class="field-hint__text">用于计算 BMI，影响体重维度评分</span>` 仍是 R-1 已判为失真的句子（体重维度只消费 `RealtimeMetric`，不读档案） | 骨架字符串改为 Copywriting Contract「字段说明 · 身高」行：`用于生成 BMI，影响风险评估与健康报告`。契约内唯一字符串，逐字相同 |
| Contract 5 骨架字段集 | R-2 只改了说明表（4 个输入），骨架仍只有 `性别` + `出生日期` 一个 `el-row` | 骨架补第 2 个 `el-row :gutter="16"`：`身高（可选）` / `体重（可选）` 两个 `el-input-number`，`:min`/`:max` 对齐 DTO（50–300 cm / 20–500 kg），体重带 `:precision="1"`，**无 `:rules` 条目**、不设 `:disabled`，`placeholder="可不填"`。落实 D-06a |
| Copywriting Contract 假设条目 | `HealthScoreVO.assumptions` 的两条评分路径句子（`缺少健康指标数据，指标维度按中性 70 分计算` / `缺少 BMI 指标，体重维度按参考值估算`）虽无前端表面渲染，但经 API 对用户可见，未入表则契约不再是文案唯一来源 | 补两行「假设条目 · 评分路径无实时指标 / 无 BMI 实时指标」，与 13-03 的后端常量逐字一致 |
