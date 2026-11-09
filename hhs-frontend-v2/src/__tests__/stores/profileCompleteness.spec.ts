import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useProfileCompletenessStore } from '@/stores/profileCompleteness'
import { preventionApi } from '@/api/prevention'
import { ApiError } from '@/utils/appError'
import {
  firstMissingKeyOf,
  missingFieldsOf,
  NO_PROFILE_ASSUMPTION,
  PROFILE_FIELDS,
  synthesizeEmptyCompleteness
} from '@/utils/profileFields'
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

describe('档案字段静态目录', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    vi.clearAllMocks()
  })

  /** 目录里第一个未填字段的 key —— 用来把「优先级」与「目录顺序」显式对立起来。 */
  function firstUnfilledInCatalogOrder(fields: ProfileFieldStatusVO[]): string | null {
    const found = fields.find(field => !field.filled)
    return found ? found.key : null
  }

  function fieldsWithFilled(keys: string[]): ProfileFieldStatusVO[] {
    return PROFILE_FIELDS.map(field => ({ ...field, filled: keys.includes(field.key) }))
  }

  describe('firstMissingKey 优先级高于目录顺序', () => {
    it('returns gender for a completely empty catalog', () => {
      expect(firstMissingKeyOf(fieldsWithFilled([]))).toBe('gender')
    })

    it('returns birthDate once gender is filled', () => {
      expect(firstMissingKeyOf(fieldsWithFilled(['gender']))).toBe('birthDate')
    })

    it('returns heightCm once both required fields are filled — recommended beats optional despite catalog order', () => {
      const fields = fieldsWithFilled(['gender', 'birthDate'])

      // 目录顺序下第一个未填项就是 heightCm（bloodType 在它之后），
      // 但这里断言的重点是：即使把顺序打乱，recommended 仍必须先于 optional。
      expect(firstMissingKeyOf(fields)).toBe('heightCm')
      expect(firstUnfilledInCatalogOrder(fields)).toBe('heightCm')
    })

    it('still prefers heightCm over bloodType when bloodType sits earlier in a shuffled list', () => {
      const fields = fieldsWithFilled(['gender', 'birthDate'])
      const bloodType = fields.find(f => f.key === 'bloodType')
      expect(bloodType).toBeDefined()
      const heightCmIndex = fields.findIndex(f => f.key === 'heightCm')
      const shuffledWithoutBloodType = fields.filter(f => f.key !== 'bloodType')
      // 把 bloodType 插到 heightCm 之前，模拟「目录顺序被改动」的最坏情况
      shuffledWithoutBloodType.splice(heightCmIndex, 0, bloodType as ProfileFieldStatusVO)

      expect(firstUnfilledInCatalogOrder(shuffledWithoutBloodType)).toBe('bloodType')
      expect(firstMissingKeyOf(shuffledWithoutBloodType)).toBe('heightCm')
    })

    it('returns bloodType when every required and recommended field is filled', () => {
      const keys = PROFILE_FIELDS.filter(f => f.importance !== 'optional').map(f => f.key)

      expect(firstMissingKeyOf(fieldsWithFilled(keys))).toBe('bloodType')
    })

    it('returns null when every field is filled', () => {
      expect(firstMissingKeyOf(fieldsWithFilled(PROFILE_FIELDS.map(f => f.key)))).toBeNull()
    })

    it('returns null for an empty field list', () => {
      expect(firstMissingKeyOf([])).toBeNull()
    })

    it('keeps catalog order as the stable tiebreak within the same tier', () => {
      // allergyHistory（optional）在家庭病史之前；只填必需与推荐时，第一个未填是 bloodType
      const keys = PROFILE_FIELDS.filter(f => f.importance === 'required' || f.importance === 'recommended').map(
        f => f.key
      )
      const fields = fieldsWithFilled(keys)

      expect(firstMissingKeyOf(fields)).toBe('bloodType')
      // 同重要度内的平局由目录顺序决定
      expect(fields.filter(f => f.importance === 'optional' && !f.filled).map(f => f.key)).toEqual([
        'bloodType',
        'allergyHistory',
        'familyHistory',
        'lifestyleHabits'
      ])
    })
  })

  describe('missingFields 保持目录顺序', () => {
    it('lists gender before bloodType when both are missing and length equals totalCount - filledCount', () => {
      const filled = ['birthDate', 'heightCm', 'weightKg', 'bmi', 'familyHistory']
      const fields = fieldsWithFilled(filled)
      const missing = missingFieldsOf(fields)

      expect(missing.map(f => f.key)).toEqual([
        'gender',
        'bloodType',
        'allergyHistory',
        'lifestyleHabits'
      ])
      expect(missing.length).toBe(fields.length - filled.length)
    })

    it('returns an empty array when nothing is missing', () => {
      expect(missingFieldsOf(fieldsWithFilled(PROFILE_FIELDS.map(f => f.key)))).toEqual([])
    })

    it('returns a new array, not the input', () => {
      const fields = fieldsWithFilled(['gender'])

      const missing = missingFieldsOf(fields)

      expect(missing).not.toBe(fields)
    })
  })

  describe('与后端 ProfileFieldCatalog 的一致性', () => {
    it('carries exactly the backend 9 keys in backend order', () => {
      expect(PROFILE_FIELDS.map(f => f.key)).toEqual([
        'gender',
        'birthDate',
        'heightCm',
        'weightKg',
        'bmi',
        'bloodType',
        'allergyHistory',
        'familyHistory',
        'lifestyleHabits'
      ])
    })

    it('carries the backend 9 labels in the same order', () => {
      expect(PROFILE_FIELDS.map(f => f.label)).toEqual([
        '性别',
        '出生日期',
        '身高',
        '体重',
        'BMI',
        '血型',
        '过敏史',
        '家族病史',
        '生活习惯'
      ])
    })

    it('has the 2 required / 3 recommended / 4 optional distribution', () => {
      const byImportance = PROFILE_FIELDS.reduce<Record<string, number>>((acc, field) => {
        acc[field.importance] = (acc[field.importance] ?? 0) + 1
        return acc
      }, {})

      expect(byImportance.required).toBe(2)
      expect(byImportance.recommended).toBe(3)
      expect(byImportance.optional).toBe(4)
      expect(Object.keys(byImportance).sort()).toEqual(['optional', 'recommended', 'required'])
    })

    it('every entry starts unfilled and carries a non-blank reason', () => {
      expect(PROFILE_FIELDS.every(field => field.filled === false)).toBe(true)
      expect(PROFILE_FIELDS.every(field => field.reason.trim().length > 0)).toBe(true)
    })

    it('reproduces the corrected UI-SPEC reason copy field by field', () => {
      const expected: Record<string, string> = {
        gender: '用于健康报告与 AI 建议中的性别化解读',
        birthDate: '用于计算年龄，影响心血管与糖尿病风险评估',
        heightCm: '用于生成 BMI，影响风险评估与健康报告',
        weightKg: '用于生成 BMI，影响风险评估与健康报告',
        bmi: '用于风险评估的 BMI 代入；缺失按 22.0 估算',
        bloodType: '记录在健康报告与 AI 建议中，供就医与用血场景参考',
        allergyHistory: '让 AI 建议避开与过敏史冲突的饮食与用药提醒',
        familyHistory: '家族糖尿病史直接影响糖尿病风险评估得分',
        lifestyleHabits: '用于健康评分与 AI 建议的针对性'
      }

      for (const field of PROFILE_FIELDS) {
        expect(field.reason).toBe(expected[field.key])
      }
    })

    it('never claims the weight dimension: no reason contains 体重维度', () => {
      // W4 回归护栏：体重维度只消费实时 bmi 指标，不读档案列，因此没有任何
      // 档案字段可以诚实地声称它影响「体重维度」。
      expect(PROFILE_FIELDS.filter(field => field.reason.includes('体重维度'))).toEqual([])
    })

    it('mentions 风险 only in the risk-chain fields, so gender cannot claim a risk-engine consumer', () => {
      // 镜像后端 ProfileFieldCatalogTest.onlyRiskChainFieldsMentionRisk：
      // 真正进入 RiskAssessmentServiceImpl 的字段（birthDate 供年龄、heightCm/weightKg
      // 供存储 bmi、bmi 直接代入、familyHistory 供糖尿病评分）可以说「风险」；
      // 另外四个没有通往风险引擎的路径，声称它就是虚假消费者。
      const mentioningRisk = PROFILE_FIELDS.filter(field => field.reason.includes('风险')).map(
        field => field.key
      )

      expect(mentioningRisk).toEqual(['birthDate', 'heightCm', 'weightKg', 'bmi', 'familyHistory'])
      for (const key of ['gender', 'bloodType', 'allergyHistory', 'lifestyleHabits']) {
        const field = PROFILE_FIELDS.find(f => f.key === key)
        expect(field).toBeDefined()
        expect(field?.reason).not.toContain('风险')
      }
    })

    it('carries the backend absent-case assumption sentences', () => {
      const expected: Record<string, string | null> = {
        gender: null,
        birthDate: '缺少出生日期，年龄按 35 岁估算',
        heightCm: null,
        weightKg: null,
        bmi: '缺少 BMI，按 22.0 估算',
        bloodType: null,
        allergyHistory: null,
        familyHistory: '缺少家族病史，糖尿病风险评估按「无家族史」计算',
        lifestyleHabits: null
      }

      for (const field of PROFILE_FIELDS) {
        expect(field.assumption).toBe(expected[field.key])
      }
    })
  })

  describe('synthesizeEmptyCompleteness', () => {
    it('mirrors the backend 404 payload shape exactly', () => {
      const payload = synthesizeEmptyCompleteness()

      expect(payload.score).toBe(0)
      expect(payload.level).toBe('基础档案')
      expect(payload.filledCount).toBe(0)
      expect(payload.totalCount).toBe(PROFILE_FIELDS.length)
      expect(payload.hasProfile).toBe(false)
      expect(payload.assumptions).toEqual([NO_PROFILE_ASSUMPTION])
      expect(payload.fields.length).toBe(PROFILE_FIELDS.length)
    })

    it('totalCount derives from the field list, never from a literal', () => {
      const payload = synthesizeEmptyCompleteness()

      expect(payload.totalCount).toBe(payload.fields.length)
    })

    it('returns a fresh fields array each call', () => {
      const first = synthesizeEmptyCompleteness()
      first.fields[0].filled = true
      first.fields[0].label = '被改写'

      const second = synthesizeEmptyCompleteness()

      expect(second.fields[0].filled).toBe(false)
      expect(second.fields[0].label).toBe('性别')
      // 模块常量同样未被污染
      expect(PROFILE_FIELDS[0].filled).toBe(false)
      expect(PROFILE_FIELDS[0].label).toBe('性别')
    })

    it('returns a fresh assumptions array each call', () => {
      const first = synthesizeEmptyCompleteness()
      first.assumptions.push('被追加的句子')

      const second = synthesizeEmptyCompleteness()

      expect(second.assumptions).toEqual([NO_PROFILE_ASSUMPTION])
      expect(first.assumptions).not.toBe(second.assumptions)
    })
  })
})
