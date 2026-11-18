// QA 回归验证：确认 5 项修复在真实浏览器中生效
// 运行: npx playwright test qa-regression.spec.ts --reporter=list
import { test, expect, type ConsoleMessage } from '@playwright/test'

const BASE = 'http://localhost:5174'
const USER = 'qa_wb_01'
const PASS = 'QaTest@123456'

test.describe('缺陷修复回归', () => {
  let consoleErrors: string[] = []

  test.beforeEach(async ({ page }) => {
    consoleErrors = []
    page.on('console', (msg: ConsoleMessage) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text())
    })
    await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' })
    await page.getByRole('textbox', { name: '请输入用户名' }).fill(USER)
    await page.getByRole('textbox', { name: '请输入密码' }).fill(PASS)
    await page.getByRole('button', { name: '登 录' }).click()
    await page.waitForURL('**/dashboard', { timeout: 20000 })
    await page.waitForTimeout(2500)
  })

  test('FIX-1 仪表盘无 ECharts 组件缺失错误', async ({ page }) => {
    const echartsErrors = consoleErrors.filter(
      e => e.includes('echarts') || e.includes('Component grid') || e.includes('xAxis')
    )
    expect(echartsErrors, `ECharts errors: ${echartsErrors.join(' | ')}`).toHaveLength(0)
    // Sparkline 应已渲染出 canvas
    const canvases = await page.locator('canvas').count()
    expect(canvases).toBeGreaterThan(0)
  })

  test('FIX-2 实时监控菜单图标已解析（无 Vue 组件告警）', async ({ page }) => {
    const monitorWarn = consoleErrors.filter(e => e.includes('Failed to resolve component'))
    expect(monitorWarn).toHaveLength(0)
    // 侧栏「实时监控」菜单项存在且其中有 svg 图标
    const item = page.locator('.el-menu-item', { hasText: '实时监控' })
    await expect(item).toHaveCount(1)
    await expect(item.locator('svg').first()).toBeVisible()
  })

  test('FIX-3 图标按钮具备可访问名称', async ({ page }) => {
    const collapse = page.locator('.collapse-btn')
    const theme = page.locator('.theme-toggle')
    await expect(collapse).toHaveAttribute('aria-label', /.+/)
    await expect(theme).toHaveAttribute('aria-label', /.+/)
    const userInfo = page.locator('.user-info')
    await expect(userInfo).toHaveAttribute('aria-label', /.+/)
  })

  test('FIX-4 AI 确认录入后解析历史自动刷新', async ({ page }) => {
    await page.goto(`${BASE}/data-input`, { waitUntil: 'networkidle' })
    await page.waitForTimeout(1500)

    const marker = `回归${Date.now() % 100000}`
    await page.locator('textarea').first().fill(`今天心率${68 + (Date.now() % 10)}，走了${7000 + (Date.now() % 500)}步 ${marker}`)
    await page.getByRole('button', { name: '智能解析' }).click()

    const confirmBtn = page.getByRole('button', { name: '确认录入' })
    await expect(confirmBtn).toBeVisible({ timeout: 60000 })
    await confirmBtn.click()

    // 关键断言 1：本次解析记录（marker 文本）自动出现在页面上，无需手动点刷新
    await expect(page.getByText(marker, { exact: false })).toBeVisible({ timeout: 15000 })
    // 关键断言 2：占位符不再停留在「暂无解析记录」
    await expect(page.getByText('暂无解析记录')).toHaveCount(0)
  })

  test('FIX-5 404 资源显示「不存在」提示而非服务故障', async ({ page }) => {
    // 前端通过 errorCode + 数值 404 判定 notFound
    const res = await page.request.get(`${BASE}/api/users/999999`, {
      headers: { Authorization: `Bearer ${await page.evaluate(() => localStorage.getItem('hhs_token') || '')}` }
    })
    const body = await res.json()
    expect(body.code).toBe(404)
    expect(body.errorCode).toBe('user.not_found')
  })
})