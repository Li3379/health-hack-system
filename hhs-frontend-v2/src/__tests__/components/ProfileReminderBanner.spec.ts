import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ProfileReminderBanner from '@/components/common/ProfileReminderBanner.vue'
import { PROFILE_FIELDS } from '@/utils/profileFields'
import type { ProfileCompletenessVO, ProfileFieldStatusVO } from '@/types/api'

const ALL_KEYS = PROFILE_FIELDS.map(field => field.key)

/**
 * el-button 必须渲染插槽，否则「完善档案 / 稍后提醒」的可访问标签无从断言。
 * 末尾刻意**不**再 `$emit('click')` —— 父级的 @click 会作为原生监听器落到这个
 * button 根节点上，若再 emit 一次会重复触发（两次 dismiss）。
 */
const globalStubs = {
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'size', 'loading']
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  InfoFilled: {
    template: '<span class="icon-info-filled">i</span>'
  }
}

function makeFields(filledKeys: string[]): ProfileFieldStatusVO[] {
  return PROFILE_FIELDS.map(field => ({ ...field, filled: filledKeys.includes(field.key) }))
}

interface FixtureOptions {
  score?: number
  level?: string
  filledKeys?: string[]
  hasProfile?: boolean
  assumptions?: string[]
}

function makeCompleteness(options: FixtureOptions = {}): ProfileCompletenessVO {
  // 默认 45% / 4 项已填 → 还缺 5 项，对应 UI-SPEC 的「档案完整度 45%，还缺 5 项」示例。
  // 计数必须由 fields 真实推出，否则标题里的 N 会与正文枚举的字段数打架。
  const filledKeys = options.filledKeys ?? ['gender', 'birthDate', 'heightCm', 'bmi']
  const fields = makeFields(filledKeys)
  return {
    score: options.score ?? 45,
    level: options.level ?? '基础档案',
    filledCount: fields.filter(field => field.filled).length,
    totalCount: fields.length,
    hasProfile: options.hasProfile ?? true,
    assumptions: options.assumptions ?? [],
    fields
  }
}

function mountBanner(
  completeness: ProfileCompletenessVO | null,
  dismissed = false
) {
  return mount(ProfileReminderBanner, {
    props: { completeness, dismissed },
    global: { stubs: globalStubs }
  })
}

describe('ProfileReminderBanner.vue', () => {
  describe('score gate', () => {
    it('renders nothing at exactly 60 — a gender + birthDate user is not nagged', () => {
      expect(mountBanner(makeCompleteness({ score: 60 })).find('.profile-banner').exists()).toBe(
        false
      )
    })

    it('renders nothing above the threshold (61 and 100)', () => {
      expect(mountBanner(makeCompleteness({ score: 61 })).find('.profile-banner').exists()).toBe(
        false
      )
      expect(mountBanner(makeCompleteness({ score: 100 })).find('.profile-banner').exists()).toBe(
        false
      )
    })

    it('renders below the threshold (0, 30 and 59)', () => {
      for (const score of [0, 30, 59]) {
        const wrapper = mountBanner(makeCompleteness({ score }))
        expect(wrapper.find('.profile-banner').exists()).toBe(true)
      }
    })

    it('renders nothing when the store reports the reminder dismissed', () => {
      const wrapper = mountBanner(makeCompleteness({ score: 45 }), true)

      expect(wrapper.find('.profile-banner').exists()).toBe(false)
    })

    it('renders nothing for a null payload, never as a fabricated 0%', () => {
      const wrapper = mountBanner(null)

      expect(wrapper.find('.profile-banner').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('0%')
    })
  })

  describe('copy', () => {
    it('titles itself with the score and the remaining count from the payload', () => {
      const wrapper = mountBanner(makeCompleteness({ score: 45, level: '基础档案' }))

      expect(wrapper.find('.profile-banner__title').text()).toBe(
        '档案完整度 45%，还缺 5 项'
      )
    })

    it('names the missing field labels in catalog order and ends with the consequence clause', () => {
      const wrapper = mountBanner(makeCompleteness())

      const desc = wrapper.find('.profile-banner__desc').text()
      expect(desc).toBe('缺少体重、血型、过敏史、家族病史、生活习惯，健康评分与风险结果会按参考值估算。')
    })

    it('lists nine labels when nothing is filled', () => {
      const wrapper = mountBanner(makeCompleteness({ score: 0, filledKeys: [] }))

      const desc = wrapper.find('.profile-banner__desc').text()
      expect(desc).toBe(
        `缺少${PROFILE_FIELDS.map(field => field.label).join('、')}，健康评分与风险结果会按参考值估算。`
      )
    })
  })

  describe('controls', () => {
    it('exposes exactly one primary 完善档案, one 稍后提醒 and one named close control', () => {
      const wrapper = mountBanner(makeCompleteness())

      const buttons = wrapper.findAll('.profile-banner__actions .el-button')
      expect(buttons).toHaveLength(2)
      expect(buttons[0].text()).toBe('完善档案')
      expect(buttons[1].text()).toBe('稍后提醒')

      const close = wrapper.findAll('button').filter(node =>
        node.classes().includes('profile-banner__close')
      )
      expect(close).toHaveLength(1)
      expect(close[0].attributes('aria-label')).toBe('关闭提醒，7 天内不再显示')
    })

    it('emits complete with the required-first missing key when 完善档案 is clicked', () => {
      // gender 已填、birthDate 已填 → 第一个缺的 required 是 heightCm（recommended 优先于 optional）
      const wrapper = mountBanner(
        makeCompleteness({
          filledKeys: ['gender', 'birthDate'],
          score: 22
        })
      )

      const primary = wrapper.findAll('.profile-banner__actions .el-button')[0]
      primary.trigger('click')

      expect(wrapper.emitted('complete')).toBeTruthy()
      expect(wrapper.emitted('complete')?.[0]).toEqual(['heightCm'])
    })

    it('prefers a missing required field over an earlier missing optional field', async () => {
      // 只缺 birthDate(required) 与 bloodType(optional)：必须指向 birthDate。
      // score 必须仍低于 60，否则横幅不渲染，这条断言就无从谈起。
      const wrapper = mountBanner(
        makeCompleteness({
          filledKeys: ALL_KEYS.filter(key => key !== 'birthDate' && key !== 'bloodType'),
          score: 55
        })
      )

      await wrapper.findAll('.profile-banner__actions .el-button')[0].trigger('click')

      expect(wrapper.emitted('complete')?.[0]).toEqual(['birthDate'])
    })

    it('emits dismiss from 稍后提醒 and from the close control alike', async () => {
      const wrapper = mountBanner(makeCompleteness())

      await wrapper.findAll('.profile-banner__actions .el-button')[1].trigger('click')
      expect(wrapper.emitted('dismiss')).toHaveLength(1)

      await wrapper.find('.profile-banner__close').trigger('click')
      expect(wrapper.emitted('dismiss')).toHaveLength(2)
    })

    it('emits complete with null when nothing is missing', () => {
      const wrapper = mountBanner(
        makeCompleteness({ filledKeys: ALL_KEYS, score: 100, level: '完整档案' })
      )

      // score 100 时横幅本不渲染；直接调用暴露的处理器以验证 null 分支可控
      const vm = wrapper.vm as unknown as { goProfileWithFocus: () => void }
      vm.goProfileWithFocus()

      expect(wrapper.emitted('complete')?.[0]).toEqual([null])
    })
  })

  describe('structure and accessibility', () => {
    it('is a self-drawn div, never an el-alert', () => {
      const wrapper = mountBanner(makeCompleteness())

      expect(wrapper.find('.el-alert').exists()).toBe(false)
      expect(wrapper.element.tagName.toLowerCase()).toBe('div')
      expect(wrapper.classes()).toContain('profile-banner')
    })

    it('carries role=status plus an aria-labelledby that resolves to its own title id', () => {
      const wrapper = mountBanner(makeCompleteness())

      const root = wrapper.find('[role="status"]')
      expect(root.exists()).toBe(true)

      const labelledBy = root.attributes('aria-labelledby')
      expect(labelledBy).toBeTruthy()
      expect(wrapper.find('.profile-banner__title').attributes('id')).toBe(labelledBy)
    })

    it('hides the decorative icon from assistive technology', () => {
      const wrapper = mountBanner(makeCompleteness())

      expect(wrapper.find('.profile-banner__icon').attributes('aria-hidden')).toBe('true')
    })
  })
})