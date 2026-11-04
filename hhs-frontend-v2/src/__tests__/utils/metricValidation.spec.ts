import { describe, it, expect } from 'vitest'
import {
  evaluateMetricValue,
  checkBloodPressureInversion,
  PANIC_LIMITS
} from '@/utils/metricValidation'
import { FALLBACK_METRIC_META } from '@/constants/metricMeta'

describe('metricValidation', () => {
  const systolicDef = FALLBACK_METRIC_META.systolicBP
  const diastolicDef = FALLBACK_METRIC_META.diastolicBP
  const glucoseDef = FALLBACK_METRIC_META.bloodGlucose
  const spo2Def = FALLBACK_METRIC_META.bloodOxygen
  const stepsDef = FALLBACK_METRIC_META.steps

  describe('evaluateMetricValue - Four-Tier Physiological Model', () => {
    it('should return null for empty, undefined, or NaN input', () => {
      expect(evaluateMetricValue(undefined, systolicDef)).toBeNull()
      expect(evaluateMetricValue(null, systolicDef)).toBeNull()
      expect(evaluateMetricValue(NaN, systolicDef)).toBeNull()
    })

    it('Tier 1 (Normal): should evaluate values within clinical reference range', () => {
      const res = evaluateMetricValue(120, systolicDef)
      expect(res).not.toBeNull()
      expect(res?.tier).toBe('NORMAL')
      expect(res?.status).toBe('success')
      expect(res?.badgeText).toBe('正常参考区间')
      expect(res?.canSave).toBe(true)
      expect(res?.isCritical).toBe(false)
      expect(res?.isHardLimit).toBe(false)
    })

    it('Tier 2 (Warning Deviation): should evaluate values outside normal range but within biological limits without blocking save', () => {
      // Systolic BP: normal 90 ~ 139, hard 40 ~ 300, panic max 180. Value 150 is Tier 2
      const resHigh = evaluateMetricValue(150, systolicDef)
      expect(resHigh).not.toBeNull()
      expect(resHigh?.tier).toBe('WARNING')
      expect(resHigh?.status).toBe('warning')
      expect(resHigh?.badgeText).toBe('指标偏高')
      expect(resHigh?.canSave).toBe(true) // Crucial: must allow saving!
      expect(resHigh?.isCritical).toBe(false)
      expect(resHigh?.isHardLimit).toBe(false)

      // Systolic BP: value 80 is below normalMin 90 but above panic min 70
      const resLow = evaluateMetricValue(80, systolicDef)
      expect(resLow?.tier).toBe('WARNING')
      expect(resLow?.badgeText).toBe('指标偏低')
      expect(resLow?.canSave).toBe(true)
    })

    it('Tier 3 (Critical Panic): should evaluate values exceeding panic limits as critical while permitting save after confirmation', () => {
      // Systolic BP: panic max is 180. Value 190 is Tier 3
      const res = evaluateMetricValue(190, systolicDef)
      expect(res).not.toBeNull()
      expect(res?.tier).toBe('CRITICAL')
      expect(res?.status).toBe('danger')
      expect(res?.badgeText).toBe('严重危急值')
      expect(res?.canSave).toBe(true) // Permitted to save with confirmation
      expect(res?.isCritical).toBe(true)
      expect(res?.isHardLimit).toBe(false)

      // Blood Oxygen: <= 90% is critical
      const o2Critical = evaluateMetricValue(88, spo2Def)
      expect(o2Critical?.tier).toBe('CRITICAL')
      expect(o2Critical?.isCritical).toBe(true)
      expect(o2Critical?.canSave).toBe(true)
    })

    it('Tier 4 (Hard Biological Bounds): should block save when exceeding human survival limits without wiping the value', () => {
      // Systolic BP: hard limits 40 ~ 300. Value 350 is Tier 4
      const resOver = evaluateMetricValue(350, systolicDef)
      expect(resOver).not.toBeNull()
      expect(resOver?.tier).toBe('HARD_BOUND')
      expect(resOver?.status).toBe('hard_danger')
      expect(resOver?.badgeText).toBe('超出存活极限')
      expect(resOver?.canSave).toBe(false) // Hard blocked!
      expect(resOver?.isHardLimit).toBe(true)
      expect(resOver?.message).toContain('收缩压有效生理区间为 40 ~ 300 mmHg')

      // Below hard min
      const resUnder = evaluateMetricValue(30, systolicDef)
      expect(resUnder?.tier).toBe('HARD_BOUND')
      expect(resUnder?.canSave).toBe(false)
      expect(resUnder?.isHardLimit).toBe(true)
    })

    it('should correctly handle value 0 as a valid numeric input (e.g. steps: 0)', () => {
      // Steps: normal 6000 ~ 10000, hard 0 ~ 150000
      const res = evaluateMetricValue(0, stepsDef)
      expect(res).not.toBeNull()
      expect(res?.tier).toBe('WARNING')
      expect(res?.badgeText).toBe('指标偏低')
      expect(res?.canSave).toBe(true)
      expect(res?.isHardLimit).toBe(false)
    })

    it('should safely fall back when definition is missing', () => {
      const res = evaluateMetricValue(100, undefined)
      expect(res).not.toBeNull()
      expect(res?.tier).toBe('NORMAL')
      expect(res?.canSave).toBe(true)
    })
  })

  describe('checkBloodPressureInversion', () => {
    it('should return false for valid physiological pressure (systolic > diastolic)', () => {
      expect(checkBloodPressureInversion(120, 80)).toBe(false)
      expect(checkBloodPressureInversion(140, 90)).toBe(false)
    })

    it('should return true when systolic is lower than or equal to diastolic', () => {
      // Inverted
      expect(checkBloodPressureInversion(80, 120)).toBe(true)
      // Equal
      expect(checkBloodPressureInversion(90, 90)).toBe(true)
    })

    it('should return false if either systolic or diastolic is missing', () => {
      expect(checkBloodPressureInversion(undefined, 80)).toBe(false)
      expect(checkBloodPressureInversion(120, undefined)).toBe(false)
      expect(checkBloodPressureInversion(null, null)).toBe(false)
      expect(checkBloodPressureInversion(NaN, 80)).toBe(false)
    })
  })
})
