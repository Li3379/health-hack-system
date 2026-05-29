<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# config/

**Purpose**: Spring 配置类 (15 个)，定义安全、数据库、缓存、AI、WebSocket 等基础设施。

**Key Files**:
- `SecurityConfig.java` — Spring Security 配置 (JWT filter chain, CORS, password encoder)
- `SecurityHeadersConfig.java` — 安全响应头 (CSP, HSTS, XSS protection)
- `RedisConfig.java` — Redis 序列化和缓存配置
- `MyBatisPlusConfig.java` — MyBatis-Plus 分页插件和自动填充
- `CorsConfig.java` — CORS 配置
- `WebSocketConfig.java` — WebSocket 端点注册
- `LangChain4jConfig.java` — AI 模型配置 (Qianwen)
- `Knife4jConfig.java` — API 文档配置

**For AI Agents**:
- 使用 `@Configuration` + `@Bean`
- Profile 条件: `@Profile("dev")` / `@Profile("prod")`
- 安全配置修改: 编辑 SecurityConfig.securityFilterChain()
- 新增配置: 创建 `@Configuration` 类，放在 config/ 目录
