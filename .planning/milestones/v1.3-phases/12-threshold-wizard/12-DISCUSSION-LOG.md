# Phase 12: 阈值配置向导化与告警正确性修复 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-07
**Phase:** 12-threshold-wizard
**Areas discussed:** 向导形态, 指标范围, 推荐值来源, 存量修复, 层级校验

---

## 向导形态

| Option | Description | Selected |
|--------|-------------|----------|
| ElSteps 三步向导 | 第1步选指标 + 自动预填推荐值；第2步四项阈值（带解释/单位/预览条）；第3步确认语句 + 保存。与现有 DeviceConfigWizard.vue 一致。新用户友好，老用户改一个数值要过 3 步。 | ✓ |
| 单页增强弹窗 | 保持单弹窗，加宽到 720px。指标选择后展开预填 + 四项带解释 + 预览条 + 确认语句全部在一个滚动页。快，但信息密度高。 | |
| 双路径：一键 + 微调 | 列表页行内「使用推荐阈值」一键创建（新手路径），弹窗只服务「微调」（老手路径）。两条路径，两套代码要同步。 | |

**User's choice:** ElSteps 三步向导（推荐）

**Notes:** 复用 `DeviceConfigWizard.vue` 的既有 `ElSteps` 范式。新增内容量（分组选择器 + 四项解释 + 预览条 + 确认语句）已超出单弹窗舒适容量。

### 追问：编辑已有阈值时向导怎么走

| Option | Description | Selected |
|--------|-------------|----------|
| 跳过第 1 步 | 点「编辑」直接进第 2 步（四项阈值），指标类型锁定不可改。第 3 步确认语句仍显示，可回退到第 2 步改。改一个数值 ≈ 2 击。 | ✓ |
| 编辑也走三步 | 新增和编辑走完全相同的三步。一致，但改一个数值要过 3 步。 | |
| 编辑用精简单页 | 编辑时不用向导，弹一个精简单页（四项 + 预览 + 确认）。需要维护两套 UI。 | |

**User's choice:** 跳过第 1 步（推荐）

---

## 指标范围

| Option | Description | Selected |
|--------|-------------|----------|
| 前端白名单常量 | 在前端用一份显式白名单常量（如 ALERTABLE_METRIC_KEYS）列出可评估指标。改动最小、零后端风险，但白名单会与种子规则漂移。 | |
| 后端 alertable 标记 | 后端注册表为每个指标加 alertable 标志 + 推荐阈值字段，通过 GET /api/metrics/metadata 下发。单一权威源，前端零硬编码。 | ✓ |
| 后端种子规则驱动 | 只列 alert_rule 里四值非全 NULL 的指标（当前 6 个），后端新增只读端点暴露种子规则。白名单天然跟随数据，但改种子要改数据。 | |

**User's choice:** 后端 alertable 标记（推荐）

**Notes:** 避免前后端白名单漂移；符合 REQUIREMENTS.md Out of Scope 中「阈值选择器不暴露全部 23 项指标」的约束。

### 追问：选择器里分组和不可用项怎么处理

| Option | Description | Selected |
|--------|-------------|----------|
| 仅显 alertable | 按元数据 category 分 HEALTH / WELLNESS 两组，但只渲染 alertable 的那些。当前 6-7 个指标都属于 HEALTH，WELLNESS 组会是空组，需隐藏空组。 | ✓ |
| 全列 + 置灰 | HEALTH 全 16 项都列出，非 alertable 的置灰并附原因。用户能看到完整指标世界，但大多数不可点。 | |
| alertable + 常用分组 | 先筛 alertable，再在组内按「常用/其他」二级分组。多一层分组，对 6 个指标是过度设计。 | |

**User's choice:** 仅显 alertable（推荐）

---

## 推荐值来源

| Option | Description | Selected |
|--------|-------------|----------|
| 后端一并下发 | 后端在 alertable 标记旁一并下发推荐四值（由 alert_rule 种子派生，缺失时用 normalMin/Max 推导）。前端直接渲染，前后端不会不一致。 | ✓ |
| 前端纯计算 | 前端用 normalMin/normalMax + PANIC_LIMITS 算。零后端改动，但同一指标前后端可能算出不同推荐值。 | |
| 双源双回退 | 优先用后端下发值，后端缺席时前端回退到派生逻辑。鲁棒但有两套逻辑要同步维护。 | |

**User's choice:** 后端一并下发（推荐）

### 追问：推荐值要不要再分档位

| Option | Description | Selected |
|--------|-------------|----------|
| 单一推荐值 | 只提供「推荐值」一种，来源为系统默认规则。用户可选接受或手改，不出现多档。 | ✓ |
| 加三档预设 | 在推荐值之外多一个「宽松/标准/严格」三档预设（研究文档 THR-09，属 differentiator）。需要为每个指标定义三套值。 | |
| 部分指标才推荐 | 只对少数指标（血压/血糖/心率）给推荐，其余需用户自己填。会重现「不知道怎么填」的原始痛点。 | |

**User's choice:** 单一推荐值（推荐）

### 追问：选定指标后推荐值怎么应用到表单

| Option | Description | Selected |
|--------|-------------|----------|
| 自动预填 + 可恢复 | 选完指标立即把四项填成推荐值并标「推荐」徽标。用户可一键「恢复推荐值」回到初值。 | ✓ |
| 不自动填，给按钮 | 选完指标四项仍为空，下方出现「使用推荐值」按钮，用户主动点才填。 | |
| 自动预填无恢复 | 自动预填，但不提供恢复按钮。改乱后无退路。 | |

**User's choice:** 自动预填 + 可恢复（推荐）

---

## 存量修复

| Option | Description | Selected |
|--------|-------------|----------|
| 独立 SQL 脚本 | 新增幂等 SQL 文件（sql/fix-threshold-metric-key.sql），部署时手动执行。符合项目「不引入 Flyway、迁移暂用手动」的已定决策。 | ✓ |
| 启动时自动执行 | 应用启动时用 ApplicationRunner 自动执行归一化。部署零额外步骤，但每次启动都有写库风险。 | |
| 管理端点触发 | 新增受权限保护的管理端点（POST /api/admin/thresholds/normalize），运维手动调用。要新增端点与权限控制。 | |

**User's choice:** 独立 SQL 脚本（推荐）

### 追问：归一化后产生的重复行怎么处理

| Option | Description | Selected |
|--------|-------------|----------|
| 保留最新 | 保留 updated_at 最新的一行，删除其余。反映用户最近一次设置意图。 | ✓ |
| 保留 id 最大 | 保留 id 最大的一行（通常也是最新插入）。简单，但不一定反映最近一次修改。 | |
| 不删，重命名保留 | 只把重复行改成带后缀的占位 key 保留。不丢数据，但留下垃圾行。 | |

**User's choice:** 保留最新（推荐）

### 追问：alert_rule 种子里的非标准 key 要一并修吗

| Option | Description | Selected |
|--------|-------------|----------|
| 一并修正 | 同时把 schema.sql 里 alert_rule 的 glucose/temperature 改成 bloodGlucose/bodyTemperature，并写幂等 UPDATE 修存量 alert_rule。 | ✓ |
| 只修用户阈值 | 只改 user_threshold，alert_rule 种子保留原样。用户没配阈值时回退到种子规则仍会因 key 不匹配而失效。 | |

**User's choice:** 一并修正（推荐）

---

## 层级校验

| Option | Description | Selected |
|--------|-------------|----------|
| 双侧 + GOV.UK 双呈现 | 前端实时禁用「下一步/保存」并在违反的两个输入框下各挂内联中文，第2步顶部同步出现汇总。后端用类级约束 @ValidThresholdRange 做权威拦截，错误通过既有 fieldErrors 回到同两个字段。 | ✓ |
| 仅提交时校验 | 前端只在提交时校验，不实时禁用；后端权威拦截。实现简，但用户填错后要等点击才知道。 | |
| 仅前端校验 | 前端实时禁用，后端不做交叉校验（只靠非负）。前端可被绕过，会出现无效阈值入库。 | |

**User's choice:** 双侧 + GOV.UK 双呈现（推荐）

### 追问：生理极限用哪套边界

| Option | Description | Selected |
|--------|-------------|----------|
| 元数据 hardMin/Max | 用元数据里的 hardMin/hardMax 作为输入框 min/max 和校验边界。23 项指标都有这两个值，是现成权威源。 | ✓ |
| PANIC_LIMITS | 用前端 PANIC_LIMITS（仅 10 项，语义是急危重症阈值而非物理极限）。覆盖不全，语义也不同。 | |
| 新增阈值专用边界 | 后端注册表另加一层「阈值允许边界」字段，比 hardMin/Max 更宽。多一层配置。 | |

**User's choice:** 元数据 hardMin/Max（推荐）

---

## Claude's Discretion

- 四区预览条的具体视觉实现（`ElSlider` range + marks、自绘色带、或 `div` 拼接）——只要满足「颜色带 + 当前输入位置标注」即可。
- 确认语句的具体中文句式。
- 教学式空状态（THR-07）的默认阈值展示方式：直接读 `alert_rule` 种子或复用向导的推荐值来源。
- 重复指标（THR-08）回退到 Phase 11 已实现的 `ERROR_CODE_ACTIONS.resource.exists → 打开编辑` 机制，只需确认文案。

## Deferred Ideas

- 宽松/标准/严格三档预设（研究文档 THR-09 differentiator）——本轮不做，若后续需要可单开阶段。
- 条件感知预设（"我已确诊高血压" → 血压阈值对齐指南，THR-10）——涉及隐私与临床建议，不在本轮范围。
- 阈值变更影响预览（"改为这些值后，你近 30 天会有 N 条记录触发告警"）——需要历史数据扫描，属新能力。
- 应用启动时自动迁移框架（Flyway/Liquibase）——项目已明确推迟。