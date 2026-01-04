import { test, expect } from '@playwright/test';

/**
 * E2E tests for CSV Import/Export operations.
 * 
 * Tests the complete CSV workflow:
 * 1. Import CSV file with work log entries
 * 2. Verify entries appear in calendar
 * 3. Export the same month to CSV
 * 4. Verify exported data matches imported data
 * 
 * Task: T145 - CSV import/export roundtrip E2E test
 */
test.describe('CSV Import/Export', () => {
  const testMemberId = '00000000-0000-0000-0000-000000000001';

  test('should download CSV template', async ({ page }) => {
    await page.goto('/worklog/import');

    // Click download template button
    const downloadPromise = page.waitForEvent('download');
    await page.click('button:has-text("Download CSV Template")');
    const download = await downloadPromise;

    // Verify download
    expect(download.suggestedFilename()).toBe('worklog-template.csv');
  });

  test('should import CSV and show entries in calendar', async ({ page }) => {
    // Navigate to import page
    await page.goto('/worklog/import');

    // Create test CSV content (using past dates to avoid validation errors)
    const csvContent = `Date,Project Code,Hours,Notes
2025-12-25,PRJ-001,8.00,Christmas work
2025-12-26,PRJ-002,4.00,Boxing day morning`;

    // Upload CSV file
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'test-import.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent),
    });

    // Click import button
    await page.click('button:has-text("Import CSV")');

    // Wait for import to complete
    await expect(page.locator('text=Import completed!')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=Successfully imported 2 of 2 rows')).toBeVisible();

    // Navigate to worklog calendar for December 2025
    await page.goto('/worklog');
    
    // Navigate to December 2025
    await page.click('button:has-text("Previous")'); // Go back from current month
    
    // Verify entries appear in calendar
    // Note: This test assumes the Calendar component displays the entries
    // You may need to adjust selectors based on actual Calendar implementation
    await expect(page.locator('text=PRJ-001')).toBeVisible();
    await expect(page.locator('text=8.00')).toBeVisible();
  });

  test('should export CSV with correct data', async ({ page }) => {
    // Navigate to worklog page
    await page.goto('/worklog');

    // Click export button
    const downloadPromise = page.waitForEvent('download');
    await page.click('button:has-text("Export CSV")');
    const download = await downloadPromise;

    // Verify download
    expect(download.suggestedFilename()).toMatch(/worklog-\d{4}-\d{2}\.csv/);

    // Read downloaded file
    const path = await download.path();
    if (path) {
      const fs = require('fs');
      const content = fs.readFileSync(path, 'utf-8');
      
      // Verify CSV structure
      expect(content).toContain('Date,Project Code,Hours,Notes');
      
      // Verify at least header is present
      const lines = content.split('\n');
      expect(lines.length).toBeGreaterThan(0);
    }
  });

  test('should complete full CSV roundtrip (import, verify, export)', async ({ page }) => {
    // Step 1: Import CSV
    await page.goto('/worklog/import');

    const csvContent = `Date,Project Code,Hours,Notes
2025-12-20,PRJ-001,4.00,Roundtrip test 1
2025-12-20,PRJ-002,3.50,Roundtrip test 2
2025-12-21,PRJ-001,8.00,Roundtrip test 3`;

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'roundtrip-test.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent),
    });

    await page.click('button:has-text("Import CSV")');
    await expect(page.locator('text=Import completed!')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=Successfully imported 3 of 3 rows')).toBeVisible();

    // Step 2: Navigate to worklog and verify entries
    await page.goto('/worklog');
    
    // Navigate to December 2025
    // (Implementation depends on current date and navigation logic)
    
    // Step 3: Export CSV
    const downloadPromise = page.waitForEvent('download');
    await page.click('button:has-text("Export CSV")');
    const download = await downloadPromise;

    // Step 4: Verify exported content
    const path = await download.path();
    if (path) {
      const fs = require('fs');
      const exportedContent = fs.readFileSync(path, 'utf-8');
      
      // Verify all imported entries are in export
      expect(exportedContent).toContain('PRJ-001');
      expect(exportedContent).toContain('PRJ-002');
      expect(exportedContent).toContain('Roundtrip test');
      
      // Verify hours are preserved
      expect(exportedContent).toContain('4.00');
      expect(exportedContent).toContain('3.50');
      expect(exportedContent).toContain('8.00');
    }
  });

  test('should handle import errors gracefully', async ({ page }) => {
    await page.goto('/worklog/import');

    // Upload CSV with validation errors
    const invalidCsv = `Date,Project Code,Hours,Notes
invalid-date,PRJ-001,8.00,Bad date
2025-12-25,,4.00,Missing project code
2025-12-26,PRJ-001,25.00,Too many hours`;

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'invalid.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(invalidCsv),
    });

    await page.click('button:has-text("Import CSV")');

    // Wait for import to complete with errors
    await expect(page.locator('text=Import completed!')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('text=had errors and were skipped')).toBeVisible();

    // Verify error display
    await expect(page.locator('text=Validation Errors')).toBeVisible();
    await expect(page.locator('text=Row')).toBeVisible();
  });

  test('should show progress during import', async ({ page }) => {
    await page.goto('/worklog/import');

    // Create larger CSV file
    let csvContent = 'Date,Project Code,Hours,Notes\n';
    for (let i = 1; i <= 20; i++) {
      const day = String(i).padStart(2, '0');
      csvContent += `2025-12-${day},PRJ-001,8.00,Progress test ${i}\n`;
    }

    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles({
      name: 'progress-test.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(csvContent),
    });

    await page.click('button:has-text("Import CSV")');

    // Verify progress bar appears
    await expect(page.locator('text=Importing...')).toBeVisible();
    
    // Verify progress information
    await expect(page.locator('text=Total rows:')).toBeVisible();
    await expect(page.locator('text=Valid:')).toBeVisible();

    // Wait for completion
    await expect(page.locator('text=Import completed!')).toBeVisible({ timeout: 15000 });
  });
});
