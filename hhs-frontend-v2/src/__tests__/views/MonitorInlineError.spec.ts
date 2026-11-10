import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * ERR-03 / ERR-05 / ERR-08 的端到端锚点（`views/realtime/Monitor.vue`）。
 *
 * 这个页面的行内表单原先有 `:model` 但**一个 `prop` 都没有**，并且在发出请求前用
 * `ElMessage.warning('请填写完整信息')` 预检。两个用例各自证明一条无法用 grep 证明的性质：
 *
 *  1. 一个 `value` 的 400 → 汇总读作「指标数值」、恰好一项内联、零 toast。
 *  2. 一个 500 → 恰好一条 toast，**并且** `form.value` 与 `form.metricKey` 仍是用户
 *     输入的值。这一条是 ERR-05 的护栏：`Object.assign(form, { … })` 是成功分支里的
 *     部分重置，任何人把它移出 try 或复制进 catch 都会让这条断言失败 —— 而那种改动
 *     会把一次常规失败变成静默数据丢失（T-14-25）。
 *  3. 趋势加载失败 → 恰好一条 toast（本页无 `ErrorState`，`presentStates: true` 承重）。
 *
 * 模块作用域 `echarts.use([...])` 会在 import 时执行，因此 echarts 必须显式 mock
 * （同 ScoreEstimateBadge.spec.ts:94-110）。图表容器组件与组合式函数一并 stub，
 * 避免在 happy-dom 里真的初始化一个 canvas。
 */

const { getLatestMetricsMock, getTrendMock, addMetricMock } = vi.hoisted(() => ({
  getLatestMetricsMock: vi.fn(),
  getTrendMock: vi.fn(),
  addMetricMock: vi.fn()
}))

vi.mock('@/api/realtime', () => ({
  realtimeApi: {
    addMetric: addMetricMock,
    getLatestMetrics: getLatestMetricsMock,
    getMetricTrend: getTrendMock
  }
}))

// 真实 store 会在 connect() 里 new WebSocket；本 spec 只关心表单，故整体替换。
vi.mock('@/stores/realtime', () => ({
  useRealtimeStore: () => ({
    connected: false,
    connecting: false,
    errorMessage: null,
    latestMetrics: [],
    connect: vi.fn(),
    disconnect: vi.fn(),
    sendPing: vi.fn()
  })
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ token: null })
}))

vi.mock('@/composables/useECharts', () => ({
  useECharts: () => ({
    chartInstance: { value: null },
    init: vi.fn(),
    updateOption: vi.fn(),
    handleResize: vi.fn()
  })
}))

vi.mock('@/composables/useGsap', () => ({
  staggerReveal: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  tilt3D: vi.fn(),
  glowPulse: vi.fn(),
  buttonPress: vi.fn(),
  hoverLift: vi.fn(),
  rippleClick: vi.fn(),
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: vi.fn(),
  graphic: {
    LinearGradient: class LinearGradient {}
  }
}))
vi.mock('echarts/charts', () => ({ LineChart: {} }))
vi.mock('echarts/components', () => ({ TooltipComponent: {}, GridComponent: {} }))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

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

import Monitor from '@/views/realtime/Monitor.vue'
import { ElMessage } from 'element-plus'

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/**
 * showError 的 VNode 分支有模块级 2000ms 去重闩锁。同文案的用例会互相吞掉对方的
 * toast，让断言读成 0 —— 那是闩锁生效，不是呈现缺失。让每次 Date.now() 前进 60s。
 */
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

let nextClientInvalid: Record<string, Array<{ message?: string }>> | undefined

const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-skeleton': {
    template: '<div class="el-skeleton"><slot /></div>',
    props: ['loading', 'animated']
  },
  'el-skeleton-item': { template: '<div class="el-skeleton-item" />', props: ['variant', 'style'] },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  // FormErrorSummary 用 #title 插槽承载标题；连接状态卡片用的是 :title 属性。
  // 两者都渲染，才能同时服务汇总块与页面自身的告警条。
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" />{{ title }}</div>',
    props: ['title', 'type', 'closable', 'showIcon']
  },
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: (cb: (valid: boolean, invalidFields?: unknown) => void) => {
        if (nextClientInvalid) {
          const invalid = nextClientInvalid
          nextClientInvalid = undefined
          return Promise.resolve(cb(false, invalid))
        }
        return Promise.resolve(cb(true, undefined))
      },
      validateField: vi.fn(),
      clearValidate: vi.fn(),
      resetFields: vi.fn()
    }
  },
  'el-form-item': {
    template:
      '<div class="el-form-item" :class="{ \'is-error\': !!error }"><label>{{ label }}</label><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['label', 'prop', 'error']
  },
  'el-input': {
    template: '<input class="el-input" :value="modelValue" />',
    props: ['modelValue', 'placeholder', 'type', 'rows', 'readonly', 'style']
  },
  'el-input-number': {
    template: '<input class="el-input-number" :value="modelValue" />',
    props: ['modelValue', 'precision', 'min', 'max', 'style']
  },
  'el-select': {
    template: '<select class="el-select"><slot /></select>',
    props: ['modelValue', 'placeholder', 'style', 'disabled']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-option-group': {
    template: '<optgroup class="el-option-group"><slot /></optgroup>',
    props: ['label']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'link']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  // 图表容器不需要真的渲染 echarts。
  GaugeChart: { template: '<div class="gauge-chart-stub" />', props: ['value', 'title'] }
}

function mountMonitor() {
  return mount(Monitor, {
    attachTo: document.body,
    global: { stubs: globalStubs, directives: { loading: () => {} } }
  })
}

describe('realtime/Monitor.vue — 行内表单的服务端错误投影 + 成功-only 重置（ERR-03 / ERR-05）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    nextClientInvalid = undefined
    getLatestMetricsMock.mockReset()
    getTrendMock.mockReset()
    addMetricMock.mockReset()
    getLatestMetricsMock.mockResolvedValue({ code: 200, message: 'success', data: [] })
    getTrendMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { metricKey: 'heartRate', metricDisplayName: '心率', unit: 'bpm', dataPoints: [] }
    })
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    expect(ElMessageMock.warning).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('400：汇总读作「指标数值」、一项内联、零 toast，且表单值原样保留', async () => {
    const wrapper = mountMonitor()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.metricKey = 'heartRate'
    vm.form.value = 72.5
    vm.form.unit = 'bpm'

    addMetricMock.mockRejectedValueOnce({
      response: {
        status: 400,
        data: {
          code: 400,
          message: '请检查以下填写内容',
          errorCode: 'validation.invalid_parameter',
          fieldErrors: [{ field: 'value', message: '数值必须大于 0' }]
        }
      }
    })

    await vm.handleAdd()
    await flushPromises()

    // 1. 零 toast —— validation 由汇总 + 内联承担呈现（D-01）。
    expect(toastCount()).toBe(0)

    // 2. 汇总读作「指标数值」（全局 FIELD_LABELS 的 value 标签，本页无需覆盖）。
    const summary = wrapper.find('.form-error-summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('请修正以下 1 项填写问题')
    const items = summary.findAll('li')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toBe('指标数值：数值必须大于 0')

    // 3. 恰好一项带 is-error，且只有 `value` 那一项。
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(1)
    expect(wrapper.findAll('.el-form-item__error').map(node => node.text())).toEqual(['数值必须大于 0'])

    // 4. 焦点去汇总一次（Contract 4.4）。
    expect(document.activeElement).toBe(summary.element as HTMLElement)

    // 5. ERR-05：失败不清空用户输入。
    expect(vm.form.metricKey).toBe('heartRate')
    expect(vm.form.value).toBe(72.5)

    wrapper.unmount()
  })

  it('500：恰好一条 toast，且已选的指标与已输入的数值都还在（成功-only 重置护栏）', async () => {
    const wrapper = mountMonitor()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.metricKey = 'glucose'
    vm.form.value = 5.8
    vm.form.unit = 'mmol/L'

    addMetricMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    await vm.handleAdd()
    await flushPromises()

    // 1. 传输层 5xx → server kind → 一条 VNode 常驻 toast（带重试入口）。
    expect(errorToastCount()).toBe(1)

    // 2. 这条断言是护栏本身：`Object.assign(form, { metricKey: '', value: 0, unit: '' })`
    //    只允许在成功分支里执行。若有人把它移出 try、复制进 catch，或改成 finally，
    //    这三个字段就会变成空/0，本条失败。
    expect(vm.form.metricKey).toBe('glucose')
    expect(vm.form.value).toBe(5.8)
    expect(vm.form.unit).toBe('mmol/L')

    // 3. 失败不产生凭空的内联错误：`apply()` 只处理 validation。
    expect(wrapper.find('.form-error-summary').exists()).toBe(false)
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(0)

    wrapper.unmount()
  })

  it('成功路径：重置仍然发生（证明上一条不是靠「永远不重置」空过的）', async () => {
    const wrapper = mountMonitor()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.form.metricKey = 'weight'
    vm.form.value = 68
    vm.form.unit = 'kg'

    addMetricMock.mockResolvedValueOnce({ code: 200, message: 'success', data: {} })

    await vm.handleAdd()
    await flushPromises()

    expect(vm.form.metricKey).toBe('')
    expect(vm.form.value).toBe(0)
    expect(vm.form.unit).toBe('')

    wrapper.unmount()
  })

  it('空表单提交（客户端校验失败）：汇总 + 内联、零 toast，取代原来那句含糊的 warning', async () => {
    const wrapper = mountMonitor()
    await flushPromises()

    const vm = wrapper.vm as any
    // 空表单是合法输入状态：按钮不禁用，提交按钮仍然可点（E8）。
    expect(vm.form.metricKey).toBe('')
    expect(vm.form.value).toBe(0)

    // EP 的真实回调给出 invalidFields；stub 按同一签名回放（见文件头注释）。
    nextClientInvalid = {
      value: [{ message: '请输入指标数值' }],
      metricKey: [{ message: '请选择指标类型' }]
    }

    await vm.handleAdd()
    await flushPromises()

    // 1. 汇总按 fieldOrder 排序（metricKey 先于 value），而不是按 invalidFields 的键序。
    const items = wrapper.findAll('.form-error-summary li')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toBe('指标类型：请选择指标类型')
    expect(items[1].text()).toBe('指标数值：请输入指标数值')

    // 2. 零 toast —— 原来的 `ElMessage.warning('请填写完整信息')` 已删除。
    expect(toastCount()).toBe(0)

    // 3. 客户端校验拦下了请求。
    expect(addMetricMock).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('趋势加载失败：恰好一条 toast（本页无状态块，presentStates 承重）', async () => {
    getTrendMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, errorCode: 'auth.forbidden', message: '没有访问权限' } }
    })

    const wrapper = mountMonitor()
    await flushPromises()

    // forbidden 走 VNode 分支：一条常驻 toast，文案取后端消息。
    expect(errorToastCount()).toBe(1)
    const options = ElMessageMock.mock.calls[0][0] as { message: { children?: unknown[] } }
    const text = (options.message.children as Array<{ children?: unknown }>)[0]
    expect(text?.children).toBe('没有访问权限')

    wrapper.unmount()
  })
})