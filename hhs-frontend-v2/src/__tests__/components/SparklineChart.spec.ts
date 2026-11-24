import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SparklineChart from '@/components/SparklineChart.vue'

/**
 * SparklineChart 把什么颜色交给 canvas —— 直接对 setOption 收到的 option 断言。
 *
 * 真实 ECharts 在 happy-dom 里没有 2D canvas 上下文（`clearRect` of null），
 * 无法渲染，因此这里 mock 掉 echarts，只捕获 option。这正好是本缺陷的层面：
 * 缺陷不在绘制，而在传给绘制层的颜色字符串本身。
 */

const { setOption, dispose, FakeLinearGradient } = vi.hoisted(() => {
  class FakeLinearGradient {
    constructor(
      public x: number,
      public y: number,
      public x2: number,
      public y2: number,
      public colorStops: Array<{ offset: number; color: string }>
    ) {}
  }
  return { setOption: vi.fn(), dispose: vi.fn(), FakeLinearGradient }
})

vi.mock('echarts/core', () => {
  const use = vi.fn()
  const init = vi.fn(() => ({ setOption, dispose, resize: vi.fn() }))
  return {
    use,
    init,
    graphic: { LinearGradient: FakeLinearGradient },
    default: { use, init, graphic: { LinearGradient: FakeLinearGradient } }
  }
})

vi.mock('echarts/charts', () => ({ LineChart: {} }))
vi.mock('echarts/components', () => ({ GridComponent: {} }))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

interface GradientStop {
  offset: number
  color: string
}

interface CapturedOption {
  series: Array<{
    lineStyle: { color: string }
    areaStyle: { color: { colorStops: GradientStop[] } }
  }>
}

const areaColorsOf = (option: CapturedOption): string[] =>
  option.series[0].areaStyle.color.colorStops.map(s => s.color)

afterEach(() => {
  document.documentElement.style.removeProperty('--accent-cool')
  document.documentElement.style.removeProperty('--accent-warm')
})

beforeEach(() => {
  setOption.mockClear()
  dispose.mockClear()
})

describe('SparklineChart canvas-safe colors', () => {
  it('never hands a raw var() expression to the canvas gradient', () => {
    document.documentElement.style.setProperty('--accent-cool', '#5EEAD4')

    mount(SparklineChart, { props: { data: [1, 2, 3] } })

    const option = setOption.mock.calls[0][0] as CapturedOption
    const colors = areaColorsOf(option)

    for (const color of colors) {
      expect(color).not.toContain('var(')
    }
    expect(colors).toEqual(['rgba(94, 234, 212, 0.25)', 'rgba(94, 234, 212, 0.02)'])
    expect(option.series[0].lineStyle.color).toBe('#5EEAD4')
  })

  it('uses the light-theme value when the variable carries it', () => {
    document.documentElement.style.setProperty('--accent-cool', '#0d9488')

    mount(SparklineChart, { props: { data: [1, 2, 3] } })

    const option = setOption.mock.calls[0][0] as CapturedOption
    expect(areaColorsOf(option)).toEqual([
      'rgba(13, 148, 136, 0.25)',
      'rgba(13, 148, 136, 0.02)'
    ])
  })

  it('still renders with the fallback color when the CSS variable is unreadable', () => {
    // 变量未定义 —— 等价于元素尚未挂载 / 样式表未加载
    expect(() => mount(SparklineChart, { props: { data: [1, 2, 3] } })).not.toThrow()

    const option = setOption.mock.calls[0][0] as CapturedOption
    expect(option.series[0].lineStyle.color).toBe('#5EEAD4')
    expect(areaColorsOf(option)).toEqual(['rgba(94, 234, 212, 0.25)', 'rgba(94, 234, 212, 0.02)'])
  })

  it('honours an explicit color prop (hex)', () => {
    mount(SparklineChart, { props: { data: [1, 2, 3], color: '#f97316' } })

    const option = setOption.mock.calls[0][0] as CapturedOption
    expect(option.series[0].lineStyle.color).toBe('#f97316')
    expect(areaColorsOf(option)[0]).toBe('rgba(249, 115, 22, 0.25)')
  })

  it('honours an explicit color prop given as a CSS var', () => {
    document.documentElement.style.setProperty('--accent-warm', '#FB923C')

    mount(SparklineChart, { props: { data: [1, 2, 3], color: 'var(--accent-warm)' } })

    const option = setOption.mock.calls[0][0] as CapturedOption
    expect(option.series[0].lineStyle.color).toBe('#FB923C')
    expect(areaColorsOf(option)[0]).not.toContain('var(')
  })
})
