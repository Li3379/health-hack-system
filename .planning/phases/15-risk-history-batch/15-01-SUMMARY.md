---
plan: 15-01
status: complete
commit: 04d4119
completed: 2026-11-18
---

# 15-01 SUMMARY — 批次折叠端到端贯通

## 交付

`Risk.vue` 的历史区域从「9 行平铺表格」改为「一次评估一张可折叠卡片」。

- `batches` computed：按 `batchId` 分组，无 `batchId` 的存量记录归入
  `legacy-<createTime>`。保持后端 `createTime DESC` 的插入序，**不重排** ——
  否则与后端顺序分歧会让「最近批次」变成随机批次。
- `activeBatchNames`（`string[]`）驱动 `el-collapse`，非 accordion：多个批次可同时
  展开。默认只展开最近一个；在 `fetchAssessments` 成功末尾同步，使创建评估后新
  批次自动展开。
- `BatchGroup` 含 `worstLevel` / `hasDetail` / `isLegacy`；`hasDetail` 为 false 时
  渲染「该记录创建时未保存评分明细」且不渲染明细列表。
- 空态 `暂无风险评估记录`，不残留空表格。

## 测试

两个 spec 补齐 `el-collapse` / `el-collapse-item` / `el-empty` stub。
`el-collapse-item` 的 stub **必须渲染 `#title` 插槽** —— 未 stub 时 EP 组件渲染成
未知元素并丢弃默认插槽，批次内容静默消失而所有既有断言照旧通过。这是 CONTEXT
点名的本阶段最危险失败模式，实测确实复现（22 个既有测试全绿、功能却在测试里不可见）。

## 关键实现说明

- `LEVEL_RANK` 只做「谁更严重」的排序，**不重复后端的 60/35 分数阈值**：等级是后端
  算好的结论，前端只负责排序与展示。
- 新增样式一律用 `var(--border)`；既有 8 处 `var(--border-1)` 误用（全站未定义、
  实际不渲染）按 CONTEXT 要求**原样不动**。
- 用到 `--error` / `--success` / `--hhs-color-warning-tint` / `--warning-700`，
  均先核对 `theme.css` 与文件内既有用法后选用，未引入新 token。

## 偏差

无。范围严格限定在计划声明的 3 个文件内。
