import dayjs from 'dayjs'

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

// 获取指标显示名称
export const getMetricDisplayName = (metricKey: string): string => {
  const map: Record<string, string> = {
    // 小驼峰格式 (camelCase) - 用于 Metrics.vue, Monitor.vue 等
    heartRate: '心率',
    systolicBP: '收缩压',
    diastolicBP: '舒张压',
    glucose: '血糖',
    weight: '体重',
    bmi: 'BMI',
    temperature: '体温',
    bloodOxygen: '血氧',

    // 大写下划线格式 (SCREAMING_SNAKE_CASE) - 用于 Thresholds.vue 等
    HEART_RATE: '心率',
    BLOOD_PRESSURE: '血压',
    BLOOD_GLUCOSE: '血糖',
    BODY_TEMPERATURE: '体温',
    BLOOD_OXYGEN: '血氧'
  }
  return map[metricKey] || metricKey
}

// 获取指标显示名称（别名）
export const getMetricLabel = getMetricDisplayName

// 获取指标单位
export const getMetricUnit = (metricKey: string): string => {
  const map: Record<string, string> = {
    // 小驼峰格式
    heartRate: '次/分',
    systolicBP: 'mmHg',
    diastolicBP: 'mmHg',
    glucose: 'mmol/L',
    weight: 'kg',
    bmi: '',
    temperature: '°C',
    bloodOxygen: '%',

    // 大写下划线格式
    HEART_RATE: '次/分',
    BLOOD_PRESSURE: 'mmHg',
    BLOOD_GLUCOSE: 'mmol/L',
    BODY_TEMPERATURE: '°C',
    BLOOD_OXYGEN: '%'
  }
  return map[metricKey] || ''
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
    POOR: '较差'
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
