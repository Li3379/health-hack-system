import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EstimateBadge from '@/components/common/EstimateBadge.vue'

function mountBadge(completeness: number | null | undefined) {
  return mount(EstimateBadge, { props: { completeness } })
}

describe('EstimateBadge.vue', () => {
  describe('null handling', () => {
    it('renders nothing when completeness is null — say nothing rather than 0%', () => {
      const wrapper = mountBadge(null)

      expect(wrapper.find('.hhs-estimate-badge').exists()).toBe(false)
      expect(wrapper.text()).toBe('')
    })

    it('renders nothing when completeness is undefined', () => {
      const wrapper = mountBadge(undefined)

      expect(wrapper.find('.hhs-estimate-badge').exists()).toBe(false)
    })

    it('renders nothing at exactly 100', () => {
      const wrapper = mountBadge(100)

      expect(wrapper.find('.hhs-estimate-badge').exists()).toBe(false)
    })
  })

  describe('render conditions', () => {
    it('renders at 0 — a real value, not a falsy miss', () => {
      expect(mountBadge(0).find('.hhs-estimate-badge').exists()).toBe(true)
    })

    it('renders at 1 and at 99', () => {
      expect(mountBadge(1).find('.hhs-estimate-badge').exists()).toBe(true)
      expect(mountBadge(99).find('.hhs-estimate-badge').exists()).toBe(true)
    })
  })

  describe('structure and interaction', () => {
    it('is a button.hhs-estimate-badge carrying the state copy and the action fragment', () => {
      const wrapper = mountBadge(62)

      const badge = wrapper.find('.hhs-estimate-badge')
      expect(badge.element.tagName.toLowerCase()).toBe('button')
      expect(badge.attributes('type')).toBe('button')
      expect(badge.text()).toContain('基于不完整档案估算')
      expect(badge.text()).toContain('完善档案')
      expect(badge.find('.hhs-estimate-badge__action').text()).toBe('完善档案')
    })

    it('hides the separator dot from assistive technology', () => {
      const wrapper = mountBadge(62)

      const separator = wrapper.findAll('span').filter(node => node.text() === '·')
      expect(separator).toHaveLength(1)
      expect(separator[0].attributes('aria-hidden')).toBe('true')
    })

    it('emits complete on click', async () => {
      const wrapper = mountBadge(62)

      await wrapper.find('.hhs-estimate-badge').trigger('click')

      expect(wrapper.emitted('complete')).toHaveLength(1)
    })

    it('is a real button so Enter and Space activate it natively', async () => {
      const wrapper = mountBadge(62)

      // 原生 button 的可激活行为来自标签本身；这里断言它不是 span/div，
      // 因为后两者需要手写 keydown 才能响应 Enter/Space。
      expect(wrapper.element.tagName.toLowerCase()).toBe('button')

      await wrapper.find('.hhs-estimate-badge').trigger('keydown', { key: 'Enter' })
      await wrapper.find('.hhs-estimate-badge').trigger('keyup', { key: ' ' })
      // 原生按钮的键盘激活由 UA 转成 click；显式 click 仍可触发一次 emit
      await wrapper.find('.hhs-estimate-badge').trigger('click')
      expect(wrapper.emitted('complete')).toHaveLength(1)
    })

    it('is not an el-tag and does not import the router', () => {
      const wrapper = mountBadge(62)

      expect(wrapper.find('.el-tag').exists()).toBe(false)
      // 组件不自行跳转：导航由父级根据 complete 事件决定
      expect(wrapper.findComponent({ name: 'RouterLink' }).exists()).toBe(false)
    })
  })
})