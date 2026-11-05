# Phase 7: 全渠道校验对齐与跨端测试验证 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-05  
**Phase:** 7-全渠道校验对齐与跨端测试验证  
**Areas discussed:** AI提示词对齐, OCR扩展指标, 全渠道异常判定  

---

## 1. AI提示词对齐 (SYNC-01)

| Option | Description | Selected |
|--------|-------------|----------|
| 动态组装提示词（推荐） | 启动时或初始化时直接从 HealthMetricMetaRegistry 动态生成/填充 AI 解析的系统提示词表格，确保注册中心与大模型提示词单点维护、零偏差。 | ✓ |
| 模板插槽替换注入 | 保留结构化的 Markdown 模板文件，但将指标对照表、单位与范围字段通过注册表动态插槽替换注入。 | |

**User's choice:** 动态组装提示词（推荐）  
**Notes:** 彻底消灭散落在 Java 源码中的多份重复表格硬编码，达到真正的单点真实源（SSOT）。

---

## 2. OCR扩展指标 (SYNC-01)

| Option | Description | Selected |
|--------|-------------|----------|
| 核心注册表+化验扩展字典（推荐） | 优先查询 HealthMetricMetaRegistry 的 23 项核心指标；对于血常规等化验单专有指标（如血红蛋白、白细胞等）通过专门的体检扩展字典兜底。 | ✓ |
| 全量并入核心注册表 | 将血常规指标（hemoglobin, wbc, rbc, platelet 等）全量合并至 HealthMetricMetaRegistry 统一管理。 | |

**User's choice:** 核心注册表+化验扩展字典（推荐）  
**Notes:** 保持核心日常监测指标库的纯粹性与高内聚，同时支持体检报告专有化验项的扩展识别。

---

## 3. 全渠道异常判定 (VAL-02, VAL-03, VAL-05, SYNC-01)

| Option | Description | Selected |
|--------|-------------|----------|
| 四梯度统一判定+极限校验（推荐） | AI 解析与 OCR 识别结果直接复用 Phase 4/6 的四梯度模型判定结果（正常、预警偏差、危急重症），并在保存时统一执行 @ValidMetricRecord 物理极限校验。 | ✓ |
| 二元标记+入库极限拦截 | AI/OCR 解析阶段仅对比 normalMin/normalMax 输出二元异常标记（isAbnormal 0/1），仅在入库持久化时执行物理极限拦截。 | |

**User's choice:** 四梯度统一判定+极限校验（推荐）  
**Notes:** 保证用户在手动录入、智能 AI 文本分析、OCR 体检报告上传三种场景下看到的预警标准与健康评估等级 100% 相同。

---

## Claude's Discretion

- `AiParseServiceImpl.java` 内部动态提示词的预热与缓存性能优化。
- `LabRoutineMetricRegistry` 的实体结构与包路径设计。
- 自动化跨渠道端到端集成测试用例的设计与断言覆盖。

## Deferred Ideas

- v1.2+: 支持体检报告多页 PDF 批量上传与自适应表格跨页拼接。
- v1.2+: 多模态大模型视觉端直接提取化验单图片数据。
