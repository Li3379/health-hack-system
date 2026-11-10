import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { Mock } from 'vitest'

/**
 * ERR-03 + ERR-05 的认证表单锚点（`views/auth/Login.vue` 与 `views/auth/Register.vue`）。
 *
 * 三个用例各自钉住一条**无法用 grep 证明**的性质：
 *  1. 注册被 400 拒（`username` 一条 fieldErrors）→ 汇总 + 单条内联 + 零 toast，
 *     且**已输入的邮箱仍在输入框里**、表单仍在屏上（ERR-05 的值快照断言）。
 *  2. 登录被 400 拒（两个字段）→ 两条内联 + 一个两行汇总 + 零 toast，
 *     且汇总顺序按 `fieldOrder`（用户名先于密码）而不是后端下发顺序。
 *  3. 登录被非校验类失败拒（信封 `business`）→ **恰好一条** toast、无汇总 ——
 *     两条通道互斥，不会同时出现。
 *
 * 后端形态说明（本 spec 的 mock 依据）：
 * - `RegisterRequest` 声明 `@NotBlank` + `@Size` on `username`/`password` 与 `@Email` on `email`，
 *   因此 HTTP 400 + `fieldErrors` 在注册端点是**真实可达**的（超长用户名就是一条）。
 *   重复用户名走的是 `BusinessException(USER_CONFLICT)` 的信封形态，那是另一个分支；
 *   本 spec 的第一例刻意用 400，因为要验证的是字段投影，不是信封分类。
 * - 登录密码错误是 `BusinessException(USER_PASSWORD_INVALID)` → HTTP 200 + 信封 code 500 +
 *   `errorCode: 'user.password_invalid'`。它既不是 `auth`（401）也不是 `validation`，
 *   因此落到 `business` 并产生一条 toast —— 第三例按这个真实形态构造，而不是裸 500。
 */

const { loginMock, registerMock } = vi.hoisted(() => ({
  loginMock: vi.fn(),
  registerMock: vi.fn()
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ login: loginMock, register: registerMock })
}))

// 两个页面的 onMounted 都消费 gsap 动效；形状照抄 LoginAuthError.spec.ts:16-27。
vi.mock('@/composables/useGsap', () => ({
  gsap: {
    timeline: () => ({ fromTo: vi.fn().mockReturnThis() }),
    to: vi.fn()
  },
  magneticHover: vi.fn(),
  buttonPress: vi.fn(),
  hoverShine: vi.fn(),
  rippleClick: vi.fn(),
  DUR: { fast: 0.2, mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out', none: 'none', inOut: 'power1.inOut' }
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

import Login from '@/views/auth/Login.vue'
import Register from '@/views/auth/Register.vue'
import { ElMessage } from 'element-plus'

const ElMessageMock = ElMessage as unknown as Mock &
  Record<'error' | 'success' | 'warning' | 'info', Mock>

/** showError 的 VNode 分支有模块级 2000ms 闩锁；让每次 Date.now() 前进 60s 以免互相吞掉。 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

/** 四通道统计：对象式调用 + 函数式旧 API。 */
function toastCount(): number {
  return (
    ElMessageMock.mock.calls.length +
    ElMessageMock.error.mock.calls.length +
    ElMessageMock.success.mock.calls.length +
    ElMessageMock.warning.mock.calls.length +
    ElMessageMock.info.mock.calls.length
  )
}

/** 错误类调用：对象式且 type='error'，或函数式 ElMessage.error。 */
function errorToastCount(): number {
  const objectCalls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

/**
 * `el-form` 的 stub 出口。
 *
 * 两个页面的提交形态不同，stub 必须同时支持：
 * - `Register.vue` 用 `validate(async (valid, invalidFields) => …)` 回调形态；
 * - `Login.vue` 用 `await validate()` 并接住拒绝值（EP 的拒绝值就是 invalidFields）。
 * 默认回放「通过」，`nextClientInvalid` 可让下一次校验失败。
 */
let nextClientInvalid: Record<string, Array<{ message?: string }>> | undefined

const globalStubs = {
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: (cb?: (valid: boolean, invalidFields?: unknown) => void) => {
        const invalid = nextClientInvalid
        nextClientInvalid = undefined
        if (invalid) {
          // 与 EP 一致：有回调时回调 false + invalidFields，无回调时 reject(invalidFields)。
          if (cb) return Promise.resolve(cb(false, invalid))
          return Promise.reject(invalid)
        }
        if (cb) return Promise.resolve(cb(true, undefined))
        return Promise.resolve(true)
      },
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
    template: '<input class="el-input" :value="modelValue" :type="type" />',
    props: ['modelValue', 'type', 'placeholder', 'prefixIcon', 'showPassword']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'nativeType', 'loading']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-card': { template: '<div class="el-card"><slot /></div>' },
  'router-link': { template: '<a class="router-link"><slot /></a>' },
  // FormErrorSummary 内部用 el-alert 承载标题（默认插槽在 <p> 里，列表放在外面）。
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  }
}

const mountOptions = {
  attachTo: document.body,
  global: {
    stubs: globalStubs,
    directives: { loading: () => {} }
  }
}

/** 服务端 400：注册端点的 `username` 约束（`@Size(min = 4, max = 32)`）。 */
function registerValidationRejection() {
  return {
    response: {
      status: 400,
      data: {
        code: 400,
        message: '参数校验失败',
        errorCode: 'validation.invalid_parameter',
        fieldErrors: [{ field: 'username', message: '用户名已存在' }]
      }
    }
  }
}

/** 服务端 400：登录两个字段同时被拒，**倒序**下发以证明 fieldOrder 生效。 */
function loginValidationRejection() {
  return {
    response: {
      status: 400,
      data: {
        code: 400,
        message: '参数校验失败',
        errorCode: 'validation.invalid_parameter',
        fieldErrors: [
          { field: 'password', message: '密码不能为空' },
          { field: 'username', message: '用户名不能为空' }
        ]
      }
    }
  }
}

/**
 * 真实密码错误形态：`BusinessException(USER_PASSWORD_INVALID)` → HTTP 200 + 信封 code 500
 * + `errorCode: 'user.password_invalid'` + 后端中文。分类结果是 `business`（一条 toast），
 * 不是 `auth`（401）也不是 `validation` —— 这正是第三例要钉的通道互斥。
 */
function wrongPasswordRejection() {
  return {
    response: {
      status: 200,
      data: {
        code: 500,
        message: '用户名或密码错误',
        errorCode: 'user.password_invalid'
      }
    }
  }
}

describe('auth 表单 — 400 字段校验的内联 + 汇总双呈现（ERR-03 / ERR-05）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    nextClientInvalid = undefined
    loginMock.mockReset()
    registerMock.mockReset()
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('注册被 400 拒：汇总含「用户名」，单条内联，零 toast，已输入的邮箱仍在输入框里', async () => {
    registerMock.mockRejectedValueOnce(registerValidationRejection())

    const wrapper = mount(Register, mountOptions)
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.username = 'tester'
    vm.form.password = 'secret123'
    vm.form.confirmPassword = 'secret123'
    vm.form.email = 'kept@example.com'
    await flushPromises()

    // 提交前快照：失败后必须逐字相等（ERR-05）。
    const before = { ...vm.form }

    await vm.handleRegister()
    await flushPromises()

    // 1. 零 toast —— validation 由汇总 + 内联承担呈现（D-01）。
    expect(toastCount()).toBe(0)
    expect(errorToastCount()).toBe(0)

    // 2. 汇总块存在，逐字断言标签是「用户名」（全局 FIELD_LABELS 命中）。
    const summary = wrapper.find('.form-error-summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('请修正以下 1 项填写问题')
    const items = summary.findAll('li')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toBe('用户名：用户名已存在')

    // 3. 恰好一个 el-form-item 带 is-error —— 服务端只点名了 username。
    const errored = wrapper.findAll('.el-form-item.is-error')
    expect(errored).toHaveLength(1)
    expect(wrapper.findAll('.el-form-item__error').map(n => n.text())).toEqual(['用户名已存在'])

    // 4. ERR-05：用户输入的邮箱**仍在输入框里**，不是只在 model 里。
    const emailInput = wrapper.find('[data-field="email"] input')
    expect(emailInput.exists()).toBe(true)
    expect((emailInput.element as HTMLInputElement).value).toBe('kept@example.com')
    // 并且整份表单值一字未变（不是被 resetFields 清空后又被重填）。
    expect({ ...vm.form }).toEqual(before)

    // 5. 表单仍在屏上（失败不卸载、不跳转）。
    expect(wrapper.find('form').exists()).toBe(true)
    expect(wrapper.find('.form-error-summary').exists()).toBe(true)

    wrapper.unmount()
  })

  it('登录被 400 拒（两个字段）：两条内联、一个两行汇总、零 toast，顺序按 fieldOrder', async () => {
    loginMock.mockRejectedValueOnce(loginValidationRejection())

    const wrapper = mount(Login, mountOptions)
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.username = 'tester'
    vm.form.password = 'secret123'
    await flushPromises()

    await vm.handleLogin()
    await flushPromises()

    // 1. 零 toast。
    expect(toastCount()).toBe(0)

    // 2. 汇总两行，且 username 先于 password（后端下发是倒序的）。
    const items = wrapper.findAll('.form-error-summary li')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toBe('用户名：用户名不能为空')
    expect(items[1].text()).toBe('密码：密码不能为空')
    expect(wrapper.find('.form-error-summary').text()).toContain('请修正以下 2 项填写问题')

    // 3. 两个字段各自内联，值保留。
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(2)
    expect(wrapper.findAll('.el-form-item__error').map(n => n.text())).toEqual([
      '用户名不能为空',
      '密码不能为空'
    ])
    expect(vm.form.username).toBe('tester')
    expect(vm.form.password).toBe('secret123')

    wrapper.unmount()
  })

  it('登录被非校验类失败拒（信封 business）：恰好一条 toast、无汇总 —— 两条通道互斥', async () => {
    loginMock.mockRejectedValueOnce(wrongPasswordRejection())

    const wrapper = mount(Login, mountOptions)
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.username = 'tester'
    vm.form.password = 'secret123'
    await flushPromises()

    await vm.handleLogin()
    await flushPromises()

    // 1. 恰好一条错误 toast，文案是后端中文（D-05）。
    expect(errorToastCount()).toBe(1)
    expect(toastCount()).toBe(1)
    const options = ElMessageMock.mock.calls[0][0] as { message: string; type: string }
    expect(options.type).toBe('error')
    expect(options.message).toBe('用户名或密码错误')

    // 2. 没有汇总、没有内联 —— 通道互斥，不会既弹 toast 又标字段。
    expect(wrapper.find('.form-error-summary').exists()).toBe(false)
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(0)

    // 3. loading 已复位，密码字段的值不被清空（ERR-05）。
    expect(vm.loading).toBe(false)
    expect(vm.form.password).toBe('secret123')

    wrapper.unmount()
  })

  it('客户端校验失败：走同一份汇总通道，零 toast，且不发请求', async () => {
    const wrapper = mount(Register, mountOptions)
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.username = 'tester'
    vm.form.email = 'kept@example.com'

    // EP 只在字段下方内联提示，没有「一共错了几项」；汇总必须由我们补。
    nextClientInvalid = {
      password: [{ message: '请输入密码' }],
      confirmPassword: [{ message: '请确认密码' }]
    }

    await vm.handleRegister()
    await flushPromises()

    const items = wrapper.findAll('.form-error-summary li')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toBe('密码：请输入密码')
    expect(items[1].text()).toBe('确认密码：请确认密码')

    expect(toastCount()).toBe(0)
    expect(registerMock).not.toHaveBeenCalled()
    // 焦点去汇总块一次（Contract 4.4），不是去第一个字段。
    expect(document.activeElement).toBe(
      wrapper.find('.form-error-summary').element as HTMLElement
    )
    // ERR-05：客户端失败同样不许碰输入。
    expect(vm.form.username).toBe('tester')
    expect(vm.form.email).toBe('kept@example.com')

    wrapper.unmount()
  })
})
