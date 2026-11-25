import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import type { Mock } from 'vitest'

/**
 * 「测试推送」失败的文案必须与推送历史一致且可操作（PUSH-3）。
 *
 * 同一次 SMTP 故障有两条呈现通道：
 *  - 推送历史时间线：PushSettings.vue 用 `getFailureHint` 把原文翻成中文；
 *  - 「测试推送」toast：stores/push.ts 的 testChannel → showError。
 *
 * 曾经只有前者做翻译，后者把后端 message 原样交给 showError，而 `resolveMessage` 对
 * 非空 backendMessage 是**后端优先**（utils/errorMessages.ts:156-161）——于是 toast 上屏的
 * 就是 `推送失败: Authentication failed` 这段英文，用户既不知道原因也不知道该找谁。
 *
 * 现在两边共用 `utils/pushFailure.getFailureHint`。这里从 store 这一侧钉住：
 * 后端给出英文时 toast 是中文、且保留服务器原文；后端自己写的中文说明不被改写。
 */

vi.mock('element-plus', async importOriginal => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(() => ({ close: vi.fn() })), {
      error: vi.fn(),
      success: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      closeAll: vi.fn()
    })
  }
})

vi.mock('@/api/push', () => ({
  getPushConfigs: vi.fn(),
  updatePushConfig: vi.fn(),
  testPushChannel: vi.fn(),
  getPushHistory: vi.fn(),
  getPushStats: vi.fn()
}))

import { usePushStore } from '@/stores/push'
import { testPushChannel } from '@/api/push'
import { ElMessage } from 'element-plus'
import { ApiError } from '@/utils/appError'
import { getFailureHint } from '@/utils/pushFailure'

const testPushMock = testPushChannel as unknown as Mock

interface MessageSpy extends Mock {
  error: Mock
  success: Mock
  warning: Mock
  info: Mock
}

const ElMessageMock = ElMessage as unknown as MessageSpy

/** 取出这轮里唯一一条 toast 的可见文案（对象式调用的 message 字段或函数式首个参数）。 */
function toastText(): string {
  const objectCalls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { message?: unknown } | undefined)?.message !== undefined
  )
  if (objectCalls.length > 0) {
    const last = objectCalls[objectCalls.length - 1][0] as { message: unknown }
    return typeof last.message === 'string' ? last.message : String(last.message)
  }
  const flat = [
    ...ElMessageMock.error.mock.calls,
    ...ElMessageMock.success.mock.calls,
    ...ElMessageMock.warning.mock.calls
  ]
  expect(flat.length).toBeGreaterThan(0)
  return String(flat[flat.length - 1][0])
}

describe('utils/pushFailure —— 失败归因译文', () => {
  it('英文 Authentication failed：给出中文去向，且不假装知道是哪一种原因', () => {
    const hint = getFailureHint('推送失败: Authentication failed')
    expect(hint).toContain('邮箱凭据或账号状态')
    expect(hint).not.toContain('SMTP 服务未开启')
    // 英文原文不应再出现在提示主体里
    expect(hint).not.toContain('Authentication failed')
  })

  it('带 535 原文时：摘要只给一句结论，535 原文交给详情展开', () => {
    // 摘要面向普通用户，只回答「怎么了 + 该做什么」；535 那一长串并列原因
    // 属于排障信息，由时间线的「查看详情」按需展开，不再挤进摘要。
    const hint = getFailureHint(
      '推送失败: Authentication failed (535 Login fail. Account is abnormal, service is not open, '
        + 'password is incorrect, login frequency limited, or system is busy.)'
    )
    expect(hint).toContain('邮箱凭据或账号状态')
    expect(hint).not.toContain('535')
    // 摘要必须是能扫读的一句话，不是一段
    expect(hint.length).toBeLessThan(80)
  })

  it('主机名带 auth 的 connection refused 归为连接失败，不误标凭据问题', () => {
    const hint = getFailureHint(
      'MailConnectException (Could not connect to host "smtp.auth.example.com": connection refused)'
    )
    expect(hint).toContain('邮件服务器连接失败')
    expect(hint).not.toContain('凭据')
  })

  // PUSH-12 的回归：裸 `includes('auth')` 会把任何含 auth 子串的文本标成凭据问题。
  // 注意 `assertion` 并不含 "auth"（是 assert + ion），拿它当例子是错的 —— 这里只用
  // 真正包含该子串、且与 SMTP 凭据无关的文本。
  it('只含 authorization 字样的错误不被标成凭据问题', () => {
    const hint = getFailureHint('authorization header missing on relay response')
    expect(hint).not.toContain('凭据')
    expect(hint).toContain('失败原因：authorization header missing on relay response')
  })

  it('只含 authentic 字样的非 SMTP 错误不被标成凭据问题', () => {
    const hint = getFailureHint('upstream proxy authentic required')
    expect(hint).not.toContain('凭据')
    expect(hint).toContain('失败原因：')
  })

  it('主机名本身叫 smtp.auth.* 的连接超时，归为连接失败', () => {
    // 真实的 jakarta MailConnectException 文案（javap 确认）：
    // "Couldn't connect to host, port: <host>, <port>; timeout <ms>"
    const hint = getFailureHint(
      "Mail server connection failed (Couldn't connect to host, port: smtp.auth.example.com, 465; timeout 5000)"
    )
    expect(hint).toContain('邮件服务器连接失败')
    expect(hint).not.toContain('凭据')
  })

  it('connect timed out 措辞也归为连接失败（仅靠 timeout 匹配不到它）', () => {
    // 故意不带 "timeout" 这个词：旧规则只有 includes('timeout')，这种写法会落到原文展示
    const hint = getFailureHint(
      "Couldn't connect to host, port: smtp.auth.example.com, 587; connect timed out"
    )
    expect(hint).toContain('邮件服务器连接失败')
    expect(hint).not.toContain('凭据')
  })

  it('UnknownHostException 措辞归为地址错误', () => {
    const hint = getFailureHint(
      "Couldn't connect to host, port: smtp.auth.example.com, 465; UnknownHostException"
    )
    expect(hint).toContain('邮件服务器地址错误')
    expect(hint).not.toContain('凭据')
  })

  it('后端自己写的中文说明原样返回，不再包一层「失败原因：」', () => {
    expect(getFailureHint('推送失败: 请先启用该推送渠道后再发送测试'))
      .toBe('请先启用该推送渠道后再发送测试')
  })

  it('`推送失败: ` 前缀被剥掉，免得「失败」说两遍', () => {
    expect(getFailureHint('推送失败: Some unknown error')).toBe('失败原因：Some unknown error')
  })

  it('空消息给出可执行的下一步，而不是空白 toast', () => {
    expect(getFailureHint('')).toContain('未返回具体原因')
    expect(getFailureHint(null)).toContain('未返回具体原因')
  })

  // SKIPPED 行现在也会显示在历史里（PUSH-4 让「没送达」可见），必须说人话。
  it('从未配置的 SKIPPED：告诉用户去补配置，而不是显示英文 Skipped: Channel not configured', () => {
    const hint = getFailureHint('Skipped: Channel not configured')
    expect(hint).toContain('尚未配置')
    expect(hint).not.toContain('Skipped')
  })

  it('配置了但此刻不可达的 SKIPPED：说明会自行恢复，不要求用户动手', () => {
    const hint = getFailureHint('Skipped: Channel temporarily unreachable')
    expect(hint).toContain('不可达')
    expect(hint).not.toContain('尚未配置')
  })

  it('限流跳过单独成句', () => {
    expect(getFailureHint('Skipped: Rate limit exceeded')).toContain('过于频繁')
  })
})

describe('stores/push.ts —— 测试推送失败走同一份译文', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  /**
   * 实网路径：后端 `Result.failure(400, "推送失败: ...")` 让拦截器对 `code !== 200`
   * 直接 reject（utils/request.ts:45-52），store 落在 catch 分支。因此 mock 必须
   * **reject** 一个与拦截器同形的 ApiError，而不是 resolve 一个 code=400 的信封 ——
   * 后者会走 else 分支，测不到真实链路。
   */
  function rejectWith(message: string): ApiError {
    return new ApiError(message, 'business', 200, undefined)
  }

  it('后端返回英文原文时，toast 是一句可操作中文，不带英文原文', async () => {
    testPushMock.mockRejectedValue(
      rejectWith('推送失败: Authentication failed (535 Login fail. Account is abnormal.)')
    )

    const store = usePushStore()
    const ok = await store.testChannel('EMAIL')

    expect(ok).toBe(false)
    const text = toastText()
    expect(text).toContain('邮箱凭据或账号状态')
    // toast 没有折叠空间，必须一句话说完：535 原文留给时间线的「查看详情」
    expect(text).not.toContain('535')
    expect(text.length).toBeLessThan(80)
    // 关键回归：不能把后端英文原样上屏
    expect(text).not.toContain('推送失败: Authentication failed')
  })

  it('后端返回中文说明时原样呈现，不被「后端优先」规则覆盖成泛化文案', async () => {
    testPushMock.mockRejectedValue(rejectWith('推送失败: 请先启用该推送渠道后再发送测试'))

    const store = usePushStore()
    await store.testChannel('EMAIL')

    expect(toastText()).toBe('请先启用该推送渠道后再发送测试')
  })

  it('401 不被改写：认证分类与会话过期提示归拦截器/logout，store 不越权', async () => {
    testPushMock.mockRejectedValue(new ApiError('登录已过期，请重新登录', 'auth', 401))

    const store = usePushStore()
    await store.testChannel('EMAIL')

    // showError 对 auth 分类零 toast（可见提示由 authStore.logout 承担）
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    const objectErrors = ElMessageMock.mock.calls.filter(
      ([options]) => (options as { type?: string } | undefined)?.type === 'error'
    )
    expect(objectErrors).toHaveLength(0)
  })

  it('成功路径不受影响：仍是一条 success，且不含错误文案', async () => {
    testPushMock.mockResolvedValue({
      code: 200,
      message: 'success',
      data: { success: true, channel: '邮件', message: 'ok', timestamp: '2026-11-25 10:00:00' }
    })

    const store = usePushStore()
    const ok = await store.testChannel('EMAIL')

    expect(ok).toBe(true)
    expect(ElMessageMock.success).toHaveBeenCalledTimes(1)
    expect(String(ElMessageMock.success.mock.calls[0][0])).toContain('推送测试成功')
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
  })
})