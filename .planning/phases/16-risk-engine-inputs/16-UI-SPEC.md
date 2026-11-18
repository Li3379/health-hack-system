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
`risk-estimate` 补填面板、创建流程、分页。本阶段不动它们的任何一行。

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
| `--sp-1` | 4px | 状态 pill 内边距垂直分量（`padding: 1px 6px` 的 4 倍数近似已存在，不改）；新增声明只允许 4 |
| `--sp-2` | 8px | 弹窗构成列表的 `padding: 8px 12px` 垂直分量；标题 → 列表间距 `margin-bottom: 8px` |
| `--sp-3` | 12px | 覆盖面板行 `padding` 水平分量（`7px 12px`）、列表项 `gap: 10px`→见例外；面板内边距 `padding: 16px` 的水平补偿 |
| `--sp-4` | 16px | `.risk-coverage { padding: 16px }`、`margin-bottom: 16px`；`.risk-dialog-breakdown { margin-top: 16px }` |
| `--sp-5` | 24px | 弹窗列表缩进基线（`padding-left: 24px` 用于假设列表） |
| `--sp-6` | 32px | 空态垂直留白（`.risk-batches__empty { padding: 32px 0 }`） |
| `--sp-7` | 48px | 本阶段不使用 |
| `--sp-8` | 64px | 本阶段不使用 |

**例外（已存在，本阶段不新增，仅登记以免被误判为漂移）：**

| 值 | 位置 | 说明 |
|----|------|------|
| `7px` | `.risk-coverage__item { padding: 7px 12px }`（:1252） | 覆盖面板行高压缩，Phase 15 已交付；本阶段不改行高 |
| `10px` | `.risk-coverage__item { gap: 10px }`（:1250）、`.risk-dialog-breakdown__item { gap: 10px }`（:1379） | 栅格列间距；本阶段不改 |
| `6px` | `.risk-batch` 系列 | 属批次区，本阶段不动 |

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
| Pill | 11px | 500 | 1.4 | 两处状态 pill（`--font-mono`） |

**字重的例外说明（必须保留，否则 checker 会视为第三档字重漂移）：**
500 只出现在 `.risk-coverage__state` 与 `.risk-dialog-breakdown__state` 两个状态 pill 上，
且与 `--font-mono` 绑定 —— 这是 Phase 15 已交付的既有形态，本阶段**不新增任何 500 字重声明**，
也不把 500 扩展到文字正文。新增的 pill 类型（若后端将来新增状态）必须沿用这一个 pill 类，
不得另起字号。

**不得**把 `explanation` 降到 12px 来吸收四行 MISSING 的视觉重量 —— 那会同时降低
「为什么计了这些分」的可读性，而这句话正是整个弹窗存在的理由。

---

## Color

所有色值来自 `theme.css` 既有 token。**本阶段不新增任何颜色 token。**
下表对比度为 WCAG 2.1 相对亮度公式实测（半透明值按合成后计算）。

### 60 / 30 / 10 分配

| 占比 | 角色 | Dark | Light | 本阶段占用位置 |
|------|------|------|-------|---------------|
| 60% | 主导 / 画布 | `--bg` `#0a0b0e` | `--bg` `#f5f7fa` | 页面背景（`el-card` 之下） |
| 30% | 次主导 / 表面 | `--surface-1` `#111217` | `#ffffff` | 覆盖面板容器、列表底板、弹窗内容面 |
| 10% | 强调 | `--accent-cool` `#5EEAD4` | `#0d9488` | **仅限**：`创建评估` 主按钮底面、分页当前页、`:focus-visible` 轮廓 |
| 语义 / 错误 | `--hhs-color-error-text` `#f56c6c` | `#b91c1c` | 仅 HIGH 与 CONTRIBUTED 两态的文字/底纹（见下） |

**强调色绝不用于状态。** 两处状态 pill 一律走语义色（红/橙/绿/灰），不得用 accent-cool ——
accent 在本项目已经固定表达「可交互的主操作」，若也表达「正常」，会让唯一的主 CTA 失去区分度。

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
| `.risk-coverage__empty`（:1325-1330） | `color: var(--text-3)`，12px | `var(--text-2)` | 空态说明句（Copywriting 要求它承担「下一步」） |
| `.risk-batches__empty`（:1044-1048） | `color: var(--text-3)`，13px | `var(--text-2)` | 同上 |

**保持 `--text-3` 的位置（非信息文字，不改）：** `.risk-coverage__hint`（分组标题的括号补充，
紧邻已用 `--text-2` 的标题主体）、`.risk-batch__legacy-badge`、`.risk-batch__delta--flat/first`。

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
| 新增（仅当 `usedMissing > 0`） | — | `（其中 {{ usedMissing }} 项缺数据）` |

`usedMissing = usedMetrics.filter(m => m.state === 'MISSING').length`。

**不新增分组、不新增组件、不改行结构** —— 只改一句文案与一个 computed。
**不得**把缺失的参与项从「参与」组里挪走以让数字变小：那会重新引入 1.2 的谎言。

---

## Component Contract 2 — 覆盖面板的 `riskNote` 密度

D-03/D-05 之后，有 `riskNote` 的指标从 3 项增加到约 8 项（血糖、收缩压、舒张压、BMI、血脂四项）。
面板的栅格是：

```css
.risk-coverage__item { grid-template-columns: 96px 88px 96px 56px 1fr; }
```

`riskNote` 落在最后那个 `1fr`。其可用宽度：

| 分量 | 值 |
|---|---|
| 面板内容宽（`--content-max-width` 1120 − 页面 `padding: 20px × 2` − 面板 `padding: 16px × 2`） | 1048px |
| 固定列 | 96 + 88 + 96 + 56 = 336px |
| 列间距 | `gap: 10px × 4` = 40px |
| 行内边距 | `12px × 2` = 24px |
| **`1fr` 实际可用** | **≈ 648px** |

`riskNote` 在 12px 下每字约 12px，648px 约容 54 字。本契约把单条 `riskNote` 上限定为
**24 字**（Contract 5 的格式），因此**绝大多数落在单行内**，行高不变。

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

**必须把 8 处 `var(--border-1)` 全部改为 `var(--border)`。** 这是本契约中**唯一**要求
改动 `Risk.vue` 样式声明的几何修正（其余改动都是文案/宽度的补充）。清单：

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

**长度上限 24 字**（`心血管评估：超过 5.2 mmol/L 计入 20 分` = 22 字）。
**不得**包含「可能」「建议」「请」，该列只陈述评分规则，不确定性的表达属于
`assumptions` 与弹窗警示句。

**一个指标被多个疾病读取时，`riskNote` 只描述第一条（`RISK_CONSUMERS` 的既有单值语义），
不得用 `、` 拼接两个疾病** —— 那会立刻超过 24 字并在表格里折成三行。
若将来确需表达多处参与，应作为 Phase 18+ 的字段扩展，不在本阶段。

### 5.3 BMI 的来源必须如实标注（D-05 的直接后果）

D-01 让 BMI 成为心血管的独立输入，但 **BMI 有两条数据来源**：`health_metric` 表的
`bmi` 行，与 `health_profile.bmi` 字段。引擎读的是后者（`RiskAssessmentServiceImpl.java:326-327`），
而 `MetricCoverageAssembler` 读的是前者（`latestByCanonicalKey`）。

因此一个「档案里填了 BMI、但没录过 bmi 指标」的用户（CONTEXT 实测：`bmi` 档案 3/3 有、
指标行 0 条）会看到：

| 表面 | 显示 | 与事实一致？ |
|---|---|---|
| 覆盖面板 `bmi` 行 | `未测`（因为 metric 表无行） | ❌ 引擎其实用了档案里的值 |
| 弹窗归因 | `BMI 23.5`，状态 `CONTRIBUTED`/`NORMAL` | ✅ |

这就是 D-05 说的「面板开始说谎」的具体形态。

**契约：** `MetricCoverageAssembler` 对**任何同时存在于 `health_profile` 与 `health_metric`
的因子**（当前仅 `bmi`）必须以引擎相同的来源取值。允许两种实现，二者择一：

1. 覆盖面板对有档案兜底的指标，用档案值填充 `value` 并把 `riskNote` 前缀改为
   `心血管评估（取自健康档案）：…`；
2. 或者保留 `未测`，但在 `riskNote` 末尾追加 `（评估实际使用健康档案中的 BMI）`。

**判定标准（可在前端断言）：** 对同一用户，覆盖面板 `bmi` 行的 `state !== 'MISSING'`
必须与弹窗里 `key === 'bmi'` 的归因 `state !== 'MISSING'` **一致**。不一致即为面板说谎。

> 这是后端改动（`MetricCoverageAssembler`），但它的验收面在前端两个面板的一致性上，
> 因此写进本契约。执行者不得只改后端而不同时核对两个面板的渲染结果。

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
| 摘要段 3 补充 | **`（其中 {usedMissing} 项缺数据）`** | 仅 `usedMissing > 0` 时；`usedMissing` = 有 `riskNote` 且 `MISSING` 的项数 |
| 分组标题 1 | `参与风险评估的指标` | 不变 |
| 分组标题 2 | `其他已测指标` + `（有数据，但不参与本次风险评估）` | 不变 |
| 分组标题 3 | `尚未采集` + `（{n} 项；这些不是「正常」，是「不知道」）` | 不变；`{n}` 因 Contract 1 而变小 |
| 空态 · 其他已测 | `暂无其他已测指标` | 不变 |
| 空态 · 无任何批次 | `暂无风险评估记录` | 不变 |
| 覆盖态 4 词 | `偏高` / `偏低` / `正常` / `未测` | 锁定 |
| 归因态 4 词 | `已计入` / `正常` / `按默认值` / `缺数据` | 锁定 |
| 弹窗构成标题 | `评分构成` + `各项合计 {sum} 分 = 风险评分` | 不变 |
| 弹窗缺失警示 | `有 {n} 项缺少数据，本结果未包含这些因素 —— 补齐后评分可能上升，请勿把「未计入」当作「正常」。` | 不变；`{n}` 本阶段典型值为 4 |
| 弹窗假设区标题 | `本次评估采用的假设` | 不变 |
| `riskNote` | `{疾病}评估：{方向动词} {阈值}{单位} 计入 {分数} 分` | Contract 5.2；≤24 字 |
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

Applicable state considerations resolved: **17 covered (13 explicit, 4 backstop, 0 unresolved)**

表面编号：**U1** 覆盖面板（`.risk-coverage`）· **U2** 评分构成弹窗（`.risk-dialog-breakdown`）·
**U3** 状态 pill（两套词汇共用形态）· **U4** 归因列表单元（行容器）

### 空 / 加载 / 错误

| Category | Element(s) | Status | Resolution / Reason |
|----------|------------|--------|---------------------|
| empty | U1 list-collection | ✅ covered | 三个分组各自可空：分组 1/3 用 `v-if` 整组不渲染，分组 2 空时渲染 `暂无其他已测指标`。**不得**改为 `el-empty`（会引入第三种空态形态） |
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
| partial | U2 list-collection | ✅ covered | 同一次评估内 `CONTRIBUTED` / `NORMAL` / `ASSUMED` / `MISSING` 四态混排，底部警示句给出缺失计数 |
| partial | U2 四行连续 MISSING | ✅ covered | 逐行保留（Contract 3.2），靠 1px `--border` 分隔 + 灰色 pill + 斜体句三重区分。分隔线修复是本阶段的必要条件 |
| zero-one-many | U2 归因行数 | ✅ covered | 0 → 不渲染；5（糖尿病典型）→ 单屏可见；9（心血管 D-01 后）→ 弹窗内容超出视口时由 `.el-overlay { overflow: auto }` 滚动承担（实测 `el-overlay.css` 已声明），弹窗自身不改高度 |
| zero-one-many | U3 pill | ✅ covered | 每个指标恰一个 pill，不出现 0 个或多个 |
| zero-one-many | U1 摘要数字 | ✅ covered | `usedMissing` 为 0 时括号段不渲染，不出现「（其中 0 项缺数据）」 |
| overflow | U1 `riskNote` 列 | ✅ covered | 648px 可用、单条 ≤24 字，单行；超出时自然换行至 2 行，不截断（Contract 2） |
| overflow | U1 窄屏 | ✅ covered | ≤767px 时 `range` 与 `note` 均 `grid-column: 1 / -1`（:1336-1345 既有，必须保留） |
| overflow | U2 标签列 | ✅ covered | 88px 容纳六字名（78px 文本 + 10px 余量）。超过 6 字须重新量列宽，不得截断（Contract 4） |
| long-text | U2 `explanation` | 🧪 backstop | 1fr ≈ 298px，13px 下约 22 字/行；既有最长句（含阈值与分数）实测折 2 行，可接受。**D-01 后的新句子长度未知**，须在 767px 与 1024px 两档视口实跑，确认无横向溢出、无字形裁切 |
| long-text | U1 `riskNote` | 🧪 backstop | 契约限定 ≤24 字。若后端生成超过 24 字，行高从 1 行变 2 行而不会溢出；须实跑确认不出现 3 行以上导致面板失衡 |
| long-text | U3 pill | 🧪 backstop | pill 文本来自固定 8 词表，长度可控；但 `未测`/`缺数据` 之外若走 `?? state` 兜底会露出英文枚举，须实跑确认兜底路径未触发 |

**四个 backstop 的验证方式：** 在 320px / 767px / 1024px 三档视口各实跑一次，记录
（a）`document.querySelector('.risk-coverage').scrollWidth` 是否大于 `clientWidth`；
（b）`.risk-dialog-breakdown__item` 的最大行高与行数；
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
| 视觉回归 / 截图 diff | 前序阶段一致排除；本阶段以三档视口实测读数替代 |

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

## Appendix — 证据索引与执行顺序

### A-1 本契约的每一条主张及其可复现证据

| 主张 | 证据 | 复现命令 |
|------|------|---------|
| 分组谓词交叠，指标会渲染两次 | `Risk.vue:845` / `:853` 两条 filter 无互斥条件 | 见 A-1 下方推演脚本 |
| `--border-1` 未定义，分隔线失效 | 仓库内 8 处 `var(--border-1)`，0 处定义 | `grep -rn "var(--border-1)" src/ \| wc -l` → 8；`grep -rn "border-1" src/ \| grep -v "var(--border-1)"` → 空 |
| 六字标签名挤压 80px 列 | `高密度脂蛋白` = 6 × 13px = 78px；列宽 `:1378` = `80px` | 见 A-1 下方测算 |
| `--text-3` 在 Light 下 3.97:1 | `#7c7f8e` on `#ffffff` | WCAG 2.1 公式实测 |
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
| 5 | Contract 5.3 BMI 同源 | 面板与弹窗对同一用户给出矛盾的「有没有 BMI」 |

**第 1 项必须与后端 D-02 同批交付**，否则本阶段一上线就是可见的重复与错位数字。

### A-3 与上游契约的关系

| 上游契约 | 本阶段动作 |
|---------|-----------|
| 15-UI-SPEC（覆盖面板与归因弹窗的形态） | **沿用，不改结构**。本契约只在既有 DOM 上改谓词、文案、列宽与 token 引用 |
| 14-UI-SPEC §Color（`--text-3` → `--text-2` 收紧） | **沿用同一判定**，作用于 S1 的三处信息文字 |
| 14-UI-SPEC（错误呈现：`showError` 唯一出口） | **沿用**。本阶段不新增错误通道、不新增字典键 |
| Phase 11 §3.4 四态互斥（空/失败/不存在/无权限） | **沿用**；覆盖面板的失败态缺陷记入 Deferred，不在本阶段新增第五态 |
| `ROADMAP` Phase 16 成功标准 2 / 3 / 4 / 5 | 前端观测点：各项之和 = 总分（C7.2）、`MISSING` 与 `NORMAL` 可区分（C6.1）、`riskNote` 与引擎一致（C5）、快照不重算（C7.3 空明细不渲染） |

### A-4 测试影响

| 文件 | 影响 |
|------|------|
| `src/__tests__/views/RiskBackfill.spec.ts`（31 条 `it()`） | 现有 31 条**全部保持不变**（它们断言的是补填面板、批次折叠、四态标签、面包屑文案，均未被本契约触及）。**新增** 4 条：① 有 `riskNote` 且 `MISSING` 的指标只渲染一次；② 三组行数之和等于 `coverage.length`；③ `usedMissing > 0` 时摘要出现「其中 N 项缺数据」；④ `CARDIO` 归因不含 `来自` / `折半` 字样 |
| 几何断言 | 沿用该文件既有的「读源码 + 正则」手法：新增一条断言 `.risk-dialog-breakdown__item` 的首列 ≥ 88px，且 `Risk.vue` 内 `var(--border-1)` 出现次数为 0 |
| 基线 | 前端 `npm run test:run` 当前 640 条全绿；本阶段只加不减 |
