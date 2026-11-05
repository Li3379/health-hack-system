# Phase 08: 健康指标管理页录入与编辑对齐 - Research

**Researched:** 2026-11-05  
**Domain:** 前端工程 (Vue 3, Pinia, Element Plus, 表单四梯度校验, 血压协同交互)  
**Confidence:** HIGH  

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### 1. 指标选项全量元数据动态驱动 (HEALTH-01)
- **D-01 (全量元数据动态生成):**
  - 在 `src/views/health/Metrics.vue` 中，顶部筛选栏（filter-bar）和添加/编辑对话框（dialog）内的「指标类型」下拉选择器（`el-select`），全部通过 `v-for="item in metricMetaStore.healthMetrics"` 动态循环渲染全量 16 项权威医学指标；
  - 下拉选项的 `label` 显示权威中文名称（`item.displayName`），`value` 绑定标准 canonicalKey（`item.canonicalKey`）；
  - 彻底删除模板中硬编码的 7 项静态 `<el-option>` 以及 `<script setup>` 中的私有常量 `metricUnitMap`；单位与精度直接从 `useMetricMetaStore` 动态获取。

#### 2. 血压协同录入与历史单项编辑适配 (HEALTH-02)
- **D-02 (血压新增协同+编辑单项精准适配):**
  - **新增指标模式 (`editingId === null`)**：
    - 支持用户在下拉框中选择常规单项指标或“血压”；
    - 当选择血压时，表单切换为收缩压与舒张压同屏并列网格布局，动态展示双状态胶囊；若出现 `收缩压 <= 舒张压` 倒置，即时弹出黄色警告条并提供「对调高低压数值」原子交换按钮，保存时通过 `Promise.all` 协同持久化；
  - **编辑指标模式 (`editingId !== null`)**：
    - 根据当前行的 `row.metricKey` 加载记录，禁用指标类型切换；
    - 绑定当前单项指标专属的元数据（例如仅编辑收缩压或舒张压单个历史点），表单展示当前指标的数值输入、状态胶囊与参考指南，允许用户调整历史数值并重新评估。

#### 3. 四梯度宽容校验与非破坏性交互 (HEALTH-03)
- **D-03 (四梯度非破坏性反馈与危急值确认闭环):**
  - 接入 `useMetricValidation`，输入数值变化实时派生四梯度状态（正常/预警偏差/急危重症/存活极限）；
  - **非破坏性原则**：彻底禁止失焦抹除任何输入数据，原值 100% 忠实保留在表单中；
  - **宽容放行**：Tier 1（正常）与 Tier 2（预警偏差）表单校验直接放行通过，允许真实病变数据留存；
  - **危急值确认**：达到 Panic Limits 时在点击保存前触发 `CriticalConfirmDialog` 模态窗，提供 120 急救指引，确认后顺利持久化入库；
  - **物理极限拦截**：仅在超出人体生理存活极限（Tier 4）时提示阻断保存，原输入文本不被清空。

#### 4. 弹窗全局平铺与置顶标签排版优化 (UI-SPEC)
- **D-04 (弹窗全局平铺与布局优化):**
  - `Metrics.vue` 中的添加/编辑弹窗 `<el-dialog>` 显式添加 `append-to-body`，使其 DOM 脱离外层 `<el-card>` 的 CSS transform 动画层叠上下文，居中覆盖全屏视口；
  - 表单设置 `label-position="top"`，赋予数值输入框 100% 完整宽度，确保占位符（如 `参考范围: 90 ~ 139 mmHg`）清晰平铺呈现而不被截断；
  - 表单底部常态化挂载 `ReferenceGuideCard.vue`。

### Claude's Discretion
- 数据表格列表列宽微调与响应式适配；
- 趋势选择项（上升/下降/稳定）与自动计算趋势的协同展示逻辑。

### Deferred Ideas (OUT OF SCOPE)
- **Phase 9 承接：** `src/views/prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 保健指标录入对齐。
- **Phase 10 承接：** 通用指标表单交互组件抽象与全站编辑模式终极统一。
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| HEALTH-01 | `src/views/health/Metrics.vue` 弹窗重构为动态从 `useMetricMetaStore` 获取指标列表、单位、精度、步长与参考范围占位符，彻底废除硬编码的 7 项 `el-option` 与 `metricUnitMap` 私有字典 | 接入 `useMetricMetaStore().healthMetrics` 循环渲染 16 项医学指标；废除 `metricUnitMap`，自动绑定 `definition.unit`、`precision`、`step` 和参考范围 placeholder |
| HEALTH-02 | `src/views/health/Metrics.vue` 实现血压收缩压与舒张压协同双字段录入、倒置警示检测与一键对调能力，支持在列表页中统一录入与编辑 | 扩展 `useMetricValidation` 增加协作标志支持，新增模式提供血压双字段协同与原子对调，编辑模式锁定历史单项记录精准评估与更新 |
| HEALTH-03 | `src/views/health/Metrics.vue` 接入四梯度纯函数医学校验引擎与非破坏性失焦保护，超出正常范围展示状态胶囊与解释性文案，危急值触发二次确认弹窗，物理极限拦截但不抹除输入 | 接入 `useMetricValidation`、挂载 `CriticalConfirmDialog.vue` 与 `ReferenceGuideCard.vue`，实现 Tier 1/2 宽容放行、Tier 3 二次确认、Tier 4 非破坏性拦截 |
</phase_requirements>

## Summary

Phase 08 聚焦于对健康指标主管理页面（`hhs-frontend-v2/src/views/health/Metrics.vue`）的录入与编辑交互进行彻底重构，将其从原始的硬编码、粗粒度拦截和破坏性重置升级为元数据驱动的现代临床医疗填报系统。

目前 `Metrics.vue` 存在三大核心痛点：
1. **硬编码字典与不一致**：筛选区和对话框内均硬编码了 7 项写死的指标（`glucose`, `systolicBP`, `diastolicBP`, `heartRate`, `temperature`, `weight`, `bmi`），且组件内部维护了私有的 `metricUnitMap` 字典（[VERIFIED: hhs-frontend-v2/src/views/health/Metrics.vue:188-196]），无法呈现血氧、血脂四项、尿酸、肌酐等其它 9 项指标；
2. **血压割裂输入与倒置风险**：血压记录在新增时无法协同录入高压和低压，容易发生收缩压低于舒张压的倒置错误；而在编辑模式下，由于每条记录为独立持久化实体，必须精准匹配单项元数据（如仅编辑收缩压），不得错误唤起双字段协同；
3. **生硬拦截与破坏性重置**：输入切换时直接将数值重置为 0，缺乏生理四梯度分级、临床解读卡片与危急值二次确认机制；弹窗因挂载在外层带有 GSAP CSS transform 动画的 `.el-card` 内而破坏了层叠上下文居中。

通过将 Phase 5/6 沉淀的核心资产（`useMetricMetaStore`, `useMetricValidation`, `ReferenceGuideCard.vue`, `CriticalConfirmDialog.vue`）全面接入 `Metrics.vue`，实现指标选项全量动态化、新增协同与编辑单项精准适配、四梯度宽容与非破坏性保护，以及通过 `append-to-body` 脱离动画上下文实现全局平铺。

**Primary recommendation:** 重构 `Metrics.vue` 时将状态清晰划分为“新增协作模式”与“编辑单项模式”，利用 `useMetricValidation` 统一四梯度评估和危急确认，表单添加 `append-to-body` 与 `label-position="top"`，彻底移除硬编码 `el-option` 与私有 `metricUnitMap`。

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 全量医学指标元数据拉取与响应式共享 | Frontend Store (`metricMeta.ts`) | Backend REST API (`/api/metrics/metadata`) | Pinia Store 集中管理 16 项健康指标，具备本地静态离线兜底与热更新能力 |
| 四梯度生理状态评估与 Panic Limits 识别 | Frontend Composable (`useMetricValidation.ts`) | Frontend Pure Utils (`metricValidation.ts`) | 纯函数判定 Tier 1~4，零网络开销毫秒级响应，与后端 Class-level Jakarta 校验规则严格一致 |
| 血压高低压协同录入与原子自纠 | Frontend View / Composable | — | 纯前端响应式状态管理，收缩压与舒张压倒置检测及原子数据互换 |
| 急危重症二次确认与急救就医指引 | Frontend Component (`CriticalConfirmDialog.vue`) | — | UI 模态对话框交互，拦截误触并提供 120 急救指引，确认后放行保存 |
| 临床生理参考标准常态化解读 | Frontend Component (`ReferenceGuideCard.vue`) | — | 纯 UI 呈现组件，根据当前指标元数据动态展示基准区间与场景注意事项 |
| 指标数据持久化与更新 | Frontend View / API (`@/api/health`) | Backend Controller (`HealthMetricController`) | 新增血压使用 `Promise.all` 协同持久化两条记录，单项更新使用 `PUT /api/metrics/{id}` |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `vue` | `^3.4.15` [VERIFIED: hhs-frontend-v2/package.json:28] | 前端核心框架 | 响应式 Composition API、`<script setup>` 标准语法 |
| `element-plus` | `^2.5.6` [VERIFIED: hhs-frontend-v2/package.json:23] | UI 组件库 | 提供 `el-dialog`, `el-form`, `el-select`, `el-input-number`, `el-alert`, `el-table` |
| `pinia` | `^2.1.7` [VERIFIED: hhs-frontend-v2/package.json:26] | 状态管理库 | 响应式共享权威元数据 `useMetricMetaStore` |
| `@element-plus/icons-vue` | `^2.3.1` [VERIFIED: hhs-frontend-v2/package.json:18] | 图标集合 | 提供 `Check`, `Warning`, `Bell`, `CircleClose`, `Switch`, `InfoFilled`, `Plus` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `gsap` | `^3.15.0` [VERIFIED: hhs-frontend-v2/package.json:24] | 动画增强 | 页面初始化 `scrollReveal`、按钮点击 `buttonPress` |
| `vitest` | `^4.1.0` [VERIFIED: hhs-frontend-v2/package.json:52] | 单元与集成测试 | 执行前端组件与 composable 自动化测试 (`npm run test:run`) |
| `@vue/test-utils` | `^2.4.6` [VERIFIED: hhs-frontend-v2/package.json:39] | Vue 测试工具 | 挂载组件与验证 DOM 交互 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 复用 `ReferenceGuideCard` & `CriticalConfirmDialog` | 在 `Metrics.vue` 内重新写一套解读与弹窗 | 重复代码，造成交互不一致与维护成本激增；现成组件已被 Phase 6 充分验证 |
| 扩展 `useMetricValidation` 支持协同标志 | 在 `Metrics.vue` 单独手写一套倒置和评估逻辑 | 破坏单一事实来源，违背 DRY 原则；扩展参数完全向后兼容 |

**Installation:**
本阶段使用项目现有前端依赖，无需安装任何额外依赖包：
```bash
cd hhs-frontend-v2
npm run test:run
```

## Package Legitimacy Audit

本阶段全部变更均在现存代码仓（`hhs-frontend-v2`）内部完成，不引入任何新增外部 npm 包。现有依赖均为生产与开发已安装配置包：

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `vue` | npm | 10+ yrs | 5M+/wk | `github.com/vuejs/core` | [OK] | Approved (existing) |
| `element-plus` | npm | 4+ yrs | 400k+/wk | `github.com/element-plus/element-plus` | [OK] | Approved (existing) |
| `pinia` | npm | 4+ yrs | 3M+/wk | `github.com/vuejs/pinia` | [OK] | Approved (existing) |
| `@element-plus/icons-vue`| npm | 3+ yrs | 300k+/wk | `github.com/element-plus/element-plus-icons` | [OK] | Approved (existing) |
| `vitest` | npm | 3+ yrs | 4M+/wk | `github.com/vitest-dev/vitest` | [OK] | Approved (existing) |

**Packages removed due to [SLOP] verdict:** none  
**Packages flagged as suspicious [SUS]:** none  

## Architecture Patterns

### System Architecture Diagram

```
+---------------------------------------------------------------------------------------------------+
| Metrics.vue Architecture & Data Flow                                                              |
+---------------------------------------------------------------------------------------------------+
|                                                                                                   |
|  [顶部筛选 Filter Bar]                                 [操作区 Action Bar]                        |
|   - el-select (v-for in metricMetaStore.healthMetrics)   - 添加指标按钮 -> showAddDialog()         |
|   - el-date-picker                                                                                |
|          |                                                                                        |
|          v                                                                                        |
|  [健康指标数据表格 el-table]                                                                      |
|   - 格式化展示: row.metricName || getMetricLabel(row.metricKey)                                   |
|   - 操作列: [编辑] -> handleEdit(row)  /  [删除] -> handleDelete(row)                             |
|          |                                       |                                                |
|          | (用户点击添加)                        | (用户点击编辑某单条记录)                       |
|          v                                       v                                                |
|  +---------------------------------------------------------------------------------------------+  |
|  | 指标弹窗 (el-dialog append-to-body, label-position="top", width="560px")                    |  |
|  |                                                                                             |  |
|  | 模式分支: isCollaborative = (editingId === null && form.metricKey === 'bloodPressure')      |  |
|  |                                                                                             |  |
|  | +--------------------------------------+   +----------------------------------------------+ |  |
|  | | 分支 A: 血压协同新增模式 (Add BP)     |   | 分支 B: 单项常规/历史编辑模式 (Single Metric)| |  |
|  | |                                      |   |                                              | |  |
|  | | 1. 左右两列 (收缩压 + 舒张压)        |   | 1. 单列输入 (v-model="form.value")           | |  |
|  | | 2. 状态胶囊 (primaryEval/diastolic)  |   | 2. 状态胶囊 (primaryEval)                    | |  |
|  | | 3. 倒置警示条 (收缩压 <= 舒张压)      |   | 3. 动态 placeholder/precision/step           | |  |
|  | | 4. 一键对调按钮 (原子 swap)          |   | 4. 编辑时禁用指标类型下拉 (:disabled="true") | |  |
|  | +--------------------------------------+   +----------------------------------------------+ |  |
|  |                                                                                             |  |
|  | [辅助字段行: 单位 disabled el-input + 记录日期 el-date-picker + 趋势变化 el-select]         |  |
|  |                                                                                             |  |
|  | [常态化参考卡片: ReferenceGuideCard :definition="definition" :diastolicDef="..."]           |  |
|  |                                                                                             |  |
|  | [底部动作区: 取消按钮 / 确定保存按钮 (:disabled="isSaveBlocked")]                           |  |
|  +---------------------------------------------------------------------------------------------+  |
|                                       | 点击保存 (handleSubmit)                                   |
|                                       v                                                           |
|                       +-------------------------------+                                           |
|                       | hasCritical (达到危急值) ?    |                                           |
|                       +-------------------------------+                                           |
|                               /               \                                                   |
|                        YES   /                 \  NO                                              |
|                             v                   v                                                 |
|                 [CriticalConfirmDialog]     [executeSave()]                                       |
|                 (120急救提示/二次确认)              |                                              |
|                             |                   |                                                 |
|                       用户点击确认              |                                                 |
|                             +-------------------+                                                 |
|                             |                                                                     |
|                             v                                                                     |
|                 +---------------------------------------+                                         |
|                 | 协同持久化还是单条更新?               |                                         |
|                 +---------------------------------------+                                         |
|                        /                         \                                                |
|          isCollaborative /                         \ 单项新增或更新                               |
|                         v                           v                                             |
|             Promise.all([                       editingId ?                                       |
|               createMetric(systolic),             updateMetric(id, data)                          |
|               createMetric(diastolic)           : createMetric(data)                              |
|             ])                                      |                                             |
|                         \                          /                                              |
|                          v                        v                                               |
|                    成功提示 ElMessage.success + fetchMetrics() 刷新表格                            |
+---------------------------------------------------------------------------------------------------+
```

### Recommended Project Structure
```
hhs-frontend-v2/src/
├── composables/
│   └── useMetricValidation.ts          # 扩展协同判断支持: isCollaborativeRef
├── stores/
│   └── metricMeta.ts                   # 权威元数据 Store (healthMetrics, getDefinition)
├── utils/
│   ├── format.ts                       # getMetricLabel, getMetricUnit, formatDateTime
│   └── metricValidation.ts             # 四梯度评估纯函数与 Panic Limits
├── views/
│   ├── data-input/components/
│   │   ├── ReferenceGuideCard.vue      # 常态化临床指南解读卡片 (复用)
│   │   └── CriticalConfirmDialog.vue   # 急危重症二次确认弹窗 (复用)
│   └── health/
│       └── Metrics.vue                 # 核心重构目标页面 (全量元数据驱动、血压协同与编辑适配)
└── __tests__/
    ├── composables/
    │   └── useMetricValidation.spec.ts # 单元测试
    └── views/
        └── Metrics.spec.ts             # 页面集成测试 (HEALTH-01, 02, 03 核心用例覆盖)
```

### Pattern 1: 全量元数据动态生成与字典统一 (HEALTH-01)
**What:** 移除页面内硬编码的 7 项静态 `<el-option>` 及私有字典 `metricUnitMap`，由 Pinia `useMetricMetaStore().healthMetrics` 动态驱动。  
**When to use:** 顶部筛选栏下拉选择器、添加/编辑指标弹窗中的下拉选择器。  
**Verifiable Source Quote:**
> [VERIFIED: hhs-frontend-v2/src/views/health/Metrics.vue:24-30]
> ```html
> <el-option label="血糖" value="glucose" />
> <el-option label="收缩压" value="systolicBP" />
> <el-option label="舒张压" value="diastolicBP" />
> <el-option label="心率" value="heartRate" />
> <el-option label="体温" value="temperature" />
> <el-option label="体重" value="weight" />
> <el-option label="BMI" value="bmi" />
> ```
> [VERIFIED: hhs-frontend-v2/src/views/health/Metrics.vue:188-196]
> ```ts
> const metricUnitMap: Record<string, string> = {
>   glucose: 'mmol/L',
>   systolicBP: 'mmHg',
>   diastolicBP: 'mmHg',
>   heartRate: '次/分',
>   temperature: '°C',
>   weight: 'kg',
>   bmi: ''
> }
> ```
> [VERIFIED: hhs-frontend-v2/src/stores/metricMeta.ts:18-20]
> ```ts
> const healthMetrics = computed<MetricDefinitionVO[]>(() =>
>   allDefinitions.value.filter(d => d.category === 'HEALTH')
> )
> ```

**Replacement Pattern:**
```html
<!-- 顶部筛选栏 -->
<el-select v-model="filters.metricKey" placeholder="全部" clearable filterable style="width: 180px">
  <el-option
    v-for="item in metricMetaStore.healthMetrics"
    :key="item.canonicalKey"
    :label="item.displayName"
    :value="item.canonicalKey"
  />
</el-select>

<!-- 弹窗表单 -->
<el-select
  v-model="form.metricKey"
  :disabled="editingId !== null"
  placeholder="请选择指标类型"
  filterable
  style="width: 100%"
  @change="handleMetricKeyChange"
>
  <el-option v-if="editingId === null" label="血压 (高低压协同)" value="bloodPressure" />
  <el-option
    v-for="item in metricMetaStore.healthMetrics"
    :key="item.canonicalKey"
    :label="item.displayName"
    :value="item.canonicalKey"
  />
</el-select>
```

### Pattern 2: 血压协同录入与单项编辑精准适配 (HEALTH-02)
**What:** 区分新增与编辑模式：新增时若选择血压（`form.metricKey === 'bloodPressure'`），展示收缩压与舒张压并列网格，支持倒置检测与原子对调，保存时 `Promise.all` 提交；编辑历史记录时，根据当前记录的 `metricKey`（如单独编辑 `systolicBP` 或 `diastolicBP`）只渲染单项输入，禁用类型修改，单条记录更新。  
**When to use:** 解决数据库中每条记录为独立行与用户实际录入血压时需要高低压同屏填写的协同矛盾。

### Pattern 3: 四梯度宽容校验与危急值确认闭环 (HEALTH-03)
**What:** 接入 `useMetricValidation`，输入数值变化实时派生四梯度状态（正常/预警偏差/急危重症/存活极限）；Tier 1 与 Tier 2 直接放行保存；Tier 3 触碰 Panic Limits 时在点击保存前弹出 `CriticalConfirmDialog` 模态窗，确认后入库；Tier 4 超出生理存活极限时拦截提交，但原输入文本原样保留在输入框中，绝不失焦清空。  
**Verifiable Source Quote:**
> [VERIFIED: hhs-frontend-v2/src/utils/metricValidation.ts:3-13]
> ```ts
> export type MetricTier = 'NORMAL' | 'WARNING' | 'CRITICAL' | 'HARD_BOUND'
> 
> export interface MetricEvaluation {
>   tier: MetricTier
>   status: 'success' | 'warning' | 'danger' | 'hard_danger'
>   badgeText: string
>   message: string
>   canSave: boolean
>   isCritical: boolean
>   isHardLimit: boolean
> }
> ```
> [VERIFIED: hhs-frontend-v2/src/utils/metricValidation.ts:19-28]
> ```ts
> export const PANIC_LIMITS: Record<string, { min?: number; max?: number }> = {
>   systolicBP: { min: 70, max: 180 },
>   diastolicBP: { min: 40, max: 110 },
>   bloodGlucose: { min: 2.8, max: 16.7 },
>   fastingGlucose: { min: 2.8, max: 16.7 },
>   postprandialGlucose: { min: 2.8, max: 16.7 },
>   heartRate: { min: 45, max: 140 },
>   bodyTemperature: { min: 35.0, max: 39.5 },
>   bloodOxygen: { min: 90 } // 血氧 <= 90% 即为危急重症低氧血症
> }
> ```

### Pattern 4: GSAP 动画层叠上下文隔离 (D-04 UI-SPEC)
**What:** 在 `Metrics.vue` 中，外层卡片使用了 `scrollReveal('.el-card', { y: 16 })`，这会在 `.el-card` 上设置 CSS `transform` 样式。在 CSS 规范中，任何非 `none` 的 `transform` 会为子元素创建新的局部包含块（containing block），导致内嵌的 `position: fixed` 的 `el-dialog` 无法相对于浏览器视口居中覆盖，而是被禁锢在卡片内产生视觉偏移和蒙层错位。  
**Resolution:** 给 `el-dialog` 显式设置 `append-to-body` 和 `destroy-on-close`，将其 DOM 节点提升至 `document.body`，彻底规避包含块限制。

### Anti-Patterns to Avoid
- **失焦清空或重置数值 (`form.value = 0` / `form.value = undefined`)：** 任何校验失败或指标切换时粗暴清空用户刚输入的数据属于严重反模式，违背 VAL-01。
- **将真实病理异常作为表单错误拦截：** 如收缩压 170 mmHg、心率 120 次/分属于真实病理状态，严禁用表单红色报错阻止保存。
- **在编辑单项收缩压时错误强制要求填报舒张压：** 历史表格中单条记录是独立的，单项编辑如果强行展示舒张压并将其设为必填，会导致用户无法更新单条历史数据。
- **私自维护组件内部的指标单位或精度字典：** 导致和系统元数据脱节，严禁定义新的 `metricUnitMap` 或私有范围常数。

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 临床指标四梯度分级与危急值标准 | 在页面写 `if (val > 180)` | `useMetricValidation` / `evaluateMetricValue` | 23 项指标的医学极限与 Panic Limits 属于专业领域模型，手写极易遗漏血氧、高低压或临界值 |
| 血压倒置判断与高低压数据互换 | 自行在组件内写交换临时变量与监听 | `useMetricValidation` 导出的 `isInverted` 与 `swapBloodPressure` | 现成组合函数内置原子对调、清除表单验证状态与响应式同步 |
| 临床参考指南与生理区间卡片 | 在组件内拼接 HTML 卡片 | `ReferenceGuideCard.vue` | 现成组件已完备支持单项指标及血压双区间网格展示、注意事项提示 |
| 严重危急值二次确认与急救指引 | 自写 `ElMessageBox.confirm` 纯文字框 | `CriticalConfirmDialog.vue` | 具备防误触警示、120急救指南、紧急处置建议和标准化确认交互 |
| 指标显示名称与单位格式化 | 在模板写三元表达式或私有 mapping | `@/utils/format.ts` (`getMetricLabel`, `getMetricUnit`) | 自动兼容 camelCase、snake_case、历史别名及未知指标优雅回退 |

## Runtime State Inventory

本阶段为纯前端组件重构与体验对齐，不涉及数据库重命名、表结构迁移或系统环境变量重命名：

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None | 数据库指标表结构已在 Phase 4 完成 Key 归一化，后端 `HealthMetricRequest` 已支持别名自动映射，无需数据迁移 |
| Live service config | None | 无 live 外部服务需配置变更 |
| OS-registered state | None | 无操作系统注册项 |
| Secrets/env vars | None | 环境变量无须变动 |
| Build artifacts | None | 纯 Vue 组件与 Composable 更新，常规构建即可 |

## Common Pitfalls

### Pitfall 1: 编辑模式误触血压协同导致单条记录无法更新
**What goes wrong:** 用户在列表中点击单条“收缩压 (130 mmHg)”记录的编辑按钮时，页面如果依然判断 `canonicalKey === 'systolicBP'` 为血压并渲染出“收缩压”与“舒张压”两个输入框，由于原始记录中根本没有舒张压数据，表单将报舒张压为空拦截提交，或者更新时意外覆盖或无法保存。  
**Why it happens:** 忽略了新增与编辑的模式差异。新增是面向用户录入现实，而编辑是面向历史数据修补。  
**How to avoid:** 显式引入 `isCollaborative` 计算属性，仅当 `editingId === null && (form.metricKey === 'bloodPressure' || form.metricKey === 'systolicBP')` 时启用双字段协同；当 `editingId !== null` 时强制关闭协同，退化为针对单项指标的标准四梯度评估。  
**Warning signs:** 编辑收缩压记录时界面出现了舒张压输入框或报错“请输入舒张压”。

### Pitfall 2: GSAP 动画导致弹窗定位偏移与蒙层覆盖不全
**What goes wrong:** 打开添加/编辑指标弹窗时，弹窗偏向右下方或被局限在卡片宽度内，背景遮罩无法全屏覆盖。  
**Why it happens:** `Metrics.vue` 挂载了 `scrollReveal('.el-card', { y: 16 })`，GSAP 为 `.el-card` 注入了 `transform: translate(0px, 0px)`，在 CSS 渲染树中创建了新的包含块，破坏了 `<el-dialog>` 默认的 `position: fixed` 全屏基准。  
**How to avoid:** 给 `<el-dialog>` 显式添加 `append-to-body` 属性。  
**Warning signs:** 滚动页面时弹窗跟随卡片滚动，或者弹窗宽度受制于外层卡片。

### Pitfall 3: 血压数值倒置自纠后表单校验残留红色报错
**What goes wrong:** 用户误将高压填为 80、低压填为 120，触发倒置黄色警告与表单拦截；点击“对调高低压数值”后，数值已正确变为 120 与 80，但表单下方依旧残留红字错误提示。  
**Why it happens:** 数值虽然对调了，但 Element Plus 的 `FormItem` 校验状态并未自动重新触发或清除。  
**How to avoid:** 监听 `isInverted` 状态，当其从 `true` 转为 `false` 时，主动调用 `formRef.value?.clearValidate(['value', 'diastolicValue'])`，并在 `handleSwap` 中主动触发 `formRef.value?.validateField(['value', 'diastolicValue'])`。

### Pitfall 4: 0 值被误判为空值拦截
**What goes wrong:** 如果用户输入 0 或者某些特殊指标，被判定为无输入。  
**Why it happens:** 使用 `if (!value)` 进行非空校验，导致 `value = 0` 被当作假值。  
**How to avoid:** 校验必须使用严格判断 `if (value === undefined || value === null || isNaN(value))`。

## Code Examples

### 1. `useMetricValidation.ts` 扩展协同支持
```typescript
// Source: hhs-frontend-v2/src/composables/useMetricValidation.ts
import { computed, type Ref } from 'vue'
import { useMetricMetaStore } from '@/stores/metricMeta'
import { toCanonicalKey } from '@/utils/metricNormalizer'
import {
  evaluateMetricValue,
  checkBloodPressureInversion,
  type MetricEvaluation
} from '@/utils/metricValidation'
import type { MetricDefinitionVO } from '@/types/api'

export function useMetricValidation(
  metricKeyRef: Ref<string | undefined>,
  valueRef: Ref<number | undefined>,
  diastolicRef?: Ref<number | undefined>,
  collaborativeRef?: Ref<boolean> // 新增可选协作支持标志，缺省为 true (向后兼容)
) {
  const metaStore = useMetricMetaStore()

  // 规范化指标标识符
  const canonicalKey = computed<string>(() => {
    const raw = metricKeyRef.value || ''
    if (raw === 'bloodPressure' || raw === 'blood_pressure' || raw.toLowerCase() === 'bp') {
      return 'systolicBP'
    }
    return toCanonicalKey(raw)
  })

  // 判定是否为血压协同指标 (当 collaborativeRef 为 false 时，即使是 systolicBP 也按单项处理)
  const isBloodPressure = computed<boolean>(() => {
    const isCollab = collaborativeRef ? collaborativeRef.value : true
    return isCollab && canonicalKey.value === 'systolicBP'
  })

  // 主指标权威元数据定义
  const definition = computed<MetricDefinitionVO | undefined>(() => {
    if (!metricKeyRef.value) return undefined
    return metaStore.getDefinition(canonicalKey.value)
  })

  // 舒张压权威元数据定义
  const diastolicDef = computed<MetricDefinitionVO | undefined>(() => {
    if (!isBloodPressure.value) return undefined
    return metaStore.getDefinition('diastolicBP')
  })

  // 主指标四梯度生理评估
  const primaryEval = computed<MetricEvaluation | null>(() => {
    return evaluateMetricValue(valueRef.value, definition.value)
  })

  // 舒张压四梯度生理评估
  const diastolicEval = computed<MetricEvaluation | null>(() => {
    if (!isBloodPressure.value || !diastolicRef) return null
    return evaluateMetricValue(diastolicRef.value, diastolicDef.value)
  })

  // 血压生理倒置异常判定（收缩压 <= 舒张压）
  const isInverted = computed<boolean>(() => {
    if (!isBloodPressure.value || !diastolicRef) return false
    return checkBloodPressureInversion(valueRef.value, diastolicRef.value)
  })

  // 是否触碰急危重症 Panic Limits（主值或舒张压任一触发）
  const hasCritical = computed<boolean>(() => {
    return (primaryEval.value?.isCritical ?? false) || (diastolicEval.value?.isCritical ?? false)
  })

  // 保存阻断状态：仅在触碰 Tier 4 生理不可能极限或血压倒置时阻断保存
  const isSaveBlocked = computed<boolean>(() => {
    if (primaryEval.value?.isHardLimit) return true
    if (diastolicEval.value?.isHardLimit) return true
    if (isInverted.value) return true
    return false
  })

  // 血压高低压数值原子一键对调方法
  const swapBloodPressure = () => {
    if (!isBloodPressure.value || !diastolicRef) return
    const temp = valueRef.value
    valueRef.value = diastolicRef.value
    diastolicRef.value = temp
  }

  return {
    canonicalKey,
    isBloodPressure,
    definition,
    diastolicDef,
    primaryEval,
    diastolicEval,
    isInverted,
    hasCritical,
    isSaveBlocked,
    swapBloodPressure
  }
}
```

### 2. `Metrics.vue` 弹窗与校验集成骨架
```html
<!-- Source: hhs-frontend-v2/src/views/health/Metrics.vue -->
<template>
  <div class="metrics-page">
    <el-card>
      <!-- 筛选区: 动态渲染 16 项指标 -->
      <div class="filter-bar">
        <el-form :inline="true" :model="filters">
          <el-form-item label="指标类型">
            <el-select
              v-model="filters.metricKey"
              placeholder="全部"
              clearable
              filterable
              style="width: 180px"
            >
              <el-option
                v-for="item in metricMetaStore.healthMetrics"
                :key="item.canonicalKey"
                :label="item.displayName"
                :value="item.canonicalKey"
              />
            </el-select>
          </el-form-item>
          <!-- ... 其他筛选条件保持不变 ... -->
        </el-form>
      </div>

      <!-- 表格数据展示 -->
      <el-table v-loading="loading" :data="metrics" stripe>
        <el-table-column prop="metricKey" label="指标类型" width="130">
          <template #default="{ row }">
            {{ row.metricName || getMetricLabel(row.metricKey) }}
          </template>
        </el-table-column>
        <!-- ... 其余列保持不变 ... -->
      </el-table>
    </el-card>

    <!-- 添加/编辑对话框 (append-to-body, label-position="top") -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="560px"
      append-to-body
      destroy-on-close
      class="metrics-dialog"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
        <!-- 指标类型选择 (编辑时禁用切换) -->
        <el-form-item label="指标类型" prop="metricKey">
          <el-select
            v-model="form.metricKey"
            :disabled="editingId !== null"
            placeholder="请选择指标类型"
            filterable
            style="width: 100%"
            @change="handleMetricKeyChange"
          >
            <el-option v-if="editingId === null" label="血压 (高低压协同)" value="bloodPressure" />
            <el-option
              v-for="item in metricMetaStore.healthMetrics"
              :key="item.canonicalKey"
              :label="item.displayName"
              :value="item.canonicalKey"
            />
          </el-select>
        </el-form-item>

        <!-- 血压双字段同屏并列排版 (仅新增协同模式激活) -->
        <template v-if="isBloodPressure">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="收缩压 (高压)" prop="value">
                <el-input-number
                  v-model="form.value"
                  :precision="0"
                  :step="1"
                  :placeholder="placeholder"
                  style="width: 100%"
                />
                <div v-if="primaryEval" class="tier-pill-wrapper">
                  <el-tag :type="getTagType(primaryEval.status)" size="small" effect="light">
                    <el-icon><component :is="getTagIcon(primaryEval.status)" /></el-icon>
                    <span>{{ primaryEval.badgeText }}</span>
                  </el-tag>
                </div>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="舒张压 (低压)" prop="diastolicValue">
                <el-input-number
                  v-model="form.diastolicValue"
                  :precision="0"
                  :step="1"
                  :placeholder="diastolicPlaceholder"
                  style="width: 100%"
                />
                <div v-if="diastolicEval" class="tier-pill-wrapper">
                  <el-tag :type="getTagType(diastolicEval.status)" size="small" effect="light">
                    <el-icon><component :is="getTagIcon(diastolicEval.status)" /></el-icon>
                    <span>{{ diastolicEval.badgeText }}</span>
                  </el-tag>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 血压倒置警告与一键对调按钮 -->
          <el-alert
            v-if="isInverted"
            type="warning"
            show-icon
            :closable="false"
            class="inversion-alert"
          >
            <template #title>
              <div class="inversion-banner-title">
                <span>{{ isBpEqual ? '检测到收缩压与舒张压数值相同，请核对后修改' : '检测到收缩压低于舒张压，疑似输入颠倒' }}</span>
                <el-button
                  v-if="!isBpEqual"
                  size="small"
                  type="warning"
                  plain
                  :icon="Switch"
                  class="swap-btn"
                  @click="handleSwap"
                >
                  对调高低压数值
                </el-button>
              </div>
            </template>
          </el-alert>
        </template>

        <!-- 单项常规指标 / 单项历史编辑排版 -->
        <template v-else>
          <el-form-item :label="(definition?.displayName || '测量数值')" prop="value">
            <el-input-number
              v-model="form.value"
              :precision="precision"
              :step="step"
              :placeholder="placeholder"
              style="width: 100%"
            />
            <div v-if="primaryEval" class="tier-pill-wrapper">
              <el-tag :type="getTagType(primaryEval.status)" size="small" effect="light">
                <el-icon><component :is="getTagIcon(primaryEval.status)" /></el-icon>
                <span>{{ primaryEval.badgeText }}</span>
              </el-tag>
              <span class="eval-message">{{ primaryEval.message }}</span>
            </div>
          </el-form-item>
        </template>

        <!-- 单位、记录日期与趋势行 -->
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="单位">
              <el-input :value="definition?.unit || form.unit || ''" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="记录日期" prop="recordDate">
              <el-date-picker
                v-model="form.recordDate"
                type="date"
                placeholder="选择日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="趋势变化 (可选)" prop="trend">
              <el-select v-model="form.trend" placeholder="请选择" clearable style="width: 100%">
                <el-option label="上升" value="UP" />
                <el-option label="下降" value="DOWN" />
                <el-option label="稳定" value="STABLE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 临床生理参考指南解读卡片 -->
        <ReferenceGuideCard
          :definition="definition"
          :diastolic-def="isBloodPressure ? diastolicDef : undefined"
        />
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="isSaveBlocked"
          @click="handleSubmit"
        >
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 急危重症二次确认模态窗 -->
    <CriticalConfirmDialog
      v-model="criticalDialogVisible"
      :metric-name="criticalMetricName"
      :value="criticalMetricValue"
      :unit="criticalMetricUnit"
      @confirm="executeSave"
    />
  </div>
</template>
```

## State of the Art

| Old Approach (`Metrics.vue` 现存实现) | Current Approach (Phase 08 重构目标) | When Changed | Impact |
|-------------------------------------|-------------------------------------|--------------|--------|
| 写死 7 项 `el-option`（`glucose`, `systolicBP` 等） | Pinia `useMetricMetaStore().healthMetrics` 动态生成 16 项指标 | 2026-11 | 消除硬编码，完整覆盖血脂四项、尿酸、肌酐、血氧等现代医学指标 |
| 页面内私有字典 `metricUnitMap` | 权威元数据注册表 `definition.unit` 驱动 | 2026-11 | 保证全系统单位单一事实来源，消除维护不同步 |
| 血压收缩压和舒张压割裂录入，容易倒置 | 新增支持“血压”协同双字段排版，倒置检测与原子对调自纠 | 2026-11 | 避免误将舒张压当作收缩压录入造成的临床数据污染 |
| 缺乏急危重症二次确认，无临床指南卡片 | 挂载 `ReferenceGuideCard` 与 `CriticalConfirmDialog` | 2026-11 | 建立专业临床解释反馈体系，兼顾真实病理留存与防误触急救指引 |
| `<el-dialog>` 挂在带有 GSAP transform 的卡片内 | `<el-dialog append-to-body label-position="top">` | 2026-11 | 彻底解决弹窗被局限在卡片内的层叠上下文偏移 Bug |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 数据库中历史记录的血压数据是以单条 `systolicBP` 和 `diastolicBP` 分开存储的行记录 | Architecture Patterns | 极低（已在 `HealthMetricVO`、`HealthMetricRequest` 及后端 Controller 中核实） |
| A2 | 用户在表格中点击单行“编辑”时，仅需更新该行的单一指标值，无需强制关联修改另一行血压数据 | Architecture Patterns / D-02 | 极低（已在 D-02 锁定决策中明确约定编辑模式精准适配单项记录） |

## Open Questions

1. **表格中血压指标行是否需要在未来版本聚合成单一复合行？**
   - What we know: 数据库中每条记录有唯一的 `id`，历史数据各自带时间戳。目前列表中分别显示收缩压行和舒张压行。
   - What's unclear: 是否需要把同一时间段记录的收缩压与舒张压前端聚合成“血压 120/80 mmHg”。
   - Recommendation: 本阶段严格聚焦于“录入与编辑对齐”，表格展示继续按行渲染，在 Phase 10 通用组件或后续里程碑中再考虑视图聚合展示。

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | 前端构建与测试运行 | ✓ | v24.15.0 | — |
| npm | 包管理与脚本执行 | ✓ | 11.12.1 | — |
| Vitest | 前端自动化测试 | ✓ | 4.1.0 | — |
| Vue 3 / Element Plus | 前端核心框架 | ✓ | 3.4.15 / 2.5.6 | — |

**Missing dependencies with no fallback:** None.  
**Missing dependencies with fallback:** None.  

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.1.0 + @vue/test-utils 2.4.6 |
| Config file | `hhs-frontend-v2/vite.config.ts` |
| Quick run command | `cd hhs-frontend-v2 && npm run test:run` |
| Full suite command | `cd hhs-frontend-v2 && npm run test:run && npm run build` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| HEALTH-01 | 筛选栏和对话框指标选择器由 `useMetricMetaStore` 动态渲染 16 项健康指标，无硬编码 7 项 | unit/integration | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/Metrics.spec.ts` | ❌ Wave 0 (待创建) |
| HEALTH-01 | 动态绑定指标单位、推荐步长、小数精度与参考范围占位符 | unit | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/Metrics.spec.ts` | ❌ Wave 0 (待创建) |
| HEALTH-02 | 新增模式选择血压时展示双字段协同并列排版，倒置警示检测与原子对调正常工作 | unit/integration | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/Metrics.spec.ts` | ❌ Wave 0 (待创建) |
| HEALTH-02 | 编辑模式针对历史单项指标（如单独编辑收缩压）禁用类型切换，只展示单项字段评估，不触发双字段协同 | unit | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/Metrics.spec.ts` | ❌ Wave 0 (待创建) |
| HEALTH-02 | 血压协同保存时调用 `Promise.all` 同时创建收缩压与舒张压记录 | unit | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/Metrics.spec.ts` | ❌ Wave 0 (待创建) |
| HEALTH-03 | Tier 2 预警偏差数值（如血糖 9.5 mmol/L）放行保存，状态胶囊正常呈现且数值绝不失焦清空 | unit | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/Metrics.spec.ts` | ❌ Wave 0 (待创建) |
| HEALTH-03 | Tier 3 急危重症数值（如收缩压 190 mmHg）触发 `CriticalConfirmDialog` 二次确认弹窗，确认后放行保存 | unit | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/Metrics.spec.ts` | ❌ Wave 0 (待创建) |
| HEALTH-03 | Tier 4 生理不可能极限（如心率 400 次/分）阻断保存按钮，但原输入数值忠实保留 | unit | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/Metrics.spec.ts` | ❌ Wave 0 (待创建) |

### Sampling Rate
- **Per task commit:** `cd hhs-frontend-v2 && npm run test:run`
- **Per wave merge:** `cd hhs-frontend-v2 && npm run test:run && npm run build`
- **Phase gate:** 全部 Vitest 单元与组件测试用例 100% 绿灯且 `npm run build` 成功。

### Wave 0 Gaps
- [ ] `hhs-frontend-v2/src/__tests__/views/Metrics.spec.ts` — 覆盖 HEALTH-01, HEALTH-02, HEALTH-03 的完整页面测试套件
- [ ] `hhs-frontend-v2/src/__tests__/composables/useMetricValidation.spec.ts` — 补充协同开关 `isCollaborativeRef` 验证用例

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | 前端请求自动附加 JWT Bearer token（`@/utils/request.ts`） |
| V3 Session Management | yes | 401 响应拦截自动触发登出与页面跳转 |
| V4 Access Control | yes | 后端基于当前用户会话校验指标归属，前端传递合法载荷 |
| V5 Input Validation | yes | 前端四梯度校验 + 存活极限物理拦截（Tier 4），后端 Jakarta class-level 校验兜底 |
| V6 Cryptography | no | 指标数值属于普通健康数值，不涉及密码学或敏感密钥加密 |

### Known Threat Patterns for Vue3 / Element Plus Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| XSS injection in metric units or note | Tampering | Element Plus 默认转义，文本插值使用 `{{ }}`，严禁对用户或接口返回文案使用 `v-html` |
| Bypassing client-side limits | Tampering | 客户端校验仅作为 UX 引导，后端 `@ValidMetricRecord` 提供同构物理极限刚性拦截 |
| CSRF on metric creation | Spoofing | 采用 JWT 放在 `Authorization: Bearer <token>` 头部，不依赖跨站 Cookie |

## Sources

### Primary (HIGH confidence)
- `hhs-frontend-v2/src/views/health/Metrics.vue` - 审查现存写死下拉框、`metricUnitMap`、表单提交与动画挂载
- `hhs-frontend-v2/src/stores/metricMeta.ts` - 审查 `useMetricMetaStore` 与 `healthMetrics` 16 项指标响应式列表
- `hhs-frontend-v2/src/constants/metricMeta.ts` - 审查权威离线元数据字典与生理极限
- `hhs-frontend-v2/src/utils/metricValidation.ts` - 审查四梯度纯函数评估逻辑与 `PANIC_LIMITS`
- `hhs-frontend-v2/src/composables/useMetricValidation.ts` - 审查响应式评估、血压倒置检测与原子对调机制
- `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` - 审查 Phase 6 已落地的标准实现与交互规范
- `hhs-frontend-v2/src/views/data-input/components/ReferenceGuideCard.vue` - 审查常态化参考指南卡片属性与展示
- `hhs-frontend-v2/src/views/data-input/components/CriticalConfirmDialog.vue` - 审查急危重症二次确认模态窗交互
- `hhs-frontend-v2/src/__tests__/views/QuickInputDialog.spec.ts` - 审查 Vitest 组件模拟测试规范与桩定义

### Secondary (MEDIUM confidence)
- `CLAUDE.md` - 前端构建、测试与代码规范要求

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - 既有 Vue 3, Element Plus, Pinia, Vitest 稳定生态
- Architecture: HIGH - 模式划分（新增双字段协同 vs 编辑单项精准适配）清晰严谨，完全符合数据库实体与医疗实际
- Pitfalls: HIGH - 已深入排查 GSAP transform 动画上下文、倒置表单校验残留及单项编辑误触协同等潜在陷阱

**Research date:** 2026-11-05  
**Valid until:** 2026-12-05 (稳定架构)  
