import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * 全页表单双呈现的行为证明（不是 grep 证明）：
 * 校验失败 → 内联 + 顶部汇总 + 零 toast，且用户输入保留。
 */

vi.mock('@/api/prevention', () => ({
  preventionApi: {
    getProfile: vi.fn(),
    getCompleteness: vi.fn(),
    createProfile: vi.fn(),
    updateProfile: vi.fn()
  }
}))

// Profile.vue 现在用 useRoute / useRouter 消费 ?focus=；useRoute 是 inject 实现，
// 不 mock 就会 TypeError: Cannot read properties of undefined (reading 'query')。
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() })
}))

// 部分 mock：保住组件导出，只替换 ElMessage，以便对每次调用（含对象式）计数。
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

// Profile.vue 的 onMounted 用 gsap.timeline + DUR/ EASE；形状照抄 PreventionAndWellness.spec.ts。
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
import { ElMessage } from 'element-plus'

const getProfile = preventionApi.getProfile as unknown as Mock
const getCompleteness = preventionApi.getCompleteness as unknown as Mock
const updateProfile = preventionApi.updateProfile as unknown as Mock
const ElMessageMock = ElMessage as unknown as Mock &
  Record<'error' | 'success' | 'warning' | 'info', Mock>

const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-row': {
    template: '<div class="el-row"><slot /></div>',
    props: ['gutter']
  },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: (cb: (valid: boolean) => void) => cb(true),
      validateField: vi.fn(),
      clearValidate: vi.fn()
    }
  },
  // 内联错误必须真的渲染到 DOM，测试才能证明 :error 绑对了 prop。
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
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
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

function mountProfile() {
  return mount(Profile, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

/** 四通道统计：对象式调用（ElMessage(...)）+ 函数式旧 API（ElMessage.error 等）。 */
function toastCount(): number {
  return (
    ElMessageMock.mock.calls.length +
    ElMessageMock.error.mock.calls.length +
    ElMessageMock.success.mock.calls.length +
    ElMessageMock.warning.mock.calls.length +
    ElMessageMock.info.mock.calls.length
  )
}

describe('ProfileErrorSample.spec.ts — 健康档案全页表单双呈现', () => {
  beforeEach(() => {
    // 档案页现在会读完整度 store，需要一个 active pinia（与其余 view spec 同构）。
    setActivePinia(createPinia())
    vi.clearAllMocks()
    getProfile.mockResolvedValue(PROFILE_RES)
    // fields 为空数组：这些测试聚焦错误呈现，说明行数量必须保持 0，
    // 既有的 .el-form-item__error 计数（2）才不会被新增 DOM 干扰。
    // hasProfile / assumptions 必须给全（13-05 的 VO 已要求），否则是类型错误。
    // 用 mockResolvedValue 而非 Once：挂载一次 + 保存后可能的重取都必须有值。
    getCompleteness.mockResolvedValue({
      code: 200,
      message: 'success',
      data: {
        score: 60,
        level: '标准档案',
        filledCount: 2,
        totalCount: 9,
        hasProfile: true,
        assumptions: [],
        fields: []
      }
    })
    updateProfile.mockResolvedValue({ code: 200, message: 'success', data: {} })
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('A. 400 字段校验失败：零 toast、顶部汇总 2 项、中文字段标签、内联错误、值保留', async () => {
    const wrapper = mountProfile()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.editing = true
    vm.form.heightCm = 175
    vm.form.weightKg = 70

    updateProfile.mockRejectedValueOnce({
      response: {
        status: 400,
        data: {
          code: 400,
          message: '参数校验失败',
          errorCode: 'validation.invalid_parameter',
          fieldErrors: [
            { field: 'heightCm', message: '身高不能低于 50 厘米' },
            { field: 'weightKg', message: '体重不能低于 20 千克' }
          ]
        }
      }
    })

    await vm.handleSubmit()
    await flushPromises()

    // 四通道契约：validation → 0 toast（含对象式与函数式全部计入）
    expect(toastCount()).toBe(0)

    const summary = wrapper.find('.form-error-summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('请修正以下 2 项填写问题')

    const items = summary.findAll('li')
    expect(items).toHaveLength(2)
    expect(items[0].text()).toContain('身高：身高不能低于 50 厘米')
    expect(items[1].text()).toContain('体重：体重不能低于 20 千克')

    // 内联：被拒字段的 el-form-item 确实收到 error prop
    const inlineErrors = wrapper.findAll('.el-form-item__error')
    expect(inlineErrors).toHaveLength(2)
    expect(inlineErrors.map(node => node.text())).toEqual([
      '身高不能低于 50 厘米',
      '体重不能低于 20 千克'
    ])

    // 用户已填值保留（不重置表单模型）
    expect(vm.form.heightCm).toBe(175)
    expect(vm.form.weightKg).toBe(70)

    wrapper.unmount()
  })

  it('B. 档案不存在（业务 404）：进入创建模式且零 toast（Pitfall 6）', async () => {
    getProfile.mockRejectedValueOnce({
      response: {
        status: 404,
        data: { code: 404, message: '健康档案不存在' }
      }
    })

    const wrapper = mountProfile()
    await flushPromises()

    const vm = wrapper.vm as any
    expect(vm.editing).toBe(true)
    expect(toastCount()).toBe(0)

    wrapper.unmount()
  })

  it('B2. 档案不存在（真实线上形状：HTTP 200 信封 + 无 errorCode）：仍进入创建模式', async () => {
    // 用例 B 用的是 status:404，但预防保健接口从不返回 HTTP 404 ——
    // PreventionController 用 `Result.failure(404, msg)`，request.ts 把信封包成
    // `{ response: { status: 200, data: envelope } }`，且该重载不设 errorCode。
    // 于是 `err.status === 404` 与 `err.code === '..._not_found'` 双双不成立，
    // 创建模式进不去，只能靠用户手动点「编辑」。本用例锁住真实形状。
    getProfile.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 404, message: '健康档案不存在，请先创建', data: null }
      }
    })

    const wrapper = mountProfile()
    await flushPromises()

    const vm = wrapper.vm as any
    expect(vm.editing).toBe(true)
    expect(toastCount()).toBe(0)

    wrapper.unmount()
  })

  it('C. 保存成功：一条成功提示，且无任何错误 chrome', async () => {
    const wrapper = mountProfile()
    await flushPromises()

    const vm = wrapper.vm as any
    vm.editing = true
    vm.form.heightCm = 175
    vm.form.weightKg = 70

    await vm.handleSubmit()
    await flushPromises()

    expect(ElMessageMock.success).toHaveBeenCalledTimes(1)
    expect(toastCount()).toBe(1)
    expect(wrapper.find('.form-error-summary').exists()).toBe(false)
    expect(wrapper.findAll('.el-form-item__error')).toHaveLength(0)

    wrapper.unmount()
  })
})