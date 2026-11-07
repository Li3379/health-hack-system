import { describe, it, expect, afterEach } from 'vitest'
import { ApiError } from '@/utils/appError'
import { useFormServerErrors } from '@/composables/useFormServerErrors'

// 本 composable 无 store 依赖，因此无需 setActivePinia。
function validationError(fieldErrors: Record<string, string>, formField?: string): ApiError {
  return new ApiError(
    '参数校验失败',
    'validation',
    400,
    'validation.invalid_parameter',
    fieldErrors,
    formField
  )
}

describe('useFormServerErrors', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('projects a two-field validation error into inline map and Chinese summary items', () => {
    const { fieldErrors, summaryItems, apply } = useFormServerErrors()

    apply(
      validationError({
        metricKey: '指标类型不能为空',
        value: '指标数值不能为空'
      })
    )

    expect(fieldErrors.value.metricKey).toBe('指标类型不能为空')
    expect(fieldErrors.value.value).toBe('指标数值不能为空')
    expect(summaryItems.value).toHaveLength(2)
    expect(summaryItems.value[0].label).toBe('指标类型')
    expect(summaryItems.value[0].message).toBe('指标类型不能为空')
    expect(summaryItems.value[1].label).toBe('指标数值')
  })

  it('setErrors projects client-side el-form validation failures into the same summary channel', () => {
    const { fieldErrors, summaryItems, setErrors, clearField } = useFormServerErrors()

    setErrors({ gender: '请选择性别', birthDate: '请选择出生日期' })

    expect(fieldErrors.value.gender).toBe('请选择性别')
    expect(summaryItems.value).toHaveLength(2)
    expect(summaryItems.value.map(item => item.field)).toEqual(['gender', 'birthDate'])

    // 修正一项后汇总只剩另一项；全部修正后汇总整体消失（GOV.UK 双呈现）。
    clearField('gender')
    expect(summaryItems.value.map(item => item.field)).toEqual(['birthDate'])
    clearField('birthDate')
    expect(summaryItems.value).toHaveLength(0)
  })

  it('setErrors copies the input so later mutation of the caller object cannot leak in', () => {
    const { fieldErrors, setErrors } = useFormServerErrors()
    const source: Record<string, string> = { gender: '请选择性别' }

    setErrors(source)
    source.gender = '被外部改掉'

    expect(fieldErrors.value.gender).toBe('请选择性别')
  })

  it('renders an empty label for unknown fields while keeping the backend message', () => {
    const { summaryItems, apply } = useFormServerErrors()

    apply(validationError({ unknownField: '该值超出允许范围' }))

    expect(summaryItems.value).toHaveLength(1)
    expect(summaryItems.value[0].label).toBe('')
    expect(summaryItems.value[0].message).toBe('该值超出允许范围')
    // 绝不把原始英文/驼峰字段名露给用户（D-15）。
    expect(summaryItems.value[0].label).not.toContain('unknownField')
  })

  it('moves a generic value error onto the caller-supplied formField (blood pressure dual request)', () => {
    // 这条用例同时是契约文档：formField 由调用点在 catch 里写入，
    // normalizeApiError 不会产出它 —— 这里证明的是 apply() 的消费能力。
    const { fieldErrors, apply } = useFormServerErrors()

    apply(validationError({ value: '收缩压超出范围' }, 'diastolicValue'))

    expect(fieldErrors.value.diastolicValue).toBe('收缩压超出范围')
    expect(fieldErrors.value.value).toBeUndefined()
  })

  it('does not remap the generic value error when formField is absent', () => {
    const { fieldErrors, apply } = useFormServerErrors()

    apply(validationError({ value: '收缩压超出范围' }))

    expect(fieldErrors.value.value).toBe('收缩压超出范围')
    expect(fieldErrors.value.diastolicValue).toBeUndefined()
  })

  it('ignores non-validation errors — presentation belongs to showError', () => {
    const { fieldErrors, summaryItems, apply } = useFormServerErrors()

    apply(new ApiError('该指标已有阈值', 'business', 200, 'resource.exists'))
    expect(fieldErrors.value).toEqual({})
    expect(summaryItems.value).toHaveLength(0)

    apply(new ApiError('网络连接异常', 'network'))
    expect(fieldErrors.value).toEqual({})

    apply(new ApiError('登录已过期', 'auth', 401))
    expect(fieldErrors.value).toEqual({})
  })

  it('clearField removes one row, and the last removal empties the summary', () => {
    const { fieldErrors, summaryItems, apply, clearField } = useFormServerErrors()

    apply(
      validationError({
        metricKey: '指标类型不能为空',
        value: '指标数值不能为空'
      })
    )
    expect(summaryItems.value).toHaveLength(2)

    clearField('metricKey')
    expect(summaryItems.value).toHaveLength(1)
    expect(fieldErrors.value.metricKey).toBeUndefined()
    expect(fieldErrors.value.value).toBe('指标数值不能为空')

    clearField('value')
    expect(summaryItems.value).toHaveLength(0)
    expect(fieldErrors.value).toEqual({})
  })

  it('clearField on an unknown field is a no-op', () => {
    const { summaryItems, apply, clearField } = useFormServerErrors()

    apply(validationError({ metricKey: '指标类型不能为空' }))
    clearField('notPresent')

    expect(summaryItems.value).toHaveLength(1)
  })

  it('reset clears every field error', () => {
    const { fieldErrors, summaryItems, apply, reset } = useFormServerErrors()

    apply(validationError({ metricKey: '指标类型不能为空', value: '指标数值不能为空' }))
    reset()

    expect(fieldErrors.value).toEqual({})
    expect(summaryItems.value).toHaveLength(0)
  })

  it('sorts summary items by the supplied fieldOrder, keeping unknown fields last', () => {
    const { summaryItems, apply } = useFormServerErrors({
      fieldOrder: ['value', 'metricKey']
    })

    apply(
      validationError({
        metricKey: '指标类型不能为空',
        value: '指标数值不能为空',
        notes: '备注过长'
      })
    )

    expect(summaryItems.value.map(item => item.field)).toEqual(['value', 'metricKey', 'notes'])
  })

  it('keeps backend insertion order when no fieldOrder is supplied', () => {
    const { summaryItems, apply } = useFormServerErrors()

    apply(validationError({ value: '指标数值不能为空', metricKey: '指标类型不能为空' }))

    expect(summaryItems.value.map(item => item.field)).toEqual(['value', 'metricKey'])
  })

  it('focusField focuses the control inside the matching data-field container', () => {
    document.body.innerHTML = '<div data-field="metricKey"><input id="probe"></div>'

    const { focusField } = useFormServerErrors()
    focusField('metricKey')

    expect(document.activeElement?.id).toBe('probe')
  })

  it('focusField silently returns when the field or container is missing', () => {
    document.body.innerHTML = '<div data-field="other"><input id="probe"></div>'

    const { focusField } = useFormServerErrors()

    expect(() => focusField('metricKey')).not.toThrow()
    expect(() => focusField('')).not.toThrow()
    expect(document.activeElement?.id).not.toBe('probe')
  })
})
