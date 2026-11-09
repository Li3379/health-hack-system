import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
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
})
