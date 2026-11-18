import { describe, it, expect } from 'vitest'
import {
  ERROR_MESSAGES,
  FIELD_LABELS,
  resolveMessage,
  fieldLabel
} from '@/utils/errorMessages'

const DICTIONARY_KEYS = [
  'threshold.saveFailed',
  'threshold.loadFailed',
  'threshold.deleteFailed',
  'threshold.duplicate',
  'profile.saveFailed',
  'profile.loadFailed',
  'push.saveFailed',
  'metric.saveFailed',
  'metric.deleteFailed',
  'report.generateFailed',
  'ai.parseFailed',
  'ocr.uploadFailed',
  'auth.expired',
  'auth.loginFailed',
  'auth.registerFailed',
  'network.offline',
  'server.unavailable',
  'dialog.unsavedConfirm',
  // Phase 14（14-03）：Copywriting Contract 的 26 键 + mood 的 2 个读写失败键
  'goal.loadFailed',
  'goal.saveFailed',
  'goal.deleteFailed',
  'reminder.loadFailed',
  'reminder.saveFailed',
  'reminder.deleteFailed',
  'screening.loadFailed',
  'screening.deleteFailed',
  'mood.saveFailed',
  'mood.loadFailed',
  'mood.deleteFailed',
  'realtime.loadFailed',
  'realtime.saveFailed',
  'alert.loadFailed',
  'alert.updateFailed',
  'alert.deleteFailed',
  'ai.loadFailed',
  'ai.deleteFailed',
  'wellness.loadFailed',
  'wellness.saveFailed',
  'user.loadFailed',
  'user.saveFailed',
  'metric.loadFailed',
  'score.loadFailed',
  'resource.gone'
]

// 心情表单的五个受约束字段（RecordMoodRequest 的 @NotNull / @Min / @Max）。
// 单独成常量：下面的循环断言依赖它，删键会立刻失败而不是静默通过。
const MOOD_FIELD_KEYS = ['moodScore', 'energyLevel', 'stressLevel', 'sleepQuality', 'entryDate']

const BANNED_COPY = /操作失败|请求失败|系统错误|出错了|Error|Failed|Invalid|Please|Sorry|Exception/

describe('ERROR_MESSAGES', () => {
  it('contains all scenario keys with non-empty copy', () => {
    expect(Object.keys(ERROR_MESSAGES)).toHaveLength(DICTIONARY_KEYS.length)
    for (const key of DICTIONARY_KEYS) {
      expect(ERROR_MESSAGES[key], `missing key: ${key}`).toBeTruthy()
      expect(ERROR_MESSAGES[key].trim().length).toBeGreaterThan(0)
    }
  })

  it('never contains generic or English wording', () => {
    for (const key of DICTIONARY_KEYS) {
      expect(ERROR_MESSAGES[key], `banned copy in ${key}`).not.toMatch(BANNED_COPY)
    }
  })

  it('caps every entry at 30 characters', () => {
    for (const key of DICTIONARY_KEYS) {
      expect(ERROR_MESSAGES[key].length, `too long: ${key}`).toBeLessThanOrEqual(30)
    }
  })
})

describe('resolveMessage', () => {
  it('prefers the backend message when present (D-05)', () => {
    expect(resolveMessage('threshold.saveFailed', '该指标已存在阈值，请直接编辑')).toBe(
      '该指标已存在阈值，请直接编辑'
    )
  })

  it('falls back to the scenario copy when no backend message is supplied', () => {
    expect(resolveMessage('threshold.saveFailed', undefined)).toBe(
      ERROR_MESSAGES['threshold.saveFailed']
    )
  })

  it('treats a whitespace-only backend message as absent', () => {
    expect(resolveMessage('threshold.saveFailed', '   ')).toBe(
      ERROR_MESSAGES['threshold.saveFailed']
    )
  })

  it('falls back to server.unavailable for an unknown scenario key', () => {
    expect(resolveMessage('unknown.key', undefined)).toBe(ERROR_MESSAGES['server.unavailable'])
  })

  it('never returns an empty string for an empty backend message', () => {
    expect(resolveMessage('threshold.saveFailed', '')).not.toBe('')
  })
})

describe('FIELD_LABELS / fieldLabel', () => {
  it('maps all 44 backend field names to Chinese labels', () => {
    expect(Object.keys(FIELD_LABELS)).toHaveLength(44)
    expect(FIELD_LABELS.metricKey).toBe('指标类型')
    expect(FIELD_LABELS.warningHigh).toBe('警告高值')
    expect(FIELD_LABELS.notes).toBe('备注说明')
  })

  it('covers every field the contract calls out as newly labelled', () => {
    expect(fieldLabel('moodScore')).toBe('心情评分')
    expect(fieldLabel('cronExpression')).toBe('Cron 表达式')
    expect(fieldLabel('targetValue')).toBe('目标值')
    expect(fieldLabel('clientSecret')).toBe('客户端密钥')
  })

  it('never resolves a mood field back to its raw key (the mood-400 raw-key leak)', () => {
    // 这条断言就是那个「本该捕获原始 key 泄漏」的断言：
    // 一旦有人删掉 FIELD_LABELS.moodScore，汇总会渲染 `moodScore：心情评分不能为空`。
    expect(fieldLabel('moodScore')).not.toBe('moodScore')
    for (const field of MOOD_FIELD_KEYS) {
      expect(fieldLabel(field), `missing label: ${field}`).not.toBe('')
      expect(fieldLabel(field), `raw key leaked: ${field}`).not.toBe(field)
    }
  })

  it('pins all four threshold field labels (THR-05: threshold violations must never render a blank label)', () => {
    expect(FIELD_LABELS.warningHigh).toBe('警告高值')
    expect(FIELD_LABELS.warningLow).toBe('警告低值')
    expect(FIELD_LABELS.criticalHigh).toBe('严重高值')
    expect(FIELD_LABELS.criticalLow).toBe('严重低值')
  })

  it('returns the label for a known field', () => {
    expect(fieldLabel('warningHigh')).toBe('警告高值')
  })

  it('returns an empty string for an unknown field so raw names are never rendered (D-15)', () => {
    expect(fieldLabel('nonexistentField')).toBe('')
  })
})