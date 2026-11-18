import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'
import type { RiskAssessmentVO, RiskContributorVO, MetricCoverageVO } from '@/types/api'

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
    getCompleteness: vi.fn(),
    // 面板覆盖率的唯一前置：缺了它 loadCoverage 会抛 TypeError 被 catch 吞掉，
    // coverage 永远是 [] —— 覆盖面板的全部断言都在断言一个空数组。
    getMetricCoverage: vi.fn()
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
const getMetricCoverage = preventionApi.getMetricCoverage as unknown as Mock

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
  },
  /**
   * 批次折叠的重型 stub：**必须渲染 #title 插槽**。
   *
   * 未 stub 的 <el-collapse> 会渲染成未知元素并丢弃默认插槽 —— 批次内容静默
   * 消失，而所有既有断言照旧通过（green tests, absent feature）。这里连
   * aria-expanded 一并对齐真实 EP，让「默认只展开最近批次」可断言。
   */
  'el-collapse': {
    template: '<div class="el-collapse"><slot /></div>',
    props: ['modelValue']
  },
  'el-collapse-item': {
    template:
      '<div class="el-collapse-item">' +
      '<div class="el-collapse-item__header"><slot name="title" /></div>' +
      '<div class="el-collapse-item__content"><slot /></div></div>',
    props: ['name', 'title']
  },
  'el-empty': {
    template: '<div class="el-empty">{{ description }}</div>',
    props: ['description']
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

/**
 * 镜像 `GET /risk-assessment/metric-coverage` 的真实返回。
 *
 * **本文件只验证渲染，不验证后端的格式化与同源。** 这里不得出现任何模拟后端
 * formatter 或阈值判定的代码 —— 那会把「面板收到了什么就渲染成什么」变成
 * 「前端复刻了一遍后端算式」，两侧一旦漂移，测试反而更绿。
 *
 * 六行覆盖三种组合：
 * 1. 有 riskNote + 有值（BMI / 血糖）—— 正常渲染，`NORMAL` / `HIGH` 两态；
 * 2. 有 riskNote + MISSING（总胆固醇 / 高密度脂蛋白）—— 今天的三个分组谓词交叠，
 *    这一形态会在「参与评估」与「尚未采集」两组里各渲染一次（16-04 修正）；
 * 3. 无 riskNote（步数 / 尿酸）—— 已测但不参与 / 未测且不参与。
 *
 * `高密度脂蛋白` 的 riskNote 是下限型（`低于`），方向动词与上限型相反，用来
 * 证明面板不假设「越大越危险」。
 */
const coverageRows: MetricCoverageVO[] = [
  {
    key: 'bmi', label: 'BMI', unit: 'kg/m²', category: 'HEALTH', state: 'NORMAL',
    value: '22.9', normalRange: '18.5–23.9',
    riskNote: '心血管评估：超过 24.0 kg/m² 计入 20 分'
  },
  {
    key: 'bloodGlucose', label: '血糖', unit: 'mmol/L', category: 'HEALTH', state: 'HIGH',
    value: '13.8', normalRange: '3.9–6.1',
    riskNote: '糖尿病评估：超过 6.1 mmol/L 计入 30 分'
  },
  {
    key: 'totalCholesterol', label: '总胆固醇', unit: 'mmol/L', category: 'HEALTH', state: 'MISSING', value: null, normalRange: '2.8–5.2',
    riskNote: '心血管评估：超过 5.20 mmol/L 计入 20 分'
  },
  {
    key: 'hdlCholesterol', label: '高密度脂蛋白', unit: 'mmol/L', category: 'HEALTH', state: 'MISSING',
    value: null, normalRange: '1.00–2.20',
    riskNote: '心血管评估：低于 1.00 mmol/L 计入 15 分'
  },
  {
    key: 'steps', label: '步数', unit: '步', category: 'WELLNESS', state: 'NORMAL',
    value: '8000', normalRange: null, riskNote: null
  },
  {
    key: 'uricAcid', label: '尿酸', unit: 'μmol/L', category: 'HEALTH', state: 'MISSING',
    value: null, normalRange: '150–420', riskNote: null
  }
]

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

  /**
   * 历史按「一次评估」折叠（Phase 15）。`batchId` 是后端在同一次评估里写给
   * 三条疾病的同一个批次号；平铺表格让用户分不清哪三行属于同一次评估，也看
   * 不出这次比上次是好是坏。
   *
   * 这些断言依赖 globalStubs 里能渲染 #title 的 el-collapse(-item)：没有那个
   * stub，批次内容会静默消失而这些断言全部失败 —— 这正是我们想要的护栏。
   */
  describe('历史批次折叠与趋势', () => {
    /** 一个批次的全部疾病行。 */
    const batchOf = (
      batchId: string | null,
      createTime: string,
      rows: Array<[string, string, number]>
    ): RiskAssessmentVO[] =>
      rows.map(([diseaseName, riskLevel, riskScore], i) => ({
        ...diabetesAssessment,
        id: `${batchId ?? createTime}-${i}` as unknown as number,
        batchId,
        diseaseName,
        riskLevel,
        riskScore,
        createTime,
        contributors: []
      }))

    // 最新批：糖尿病 58→62 并跨入高风险；高血压 40→42；心血管 46→46 持平。
    const latest = batchOf('b3', '2026-11-17 10:00:00', [
      ['糖尿病', 'HIGH', 62],
      ['高血压', 'MEDIUM', 42],
      ['心血管疾病', 'MEDIUM', 46]
    ])
    const previous = batchOf('b2', '2026-11-10 09:00:00', [
      ['糖尿病', 'MEDIUM', 58],
      ['高血压', 'MEDIUM', 40],
      ['心血管疾病', 'MEDIUM', 46]
    ])
    const legacy = batchOf(null, '2026-10-01 08:00:00', [
      ['糖尿病', 'MEDIUM', 50],
      ['高血压', 'MEDIUM', 40],
      ['心血管疾病', 'MEDIUM', 44]
    ])

    const renderBatches = async (data: RiskAssessmentVO[]) => {
      getRiskAssessments.mockResolvedValue({ data })
      const wrapper = mountRisk()
      await flushPromises()
      return wrapper
    }

    it('每个批次渲染成一张卡片，时间与最高风险在头部', async () => {
      const wrapper = await renderBatches([...latest, ...previous, ...legacy])
      const cards = wrapper.findAll('.risk-batch')
      expect(cards.length).toBe(3)

      const head = cards[0].find('.risk-batch__header')
      expect(head.text()).toContain('2026-11-17')
      expect(head.text()).toContain('高风险')
    })

    it('相对上一批次的绝对分差与方向，不用百分比', async () => {
      const wrapper = await renderBatches([...latest, ...previous, ...legacy])
      const latestCard = wrapper.findAll('.risk-batch')[0]
      expect(latestCard.text()).toContain('↑4')
      expect(latestCard.text()).toContain('↑2')
      expect(latestCard.text()).not.toContain('%')
    })

    /**
     * D-02 的正面锚定：三个批次的数字刻意互不相同，使两种错误实现当场失败。
     *
     * - 若把每个批次都锚定在**最旧**批次上，panels[0] 会变成 ↑20/↓18/↑30，
     *   与 ↑4/↓4/↑10 不符；
     * - 若把对照组写成 batches[i-1]（锚定**更新**的批次），panels[1] 糖尿病
     *   会变成 46-50=↓4 而不是 ↑16。
     *
     * 只有两批时这两种错误都算得出相同数字而全绿，所以必须是三批。
     */
    it('分差只锚定紧邻上一批次（三批次互不相同的数字钉死 D-02）', async () => {
      const mkBatch = (
        batchId: string,
        createTime: string,
        rows: Array<[string, string, number]>
      ): RiskAssessmentVO[] =>
        rows.map(([diseaseName, riskLevel, riskScore], i) => ({
          ...diabetesAssessment,
          id: `${batchId}-${i}` as unknown as number,
          batchId,
          diseaseName,
          riskLevel,
          riskScore,
          createTime,
          contributors: []
        }))

      const threeBatches = [
        ...mkBatch('b20261117100000-1', '2026-11-17 10:00:00', [
          ['糖尿病', 'MEDIUM', 50],
          ['高血压', 'MEDIUM', 44],
          ['心血管疾病', 'MEDIUM', 40]
        ]),
        ...mkBatch('b20261116090000-2', '2026-11-16 09:00:00', [
          ['糖尿病', 'MEDIUM', 46],
          ['高血压', 'MEDIUM', 48],
          ['心血管疾病', 'LOW', 30]
        ]),
        ...mkBatch('b20261115090000-3', '2026-11-15 09:00:00', [
          ['糖尿病', 'LOW', 30],
          ['高血压', 'HIGH', 62],
          ['心血管疾病', 'LOW', 10]
        ])
      ]

      const wrapper = await renderBatches(threeBatches)
      const panels = wrapper.findAll('.risk-batch')
      expect(panels.length).toBe(3)

      /** 面板内 疾病名 → 分差文本，保证配对在同一行内。 */
      const deltasOf = (index: number): Record<string, string> => {
        const out: Record<string, string> = {}
        for (const row of panels[index].findAll('.risk-batch__row')) {
          const disease = row.find('.risk-batch__disease').text()
          const delta = row.find('.risk-batch__delta')
          out[disease] = delta.exists() ? delta.text() : ''
        }
        return out
      }

      // 最新 vs 中间
      expect(deltasOf(0)).toMatchObject({
        糖尿病: '↑4',
        高血压: '↓4',
        心血管疾病: '↑10'
      })
      // 中间 vs 最旧 —— 正是这一组让「锚定最旧」与「锚定更新」都被证伪
      expect(deltasOf(1)).toMatchObject({
        糖尿病: '↑16',
        高血压: '↓14',
        心血管疾病: '↑20'
      })
      // 最旧无对照组
      expect(panels[2].findAll('.risk-batch__delta--first').length).toBe(3)

      expect(panels[0].text()).not.toContain('%')
      expect(panels[1].text()).not.toContain('%')
    })

    it('三批次时默认只展开最近一个批次', async () => {
      const mkOne = (batchId: string, createTime: string) =>
        ['糖尿病', '高血压', '心血管疾病'].map((diseaseName, i) => ({
          ...diabetesAssessment,
          id: `${batchId}-${i}` as unknown as number,
          batchId,
          diseaseName,
          createTime,
          contributors: []
        }))
      const wrapper = await renderBatches([
        ...mkOne('b-newest', '2026-11-17 10:00:00'),
        ...mkOne('b-middle', '2026-11-16 09:00:00'),
        ...mkOne('b-oldest', '2026-11-15 09:00:00')
      ])

      // defineExpose 只暴露 { viewDetail, handleCreateClick }，读 setup 状态需 cast。
      // 单批次 fixture 下「全展开」也能通过，只有三批次能把全展开实现判为失败。
      const vm = wrapper.vm as unknown as { activeBatchNames: string[] }
      expect(vm.activeBatchNames).toEqual(['b-newest'])
    })

    it('分差为 0 显示「持平」，全页不出现「↑0」', async () => {
      const wrapper = await renderBatches([...latest, ...previous, ...legacy])
      expect(wrapper.findAll('.risk-batch')[0].text()).toContain('持平')
      expect(wrapper.text()).not.toContain('↑0')
    })

    it('跨等级阈值时单独显示等级变化', async () => {
      const wrapper = await renderBatches([...latest, ...previous, ...legacy])
      const change = wrapper.findAll('.risk-batch')[0].find('.risk-batch__level-change')
      expect(change.exists()).toBe(true)
      expect(change.text()).toContain('中风险')
      expect(change.text()).toContain('高风险')
    })

    it('最早批次无对照组时全部显示「首次评估」', async () => {
      const wrapper = await renderBatches([...latest, ...previous, ...legacy])
      const oldest = wrapper.findAll('.risk-batch')[2]
      expect(oldest.findAll('.risk-batch__delta--first').length).toBe(3)
    })

    it('只有 1 个批次时全部是「首次评估」', async () => {
      const wrapper = await renderBatches([...latest])
      const card = wrapper.findAll('.risk-batch')[0]
      expect(card.findAll('.risk-batch__delta--first').length).toBe(3)
      expect(wrapper.text()).not.toContain('↑0')
    })

    it('无 batchId 的存量记录正常渲染，且说明未保存明细', async () => {
      const wrapper = await renderBatches([...legacy])
      const cards = wrapper.findAll('.risk-batch')
      expect(cards.length).toBe(1)
      // legacy- 批次共享同一个 createTime，因此折叠成一个批次。
      expect(cards[0].find('.risk-batch__legacy').text()).toContain('该记录创建时未保存评分明细')
      expect(wrapper.text()).not.toContain('undefined')
    })

    /**
     * 存量记录不能按 `createTime` 精确相等分组。
     *
     * `create_time` 是 `DATETIME DEFAULT CURRENT_TIMESTAMP`（秒精度）。同一次旧评估
     * 的三条记录落在秒边界两侧时，精确相等会把「一次评估」展成两张卡片
     * （2 条 + 1 条），比分差更容易误导人。
     */
    it('跨秒边界的同一批存量记录仍折叠成一张卡片', async () => {
      const mkLegacy = (createTime: string, diseaseName: string, score: number) => ({
        ...diabetesAssessment,
        id: `${createTime}-${diseaseName}` as unknown as number,
        batchId: null,
        diseaseName,
        riskScore: score,
        createTime,
        contributors: []
      })
      const wrapper = await renderBatches([
        mkLegacy('2026-10-01 08:00:00', '糖尿病', 50),
        mkLegacy('2026-10-01 08:00:00', '高血压', 40),
        // 这一条写入时跨过了秒边界
        mkLegacy('2026-10-01 08:00:01', '心血管疾病', 44)
      ])

      const cards = wrapper.findAll('.risk-batch')
      expect(cards.length).toBe(1)
      expect(cards[0].findAll('.risk-batch__row').length).toBe(3)
    })

    /**
     * 反过来：时间上真正分开的两次旧评估**不得**被合并，否则会把两次评估
     * 说成一次，趋势也失去意义。
     */
    it('相隔较远的两次存量评估不会被合并', async () => {
      const mkLegacy = (createTime: string, diseaseName: string) => ({
        ...diabetesAssessment,
        id: `${createTime}-${diseaseName}` as unknown as number,
        batchId: null,
        diseaseName,
        createTime,
        contributors: []
      })
      const wrapper = await renderBatches([
        // 两次评估相隔约 30 分钟：真实存在，但足够近以暴露过大的聚类窗口。
        mkLegacy('2026-10-01 09:30:00', '糖尿病'),
        mkLegacy('2026-10-01 09:30:00', '高血压'),
        mkLegacy('2026-10-01 09:30:00', '心血管疾病'),
        mkLegacy('2026-10-01 09:00:00', '糖尿病'),
        mkLegacy('2026-10-01 09:00:00', '高血压'),
        mkLegacy('2026-10-01 09:00:00', '心血管疾病')
      ])

      const cards = wrapper.findAll('.risk-batch')
      expect(cards.length).toBe(2)
      expect(cards[0].findAll('.risk-batch__row').length).toBe(3)
      expect(cards[1].findAll('.risk-batch__row').length).toBe(3)
    })

    it('无任何记录时显示空态，不残留空表格', async () => {
      const wrapper = await renderBatches([])
      expect(wrapper.findAll('.risk-batch').length).toBe(0)
      expect(wrapper.find('.risk-batches__empty').text()).toContain('暂无风险评估记录')
    })

    /**
     * 分页单位必须与展示单位一致。
     *
     * 后端一次返回全部记录，不吃 page/size，所以切分在客户端。若按「行」分页，
     * 12 条记录会让分页器显示 12 —— 而屏幕上只有 4 张批次卡片，用户被告知还有
     * 第二页，点进去却什么也没有（后端根本没有第二页）。
     */
    it('分页总数按批次计，与屏幕上的卡片数一致', async () => {
      const mkBatch = (id: string, time: string) =>
        ['糖尿病', '高血压', '心血管疾病'].map((diseaseName, i) => ({
          ...diabetesAssessment,
          id: `${id}-${i}` as unknown as number,
          batchId: id,
          diseaseName,
          createTime: time,
          contributors: []
        }))
      // 4 个批次 = 12 条记录
      const wrapper = await renderBatches([
        ...mkBatch('b4', '2026-11-17 10:00:00'),
        ...mkBatch('b3', '2026-11-16 10:00:00'),
        ...mkBatch('b2', '2026-11-15 10:00:00'),
        ...mkBatch('b1', '2026-11-14 10:00:00')
      ])

      expect(wrapper.findAll('.risk-batch').length).toBe(4)
      // 总数以批次为单位：4，而不是 12 条记录。
      const vm = wrapper.vm as unknown as { pagination: { total: number; size: number } }
      expect(vm.pagination.total).toBe(4)
      expect(vm.pagination.size).toBeGreaterThanOrEqual(4)
    })

    /**
     * 展开态必须跟着可见页面走。
     *
     * 若 `activeBatchNames` 锚定**全局**最新批次，翻到第 2 页后它指向一个不在
     * 屏幕上的批次名，本页唯一那张卡片就渲染成折叠状态 —— 用户翻页后看到一张
     * 点开才有内容的空壳。
     */
    it('翻页后展开态跟随本页最新批次，不会指向屏外批次', async () => {
      const mkBatch = (id: string, time: string) =>
        ['糖尿病', '高血压', '心血管疾病'].map((diseaseName, i) => ({
          ...diabetesAssessment,
          id: `${id}-${i}` as unknown as number,
          batchId: id,
          diseaseName,
          createTime: time,
          contributors: []
        }))
      const wrapper = await renderBatches([
        ...mkBatch('b3', '2026-11-17 10:00:00'),
        ...mkBatch('b2', '2026-11-16 10:00:00'),
        ...mkBatch('b1', '2026-11-15 10:00:00')
      ])
      const vm = wrapper.vm as unknown as {
        pagination: { page: number; size: number; total: number }
        activeBatchNames: string[]
      }

      // 第 1 页：3 个批次里展开最新的 b3。
      expect(vm.activeBatchNames).toEqual(['b3'])

      // 每页 2 个 → 第 2 页只剩 b1。
      vm.pagination.size = 2
      vm.pagination.page = 2
      await flushPromises()
      await new Promise(r => setTimeout(r, 30))
      await flushPromises()

      const cards = wrapper.findAll('.risk-batch')
      expect(cards.length).toBe(1)
      // 关键断言：展开的是本页那一个，不是屏外的 b3。
      expect(vm.activeBatchNames).toEqual(['b1'])
    })

    /**
     * 在第 3 页点「创建评估」后，用户必须看得见新建的那一次评估。
     *
     * 新批次永远是最新的一个，排在第 1 页开头。若留在第 3 页，会弹出
     * 「评估创建成功」而屏幕上一张新卡片都没有 —— 用户会以为创建失败了。
     */
    it('在后面的页创建评估会跳回第一页，让新批次可见', async () => {
      const mkBatch = (id: string, time: string) =>
        ['糖尿病', '高血压', '心血管疾病'].map((diseaseName, i) => ({
          ...diabetesAssessment,
          id: `${id}-${i}` as unknown as number,
          batchId: id,
          diseaseName,
          createTime: time,
          contributors: []
        }))
      const wrapper = await renderBatches([
        ...mkBatch('b6', '2026-11-17 10:00:00'),
        ...mkBatch('b5', '2026-11-16 10:00:00'),
        ...mkBatch('b4', '2026-11-15 10:00:00'),
        ...mkBatch('b3', '2026-11-14 10:00:00'),
        ...mkBatch('b2', '2026-11-13 10:00:00'),
        ...mkBatch('b1', '2026-11-12 10:00:00')
      ])
      const vm = wrapper.vm as unknown as {
        pagination: { page: number; size: number }
        activeBatchNames: string[]
        createRiskAssessment: () => Promise<void>
      }
      vm.pagination.size = 2
      vm.pagination.page = 3
      await flushPromises()

      // 直接驱动创建成功后的收尾逻辑：这里关心的是「创建完成后分页与展开态
      // 是否把新批次带到眼前」，不是「点一次按钮走哪条分支」（缺指标时按钮
      // 会先展开只读依据面板，那是另一条已被覆盖的路径）。
      await vm.createRiskAssessment()
      await flushPromises()

      expect(vm.pagination.page).toBe(1)
      // 回到第 1 页后，最新的 b6 在屏幕上并且是展开的。
      expect(vm.activeBatchNames).toEqual(['b6'])
      expect(wrapper.findAll('.risk-batch').length).toBeGreaterThan(0)
    })
  })
})

/**
 * 布局契约：批次行的网格列数必须等于模板渲染的子元素数。
 *
 * 这条测试的存在理由是一次真实缺陷：模板在跨等级时渲染 6 个子元素
 * （疾病 / 等级标签 / 分数+分差 / 等级变化 / 建议 / 详情），而 CSS 只声明了 5 列，
 * 于是最该看清的那一行把详情按钮折到了第二行，普通行却正常。
 *
 * 之前的 13 项 UAT 全绿却没抓到 —— 它们只查类名与文本，没有一项看几何。
 * happy-dom 不做布局，所以这里改为源码级契约：列数对不上就红。
 */
describe('Risk.vue — 批次行网格列数与子元素数一致', () => {
  it('grid-template-columns 的列数等于模板中该行的直接子元素数', async () => {
    const fs = await import('node:fs')
    const path = await import('node:path')
    const src = fs.readFileSync(
      path.resolve(__dirname, '../../views/prevention/Risk.vue'),
      'utf8'
    )

    // 1. 取 .risk-batch__row 的 grid-template-columns 列数
    const cssBlock = src.match(/\.risk-batch__row\s*\{[\s\S]*?\}/)
    expect(cssBlock, '未找到 .risk-batch__row 样式块').not.toBeNull()
    const colsLine = cssBlock![0].match(/grid-template-columns:\s*([^;]+);/)
    expect(colsLine, '.risk-batch__row 未声明 grid-template-columns').not.toBeNull()

    // 按空白切分并排除 minmax(...) 内部的空格
    const colSpec = colsLine![1].trim()
    let depth = 0
    let colCount = 1
    for (const ch of colSpec) {
      if (ch === '(') depth++
      else if (ch === ')') depth--
      else if (/\s/.test(ch) && depth === 0) colCount++
    }
    // 连续空格只算一次
    colCount = colSpec.split(/\s+(?![^()]*\))/).filter(Boolean).length

    // 2. 数模板中 .risk-batch__row 内的直接子元素
    //    子元素标记：span.risk-batch__disease / el-tag / span.risk-batch__score /
    //    span.risk-batch__level-change / span.risk-batch__suggestion / el-button
    const rowTemplate = src.match(/class="risk-batch__row"[\s\S]*?<\/div>\s*<\/div>\s*<\/el-collapse-item>/)
    expect(rowTemplate, '未找到批次行模板').not.toBeNull()

    const childMarkers = [
      /class="risk-batch__disease"/,
      /<el-tag[\s\S]*?<\/el-tag>/,
      /class="risk-batch__score"/,
      /class="risk-batch__level-change"/,
      /class="risk-batch__suggestion"/,
      /<el-button[^>]*>详情<\/el-button>/,
    ]
    const childCount = childMarkers.filter(m => m.test(rowTemplate![0])).length

    expect(childCount).toBe(6) // 模板前提：六个子元素都在
    expect(colCount).toBe(childCount)
  })

  it('存在 767px 媒体查询，把批次行折成两列以免窄屏横向溢出', async () => {
    const fs = await import('node:fs')
    const path = await import('node:path')
    const src = fs.readFileSync(
      path.resolve(__dirname, '../../views/prevention/Risk.vue'),
      'utf8'
    )

    // 实测 375px 下六列横排会 scrollWidth 444 > clientWidth 375。
    //
    // 文件里有两个 767px 媒体查询（另一个属于 .risk-coverage），所以必须定位到
    // 「里面真的含有 .risk-batch__row 折列规则」的那一个，而不是第一个匹配。
    // 取所有 767px 块，逐个检查其后的规则体。
    const blocks = src.split(/@media\s*\(max-width:\s*767px\)\s*\{/)
    expect(blocks.length, '缺少 767px 媒体查询').toBeGreaterThan(1)

    const hasRowRule = blocks.slice(1).some(block => {
      const rule = block.match(/\.risk-batch__row\s*\{[\s\S]*?\}/)
      return !!rule && rule[0].includes('grid-template-columns')
    })
    expect(hasRowRule, '没有任何 767px 媒体查询为 .risk-batch__row 声明 grid-template-columns').toBe(true)
  })
})

/**
 * 呈现契约的源码级门禁（UI-SPEC Contract 3.2 / §Color / Contract 4.2）。
 *
 * 「需要有人记得去跑的 grep 就是不再跑的 grep」—— 这三件事（描边 token、
 * 信息文字对比度、六字指标名列宽）都是没有肉眼可见报错的纯样式缺陷，
 * 只有把它们写进 `test:run` 才会在下次被改坏时立刻变红。
 */
describe('Risk.vue — 呈现契约（源码扫描）', () => {
  const readRisk = async (): Promise<string> => {
    const fs = await import('node:fs')
    const path = await import('node:path')
    return fs.readFileSync(
      path.resolve(__dirname, '../../views/prevention/Risk.vue'),
      'utf8'
    )
  }

  /** 取某个选择器的样式块，取不到时返回空串（断言会给出可读失败信息）。 */
  const blockOf = (src: string, selector: string): string => {
    const escaped = selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    const m = src.match(new RegExp(`${escaped}\\s*\\{[\\s\\S]*?\\}`))
    return m ? m[0] : ''
  }

  it('归因行的分隔线指向已定义的 --border，而不是未定义的 --border-1', async () => {
    const src = await readRisk()
    const block = blockOf(src, '.risk-dialog-breakdown__item')

    expect(block, '未找到 .risk-dialog-breakdown__item 样式块').not.toBe('')
    // 区域限定的正负成对断言：只查全文在这个阶段会红（还有 6 处未改），
    // 只查「含 var(--border)」又会被相邻选择器混淆。
    expect(block).toContain('border-bottom: 1px solid var(--border);')
    expect(block).not.toContain('--border-1')
  })

  /**
   * 覆盖面板必须在测试里真的渲染出行。
   *
   * 在此之前 `preventionApi.getMetricCoverage` 根本不在 mock 工厂里，调用抛
   * TypeError 被 `loadCoverage` 的 `catch` 吞掉，`coverage` 恒为 `[]` —— 面板的
   * 每条断言都在断言一个空数组。
   */
  it('覆盖面板渲染出后端会给出的行，不再恒为空数组', async () => {
    getMetricCoverage.mockResolvedValue({ data: coverageRows })
    const wrapper = mountRisk()
    await flushPromises()

    const items = wrapper.findAll('.risk-coverage__item')
    // 已知缺陷（16-04 修正）：今天的 missingMetrics 谓词不看 riskNote，
    // 「有 riskNote 且 MISSING」的两行会被重复渲染，所以这 6 行 fixture 实际
    // 渲染成 8 项。断言写成 >= 而不是 ==，免得把待修的重复固化成契约。
    expect(items.length).toBeGreaterThanOrEqual(coverageRows.length)
    expect(wrapper.text()).toContain('高密度脂蛋白')
  })
})
