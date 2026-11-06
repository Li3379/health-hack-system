import { describe, it, expect } from 'vitest'
import {
  ApiError,
  normalizeApiError,
  isCancel,
  CANCEL_SENTINELS,
  type ErrorKind
} from '@/utils/appError'

/** 造一个原生 Axios 错误形状（无 response 之外的任何附加能力） */
function axiosError(status: number, data: unknown, extra?: Record<string, unknown>) {
  return { response: { status, data }, ...extra }
}

describe('appError.normalizeApiError', () => {
  describe('第 0 步：ApiError 幂等直通（反回归锚点）', () => {
    const kinds: ErrorKind[] = ['validation', 'business', 'auth', 'network', 'server', 'cancel']

    for (const kind of kinds) {
      it(`returns the same reference for kind="${kind}"`, () => {
        const original = new ApiError(
          `原始消息-${kind}`,
          kind,
          kind === 'auth' ? 401 : 400,
          kind === 'business' ? 'resource.exists' : undefined,
          kind === 'validation' ? { metricKey: '指标类型不能为空' } : {}
        )

        const normalized = normalizeApiError(original)

        // toBe 而非 toEqual：必须是同一引用，证明没有重新分类/重建对象
        expect(normalized).toBe(original)
        expect(normalized.kind).toBe(kind)
        expect(normalized.message).toBe(`原始消息-${kind}`)
        expect(normalized.code).toBe(original.code)
        expect(normalized.status).toBe(original.status)
        expect(normalized.fieldErrors).toEqual(original.fieldErrors)
      })
    }

    it('keeps cancel as cancel, never downgraded to network', () => {
      const original = new ApiError('', 'cancel')
      const normalized = normalizeApiError(original)

      expect(normalized.kind).toBe('cancel')
      expect(normalized.kind).not.toBe('network')
      expect(normalized).toBe(original)
    })

    it('preserves D-08 errorCode so resource.exists survives re-normalization', () => {
      const original = new ApiError(
        '该指标已存在阈值，请直接编辑',
        'business',
        200,
        'resource.exists'
      )
      const normalized = normalizeApiError(original)

      expect(normalized.kind).toBe('business')
      expect(normalized.code).toBe('resource.exists')
      expect(normalized.formField).toBeUndefined()
    })
  })

  describe('取消五形态（D-12：零提示）', () => {
    const forms: Array<[string, unknown]> = [
      ['字符串 cancel', 'cancel'],
      ['字符串 close', 'close'],
      ['CanceledError', { name: 'CanceledError' }],
      ['AbortError', { name: 'AbortError' }],
      ['ERR_CANCELED', { code: 'ERR_CANCELED' }]
    ]

    for (const [label, raw] of forms) {
      it(`classifies ${label} as cancel with empty message`, () => {
        const err = normalizeApiError(raw)
        expect(err.kind).toBe('cancel')
        expect(err.message).toBe('')
      })
    }

    it('exposes the two string sentinels', () => {
      expect(CANCEL_SENTINELS).toEqual(['cancel', 'close'])
      expect(isCancel('cancel')).toBe(true)
      expect(isCancel('close')).toBe(true)
      expect(isCancel('retry')).toBe(false)
      expect(isCancel(null)).toBe(false)
      expect(isCancel(undefined)).toBe(false)
    })
  })

  describe('校验通道（HTTP 400 + fieldErrors）', () => {
    const raw = axiosError(400, {
      code: 400,
      message: '参数校验失败',
      errorCode: 'validation.invalid_parameter',
      fieldErrors: [
        { field: 'metricKey', message: '指标类型不能为空' },
        { field: 'value', message: '指标数值不能为空' }
      ]
    })

    it('classifies as validation and maps fieldErrors', () => {
      const err = normalizeApiError(raw)
      expect(err.kind).toBe('validation')
      expect(err.status).toBe(400)
      expect(err.code).toBe('validation.invalid_parameter')
      expect(err.fieldErrors.metricKey).toBe('指标类型不能为空')
      expect(err.fieldErrors.value).toBe('指标数值不能为空')
    })

    it('uses the human-readable summary from the backend', () => {
      expect(normalizeApiError(raw).message).toBe('参数校验失败')
    })

    it('merges duplicate fields with a full-width semicolon', () => {
      const merged = normalizeApiError(
        axiosError(400, {
          code: 400,
          message: '参数校验失败',
          fieldErrors: [
            { field: 'warningHigh', message: '必须大于收缩压下限' },
            { field: 'warningHigh', message: '必须小于危急上限' }
          ]
        })
      )

      expect(merged.fieldErrors.warningHigh).toBe('必须大于收缩压下限；必须小于危急上限')
    })
  })

  describe('业务通道（HTTP 200 + 信封 code !== 200）', () => {
    it('classifies envelope code 409 as business with backend message', () => {
      const err = normalizeApiError(
        axiosError(200, {
          code: 409,
          message: '该指标已存在阈值，请直接编辑',
          errorCode: 'resource.exists'
        })
      )

      expect(err.kind).toBe('business')
      expect(err.code).toBe('resource.exists')
      expect(err.message).toBe('该指标已存在阈值，请直接编辑')
      expect(err.retryable).toBe(false)
    })

    // 线上真实形态：BusinessException 经 parseCodeToInt 回退后信封 code 恒为 500。
    // 这条是本计划的反回归锚点 —— 把 5xx 判定放到信封业务码之前就会失败。
    it('keeps envelope code 500 as business, not server, and preserves the Chinese message', () => {
      const err = normalizeApiError(
        axiosError(200, {
          code: 500,
          message: '该指标已存在阈值，请直接编辑',
          errorCode: 'resource.exists'
        })
      )

      expect(err.kind).toBe('business')
      expect(err.kind).not.toBe('server')
      expect(err.retryable).toBe(false)
      expect(err.message).toBe('该指标已存在阈值，请直接编辑')
      expect(err.message).not.toBe('服务暂时不可用，请稍后重试')
      expect(err.code).toBe('resource.exists')
    })

    it('records the既定 behavior for generic backend 500 delivered over an HTTP-200 envelope', () => {
      const err = normalizeApiError(
        axiosError(200, {
          code: 500,
          message: '服务暂时不可用，请稍后重试',
          errorCode: 'system.error'
        })
      )

      expect(err.kind).toBe('business')
      expect(err.message).toBe('服务暂时不可用，请稍后重试')
      expect(err.code).toBe('system.error')
    })
  })

  describe('真实 HTTP 5xx（信封无 code）', () => {
    it('classifies as server, retryable, with fixed Chinese message', () => {
      const err = normalizeApiError(axiosError(500, { message: 'Internal Server Error' }))

      expect(err.kind).toBe('server')
      expect(err.retryable).toBe(true)
      expect(err.message).toBe('服务暂时不可用，请稍后重试')
      expect(err.message).not.toContain('Internal Server Error')
    })
  })

  describe('断网', () => {
    it('classifies a response-less error as network and retryable', () => {
      const err = normalizeApiError(new Error('Network Error'))

      expect(err.kind).toBe('network')
      expect(err.retryable).toBe(true)
      expect(err.message).toBe('网络连接异常，请检查网络后重试')
    })
  })

  describe('401（必须早于信封业务码）', () => {
    // 11-01 的真实形态：HTTP 401 + 信封 code 401（!== 200）
    it('classifies 401 with an envelope 401 body as auth, not business', () => {
      const err = normalizeApiError(
        axiosError(401, {
          code: 401,
          message: '登录已过期，请重新登录',
          errorCode: 'auth.unauthorized'
        })
      )

      expect(err.kind).toBe('auth')
      expect(err.kind).not.toBe('business')
      expect(err.status).toBe(401)
    })

    it('classifies a bare 401 without envelope as auth', () => {
      expect(normalizeApiError(axiosError(401, {})).kind).toBe('auth')
    })
  })

  describe('兜底与字符串信封码', () => {
    it('does not treat a string envelope code as business (typeof number guard)', () => {
      const err = normalizeApiError(
        axiosError(200, {
          code: 'AUTHENTICATION_REQUIRED',
          message: '登录已过期，请重新登录'
        })
      )

      expect(err.kind).toBe('server')
      expect(err.message).toBe('登录已过期，请重新登录')
    })

    it('falls back to a Chinese message when a response exists but carries nothing usable', () => {
      const err = normalizeApiError(axiosError(400, {}))
      expect(err.kind).toBe('server')
      expect(err.status).toBe(400)
      expect(err.message).toBe('服务暂时不可用，请稍后重试')
    })
  })

  describe('文案语言门禁（D-05）', () => {
    it('never surfaces an English sentence except when relaying backend business text', () => {
      const nonBusinessMessages = [
        normalizeApiError('cancel').message,
        normalizeApiError(
          axiosError(400, {
            message: '参数校验失败',
            fieldErrors: [{ field: 'a', message: '必填' }]
          })
        ).message,
        normalizeApiError(axiosError(401, {})).message,
        normalizeApiError(new Error('Network Error')).message,
        normalizeApiError(axiosError(503, {})).message,
        normalizeApiError({}).message
      ]

      for (const msg of nonBusinessMessages) {
        if (msg === '') continue
        expect(msg).not.toMatch(/Error|Failed|Invalid|Exception/)
      }
    })
  })
})
