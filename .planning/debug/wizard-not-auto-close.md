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
