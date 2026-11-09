import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'
import type { HealthScoreVO, ProfileCompletenessVO, ProfileFieldStatusVO } from '@/types/api'
import { PROFILE_FIELDS } from '@/utils/profileFields'

/**
 * 健康评分页估算徽标的行为证明（PRF-06、UI-SPEC Contract 4 / Contract 7）。
 *
 * 三条不变量由断言而非注释保证：
 * 1. 只有 `profileCompleteness != null && < 100` 才画徽标 —— `0` 是合法值，`null`
 *    是 NO_DATA 路径的后端契约（13-03），两者绝不可混为一谈；
 * 2. 落点固定在 `.score-info` 内、`.score-tips` 之后、`.score-meta` 之前；
 * 3. 点击把它变成一次带 `?focus=` 的跳转，focus 来自 store 的 `firstMissingKey()`
 *    而不是评分载荷（载荷可能根本没有字段清单）。
 *
 * EstimateBadge 用真实组件：若用 stub，`null 不渲染` 这条契约就成了对 stub 的断言。
 * Score.vue 在模块作用域调用 `echarts.use(...)`，因此 echarts 必须显式 mock。
 */

const routerPush = vi.fn()

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
// createRouter / createWebHistory：`@/utils/request` → `@/stores/auth` 会 import `@/router`，
// 整体替换会让 router/index.ts 在模块求值时崩掉。
vi.mock('vue-router', async importOriginal => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => ({ push: routerPush, replace: vi.fn() })
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
    })
  }
})

// Score.vue 的 onMounted 用到这一整组导出；形状照抄 DashboardProfileBanner.spec.ts。
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

// 模块作用域 `echarts.use([...])` 会在 import 时执行，且 buildChartOption 可能取 graphic。
vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: vi.fn(),
  graphic: {
    LinearGradient: class LinearGradient {}
  }
}))
vi.mock('echarts/charts', () => ({
  LineChart: {},
  RadarChart: {},
  GaugeChart: {}
}))
vi.mock('echarts/components', () => ({
  TooltipComponent: {},
  GridComponent: {},
  RadarComponent: {}
}))
vi.mock('echarts/renderers', () => ({
  CanvasRenderer: {}
}))

// 图表生命周期不是本 spec 的主题；返回形状必须与真实 composable 的消费点一致。
vi.mock('@/composables/useECharts', () => ({
  useECharts: () => ({ init: vi.fn(), updateOption: vi.fn() })
}))

import Score from '@/views/health/Score.vue'
import { scoreApi } from '@/api/score'
import { preventionApi } from '@/api/prevention'

const getScore = scoreApi.getScore as unknown as Mock
const getScoreHistory = scoreApi.getScoreHistory as unknown as Mock
const getReport = scoreApi.getReport as unknown as Mock
const getCompleteness = preventionApi.getCompleteness as unknown as Mock

/** el-button 不 emit click：父级 @click 作为原生监听器落到根 button 上（否则会跑两次）。 */
const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-skeleton': {
    template: '<div class="el-skeleton"><slot /></div>',
    props: ['loading', 'animated']
  },
  'el-skeleton-item': {
    template: '<div class="el-skeleton-item" />',
    props: ['variant']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  'el-empty': {
    template: '<div class="el-empty"><slot name="image" /><slot /></div>',
    props: ['description', 'image', 'imageSize']
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>',
    props: ['type', 'size', 'effect']
  },
  'el-button': {
    template: '<button class="el-button" type="button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'plain', 'link', 'text']
  },
  'el-row': {
    template: '<div class="el-row"><slot /></div>',
    props: ['gutter']
  },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-progress': {
    template: '<div class="el-progress" />',
    props: ['percentage', 'color', 'strokeWidth']
  },
  'el-radio-group': {
    template: '<div class="el-radio-group"><slot /></div>',
    props: ['modelValue']
  },
  'el-radio-button': {
    template: '<label class="el-radio-button"><slot /></label>',
    props: ['value', 'label']
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'top']
  },
  'el-descriptions': {
    template: '<div class="el-descriptions"><slot /></div>',
    props: ['title', 'column', 'border']
  },
  'el-descriptions-item': {
    template: '<div class="el-descriptions-item"><slot /></div>',
    props: ['label']
  },
  'el-alert': {
    template: '<div class="el-alert"><slot /></div>',
    props: ['title', 'description', 'type', 'showIcon']
  },
  'el-timeline': {
    template: '<div class="el-timeline"><slot /></div>'
  },
  'el-timeline-item': {
    template: '<div class="el-timeline-item"><slot /></div>',
    props: ['type']
  },
  HealthScoreCircle: {
    name: 'HealthScoreCircle',
    props: ['score', 'level', 'size', 'strokeWidth'],
    template: '<div class="stub-health-score"><span class="score-number">{{ score }}</span></div>'
  }
}

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

function makeCompleteness(options: {
  score: number
  filledKeys?: string[]
}): ProfileCompletenessVO {
  const filledKeys = options.filledKeys ?? []
  const fields: ProfileFieldStatusVO[] = PROFILE_FIELDS.map(field => ({
    ...field,
    filled: filledKeys.includes(field.key)
  }))
  return {
    score: options.score,
    level: options.score >= 85 ? '完整档案' : options.score >= 60 ? '标准档案' : '基础档案',
    filledCount: fields.filter(field => field.filled).length,
    totalCount: fields.length,
    hasProfile: true,
    assumptions: [],
    fields
  }
}

const ALL_FILLED = PROFILE_FIELDS.map(field => field.key)

function makeScore(overrides: Partial<HealthScoreVO> = {}): HealthScoreVO {
  return {
    score: 72,
    level: 'GOOD',
    factors: {},
    calculationMethod: 'RULE',
    calculatedAt: '2026-11-09 10:00:00',
    expiresAt: '2026-11-09 12:00:00',
    isCached: false,
    ...overrides
  }
}

const NO_DATA_SCORE: HealthScoreVO = makeScore({
  score: 0,
  level: 'NO_DATA',
  message: '暂无健康数据',
  factors: { requiredMetrics: ['身高', '体重'] }
})

function mountScore() {
  return mount(Score, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

function badge(wrapper: ReturnType<typeof mountScore>) {
  return wrapper.find('.hhs-estimate-badge')
}

describe('ScoreEstimateBadge.spec.ts — 评分页估算徽标（PRF-06）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    routerPush.mockReset()
    getScore.mockResolvedValue(ok(makeScore({ profileCompleteness: 40, assumptions: ['缺少 BMI，按 22.0 估算'] })))
    getScoreHistory.mockResolvedValue(ok([]))
    // 无未填字段 → firstMissingKey() 为 null → 查询串为空对象。
    getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 100, filledKeys: ALL_FILLED })))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('mounts the fetch so the badge destination key is ready without a click', async () => {
    const wrapper = mountScore()
    await flushPromises()

    expect(getCompleteness).toHaveBeenCalledTimes(1)
    expect(getCompleteness).toHaveBeenCalledWith({ silent: true })

    wrapper.unmount()
  })

  it('renders the badge inside .score-info, after .score-tips and before .score-meta', async () => {
    const wrapper = mountScore()
    await flushPromises()

    expect(badge(wrapper).exists()).toBe(true)
    expect(wrapper.find('.score-info .hhs-estimate-badge').exists()).toBe(true)
    // 徽标不是表格里的行标签（Contract 4 隔离规则 1）
    expect(wrapper.find('.el-table .hhs-estimate-badge').exists()).toBe(false)

    const info = wrapper.find('.score-info').element
    const children = Array.from(info.children)
    const tipsIndex = children.indexOf(info.querySelector('.score-tips') as Element)
    const badgeIndex = children.indexOf(info.querySelector('.hhs-estimate-badge') as Element)
    const metaIndex = children.indexOf(info.querySelector('.score-meta') as Element)

    expect(tipsIndex).toBeGreaterThanOrEqual(0)
    expect(badgeIndex).toBe(tipsIndex + 1)
    expect(metaIndex).toBeGreaterThan(badgeIndex)

    wrapper.unmount()
  })

  it('renders the badge for a genuine 0% profile — a real value is not a falsy miss', async () => {
    getScore.mockResolvedValue(ok(makeScore({ profileCompleteness: 0 })))
    const wrapper = mountScore()
    await flushPromises()

    expect(badge(wrapper).exists()).toBe(true)

    wrapper.unmount()
  })

  it('renders no badge when profileCompleteness is null — the NO_DATA contract says unknown', async () => {
    getScore.mockResolvedValue(ok(makeScore({ profileCompleteness: null })))
    const wrapper = mountScore()
    await flushPromises()

    // 分数区块仍然渲染：这证明缺的是徽标，不是整块内容。
    expect(wrapper.find('.score-content').exists()).toBe(true)
    expect(badge(wrapper).exists()).toBe(false)
    expect(wrapper.text()).not.toContain('基于不完整档案估算')

    wrapper.unmount()
  })

  it('renders no badge when the field is absent entirely (older backend payload)', async () => {
    getScore.mockResolvedValue(ok(makeScore()))
    const wrapper = mountScore()
    await flushPromises()

    expect(badge(wrapper).exists()).toBe(false)

    wrapper.unmount()
  })

  it('renders no badge at exactly 100', async () => {
    getScore.mockResolvedValue(ok(makeScore({ profileCompleteness: 100 })))
    const wrapper = mountScore()
    await flushPromises()

    expect(badge(wrapper).exists()).toBe(false)

    wrapper.unmount()
  })

  it('pushes to the profile page with no query when nothing is missing', async () => {
    getScore.mockResolvedValue(ok(makeScore({ profileCompleteness: 40 })))
    const wrapper = mountScore()
    await flushPromises()

    await badge(wrapper).trigger('click')

    expect(routerPush).toHaveBeenCalledTimes(1)
    expect(routerPush).toHaveBeenCalledWith({ path: '/prevention/profile', query: {} })

    wrapper.unmount()
  })

  it('pushes with query.focus set to the store first missing key', async () => {
    getScore.mockResolvedValue(ok(makeScore({ profileCompleteness: 40 })))
    // 只填了性别 → 最高优先级的未填项是 birthDate（required 组内目录顺序，13-05 唯一实现）。
    getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 40, filledKeys: ['gender'] })))
    const wrapper = mountScore()
    await flushPromises()

    await badge(wrapper).trigger('click')

    expect(routerPush).toHaveBeenCalledTimes(1)
    expect(routerPush).toHaveBeenCalledWith({
      path: '/prevention/profile',
      query: { focus: 'birthDate' }
    })

    wrapper.unmount()
  })

  it('renders the empty state and no badge on the NO_DATA branch', async () => {
    // 即便载荷带了 profileCompleteness，NO_DATA 分支渲染的是 .no-data-content，
    // 徽标所在的 .score-info 根本不存在 —— 这就是「不伪造估算声明」的第二道保险。
    getScore.mockResolvedValue(ok({ ...NO_DATA_SCORE, profileCompleteness: 40 }))
    const wrapper = mountScore()
    await flushPromises()

    expect(wrapper.find('.no-data-content').exists()).toBe(true)
    expect(wrapper.find('.score-content').exists()).toBe(false)
    expect(badge(wrapper).exists()).toBe(false)
    expect(wrapper.text()).not.toContain('基于不完整档案估算')

    wrapper.unmount()
  })

  it('labels the report dialog secondary action 放弃生成, never the bare 取消', async () => {
    // 挂住 getReport：reportData 保持 null 且 reportLoading 为 true，渲染 .report-loading。
    getReport.mockReturnValue(new Promise(() => {}))
    const wrapper = mountScore()
    await flushPromises()

    const reportButton = wrapper
      .findAll('.score-info .el-button')
      .find(node => node.text().includes('生成健康报告'))
    expect(reportButton).toBeDefined()
    if (!reportButton) throw new Error('生成健康报告 按钮未渲染')

    await reportButton.trigger('click')
    await flushPromises()

    const loading = wrapper.find('.report-loading')
    expect(loading.exists()).toBe(true)
    expect(loading.find('button').text()).toBe('放弃生成')
    expect(loading.text()).not.toContain('取消')

    wrapper.unmount()
  })
})