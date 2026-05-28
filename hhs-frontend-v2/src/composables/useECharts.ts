import { onMounted, onUnmounted, watch, type Ref, shallowRef } from 'vue'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { useThemeStore } from '@/stores/theme'
import { registerHhsTheme } from '@/utils/echarts-theme'

echarts.use([CanvasRenderer])

const THEME_NAME = 'hhs'

/**
 * Composable that encapsulates ECharts lifecycle: init, dispose, resize, theme switching.
 *
 * @param containerRef - ref to the DOM container element
 * @param buildOption - function that returns the ECharts option for this chart
 */
export function useECharts(
  containerRef: Ref<HTMLElement | undefined>,
  buildOption: () => echarts.EChartsCoreOption
) {
  const chartInstance = shallowRef<echarts.ECharts | null>(null)
  const themeStore = useThemeStore()

  const init = () => {
    if (!containerRef.value) return
    if (chartInstance.value) {
      chartInstance.value.dispose()
    }
    registerHhsTheme()
    chartInstance.value = echarts.init(containerRef.value, THEME_NAME)
    chartInstance.value.setOption(buildOption())
  }

  const updateOption = (option?: echarts.EChartsCoreOption) => {
    if (!chartInstance.value) return
    chartInstance.value.setOption(option ?? buildOption(), true)
  }

  const handleResize = () => {
    chartInstance.value?.resize()
  }

  // Watch theme changes and re-apply
  watch(
    () => themeStore.effectiveTheme,
    () => {
      if (chartInstance.value) {
        registerHhsTheme()
        const container = containerRef.value
        if (container) {
          chartInstance.value.dispose()
          chartInstance.value = echarts.init(container, THEME_NAME)
          chartInstance.value.setOption(buildOption())
        }
      }
    }
  )

  onMounted(() => {
    window.addEventListener('resize', handleResize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
    chartInstance.value?.dispose()
    chartInstance.value = null
  })

  return {
    chartInstance,
    init,
    updateOption,
    handleResize,
  }
}
