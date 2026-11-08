import { describe, it, expect } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import {
  getMetricDisplayName,
  getMetricLabel,
  getMetricUnit,
  getWellnessMetricUnit,
  formatDateTime,
  formatDate,
  formatTime,
  formatFileSize,
  formatNumber
} from '@/utils/format'
import { useMetricMetaStore } from '@/stores/metricMeta'

describe('format utils', () => {
  describe('getMetricDisplayName & getMetricLabel', () => {
    it('should correctly format all 16 HEALTH canonical metrics', () => {
      expect(getMetricDisplayName('heartRate')).toBe('心率')
      expect(getMetricDisplayName('systolicBP')).toBe('收缩压')
      expect(getMetricDisplayName('diastolicBP')).toBe('舒张压')
      expect(getMetricDisplayName('bloodGlucose')).toBe('血糖')
      expect(getMetricDisplayName('fastingGlucose')).toBe('空腹血糖')
      expect(getMetricDisplayName('postprandialGlucose')).toBe('餐后血糖')
      expect(getMetricDisplayName('bodyTemperature')).toBe('体温')
      expect(getMetricDisplayName('weight')).toBe('体重')
      expect(getMetricDisplayName('bmi')).toBe('BMI')
      expect(getMetricDisplayName('bloodOxygen')).toBe('血氧')
      expect(getMetricDisplayName('totalCholesterol')).toBe('总胆固醇')
      expect(getMetricDisplayName('triglycerides')).toBe('甘油三酯')
      expect(getMetricDisplayName('hdlCholesterol')).toBe('高密度脂蛋白')
      expect(getMetricDisplayName('ldlCholesterol')).toBe('低密度脂蛋白')
      expect(getMetricDisplayName('uricAcid')).toBe('尿酸')
      expect(getMetricDisplayName('creatinine')).toBe('肌酐')
    })

    it('should correctly format all 7 WELLNESS canonical metrics', () => {
      expect(getMetricDisplayName('sleepDuration')).toBe('睡眠时长')
      expect(getMetricDisplayName('sleepQuality')).toBe('睡眠质量')
      expect(getMetricDisplayName('steps')).toBe('步数')
      expect(getMetricDisplayName('exerciseMinutes')).toBe('运动时长')
      expect(getMetricDisplayName('waterIntake')).toBe('饮水量')
      expect(getMetricDisplayName('mood')).toBe('心情')
      expect(getMetricDisplayName('energy')).toBe('精力')
    })

    it('should resolve snake_case aliases to Chinese display names', () => {
      expect(getMetricDisplayName('heart_rate')).toBe('心率')
      expect(getMetricDisplayName('systolic_bp')).toBe('收缩压')
      expect(getMetricDisplayName('diastolic_bp')).toBe('舒张压')
      expect(getMetricDisplayName('blood_glucose')).toBe('血糖')
      expect(getMetricDisplayName('fasting_glucose')).toBe('空腹血糖')
      expect(getMetricDisplayName('postprandial_glucose')).toBe('餐后血糖')
      expect(getMetricDisplayName('body_temperature')).toBe('体温')
      expect(getMetricDisplayName('blood_oxygen')).toBe('血氧')
      expect(getMetricDisplayName('total_cholesterol')).toBe('总胆固醇')
      expect(getMetricDisplayName('uric_acid')).toBe('尿酸')
      expect(getMetricDisplayName('sleep_duration')).toBe('睡眠时长')
      expect(getMetricDisplayName('sleep_quality')).toBe('睡眠质量')
      expect(getMetricDisplayName('exercise_minutes')).toBe('运动时长')
      expect(getMetricDisplayName('water_intake')).toBe('饮水量')
    })

    it('should resolve medical acronyms and shortened aliases', () => {
      expect(getMetricDisplayName('sbp')).toBe('收缩压')
      expect(getMetricDisplayName('SBP')).toBe('收缩压')
      expect(getMetricDisplayName('dbp')).toBe('舒张压')
      expect(getMetricDisplayName('DBP')).toBe('舒张压')
      expect(getMetricDisplayName('spo2')).toBe('血氧')
      expect(getMetricDisplayName('SPO2')).toBe('血氧')
      expect(getMetricDisplayName('temp')).toBe('体温')
      expect(getMetricDisplayName('temperature')).toBe('体温')
      expect(getMetricDisplayName('glu')).toBe('血糖')
      expect(getMetricDisplayName('glucose')).toBe('血糖')
      expect(getMetricDisplayName('tc')).toBe('总胆固醇')
      expect(getMetricDisplayName('tg')).toBe('甘油三酯')
      expect(getMetricDisplayName('hdl')).toBe('高密度脂蛋白')
      expect(getMetricDisplayName('ldl')).toBe('低密度脂蛋白')
      expect(getMetricDisplayName('ua')).toBe('尿酸')
      expect(getMetricDisplayName('cr')).toBe('肌酐')
    })

    it('should support legacy uppercase and lowercase blood pressure formats', () => {
      expect(getMetricDisplayName('HEART_RATE')).toBe('心率')
      expect(getMetricDisplayName('BLOOD_PRESSURE')).toBe('血压')
      expect(getMetricDisplayName('blood_pressure')).toBe('血压')
      expect(getMetricDisplayName('bloodPressure')).toBe('血压')
      expect(getMetricDisplayName('BLOOD_GLUCOSE')).toBe('血糖')
      expect(getMetricDisplayName('BODY_TEMPERATURE')).toBe('体温')
      expect(getMetricDisplayName('BLOOD_OXYGEN')).toBe('血氧')
    })

    it('should convert unknown keys to Title Case words without underscores (per D-05)', () => {
      expect(getMetricDisplayName('vo2_max')).toBe('Vo2 Max')
      expect(getMetricDisplayName('custom_wellness_score')).toBe('Custom Wellness Score')
      expect(getMetricDisplayName('unknown_metric')).toBe('Unknown Metric')
      expect(getMetricDisplayName('deepSleepMinutes')).toBe('Deep Sleep Minutes')
      // Guarantee no underscores are leaked
      expect(getMetricDisplayName('vo2_max')).not.toContain('_')
    })

    it('should handle empty or whitespace-only keys safely', () => {
      expect(getMetricDisplayName('')).toBe('')
      expect(getMetricDisplayName('   ')).toBe('')
      // @ts-expect-error testing invalid input
      expect(getMetricDisplayName(null)).toBe('')
      // @ts-expect-error testing invalid input
      expect(getMetricDisplayName(undefined)).toBe('')
    })

    it('getMetricLabel should be an exact alias of getMetricDisplayName', () => {
      expect(getMetricLabel).toBe(getMetricDisplayName)
      expect(getMetricLabel('heartRate')).toBe('心率')
      expect(getMetricLabel('heart_rate')).toBe('心率')
      expect(getMetricLabel('vo2_max')).toBe('Vo2 Max')
    })
  })

  describe('getMetricUnit', () => {
    it('should return correct units for canonical keys', () => {
      expect(getMetricUnit('heartRate')).toBe('次/分')
      expect(getMetricUnit('systolicBP')).toBe('mmHg')
      expect(getMetricUnit('diastolicBP')).toBe('mmHg')
      expect(getMetricUnit('bloodGlucose')).toBe('mmol/L')
      expect(getMetricUnit('bodyTemperature')).toBe('°C')
      expect(getMetricUnit('weight')).toBe('kg')
      expect(getMetricUnit('bmi')).toBe('kg/m²')
      expect(getMetricUnit('bloodOxygen')).toBe('%')
      expect(getMetricUnit('totalCholesterol')).toBe('mmol/L')
      expect(getMetricUnit('uricAcid')).toBe('μmol/L')
      expect(getMetricUnit('sleepDuration')).toBe('小时')
      expect(getMetricUnit('steps')).toBe('步')
      expect(getMetricUnit('exerciseMinutes')).toBe('分钟')
      expect(getMetricUnit('waterIntake')).toBe('ml')
    })

    it('should return correct units for aliases and legacy blood pressure keys', () => {
      expect(getMetricUnit('heart_rate')).toBe('次/分')
      expect(getMetricUnit('sbp')).toBe('mmHg')
      expect(getMetricUnit('dbp')).toBe('mmHg')
      expect(getMetricUnit('temp')).toBe('°C')
      expect(getMetricUnit('spo2')).toBe('%')
      expect(getMetricUnit('glucose')).toBe('mmol/L')
      expect(getMetricUnit('BLOOD_PRESSURE')).toBe('mmHg')
      expect(getMetricUnit('blood_pressure')).toBe('mmHg')
      expect(getMetricUnit('bloodPressure')).toBe('mmHg')
    })

    it('should harmonize wellness water intake unit with authoritative registry', () => {
      expect(getWellnessMetricUnit('waterIntake')).toBe('ml')
      expect(getMetricUnit('waterIntake')).toBe('ml')
    })

    it('should dynamically read newly added metrics when Pinia store is active', () => {
      setActivePinia(createPinia())
      const store = useMetricMetaStore()
      store.metadataMap.customDynamicMetric = {
        canonicalKey: 'customDynamicMetric',
        aliases: ['custom_dynamic_metric'],
        displayName: '自定义动态指标',
        category: 'HEALTH',
        unit: '分',
        precision: 0,
        step: 1,
        hardMin: 0,
        hardMax: 100,
        normalMin: 60,
        normalMax: 100,
        placeholder: '例如: 80',
        guideHint: '测试动态指标说明',
        alertable: false,
        recommended: {}
      }

      expect(getMetricDisplayName('customDynamicMetric')).toBe('自定义动态指标')
      expect(getMetricDisplayName('custom_dynamic_metric')).toBe('自定义动态指标')
      expect(getMetricUnit('customDynamicMetric')).toBe('分')
    })

    it('should return empty string for unknown or empty keys', () => {
      expect(getMetricUnit('unknown_metric')).toBe('')
      expect(getMetricUnit('')).toBe('')
      expect(getMetricUnit('   ')).toBe('')
      // @ts-expect-error testing invalid input
      expect(getMetricUnit(null)).toBe('')
    })
  })

  describe('general formatters', () => {
    it('should format date and time', () => {
      const dateStr = '2026-11-04 14:30:00'
      expect(formatDate(dateStr)).toBe('2026-11-04')
      expect(formatTime(dateStr)).toBe('14:30:00')
      expect(formatDateTime(dateStr)).toBe('2026-11-04 14:30:00')
    })

    it('should format numbers and file size', () => {
      expect(formatNumber(12.3456, 2)).toBe('12.35')
      expect(formatFileSize(0)).toBe('0 B')
      expect(formatFileSize(1024)).toBe('1.00 KB')
      expect(formatFileSize(1048576)).toBe('1.00 MB')
    })
  })
})
