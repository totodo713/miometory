/**
 * E2E Test: Timesheet Page
 *
 * Test Scenario: Timesheet page load, project selection, period toggle, row editing
 *
 * Success Criteria:
 * - Page loads with title and "no project" message
 * - Selecting a project shows the timesheet table with rows
 * - Calendar/Fiscal toggle changes the API call
 * - Editing a row and saving calls the attendance API
 */

import { expect, mockProjectsApi, selectProject, test } from "./fixtures/auth";

interface TimesheetRowData {
  date: string;
  dayOfWeek: string;
  isWeekend: boolean;
  isHoliday: boolean;
  holidayName: string | null;
  startTime: string | null;
  endTime: string | null;
  workingHours: number;
  remarks: string | null;
  defaultStartTime: string;
  defaultEndTime: string;
  hasAttendanceRecord: boolean;
  attendanceId: string | null;
  attendanceVersion: number;
}

function generateMarchRows(overrides: Record<string, Partial<TimesheetRowData>> = {}): TimesheetRowData[] {
  return Array.from({ length: 31 }, (_, i) => {
    const day = i + 1;
    const dateStr = `2026-03-${String(day).padStart(2, "0")}`;
    const dayOfWeek = new Date(2026, 2, day).toLocaleDateString("en-US", {
      weekday: "short",
    });
    const dayNum = new Date(2026, 2, day).getDay();
    const isWeekend = dayNum === 0 || dayNum === 6;
    const base: TimesheetRowData = {
      date: dateStr,
      dayOfWeek,
      isWeekend,
      isHoliday: false,
      holidayName: null,
      startTime: null,
      endTime: null,
      workingHours: 0,
      remarks: null,
      defaultStartTime: "09:00",
      defaultEndTime: "18:00",
      hasAttendanceRecord: false,
      attendanceId: null,
      attendanceVersion: 0,
    };
    return { ...base, ...(overrides[dateStr] || {}) };
  });
}

function buildTimesheetResponse(
  overrides: {
    periodType?: string;
    periodStart?: string;
    periodEnd?: string;
    rows?: TimesheetRowData[];
    summary?: {
      totalWorkingHours: number;
      totalWorkingDays: number;
      totalBusinessDays: number;
    };
  } = {},
) {
  return {
    memberId: "00000000-0000-0000-0000-000000000001",
    memberName: "Bob Engineer",
    projectId: "project-1",
    projectName: "Project Alpha",
    periodType: overrides.periodType ?? "calendar",
    periodStart: overrides.periodStart ?? "2026-03-01",
    periodEnd: overrides.periodEnd ?? "2026-03-31",
    canEdit: true,
    rows: overrides.rows ?? generateMarchRows(),
    summary: overrides.summary ?? {
      totalWorkingHours: 0,
      totalWorkingDays: 0,
      totalBusinessDays: 22,
    },
  };
}

test.describe("Timesheet Page", () => {
  test("should load page and show title with no-project message", async ({ page }) => {
    await mockProjectsApi(page);

    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });

    // Verify heading
    await expect(page.getByRole("heading", { name: "Monthly Timesheet" })).toBeVisible({ timeout: 10000 });

    // Verify "Please select a project" message
    await expect(page.getByText("Please select a project")).toBeVisible();
  });

  test("should show timesheet table with rows after selecting a project", async ({ page }) => {
    await mockProjectsApi(page);

    // Mock timesheet API
    await page.route("**/api/v1/worklog/timesheet/2026/3**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(buildTimesheetResponse()),
      });
    });

    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });

    // Wait for "Please select a project" to confirm page is ready
    await expect(page.getByText("Please select a project")).toBeVisible({
      timeout: 10000,
    });

    // Select a project
    await selectProject(page, 0, "Project Alpha");

    // Wait for the table to appear
    await expect(page.locator("table")).toBeVisible({ timeout: 10000 });

    // Verify rows exist — check for the first date
    await expect(page.getByText("2026-03-01")).toBeVisible();

    // Verify summary section shows business days count ("0 / 22 days")
    await expect(page.getByText("0 / 22 days")).toBeVisible();
  });

  test("should toggle between calendar and fiscal period types", async ({ page }) => {
    await mockProjectsApi(page);

    let lastRequestedPeriodType: string | null = null;

    // Mock timesheet API — capture periodType from the query string
    await page.route("**/api/v1/worklog/timesheet/2026/3**", async (route) => {
      const url = new URL(route.request().url());
      lastRequestedPeriodType = url.searchParams.get("periodType");

      const periodType = lastRequestedPeriodType ?? "calendar";
      const isFiscal = periodType === "fiscal";

      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(
          buildTimesheetResponse({
            periodType,
            periodStart: isFiscal ? "2026-02-21" : "2026-03-01",
            periodEnd: isFiscal ? "2026-03-20" : "2026-03-31",
          }),
        ),
      });
    });

    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });

    await expect(page.getByText("Please select a project")).toBeVisible({
      timeout: 10000,
    });

    await selectProject(page, 0, "Project Alpha");

    // Wait for table to render
    await expect(page.locator("table")).toBeVisible({ timeout: 10000 });

    // Verify "Calendar" button has active (blue) style
    const calendarButton = page.getByRole("button", { name: "Calendar" });
    await expect(calendarButton).toHaveClass(/bg-blue-600/);

    // Click "Fiscal" button
    const fiscalButton = page.getByRole("button", { name: "Fiscal" });
    await fiscalButton.click();

    // Wait for the table to refresh (fiscal response includes different date range)
    await expect(page.getByText("2026-02-21")).toBeVisible({
      timeout: 10000,
    });

    // Verify fiscal was the last requested period type
    expect(lastRequestedPeriodType).toBe("fiscal");

    // Verify "Fiscal" button now has active style
    await expect(fiscalButton).toHaveClass(/bg-blue-600/);
  });

  test("should edit a row and save attendance", async ({ page }) => {
    await mockProjectsApi(page);

    // Provide a row with an existing attendance record on March 2 (Monday)
    const rows = generateMarchRows({
      "2026-03-02": {
        startTime: "09:00",
        endTime: "18:00",
        workingHours: 8,
        hasAttendanceRecord: true,
        attendanceId: "att-001",
        attendanceVersion: 1,
      },
    });

    await page.route("**/api/v1/worklog/timesheet/2026/3**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(buildTimesheetResponse({ rows })),
      });
    });

    // Track save API calls
    let saveApiCalled = false;
    await page.route("**/api/v1/worklog/timesheet/attendance", async (route) => {
      if (route.request().method() === "PUT") {
        saveApiCalled = true;
        await route.fulfill({ status: 200 });
      } else {
        await route.continue();
      }
    });

    await page.goto("/worklog/timesheet", { waitUntil: "networkidle" });

    await expect(page.getByText("Please select a project")).toBeVisible({
      timeout: 10000,
    });

    await selectProject(page, 0, "Project Alpha");

    // Wait for table to render with the attendance record row
    await expect(page.locator("table")).toBeVisible({ timeout: 10000 });
    await expect(page.getByText("2026-03-02")).toBeVisible();

    // Find the start time input for March 2 and change its value
    const startTimeInput = page.getByLabel("Start 2026-03-02");
    await startTimeInput.fill("08:30");

    // The Save button should appear for this row since it is now dirty
    const saveButton = page.getByLabel("Save 2026-03-02");
    await expect(saveButton).toBeVisible({ timeout: 5000 });

    // Click save
    await saveButton.click();

    // Verify the save API was called
    expect(saveApiCalled).toBe(true);
  });
});
