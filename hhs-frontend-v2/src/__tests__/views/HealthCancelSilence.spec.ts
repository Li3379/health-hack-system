import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * ERR-08 的端到端锚点（health/Alerts.vue）。
 *
 * 三条不变量，全部靠断言而不是靠注释：
 *  1. 取消删除确认框 → 零 toast **且** 一个 DELETE 都没发。后半句才是关键：
 *     只有它能把「真的没发请求」和「发了请求但被静默吞掉」区分开。
 *  2. 确认后让 DELETE 失败（传输层 500）→ 恰好一条 toast，且文案不是被禁的
 *     通用措辞「操作失败」。
 *  3. 取消「全部标记已读」→ 零 toast **且** markAllAsRead 没被调用。
 *
 * 取消值刻意用 `AbortError` 而非字符串 `'cancel'`：旧的手写判断
 * `if (error !== 'cancel')` 对字符串恰好能用，因此字符串形态证明不了任何事
 * （见 14-05 的 Rule 1 偏差记录）。AbortError 正是旧判断会误判成真失败的那种形态。
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

// Alerts.vue 的 onMounted 消费 gsap / DUR / EASE；形状照抄 ErrorLayerSample.spec.ts:36-46。
vi.mock('@/composables/useGsap', () => {
  const timeline = { fromTo: () => timeline }
  return {
    gsap: { timeline: () => timeline },
    cleanupScrollTriggers: vi.fn(),
    DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
    EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
  }
})

// element-plus 用 importOriginal 部分 mock：保住组件导出，只替换 ElMessage / ElMessageBox，
// 这样才能既挂载页面、又对每次 toast（含对象式）计数、并让确认框可控。
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
    }),
    ElMessageBox: Object.assign(vi.fn(), {
      confirm: vi.fn(),
      alert: vi.fn(),
      prompt: vi.fn()
    })
  }
})

import Alerts from '@/views/health/Alerts.vue'
import { alertApi } from '@/api/alert'
import { ElMessage, ElMessageBox } from 'element-plus'

const getAlertsMock = alertApi.getAlerts as unknown as Mock
const getUnreadCountMock = alertApi.getUnreadCount as unknown as Mock
const deleteAlertMock = alertApi.deleteAlert as unknown as Mock
const markAllAsReadMock = alertApi.markAllAsRead as unknown as Mock
const getStatisticsMock = alertApi.getStatistics as unknown as Mock
const confirmMock = ElMessageBox.confirm as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/**
 * showError 的 VNode 分支有模块级 2000ms 去重闩锁，跨用例存活。让每次 Date.now()
 * 前进 60s，同文案的用例就不会互相把对方的 toast 闩掉（照抄 GoalFormInlineError.spec.ts:76）。
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

/** 错误类调用：对象式且 type='error'，或函数式 ElMessage.error。 */
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

/** 取最后一条错误 toast 的可见正文。 */
function lastErrorToastText(): string {
  const calls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  )
  expect(calls.length).toBeGreaterThan(0)
  const message = (calls[calls.length - 1][0] as { message: unknown }).message
  if (typeof message === 'string') return message
  const textNode = findNodeByClass(message, 'error-toast__text')
  return String(textNode?.children ?? '')
}

const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot /></div>', props: ['span'] },
  'el-form': { template: '<form class="el-form"><slot /></form>', props: ['inline', 'model'] },
  'el-form-item': {
    template: '<div class="el-form-item"><slot /></div>',
    props: ['label']
  },
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

function mountAlerts() {
  return mount(Alerts, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

/** 页面挂载期读到的列表载荷；返回 200 保证加载路径本身不产生噪音。 */
function okList() {
  return { code: 200, message: 'success', data: { records: [], total: 0 } }
}

describe('health/Alerts.vue — 取消零提示且不发请求，真实失败恰好一条（ERR-08 / ERR-07）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getAlertsMock.mockResolvedValue(okList())
    getUnreadCountMock.mockResolvedValue({ code: 200, message: 'success', data: 0 })
    getStatisticsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { total: 0, pending: 0, critical: 0, warning: 0, normal: 0, unread: 0 }
    })
    deleteAlertMock.mockResolvedValue({ code: 200, message: 'success' })
    markAllAsReadMock.mockResolvedValue({ code: 200, message: 'success', data: 0 })
    confirmMock.mockReset()
    confirmMock.mockResolvedValue('confirm')
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('取消删除确认框：零提示，且一个 DELETE 都没发', async () => {
    confirmMock.mockRejectedValueOnce(Object.assign(new Error('canceled'), { name: 'AbortError' }))

    const wrapper = mountAlerts()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleDelete(7)
    await flushPromises()

    expect(toastCount()).toBe(0)
    // 后半句是 ERR-08 的核心：静默不能掩盖一个被吞掉的请求。
    expect(deleteAlertMock).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('确认后 DELETE 失败：恰好一条提示，文案不是被禁的通用措辞', async () => {
    // 信封业务失败且后端未下发 message（HTTP 200 + code 500 + message 空）→
    // normalizeApiError 第 8 步判 business，resolveMessage 落到字典，
    // 因此这条断言证明的是 `alert.deleteFailed` 这个键真的接上了。
    deleteAlertMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, message: '' } }
    })

    const wrapper = mountAlerts()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleDelete(7)
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(deleteAlertMock).toHaveBeenCalledWith(7)

    const text = lastErrorToastText()
    // 迁移前这里是 `删除失败`；`操作失败` 是 Copywriting Contract 的禁止词。
    expect(text).not.toBe('操作失败')
    expect(text).not.toBe('删除失败')
    expect(text).toBe('无法删除该预警，请稍后重试')

    wrapper.unmount()
  })

  it('取消「全部标记已读」：零提示，且 markAllAsRead 没被调用', async () => {
    confirmMock.mockRejectedValueOnce(Object.assign(new Error('canceled'), { name: 'AbortError' }))

    const wrapper = mountAlerts()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.markAllRead()
    await flushPromises()

    expect(toastCount()).toBe(0)
    expect(markAllAsReadMock).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('确认后 markAllAsRead 失败：恰好一条提示，成功确认仍然只有成功路径才有', async () => {
    markAllAsReadMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, message: '' } }
    })

    const wrapper = mountAlerts()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.markAllRead()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)
    expect(lastErrorToastText()).toBe('无法更新预警状态，请稍后重试')

    wrapper.unmount()
  })

  it('删除一条已被别人删掉的预警（notFound 信封）：仍恰好一条提示，不是静默', async () => {
    // 本页不渲染 ErrorState，因此 forbidden / notFound 没有状态块可落。
    // 缺 `presentStates: true` 时 showError 在 resolveMessage 之前就返回 → 0 条，
    // 一次「行已消失」的常规竞态会变成完全静默。这条用例钉住第三个参数。
    deleteAlertMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'resource.not_found', message: '预警不存在' }
      }
    })

    const wrapper = mountAlerts()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleDelete(9)
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    // 断言文案而不只是条数：证明 presentStates 真的走到了 resolveMessage。
    expect(lastErrorToastText()).toBe('预警不存在')

    wrapper.unmount()
  })
})
