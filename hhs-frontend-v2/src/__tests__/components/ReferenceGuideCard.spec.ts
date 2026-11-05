import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ReferenceGuideCard from '@/views/data-input/components/ReferenceGuideCard.vue'
import { FALLBACK_METRIC_META } from '@/constants/metricMeta'

const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot /></div>'
  },
  'el-icon': {
    template: '<span class="el-icon"><slot /></span>'
  },
  'el-tag': {
    template: '<span class="el-tag"><slot /></span>'
  }
}

describe('ReferenceGuideCard.vue', () => {
  it('should render single metric reference range and guide hint correctly', () => {
    const wrapper = mount(ReferenceGuideCard, {
      props: {
        definition: FALLBACK_METRIC_META.bloodGlucose
      },
      global: {
        stubs: globalStubs
      }
    })

    expect(wrapper.text()).toContain('血糖常规参考区间')
    expect(wrapper.text()).toContain('3.9 ~ 6.1 mmol/L')
    expect(wrapper.text()).toContain('正常空腹参考值 3.9 ~ 6.1 mmol/L')
    expect(wrapper.text()).toContain('餐后2小时血糖应 < 7.8 mmol/L')
  })

  it('should render dual systolic and diastolic reference ranges for blood pressure', () => {
    const wrapper = mount(ReferenceGuideCard, {
      props: {
        definition: FALLBACK_METRIC_META.systolicBP,
        diastolicDef: FALLBACK_METRIC_META.diastolicBP
      },
      global: {
        stubs: globalStubs
      }
    })

    expect(wrapper.text()).toContain('收缩压 (高压)')
    expect(wrapper.text()).toContain('90 ~ 139 mmHg')
    expect(wrapper.text()).toContain('舒张压 (低压)')
    expect(wrapper.text()).toContain('60 ~ 89 mmHg')
    expect(wrapper.text()).toContain('静坐休息 5 分钟')
  })

  it('should render nothing when definition is undefined', () => {
    const wrapper = mount(ReferenceGuideCard, {
      props: {
        definition: undefined
      },
      global: {
        stubs: globalStubs
      }
    })

    expect(wrapper.find('.reference-guide-card').exists()).toBe(false)
  })
})
