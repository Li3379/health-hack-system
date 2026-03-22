import { test, expect } from '@playwright/test';
import path from 'path';

/**
 * E2E tests for OCR Input (Image Recognition) flow
 * Tests the OCR-based health report recognition functionality
 */

test.describe('OCR Input Flow', () => {
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

  test('should display OCR input card with upload area', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');

    // Wait for page to load
    await page.waitForSelector('.data-input-page', { timeout: 10000 });

    // Verify OCR input card exists
    const ocrInputCard = page.locator('.ocr-input-card');
    await expect(ocrInputCard).toBeVisible();

    // Verify card header
    await expect(page.locator('.ocr-input-card .card-header')).toContainText('OCR 图片识别');

    // Verify OCR type selector exists
    await expect(page.locator('.ocr-type-selector')).toBeVisible();

    // Verify upload area exists
    await expect(page.locator('.ocr-uploader')).toBeVisible();

    // Verify recognize button exists (disabled initially)
    const recognizeButton = page.locator('.ocr-input-card button:has-text("开始识别")');
    await expect(recognizeButton).toBeDisabled();
  });

  test('should display OCR type options', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Verify type options exist
    await expect(page.locator('.el-radio-button:has-text("体检报告")')).toBeVisible();
    await expect(page.locator('.el-radio-button:has-text("药品标签")')).toBeVisible();
    await expect(page.locator('.el-radio-button:has-text("营养标签")')).toBeVisible();

    // Default should be "体检报告"
    const reportRadio = page.locator('.el-radio-button:has-text("体检报告")');
    await expect(reportRadio).toHaveAttribute('class', /is-active/);
  });

  test('should allow switching OCR type', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Click on medicine label option
    await page.click('.el-radio-button:has-text("药品标签")');

    // Verify it's selected
    const medicineRadio = page.locator('.el-radio-button:has-text("药品标签")');
    await expect(medicineRadio).toHaveAttribute('class', /is-active/);

    // Click on nutrition label option
    await page.click('.el-radio-button:has-text("营养标签")');

    // Verify it's selected
    const nutritionRadio = page.locator('.el-radio-button:has-text("营养标签")');
    await expect(nutritionRadio).toHaveAttribute('class', /is-active/);
  });

  test('should upload image and show preview', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');

    await fileInput.setInputFiles(sampleImagePath);

    // Wait for preview to appear
    await page.waitForSelector('.preview-container', { timeout: 5000 });

    // Verify preview image is visible
    await expect(page.locator('.preview-image')).toBeVisible();

    // Verify recognize button is now enabled
    const recognizeButton = page.locator('.ocr-input-card button:has-text("开始识别")');
    await expect(recognizeButton).toBeEnabled();
  });

  test('should clear preview when remove button clicked', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');
    await fileInput.setInputFiles(sampleImagePath);

    // Wait for preview
    await page.waitForSelector('.preview-container', { timeout: 5000 });

    // Click remove button
    await page.click('.remove-btn');

    // Preview should be gone
    await expect(page.locator('.preview-container')).not.toBeVisible();

    // Recognize button should be disabled again
    const recognizeButton = page.locator('.ocr-input-card button:has-text("开始识别")');
    await expect(recognizeButton).toBeDisabled();
  });

  test('should perform OCR recognition and show results', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');
    await fileInput.setInputFiles(sampleImagePath);

    // Wait for preview
    await page.waitForSelector('.preview-container', { timeout: 5000 });

    // Click recognize button
    await page.click('.ocr-input-card button:has-text("开始识别")');

    // Wait for result
    await page.waitForSelector('.recognize-result', { timeout: 30000 });

    // Verify result is displayed
    const result = page.locator('.recognize-result');
    await expect(result).toBeVisible();

    // Verify status tag is shown
    await expect(page.locator('.recognize-result .el-tag')).toBeVisible();
  });

  test('should display recognized metrics in table', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');
    await fileInput.setInputFiles(sampleImagePath);

    await page.waitForSelector('.preview-container', { timeout: 5000 });

    // Click recognize
    await page.click('.ocr-input-card button:has-text("开始识别")');

    // Wait for result
    await page.waitForSelector('.recognize-result', { timeout: 30000 });

    // If metrics were found, verify table structure
    const metricsTable = page.locator('.recognize-result .el-table');
    if (await metricsTable.isVisible()) {
      // Verify column headers
      await expect(page.locator('.recognize-result th:has-text("指标")')).toBeVisible();
      await expect(page.locator('.recognize-result th:has-text("数值")')).toBeVisible();
      await expect(page.locator('.recognize-result th:has-text("分类")')).toBeVisible();
      await expect(page.locator('.recognize-result th:has-text("置信度")')).toBeVisible();
    }
  });

  test('should allow selecting/deselecting recognized metrics', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');
    await fileInput.setInputFiles(sampleImagePath);

    await page.waitForSelector('.preview-container', { timeout: 5000 });
    await page.click('.ocr-input-card button:has-text("开始识别")');
    await page.waitForSelector('.recognize-result', { timeout: 30000 });

    // Check if metrics table exists
    const checkboxes = page.locator('.recognize-result .el-checkbox');
    const count = await checkboxes.count();

    if (count > 0) {
      // First checkbox should be checked by default
      await expect(checkboxes.first()).toBeChecked();

      // Click to deselect
      await checkboxes.first().click();
      await expect(checkboxes.first()).not.toBeChecked();
    }
  });

  test('should confirm and submit recognized metrics', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');
    await fileInput.setInputFiles(sampleImagePath);

    await page.waitForSelector('.preview-container', { timeout: 5000 });
    await page.click('.ocr-input-card button:has-text("开始识别")');
    await page.waitForSelector('.recognize-result', { timeout: 30000 });

    // Check if there are metrics to confirm
    const checkboxes = page.locator('.recognize-result .el-checkbox');
    const count = await checkboxes.count();

    if (count > 0) {
      // Click confirm button
      await page.click('.recognize-result button:has-text("确认录入")');

      // Verify success message
      await page.waitForSelector('.el-message--success', { timeout: 10000 });

      // Result should be cleared
      await expect(page.locator('.recognize-result')).not.toBeVisible();
    }
  });

  test('should show warning when no metrics selected for confirmation', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');
    await fileInput.setInputFiles(sampleImagePath);

    await page.waitForSelector('.preview-container', { timeout: 5000 });
    await page.click('.ocr-input-card button:has-text("开始识别")');
    await page.waitForSelector('.recognize-result', { timeout: 30000 });

    // Check if there are metrics
    const checkboxes = page.locator('.recognize-result .el-checkbox');
    const count = await checkboxes.count();

    if (count > 0) {
      // Deselect all
      for (let i = 0; i < count; i++) {
        const checkbox = checkboxes.nth(i);
        if (await checkbox.isChecked()) {
          await checkbox.click();
        }
      }

      // Try to confirm
      await page.click('.recognize-result button:has-text("确认录入")');

      // Should show warning
      await page.waitForSelector('.el-message--warning', { timeout: 5000 });
    }
  });

  test('should validate file type on upload', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Try to upload invalid file type
    const invalidFilePath = path.join(__dirname, 'fixtures', 'invalid.txt');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');

    await fileInput.setInputFiles(invalidFilePath);

    // Should show error message
    await page.waitForSelector('.el-message--error', { timeout: 5000 });
    const errorMessage = await page.locator('.el-message--error').textContent();
    expect(errorMessage).toContain('图片');
  });

  test('should show loading state during recognition', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');
    await fileInput.setInputFiles(sampleImagePath);

    await page.waitForSelector('.preview-container', { timeout: 5000 });

    // Click recognize
    const recognizeButton = page.locator('.ocr-input-card button:has-text("开始识别")');
    await recognizeButton.click();

    // Button should show loading state
    await expect(recognizeButton).toHaveAttribute('class', /is-loading/);

    // Wait for result
    await page.waitForSelector('.recognize-result', { timeout: 30000 });

    // Button should no longer be loading
    await expect(recognizeButton).not.toHaveAttribute('class', /is-loading/);
  });

  test('should allow canceling recognition result', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');
    await fileInput.setInputFiles(sampleImagePath);

    await page.waitForSelector('.preview-container', { timeout: 5000 });
    await page.click('.ocr-input-card button:has-text("开始识别")');
    await page.waitForSelector('.recognize-result', { timeout: 30000 });

    // Click cancel button
    await page.click('.recognize-result button:has-text("取消")');

    // Result should be cleared
    await expect(page.locator('.recognize-result')).not.toBeVisible();
  });

  test('should show raw text collapse if available', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.ocr-input-card', { timeout: 10000 });

    // Upload sample image
    const sampleImagePath = path.join(__dirname, 'fixtures', 'sample-report.png');
    const fileInput = page.locator('.ocr-uploader input[type="file"]');
    await fileInput.setInputFiles(sampleImagePath);

    await page.waitForSelector('.preview-container', { timeout: 5000 });
    await page.click('.ocr-input-card button:has-text("开始识别")');
    await page.waitForSelector('.recognize-result', { timeout: 30000 });

    // Check if raw text collapse exists
    const rawTextCollapse = page.locator('.raw-text-collapse');
    if (await rawTextCollapse.isVisible()) {
      // Click to expand
      await page.click('.raw-text-collapse .el-collapse-item__header');

      // Verify content is shown
      await expect(page.locator('.raw-text-content')).toBeVisible();
    }
  });
});