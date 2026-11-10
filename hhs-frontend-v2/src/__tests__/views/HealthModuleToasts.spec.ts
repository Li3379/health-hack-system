import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * ERR-07 的端到端锚点（health/Metrics.vue + health/Score.vue）。
 *
 * 两条不变量：
 *  1. 列表/报告加载失败 → 恰好一条可执行的中文提示，且**不静默** ——
 *     两个页面都不渲染 ErrorState，`forbidden` / `notFound` 没有落点，
 *     因此每个 showError 都必须带 `presentStates: true`。
 *  2. 后端下发的 message 优先于字典兜底。这条断言是**回归护栏**：
 *     旧代码读 `error.response?.data?.message`，而 ApiError 没有 response 属性，
 *     所以后端文案永远拿不到、永远走兜底。给 mock 一个与字典不同的后端消息，
 *     断言它出现在 toast 里 —— 对旧实现这条必然失败。
 *
 * 第三条（取消）在 Score 的 regenerate 路径上：取消必须零 toast。用真实的
 * `new ApiError('', 'cancel')` 而不是裸字符串，因为 Phase 11 之后真正到达 catch
 * 的就是这个形态；旧的手写 name 比较对它必然误判成失败。
 */

vi.mock('@/api/health', () => ({
  healthApi: {
    getMetrics: vi.fn(),
    createMetric: vi.fn(),
    updateMetric: vi.fn(),
    deleteMetric: vi.fn()
  }
}))

vi.mock('@/api/metric-meta', () => ({
  metricMetaApi: {
    getMetadata: vi.fn()
  }
}))

vi.mock('@/api/score', () => ({
  scoreApi: {
    getScore: vi.fn(),
    recalculateScore: vi.fn(),
    getScoreBreakdown: vi.fn(),
    getScoreHistory: vi.fn(),
    getReport: vi.fn(),
    generateReport: vi.fn(),
    getReportHistory: vi.fn(),
    getReportById: vi.fn()
  }
}))

vi.mock('@/api/prevention', () => ({
  preventionApi: {
    getProfile: vi.fn(),
    getCompleteness: vi.fn(),
    createProfile: vi.fn(),
    updateProfile: vi.fn()
  }
}))

// useRouter 是 inject 实现（RESEARCH Pitfall 2），必须 mock。用 importOriginal 保留
// createRouter / createWebHistory：`@/utils/request` → `@/stores/auth` 会 import `@/router`。
vi.mock('vue-router', async importOriginal => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: vi.fn(() => ({ query: {} })),
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
  }
})

vi.mock('@/composables/useGsap', () => {
  const timeline = { fromTo: () => timeline }
  return {
    gsap: { timeline: () => timeline },
    scrollReveal: vi.fn(),
    staggerReveal: vi.fn(),
    countUp: vi.fn(),
    cleanupScrollTriggers: vi.fn(),
    tilt3D: vi.fn(),
    parallax: vi.fn(),
    glowPulse: vi.fn(),
    scrollScale: vi.fn(),
    buttonPress: vi.fn(),
    hoverLift: vi.fn(),
    hoverShine: vi.fn(),
    rippleClick: vi.fn(),
    DUR: { fast: 0.2, mid: 0.5, slow: 0.8, dramatic: 1.2 },
    EASE: { out: 'power2.out', expo: 'expo.out', back: 'back.out' }
  }
})

// Score.vue 在模块作用域调用 echarts.use(...)，且 buildChartOption 可能取 graphic。
vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: vi.fn(),
  graphic: { LinearGradient: class LinearGradient {} }
}))
vi.mock('echarts/charts', () => ({ LineChart: {}, RadarChart: {}, GaugeChart: {} }))
vi.mock('echarts/components', () => ({
  TooltipComponent: {},
  GridComponent: {},
  RadarComponent: {}
}))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))
vi.mock('@/composables/useECharts', () => ({
  useECharts: () => ({ init: vi.fn(), updateOption: vi.fn() })
}))

// element-plus 用 importOriginal 部分 mock：保住组件导出，只替换 ElMessage / ElMessageBox。
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

import Metrics from '@/views/health/Metrics.vue'
import Score from '@/views/health/Score.vue'
import { healthApi } from '@/api/health'
import { scoreApi } from '@/api/score'
import { ApiError } from '@/utils/appError'
import { ElMessage } from 'element-plus'

const getMetricsMock = healthApi.getMetrics as unknown as Mock
const deleteMetricMock = healthApi.deleteMetric as unknown as Mock
const getScoreMock = scoreApi.getScore as unknown as Mock
const getScoreHistoryMock = scoreApi.getScoreHistory as unknown as Mock
const generateReportMock = scoreApi.generateReport as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/** showError 的 VNode 闩锁是模块级、跨用例存活的。让 Date.now 单调前进 60s。 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

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

const metricStubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-form': { template: '<form class="el-form"><slot /></form>', props: ['inline', 'model'] },
  'el-form-item': { template: '<div class="el-form-item"><slot /></div>', props: ['label'] },
  'el-select': {
    template: '<select class="el-select"><slot /></select>',
    props: ['modelValue', 'placeholder', 'clearable', 'style']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-date-picker': {
    template: '<input class="el-date-picker" />',
    props: ['modelValue', 'type', 'rangeSeparator', 'startPlaceholder', 'endPlaceholder', 'valueFormat']
  },
  'el-table': { template: '<div class="el-table"><slot /></div>', props: ['data', 'stripe'] },
  'el-table-column': {
    template: '<div class="el-table-column"><slot name="default" :row="{}" /></div>',
    props: ['prop', 'label', 'width', 'fixed', 'sortable']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size', 'effect'] },
  'el-button': {
    template: '<button class="el-button" type="button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'link', 'icon']
  },
  'el-pagination': {
    template: '<div class="el-pagination" />',
    props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout']
  },
  UniversalMetricDialog: { template: '<div class="universal-metric-dialog-stub" />' }
}

const scoreStubs = {
  ...metricStubs,
  'el-skeleton': { template: '<div class="el-skeleton"><slot /></div>', props: ['loading', 'animated'] },
  'el-skeleton-item': { template: '<div class="el-skeleton-item" />', props: ['variant'] },
  'el-empty': { template: '<div class="el-empty"><slot name="image" /><slot /></div>', props: ['description'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot /></div>', props: ['span', 'xs', 'sm', 'md', 'lg'] },
  'el-progress': { template: '<div class="el-progress" />', props: ['percentage', 'color', 'strokeWidth'] },
  'el-radio-group': { template: '<div class="el-radio-group"><slot /></div>', props: ['modelValue'] },
  'el-radio-button': { template: '<label class="el-radio-button"><slot /></label>', props: ['value'] },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'top']
  },
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['title', 'column', 'border'] },
  'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label'] },
  'el-alert': { template: '<div class="el-alert"><slot /></div>', props: ['title', 'description', 'type', 'showIcon'] },
  'el-timeline': { template: '<div class="el-timeline"><slot /></div>' },
  'el-timeline-item': { template: '<div class="el-timeline-item"><slot /></div>', props: ['type'] },
  HealthScoreCircle: {
    name: 'HealthScoreCircle',
    props: ['score', 'level', 'size', 'strokeWidth'],
    template: '<div class="stub-health-score">{{ score }}</div>'
  }
}

function mountMetrics() {
  return mount(Metrics, {
    global: { stubs: metricStubs, directives: { loading: () => {} } }
  })
}

function mountScore() {
  return mount(Score, {
    global: { stubs: scoreStubs, directives: { loading: () => {} } }
  })
}

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

function makeScore() {
  return {
    score: 72,
    level: 'GOOD',
    factors: {},
    calculationMethod: 'RULE',
    calculatedAt: '2026-11-09 10:00:00',
    expiresAt: '2026-11-09 12:00:00',
    isCached: false
  }
}

describe('health/Metrics.vue + health/Score.vue — 失败恰好一条、后端文案优先（ERR-07）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getMetricsMock.mockResolvedValue(ok({ records: [], total: 0 }))
    deleteMetricMock.mockResolvedValue(ok(true))
    getScoreMock.mockResolvedValue(ok(makeScore()))
    getScoreHistoryMock.mockResolvedValue(ok([]))
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('指标列表加载失败：恰好一条提示，文案是字典里可执行的下一句', async () => {
    getMetricsMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, message: '' } }
    })

    const wrapper = mountMetrics()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('无法加载指标记录，请点击重试')

    wrapper.unmount()
  })

  it('指标列表加载遇到 forbidden（AUTH_FORBIDDEN 信封）：仍恰好一条提示，不是静默', async () => {
    // 本页不渲染 ErrorState，`forbidden` 没有状态块可落。缺 presentStates 时
    // showError 在 resolveMessage 之前就返回 → 0 条，一次权限失败会完全静默。
    getMetricsMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'auth.forbidden', message: '无权查看该指标数据' }
      }
    })

    const wrapper = mountMetrics()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('无权查看该指标数据')

    wrapper.unmount()
  })

  it('指标删除失败：恰好一条提示，成功路径的成功提示没有误弹', async () => {
    deleteMetricMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, message: '' } }
    })

    const wrapper = mountMetrics()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.handleDelete({ id: 5, metricKey: 'heartRate' })
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)
    expect(lastErrorToastText()).toBe('无法删除该条指标记录，请稍后重试')

    wrapper.unmount()
  })

  it('报告生成失败：toast 里是后端下发的消息，不是字典兜底（回归护栏）', async () => {
    // 后端消息刻意与字典「报告生成未完成，请稍后重试」不同。旧实现读
    // `error.response?.data?.message`，而 ApiError 没有 response 属性，
    // 因此它只会显示字典兜底 —— 这条断言对旧实现必然失败。
    const backendMessage = '报告服务当前繁忙，请在两分钟后重新生成'
    generateReportMock.mockRejectedValueOnce(
      new ApiError(backendMessage, 'business')
    )

    const wrapper = mountScore()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.regenerateReport()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe(backendMessage)
    expect(lastErrorToastText()).not.toBe('报告生成未完成，请稍后重试')

    wrapper.unmount()
  })

  it('报告生成被取消（真实 ApiError(\'\', \'cancel\')）：零提示（ERR-08）', async () => {
    // 用真实的归一化取消形态而不是裸字符串：Phase 11 之后真正到达 catch 的就是它。
    // 旧的手写 name 比较（对 'ApiError' 恒为假）会把它当成失败并弹红灯。
    generateReportMock.mockRejectedValueOnce(new ApiError('', 'cancel'))

    const wrapper = mountScore()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.regenerateReport()
    await flushPromises()

    expect(toastCount()).toBe(0)

    wrapper.unmount()
  })

  it('评分加载失败（notFound 信封）：仍恰好一条提示，不是静默', async () => {
    // 本页不渲染 ErrorState；缺 presentStates 时 showError 在 resolveMessage
    // 之前就返回 → 0 条，一次「还没有评分档案」会变成完全静默。
    getScoreMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'health.score_not_found', message: '尚无评分记录' }
      }
    })

    const wrapper = mountScore()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('尚无评分记录')

    wrapper.unmount()
  })

  it('报告生成成功：成功提示仍在（happy path 未被迁移删掉）', async () => {
    generateReportMock.mockResolvedValueOnce(ok({ generatedAt: '2026-11-09 10:00:00' }))

    const wrapper = mountScore()
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.regenerateReport()
    await flushPromises()

    expect(ElMessageMock.success).toHaveBeenCalledTimes(1)
    expect(errorToastCount()).toBe(0)

    wrapper.unmount()
  })
})
