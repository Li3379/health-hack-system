# Phase 11: 全站统一友好错误反馈地基 - Context

**Gathered:** 2026-11-05
**Status:** Ready for planning

<domain>
## Phase Boundary

本阶段交付**错误基础设施**，不是逐页迁移。具体范围：

1. 前端错误层重构：`request.ts` 从"拦截器直接弹红"改为"只翻译并抛出类型化 `ApiError`"，并新增统一呈现层（`showError` / `useFormServerErrors` / 错误状态块）与扁平文案字典
2. 后端错误信封结构化：`Result<T>` 增加可选 `errorCode` 与 `fieldErrors`，`GlobalExceptionHandler` 三条分支统一产出，并保留既有 `code` 数值语义不变
3. 后端英文校验消息清理：DTO 注解、`BusinessException` 字面量改为中文
4. 静默请求、取消操作、并发 401 的正确分类

**不在本阶段**：逐页替换 105 处 `ElMessage.error` 调用点（Phase 14）、字段错误内联到具体业务表单（Phase 14 的 ERR-03）、阈值与档案功能（Phase 12/13）。

</domain>

<decisions>
## Implementation Decisions

### 错误呈现落点
- **D-01:** 字段校验失败采用 GOV.UK 双呈现：弹窗/表单顶部常驻 `el-alert` 汇总全部字段错误，逐条可点击聚焦到对应输入框；每条错误同时在字段下方内联显示；用户修正后汇总与内联同步消失 — **Reversibility:** costly — 该契约一旦被 105 个调用点依赖，改回 toast 模式需要重写所有调用点
- **D-02:** 页面级加载失败（列表拉取、详情加载）使用**错误状态块**（图标 + 原因文案 + 重试按钮）占据内容区；toast 只用于瞬时动作失败（保存/删除/导出等）。两者分工固定，不混用
- **D-03:** 网络错误与 5xx 错误的 toast 内带"重试"动作按钮，`showError` 接受重试回调并在点击时重新发起同一请求；不实现自动重试（避免写操作重复提交）
- **D-04:** 全页表单（健康档案、阈值页）与弹窗表单复用同一套机制 — 同一个 `el-alert` 汇总 + 字段内联 + `useFormServerErrors`，不做两套实现

### 文案字典归属
- **D-05:** 文案**后端优先**：后端下发完整中文消息，前端只在后端未提供时用本地字典回退 — **Reversibility:** one-way — 前端 105 处调用点的文案来源一旦确立，回头改由前端主导需要重写全部调用点与后端错误码契约
- **D-06:** 前端字典为**扁平 key → 中文文案**结构（如 `threshold.saveFailed` / `profile.loadFailed`），不按模块嵌套
- **D-07:** 该字典**兼任场景回退表**：后端无具体文案时，使用调用点传入的场景 key 对应的默认文案，而非统一"操作未完成"
- **D-08:** 前端**解析后端错误码并据此决定呈现方式**（如 `RESOURCE_ALREADY_EXISTS` 自动配置"打开编辑"恢复动作）；但文案仍取后端消息，不由前端码表覆盖

### 静默请求边界
- **D-09:** 静默由**调用点显式标记** `{ silent: true }`（通过 `AxiosRequestConfig` 模块增强），不存在 URL 模式自动判定表；默认所有请求都会提示
- **D-10:** `silent` 请求失败时**仍然 reject**，由调用方决定降级（如元数据用本地缓存、未读数显示 0），绝不 resolve 空值来掩盖失败
- **D-11:** 并发 401 使用**闩锁**：首条弹一次中文提示并 `authStore.logout()`，其余并发 401 静默 reject，避免多条提示与多次跳转
- **D-12:** 取消操作**统一识别并完全忽略**：字符串哨兵 `'cancel'` / `'close'`、`AbortError`、`CanceledError`、`ERR_CANCELED` 均不产生任何提示

### 后端英文治理
- **D-13:** 直接改写 DTO 注解消息与 `BusinessException` 字面量为中文，**不引入** `messages.properties` / `MessageSource` i18n 体系
- **D-14:** `request.ts` 固定发送 `Accept-Language: zh-CN`；后端 `resolveLanguage` 保持现有 en 分支不变
- **D-15:** 字段**标签翻译在前端**：后端只返回字段名（如 `warningHigh`），前端维护字段名 → 中文标签映射
- **D-16:** 既有英文 `BusinessException` 字面量（如 `Threshold already exists for this metric`）在**本阶段一并改为中文**，不推迟到 Phase 14

### Claude's Discretion
- `ApiError` 的具体字段命名与 `kind` 枚举取值（建议 `validation` / `business` / `auth` / `network` / `server` / `unknown`）
- 错误状态块的组件命名与图标选择（建议 `ErrorState.vue`，复用 Element Plus 图标）
- 扁平字典的 key 命名规范细节（建议 `<模块>.<动作><结果>` 驼峰式，如 `threshold.saveFailed`）
- 重复提示合并窗口的具体毫秒数（建议 1500–3000ms，与 `ElMessage` 的 `grouping` 协同）
- 401 闩锁的重置时机（建议登录成功后重置）

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 错误层设计与契约
- `.planning/research/ARCHITECTURE.md` — Flow 1 完整错误链路设计（两条失败通道、`ApiError` 契约、`silent` 语义、字段归属、401 闩锁）；Flow 3 同类校验归属模式
- `.planning/research/SUMMARY.md` — v1.3 总体架构结论与"翻译而非展示"原则
- `.planning/research/PITFALLS.md` — 19 个陷阱，其中 Phase 11 相关：拦截器吞错误破坏调用方契约、取消操作误报、英文文案外露、重复 toast

### 需求与阶段定义
- `.planning/REQUIREMENTS.md` §ERR — ERR-01/02/04/06 为本阶段交付项；ERR-03/05/07/08 属 Phase 14
- `.planning/ROADMAP.md` §Phase 11 — 5 条成功标准（一次失败一条提示、取消无提示、静默请求无噪音、并发 401 单次处理、重复提示合并）

### 现有实现（必读）
- `hhs-frontend-v2/src/utils/request.ts` — 待改造的拦截器（当前在 38-56 行双重弹窗）
- `hhs-frontend-v2/src/types/api.ts` — `ApiResponse` 缺 `errors`/`errorCode` 字段
- `hhs-backend/src/main/java/com/hhs/common/Result.java` — 响应信封，需增 `errorCode` / `fieldErrors` 且保持 `code` 数值语义
- `hhs-backend/src/main/java/com/hhs/common/constant/ErrorCode.java` — 已有 zh/en 双文案枚举，本阶段复用不重建
- `hhs-backend/src/main/java/com/hhs/exception/GlobalExceptionHandler.java` — 三条分支（BusinessException / MethodArgumentNotValid / ConstraintViolation）需统一产 `fieldErrors`
- `hhs-backend/src/main/java/com/hhs/dto/UserThresholdRequest.java` — 英文注解消息典型样本

### 平台约束
- `hhs-frontend-v2/src/App.vue` §onMounted — `metricMetaStore.fetchMetadata()` 是首个需要标 `silent` 的启动期请求

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ElMessage`(`grouping` / `duration: 0`)** — Element Plus 已内置重复合并与不自动关闭能力，无需自建去重器
- **`ErrorCode` 枚举** — 已有完整 zh/en 消息与 HTTP 状态，是后端文案的既有权威源，只需暴露 `errorCode` 字符串到信封
- **`useMetricMetaStore`** — 已验证"本地兜底 + 后台静默拉取"的降级模式，可作 `silent` 的标准范例
- **`stores/alert.ts` / `stores/ai.ts` / `stores/ai.ts`** — 已有大量"catch 后静默"调用点，是 `silent` 标记的首批改造对象

### Established Patterns
- **两套失败通道**：`MethodArgumentNotValidException` 走 HTTP 400（Axios error 分支）；`BusinessException` 走 HTTP 200 + `code != 200`（Axios response 分支）。`normalizeApiError` 必须同时处理两条
- **`Result.failure(String, String)` 会把字符串错误码降级为 500**（`Result.java:42-48` 的 `parseCodeToInt` 回退）——新增 `errorCode` 字段后前端可精确分支，同时不改 `code` 以避免破坏 `PushConfigControllerTest` 等既有断言
- **响应式样式**：`el-form-item :error` 可编程设置字段错误（Element Plus 已支持），是内联回填的官方入口
- **测试基线**：13 个 spec 文件、90 项用例；vitest 4.1；改动 `request.ts` 会波及所有 mock 该模块的测试

### Integration Points
- `request.ts` 响应拦截器（response + error 两个分支）
- `types/api.ts` 的 `ApiResponse` 接口
- 新增 `types/axios.d.ts` 模块增强（`silent`）
- 新增 `utils/appError.ts`（`ApiError` / `normalizeApiError`）
- 新增 `utils/errorMessages.ts`（扁平字典 + 字段标签映射）
- 新增 `composables/useFormServerErrors.ts`
- 新增 `components/common/ErrorState.vue`
- 后端 `Result.java` / `GlobalExceptionHandler.java` / `vo/FieldErrorVO.java`（新）
- `App.vue` 的 `fetchMetadata()` 调用点

</code_context>

<specifics>
## Specific Ideas

- 用户明确要求"一提交就报红"必须消失，且**一次失败只出现一次提示**（原始诉求）
- 用户要求"合理友善的提示信息"而非红色错误 — 文案要说明"什么失败了 + 下一步怎么做"
- 用户要求"多个地方自行排查问题存在出处"，故本阶段先建地基，Phase 14 做全站清点，避免逐页改造时重新引入重复 toast
- GOV.UK 模式被采纳为字段错误的权威参考（顶部汇总 + 字段内联双呈现）
- toast 带重试按钮被采纳，但不做自动重试（防写操作重复提交）

</specifics>

<deferred>
## Deferred Ideas

- **逐页替换 105 处 `ElMessage.error`** — 属 Phase 14（ERR-03/05/07/08），本阶段只建机制与样本
- **错误摘要中每条自动聚焦字段的滚动定位细节** — Phase 14 与具体表单一起打磨
- **为 403/404 设计专门的解释性状态页** — Phase 14（ERR-07）
- **按错误码自动生成恢复动作按钮的完整清单** — 本阶段只做 `RESOURCE_ALREADY_EXISTS` 一个样本，其余随 Phase 14 扩充
- **前后端共享一份文案（BFF 下发字典）** — 已明确否决（D-05 后端优先 + D-13 不做 i18n 体系），如需多语言属新里程碑

</deferred>

---

*Phase: 11-全站统一友好错误反馈地基*
*Context gathered: 2026-11-05*