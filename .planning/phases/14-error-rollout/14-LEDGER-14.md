# 14-14 台账片段 — 认证表单与推送设置的内联覆盖审计

**本片段与 87 行迁移台账是两件不同的事，不要合并计数。**

`14-MIGRATION-LEDGER.md` 记录的是**迁移前的裸 `ElMessage.error` 调用点**（87 个），一行一个调用点，目的是证明「统一 helper 之外的裸调用归零」。本片段记录的是另一件同样可核对的事：**哪些参与服务端校验的表单已经把 `fieldErrors` 接成了内联 + 汇总，哪些故意不接**。

本 plan 的三个文件**本来就已经零裸 `ElMessage.error`**：`auth/Login.vue` 与 `auth/Register.vue` 在 Phase 11 就已改调 `showError`，`user/PushSettings.vue` 把呈现完全委托给 `pushStore`。因此本 plan 的工作**不给 87 行计数增加任何一行** —— 它补的是内联覆盖（ERR-03 的「全站所有表单提交」），不是 toast 迁移。

| 表单 | 提交字段 | 投影已接 | 未接理由 |
|------|---------|---------|---------|
| `views/auth/Login.vue` | `username`, `password`（两个都是服务端可达） | 是 —— 汇总块为 `<el-form>` 第一子节点；两项各有 `prop` + `data-field` + `:error` + `clearField`；客户端校验失败同样进汇总 | — |
| `views/auth/Register.vue` | 见下方三分法：三个服务端可达（`username`, `password`, `email`）+ 一个纯客户端（`confirmPassword`）+ 一个不提交（`nickname`） | 是 —— 四项各有 `prop` + `data-field` + `:error` + `clearField`；`validateConfirmPassword` 客户端规则原样保留 | `nickname` 不接：见下 |
| `views/user/PushSettings.vue` | 每个通道一个 `configValue`（四个通道共用同一字段名） | 是 —— 每个通道的 `el-form-item` 带 `prop="configValue"` + `data-field="{通道}::configValue"` + `:error` 读复合键；每通道一份自己的汇总（按复合键前缀过滤） | — |
| `views/user/PushSettings.vue`（空值预检） | — | 保持 | 见下方「保持」行 |

## `Register.vue` 的字段三分法（I1）

`RegisterRequest.java` 声明 **4 个字段**，本页提交 **3 个**，绑定 **4 个**投影键。三个数字不同，必须分开陈述，否则会被读成「四个服务端可达」：

| 类别 | 字段 | 依据 |
|------|------|------|
| **三个服务端可达** | `username`（`@NotBlank` + `@Size(min=4,max=32)`）、`password`（`@NotBlank` + `@Size(min=6,max=64)`）、`email`（`@Email`） | 这三个是 `RegisterRequest` 上真实存在约束、**且本页实际提交**的字段，因此服务端 `fieldErrors` 只会点名它们 |
| **一个纯客户端** | `confirmPassword` | `RegisterRequest.java` **没有**这个字段（已逐行核对：只有 username / password / nickname / email 四个）。后端永远不会在 `fieldErrors` 里下发它。投影表里的这一项是**防御性绑定** —— 它真正生效的路径是 `rules.confirmPassword` 的 `validateConfirmPassword` 自定义校验器，在发请求前比对 `password`。保留这个绑定是为了让「将来后端加一条密码确认约束」时无需改模板 |
| **一个不提交** | `nickname` | DTO 上声明但**无任何约束**，且本页提交时**不发送**（`Register.vue` 的 `authStore.register({ username, password, email: form.email \|\| undefined })` 只有三个键）。既然不发送，它就不可能出现在 `fieldErrors` 里 —— 不绑定它，也不为了让它能绑定而改动 payload（那会改变请求语义换取零收益）。台账把它记为「声明但未提交」，**不是**第四个服务端可达字段 |

一句话记法：**三服务端 + 一客户端 + 一不提交**。不要写成「四个字段都被服务端校验」。

## 登录路径的 `business` vs `auth` 分类结论（写下来，免得下一个人重新发现）

`UserServiceImpl.java:88,91` 对密码错误抛 `BusinessException(ErrorCode.USER_PASSWORD_INVALID, "用户名或密码错误")`；`ErrorCode.USER_PASSWORD_INVALID` 的 code 是 `user.password_invalid`，经 `Result.failure(String, String)` → `parseCodeToInt` **回退成 500**，HTTP 状态是 **200**（`GlobalExceptionHandler.handleBusinessException` 返回 `Result<Void>` 而非 `ResponseEntity`）。

因此前端 `normalizeApiError` 的分类结果是 **`business`**（第 8 步：HTTP 200 + 信封 code !== 200），**不是** `auth`（那需要真实 401）。`showError` 对 `business` 产生一条字符串 toast（`duration: 4000`），文案是后端下发的「用户名或密码错误」。

**结论：登录密码错误不会静默。** 本 plan 因此**没有**在提交按钮上方加内联的非字段错误行 —— 那会与 toast 重复（D-02 的「一次失败恰好一条提示」）。`showError` 对 `auth` 的早退（`showError.ts:160`）在本端点不适用，因为这里根本走不到 `auth`。

`AuthFormsInlineError.spec.ts` 的第三例按这个真实形态构造 mock（HTTP 200 + `code: 500` + `errorCode: 'user.password_invalid'`），而不是裸 500 —— 裸 500 会分类成 `server` 并带「重试」按钮，与实际行为不符。

## `PushSettings.vue` 的「保持」行

| 位置 | 形态 | 理由 |
|------|------|------|
| `handleToggle` 的空值预检（`请先输入{标签}` 的 `ElMessage.warning`） | 保持 | 这是**发请求前的客户端守卫**，不是失败呈现：用户把通道打开却没填配置值时，服务端根本没被调用。把它搬进 `showError` 会把「你还没填」说成「保存失败」。与档案页头像预检同一口径。`ElMessage.warning` 不在 Contract 5 的 `ElMessage.error` 禁令内。 |

## 推送保存的呈现分工（D-02，单次呈现）

`pushStore.saveConfig` **自己**调用 `showError`（`stores/push.ts`），只返回布尔值 —— 这是既有契约（T-11-21），本 plan 不推翻它。视图因此**不能**再调 `showError`，否则一次保存失败会有两条提示。

为了让视图仍能做字段级投影，本 plan 给 store 增加了 `saveError` ref（与既有 `loadError` 同形）：

```ts
const saveError = ref<{ channelType: string; error: ApiError } | null>(null)
```

- 带上 `channelType` 是必需的：`PushConfigRequest` 的字段名恒为 `configValue`，只存 `ApiError` 会让 A 通道的失败标到 B 通道的输入框上。
- 视图的 `projectSaveError(channelType)` 只在该记录属于本通道、且 `err.kind === 'validation'` 时投影；其余 kind 由 store 的 toast 承担。
- `handleSaveConfig` 在调用 store 前先 `clearField(channelField(...))`（清上一轮的错误文本，不碰输入值），失败后调 `projectSaveError`，成功路径完全不变。

## `presentStates` 记账

三个文件**都不渲染 `ErrorState` 的对应块**（`PushSettings.vue` 有一个**加载**用的块，但它绑定的是 `pushStore.loadError`，与提交动作无关），因此每一处 `showError` 都传 `{ presentStates: true }`：

| 文件 | `showError(` | `presentStates: true` |
|------|-------------|----------------------|
| `auth/Login.vue` | 1 | 1 |
| `auth/Register.vue` | 1 | 1 |
| `user/PushSettings.vue` | 0（呈现权在 store） | — |
| `stores/push.ts`（`saveConfig`） | 1 | 1 —— 保存是用户主动发起的动作，store 不渲染任何状态块，`forbidden`（无权修改配置）与 `notFound`（配置行已被删除）没有落点；不传会把它们变成静默 |

## 密码与密钥不进入任何输出（T-14-42 / T-14-43）

- 两个 spec 只断言密码**输入框存在**与 `form.password` 的**模型值**，从不把密码写进任何 toast、汇总或内联文本。
- 推送页的内联错误只渲染后端下发的消息（`配置内容不能超过 500 个字符`），**不**回显用户粘贴的 URL —— 超长粘贴正是最容易被二次泄露的场景，而错误文案里没有任何 `configValue` 内容。
