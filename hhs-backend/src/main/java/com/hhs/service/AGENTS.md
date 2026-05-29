<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-29 | Updated: 2026-05-29 -->

# service/

**Purpose**: 业务逻辑层，定义服务接口。实现类在 `impl/` 子目录。

**Subdirectories**:
- `impl/` — 服务实现类
- `domain/` — 无状态领域逻辑 (17 个类: ScoreCalculator, DimensionScoreCalculator, RiskScorer, MetricValidator, ThresholdEvaluator 等)
- `alert/` — 智能告警子系统 (9 个类: AlertDeduplicator, TrendPredictor, AlertFrequencyStrategy, AiAlertAnalyzer, RecoveryNotifier 等)
- `push/` — 推送服务，含 `channel/` 子目录 (WebSocket, Email, Feishu, WeCom 实现)

**For AI Agents**:
- 接口定义在 service/，实现在 service/impl/
- 领域服务 (domain/) 无 Spring 注解，纯 Java 类
- 事务: @Transactional 在 ServiceImpl 上
- 事件发布: 注入 ApplicationEventPublisher
