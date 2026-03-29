# 预警系统模块

> 本文档描述健康预警系统模块的功能和实现。

## 模块概述

预警系统监控健康指标，当指标超出阈值时自动生成预警通知。

## 功能特性

- 多级预警（严重、警告、提示）
- 个性化阈值配置
- 智能去重与合并
- AI 预警分析
- 多渠道推送
- 预警模板管理

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                         预警触发                                 │
│         指标录入 → AlertRuleEngine.evaluateMetric()              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       规则引擎层                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │   默认规则      │  │  用户阈值       │  │  预警模板       │  │
│  │  alert_rule     │  │  user_threshold │  │  alert_template │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       智能处理层                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │ AlertDeduplicator│ │ AlertAIAnalysis │ │IntelligentAlert │  │
│  │    去重器        │  │    AI 分析      │  │    智能服务     │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       预警存储层                                 │
│                   health_alert (预警记录)                        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       推送层                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  │
│  │   WebSocket     │  │     Email       │  │     企业微信    │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## 预警级别

| 级别 | 英文 | 说明 | 示例 |
|------|------|------|------|
| 严重 | CRITICAL | 需要立即关注 | 心率 > 120 bpm |
| 警告 | WARNING | 需要注意 | 血压 > 140 mmHg |
| 提示 | INFO | 健康建议 | BMI 偏高 |
| 趋势 | TREND | 趋势变化 | 血糖连续上升 |
| 恢复 | RECOVERY | 指标恢复正常 | 血压恢复正常 |

## 预警规则

### 默认规则（alert_rule）

| 指标 | 警告上限 | 严重上限 | 警告下限 | 严重下限 |
|------|----------|----------|----------|----------|
| heartRate | 100 | 120 | 50 | 40 |
| systolicBP | 140 | 160 | 90 | 80 |
| diastolicBP | 90 | 100 | 60 | 50 |
| glucose | 7.0 | 11.1 | 3.9 | 3.0 |
| bmi | 28 | 35 | 18.5 | 16 |
| temperature | 37.5 | 38.5 | 36.0 | 35.5 |

### 用户个性化阈值

用户可以设置自己的阈值，覆盖默认规则：

```sql
INSERT INTO user_threshold (user_id, metric_key, warning_high, critical_high)
VALUES (1, 'heartRate', 90, 110);
```

## 规则引擎

### 评估流程

```java
public List<AlertVO> evaluateMetric(RealtimeMetric metric) {
    List<AlertVO> alerts = new ArrayList<>();
    
    // 1. 获取适用阈值（用户阈值优先，否则使用默认规则）
    UserThreshold threshold = getApplicableThreshold(metric.getUserId(), metric.getMetricKey());
    
    // 2. 检查上限
    if (threshold.getCriticalHigh() != null && 
        metric.getValue().compareTo(threshold.getCriticalHigh()) > 0) {
        alerts.add(createAlert(metric, "CRITICAL", "HIGH", threshold.getCriticalHigh()));
    } else if (threshold.getWarningHigh() != null && 
               metric.getValue().compareTo(threshold.getWarningHigh()) > 0) {
        alerts.add(createAlert(metric, "WARNING", "HIGH", threshold.getWarningHigh()));
    }
    
    // 3. 检查下限
    if (threshold.getCriticalLow() != null && 
        metric.getValue().compareTo(threshold.getCriticalLow()) < 0) {
        alerts.add(createAlert(metric, "CRITICAL", "LOW", threshold.getCriticalLow()));
    } else if (threshold.getWarningLow() != null && 
               metric.getValue().compareTo(threshold.getWarningLow()) < 0) {
        alerts.add(createAlert(metric, "WARNING", "LOW", threshold.getWarningLow()));
    }
    
    // 4. 去重检查
    alerts = alerts.stream()
        .filter(a -> shouldCreateAlert(metric.getUserId(), metric.getMetricKey(), a.getAlertType()))
        .collect(Collectors.toList());
    
    return alerts;
}
```

### 获取适用阈值

```java
public UserThreshold getApplicableThreshold(Long userId, String metricKey) {
    // 优先使用用户个性化阈值
    UserThreshold userThreshold = userThresholdMapper.selectOne(
        new LambdaQueryWrapper<UserThreshold>()
            .eq(UserThreshold::getUserId, userId)
            .eq(UserThreshold::getMetricKey, metricKey)
    );
    
    if (userThreshold != null) {
        return userThreshold;
    }
    
    // 否则使用默认规则
    AlertRule defaultRule = alertRuleMapper.selectOne(
        new LambdaQueryWrapper<AlertRule>()
            .eq(AlertRule::getMetricKey, metricKey)
    );
    
    if (defaultRule != null) {
        return convertToThreshold(defaultRule);
    }
    
    return null;
}
```

## 预警模板

### 模板结构

预警消息使用模板生成，支持变量替换：

```sql
INSERT INTO alert_template (template_key, metric_key, severity_level, 
                            title_template, message_template, suggestion_template) 
VALUES (
  'heartRate.high.critical',
  'heartRate',
  'CRITICAL',
  '心率严重偏高',
  '您的心率达到 {value} bpm，严重超过正常范围(60-100 bpm)。',
  '请立即停止活动并休息。如伴有胸闷、气短、头晕等症状，请立即就医。'
);
```

### 模板变量

| 变量 | 说明 |
|------|------|
| `{value}` | 当前值 |
| `{threshold}` | 阈值 |
| `{unit}` | 单位 |

## 智能去重

### 去重策略

```java
@Service
public class AlertDeduplicator {

    // 同一用户、同一指标、同一类型的预警，30 分钟内只发一次
    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(30);

    public boolean shouldCreateAlert(Long userId, String metricKey, String alertType) {
        String key = String.format("alert:dedup:%d:%s:%s", userId, metricKey, alertType);
        
        Boolean isNew = redisTemplate.opsForValue()
            .setIfAbsent(key, "1", DEDUP_WINDOW);
        
        return Boolean.TRUE.equals(isNew);
    }
}
```

### 预警合并

对于频繁触发的相同类型预警，进行合并：

```sql
-- 更新已有预警的次数和时间
UPDATE health_alert 
SET occurrence_count = occurrence_count + 1,
    last_occurrence_at = NOW()
WHERE user_id = ? AND metric_key = ? AND alert_type = ? AND is_acknowledged = FALSE;
```

## AI 预警分析

```java
@Service
public class AlertAIAnalysisService {

    public String analyzeAlert(HealthAlert alert, HealthProfile profile) {
        String prompt = String.format("""
            用户健康预警：
            - 预警类型：%s
            - 指标：%s
            - 当前值：%s
            - 阈值：%s
            
            用户档案：
            - 年龄：%d
            - 性别：%s
            - 病史：%s
            
            请提供简短的健康分析和建议（100字以内）。
            """,
            alert.getAlertType(),
            alert.getMetricKey(),
            alert.getCurrentValue(),
            alert.getThresholdValue(),
            // ... 用户档案信息
        );
        
        return chatModel.generate(prompt);
    }
}
```

## API 接口

### 预警管理

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/alerts` | 获取预警列表 |
| GET | `/api/alerts/unread-count` | 获取未读数量 |
| PUT | `/api/alerts/{id}/read` | 标记已读 |
| PUT | `/api/alerts/{id}/acknowledge` | 确认预警 |
| PUT | `/api/alerts/read-all` | 全部标记已读 |

### 阈值配置

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/thresholds` | 获取用户阈值 |
| POST | `/api/thresholds` | 设置阈值 |
| DELETE | `/api/thresholds/{id}` | 删除阈值 |

## 前端组件

### Alerts.vue

```vue
<template>
  <div class="alerts-page">
    <!-- 未读预警统计 -->
    <el-row :gutter="20">
      <el-col :span="8">
        <el-statistic title="未读预警" :value="unreadCount" />
      </el-col>
      <el-col :span="8">
        <el-statistic title="严重预警" :value="criticalCount" />
      </el-col>
      <el-col :span="8">
        <el-statistic title="警告" :value="warningCount" />
      </el-col>
    </el-row>
    
    <!-- 预警列表 -->
    <el-table :data="alerts">
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="alertLevel" label="级别">
        <template #default="{ row }">
          <el-tag :type="getLevelType(row.alertLevel)">
            {{ row.alertLevel }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="message" label="内容" />
      <el-table-column prop="createdAt" label="时间" />
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button @click="markRead(row.id)">已读</el-button>
          <el-button @click="acknowledge(row.id)">确认</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
```

### Thresholds.vue

阈值配置页面，用户可以设置个性化预警阈值。

## 数据库表

### health_alert

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID |
| alert_type | VARCHAR(50) | 预警类型 |
| alert_level | VARCHAR(20) | 预警级别 |
| title | VARCHAR(200) | 标题 |
| message | TEXT | 消息内容 |
| metric_key | VARCHAR(50) | 关联指标 |
| current_value | DECIMAL(10,2) | 当前值 |
| threshold_value | DECIMAL(10,2) | 阈值 |
| occurrence_count | INT | 发生次数 |
| ai_analysis | TEXT | AI 分析 |
| suggestion | TEXT | 建议 |
| is_read | BOOLEAN | 是否已读 |
| is_acknowledged | BOOLEAN | 是否已确认 |

## 推送渠道

| 渠道 | 说明 | 配置 |
|------|------|------|
| WebSocket | 实时推送 | 默认启用 |
| Email | 邮件通知 | 需配置 SMTP |
| 企业微信 | 微信推送 | 需配置 Webhook |
| 飞书 | 飞书推送 | 需配置 Webhook |

## 注意事项

1. **阈值优先级**：用户阈值 > 默认规则
2. **去重窗口**：根据业务需求调整去重时间
3. **推送频率**：避免频繁推送骚扰用户
4. **AI 分析**：确保 AI 分析结果准确可靠