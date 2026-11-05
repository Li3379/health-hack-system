# Requirements: HHS v1.1 智能健康指标填报引导与友好校验

**Defined:** 2026-11-04  
**Milestone:** v1.1 智能健康指标填报引导与友好校验 (Intelligent Health Metric Input & Unified Validation)  
**Core Value:** 彻底解决健康指标填报时理解困难、缺乏引导、报错生硬及命名不一致问题，建立全链路预知引导、解释性友好反馈与统一校验字典。

---

## v1.1 Requirements

### META — 统一指标元数据注册中心 (Single Source of Truth)

- [x] **META-01**: 后端建立不可变的 `HealthMetricMetaRegistry` 领域服务，单点集中管理全系统 19+ 种健康与保健指标的标准 Key、中文名、单位、输入精度、步长、生理极限与临床正常参考范围
- [x] **META-02**: 后端提供 `GET /api/metrics/metadata` REST 接口，支持全量指标元数据及分类查询，并配置 HTTP 客户端/服务端强缓存
- [x] **META-03**: 前端通过 Pinia 状态库 `useMetricMetaStore` 统一拉取并响应式共享指标元数据，内置本地静态离线兜底常量，彻底废除各组件私自硬编码的范围字典

### KEY — Key 规范化与全站防英文外露 (Metric Key Normalization)

- [x] **KEY-01**: 后端与前端分别实现 `MetricKeyNormalizer`，双向无缝兼容 `camelCase`（如 `heartRate`）、`snake_case`（如 `heart_rate`）、大写（如 `HEART_RATE`）以及历史别名（如 `bodyTemperature`、`spo2`）
- [x] **KEY-02**: 后端 `HealthMetricRequest` 在反序列化阶段自动执行 Key 规范化，数据库查询与 Mapper 自动兼容别名匹配
- [x] **KEY-03**: 重构前端 `format.ts` 与各大页面（指标列表、预警中心、趋势图、实时监控），消除因缺少映射而向用户直接显示 `blood_glucose` 等原始英文代码的现象

### GUIDE — 录入前预知引导与参考体系 (Pre-Input Guidance)

- [x] **GUIDE-01**: `QuickInputDialog.vue` 与各录入表单根据指标元数据动态绑定标准单位、推荐步长（step）与小数精度（precision），输入框占位符显式展示生理参考范围（如“参考范围：3.9 ~ 6.1 mmol/L”）
- [x] **GUIDE-02**: 在输入框下方或侧边常态化展示健康参考标准解读卡片（如正常指标参考值区间、常见测量场景注意事项）
- [x] **GUIDE-03**: 血压录入支持收缩压（高压）与舒张压（低压）同屏联动展示与双向关联校验

### VAL — 四梯度宽容校验与友好解释性反馈 (Four-Tier Validation & Friendly Feedback)

- [x] **VAL-01**: 彻底废除失焦清空输入框机制（移除 `form.value.value = undefined`），输入异常时保留用户键入原值，绝不强制抹除用户劳动
- [x] **VAL-02**: 建立“正常 → 异常警示（允许保存）→ 急危重症（二次确认后允许保存）→ 物理不可能极限（拦截）”的四梯度模型，严禁将高血压、高血糖等真实病理数据当作表单非法输入拦截
- [x] **VAL-03**: 当输入超出正常参考范围时，以非阻塞的友好黄色/橙色状态胶囊及解释性文案替代生硬的红色报错（说明可能原因、测量状态区别，或单位误换算提示）
- [x] **VAL-04**: 检测到收缩压低于舒张压等生理倒置情况时，给出清晰的逻辑解释，并提供“一键对调高低压数值”的便捷自纠按钮
- [x] **VAL-05**: 录入达到严重危急值区间时，弹出友好的二次确认模态窗（提示防误触，同时提供急救与就医建议），用户点击“确认录入”后仍允许正常保存入库

### SYNC — 全渠道输入规则对齐 (Cross-Channel Consistency)

- [ ] **SYNC-01**: 重构 `AiParseServiceImpl.java` 与 OCR 图片识别确认流程，底层完全接入权威元数据注册表，确保 AI 解析提示、识别校验与手动表单规则 100% 统一

---

## v1.2 Requirements (全站健康与保健指标录入标准化与体验对齐)

### HEALTH — 健康指标录入对齐 (Health Metric Input Alignment)

- [ ] **HEALTH-01**: `src/views/health/Metrics.vue` 弹窗重构为动态从 `useMetricMetaStore` 获取指标列表、单位、精度、步长与参考范围占位符，彻底废除硬编码的 7 项 `el-option` 与 `metricUnitMap` 私有字典
- [ ] **HEALTH-02**: `src/views/health/Metrics.vue` 实现血压收缩压与舒张压协同双字段录入、倒置警示检测与一键对调能力，支持在列表页中统一录入与编辑
- [ ] **HEALTH-03**: `src/views/health/Metrics.vue` 接入四梯度纯函数医学校验引擎与非破坏性失焦保护，超出正常范围展示状态胶囊与解释性文案，危急值触发二次确认弹窗，物理极限拦截但不抹除输入

### WELL — 保健生活指标录入对齐 (Wellness Metric Input Alignment)

- [ ] **WELL-01**: `src/views/prevention/Metrics.vue` 与 `src/views/wellness/Dashboard.vue` 统一接入权威元数据 Store，废除私有 `WELLNESS_METRICS` 硬编码字典与固定 `precision=2/1`
- [ ] **WELL-02**: 保健指标录入保留心情（mood）特有的 1-5 星级打分机制，其他数值型指标（喝水、睡眠、步数、卡路里等）自动匹配推荐步长与精度，并展示常态化参考解读卡片
- [ ] **WELL-03**: 保健指标录入接入四梯度宽容校验与非破坏性失焦保护，避免生硬红色报错与强制清空，支持异常数值顺利留存

### REUSE — 通用表单复用与编辑一致性 (Reusable Input Form & UX Cohesion)

- [ ] **REUSE-01**: 提炼/封装可复用的健康与保健指标录入组件（或基于 `QuickInputDialog` 模式抽象共享组件），统一健康指标与保健指标的表单排版、`ReferenceGuideCard` 与 `CriticalConfirmDialog` 交互，消除三个页面间的重复代码
- [ ] **REUSE-02**: 指标编辑模式（修改既有记录）完整接入四梯度校验与元数据绑定，防止编辑历史数据时出现破坏性清空或校验规则不一致

---

## Future Requirements (v1.3+)

### Personalized Baseline

- **PERS-01**: 结合用户既往病史与个人偏好，支持自定义个性化基线控制目标（如老年人空腹血糖放宽至 7.0 mmol/L），并优先于群体标准在输入界面进行贴身提示

### Smart Context

- **CTXT-01**: 时间与场景自适应填报推荐（如早晨默认推荐空腹血糖与晨起血压，夜晚推荐今日步数与睡眠质量）

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| 表单输入逐字调用大模型诊断 | 产生秒级网络延迟、高昂 Token 成本，确定性医学规则本地引擎毫秒级响应更优 |
| 引入庞大的外部规则引擎（如 Drools） | 既有 Spring Boot + Element Plus 纯原生组合已完全满足需求，避免引入笨重外部依赖 |
| 强制修改数据库历史所有记录 key | 采用服务层透明映射兼容存量数据，避免破坏既有外系统同步与离线日志 |

---

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| META-01 | Phase 4 | Complete |
| META-02 | Phase 4 | Complete |
| META-03 | Phase 5 | Complete |
| KEY-01 | Phase 4 | Complete |
| KEY-02 | Phase 4 | Complete |
| KEY-03 | Phase 5 | Complete |
| GUIDE-01 | Phase 6 | Complete |
| GUIDE-02 | Phase 6 | Complete |
| GUIDE-03 | Phase 6 | Complete |
| VAL-01 | Phase 6 | Complete |
| VAL-02 | Phase 6 | Complete |
| VAL-03 | Phase 6 | Complete |
| VAL-04 | Phase 6 | Complete |
| VAL-05 | Phase 6 | Complete |
| SYNC-01 | Phase 7 | Pending |
| HEALTH-01 | Phase 8 | Pending |
| HEALTH-02 | Phase 8 | Pending |
| HEALTH-03 | Phase 8 | Pending |
| WELL-01 | Phase 9 | Pending |
| WELL-02 | Phase 9 | Pending |
| WELL-03 | Phase 9 | Pending |
| REUSE-01 | Phase 10 | Pending |
| REUSE-02 | Phase 10 | Pending |

---
*Requirements defined: 2026-11-04 after research synthesis*
