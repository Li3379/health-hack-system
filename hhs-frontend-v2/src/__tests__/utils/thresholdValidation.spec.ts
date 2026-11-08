import { describe, it, expect } from 'vitest'
import {
  validateThresholdOrder,
  hasAnyBoundary,
  type ThresholdDraft
} from '@/utils/thresholdValidation'
import type { MetricDefinitionVO } from '@/types/api'

/** 心率元数据（hardMin 20 / hardMax 300，hardMax 与 fallback 常量一致） */
const heartRateDef: MetricDefinitionVO = {
  canonicalKey: 'heartRate',
  aliases: [],
  displayName: '心率',
  category: 'HEALTH',
  unit: '次/分',
  precision: 0,
  step: 1,
  hardMin: 20,
  hardMax: 300,
  normalMin: 60,
  normalMax: 100,
  placeholder: '例如: 75',
  guideHint: '正常静息心率 60 ~ 100 次/分',
  alertable: true,
  recommended: { warningLow: 50, warningHigh: 100, criticalLow: 40, criticalHigh: 120 }
}

/** 完全合法的草稿（收缩压推荐值形态）：80 < 90 < 140 < 160 */
const validDraft: ThresholdDraft = {
  criticalLow: 80,
  warningLow: 90,
  warningHigh: 140,
  criticalHigh: 160
}

describe('validateThresholdOrder — 次序（D-10）', () => {
  it('returns zero violations and hasAnyBoundary false for an all-null draft', () => {
    expect(validateThresholdOrder({})).toEqual([])
    expect(validateThresholdOrder({ criticalLow: null, warningLow: null, warningHigh: null, criticalHigh: null })).toEqual([])
    expect(hasAnyBoundary({})).toBe(false)
    expect(hasAnyBoundary({ criticalLow: null, warningLow: null, warningHigh: null, criticalHigh: null })).toBe(false)
  })

  it('returns zero violations for a fully ordered draft', () => {
    expect(validateThresholdOrder(validDraft)).toEqual([])
    expect(hasAnyBoundary(validDraft)).toBe(true)
  })

  it('flags criticalLow >= warningLow with both fields named and the exact UI-SPEC message', () => {
    const violations = validateThresholdOrder({
      criticalLow: 90,
      warningLow: 90,
      warningHigh: 140,
      criticalHigh: 160
    })
    expect(violations).toHaveLength(1)
    expect(violations[0].fields).toEqual(['criticalLow', 'warningLow'])
    expect(violations[0].message).toBe('严重低值必须小于警告低值，请调整其中一个')
  })

  it('flags warningLow >= warningHigh with both fields named and the exact UI-SPEC message', () => {
    const violations = validateThresholdOrder({
      criticalLow: 80,
      warningLow: 140,
      warningHigh: 140,
      criticalHigh: 160
    })
    expect(violations).toHaveLength(1)
    expect(violations[0].fields).toEqual(['warningLow', 'warningHigh'])
    expect(violations[0].message).toBe('警告低值必须小于警告高值，请调整其中一个')
  })

  it('flags warningHigh >= criticalHigh with both fields named and the exact UI-SPEC message', () => {
    const violations = validateThresholdOrder({
      criticalLow: 80,
      warningLow: 90,
      warningHigh: 160,
      criticalHigh: 160
    })
    expect(violations).toHaveLength(1)
    expect(violations[0].fields).toEqual(['warningHigh', 'criticalHigh'])
    expect(violations[0].message).toBe('警告高值必须小于严重高值，请调整其中一个')
  })

  it('treats null as unbounded: a draft with only criticalHigh set has zero violations', () => {
    const violations = validateThresholdOrder({ criticalHigh: 160 })
    expect(violations).toEqual([])
    expect(hasAnyBoundary({ criticalHigh: 160 })).toBe(true)
  })

  it('returns one violation per broken adjacent pair — two broken pairs yield two violations', () => {
    const violations = validateThresholdOrder({
      criticalLow: 90,
      warningLow: 90,
      warningHigh: 160,
      criticalHigh: 160
    })
    expect(violations).toHaveLength(2)
    expect(violations[0].fields).toEqual(['criticalLow', 'warningLow'])
    expect(violations[1].fields).toEqual(['warningHigh', 'criticalHigh'])
    // 每条违规都携带两个涉及字段，UI 才能把两个输入框同时标红
    for (const v of violations) {
      expect(v.fields).toHaveLength(2)
    }
  })
})

describe('validateThresholdOrder — 生理极限（D-11）', () => {
  it('reports a hard-limit violation above hardMax when a definition is supplied', () => {
    const violations = validateThresholdOrder({ warningHigh: 400 }, heartRateDef)
    expect(violations).toHaveLength(1)
    expect(violations[0].fields).toEqual(['warningHigh'])
    expect(violations[0].message).toBe('该值超出该指标的生理极限范围（20 ~ 300 次/分）')
  })

  it('reports a hard-limit violation below hardMin', () => {
    const violations = validateThresholdOrder({ criticalLow: 5 }, heartRateDef)
    expect(violations).toHaveLength(1)
    expect(violations[0].fields).toEqual(['criticalLow'])
    expect(violations[0].message).toBe('该值超出该指标的生理极限范围（20 ~ 300 次/分）')
  })

  it('accepts values exactly at hardMin/hardMax — the range is inclusive', () => {
    const violations = validateThresholdOrder(
      { criticalLow: 20, warningLow: 50, warningHigh: 100, criticalHigh: 300 },
      heartRateDef
    )
    expect(violations).toEqual([])
  })

  it('never flags unset boundaries against hard limits and stacks both violation kinds', () => {
    // null 边界不进入任何违规列表（D-10：null 不是错误）
    expect(validateThresholdOrder({ warningHigh: null }, heartRateDef)).toEqual([])

    // 次序违规与极限违规同时存在时各自独立报告
    const both = validateThresholdOrder(
      { criticalLow: 90, warningLow: 90, warningHigh: 140, criticalHigh: 400 },
      heartRateDef
    )
    expect(both).toHaveLength(2)
    expect(both[0].fields).toEqual(['criticalLow', 'warningLow'])
    expect(both[1].fields).toEqual(['criticalHigh'])
  })
})

describe('hasAnyBoundary — 部分填写契约', () => {
  it('is true when exactly one of the four boundaries is set', () => {
    expect(hasAnyBoundary({ warningLow: 50 })).toBe(true)
    expect(hasAnyBoundary({ criticalLow: null, warningLow: 50, warningHigh: undefined, criticalHigh: null })).toBe(true)
  })

  it('is false only when all four boundaries are null or undefined', () => {
    expect(hasAnyBoundary({})).toBe(false)
    expect(hasAnyBoundary({ criticalLow: null, warningLow: null, warningHigh: null, criticalHigh: null })).toBe(false)
    expect(hasAnyBoundary({ criticalLow: null, warningLow: undefined, warningHigh: null, criticalHigh: undefined })).toBe(false)
  })
})
