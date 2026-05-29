<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# hhs-backend

Spring Boot 3.2 后端服务，提供健康监控 REST API、AI 分析、设备同步、实时推送。Java 17 + MyBatis-Plus + JWT 认证。

## Key Files

- `pom.xml` — Maven 依赖和构建配置
- `src/main/resources/application.yml` — 主配置文件
- `src/main/resources/application-dev.yml` — 开发环境配置
- `src/main/resources/application-prod.yml` — 生产环境配置
- `src/main/resources/sql/schema.sql` — 数据库 schema (35 张表，748 行)

## Subdirectories

Under `src/main/java/com/hhs/`:

| Directory | Purpose |
|-----------|---------|
| `controller/` | REST 端点，所有返回 `Result<T>` |
| `service/` | 业务逻辑接口 |
| `service/impl/` | 服务实现 |
| `service/domain/` | 无状态领域逻辑 (ScoreCalculator, RiskScorer, MetricValidator) |
| `service/alert/` | 智能告警子系统 (去重、趋势预测、AI 分析、频率策略) |
| `service/push/` | 多渠道推送 (WebSocket, Email, Feishu, WeCom) |
| `entity/` | MyBatis-Plus 实体类 (35 表) |
| `mapper/` | MyBatis-Plus Mapper 接口 |
| `dto/` | 请求/响应 DTO |
| `vo/` | 视图对象 |
| `domain/event/` | Spring 领域事件 (AlertGeneratedEvent, MetricRecordedEvent, OcrProcessingEvent, ScoreUpdatedEvent) |
| `domain/handler/` | 事件监听器 |
| `config/` | Spring 配置类 (SecurityConfig, RedisConfig, MyBatisPlusConfig 等) |
| `security/` | JWT 认证 (JwtAuthenticationFilter, JwtUtil, SecurityUtils) |
| `exception/` | 异常体系 (BusinessException, SystemException, GlobalExceptionHandler) |
| `common/` | 通用工具 (Result, PageResult, ErrorCode, Constants) |
| `component/` | 基础设施 (BaiduOcrClient, ContentFilter, 限流器) |
| `enums/` | 枚举类 |
| `validation/` | 自定义校验注解 |
| `websocket/` | WebSocket 端点和拦截器 |
| `schedule/` | 定时任务 |

## Test Directories

Under `src/test/java/com/hhs/`:

| Directory | Purpose |
|-----------|---------|
| `service/` | 单元测试 (JUnit 5 + Mockito) |
| `integration/` | 集成测试 (Testcontainers MySQL) |
| `security/` | 安全测试 (CORS, 路径遍历) |
| `controller/` | 控制器测试 |
| `domain/handler/` | 事件处理器测试 |
| `performance/` | 性能测试 |

## For AI Agents

- 服务层遵循 Interface + Impl 模式：`FooService`（接口）+ `FooServiceImpl`（实现），实现类在 `service/impl/`
- 领域服务 (`service/domain/`) 是纯逻辑类，无 Spring 注解，可独立测试
- 错误处理：抛 `BusinessException(ErrorCode.XXX)` 表示业务错误，`GlobalExceptionHandler` 自动翻译为 HTTP 响应并支持 i18n
- 所有 API 返回 `Result<T>`，成功 code 为 200；前端 Axios 拦截器检查 `code === 200`
- MyBatis-Plus：实体用 `@TableName`/`@TableId`/`@TableField`，Mapper 继承 `BaseMapper<T>`，无需 XML
- 领域事件：通过 `ApplicationEventPublisher` 发布，`domain/handler/` 中的监听器处理（缓存失效、告警、打卡）
- Spring profiles：`dev`（默认，调试日志，宽松 CORS，mock 设备数据）、`prod`（严格 CORS）、`test`（完全开放）
- 集成测试基类：`AbstractIntegrationTest`（Testcontainers MySQL）
- 覆盖率：JaCoCo 30% line / 25% branch，排除 entity/dto/vo/exception/config/component/controller/mapper
- 限流：`AIRateLimiter`（AI 端点）、`DeviceSyncRateLimiter`（设备同步）
- WebSocket 认证：通过 `WebSocketAuthInterceptor`（token 在 query param）

## Dependencies

Spring Boot 3.2, MyBatis-Plus, Spring Security, LangChain4j (qwen3.5-flash), Baidu OCR SDK, JavaMail, WebSocket, Redis, MySQL 8.0+
