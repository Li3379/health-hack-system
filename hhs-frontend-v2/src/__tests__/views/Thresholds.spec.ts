import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { defineComponent, h, type PropType, type VNode } from 'vue'
import type { Mock } from 'vitest'

/**
 * Thresholds 页面契约（THR-07 / THR-08 页面侧，12-08）：
 * 1. 教学型空状态 —— 标题/正文/CTA 与「绝不出现暂无数据」
 * 2. 默认规则表 —— 恰好 6 项、标签有序、内容派生自 metricMeta store（T-12-29：无第二份硬编码表）
 * 3. CTA 与「添加阈值」两个入口都打开向导（新增 editing=null，编辑传整行）
 * 4. 有数据时空状态不渲染、列表渲染全部存量行（D-20：不可告警指标不消失）
 * 5. 加载失败 —— ErrorState 独占、零 toast（D-02：一个失败只呈现一次）
 *
 * 挂载配方复用 ErrorLayerSample.spec.ts：mock @/api/threshold、@/composables/useGsap、
 * @/stores/auth，element-plus 用 importOriginal 部分 mock 只替换 ElMessage。
 * ThresholdWizard 打成最小桩：页面契约只断言 props 透传，向导本身由 12-07 的组件 spec 覆盖。
 */

vi.mock('@/api/threshold', () => ({
  thresholdApi: {
    getThresholds: vi.fn(),
    getByUserId: vi.fn(),
    getByMetricKey: vi.fn(),
    createThreshold: vi.fn(),
    updateThreshold: vi.fn(),
    deleteThreshold: vi.fn()
  }
}))

// Thresholds.vue 的 onMounted 消费 gsap / DUR / EASE；形状照抄 ErrorLayerSample.spec.ts:37。
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

// Thresholds.vue 读 authStore.user?.id（兼容表单 userId）。
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ user: { id: 1, username: 'tester' } })
}))

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

import Thresholds from '@/views/health/Thresholds.vue'
import { thresholdApi } from '@/api/threshold'
import { ElMessage } from 'element-plus'
import type { UserThreshold } from '@/types/api'

const getThresholdsMock = thresholdApi.getThresholds as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

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

// —— 组件桩 ——

/** 最小向导桩：只暴露页面会绑定的 props（modelValue / editing），供断言透传。 */
const ThresholdWizardStub = defineComponent({
  name: 'ThresholdWizard',
  props: {
    modelValue: { type: Boolean, default: false },
    editing: { type: Object as PropType<UserThreshold | null>, default: null }
  },
  emits: ['update:modelValue', 'saved', 'error'],
  template:
    '<div class="wizard-stub" :data-open="String(modelValue)" :data-editing-id="editing ? String(editing.id) : \'\'" />'
})

/** 列 vnodes 上的模板插槽（#default="{ row }"）。 */
interface ColumnChildren {
  default?: (scope: { row: UserThreshold }) => VNode[]
}

/**
 * el-table 桩：从默认插槽里取出每个 el-table-column 的模板插槽，
 * 逐行调用并渲染 —— 这样行内「编辑/删除」按钮可以真实点击，走到 handleEdit/handleDelete。
 */
const ElTableStub = defineComponent({
  name: 'ElTableStub',
  props: {
    data: { type: Array as PropType<UserThreshold[]>, default: () => [] }
  },
  setup(props, { slots }) {
    return () => {
      const columnVnodes = (slots.default?.() ?? []) as VNode[]
      const columnSlots = columnVnodes
        .map(vnode => {
          const children = vnode.children as ColumnChildren | string | null
          return children &&
            typeof children === 'object' &&
            typeof children.default === 'function'
            ? children.default
            : null
        })
        .filter((slot): slot is NonNullable<typeof slot> => slot !== null)
      return h(
        'div',
        { class: 'el-table' },
        props.data.map(row =>
          h(
            'div',
            { class: 'el-table__row', 'data-row-id': String(row.id) },
            columnSlots.map(slot => slot({ row }))
          )
        )
      )
    }
  }
})

const globalStubs = {
  ThresholdWizard: ThresholdWizardStub,
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-table': ElTableStub,
  // 列 vnodes 从不被挂载（表格桩直接取其插槽渲染），此桩只为消解解析告警。
  'el-table-column': { template: '<div class="el-table-column" />' },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'loading', 'disabled', 'size', 'link']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  'el-descriptions': {
    template: '<div class="el-descriptions"><slot /></div>',
    props: ['column', 'border', 'title']
  },
  'el-descriptions-item': {
    template:
      '<div class="el-descriptions-item">' +
      '<span class="descriptions-item__label">{{ label }}</span>' +
      '<span class="descriptions-item__content"><slot /></span>' +
      '</div>',
    props: ['label']
  },
  'el-pagination': {
    template: '<div class="el-pagination" />',
    props: ['currentPage', 'pageSize', 'total', 'layout']
  }
}

function mountThresholds() {
  return mount(Thresholds, {
    global: {
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

// —— 测试数据 ——

function makeRow(overrides: Partial<UserThreshold> = {}): UserThreshold {
  return {
    id: 3,
    userId: 1,
    metricKey: 'glucose',
    warningHigh: 6,
    criticalHigh: 7,
    warningLow: 3,
    criticalLow: 2,
    createdAt: '2026-01-01 00:00:00',
    updatedAt: '2026-01-01 00:00:00',
    ...overrides
  }
}

function pageResult(records: UserThreshold[]) {
  return {
    code: 200,
    message: 'success',
    data: { records, total: records.length, page: 1, size: 100 }
  }
}

describe('Thresholds — 教学空状态、默认规则表与页面契约（THR-07）', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    // 空态是默认背景：多数用例不需要重新 stub
    getThresholdsMock.mockResolvedValue(pageResult([]))
  })

  afterEach(() => {
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  it('1. 空列表：渲染教学空状态（标题 + CTA），且绝不出现「暂无数据」', async () => {
    const wrapper = mountThresholds()
    await flushPromises()

    expect(wrapper.find('.threshold-empty').exists()).toBe(true)
    expect(wrapper.find('.threshold-empty__title').text()).toBe('尚未设置个性化阈值')
    expect(wrapper.find('.threshold-empty__body').text()).toContain('系统正在使用默认告警规则')
    const cta = wrapper.findAll('button').find(button => button.text() === '使用推荐阈值')
    expect(cta).toBeDefined()
    // 通用空态文案零出现：空态必须是教学，而不是「暂无数据」
    expect(wrapper.html()).not.toContain('暂无数据')

    wrapper.unmount()
  })

  it('2. 默认规则表恰好 6 行，标签按序为 心率/收缩压/舒张压/血糖/体温/BMI，内容派生自元数据', async () => {
    const wrapper = mountThresholds()
    await flushPromises()

    const labels = wrapper.findAll('.descriptions-item__label').map(node => node.text())
    expect(labels).toEqual(['心率', '收缩压', '舒张压', '血糖', '体温', 'BMI'])

    // 内容模式照 UI-SPEC Contract 6：由 store 的 normalMin/normalMax/unit + recommended 生成
    const contents = wrapper.findAll('.descriptions-item__content').map(node => node.text())
    expect(contents[0]).toBe('正常 60–100 次/分，高于 100 黄色提醒，高于 120 红色提醒')
    expect(contents).toHaveLength(6)

    wrapper.unmount()
  })

  it('3. 点击「使用推荐阈值」打开向导（新增模式，editing=null）', async () => {
    const wrapper = mountThresholds()
    await flushPromises()

    expect(wrapper.find('.wizard-stub').attributes('data-open')).toBe('false')
    const cta = wrapper.findAll('button').find(button => button.text() === '使用推荐阈值')
    expect(cta).toBeDefined()

    await cta?.trigger('click')
    await flushPromises()

    expect(wrapper.find('.wizard-stub').attributes('data-open')).toBe('true')
    expect(wrapper.find('.wizard-stub').attributes('data-editing-id')).toBe('')

    wrapper.unmount()
  })

  it('4. 有数据：空状态不渲染，表格渲染全部存量行（含不可告警指标）', async () => {
    getThresholdsMock.mockResolvedValue(
      pageResult([
        makeRow(),
        // D-20：bloodOxygen 无种子规则、不可告警，但已有阈值行必须原样可见
        makeRow({ id: 4, metricKey: 'bloodOxygen', warningHigh: 96, criticalHigh: 90 })
      ])
    )

    const wrapper = mountThresholds()
    await flushPromises()

    expect(wrapper.find('.threshold-empty').exists()).toBe(false)
    expect(wrapper.find('.el-table').exists()).toBe(true)
    expect(wrapper.findAll('.el-table__row')).toHaveLength(2)
    expect(wrapper.find('.el-table__row[data-row-id="4"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('5. 加载失败：ErrorState 独占渲染、零 toast（D-02：一个失败只呈现一次）', async () => {
    getThresholdsMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    const wrapper = mountThresholds()
    await flushPromises()

    expect(wrapper.find('.error-state__title').text()).toBe('无法加载阈值列表')
    expect(wrapper.find('.error-state__desc').text()).toBe('可能是网络波动或服务暂时不可用，请重试。')
    // 重试可访问名 = 重新加载阈值列表（动词 + 对象）
    expect(wrapper.find('.error-state__actions button').attributes('aria-label')).toBe(
      '重新加载阈值列表'
    )
    // 失败与空态互斥：ErrorState 出现时教学空状态绝不出现
    expect(wrapper.find('.threshold-empty').exists()).toBe(false)
    // catch 只置状态即返回，不叠加 toast
    expect(toastCount()).toBe(0)

    wrapper.unmount()
  })

  it('6. 向导桩收到 editing=null（新增）与被点击行（编辑）', async () => {
    getThresholdsMock.mockResolvedValue(pageResult([makeRow({ id: 7 })]))

    const wrapper = mountThresholds()
    await flushPromises()

    // 入口一：列表头「添加阈值」
    const addButton = wrapper.findAll('button').find(button => button.text() === '添加阈值')
    expect(addButton).toBeDefined()
    await addButton?.trigger('click')
    await flushPromises()
    expect(wrapper.find('.wizard-stub').attributes('data-open')).toBe('true')
    expect(wrapper.find('.wizard-stub').attributes('data-editing-id')).toBe('')

    // 入口二：行内「编辑」，向导应收到被点击的整行
    const editButton = wrapper
      .find('.el-table__row[data-row-id="7"]')
      .findAll('button')
      .find(button => button.text() === '编辑')
    expect(editButton).toBeDefined()
    await editButton?.trigger('click')
    await flushPromises()
    expect(wrapper.find('.wizard-stub').attributes('data-open')).toBe('true')
    expect(wrapper.find('.wizard-stub').attributes('data-editing-id')).toBe('7')

    wrapper.unmount()
  })
})
