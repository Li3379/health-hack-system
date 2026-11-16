# 14-06 迁移台账片段 — user/Profile 的双表单与 screening/List 的上传/删除

本片段记录 plan 14-06 触达的九个裸 toast 调用点（`views/user/Profile.vue` 六行 + `views/screening/List.vue` 三行，一行一个调用点、绝不按分支拆行，Contract 6.1）；plan 14-13 会把各份片段合并为 `14-MIGRATION-LEDGER.md` 并对账到迁移前的 87 个调用点。

`file:line` 一列取自迁移前的 commit（`14-RESEARCH.md` 的「87 Bare ElMessage.error Calls — Classified Inventory」），因此在本片段落盘后源码行号已经漂移 —— 这是台账的设计前提，不是笔误。

**本片段承接 plan 14-05 建立的呈现通道规则**：一个文件若不渲染 `ErrorState`，其中每个 `showError` 都带 `{ presentStates: true }`；若渲染了，则一个都不带，绝不两可。`user/Profile.vue` 与 `screening/List.vue` 都不渲染 `ErrorState`，因此两个文件里 `presentStates: true` 的条数等于 `showError(` 的条数（4 / 3），这条等式本身就是可断言的验收判据。

`views/screening/Compare.vue` 的两处（`:82` 参数错误、`:88` 获取对比数据失败）**不在本片段** —— 它们属于 plan 14-04 的片段。

| file:line | 原文案 | kind 覆盖 | 迁移后形态 | fallback key | 理由 | spec 断言 |
|-----------|--------|-----------|------------|--------------|------|-----------|
| `user/Profile.vue:223` | `获取个人信息失败` | `notFound`, `forbidden`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `user.loadFailed` | 本页不渲染任何错误状态块，`forbidden` / `notFound` 没有落点；不 opt-in 会把一次个人信息加载失败变成完全静默（Contract 1.3）。页面无块时以一条常驻 toast 降级是唯一可接受的呈现，补块是另一个 plan 的工作。 | `src/__tests__/views/UserProfileInlineError.spec.ts#个人信息表单被拒：只有个人信息汇总出现，邮箱值保留，零 toast`（挂载期即调用 `fetchProfile`，返回 200 故该路径零 toast —— 该用例同时守护加载路径不得引入噪音） |
| `user/Profile.vue:242` | `更新失败` | `validation`, `business`, `forbidden`, `notFound`, `server`, `network`, `auth`, `cancel`, `unknown` | `presentStates-toast` | `user.saveFailed` | 表单接入 `useFormServerErrors` 后仍要 `showError` 的返回值来取归一化 `ApiError`：`validation` 由**个人信息表单自己的**汇总 + 内联承担（0 toast），其余 kind 由 toast 承担。本页无状态块故第三个参数必带；缺它会让「更新他人的资料」被拒的 403 变成静默。`UpdateProfileRequest` 的 `@Size(max = 64)` / `@Email` 使 400 通路真实可达。 | `src/__tests__/views/UserProfileInlineError.spec.ts#个人信息表单被拒：只有个人信息汇总出现，邮箱值保留，零 toast` |
| `user/Profile.vue:282` | `密码修改失败` | `validation`, `business`, `forbidden`, `notFound`, `server`, `network`, `auth`, `cancel`, `unknown` | `presentStates-toast` | `user.saveFailed` | **本行的存在理由是 ERR-05 的最严重形态**：一次「原密码不正确」若清空三个输入框，用户被迫重打一个刚被服务端判定为错的密码。因此清空语句留在成功路径的 `await` 之后，失败分支只清投影（`reset()`）。复用 `user.saveFailed` 而非新增 `user.passwordFailed`：两者都是「保存本页某个表单的写入」，后端下发的具体中文区分细节，新增近似键需同 commit 改字典与其计数断言。`ChangePasswordRequest.oldPassword` 的 `@NotBlank` 使 400 可达。 | `src/__tests__/views/UserProfileInlineError.spec.ts#密码表单被拒：只有密码汇总出现，三项保留值，零 toast` |
| `user/Profile.vue:294` | `只能上传图片文件` | `n/a`（**不是失败**） | `保持` | — | **客户端预检查，刻意保留。** 它校验的是用户刚选中、尚未发出任何请求的文件类型：没有 `ApiError` 可传、没有 `kind` 可分类。路由到 `showError` 需要凭空构造一个 `ApiError` 才能满足签名，并会声称一次并未发生的服务端失败。这是本阶段 4 行残留允许清单的第 1 行（另外 2 行在 plan 14-11 的 `OcrInput.vue`），性质是 `views/user/Profile.vue` 内 `beforeAvatarUpload` 函数体中的这一条字面量，plan 14-13 的检查 ① 以此三元组（路径 + 函数 + 字面量）而非行号核对。 | `src/__tests__/views/UserProfileInlineError.spec.ts#头像上传的 RESOURCE_NOT_FOUND 信封：恰好一条提示，文案是后端消息（B1 回归）` 的 `afterEach`（`expect(ElMessageMock.error).toHaveBeenCalledTimes(0)` —— 证明两条预检查之外的任何路径都不再走函数式 `ElMessage.error`） |
| `user/Profile.vue:298` | `图片大小不能超过 2MB` | `n/a`（**不是失败**） | `保持` | — | 同上，2MB 体积预检查。同为客户端预检查，同一理由，同为允许清单的第 2 行；`beforeAvatarUpload` 函数体内的字面量，plan 14-13 按三元组核对。 | 同上 `afterEach` 的零调用断言 |
| `user/Profile.vue:317` | `头像上传失败` | `business`, `forbidden`, `notFound`, `server`, `network`, `auth`, `cancel`, `unknown`（`validation` 不适用：上传无字段约束） | `presentStates-toast` | `user.saveFailed` | 上传是用户主动触发的动作且本页无状态块；`resource.not_found`（用户不存在）会经信封落到 `notFound`，缺第三个参数就是一次静默的头像失败。既有「吞掉并呈现」的契约保持不变 —— Element Plus 的 uploader 用 `before-upload` 的布尔返回与 `http-request` 的 promise 结算分别管理自身状态，本 plan 不改变 promise 的结算状态。**B1 回归护栏就在这一行。** | `src/__tests__/views/UserProfileInlineError.spec.ts#头像上传的 RESOURCE_NOT_FOUND 信封：恰好一条提示，文案是后端消息（B1 回归）` |
| `screening/List.vue:154` | `获取报告列表失败` | `notFound`, `forbidden`, `server`, `network`, `business`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `screening.loadFailed` | 本页同样不渲染任何错误状态块（报告详情页 `Detail.vue` 才是 404 的 `ErrorState` 落点，属别的 plan）；一次列表加载失败必须换一条常驻 toast 而不是静默。 | `src/__tests__/views/ScreeningListInlineError.spec.ts#上传被 400 拒：汇总 + 内联，零 toast，弹窗仍开，报告名称保留`（挂载期 `fetchReports` 返回 200 故零 toast —— 同时守护加载路径不得引入噪音） |
| `screening/List.vue:198` | `上传失败` | `validation`, `business`, `forbidden`, `notFound`, `server`, `network`, `auth`, `cancel`, `unknown` | `presentStates-toast` | `ocr.uploadFailed` | 上传对话框的四个字段（`reportName` / `institution` / `reportDate` / `file`）接入投影后，`validation` 由汇总 + 内联承担（0 toast），其余 kind 由 toast 承担；`showError` 的返回值仍是取得归一化 `ApiError` 的唯一受支持途径。本页无状态块，故第三参数必带。**注意**：`file` 的类型/大小检查如果将来加到这里，必须走本行的同一分类而不另开一条 —— 当前该检查不存在于 `screening/List.vue`（只有 `views/user/Profile.vue` 与 `OcrInput.vue` 有）。 | `src/__tests__/views/ScreeningListInlineError.spec.ts#上传被 400 拒：汇总 + 内联，零 toast，弹窗仍开，报告名称保留` |
| `screening/List.vue:219` | `删除失败`（含 `if (error !== 'cancel')`） | `notFound`, `forbidden`, `business`, `server`, `network`, `auth`, `validation`, `cancel`, `unknown` | `presentStates-toast` | `screening.deleteFailed` | **动作型 404 是本行的存在理由**：`ScreeningServiceImpl:197` 对「报告不存在」与「别人的报告」都抛 `RESOURCE_NOT_FOUND`；删除一份已在别的标签页被删掉的报告是常规竞态，缺第三个参数会让它一声不响。确认与请求彻底分离：确认失败是一个裸 `return`（零提示，带注释说明刻意静默），因此手写的取消比较被整段删除 —— 它对 `AbortError` 与归一化后的 `ApiError('', 'cancel')` 都会误判成真失败。 | `src/__tests__/views/ScreeningListInlineError.spec.ts#删除：取消时零提示且不发 DELETE；确认后失败恰好一条提示`；`#删除已被删除的报告（RESOURCE_NOT_FOUND 信封）：恰好一条提示，文案是后端消息（B1 回归）` |

## 本片段的残留允许清单（供 plan 14-13 检查 ① 使用）

| # | 路径 | 包围函数 | 字面量 | 形态 |
|---|------|---------|--------|------|
| 1 | `hhs-frontend-v2/src/views/user/Profile.vue` | `beforeAvatarUpload` | `只能上传图片文件` | `保持`（客户端预检查） |
| 2 | `hhs-frontend-v2/src/views/user/Profile.vue` | `beforeAvatarUpload` | `图片大小不能超过 2MB` | `保持`（客户端预检查） |

本片段拥有本阶段 4 行残留中的 2 行；另外 2 行是 plan 14-11 的 `components/.../OcrInput.vue` 内 `handleFileChange` 的两条同类预检查。定位一律使用「路径 + 包围函数 + 字面量」三元组 —— 行号在本片段落盘后必然漂移（14-06 自己的 import 与模板插入就移动了这两行）。
