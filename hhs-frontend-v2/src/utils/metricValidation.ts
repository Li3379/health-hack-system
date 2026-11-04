import type { MetricDefinitionVO } from '@/types/api'

export type MetricTier = 'NORMAL' | 'WARNING' | 'CRITICAL' | 'HARD_BOUND'

export interface MetricEvaluation {
  tier: MetricTier
  status: 'success' | 'warning' | 'danger' | 'hard_danger'
  badgeText: string
  message: string
  canSave: boolean
  isCritical: boolean
  isHardLimit: boolean
}

/**
 * 临床急危重症阈值标准 (Panic Limits)
 * 严格对齐 D-05 锁定决策，触碰时触发二次确认模态弹窗与就医指引
 */
export const PANIC_LIMITS: Record<string, { min?: number; max?: number }> = {
  systolicBP: { min: 70, max: 180 },
  diastolicBP: { min: 40, max: 110 },
  bloodGlucose: { min: 2.8, max: 16.7 },
  fastingGlucose: { min: 2.8, max: 16.7 },
  postprandialGlucose: { min: 2.8, max: 16.7 },
  heartRate: { min: 45, max: 140 },
  bodyTemperature: { min: 35.0, max: 39.5 },
  bloodOxygen: { min: 90 } // 血氧 <= 90% 即为危急重症低氧血症
}

/**
 * 生理四梯度纯函数评估引擎
 * 严格按照 Tier 4 (物理极限拦截) -> Tier 3 (急危重症提示) -> Tier 2 (预警偏差宽容) -> Tier 1 (正常) 分流
 * 注意：0 值为有效生理输入（如步数 0 步或运动 0 分钟），不得被当作空值拦截
 */
export function evaluateMetricValue(
  value: number | undefined | null,
  def?: MetricDefinitionVO
): MetricEvaluation | null {
  if (value === undefined || value === null || isNaN(value)) {
    return null
  }

  // 无元数据时的安全兜底
  if (!def) {
    return {
      tier: 'NORMAL',
      status: 'success',
      badgeText: '数据正常',
      message: '指标已录入，无特殊边界限制',
      canSave: true,
      isCritical: false,
      isHardLimit: false
    }
  }

  // Tier 4 生理不可能极限判定（最高优先级，禁止保存，保留原值）
  if (value < def.hardMin || value > def.hardMax) {
    return {
      tier: 'HARD_BOUND',
      status: 'hard_danger',
      badgeText: '超出存活极限',
      message: `${def.displayName}有效生理区间为 ${def.hardMin} ~ ${def.hardMax} ${def.unit}，请核对是否多输了数字`,
      canSave: false,
      isCritical: false,
      isHardLimit: true
    }
  }

  // Tier 3 急危重症判定 (Panic Limits)
  const panic = PANIC_LIMITS[def.canonicalKey]
  if (panic) {
    const isOxygenCritical = def.canonicalKey === 'bloodOxygen' && value <= (panic.min ?? 90)
    const isGeneralCritical =
      (panic.min !== undefined && value <= panic.min) ||
      (panic.max !== undefined && value >= panic.max)

    if (isOxygenCritical || isGeneralCritical) {
      return {
        tier: 'CRITICAL',
        status: 'danger',
        badgeText: '严重危急值',
        message: `测量值已达到临床急危重症预警线（${def.displayName} ${value} ${def.unit}），若身体不适请尽快就医，保存前请核对`,
        canSave: true,
        isCritical: true,
        isHardLimit: false
      }
    }
  }

  // Tier 2 预警偏差判定（非阻塞，允许正常保存入库）
  if (value < def.normalMin || value > def.normalMax) {
    const isHigh = value > def.normalMax
    const direction = isHigh ? '偏高' : '偏低'
    const guideNote = def.guideHint ? `（${def.guideHint}）` : ''
    return {
      tier: 'WARNING',
      status: 'warning',
      badgeText: `指标${direction}`,
      message: `当前数值${direction}于常规参考区间${guideNote}，系统已记录，可正常保存`,
      canSave: true,
      isCritical: false,
      isHardLimit: false
    }
  }

  // Tier 1 正常参考区间
  return {
    tier: 'NORMAL',
    status: 'success',
    badgeText: '正常参考区间',
    message: `指标处于健康基准参考区间（${def.normalMin} ~ ${def.normalMax} ${def.unit}）`,
    canSave: true,
    isCritical: false,
    isHardLimit: false
  }
}

/**
 * 血压生理倒置检测纯函数 (收缩压与舒张压协同校验)
 * 当收缩压 <= 舒张压时返回 true，提示输入颠倒
 */
export function checkBloodPressureInversion(
  systolic: number | undefined | null,
  diastolic: number | undefined | null
): boolean {
  if (
    systolic === undefined ||
    systolic === null ||
    isNaN(systolic) ||
    diastolic === undefined ||
    diastolic === null ||
    isNaN(diastolic)
  ) {
    return false
  }

  return systolic <= diastolic
}
