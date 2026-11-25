import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * 「服务端已注册」≠「投递得了」（PUSH-6）。
 *
 * <p>页面原先只凭 `availability` 就给邮件渠道打「支持离线推送」标签、写「支持离线推送」描述。
 * 而 `availability` 在后端只是 `PushChannelManager.hasChannel()` —— 对 EMAIL 而言等于
 * `spring.mail.host` 非空，与凭据是否可用毫无关系。结果：用户打开开关、页面承诺离线推送、
 * 实际一条都发不出。
 *
 * <p>修复不引入主动探测（SMTP 探测是一次真实 AUTH，服务商对反复失败限频 —— 每次读配置页都探
 * 会制造它所报告的故障），而是把**已有的事实**呈现出来：后端新增 `deliveryStatus`，取自该
 * 渠道最近一次 push_history 结果。这里钉前端据此降级文案与标签。
 */

vi.mock('@/api/push', () => ({
  getPushConfigs: vi.fn(),
  updatePushConfig: vi.fn(),
  testPushChannel: vi.fn(),
  getPushHistory: vi.fn(),
  getPushStats: vi.fn()
}))

vi.mock('@/composables/useGsap', () => ({
  staggerReveal: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  gsap: { to: vi.fn(), timeline: () => ({ fromTo: vi.fn().mockReturnThis() }) },
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

import PushSettings from '@/views/user/PushSettings.vue'
import { getPushConfigs, getPushHistory } from '@/api/push'

const getConfigsMock = getPushConfigs as unknown as Mock
const getHistoryMock = getPushHistory as unknown as Mock

const stubs = {
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot /></div>', props: ['xs', 'lg'] },
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-collapse-transition': { template: '<div><slot /></div>' },
  'el-switch': { template: '<input type="checkbox" class="el-switch" />', props: ['modelValue', 'disabled'] },
  'el-tag': {
    template: '<span class="el-tag" :data-type="type"><slot /></span>',
    props: ['type', 'size']
  },
  'el-empty': { template: '<div class="el-empty">{{ description }}</div>', props: ['description'] },
  'el-timeline': { template: '<div class="el-timeline"><slot /></div>' },
  'el-timeline-item': { template: '<div class="el-timeline-item"><slot /></div>' },
  'el-icon': { template: '<span class="el-icon"><slot /></span>', props: ['size'] },
  'el-button': { template: '<button class="el-button"><slot /></button>', props: ['type', 'text', 'size', 'loading'] },
  'el-form': { template: '<form class="el-form"><slot /></form>' },
  'el-form-item': {
    template: '<div class="el-form-item"><label>{{ label }}</label><slot /></div>',
    props: ['label', 'prop', 'error']
  },
  'el-input': { template: '<input class="el-input" />', props: ['modelValue', 'placeholder', 'clearable'] },
  'el-alert': { template: '<div class="el-alert"><slot name="title" /></div>', props: ['type', 'closable', 'showIcon'] }
}

function emailConfig(deliveryStatus: 'SUCCESS' | 'FAILED' | 'SKIPPED' | null) {
  return {
    id: 1,
    channelType: 'EMAIL',
    channelLabel: '邮件',
    configKey: 'address',
    configValue: 'a@b.com',
    enabled: true,
    offlineSupported: true,
    isConfigured: true,
    availability: true,
    deliveryStatus,
    lastDeliveryAt: deliveryStatus ? '2026-11-24 16:09:11' : null,
    createdAt: '',
    updatedAt: ''
  }
}

async function mountWith(deliveryStatus: 'SUCCESS' | 'FAILED' | 'SKIPPED' | null) {
  getConfigsMock.mockResolvedValue({ code: 200, message: 'success', data: [emailConfig(deliveryStatus)] })
  const wrapper = mount(PushSettings, {
    attachTo: document.body,
    global: { stubs, directives: { loading: () => {} } }
  })
  await flushPromises()
  return wrapper
}

function emailCardText(wrapper: ReturnType<typeof mountWith> extends Promise<infer W> ? W : never) {
  // 通道描述与状态标签都在同一张通道卡里
  const card = wrapper.findAll('.el-card').find(node => node.text().includes('邮件'))
  expect(card).toBeDefined()
  return (card as NonNullable<typeof card>).text()
}

describe('PushSettings.vue — 不能凭 availability 声称支持离线推送', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getHistoryMock.mockResolvedValue({ code: 200, message: 'success', data: [] })
    document.body.innerHTML = ''
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('最近投递失败：显示「最近投递失败」，且不再出现「支持离线推送」', async () => {
    const wrapper = await mountWith('FAILED')

    const text = emailCardText(wrapper)
    expect(text).toContain('最近投递失败')
    expect(text).not.toContain('支持离线推送')
    wrapper.unmount()
  })

  it('失败时的描述指明该检查什么（授权码），而不是笼统说支持离线推送', async () => {
    const wrapper = await mountWith('FAILED')

    const text = emailCardText(wrapper)
    expect(text).toContain('服务端已配置')
    expect(text).toContain('投递失败')
    expect(text).toContain('授权码')
    wrapper.unmount()
  })

  it('最近投递成功：照常显示「支持离线推送」，不出现失败标记', async () => {
    const wrapper = await mountWith('SUCCESS')

    const text = emailCardText(wrapper)
    expect(text).toContain('支持离线推送')
    expect(text).not.toContain('最近投递失败')
    wrapper.unmount()
  })

  it('从未尝试过（deliveryStatus=null）：不声称「支持离线推送」，请用户先验证', async () => {
    const wrapper = await mountWith(null)

    const text = emailCardText(wrapper)
    expect(text).not.toContain('最近投递失败')
    // 没证据就不替系统做承诺 —— 这正是 PUSH-6 的过度承诺，只是换了个触发条件
    expect(text).not.toContain('支持离线推送')
    expect(text).toContain('尚未验证')
    wrapper.unmount()
  })

  it('服务端未开通时优先级最高：不显示失败标记，显示「服务端未开通」', async () => {
    getConfigsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: [{ ...emailConfig('FAILED'), availability: false }]
    })
    const wrapper = mount(PushSettings, {
      attachTo: document.body,
      global: { stubs, directives: { loading: () => {} } }
    })
    await flushPromises()

    const text = emailCardText(wrapper)
    expect(text).toContain('服务端未开通')
    expect(text).not.toContain('最近投递失败')
    wrapper.unmount()
  })
})