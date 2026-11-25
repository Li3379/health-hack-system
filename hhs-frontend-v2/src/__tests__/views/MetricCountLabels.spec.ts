import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * 口径标签的表面证明：两处「健康指标」数字取自不同分母 ——
 * 仪表盘是 `/api/metrics` 的全时段 HEALTH 合计，智能录入页是 `/api/stats/today`
 * 的当日 recordDate 计数。两者都合法，同名会让用户以为其中一个错了，所以标签必须
 * 自己说明口径：仪表盘「健康指标（累计）」，录入页「今日健康指标」。
 */

vi.mock('@/api/score', () => ({
  scoreApi: { getScore: vi.fn(), recalculateScore: vi.fn(), getScoreBreakdown: vi.fn(), getScoreHistory: vi.fn() }
}))

vi.mock('@/api/health', () => ({
  healthApi: { getMetrics: vi.fn() }
}))

vi.mock('@/api/stats', () => ({
  statsApi: { getTrends: vi.fn() }
}))

vi.mock('@/api/prevention', () => ({
  preventionApi: {
    getProfile: vi.fn(),
    getCompleteness: vi.fn(),
    createProfile: vi.fn(),
    updateProfile: vi.fn()
  }
}))

vi.mock('@/api/screening', () => ({
  screeningApi: {
    listReports: vi.fn()
  }
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { nickname: '测试用户', username: 'tester' }, token: 'test-token' })
}))

vi.mock('@/stores/alert', () => ({
  useAlertStore: () => ({
    unreadCount: 0,
    recentAlerts: [],
    fetchRecentAlerts: vi.fn().mockResolvedValue(undefined),
    fetchUnreadCount: vi.fn().mockResolvedValue(undefined)
  })
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() })
}))

vi.mock('@/composables/useGsap', () => {
  const timeline: Record<string, unknown> = {}
  timeline.fromTo = () => timeline
  return {
    gsap: { timeline: () => timeline, to: vi.fn() },
    staggerReveal: vi.fn(),
    countUp: vi.fn(),
    cleanupScrollTriggers: vi.fn(),
    tilt3D: vi.fn(),
    magneticHover: vi.fn(),
    parallax: vi.fn(),
    glowPulse: vi.fn(),
    buttonPress: vi.fn(),
    hoverLift: vi.fn(),
    hoverShine: vi.fn(),
    rippleClick: vi.fn(),
    DUR: { fast: 0.2, mid: 0.5, slow: 0.8, dramatic: 1.2 },
    EASE: { out: 'power2.out', expo: 'expo.out', back: 'back.out' }
  }
})

const requestGet = vi.fn()

vi.mock('@/utils/request', () => ({
  request: { get: (...args: unknown[]) => requestGet(...args) }
}))

import Dashboard from '@/views/dashboard/Index.vue'
import DataInput from '@/views/data-input/Index.vue'
import { scoreApi } from '@/api/score'
import { healthApi } from '@/api/health'
import { statsApi } from '@/api/stats'
import { preventionApi } from '@/api/prevention'
import { screeningApi } from '@/api/screening'

const getScore = scoreApi.getScore as unknown as Mock
const getMetrics = healthApi.getMetrics as unknown as Mock
const getTrends = statsApi.getTrends as unknown as Mock
const getCompleteness = preventionApi.getCompleteness as unknown as Mock
const listReports = screeningApi.listReports as unknown as Mock

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

const elementStubs = {
  'el-skeleton': { template: '<div class="el-skeleton"><slot /></div>', props: ['loading', 'animated'] },
  'el-skeleton-item': { template: '<div class="el-skeleton-item" />', props: ['variant', 'style'] },
  'el-button': { template: '<button class="el-button"><slot /></button>', props: ['type', 'text', 'size', 'plain', 'icon'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-empty': { template: '<div class="el-empty">{{ description }}<slot /></div>', props: ['description', 'image', 'imageSize'] },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size', 'effect'] },
  'el-card': { template: '<div class="el-card"><slot /></div>', props: ['shadow'] },
  'el-tabs': { template: '<div class="el-tabs"><slot /></div>', props: ['modelValue'] },
  'el-tab-pane': { template: '<div class="el-tab-pane"><slot /></div>', props: ['label', 'name'] },
  HealthScoreCircle: { name: 'HealthScoreCircle', template: '<div class="stub-health-score" />', props: ['score', 'level', 'size', 'strokeWidth'] },
  SparklineChart: { name: 'SparklineChart', template: '<div class="stub-sparkline" />', props: ['data', 'height'] },
  ActivityRings: { name: 'ActivityRings', template: '<div class="stub-activity-rings" />', props: ['exercisePercent', 'sleepPercent', 'habitsPercent'] },
  ProfileReminderBanner: { name: 'ProfileReminderBanner', template: '<div class="stub-banner" />', props: ['completeness', 'dismissed'] },
  ProfileCompleteness: { name: 'ProfileCompleteness', template: '<div class="stub-completeness" />', props: ['completeness', 'variant'] },
  QuickInput: { name: 'QuickInput', template: '<div class="stub-quick-input" />' },
  AIInput: { name: 'AIInput', template: '<div class="stub-ai-input" />' },
  OcrInput: { name: 'OcrInput', template: '<div class="stub-ocr-input" />' },
  RecentRecords: { name: 'RecentRecords', template: '<div class="stub-recent-records" />' },
  OcrHistory: { name: 'OcrHistory', template: '<div class="stub-ocr-history" />' },
  AiHistory: { name: 'AiHistory', template: '<div class="stub-ai-history" />' }
}

function statLabels(wrapper: ReturnType<typeof mount>) {
  return wrapper.findAll('.stat-label').map(node => node.text())
}

describe('MetricCountLabels.spec.ts — 「健康指标」两处口径必须在标签上可区分', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getScore.mockResolvedValue(
      ok({
        score: 82,
        level: 'GOOD',
        factors: {},
        calculationMethod: 'RULE',
        calculatedAt: '2026-11-23 10:00:00',
        expiresAt: '2026-11-23 12:00:00',
        isCached: false
      })
    )
    // 仪表盘读的是全时段 total，与日期无关
    getMetrics.mockResolvedValue(ok({ records: [], total: 12 }))
    getTrends.mockResolvedValue(ok([]))
    getCompleteness.mockResolvedValue(
      ok({ score: 60, level: '标准档案', filledCount: 0, totalCount: 0, hasProfile: true, assumptions: [], fields: [] })
    )
    listReports.mockResolvedValue(ok({ records: [], total: 0 }))
  })

  it('仪表盘把全时段数字标成「健康指标（累计）」', async () => {
    const wrapper = mount(Dashboard, {
      global: { stubs: elementStubs, directives: { loading: () => {} } }
    })
    await flushPromises()

    const labels = statLabels(wrapper)
    expect(labels).toContain('健康指标（累计）')
    // 裸「健康指标」会与新页签「今日健康指标」撞名，必须不存在
    expect(labels).not.toContain('健康指标')
    wrapper.unmount()
  })

  it('智能录入页把当日数字标成「今日健康指标」，三个卡片都写明今日', async () => {
    requestGet.mockResolvedValue(ok({ healthMetricsCount: 0, wellnessMetricsCount: 2, aiRecognizeCount: 3 }))

    const wrapper = mount(DataInput, {
      global: { stubs: elementStubs, directives: { loading: () => {} } }
    })
    await flushPromises()

    const labels = wrapper.findAll('.overview-label').map(node => node.text())
    expect(labels).toEqual(['今日健康指标', '今日保健指标', '今日 AI 识别'])
    expect(labels).not.toContain('健康指标')
    wrapper.unmount()
  })
})

/**
 * 仪表盘「体检报告」卡片（IN-14）。
 *
 * 它的 `reportCount` 曾初始化为 0 后再无赋值/请求 —— 卡片永远显示 0，即使上传过报告。
 * 现在读 `/api/screening/reports` 的 total；拿不到就是破折号，不是 0。
 */
describe('dashboard/Index.vue — 体检报告数不能恒为 0', () => {
  function mountDashboard() {
    return mount(Dashboard, {
      global: { stubs: elementStubs, directives: { loading: () => {} } }
    })
  }

  function reportCardValue(wrapper: ReturnType<typeof mountDashboard>) {
    const card = wrapper.findAll('.stat-card').find(node => node.text().includes('体检报告'))
    expect(card).toBeDefined()
    // 取 .stat-value 而不是整卡文本：整卡文本里的其它字符会让 toContain('0') 之类断言空过。
    return (card as NonNullable<typeof card>).find('.stat-value').text()
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getScore.mockResolvedValue(ok({ score: 82, level: 'GOOD', factors: {}, calculationMethod: 'RULE', calculatedAt: '2026-11-23 10:00:00', expiresAt: '2026-11-23 12:00:00', isCached: false }))
    getMetrics.mockResolvedValue(ok({ records: [], total: 12 }))
    getTrends.mockResolvedValue(ok([]))
    getCompleteness.mockResolvedValue(ok({ score: 60, level: '标准档案', filledCount: 0, totalCount: 0, hasProfile: true, assumptions: [], fields: [] }))
    listReports.mockResolvedValue(ok({ records: [], total: 0 }))
  })

  it('把报告列表的 total 显示在卡片上', async () => {
    listReports.mockResolvedValue(ok({ records: [], total: 3 }))

    const wrapper = mountDashboard()
    await flushPromises()

    expect(listReports).toHaveBeenCalledTimes(1)
    expect(reportCardValue(wrapper)).toBe('3')
    wrapper.unmount()
  })

  it('报告列表请求失败时显示破折号，不把 0 伪装成「一份都没有」', async () => {
    listReports.mockRejectedValue({ response: { status: 500, data: {} } })

    const wrapper = mountDashboard()
    await flushPromises()

    expect(reportCardValue(wrapper)).toBe('—')
    wrapper.unmount()
  })

  it('确实没有报告时显示真实的 0（0 是合法值，与「不知道」区分开）', async () => {
    listReports.mockResolvedValue(ok({ records: [], total: 0 }))

    const wrapper = mountDashboard()
    await flushPromises()

    expect(reportCardValue(wrapper)).toBe('0')
    wrapper.unmount()
  })
})

/**
 * 今日统计的三态：已知 / 加载中 / 失败。
 *
 * 旧实现的 catch 是空实现（注释称统计为「装饰性数据」），失败时三个计数保持初值 0 ——
 * 用户看到「今日健康指标 0」会以为刚才那次录入没成功。现在拿不到数就显示破折号，
 * 页面内给出「重新加载统计」入口，绝不把 0 当真实值。
 */
describe('data-input/Index.vue — 今日统计不能把 0 冒充真实值', () => {
  function mountDataInput() {
    return mount(DataInput, {
      global: { stubs: elementStubs, directives: { loading: () => {} } }
    })
  }

  function overviewValues(wrapper: ReturnType<typeof mountDataInput>) {
    return wrapper.findAll('.overview-value').map(node => node.text())
  }

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('拿到数据时显示真实数字，且不渲染失败提示', async () => {
    requestGet.mockResolvedValue(ok({ healthMetricsCount: 4, wellnessMetricsCount: 2, aiRecognizeCount: 3 }))

    const wrapper = mountDataInput()
    await flushPromises()

    expect(overviewValues(wrapper)).toEqual(['4', '2', '3'])
    expect(wrapper.find('.overview-failed').exists()).toBe(false)
    wrapper.unmount()
  })

  it('请求失败时三个卡片显示破折号而不是 0，并给出重试入口', async () => {
    requestGet.mockRejectedValue({ response: { status: 500, data: {} } })

    const wrapper = mountDataInput()
    await flushPromises()

    expect(overviewValues(wrapper)).toEqual(['—', '—', '—'])
    // 0 绝不能出现在失败态里：它是「今天录了 0 条」的断言
    expect(overviewValues(wrapper)).not.toContain('0')

    const notice = wrapper.find('.overview-failed')
    expect(notice.exists()).toBe(true)
    expect(notice.attributes('role')).toBe('status')
    expect(notice.text()).toContain('占位符而非 0')
    expect(notice.text()).toContain('重新加载统计')
    wrapper.unmount()
  })

  it('信封 200 但 data 为空同样是「拿不到数」', async () => {
    requestGet.mockResolvedValue({ code: 200, message: 'success', data: null })

    const wrapper = mountDataInput()
    await flushPromises()

    expect(overviewValues(wrapper)).toEqual(['—', '—', '—'])
    expect(wrapper.find('.overview-failed').exists()).toBe(true)
    wrapper.unmount()
  })

  it('重试成功后提示消失、数字恢复', async () => {
    requestGet.mockRejectedValueOnce({ response: { status: 503, data: {} } })

    const wrapper = mountDataInput()
    await flushPromises()
    expect(wrapper.find('.overview-failed').exists()).toBe(true)

    requestGet.mockResolvedValueOnce(ok({ healthMetricsCount: 1, wellnessMetricsCount: 0, aiRecognizeCount: 2 }))
    await wrapper.find('.overview-failed .el-button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.overview-failed').exists()).toBe(false)
    expect(overviewValues(wrapper)).toEqual(['1', '0', '2'])
    wrapper.unmount()
  })

  it('首载期间（尚未落定）也显示占位符，不是 0', async () => {
    // 永不 resolve：模拟请求在途
    requestGet.mockReturnValue(new Promise(() => {}))

    const wrapper = mountDataInput()
    await flushPromises()

    expect(overviewValues(wrapper)).toEqual(['—', '—', '—'])
    // 在途不是失败，不该弹「无法加载」
    expect(wrapper.find('.overview-failed').exists()).toBe(false)
    wrapper.unmount()
  })
})
