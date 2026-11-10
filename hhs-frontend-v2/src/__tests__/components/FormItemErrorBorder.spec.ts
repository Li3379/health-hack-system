import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { h, ref } from 'vue'
import { ElForm, ElFormItem, ElInput } from 'element-plus'

// ---------------------------------------------------------------------------
// 这个 spec 覆盖两件事，两件事都**不需要真实浏览器**：
//
//   半 A（锚点）：`el-form-item` 的 `:error` prop 是否真的产出 `.el-form-item.is-error`
//                 类。不用 stub —— 断言的全部价值就在于「真实组件设置了该类」。
//   半 B（样式表形状）：theme.css 里新增的字段错误描边块是否按契约写了三组选择器
//                 （input/select 的 border-color、textarea 的 inset shadow、
//                 三个聚焦标记），并且每条 wrapper 选择器都用 `body` 前缀抬高特异性。
//
// ===== 交给 UAT 的四条浏览器检查（本文件刻意不测） =====
// UI-SPEC 4.2「执行者的四条自检」需要在真实浏览器里读计算样式，happy-dom 给不出
// 可信结果（`getComputedStyle` 对 border-color / box-shadow 返回空串）。因此以下
// 四项在 14-14 的人工 UAT 中逐条完成，本 spec 只覆盖「类锚点 + 选择器清单」：
//
//   1. 出错字段的 `.el-input__wrapper` 计算 border-color 解析为 rgb(185,28,28)（亮色）
//      / rgb(245,108,108)（暗色）。
//   2. 出错字段的 `.el-textarea__inner` 计算 box-shadow 含 `inset` 且颜色为同一 token。
//   3. 出错字段聚焦后 box-shadow 含 3px tint 光晕、border-color 仍为错误色 ——
//      三类控件各验一次：el-input（.is-focus）、el-select（.is-focused）、
//      el-date-picker type="daterange"（.is-active；这一类是唯一会在规则 ①
//      的 `box-shadow: none !important` 下丢失焦点指示的控件）。
//   4. 失焦后 box-shadow 回到 none（光晕消失），border-color 仍是错误色。
//
// 本文件**没有**任何 `getComputedStyle` 断言：写一个在 happy-dom 里永远通过的空断言，
// 比显式把这项交出去更糟。
// ---------------------------------------------------------------------------

const THEME_CSS_PATH = resolve(process.cwd(), 'src/assets/styles/theme.css')
const BLOCK_MARKER = 'Phase 14: 字段级错误描边'

/**
 * 取出 Phase 14 新增块的源码，并剥掉注释行。
 * 先剥注释再断言：块头的中文说明里也写着 `is-focus` / `is-active` 等词，
 * 不剥注释的话，注释本身就能满足断言，测试会变成同义反复。
 */
function readErrorBlock(): string {
  const source = readFileSync(THEME_CSS_PATH, 'utf8')
  const marker = source.indexOf(BLOCK_MARKER)
  expect(marker, `theme.css 中找不到「${BLOCK_MARKER}」区块标记`).toBeGreaterThanOrEqual(0)

  // 区块标记在块头注释内部：先走到该注释结束，再向后取到下一个区块标题之前。
  const headerEnd = source.indexOf('*/', marker)
  const afterHeader = source.slice(headerEnd + 2)
  const nextBlock = afterHeader.indexOf('/* ====')

  return (nextBlock >= 0 ? afterHeader.slice(0, nextBlock) : afterHeader).replace(
    /\/\*[\s\S]*?\*\//g,
    ''
  )
}

function readThemeCss(): string {
  return readFileSync(THEME_CSS_PATH, 'utf8')
}

interface CssRule {
  selectors: string[]
  body: string
}

/** 把剥过注释的块拆成 { selectors, body } 列表，供选择器级断言使用。 */
function rulesIn(block: string): CssRule[] {
  const rules: CssRule[] = []
  const pattern = /([^{}]+)\{([^{}]*)\}/g
  let match: RegExpExecArray | null

  while ((match = pattern.exec(block)) !== null) {
    rules.push({
      selectors: match[1]
        .split(',')
        .map(selector => selector.trim())
        .filter(Boolean),
      body: match[2].trim()
    })
  }

  return rules
}

/**
 * 三个控件族 → 各自的聚焦标记。
 * 用表驱动而不是三条独立的 expect：将来若出现第四个控件族，漏写标记时
 * 失败信息会直接点名缺哪一族，而不是让读者自己比对两处清单。
 *
 * daterange 一族的 wrapper 是 `.el-input__wrapper`（范围选择器的根节点自身就是它，
 * 见 picker.vue 的 rangeInputKls），但它不经过 ElInput，所以标记是 `.is-active`，
 * 永远拿不到 `.is-focus`。
 */
const FOCUS_FAMILIES: Array<{ family: string; wrapper: string; marker: string }> = [
  { family: 'el-input', wrapper: '.el-input__wrapper', marker: '.is-focus' },
  { family: 'el-select', wrapper: '.el-select__wrapper', marker: '.is-focused' },
  {
    family: 'el-date-picker type="daterange"',
    wrapper: '.el-input__wrapper',
    marker: '.is-active'
  }
]

describe('el-form-item 错误锚点（半 A：真实 element-plus）', () => {
  // `.is-error` 类来自 validateState（immediate），但错误消息节点由
  // `refDebounced(validateState, 100)` 驱动（form-item.vue:31/114）。
  // 因此断言消息文本前要越过这个去抖窗口；类断言本身不需要等待。
  const flushErrorDebounce = () => new Promise(resolve => setTimeout(resolve, 150))

  it('`:error` 为非空串时渲染 .el-form-item.is-error 并显示消息；清空后类消失', async () => {
    const errorMessage = ref('该项必填')

    // 用 h() 写渲染函数，而不是 template 字符串：测试套件的 Vue 运行时构建
    // 不支持模板编译，template 写法会静默降级、把这行为断言变成 stub 形状的空壳。
    const Host = {
      setup() {
        return () =>
          h(ElForm, { model: { value: '1' } }, () =>
            h(ElFormItem, { prop: 'value', error: errorMessage.value }, () =>
              h(ElInput, { modelValue: '1' })
            )
          )
      }
    }

    const wrapper = mount(Host, { attachTo: document.body })

    const formItem = wrapper.find('.el-form-item')
    expect(formItem.exists()).toBe(true)
    expect(formItem.classes()).toContain('is-error')

    await flushErrorDebounce()
    expect(wrapper.find('.el-form-item__error').text()).toContain('该项必填')

    // clearField 依赖的行为：清空 error 后描边必须一并消失，
    // 否则「已修正」的字段会一直红着。
    errorMessage.value = ''
    await flushErrorDebounce()

    expect(wrapper.find('.el-form-item').classes()).not.toContain('is-error')
    expect(wrapper.find('.el-form-item__error').exists()).toBe(false)

    wrapper.unmount()
  })
})

describe('theme.css 字段错误描边块（半 B：源码形状）', () => {
  it('三个控件族各自都有匹配的聚焦标记选择器', () => {
    const rules = rulesIn(readErrorBlock())

    for (const { family, wrapper, marker } of FOCUS_FAMILIES) {
      const matched = rules.some(rule =>
        rule.selectors.some(
          selector => selector.includes(wrapper) && selector.includes(marker)
        )
      )

      expect(
        matched,
        `控件族「${family}」缺少带 ${marker} 的 ${wrapper} 选择器 —— 出错且聚焦时该控件将没有焦点指示`
      ).toBe(true)
    }
  })

  it('规则 ③ 在同一条规则里同时声明错误描边色和 tint 光晕', () => {
    const rules = rulesIn(readErrorBlock())
    const focusMarkers = FOCUS_FAMILIES.map(entry => entry.marker)

    // UI-SPEC 的 WCAG 2.4.7 论证依赖两者同时存在：只声明光晕时，
    // border-color 会回落到非错误样式，聚焦中的出错字段看起来反而像健康字段。
    const pairedRule = rules.find(
      rule =>
        rule.selectors.some(selector => focusMarkers.some(marker => selector.includes(marker))) &&
        rule.body.includes('border-color: var(--hhs-color-error-text) !important') &&
        rule.body.includes('box-shadow: 0 0 0 3px var(--hhs-color-error-tint) !important')
    )

    expect(
      pairedRule,
      '焦点规则必须同时声明 border-color 错误色与 3px tint 光晕，否则聚焦态会掩盖错误态'
    ).toBeDefined()
  })

  it('textarea 两条规则都在（inset 描边 + 聚焦组合）', () => {
    const block = readErrorBlock()
    const matches = block.match(/el-textarea__inner/g) ?? []

    expect(matches.length).toBeGreaterThanOrEqual(2)
    expect(block).toMatch(/\.el-textarea__inner\s*\{[^}]*inset/)
  })

  it('新增块里每条 wrapper 选择器都以 body 前缀开头', () => {
    const block = readErrorBlock()
    const selectorGroups = block.match(/[^{}]+\{/g) ?? []

    const wrapperSelectors = selectorGroups
      .map(group => group.replace(/\{\s*$/, '').trim())
      .flatMap(group => group.split(','))
      .map(selector => selector.trim())
      .filter(selector => selector.includes('__wrapper'))

    expect(wrapperSelectors.length).toBeGreaterThan(0)
    for (const selector of wrapperSelectors) {
      expect(selector, `wrapper 选择器缺少 body 前缀，会输给 EP 后注入的同特异性规则：${selector}`).toMatch(
        /^body\s/
      )
    }
  })

  it('新增块只用项目错误 token，不引用 EP 的 danger 变量', () => {
    const block = readErrorBlock()

    expect(block).toContain('--hhs-color-error-text')
    expect(block).not.toContain('--el-color-danger')
  })

  it('整份 theme.css 都不引用 --el-color-danger（错误色必须是项目 token）', () => {
    // EP 的 #f56c6c 在纯白上只有 2.90:1，低于非文字对比度 3:1 的底线。
    expect(readThemeCss()).not.toContain('el-color-danger')
  })
})