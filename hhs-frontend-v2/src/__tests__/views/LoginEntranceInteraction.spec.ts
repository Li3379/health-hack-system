import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * 登录页入场动画的交互可用性护栏（auth/Login.vue）。
 *
 * 根因：GSAP 的 `autoAlpha` 被展开成 `opacity,visibility`，所以入场期间
 * `.login-btn` 与 `.login-form .el-form-item` 会带上 `visibility: hidden`，
 * 元素及其子节点既不能命中测试也无法获得焦点 —— 按钮点不中、密码框 focus() 失效。
 *
 * 本用例把 `gsap.timeline` 换成记录器，直接检查这两条 fromTo 的 vars：
 * 只要有人把 `opacity` 改回 `autoAlpha`，`fromVars.autoAlpha` 就不再是 undefined，
 * 用例立刻红。断言不看毫秒数，因此不依赖浏览器渲染时序。
 */

const { fromToCalls } = vi.hoisted(() => ({
  fromToCalls: [] as Array<{
    target: unknown
    fromVars: Record<string, unknown>
    toVars: Record<string, unknown>
  }>
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ login: vi.fn() })
}))

vi.mock('@/composables/useGsap', () => {
  const timeline: Record<string, unknown> = {}
  timeline.fromTo = (
    target: unknown,
    fromVars: Record<string, unknown>,
    toVars: Record<string, unknown>
  ) => {
    fromToCalls.push({ target, fromVars, toVars })
    return timeline
  }
  return {
    gsap: {
      timeline: () => timeline,
      to: vi.fn()
    },
    magneticHover: vi.fn(),
    buttonPress: vi.fn(),
    hoverShine: vi.fn(),
    rippleClick: vi.fn(),
    DUR: { instant: 0.15, fast: 0.3, mid: 0.45, slow: 0.6, dramatic: 1.0 },
    EASE: {
      out: 'power2.out',
      back: 'back.out(1.7)',
      expo: 'expo.out',
      none: 'none',
      inOut: 'power2.inOut'
    }
  }
})

vi.mock('element-plus', async importOriginal => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: Object.assign(
      vi.fn(() => ({ close: vi.fn() })),
      {
        error: vi.fn(),
        success: vi.fn(),
        warning: vi.fn(),
        info: vi.fn(),
        closeAll: vi.fn()
      }
    )
  }
})

import Login from '@/views/auth/Login.vue'

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
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  },
  'router-link': { template: '<a class="router-link"><slot /></a>' }
}

function callsFor(target: string) {
  return fromToCalls.filter(call => call.target === target)
}

describe('Login.vue — 入场动画不得隐藏交互控件', () => {
  beforeEach(() => {
    fromToCalls.length = 0
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('输入框所在 el-form-item 用 opacity 入场，不使用 autoAlpha', async () => {
    const wrapper = mount(Login, {
      global: { stubs: globalStubs, directives: { loading: () => {} } }
    })
    await flushPromises()

    const calls = callsFor('.login-form .el-form-item')
    expect(calls).toHaveLength(1)
    expect(calls[0].fromVars.autoAlpha).toBeUndefined()
    expect(calls[0].fromVars.opacity).toBe(0)
    expect(calls[0].toVars.autoAlpha).toBeUndefined()
    expect(String(calls[0].toVars.clearProps)).not.toContain('visibility')
    expect(String(calls[0].toVars.clearProps)).not.toContain('autoAlpha')

    wrapper.unmount()
  })

  it('提交按钮用 opacity 入场，不使用 autoAlpha', async () => {
    const wrapper = mount(Login, {
      global: { stubs: globalStubs, directives: { loading: () => {} } }
    })
    await flushPromises()

    const calls = callsFor('.login-btn')
    expect(calls).toHaveLength(1)
    expect(calls[0].fromVars.autoAlpha).toBeUndefined()
    expect(calls[0].fromVars.opacity).toBe(0)
    expect(calls[0].toVars.autoAlpha).toBeUndefined()
    expect(String(calls[0].toVars.clearProps)).not.toContain('visibility')
    expect(String(calls[0].toVars.clearProps)).not.toContain('autoAlpha')

    wrapper.unmount()
  })
})
