import { describe, it, expect, vi, beforeEach } from 'vitest'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { mount, flushPromises } from '@vue/test-utils'
import type { Mock } from 'vitest'

/**
 * 「最近录入」的三个契约：
 *
 * 1. 不再渲染恒错的「来源」列 —— health_metric 表没有 source 列，后端 HealthMetricVO 与
 *    前端 types/api.ts 的 HealthMetricVO 也都没有该字段，旧列对每一行都走默认分支渲染
 *    「手动录入」，AI / OCR 录入的同样被标成手动。
 * 2. 改由后端 /api/metrics/recent 一条查询跨 category 取数，不再并行请求 /api/metrics 与
 *    /api/wellness 各自截断再在浏览器 slice —— 那会让保健类占满名额、把刚录入的健康指标
 *    整段挤掉。
 * 3. 日期分两列：记录日期（recordDate，用户填的那天）与录入时间（createTime）。旧实现只
 *    渲染 createTime 的 HH:mm，补录历史数据时看起来像是今天录的。
 */

vi.mock('@/api/health', () => ({
  healthApi: { getMetrics: vi.fn(), getRecentMetrics: vi.fn() }
}))

import RecentRecords from '@/views/data-input/components/RecentRecords.vue'
import { healthApi } from '@/api/health'

const getHealthMetrics = healthApi.getMetrics as unknown as Mock
const getRecentMetrics = healthApi.getRecentMetrics as unknown as Mock

/**
 * el-table 用 provide 把行数据交给列 stub，列才能真的把 default slot 渲染出来。
 * 之前的 stub 只渲染 label，日期列的单元格内容根本没被求值。
 */
const stubs = {
  'el-card': { template: '<div class="el-card"><slot name="header" /><slot /></div>', props: ['shadow'] },
  'el-table': {
    props: ['data', 'stripe'],
    provide() {
      return { tableRows: () => (this as unknown as { data: unknown[] }).data ?? [] }
    },
    template: '<div class="el-table"><slot /></div>'
  },
  'el-table-column': {
    inject: { tableRows: { default: () => () => [] } },
    props: ['label', 'width', 'prop'],
    template: `<div class="el-table-column" :data-label="label">
        <span class="col-label">{{ label }}</span>
        <div v-for="(row, i) in tableRows()" :key="i" class="cell"><slot :row="row" /></div>
      </div>`
  },
  'el-button': { template: '<button class="el-button"><slot /></button>', props: ['type', 'text', 'size'] },
  'el-tag': { template: '<span class="el-tag"><slot /></span>', props: ['type', 'size'] },
  'el-icon': { template: '<span class="el-icon"><slot /></span>' },
  Clock: { template: '<span class="icon-clock" />' }
}

function metric(id: number, metricKey: string, metricName: string) {
  return {
    id,
    userId: 7,
    metricKey,
    metricName,
    value: 7.5,
    unit: '小时',
    recordDate: '2026-11-23',
    trend: 'STABLE',
    createTime: '2026-11-23T10:00:00'
  }
}

function mountRecords() {
  return mount(RecentRecords, { global: { stubs, directives: { loading: () => {} } } })
}

function labels(wrapper: ReturnType<typeof mountRecords>) {
  return wrapper.findAll('.col-label').map(node => node.text())
}

function cellsOf(wrapper: ReturnType<typeof mountRecords>, label: string) {
  return wrapper.find(`.el-table-column[data-label="${label}"]`).findAll('.cell').map(n => n.text())
}

describe('RecentRecords.spec.ts — 最近录入的取数与列契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getRecentMetrics.mockResolvedValue({
      code: 200,
      data: [metric(1, 'heartRate', '心率'), metric(2, 'sleepDuration', '睡眠时长')]
    })
  })

  it('列头是 记录日期 / 录入时间 / 指标 / 数值，不再有「来源」', async () => {
    const wrapper = mountRecords()
    await flushPromises()

    const heads = labels(wrapper)
    expect(heads).toEqual(['记录日期', '录入时间', '指标', '数值'])
    expect(heads).not.toContain('来源')
    // 单独的「时间」标题会让用户无法分辨是记录日还是录入时刻
    expect(heads).not.toContain('时间')
    wrapper.unmount()
  })

  it('补录历史日期时，记录日期列显示 recordDate 而不是 createTime 的那一天', async () => {
    // DB 实测形态：record_date=2026-11-19、create_time=2026-11-23 15:03
    getRecentMetrics.mockResolvedValue({
      code: 200,
      data: [{ ...metric(256, 'bodyTemperature', '体温'), recordDate: '2026-11-19', createTime: '2026-11-23T15:03:00' }]
    })

    const wrapper = mountRecords()
    await flushPromises()

    expect(cellsOf(wrapper, '记录日期')).toEqual(['2026-11-19'])
    const createTimeCell = cellsOf(wrapper, '录入时间')[0]
    expect(createTimeCell).toContain('2026')
    expect(createTimeCell).toContain('11-23')
    // 旧实现只给 HH:mm，看不出是哪一天
    expect(createTimeCell).not.toMatch(/^\s*\d{2}:\d{2}\s*$/)
    wrapper.unmount()
  })

  it('后端 Jackson 的空格分隔时间（yyyy-MM-dd HH:mm:ss）也能渲染', async () => {
    // JacksonConfig 输出的是 "2026-11-23 15:03:00"：Safari 的 new Date 不接受这种写法
    getRecentMetrics.mockResolvedValue({
      code: 200,
      data: [{ ...metric(310, 'bodyTemperature', '体温'), recordDate: '2026-11-19', createTime: '2026-11-23 15:03:00' }]
    })

    const wrapper = mountRecords()
    await flushPromises()

    const cell = cellsOf(wrapper, '录入时间')[0]
    expect(cell).toContain('2026-11-23')
    expect(cell).toContain('15:03')
    wrapper.unmount()
  })

  it('recordDate 缺失时不渲染空白/错误日期（不拼 recordTime 空值）', async () => {
    getRecentMetrics.mockResolvedValue({
      code: 200,
      data: [{ ...metric(300, 'heartRate', '心率'), recordDate: '', recordTime: null }]
    })

    const wrapper = mountRecords()
    await flushPromises()

    expect(cellsOf(wrapper, '记录日期')).toEqual([''])
    // 表头仍在，说明列不是因为空值被 v-if 掉
    expect(labels(wrapper)).toContain('记录日期')
    wrapper.unmount()
  })

  it('页面上不再出现「手动录入」这个恒定错值', async () => {
    const wrapper = mountRecords()
    await flushPromises()

    expect(wrapper.text()).not.toContain('手动录入')
    expect(wrapper.text()).not.toContain('AI识别')
    expect(wrapper.text()).not.toContain('OCR识别')
    wrapper.unmount()
  })

  it('只调用一条跨 category 的 /api/metrics/recent，不再各取一页再合并', async () => {
    const wrapper = mountRecords()
    await flushPromises()

    expect(getRecentMetrics).toHaveBeenCalledTimes(1)
    expect(getRecentMetrics).toHaveBeenCalledWith(10)
    // 旧的合并路径会同时打这两个接口，任何一个出现都说明分页截断的口子还在
    expect(getHealthMetrics).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('把后端返回的混排结果原样按序展示（不再自己 slice / 重排）', async () => {
    getRecentMetrics.mockResolvedValue({
      code: 200,
      data: [
        metric(11, 'heartRate', '心率'),
        metric(12, 'sleepDuration', '睡眠时长'),
        metric(13, 'bloodGlucose', '血糖')
      ]
    })

    const wrapper = mountRecords()
    await flushPromises()

    // 新的列 stub 会渲染单元格，顺序可直接从记录日期列读出来
    expect(cellsOf(wrapper, '记录日期')).toHaveLength(3)
    const vm = wrapper.vm as unknown as { records: Array<{ id: number }> }
    expect(vm.records.map(r => r.id)).toEqual([11, 12, 13])
    wrapper.unmount()
  })

  it('接口失败时保留空列表，不抛错也不渲染残留数据', async () => {
    getRecentMetrics.mockRejectedValue({ response: { status: 500 } })

    const wrapper = mountRecords()
    await flushPromises()

    const vm = wrapper.vm as unknown as { records: unknown[] }
    expect(vm.records).toEqual([])
    wrapper.unmount()
  })

  it('组件源码里不再引用 row.source，也不再请求 wellnessApi', () => {
    const source = readFileSync(
      resolve(process.cwd(), 'src/views/data-input/components/RecentRecords.vue'),
      'utf8'
    )
    // 注释块解释了为什么移除，但代码里不能有对 source 的读取
    const codeLines = source
      .split('\n')
      .filter(line => !line.trim().startsWith('//'))
      .filter(line => !line.trim().startsWith('*'))
    expect(codeLines.some(line => /row\.source/.test(line) || /getSource(Name|Type)/.test(line))).toBe(false)
    expect(codeLines.some(line => /wellnessApi/.test(line))).toBe(false)
  })
})
