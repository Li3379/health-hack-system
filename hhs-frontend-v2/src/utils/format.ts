import dayjs from 'dayjs'
import { FALLBACK_METRIC_META } from '@/constants/metricMeta'
import { toCanonicalKey, formatUnknownKey } from '@/utils/metricNormalizer'

// 遗留大写下划线格式备用字典 (用于极个别历史组件或静态配置保底)
const LEGACY_UPPERCASE_MAP: Record<string, string> = {
  HEART_RATE: '心率',
  BLOOD_PRESSURE: '血压',
  BLOOD_GLUCOSE: '血糖',
  BODY_TEMPERATURE: '体温',
  BLOOD_OXYGEN: '血氧'
}

const LEGACY_UPPERCASE_UNIT_MAP: Record<string, string> = {
  HEART_RATE: '次/分',
  BLOOD_PRESSURE: 'mmHg',
  BLOOD_GLUCOSE: 'mmol/L',
  BODY_TEMPERATURE: '°C',
  BLOOD_OXYGEN: '%'
}

// 格式化日期时间
export const formatDateTime = (date: string | Date, format = 'YYYY-MM-DD HH:mm:ss'): string => {
  return dayjs(date).format(format)
}

// 格式化日期
export const formatDate = (date: string | Date): string => {
  return dayjs(date).format('YYYY-MM-DD')
}

// 格式化时间
export const formatTime = (date: string | Date): string => {
  return dayjs(date).format('HH:mm:ss')
}

// 相对时间
export const formatRelativeTime = (date: string | Date): string => {
  const now = dayjs()
  const target = dayjs(date)
  const diff = now.diff(target, 'second')

  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  return formatDate(date)
}

// 格式化数字
export const formatNumber = (num: number, decimals = 2): string => {
  return num.toFixed(decimals)
}

// 格式化文件大小
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`
}

/**
 * 获取指标显示名称
 * 透明集成 Key 归一化器与权威 23 项元数据字典
 * 未知指标 key 自动执行首字母大写词组化转换，杜绝下划线暴露 (per D-03, D-05)
 */
export const getMetricDisplayName = (metricKey: string): string => {
  if (!metricKey || typeof metricKey !== 'string') {
    return ''
  }

  const trimmed = metricKey.trim()
  if (!trimmed) {
    return ''
  }

  // 1. 通过 Normalizer 归一化为权威 camelCase
  const canonical = toCanonicalKey(trimmed)

  // 2. 检索权威元数据静态字典
  if (FALLBACK_METRIC_META[canonical]) {
    return FALLBACK_METRIC_META[canonical].displayName
  }

  // 3. 兼容检查历史遗留大写下划线字典
  if (LEGACY_UPPERCASE_MAP[trimmed] || LEGACY_UPPERCASE_MAP[canonical]) {
    return LEGACY_UPPERCASE_MAP[trimmed] || LEGACY_UPPERCASE_MAP[canonical]
  }

  // 4. 未知/自定义 Key 优雅降级为可读 Title Case 词组 (例如 vo2_max -> Vo2 Max)
  return formatUnknownKey(trimmed)
}

// 获取指标显示名称（别名）
export const getMetricLabel = getMetricDisplayName

/**
 * 获取指标单位
 * 经归一化后从权威元数据字典中查询标准单位，未知指标返回空字符串
 */
export const getMetricUnit = (metricKey: string): string => {
  if (!metricKey || typeof metricKey !== 'string') {
    return ''
  }

  const trimmed = metricKey.trim()
  if (!trimmed) {
    return ''
  }

  // 1. 归一化为权威 camelCase
  const canonical = toCanonicalKey(trimmed)

  // 2. 检索权威元数据静态字典中的单位
  if (FALLBACK_METRIC_META[canonical]) {
    return FALLBACK_METRIC_META[canonical].unit
  }

  // 3. 兼容检查历史遗留大写下划线字典
  if (LEGACY_UPPERCASE_UNIT_MAP[trimmed] || LEGACY_UPPERCASE_UNIT_MAP[canonical]) {
    return LEGACY_UPPERCASE_UNIT_MAP[trimmed] || LEGACY_UPPERCASE_UNIT_MAP[canonical]
  }

  return ''
}

// 获取预警级别颜色
export const getAlertLevelColor = (level: string): string => {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'info'
  }
  return map[level] || 'info'
}

// 获取预警级别标签（别名）
export const getAlertLevelLabel = getAlertLevelColor

// 获取预警类型标签
export const getAlertTypeLabel = (type: string): string => {
  const map: Record<string, string> = {
    CRITICAL: '严重',
    WARNING: '警告',
    INFO: '提示',
    TREND: '趋势'
  }
  return map[type] || type
}

// 获取健康评分等级
export const getScoreLevelLabel = (level: string): string => {
  const map: Record<string, string> = {
    EXCELLENT: '优秀',
    GOOD: '良好',
    FAIR: '一般',
    POOR: '较差',
    NO_DATA: '暂无数据'
  }
  return map[level] || level
}

// 获取风险等级颜色
export const getRiskLevelColor = (level: string): string => {
  const map: Record<string, string> = {
    HIGH: 'danger',
    MEDIUM: 'warning',
    LOW: 'success'
  }
  return map[level] || 'info'
}

// 保健指标配置
export const WELLNESS_METRICS: Record<
  string,
  { label: string; unit: string; icon: string; color: string }
> = {
  sleepDuration: { label: '睡眠时长', unit: '小时', icon: 'Moon', color: '#9b59b6' },
  sleepQuality: { label: '睡眠质量', unit: '分', icon: 'Star', color: '#8e44ad' },
  steps: { label: '步数', unit: '步', icon: 'Aim', color: '#3498db' },
  exerciseMinutes: { label: '运动时长', unit: '分钟', icon: 'Timer', color: '#2ecc71' },
  waterIntake: { label: '饮水量', unit: '杯', icon: 'Coffee', color: '#00bcd4' },
  mood: { label: '心情', unit: '分', icon: 'Sunny', color: '#f1c40f' },
  energy: { label: '精力', unit: '分', icon: 'Lightning', color: '#e74c3c' }
}

// 心情等级描述
export const MOOD_LEVELS = ['很差', '较差', '一般', '较好', '很好']

// 获取心情描述
export const getMoodDescription = (value: number): string => {
  const idx = Math.max(0, Math.min(4, Math.round(value) - 1))
  return MOOD_LEVELS[idx]
}

export const getWellnessMetricLabel = (key: string): string => WELLNESS_METRICS[key]?.label || key
export const getWellnessMetricUnit = (key: string): string => WELLNESS_METRICS[key]?.unit || ''
export const getWellnessMetricIcon = (key: string): string =>
  WELLNESS_METRICS[key]?.icon || 'DataLine'
export const getWellnessMetricColor = (key: string): string =>
  WELLNESS_METRICS[key]?.color || '#409eff'
