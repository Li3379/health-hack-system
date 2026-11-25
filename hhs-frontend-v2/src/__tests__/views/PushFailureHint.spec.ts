import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * 推送失败提示的可读性（PUSH-1）。
 *
 * 这一项本身是**配置问题**：QQ 的 SMTP 用 535 拒绝凭据，只能由使用者去邮箱重新开启服务 /
 * 换授权码。代码侧能做的只有一件事 —— 让已经发生的失败变得可诊断。原先后端只把 Spring
 * 硬编码的 "Authentication failed" 记进 push_history（服务器原文在 cause 上被丢掉），
 * 于是「授权码错」「SMTP 服务没开」「频率限制」三种处置完全不同的情况在界面上数字一模一样。
 *
 * 后端改动见 EmailPushChannel.describeFailure；本 spec 钉前端怎样呈现那条更完整的原文。
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
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
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

function historyRow(message: string, status = 'FAILED') {
  return {
    id: 1,
    userId: 1,
    channelType: 'EMAIL',
    channelLabel: '邮件',
    status,
    message,
    pushedAt: '2026-11-24 16:09:11'
  }
}

async function mountWithHistory(message: string, status = 'FAILED') {
  getHistoryMock.mockResolvedValue({
    code: 200,
    message: 'success',
    data: [historyRow(message, status)]
  })
  const wrapper = mount(PushSettings, {
    attachTo: document.body,
    global: { stubs, directives: { loading: () => {} } }
  })
  await flushPromises()
  return wrapper
}

describe('PushSettings.vue — 推送失败提示必须能区分真正的原因', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getConfigsMock.mockResolvedValue({ code: 200, message: 'success', data: [] })
    document.body.innerHTML = ''
  })

  it('只拿到 Spring 的 Authentication failed 时：说明该找谁，但不假装知道具体原因', async () => {
    const wrapper = await mountWithHistory('Authentication failed')

    const hint = wrapper.find('.history-summary')
    expect(hint.exists()).toBe(true)
    expect(hint.text()).toContain('邮箱凭据或账号状态')
    // 服务器的并列原因里包含「服务未开」，不能凭这一句就断言服务没开
    expect(hint.text()).not.toContain('SMTP 服务未开启')
    wrapper.unmount()
  })

  it('后端带上 535 原文后：摘要只给结论，535 原文留在可展开的详情里', async () => {
    // 用户视角：时间线每条记录只占一两行，能扫读「怎么了 + 该做什么」；
    // 服务器原文属于排障信息，折叠到「查看详情」。此前摘要 + 后端原文 + 详情里
    // 各贴一遍 535，同一条失败堆成三百多字。
    const wrapper = await mountWithHistory(
      'Authentication failed (535 Login fail. Account is abnormal, service is not open, '
        + 'password is incorrect, login frequency limited, or system is busy.)'
    )

    const hint = wrapper.find('.history-summary').text()
    expect(hint).toContain('邮箱凭据或账号状态')
    // 摘要本身不再重复 535 —— 这正是「信息过于冗余」要修的点
    expect(hint).not.toContain('535')
    // 折叠状态下原文不出现在正文里（不占阅读空间），但入口存在
    expect(wrapper.text()).toContain('查看详情')
    expect(wrapper.text()).not.toContain('login frequency limited')

    // 展开后才看得到服务器原文 —— 排障信息按需可取
    await wrapper.find('.history-detail button').trigger('click')
    expect(wrapper.text()).toContain('535')
    expect(wrapper.text()).toContain('login frequency limited')
    wrapper.unmount()
  })

  it('后端追加了「→ 处置建议」时，摘要不复述运维向的处置建议', async () => {
    // PUSH-2 之后后端的真实形态：原文括号 + 箭头 + 中文处置建议。
    // 摘要只保留面向用户的一句；那段 MAIL_* 运维说明留在详情里。
    const wrapper = await mountWithHistory(
      'Authentication failed (535 Login fail. Account is abnormal, service is not open, '
        + 'password is incorrect, login frequency limited, or system is busy.)'
        + ' → SMTP 认证失败：请检查 MAIL_USERNAME 是否为完整邮箱地址、'
        + 'MAIL_PASSWORD 是否为邮箱服务商生成的授权码（QQ 邮箱需在设置中开启 SMTP 服务），修改后重启后端'
    )

    const hint = wrapper.find('.history-summary').text()
    expect(hint).toContain('邮箱凭据或账号状态')
    expect(hint).not.toContain('MAIL_PASSWORD')
    expect(hint).not.toContain('修改后重启后端')
    wrapper.unmount()
  })

  it('连接类失败仍然原样归类为连接问题，不被误标成凭据问题', async () => {
    // 复审指出：主机名里带 "auth"（smtp.auth.example.com）的 connection refused
    // 必须在「连接失败」这一步就被拦下，不能落到 535/auth 分支。
    const wrapper = await mountWithHistory(
      'MailConnectException (Could not connect to host "smtp.auth.example.com": connection refused)'
    )

    const hint = wrapper.find('.history-summary').text()
    expect(hint).toContain('邮件服务器连接失败')
    expect(hint).not.toContain('凭据')
    wrapper.unmount()
  })

  it('认不出的失败保留原文，绝不伪装成已知错误', async () => {
    const wrapper = await mountWithHistory('Something entirely unexpected')

    const hint = wrapper.find('.history-summary').text()
    expect(hint).toContain('失败原因：')
    expect(hint).toContain('Something entirely unexpected')
    wrapper.unmount()
  })

  it('成功的记录不显示任何失败文案，也没有「查看详情」入口', async () => {
    // 这条曾经回归过：改成无条件渲染摘要后，SUCCESS 记录显示成
    // 「失败原因：Push successful」—— 成功消息被套上了失败措辞。
    const wrapper = await mountWithHistory('Push successful', 'SUCCESS')

    expect(wrapper.find('.history-summary').exists()).toBe(false)
    expect(wrapper.find('.history-detail').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('失败原因')
    wrapper.unmount()
  })
})