import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'
import type { ProfileCompletenessVO, ProfileFieldStatusVO } from '@/types/api'
import { PROFILE_FIELDS } from '@/utils/profileFields'

/**
 * 仪表盘引导表面的行为证明（Task 1：挂载点、门控、跳转、关闭）：
 * - 横幅全站单实例、位置在 welcome 之后 / stats 之前、门控 score < 60；
 * - 紧凑完整度行与横幅共用同一份 payload，但门控不同（信息性 vs 打扰）；
 * - 完善档案带 ?focus= 且不写静默时间戳；稍后提醒 / × 只写本地状态。
 *
 * 横幅用**真实组件**而不是 stub：stub 自己实现 < 60 门控的话，「60 不打扰」这条
 * 成功标准就成了对 stub 的断言，等于没测。完整度块用 stub —— 它的内部结构已有
 * 13-06 的 23 个测试，这里只需要证明父级传了 variant="compact" 且在正确的门控下渲染。
 */

vi.mock('@/api/score', () => ({
  scoreApi: {
    getScore: vi.fn(),
    recalculateScore: vi.fn(),
    getScoreBreakdown: vi.fn(),
    getScoreHistory: vi.fn()
  }
}))

vi.mock('@/api/health', () => ({
  healthApi: {
    getMetrics: vi.fn()
  }
}))

vi.mock('@/api/stats', () => ({
  statsApi: {
    getTrends: vi.fn()
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

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: { nickname: '测试用户', username: 'tester' },
    token: 'test-token'
  })
}))

vi.mock('@/stores/alert', () => ({
  useAlertStore: () => ({
    unreadCount: 0,
    recentAlerts: [],
    fetchRecentAlerts: vi.fn().mockResolvedValue(undefined),
    fetchUnreadCount: vi.fn().mockResolvedValue(undefined)
  })
}))

const routerPush = vi.fn()

// useRouter 是 inject 实现（RESEARCH Pitfall 2），必须整体 mock vue-router。
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush, replace: vi.fn() })
}))

vi.mock('@/composables/useGsap', () => {
  const timeline = { fromTo: () => timeline }
  return {
    gsap: { timeline: () => timeline },
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

import Dashboard from '@/views/dashboard/Index.vue'
import { scoreApi } from '@/api/score'
import { healthApi } from '@/api/health'
import { statsApi } from '@/api/stats'
import { preventionApi } from '@/api/prevention'

const getScore = scoreApi.getScore as unknown as Mock
const getMetrics = healthApi.getMetrics as unknown as Mock
const getTrends = statsApi.getTrends as unknown as Mock
const getCompleteness = preventionApi.getCompleteness as unknown as Mock

const DISMISS_KEY = 'hhs_profile_reminder_dismissed_at'

/** el-button 不 emit click，让父级 @click 以原生监听器落到根 button 上（否则会触发两次）。 */
const globalStubs = {
  'el-skeleton': {
    template: '<div class="el-skeleton"><slot /></div>',
    props: ['loading', 'animated']
  },
  'el-skeleton-item': {
    template: '<div class="el-skeleton-item" />',
    props: ['variant']
  },
  'el-button': {
    template: '<button class="el-button" :disabled="disabled"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'plain', 'icon', 'link', 'text']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  'el-empty': {
    template: '<div class="el-empty">{{ description }}<slot /></div>',
    props: ['description', 'image', 'imageSize']
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>',
    props: ['type', 'size', 'effect']
  },
  HealthScoreCircle: {
    name: 'HealthScoreCircle',
    props: ['score', 'level', 'size', 'strokeWidth'],
    template: '<div class="stub-health-score" :data-score="score">{{ score }}</div>'
  },
  SparklineChart: {
    name: 'SparklineChart',
    props: ['data', 'height'],
    template: '<div class="stub-sparkline" />'
  },
  ActivityRings: {
    name: 'ActivityRings',
    props: ['exercisePercent', 'sleepPercent', 'habitsPercent'],
    template: '<div class="stub-activity-rings" />'
  },
  // 只断言父级传了什么（13-06 已单独测过内部）。
  ProfileCompleteness: {
    name: 'ProfileCompleteness',
    props: ['completeness', 'variant'],
    template:
      '<div class="stub-completeness" :data-variant="variant">{{ completeness.score }}%</div>'
  }
}

const SCORE_RES = {
  code: 200,
  message: 'success',
  data: {
    score: 82,
    level: 'GOOD',
    factors: {},
    calculationMethod: 'RULE',
    calculatedAt: '2026-11-09 10:00:00',
    expiresAt: '2026-11-09 12:00:00',
    isCached: false
  }
}

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

function makeCompleteness(options: {
  score?: number
  level?: string
  filledKeys?: string[]
}): ProfileCompletenessVO {
  const filledKeys = options.filledKeys ?? []
  const fields: ProfileFieldStatusVO[] = PROFILE_FIELDS.map(field => ({
    ...field,
    filled: filledKeys.includes(field.key)
  }))
  return {
    score: options.score ?? 40,
    level: options.level ?? '基础档案',
    filledCount: fields.filter(field => field.filled).length,
    totalCount: fields.length,
    hasProfile: true,
    assumptions: [],
    fields
  }
}

function mountDashboard() {
  return mount(Dashboard, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

/** 横幅内的两个 EP 按钮：0 = 完善档案，1 = 稍后提醒（× 是原生 button，不在此列）。 */
function bannerButtons(wrapper: ReturnType<typeof mountDashboard>) {
  return wrapper.findAll('.profile-banner .el-button')
}

describe('DashboardProfileBanner.spec.ts — 仪表盘档案引导表面', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    getScore.mockResolvedValue(SCORE_RES)
    getMetrics.mockResolvedValue(ok({ records: [], total: 0 }))
    getTrends.mockResolvedValue(ok([]))
    getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 40 })))
  })

  afterEach(() => {
    localStorage.clear()
  })

  describe('单实例与位置', () => {
    it('renders exactly one banner for a genuinely incomplete profile', async () => {
      const wrapper = mountDashboard()
      await flushPromises()

      expect(wrapper.findAll('.profile-banner')).toHaveLength(1)
      // 单实例是 ROADMAP 成功标准 4 的代码级保证：整个 src/views 只有这一个挂载点。
      expect(wrapper.findAll('.profile-banner')).toHaveLength(
        wrapper.findAll('[role="status"]').length
      )
      wrapper.unmount()
    })

    it('places the banner after the welcome section and before the stats grid', async () => {
      const wrapper = mountDashboard()
      await flushPromises()

      const banner = wrapper.find('.profile-banner').element
      const previous = banner.previousElementSibling as HTMLElement
      expect(previous.className).toContain('welcome-section')

      // 第一个内容块就是横幅 —— 它不能沉到 stats 之后
      const dashboard = wrapper.find('.dashboard').element
      const order = Array.from(dashboard.children).map(node => node.className)
      expect(order.findIndex(name => name.includes('profile-banner'))).toBe(1)
      wrapper.unmount()
    })
  })

  describe('score 门控（成功标准 5：只填性别+出生日期约 60%，不打扰）', () => {
    it('renders nothing at exactly 60', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 60, level: '标准档案' })))
      const wrapper = mountDashboard()
      await flushPromises()

      expect(wrapper.find('.profile-banner').exists()).toBe(false)
      wrapper.unmount()
    })

    it('renders the banner at 59 and below', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 59 })))
      const wrapper = mountDashboard()
      await flushPromises()

      expect(wrapper.find('.profile-banner').exists()).toBe(true)
      wrapper.unmount()
    })

    it('hides the banner when it was dismissed within the seven-day window', async () => {
      localStorage.setItem(DISMISS_KEY, new Date().toISOString())
      const wrapper = mountDashboard()
      await flushPromises()

      expect(wrapper.find('.profile-banner').exists()).toBe(false)
      wrapper.unmount()
    })

    it('shows the banner again once the seven-day window has passed', async () => {
      const eightDaysAgo = new Date(Date.now() - 8 * 24 * 60 * 60 * 1000).toISOString()
      localStorage.setItem(DISMISS_KEY, eightDaysAgo)
      const wrapper = mountDashboard()
      await flushPromises()

      expect(wrapper.find('.profile-banner').exists()).toBe(true)
      wrapper.unmount()
    })
  })

  describe('紧凑完整度行（信息性，不是打扰）', () => {
    it('renders the compact row with variant="compact" and the same payload as the banner', async () => {
      const wrapper = mountDashboard()
      await flushPromises()

      const row = wrapper.findComponent({ name: 'ProfileCompleteness' })
      expect(row.exists()).toBe(true)
      expect(row.props('variant')).toBe('compact')
      const payload = row.props('completeness') as ProfileCompletenessVO
      expect(payload.score).toBe(40)

      // 两个表面共用同一份 payload，永远不会对分数各执一词
      expect(wrapper.find('.profile-banner').text()).toContain('40%')
      wrapper.unmount()
    })
  })

  describe('完善档案：带 focus 跳转，不写静默时间戳', () => {
    it('pushes to /prevention/profile with query.focus set to the required-first missing key', async () => {
      const wrapper = mountDashboard()
      await flushPromises()

      await bannerButtons(wrapper)[0].trigger('click')

      expect(routerPush).toHaveBeenCalledTimes(1)
      expect(routerPush).toHaveBeenCalledWith({
        path: '/prevention/profile',
        query: { focus: 'gender' }
      })
      wrapper.unmount()
    })

    it('points at the recommended field when both required fields are filled', async () => {
      getCompleteness.mockResolvedValue(
        ok(makeCompleteness({ score: 40, filledKeys: ['gender', 'birthDate'] }))
      )
      const wrapper = mountDashboard()
      await flushPromises()

      await bannerButtons(wrapper)[0].trigger('click')

      // 优先级 required → recommended → optional 只在 utils/profileFields.ts 实现一次
      expect(routerPush).toHaveBeenCalledWith({
        path: '/prevention/profile',
        query: { focus: 'heightCm' }
      })
      wrapper.unmount()
    })

    it('does not write the dismissal timestamp when the user abandons the profile page', async () => {
      const wrapper = mountDashboard()
      await flushPromises()

      await bannerButtons(wrapper)[0].trigger('click')

      expect(localStorage.getItem(DISMISS_KEY)).toBeNull()
      // 跳走但未完成 → 下次回来仍应看到提醒
      expect(wrapper.find('.profile-banner').exists()).toBe(true)
      wrapper.unmount()
    })
  })

  describe('稍后提醒 / ×：本地静默', () => {
    it('writes a parseable timestamp and hides the banner immediately', async () => {
      const wrapper = mountDashboard()
      await flushPromises()
      expect(wrapper.find('.profile-banner').exists()).toBe(true)

      await bannerButtons(wrapper)[1].trigger('click')
      await flushPromises()

      const raw = localStorage.getItem(DISMISS_KEY)
      expect(raw).not.toBeNull()
      expect(Number.isNaN(Date.parse(raw as string))).toBe(false)
      expect(wrapper.find('.profile-banner').exists()).toBe(false)
      wrapper.unmount()
    })

    it('dismisses through the close × as the same action', async () => {
      const wrapper = mountDashboard()
      await flushPromises()

      await wrapper.find('.profile-banner__close').trigger('click')
      await flushPromises()

      expect(localStorage.getItem(DISMISS_KEY)).not.toBeNull()
      expect(wrapper.find('.profile-banner').exists()).toBe(false)
      wrapper.unmount()
    })

    it('requests completeness exactly once on mount, silently', async () => {
      const wrapper = mountDashboard()
      await flushPromises()

      expect(getCompleteness).toHaveBeenCalledTimes(1)
      expect(getCompleteness).toHaveBeenCalledWith({ silent: true })
      wrapper.unmount()
    })
  })

  describe('失败态（Contract 8：绝不把失败渲染成 0%）', () => {
    it('renders no banner when the completeness fetch rejects', async () => {
      getCompleteness.mockRejectedValue({
        response: { status: 500, data: { code: 500, message: '服务异常' } }
      })
      const wrapper = mountDashboard()
      await flushPromises()

      expect(wrapper.find('.profile-banner').exists()).toBe(false)
      wrapper.unmount()
    })

    it('never renders a fabricated 0% when the payload is still null', async () => {
      getCompleteness.mockReturnValue(new Promise(() => {}))
      const wrapper = mountDashboard()
      await flushPromises()

      expect(wrapper.find('.profile-banner').exists()).toBe(false)
      expect(wrapper.find('.stub-completeness').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('档案完整度 0%')
      wrapper.unmount()
    })
  })
})
