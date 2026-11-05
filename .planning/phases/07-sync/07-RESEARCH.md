# Phase 7: 全渠道校验对齐与跨端测试验证 - Research

**Researched:** 2026-11-05
**Domain:** Cross-Channel Data Ingestion, Dynamic Prompt Engineering, OCR Lab Extension, Four-Tier Clinical Validation, E2E Consistency Testing
**Confidence:** HIGH

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01 (动态组装提示词):** 在 `AiParseServiceImpl.java` 中，彻底废除写死在代码中的 `PARSE_SYSTEM_PROMPT` 静态 Markdown 表格。改为在 Spring Bean 初始化时或动态构建时，直接从 `@Autowired HealthMetricMetaRegistry` 中提取全部已注册指标的标准 Key、权威中文名称、标准单位、正常参考范围，动态生成结构化提示词表格。无论后续注册表新增或微调何种指标，大模型提示词均保持单点权威、零配置偏差。
- **D-02 (核心注册表+化验扩展字典):** 
  - `OcrServiceImpl.java` 提取健康与保健指标时，优先委派给 `HealthMetricMetaRegistry` 进行权威 Key 规范化、单位补全与临床参考区间查询；
  - 针对专属化验单/体检报告的血常规指标（如 `hemoglobin` 血红蛋白、`wbc` 白细胞、`rbc` 红细胞、`platelet` 血小板），维护清晰的 `LabRoutineMetricRegistry` 或结构化扩展映射，作为医学体检报告领域的专门补充，既不污染通用健康监测核心注册表，又保证体检报告识别的完整性。
- **D-03 (四梯度统一判定+入库物理极限校验):**
  - AI 解析结果与 OCR 提取结果中的预警/异常标注逻辑，废除原有的二元宽松规则，完全对齐四梯度模型（Tier 1 正常、Tier 2 预警偏差、Tier 3 急危重症）；
  - 所有渠道（手动录入、AI 提取确认、OCR 结果确认）最终提交入库时，请求 DTO 统一通过 `@ValidMetricRecord` Jakarta 类级别注解拦截不可能极限数值（Tier 4），保障数据库与下游健康评分引擎免受恶性畸变数据污染。
- **D-04 (统一血压拆分与规范化 Key 保证):**
  - AI 解析与 OCR 识别中提取到“血压”组合数据时，继续严格执行拆分为 `systolicBP` 与 `diastolicBP` 的标准约定；
  - 彻底杜绝下游数据库存入非法的 `bloodPressure` 混合 key，确保与 Phase 4/5/6 确立的小驼峰标准 Key 保持 100% 统一。
- **D-05 (全页面统一对齐 Phase 6 规范):**
  - `health/Metrics.vue`（健康指标管理页）弹窗：全面改用 `useMetricMetaStore` 动态指标列表，废除 7 个写死 `el-option` 和 `metricUnitMap`；接入 `useMetricValidation`、`ReferenceGuideCard`、`CriticalConfirmDialog`，血压支持高低压双字段联动与一键对调，非破坏性失焦保护；
  - `prevention/Metrics.vue` 与 `wellness/Dashboard.vue`（保健生活管理页）弹窗：彻底废除私有 `WELLNESS_METRICS` 字典与硬编码精度，接入元数据 Store；保留心情（mood）星级评分，其他数值型生活指标接入四梯度宽容校验与常态化参考卡片；
  - 无论新增还是编辑模式，输入异常均不抹除用户输入，急危重症二次确认后仍可保存入库。

### Claude's Discretion

- `AiParseServiceImpl.java` 动态提示词的字符串模板缓存设计（初始化时预生成，避免每次解析重复拼接）；
- `OcrServiceImpl.java` 中化验单血常规扩展指标常量的包结构组织；
- 跨渠道全链路集成测试用例数据设计。

### Deferred Ideas (OUT OF SCOPE)

- **v1.2 规划：** 支持体检报告多页 PDF 批量上传与自适应表格跨页拼接。
- **v1.2 规划：** 多模态大模型视觉端直接提取化验单图片数据。
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SYNC-01 | 重构 `AiParseServiceImpl.java` 与 OCR 图片识别确认流程，底层完全接入权威元数据注册表，确保 AI 解析提示、识别校验与手动表单规则 100% 统一 | 通过注入 `HealthMetricMetaRegistry` 动态构建大模型 System Prompt 与四梯度预警判定；通过引入 `LabRoutineMetricRegistry` 解耦血常规化验单专属指标；通过扩展 `@ValidMetricRecord` 支持多 DTO 入库物理极限拦截 |
| D-05 (HEALTH) | `src/views/health/Metrics.vue` 弹窗重构：废除 7 个写死选项与 `metricUnitMap`，接入 `useMetricMetaStore`、`useMetricValidation`、`ReferenceGuideCard`、`CriticalConfirmDialog` 与血压一键对调 | 复用 Phase 6 建立的 `useMetricValidation` 与标准卡片/弹窗组件，统一健康指标列表页的新增与编辑体验 |
| D-05 (WELL) | `src/views/prevention/Metrics.vue` 与 `src/views/wellness/Dashboard.vue` 弹窗重构：废除私有 `WELLNESS_METRICS` 字典与写死精度，接入权威元数据 Store，保留心情星级评分 | 保留 mood 特有的 1-5 星级打分组件，其余数值型生活指标动态绑定元数据 step/precision 与常态化解读卡片 |
</phase_requirements>

## Summary

Phase 07 是 HHS 系统健康与保健数据采集链路的“大收口”阶段，彻底解决各端独立演进导致的割裂问题：
1. **AI 解析动态对齐**：后端 `AiParseServiceImpl.java` 目前写死了包含旧 Key `glucose` 的静态 Markdown 表格与 19 项硬编码数值范围。重构后在 `@PostConstruct` 阶段消费 `HealthMetricMetaRegistry`，动态拼接包含 23 项权威指标的 Markdown 对照表，并消除 `METRIC_RANGES` 私有表，统一调用元数据与归一化工具。
2. **OCR 化验单扩展架构**：后端 `OcrServiceImpl.java` 原有私有 `METRIC_KEY_MAP`、`DEFAULT_UNITS`、`REFERENCE_RANGES` 混杂了核心指标与化验单血常规指标（血红蛋白、白细胞、红细胞、血小板）。重构后通过分层设计，优先委派核心 23 项指标至 `HealthMetricMetaRegistry`，其余血常规专属指标下沉至独立的 `LabRoutineMetricRegistry`，既不污染核心单点事实源，又保障体检化验单的完整识别与参考范围计算。
3. **入库极限拦截统一**：目前 `@ValidMetricRecord` 仅绑定到 `HealthMetricRequest`。重构后抽取轻量接口 `MetricValueProvider`，让 `HealthMetricRequest`、`WellnessMetricRequest`、`ConfirmMetricsRequest.MetricItem`、`OcrConfirmRequest.MetricItem` 统一实现该接口并受 `@ValidMetricRecord` 保护，在 HTTP 边界直接拦截 Tier 4 生理不可能极限。
4. **前端指标管理页对齐**：重构 `src/views/health/Metrics.vue`、`src/views/prevention/Metrics.vue` 和 `src/views/wellness/Dashboard.vue` 的弹窗，废除硬编码的 7 项选项与私有 `WELLNESS_METRICS` 字典，全面引入 `useMetricMetaStore`、`useMetricValidation`、`ReferenceGuideCard.vue`、`CriticalConfirmDialog.vue`，实现血压双字段协同与一键对调。
5. **跨渠道一致性自动化验证**：构建端到端一致性测试套件，覆盖手动录入、AI 对话抽取、OCR 图像识别三条链路，严格验证指标 Key、标准单位、四梯度生理分级判定达到 100% 规则对齐。

**Primary recommendation:** 以 `HealthMetricMetaRegistry` 与 `MetricValueProvider` 为核心轴心，分别重构 AI 解析动态 Prompt、OCR 扩展字典、Jakarta 校验拦截，并在前端各管理页全面推广 Phase 6 沉淀的 `useMetricValidation` 模式。

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 动态 AI 提示词与对照表构建 | Backend (API Service) | — | 提示词包含系统不可变临床元数据，必须在服务端从 `HealthMetricMetaRegistry` 内存单例单点渲染并缓存 |
| 化验单专属血常规指标扩展 | Backend (Domain Registry) | — | 体检报告专属指标（如血红蛋白、白细胞）属于医学检验域，由 `LabRoutineMetricRegistry` 独立管理，不污染通用生活监测 |
| 入库物理极限拦截 (Tier 4) | Backend (Jakarta Validation) | Browser / Client | 服务端通过 `@ValidMetricRecord` 提供最终安全边界，拦截恶意构造或畸变数据；前端做前置即时阻断 |
| 四梯度生理分级判定 (Tier 1~3) | Browser / Client | Backend (AI/OCR Warnings) | 前端负责实时展示友好胶囊文案与二次确认；AI 和 OCR 服务在解析阶段生成解释性 warnings 并下发 |
| 血压双字段协同与一键对调 | Browser / Client | Backend (Normalization) | 前端提供收缩压与舒张压并列输入与原子对调交互；后端确保始终拆分为 `systolicBP` 与 `diastolicBP`，杜绝混合 Key |
| 保健生活指标动态元数据绑定 | Browser / Client | Backend (Metadata API) | 前端通过 Pinia `useMetricMetaStore` 获取步长、精度、常态化参考指南卡片，保留心情星级评分 |
| 跨渠道全链路一致性自动化验证 | Automated Test Suite | — | JUnit 5 + Vitest 测试套件负责自动化断言三端在 Key、单位、分级规则上的 100% 等价性 |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.2.0 | 后端核心框架 | 既有工程基础设施，提供依赖注入与切面支持 [VERIFIED: hhs-backend/pom.xml] |
| Jakarta Bean Validation | 3.0.2 (Hibernate Validator 8.0.1.Final) | 请求入库参数校验 | 原生支持类级别注解与自定义约束，用于 `@ValidMetricRecord` 拦截 Tier 4 极限 [VERIFIED: hhs-backend/pom.xml] |
| LangChain4j | 0.29.1 | 大模型接口交互 | 统一封装阿里云通义千问模型调用与系统提示词消息注入 [VERIFIED: hhs-backend/pom.xml] |
| Vue 3 | 3.4.21 | 前端响应式框架 | 基于 Composition API `<script setup>` 响应式组织指标校验状态 [VERIFIED: hhs-frontend-v2/package.json] |
| Pinia | 2.1.7 | 前端全局状态管理 | `useMetricMetaStore` 统一缓存并共享指标元数据 [VERIFIED: hhs-frontend-v2/package.json] |
| Element Plus | 2.6.1 | 前端组件库 | 提供 `el-input-number`、`el-rate`、`el-dialog`、`el-tag` 基础交互 [VERIFIED: hhs-frontend-v2/package.json] |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 / Mockito | 5.10.1 / 5.7.0 | 后端单元与集成测试 | 编写跨渠道指标等价性与动态提示词集成测试 [VERIFIED: hhs-backend/pom.xml] |
| Vitest | 4.1.7 | 前端单元测试 | 编写 `Metrics.vue` 与 `Dashboard.vue` 交互行为测试 [VERIFIED: hhs-frontend-v2/package.json] |
| @vue/test-utils | 2.4.4 | Vue 组件测试工具 | 挂载与触发弹窗组件的表单校验与一键对调逻辑 [VERIFIED: hhs-frontend-v2/package.json] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| 启动时动态生成 AI Prompt 字符串并缓存 | 每次请求时动态拼接 | 每次请求重复循环拼接 23 项元数据产生不必要的字符串分配开销；在 `@PostConstruct` 初始化生成并缓存为 final 字符串性能更优且线程安全 |
| 建立独立的 `LabRoutineMetricRegistry` | 直接把血常规指标放入 `HealthMetricMetaRegistry` | 直接合并会污染日常健康打卡与生活方式指标字典（导致健康列表多出白细胞、血小板等无法日常自测的指标）；独立化验扩展字典职责更清晰 |
| 多 DTO 实现 `MetricValueProvider` 接口 | 为每个 DTO 分别复制一份 Validator 代码 | 复制 Validator 会导致生理极限校验规则存在多个副本，违反 DRY 原则；接口模式统一委托给单个核心校验逻辑 |

**Installation:**
本阶段无需安装任何新的 npm 或 Maven 外部依赖，完全复用工程既有依赖。

## Package Legitimacy Audit

本阶段未安装任何新的外部包或类库，所有功能均基于既有架构与现有依赖实现。

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| (none) | — | — | — | — | [OK] | 无新增依赖 |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

## Architecture Patterns

### System Architecture Diagram

```
                 [ 多渠道健康数据输入源 ]
   ┌───────────────────┬───────────────────┬──────────────────┐
   │ 1. 手动表单录入   │ 2. AI 智能对话    │ 3. OCR 体检识别  │
   │ (QuickInput /     │ (文本/语音描述)   │ (化验单/报告图片)│
   │ Metrics /         │                   │                  │
   │ Dashboard)        │                   │                  │
   └─────────┬─────────┴─────────┬─────────┴─────────┬────────┘
             │                   │                   │
             │           [动态 Prompt 生成]   [化验扩展识别]
             │                   │                   │
             │       ┌───────────┴──────────┐        │
             │       │ HealthMetricMeta     │        │
             │       │ Registry (权威 SSOT) │◄───────┼───────┐
             │       └───────────┬──────────┘        │       │
             │                   ▼                   ▼       │
             │         AiParseServiceImpl    OcrServiceImpl  │
             │                   │                   │       │
             │                   │           LabRoutineMetric│
             │                   │           Registry (扩展) ┘
             │                   │                   │
             ▼                   ▼                   ▼
   ┌──────────────────────────────────────────────────────────┐
   │               四梯度生理分级判定 (Tier 1 ~ 3)            │
   │  - Tier 1: 临床正常 (Normal)                             │
   │  - Tier 2: 预警偏差 (Warning / Deviated)                 │
   │  - Tier 3: 急危重症 (Critical Panic - 二次确认后允许)     │
   └─────────────────────────────┬────────────────────────────┘
                                 │
                                 ▼
   ┌──────────────────────────────────────────────────────────┐
   │              入库前物理极限校验器 (Tier 4)                │
   │           @ValidMetricRecord (MetricValueProvider)       │
   │       拦截: heartRate=500, systolicBP=999 等不可能值      │
   └─────────────────────────────┬────────────────────────────┘
                                 │ (通过校验)
                                 ▼
   ┌──────────────────────────────────────────────────────────┐
   │            业务持久化层 (Health / Wellness Service)       │
   │       统一小驼峰 Key (systolicBP / diastolicBP / etc.)   │
   └──────────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
hhs-backend/src/main/java/com/hhs/
├── service/
│   ├── domain/
│   │   ├── HealthMetricMetaRegistry.java     # [EXISTING] 23 项核心指标单点权威源
│   │   ├── LabRoutineMetricRegistry.java     # [NEW] 化验单专属血常规指标扩展字典
│   │   ├── MetricKeyNormalizer.java          # [EXISTING] 规范化与别名工具
│   │   ├── MetricValueProvider.java          # [NEW] 统一指标数据提供者接口
│   │   └── MetricDefinition.java             # [EXISTING] 不可变 Record 实体
│   └── impl/
│       ├── AiParseServiceImpl.java           # [REFACTOR] 动态装配提示词与元数据边界
│       └── OcrServiceImpl.java               # [REFACTOR] 接入元数据注册表与化验扩展
├── validation/
│   ├── ValidMetricRecord.java                # [UPDATE] 扩展支持多 DTO 类级别约束
│   └── MetricRecordValidator.java            # [UPDATE] 支持 MetricValueProvider 校验
└── dto/
    ├── HealthMetricRequest.java              # [EXISTING] 实现 MetricValueProvider
    ├── WellnessMetricRequest.java            # [UPDATE] 实现 MetricValueProvider 并加注解
    ├── ConfirmMetricsRequest.java            # [UPDATE] MetricItem 加 @ValidMetricRecord
    └── OcrConfirmRequest.java                # [UPDATE] MetricItem 加 @ValidMetricRecord

hhs-frontend-v2/src/
├── views/
│   ├── health/
│   │   └── Metrics.vue                       # [REFACTOR] 接入 useMetricMetaStore 与四梯度
│   ├── prevention/
│   │   └── Metrics.vue                       # [REFACTOR] 废除私有字典，保留心情星级
│   └── wellness/
│       └── Dashboard.vue                     # [REFACTOR] 废除私有字典，接入权威元数据
└── __tests__/views/
    ├── HealthMetrics.spec.ts                 # [NEW] 健康指标列表页弹窗交互测试
    └── WellnessMetrics.spec.ts               # [NEW] 保健指标列表页弹窗交互测试
```

### Pattern 1: Dynamic AI System Prompt Generation from Single Source of Truth
**What:** 在 Spring Bean 初始化完成阶段，从 `HealthMetricMetaRegistry` 读取全部健康与保健指标元数据，动态渲染 Markdown 对照表并注入提示词模板中缓存为 final 字符串。
**When to use:** 在 `AiParseServiceImpl.java` 中替代写死的静态 `PARSE_SYSTEM_PROMPT`。
**Example:**
```java
// [VERIFIED: hhs-backend/src/main/java/com/hhs/service/impl/AiParseServiceImpl.java]
@Service
@RequiredArgsConstructor
public class AiParseServiceImpl implements AiParseService {

    private final HealthMetricMetaRegistry metaRegistry;
    private String cachedSystemPrompt;

    @PostConstruct
    public void initSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的健康数据解析助手。请从用户的输入中提取健康指标信息。\n\n");
        sb.append("## 指标对照表\n\n### 健康指标 (HEALTH):\n");
        sb.append("| 中文名称 | metricKey | 单位 | 正常参考区间 | 生理极限 |\n");
        sb.append("|---------|-----------|------|-------------|----------|\n");

        for (MetricDefinition def : metaRegistry.getAllDefinitions().values()) {
            if (def.category() == MetricCategory.HEALTH) {
                sb.append(String.format("| %s | %s | %s | %s ~ %s | %s ~ %s |\n",
                        def.displayName(), def.canonicalKey(), def.unit(),
                        def.normalMin(), def.normalMax(),
                        def.hardMin(), def.hardMax()));
            }
        }

        sb.append("\n### 保健指标 (WELLNESS):\n");
        sb.append("| 中文名称 | metricKey | 单位 | 正常参考区间 | 生理极限 | 说明 |\n");
        sb.append("|---------|-----------|------|-------------|----------|------|\n");

        for (MetricDefinition def : metaRegistry.getAllDefinitions().values()) {
            if (def.category() == MetricCategory.WELLNESS) {
                sb.append(String.format("| %s | %s | %s | %s ~ %s | %s ~ %s | %s |\n",
                        def.displayName(), def.canonicalKey(), def.unit(),
                        def.normalMin(), def.normalMax(),
                        def.hardMin(), def.hardMax(),
                        def.guideHint() != null ? def.guideHint() : "-"));
            }
        }

        sb.append("""
            
            ## 解析规则
            1. 识别用户输入中的数值和对应指标
            2. 判断指标属于 HEALTH 还是 WELLNESS 类别
            3. **血压必须拆分**: 用户提到"血压"时，必须返回两个指标：systolicBP（收缩压）和 diastolicBP（舒张压）。例如"血压120/80"，返回 systolicBP=120 和 diastolicBP=80。如果用户只给了一个数值（如"血压120"），不要返回任何血压指标，在 warnings 中提示"血压数据不完整，请提供收缩压/舒张压格式，如120/80"
            4. 置信度(confidence)根据输入的明确程度设置(0.5-1.0)
            5. 如果数值超出正常参考区间或生理极限，在 warnings 中记录友好提示
            6. **严禁返回指标对照表中不存在的 metricKey**，只能使用上表中列出的 metricKey
            """);

        this.cachedSystemPrompt = sb.toString();
    }
}
```

### Pattern 2: Domain-Isolated Medical Lab Routine Registry
**What:** 针对体检报告中的血常规检验项目（`hemoglobin`, `wbc`, `rbc`, `platelet`），建立独立的 `LabRoutineMetricRegistry`，提供权威中文别名、标准单位及临床参考区间，不污染 `HealthMetricMetaRegistry`。
**When to use:** 在 `OcrServiceImpl.java` 中进行医学化验单图片文本提取时作为扩展字典协同查询。
**Example:**
```java
package com.hhs.service.domain;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.*;

@Component
public class LabRoutineMetricRegistry {

    public record LabDefinition(
            String key,
            String displayName,
            Set<String> aliases,
            String unit,
            BigDecimal normalMin,
            BigDecimal normalMax,
            BigDecimal hardMin,
            BigDecimal hardMax
    ) {}

    private final Map<String, LabDefinition> definitions = new LinkedHashMap<>();

    public LabRoutineMetricRegistry() {
        register(new LabDefinition("hemoglobin", "血红蛋白", Set.of("血红蛋白", "血红蛋白计数", "HGB", "Hb"), "g/L",
                new BigDecimal("120"), new BigDecimal("160"), new BigDecimal("20"), new BigDecimal("300")));
        register(new LabDefinition("wbc", "白细胞计数", Set.of("白细胞", "白细胞计数", "WBC"), "10^9/L",
                new BigDecimal("4.0"), new BigDecimal("10.0"), new BigDecimal("0.1"), new BigDecimal("200.0")));
        register(new LabDefinition("rbc", "红细胞计数", Set.of("红细胞", "红细胞计数", "RBC"), "10^12/L",
                new BigDecimal("4.0"), new BigDecimal("5.5"), new BigDecimal("0.5"), new BigDecimal("15.0")));
        register(new LabDefinition("platelet", "血小板计数", Set.of("血小板", "血小板计数", "PLT"), "10^9/L",
                new BigDecimal("100"), new BigDecimal("300"), new BigDecimal("5"), new BigDecimal("2000")));
    }

    private void register(LabDefinition def) {
        definitions.put(def.key(), def);
    }

    public Optional<LabDefinition> getDefinition(String keyOrAlias) {
        if (keyOrAlias == null || keyOrAlias.isBlank()) return Optional.empty();
        String trimmed = keyOrAlias.trim();
        if (definitions.containsKey(trimmed)) return Optional.of(definitions.get(trimmed));
        for (LabDefinition def : definitions.values()) {
            if (def.aliases().contains(trimmed)) return Optional.of(def);
        }
        return Optional.empty();
    }
}
```

### Pattern 3: Multi-DTO Class-Level Physical Limit Interception via Common Interface
**What:** 定义通用的指标值提供接口 `MetricValueProvider`，供所有涉及指标保存的请求 DTO（`HealthMetricRequest`、`WellnessMetricRequest`、`ConfirmMetricsRequest.MetricItem`、`OcrConfirmRequest.MetricItem`）实现；`MetricRecordValidator` 统一对该接口进行入库拦截。
**When to use:** 在入库 HTTP 控制器阶段拦截心率 500 次/分、收缩压 999 mmHg 等不可能生理极限。
**Example:**
```java
// 1. 通用指标提供接口
public interface MetricValueProvider {
    String getMetricKey();
    BigDecimal getValue();
}

// 2. 统一验证器
@Component
public class MetricRecordValidator implements ConstraintValidator<ValidMetricRecord, MetricValueProvider> {

    private final HealthMetricMetaRegistry registry;
    private final LabRoutineMetricRegistry labRegistry;

    @Autowired
    public MetricRecordValidator(HealthMetricMetaRegistry registry, LabRoutineMetricRegistry labRegistry) {
        this.registry = registry != null ? registry : new HealthMetricMetaRegistry();
        this.labRegistry = labRegistry != null ? labRegistry : new LabRoutineMetricRegistry();
    }

    @Override
    public boolean isValid(MetricValueProvider provider, ConstraintValidatorContext context) {
        if (provider == null || provider.getValue() == null || provider.getMetricKey() == null) {
            return true;
        }

        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(provider.getMetricKey());
        BigDecimal value = provider.getValue();

        // 先查通用注册表
        Optional<MetricDefinition> defOpt = registry.getDefinition(canonicalKey);
        if (defOpt.isPresent()) {
            MetricDefinition def = defOpt.get();
            if ((def.hardMin() != null && value.compareTo(def.hardMin()) < 0) ||
                (def.hardMax() != null && value.compareTo(def.hardMax()) > 0)) {
                buildViolation(context, def.displayName(), def.hardMin(), def.hardMax(), def.unit());
                return false;
            }
            return true;
        }

        // 次查化验单扩展注册表
        Optional<LabRoutineMetricRegistry.LabDefinition> labOpt = labRegistry.getDefinition(canonicalKey);
        if (labOpt.isPresent()) {
            var lab = labOpt.get();
            if ((lab.hardMin() != null && value.compareTo(lab.hardMin()) < 0) ||
                (lab.hardMax() != null && value.compareTo(lab.hardMax()) > 0)) {
                buildViolation(context, lab.displayName(), lab.hardMin(), lab.hardMax(), lab.unit());
                return false;
            }
            return true;
        }

        return true; // 未知自定义指标放行
    }
}
```

### Anti-Patterns to Avoid
- **在 Controller 或 Service 中写死中文与 Key 映射**：严禁在 `OcrServiceImpl` 中维护私有的 `METRIC_KEY_MAP`，必须统一委托给 `MetricKeyNormalizer` 与注册表。
- **失焦时直接重置或清空用户输入**：严禁执行 `form.value = 0` 或 `form.value = undefined`，用户输入的异常数据必须保留，以便对照自纠。
- **将病理高血压/高血糖当作非法输入直接拦截**：严禁把收缩压 190 mmHg 或血糖 18.0 mmol/L 当作表单非法输入拦截，只有突破人类物理极限的数值（Tier 4，如收缩压 > 300 mmHg）才属于阻断性拦截。
- **允许写入未拆分的 `bloodPressure`**：AI 和 OCR 解析若提取到“血压”，必须拆分为 `systolicBP` 和 `diastolicBP`，数据库中绝不允许存在 `bloodPressure` 单一混合记录。

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 指标 Key 规范化与别名转换 | 正则替换 / switch-case | `MetricKeyNormalizer.toCanonicalKey()` | [VERIFIED: MetricKeyNormalizer.java:24-51] 集中管理了大小写与别名，避免遗漏 SBP、glucose、spo2 |
| 生理范围与单位获取 | 页面内私有字典 (`metricUnitMap` / `WELLNESS_METRICS`) | `useMetricMetaStore` (Pinia) | [VERIFIED: useMetricMetaStore.ts:31-50] 支持在线同步与离线兜底，避免 3 个页面各维护一份硬编码 |
| 四梯度生理评级与倒置判定 | 各表单内独立的 if-else 边界判断 | `useMetricValidation` / `evaluateMetricValue()` | [VERIFIED: useMetricValidation.ts:45-73] 集中沉淀医学常模、Panic Limits 及一键对调原子操作 |
| 生理不可能极限入库拦截 | 服务层手动抛出异常 | `@ValidMetricRecord` Jakarta 注解 | 与 Spring MVC `@Valid` 无缝配合，由 `GlobalExceptionHandler` 统一转换为友好提示 |

## Runtime State Inventory

> 本阶段为代码重构与全渠道逻辑对齐阶段，不涉及数据库物理列更名或实体迁移。

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — 数据库表结构 `health_metric` 与 `wellness_metric` 结构保持不变 [VERIFIED: schema.sql] | 无需数据迁移 |
| Live service config | None — 外部百度 OCR 与通义千问 API 配置不变 [VERIFIED: application.yml] | 无需更新远程配置 |
| OS-registered state | None — 无操作系统注册服务 | 无 |
| Secrets/env vars | None — 密钥环境变量保持不变 (`DASH_SCOPE_API_KEY`, `BAIDU_OCR_API_KEY`) | 无 |
| Build artifacts | None — 标准 Maven / Vite 编译包 | 正常重构构建即可 |

## Common Pitfalls

### Pitfall 1: AI 模型返回历史废弃的 `glucose` 导致前端无法正确展示
**What goes wrong:** 大模型在未严格约束的情况下容易输出 `glucose`，导致前端列表在未做映射时出现原始英文。
**Why it happens:** 历史 `PARSE_SYSTEM_PROMPT` 中第一项写死为 `| 血糖 | glucose | mmol/L | 3.9-6.1 |` [VERIFIED: AiParseServiceImpl.java:73]。
**How to avoid:** 动态构建 Prompt 时，直接提取权威 Key `bloodGlucose`（中文名“血糖”）；同时在 `AiParseServiceImpl.java` 解析结果处理循环中，通过 `MetricKeyNormalizer.toCanonicalKey(metricKey)` 统一将 `glucose` 转为 `bloodGlucose`。
**Warning signs:** AI 解析结果返回列表中的 `metricKey` 为 `glucose` 而不是 `bloodGlucose`。

### Pitfall 2: `confirmMetrics` 批量保存部分成功时事务与状态不一致
**What goes wrong:** 批量确认包含 3 个指标，前 2 个入库成功，第 3 个因超限拦截失败，导致前端展示报错但后端部分入库，且确认状态标记混乱。
**Why it happens:** 既有代码在循环内部单条 try-catch，并在发生失败时仅记录 log，最后根据 `savedIds` 的情况抛出异常。
**How to avoid:** 
1. 校验前置：在进入持久化循环前，先对整个列表的 `metricKey` 与生理极限进行全量校验，若存在非法数据立即报错返回，保障原子性；
2. 在 DTO 层加 `@ValidMetricRecord`，Spring MVC 自动在 Controller 阶段拦截整体请求，避免部分脏数据入库。

### Pitfall 3: 血压在编辑历史记录模式下被错误要求输入舒张压
**What goes wrong:** 用户在 `Metrics.vue` 中点击编辑一条现有的 `systolicBP`（收缩压）记录，弹窗误判为“血压录入”并要求同时输入舒张压，导致编辑保存逻辑混乱。
**Why it happens:** 在添加模式下，用户希望录入“血压”，此时收缩压与舒张压同屏展示并两笔入库；而在编辑模式下，被编辑的仅是一条具象的主键记录（比如仅仅修订单条收缩压值）。
**How to avoid:** 在 `Metrics.vue` 弹窗中判定：如果处于编辑模式 (`editingId !== null`)，仅渲染当前指标单项输入框；仅在新增模式且选中血压协同指标时，才开启双字段并列与一键对调。

### Pitfall 4: 心情（mood）星级评分被通用数值输入框覆盖破坏体验
**What goes wrong:** 将 `prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 重构为通用元数据输入后，心情指标变成 `el-input-number`（输入数字 1~5），丢失了原有的 1~5 颗星打分及文字描述（很差/较差/一般/较好/很好）。
**Why it happens:** 机械化接入通用表单，未针对离散等级指标设置专用分支模板。
**How to avoid:** 在 `form.metricKey === 'mood'` 时保留 `<el-rate>` 评分组件，双向绑定至 `1~5` 整数值，仅在其他连续数值指标（喝水、步数、睡眠时长）上渲染动态 `el-input-number`。

## Code Examples

### 1. 前端 `src/views/health/Metrics.vue` 弹窗重构核心模式
```vue
<template>
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <!-- 指标类型选择（从 Store 动态加载） -->
      <el-form-item label="指标类型" prop="metricKey">
        <el-select
          v-model="form.metricKey"
          placeholder="请选择"
          style="width: 100%"
          :disabled="editingId !== null"
          @change="handleMetricChange"
        >
          <el-option
            v-for="item in metaStore.healthMetrics"
            :key="item.canonicalKey"
            :label="item.displayName"
            :value="item.canonicalKey"
          />
        </el-select>
      </el-form-item>

      <!-- 新增模式下的血压双字段排版 -->
      <template v-if="isBloodPressure && editingId === null">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="收缩压 (高压)" prop="value">
              <el-input-number
                v-model="form.value"
                :precision="0"
                :step="1"
                placeholder="例如: 120"
                style="width: 100%"
              />
              <div v-if="primaryEval" class="tier-pill-wrapper">
                <el-tag :type="getTagType(primaryEval.status)" size="small">
                  {{ primaryEval.badgeText }}
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
                placeholder="例如: 80"
                style="width: 100%"
              />
              <div v-if="diastolicEval" class="tier-pill-wrapper">
                <el-tag :type="getTagType(diastolicEval.status)" size="small">
                  {{ diastolicEval.badgeText }}
                </el-tag>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 生理倒置警告与对调按钮 -->
        <el-alert v-if="isInverted" type="warning" show-icon :closable="false" class="mb-3">
          <template #title>
            <div class="flex items-center justify-between">
              <span>收缩压低于或等于舒张压，疑似输入颠倒</span>
              <el-button size="small" type="warning" plain @click="swapBloodPressure">
                对调高低压
              </el-button>
            </div>
          </template>
        </el-alert>
      </template>

      <!-- 常规单指标排版（或编辑模式） -->
      <template v-else>
        <el-form-item :label="currentDef?.displayName || '测量数值'" prop="value">
          <el-input-number
            v-model="form.value"
            :precision="currentDef?.precision ?? 1"
            :step="currentDef?.step ?? 1"
            :placeholder="placeholderText"
            style="width: 100%"
          />
          <div v-if="primaryEval" class="tier-pill-wrapper mt-1">
            <el-tag :type="getTagType(primaryEval.status)" size="small">
              {{ primaryEval.badgeText }}
            </el-tag>
            <span class="ml-2 text-xs text-gray-500">{{ primaryEval.message }}</span>
          </div>
        </el-form-item>
      </template>

      <el-form-item label="测量单位">
        <el-input :value="currentDef?.unit || form.unit" disabled placeholder="自动匹配单位" />
      </el-form-item>

      <el-form-item label="记录日期" prop="recordDate">
        <el-date-picker
          v-model="form.recordDate"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>

      <!-- 常态化临床指南参考卡片 -->
      <ReferenceGuideCard :definition="currentDef" :diastolic-def="diastolicDef" />
    </el-form>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :disabled="isSaveBlocked" :loading="saving" @click="handleSubmit">
        确定
      </el-button>
    </template>

    <!-- 危急值二次确认弹窗 -->
    <CriticalConfirmDialog
      v-model="criticalDialogVisible"
      :metric-name="currentDef?.displayName || '指标'"
      :value="form.value"
      :unit="currentDef?.unit || form.unit"
      @confirm="doSave"
    />
  </el-dialog>
</template>
```

### 2. 后端 OCR 服务接入元数据与化验扩展
```java
// [VERIFIED: hhs-backend/src/main/java/com/hhs/service/impl/OcrServiceImpl.java]
@Service
public class OcrServiceImpl implements OcrService {

    private final HealthMetricMetaRegistry metaRegistry;
    private final LabRoutineMetricRegistry labRegistry;

    // 统一从两大注册中心查询指标定义
    private MetricMetadataHolder resolveMetric(String rawName) {
        String canonicalKey = MetricKeyNormalizer.toCanonicalKey(rawName);

        // 1. 核心健康/保健指标注册中心
        Optional<MetricDefinition> coreOpt = metaRegistry.getDefinition(canonicalKey);
        if (coreOpt.isPresent()) {
            MetricDefinition def = coreOpt.get();
            return new MetricMetadataHolder(
                    def.canonicalKey(), def.displayName(), def.unit(),
                    def.normalMin(), def.normalMax(), def.category().name()
            );
        }

        // 2. 化验单血常规扩展注册中心
        Optional<LabRoutineMetricRegistry.LabDefinition> labOpt = labRegistry.getDefinition(rawName);
        if (labOpt.isPresent()) {
            var lab = labOpt.get();
            return new MetricMetadataHolder(
                    lab.key(), lab.displayName(), lab.unit(),
                    lab.normalMin(), lab.normalMax(), "HEALTH"
            );
        }

        return null;
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `AiParseServiceImpl` 写死静态 String `PARSE_SYSTEM_PROMPT` | 启动时由 `HealthMetricMetaRegistry` 动态拼装 Markdown 提示词并缓存 | Phase 7 (本阶段) | 提示词与系统权威 23 项元数据强一致，后续调整指标零偏差 |
| `AiParseServiceImpl` 私有 `METRIC_RANGES` 二元判断 | 消费元数据 `hardMin`/`hardMax` 与 `normalMin`/`normalMax` 四梯度评估 | Phase 7 (本阶段) | 不抹杀病理异常数据，仅对物理极限生成阻断性错误 |
| `OcrServiceImpl` 混杂维护 25+ 字符串映射表 | 分离核心元数据注册表与 `LabRoutineMetricRegistry` | Phase 7 (本阶段) | 架构分层清晰，化验单专属指标不污染通用健康模型 |
| 前端各管理页硬编码 7 个 `el-option` 与私有字典 | 统一接入 `useMetricMetaStore` 与 `useMetricValidation` | Phase 7 (本阶段) | 彻底消除页面间校验不一致、英文漏出与破坏性失焦清空 |

**Deprecated/outdated:**
- `AiParseServiceImpl.PARSE_SYSTEM_PROMPT`: 废除硬编码静态字符串，改为动态组装。
- `AiParseServiceImpl.METRIC_RANGES`: 废除，统一使用 `HealthMetricMetaRegistry`。
- `OcrServiceImpl.METRIC_KEY_MAP` / `DEFAULT_UNITS` / `REFERENCE_RANGES`: 废除，由核心注册表与 `LabRoutineMetricRegistry` 替代。
- `Metrics.vue.metricUnitMap`: 废除，由元数据 Store 统一供应单位与精度。

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | 化验单血常规专属指标（血红蛋白、白细胞、红细胞、血小板）在用户确认入库时，作为 `HEALTH` 类别存储于 `health_metric` 表 [ASSUMED] | Standard Stack / Architecture Patterns | 若需存储于独立的 `lab_result` 表，需调整确认持久化服务调用。但目前系统现有 `OcrServiceImpl.saveMetric` 也是将其作为 `HEALTH` 存储于 `healthMetricService.add()`，且 `health_metric` 表支持任意合法的 canonicalKey，风险极低。 |

## Open Questions

1. **OCR 确认入库化验单指标（如白细胞、红细胞）是否参与实时预警与健康评分？**
   - What we know: `HealthMetricServiceImpl.add()` 会发出 `MetricRecordedEvent`，下游 `AlertService` 会尝试匹配阈值规则。由于这 4 项属于化验扩展指标，目前 `HealthMetricMetaRegistry` 未注册其基线规则。
   - What's unclear: 是否需要为其配置专有的用户预警规则。
   - Recommendation: 保持当前行为，在 `MetricRecordValidator` 中放行未注册指标或通过 `LabRoutineMetricRegistry` 校验其生理极限，评分计算引擎对未注册指标默认旁路放行，不影响核心评分。

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java Development Kit | 后端服务与测试运行 | ✓ | 17.0.0 (Oracle JDK 17) | — |
| Node.js / npm | 前端服务与 Vitest 测试运行 | ✓ | Node v24.15.0 / npm 11.12.1 | — |
| Apache Maven | 后端编译与 Checkstyle 检查 | ✓ | 3.8.4 | — |
| MySQL (Docker/Local) | 数据库 (端口 3307) | ✓ | 8.0+ | H2 / Testcontainers (CI环境) |
| Redis (Docker/Local) | 缓存 (端口 6380) | ✓ | 7.0+ | — |

**Missing dependencies with no fallback:**
- None

**Missing dependencies with fallback:**
- None

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Backend Framework | JUnit 5 (5.10.1) + Mockito (5.7.0) [VERIFIED: hhs-backend/pom.xml] |
| Frontend Framework | Vitest (4.1.7) + @vue/test-utils (2.4.4) [VERIFIED: hhs-frontend-v2/package.json] |
| Backend Quick Run | `cd hhs-backend && mvn test -Dtest=AiParseServiceTest,OcrServiceTest,MetricRecordValidatorTest -Dcheckstyle.skip=true` |
| Backend Full Suite | `cd hhs-backend && mvn clean verify -DskipITs` |
| Frontend Quick Run | `cd hhs-frontend-v2 && npm run test:run` |
| Frontend Full Suite | `cd hhs-frontend-v2 && npm run test:run && npm run build` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SYNC-01 | AI 解析动态 System Prompt 必须包含 23 项权威指标且使用 canonicalKey（如 bloodGlucose） | Backend Unit | `mvn test -Dtest=AiParseServiceTest#testDynamicPromptGeneration -Dcheckstyle.skip=true` | ❌ Wave 0 Gap |
| SYNC-01 | AI 解析超出生理极限数值时返回 Tier 4 严重警告，正常病理数据返回 Tier 2/3 警告但放行 | Backend Unit | `mvn test -Dtest=AiParseServiceTest#testPhysiologicalFourTierWarnings -Dcheckstyle.skip=true` | ❌ Wave 0 Gap |
| SYNC-01 | OCR 识别全面对接元数据注册中心与化验单血常规扩展字典，标准 Key 与参考范围统一 | Backend Unit | `mvn test -Dtest=OcrServiceTest#testRecognizeReportWithMetaRegistry -Dcheckstyle.skip=true` | ❌ Wave 0 Gap |
| SYNC-01 | 所有入库渠道请求 DTO 遇到不可能生理极限（Tier 4）统一被 `@ValidMetricRecord` 拦截 | Backend Unit | `mvn test -Dtest=MetricRecordValidatorTest#testMultiDtoPhysicalLimitInterception -Dcheckstyle.skip=true` | ❌ Wave 0 Gap |
| SYNC-01 | 跨渠道全链路等价性（手动、AI 提取、OCR 识别针对同组指标产出完全相同的 Key 与单位） | Backend Integration | `mvn test -Dtest=CrossChannelConsistencyTest -Dcheckstyle.skip=true` | ❌ Wave 0 Gap |
| D-05 (HEALTH) | `Metrics.vue` 弹窗接入 `useMetricMetaStore` 动态指标，血压支持双字段录入与一键对调 | Frontend Unit | `npm run test:run -- HealthMetrics.spec.ts` | ❌ Wave 0 Gap |
| D-05 (WELL) | `prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 接入元数据，保留心情星级打分 | Frontend Unit | `npm run test:run -- WellnessMetrics.spec.ts` | ❌ Wave 0 Gap |

### Sampling Rate
- **Per task commit:** `npm run test:run` (前端) 或 `mvn test -Dtest=... -Dcheckstyle.skip=true` (后端)
- **Per wave merge:** `cd hhs-backend && mvn test -Dcheckstyle.skip=true` + `cd hhs-frontend-v2 && npm run test:run`
- **Phase gate:** 全部测试绿灯并通过 `mvn checkstyle:check` 与 `npm run lint:check`。

### Wave 0 Gaps
- [ ] `hhs-backend/src/test/java/com/hhs/service/CrossChannelConsistencyTest.java` — 验证手动、AI、OCR 三渠道 Key 与单位 100% 对齐
- [ ] `hhs-frontend-v2/src/__tests__/views/HealthMetrics.spec.ts` — 验证 `health/Metrics.vue` 弹窗动态元数据与血压对调
- [ ] `hhs-frontend-v2/src/__tests__/views/WellnessMetrics.spec.ts` — 验证 `prevention/Metrics.vue` 与 `wellness/Dashboard.vue` 弹窗对齐

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT Bearer 无状态认证，`SecurityUtils.getCurrentUserId()` 注入 [VERIFIED: CLAUDE.md] |
| V3 Session Management | no | 无服务端 Session，JWT 无状态管理 |
| V4 Access Control | yes | 校验记录所有者 `history.getUserId().equals(userId)`，防止越权确认 [VERIFIED: AiParseServiceImpl.java:437] |
| V5 Input Validation | yes | Jakarta `@Valid` + 自定义 `@ValidMetricRecord` 拦截 Tier 4 畸变数据；前端非破坏性校验 |
| V6 Cryptography | no | 本阶段不涉及新加密算法或敏感数据加密存储变更 |

### Known Threat Patterns for this Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| AI 提示词注入 (Prompt Injection) | Tampering | 在用户输入前加上隔离前缀与固定结构化输出指令，严格校验返回 JSON 的 metricKey 白名单 |
| 非法/畸变数值污染历史趋势 (Extreme Value Injection) | Tampering / Denial of Service | `@ValidMetricRecord` 在入库网关层统一拦截物理不可能生理极限（如收缩压 999 mmHg） |
| 跨用户越权确认解析记录 (IDOR) | Elevation of Privilege | 在 `confirmMetrics` 中严格对比 `history.getUserId().equals(currentUserId)` |
| 恶意文件上传 (OCR 文件路径遍历) | Tampering / Information Disclosure | 复用 `PathValidationUtil.validateAndResolvePath` 防御路径穿越攻击 [VERIFIED: OcrServiceImpl.java:682] |

## Sources

### Primary (HIGH confidence)
- `hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java:37-51,421-447` — 权威 23 项不可变元数据注册中心 [VERIFIED: in-repo file]
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java:24-51,58` — 权威小驼峰 Key 规范化与别名体系 [VERIFIED: in-repo file]
- `hhs-backend/src/main/java/com/hhs/service/impl/AiParseServiceImpl.java:65-174,423-563` — AI 解析提示词与确认持久化既有实现 [VERIFIED: in-repo file]
- `hhs-backend/src/main/java/com/hhs/service/impl/OcrServiceImpl.java:73-183,503-625` — OCR 识别解析与确认持久化既有实现 [VERIFIED: in-repo file]
- `hhs-backend/src/main/java/com/hhs/validation/MetricRecordValidator.java:21-77` — Jakarta 物理生理极限类级别校验器 [VERIFIED: in-repo file]
- `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue:12-80,195-206` — Phase 6 手动填报弹窗与血压协同标准实现 [VERIFIED: in-repo file]
- `hhs-frontend-v2/src/composables/useMetricValidation.ts:18-82` — 四梯度生理评估与血压一键对调组合式函数 [VERIFIED: in-repo file]
- `hhs-frontend-v2/src/stores/metricMeta.ts:18-50` — 前端 Pinia 元数据共享 Store [VERIFIED: in-repo file]
- `hhs-frontend-v2/src/views/health/Metrics.vue:24-30,188-202` — 待重构健康指标列表及弹窗 [VERIFIED: in-repo file]
- `hhs-frontend-v2/src/views/prevention/Metrics.vue:59-90` — 待重构保健指标列表及弹窗 [VERIFIED: in-repo file]
- `hhs-frontend-v2/src/views/wellness/Dashboard.vue:156-188` — 待重构保健指标仪表盘及弹窗 [VERIFIED: in-repo file]

### Secondary (MEDIUM confidence)
- `.planning/phases/07-sync/07-CONTEXT.md` — 阶段决策约束与边界定义

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 现有工程既有技术栈，无需新增外部依赖
- Architecture: HIGH — 详细阅读了现有全部核心实现，掌握了所有待解耦的私有字典与注入点
- Pitfalls: HIGH — 深入分析了旧 Key（如 glucose）、心情星级、血压编辑模式下的边界风险

**Research date:** 2026-11-05
**Valid until:** 2026-12-05 (稳定架构)
