# 健康指标模块

> 本文档描述健康指标管理模块的功能和实现。

## 模块概述

健康指标模块是 HHS 的核心功能，提供健康数据的记录、查询、分析和可视化。

## 功能特性

- 多类型指标支持（血糖、血压、心率、体重等）
- 指标历史记录与趋势分析
- 健康评分计算
- OCR 快速录入
- AI 智能解析

## 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                         前端视图层                               │
│  Metrics.vue │ Score.vue │ QuickInput.vue │ OcrInput.vue        │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         API 层                                   │
│  health.ts │ score.ts │ ocr.ts │ ai-parse.ts                    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       后端控制层                                 │
│  HealthMetricController │ HealthScoreController │ OcrController │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       服务层                                     │
│  HealthMetricService │ HealthScoreService │ OcrService │         │
│  AiParseService │ MetricDeduplicationService                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       数据层                                     │
│  health_metric │ health_score_cache │ ocr_health_record          │
└─────────────────────────────────────────────────────────────────┘
```

## 指标类型

### 健康指标（HEALTH）

| 指标键 | 名称 | 单位 | 正常范围 |
|--------|------|------|----------|
| heartRate | 心率 | bpm | 60-100 |
| systolicBP | 收缩压 | mmHg | 90-140 |
| diastolicBP | 舒张压 | mmHg | 60-90 |
| glucose | 血糖 | mmol/L | 3.9-6.1 |
| weight | 体重 | kg | - |
| bmi | BMI | - | 18.5-24 |
| temperature | 体温 | °C | 36.0-37.3 |

### 保健指标（WELLNESS）

| 指标键 | 名称 | 单位 |
|--------|------|------|
| sleepDuration | 睡眠时长 | 小时 |
| sleepQuality | 睡眠质量 | 级 |
| steps | 步数 | 步 |
| exerciseMinutes | 运动时长 | 分钟 |
| waterIntake | 饮水量 | 杯 |
| mood | 心情 | 分 |
| energy | 精力 | 分 |

## API 接口

### 健康指标 CRUD

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/metrics` | 获取指标列表（分页） |
| POST | `/api/metrics` | 创建指标记录 |
| PUT | `/api/metrics/{id}` | 更新指标记录 |
| DELETE | `/api/metrics/{id}` | 删除指标记录 |

### 请求示例

```json
// POST /api/metrics
{
  "metricKey": "glucose",
  "value": 5.6,
  "unit": "mmol/L",
  "recordDate": "2026-03-25",
  "category": "HEALTH"
}
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "userId": 1,
    "metricKey": "glucose",
    "value": 5.6,
    "unit": "mmol/L",
    "recordDate": "2026-03-25",
    "trend": "NORMAL",
    "category": "HEALTH",
    "createTime": "2026-03-25T10:30:00"
  }
}
```

## 健康评分

### 评分维度

| 维度 | 权重 | 说明 |
|------|------|------|
| 基础指标 | 30% | BMI、血压、血糖等 |
| 生活习惯 | 25% | 睡眠、运动、饮水等 |
| 健康档案 | 20% | 年龄、性别、病史等 |
| 指标趋势 | 15% | 近期指标变化趋势 |
| 预警记录 | 10% | 预警数量和严重程度 |

### 评分等级

| 分数范围 | 等级 | 说明 |
|----------|------|------|
| 90-100 | EXCELLENT | 优秀 |
| 75-89 | GOOD | 良好 |
| 60-74 | FAIR | 一般 |
| 0-59 | POOR | 较差 |

### 评分缓存

评分结果缓存 1 小时，避免重复计算。

```java
@Cacheable(value = "health-score", key = "#userId")
public HealthScoreVO calculateScore(Long userId) {
    // 评分计算逻辑
}
```

## OCR 数据录入

### 支持类型

- 体检报告
- 化验单
- 药品标签
- 营养成分表

### OCR 流程

```
1. 上传图片
2. 百度 OCR 识别文字
3. AI 解析结构化数据
4. 用户确认保存
5. 写入健康指标表
```

### 百度 OCR 集成

```java
@Service
public class OcrService {
    
    public OcrResult recognize(MultipartFile file) {
        // 调用百度 OCR API
        AipOcr client = new AipOcr(appId, apiKey, secretKey);
        JSONObject result = client.basicGeneral(file.getBytes(), options);
        
        // 解析结果
        return parseOcrResult(result);
    }
}
```

## AI 智能解析

### 功能

- 自然语言输入解析为结构化指标
- 支持多种表达方式
- 智能推断指标类型和单位

### 示例

```
用户输入: "今天早上空腹血糖5.6"
解析结果: 
{
  "metricKey": "glucose",
  "value": 5.6,
  "unit": "mmol/L",
  "recordTime": "morning fasting"
}

用户输入: "血压130/85"
解析结果:
{
  "metricKey": ["systolicBP", "diastolicBP"],
  "values": [130, 85],
  "unit": "mmHg"
}
```

## 数据去重

### 去重策略

同一用户、同一指标、同一时间窗口内不重复记录。

```java
@Service
public class MetricDeduplicationService {
    
    public boolean shouldAcceptMetric(RealtimeMetric metric) {
        String key = String.format("metric:%d:%s", 
            metric.getUserId(), metric.getMetricKey());
        
        // 检查 5 分钟内是否有重复
        return redisTemplate.opsForValue()
            .setIfAbsent(key, "1", Duration.ofMinutes(5));
    }
}
```

## 前端组件

### Metrics.vue

指标列表页面，支持：

- 分页展示
- 按类型筛选
- 按时间范围查询
- 编辑/删除操作

### Score.vue

健康评分页面，展示：

- 综合评分环形图
- 各维度评分
- 改进建议

### QuickInput.vue

快速录入组件：

- 表单输入
- 快捷指标选择
- 批量录入

### OcrInput.vue

OCR 录入组件：

- 图片上传
- 识别结果预览
- 确认保存

## 数据库表

### health_metric

历史指标记录表，按日期查询。

### realtime_metric

实时指标表，分区表，高并发写入优化。

### health_score_cache

评分缓存表，存储计算结果。

### ocr_health_record

OCR 识别记录表。

## 注意事项

1. **数据一致性**：实时指标表无外键约束，应用层保证数据一致性
2. **分区维护**：定期添加新月份分区
3. **缓存失效**：指标更新时需要清除评分缓存
4. **限流保护**：OCR 和 AI 解析有频率限制