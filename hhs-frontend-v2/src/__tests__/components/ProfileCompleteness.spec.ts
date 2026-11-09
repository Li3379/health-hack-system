import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ProfileCompleteness from '@/components/common/ProfileCompleteness.vue'
import { PROFILE_FIELDS } from '@/utils/profileFields'
import type { ProfileCompletenessVO, ProfileFieldStatusVO } from '@/types/api'

const ALL_KEYS = PROFILE_FIELDS.map(field => field.key)

const routerLinkStub = {
  props: ['to'],
  template: '<a class="router-link-stub" :href="String(to)"><slot /></a>'
}

const globalStubs = {
  RouterLink: routerLinkStub,
  'router-link': routerLinkStub
}

function makeFields(filledKeys: string[]): ProfileFieldStatusVO[] {
  return PROFILE_FIELDS.map(field => ({ ...field, filled: filledKeys.includes(field.key) }))
}

interface FixtureOptions {
  /** 默认 5/9 已填，与 UI-SPEC 的「已填 5 / 9」示例对齐。 */
  filledKeys?: string[]
  hasProfile?: boolean
  assumptions?: string[]
  score?: number
  level?: string
  fields?: ProfileFieldStatusVO[]
}

/**
 * 固定装置工厂。`hasProfile` 必须能显式传入 —— 影响句的第一条分支依据它，
 * 若默认 true 就无法覆盖「完全无档案」的新用户路径。
 */
function makeCompleteness(options: FixtureOptions = {}): ProfileCompletenessVO {
  const filledKeys = options.filledKeys ?? [
    'gender',
    'birthDate',
    'heightCm',
    'bmi',
    'bloodType'
  ]
  const fields = options.fields ?? makeFields(filledKeys)
  return {
    score: options.score ?? 62,
    level: options.level ?? '标准档案',
    filledCount: fields.filter(field => field.filled).length,
    totalCount: fields.length,
    hasProfile: options.hasProfile ?? true,
    assumptions: options.assumptions ?? [],
    fields
  }
}

function mountCompleteness(
  completeness: ProfileCompletenessVO,
  variant: 'full' | 'compact' = 'full'
) {
  return mount(ProfileCompleteness, {
    props: { completeness, variant },
    global: { stubs: globalStubs }
  })
}

describe('ProfileCompleteness.vue', () => {
  describe('meter and legend geometry', () => {
    it('renders one segment and one legend item per payload field (9 of each)', () => {
      const wrapper = mountCompleteness(makeCompleteness())

      expect(wrapper.findAll('.hhs-completeness-meter__seg')).toHaveLength(9)
      expect(wrapper.findAll('.completeness__legend-item')).toHaveLength(9)
    })

    it('drives the segment and legend count from fields.length, not a hard-coded 9', () => {
      const fourFields = makeFields([
        'gender',
        'birthDate',
        'heightCm',
        'weightKg'
      ])
      const wrapper = mountCompleteness(
        makeCompleteness({ fields: fourFields, score: 44, level: '基础档案' })
      )

      expect(wrapper.findAll('.hhs-completeness-meter__seg')).toHaveLength(4)
      expect(wrapper.findAll('.completeness__legend-item')).toHaveLength(4)
    })

    it('applies is-filled to exactly the filled segments', () => {
      const wrapper = mountCompleteness(makeCompleteness())

      const segments = wrapper.findAll('.hhs-completeness-meter__seg')
      expect(segments.filter(node => node.classes().includes('is-filled'))).toHaveLength(5)

      // 目录顺序：gender ✓ / birthDate ✓ / heightCm ✓ / weightKg ○ / bmi ✓ / bloodType ✓ …
      expect(segments[0].classes()).toContain('is-filled')
      expect(segments[3].classes()).not.toContain('is-filled')
    })

    it('renders ✓ for filled fields and ○ for missing fields in the legend', () => {
      const wrapper = mountCompleteness(makeCompleteness())

      const glyphs = wrapper.findAll('.completeness__state').map(node => node.text())
      expect(glyphs).toHaveLength(9)
      expect(glyphs[0]).toBe('✓')
      expect(glyphs[3]).toBe('○')
      // 9 项中恰好 5 个 ✓
      expect(glyphs.filter(glyph => glyph === '✓')).toHaveLength(5)
      expect(glyphs.filter(glyph => glyph === '○')).toHaveLength(4)
    })

    it('renders the score and the filled / total count from the payload', () => {
      const wrapper = mountCompleteness(makeCompleteness())

      expect(wrapper.find('.completeness__score').text()).toBe('62%')
      expect(wrapper.find('.completeness__count').text()).toBe('已填 5 / 9')
    })

    it('never hard-codes the total: a different payload renders its own count', () => {
      const wrapper = mountCompleteness(
        makeCompleteness({ fields: makeFields(ALL_KEYS), score: 100, level: '完整档案' })
      )

      expect(wrapper.find('.completeness__count').text()).toBe('已填 9 / 9')
    })
  })

  describe('tier pill', () => {
    it('maps 基础档案 to hhs-tier-pill--basic', () => {
      const wrapper = mountCompleteness(makeCompleteness({ level: '基础档案', score: 45 }))

      const pill = wrapper.find('.hhs-tier-pill')
      expect(pill.text()).toBe('基础档案')
      expect(pill.classes()).toContain('hhs-tier-pill--basic')
    })

    it('adds no modifier for 标准档案', () => {
      const wrapper = mountCompleteness(makeCompleteness({ level: '标准档案' }))

      const pill = wrapper.find('.hhs-tier-pill')
      expect(pill.text()).toBe('标准档案')
      expect(pill.classes()).toContain('hhs-tier-pill')
      expect(pill.classes()).not.toContain('hhs-tier-pill--basic')
      expect(pill.classes()).not.toContain('hhs-tier-pill--full')
    })

    it('maps 完整档案 to hhs-tier-pill--full', () => {
      const wrapper = mountCompleteness(makeCompleteness({ level: '完整档案', score: 100 }))

      expect(wrapper.find('.hhs-tier-pill').classes()).toContain('hhs-tier-pill--full')
    })
  })

  describe('accessibility', () => {
    it('hides the visual meter from assistive technology', () => {
      const wrapper = mountCompleteness(makeCompleteness())

      const meter = wrapper.find('.hhs-completeness-meter')
      expect(meter.attributes('aria-hidden')).toBe('true')
    })

    it('gives every legend item a visually-hidden 已填/未填 word next to the glyph', () => {
      const wrapper = mountCompleteness(makeCompleteness())

      const items = wrapper.findAll('.completeness__legend-item')
      const words = items.map(
        item => item.find('.visually-hidden').text()
      )
      expect(words).toHaveLength(9)
      expect(words[0]).toBe('已填')
      expect(words[3]).toBe('未填')

      // 每个字符号本身对读屏隐藏
      const glyph = wrapper.findAll('.completeness__state')[0]
      expect(glyph.attributes('aria-hidden')).toBe('true')
    })

    it('labels the full block from its own 档案完整度 heading', () => {
      const wrapper = mountCompleteness(makeCompleteness())

      const root = wrapper.find('section.completeness')
      const labelId = root.attributes('aria-labelledby')
      expect(labelId).toBeTruthy()
      expect(root.find('.completeness__label').attributes('id')).toBe(labelId)
    })

    it('renders nothing at all for a null payload instead of a 0% claim', () => {
      const wrapper = mount(ProfileCompleteness, {
        // 旧后端 / 缓存 VO 可能没有该字段；宁可不说，也不画一个假的 0%
        props: { completeness: null as unknown as ProfileCompletenessVO },
        global: { stubs: globalStubs }
      })

      expect(wrapper.find('.completeness').exists()).toBe(false)
      expect(wrapper.find('.hhs-completeness-meter').exists()).toBe(false)
      expect(wrapper.text()).not.toContain('0%')
    })
  })

  describe('impact line', () => {
    it('tells a brand-new user about the generic model when hasProfile is false', () => {
      const wrapper = mountCompleteness(
        makeCompleteness({ hasProfile: false, filledKeys: [], score: 0, level: '基础档案' })
      )

      const impact = wrapper.find('.completeness__impact').text()
      expect(impact).toBe('尚未填写健康档案，评分与风险结果将按通用参考值估算。')
      // 无档案行时风险引擎走固定 30/25/25，不使用 35 岁
      expect(impact).not.toContain('35 岁')
    })

    it('names the missing birthDate when a profile row exists', () => {
      const wrapper = mountCompleteness(
        makeCompleteness({ hasProfile: true, filledKeys: ['gender'], score: 11 })
      )

      const impact = wrapper.find('.completeness__impact').text()
      expect(impact).toBe('缺少出生日期，风险评估将按 35 岁与通用模型估算。')
      expect(impact).not.toContain('尚未填写健康档案')
    })

    it('names the missing gender without claiming a risk-engine consequence', () => {
      const wrapper = mountCompleteness(
        makeCompleteness({ hasProfile: true, filledKeys: ['birthDate'], score: 11 })
      )

      const impact = wrapper.find('.completeness__impact').text()
      expect(impact).toBe('缺少性别，健康报告与 AI 建议将按通用表述生成。')
      expect(impact).not.toContain('35 岁')
    })

    it('reports only the optional fields when every required and recommended field is filled', () => {
      const wrapper = mountCompleteness(
        makeCompleteness({
          filledKeys: ['gender', 'birthDate', 'heightCm', 'weightKg', 'bmi'],
          score: 78
        })
      )

      expect(wrapper.find('.completeness__impact').text()).toBe(
        '缺少血型、过敏史、家族病史或生活习惯，健康报告与 AI 建议的个性化程度会降低。'
      )
    })

    it('promises the 22.0 BMI substitution only when the payload assumptions carry the bmi sentence', () => {
      const wrapper = mountCompleteness(
        makeCompleteness({
          filledKeys: ['gender', 'birthDate', 'allergyHistory', 'familyHistory', 'lifestyleHabits'],
          assumptions: ['缺少 BMI，按 22.0 估算'],
          score: 62
        })
      )

      expect(wrapper.find('.completeness__impact').text()).toBe(
        '缺少身高、体重或 BMI，风险评估将按 BMI 参考值 22.0 估算。'
      )
    })

    it('uses the stale-bmi sentence and never promises 22.0 when the assumptions omit the bmi entry', () => {
      const wrapper = mountCompleteness(
        makeCompleteness({
          filledKeys: ['gender', 'birthDate', 'allergyHistory', 'familyHistory', 'lifestyleHabits'],
          assumptions: [],
          score: 62
        })
      )

      const impact = wrapper.find('.completeness__impact').text()
      expect(impact).toBe(
        '缺少身高或体重，BMI 无法随身体变化重算，风险评估沿用已记录的 BMI。'
      )
      expect(impact).not.toContain('22.0')
    })

    it('reports completeness when all nine fields are filled', () => {
      const wrapper = mountCompleteness(
        makeCompleteness({ filledKeys: ALL_KEYS, score: 100, level: '完整档案' })
      )

      expect(wrapper.find('.completeness__impact').text()).toBe(
        '档案信息完整，评分与风险结果均基于真实数据。'
      )
      // 全填时图例全 ✓
      const glyphs = wrapper.findAll('.completeness__state').map(node => node.text())
      expect(glyphs.every(glyph => glyph === '✓')).toBe(true)
    })
  })

  describe('compact variant', () => {
    it('renders the score, the pill and a 完善档案 link, with no legend and no impact line', () => {
      const wrapper = mountCompleteness(makeCompleteness(), 'compact')

      expect(wrapper.find('.completeness__score').text()).toBe('62%')
      expect(wrapper.find('.hhs-tier-pill').text()).toBe('标准档案')
      expect(wrapper.find('.completeness__legend').exists()).toBe(false)
      expect(wrapper.find('.completeness__impact').exists()).toBe(false)

      const link = wrapper.find('.completeness__link')
      expect(link.exists()).toBe(true)
      expect(link.text()).toBe('完善档案')
      expect(link.attributes('href')).toBe('/prevention/profile')
    })

    it('marks the compact meter with the compact modifier and still hides it from screen readers', () => {
      const wrapper = mountCompleteness(makeCompleteness(), 'compact')

      const meter = wrapper.find('.hhs-completeness-meter')
      expect(meter.classes()).toContain('hhs-completeness-meter--compact')
      expect(meter.attributes('aria-hidden')).toBe('true')
      expect(wrapper.findAll('.hhs-completeness-meter__seg')).toHaveLength(9)
    })

    it('does not render a section landmark in the compact variant', () => {
      const wrapper = mountCompleteness(makeCompleteness(), 'compact')

      expect(wrapper.find('section.completeness').exists()).toBe(false)
      expect(wrapper.find('div.completeness.completeness--compact').exists()).toBe(true)
    })
  })
})