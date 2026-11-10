# 14-12 迁移台账片段 — store 内 8 个错误调用点（AI / wellness / floatingAi）

本片段记录 plan 14-12 触达的**八个** store 内部错误调用点，一行一个调用点、绝不按分支拆行（Contract 6.1）。其中 5 行由 Task 1 落盘（`stores/ai.ts` 四行 + `stores/floatingAi.ts` 一行），3 行由 Task 2 落盘（`stores/wellness.ts` 三行）。plan 14-13 会把全部片段合并为 `14-MIGRATION-LEDGER.md` 并对账到迁移前的 87 个调用点。

`file:line` 一列取自迁移前的 commit（`14-RESEARCH.md` §"Store-Internal 8 Calls (D-02)" 的表），因此在本片段落盘后源码行号已经漂移 —— 这是台账的设计前提，不是笔误。

## 本片段的核心判据：store 不是 UI 层（D-02）

这八行是**唯一**一类「呈现权被移动而不是被删除」的迁移。旧代码在 store 内直接弹 toast，后果有两条：

1. **失败对调用方不可观测。** `getHistory` 在失败时 `return []`，调用方无法区分「历史请求失败」与「这段会话没有历史」；`fetchSummary` 失败后仪表盘继续显示上一次的数据，读起来像一个真实的零值。这是 T-14-36（Repudiation：静默失败）的实质 —— 迁移后「我们不知道它失败了」不再可能。
2. **同一次失败可能被呈现两次。** store 弹一条、页面再渲染一个状态块，就是 UI-SPEC §1.3 明令禁止的「一次失败两条提示」。

因此本片段的 `迁移后形态` 一律为 **`静默`**，但语义是**呈现权转移**：store 只做分类（`normalizeApiError`）或干脆只返回 falsy，`理由` 一列逐行写明**哪个具名调用点**接手呈现。这与 Contract 6.2 对 `静默` 的定义一致 —— 它禁止的是「用户点了保存、请求失败、什么都不说」，而这里每一次失败都有一个在**同一 plan 内**完成接线的调用点。

**逐方法的选择依据是既有返回契约的形状，不是统一规则：**

| 方法 | 旧返回契约 | 决定 | 依据 |
|------|-----------|------|------|
| `loadSessions` / `switchSession` / `getHistory` / `fetchSummary` / `fetchTrend` | `void`（或失败时返回空值） | **throw** | 改签名零成本，且修复一个真实缺陷 |
| `sendMessage`（两处） | `Promise<boolean>` | **保留 `false`** | 两个调用方都已按布尔分支；改成抛错会在事件处理器里变成未处理的拒绝（T-14-37） |
| `deleteSession` | `Promise<boolean>` | **throw** | 它唯一的调用方原本用一个裸 catch 把「删除失败」吞成「用户取消」，必须重写；重写后抛错才让失败可见 |

**三处 `ElMessage.success` 全部保留**（`ai.ts` 会话已删除、`wellness.ts` 添加成功、`push.ts` 推送测试成功）：UI-SPEC §5.1 明确把它们排除在禁令之外，14-RESEARCH 开放问题 1 的 RESOLVED 结论也是「不移动，修正检查②的措辞」。本片段因此**不含**任何 `保持` 行 —— 它们不是被迁移的调用点，而是被明确保留的合法呈现。

## stores/ai.ts（4 行：4 迁移）

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `ai.ts:70` | `加载会话失败` | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | **静默**（改为 `throw normalizeApiError(error)`） | `ai.loadFailed` | **呈现权转移给 `views/ai/Chat.vue` 的 `handleSwitchSession`**（本 plan 新增 try/catch → `showError(error, 'ai.loadFailed', { presentStates: true })`）。`switchSession` 返回 `void`，抛错零成本；而静默会让「切换会话失败」看起来就是「这段会话没有消息」。`presentStates: true` 是必需的：Chat.vue 不渲染 `ErrorState`，不 opt-in 会把 403/404 早退成完全静默。 | `src/__tests__/stores/StoreErrorContract.spec.ts#1. 源码里没有 ElMessage.error…`（源码级）；`ChatSendFailure.spec.ts` 全量回归覆盖 Chat.vue 的三条 `showError` 通道 |
| `ai.ts:105` | `error.message \|\| '发送失败'` | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | **静默**（保留 `return false` + 乐观气泡回滚） | —（不走 `showError`，无字典键） | **呈现权转移给调用点，且刻意不用 toast**：`views/ai/Chat.vue` 的 `handleSendMessage` 在 `false` 时回填 `inputMessage` 并置 `sendFailed`，模板渲染一行「发送未完成，请重试」；`FloatingChatPanel.vue` 同形。**不抛**：两个调用方都读布尔值，抛错会在事件处理器里变成未处理的拒绝。**不加 `ai.sendFailed` 键**（14-RESEARCH 开放问题 2 的 RESOLVED 结论）：一次正在被注视的气泡消失后弹一条转瞬即逝的横幅比静默更糟，而新增键要同步改 46 键计数断言。 | `src/__tests__/views/ChatSendFailure.spec.ts#1. 发送失败：输入文本被回填、就地提示可见、零 toast`；`#2. 重试成功：提示消失…`；`#3. 用户重新编辑输入：提示被撤掉…`；`StoreErrorContract.spec.ts#4. 发送失败：sendMessage 解析为 false、不抛、并回滚乐观气泡` |
| `ai.ts:126` | `删除会话失败` | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | **静默**（改为 `throw normalizeApiError(error)`） | `ai.deleteFailed` | **呈现权转移给 `views/ai/Chat.vue` 的 `handleDeleteSession`**，且该 handler 本 plan 已拆成 confirm-then-api 两段：确认框的 catch 是裸 `return`（唯一且刻意的静默），接口的 catch 走 `showError(error, 'ai.deleteFailed', { presentStates: true })`。**选择 throw 而非保留布尔值**：旧代码返回 `false`，而唯一调用方用一个裸 catch 同时包住接口调用，把真实失败报告成取消 —— 调用方本来就要重写，且「删除失败但会话仍在列表里」是用户能看见的不一致，必须说出来。**成功分支的 `ElMessage.success('会话已删除')` 保留**（§5.1）。 | `ChatSendFailure.spec.ts#4. 取消确认：零提示，且没有发出删除请求`；`#5. 真实删除失败：恰好一条 toast，会话仍留在列表里`；`#6. 删除成功：仍弹那一条 success 确认`；`StoreErrorContract.spec.ts#6. 删除失败：deleteSession 上抛，且不把会话从列表里移除` |
| `ai.ts:137` | `获取历史记录失败` | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | **静默**（改为 `throw normalizeApiError(error)`，不再返回空数组） | `ai.loadFailed` | **呈现权转移给 `views/ai/Chat.vue` 的 `showHistory`**（本 plan 新增 try/catch → `showError(error, 'ai.loadFailed', { presentStates: true })`），并且**只在成功时打开对话框**。旧实现先赋值空数组再无条件 `historyVisible = true`，把「请求失败」渲染成「暂无历史记录」—— 这是本 plan 要消除的第二个静默吞错（T-14-36）。键复用 `ai.loadFailed` 而非新增 `ai.historyFailed`：「无法加载 AI 会话」对历史记录同样贴切，且不动 46 键计数。 | `ChatSendFailure.spec.ts#7. 历史加载失败：一条 toast，且不打开空对话框`；`#8. 历史加载成功：打开对话框、零 toast`；`StoreErrorContract.spec.ts#3. 历史加载失败：getHistory 上抛而不是返回空数组` |

## stores/floatingAi.ts（1 行：1 迁移）

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `floatingAi.ts:120` | `error.message \|\| 'AI 对话失败'` | `business`, `server`, `network`, `forbidden`, `notFound`, `auth`, `validation`, `cancel`, `unknown` | **静默**（保留 `return false` + 乐观气泡回滚，移除 `element-plus` 导入） | —（不走 `showError`，无字典键） | **呈现权转移给 `components/ai-floating-ball/FloatingChatPanel.vue` 的 `handleSend`**，与 `ai.ts` 的发送路径同形（保留布尔值，不抛）。**核实结论（14-RESEARCH 开放问题 6）**：该面板此前**没有**任何内联失败面 —— 消息区只渲染气泡与 typing dots，输入区只有 input + 发送按钮；`handleSend` 已有 `inputText` 回填但无任何可见说明。因此本 plan **新建**一行 `panel-send-failed`（本地 ref + 模板一行，`role="status"`），而不是接进既有元素。`element-plus` 的 `ElMessage` 导入随本次迁移一并移除，文件内 `ElMessage` 计数归零。 | `StoreErrorContract.spec.ts#7. 源码里没有 ElMessage.error，也不再从 element-plus 导入 ElMessage`；`#8. 发送失败：解析为 false、不抛、回滚乐观气泡、零 toast` |

## stores/wellness.ts（3 行：2 迁移 + 1 删除，由 Task 2 落盘）

见下方 Task 2 段落（`fetchSummary` / `fetchTrend` 迁移 + `addMetric` 依证据删除）。

## 本片段的呈现通道规则

`views/ai/Chat.vue` **不渲染**任何 `ErrorState`（`grep -c 'ErrorState'` = 0），因此该文件的每个 `showError` 都必须显式传 `{ presentStates: true }` —— 否则 `forbidden` / `notFound` 会在 `resolveMessage` 之前早退成零 toast，把一次真实的权限失败或「会话已在别的标签页被删掉」变成完全静默。可执行判据是 `grep -c 'presentStates: true'` 与 `grep -c 'showError('` **逐文件相等**（本文件 4 = 4）。

`views/wellness/Dashboard.vue` 是这条规则的**另一半**：它渲染两个 `ErrorState`（摘要块 + 图表区块），因此该文件的 `showError` 计数为 **0** —— 状态块拥有呈现权时绝不叠加 toast。
