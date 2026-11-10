import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import type { Mock } from 'vitest'

/**
 * Phase 14 / ERR-07 / D-03 的 S2 端到端锚点：
 * 体检报告详情页的首个主查询返回 404 时，**只**替换资源区为内容区解释块，
 * 页头与导航保持可用，零 toast，且动作对固定为「返回列表(primary) + 重新加载该记录(default)」。
 *
 * 为什么必须在页面级测：404 的三个性质 ——「内容区而非整页」「两个有序动作」「零 toast」——
 * 只有把分类层（normalizeApiError）、呈现层（showError / ErrorState）与页面状态串起来才成立。
 * 单独测 normalizeApiError 或单独测 ErrorState 都无法证明它们被接在一起。
 *
 * element-plus 用 importOriginal 部分 mock（同 ErrorLayerSample.spec.ts:53-68）：
 * 保住组件导出，只替换 ElMessage，这样才能既挂载页面、又对每次 toast（含对象式）计数。
 */

vi.mock('@/api/screening', () => ({
  screeningApi: {
    getReportDetail: vi.fn(),
    getLabResults: vi.fn(),
    uploadReport: vi.fn(),
    listReports: vi.fn(),
    getReport: vi.fn(),
    deleteReport: vi.fn()
  }
}))

vi.mock('@/api/screening-compare', () => ({
  screeningCompareApi: {
    compare: vi.fn()
  }
}))

// Compare.vue 的 onMounted 消费 staggerReveal / countUp；Detail.vue 消费 gsap 时间线。
vi.mock('@/composables/useGsap', () => ({
  gsap: {
    timeline: () => ({
      fromTo: vi.fn().mockReturnThis()
    })
  },
  staggerReveal: vi.fn(),
  countUp: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

// 两个页面都在 setup 期读 route.params，因此 mock 必须可逐用例改写。
const mockRoute: { params: Record<string, string>; query: Record<string, string>; path: string } = {
  params: { id: '42' },
  query: {},
  path: '/screening/detail/42'
}

vi.mock('vue-router', async importOriginal => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: vi.fn(() => mockRoute),
    useRouter: vi.fn(() => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn() }))
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

import Detail from '@/views/screening/Detail.vue'
import Compare from '@/views/screening/Compare.vue'
import { screeningApi } from '@/api/screening'
import { screeningCompareApi } from '@/api/screening-compare'
import { ElMessage } from 'element-plus'

const getReportDetailMock = screeningApi.getReportDetail as unknown as Mock
const getLabResultsMock = screeningApi.getLabResults as unknown as Mock
const compareMock = screeningCompareApi.compare as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/**
 * showError 有一个模块级 2000ms 去重闩锁（VNode toast 不能用 EP 的 grouping）。
 * 同一文件里的多个用例会发同一条文案，闩锁会把后一个用例的 toast 吞掉，
 * 让断言读成 0 —— 那是闩锁生效，不是呈现缺失。让每次 Date.now() 前进 60s，
 * 使每个用例落在闩锁窗口之外；用例内部的重复呈现仍会被正常去重。
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

const globalStubs = {
  'el-page-header': {
    template: '<div class="el-page-header"><slot name="content" /></div>',
    props: ['title']
  },
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-descriptions': {
    template: '<div class="el-descriptions"><slot /></div>',
    props: ['column', 'border']
  },
  'el-descriptions-item': {
    template: '<div class="el-descriptions-item"><slot /></div>',
    props: ['label', 'span']
  },
  'el-table': {
    template: '<div class="el-table"><slot /></div>',
    props: ['data', 'stripe']
  },
  'el-table-column': {
    template: '<div class="el-table-column" />',
    props: ['prop', 'label', 'width', 'minWidth']
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>',
    props: ['type', 'size']
  },
  'el-image': {
    template: '<img class="el-image" />',
    props: ['src', 'fit', 'previewSrcList']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>',
    props: ['size', 'color']
  },
  'el-empty': {
    template: '<div class="el-empty">{{ description }}</div>',
    props: ['description']
  },
  'el-row': {
    template: '<div class="el-row"><slot /></div>',
    props: ['gutter']
  },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span']
  },
  'el-button': {
    template:
      '<button class="el-button" :type="type" :aria-label="ariaLabel"><slot /></button>',
    props: ['type', 'size', 'loading', 'disabled', 'ariaLabel']
  },
  Top: { template: '<span class="icon-top" />' },
  Bottom: { template: '<span class="icon-bottom" />' },
  Minus: { template: '<span class="icon-minus" />' },
  Document: { template: '<span class="icon-document" />' },
  // ErrorState 不 stub —— 本用例要断言它真的渲染出内容区块。
  DocumentDelete: { template: '<span class="icon-document-delete">D</span>' },
  WarningFilled: { template: '<span class="icon-warning-filled">!</span>' },
  Lock: { template: '<span class="icon-lock">L</span>' }
}

function mountPage() {
  return mount(Detail, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

/** 真实形态：BusinessException(*_NOT_FOUND) → HTTP 200 + 信封 code 500 + errorCode 字符串。 */
function notFoundRejection() {
  return {
    response: {
      status: 200,
      data: { code: 500, errorCode: 'resource.not_found', message: '报告不存在' }
    }
  }
}

describe('screening/Detail.vue — 404 内容区解释块（S2 端到端）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getReportDetailMock.mockReset()
    getLabResultsMock.mockReset()
    getLabResultsMock.mockResolvedValue({ code: 200, message: 'success', data: [] })
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
  })

  it('404：渲染内容区块、两个有序动作、零 toast，且页头仍在', async () => {
    getReportDetailMock.mockRejectedValueOnce(notFoundRejection())

    const wrapper = mountPage()
    await flushPromises()

    // 1. 内容区作用域：块存在，且不带 page 修饰类（不是整页降级）。
    const block = wrapper.find('.error-state')
    expect(block.exists()).toBe(true)
    expect(wrapper.find('.error-state--page').exists()).toBe(false)
    expect(wrapper.find('.error-state__title').text()).toBe('资源不存在')

    // 2. 页面壳保留：页头仍然渲染，导航未被替换。
    expect(wrapper.find('.el-page-header').exists()).toBe(true)

    // 3. 动作对与顺序（UI-SPEC Contract 3 §3.1）。
    const buttons = block.findAll('.el-button')
    expect(buttons).toHaveLength(2)
    expect(buttons[0].text()).toBe('返回列表')
    expect(buttons[1].text()).toBe('重新加载该记录')
    // 标签同时是可见文本与可访问名。
    expect(buttons[0].attributes('aria-label')).toBe('返回列表')
    expect(buttons[1].attributes('aria-label')).toBe('重新加载该记录')

    // 4. 没有任何裸「重试」按钮（Contract 3 §3.2 的局部覆盖）。
    expect(block.findAll('[aria-label="重试"]')).toHaveLength(0)
    expect(buttons.some(button => button.text() === '重试')).toBe(false)

    // 5. 零 toast —— 状态块独占呈现，不得同时弹提示。
    expect(errorToastCount()).toBe(0)
    expect(toastCount()).toBe(0)

    wrapper.unmount()
  })

  it('非 404（传输层 500）：不渲染状态块，且恰好一条 toast', async () => {
    getReportDetailMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.error-state').exists()).toBe(false)
    expect(errorToastCount()).toBe(1)

    wrapper.unmount()
  })

  it('forbidden：不渲染状态块，且恰好一条 toast（证明 fallback 带了 presentStates）', async () => {
    // 本页没有 403 的页级处理；若 fallback 缺 presentStates，这里会得到 0 条（静默失败）。
    getReportDetailMock.mockRejectedValueOnce({
      response: {
        status: 403,
        data: { code: 403, errorCode: 'auth.forbidden', message: '没有访问权限' }
      }
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.error-state').exists()).toBe(false)
    expect(errorToastCount()).toBe(1)

    wrapper.unmount()
  })
})

function mountCompare() {
  return mount(Compare, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

describe('screening/Compare.vue — 非法路由参数渲染页级块而非 toast', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    compareMock.mockReset()
    mockRoute.params = { idA: '1', idB: '2' }
    mockRoute.path = '/screening/compare/1/2'
  })

  afterEach(() => {
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
  })

  it('非数字 id：页级 notFound 块、单个 返回列表、零 toast，且绝不发请求', async () => {
    // 路由状态在请求之前就可判定 —— 断言 compare 一次都没被调用。
    mockRoute.params = { idA: 'abc', idB: '2' }
    mockRoute.path = '/screening/compare/abc/2'

    const wrapper = mountCompare()
    await flushPromises()

    const block = wrapper.find('.error-state')
    expect(block.exists()).toBe(true)
    // 页级作用域：带 page 修饰类，与详情页的内容区块分属两档几何。
    expect(wrapper.find('.error-state--page').exists()).toBe(true)
    expect(wrapper.find('.error-state__title').text()).toBe('页面不存在')

    const buttons = block.findAll('.el-button')
    expect(buttons).toHaveLength(1)
    expect(buttons[0].text()).toBe('返回列表')

    expect(compareMock).not.toHaveBeenCalled()
    expect(errorToastCount()).toBe(0)
    expect(toastCount()).toBe(0)

    wrapper.unmount()
  })

  it('缺失 id 参数：同样渲染页级块，且不发请求', async () => {
    mockRoute.params = { idA: '1' }
    mockRoute.path = '/screening/compare/1'

    const wrapper = mountCompare()
    await flushPromises()

    expect(wrapper.find('.error-state--page').exists()).toBe(true)
    expect(compareMock).not.toHaveBeenCalled()
    expect(errorToastCount()).toBe(0)

    wrapper.unmount()
  })

  it('参数合法但查询 500：不渲染状态块，且恰好一条 toast', async () => {
    compareMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountCompare()
    await flushPromises()

    expect(wrapper.find('.error-state').exists()).toBe(false)
    expect(compareMock).toHaveBeenCalledTimes(1)
    expect(errorToastCount()).toBe(1)

    wrapper.unmount()
  })

  it('参数合法但查询 403：不渲染状态块，且恰好一条 toast（证明 presentStates）', async () => {
    compareMock.mockRejectedValueOnce({
      response: {
        status: 403,
        data: { code: 403, errorCode: 'auth.forbidden', message: '没有访问权限' }
      }
    })

    const wrapper = mountCompare()
    await flushPromises()

    expect(wrapper.find('.error-state').exists()).toBe(false)
    expect(errorToastCount()).toBe(1)

    wrapper.unmount()
  })
})
