<!-- Parent: ../../../../../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# AGENTS.md — Backend Test Root

**Purpose**: 后端测试代码根目录，包含单元测试、集成测试、安全测试、性能测试。

## Subdirectories

- `service/` — 业务服务单元测试 (JUnit 5 + Mockito)
  - `alert/` — 告警服务测试
  - `domain/` — 领域服务测试
  - `impl/` — 服务实现测试
- `integration/` — 集成测试 (Testcontainers MySQL)，基类 AbstractIntegrationTest
- `security/` — 安全测试 (CORS 配置、路径遍历防护)
- `controller/` — 控制器测试 (MockMvc)
- `domain/handler/` — 事件处理器测试
- `component/` — 组件测试
- `config/` — 配置类测试
- `performance/` — 性能测试
- `util/` — 测试工具类
- `utils/` — 测试工具类 (另一组)
- `validation/` — 校验测试
- `resources/` — 测试资源文件

## For AI Agents

- **单元测试**: `@ExtendWith(MockitoExtension.class)`，mock 依赖，测试单个类
- **集成测试**: 继承 `AbstractIntegrationTest`，使用 Testcontainers 启动真实 MySQL
- **Surefire 排除**: `*IntegrationTest.java`, `*PerformanceTest.java`
- **Failsafe 包含**: `*IntegrationTest.java`
- **测试命名**: `FooServiceTest`, `FooControllerTest`, `*IntegrationTest`
- **覆盖率门槛**: 30% line / 25% branch (JaCoCo)
- **运行**: `mvn test` (单元) / `mvn verify -DskipTests failsafe:integration-test` (集成)
