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
  'metric.saveFailed': '无法保存指标记录，请检查指标类型与数值',
  'metric.deleteFailed': '无法删除该条指标记录，请稍后重试',
  'report.generateFailed': '报告生成未完成，请稍后重试',
  'ai.parseFailed': 'AI 解析未完成，请调整描述后重试',
  'ocr.uploadFailed': '报告上传失败，请检查文件格式与大小',
  'auth.expired': '登录已过期，请重新登录',
  'network.offline': '网络连接异常，请检查网络后重试',
  'server.unavailable': '服务暂时不可用，请稍后重试',
  'dialog.unsavedConfirm': '内容尚未保存，确定离开？'
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
  notes: '备注说明'
}

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