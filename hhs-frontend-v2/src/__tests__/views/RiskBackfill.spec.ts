import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'
import type { RiskAssessmentVO, RiskContributorVO } from '@/types/api'

/**
 * 风险页的行为证明（PRF-06 / PRF-08，以及本轮「评估直接读档案、不再二次填表」的改造）。
 *
 * 本文件替换了原先 1000+ 行的补填面板测试：那套面板已被移除，因为评估本来就读取
 * 健康档案与指标，要求用户在风险页重复录入性别/生日是多余且易错的。
 *
 * 现在由断言保证的不变量：
 * 1. 点击创建 → 先进入加载态（真实请求在跑），数据到齐后面板才出现；
 * 2. 面板**没有任何输入控件**，只读展示数据来源与缺失项；
 * 3. 缺失项被明确标注，不会被渲染成「正常」；
 * 4. 详情弹窗逐项列出评分构成，且各项之和等于风险评分。
 */

const routerPush = vi.fn()

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

vi.mock('@/composables/useGsap', () => {
  const timeline = { fromTo: () => timeline }
  return {
    gsap: { timeline: () => timeline },
    cleanupScrollTriggers: vi.fn(),
    staggerReveal: vi.fn(),
    countUp: vi.fn(),
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

import Risk from '@/views/prevention/Risk.vue'
import { preventionApi } from '@/api/prevention'

const createRiskAssessment = preventionApi.createRiskAssessment as unknown as Mock
const getRiskAssessments = preventionApi.getRiskAssessments as unknown as Mock
const getProfile = preventionApi.getProfile as unknown as Mock
const getCompleteness = preventionApi.getCompleteness as unknown as Mock

/**
 * 轻量 EP stub。关键点：**不提供任何输入控件 stub 的交互语义** ——
 * 若页面重新引入表单，`.risk-estimate input` 的断言会失败。
 */
const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-table': {
    template: '<div class="el-table"><slot /></div>',
    props: ['data', 'stripe']
  },
  'el-table-column': {
    template: '<div class="el-table-column" />',
    props: ['prop', 'label', 'width', 'minWidth', 'showOverflowTooltip', 'fixed']
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>',
    props: ['type', 'size', 'effect']
  },
  'el-pagination': { template: '<div class="el-pagination" />' },
  'el-button': {
    template: '<button class="el-button" v-bind="$attrs"><slot /></button>',
    props: ['type', 'loading', 'text']
  },
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  },
  'el-skeleton': {
    template: '<div class="el-skeleton" />',
    props: ['rows', 'animated']
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /></div>',
    props: ['modelValue', 'title', 'width']
  },
  'el-descriptions': {
    template: '<div class="el-descriptions"><slot /></div>',
    props: ['column', 'border']
  },
  'el-descriptions-item': {
    template: '<div class="el-descriptions-item"><slot /></div>',
    props: ['label']
  }
}

/** 一个完整的评估结果：糖尿病 50 分，归因四项合计 50。 */
const diabetesAssessment: RiskAssessmentVO = {
  id: 1,
  userId: 7,
  profileId: 3,
  diseaseName: '糖尿病',
  riskLevel: 'MEDIUM',
  riskScore: 50,
  suggestion: '建议定期体检',
  profileCompleteness: 60,
  assumptions: ['缺少 BMI，按 22.0 估算'],
  contributors: [
    {
      key: 'age', label: '年龄', state: 'CONTRIBUTED',
      value: '40 岁', threshold: '45 岁', points: 10,
      explanation: '年龄 40 岁，未超过 45 岁，计入 10 分'
    },
    {
      key: 'bmi', label: 'BMI', state: 'ASSUMED',
      value: '22.0（估算）', threshold: '24.0', points: 10,
      explanation: 'BMI 22.0，档案缺失按 22.0 估算，未超过 24.0，计入 10 分'
    },
    {
      key: 'familyHistory', label: '家族病史', state: 'MISSING',
      value: null, threshold: null, points: 0,
      explanation: '缺少家族病史数据，此项未计入评分'
    },
    {
      key: 'bloodGlucose', label: '血糖', state: 'CONTRIBUTED',
      value: '13.8 mmol/L', threshold: '6.1 mmol/L', points: 30,
      explanation: '血糖 13.8 mmol/L，超过参考值 6.1 mmol/L，因此计入 30 分'
    }
  ] as RiskContributorVO[],
  createTime: '2026-11-17 10:00:00'
}

const profileWithGaps = {
  id: 3,
  userId: 7,
  gender: 'male',
  birthDate: '1986-01-01',
  heightCm: null,
  weightKg: null,
  bmi: null,
  bloodType: '',
  allergyHistory: '',
  familyHistory: '',
  lifestyleHabits: '',
  createTime: '2026-11-16 10:00:00',
  updateTime: '2026-11-16 10:00:00'
}

const completenessPayload = {
  score: 60,
  level: '基础档案',
  filledCount: 6,
  totalCount: 9,
  hasProfile: true,
  assumptions: ['缺少 BMI，按 22.0 估算'],
  fields: []
}

function mountRisk() {
  return mount(Risk, { global: { stubs: globalStubs } })
}

describe('Risk.vue — 评估直接读取档案，不二次填表', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getRiskAssessments.mockResolvedValue({ data: [diabetesAssessment] })
    getProfile.mockResolvedValue({ data: profileWithGaps })
    getCompleteness.mockResolvedValue({ data: completenessPayload })
    createRiskAssessment.mockResolvedValue({ data: [diabetesAssessment] })
  })

  describe('创建前的加载态', () => {
    it('点击后先显示加载态，数据到齐后才消失', async () => {
      // 用一个手动 resolve 的 promise 卡住请求，才能观察到「加载中」这一帧。
      let release!: () => void
      const gate = new Promise<void>(resolve => {
        release = resolve
      })
      getProfile.mockImplementation(async () => {
        await gate
        return { data: profileWithGaps }
      })

      const wrapper = mountRisk()
      await flushPromises()
      expect(wrapper.find('.risk-preparing').exists()).toBe(false)

      const btn = wrapper.findAll('button').find(b => b.text().includes('创建评估'))
      expect(btn).toBeDefined()
      await btn!.trigger('click')
      await wrapper.vm.$nextTick()

      // 请求尚未落定 → 必须显示加载态
      expect(wrapper.find('.risk-preparing').exists()).toBe(true)
      expect(wrapper.find('.risk-preparing__text').text()).toContain('正在读取')

      release()
      await flushPromises()
      expect(wrapper.find('.risk-preparing').exists()).toBe(false)
    })

    it('加载态带 role=status 与 aria-live，屏幕阅读器能感知', async () => {
      let release!: () => void
      const gate = new Promise<void>(resolve => {
        release = resolve
      })
      getProfile.mockImplementation(async () => {
        await gate
        return { data: profileWithGaps }
      })

      const wrapper = mountRisk()
      await flushPromises()
      const btn = wrapper.findAll('button').find(b => b.text().includes('创建评估'))
      await btn!.trigger('click')
      await wrapper.vm.$nextTick()

      const el = wrapper.find('.risk-preparing')
      expect(el.exists()).toBe(true)
      expect(el.attributes('role')).toBe('status')
      expect(el.attributes('aria-live')).toBe('polite')

      release()
      await flushPromises()
    })
  })

  describe('只读数据面板', () => {
    it('面板里没有任何输入控件 —— 评估不要求二次录入', async () => {
      const wrapper = mountRisk()
      await flushPromises()
      const btn = wrapper.findAll('button').find(b => b.text().includes('创建评估'))
      await btn!.trigger('click')
      await flushPromises()

      const panel = wrapper.find('.risk-estimate')
      expect(panel.exists()).toBe(true)
      expect(panel.findAll('input').length).toBe(0)
      expect(panel.findAll('textarea').length).toBe(0)
      expect(panel.findAll('.el-form').length).toBe(0)
    })

    it('逐项列出数据来源，并标注来源类别', async () => {
      const wrapper = mountRisk()
      await flushPromises()
      const btn = wrapper.findAll('button').find(b => b.text().includes('创建评估'))
      await btn!.trigger('click')
      await flushPromises()

      const rows = wrapper.findAll('.risk-estimate__source')
      expect(rows.length).toBeGreaterThan(0)

      const labels = rows.map(r => r.find('.risk-estimate__source-label').text())
      expect(labels).toContain('年龄')
      expect(labels).toContain('BMI')
      expect(labels).toContain('收缩压')
      expect(labels).toContain('血糖')

      const origins = rows.map(r => r.find('.risk-estimate__source-origin').text())
      expect(origins.some(o => o.includes('健康档案'))).toBe(true)
      expect(origins.some(o => o.includes('指标'))).toBe(true)
    })

    it('档案缺 BMI 时标为 assumed，不谎称是用户数据', async () => {
      const wrapper = mountRisk()
      await flushPromises()
      const btn = wrapper.findAll('button').find(b => b.text().includes('创建评估'))
      await btn!.trigger('click')
      await flushPromises()

      const bmiRow = wrapper.findAll('.risk-estimate__source')
        .find(r => r.find('.risk-estimate__source-label').text() === 'BMI')
      expect(bmiRow).toBeDefined()
      expect(bmiRow!.classes().join(' ')).toContain('assumed')
      expect(bmiRow!.text()).toContain('估算')
    })

    it('无指标数据时标为 missing，明确「未计入」而不是「正常」', async () => {
      getRiskAssessments.mockResolvedValue({ data: [] })
      const wrapper = mountRisk()
      await flushPromises()
      const btn = wrapper.findAll('button').find(b => b.text().includes('创建评估'))
      await btn!.trigger('click')
      await flushPromises()

      const bpRow = wrapper.findAll('.risk-estimate__source')
        .find(r => r.find('.risk-estimate__source-label').text() === '收缩压')
      expect(bpRow).toBeDefined()
      expect(bpRow!.classes().join(' ')).toContain('missing')
      expect(bpRow!.text()).toContain('未计入')
    })

    it('缺失项给出「去完善档案」入口而不是就地录入', async () => {
      const wrapper = mountRisk()
      await flushPromises()
      const btn = wrapper.findAll('button').find(b => b.text().includes('创建评估'))
      await btn!.trigger('click')
      await flushPromises()

      const goProfile = wrapper.findAll('button').find(b => b.text().includes('去完善档案'))
      expect(goProfile).toBeDefined()

      await goProfile!.trigger('click')
      expect(routerPush).toHaveBeenCalledWith(
        expect.objectContaining({ path: '/prevention/profile' })
      )
    })

    it('确认按钮文案表明「按现有数据」创建，用户知道缺项会被代入', async () => {
      const wrapper = mountRisk()
      await flushPromises()
      const btn = wrapper.findAll('button').find(b => b.text().includes('创建评估'))
      await btn!.trigger('click')
      await flushPromises()

      const confirmBtn = wrapper.findAll('button').find(b => b.text().includes('按现有数据创建'))
      expect(confirmBtn).toBeDefined()

      await confirmBtn!.trigger('click')
      await flushPromises()
      expect(createRiskAssessment).toHaveBeenCalledTimes(1)
    })
  })

  describe('详情弹窗的评分构成', () => {
    /** 表格列由 EP 渲染，stub 下取不到行内按钮，因此直接驱动组件方法。 */
    async function openDetail(wrapper: ReturnType<typeof mountRisk>) {
      const vm = wrapper.vm as unknown as {
        viewDetail: (row: RiskAssessmentVO) => void
      }
      vm.viewDetail(diabetesAssessment)
      await flushPromises()
    }

    it('逐项渲染归因，并显示各项合计等于风险评分', async () => {
      const wrapper = mountRisk()
      await flushPromises()
      await openDetail(wrapper)

      const items = wrapper.findAll('.risk-dialog-breakdown__item')
      expect(items.length).toBe(4)

      const sum = diabetesAssessment.contributors!.reduce((a, c) => a + c.points, 0)
      expect(sum).toBe(diabetesAssessment.riskScore)
      expect(wrapper.find('.risk-dialog-breakdown__sum').text()).toContain(String(sum))
    })

    it('四种状态各自渲染成可读标签', async () => {
      const wrapper = mountRisk()
      await flushPromises()
      await openDetail(wrapper)

      const states = wrapper.findAll('.risk-dialog-breakdown__state').map(s => s.text())
      expect(states).toContain('已计入')
      expect(states).toContain('按默认值')
      expect(states).toContain('缺数据')
    })

    it('有缺数据项时显示警示，提醒不要把「未计入」当作「正常」', async () => {
      const wrapper = mountRisk()
      await flushPromises()
      await openDetail(wrapper)

      const warn = wrapper.find('.risk-dialog-breakdown__warn')
      expect(warn.exists()).toBe(true)
      expect(warn.text()).toContain('缺')
      expect(warn.text()).toContain('正常')
    })

    it('无归因时不渲染评分构成（后端无法重建的历史记录）', async () => {
      const wrapper = mountRisk()
      await flushPromises()
      const vm = wrapper.vm as unknown as { viewDetail: (row: RiskAssessmentVO) => void }
      vm.viewDetail({ ...diabetesAssessment, contributors: [] })
      await flushPromises()

      expect(wrapper.find('.risk-dialog-breakdown').exists()).toBe(false)
    })
  })
})
