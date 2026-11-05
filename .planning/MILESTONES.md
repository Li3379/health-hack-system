# Milestones

## v1.2 全站健康与保健指标录入标准化与体验对齐 (Shipped: 2026-11-05)

**Phases completed:** 7 phases, 15 plans, 11 tasks

**Key accomplishments:**

- Immutable single source of truth (SSOT) metric registry for 23 metrics, multi-variant key normalizer, and public HTTP-cached metadata endpoint
- Jakarta class-level physiological limit validation `@ValidMetricRecord`, transparent service query alias expansion with SQL IN clauses, and idempotent batch data cleansing migration patch
- 全面重构健康指标管理页、保健指标管理页与生活方式看板录入弹窗，彻底废除私有硬编码字典，统一接入权威元数据、血压协同、心情打分、四梯度非破坏性校验与危急值二次确认保护。
- 后端 AI 解析与 OCR 体检识别全面接入权威元数据注册中心与化验单扩展注册表，通过通用接口 MetricValueProvider 在全渠道入库 DTO 网关层统一拦截 Tier 4 生理极限。
- CrossChannelConsistencyTest 后端跨渠道一致性测试与 HealthMetrics / WellnessMetrics 前端管理页弹窗测试套件落地，完成手动录入、AI 解析与 OCR 识别三端指标标准与校验界限的端到端自动化质量闭环

---
