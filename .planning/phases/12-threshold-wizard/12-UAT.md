---
status: testing
phase: 12-threshold-wizard
source:
  - 12-01-SUMMARY.md
  - 12-02-SUMMARY.md
  - 12-03-SUMMARY.md
  - 12-04-SUMMARY.md
  - 12-05-SUMMARY.md
  - 12-06-SUMMARY.md
  - 12-07-SUMMARY.md
  - 12-08-SUMMARY.md
started: 2026-11-08T12:10:00Z
updated: 2026-11-08T12:10:00Z
---

## Current Test

number: 1
name: 向导新增走查（分组选择器 / 预填推荐 / 四区条 / 确认语句 / 保存）
expected: |
  1. 打开健康模块的「阈值设置」页面，点击新增/添加阈值入口，三步向导弹出（760px、居中）；
  2. 第 1 步指标选择器按「健康指标 / 保健指标」分组，健康组恰好 6 项（心/血压、血糖、体温、BMI），选项文案为「显示名 (单位)」如「血糖 (mmol/L)」，没有血氧、没有体重、没有裸英文 key；
  3. 选定「血糖」后自动进入/可下一步，第 2 步四项阈值已预填推荐值（血糖 3.9/7.0/3.0/11.1），每项带灰色「推荐」徽标（非标签样式）；
  4. 四项各有一句通俗中文说明并带单位插值（如「高于此值发出黄色提醒（mmol/L）」）；
  5. 修改任一值时，下方五段四区条实时重绘，未填边界显示「—」，当前值位置标在对应区段；
  6. 第 3 步展示一句自然语言确认语句（只包含已填边界，数字加粗），保存成功后向导关闭、列表出现新行。
awaiting: user response

## Tests

### 1. 向导新增走查（分组选择器 / 预填推荐 / 四区条 / 确认语句 / 保存）
expected: |
  1. 打开阈值设置页，点击新增入口，三步向导弹出（760px、居中）；
  2. 第 1 步按「健康指标/保健指标」分组，健康组恰 6 项，文案「显示名 (单位)」，无血氧/体重/裸英文 key；
  3. 选定血糖后第 2 步四项预填推荐值（3.9/7.0/3.0/11.1），每项带「推荐」徽标；
  4. 四项说明含单位插值；
  5. 改值时五段四区条实时重绘，未填边界显示「—」；
  6. 第 3 步确认语句只含已填边界、数字加粗；保存成功关闭向导、列表新增行。
result: pending

### 2. 内联校验与未保存守卫（零 toast / 不清空 / 双主题 / 窄屏）
expected: |
  1. 第 2 步把「警告低值」改得比「警告高值」大：主按钮（下一步/保存）被禁用，两个字段各自出现解释性中文内联错误与顶部汇总条，全程无红色弹窗、无 toast，已输入数值不被清空；
  2. 改回合法值后错误立即消失、按钮恢复；
  3. 修改数值后不保存，按 ESC 或点遮罩关闭：出现「内容尚未保存，确定离开？」确认框，选「继续编辑」留在向导且输入原样保留，选「放弃设置」才关闭；未改动时关闭不弹确认；
  4. 切换深色主题重走一遍：四区条色带与错误色对比度正常；
  5. 窗口缩窄到手机宽度（<640px）：四区条换行为 3+2 排列、页面无横向滚动条。
result: pending

### 3. 重复指标恢复动作（1 条 toast + 打开编辑）
expected: |
  1. 对已存在阈值的指标再次走向导保存：页面恰好出现 1 条 toast「该指标已有阈值，是否改为编辑？」，无重复弹窗、无 500 报错；
  2. 点击「打开编辑」重新打开向导并直接落在第 2 步，指标类型已锁定不可改，表单预填该指标现有阈值。
result: pending

### 4. 别名写入落库端到端（FIX-02/FIX-03）
expected: |
  1. 通过 API（或任何非向导入口）以别名 key `glucose` 保存一条阈值，返回成功而非 500；
  2. 查库确认该行 `metric_key` 落库为标准 key `bloodGlucose`；
  3. 之后用标准 key `bloodGlucose` 查询/评估阈值能命中该行；
  4. 再次以别名 `glucose` 保存同一指标：返回业务错误「该指标已有阈值，是否改为编辑？」而非唯一索引 500。
result: pending

### 5. SQL 修复脚本幂等双跑（FIX-04）
expected: |
  1. 执行前记录：`SELECT user_id, metric_key, COUNT(*) FROM user_threshold GROUP BY 1,2 HAVING COUNT(*)>1`（重复组）与两张表总行数；
  2. 执行 `hhs-backend/src/main/resources/sql/fix-threshold-metric-key.sql` 一次：事务完成，AFTER 计数中非标准 key 计数为 0，别名行合并为保留最新 updated_at 的一行；
  3. 再执行第二次：总行数与第一次执行后完全一致（幂等），无报错；
  4. `alert_rule.metric_key` 全部为标准 key；无重复 `(user_id, metric_key)` 组合残留。
result: pending

### 6. 修复后 WARNING 真实推送观察（FIX-01 副作用）
expected: |
  1. 用一个配置了阈值的账号产生一次落在警告区间的测量（如血糖略超警告高值）；
  2. WebSocket 实时提醒收到 1 条；若该账号配置了 Email，邮件也收到 1 条；
  3. 1 小时内不重复推送同一条告警；记录实际观查结果（本阶段未加限流，D-21）。
result: pending

### 7. 教学型空状态（最后执行 — 会清空数据）
expected: |
  1. 删除当前账号全部 user_threshold 行后刷新阈值页；
  2. 列表呈现教学型空状态：说明当前使用系统默认规则、展示 6 项默认阈值内容表格、提供「使用推荐阈值」入口；
  3. 点击「使用推荐阈值」可一键带入推荐值完成配置；该账号其余功能（评分/评估/录入）不受影响。
result: pending

## Automated Coverage (pre-verified — 不再逐条人工重复)

以下交付物已由全量自动化测试钉死（`mvn verify` 778/0 + `test:run` 263/263 全绿），
依据各 SUMMARY 的 coverage 块分类记录为 pass / source: automated：

| Plan | 已钉死交付物（对应测试） |
|------|--------------------------|
| 12-01 | classify() 四档判定与 D-18 全边界矩阵、互补性不变量（ThresholdRangeCheckerTest 30 用例） |
| 12-04 | @ValidThresholdRange 一次违规两条 violation、字段名不含点；alertable=6 项集合相等；推荐值域与 hardMin/hardMax 约束（ThresholdRangeValidatorTest 7、MetricMetadataControllerTest、RegistryTest） |
| 12-06 | 五段渲染、null 显示 —、实时更新、aria-label 完整、token 数量（ThresholdZoneBar.spec 9 用例；仅 D10 窄屏换行与 D11 无横向溢出留人工 → 并入测试 2） |
| 12-02/03/05/07/08 | 写入规范化、别名 IN、SQL 脚本静态断言、前端契约、向导 13 用例、页面 6 用例等均由对应自动化套件覆盖；人工面经下方 Tests 1-7 |

## Summary

total: 7
passed: 0
issues: 0
pending: 7
skipped: 0
blocked: 0

## Gaps

<!-- None yet -->
