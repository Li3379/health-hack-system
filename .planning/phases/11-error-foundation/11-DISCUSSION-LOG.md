# Phase 11: 全站统一友好错误反馈地基 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-11-05
**Phase:** 11-全站统一友好错误反馈地基
**Areas discussed:** 错误呈现落点, 文案字典归属, 静默请求边界, 后端英文治理

---

## 错误呈现落点

### 字段校验顶部汇总形态

| Option | Description | Selected |
|--------|-------------|----------|
| 顶部常驻 el-alert（推荐） | 弹窗顶部常驻汇总所有字段错误，逐条可点击聚焦，修正后自动消失（GOV.UK 模式） | ✓ |
| 单条 toast 提示 | 顶部只显示一条 "有 N 项需要修正"，3 秒后消失，仅靠字段旁红字告知 | |
| 仅字段内联 | 不做汇总，只在字段旁内联红字 | |

**User's choice:** 顶部常驻 el-alert（推荐）
**Notes:** 采纳 GOV.UK 双呈现模式，避免长表单顶部错误被用户忽略

### 页面级加载失败形态

| Option | Description | Selected |
|--------|-------------|----------|
| 错误状态块 + toast（推荐） | 页面主体显示图标+原因+重试按钮；toast 只用于瞬时动作失败 | ✓ |
| 统一用 toast | 不区分页面加载与动作提交 | |
| 页顶内联横幅 | 加载失败也用内联横幅 | |

**User's choice:** 错误状态块 + toast（推荐）
**Notes:** 明确加载失败与动作失败的呈现分工

### 网络/服务端故障是否给重试

| Option | Description | Selected |
|--------|-------------|----------|
| toast 带重试按钮（推荐） | toast 内带"重试"动作，点击重新发起同一请求 | ✓ |
| 仅描述不提供重试 | toast 只说明失败原因与建议 | |
| 自动重试 GET | 自动重试 1 次幂等请求 | |

**User's choice:** toast 带重试按钮（推荐）
**Notes:** 否决自动重试，避免写操作重复提交

### 全页表单呈现方式

| Option | Description | Selected |
|--------|-------------|----------|
| 统一复用同一机制（推荐） | 全页表单与弹窗共用 el-alert 汇总 + 字段内联 | ✓ |
| 整页仅字段内联 | 整页只用字段内联，依赖滚动定位 | |

**User's choice:** 统一复用同一机制（推荐）
**Notes:** 避免两套实现分裂

---

## 文案字典归属

### 文案优先来源

| Option | Description | Selected |
|--------|-------------|----------|
| 后端优先（推荐） | 后端下发完整中文文案，前端仅在后端未给时回退 | ✓ |
| 前端场景优先 | 前端按界面场景写文案，后端消息作为详情补充 | |
| 仅错误码 + 前端字典 | 后端只下发错误码，前端按码查字典 | |

**User's choice:** 后端优先（推荐）
**Notes:** 文案集中于后端，减少 105 处调用点的维护面

### 字典结构

| Option | Description | Selected |
|--------|-------------|----------|
| 扁平 key → 文案（推荐） | 如 threshold.saveFailed 的扁平映射表 | ✓ |
| 按模块嵌套 | 如 errors.threshold.saveFailed 的嵌套对象 | |
| 不建字典 | 每页直接写具体中文文案 | |

**User's choice:** 扁平 key → 文案（推荐）

### 后端无文案时的回退

| Option | Description | Selected |
|--------|-------------|----------|
| 字典兼任回退（推荐） | 用调用点场景 key 对应的默认文案 | ✓ |
| 统一通用文案 | 统一"操作未完成，请稍后重试" | |
| 不提示只记日志 | 后端无文案时不提示 | |

**User's choice:** 字典兼任回退（推荐）

### 是否解析错误码

| Option | Description | Selected |
|--------|-------------|----------|
| 码决定呈现方式（推荐） | 如 RESOURCE_ALREADY_EXISTS 自动配置"打开编辑" | ✓ |
| 不解析错误码 | 前端只看消息文本 | |
| 码覆盖文案 | 前端字典按码覆盖后端消息 | |

**User's choice:** 码决定呈现方式（推荐）
**Notes:** 文案仍取后端消息，码只管呈现形态

---

## 静默请求边界

### silent 如何决定

| Option | Description | Selected |
|--------|-------------|----------|
| 逐调用显式标记（推荐） | 调用点显式传 { silent: true }，默认均提示 | ✓ |
| 按 URL 规则自动 | 按 URL 模式自动判静默 | |
| 默认表 + 覆盖 | 两者结合 | |

**User's choice:** 逐调用显式标记（推荐）
**Notes:** 控制权留在调用点，避免隐式吞掉真实故障

### silent 失败对调用方表现

| Option | Description | Selected |
|--------|-------------|----------|
| 仍 reject 由调用方降级（推荐） | 不吞错、不弹窗，调用方自行降级 | ✓ |
| 直接 resolve 空值 | 调用方无需 try/catch | |
| 仍弹窗 | 失去 silent 意义 | |

**User's choice:** 仍 reject 由调用方降级（推荐）

### 并发 401 处理

| Option | Description | Selected |
|--------|-------------|----------|
| 闩锁只处理一次（推荐） | 首条弹提示并 logout，其余静默 reject | ✓（超时自动采用） |
| 每条都提示 | 每条 401 都弹提示并 logout | |
| 静默登出 | 401 完全静默只 logout | |

**User's choice:** 闩锁只处理一次（推荐）— 问题超时后自动采用推荐项

### 取消操作识别

| Option | Description | Selected |
|--------|-------------|----------|
| 识别并全忽略（推荐） | 识别 'cancel'/'close' 哨兵、AbortError、CanceledError、ERR_CANCELED | ✓（超时自动采用） |
| 仅忽略 Abort | 只忽略 AbortError/CanceledError | |
| 不处理 | 所有错误一律提示 | |

**User's choice:** 识别并全忽略（推荐）— 问题超时后自动采用推荐项

---

## 后端英文治理

### 治理方式

| Option | Description | Selected |
|--------|-------------|----------|
| 直接改中文（推荐） | 改写 DTO 注解与字面量为中文，不引入 i18n 体系 | ✓（超时自动采用） |
| i18n 资源文件 | messages.properties + MessageSource | |
| 混合 | 只改用户可见错误，日志保留英文 | |

**User's choice:** 直接改中文（推荐）— 问题超时后自动采用推荐项

### Accept-Language

| Option | Description | Selected |
|--------|-------------|----------|
| 固定发 zh-CN（推荐） | request.ts 固定发送 Accept-Language: zh-CN | ✓（超时自动采用） |
| 跟浏览器语言 | 前端跟随浏览器语言 | |
| 不发 | 依赖浏览器默认 | |

**User's choice:** 固定发 zh-CN（推荐）— 问题超时后自动采用推荐项

### 字段标签归属

| Option | Description | Selected |
|--------|-------------|----------|
| 前端映射标签（推荐） | 后端只返回字段名，前端维护中文标签映射 | ✓（超时自动采用） |
| 后端返回中文标签 | 后端直接返回中文字段标签 | |
| 双端都维护 | 两边各维护一份 | |

**User's choice:** 前端映射标签（推荐）— 问题超时后自动采用推荐项

### 既有英文字面量清理时机

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 11 一并改（推荐） | 本阶段一并改为中文 | ✓（超时自动采用） |
| 留到 Phase 14 | 只建机制，英文清理留给扫尾 | |
| 只改两模块 | 只改阈值与档案 | |

**User's choice:** Phase 11 一并改（推荐）— 问题超时后自动采用推荐项

---

## Claude's Discretion

- `ApiError` 字段命名与 `kind` 枚举取值
- 错误状态块组件命名与图标选择
- 扁平字典 key 命名规范细节
- 重复提示合并窗口毫秒数
- 401 闩锁重置时机

## Deferred Ideas

- 逐页替换 105 处 `ElMessage.error` → Phase 14
- 错误摘要自动聚焦滚动定位细节 → Phase 14
- 403/404 专用解释性状态页 → Phase 14
- 错误码 → 恢复动作的完整清单 → Phase 14 扩充
- 前后端共享文案（BFF 下发字典）→ 已否决，多语言属新里程碑