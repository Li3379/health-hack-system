import { defineStore } from 'pinia'
import { ref } from 'vue'
import { preventionApi } from '@/api/prevention'
import { normalizeApiError } from '@/utils/appError'
import {
  firstMissingKeyOf,
  missingFieldsOf,
  synthesizeEmptyCompleteness
} from '@/utils/profileFields'
import type { ProfileCompletenessVO, ProfileFieldStatusVO } from '@/types/api'

/** 关闭提醒后的静默期：7 天。 */
const SILENCE_MS = 7 * 24 * 60 * 60 * 1000

/**
 * 提醒静默时间戳的 localStorage 键。
 *
 * 刻意不含 userId（13-CONTEXT D-05 锁定值）：同一浏览器上切换多个账号会共享这个
 * 静默窗口 —— 已知限制，按设计接受。它是「提醒偏好」而不是会话状态，因此
 * `storage.clear()` / `logout()` **不清除**本键（见 utils/storage.ts 只删 token 与 user）。
 */
const DISMISS_KEY = 'hhs_profile_reminder_dismissed_at'

/** 后端以 404 表达「档案行不存在」时用于识别的业务码集合。 */
const NOT_FOUND_CODES = ['health.profile_not_found', 'resource.not_found']

export const useProfileCompletenessStore = defineStore('profileCompleteness', () => {
  const data = ref<ProfileCompletenessVO | null>(null)
  const loading = ref(false)
  const loadFailed = ref(false)
  /** 关闭时间戳的响应式镜像，让 showBanner 之类的 computed 在 dismiss() 后重新求值。 */
  const dismissedAt = ref<string | null>(null)

  // 单飞守卫。声明在 store 工厂作用域内（而非模块顶层），因此两个 Pinia 实例
  // 不会共享同一个 promise。
  let inFlight: Promise<void> | null = null

  /**
   * 拉取档案完整度。
   *
   * **单飞且可等待** —— 并发调用共享同一个 pending promise：
   * - 第二次调用直接 `return inFlight`，不再发第二个 HTTP 请求；
   * - 而且它**不会**提前 resolve。这是关键：13-07 / 13-09 的调用点会 `await` 之后再读
   *   `data`，如果这里像 metricMeta 那样在 `loading` 时直接 `return`，`await` 就成了
   *   空操作，调用方会读到尚未落定的 `null`（plan-check B1-R 记录的正是这个 bug）。
   *
   * 失败语义三态（UI-SPEC Contract 8）：
   * - 成功 → 原样保存后端 payload；
   * - 404 / 档案不存在 → 用静态目录合成 0%，`loadFailed` 保持 false（这是合法状态）；
   * - 其它失败 → `data` 保持原值（首载为 null），`loadFailed = true`，绝不合成 0%。
   *
   * 本函数永不抛出，也从不 toast —— `{ silent: true }` 已透传到 API 层。
   */
  const fetchCompleteness = (): Promise<void> => {
    if (inFlight) return inFlight

    // IIFE 同步启动：`loading.value = true` 在下面的赋值语句完成前就已生效。
    // 其 finally 只可能在 await 落定后执行，因此不可能在赋值前把 inFlight 清空。
    inFlight = (async () => {
      loading.value = true
      try {
        const res = await preventionApi.getCompleteness({ silent: true })
        data.value = res.data
        loadFailed.value = false
      } catch (error) {
        const err = normalizeApiError(error)
        if (err.status === 404 || (err.code != null && NOT_FOUND_CODES.includes(err.code))) {
          // 档案不存在是正常的引导状态，不是错误。
          data.value = synthesizeEmptyCompleteness()
          loadFailed.value = false
        } else {
          // 保留既有 data（首载时即 null）。宁可什么都不画，也不画一个假的 0%。
          loadFailed.value = true
        }
      } finally {
        loading.value = false
        inFlight = null
      }
    })()

    return inFlight
  }

  /**
   * 提醒是否处于静默期。
   *
   * 失败方向是「显示提醒」：键缺失、值不可解析都返回 false（T-13-17，fail-open）。
   * 边界精确到毫秒：正好 7 天仍算静默（`<=`），7 天 + 1ms 恢复显示。
   */
  const isDismissed = (): boolean => {
    const raw = localStorage.getItem(DISMISS_KEY)
    if (!raw) return false
    const parsed = Date.parse(raw)
    if (Number.isNaN(parsed)) return false
    return Date.now() - parsed <= SILENCE_MS
  }

  /** 记录关闭时间戳。整个 store 里这是本键唯一的写入点（横幅组件只 emit）。 */
  const dismiss = (): void => {
    const now = new Date().toISOString()
    localStorage.setItem(DISMISS_KEY, now)
    dismissedAt.value = now
  }

  /** 优先级最高的未填字段 key；全部已填时为 null。排序规则只在 utils/profileFields.ts 实现。 */
  const firstMissingKey = (): string | null => firstMissingKeyOf(data.value?.fields ?? [])

  /** 未填字段，保持目录顺序（横幅直接念名字，不再重排）。 */
  const missingFields = (): ProfileFieldStatusVO[] => missingFieldsOf(data.value?.fields ?? [])

  return {
    data,
    loading,
    loadFailed,
    dismissedAt,
    fetchCompleteness,
    isDismissed,
    dismiss,
    firstMissingKey,
    missingFields
  }
})
