// 全站唯一的错误 toast 出口（本阶段口径：src/utils/ 内只有本文件 import ElMessage）。
//
// 设计约束（来自 11-UI-SPEC 与 11-PLAN-04）：
// - Element Plus 的 grouping 不支持 VNode 消息，因此带按钮的常驻 toast 关闭 grouping，
//   改由本模块的 2000ms 闩锁去重。
// - toast 被 teleport 到 document.body，样式必须写在全局 theme.css，不能写在 scoped 里。
// - 本文件不得 import 任何 store / router，避免与 stores/auth.ts 形成循环依赖。

import { h } from 'vue'
import { ElMessage } from 'element-plus'
import type { MessageHandler, MessageOptions } from 'element-plus'
import { normalizeApiError, type ApiError } from '@/utils/appError'
import { resolveMessage } from '@/utils/errorMessages'

/**
 * 错误码 → 恢复动作标签（D-08）。
 * 调用方只需提供 onClick，标签从这里自动补齐；调用方显式给出的 label 优先。
 */
export const ERROR_CODE_ACTIONS: Record<string, string> = {
  'resource.exists': '打开编辑'
}

/** `showError` 的公开选项形状 —— Phase 12/13/14 的调用契约。 */
export interface ShowErrorOptions {
  /** 提供后渲染常驻的「重试」按钮；点击只执行该回调一次，绝不自动重试（D-03）。 */
  retry?: () => void | Promise<void>
  /** 恢复动作；`label` 可省略，命中 ERROR_CODE_ACTIONS 时自动补。 */
  action?: { label?: string; onClick: () => void }
  /** 只分类不呈现（D-09/D-10）：零 toast，仍返回 ApiError。 */
  silent?: boolean
  /**
   * 呈现校验失败（默认 false）。
   *
   * <p>D-01 的默认契约是 validation → 0 toast，由调用点内联 + 汇总呈现。但个别表单
   * 尚未接入 useFormServerErrors、没有任何内联绑定（例如阈值对话框），若仍静默，
   * 后端 400 会完全不可见。这些调用点显式 opt-in，让校验失败也走同一个 toast 出口。
   */
  presentValidation?: boolean
  /**
   * 呈现状态类失败（默认 false → 零 toast）。
   *
   * <p>403 / 404 的语义是「状态」，默认由页面上的状态块（`ErrorState`）承担呈现，
   * 因此默认**不弹任何 toast** —— 状态块与 toast 同时出现就是同一次失败的两次呈现。
   * 只有用户主动触发的动作没有对应状态块可落（例如「删除他人记录」被拒），
   * 调用点才显式 opt-in 一条常驻 toast。
   */
  presentStates?: boolean
}

/** 字典条文长度上限；后端下发的文案不受此限制，绝不截断。 */
const MAX_DICTIONARY_CHARS = 30
/** 最多同时可见 3 条，第 4 条挤掉最旧的非 retry 消息。 */
const MAX_VISIBLE_TOASTS = 3
/** VNode toast 的模块级去重窗口。 */
const DUPLICATE_WINDOW_MS = 2000

interface OpenToast {
  handler: MessageHandler
  /** retry 类不参与淘汰，避免把用户唯一的重试入口顶掉。 */
  retry: boolean
}

const openToasts: OpenToast[] = []
const recentToasts = new Map<string, number>()

function forgetToast(handler: MessageHandler): void {
  const idx = openToasts.findIndex(entry => entry.handler === handler)
  if (idx !== -1) openToasts.splice(idx, 1)
}

/**
 * 登记已弹出的 toast，并把可见数量压到上限。
 * 字符串分支与 VNode 分支共用同一个数组 —— 「最多 3 条」对两者统一生效。
 */
function registerToast(handler: MessageHandler, retry: boolean): void {
  // EP 的 grouping 合并会返回同一个 handler，重复登记会让计数失真。
  if (openToasts.some(entry => entry.handler === handler)) return
  openToasts.push({ handler, retry })

  while (openToasts.length > MAX_VISIBLE_TOASTS) {
    const victim = openToasts.find(entry => !entry.retry)
    // 全是 retry 类时宁可短暂多一条，也不掐掉用户唯一的重试入口。
    if (!victim) break
    openToasts.splice(openToasts.indexOf(victim), 1)
    victim.handler.close()
  }
}

/** 对象式调用 ElMessage，并接住 handler 以便登记 / 在关闭时同步撤销。 */
function emitToast(options: Omit<MessageOptions, 'onClose'>, retry: boolean): MessageHandler {
  const marker: { handler: MessageHandler | null } = { handler: null }
  const handler = ElMessage({
    ...options,
    onClose: () => {
      if (marker.handler) forgetToast(marker.handler)
    }
  })
  marker.handler = handler
  registerToast(handler, retry)
  return handler
}

/** VNode 分支的 2000ms 去重闩锁（EP 的 grouping 不支持 VNode）。 */
function isDuplicateVNodeToast(key: string, now: number): boolean {
  const last = recentToasts.get(key)
  if (last !== undefined && now - last < DUPLICATE_WINDOW_MS) return true

  recentToasts.set(key, now)
  if (recentToasts.size > 64) {
    for (const [k, ts] of recentToasts) {
      if (now - ts >= DUPLICATE_WINDOW_MS) recentToasts.delete(k)
    }
  }
  return false
}

/**
 * 呈现一次失败。返回归一化后的 ApiError，便于调用点继续分支。
 *
 * 四通道（UI-SPEC Toast contract）：
 * - validation / cancel / silent → 0 条 toast
 * - business（无动作）→ 1 条字符串 toast，grouping 合并重复
 * - business（带恢复动作）/ network / server / 带 retry → 1 条 VNode 常驻 toast
 * - auth(401) → 0 条（可见提示统一由 authStore.logout() 的 ElMessage.info 承担）
 * - forbidden / notFound → 0 条（状态块承担呈现）；显式 presentStates 时 1 条 duration:0 常驻 toast，无动作按钮
 *
 * 安全：showError 只把文案作为文本子节点交给 `h`，不使用 dangerouslyUseHTMLString，
 * 也不拼接 HTML —— 后端 message 不会被当作标记解释（T-11-10）。
 */
export function showError(
  raw: unknown,
  fallbackKey: string,
  opts?: ShowErrorOptions
): ApiError {
  const err = normalizeApiError(raw)

  // 取消 / 校验 / silent：分类完成但绝不呈现。
  // 校验失败默认零 toast（D-01）；未接入内联表单的调用点可显式 presentValidation 放行。
  if (err.kind === 'cancel') return err
  if (err.kind === 'validation' && opts?.presentValidation !== true) return err
  if (opts?.silent === true) return err
  // 403 / 404 是「状态」：默认零 toast，由页面状态块承担呈现（D-03）。
  // 必须放在 resolveMessage 之前 —— 不产生任何呈现的状态不该做文案解析。
  if ((err.kind === 'forbidden' || err.kind === 'notFound') && opts?.presentStates !== true) {
    return err
  }

  // 后端文案优先；只有走到字典回退时才考虑 30 字上限（字典本身已受约束，实际不会截断）。
  // 后端下发的超长中文必须完整换行显示，绝不 slice —— UI-SPEC long-text 兜底要求（backstop）。
  const resolved = resolveMessage(fallbackKey, err.message)
  const fromBackend = Boolean(err.message?.trim())
  const shown = fromBackend
    ? resolved
    : resolved.length > MAX_DICTIONARY_CHARS
      ? resolved.slice(0, MAX_DICTIONARY_CHARS)
      : resolved

  // 401：可见提示由 authStore.logout() 内部的 ElMessage.info 承担，
  // 此处再弹会让一次令牌过期出现两条消息（T-11-13）。刻意不在此处产生 toast。
  if (err.kind === 'auth') return err

  // D-03：调用方显式给的 retry 优先；没有时，网络/传输层 5xx 自动用拦截器挂上的
  // 「重发同一请求」回调，保证这类 toast 始终带「重试」按钮。
  // 重试再次失败时重新走一次 showError，用户不会因为重试而失去反馈。
  const autoRetry = err.retryable && err.retryRequest ? err.retryRequest : undefined
  const retryFn =
    opts?.retry ??
    (autoRetry
      ? () =>
          Promise.resolve(autoRetry()).catch(retryError => {
            showError(retryError, fallbackKey)
          })
      : undefined)
  const actionOnClick = opts?.action?.onClick
  // D-03：只有网络/传输层 5xx（retryable）才渲染「重试」。
  // 业务失败（含信封包装的系统异常）不可重试 —— 重发同一次写操作只会重复失败。
  const hasRetry = typeof retryFn === 'function' && err.retryable
  // 标签解析顺序：调用方显式 label → ERROR_CODE_ACTIONS 自动补 → 空串（不渲染死按钮）。
  const actionLabel = opts?.action
    ? opts.action.label || ERROR_CODE_ACTIONS[err.code ?? ''] || ''
    : ''
  const hasAction = Boolean(opts?.action) && actionLabel !== ''

  // 字符串分支：business 且没有可用动作。
  // 若调用方给了 action 但标签解析为空串，也退化为这一分支（不引入未审校的兜底文案）。
  // forbidden / notFound 必须进 VNode 分支：字符串分支的 duration 恒为 4000，
  // 而状态类失败要求 duration: 0 常驻（UI-SPEC Contract 5 §5.2）。
  // 二者都不带动作按钮 —— 权限不会因重试而改变，资源消失也不该由 toast 提供出口。
  const needsVNode =
    hasRetry ||
    hasAction ||
    err.kind === 'network' ||
    err.kind === 'server' ||
    err.kind === 'forbidden' ||
    err.kind === 'notFound'
  if (!needsVNode) {
    emitToast(
      {
        type: 'error',
        message: shown,
        grouping: true,
        duration: 4000,
        showClose: true
      },
      false
    )
    return err
  }

  // VNode 分支：grouping 不支持 VNode，改由闩锁去重；duration: 0 常驻以便键盘可达。
  const latchKey = `${shown}|${hasRetry ? 'retry' : actionLabel}`
  if (isDuplicateVNodeToast(latchKey, Date.now())) return err

  let activeHandler: MessageHandler | null = null
  const handleRetry = () => {
    activeHandler?.close()
    // 只执行调用方回调一次，绝不自动重试（D-03：写操作会被重复提交）。
    if (retryFn) {
      Promise.resolve(retryFn()).catch(() => undefined)
    }
  }

  const children = [h('span', { class: 'error-toast__text' }, shown)]
  if (hasRetry) {
    children.push(
      h(
        'button',
        {
          type: 'button',
          class: 'error-toast__retry',
          // 可访问名必须带上对象：裸「重试」无法让读屏用户知道在重试什么。
          'aria-label': `重试：${shown}`,
          onClick: handleRetry
        },
        '重试'
      )
    )
  } else if (hasAction) {
    children.push(
      h(
        'button',
        {
          type: 'button',
          class: 'error-toast__action',
          'aria-label': actionLabel,
          onClick: () => actionOnClick?.()
        },
        actionLabel
      )
    )
  }

  const vnode = h('div', { class: 'error-toast' }, children)
  activeHandler = emitToast(
    {
      type: 'error',
      message: vnode,
      grouping: false,
      duration: 0,
      showClose: true
    },
    hasRetry
  )

  return err
}