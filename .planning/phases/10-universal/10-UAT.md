---
status: complete
phase: 10-universal
source: [10-VERIFICATION.md]
started: 2026-11-05T19:20:00Z
updated: 2026-11-05T19:28:00Z
---

## Current Test

number: 2
name: 四梯度生理校验、非破坏性输入保留与急危重症二次确认模态窗验收
expected: |
  1. 输入偏高或偏低数值（如心率 110 次/分），失焦后输入框原值绝不被清空，展示黄色「指标偏高」状态胶囊与解释性文案，保存按钮保持可用直接保存；
  2. 输入急危重症数值（如收缩压 190 mmHg 或单日饮水 6000ml），点击保存触发 CriticalConfirmDialog 模态窗展示警示与急救建议，确认后成功入库并刷新列表；
  3. 输入超出人体存活极限数值（如心率 450 次/分），保存按钮被置灰拦截，但 450 数值在输入框中忠实保留，不发生任何破坏性重置清空。
result: pass

## Tests

### 1. 全站四大录入与编辑入口（UniversalMetricDialog）统一视效与交互一致性验收
expected: |
  1. 在浏览器中打开任意指标录入入口（快捷录入 /#/data-input/quick、健康指标管理 /#/health/metrics、预防保健 /#/prevention/metrics 或生活方式仪表盘 /#/wellness/dashboard）；
  2. 点击「添加」或录入按钮，弹窗（append-to-body）脱离父级动画层叠上下文，以 560px 宽度居中平铺于视口；
  3. 表单标签置顶（label-position="top"），参考范围占位符（如“参考范围: 3.9 ~ 6.1 mmol/L”）完整展示无文字截断；
  4. 新增时选择“血压”展现高低压并列网格与倒置对调按钮；选择“心情”展现 1-5 星级打分；下方常态化展示 ReferenceGuideCard 解读卡片；
  5. 在健康指标或预防保健列表页点击任意行的「编辑」操作，弹窗标题精准展示为“编辑[指标名]”，指标类型选择框置灰禁用，关闭血压协同联动，单项数值与记录信息精准回填。
result: pass

### 2. 四梯度生理校验、非破坏性输入保留与急危重症二次确认模态窗验收
expected: |
  1. 输入偏高或偏低数值（如心率 110 次/分），失焦后输入框原值绝不被清空，展示黄色「指标偏高」状态胶囊与解释性文案，保存按钮保持可用直接保存；
  2. 输入急危重症数值（如收缩压 190 mmHg 或单日饮水 6000ml），点击保存触发 CriticalConfirmDialog 模态窗展示警示与急救建议，确认后成功入库并刷新列表；
  3. 输入超出人体存活极限数值（如心率 450 次/分），保存按钮被置灰拦截，但 450 数值在输入框中忠实保留，不发生任何破坏性重置清空。
result: pass

## Summary

total: 2
passed: 2
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

None. All Phase 10 UAT tests passed.
