<!-- Parent: ../../../../../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# com.hhs

Spring Boot 后端主源码根目录，包含所有 REST API、业务逻辑、数据访问、安全和配置。

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `controller/` | REST 端点 (25 个 Controller)，所有返回 `Result<T>` |
| `service/` | 业务逻辑接口 (35+ 接口) |
| `service/impl/` | 服务实现 |
| `service/domain/` | 无状态领域逻辑 (ScoreCalculator, DimensionScoreCalculator, RiskScorer, MetricValidator, ThresholdEvaluator, RiskAnalyzer, ProfileScorer, WellnessScorer, ScreeningScorer, MetricsScorer, MetricDisplayFormatter, MetricCategoryService, ThresholdComparator, ThresholdRangeChecker, AlertRateLimiter, ConversationService, MessageService) |
| `service/alert/` | 智能告警子系统 (AlertDeduplicator, TrendPredictor, AlertFrequencyStrategy, AlertAIAnalysisService, IntelligentAlertService, RecoveryNotifier, AlertTemplateService, TrendDirection, TrendResult) |
| `service/push/` | 推送服务 (PushChannel 接口, PushChannelManager, PushStrategyService, ChannelType, PushResult) |
| `service/push/channel/` | 推送渠道实现 (WebSocketPushChannel, EmailPushChannel, FeishuPushChannel, WeComPushChannel) |
| `entity/` | MyBatis-Plus 实体 (33 个表) |
| `mapper/` | MyBatis-Plus Mapper 接口 (33 个) |
| `dto/` | 请求/响应 DTO |
| `dto/ai/` | AI 相关 DTO (AIChatRequest/Response, AIClassifyRequest/Response, AIHistoryResponse, ChatContextVO, ChatSessionVO, ConversationVO) |
| `vo/` | 视图对象 (36 个) |
| `domain/event/` | Spring 领域事件 (AlertGeneratedEvent, MetricRecordedEvent, OcrProcessingEvent, ScoreUpdatedEvent) |
| `domain/handler/` | 事件监听器 (AlertEventListener, CacheInvalidationListener, GamificationEventListener, OcrEventListener, ScoreUpdatedEventListener) |
| `config/` | Spring 配置 (SecurityConfig, RedisConfig, MyBatisPlusConfig, LangChain4jConfig, WebSocketConfig, AsyncConfig, Knife4jConfig, JacksonConfig, WebMvcConfig, RestTemplateConfig, SecurityHeadersConfig, DeviceMockProperties, DeviceOAuthProperties, TokenEncryptionProperties, AchievementSeedRunner) |
| `security/` | JWT 认证 (JwtAuthenticationFilter, JwtUtil, JwtProperties, JwtSecretValidator, SecurityUtils, UserDetailsServiceImpl, LoginUser, RestAuthenticationEntryPoint, RestAccessDeniedHandler, PathValidationUtil) |
| `exception/` | 异常体系 (BusinessException, SystemException, AuthenticationCredentialsNotFoundException, GlobalExceptionHandler) |
| `common/` | 通用 (Result, PageResult, Constants) |
| `common/constant/` | 常量类 |
| `common/enums/` | 通用枚举 (ErrorCode, MetricCategory, ResolutionStrategy) |
| `enums/` | 业务枚举 (PlatformCapability, PlatformStatus) |
| `component/` | 基础设施 (BaiduOcrClient, ContentFilter, AIRateLimiter, AuthRateLimiter, DeviceSyncRateLimiter) |
| `validation/` | 自定义校验 (ValidMetricRange, ValidProfileData, MetricRangeValidator, ProfileDataValidator) |
| `websocket/` | WebSocket (HealthWebSocketHandler, WebSocketAuthInterceptor) |
| `schedule/` | 定时任务 (DataCleanupScheduler, ReminderScheduler) |

## For AI Agents

- **新增 API**: Controller -> Service 接口 -> ServiceImpl -> (可选) Mapper/Entity
- **新增领域事件**: 创建 Event 类 -> ApplicationEventPublisher 发布 -> 创建 Handler
- **新增推送渠道**: 实现 PushChannel 接口，注册到 PushChannelManager
- **错误码**: 在 ErrorCode enum 中添加，使用 `BusinessException(ErrorCode.XXX)` 抛出
- **配置类**: @Configuration + @Bean，注意 profile 条件 (@Profile)
- **校验注解**: 自定义注解在 validation/，配合 @Valid 使用
- **领域服务**: service/domain/ 下的类无 Spring 注解，纯逻辑，可独立单元测试

## Key Files

- `HhsApplication.java` — Spring Boot 启动类
