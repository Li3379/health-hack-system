import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import type { Mock } from 'vitest'

/**
 * ERR-03 / ERR-05 / ERR-08 的端到端锚点（`views/screening/List.vue`）。
 *
 * 三个用例各自证明一条**无法用 grep 证明**的性质：
 *  1. 上传被 400 拒 → 汇总 + 内联 + 零 toast，弹窗仍然打开、已填的报告名称一字未变。
 *  2. 删除确认被取消 → 零提示**且 DELETE 一次都没发**；确认后失败 → 恰好一条提示。
 *     这两半合起来才能证明「确认与请求分离」是真的，而不是把旧的手写判断改了名字。
 *  3. 删除返回 `RESOURCE_NOT_FOUND` 信封 → 恰好一条 toast，文案是后端消息。
 *     缺 `presentStates: true` 时这里是 0（B1 回归护栏，与 14-05/14-10/14-11 同形）。
 *
 * element-plus 用 importOriginal 部分 mock（同 GoalFormInlineError.spec.ts:43-60）。
 */

const { listReportsMock, uploadReportMock, deleteReportMock } = vi.hoisted(() => ({
  listReportsMock: vi.fn(),
  uploadReportMock: vi.fn(),
  deleteReportMock: vi.fn()
}))

vi.mock('@/api/screening', () => ({
  screeningApi: {
    listReports: listReportsMock,
    uploadReport: uploadReportMock,
    deleteReport: deleteReportMock,
    getReport: vi.fn(),
    getReportDetail: vi.fn(),
    triggerOcr: vi.fn(),
    getOcrStatus: vi.fn(),
    getLabResults: vi.fn()
  }
}))

// onMounted 用 staggerReveal，onUnmounted 用 cleanupScrollTriggers。
vi.mock('@/composables/useGsap', () => ({
  staggerReveal: vi.fn(),
  cleanupScrollTriggers: vi.fn(),
  DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
  EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
}))

vi.mock('element-plus', async importOriginal => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(() => ({ close: vi.fn() })), {
      error: vi.fn(),
      success: vi.fn(),
      warning: vi.fn(),
      info: vi.fn(),
      closeAll: vi.fn()
    }),
    ElMessageBox: Object.assign(vi.fn(), {
      confirm: vi.fn(),
      alert: vi.fn(),
      prompt: vi.fn()
    })
  }
})

import ScreeningList from '@/views/screening/List.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy
const confirmMock = ElMessageBox.confirm as unknown as Mock

/**
 * showError 的 VNode 分支有模块级 2000ms 去重闩锁。同文案的用例会互相吞掉对方的
 * toast，让断言读成 0 —— 那是闩锁生效，不是呈现缺失。让每次 Date.now() 前进 60s。
 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

/** 四通道统计：对象式调用 + 函数式旧 API（ElMessage.error / success …）。 */
function toastCount(): number {
  return (
    ElMessageMock.mock.calls.length +
    ElMessageMock.error.mock.calls.length +
    ElMessageMock.success.mock.calls.length +
    ElMessageMock.warning.mock.calls.length +
    ElMessageMock.info.mock.calls.length
  )
}

function errorToastCount(): number {
  const objectCalls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  ).length
  return objectCalls + ElMessageMock.error.mock.calls.length
}

interface VNodeLike {
  type?: unknown
  props?: Record<string, unknown>
  children?: unknown
}

/** 深度遍历 VNode，返回第一个带指定 class 片段的节点（同 ErrorLayerSample.spec.ts:110）。 */
function findNodeByClass(node: unknown, className: string): VNodeLike | undefined {
  if (!node || typeof node === 'string' || !Array.isArray((node as VNodeLike).children)) {
    return undefined
  }
  const target = node as VNodeLike
  for (const child of target.children as unknown[]) {
    if (!child || typeof child !== 'object') continue
    const candidate = child as VNodeLike
    const classes = String(candidate.props?.class ?? '')
    if (classes.includes(className)) return candidate
    const nested = findNodeByClass(candidate, className)
    if (nested) return nested
  }
  return undefined
}

/** 客户端校验的 stub 出口：默认 valid=true（本 spec 测服务端分支）。 */
const globalStubs = {
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>',
    props: ['shadow', 'class']
  },
  'el-row': { template: '<div class="el-row"><slot /></div>', props: ['gutter'] },
  'el-col': {
    template: '<div class="el-col"><slot /></div>',
    props: ['span', 'xs', 'sm', 'md', 'lg']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-empty': { template: '<div class="el-empty">{{ description }}</div>', props: ['description'] },
  'el-pagination': {
    template: '<div class="el-pagination" />',
    props: ['currentPage', 'pageSize', 'total', 'pageSizes', 'layout']
  },
  // el-dialog 只在 modelValue 为真时渲染内容 —— formRef 依赖它在失败后仍然存在。
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width']
  },
  // 真实 EP 的 validate 回调签名是 (valid, invalidFields)；stub 必须同形。
  'el-form': {
    template: '<form class="el-form"><slot /></form>',
    methods: {
      validate: (cb: (valid: boolean, invalidFields?: unknown) => void) =>
        Promise.resolve(cb(true, undefined)),
      validateField: vi.fn(),
      clearValidate: vi.fn()
    }
  },
  // 内联错误必须真的渲染到 DOM（含 is-error 类），才能证明 :error 绑对了 prop。
  'el-form-item': {
    template:
      '<div class="el-form-item" :class="{ \'is-error\': !!error }"><label>{{ label }}</label><slot /><span v-if="error" class="el-form-item__error">{{ error }}</span></div>',
    props: ['label', 'prop', 'error']
  },
  'el-input': {
    template: '<input class="el-input" :value="modelValue" />',
    props: ['modelValue', 'placeholder']
  },
  'el-date-picker': {
    template: '<input class="el-date-picker" :value="modelValue" />',
    props: ['modelValue', 'type', 'placeholder', 'valueFormat', 'style']
  },
  'el-upload': {
    // 不声明 `ref` 为 prop —— 它是 Vue 的保留属性，声明会触发
    // `Invalid prop name: "ref" is a reserved property` 警告。
    template: '<div class="el-upload"><slot /><slot name="tip" /></div>',
    props: ['autoUpload', 'limit', 'onChange', 'onRemove', 'accept']
  },
  'el-button': {
    template: '<button class="el-button"><slot /></button>',
    props: ['type', 'loading', 'link', 'size']
  },
  'el-icon': { template: '<span class="el-icon"><slot /></span>', props: ['size', 'color'] },
  // FormErrorSummary 内部用 el-alert 承载标题。
  'el-alert': {
    template: '<div class="el-alert"><slot name="title" /></div>',
    props: ['type', 'closable', 'showIcon']
  }
}

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/screening/detail/:id', component: { template: '<div />' } }
    ]
  })
  // 初始导航不是本 spec 的断言对象；先解析到 / 以消掉 "No match found for location with path ''"。
  return router.push('/').catch(() => undefined).then(() => router)
}

async function mountList() {
  // attachTo 是必需的：focusSummary() 只在真实挂载到 document 的元素上才改变
  // document.activeElement。
  return mount(ScreeningList, {
    attachTo: document.body,
    global: {
      plugins: [await makeRouter()],
      stubs: globalStubs,
      directives: { loading: () => {} }
    }
  })
}

/** 挂载 + 等首次列表加载落地（三个用例都要先脱离加载态）。 */
async function mountAndLoad() {
  const wrapper = await mountList()
  await flushPromises()
  return wrapper
}

/** 真实形态：`reportName` 的 @NotBlank 被违反 → HTTP 400 + fieldErrors。 */
function uploadValidationRejection() {
  return {
    response: {
      status: 400,
      data: {
        code: 400,
        message: '参数校验失败',
        errorCode: 'validation.invalid_parameter',
        fieldErrors: [{ field: 'reportName', message: '报告名称不能为空' }]
      }
    }
  }
}

describe('screening/List.vue — 上传对话框内联 + 删除的确认/请求分离（ERR-03 / ERR-05 / ERR-08）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listReportsMock.mockReset()
    uploadReportMock.mockReset()
    deleteReportMock.mockReset()
    confirmMock.mockReset()

    listReportsMock.mockResolvedValue({ code: 200, message: 'success', data: { records: [], total: 0 } })
    confirmMock.mockResolvedValue('confirm')
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 必须零调用（错误收敛到 showError 单一出口）。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('上传被 400 拒：汇总 + 内联，零 toast，弹窗仍开，报告名称保留', async () => {
    const wrapper = await mountAndLoad()

    const vm = wrapper.vm as any
    vm.showUploadDialog()
    await flushPromises()

    vm.form.reportName = '2026 年度体检'
    vm.form.institution = '第一人民医院'
    vm.form.reportDate = '2026-03-01'
    const file = new File(['x'], 'report.pdf', { type: 'application/pdf' })
    vm.handleFileChange({ raw: file })
    await flushPromises()

    // 提交前快照 —— 失败后必须逐字相等。
    const before = { reportName: vm.form.reportName, institution: vm.form.institution, reportDate: vm.form.reportDate }

    uploadReportMock.mockRejectedValueOnce(uploadValidationRejection())

    await vm.handleUpload()
    await flushPromises()

    // 1. 零 toast —— validation 由汇总 + 内联承担（D-01）。
    expect(toastCount()).toBe(0)

    // 2. 汇总块渲染，标签来自全局 FIELD_LABELS 的 reportName →「报告名称」。
    const summary = wrapper.find('.form-error-summary')
    expect(summary.exists()).toBe(true)
    expect(summary.text()).toContain('请修正以下 1 项填写问题')
    expect(summary.text()).toContain('报告名称：报告名称不能为空')
    expect(summary.findAll('li')).toHaveLength(1)

    // 3. 恰好一个 el-form-item 带 is-error，落在 reportName 上。
    expect(wrapper.findAll('.el-form-item.is-error')).toHaveLength(1)
    expect(wrapper.find('[data-field="reportName"]').classes()).toContain('is-error')
    expect(wrapper.find('[data-field="reportName"] .el-form-item__error').text()).toBe(
      '报告名称不能为空'
    )

    // 4. 弹窗仍然打开（关闭只发生在解析成功之后）。
    expect(vm.uploadVisible).toBe(true)

    // 5. ERR-05：已填值一字未变。
    expect({
      reportName: vm.form.reportName,
      institution: vm.form.institution,
      reportDate: vm.form.reportDate
    }).toEqual(before)

    // 6. 焦点只去汇总块一次（Contract 4.4）。
    expect(document.activeElement).toBe(summary.element as HTMLElement)

    wrapper.unmount()
  })

  it('删除：取消时零提示且不发 DELETE；确认后失败恰好一条提示', async () => {
    const wrapper = await mountAndLoad()

    const vm = wrapper.vm as any

    // ── 前半：取消必须既不提示、也不发请求 ──
    // 用 AbortError 而不是字符串 'cancel'：旧的手写判断对字符串恰好能用，
    // 因此字符串形态**证明不了**任何事 —— 它对 AbortError 判成真失败并弹红灯。
    confirmMock.mockRejectedValueOnce(Object.assign(new Error('canceled'), { name: 'AbortError' }))

    await vm.handleDelete(1)
    await flushPromises()

    expect(toastCount()).toBe(0)
    expect(deleteReportMock).not.toHaveBeenCalled()

    // ── 后半：确认之后失败仍要恰好一条提示 ──
    confirmMock.mockResolvedValueOnce('confirm')
    deleteReportMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })

    await vm.handleDelete(1)
    await flushPromises()

    expect(deleteReportMock).toHaveBeenCalledTimes(1)
    expect(errorToastCount()).toBe(1)

    wrapper.unmount()
  })

  it('删除已被删除的报告（RESOURCE_NOT_FOUND 信封）：恰好一条提示，文案是后端消息（B1 回归）', async () => {
    // 真实形态：BusinessException(RESOURCE_NOT_FOUND) → HTTP 200 + 信封 code 500 + errorCode
    //（`ScreeningServiceImpl:197` 对缺失与外键不匹配的报告都抛它）。
    // 缺 presentStates 时 showError 在 resolveMessage 之前就返回 → 0 条，这正是 B1 的缺陷。
    deleteReportMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'resource.not_found', message: '报告不存在' }
      }
    })

    const wrapper = await mountAndLoad()

    const vm = wrapper.vm as any
    await vm.handleDelete(1)
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    // 断言文案而不只是条数：证明 presentStates 真的走到了 resolveMessage。
    const options = ElMessageMock.mock.calls[0][0] as { message: unknown }
    const textNode = findNodeByClass(options.message, 'error-toast__text')
    expect(textNode?.children).toBe('报告不存在')

    wrapper.unmount()
  })
})
