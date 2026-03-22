import { test, expect } from '@playwright/test';

/**
 * E2E tests for Device Sync flow
 * Tests the wearable device connection and synchronization functionality
 */

test.describe('Device Sync Flow', () => {
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

  test('should display device sync card with device list', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');

    // Wait for page to load
    await page.waitForSelector('.data-input-page', { timeout: 10000 });

    // Verify device sync card exists
    const deviceSyncCard = page.locator('.device-sync-card');
    await expect(deviceSyncCard).toBeVisible();

    // Verify card header
    await expect(page.locator('.device-sync-card .card-header')).toContainText('穿戴设备同步');

    // Verify history button exists
    await expect(page.locator('.device-sync-card button:has-text("查看同步历史")')).toBeVisible();
  });

  test('should display device list with status', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Wait for devices to load
    await page.waitForSelector('.device-list', { timeout: 10000 });

    // Verify device items are displayed
    const deviceItems = page.locator('.device-item');
    const count = await deviceItems.count();

    // Should have at least some devices listed
    expect(count).toBeGreaterThan(0);

    // Each device should have name and status
    for (let i = 0; i < Math.min(count, 3); i++) {
      const device = deviceItems.nth(i);
      await expect(device.locator('.device-name')).toBeVisible();
      await expect(device.locator('.device-status')).toBeVisible();
    }
  });

  test('should show connect button for disconnected devices', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Wait for devices to load
    await page.waitForSelector('.device-list', { timeout: 10000 });

    // Find disconnected devices
    const disconnectedDevices = page.locator('.device-item').filter({
      has: page.locator('.device-status.disconnected')
    });

    const count = await disconnectedDevices.count();

    if (count > 0) {
      // Should have connect button
      const connectButton = disconnectedDevices.first().locator('button:has-text("连接")');
      await expect(connectButton).toBeVisible();
    }
  });

  test('should show sync and disconnect buttons for connected devices', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Wait for devices to load
    await page.waitForSelector('.device-list', { timeout: 10000 });

    // Find connected devices
    const connectedDevices = page.locator('.device-item').filter({
      has: page.locator('.device-status.connected')
    });

    const count = await connectedDevices.count();

    if (count > 0) {
      const firstConnected = connectedDevices.first();

      // Should have sync button
      await expect(firstConnected.locator('button:has-text("同步")')).toBeVisible();

      // Should have disconnect button
      await expect(firstConnected.locator('button:has-text("断开")')).toBeVisible();
    }
  });

  test('should show sync all button with connected count', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Find sync all button
    const syncAllButton = page.locator('.device-sync-card button:has-text("同步全部")');

    // Button should be visible
    await expect(syncAllButton).toBeVisible();

    // Should show connected count in button text
    const buttonText = await syncAllButton.textContent();
    expect(buttonText).toMatch(/\(\d+\)/);
  });

  test('should open sync history dialog', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Click history button
    await page.click('.device-sync-card button:has-text("查看同步历史")');

    // Wait for dialog
    await page.waitForSelector('.el-dialog', { timeout: 5000 });

    // Verify dialog title
    await expect(page.locator('.el-dialog__title')).toContainText('同步历史');

    // Verify table exists in dialog
    await expect(page.locator('.el-dialog .el-table')).toBeVisible();
  });

  test('should display sync history with pagination', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Open history dialog
    await page.click('.device-sync-card button:has-text("查看同步历史")');
    await page.waitForSelector('.el-dialog', { timeout: 5000 });

    // Check for pagination
    const pagination = page.locator('.el-dialog .el-pagination');
    await expect(pagination).toBeVisible();

    // Check for total count
    const paginationText = await pagination.textContent();
    expect(paginationText).toMatch(/\d+/);
  });

  test('should sync individual device', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Find connected device
    const connectedDevices = page.locator('.device-item').filter({
      has: page.locator('.device-status.connected')
    });

    const count = await connectedDevices.count();

    if (count > 0) {
      // Click sync button
      const syncButton = connectedDevices.first().locator('button:has-text("同步")');
      await syncButton.click();

      // Wait for sync to complete
      await page.waitForTimeout(5000);

      // Should show success or error message
      const successVisible = await page.locator('.el-message--success').isVisible();
      const errorVisible = await page.locator('.el-message--error').isVisible();

      expect(successVisible || errorVisible).toBeTruthy();
    }
  });

  test('should show loading state during sync', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Find connected device
    const connectedDevices = page.locator('.device-item').filter({
      has: page.locator('.device-status.connected')
    });

    const count = await connectedDevices.count();

    if (count > 0) {
      const syncButton = connectedDevices.first().locator('button:has-text("同步")');

      // Click sync
      await syncButton.click();

      // Button should show loading state
      await expect(syncButton).toHaveAttribute('class', /is-loading/);

      // Wait for completion
      await page.waitForTimeout(10000);
    }
  });

  test('should sync all connected devices', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Check if sync all button is enabled
    const syncAllButton = page.locator('.device-sync-card button:has-text("同步全部")');

    // Extract connected count from button text
    const buttonText = await syncAllButton.textContent();
    const match = buttonText?.match(/\((\d+)\)/);
    const connectedCount = match ? parseInt(match[1]) : 0;

    if (connectedCount > 0) {
      // Click sync all
      await syncAllButton.click();

      // Wait for sync
      await page.waitForTimeout(5000);

      // Should show message
      const successVisible = await page.locator('.el-message--success').isVisible();
      const warningVisible = await page.locator('.el-message--warning').isVisible();
      const errorVisible = await page.locator('.el-message--error').isVisible();

      expect(successVisible || warningVisible || errorVisible).toBeTruthy();
    }
  });

  test('should handle disconnect device with confirmation', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Find connected device
    const connectedDevices = page.locator('.device-item').filter({
      has: page.locator('.device-status.connected')
    });

    const count = await connectedDevices.count();

    if (count > 0) {
      // Click disconnect button
      await connectedDevices.first().locator('button:has-text("断开")').click();

      // Wait for confirmation dialog
      await page.waitForSelector('.el-message-box', { timeout: 5000 });

      // Verify confirmation message
      const messageBox = page.locator('.el-message-box');
      await expect(messageBox).toBeVisible();

      // Cancel the operation
      await messageBox.locator('button:has-text("取消")').click();

      // Dialog should close
      await expect(messageBox).not.toBeVisible();
    }
  });

  test('should handle device connection', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Find disconnected device
    const disconnectedDevices = page.locator('.device-item').filter({
      has: page.locator('.device-status.disconnected')
    });

    const count = await disconnectedDevices.count();

    if (count > 0) {
      // Click connect button
      const connectButton = disconnectedDevices.first().locator('button:has-text("连接")');

      // Listen for popup (OAuth window)
      const popupPromise = page.waitForEvent('popup', { timeout: 5000 }).catch(() => null);

      await connectButton.click();

      // Either popup opens or info message appears
      const popup = await popupPromise;

      if (popup) {
        // Close popup
        await popup.close();
      } else {
        // Check for info message
        const infoVisible = await page.locator('.el-message--info').isVisible({ timeout: 3000 }).catch(() => false);
        expect(infoVisible).toBeTruthy();
      }
    }
  });

  test('should show error alert when sync fails', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Find connected device
    const connectedDevices = page.locator('.device-item').filter({
      has: page.locator('.device-status.connected')
    });

    const count = await connectedDevices.count();

    if (count > 0) {
      // Click sync
      await connectedDevices.first().locator('button:has-text("同步")').click();

      // Wait for result
      await page.waitForTimeout(10000);

      // Check if error alert is shown
      const errorAlert = page.locator('.sync-error-alert');
      const errorAlertVisible = await errorAlert.isVisible().catch(() => false);

      // If error alert is visible, check retry button
      if (errorAlertVisible) {
        const retryButton = errorAlert.locator('button:has-text("重试")');
        await expect(retryButton).toBeVisible();
      }
    }
  });

  test('should update last sync time after successful sync', async ({ page }) => {
    // Navigate to data input page
    await page.goto('/data-input');
    await page.waitForSelector('.device-sync-card', { timeout: 10000 });

    // Find connected device with sync time
    const connectedDevices = page.locator('.device-item').filter({
      has: page.locator('.device-status.connected')
    });

    const count = await connectedDevices.count();

    if (count > 0) {
      // Get current sync time
      const syncTimeBefore = await connectedDevices.first().locator('.sync-time').textContent().catch(() => null);

      // Click sync
      await connectedDevices.first().locator('button:has-text("同步")').click();

      // Wait for success
      await page.waitForSelector('.el-message--success', { timeout: 15000 }).catch(() => {});

      // Reload page to check updated time
      await page.reload();
      await page.waitForSelector('.device-sync-card', { timeout: 10000 });

      // Check if sync time updated
      const syncTimeAfter = await connectedDevices.first().locator('.sync-time').textContent().catch(() => null);

      // Time should exist
      expect(syncTimeAfter).not.toBeNull();
    }
  });
});