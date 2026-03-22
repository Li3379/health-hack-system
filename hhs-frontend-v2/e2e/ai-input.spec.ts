import { test, expect } from '@playwright/test';

/**
 * E2E tests for AI Input (Natural Language Parsing) flow
 * Tests the AI-powered natural language metric parsing functionality
 */

test.describe('AI Input Flow', () => {
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

  test('should display AI input card with textarea and parse button', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');

    // Wait for page to load
    await page.waitForSelector('.data-input-page', { timeout: 10000 });

    // Verify AI input card exists
    const aiInputCard = page.locator('.ai-input-card');
    await expect(aiInputCard).toBeVisible();

    // Verify card header
    await expect(page.locator('.ai-input-card .card-header')).toContainText('AI 智能录入');

    // Verify textarea exists
    const textarea = page.locator('.ai-input-card textarea');
    await expect(textarea).toBeVisible();

    // Verify parse button exists
    await expect(page.locator('.ai-input-card button:has-text("智能解析")')).toBeVisible();
  });

  test('should show remaining count for AI parsing', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Wait for remaining count to load
    await page.waitForSelector('.remaining-tag', { timeout: 5000 });

    // Verify remaining count is displayed
    const remainingTag = page.locator('.remaining-tag');
    await expect(remainingTag).toContainText('今日剩余');
    await expect(remainingTag).toContainText('次');
  });

  test('should parse natural language input and show results', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Fill natural language input
    const textarea = page.locator('.ai-input-card textarea');
    await textarea.fill('今天血糖5.6，心率72，走了8000步');

    // Click parse button
    await page.click('.ai-input-card button:has-text("智能解析")');

    // Wait for parse result
    await page.waitForSelector('.parse-result', { timeout: 15000 });

    // Verify parse result is displayed
    const parseResult = page.locator('.parse-result');
    await expect(parseResult).toBeVisible();

    // Verify metrics table is shown
    await expect(page.locator('.parse-result .el-table')).toBeVisible();

    // Verify confirm button is available
    await expect(page.locator('.parse-result button:has-text("确认录入")')).toBeVisible();
  });

  test('should display parsed metrics with confidence levels', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Fill natural language input
    const textarea = page.locator('.ai-input-card textarea');
    await textarea.fill('今天血压120/80');

    // Click parse button
    await page.click('.ai-input-card button:has-text("智能解析")');

    // Wait for parse result
    await page.waitForSelector('.parse-result', { timeout: 15000 });

    // Verify confidence column exists
    await expect(page.locator('.parse-result th:has-text("置信度")')).toBeVisible();

    // Verify progress bars for confidence are shown
    await expect(page.locator('.parse-result .el-progress')).toBeVisible();
  });

  test('should allow selecting/deselecting parsed metrics', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Fill natural language input
    const textarea = page.locator('.ai-input-card textarea');
    await textarea.fill('今天血糖5.6，心率72');

    // Click parse button
    await page.click('.ai-input-card button:has-text("智能解析")');

    // Wait for parse result
    await page.waitForSelector('.parse-result', { timeout: 15000 });

    // All checkboxes should be checked by default
    const checkboxes = page.locator('.parse-result .el-checkbox');
    const count = await checkboxes.count();

    for (let i = 0; i < count; i++) {
      await expect(checkboxes.nth(i)).toBeChecked();
    }

    // Click first checkbox to deselect
    await checkboxes.first().click();
    await expect(checkboxes.first()).not.toBeChecked();
  });

  test('should confirm and submit parsed metrics', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Fill natural language input
    const textarea = page.locator('.ai-input-card textarea');
    await textarea.fill('今天体重70公斤');

    // Click parse button
    await page.click('.ai-input-card button:has-text("智能解析")');

    // Wait for parse result
    await page.waitForSelector('.parse-result', { timeout: 15000 });

    // Click confirm button
    await page.click('.parse-result button:has-text("确认录入")');

    // Verify success message
    await page.waitForSelector('.el-message--success', { timeout: 10000 });
    const successMessage = await page.locator('.el-message--success').textContent();
    expect(successMessage).toContain('成功录入');

    // Verify textarea is cleared after success
    await expect(textarea).toHaveValue('');

    // Verify parse result is cleared
    await expect(page.locator('.parse-result')).not.toBeVisible();
  });

  test('should show warning when no metrics selected', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Fill natural language input
    const textarea = page.locator('.ai-input-card textarea');
    await textarea.fill('今天血糖5.6');

    // Click parse button
    await page.click('.ai-input-card button:has-text("智能解析")');

    // Wait for parse result
    await page.waitForSelector('.parse-result', { timeout: 15000 });

    // Deselect all metrics
    const checkbox = page.locator('.parse-result .el-checkbox');
    await checkbox.click();

    // Try to confirm
    await page.click('.parse-result button:has-text("确认录入")');

    // Should show warning message
    await page.waitForSelector('.el-message--warning', { timeout: 5000 });
    const warningMessage = await page.locator('.el-message--warning').textContent();
    expect(warningMessage).toContain('请至少选择一个指标');
  });

  test('should disable parse button when input is empty', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Parse button should be disabled initially
    const parseButton = page.locator('.ai-input-card button:has-text("智能解析")');
    await expect(parseButton).toBeDisabled();

    // Type something
    const textarea = page.locator('.ai-input-card textarea');
    await textarea.fill('test');

    // Button should be enabled
    await expect(parseButton).toBeEnabled();

    // Clear input
    await textarea.fill('');

    // Button should be disabled again
    await expect(parseButton).toBeDisabled();
  });

  test('should show loading state during parsing', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Fill natural language input
    const textarea = page.locator('.ai-input-card textarea');
    await textarea.fill('今天血糖5.6');

    // Click parse button
    const parseButton = page.locator('.ai-input-card button:has-text("智能解析")');
    await parseButton.click();

    // Button should show loading state
    await expect(parseButton).toHaveAttribute('class', /is-loading/);

    // Wait for result
    await page.waitForSelector('.parse-result', { timeout: 15000 });

    // Button should no longer be loading
    await expect(parseButton).not.toHaveAttribute('class', /is-loading/);
  });

  test('should handle parse errors gracefully', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Fill with potentially unparsable input
    const textarea = page.locator('.ai-input-card textarea');
    await textarea.fill('这是一段没有任何健康数据的文本');

    // Click parse button
    await page.click('.ai-input-card button:has-text("智能解析")');

    // Wait for response (either result or error)
    await page.waitForTimeout(15000);

    // Either shows empty result or error message
    const parseResultVisible = await page.locator('.parse-result').isVisible();
    const errorMessageVisible = await page.locator('.el-message--error').isVisible();

    // At least one should be true (handled gracefully)
    expect(parseResultVisible || errorMessageVisible).toBeTruthy();
  });

  test('should show character limit for input', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ai-input-card', { timeout: 10000 });

    // Check character limit indicator exists
    const wordLimit = page.locator('.el-input__count');
    await expect(wordLimit).toBeVisible();

    // Type some text
    const textarea = page.locator('.ai-input-card textarea');
    await textarea.fill('a'.repeat(100));

    // Check count is shown
    const count = await wordLimit.textContent();
    expect(count).toContain('100');
  });
});