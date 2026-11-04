import { describe, it, expect } from 'vitest'
import {
  toCanonicalKey,
  getAliases,
  isCanonicalKey,
  formatUnknownKey,
  CANONICAL_KEYS
} from '@/utils/metricNormalizer'

describe('metricNormalizer', () => {
  describe('toCanonicalKey', () => {
    it('should return the canonical key unchanged when passed canonical camelCase', () => {
      expect(toCanonicalKey('heartRate')).toBe('heartRate')
      expect(toCanonicalKey('systolicBP')).toBe('systolicBP')
      expect(toCanonicalKey('diastolicBP')).toBe('diastolicBP')
      expect(toCanonicalKey('bloodGlucose')).toBe('bloodGlucose')
      expect(toCanonicalKey('bodyTemperature')).toBe('bodyTemperature')
      expect(toCanonicalKey('bloodOxygen')).toBe('bloodOxygen')
      expect(toCanonicalKey('totalCholesterol')).toBe('totalCholesterol')
      expect(toCanonicalKey('sleepDuration')).toBe('sleepDuration')
      expect(toCanonicalKey('exerciseMinutes')).toBe('exerciseMinutes')
    })

    it('should normalize snake_case aliases to canonical keys', () => {
      expect(toCanonicalKey('heart_rate')).toBe('heartRate')
      expect(toCanonicalKey('systolic_bp')).toBe('systolicBP')
      expect(toCanonicalKey('diastolic_bp')).toBe('diastolicBP')
      expect(toCanonicalKey('blood_glucose')).toBe('bloodGlucose')
      expect(toCanonicalKey('body_temperature')).toBe('bodyTemperature')
      expect(toCanonicalKey('blood_oxygen')).toBe('bloodOxygen')
      expect(toCanonicalKey('total_cholesterol')).toBe('totalCholesterol')
      expect(toCanonicalKey('uric_acid')).toBe('uricAcid')
      expect(toCanonicalKey('sleep_duration')).toBe('sleepDuration')
      expect(toCanonicalKey('sleep_quality')).toBe('sleepQuality')
      expect(toCanonicalKey('exercise_minutes')).toBe('exerciseMinutes')
      expect(toCanonicalKey('water_intake')).toBe('waterIntake')
    })

    it('should normalize medical acronyms and abbreviations', () => {
      expect(toCanonicalKey('sbp')).toBe('systolicBP')
      expect(toCanonicalKey('SBP')).toBe('systolicBP')
      expect(toCanonicalKey('dbp')).toBe('diastolicBP')
      expect(toCanonicalKey('DBP')).toBe('diastolicBP')
      expect(toCanonicalKey('spo2')).toBe('bloodOxygen')
      expect(toCanonicalKey('SPO2')).toBe('bloodOxygen')
      expect(toCanonicalKey('temp')).toBe('bodyTemperature')
      expect(toCanonicalKey('temperature')).toBe('bodyTemperature')
      expect(toCanonicalKey('glu')).toBe('bloodGlucose')
      expect(toCanonicalKey('glucose')).toBe('bloodGlucose')
      expect(toCanonicalKey('tc')).toBe('totalCholesterol')
      expect(toCanonicalKey('tg')).toBe('triglycerides')
      expect(toCanonicalKey('hdl')).toBe('hdlCholesterol')
      expect(toCanonicalKey('ldl')).toBe('ldlCholesterol')
      expect(toCanonicalKey('ua')).toBe('uricAcid')
      expect(toCanonicalKey('cr')).toBe('creatinine')
      expect(toCanonicalKey('step')).toBe('steps')
    })

    it('should handle uppercase aliases and case variations', () => {
      expect(toCanonicalKey('HEART_RATE')).toBe('heartRate')
      expect(toCanonicalKey('SYSTOLIC_BP')).toBe('systolicBP')
      expect(toCanonicalKey('systolicBp')).toBe('systolicBP')
      expect(toCanonicalKey('diastolicBp')).toBe('diastolicBP')
      expect(toCanonicalKey('BLOOD_GLUCOSE')).toBe('bloodGlucose')
      expect(toCanonicalKey('BMI')).toBe('bmi')
      expect(toCanonicalKey('WEIGHT')).toBe('weight')
    })

    it('should pass through unrecognized custom keys unchanged', () => {
      expect(toCanonicalKey('unknown_metric')).toBe('unknown_metric')
      expect(toCanonicalKey('customScore')).toBe('customScore')
      expect(toCanonicalKey('vo2_max')).toBe('vo2_max')
    })

    it('should handle empty or whitespace-only inputs gracefully', () => {
      expect(toCanonicalKey('')).toBe('')
      expect(toCanonicalKey('   ')).toBe('')
      // @ts-expect-error testing invalid input
      expect(toCanonicalKey(null)).toBe(null)
      // @ts-expect-error testing invalid input
      expect(toCanonicalKey(undefined)).toBe(undefined)
    })
  })

  describe('getAliases', () => {
    it('should return alias list containing canonical key and known aliases', () => {
      const aliases = getAliases('heartRate')
      expect(aliases).toContain('heartRate')
      expect(aliases).toContain('heart_rate')
      expect(aliases).toContain('pulse')

      const bpAliases = getAliases('systolic_bp')
      expect(bpAliases).toContain('systolicBP')
      expect(bpAliases).toContain('sbp')
      expect(bpAliases).toContain('systolic_bp')
    })

    it('should return single-item array for unknown keys', () => {
      expect(getAliases('unknown_key')).toEqual(['unknown_key'])
    })
  })

  describe('isCanonicalKey', () => {
    it('should return true only for registered canonical keys', () => {
      expect(isCanonicalKey('heartRate')).toBe(true)
      expect(isCanonicalKey('systolicBP')).toBe(true)
      expect(isCanonicalKey('diastolicBP')).toBe(true)
      expect(isCanonicalKey('waterIntake')).toBe(true)
      expect(isCanonicalKey('energy')).toBe(true)

      expect(isCanonicalKey('heart_rate')).toBe(false)
      expect(isCanonicalKey('sbp')).toBe(false)
      expect(isCanonicalKey('unknown_metric')).toBe(false)
      expect(isCanonicalKey('')).toBe(false)
    })

    it('should verify all 23 canonical keys are recognized', () => {
      expect(CANONICAL_KEYS.size).toBe(23)
    })
  })

  describe('formatUnknownKey', () => {
    it('should convert snake_case unknown keys into Title Case words', () => {
      expect(formatUnknownKey('vo2_max')).toBe('Vo2 Max')
      expect(formatUnknownKey('unknown_metric')).toBe('Unknown Metric')
      expect(formatUnknownKey('custom_wellness_score')).toBe('Custom Wellness Score')
    })

    it('should split camelCase and kebab-case and title-case each word', () => {
      expect(formatUnknownKey('vo2Max')).toBe('Vo2 Max')
      expect(formatUnknownKey('custom-health-score')).toBe('Custom Health Score')
      expect(formatUnknownKey('deepSleepPercentage')).toBe('Deep Sleep Percentage')
    })

    it('should handle empty or whitespace strings', () => {
      expect(formatUnknownKey('')).toBe('')
      expect(formatUnknownKey('   ')).toBe('')
      // @ts-expect-error testing invalid input
      expect(formatUnknownKey(null)).toBe('')
    })
  })
})
