# Phase 14 — 浏览器实跑脚本（UAT）

> 本文件是**可执行脚本**，不是描述。每个编号步骤给出：页面 → 动作 → 采样前等待 → DOM 查询 → 期望读数。
> 覆盖单元测试证明不了的十三个状态：真实网络失败、真实轮询节奏、真实 403/404 通道、真实渲染出来的描边与焦点光晕。
>
> **前置条件**：MySQL 3307 + Redis 6380 + 后端 8082 全部就绪，前端 `npm run dev` 在 5173。
> 后端健康检查：`curl -s http://localhost:8082/actuator/health` → `{"status":"UP"}`。
> 登录账号：`admin` / `123456`（若 `sys_user` 中不存在，先 `POST /api/auth/register` 建一次）。

---

## 采样口径（每个数 toast 的步骤都适用，先读这一节）

**`.el-message` 有 300ms 入场过渡。** 动作之后立刻查询会读到 `0`，而 300ms 后同一动作显示 `1` —— 这不是「没有提示」，是查早了。
因此**所有数 toast 的步骤统一等待 300ms 再采样**。这是本脚本唯一容易被误判成「零提示」的地方。

统一查询片段（下文以 `COUNT` 指代）：

```js
// 等待 300ms 让入场过渡完成，再数当前可见的 toast
await new Promise(r => setTimeout(r, 300));
document.querySelectorAll('.el-message').length
```

DevTools Console 里可以直接粘 `await` 版本；或用 `$0` 面板手动观察后按 `COUNT` 的规则读数。

**判定基线**：一次失败恰好一条提示（D-02）。读到 `2` 是重复呈现，读到 `0` 是静默失败 —— 两者都算失败，不是「更保守」。

---

## 必需步骤（1–12）

### 步骤 1 — 停后端后，列表页刷新：恰好一条 toast

| 项 | 值 |
|----|-----|
| 页面 | `/health/metrics`（任何列表页同构） |
| 动作 | `docker compose stop backend`，然后在页面点「刷新」/ 切分页触发一次重新加载 |
| 等待 | **300ms**（入场过渡） |
| 查询 | `document.querySelectorAll('.el-message').length` |
| 期望 | **1** —— 不是 0（静默），也不是 2（重复） |
| 备注 | 该页不渲染 `ErrorState`，因此加载失败必须以一条常驻 toast 降级。这是 `presentStates-toast` 通道。 |

### 步骤 2 — 停后端后，表单页保存：一条 toast 且值保留

| 项 | 值 |
|----|-----|
| 页面 | `/goals`（或 `/reminders`） |
| 动作 | 打开创建弹窗，填入标题等字段，点「保存」 |
| 等待 | **300ms** |
| 查询 | `document.querySelectorAll('.el-message').length`；随后读弹窗内输入框的 `value` |
| 期望 | toast **1**；**弹窗仍然打开**；已填字段**一字未变**（ERR-05 值保留） |
| 备注 | 若弹窗关闭或输入被清空，是本阶段最严重的行为回归 —— 直接判失败。 |

### 步骤 3 — 停后端后，打开详情页：一个内容区状态块、零 toast

| 项 | 值 |
|----|-----|
| 页面 | `/screening/detail/1`（真实存在的报告 id） |
| 动作 | 直接导航到该 URL |
| 等待 | **300ms** |
| 查询 | `document.querySelectorAll('.error-state').length` 与 `document.querySelectorAll('.el-message').length` |
| 期望 | 状态块 **1** 个（**不含** `error-state--page` 类，即内容区而非全页）；toast **0** |
| 备注 | 内容区块的标题是「资源不存在」以外的加载失败形态；关键是「块出现且 toast 为 0」 —— 状态块拥有呈现权时不叠加 toast。 |

### 步骤 4 — 断网 60 秒、轮询活跃：全程零 toast，恢复后仍零

| 项 | 值 |
|----|-----|
| 页面 | `/realtime/monitor`（轮询活跃页） |
| 动作 | DevTools → Network → 勾 **Offline**，停留 60 秒 |
| 等待 | **每 10 秒采样一次，共 6 次**（0s/10s/20s/30s/40s/50s），每次采样前额外等 **300ms** |
| 查询 | 每次：`await new Promise(r=>setTimeout(r,300)); document.querySelectorAll('.el-message').length` |
| 期望 | 六次全部 **0**；取消 Offline 后再采一次仍 **0** |
| 备注 | 「轮询 60 秒」不写间隔就不可复现 —— 10 秒是这里的固定间隔。这条验证的是去重/静默，不是「失败可见」。 |

### 步骤 5 — 服务端拒绝某字段的表单：汇总 + N 条内联 + 零 toast + 值保留

| 项 | 值 |
|----|-----|
| 页面 | `/goals`（创建弹窗） |
| 动作 | 填一个后端会拒绝的字段（例如超出长度约束的标题），提交 |
| 等待 | **300ms** |
| 查询 | `.form-error-summary` 计数、`.el-form-item__error` 计数、`.el-message` 计数 |
| 期望 | 汇总 **1**；内联 **N**（N = 被拒字段数，≥1）；toast **0**；所有输入值仍在 |
| 备注 | 汇总首条应是**字段中文标签**（如「目标标题：…」），不是通用「标题」。显示英文 key 即 `FIELD_LABELS` 缺项。 |

### 步骤 6 — 普通账号访问设备平台页：页级 403 块、单个「返回首页」、侧边栏可用

| 项 | 值 |
|----|-----|
| 页面 | `/settings/device-platform`（**手工输入 URL**，侧边栏无入口） |
| 动作 | 以任意已登录账号访问（`LoginUser.getAuthorities()` 恒返回空列表，因此任何账号都会 403） |
| 等待 | **300ms** |
| 查询 | `.error-state--page` 是否存在、`.error-state__actions .el-button` 的文本、`.el-message` 计数 |
| 期望 | `.error-state--page` **存在**；动作按钮**恰好 1 个**且文本为「返回首页」；toast **0**；侧边栏仍可点击 |
| 备注 | 该路由没有侧边栏入口，只能手输 —— 这是研究结论，不是缺陷。 |

### 步骤 7 — 不存在的筛查记录详情：内容区块、两个动作、零 toast

| 项 | 值 |
|----|-----|
| 页面 | `/screening/detail/999999`（不存在的 id） |
| 动作 | 直接导航 |
| 等待 | **300ms** |
| 查询 | `.error-state` 计数、`.error-state__actions .el-button` 文本数组、`.el-message` 计数 |
| 期望 | 内容区块 **1**（**无** `error-state--page`）；两个动作按顺序为「返回列表」「重新加载该记录」；toast **0**；外壳（侧边栏/顶栏）仍可用 |
| 备注 | 「重新加载该记录」重试后仍 404 时，不得弹出第二条 toast。 |

### 步骤 8 — 手输未知 URL：页级 404 块，不是白屏

| 项 | 值 |
|----|-----|
| 页面 | `/no-such-page` |
| 动作 | 直接导航 |
| 等待 | 无需（非 toast 采样），但建议 300ms 让渲染稳定 |
| 查询 | `.error-state--page` 是否存在、`.error-state__title` 文本 |
| 期望 | 页级块**存在**；标题「页面不存在」；说明「该页面可能已下线，或链接地址有误。」；单个「返回首页」动作 |
| 备注 | 未登录访问一个不存在的 URL 也应看到 404，而不是被弹去登录页。 |

### 步骤 9 — 取消删除确认：零 toast

| 项 | 值 |
|----|-----|
| 页面 | `/screening/list` 或 `/goals` |
| 动作 | 点某行「删除」，在确认框点「取消」 |
| 等待 | **300ms** |
| 查询 | `document.querySelectorAll('.el-message').length` |
| 期望 | **0** |
| 备注 | 手写 `!== 'cancel'` 判断已被整段删除；取消路径是裸 `return`。读到 1 条说明旧判断回来了。 |

### 步骤 10 — 导航离开取消在途请求：零 toast

| 项 | 值 |
|----|-----|
| 页面 | `/screening/list`（快速切换筛选，或在请求未完成时点侧边栏离开） |
| 动作 | 在列表加载/筛选请求发出后立刻导航到另一个路由 |
| 等待 | **300ms** |
| 查询 | `document.querySelectorAll('.el-message').length` |
| 期望 | **0** |
| 备注 | 验证 `AbortError` / `CanceledError` 被识别为取消而非失败。读到红 toast 说明某处仍在手写判断错误对象。 |

### 步骤 11 — 必填留空提交：输入框有红**描边**，不只是红字

| 项 | 值 |
|----|-----|
| 页面 | `/goals`（创建弹窗） |
| 动作 | 标题留空，点「保存」 |
| 等待 | 渲染稳定即可 |
| 查询 | 对第一个出错输入：`getComputedStyle(el).borderColor`（input 族）或 `getComputedStyle(el).boxShadow`（textarea 族） |
| 期望 | `borderColor` 解析为 `--hhs-color-error-text` 的实际值（浅色主题 `rgb(185, 28, 28)` / `#b91c1c`；深色主题 `rgb(245, 108, 108)` / `#f56c6c`）；textarea 的 `boxShadow` **含 `inset`** |
| 备注 | `theme.css` 曾用 `!important` 抹掉 EP 的错误描边，因此「只有红字没有红框」正是本阶段要修的历史缺陷。 |

### 步骤 12 — 日期范围控件出错并聚焦：边框仍红，且有可见焦点光晕

| 项 | 值 |
|----|-----|
| 页面 | 任一带 `el-date-picker type="daterange"` 的表单（如筛查上传弹窗的报告日期，或目标页的日期范围） |
| 动作 | 让该控件进入错误态，然后用 Tab 或点击把它聚焦 |
| 等待 | 渲染稳定即可 |
| 查询 | `getComputedStyle(wrapper).borderColor` 与 `getComputedStyle(wrapper).boxShadow` |
| 期望 | `borderColor` 仍是错误色（**不是** EP 默认的青色/主色）；`boxShadow` 含 3px 的 `--hhs-color-error-tint` 光晕 |
| 备注 | daterange 的根 div 自身就是 `.el-input__wrapper`，不经过 `ElInput`，因此永远拿不到 `.is-focus` —— 它靠 `.is-active`。漏掉这条选择器就会出现「出错且聚焦时完全没有焦点指示」（WCAG 2.4.7 失败）。 |

---

## 兜底步骤（13）

### 步骤 13 — 两个视口兜底：320px 与 640px

| 项 | 值 |
|----|-----|
| 页面 | 页级块：`/no-such-page`；内容区块：`/screening/detail/999999` |
| 动作 | DevTools 设备模式分别设 320px 与 640px 宽，各截一张图 |
| 等待 | 渲染稳定即可 |
| 查询 | 说明文字的 `getClientRects().length`（应 ≥1）与其 `scrollHeight <= clientHeight + 1`（无截断）；动作按钮的 `offsetTop` 是否不同行（是否换行） |
| 期望 | 说明文字**换行而不截断**；两个动作按钮**换行堆叠**而非溢出容器 |
| **必须记录的证据** | 每个视口下说明文字的实际行数（`Math.round(scrollHeight / lineHeight)`）与按钮是否换行（`boolean`） |
| 备注 | **没有记录证据的兜底不算通过。** 「看起来没问题」不是读数。 |

---

## 非回归检查（本阶段可能碰坏、但没有单元测试端到端覆盖的两个状态）

这两项不计入 13 步，但必须一并跑：它们覆盖「共享分类改动」与「恢复动作」的副作用面。

### N-1 — 无档案用户打开个人档案页：create 模式仍成立

| 项 | 值 |
|----|-----|
| 页面 | `/user/profile`，用一个**没有 health_profile** 的账号登录 |
| 动作 | 直接导航 |
| 查询 | 表单是否为空待填（create 模式）而非渲染错误块；`.el-message` 计数 |
| 期望 | create 模式表单**正常渲染**；零错误 toast |
| 理由 | 本阶段改了共享的错误分类（新增 `notFound`）。若 `profile_not_found` 被误分类成通用失败，这个「正常的首次建档」会变成一次假报错 —— 这是 A-2 ④ 要守的分支。 |

### N-2 — 阈值页重复指标恢复动作仍可用

| 项 | 值 |
|----|-----|
| 页面 | `/health/thresholds` |
| 动作 | 新增一个**已存在**的指标（如血糖），提交 |
| 查询 | toast 计数与 toast 内是否带可点击的恢复动作 |
| 期望 | 恰好 **1** 条 toast；若列表里能定位到该指标，toast 带一个「打开已有阈值」类动作；**绝不渲染死按钮**（找不到时不传 action） |
| 理由 | `presentDuplicateError` 是本阶段唯一保留 `presentValidation: true` 的显式放行点；`resource.exists` 的标签由 `ERROR_CODE_ACTIONS` 自动补齐。 |

---

## 附：`14-02` 遗留的四条 computed-style 自检（WINDOWS.md #7 结转）

这四条无法在 happy-dom 下断言（`getComputedStyle` 返回空串），因此只能在真实浏览器里读。它们与步骤 11/12 共用同一次操作，可一并记录：

| # | 自检 | 查询 | 期望 |
|---|------|------|------|
| 1 | 出错 input 的 `border-color` 解析为 token 值 | `getComputedStyle(inputWrapper).borderColor` | 等于 `--hhs-color-error-text` 的解析值 |
| 2 | textarea 的 `box-shadow` 含 `inset` | `getComputedStyle(textarea).boxShadow` | 含 `inset` |
| 3 | 三族控件（input / select / daterange）出错且聚焦时都有 3px tint 光晕，且边框仍红 | 各自 `borderColor` + `boxShadow` | 边框为错误色；`boxShadow` 含 3px tint |
| 4 | 失焦后光晕消失、红边保留 | 失焦后重读同样两项 | `boxShadow` 不再含 3px tint；`borderColor` 仍为错误色 |

## 附：403 可达性说明（写进脚本，免得下一个人以为走错了）

`/settings/device-platform` **没有侧边栏入口，也没有任何应用内链接** —— 只能手输 URL。
同时 `LoginUser.getAuthorities()` 无条件返回空列表、`sys_user` 没有 `role` 列，因此**任何**已登录账号访问 `/api/admin/device/config/**` 都必然得到 403。
这两条合起来使 403 通道**更容易复现**（任何账号、任何时刻）但**更难被自然走到**（无导航入口）。步骤 6 因此以「手输 URL」为唯一入口。
