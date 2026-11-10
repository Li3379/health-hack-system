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
// 刻意不做的事：本文件**没有**任何 `getComputedStyle` 断言。happy-dom 对
// `border-color` / `box-shadow` 的解析会返回空串，写这种断言只会得到一个
// 「永远通过但什么都没验证」的假绿灯 —— 那比显式交给 UAT 更糟。
// 计算样式的四条自检（描边色解析值、textarea shadow 含 inset、halo 存在且
// 描边仍为错误色、失焦后 halo 消失）在 14-14 的人工 UAT 中于真实浏览器内完成。
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
  it('三个聚焦标记都在新增块里出现', () => {
    const block = readErrorBlock()

    expect(block).toContain('.is-focus')
    expect(block).toContain('.is-focused')
    expect(block).toContain('.is-active')
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
})