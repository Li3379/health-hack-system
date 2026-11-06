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
  'dialog.unsavedConfirm'
]

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
  it('maps all 17 backend field names to Chinese labels', () => {
    expect(Object.keys(FIELD_LABELS)).toHaveLength(17)
    expect(FIELD_LABELS.metricKey).toBe('指标类型')
    expect(FIELD_LABELS.warningHigh).toBe('警告高值')
    expect(FIELD_LABELS.notes).toBe('备注说明')
  })

  it('returns the label for a known field', () => {
    expect(fieldLabel('warningHigh')).toBe('警告高值')
  })

  it('returns an empty string for an unknown field so raw names are never rendered (D-15)', () => {
    expect(fieldLabel('nonexistentField')).toBe('')
  })
})