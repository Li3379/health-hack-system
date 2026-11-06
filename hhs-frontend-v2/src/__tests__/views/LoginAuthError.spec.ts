import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { Mock } from 'vitest'

/**
 * CR-01 反回归测试：request.ts 只翻译不展示后，登录失败必须由 Login.vue
 * 调用点通过 showError 呈现 —— 恰好一条用户可见消息，且不重复。
 */

const { loginMock } = vi.hoisted(() => ({ loginMock: vi.fn() }))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ login: loginMock })
}))

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
import { ElMessage } from 'element-plus'

const ElMessageMock = ElMessage as unknown as Mock &
  Record<'error' | 'success' | 'warning' | 'info', Mock>

const globalStubs = {
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: () => Promise.resolve(true),
      validateField: vi.fn(),
      clearValidate: vi.fn()
    }
  },
  'el-form-item': {
    template: '<div class="el-form-item"><slot /></div>',
    props: ['prop', 'label']
  },
  'el-input': {
    template: '<input class="el-input" />',
    props: ['modelValue', 'type', 'placeholder', 'prefixIcon', 'showPassword']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'nativeType', 'loading']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  'router-link': {
    template: '<a class="router-link"><slot /></a>'
  }
}

/** 只统计「错误」类消息：对象式 type='error' + 函数式 ElMessage.error。 */
function errorToastCount(): number {
  const objectCalls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string })?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

function visibleToastCount(): number {
  return (
    ElMessageMock.mock.calls.length +
    ElMessageMock.error.mock.calls.length +
    ElMessageMock.success.mock.calls.length +
    ElMessageMock.warning.mock.calls.length +
    ElMessageMock.info.mock.calls.length
  )
}

/** 后端真实形态：400 + 中文 message + 业务错误码（无 fieldErrors）。 */
function wrongPasswordRejection() {
  return {
    response: {
      status: 400,
      data: {
        code: 400,
        message: '用户名或密码错误',
        errorCode: 'user.password_invalid'
      }
    }
  }
}

describe('Login.vue — 登录失败不得静默', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('密码错误：恰好一条可见错误消息，文案取自后端中文', async () => {
    loginMock.mockRejectedValueOnce(wrongPasswordRejection())

    const wrapper = mount(Login, {
      global: { stubs: globalStubs, directives: { loading: () => {} } }
    })
    await flushPromises()

    const vm = wrapper.vm as unknown as { handleLogin: () => Promise<void> }
    await vm.handleLogin()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(visibleToastCount()).toBe(1)

    const options = ElMessageMock.mock.calls[0][0] as { message: string }
    expect(options.message).toBe('用户名或密码错误')

    wrapper.unmount()
  })

  it('失败时 loading 复位，且不产生成功提示', async () => {
    loginMock.mockRejectedValueOnce(wrongPasswordRejection())

    const wrapper = mount(Login, {
      global: { stubs: globalStubs, directives: { loading: () => {} } }
    })
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      handleLogin: () => Promise<void>
      loading: boolean
    }
    await vm.handleLogin()
    await flushPromises()

    expect(vm.loading).toBe(false)
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)

    wrapper.unmount()
  })
})