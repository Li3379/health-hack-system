import { describe, it, expect, afterEach } from 'vitest'
import { resolveCssColor, toRgba, FALLBACK_CHART_COLOR } from '@/utils/cssColor'

/**
 * SparklineChart 的 canvas 渐变回归证明。
 *
 * 缺陷：`(props.color || 'var(--accent-cool)') + '40'` 把 CSS 变量直接拼上
 * 十六进制 alpha，得到 `var(--accent-cool)40`。canvas 的 `addColorStop`
 * 不解析 CSS 变量，抛 SyntaxError，整张折线图不渲染。
 *
 * 这里锁定两条不变量：
 * 1. `var(--x)` 一定被解析成具体颜色，永不原样流到画布；
 * 2. 变量读不到（元素未挂载 / 无 DOM）时返回兜底色，而不是抛异常。
 */

const ACCENT_COOL = '--accent-cool'

afterEach(() => {
  document.documentElement.style.removeProperty(ACCENT_COOL)
})

describe('resolveCssColor', () => {
  it('resolves var(--x) to the computed value of the current theme', () => {
    document.documentElement.style.setProperty(ACCENT_COOL, '#0d9488')

    expect(resolveCssColor('var(--accent-cool)')).toBe('#0d9488')
  })

  it('follows the theme: same var yields the other theme value after the variable changes', () => {
    document.documentElement.style.setProperty(ACCENT_COOL, '#5EEAD4')
    expect(resolveCssColor('var(--accent-cool)')).toBe('#5EEAD4')

    // 切到亮色主题 —— 变量值变了，解析结果必须跟着变
    document.documentElement.style.setProperty(ACCENT_COOL, '#0d9488')
    expect(resolveCssColor('var(--accent-cool)')).toBe('#0d9488')
  })

  it('returns the fallback color when the variable is not defined — never throws', () => {
    expect(() => resolveCssColor('var(--accent-cool)')).not.toThrow()
    expect(resolveCssColor('var(--accent-cool)')).toBe(FALLBACK_CHART_COLOR)
  })

  it('honours a custom fallback', () => {
    expect(resolveCssColor('var(--not-defined-anywhere)', '#123456')).toBe('#123456')
  })

  it('passes non-var colors through untouched', () => {
    expect(resolveCssColor('#f97316')).toBe('#f97316')
    expect(resolveCssColor('rgba(249, 115, 22, 0.3)')).toBe('rgba(249, 115, 22, 0.3)')
  })

  it('falls back instead of throwing on an empty value', () => {
    expect(resolveCssColor('')).toBe(FALLBACK_CHART_COLOR)
  })
})

describe('toRgba', () => {
  it('converts 6-digit hex to rgba', () => {
    expect(toRgba('#5EEAD4', 0.25)).toBe('rgba(94, 234, 212, 0.25)')
  })

  it('expands 3-digit hex', () => {
    expect(toRgba('#fff', 0.1)).toBe('rgba(255, 255, 255, 0.1)')
  })

  it('recomputes alpha on an existing rgb()/rgba() value', () => {
    expect(toRgba('rgba(13, 148, 136, 0.5)', 0.25)).toBe('rgba(13, 148, 136, 0.25)')
    expect(toRgba('rgb(13 148 136)', 0.02)).toBe('rgba(13, 148, 136, 0.02)')
  })

  it('never returns something canvas cannot parse', () => {
    // 无法识别的写法原样返回：实色退化，但不会让 addColorStop 抛异常
    expect(toRgba('currentColor', 0.25)).toBe('currentColor')
  })
})

describe('the exact broken expression is gone', () => {
  it('var(--accent-cool) + "40" would produce an unparseable color; the pipeline does not', () => {
    document.documentElement.style.setProperty(ACCENT_COOL, '#5EEAD4')

    // 缺陷写法
    const before = 'var(--accent-cool)' + '40'
    expect(before).toBe('var(--accent-cool)40')

    // 修复后的写法
    const after = toRgba(resolveCssColor('var(--accent-cool)'), 0.25)
    expect(after).toBe('rgba(94, 234, 212, 0.25)')
    expect(after).not.toContain('var(')
  })
})
