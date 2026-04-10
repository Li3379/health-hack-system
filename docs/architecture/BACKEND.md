# 后端架构详解

> 本文档详细描述 HHS 后端的技术架构、模块划分和设计模式。

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.0 | 应用框架 |
| Java | 17 | 编程语言 |
| Spring Security | 6.x | 安全框架 |
| JWT | - | 认证令牌 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| MySQL | 8.0+ | 关系数据库 |
| Redis | 7.0+ | 缓存/会话存储 |
| LangChain4j | 0.35.0 | AI 集成框架 |
| Knife4j | 4.x | API 文档 |

## 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway (Nginx)                      │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Spring Boot Application                     │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    Controller Layer                       │    │
│  │  UserController │ HealthMetricController │ AIController  │    │
│  │  AlertController │ RealtimeController │ DeviceController │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                     Service Layer                         │    │
│  │  UserService │ HealthMetricService │ AIChatService       │    │
│  │  AlertService │ DeviceSyncService │ AlertRuleEngine      │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                     Mapper Layer                          │    │
│  │          MyBatis-Plus BaseMapper + XML Mappers           │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│                              ▼                                   │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                   Data Layer                              │    │
│  │           MySQL (主存储)  │  Redis (缓存/会话)           │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## 模块划分

### 1. Controller 层

| 控制器 | 路径前缀 | 职责 |
|--------|----------|------|
| `UserController` | `/api` | 用户认证、个人资料管理 |
| `HealthMetricController` | `/api/metrics` | 健康指标 CRUD |
| `HealthScoreController` | `/api/health-score` | 健康评分计算 |
| `RealtimeController` | `/api/realtime` | 实时指标录入与查询 |
| `AlertController` | `/api/alerts` | 健康预警管理 |
| `AIParseController` | `/api/ai-parse` | AI 智能解析 |
| `OcrController` | `/api/ocr` | OCR 图片识别 |
| `DevicePlatformConfigController` | `/api/device-platform` | 设备平台配置 |
| `ScreeningController` | `/api/screening` | 筛查记录管理 |
| `PreventionController` | `/api/prevention` | 预防保健管理 |
| `StatsController` | `/api/stats` | 统计数据 |

### 2. Service 层

#### 核心业务服务

| 服务 | 职责 |
|------|------|
| `UserService` | 用户注册、登录、资料管理 |
| `HealthMetricService` | 健康指标业务逻辑 |
| `HealthScoreService` | 健康评分计算与缓存 |
| `AlertService` | 预警创建、查询、状态管理 |
| `AlertRuleEngine` | 指标评估、规则匹配 |
| `AIChatService` | AI 对话、上下文管理 |
| `AiParseService` | 自然语言解析为健康指标 |
| `OcrService` | 百度 OCR 集成 |

#### 设备同步服务

| 服务 | 职责 |
|------|------|
| `DeviceConnectionService` | 设备连接状态管理 |
| `DevicePlatformService` | 平台 OAuth 流程 |
| `DeviceSyncOrchestrationService` | 同步编排与调度 |
| `XiaomiHealthService` | 小米健康数据同步 |
| `TokenEncryptionService` | OAuth Token 加密存储 |

#### 预警子系统服务

| 服务 | 职责 |
|------|------|
| `IntelligentAlertService` | 智能预警生成 |
| `AlertAIAnalysisService` | AI 预警分析 |
| `AlertDeduplicator` | 预警去重 |
| `AlertTemplateService` | 预警模板管理 |
| `RecoveryNotifier` | 恢复通知 |

### 3. 配置类

| 配置类 | 职责 |
|--------|------|
| `SecurityConfig` | Spring Security 配置、JWT 过滤器 |
| `JwtProperties` | JWT 密钥配置与验证 |
| `RedisConfig` | Redis 连接、序列化配置 |
| `LangChain4jConfig` | AI 模型配置（通义千问） |
| `WebMvcConfig` | CORS、静态资源、拦截器 |
| `TokenEncryptionProperties` | 设备 Token 加密配置 |

### 4. 组件

| 组件 | 职责 |
|------|------|
| `AIRateLimiter` | AI 请求限流（用户 20 次/天，访客 3 次/天） |
| `DeviceSyncRateLimiter` | 设备同步频率限制 |
| `AlertEventListener` | 预警事件监听器 |
| `CacheInvalidationListener` | 缓存失效监听器 |

## 设计模式

### 1. 分层架构

```
Controller → Service → Mapper → Database
```

- Controller 仅做路由和参数校验
- Service 包含业务逻辑
- Mapper 数据访问

### 2. 领域事件

```java
// 预警生成事件
AlertGeneratedEvent → AlertEventListener → 推送通知

// 指标录入事件
MetricRecordedEvent → CacheInvalidationListener → 缓存刷新
```

### 3. 策略模式

预警规则引擎使用策略模式处理不同指标类型：

```java
public interface AlertRuleEngine {
    List<AlertVO> evaluateMetric(RealtimeMetric metric);
    UserThreshold getApplicableThreshold(Long userId, String metricKey);
}
```

### 4. 仓库模式

数据访问通过 MyBatis-Plus Mapper 抽象：

```java
public interface RealtimeMetricMapper extends BaseMapper<RealtimeMetric> {
    List<RealtimeMetric> getLatestMetricsByUser(Long userId);
    List<RealtimeMetric> getMetricsInRange(Long userId, String metricKey, 
                                            LocalDateTime start, LocalDateTime end);
}
```

## API 响应格式

所有 API 统一使用 `Result<T>` 响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

错误响应：

```json
{
  "code": 400,
  "message": "参数校验失败",
  "data": null
}
```

## 安全机制

### JWT 认证流程

```
1. 用户登录 → 验证凭据
2. 生成 JWT Token（有效期 7 天）
3. 客户端存储 Token
4. 请求携带 Authorization: Bearer <token>
5. JwtFilter 验证 Token
6. 设置 SecurityContext
```

### 密钥要求

- JWT_SECRET: 至少 32 字符（256 位）
- DEVICE_ENCRYPTION_KEY: 用于 OAuth Token 加密

### 敏感数据加密

OAuth Token 使用 AES 加密存储：

```java
TokenEncryptionService.encrypt(token)
TokenEncryptionService.decrypt(encryptedToken)
```

## 限流策略

| 场景 | 限制 |
|------|------|
| AI 对话（登录用户） | 20 次/天 |
| AI 对话（访客） | 3 次/天 |
| 设备同步 | 1 次/分钟 |
| 预警生成 | 10 次/小时/用户 |

## 性能优化

### 数据库

- 分区表：`realtime_metric` 按月分区
- 索引：用户 ID、指标类型、时间组合索引
- 连接池：HikariCP

### 缓存

- 健康评分缓存（Redis，1 小时过期）
- 用户会话缓存
- 预警去重缓存

### 异步处理

- 预警推送使用事件驱动
- 设备同步支持异步执行

## 配置文件

| 文件 | 用途 |
|------|------|
| `application.yml` | 基础配置 |
| `application-dev.yml` | 开发环境 |
| `application-prod.yml` | 生产环境 |

关键配置项：

```yaml
security:
  jwt:
    secret: ${JWT_SECRET}
    expire-days: 7

spring:
  ai:
    langchain4j:
      openai:
        chat-model:
          api-key: ${DASH_SCOPE_API_KEY}
          base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
          model-name: qwen3.5-flash
```

## 错误处理

全局异常处理器 `GlobalExceptionHandler`：

- `BusinessException`: 业务异常，返回友好错误信息
- `MethodArgumentNotValidException`: 参数校验失败
- `Exception`: 未知异常，返回 500

## 日志规范

- 使用 SLF4J + Lombok `@Slf4j`
- 关键操作记录 INFO 日志
- 错误记录 ERROR 日志（包含堆栈）
- 敏感信息脱敏