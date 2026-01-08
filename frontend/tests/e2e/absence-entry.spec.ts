/**
 * E2E Test: Absence Entry Workflow (T098)
 *
 * Test Scenario: Navigate to date → Switch to Absence tab → Record absence → Verify calendar
 *
 * Success Criteria:
 * - User can switch between Work Hours and Absence tabs
 * - User can record various types of absences (paid leave, sick leave, etc.)
 * - Absences are saved and reflected in calendar view with visual indicators
 * - Total hours validation includes both work and absence hours (≤24h)
 * - User can update existing absence records
 * - Calendar shows absence hours with appropriate styling (blue background, 🏖️ icon)
 */

import { expect, test } from "@playwright/test";

test.describe("Absence Entry Workflow", () => {
  const baseURL = "http://localhost:3000";
  const memberId = "00000000-0000-0000-0000-000000000001";
  const testDate = "2026-01-20";

  test.beforeEach(async ({ page }) => {
    // Mock calendar API - includes both work and absence hours
    await page.route("**/api/v1/worklog/calendar/**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          memberId: memberId,
          memberName: "Test Engineer",
          periodStart: "2026-01-01",
          periodEnd: "2026-01-31",
          dates: Array.from({ length: 31 }, (_, i) => ({
            date: `2026-01-${String(i + 1).padStart(2, "0")}`,
            totalWorkHours: 0,
            totalAbsenceHours: 0,
            status: "DRAFT",
            isWeekend: [6, 0].includes(new Date(2026, 0, i + 1).getDay()),
            isHoliday: false,
          })),
        }),
      });
    });

    // Mock get work log entries API
    await page.route("**/api/v1/worklog/entries?**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          entries: [],
          total: 0,
        }),
      });
    });

    // Mock get absences API
    await page.route("**/api/v1/absences?**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          absences: [],
          total: 0,
        }),
      });
    });

    // Mock create absence API
    await page.route("**/api/v1/absences", async (route) => {
      if (route.request().method() === "POST") {
        const requestBody = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify({
            id: `absence-${Date.now()}`,
            ...requestBody,
            status: "DRAFT",
            recordedBy: memberId,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            version: 1,
          }),
        });
      } else {
        await route.continue();
      }
    });

    // Mock update absence API
    await page.route("**/api/v1/absences/*", async (route) => {
      if (route.request().method() === "PATCH") {
        await route.fulfill({
          status: 204,
          headers: {
            ETag: "2",
          },
        });
      } else if (route.request().method() === "DELETE") {
        await route.fulfill({
          status: 204,
        });
      } else {
        await route.continue();
      }
    });

    // Mock create work log entry API (for mixed scenarios)
    await page.route("**/api/v1/worklog/entries", async (route) => {
      if (route.request().method() === "POST") {
        const requestBody = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify({
            id: `entry-${Date.now()}`,
            ...requestBody,
            status: "DRAFT",
            enteredBy: memberId,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            version: 1,
          }),
        });
      } else {
        await route.continue();
      }
    });
  });

  test("should record full day paid leave", async ({ page }) => {
    // Step 1: Navigate to calendar view
    await page.goto(`${baseURL}/worklog`);
    await expect(page).toHaveURL(/\/worklog$/);

    // Step 2: Click on date (20th)
    await page.click('button[aria-label*="January 20"]');

    // Step 3: Verify navigation to daily entry form
    await expect(page).toHaveURL(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");

    // Step 4: Switch to Absence tab
    await page.click('button:has-text("Absence")');

    // Step 5: Wait for absence form to be visible
    await page.waitForSelector('select[id="absenceType"]', { timeout: 5000 });

    // Step 6: Fill absence form
    await page.selectOption('select[id="absenceType"]', "PAID_LEAVE");
    await page.fill('input[id="hours"]', "8");
    await page.fill('textarea[id="reason"]', "Annual vacation");

    // Step 7: Verify form shows correct data
    await expect(page.locator('select[id="absenceType"]')).toHaveValue(
      "PAID_LEAVE",
    );
    await expect(page.locator('input[id="hours"]')).toHaveValue("8");

    // Step 8: Save absence
    await page.click('button:has-text("Save Absence")');

    // Step 9: Wait for redirect or success indication
    await page.waitForTimeout(500);

    // Success - form should be cleared or modal closed
    // In real implementation, this might navigate back to calendar
  });

  test("should record partial sick leave with work hours", async ({ page }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");

    // Step 1: Add work hours first (4 hours)
    await page.waitForSelector('input[id="project-0"]', { timeout: 5000 });
    await page.fill('input[id="project-0"]', "project-1");
    await page.fill('input[id="hours-0"]', "4");

    // Step 2: Switch to Absence tab
    await page.click('button:has-text("Absence")');

    // Step 3: Wait for absence form
    await page.waitForSelector('select[id="absenceType"]', { timeout: 5000 });

    // Step 4: Add 4 hours of sick leave
    await page.selectOption('select[id="absenceType"]', "SICK_LEAVE");
    await page.fill('input[id="hours"]', "4");
    await page.fill('textarea[id="reason"]', "Doctor appointment");

    // Step 5: Verify combined total is shown (8h)
    // This depends on UI implementation - might show in tab or summary
    await expect(
      page.locator("text=/Total Daily Hours.*8/i"),
    ).toBeVisible();

    // Step 6: Save absence
    await page.click('button:has-text("Save Absence")');

    // Success - both work and absence saved
    await page.waitForTimeout(500);
  });

  test("should validate 24-hour limit for combined work and absence", async ({
    page,
  }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");

    // Step 1: Add work hours (20 hours)
    await page.waitForSelector('input[id="project-0"]', { timeout: 5000 });
    await page.fill('input[id="project-0"]', "project-1");
    await page.fill('input[id="hours-0"]', "20");

    // Step 2: Switch to Absence tab
    await page.click('button:has-text("Absence")');
    await page.waitForSelector('select[id="absenceType"]', { timeout: 5000 });

    // Step 3: Try to add 8 hours of absence (total would be 28h)
    await page.selectOption('select[id="absenceType"]', "PAID_LEAVE");
    await page.fill('input[id="hours"]', "8");

    // Step 4: Wait for validation to trigger
    await page.waitForTimeout(200);

    // Step 5: Verify error message appears
    await expect(
      page.locator("text=/Combined hours cannot exceed 24/i"),
    ).toBeVisible();

    // Step 6: Save button should be disabled
    const saveButton = page.locator('button:has-text("Save Absence")');
    await expect(saveButton).toBeDisabled();
  });

  test("should allow combined work and absence up to 24 hours", async ({
    page,
  }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");

    // Step 1: Add work hours (16 hours)
    await page.waitForSelector('input[id="project-0"]', { timeout: 5000 });
    await page.fill('input[id="project-0"]', "project-1");
    await page.fill('input[id="hours-0"]', "16");

    // Step 2: Switch to Absence tab
    await page.click('button:has-text("Absence")');
    await page.waitForSelector('select[id="absenceType"]', { timeout: 5000 });

    // Step 3: Add 8 hours of absence (total = 24h, should be valid)
    await page.selectOption('select[id="absenceType"]', "PAID_LEAVE");
    await page.fill('input[id="hours"]', "8");
    await page.fill('textarea[id="reason"]', "Vacation half-day");

    // Step 4: Verify total shows 24h
    await expect(
      page.locator("text=/Total Daily Hours.*24/i"),
    ).toBeVisible();

    // Step 5: Save button should be enabled
    const saveButton = page.locator('button:has-text("Save Absence")');
    await expect(saveButton).not.toBeDisabled();

    // Step 6: Save should succeed
    await saveButton.click();
    await page.waitForTimeout(500);
  });

  test("should support all absence types", async ({ page }) => {
    const absenceTypes = [
      { value: "PAID_LEAVE", label: "Paid Leave" },
      { value: "SICK_LEAVE", label: "Sick Leave" },
      { value: "SPECIAL_LEAVE", label: "Special Leave" },
      { value: "OTHER", label: "Other" },
    ];

    for (const absenceType of absenceTypes) {
      // Navigate to daily entry form
      await page.goto(`${baseURL}/worklog/${testDate}`);
      await page.waitForLoadState("networkidle");

      // Switch to Absence tab
      await page.click('button:has-text("Absence")');
      await page.waitForSelector('select[id="absenceType"]', {
        timeout: 5000,
      });

      // Select absence type
      await page.selectOption('select[id="absenceType"]', absenceType.value);
      await page.fill('input[id="hours"]', "4");
      await page.fill('textarea[id="reason"]', `Testing ${absenceType.label}`);

      // Verify selection
      await expect(page.locator('select[id="absenceType"]')).toHaveValue(
        absenceType.value,
      );

      // Save should be enabled
      const saveButton = page.locator('button:has-text("Save Absence")');
      await expect(saveButton).not.toBeDisabled();
    }
  });

  test("should support quarter-hour increments for absences", async ({
    page,
  }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");

    // Switch to Absence tab
    await page.click('button:has-text("Absence")');
    await page.waitForSelector('select[id="absenceType"]', { timeout: 5000 });

    // Test various quarter-hour increments
    const validIncrements = ["0.25", "0.5", "0.75", "1", "4.25", "7.5"];

    for (const hours of validIncrements) {
      await page.selectOption('select[id="absenceType"]', "PAID_LEAVE");
      await page.fill('input[id="hours"]', hours);

      // Verify input accepted
      await expect(page.locator('input[id="hours"]')).toHaveValue(hours);

      // No error should be shown
      await expect(
        page.locator("text=/Invalid hours/i, text=/must be.*0.25/i"),
      ).not.toBeVisible();
    }
  });

  test("should validate absence reason length (max 500 characters)", async ({
    page,
  }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");

    // Switch to Absence tab
    await page.click('button:has-text("Absence")');
    await page.waitForSelector('select[id="absenceType"]', { timeout: 5000 });

    // Fill form with long reason (501 characters)
    await page.selectOption('select[id="absenceType"]', "PAID_LEAVE");
    await page.fill('input[id="hours"]', "8");

    const longReason = "x".repeat(501);
    await page.fill('textarea[id="reason"]', longReason);

    // Wait for validation
    await page.waitForTimeout(200);

    // Should show validation error
    await expect(
      page.locator("text=/Reason cannot exceed 500 characters/i"),
    ).toBeVisible();

    // Save button should be disabled
    const saveButton = page.locator('button:has-text("Save Absence")');
    await expect(saveButton).toBeDisabled();
  });

  test("should allow exactly 500 character reason", async ({ page }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");

    // Switch to Absence tab
    await page.click('button:has-text("Absence")');
    await page.waitForSelector('select[id="absenceType"]', { timeout: 5000 });

    // Fill form with exactly 500 character reason
    await page.selectOption('select[id="absenceType"]', "PAID_LEAVE");
    await page.fill('input[id="hours"]', "8");

    const maxReason = "x".repeat(500);
    await page.fill('textarea[id="reason"]', maxReason);

    // Wait for validation
    await page.waitForTimeout(200);

    // Should NOT show validation error
    await expect(
      page.locator("text=/Reason cannot exceed 500 characters/i"),
    ).not.toBeVisible();

    // Save button should be enabled
    const saveButton = page.locator('button:has-text("Save Absence")');
    await expect(saveButton).not.toBeDisabled();
  });

  test("should allow optional reason (null/empty)", async ({ page }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");

    // Switch to Absence tab
    await page.click('button:has-text("Absence")');
    await page.waitForSelector('select[id="absenceType"]', { timeout: 5000 });

    // Fill form WITHOUT reason
    await page.selectOption('select[id="absenceType"]', "SICK_LEAVE");
    await page.fill('input[id="hours"]', "4");
    // Leave reason empty

    // Save button should still be enabled
    const saveButton = page.locator('button:has-text("Save Absence")');
    await expect(saveButton).not.toBeDisabled();

    // Save should succeed
    await saveButton.click();
    await page.waitForTimeout(500);
  });

  test("should update existing absence hours", async ({ page }) => {
    // Mock existing absence
    await page.route("**/api/v1/absences?**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          absences: [
            {
              id: "absence-123",
              memberId: memberId,
              date: testDate,
              hours: 8,
              absenceType: "PAID_LEAVE",
              reason: "Original reason",
              status: "DRAFT",
              version: 1,
            },
          ],
          total: 1,
        }),
      });
    });

    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");

    // Switch to Absence tab
    await page.click('button:has-text("Absence")');
    await page.waitForSelector('select[id="absenceType"]', { timeout: 5000 });

    // Existing absence should be pre-filled
    await expect(page.locator('select[id="absenceType"]')).toHaveValue(
      "PAID_LEAVE",
    );
    await expect(page.locator('input[id="hours"]')).toHaveValue("8");
    await expect(page.locator('textarea[id="reason"]')).toHaveValue(
      "Original reason",
    );

    // Update hours
    await page.fill('input[id="hours"]', "4");

    // Save updated absence
    await page.click('button:has-text("Save Absence")');
    await page.waitForTimeout(500);

    // Success
  });

  test("should show absence visual indicators in calendar", async ({
    page,
  }) => {
    // Mock calendar with absence data
    await page.route("**/api/v1/worklog/calendar/**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          memberId: memberId,
          memberName: "Test Engineer",
          periodStart: "2026-01-01",
          periodEnd: "2026-01-31",
          dates: Array.from({ length: 31 }, (_, i) => {
            const date = `2026-01-${String(i + 1).padStart(2, "0")}`;
            return {
              date: date,
              totalWorkHours: date === testDate ? 0 : 0,
              totalAbsenceHours: date === testDate ? 8 : 0,
              status: "DRAFT",
              isWeekend: [6, 0].includes(new Date(2026, 0, i + 1).getDay()),
              isHoliday: false,
            };
          }),
        }),
      });
    });

    // Navigate to calendar
    await page.goto(`${baseURL}/worklog`);
    await expect(page).toHaveURL(/\/worklog$/);
    await page.waitForLoadState("networkidle");

    // Find the date button (20th)
    const dateButton = page.locator('button[aria-label*="January 20"]');

    // Verify absence indicator is visible (🏖️ emoji or absence hours)
    await expect(dateButton.locator("text=/🏖️|8.*h|Absence/i")).toBeVisible();

    // Date might have special styling (blue background, etc.)
    // This depends on actual implementation
  });

  test("should show combined work and absence in monthly summary", async ({
    page,
  }) => {
    // Mock calendar with mixed data
    await page.route("**/api/v1/worklog/calendar/**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          memberId: memberId,
          memberName: "Test Engineer",
          periodStart: "2026-01-01",
          periodEnd: "2026-01-31",
          dates: [
            {
              date: "2026-01-15",
              totalWorkHours: 8,
              totalAbsenceHours: 0,
              status: "DRAFT",
            },
            {
              date: "2026-01-20",
              totalWorkHours: 0,
              totalAbsenceHours: 8,
              status: "DRAFT",
            },
            {
              date: "2026-01-25",
              totalWorkHours: 4,
              totalAbsenceHours: 4,
              status: "DRAFT",
            },
            // Fill rest with zeros
            ...Array.from({ length: 28 }, (_, i) => ({
              date: `2026-01-${String(i + 1).padStart(2, "0")}`,
              totalWorkHours: 0,
              totalAbsenceHours: 0,
              status: "DRAFT",
              isWeekend: false,
              isHoliday: false,
            })),
          ],
        }),
      });
    });

    // Navigate to calendar
    await page.goto(`${baseURL}/worklog`);
    await page.waitForLoadState("networkidle");

    // Monthly summary should show:
    // - Total work hours: 12h (8 + 4)
    // - Total absence hours: 12h (8 + 4)
    // - Combined hours: 24h

    await expect(
      page.locator("text=/Work Hours:.*12/i"),
    ).toBeVisible();
    await expect(
      page.locator("text=/Absence Hours:.*12/i"),
    ).toBeVisible();
    await expect(
      page.locator("text=/Total Daily Hours:.*24/i"),
    ).toBeVisible();
  });
});
