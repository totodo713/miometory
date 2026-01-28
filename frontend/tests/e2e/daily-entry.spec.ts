/**
 * E2E Test: Daily Entry Workflow (T063)
 *
 * Test Scenario: Login → Select Date → Enter Time → Save → Verify Calendar
 *
 * Success Criteria:
 * - User can navigate from calendar to daily entry form
 * - User can enter time for multiple projects
 * - Entries are saved and reflected in calendar view
 * - Total hours are correctly displayed on calendar
 * - Data persists after page reload
 */

import { expect, test } from "@playwright/test";

test.describe("Daily Entry Workflow", () => {
  const baseURL = "http://localhost:3000";
  const memberId = "00000000-0000-0000-0000-000000000001";
  const testDate = "2026-01-15";

  test.beforeEach(async ({ page }) => {
    // Mock the API endpoints to avoid needing a real backend
    // This allows E2E tests to run independently

    // Mock calendar API
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

    // Mock get entries API (initially empty)
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

    // Mock create entry API
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

    // Mock update entry API (for auto-save)
    await page.route("**/api/v1/worklog/entries/*", async (route) => {
      if (route.request().method() === "PATCH") {
        await route.fulfill({
          status: 204,
        });
      } else if (route.request().method() === "DELETE") {
        await route.fulfill({
          status: 204,
        });
      } else {
        await route.continue();
      }
    });
  });

  test("should complete full daily entry workflow", async ({ page }) => {
    // Step 1: Navigate to calendar view
    await page.goto(`${baseURL}/worklog`, { waitUntil: "networkidle" });
    await expect(page).toHaveURL(/\/worklog$/);

    // Verify calendar is loaded
    await expect(page.locator("h1")).toContainText("Work Log");

    // Wait for calendar to be fully rendered with data
    await expect(
      page.locator('button[aria-label*="January 15"]'),
    ).toBeVisible();

    // Step 2: Click on a specific date (15th)
    // The calendar should have clickable date cells
    await page.locator('button[aria-label*="January 15"]').click();

    // Step 3: Verify navigation to daily entry form
    await expect(page).toHaveURL(`${baseURL}/worklog/${testDate}`);

    // Wait for form to load
    await page.waitForLoadState("networkidle");
    await page.waitForSelector('input[id="project-0"]', { timeout: 10000 });

    // Verify form is loaded
    await expect(page.locator("h2, h3")).toContainText(
      /Work Log Entry|Daily Entry|2026-01-15/i,
    );

    // Step 4: Enter time for first project
    // Add first project row (project is text input, not select)
    const firstProjectInput = page.locator('input[id="project-0"]');
    const firstHoursInput = page.locator('input[id="hours-0"]');

    await firstProjectInput.fill("project-1");
    await firstHoursInput.fill("5");

    // Step 5: Add second project
    // Click "Add Project" button
    await page.click('button:has-text("Add Project")');

    // Fill second project row
    const secondProjectInput = page.locator('input[id="project-1"]');
    const secondHoursInput = page.locator('input[id="hours-1"]');

    await secondProjectInput.fill("project-2");
    await secondHoursInput.fill("3");

    // Step 6: Verify total hours calculation
    await expect(page.locator("text=/Total.*8.00h/")).toBeVisible();

    // Step 7: Save the entry
    await page.click('button:has-text("Save")');

    // Step 8: Verify redirect back to calendar
    await expect(page).toHaveURL(`${baseURL}/worklog`);

    // Step 9: Verify calendar is displayed again
    await expect(page.locator("h1")).toContainText("Work Log");

    // Note: Verifying updated hours in calendar would require mocking
    // the calendar API response to include the newly saved data
  });

  test("should validate 24-hour maximum per day", async ({ page }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);
    await page.waitForLoadState("networkidle");
    await page.waitForSelector('input[id="hours-0"]', { timeout: 10000 });

    // Fill in project first
    const projectInput = page.locator('input[id="project-0"]');
    await projectInput.fill("project-1");

    // Enter hours exceeding 24
    const hoursInput = page.locator('input[id="hours-0"]');
    await hoursInput.fill("25");

    // Wait for validation to trigger
    await page.waitForTimeout(100);

    // Verify validation error appears (global error message)
    await expect(
      page.locator("text=/Combined hours cannot exceed 24 hours/i"),
    ).toBeVisible();

    // Save button should be disabled
    const saveButton = page.locator('button:has-text("Save")');
    await expect(saveButton).toBeDisabled();
  });

  test("should support 15-minute (0.25h) granularity", async ({ page }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);

    // Enter time with 15-minute increments
    const projectInput = page.locator('input[id="project-0"]');
    const hoursInput = page.locator('input[id="hours-0"]');

    await projectInput.fill("project-1");
    await hoursInput.fill("2.25"); // 2 hours 15 minutes

    // Add another project with 0.5h
    await page.click('button:has-text("Add Project")');
    const secondProjectInput = page.locator('input[id="project-1"]');
    const secondHoursInput = page.locator('input[id="hours-1"]');

    await secondProjectInput.fill("project-2");
    await secondHoursInput.fill("0.75"); // 45 minutes

    // Verify total: 2.25 + 0.75 = 3.0
    await expect(page.locator("text=/Total.*3.00h/i")).toBeVisible();

    // Save should work
    const saveButton = page.locator('button:has-text("Save")');
    await expect(saveButton).not.toBeDisabled();
  });

  test("should require project selection before saving", async ({ page }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);

    // Enter hours without selecting project
    const hoursInput = page.locator('input[id="hours-0"]');
    await hoursInput.fill("8");

    // Try to save
    const saveButton = page.locator('button:has-text("Save")');
    await saveButton.click();

    // Should show validation error
    await expect(page.locator("text=/Project is required/i")).toBeVisible();
  });

  test("should allow adding and removing project rows", async ({ page }) => {
    // Navigate to daily entry form
    await page.goto(`${baseURL}/worklog/${testDate}`);

    // Initially should have 1 row
    const projectRows = page.locator('input[id^="project-"]');
    await expect(projectRows).toHaveCount(1);

    // Add 2 more rows
    await page.click('button:has-text("Add Project")');
    await page.click('button:has-text("Add Project")');

    // Should now have 3 rows
    await expect(projectRows).toHaveCount(3);

    // Fill all rows
    await page.locator('input[id="project-0"]').fill("project-1");
    await page.locator('input[id="hours-0"]').fill("3");

    await page.locator('input[id="project-1"]').fill("project-2");
    await page.locator('input[id="hours-1"]').fill("2");

    await page.locator('input[id="project-2"]').fill("project-3");
    await page.locator('input[id="hours-2"]').fill("1");

    // Remove middle row
    const removeButtons = page.locator('button:has-text("Remove")');
    await removeButtons.nth(1).click();

    // Should now have 2 rows
    await expect(projectRows).toHaveCount(2);

    // Verify total: 3 + 1 = 4
    await expect(page.locator("text=/Total.*4.00h/i")).toBeVisible();
  });
});
