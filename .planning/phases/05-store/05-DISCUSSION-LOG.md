# Phase 5: 前端元数据 Store 与全站格式化防英文外露 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-04  
**Phase:** 5-前端元数据 Store 与全站格式化防英文外露  
**Areas discussed:** Store 加载时机与离线兜底, 全站格式化升级与兼容策略, 未知 Key 优雅降级策略  

---

## 1. Store 加载时机与离线兜底

| Option | Description | Selected |
|--------|-------------|----------|
| 全局预加载+静态兜底（推荐） | 应用挂载时（App.vue 或路由自举）静默预加载，且 Pinia 初始 state 直接内置 23 项静态常量（@/constants/metricMeta.ts）作为兜底。首屏 0 延迟渲染，网络返回后增量覆盖，弱网或离线时体验完全无损。 | ✓ |
| 按需懒加载+纯API依赖 | 进入各指标页面时按需懒加载，不内置静态常量，纯依赖网络 API。如果 API 未返回则展示骨架屏或加载动画。 | |

**User's choice:** 全局预加载+静态兜底（推荐）  
**Notes:** 结合 Pinia state 初始值与静态常量，从架构上彻底消灭首屏由于异步网络请求带来的短暂闪烁与空缺。

---

## 2. 全站格式化升级与兼容策略

| Option | Description | Selected |
|--------|-------------|----------|
| 透明增强旧函数向后兼容（推荐） | 重构 format.ts，保持原有的 getMetricLabel / getMetricUnit 签名 100% 兼容不变。内部透明集成 Normalizer 归一化与元数据字典查询。全站既有组件零修改即可立刻自动消除英文 Key 外露，同时消除 prevention/Metrics.vue 等组件内的私有重复硬编码。 | ✓ |
| 全量重写为响应式 Hook | 废弃旧函数，强制将全站所有使用指标名称与单位的 Vue 页面逐个重构为使用新的 useMetricMeta() Pinia 响应式钩子。工作量较大，但类型响应更为直接。 | |

**User's choice:** 透明增强旧函数向后兼容（推荐）  
**Notes:** 以最小改动面实现全系统 100% 页面即时受益，杜绝遗漏修改某些模板的风险。

---

## 3. 未知 Key 优雅降级策略

| Option | Description | Selected |
|--------|-------------|----------|
| 可读单词化转换（推荐） | 人性化词法转换：自动将下划线/驼峰分词并首字母大写（如 vo2_max 显示为 Vo2 Max），保证排版工整，杜绝丑陋的代码下划线直接外露。 | ✓ |
| 「未定义指标」+浮动提示 | 直接显示为「未定义指标」，鼠标悬浮（Tooltip）展示原始 key。（语义最严谨，但界面可能重复出现未定义字样） | |
| 原样显示原始代码 | 原样显示原始字符串（不做任何处理）。 | |

**User's choice:** 可读单词化转换（推荐）  
**Notes:** 保证即使用户或第三方设备上报了非常用冷门指标，前端也不会出现破损或代码下划线。

---

## Claude's Discretion

- `metricNormalizer.ts` 分词与正则优化。
- 前端测试用例参数矩阵。

## Deferred Ideas

- Phase 6: 表单组件（`QuickInputDialog.vue`）彻底移除失焦抹除，增加占位符引导与四梯度胶囊展示。
- Phase 7: AI 智能解析与 OCR 报告识别对齐统一元数据。
