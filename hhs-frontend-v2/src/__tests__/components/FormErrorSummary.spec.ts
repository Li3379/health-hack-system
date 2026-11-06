import { describe, it, expect, afterEach } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import FormErrorSummary from '@/components/common/FormErrorSummary.vue'
import type { SummaryItem } from '@/composables/useFormServerErrors'

// el-alert 的 stub 只渲染 title 槽（刻意不渲染默认插槽）：
// 若列表被误写进插槽，下面的 li 断言会直接失败。
const globalStubs = {
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  }
}

function items(...fields: Array<Partial<SummaryItem> & { field: string }>): SummaryItem[] {
  return fields.map(({ field, label = '', message = '后端消息' }) => ({ field, label, message }))
}

function mountSummary(summaryItems: SummaryItem[]): VueWrapper {
  return mount(FormErrorSummary, {
    props: { summaryItems },
    global: { stubs: globalStubs },
    attachTo: document.body
  })
}

describe('FormErrorSummary.vue', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders nothing when there are zero entries', () => {
    const wrapper = mountSummary([])

    expect(wrapper.find('.form-error-summary').exists()).toBe(false)
    expect(wrapper.find('.el-alert').exists()).toBe(false)
    wrapper.unmount()
  })

  it('renders a singular Chinese title and a single list row for one entry', () => {
    const wrapper = mountSummary(
      items({ field: 'metricKey', label: '指标类型', message: '不能为空' })
    )

    expect(wrapper.text()).toContain('请修正以下 1 项填写问题')
    expect(wrapper.findAll('li')).toHaveLength(1)
    expect(wrapper.find('.form-error-summary__link').text()).toBe('指标类型：不能为空')
    wrapper.unmount()
  })

  it('renders the count for three entries and one row each', () => {
    const wrapper = mountSummary(
      items(
        { field: 'metricKey', label: '指标类型', message: '不能为空' },
        { field: 'value', label: '指标数值', message: '超出范围' },
        { field: 'notes', label: '备注说明', message: '过长' }
      )
    )

    expect(wrapper.text()).toContain('请修正以下 3 项填写问题')
    expect(wrapper.findAll('li')).toHaveLength(3)
    wrapper.unmount()
  })

  it('renders the list as a sibling of el-alert, not as slot content', () => {
    const wrapper = mountSummary(
      items({ field: 'metricKey', label: '指标类型', message: '不能为空' })
    )

    const alert = wrapper.find('.el-alert')
    expect(alert.exists()).toBe(true)

    const siblingClass = alert.element.nextElementSibling?.className ?? ''
    expect(siblingClass).toContain('form-error-summary__list')

    // 列表绝不出现在 alert 内部。
    expect(alert.element.querySelector('li')).toBeNull()
    wrapper.unmount()
  })

  it('renders only the backend message for an unknown field — no colon prefix', () => {
    const wrapper = mountSummary(
      items({ field: 'unknownField', label: '', message: '该值超出允许范围' })
    )

    const text = wrapper.find('.form-error-summary__link').text()
    expect(text.startsWith('该值超出允许范围')).toBe(true)
    expect(text).not.toContain('：')
    expect(text).not.toContain('unknownField')
    wrapper.unmount()
  })

  it('emits focus-field with the clicked entry field', async () => {
    const wrapper = mountSummary(
      items(
        { field: 'metricKey', label: '指标类型', message: '不能为空' },
        { field: 'value', label: '指标数值', message: '超出范围' }
      )
    )

    await wrapper.findAll('.form-error-summary__link')[0].trigger('click')

    expect(wrapper.emitted('focus-field')).toEqual([['metricKey']])
    wrapper.unmount()
  })

  it('exposes the group role, negative tabindex and matching aria-labelledby', () => {
    const wrapper = mountSummary(
      items({ field: 'metricKey', label: '指标类型', message: '不能为空' })
    )

    const root = wrapper.find('.form-error-summary')
    const titleSpan = wrapper.find('.el-alert span')

    expect(root.attributes('tabindex')).toBe('-1')
    expect(root.attributes('role')).toBe('group')
    expect(root.attributes('aria-labelledby')).toBe(titleSpan.attributes('id'))
    expect(root.attributes('aria-labelledby')).toContain('form-error-summary-title-')
    wrapper.unmount()
  })

  it('generates distinct title ids for two concurrently mounted instances', () => {
    const first = mountSummary(
      items({ field: 'metricKey', label: '指标类型', message: '不能为空' })
    )
    const second = mountSummary(items({ field: 'value', label: '指标数值', message: '超出范围' }))

    const firstId = first.find('.form-error-summary').attributes('aria-labelledby')
    const secondId = second.find('.form-error-summary').attributes('aria-labelledby')

    expect(firstId).toBeTruthy()
    expect(secondId).toBeTruthy()
    expect(firstId).not.toBe(secondId)

    first.unmount()
    second.unmount()
  })

  it('focusSummary moves focus to the summary wrapper', () => {
    const wrapper = mountSummary(
      items({ field: 'metricKey', label: '指标类型', message: '不能为空' })
    )

    const vm = wrapper.vm as unknown as { focusSummary: () => void }
    vm.focusSummary()

    expect(document.activeElement).toBe(wrapper.find('.form-error-summary').element)
    wrapper.unmount()
  })
})
