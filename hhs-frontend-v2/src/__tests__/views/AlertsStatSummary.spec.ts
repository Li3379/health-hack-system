import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * 统计卡口径的回归护栏（health/Alerts.vue）。
 *
 * 三张卡（严重/警告/正常提示）原先按 `alertLevel === 'CRITICAL' | 'WARNING' | 'INFO'`
 * 过滤，而 alert_level 的真实值域是 HIGH/MEDIUM/LOW —— 一个都匹配不上，三个数字恒为 0。
 * 修复后按 alert_type（CRITICAL/WARNING/NORMAL）统计。
 *
 * 这里刻意给每条记录**同时**填 alertLevel 与 alertType，且让两者交叉不含原错误对照：
 * 若有人把过滤字段改回 alertLevel，CRITICAL 计数会变成 0，用例立刻红。
 */

vi.mock('@/api/alert', () => ({
  alertApi: {
    getAlerts: vi.fn(),
    getUnreadCount: vi.fn(),
    getRecentAlerts: vi.fn(),
    markAsRead: vi.fn(),
    acknowledgeAlert: vi.fn(),
    markAllAsRead: vi.fn(),
    getStatistics: vi.fn(),
    deleteAlert: vi.fn()
  }
}))

// Alerts.vue 的 onMounted 消费 gsap / DUR / EASE；形状照抄 HealthCancelSilence.spec.ts:41-49。
vi.mock('@/composables/useGsap', () => {
  const timeline = { fromTo: () => timeline }
  return {
    gsap: { timeline: () => timeline },
    cleanupScrollTriggers: vi.fn(),
    DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
    EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
  }
})

import Alerts from '@/views/health/Alerts.vue'
import { alertApi } from '@/api/alert'

const getAlertsMock = alertApi.getAlerts as unknown as Mock
const getUnreadCountMock = alertApi.getUnreadCount as unknown as Mock
const getStatisticsMock = alertApi.getStatistics as unknown as Mock

const globalStubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot /></div>', props: ['span'] },
  'el-form': { template: '<form class="el-form"><slot /></form>', props: ['inline', 'model'] },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label'] },
  'el-select': {
    template: '<select class="el-select"><slot /></select>',
    props: ['modelValue', 'placeholder', 'clearable']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-table': { template: '<div class="el-table"><slot /></div>', props: ['data', 'stripe'] },
  'el-table-column': {
    template: '<div class="el-table-column"><slot name="default" :row="{}" /></div>',
    props: ['prop', 'label', 'width', 'minWidth', 'fixed', 'showOverflowTooltip']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size', 'effect'] },
  'el-button': {
    template: '<button class="el-button" type="button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'link']
  },
  'el-pagination': {
    template: '<div class="el-pagination" />',
    props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout']
  },
  'el-badge': { template: '<span class="el-badge"><slot /></span>', props: ['isDot', 'type'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' }
}

function row(id: number, alertType: string, alertLevel: string) {
  return {
    id,
    alertType,
    alertLevel,
    title: `t${id}`,
    message: `m${id}`,
    isRead: false,
    isAcknowledged: false,
    createdAt: '2026-11-23T00:00:00'
  }
}

function statValues(): string[] {
  return Array.from(document.querySelectorAll('.stat-value')).map(
    el => el.textContent?.trim() ?? ''
  )
}

function statLabels(): string[] {
  return Array.from(document.querySelectorAll('.stat-label')).map(
    el => el.textContent?.trim() ?? ''
  )
}

describe('health/Alerts.vue — 统计卡按 alert_type 计数', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getUnreadCountMock.mockResolvedValue({ code: 200, message: 'success', data: 3 })
    // 默认让统计接口挂掉：下面的用例验证的是「降级到当前页」这条路径。
    getStatisticsMock.mockRejectedValue(new Error('statistics down'))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('三张卡分别统计 CRITICAL / WARNING / NORMAL，第三张标签为「正常提示」', async () => {
    getAlertsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: {
        records: [
          row(1, 'CRITICAL', 'HIGH'),
          row(2, 'CRITICAL', 'HIGH'),
          row(3, 'WARNING', 'MEDIUM'),
          row(4, 'NORMAL', 'LOW')
        ],
        total: 4
      }
    })

    const wrapper = mount(Alerts, {
      attachTo: document.body,
      global: { stubs: globalStubs, directives: { loading: () => {} } }
    })
    await flushPromises()

    // [未读, 严重, 警告, 正常提示]
    expect(statValues()).toEqual(['3', '2', '1', '1'])
    expect(statLabels()).toContain('正常提示')

    wrapper.unmount()
  })

  it('统计接口可用时读全量口径：换页/筛选不改变三张卡', async () => {
    // 全量 7/5/4，与当前页的 2/1/1 刻意不同：只要卡片读了列表，断言就会红。
    getStatisticsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { total: 16, pending: 9, critical: 7, warning: 5, normal: 4, unread: 3 }
    })
    getAlertsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: {
        records: [row(1, 'CRITICAL', 'HIGH'), row(2, 'CRITICAL', 'HIGH'), row(3, 'WARNING', 'MEDIUM'), row(4, 'NORMAL', 'LOW')],
        total: 4
      }
    })

    const wrapper = mount(Alerts, {
      attachTo: document.body,
      global: { stubs: globalStubs, directives: { loading: () => {} } }
    })
    await flushPromises()

    expect(statValues()).toEqual(['3', '7', '5', '4'])

    // 用户筛「警告预警」：列表只剩 1 条 WARNING，三张卡必须仍是全量数字。
    // 这里让 getAlerts 真的返回过滤后的窄列表——否则断言证明不了「卡片不读列表」。
    getAlertsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { records: [row(3, 'WARNING', 'MEDIUM')], total: 1 }
    })
    await wrapper.find('.filter-bar button').trigger('click')
    await flushPromises()
    expect(statValues()).toEqual(['3', '7', '5', '4'])

    wrapper.unmount()
  })
})
