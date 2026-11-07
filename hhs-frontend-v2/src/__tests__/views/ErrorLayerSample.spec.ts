import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Mock } from 'vitest'

/**
 * 错误层语义的端到端样本：
 * 1. push store —— 单一 toast 出口 + 布尔返回值契约（T-11-21）
 * 2. Thresholds —— RESOURCE_ALREADY_EXISTS 的「打开编辑」恢复动作（D-08）
 *
 * element-plus 用 importOriginal 部分 mock：保住组件导出，只替换 ElMessage，
 * 这样才能既挂载页面组件、又对每次 toast 调用（含对象式）计数。
 */

vi.mock('@/api/push', () => ({
  getPushConfigs: vi.fn(),
  updatePushConfig: vi.fn(),
  testPushChannel: vi.fn(),
  getPushHistory: vi.fn(),
  getPushStats: vi.fn()
}))

vi.mock('@/api/threshold', () => ({
  thresholdApi: {
    getThresholds: vi.fn(),
    getByUserId: vi.fn(),
    getByMetricKey: vi.fn(),
    createThreshold: vi.fn(),
    updateThreshold: vi.fn(),
    deleteThreshold: vi.fn()
  }
}))

// Thresholds.vue 的 onMounted 消费 gsap / DUR / EASE；形状照抄 PreventionAndWellness.spec.ts:73。
vi.mock('@/composables/useGsap', () => ({
  gsap: {
    timeline: () => ({
      fromTo: vi.fn().mockReturnThis()
    })
  },
  cleanupScrollTriggers: vi.fn(),
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

// Thresholds.vue 读 authStore.user?.id 填 form.userId。
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { id: 1, username: 'tester' } })
}))

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

import { usePushStore } from '@/stores/push'
import { getPushConfigs, updatePushConfig } from '@/api/push'
import Thresholds from '@/views/health/Thresholds.vue'
import { thresholdApi } from '@/api/threshold'
import { ElMessage } from 'element-plus'

const updatePushConfigMock = updatePushConfig as unknown as Mock
const getPushConfigsMock = getPushConfigs as unknown as Mock
const getThresholdsMock = thresholdApi.getThresholds as unknown as Mock
const createThresholdMock = thresholdApi.createThreshold as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

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
    ([options]) => (options as { type?: string })?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

interface VNodeLike {
  type?: unknown
  props?: Record<string, unknown>
  children?: unknown
}

/** 深度遍历 VNode，返回第一个带指定 class 片段的节点。 */
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

describe('ErrorLayerSample — push store 单一出口与返回值契约', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('1. 保存失败：返回 false、恰好一条错误 toast、零成功提示', async () => {
    // 500 → kind='server' → VNode 常驻 toast（可在 message 里断言）。
    updatePushConfigMock.mockRejectedValueOnce({
      response: { status: 500, data: {} }
    })

    const store = usePushStore()
    const ok = await store.saveConfig('EMAIL', { configValue: 'a@b.com' })

    expect(ok).toBe(false)
    expect(errorToastCount()).toBe(1)
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)
    // 函数式旧 API 全面退场
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
  })

  it('2. 保存成功：返回 true、store 不弹成功提示（调用方拥有）、零错误 toast', async () => {
    updatePushConfigMock.mockResolvedValueOnce({
      code: 200,
      message: 'success',
      data: {
        id: 1,
        channelType: 'EMAIL',
        channelLabel: '邮件',
        configKey: 'address',
        configValue: 'a@b.com',
        enabled: true,
        offlineSupported: true,
        isConfigured: true,
        createdAt: '',
        updatedAt: ''
      }
    })

    const store = usePushStore()
    const ok = await store.saveConfig('EMAIL', { configValue: 'a@b.com' })

    expect(ok).toBe(true)
    // WR-07：成功反馈归调用方，避免开关/保存各弹一条堆叠提示。
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)
    expect(errorToastCount()).toBe(0)
  })

  it('3. 用户取消：返回 false 且任何 toast 都不出现（D-12）', async () => {
    updatePushConfigMock.mockRejectedValueOnce('cancel')

    const store = usePushStore()
    const ok = await store.saveConfig('EMAIL', { configValue: 'a@b.com' })

    expect(ok).toBe(false)
    expect(toastCount()).toBe(0)
  })

  it('4. 连续两次网络失败：VNode 闩锁把重复提示压到 1 条（<= 1）', async () => {
    // 网络错误走 showError 自身的 2000ms VNode 闩锁（EP grouping 不支持 VNode）。
    // 必须是原生 Axios 错误形态（isAxiosError=true 且无 response），见 WR-17。
    updatePushConfigMock.mockRejectedValue({
      isAxiosError: true,
      request: {},
      message: 'Network Error'
    })

    const store = usePushStore()
    await store.saveConfig('EMAIL', { configValue: 'a@b.com' })
    await store.saveConfig('EMAIL', { configValue: 'a@b.com' })

    expect(errorToastCount()).toBeLessThanOrEqual(1)
  })

  it('4b. 加载失败：零 toast，只写 loadError 交给错误状态块（D-02 分工）', async () => {
    // D-02：页面级加载失败由 ErrorState 承担呈现与重试，不得同时弹 toast。
    // 回归来源：loadConfigs 曾按 options.silent 放行 toast，而 PushSettings.vue
    // 无参调用 → 常驻 toast + 错误块 = 同一失败两次呈现、两个重试入口。
    getPushConfigsMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const store = usePushStore()
    await store.loadConfigs()

    expect(toastCount()).toBe(0)
    expect(store.loadError).toBeTruthy()
  })

  it('5. push store 源码内不再有 ElMessage.error（单一出口）', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/stores/push.ts'), 'utf8')
    const codeLines = source
      .split('\n')
      .filter(line => !line.trimStart().startsWith('//') && !line.trimStart().startsWith('*'))
    expect(codeLines.join('\n')).not.toContain('ElMessage.error')
  })
})

describe('ErrorLayerSample — Thresholds RESOURCE_ALREADY_EXISTS 恢复动作（D-08）', () => {
  const globalStubs = {
    'el-card': {
      template: '<div class="el-card"><slot name="header" /><slot /></div>'
    },
    'el-table': { template: '<div class="el-table"></div>' },
    'el-table-column': { template: '<div class="el-table-column"></div>' },
    // el-dialog 只在 modelValue 为真时渲染插槽 —— formRef 依赖它存在。
    'el-dialog': {
      template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
      props: ['modelValue', 'title', 'width']
    },
    // Thresholds.vue:176 是 formRef.value.validate(cb)；没有 validate 就会在进入
    // catch（被测分支）之前抛 TypeError。形状照抄 HealthMetrics.spec.ts:76 的 globalStubs。
    'el-form': {
      template: '<form class="el-form"><slot /></form>',
      methods: {
        validate: (cb: (valid: boolean) => void) => Promise.resolve(cb(true)),
        validateField: vi.fn(),
        clearValidate: vi.fn()
      }
    },
    'el-form-item': {
      template: '<div class="el-form-item"><slot /></div>',
      props: ['label', 'prop']
    },
    'el-select': {
      template: '<select class="el-select"><slot /></select>',
      props: ['modelValue', 'placeholder', 'disabled']
    },
    'el-option': {
      template: '<option class="el-option" :value="value">{{ label }}</option>',
      props: ['label', 'value']
    },
    'el-input-number': {
      template: '<input class="el-input-number" />',
      props: ['modelValue', 'precision', 'min']
    },
    'el-divider': { template: '<div class="el-divider"><slot /></div>' },
    'el-button': {
      template: '<button class="el-button"><slot /></button>',
      props: ['type', 'loading', 'disabled', 'size', 'link']
    },
    'el-icon': { template: '<span class="el-icon"><slot /></span>' }
  }

  function mountThresholds() {
    return mount(Thresholds, {
      global: {
        stubs: globalStubs,
        directives: { loading: () => {} }
      }
    })
  }

  // 单调递增、不随用例重置：闩锁窗口跨用例存活，回拨时间会让后一个用例被误判成重复。
  let clock = 1_700_000_000_000

  const EXISTING_THRESHOLD = {
    id: 3,
    userId: 1,
    metricKey: 'glucose',
    warningHigh: 6,
    criticalHigh: 7,
    warningLow: 3,
    criticalLow: 2,
    createdAt: '2026-01-01 00:00:00',
    updatedAt: '2026-01-01 00:00:00'
  }

  /** 后端重复阈值：信封 code=500（parseCodeToInt 回退值），errorCode 才是判定依据。 */
  function duplicateRejection(message: string) {
    return {
      response: {
        status: 200,
        data: { code: 500, message, errorCode: 'resource.exists' }
      }
    }
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // showError 的 2000ms VNode 去重闩锁是模块级的、跨用例存活。让 Date.now 单调前进
    // 5 秒/次，用例之间就不会互相把对方的 toast 闩掉（只在 setup.ts 的 happy-dom 下）。
    vi.spyOn(Date, 'now').mockImplementation(() => (clock += 5000))
    getThresholdsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { records: [EXISTING_THRESHOLD], total: 1, page: 1, size: 100 }
    })
  })

  afterEach(() => {
    // 全程：函数式旧 API ElMessage.error 必须零调用（收敛到对象式单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  it('6. 列表中存在该指标：一条错误 toast，VNode 内按钮文本为「打开编辑」', async () => {
    createThresholdMock.mockRejectedValueOnce(duplicateRejection('该指标已存在阈值，请直接编辑'))

    const wrapper = mountThresholds()
    await flushPromises()

    const vm = wrapper.vm as any
    // 先打开对话框：formRef 只在 dialog 插槽渲染后才存在，否则 handleSubmit 提前 return。
    vm.showAddDialog()
    await flushPromises()
    vm.form.metricKey = 'glucose'

    await vm.handleSubmit()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    const options = ElMessageMock.mock.calls[0][0] as { message: unknown }
    expect(typeof options.message).toBe('object')

    // D-08 端到端：标签由 ERROR_CODE_ACTIONS 自动补全，调用点没有传 label。
    const actionButton = findNodeByClass(options.message, 'error-toast__action')
    expect(actionButton).toBeDefined()
    expect(actionButton?.children).toBe('打开编辑')
    expect(actionButton?.props?.['aria-label']).toBe('打开编辑')

    wrapper.unmount()
  })

  it('7. 点击「打开编辑」：关闭对话框并进入该指标的编辑态', async () => {
    createThresholdMock.mockRejectedValueOnce(duplicateRejection('该指标已存在阈值，请直接编辑'))

    const wrapper = mountThresholds()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showAddDialog()
    await flushPromises()
    vm.form.metricKey = 'glucose'
    await vm.handleSubmit()
    await flushPromises()

    const options = ElMessageMock.mock.calls[0][0] as { message: VNodeLike }
    const actionButton = findNodeByClass(options.message, 'error-toast__action')
    const onClick = actionButton?.props?.onClick as (() => void) | undefined
    expect(typeof onClick).toBe('function')

    onClick?.()
    await flushPromises()

    expect(vm.editingId).toBe(3)
    expect(vm.dialogVisible).toBe(true)

    wrapper.unmount()
  })

  it('8. 列表里找不到该指标：退化为字符串 toast，不渲染死按钮（T-11-24）', async () => {
    getThresholdsMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { records: [], total: 0, page: 1, size: 100 }
    })
    // 与前两个用例文案不同，避开 showError 的 2000ms VNode 闩锁（模块级、跨用例存活）。
    createThresholdMock.mockRejectedValueOnce(duplicateRejection('阈值已存在'))

    const wrapper = mountThresholds()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.showAddDialog()
    await flushPromises()
    vm.form.metricKey = 'glucose'
    await vm.handleSubmit()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    const options = ElMessageMock.mock.calls[0][0] as { message: unknown }
    expect(typeof options.message).toBe('string')
    expect(options.message).toContain('阈值已存在')

    wrapper.unmount()
  })
})
