# Phase 5: 前端元数据 Store 与全站格式化防英文外露 - Context

**Gathered:** 2026-11-04  
**Phase:** 5 — 前端元数据 Store 与全站格式化防英文外露  
**Status:** Ready for planning  

<domain>
## Phase Boundary

本阶段构建前端响应式指标元数据体系与全站显示防护层。交付内容包括：
1. 本地权威静态常量定义 `@/constants/metricMeta.ts`（包含 23 项指标离线兜底定义）；
2. 客户端 Key 双向归一化与缩写保护工具 `@/utils/metricNormalizer.ts`；
3. Pinia 状态管理库 `useMetricMetaStore`（对接后端 `GET /api/metrics/metadata`，带缓存与离线兜底）；
4. 重构 `@/utils/format.ts`，在 100% 保持既有函数签名兼容的前提下，彻底杜绝全系统界面外露 `blood_glucose`、`heart_rate` 等原始英文代码；
5. 清理全站组件中私有重复的硬编码字典（如 `prevention/Metrics.vue` 中的 `getMetricLabelLocal`）。

*（注：Phase 4 已完成后端权威注册表与 REST 接口；Phase 6 将在此基础上重构录入表单引导与四梯度校验交互）*
</domain>

<decisions>
## Implementation Decisions

### 1. Store 加载时机与离线兜底机制 (META-03)
- **D-01 (静态离线兜底常量):** 创建 `@/constants/metricMeta.ts`，完整同步 Phase 4 确立的 23 种指标属性（中文名称、单位、精度、步长、参考范围、生理极限）。作为 Pinia Store 的初始静态 state，保障即使网络离线、后端尚未就绪或弱网情况下，前端依然具备 100% 完整的中文格式化能力，杜绝白屏与未定义异常。
- **D-02 (应用挂载全局预加载):** 在 `App.vue` 的 `onMounted()` 阶段静默调用 `useMetricMetaStore().fetchMetadata()`。得益于 Phase 4 的 HTTP 86400 强缓存与 ETag 机制，日常访问几乎 0 开销；数据返回后平滑增量合并至 store，用户无任何感知与界面闪烁。

### 2. 全站格式化升级与向后兼容策略 (KEY-03)
- **D-03 (透明增强向后兼容):** 重构 `@/utils/format.ts` 中的 `getMetricDisplayName`、`getMetricLabel` 与 `getMetricUnit`。保持现有函数签名不变（接收 `metricKey: string`，返回 `string`）。内部透明集成 `metricNormalizer.ts` 与 Store（或 fallback 常量字典），所有现有视图组件（`Metrics.vue`, `Alerts.vue`, `Thresholds.vue` 等）无需修改模板与导入语句，即可自动获得下划线、大写及别名的规范化中文呈现。
- **D-04 (私有硬编码清理):** 清除 `hhs-frontend-v2/src/views/prevention/Metrics.vue` 中的私有 `getMetricLabelLocal` 硬编码逻辑，全站统一收敛至 `@/utils/format.ts`。

### 3. 未知 Key 优雅降级策略 (KEY-03)
- **D-05 (可读单词化转换):** 当遇到字典完全未收录的全新/自定义指标 Key 时，`metricNormalizer.ts` 自动将下划线与小驼峰分词并转换为可读的首字母大写词组（例如 `vo2_max` 转换为 `Vo2 Max`，`unknown_metric` 转换为 `Unknown Metric`），杜绝丑陋的代码下划线直接暴露破坏排版。

### Claude's Discretion
- `metricNormalizer.ts` 内部的正规化正则表达式与性能缓存设计；
- 前端 TypeScript 接口定义 (`MetricDefinitionVO`, `MetricMetaMap`) 与后端 DTO 的精确契约映射；
- 前端单元测试（Vitest）用例设计，确保所有 23 种指标及其别名在 `format.ts` 下的测试断言。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 上游设计与阶段成果
- `.planning/phases/04-key/04-CONTEXT.md` — Phase 4 锁定的 23 种指标权威标准 Key、别名与数值边界
- `.planning/phases/04-key/04-01-SUMMARY.md` — 后端元数据接口契约 (`GET /api/metrics/metadata` 结构与 ETag)
- `.planning/phases/04-key/04-02-SUMMARY.md` — 后端 Key 归一化规则与 DTO 序列化标准
- `.planning/research/SUMMARY.md` — HHS Milestone v1.1 全景技术与架构总结

### 前端现有代码（修改与集成锚点）
- `hhs-frontend-v2/src/utils/format.ts` — 核心重构目标，现有 `getMetricDisplayName` / `getMetricUnit`
- `hhs-frontend-v2/src/App.vue` — Store 预加载入口
- `hhs-frontend-v2/src/stores/` — 现有 Pinia 状态库目录
- `hhs-frontend-v2/src/views/health/Metrics.vue` — 使用 `getMetricLabel` 的指标管理主页面
- `hhs-frontend-v2/src/views/health/Alerts.vue` — 使用 `getMetricLabel` 的预警中心页面
- `hhs-frontend-v2/src/views/health/Thresholds.vue` — 使用 `getMetricLabel` 的阈值配置页面
- `hhs-frontend-v2/src/views/prevention/Metrics.vue` — 存在私有 `getMetricLabelLocal` 的页面

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `hhs-frontend-v2/src/api/health.ts`: 现有健康指标 API 封装，可在此新增 `getMetricMetadataApi()`。
- `hhs-frontend-v2/src/stores/auth.ts`: Pinia store 样板与响应式模式参考。
- `hhs-frontend-v2/src/utils/format.ts`: 现存时间、日期格式化工具。

### Established Patterns
- Pinia `defineStore('metricMeta', () => { ... })` 组合式 API 风格。
- TypeScript 严格类型与全局导出规范。
- Element Plus 辅助工具库。

### Integration Points
- 新增 `hhs-frontend-v2/src/constants/metricMeta.ts`：静态常量元数据字典。
- 新增 `hhs-frontend-v2/src/utils/metricNormalizer.ts`：Key 归一化与分词工具。
- 新增 `hhs-frontend-v2/src/api/metric-meta.ts`：REST API 请求模块。
- 新增 `hhs-frontend-v2/src/stores/metricMeta.ts`：Pinia 状态管理。
- 修改 `hhs-frontend-v2/src/utils/format.ts`：重写指标格式化底层逻辑。
- 修改 `hhs-frontend-v2/src/App.vue`：挂载时自举预加载。
- 修改 `hhs-frontend-v2/src/views/prevention/Metrics.vue`：移除冗余私有字典。

</code_context>

<specifics>
## Specific Ideas

- **前端常量与别名表：**
  - 同步 Phase 4 的 23 项指标：
    - HEALTH: `heartRate`, `systolicBP`, `diastolicBP`, `bloodGlucose`, `fastingGlucose`, `postprandialGlucose`, `bodyTemperature`, `weight`, `bmi`, `bloodOxygen`, `totalCholesterol`, `triglycerides`, `hdlCholesterol`, `ldlCholesterol`, `uricAcid`, `creatinine`
    - WELLNESS: `sleepDuration`, `sleepQuality`, `steps`, `exerciseMinutes`, `waterIntake`, `mood`, `energy`
  - 缩写精准保护：`systolic_bp` / `systolicBp` / `sbp` -> `systolicBP`；`diastolic_bp` / `diastolicBp` / `dbp` -> `diastolicBP`
  - 血糖兼容：`blood_glucose` / `glucose` -> `bloodGlucose`
  - 体温兼容：`body_temperature` / `temperature` / `temp` -> `bodyTemperature`

</specifics>

<deferred>
## Deferred Ideas

- **Phase 6 承接：** 表单组件（`QuickInputDialog.vue`）彻底移除失焦抹除，增加占位符引导与四梯度胶囊展示。
- **Phase 7 承接：** AI 智能解析与 OCR 报告识别对齐统一元数据。

</deferred>

---

*Phase: 05-前端元数据 Store 与全站格式化防英文外露*  
*Context gathered: 2026-11-04*  
