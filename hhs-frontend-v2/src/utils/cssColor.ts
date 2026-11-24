/**
 * Canvas 与 ECharts 只认真实颜色值，不认 CSS 变量：
 * `addColorStop(0, 'var(--accent-cool)40')` 会直接抛
 * `SyntaxError: could not be parsed as a color`，整个图表不渲染。
 * 因此绘图前必须把 `var(--x)` 解析成当前主题下的实际颜色。
 */

/** 变量读不到时的兜底色（暗色主题的 --accent-cool）。 */
export const FALLBACK_CHART_COLOR = '#5EEAD4'

const VAR_PATTERN = /^var\(\s*(--[\w-]+)\s*(?:,[^)]*)?\)$/

/**
 * 把 `var(--x)` 解析成当前主题下的实际颜色值。
 *
 * - 非 `var()` 的入参原样返回（调用方直接传 #hex / rgba 时不做多余处理）。
 * - 元素未挂载、变量未定义、或运行环境没有 DOM 时返回 `fallback`，**不抛异常**。
 */
export function resolveCssColor(value: string, fallback: string = FALLBACK_CHART_COLOR): string {
  const input = value.trim()
  if (!input) return fallback

  const match = VAR_PATTERN.exec(input)
  if (!match) return input
  if (typeof document === 'undefined' || typeof getComputedStyle !== 'function') return fallback

  try {
    return getComputedStyle(document.documentElement).getPropertyValue(match[1]).trim() || fallback
  } catch {
    return fallback
  }
}

/**
 * 给颜色加透明度，返回 canvas / ECharts 都能解析的 `rgba(...)`。
 *
 * 支持 `#rgb`、`#rrggbb`、`rgb()`、`rgba()`；无法识别的写法原样返回
 * （实色、不透明，但不会让渐变报错），保证图表始终能画出来。
 */
export function toRgba(color: string, alpha: number): string {
  const input = color.trim()

  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(input)
  if (hex) {
    const digits =
      hex[1].length === 3
        ? hex[1]
            .split('')
            .map(c => c + c)
            .join('')
        : hex[1]
    const r = parseInt(digits.slice(0, 2), 16)
    const g = parseInt(digits.slice(2, 4), 16)
    const b = parseInt(digits.slice(4, 6), 16)
    return `rgba(${r}, ${g}, ${b}, ${alpha})`
  }

  const fn = /^rgba?\(([^)]+)\)$/i.exec(input)
  if (fn) {
    const parts = fn[1].split(/[\s,/]+/).filter(Boolean)
    if (parts.length >= 3) return `rgba(${parts[0]}, ${parts[1]}, ${parts[2]}, ${alpha})`
  }

  return input
}
