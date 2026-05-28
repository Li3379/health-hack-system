import * as echarts from 'echarts/core'
import type { EChartsCoreOption } from 'echarts/core'

const THEME_NAME = 'hhs'

/**
 * Read a CSS custom property value from the document root.
 */
function getCSSVar(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim()
}

/**
 * Register the HHS ECharts theme from current CSS variables.
 * Safe to call multiple times — re-registers with latest colors.
 */
export function registerHhsTheme() {
  echarts.registerTheme(THEME_NAME, buildHhsTheme())
}

/**
 * Build an ECharts theme config from current CSS custom properties.
 * Call this at theme registration time (and on theme change) so the
 * chart colors always match the active CSS theme.
 */
export function buildHhsTheme(): EChartsCoreOption {
  return {
    color: [
      getCSSVar('--accent-cool') || '#5EEAD4',
      getCSSVar('--accent-warm') || '#FB923C',
      getCSSVar('--color-health-excellent') || '#22c55e',
      getCSSVar('--color-health-good') || '#84cc16',
      getCSSVar('--color-health-fair') || '#eab308',
      getCSSVar('--color-health-poor') || '#ef4444',
    ],
    backgroundColor: 'transparent',
    textStyle: {
      color: getCSSVar('--text-2') || '#c6c9d2',
    },
    title: {
      textStyle: {
        color: getCSSVar('--text-1') || '#ebecef',
      },
      subtextStyle: {
        color: getCSSVar('--text-3') || '#8d909c',
      },
    },
    line: {
      itemStyle: { borderWidth: 2 },
      lineStyle: { width: 2 },
      symbolSize: 4,
      symbol: 'circle',
      smooth: true,
    },
    radar: {
      axisName: {
        color: getCSSVar('--text-2') || '#c6c9d2',
      },
      splitLine: {
        lineStyle: {
          color: getCSSVar('--border') || 'rgba(67, 70, 81, 0.5)',
        },
      },
      splitArea: {
        areaStyle: {
          color: [
            'transparent',
            getCSSVar('--surface-2') || '#171921',
          ],
        },
      },
      axisLine: {
        lineStyle: {
          color: getCSSVar('--border') || 'rgba(67, 70, 81, 0.5)',
        },
      },
    },
    gauge: {
      axisLine: {
        lineStyle: {
          color: [
            [0.4, getCSSVar('--color-health-poor') || '#ef4444'],
            [0.6, getCSSVar('--color-health-fair') || '#eab308'],
            [0.8, getCSSVar('--color-health-good') || '#84cc16'],
            [1, getCSSVar('--color-health-excellent') || '#22c55e'],
          ],
        },
      },
      axisTick: {
        lineStyle: {
          color: getCSSVar('--text-4') || '#60636f',
        },
      },
      splitLine: {
        lineStyle: {
          color: getCSSVar('--text-4') || '#60636f',
        },
      },
      axisLabel: {
        color: getCSSVar('--text-3') || '#8d909c',
      },
      pointer: {
        itemStyle: {
          color: getCSSVar('--accent-cool') || '#5EEAD4',
        },
      },
      title: {
        color: getCSSVar('--text-2') || '#c6c9d2',
      },
      detail: {
        color: getCSSVar('--text-1') || '#ebecef',
      },
    },
    tooltip: {
      backgroundColor: getCSSVar('--surface-2') || '#171921',
      borderColor: getCSSVar('--border') || 'rgba(67, 70, 81, 0.5)',
      textStyle: {
        color: getCSSVar('--text-1') || '#ebecef',
      },
    },
    grid: {
      borderColor: getCSSVar('--border') || 'rgba(67, 70, 81, 0.5)',
    },
    categoryAxis: {
      axisLine: {
        lineStyle: {
          color: getCSSVar('--border') || 'rgba(67, 70, 81, 0.5)',
        },
      },
      axisTick: {
        lineStyle: {
          color: getCSSVar('--text-4') || '#60636f',
        },
      },
      axisLabel: {
        color: getCSSVar('--text-3') || '#8d909c',
      },
      splitLine: {
        lineStyle: {
          color: getCSSVar('--border') || 'rgba(67, 70, 81, 0.5)',
        },
      },
    },
    valueAxis: {
      axisLine: {
        lineStyle: {
          color: getCSSVar('--border') || 'rgba(67, 70, 81, 0.5)',
        },
      },
      axisTick: {
        lineStyle: {
          color: getCSSVar('--text-4') || '#60636f',
        },
      },
      axisLabel: {
        color: getCSSVar('--text-3') || '#8d909c',
      },
      splitLine: {
        lineStyle: {
          color: getCSSVar('--border') || 'rgba(67, 70, 81, 0.5)',
        },
      },
    },
  }
}
