// 前端唯一错误模型：把任何异形失败翻译成同一个 ApiError。
// 本模块是纯函数模块 —— 不得 import pinia / store / vue-router / element-plus，
// 否则会与 stores/auth.ts 形成循环依赖，也会让"翻译"与"展示"重新耦合。

export type ErrorKind =
  | 'validation'
  | 'business'
  | 'auth'
  | 'network'
  | 'server'
  | 'cancel'
  | 'unknown'

export class ApiError extends Error {
  constructor(
    message: string,
    readonly kind: ErrorKind,
    readonly status?: number,
    readonly code?: string,
    readonly fieldErrors: Record<string, string> = {},
    readonly formField?: string,
    readonly retryable = false
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

// D-12: 取消的五种形态统一识别并完全忽略，不产生任何提示。
export const CANCEL_SENTINELS = ['cancel', 'close'] as const

export function isCancel(raw: unknown): boolean {
  if (typeof raw === 'string') {
    return (CANCEL_SENTINELS as readonly string[]).includes(raw)
  }
  const candidate = raw as { name?: string; code?: string } | null | undefined
  return (
    candidate?.name === 'CanceledError' ||
    candidate?.name === 'AbortError' ||
    candidate?.code === 'ERR_CANCELED'
  )
}

/**
 * 把任意失败翻译成 ApiError。
 *
 * 分类顺序即契约，不可调换：
 *   0. ApiError 直通（幂等）—— 必须最先
 *   1. 取消
 *   2. 字段校验（HTTP 400 + fieldErrors）
 *   3. 401（必须早于信封业务码）
 *   4. 断网（无 response）
 *   5. 真实 HTTP 5xx
 *   6. 信封业务失败（HTTP 200 + 信封 code !== 200）
 *   7. 兜底
 *
 * 已知语义边界：本后端把通用 500/系统异常也包成 HTTP 200 信封
 * （GlobalExceptionHandler.handleException / handleSystemException 返回 Result<Void>
 * 而非 ResponseEntity），因此这类失败会落入第 6 步归为 `business`，
 * 文案仍是后端下发的固定中文「服务暂时不可用，请稍后重试」，只是不带重试按钮。
 * `server` 分支保留给真实的传输层 5xx。这是刻意的 —— ROADMAP 准则 2 只要求
 * 「一条中文提示」，不要求所有服务端故障都可重试。请勿把这里的顺序"修正"回去。
 */
export function normalizeApiError(raw: unknown): ApiError {
  // 0. 幂等直通：request.ts 的两个分支抛出的都是 ApiError，showError / apply /
  //    页面 catch 会把它再喂回来。ApiError 没有 response 属性，若缺这道直通，
  //    第 4 步会把每条拦截器错误改判成 network —— 校验内联永不出现、D-08 的
  //    resource.exists 丢失、取消也会被覆盖成 network 而弹出一条本应忽略的提示。
  //    幂等性必须只依赖类型，不得按 message 内容猜测。
  if (raw instanceof ApiError) return raw

  // 1. 取消先于任何呈现识别（约束 #4）
  if (isCancel(raw)) return new ApiError('', 'cancel')

  const err = raw as { response?: { status?: number; data?: any } } | null | undefined
  const data = err?.response?.data
  const status = err?.response?.status

  // 2. 字段校验：HTTP 400 + fieldErrors 非空
  if (Array.isArray(data?.fieldErrors) && data.fieldErrors.length > 0) {
    const map: Record<string, string> = {}
    for (const fe of data.fieldErrors as Array<{ field?: string; message?: string }>) {
      if (!fe?.field) continue
      const message = fe.message ?? ''
      map[fe.field] = map[fe.field] ? `${map[fe.field]}；${message}` : message
    }
    return new ApiError(
      data.message || '请检查以下填写内容',
      'validation',
      status,
      data.errorCode,
      map
    )
  }

  // 3. 401 必须早于信封业务码：11-01 的认证分支返回真实 HTTP 401，
  //    其响应体里 code 也是 401（!== 200）；若先判业务码，令牌过期会被
  //    误判成 business，闩锁不触发、不 logout，一次会话过期就变成静默失败。
  if (status === 401) return new ApiError('登录已过期，请重新登录', 'auth', 401)

  // 4. 断网：仅对原生 Axios 错误形态生效（ApiError 已在第 0 步直通返回）
  if (!err?.response) {
    return new ApiError(
      '网络连接异常，请检查网络后重试',
      'network',
      undefined,
      undefined,
      {},
      undefined,
      true
    )
  }

  // 5. 真实 HTTP 5xx（网关 / 容器层返回，响应体不带信封 code）
  if (typeof status === 'number' && status >= 500) {
    return new ApiError(
      '服务暂时不可用，请稍后重试',
      'server',
      status,
      data?.errorCode,
      {},
      undefined,
      true
    )
  }

  // 6. 信封业务失败（HTTP 200 + 信封 code !== 200）。
  //    判定只读信封里的 data.code，不读 status、也不看 code 的数值大小：
  //    BusinessException 经 parseCodeToInt 回退后信封 code 恒为 500，若按数值
  //    判 5xx，每条业务失败都会被改判成 server 并丢掉后端中文 message（违反 D-05）。
  //    typeof === 'number' 守卫生效：字符串码（如旧 AUTHENTICATION_REQUIRED）
  //    不进入本分支，落到兜底。
  if (typeof data?.code === 'number' && data.code !== 200) {
    return new ApiError(data.message || '', 'business', status, data.errorCode)
  }

  // 7. 兜底
  return new ApiError(data?.message || '服务暂时不可用，请稍后重试', 'server', status)
}
