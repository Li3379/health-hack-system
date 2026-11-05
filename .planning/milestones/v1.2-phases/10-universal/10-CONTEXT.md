# Phase 10: 指标录入通用交互组件封装与全站验证 - Context

**Gathered:** 2026-11-05  
**Phase:** 10 — 指标录入通用交互组件封装与全站验证  
**Status:** Ready for planning  

<domain>
## Phase Boundary

本阶段作为 Milestone v1.2 的收官阶段，重在架构提炼、消除重复代码与全站体验终极闭环。核心交付物包括：
1. **通用指标录入与编辑弹窗组件 (`UniversalMetricDialog.vue`) (REUSE-01)**：
   - 抽象提炼高度可复用、低耦合的通用组件，统一支持健康指标（HEALTH）、保健生活指标（WELLNESS）或全量指标的录入；
   - 内部完整集成血压高低压同屏协同与一键对调（GUIDE-03, VAL-04）、心情专属 1-5 星级打分（WELL-02）、动态步长/精度/参考占位符（GUIDE-01）、四梯度宽容胶囊与非破坏性保留（VAL-02, VAL-03）、`ReferenceGuideCard` 常态化解读卡片（GUIDE-02）及 `CriticalConfirmDialog` 危急值二次确认模态窗（VAL-05）；
2. **统一编辑模式与自适应持久化流转 (REUSE-02)**：
   - 通用组件支持传入 `initialData?: HealthMetricVO | WellnessMetricVO`；当编辑历史记录时，自动识别为编辑模式，锁定指标类型，加载历史数值与对应标准元数据，全流程对齐四梯度校验规则；
   - 组件内部根据指标的权威 `category`（HEALTH vs WELLNESS）自适应分流调用 `healthApi` 或 `wellnessApi` 的新增或更新接口，父页面零心智负担；
3. **全站业务页面全面对齐接入**：
   - 重构并接入 `src/views/health/Metrics.vue`、`src/views/prevention/Metrics.vue`、`src/views/wellness/Dashboard.vue` 及 `src/views/data-input/components/QuickInput.vue`，彻底消除各页面中分散重复编写的 200+ 行表单与弹窗模板代码；
4. **全链路综合测试矩阵与全站回归审计**：
   - 构建通用组件综合测试套件 `UniversalMetricDialog.spec.ts`，并对全站录入入口执行自动化回归，验证全站录入行为 100% 统一。

</domain>

<decisions>
## Implementation Decisions

### 1. 完整通用弹窗组件提炼封装 (REUSE-01)
- **D-01 (完整通用弹窗封装):**
  - 创建 `@/components/metric/UniversalMetricDialog.vue`（或 `@/views/data-input/components/UniversalMetricDialog.vue`）；
  - Props 接收：
    - `modelValue: boolean`（控制弹窗显隐）；
    - `category?: 'HEALTH' | 'WELLNESS' | 'ALL'`（指标大类过滤，缺省为 ALL）；
    - `defaultMetricKey?: string`（默认选中的指标 Key，如快捷录入时传入）；
    - `initialData?: HealthMetricVO | WellnessMetricVO | null`（用于编辑模式的历史记录对象）；
  - Emits 触发 `update:modelValue` 与 `success`；
  - 内部完整继承已在 Phase 6/8/9 验证成熟的四梯度校验、血压双向网格与对调、心情星级打分、`ReferenceGuideCard`、`CriticalConfirmDialog`、`append-to-body` 全局平铺与 `label-position="top"` 排版规范。

### 2. 统一编辑模式与自适应 API 路由 (REUSE-02)
- **D-02 (自适应 API 路由与编辑流转):**
  - 当 `initialData` 存在且包含 `id` 时，自动判定为编辑模式：弹窗标题呈现“编辑[指标名]”，指标下拉框置灰禁用，自动关闭血压协同模式，按单项指标精准评估；
  - 提交数据时，组件内部优先从权威元数据 `definition.value?.category` 判定指标类型：
    - 若为 `WELLNESS`：新增调用 `wellnessApi.createMetric`，编辑调用 `wellnessApi.updateMetric(id, data)`；
    - 若为 `HEALTH`：新增血压协同调用 `Promise.all([healthApi.createMetric, healthApi.createMetric])`，常规新增调用 `healthApi.createMetric`，编辑调用 `healthApi.updateMetric(id, data)`；
  - 保存成功后触发 `emit('success')` 并关闭弹窗，父页面仅需监听 `@success` 重新拉取数据列表，无需在各个页面重复编写复杂的提交分支。

### 3. 全站业务页面全面收敛复用 (REUSE-01)
- **D-03 (三大业务页面收敛复用):**
  - `src/views/data-input/components/QuickInput.vue` 直接使用 `UniversalMetricDialog` 替换原 `QuickInputDialog`；
  - `src/views/health/Metrics.vue` 移除页面内重复的 `<el-dialog>` 及其内部表单字段，直接复用 `<UniversalMetricDialog category="HEALTH" ... />`；
  - `src/views/prevention/Metrics.vue` 移除页面内重复的 `<el-dialog>` 及其内部表单字段，直接复用 `<UniversalMetricDialog category="WELLNESS" ... />`；
  - `src/views/wellness/Dashboard.vue` 移除页面内重复的 `<el-dialog>` 及其内部表单字段，直接复用 `<UniversalMetricDialog category="WELLNESS" ... />`；
  - 全站消除 600+ 行重复的弹窗、表单、状态胶囊与校验逻辑代码，实现“一次修改，全站受益”。

### 4. 全站端到端集成测试矩阵与回归保证
- **D-04 (全量录入入口回归矩阵):**
  - 编写 `UniversalMetricDialog.spec.ts` 全覆盖单元测试（覆盖 HEALTH、WELLNESS、血压协同、心情评星、新增模式、编辑模式、四梯度拦截与危急值确认）；
  - 保持现有各大页面的单测（`Metrics.spec.ts`、`PreventionAndWellness.spec.ts`、`QuickInputDialog.spec.ts`）全部绿灯通过，确保重构零破坏、零回归。

### Claude's Discretion
- `UniversalMetricDialog.vue` 的公共组件存放目录（推荐 `@/components/metric/`，并在 `@/views/data-input/components/` 建立重导出以保持已有引用的向后兼容）；
- 弹窗针对不同业务场景的标题自适应定制（例如传入可选 `title` 属性覆盖默认标题）。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 核心资产与复用组件 (Phase 6/8/9 交付物)
- `hhs-frontend-v2/src/views/data-input/components/ReferenceGuideCard.vue` — 常态化参考解读卡片
- `hhs-frontend-v2/src/views/data-input/components/CriticalConfirmDialog.vue` — 急危重症二次确认模态窗
- `hhs-frontend-v2/src/composables/useMetricValidation.ts` — 响应式四梯度生理校验组合函数
- `hhs-frontend-v2/src/stores/metricMeta.ts` — Pinia 权威元数据 Store
- `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` — Phase 6 标杆改造实现
- `hhs-frontend-v2/src/views/health/Metrics.vue` — Phase 8 标杆改造实现
- `hhs-frontend-v2/src/views/prevention/Metrics.vue` — Phase 9 标杆改造实现
- `hhs-frontend-v2/src/views/wellness/Dashboard.vue` — Phase 9 标杆改造实现

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useMetricMetaStore`: 提供 `healthMetrics`、`wellnessMetrics` 与 `allDefinitions`；
- `useMetricValidation`: 已支持 `collaborativeRef` 协同开关与双向血压对调；
- `ReferenceGuideCard` & `CriticalConfirmDialog`: 现成的标准组件，直接在通用组件内集成。

### Established Patterns
- Element Plus 表单组件标准；
- Vue 3 `<script setup lang="ts">`；
- `v-model` 双向绑定与 `emit('success')` 事件回调。

### Integration Points
- 新建 `hhs-frontend-v2/src/components/metric/UniversalMetricDialog.vue`；
- `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` 包装/重导出通用组件保持兼容；
- `health/Metrics.vue`、`prevention/Metrics.vue`、`wellness/Dashboard.vue` 替换原有弹窗。

</code_context>

<specifics>
## Specific Ideas

- 通用组件设计成开箱即用：只需 `<UniversalMetricDialog v-model="visible" :category="cat" :initial-data="editRow" @success="refresh" />`，所有校验、四梯度、危急弹窗、API 路由、错误处理与轻提示在内部自动完成。

</specifics>

<deferred>
## Deferred Ideas

- **v1.3+ 规划：** 将 AI 智能文本解析与 OCR 报告识别对齐到权威元数据注册中心（SYNC-01）。
- **v1.3+ 规划：** 个性化基线定制（结合病史针对特定患者放宽或收紧正常区间）。

</deferred>

---

*Phase: 10-指标录入通用交互组件封装与全站验证*  
*Context gathered: 2026-11-05*  
