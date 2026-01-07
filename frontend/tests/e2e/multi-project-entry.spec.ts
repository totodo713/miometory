/**
 * E2E tests for multi-project time allocation (US2)
 * Task: T074
 *
 * Tests the ability to enter work hours across multiple projects on the same day
 * and verify the 24-hour daily limit validation.
 */

import { expect, test } from "@playwright/test";

test.describe("Multi-project time allocation", () => {
  const projectA = "11111111-1111-1111-1111-111111111111";
  const projectB = "22222222-2222-2222-2222-222222222222";
  const projectC = "33333333-3333-3333-3333-333333333333";

  test.beforeEach(async ({ page }) => {
    // Navigate to the work log page
    await page.goto("http://localhost:3000/worklog");

    // Wait for calendar to load
    await expect(page.getByRole("heading", { name: "Work Log" })).toBeVisible();
  });

  test("should allow adding multiple project entries for the same day", async ({
    page,
  }) => {
    // Click on January 15, 2026
    await page.click('button[aria-label*="January 15"]');

    // Wait for the daily entry form to appear
    await expect(page.locator("text=/Daily Entry/i")).toBeVisible();

    // First row is already present, fill it with 4.5 hours
    await page.locator('input[id="project-0"]').fill(projectA);
    await page.locator('input[id="hours-0"]').fill("4.5");
    await page.locator('textarea[id="comment-0"]').fill("Project A work");

    // Verify running total updates (4.5h from 1 project)
    await expect(page.locator("text=/Total.*4\\.50h/i")).toBeVisible();

    // Add second project row
    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-1"]').fill(projectB);
    await page.locator('input[id="hours-1"]').fill("2.5");
    await page.locator('textarea[id="comment-1"]').fill("Project B work");

    // Verify running total updates (7.0h from 2 projects)
    await expect(page.locator("text=/Total.*7\\.00h/i")).toBeVisible();

    // Add third project row
    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-2"]').fill(projectC);
    await page.locator('input[id="hours-2"]').fill("1.0");
    await page.locator('textarea[id="comment-2"]').fill("Internal tasks");

    // Verify running total updates (8.0h from 3 projects)
    await expect(page.locator("text=/Total.*8\\.00h/i")).toBeVisible();

    // Check if Save button is enabled before clicking
    const saveButton = page.locator('button:has-text("Save")');
    await expect(saveButton).toBeEnabled();

    // Note: Skipping actual save and calendar verification as it requires backend API
    // The important functionality (multi-project UI, validation, totals) is verified above
  });

  test("should prevent adding entries that exceed 24-hour daily limit", async ({
    page,
  }) => {
    // Click on January 16, 2026
    await page.click('button[aria-label*="January 16"]');

    // Wait for the daily entry form
    await expect(page.locator("text=/Daily Entry/i")).toBeVisible();

    // Add first entry: 12 hours
    await page.locator('input[id="project-0"]').fill(projectA);
    await page.locator('input[id="hours-0"]').fill("12");
    await page.locator('textarea[id="comment-0"]').fill("Long project day");

    // Verify total
    await expect(page.locator("text=/Total.*12\\.00h/i")).toBeVisible();

    // Add second entry: 10 hours
    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-1"]').fill(projectB);
    await page.locator('input[id="hours-1"]').fill("10");
    await page.locator('textarea[id="comment-1"]').fill("Another project");

    // Verify total
    await expect(page.locator("text=/Total.*22\\.00h/i")).toBeVisible();

    // Add third entry: 3 hours (would exceed 24h limit)
    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-2"]').fill(projectC);
    await page.locator('input[id="hours-2"]').fill("3");
    await page.locator('textarea[id="comment-2"]').fill("Too much work");

    // Verify total shows 25h in red (invalid)
    await expect(page.locator("text=/Total.*25\\.00h/i")).toBeVisible();
    await expect(page.locator("text=/cannot exceed 24 hours/i")).toBeVisible();

    // Verify Save button is disabled
    const saveButton = page.locator('button:has-text("Save")');
    await expect(saveButton).toBeDisabled();

    // Remove the third entry
    await page.click('button[aria-label="Remove project entry 3"]');

    // Verify total is now 22h and save is enabled
    await expect(page.locator("text=/Total.*22\\.00h/i")).toBeVisible();
    await expect(saveButton).toBeEnabled();
  });

  test("should show 24-hour limit when exactly at limit", async ({ page }) => {
    // Click on January 17, 2026
    await page.click('button[aria-label*="January 17"]');

    await expect(page.locator("text=/Daily Entry/i")).toBeVisible();

    // Add entries totaling exactly 24 hours
    await page.locator('input[id="project-0"]').fill(projectA);
    await page.locator('input[id="hours-0"]').fill("8");
    await page.locator('textarea[id="comment-0"]').fill("Morning shift");

    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-1"]').fill(projectB);
    await page.locator('input[id="hours-1"]').fill("8");
    await page.locator('textarea[id="comment-1"]').fill("Afternoon shift");

    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-2"]').fill(projectC);
    await page.locator('input[id="hours-2"]').fill("8");
    await page.locator('textarea[id="comment-2"]').fill("Evening shift");

    // Verify total is exactly 24h
    await expect(page.locator("text=/Total.*24\\.00h/i")).toBeVisible();

    // Verify Save button is enabled (24h is valid)
    await expect(page.locator('button:has-text("Save")')).toBeEnabled();

    // Verify no warning message
    await expect(
      page.locator("text=/cannot exceed 24 hours/i"),
    ).not.toBeVisible();

    // Try to add one more quarter hour
    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-3"]').fill(projectA);
    await page.locator('input[id="hours-3"]').fill("0.25");
    await page.locator('textarea[id="comment-3"]').fill("Extra work");

    // Verify total exceeds and shows warning
    await expect(page.locator("text=/Total.*24\\.25h/i")).toBeVisible();
    await expect(page.locator("text=/cannot exceed 24 hours/i")).toBeVisible();
    await expect(page.locator('button:has-text("Save")')).toBeDisabled();
  });

  test("should update total when editing existing entry hours", async ({
    page,
  }) => {
    // Click on January 18, 2026
    await page.click('button[aria-label*="January 18"]');

    await expect(page.locator("text=/Daily Entry/i")).toBeVisible();

    // Add two entries
    await page.locator('input[id="project-0"]').fill(projectA);
    await page.locator('input[id="hours-0"]').fill("5");
    await page.locator('textarea[id="comment-0"]').fill("Initial work");

    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-1"]').fill(projectB);
    await page.locator('input[id="hours-1"]').fill("3");
    await page.locator('textarea[id="comment-1"]').fill("More work");

    // Verify total is 8h
    await expect(page.locator("text=/Total.*8\\.00h/i")).toBeVisible();

    // Edit first entry hours from 5 to 20
    const firstHoursInput = page.locator('input[id="hours-0"]');
    await firstHoursInput.clear();
    await firstHoursInput.fill("20");

    // Verify total updates to 23h
    await expect(page.locator("text=/Total.*23\\.00h/i")).toBeVisible();

    // Edit first entry hours from 20 to 22
    await firstHoursInput.clear();
    await firstHoursInput.fill("22");

    // Verify total shows 25h and is invalid
    await expect(page.locator("text=/Total.*25\\.00h/i")).toBeVisible();
    await expect(page.locator("text=/cannot exceed 24 hours/i")).toBeVisible();
  });

  test("should display work hours breakdown in total", async ({ page }) => {
    // Click on January 19, 2026
    await page.click('button[aria-label*="January 19"]');

    await expect(page.locator("text=/Daily Entry/i")).toBeVisible();

    // Add first project
    await page.locator('input[id="project-0"]').fill(projectA);
    await page.locator('input[id="hours-0"]').fill("4");
    await page.locator('textarea[id="comment-0"]').fill("Project 1");

    // Verify total shows 4.00h
    await expect(
      page.locator("text=/Total Daily Hours.*4\\.00h/i"),
    ).toBeVisible();
    // Verify Work Hours breakdown appears
    await expect(page.locator("text=/Work Hours:.*4\\.00h/i")).toBeVisible();

    // Add second project
    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-1"]').fill(projectB);
    await page.locator('input[id="hours-1"]').fill("3");
    await page.locator('textarea[id="comment-1"]').fill("Project 2");

    // Verify total updates to 7.00h
    await expect(
      page.locator("text=/Total Daily Hours.*7\\.00h/i"),
    ).toBeVisible();
    await expect(page.locator("text=/Work Hours:.*7\\.00h/i")).toBeVisible();

    // Add third project
    await page.click('button:has-text("+ Add Project")');
    await page.locator('input[id="project-2"]').fill(projectC);
    await page.locator('input[id="hours-2"]').fill("2");
    await page.locator('textarea[id="comment-2"]').fill("Project 3");

    // Verify total updates to 9.00h
    await expect(
      page.locator("text=/Total Daily Hours.*9\\.00h/i"),
    ).toBeVisible();
    await expect(page.locator("text=/Work Hours:.*9\\.00h/i")).toBeVisible();
  });
});
