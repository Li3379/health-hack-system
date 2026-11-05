# Phase 9: 保健指标管理页录入对齐 - Context

**Gathered:** 2026-11-05  
**Phase:** 9 — 保健指标管理页录入对齐  
**Status:** Ready for planning  

<domain>
## Phase Boundary

本阶段重构预防保健管理页（`src/views/prevention/Metrics.vue`）与生活方式仪表盘（`src/views/wellness/Dashboard.vue`）的填报与编辑交互体验。核心交付物包括：
1. **全量保健指标元数据动态驱动 (WELL-01)**：两个页面添加弹窗内的「指标类型」下拉框，统一由 Pinia `useMetricMetaStore().wellnessMetrics`（全量 7 项保健指标：睡眠时长、睡眠质量、步数、运动时长、饮水量、心情、精力）动态渲染，彻底废除私有 `WELLNESS_METRICS` 硬编码字典与写死精度；
2. **心情专属 1-5 星级交互与数值型指标分流 (WELL-02)**：
   - 当选中「心情（mood）」指标时，呈现友好的 1-5 星级评分交互（`el-rate`），辅助展示中文心情等级说明（很差/较差/一般/较好/很好）及状态胶囊；
   - 选中其他数值型指标（如饮水量、步数、睡眠时长等）时，自动匹配标准单位、步长、精度与参考范围占位符，并在表单下方常态化挂载 `ReferenceGuideCard`；
3. **四梯度医学校验与非破坏性保留 (WELL-03)**：全面接入 `useMetricValidation` 纯函数评估引擎，彻底废除失焦清空机制；正常与预警偏差数值（Tier 1/2）允许正常保存，极端异常数值触发 `CriticalConfirmDialog` 二次确认弹窗防误触，存活极限（Tier 4）拦截提交但不抹除输入；
4. **视觉规范与全局平铺优化 (UI-SPEC 对齐)**：两个页面的弹窗显式添加 `append-to-body`，脱离外层 GSAP CSS 动画包含块，启用 `label-position="top"` 顶置标签排版，避免文字挤压与占位符截断。

</domain>

<decisions>
## Implementation Decisions

### 1. 全量保健元数据动态绑定 (WELL-01)
- **D-01 (全量保健元数据动态生成):**
  - 在 `src/views/prevention/Metrics.vue` 与 `src/views/wellness/Dashboard.vue` 中，指标类型下拉框（`el-select`）统一通过 `v-for="item in metricMetaStore.wellnessMetrics"` 动态循环渲染全量 7 项保健指标；
  - 彻底清除组件内写死的 `WELLNESS_METRICS` 字典与固定 `precision=2/1`，单位、精度、步长与参考区间占位符直接从元数据注册表中读取。

### 2. 心情专属 1-5 星级交互与数值型分流 (WELL-02)
- **D-02 (心情专属 1-5 星级打分+描述文本):**
  - 当选中「心情（mood）」时，展示 `<el-rate v-model="moodValue" :max="5" show-text :texts="['很差', '较差', '一般', '较好', '很好']" />`；
  - 当选中其他数值型生活方式指标（`sleepDuration`, `sleepQuality`, `steps`, `exerciseMinutes`, `waterIntake`, `energy`）时，展示 `<el-input-number>`，并在表单底部挂载 `ReferenceGuideCard` 进行解读。

### 3. 四梯度非破坏性校验与危急值确认防误触 (WELL-03)
- **D-03 (四梯度非破坏性校验与危急确认防误触):**
  - 接入 `useMetricValidation` 评估引擎；
  - **非破坏性输入保留**：彻底移除失焦清空输入框逻辑，异常数值 100% 忠实保留在表单中；
  - **宽容放行**：偏高或偏低数值（Tier 2，如睡眠 4 小时、饮水 3500ml）在表单验证器中予以放行，允许真实记录生活方式数据；
  - **危急重症防误触**：极度异常数值（如严重超负荷饮水、极度睡眠不足等超出安全边界时）在保存前弹出 `CriticalConfirmDialog` 二次确认防误触并提示健康风险，确认后正常入库；
  - **存活极限拦截**：仅超出人体存活物理极限（Tier 4）时提示阻断提交，但不抹除原文本。

### 4. 弹窗全局平铺与置顶标签排版优化 (UI-SPEC)
- **D-04 (统一全局平铺+置顶排版):**
  - 两个页面的录入弹窗显式添加 `append-to-body`，使其 DOM 脱离外层卡片的 GSAP transform 动画层叠上下文，居中平铺全屏视口；
  - 表单使用 `label-position="top"` 顶置排版，确保横向空间充足，占位符文字完整平铺呈现不被截断；
  - 表单底部常态化挂载 `ReferenceGuideCard.vue`。

### Claude's Discretion
- 历史记录表格列宽微调与响应式适配；
- 生活方式仪表盘活动环（ActivityRings）的数据联动刷新。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 核心资产与复用组件 (Phase 5/6 交付物)
- `hhs-frontend-v2/src/stores/metricMeta.ts` — 前端 Pinia 元数据 Store，提供 `wellnessMetrics`（7 项指标）与 `getDefinition`
- `hhs-frontend-v2/src/constants/metricMeta.ts` — 23 项指标静态离线字典与生理边界
- `hhs-frontend-v2/src/utils/metricValidation.ts` — 四梯度生理评估纯函数与 Panic Limits 字典
- `hhs-frontend-v2/src/composables/useMetricValidation.ts` — 响应式四梯度评估组合函数
- `hhs-frontend-v2/src/views/data-input/components/ReferenceGuideCard.vue` — 常态化参考解读卡片
- `hhs-frontend-v2/src/views/data-input/components/CriticalConfirmDialog.vue` — 急危重症二次确认模态窗
- `hhs-frontend-v2/src/views/health/Metrics.vue` — Phase 8 标杆改造参考实现

### 待重构目标页面
- `hhs-frontend-v2/src/views/prevention/Metrics.vue` — 预防保健指标列表页面
- `hhs-frontend-v2/src/views/wellness/Dashboard.vue` — 保健生活方式仪表盘页面

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useMetricMetaStore`: 直接提供 `wellnessMetrics.value` 数组，包含全部 7 项生活保健指标；
- `useMetricValidation`: 直接接收 `metricKeyRef` 与 `valueRef`，输出完整的响应式评估对象；
- `ReferenceGuideCard` & `CriticalConfirmDialog`: 现成的 Element Plus UI 组件，直接挂载复用。

### Established Patterns
- Element Plus 表单校验；
- Composition API + `<script setup lang="ts">`；
- API 请求通过 `@/api/wellness`。

### Integration Points
- 检查并完善 `prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 中的弹窗表单；
- 编写针对两个页面的全流程集成测试套件。

</code_context>

<specifics>
## Specific Ideas

- 心情评级交互与数值双向映射：`1: 很差, 2: 较差, 3: 一般, 4: 较好, 5: 很好`。

</specifics>

<deferred>
## Deferred Ideas

- **Phase 10 承接：** 通用指标表单交互组件抽象与全站编辑模式终极统一。

</deferred>

---

*Phase: 09-保健指标管理页录入对齐*  
*Context gathered: 2026-11-05*  
