// 档案字段静态目录 —— 仅供渲染兜底使用。
//
// 本文件的唯一职责是「离线时也能把完整度画出来」：当 GET /api/prevention/profile/completeness
// 以 404 返回（档案行不存在）时，store 用 synthesizeEmptyCompleteness() 合成一个与后端
// 0% 语义完全一致的 payload。**本文件绝不可用于计算分数** —— D-01 规定完整度由后端
// ProfileFieldCatalog + ProfileCompletenessCalculator 权威计算，前端一旦自己算分就会
// 与后端漂移，且无法解释给用户。
//
// 目录内容（9 项、顺序、标签、重要度、reason 文案）逐字镜像后端
// hhs-backend/src/main/java/com/hhs/service/domain/ProfileFieldCatalog.java。
// 任何一侧改动都必须同步另一侧，并由 profileCompleteness.spec.ts 的一致性测试兜住。
//
// 字段优先级规则（firstMissingKeyOf / missingFieldsOf）也只在本文件实现一次：
// 13-05 的 store、13-06 的横幅、徽标与 `?focus=` 交接全部调用这里。若各处自行重排，
// 横幅指向的字段会与徽标和跳转定位的字段悄悄分叉。

import type { ProfileCompletenessVO, ProfileFieldStatusVO } from '@/types/api'

/** 无档案行时的唯一通用假设句，镜像后端 ProfileFieldCatalog.NO_PROFILE_ASSUMPTION。 */
export const NO_PROFILE_ASSUMPTION = '未填写健康档案，风险得分按通用参考值估算'

/**
 * 9 项字段目录，顺序与后端目录一致（gender → birthDate → heightCm → weightKg → bmi →
 * bloodType → allergyHistory → familyHistory → lifestyleHabits）。
 *
 * 所有条目 `filled: false`：这是一份「空档案」模板，真实状态只能来自后端。
 * 消费者必须通过 synthesizeEmptyCompleteness() 取深拷贝，不得直接改写本常量。
 */
export const PROFILE_FIELDS: ProfileFieldStatusVO[] = [
  {
    key: 'gender',
    label: '性别',
    importance: 'required',
    filled: false,
    reason: '用于健康报告与 AI 建议中的性别化解读',
    assumption: null
  },
  {
    key: 'birthDate',
    label: '出生日期',
    importance: 'required',
    filled: false,
    reason: '用于计算年龄，影响心血管与糖尿病风险评估',
    assumption: '缺少出生日期，年龄按 35 岁估算'
  },
  {
    key: 'heightCm',
    label: '身高',
    importance: 'recommended',
    filled: false,
    reason: '用于生成 BMI，影响风险评估与健康报告',
    assumption: null
  },
  {
    key: 'weightKg',
    label: '体重',
    importance: 'recommended',
    filled: false,
    reason: '用于生成 BMI，影响风险评估与健康报告',
    assumption: null
  },
  {
    key: 'bmi',
    label: 'BMI',
    importance: 'recommended',
    filled: false,
    reason: '用于风险评估的 BMI 代入；缺失按 22.0 估算',
    assumption: '缺少 BMI，按 22.0 估算'
  },
  {
    key: 'bloodType',
    label: '血型',
    importance: 'optional',
    filled: false,
    reason: '记录在健康报告与 AI 建议中，供就医与用血场景参考',
    assumption: null
  },
  {
    key: 'allergyHistory',
    label: '过敏史',
    importance: 'optional',
    filled: false,
    reason: '让 AI 建议避开与过敏史冲突的饮食与用药提醒',
    assumption: null
  },
  {
    key: 'familyHistory',
    label: '家族病史',
    importance: 'optional',
    filled: false,
    reason: '家族糖尿病史直接影响糖尿病风险评估得分',
    assumption: '缺少家族病史，糖尿病风险评估按「无家族史」计算'
  },
  {
    key: 'lifestyleHabits',
    label: '生活习惯',
    importance: 'optional',
    filled: false,
    reason: '用于健康评分与 AI 建议的针对性',
    assumption: null
  }
]

/** 重要度排序权重：required 优先于 recommended，recommended 优先于 optional。 */
const IMPORTANCE_RANK: Record<ProfileFieldStatusVO['importance'], number> = {
  required: 0,
  recommended: 1,
  optional: 2
}

/**
 * 返回未填字段中优先级最高的那一个 key，全部已填时返回 null。
 *
 * 规则（UI-SPEC Contract 7）：先按重要度 required → recommended → optional 排序，
 * 同一重要度内保持目录顺序（稳定排序），取第一个未填项。
 *
 * 注意「优先级高于目录顺序」：bloodType（optional）在目录里排在 heightCm（recommended）
 * 之前，但缺 heightCm 时必须先指向 heightCm。这条规则是本阶段唯一实现。
 */
export function firstMissingKeyOf(fields: ProfileFieldStatusVO[]): string | null {
  let bestIndex = -1
  let bestRank = Number.POSITIVE_INFINITY
  for (let index = 0; index < fields.length; index += 1) {
    const field = fields[index]
    if (field.filled) continue
    const rank = IMPORTANCE_RANK[field.importance] ?? IMPORTANCE_RANK.optional
    // 严格小于才替换：同重要度内保留更靠前的目录位置（稳定平局判定）。
    if (rank < bestRank) {
      bestRank = rank
      bestIndex = index
    }
  }
  return bestIndex === -1 ? null : fields[bestIndex].key
}

/**
 * 返回所有未填字段，保持目录顺序。
 *
 * 横幅需要「按目录顺序把缺的字段名念出来」，而不是再排一次优先级 —— 排序在这里
 * 由数组本身保证，消费者不必也不应重排。
 */
export function missingFieldsOf(fields: ProfileFieldStatusVO[]): ProfileFieldStatusVO[] {
  return fields.filter(field => !field.filled)
}

/**
 * 合成一个与后端 404 场景等价的无档案 payload。
 *
 * `hasProfile: false` 与单条通用 assume 句必须与后端完全对齐：风险页的补填面板直接
 * 渲染 `data.assumptions`，若这里给空数组，全新用户的面板会静默消失。
 *
 * 每次调用返回全新对象与全新数组（含 assumptions），消费者改写结果不会污染模块常量。
 */
export function synthesizeEmptyCompleteness(): ProfileCompletenessVO {
  const fields = PROFILE_FIELDS.map(field => ({ ...field }))
  return {
    score: 0,
    level: '基础档案',
    filledCount: 0,
    totalCount: fields.length,
    hasProfile: false,
    assumptions: [NO_PROFILE_ASSUMPTION],
    fields
  }
}
