import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * ERR-03 + ERR-05 的端到端锚点（`views/user/Profile.vue`）。
 *
 * 本页是全站唯一「同一页两个表单」的案例（个人信息 + 修改密码）。三个用例各自证明
 * 一条**无法用 grep 证明**的性质：
 *  1. 密码被拒 → 只有**密码表单**的汇总出现、个人信息表单的汇总不存在，三条密码输入
 *     的值一字未变、零 toast。单个共享实例会让两处汇总都渲染同一批条目，因此
 *     「另一个表单的汇总必须不存在」这半条断言才是本用例的重点。
 *  2. 个人信息被拒 → 反向的同一性质，且被拒的邮箱值保留。
 *  3. 头像上传的 `RESOURCE_NOT_FOUND` 信封 → 恰好一条 toast，文案是后端消息。
 *     缺 `presentStates: true` 时这里读到 0（B1 回归护栏，与 14-05/14-10/14-11 同形）。
 *
 * element-plus 用 importOriginal 部分 mock（同 GoalFormInlineError.spec.ts:43-60）：
 * 保住组件导出，只替换 ElMessage，这样才能既挂载页面、又对每次 toast 计数。
 */

const { updateProfileMock, getProfileMock, uploadAvatarMock, changePasswordMock } = vi.hoisted(() => ({
  updateProfileMock: vi.fn(),
  getProfileMock: vi.fn(),
  uploadAvatarMock: vi.fn(),
  changePasswordMock: vi.fn()
}))

vi.mock('@/api/user', () => ({
  userApi: {
    getProfile: getProfileMock,
    updateProfile: updateProfileMock,
    changePassword: changePasswordMock,
    uploadAvatar: uploadAvatarMock
  }
}))

// 本页 onMounted 里 fetchStats 消费两个列表 API；不给固定返回就会产生未处理的 rejection。
vi.mock('@/api/health', () => ({ healthApi: { getMetrics: vi.fn() } }))
vi.mock('@/api/alert', () => ({ alertApi: { getAlerts: vi.fn() } }))
vi.mock('@/api/screening', () => ({ screeningApi: { listReports: vi.fn() } }))

// onMounted 里的 gsap.timeline 动效；形状照抄 ProfileErrorSample.spec.ts:43-52。
vi.mock('@/composables/useGsap', () => ({
  gsap: { timeline: () => ({ fromTo: vi.fn().mockReturnThis() }) },
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

vi.mock('element-plus', async importOriginal => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(() => ({ close: vi.fn() })), {
      error: vi.fn(),
      success: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      closeAll: vi.fn()
    })
  }
})

import Profile from '@/views/user/Profile.vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { healthApi } from '@/api/health'
import { alertApi } from '@/api/alert'
import { screeningApi } from '@/api/screening'

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/**
 * showError 的 VNode 分支有模块级 2000ms 去重闩锁。同文案的用例会互相吞掉对方的
 * toast，让断言读成 0 —— 那是闩锁生效，不是呈现缺失。让每次 Date.now() 前进 60s。
 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

/** 四通道统计：对象式调用 + 函数式旧 API（ElMessage.error / success …）。 */
function toastCount(): number {
  return (
    ElMessageMock.mock.calls.length +
    ElMessageMock.error.mock.calls.length +
    ElMessageMock.success.mock.calls.length +
    ElMessageMock.warning.mock.calls.length +
    ElMessageMock.info.mock.calls.length
  )
}

function errorToastCount(): number {
  const objectCalls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

interface VNodeLike {
  type?: unknown
  props?: Record<string, unknown>
  children?: unknown
}

/** 深度遍历 VNode，返回第一个带指定 class 片段的节点（同 ErrorLayerSample.spec.ts:110）。 */
function findNodeByClass(node: unknown, className: string): VNodeLike | undefined {
  if (!node || typeof node === 'string' || !Array.isArray((node as VNodeLike).children)) {
    return undefined
  }
  const target = node as VNodeLike
  for (const child of target.children as unknown[]) {
    if (!child || typeof child !== 'object') continue
    const candidate = child as VNodeLike
    const classes = String(candidate.props?.class ?? '')
    if (classes.includes(className)) return candidate
    const nested = findNodeByClass(candidate, className)
    if (nested) return nested
  }
  return undefined
}

/**
 * 客户端校验的 stub 出口：默认 valid=true（本 spec 只测服务端分支，客户端链路
 * 已由 14-03/14-05 的用例钉住）。保留 invalidFields 回调签名以匹配真实 EP。
 */
const globalStubs = {
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>',
    props: ['class']
  },
  // 真实 EP 的 validate 回调签名是 (valid, invalidFields)；stub 必须同形。
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: (cb: (valid: boolean, invalidFields?: unknown) => void) =>
        Promise.resolve(cb(true, undefined)),
      validateField: vi.fn(),
      clearValidate: vi.fn()
    }
  },
  // 内联错误必须真的渲染到 DOM（含 is-error 类），才能证明 :error 绑对了 prop。
  'el-form-item': {
    template:
      '<div class="el-form-item" :class="{ \'is-error\': !!error }"><label>{{ label }}</label><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['label', 'prop', 'error']
  },
  'el-input': {
    template: '<input class="el-input" :value="modelValue" />',
    props: ['modelValue', 'placeholder', 'type', 'disabled', 'showPassword', 'value']
  },
  'el-avatar': {
    template: '<div class="el-avatar"><slot /></div>',
    props: ['size', 'src']
  },
  // uploader 只需渲染插槽：本 spec 直接调 handleAvatarUpload，不经过控件。
  'el-upload': {
    template: '<div class="el-upload"><slot /></div>',
    props: ['class', 'showFileList', 'beforeUpload', 'httpRequest', 'accept']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'link', 'style']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  // FormErrorSummary 内部用 el-alert 承载标题。
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  }
}

const PROFILE_RES = {
  code: 200,
  message: 'success',
  data: {
    profile: {
      id: 1,
      username: 'alice',
      nickname: 'Alice',
      avatar: '',
      email: 'alice@example.com',
      phone: '13800000000',
      status: 1,
      createdAt: '2026-01-01 00:00:00',
      updatedAt: '2026-01-01 00:00:00'
    }
  }
}

function mountProfile() {
  // attachTo 是必需的：focusSummary() 只在真实挂载到 document 的元素上才改变
  // document.activeElement，游离节点上断言 activeElement 永远读到 <body>。
  return mount(Profile, {
    attachTo: document.body,
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

/** 真实形态：`@Email` 被违反 → HTTP 400 + fieldErrors。 */
function profileValidationRejection() {
  return {
    response: {
      status: 400,
      data: {
        code: 400,
        message: '参数校验失败',
        errorCode: 'validation.invalid_parameter',
        fieldErrors: [{ field: 'email', message: '邮箱格式不正确' }]
      }
    }
  }
}

/** 真实形态：`ChangePasswordRequest.oldPassword` 的 `@NotBlank` / 业务拒绝 → 400 + fieldErrors。 */
function passwordValidationRejection() {
  return {
    response: {
      status: 400,
      data: {
        code: 400,
        message: '参数校验失败',
        errorCode: 'validation.invalid_parameter',
        fieldErrors: [{ field: 'oldPassword', message: '原密码不正确' }]
      }
    }
  }
}

describe('user/Profile.vue — 两个表单各自独立的错误投影（ERR-03 / ERR-05）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()

    // 模板里读 `authStore.user!.username` / `.createdAt`（非空断言），user 为 null 会
    // 在渲染期抛 TypeError。挂载前先把会话用户塞进 store。
    useAuthStore().updateUser(PROFILE_RES.data.profile)

    getProfileMock.mockResolvedValue(PROFILE_RES)
    updateProfileMock.mockResolvedValue({ code: 200, message: 'success', data: {} })
    changePasswordMock.mockResolvedValue({ code: 200, message: 'success', data: {} })
    uploadAvatarMock.mockResolvedValue({ code: 200, message: 'success', data: { url: '/a.png' } })

    // fetchStats 的三个 Promise.allSettled 成员：给固定值避免未处理 rejection。
    ;(healthApi.getMetrics as unknown as Mock).mockResolvedValue({ data: { total: 0 } })
    ;(alertApi.getAlerts as unknown as Mock).mockResolvedValue({ data: { total: 0 } })
    ;(screeningApi.listReports as unknown as Mock).mockResolvedValue({ data: { total: 0 } })
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 只允许出现在 beforeAvatarUpload 的两条预检查里，
    // 任何失败路径调用它都说明迁移漏了一处。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('密码表单被拒：只有密码汇总出现，三项保留值，零 toast', async () => {
    const wrapper = mountProfile()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.passwordForm.oldPassword = 'wrong-old-pw'
    vm.passwordForm.newPassword = 'brand-new-pw'
    vm.passwordForm.confirmPassword = 'brand-new-pw'
    await flushPromises()

    // 提交前快照 —— 失败后必须逐字相等（ERR-05 最严重的形态：被迫重打密码）。
    const before = {
      oldPassword: vm.passwordForm.oldPassword,
      newPassword: vm.passwordForm.newPassword,
      confirmPassword: vm.passwordForm.confirmPassword
    }

    changePasswordMock.mockRejectedValueOnce(passwordValidationRejection())

    await vm.handleChangePassword()
    await flushPromises()

    // 1. 零 toast —— validation 由汇总 + 内联承担（D-01）。
    expect(toastCount()).toBe(0)

    // 2. **本用例的重点**：页面只有一个汇总块（密码表单的），个人信息表单的没有。
    //    单个共享实例会让两个 el-form 都渲染同一份 summaryItems，读到 2。
    const summaries = wrapper.findAll('.form-error-summary')
    expect(summaries).toHaveLength(1)
    const summary = summaries[0]
    expect(summary.text()).toContain('请修正以下 1 项填写问题')
    // 标签来自全局 FIELD_LABELS 的 oldPassword →「原密码」（无需 labels 覆盖）。
    expect(summary.text()).toContain('原密码：原密码不正确')
    expect(summary.findAll('li')).toHaveLength(1)

    // 3. 恰好一个 el-form-item 带 is-error，且落在旧密码那一项上。
    const erroredItems = wrapper.findAll('.el-form-item.is-error')
    expect(erroredItems).toHaveLength(1)
    expect(wrapper.find('[data-field="oldPassword"]').classes()).toContain('is-error')
    expect(
      wrapper.find('[data-field="oldPassword"] .el-form-item__error').text()
    ).toBe('原密码不正确')
    // 另外两项仍是正常描边（Contract 7.1：只标被拒绝的字段）。
    expect(wrapper.find('[data-field="newPassword"]').classes()).not.toContain('is-error')
    expect(wrapper.find('[data-field="confirmPassword"]').classes()).not.toContain('is-error')

    // 4. ERR-05：三个值一字未变 —— 清空只发生在成功路径的 await 之后。
    expect({
      oldPassword: vm.passwordForm.oldPassword,
      newPassword: vm.passwordForm.newPassword,
      confirmPassword: vm.passwordForm.confirmPassword
    }).toEqual(before)
    expect(vm.passwordForm.oldPassword).toBe('wrong-old-pw')

    // 5. 焦点只去汇总块一次（Contract 4.4），不去第一个字段。
    expect(document.activeElement).toBe(summary.element as HTMLElement)

    wrapper.unmount()
  })

  it('个人信息表单被拒：只有个人信息汇总出现，邮箱值保留，零 toast', async () => {
    const wrapper = mountProfile()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.editing = true
    vm.form.nickname = 'Alice2'
    vm.form.email = 'not-an-email'
    vm.form.phone = '13900000000'
    await flushPromises()

    updateProfileMock.mockRejectedValueOnce(profileValidationRejection())

    await vm.handleSubmit()
    await flushPromises()

    // 1. 零 toast。
    expect(toastCount()).toBe(0)

    // 2. 反向独立性：只有个人信息表单的汇总，密码表单的没有。
    const summaries = wrapper.findAll('.form-error-summary')
    expect(summaries).toHaveLength(1)
    expect(summaries[0].text()).toContain('请修正以下 1 项填写问题')
    // 标签来自全局 FIELD_LABELS 的 email →「邮箱」。
    expect(summaries[0].text()).toContain('邮箱：邮箱格式不正确')
    // 密码表单的三个字段名一个都不许出现在这份汇总里。
    expect(summaries[0].text()).not.toContain('原密码')
    expect(summaries[0].text()).not.toContain('新密码')

    // 3. 内联只落在 email 上。
    const erroredItems = wrapper.findAll('.el-form-item.is-error')
    expect(erroredItems).toHaveLength(1)
    expect(wrapper.find('[data-field="email"]').classes()).toContain('is-error')
    expect(wrapper.find('[data-field="email"] .el-form-item__error').text()).toBe('邮箱格式不正确')

    // 4. ERR-05：已填值保留，且仍停在编辑态（表单没有退回只读）。
    expect(vm.form.email).toBe('not-an-email')
    expect(vm.form.nickname).toBe('Alice2')
    expect(vm.editing).toBe(true)

    wrapper.unmount()
  })

  it('头像上传的 RESOURCE_NOT_FOUND 信封：恰好一条提示，文案是后端消息（B1 回归）', async () => {
    // 真实形态：BusinessException(RESOURCE_NOT_FOUND) → HTTP 200 + 信封 code 500 + errorCode。
    // 缺 presentStates 时 showError 在 resolveMessage 之前就返回 → 0 条，这正是 B1 的缺陷。
    uploadAvatarMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'resource.not_found', message: '用户不存在' }
      }
    })

    const wrapper = mountProfile()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleAvatarUpload({ file: new File(['x'], 'a.png', { type: 'image/png' }) })
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    // 断言文案而不只是条数：证明 presentStates 真的走到了 resolveMessage，
    // 一条「分类了但没解析」的路径不会有本节点。
    const options = ElMessageMock.mock.calls[0][0] as { message: unknown }
    const textNode = findNodeByClass(options.message, 'error-toast__text')
    expect(textNode?.children).toBe('用户不存在')

    wrapper.unmount()
  })
})
