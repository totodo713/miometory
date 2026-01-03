/**
 * E2E tests for multi-project time allocation (US2)
 * Task: T074
 *
 * Tests the ability to enter work hours across multiple projects on the same day
 * and verify the 24-hour daily limit validation.
 */

import { test, expect } from "@playwright/test";

test.describe("Multi-project time allocation", () => {
  test.beforeEach(async ({ page }) => {
    // Navigate to the work log page
    await page.goto("http://localhost:3000/worklog");
    
    // Wait for calendar to load
    await expect(page.getByRole("heading", { name: "Work Log" })).toBeVisible();
  });

  test("should allow adding multiple project entries for the same day", async ({ page }) => {
    // Click on a date in the calendar
    const today = new Date();
    const dateButton = page.getByRole("button", { name: new RegExp(today.getDate().toString()) });
    await dateButton.first().click();

    // Wait for the daily entry form to appear
    await expect(page.getByRole("heading", { name: /Daily Entry/ })).toBeVisible();

    // Add first project entry (4.5 hours)
    await page.getByLabel("Project").selectOption({ index: 1 }); // Select first project
    await page.getByLabel("Hours").fill("4.5");
    await page.getByLabel("Comment").fill("Project A work");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    // Verify first entry appears and total updates
    await expect(page.getByText("4.5h")).toBeVisible();
    await expect(page.getByText(/Total: 4.50h/i)).toBeVisible();

    // Add second project entry (2.5 hours)
    await page.getByLabel("Project").selectOption({ index: 2 }); // Select second project
    await page.getByLabel("Hours").fill("2.5");
    await page.getByLabel("Comment").fill("Project B work");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    // Verify second entry appears and total updates
    await expect(page.getByText("2.5h")).toBeVisible();
    await expect(page.getByText(/Total: 7.00h/i)).toBeVisible();

    // Add third project entry (1.0 hour)
    await page.getByLabel("Project").selectOption({ index: 3 }); // Select third project
    await page.getByLabel("Hours").fill("1.0");
    await page.getByLabel("Comment").fill("Internal tasks");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    // Verify third entry appears and total updates
    await expect(page.getByText("1.0h")).toBeVisible();
    await expect(page.getByText(/Total: 8.00h/i)).toBeVisible();

    // Verify total hours shows 8h in green (valid)
    const totalDisplay = page.getByText(/Total: 8.00h/i);
    await expect(totalDisplay).toHaveClass(/text-green/);

    // Save all entries
    await page.getByRole("button", { name: /Save All/i }).click();

    // Wait for success message
    await expect(page.getByText(/saved successfully/i)).toBeVisible();

    // Return to calendar
    await page.getByRole("button", { name: /Back to Calendar/i }).click();

    // Verify the date cell shows 8h total
    await expect(dateButton.first()).toContainText("8");
  });

  test("should prevent adding entries that exceed 24-hour daily limit", async ({ page }) => {
    // Click on a different date
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const dateButton = page.getByRole("button", { name: new RegExp(yesterday.getDate().toString()) });
    await dateButton.first().click();

    // Wait for the daily entry form
    await expect(page.getByRole("heading", { name: /Daily Entry/ })).toBeVisible();

    // Add first entry: 12 hours
    await page.getByLabel("Project").selectOption({ index: 1 });
    await page.getByLabel("Hours").fill("12");
    await page.getByLabel("Comment").fill("Long project day");
    await page.getByRole("button", { name: /Add Entry/i }).click();
    await expect(page.getByText(/Total: 12.00h/i)).toBeVisible();

    // Add second entry: 10 hours
    await page.getByLabel("Project").selectOption({ index: 2 });
    await page.getByLabel("Hours").fill("10");
    await page.getByLabel("Comment").fill("Another project");
    await page.getByRole("button", { name: /Add Entry/i }).click();
    await expect(page.getByText(/Total: 22.00h/i)).toBeVisible();

    // Add third entry: 3 hours (would exceed 24h limit)
    await page.getByLabel("Project").selectOption({ index: 3 });
    await page.getByLabel("Hours").fill("3");
    await page.getByLabel("Comment").fill("Too much work");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    // Verify total shows 25h in red (invalid)
    const totalDisplay = page.getByText(/Total: 25.00h/i);
    await expect(totalDisplay).toBeVisible();
    await expect(totalDisplay).toHaveClass(/text-red/);

    // Verify warning message appears
    await expect(page.getByText(/exceeds 24-hour daily limit/i)).toBeVisible();

    // Verify Save button is disabled
    const saveButton = page.getByRole("button", { name: /Save All/i });
    await expect(saveButton).toBeDisabled();

    // Remove the third entry
    const removeButtons = page.getByRole("button", { name: /Remove/i });
    await removeButtons.last().click();

    // Verify total is now 22h and valid
    await expect(page.getByText(/Total: 22.00h/i)).toBeVisible();
    await expect(totalDisplay).toHaveClass(/text-green/);

    // Verify Save button is now enabled
    await expect(saveButton).toBeEnabled();
  });

  test("should show 24-hour limit when exactly at limit", async ({ page }) => {
    // Navigate to a specific date
    const twoDaysAgo = new Date();
    twoDaysAgo.setDate(twoDaysAgo.getDate() - 2);
    const dateButton = page.getByRole("button", { name: new RegExp(twoDaysAgo.getDate().toString()) });
    await dateButton.first().click();

    await expect(page.getByRole("heading", { name: /Daily Entry/ })).toBeVisible();

    // Add entries totaling exactly 24 hours
    const projects = [
      { hours: "8", comment: "Morning shift" },
      { hours: "8", comment: "Afternoon shift" },
      { hours: "8", comment: "Evening shift" },
    ];

    for (let i = 0; i < projects.length; i++) {
      await page.getByLabel("Project").selectOption({ index: i + 1 });
      await page.getByLabel("Hours").fill(projects[i].hours);
      await page.getByLabel("Comment").fill(projects[i].comment);
      await page.getByRole("button", { name: /Add Entry/i }).click();
    }

    // Verify total is exactly 24h
    await expect(page.getByText(/Total: 24.00h/i)).toBeVisible();

    // Verify Save button is enabled (24h is valid)
    await expect(page.getByRole("button", { name: /Save All/i })).toBeEnabled();

    // Verify no warning message
    await expect(page.getByText(/exceeds.*limit/i)).not.toBeVisible();

    // Try to add one more quarter hour
    await page.getByLabel("Project").selectOption({ index: 1 });
    await page.getByLabel("Hours").fill("0.25");
    await page.getByLabel("Comment").fill("Extra work");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    // Verify total exceeds and shows warning
    await expect(page.getByText(/Total: 24.25h/i)).toBeVisible();
    await expect(page.getByText(/exceeds 24-hour daily limit/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Save All/i })).toBeDisabled();
  });

  test("should update total when editing existing entry hours", async ({ page }) => {
    // Navigate to a date with existing entries
    const today = new Date();
    const dateButton = page.getByRole("button", { name: new RegExp(today.getDate().toString()) });
    await dateButton.first().click();

    await expect(page.getByRole("heading", { name: /Daily Entry/ })).toBeVisible();

    // Add two entries
    await page.getByLabel("Project").selectOption({ index: 1 });
    await page.getByLabel("Hours").fill("5");
    await page.getByLabel("Comment").fill("Initial work");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    await page.getByLabel("Project").selectOption({ index: 2 });
    await page.getByLabel("Hours").fill("3");
    await page.getByLabel("Comment").fill("More work");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    // Verify total is 8h
    await expect(page.getByText(/Total: 8.00h/i)).toBeVisible();

    // Edit first entry hours from 5 to 20
    const firstEntryHours = page.getByLabel("Hours").first();
    await firstEntryHours.clear();
    await firstEntryHours.fill("20");

    // Verify total updates to 23h
    await expect(page.getByText(/Total: 23.00h/i)).toBeVisible();

    // Edit first entry hours from 20 to 22
    await firstEntryHours.clear();
    await firstEntryHours.fill("22");

    // Verify total shows 25h and is invalid
    await expect(page.getByText(/Total: 25.00h/i)).toBeVisible();
    await expect(page.getByText(/exceeds 24-hour daily limit/i)).toBeVisible();
  });

  test("should display project count in running total", async ({ page }) => {
    const today = new Date();
    const dateButton = page.getByRole("button", { name: new RegExp(today.getDate().toString()) });
    await dateButton.first().click();

    await expect(page.getByRole("heading", { name: /Daily Entry/ })).toBeVisible();

    // Add first project
    await page.getByLabel("Project").selectOption({ index: 1 });
    await page.getByLabel("Hours").fill("4");
    await page.getByLabel("Comment").fill("Project 1");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    // Verify project count shows 1
    await expect(page.getByText(/1 project/i)).toBeVisible();

    // Add second project
    await page.getByLabel("Project").selectOption({ index: 2 });
    await page.getByLabel("Hours").fill("3");
    await page.getByLabel("Comment").fill("Project 2");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    // Verify project count shows 2
    await expect(page.getByText(/2 projects/i)).toBeVisible();

    // Add third project
    await page.getByLabel("Project").selectOption({ index: 3 });
    await page.getByLabel("Hours").fill("2");
    await page.getByLabel("Comment").fill("Project 3");
    await page.getByRole("button", { name: /Add Entry/i }).click();

    // Verify project count shows 3
    await expect(page.getByText(/3 projects/i)).toBeVisible();
  });
});
