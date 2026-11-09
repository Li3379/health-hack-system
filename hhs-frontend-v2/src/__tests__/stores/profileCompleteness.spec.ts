import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProfileCompletenessStore } from '@/stores/profileCompleteness'
import { preventionApi } from '@/api/prevention'
import { ApiError } from '@/utils/appError'
import { NO_PROFILE_ASSUMPTION, PROFILE_FIELDS } from '@/utils/profileFields'
import type { ProfileCompletenessVO, ProfileFieldStatusVO } from '@/types/api'

vi.mock('@/api/prevention', () => ({
  preventionApi: {
    getCompleteness: vi.fn()
  }
}))

const DISMISS_KEY = 'hhs_profile_reminder_dismissed_at'
// 刻意在测试里重算静默期，而不是从生产模块导入常量：
// 若生产把 7 天悄悄改成别的窗口，这里的边界测试必须失败。
const SILENCE_MS = 7 * 24 * 60 * 60 * 1000

/** 造一个后端形状的 9 项 payload（filled 可覆盖）。 */
function makePayload(overrides: Partial<ProfileCompletenessVO> = {}): ProfileCompletenessVO {
  const fields: ProfileFieldStatusVO[] = PROFILE_FIELDS.map(field => ({ ...field }))
  return {
    score: 60,
    level: '标准档案',
    filledCount: 2,
    totalCount: fields.length,
    hasProfile: true,
    assumptions: ['缺少出生日期，年龄按 35 岁估算'],
    fields,
    ...overrides
  }
}

function okResponse<T>(data: T) {
  return { code: 200, message: 'success', data }
}

describe('useProfileCompletenessStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  describe('fetchCompleteness · 成功路径', () => {
    it('stores score, level, filledCount, totalCount and fields verbatim', async () => {
      const payload = makePayload({ score: 73, level: '标准档案', filledCount: 5 })
      vi.mocked(preventionApi.getCompleteness).mockResolvedValue(okResponse(payload))

      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.data?.score).toBe(73)
      expect(store.data?.level).toBe('标准档案')
      expect(store.data?.filledCount).toBe(5)
      expect(store.data?.fields.length).toBe(store.data?.totalCount)
      expect(store.loadFailed).toBe(false)
      expect(store.loading).toBe(false)
    })

    it('derives totalCount from the payload field list, never from a hardcoded number', async () => {
      const fields: ProfileFieldStatusVO[] = PROFILE_FIELDS.slice(0, 4).map(f => ({
        ...f,
        filled: true
      }))
      vi.mocked(preventionApi.getCompleteness).mockResolvedValue(
        okResponse(makePayload({ totalCount: fields.length, fields }))
      )

      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.data?.fields.length).toBe(4)
      expect(store.data?.totalCount).toBe(4)
      // 静态目录是 9 项，但 payload 只有 4 项 —— store 不得把 9 写回来
      expect(store.data?.totalCount).not.toBe(PROFILE_FIELDS.length)
    })

    it('stores hasProfile verbatim with no transformation', async () => {
      vi.mocked(preventionApi.getCompleteness).mockResolvedValue(
        okResponse(makePayload({ hasProfile: true }))
      )

      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.data?.hasProfile).toBe(true)
    })

    it('stores assumptions verbatim: a two-sentence payload yields a two-item array', async () => {
      const assumptions = ['缺少 BMI，按 22.0 估算', '缺少家族病史，糖尿病风险评估按「无家族史」计算']
      vi.mocked(preventionApi.getCompleteness).mockResolvedValue(
        okResponse(makePayload({ assumptions }))
      )

      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.data?.assumptions).toEqual(assumptions)
      expect(store.data?.assumptions.length).toBe(2)
    })

    it('keeps a single-sentence assumptions payload at one item', async () => {
      vi.mocked(preventionApi.getCompleteness).mockResolvedValue(
        okResponse(makePayload({ assumptions: [NO_PROFILE_ASSUMPTION] }))
      )

      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.data?.assumptions).toEqual([NO_PROFILE_ASSUMPTION])
      expect(store.data?.assumptions.length).toBe(1)
    })
  })

  describe('fetchCompleteness · 失败路径', () => {
    it('leaves data null and sets loadFailed on a network error — never a fabricated 0%', async () => {
      vi.mocked(preventionApi.getCompleteness).mockRejectedValue(
        new ApiError('网络连接异常，请检查网络后重试', 'network')
      )

      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.data).toBeNull()
      expect(store.loadFailed).toBe(true)
      expect(store.loading).toBe(false)
    })

    it('synthesizes 0% / 基础档案 / 9 fields on a 404 and keeps loadFailed false', async () => {
      const notFound = new ApiError('未找到资源', 'business', 404)
      vi.mocked(preventionApi.getCompleteness).mockRejectedValue(notFound)

      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.data).not.toBeNull()
      expect(store.data?.score).toBe(0)
      expect(store.data?.level).toBe('基础档案')
      expect(store.data?.filledCount).toBe(0)
      expect(store.data?.fields.length).toBe(9)
      expect(store.data?.totalCount).toBe(store.data?.fields.length)
      expect(store.data?.hasProfile).toBe(false)
      expect(store.data?.assumptions).toEqual([NO_PROFILE_ASSUMPTION])
      expect(store.loadFailed).toBe(false)
    })

    it('treats the health.profile_not_found business code as a 404', async () => {
      vi.mocked(preventionApi.getCompleteness).mockRejectedValue(
        new ApiError('档案不存在', 'business', 200, 'health.profile_not_found')
      )

      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.data?.score).toBe(0)
      expect(store.data?.hasProfile).toBe(false)
      expect(store.loadFailed).toBe(false)
    })

    it('keeps a previously loaded payload when a later refresh fails', async () => {
      vi.mocked(preventionApi.getCompleteness).mockResolvedValue(okResponse(makePayload({ score: 80 })))
      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      vi.mocked(preventionApi.getCompleteness).mockRejectedValue(
        new ApiError('服务暂时不可用，请稍后重试', 'server', 503)
      )
      await store.fetchCompleteness()

      expect(store.data?.score).toBe(80)
      expect(store.loadFailed).toBe(true)
    })
  })

  describe('fetchCompleteness · 单飞且可等待', () => {
    it('dedupes a concurrent burst to one HTTP call and resolves only after the request settles', async () => {
      const payload = makePayload({ score: 42 })
      let release!: (value: unknown) => void
      const deferred = new Promise(resolve => {
        release = resolve
      })
      vi.mocked(preventionApi.getCompleteness).mockReturnValue(deferred as never)

      const store = useProfileCompletenessStore()
      const first = store.fetchCompleteness()
      const second = store.fetchCompleteness()

      // (a) 恰好一次 HTTP 调用
      expect(preventionApi.getCompleteness).toHaveBeenCalledTimes(1)
      // 注：不比对 promise 的引用相等 —— Pinia 会把 setup action 包一层，每次调用
      // 返回不同的派生 promise。真正要锁住的是「一次调用」与「落定前不 resolve」。

      let firstSettled = false
      let secondSettled = false
      void first.then(() => {
        firstSettled = true
      })
      void second.then(() => {
        secondSettled = true
      })

      // 让微任务全部排空 —— 若实现是 `if (loading) return`，两个 promise 已立即 resolve
      await Promise.resolve()
      await Promise.resolve()
      await Promise.resolve()

      // (b) 请求未落定前，任何一个调用都不得 resolve
      expect(firstSettled).toBe(false)
      expect(secondSettled).toBe(false)
      expect(store.data).toBeNull()
      expect(store.loading).toBe(true)

      release(okResponse(payload))
      await Promise.all([first, second])

      expect(firstSettled).toBe(true)
      expect(secondSettled).toBe(true)
      expect(store.data?.score).toBe(42)
      expect(store.loading).toBe(false)
      // 仍然只有一次调用：闸门没有在落定后再发一个请求
      expect(preventionApi.getCompleteness).toHaveBeenCalledTimes(1)
    })

    it('starts a fresh request after the previous one settles', async () => {
      vi.mocked(preventionApi.getCompleteness).mockResolvedValue(okResponse(makePayload({ score: 10 })))
      const store = useProfileCompletenessStore()

      await store.fetchCompleteness()
      await store.fetchCompleteness()

      expect(preventionApi.getCompleteness).toHaveBeenCalledTimes(2)
    })

    it('passes { silent: true } so a guidance-only surface never toasts', async () => {
      vi.mocked(preventionApi.getCompleteness).mockResolvedValue(okResponse(makePayload()))
      const store = useProfileCompletenessStore()

      await store.fetchCompleteness()

      expect(preventionApi.getCompleteness).toHaveBeenCalledWith({ silent: true })
    })
  })

  describe('firstMissingKey 优先级', () => {
    it('returns gender for an empty synthesized profile', async () => {
      vi.mocked(preventionApi.getCompleteness).mockRejectedValue(
        new ApiError('未找到资源', 'business', 404)
      )
      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.firstMissingKey()).toBe('gender')
    })

    it('returns heightCm when gender and birthDate are filled', async () => {
      const fields = PROFILE_FIELDS.map(field =>
        field.key === 'gender' || field.key === 'birthDate' ? { ...field, filled: true } : { ...field }
      )
      vi.mocked(preventionApi.getCompleteness).mockResolvedValue(
        okResponse(makePayload({ filledCount: 2, fields }))
      )
      const store = useProfileCompletenessStore()
      await store.fetchCompleteness()

      expect(store.firstMissingKey()).toBe('heightCm')
    })
  })

  describe('7 天静默窗口', () => {
    const BASE = Date.parse('2026-11-09T00:00:00.000Z')

    beforeEach(() => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date(BASE))
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('is not dismissed when no key is present', () => {
      const store = useProfileCompletenessStore()

      expect(localStorage.getItem(DISMISS_KEY)).toBeNull()
      expect(store.isDismissed()).toBe(false)
    })

    it('is dismissed immediately after dismiss()', () => {
      const store = useProfileCompletenessStore()

      store.dismiss()

      expect(store.isDismissed()).toBe(true)
    })

    it('dismiss() writes a parseable ISO string under hhs_profile_reminder_dismissed_at', () => {
      const store = useProfileCompletenessStore()

      store.dismiss()

      const raw = localStorage.getItem(DISMISS_KEY)
      expect(raw).toBe(new Date(BASE).toISOString())
      expect(Number.isNaN(Date.parse(raw as string))).toBe(false)
    })

    it('dismiss() bumps the reactive dismissedAt mirror so consumers re-evaluate', () => {
      const store = useProfileCompletenessStore()

      expect(store.dismissedAt).toBeNull()
      store.dismiss()

      expect(store.dismissedAt).toBe(new Date(BASE).toISOString())
    })

    it('is still dismissed 1 ms before the 7-day boundary', () => {
      const store = useProfileCompletenessStore()
      store.dismiss()

      vi.setSystemTime(new Date(BASE + SILENCE_MS - 1))

      expect(store.isDismissed()).toBe(true)
    })

    it('is no longer dismissed at exactly the 7-day boundary plus 1 ms', () => {
      const store = useProfileCompletenessStore()
      store.dismiss()

      vi.setSystemTime(new Date(BASE + SILENCE_MS + 1))

      expect(store.isDismissed()).toBe(false)
    })

    it('is still dismissed at exactly 7 days', () => {
      const store = useProfileCompletenessStore()
      store.dismiss()

      vi.setSystemTime(new Date(BASE + SILENCE_MS))

      expect(store.isDismissed()).toBe(true)
    })

    it('is no longer dismissed after 8 days', () => {
      const store = useProfileCompletenessStore()
      store.dismiss()

      vi.setSystemTime(new Date(BASE + 8 * 24 * 60 * 60 * 1000))

      expect(store.isDismissed()).toBe(false)
    })

    it('fails open on a corrupt timestamp: "not-a-date" is treated as not dismissed', () => {
      localStorage.setItem(DISMISS_KEY, 'not-a-date')
      const store = useProfileCompletenessStore()

      expect(store.isDismissed()).toBe(false)
    })

    it('fails open on an empty string value', () => {
      localStorage.setItem(DISMISS_KEY, '')
      const store = useProfileCompletenessStore()

      expect(store.isDismissed()).toBe(false)
    })

    it('re-dismissing restarts the 7-day window from the new instant', () => {
      const store = useProfileCompletenessStore()
      store.dismiss()

      // 第 6 天再次关闭：窗口从这一刻重新起算
      vi.setSystemTime(new Date(BASE + 6 * 24 * 60 * 60 * 1000))
      store.dismiss()

      // 距第一次关闭已 7 天 + 1ms，但距第二次关闭只 1ms
      vi.setSystemTime(new Date(BASE + SILENCE_MS + 1))

      expect(store.isDismissed()).toBe(true)
    })
  })
})
