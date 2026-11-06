# Roadmap: Health Hack System

## Milestones

- ✅ **v1.0 Optimization & Redeployment** — Phases 1-3 (completed 2026-05-29)
- ✅ **v1.1 智能健康指标填报引导与友好校验** — Phases 4-7 (completed 2026-11-05)
- ✅ **v1.2 全站健康与保健指标录入标准化与体验对齐** — [Phases 8-10](milestones/v1.2-ROADMAP.md) (completed 2026-11-05)
- 🚧 **v1.3 引导式配置与友好错误反馈** — Phases 11-14 (in progress)

## Phases

- [x] **Phase 4: 后端统一指标元数据注册中心与 Key 归一化** - 建立不可变 HealthMetricMetaRegistry 与 MetricKeyNormalizer，统一全系统 19+ 指标元数据与别名反序列化 (completed 2026-11-04)
- [x] **Phase 5: 前端元数据 Store 与全站格式化防英文外露** - 构建 Pinia useMetricMetaStore 与 format.ts 深度集成，全站杜绝原始英文 key 外露 (completed 2026-11-04)
- [x] **Phase 6: 前端非破坏性录入引导、四梯度宽容校验与友好反馈** - 彻底废除失焦抹除，实现四梯度宽容反馈、血压双向联动与危急值二次确认 (completed 2026-11-05)
- [x] **Phase 7: 全渠道校验对齐与跨端测试验证** - 统一 AI 解析与 OCR 识别校验引擎，跨渠道全链路测试闭环 (completed 2026-11-05)
- [x] **Phase 8: 健康指标管理页录入与编辑对齐** - 将 health/Metrics.vue 录入与编辑弹窗重构为权威元数据驱动，支持血压双字段协同与四梯度宽容校验 (completed 2026-11-05)
- [x] **Phase 9: 保健指标管理页录入对齐** - 将 prevention/Metrics.vue 与 wellness/Dashboard.vue 统一接入权威元数据，对齐心情评星与四梯度宽容校验 (completed 2026-11-05)
- [x] **Phase 10: 指标录入通用交互组件封装与全站验证** - 提炼高复用指标表单交互组件，统一编辑模式与全站体验一致性验证 (completed 2026-11-05)
- [ ] **Phase 11: 全站统一友好错误反馈地基** - 建立"只翻译不呈现"的错误信封、类型化 ApiError、字段错误内联投影与中文文案字典，使一次失败只出现一次提示
- [ ] **Phase 12: 阈值配置向导化与告警正确性修复** - 先修 ThresholdRangeChecker 与 metricKey 规范化/存量修复，再以元数据驱动向导替代四个裸数字输入框
- [ ] **Phase 13: 健康档案完善引导（非阻塞）** - 完整度分数、字段级"为何需要"、后端默认假设披露与可关闭仪表盘提示，全程不阻断任何既有功能
- [ ] **Phase 14: 全站收尾排查与回归验证** - 单站点迁移台账收口、残留裸 ElMessage.error 清零、全量后端/前端回归与端到端验证

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

**Plans:** 2/2 plans complete

Plans:
**Wave 1**

- [x] 06-01-PLAN.md — 四梯度纯函数评估引擎 (metricValidation.ts) 与响应式组合函数 (useMetricValidation.ts)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 06-02-PLAN.md — 常态化参考卡片、危急确认模态弹窗与 QuickInputDialog 非破坏性录入重构

**UI hint**: yes

### Phase 7: 全渠道校验对齐与跨端测试验证

**Goal**: 将 AI 智能文本解析与 OCR 报告识别对齐到权威元数据注册中心，完成手动填报、智能解析与多渠道采集的端到端一致性校验与回归测试闭环
**Depends on**: Phase 6
**Requirements**: SYNC-01
**Success Criteria** (what must be TRUE):

  1. AI 智能对话/文本解析 (`AiParseServiceImpl`) 与 OCR 体检报告识别 (`OcrServiceImpl`) 废除独立硬编码规则，完全消费 `HealthMetricMetaRegistry` 生成解析提示词与边界校验
  2. AI 解析、OCR 识别提取出的指标数据与手动录入在指标命名、单位换算、四梯度状态判定规则上达到 100% 严格一致
  3. 跨渠道（手动表单录入、OCR 上传解析、AI 文本抽取）全链路集成与端到端测试验证全部通过，无回归缺陷

**Plans:** 3/3 plans complete

Plans:
**Wave 1**

- [x] 07-01-PLAN.md — 前端健康与保健指标录入弹窗全面重构

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 07-02-PLAN.md — 后端 AI 动态提示词、OCR 化验扩展与全渠道入库物理极限拦截

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 07-03-PLAN.md — 跨渠道全链路测试闭环与跨端一致性验证

**UI hint**: yes

### Phase 8: 健康指标管理页录入与编辑对齐

**Goal**: 用户在健康指标管理页（`health/Metrics.vue`）录入或修改指标时，享受完整的元数据驱动动态选项、血压双向智能协同与四梯度宽容校验，彻底摆脱硬编码与破坏性清空体验
**Depends on**: Phase 6
**Requirements**: HEALTH-01, HEALTH-02, HEALTH-03
**Success Criteria** (what must be TRUE):

  1. 用户在健康指标管理页点击“添加指标”或“编辑”时，下拉列表动态展示由 `useMetricMetaStore` 提供的全部权威健康指标及中文标签，输入框占位符动态展示生理参考范围（如“参考范围：3.9 ~ 6.1 mmol/L”），输入组件自动绑定对应的精度与步长
  2. 用户选择“血压”指标时，表单切换为收缩压与舒张压协同双字段录入；当录入收缩压低于舒张压时，界面立即给出倒置原理解释并提供“一键对调高低压数值”按钮，点击后自动对调并重新评估状态
  3. 用户在表单中输入超出正常范围的值（如空腹血糖 12.0 mmol/L）并失焦时，输入值被完整保留绝不被清空，界面展示对应黄色/橙色状态胶囊与解释性文案；危急值触发二次确认模态窗，用户确认后仍可成功提交入库
  4. 用户输入超出物理生理极限（如心率 0 或 400 bpm）时，提交被拦截，但输入内容原样保留在输入框中方便用户修改，绝不发生强制重置清空

**Plans:** 2/2 plans complete

Plans:
**Wave 1**

- [x] 08-01-PLAN.md — 权威元数据动态选项、废除私有字典与血压协同录入及单项编辑适配

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 08-02-PLAN.md — 四梯度纯函数校验、危急确认模态窗、全局平铺优化与页面集成测试套件

**UI hint**: yes

### Phase 9: 保健指标管理页录入对齐

**Goal**: 用户在预防保健指标管理页（`prevention/Metrics.vue`）与生活方式看板（`wellness/Dashboard.vue`）录入保健数据时，获得统一的权威元数据支持、心情评星个性化交互及四梯度宽容校验保护
**Depends on**: Phase 8
**Requirements**: WELL-01, WELL-02, WELL-03
**Success Criteria** (what must be TRUE):

  1. 用户在预防保健管理页与生活方式看板点击录入时，指标选择项来自权威元数据注册中心，彻底消除 `WELLNESS_METRICS` 私有字典与固定写死精度；各数值型指标（如饮水量、步数、卡路里、睡眠时长）自动匹配对应的单位、步长与占位符
  2. 用户选择“心情（mood）”指标时，录入界面友好呈现 1-5 星级评分交互；选择其他数值型指标时，表单常态化展示健康参考标准解读卡片（ReferenceGuideCard），为生活方式管理提供权威指导
  3. 用户在录入偏高或偏低的保健数值（如单日饮水量 5000ml 或睡眠 3 小时）并失焦时，数据不被强行清空或阻断，而是以宽容警告胶囊提示生理影响，用户可正常保存入库追踪真实生活习惯

**Plans**: TBD

- [x] 09-01-PLAN.md
- [x] 09-02-PLAN.md

**UI hint**: yes

### Phase 10: 指标录入通用交互组件封装与全站验证

**Goal**: 提炼高复用、低耦合的通用指标录入交互体系，消除全站三个核心业务页面之间的代码重复，并对健康与保健指标的添加与编辑全链路执行一致性与回归验证
**Depends on**: Phase 9
**Requirements**: REUSE-01, REUSE-02
**Success Criteria** (what must be TRUE):

  1. 全站健康指标页（`health/Metrics.vue`）、预防保健页（`prevention/Metrics.vue`）与生活方式看板（`wellness/Dashboard.vue`）均基于统一提炼的指标表单组件或复用体系渲染，消除表单排版、状态胶囊、`ReferenceGuideCard` 与 `CriticalConfirmDialog` 的重复模板与逻辑代码
  2. 用户对既有历史指标进行“编辑”修改时，弹窗完整加载该指标的历史值与对应权威元数据规则，编辑过程中的非破坏性失焦保护与四梯度校验与新增模式完全一致，杜绝任何字段清空或规则脱节
  3. 全站指标录入全流程完成自动化与交互回归测试，前端单测覆盖新增与重构的录入组件，无 ESLint/TypeScript 类型报错

**Plans:** 2/2 plans complete

Plans:
**Wave 1**

- [x] 10-01-PLAN.md — UniversalMetricDialog 提炼封装与向后兼容适配

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 10-02-PLAN.md — 三大核心页面收敛接入 UniversalMetricDialog 与全站回归

**UI hint**: yes

### Phase 11: 全站统一友好错误反馈地基

**Goal**: 建立全站唯一的错误"翻译—呈现"分层：后端输出结构化字段错误信封，前端拦截器只翻译不展示，呈现权下放给统一呈现层与表单内联投影，使任何一次失败恰好产生一条人类可读的中文提示
**Depends on**: Nothing (first phase of v1.3, builds on the v1.2 foundation)
**Requirements**: ERR-01, ERR-02, ERR-04, ERR-06
**Success Criteria** (what must be TRUE):

  1. 当后端返回字段校验失败（`errors[]` / `fieldErrors[]`）时，用户只在对应输入框下方看到一条中文内联红字，弹窗顶部出现一条汇总提示，不再出现任何 toast
  2. 当后端返回业务失败（HTTP 200 但 `code != 200`）、HTTP 5xx 或断网时，用户只看到一条中文提示，说明"什么失败了 + 下一步怎么做"，不再出现"操作失败/请求失败"这类泛化文案，也不再出现英文原文与字段名
  3. 用户取消确认框（`'cancel'`/`'close'`）或取消报告生成（`AbortError`/`CanceledError`）时，界面不出现任何红色提示
  4. 后台轮询、元数据预加载等静默请求失败时界面无 toast；令牌过期时并发请求最多产生一条提示与一次跳转
  5. 同一失败在短时间内的重复提示自动合并为一条，不再叠加多条相同 toast

**Plans:** 7 plans

Plans:
**Wave 1**

- [ ] 11-01-PLAN.md — 后端错误信封契约：FieldErrorVO、Result 增 errorCode/fieldErrors、GlobalExceptionHandler 三校验分支统一投影（含 getGlobalErrors）

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 11-02-PLAN.md — 后端英文文案治理：8 个 DTO 注解消息、阈值服务异常、推送控制器字面量中文化
- [ ] 11-03-PLAN.md — 前端错误模型：ApiError/normalizeApiError/isCancel、request.ts 只翻译不展示、silent 模块增强、401 闩锁

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 11-04-PLAN.md — 扁平文案字典 FIELD_LABELS 与唯一 toast 出口 showError，含重复合并与重试动作；theme.css 错误 token 与 EP 覆盖

**Wave 4** *(blocked on Wave 3 completion)*

- [ ] 11-05-PLAN.md — 字段错误投影：useFormServerErrors composable 与 FormErrorSummary 顶部汇总组件
- [ ] 11-06-PLAN.md — 页面级错误状态块 ErrorState.vue 与两处后台请求 silent 化（元数据预加载、未读数）

**Wave 5** *(blocked on Wave 4 completion)*

- [ ] 11-07-PLAN.md — 三个迁移样本接线：prevention/Profile.vue 双呈现、stores/push.ts 单一出口与返回值契约、health/Thresholds.vue 的 RESOURCE_ALREADY_EXISTS 恢复动作

**UI hint**: yes

### Phase 12: 阈值配置向导化与告警正确性修复

**Goal**: 先让阈值与告警的判定和落库真实生效（修正警告区间反向条件、写入与查询路径 key 规范化、存量数据修复），再把阈值配置从四个裸数字输入框升级为元数据驱动的引导式向导，使用户看得懂、填得对、存得生效
**Depends on**: Phase 11
**Requirements**: FIX-01, FIX-02, FIX-03, FIX-04, FIX-05, THR-01, THR-02, THR-03, THR-04, THR-05, THR-06, THR-07, THR-08
**Success Criteria** (what must be TRUE):

  1. 用户配置的阈值在保存后真实触发告警：`warningHigh ≤ 值 < criticalHigh` 与 `criticalLow < 值 ≤ warningLow` 两个区间被正确判定为 WARNING；无论通过向导还是直接调用 API 保存 `glucose`/`bloodGlucose` 等任一别名，落库均为标准 key 且告警引擎能命中；存量非标准 key 行已被幂等脚本归一化并合并重复行
  2. 用户打开阈值页时，指标选择器按"健康指标/保健指标"分组列出告警引擎可评估的指标，选项文案为 `显示名 (单位)`；选定指标后四项阈值自动预填推荐值并标注"推荐"，可一键回填
  3. 四项阈值输入各自带通俗中文说明与单位，并附实时四区预览条（严重低/警告低/正常/警告高/严重高）标注当前输入位置；保存前展示一句自然语言确认语句描述实际告警行为
  4. 用户输入违反层级或生理极限的阈值（如警告高值低于警告低值、超出硬极限）时，保存被前后端同规则拦截并以解释性中文内联提示说明原因，不出现红色弹窗、不清空已输入内容
  5. 阈值列表为空时展示教学型空状态（说明当前使用系统默认规则、展示默认阈值内容、提供"使用推荐阈值"入口）；重复指标保存时提示"该指标已有阈值，是否改为编辑？"并提供"打开编辑"操作

**Plans**: TBD

**UI hint**: yes

### Phase 13: 健康档案完善引导（非阻塞）

**Goal**: 让健康档案的缺失从"静默代入默认值"变为"持续可见、可理解、可关闭、绝不阻断"的引导：展示完整度与逐字段状态、解释每个字段为何需要、披露后端实际采用的默认假设，并在下游结果页标注估算来源
**Depends on**: Phase 11
**Requirements**: PRF-01, PRF-02, PRF-03, PRF-04, PRF-05, PRF-06, PRF-07, PRF-08
**Success Criteria** (what must be TRUE):

  1. 用户在健康档案页与仪表盘持续看到档案完整度分数与等级（如"档案完整度 60% · 标准档案"）及逐字段已填/未填状态；每个字段下方有与实际消费方一致的"为何需要"说明，并标注必需/推荐/可选，且明确说明推荐与可选字段不填也可保存
  2. 档案缺失时用户看到点名下游后果的影响横幅（如"缺少身高/体重，BMI 与体重评分将按参考值估算"）；当系统使用默认假设时，风险评估页与健康评分页展示"基于不完整档案估算 · 完善档案"徽标并可跳转档案页
  3. 后端风险评估与健康评分响应中实际披露所采用的默认假设（如 `assumptions[]` / `profileCompleteness`），不再静默填充
  4. 仪表盘在完整度低于阈值时展示可关闭提示，提供"完善档案"与"稍后提醒"，且频率上限生效——刷新多个页面最多出现一条提示，关闭后 7 天内不再打扰
  5. 仅有性别与出生日期的既有用户仍可正常使用健康评分、风险评估、AI 建议与指标录入等全部既有功能（无路由门禁、无新增必填字段）；创建风险评估时若档案缺失只就地提示将估算哪些内容并允许补填，不硬性阻断

**Plans**: TBD

**UI hint**: yes

### Phase 14: 全站收尾排查与回归验证

**Goal**: 将统一错误体系推行到全站所有提交与加载路径，清除残留的裸错误弹窗与重复提示，并以全量自动化与端到端验证锁定"90 + 新增"测试基线，确保 v1.3 改动无回归
**Depends on**: Phase 12, Phase 13
**Requirements**: ERR-03, ERR-05, ERR-07, ERR-08
**Success Criteria** (what must be TRUE):

  1. 全站所有表单提交在服务端字段校验失败时均将错误内联回填到对应 `el-form-item`（字段名映射为中文标签），而其他失败类型呈现方式可区分：校验错误内联、网络错误 toast 可重试、403/404 为解释性状态
  2. 任何校验失败或服务端报错后，用户已输入内容不被清空、弹窗保持打开且值保留；全站迁移台账完成，统一 helper 之外的裸 `ElMessage.error` 残留归零
  3. 停止后端后逐页点击保存/加载，每个动作恰好出现一条中文可行动提示；断网 60 秒内轮询不产生重复 toast
  4. 前端 `npm run test:run` 全绿且用例数不少于既有 90 项基线（新增错误分类、取消/中止、无英文文案、阈值次序、完整度等级测试），后端 `mvn clean verify -DskipITs` 全绿，E2E `quick-input.spec.ts` 成功文案契约仍然通过

**Plans**: TBD

**UI hint**: yes

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 4. 后端统一指标元数据注册中心与 Key 归一化 | 2/2 | Complete    | 2026-11-04 |
| 5. 前端元数据 Store 与全站格式化防英文外露 | 2/2 | Complete    | 2026-11-04 |
| 6. 前端非破坏性录入引导、四梯度宽容校验与友好反馈 | 2/2 | Complete    | 2026-11-05 |
| 7. 全渠道校验对齐与跨端测试验证 | 3/3 | Complete    | 2026-11-05 |
| 8. 健康指标管理页录入与编辑对齐 | 2/2 | Complete    | 2026-11-05 |
| 9. 保健指标管理页录入对齐 | 2/2 | Complete    | 2026-11-05 |
| 10. 指标录入通用交互组件封装与全站验证 | 2/2 | Complete    | 2026-11-05 |
| 11. 全站统一友好错误反馈地基 | 0/0 | Not started | - |
| 12. 阈值配置向导化与告警正确性修复 | 0/0 | Not started | - |
| 13. 健康档案完善引导（非阻塞） | 0/0 | Not started | - |
| 14. 全站收尾排查与回归验证 | 0/0 | Not started | - |
