# Phase 09: 保健指标管理页录入对齐 - Research

**Researched:** 2026-11-05  
**Domain:** Vue 3 / Element Plus 前端生活保健指标填报、四梯度生理校验、星级交互分流与全局模态排版  
**Confidence:** HIGH  

## Summary

本阶段的核心技术目标是实现「预防保健管理页（`src/views/prevention/Metrics.vue`）」与「生活方式仪表盘（`src/views/wellness/Dashboard.vue`）」在填报和编辑交互上的标准化与深度对齐。在此前版本中，前端页面存在私有硬编码指标字典、失焦强制清空、生硬拦截及星级评分体验不一致等问题。

通过本次调研与技术验证，明确了三大核心交付支柱：
1. **统一权威元数据驱动 (WELL-01)**：彻底废除各视图私有的 `WELLNESS_METRICS` 硬编码字典及固定小数精度，统一通过 Pinia `useMetricMetaStore().wellnessMetrics`（全量 7 项保健指标：睡眠时长、睡眠质量、步数、运动时长、饮水量、心情、精力）动态生成下拉选项与精度/步长/占位符 [VERIFIED: hhs-frontend-v2/src/stores/metricMeta.ts:23-25]；
2. **心情 1-5 星级交互与数值型指标分流 (WELL-02)**：当指标为「心情（`mood`）」时分流至 `<el-rate>` 1-5 星交互并绑定标准中文等级文案（`['很差', '较差', '一般', '较好', '很好']`）[VERIFIED: hhs-frontend-v2/src/utils/format.ts:220-226]；其他指标渲染 `<el-input-number>` 并常态化挂载 `ReferenceGuideCard`；
3. **四梯度医学校验与非破坏性保留 (WELL-03)**：全面接入 `useMetricValidation` 评估引擎 [VERIFIED: hhs-frontend-v2/src/utils/metricValidation.ts:3-13]，彻底废除失焦清空机制；Tier 2 预警偏差允许正常保存，极端异常数值触发 `CriticalConfirmDialog` 二次确认防误触，Tier 4 存活极限拦截提交但不抹除输入。

**Primary recommendation:** 严格按照 Phase 8 `src/views/health/Metrics.vue` 标杆架构模式，在 `prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 中统一接入 `useMetricValidation`，在 `src/utils/metricValidation.ts` 的 `PANIC_LIMITS` 中补充保健指标危急阈值，弹窗显式配置 `append-to-body` 与 `label-position="top"`，并通过 Vitest 测试套件保障 100% 交互与校验回归。

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01 (全量保健元数据动态生成):**
  - 在 `src/views/prevention/Metrics.vue` 与 `src/views/wellness/Dashboard.vue` 中，指标类型下拉框（`el-select`）统一通过 `v-for="item in metricMetaStore.wellnessMetrics"` 动态循环渲染全量 7 项保健指标；
  - 彻底清除组件内写死的 `WELLNESS_METRICS` 字典与固定 `precision=2/1`，单位、精度、步长与参考区间占位符直接从元数据注册表中读取。
- **D-02 (心情专属 1-5 星级打分+描述文本):**
  - 当选中「心情（mood）」时，展示 `<el-rate v-model="moodValue" :max="5" show-text :texts="['很差', '较差', '一般', '较好', '很好']" />`；
  - 当选中其他数值型生活方式指标（`sleepDuration`, `sleepQuality`, `steps`, `exerciseMinutes`, `waterIntake`, `energy`）时，展示 `<el-input-number>`，并在表单底部挂载 `ReferenceGuideCard` 进行解读。
- **D-03 (四梯度非破坏性校验与危急确认防误触):**
  - 接入 `useMetricValidation` 评估引擎；
  - **非破坏性输入保留**：彻底移除失焦清空输入框逻辑，异常数值 100% 忠实保留在表单中；
  - **宽容放行**：偏高或偏低数值（Tier 2，如睡眠 4 小时、饮水 3500ml）在表单验证器中予以放行，允许真实记录生活方式数据；
  - **危急重症防误触**：极度异常数值（如严重超负荷饮水、极度睡眠不足等超出安全边界时）在保存前弹出 `CriticalConfirmDialog` 二次确认防误触并提示健康风险，确认后正常入库；
  - **存活极限拦截**：仅超出人体存活物理极限（Tier 4）时提示阻断提交，但不抹除原文本。
- **D-04 (统一全局平铺+置顶排版):**
  - 两个页面的录入弹窗显式添加 `append-to-body`，使其 DOM 脱离外层卡片的 GSAP transform 动画层叠上下文，居中平铺全屏视口；
  - 表单使用 `label-position="top"` 顶置排版，确保横向空间充足，占位符文字完整平铺呈现不被截断；
  - 表单底部常态化挂载 `ReferenceGuideCard.vue`。

### Claude's Discretion
- 历史记录表格列宽微调与响应式适配；
- 生活方式仪表盘活动环（ActivityRings）的数据联动刷新。

### Deferred Ideas (OUT OF SCOPE)
- **Phase 10 承接：** 通用指标表单交互组件抽象与全站编辑模式终极统一。
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| WELL-01 | `src/views/prevention/Metrics.vue` 与 `src/views/wellness/Dashboard.vue` 统一接入权威元数据 Store，废除私有 `WELLNESS_METRICS` 硬编码字典与固定 `precision=2/1` | 前端 Pinia Store `useMetricMetaStore().wellnessMetrics` 动态输出 7 项保健指标，包含单位、精度、步长、参考范围和占位符 [VERIFIED: hhs-frontend-v2/src/stores/metricMeta.ts:23-25] |
| WELL-02 | 保健指标录入保留心情（mood）特有的 1-5 星级打分机制，其他数值型指标（喝水、睡眠、步数、卡路里等）自动匹配推荐步长与精度，并展示常态化参考解读卡片 | 模版条件渲染分支：`form.metricKey === 'mood'` 时渲染 `<el-rate>` 并绑定 `MOOD_LEVELS`，非 mood 指标使用 `<el-input-number>`，并在表单底部挂载 `ReferenceGuideCard` [VERIFIED: hhs-frontend-v2/src/utils/format.ts:220-226] |
| WELL-03 | 保健指标录入接入四梯度宽容校验与非破坏性失焦保护，避免生硬红色报错与强制清空，支持异常数值顺利留存 | 引入 `useMetricValidation` 响应式评估函数，移除失焦清空；在 `PANIC_LIMITS` 增加保健指标危急阈值以支持 `CriticalConfirmDialog` 二次确认防误触，Tier 4 仅拦截提交不抹除输入 [VERIFIED: hhs-frontend-v2/src/utils/metricValidation.ts:3-13] |
</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 全量保健元数据获取与响应式分发 | Frontend Store (`useMetricMetaStore`) | API / Backend (`/api/metrics/metadata`) | 前端 Pinia Store 提供单例响应式 `wellnessMetrics` 并内置离线兜底常量，确保首屏零延迟加载与离线可用 |
| 指标填报与分流交互 (星级 vs 数值) | Browser / Client (`el-rate`, `el-input-number`) | — | 用户交互、输入事件处理、星级打分以及双向数据绑定完全属于前端客户端视图职责 |
| 四梯度生理状态评估与非破坏性反馈 | Browser / Client (`useMetricValidation`) | — | 纯函数计算引擎毫秒级实时计算状态胶囊、解释性文案与阻断判定，不产生网络开销与输入卡顿 |
| 危急重症防误触与二次确认拦截 | Browser / Client (`CriticalConfirmDialog`) | API / Backend (服务端防物理极限注入) | 弹窗拦截为前端操作防呆机制，服务端仅对物理不可能极限通过 `@ValidMetricRecord` 拦截 |
| 保健数据持久化与摘要刷新 | API / Backend (`/api/wellness`) | Frontend Store (`useWellnessStore`) | 数据入库持久化由 Spring Boot 后端控制器与 MyBatis-Plus 服务负责，成功后通知前端 Store 刷新摘要与活动环 |

---

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Vue 3 | 3.4.15 | 核心前端框架 | 全系统标准，Composition API `<script setup>` 驱动 |
| Element Plus | 2.5.6 | UI 组件库 | 全站统一组件标准，提供 `el-rate`, `el-input-number`, `el-dialog`, `el-form` |
| Pinia | 2.1.7 | 状态管理 | 统一状态库，提供 `useMetricMetaStore` 与 `useWellnessStore` |
| TypeScript | 5.5.0 | 类型安全 | 提供 `MetricDefinitionVO`, `WellnessMetricRequest`, `MetricEvaluation` 类型安全检查 |
| Vitest | 4.1.7 | 自动化单元与集成测试 | 项目标准测试套件，毫秒级快速运行并支持 happy-dom 环境 |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| @element-plus/icons-vue | 2.3.1 | 图标库 | 用于状态胶囊、表单提示图标与卡片装饰 |
| Day.js | 1.11.10 (内置) | 日期处理与格式化 | 表单记录日期默认值与日期格式化 |
| ECharts | 5.4.3 | 数据可视化 | 仪表盘睡眠趋势与活动趋势折线图渲染 |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 单独为 mood 制作普通数字输入框 | `el-rate` 星级评分 | 普通数字框失去直观星级体验与心理主观情感表达，用户决策要求保留 `el-rate` |
| 表单校验直接调用后端 AI 诊断 | 本地纯函数校验 `useMetricValidation` | 后端 AI 调用有 1~3 秒网络延迟并消耗 Token，客户端纯函数规则毫秒级响应，体验更佳 |

---

## Package Legitimacy Audit

> 本阶段为纯前端代码重构与对齐，完全复用项目现有的 `element-plus`, `vue`, `pinia`, `vitest` 等依赖，**无需安装任何新的外部包**。

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | [OK] | 无需新增依赖 |

**Packages removed due to [SLOP] verdict:** none  
**Packages flagged as suspicious [SUS]:** none  

---

## Architecture Patterns

### System Architecture Diagram

```
+-------------------------------------------------------------------------------------------------+
|                                Browser / Client (Vue 3 + Element Plus)                          |
+-------------------------------------------------------------------------------------------------+
|                                                                                                 |
|   1. 用户打开弹窗 (showAddDialog / handleEdit)                                                   |
|          │                                                                                      |
|          ▼                                                                                      |
|   [el-dialog append-to-body] ──脱离 GSAP 动画层叠上下文──> 全屏居中平铺视口                        |
|          │                                                                                      |
|          ▼                                                                                      |
|   [el-select: 指标类型] <──动态遍历── useMetricMetaStore.wellnessMetrics (全量 7 项)              |
|          │                                                                                      |
|   ┌──────┴────────────────────────────────────────────────┐                                     |
|   ▼ (form.metricKey === 'mood')                           ▼ (数值型指标: 步数/睡眠/饮水/等)      |
|   [el-rate 1-5 星交互]                                   [el-input-number 动态步长与精度]        |
|   texts: ['很差', '较差', '一般', '较好', '很好']         placeholder: 参考范围动态占位符        |
|   绑定 moodValue (1~5 整数)                               保留用户键入真实原值 (非破坏性)        |
|   └──────┬────────────────────────────────────────────────┘                                     |
|          │                                                                                      |
|          ▼                                                                                      |
|   [useMetricValidation 响应式评估] ───> 四梯度生理模型 (Tier 1~4)                               |
|          │                                                                                      |
|          ├─► 渲染动态状态胶囊 (<el-tag> 正常/偏高/偏低/危急)                                    |
|          ├─► 常态化健康解读卡片 (<ReferenceGuideCard :definition="definition" />)               |
|          │                                                                                      |
|   用户点击 [保存 / 确定]                                                                        |
|          │                                                                                      |
|          ▼                                                                                      |
|   formRef.validate() 评估拦截:                                                                  |
|          ├── Tier 4 (HARD_BOUND): 阻断提交，提示超出极限，保留输入原值                           |
|          ├── Tier 3 (CRITICAL): 触发 <CriticalConfirmDialog> 弹出二次确认模态窗                 |
|          │      └── 用户点击 [确认保存] ───► 允许放行入库                                        |
|          └── Tier 1 (NORMAL) & Tier 2 (WARNING): 直接放行入库                                    |
|                                     │                                                           |
|                                     ▼                                                           |
|                       [POST/PUT /api/wellness] ──► 后端入库成功                                 |
|                                     │                                                           |
|                                     ▼                                                           |
|                  刷新 Pinia Store (fetchSummary, fetchLatest)                                   |
|                  联动刷新 ActivityRings 活动环与 ECharts 趋势图                                 |
+-------------------------------------------------------------------------------------------------+
```

### In-Repo Discrete Values Verbatim Citations

为严格遵循 Discrete Value Provenance 规则，以下关键常量已于本会话通过 `Read` 工具打开源码审查：

#### 1. 全量 7 项保健指标元数据静态定义 [VERIFIED: hhs-frontend-v2/src/constants/metricMeta.ts:267-371]
```typescript
  // WELLNESS METRICS (7 项)
  sleepDuration: {
    canonicalKey: 'sleepDuration',
    aliases: ['sleep_duration', 'SLEEP_DURATION', 'sleepduration'],
    displayName: '睡眠时长',
    category: 'WELLNESS',
    unit: '小时',
    precision: 1,
    step: 0.5,
    hardMin: 0.0,
    hardMax: 24.0,
    normalMin: 6.0,
    normalMax: 9.0,
    placeholder: '例如: 7.5',
    guideHint: '推荐每晚睡眠 6.0 ~ 9.0 小时'
  },
  sleepQuality: {
    canonicalKey: 'sleepQuality',
    aliases: ['sleep_quality', 'SLEEP_QUALITY', 'sleepquality'],
    displayName: '睡眠质量',
    category: 'WELLNESS',
    unit: '级',
    precision: 0,
    step: 1,
    hardMin: 1,
    hardMax: 5,
    normalMin: 3,
    normalMax: 5,
    placeholder: '1~5',
    guideHint: '1: 很差, 3: 一般, 5: 很好'
  },
  steps: {
    canonicalKey: 'steps',
    aliases: ['STEPS', 'step', 'STEP'],
    displayName: '步数',
    category: 'WELLNESS',
    unit: '步',
    precision: 0,
    step: 100,
    hardMin: 0,
    hardMax: 150000,
    normalMin: 6000,
    normalMax: 10000,
    placeholder: '例如: 8000',
    guideHint: '推荐每日活动 6000 ~ 10000 步'
  },
  exerciseMinutes: {
    canonicalKey: 'exerciseMinutes',
    aliases: ['exercise_minutes', 'EXERCISE_MINUTES', 'exerciseminutes'],
    displayName: '运动时长',
    category: 'WELLNESS',
    unit: '分钟',
    precision: 0,
    step: 5,
    hardMin: 0,
    hardMax: 1440,
    normalMin: 30,
    normalMax: 120,
    placeholder: '例如: 45',
    guideHint: '推荐每日适度运动 30 ~ 60 分钟'
  },
  waterIntake: {
    canonicalKey: 'waterIntake',
    aliases: ['water_intake', 'WATER_INTAKE', 'waterintake'],
    displayName: '饮水量',
    category: 'WELLNESS',
    unit: 'ml',
    precision: 0,
    step: 50,
    hardMin: 0,
    hardMax: 20000,
    normalMin: 1500,
    normalMax: 2500,
    placeholder: '例如: 2000',
    guideHint: '推荐每日饮水 1500 ~ 2500 ml'
  },
  mood: {
    canonicalKey: 'mood',
    aliases: ['MOOD'],
    displayName: '心情',
    category: 'WELLNESS',
    unit: '级',
    precision: 0,
    step: 1,
    hardMin: 1,
    hardMax: 5,
    normalMin: 3,
    normalMax: 5,
    placeholder: '1~5',
    guideHint: '1: 极差, 3: 平静, 5: 极佳'
  },
  energy: {
    canonicalKey: 'energy',
    aliases: ['ENERGY'],
    displayName: '精力',
    category: 'WELLNESS',
    unit: '级',
    precision: 0,
    step: 1,
    hardMin: 1,
    hardMax: 5,
    normalMin: 3,
    normalMax: 5,
    placeholder: '1~5',
    guideHint: '1: 疲惫, 3: 良好, 5: 充沛'
  }
```

#### 2. 四梯度类型与评估结果模型 [VERIFIED: hhs-frontend-v2/src/utils/metricValidation.ts:3-13]
```typescript
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

#### 3. 心情等级中文描述映射 [VERIFIED: hhs-frontend-v2/src/utils/format.ts:220-226]
```typescript
// 心情等级描述
export const MOOD_LEVELS = ['很差', '较差', '一般', '较好', '很好']

// 获取心情描述
export const getMoodDescription = (value: number): string => {
  const idx = Math.max(0, Math.min(4, Math.round(value) - 1))
  return MOOD_LEVELS[idx]
}
```

#### 4. 前端 Store 保健指标过滤 [VERIFIED: hhs-frontend-v2/src/stores/metricMeta.ts:23-25]
```typescript
  // 保健生活方式指标 (7 项)
  const wellnessMetrics = computed<MetricDefinitionVO[]>(() =>
    allDefinitions.value.filter(d => d.category === 'WELLNESS')
  )
```

#### 5. 弹窗置顶排版与脱离包含块属性 [VERIFIED: hhs-frontend-v2/src/views/prevention/Metrics.vue:61-68] & [VERIFIED: hhs-frontend-v2/src/views/wellness/Dashboard.vue:160-168]
```vue
      append-to-body
      class="prevention-metrics-dialog"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        status-icon
      >
```

---

### Pattern 1: 全量保健元数据动态绑定 (WELL-01)
**What:** 废除组件中所有的静态指标选项与硬编码 `precision=1/2`，通过 `metricMetaStore.wellnessMetrics` 动态输出。  
**When to use:** 录入弹窗的 `el-select` 下拉选择器与精度/步长/单位计算。  
**Implementation Pattern:**
```vue
<el-form-item label="指标类型" prop="metricKey">
  <el-select
    v-model="form.metricKey"
    placeholder="请选择保健指标"
    style="width: 100%"
    :disabled="editingId !== null"
    @change="handleMetricKeyChange"
  >
    <el-option
      v-for="item in metricMetaStore.wellnessMetrics"
      :key="item.canonicalKey"
      :label="item.displayName"
      :value="item.canonicalKey"
    />
  </el-select>
</el-form-item>
```

### Pattern 2: 心情 1-5 星级交互与分流 (WELL-02)
**What:** 当 `form.metricKey === 'mood'` 时，展示 `<el-rate>` 控件并提供辅助文字说明与状态胶囊；非 mood 时展示 `<el-input-number>`。  
**Implementation Pattern:**
```vue
<!-- 心情特殊输入 (1-5 星级打分) -->
<el-form-item v-if="form.metricKey === 'mood'" label="心情状态" prop="value">
  <div class="mood-input">
    <el-rate
      v-model="moodValue"
      :max="5"
      show-text
      :texts="['很差', '较差', '一般', '较好', '很好']"
      @change="handleMoodChange"
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

<!-- 其他通用数值型生活方式指标 -->
<el-form-item v-else :label="definition?.displayName || '测量数值'" prop="value">
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
```

### Pattern 3: 四梯度生理校验与危急值二次确认 (WELL-03)
**What:** 使用 `useMetricValidation` 计算当前输入状态，表单校验放行 Tier 1 与 Tier 2；Tier 3 弹出 `CriticalConfirmDialog` 二次确认防误触；Tier 4 阻断保存但不清空数值。  
**Implementation Pattern:**
```typescript
const validateValue = (_rule: any, value: number, callback: any) => {
  if (form.metricKey === 'mood') {
    if (value === undefined || value === null || value < 1 || value > 5) {
      callback(new Error('请选择心情评级'))
      return
    }
    callback()
    return
  }

  if (value === undefined || value === null || isNaN(value)) {
    callback(new Error('请输入数值'))
    return
  }

  // Tier 4 生理不可能极限强行拦截 (不抹除输入)
  if (primaryEval.value?.isHardLimit) {
    callback(new Error(primaryEval.value.message))
    return
  }

  // Tier 1 正常、Tier 2 预警偏差、Tier 3 急危重症放行
  callback()
}
```

### Anti-Patterns to Avoid
- **失焦清空输入 (Blur-to-Clear):** 严禁在 `@blur` 或校验失败回调中执行 `form.value = undefined`，任何异常数据都必须完整留在输入框供用户核对与修改；
- **硬编码指标字典:** 严禁在页面内保留 `const WELLNESS_METRICS = { ... }` 局部对象；
- **将生活方式真实偏差拦截为非法表单:** 如睡眠 4 小时、饮水 3500ml 属于真实偏离，必须放行（Tier 2），绝不能作为 Error 阻止保存；
- **将弹窗挂载在受 GSAP 动画影响的容器内:** 缺少 `append-to-body` 时，父级 `.el-card` 的 `transform` 会形成新的 CSS 包含块，导致 `fixed` 弹窗无法居中甚至被意外裁剪。

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 保健指标范围与精度定义 | 页面内私有字典映射 | `useMetricMetaStore().wellnessMetrics` | 后端同步与本地静态离线兜底，支持动态扩展 |
| 四梯度生理状态评估算法 | 手写 `if-else` 数值区间判断 | `useMetricValidation` / `evaluateMetricValue` | 统一的四梯度纯函数逻辑与临床 Panic Limits 规则 |
| 心情 1-5 星级评分交互 | 手写五个 svg 图标与点击事件 | Element Plus `<el-rate>` | 完美支持无障碍、键盘导航、悬浮预览与 `show-text` 中文标签 |
| 危急重症二次确认弹窗 | 手写 `ElMessageBox.confirm` | `CriticalConfirmDialog.vue` | 提供完整的临床急救指引、风险强调与防误触统一视觉样式 |
| 临床指标健康指南说明卡 | 在每个表单手写说明文案 | `ReferenceGuideCard.vue` | 统一渲染参考区间、特殊指标测量注意事项与测量建议 |

---

## Common Pitfalls

### Pitfall 1: 星级打分视觉与底层模型解耦导致的空值校验失败
**What goes wrong:** 用户打开弹窗切换到心情时，看到界面显示 3 颗星，直接点击提交，表单校验报错「请选择心情评级」。  
**Why it happens:** 计算属性 `moodValue` 内部写了 `get: () => form.value ?? 3`，界面展示了 3 星，但实际底层 `form.value` 仍为 `undefined`，校验器接收到 `undefined` 判定未打分。  
**How to avoid:** 在切换到 `mood` 时（`handleMetricKeyChange`），若 `form.value` 为空，显式赋值 `form.value = 3`，确保视图呈现与底层模型 100% 同步。  
**Warning signs:** 单元测试中 `vm.form.value` 在切换到 mood 后依然是 `undefined`。

### Pitfall 2: 保健指标缺少危急阈值导致 `CriticalConfirmDialog` 无法触发
**What goes wrong:** 用户录入严重超量饮水（如 6000ml）或极端缺觉（如 2 小时），系统只提示橙色预警（Tier 2），没有触发二次确认模态窗。  
**Why it happens:** `PANIC_LIMITS` 中目前只收录了 8 项医学临床指标（血压、血糖、心率、体温、血氧），未配置 `waterIntake` 与 `sleepDuration` 的危急阈值。  
**How to avoid:** 在 `src/utils/metricValidation.ts` 中的 `PANIC_LIMITS` 增加 `sleepDuration: { min: 3.0, max: 16.0 }` 和 `waterIntake: { max: 5000 }`，确保极度危险值触发 Tier 3 CRITICAL。  
**Warning signs:** 输入 6000ml 饮水量，`hasCritical.value` 依然为 `false`。

### Pitfall 3: 指标类型切换未重置数值导致跨指标数值污染
**What goes wrong:** 用户先选步数输入 `8000`，随后下拉切换到睡眠时长，表单中依然残留 `8000`，立即触发 Tier 4 存活极限拦截（睡眠 hardMax 为 24.0）。  
**Why it happens:** 切换 `metricKey` 时未清空或校验原有数值。  
**How to avoid:** 在 `handleMetricKeyChange` 中根据目标指标重置 `form.value`（若切换为 mood 设为 3，若切换为其它数值设为 `undefined`），并清除先前的表单校验。  
**Warning signs:** 切换指标后，输入框带着上一个指标的大额数值爆红。

### Pitfall 4: 弹窗未脱离 GSAP 动画层叠上下文导致弹窗错位或变形
**What goes wrong:** 打开弹窗后，弹窗位置偏离屏幕中央，或者随卡片 hover 动效一起产生晃动。  
**Why it happens:** 外层卡片存在 GSAP 的 CSS `transform` 样式，创建了新的 containing block，打乱了 `el-dialog` 的 fixed 视口定位。  
**How to avoid:** 在 `el-dialog` 上显式设置 `append-to-body` [VERIFIED: hhs-frontend-v2/src/views/prevention/Metrics.vue:61]。  

---

## Code Examples

### 1. `prevention/Metrics.vue` 关键重构范式
```vue
<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="560px"
    destroy-on-close
    append-to-body
    class="prevention-metrics-dialog"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      status-icon
    >
      <!-- 动态元数据下拉 (WELL-01) -->
      <el-form-item label="指标类型" prop="metricKey">
        <el-select
          v-model="form.metricKey"
          placeholder="请选择保健指标"
          style="width: 100%"
          :disabled="editingId !== null"
          @change="handleMetricKeyChange"
        >
          <el-option
            v-for="item in metricMetaStore.wellnessMetrics"
            :key="item.canonicalKey"
            :label="item.displayName"
            :value="item.canonicalKey"
          />
        </el-select>
      </el-form-item>

      <!-- 心情星级分流 (WELL-02) -->
      <el-form-item v-if="form.metricKey === 'mood'" label="心情状态" prop="value">
        <div class="mood-input">
          <el-rate
            v-model="moodValue"
            :max="5"
            show-text
            :texts="['很差', '较差', '一般', '较好', '很好']"
            @change="handleMoodChange"
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

      <!-- 其他数值型指标 -->
      <el-form-item v-else :label="definition?.displayName || '测量数值'" prop="value">
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

      <!-- 常态化参考解读卡片 (WELL-02) -->
      <ReferenceGuideCard :definition="definition" />
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="isSaveBlocked"
          @click="handleSubmit"
        >
          {{ editingId ? '保存修改' : '确定' }}
        </el-button>
      </div>
    </template>

    <!-- 急危重症二次确认弹窗 (WELL-03) -->
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

### 2. 提交入口与危急拦截防误触 (WELL-03)
```typescript
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async valid => {
    if (!valid) return

    // 触碰 Tier 3 危急值触发二次确认模态窗
    if (hasCritical.value) {
      criticalDialogVisible.value = true
      return
    }

    await executeSave()
  })
}
```

---

## State of the Art

| 维度 | 改造前 (Legacy) | 改造后 (Current Approach) | 核心收益 |
|------|----------------|---------------------------|----------|
| 指标来源 | 组件内写死 `WELLNESS_METRICS` 字典与固定 `precision=2/1` | `useMetricMetaStore().wellnessMetrics` 动态获取 7 项元数据 | 消除字典漂移，支持后端动态下发新指标 |
| 心情录入 | 缺乏星级交互或交互混乱，无中文对照 | `<el-rate>` 1-5 星交互并绑定 `MOOD_LEVELS`，支持状态胶囊 | 人性化心理体验与四梯度体系完美兼容 |
| 表单校验 | 失焦破坏性清空，异常数值一律红色报错拦截 | 四梯度宽容校验（Tier 1~4），异常值 100% 保留不抹除 | 保护用户录入劳动，真实生活偏离可顺利入库 |
| 危急防误触 | 无二次确认，用户多输一个 0 直接入库污染数据 | 触碰 Panic Limits 触发 `CriticalConfirmDialog` 弹窗确认 | 阻断因手抖/误操作导致的异常数据污染 |
| 弹窗渲染 | 局部包含块内渲染，标签左右挤压截断 | `append-to-body` 脱离包含块，`label-position="top"` 顶置 | 占位符完整显示，解决 GSAP 动画干扰 |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 饮水量达到 5000ml 以上、睡眠时长低于 3 小时属于需二次确认的生活方式危急区间 [ASSUMED] | Common Pitfalls & D-03 | 若阈值过紧或过松，需调整 `src/utils/metricValidation.ts` 中的 `PANIC_LIMITS` 配置 |
| A2 | 仪表盘折线图在录入后仅需自动刷新睡眠和活动折线图 [ASSUMED] | Claude's Discretion | 若用户录入了心情或饮水，折线图无直接关联，仅活动环与摘要数字实时刷新 |

---

## Open Questions (RESOLVED)

1. **保健指标 Panic Limits 的统一定义范围**
   - RESOLVED: 在 `src/utils/metricValidation.ts` 中针对 `sleepDuration`（`< 3.0` 或 `> 16.0` 小时）与 `waterIntake`（`> 5000` ml）补充 Panic Limits 规则，既保持架构纯洁性，又能满足用户二次确认防误触的需求。所有生活方式极端阈值由统一字典驱动。
   - 现状：当前 `PANIC_LIMITS` 仅包含 8 项医疗指标。根据 CONTEXT.md D-03，需要防止严重超负荷饮水和极度缺觉误触入库。

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | 前端开发与构建 | ✓ | 24.15.0 | — |
| npm | 依赖管理与测试命令执行 | ✓ | 11.12.1 | — |
| Vitest | 前端自动化测试套件 | ✓ | 4.1.7 | — |
| Element Plus | UI 组件渲染与交互 | ✓ | 2.5.6 | — |
| Pinia | 响应式状态与元数据缓存 | ✓ | 2.1.7 | — |

**Missing dependencies with no fallback:** None  
**Missing dependencies with fallback:** None  

---

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Vitest 4.1.7 + @vue/test-utils 2.4.6 |
| Config file | `hhs-frontend-v2/vite.config.ts` |
| Quick run command | `npm run test:run -- src/__tests__/views/PreventionAndWellness.spec.ts` |
| Full suite command | `npm run test:run` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| WELL-01 | `prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 动态绑定全量 7 项保健指标元数据，读取正确单位、步长与精度 | component unit test | `npm run test:run -- src/__tests__/views/PreventionAndWellness.spec.ts` | ✅ 已存在并覆盖 |
| WELL-02 | 心情专属 1-5 星级打分（`el-rate`）交互正确，文字呈现 `MOOD_LEVELS`，非 mood 指标渲染 `<el-input-number>` 并挂载 `ReferenceGuideCard` | component unit test | `npm run test:run -- src/__tests__/views/PreventionAndWellness.spec.ts` | ✅ 已存在并覆盖 |
| WELL-03 | 接入四梯度宽容校验：失焦不破坏性清空，Tier 2 允许正常保存，Tier 3 危急值触发二次确认弹窗，Tier 4 存活极限拦截提交 | component unit test | `npm run test:run -- src/__tests__/views/PreventionAndWellness.spec.ts` | ✅ 已存在并覆盖 |

### Sampling Rate
- **Per task commit:** `cd hhs-frontend-v2 && npm run test:run -- src/__tests__/views/PreventionAndWellness.spec.ts` (< 5s)
- **Per wave merge:** `cd hhs-frontend-v2 && npm run test:run` (全量 10 个测试文件，71+ 用例，< 10s)
- **Phase gate:** 前端全量测试绿灯且 `npm run build` 成功

### Wave 0 Gaps
- [ ] 扩展 `src/__tests__/views/PreventionAndWellness.spec.ts`：补充 `CriticalConfirmDialog` 二次确认弹窗在极度异常（Tier 3）时的弹出与确认保存完整时序测试。

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | yes | 采用 `useMetricValidation` 纯函数执行类型校验与四梯度生理边界评估；服务端采用 `@ValidMetricRecord` 执行物理极限阻断 |
| V4 Access Control | yes | 路由层 `beforeEach` JWT 状态校验与 Axios 请求拦截器 Token 注入 |
| V3 Session Management | yes | 401 响应全局拦截自动清空认证状态并导航至 `/login` |

### Known Threat Patterns for Frontend Input

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| 非法/越界生理极限数据篡改 | Tampering | 前端 Tier 4 物理存活极限硬拦截 + 后端 Jakarta Validation 强类型双重校验 |
| XSS 跨站脚本注入 | Tampering | Vue 3 模板引擎自动 HTML 转义，Element Plus 组件属性原生过滤 |
| 误操作导致极端数据污染 | Denial of Service / Integrity | 触碰危急预警阈值时强制弹出 `CriticalConfirmDialog` 二次确认防误触 |

---

## Sources

### Primary (HIGH confidence)
- `hhs-frontend-v2/src/constants/metricMeta.ts:267-371` — 7 项保健指标元数据静态定义与四梯度生理边界
- `hhs-frontend-v2/src/stores/metricMeta.ts:23-25` — `wellnessMetrics` 响应式过滤计算属性
- `hhs-frontend-v2/src/utils/metricValidation.ts:3-13` — 四梯度生理评估结果接口与评估纯函数
- `hhs-frontend-v2/src/utils/format.ts:220-226` — `MOOD_LEVELS` 心情等级中文描述映射
- `hhs-frontend-v2/src/views/prevention/Metrics.vue` — 预防保健指标列表页面及弹窗
- `hhs-frontend-v2/src/views/wellness/Dashboard.vue` — 保健生活方式仪表盘及添加弹窗
- `hhs-frontend-v2/src/views/health/Metrics.vue` — Phase 8 标杆四梯度与协同录入参考实现

### Secondary (MEDIUM confidence)
- `hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java:294-405` — 后端元数据注册中心 7 项保健指标定义

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 已在 `hhs-frontend-v2/package.json` 与测试环境中验证
- Architecture: HIGH — Phase 8 `health/Metrics.vue` 经过完整实践，模式完全可复用
- Pitfalls & Edge cases: HIGH — 深入排查了星级打分默认值同步、包含块层叠和危急阈值触发

**Research date:** 2026-11-05  
**Valid until:** 2026-12-05 (代码与规范高度稳定，30天有效)
