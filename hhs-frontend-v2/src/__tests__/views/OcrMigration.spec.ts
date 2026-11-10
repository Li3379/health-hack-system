import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import type { Mock } from 'vitest'

/**
 * Phase 14 / plan 14-11 的端到端锚点（`OcrInput.vue` + `OcrHistory.vue`）。
 *
 * 四条不变量：
 *
 * 1. **两条客户端前置校验必须保留。** `handleFileChange` 里的 10MB / 图片类型检查在
 *    任何请求存在之前运行，它们不是 API 失败 —— 硬塞进 `showError` 需要伪造一个
 *    `ApiError` 去满足「分类服务端响应」的签名。它们是被台账记为 `保持` 的两行，
 *    也是本阶段 4 行残留许可清单里的 2 行（plan 14-13 的检查 ① 按三元组钉住它们）。
 *    下面每条都用「恰好一条提示 **且** 零请求」双断言：后半句才能把「真的短路了」
 *    和「发了请求但被吞掉」区分开。
 * 2. **识别失败的面板必须活下来。** 旧的 catch 里 `recognizeResult.status = 'failed'`
 *    是内容区的失败态，toast 是通道提示 —— 两个机制服务不同需求。迁移只许换文案
 *    路径，不许删面板赋值。
 * 3. **B1 不得回归（最要紧）。** 三个文件都不渲染 `ErrorState`；`OcrServiceImpl:691`
 *    （详情）与 `:727`（删除）抛 `RESOURCE_NOT_FOUND`，对外人记录抛 `AUTH_FORBIDDEN`。
 *    若 `showError` 少传 `presentStates: true`，「查看/删除一条已被删掉的记录」
 *    会变成零提示的静默。
 * 4. **不可达分支已删除。** 拦截器在信封 `code !== 200` 时直接 reject，
 *    因此 `if (res.code === 200) { … } else { … }` 的 else 永远不到。
 */

vi.mock('@/api/ocr', () => ({
  ocrApi: {
    getHistory: vi.fn(),
    getRecordDetail: vi.fn(),
    deleteRecord: vi.fn()
  }
}))

// OcrInput 直接用 request，不走 api 层。
vi.mock('@/utils/request', () => ({
  request: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    upload: vi.fn()
  },
  resetAuthLatch: vi.fn()
}))

vi.mock('@/composables/useGsap', () => {
  const timeline = { fromTo: () => timeline }
  return {
    gsap: { timeline: () => timeline },
    scrollReveal: vi.fn(),
    cleanupScrollTriggers: vi.fn(),
    DUR: { mid: 0.5, slow: 0.8, dramatic: 1.2 },
    EASE: { out: 'power2.out', back: 'back.out', expo: 'expo.out' }
  }
})

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
    })
  }
})

import OcrInput from '@/views/data-input/components/OcrInput.vue'
import OcrHistory from '@/views/data-input/components/OcrHistory.vue'
import { ocrApi } from '@/api/ocr'
import { request } from '@/utils/request'
import { ElMessage } from 'element-plus'

const getHistoryMock = ocrApi.getHistory as unknown as Mock
const getRecordDetailMock = ocrApi.getRecordDetail as unknown as Mock
const deleteRecordMock = ocrApi.deleteRecord as unknown as Mock
const requestUploadMock = request.upload as unknown as Mock
const requestPostMock = request.post as unknown as Mock

type MessageSpy = Mock & Record<'error' | 'success' | 'warning' | 'info', Mock>
const ElMessageMock = ElMessage as unknown as MessageSpy

/** showError 的 VNode 闩锁是模块级、跨用例存活的。让 Date.now 单调前进 60s。 */
let nowCursor = 1_700_000_000_000
vi.spyOn(Date, 'now').mockImplementation(() => (nowCursor += 60_000))

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

function lastErrorToastText(): string {
  const calls = ElMessageMock.mock.calls.filter(
    ([options]) => (options as { type?: string } | undefined)?.type === 'error'
  )
  expect(calls.length).toBeGreaterThan(0)
  const message = (calls[calls.length - 1][0] as { message: unknown }).message
  if (typeof message === 'string') return message
  const textNode = findNodeByClass(message, 'error-toast__text')
  return String(textNode?.children ?? '')
}

const ocrInputStubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-radio-group': { template: '<div class="el-radio-group"><slot /></div>', props: ['modelValue'] },
  'el-radio-button': {
    template: '<button class="el-radio-button" type="button"><slot /></button>',
    props: ['value']
  },
  'el-upload': { template: '<div class="el-upload"><slot /></div>', props: ['autoUpload', 'showFileList', 'accept'] },
  'el-button': {
    template: '<button class="el-button" type="button"><slot /></button>',
    props: ['type', 'size', 'circle', 'disabled', 'loading', 'text']
  },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-alert': { template: '<div class="el-alert" />', props: ['type', 'title', 'closable', 'showIcon'] },
  'el-table': { template: '<div class="el-table"><slot /></div>', props: ['data', 'stripe', 'size'] },
  'el-table-column': { template: '<div class="el-table-column" />', props: ['prop', 'label', 'width'] },
  'el-checkbox': { template: '<input type="checkbox" class="el-checkbox" />', props: ['modelValue'] },
  'el-text': { template: '<span class="el-text"><slot /></span>', props: ['type'] },
  'el-collapse': { template: '<div class="el-collapse"><slot /></div>' },
  'el-collapse-item': { template: '<div class="el-collapse-item"><slot /></div>', props: ['title', 'name'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  Camera: { template: '<span class="icon-camera" />' },
  Upload: { template: '<span class="icon-upload" />' },
  Close: { template: '<span class="icon-close" />' }
}

const ocrHistoryStubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>' },
  'el-table': { template: '<div class="el-table"><slot /></div>', props: ['data', 'stripe', 'emptyText'] },
  'el-table-column': {
    template: '<div class="el-table-column" />',
    props: ['prop', 'label', 'width', 'minWidth', 'fixed']
  },
  'el-tooltip': { template: '<span class="el-tooltip"><slot /></span>', props: ['content', 'placement', 'showAfter'] },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-button': {
    template: '<button class="el-button" type="button"><slot /></button>',
    props: ['type', 'size', 'text']
  },
  'el-popconfirm': {
    template: '<span class="el-popconfirm"><slot /><slot name="reference" /></span>',
    props: ['title', 'confirmButtonText', 'cancelButtonText']
  },
  'el-pagination': {
    template: '<div class="el-pagination" />',
    props: ['currentPage', 'pageSize', 'total', 'layout', 'small']
  },
  'el-dialog': {
    template: '<div class="el-dialog" v-if="modelValue"><slot /><slot name="footer" /></div>',
    props: ['modelValue', 'title', 'width', 'destroyOnClose']
  },
  'el-alert': { template: '<div class="el-alert"><slot /></div>', props: ['type', 'closable'] },
  'el-collapse': { template: '<div class="el-collapse"><slot /></div>' },
  'el-collapse-item': { template: '<div class="el-collapse-item"><slot /></div>', props: ['title', 'name'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  Clock: { template: '<span class="icon-clock" />' },
  Loading: { template: '<span class="icon-loading" />' }
}

type OcrInputVm = {
  handleFileChange: (file: { raw: File }) => void
  handleRecognize: () => Promise<void>
  handleConfirm: () => Promise<void>
  recognizeResult: { status?: string; errorMessage?: string } | null
  selectedFile: File | null
}

type OcrHistoryVm = {
  historyRecords: unknown[]
  detailVisible: boolean
  detailData: unknown
  viewDetail: (record: { id: number }) => Promise<void>
  handleDelete: (id: number) => Promise<void>
  refresh: () => Promise<void>
}

function mountOcrInput() {
  return mount(OcrInput, {
    global: { stubs: ocrInputStubs, directives: { loading: () => {} } }
  })
}

function mountOcrHistory() {
  return mount(OcrHistory, {
    global: { stubs: ocrHistoryStubs, directives: { loading: () => {} } }
  })
}

function makeFile(size: number, type: string): File {
  const file = new File(['x'], 'sample.png', { type })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

function ok<T>(data: T) {
  return { code: 200, message: 'success', data }
}

describe('data-input/OcrInput.vue — 两条客户端前置校验保留（保持 · 非 API 失败）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    // 函数式旧 API ElMessage.error 在这两条路径上仍被使用（它们是被保留的两行）。
    document.body.innerHTML = ''
  })

  it('12MB 文件：恰好一条提示，且一个识别请求都没发', async () => {
    const wrapper = mountOcrInput()
    await flushPromises()

    const vm = wrapper.vm as unknown as OcrInputVm
    vm.handleFileChange({ raw: makeFile(12 * 1024 * 1024, 'image/png') })
    await flushPromises()

    // 前置校验走的是函数式旧 API —— 这正是「保持」的含义。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(1)
    expect(ElMessageMock.error).toHaveBeenCalledWith('文件大小不能超过10MB')
    expect(errorToastCount()).toBe(1)
    // 后半句是关键：请求必须被短路，不能「发了但被吞」。
    expect(requestUploadMock).not.toHaveBeenCalled()
    expect(vm.selectedFile).toBeNull()

    wrapper.unmount()
  })

  it('非图片类型：恰好一条提示，且一个识别请求都没发', async () => {
    const wrapper = mountOcrInput()
    await flushPromises()

    const vm = wrapper.vm as unknown as OcrInputVm
    vm.handleFileChange({ raw: makeFile(1024, 'application/pdf') })
    await flushPromises()

    expect(ElMessageMock.error).toHaveBeenCalledTimes(1)
    expect(ElMessageMock.error).toHaveBeenCalledWith('只能上传图片文件')
    expect(errorToastCount()).toBe(1)
    expect(requestUploadMock).not.toHaveBeenCalled()
    expect(vm.selectedFile).toBeNull()

    wrapper.unmount()
  })
})

describe('data-input/OcrInput.vue — 识别/确认失败恰好一条提示，面板失败态存活（ERR-07）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    // 迁移后的两条路径不得再碰函数式旧 API。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('识别失败：恰好一条提示，且面板仍是 failed 并带非空错误文案', async () => {
    requestUploadMock.mockRejectedValueOnce({
      response: { status: 500, data: {} }
    })

    const wrapper = mountOcrInput()
    await flushPromises()

    const vm = wrapper.vm as unknown as OcrInputVm
    vm.handleFileChange({ raw: makeFile(1024, 'image/png') })
    await flushPromises()

    await vm.handleRecognize()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    // 面板存活：status 与 errorMessage 两个断言缺一不可 ——
    // 只断言 toast 的话，把面板赋值删掉也能通过。
    expect(vm.recognizeResult?.status).toBe('failed')
    const panelMessage = vm.recognizeResult?.errorMessage ?? ''
    expect(panelMessage.length).toBeGreaterThan(0)
    // 面板与 toast 必须说同一句话（T-14-32）：scan 的签名在无后端文案时落到字典。
    expect(panelMessage).toBe(lastErrorToastText())

    wrapper.unmount()
  })

  it('确认录入失败：恰好一条提示，且选中结果不被清空（ERR-05）', async () => {
    requestPostMock.mockRejectedValueOnce({
      response: { status: 200, data: { code: 500, message: '指标类型与数值不匹配' } }
    })

    const wrapper = mountOcrInput()
    await flushPromises()

    const vm = wrapper.vm as unknown as OcrInputVm
    vm.recognizeResult = {
      status: 'success',
      metrics: [
        {
          metricKey: 'blood_glucose',
          name: '血糖',
          value: 5.6,
          unit: 'mmol/L',
          confidence: 0.95,
          category: 'HEALTH',
          recordDate: '2026-11-10',
          selected: true
        }
      ]
    } as unknown as OcrInputVm['recognizeResult']

    const before = JSON.parse(JSON.stringify(vm.recognizeResult))
    await vm.handleConfirm()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('指标类型与数值不匹配')
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)
    // 失败不清空、不重置：面板内容必须与调用前逐字相同。
    expect(JSON.parse(JSON.stringify(vm.recognizeResult))).toEqual(before)

    wrapper.unmount()
  })
})

describe('data-input/OcrHistory.vue — B1 不得回归：状态类失败仍恰好一条提示', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setActivePinia(createPinia())
    getHistoryMock.mockResolvedValue(
      ok({
        total: 1,
        page: 1,
        size: 10,
        records: [
          {
            id: 7,
            ocrType: 'report',
            ocrTypeDisplay: '体检报告',
            status: 'success',
            metricsCount: 1,
            confirmed: false,
            originalFilename: 'report.png',
            durationMs: 120,
            createTime: '2026-11-10 08:00:00'
          }
        ]
      })
    )
  })

  afterEach(() => {
    // 迁移后本文件零裸 error toast。
    expect(ElMessageMock.error).toHaveBeenCalledTimes(0)
    document.body.innerHTML = ''
  })

  it('查看一条已被删除的 OCR 记录（RESOURCE_NOT_FOUND 信封）：恰好一条提示，弹窗保持打开', async () => {
    // presentStates 是这条用例的整个存在理由：缺了它 showError 在 resolveMessage
    // 之前就 return，条数会是 0 —— 用户点「查看」后什么也看不到。
    getRecordDetailMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'resource.not_found', message: '记录不存在' }
      }
    })

    const wrapper = mountOcrHistory()
    await flushPromises()

    const vm = wrapper.vm as unknown as OcrHistoryVm
    await vm.viewDetail({ id: 7 })
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('记录不存在')
    // 一次详情失败不关弹窗，列表也与失败前一致（T-14-31）。
    expect(vm.detailVisible).toBe(true)
    expect(vm.historyRecords).toHaveLength(1)

    wrapper.unmount()
  })

  it('删除一条已被别人删掉的 OCR 记录（AUTH_FORBIDDEN 信封）：恰好一条提示，不是静默', async () => {
    // 动作型 403：本文件无状态块可落，没有 presentStates 就等于一声不响。
    deleteRecordMock.mockRejectedValueOnce({
      response: {
        status: 200,
        data: { code: 500, errorCode: 'auth.forbidden', message: '无权删除他人的识别记录' }
      }
    })

    const wrapper = mountOcrHistory()
    await flushPromises()

    const vm = wrapper.vm as unknown as OcrHistoryVm
    await vm.handleDelete(7)
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(lastErrorToastText()).toBe('无权删除他人的识别记录')
    expect(ElMessageMock.success).toHaveBeenCalledTimes(0)

    wrapper.unmount()
  })

  it('列表加载失败：一条提示，且表格保留原有行（不清空）', async () => {
    const wrapper = mountOcrHistory()
    await flushPromises()

    const vm = wrapper.vm as unknown as OcrHistoryVm
    const before = vm.historyRecords
    expect(before).toHaveLength(1)

    getHistoryMock.mockRejectedValueOnce({ response: { status: 500, data: {} } })
    await vm.refresh()
    await flushPromises()

    expect(errorToastCount()).toBe(1)
    expect(vm.historyRecords).toBe(before)
    expect(vm.historyRecords).toHaveLength(1)

    wrapper.unmount()
  })
})

describe('data-input — 源码级契约（保持 / 删除 / presentStates 全覆盖）', () => {
  function read(relPath: string): string {
    return readFileSync(resolve(process.cwd(), relPath), 'utf8')
  }

  /**
   * 取一个具名箭头函数的函数体。按声明签名定位（`const NAME = … => {`），
   * 再取到紧随的顶层 `\n}\n`，避免命中模板里的同名事件绑定。
   */
  function functionBody(source: string, name: string): string {
    const declaration = new RegExp(`const ${name} = [^\\n]*\\{`)
    const match = declaration.exec(source)
    expect(match, `未找到 ${name} 的声明`).toBeTruthy()
    const start = match!.index
    const rest = source.slice(start)
    const end = rest.indexOf('\n}\n')
    expect(end, `${name} 函数体未闭合`).toBeGreaterThan(-1)
    return rest.slice(0, end + 3)
  }

  it('OcrInput 的两条前置校验仍按「函数 + 字面量」原位留在 handleFileChange 内', () => {
    // 按函数范围断言，绝不按行号：本 plan 自己新增的 import 会让行号漂移。
    const body = functionBody(read('src/views/data-input/components/OcrInput.vue'), 'handleFileChange')
    expect(body).toContain("ElMessage.error('文件大小不能超过10MB')")
    expect(body).toContain("ElMessage.error('只能上传图片文件')")
    // 每个字面量在整文件里恰好出现一次 —— 证明它们没有被复制到别处。
    const source = read('src/views/data-input/components/OcrInput.vue')
    expect(source.split('文件大小不能超过10MB').length - 1).toBe(1)
    expect(source.split('只能上传图片文件').length - 1).toBe(1)
    // 全文件恰好 2 条 error：即这两条，没有第三条。
    expect(source.split('ElMessage.error').length - 1).toBe(2)
  })

  it('三个文件的每个 showError 都带 presentStates: true（无状态块 → 不许静默）', () => {
    for (const rel of [
      'src/views/data-input/components/OcrInput.vue',
      'src/views/data-input/components/OcrHistory.vue',
      'src/views/data-input/components/DeviceConfigWizard.vue'
    ]) {
      const source = read(rel)
      const calls = source.split('showError(').length - 1
      expect(calls).toBeGreaterThan(0)
      expect(source.split('presentStates: true').length - 1).toBe(calls)
      // 三个文件都不渲染状态块，因此 presentStates 是唯一兜底。
      expect(source).not.toContain('ErrorState')
    }
  })

  it('三个文件不再有「非 200」的不可达 else 兜底', () => {
    // OcrInput 的确认路径：`if (res.code === 200 && res.data)` 之后不得再有 else。
    expect(functionBody(read('src/views/data-input/components/OcrInput.vue'), 'handleConfirm')).not.toContain(
      '} else {'
    )
    // OcrHistory 的三个 handler 同理，且不再有 `res.message ||` 兜底文案。
    for (const name of ['loadHistory', 'viewDetail', 'handleDelete']) {
      const body = functionBody(read('src/views/data-input/components/OcrHistory.vue'), name)
      expect(body).not.toContain('ElMessage.error')
    }
    const historySource = read('src/views/data-input/components/OcrHistory.vue')
    expect(historySource).not.toContain('res.message ||')
    expect(read('src/views/data-input/components/DeviceConfigWizard.vue')).not.toContain('res.message ||')
  })

  it('OcrInput 的识别面板失败态赋值仍在，且由归一化结果驱动', () => {
    const source = read('src/views/data-input/components/OcrInput.vue')
    // 面板形状只定义一次（`status: 'failed'` 全文件恰好 1 处），两条失败路径共用。
    expect(source.split("status: 'failed'").length - 1).toBe(1)
    const body = functionBody(source, 'handleRecognize')
    expect(body).toContain('failedResult(')
    expect(body).toContain('resolveMessage')
    // 迁移掉的旧写法：从 Error 上直接读 message 再单独弹一条。
    expect(body).not.toContain('ElMessage.error')
  })

  it('OcrHistory 复用 ai.* 键而不是新造 ocr.* 近重复键', () => {
    const source = read('src/views/data-input/components/OcrHistory.vue')
    expect(source).toContain("showError(error, 'ai.loadFailed'")
    expect(source).toContain("showError(error, 'ai.deleteFailed'")
    expect(source).not.toContain("'ocr.loadFailed'")
    expect(source).not.toContain("'ocr.deleteFailed'")
  })

  it('OcrHistory 与 DeviceConfigWizard 的 showError 计数与文件匹配', () => {
    expect(read('src/views/data-input/components/OcrHistory.vue').split('showError(').length - 1).toBe(3)
    expect(
      read('src/views/data-input/components/DeviceConfigWizard.vue').split('showError(').length - 1
    ).toBe(1)
  })
})