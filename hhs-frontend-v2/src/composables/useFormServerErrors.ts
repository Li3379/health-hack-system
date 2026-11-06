// 服务端字段错误投影：把 ApiError 的 fieldErrors 变成 el-form-item :error 可直接消费的形状。
//
// 交付边界（D-01 / D-04）：
// - apply() 只处理 kind === 'validation'；其他 kind 的呈现权在 showError。
// - 本模块只做投影与生命周期，不弹 toast、不碰任何 store、不清空用户输入。
// - formField 由调用方供给（ARCHITECTURE Flow 1 第 6 条），normalizeApiError 不会写它；
//   本模块只负责在该字段存在时消费它。

import { computed, ref, type ComputedRef, type Ref } from 'vue'
import type { ApiError } from '@/utils/appError'
import { fieldLabel } from '@/utils/errorMessages'

/** 汇总块的一条：字段名、中文标签、后端消息。 */
export interface SummaryItem {
  field: string
  label: string
  message: string
}

export interface UseFormServerErrorsOptions {
  /**
   * 表单字段顺序（用于汇总排序，GOV.UK: 汇总顺序必须与页面一致）。
   * 缺省时保持后端下发的键顺序。
   */
  fieldOrder?: string[]
}

/**
 * 服务端字段错误投影 composable（D-01 / D-04）。
 *
 * 消费契约（Phase 12/14）：每个参与服务端校验错误的 el-form-item 需同时具备
 * `prop`、`:data-field="prop"`、`:error="fieldErrors[prop]"`；输入事件绑定
 * `clearField(prop)`；每次提交前调用 `reset()`。弹窗与全页表单同一套机制。
 */
export function useFormServerErrors(options: UseFormServerErrorsOptions = {}): {
  fieldErrors: Ref<Record<string, string>>
  summaryItems: ComputedRef<SummaryItem[]>
  apply: (err: ApiError) => void
  clearField: (field: string) => void
  reset: () => void
  focusField: (field: string) => void
} {
  const fieldErrors = ref<Record<string, string>>({})

  // 字段顺序：fieldOrder 命中项按声明顺序，未命中项排在末尾且保持相对顺序。
  const summaryItems = computed<SummaryItem[]>(() => {
    const items: SummaryItem[] = Object.entries(fieldErrors.value).map(([field, message]) => ({
      field,
      message,
      label: fieldLabel(field)
    }))

    const order = options.fieldOrder
    if (!order || order.length === 0) return items

    const rank = (field: string): number => {
      const index = order.indexOf(field)
      return index === -1 ? Number.MAX_SAFE_INTEGER : index
    }

    return items
      .map((item, index) => ({ item, index }))
      .sort((a, b) => {
        const byOrder = rank(a.item.field) - rank(b.item.field)
        return byOrder !== 0 ? byOrder : a.index - b.index
      })
      .map(({ item }) => item)
  })

  /**
   * 投影一次校验失败。
   * - 仅 kind === 'validation' 时动作；其余 kind 直接返回（呈现权在 showError）。
   * - 血压双请求：调用方标注了 formField 时，把后端通用的 `value` 错误改投到该字段。
   * - 绝不清空用户输入、绝不调用表单 reset。
   */
  function apply(err: ApiError): void {
    // 非校验错误在这里一律不动作：呈现权属于 showError（D-02 的通道划分）。
    if (err.kind === 'validation') {
      const next: Record<string, string> = { ...err.fieldErrors }
      if (err.formField && next.value !== undefined) {
        next[err.formField] = next.value
        delete next.value
      }
      fieldErrors.value = next
    }
  }

  /** 只移除单个字段；最后一条清除后汇总块由 v-if 自动卸载。 */
  function clearField(field: string): void {
    if (!(field in fieldErrors.value)) return
    const next = { ...fieldErrors.value }
    delete next[field]
    fieldErrors.value = next
  }

  /** 清空全部（每次提交前调用，避免陈旧汇总带入下一次尝试）。 */
  function reset(): void {
    fieldErrors.value = {}
  }

  /**
   * 把焦点移到出错的控件上（汇总条目点击时调用）。
   * 目标由 `:data-field="prop"` 标记，不依赖 Element Plus 内部结构。
   * 选择器构造做了转义，非法或未命中时静默返回（T-11-15）。
   */
  function focusField(field: string): void {
    if (!field) return

    // 字段名本是后端 DTO 常量，但控制字符（换行、制表符）仍能让 querySelector 抛
    // DOMException。优先用 CSS.escape（完整转义），不可用时退回双引号/反斜杠转义；
    // 任何非法选择器都必须静默返回，不得让异常冒泡出点击处理器（T-11-15）。
    const escaped =
      typeof CSS !== 'undefined' && typeof CSS.escape === 'function'
        ? CSS.escape(field)
        : field.replace(/["\\]/g, '\\$&')
    let container: HTMLElement | null = null
    try {
      container = document.querySelector<HTMLElement>(`[data-field="${escaped}"]`)
    } catch {
      return
    }
    if (!container) return

    const control = container.querySelector<HTMLElement>(
      'input, textarea, .el-select__wrapper, .el-rate'
    )
    if (!control) return

    control.focus()
    // 只用原生 scrollIntoView，不引入 GSAP（减少动效）。
    if (typeof control.scrollIntoView === 'function') {
      control.scrollIntoView({ block: 'center' })
    }
  }

  return {
    fieldErrors,
    summaryItems,
    apply,
    clearField,
    reset,
    focusField
  }
}
