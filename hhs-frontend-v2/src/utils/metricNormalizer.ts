/**
 * 指标 Key 规范化与别名映射工具
 * 严格对齐后端 MetricKeyNormalizer.java，实现双向映射与缩写保护
 */
import { FALLBACK_METRIC_META } from '@/constants/metricMeta'

export const CANONICAL_KEYS: ReadonlySet<string> = new Set(Object.keys(FALLBACK_METRIC_META))

const EXACT_ALIAS_MAP: Record<string, string> = {}
const SIMPLIFIED_MAP: Record<string, string> = {}
const CANONICAL_TO_ALIASES: Record<string, string[]> = {}

const simplify = (str: string): string => {
  return str.toLowerCase().replace(/[-_]/g, '')
}

// 初始化别名字典
for (const [canonicalKey, def] of Object.entries(FALLBACK_METRIC_META)) {
  const allAliases = new Set<string>([canonicalKey, ...(def.aliases || [])])
  CANONICAL_TO_ALIASES[canonicalKey] = Array.from(allAliases)

  SIMPLIFIED_MAP[simplify(canonicalKey)] = canonicalKey

  for (const alias of allAliases) {
    EXACT_ALIAS_MAP[alias] = canonicalKey
    SIMPLIFIED_MAP[simplify(alias)] = canonicalKey
  }
}

/**
 * 将原始 key 归一化为标准的 camelCase 标识符
 * 支持下划线、大小写不敏感及临床医学常用缩写（如 sbp -> systolicBP, spo2 -> bloodOxygen）
 * 未知指标 key 原样透传
 */
export function toCanonicalKey(rawKey: string): string {
  if (!rawKey || typeof rawKey !== 'string') {
    return rawKey
  }

  const trimmed = rawKey.trim()
  if (!trimmed) {
    return trimmed
  }

  if (CANONICAL_KEYS.has(trimmed)) {
    return trimmed
  }

  const exactMatch = EXACT_ALIAS_MAP[trimmed]
  if (exactMatch) {
    return exactMatch
  }

  const simplifiedKey = simplify(trimmed)
  const simplifiedMatch = SIMPLIFIED_MAP[simplifiedKey]
  if (simplifiedMatch) {
    return simplifiedMatch
  }

  return trimmed
}

/**
 * 获取指标的所有已知别名列表（含标准 key 自身）
 * 未知 key 返回仅含自身的单元素数组，空值返回空数组
 */
export function getAliases(key: string): string[] {
  if (!key || typeof key !== 'string' || !key.trim()) {
    return []
  }

  const trimmed = key.trim()
  const canonicalKey = toCanonicalKey(trimmed)
  const aliases = CANONICAL_TO_ALIASES[canonicalKey]
  if (aliases && aliases.length > 0) {
    return [...aliases]
  }

  return [trimmed]
}

/**
 * 判断是否为已知标准指标 Key
 */
export function isCanonicalKey(key: string): boolean {
  if (!key || typeof key !== 'string') {
    return false
  }
  return CANONICAL_KEYS.has(key.trim())
}

/**
 * 对未知指标 Key 进行友好词组化转换 (per D-05)
 * 将下划线、短横线以及驼峰拆分为首字母大写的单词（例如 vo2_max -> Vo2 Max）
 * 彻底杜绝下划线代码外露
 */
export function formatUnknownKey(rawKey: string): string {
  if (!rawKey || typeof rawKey !== 'string') {
    return ''
  }

  const trimmed = rawKey.trim()
  if (!trimmed) {
    return ''
  }

  // 1. 将驼峰分词：如 vo2Max -> vo2 Max, unknownMetric -> unknown Metric
  const splitCamel = trimmed.replace(/([a-z0-9])([A-Z])/g, '$1 $2')

  // 2. 将下划线和短横线替换为空格
  const cleanSeparators = splitCamel.replace(/[_-]+/g, ' ')

  // 3. 将单词拆分并转换为首字母大写
  const words = cleanSeparators.split(/\s+/).filter(Boolean)
  if (words.length === 0) {
    return ''
  }

  return words
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ')
}
