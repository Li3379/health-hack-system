import { describe, it, expect } from 'vitest'
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { resolve, relative, sep } from 'node:path'

/**
 * Phase 14 的结构性门禁：把四条「人工 grep」变成会失败的测试。
 *
 * <p>研究结论很直接：一个需要有人记得去跑的 grep，就是里程碑关闭之后不再跑的那个
 * grep。因此四条检查全部落在本文件里，`test:run` 即强制执行。
 *
 * <p>三条检查的原始形态（UI-SPEC Contract 6 §6.3）在本阶段被**修正**过，原因写在
 * `14-RESEARCH.md` §「Contract 6.3's four checks — three need correction」：严格的
 * 「store 内零 message」会禁掉契约允许的 success toast；未排除的 cancel 模式会命中
 * 两个必须保留比较的工具模块；过宽的样式模式会命中一个合法使用 danger token 的
 * 危险区确认对话框。照抄原版会把这三条做成「合法的红」，团队就会开始忽略门禁。
 *
 * <p>检查 ① 的头条数字是 **87 → 4**，不是 87 → 0：两个迁移 plan（14-06 / 14-11）
 * 刻意保留了四个客户端文件预检查作为 `保持` —— 它们校验用户刚选中、尚未发出任何
 * 请求的文件，所在组件也没有 `el-form-item` 可挂内联错误。要求 0 的门禁无法被
 * 产出它的那组 plan 满足。
 *
 * <p>**允许清单按 `(path, fn, literal)` 三元组定位，不含任何行号。** 14-06 与 14-11
 * 在本 wave 之前就已在两个函数上方插入了 import 与模板块，任何今天写下的行号在本
 * 文件运行时都已失效。按函数名 + 字面量定位可以承受任意插入。
 */

const SRC_ROOT = resolve(process.cwd(), 'src')
const TESTS_DIR = '__tests__'

// ---------------------------------------------------------------------------
// 源码遍历与注释剥离
// ---------------------------------------------------------------------------

/** 递归收集 `src` 下的 `.vue` / `.ts`，跳过 `__tests__`。 */
function collectSources(dir: string = SRC_ROOT, out: string[] = []): string[] {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = resolve(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === TESTS_DIR) continue
      collectSources(full, out)
    } else if (/\.(vue|ts)$/.test(entry.name)) {
      out.push(full)
    }
  }
  return out
}

/** `src` 相对路径，统一用 `/` 分隔，便于与允许清单字面量比对。 */
function srcPath(absPath: string): string {
  return relative(SRC_ROOT, absPath).split(sep).join('/')
}

interface SourceLine {
  /** 1-based 行号，仅用于失败信息，绝不参与断言比较。 */
  lineNo: number
  text: string
}

/**
 * 剥离注释后再逐行返回。
 *
 * <p>沿用 `ErrorLayerSample.spec.ts` 的做法：整行 `//`、`*`、`/*` 开头视为注释丢弃。
 * 这样「为什么不再用 ElMessage.error」这类解释性注释不会把检查弄红，而真正被注释掉
 * 的旧代码也不会假装成残留。
 *
 * <p>行号保持原文件的 1-based 位置（被丢弃的行仍然计数），所以失败信息能指回真实
 * 位置 —— 但没有任何断言读它。
 */
function codeLines(absPath: string): SourceLine[] {
  const raw = readFileSync(absPath, 'utf8').split(/\r?\n/)
  const out: SourceLine[] = []
  raw.forEach((text, i) => {
    const trimmed = text.trimStart()
    if (
      trimmed.startsWith('//') ||
      trimmed.startsWith('*') ||
      trimmed.startsWith('/*')
    ) {
      return
    }
    out.push({ lineNo: i + 1, text })
  })
  return out
}

// ---------------------------------------------------------------------------
// 检查 ① —— 裸错误 toast 残留
// ---------------------------------------------------------------------------

/**
 * 四个 `保持` 的客户端文件预检查：它们校验的是用户刚选中、尚未发出任何请求的文件。
 * 不是 API 失败 —— 没有 `ApiError` 可传、没有 `kind` 可分类。
 *
 * <p>每个文件两条，永远不多于两条。键是 `(path, fn, literal)`，**没有行号**。
 */
const RESIDUE_ALLOWLIST: ReadonlyArray<{
  path: string
  fn: string
  literal: string
}> = [
  // user/Profile.vue —— 头像上传前的类型与体积预检查
  { path: 'views/user/Profile.vue', fn: 'beforeAvatarUpload', literal: '只能上传图片文件' },
  { path: 'views/user/Profile.vue', fn: 'beforeAvatarUpload', literal: '图片大小不能超过 2MB' },
  // data-input/components/OcrInput.vue —— OCR 上传前的体积与类型预检查
  {
    path: 'views/data-input/components/OcrInput.vue',
    fn: 'handleFileChange',
    literal: '文件大小不能超过10MB'
  },
  {
    path: 'views/data-input/components/OcrInput.vue',
    fn: 'handleFileChange',
    literal: '只能上传图片文件'
  }
]

const BARE_ERROR_TOAST = /ElMessage\s*\.\s*error\s*\(/

/**
 * 判断某行是否处于文件顶层（缩进 0 或 2 空格），用于识别函数声明。
 * `.vue` 的 `<script setup>` 内顶层声明缩进为 0；`.ts` 同样为 0。
 */
function isTopLevelDeclaration(line: string): boolean {
  return /^( {0,2})(const\s+\w+\s*=\s*(async\s*)?\(|function\s+\w+\s*\(|(async\s+)?function\s+\w+\s*\()/.test(
    line
  )
}

/**
 * 从 `fromIndex` 向上扫描，返回最近的顶层声明所引入的函数名。
 *
 * <p>识别三种形态：`const NAME = (...) => {`、`const NAME = async (...) => {`、
 * `function NAME(...)`、`async function NAME(...)`。扫不到时返回 `'<top-level>'`，
 * 此时该匹配不属于任何允许清单条目 —— 正是想要的效果。
 */
function enclosingFunction(lines: SourceLine[], fromIndex: number): string {
  for (let i = fromIndex; i >= 0; i--) {
    const text = lines[i].text
    if (!isTopLevelDeclaration(text)) continue
    const m =
      text.match(/^\s*const\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?\(/) ||
      text.match(/^\s*(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/)
    if (m) return m[1]
  }
  return '<top-level>'
}

/**
 * 计算某个顶层函数在 `lines` 中的行区间 `[start, end]`（闭区间，索引）。
 *
 * <p>策略：找到声明行后，用花括号配平推进到函数体结束。声明行本身到配平结束之间
 * 即函数范围。找不到函数或花括号不配平时返回 `null`，让调用方把「解析失败」当成
 * 失败而不是静默通过。
 */
function functionRange(
  lines: SourceLine[],
  fn: string
): { start: number; end: number } | null {
  const declIndex = lines.findIndex(l => {
    if (!isTopLevelDeclaration(l.text)) return false
    const m =
      l.text.match(/^\s*const\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?\(/) ||
      l.text.match(/^\s*(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/)
    return m?.[1] === fn
  })
  if (declIndex === -1) return null

  // 声明可能跨多行（参数列表换行），从声明行开始累计花括号直到首次配平。
  let depth = 0
  let opened = false
  for (let i = declIndex; i < lines.length; i++) {
    for (const ch of lines[i].text) {
      if (ch === '{') {
        depth++
        opened = true
      } else if (ch === '}') {
        depth--
      }
    }
    if (opened && depth === 0) return { start: declIndex, end: i }
    // 声明是表达式而非块（例如 `const x = () => foo()`），没有花括号可配平。
    if (!opened && i > declIndex + 40) return { start: declIndex, end: i }
  }
  return null
}

/** 统计某个字面量（单引号或双引号）在给定行区间内出现的次数。 */
function literalOccurrences(
  lines: SourceLine[],
  range: { start: number; end: number },
  literal: string
): number {
  let count = 0
  for (let i = range.start; i <= range.end; i++) {
    const text = lines[i].text
    const single = `'${literal}'`
    const double = `"${literal}"`
    count += countOccurrences(text, single) + countOccurrences(text, double)
  }
  return count
}

function countOccurrences(haystack: string, needle: string): number {
  if (!needle) return 0
  let count = 0
  let idx = haystack.indexOf(needle)
  while (idx !== -1) {
    count++
    idx = haystack.indexOf(needle, idx + needle.length)
  }
  return count
}

interface ResidueHit {
  path: string
  fn: string
  literal: string
  lineNo: number
}

/**
 * 从一行里提取 `ElMessage.error(<literal>)` 的字符串字面量；取不到时返回 `''`。
 * 字面量只用于与允许清单比对，不参与「是否残留」的判定。
 */
function extractLiteral(text: string): string {
  const m = text.match(/ElMessage\s*\.\s*error\s*\(\s*(['"])(.*?)\1/)
  return m ? m[2] : ''
}

function collectResidue(): ResidueHit[] {
  const hits: ResidueHit[] = []
  for (const absPath of collectSources()) {
    const lines = codeLines(absPath)
    lines.forEach((line, idx) => {
      if (!BARE_ERROR_TOAST.test(line.text)) return
      hits.push({
        path: srcPath(absPath),
        fn: enclosingFunction(lines, idx),
        literal: extractLiteral(line.text),
        lineNo: line.lineNo
      })
    })
  }
  return hits
}

function isAllowlisted(hit: ResidueHit): boolean {
  return RESIDUE_ALLOWLIST.some(
    entry =>
      entry.path === hit.path && entry.fn === hit.fn && entry.literal === hit.literal
  )
}

describe('检查 ① — 裸 ElMessage.error 残留（87 → 4，允许清单按 path/fn/literal 定位）', () => {
  it('①a. 允许清单之外零残留', () => {
    const offenders = collectResidue().filter(hit => !isAllowlisted(hit))

    const rendered = offenders
      .map(h => `${h.path} → ${h.fn} → "${h.literal}" (第 ${h.lineNo} 行，仅供定位)`)
      .join('\n')

    expect(
      offenders,
      offenders.length === 0
        ? ''
        : `以下调用点不在允许清单内，必须迁移到 showError / 状态块 / 内联：\n${rendered}`
    ).toHaveLength(0)
  })

  it('①a2. 允许清单的四个三元组是本阶段已知的那四个（失败信息说明期望残留是 4，不是 0）', () => {
    // 把期望值写成可读清单，让失败信息直接告诉读者「4 是正确答案」。
    const expected = RESIDUE_ALLOWLIST.map(e => `${e.path} → ${e.fn} → "${e.literal}"`)
    expect(expected).toHaveLength(4)

    const residue = collectResidue()
    expect(
      residue.length,
      [
        '本阶段迁移后应存活 4 条裸 error toast，全部是客户端文件预检查：',
        ...expected.map(e => `  - ${e}`),
        `实际存活 ${residue.length} 条：`,
        ...residue.map(r => `  * ${r.path} → ${r.fn} → "${r.literal}"`)
      ].join('\n')
    ).toBe(4)
  })

  it('①b. 四个允许条目各自仍在其命名函数内、且字面量恰好出现一次（删掉预检查也必须失败）', () => {
    for (const entry of RESIDUE_ALLOWLIST) {
      const absPath = resolve(SRC_ROOT, entry.path)
      const lines = codeLines(absPath)
      const range = functionRange(lines, entry.fn)

      expect(
        range,
        `${entry.path} 里找不到顶层函数 ${entry.fn} —— 允许清单已失效，请核对函数是否被改名`
      ).not.toBeNull()

      const occurrences = literalOccurrences(lines, range!, entry.literal)
      expect(
        occurrences,
        [
          `${entry.path} → ${entry.fn} → "${entry.literal}"`,
          `期望恰好 1 次，实际 ${occurrences} 次。`,
          '0 次说明这条真实预检查被删了（「残留归零」不能靠删除达成）；',
          '>1 次说明同一个字面量被复制了一份。'
        ].join('\n')
      ).toBe(1)
    }
  })

  it('①c. 允许清单与断言里不含任何行号（定位只能靠 path/fn/literal）', () => {
    for (const entry of RESIDUE_ALLOWLIST) {
      expect(Object.keys(entry).sort()).toEqual(['fn', 'literal', 'path'])
      expect(entry.path).not.toMatch(/:\d+/)
      expect(entry.fn).not.toMatch(/:\d+/)
      expect(entry.literal).not.toMatch(/:\d+/)
    }
  })
})

// ---------------------------------------------------------------------------
// 检查 ② —— store 内不得弹错误 toast
// ---------------------------------------------------------------------------

const STORES_ROOT = resolve(SRC_ROOT, 'stores')

function storeSource(name: string): SourceLine[] {
  return codeLines(resolve(STORES_ROOT, name))
}

/** 统计文件内 `ElMessage.error(` 的次数。 */
function errorToastCalls(lines: SourceLine[]): number {
  return lines.reduce(
    (n, l) => n + (BARE_ERROR_TOAST.test(l.text) ? 1 : 0),
    0
  )
}

/**
 * 统计文件内**非 error** 的 ElMessage 调用次数。
 *
 * <p>`ElMessage(...)` 的对象式调用也算一条（`showError` 用它构造 VNode toast），
 * 但 store 里不该出现；这里只数 `ElMessage.success/info/warning` 三个具名通道。
 */
function nonErrorMessageCalls(lines: SourceLine[]): number {
  return lines.reduce((n, l) => {
    const matches = l.text.match(/ElMessage\s*\.\s*(success|info|warning)\s*\(/g)
    return n + (matches ? matches.length : 0)
  }, 0)
}

describe('检查 ② — store 内零错误 toast（D-02：store 只分类/上抛，不呈现）', () => {
  it('②a. src/stores 下所有 .ts 的 ElMessage.error 计数为 0', () => {
    const offenders: string[] = []
    for (const absPath of collectSources(STORES_ROOT)) {
      const lines = codeLines(absPath)
      const n = errorToastCalls(lines)
      if (n > 0) offenders.push(`${srcPath(absPath)} → ${n} 条 ElMessage.error`)
    }
    expect(
      offenders,
      offenders.length ? `store 内仍有错误 toast：\n${offenders.join('\n')}` : ''
    ).toHaveLength(0)
  })

  it('②b. ai.ts —— 恰好 1 条非错误提示（删除会话成功的 success）', () => {
    expect(nonErrorMessageCalls(storeSource('ai.ts'))).toBe(1)
  })

  it('②c. push.ts —— 恰好 1 条非错误提示（推送测试成功的 success）', () => {
    expect(nonErrorMessageCalls(storeSource('push.ts'))).toBe(1)
  })

  it('②d. wellness.ts —— 0 条（14-12 已删除 addMetric，其 success 随之消失）', () => {
    const lines = storeSource('wellness.ts')
    expect(nonErrorMessageCalls(lines)).toBe(0)
    // 连 ElMessage 的导入都应已移除，否则残留的导入就是下一次误用的入口。
    const importsElMessage = lines.some(
      l => l.text.includes("from 'element-plus'") && /\bElMessage\b/.test(l.text)
    )
    expect(importsElMessage).toBe(false)
  })

  it('②e. floatingAi.ts —— 0 条', () => {
    expect(nonErrorMessageCalls(storeSource('floatingAi.ts'))).toBe(0)
  })

  it('②f. auth.ts —— 恰好 4 条，且这是认证流程提示（本阶段范围之外，单独断言、不并入上面四个文件）', () => {
    // 401 的唯一可见提示由 authStore.logout() 承担（T-11-13）。它们不是错误 toast，
    // 也不在本阶段的迁移范围内 —— 单独命名断言，避免被「顺手清理 auth」蒙混通过。
    const lines = storeSource('auth.ts')
    expect(errorToastCalls(lines)).toBe(0)
    expect(nonErrorMessageCalls(lines)).toBe(4)
  })
})

// ---------------------------------------------------------------------------
// 检查 ③ —— 无手写 cancel 判断
// ---------------------------------------------------------------------------

/**
 * 三个被排除的文件，各自拥有一种职责。用字面路径 + 注释，**不允许通配**：
 * 通配排除会让下一个在这里写下第四条比较的人悄悄通过。
 */
const CANCEL_CHECK_EXCLUSIONS: ReadonlyArray<{ path: string; owns: string }> = [
  {
    path: 'utils/appError.ts',
    owns: '取消的识别本身（isCancel 的 CANCEL_SENTINELS / CanceledError / AbortError / ERR_CANCELED）'
  },
  {
    path: 'utils/request.ts',
    owns: '拦截器对已识别 kind 的短路（apiError.kind === \'cancel\' 时不重试、不弹提示）'
  },
  {
    path: 'utils/showError.ts',
    owns: 'toast 模块对已识别 kind 的短路（err.kind === \'cancel\' 时零呈现）'
  }
]

/**
 * 四条手写取消判断模式（修正版）。
 *
 * <p>注意**不**匹配 `=== 'cancel'`：`request.ts:65` 与 `showError.ts:139` 的两处
 * 等式比较是合法的（它们在消费已经识别好的 `kind`），而且这两个文件本来就在排除
 * 清单里 —— 但把它们写进模式会让排除清单成为唯一的安全网，一旦有人新增第四个文件
 * 的等式比较，检查就会以「误报」的形式变红，而不是以「真残留」的形式。
 */
const CANCEL_PATTERNS: ReadonlyArray<{ name: string; re: RegExp }> = [
  { name: "!== 'cancel'", re: /!==\s*['"]cancel['"]/ },
  { name: "!== 'close'", re: /!==\s*['"]close['"]/ },
  { name: "name === 'CanceledError'", re: /name\s*===\s*['"]CanceledError['"]/ },
  { name: "name === 'AbortError'", re: /name\s*===\s*['"]AbortError['"]/ }
]

describe('检查 ③ — 无手写 cancel 判断（ERR-08：取消不是失败）', () => {
  it('③a. 三个排除文件之外，四条取消模式全部归零', () => {
    const excluded = new Set(CANCEL_CHECK_EXCLUSIONS.map(e => e.path))
    const offenders: string[] = []

    for (const absPath of collectSources()) {
      const rel = srcPath(absPath)
      if (excluded.has(rel)) continue
      for (const line of codeLines(absPath)) {
        for (const pattern of CANCEL_PATTERNS) {
          if (pattern.re.test(line.text)) {
            offenders.push(`${rel} → ${pattern.name} (第 ${line.lineNo} 行)`)
          }
        }
      }
    }

    expect(
      offenders,
      offenders.length
        ? `以下手写取消判断必须改为 isCancel() / 归一化 kind：\n${offenders.join('\n')}`
        : ''
    ).toHaveLength(0)
  })

  it('③b. 排除清单恰好三个字面路径，每个都注明职责（无通配）', () => {
    expect(CANCEL_CHECK_EXCLUSIONS).toHaveLength(3)
    for (const entry of CANCEL_CHECK_EXCLUSIONS) {
      expect(entry.path).not.toMatch(/[*?]/)
      expect(entry.owns.trim().length).toBeGreaterThan(0)
    }
  })

  it("③c. 两处合法的 === 'cancel' 比较仍存在（它们是消费 kind，不是手写判断）", () => {
    const request = codeLines(resolve(SRC_ROOT, 'utils/request.ts')).map(l => l.text).join('\n')
    const showError = codeLines(resolve(SRC_ROOT, 'utils/showError.ts')).map(l => l.text).join('\n')
    expect(request).toMatch(/kind\s*===\s*['"]cancel['"]/)
    expect(showError).toMatch(/kind\s*===\s*['"]cancel['"]/)
  })
})

// ---------------------------------------------------------------------------
// 检查 ④ —— 无局部 form-item 错误样式
// ---------------------------------------------------------------------------

/**
 * 只匹配 **form-item 错误选择器**。
 *
 * <p>刻意**不**匹配 `--el-color-danger`：`CriticalConfirmDialog.vue` 是一个危险区确认
 * 对话框，它用 danger token 给非表单表面着色（危险操作按钮与说明文案），那是正确
 * 代码。把模式放宽到 danger token 会让这条检查为正确的代码变红。
 */
const LOCAL_FORM_ITEM_ERROR_SELECTORS: ReadonlyArray<{ name: string; re: RegExp }> = [
  { name: '.el-form-item__error', re: /\.el-form-item__error\b/ },
  {
    name: '.el-form-item.is-error',
    re: /\.el-form-item(?:__content)?\.is-error\b|\.el-form-item\.is-error\b/
  }
]

describe('检查 ④ — 无局部 form-item 错误样式（错误描边只在 theme.css 一处）', () => {
  it('④a. .vue 文件（排除 __tests__）里没有 form-item 错误选择器', () => {
    const offenders: string[] = []
    for (const absPath of collectSources()) {
      if (!absPath.endsWith('.vue')) continue
      const rel = srcPath(absPath)
      for (const line of codeLines(absPath)) {
        for (const sel of LOCAL_FORM_ITEM_ERROR_SELECTORS) {
          if (sel.re.test(line.text)) {
            offenders.push(`${rel} → ${sel.name} (第 ${line.lineNo} 行)`)
          }
        }
      }
    }
    expect(
      offenders,
      offenders.length
        ? `以下文件在局部覆盖了 form-item 错误样式，必须移回 theme.css：\n${offenders.join('\n')}`
        : ''
    ).toHaveLength(0)
  })

  it('④b. 全局样式表确实包含字段错误描边块（证明样式存在，而不只是「别处没有」）', () => {
    const theme = readFileSync(resolve(SRC_ROOT, 'assets/styles/theme.css'), 'utf8')
    // 三族控件：input/select 的 border-color、textarea 的 inset shadow、出错且聚焦的光晕。
    expect(theme).toMatch(/\.el-form-item\.is-error[\s\S]{0,200}border-color:\s*var\(--hhs-color-error-text\)/)
    expect(theme).toMatch(/\.el-form-item\.is-error[\s\S]{0,300}box-shadow:[^;]*inset/)
    expect(theme).toMatch(/--hhs-color-error-tint/)
  })

  it('④c. 危险区确认对话框仍合法使用 danger token（这是 ④ 不匹配该 token 的原因）', () => {
    const dialog = readFileSync(
      resolve(SRC_ROOT, 'views/data-input/components/CriticalConfirmDialog.vue'),
      'utf8'
    )
    expect(dialog).toContain('--el-color-danger')
    // 它不碰 form-item 错误选择器 —— 两个模式互不重叠，所以它既合法又不被误判。
    expect(dialog).not.toMatch(/\.el-form-item__error|\.el-form-item\.is-error/)
  })
})

// ---------------------------------------------------------------------------
// 台账对账 —— 87 行且每行七列完整
// ---------------------------------------------------------------------------

// Phase 14 was archived into `.planning/milestones/v1.3-phases/` when v1.3 shipped.
// Resolve against the live layout first, then the archived layout, so the ledger
// reconciliation keeps working across milestone archive moves.
const LEDGER_DIR = [
  '../.planning/phases/14-error-rollout',
  '../.planning/milestones/v1.3-phases/14-error-rollout'
]
  .map(dir => resolve(process.cwd(), dir))
  .find(dir => existsSync(resolve(dir, '14-MIGRATION-LEDGER.md')))

if (!LEDGER_DIR) {
  throw new Error(
    '找不到 Phase 14 台账目录：既不在 .planning/phases/14-error-rollout，也不在 .planning/milestones/v1.3-phases/14-error-rollout'
  )
}

const LEDGER_PATH = resolve(LEDGER_DIR, '14-MIGRATION-LEDGER.md')
const PRE_MIGRATION_COUNT = 87
const LEDGER_FRAGMENT_PLANS = ['04', '05', '06', '07', '08', '09', '10', '11', '12'] as const

/**
 * 把一行 markdown 表格拆成单元格，把反引号跨度内的 `|` 当作普通字符。
 * 台账的「理由」与「spec 断言」两列大量含 `|`（`error.message || fallback`），
 * 按裸 `|` 切会把一行拆成十几格。
 */
function splitTableRow(line: string): string[] {
  const cells: string[] = []
  let cur = ''
  let inCode = false
  for (let i = 0; i < line.length; i++) {
    const ch = line[i]
    if (ch === '\\' && line[i + 1] === '|') {
      cur += '|'
      i++
      continue
    }
    if (ch === '`') {
      inCode = !inCode
      cur += ch
      continue
    }
    if (ch === '|' && !inCode) {
      cells.push(cur)
      cur = ''
      continue
    }
    cur += ch
  }
  cells.push(cur)
  if (cells.length && cells[0].trim() === '') cells.shift()
  if (cells.length && cells[cells.length - 1].trim() === '') cells.pop()
  return cells.map(c => c.trim())
}

/** 台账里的迁移数据行：首格形如 `` `path:line` ``。 */
const LEDGER_DATA_ROW = /^`[^`]+:\d+`/

function ledgerDataRows(): string[] {
  return readFileSync(LEDGER_PATH, 'utf8')
    .split(/\r?\n/)
    .filter(l => /^\|/.test(l))
    .filter(l => LEDGER_DATA_ROW.test(splitTableRow(l)[0] ?? ''))
}

describe('台账对账 — 87 行、每行七列非空', () => {
  it('① 迁移台账的数据行数等于迁移前记录的 87', () => {
    const rows = ledgerDataRows()
    expect(
      rows.length,
      `台账数据行 ${rows.length}，与迁移前基线 ${PRE_MIGRATION_COUNT} 不符。` +
        '少了说明某个片段的数据行没被合并；多了说明有调用点被按分支拆成了两行（Contract 6.1 禁止）。'
    ).toBe(PRE_MIGRATION_COUNT)
  })

  it('② 每一行七列都非空（完整性是机械断言，不是判断）', () => {
    const incomplete: string[] = []
    for (const line of ledgerDataRows()) {
      const cells = splitTableRow(line)
      if (cells.length !== 7) {
        incomplete.push(`${cells[0] ?? line.slice(0, 40)} → ${cells.length} 列`)
      } else if (cells.some(c => c === '')) {
        incomplete.push(`${cells[0]} → 存在空列`)
      }
    }
    expect(
      incomplete,
      incomplete.length ? `以下台账行不完整：\n${incomplete.join('\n')}` : ''
    ).toHaveLength(0)
  })

  it('③ 九份片段全部存在且被合并（缺一份要大声失败，不能只合并在场的那几份）', () => {
    for (const plan of LEDGER_FRAGMENT_PLANS) {
      const fragment = resolve(LEDGER_DIR, `14-LEDGER-${plan}.md`)
      expect(() => readFileSync(fragment, 'utf8'), `缺少片段 14-LEDGER-${plan}.md`).not.toThrow()
    }
    expect(LEDGER_FRAGMENT_PLANS).toHaveLength(9)
  })

  it('④ 14-14 的内联覆盖审计独立成节，其行不并入 87 行计数', () => {
    const text = readFileSync(LEDGER_PATH, 'utf8')
    expect(text).toContain('内联覆盖审计')
    // 该节的行首格是表单路径而非 `path:line`，因此天然不进入 87 行。
    const auditRows = text
      .split(/\r?\n/)
      .filter(l => /^\|/.test(l))
      .filter(l => !LEDGER_DATA_ROW.test(splitTableRow(l)[0] ?? ''))
    expect(auditRows.length).toBeGreaterThan(0)
  })
})
