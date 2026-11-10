import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Mock } from 'vitest'

/**
 * Phase 14 / plan 14-10 的端到端锚点（`AIInput.vue` + `AiHistory.vue`）。
 *
 * 三条不变量：
 *
 * 1. **私有错误助手已退役。** `AIInput.handleApiError` 之所以存在，只因 Phase 11 之前的
 *    代码去读 `error.response.data.message` —— 而 `normalizeApiError` 返回的 `ApiError`
 *    根本没有 `response` 属性，那条 peek **恒为 undefined**，于是永远走兜底文案。
 *    429 的特例同样是冗余的（后端本就下发中文）。这里用源码断言把退役钉成永久事实。
 * 2. **后端文案优先。** 给 mock 一个与字典不同的后端消息，断言它出现在 toast 里 ——
 *    对旧的 `error.response` 实现这条必然失败，是回归护栏。
 * 3. **B1 不得回归（本条最要紧）。** 这两个文件都不渲染 `ErrorState`；
 *    `AiParseServiceImpl:749`（详情）与 `:783`（删除）抛 `RESOURCE_NOT_FOUND`，
 *    `:776` 抛 `AUTH_FORBIDDEN`。若 `showError` 少传 `presentStates: true`，
 *    「查看/删除一条已被别人删掉的记录」会变成**零提示**的静默 —— 下面两条守卫
 *    正是为这件事写的。
 */

vi.mock('@/api/ai-parse', () => ({
  aiParseApi: {
    getHistory: vi.fn(),
    getHistoryDetail: vi.fn(),
    deleteHistory: vi.fn(),
    getRemainingCount: vi.fn()
  }
}))

// AIInput 直接用 request，不走 api 层。
vi.mock('@/utils/request', () => ({
  request: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  },
  resetAuthLatch: vi.fn()
}))

vi.mock('@/composables/useGsap', () => ({
  gsap: {
    timeline: () => {
      const timeline: Record<string, unknown> = {}
      timeline.fromTo = () => timeline
      return timeline
    }
  },
  staggerReveal: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

vi.mock('vue-router', async importOriginal => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: vi.fn(() => ({ query: {}, params: {}, path: '/data-input' })),
    useRouter: vi.fn(() => ({ push: vi.fn(), replace: vi.fn() }))
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

import AIInput from '@/views/data-input/components/AIInput.vue'
import AiHistory from '@/views/data-input/components/AiHistory.vue'
import { aiParseApi } from '@/api/ai-parse'
import { request } from '@/utils/request'
import { ElMessage } from 'element-plus'

const getHistoryMock = aiParseApi.getHistory as unknown as Mock
const getHistoryDetailMock = aiParseApi.getHistoryDetail as unknown as Mock
const deleteHistoryMock = aiParseApi.deleteHistory as unknown as Mock
const requestGetMock = request.get as unknown as Mock
const requestPostMock = request.post as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/** showError 的 VNode 闩锁是模块级、跨用例存活的。让 Date.now 单调前进 60s。 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

/** 被退役的 429 静态文案。它绝不能出现在任何 toast 里。 */
const RETIRED_429_COPY = '请求过于频繁，请稍后再试'

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

const aiInputStubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-input': { template: '<textarea class="el-input" />', props: ['modelValue', 'type', 'rows', 'placeholder', 'disabled', 'maxlength', 'showWordLimit'] },
  'el-button': { template: '<button class="el-button" type="button"><slot /></button>', props: ['type', 'loading', 'disabled'] },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-table': { template: '<div class="el-table"><slot /></div>', props: ['data', 'stripe', 'size'] },
  'el-table-column': { template: '<div class="el-table-column" />', props: ['prop', 'label', 'width'] },
  'el-checkbox': { template: '<input type="checkbox" class="el-checkbox" />', props: ['modelValue'] },
  'el-progress': { template: '<div class="el-progress" />', props: ['percentage', 'strokeWidth', 'color'] },
  'el-alert': { template: '<div class="el-alert"><slot name="title" /><slot /></div>', props: ['type', 'closable', 'title'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  MagicStick: { template: '<span class="icon-magic" />' }
}

const aiHistoryStubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-table': { template: '<div class="el-table"><slot /></div>', props: ['data', 'stripe', 'emptyText'] },
  'el-table-column': { template: '<div class="el-table-column" />', props: ['prop', 'label', 'width', 'minWidth', 'fixed'] },
  'el-tooltip': { template: '<span class="el-tooltip"><slot /></span>', props: ['content', 'placement', 'showAfter'] },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-button': { template: '<button class="el-button" type="button"><slot /></button>', props: ['type', 'size', 'text'] },
  'el-popconfirm': { template: '<span class="el-popconfirm"><slot /><slot name="reference" /></span>', props: ['title', 'confirmButtonText', 'cancelButtonText'] },
  'el-pagination': { template: '<div class="el-pagination" />', props: ['currentPage', 'pageSize', 'total', 'layout', 'small'] },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose']
  },
  'el-text': { template: '<span class="el-text"><slot /></span>' },
  'el-alert': { template: '<div class="el-alert"><slot /></div>', props: ['title', 'type', 'closable', 'showIcon'] },
  'el-empty': { template: '<div class="el-empty" />', props: ['description'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  ChatDotRound: { template: '<span class="icon-chat" />' },
  Loading: { template: '<span class="icon-loading" />' }
}

type AIInputVm = {
  inputText: string
  remainingCount: number | null
  parseResult: unknown
  handleParse: () => Promise<void>
  handleConfirm: () => Promise<void>
}

type AiHistoryVm = {
  detailVisible: boolean
  detailData: unknown
  historyRecords: unknown[]
  viewDetail: (record: { id: number }) => Promise<void>
  handleDelete: (id: number) => Promise<void>
}

function mountAIInput() {
  return mount(AIInput, {
    global: { stubs: aiInputStubs, directives: { loading: () => {} } }
  })
}

function mountAiHistory() {
  return mount(AiHistory, {
    global: { stubs: aiHistoryStubs, directives: { loading: () => {} } }
  })
}

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

describe('data-input/AIInput.vue — 私有助手退役后两条路径都只有一条提示（ERR-07）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    requestGetMock.mockResolvedValue(ok(10))
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('解析被 429 拒绝：恰好一条提示，且文案不是被退役的「请求过于频繁」静态句', async () => {
    // 后端这次没有下发消息（只有信封 code），所以文案只能来自字典。
    // 旧的助手在这里会命中 `status === 429` 分支并弹静态句 —— 那条分支已删除。
    requestPostMock.mockRejectedValueOnce({ response: { status: 429, data: { code: 429 } } })

    const wrapper = mountAIInput()
    await flushPromises()

    const vm = wrapper.vm as unknown as AIInputVm
    vm.inputText = '今天血糖5.6'
    await vm.handleParse()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).not.toBe(RETIRED_429_COPY)
    // 字典里可执行的下一句（ai.parseFailed），而不是只陈述失败的兜底。
    expect(lastErrorToastText()).toBe('AI 解析未完成，请调整描述后重试')

    wrapper.unmount()
  })

  it('解析失败且后端下发中文：toast 里是后端消息，不是字典兜底（回归护栏）', async () => {
    // 旧实现读 error.response.data.message，而 ApiError 没有 response 属性，
    // 因此后端文案永远拿不到、永远走兜底 —— 对旧代码这条断言必然失败。
    requestPostMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, message: '描述的文本过长，请精简后重试' } }
    })

    const wrapper = mountAIInput()
    await flushPromises()

    const vm = wrapper.vm as unknown as AIInputVm
    vm.inputText = '一段很长的描述'
    await vm.handleParse()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('描述的文本过长，请精简后重试')

    wrapper.unmount()
  })

  it('确认录入失败：恰好一条提示，成功提示只在成功路径出现', async () => {
    requestPostMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, message: '指标类型与数值不匹配' } }
    })

    const wrapper = mountAIInput()
    await flushPromises()

    const vm = wrapper.vm as unknown as AIInputVm
    vm.parseResult = {
      metrics: [
        {
          metricKey: 'blood_glucose',
          metricName: '血糖',
          value: 5.6,
          unit: 'mmol/L',
          confidence: 0.95,
          category: 'HEALTH',
          recordDate: '2026-11-10',
          selected: true
        }
      ],
      summary: '',
      warnings: [],
      parseHistoryId: 1,
      remainingCount: 9
    }

    await vm.handleConfirm()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('指标类型与数值不匹配')
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)

    wrapper.unmount()
  })
})

describe('data-input/AIInput.vue — 源码级退役契约', () => {
  function readSource(): string {
    return readFileSync(
      resolve(process.cwd(), 'src/views/data-input/components/AIInput.vue'),
      'utf8'
    )
  }

  it('私有 handleApiError 已不存在', () => {
    expect(readSource()).not.toContain('handleApiError')
  })

  it('不再读 error.response（ApiError 没有该属性，这条 peek 恒为 undefined）', () => {
    expect(readSource()).not.toContain('error.response')
  })

  it('不再有 429 特例', () => {
    expect(readSource()).not.toContain('429')
  })

  it('不再有裸的错误 toast，也不再有没有非 200 兜底的分支', () => {
    const source = readSource()
    expect(source).not.toContain('ElMessage.error')
    expect(source).not.toContain('res.code !== 200')
  })
})

describe('data-input/AiHistory.vue — B1 不得回归：状态类失败仍恰好一条提示（ERR-07）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getHistoryMock.mockResolvedValue(
      ok({
        total: 1,
        page: 1,
        size: 10,
        records: [
          {
            id: 7,
            inputText: '今天血糖5.6',
            inputType: 'text',
            inputTypeDisplay: '文本',
            metricsCount: 1,
            confirmed: false,
            createTime: '2026-11-10 08:00:00'
          }
        ]
      })
    )
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('查看一条已被删除的记录（RESOURCE_NOT_FOUND 信封）：恰好一条提示，弹窗保持打开', async () => {
    // presentStates 是这条用例的整个存在理由：缺了它 showError 在 resolveMessage
    // 之前就 return，条数会是 0 —— 用户点「查看」后什么也看不到。
    getHistoryDetailMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'resource.not_found', message: '记录不存在' }
      }
    })

    const wrapper = mountAiHistory()
    await flushPromises()

    const vm = wrapper.vm as unknown as AiHistoryVm
    await vm.viewDetail({ id: 7 })
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('记录不存在')
    // 弹窗不因一次详情失败被关掉，列表也与失败前一致（T-14-31）。
    expect(vm.detailVisible).toBe(true)
    expect(vm.historyRecords).toHaveLength(1)

    wrapper.unmount()
  })

  it('删除一条已被别人删掉的记录（AUTH_FORBIDDEN 信封）：恰好一条提示，不是静默', async () => {
    // 动作型 403：本文件无状态块可落，没有 presentStates 就等于一声不响。
    deleteHistoryMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'auth.forbidden', message: '无权删除他人的解析记录' }
      }
    })

    const wrapper = mountAiHistory()
    await flushPromises()

    const vm = wrapper.vm as unknown as AiHistoryVm
    await vm.handleDelete(7)
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('无权删除他人的解析记录')
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)

    wrapper.unmount()
  })

  it('列表加载失败：一条提示，且表格保留原有行（不清空）', async () => {
    getHistoryMock.mockResolvedValueOnce(
      ok({
        total: 1,
        page: 1,
        size: 10,
        records: [
          {
            id: 9,
            inputText: '心率72',
            inputType: 'text',
            inputTypeDisplay: '文本',
            metricsCount: 1,
            confirmed: true,
            createTime: '2026-11-09 08:00:00'
          }
        ]
      })
    )

    const wrapper = mountAiHistory()
    await flushPromises()
    const vm = wrapper.vm as unknown as AiHistoryVm
    const before = vm.historyRecords
    expect(before).toHaveLength(1)

    // 第二次加载（刷新按钮）失败 —— 已加载的行必须留着。
    getHistoryMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    await (wrapper.vm as unknown as { refresh: () => Promise<void> }).refresh()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(vm.historyRecords).toBe(before)
    expect(vm.historyRecords).toHaveLength(1)

    wrapper.unmount()
  })
})

describe('data-input/AiHistory.vue — 源码级删除契约', () => {
  function readSource(): string {
    return readFileSync(
      resolve(process.cwd(), 'src/views/data-input/components/AiHistory.vue'),
      'utf8'
    )
  }

  it('不再有裸的错误 toast', () => {
    expect(readSource()).not.toContain('ElMessage.error')
  })

  it('不再有「非 200」兜底呈现（拦截器 reject 之后不可达）', () => {
    expect(readSource()).not.toContain('res.code !== 200')
  })

  it('成功提示与详情键复用都还在', () => {
    const source = readSource()
    expect(source).toContain("ElMessage.success('删除成功')")
    expect(source).toContain("showError(error, 'ai.loadFailed'")
    expect(source).toContain("showError(error, 'ai.deleteFailed'")
  })
})
