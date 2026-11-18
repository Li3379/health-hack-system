---
phase: "16"
slug: "risk-engine-inputs"
status: draft
shadcn_initialized: false
preset: none
created: "2026-11-18"
---

# Phase 16 — 风险评估引擎输入重组 — UI Design Contract

> 视觉与交互契约。由 gsd-ui-researcher 生成，gsd-ui-checker 校验。
>
> **本契约不新建组件、不新建页面、不新建颜色或字号。** 本阶段是后端重组（D-01…D-07），
> 前端 `Risk.vue` 的结构已经就位。契约要回答的唯一问题是：**当引擎开始读新的指标之后，
> 已有的两个面板会进入哪些从未出现过的内容与状态组合，而那些组合是否仍然读得懂。**
>
> 契约中每一条「必须修正」都附了可复现的证据（`file:line` + 实测值）。任何未在此定义的
> 视觉形态都视为缺陷，而非自由度。

---

## 本阶段受影响的三个表面

| # | 表面 | 位置 | 契约 |
|---|------|------|------|
| S1 | 指标覆盖面板 | `Risk.vue` `.risk-coverage`（模板 :80-155，样式 :1190-1345） | Contract 1 / 2 / 5 |
| S2 | 评分构成弹窗列表 | `Risk.vue` `.risk-dialog-breakdown`（模板 :283-310，样式 :1348-1450） | Contract 3 / 4 |
| S3 | 归因行的措辞与状态词汇 | 后端 `RiskContributorVO` + 前端 `stateLabel()` | Contract 6 / 7 |

**不在本契约内：** 批次折叠区（`.risk-batch*`，Phase 15 已交付且本阶段不改其结构）、
`risk-estimate` 补填面板、创建流程、分页。本阶段不动它们的任何一行 —— **两个具名例外**
（两者都只有一条颜色声明，零结构、零几何，见 §Color）：

| # | 选择器 | 改动 | 为什么它仍算「不动批次区」 |
|---|--------|------|---------------------------|
| E1 | `.risk-batches__empty`（`Risk.vue:1044-1048`） | `color: var(--text-3)` → `var(--text-2)` | 匹配 `.risk-batch*` 前缀，但属于「历史区整体为空」的列表级空态，不是一个批次卡片 |
| E2 | `.risk-batch__legacy`（`Risk.vue:1038-1042`） | `color: var(--text-3)` → `var(--text-2)` | 是单个批次内的说明句，但承载的是「为什么这里没有明细」这条信息（13 字），属信息文字，对比度门槛不因位于批次卡片内而降级（判定与 §Color 对 `.risk-coverage__hint` 的论证同源） |

这两个例外是**枚举闭合的**：该前缀下除上表两条 `color` 声明外，不得有任何其他改动。
任何第三种改动都必须先改写本表，而不是在执行时「顺带」发生。

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none（非 shadcn 项目） |
| Preset | not applicable |
| Component library | `element-plus@2.14.0`（已安装；本阶段零新增依赖） |
| Icon library | `@element-plus/icons-vue@2.3.2`（本阶段不新增图标用法） |
| Font | `--font-ui`: `'DM Sans', 'Noto Sans SC', system-ui, …`；`--font-mono`: `'JetBrains Mono', …`（`theme.css:74-75`） |

**为什么不是 shadcn：** 前端为 Vue 3 + Element Plus，`components.json` 不存在，
shadcn 初始化门禁不适用（`ls components.json` → `No such file or directory`，2026-11-18 实测）。
本项目已有成体系的 CSS token 系统（`theme.css`，76 个 token），引入第二套 UI 库会直接违反
v1.2 以来的一致性原则。

---

## Component Inventory

Enumerated by `node -e "const fs=require('fs');const b='node_modules/element-plus/es/components';console.log(fs.readdirSync(b).filter(n=>fs.existsSync(b+'/'+n+'/index.mjs')).length)"` — 91 components — element-plus@2.14.0 — 2026-11-18.

Icons enumerated by `node -p "Object.keys(require('@element-plus/icons-vue')).length"` — 293 icons — @element-plus/icons-vue@2.3.2 — 2026-11-18.

下表只列出本阶段涉及判定的子集，**不是封闭白名单**；执行者可继续使用该库导出的任何组件。

| Component | Import path | 本阶段用途 |
|-----------|-------------|-----------|
| `ElCard` | `element-plus` | 页面容器（不变） |
| `ElDialog` | `element-plus` | S2 宿主；`width="600px"`（不变） |
| `ElDescriptions` | `element-plus` | 弹窗上半部字段表（不变） |
| `ElCollapse` / `ElCollapseItem` | `element-plus` | 批次折叠（本阶段不动） |
| `ElSkeleton` | `element-plus` | 覆盖面板加载态（不变） |
| `ElTag` | `element-plus` | 风险等级标签（不变；**不得**用于归因/覆盖状态） |
| `ElButton` | `element-plus` | 详情 / 创建评估（不变） |
| `ElEmpty` | `element-plus` | **不使用** —— 覆盖面板与批次区各有自己的空态文案，见 Copywriting |

**明确不引入：** 任何图表库调用、任何新图标、任何新组件文件。S1/S2 的 DOM 结构在本阶段
保持模板逐行不变，改动只发生在 CSS 声明与模板内的文本表达式上。

---

## Spacing Scale

`theme.css:77-80` 已定义 10 档 4 倍数刻度。本契约以 token 名为准，不新造值。

| Token | Value | 本阶段绑定到 |
|-------|-------|-------------|
| `--sp-1` | 4px | 本阶段新增间距的最小单位。**不绑定** `padding: 1px 6px`（那两值都不是 4 的倍数）—— 见例外表第 1 条 |
| `--sp-2` | 8px | 弹窗构成列表的 `padding: 8px 12px` 垂直分量；标题 → 列表间距 `margin-bottom: 8px` |
| `--sp-3` | 12px | 覆盖面板行 `padding` 水平分量（`7px 12px`）、列表项 `gap: 10px`→见例外；面板内边距 `padding: 16px` 的水平补偿 |
| `--sp-4` | 16px | `.risk-coverage { padding: 16px }`、`margin-bottom: 16px`；`.risk-dialog-breakdown { margin-top: 16px }` |
| `--sp-5` | 24px | 弹窗列表缩进基线（`padding-left: 24px` 用于假设列表） |
| `--sp-6` | 32px | 空态垂直留白（`.risk-batches__empty { padding: 32px 0 }`） |
| `--sp-7` | 48px | 本阶段不使用 |
| `--sp-8` | 64px | 本阶段不使用 |

**例外（已存在，本阶段不新增，仅登记以免被误判为漂移）：**

| # | 值 | 位置 | 说明 |
|---|----|------|------|
| 1 | `1px 6px` | `.risk-coverage__state { padding }`（`Risk.vue:1284`）、`.risk-dialog-breakdown__state { padding }`（`Risk.vue:1398`） | 11px mono pill 的最小内边距。**1px 与 6px 都不是 4 的倍数** —— 这是既定形态。11px × 1.4 = 15.4px 行盒 + 2px 垂直内边距 ≈ **17px 高**；把垂直分量提到 `--sp-1` 的 4px 会变成 ≈ 23px，pill 比 13px 行（19.5px）还高，撑开整行。本阶段不改 |
| 2 | `7px 12px` | `.risk-coverage__item { padding: 7px 12px }`（`Risk.vue:1252`） | 覆盖面板行高压缩，Phase 15 已交付；`7px` 不是 4 的倍数。本阶段不改行高 |
| 3 | `10px` | `.risk-coverage__item { gap: 10px }`（`Risk.vue:1250`）、`.risk-dialog-breakdown__item { gap: 10px }`（`Risk.vue:1379`） | 栅格列间距。`--sp-2`(8) 会让 96px 的标签列与 88px 取值列视觉粘连，`--sp-3`(12) 又会让 5 列累计多占 8px。本阶段不改 |
| 4 | `14px` | `.risk-coverage__group + .risk-coverage__group { margin-top: 14px }`（`Risk.vue:1224`） | 分组之间留白。落在 `--sp-3`(12) 与 `--sp-4`(16) 之间，是 Phase 15 为压缩三组总高选的中间值。本阶段不改 |
| 5 | `6px` | `.risk-coverage__group-title { margin: 0 0 6px }`（`Risk.vue:1228`） | 分组标题与其列表的贴合间距。`--sp-2`(8) 会让标题与所属列表看起来分家。本阶段不改 |
| 6 | `2px` | `.risk-coverage__item { row-gap: 2px }` @ ≤767px（`Risk.vue:1339`） | 窄屏两列折行后，标签行与 note 行的基线贴合。本阶段不改（该媒体查询必须保留，见 Contract 2） |
| 7 | `6px` | `.risk-batch__rows { gap: 6px }`（`Risk.vue:998`） | 属批次区，本阶段不动（该前缀下的间距整体不在本契约内，仅登记以免被误判为漂移） |
| 8 | `10px` | `.risk-dialog-breakdown__warn { margin: 10px 0 0 }`（`Risk.vue:1444`） | 弹窗底部缺失警示句与上方列表的间距。在 S2 内、本阶段要改的颜色也在这里（`--border-1` 修复的同一区块），故必须登记。`--sp-2`(8) 会让警示句贴上最后一行，`--sp-3`(12) 又比列表行内边距 `8px` 的节奏更松；`10px` 与该列表的 `gap: 10px` 同值，是同一节奏的延续。**本阶段不改此值** |

**闭合口径（可直接复跑核对）：** 8 行覆盖 **10 条声明** —— 第 1 行含 2 条（`:1284` / `:1398`）、
第 3 行含 2 条（`:1250` / `:1379`），其余 6 行各 1 条。值域 **6 种**（`1px 6px` / `7px` / `10px` /
`14px` / `6px` / `2px`）。第 3 行与第 8 行同为 `10px` 但作用于不同属性（列间距 vs. 外边距），
故分行登记。

**S1 与 S2 样式区间（`Risk.vue:1190-1450`）内的间距声明中，非 4 倍数者恰为上表第 1–6 行与第 8 行
（共 7 行 / 9 条声明；第 7 行在 `:998`，落在区间外）；不存在第 10 条。**
核对方式：把该区间内 `margin` / `padding` / `gap` / `row-gap` 声明列出后，非 4 倍数项应逐一对上
`1224` `1228` `1250` `1252` `1284` `1339` `1379` `1398` `1444` 这 **9 个行号**。
（注意：区间内 `border: 1px`、`border-radius: 6px`、`font: … 13px`、`@media … 767px` 等**不是间距**，
不计入本表 —— 它们是描边、圆角、字号与断点，各有自己的契约归属。）

**本阶段新增的声明必须落在 4 的倍数上。** 唯一新增的几何变更（Contract 4 的标签列宽
`80px → 88px`）是 4 的倍数。

---

## Typography

**4 个字号、2 个主字重**，全部为 `Risk.vue` 既有值，本阶段**不新增字号、不改字重**。

| Role | Size | Weight | Line Height | 本阶段用于 |
|------|------|--------|-------------|-----------|
| Body | 13px | 400 | 1.5 | S1 覆盖行正文、S2 归因行正文、`explanation` 句 |
| Label | 12px | 400 | 1.4–1.5 | S1 `riskNote`、参考范围、顶部摘要、分组提示 |
| Heading | 14px | 600 | 1.4 | 面板标题、「评分构成」标题、指标名与疾病名单元格 |
| Pill | 11px | 500 | 1.4 | 三处 11px mono 徽标（`--font-mono`） |

**字重的例外说明（必须保留，否则 checker 会视为第三档字重漂移）：**

500 字重在 `Risk.vue` 中共 **3 处声明，全部是 `font: 500 11px/1.4 var(--font-mono)`** ——

| # | 选择器 | 行号 | 形态 |
|---|--------|------|------|
| 1 | `.risk-coverage__state` | `:1283` | 覆盖态 pill（S1） |
| 2 | `.risk-dialog-breakdown__state` | `:1397` | 归因态 pill（S2） |
| 3 | `.risk-estimate__source-origin` | `:1123` | 评估依据面板的来源徽标（`健康档案` / `指标录入` / `档案缺失` 等） |

**受约束的性质（不是「恰好两处」，而是这个）：500 只允许出现在 11px + `--font-mono` + 短徽标
的组合上。** 三个声明都满足，且都是 Phase 15 已交付的既有形态。本阶段**不新增任何 500 字重声明**，
也不把 500 扩展到任何 13px/14px 的文字正文。新增的 badge 类型必须落在同一性质下；新增的
pill 类型（若后端将来新增状态）必须沿用既有的两个 pill 类，不得另起字号。

**字号例外（登记，不改）：** `.risk-batches__empty`（`Risk.vue:1044-1048`）**没有 `font-size`
声明，继承 `body` 的 `15px`**（`theme.css:285-287`）。它不是 `Risk.vue` 声明的第五档字号，
而是全局正文默认值在未声明元素的落点。本阶段只改它的 `color`，**不得**顺手给它加
`font-size: 13px` —— 那会把一个「历史区空态」缩成与表内文字同号，读成「一行数据」而不是
「一整块是空的」。四个声明字号（13/12/14/11）仍成立：它们描述的是本组件**主动声明**的字号。

（若把第 3 处也算作「pill」会与上表的 4 行角色表冲突 —— 它是徽标而非状态 pill，
故本契约用「11px mono 徽标」这一性质描述三者，而不是用数量。）

**不得**把 `explanation` 降到 12px 来吸收四行 MISSING 的视觉重量 —— 那会同时降低
「为什么计了这些分」的可读性，而这句话正是整个弹窗存在的理由。

---

## Color

所有色值来自既有的两处来源之一：`theme.css` 的 token，或 `Risk.vue` 声明中已存在的
行内 fallback 字面量。**本阶段不新增任何颜色 token、不新增任何颜色字面量。**

**一处必须如实说明的来源缺口：** 下表的 `--warning-700` / `--warning-50` / `--success-700` /
`--success-50` **在 `theme.css` 中定义数为 0**（实测 `grep -rn -- "--warning-700\s*:\|--success-700\s*:" src/` → 0 条）。
它们今天能生效，完全靠 `Risk.vue` 里每处引用自带的 fallback 字面量
（`var(--warning-700, #b45309)` 等，共 14 处引用、全部带 fallback）。因此：

- 本契约把它们当作**有效色值**登记（实测对比度基于 fallback 字面量计算，结论成立）；
- 但它们**不是** `theme.css` 的 token，不得在下游描述为「来自 theme.css」；
- 把它们补进 `theme.css` 是一次 token 补齐，**不在本阶段**（会新增 4 个 token，违反「零新增颜色」）。

下表对比度为 WCAG 2.1 相对亮度公式实测（半透明值按合成后计算）。

### 60 / 30 / 10 分配

| 占比 | 角色 | Dark | Light | 本阶段占用位置 |
|------|------|------|-------|---------------|
| 60% | 主导 / 画布 | `--bg` `#0a0b0e` | `--bg` `#f5f7fa` | 页面背景（`el-card` 之下） |
| 30% | 次主导 / 表面 | `--surface-1` `#111217` | `#ffffff` | 覆盖面板容器、列表底板、弹窗内容面 |
| 10% | 强调 | `--accent-cool` `#5EEAD4` | `#0d9488` | **仅限**：`创建评估` 主按钮底面、分页当前页、`:focus-visible` 轮廓 |
| 语义 / 错误（独立于 60/30/10） | — | `--hhs-color-error-text` `#f56c6c` | `#b91c1c` | 仅 HIGH 与 CONTRIBUTED 两态的文字/底纹（见下） |

**强调色绝不用于状态。** 两处**状态 pill**（`.risk-coverage__state`、`.risk-dialog-breakdown__state`）
一律走语义色（红/橙/绿/灰），不得用 accent-cool ——
accent 在本项目已经固定表达「可交互的主操作」，若也表达「正常」，会让唯一的主 CTA 失去区分度。
（`risk-estimate__source-origin` 是第三处 11px mono 徽标，走同一套语义色，同样不用 accent。）

### 状态色（两套词汇各自一套配色，均为既有声明）

| 状态 | 前景 token | 底纹 token | Dark 实测 | Light 实测 |
|------|-----------|-----------|-----------|-----------|
| `HIGH` / `CONTRIBUTED` | `--hhs-color-error-text` | `--hhs-color-error-tint` | 6.45:1 | 6.47:1 |
| `LOW` / `ASSUMED` | `--warning-700, #b45309` | `--warning-50, #fffbeb` | 4.84:1（底纹上） | 4.84:1 |
| `NORMAL` | `--success-700, #15803d` | `--success-50, #f0fdf4` | 4.79:1（底纹上） | 4.79:1 |
| `MISSING`（两套） | `--text-2` `#c6c9d2` | `--surface-2` `#171921` | 10.59:1 | 7.47:1 |

### 必须修正的对比度缺陷（本阶段唯一强制收紧）

`--text-3` 在 Light 主题下**只有 3.97:1**（`#7c7f8e` on `#ffffff`），12px/13px 正文不达
WCAG AA 4.5:1。Light 是本项目实际首屏默认主题（`stores/theme.ts` 的 `storedTheme || 'light'`）。

**同一收紧在 Phase 14 §Color 已执行过一次**（`ErrorState` 的说明句 `--text-3` → `--text-2`），
本阶段沿用同一判定，只作用于承载信息的文字：

| 选择器 | 现状 | 改为 | 理由 |
|--------|------|------|------|
| `.risk-coverage__range`（:1277-1280） | `color: var(--text-3)`，12px | `var(--text-2)` | 参考范围是用户判断「我这项算不算高」的依据，属信息文字 |
| `.risk-coverage__empty`（:1325-1330） | `color: var(--text-3)`，12px | `var(--text-2)` | 空态说明句，承载「下一步」（见下方 Copywriting 的改文案） |
| `.risk-batches__empty`（:1044-1048） | `color: var(--text-3)`；**无 `font-size` 声明，继承 `body` 的 15px**（初版误记为 13px） | `var(--text-2)` | 同上 |
| `.risk-coverage__hint`（:1233-1236） | `color: var(--text-3)`，12px | `var(--text-2)` | **改**：见下方「为什么 hint 也改」 |
| `.risk-batch__legacy`（:1038-1042） | `color: var(--text-3)`，13px | `var(--text-2)` | **改**：见下方「为什么 `.risk-batch__legacy` 也在名单里」（§「本阶段受影响的三个表面」例外 E2） |

**`var(--text-3)` 全量清点（7 处，5 改 2 留 —— 本修订补全，避免沉默即豁免）：**

| # | 选择器 | 行号 | 文案/形态 | 判定 |
|---|--------|------|-----------|------|
| 1 | `.risk-coverage__range` | `:1279` | 参考范围数值 | **收紧** |
| 2 | `.risk-coverage__hint` | `:1235` | 分组提示句 | **收紧** |
| 3 | `.risk-coverage__empty` | `:1329` | 空态说明句 | **收紧** |
| 4 | `.risk-batches__empty` | `:1047` | 列表级空态句 | **收紧** |
| 5 | `.risk-batch__legacy` | `:1040` | `该记录创建时未保存评分明细` | **收紧**（13px 信息句） |
| 6 | `.risk-batch__legacy-badge` | `:956` | `存量记录`，12px | 保持（已登记，见下） |
| 7 | `.risk-batch__delta--flat` / `--first` | `:1017` | `—` / 首次评估的无分差记号 | 保持（纯符号位，见下） |

7 = 5 + 2，不存在第 8 处，也不存在「未判定」的第 8 种情况。

**为什么 `.risk-batch__legacy` 也在名单里（本修订新增）：**
`该记录创建时未保存评分明细` 是一句 13 字的信息句，解释「这一批次为什么没有明细可展开」——
它回答的是用户的真实疑问，不是对某条已渲染记录的修饰。13px 在 Light 下 3.97:1，不达 AA。
它位于批次卡片内部，而批次区被 §「本阶段受影响的三个表面」列为「不改」，**但 scope fence 不能代替对比度门槛**：
被排除的是「改结构与几何」，不是「让一句信息文字达到可读」。因此本契约按 E2 明确具名例外，
只改 `color`，**不动那句已声明为 13px 的字号**（13px 正是 §Typography 的 Body 档；降到 12px 会让
它落进 Label 档，把「一句说明」读成「一条标注」）。

**为什么 `.risk-coverage__hint` 也在名单里（本修订新增）：**
原文把它豁免于「紧邻已用 `--text-2` 的标题主体」的邻接论证。**对比度合规不取决于邻接** ——
`（有数据，但不参与本次风险评估）` 与 `（{n} 项；这些不是「正常」，是「不知道」）` 这两句
本身就是信息：前者告诉用户「这条数据为什么不算」，后者是 Phase 15 建立的核心区分语。
在 Light 主题 12px 下 3.97:1 不达 AA。豁免论证若成立，也是**关于重要性**的论证，无法免除
对比度门槛。本契约把它并入同一收紧：**只改颜色，不改字号、不改字重、不改位置。**

**保持 `--text-3` 的两个位置（非信息文字，不改 —— 与上方 7 处清点表的第 6、7 行一一对应）：**
`.risk-batch__legacy-badge`（`存量记录`，12px，是对一条已渲染记录的修饰，紧邻已用 `--text-2`
的疾病名与分数）、`.risk-batch__delta--flat/first`（`—` 与首次评估的无分差记号，纯符号位）。
这两处是 `--text-3` 在 `Risk.vue` 中仅存的引用；本阶段结束后该文件内 `var(--text-3)` 应为 **2 处**。
这是可用 `grep` 直接核对的**精确值**（不是「至少」）：
`grep -c "var(--text-3)" src/views/prevention/Risk.vue` → 2。

`--text-2` 实测：Dark 11.30:1 / Light 8.38:1（on `--surface-1`），两主题均通过。

---

## Component Contract 1 — 覆盖面板的分组必须互斥（阻断级）

### 1.1 缺陷事实

`Risk.vue:845-853` 的三个分组谓词**不是互斥的**，而是交叠的：

```ts
const usedMetrics    = computed(() => coverage.value.filter(m => !!m.riskNote))          // 不看 state
const otherMeasured  = computed(() => coverage.value.filter(m => !m.riskNote && m.state !== 'MISSING'))
const missingMetrics = computed(() => coverage.value.filter(m => m.state === 'MISSING'))  // 不看 riskNote
```

一个「有 `riskNote` 且 `state === 'MISSING'`」的指标会**同时**进入 `usedMetrics` 与
`missingMetrics`，于是同一个指标名在屏幕上渲染两次 —— 一次在「参与风险评估的指标」，
一次在「尚未采集」。用真实数据推演（2026-11-18 数据库实测）：

| 数据情形 | `usedMetrics` | `missingMetrics` | 重复渲染 |
|---|---|---|---|
| 今天，有数据的用户 | 血糖 / 收缩压 / 舒张压（3 项，均有值） | 其余 15 项 | **0 项** |
| 今天，零指标的新用户 | 同样 3 项，但全部 MISSING | 含这 3 项 | **3 项重复** |
| **D-02 落地后，任何用户** | 上述 3 项 + BMI + 血脂 4 项（8 项） | 含 BMI + 血脂 4 项 | **5 项重复** |

也就是说：本阶段把这个缺陷从「只有新用户能看到」变成「每一个用户都能看到」。
`CONTEXT.md` 的「数据现实」表已经写明血脂四项零行、`bmi` 档案 3/3 有 —— 所以重复是必然而非偶发。

同时 `coverageSummary.measured`（:857）与 `missingMetrics.length` 的分组标题
「（N 项；这些不是「正常」，是「不知道」）」会因为重复而**各多算 5**：
屏幕上 23 项，三组加起来的行数却是 28。

### 1.2 契约：分组谓词必须互斥

三个分组的并集恰好等于全部指标，交集为空：

| 组 | 谓词（唯一允许的写法） | 语义 |
|----|----------------------|------|
| 参与风险评估的指标 | `!!m.riskNote` | 引擎会读它。**无论有没有值**都在这里，因为「参与」描述的是引擎行为，不是数据状态 |
| 其他已测指标 | `!m.riskNote && m.state !== 'MISSING'` | 有值，但引擎不读 |
| 尚未采集 | `m.state === 'MISSING' && !m.riskNote` | 没值，且引擎不读 |

**为什么要改 `missingMetrics` 而不是改 `usedMetrics`：** 血脂四项在心血管评估里**确实参与**
（D-02），把它从「参与」组去掉就是说谎；而它在「参与」组里已经以 `未测` 状态诚实呈现。
`missingMetrics` 只负责「引擎不读、你也没有」的那批，减掉已在上面的行是唯一正确做法。

**验证（执行者必须实跑）：** 构造一个含 `{riskNote: '…', state: 'MISSING'}` 的行，
断言 `.risk-coverage__item` 的数量等于 `coverage.length`，且该指标名只在
「参与风险评估的指标」组内出现一次。这条断言用今天的代码会**失败**，用修正后的代码会通过 ——
即测试有牙齿。

### 1.3 连带修正：顶部摘要不能让「参与」数大于「已测」数

改完 1.2 之后，典型用户会看到：

```
已测 8 / 23 项 · 3 项超出参考范围 · 8 项参与风险评估
```

`8 项参与` 大于 `已测 8/23` 里的已测数（有值的只有 3 项）在数学上正确，但读起来像
「用了 8 项」。本契约要求把措辞改成**描述范围而不是描述使用**，并在有待补项时点明：

| 位置 | 现状 | 改为 |
|------|------|------|
| 摘要第三段（模板 :88-90） | `· {{ used }} 项参与风险评估` | `· {{ used }} 项纳入评估范围` |
| 新增（仅当 `usedMissing > 0`） | — | `（其中 {{ usedMissing }} 项未测）` |

`usedMissing = usedMetrics.filter(m => m.state === 'MISSING').length`。

**用 `未测` 而不是 `缺数据`（本修订修正）：** 摘要位于 **S1**，而 S1 同一批行在状态列渲染的正是
`未测`（`coverageStateLabel('MISSING')` → `未测`）。`缺数据` 是 **S2 归因态**的词
（`stateLabel('MISSING')` → `缺数据`）。Contract 6.1 立这两套词的理由是它们描述不同的事：
`未测` =「你没测这个指标」，`缺数据` =「这项没参与计分」。在 S1 里写一句「3 项缺数据」，
读起来像在说「计分缺了 3 项」，而同一屏幕上的那一列正写着「未测」—— 同一件事两个词，
读者只会怀疑是两件事。**词必须跟着表面走。**

下文所有涉及 S1 的表述（包括 UI Considerations 的对应条目与 A-4 测试清单）一律用 `未测`；
S2 弹窗底部警示句继续用 `缺数据`（那是归因态表面，不变）。

**不新增分组、不新增组件、不改行结构** —— 只改一句文案与一个 computed。
**不得**把缺失的参与项从「参与」组里挪走以让数字变小：那会重新引入 1.2 的谎言。

---

## Component Contract 2 — 覆盖面板的 `riskNote` 密度

D-03/D-05 之后，有 `riskNote` 的指标从 3 项增加到约 8 项（血糖、收缩压、舒张压、BMI、血脂四项）。
面板的栅格是：

```css
.risk-coverage__item { grid-template-columns: 96px 88px 96px 56px 1fr; }
```

`riskNote` 落在最后那个 `1fr`。宽度链路（`1fr` = 容器宽 − **512px** 的固定扣除）：

| 分量 | 值 |
|---|---|
| `.risk-page` 容器宽（**注意：没有 `max-width`**，`Risk.vue:921-923` 只有 `padding: 20px`） | 视口 − 侧栏（展开 240 / 折叠 64） |
| − 页面 `padding: 20px × 2` | −40px |
| − `el-card__body { padding: var(--el-card-padding) }`，默认 **20px**，`theme.css` 未覆盖 | −40px |
| − 面板 `padding: 16px × 2`（`.risk-coverage`） | −32px |
| − 固定列 96 + 88 + 96 + 56 | −336px |
| − 列间距 `gap: 10px × 4` | −40px |
| − 行内边距 `12px × 2` | −24px |
| **`1fr`** | **容器宽 − 512px** |

三个实测口径：

| 口径 | 容器宽 | `1fr` |
|---|---|---|
| 1440px 视口，侧栏展开 | 1200px | ≈ 688px |
| **1120px 内容宽（`--content-max-width` 参考值）** | 1120px | **≈ 608px** |
| 1024px 视口，侧栏展开 | 784px | ≈ 272px |

**608px 不在任何视口下都成立。** 常被误用的是两个数：(a) 忘掉 `el-card__body` 的 40px；
(b) 以为 `.risk-page` 有 `max-width`。它没有 —— `--content-max-width` 只被 `.container`、
dashboard、data-input、Alerts 使用，不在风险页链路上。所以 1120px 只是本契约选定的
**参考口径**（用于给出一个可复算的数），不是页面的实际宽度上限。

`riskNote` 是 12px `var(--font-ui)`（`Risk.vue:1290-1293`）。按**上限**计（每字符 12px，
即把拉丁字母也当全角）单条 24 字 ≈ 288px；按实际混排（CJK 12px + 拉丁约 6–7px）实测约
215–230px。**契约按上限设计**，因此：

- ≥1280px 视口（`1fr` ≥ 528px）：两档都单行，行高不变；
- 1024px 附近（`1fr` ≈ 272px）：上限估算下可能折 2 行，实际宽度下仍单行；
- ≤767px：媒体查询让 `note` 独占整行（见下），不适用于本算式。

**上限 24 字的作用是让最坏情况至多 2 行**，不是保证 1 行 —— 这一点必须写清，否则执行者
会在 1024px 看到 2 行就以为契约被违反。

**必须声明的两条：**

1. **`riskNote` 允许换行到最多 2 行，不得截断。** 截断会把「计入多少分」这个数字吃掉，
   而那正是这一列的全部价值。行长超出时用自然换行（`overflow-wrap: anywhere` 已在
   `.risk-estimate__source-value` 上用过同一手法，此处沿用）。
2. **允许行高增长。** 列表用 `border-bottom` 分隔而非等高行，行高不一致不破坏对齐基线
   （`align-items: baseline` 已保证同一行内文字对齐）。

**不得**为容纳更多 note 而把 `riskNote` 缩到 11px：11px 在本契约里专属 pill。

**窄屏（≤767px）已由既有媒体查询接管**（:1336-1345）：`riskNote` 与 `range` 一起
`grid-column: 1 / -1` 独占整行。这条规则**必须保留**，因为 8 条 note 在窄屏若挤在剩余列里
会逐条折成 3–4 行，面板高度失控。

---

## Component Contract 3 — 弹窗里四行连续 MISSING 的可读性

### 3.1 缺陷事实：分隔线现在是隐形的

`.risk-dialog-breakdown__item` 用 `border-bottom: 1px solid var(--border-1)`（:1383）作为行分隔。
**`--border-1` 从未被定义** —— 实测：

```
$ grep -rn "border-1" src/ --include=*.vue --include=*.css --include=*.ts | grep -v "var(--border-1)"
(空)
$ grep -rn "var(--border-1)" src/ | wc -l
8
```

`theme.css` 定义的边框 token 只有 `--border`、`--border-strong`、`--border-accent`。
引用一个未定义的自定义属性且**不带 fallback** 时，声明在 computed-value 阶段失效，
属性回落到初始值 —— 即 `border-bottom: none`。**所以这些行今天没有任何分隔线。**

今天之所以不明显：归因行最多 5–6 行且状态各异（红/绿/灰混排），靠色块即可分辨。
D-01 之后心血管归因变成「年龄 / BMI / 收缩压 / 舒张压 / 血糖 / 总胆固醇 / 甘油三酯 /
高密度脂蛋白 / 低密度脂蛋白」共 9 行，其中**血脂四行连续同为 MISSING**，
四行都是灰 pill + 斜体灰句、无边框 → 读起来是一整块灰。

### 3.2 契约：修 token，并接受四行连续

**必须把 8 处 `var(--border-1)` 全部改为 `var(--border)`。** 这是本契约中唯一改动
**颜色引用**的样式修正（几何修正另有 Contract 4 的列宽）。清单：

`:1096`（`.risk-estimate__sources` background）、`:1097`（同元素 border）、
`:1158`（`.risk-preparing` border）、`:1193`（`.risk-coverage` border）、
`:1242`（`.risk-coverage__list` border）、`:1254`（`.risk-coverage__item` border-bottom）、
`:1371`（`.risk-dialog-breakdown__list` border）、`:1383`（`.risk-dialog-breakdown__item` border-bottom）。

**为什么是 `--border` 而不是新增 `--border-1`：** 新增 token 会让站内出现两个几乎同值的边框色
（`--border` = `rgba(67,70,81,.5)`，另造一个会与之漂移），而本项目的既有结论是「边框只有三档」。
8 处全部改指既有 token，零新增颜色。

**四行连续 MISSING 保持逐行，不做聚合。** 明确决定与理由：

| 方案 | 判定 | 理由 |
|------|------|------|
| 逐行列出四项血脂（**采纳**） | ✅ | 验收标准 2 要求「血脂四项进入心血管评估；缺失时显示 MISSING +「此项未计入」」—— 聚合掉就看不到是哪一项缺，用户无法据此去补录 |
| 聚合成「血脂 4 项未采集」 | ❌ | 需要新增分组组件与新状态，超出 scope fence；且掩盖了四项各自的阈值 |
| 四行合并为一行多值 | ❌ | 同上，且破坏「一项输入 = 一条归因」的既有契约（各项之和 = 总分） |

分隔线修好后，四行的可读性由三重既有手段承担，不新增样式：
①1px `--border` 分隔；②每行独立的灰色 pill（`MISSING` → `--text-2` on `--surface-2`，两主题均 ≥7.4:1）；
③`explanation` 的 `font-style: italic`（:1439-1441 既有）。

**弹窗底部的既有警示句必须保留并自动更新数字**（模板 :303-309）：
`有 {{ missingContributorCount }} 项缺少数据，本结果未包含这些因素 —— 补齐后评分可能上升，请勿把「未计入」当作「正常」。`
在现有数据下它会显示 **4**，这正是本阶段想要的诚实提示。**不得**因为它占空间而移除。

---

## Component Contract 4 — 归因标签列宽必须容纳六字指标名

### 4.1 事实

`.risk-dialog-breakdown__item` 的标签列是 `80px`（:1378）。D-01/D-02 之后心血管归因的
`label` 将新出现六字名字：

| label | 字数 | 13px/600 下的文本宽 |
|---|---|---|
| 总胆固醇 | 4 | 52px |
| 甘油三酯 | 4 | 52px |
| 高密度脂蛋白 | 6 | **78px** |
| 低密度脂蛋白 | 6 | **78px** |

`78px` 对 `80px` 只剩 2px 余量。`.risk-dialog-breakdown__label` **没有** `white-space`
声明，因此任何亚像素累积或字体回退（`Noto Sans SC` 的汉字 advance 为整 13px，但 fallback 不保证）
都会让六字名折成两行，进而把该行行高从 1 行变成 2 行 —— 在一个 9 行的列表里，
只有两行变高会读成「这两项特别重要」。

同理检查覆盖面板：其标签列是 `96px`（:1249），78px 有 18px 余量，**无需改动**。

### 4.2 契约

| 声明 | 现状 | 改为 |
|------|------|------|
| `.risk-dialog-breakdown__item { grid-template-columns: … }` | `80px 72px 48px 1fr` | `88px 72px 48px 1fr` |

`88px` 给六字名留 10px 余量，是 4 的倍数，且从 `1fr` 借走 8px（约 306px → 298px）不改变
explanation 的换行行为（实测最长的既有句子在 306px 下已折 2 行）。

**不采用 `white-space: nowrap`：** 它会把未来的更长名字直接顶出单元格造成横向溢出
（`.el-dialog` 没有 `max-width`，`width="600px"` 是硬宽度）。**规则：** 若注册表将来出现
超过 6 字的指标名，必须重新量列宽，而**不得**截断或缩小字号。

**不得**同时改 `72px`（状态 pill）与 `48px`（分数）列：pill 内容最长 `按默认值`（4 字 × 11px
mono ≈ 44px + `padding: 1px 6px` = 56px）已贴边但未溢出，本阶段不动它。

---

## Component Contract 5 — `riskNote` 的文案格式（含方向）

### 5.1 为什么需要格式约束

D-05 要求 `riskNote` 与引擎同源派生。前端这一侧的契约是：**该字段必须是一句能塞进一列的话，
并且对「越低越危险」的指标不能说「超过」。**

血脂四项里 `hdlCholesterol` 是**下限型**（越高越好，参考 `≥ 1.00 mmol/L`），其余三项是
上限型。当前 `RISK_CONSUMERS` 的三条**全部**是「超过 …」句式（`MetricCoverageAssembler.java:43-45`）。
若 D-03 的新 `RiskThresholdRegistry` 沿用这个句式为 HDL 生成 `超过 1.0 mmol/L 计入 N 分`，
面板会对一个 **1.0 的人（偏低）** 说「超过」，用户读到的是与事实相反的句子。

### 5.2 格式规格（唯一允许的形状）

```
{疾病}评估：{方向动词} {阈值}{单位} 计入 {分数} 分
```

| 组成 | 取值 |
|------|------|
| `{疾病}` | `糖尿病` / `高血压` / `心血管` |
| `{方向动词}` | 上限型 → `超过`；下限型 → `低于`；区间外 → `超出` |
| `{阈值}` | 与 `threshold` 字段同值（如 `5.2`、`1.0`） |
| `{单位}` | 该指标的 `unit`（如 `mmol/L`、`mmHg`、`kg/m²`） |
| `{分数}` | 整数 |

示例（首个为新增，其余为既有格式）：

```
心血管评估：超过 5.2 mmol/L 计入 20 分
心血管评估：低于 1.0 mmol/L 计入 15 分      ← HDL，方向与上面相反
高血压评估：超过 130 mmHg 计入 35 分
糖尿病评估：超过 6.1 mmol/L 计入 30 分
```

**长度上限 24 字。计字规则（必须按此口径实现，否则上限不可测）：去掉空白后按码点计长。**

```js
// JS —— 与下文五例自洽
const n = r.replace(/\s/g, '').length;
```

```java
// Java —— 必须先赋值给变量再取长。把两段写在一行里是错的：
//   r.replaceAll("\\s", "").codePointCount(0, r.length())   ← r.length() 是未去空白的长度
// 五个示例的原文都比去空白后长（27>22、27>22、25>20、27>22、27>22），
// 所以上式的 endIndex 恒大于实际串长，必然抛 StringIndexOutOfBoundsException。
String s = r.replaceAll("\\s", "");
int n = s.codePointCount(0, s.length());   // 正确：长度取自同一个已去空白的字符串
```

上例四条实测 **22 / 22 / 20 / 22**，5.3 的 BMI 示例实测 **22** —— 全部在限内，留 2–4 字余量。
（这四条若按**原文字符**数计则是 27 / 27 / 25 / 27，全部「超标」；差别正是去空白那 5 个字符。）

（不按「字」的直觉数中文个数，也不按 UTF-8 字节数 —— 前者会把 `mmol/L` 算成 1 字而
放行超长句，后者会把每个汉字算 3 造成全线超标。码点去空白是唯一与这五个示例自洽的口径。）

**不得**包含「可能」「建议」「请」，该列只陈述评分规则，不确定性的表达属于
`assumptions` 与弹窗警示句。

**该形状与上限是本契约中唯一不带例外条款的文案规则。** Typography 与 Spacing 各自登记了
自己的例外值；Copywriting 的 `riskNote` 不登记 —— 任何看似需要「加一句说明」的情况，
按 5.3 的处理方式解决（见该节），**不得**在本字段内追加括号、来源、限定词或第二个疾病名。
理由：这一列宽度从 272px（1024px 视口）到 688px（1440px 视口）不等（Contract 2），
24 字上限（≈288px 上限估算）保证**最坏情况也只有 2 行**；初版的前缀版 30 字与后缀版 39 字
在 1024px 视口会变成 3 行以上，8 条里只要折 2 条，看上去就像「这两项更重要」——
与事实无关的视觉权重。

**`riskNote` 不承载来源/出处信息。** 它是一个「评分规则」字段，回答「引擎拿这项怎么算分」，
不回答「这个值从哪来」。出处的唯一既有表面是 `.risk-estimate__source-origin`
（`Risk.vue:46`、`:1123`，渲染 `健康档案` / `指标录入` / `档案缺失`）。在两个表面重复
表达出处，会制造两个需要同步维护的出处声明 —— 正是 D-05 要消灭的那类双写。

**一个指标被多个疾病读取时，`riskNote` 只描述第一条（`RISK_CONSUMERS` 的既有单值语义），
不得用 `、` 拼接两个疾病** —— 那会立刻超过 24 字并在表格里折成三行。
若将来确需表达多处参与，应作为 Phase 18+ 的字段扩展，不在本阶段。

### 5.3 BMI 的来源与精度必须同源（D-05 的直接后果）

D-01 让 BMI 成为心血管的独立输入，但 **BMI 有两条数据来源**：`health_metric` 表的
`bmi` 行，与 `health_profile.bmi` 字段。引擎读的是后者（`RiskAssessmentServiceImpl.java:326-327`），
而 `MetricCoverageAssembler` 读的是前者（`latestByCanonicalKey`）。

因此一个「档案里填了 BMI、但没录过 bmi 指标」的用户（CONTEXT 实测：`bmi` 档案 3/3 有、
指标行 0 条）会看到：

| 表面 | 显示 | 与事实一致？ |
|---|---|---|
| 覆盖面板 `bmi` 行 | `未测`（因为 metric 表无行） | ❌ 引擎其实用了档案里的值 |
| 弹窗归因 | `BMI 23.5`，`CONTRIBUTED` / `NORMAL` | ✅（引擎读的是档案值） |
| 评估依据面板（`risk-estimate`） | `BMI 23.5`，origin `健康档案` | ✅（既有行为，`:388`） |

这就是 D-05 说的「面板开始说谎」的具体形态：**三个表面里，只有 S1 说了谎。**

#### 契约：在值/状态层同源，不改 `riskNote` 的一个字

**唯一要求的实现：** `MetricCoverageAssembler` 对**任何同时存在于 `health_profile` 与
`health_metric` 的因子**（当前仅 `bmi`），其覆盖行必须**只由引擎实际读取的那一源派生**：

| 引擎读什么 | 面板行必须显示 | 面板行 `state` |
|---|---|---|
| 档案有值 | 该值，经 §5.3.1 的**共享 formatter** 按注册表精度格式化（`bmi` precision=1：`22.86` → `22.9`） | 由该值判定，**不会是 `MISSING`** |
| 档案无值 | — | `MISSING`（与引擎的 `ASSUMED` 兜底分别记录在 `assumptions` 里，不在 S1 伪造状态） |

**本契约只约束两件事：`MISSING` 与否，以及 `value` 等于什么。** 它不改 `偏高/偏低/正常` 的
判定来源 —— 那是 D-03（**锁定决策**）的范围：风险阈值来自 `RiskThresholdRegistry`，引擎与覆盖
面板同读一处。本契约不在这里重述或改写 D-03 的阈值口径，只要求：同一因子在 S1 与引擎里
**不得一个说 `MISSING`、一个说有值**，且**有值时两处的 `value` 在契约精度上相同**（§5.3.1：
两侧共用同一 formatter）。
A(i) 只比较 `value`、不比较 HIGH/LOW 取值，因此 D-03 的阈值落地细节不会让断言 A 失真。

> 一处待随 D-03 复核的文案耦合（不在本阶段改动范围内）：摘要第二段写的是
> `{abnormal} 项超出参考范围`，而 D-03 要求面板按风险阈值分类。两者同批落地时，这句话的措辞
> 需一并确认；本契约不单方面改动它，也不冻结 `classify()` 的实现。

**关键：`health_metric` 里的同名行不得覆盖档案。** 引擎读的是 `profile.getBmi()`
（`RiskAssessmentServiceImpl.java:326-327`），它根本不看 `health_metric.bmi`。因此当档案无值、
而指标表有一行 `bmi`（例如 31）时，面板若读指标行就会显示 `31 / 偏高`，而引擎实际用的是
`22.0（估算）` —— 这正是 D-05 要消灭的「面板说谎」，只是换了个方向。
**规则：** 对双源因子，面板的值与状态一律取引擎源；指标表那一行在引擎不读它时**不参与**该行的
显示。这条规则是 `MetricCoverageAssembler` 里的一个判断，**不得**下放到前端补。

> 这条不是对 D-05 的扩展解读，而是它的字面要求：D-05 说「覆盖面板的 `riskNote` 必须与引擎
> 实际读取的指标**同源**」。值同样如此 —— 同一个字段的措辞与数值若来自两个源，面板就还是会说谎。

#### 5.3.1 两个表面必须共用同一个 formatter（本修订采用 route (b)）

**问题不是「断言写法」，是产品缺陷。** 面板按注册表精度渲染，引擎按 `stripTrailingZeros()`
渲染，于是**同一个数字在两个表面显示成两个样子**：

| 表面 | 代码 | `bmi = 22.86` 渲染为 |
|---|---|---|
| S1 覆盖面板 | `MetricCoverageAssembler.formatValue` → `setScale(precision, HALF_UP)`，`bmi` precision = **1**（`HealthMetricMetaRegistry.java:187`） | `22.9` |
| S2 弹窗归因 | `RiskAssessmentServiceImpl.java:328` → `stripTrailingZeros().toPlainString()` | `22.86` |

实测（2026-11-18，`health_profile` 全三行，`bmi` 由 `HealthProfileServiceImpl.java:121` 以
`divide(..., 2, HALF_UP)` 写入）：

| id | height | weight | `bmi` 入库值 | 面板（precision 1） | 引擎 | 字符串相等？ |
|---|---|---|---|---|---|---|
| 1 | 175.00 | 70.00 | 22.86 | `22.9` | `22.86` | ✗ |
| 2 | 181.00 | 66.00 | 20.15 | `20.2` | `20.15` | ✗ |
| 3 | 181.00 | 66.00 | 20.15 | `20.2` | `20.15` | ✗ |

**三行全部不一致。** 这是精度分歧的普遍形态，不是 BMI 特例 —— `health_metric.value` 是
`DECIMAL(10,2)`（`schema.sql:94`），因此任何 precision=1 的指标只要存到第 2 位小数就会分裂
（构造值 `bloodGlucose = 5.55` → 面板 `5.6`、引擎 `5.55`）。
`weight`（面板 `70.0`）今天**不在引擎输入里**（引擎不调 `getLatestMetric("weight")`），故不在
A(i) 的范围内；若将来纳入评估，按 §5.3.1 的同一规则处理。

**采纳的实现（route (b)）：引擎的 `value` 与面板走同一个 formatter，按注册表精度输出。**
调用点已存在，无需新建类：

| 项 | 规格 |
|---|---|
| formatter | `HealthMetricMetaRegistry.getDefinition(key)` 取 `MetricDefinition.precision()`，再 `value.setScale(precision, RoundingMode.HALF_UP).toPlainString()` —— 与 `MetricCoverageAssembler.formatValue`（`:137-140`）**同一算式** |
| 归属 | 抽成 `MetricCoverageAssembler` 的包级静态方法（或 `MetricDefinition` 上的实例方法）供 `RiskRuleEngine` 调用；两份实现**必须只有一处**，因为 A(i) 的全部价值就建立在「两侧来自同一段代码」上 |
| 调用范围 | `RiskAssessmentServiceImpl` 中所有写进 `contributor.value` 的数值：`bmi`（`:328`）、`systolicBP`（`:406`）、`diastolicBP`（`:411`）、`bloodGlucose`（`:386`），以及 D-03 落地后新增的血脂四项 |
| 各指标精度 | 取自注册表，**不得**在此硬编码：`systolicBP` 0、`diastolicBP` 0、`bloodGlucose` 1、`bmi` 1、`totalCholesterol` 2、`triglycerides` 2、`hdlCholesterol` 2、`ldlCholesterol` 2（`HealthMetricMetaRegistry.java:61 / :79 / :97 / :187 / :223 / :241 / :259 / :277`） |
| 单位后缀 | 保持不变，仍在 formatter 之外拼接（`" mmHg"`、`" mmol/L"`）。formatter 只负责数值部分 |
| `threshold` 字段 | **不在本次范围**。阈值是常量（`"130 mmHg"`、`"24.0"`），两侧本就同源，不改。**一处留待 D-03 附带的格式对齐（不影响 A(i)）**：血脂四项 precision = 2，若 D-03 的风险阈值写成 `"5.2 mmol/L"` 而同一句里的取值是 `"4.50 mmol/L"`，一句话内会出现两种小数位。`RiskThresholdRegistry` 落地时应让阈值按该指标的 registry precision 渲染（`"5.20"`），§5.2 的 `{阈值}` 随 `threshold` 字段同值即可（`心血管评估：超过 5.20 mmol/L 计入 20 分` 去空白 23 字，仍在 24 上限内）。A(i) 不比较 `threshold`，故本条不改变断言的通过条件 |

**单位后缀造成的第二处不对称（必须声明，否则 A(i) 会被推广成必然失败的形状）：**
S1 覆盖面板**没有单位列**（模板 `:112` 只渲染 `m.value`，`grep -n "unit" Risk.vue` → 0 条），
而引擎的血压/血糖 `value` 带单位（`"120 mmHg"`、`"5.6 mmol/L"`）。因此：

| 指标 | 面板 `value` | 引擎 `value` | 可比较的部分 |
|---|---|---|---|
| `bmi` | `22.9` / `31.0` | `22.9` / `31.0` | **整串**（两侧都无单位）→ A(i) 用字符串相等 |
| `systolicBP` / `diastolicBP` / `bloodGlucose` / 血脂四项 | `120` / `5.6` / `4.50` | `120 mmHg` / `5.6 mmol/L` / `4.50 mmol/L` | **数值前缀**，单位只在 S2 出现 |

**A(i) 因此只对 `bmi` 定义整串相等**（`bmi` 两侧都不带单位，这正是它能用最严格比较的原因）。
其余指标的单位不对称**不在本阶段消除** —— 给面板加单位列会撑破 Contract 2 的五列栅格，属于几何
变更，超出 scope fence。这条只要求「数值部分必须由同一个 formatter 产出」，不要求两处字符串整体相等。
（`weight` 的 `70.0` vs `70` 同属这一格式分歧，但引擎今天不读 `weight`，故不在 A(i) 的范围内；
若将来纳入评估，按上表同一规则处理。）

**`（估算）` 后缀的去留（必须明确，否则 route (b) 会顺手把它格式化掉）：**

| 值 | 现状 | route (b) 之后 |
|---|---|---|
| 档案有 BMI = 22.86 | `22.86` | `22.9`（precision 1） |
| 档案无 BMI（`ASSUMED`） | `22.0（估算）` | **`22.0（估算）` 不变** —— 这是字面量兜底，不是从 `BigDecimal` 格式化来的，所以 formatter 不参与；`22.0` 恰好已是 precision 1 的形状，与其后 `（估算）` 连写不产生新的不一致 |
| 年龄 | `35 岁（估算）` / `52 岁` | 不变（年龄不是注册表指标，无 precision） |

**A(i) 在这种实现下的形状：** 两侧都是同一 formatter 的输出，因此**字符串相等即可**，
不再需要数值归一：

```
assert panelRow.value === contributorById('bmi').value   // 两侧都是 precision-1 字符串
```

`（估算）` 分支仍由 A(ii) 承担（面板在该分支是 `MISSING`，A(i) 不触发），所以这个直接比较
不会撞上后缀。

**A(i) 只对「本次改动之后创建的评估」成立（D-06 的必然结果，必须写明）：**
归因快照在创建时冻结、读时不重算（D-06）。**改动前已入库的历史记录里 `value` 仍是 `22.86`**，
而覆盖面板是实时按 precision 1 渲成 `22.9` —— 拿历史记录跑 A(i) 会失败，而实现是完全正确的。
因此断言必须这样限定：

| 场景 | A(i) 是否适用 |
|---|---|
| 本次改动后点击「创建评估」产生的新记录 | **适用**，字符串必须相等 |
| 改动前已存在的历史记录（快照 `value` = `22.86`） | **不适用**，跳过。这是 D-06 要求的行为，不是缺陷 |
| 无 `contributors` 的存量记录（`contributors` 为空，Phase 15 已定不渲染明细） | 不适用（A(iii) 的相邻分支，弹窗无 `bmi` 行） |

测试里的取数口径：**先跑一次创建评估，再对该次返回值断言**，不要读库里已有的批次。

**由此留下的一处已知残留（如实登记，不掩盖）：** 改动前入库的三条历史记录，其快照 `value` 是
`22.86` / `20.15`，而覆盖面板始终实时渲成 `22.9` / `20.2`。用户点开一条旧记录仍会看到这个差异。
**本阶段不回溯改写快照** —— D-06 明令「历史记录必须是当时的真实依据」，回去改数字违反它，且
会掩盖「当时的引擎输出是什么」。这条差异随新记录自然消失。记入 Deferred。

**为什么不采用 route (a)（只改断言）：** 若只在测试里做 `toFixed`/容差归一，A(i) 会通过，
但产品仍然把 `22.9` 和 `22.86` 同时呈现给用户。更糟的是容差不单调 —— 实测
`Math.abs(22.9 - 22.86) = 0.0399`（通过）而 `Math.abs(20.2 - 20.15) = 0.0500000000000007`
（**失败**），于是同样合法的两行数据一个过一个不过，断言本身变成随浮点尾差摇摆的不稳定门禁；
`(20.15).toFixed(1)` 在 IEEE-754 下还是 `20.1`（不是面板的 `20.2`），因为面板是
`BigDecimal` HALF_UP 而上侧是二进制浮点。**这就是为什么必须让两侧共用 formatter，而不是在
断言里模拟它。** route (a) 会把一个实现缺陷固化成契约认可的形态。

`riskNote` 保持 5.2 的形状，**不加前缀、不加后缀**：

```
心血管评估：超过 24.0 kg/m² 计入 20 分
```

#### 为什么不在 `riskNote` 上写来源（本修订明确否决了先前的两种写法）

初版给了两条实现路径 —— 前缀 `心血管评估（取自健康档案）：…` 或后缀
`（评估实际使用健康档案中的 BMI）`。两者都与 5.2 直接冲突：按 5.2 的计字规则实测，
前缀版 **30 字**（超 6），后缀版 **39 字**（超 15），而初版没有给 5.2 加例外条款，
于是「照 5.3 做」必然「违反 5.2」。本修订的处置是**取消这两条路径**，而不是给 5.2 开口子：

| 路径 | 判定 |
|------|------|
| **值/状态层同源，`riskNote` 不动（采纳）** | ✅ D-05 的缺陷是「面板说未测、引擎却说用了」。把值填上、状态算对，这个缺陷就没了。来源披露不由 `riskNote` 承担 —— 它在用户真正需要知道的时刻（创建评估前）由 `risk-estimate` 的 origin 列给出：`BMI 23.5` / `健康档案`（`Risk.vue:388`），那是既有且正确的表面 |
| 前缀 `心血管评估（取自健康档案）：…` | ❌ 30 字，超上限 6 字；且会让 `bmi` 一行与其余 7 行的 note 在视觉上不同类，读成「这一项特殊」 |
| 后缀 `（评估实际使用健康档案中的 BMI）` | ❌ 39 字，超上限 15 字；即便另起一段也会让该行 2 行，与 5.2 的「至多 2 行」在 1024px 下叠加成 3 行 |
| 把来源信息压到「档案」两个字以塞进 24 字 | ❌ **明令禁止。** 这正是 checker 指出的错误解法：为了迁就列宽而削弱来源披露，等于让 D-05 想防的「面板不说实话」以更隐蔽的形式回来 |

#### 判定标准（执行者必须实跑，两条都要）

**断言 A —— 方向性，按面板行的状态分三支。** 不是对称的「两边一致」，因为下面这件事是
**契约要求**的、不是缺陷：

| 分支 | 条件 | 必须成立 |
|---|---|---|
| A(i) | 面板 `bmi` 行 `state !== 'MISSING'` | 面板该行的 `value` 与弹窗中 `key === 'bmi'` 归因的 `value` **字符串相等**（两侧都经 §5.3.1 的同一 formatter、同一注册表精度） |
| A(ii) | 面板 `bmi` 行 `state === 'MISSING'` 且弹窗**有** `key === 'bmi'` 的归因 | 该归因 `state === 'ASSUMED'`，且 `assumptions` 含 `缺少 BMI，按 22.0 估算`；**不得**为 `CONTRIBUTED` / `NORMAL` / `MISSING` |
| A(iii) | 面板 `bmi` 行 `state === 'MISSING'` 且弹窗**没有** `key === 'bmi'` 的归因 | 成立 —— 这是「连档案都没有」的用户（引擎在 `:299-307` 提前返回，只发一条 `profile` 归因）。**不构成违反**：引擎没读 BMI，面板说 `未测` 是真话 |

**为什么必须分方向、不能写成对称比较：** §5.3 上方表格已把「档案无值 → 面板 `MISSING`，
引擎 `ASSUMED`」定为**正确形态**。若断言写成「两侧 `state !== 'MISSING'` 必须相同」，它在**合规实现上会失败** ——
对一个档案无 BMI 的用户（含 Contract 1.1 里明列在范围内的零指标新用户），面板按契约是
`MISSING`、引擎按 `:326-328` 取默认值、在 `:362-364` 赋 `STATE_ASSUMED`，两个布尔值按设计不同。
断言必须描述这个方向，而不是抹平它。

**A(i) 的比较口径（精度归一后的字符串相等，不是原始数值相等）：** 参 §5.3.1 ——
两侧都调用同一个 formatter，结果都是注册表精度下的字符串，因此**在契约声明的精度上**相等。
这既不是逐字符比原始值（那会让 `22.9` vs `22.86` 误报），也不是浮点容差（那不单调，见 §5.3.1）。
`bmi` 的契约精度是 **1 位小数**，三个现有档案因此分别渲染为 `22.9` / `20.2` / `20.2`，
两侧一致。

**A(i) 的作用（这是它有牙齿的地方）：** 档案无 BMI 但指标表有一行 `bmi`（如 31）时，今天的面板
读指标行显示 `31.0 / 偏高`，引擎读档案显示 `22.0（估算）` —— 两侧都 `!== 'MISSING'`，
**对称布尔断言会放过它**，而 A(i) 的字符串比较 **`31.0` ≠ `22.0（估算）`，失败**。同理，若有人把面板改回
「指标表优先」，A(i) 也会在下一次运行时失败；若有人把引擎的 formatter 摘掉、退回
`stripTrailingZeros()`，A(i) 同样失败（`22.86` ≠ `22.9`）—— 后一条正是本修订要守住的。

**A(ii) 的作用（今天最普遍的那一种）：** 档案有 BMI（`bmi` 3/3 有）、指标行 0 条的现有用户 ——
面板今天报 `MISSING`，弹窗报 `CONTRIBUTED`。A(ii) 的条件被触发（面板是 `MISSING` 且弹窗有 `bmi`），
而它要求弹窗为 `ASSUMED`，实际是 `CONTRIBUTED` → **失败**。这与 §5.3 上方表格标注的
「三个表面里只有 S1 说了谎」是同一件事，由 A(ii) 直接捕获。

**为什么需要 A(iii)：** 没有档案的用户在引擎里是**提前返回**的（`:299-307`，只发一条
`RiskContributorVO.missing("profile", "健康档案")`），因此弹窗里根本没有 `bmi` 这一行。
若 A(ii) 只写「`MISSING` 时必须 `ASSUMED`」而不加「且弹窗有 `bmi`」这个前提，它在**这类用户上会
无条件失败**，而实现是完全正确的。A(iii) 把这个分支显式命名为通过，而不是靠断言写得含糊来绕过。

**断言 B —— 格式合规（5.2 的本体）：** 遍历 `coverage` 中所有 `riskNote`，断言每条都匹配
`^[^：]+评估：(超过|低于|超出) .+ 计入 \d+ 分$` 且长度 ≤ 24。

两条断言互不干扰：A 只看 `state` 与 `value`，B 只看 `riskNote` 字符串。**这就是本契约
能同时满足两条强制规则的原因** —— 它们作用在字段的不同部分上。

**A 与 B 之外，来源禁令由散文承担，不由 B 的格式守卫承担。** 实测：`心血管评估：超过 24（档）kg/m² 计入 20 分`
匹配 B 的正则且去空白后 23 字（≤24），**能通过 B** —— 它并不含 `档案`/`来源` 字样，只是多了一个
括号注。B 的职责仅仅是「形状 + 长度」。来源禁令的强制力来自 §5.2 的散文
（`riskNote` 不承载来源/出处信息）与 Copywriting 表对 `riskNote` 的「不含来源标注」注释。
若要把它变成可执行断言，须另加一条否定断言（见 A-4）。

> 这是后端改动（`MetricCoverageAssembler`），但它的验收面在前端两个面板的一致性上，
> 因此写进本契约。执行者不得只改后端而不同时核对两个面板的渲染结果。

> **其余口径不变：** 若将来某个因子在档案与指标表**都有**值，以引擎实际读取的那一源为准
> （引擎读档案则面板读档案），仍不改 `riskNote` 文案。新增双源因子时必须在
> `MetricCoverageAssembler` 的同一处登记，不得在前端补判断。

---

## Component Contract 6 — 两套状态词汇必须各自独立且完整

### 6.1 词汇表（锁定，不得合并、不得新增第三种）

| 词汇组 | 出处 | 四个值 → 用户可见标签 | 渲染位置 |
|--------|------|---------------------|---------|
| 归因态 | `RiskContributorVO.state` | `CONTRIBUTED`→已计入 · `NORMAL`→正常 · `ASSUMED`→按默认值 · `MISSING`→缺数据 | S2 弹窗 pill |
| 覆盖态 | `MetricCoverageVO.state` | `HIGH`→偏高 · `LOW`→偏低 · `NORMAL`→正常 · `MISSING`→未测 | S1 面板 pill |

`stateLabel()`（:829-837）与 `coverageStateLabel()`（:864-872）是两套独立映射，**都必须保留**。

### 6.2 三条禁止

1. **不得把两套合并成一个 `stateLabel`。** `MISSING` 在两处刻意用了不同的词
   （`缺数据` vs `未测`）：前者描述「这项没参与计分」，后者描述「你没测这个指标」。
   合并会让「未测」出现在归因里，读成「引擎没测」，与事实不符。
2. **不得引入第三种状态词汇。** 后端若新增状态（如 `OUT_OF_RANGE`），前端必须把它映射进
   上述两套之一的既有标签，或走 `?? state` 兜底并在 UAT 中记录 —— **不得**新造第五个中文词。
3. **不得用 `el-tag` 承载状态 pill。** `theme.css:542-549` 给 `.el-tag` 加了 `!important`
   的 `font: 500 11px/1.4 var(--font-mono)` 与 `--radius-full`，与 pill 的 `4px` 圆角冲突；
   且 `el-tag` 已被风险等级（低/中/高风险）占用，复用会让「状态」与「等级」看起来同类。

### 6.3 `ASSUMED` 在 D-02 之后的边界

D-02 明确血脂缺失时**绝不代入默认值**。因此本阶段之后：

| 因子 | 缺失时的状态 | 依据 |
|------|-------------|------|
| 血脂四项 | `MISSING`（不是 `ASSUMED`） | D-02 |
| 年龄 / BMI | `ASSUMED`（35 岁 / 22.0） | 既有档案兜底 |
| 家族史 | `MISSING` | 既有（`RiskContributorVO.missing`） |

**这张表描述的是 S2（归因态）。** S1 对同一个 BMI 因子在档案无值时报 `MISSING`（§5.3）——
两者**按设计不同**，因为它们回答不同的问题：S2 说「引擎用了估算值」，S1 说「你没有这个数据」。
这不是矛盾，是 §6.1 那两套词汇存在的理由；断言 A 的三支正是照这个方向写的，不得改为对称比较。

**断言（测试必须有牙齿）：** 构造一个血脂四项全缺的用户，断言其心血管归因中 `key` ∈
`{totalCholesterol, triglycerides, hdlCholesterol, ldlCholesterol}` 的每一条 `state === 'MISSING'`，
且**没有任何一条** `state === 'ASSUMED'`。若实现退回默认值，这条断言会失败。

---

## Component Contract 7 — 「来自 X」前缀消失后的行措辞

### 7.1 变化事实

今天心血管的每一条归因都是 `halve()` 的产物，其 `explanation` 形如
`来自糖尿病：年龄 52 岁，超过 45 岁，计入 20 分（折半计入 10 分）`（:455-461）。
`label` 字段本身**不含**前缀，前缀只在 `explanation` 里。

D-01 之后 `halve()` 被删除，心血管的每条 `explanation` 变成自己的一句话：
`BMI 23.5，未超过 24.0，未加分`。

### 7.2 契约

| 项 | 规则 |
|---|---|
| `explanation` | 必须是一句自足的、以指标名或「年龄」开头的完整句。**不得**残留 `来自`、`折半`、`（折半计入 N 分）` —— 它们是复合评分时代的产物，留着会让用户去找一个已经不存在的父级疾病分 |
| `label` | 保持指标短名（`收缩压`/`总胆固醇`），不得写成 `高血压-收缩压` 这类带来源的组合名 —— 88px 列放不下，且两个面板的 label 将不再一致 |
| 封顶折算 | 若心血管分超过 100 而需按比例折算，仍走既有 `scaleTo()`（:439-441），其 explanation 必须保留「因总分上限 100，按比例折算」这层意思。**不得**用「折半」措辞 |
| 各项之和 | 弹窗顶部「各项合计 {{ contributorSum }} 分 = 风险评分」必须继续成立（:286-288）。这是验收标准 5 的前端观测点 |

**不新增任何 UI 元素来表达「这是心血管自己的依据」。** 前缀消失本身就是信号：
一行不再说「来自糖尿病」，就说明它属于本疾病。契约**明令禁止**为了强调独立性而
在弹窗里加标题、徽章或分组线 —— 那超出 scope fence，且会破坏「所有疾病用同一套归因表」的一致性。

### 7.3 空态与无明细

`contributors` 为空（存量记录）时，模板 :283 的 `v-if="currentAssessment?.contributors?.length"`
使整个构成区块不渲染 —— 这是既有且正确的行为（Phase 15 测试 `无归因时不渲染评分构成` 已锁定）。
本阶段**不得**改为渲染一个空列表或占位说明。

---

## Copywriting Contract

### 通用纪律（沿用 Phase 11–15）

1. 禁止词：`操作失败`、`请求失败`、`系统错误`、`出错了`、`Error`、`Failed`、`null`、`undefined`，
   以及任何原始后端字段名。
2. 状态标签只能用 Contract 6.1 的八个中文词，一个不多一个不少。
3. 空态必须给出「下一步」，而不是只陈述没有东西。

### 本阶段新增 / 变更的文案

| Element | Copy | 备注 |
|---------|------|------|
| 主 CTA | `创建评估` | **不变**（阶段内唯一的主按钮） |
| 面板标题 | `我的指标覆盖情况` | 不变 |
| 摘要段 1 | `已测 {measured} / {total} 项` | 不变 |
| 摘要段 2 | `{abnormal} 项超出参考范围` | 不变（仅 `abnormal > 0` 时） |
| 摘要段 3 | **`{used} 项纳入评估范围`** | 由「参与风险评估」改为「纳入评估范围」（Contract 1.3） |
| 摘要段 3 补充 | **`（其中 {usedMissing} 项未测）`** | 仅 `usedMissing > 0` 时；`usedMissing` = 有 `riskNote` 且 `MISSING` 的项数。**用 S1 自己的词 `未测`**，不用归因态的 `缺数据`（Contract 1.3） |
| 分组标题 1 | `参与风险评估的指标` | 不变 |
| 分组标题 2 | `其他已测指标` + `（有数据，但不参与本次风险评估）` | 不变 |
| 分组标题 3 | `尚未采集` + `（{n} 项；这些不是「正常」，是「不知道」）` | 不变；`{n}` 因 Contract 1 而变小 |
| 空态 · 其他已测 | **`暂无其他已测指标。录入指标后，未被风险评估使用的项目会显示在这里。`** | **改**：原句 `暂无其他已测指标` 只陈述缺席，不满足纪律 3。新句给下一步（去录入指标）并预告这一组会出现什么。**不新增按钮、不新增链接**：指标录入在既有导航（`/prevention/metrics`、`/health/metrics`）可达，本面板不加跳转（见 Deferred「血脂四项的补录入口」） |
| 空态 · 无任何批次 | `暂无风险评估记录` | **不变**。下一步不在句子里，而在同卡常驻的 `创建评估` 主按钮（卡片头部，`Risk.vue:7`）；它是阶段内唯一主 CTA，且始终可见，把「点它」写进空态句会重复同一个动作提示 |
| 覆盖态 4 词 | `偏高` / `偏低` / `正常` / `未测` | 锁定 |
| 归因态 4 词 | `已计入` / `正常` / `按默认值` / `缺数据` | 锁定。**`已计入`、`按默认值`、`缺数据` 三词只允许出现在 S2 弹窗内**；S1 用覆盖态的 `偏高`/`偏低`/`未测`（`正常` 是两套共有，无需区分） |
| 弹窗构成标题 | `评分构成` + `各项合计 {sum} 分 = 风险评分` | 不变 |
| 弹窗缺失警示 | `有 {n} 项缺少数据，本结果未包含这些因素 —— 补齐后评分可能上升，请勿把「未计入」当作「正常」。` | 不变；`{n}` 本阶段典型值为 4。**属于 S2，用归因态的 `缺少数据` 是对的** |
| 弹窗假设区标题 | `本次评估采用的假设` | 不变 |
| `riskNote` | `{疾病}评估：{方向动词} {阈值}{单位} 计入 {分数} 分` | Contract 5.2；≤24 字；**无例外，不含来源标注**（Contract 5.3 已把来源需求移出本字段）。来源禁令的可执行守卫是 A-4 的 ⑥（否定断言），**不是** ⑤ 的格式正则 —— 见 A-4 的覆盖边界行 |
| `explanation`（缺失） | `缺少{label}数据，此项未计入评分` | 由 `RiskContributorVO.missing()` 生成，不变 |
| `explanation`（归因） | 见 Contract 7.2；**禁止**出现 `来自` / `折半` | 本阶段变更 |
| 错误态 | 沿用 `showError(error, 'profile.loadFailed', { presentStates: true })`（:728）与 `'profile.saveFailed'`（:757） | 不新增键 |

### 错误态与空态的边界（沿用 Phase 11 §3.4 四态互斥）

| 状态 | 载体 | 本阶段是否变化 |
|---|---|---|
| 加载成功但 0 条批次 | `.risk-batches__empty` 文案 | 否 |
| 覆盖面板加载失败 | 静默（`catch { coverage.value = [] }`，:883）→ 见 Deferred | **不在本阶段修**，但必须记录 |
| 批次加载失败 | `showError(..., { presentStates: true })` 一条 toast，列表保留原有行 | 否 |

---

## UI Considerations

> 形态驱动的 UI **状态**覆盖。文案见 `## Copywriting Contract`，本节不重复。

Applicable state considerations resolved: **19 covered (15 explicit, 4 backstop, 0 unresolved)**
（初版记为 17 / 13 / 4，与下表实际行数不符；本修订按两表合计重算。）

表面编号：**U1** 覆盖面板（`.risk-coverage`）· **U2** 评分构成弹窗（`.risk-dialog-breakdown`）·
**U3** 状态 pill（两套词汇共用形态）· **U4** 归因列表单元（行容器）

### 空 / 加载 / 错误

| Category | Element(s) | Status | Resolution / Reason |
|----------|------------|--------|---------------------|
| empty | U1 list-collection | ✅ covered | 三个分组各自可空：分组 1/3 用 `v-if` 整组不渲染，分组 2 空时渲染 `暂无其他已测指标。录入指标后，未被风险评估使用的项目会显示在这里。`（含下一步）。**不得**改为 `el-empty`（会引入第三种空态形态） |
| empty | U2 list-collection | ✅ covered | `contributors` 为空时整块不渲染（`v-if` :283）。存量记录因此不显示明细，与 Phase 15 测试一致 |
| empty | U1 零指标新用户 | ✅ covered | 全部 23 项落在分组 1（有 note，8 项 `未测`）+ 分组 3（无 note，15 项）。Contract 1 修正后零重复 |
| loading | U1 | ✅ covered | `coverageLoading` → `el-skeleton :rows="2"`（:94-96）。不叠加第二种指示 |
| loading | U2 | ✅ covered | 弹窗数据来自已载入的 `assessments`，无独立加载态；打开即渲染 |
| error | U1 | 🧪 backstop | 失败路径静默清空（:883）。修正后语义应为「空」而非「假」。本阶段**不加**错误态（见 Deferred），但 UAT 必须实跑一次「接口 500 时面板是否会让用户误以为没有指标」，并记录观察结果 |
| error | U2 | ✅ covered | 无独立请求，复用列表的失败处理 |

### 部分 / 零一多 / 溢出 / 长文本

| Category | Element(s) | Status | Resolution / Reason |
|----------|------------|--------|---------------------|
| partial | U1 list-collection | ✅ covered | **本阶段的核心状态。** 部分指标有值、部分缺失：分组 1 内混排 `偏高/正常/未测`，分组 3 集中呈现「没有且不参与」。Contract 1 保证一个指标只出现一次 |
| partial | U2 list-collection | ✅ covered | 同一次评估内 `CONTRIBUTED` / `NORMAL` / `ASSUMED` / `MISSING` 四态混排，底部警示句给出缺失计数。**合法的一对跨表面状态：** U1 的 `bmi` 行 `MISSING` 与 U2 的 `bmi` 归因 `ASSUMED`（档案无值，§5.3 / §6.3）—— 按设计不同，不是不一致 |
| partial | U2 四行连续 MISSING | ✅ covered | 逐行保留（Contract 3.2），靠 1px `--border` 分隔 + 灰色 pill + 斜体句三重区分。分隔线修复是本阶段的必要条件 |
| zero-one-many | U2 归因行数 | ✅ covered | 0 → 不渲染；5（糖尿病典型）→ 单屏可见；9（心血管 D-01 后）→ 弹窗内容超出视口时由 `.el-overlay { overflow: auto }` 滚动承担（实测 `el-overlay.css` 已声明），弹窗自身不改高度 |
| zero-one-many | U3 pill | ✅ covered | 每个指标恰一个 pill，不出现 0 个或多个 |
| zero-one-many | U1 摘要数字 | ✅ covered | `usedMissing` 为 0 时括号段不渲染，不出现「（其中 0 项未测）」 |
| overflow | U1 `riskNote` 列 | ✅ covered | `1fr` = 容器宽 − 512px（1120px 口径 ≈ 608px）、单条上限 24 字（≈ 288px），至多 2 行；超出时自然换行，不截断（Contract 2） |
| overflow | U1 窄屏 | ✅ covered | ≤767px 时 `range` 与 `note` 均 `grid-column: 1 / -1`（:1336-1345 既有，必须保留） |
| overflow | U2 标签列 | ✅ covered | 88px 容纳六字名（78px 文本 + 10px 余量）。超过 6 字须重新量列宽，不得截断（Contract 4） |
| long-text | U2 `explanation` | 🧪 backstop | 1fr ≈ 298px，13px 下约 22 字/行；既有最长句（含阈值与分数）实测折 2 行，可接受。**D-01 后的新句子长度未知**，须在 767px 与 1024px 两档视口实跑，确认无横向溢出、无字形裁切 |
| long-text | U1 `riskNote` | 🧪 backstop | 契约限定 ≤24 字（去空格码点计）。若后端生成超过 24 字，仍只会变 2–3 行而不会横向溢出；须实跑确认不出现 3 行以上导致面板失衡。**在 1024px 与 1280px 各量一次实际行数** —— 608px 只成立于 1120px 内容宽口径，1024px 视口下 `1fr` 只有约 272px |
| long-text | U3 pill | 🧪 backstop | pill 文本来自固定 8 词表，长度可控；但 `未测`/`缺数据` 之外若走 `?? state` 兜底会露出英文枚举，须实跑确认兜底路径未触发 |

**四个 backstop 的验证方式：** 在 320px / 767px / 1024px / 1280px 四档视口各实跑一次，记录
（a）`document.querySelector('.risk-coverage').scrollWidth` 是否大于 `clientWidth`；
（b）`.risk-dialog-breakdown__item` 与 `.risk-coverage__item` 的最大行高与行数
（**1280px 与 1024px 两档都要量**，因为 `1fr` 从 ≈528px 掉到 ≈272px，`riskNote` 的行数在这里变化）；
（c）pill 文本是否出现任何 ASCII 大写枚举。无实测读数不得声称通过。

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none（`Tool: none`，无 `components.json`） | not required |
| 第三方 registry | none | not applicable |

本阶段**不引入任何外部 registry 组件**。所有元素来自已安装的 `element-plus@2.14.0`；
Contract 1/3/4 的改动全部是本仓库 `Risk.vue` 的文本与 CSS 声明。

---

## Deferred（明确不做，避免范围蔓延）

| 项 | 理由 |
|----|------|
| 覆盖面板加载失败时的可见错误态 | `catch { coverage.value = [] }`（:883）会把「取不到」渲染成「你没有指标」。修它需要新的错误态表面（`ErrorState` 或面板级提示），超出本阶段「只改数据不改结构」的边界。**记录为已知缺陷**，建议 Phase 18 处理 |
| `el-dialog width="600px"` 在 375px 视口横向溢出 | `.el-dialog` 无 `max-width`，`--el-overlay { overflow: auto }` 使页面可横向滚动。pre-existing，与本阶段新增内容无关（Contract 4 已保证新标签不额外增宽） |
| 把 `el-tag` 的 500 字重归一化 | `theme.css:542-549` 的 `!important` 锁；属字重档位债，与内容密度无关 |
| 血脂四项的补录入口 | 覆盖面板对 `MISSING` 行不提供「去补录」链接（创建流程的 `goProfileWithFocus` 只覆盖档案字段）。用户看到四项血脂未测却无处可去，是真实体验缺口 —— 但新增跳转按钮属新组件行为，留给后续阶段 |
| 为「心血管已独立」增加任何视觉强调 | Contract 7.2 明令禁止；那会破坏三种疾病共用一套归因表的一致性 |
| 视觉回归 / 截图 diff | 前序阶段一致排除；本阶段以四档视口实测读数替代 |
| 把 `--warning-700/-50`、`--success-700/-50` 补进 `theme.css` | 它们今天只靠 `Risk.vue` 的行内 fallback 生效。补进去要新增 4 个 token，违反「零新增颜色」；且需在 Dark/Light 两个块各定义一次（共 8 行）。本阶段按既有 fallback 字面量计算对比度，结论有效。建议作为独立的 token 债务阶段处理 |
| 回溯改写历史快照里的小数位（`22.86` → `22.9`） | **不得做。** D-06 要求历史记录冻结为当时的真实依据（读时不重算）。改它会抹掉「当时的引擎输出是什么」，且需要一次数据迁移。新旧记录的小数位差异随新记录自然消失。见 §5.3.1 的「已知残留」 |
| 给 S1 覆盖面板加单位列，使 `120` / `120 mmHg` 两侧字符串整体可比 | 会撑破 Contract 2 的五列栅格（96/88/96/56/1fr → 需第六列），属几何变更，超出 scope fence。本阶段只要求「数值部分同 formatter」，见 §5.3.1 |

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS — Contract 5.2（`riskNote` 形状 + 24 字上限，Java 计字口径可执行）与 5.3/5.3.1（BMI 同源，断言 A 方向性三支 + **共享 formatter 后的精度归一字符串相等**）已解耦：5.3 在 `state`/`value` 层满足，5.2 不带例外；空态给下一步；S1 用 `未测`、S2 用 `缺数据`
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS — 五处 `--text-3` → `--text-2`（`range` / `empty` / `hint` / `batches__empty` / `batch__legacy`）；7 处引用 5 改 2 留，全部逐条判定，无沉默豁免
- [ ] Dimension 4 Typography: PASS — 4 档声明字号；500 共 3 处、全部 11px mono 徽标；`.risk-batches__empty` 继承 15px 已登记
- [ ] Dimension 5 Spacing: PASS — 新增声明限定 4 的倍数；既有非 4 值 **8 行 / 10 条声明 / 6 种值**全部逐条登记（`1px 6px`×2 / `7px` / `10px`×2 列间距 / `10px` 外边距 / `14px` / `6px`×2 / `2px`），含 `.risk-dialog-breakdown__warn` 的 `margin: 10px 0 0`；闭合口径可复跑
- [ ] Dimension 6 Registry Safety: PASS
- [ ] Dimension 7 Inventory Provenance: PASS — 91 components / 293 icons，附可复跑命令与解析后版本

**Approval:** pending

---

## Appendix — 证据索引与执行顺序

### A-1 本契约的每一条主张及其可复现证据

| 主张 | 证据 | 复现命令 |
|------|------|---------|
| 分组谓词交叠，指标会渲染两次 | `Risk.vue:845` / `:853` 两条 filter 无互斥条件 | 见 A-1 下方推演脚本 |
| `--border-1` 未定义，分隔线失效 | 仓库内 8 处 `var(--border-1)`，0 处定义 | `grep -rn "var(--border-1)" src/ \| wc -l` → 8；`grep -rn "border-1" src/ \| grep -v "var(--border-1)"` → 空 |
| 六字标签名挤压 80px 列 | `高密度脂蛋白` = 6 × 13px = 78px；列宽 `:1378` = `80px` | 见 A-1 下方测算 |
| `--text-3` 在 Light 下 3.97:1 | `#7c7f8e` on `#ffffff` | WCAG 2.1 公式实测 |
| `--warning-700/-50`、`--success-700/-50` 无定义 | `theme.css` 定义数 0；引用 14 处全部自带 fallback 字面量 | `grep -rn -- "--warning-700\s*:\|--warning-50\s*:\|--success-700\s*:\|--success-50\s*:" src/ \| wc -l` → 0；`for t in warning-700 warning-50 success-700 success-50; do grep -rn "var(--$t" src/ \| wc -l; done` → 5 / 3 / 3 / 3，共 14 |
| `.risk-page` 无 `max-width` | 只有 `padding: 20px`（`Risk.vue:921-923`）；`--content-max-width` 仅被 `.container`/dashboard/data-input/Alerts 使用 | `grep -rn "content-max-width" src/` |
| `el-card__body` padding = 20px | `--el-card-padding: 20px` 默认，`theme.css` 未覆盖 | 读 `node_modules/element-plus/theme-chalk/el-card.css` |
| 500 字重共 3 处 | `:1123`、`:1283`、`:1397`，全部 `500 11px/1.4 var(--font-mono)` | `grep -n "font: 500" src/views/prevention/Risk.vue` |
| 非 4 倍数间距共 8 行 / 10 条声明 / 6 种值 | 区间内 9 条：`:1284` / `:1398`（`1px 6px`）、`:1252`（`7px`）、`:1250` / `:1379`（`10px` 列间距）、`:1444`（`10px` 外边距）、`:1224`（`14px`）、`:1228`（`6px`）、`:1339`（`2px`）；区间外 1 条：`:998`（`6px`，批次区） | `awk 'NR>=1190 && NR<=1450 && /(margin\|padding\|gap)/ {print NR": "$0}' src/views/prevention/Risk.vue` 后逐条核对；`sed -n '995,1000p'` 取批次区 `gap: 6px` |
| `--text-3` 共 7 处，5 改 2 留 | `:1279` / `:1235` / `:1329` / `:1047` / `:1040` 收紧；`:956` / `:1017` 保持 | `grep -n "var(--text-3)" src/views/prevention/Risk.vue` → 7 条（956 / 1017 / 1040 / 1047 / 1235 / 1279 / 1329） |
| 格式守卫放行括号注 | `心血管评估：超过 24（档）kg/m² 计入 20 分` → 正则 PASS、去空白 23 字 | `node -e "console.log(/^[^：]+评估：(超过\|低于\|超出) .+ 计入 \d+ 分$/.test('心血管评估：超过 24（档）kg/m² 计入 20 分'))"` → true |
| 面板与引擎的 BMI 格式不同（本修订的核心缺陷与修复） | 面板 `formatValue` 按 `precision=1` → `22.9`；引擎 `stripTrailingZeros().toPlainString()` → `22.86`。三行真实档案全部不一致（22.86/20.15/20.15）。修复 = 两侧共用同一 formatter（§5.3.1） | 读 `MetricCoverageAssembler:137-140`、`RiskAssessmentServiceImpl:328`、`HealthMetricMetaRegistry:187`（`bmi` precision=1）、`HealthProfileServiceImpl:121`（写入 2 位）；SQL `SELECT id,height_cm,weight_kg,bmi FROM health_profile` |
| 容差断言不单调（route (a) 不可用的原因） | `abs(22.9−22.86)=0.0399` 通过；`abs(20.2−20.15)=0.0500000000000007` 失败；`(20.15).toFixed(1)='20.1'` 而面板给 `20.2` | `node -e "console.log(Math.abs(20.2-20.15), (20.15).toFixed(1))"` |
| 各指标的注册表精度 | `systolicBP` 0 · `diastolicBP` 0 · `bloodGlucose` 1 · `bmi` 1 · 血脂四项 2 | `HealthMetricMetaRegistry.java:61 / :79 / :97 / :187 / :223 / :241 / :259 / :277` |
| 档案无 BMI 时两侧状态按设计不同 | 引擎 `:326-328` → `bmiValue="22.0（估算）"`、`:362-364` → `STATE_ASSUMED`；面板 `:99-102` → `STATE_MISSING` | 读两处；断言 A 因此必须分方向 |
| HDL 是下限型指标 | `HealthMetricMetaRegistry.java:263-266`：`normalMin=1.0`, `normalMax=2.2`, 说明「正常参考值 >= 1.00」 | 读文件 |
| `bmi` 同时存在于档案与指标表 | 引擎读 `profile.getBmi()`（`:326-327`）；面板读 `latestByCanonicalKey`（`MetricCoverageAssembler:56`） | 读两处 |
| `age` / `familyHistory` 不是注册表指标 | `grep '"age"' HealthMetricMetaRegistry.java` → 空；`familyHistory` → 0 | 读文件 |
| 弹窗纵向溢出可滚动 | `el-overlay.css`: `.el-overlay{…;overflow:auto}` | 读 `node_modules/element-plus/theme-chalk/el-overlay.css` |

推演脚本（Zero-dependency，Node）：

```js
const rows=[
 {key:'totalCholesterol',riskNote:'心血管评估：超过 5.2 mmol/L 计入 20 分',state:'MISSING'},
 {key:'systolicBP',riskNote:'高血压评估：超过 130 mmHg 计入 35 分',state:'HIGH'},
 {key:'steps',riskNote:null,state:'NORMAL'},
 {key:'uricAcid',riskNote:null,state:'MISSING'},
];
const used=rows.filter(m=>!!m.riskNote);
const other=rows.filter(m=>!m.riskNote&&m.state!=='MISSING');
const missing=rows.filter(m=>m.state==='MISSING');            // 现状
const missingFixed=rows.filter(m=>m.state==='MISSING'&&!m.riskNote); // 契约
const all=[...used,...other,...missing].map(r=>r.key);
console.log('现状重复:', all.filter((k,i)=>all.indexOf(k)!==i));        // ['totalCholesterol']
console.log('修正后总数:', used.length+other.length+missingFixed.length); // = rows.length
```

### A-2 执行顺序约束

| 序 | 事项 | 不先做的后果 |
|----|------|-------------|
| 1 | Contract 1 分组互斥 | 后端 D-02 一落地，每个用户立刻看到 5 行重复渲染；`missingMetrics.length` 与摘要数字同时偏大 |
| 2 | Contract 3 的 `--border-1` → `--border` | 四行连续 MISSING 没有分隔线，读成一块灰；且这个缺陷会让 UAT 把「密度可接受」判为不通过 |
| 3 | Contract 4 标签列 `80px → 88px` | 六字血脂名在两行上折行，列表行高不均 |
| 4 | Contract 5 `riskNote` 方向 | HDL 会输出与事实相反的句子（对偏低的人说「超过」） |
| 5 | Contract 5.3 BMI 同源（值/状态层）+ §5.3.1 共享 formatter | 面板与弹窗对同一用户给出矛盾的「有没有 BMI」，且**三行现有档案每一行都在两个表面显示不同数字**（`22.9` vs `22.86`）。注意此修**不改 `riskNote` 文案**（Contract 5.2 无例外条款），只填 `value`、算 `state`、**并让引擎的 `value` 走面板同一个 formatter**。**双源因子以引擎源为唯一取值源**：档案无值时不得回落到指标表的同名行，否则面板会显示引擎从未使用的数字。落地后跑断言 A(i)/A(ii)/A(iii) 三支，全部通过才算完成 —— A(i) 用**字符串相等**，若引擎仍用 `stripTrailingZeros()` 则必然失败 |

**第 1 项必须与后端 D-02 同批交付**，否则本阶段一上线就是可见的重复与错位数字。

### A-3 与上游契约的关系

| 上游契约 | 本阶段动作 |
|---------|-----------|
| 15-UI-SPEC（覆盖面板与归因弹窗的形态） | **沿用，不改结构**。本契约只在既有 DOM 上改谓词、文案、列宽与 token 引用 |
| 14-UI-SPEC §Color（`--text-3` → `--text-2` 收紧） | **沿用同一判定**，作用于五处信息文字（S1 内 `range` / `empty` / `hint` 三处，加 `batches__empty` 与 `batch__legacy` 两处批次区列表级/说明级文字，后者按 §「本阶段受影响的三个表面」例外 E1/E2 具名） |
| 14-UI-SPEC（错误呈现：`showError` 唯一出口） | **沿用**。本阶段不新增错误通道、不新增字典键 |
| Phase 11 §3.4 四态互斥（空/失败/不存在/无权限） | **沿用**；覆盖面板的失败态缺陷记入 Deferred，不在本阶段新增第五态 |
| `ROADMAP` Phase 16 成功标准 2 / 3 / 4 / 5 | 前端观测点：各项之和 = 总分（C7.2）、`MISSING` 与 `NORMAL` 可区分（C6.1）、`riskNote` 与引擎一致（C5）、快照不重算（C7.3 空明细不渲染） |

### A-4 测试影响

| 文件 | 影响 |
|------|------|
| `src/__tests__/views/RiskBackfill.spec.ts`（31 条 `it()`） | 现有 31 条**全部保持不变**（它们断言的是补填面板、批次折叠、四态标签、面包屑文案，均未被本契约触及）。**新增 8 条**（①–⑧）：① 有 `riskNote` 且 `MISSING` 的指标只渲染一次；② 三组行数之和等于 `coverage.length`；③ `usedMissing > 0` 时摘要出现「其中 N 项未测」（**断言 `未测`，不是 `缺数据`**）；④ `CARDIO` 归因不含 `来自` / `折半` 字样；⑤ 每条 `riskNote` 匹配 `^[^：]+评估：(超过\|低于\|超出) .+ 计入 \d+ 分$` 且 `replace(/\s/g,'').length <= 24`；⑥ 每条 `riskNote` **不匹配** `/(健康档案\|档案\|来源\|取自)/`（来源禁令的可执行化）；⑦ §5.3 断言 A 的三支 —— A(i) `bmi` 行非 `MISSING` 时其 `value` **字符串等于**弹窗 `key==='bmi'` 的 `value`（两侧同 formatter、同 precision，**不做数值归一**）；A(ii) `bmi` 行 `MISSING` 且弹窗有 `bmi` 时为 `ASSUMED` 且 `assumptions` 含 `缺少 BMI，按 22.0 估算`；A(iii) `bmi` 行 `MISSING` 且弹窗无 `bmi` 时通过；⑧ **§5.3.1 的精度守卫（新增，必须有牙齿）**：造一条 `bmi = 22.86` 的档案，**新跑一次创建评估**，断言 `coverage` 中 `bmi` 行的 `value` 与本次归因中 `key==='bmi'` 的 `value` **逐字符相等且都等于 `22.9`**，并额外断言两者 **不等于 `22.86`**。同理对 `bloodGlucose = 5.55` 断言两侧数值部分都是 `5.6`（引擎值带 ` mmol/L`，只比数值前缀）。这条用 `stripTrailingZeros()` 的旧实现会失败（`22.86` / `5.55`），是 formatter 不被摘掉的牙齿 |
| 关于 ⑤ 的覆盖边界（必须写清，否则会被当成「来源禁令已被守卫」） | ⑤ 只锁**形状与长度**。实测反例：`心血管评估：超过 24（档）kg/m² 计入 20 分` 匹配该正则、去空白后 23 字，**⑤ 放行**。来源禁令由 ⑥ 的否定断言 + §5.2 散文 + Copywriting 表共同承担；⑤ **不得**在下游被描述为「同时锁住了来源标注」 |
| 几何断言 | 沿用该文件既有的「读源码 + 正则」手法：新增一条断言 `.risk-dialog-breakdown__item` 的首列 ≥ 88px，且 `Risk.vue` 内 `var(--border-1)` 出现次数为 0 |
| 基线 | 前端 `npm run test:run` 当前 640 条全绿；本阶段只加不减 |
