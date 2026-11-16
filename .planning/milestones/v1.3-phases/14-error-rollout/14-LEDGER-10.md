# 14-10 迁移台账片段 — data-input 模块（DeviceSync / AIInput / AiHistory）

本片段记录 plan 14-10 触达的**二十二个**裸 toast 调用点，一行一个调用点、绝不按分支拆行（Contract 6.1）：`views/data-input/components/DeviceSync.vue` 十一行 + `AIInput.vue` 六行 + `AiHistory.vue` 五行。plan 14-13 会把全部片段合并为 `14-MIGRATION-LEDGER.md` 并对账到迁移前的 87 个调用点。

`file:line` 一列取自迁移前的 commit（`14-RESEARCH.md` §"87 Bare `ElMessage.error` Calls — Classified Inventory" 的 `data-input/` 表），因此在本片段落盘后源码行号已经漂移 —— 这是台账的设计前提，不是笔误。

**全片段共同前提（`迁移后形态` 一律为 `presentStates-toast`）：** 这三个文件都**不渲染任何 `ErrorState`**（`grep -c 'ErrorState'` 三者均为 0）。按 14-05 确立的呈现通道规则，文件内没有状态块时，每个 `showError` 都必须显式传 `{ presentStates: true }` —— 否则 `forbidden` / `notFound` 会在 `resolveMessage` 之前早退成零 toast，把一次真实的权限失败或「记录已被别人删掉」变成完全静默。三个文件的 `grep -c 'presentStates: true'` 与 `grep -c 'showError('` 逐文件相等（6=6、2=2、3=3）。

## DeviceSync.vue（11 行：3 删除 + 8 迁移）

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `DeviceSync.vue:496` | `res.message \|\| '连接失败'` | — | **删除** | — | **不可达分支**。`@/utils/request` 的响应拦截器在信封 `code !== 200` 时直接 `reject`（`request.ts:45-52`），因此 `if (res.code === 200 && res.data) { … } else { … }` 里的 `else` 永远不会执行。删除是保行为的：保留它只会留下第二条永久死掉的呈现路径，后人会「贴心地」把它修回成一条真实 toast，且残留 grep 永远归不了零。**同一 handler 里 `:492` 的 `else if (res.code === 400 && …'未配置')` 保留** —— 那条可达，且驱动 `showConfigWizard`。 | `src/__tests__/views/DeviceSyncMigration.spec.ts#不再有「非 200」兜底呈现：那个 else 在拦截器 reject 之后不可达`（源码级）；`#连接受阻且无 response：即使页面 isOnline 为 false，仍只有一条提示`（可达路径仍恰好一条） |
| `DeviceSync.vue:501` | `offlineMessage` | `network` | `presentStates-toast` | `device.loadFailed` | **断网分支合并**。旧代码用 `isOnline.value \|\| error.message?.includes('Network Error')` 自选文案，等于给「是否断网」造了第二个真值源。`normalizeApiError` 已把「无 `response` 且 `isAxiosError`」归为 `network` 并置 `retryable`，`showError` 因此自动挂「重试」按钮，严格优于一条静态字符串。与下一行共用同一个 `showError` 调用。 | `#连接受阻且无 response：即使页面 isOnline 为 false，仍只有一条提示`（把 `isOnline` 置 false 后再调用，条数仍为 1 —— 这是「分支被删掉」而非「被重排」的证据） |
| `DeviceSync.vue:503` | `error.message \|\| '连接失败'` | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `device.loadFailed` | 与上一行合并成一次 `showError(error, 'device.loadFailed', { presentStates: true })`。后端中文优先（D-05），字典兜底「无法加载设备配置，请点击重试」。`presentStates` 是必需的：连接是用户主动发起的动作，本页没有状态块承接 403/404。 | 同上 |
| `DeviceSync.vue:528` | `res.message \|\| '断开失败'` | — | **删除** | — | **不可达分支**，与 `:496` 同一理由（拦截器 reject）。删掉后 handler 的 `if (res.code === 200)` 直接接 `catch`。**同时修复同一个 handler 里的静默吞错**：旧代码用一个 `catch { /* User cancelled */ }` 同时包住确认框与接口调用，把接口的真实失败当成「用户取消」吞掉。拆分后确认框的 `catch` 是裸 `return`（唯一且刻意的静默），接口的 `catch` 走 `showError`。 | `#断开连接的确认框被取消：零提示，且没有发出请求`；`#断开连接的真实失败不再被当成「用户取消」：恰好一条提示`（后者对旧的单 `try` 形态必然失败 —— 旧代码会静默）；`#不再有吞掉真实失败的「User cancelled」catch`（源码级） |
| `DeviceSync.vue:556` | `data.errorMessage \|\| '同步失败'` | `business` | `presentStates-toast` | `device.syncFailed` | **不是 HTTP 失败**（research 特别标注）：这一次 HTTP 调用**成功**，是响应体里的 `data.status !== 'success'` 报告同步失败。直接把原始对象交给 `showError` 会被归一化成 `unknown`（无 `response`），因此显式构造 `new ApiError(data.errorMessage \|\| '同步失败', 'business')`。**面板与 toast 并存是刻意的**：`lastSyncError` 是「这台设备上次同步结果」的内容区状态（带按设备重试、关闭前一直留着），toast 确认「这一次动作没完成」；两者生命周期不同，服务不同的读者需求（Contract 3 §3.4）。若评审认定重复，正确的收敛是去掉 toast（面板已带重试），而不是去掉面板。 | `#同步失败：恰好一条提示，且按设备面板同时被填充（面板与 toast 并存）`（同一用例里两条断言：`errorToastCount()===1` 与 `lastSyncError` 的 platform/title/message 全部就位） |
| `DeviceSync.vue:565` | `res.message \|\| '同步失败'` | — | **删除** | — | **不可达分支**（外层 `else`，拦截器 reject）。注意它的函数体里还有一条 `message: res.message \|\| '同步失败'` 的面板赋值 —— 那一行随分支一并删除，因此本文件的 `res.message` 计数从 5 降到 1（仅剩 `:487` 那条可达的 400-未配置判断），**降幅是 4 而不是 3**。 | 源码级断言覆盖（`grep -c 'res.message'` = 1） |
| `DeviceSync.vue:576` | `offlineMessage` | `network` | `presentStates-toast` | `device.syncFailed` | **断网分支合并**，与 `:501` 同一理由。合并后 catch 里只剩一次 `showError`；面板标题仍区分网络类失败，但依据换成**归一化后的 `kind === 'network'`**，不再看 `isOnline` 与 message 字符串。 | `#同步失败：恰好一条提示，且按设备面板同时被填充`（面板 message 取的是 `ApiError.message`，即后端/归一化文案，不再是 `offlineMessage` 常量） |
| `DeviceSync.vue:583` | `error.message \|\| '同步失败'` | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `device.syncFailed` | 与上一行合并成一次 `showError`，且**复用它的返回值**填充面板（`const apiError = showError(...)`），避免为面板再解析一遍错误。`presentStates` 必需：同步是动作型调用，本页无状态块。 | 同上；`#同步失败：恰好一条提示，且按设备面板同时被填充` |
| `DeviceSync.vue:629` | `offlineMessage` | `network` | `presentStates-toast` | `device.syncFailed` | **断网分支合并**（`handleSyncAll`）。「同步全部」的失败单位是整批，不写面板 —— 面板由单个设备的 `handleSync`/`handleSyncAll` 的 `failedCount` 分支负责（那条是 200 成功响应里的业务失败，走 `ElMessage.warning`，不是错误呈现）。 | 全量套件回归（`#同步失败…` 覆盖同一 `showError` 契约；`handleSyncAll` 路径由 `DevicePlatformConfigForbidden.spec.ts` 的 `syncAll` 挂载期调用守护不得引入噪音） |
| `DeviceSync.vue:631` | `error.message \|\| '同步失败'` | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `device.syncFailed` | 与上一行合并。旧代码在这里弹「同步失败」而页面无任何状态变化 —— 用户读完 toast 就失去线索；现在仍是一条可重试的常驻 toast（network/server 自动带「重试」）。 | 同上 |
| `DeviceSync.vue:655` | `加载同步历史失败` | `server`, `network`, `business`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `device.loadFailed` | **键复用**：字典没有 `device.historyFailed`，而「无法加载设备配置，请点击重试」对同步历史同样贴切（历史是配置页的一块）。原文案只说失败、不说下一步，换键后带上了可执行动作。**表格保留原有行**（UI Considerations · error E7）：catch 里不清空 `syncHistory`。 | `src/__tests__/views/DeviceSyncMigration.spec.ts` 全量套件回归；UI Considerations · error E7 在 SUMMARY 的 coverage 块里以源码行引用 |

## AIInput.vue（6 行：2 删除 + 1 退役 + 3 折叠）

`AIInput.vue` 的六行里有四行属于同一个被退役的私有helper `handleApiError`（`:247`、`:251`、`:256`、`:258`）。阶段级 DEAD 对账把它算作**一个聚合行**（RESEARCH 的桶统计口径），因此本文件对 12 的贡献是 **2 + 1 = 3** 行删除，其余三行是折叠进替换调用的转换行。helper 退役后 `grep -c 'handleApiError'` 与 `grep -c 'error.response'` 均为 0。

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `AIInput.vue:194` | `res.message \|\| '解析失败'` | — | **删除** | — | **不可达分支**。拦截器在信封 `code !== 200` 时 reject（`request.ts:45-52`），`if (res.code === 200 && res.data)` 的 `else` 永远不到。删除保行为，且避免留下一条会被后人「修回来」的死呈现路径。 | `src/__tests__/views/AiParseMigration.spec.ts#不再有裸的错误 toast，也不再有没有非 200 兜底的分支`（源码级，`res.code !== 200` 缺席）；`#解析失败且后端下发中文：toast 里是后端消息，不是字典兜底`（可达路径一条） |
| `AIInput.vue:231` | `res.message \|\| '录入失败'` | — | **删除** | — | **不可达分支**，同上。 | `#确认录入失败：恰好一条提示，成功提示只在成功路径出现` |
| `AIInput.vue:247` | `axiosError.response.data.message` | *（整行退役）* | **删除（helper 退役 · 聚合行）** | — | **死代码退役**。`normalizeApiError` 返回 `ApiError` 实例，其字段只有 `message/kind/status/code/fieldErrors/formField/retryable`（`appError.ts:16-39`），**没有 `response` 属性**。因此 `'response' in error` 恒为假，整段 peek 一次都没生效过，后端消息从未被读到。改为 `showError` 后后端 message 首次真正优先（D-05）—— 文案因此变好而非变差。**这是阶段 DEAD 桶里那第 12 行。** | `#私有 handleApiError 已不存在`；`#不再读 error.response（ApiError 没有该属性，这条 peek 恒为 undefined）`；`#解析失败且后端下发中文：toast 里是后端消息，不是字典兜底`（对旧实现必然失败） |
| `AIInput.vue:251` | `请求过于频繁，请稍后再试`（`status === 429`） | `server` | `presentStates-toast` | `ai.parseFailed` | **429 特例退役**。429 有一个真实 status，落到归一化第 7 步 → `server` 且 `retryable: true`，`showError` 因此自动挂「重试」按钮 —— 严格优于一条静态字符串（用户点一下就能重发，而不是读一句「稍后再试」然后自己猜多久）。后端本就下发中文，重放静态句只会覆盖更好的文案。 | `#解析被 429 拒绝：恰好一条提示，且文案不是被退役的「请求过于频繁」静态句`（同一用例断言 `!== RETIRED_429_COPY` 且等于字典的 `ai.parseFailed`） |
| `AIInput.vue:256` | `error.message \|\| defaultMessage` | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `ai.parseFailed` / `metric.saveFailed` | **折叠进替换调用**。原 helper 的两个调用点各带一个默认文案（「解析失败」/「录入失败」），因此退役后用两个不同的 key 取代一个 `defaultMessage` 参数：解析路径 `ai.parseFailed`、确认路径 `metric.saveFailed`。**不新增键**：录入的语义就是一次指标写入，字典里已有贴切措辞。 | `#解析失败且后端下发中文…`（`ai.parseFailed` 的字典回退在 429 用例里被断言）；`#确认录入失败：恰好一条提示…`（后端消息优先） |
| `AIInput.vue:258` | `defaultMessage` | 同上 | `presentStates-toast` | `ai.parseFailed` / `metric.saveFailed` | 与上一行同一次替换的兜底半边 —— `showError` 内部用 `resolveMessage(fallbackKey, err.message)` 同时承担了「有后端消息用后端、没有用字典」两层判断，因此原 helper 的 `if/else` 两层在这里塌成一次调用。`presentStates: true` 必需：本文件无状态块，解析/录入都是动作型调用。 | 同上 |

## AiHistory.vue（5 行：2 删除 + 3 迁移）

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `AiHistory.vue:155` | `加载历史记录失败` | `forbidden`, `notFound`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `ai.loadFailed` | **键复用**：字典无 `ai.historyFailed`，「无法加载 AI 会话，请稍后重试」对本表同样贴切（历史是本模块的一块）。原文案只说失败、不说下一步。**表格保留原有行**（UI Considerations · error E7）：catch 不清空 `historyRecords`。 | `#列表加载失败：一条提示，且表格保留原有行（不清空）`（同一用例断言 `historyRecords` 引用与长度都不变） |
| `AiHistory.vue:174` | `res.message \|\| '获取详情失败'` | — | **删除** | — | **不可达分支**（拦截器 reject）。 | `#不再有「非 200」兜底呈现（拦截器 reject 之后不可达）` |
| `AiHistory.vue:177` | `获取详情失败` | `notFound`, `forbidden`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `ai.loadFailed` | **B1 关键点之一**。`AiParseServiceImpl:749` 的 `getHistoryDetail` 抛 `RESOURCE_NOT_FOUND`、`:776` 抛 `AUTH_FORBIDDEN`；本文件无状态块，缺第三个参数会让「查看一条已被别人删掉的记录」变成零提示的静默。**弹窗内不做 404 内容块（刻意推迟）**：这个弹窗只有 600px 宽，480px 的内容块会占满它并挤掉用户正在看的详情；弹窗内的内容块是独立后续项，不在这里顺手实现。**键复用** `ai.loadFailed`（一次详情读取就是一次「加载 AI 相关内容」）。`detailData` 在打开时先清空是本函数既有次序，保留原样；失败时列表与分页状态不动（T-14-31）。 | `#查看一条已被删除的记录（RESOURCE_NOT_FOUND 信封）：恰好一条提示，弹窗保持打开`（mutation-verified：去掉第三个参数后该用例失败）；`#成功提示与详情键复用都还在` |
| `AiHistory.vue:193` | `res.message \|\| '删除失败'` | — | **删除** | — | **不可达分支**（拦截器 reject）。 | `#不再有裸的错误 toast` |
| `AiHistory.vue:196` | `删除失败` | `notFound`, `forbidden`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `ai.deleteFailed` | **B1 关键点之二**。`AiParseServiceImpl:783` 的 `deleteHistory` 抛 `RESOURCE_NOT_FOUND`（重复删除）、`:776` 抛 `AUTH_FORBIDDEN`（删他人记录）。没有 `presentStates` 时这两种都是零提示，而删除恰恰是用户最需要确认「到底删掉没有」的动作。**不新增键**：字典已有 `ai.deleteFailed`。成功提示「删除成功」与随后 `loadHistory()` 刷新都保留。 | `#删除一条已被别人删掉的记录（AUTH_FORBIDDEN 信封）：恰好一条提示，不是静默`（mutation-verified：去掉第三个参数后该用例失败）；`#成功提示与详情键复用都还在` |

## 对账说明（供 14-13 合并时核对）

- **本片段 22 行** = `DeviceSync.vue` 11 + `AIInput.vue` 6 + `AiHistory.vue` 5。
- **删除 8 行** = `DeviceSync` **3**（`:496`、`:528`、`:565`）+ `AIInput` **2**（`:194`、`:231`）+ `AiHistory` **2**（`:174`、`:193`）+ 退役 helper **1**（`AIInput:247` 聚合行）。阶段总计 = 8（本 plan）+ 1（14-07 的 `DevicePlatformConfig` save else）+ 3（14-11 的 `OcrInput` 1 + `OcrHistory` 2）= **12**，与 RESEARCH 的 DEAD 桶一致。
- **一条静默吞错的修复**：`DeviceSync.vue:530` 的裸 `catch { /* User cancelled */ }`（RESEARCH 的 Contract 6 检查 ③ 抓不到的那类）。拆成「确认的 catch 静默 + 请求的 catch 走 showError」。
- **两条刻意保留的重复呈现**：`DeviceSync` 的 `lastSyncError` 面板 + toast（同步失败）；`DeviceSync` 的 `ElMessage.warning`（同步全部的部分失败）+ 面板。前者理由见 `:556` 行；后者是**部分成功**而非失败 —— warning 陈述「成功 N 失败 M」，面板指出是哪台设备，两者不重复。
- **三条键复用**：`AiHistory:177`（详情）复用 `ai.loadFailed`；`AiHistory:155`（列表）复用 `ai.loadFailed`；`DeviceSync:655`（同步历史）复用 `device.loadFailed`。每处都在 `理由` 里写明。
- **未新增任何字典键**，`errorMessages.ts` 无改动。
- 三个文件迁移后均无 `ElMessage.error`（`grep -c` 全为 0），`ElMessage.success` 计数不变（DeviceSync 4、AIInput 1、AiHistory 1）。
- `AIInput` 的 `ElMessage` import **必须保留** —— 它有两条 `warning`（次数用完 / 未选指标）与一条 `success`（录入成功），不是只在错误路径用到，因此不会触发 `noUnusedLocals`。

