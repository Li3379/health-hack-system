# Research: Wellness Metric 前端设计

## 现有健康指标模块参考

### 前端目录结构 (hhs-frontend-v2)
参考现有模块结构：
- views/ - 页面组件
- api/ - API 调用层
- stores/ - Pinia 状态管理
- components/ - 可复用组件

### 建议的 Wellness 页面
1. WellnessDashboard - 保健指标概览
2. WellnessList - 保健数据列表
3. WellnessForm - 添加/编辑表单

### UI 组件建议
- 使用 Element Plus 卡片展示各项指标
- 使用 ECharts 图表展示趋势
- 复用现有的分页组件
- 复用现有的表单验证逻辑

### API 调用层
- 复用现有的 axios 实例配置
- 复用相同的错误处理模式
