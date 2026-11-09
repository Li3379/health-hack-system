import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'
import type { ProfileCompletenessVO, ProfileFieldStatusVO } from '@/types/api'
import { PROFILE_FIELDS } from '@/utils/profileFields'

/**
 * 档案页完整度引导的行为证明（不是 grep 证明）：
 * 头部块 + 9 条后端来源的字段说明 + 单条不填承诺 + 保存后重新推导完整度。
 */

vi.mock('@/api/prevention', () => ({
  preventionApi: {
    getProfile: vi.fn(),
    getCompleteness: vi.fn(),
    createProfile: vi.fn(),
    updateProfile: vi.fn()
  }
}))

// useRoute 是 inject 实现（RESEARCH Pitfall 2），所以必须整体 mock vue-router。
const routerReplace = vi.fn()
const routerPush = vi.fn()
let routeQuery: Record<string, string> = {}

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery }),
  useRouter: () => ({ replace: routerReplace, push: routerPush })
}))

// 部分 mock：保住组件导出，只替换 ElMessage。
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

import Profile from '@/views/prevention/Profile.vue'
import { preventionApi } from '@/api/prevention'

const getProfile = preventionApi.getProfile as unknown as Mock
const getCompleteness = preventionApi.getCompleteness as unknown as Mock
const updateProfile = preventionApi.updateProfile as unknown as Mock

/**
 * el-row 把 gutter 渲染成 data-gutter，才能证明「每一行都是 16」——
 * 声明了 props 却不渲染的 stub 只能证明「传了」，无法证明「渲染出来是什么」。
 */
const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-row': {
    template: '<div class="el-row" :data-gutter="gutter"><slot /></div>',
    props: ['gutter']
  },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    props: ['model', 'rules', 'labelWidth', 'labelPosition', 'hideRequiredAsterisk', 'disabled'],
    methods: {
      validate: (cb: (valid: boolean) => void) => cb(true),
      validateField: vi.fn(),
      clearValidate: vi.fn()
    }
  },
  'el-form-item': {
    template:
      '<div class="el-form-item"><label>{{ label }}</label><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['label', 'prop', 'error']
  },
  'el-radio-group': {
    template: '<div class="el-radio-group"><slot /></div>',
    props: ['modelValue']
  },
  'el-radio': {
    template: '<label class="el-radio"><slot /></label>',
    props: ['value', 'label']
  },
  'el-select': {
    template: '<select class="el-select"><slot /></select>',
    props: ['modelValue', 'placeholder', 'clearable', 'disabled']
  },
  'el-option': {
    template: '<option class="el-option" :value="value">{{ label }}</option>',
    props: ['label', 'value']
  },
  'el-input-number': {
    template: '<input class="el-input-number" />',
    props: ['modelValue', 'min', 'max', 'precision', 'disabled', 'controls']
  },
  'el-input': {
    template: '<input class="el-input" />',
    props: ['modelValue', 'value', 'disabled', 'placeholder', 'readonly', 'type', 'rows']
  },
  'el-date-picker': {
    template: '<input class="el-date-picker" />',
    props: ['modelValue', 'type', 'placeholder', 'format', 'valueFormat', 'disabledDate']
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>',
    props: ['type', 'size', 'effect']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'link']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /><slot /></div>',
    props: ['type', 'closable', 'showIcon']
  },
  // 断言它收到的 props，而不是它内部怎么画（13-06 已单独测过）。
  ProfileCompleteness: {
    name: 'ProfileCompleteness',
    props: ['completeness', 'variant'],
    template: '<div class="stub-completeness">{{ completeness.score }}%</div>'
  }
}

const PROFILE_RES = {
  code: 200,
  message: 'success',
  data: {
    id: 7,
    userId: 1,
    gender: 'male',
    birthDate: '1990-01-01',
    heightCm: 170,
    weightKg: 65,
    bloodType: 'A',
    allergyHistory: '',
    familyHistory: '',
    lifestyleHabits: '',
    createTime: '2026-01-01 00:00:00',
    updateTime: '2026-01-01 00:00:00'
  }
}

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

/**
 * 后端形状的 9 项 payload。reason 用哨兵串（REASON-<key>），
 * 这样「DOM 里的说明文字来自 payload」可以被直接证明 —— 若模板硬编码了文案，
 * 哨兵串不会出现在 DOM 里。
 */
function makeCompleteness(options: {
  score?: number
  level?: string
  filledKeys?: string[]
}): ProfileCompletenessVO {
  const filledKeys = options.filledKeys ?? []
  const fields: ProfileFieldStatusVO[] = PROFILE_FIELDS.map(field => ({
    ...field,
    filled: filledKeys.includes(field.key),
    reason: `REASON-${field.key}`
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

function mountProfile() {
  return mount(Profile, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

describe('ProfileCompleteness.spec.ts — 档案页完整度引导', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    routeQuery = {}
    getProfile.mockResolvedValue(PROFILE_RES)
    updateProfile.mockResolvedValue({ code: 200, message: 'success', data: {} })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  describe('完整度头部与字段说明', () => {
    it('renders exactly nine field hints, one per payload entry', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      expect(wrapper.findAll('.field-hint')).toHaveLength(9)
      wrapper.unmount()
    })

    it('sources every hint text from the payload reason, never from a local constant', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      const heightHint = wrapper.find('[data-field="heightCm"] .field-hint')
      expect(heightHint.exists()).toBe(true)
      expect(heightHint.text()).toContain('REASON-heightCm')

      // 九个哨兵串各自出现在自己的字段下 —— 任何一条被硬编码就会在这里断掉。
      for (const field of PROFILE_FIELDS) {
        const hint = wrapper.find(`[data-field="${field.key}"] .field-hint`)
        expect(hint.text()).toContain(`REASON-${field.key}`)
      }
      wrapper.unmount()
    })

    it('maps each field importance to its badge class and Chinese label', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      const genderBadge = wrapper.find('[data-field="gender"] .field-hint .hhs-imp-badge')
      expect(genderBadge.classes()).toContain('hhs-imp-badge--required')
      expect(genderBadge.text()).toBe('必需')

      const birthBadge = wrapper.find('[data-field="birthDate"] .field-hint .hhs-imp-badge')
      expect(birthBadge.classes()).toContain('hhs-imp-badge--required')

      const heightBadge = wrapper.find('[data-field="heightCm"] .field-hint .hhs-imp-badge')
      expect(heightBadge.classes()).toContain('hhs-imp-badge--recommended')
      expect(heightBadge.text()).toBe('推荐')

      const bloodBadge = wrapper.find('[data-field="bloodType"] .field-hint .hhs-imp-badge')
      expect(bloodBadge.classes()).toContain('hhs-imp-badge--optional')
      expect(bloodBadge.text()).toBe('可选')

      wrapper.unmount()
    })

    it('renders the promise sentence exactly once, right after the completeness block', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      const promises = wrapper.findAll('.profile-promise')
      expect(promises).toHaveLength(1)
      expect(promises[0].text()).toBe('推荐与可选字段不填也可保存，不影响核心功能使用。')

      // 阅读顺序：完整度块 → 承诺句 → 表单
      const html = wrapper.html()
      expect(html.indexOf('stub-completeness')).toBeLessThan(html.indexOf('profile-promise'))
      expect(html.indexOf('profile-promise')).toBeLessThan(html.indexOf('el-form'))
      wrapper.unmount()
    })

    it('uses gutter 16 on every row, never 20', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      const rows = wrapper.findAll('.el-row')
      expect(rows).toHaveLength(3)
      expect(rows.map(row => row.attributes('data-gutter'))).toEqual(['16', '16', '16'])
      wrapper.unmount()
    })

    it('keeps the BMI item always rendered and shows the placeholder instead of classifying it', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      // 无身高体重 → BMI 不可计算，但表单项与说明行仍在
      const bmiItem = wrapper.find('[data-field="bmi"]')
      expect(bmiItem.exists()).toBe(true)
      expect(bmiItem.find('.field-hint').text()).toContain('REASON-bmi')
      expect(bmiItem.find('.el-tag').exists()).toBe(false)
      wrapper.unmount()
    })

    it('renders no completeness block at all before the payload arrives', async () => {
      getCompleteness.mockReturnValue(new Promise(() => {}))
      const wrapper = mountProfile()
      await flushPromises()

      expect(wrapper.find('.stub-completeness').exists()).toBe(false)
      expect(wrapper.findAll('.field-hint')).toHaveLength(0)
      wrapper.unmount()
    })
  })

  describe('编辑态按钮文案', () => {
    it('labels the secondary edit-mode button 放弃编辑, with no bare single-word label', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      const vm = wrapper.vm as unknown as { editing: boolean }
      vm.editing = true
      await flushPromises()

      const labels = wrapper.findAll('.header-actions .el-button').map(node => node.text())
      expect(labels).toContain('放弃编辑')
      for (const label of labels) {
        expect(['取消', '确定', '关闭']).not.toContain(label)
      }
      wrapper.unmount()
    })
  })

  describe('保存后重新推导完整度', () => {    it('refetches completeness after a successful save so the header moves 40 → 70', async () => {
      getCompleteness.mockResolvedValueOnce(ok(makeCompleteness({ score: 40 })))
      getCompleteness.mockResolvedValueOnce(
        ok(
          makeCompleteness({
            score: 70,
            level: '标准档案',
            filledKeys: ['gender', 'birthDate', 'heightCm']
          })
        )
      )

      const wrapper = mountProfile()
      await flushPromises()
      expect(wrapper.find('.stub-completeness').text()).toBe('40%')

      const vm = wrapper.vm as unknown as {
        editing: boolean
        form: { gender: string; birthDate: string; heightCm?: number }
        handleSubmit: () => Promise<void>
      }
      vm.editing = true
      vm.form.gender = 'male'
      vm.form.birthDate = '1990-01-01'
      vm.form.heightCm = 175

      await vm.handleSubmit()
      await flushPromises()

      // 70 = gender 60/2 (30) + birthDate 60/2 (30) + heightCm 30/3 (10)，D-01 算术可达值
      expect(getCompleteness).toHaveBeenCalledTimes(2)
      expect(wrapper.find('.stub-completeness').text()).toBe('70%')

      const received = wrapper
        .findComponent({ name: 'ProfileCompleteness' })
        .props('completeness') as ProfileCompletenessVO
      expect(received.score).toBe(70)
      expect(received.filledCount).toBe(3)
      const filledKeys = received.fields.filter(field => field.filled).map(field => field.key)
      expect(filledKeys).toEqual(['gender', 'birthDate', 'heightCm'])
      wrapper.unmount()
    })

    it('skips the refetch entirely on a failed save and leaves the previous header intact', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({ score: 40 })))
      updateProfile.mockRejectedValue({
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

      const wrapper = mountProfile()
      await flushPromises()

      const vm = wrapper.vm as unknown as {
        editing: boolean
        form: { gender: string; birthDate: string; heightCm?: number }
        handleSubmit: () => Promise<void>
      }
      vm.editing = true
      vm.form.gender = 'male'
      vm.form.birthDate = '1990-01-01'
      vm.form.heightCm = 175

      await vm.handleSubmit()
      await flushPromises()

      // 一次挂载 + 零次保存后重取：失败保存不得发第二个 getCompleteness
      expect(getCompleteness).toHaveBeenCalledTimes(1)
      expect(wrapper.find('.stub-completeness').text()).toBe('40%')
      wrapper.unmount()
    })
  })

  describe('?focus= 交接', () => {
    it('applies is-focus-target to the queried field when it is still unfilled', async () => {
      routeQuery = { focus: 'heightCm' }
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      expect(wrapper.find('[data-field="heightCm"]').classes()).toContain('is-focus-target')
      // 只有一个字段被标记，不能顺带点亮别的
      expect(wrapper.findAll('.is-focus-target')).toHaveLength(1)
      wrapper.unmount()
    })

    it('opens edit mode before focusing, because a read-only form cannot take focus', async () => {
      routeQuery = { focus: 'heightCm' }
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      // 只读态下 el-form 是 disabled，focus() 为空操作 —— 链接的语义是「来补填」
      const vm = wrapper.vm as unknown as { editing: boolean }
      expect(vm.editing).toBe(true)
      wrapper.unmount()
    })

    it('cleans the query exactly once after a successful focus', async () => {      routeQuery = { focus: 'heightCm' }
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      expect(routerReplace).toHaveBeenCalledTimes(1)
      expect(routerReplace).toHaveBeenCalledWith({ query: {} })
      expect(routerPush).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('ignores a queried key that is already filled', async () => {
      routeQuery = { focus: 'birthDate' }
      getCompleteness.mockResolvedValue(
        ok(makeCompleteness({ filledKeys: ['gender', 'birthDate'] }))
      )
      const wrapper = mountProfile()
      await flushPromises()

      expect(wrapper.find('[data-field="birthDate"]').classes()).not.toContain(
        'is-focus-target'
      )
      expect(wrapper.findAll('.is-focus-target')).toHaveLength(0)
      expect(routerReplace).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('ignores an unknown key without throwing', async () => {
      routeQuery = { focus: 'nonexistentKey' }
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      expect(wrapper.findAll('.is-focus-target')).toHaveLength(0)
      expect(routerReplace).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('does nothing at all when no focus query is present', async () => {
      getCompleteness.mockResolvedValue(ok(makeCompleteness({})))
      const wrapper = mountProfile()
      await flushPromises()

      expect(wrapper.findAll('.is-focus-target')).toHaveLength(0)
      expect(routerReplace).not.toHaveBeenCalled()
      wrapper.unmount()
    })

    it('applies no class and no replace when the completeness load failed', async () => {
      routeQuery = { focus: 'heightCm' }
      getCompleteness.mockRejectedValue({
        response: { status: 500, data: { code: 500, message: '服务异常' } }
      })
      const wrapper = mountProfile()
      await flushPromises()

      // 加载失败 → data 保持 null → 没有可匹配的 fields[]
      expect(wrapper.find('.stub-completeness').exists()).toBe(false)
      expect(wrapper.findAll('.is-focus-target')).toHaveLength(0)
      expect(routerReplace).not.toHaveBeenCalled()
      wrapper.unmount()
    })
  })
})
