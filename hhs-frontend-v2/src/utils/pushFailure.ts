// 推送失败原文 → 可操作中文提示。
//
// 为什么是独立 utils 而不是留在 PushSettings.vue：同一次故障有两条呈现通道 ——
//  ① 推送历史时间线（PushSettings.vue 的 getFailureHint）
//  ② 「测试推送」toast（stores/push.ts 的 testChannel → showError）
// 曾经只有 ① 做翻译，② 把后端 message 原样上屏。于是 `Authentication failed` 在历史里
// 显示「邮箱凭据或账号状态被服务器拒绝…」，测试推送却弹英文原文 —— 同一故障两套文案，
// 且用户看到的那一条不可操作。翻译必须只有一份，两个调用点共用。
//
// 本模块是纯函数模块（与 utils/appError.ts 同规格）：不 import store / element-plus，
// 避免错误翻译与错误展示重新耦合。

/**
 * 把推送失败原文翻成用户能懂、且知道该找谁的中文提示。
 *
 * 后端 `push_history.message` 的形态是「Spring 措辞（服务器原文）→ 处置建议」，
 * 例如 `Authentication failed (535 Login fail. Account is abnormal, ...) → SMTP 认证失败：…`。
 * 「测试推送」的 message 前面还会多一个后端拼的 `推送失败: ` 前缀。
 *
 * 只匹配已知的几类失败；认不出来的一律保留原文并标注「失败原因：」，绝不伪装成已知错误。
 *
 * 535 那段原文是**并列的候选原因**（账号异常 / 服务未开 / 口令错 / 频率限制 / 系统忙），
 * 服务器并没有告诉我们是哪一条。所以这里只说「凭据或账号状态」，并把服务器原文带在后面
 * —— 按其中一个短语断言「SMTP 服务没开」会让用户去改一个其实正确的设置。
 */
export function getFailureHint(message?: string | null): string {
  const raw = stripFailurePrefix(message)
  if (!raw) return '失败原因：未返回具体原因，请稍后重试'
  const lower = raw.toLowerCase()

  // SKIPPED 的两种成因（后端 PushChannelManager 区分了它们）：
  //  - 从未配置：这条「没送达」是真实信息，用户该去补配置；
  //  - 配置了但此刻不可达：例如 WebSocket 页面已关闭，稍后自然会好，不需要用户动手。
  // 两者的原文都以 "Skipped:" 开头，先于凭据判定处理，否则会被下面的分支吞掉。
  if (lower.includes('not configured')) {
    return '该渠道尚未配置，本次告警未通过它送达，请先在上方填写并启用'
  }
  // 实时推送的离线态：后端 WebSocketPushChannel.isAvailable 要求用户当前在线，
  // 否则返回 "Channel not available"。这不是故障，是实时通道的固有语义，
  // 用户只需要知道「打开页面就会恢复」，不必做任何事。
  if (lower.includes('channel not available')) {
    return '实时推送需要页面处于打开状态，离线期间的告警不会实时弹出，重新打开页面即恢复'
  }
  if (lower.includes('temporarily unreachable')) {
    return '该渠道当前不可达（例如页面已关闭导致实时连接断开），连接恢复后会自动恢复正常推送'
  }
  if (lower.includes('rate limit')) {
    return '该渠道推送过于频繁，本次已被限流跳过，请稍后再试'
  }
  if (lower.startsWith('skipped:')) {
    // 其余跳过原因不猜，至少说清「这次没发出去」
    return `本次未送达：${raw.replace(/^skipped:\s*/i, '')}`
  }

  // 连接类判定必须排在任何凭据判定之前，且覆盖做得到的所有措辞：
  //   - unknown host            —— 主机名解析失败
  //   - couldn't connect to host —— jakarta MailConnectException 的实际文案
  //     （javap 确认：`Couldn't connect to host, port: <host>, <port>; timeout <ms>`）
  //   - connection refused / timeout / connect timed out —— socket 层措辞
  // 漏掉 `couldn't connect to host` 时，这种最常见的连接失败会落到「失败原因：原文」，
  // 用户看到的是英文。
  if (lower.includes('unknown host') || lower.includes('unknownhostexception')) {
    return '邮件服务器地址错误，请联系管理员'
  }
  if (lower.includes("couldn't connect to host")
      || lower.includes('connection refused')
      || lower.includes('timeout')
      || lower.includes('timed out')
      || lower.includes('connectexception')) {
    return '邮件服务器连接失败，请稍后重试'
  }
  // 凭据判定只认 SMTP 认证的确切措辞。这里刻意不写裸 includes('auth')：
  // 任何含该子串的文本（authorization header、proxy authentic required、
  // 主机名 smtp.auth.*）都会被误标成「凭据无效」，把用户推向错误的排查方向。
  if (lower.includes('authentication failed') || lower.includes('535')) {
    // 只说一句用户能执行的话。服务器原文与 535 的并列原因**不再拼进来**：
    // 后端已经把 535 全文和中文处置建议写进 message，这里再贴一遍会让同一条
    // 失败在时间线里重复三次（后端原文 + 前端摘要 + 两段里的 535），
    // 用户要在三百多字里找那句「我该做什么」。原始文本由「查看详情」按需展开。
    return '邮箱凭据或账号状态被服务器拒绝，请联系管理员核对授权码与 SMTP 服务状态'
  }
  // 已经是中文的（后端自己写的可操作说明，如「请先启用该推送渠道后再发送测试」）
  // 原样返回 —— 再加一层「失败原因：」只会让同一句话读起来像说了两遍。
  if (HAS_CJK.test(raw)) {
    return raw
  }
  return `失败原因：${raw}`
}

/** 判定文案是否已含中文（后端可能直接给出可操作的中文说明）。 */
const HAS_CJK = /[\u4e00-\u9fff]/

/**
 * 去掉后端在「测试推送」里加的前缀（`推送失败: `）。
 *
 * 历史记录没有这个前缀；不剥掉的话，认不出的失败会渲染成「失败原因：推送失败: xxx」，
 * 同一句「失败」说两遍。
 */
function stripFailurePrefix(message?: string | null): string {
  return (message || '').trim().replace(/^推送失败\s*[:：]\s*/, '')
}