# 数据库设计

> 本文档详细描述 HHS 数据库的表结构、关系设计和索引策略。

## 数据库信息

| 属性 | 值 |
|------|-----|
| 数据库 | MySQL 8.0+ |
| 字符集 | utf8mb4 |
| 排序规则 | utf8mb4_unicode_ci |
| 版本 | 3.5.0 |

## 实体关系图

```
┌─────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  sys_user   │────<│  health_profile │     │ examination_    │
│             │     │                 │     │    report       │
│ - id (PK)   │     │ - user_id (FK)  │     │ - user_id (FK)  │
│ - username  │     │ - gender        │     │ - report_name   │
│ - password  │     │ - birth_date    │     │ - report_date   │
│ - nickname  │     │ - height_cm     │     │ - ocr_status    │
│ - email     │     │ - weight_kg     │     └─────────────────┘
│ - phone     │     │ - bmi           │              │
│ - status    │     │ - blood_type    │              │
└─────────────┘     └─────────────────┘     ┌─────────────────┐
       │                                     │   lab_result    │
       │                                     │ - report_id(FK) │
       │                                     │ - name          │
       │                                     │ - value         │
       │                                     │ - is_abnormal   │
       │                                     └─────────────────┘
       │
       ├─────────────────────────────────────────────────────────┐
       │              │                    │                     │
       ▼              ▼                    ▼                     ▼
┌─────────────┐ ┌─────────────┐   ┌─────────────┐      ┌─────────────┐
│health_metric│ │realtime_    │   │health_alert │      │ai_          │
│             │ │   metric    │   │             │      │conversation│
│- user_id(FK)│ │- user_id    │   │- user_id(FK)│      │- user_id(FK)│
│- metric_key │ │- metric_key │   │- alert_type │      │- session_id │
│- value      │ │- value      │   │- alert_level│      │- question   │
│- record_date│ │- created_at │   │- title      │      │- answer     │
│- category   │ │(分区表)      │   │- message    │      │- tokens_used│
└─────────────┘ └─────────────┘   └─────────────┘      └─────────────┘
       │                                   
       │              ┌─────────────────────────────────────────┐
       │              │           预警子系统                     │
       │              ├─────────────────────────────────────────┤
       │              │ ┌───────────┐ ┌───────────┐ ┌─────────┐ │
       │              │ │alert_rule │ │user_      │ │alert_   │ │
       │              │ │           │ │threshold  │ │template │ │
       │              │ │-metric_key│ │-user_id   │ │-template│ │
       │              │ │-warning_hi│ │-metric_key│ │  _key   │ │
       │              │ │-critical_ │ │-warning_  │ │-severity│ │
       │              │ │  high     │ │  high     │ │  _level │ │
       │              │ └───────────┘ └───────────┘ └─────────┘ │
       │              └─────────────────────────────────────────┘
       │
       │              ┌─────────────────────────────────────────┐
       │              │           设备同步子系统                  │
       │              ├─────────────────────────────────────────┤
       │              │ ┌─────────────────┐ ┌─────────────────┐ │
       │              │ │device_connection│ │  sync_history   │ │
       │              │ │- user_id (FK)   │ │- user_id (FK)   │ │
       │              │ │- platform       │ │- platform       │ │
       │              │ │- access_token   │ │- sync_type      │ │
       │              │ │- refresh_token  │ │- status         │ │
       │              │ │- last_sync_at   │ │- metrics_count  │ │
       │              │ └─────────────────┘ └─────────────────┘ │
       │              └─────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────────┐
│                    device_platform_config                        │
│ - platform (PK)  │ - client_id  │ - auth_url  │ - enabled       │
│ - client_secret  │ - token_url  │ - scopes    │ - configured    │
└─────────────────────────────────────────────────────────────────┘
```

## 核心表结构

### 1. 用户管理

#### sys_user（用户表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| username | VARCHAR(50) | 用户名，唯一 |
| password | VARCHAR(255) | 密码（BCrypt 加密） |
| nickname | VARCHAR(50) | 昵称 |
| avatar | VARCHAR(500) | 头像 URL |
| email | VARCHAR(100) | 邮箱，唯一 |
| phone | VARCHAR(20) | 手机号，唯一 |
| status | TINYINT | 状态：0-禁用，1-正常 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**索引**：
- `uk_username`：用户名唯一索引
- `uk_email`：邮箱唯一索引
- `uk_phone`：手机号唯一索引
- `idx_status`：状态索引

#### delete_log（删除日志表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| operator_id | BIGINT | 操作人 ID |
| target_type | VARCHAR(32) | 目标类型 |
| target_id | BIGINT | 目标 ID |
| delete_time | DATETIME | 删除时间 |

### 2. 健康档案

#### health_profile（健康档案表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID（外键） |
| gender | VARCHAR(16) | 性别 |
| birth_date | DATE | 出生日期 |
| height_cm | DECIMAL(5,2) | 身高（cm） |
| weight_kg | DECIMAL(5,2) | 体重（kg） |
| bmi | DECIMAL(4,2) | BMI |
| blood_type | VARCHAR(16) | 血型 |
| allergy_history | VARCHAR(512) | 过敏史 |
| family_history | VARCHAR(512) | 家族病史 |
| lifestyle_habits | VARCHAR(512) | 生活习惯 |

### 3. 健康指标

#### health_metric（健康指标历史表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID（外键） |
| profile_id | BIGINT | 档案 ID |
| metric_key | VARCHAR(32) | 指标类型 |
| value | DECIMAL(10,2) | 值 |
| unit | VARCHAR(32) | 单位 |
| record_date | DATE | 记录日期 |
| record_time | DATETIME | 精确时间戳（冲突解决） |
| trend | VARCHAR(16) | 趋势：NORMAL/HIGH/LOW |
| category | ENUM | 分类：HEALTH/WELLNESS |

**索引**：
- `idx_user_metric_date`：用户+指标+日期组合索引
- `idx_user_category_date`：用户+分类+日期索引
- `idx_user_metric_time`：用户+指标+时间索引

#### realtime_metric（实时指标表 - 分区表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（与 created_at 组合） |
| user_id | BIGINT | 用户 ID |
| metric_key | VARCHAR(50) | 指标类型 |
| value | DECIMAL(10,2) | 值 |
| unit | VARCHAR(20) | 单位 |
| source | VARCHAR(50) | 数据来源 |
| quality_score | DECIMAL(3,2) | 数据质量评分 |
| created_at | DATETIME(3) | 创建时间（毫秒精度） |

**分区策略**：按月范围分区（p202602, p202603, ...）

> 注意：分区表不支持外键约束，数据一致性由应用层保证。

### 4. 预警系统

#### health_alert（健康预警表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID（外键） |
| alert_type | VARCHAR(50) | 预警类型 |
| alert_level | VARCHAR(20) | 预警级别 |
| title | VARCHAR(200) | 标题 |
| message | TEXT | 消息内容 |
| metric_key | VARCHAR(50) | 关联指标 |
| current_value | DECIMAL(10,2) | 当前值 |
| threshold_value | DECIMAL(10,2) | 阈值 |
| occurrence_count | INT | 发生次数 |
| ai_analysis | TEXT | AI 分析结果 |
| suggestion | TEXT | 健康建议 |
| is_read | BOOLEAN | 是否已读 |
| is_acknowledged | BOOLEAN | 是否已确认 |

**预警类型**：
- CRITICAL：严重预警
- WARNING：警告
- INFO：提示
- TREND：趋势预警
- RECOVERY：恢复通知

#### alert_rule（预警规则表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| metric_key | VARCHAR(50) | 指标类型（唯一） |
| warning_high | DECIMAL(10,2) | 警告上限 |
| critical_high | DECIMAL(10,2) | 严重上限 |
| warning_low | DECIMAL(10,2) | 警告下限 |
| critical_low | DECIMAL(10,2) | 严重下限 |
| enabled | BOOLEAN | 是否启用 |

#### user_threshold（用户个性化阈值表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID（外键） |
| metric_key | VARCHAR(50) | 指标类型 |
| warning_high | DECIMAL(10,2) | 警告上限 |
| critical_high | DECIMAL(10,2) | 严重上限 |
| warning_low | DECIMAL(10,2) | 警告下限 |
| critical_low | DECIMAL(10,2) | 严重下限 |

**唯一约束**：`uk_user_metric` (user_id, metric_key)

#### alert_template（预警模板表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| template_key | VARCHAR(100) | 模板唯一键 |
| metric_key | VARCHAR(50) | 指标类型 |
| severity_level | VARCHAR(20) | 严重级别 |
| condition_expr | VARCHAR(200) | 触发条件表达式 |
| title_template | VARCHAR(200) | 标题模板 |
| message_template | TEXT | 消息模板 |
| suggestion_template | TEXT | 建议模板 |
| priority | INT | 优先级 |

### 5. 设备同步

#### device_connection（设备连接表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID（外键） |
| platform | VARCHAR(20) | 平台：huawei/xiaomi/wechat/apple |
| platform_user_id | VARCHAR(100) | 平台用户标识 |
| access_token | VARCHAR(500) | Access Token（加密） |
| refresh_token | VARCHAR(500) | Refresh Token（加密） |
| token_expire_at | DATETIME | Token 过期时间 |
| last_sync_at | DATETIME | 最后同步时间 |
| sync_enabled | TINYINT | 是否启用自动同步 |
| status | VARCHAR(20) | 状态：connected/expired/disconnected |

**唯一约束**：`uk_user_platform` (user_id, platform)

#### device_platform_config（设备平台配置表）

| 字段 | 类型 | 说明 |
|------|------|------|
| platform | VARCHAR(20) | 主键，平台标识 |
| client_id | VARCHAR(500) | OAuth Client ID（加密） |
| client_secret | VARCHAR(500) | OAuth Client Secret（加密） |
| auth_url | VARCHAR(500) | 授权 URL |
| token_url | VARCHAR(500) | Token URL |
| redirect_uri | VARCHAR(500) | 回调 URL |
| scopes | JSON | 权限范围 |
| configured | TINYINT | 是否已配置 |
| enabled | TINYINT | 是否启用 |

#### sync_history（同步历史表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID |
| platform | VARCHAR(20) | 平台 |
| sync_type | VARCHAR(20) | 同步类型：manual/scheduled |
| metrics_count | INT | 同步指标数量 |
| status | VARCHAR(20) | 状态：success/partial/failed |
| error_message | TEXT | 错误信息 |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| duration_ms | INT | 耗时（毫秒） |

### 6. AI 对话

#### ai_conversation（AI 对话记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID（外键） |
| session_id | VARCHAR(100) | 会话 ID |
| question | TEXT | 用户问题 |
| answer | TEXT | AI 回答 |
| tokens_used | INT | Token 使用量 |
| model_version | VARCHAR(50) | 模型版本 |
| created_at | DATETIME | 创建时间 |

#### ai_parse_history（AI 解析历史表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID |
| input_text | TEXT | 输入文本 |
| parse_result | JSON | 解析结果 |
| confirmed | TINYINT | 是否确认保存 |
| metric_ids | JSON | 保存的指标 ID 列表 |

### 7. 检查报告

#### examination_report（检查报告表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID（外键） |
| report_name | VARCHAR(128) | 报告名称 |
| report_type | VARCHAR(32) | 报告类型 |
| institution | VARCHAR(128) | 机构名称 |
| report_date | DATE | 报告日期 |
| file_url | VARCHAR(512) | 文件 URL |
| ocr_status | VARCHAR(32) | OCR 状态 |
| abnormal_summary | TEXT | 异常指标摘要 |
| structured_data | JSON | 结构化数据 |

#### lab_result（检验结果表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| report_id | BIGINT | 报告 ID（外键） |
| name | VARCHAR(64) | 指标名称 |
| category | VARCHAR(64) | 分类 |
| value | VARCHAR(64) | 检验值 |
| unit | VARCHAR(32) | 单位 |
| reference_range | VARCHAR(128) | 参考范围 |
| is_abnormal | TINYINT | 是否异常 |

### 8. 推送配置

#### user_push_config（用户推送配置表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID（外键） |
| channel_type | VARCHAR(20) | 渠道类型：EMAIL/WECOM/FEISHU/WEBSOCKET |
| config_key | VARCHAR(100) | 配置键 |
| config_value | VARCHAR(500) | 配置值 |
| enabled | TINYINT | 是否启用 |

#### push_history（推送历史表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID |
| alert_id | BIGINT | 关联预警 ID |
| channel_type | VARCHAR(20) | 渠道类型 |
| status | VARCHAR(20) | 状态：SUCCESS/FAILED/SKIPPED |
| message | VARCHAR(500) | 结果消息 |
| pushed_at | DATETIME | 推送时间 |

## 视图

### v_user_health_overview

用户健康概览视图，聚合用户基本信息和健康统计数据。

### v_latest_metrics_summary

最新指标汇总视图，每个用户每种指标的最新值。

### v_unread_alerts_summary

未读预警汇总视图，统计用户的未读预警数量。

## 存储过程

### cleanup_old_realtime_metrics

清理过期的实时指标数据：

```sql
CALL cleanup_old_realtime_metrics(3); -- 保留 3 个月数据
```

### cleanup_expired_score_cache

清理过期的健康评分缓存。

## 索引策略

### 组合索引

1. `idx_user_metric_date`：用户 + 指标 + 日期（支持按用户查询特定指标的历史记录）
2. `idx_user_latest`：用户 + 指标 + ID（支持快速获取最新指标）
3. `idx_user_read`：用户 + 是否已读（支持预警未读列表查询）

### 分区表

`realtime_metric` 表按月分区，优势：

- 查询性能：时间范围查询只扫描相关分区
- 维护便利：可以轻松删除旧分区
- 存储优化：历史数据可以归档

## 数据迁移

Schema 版本通过 `Change Log` 注释追踪：

-- v3.6.0 (2026-03-24): Fix avatar URLs stored with localhost prefix

- v3.4.0 (2026-03-19): 添加 record_time 字段
- v3.3.0 (2026-03-05): 添加 push_history 表
- v3.2.0 (2026-03-04): 添加预警优化相关表

## 数据迁移与手工修复

### 阈值指标键归一化脚本（fix-threshold-metric-key.sql）

文件：`hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql`（Phase 12-threshold-wizard / FIX-04）

| 属性 | 说明 |
|------|------|
| 用途 | 将 `user_threshold` / `alert_rule` / `alert_template` / `health_alert` 四表中历史遗留的非标准 `metric_key`（`glucose`、`temperature`、`spo2` 等全部别名）归一化为标准 key（`bloodGlucose` / `bodyTemperature` / `bloodOxygen`），并在改名前按「同一用户同一指标族保留 `updated_at` 最新一行」合并重复行 |
| 幂等性 | **幂等且事务性**：可重复执行，第二次执行零行变更（AFTER 计数与第一次执行后完全一致）；整个变更包在单个事务中，任一语句失败整体回滚，不会留下半归一化状态 |
| 执行方式 | **必须手工执行**（`mysql ... < fix-threshold-metric-key.sql`），不随应用启动自动运行，也不由任何迁移框架托管 |
| 回滚边界 | 单向操作，无自动反向映射，被合并删除的行无法恢复原 id；执行前先记录脚本打印的 BEFORE 计数，并先在 UAT 环境完成幂等性验证（见 12-VALIDATION §Manual-Only Verifications）后再对生产库执行 |

**执行顺序（强制）：先发布代码，后执行脚本——严禁反向。**

1. 先部署包含 `MetricKeyNormalizer` 别名 IN 查询与写入规范化的后端代码（Phase 12 plan 02，FIX-02/FIX-03）。
2. 再手工执行 `fix-threshold-metric-key.sql`。

旧代码按字面精确匹配 `metric_key`：若先跑脚本后发代码，用户阈值与告警规则对旧代码"查不到"，告警彻底失效。别名 IN 查询使新代码在脚本执行前后都能命中，因此唯一安全的顺序是「代码发布 → 脚本执行」，绝不允许先执行脚本。

**新库豁免：** 由 `schema.sql` 全新初始化的数据库，`alert_rule` / `alert_template` 种子已直接使用标准 key（`bloodGlucose` / `bodyTemperature`），**无需执行本脚本**。该脚本仅针对在标准 key 种子落地之前创建的存量数据库。