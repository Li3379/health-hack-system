# Phase 8: 健康指标管理页录入与编辑对齐 - Context

**Gathered:** 2026-11-05  
**Phase:** 8 — 健康指标管理页录入与编辑对齐  
**Status:** Ready for planning  

<domain>
## Phase Boundary

本阶段重构健康指标主管理页面（`hhs-frontend-v2/src/views/health/Metrics.vue`）的填报与编辑交互体验。核心交付物包括：
1. **指标下拉与筛选全量元数据动态驱动 (HEALTH-01)**：彻底废除页面内写死的 7 项 `el-option`（`glucose`, `systolicBP`, `diastolicBP`, `heartRate`, `temperature`, `weight`, `bmi`）以及类内私有 `metricUnitMap` 字典，改为完全由 Pinia `useMetricMetaStore().healthMetrics` 动态渲染全量 16 项权威医学健康指标；
2. **血压协同录入与单项编辑智能适配 (HEALTH-02)**：
   - **新增模式**：用户选择“血压”时，表单切换为收缩压（高压）与舒张压（低压）同屏并列双字段协同录入，支持高低压倒置实时检测与一键对调原子自纠，保存时协同持久化入库；
   - **编辑模式**：编辑历史记录列表中的单条记录时，精准匹配该单项指标（如单独编辑某条收缩压或舒张压记录）的元数据、标准单位与四梯度模型，支持数值安全修改；
3. **四梯度医学校验与非破坏性保留 (HEALTH-03)**：全面接入 `useMetricValidation` 纯函数评估引擎，彻底废除失焦清空机制；正常与预警偏差指标（Tier 1/2）允许正常保存，危急重症（Tier 3）触发二次确认模态窗，物理不可能极限（Tier 4）拦截提交但不抹除输入；
4. **视觉规范与全局平铺优化 (UI-SPEC 对齐)**：弹窗挂载 `ReferenceGuideCard.vue` 临床解读卡片与 `CriticalConfirmDialog.vue` 危急确认窗，弹窗启用 `append-to-body` 与置顶标签排版，避免组件层叠上下文局限。

</domain>

<decisions>
## Implementation Decisions

### 1. 指标选项全量元数据动态驱动 (HEALTH-01)
- **D-01 (全量元数据动态生成):**
  - 在 `src/views/health/Metrics.vue` 中，顶部筛选栏（filter-bar）和添加/编辑对话框（dialog）内的「指标类型」下拉选择器（`el-select`），全部通过 `v-for="item in metricMetaStore.healthMetrics"` 动态循环渲染全量 16 项权威医学指标；
  - 下拉选项的 `label` 显示权威中文名称（`item.displayName`），`value` 绑定标准 canonicalKey（`item.canonicalKey`）；
  - 彻底删除模板中硬编码的 7 项静态 `<el-option>` 以及 `<script setup>` 中的私有常量 `metricUnitMap`；单位与精度直接从 `useMetricMetaStore` 动态获取。

### 2. 血压协同录入与历史单项编辑适配 (HEALTH-02)
- **D-02 (血压新增协同+编辑单项精准适配):**
  - **新增指标模式 (`editingId === null`)**：
    - 支持用户在下拉框中选择常规单项指标或“血压”；
    - 当选择血压时，表单切换为收缩压与舒张压同屏并列网格布局，动态展示双状态胶囊；若出现 `收缩压 <= 舒张压` 倒置，即时弹出黄色警告条并提供「对调高低压数值」原子交换按钮，保存时通过 `Promise.all` 协同持久化；
  - **编辑指标模式 (`editingId !== null`)**：
    - 根据当前行的 `row.metricKey` 加载记录，禁用指标类型切换；
    - 绑定当前单项指标专属的元数据（例如仅编辑收缩压或舒张压单个历史点），表单展示当前指标的数值输入、状态胶囊与参考指南，允许用户调整历史数值并重新评估。

### 3. 四梯度宽容校验与非破坏性交互 (HEALTH-03)
- **D-03 (四梯度非破坏性反馈与危急值确认闭环):**
  - 接入 `useMetricValidation`，输入数值变化实时派生四梯度状态（正常/预警偏差/急危重症/存活极限）；
  - **非破坏性原则**：彻底禁止失焦抹除任何输入数据，原值 100% 忠实保留在表单中；
  - **宽容放行**：Tier 1（正常）与 Tier 2（预警偏差）表单校验直接放行通过，允许真实病变数据留存；
  - **危急值确认**：达到 Panic Limits 时在点击保存前触发 `CriticalConfirmDialog` 模态窗，提供 120 急救指引，确认后顺利持久化入库；
  - **物理极限拦截**：仅在超出人体生理存活极限（Tier 4）时提示阻断保存，原输入文本不被清空。

### 4. 弹窗全局平铺与置顶标签排版优化 (UI-SPEC)
- **D-04 (弹窗全局平铺与布局优化):**
  - `Metrics.vue` 中的添加/编辑弹窗 `<el-dialog>` 显式添加 `append-to-body`，使其 DOM 脱离外层 `<el-card>` 的 CSS transform 动画层叠上下文，居中覆盖全屏视口；
  - 表单设置 `label-position="top"`，赋予数值输入框 100% 完整宽度，确保占位符（如 `参考范围: 90 ~ 139 mmHg`）清晰平铺呈现而不被截断；
  - 表单底部常态化挂载 `ReferenceGuideCard.vue`。

### Claude's Discretion
- 数据表格列表列宽微调与响应式适配；
- 趋势选择项（上升/下降/稳定）与自动计算趋势的协同展示逻辑。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 核心资产与复用组件 (Phase 5/6 交付物)
- `hhs-frontend-v2/src/stores/metricMeta.ts` — 前端 Pinia 元数据 Store，提供 `healthMetrics`（16 项指标）与 `getDefinition`
- `hhs-frontend-v2/src/constants/metricMeta.ts` — 23 项指标静态离线字典与生理边界
- `hhs-frontend-v2/src/utils/metricValidation.ts` — 四梯度生理评估纯函数与 Panic Limits 字典
- `hhs-frontend-v2/src/composables/useMetricValidation.ts` — 响应式四梯度评估、血压倒置检测与一键对调组合函数
- `hhs-frontend-v2/src/views/data-input/components/ReferenceGuideCard.vue` — 常态化参考解读卡片
- `hhs-frontend-v2/src/views/data-input/components/CriticalConfirmDialog.vue` — 急危重症二次确认模态窗
- `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` — Phase 6 标杆改造参考实现

### 待重构目标页面
- `hhs-frontend-v2/src/views/health/Metrics.vue` — 健康指标主管理与列表页面

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useMetricMetaStore`: 直接提供 `healthMetrics.value` 数组，包含全部 16 项医学指标；
- `useMetricValidation`: 直接接收 `metricKeyRef`、`valueRef`、`diastolicRef`，输出完整的响应式评估对象与对调动作；
- `ReferenceGuideCard` & `CriticalConfirmDialog`: 现成的 Element Plus UI 组件，直接挂载复用。

### Established Patterns
- Element Plus 表单校验；
- Composition API + `<script setup lang="ts">`；
- API 请求通过 `@/api/health`。

### Integration Points
- 重构 `Metrics.vue` 中的 `<el-dialog>` 表单，移除硬编码下拉列表，引入 `ReferenceGuideCard` 与 `CriticalConfirmDialog`；
- 接入 `useMetricValidation` 并在保存前增加危急值二次确认拦截。

</code_context>

<specifics>
## Specific Ideas

- 下拉框新增“血压（高低压协同）”虚拟组合选项，当选中时表单自动切为两列网格展示收缩压与舒张压。

</specifics>

<deferred>
## Deferred Ideas

- **Phase 9 承接：** `src/views/prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 保健指标录入对齐。
- **Phase 10 承接：** 通用指标表单交互组件抽象与全站编辑模式终极统一。

</deferred>

---

*Phase: 08-健康指标管理页录入与编辑对齐*  
*Context gathered: 2026-11-05*  
