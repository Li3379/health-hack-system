// 前端唯一错误模型：把任何异形失败翻译成同一个 ApiError。
// 本模块是纯函数模块 —— 不得 import pinia / store / vue-router / element-plus，
// 否则会与 stores/auth.ts 形成循环依赖，也会让"翻译"与"展示"重新耦合。

export type ErrorKind =
  | 'validation'
  | 'business'
  | 'auth'
  | 'forbidden'
  | 'notFound'
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

  /**
   * 重新发起同一次请求的回调（D-03）。
   *
   * 由 `request.ts` 的响应拦截器在拿到 `error.config` 时挂上；`showError` 在
   * `retryable === true` 且调用方没有显式给 retry 时使用它，让网络/传输层 5xx
   * 的 toast 天然带「重试」按钮，而不必每个调用点各写一遍。
   *
   * 只在用户点击时执行一次，绝不自动重试 —— 写操作重复提交的风险由用户承担。
   */
  retryRequest?: () => Promise<unknown>
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
 * 分类顺序即契约，不可调换（共 9 步）：
 *   0. ApiError 直通（幂等）—— 必须最先
 *   1. 取消
 *   2. 字段校验（HTTP 400 + fieldErrors）
 *   3. 401（必须早于信封业务码）
 *   4. 403 无权限（必须晚于 401、早于第 6 步信封业务码）
 *   5. 404 资源不存在（必须晚于 403、早于第 6 步信封业务码）
 *   6. 断网（无 response）
 *   7. 真实 HTTP 5xx
 *   8. 信封业务失败（HTTP 200 + 信封 code !== 200）
 *   9. 兜底
 *
 * 第 4/5 步的位置是契约：早于第 3 步会把会话过期误判成权限失败；晚于第 6 步
 * 则永远不会命中（ApiError 已在第 0 步直通，其余无 response 的形态已在第 6 步返回）。
 * 同理，只要落到第 8 步，403/404 就会被信封业务码吞成 `business`，因此第 4/5 步
 * 必须留在第 8 步之前。
 *
 * 第 4/5 步各自接受三种证据，缺一不可（兼容新旧后端）：数值信封 code
 * （现由 ErrorCode.httpStatus 下发 403/404）、真实 HTTP status、errorCode 正则。
 * 旧后端把字符串错误码交给 `parseCodeToInt` 后会恒回退成 500，那种形态只能靠
 * `errorCode` 识别 —— 正则因此必须保留，不能因为数值码已可信就删掉。
 *
 * 已知语义边界：本后端把通用 500/系统异常也包成 HTTP 200 信封
 * （GlobalExceptionHandler.handleException / handleSystemException 返回 Result<Void>
 * 而非 ResponseEntity），因此这类失败会落入第 8 步归为 `business`，
 * 文案仍是后端下发的固定中文「服务暂时不可用，请稍后重试」，只是不带重试按钮。
 * `server` 分支保留给真实的传输层 5xx。这是刻意的 —— ROADMAP 准则 2 只要求
 * 「一条中文提示」，不要求所有服务端故障都可重试。请勿把这里的顺序"修正"回去。
 */
export function normalizeApiError(raw: unknown): ApiError {
  // 0. 幂等直通：request.ts 的两个分支抛出的都是 ApiError，showError / apply /
  //    页面 catch 会把它再喂回来。ApiError 没有 response 属性，若缺这道直通，
  //    第 6 步会把每条拦截器错误改判成 network —— 校验内联永不出现、D-08 的
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

  // 信封里的两个字符串字段只读一次，让下面两条判定条件可读。
  const envelopeCode = typeof data?.code === 'number' ? data.code : undefined
  const errorCode = typeof data?.errorCode === 'string' ? data.errorCode : ''

  // 4. 403 无权限。真实 HTTP 403 由 GlobalExceptionHandler.handleAccessDenied 下发
  //    （Result.failure(403, 'auth.forbidden', '没有访问权限') + ResponseEntity.status(403)）。
  //    信封形态由 BusinessException(AUTH_FORBIDDEN / GOAL_FORBIDDEN) 下发，
  //    其数值 code 已被 parseCodeToInt 回退成 500，只能靠 errorCode 字符串识别。
  //    必须保留 status / errorCode：prevention/Profile.vue 与 stores/profileCompleteness.ts
  //    读它们区分「档案不存在」与真实故障。
  if (status === 403 || /(^|\.)forbidden$/.test(errorCode)) {
    return new ApiError(data?.message || '没有访问权限', 'forbidden', status, errorCode)
  }

  // 5. 404 资源不存在。数值 404 由 GlobalExceptionHandler.handleNoResourceFoundException
  //    下发（HTTP 200 信封）；BusinessException(*_NOT_FOUND) 现也下发数值 404（信封 code
  //    取自 ErrorCode.httpStatus）。errorCode 正则同时保留，兼容旧版本后端与两种分隔符：
  //    health.profile_not_found（下划线）与 resource.not_found（点号）在 ErrorCode 中并存。
  if (envelopeCode === 404 || status === 404 || /(^|_|\.)not[._]?found$/.test(errorCode)) {
    return new ApiError(data?.message || '资源不存在', 'notFound', status, errorCode)
  }

  // 6. 断网：仅对原生 Axios 错误形态生效（ApiError 已在第 0 步直通返回）。
  //    应用代码里抛出的普通 Error / TypeError 没有 response，却也不代表网络故障 ——
  //    把它们判成 network 会显示带「重试」按钮的网络提示，掩盖真正的程序缺陷。
  //    这类错误统一落到 'unknown'（无重试按钮；showError 按 business 处理，一条字符串提示）。
  if (!err?.response) {
    const isAxios = (raw as { isAxiosError?: boolean } | null | undefined)?.isAxiosError === true
    if (isAxios) {
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
    return new ApiError('操作未完成，请稍后重试', 'unknown', undefined, undefined, {}, undefined, false)
  }

  // 7. 真实 HTTP 5xx（网关 / 容器层返回，响应体不带信封 code）
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

  // 8. 信封业务失败（HTTP 200 + 信封 code !== 200）。
  //    判定只读信封里的 data.code，不读 status、也不看 code 的数值大小 —— 这里刻意
  //    不按数值判 5xx：业务失败必须保留后端中文 message（D-05），改判 server 会丢掉它。
  //    后端现已让信封 code 与 ErrorCode.httpStatus 对齐（404/403/409/400 语义正确），
  //    前四步已优先命中这些码；走到本步的是其余业务失败（以 400/500 为主）。
  //    typeof === 'number' 守卫生效：字符串码（如旧 AUTHENTICATION_REQUIRED）
  //    不进入本分支，落到兜底。
  if (typeof data?.code === 'number' && data.code !== 200) {
    return new ApiError(data.message || '', 'business', status, data.errorCode)
  }

  // 9. 兜底
  return new ApiError(data?.message || '服务暂时不可用，请稍后重试', 'server', status)
}
