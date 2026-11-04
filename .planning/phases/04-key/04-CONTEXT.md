# Phase 4: 后端统一指标元数据注册中心与 Key 归一化 - Context

**Gathered:** 2026-11-04  
**Phase:** 4 — 后端统一指标元数据注册中心与 Key 归一化  
**Status:** Ready for planning  

<domain>
## Phase Boundary

本阶段为全系统建立不可变的指标元数据权威定义源（Single Source of Truth, SSOT）与多格式 Key 规范化引擎。交付内容包括：
1. 内存型指标元数据权威注册表（`HealthMetricMetaRegistry` 与 `MetricDefinition`）；
2. 指标 Key 双向归一化与别名映射转换器（`MetricKeyNormalizer`）；
3. 元数据查询 REST 接口 `GET /api/metrics/metadata` 及其 HTTP 强缓存；
4. 基于 Jakarta `@ValidMetricRecord` 的请求级物理生理极限硬校验；
5. 服务层多格式 Key 查询兼容机制与存量数据清洗 SQL 补丁。

*（注：前端元数据 Store、UI 引导组件与四梯度展示交互由 Phase 5 / Phase 6 实现；AI / OCR 校验对齐由 Phase 7 实现）*
</domain>

<decisions>
## Implementation Decisions

### 1. 标准 Key 规范与命名映射 (KEY-01, KEY-02)
- **D-01 (规范Key风格):** 全系统权威标准指标键统一为 `camelCase`（小驼峰命名，如 `heartRate`, `bloodGlucose`, `systolicBP`, `diastolicBP`, `bodyTemperature`）。—— **Reversibility:** costly — 涉及到 API 契约与数据库标准存储格式，与现有 Java 实体、MyBatis 属性及大部分前端组件保持自然契合。
- **D-02 (别名字典映射):** 采用权威白名单别名字典（`MetricKeyNormalizer`）实现精确双向映射，重点保护特定英文缩写（如 `systolic_bp` 必须映射为规范的 `systolicBP` 而非纯算法转换的 `systolicBp`）。对未识别的未知扩展 key 采取宽松透传、原样保留策略。

### 2. 元数据字典存储与接口交付 (META-01, META-02)
- **D-03 (内存权威注册表):** 采用 Java Record `MetricDefinition` 结合 Spring `@Component HealthMetricMetaRegistry` 作为权威元数据单点真值源（SSOT）。零数据库表迁移开销、编译期强类型安全、微秒级响应。管理全量 19+ 种健康/保健指标（标准 Key、中文名、标准单位、小数精度、步长 step、物理极值、临床参考区间）。
- **D-04 (REST 接口与缓存):** 新增 `MetricMetadataController` 暴露 `GET /api/metrics/metadata` 端点，返回全量指标元数据字典；响应头显式携带 `Cache-Control: public, max-age=86400` 与 ETag，允许前端和网关长期缓存并在后端重启/更新时自动穿透验证。

### 3. 历史存量数据兼容性 (KEY-02)
- **D-05 (查询透明别名展开):** `HealthMetricServiceImpl`（以及趋势查询、告警规则检查点）在按 `metricKey` 检索时，通过 `MetricKeyNormalizer.getAliases(key)` 自动包装 SQL `IN ('heartRate', 'heart_rate')`，确保历史存量下划线数据能够被无缝查出，零停机、零锁表。
- **D-06 (幂等数据清洗脚本):** 提供独立的 `migration/v1.1_normalize_metric_keys.sql` 脚本，在生产部署维护窗口可选执行，对历史记录进行安全的幂等更新（`UPDATE health_metric SET metric_key = 'heartRate' WHERE metric_key = 'heart_rate'`）。

### 4. 物理生理极限硬拦截与反馈格式 (META-01)
- **D-07 (校验切入层级):** 针对物理不可能生理极值（Tier 1 极限，如心率 400 bpm、体温 80°C、负数数值），定义自定义类级别校验注解 `@ValidMetricRecord`，并在 Controller 接收 `HealthMetricRequest` 时通过 Jakarta Validation 框架直接拦截。
- **D-08 (简洁明确的错误反馈):** 极值拦截错误文案严格聚焦于有效区间引导，格式统一为：“`[指标中文名]有效生理区间为 [min] ~ [max] [unit]，请核对后重新输入`”，由现有的 `GlobalExceptionHandler` 统一转换为标准响应返回给调用端。

### Claude's Discretion
- `MetricDefinition` 的内部字段命名（采用 Java 标准 record 命名规范）；
- `MetricKeyNormalizer` 内部的并发安全字典初始化方式（推荐 `Map.ofEntries` 与不可变集合）；
- 单元测试用例的覆盖策略（确保全部 19+ 个指标的极值拦截、别名双向映射及边界用例覆盖率达 100%）。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 领域与生态调研
- `.planning/research/SUMMARY.md` — HHS Milestone v1.1 全景技术与架构总结报告
- `.planning/research/STACK.md` — 技术栈选型、零新增依赖决策与代码库技术契约
- `.planning/research/FEATURES.md` — 生理指标数值光谱、四梯度校验规范与临床指南参考
- `.planning/research/ARCHITECTURE.md` — 组件交互图、元数据数据流与分阶段构建顺序
- `.planning/research/PITFALLS.md` — 关键陷阱规避清单（防止病理异常被拒、单位不一致、缩写转驼峰缺陷）

### 现有核心代码（修改与集成锚点）
- `hhs-backend/src/main/resources/sql/schema.sql` — `health_metric` 表结构与现有字段定义
- `hhs-backend/src/main/java/com/hhs/dto/HealthMetricRequest.java` — 待增加 `@ValidMetricRecord` 校验与 Key 规范化支持的入参 DTO
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricDisplayFormatter.java` — 现有格式化组件（后续重构其内部数据源以委托给 Registry）
- `hhs-backend/src/main/java/com/hhs/service/impl/HealthMetricServiceImpl.java` — 指标创建与查询主链路，集成查询别名展开
- `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java` — 现有全局校验异常处理逻辑
- `hhs-backend/src/main/java/com/hhs/service/impl/AiParseServiceImpl.java` — 现有硬编码 `METRIC_RANGES`（供核对参考）

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GlobalExceptionHandler.java`: 已有 `handleMethodArgumentNotValid` 机制，返回 `code: 40001` 与结构化 `errors` 列表，完美承接自定义校验器的异常抛出。
- `MetricCategoryService.java`: 现有的指标分类推断服务，可作为参考或直接融入元数据定义。
- `HealthMetricVO.java`: 包含 `metricKey`, `metricName`, `value`, `unit`, `category`，元数据返回结构可与其保持风格一致。

### Established Patterns
- Spring `@Component` 领域服务模式（如 `MetricDisplayFormatter`、`DimensionScoreCalculator`）。
- Jakarta Validation 注解扩展模式（`ConstraintValidator<A, T>`）。
- MyBatis-Plus `LambdaQueryWrapper` 查询构建方式。

### Integration Points
- 新增 `com.hhs.service.domain.HealthMetricMetaRegistry`：单例注册表。
- 新增 `com.hhs.service.domain.MetricKeyNormalizer`：静态规范化工具类。
- 新增 `com.hhs.validation.ValidMetricRecord` 与 `MetricRecordValidator`：自定义参数校验。
- 新增 `com.hhs.controller.MetricMetadataController`：暴露 `GET /api/metrics/metadata`。
- 修改 `HealthMetricRequest.java`：挂载自定义校验注解，增加 setter 归一化。
- 修改 `HealthMetricServiceImpl.java`：在 `list`、`getTrend` 中扩展别名查询。

</code_context>

<specifics>
## Specific Ideas

- **指标范围医学标准：**
  - 心率 (`heartRate`): 生理极限 20 ~ 300 bpm，临床参考 60 ~ 100 bpm，单位 "次/分"（或 bpm）
  - 收缩压 (`systolicBP`): 生理极限 40 ~ 300 mmHg，临床参考 90 ~ 139 mmHg，单位 "mmHg"
  - 舒张压 (`diastolicBP`): 生理极限 20 ~ 200 mmHg，临床参考 60 ~ 89 mmHg，单位 "mmHg"
  - 空腹血糖 (`bloodGlucose`): 生理极限 1.0 ~ 33.3 mmol/L，临床参考 3.9 ~ 6.1 mmol/L，单位 "mmol/L"
  - 体温 (`bodyTemperature`): 生理极限 34.0 ~ 42.5 °C，临床参考 36.0 ~ 37.2 °C，单位 "°C"
  - 体重 (`weight`): 生理极限 1.0 ~ 500.0 kg，单位 "kg"
- **缩写别名规范：**
  - `systolic_bp` / `systolicBp` -> `systolicBP`
  - `diastolic_bp` / `diastolicBp` -> `diastolicBP`
  - `blood_glucose` / `glucose` -> `bloodGlucose`
  - `temperature` / `body_temperature` -> `bodyTemperature`
  - `heart_rate` -> `heartRate`

</specifics>

<deferred>
## Deferred Ideas

- **Phase 5 承接：** 前端 Pinia Store、`format.ts` 重构及全站中文标签防英文外露。
- **Phase 6 承接：** `QuickInputDialog.vue` 前端非破坏性录入引导、四梯度色彩状态胶囊与血压联动自纠。
- **Phase 7 承接：** `AiParseServiceImpl` 与 `OcrServiceImpl` 接入统一注册表。
- **v1.2 以后规划：** 用户个性化基线阈值与时间自适应填报推荐。

</deferred>

---

*Phase: 04-后端统一指标元数据注册中心与 Key 归一化*  
*Context gathered: 2026-11-04*  
