/**
 * E2E Test: Monthly Timesheet Page
 *
 * Test Scenario: Navigate to timesheet → verify heading → select project → verify data
 *
 * Success Criteria:
 * - Timesheet page title is displayed
 * - After selecting a project, timesheet data (hours) is visible
 * - Period toggle switches between calendar and fiscal
 */

import type { TimesheetResponse } from "../../app/types/timesheet";
import { expect, mockProjectsApi, test } from "./fixtures/auth";

const memberId = "00000000-0000-0000-0000-000000000001";

const mockTimesheetData: TimesheetResponse = {
  memberId,
  memberName: "Test Engineer",
  projectId: "project-1",
  projectName: "Project Alpha",
  periodType: "calendar",
  periodStart: "2026-03-01",
  periodEnd: "2026-03-31",
  rows: [
    {
      date: "2026-03-02",
      dayOfWeek: "Mon",
      isWeekend: false,
      isHoliday: false,
      holidayName: null,
      startTime: "09:00",
      endTime: "18:00",
      workingHours: 8.0,
      remarks: "meeting",
      defaultStartTime: "09:00",
      defaultEndTime: "18:00",
      hasAttendanceRecord: true,
      attendanceId: "att-1",
      attendanceVersion: 0,
    },
    {
      date: "2026-03-07",
      dayOfWeek: "Sat",
      isWeekend: true,
      isHoliday: false,
      holidayName: null,
      startTime: null,
      endTime: null,
      workingHours: 0,
      remarks: null,
      defaultStartTime: null,
      defaultEndTime: null,
      hasAttendanceRecord: false,
      attendanceId: null,
      attendanceVersion: null,
    },
  ],
  summary: {
    totalWorkingHours: 8.0,
    totalWorkingDays: 1,
    totalBusinessDays: 22,
  },
};

test.describe("Monthly Timesheet Page", () => {
  test.beforeEach(async ({ page }) => {
    // Mock timesheet API
    await page.route("**/api/v1/worklog/timesheet/**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockTimesheetData),
      });
    });

    // Mock assigned projects API (required by ProjectSelector)
    await mockProjectsApi(page);
  });

  test("should display timesheet page title", async ({ page }) => {
    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });

    // "Monthly Timesheet" is the en.json timesheet.title value
    await expect(page.getByRole("heading", { name: /Monthly Timesheet/i })).toBeVisible({
      timeout: 10000,
    });
  });

  test("should show data after selecting project", async ({ page }) => {
    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });

    // Wait for heading to confirm page loaded
    await expect(page.getByRole("heading", { name: /Monthly Timesheet/i })).toBeVisible({
      timeout: 10000,
    });

    // Select a project using the combobox
    const combobox = page.getByRole("combobox");
    await combobox.fill("Project Alpha");
    await page
      .getByRole("option", { name: /Project Alpha/i })
      .first()
      .click();

    // After selecting project, the mocked API returns data with 8.0 total hours
    // Verify summary shows "Total Hours" label and the value
    await expect(page.getByText("Total Hours")).toBeVisible({ timeout: 10000 });
    await expect(page.getByText("8h")).toBeVisible();
  });

  test("should toggle between calendar and fiscal periods", async ({ page }) => {
    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });

    // Wait for page to load
    await expect(page.getByRole("heading", { name: /Monthly Timesheet/i })).toBeVisible({
      timeout: 10000,
    });

    // "Calendar" radio should be checked by default
    const calendarRadio = page.getByRole("radio", { name: "Calendar" });
    const fiscalRadio = page.getByRole("radio", { name: "Fiscal" });

    await expect(calendarRadio).toBeChecked();
    await expect(fiscalRadio).not.toBeChecked();

    // Click the Fiscal label (radio is sr-only, so we click its parent label)
    await page.getByText("Fiscal", { exact: true }).click();

    // Now fiscal should be checked
    await expect(fiscalRadio).toBeChecked();
    await expect(calendarRadio).not.toBeChecked();
  });
});
