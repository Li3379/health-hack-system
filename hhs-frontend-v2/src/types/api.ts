// API 统一响应格式
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
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

export interface RiskAssessmentVO {
  id: number
  userId: number
  profileId?: number
  diseaseName: string
  riskLevel: string
  riskScore?: number
  suggestion?: string
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
