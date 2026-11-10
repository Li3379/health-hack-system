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
  /**
   * 覆盖全局 `FIELD_LABELS` 的字段标签。
   *
   * 用于同一字段名在不同页面语义不同的场景（如 goals 的 `title` 是「目标标题」而非「标题」，
   * `value` 是「当前值」而非「指标数值」）。优先级：本表 → 全局表 → 空串。
   * 空串时汇总条目只渲染后端消息，绝不回退成字段名（D-15）。
   */
  labels?: Record<string, string>
}

/**
 * 把 Element Plus 客户端校验的 `invalidFields` 摊平成 `字段 → 首条中文消息`。
 *
 * 与服务端 `apply()` 投影出的 `fieldErrors` 同一形状，因此客户端与服务端校验
 * 共用一份汇总通道（D-01 的 GOV.UK 双呈现），不需要第二套渲染分支。
 * 纯函数：不读 store、不写回表单 model、不改动入参。
 *
 * 放在模块级而不是 composable 返回值里 —— 它是无状态的，且在 `validate` 回调内使用，
 * 不需要绑到某个表单实例。
 */
export function collectInvalidFieldMessages(
  invalidFields: Record<string, Array<{ message?: string }>> | undefined
): Record<string, string> {
  const next: Record<string, string> = {}
  for (const [field, errors] of Object.entries(invalidFields ?? {})) {
    const message = errors?.[0]?.message
    if (message) next[field] = message
  }
  return next
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
  setErrors: (next: Record<string, string>) => void
  clearField: (field: string) => void
  reset: () => void
  focusField: (field: string) => void
} {
  const fieldErrors = ref<Record<string, string>>({})

  // 字段顺序：fieldOrder 命中项按声明顺序，未命中项排在末尾且保持相对顺序。
  // 标签优先级：调用方 labels → 全局 FIELD_LABELS → 空串。
  // 内联错误（:error="fieldErrors[prop]"）刻意只显示后端消息，不拼标签 —— 标签是汇总块的职责。
  const summaryItems = computed<SummaryItem[]>(() => {
    const items: SummaryItem[] = Object.entries(fieldErrors.value).map(([field, message]) => ({
      field,
      message,
      label: options.labels?.[field] ?? fieldLabel(field)
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

  /**
   * 直接投影一批字段错误。
   *
   * 用于客户端 `el-form` 校验失败（Element Plus 只在字段下方内联提示，没有汇总）。
   * 与服务端 apply() 共用同一份 `fieldErrors`，因此汇总块与内联提示走同一通道，
   * 满足 D-01 的 GOV.UK 双呈现要求，且不产生 toast。
   */
  function setErrors(next: Record<string, string>): void {
    fieldErrors.value = { ...next }
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
    setErrors,
    clearField,
    reset,
    focusField
  }
}
