# 实时监控模块

> 本文档描述实时健康指标监控模块的功能和实现。

## 模块概述

实时监控模块提供健康指标的实时录入、展示和趋势分析。

## 功能特性

- 实时指标录入
- 指标趋势图表
- 多指标对比
- 预警即时触发
- 数据去重

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端视图层                               │
│                   views/realtime/Monitor.vue                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         API 层                                   │
│                    api/realtime.ts                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       后端控制层                                 │
│                    RealtimeController.java                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       服务层                                     │
│  MetricDeduplicationService │ AlertRuleEngine │ AlertService    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       数据层                                     │
│            realtime_metric (分区表)                              │
└─────────────────────────────────────────────────────────────────┘
```

## 数据库设计

### realtime_metric 表

实时指标表采用按月分区，优化查询性能：

```sql
CREATE TABLE `realtime_metric` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `metric_key` VARCHAR(50) NOT NULL,
    `value` DECIMAL(10,2) NOT NULL,
    `unit` VARCHAR(20) DEFAULT NULL,
    `source` VARCHAR(50) DEFAULT 'manual',
    `quality_score` DECIMAL(3,2) DEFAULT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`, `created_at`),
    KEY `idx_user_metric_time` (`user_id`, `metric_key`, `created_at`),
    KEY `idx_user_latest` (`user_id`, `metric_key`, `id`)
) PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202602 VALUES LESS THAN (202603),
    PARTITION p202603 VALUES LESS THAN (202604),
    -- ...
);
```

### 分区策略优势

1. **查询性能**：时间范围查询只扫描相关分区
2. **维护便利**：可以轻松删除旧分区
3. **存储优化**：历史数据可以归档到冷存储

## API 接口

### 指标录入

```
POST /api/realtime/metrics
```

请求体：

```json
{
  "metricKey": "heartRate",
  "value": 75,
  "unit": "bpm",
  "source": "manual",
  "qualityScore": 1.0
}
```

响应：

```json
{
  "code": 200,
  "data": {
    "id": 1,
    "metricKey": "heartRate",
    "metricDisplayName": "心率",
    "value": 75,
    "unit": "bpm",
    "source": "manual",
    "createdAt": "2026-03-25T10:30:00.123"
  }
}
```

### 获取最新指标

```
GET /api/realtime/metrics
```

响应：用户每种指标的最新值列表。

### 获取趋势数据

```
GET /api/realtime/metrics/trend?metricKey=heartRate&hours=24
```

响应：

```json
{
  "code": 200,
  "data": {
    "metricKey": "heartRate",
    "metricDisplayName": "心率",
    "unit": "bpm",
    "dataPoints": [
      { "timestamp": "2026-03-25T10:00:00", "value": 72 },
      { "timestamp": "2026-03-25T11:00:00", "value": 75 },
      { "timestamp": "2026-03-25T12:00:00", "value": 78 }
    ]
  }
}
```

## 指标录入流程

```java
@PostMapping("/metrics")
public Result<RealtimeMetricVO> addMetric(@Valid @RequestBody RealtimeMetricRequest request) {
    Long userId = SecurityUtils.getCurrentUserId();
    
    // 1. 创建指标实体
    RealtimeMetric metric = new RealtimeMetric();
    metric.setUserId(userId);
    metric.setMetricKey(request.getMetricKey());
    metric.setValue(request.getValue());
    // ...
    
    // 2. 去重检查
    if (!deduplicationService.shouldAcceptMetric(metric)) {
        return Result.failure(400, "该指标最近已录入，请稍后再试");
    }
    
    // 3. 保存指标
    realtimeMetricMapper.insert(metric);
    deduplicationService.recordMetric(metric);
    
    // 4. 触发预警评估
    List<AlertVO> alerts = alertRuleEngine.evaluateMetric(metric);
    for (AlertVO alert : alerts) {
        alertService.createAlert(alert);
    }
    
    // 5. 返回结果
    return Result.success(toVO(metric));
}
```

## 数据去重

### 去重策略

同一用户、同一指标在短时间内（5分钟）不重复记录。

```java
@Service
public class MetricDeduplicationService {

    private static final Duration DEDUP_WINDOW = Duration.ofMinutes(5);

    public boolean shouldAcceptMetric(RealtimeMetric metric) {
        String key = String.format("metric:%d:%s", 
            metric.getUserId(), metric.getMetricKey());
        
        Boolean accepted = redisTemplate.opsForValue()
            .setIfAbsent(key, String.valueOf(metric.getValue()), DEDUP_WINDOW);
        
        return Boolean.TRUE.equals(accepted);
    }

    public void recordMetric(RealtimeMetric metric) {
        String key = String.format("metric:%d:%s", 
            metric.getUserId(), metric.getMetricKey());
        redisTemplate.opsForValue().set(key, String.valueOf(metric.getValue()), DEDUP_WINDOW);
    }
}
```

## 预警触发

实时指标录入后自动触发预警评估：

```java
// RealtimeController.java
List<AlertVO> alerts = alertRuleEngine.evaluateMetric(metric);
for (AlertVO alertVO : alerts) {
    HealthAlert alert = new HealthAlert();
    alert.setUserId(userId);
    alert.setAlertType(alertVO.getAlertType());
    alert.setAlertLevel(alertVO.getAlertLevel());
    alert.setTitle(alertVO.getTitle());
    alert.setMessage(alertVO.getMessage());
    alert.setMetricKey(alertVO.getMetricKey());
    alert.setCurrentValue(alertVO.getCurrentValue());
    alert.setThresholdValue(alertVO.getThresholdValue());
    alertService.createAlert(alert);
}
```

## 前端实现

### Monitor.vue

```vue
<template>
  <div class="realtime-monitor">
    <!-- 指标录入表单 -->
    <el-form :model="form" @submit.prevent="submitMetric">
      <el-select v-model="form.metricKey" placeholder="选择指标">
        <el-option label="心率" value="heartRate" />
        <el-option label="收缩压" value="systolicBP" />
        <el-option label="舒张压" value="diastolicBP" />
        <el-option label="血糖" value="glucose" />
      </el-select>
      <el-input v-model.number="form.value" type="number" />
      <el-button type="primary" @click="submitMetric">提交</el-button>
    </el-form>
    
    <!-- 最新指标展示 -->
    <div class="latest-metrics">
      <el-card v-for="metric in latestMetrics" :key="metric.metricKey">
        <h3>{{ metric.metricDisplayName }}</h3>
        <div class="value">{{ metric.value }} {{ metric.unit }}</div>
        <div class="time">{{ formatTime(metric.createdAt) }}</div>
      </el-card>
    </div>
    
    <!-- 趋势图表 -->
    <div class="trend-chart">
      <ECharts :option="chartOption" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { realtimeApi } from '@/api/realtime'

const form = ref({ metricKey: '', value: null })
const latestMetrics = ref([])
const chartOption = ref({})

const submitMetric = async () => {
  await realtimeApi.addMetric(form.value)
  // 刷新数据
  fetchLatestMetrics()
}

const fetchLatestMetrics = async () => {
  const res = await realtimeApi.getLatestMetrics()
  latestMetrics.value = res.data
}

onMounted(() => {
  fetchLatestMetrics()
})
</script>
```

## 指标类型映射

```java
private String getDisplayName(String metricKey) {
    return switch (metricKey) {
        case "heartRate" -> "心率";
        case "systolicBP" -> "收缩压";
        case "diastolicBP" -> "舒张压";
        case "glucose" -> "血糖";
        case "weight" -> "体重";
        case "bmi" -> "BMI";
        case "temperature" -> "体温";
        case "sleepDuration" -> "睡眠时长";
        case "sleepQuality" -> "睡眠质量";
        case "steps" -> "步数";
        case "exerciseMinutes" -> "运动时长";
        default -> metricKey;
    };
}

private String getUnit(String metricKey) {
    return switch (metricKey) {
        case "heartRate" -> "次/分";
        case "systolicBP", "diastolicBP" -> "mmHg";
        case "glucose" -> "mmol/L";
        case "weight" -> "kg";
        case "temperature" -> "°C";
        case "sleepDuration" -> "小时";
        case "steps" -> "步";
        case "exerciseMinutes" -> "分钟";
        default -> "";
    };
}
```

## 性能优化

### 分区维护

定期添加新月份的分区：

```sql
ALTER TABLE realtime_metric ADD PARTITION (
    PARTITION p202701 VALUES LESS THAN (202702)
);
```

### 数据清理

清理过期的实时指标：

```sql
CALL cleanup_old_realtime_metrics(3); -- 保留 3 个月
```

### 索引优化

- `idx_user_metric_time`：用户+指标+时间组合索引
- `idx_user_latest`：快速获取最新指标

## 注意事项

1. **分区表限制**：不支持外键约束，应用层保证数据一致性
2. **时间精度**：使用 `DATETIME(3)` 存储毫秒级精度
3. **去重窗口**：根据业务需求调整去重时间窗口
4. **预警触发**：录入后立即评估预警规则