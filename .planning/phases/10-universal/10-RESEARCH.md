# Phase 10: 指标录入通用交互组件封装与全站验证 - Research

**Researched:** 2026-11-05  
**Domain:** Vue 3 / Element Plus 通用指标录入组件封装、编辑模式自适应流转与全站体验一致性验证  
**Confidence:** HIGH  

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### 1. 完整通用弹窗组件提炼封装 (REUSE-01)
- **D-01 (完整通用弹窗封装):**
  - 创建 `@/components/metric/UniversalMetricDialog.vue`（并在 `@/views/data-input/components/` 建立重导出以保持已有引用的向后兼容）；
  - Props 接收：
    - `modelValue: boolean`（控制弹窗显隐）；
    - `category?: 'HEALTH' | 'WELLNESS' | 'ALL'`（指标大类过滤，缺省为 ALL）；
    - `defaultMetricKey?: string`（默认选中的指标 Key，如快捷录入时传入）；
    - `initialData?: HealthMetricVO | WellnessMetricVO | null`（用于编辑模式的历史记录对象）；
  - Emits 触发 `update:modelValue` 与 `success`；
  - 内部完整继承已在 Phase 6/8/9 验证成熟的四梯度校验、血压双向网格与对调、心情星级打分、`ReferenceGuideCard`、`CriticalConfirmDialog`、`append-to-body` 全局平铺与 `label-position="top"` 排版规范。

#### 2. 统一编辑模式与自适应 API 路由 (REUSE-02)
- **D-02 (自适应 API 路由与编辑流转):**
  - 当 `initialData` 存在且包含 `id` 时，自动判定为编辑模式：弹窗标题呈现“编辑[指标名]”，指标下拉框置灰禁用，自动关闭血压协同模式，按单项指标精准评估；
  - 提交数据时，组件内部优先从权威元数据 `definition.value?.category` 判定指标类型：
    - 若为 `WELLNESS`：新增调用 `wellnessApi.createMetric`，编辑调用 `wellnessApi.updateMetric(id, data)`；
    - 若为 `HEALTH`：新增血压协同调用 `Promise.all([healthApi.createMetric, healthApi.createMetric])`，常规新增调用 `healthApi.createMetric`，编辑调用 `healthApi.updateMetric(id, data)`；
  - 保存成功后触发 `emit('success')` 并关闭弹窗，父页面仅需监听 `@success` 重新拉取数据列表，无需在各个页面重复编写复杂的提交分支。

#### 3. 全站业务页面全面收敛复用 (REUSE-01)
- **D-03 (三大业务页面收敛复用):**
  - `src/views/data-input/components/QuickInput.vue` 直接使用 `UniversalMetricDialog` 替换原 `QuickInputDialog`；
  - `src/views/health/Metrics.vue` 移除页面内重复的 `<el-dialog>` 及其内部表单字段，直接复用 `<UniversalMetricDialog category="HEALTH" ... />`；
  - `src/views/prevention/Metrics.vue` 移除页面内重复的 `<el-dialog>` 及其内部表单字段，直接复用 `<UniversalMetricDialog category="WELLNESS" ... />`；
  - `src/views/wellness/Dashboard.vue` 移除页面内重复的 `<el-dialog>` 及其内部表单字段，直接复用 `<UniversalMetricDialog category="WELLNESS" ... />`；
  - 全站消除 600+ 行重复的弹窗、表单、状态胶囊与校验逻辑代码，实现“一次修改，全站受益”。

#### 4. 全站端到端集成测试矩阵与回归保证
- **D-04 (全量录入入口回归矩阵):**
  - 编写 `UniversalMetricDialog.spec.ts` 全覆盖单元测试（覆盖 HEALTH、WELLNESS、血压协同、心情评星、新增模式、编辑模式、四梯度拦截与危急值确认）；
  - 保持现有各大页面的单测（`Metrics.spec.ts`、`PreventionAndWellness.spec.ts`、`QuickInputDialog.spec.ts`）全部绿灯通过，确保重构零破坏、零回归。

### Claude's Discretion
- `UniversalMetricDialog.vue` 的公共组件存放目录（推荐 `@/components/metric/`，并在 `@/views/data-input/components/` 建立重导出以保持已有引用的向后兼容）；
- 弹窗针对不同业务场景的标题自适应定制（例如传入可选 `title` 属性覆盖默认标题）。

### Deferred Ideas (OUT OF SCOPE)
- **v1.3+ 规划：** 将 AI 智能文本解析与 OCR 报告识别对齐到权威元数据注册中心（SYNC-01）。
- **v1.3+ 规划：** 个性化基线定制（结合病史针对特定患者放宽或收紧正常区间）。
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REUSE-01 | 提炼/封装可复用的健康与保健指标录入组件（或基于 `QuickInputDialog` 模式抽象共享组件），统一健康指标与保健指标的表单排版、`ReferenceGuideCard` 与 `CriticalConfirmDialog` 交互，消除三个页面间的重复代码 | 本研究明确了在 `src/components/metric/UniversalMetricDialog.vue` 提炼全功能组件，将 `QuickInput.vue`、`health/Metrics.vue`、`prevention/Metrics.vue` 及 `wellness/Dashboard.vue` 中重复分散的 700+ 行模板与逻辑代码完全收敛，各页面仅需 5-10 行标签声明即可复用全部医学输入能力。并在 `QuickInputDialog.vue` 建立向前兼容重导出。 |
| REUSE-02 | 指标编辑模式（修改既有记录）完整接入四梯度校验与元数据绑定，防止编辑历史数据时出现破坏性清空或校验规则不一致 | 本研究设计了基于 `initialData` 存在性的自适应编辑流转架构：自动解析 `id` 并锁定为编辑模式，禁用指标类型修改，自动关闭血压协同双字段编辑（历史记录为单条指标记录精准更新），拉取权威定义并绑定四梯度校验与危急值二次确认，底层自适应分流调用 `healthApi.updateMetric` 或 `wellnessApi.updateMetric`。 |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- **Frontend Tech Stack:** Vue 3.4+, TypeScript 5.5+, Vite 5, Element Plus 2.5+, Pinia 2.1+, Vitest 4.1+.
- **Frontend Code Style:** ESLint 9 + Prettier 3. 2-space indentation. Vue 3 Composition API with `<script setup lang="ts">`.
- **Response Structure:** Backend returns `Result<T>` with `code: 200` for success. Frontend wraps in `ApiResponse<T>` (`{ code: number, message: string, data: T }`).
- **HTTP Client:** `@/utils/request.ts` wrapping Axios with automatic token attachment and 401 handling.
- **Auto-import:** `unplugin-auto-import` and `unplugin-vue-components` configured for Element Plus and Vue reactivity primitives (`ref`, `computed`, etc.).
- **Testing Standard:** Vitest + `@vue/test-utils` for unit and component testing (`npm run test:run`). Must run with zero failures.

## Summary

Phase 10 作为 Milestone v1.2 的收官阶段，其核心使命是**架构提炼（Architectural Consolidation）**与**体验一致性闭环（UX Cohesion）**。在过去的 Phase 6、Phase 8 与 Phase 9 中，平台先后在快捷录入弹窗、健康指标管理页以及保健指标管理页中实现了四梯度宽容生理校验、血压同屏协同与一键对调、心情星级打分、常态化解读卡片和危急值二次确认。然而，由于这些能力在各页面中是以独立 `<el-dialog>` 形式复制集成的，导致全站出现了超过 700 行高度相似的表单字段、排版网格、校验规则与弹窗状态控制模板。

通过在 `src/components/metric/UniversalMetricDialog.vue` 封装通用指标录入组件，全站业务页面（`src/views/health/Metrics.vue`、`src/views/prevention/Metrics.vue`、`src/views/wellness/Dashboard.vue` 和 `src/views/data-input/components/QuickInput.vue`）将完全剔除重复的表单与弹窗模板代码，直接复用通用组件。组件内聚了全套医学表单交互规范：
1. **多分类支持与动态过滤**：支持 `category: 'HEALTH' | 'WELLNESS' | 'ALL'` 灵活过滤指标选项，配合 `useMetricMetaStore` 动态拉取权威元数据；
2. **场景自适应输入界面**：新增模式支持血压高低压双字段同屏并列与一键对调（GUIDE-03, VAL-04），心情指标专属 1-5 星级打分（WELL-02），常规指标动态步长/精度/占位符（GUIDE-01），健康指标趋势选择，保健指标备注输入；
3. **四梯度生理校验与安全确认**：完整内置 Tier 1 正常、Tier 2 预警胶囊、Tier 3 危急值二次确认模态窗（VAL-05）与 Tier 4 物理不可能极限拦截，绝不失焦清空键入原值（VAL-01）；
4. **自适应编辑流转与 API 路由 (REUSE-02)**：传入历史对象 `initialData` 自动切换为编辑模式，置灰锁定指标下拉框，精准更新单条历史记录；根据权威分类 `category` 自动分流调用 `healthApi` 或 `wellnessApi` 的新增或修改接口，保存成功触发 `emit('success')`。

**Primary recommendation:** 集中在 `src/components/metric/UniversalMetricDialog.vue` 封装完备的通用弹窗组件，并在 `src/views/data-input/components/QuickInputDialog.vue` 建立向前兼容重导出；各业务页面仅声明 `<UniversalMetricDialog v-model="visible" :category="..." :initial-data="row" @success="refresh" />`，消除 700+ 行重复代码；配套构建 `UniversalMetricDialog.spec.ts` 综合测试套件，确保全站现有 12 个测试文件、80+ 项自动化测试保持 100% 绿灯。

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Universal Modal Presentation (`UniversalMetricDialog.vue`) | Browser / Client Component | — | 负责顶层 `<el-dialog append-to-body>` 模态生命周期、`label-position="top"` 排版布局、标题自适应计算及 `v-model` 响应式同步。 |
| Physiological Validation & Tiers (`useMetricValidation.ts`) | Frontend Composable / Pure Logic | — | 响应式计算四梯度状态（NORMAL, WARNING, CRITICAL, HARD_BOUND）、血压倒置检测与急危重症 Panic 判定，纯函数无副作用。 |
| Metadata Registry & Dynamic Bounds (`useMetricMetaStore.ts`) | Frontend Pinia Store | Browser Local Cache | 集中式响应式元数据管理，为通用组件提供 23+ 种指标的标准 Key、单位、精度、步长与参考区间。 |
| Clinical Guidance (`ReferenceGuideCard.vue`) | Shared Client Component | — | 常态化展示临床参考区间、测量注意事项与血糖/血压专病提醒。 |
| Panic Modal Confirmation (`CriticalConfirmDialog.vue`) | Shared Client Component | — | 触碰急危重症阈值时阻断静默入库，弹出二次防误触确认窗及急救处置指引。 |
| Adaptive API Routing (`healthApi` vs `wellnessApi`) | Universal Component Action | Backend REST API | 通用组件内部依据指标定义 `definition.value.category` 自适应路由调用 `healthApi.createMetric` / `healthApi.updateMetric` 或 `wellnessApi.createMetric` / `wellnessApi.updateMetric`。 |
| Business View Integration (`health/Metrics`, `prevention/Metrics`, `wellness/Dashboard`, `QuickInput`) | View Layer | — | 消费通用组件，仅保留数据列表、过滤筛选与分页逻辑，彻底剥离弹窗与表单冗余代码。 |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `vue` | `^3.4.15` [VERIFIED: hhs-frontend-v2/package.json:28] | 前端核心框架 | 组合式 API (`<script setup>`)，计算属性与响应式状态 |
| `element-plus` | `^2.5.6` [VERIFIED: hhs-frontend-v2/package.json:23] | 企业级 UI 组件库 | `<el-dialog>`, `<el-form>`, `<el-input-number>`, `<el-rate>`, `<el-tag>`, `<el-select>` 等标准表单与模态组件 |
| `pinia` | `^2.1.7` [VERIFIED: hhs-frontend-v2/package.json:26] | 状态管理库 | 响应式提供 `useMetricMetaStore` 权威元数据字典与缓存 |
| `typescript` | `~5.5.0` [VERIFIED: hhs-frontend-v2/package.json:48] | 静态类型校验 | 强化 `HealthMetricVO`, `WellnessMetricVO`, `MetricDefinitionVO` 接口类型安全 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `@element-plus/icons-vue` | `^2.3.1` [VERIFIED: hhs-frontend-v2/package.json:18] | 矢量图标集 | 表单状态胶囊、警告提示条及一键对调按钮图标 (`Check`, `Warning`, `Bell`, `CircleClose`, `Switch`, `InfoFilled`) |
| `dayjs` | (transitive) | 日期处理库 | 默认填报日期与时间格式化 (`formatDate`, `formatDateTime`) |
| `@vue/test-utils` | `^2.4.6` [VERIFIED: hhs-frontend-v2/package.json:39] | Vue 单测工具库 | 挂载组件与事件模拟 (`mount`, `flushPromises`) |
| `vitest` | `^4.1.0` [VERIFIED: hhs-frontend-v2/package.json:52] | 单元测试框架 | 快速执行前端单元与组件测试矩阵 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 单一通用组件 + 内部自适应路由 | 两个独立组件 (`HealthMetricDialog` + `WellnessMetricDialog`) | 会保留 80% 相同的四梯度校验、状态胶囊、占位符、参考卡片与急危确认模态逻辑，无法解决全站录入规则零分裂的核心诉求。 |
| 在各页面直接维护表单 | 复制粘贴表单模板 | 造成全站 700+ 行重复代码，若未来新增字段（如测量时间段、场景标签），需修改 4 处以上文件，维护成本极高。 |

## Package Legitimacy Audit

本阶段为已有前端项目内部组件提炼与代码收敛重构，**不引入任何新增的外部 npm 包**。

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `vue` | npm | 10+ yrs | ~25M/wk | github.com/vuejs/core | [OK] | Approved (Existing: ^3.4.15) |
| `element-plus` | npm | 4+ yrs | ~770K/wk | github.com/element-plus/element-plus | [OK] | Approved (Existing: ^2.5.6) |
| `pinia` | npm | 4+ yrs | ~7.6M/wk | github.com/vuejs/pinia | [OK] | Approved (Existing: ^2.1.7) |
| `@element-plus/icons-vue` | npm | 3+ yrs | ~1.5M/wk | github.com/element-plus/element-plus-icons | [OK] | Approved (Existing: ^2.3.1) |

**Packages removed due to [SLOP] verdict:** None  
**Packages flagged as suspicious [SUS]:** None  

## Architecture Patterns

### System Architecture Diagram

```
+---------------------------------------------------------------------------------------------------+
|                                      User Trigger / Entry Points                                  |
|  [QuickInput Card]   [health/Metrics: 添加/编辑]   [prevention/Metrics: 添加/编辑]   [wellness/Dashboard] |
+---------------------------------------------------------------------------------------------------+
                                                  │
                                                  ▼
+───────────────────────────────────────────────────────────────────────────────────────────────────+
|                  UniversalMetricDialog.vue (src/components/metric/UniversalMetricDialog.vue)      |
|  Props: v-model, category ('HEALTH'|'WELLNESS'|'ALL'), defaultMetricKey, initialData, title        |
+───────────────────────────────────────────────────────────────────────────────────────────────────+
           │                                      │                                      │
           ▼                                      ▼                                      ▼
+──────────────────────+              +──────────────────────+              +───────────────────────+
|  Mode Determination  |              | Form Fields Render   |              | Dynamic Guidance &    |
| - initialData?.id    |              | - Blood Pressure     |              | Validation Feedback   |
|   -> Edit Mode:      |              |   (Systolic/Diastol) |              | - useMetricValidation |
|      Lock metricKey, |              |   + Swap Button      |              | - Tier 1..4 Badges    |
|      Single field.   |              | - Mood Rating (1..5) |              | - ReferenceGuideCard  |
| - No initialData?.id |              | - Numeric Input      |              | - CriticalConfirm     |
|   -> Add Mode:       |              |   (dynamic step/prec)|              |   Modal               |
|      Select metric.  |              | - Unit, Date, Notes  |              |                       |
+──────────────────────+              +──────────────────────+              +───────────────────────+
                                                  │
                                                  ▼
                                      [User Click "保存/确定"]
                                                  │
                                                  ▼
                                      Four-Tier Validation Gate
                                 ┌────────────────────────────────┐
                                 │ Tier 4 Hard Limit? -> Block    │
                                 │ BP Inversion?     -> Block     │
                                 │ Tier 3 Panic?     -> Open Modal│
                                 │ Tier 1/2 Pass     -> Save      │
                                 └────────────────────────────────┘
                                                  │
                                       [Confirmed / Validated]
                                                  │
                                                  ▼
                                      Adaptive API Router (D-02)
                          ┌───────────────────────┴───────────────────────┐
                          ▼                                               ▼
              [category === 'WELLNESS']                       [category === 'HEALTH']
              ┌───────────────────────┐                       ┌───────────────────────┐
              │ Edit Mode?            │                       │ Edit Mode?            │
              │  -> wellnessApi.update│                       │  -> healthApi.update  │
              │ Add Mode?             │                       │ Add Mode?             │
              │  -> wellnessApi.create│                       │  BP Collab:           │
              └───────────────────────┘                       │   Promise.all([       │
                          │                                   │     healthApi.create, │
                          │                                   │     healthApi.create  │
                          │                                   │   ])                  │
                          │                                   │  Regular:             │
                          │                                   │   healthApi.create    │
                          │                                   └───────────────────────┘
                          └───────────────────────┬───────────────────────┘
                                                  ▼
                                    Emit 'success' -> Close Dialog
                                                  │
                                                  ▼
                                    Parent Page Refresh Data List
```

### Recommended Project Structure
```
hhs-frontend-v2/src/
├── components/
│   └── metric/
│       └── UniversalMetricDialog.vue          # [REUSE-01, REUSE-02] 核心通用指标录入/编辑弹窗组件
├── views/
│   ├── data-input/
│   │   └── components/
│   │       ├── QuickInput.vue                 # 快捷录入组件（重构接入 UniversalMetricDialog）
│   │       ├── QuickInputDialog.vue           # [D-01] 保持向后兼容的包装重导出组件
│   │       ├── ReferenceGuideCard.vue         # 常态化临床参考标准解读卡片（已成熟）
│   │       └── CriticalConfirmDialog.vue      # 急危重症二次确认模态窗（已成熟）
│   ├── health/
│   │   └── Metrics.vue                        # [D-03] 健康指标管理页（彻底移除内嵌 el-dialog 模板与表单状态）
│   ├── prevention/
│   │   └── Metrics.vue                        # [D-03] 保健指标管理页（彻底移除内嵌 el-dialog 模板与表单状态）
│   └── wellness/
│       └── Dashboard.vue                      # [D-03] 保健指标仪表盘（彻底移除内嵌 el-dialog 模板与表单状态）
└── __tests__/
    ├── components/
    │   └── UniversalMetricDialog.spec.ts      # [D-04] 通用组件全场景全覆盖综合测试套件
    └── views/
        ├── QuickInputDialog.spec.ts           # 既有快捷录入测试（保持 100% 绿灯通过）
        ├── Metrics.spec.ts                    # 既有健康指标页测试（保持 100% 绿灯通过）
        ├── HealthMetrics.spec.ts              # 既有健康指标集成测试（保持 100% 绿灯通过）
        ├── PreventionAndWellness.spec.ts      # 既有保健管理页测试（保持 100% 绿灯通过）
        └── WellnessMetrics.spec.ts            # 既有保健指标集成测试（保持 100% 绿灯通过）
```

### In-Repo Discrete Values Citation (Provenance Rule)

为满足 Discrete Value Provenance 规范，本研究所依赖的系统在库类型、字段与枚举均已在本次会话中通过 `Read` 真实文件验证，绝对原样引用如下：

#### 1. MetricDefinitionVO 接口字段定义 [VERIFIED: hhs-frontend-v2/src/types/api.ts:424-438]
```ts
export interface MetricDefinitionVO {
  canonicalKey: string
  aliases: string[]
  displayName: string
  category: 'HEALTH' | 'WELLNESS'
  unit: string
  precision: number
  step: number
  hardMin: number
  hardMax: number
  normalMin: number
  normalMax: number
  placeholder: string
  guideHint: string
}
```

#### 2. HealthMetricRequest 与 HealthMetricVO 字段定义 [VERIFIED: hhs-frontend-v2/src/types/api.ts:64-82]
```ts
export interface HealthMetricRequest {
  metricKey: string
  value: number
  recordDate: string
  unit?: string
  trend?: string
}

export interface HealthMetricVO {
  id: number
  userId: number
  metricKey: string
  metricName: string
  value: number
  unit?: string
  recordDate: string
  trend?: string
  createTime: string
}
```

#### 3. WellnessMetricRequest 与 WellnessMetricVO 字段定义 [VERIFIED: hhs-frontend-v2/src/types/api.ts:298-316]
```ts
export interface WellnessMetricRequest {
  metricKey: string
  value: number
  recordDate: string
  unit?: string
  notes?: string
}

export interface WellnessMetricVO {
  id: number
  userId: number
  metricKey: string
  metricName: string
  value: number
  unit?: string
  recordDate: string
  notes?: string
  createTime: string
}
```

#### 4. 四梯度类型与评估结果接口定义 [VERIFIED: hhs-frontend-v2/src/utils/metricValidation.ts:3-13]
```ts
export type MetricTier = 'NORMAL' | 'WARNING' | 'CRITICAL' | 'HARD_BOUND'

export interface MetricEvaluation {
  tier: MetricTier
  status: 'success' | 'warning' | 'danger' | 'hard_danger'
  badgeText: string
  message: string
  canSave: boolean
  isCritical: boolean
  isHardLimit: boolean
}
```

#### 5. 组合函数返回签名 [VERIFIED: hhs-frontend-v2/src/composables/useMetricValidation.ts:87-98]
```ts
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
```

#### 6. 日期格式化工具函数签名 [VERIFIED: hhs-frontend-v2/src/utils/format.ts:34-36]
```ts
export const formatDate = (date: string | Date): string => {
  return dayjs(date).format('YYYY-MM-DD')
}
```

### Pattern 1: 双模式自适应流转 (New vs Edit Mode)
**What:** 组件通过检查 `initialData?.id` 自动在“新增录入模式”与“历史编辑模式”之间平滑切换。  
**When to use:** 在同一个通用模态弹窗中同时承接“新建健康/保健指标”与“修改既有历史指标”，彻底废除两套代码。  
**Implementation Details:**
- **新增模式 (`!initialData?.id`):** 
  - 标题显示“添加[类型]指标”或根据 `defaultMetricKey` 动态呈现；
  - 指标下拉选择器启用 (`disabled = false`)；
  - 若为血压指标且未禁用协同，开启双字段协同录入（收缩压 + 舒张压）；
  - 默认选中当前分类的第一项或由 `defaultMetricKey` 指定的指标；
- **编辑模式 (`initialData?.id != null`):**
  - 标题显示 `编辑${displayName}`；
  - 指标下拉框置灰禁用 (`disabled = true`)，防止编辑时篡改指标类型导致底层数据结构混乱；
  - 自动禁用血压高低压协同排版（`isCollaborative = false`），按单项历史记录精准评估与回显；
  - 自动将数值、日期、单位、趋势/备注正确映射至响应式表单。

### Pattern 2: 声明式分类与自适应 API 路由
**What:** 父组件仅需声明大类约束（`category="HEALTH"` 或 `category="WELLNESS"` 或缺省 `ALL`），组件内部基于当前选中的权威定义 `definition.value.category` 执行自适应 API 分流。  
**When to use:** 抹平健康医学指标（`healthApi`）与保健生活方式指标（`wellnessApi`）的接口差异，父组件监听 `@success` 重新拉取列表即可，心智负担降为零。

### Pattern 3: 向前兼容重导出 (Backward-Compatible Forwarding)
**What:** 历史代码中 `QuickInputDialog.vue` 被 `QuickInput.vue` 及 `QuickInputDialog.spec.ts` 深度依赖。通过让 `QuickInputDialog.vue` 重导出 `UniversalMetricDialog`，既升级了底层组件，又确保现有单测与页面零破坏。  
**Implementation:**
```vue
<script lang="ts">
import UniversalMetricDialog from '@/components/metric/UniversalMetricDialog.vue'
export default UniversalMetricDialog
</script>
```

### Anti-Patterns to Avoid
- **Anti-Pattern 1: 破坏性失焦清空 (Destructive Blur Wipe):** 历史代码中输入非法值时执行 `form.value = undefined`，会彻底抹除用户键入的原值，违反医学可溯源与非破坏性原则。严禁在通用组件中加入任何自动清空输入的逻辑。
- **Anti-Pattern 2: 编辑模式血压错误协同 (Accidental BP Collaboration in Edit):** 数据库中历史血压是以 `systolicBP` 和 `diastolicBP` 分开存储的独立单行。如果在编辑收缩压历史记录时也强行渲染舒张压并要求 `Promise.all` 提交，会造成无法更新单条记录甚至凭空产生幽灵舒张压。编辑模式必须通过 `collaborativeRef = false` 关闭协同。
- **Anti-Pattern 3: 忽略 `destroy-on-close` 导致旧状态残留:** 通用弹窗在不同页面、不同模式之间频繁打开，若缺少 `destroy-on-close` 或未在打开时重置 `formRef.value?.clearValidate()`，会导致打开时残留上一次录入的报错红色警告。

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 生理范围与四梯度医学校验 | 自定义写 if-else 区间判断 | `useMetricValidation` & `evaluateMetricValue` | 已完整处理 Tier 1..4 生理层级、Panic Limits、0 值有效性及非破坏性保留，自制容易漏测临界点或误拦截病理数据。 |
| 指标元数据与单位字典 | 在组件内私有定义 `metricList = [...]` | `useMetricMetaStore` (`healthMetrics`, `wellnessMetrics`) | 违反系统单一权威事实源 (SSOT) 原则，无法与后端 `HealthMetricMetaRegistry` 保持动态同步。 |
| 指标 Key 规范化与别名转换 | 手写正则替换下划线与大小写 | `toCanonicalKey` (`@/utils/metricNormalizer`) | 别名字典收录了 30+ 种医学别名与历史别名（如 `glucose`, `bp`, `spo2`），自制正则无法识别专业医学缩写。 |
| 临床生理参考指南展示 | 手写 HTML 卡片与参考区间 | `ReferenceGuideCard.vue` | 现成组件已具备血压双参考区间并列、空腹/餐后血糖特殊临床注释及静坐休息指引。 |
| 急危重症二次确认弹窗 | 手写 `ElMessageBox.confirm` | `CriticalConfirmDialog.vue` | 现成组件拥有专业的急救处置建议卡片（静坐、复测、拨打 120），`ElMessageBox` 难以排版多行临床应急指南。 |

**Key insight:** 指标录入在医疗健康应用中兼具临床安全性与用户体验。自制校验容易引发两类极端事故：要么过紧将真实高血压/高血糖病理数据当非法输入直接阻断报错，要么过松允许负心率或 500 次/分的心率静默入库。复用现有验证成熟的医学规则引擎是保障系统安全的最优解。

## Runtime State Inventory

> 本阶段为前端代码结构重构与组件收敛，不涉及数据库 schema 变更或持久化数据迁移。

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — 验证方式：查验数据库实体与 REST 接口，前后端数据表结构与字段名（`metricKey`, `value`, `recordDate`, `trend`, `notes`）保持完全一致，无需任何数据迁移。 | None |
| Live service config | None — 验证方式：纯前端 UI 交互重构，不涉及 Nginx、Redis、后端配置或外部第三方服务配置变动。 | None |
| OS-registered state | None — 验证方式：查验操作系统及后台进程，无 OS 级注册服务。 | None |
| Secrets/env vars | None — 验证方式：不新增或变更任何环境变量。 | None |
| Build artifacts | `hhs-frontend-v2/dist/` — Vite 构建产物由 CI 自动生成更新。 | 本地执行 `npm run build` 验证 TypeScript 编译与打包无报错。 |

## Common Pitfalls

### Pitfall 1: 编辑模式下血压双字段错误协同导致覆盖其他记录
**What goes wrong:** 用户在 `health/Metrics.vue` 列表点击单条历史收缩压记录（`id: 101`）的“编辑”按钮，弹窗却自动并列出舒张压字段；保存时调用了两次 API，导致不仅更新了 101，还创建了一条多余的舒张压记录。  
**Why it happens:** 逻辑仅根据 `canonicalKey === 'systolicBP'` 就激活了协同排版，忽略了当前处于编辑历史记录状态。  
**How to avoid:** 在 `useMetricValidation` 中传入第四个参数 `collaborativeRef`：仅当 `!isEditMode.value` 且指标为血压协同选项时，才开启双字段协同；编辑历史记录时强制将 `collaborativeRef` 设为 `false`。  
**Warning signs:** 编辑收缩压时，网络面板同时发出了 `PUT /api/metrics/101` 和 `POST /api/metrics`。

### Pitfall 2: 指标别名大小写不一致导致元数据获取为 `undefined`
**What goes wrong:** 后端或历史数据返回 `metricKey: 'heart_rate'` 或 `bodyTemperature`，直接通过 `metaStore.metadataMap[key]` 找不到定义，导致占位符显示“请输入数值”，单位丢失。  
**Why it happens:** 绕过了 `toCanonicalKey` 归一化工具函数，直接用原始字符串查询字典。  
**How to avoid:** 通用组件在初始化或接收 `props.defaultMetricKey`、`initialData.metricKey` 时，必须一律经过 `toCanonicalKey()` 进行规范化清洗。

### Pitfall 3: 提交时混淆健康指标与保健指标的专有载荷字段
**What goes wrong:** 提交保健指标（如步数）时传入了 `trend: ''`，或提交健康指标时传入了 `notes: ''`，导致后端 Jakarta Validation 校验不通过或字段丢失。  
**Why it happens:** 构造统一请求载荷时未根据 `definition.category` 进行字段隔离。  
**How to avoid:** 根据指标分类精确组装请求结构：
- HEALTH: `{ metricKey, value, recordDate, unit, trend: toBackendTrend(form.trend) }`
- WELLNESS: `{ metricKey, value, recordDate, unit, notes: form.notes }`

### Pitfall 4: 既有测试用例直接访问子组件内部 vm 属性导致断言失败
**What goes wrong:** 重构 `health/Metrics.vue` 后，`Metrics.spec.ts` 依然执行 `vm.form.value = 150`，报错 `TypeError: Cannot set properties of undefined (setting 'value')`。  
**Why it happens:** 原测试用例在 Phase 8 是直接针对 `Metrics.vue` 内联表单变量编写的，重构后表单状态封装到了 `UniversalMetricDialog` 内部。  
**How to avoid:** 
- 方案：全站所有详细的四梯度校验、血压协同、一键对调、星级打分、危急值弹窗测试完整收敛到新测试文件 `UniversalMetricDialog.spec.ts`；
- 在 `Metrics.spec.ts`、`HealthMetrics.spec.ts`、`PreventionAndWellness.spec.ts` 中，适度使页面组件保留对弹窗 ref 的委托引用或通过 `wrapper.findComponent(UniversalMetricDialog)` 交互，确保全站单测矩阵 100% 保持绿灯。

## Code Examples

### 1. UniversalMetricDialog.vue 核心骨架实现
```vue
<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="560px"
    destroy-on-close
    append-to-body
    class="universal-metric-dialog"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" status-icon>
      <!-- 指标类型选择 (新增模式可切换，编辑模式置灰锁定) -->
      <el-form-item label="指标类型" prop="metricKey">
        <el-select
          v-model="form.metricKey"
          placeholder="请选择指标类型"
          style="width: 100%"
          :disabled="isEditMode"
          @change="handleMetricKeyChange"
        >
          <!-- 仅在 HEALTH 且新增模式下提供血压协同选项 -->
          <el-option
            v-if="!isEditMode && category !== 'WELLNESS'"
            label="血压 (高低压协同)"
            value="bloodPressure"
          />
          <el-option
            v-for="item in availableMetrics"
            :key="item.canonicalKey"
            :label="item.displayName"
            :value="item.canonicalKey"
          />
        </el-select>
      </el-form-item>

      <!-- 血压双字段同屏并列协同排版 (仅新增模式) -->
      <template v-if="isBloodPressure">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="收缩压 (高压)" prop="value">
              <el-input-number
                v-model="form.value"
                :precision="0"
                :step="1"
                :controls="true"
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
                :controls="true"
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

        <el-alert v-if="isInverted" type="warning" show-icon :closable="false" class="inversion-alert">
          <template #title>
            <div class="inversion-banner-title">
              <span>{{ isBpEqual ? '检测到收缩压与舒张压数值相同，请核对后修改' : '检测到收缩压低于舒张压，疑似输入颠倒' }}</span>
              <el-button v-if="!isBpEqual" size="small" type="warning" plain :icon="Switch" class="swap-btn" @click="handleSwap">
                对调高低压数值
              </el-button>
            </div>
          </template>
        </el-alert>
      </template>

      <!-- 心情指标专属 1-5 星级打分 -->
      <template v-else-if="form.metricKey === 'mood'">
        <el-form-item label="心情状态" prop="value">
          <div class="mood-input">
            <el-rate
              v-model="moodValue"
              :max="5"
              show-text
              :texts="['很差', '较差', '一般', '较好', '很好']"
            />
          </div>
          <div v-if="primaryEval" class="tier-pill-wrapper">
            <el-tag :type="getTagType(primaryEval.status)" size="small" effect="light">
              <el-icon><component :is="getTagIcon(primaryEval.status)" /></el-icon>
              <span>{{ primaryEval.badgeText }}</span>
            </el-tag>
            <span class="eval-message">{{ primaryEval.message }}</span>
          </div>
        </el-form-item>
      </template>

      <!-- 单一常规健康/保健指标排版 -->
      <template v-else>
        <el-form-item :label="definition?.displayName || '测量数值'" prop="value">
          <el-input-number
            v-model="form.value"
            :precision="precision"
            :step="step"
            :controls="true"
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

      <!-- 单位与记录日期行 -->
      <el-row :gutter="16">
        <el-col :span="10">
          <el-form-item label="测量单位">
            <el-input :value="definition?.unit || form.unit || ''" disabled placeholder="自动获取" />
          </el-form-item>
        </el-col>
        <el-col :span="14">
          <el-form-item label="记录日期" prop="recordDate">
            <el-date-picker
              v-model="form.recordDate"
              type="date"
              placeholder="选择测量日期"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 健康指标独有：趋势 -->
      <el-form-item v-if="definition?.category === 'HEALTH'" label="趋势" prop="trend">
        <el-select v-model="form.trend" placeholder="请选择趋势" style="width: 100%">
          <el-option label="上升" value="UP" />
          <el-option label="下降" value="DOWN" />
          <el-option label="稳定" value="STABLE" />
        </el-select>
      </el-form-item>

      <!-- 保健指标独有：备注 -->
      <el-form-item v-if="definition?.category === 'WELLNESS'" label="备注说明" prop="notes">
        <el-input v-model="form.notes" type="textarea" :rows="2" placeholder="可选备注信息" />
      </el-form-item>

      <!-- 常态化健康参考标准解读卡片 -->
      <ReferenceGuideCard :definition="definition" :diastolic-def="diastolicDef" />
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="isSaveBlocked"
          @click="handleSubmit"
        >
          {{ isEditMode ? '保存修改' : '保存指标数据' }}
        </el-button>
      </div>
    </template>

    <!-- 急危重症二次确认模态窗 -->
    <CriticalConfirmDialog
      v-model="criticalDialogVisible"
      :metric-name="criticalMetricName"
      :value="criticalMetricValue"
      :unit="criticalMetricUnit"
      @confirm="executeSave"
    />
  </el-dialog>
</template>
```

### 2. 自适应 API 路由与保存逻辑实现
```typescript
const executeSave = async () => {
  if (submitting.value) return
  submitting.value = true
  try {
    const isWellness = definition.value?.category === 'WELLNESS' || props.category === 'WELLNESS'
    const canonical = definition.value?.canonicalKey || form.value.metricKey

    if (isWellness) {
      const payload: WellnessMetricRequest = {
        metricKey: canonical,
        value: form.value.value!,
        unit: definition.value?.unit || form.value.unit,
        recordDate: form.value.recordDate,
        notes: form.value.notes
      }
      if (isEditMode.value && editingId.value) {
        await wellnessApi.updateMetric(editingId.value, payload)
        ElMessage.success('保健指标更新成功')
      } else {
        await wellnessApi.createMetric(payload)
        ElMessage.success('保健指标添加成功')
      }
    } else {
      // 健康指标分支
      if (isBloodPressure.value && form.value.diastolicValue !== undefined) {
        const systolicPayload: HealthMetricRequest = {
          metricKey: 'systolicBP',
          value: form.value.value!,
          recordDate: form.value.recordDate,
          unit: 'mmHg',
          trend: toBackendTrend(form.value.trend)
        }
        const diastolicPayload: HealthMetricRequest = {
          metricKey: 'diastolicBP',
          value: form.value.diastolicValue!,
          recordDate: form.value.recordDate,
          unit: 'mmHg',
          trend: toBackendTrend(form.value.trend)
        }
        await Promise.all([
          healthApi.createMetric(systolicPayload),
          healthApi.createMetric(diastolicPayload)
        ])
        ElMessage.success('血压数据协同录入成功')
      } else {
        const payload: HealthMetricRequest = {
          metricKey: canonical,
          value: form.value.value!,
          recordDate: form.value.recordDate,
          unit: definition.value?.unit || form.value.unit,
          trend: toBackendTrend(form.value.trend)
        }
        if (isEditMode.value && editingId.value) {
          await healthApi.updateMetric(editingId.value, payload)
          ElMessage.success('健康指标更新成功')
        } else {
          await healthApi.createMetric(payload)
          ElMessage.success('健康指标添加成功')
        }
      }
    }

    emit('success')
    visible.value = false
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    submitting.value = false
  }
}
```

### 3. 父页面使用极简声明（以 `src/views/health/Metrics.vue` 为例）
```vue
<template>
  <div class="metrics-page">
    <el-card>
      <template #header>
        <div class="header-actions">
          <span>健康指标管理</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon>
            添加健康指标
          </el-button>
        </div>
      </template>

      <!-- 筛选与表格保持不变 -->
      ...
    </el-card>

    <!-- 一行组件声明替代原 350+ 行重复弹窗模板 -->
    <UniversalMetricDialog
      v-model="dialogVisible"
      category="HEALTH"
      :initial-data="editingMetric"
      @success="fetchMetrics"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import UniversalMetricDialog from '@/components/metric/UniversalMetricDialog.vue'
import type { HealthMetricVO } from '@/types/api'

const dialogVisible = ref(false)
const editingMetric = ref<HealthMetricVO | null>(null)

const showAddDialog = () => {
  editingMetric.value = null
  dialogVisible.value = true
}

const handleEdit = (row: HealthMetricVO) => {
  editingMetric.value = row
  dialogVisible.value = true
}
</script>
```

## State of the Art

| Old Approach (Phase 8/9 前) | Current Approach (Phase 10) | Impact |
|-----------------------------|-----------------------------|--------|
| 每个视图（`QuickInputDialog`, `health/Metrics`, `prevention/Metrics`, `wellness/Dashboard`）各自硬编码 `<el-dialog>` 与表单 | 统一使用 `src/components/metric/UniversalMetricDialog.vue` | 消除 700+ 行重复模板与冗余状态代码，全站录入交互行为 100% 统一 |
| 编辑模式使用简单表单或缺少四梯度宽容验证 | `initialData` 驱动自适应编辑流转，保留四梯度医学规则与非破坏性原则 | 历史数据修改完全对齐生理正常、预警与急危重症规则，防止异常历史数据被破坏性篡改 |
| 各页面自行编写 `healthApi` 与 `wellnessApi` 的条件判断分支 | 组件内部根据指标定义 `category` 自动分流调用对应的 API | 父页面无需了解各指标底层的接口路由细节，零心智负担 |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 数据库历史记录中血压是以收缩压和舒张压分开的独立行存储 | Architecture Patterns | 极低。已通过查看 `HealthMetricVO`、`healthApi` 接口实现以及 Phase 8 测试用例确认。 |

## Open Questions

1. **`QuickInputDialog.vue` 如何做到 100% 向后兼容？**
   - What we know: `QuickInput.vue` 和 `QuickInputDialog.spec.ts` 均直接导入了该文件。
   - What's unclear: 是否应该将其完全删除还是保留重导出。
   - Recommendation: 保留 `QuickInputDialog.vue`，在内部重导出 `UniversalMetricDialog`，确保已有代码和测试零报错。(RESOLVED: 已决定采用重导出架构，兼顾向后兼容与重构解耦)

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | 前端开发构建 | ✓ | 24.15.0 | — |
| npm | 包管理 | ✓ | 11.12.1 | — |
| Vite | 构建与热更新 | ✓ | 5.0.11 | — |
| Vitest | 单元测试执行 | ✓ | 4.1.0 | — |
| Element Plus | UI 组件 | ✓ | 2.5.6 | — |
| Pinia | 状态管理 | ✓ | 2.1.7 | — |

**Missing dependencies with no fallback:** None  
**Missing dependencies with fallback:** None  

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.1.0 + `@vue/test-utils` 2.4.6 [VERIFIED: hhs-frontend-v2/package.json:39,52] |
| Config file | `hhs-frontend-v2/vite.config.ts` |
| Quick run command | `cd hhs-frontend-v2 && npm run test:run` |
| Full suite command | `cd hhs-frontend-v2 && npm run test:run` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REUSE-01 | 通用录入组件封装：支持 HEALTH、WELLNESS 及全量指标，具备动态单位/精度/步长、血压双字段协同与对调、心情 1-5 星级打分、常态化卡片及危急确认模态 | component | `cd hhs-frontend-v2 && npx vitest run src/__tests__/components/UniversalMetricDialog.spec.ts` | ❌ Wave 0 (需新建) |
| REUSE-01 | 向后兼容性：`QuickInputDialog.vue` 重导出组件在 `QuickInputDialog.spec.ts` 中全部 7 项测试通过 | component | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/QuickInputDialog.spec.ts` | ✅ 已存在 (7 passed) |
| REUSE-01 | 全站业务页面收敛复用：`health/Metrics.vue` 接入通用组件后单测全部绿灯 | integration | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/Metrics.spec.ts src/__tests__/views/HealthMetrics.spec.ts` | ✅ 已存在 (10 passed) |
| REUSE-01 | 全站业务页面收敛复用：`prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 接入通用组件后单测全部绿灯 | integration | `cd hhs-frontend-v2 && npx vitest run src/__tests__/views/PreventionAndWellness.spec.ts src/__tests__/views/WellnessMetrics.spec.ts` | ✅ 已存在 (6 passed) |
| REUSE-02 | 统一编辑模式流转：`initialData` 传入自动锁定为编辑模式，置灰选择器，禁用血压协同，精准评估历史单项数值并正确调用 `updateMetric` | component | `cd hhs-frontend-v2 && npx vitest run src/__tests__/components/UniversalMetricDialog.spec.ts` | ❌ Wave 0 (需新建) |

### Sampling Rate
- **Per task commit:** `cd hhs-frontend-v2 && npm run test:run` (< 15 秒完成全套 80+ 个单测)
- **Per wave merge:** `cd hhs-frontend-v2 && npm run test:run && npm run build`
- **Phase gate:** 全部 13+ 个测试套件保持 100% 绿灯且生产打包零报错。

### Wave 0 Gaps
- [ ] `hhs-frontend-v2/src/__tests__/components/UniversalMetricDialog.spec.ts` — 覆盖 REUSE-01, REUSE-02 全部 10 维测试场景：
  1. `category="HEALTH"` 下拉仅展示健康指标及血压协同项
  2. `category="WELLNESS"` 下拉仅展示 7 项保健指标
  3. 血压协同新增与一键对调（收缩压 <= 舒张压拦截，点击对调解除阻断）
  4. 心情专属 1-5 星级打分交互与数值双向同步
  5. 常规指标动态精度（precision）、步长（step）与生理范围占位符
  6. 四梯度非破坏性输入保留（Tier 4 阻断保存但不抹除用户输入）
  7. 严重危急值（Panic Limits）触发 `CriticalConfirmDialog` 二次确认
  8. `initialData` 存在时自动进入编辑模式，禁用类型下拉框与血压协同
  9. 自适应 API 路由：HEALTH 分支调用 `healthApi`，WELLNESS 分支调用 `wellnessApi`
  10. 向前兼容性测试：兼容传统 `metric` 对象传参

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT Bearer Token 由 Axios 拦截器 `@/utils/request.ts` 自动装配，401 自动登出 |
| V3 Session Management | no | 无状态 REST API，纯客户端 Pinia 会话 |
| V4 Access Control | yes | 普通用户仅允许操作自己的指标记录，后端控制器层统一校验 `userId` |
| V5 Input Validation | yes | 前端输入使用 Element Plus `status-icon` + `useMetricValidation` 四梯度纯函数校验，Tier 4 生理不可能极限强行拦截，后端同时执行 Jakarta Bean Validation 校验 |
| V6 Cryptography | no | 本阶段为指标通用录入，不涉及加密机密传输 |

### Known Threat Patterns for Vue3 Form & Dialog

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| XSS in Metric Notes or Custom Units | Tampering | Element Plus `el-input` 原生文本转义，严禁对 notes 字段使用 `v-html` 渲染 |
| Overwriting Existing Records via ID Tampering | Elevation of Privilege | 编辑模式 `editingId` 绑定自经过鉴权拉取的列表行数据，后端验证记录所有权 |
| Double Submit / Re-entrancy during network latency | Denial of Service | 保存按钮绑定 `:loading="submitting"` 并配置 `if (submitting.value) return` 防重入拦截 |

## Sources

### Primary (HIGH confidence)
- `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` — 已成熟验证的四梯度录入弹窗标杆实现
- `hhs-frontend-v2/src/composables/useMetricValidation.ts` — 四梯度生理评估与血压倒置检测核心组合函数
- `hhs-frontend-v2/src/stores/metricMeta.ts` — 权威指标元数据响应式 Store
- `hhs-frontend-v2/src/types/api.ts` — 后端实体、DTO 与 VO 类型声明
- `hhs-frontend-v2/src/api/health.ts` & `src/api/wellness.ts` — 健康与保健指标 REST 客户端

### Secondary (MEDIUM confidence)
- Element Plus 官方文档 (`el-dialog`, `el-form`, `el-rate`, `el-input-number`) — append-to-body 与动态表单校验模式

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - 前端工程配置与既有组件经过充分验证，依赖明确
- Architecture: HIGH - 提炼方案基于成熟已上线的 4 个业务页面抽象，API 与数据流清晰闭环
- Pitfalls: HIGH - 已定位血压编辑误协同、别名丢失及既有单测属性暴露等关键边界

**Research date:** 2026-11-05  
**Valid until:** 2026-12-05 (稳定架构)  
