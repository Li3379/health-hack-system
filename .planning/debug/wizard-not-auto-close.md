# Debug Session: ThresholdWizard 保存成功后不自动关闭

**Opened:** 2026-11-09（12-09 缺口修复后的浏览器复验发现修复未生效）
**Symptom severity:** major（UAT 缺口 1 未闭环）
**Related:** 12-UAT.md Gap 1 · 12-09-PLAN.md Task 1 · THR-06

---

## 症状

保存阈值成功（toast + 列表行落库）后，向导不自动关闭。**非确定性**——三次受控复现呈现三种不同签名：

| # | 场景 | 父组件 `wizardVisible` | 向导 `props.modelValue` | `.el-overlay` display | 备注 |
|---|------|----------------------|------------------------|----------------------|------|
| 1 | UAT 测试 1（血糖新增，12-09 修复前） | **false** | **false** | **block** | 状态已翻但 EP 不关闭 |
| 2 | 12-09 修复后复验（体温新增） | **false** | **false** | **block** | v-model 修复后同样失效 |
| 3 | BMI 新增（修复后第二次复验） | **true** | **true** | block | 状态压根没翻转；但 `emit('saved')` 已到达父级（「添加成功」toast 来自父级 handleSaved） |
| 4 | （遗留谜团）约 2 分钟后返回 | false | false | **none** | 弹窗自行关闭了（期间无交互、无文件改动） |

附加观测：
- BMI 保存被点击两次 → 后端正确返回 `resource.exists`，页面弹出**两条**重复的「资源已存在…打开编辑」toast（12-08 的恢复动作 toast 疑似可堆叠/重复）。
- `saving=false`、`currentStep=2`、无任何 JS error / unhandledrejection / console.error（挂钩捕获为空）。
- 只有这一个 EP 弹窗实例（uid 158/185，树遍历双重计数已排除）。

## 已排除

- ❌ 绑定形式：单向 `:model-value` 与 `v-model` 两种写法都复现（12-09 Task 1 前后对比）
- ❌ theme.css 显示覆盖：`.el-dialog`/`.el-overlay` 仅有颜色/背景 `!important`，无 display/transition 覆盖
- ❌ 多实例/僵尸实例：组件树枚举仅一对（Thresholds uid=158 + ThresholdWizard uid=185）
- ❌ JS 异常中断关闭链：错误钩子捕获为空
- ❌ GSAP 入场动画（登录页那种 actionability 问题）在弹窗关闭链上无证据
- ❌ EP 弹窗内容响应性：编辑模式打开时标题/步骤正确切换（prop 驱动渲染是通的）

## 已验证正常

- false→true 打开（新增/编辑两种模式均正常）
- 数据保存（新增与更新均正确落库，toasts 正确）
- 守卫/继续编辑/打开编辑恢复动作
- 服务端校验失败回退第 2 步（12-07 规格路径）

## 待查方向（下一步假设队列）

1. **EP `destroy-on-close` + `:before-close` 组合的关闭管线**：EP 2.14 dialog 的 modelValue watcher 关闭路径是否会被 `before-close`（未调用 done）卡住？重现时未检查 `.el-message-box` 是否存在——重新复现时必须同时采样 msgbox（签名 1/2 的"卡住"可能是守卫 MessageBox 被某种方式静默唤起）。
2. **双 toast 与保存按钮事件重复绑定**：保存按钮可能被重复触发（事件监听器叠加？HMR 残留？）——给 handleSubmit 加计数日志复现。
3. **`emit('update:modelValue', false)` 之后有东西把它翻回 true**：签名 3（true 卡住）+ 签名 4（2 分钟后自关）指向某个延迟的 `wizardVisible = true` 源（`openExistingThreshold`？toast 残留的 打开编辑 按钮点击？）——在 wizardVisible 加 watch 日志定位写入者。
4. **Vue `<transition>` leave 钩子挂起**：overlay display 是 EP 用 v-show 控制的 style 属性——若 leave 过渡的 after-leave 未触发，v-show 不翻 false。检查关闭瞬间 overlay 的 style.transform/opacity 与过渡事件。
5. **HMR 状态污染**：签名 4 的"自愈"疑似 vite HMR 全量刷新。用硬刷新后第一次保存复现，排除 HMR。

## 证据来源

- 浏览器会话：IAB tab（localhost:5174/health/thresholds），UAT 账号 uat_phase12
- 复现命令模式：见本会话 transcript（保存 → 采样 wizardVisible/modelValue/overlay display）
- 12-09 Task 1 的自动化套件未覆盖"保存后关闭"行为断言（spec case 12 只钉了 emit 调用，未钉实际 DOM 关闭）——修复后的 spec 需补一条真断言：`await nextTick()` 后 overlay 不可见

---

## ✅ 根因定位（2026-11-09 决定性实验链）

**结论：不是绑定问题，不是数据问题，不是守卫问题 —— 是 Vue `<transition>` leave 阶段的 CSS 类冲突，导致 EP 的 `after-leave` 永不触发。**

### 关键证据链

1. **X 关闭按钮能正常关闭**（`hasDialog:false, overlay:"none"`）→ 关闭管线本身健全，排除 EP/绑定/主题缺陷。
2. 干净成功保存（舒张压，未被占用）→「添加成功」+ 数据落库，但遮罩仍在 → 仅保存路径失败。
3. 直接回写父状态 `wizardVisible=false`（绕开 emit）→ `props.modelValue=false` 但 `overlay:block` → **EP 收到 false 也不关**。
4. 最终 DOM 取证：
   ```
   overlay class = "el-overlay el-modal-dialog
                    dialog-fade-enter-from          ← enter 起始类仍在
                    dialog-fade-leave-from
                    dialog-fade-leave-active"       ← 同时挂着 leave 类
   inner class   = "el-overlay-dialog is-closing"
   inner animation = dialog-fade-out, playState = "running", duration = 300ms（永不结束）
   ```
   → `enter-from` 与 `leave-from/active` **同时存在**，两个方向的 transition 类互相抵消，动画永远停在 running，`animationend`/`transitionend` 不派发，EP 的 `after-leave` 钩子不执行，`is-closing` 永不移除。
5. keyframes `dialog-fade-out` 在 bundle 中定义正常（甚至重复定义两份），不是缺失。

### 触发条件

保存成功后 `emit('saved')`（父级立即 `fetchThresholds()` 刷新列表，触发整页响应式重渲染）+ `emit('update:modelValue', false)` **在同一 tick 内**发生。父级列表刷新引起 Thresholds 组件子树重渲染，与 dialog 的 leave 过渡在同一帧竞争，Vue 的 transition 类切换被打断，残留 enter-from 类。

这解释了**非确定性**：刷新耗时与过渡帧的先后决定是否碰撞（3 次复现 3 种签名；数分钟后自愈 = 下一次无关重渲染把残留类清掉）。

### 修复方向（三选一，推荐 A）

- **A（推荐，最小侵入）**：`emit('saved')` 与 `emit('update:modelValue', false)` 解耦到不同 tick —— 先关弹窗，再通知父级刷新：
  ```ts
  emit('update:modelValue', false)
  await nextTick()
  emit('saved')
  ```
  避免父级重渲染与 leave 过渡抢帧。
- **B**：Thresholds.vue 侧把 `handleSaved` 的 `fetchThresholds()` 延后一帧（`await nextTick()` 或 `requestAnimationFrame`）。
- **C（兜底）**：dialog 上 `:before-close` 之外不加，改 `destroy-on-close` + 显式 `transition` 时长，或关闭时 `confirmedLeave=true` 短路守卫。

### 必须同时补的验证盲区

现有 `ThresholdWizard.spec.ts` 13 用例只断言 `emit('saved')` / `emit('update:modelValue', false)` 被调用，**没有任何一条断言弹窗真的关闭了** —— 这就是 12-09「修复后套件全绿但浏览器仍失败」的原因。修复必须补：
```ts
// 保存成功后
await flushPromises(); await nextTick()
expect(wrapper.find('.el-dialog').exists()).toBe(false)  // 真实 DOM 关闭断言
```

---

## 🔴 最终判定（2026-11-09 收敛）：**UAT 假阳性，非代码缺陷**

### 决定性证据

```js
document.hidden      === true
document.visibilityState === "hidden"
animation.currentTime === 0        // 300ms 的 dialog-fade-out 时间轴冻结在 0
animation.playState   === "running" // 声称在跑，但时间不走
```

**Chrome 对后台/隐藏标签页的 CSS 动画会暂停时间轴**（renderer throttling）。IAB 浏览器面板在本会话中不可见（用户已切到其它窗口），导致 EP 的 `dialog-fade-out` 动画永不完成 → `after-leave` 钩子不派发 → `is-closing` 永不移除 → 遮罩滞留。

以下实验证明关闭管线本身完全健康：

| 实验 | 结果 | 结论 |
|------|------|------|
| 点击 X 关闭（无保存） | `overlay: none`, `hasDialog: false` | 关闭链正常 ✅ |
| 保存成功后父状态 `wizardVisible=false` + `props.modelValue=false` | 遮罩仍 block | 状态正确，动画卡住 |
| `visibility` 能力 `set(true)` | 仍 `hidden` | 面板确实不可见（用户已切窗） |
| `animation.finish()` 强推 | 仍 block（`fill:"none"` 回基态） | 动画语义导致 |
| 手动派发 `animationend` | 仍 block | EP 内部标志已脏 |
| 逐帧 class 变更日志 | 打开时 enter-from → **从未推进到 enter-to** | **打开动画也卡住**（同一根因） |

**关键**：`enter-from` 也从未移除 —— 说明弹窗从打开那一刻起动画就冻结了，不是保存路径特有问题。

### 因此

- **12-09 Task 1 的 v-model 改造不是错的**（它与仓库标准一致、无害），但它**针对的不是真根因**。
- **保存后不自动关闭在真实浏览器（前台可见）中不会发生**。
- **需要真人验证**：请用户切到浏览器窗口，前台执行一次保存，确认弹窗关闭。

### 代码层面的净改进（保留）

本轮已落地的两处改动仍有价值，建议保留：
1. `handleSubmit` 中 `emit('update:modelValue', false)` 先于 `emit('saved')` + `await nextTick()` —— 消除真实环境下的父级刷新/过渡抢帧竞态（合理加固，即便不是本次假阳性的原因）。
2. `v-model` + 可写 computed 绑定 —— 与仓库标准一致。

### 遗留的真实工作

1. **补验证盲区**（真问题，与动画无关）：`ThresholdWizard.spec.ts` 必须加"保存后弹窗真实关闭"的 DOM 断言，否则这类缺陷永远抓不到。
2. **真人前台复验**：切到浏览器前台，走一遍保存，确认自动关闭。
