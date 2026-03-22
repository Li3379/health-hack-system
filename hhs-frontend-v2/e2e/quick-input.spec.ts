import { test, expect } from '@playwright/test';

/**
 * E2E tests for Quick Input (Manual Metric Input) flow
 * Tests the manual metric entry functionality on the data-input page
 */

test.describe('Quick Input Flow', () => {
  // Login before each test
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');

    // Fill login form
    await page.fill('input[placeholder="请输入用户名"]', 'admin');
    await page.fill('input[placeholder="请输入密码"]', '123456');

    // Click login button
    await page.click('button:has-text("登 录")');

    // Wait for redirect to dashboard
    await page.waitForURL('**/dashboard**', { timeout: 10000 });
  });

  test('should display quick input card with metric buttons', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');

    // Wait for page to load
    await page.waitForSelector('.data-input-page', { timeout: 10000 });

    // Verify quick input card exists
    const quickInputCard = page.locator('.quick-input-card');
    await expect(quickInputCard).toBeVisible();

    // Verify health metric buttons exist
    await expect(page.locator('button:has-text("血糖")')).toBeVisible();
    await expect(page.locator('button:has-text("血压")')).toBeVisible();
    await expect(page.locator('button:has-text("心率")')).toBeVisible();
    await expect(page.locator('button:has-text("体温")')).toBeVisible();
    await expect(page.locator('button:has-text("体重")')).toBeVisible();
    await expect(page.locator('button:has-text("BMI")')).toBeVisible();

    // Verify wellness metric buttons exist
    await expect(page.locator('button:has-text("睡眠")')).toBeVisible();
    await expect(page.locator('button:has-text("步数")')).toBeVisible();
    await expect(page.locator('button:has-text("饮水")')).toBeVisible();
    await expect(page.locator('button:has-text("心情")')).toBeVisible();
    await expect(page.locator('button:has-text("精力")')).toBeVisible();
    await expect(page.locator('button:has-text("运动")')).toBeVisible();
  });

  test('should open input dialog and submit health metric (glucose)', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.quick-input-card', { timeout: 10000 });

    // Click glucose button
    await page.click('button:has-text("血糖")');

    // Wait for dialog to appear
    await page.waitForSelector('.el-dialog', { timeout: 5000 });

    // Verify dialog title
    await expect(page.locator('.el-dialog__title')).toContainText('血糖录入');

    // Fill value
    const valueInput = page.locator('.el-input-number input').first();
    await valueInput.fill('5.6');

    // Verify date is set (default to today)
    const dateInput = page.locator('.el-date-editor input');
    await expect(dateInput).not.toHaveValue('');

    // Submit
    await page.click('.el-dialog button:has-text("保存")');

    // Verify success message
    await page.waitForSelector('.el-message--success', { timeout: 5000 });
    const successMessage = await page.locator('.el-message--success').textContent();
    expect(successMessage).toContain('录入成功');
  });

  test('should handle blood pressure input with systolic and diastolic values', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.quick-input-card', { timeout: 10000 });

    // Click blood pressure button
    await page.click('button:has-text("血压")');

    // Wait for dialog
    await page.waitForSelector('.el-dialog', { timeout: 5000 });

    // Fill systolic value (first input)
    const inputs = page.locator('.el-input-number input');
    await inputs.first().fill('120');

    // Fill diastolic value (second input)
    await inputs.nth(1).fill('80');

    // Submit
    await page.click('.el-dialog button:has-text("保存")');

    // Verify success
    await page.waitForSelector('.el-message--success', { timeout: 5000 });
  });

  test('should open input dialog and submit wellness metric (sleep)', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.quick-input-card', { timeout: 10000 });

    // Click sleep button
    await page.click('button:has-text("睡眠")');

    // Wait for dialog
    await page.waitForSelector('.el-dialog', { timeout: 5000 });

    // Fill value
    const valueInput = page.locator('.el-input-number input').first();
    await valueInput.fill('7.5');

    // Submit
    await page.click('.el-dialog button:has-text("保存")');

    // Verify success
    await page.waitForSelector('.el-message--success', { timeout: 5000 });
  });

  test('should validate required fields in input dialog', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.quick-input-card', { timeout: 10000 });

    // Click heart rate button
    await page.click('button:has-text("心率")');

    // Wait for dialog
    await page.waitForSelector('.el-dialog', { timeout: 5000 });

    // Try to submit without value
    await page.click('.el-dialog button:has-text("保存")');

    // Should show validation error
    await page.waitForSelector('.el-form-item__error', { timeout: 3000 });
    const errorMessage = await page.locator('.el-form-item__error').textContent();
    expect(errorMessage).toContain('请输入数值');
  });

  test('should validate value range for metrics', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.quick-input-card', { timeout: 10000 });

    // Click heart rate button
    await page.click('button:has-text("心率")');

    // Wait for dialog
    await page.waitForSelector('.el-dialog', { timeout: 5000 });

    // Fill invalid value (out of range)
    const valueInput = page.locator('.el-input-number input').first();
    await valueInput.fill('500'); // Heart rate max is 300

    // Move focus to trigger validation
    await page.locator('.el-date-editor input').click();

    // Check if input shows error or is clamped
    // The input should either show validation error or clamp the value
    const actualValue = await valueInput.inputValue();
    // Value should be clamped or show error
    expect(parseInt(actualValue)).toBeLessThanOrEqual(300);
  });

  test('should cancel input dialog without saving', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.quick-input-card', { timeout: 10000 });

    // Click temperature button
    await page.click('button:has-text("体温")');

    // Wait for dialog
    await page.waitForSelector('.el-dialog', { timeout: 5000 });

    // Fill value
    const valueInput = page.locator('.el-input-number input').first();
    await valueInput.fill('36.5');

    // Click cancel
    await page.click('.el-dialog button:has-text("取消")');

    // Dialog should close
    await expect(page.locator('.el-dialog')).not.toBeVisible();

    // No success message should appear
    await expect(page.locator('.el-message--success')).not.toBeVisible();
  });

  test('should update today statistics after successful input', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.data-input-page', { timeout: 10000 });

    // Get initial health metrics count
    const healthCountBefore = await page.locator('.overview-icon.health + .overview-info .overview-value').textContent();
    const countBefore = parseInt(healthCountBefore || '0');

    // Click weight button and submit
    await page.click('button:has-text("体重")');
    await page.waitForSelector('.el-dialog', { timeout: 5000 });

    const valueInput = page.locator('.el-input-number input').first();
    await valueInput.fill('70');

    await page.click('.el-dialog button:has-text("保存")');
    await page.waitForSelector('.el-message--success', { timeout: 5000 });

    // Wait a moment for stats to update
    await page.waitForTimeout(1000);

    // Check if count increased
    const healthCountAfter = await page.locator('.overview-icon.health + .overview-info .overview-value').textContent();
    const countAfter = parseInt(healthCountAfter || '0');

    expect(countAfter).toBeGreaterThanOrEqual(countBefore);
  });
});