import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'
import type { ProfileCompletenessVO, ProfileFieldStatusVO } from '@/types/api'
import { PROFILE_FIELDS } from '@/utils/profileFields'

/**
 * 风险页档案引导的行为证明（PRF-06 / PRF-08、D-06、UI-SPEC Contract 4/5/8）。
 *
 * 三条不变量由这里的断言而非注释保证：
 * 1. 首次访问时 onMounted 就拉取完整度 —— 否则画不出徽标，三序判定也会对着空 store 求值；
 * 2. 点击时 store 仍为空 → 先 await 一次单飞请求（挂载中的同一个 promise）再判定，绝不抢先创建；
 * 3. 面板的假设列表 = `completeness.assumptions` 原样渲染，不是前端逐字段拼接。
 *
 * 关键设计：`el-form` stub 只实现「required 规则」这一件真实语义 —— 它读的是页面真正
 * 传给 el-form 的 `rules`，因此「留空的可选字段不阻断保存」证明的是页面的规则表，
 * 而不是 stub 自己的行为。
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

// useRoute / useRouter 是 inject 实现，必须 mock（RESEARCH Pitfall 2）。
// 用 importOriginal 保留 createRouter / createWebHistory：@/stores/auth 会 import
// `@/router`，整体替换会让 router/index.ts 在模块求值时崩掉。
vi.mock('vue-router', async importOriginal => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ query: {} }),
    useRouter: () => ({ push: routerPush, replace: vi.fn() })
  }
})

// 部分 mock：保住组件导出，只替换 ElMessage，以便对 toast 计数（含 showError 内部调用）。
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
import { ElMessage } from 'element-plus'

const createRiskAssessment = preventionApi.createRiskAssessment as unknown as Mock
const getRiskAssessments = preventionApi.getRiskAssessments as unknown as Mock
const createProfile = preventionApi.createProfile as unknown as Mock
const updateProfile = preventionApi.updateProfile as unknown as Mock
const getProfile = preventionApi.getProfile as unknown as Mock
const getCompleteness = preventionApi.getCompleteness as unknown as Mock
const ElMessageMock = ElMessage as unknown as Mock &
  Record<'error' | 'success' | 'warning' | 'info', Mock>

/**
 * EP stub 集合。`el-form` 的 `validate` 只做 required 判定（EP 默认语义的子集），
 * 其余控件保持轻量，`el-button` 不 emit click —— 父级的 @click 作为原生监听器落到
 * 根 button 上，emit 会让处理器跑两次（13-06 记录的坑）。
 */
const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
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
  'el-pagination': {
    template: '<div class="el-pagination" />',
    props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout']
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
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
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /><slot /></div>',
    props: ['type', 'closable', 'showIcon']
  },
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    props: ['model', 'rules', 'labelWidth', 'hideRequiredAsterisk'],
    methods: {
      validate(cb: (valid: boolean, invalidFields?: Record<string, unknown[]>) => void) {
        // stub 的 this 无类型声明，显式取形状，避免 any 泄漏到断言里。
        const self = this as unknown as {
          rules?: Record<string, unknown>
          model?: Record<string, unknown>
        }
        const invalidFields: Record<string, Array<{ message?: string }>> = {}
        const rules = self.rules ?? {}
        for (const [field, fieldRules] of Object.entries(rules)) {
          const list = Array.isArray(fieldRules) ? fieldRules : [fieldRules]
          for (const rule of list as Array<{ required?: boolean; message?: string }>) {
            if (!rule?.required) continue
            const value = self.model?.[field]
            const empty = value === undefined || value === null || value === ''
            if (empty) {
              invalidFields[field] = [{ message: rule.message }]
              break
            }
          }
        }
        const valid = Object.keys(invalidFields).length === 0
        cb(valid, invalidFields)
        return Promise.resolve(valid)
      },
      validateField: vi.fn(),
      clearValidate: vi.fn(),
      resetFields: vi.fn()
    }
  },
  // 内联错误必须真的渲染到 DOM，测试才能证明 :error 绑对了 prop。
  'el-form-item': {
    template:
      '<div class="el-form-item"><label>{{ label }}</label><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['label', 'prop', 'error']
  },
  // gutter 必须落到 DOM：网格间距契约（16，不是 EP 默认 20）靠它断言。
  'el-row': {
    template: '<div class="el-row" :gutter="gutter"><slot /></div>',
    props: ['gutter']
  },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-radio-group': {
    template: '<div class="el-radio-group"><slot /></div>',
    props: ['modelValue']
  },
  // 渲染真实 input：focusField 的 querySelector('input') 必须命中一个可聚焦控件。
  'el-radio': {
    template: '<label class="el-radio"><input type="radio" :value="value" /><slot /></label>',
    props: ['value', 'label']
  },
  'el-date-picker': {
    template: '<input class="el-date-picker" :placeholder="placeholder" />',
    props: ['modelValue', 'type', 'placeholder', 'valueFormat', 'disabledDate']
  },
  'el-input-number': {
    template: '<input class="el-input-number" :placeholder="placeholder" :disabled="disabled" />',
    props: ['modelValue', 'min', 'max', 'precision', 'placeholder', 'disabled']
  },
  'el-button': {
    template:
      '<button class="el-button" :class="{ \'el-button--primary\': type === \'primary\' }" type="button" :disabled="disabled" :data-loading="loading ? \'true\' : \'false\'"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'text', 'link', 'size', 'plain']
  }
}

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

function makeCompleteness(options: {
  score: number
  assumptions: string[]
  hasProfile?: boolean
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
    hasProfile: options.hasProfile ?? true,
    assumptions: options.assumptions,
    fields
  }
}

/** 无档案用户：一条通用句 + 0%。 */
const NO_PROFILE_PAYLOAD = makeCompleteness({
  score: 0,
  hasProfile: false,
  assumptions: ['未填写健康档案，风险得分按通用参考值估算']
})

/** 有档案但缺出生日期与 BMI：两条假设，60%。 */
const TWO_ASSUMPTIONS = [
  '缺少出生日期，年龄按 35 岁估算',
  '缺少 BMI，按 22.0 估算'
]
const PARTIAL_PAYLOAD = makeCompleteness({
  score: 60,
  assumptions: TWO_ASSUMPTIONS,
  filledKeys: ['gender']
})

const NOT_FOUND = {
  response: { status: 404, data: { code: 404, message: '健康档案不存在' } }
}

function mountRisk() {
  return mount(Risk, {
    // attachTo 是必须的：面板打开时用 document.querySelector('.risk-estimate') 滚动定位，
    // 未挂载到文档的 wrapper 会让那次调用变成空操作。
    attachTo: document.body,
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

/** 四通道 toast 统计：对象式调用 + 函数式旧 API。 */
function toastCount(): number {
  return (
    ElMessageMock.mock.calls.length +
    ElMessageMock.error.mock.calls.length +
    ElMessageMock.success.mock.calls.length +
    ElMessageMock.warning.mock.calls.length +
    ElMessageMock.info.mock.calls.length
  )
}

/** 顶部 创建评估 按钮（header-actions 里的唯一 EP 按钮）。 */
function createButton(wrapper: ReturnType<typeof mountRisk>) {
  return wrapper.find('.header-actions .el-button')
}

/** 面板动作按钮：0 = 保存档案并创建，1 = 直接创建（按估算），2 = 放弃创建。 */
function panelButtons(wrapper: ReturnType<typeof mountRisk>) {
  return wrapper.findAll('.risk-estimate__actions .el-button')
}

describe('RiskBackfill.spec.ts — 风险页就地补填（PRF-06 / PRF-08）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    routerPush.mockReset()
    getRiskAssessments.mockResolvedValue(ok([]))
    createRiskAssessment.mockResolvedValue(ok([]))
    createProfile.mockResolvedValue(ok({ id: 9 }))
    updateProfile.mockResolvedValue(ok({ id: 7 }))
    getProfile.mockRejectedValue(NOT_FOUND)
    getCompleteness.mockResolvedValue(ok(PARTIAL_PAYLOAD))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  describe('Task 1 · 首次访问与三序判定', () => {
    it('fetches the completeness payload on mount, before any click', async () => {
      const wrapper = mountRisk()
      await flushPromises()

      expect(getCompleteness).toHaveBeenCalledTimes(1)
      expect(getCompleteness).toHaveBeenCalledWith({ silent: true })
      // 档案行同样静默读取：404 是正常的引导状态，不得弹红色提示（D-09）。
      expect(getProfile).toHaveBeenCalledWith({ silent: true })

      wrapper.unmount()
    })

    it('opens the panel and creates nothing when assumptions exist and score is below 100', async () => {
      const wrapper = mountRisk()
      await flushPromises()

      await createButton(wrapper).trigger('click')
      await flushPromises()

      expect(wrapper.find('.risk-estimate').exists()).toBe(true)
      expect(createRiskAssessment).not.toHaveBeenCalled()

      wrapper.unmount()
    })

    it('creates immediately and opens no panel when the assumption list is empty', async () => {
      getCompleteness.mockResolvedValue(
        ok(makeCompleteness({ score: 100, assumptions: [], filledKeys: PROFILE_FIELDS.map(f => f.key) }))
      )
      const wrapper = mountRisk()
      await flushPromises()

      await createButton(wrapper).trigger('click')
      await flushPromises()

      expect(wrapper.find('.risk-estimate').exists()).toBe(false)
      expect(createRiskAssessment).toHaveBeenCalledTimes(1)

      wrapper.unmount()
    })

    it('waits for the in-flight mount fetch instead of creating while the store is still empty', async () => {
      let resolveCompleteness: (value: unknown) => void = () => {}
      getCompleteness.mockReturnValue(
        new Promise(resolve => {
          resolveCompleteness = resolve
        })
      )

      const wrapper = mountRisk()
      // 刻意不 flush：挂载请求仍未落定。

      await createButton(wrapper).trigger('click')
      await flushPromises()

      // 这是本 spec 的核心断言：若点击不 await 单飞 promise，它会立刻读到 null 并创建。
      expect(createRiskAssessment).not.toHaveBeenCalled()
      expect(wrapper.find('.risk-estimate').exists()).toBe(false)

      resolveCompleteness(ok(PARTIAL_PAYLOAD))
      await flushPromises()

      expect(wrapper.find('.risk-estimate').exists()).toBe(true)
      expect(createRiskAssessment).not.toHaveBeenCalled()

      wrapper.unmount()
    })

    it('creates exactly once when the deferred payload turns out to have no assumptions', async () => {
      let resolveCompleteness: (value: unknown) => void = () => {}
      getCompleteness.mockReturnValue(
        new Promise(resolve => {
          resolveCompleteness = resolve
        })
      )

      const wrapper = mountRisk()

      await createButton(wrapper).trigger('click')
      await flushPromises()
      expect(createRiskAssessment).not.toHaveBeenCalled()

      resolveCompleteness(ok(makeCompleteness({ score: 30, assumptions: [] })))
      await flushPromises()

      // await 只推迟创建，不阻断创建。
      expect(createRiskAssessment).toHaveBeenCalledTimes(1)
      expect(wrapper.find('.risk-estimate').exists()).toBe(false)

      wrapper.unmount()
    })

    it('renders one li per backend assumption with the exact sentence', async () => {
      const wrapper = mountRisk()
      await flushPromises()

      await createButton(wrapper).trigger('click')
      await flushPromises()

      const items = wrapper.findAll('.risk-estimate__list li')
      expect(items).toHaveLength(TWO_ASSUMPTIONS.length)
      expect(items.map(item => item.text())).toEqual(TWO_ASSUMPTIONS)

      wrapper.unmount()
    })

    it('renders exactly one li for a no-profile payload — never three per-field sentences', async () => {
      getCompleteness.mockResolvedValue(ok(NO_PROFILE_PAYLOAD))
      const wrapper = mountRisk()
      await flushPromises()

      await createButton(wrapper).trigger('click')
      await flushPromises()

      const items = wrapper.findAll('.risk-estimate__list li')
      expect(items).toHaveLength(1)
      expect(items[0].text()).toBe('未填写健康档案，风险得分按通用参考值估算')
      // 逐字段拼接会产出这三句，而引擎对空档案只代入一句通用句。
      expect(wrapper.find('.risk-estimate__list').text()).not.toContain('35 岁')
      expect(wrapper.find('.risk-estimate__list').text()).not.toContain('22.0')

      wrapper.unmount()
    })

    it('scrolls the panel into view and focuses the gender control on open', async () => {
      const scrollSpy = vi.spyOn(HTMLElement.prototype, 'scrollIntoView')
      const wrapper = mountRisk()
      await flushPromises()

      await createButton(wrapper).trigger('click')
      await flushPromises()

      expect(scrollSpy).toHaveBeenCalledWith({ block: 'center' })
      const active = document.activeElement as HTMLInputElement | null
      expect(active).not.toBeNull()
      expect(active?.getAttribute('value')).toBe('male')

      scrollSpy.mockRestore()
      wrapper.unmount()
    })

    it('creates once and closes the panel on 直接创建（按估算）', async () => {
      const wrapper = mountRisk()
      await flushPromises()

      await createButton(wrapper).trigger('click')
      await flushPromises()
      expect(wrapper.find('.risk-estimate').exists()).toBe(true)

      await panelButtons(wrapper)[1].trigger('click')
      await flushPromises()

      expect(createRiskAssessment).toHaveBeenCalledTimes(1)
      expect(wrapper.find('.risk-estimate').exists()).toBe(false)

      wrapper.unmount()
    })

    it('closes the panel on 放弃创建 with zero create call and zero toast', async () => {
      const wrapper = mountRisk()
      await flushPromises()

      await createButton(wrapper).trigger('click')
      await flushPromises()
      ElMessageMock.mockClear()
      ElMessageMock.success.mockClear()

      await panelButtons(wrapper)[2].trigger('click')
      await flushPromises()

      expect(createRiskAssessment).not.toHaveBeenCalled()
      expect(wrapper.find('.risk-estimate').exists()).toBe(false)
      // 用户主动放弃不是失败（Phase 11 D-12）：不得有任何 toast。
      expect(toastCount()).toBe(0)

      wrapper.unmount()
    })

    it('renders the optional 身高/体重 inputs with no disabled state and no validation rule', async () => {
      const wrapper = mountRisk()
      await flushPromises()

      await createButton(wrapper).trigger('click')
      await flushPromises()

      const labels = wrapper.findAll('.risk-estimate .el-form-item label').map(node => node.text())
      expect(labels).toContain('身高（可选）')
      expect(labels).toContain('体重（可选）')

      const inputs = wrapper.findAll('.risk-estimate .el-input-number')
      expect(inputs).toHaveLength(2)
      for (const input of inputs) {
        expect(input.attributes('disabled')).toBeUndefined()
        expect(input.attributes('placeholder')).toBe('可不填')
      }

      const vm = wrapper.vm as any
      expect(vm.backfillRules.heightCm).toBeUndefined()
      expect(vm.backfillRules.weightKg).toBeUndefined()

      // 网格间距契约：16，不是 EP 默认的 20。
      expect(wrapper.findAll('.risk-estimate .el-row').map(row => row.attributes('gutter'))).toEqual(
        ['16', '16']
      )

      wrapper.unmount()
    })
  })

  describe('Task 2 · 保存路径：校验、写入、假设刷新与失败保留', () => {
    /** 打开面板并返回 wrapper（三条假设路径已由 Task 1 证明）。 */
    async function openPanel() {
      const wrapper = mountRisk()
      await flushPromises()
      await createButton(wrapper).trigger('click')
      await flushPromises()
      expect(wrapper.find('.risk-estimate').exists()).toBe(true)
      return wrapper
    }

    function saveButton(wrapper: ReturnType<typeof mountRisk>) {
      return panelButtons(wrapper)[0]
    }

    it('surfaces two inline errors plus a two-item summary and saves nothing on an empty form', async () => {
      const wrapper = await openPanel()

      await saveButton(wrapper).trigger('click')
      await flushPromises()

      expect(wrapper.findAll('.risk-estimate .el-form-item__error').map(n => n.text())).toEqual([
        '请选择性别',
        '请选择出生日期'
      ])
      const summary = wrapper.find('.risk-estimate .form-error-summary')
      expect(summary.exists()).toBe(true)
      expect(summary.text()).toContain('请修正以下 2 项填写问题')
      expect(summary.findAll('li')).toHaveLength(2)

      expect(createProfile).not.toHaveBeenCalled()
      expect(updateProfile).not.toHaveBeenCalled()
      expect(createRiskAssessment).not.toHaveBeenCalled()

      wrapper.unmount()
    })

    it('saves the two required values, refetches completeness, then creates — in that order', async () => {
      const wrapper = await openPanel()
      const vm = wrapper.vm as any
      vm.backfill.gender = 'female'
      vm.backfill.birthDate = '1992-03-04'

      await saveButton(wrapper).trigger('click')
      await flushPromises()

      expect(createProfile).toHaveBeenCalledTimes(1)
      const payload = createProfile.mock.calls[0][0]
      expect(payload.gender).toBe('female')
      expect(payload.birthDate).toBe('1992-03-04')

      // 挂载时一次 + 保存后一次
      expect(getCompleteness).toHaveBeenCalledTimes(2)
      expect(createRiskAssessment).toHaveBeenCalledTimes(1)

      // 顺序：写档案 → 重取完整度 → 创建评估。顺序颠倒会让面板承诺过期假设。
      const writeOrder = createProfile.mock.invocationCallOrder[0]
      const refetchOrder = getCompleteness.mock.invocationCallOrder[1]
      const createOrder = createRiskAssessment.mock.invocationCallOrder[0]
      expect(writeOrder).toBeLessThan(refetchOrder)
      expect(refetchOrder).toBeLessThan(createOrder)

      expect(wrapper.find('.risk-estimate').exists()).toBe(false)

      wrapper.unmount()
    })

    it('updates the existing profile by id instead of creating a second one', async () => {
      getProfile.mockResolvedValue(
        ok({
          id: 42,
          userId: 1,
          gender: 'male',
          birthDate: '1990-01-01',
          bloodType: 'A',
          createTime: '2026-01-01 00:00:00',
          updateTime: '2026-01-01 00:00:00'
        })
      )
      const wrapper = await openPanel()
      const vm = wrapper.vm as any
      vm.backfill.gender = 'male'
      vm.backfill.birthDate = '1990-01-01'

      await saveButton(wrapper).trigger('click')
      await flushPromises()

      expect(updateProfile).toHaveBeenCalledTimes(1)
      expect(updateProfile.mock.calls[0][0]).toBe(42)
      expect(createProfile).not.toHaveBeenCalled()
      expect(createRiskAssessment).toHaveBeenCalledTimes(1)

      wrapper.unmount()
    })

    it('sends typed 身高/体重 alongside the required fields', async () => {
      const wrapper = await openPanel()
      const vm = wrapper.vm as any
      vm.backfill.gender = 'male'
      vm.backfill.birthDate = '1988-05-06'
      vm.backfill.heightCm = 178
      vm.backfill.weightKg = 72.5

      await saveButton(wrapper).trigger('click')
      await flushPromises()

      const payload = createProfile.mock.calls[0][0]
      expect(payload.heightCm).toBe(178)
      expect(payload.weightKg).toBe(72.5)
      // 前端绝不计算 bmi（D-01）：派生值由后端 toEntity 负责。
      expect(payload.bmi).toBeUndefined()

      wrapper.unmount()
    })

    it('still saves and still creates when both optional inputs are left blank', async () => {
      const wrapper = await openPanel()
      const vm = wrapper.vm as any
      vm.backfill.gender = 'male'
      vm.backfill.birthDate = '1988-05-06'

      await saveButton(wrapper).trigger('click')
      await flushPromises()

      // 可选字段没有规则 → 不得产生任何内联错误
      expect(wrapper.findAll('.risk-estimate .el-form-item__error')).toHaveLength(0)
      expect(createProfile).toHaveBeenCalledTimes(1)
      expect(createRiskAssessment).toHaveBeenCalledTimes(1)

      const payload = createProfile.mock.calls[0][0]
      expect(payload.heightCm).toBeUndefined()
      expect(payload.weightKg).toBeUndefined()

      wrapper.unmount()
    })

    it('falls back to the stored profile height/weight when the prefill never ran', async () => {
      // 真实竞态：用户点得比 getProfile 快，面板打开时 profileData 仍是 null，
      // 因此 backfill 的两个可选值保持空 —— 提交时不得把已存值抹成空。
      let resolveProfile: (value: unknown) => void = () => {}
      getProfile.mockReturnValue(
        new Promise(resolve => {
          resolveProfile = resolve
        })
      )

      const wrapper = await openPanel()
      expect((wrapper.vm as any).backfill.heightCm).toBeNull()

      resolveProfile(
        ok({
          id: 11,
          userId: 1,
          heightCm: 170,
          weightKg: 65,
          createTime: '2026-01-01 00:00:00',
          updateTime: '2026-01-01 00:00:00'
        })
      )
      await flushPromises()

      const vm = wrapper.vm as any
      vm.backfill.gender = 'male'
      vm.backfill.birthDate = '1990-01-01'

      await saveButton(wrapper).trigger('click')
      await flushPromises()

      const payload = updateProfile.mock.calls[0][1]
      expect(payload.heightCm).toBe(170)
      expect(payload.weightKg).toBe(65)

      wrapper.unmount()
    })

    it('keeps both typed values, keeps the panel open and shows no toast on a 400 save failure', async () => {
      const wrapper = await openPanel()
      const vm = wrapper.vm as any
      vm.backfill.gender = 'male'
      vm.backfill.birthDate = '1990-01-01'
      vm.backfill.heightCm = 40

      createProfile.mockRejectedValueOnce({
        response: {
          status: 400,
          data: {
            code: 400,
            message: '参数校验失败',
            errorCode: 'validation.invalid_parameter',
            fieldErrors: [{ field: 'heightCm', message: '身高不能低于 50 厘米' }]
          }
        }
      })

      await saveButton(wrapper).trigger('click')
      await flushPromises()

      // validation → 0 toast（GOV.UK 双呈现：内联 + 汇总）
      expect(toastCount()).toBe(0)
      expect(wrapper.find('.risk-estimate').exists()).toBe(true)
      expect(wrapper.findAll('.risk-estimate .el-form-item__error').map(n => n.text())).toEqual([
        '身高不能低于 50 厘米'
      ])
      expect(wrapper.find('.risk-estimate .form-error-summary').exists()).toBe(true)

      // 输入保留（D-12）：失败后不得清空用户已填的值
      expect(vm.backfill.gender).toBe('male')
      expect(vm.backfill.birthDate).toBe('1990-01-01')
      expect(vm.backfill.heightCm).toBe(40)

      expect(createRiskAssessment).not.toHaveBeenCalled()

      wrapper.unmount()
    })

    it('shows exactly one toast on a 500 save failure and still keeps the panel and the input', async () => {
      const wrapper = await openPanel()
      const vm = wrapper.vm as any
      vm.backfill.gender = 'female'
      vm.backfill.birthDate = '1994-07-08'

      createProfile.mockRejectedValueOnce({
        response: { status: 500, data: { message: '服务异常' } }
      })

      await saveButton(wrapper).trigger('click')
      await flushPromises()

      expect(toastCount()).toBe(1)
      expect(wrapper.find('.risk-estimate').exists()).toBe(true)
      expect(vm.backfill.gender).toBe('female')
      expect(vm.backfill.birthDate).toBe('1994-07-08')
      expect(createRiskAssessment).not.toHaveBeenCalled()

      wrapper.unmount()
    })

    it('marks only the primary action as loading while the save is in flight', async () => {
      const wrapper = await openPanel()
      const vm = wrapper.vm as any
      vm.backfill.gender = 'male'
      vm.backfill.birthDate = '1990-01-01'

      let resolveSave: (value: unknown) => void = () => {}
      createProfile.mockReturnValueOnce(
        new Promise(resolve => {
          resolveSave = resolve
        })
      )

      await saveButton(wrapper).trigger('click')
      await nextTick()

      const buttons = panelButtons(wrapper)
      expect(buttons[0].attributes('data-loading')).toBe('true')
      // 另外两个动作不得因为保存中而禁用：用户仍可选择直接创建或放弃。
      expect(buttons[1].attributes('disabled')).toBeUndefined()
      expect(buttons[2].attributes('disabled')).toBeUndefined()

      resolveSave(ok({ id: 9 }))
      await flushPromises()

      expect(panelButtons(wrapper)).toHaveLength(0)

      wrapper.unmount()
    })
  })

  describe('Task 3 · 估算徽标与详情对话框的假设列表', () => {
    function badge(wrapper: ReturnType<typeof mountRisk>) {
      return wrapper.find('.hhs-estimate-badge')
    }

    it('renders the badge on a first-visit mount with no interaction at all', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 40, assumptions: TWO_ASSUMPTIONS })))
      const wrapper = mountRisk()
      await flushPromises()

      // 这条断言证明的是挂载拉取喂给了徽标：没有任何点击，也没有任何列表行。
      expect(badge(wrapper).exists()).toBe(true)
      expect(badge(wrapper).text()).toContain('基于不完整档案估算')

      wrapper.unmount()
    })

    it('renders the badge at score 40 with no completed assessments', async () => {
      getRiskAssessments.mockResolvedValue(ok([]))
      getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 40, assumptions: TWO_ASSUMPTIONS })))
      const wrapper = mountRisk()
      await flushPromises()

      expect(badge(wrapper).exists()).toBe(true)

      wrapper.unmount()
    })

    it('hides the badge while the panel is open — one prompt at a time', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 40, assumptions: TWO_ASSUMPTIONS })))
      const wrapper = mountRisk()
      await flushPromises()
      expect(badge(wrapper).exists()).toBe(true)

      await createButton(wrapper).trigger('click')
      await flushPromises()

      expect(wrapper.find('.risk-estimate').exists()).toBe(true)
      expect(badge(wrapper).exists()).toBe(false)

      wrapper.unmount()
    })

    it('renders no badge when the completeness load failed (never a fabricated 0%)', async () => {
      getCompleteness.mockRejectedValue({
        response: { status: 500, data: { code: 500, message: '服务异常' } }
      })
      const wrapper = mountRisk()
      await flushPromises()

      expect(badge(wrapper).exists()).toBe(false)
      expect(wrapper.text()).not.toContain('基于不完整档案估算')

      wrapper.unmount()
    })

    it('renders no badge at score 100', async () => {
      getCompleteness.mockResolvedValue(
        ok(makeCompleteness({ score: 100, assumptions: [], filledKeys: PROFILE_FIELDS.map(f => f.key) }))
      )
      const wrapper = mountRisk()
      await flushPromises()

      expect(badge(wrapper).exists()).toBe(false)

      wrapper.unmount()
    })

    it('pushes to the profile page with query.focus set to the first missing key', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 40, assumptions: TWO_ASSUMPTIONS })))
      const wrapper = mountRisk()
      await flushPromises()

      await badge(wrapper).trigger('click')

      expect(routerPush).toHaveBeenCalledTimes(1)
      expect(routerPush).toHaveBeenCalledWith({
        path: '/prevention/profile',
        query: { focus: 'gender' }
      })

      wrapper.unmount()
    })

    it('never renders the badge inside the table — the risk-level tag keeps its own region', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 40, assumptions: TWO_ASSUMPTIONS })))
      const wrapper = mountRisk()
      await flushPromises()

      expect(badge(wrapper).exists()).toBe(true)
      expect(wrapper.find('.el-table .hhs-estimate-badge').exists()).toBe(false)
      // 徽标落在表格之前的披露块里
      expect(wrapper.find('.risk-disclosure .hhs-estimate-badge').exists()).toBe(true)

      wrapper.unmount()
    })

    it('lists the read-time assumptions in the detail dialog, one li per sentence', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 40, assumptions: TWO_ASSUMPTIONS })))
      const wrapper = mountRisk()
      await flushPromises()

      ;(wrapper.vm as any).viewDetail({
        id: 1,
        userId: 1,
        diseaseName: '高血压',
        riskLevel: 'MEDIUM',
        riskScore: 55,
        suggestion: '规律作息',
        profileCompleteness: 40,
        assumptions: TWO_ASSUMPTIONS,
        createTime: '2026-11-09 10:00:00'
      })
      await flushPromises()

      const block = wrapper.find('.risk-dialog-assumptions')
      expect(block.exists()).toBe(true)
      expect(block.find('.risk-dialog-assumptions__title').text()).toBe('本次评估采用的假设')
      expect(block.findAll('li').map(node => node.text())).toEqual(TWO_ASSUMPTIONS)
      // 读时重算：标题不得声称是创建时冻结的记录
      expect(block.text()).not.toContain('创建时')

      wrapper.unmount()
    })

    it('renders no dialog assumption list for an empty assumptions array', async () => {
      const wrapper = mountRisk()
      await flushPromises()

      ;(wrapper.vm as any).viewDetail({
        id: 2,
        userId: 1,
        diseaseName: '糖尿病',
        riskLevel: 'LOW',
        riskScore: 20,
        suggestion: '保持',
        profileCompleteness: 100,
        assumptions: [],
        createTime: '2026-11-09 10:00:00'
      })
      await flushPromises()

      expect(wrapper.find('.risk-dialog-assumptions').exists()).toBe(false)

      wrapper.unmount()
    })

    it('renders identical text in the dialog and the panel for the same payload', async () => {
      getCompleteness.mockResolvedValue(ok(NO_PROFILE_PAYLOAD))
      const wrapper = mountRisk()
      await flushPromises()

      // 无档案：两边都必须只有那一条通用句，逐字相同。
      await createButton(wrapper).trigger('click')
      await flushPromises()
      const panelText = wrapper.findAll('.risk-estimate__list li').map(node => node.text())

      ;(wrapper.vm as any).viewDetail({
        id: 3,
        userId: 1,
        diseaseName: '冠心病',
        riskLevel: 'HIGH',
        riskScore: 70,
        suggestion: '就医',
        profileCompleteness: 0,
        assumptions: NO_PROFILE_PAYLOAD.assumptions,
        createTime: '2026-11-09 10:00:00'
      })
      await flushPromises()

      const dialogText = wrapper
        .findAll('.risk-dialog-assumptions li')
        .map(node => node.text())
      expect(dialogText).toEqual(panelText)
      expect(dialogText).toEqual(['未填写健康档案，风险得分按通用参考值估算'])

      wrapper.unmount()
    })
  })
})
