---
status: partial
phase: 15-risk-history-batch
source:
  - 15-01-SUMMARY.md
  - 15-02-SUMMARY.md
started: 2026-11-18T01:00:00Z
updated: 2026-11-18T01:10:00Z
blocked_reason: Docker Desktop not running
---

## 环境阻塞（非代码缺陷）

浏览器 UAT **未能执行**。原因：

- Docker Desktop 未运行（`docker info` 无响应，`npipe:////./pipe/dockerDesktopLinuxEngine` 不存在）
- MySQL 3307 / Redis 6380 容器随之停止
- 8082 上残留一个 java 进程，但它连不上 MySQL，`POST /api/auth/login` 返回 `http=000`，页面被路由守卫重定向到 `/login`

因此**本轮只有自动化证据**，没有真实浏览器观测。

## 已由自动化证明的

| 项 | 证据 |
|---|---|
| 批次折叠渲染 | `RiskBackfill.spec.ts` 断言 `.risk-batch` 数量与 `.risk-batch__header` 文本 |
| D-02 紧邻批次比较 | 三批次 fixture 断言 ↑16 / ↓14 / ↑20；注释写明锚定最旧会变 ↑20/↓18/↑30、反向锚定会变 ↓4 |
| D-03 绝对分差 | 断言含 `↑4` / `↓4` / `持平`，且文本不含 `%` |
| D-04 等级变化独立 | 断言 `.risk-batch__level-change` 文本逐字为「低风险 → 中风险」 |
| 静默插槽守卫 | `el-collapse-item` stub 渲染 `el-collapse-item__header` + `<slot name="title" />` |
| 默认展开 | cast 读 `vm.activeBatchNames`，深度等于 `['b-newest']` |
| 门禁 | 638 tests / 59 files 全绿，build green |

## 待真人复验（环境就绪后）

1. **批次卡片外观** —— 打开 `/prevention/risk`，确认每个批次是一张可折叠卡片，头部显示「时间 · 最高风险：X」
2. **最近批次默认展开** —— 确认只有最新一张展开，其余折叠
3. **分差与等级徽标** —— 确认行内显示 `↑4` / `↓4` / `持平`，跨等级时另有「低风险 → 中风险」徽标
4. **翻页行为** —— 确认翻到第 2 页后本页最新批次自动展开（而非全局最新，否则会看到空壳）
5. **分页计数** —— 确认分页器按**批次**计数，不再声称不存在的页
6. **创建后回第 1 页** —— 在第 2 页点创建评估，确认自动回到第 1 页并能看到新批次
7. **legacy 批次** —— 确认 v1.3 之前的旧记录正常渲染，且显示「该记录创建时未保存评分明细」

## 复验前置

```bash
docker compose up -d mysql redis   # 或先启动 Docker Desktop
bash scripts/start-backend-dev.sh
# 前端 5174，账号 admin / 123456
```
