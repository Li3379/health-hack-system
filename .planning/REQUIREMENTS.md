# Requirements: HHS v1.3 引导式配置与友好错误反馈

**Defined:** 2026-11-05
**Milestone:** v1.3 引导式配置与友好错误反馈 (Guided Configuration & Friendly Error Feedback)
**Core Value:** 消除"不知道怎么填"与"一提交就报红"两类体验断层 — 让阈值配置有推荐值与通俗解释、让健康档案缺失被持续且非阻塞地引导、让全站失败反馈只出现一次且说人话。

---

## v1.3 Requirements

### FIX — 阈值与告警正确性缺陷修复 (Correctness Defects)

- [ ] **FIX-01**: 修正 `ThresholdRangeChecker.isWarningRange` 的区间判断逻辑，使 `criticalLow < 值 ≤ warningLow` 与 `warningHigh ≤ 值 < criticalHigh` 两个区间均正确判定为 WARNING，不再静默返回 NORMAL
- [ ] **FIX-02**: 阈值写入路径（`UserThresholdServiceImpl.create/update`）对 `metricKey` 执行 `MetricKeyNormalizer` 规范化后再存储，保证告警引擎按标准 key 能命中
- [ ] **FIX-03**: 阈值查询路径（`getByUserAndMetricKey` 及告警侧查询）兼容全部别名集合，即使存在历史非标准 key 记录也能命中
- [ ] **FIX-04**: 提供幂等 SQL 修复脚本，将存量 `user_threshold.metric_key` 非标准值（如 `glucose`、`temperature`、`bloodOxygen`）批量归一化为标准 key，并合并归一化后产生的重复行
- [ ] **FIX-05**: 为 `ThresholdRangeChecker` 的全部区间判定补充单元测试，覆盖正常/警告/危急/单侧为空/null 阈值等边界

### THR — 阈值配置引导 (Threshold Configuration Guidance)

- [ ] **THR-01**: 阈值页指标选择器改为从 `useMetricMetaStore` 动态渲染，按"健康指标/保健指标"分组，选项文案为 `显示名 (单位)`，仅列出告警引擎可评估的指标
- [ ] **THR-02**: 选定指标后自动预填四项阈值的推荐值并标注"推荐"徽标，同时提供"使用推荐值"一键回填
- [ ] **THR-03**: 四项阈值输入各自附带通俗中文说明与单位（如"警告高值：高于此值发出黄色提醒（mmHg）"），替代裸数字输入框
- [ ] **THR-04**: 提供实时四区预览条，以颜色带呈现严重低/警告低/正常/警告高/严重高，并标注当前输入位置
- [ ] **THR-05**: 保存前执行层级与生理极限校验（`criticalLow ≤ warningLow < warningHigh ≤ criticalHigh`，且均在指标硬极限内），前后端同规则，失败时以解释性中文文案内联提示而非红色弹窗
- [ ] **THR-06**: 保存前生成自然语言确认语句，描述实际告警行为（如"当收缩压 ≥ 140 时发黄色提醒，≥ 160 时发红色提醒"）
- [ ] **THR-07**: 阈值列表空状态改为教学型：说明当前使用系统默认规则、展示默认阈值内容，并提供"使用推荐阈值"入口
- [ ] **THR-08**: 重复指标保存时以中文友好提示"'该指标已有阈值，是否改为编辑？"并提供"打开编辑"操作，不再显示英文 `Threshold already exists for this metric`

### PRF — 健康档案完善引导 (Profile Completeness Guidance)

- [ ] **PRF-01**: 健康档案页与仪表盘持续展示档案完整度分数与等级（如"档案完整度 60% · 标准档案"），并逐字段标注已填/未填
- [ ] **PRF-02**: 每个档案字段下方展示"为何需要"说明（如出生日期 → "用于计算年龄，影响心血管风险评估"），且与实际消费方一致
- [ ] **PRF-03**: 档案字段标注重要度（必需/推荐/可选），并明确说明推荐与可选字段不填也可保存
- [ ] **PRF-04**: 档案缺失时展示影响横幅，具体点名下游后果（如"缺少身高/体重，BMI 与体重评分将按参考值估算"）
- [ ] **PRF-05**: 后端在风险评估与健康评分响应中披露实际采用的默认假设（附加字段，如 `assumptions[]` / `profileCompleteness`），不再静默填充
- [ ] **PRF-06**: 当使用默认假设时，风险评估页与健康评分页展示"基于不完整档案估算 · 完善档案"徽标并可跳转档案页
- [ ] **PRF-07**: 仪表盘在完整度低于阈值时展示可关闭提示，提供"完善档案"入口与"稍后提醒"，且设频率上限避免每次加载重复打扰
- [ ] **PRF-08**: 创建风险评估时若档案缺失，先展示将估算哪些内容并允许就地补填最少必需字段，不硬性阻断流程

### ERR — 全站友好错误反馈 (Site-wide Friendly Error Feedback)

- [ ] **ERR-01**: `utils/request.ts` 改为只翻译不呈现 — 失败时抛出类型化 `ApiError`（含 code、message、fieldErrors），由调用方或统一呈现层决定是否提示，保证一次失败只出现一次提示
- [ ] **ERR-02**: 所有面向用户的错误文案为中文且人性化，清除英文校验原文（如 `User ID is required`、`Threshold already exists for this metric`）
- [ ] **ERR-03**: 后端字段校验错误（`GlobalExceptionHandler` 返回的 `errors[]`）解析并内联回填到对应 `el-form-item`，字段名映射为中文标签
- [ ] **ERR-04**: 建立统一错误文案字典，替换全站 100+ 处泛化的"操作失败/请求失败"，改为说明"什么失败了 + 下一步怎么做"
- [ ] **ERR-05**: 校验失败或服务端报错后不清空用户已输入内容，弹窗保持打开且值保留
- [ ] **ERR-06**: 短时间内的相同错误提示自动合并，避免重试或多请求叠加产生多条重复 toast
- [ ] **ERR-07**: 网络错误、校验错误、权限错误在呈现方式与文案上可区分（校验内联、网络 toast 可重试、403/404 解释性状态）
- [ ] **ERR-08**: 清理用户取消操作（`'cancel'` 哨兵值、`AbortError`）被误判为失败并弹出红色提示的问题

---

## Future Requirements (v1.4+)

### Personalized Baseline

- **PERS-01**: 结合用户既往病史与个人偏好，支持自定义个性化基线控制目标（如老年人空腹血糖放宽至 7.0 mmol/L），并优先于群体标准在输入界面进行贴身提示

### Smart Context

- **CTXT-01**: 时间与场景自适应填报推荐（如早晨默认推荐空腹血糖与晨起血压，夜晚推荐今日步数与睡眠质量）

### Threshold Advanced

- **THR-F1**: 条件感知预设（如"我已确诊高血压"生成指南对齐的血压阈值），需临床审核
- **THR-F2**: 编辑模式内"重置为推荐值"操作

### Profile Advanced

- **PRF-F1**: 完善档案后的前后对比回执（"健康评分从估算 70 更新为 82"）
- **PRF-F2**: 完整度感知的 AI 建议 — AI 回复声明使用了哪些档案属性、缺失哪些

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| 阻塞式强制档案填写门禁 | 严重伤害新用户主流程；Apple HIG 明确建议推迟非必要设置，改为非阻塞引导 |
| 将过敏史/家族病史/生活习惯设为必填 | 自由文本病史不应强制；用户可能编造，比披露默认值更糟 |
| 阈值选择器暴露全部 23 项指标 | 告警引擎只评估部分指标；为心情/步数等提供阈值会产生永不生效的死设置 |
| 新建阈值模板表或引入 Flyway | 项目已决定延迟引入迁移框架；改用元数据注册表 + 告警种子规则派生 |
| 引入 vue-i18n / VeeValidate / driver.js / 第二套 UI 库 | 已安装的 Element Plus + 现有校验体系完全覆盖；新增依赖只会增加维护面 |
| 把四阈值模型改为单一目标区间 | 破坏 `ThresholdEvaluator` / `RecoveryNotifier` 契约并丢失严重度分层 |
| 静默自动创建阈值 | 健康告警必须经用户明确同意，否则用户不知道告警存在直到它触发 |
| 直接 `import @vueuse/core` | 它仅作为 Element Plus 的传递依赖存在，直接引用会在 lockfile 重解析时断裂 |

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| FIX-01 | Phase 12 | Pending |
| FIX-02 | Phase 12 | Pending |
| FIX-03 | Phase 12 | Pending |
| FIX-04 | Phase 12 | Pending |
| FIX-05 | Phase 12 | Pending |
| THR-01 | Phase 12 | Pending |
| THR-02 | Phase 12 | Pending |
| THR-03 | Phase 12 | Pending |
| THR-04 | Phase 12 | Pending |
| THR-05 | Phase 12 | Pending |
| THR-06 | Phase 12 | Pending |
| THR-07 | Phase 12 | Pending |
| THR-08 | Phase 12 | Pending |
| PRF-01 | Phase 13 | Pending |
| PRF-02 | Phase 13 | Pending |
| PRF-03 | Phase 13 | Pending |
| PRF-04 | Phase 13 | Pending |
| PRF-05 | Phase 13 | Pending |
| PRF-06 | Phase 13 | Pending |
| PRF-07 | Phase 13 | Pending |
| PRF-08 | Phase 13 | Pending |
| ERR-01 | Phase 11 | Pending |
| ERR-02 | Phase 11 | Pending |
| ERR-03 | Phase 14 | Pending |
| ERR-04 | Phase 11 | Pending |
| ERR-05 | Phase 14 | Pending |
| ERR-06 | Phase 11 | Pending |
| ERR-07 | Phase 14 | Pending |
| ERR-08 | Phase 14 | Pending |

**Coverage:**
- v1.3 requirements: 29 total
- Mapped to phases: 29
- Unmapped: 0

**Phase distribution:**
- Phase 11 全站统一友好错误反馈地基: ERR-01, ERR-02, ERR-04, ERR-06 (4)
- Phase 12 阈值配置向导化与告警正确性修复: FIX-01..05, THR-01..08 (13)
- Phase 13 健康档案完善引导（非阻塞）: PRF-01..08 (8)
- Phase 14 全站收尾排查与回归验证: ERR-03, ERR-05, ERR-07, ERR-08 (4)

---
*Requirements defined: 2026-11-05 after v1.3 research synthesis*
