// 字段级校验错误：后端在 HTTP 400 时下发，前端据此做内联回填
export interface FieldError {
  field: string
  message: string
  code?: string
}

// API 统一响应格式
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  // 稳定错误码（如 resource.exists），用于调用点决定呈现方式（D-08）
  errorCode?: string
  // 字段校验明细（仅 HTTP 400 校验失败时下发）
  fieldErrors?: FieldError[]
}

// 分页响应
export interface PageResult<T> {
  total: number
  page: number
  size: number
  records: T[]
}

// 认证相关
export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
  email?: string
}

export interface AuthResponse {
  token: string
  user: UserVO
}

// 用户相关
export interface UserVO {
  id: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  status: number
  createdAt: string
  updatedAt: string
}

export interface UserProfileVO {
  profile: UserVO
}

export interface UpdateProfileRequest {
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

// 健康指标
export interface HealthMetricRequest {
  metricKey: string
  value: number
  recordDate: string
  unit?: string
  trend?: string
}

export interface HealthMetricVO {
  id: number
  userId: number
  metricKey: string
  metricName: string
  value: number
  unit?: string
  recordDate: string
  trend?: string
  createTime: string
}

export interface HealthMetricTrendVO {
  metricKey: string
  dataPoints: Array<{
    date: string
    value: number
  }>
}

// 实时指标
export interface RealtimeMetricRequest {
  metricKey?: string
  metricType?: string
  value: number
  unit?: string
  source?: string
  qualityScore?: number
}

export interface RealtimeMetricVO {
  id: number
  metricKey: string
  metricDisplayName: string
  value: number
  unit?: string
  source?: string
  recordedAt: string
}

export interface MetricTrendVO {
  metricKey: string
  metricDisplayName: string
  unit: string
  dataPoints: Array<{
    timestamp: string
    value: number
  }>
}

// 预警
export interface AlertVO {
  id: number
  alertType: string
  alertLevel: string
  title: string
  message: string
  metricKey?: string
  currentValue?: number
  thresholdValue?: number
  isRead: boolean
  isAcknowledged: boolean
  acknowledgedAt?: string
  createdAt: string
}

// 阈值
export interface UserThresholdRequest {
  userId: number
  metricKey: string
  warningHigh?: number
  criticalHigh?: number
  warningLow?: number
  criticalLow?: number
}

export interface UserThreshold {
  id: number
  userId: number
  metricKey: string
  warningHigh?: number
  criticalHigh?: number
  warningLow?: number
  criticalLow?: number
  createdAt: string
  updatedAt: string
}

// 健康评分
export interface HealthScoreVO {
  score: number
  level: string
  factors: Record<string, any>
  calculationMethod: string
  calculatedAt: string
  expiresAt: string
  isCached: boolean
  message?: string
  /**
   * 档案加权完整度（0-100）。null / undefined 表示「未知」—— 无任何健康数据时
   * 后端在评分器之前就返回 NO_DATA，此时前端不得渲染 0% 徽标。
   */
  profileCompleteness?: number | null
  /** 本次评分代入的默认值说明。 */
  assumptions?: string[]
}

// AI 相关
export interface AIChatRequest {
  sessionId: string
  question: string
}

export interface AIChatResponse {
  sessionId: string
  answer: string
  remainingCount: number
}

export interface ConversationVO {
  id: number
  question: string
  answer: string
  createTime: string
}

export interface AIHistoryResponse {
  history: ConversationVO[]
  remainingCount: number
}

export interface ChatSessionVO {
  sessionId: string
  lastMessageAt: string
  summary: string
}

export interface ChatContextVO {
  sessionId: string
  recentTurns: number
  context: Array<{
    question: string
    answer: string
    timestamp: string
  }>
}

export interface AIClassifyRequest {
  title: string
  content: string
}

export interface AIClassifyResponse {
  category: string
  tags: string[]
  summary: string
}

// 预防保健
export interface HealthProfileRequest {
  gender?: string
  birthDate?: string
  heightCm?: number
  weightKg?: number
  bloodType?: string
  allergyHistory?: string
  familyHistory?: string
  lifestyleHabits?: string
}

export interface HealthProfileVO {
  id: number
  userId: number
  gender?: string
  birthDate?: string
  heightCm?: number
  weightKg?: number
  bmi?: number
  bloodType?: string
  allergyHistory?: string
  familyHistory?: string
  lifestyleHabits?: string
  createTime: string
  updateTime: string
}

// 档案完整度（D-01：后端权威计算，前端只透传与渲染）
export interface ProfileFieldStatusVO {
  key: string
  label: string
  importance: 'required' | 'recommended' | 'optional'
  filled: boolean
  reason: string
  assumption?: string | null
}

export interface ProfileCompletenessVO {
  score: number
  level: string
  filledCount: number
  totalCount: number
  /**
   * 区分「完全没有档案行」与「有档案但很稀疏」。
   * 必须显式下发：前端不得从 score 反推这个状态（无档案 0% 与稀疏 0% 语义不同）。
   */
  hasProfile: boolean
  /** 风险引擎本次实际代入的假设句；无档案时为单条通用句。 */
  assumptions: string[]
  /** 按后端目录顺序下发的逐字段状态；分段数必须取该数组长度，不得硬编码。 */
  fields: ProfileFieldStatusVO[]
}

export interface RiskAssessmentVO {
  id: number
  userId: number
  profileId?: number
  diseaseName: string
  riskLevel: string
  riskScore?: number
  suggestion?: string
  /** 当前档案的加权完整度（0-100）；null 表示未知，不得当作 0% 渲染。 */
  profileCompleteness?: number | null
  /** 本次评估依赖的默认假设句（读时按当前档案重算）。 */
  assumptions?: string[]
  createTime: string
}

// 体检报告
export interface ExaminationReportVO {
  id: number
  userId: number
  reportName: string
  reportType?: string
  institution?: string
  reportDate?: string
  fileUrl: string
  ocrStatus: string
  abnormalSummary?: string
  createTime: string
  updateTime: string
}

export interface LabResultVO {
  id: number
  reportId: number
  name: string
  category?: string
  value: string
  unit?: string
  referenceRange?: string
  isAbnormal: number
  trend?: string
  sortOrder: number
  createTime: string
}

export interface OcrStatusVO {
  status: string
  errorMessage?: string
  completedTime?: string
}

// 保健指标
export interface WellnessMetricRequest {
  metricKey: string
  value: number
  recordDate: string
  unit?: string
  notes?: string
}

export interface WellnessMetricVO {
  id: number
  userId: number
  metricKey: string
  metricName: string
  value: number
  unit?: string
  recordDate: string
  notes?: string
  createTime: string
}

export interface WellnessSummaryVO {
  summaryDate: string
  avgSleepDuration: number | null
  avgSleepQuality: number | null
  totalSteps: number | null
  totalExerciseMinutes: number | null
  totalWaterIntake: number | null
  avgMood: number | null
  avgEnergy: number | null
  metrics: WellnessMetricSummary[]
}

export interface WellnessMetricSummary {
  metricKey: string
  displayName: string
  latestValue: number | null
  unit: string
  avgValue: number | null
  trend: number | null
}

export interface WellnessTrendVO {
  metricKey: string
  dates: string[]
  values: number[]
  unit: string
}

// 健康报告
export interface HealthReportVO {
  reportId: string
  generatedAt: string
  userInfo: {
    gender: string
    age: number
    heightCm: number
    weightKg: number
    bmi: number
  }
  overallScore: number
  scoreLevel: string
  dimensions: Array<{
    name: string
    score: number
    status: string
    description: string
  }>
  riskAlerts: Array<{
    level: string
    category: string
    description: string
    recommendation: string
  }>
  suggestions: Array<{
    category: string
    priority: string
    suggestion: string
    action: string
  }>
  summary: string
  isEstimated?: boolean
}

// 设备同步相关
export interface DeviceConnectionVO {
  platform: string
  platformName: string
  status: string
  statusName: string
  lastSyncAt?: string
  syncEnabled?: boolean
  platformUserId?: string
}

export interface SyncResultVO {
  platform: string
  platformName: string
  status: string
  statusName: string
  metricsCount?: number
  syncTime?: string
  errorMessage?: string
  durationMs?: number
}

export interface SyncHistoryVO {
  id: number
  platform: string
  platformName: string
  syncType: string
  syncTypeName: string
  metricsCount?: number
  status: string
  statusName: string
  errorMessage?: string
  syncTime: string
  durationMs?: number
}

export interface PlatformInfo {
  platform: string
  displayName: string
  configured: boolean
}

// 指标元数据相关

/**
 * 后端下发的推荐阈值四项（D-06：与 alertable 同一响应下发，来源为 alert_rule 种子派生，
 * 前端不做独立计算）。每项可独立为 null = 无界。
 */
export interface RecommendedThresholdVO {
  warningLow?: number | null
  warningHigh?: number | null
  criticalLow?: number | null
  criticalHigh?: number | null
}

export interface MetricDefinitionVO {
  canonicalKey: string
  aliases: string[]
  displayName: string
  category: 'HEALTH' | 'WELLNESS'
  unit: string
  precision: number
  step: number
  hardMin: number
  hardMax: number
  normalMin: number
  normalMax: number
  placeholder: string
  guideHint: string
  /**
   * 是否可配置告警阈值（D-03：由后端元数据注册中心权威判定，前端不维护白名单）。
   * D-19 定义：alert_rule 有 enabled 且四项阈值至少一列非 null。
   */
  alertable: boolean
  /** 后端推荐阈值（12-04 起后端总是下发；fallback 常量镜像种子值） */
  recommended: RecommendedThresholdVO
}

export type MetricMetaMap = Record<string, MetricDefinitionVO>
