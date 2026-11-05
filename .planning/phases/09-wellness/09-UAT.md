---
status: testing
phase: 09-wellness
source: [09-VERIFICATION.md]
started: 2026-11-05T14:10:00Z
updated: 2026-11-05T14:10:00Z
---

## Current Test

number: 1
name: 预防保健指标管理页（prevention/Metrics.vue）弹窗布局与心情星级交互测试
expected: |
  1. 弹窗全宽平铺居中覆盖视口（append-to-body 脱离动画包含块），表单项采用顶置标签排版；
  2. 切换指标类型至「心情」时呈现 1-5 星级打分（el-rate）并显示中文评级（很差/较差/一般/较好/很好）；
  3. 切换至「饮水量」时显示数值输入框、推荐步长（50）与参考范围占位符，底部常态化展示健康参考标准解读卡片（ReferenceGuideCard）。
awaiting: user response

## Tests

### 1. 预防保健指标管理页（prevention/Metrics.vue）弹窗布局与心情星级交互测试
expected: |
  1. 弹窗全宽平铺居中覆盖视口（append-to-body 脱离动画包含块），表单项采用顶置标签排版；
  2. 切换指标类型至「心情」时呈现 1-5 星级打分（el-rate）并显示中文评级（很差/较差/一般/较好/很好）；
  3. 切换至「饮水量」时显示数值输入框、推荐步长（50）与参考范围占位符，底部常态化展示健康参考标准解读卡片（ReferenceGuideCard）。
result: [pending]

### 2. 生活方式仪表盘（wellness/Dashboard.vue）危急值二次确认与数据联动测试
expected: |
  1. 输入极度异常数值（如饮水量 6000ml 触碰 Panic Limits），失焦后原值 100% 忠实保留；
  2. 点击提交时阻断直接入库，触发 CriticalConfirmDialog 模态窗展示严重危急值风险与急救建议；
  3. 点击确认录入后顺利入库，弹窗关闭，自动刷新活动环与统计数据。
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
