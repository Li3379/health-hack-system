# Architecture Research

**Domain:** 既有 Spring Boot 3.2 + Vue 3 单体应用的增量能力集成 — 统一错误反馈层、阈值关系校验、健康档案完整度与下游依赖门控
**Researched:** 2026-11-05
**Confidence:** HIGH（结论基于对仓库现有代码的逐行审计；所有集成点均已定位到文件与行号）

---

## Scope & Method

本文件回答一个**集成问题**，而非绿地设计问题：三项新能力如何嵌入既有架构，才能不破坏 v1.0–v1.2 已交付的行为（四梯度校验、UniversalMetricDialog、元数据 SSOT、评分持久化、CI/CD 流水线）。

研究方式：以仓库为第一手来源，读取 `request.ts`、`GlobalExceptionHandler`、`Result<T>`、`ErrorCode`、`UserThresholdRequest/ServiceImpl/Controller`、`ThresholdRangeChecker/Comparator/Evaluator`、`ProfileScorer`、`RiskAssessmentServiceImpl`、`HealthReportServiceImpl`、`AIChatServiceImpl`、`AlertAIAnalysisService`、`HealthScoreServiceImpl`、`UniversalMetricDialog.vue`、`Thresholds.vue`、`Profile.vue`、`metricMeta.ts`、`metricValidation.ts`，并核对本地 `element-plus` 类型声明（`FormInstance.validateField`、`FormItem.error`）。未依赖外部二手资料。

---

## Standard Architecture

### System Overview — 三项能力的落点

```
┌───────────────────────────────────────────────────────────────────────────────┐
│                        前端展示层 (Vue 3 + Element Plus)                       │
├───────────────────────────────────────────────────────────────────────────────┤
│  UniversalMetricDialog   Thresholds.vue   Profile.vue   Risk.vue   Score.vue  │
│        │                      │                │             │          │      │
│        │  useFormServerErrors (NEW) │          │             │          │      │
│        ▼                      ▼                ▼             ▼          ▼      │
│  ┌────────────────────────────────────────┐  ┌──────────────────────────────┐ │
│  │ showError() / ApiError  (NEW appError) │  │ useProfileReadinessStore(NEW)│ │
│  │ 唯一 toast 出口 + 字段错误归一化        │  │ 会话级单次拉取 + 失效重取     │ │
│  └────────────────┬───────────────────────┘  └──────────────┬───────────────┘ │
│                   │                                          │                │
│  ┌────────────────▼───────────────────────┐                  │                │
│  │ request.ts  (MODIFIED: 只翻译不展示)    │                  │                │
│  │ + silent 请求级配置 + Accept-Language   │                  │                │
│  └────────────────┬───────────────────────┘                  │                │
├───────────────────┼──────────────────────────────────────────┼────────────────┤
│                   │ HTTP                                     │ HTTP           │
│                   ▼                                          ▼                │
│  ┌────────────────────────────────────────┐  ┌──────────────────────────────┐ │
│  │ GlobalExceptionHandler (MODIFIED)      │  │ PreventionController         │ │
│  │ → Result {code,errorCode,message,      │  │ GET /profile/readiness (NEW) │ │
│  │           fieldErrors[]}               │  └──────────────┬───────────────┘ │
│  └────────────────┬───────────────────────┘                 │                 │
├───────────────────┼─────────────────────────────────────────┼─────────────────┤
│                   ▼                                         ▼                 │
│  ┌──────────────────────────────┐   ┌───────────────────────────────────────┐ │
│  │ @ValidThresholdRange (NEW)   │   │ ProfileReadinessService (NEW)         │ │
│  │ ThresholdRangeValidator(NEW) │   │  └ ProfileCompletenessCalculator(NEW) │ │
│  │ 挂载 UserThresholdRequest    │   │      ↑ 复用（重构）ProfileScorer      │ │
│  └──────────────┬───────────────┘   └───────────────┬───────────────────────┘ │
│                 ▼                                   ▼                         │
│  ┌──────────────────────────────┐   ┌───────────────────────────────────────┐ │
│  │ UserThresholdServiceImpl     │   │ HealthProfileMapper（单次读取）        │ │
│  │ (MODIFIED: 仅补测试与委托)    │   │ RiskAssessment / Report / AIChat      │ │
│  └──────────────────────────────┘   │ 各自 getProfileByUserId（现状保留）    │ │
│                                      └───────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ ThresholdRangeChecker (BUG FIX: isWarningRange 条件颠倒) → 统一 classify │  │
│  └──────────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Home | New/Modified |
|-----------|----------------|------|--------------|
| **`Result<T>`** | 统一响应信封；新增 `errorCode`（字符串）与 `fieldErrors`（结构化）两个可选字段 | `common/Result.java` | MODIFIED |
| **`FieldErrorVO`** | 字段级错误载荷 `{field, message, code?}`，替代当前 `List<String>` 的 `"field: msg"` 字符串拼接 | `vo/FieldErrorVO.java` | NEW |
| **`GlobalExceptionHandler`** | 所有异常收敛为同一信封；validation 分支产出 `fieldErrors`；保留 HTTP 语义不变 | `exception/GlobalExceptionHandler.java` | MODIFIED |
| **`ApiError` + `normalizeApiError`** | 前端唯一错误模型：`kind`（validation/auth/network/server/business）、`message`、`fieldErrors: Record<field,string>`、`status`、`code` | `utils/appError.ts` | NEW |
| **`request.ts`** | 只做**翻译与拒绝**，不做展示（401 例外）；支持 `silent` 请求配置；固定 `Accept-Language` | `utils/request.ts` | MODIFIED |
| **`useFormServerErrors`** | 把 `fieldErrors` 绑定到 `el-form-item :error`，提交时清空、成功后清空 | `composables/useFormServerErrors.ts` | NEW |
| **`@ValidThresholdRange` + `ThresholdRangeValidator`** | 类级跨字段约束，校验 `criticalLow < warningLow < warningHigh < criticalHigh`（null 表示无界） | `validation/` | NEW |
| **`thresholdValidation.ts`** | 前端同构纯函数，用于即时禁用保存与内联提示（仅 UX 前置，非权威） | `utils/thresholdValidation.ts` | NEW |
| **`ThresholdRangeChecker`** | 现有四态判定；`isWarningRange` 逻辑缺陷需修复 | `service/domain/ThresholdRangeChecker.java` | MODIFIED (bug fix) |
| **`ProfileCompletenessCalculator`** | 纯函数：由 `HealthProfile`（可为 null）推导完整度分数、缺失字段、每项权重与下游影响 | `service/domain/ProfileCompletenessCalculator.java` | NEW |
| **`ProfileScorer`** | 改为委托 `ProfileCompletenessCalculator`，消除第二套完整度规则 | `service/domain/ProfileScorer.java` | MODIFIED |
| **`ProfileReadinessService` / `Impl`** | 单次读取档案 → 组装 `ProfileReadinessVO`（完整度 + 下游依赖状态表） | `service/ProfileReadinessService.java` + `service/impl/` | NEW |
| **`ProfileReadinessVO`** | `{completeness, dependencies[]}`，前端无需二次调用 | `vo/ProfileReadinessVO.java` | NEW |
| **`PreventionController`** | 新增 `GET /api/prevention/profile/readiness`（复用已有路由前缀与鉴权） | `controller/PreventionController.java` | MODIFIED |
| **`useProfileReadinessStore`** | Pinia：会话级单次拉取 + `invalidate()`，仿 `useMetricMetaStore` 的离线容错风格 | `stores/profileReadiness.ts` | NEW |
| **`ProfileReadinessBanner`** | 全局单条横幅：完整度 + 一条最高优先级建议 + CTA | `components/profile/ProfileReadinessBanner.vue` | NEW |

---

## Data Flow

### Flow 1: 一次失败 → 恰好一条人类可读信息

**目标契约（新规则）**

| 失败类型 | 后端形状 | 前端展示 | 数量 |
|---------|---------|---------|------|
| 字段校验失败（400 + `fieldErrors`） | `fieldErrors[]` 非空 | **仅内联**：`el-form-item :error`；弹窗顶部一条 `el-alert` 汇总 | 每条信息 1 个位置，无 toast |
| 业务规则失败（HTTP 200，body `code != 200`） | `errorCode` 存在、`fieldErrors` 为空 | `showError(error, fallback)` → 1 个 `ElMessage` | 1 |
| 传输/服务器失败（5xx、超时、断网） | 无信封 | `showError` → 1 个 `ElMessage` | 1 |
| 401 | 无信封 | 1 个 toast + `authStore.logout()`（唯一在拦截器内展示的例外） | 1 |

**请求链路（改造后）**

```
用户点击保存
   ↓
formRef.validate()  ← 本地规则（四梯度、硬极限、血压倒置）
   ↓ 通过
API 调用（可能 Promise.all 两个血压请求）
   ↓
request.ts 响应拦截器
   ├─ res.code === 200 → 返回信封
   └─ 否则 → normalizeApiError() → reject(new ApiError(...))     ← 不再 ElMessage
   ↓ (catch)
调用方
   ├─ validation kind → useFormServerErrors.apply(error.fieldErrors)；不 toast
   └─ 其它 kind      → showError(error, '<场景回退文案>')
```

**关键实现细节（均为已核实的现状约束）**

1. **两条不同的失败通道必须都处理。** `GlobalExceptionHandler.handleMethodArgumentNotValid` 返回 `ResponseEntity.badRequest()`（HTTP 400，走 Axios `error` 分支）；而 `handleBusinessException` 返回 `Result.failure(ec.getCode(), message)`（HTTP 200，走 `response` 分支）。因此 `normalizeApiError` 必须同时读取 `error.response.data` 与 `error.response.data.code`。
2. **当前 `errors` 数组在传输层已被丢弃。** `request.ts` 的 error 分支只取 `data?.message`（第 70 行），`errors` 从未到达页面。这是"字段级细节无法回到表单"的根因。
3. **业务错误码当前会退化为 500。** `BusinessException` 的 `ec.getCode()` 返回字符串（如 `health.threshold_invalid`），而 `Result.failure(String, String)` 会走 `parseCodeToInt` 并回退到 `500`（`Result.java:42-48`）。新增 `errorCode` 字段后前端可精确分支，同时**不改变** `code` 的既有数值语义以保护既有测试（如 `PushConfigControllerTest` 断言 `status().isOk()` + `$.code == 400`）。
4. **语言漂移。** 前端从不发送 `Accept-Language`，后端按浏览器语言分支（`resolveLanguage` 只在 `startsWith("en")` 时用英文）。中文界面 + 英文浏览器会拿到英文报错。修正：`request.ts` 显式设置 `Accept-Language: zh-CN`。这比给每个 DTO 注解做 i18n 更小、更确定。
5. **`silent` 是消除"背景请求噪音"的必要开关。** 当前 `App.vue` 的 `fetchMetadata()`、`data-input/Index.vue` 的 `loadTodayStats()` 在 `catch` 中静默吞掉错误，但拦截器已经弹过一次红 toast——"静默加载失败却弹窗"是重复反馈的另一来源。新增 `AxiosRequestConfig` 扩展 `{ silent?: boolean }`（`types/axios.d.ts` NEW），拦截器据此跳过展示，只 reject。
6. **血压双请求的分字段归属。** `UniversalMetricDialog.vue:716-719` 用 `Promise.all([createMetric(systolic), createMetric(diastolic)])` 提交。后端返回的 `fieldErrors.field` 一律是 `value`（DTO 字段名），前端无法区分是哪一条失败。必须在调用侧标注上下文（例如 `catch(e => { e.formField = 'diastolicValue'; throw e })`），由 `useFormServerErrors` 优先使用 `formField` 覆盖后端字段名。

---

### Flow 2: 档案完整度与下游依赖（单次调用，无 N+1）

```
应用启动 / 登录成功 / 档案保存成功
   ↓
useProfileReadinessStore.fetch()                       ← 每会话 1 次
   ↓ GET /api/prevention/profile/readiness
ProfileReadinessService
   ├─ HealthProfileMapper.selectOne(userId)             ← 唯一一次档案查询
   ├─ ProfileCompletenessCalculator.calculate(profile)  ← 纯函数，无 IO
   └─ DependencyMatrix（静态表，编译期常量）
   ↓ Result<ProfileReadinessVO>
{ completeness: { status, score, fields[] }, dependencies: [ {key,label,status,reason} ] }
   ↓
ProfileReadinessBanner（MainLayout 内，1 处渲染）
Risk.vue / Score.vue / Dashboard.vue（读取同一 store，不额外请求）
```

**依赖矩阵（与现有消费者一一对应，均为代码实证）**

| featureKey | 消费者 | 档案缺失 (MISSING) | 档案不完整 (PARTIAL) | 依据 |
|-----------|--------|--------------------|----------------------|------|
| `healthScore` | `ScoreCalculator` → `ProfileScorer` | DEGRADED（返回中性 70，占权重 25%） | DEGRADED（按缺失项扣分） | `ProfileScorer.java:35-61`、`ScoreCalculator.java:53-60` |
| `riskAssessment` | `RiskAssessmentServiceImpl` | **BLOCKED**（现状静默代入 age=35 / BMI=22 产出伪评分） | DEGRADED（缺 age 或 BMI 时代入默认值） | `RiskAssessmentServiceImpl.java:50-51,117-124` |
| `healthReport` | `HealthReportServiceImpl` | BLOCKED（抛 `HEALTH_PROFILE_NOT_FOUND`） | DEGRADED（提示词缺字段） | `HealthReportServiceImpl.java:172-175` |
| `aiAdvice` | `AIChatServiceImpl` / `AlertAIAnalysisService` | DEGRADED（提示词无个性化段落） | DEGRADED（部分字段省略） | `AIChatServiceImpl.java:85-116`、`AlertAIAnalysisService.java:134-150` |

**为什么不把 readiness 塞进 `DashboardSummaryVO`：** 仪表盘并非所有用户入口，且会让 `StatsServiceImpl` 额外依赖 `HealthProfileMapper`；而 store + 专用端点只调用一次、命中缓存的成本更低，契约也更单一。这属于"避免投机抽象"的取舍。

**缓存失效策略（最小实现）：** 档案的唯一写入方是 `HealthProfileServiceImpl.create/update`，且由同一前端会话触发。因此 `Profile.vue` 保存成功后调用 `store.fetch()`，登录时在 `App.vue` 调用一次即可，无需新增领域事件。若后续出现管理端代改档案，再引入 `ProfileUpdatedEvent`。

---

### Flow 3: 阈值关系校验

**不变量（必须与判定代码一致，而非"直觉顺序"）**

`ThresholdRangeChecker` 的语义为：`value <= criticalLow` → CRITICAL；`value >= criticalHigh` → CRITICAL；`isNormalRange` 用 `value <= warningLow || value >= warningHigh` 判定异常。由此推导的合法次序：

```
criticalLow  <  warningLow  <  warningHigh  <  criticalHigh
（每个边界可独立为 null；仅当相邻两侧均非 null 时才要求成立）
```

**校验归属**

| 规则 | 归属 | 理由 |
|------|------|------|
| 关系次序（权威） | **后端** `@ValidThresholdRange` 类级约束（挂 `UserThresholdRequest`） | 直接调用 API 可绕过前端；且 `UserThresholdRequest` 已被 `@Valid` 装饰，无需新增调用点；沿用既有 `@ValidMetricRecord` 的类级约束先例 |
| 关系次序（UX 前置） | **前端** `utils/thresholdValidation.ts` 纯函数 | 即时禁用保存 + 内联提示，避免无谓往返，符合四梯度友好反馈哲学；但明确标注为"非权威" |
| 边界非负 | 后端 `@DecimalMin("0.0")`（已存在） | 无需改动 |
| 数据库 CHECK 约束 | **不做** | ① 部署路径为手工 SQL（`PROJECT.md` Key Decisions: 迁移暂用手动），无 Flyway 回滚/数据清洗；② 存量 `user_threshold` 行可能已违反次序，CHECK 会导致迁移失败；③ 四个可空列的 CHECK 表达冗长。留待"数据库迁移策略"需求单独处理 |
| 判定逻辑一致性 | **后端** 重构 `ThresholdRangeChecker` 为单一 `classify()` | 见下方"已发现缺陷" |

**校验器产出两个属性节点。** 类级约束无法判断"是谁的错"，因此违规消息应同时 `addPropertyNode("warningHigh")` 与 `addPropertyNode("criticalHigh")`（或对应的低值字段对），使两个输入框都获得内联红字，与 Flow 1 的 `fieldErrors` 契约天然对齐。

---

## Recommended Project Structure

```
hhs-backend/src/main/java/com/hhs/
├── common/
│   └── Result.java                              # [MODIFIED] +errorCode +fieldErrors（可选，@JsonInclude(NON_EMPTY)）
├── vo/
│   ├── FieldErrorVO.java                        # [NEW] record(String field, String message, String code)
│   ├── ProfileReadinessVO.java                  # [NEW] completeness + dependencies
│   └── (既有 HealthProfileVO/HealthScoreVO 不变)
├── exception/
│   └── GlobalExceptionHandler.java              # [MODIFIED] 三个 validation 分支改产 fieldErrors；业务分支填 errorCode
├── validation/
│   ├── ValidThresholdRange.java                 # [NEW] @Target(TYPE) 类级约束
│   ├── ThresholdRangeValidator.java             # [NEW] ConstraintValidator<ValidThresholdRange, UserThresholdRequest>
│   └── (既有 ValidMetricRecord / MetricRecordValidator 不变)
├── dto/
│   └── UserThresholdRequest.java                # [MODIFIED] 挂载 @ValidThresholdRange
├── service/
│   ├── ProfileReadinessService.java             # [NEW] 接口
│   ├── impl/
│   │   └── ProfileReadinessServiceImpl.java     # [NEW] 单次读取 + 组装 VO
│   └── domain/
│       ├── ProfileCompletenessCalculator.java   # [NEW] 纯函数，无 Spring 依赖
│       ├── ProfileScorer.java                   # [MODIFIED] 委托 calculator（保持权重不变）
│       └── ThresholdRangeChecker.java           # [MODIFIED] 修复 isWarningRange；抽出 classify()
├── controller/
│   └── PreventionController.java                # [MODIFIED] +GET /profile/readiness
└── (mapper/entity 无改动 —— 不新增表、不新增列)

hhs-frontend-v2/src/
├── types/
│   ├── api.ts                                   # [MODIFIED] ApiResponse +errorCode? +fieldErrors?；新增 FieldError/ProfileReadiness 类型
│   └── axios.d.ts                               # [NEW] 模块增强：AxiosRequestConfig.silent?: boolean
├── utils/
│   ├── request.ts                               # [MODIFIED] 只翻译不展示；silent；Accept-Language
│   ├── appError.ts                              # [NEW] ApiError / normalizeApiError / showError
│   └── thresholdValidation.ts                   # [NEW] 纯函数：次序校验（UX 前置）
├── composables/
│   └── useFormServerErrors.ts                   # [NEW] fieldErrors → el-form-item :error
├── api/
│   └── prevention.ts                            # [MODIFIED] +getReadiness()
├── stores/
│   └── profileReadiness.ts                      # [NEW] Pinia store
├── components/profile/
│   └── ProfileReadinessBanner.vue               # [NEW] 全局横幅
└── views/
    ├── health/Thresholds.vue                    # [MODIFIED] 补 prop 绑定、前端次序校验、字段内联错误
    ├── prevention/Profile.vue                   # [MODIFIED] 展示缺失字段、保存后 fetch readiness、字段内联错误
    ├── prevention/Risk.vue                      # [MODIFIED] 依 store 显示 BLOCKED/DEGRADED，而非"创建评估失败"
    ├── health/Score.vue                         # [MODIFIED] 展示"DEGRADED：档案不完整"提示
    └── components/layout/MainLayout.vue         # [MODIFIED] 在 el-main 顶部挂载 ProfileReadinessBanner
```

### Structure Rationale

- **`validation/` 而非新增 `metadata/` 分包：** `@ValidThresholdRange` 与既有 `@ValidMetricRecord` 同属 Jakarta 约束层，放同一包可直接复用既有测试基建（`MetricRecordValidatorTest` 的 `Validator` 用法）。
- **`service/domain/` 承载纯函数：** `ProfileCompletenessCalculator` 与 `ThresholdRangeChecker` 都是无状态、可脱离 Spring 测试的规则体，符合项目既有 `service/domain/` 约定（`ScoreCalculator`、`RiskScorer` 等）。
- **前端 `utils/appError.ts` 单文件而非 `errors/` 目录：** 当前错误模型只有一个类与两个函数，建立目录属于过度设计；当出现第三方错误适配时再拆。
- **不新增数据库对象：** 三项能力全部可由现有表（`health_profile`、`user_threshold`）与编译期常量推导，避免触发项目尚未建立的迁移机制。

---

## Architectural Patterns

### Pattern 1: Translate-Don't-Display（拦截器只翻译）

**What:** 传输层拦截器负责把异形错误统一为 `ApiError`，展示权完全下放给消费方。
**When to use:** 应用同时存在表单（需要内联）与非表单（需要 toast）两类失败面。
**Trade-offs:** 需要在各调用点做一次迁移，但迁移是**可增量的**——移除拦截器内的 `ElMessage` 会立刻把现状的"三重 toast"降为"单重 toast"，之后逐步替换为 `showError` 提升文案质量。反过来说，若先改调用点再改拦截器，会暂时出现"零反馈"。

**Example:**
```ts
// utils/appError.ts  —— 唯一错误模型
export type ErrorKind = 'validation' | 'auth' | 'network' | 'server' | 'business'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly kind: ErrorKind,
    readonly status?: number,
    readonly code?: string,
    readonly fieldErrors: Record<string, string> = {},
    readonly formField?: string          // 调用方覆盖：血压双请求分字段归属
  ) { super(message) }
}

export function normalizeApiError(raw: unknown): ApiError {
  const err = raw as any
  const data = err?.response?.data
  const status: number | undefined = err?.response?.status

  if (data?.fieldErrors?.length) {
    const map: Record<string, string> = {}
    for (const fe of data.fieldErrors) map[fe.field] = fe.message
    return new ApiError(data.message || '请检查填写内容', 'validation', status, data.errorCode, map)
  }
  if (status === 401) return new ApiError('登录已过期，请重新登录', 'auth', status)
  if (!err?.response) return new ApiError('网络异常，请检查连接后重试', 'network')
  if (status && status >= 500) return new ApiError('服务暂时不可用，请稍后重试', 'server', status)
  if (data?.code && data.code !== 200) {
    return new ApiError(data.message || '操作失败', 'business', status, data.errorCode)
  }
  return new ApiError(data?.message || '请求失败', 'server', status)
}

/** 非表单场景的唯一 toast 出口 */
export function showError(raw: unknown, fallback: string): ApiError {
  const e = normalizeApiError(raw)
  if (e.kind !== 'validation') ElMessage.error(e.message || fallback)
  return e
}
```

### Pattern 2: Field-Error Projection（字段错误投影到表单）

**What:** 后端字段路径 → 表单 `prop` 的直接映射，通过 `el-form-item` 的 `error` 属性实现"服务端红字"，无需 `validateField` 回调。
**When to use:** 表单字段名与 DTO 字段名可稳定对齐（本项目基本满足）。
**Trade-offs:** `el-form-item` 的 `error` 一旦设置会永久显示直到被清空，因此必须在"任何输入变化"与"重新提交"时复位；`FormItem.error` 是 `element-plus` 公开 prop（已在本地类型 `form-item.d.ts:38-40` 确认）。

**Example:**
```ts
// composables/useFormServerErrors.ts
export function useFormServerErrors() {
  const fieldErrors = ref<Record<string, string>>({})
  const summary = ref('')

  function apply(err: ApiError) {
    if (err.kind !== 'validation') { summary.value = err.message; return }
    const next: Record<string, string> = {}
    for (const [field, msg] of Object.entries(err.fieldErrors)) next[field] = msg
    if (err.formField) {                       // 血压：把 value 改投到 diastolicValue
      const moved = next.value
      delete next.value
      if (moved) next[err.formField] = moved
    }
    fieldErrors.value = next
    summary.value = err.message                 // 弹窗顶部 el-alert 用
  }

  function reset() { fieldErrors.value = {}; summary.value = '' }
  return { fieldErrors, summary, apply, reset }
}
```
```vue
<!-- 用法：<el-form-item prop="warningHigh" :error="fieldErrors.warningHigh"> -->
```

### Pattern 3: Derived-Readiness with Single Read（单读派生就绪度）

**What:** 一次读取档案，纯函数派生完整度，静态矩阵派生下游影响。
**When to use:** 多个下游服务共享同一份前置数据，且当前各自重复查询、各自兜底。
**Trade-offs:** 不引入共享缓存/加载器（避免投机抽象）；代价是"评分页"可能同时触发 `ProfileScorer` 与 readiness 各一次档案查询——这是常数（2 次），不是 N+1。

**Example:**
```java
// service/domain/ProfileCompletenessCalculator.java （权重必须与现 ProfileScorer 完全一致）
public ProfileCompleteness calculate(HealthProfile profile) {
    if (profile == null) return ProfileCompleteness.missing();   // score=0, status=MISSING
    int score = 100;
    List<MissingField> missing = new ArrayList<>();
    if (profile.getGender() == null || profile.getBirthDate() == null) { score -= 10; missing.add(...); }
    if (profile.getHeightCm() == null || profile.getWeightKg() == null) { score -= 15; missing.add(...); }
    if (profile.getBmi() == null) { score -= 10; missing.add(...); }
    if (isBlank(profile.getLifestyleHabits())) { score -= 15; missing.add(...); }
    return new ProfileCompleteness(Math.max(0, score), statusOf(missing), missing);
}
```

---

## Validation Rule Homes (汇总表)

| 规则 | 前端 | 后端 | 数据库 | 判定理由 |
|------|------|------|--------|---------|
| 阈值相邻次序 `criticalLow < warningLow < warningHigh < criticalHigh` | ✅ 纯函数（禁用保存 + 内联） | ✅ `@ValidThresholdRange`（权威） | ❌ | API 可被直接调用；手工 SQL 部署无迁移回滚；存量数据可能违规 |
| 阈值可空边界 | ✅（允许留空并跳过比较） | ✅（null 跳过比较） | — | 与既有"null = 无界"语义一致 |
| 阈值非负 | ✅ 输入 `min=0`（已存在） | ✅ `@DecimalMin("0.0")`（已存在） | — | 已有，不重复实现 |
| 档案必填字段（性别/生日） | ✅ 表单 `required`（已存在） | ✅ `@NotNull`/`@Past`（已存在） | — | 已有 |
| 档案完整度权重 | ✅ 展示用（读 VO，不重算） | ✅ 唯一计算方 | — | 避免前后端权重漂移导致"页面说 85 分、评分按 70 算" |
| 依赖门控（报告/风险/评分/建议） | ✅ 依 `dependencies[]` 渲染 | ✅ 唯一产出方 | — | 门控是业务策略，不可由客户端决定 |
| 指标生理极限 | ✅ 四梯度（已存在） | ✅ `@ValidMetricRecord`（已存在） | — | v1.1 已落地，不重复 |

---

## Discovered Defects (必须纳入计划，否则新能力建立在不稳地基上)

### D-1: `ThresholdRangeChecker.isWarningRange` 条件颠倒（HIGH）

**Evidence:** `ThresholdRangeChecker.java:37-45`
```java
return (belowCriticalLow && aboveWarningLow) ||   // v <= criticalLow && v >= warningLow
       (belowWarningHigh && aboveCriticalHigh);   // v <= warningHigh && v >= criticalHigh
```
两侧区间在合法数据下**恒为空**：低侧要求 `warningLow <= criticalLow`（与语义相反），高侧要求 `criticalHigh <= warningHigh`（与语义相反）。
**Consequence:** 介于 `warningHigh` 与 `criticalHigh` 之间的值（如心率 110，warningHigh=100，criticalHigh=120）被 `getSeverityLevel` 判为 `NORMAL`，**告警丢失**。这直接影响 `AlertRuleEngineImpl` → `ThresholdEvaluator.evaluateValue` 的告警生成。
**Fix:** 抽出单一 `classify(value, threshold) → NORMAL | WARNING_LOW | WARNING_HIGH | CRITICAL_LOW | CRITICAL_HIGH`，采用互斥半开区间，保留既有 `<=`/`>=` 约定：
```
CRITICAL_LOW  : v <= criticalLow
WARNING_LOW   : criticalLow < v <= warningLow
NORMAL        : warningLow < v < warningHigh
WARNING_HIGH  : warningHigh <= v < criticalHigh
CRITICAL_HIGH : v >= criticalHigh
```
`isNormalRange/isWarningRange/isCriticalRange/getSeverityLevel` 全部委托 `classify`。
**Blast radius:** 告警从"漏报"变为"正常上报"——这是行为变更，需要专门的单元测试与 UAT 条目（`ThresholdRangeCheckerTest` 目前不存在，须新建）。

### D-2: `Thresholds.vue` 表单项缺少 `prop`（HIGH，阻塞字段级反馈）

**Evidence:** `Thresholds.vue:74-92` 的四个 `el-form-item` 均无 `prop`，因此 `el-form-item :error` 无法注入、`validateField` 无法定位。
**Fix:** 为 `criticalHigh`/`warningHigh`/`warningLow`/`criticalLow` 补 `prop`，加入 `rules`，并接入 `useFormServerErrors`。

### D-3: 业务错误码退化为 500（MEDIUM）

**Evidence:** `Result.java:42-48` + `GlobalExceptionHandler.java:53`。
**Fix:** `Result` 增加 `errorCode` 字符串字段；业务分支改为 `Result.failure(ec.getHttpStatus(), errorCodeStr, message)` 的重载，保持 `code` 数值语义不变。

### D-4: 背景请求失败仍弹 toast（MEDIUM）

**Evidence:** `App.vue:17`、`data-input/Index.vue:132-144` 的 catch 均为静默，但拦截器已弹窗。
**Fix:** `silent: true` 请求配置 + 在元数据/统计/轮询类调用上启用。

### D-5: 风险评分在无档案时产出伪评分（HIGH，需产品确认）

**Evidence:** `RiskAssessmentServiceImpl.java:135-139` 在 `profile == null` 时仍返回糖尿病 30 / 高血压 25 / 心血管 25。
**Fix:** 依赖门控置 `riskAssessment = BLOCKED`，`createAssessment` 抛 `BusinessException(HEALTH_PROFILE_NOT_FOUND, "请先完善健康档案")`；前端 `Risk.vue` 改为引导 CTA。**这是行为变更，需在 roadmap 中单列并标记产品确认点。**

### D-6: 未捕获的 3 处"不可达"错误分支（LOW，清理）

`stores/push.ts:55,75`、`stores/ai.ts:105`、`stores/wellness.ts:64` 在 `try` 内检查 `res.code`，但拦截器已拒绝非 200，故分支不可达。改造后可删除，降低理解成本。

---

## Anti-Patterns

### Anti-Pattern 1: 在拦截器里做展示（现状）

**What people do:** 在 Axios 拦截器中直接 `ElMessage.error`。
**Why it's wrong:** 与调用方 `catch` 中的 toast 叠加，产生 2–3 条同义红条；且拦截器不知道上下文（表单字段、静默请求、批量请求），无法给出正确呈现方式。
**Do this instead:** 拦截器只 reject 结构化 `ApiError`；展示由 `showError`（非表单）与 `useFormServerErrors`（表单）按场景决定。

### Anti-Pattern 2: 把 `errors: ["field: msg"]` 当契约

**What people do:** 后端拼字符串，前端按 `":"` 切割。
**Why it's wrong:** 冒号在中文/英文文案中均可能出现；字段路径含嵌套（`a.b`）；无法携带错误码；无法本地化。
**Do this instead:** `fieldErrors: [{field, message, code?}]`；字符串数组保留一个版本做兼容后删除。

### Anti-Pattern 3: 为完整度另建一套规则

**What people do:** 新增计算器复制 `ProfileScorer` 的 `-10/-15/-10/-15` 权重。
**Why it's wrong:** 两份权重必然漂移，最终出现"横幅说缺 15 分、评分却只扣 10 分"。
**Do this instead:** 让 `ProfileScorer` 委托 `ProfileCompletenessCalculator`，权重只存在一处；并在单测中锁定 `ProfileScorer.calculate` 的输出不变（回归保护）。

### Anti-Pattern 4: 在数据库上加 CHECK 约束来兜底阈值次序

**What people do:** `ALTER TABLE user_threshold ADD CONSTRAINT chk_order CHECK (...)`。
**Why it's wrong:** 项目无迁移框架（手工 SQL），存量违规行会让迁移失败；且四列可空使 CHECK 表达复杂、错误信息对用户不可读。
**Do this instead:** 应用层类级约束 + 显式 `fieldErrors`；数据库清理作为独立需求评估。

---

## Integration Points

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `request.ts` ↔ `appError.ts` | 直接函数调用 | 拦截器只 import `normalizeApiError`，避免循环依赖（`appError` 不得 import store） |
| `appError.ts` ↔ `ElMessage` | 唯一出口 `showError` | 禁止在其它模块直接 `ElMessage.error(serverMessage)`；可在 code review 中用 grep 约束 |
| `useFormServerErrors` ↔ `el-form` | `el-form-item :error` | 需 `prop` 与 DTO 字段同名；血压例外由 `formField` 覆盖 |
| `ProfileReadinessServiceImpl` ↔ `ProfileScorer` | 共享 `ProfileCompletenessCalculator` | `ProfileScorer` 保留 `calculate(Long userId)` 签名以兼容 `ScoreCalculator` 与既有测试 |
| `PreventionController` ↔ `ProfileReadinessService` | 直接服务注入 | 复用既有 `/api/prevention` 前缀与 `@PreAuthorize("isAuthenticated()")` |
| `useProfileReadinessStore` ↔ `MainLayout` | Pinia 订阅 | 横幅单点渲染，`/prevention/profile` 路由上隐藏以免自我指涉 |
| `ThresholdRangeValidator` ↔ `GlobalExceptionHandler` | 约束违规 → `ConstraintViolationException` 或 `MethodArgumentNotValidException` | 类级约束在 `@RequestBody @Valid` 上会进入 `handleMethodArgumentNotValid`（`getFieldErrors` 可能为空，需同时读 `getGlobalErrors`）；**必须处理 `GlobalError` 分支**，否则类级违规会落到兜底 500 |

### External Services

无新增外部依赖。本方案不引入 Maven 依赖、npm 包或新基础设施，符合 `PROJECT.md` 的技术栈约束。

---

## Suggested Build Order (依赖感知)

> 三项能力彼此可并行，但共享 Flow 1 的信封契约。推荐按"契约 → 消费 → 领域"推进。

**Wave 0（无依赖，可先行）**
1. **阈值判定缺陷修复**：`ThresholdRangeChecker.classify()` 重构 + 新建 `ThresholdRangeCheckerTest`（覆盖 `warningHigh` 与 `criticalHigh` 之间、`criticalLow` 与 `warningLow` 之间的分诊，以及 null 边界）。
   - 理由：独立于其它改动，且是阈值能力的地基；先修真错，再谈校验次序。

**Wave 1（契约层）**
2. **后端错误信封**：`Result` + `errorCode`/`fieldErrors`，`FieldErrorVO`，`GlobalExceptionHandler` 三处 validation 分支（含 `GlobalError` 处理）+ `errorCode` 填充。
3. **前端错误模型**：`types/api.ts`、`types/axios.d.ts`、`utils/appError.ts`、`request.ts` 改造（translate-only + `silent` + `Accept-Language`）。
   - 依赖 2 的字段名。

**Wave 2（表单投影，依赖 Wave 1）**
4. `useFormServerErrors` + `UniversalMetricDialog.vue` 接入（含血压 `formField` 归属）。
5. `Thresholds.vue` 补 `prop`/`rules` + 接入。
6. `Profile.vue` 接入；随后按模块分派替换 `catch` 中的裸 `ElMessage.error`（优先 `data-input/*`、`health/Metrics.vue`、`health/Thresholds.vue`、`prevention/*`）。
   - 该步骤可增量交付：Wave 1 完成后"三重 toast"已降为"单重"，替换只提升文案质量。

**Wave 3（阈值关系，依赖 Wave 1 以产出 fieldErrors）**
7. `@ValidThresholdRange` + `ThresholdRangeValidator` + 挂载 + 单测。
8. `utils/thresholdValidation.ts` + `Thresholds.vue` 即时校验与四段区间可视化。

**Wave 4（档案就绪度，可与 Wave 2/3 并行）**
9. `ProfileCompletenessCalculator` + 重构 `ProfileScorer` 委托 + 回归测试（锁定评分不变）。
10. `ProfileReadinessVO` + `ProfileReadinessService(Impl)` + `GET /profile/readiness` + 集成测试。
11. `useProfileReadinessStore` + `ProfileReadinessBanner` + `MainLayout` 挂载。
12. 消费端：`Risk.vue`（BLOCKED CTA）、`Score.vue`（DEGRADED 提示）、`Profile.vue`（缺失字段清单 + 保存后 `fetch()`）。

**Wave 5（收尾）**
13. `RiskAssessmentServiceImpl` 无档案时的 BLOCKED 行为变更（**需产品确认**，单列风险项）。
14. 清理 D-6 不可达分支；全量 `npm run test:run` / `mvn clean verify -DskipITs` 回归。

**关键依赖说明**
- 4/5/6/7 都依赖 2/3；若先做前端，会因信封字段缺失而返工。
- 9 必须先于 10：否则完整度权重会出现第二份实现。
- 13 必须晚于 10/11：前端需先具备展示 BLOCKED 的能力，否则用户只见报错。

---

## Scalability Considerations

| Concern | 当前规模（<10k 用户） | 若增长 |
|---------|----------------------|--------|
| 错误信封体积 | `fieldErrors` 通常 ≤5 条，可忽略 | 若批量子实体校验，需限制条数（如 cap 20） |
| readiness 调用 | 每会话 1 次；单表主键查询 | 可加 Redis `profile:readiness:{userId}`，随档案更新失效 |
| 档案重复读取 | 评分页最多 2 次（`ProfileScorer` + readiness） | 如需合并，引入请求级上下文缓存，而不是全局单例缓存 |
| 阈值校验 | 纯内存比较，O(1) | 无变化 |

---

## Gaps & Open Questions

1. **`@ValidThresholdRange` 的违规是否进入 `getFieldErrors` 还是 `getGlobalErrors`** —— 需在实现时以 `MetricRecordValidatorTest` 的同类模式实测确认；`GlobalExceptionHandler` 必须两个集合都消费（已在上文标注）。
2. **风险评分 BLOCKED 的产品语义** —— 是"拒绝创建"还是"创建但标记为不完整"？本文推荐拒绝，但需产品确认。
3. **错误文案的 i18n 深度** —— 本方案固定 `zh-CN` 并保留 `ErrorCode` 双语能力，不为 DTO 注解建立消息目录；若未来真正需要多语言 UI，再引入 `ValidationMessageCatalog`。
4. **`Thresholds.vue` 的指标下拉目前硬编码 6 项且含 `glucose`/`temperature` 旧别名** —— 与 v1.1 的元数据 SSOT 未对齐。建议在同一阶段顺手切换为 `metricMetaStore.allDefinitions`，但属可选优化，需评估测试影响。
5. **`ThresholdRangeChecker` 修复会改变告警产出** —— 需要新的 UAT 条目，且应确认生产环境历史数据的阈值次序分布（若存量已违规，修复后可能出现大量新告警）。

---

## Sources

| 来源 | 类型 | 置信度 |
|------|------|--------|
| `hhs-frontend-v2/src/utils/request.ts`（拦截器双通道展示） | 第一手代码 | HIGH |
| `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java` | 第一手代码 | HIGH |
| `hhs-backend/src/main/java/com/hhs/common/Result.java` | 第一手代码 | HIGH |
| `hhs-backend/src/main/java/com/hhs/service/domain/ThresholdRangeChecker.java`（D-1） | 第一手代码 | HIGH |
| `hhs-backend/src/main/java/com/hhs/service/domain/ProfileScorer.java` | 第一手代码 | HIGH |
| `hhs-backend/src/main/java/com/hhs/service/impl/RiskAssessmentServiceImpl.java`（D-5） | 第一手代码 | HIGH |
| `hhs-backend/src/main/java/com/hhs/service/impl/{HealthReportServiceImpl,AIChatServiceImpl}.java`、`service/alert/AlertAIAnalysisService.java` | 第一手代码 | HIGH |
| `hhs-backend/src/main/java/com/hhs/dto/{UserThresholdRequest,HealthMetricRequest,HealthProfileRequest}.java` | 第一手代码 | HIGH |
| `hhs-frontend-v2/src/views/health/Thresholds.vue`（D-2） | 第一手代码 | HIGH |
| `hhs-frontend-v2/src/components/metric/UniversalMetricDialog.vue:664-750` | 第一手代码 | HIGH |
| `hhs-frontend-v2/node_modules/element-plus/es/components/form/src/form.vue.d.ts:20,28`、`form-item.d.ts:38-40` | 依赖类型声明（一手） | HIGH |
| `hhs-frontend-v2/src/stores/metricMeta.ts`（store 设计先例） | 第一手代码 | HIGH |
| `.planning/PROJECT.md`（技术栈锁定、手工迁移决策） | 项目权威文档 | HIGH |
| `.planning/ROADMAP.md` / `.planning/research/SUMMARY.md`（v1.1–v1.2 既有决策） | 项目权威文档 | HIGH |

> 本次未使用外部 web 资料：三项问题均为本仓库内部集成决策，第一手代码即可完整判定。research-plan seam 已生成 web 查询项，但在本地代码证据充分的前提下，外部检索只会降低置信度而非提升。

---

*Architecture research for: HHS 统一错误反馈 / 阈值关系校验 / 档案就绪度门控*
*Researched: 2026-11-05*
