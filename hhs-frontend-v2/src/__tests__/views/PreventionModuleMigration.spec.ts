import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Mock } from 'vitest'

/**
 * Phase 14 / plan 14-11 的端到端锚点（`prevention/Metrics.vue` + `prevention/Risk.vue`）。
 *
 * 三条不变量：
 *
 * 1. **取消不是失败（ERR-08 的第九处、也是最后一处）。** `prevention/Metrics.vue:131`
 *    原来比较两个哨兵值，第二个（关闭对话框）永远不会被 `ElMessageBox.confirm` 产生。
 *    拆分后确认框的 catch 是裸 `return`，请求的 catch 才走 `showError`。取消用例必须
 *    **同时**断言「零提示」与「一个 DELETE 都没发」—— 后半句才能把「真的没发请求」
 *    和「发了请求但被静默吞掉」区分开。
 * 2. **恰好一条提示，且文案来自字典。** 确认之后 DELETE 失败 → 1 条，且等于
 *    `metric.deleteFailed` 的字典条文（不是被禁的「删除失败」/「操作失败」）。
 * 3. **键复用而非新造。** 风险页复用 `profile.*`：它是健康档案的「风险」页，
 *    两者共享同一个后端聚合。断言文案逐字等于字典条目，证明走的是既有键而不是兜底。
 */

vi.mock('@/api/wellness', () => ({
  wellnessApi: {
    getMetrics: vi.fn(),
    getSummary: vi.fn(),
    getLatest: vi.fn(),
    getTrend: vi.fn(),
    createMetric: vi.fn(),
    updateMetric: vi.fn(),
    deleteMetric: vi.fn()
  }
}))

vi.mock('@/api/metric-meta', () => ({
  metricMetaApi: {
    getMetadata: vi.fn().mockResolvedValue({ code: 200, message: 'success', data: {} })
  }
}))

vi.mock('@/api/prevention', () => ({
  preventionApi: {
    createRiskAssessment: vi.fn(),
    getRiskAssessments: vi.fn(),
    createProfile: vi.fn(),
    updateProfile: vi.fn(),
    getProfile: vi.fn(),
    getCompleteness: vi.fn()
  }
}))

vi.mock('vue-router', async importOriginal => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ query: {} }),
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
  }
})

vi.mock('@/composables/useGsap', () => {
  const timeline = { fromTo: () => timeline }
  return {
    gsap: { timeline: () => timeline },
    scrollReveal: vi.fn(),
    cleanupScrollTriggers: vi.fn(),
    staggerReveal: vi.fn(),
    countUp: vi.fn(),
    tilt3D: vi.fn(),
    magneticHover: vi.fn(),
    parallax: vi.fn(),
    scrollScale: vi.fn(),
    buttonPress: vi.fn(),
    hoverLift: vi.fn(),
    hoverShine: vi.fn(),
    rippleClick: vi.fn(),
    DUR: { fast: 0.2, mid: 0.5, slow: 0.8, dramatic: 1.2 },
    EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
  }
})

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

import PreventionMetrics from '@/views/prevention/Metrics.vue'
import Risk from '@/views/prevention/Risk.vue'
import { wellnessApi } from '@/api/wellness'
import { preventionApi } from '@/api/prevention'
import { ElMessage, ElMessageBox } from 'element-plus'

const getMetricsMock = wellnessApi.getMetrics as unknown as Mock
const deleteMetricMock = wellnessApi.deleteMetric as unknown as Mock
const getRiskAssessmentsMock = preventionApi.getRiskAssessments as unknown as Mock
const getCompletenessMock = preventionApi.getCompleteness as unknown as Mock
const getProfileMock = preventionApi.getProfile as unknown as Mock
const confirmMock = ElMessageBox.confirm as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/** showError 的 VNode 闩锁是模块级、跨用例存活的。让 Date.now 单调前进 60s。 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

function errorToastCount(): number {
  const objectCalls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

function toastCount(): number {
  return (
    ElMessageMock.mock.calls.length +
    ElMessageMock.error.mock.calls.length +
    ElMessageMock.success.mock.calls.length +
    ElMessageMock.warning.mock.calls.length +
    ElMessageMock.info.mock.calls.length
  )
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

/** 两个页面的共享 stub 集：只保留布局语义，不实现 EP 的真实行为。 */
const baseStubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-table': { template: '<div class="el-table"><slot /></div>', props: ['data', 'stripe'] },
  'el-table-column': {
    template: '<div class="el-table-column"><slot name="default" :row="{}" /></div>',
    props: ['prop', 'label', 'width', 'minWidth', 'fixed', 'sortable', 'showOverflowTooltip']
  },
  'el-pagination': {
    template: '<div class="el-pagination" />',
    props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size', 'effect'] },
  'el-button': {
    template: '<button class="el-button" type="button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'plain', 'icon', 'link', 'text']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /><slot /></div>',
    props: ['type', 'closable', 'showIcon', 'title']
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width']
  },
  'el-descriptions': { template: '<div class="el-descriptions"><slot /></div>', props: ['column', 'border'] },
  'el-descriptions-item': { template: '<div class="el-descriptions-item"><slot /></div>', props: ['label'] },
  'el-form': { template: '<form class="el-form"><slot /></form>', props: ['model', 'rules', 'labelWidth'] },
  'el-form-item': {
    template: '<div class="el-form-item"><slot /></div>',
    props: ['label', 'prop', 'error']
  },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': { template: '<div class="el-col"><slot /></div>', props: ['span', 'xs', 'sm', 'md', 'lg'] },
  'el-input': { template: '<input class="el-input" />', props: ['modelValue', 'placeholder', 'type', 'rows'] },
  'el-input-number': {
    template: '<input class="el-input-number" />',
    props: ['modelValue', 'min', 'max', 'precision', 'placeholder', 'disabled']
  },
  'el-select': { template: '<select class="el-select"><slot /></select>', props: ['modelValue', 'placeholder'] },
  'el-option': { template: '<option class="el-option">{{ label }}</option>', props: ['label', 'value'] },
  'el-date-picker': { template: '<input class="el-date-picker" />', props: ['modelValue', 'type', 'placeholder', 'valueFormat'] },
  'el-radio-group': { template: '<div class="el-radio-group"><slot /></div>', props: ['modelValue'] },
  'el-radio': { template: '<label class="el-radio"><slot /></label>', props: ['value', 'label'] },
  'el-rate': { template: '<div class="el-rate" />', props: ['modelValue', 'max', 'showText', 'texts'] },
  'el-skeleton': { template: '<div class="el-skeleton"><slot /></div>', props: ['loading', 'animated'] },
  'el-skeleton-item': { template: '<div class="el-skeleton-item" />', props: ['variant'] },
  'el-tooltip': { template: '<span class="el-tooltip"><slot /></span>', props: ['content', 'placement'] },
  'el-empty': { template: '<div class="el-empty">{{ description }}</div>', props: ['description'] },
  'el-text': { template: '<span class="el-text"><slot /></span>', props: ['type'] },
  /**
   * 批次折叠 stub：**必须渲染 #title 插槽**。未 stub 的 <el-collapse> 会丢弃
   * 默认插槽 —— 批次内容静默消失，而所有既有断言照旧通过。
   */
  'el-collapse': { template: '<div class="el-collapse"><slot /></div>', props: ['modelValue'] },
  'el-collapse-item': {
    template:
      '<div class="el-collapse-item">' +
      '<div class="el-collapse-item__header"><slot name="title" /></div>' +
      '<div class="el-collapse-item__content"><slot /></div></div>',
    props: ['name', 'title']
  },
  Plus: { template: '<span class="icon-plus" />' },
  EstimateBadge: { template: '<span class="estimate-badge" />', props: ['completeness', 'assumptions'] },
  UniversalMetricDialog: { template: '<div class="universal-metric-dialog" />', props: ['modelValue', 'category', 'title', 'initialData'] },
  FormErrorSummary: { template: '<div class="form-error-summary" />' },
  CriticalConfirmDialog: { template: '<div class="critical-confirm-dialog" />', props: ['modelValue'] }
}

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

function mountPreventionMetrics() {
  return mount(PreventionMetrics, {
    global: { stubs: baseStubs, directives: { loading: () => {} } }
  })
}

function mountRisk() {
  return mount(Risk, {
    attachTo: document.body,
    global: { stubs: baseStubs, directives: { loading: () => {} } }
  })
}

const METRIC_ROWS = [
  {
    id: 31,
    metricKey: 'sleepDuration',
    metricName: '睡眠时长',
    value: 7.5,
    unit: '小时',
    recordDate: '2026-11-10',
    createTime: '2026-11-10 10:00:00'
  }
]

describe('prevention/Metrics.vue — 取消零提示且不发请求，真实失败恰好一条（ERR-08 / ERR-07）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getMetricsMock.mockResolvedValue(ok({ records: METRIC_ROWS, total: 1 }))
    deleteMetricMock.mockResolvedValue(ok(true))
    confirmMock.mockReset()
    confirmMock.mockResolvedValue('confirm')
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('取消删除确认框：零提示，且一个 DELETE 都没发', async () => {
    // 用 AbortError 而不是字符串 'cancel'：旧的手写判断对字符串恰好能用，
    // 因此字符串形态证明不了任何事（14-05 Rule 1 偏差记录）。AbortError 正是
    // 旧判断会误判成真失败的那种形态。
    confirmMock.mockRejectedValueOnce(Object.assign(new Error('canceled'), { name: 'AbortError' }))

    const wrapper = mountPreventionMetrics()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      handleDelete: (row: { id: number }) => Promise<void>
    }
    await vm.handleDelete(METRIC_ROWS[0])
    await flushPromises()

    expect(toastCount()).toBe(0)
    // 后半句是 ERR-08 的核心：静默不能掩盖一个被吞掉的请求。
    expect(deleteMetricMock).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('确认后 DELETE 失败：恰好一条提示，文案是字典的 metric.deleteFailed', async () => {
    // 信封业务失败且后端未下发 message（HTTP 200 + code 500 + message 空）
    // → normalizeApiError 判 business，resolveMessage 落到字典，
    // 因此这条断言证明 `metric.deleteFailed` 真的接上了。
    deleteMetricMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, message: '' } }
    })

    const wrapper = mountPreventionMetrics()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      handleDelete: (row: { id: number }) => Promise<void>
      metrics: unknown[]
    }
    const before = vm.metrics
    await vm.handleDelete(METRIC_ROWS[0])
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(deleteMetricMock).toHaveBeenCalledWith(31)

    const text = lastErrorToastText()
    expect(text).not.toBe('删除失败')
    expect(text).not.toBe('操作失败')
    expect(text).toBe('无法删除该条指标记录，请稍后重试')
    // 失败不刷新、不清空：既有行必须还在（UI Considerations · error E7）。
    expect(vm.metrics).toBe(before)

    wrapper.unmount()
  })

  it('列表加载失败：一条提示，且表格保留原有行（不清空）', async () => {
    const wrapper = mountPreventionMetrics()
    await flushPromises()

    const vm = wrapper.vm as unknown as { fetchMetrics: () => Promise<void>; metrics: unknown[] }
    const before = vm.metrics
    expect(before).toHaveLength(1)

    getMetricsMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    await vm.fetchMetrics()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(vm.metrics).toBe(before)
    expect(vm.metrics).toHaveLength(1)

    wrapper.unmount()
  })
})

describe('prevention/Risk.vue — 复用 profile.* 键，列表失败恰好一条（ERR-07）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getRiskAssessmentsMock.mockResolvedValue(ok([]))
    getProfileMock.mockRejectedValue(Object.assign(new Error('not found'), {
      response: { status: 404, data: { code: 404 } }
    }))
    getCompletenessMock.mockResolvedValue(
      ok({
        score: 0,
        level: '基础档案',
        filledCount: 0,
        totalCount: 8,
        hasProfile: false,
        assumptions: [],
        fields: []
      })
    )
  })

  afterEach(() => {
    // 迁移后本文件零裸 error toast。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('评估列表加载失败：恰好一条提示，文案解析到 profile.loadFailed 的字典条文', async () => {
    // 键复用是本 plan 的决定：风险页是健康档案的风险页，两者共享同一后端聚合。
    // 断言逐字等于字典条目 —— 走的是既有键，不是兜底、也不是新造的 risk.*。
    // 用「HTTP 200 + 信封 code 500 + 无 message」：归一化判 business 且消息为空，
    // 才会真正落到字典；传裸 500 会让第 7 步先填上「服务暂时不可用」。
    getRiskAssessmentsMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, message: '' } }
    })

    const wrapper = mountRisk()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('无法加载健康档案，请稍后重试')

    wrapper.unmount()
  })

  it('列表失败保留已有评估行（不清空）', async () => {
    getRiskAssessmentsMock.mockResolvedValueOnce(
      ok([
        {
          id: 5,
          riskLevel: 'LOW',
          score: 12,
          assumptions: [],
          createTime: '2026-11-10 09:00:00'
        }
      ])
    )

    const wrapper = mountRisk()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      fetchAssessments: () => Promise<void>
      assessments: unknown[]
    }
    expect(vm.assessments).toHaveLength(1)
    const before = vm.assessments

    getRiskAssessmentsMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    await vm.fetchAssessments()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(vm.assessments).toBe(before)
    expect(vm.assessments).toHaveLength(1)

    wrapper.unmount()
  })
})

describe('prevention — 源码级契约（零裸 toast / presentStates 全覆盖 / 取消判断消失 / 键复用）', () => {
  function read(relPath: string): string {
    return readFileSync(resolve(process.cwd(), relPath), 'utf8')
  }

  it('两个文件都没有裸的 error toast', () => {
    expect(read('src/views/prevention/Metrics.vue')).not.toContain('ElMessage.error')
    expect(read('src/views/prevention/Risk.vue')).not.toContain('ElMessage.error')
  })

  it('两个文件的每个 showError 都带 presentStates: true（无状态块 → 不许静默）', () => {
    for (const rel of ['src/views/prevention/Metrics.vue', 'src/views/prevention/Risk.vue']) {
      const source = read(rel)
      const calls = source.split('showError(').length - 1
      expect(calls).toBeGreaterThan(0)
      expect(source.split('presentStates: true').length - 1).toBe(calls)
      expect(source).not.toContain('ErrorState')
    }
  })

  it('Metrics 的手写取消比较已消失，且确认框的 catch 是裸 return', () => {
    const source = read('src/views/prevention/Metrics.vue')
    // 注释里也不得留下可被 grep 命中的字面量（14-08/14-09 的先例）。
    expect(source).not.toContain("!== 'cancel'")
    expect(source).not.toContain("!== 'close'")
    const body = source.slice(source.indexOf('const handleDelete'))
    expect(body).toContain('} catch {')
    expect(body).toContain('return')
  })

  it('风险页复用 profile.* 而非新造 risk.*', () => {
    const source = read('src/views/prevention/Risk.vue')
    expect(source).toContain("showError(error, 'profile.loadFailed', { presentStates: true })")
    expect(source).toContain("showError(error, 'profile.saveFailed', { presentStates: true })")
    expect(source).not.toContain("'risk.loadFailed'")
    expect(source).not.toContain("'risk.saveFailed'")
  })

  it('风险页不再二次填表：评估直接读档案，面板只读', () => {
    const source = read('src/views/prevention/Risk.vue')
    // 补填表单已移除 —— 评估读取健康档案与指标，不要求用户重复录入。
    expect(source).not.toContain('useFormServerErrors(')
    expect(source).not.toContain('FormErrorSummary')
    expect(source).not.toContain('collectInvalidFieldMessages')
    // 取而代之的是只读数据来源面板、加载态与逐项归因。
    expect(source).toContain('dataSources')
    expect(source).toContain('risk-preparing')
    expect(source).toContain('contributors')
  })
})