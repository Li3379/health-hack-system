# 14-11 迁移台账片段 — data-input 收尾（OcrInput / OcrHistory / DeviceConfigWizard）+ prevention 模块（Metrics / Risk）

本片段记录 plan 14-11 触达的**十五个**裸 toast 调用点，一行一个调用点、绝不按分支拆行（Contract 6.1）：`views/data-input/components/OcrInput.vue` 五行 + `OcrHistory.vue` 五行 + `DeviceConfigWizard.vue` 一行 + `views/prevention/Metrics.vue` 两行 + `Risk.vue` 两行。plan 14-13 会把全部片段合并为 `14-MIGRATION-LEDGER.md` 并对账到迁移前的 87 个调用点。

`file:line` 一列取自迁移前的 commit（`14-RESEARCH.md` §"87 Bare `ElMessage.error` Calls — Classified Inventory"），因此在本片段落盘后源码行号已经漂移 —— 这是台账的设计前提，不是笔误。

**全片段共同前提（`迁移后形态` 一律为 `presentStates-toast`）：** 这五个文件都**不渲染任何 `ErrorState`**（`grep -c 'ErrorState'` 五者均为 0）。按 14-05 确立的呈现通道规则，文件内没有状态块时，每个 `showError` 都必须显式传 `{ presentStates: true }` —— 否则 `forbidden` / `notFound` 会在 `resolveMessage` 之前早退成零 toast，把一次真实的权限失败或「记录已被别人删掉」变成完全静默。五个文件的 `grep -c 'presentStates: true'` 与 `grep -c 'showError('` 逐文件相等（2=2、3=3、1=1、2=2、3=3）。

**两条 `保持` 行是本片段的特殊之处：** `OcrInput.handleFileChange` 里的两条前置校验（大小 / 类型）在**任何请求存在之前**运行，它们校验的是用户刚选中的文件，不是服务端响应。按 Contract 6.2 的判定表，`保持` 要求「有理由」——理由见下表两行。它们是本阶段 4 行残留许可清单中的 2 行，plan 14-13 的检查 ① 以 `(路径, 所在函数, 字面量)` 三元组钉住它们，而不是要求一个本 plan 自己的保留决定就已排除了的零。

## OcrInput.vue（5 行：2 保持 + 1 删除 + 2 迁移）

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `OcrInput.vue:196` | `文件大小不能超过10MB` | *（不适用 —— 无 API 调用）* | **保持** | — | **客户端前置校验，不是 API 失败**。它在 `handleFileChange` 里、在任何请求存在之前运行，校验的是用户刚选中的 `File` 对象的 `size`。硬塞进 `showError` 需要伪造一个 `ApiError` 去满足一个专门用来分类**服务端响应**的签名 —— 那会把「你选的这个文件太大」误报成「服务器拒绝了你的请求」，误导用户去重试。本组件没有任何 `el-form-item`（`grep -c 'el-form-item'` = 0），因此把它改成内联字段错误需要重构整个上传控件，超出本阶段范围。**这是本阶段 4 行残留许可清单的第 1 行**，按三元组 `(OcrInput.vue, handleFileChange, 文件大小不能超过10MB)` 钉住。 | `#12MB 文件：恰好一条提示，且一个识别请求都没发`（双断言：`ElMessage.error` 恰好 1 次 + `request.upload` 零调用）；`#OcrInput 的两条前置校验仍按「函数 + 字面量」原位留在 handleFileChange 内`（源码级，按函数范围而非行号） |
| `OcrInput.vue:202` | `只能上传图片文件` | *（不适用 —— 无 API 调用）* | **保持** | — | 同上第二条。**这是残留许可清单的第 2 行**，三元组 `(OcrInput.vue, handleFileChange, 只能上传图片文件)`。两条字面量在整个文件里各出现恰好一次，`grep -c 'ElMessage.error'` 全文件为 2 —— 即只有这两条，没有第三条混进来。 | `#非图片类型：恰好一条提示，且一个识别请求都没发`（同样双断言）；`#OcrInput 的两条前置校验仍按「函数 + 字面量」原位留在 handleFileChange 内` |
| `OcrInput.vue:261` | `errorMessage`（源自 `error.message`） | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `ocr.uploadFailed` | **面板与 toast 并存，各自保留**。`recognizeResult.status = 'failed'` 是内容区的失败态 —— 面板里留着一个持续可见的「识别失败」标签与失败原因（`el-alert`），toast 确认「这一次动作没完成」。两者生命周期不同、服务的读者需求不同（Contract 3 §3.4），因此**只换文案路径，不删面板赋值**（-research 明确标注 keep that）。面板文案由 `resolveMessage('ocr.uploadFailed', normalized.message)` 从**归一化结果**派生，与 toast 走同一次解析 —— 两处各算一遍就会互相打架（T-14-32）。旧实现读 `error.message` 对 `ApiError` 恰好可用，但会丢掉「后端中文优先」这一层。 | `#识别失败：恰好一条提示，且面板仍是 failed 并带非空错误文案`（三断言：条数 1、`status === 'failed'`、`errorMessage` 非空且 `=== lastErrorToastText()`） |
| `OcrInput.vue:295` | `res.message \|\| '录入失败'` | — | **删除** | — | **不可达分支**。`@/utils/request` 的响应拦截器在信封 `code !== 200` 时直接 `reject`（`request.ts:45-52`），因此 `if (res.code === 200 && res.data) { … } else { … }` 里的 `else` 永远不会执行。删除是保行为的：保留它只会留下第二条永久死掉的呈现路径，后人会「贴心地」把它修回成一条真实 toast。**这是本 plan 的 3 条删除之一**。 | `#三个文件不再有「非 200」的不可达 else 兜底`（源码级：`handleConfirm` 函数体内不含 `} else {`） |
| `OcrInput.vue:299` | `errorMessage`（源自 `error.message`） | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `metric.saveFailed` | **键不上新**：确认录入的语义就是一次指标写入，字典里已有贴切措辞「无法保存指标记录，请检查指标类型与数值」。**失败不清空选中结果、不重置表单**（ERR-05 / Contract 7 §7.1）：面板保持原样，用户可以直接重试。`ElMessage.success`（成功录入 N 个指标）保留。 | `#确认录入失败：恰好一条提示，且选中结果不被清空（ERR-05）`（用调用前后 JSON 快照 `toEqual` 证明零改动） |

## OcrHistory.vue（5 行：2 删除 + 3 迁移）

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `OcrHistory.vue:166` | `加载历史记录失败` | `forbidden`, `notFound`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `ai.loadFailed` | **键复用**：字典无 `ocr.loadFailed`，而「无法加载 AI 会话，请稍后重试」对本表同样贴切（OCR 历史是同一类「识别会话」的表）。新造 `ocr.loadFailed` 会形成一个与 `ai.loadFailed` 近重复的键，让文案维护出现两套同义词汇 —— 这是 RESEARCH §"Dictionary key gaps" 明确要求「add, or explicitly reuse and record it」时的复用选项，理由记录在此。原文案只说失败、不说下一步。**表格保留原有行**（UI Considerations · error E7）：catch 不清空 `historyRecords`。 | `#列表加载失败：一条提示，且表格保留原有行（不清空）`（同一用例断言 `historyRecords` 引用与长度都不变） |
| `OcrHistory.vue:185` | `res.message \|\| '获取详情失败'` | — | **删除** | — | **不可达分支**（拦截器 reject）。**本 plan 的 3 条删除之二**。 | `#三个文件不再有「非 200」的不可达 else 兜底`（源码级：`viewDetail` 不含 `ElMessage.error`） |
| `OcrHistory.vue:188` | `获取详情失败` | `notFound`, `forbidden`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `ai.loadFailed` | **B1 关键点之一**。`OcrServiceImpl:691` 的 `getRecordDetail` 对不存在的记录抛 `RESOURCE_NOT_FOUND`、对外人记录抛 `AUTH_FORBIDDEN`；本文件无状态块，缺第三个参数会让「查看一条已被别人删掉的记录」变成零提示的静默。**键复用** `ai.loadFailed`（一次详情读取就是一次「加载相关内容」）。`detailVisible` 保持 `true`（打开即置位，失败不回滚），列表与分页状态不动（T-14-31）。 | `#查看一条已被删除的 OCR 记录（RESOURCE_NOT_FOUND 信封）：恰好一条提示，弹窗保持打开`（mutation-verified：去掉第三个参数后该用例失败） |
| `OcrHistory.vue:204` | `res.message \|\| '删除失败'` | — | **删除** | — | **不可达分支**（拦截器 reject）。**本 plan 的 3 条删除之三 —— 至此阶段 12 条删除全部落地。** | `#三个文件不再有「非 200」的不可达 else 兜底`（源码级：`handleDelete` 不含 `ElMessage.error`） |
| `OcrHistory.vue:207` | `删除失败` | `notFound`, `forbidden`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `ai.deleteFailed` | **B1 关键点之二**。`OcrServiceImpl:727` 的 `deleteRecord` 对不存在的记录抛 `RESOURCE_NOT_FOUND`（重复删除）、对外人记录抛 `AUTH_FORBIDDEN`。没有 `presentStates` 时这两种都是零提示，而删除恰恰是用户最需要确认「到底删掉没有」的动作。**键复用** `ai.deleteFailed`，不新增 `ocr.deleteFailed`。成功提示「删除成功」与随后 `loadHistory()` 刷新都保留。 | `#删除一条已被别人删掉的 OCR 记录（AUTH_FORBIDDEN 信封）：恰好一条提示，不是静默`（mutation-verified：去掉第三个参数后该用例失败）；`#OcrHistory 复用 ai.* 键而不是新造 ocr.* 近重复键` |

## DeviceConfigWizard.vue（1 行：迁移）

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `DeviceConfigWizard.vue:343` | `加载配置状态失败` | `forbidden`, `notFound`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `device.loadFailed` | 原文案只说失败、不说下一步；字典条目「无法加载设备配置，请点击重试」带上了可执行动作，且与 `DeviceSync` 的三处加载共用同一句话（同一份设备配置语义）。**已核对该向导的状态面板**：`configReady` / `platforms` 是两段 `ref` 驱动的展示，不是错误态容器 —— 失败时它们保持上一次的值不动（不因一次失败而清空，UI Considerations · error E7），错误呈现只能靠 toast，因此 `presentStates` 必需。向导没有第二个错误场景（配置更新成功只有 `ElMessage.success`，保留）。 | 全量套件回归（该组件的加载路径由 `DeviceSyncMigration.spec.ts` 的挂载期调用间接覆盖，不引入噪音）；`#OcrHistory 与 DeviceConfigWizard 的 showError 计数与文件匹配`（源码级 1 = 1） |

## prevention/Metrics.vue（2 行：迁移 + 取消顺序修正）

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `prevention/Metrics.vue:100` | `获取数据失败` | `forbidden`, `notFound`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `metric.loadFailed` | 原文案只说失败、不说下一步；「无法加载指标记录，请点击重试」带上了可执行动作，且与 `health/Metrics.vue:158` 的同类列表共用一句话（保健指标与健康指标是同一个展示口）。**表格保留原有行**（UI Considerations · error E7）：catch 不清空 `metrics`，`pagination.total` 也不动。 | `#列表加载失败：一条提示，且表格保留原有行（不清空）`（`metrics` 引用与长度都不变） |
| `prevention/Metrics.vue:132`（`handleDelete` 的 catch；手写取消比较在 `:131`） | `删除失败` | `notFound`, `forbidden`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `metric.deleteFailed` | **拆分确认与请求，手写取消比较整条消失**（ERR-08 的第九处、也是最后一处）。旧写法 `if (error !== 'cancel' && error !== 'close')` 的两个问题：(a) 对 `AbortError` 与归一化后的 `ApiError('', 'cancel')` 都恒为真 → 真实取消会弹红灯；(b) 第二个哨兵值（关闭对话框）永远不会被 `ElMessageBox.confirm` 产生 → 死代码，而它的存在让阶段级检查 ③ 永远归不了零。拆分后确认框的 catch 是裸 `return`（唯一且刻意的静默，Contract 7 §7.2 第二种合法形态），请求的 catch 才走 `showError`（它已识别全部五种取消形态）。成功提示「删除成功」与 `fetchMetrics()` 刷新保留。 | `#取消删除确认框：零提示，且一个 DELETE 都没发`（双断言：`toastCount()===0` + `deleteMetric` 零调用，取消值用 `AbortError` 而非字符串）；`#确认后 DELETE 失败：恰好一条提示，文案是字典的 metric.deleteFailed` |

## prevention/Risk.vue（2 行：迁移 + 键复用）

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `prevention/Risk.vue:310` | `获取评估列表失败` | `forbidden`, `notFound`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `profile.loadFailed` | **键复用而非新造 `risk.*`**：这是健康档案的「风险」页，页面与 `prevention/Profile.vue` 共享同一个后端聚合（`preventionApi` / `/api/prevention/*`），两者的加载失败是同一个语义。`risk.loadFailed` 会形成一个与 `profile.loadFailed` 近重复的键。**表格保留原有行**（UI Considerations · error E7）：catch 不清空 `assessments`。 | `#评估列表加载失败：恰好一条提示，文案解析到 profile.loadFailed 的字典条文`（逐字断言「无法加载健康档案，请稍后重试」，证明走的是既有键）；`#列表失败保留已有评估行（不清空）` |
| `prevention/Risk.vue:335` | `创建评估失败` | 同上 | `presentStates-toast` | `profile.saveFailed` | **键复用**同上一行（同一个聚合、同一次档案写入的延伸动作）。**不得触碰 plan 13 的交付**：`useFormServerErrors` 接线、`FormErrorSummary`、backfill 面板全部保持原样 —— 本行只换 `createRiskAssessment` 的 catch，`backfillAndCreate` 里的那次调用只是补上 `presentStates`（见下方「同文件既有 showError 的补齐」）。成功提示「评估创建成功」与 `fetchAssessments()` 刷新保留。 | `#风险页复用 profile.* 而非新造 risk.*`（源码级：两处 `showError(...'profile.*'..., { presentStates: true })`，且不含 `'risk.loadFailed'` / `'risk.saveFailed'`）；`#风险页的 useFormServerErrors 接线仍然在` |

## 同文件既有 `showError` 的补齐（不属于 87 行清单，不计入本片段的 15 行）

`prevention/Risk.vue` 的 `backfillAndCreate` catch 里，plan 13 交付的 `showError(error, 'profile.saveFailed')` 本次补上了 `{ presentStates: true }`。理由：plan 13 交付时该页还没有本 plan 的呈现通道口径；本页无状态块，补填保存是**用户主动动作** —— 一次 403（无权限）或 404（档案已被删除）在默认零 toast 下会让用户的点击换不回任何反馈。`validation → 0 toast + 内联 + 汇总` 的既有分流不变（`if (err.kind === 'validation')` 仍在）。这一处是既有调用点的参数补齐，不是新的迁移行，因此不进上表。

## 对账说明（供 14-13 合并时核对）

- **本片段 15 行** = `OcrInput.vue` 5 + `OcrHistory.vue` 5 + `DeviceConfigWizard.vue` 1 + `prevention/Metrics.vue` 2 + `prevention/Risk.vue` 2。
- **阶段总数 87** = 14-04 的 3 + 14-05 的 8 + 14-06 的 9 + 14-07 的 5 + 14-08 的 5 + 14-09 的 12 + 14-10 的 22 + 14-12 的 8 + 本片段 15。
- **删除 3 行**（本 plan）= `OcrInput:295` + `OcrHistory:185` + `OcrHistory:204`。阶段删除总数 **12** = 14-10 的 **8**（`DeviceSync` 3 + `AIInput` 2 + `AiHistory` 2 + 退役 `handleApiError` 1）+ 14-07 的 **1**（`DevicePlatformConfig` save else）+ 本 plan 的 **3**。**至此 12 条不可达分支全部删除。**
- **`保持` 2 行**（本 plan）= `OcrInput:196` + `OcrInput:202`。它们是阶段 4 行残留许可清单的前 2 行（另 2 行属 `user/Profile.vue`）。检查 ① 按 `(路径, 所在函数, 字面量)` 三元组核对，**不按行号**。
- **四次键复用**：`OcrHistory:166/188` → `ai.loadFailed`；`OcrHistory:207` → `ai.deleteFailed`；`Risk:310` → `profile.loadFailed`；`Risk:335` → `profile.saveFailed`。每处理由均在上表写明。
- **未新增任何字典键**，`errorMessages.ts` 无改动（复用日志里的既有注释行也已登记 `prevention/Risk.vue` 与 `OcrHistory` 的复用决定）。
- **五条 `ElMessage.error` 计数迁移后**：`OcrInput` **2**（两条被保留的前置校验）、`OcrHistory` **0**、`DeviceConfigWizard` **0**、`prevention/Metrics` **0**、`prevention/Risk` **0**。`src/views/data-input/` 全模块合计 **2**（down from 33），`src/views/prevention/` 合计 **0**。
- **`ElMessage` import 保留判断**：`OcrInput` 仍有 2 条 `error`（保留的前置校验）+ 1 条 `warning`（至少选一个指标）+ 1 条 `success`（录入成功），因此 import 必需。`OcrHistory` 保留 1 条 `success`（删除成功），`DeviceConfigWizard` 保留 1 条 `success`（配置已更新），`prevention/Metrics` 保留 1 条 `success`（删除成功），`prevention/Risk` 保留 1 条 `success`（评估创建成功）—— 五者都不会触发 `noUnusedLocals`。
- **取消判断阶段级归零**：`grep -rnF "!== 'cancel'" / "!== 'close'" / "=== 'CanceledError'" / "=== 'AbortError'" src --include=*.vue` 全部无匹配。四处历史注释（`goals/Index.vue`、`screening/List.vue`、`mood/MoodTracker.vue`、本 plan 的 `prevention/Metrics.vue`）已改写为散文表述，保留原有解释但不再命中可 grep 的字面量（沿用 14-08 / 14-09 的先例）。