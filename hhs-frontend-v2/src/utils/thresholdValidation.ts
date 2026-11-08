import type { MetricDefinitionVO } from '@/types/api'

/**
 * 阈值合法性纯函数（D-09：前端即时反馈，非权威）。
 * 权威拦截在后端 @ValidThresholdRange（12-04）；本模块仅负责向导的
 * 「禁用下一步/保存 + 双输入框内联红字」体验。纯函数模块 —— 零 Vue/Pinia/Element Plus 依赖，
 * 可脱离组件在测试与向导中直接使用。
 */

/** 阈值草稿四项（D-10：每项可独立为 null = 无界，undefined 与 null 同义）。 */
export interface ThresholdDraft {
  criticalLow?: number | null
  warningLow?: number | null
  warningHigh?: number | null
  criticalHigh?: number | null
}

/**
 * 一条校验违规。fields 指出需要标红内联错误的输入框：
 * 次序违规命名两个涉及字段（同一句挂两个字段，UI-SPEC Contract 3），
 * 生理极限违规只涉及单个边界，命名该字段。
 */
export interface PairViolation {
  fields: Array<keyof ThresholdDraft>
  message: string
}

// 三条次序文案与 UI-SPEC Component Contract 3 的校验文案表逐字一致，成对出现。
const ORDER_PAIRS: Array<[keyof ThresholdDraft, keyof ThresholdDraft, string]> = [
  ['criticalLow', 'warningLow', '严重低值必须小于警告低值，请调整其中一个'],
  ['warningLow', 'warningHigh', '警告低值必须小于警告高值，请调整其中一个'],
  ['warningHigh', 'criticalHigh', '警告高值必须小于严重高值，请调整其中一个']
]

const DRAFT_FIELDS: Array<keyof ThresholdDraft> = [
  'criticalLow',
  'warningLow',
  'warningHigh',
  'criticalHigh'
]

function isUnset(v: number | null | undefined): v is null | undefined {
  return v === null || v === undefined
}

/**
 * 层级次序 + 生理极限校验。
 *
 * 次序（D-10）：合法次序为 criticalLow < warningLow < warningHigh < criticalHigh，
 * 仅当相邻两侧均非 null 时才要求成立（null = 无界，null 永远不是错误）；
 * 每个被破坏的相邻对各产生一条违规，fields 同时命名两侧字段。
 *
 * 极限（D-11）：传入 definition 且含数值 hardMin/hardMax 时，对每个落在
 * 闭区间 [hardMin, hardMax] 之外的非 null 边界追加一条生理极限违规。
 *
 * 次序用严格小于、运行时分级用包含式边界 —— 两者不矛盾：严格次序保证警告带宽度 > 0，
 * 包含式边界决定「恰好等于边界值时归属哪一档」（12-RESEARCH §Code Examples）。
 */
export function validateThresholdOrder(
  draft: ThresholdDraft,
  definition?: MetricDefinitionVO
): PairViolation[] {
  const out: PairViolation[] = []

  for (const [lowKey, highKey, message] of ORDER_PAIRS) {
    const low = draft[lowKey]
    const high = draft[highKey]
    if (isUnset(low) || isUnset(high)) continue
    if (low >= high) {
      out.push({ fields: [lowKey, highKey], message })
    }
  }

  if (definition) {
    for (const field of DRAFT_FIELDS) {
      const value = draft[field]
      if (isUnset(value)) continue
      if (value < definition.hardMin || value > definition.hardMax) {
        out.push({
          fields: [field],
          message: `该值超出该指标的生理极限范围（${definition.hardMin} ~ ${definition.hardMax} ${definition.unit}）`
        })
      }
    }
  }

  return out
}

/**
 * 是否至少设置了一个边界（UI-SPEC partial 契约：部分填写合法，
 * 仅四项全 null/undefined 时禁止提交 —— 由调用方禁用下一步/保存）。
 */
export function hasAnyBoundary(draft: ThresholdDraft): boolean {
  return DRAFT_FIELDS.some(field => !isUnset(draft[field]))
}
