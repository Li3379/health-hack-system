# Phase 7: 全渠道校验对齐与跨端测试验证 - Context

**Gathered:** 2026-11-05  
**Phase:** 7 — 全渠道校验对齐与跨端测试验证  
**Status:** Ready for planning  

<domain>
## Phase Boundary

本阶段解决全系统各数据录入渠道（手动表单录入、AI 智能对话/文本解析、OCR 体检报告识别）规则不一致与独立硬编码的割裂问题。核心交付物包括：
1. **重构 `AiParseServiceImpl.java`**：废除类内写死的 `PARSE_SYSTEM_PROMPT` 指标对照表与私有 `METRIC_RANGES` 范围表，改为直接消费 `HealthMetricMetaRegistry` 动态构建 AI 系统提示词，统一 Key 规范化与数值边界校验（SYNC-01）；
2. **重构 `OcrServiceImpl.java`**：废除私有 `METRIC_KEY_MAP`、`DEFAULT_UNITS`、`REFERENCE_RANGES`，全面对接 `HealthMetricMetaRegistry` 与 `MetricKeyNormalizer`；对于体检化验单专属的血常规指标（血红蛋白、白细胞、红细胞、血小板等），建立清晰的扩展字典架构并协同工作；
3. **全渠道指标状态与异常判定统一**：AI 文本抽取与 OCR 报告提取结果与手动录入端四梯度模型（正常、预警偏差、急危重症）完全对齐，并在最终入库时统一受到 `@ValidMetricRecord` 物理极限校验器的严格保护；
4. **跨渠道端到端集成测试与跨端回归**：构建针对手动表单录入、AI 文本抽取、OCR 图像识别三端的端到端集成测试套件，验证指标命名、单位换算、四梯度状态判定规则达到 100% 严格一致。

</domain>

<decisions>
## Implementation Decisions

### 1. AI 提示词与元数据注册中心动态同步 (SYNC-01)
- **D-01 (动态组装提示词):** 在 `AiParseServiceImpl.java` 中，彻底废除写死在代码中的 `PARSE_SYSTEM_PROMPT` 静态 Markdown 表格。改为在 Spring Bean 初始化时或动态构建时，直接从 `@Autowired HealthMetricMetaRegistry` 中提取全部已注册指标的标准 Key、权威中文名称、标准单位、正常参考范围，动态生成结构化提示词表格。无论后续注册表新增或微调何种指标，大模型提示词均保持单点权威、零配置偏差。

### 2. OCR 识别与化验单扩展指标协同架构 (SYNC-01)
- **D-02 (核心注册表+化验扩展字典):** 
  - `OcrServiceImpl.java` 提取健康与保健指标时，优先委派给 `HealthMetricMetaRegistry` 进行权威 Key 规范化、单位补全与临床参考区间查询；
  - 针对专属化验单/体检报告的血常规指标（如 `hemoglobin` 血红蛋白、`wbc` 白细胞、`rbc` 红细胞、`platelet` 血小板），维护清晰的 `LabRoutineMetricRegistry` 或结构化扩展映射，作为医学体检报告领域的专门补充，既不污染通用健康监测核心注册表，又保证体检报告识别的完整性。

### 3. 全渠道四梯度异常状态与极限校验对齐 (VAL-02, VAL-03, VAL-05, SYNC-01)
- **D-03 (四梯度统一判定+入库物理极限校验):**
  - AI 解析结果与 OCR 提取结果中的预警/异常标注逻辑，废除原有的二元宽松规则，完全对齐四梯度模型（Tier 1 正常、Tier 2 预警偏差、Tier 3 急危重症）；
  - 所有渠道（手动录入、AI 提取确认、OCR 结果确认）最终提交入库时，请求 DTO 统一通过 `@ValidMetricRecord` Jakarta 类级别注解拦截不可能极限数值（Tier 4），保障数据库与下游健康评分引擎免受恶性畸变数据污染。

### 4. 血压跨渠道同频拆分与归一化 (KEY-01, KEY-02, GUIDE-03)
- **D-04 (统一血压拆分与规范化 Key 保证):**
  - AI 解析与 OCR 识别中提取到“血压”组合数据时，继续严格执行拆分为 `systolicBP` 与 `diastolicBP` 的标准约定；
  - 彻底杜绝下游数据库存入非法的 `bloodPressure` 混合 key，确保与 Phase 4/5/6 确立的小驼峰标准 Key 保持 100% 统一。

### Claude's Discretion
- `AiParseServiceImpl.java` 动态提示词的字符串模板缓存设计（初始化时预生成，避免每次解析重复拼接）；
- `OcrServiceImpl.java` 中化验单血常规扩展指标常量的包结构组织；
- 跨渠道全链路集成测试用例数据设计。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 后端核心元数据与校验资产 (Phase 4 交付物)
- `hhs-backend/src/main/java/com/hhs/service/domain/HealthMetricMetaRegistry.java` — 权威 23 项指标不可变元数据注册中心
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricKeyNormalizer.java` — 后端 Key 归一化与别名映射工具
- `hhs-backend/src/main/java/com/hhs/service/domain/MetricDefinition.java` — 指标不可变 Java Record 实体
- `hhs-backend/src/main/java/com/hhs/validation/ValidMetricRecord.java` — Jakarta 类级别校验注解
- `hhs-backend/src/main/java/com/hhs/validation/MetricRecordValidator.java` — 生理极限拦截校验器

### 待重构的核心业务服务
- `hhs-backend/src/main/java/com/hhs/service/impl/AiParseServiceImpl.java` — AI 智能文本解析服务实现类
- `hhs-backend/src/main/java/com/hhs/service/impl/OcrServiceImpl.java` — OCR 体检报告识别服务实现类
- `hhs-backend/src/main/java/com/hhs/dto/MetricParseRequest.java` — AI 解析请求 DTO
- `hhs-backend/src/main/java/com/hhs/dto/OcrConfirmRequest.java` — OCR 确认入库请求 DTO

### 前端 Phase 5/6 统一规范 (对照参考)
- `hhs-frontend-v2/src/constants/metricMeta.ts` — 前端 23 项指标静态离线字典
- `hhs-frontend-v2/src/utils/metricValidation.ts` — 前端四梯度生理校验纯函数与 Panic Limits
- `hhs-frontend-v2/src/views/data-input/components/QuickInputDialog.vue` — 手动录入端标准规范

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `HealthMetricMetaRegistry`: 具备 `getAllDefinitions()`、`getDefinition(key)`、`contains(key)`，可直接遍历生成 AI 提示词与校验边界；
- `MetricKeyNormalizer.toCanonicalKey()`: 已经覆盖 camelCase、snake_case、SCREAMING_SNAKE_CASE 及缩写别名；
- `ValidMetricRecord`: 已经用于 `HealthMetricRequest`，统一拦截 Tier 4 物理超限值。

### Established Patterns
- Spring Boot Controller-Service-Mapper 模式；
- Spring Validation `@Valid` 参数绑定拦截与统一异常处理器 `GlobalExceptionHandler`；
- JUnit 5 + Mockito 单元与集成测试。

### Integration Points
- 在 `AiParseServiceImpl` 中注入 `HealthMetricMetaRegistry`，移除硬编码 `METRIC_RANGES` 与 `HEALTH_KEYS` / `WELLNESS_KEYS`；
- 在 `OcrServiceImpl` 中注入 `HealthMetricMetaRegistry`，替换 `REFERENCE_RANGES` 与 `DEFAULT_UNITS`；
- 编写集成测试套件验证手动、AI、OCR 三种渠道输入等价性。

</code_context>

<specifics>
## Specific Ideas

- **动态 Prompt 格式示例：**
  ```markdown
  ## 指标对照表
  ### 健康指标 (HEALTH):
  | 中文名称 | metricKey | 单位 | 正常参考区间 | 生理极限 |
  | 心率 | heartRate | 次/分 | 60 ~ 100 | 20 ~ 300 |
  ...
  ```
  AI 解析服务在 `@PostConstruct` 阶段动态组装该对照表并缓存在内存中。

</specifics>

<deferred>
## Deferred Ideas

- **v1.2 规划：** 支持体检报告多页 PDF 批量上传与自适应表格跨页拼接。
- **v1.2 规划：** 多模态大模型视觉端直接提取化验单图片数据。

</deferred>

---

*Phase: 07-全渠道校验对齐与跨端测试验证*  
*Context gathered: 2026-11-05*  
