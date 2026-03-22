import { test, expect } from '@playwright/test';

test.describe('Wellness Feature Test', () => {
  test('login and test wellness metric creation', async ({ page }) => {
    // Navigate to login page
    await page.goto('http://localhost:5173/#/login');
    
    // Login
    await page.fill('input[type="text"]', 'admin');
    await page.fill('input[type="password"]', '123456');
    await page.click('button:has-text("登录")');
    
    // Wait for redirect to dashboard
    await page.waitForURL('**/dashboard**', { timeout: 10000 });
    
    // Navigate to prevention metrics page (保健指标)
    await page.goto('http://localhost:5173/#/prevention/metrics');
    
    // Wait for page to load
    await page.waitForSelector('.prevention-metrics-page', { timeout: 10000 });
    
    // Click add metric button
    await page.click('button:has-text("添加指标")');
    
    // Wait for dialog
    await page.waitForSelector('.el-dialog');
    
    // Select metric type (sleep duration)
    await page.click('.el-select:has(.el-input__wrapper) .el-input__wrapper');
    await page.click('.el-select-dropdown__item:has-text("睡眠时长")');
    
    // Fill value
    await page.fill('.el-input-number input', '7.5');
    
    // Fill date (today)
    const today = new Date().toISOString().split('T')[0];
    await page.fill('.el-date-editor input', today);
    
    // Click add button
    await page.click('.el-dialog button:has-text("确定")');
    
    // Check for success message
    await page.waitForSelector('.el-message--success', { timeout: 5000 });
    
    // Verify the metric was added
    const successMessage = await page.locator('.el-message--success').textContent();
    expect(successMessage).toContain('添加成功');
  });
});
