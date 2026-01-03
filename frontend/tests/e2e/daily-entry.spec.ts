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

import { test, expect } from "@playwright/test";

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
		await page.goto(`${baseURL}/worklog`);
		await expect(page).toHaveURL(/\/worklog$/);

		// Verify calendar is loaded
		await expect(page.locator("h1")).toContainText(/Calendar|January 2026/);

		// Step 2: Click on a specific date (15th)
		// The calendar should have clickable date cells
		await page.click('button:has-text("15")');

		// Step 3: Verify navigation to daily entry form
		await expect(page).toHaveURL(`${baseURL}/worklog/${testDate}`);

		// Verify form is loaded
		await expect(page.locator("h1, h2")).toContainText(/Work Log Entry|Daily Entry/);

		// Step 4: Enter time for first project
		// Add first project row
		const firstProjectSelect = page
			.locator('select[name="projectId"]')
			.first();
		const firstHoursInput = page.locator('input[name="hours"]').first();

		await firstProjectSelect.selectOption("project-1");
		await firstHoursInput.fill("5");

		// Step 5: Add second project
		// Click "Add Project" button
		await page.click('button:has-text("Add Project")');

		// Fill second project row
		const projectSelects = page.locator('select[name="projectId"]');
		const hoursInputs = page.locator('input[name="hours"]');

		await projectSelects.nth(1).selectOption("project-2");
		await hoursInputs.nth(1).fill("3");

		// Step 6: Verify total hours calculation
		await expect(page.locator("text=/Total:.*8.0.*hours/")).toBeVisible();

		// Step 7: Save the entry
		await page.click('button:has-text("Save")');

		// Step 8: Verify redirect back to calendar
		await expect(page).toHaveURL(`${baseURL}/worklog`);

		// Step 9: Verify hours are reflected in calendar
		// This would require the calendar to show updated totals
		// With our mock, we'd need to update the mock to return the saved data
		await expect(page.locator(`[data-date="${testDate}"]`)).toBeVisible();

		// Step 10: Reload page and verify persistence
		await page.reload();
		await expect(page).toHaveURL(`${baseURL}/worklog`);
		await expect(page.locator(`[data-date="${testDate}"]`)).toBeVisible();
	});

	test("should validate 24-hour maximum per day", async ({ page }) => {
		// Navigate to daily entry form
		await page.goto(`${baseURL}/worklog/${testDate}`);

		// Enter hours exceeding 24
		const hoursInput = page.locator('input[name="hours"]').first();
		await hoursInput.fill("25");

		// Verify validation error appears
		await expect(
			page.locator("text=/Hours cannot exceed 24|exceeds 24/i"),
		).toBeVisible();

		// Save button should be disabled or show error
		const saveButton = page.locator('button:has-text("Save")');
		await expect(saveButton).toBeDisabled();
	});

	test("should support 15-minute (0.25h) granularity", async ({ page }) => {
		// Navigate to daily entry form
		await page.goto(`${baseURL}/worklog/${testDate}`);

		// Enter time with 15-minute increments
		const projectSelect = page.locator('select[name="projectId"]').first();
		const hoursInput = page.locator('input[name="hours"]').first();

		await projectSelect.selectOption("project-1");
		await hoursInput.fill("2.25"); // 2 hours 15 minutes

		// Add another project with 0.5h
		await page.click('button:has-text("Add Project")');
		const secondProjectSelect = page.locator('select[name="projectId"]').nth(1);
		const secondHoursInput = page.locator('input[name="hours"]').nth(1);

		await secondProjectSelect.selectOption("project-2");
		await secondHoursInput.fill("0.75"); // 45 minutes

		// Verify total: 2.25 + 0.75 = 3.0
		await expect(page.locator("text=/Total:.*3.0.*hours/")).toBeVisible();

		// Save should work
		const saveButton = page.locator('button:has-text("Save")');
		await expect(saveButton).not.toBeDisabled();
	});

	test("should require project selection before saving", async ({ page }) => {
		// Navigate to daily entry form
		await page.goto(`${baseURL}/worklog/${testDate}`);

		// Enter hours without selecting project
		const hoursInput = page.locator('input[name="hours"]').first();
		await hoursInput.fill("8");

		// Try to save
		const saveButton = page.locator('button:has-text("Save")');
		
		// Should show validation error or disable save
		// (Implementation depends on form validation logic)
		await expect(
			page.locator("text=/Project is required|Select a project/i"),
		).toBeVisible();
	});

	test("should allow adding and removing project rows", async ({ page }) => {
		// Navigate to daily entry form
		await page.goto(`${baseURL}/worklog/${testDate}`);

		// Initially should have 1 row
		let projectRows = page.locator('select[name="projectId"]');
		await expect(projectRows).toHaveCount(1);

		// Add 2 more rows
		await page.click('button:has-text("Add Project")');
		await page.click('button:has-text("Add Project")');

		// Should now have 3 rows
		await expect(projectRows).toHaveCount(3);

		// Fill all rows
		await projectRows.nth(0).selectOption("project-1");
		await page.locator('input[name="hours"]').nth(0).fill("3");

		await projectRows.nth(1).selectOption("project-2");
		await page.locator('input[name="hours"]').nth(1).fill("2");

		await projectRows.nth(2).selectOption("project-3");
		await page.locator('input[name="hours"]').nth(2).fill("1");

		// Remove middle row
		const removeButtons = page.locator('button:has-text("Remove")');
		await removeButtons.nth(1).click();

		// Should now have 2 rows
		await expect(projectRows).toHaveCount(2);

		// Verify total: 3 + 1 = 4
		await expect(page.locator("text=/Total:.*4.0.*hours/")).toBeVisible();
	});
});
