import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
// Import TimesheetTable after mock setup
import { TimesheetTable } from "@/components/worklog/timesheet/TimesheetTable";
import type { TimesheetResponse } from "@/types/timesheet";
import { IntlWrapper } from "../../../helpers/intl";

const mockOnSave = vi.fn();

const mockData: TimesheetResponse = {
  memberId: "member-1",
  memberName: "Test User",
  projectId: "project-1",
  projectName: "Test Project",
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

describe("TimesheetTable", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("should render table headers", () => {
    render(
      <IntlWrapper>
        <TimesheetTable data={mockData} readOnly={false} onSave={mockOnSave} />
      </IntlWrapper>,
    );

    // Japanese translations for table headers
    expect(screen.getByText("日付")).toBeInTheDocument();
    expect(screen.getByText("曜日")).toBeInTheDocument();
    expect(screen.getByText("始業")).toBeInTheDocument();
    expect(screen.getByText("終業")).toBeInTheDocument();
    expect(screen.getByText("稼働")).toBeInTheDocument();
    expect(screen.getByText("備考")).toBeInTheDocument();
  });

  test("should render rows for each date", () => {
    render(
      <IntlWrapper>
        <TimesheetTable data={mockData} readOnly={false} onSave={mockOnSave} />
      </IntlWrapper>,
    );

    // 1 header row + 2 data rows = 3 total rows
    const rows = screen.getAllByRole("row");
    expect(rows).toHaveLength(3);
  });

  test("should hide save buttons in readOnly mode", () => {
    render(
      <IntlWrapper>
        <TimesheetTable data={mockData} readOnly={true} onSave={mockOnSave} />
      </IntlWrapper>,
    );

    // "保存" is the Japanese translation for "Save"
    const saveButtons = screen.queryAllByRole("button", { name: /保存/ });
    expect(saveButtons).toHaveLength(0);
  });

  test("should show working hours", () => {
    render(
      <IntlWrapper>
        <TimesheetTable data={mockData} readOnly={true} onSave={mockOnSave} />
      </IntlWrapper>,
    );

    // The first row has workingHours: 8.0, displayed as "8" (since row.workingHours > 0)
    expect(screen.getByText("8")).toBeInTheDocument();
  });
});
