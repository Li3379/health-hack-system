---
phase: "14"
slug: "error-rollout"
status: draft
shadcn_initialized: false
preset: none
created: "2026-11-10"
---

# Phase 14 — 全站收尾排查与回归验证 — UI Design Contract

> 全站错误呈现收口阶段的视觉与交互契约。由 gsd-ui-researcher 生成，gsd-ui-checker 校验。
>
> **本契约不新建视觉语言。** 它在 Phase 11（错误地基）、Phase 12（阈值向导）、Phase 13（档案引导）之上**只做两件事**：把已有错误呈现机制定义成可被 87 个调用点复制的规格，并补上两处确实缺失的表面（403 全页、404 内容区）。任何本契约未定义的错误呈现形态都视为缺陷，而非自由度。

**本阶段四个表面：**

| # | 表面 | 契约 | 落点 |
|---|------|------|------|
| S1 | 403 全页解释页 | Contract 2 | `settings/DevicePlatformConfig.vue` 为主承载；任何页面的首个主查询返回 `forbidden` 时降级为它 |
| S2 | 404 内容区解释块 | Contract 3 | 详情/单资源页面（`screening/Detail.vue`、`health/Metrics.vue` 编辑态、`goals/Index.vue` 单条操作） |
| S3 | 表单内联错误一致性规格 | Contract 4 | 全站 15 个含 `<el-form>` 的页面 + 3 个弹窗表单 |
| S4 | Toast 类型区分规格 | Contract 5 | `utils/showError.ts` 唯一出口 |

**跨表面唯一真值表：** Contract 1。执行者只需要读那一张表就能决定「这次失败该不该弹、弹什么、落在哪」。S1–S4 是它的展开。

### 视觉焦点（每个表面只有一个主焦点）

| 表面 | 主焦点（第一视点） | 建立手段 | 次级层次 |
|------|-------------------|---------|----------|
| S1 403 全页 | **`无权访问此页面` 标题** | 唯一 24px/600/1.2（Display）；`--text-1`（Light 15.85:1 / Dark 16.66:1）；页面垂直居中 | 说明句 14px/400 `--text-2`（8.38:1 / 11.30:1，**不是 `--text-3`**）；`返回首页` 主按钮 |
| S2 404 内容区 | **`资源不存在` 标题** | 唯一 18px/600/1.3（Heading）；`--text-1` | 说明句 14px/400 `--text-2`；动作对 `返回列表`（primary）/ `重新加载该记录`（default） |
| S3 表单内联 | **首个出错控件的红色描边** | 1px `--hhs-color-error-text` 边框（Light 6.47:1 / Dark 6.45:1，≥3:1 满足 1.4.11） | `el-form-item__error` 12px/400/1.4 红字；顶部汇总块 |
| S4 toast | **文案本身** | 14px/400/1.5 `--hhs-color-error-text`；EP `error` 图标 | `重试` / `打开编辑` 动作（600 字重，同色） |

**跨表面优先级（同一屏内共存时）：**
1. **状态块优先于 toast**：页面级/内容区状态块一旦渲染，同一失败**不得**再出现 toast（Phase 11 D-02）。这是「一次失败恰好一条提示」的唯一实现方式。
2. **汇总块优先于内联**：汇总块位于表单第一子节点，是表单级第一视点；内联错误是逐字段的第二层次。二者同色同字号，靠位置与可点击性区分。
3. **403 优先于 404**：若一次加载同时可被判为两者（不会发生，但防御），按 403 处理 —— 权限缺失是更根本的原因。

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none（非 shadcn 项目 — 项目已有自成体系的 Element Plus + CSS token 系统） |
| Preset | not applicable |
| Component library | `element-plus@2.14.0`（已安装；本阶段零新增依赖） |
| Icon library | `@element-plus/icons-vue@2.3.2`（`Lock` / `DocumentDelete` / `WarningFilled` / `CircleCloseFilled`） |
| Font | `--font-ui`: `'DM Sans', 'Noto Sans SC', system-ui, …`（theme.css:74） |

**为什么不是 shadcn：** `REQUIREMENTS.md` `## Out of Scope` 明确禁止引入第二套 UI 库；`components.json` 不存在，技术栈为 Vue 3。shadcn 初始化门禁不适用。

**已确认的既有资产（本阶段复用，零重建）：**

| Asset | 路径 | 本阶段用法 |
|-------|------|-----------|
| `ErrorState` | `src/components/common/ErrorState.vue` | S1/S2 唯一载体；本阶段扩展 2 个可选 prop（Contract 2/3） |
| `FormErrorSummary` | `src/components/common/FormErrorSummary.vue` | S3 汇总块；**零修改** |
| `useFormServerErrors` | `src/composables/useFormServerErrors.ts` | S3 投影核心；本阶段扩展 1 个可选参数 `labels` |
| `showError` | `src/utils/showError.ts` | S4 唯一出口；本阶段扩展 1 个可选参数 `presentStates` |
| `normalizeApiError` / `ApiError` / `ErrorKind` | `src/utils/appError.ts` | 本阶段新增两个 kind：`forbidden` / `notFound` |
| `ERROR_MESSAGES` / `FIELD_LABELS` | `src/utils/errorMessages.ts` | 新增 26 键 + 22 标签（Copywriting Contract） |
| 错误样式全局块 | `theme.css:1207-1279` | `body .el-message*` / `body .el-form-item__error` / `.error-toast*`；**不重定义，只追加** |

---

## Component Inventory

Enumerated by `node -e "const fs=require('fs');const b='node_modules/element-plus/es/components';console.log(fs.readdirSync(b).filter(n=>fs.existsSync(b+'/'+n+'/index.mjs')).length)"` — 91 components — element-plus@2.14.0 — 2026-11-10.

Icons enumerated by `node -p "Object.keys(require('@element-plus/icons-vue')).length"` — 293 icons — @element-plus/icons-vue@2.3.2 — 2026-11-10.

（`readdirSync` 直接返回 124 个目录，其中 91 个含 `index.mjs` 组件入口。`@element-plus/icons-vue` 版本由 `element-plus@2.14.0` 的 peer 约束解析得出。）

下表只列出本阶段会用到的子集，**不是封闭白名单**；执行者可继续使用该库导出的任何组件。

| Component | Import path | 本阶段用途 |
|-----------|-------------|-----------|
| `ElIcon` | `element-plus` | S1/S2 图标圆；S4 图标由 EP 依据 `type` 自动渲染，不手工传 |
| `ElButton` | `element-plus` | S1/S2 动作；S3 表单提交 |
| `ElForm` / `ElFormItem` | `element-plus` | S3 全部表单；`prop` + `:error` + `data-field` |
| `ElMessage` | `element-plus` | S4 唯一 toast 原语（仅经 `showError` 调用） |
| `ElMessageBox` | `element-plus` | 既有删除确认；`'cancel'`/`'close'` 拒绝必须零提示 |
| `ElAlert` | `element-plus` | **仅** `FormErrorSummary` 内部使用；不得新增用法（默认插槽渲染进 `<p>`，多动作会被提升出去） |
| `ElEmpty` | `element-plus` | 空结果；**ErrorState 永不替代空态** |
| `ElResult` | `element-plus` | **不使用** —— 自绘 `ErrorState` 才能控制双主题色与页级/内容区两档几何 |
| `ElProgress` / `ElTag` / `ElSegmented` | `element-plus` | **不使用** —— 分别受 Phase 13 的分段能力限制、`theme.css:542-549` 的 `!important` 字级锁、以及项目选择控件约定约束 |

---

## Spacing Scale

声明的间距值**全部是 4 的倍数**，且每个 token 绑定到本契约中某个具体声明。

| Token | Value | Usage（绑定到具体声明） |
|-------|-------|------------------------|
| xs | 4px | `el-form-item__error` 的 `padding-top`（`body .el-form-item__error { padding-top: 4px }`，Phase 11 已归一化 EP 的 2px）；toast 内动作按钮内边距垂直分量（`.error-toast__retry, .error-toast__action { padding: 4px 8px }`）；S2 说明句内动作间隙（`.error-state__actions { gap: 4px }`） |
| sm | 8px | S1/S2 图标圆 → 标题、标题 → 说明各 8px（`.error-state { gap: 8px }`）；S2 动作对水平间距（`.error-state__actions { gap: 8px }`）；S1/S2 标题与说明之间的固定间隔；toast 动作按钮内边距水平分量（`4px 8px`） |
| sm-3 | 12px | toast 垂直内边距（`body .el-message { --el-message-padding: 12px 16px }`，Phase 11 已归一化 EP 的 11px 15px）；S3 汇总块 alert 垂直内边距（`padding: 12px 16px 8px`） |
| md | 16px | S3 汇总块底部间距（`.form-error-summary { margin-bottom: 16px }`）；说明 → 动作的**实际**间距（`.error-state { gap: 8px }` + `.error-state__actions { margin-top: 16px }` = 24px，见下条说明）；toast 文案 → 动作间距（`.error-toast { gap: 16px }`）；S3 汇总列表水平内边距（`padding: 0 16px 12px 40px`） |
| lg | 24px | S1/S2 说明句 → 动作区的**渲染值**（上面 8px gap + 16px margin = 24px，与 Phase 11 的 lg 行一致，本阶段保持不变）；S1/S2 水平内边距（`padding: 48px 24px`） |
| xl | 32px | toast 视口左右留白（EP 自带 `max-width: calc(100% - 32px)`，即每侧 16px，**沿用不改**） |
| 2xl | 40px | S3 汇总列表缩进（`padding-left: 40px` = 16 alert + 16 icon + 8 margin，与 alert 标题左缘对齐） |
| 3xl | 48px | S1/S2 块级垂直内边距（`padding: 48px 24px`） |
| 4xl | 64px | S1/S2 图标圆直径（`width/height: 64px`） |

**No exceptions.** 全部为 4 的倍数。`1px`/`2px`/`3px` 只出现在边框、描边与焦点环的**宽度**上，不属于间距。

**本阶段新增的一条几何刻度（不是新间距值）：** S1 页级状态的垂直高度 `min-height: calc(100vh - 104px)`，其中 `104px = 64px（.header 高）+ 40px（.el-main 上下 padding 20px × 2）`。两个分量都是 4 的倍数，合计仍是 4 的倍数。**不得**改写成 `calc(100vh - 100px)` 或类似值。

**两条必须覆盖的 Element Plus 默认值（否则 S3 的「所有表单一模一样」不成立）：**

| EP 默认 | 归一化目标 | 声明位置 |
|---|---|---|
| `.el-input__wrapper { box-shadow: none !important }`（theme.css:506）+ `border: 1px solid var(--border) !important`（theme.css:504）**压掉**了 EP 的 `.el-form-item.is-error ... { box-shadow: 0 0 0 1px var(--el-color-danger) inset }`（无 `!important`） | `border-color: var(--hhs-color-error-text) !important`，聚焦时加 `box-shadow: 0 0 0 3px var(--hhs-color-error-tint) !important` | Contract 4（theme.css 追加块） |
| `.el-input__wrapper.is-focus { box-shadow: 0 0 0 3px var(--accent-cool-soft) !important }`（theme.css:512-515）会在出错且聚焦时把错误描边换成 accent 环 | 错误态**高于**焦点态：先命中错误描边，再由 `.is-focus` 叠加 tint 光晕（见 Contract 4 特异性说明） | Contract 4 |

---

## Typography

**恰好 4 个字号、恰好 2 个字重**，与 Phase 11 / 12 / 13 一致。本阶段**不新增字号、不引入 500/700**。

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 14px | 400 (regular) | 1.5 |
| Label | 12px | 400 (regular) | 1.4 |
| Heading | 18px | 600 (semibold) | 1.3 |
| Display | 24px | 600 (semibold) | 1.2 |

**本阶段各表面的应用：**

| Surface | Size / Weight / LH | 依据 |
|---------|--------------------|------|
| S1 403 标题 `无权访问此页面` | 24px / 600 / 1.2，`--text-1` | **Display** —— 与 S2 的 18px 形成第一处「一眼可分」的差异 |
| S1 403 说明句 | 14px / 400 / 1.5，`--text-2` | Body（**不得用 `--text-3`**，见 Color） |
| S1 403 动作 `返回首页` | 600 14px / 1.5 | 主按钮归一化（Contract 2） |
| S2 404 标题 `资源不存在` | 18px / 600 / 1.3，`--text-1` | **Heading**（Phase 11 既有值，不改） |
| S2 404 说明句 | 14px / 400 / 1.5，`--text-2` | Body（**本阶段修正 Phase 11 的 `--text-3`**，见 Color） |
| S2 404 动作对 | 600 14px / 1.5（primary）/ 400 14px / 1.5（default） | 两档字重 |
| S3 字段标签 | 14px / 600 / 1.5（页面级覆盖）或 EP 默认 500（未覆盖页） | 见上方 `theme.css` 强制表 |
| S3 内联错误 | 12px / 400 / **1.4**，`--hhs-color-error-text` | Label，Phase 11 全局块已生效，**不重定义** |
| S3 汇总标题 | 14px / 600 / 1.4，`--hhs-color-error-text` | Body + semibold，已在 `FormErrorSummary.vue` 声明 |
| S3 汇总条目 | 14px / 400 / 1.5，`--hhs-color-error-text`，下划线 | Body |
| S4 toast 文案 | 14px / 400 / **1.5**（覆盖 EP 的 `line-height:1`） | Body，Phase 11 全局块已生效 |
| S4 toast 动作 | 14px / 600 / 1.5 | Body + semibold |

**必须覆盖的 `theme.css` 全局 `!important`（沿用既有归一化，不新增规则）：**

| theme.css 规则 | 实际值 | 影响本阶段 | 处理方式 |
|---|---|---|---|
| `.el-button--primary { font: 500 14px/1 … !important }`（:449-459） | 500 字重 / 行高 1 | S1 `返回首页`、S2 `返回列表`、S3 各提交按钮 | 页面级 `:deep()` 覆盖为 `600 14px/1.5`（Contract 2/3 已含说明；**弹窗内的按钮走 theme.css:469-482 的 `.el-dialog` 规则，已由 Phase 12 交付**） |
| `.el-form-item__label { font: 500 14px/… !important }`（:660-664） | 500 字重 | S3 全部标签 | 迁移页在 `:deep()` 覆盖为 `600 14px/1.5`；**未覆盖页保持 500**，本阶段不追讨（属于字重档位债，见 Appendix） |
| `.el-input__inner { font: 400 15px/1.5 … !important }`（:516-519） | 15px | S3 全部输入框 | 同上，迁移页覆盖为 `400 14px/1.5` |

> **明确不做的字体工作：** 本阶段不把 87 个调用点统一到 14px/600 标签档位。字号/字重归一化是**页面级**工作量（Phase 12/13 逐页做过），与「错误呈现一致性」是两个正交问题。本契约只强制**错误相关元素**的字号字重（上表已限死），其余沿用各页现状。此边界写入 Appendix 的「未收口项」。

---

## Color

所有色值来自 `theme.css` 既有 token；**对比度全部实测**（WCAG 2.1 相对亮度公式，半透明值按实际合成后计算）。本阶段**不新增任何颜色 token**。

### 60 / 30 / 10 分配

| 占比 | 角色 | Dark | Light | 本阶段占用位置 |
|------|------|------|-------|---------------|
| 60% | 主导 / 背景 | `--bg` `#0a0b0e` | `--bg` `#f5f7fa` | S1 全页画布（无卡片面）；页面之间的留白 |
| 30% | 次主导 / 表面 | `--surface-1` `#111217` | `#ffffff` | S2 内容区卡片面（`ErrorState` 默认 `background: var(--surface-1)`） |
| 10% | 强调 | `--accent-cool` `#5EEAD4` | `#0d9488` | **仅限**：S1 `返回首页` 主按钮底面、S2 `返回列表` 主按钮底面、其 hover、以及所有可获得焦点的控件在 `:focus-visible` 时的 `outline` |
| 破坏性 | `#f56c6c` | `#b91c1c` | 仅既有删除确认（本阶段不改）；**与 `--hhs-color-error-text` 同值**，见下 |

**强调色绝不用于文字。** Light 主题 `#0d9488` 在白底 **3.74:1**、在 `--bg` `#f5f7fa` 上 **3.49:1**，均不达 AA 正文 4.5:1。S1/S2 的按钮底色上写 `--bg`（Dark）或白（Light），是 EP 主按钮既有行为，不改。

### 语义色（沿用，不新增）

| Role | Dark | Light | 实测对比度 | 本阶段用途 |
|------|------|-------|-----------|-----------|
| 错误文字/描边 | `--hhs-color-error-text` `#f56c6c` | `#b91c1c` | 深：`#f56c6c` on `#111217` **6.45:1**；浅：`#b91c1c` on `#ffffff` **6.47:1** | S3 内联红字、汇总标题与条目、S4 toast 文案与动作、S5 字段错误描边 |
| 错误底纹 | `--hhs-color-error-tint` `rgba(245,108,108,.12)` | `#fef0f0` | 深：`#f56c6c` on tint **5.55:1**；浅：`#b91c1c` on tint **5.83:1** | 汇总块底、聚焦错误态 3px 光晕、toast 底（EP 自带） |
| 正文主色 | `--text-1` `#ebecef` | `#1a1c23` | 深 15.83:1 / 浅 17.01:1（on 白）；浅 on `--bg` **15.85:1** | S1/S2 标题 |
| 正文次色 | `--text-2` `#c6c9d2` | `#4a4d5c` | 深 **11.30:1** / 浅 on 白 **8.38:1**、on `--bg` **7.80:1** | **S1/S2 说明句唯一允许的颜色** |
| 辅助文字 | `--text-3` `#8d909c` | `#7c7f8e` | 深 5.88:1 / 浅 on 白 **3.97:1**、on `--bg` **3.70:1** | **浅色不达 AA 正文**，只允许用于非文字图形（S1/S2 的 `Lock`/`DocumentDelete` 图标） |

### `--text-3` 对比度陷阱（本阶段第三次确认，必须逐处核对）

Phase 11 的 `ErrorState.vue:127` 给说明句写了 `color: var(--text-3)`。Phase 12 首次发现、Phase 13 再次确认：**Light 主题下 `--text-3` 仅 3.97:1，14px 正文不达 AA**，而 Light 是项目实际首屏默认（`stores/theme.ts:38` `storedTheme || 'light'`）。

**本契约要求修改 `ErrorState.vue` 的 `.error-state__desc` 为 `color: var(--text-2)`**（Light 8.38:1 / Dark 11.30:1）。这是对 Phase 11 契约的一处**有意收紧**，不是漂移。

**图标色不受此限：** `--text-3` 用于 `forbidden`/`notFound` 的图形（非文字），阈值 ≥3:1，实测通过：

| 组合 | 合成色 | 图形对比度 |
|------|--------|-----------|
| `#7c7f8e` on 中性圆（`--bg` 合成 · Light） | `#e9ebef` | **3.33:1** |
| `#7c7f8e` on 中性圆（`--surface-1` 合成 · Light） | `#f1f2f3` | **3.55:1** |
| `#8d909c` on 中性圆（`--bg` 合成 · Dark） | `#1a1b1f` | **5.41:1** |
| `#8d909c` on 中性圆（`--surface-1` 合成 · Dark） | `#202127` | **5.05:1** |

**中性圆在 `--surface-3` 上不达标（Light 仅 2.28:1）** —— 因此 S1/S2 **永不**渲染在 `--surface-3` 底面上。S1 渲染在 `--bg`，S2 渲染在自己的 `--surface-1` 卡片里。

### S5 字段错误描边（本阶段新增渲染、不新增颜色）

| 状态 | 边框 | 光晕 | 实测 |
|------|------|------|------|
| 出错 · 未聚焦 | `1px solid var(--hhs-color-error-text)` | 无 | 浅 6.47:1 / 深 6.45:1，满足非文字控件边界 3:1（WCAG 1.4.11） |
| 出错 · 聚焦 | 同上（仍为红） | `0 0 0 3px var(--hhs-color-error-tint)` | 光晕是 tint 叠加，不改变文字对比度 |
| 正常 · 聚焦 | `1px solid var(--accent-cool)`（沿用 theme.css:512-515） | `0 0 0 3px var(--accent-cool-soft)` | 图形 ≥3:1，通过 |

**错误态高于焦点态，但焦点仍然可见：** 出错且聚焦时红边保持、光晕由 accent 软色换成错误软色。焦点可见性由「光晕的有无」承担，不是只靠颜色差异 —— 满足 WCAG 2.4.7。

**不得用 EP 的 `--el-color-danger` 直接作描边色**：`#f56c6c` 在纯白仅 **2.90:1**，作为控件边界不达 3:1。这是 Phase 11 引入 `--hhs-color-error-text` 的同一理由。

---

## Component Contract 1 — 错误分类 → 呈现映射（唯一真值表）

**这张表就是本阶段的核心交付。** 任何迁移点只需按 `err.kind` 查表即可，不需要自行判断「要不要弹」。

### 1.1 `ErrorKind` 的最终集合（`utils/appError.ts`）

```ts
export type ErrorKind =
  | 'validation'   // HTTP 400 + fieldErrors 非空        → 内联 + 汇总
  | 'business'     // HTTP 200 + 信封 code !== 200        → toast
  | 'auth'         // 401                                → logout 自有提示
  | 'forbidden'    // 【新增】403 或 *.forbidden 错误码   → 状态块
  | 'notFound'     // 【新增】404 或 *_not_found 错误码   → 状态块
  | 'network'      // 无 response 且 isAxiosError         → toast + 重试
  | 'server'       // 真实 HTTP 5xx                        → toast + 重试
  | 'cancel'       // 取消五形态                           → 零提示
  | 'unknown'      // 兜底                                 → toast
```

**`forbidden` / `notFound` 的判定规则（必须精确，因为后端有两条到达路径）：**

| 形态 | 后端来源 | 判定条件 |
|------|---------|---------|
| 真 403 | `GlobalExceptionHandler.handleAccessDenied` → `ResponseEntity.status(403)` | `status === 403` |
| 信封 403 | `BusinessException(ErrorCode.AUTH_FORBIDDEN/GOAL_FORBIDDEN)` → `Result.failure("auth.forbidden")` → `parseCodeToInt` 回退为 **500**，`errorCode = 'auth.forbidden'` | `typeof data?.code === 'number' && /(^|\.)forbidden$/.test(data?.errorCode ?? '')` |
| 真 404 | `handleNoResourceFoundException` → `Result.failure(404, …)`（HTTP 200 信封，`code = 404`） | `data?.code === 404` 或 `status === 404` |
| 信封 404 | `BusinessException(HEALTH_PROFILE_NOT_FOUND / GOAL_NOT_FOUND / …)` | `/(^|_|\.)not[._]?found$/.test(data?.errorCode ?? '')` |

**插入位置：紧接现有第 3 步（401）之后、第 4 步（断网）之前。** 理由：两者都以 `err.response` 存在为前提，必须早于断网分支；401 必须先判（会话过期优先于授权失败，否则 `auth.forbidden` 与 401 会互相抢）。`appError.ts:60-74` 的「分类顺序即契约，不可调换」注释**必须同步更新**，把新步骤编号写进去 —— 否则下一个人会把它「修正」回去。

> **不要把 `notFound` 的判定接到第 6 步（信封业务码）之后。** 一旦落后于业务分支，`health.profile_not_found` 已被判成 `business`，`prevention/Profile.vue` 的「创建模式」分支与 `stores/profileCompleteness.ts:67` 的 `NOT_FOUND_CODES` 会同时失效。这是回归风险最高的一处，`appError.spec.ts` 必须新增反回归锚点。

### 1.2 呈现通道表

| `kind` | 通道 | toast 条数 | toast 形态 | 调用点职责 |
|--------|------|-----------|-----------|-----------|
| `validation` | **内联 + 汇总** | **0** | — | `useFormServerErrors.apply()`；`focusSummary()`；**不得**改 `presentValidation`（唯一例外见下） |
| `business` | toast | 1 | `error` / `duration 4000` / `showClose` / 可分组 | `showError(err, key)` |
| `business` + 恢复动作 | toast | 1 | `error` / `duration 0` / VNode / `打开编辑` | `showError(err, key, { action })` |
| `network` | toast | 1 | `error` / `duration 0` / VNode / `重试` | `showError(err, key)`（`retryRequest` 自动挂上） |
| `server` | toast | 1 | 同 `network` | 同上 |
| `forbidden` | **状态块** | **0** | — | 页面渲染 S1 页级块；动作型调用点传 `{ presentStates: true }` 换取 1 条 toast |
| `notFound` | **状态块** | **0** | — | 页面渲染 S2 内容区块；动作型调用点传 `{ presentStates: true }` |
| `auth` | logout 自有 | **0** | — | 拦截器触发 `logout({ reason: 'expired' })`，提示由 `authStore` 承担 |
| `cancel` | 无 | **0** | — | `showError` 早退；调用点不做任何事 |
| `unknown` | toast | 1 | 同 `business` | `showError(err, key)` |
| `{ silent: true }` 请求 | 无 | **0** | — | 拦截器不呈现；调用点自行降级（`request.ts` 负责，`showError` 不参与） |

**`presentStates` 的语义（对既有 `presentValidation` 的同构扩展）：**

```ts
export interface ShowErrorOptions {
  retry?: () => void | Promise<void>
  action?: { label?: string; onClick: () => void }
  silent?: boolean
  presentValidation?: boolean   // 已有：让未接入内联的表单仍能看见 400
  presentStates?: boolean       // 新增：让「动作型」403/404 仍能看见一条提示
}
```

- **默认（不传）= 零 toast。** 因为 403/404 是**状态**，状态块已经承担呈现；再弹一条就是重复。
- **`presentStates: true`** 只允许在「用户主动发起的一次动作」上使用（删除、更新、提交、导出），**并且该调用点没有对应状态块**。用法与 `Thresholds.vue:260` 的 `presentValidation: true` 完全同构，理由也相同：宁可让调用点显式声明，也不让后端错误静默消失。
- 迁移台账必须为每个 `presentStates: true` 记录一句理由（Contract 6 的台账字段）。

### 1.3 分层判定顺序（执行者按此顺序走）

```
收到失败
 ├─ 是取消？            → 什么都不做（不要写 if (e !== 'cancel')）
 ├─ 是 silent 请求？     → 什么都不做
 ├─ kind === validation？→ apply() + focusSummary()；不改 inputs；不 toast
 ├─ kind === forbidden / notFound？
 │    ├─ 本页有对应状态块 → 渲染状态块（zero toast）
 │    └─ 本页没有（动作型）→ showError(err, key, { presentStates: true })
 ├─ kind === auth？      → 什么都不做（logout 已提示）
 └─ 其余                → showError(err, key[, { retry | action }])
```

**唯一允许出现「一次失败两条提示」的场合：** 不存在。若执行者发现两条，说明调用点既渲染了状态块又调了 `showError` —— 按缺陷处理。

---

## Component Contract 2 — 403 全页解释页（S1）

**语义：整个页面内容不可用。** 页面壳（侧边栏、页头、面包屑）保持可用 —— 用户必须仍然能离开这一页，否则就是死路。

### 2.1 载体：扩展 `ErrorState`，不新建组件

D-03 允许「扩展 variant」或「轻量包装」两种形式。本契约选定**扩展 `ErrorState`**，理由是可测试面单一（一份 `ErrorState.spec.ts` 覆盖两档几何），且不需要新的组件文件。

**新增 2 个可选 prop（默认值等于 Phase 11 行为，15 条既有断言全部保持通过）：**

| Prop | Type | Default | 作用 |
|------|------|---------|------|
| `scope` | `'content' \| 'page'` | `'content'` | `'page'` → 无卡片面、无虚线边框、页级高度、标题升级为 Display |
| `actions` | `Array<{ label: string; onClick: () => void \| Promise<void>; variant?: 'primary' \| 'default' }>` | `undefined` | 有序动作列表。**非空时不渲染 `retry` 按钮**（二者互斥；`actions` 里需要重试就显式写一条）。`label` 同时是可见文本与可访问名，**必须是动词 + 宾语** |

**既有 `retry` / `retryLabel` / `retrying` / `variant` / `title` / `description` 全部不动。** `ErrorState.spec.ts` 的 15 条断言不新增、不删除、不修改。

### 2.2 结构

```html
<ErrorState
  scope="page"
  variant="forbidden"
  title="无权访问此页面"
  :description="deniedDescription"
  :actions="[{ label: '返回首页', onClick: goHome, variant: 'primary' }]"
/>
```

```
┌──────────────────────────────────────────────┐
│                                              │
│              [ 图标圆 64px · Lock ]           │   ← 无底色卡片、无虚线边框
│                    (8px)                     │
│       无权访问此页面   24px/600/1.2           │   ← Display，唯一 24px 元素
│                    (8px)                     │
│    当前账号没有访问该页面的权限，请返回        │   ← 14px/400/1.5 --text-2
│    首页，或联系管理员申请开通。                │
│                    (24px)                    │
│              [ 返回首页 ]                     │   ← primary，唯一动作
│                                              │
└──────────────────────────────────────────────┘
   页级高度：min-height: calc(100vh - 104px)
```

### 2.3 与 S2（404 内容区）的五重差异 —— 必须同时成立

| 通道 | S1 403 页级 | S2 404 内容区 |
|------|------------|--------------|
| 1. 表面 | **无容器面**（透明，坐在 `--bg` 上） | `--surface-1` 卡片面 + `1px dashed var(--border)` + `radius-lg` |
| 2. 标题字号 | **24px / 600 / 1.2**（Display） | 18px / 600 / 1.3（Heading） |
| 3. 高度 | `min-height: calc(100vh - 104px)`，垂直居中 | `min-height: 320px`，max-width 480px，居中于内容区 |
| 4. 图标 | `Lock`（锁 = 权限） | `DocumentDelete`（文档缺失 = 资源不存在） |
| 5. 动作数 | **1**（`返回首页`） | **2**（`返回列表` + `重新加载该记录`） |

**五条通道互相独立、都不依赖颜色** —— 满足 WCAG 1.4.1（不以颜色为唯一信息载体），也保证色觉异常或低视力用户在 0.5 秒内分得清「没权限」与「东西没了」。

### 2.4 必须声明的 CSS（`ErrorState.vue` `<style scoped>` 追加）

```css
/* 页级：不画卡片面、不画虚线边，坐在 --bg 上；垂直填满内容区 */
.error-state--page {
  min-height: calc(100vh - 104px);   /* 64 header + 40 main padding，见 Spacing */
  max-width: 720px;                  /* 页级说明句一行可达 40 字，480px 会折 3 行 */
  padding: 48px 24px;
  background: transparent;
  border: 0;
  border-radius: 0;
}

/* 说明句：Phase 11 用 --text-3（Light 3.97:1 不达 AA），本阶段收紧为 --text-2 */
.error-state__desc {
  color: var(--text-2);
}

/* 内容区动作对（S2）：水平排列，窄屏纵向拉伸 */
.error-state__actions {
  margin-top: 16px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

/* theme.css 用 !important 把主按钮锁成 500 14px/1（行高 1 会裁中文字形），
   次按钮是 EP 的 500 字重；两处都归一到 400/600 两档。 */
.error-state :deep(.el-button--primary) {
  font: 600 14px/1.5 var(--font-ui) !important;
}
.error-state :deep(.el-button:not(.el-button--primary)) {
  font-weight: 400 !important;
  line-height: 1.5 !important;
}
/* EP 给相邻按钮加 margin-left: 12px，与 gap 叠加会变 20px */
.error-state :deep(.el-button + .el-button) {
  margin-left: 0;
}

@media (max-width: 639px) {
  .error-state__actions { flex-direction: column; align-items: stretch; }
  .error-state__actions :deep(.el-button) { width: 100%; }
  .error-state--page { min-height: 0; padding: 48px 16px; }
}
```

### 2.5 触发条件（前端无角色信息，必须以服务端为准）

**已实测的关键事实：`router/index.ts:113` 声明了 `meta.requiresAdmin`，但 `router.beforeEach` 只检查 `requiresAuth` —— `requiresAdmin` 从未被读取。** 同时 `UserVO` 没有 `role` 字段（`hhs-backend/…/UserVO.java` 只有 id/username/nickname/avatar/email/phone/createdAt/updatedAt），前端**无法**在路由层判断角色。

因此：

| 场景 | 表现 |
|------|------|
| 页面首个主查询返回 `forbidden`（`/settings/device-platform` 的所有 `@PreAuthorize("hasRole('ADMIN')")` 接口） | 渲染 S1 页级块，替换整块页面内容；**零 toast**；页头标题保持（用户知道自己在哪） |
| 页面已有部分数据、某一次动作返回 `forbidden` | **不**渲染 S1（不能因为一次动作把整页打掉）；走 `showError(err, key, { presentStates: true })` 的单条常驻 toast |
| 路由不存在 | 见 Contract 3 §3.6（页级 `notFound`，同一 `scope="page"` 几何） |

**禁止：** 在 `router.beforeEach` 里加角色门禁或白屏跳转。前端没有角色数据，加门禁只会制造一个与后端不一致的第二真值源。

### 2.6 文案

- 标题固定 `无权访问此页面`（7 字 ≤ 16）。
- 说明句：**后端消息优先**（Phase 11 D-05）。当后端消息点名了被拒动作（如 `无权删除他人记录`、`无权修改其他用户的阈值数据`），直接用它；否则用兜底句：

  `当前账号没有访问该页面的权限，请返回首页，或联系管理员申请开通。`（32 字 ≤ 40）

- **不得**向用户编造权限名。前端不知道用户缺哪个角色，写死「缺少 ROLE_ADMIN」会在非管理员页面上说错话。这是 D-03「说明当前账号缺少哪个权限」的**可实现边界**：能给的只有「访问这类内容需要额外授权」。

---

## Component Contract 3 — 404 内容区解释块（S2）

**语义：页面壳可用，仅该资源缺失。** 用户的其它数据、筛选器、导航全部照常工作 —— 只有这一块内容是「不存在」。

### 3.1 结构与动作对

```html
<ErrorState
  variant="notFound"
  title="资源不存在"
  description="该资源可能已被删除，请返回列表查看最新数据。"
  :actions="[
    { label: '返回列表', onClick: goList, variant: 'primary' },
    { label: '重新加载该记录', onClick: reload, variant: 'default' }
  ]"
/>
```

```
┌──────────────────────────────────────────┐  ← --surface-1 卡片面
│        [ 图标圆 64px · DocumentDelete ]   │     + 1px dashed --border
│                  (8px)                    │     + radius-lg
│           资源不存在  18px/600/1.3        │
│                  (8px)                    │
│  该资源可能已被删除，请返回列表查看最新数据。│
│                  (24px)                   │
│      [ 返回列表 ]  [ 重新加载该记录 ]      │
└──────────────────────────────────────────┘
     min-height 320px · max-width 480px · padding 48px 24px
```

### 3.2 动作主次（对 Phase 11「重试是唯一主 CTA」的有意分级）

Phase 11 Contract 1 把 `重试` 定为唯一 primary，因为当时只有「加载失败」一种语义。`notFound` 的语义不同：**重试大概率仍会失败**（资源真的没了），继续留在原地是错的方向。

| 动作 | 变体 | 理由 |
|------|------|------|
| `返回列表` | **primary**（accent 底） | 唯一有产出的出口；用户在这里能继续做事 |
| `重新加载该记录` | default（透明底 + 边框） | 供「刚被别处创建/网络假失败」的低概率场景；允许尝试，但不诱导 |

**可访问名 = 可见标签**（`actions[].label` 双重身份），因此两者都写成动词 + 宾语，不用裸 `重试`。这是本契约对 Phase 11 的一个**局部覆盖**，只作用于 `actions` 通道；toast 内的 `重试` 与 `aria-label="重试：{message}"` 契约不变。

### 3.3 可见标签的长度约束

`返回列表`（4 字）+ `重新加载该记录`（8 字）+ 8px gap + 两个按钮内边距 ≈ 260px，在 480px 块内单行放得下。**不得**把第二个写成「重新加载该记录并刷新周边所有关联数据」这类长句 —— 超过 8 字就要重新设计动作，而不是加长标签。

### 3.4 与 `ElEmpty` 的强制分离（Phase 11 纪律，本阶段重申）

| 状态 | 载体 | 文案示例 |
|------|------|---------|
| 加载成功但 0 条 | `ElEmpty` / 教学型空态（如 `Thresholds.vue` 的 `.threshold-empty`） | `尚未设置个性化阈值` |
| 加载失败 | `ErrorState variant="load\|server\|network"` | `无法加载阈值列表` |
| 资源不存在 | `ErrorState variant="notFound"` | `资源不存在` |
| 无权限 | `ErrorState variant="forbidden"` | `无权访问此页面` |

四者**永不同时出现**，且「0 条」与「不存在」必须看起来完全不同 —— 前者是正常的、可继续操作的状态，后者是异常。

### 3.5 零 toast

404 状态块渲染时**不得**再有任何 toast。执行者若在同一个 `catch` 里既 `render state` 又 `showError`，即为重复提示缺陷。判定方法：`showError` 的返回值 `err` 在状态块路径上只用于取 `err.message`（塞进 `description`），**不**再传给 `showError`。

### 3.6 路由级 404（同一个页级几何，不同 variant）

当前 `router/index.ts` **没有 catch-all**，访问不存在的 URL 会渲染空白的 `MainLayout` 内容区 —— 一个没有任何解释的空白页，这是全站最裸的失败面。

若 planner 在本阶段加入 catch-all（`{ path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFoundView }`），它**必须**复用 Contract 2 的页级几何：

```html
<ErrorState
  scope="page"
  variant="notFound"
  title="页面不存在"
  description="该页面可能已下线，或链接地址有误。"
  :actions="[{ label: '返回首页', onClick: goHome, variant: 'primary' }]"
/>
```

差异只有三处：`variant="notFound"`（`DocumentDelete` 图标）、标题 `页面不存在`（5 字）、动作回到单个 `返回首页`（此时没有「列表」可回）。**不得**为此新建第三个页面级组件 —— 页级几何只有一份。

> **边界声明：** 新增 catch-all 路由属于 routing 变更，需要 planner 在 PLAN 中显式列出。若 planner 判定超出本阶段范围，本节即为「未启用时也已被定义」的规格，不构成阻塞。

---

## Component Contract 4 — 表单内联错误一致性规格（S3）

**这一节的存在理由是 ERR-03 的规模化风险：87 个调用点、15 个含 `<el-form>` 的页面。** 如果每个页面各自决定「错误落在哪、长什么样」，本阶段就会产出 15 种样式。规格只有一个版本。

### 4.1 每个参与服务端校验的 `el-form-item` 必须同时具备五件事

```html
<el-form-item
  label="指标数值"
  prop="value"                          <!-- ① 必须：fieldErrors 的键 -->
  data-field="value"                    <!-- ② 必须：focusField() 的 DOM 锚点 -->
  :error="fieldErrors['value']"          <!-- ③ 必须：EP 的公开内联错误入口 -->
>
  <el-input-number
    v-model="form.value"
    @update:model-value="clearField('value')"   <!-- ④ 必须：改一下就清掉该行 -->
  />
</el-form-item>
```

⑤ 表单提交前必须 `reset()`（避免陈旧汇总带进下一次尝试）。四条缺一即视为未迁移。

**为什么必须用 `:error` 而不是自绘红字：** EP 的 `form-item` 源码里有 `watch(() => props.error, val => { validateMessage = val || ''; setValidationState(val ? 'error' : '') }, { immediate: true })`（`element-plus@2.14.0` `form-item.vue_vue_type_script_setup_true_lang.mjs`）—— 传 `:error` 会**同时**把 `validateState` 置为 `error`，从而拿到 `.el-form-item.is-error` 类。这个类是我们唯一能挂错误描边的锚点（见 4.2）。自绘红字拿不到它，描边就不会出现。

### 4.2 唯一的视觉规格（由 theme.css 全局交付，页面**禁止**再写任何错误样式）

**追加到 `src/assets/styles/theme.css` 的错误呈现块（Phase 11 块之后、同一风格）：**

```css
/* ---------- Phase 14: 字段级错误描边 ----------
   前提事实（已在 element-plus@2.14.0 的 theme-chalk 与源码中核对）：

   1) EP 自带的错误描边是
        .el-form-item.is-error .el-form-item__content .el-input__wrapper { box-shadow: 0 0 0 1px var(--el-color-danger) inset }
      （el-form-item.css，**无 !important**），同时覆盖 input / textarea / select 三类。
   2) theme.css:504-506 给 .el-input__wrapper 写了
        border: 1px solid var(--border) !important;  box-shadow: none !important;
      这两条 !important **压掉了** 上面那条错误描边；又因为 controls 现在有真实 border
      而不是 box-shadow 描边，错误态必须改走 border-color。
   3) theme.css:672-677 对 .el-select__wrapper 做了同样的事（真实 border + box-shadow: none）。
   4) .el-textarea__inner **没有** 被 theme.css 覆盖：EP 用
        border: none;  box-shadow: 0 0 0 1px var(--el-input-border-color) inset;
      → theme.css 的 border-color 方案对 textarea 无效，必须继续用 inset box-shadow。

   因此两类控件走两条不同声明。混用任何一条都会导致该类控件「出错时外观无变化」。 */

/* ① input / select：theme.css 已给出真实 border，改 border-color 即可 */
body .el-form-item.is-error .el-form-item__content .el-input__wrapper,
body .el-form-item.is-error .el-form-item__content .el-select__wrapper {
  border-color: var(--hhs-color-error-text) !important;
  box-shadow: none !important;
}

/* ② textarea：EP 的描边是 inset box-shadow（无 border），只能改 box-shadow。
      颜色从 EP 的 --el-color-danger（#f56c6c，纯白 2.90:1）换成 AA 通过的 token。 */
body .el-form-item.is-error .el-form-item__content .el-textarea__inner {
  box-shadow: 0 0 0 1px var(--hhs-color-error-text) inset !important;
}

/* ③ 出错且聚焦：错误描边保持，光晕换成错误 tint。
      WCAG 2.4.7 由「光晕的有无」满足，不依赖红色与青色的色相差异。 */
body .el-form-item.is-error .el-form-item__content .el-input__wrapper.is-focus,
body .el-form-item.is-error .el-form-item__content .el-select__wrapper.is-focused {
  border-color: var(--hhs-color-error-text) !important;
  box-shadow: 0 0 0 3px var(--hhs-color-error-tint) !important;
}
body .el-form-item.is-error .el-form-item__content .el-textarea__inner:focus {
  box-shadow:
    0 0 0 1px var(--hhs-color-error-text) inset,
    0 0 0 3px var(--hhs-color-error-tint) !important;
}
```

**`.el-input-number` 不需要单独规则：** 它内部渲染的就是 `.el-input__wrapper`，规则 ① 已覆盖。写入 `.el-input-number .el-input__wrapper` 这类冗余选择器会让后人误以为它是独立控件。

**特异性核对：** 规则 ① 为 `0,4,1`（`body` + 4 个类）＞ theme.css `.el-input__wrapper` 的 `0,1,0`。两者都带 `!important` 时由特异性决定，规则 ① 胜。规则 ② 为 `0,4,1` ＞ EP 的 `0,4,1`，同特异性下本文件后加载（`main.ts` 在 `element-plus/dist/index.css` 之后引 `theme.css`），后写者胜。

**执行者的三条自检（缺一即视为描边未交付）：**
1. 出错字段的 `.el-input__wrapper` 计算样式 `border-color` 解析为 `rgb(185,28,28)`（Light）/ `rgb(245,108,108)`（Dark）。
2. 出错字段的 `.el-textarea__inner` 计算样式 `box-shadow` 含 `inset` 且颜色为同一 token。
3. 出错字段聚焦后 `box-shadow` 含 `3px` 的 tint 光晕，且 `border-color` 仍是错误色。

**除上述全局块外，任何页面不得出现 `el-form-item__error`、`is-error`、`--el-color-danger` 的局部覆盖。** 迁移台账的 Review 列必须能回答「这个页面有没有写错误样式」，答案必须是「没有」。

**内联错误的必要不变量（Phase 11 已交付，本阶段只核对，不重写）：**

| 属性 | 值 | 交付位置 |
|------|-----|---------|
| 字号 | 12px（EP 默认，保留） | `theme-chalk/el-form-item.css` |
| 行高 | **1.4**（覆盖 EP 的 `line-height:1`，中文会裁下缘） | `theme.css:1238-1242` |
| 上内边距 | **4px**（归一化 EP 的 2px） | 同上 |
| 颜色 | `var(--hhs-color-error-text)`（覆盖 EP 的 `--el-color-danger`） | 同上 |
| 定位 | `position:absolute; top:100%; left:0` —— **保留**，避免布局跳动 | EP 默认 |
| 图标 / 底色 / 边框 / 胶囊 | **无**（GOV.UK：文字单独承担内联；图标只属于汇总块） | — |
| 同字段多条 | 后端已用 `；` 合并（`appError.ts:96`），EP 每 item 只渲染一个字符串 | 已交付 |

### 4.3 汇总块的位置与内容（唯一合法位置）

- **`<el-form>` 的第一个子节点**，位于所有 `el-form-item` 之上，位于 `el-dialog__body` 或全页表单体内。**不得**放在 dialog 标题/页头之上，**不得**放在 footer。
- 弹窗与全页表单**位置完全相同**（Phase 11 D-04：一套机制）。
- 排序：`useFormServerErrors({ fieldOrder })`，`fieldOrder` 必须与页面字段的视觉顺序一致（GOV.UK：汇总顺序必须匹配页面）。**未传 `fieldOrder` 时保持后端键顺序**，这在迁移页上视为未完成。
- 客户端校验失败也要汇总：`el-form.validate()` 的 `invalidFields` 经 `setErrors(collectInvalidFieldMessages(invalidFields))` 投影进同一份 `fieldErrors`（`prevention/Profile.vue:432-437` 是参考实现）。**只靠 EP 的逐字段红字不算完成**（用户看不到「一共错了几项」）。

### 4.4 焦点规格（GOV.UK：焦点去汇总，描边去字段）

| 时机 | 焦点落点 | 理由 |
|------|---------|------|
| 客户端校验失败 | 汇总块 wrapper（`summaryRef.focusSummary()`），`nextTick()` 之后一次 | 读屏用户先听到「请修正以下 N 项」 |
| 服务端 `validation` 失败 | 同上 | 与 Phase 11 一致；**不得**改为聚焦第一个字段 |
| 点击汇总条目 | 对应控件（`focusField(field)` → `[data-field]` 内首个 `input/textarea/.el-select__wrapper/.el-rate`） | 已有实现 |
| 打字过程中 | **不动** | 每敲一个字跳焦会让表单不可用 |

**「首个无效字段」的视觉强调由 `is-error` 描边承担，不由焦点承担。** 这样既满足 ERR-03 的「首个无效字段有可见的出错指示」，又不破坏 Phase 11 的「焦点只移动一次到汇总」纪律。执行者**不得**额外写一个「自动滚动到第一个错误字段」的动画（`scrollIntoView` 只在用户点汇总条目时发生）。

### 4.5 中文标签映射规则

| 规则 | 内容 |
|------|------|
| 1 | 后端只下发**字段路径**（如 `warningHigh`），前端拥有中文标签（Phase 11 D-15）。 |
| 2 | 解析顺序：**调用点 `labels` 覆盖** → `FIELD_LABELS` → 空串。空串时汇总条目与内联**只渲染后端消息**，绝不把 `warningHigh` 露给用户。 |
| 3 | 未知字段**不得**自动「美化」（`warningHigh` → `Warning High` 之类），不得回退到字段名本身。 |
| 4 | 同一字段名在不同页面有不同中文（`title` = 目标标题 / 提醒标题；`value` = 指标数值 / 当前值 / 进度值）时，**必须**用 `labels` 覆盖，不得改全局表。 |
| 5 | 标签表新增由本契约锁定（Copywriting Contract 的表），执行者**不得**自行加键 —— 每加一个键都要同步 `errorMessages.spec.ts` 的计数断言。 |

**`useFormServerErrors` 的签名扩展（向后兼容）：**

```ts
export interface UseFormServerErrorsOptions {
  fieldOrder?: string[]
  /** 覆盖全局 FIELD_LABELS（同一字段名在不同页面语义不同时使用） */
  labels?: Record<string, string>
}
```

`fieldLabel(field)` → `options.labels?.[field] ?? fieldLabel(field)`。既有 `useFormServerErrors.spec.ts` 不传 `labels`，全部通过。

### 4.6 「必须接入」的判定清单（给台账用）

一个表单页面/弹窗被判定为「已迁移」，以下 7 条必须同时为真：

1. 有 `<el-form ref>`，且 `submit` 走 `formRef.validate()` 或直接调 API（二者都允许）。
2. 每个提交字段的 `el-form-item` 有 `prop` + `data-field` + `:error`。
3. 每个 `:error` 绑定的字段，其控件有 `@update:model-value` / `@input` / `@change` → `clearField(prop)`。
4. 表单第一子节点是 `<FormErrorSummary :summary-items @focus-field>`。
5. 提交前 `reset()`；`validation` 失败后 `apply(err)` + `focusSummary()`。
6. 该文件**没有任何** `ElMessage.error`，也没有自定义错误样式。
7. 该文件没有任何 `if (e !== 'cancel')` 式手写判断（Contract 7）。

**7 条全真 = 已迁移。缺任意一条 = 未迁移**，即便它已经不再弹 toast（例如把 `ElMessage.error` 换成了 `console.error`，仍属未迁移）。

---

## Component Contract 5 — Toast 类型区分与零 toast 纪律（S4）

### 5.1 唯一出口

`showError()` 是**唯一**允许调用 `ElMessage` 的模块。`utils/showError.ts` 用的是对象式 `ElMessage({...})`，**从不**调用 `ElMessage.error()`。因此迁移验收的可执行判据是：

```bash
# 必须输出 0
grep -rn "ElMessage\.error" src --include=*.vue --include=*.ts \
  | grep -v "__tests__"
```

（`src/utils/showError.ts` 本身不匹配这条 grep，无需排除；把它排除只是防御性写法。）

> **`ElMessage.success` / `.warning` / `.info` 不在本禁令内。** `stores/auth.ts` 的 `登录成功` / `已退出登录` / `登录已过期` 是既有且唯一合法的 `ElMessage.*` 用户可见调用（401 的唯一提示由它承担，`showError` 对 `auth` 刻意静默）。**不得**为了把 grep 清零而改写它们。

### 5.2 变体表（每种变体的全部视觉属性）

| 变体 | 触发 `kind` | `type` | `duration` | `showClose` | 动作 | EP 图标 | 常驻？ |
|------|------------|--------|-----------|------------|------|---------|-------|
| 网络（可重试） | `network` | `error` | **0** | `true` | `重试` | `CircleCloseFilled` | 是 |
| 服务（可重试） | `server` | `error` | **0** | `true` | `重试` | `CircleCloseFilled` | 是 |
| 业务（无出口） | `business` / `unknown` | `error` | `4000` | `true` | 无 | `CircleCloseFilled` | 否 |
| 业务（有恢复） | `business` + `resource.exists` | `error` | **0** | `true` | `打开编辑` | `CircleCloseFilled` | 是 |
| 权限（动作型） | `forbidden` + `presentStates` | `error` | **0** | `true` | **无** | `CircleCloseFilled` | 是 |
| 资源已消失（动作型） | `notFound` + `presentStates` | `error` | `4000` | `true` | 无 | `CircleCloseFilled` | 否 |

**图标只有一种。** EP 的 `error` 类型自动渲染 `CircleCloseFilled`（`element-plus/es/utils/vue/icon.mjs` 的 `TypeComponentsMap.error`），颜色取 `--el-message-text-color`（已被 `theme.css:1226-1228` 改成 `--hhs-color-error-text`）。**不得**为不同变体手工传 `icon` —— 那就是 29 个页面 6 种图标的图标大杂烩。

### 5.3 变体靠什么区分（ERR-07 的可实现答案）

ERR-07 要求「网络错误、校验错误、权限错误在呈现方式与文案上可区分」。**区分来自三个渠道，颜色不是其中之一，图标也不是：**

| 维度 | 校验错误 | 网络错误 | 权限错误 |
|------|---------|---------|---------|
| 通道 | **内联 + 汇总**（无 toast） | **toast** | **状态块**（默认）/ 常驻 toast（动作型） |
| 动作 | 无（点汇总条目跳字段） | `重试`（显式，执行一次） | 无（重试不会变权限） |
| 退出方式 | 改字段即逐条消失 | 点 `重试` 或 `×` | 点 `返回首页` 或 `×` |
| 生命周期 | 与表单同寿命 | 常驻至用户处理 | 常驻至用户离开/关闭 |
| 文案 | `{字段标签}：{后端消息}` | `{什么失败了}，{下一步}` | `{被拒的动作}`（后端原话） |

**权限 toast 不带 `重试`** 是刻意的：`showError` 的 `hasRetry` 只在 `err.retryable === true` 时成立，而 `forbidden` 的 `retryable` 是 `false`。即使调用点传了 `retry` 回调，也不会渲染按钮（现有实现第 162 行已保证）。**不得**为权限错误放宽这条。

### 5.4 去重与上限（沿用 Phase 11，不改）

| 项 | 值 |
|---|---|
| 字符串 toast | `grouping: true`（EP 合并相同内容并显示 `repeatNum` 角标） |
| VNode toast | `grouping: false` + 模块级 2000ms 闩锁（键 = `message|retry-or-action`） |
| 最大同时可见 | 3；第 4 条挤掉最旧的**非 retry** 消息；全是 retry 类时宁可短暂超限 |
| 401 闩锁 | 模块级布尔；并发 401 只产生一条 `logout` 提示 |

**「断网 60 秒内轮询不产生重复 toast」（成功标准 3）由两层共同保证：**
1. 静默轮询（`App.vue:18,23`）走 `{ silent: true }` → 拦截器根本不呈现 → 60 秒内 0 条。
2. 即使某处非静默轮询漏网，`showError` 的 2000ms VNode 闩锁 + `grouping` 会把重复压成一条（带 `repeatNum`）。

**执行者禁止**为轮询新增 `if (Date.now() - last > 60000)` 之类的手写节流 —— 那是第二个真值源，且会与 `showError` 的去重叠加成「两套去重都以为对方管」。

### 5.5 文案长度与时序（沿用 Phase 11）

| 项 | 规则 |
|---|---|
| 字典回退文案 | ≤ 30 中文（`errorMessages.spec.ts` + `showError.spec.ts` 双向断言） |
| 后端下发文案 | **不截断**，完整换行（`showError.ts:134-141` 已实现；`showError.spec.ts` 有 60 字用例） |
| 计时器 | 只在 **hover** 暂停，键盘聚焦**不**暂停（EP 源码无 `focusin`）。因此带指令性内容的 toast 必须 `duration: 0` —— 上表已满足 |

---

## Component Contract 6 — 已迁移表面的判定、台账与可执行验证

**成功标准 2 要求「全站迁移台账完成，统一 helper 之外的裸 `ElMessage.error` 残留归零」。** 本契约把「完成」定义成可机器核对的东西。

### 6.1 台账载体与字段

载体：`.planning/phases/14-error-rollout/14-MIGRATION-LEDGER.md`（Markdown 表，便于 diff 与 review）。每行一个调用点：

| 字段 | 内容 | 约束 |
|------|------|------|
| `file:line` | 迁移前裸调用的精确位置 | 必须能在迁移前的 commit 上定位 |
| `原文案` | 迁移前的字面量（如 `删除失败`） | 逐字复制，不改写 |
| `kind 覆盖` | 该调用点可能收到的 `ErrorKind` 集合 | 至少一个；`unknown` 单独出现视为需要复核 |
| `迁移后形态` | `showError` / `presentStates-toast` / `状态块` / `静默` / `保持` | 五选一 |
| `fallback key` | 传入 `showError` 的字典键 | 必须是 `ERROR_MESSAGES` 已有键 |
| `理由` | 为什么这样迁移 | `静默` 与 `保持` **必须**有理由；其余可空 |
| `spec 断言` | 覆盖它的测试文件与用例名 | 无测试的空行视为未完成 |

### 6.2 「静默」与「保持」的判定（D-01 的验收口径）

| 形态 | 何时合法 | 反例（不得标为静默） |
|------|---------|---------------------|
| `静默` | 已有 `{ silent: true }` 的 16 处；后台预加载；未读数轮询；心跳 | 一个用户点了「保存」的请求失败后不提示 |
| `保持` | store 内 `ElMessage.error` 改为抛错/返回，由调用方 `showError`；或 `ElMessage.success` 不动 | 把 `ElMessage.error` 换成 `console.error` 就算迁移 |
| `状态块` | 页面/内容区的加载失败（`ErrorState`）、403 页级、404 内容区 | 表单提交失败用状态块（会用 toast/内联） |

### 6.3 四条可执行验证（全部必须输出预期值）

```bash
# ① 裸 toast 残留归零（成功标准 2）
grep -rn "ElMessage\.error" src --include=*.vue --include=*.ts | grep -v "__tests__" | wc -l
# 期望：0

# ② store 内不得弹 toast（D-02）
grep -rn "ElMessage" src/stores --include=*.ts
# 期望：只有 stores/auth.ts 的 success/info/warning（401 的唯一提示）

# ③ 手写 cancel 判断归零（ERR-08）
grep -rn "!== 'cancel'\|=== 'cancel'\|!== \"cancel\"" src --include=*.vue --include=*.ts | grep -v "utils/appError.ts"
# 期望：0

# ④ 错误样式的局部覆盖归零（Contract 4）
grep -rn "el-form-item__error\|is-error\|--el-color-danger" src --include=*.vue | grep -v "__tests__"
# 期望：0（全部集中在 theme.css）
```

### 6.4 「恰好一条提示」的观测方法（D-06 实跑清单的量化口径）

| 场景 | 观测 | 期望 |
|------|------|------|
| 停后端 → 点一次保存 | `document.querySelectorAll('.el-message').length`（等待 300ms 后读） | **1** |
| 停后端 → 进入列表页 | 内容区 `.error-state` 存在，`.el-message` 数量 | `.error-state` 1 个 / `.el-message` **0** |
| 断网 60s（轮询活跃） | 每 10s 采样 `.el-message` 数量 | 全程 **0**；恢复网络后仍 **0** |
| 400 字段校验 | `.form-error-summary` 1 个 + `.el-form-item__error` = N 个 + `.el-message` | **0** |
| 403（管理员页） | `.error-state--page` 存在 + `.el-message` | **1 个 / 0** |
| 404（详情页不存在） | `.error-state`（有卡片面）+ `.el-message` | **1 个 / 0** |
| 取消确认框 | 任意 `.el-message` | **0** |
| 触发 `AbortError`（切换筛选/离开页面） | 任意 `.el-message` | **0** |

**采样口径需写入 UAT：** `.el-message` 有 300ms 入场过渡，点击后立刻查询会读到 0。判定时统一等待 300ms。这一条必须写进实跑脚本，否则会误判「零提示」。

---

## Component Contract 7 — 非破坏性（ERR-05）与取消语义（ERR-08）

### 7.1 输入保留（ERR-05）

| 规则 | 说明 |
|------|------|
| 失败后**不得**调用 `formRef.resetFields()` / `resetField()` / 手写 `Object.assign(form, {})` | 这会把用户输入清空，是本阶段最严重的行为回归 |
| 失败后**不得**关闭对话框 | `destroy-on-close` 只在成功关闭时生效；失败时 dialog 保持打开 |
| 失败后**不得**把 `v-model` 绑定的草稿对象重新赋值 | 只允许读，不允许写 |
| 服务端 `fieldErrors` **只**写进 `fieldErrors` ref（`apply()`），**永不**回写到表单模型 | `apply()` 已保证；`setErrors()` 也是 |
| `clearField(prop)` 只清**错误文本**，不清**输入值** | 命名容易误解，执行者必须核对实现 |

**可测试判据：** 一次 `apply(err)` 之后，`form` 的每个字段值必须 `toEqual` 调用前的快照。`prevention/Profile.vue`、`ThresholdWizard.vue`、`Risk.vue` 三个参考实现都遵循此点。

### 7.2 取消语义（ERR-08）

**取消不是失败。** 现有 `appError.ts` 已识别五种形态（`CANCEL_SENTINELS = ['cancel','close']`、`CanceledError`、`AbortError`、`ERR_CANCELED`），`request.ts:65` 与 `showError.ts:129` 都已短路。

**本阶段要清除的是绕过这两个短路的手写判断。** 已知至少两处：`goals/Index.vue:157`、`health/Alerts.vue:224`。它们写成：

```ts
// 反例（不得保留）
} catch (e) {
  if (e !== 'cancel') ElMessage.error('删除失败')
}
```

这种写法在 `ElMessageBox` 取消时能用（拒绝值就是 `'cancel'` 字符串），但：
- `AbortError`（切换路由 / 新请求取消旧请求）会漏判 → 误弹红条；
- 归一化后的 `ApiError('', 'cancel')` 是对象，`!== 'cancel'` 恒真 → 必然误弹。

**统一做法（二选一，都合法）：**

| 写法 | 适用 | 说明 |
|------|------|------|
| `catch (e) { showError(e, 'goal.deleteFailed') }` | 删除/更新/提交 | `showError` 内部识别 cancel 并早退；非 cancel 走正常呈现 |
| `catch { /* 用户取消或放弃，零提示 */ }` | 纯确认框取消、导航中止 | 不做任何呈现；**必须**留一行注释说明「这里刻意静默」 |

### 7.3 既有「取消」可见标签（Phase 13 已收口的纪律，本阶段扩大检查范围）

Phase 13 把 `prevention/Profile.vue` 与 `health/Score.vue` 的 `取消` 改成了 `放弃编辑` / `放弃生成`。本阶段迁移的**弹窗**里仍存在 `取消` 可见标签（如 `reminders/Index.vue` 的对话框 footer）。**不要求**在本阶段全部整改 —— 那是文案债，与错误呈现不一致是两件事。**但**本阶段**新增**的动作标签一律必须是动词 + 宾语（见 Copywriting Contract）。

---

## Copywriting Contract

### 通用纪律（沿用 Phase 11/12/13，逐条可 grep）

1. 每条失败说明**什么失败了 + 下一步怎么做**。禁止只陈述失败。
2. 禁止词（大小写不敏感）：`操作失败`、`请求失败`、`系统错误`、`出错了`、`Error`、`Failed`、`Invalid`、`Please`、`Sorry`、`Exception`、`null`、`undefined`，以及任何原始后端字段名与堆栈片段。
3. **每个动作标签是「动词 + 宾语」**：`返回首页`、`返回列表`、`重新加载该记录`、`重新加载阈值列表`、`打开编辑`。单字/通用词（`取消`、`确定`、`关闭`、`重试`）**不得**作为**新增**的可见主标签；`重试` 只允许作为 toast 内动作（其可访问名为 `重试：{message}`，已带对象）。
4. 长度上限：toast ≤ 30 字（字典回退）；S1 标题 ≤ 16、说明 ≤ 40；S2 标题 ≤ 16、说明 ≤ 40；汇总标题 ≤ 20。

### 本阶段新增/变更的文案

| Element | Copy |
|---------|------|
| S1 403 标题 | `无权访问此页面` |
| S1 403 说明（兜底） | `当前账号没有访问该页面的权限，请返回首页，或联系管理员申请开通。` |
| S1 403 说明（后端点名动作时） | 直接渲染后端消息，例如 `无权删除他人记录` / `无权修改其他用户的阈值数据` / `没有访问权限` |
| S1 403 主 CTA | `返回首页` |
| S2 404 标题 | `资源不存在` |
| S2 404 说明 | `该资源可能已被删除，请返回列表查看最新数据。` |
| S2 404 主 CTA | `返回列表` |
| S2 404 次 CTA | `重新加载该记录` |
| S3 汇总标题 | `请修正以下 {N} 项填写问题`（不变） |
| S3 汇总条目 | `{中文字段标签}：{后端消息}`；未知字段 → `{后端消息}` |
| S4 `重试`（toast 内） | 可见 `重试`，可访问名 `重试：{message}`（不变） |
| S4 `打开编辑`（恢复动作） | 不变，由 `ERROR_CODE_ACTIONS['resource.exists']` 自动补齐 |
| 路由级 404 标题 | `页面不存在` |
| 路由级 404 说明 | `该页面可能已下线，或链接地址有误。` |
| 路由级 404 CTA | `返回首页` |

### 字典新增（`ERROR_MESSAGES`，26 键 → 总数 18 + 26 = **44**）

| Key | Copy | 字长 |
|-----|------|------|
| `goal.loadFailed` | `无法加载健康目标，请点击重试` | 15 |
| `goal.saveFailed` | `无法保存健康目标，请检查填写内容` | 16 |
| `goal.deleteFailed` | `无法删除该健康目标，请稍后重试` | 15 |
| `reminder.loadFailed` | `无法加载提醒列表，请点击重试` | 14 |
| `reminder.saveFailed` | `无法保存提醒设置，请检查时间格式后重试` | 19 |
| `reminder.deleteFailed` | `无法删除该提醒，请稍后重试` | 14 |
| `screening.loadFailed` | `无法加载体检报告，请稍后重试` | 14 |
| `screening.deleteFailed` | `无法删除该体检报告，请稍后重试` | 15 |
| `mood.saveFailed` | `无法保存心情记录，请稍后重试` | 14 |
| `realtime.loadFailed` | `无法加载实时数据，请点击重试` | 14 |
| `realtime.saveFailed` | `无法保存实时指标，请检查设备连接` | 16 |
| `alert.loadFailed` | `无法加载健康预警，请点击重试` | 14 |
| `alert.updateFailed` | `无法更新预警状态，请稍后重试` | 14 |
| `alert.deleteFailed` | `无法删除该预警，请稍后重试` | 13 |
| `ai.loadFailed` | `无法加载 AI 会话，请稍后重试` | 16 |
| `ai.deleteFailed` | `无法删除该会话，请稍后重试` | 14 |
| `wellness.loadFailed` | `无法加载保健数据，请稍后重试` | 14 |
| `wellness.saveFailed` | `无法保存保健记录，请稍后重试` | 14 |
| `device.loadFailed` | `无法加载设备配置，请点击重试` | 14 |
| `device.saveFailed` | `无法保存设备配置，请稍后重试` | 14 |
| `device.syncFailed` | `设备同步未完成，请检查连接后重试` | 16 |
| `user.loadFailed` | `无法加载个人信息，请稍后重试` | 14 |
| `user.saveFailed` | `无法保存个人信息，请检查填写内容` | 16 |
| `metric.loadFailed` | `无法加载指标记录，请点击重试` | 14 |
| `score.loadFailed` | `无法加载健康评分，请点击重试` | 14 |
| `resource.gone` | `该记录已不存在，请刷新列表` | 12 |

**既有 18 键中可直接复用于迁移的：** `metric.saveFailed`、`metric.deleteFailed`、`ai.parseFailed`、`ocr.uploadFailed`、`report.generateFailed`、`threshold.*`、`profile.*`、`push.saveFailed`、`network.offline`、`server.unavailable`。台账必须写明「复用既有键」还是「新增键」。

### 字段标签新增（`FIELD_LABELS`，22 键 → 总数 17 + 22 = **39**）

| Field | Label |
|-------|-------|
| `bloodType` | 血型 |
| `clientId` | 客户端 ID |
| `clientSecret` | 客户端密钥 |
| `confirmPassword` | 确认密码 |
| `cronExpression` | Cron 表达式 |
| `dateRange` | 起止日期 |
| `email` | 邮箱 |
| `file` | 报告文件 |
| `institution` | 体检机构 |
| `newPassword` | 新密码 |
| `nickname` | 昵称 |
| `oldPassword` | 原密码 |
| `password` | 密码 |
| `phone` | 手机号 |
| `reminderType` | 提醒类型 |
| `reportDate` | 报告日期 |
| `reportName` | 报告名称 |
| `targetValue` | 目标值 |
| `title` | 标题 |
| `trend` | 趋势 |
| `unit` | 单位 |
| `username` | 用户名 |

**计数核对（执行者必须按下表验算，不得凭感觉）：**

| 来源 | 键数 |
|------|------|
| 既有 `FIELD_LABELS`（Phase 11 交付） | 17 |
| 本表新增 | 22 |
| **合计** | **39** |

新增的 22 个键完整清单（与本表逐行一致，`institution` 只出现一次）：`bloodType`、`clientId`、`clientSecret`、`confirmPassword`、`cronExpression`、`dateRange`、`email`、`file`、`institution`、`newPassword`、`nickname`、`oldPassword`、`password`、`phone`、`reminderType`、`reportDate`、`reportName`、`targetValue`、`title`、`trend`、`unit`、`username`。

### 必须用 `labels` 覆盖的歧义字段（不改全局表）

| 文件 | 字段 | 全局/默认标签 | 覆盖为 |
|------|------|--------------|--------|
| `views/goals/Index.vue` | `title` | 标题 | `目标标题` |
| `views/goals/Index.vue` | `value` | 指标数值 | `当前值` |
| `views/goals/Index.vue` | `description` | （无全局） | `目标描述` |
| `views/goals/Index.vue`（进度对话框） | `value` | 指标数值 | `进度值` |
| `views/reminders/Index.vue` | `title` | 标题 | `提醒标题` |
| `views/reminders/Index.vue` | `description` | （无全局） | `提醒描述` |
| `components/metric/UniversalMetricDialog.vue` | `notes` | 备注说明 | 保持全局（`备注说明`） |

### 文案纪律的机器可检查判据

```bash
# 迁移页不得出现裸的通用失败词
grep -rn "操作失败\|请求失败\|系统错误\|出错了" src --include=*.vue --include=*.ts | grep -v "__tests__"
# 期望：0

# 新增动作标签不得是单字通用词（本阶段的四个新标签）
grep -rn ">取消<\|>确定<\|>关闭<" src/components/common/ErrorState.vue
# 期望：0
```

---

## UI Considerations

> 形态驱动的 UI **状态**覆盖（empty / loading / error / populated / partial / overflow / zero-one-many / long-text）。
> 具体文案见 `## Copywriting Contract`，本节不重复。

Applicable state considerations resolved: **31 covered (29 explicit, 2 backstop, 0 unresolved)**

表面编号：**E1** S1 403 页级块 · **E2** S2 404 内容区块 · **E3** 左侧栏/页头（页级块的邻接壳） · **E4** 表单内联错误 · **E5** 表单汇总块 · **E6** toast（含动作） · **E7** 迁移后的列表页整体 · **E8** 迁移后的弹窗表单

### 空 / 加载 / 错误

| Category | Element(s) | Status | Resolution / Reason |
|----------|------------|--------|---------------------|
| empty | E7 list-collection | ✅ covered | 成功 0 条走 `ElEmpty` 或教学型空态（`Thresholds.vue` 的 `.threshold-empty`）；`ErrorState` 永不用于空态（Contract 3 §3.4 四态互斥表） |
| empty | E8 form | ✅ covered | 空表单是合法输入态；提交按钮**保持可点击**（Phase 11 AF-ERR-6），点击后走校验并给出汇总 + 内联，不做灰色死按钮 |
| empty | E2 404 | ✅ covered | 404 本身就是「内容缺失」的呈现，不存在「空的下级」 |
| loading | E7 / E8 | ✅ covered | 沿用既有 `v-loading` 单一遮罩；`ErrorState.retrying` 绑 `:loading` + `aria-busy="true"`。**不叠加骨架屏**（Phase 13 Contract 8 同一纪律：一种状态一种指示） |
| loading | E1 / E2 | ✅ covered | 状态块**不出现 loading 态** —— 它只在请求已完成并失败后渲染。请求在途时是 `v-loading`，不是状态块 |
| error | E4 / E5 / E8 校验失败 | ✅ covered | `FormErrorSummary` + 内联 `:error`，**零 toast**；`apply()` 不清输入、不关弹窗 |
| error | E7 加载失败 | ✅ covered | `ErrorState`（`load`/`server`/`network`）+ 零 toast；`catch` 只置状态位即 return（`Thresholds.vue:201-205` 是范式） |
| error | E6 网络/5xx | ✅ covered | 1 条常驻 toast + `重试`（Contract 5 变体表） |
| error | E1 403 | ✅ covered | 页级块替换页面内容，零 toast；页头标题保留 |
| error | E2 404 | ✅ covered | 内容区块，零 toast；页面其余部分照常可操作 |

### 部分 / 零一多 / 溢出 / 长文本

| Category | Element(s) | Status | Resolution / Reason |
|----------|------------|--------|---------------------|
| partial | E4 form | ✅ covered | 部分字段被拒：只为被拒字段渲染内联，汇总恰好 N 条；合法字段不标记，输入全保留（Phase 11 已交付） |
| partial | E7 list-collection | ✅ covered | 列表加载成功但分页第 2 页失败：页面保留第 1 页数据，失败只影响该次翻页（toast 1 条，不整页降级为 `ErrorState`） |
| zero-one-many | E5 汇总条目 | ✅ covered | N=1 → `请修正以下 1 项填写问题`；N≥2 → `{N}`；N=0 → 汇总块整块不渲染（不是空 alert） |
| zero-one-many | E6 toast | ✅ covered | 0 → 无节点；1 → 单条；N>3 → 挤掉最旧非 retry（见 5.4） |
| zero-one-many | E2 动作对 | ✅ covered | 恒为 2 个（`返回列表` + `重新加载该记录`）；**不得**因「没有上层列表」而退化成 1 个 —— 那种页面用页级块（Contract 2 §2.1）而非内容区块 |
| overflow | E5 汇总列表 | ✅ covered | `max-height: 144px; overflow-y: auto; overscroll-behavior: contain`（4 行 + 3 gap + 16 inset）；超过 4 条内部滚动，标题与首条常驻可见 |
| overflow | E7 列表页 | ✅ covered | 表格由 EP 自身横向滚动承担；本阶段不新增容器 |
| overflow | E1 / E2 块 | ✅ covered | 块自身 `max-width: 720px / 480px` + `margin: 0 auto`；父容器窄于该值时随父收缩，`flex-wrap` 让动作换行 |
| long-text | E4 内联错误 | ✅ covered | EP 的 `position: absolute` + `line-height: 1.4`，长消息**换行且重叠下一行**（这是 EP 既有行为，本阶段不改）；`data-field` 与汇总条目保证用户总能通过点击汇总定位。**已知限制：** >40 字的内联消息会压住下一字段的顶部 —— 由后端 `fieldErrors` 的 20 条上限 + 单条消息长度共同约束，不引入覆盖层 |
| long-text | E5 汇总条目 | ✅ covered | `display: inline` + 下划线，自然换行；容器 `max-height` 截断由滚动补偿 |
| long-text | E6 toast | ✅ covered | 后端下发消息**绝不截断**（`showError.ts:134-141`）；EP `max-width: calc(100% - 32px)` + `line-height: 1.5` 自然换行。已由 `showError.spec.ts` 的 60 字用例锁定 |
| long-text | E1 / E2 说明句 | 🧪 backstop | 说明 ≤40 字的设计上限；页级块 `max-width: 720px` 下单行可达 40 字，内容区块 480px 下折 2 行。**若后端消息超过 40 字**（如 `无权修改其他用户的阈值数据` 之外的异常），必须换行而不截断。需在 320px / 640px 两个视口做一次视觉复核 |
| long-text | E7 列表页名称列 | ✅ covered | 沿用各页 `show-overflow-tooltip` / `text-overflow: ellipsis`；本阶段不改列表排版 |

### 交互与可访问性状态

| Category | Element(s) | Status | Resolution / Reason |
|----------|------------|--------|---------------------|
| populated | E7 / E8 | ✅ covered | 正常态零错误 chrome；成功提示用既有 `ElMessage.success`（不在本禁令内） |
| populated | E3 nav | ✅ covered | 页级块渲染时侧边栏与页头保持完全可用（不是路由跳转，不是全屏遮罩） —— 用户必须有出口 |
| loading | E6 动作型 | ✅ covered | `重试` 点击后 toast 立即关闭（`handler.close()`），由调用方重新发起请求；**不自动重试**（Phase 11 D-03） |
| error | E6 动作型 | ✅ covered | 权限 toast **无 `重试`**；资源消失 toast 为 4s 定时（带 `资源已不存在，请刷新列表` 的可行动作） |
| long-text | E1 / E2 动作标签 | 🧪 backstop | `重新加载该记录`（8 字）在 320px 下与 `返回列表` 并排会换行；已声明 `flex-wrap: wrap` 与 <639px 的纵向拉伸。需在 320px 复核按钮是否撑出块宽 |

**两个 backstop 的验证方式：** 在 320px 与 640px 两个视口实跑一次 S1/S2，读数（说明句行数、按钮是否换行、有无横向溢出）。无显式证据则不允许声称通过。

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none（`Tool: none`） | not required |
| 第三方 registry | none | not applicable |

本阶段**不引入任何外部 registry 组件**。所有组件来自已安装的 `element-plus@2.14.0`；`ErrorState` 的扩展、`useFormServerErrors` 的 `labels` 参数、`showError` 的 `presentStates` 参数均为本仓库自写。

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

---

## Appendix — 顺序影响、测试断言影响与未收口项

### A-1 必须先于迁移落地的三处（顺序约束）

| 序 | 事项 | 不改的后果 |
|----|------|-----------|
| 1 | `appError.ts` 新增 `forbidden` / `notFound` 两个 kind + 判定规则（Contract 1.1） | 87 处迁移完成后才发现 403/404 无对应通道，全部需要二次返工；且 `prevention/Profile.vue` 的创建模式分支会被 `business` 覆盖而失效 |
| 2 | `theme.css` 的错误描边全局块（Contract 4.2） | 迁移页的内联错误只有红字、没有描边 —— 「87 个页面看起来一样」不成立，且「首个无效字段可见」不成立 |
| 3 | `showError` 的 `presentStates` 选项 + `ErrorState` 的 `scope` / `actions` prop | 动作型 403/404 无处可去，执行者会临时写 `ElMessage.error` —— 把刚清零的裸调用重新引入 |

### A-2 测试断言影响（**必须显式修改，不是放宽**）

| 文件 | 断言 | 现状 | 本契约要求 |
|------|------|------|-----------|
| `__tests__/utils/errorMessages.spec.ts` | `DICTIONARY_KEYS` 数组 + `expect(Object.keys(ERROR_MESSAGES)).toHaveLength(18)` | 18 键精确相等 | **扩展为 44**：把 26 个新键逐条加入 `DICTIONARY_KEYS` 数组，并把 `toHaveLength` 改为 44。精确相等断言本身**保留** —— 它防止的是无意的键泄漏，不是新增键 |
| 同上 | `expect(Object.keys(FIELD_LABELS)).toHaveLength(17)` | 17 标签 | **改为 39**，并补 3–4 条 `fieldLabel('cronExpression' \| 'targetValue' \| 'clientSecret')` 的定点断言 |
| 同上 | `caps every entry at 30 characters` | 遍历全部键 | 自动覆盖新键；26 个新键已全部 ≤30（本契约逐条标注字长） |
| `__tests__/utils/showError.spec.ts` | `keeps every dictionary fallback at or below 30 characters` | 遍历 `Object.keys` | 自动覆盖，无需改断言 |
| `__tests__/utils/appError.spec.ts` | `const kinds: ErrorKind[] = [...6 项]` | 局部数组，非联合类型穷举 | **无需修改**（新增 kind 不破坏该循环）；但**必须新增** 4 条反回归用例：① 真 HTTP 403 → `forbidden`；② 信封 `errorCode: 'auth.forbidden'` + `code: 500` → `forbidden`（**不是** `business`）；③ 信封 `code: 404` → `notFound`；④ `errorCode: 'health.profile_not_found'` → `notFound`（**不是** `business`） |
| `__tests__/components/ErrorState.spec.ts` | 15 条既有断言 | 覆盖 `variant` / `retry` / `retryLabel` / `aria` / 焦点 | **不变**（新增 prop 默认值等于原行为）。**新增** 5 条：`scope="page"` 加 `.error-state--page` 类且渲染 Display 标题类；`actions` 非空时不渲染 `retry` 按钮；`actions` 按声明顺序渲染；`actions[].label` 同时是可见文本与 `aria-label`；`forbidden` 仍走中性圆 |
| `__tests__/composables/useFormServerErrors.spec.ts` | 既有断言 | 不传 `labels` | **不变**；**新增** 1 条：`labels` 覆盖优先于 `FIELD_LABELS` |
| `__tests__/views/QuickInputDialog.spec.ts` | E2E 契约 | 成功文案 | **不变** |

**基线（D-05 锁定值）：** 后端 `mvn clean verify -DskipITs` ≥ **845** 全绿；前端 `npm run test:run` ≥ **445** 全绿（37 文件，本日实测 445/37）。新增用例只能加不能减。

### A-3 本阶段明确不做（避免范围蔓延）

| 项 | 理由 |
|----|------|
| 把全站表单标签统一到 14px/600 两档字重 | 页面级排版债，与错误呈现一致性正交 |
| 把全站弹窗的 `取消` 可见标签改成动词 + 宾语 | 文案债；只在**新增**标签上强制 |
| 在 `router.beforeEach` 加角色门禁 | 前端无 `role` 字段（`UserVO` 无此属性），加了就是与后端不一致的第二真值源 |
| 引入视觉回归 / 截图 diff | `14-CONTEXT` `## Deferred Ideas` 明确排除 |
| 错误监控上报（Sentry 类） | 同上 |
| 修 `Testcontainers` Docker socket 限制 | 基础设施，WINDOWS.md #3 已记录 |
| 把 `push.ts` 的 `showError(..., { silent: true })` 重构为 `normalizeApiError` | 行为等价；属清理，不属本阶段验收项（可在迁移中顺带，但台账不必记录） |

### A-4 与既有契约的关系声明

| 上游契约 | 本阶段动作 |
|---------|-----------|
| 11-UI-SPEC Contract 1（`ErrorState`） | **扩展**：`scope` / `actions`；`variant` 的 `forbidden`/`notFound` 从「API 存在但未接线」变为**已接线**（Phase 11 §Scope note 明确把接线留给 Phase 14） |
| 11-UI-SPEC Contract 1 的「`重试` 为唯一 primary」 | **局部覆盖**：仅 `actions` 通道；404 的主 CTA 改为 `返回列表`（理由见 Contract 3 §3.2）；`retry` 通道与 toast 通道不变 |
| 11-UI-SPEC Color 的 `--text-3` 用于 ErrorState 说明 | **收紧**：改为 `--text-2`（Phase 12/13 已两次确认 3.97:1 不达 AA） |
| 11-UI-SPEC Contract 3（内联错误） | **补充交付**：错误描边（原契约只写文字样式，未覆盖 `theme.css` 的 `!important` 压盖） |
| 11-UI-SPEC Contract 4（toast） | **扩展**：两种新的 `presentStates` 变体；其余配置不变 |
| 12-UI-SPEC Contract 9 / 13-UI-SPEC Contract 6（按钮字重归一化） | **沿用**；本阶段在 `ErrorState` 的 scoped 块内复述同一规则（因为 `ErrorState` 是跨页共享组件，不能依赖页面级 `:deep`） |