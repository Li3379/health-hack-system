# Roadmap: Health Hack System

## Milestones

- ✅ **v1.0 Optimization & Redeployment** — Phases 1-3 (completed 2026-05-29)
- 🚧 **v1.1 智能健康指标填报引导与友好校验** — Phases 4-7 (in progress)

## Phases

- [x] **Phase 4: 后端统一指标元数据注册中心与 Key 归一化** - 建立不可变 HealthMetricMetaRegistry 与 MetricKeyNormalizer，统一全系统 19+ 指标元数据与别名反序列化 (completed 2026-11-04)
- [x] **Phase 5: 前端元数据 Store 与全站格式化防英文外露** - 构建 Pinia useMetricMetaStore 与 format.ts 深度集成，全站杜绝原始英文 key 外露 (completed 2026-11-04)
- [ ] **Phase 6: 前端非破坏性录入引导、四梯度宽容校验与友好反馈** - 彻底废除失焦抹除，实现四梯度宽容反馈、血压双向联动与危急值二次确认
- [ ] **Phase 7: 全渠道校验对齐与跨端测试验证** - 统一 AI 解析与 OCR 识别校验引擎，跨渠道全链路测试闭环

## Phase Details

### Phase 4: 后端统一指标元数据注册中心与 Key 归一化

**Goal**: 为全系统建立不可变的指标元数据权威定义源（SSOT）与多格式 Key 规范化引擎，向所有客户端与下游服务提供一致的指标定义与入参兼容能力
**Depends on**: Nothing (first phase of v1.1, builds on v1.0 foundation)
**Requirements**: META-01, META-02, KEY-01, KEY-02
**Success Criteria** (what must be TRUE):

  1. 调用 `GET /api/metrics/metadata` 接口可获取全量 19+ 种指标的标准元数据（包含标准 key、中文名称、单位、精度、步长、参考范围与极限范围），并带有高效缓存响应头
  2. 后端接收 `snake_case`（如 `heart_rate`）、`camelCase`（如 `heartRate`）、全大写及历史别名（如 `bodyTemperature`、`spo2`）的请求均能正确规范化为标准 key 并成功入库与持久化
  3. 通过多格式 key 查询健康指标历史记录与告警规则，均能准确命中并返回统一的标准 key
  4. 针对 19+ 种指标边界与别名转换的单元测试全部通过，杜绝硬编码分散定义

**Plans:** 2/2 plans complete

Plans:
**Wave 1**

- [x] 04-01-PLAN.md — 权威元数据注册表 (HealthMetricMetaRegistry)、Key 归一化器 (MetricKeyNormalizer) 与元数据 GET 端点

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 04-02-PLAN.md — 请求级生理极限硬拦截 (@ValidMetricRecord)、入参自动归一化与服务层别名兼容查询

### Phase 5: 前端元数据 Store 与全站格式化防英文外露

**Goal**: 建立前端响应式元数据状态管理与统一标签格式化能力，废除前端分散硬编码字典，彻底消除全系统界面上的英文代码外露缺陷
**Depends on**: Phase 4
**Requirements**: META-03, KEY-03
**Success Criteria** (what must be TRUE):

  1. 前端通过 `useMetricMetaStore` 成功从后端拉取并缓存全量指标元数据；在离线或弱网时无缝回退至内置静态兜底常量，应用无阻塞报错
  2. 用户在指标列表、预警中心、趋势分析图表与实时监控卡片中，所有指标名称与单位均正确显示为本地化中文标签与标准单位，不再出现任何形如 `blood_glucose`、`heart_rate` 等原始英文 key
  3. 各组件废除自建的硬编码范围与单位字典，统一由 store 提供响应式计算属性

**Plans:** 2/2 plans complete

Plans:
**Wave 1**

- [x] 05-01-PLAN.md — 权威元数据静态常量 (FALLBACK_METRIC_META)、Key 归一化器 (metricNormalizer) 与 Pinia Store (useMetricMetaStore)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 05-02-PLAN.md — 重构 format.ts 标签与单位解析防英文外露、清理 prevention/Metrics 私有字典并完成全量单测覆盖

**UI hint**: yes

### Phase 6: 前端非破坏性录入引导、四梯度宽容校验与友好反馈

**Goal**: 重塑健康指标数据录入交互体验，实现基于元数据的预知引导、四梯度宽容校验与非破坏性友好解释反馈，保护用户录入劳动并确保危急生理数据顺畅留存
**Depends on**: Phase 5
**Requirements**: GUIDE-01, GUIDE-02, GUIDE-03, VAL-01, VAL-02, VAL-03, VAL-04, VAL-05
**Success Criteria** (what must be TRUE):

  1. 用户打开指标填报弹窗时，输入框占位符显式展示生理参考范围（如“参考范围：3.9 ~ 6.1 mmol/L”），输入组件自动绑定标准步长与精度，下方常态化展示健康参考标准解读卡片
  2. 用户输入超出正常参考范围（如高血糖 12.0 mmol/L）并失焦时，输入值被完整保留绝不被清空；界面以友好黄色/橙色状态胶囊和解释性文案提示生理异常与测量建议，且允许用户正常点击保存入库
  3. 输入达到严重危急值区间（如高压 190 mmHg）时，弹出友好解释性二次确认模态窗（提示防误触并提供就医急救指引），用户确认后仍可成功保存入库
  4. 血压录入支持高压与低压同屏协同录入与关联校验；检测到高压低于低压倒置时，给出原理解释并提供“一键对调高低压数值”按钮，点击后自动修正对调
  5. 仅当输入处于物理不可能生理极限（如体温 55°C、心率 0 或 400 bpm）时阻止提交，且输入框原样保留输入文本方便用户直接回退修改

**Plans:** 2 plans

Plans:
**Wave 1**

- [ ] 06-01-PLAN.md — 四梯度纯函数评估引擎 (metricValidation.ts) 与响应式组合函数 (useMetricValidation.ts)

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 06-02-PLAN.md — 常态化参考卡片、危急确认模态弹窗与 QuickInputDialog 非破坏性录入重构

**UI hint**: yes

### Phase 7: 全渠道校验对齐与跨端测试验证

**Goal**: 将 AI 智能文本解析与 OCR 报告识别对齐到权威元数据注册中心，完成手动填报、智能解析与多渠道采集的端到端一致性校验与回归测试闭环
**Depends on**: Phase 6
**Requirements**: SYNC-01
**Success Criteria** (what must be TRUE):

  1. AI 智能对话/文本解析 (`AiParseServiceImpl`) 与 OCR 体检报告识别 (`OcrServiceImpl`) 废除独立硬编码规则，完全消费 `HealthMetricMetaRegistry` 生成解析提示词与边界校验
  2. AI 解析、OCR 识别提取出的指标数据与手动录入在指标命名、单位换算、四梯度状态判定规则上达到 100% 严格一致
  3. 跨渠道（手动表单录入、OCR 上传解析、AI 文本抽取）全链路集成与端到端测试验证全部通过，无回归缺陷

**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 4. 后端统一指标元数据注册中心与 Key 归一化 | 2/2 | Complete    | 2026-11-04 |
| 5. 前端元数据 Store 与全站格式化防英文外露 | 2/2 | Complete    | 2026-11-04 |
| 6. 前端非破坏性录入引导、四梯度宽容校验与友好反馈 | 0/0 | Not started | - |
| 7. 全渠道校验对齐与跨端测试验证 | 0/0 | Not started | - |
