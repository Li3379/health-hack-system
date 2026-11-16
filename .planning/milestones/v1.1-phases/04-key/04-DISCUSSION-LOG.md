# Phase 4: 后端统一指标元数据注册中心与 Key 归一化 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-04  
**Phase:** 4-后端统一指标元数据注册中心与 Key 归一化  
**Areas discussed:** 标准 Key 规范与转换策略, 元数据存储与交付形式, 历史存量数据兼容方案, 物理极限拦截响应契约  

---

## 1. 标准 Key 规范与转换策略

### 规范 Key 风格
| Option | Description | Selected |
|--------|-------------|----------|
| 统一为 camelCase（推荐） | 以 camelCase 为全系统唯一标准存储与通信格式（如 heartRate, bloodGlucose, systolicBP）。后端在 Controller 入参及 Jackson 反序列化时将所有下划线/大写/别名自动映射为规范小驼峰。 | ✓ |
| 统一为 snake_case | 以 snake_case 为全系统唯一存储格式（如 heart_rate, blood_glucose）。由于当前大部分业务逻辑已写死为小驼峰，此方案需要重构较多后端与前端现有逻辑。 | |

**User's choice:** 统一为 camelCase（推荐）  
**Notes:** 契合现有 Java DTO 与大多数 Vue 组件，变动风险最小。

### 别名映射策略
| Option | Description | Selected |
|--------|-------------|----------|
| 显式别名字典映射（推荐） | 在注册表中为每个指标声明可信别名集合（如 bloodGlucose 别名涵盖 glucose, blood_glucose, BLOOD_GLUCOSE；bodyTemperature 别名涵盖 temperature, body_temperature）。未识别的未知 key 原样保留不报错。 | ✓ |
| 纯算法通用下划线转驼峰 | 仅靠算法通用转换（下划线自动转驼峰），但遇到包含缩写（如 systolic_bp 会被算法误转为 systolicBp 而非规范的 systolicBP）时容易失效。 | |

**User's choice:** 显式别名字典映射（推荐）  
**Notes:** 规避大写缩写词（如 BP）被算法误转换为首字母大写的陷阱。

---

## 2. 元数据存储与交付形式

### 元数据底层架构
| Option | Description | Selected |
|--------|-------------|----------|
| Java内存权威注册表（推荐） | 使用 Java Record（MetricDefinition）构建内存型不可变注册表（@Component HealthMetricMetaRegistry），零数据库表改动，编译期强类型安全，启动立即可用。 | ✓ |
| 数据库动态配置表 | 新建 MySQL 元数据配置表并在 Redis 中缓存，支持系统管理员在后台动态新增/修改指标阈值和单位（开发成本与维护复杂度较高）。 | |

**User's choice:** Java内存权威注册表（推荐）  
**Notes:** 适合医学长期静态常量，免除 DDL 迁移风险。

### API 交付与缓存策略
| Option | Description | Selected |
|--------|-------------|----------|
| RESTful+长期HTTP缓存（推荐） | 提供 GET /api/metrics/metadata，响应头添加 Cache-Control: max-age=86400 与 ETag，前端拉取一次后常态化缓存在 Pinia 中。 | ✓ |
| 实时无缓存请求 | 每次进入数据录入页均实时无缓存请求后端接口，确保绝对最新但增加轻微网络开销。 | |

**User's choice:** RESTful+长期HTTP缓存（推荐）  
**Notes:** 结合浏览器强缓存与 Pinia 状态管理，兼顾高性能与热重启穿透能力。

---

## 3. 历史存量数据兼容方案

| Option | Description | Selected |
|--------|-------------|----------|
| 查询别名兼容+幂等清洗（推荐） | 查询层透明别名兼容 + 幂等更新脚本：后端在根据 metricKey 查询时（如心率），通过 Normalizer 自动将查询条件包装为 IN ('heartRate', 'heart_rate')；同时提供一次性 SQL 补丁在发布时可选清洗历史数据。 | ✓ |
| 仅代码层透明兼容，不碰DB | 仅在服务层做查询别名展开，绝不修改数据库现有任何历史行（完全只读兼容）。 | |
| 强制全量清洗历史数据 | 强制在上线前执行数据库全量 UPDATE 将所有历史数据清洗为驼峰 key，查询层不保留别名展开逻辑。 | |

**User's choice:** 查询别名兼容+幂等清洗（推荐）  
**Notes:** 零停机，安全过渡，保障前后历史数据在图表与列表上的绝对连续性。

---

## 4. 物理极限拦截响应契约

### 错误提示文案风格
| Option | Description | Selected |
|--------|-------------|----------|
| 仅提示合法区间 | 精简提示：仅告知指标合法区间。例如：「心率有效生理区间为 20 ~ 300 次/分，请核对」。 | ✓ |
| 参数+极值+操作建议（推荐） | 结构化语义化提示：明确指明指标名称、输入值、物理极限区间及操作指引。例如：「心率测量值 (999 次/分) 超出人体存活极限范围 (20 ~ 300 次/分)，请核对是否多输了数字」。 | |

**User's choice:** 仅提示合法区间  
**Notes:** 保持反馈精简明确，符合专业系统统一风格。

### 校验切入层级
| Option | Description | Selected |
|--------|-------------|----------|
| Jakarta标准注解校验（推荐） | 自定义 Jakarta 注解 @ValidMetricRecord 作用于 HealthMetricRequest，在 Controller 参数绑定时直接拦截并复用 GlobalExceptionHandler 统一返回规范错误结构。 | ✓ |
| Service业务层手动抛出 | 在 HealthMetricServiceImpl 业务逻辑层手动校验并抛出自定义 BusinessException。 | |

**User's choice:** Jakarta标准注解校验（推荐）  
**Notes:** 遵循 Spring Boot 标准 Bean Validation 范式。

---

## Claude's Discretion

- `MetricDefinition` Record 内部命名细节。
- `MetricKeyNormalizer` 并发安全与内存优化。
- 自动化单元测试矩阵的设计。

## Deferred Ideas

- Phase 5: 前端元数据 Store 与全站格式化防英文外露。
- Phase 6: 前端非破坏性录入引导、四梯度宽容校验与友好反馈。
- Phase 7: 全渠道校验对齐与跨端测试验证。
