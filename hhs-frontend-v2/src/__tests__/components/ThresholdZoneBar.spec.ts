import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ThresholdZoneBar from '@/views/health/components/ThresholdZoneBar.vue'
import type { ThresholdDraft } from '@/utils/thresholdValidation'

const FULL_DRAFT: ThresholdDraft = {
  criticalLow: 40,
  warningLow: 50,
  warningHigh: 100,
  criticalHigh: 120
}

const PARTIAL_DRAFT: ThresholdDraft = {
  criticalLow: null,
  warningLow: null,
  warningHigh: 100,
  criticalHigh: null
}

describe('ThresholdZoneBar.vue', () => {
  it('renders exactly five bands when all four boundaries are present', () => {
    const wrapper = mount(ThresholdZoneBar, { props: { draft: FULL_DRAFT } })

    expect(wrapper.findAll('.zone-bar__band')).toHaveLength(5)
  })

  it('renders the band names in the fixed order 严重低 / 警告低 / 正常 / 警告高 / 严重高', () => {
    const wrapper = mount(ThresholdZoneBar, { props: { draft: FULL_DRAFT } })

    const names = wrapper.findAll('.zone-bar__name').map(node => node.text())
    expect(names).toEqual(['严重低', '警告低', '正常', '警告高', '严重高'])
  })

  it('renders each boundary value in its own band', () => {
    const wrapper = mount(ThresholdZoneBar, { props: { draft: FULL_DRAFT } })

    const values = wrapper.findAll('.zone-bar__value').map(node => node.text())
    // 四个承载边界的带：严重低 40 / 警告低 50 / 警告高 100 / 严重高 120
    expect(values[0]).toBe('40')
    expect(values[1]).toBe('50')
    expect(values[3]).toBe('100')
    expect(values[4]).toBe('120')
  })

  it('keeps five bands with null boundaries rendered as — and never leaks undefined/null text', () => {
    const wrapper = mount(ThresholdZoneBar, { props: { draft: PARTIAL_DRAFT } })

    const bands = wrapper.findAll('.zone-bar__band')
    expect(bands).toHaveLength(5)

    const values = wrapper.findAll('.zone-bar__value').map(node => node.text())
    // 三个 null 边界带各自显示 —，已填的警告高带显示 100
    expect(values[0]).toBe('—')
    expect(values[1]).toBe('—')
    expect(values[3]).toBe('100')
    expect(values[4]).toBe('—')

    const text = wrapper.text()
    expect(text).not.toContain('undefined')
    expect(text).not.toContain('null')
  })

  it('reads all five zone prefixes and 未设置 for the null boundaries in the aria-label', () => {
    const wrapper = mount(ThresholdZoneBar, { props: { draft: PARTIAL_DRAFT } })

    const label = wrapper.attributes('aria-label') ?? ''
    expect(label).toContain('严重低：')
    expect(label).toContain('警告低：')
    expect(label).toContain('正常：')
    expect(label).toContain('警告高：')
    expect(label).toContain('严重高：')
    expect(label).toContain('未设置')
  })

  it('updates the rendered value reactively via setProps without remounting', async () => {
    const wrapper = mount(ThresholdZoneBar, { props: { draft: FULL_DRAFT } })
    expect(wrapper.findAll('.zone-bar__value')[3].text()).toBe('100')

    await wrapper.setProps({ draft: { ...FULL_DRAFT, warningHigh: 110 } })

    expect(wrapper.findAll('.zone-bar__value')[3].text()).toBe('110')
    expect(wrapper.attributes('aria-label')).toContain('110')
    // 组件没有重新挂载：带数量与名称顺序保持不变
    expect(wrapper.findAll('.zone-bar__band')).toHaveLength(5)
  })

  it('includes the unit in the aria-label when supplied', () => {
    const wrapper = mount(ThresholdZoneBar, { props: { draft: FULL_DRAFT, unit: 'mmHg' } })

    expect(wrapper.attributes('aria-label')).toContain('mmHg')
  })
})
