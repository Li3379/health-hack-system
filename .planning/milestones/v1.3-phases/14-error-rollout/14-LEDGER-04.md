# 14-04 迁移台账片段 — S2 内容区 404 与路由级 404

本片段记录 plan 14-04 触达的三个裸 toast 调用点（一行一个调用点，绝不按分支拆行，Contract 6.1）；plan 14-13 会把八份片段合并为 `14-MIGRATION-LEDGER.md` 并对账到 87 个调用点。

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `screening/Detail.vue:152` | `获取报告详情失败` | `notFound`, `forbidden`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `状态块` | `screening.loadFailed` | 一个调用点两种落点：`notFound` 渲染内容区 `ErrorState`（返回列表 + 重新加载该记录，零 toast），其余 kind（含本页无状态块可落的 `forbidden`）落入同一个 catch 的 `presentStates-toast` 调用。按 Contract 6.1 记一行而非两行 —— 这是合计仍为 87 的原因。 | `src/__tests__/views/ScreeningDetailNotFound.spec.ts#404：渲染内容区块、两个有序动作、零 toast，且页头仍在`；`#非 404（传输层 500）：不渲染状态块，且恰好一条 toast`；`#forbidden：不渲染状态块，且恰好一条 toast（证明 fallback 带了 presentStates）` |
| `screening/Compare.vue:82` | `参数错误` | `notFound`（路由状态，非错误分类） | `状态块` | —（未调用 `showError`） | 非法 URL 是**路由状态**，在发出任何请求之前即可从 route 参数判定，不涉及分类也不该发一次注定失败的调用；页级 `ErrorState` 承担呈现，单个 `返回列表` 动作。 | `src/__tests__/views/ScreeningDetailNotFound.spec.ts#非数字 id：页级 notFound 块、单个 返回列表、零 toast，且绝不发请求`；`#缺失 id 参数：同样渲染页级块，且不发请求` |
| `screening/Compare.vue:88` | `获取对比数据失败` | `notFound`, `forbidden`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `screening.loadFailed` | 这是**动作失败**（不是路由状态），且本页对 `forbidden` 没有状态块可落；缺 `presentStates` 会让 403 变成静默失败，因此显式 opt-in 一条常驻 toast。 | `src/__tests__/views/ScreeningDetailNotFound.spec.ts#参数合法但查询 500：不渲染状态块，且恰好一条 toast`；`#参数合法但查询 403：不渲染状态块，且恰好一条 toast（证明 presentStates）` |
