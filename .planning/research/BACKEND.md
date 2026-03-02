# Research: Wellness Metric 后端设计

## 现有 Health Metric 结构参考

### Entity (HealthMetric.java)
```java
@Data
@TableName("health_metric")
public class HealthMetric {
    private Long id;
    private Long userId;
    private Long profileId;
    private String metricKey;  // 指标类型：blood_glucose, blood_pressure_systolic, blood_pressure_diastolic, heart_rate
    private BigDecimal value;
    private String unit;
    private LocalDate recordDate;
    private String trend;
    private LocalDateTime createTime;
}
```

### Controller Pattern (/api/metrics)
- GET /api/metrics - 分页列表
- POST /api/metrics - 创建
- PUT /api/metrics/{id} - 更新
- DELETE /api/metrics/{id} - 删除

## Wellness Metric 建议设计

### 保健指标类型（建议）
- sleep_hours: 睡眠时长（小时）
- sleep_quality: 睡眠质量（1-5）
- steps: 步数
- water_intake: 饮水量（ml）
- exercise_minutes: 运动时长（分钟）
- weight: 体重（kg）
- mood: 心情（1-5）

### 建议的 API 端点
- GET /api/wellness - 分页列表
- POST /api/wellness - 创建
- PUT /api/wellness/{id} - 更新
- DELETE /api/wellness/{id} - 删除

### 复用现有模式
- 使用 MyBatis-Plus
- 复用 HealthMetricService 模式
- 复用相同的分页/排序逻辑
