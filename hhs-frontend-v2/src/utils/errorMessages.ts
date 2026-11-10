// 扁平中文文案字典（D-06）+ 字段标签表（D-15）。
// 本模块是纯数据模块 —— 零依赖、无副作用，确保任何层（组件/工具/store）都能安全引用。
// 结构刻意只有一层：<module>.<actionResult>，不做模块嵌套（D-06）。

/**
 * 场景回退文案（D-07）。
 * 后端没有下发具体中文时，调用点用自己传入的场景 key 取默认文案，
 * 而不是统一回退到「操作未完成」这类泛化措辞。
 *
 * 单条上限 30 个中文字符（由 errorMessages.spec.ts 断言保证）。
 */
export const ERROR_MESSAGES: Record<string, string> = {
  'threshold.saveFailed': '无法保存阈值，请检查高低值顺序后重试',
  'threshold.loadFailed': '无法加载阈值列表，请点击重试',
  'threshold.deleteFailed': '无法删除该条阈值，请稍后重试',
  'threshold.duplicate': '该指标已有阈值，是否改为编辑？',
  'profile.saveFailed': '无法保存健康档案，请检查填写内容后重试',
  'profile.loadFailed': '无法加载健康档案，请稍后重试',
  'push.saveFailed': '无法保存推送配置，请稍后重试',
  'metric.saveFailed': '无法保存指标记录，请检查指标类型与数值',
  'metric.deleteFailed': '无法删除该条指标记录，请稍后重试',
  'report.generateFailed': '报告生成未完成，请稍后重试',
  'ai.parseFailed': 'AI 解析未完成，请调整描述后重试',
  'ocr.uploadFailed': '报告上传失败，请检查文件格式与大小',
  'auth.expired': '登录已过期，请重新登录',
  'auth.loginFailed': '登录未成功，请检查用户名与密码',
  'auth.registerFailed': '注册未成功，请检查用户名与邮箱',
  'network.offline': '网络连接异常，请检查网络后重试',
  'server.unavailable': '服务暂时不可用，请稍后重试',
  'dialog.unsavedConfirm': '内容尚未保存，确定离开？',

  // --- 健康目标（views/goals）---
  'goal.loadFailed': '无法加载健康目标，请点击重试',
  'goal.saveFailed': '无法保存健康目标，请检查填写内容',
  'goal.deleteFailed': '无法删除该健康目标，请稍后重试',

  // --- 提醒（views/reminders）---
  'reminder.loadFailed': '无法加载提醒列表，请点击重试',
  'reminder.saveFailed': '无法保存提醒设置，请检查时间格式后重试',
  'reminder.deleteFailed': '无法删除该提醒，请稍后重试',

  // --- 体检报告（views/screening）---
  'screening.loadFailed': '无法加载体检报告，请稍后重试',
  'screening.deleteFailed': '无法删除该体检报告，请稍后重试',

  // --- 心情记录（views/mood）---
  'mood.saveFailed': '无法保存心情记录，请稍后重试',
  'mood.loadFailed': '无法加载心情记录，请点击重试',
  'mood.deleteFailed': '无法删除该心情记录，请稍后重试',

  // --- 实时监测（views/realtime）---
  'realtime.loadFailed': '无法加载实时数据，请点击重试',
  'realtime.saveFailed': '无法保存实时指标，请检查设备连接',

  // --- 健康预警（views/alerts）---
  'alert.loadFailed': '无法加载健康预警，请点击重试',
  'alert.updateFailed': '无法更新预警状态，请稍后重试',
  'alert.deleteFailed': '无法删除该预警，请稍后重试',

  // --- AI 会话（views/ai、components/ai-floating-ball）---
  'ai.loadFailed': '无法加载 AI 会话，请稍后重试',
  'ai.deleteFailed': '无法删除该会话，请稍后重试',

  // --- 保健数据（views/wellness）---
  'wellness.loadFailed': '无法加载保健数据，请稍后重试',
  'wellness.saveFailed': '无法保存保健记录，请稍后重试',

  // --- 设备配置（views/settings）---
  'device.loadFailed': '无法加载设备配置，请点击重试',
  'device.saveFailed': '无法保存设备配置，请稍后重试',
  'device.syncFailed': '设备同步未完成，请检查连接后重试',

  // --- 个人信息（views/user）---
  'user.loadFailed': '无法加载个人信息，请稍后重试',
  'user.saveFailed': '无法保存个人信息，请检查填写内容',

  // --- 指标与评分（views/health）---
  'metric.loadFailed': '无法加载指标记录，请点击重试',
  'score.loadFailed': '无法加载健康评分，请点击重试',

  // --- 通用资源消失（列表页删除后刷新）---
  'resource.gone': '该记录已不存在，请刷新列表'

  // 刻意复用既有键、不新增键的调用点（RESEARCH §"Dictionary key gaps" 的收敛结果）：
  // - reminders/Index.vue:181 复用 alert.updateFailed（通用「状态更新失败」措辞贴切）
  // - health/Score.vue:457 复用 score.loadFailed
  // - prevention/Risk.vue:310,335 复用 profile.loadFailed / profile.saveFailed
  // - settings/DevicePlatformConfig.vue:355 复用 device.saveFailed
  // - OcrHistory 复用 ai.loadFailed / ai.deleteFailed
  // - ai.sendFailed 刻意不新增：14-12 的发送失败走就地 retry 气泡，
  //   再加一个字典键会形成第二套、且无人消费的词汇表。
}

/**
 * 字段标签表（D-15）。
 * 后端只下发字段名，中文标签由前端拥有；未知字段返回空串，
 * 由调用方只渲染后端 message，绝不把 warningHigh 这类原始字段名露给用户。
 */
export const FIELD_LABELS: Record<string, string> = {
  metricKey: '指标类型',
  value: '指标数值',
  diastolicValue: '舒张压',
  recordDate: '记录日期',
  warningHigh: '警告高值',
  warningLow: '警告低值',
  criticalHigh: '严重高值',
  criticalLow: '严重低值',
  birthDate: '出生日期',
  gender: '性别',
  heightCm: '身高',
  weightKg: '体重',
  bmi: 'BMI',
  lifestyleHabits: '生活习惯',
  familyHistory: '家族病史',
  allergyHistory: '过敏史',
  notes: '备注说明',

  // --- UI-SPEC Copywriting Contract 的 22 个新标签 ---
  bloodType: '血型',
  clientId: '客户端 ID',
  clientSecret: '客户端密钥',
  confirmPassword: '确认密码',
  cronExpression: 'Cron 表达式',
  dateRange: '起止日期',
  email: '邮箱',
  file: '报告文件',
  institution: '体检机构',
  newPassword: '新密码',
  nickname: '昵称',
  oldPassword: '原密码',
  password: '密码',
  phone: '手机号',
  reminderType: '提醒类型',
  reportDate: '报告日期',
  reportName: '报告名称',
  targetValue: '目标值',
  title: '标题',
  trend: '趋势',
  unit: '单位',
  username: '用户名',

  // --- 心情表单（RecordMoodRequest 的 @NotNull / @Min / @Max 字段）---
  // 这五个键是承重的：moodScore 无标签时，一次 mood 400 会在汇总里渲染
  // `moodScore：心情评分不能为空` —— 把原始驼峰键露给用户。
  // notes 已在上面存在（`备注说明`），心情表单的备注字段直接复用，因此是 5 个而非 6 个。
  moodScore: '心情评分',
  energyLevel: '精力值',
  stressLevel: '压力值',
  sleepQuality: '睡眠质量',
  entryDate: '记录日期'
}

// 刻意不为以下字段加标签（Contract 4.5 规则 2：未知字段只渲染后端消息，绝不露原始键）：
// description / startDate / endDate / note / 设备配置的 6 个字段 / avatar。
// 理由：这些字段的后端消息本身已是完整中文句子，加标签只会制造噪音；
// 需要更贴切措辞的页面用 useFormServerErrors 的 `labels` 覆盖（见 UI-SPEC §"必须用 labels 覆盖的歧义字段"）。

/**
 * 解析最终展示文案：后端优先（D-05），其次场景字典，最后通用服务不可用。
 * backendMessage 为空串或纯空白时视为未提供，绝不返回空串。
 */
export function resolveMessage(key: string, backendMessage?: string): string {
  const fromBackend = backendMessage?.trim()
  if (fromBackend) return fromBackend
  const scenarioCopy = ERROR_MESSAGES[key]
  if (scenarioCopy) return scenarioCopy
  return ERROR_MESSAGES['server.unavailable']
}

/** 字段名 → 中文标签；未知字段返回空串（D-15）。 */
export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? ''
}