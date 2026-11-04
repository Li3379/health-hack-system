# Architecture Research

**Domain:** 智能健康指标填报引导、统一校验管线与元数据架构 (Intelligent Health Metric Guidance & Validation Pipeline)  
**Researched:** 2026-11-04  
**Confidence:** HIGH (基于 HHS 全栈代码审计、MySQL 数据库结构、现有集成测试以及国家临床参考区间标准)

---

## Standard Architecture

### System Overview

本架构设计旨在彻底解决 HHS 当前健康指标体系中存在的“定义碎片化”、“校验生硬破坏输入”、“Key命名不统一（驼峰/下划线/缩写）致使用户端英文外露”以及“病理异常值被当作非法输入拦截”四大痛点。

系统在后端建立**指标元数据单一事实源 (SSOT, Single Source of Truth)**，对外提供统一的元数据字典接口，并在数据接入链路嵌入**指标 Key 归一化**与**分级宽容校验管线**；前端通过 Pinia 元数据 Store 缓存字典，实现表单控件属性（单位、步长、精度、范围）的动态绑定，并在用户输入过程中实时呈现解释性引导反馈。

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                前端交互层 (Vue 3 + Element Plus)                        │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────┐  ┌─────────────────────┐  ┌───────────────────────────────┐  │
│  │ QuickInputDialog.vue │  │ QuickInput.vue      │  │ Metrics.vue / Thresholds.vue  │  │
│  │ (动态步长/范围/引导) │  │ (智能快捷入口)      │  │ (动态下拉选项/无硬编码)       │  │
│  └──────────┬───────────┘  └──────────┬──────────┘  └───────────────┬───────────────┘  │
│             │                         │                             │                  │
├─────────────┴─────────────────────────┴─────────────────────────────┴──────────────────┤
│                               前端状态与引导逻辑层 (Pinia & Composables)                │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ useMetricMetaStore (Pinia 字典缓存: 权威中文名、单位、精度、四级阈值、引导文案)   │  │
│  ├──────────────────────────────────────────────────────────────────────────────────┤  │
│  │ useMetricValidation (组合式校验函数: 实时计算健康等级胶囊、柔性提示、错误解释)   │  │
│  ├──────────────────────────────────────────────────────────────────────────────────┤  │
│  │ metricNormalizer.ts / format.ts (双向归一化转换器: 杜绝英文Key外露)              │  │
│  └──────────────────────────────────────────┬───────────────────────────────────────┘  │
│                                             │ HTTP GET /api/metrics/metadata           │
├─────────────────────────────────────────────┼──────────────────────────────────────────┤
│                                后端接入层 (Spring Boot REST Controllers)                │
├─────────────────────────────────────────────┼──────────────────────────────────────────┤
│  ┌───────────────────────────────┐          │          ┌────────────────────────────┐  │
│  │ MetricMetadataController      │◄─────────┘          │ HealthMetricController     │  │
│  │ (GET /api/metrics/metadata)   │                     │ WellnessController         │  │
│  │ (GET /api/metrics/categories) │                     │ AiParseController          │  │
│  └──────────────┬────────────────┘                     └─────────────┬──────────────┘  │
│                 │                                                    │                 │
├─────────────────┴────────────────────────────────────────────────────┴─────────────────┤
│                             后端领域元数据与校验层 (Domain Core)                       │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────┐           ┌────────────────────────────────────┐  │
│  │ MetricMetadataRegistry (SSOT)   │           │ MetricKeyNormalizer                │  │
│  │ (19+指标全属性内存元数据注册表)  │◄──────────┤ (camelCase/snake_case/别名自适应)  │  │
│  └──────────────┬──────────────────┘           └─────────────────┬──────────────────┘  │
│                 │                                                │                     │
│                 │               ┌────────────────────────────────┴──────────────────┐  │
│                 └──────────────►│ MetricValidationPipeline (分级校验管线)            │  │
│                                 │ - Tier 1: 生理极限物理不可能 (Hard Block 拒绝入库)│  │
│                                 │ - Tier 2: 临床危急值区间 (Critical 二次核对保存)  │  │
│                                 │ - Tier 3: 异常警戒区间 (Warning 解释性提示保存)   │  │
│                                 │ - Tier 4: 理想生理区间 (Normal 绿色常态)          │  │
│                                 └────────────────────────┬──────────────────────────┘  │
│                                                          │                             │
├──────────────────────────────────────────────────────────┴─────────────────────────────┤
│                             业务服务与数据持久层 (Services & Persistence)              │
├────────────────────────────────────────────────────────────────────────────────────────┤
│  ┌────────────────────────┐  ┌─────────────────────────┐  ┌─────────────────────────┐  │
│  │ HealthMetricServiceImpl│  │ AiParseServiceImpl      │  │ MetricsScorer /         │  │
│  │ (指标增删改查/事件发布)│  │ (统一校验与提示词对齐)  │  │ WellnessScorer          │  │
│  └───────────┬────────────┘  └────────────┬────────────┘  └────────────┬────────────┘  │
│              │                            │                            │               │
│  ┌───────────┴────────────┐  ┌────────────┴────────────┐  ┌────────────┴────────────┐  │
│  │ Redis (metric_meta缓存)│  │ MySQL 8.0               │  │ ApplicationEventPublisher│ │
│  │ (sub-ms 元数据响应)    │  │ (health_metric 等表)    │  │ (MetricRecordedEvent)   │  │
│  └────────────────────────┘  └─────────────────────────┘  └─────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **`MetricMetadataRegistry`** (Backend NEW) | 指标元数据全生命周期单一事实源 (SSOT)。管理全部 19+ 种健康/保健指标的标准 Key、别名列表、中文名、单位、输入精度、步长、生理极限区间、临床参考区间及引导提示文案。 | Spring `@Component` 托管的不可变注册表，初始化载入常驻内存，结合 `@Cacheable` 或高并发只读 Map 提供 sub-millisecond 查询性能。 |
| **`MetricKeyNormalizer`** (Backend NEW) | 跨渠道多命名规范自适应归一化器。统一将 `camelCase`（如 `heartRate`）、`snake_case`（如 `heart_rate`）、`SCREAMING_SNAKE`（如 `HEART_RATE`）以及历史别名（如 `bodyTemperature`、`temp`、`spo2`）转化为权威 Canonical Key。 | 静态/组件级双向映射哈希表，内建别名反查索引与首字母缩写保护规则（避免 `systolicBP` 被误转为 `systolicBp`）。 |
| **`MetricValidationPipeline`** (Backend NEW) | 四梯度分级校验引擎。替代原有的粗暴 `@DecimalMax("1000.0")`。负责：1) 物理不可能极值硬性拦截；2) 复合指标逻辑完整性校验（如收缩压必须大于舒张压）；3) 临床异常与急危值判定并生成医学解释性建议。 | 领域服务组件，输出包含 `isValid`、`severity` (NORMAL/WARN/CRITICAL/HARD_LIMIT)、`message` 和 `suggestion` 的结构化校验结果。 |
| **`MetricMetadataController`** (Backend NEW) | 前后端元数据同步网关。向前端暴露 `/api/metrics/metadata` 接口，支持 HTTP 缓存头与全量字典下发。 | Spring MVC `@RestController`，输出 `Result<Map<String, MetricMetaVO>>`。 |
| **`ValidMetricRecord` / `MetricRecordValidator`** (Backend NEW) | 声明式请求对象级 Bean 校验器。挂载在 DTO 类级别，实现请求反序列化后的即时动态范围校验。 | Jakarta Bean Validation `ConstraintValidator<ValidMetricRecord, Object>` 实现。 |
| **`useMetricMetaStore`** (Frontend NEW) | 前端全局元数据状态管理。负责应用启动或首次使用时拉取元数据字典，建立内存别名索引，提供统一的名称查询、单位查询、输入参数获取接口。 | Pinia Store (`stores/metricMeta.ts`)，具备本地只读兜底配置，离线或断网依然可用。 |
| **`useMetricValidation`** (Frontend NEW) | 前端交互式校验组合式函数。监听用户输入，实时计算健康状态胶囊、提供华氏度/单位失误自纠诊断，配合 Element Plus `async-validator` 实现非阻塞体验。 | Vue 3 Composable (`composables/useMetricValidation.ts`)。 |
| **`metricNormalizer.ts` / `format.ts`** (Frontend MODIFIED) | 前端显示映射与格式化中枢。扩展现有 `format.ts`，重定向所有指标标签与单位查询至统一归一化逻辑，确保全系统任意位置绝不泄漏英文代码。 | 统一桥接函数，包含保底别名映射表。 |
| **`QuickInputDialog.vue`** (Frontend MODIFIED) | 快捷指标录入弹窗。彻底移除硬编码 `METRIC_RANGES` 与失焦清空输入框的破坏性逻辑，动态应用元数据的精度、步长、范围，提供直观状态引导。 | Element Plus 表单，集成动态占位符与分级提示 Banner。 |
| **`AiParseServiceImpl`** (Backend MODIFIED) | 智能语义解析服务。将内部硬编码的 `METRIC_RANGES`、`HEALTH_KEYS`、`WELLNESS_KEYS` 收敛至 `MetricMetadataRegistry`，提示词对照表动态同步。 | 注入 `MetricMetadataRegistry`，统一生成异常 warning 文案。 |

---

## Recommended Project Structure

```
hhs-backend/src/main/java/com/hhs/
├── common/
│   └── enums/
│       ├── MetricCategory.java                 # 保持: HEALTH / WELLNESS
│       └── MetricValidationSeverity.java       # [NEW] NORMAL, WARNING, CRITICAL, HARD_LIMIT
├── metadata/                                   # [NEW] 指标元数据核心模块
│   ├── MetricDefinition.java                   # [NEW] 指标元数据 Java Record (不可变定义)
│   ├── MetricMetadataRegistry.java             # [NEW] 核心注册表 (SSOT 单一事实源)
│   └── MetricKeyNormalizer.java                # [NEW] Key 命名归一化器 (兼容 camel/snake/alias)
├── validation/
│   ├── ValidMetricRecord.java                  # [NEW] DTO 级别自定义校验注解
│   ├── MetricRecordValidator.java              # [NEW] Jakarta 校验器实现 (调用 Pipeline)
│   ├── MetricValidationPipeline.java           # [NEW] 领域级四梯度分级校验管线
│   ├── MetricValidationResult.java             # [NEW] 结构化校验输出 (含解释与建议)
│   ├── MetricRangeValidator.java               # [REFACTOR/DEPRECATE] 兼容旧类
│   └── ValidMetricRange.java                   # [REFACTOR/DEPRECATE] 兼容旧类
├── controller/
│   ├── MetricMetadataController.java           # [NEW] GET /api/metrics/metadata 接口
│   └── HealthMetricController.java             # [MODIFIED] 规范化入参与查询
├── dto/
│   ├── HealthMetricRequest.java                # [MODIFIED] 移除硬编码 1000 上限，挂载 @ValidMetricRecord
│   ├── WellnessMetricRequest.java              # [MODIFIED] 统一别名兼容与范围约束
│   └── MetricMetaVO.java                       # [NEW] 元数据输出视图对象
├── service/
│   ├── domain/
│   │   ├── MetricDisplayFormatter.java         # [MODIFIED] 委托给 MetricMetadataRegistry
│   │   ├── MetricCategoryService.java          # [MODIFIED] 委托给 MetricMetadataRegistry
│   │   └── MetricsScorer.java                  # [MODIFIED] 支持归一化 Key 扣分匹配
│   └── impl/
│       ├── HealthMetricServiceImpl.java        # [MODIFIED] 写入/查询自动归一化
│       ├── AiParseServiceImpl.java             # [MODIFIED] 移除分散 METRIC_RANGES
│       └── OcrServiceImpl.java                 # [MODIFIED] 移除分散 REFERENCE_RANGES
```

```
hhs-frontend-v2/src/
├── api/
│   └── metricMeta.ts                           # [NEW] 元数据 API (getMetricMetadata)
├── constants/
│   └── metricMetaDefaults.ts                   # [NEW] 离线/编译时兜底元数据清单 (20+指标)
├── stores/
│   └── metricMeta.ts                           # [NEW] Pinia Store (全局元数据缓存与查询)
├── composables/
│   └── useMetricValidation.ts                  # [NEW] 组合式实时健康引导与分级校验
├── utils/
│   ├── metricNormalizer.ts                     # [NEW] 权威双向映射与归一化工具
│   └── format.ts                               # [MODIFIED] 桥接至 metricNormalizer，杜绝英文外露
└── views/
    ├── data-input/
    │   └── components/
    │       ├── QuickInputDialog.vue            # [MODIFIED] 彻底移除失焦清空与本地字典，动态配置
    │       ├── QuickInput.vue                  # [MODIFIED] 动态呈现快捷录入按钮
    │       └── AIInput.vue                     # [MODIFIED] 统一显示规范化指标标签
    └── health/
        ├── Metrics.vue                         # [MODIFIED] 下拉筛选与表单采用动态字典
        └── Thresholds.vue                      # [MODIFIED] 动态指标选项与单位联动
```

### Structure Rationale

- **`metadata/` 独立分包：** 指标元数据是全系统（AI、OCR、告警规则、健康评分、表单录入）的基础设施。将其提升为一级核心领域，避免各 Service 循环依赖，确保单向引用。
- **`validation/` 聚焦领域校验管线：** 分离 Jakarta Bean 校验外壳（`MetricRecordValidator`）与纯业务校验引擎（`MetricValidationPipeline`）。既能在 Controller 层自动拦截物理不可能数据，又能在 Service 层为 AI/OCR 批量导入提供丰富的警告诊断。
- **前端 `stores/` + `composables/` 模式：** 元数据属于静态/低频变动的全局配置，放入 Pinia Store 集中拉取并缓存；交互式校验状态（实时胶囊、自纠提示）与具体表单生命周期绑定，放入 Composable，实现关注点清晰分离。

---

## Architectural Patterns

### Pattern 1: Centralized In-Memory Registry (SSOT) with Tiered Boundaries

**What:** 后端构建不可变的 `MetricMetadataRegistry`，以 Java Record `MetricDefinition` 集中管理所有生理指标的临床基准、单位、生理极限（Hard Limit）、临床正常区间（Normal Range）及预警阈值（Warning/Critical）。  
**When to use:** 存在多业务模块（手动表单、AI语义抽取、OCR体检报告、硬件同步）共享同一套指标度量基准，且指标医学标准高度固定（无需频繁在数据库增删表结构）。  
**Trade-offs:** 内存常驻带来极高吞吐与类型安全，无需额外维护数据库迁移脚本；若未来需要医院管理员动态扩展非标准指标，可在注册表之上叠加数据库配置提供覆盖机制（Overlay Pattern）。

**Example:**
```java
public record MetricDefinition(
    String canonicalKey,
    Set<String> aliases,
    String displayName,
    MetricCategory category,
    String defaultUnit,
    int precision,
    BigDecimal step,
    BigDecimal hardMin,       // 物理不可能下限 (低于此值拒绝入库)
    BigDecimal hardMax,       // 物理不可能上限 (高于此值拒绝入库)
    BigDecimal normalMin,     // 正常生理参考下限
    BigDecimal normalMax,     // 正常生理参考上限
    String placeholder,
    String guideHint
) {}

@Component
public class MetricMetadataRegistry {
    private final Map<String, MetricDefinition> registry = new ConcurrentHashMap<>();
    private final Map<String, String> aliasToCanonicalMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        register(new MetricDefinition(
            "glucose",
            Set.of("blood_glucose", "bloodGlucose", "fastingGlucose", "fasting_glucose", "GLUCOSE"),
            "血糖",
            MetricCategory.HEALTH,
            "mmol/L",
            1,
            new BigDecimal("0.1"),
            new BigDecimal("1.0"),    // 极端生理极限下限
            new BigDecimal("35.0"),   // 极端生理极限上限
            new BigDecimal("3.9"),    // 正常空腹下限
            new BigDecimal("6.1"),    // 正常空腹上限
            "例如: 5.6",
            "正常空腹参考值 3.9 ~ 6.1 mmol/L"
        ));
        // 注册其他 18+ 指标...
    }
}
```

---

### Pattern 2: Graduated Non-Destructive Validation Pipeline

**What:** 区分**物理不可能数据（数据污染/打字错误）**与**病理异常数据（患者真实健康危机）**。物理不可能值予以拦截但保留用户输入；病理异常值允许保存入库，并在 UI 侧呈现解释性反馈、在后台触发告警。  
**When to use:** 所有医疗健康类数据录入场景，杜绝由于表单校验拦截导致患病用户无法记录危险体征。  
**Trade-offs:** 相比简单的二元布尔校验，前端需根据校验返回的严重等级（`NORMAL`、`WARNING`、`CRITICAL`、`HARD_LIMIT`）渲染不同的交互组件（绿色标签、黄色警示条、危急值核对弹窗）。

```
        用户输入数值 (Value)
                 │
                 ▼
       ┌───────────────────┐
       │ value < hardMin ||│  YES  [Tier 1: 物理极限拦截]
       │ value > hardMax ? │──────► 拦截提交，保留输入内容，
       └─────────┬─────────┘        给出物理不可能解释 (如"体温不可超过43°C")
                 │ NO
                 ▼
       ┌───────────────────┐
       │ value < critLow ||│  YES  [Tier 2: 临床危急核对]
       │ value > critHigh ?│──────► 允许保存，弹出二次核对提示，
       └─────────┬─────────┘        防范误录入，确认后触发高优先健康预警
                 │ NO
                 ▼
       ┌───────────────────┐
       │ value < normMin ||│  YES  [Tier 3: 异常生理状态]
       │ value > normMax ? │──────► 正常保存，界面呈现黄色警示胶囊，
       └─────────┬─────────┘        展示生活方式建议与复测贴士
                 │ NO
                 ▼
    [Tier 4: 正常生理区间]
    正常保存，界面呈现绿色健康胶囊
```

---

### Pattern 3: Bidirectional Alias-Aware Key Normalization Bridge

**What:** 在数据流入口（反序列化、入库、数据同步）实施透明的 Key 归一化，在数据流出口（VO 序列化、前端渲染）实施透明的中文与单位映射，保证内部流转的一致性与外部展示的无感兼容。  
**When to use:** 历史代码中同时存在 `camelCase`（`heartRate`）、`snake_case`（`heart_rate`）、`SCREAMING_SNAKE`（`HEART_RATE`）和缩写差异（`bodyTemperature` vs `temperature`）。  
**Trade-offs:** 需要在 DTO 反序列化与数据库查询封装处做一次映射，但彻底隔绝了英文 Key 泄漏到界面的漏洞。

**Example:**
```typescript
// frontend: utils/metricNormalizer.ts
const ALIAS_MAP: Record<string, string> = {
  heart_rate: 'heartRate',
  HEART_RATE: 'heartRate',
  pulse: 'heartRate',
  bodyTemperature: 'temperature',
  body_temperature: 'temperature',
  BODY_TEMPERATURE: 'temperature',
  systolic_bp: 'systolicBP',
  diastolic_bp: 'diastolicBP',
  blood_glucose: 'glucose',
  spo2: 'bloodOxygen',
  sleep_duration: 'sleepDuration'
}

export const toCanonicalKey = (key: string): string => {
  if (!key) return ''
  return ALIAS_MAP[key] || ALIAS_MAP[key.toLowerCase()] || key
}
```

---

## Data Flow

### Request Flow

```
[用户在表单键入数值]
    │ (动态输入事件 @input)
    ▼
[useMetricValidation Composable]
    │ 实时比对 Pinia useMetricMetaStore 缓存的区间
    ▼
[即时状态渲染: 呈现绿色/黄色/橙色胶囊，绝不清空输入]
    │
[用户点击保存按钮]
    │
[Element Plus async-validator]
    │ 仅在超出 hardMin / hardMax 时触发 reject 阻止提交
    ▼
[HTTP POST /api/metrics]
    │
[Spring Controller @Valid]
    │
[@ValidMetricRecord / MetricRecordValidator]
    │ 1. MetricKeyNormalizer.toCanonicalKey(key)
    │ 2. MetricValidationPipeline.validate(canonicalKey, value)
    │ 3. 校验双指标关系 (如 systolicBP > diastolicBP)
    ▼
[HealthMetricServiceImpl.add()]
    │ 1. 采用规范化 CanonicalKey 存储至数据库
    │ 2. 采用规范化 Unit 校验并纠正
    │ 3. 发布 MetricRecordedEvent
    ▼
[ApplicationEventPublisher] ──► [AlertEventListener] 评估预警
```

---

### State Management

```
[前端应用初始化 / 路由进入 data-input]
    │
    ▼
[useMetricMetaStore.initialize()]
    │
    ├─► 优先读取 Pinia 内存缓存
    │
    ├─► 若未加载，发起异步请求: GET /api/metrics/metadata
    │       │
    │       ├─► 成功: 写入 Store 状态，构建 aliasMap 索引
    │       │
    │       └─► 失败: 加载 @/constants/metricMetaDefaults.ts 兜底字典
    ▼
[页面组件订阅 (Computed Properties)]
    │
    ├─► QuickInputDialog: 自动获取 step, precision, min, max, unit
    ├─► Metrics.vue: 动态生成筛选下拉框 options
    └─► format.ts: getMetricLabel(key) 自动反查权威中文名
```

---

### Key Data Flows

1. **元数据自举与缓存流 (Metadata Bootstrap & Cache Flow):**
   前端通过 Pinia Store 初始化拉取 `/api/metrics/metadata`。后端利用 Redis 缓存该接口数据，同时返回 HTTP `Cache-Control: max-age=86400` 与 `ETag`。客户端获得毫秒级加载性能，且即使离线也有静态常量兜底。
2. **交互引导与非破坏性校验流 (Interactive Guidance & Safe Blur Flow):**
   用户在 `QuickInputDialog` 中输入时，`useMetricValidation` 在客户端实时匹配判定状态。失焦（blur）时**绝不清空用户输入**，仅在下方呈现对应的健康建议（如血糖 7.2 mmol/L 显示“轻度偏高，建议餐后复测”）。仅当输入物理不可能数值（如体温 50°C）时，输入框亮红并禁止点击“保存”。
3. **多入口归一化流 (Multi-Channel Normalization Flow):**
   - **手动录入：** 直接提交 Canonical Key。
   - **AI 解析：** `AiParseServiceImpl` 在组装 `MetricParseResultVO` 时，通过 `MetricKeyNormalizer` 将大模型抽取的 key 规范化为统一 key，并应用统一的 `validateMetricRange` 生成统一 warning。
   - **OCR 识别：** `OcrServiceImpl` 使用同一套注册表别名库，将报告中的“收缩压/高压/SBP”映射为 `systolicBP`。
   - **设备同步：** SDK 数据点中的 `heart_rate`、`spo2` 在进入 Service 前被清洗转换为 `heartRate`、`bloodOxygen`。

---

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| **0 - 1k users (当前规模)** | 后端 `MetricMetadataRegistry` 全常驻 JVM 内存；前端一次性加载完整字典（<15KB JSON）；单体数据库存储，无需任何复杂分库分表。 |
| **1k - 100k users** | Spring Boot 启用 Redis 缓存元数据响应；开启 Nginx/浏览器静态协商缓存（ETag/If-None-Match 304）；分级校验全部由客户端计算分流，服务端仅执行轻量级拦截，零计算性能损耗。 |
| **100k+ users** | 采用 CDN 边缘分发元数据字典静态 JSON 文件；校验管线保持纯无状态（Stateless），水平扩展任意数量后端容器实例均具备完全一致的校验能力。 |

### Scaling Priorities

1. **第一瓶颈：前端重复拉取元数据接口**
   - *表现：* 用户频繁刷新页面或切换路由产生冗余请求。
   - *对策：* Pinia Store 设置 `loaded` 守卫标志位，结合 `stale-while-revalidate` 策略，只有版本号变更时才重新拉取。
2. **第二瓶颈：历史脏数据导致的查询不兼容**
   - *表现：* 数据库历史记录中混杂了 `heart_rate` 与 `heartRate`，用户按指标筛选时遗漏数据。
   - *对策：* 在 `HealthMetricServiceImpl.list()` 与 `getTrend()` 中，使用 `MetricKeyNormalizer.getAllAliases(metricKey)` 构造 SQL `IN (...)` 查询，透明覆盖历史存量数据。

---

## Anti-Patterns

### Anti-Pattern 1: 各业务模块私设“范围小孤岛” (Hardcoded Range Islands)

**What people do:** `QuickInputDialog.vue` 写一套 `METRIC_RANGES`，`AiParseServiceImpl.java` 写一套 `METRIC_RANGES`，`HealthMetricRequest` 写一套 `@DecimalMax("1000")`，`Metrics.vue` 再写一套下拉列表。  
**Why it's wrong:** 导致全系统规则分歧。例如体温在弹窗是 30~43，在 AI 解析是 34~42；步数在快速录入是 0~100000，但发给后端却被 `@DecimalMax("1000")` 拦截。  
**Do this instead:** 建立后端 `MetricMetadataRegistry` 单一事实源，前端通过 `useMetricMetaStore` 统一下发与消费。

---

### Anti-Pattern 2: 校验失败时强行清空用户输入 (Destructive Error Handling)

**What people do:** 当前 `QuickInputDialog.vue` 中的逻辑：
```typescript
const handleValueBlur = () => {
  if (val < r.min || val > r.max) {
    ElMessage.warning('超出范围，请重新输入')
    form.value.value = undefined // 强行清空！
  }
}
```
**Why it's wrong:** 极度破坏用户体验。用户误触输入 1200（想输 120），原本只需按一次退格键，系统粗暴清空迫使全部重输，产生极大挫败感。  
**Do this instead:** 严禁清空用户输入。保留原值，输入框以黄色/红色边框提示，下方显示错误原因和建议修改区间。

---

### Anti-Pattern 3: 将临床病理异常当作参数校验错误拦截 (Treating Pathology as Validation Error)

**What people do:** 将体温 38.8°C 或血糖 14.0 mmol/L 当作无效数据（`return callback(new Error('非法数值'))`），阻止表单提交。  
**Why it's wrong:** 健康监测系统存在的唯一意义是真实记录健康状态。发热或高血糖患者此时最需要保存数据以供随访和预警，被系统拒之门外将导致平台彻底失去临床价值。  
**Do this instead:** 区分 Hard Limit 与 Normal Range。正常区间外使用柔性警告（Warning/Critical），允许数据入库并触发告警；仅当出现物理不可能（如体温 60°C）时才拒绝入库。

---

### Anti-Pattern 4: 缺少映射时直接向用户抛出英文代码 (Raw Key Leaking)

**What people do:** `format.ts` 中的保底代码：
```typescript
export const getMetricDisplayName = (key: string) => map[key] || key;
```
由于 `map` 中只定义了 8 个指标，导致 `fastingGlucose`、`totalCholesterol`、`sleepDuration`、`heart_rate` 在表格中直接显示为生硬的英文代码。  
**Do this instead:** `MetricKeyNormalizer` 必须覆盖系统支持的全部 19+ 种指标及其所有别名变体。若遇到未知 Key，也应通过分词（如将 `fasting_glucose` 拆分为友好格式）处理，杜绝代码外泄。

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| **Huawei Health SDK** | `HealthDataPoint.mapHuaweiDataType()` 桥接至 `MetricKeyNormalizer` | SDK 上报的 `com.huawei.health.heart_rate` 转换为权威 `heartRate`，单位转换为标准 `次/分`。 |
| **Xiaomi Health SDK** | `XiaomiHealthServiceImpl` 注入 `MetricKeyNormalizer` | 清洗 SDK 原始 snake_case 数据类型。 |
| **Baidu OCR API** | `OcrServiceImpl` 依赖 `MetricMetadataRegistry` 进行文本模式识别 | 将 OCR 提取到的中文名称（如“总胆固醇”、“空腹血糖”）通过注册表精确匹配为 Canonical Key。 |
| **Tongyi Qianwen LLM** | `AiParseServiceImpl.PARSE_SYSTEM_PROMPT` | 提示词中的指标参考表通过 `MetricMetadataRegistry` 模板化生成，确保大模型理解的范围与系统校验范围绝对一致。 |

---

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| **Metadata Registry ↔ Validation Pipeline** | 本地 Spring Bean 依赖注入 | 校验管线直接从注册表读取 `MetricDefinition` 进行四级区间比对。 |
| **Validation Pipeline ↔ Request DTOs** | Jakarta Validation 注解联动 | `@ValidMetricRecord` 拦截 Controller 请求，统一抛出 `MethodArgumentNotValidException` 并由全局异常处理器转换。 |
| **Backend REST API ↔ Frontend Pinia Store** | HTTP REST (`/api/metrics/metadata`) | 前端异步拉取，内存持久化，提供全局同步查询方法。 |
| **Frontend Pinia Store ↔ UI Components** | Vue 3 响应式系统 (`computed`) | `QuickInputDialog`、`QuickInput`、`Metrics.vue` 自动绑定元数据属性。 |
| **HealthMetricService ↔ Realtime & Alerts** | Spring `ApplicationEventPublisher` | 即使录入的是极端异常值，只要通过了 Hard Limit 校验，依然正常入库并触发 `MetricRecordedEvent`。 |

---

## New vs. Modified Components

为达成项目质量门禁与实施要求，将组件划分为**新建 (NEW)**与**改造 (MODIFIED)**两类，明确改造范围与边界：

### 1. 新建组件 (NEW)

| 组件全路径 | 职责与技术细节 | 依赖 |
|------------|----------------|------|
| `com.hhs.metadata.MetricDefinition` | Java Record。不可变数据类，封装单一指标全维度元数据（key, aliases, name, category, unit, precision, step, hardMin, hardMax, normalMin, normalMax, guidance）。 | 无 |
| `com.hhs.metadata.MetricMetadataRegistry` | Spring `@Component`。维护 19+ 核心指标字典，构建别名反查索引，提供快速检索与分类过滤。 | `MetricDefinition` |
| `com.hhs.metadata.MetricKeyNormalizer` | 工具/组件类。提供 `toCanonicalKey(key)`、`getAliases(key)`，兼容 camelCase、snake_case、SCREAMING_SNAKE。 | `MetricMetadataRegistry` |
| `com.hhs.validation.MetricValidationPipeline` | 校验管线核心。输入 `(metricKey, value, context)`，评估返回包含严重级别与指导意见的 `MetricValidationResult`。 | `MetricMetadataRegistry`, `MetricKeyNormalizer` |
| `com.hhs.validation.ValidMetricRecord` / `MetricRecordValidator` | Jakarta 类级别约束注解及校验实现，无缝融入 Spring MVC `@Valid` 流程。 | `MetricValidationPipeline` |
| `com.hhs.controller.MetricMetadataController` | 暴露 `GET /api/metrics/metadata` 供各端消费。 | `MetricMetadataRegistry` |
| `frontend: api/metricMeta.ts` | Axios 请求封装，获取后端元数据。 | `@/utils/request` |
| `frontend: stores/metricMeta.ts` | Pinia Store。管理元数据加载生命周期，对外暴露权威名称与属性查询。 | `api/metricMeta`, `metricMetaDefaults` |
| `frontend: composables/useMetricValidation.ts` | 组合式函数。封装表单层四梯度实时判定、胶囊状态计算与输入自纠逻辑。 | `stores/metricMeta` |
| `frontend: utils/metricNormalizer.ts` | 前端双向 Key 映射与显示守卫工具。 | 无依赖（静态字典+Store） |

### 2. 改造组件 (MODIFIED)

| 组件全路径 | 改造前问题与现状 | 改造方案与兼容性保障 |
|------------|------------------|----------------------|
| `com.hhs.dto.HealthMetricRequest` | 硬编码 `@DecimalMax("1000.0")`，导致步数/饮水等指标报错；无针对具体指标的校验。 | 移除 `@DecimalMax("1000.0")`（或放宽至物理极限），添加 `@ValidMetricRecord` 注解，利用管线动态校验。 |
| `com.hhs.service.impl.HealthMetricServiceImpl` | 写入时直接使用前端传入的 raw `metricKey`；单位自动纠正依赖分散的 formatter；查询未考虑别名。 | 注入 `MetricKeyNormalizer` 归一化 Key；查询时使用别名集合匹配存量数据；单位校验委托给注册表。 |
| `com.hhs.service.impl.AiParseServiceImpl` | 包含私有静态 `METRIC_RANGES`、`HEALTH_KEYS`、`WELLNESS_KEYS`，与外界定义脱节。 | 废除私有常量 Map，全量引用 `MetricMetadataRegistry` 与 `MetricValidationPipeline`。 |
| `com.hhs.service.impl.OcrServiceImpl` | 包含私有 `REFERENCE_RANGES`、`DEFAULT_UNITS`、`METRIC_KEY_MAP`。 | 统一对接注册表与归一化器，提升体检指标识别匹配率。 |
| `com.hhs.service.domain.MetricDisplayFormatter` | 仅支持 8 个指标的中文名，缺少别名支持，导致 `bodyTemperature` 泄漏英文。 | 内部委托给 `MetricMetadataRegistry`，若未匹配则由 `MetricKeyNormalizer` 兜底。 |
| `frontend: QuickInputDialog.vue` | 包含硬编码 `METRIC_RANGES`；失焦时清空输入框；错误反馈生硬。 | 移除本地 ranges；从 Store 动态获取步长/精度/范围；移除清空逻辑；增加状态胶囊与解释性提示。 |
| `frontend: QuickInput.vue` | 快捷录入按钮硬编码指标清单与单位。 | 从 Store 读取指标标准信息，保持配置与后端严格一致。 |
| `frontend: views/health/Metrics.vue` | 下拉框 `<el-option>` 静态写死；`metricUnitMap` 本地硬编码；无范围校验。 | 下拉框绑定 Store 动态列表；表单录入集成 `useMetricValidation`。 |
| `frontend: views/health/Thresholds.vue` | 阈值下拉框硬编码 6 项指标。 | 改为从 Store 读取 HEALTH 指标列表，联动显示单位。 |
| `frontend: utils/format.ts` | `getMetricDisplayName` 仅包含少量指标，回退逻辑 `map[key] || key` 导致英文外露。 | 重新实现该方法，优先调用 `metricNormalizer.ts`，确保所有 19+ 指标均能正确显示中文。 |

---

## Suggested Build Order & Dependencies

为了降低集成风险、避免破坏现有 CI/CD 测试流水线，推荐按照以下依赖顺序递进开发与交付：

```
[Phase 1: 后端元数据核心] ──► [Phase 2: 校验管线与接口] ──► [Phase 3: 前端状态与映射工具]
           │                                                       │
           ▼                                                       ▼
[Phase 5: 全渠道对齐 (AI/OCR/存量)] ◄────────────── [Phase 4: 前端交互与弹窗重构]
```

### Step 1: 建立后端元数据注册表与归一化基础 (Zero Breakage Foundation)
- **目标：** 创建 `MetricDefinition`、`MetricMetadataRegistry` 与 `MetricKeyNormalizer`。
- **实施：** 录入全部 19+ 种指标标准参数（心率、收缩压、舒张压、空腹血糖、餐后血糖、体温、体重、BMI、血氧、胆固醇、甘油三酯、睡眠、步数、运动、饮水、心情、精力等）。
- **验证：** 编写单元测试验证所有常见变体（如 `heart_rate`、`HEART_RATE`、`bodyTemperature`）均能稳定解析为 Canonical Key。

### Step 2: 实现分级校验管线与元数据 REST API
- **目标：** 构建 `MetricValidationPipeline` 并发布 `/api/metrics/metadata` 接口。
- **实施：** 实现四梯度判定逻辑；创建 `@ValidMetricRecord` 类级别校验器；改造 `HealthMetricRequest` 放宽粗暴的 1000 上限；在 `MetricMetadataController` 暴露元数据。
- **验证：** 后端单元测试验证正常值、警告值、危急值与物理极限拦截行为。

### Step 3: 前端元数据 Store 与双向归一化工具升级
- **目标：** 在前端建立与后端对应的消费端体系。
- **实施：** 编写 `api/metricMeta.ts` 与 `stores/metricMeta.ts`；编写 `metricNormalizer.ts` 并改造 `format.ts` 的 `getMetricDisplayName`。
- **验证：** Vitest 单元测试覆盖各类驼峰/下划线 key 的中文标签解析，验证无任何指标回退为原始英文代码。

### Step 4: 改造前端录入与交互组件 (UX Upgrade)
- **目标：** 彻底改善用户录入体验与引导。
- **实施：** 重构 `QuickInputDialog.vue`，动态加载 Store 配置，移除失焦清空，集成状态胶囊与解释性提示；改造 `QuickInput.vue`、`Metrics.vue`、`Thresholds.vue` 接入动态字典。
- **验证：** 手动录入各维度指标，检查极值阻断、异常预警与正常展示全链路流畅性。

### Step 5: 全渠道对齐与存量兼容验证 (Cross-Channel Convergence)
- **目标：** 收敛 AI 解析与 OCR 识别中的分散定义，确保系统整洁统一。
- **实施：** 改造 `AiParseServiceImpl` 和 `OcrServiceImpl`，废弃其私有 ranges；在 `HealthMetricServiceImpl` 读写链路注入归一化保护。
- **验证：** 运行 `mvn clean verify -DskipITs` 与前端测试，确保既有 489+ 测试用例全部绿灯通过。

---

## Sources

- **中国高血压防治指南 / 2024 中华医学会糖尿病诊疗标准:** 血压、血糖临床正常与危急值切分点。
- **WHO 国际临床生理参数参考标准:** 心率、血氧饱和度、体温的极限生理区间（Hard Bounds）。
- **Jakarta Bean Validation 3.0 (Hibernate Validator 8.0) 官方文档:** 动态类级别约束与上下文错误构建最佳实践。
- **Element Plus Form Validation Specifications:** 自定义异步校验规则与非侵入式响应设计规范。
- **HHS 系统源码审计:** `hhs-backend` (v3.2.0), `hhs-frontend-v2` (v3.4.15), `schema.sql` (v3.8.0)。

---
*Architecture research for: Intelligent Health Metric Input & Unified Validation (HHS v1.1)*  
*Researched: 2026-11-04*
