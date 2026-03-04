import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { IntlWrapper } from "../../../../helpers/intl";

vi.mock("../../../../../app/services/api", () => ({
  api: {
    worklog: {
      timesheet: {
        saveAttendance: vi.fn(),
        deleteAttendance: vi.fn(),
      },
    },
  },
}));

import { TimesheetTable } from "../../../../../app/components/worklog/timesheet/TimesheetTable";
import { api } from "../../../../../app/services/api";
import type { TimesheetResponse, TimesheetRow } from "../../../../../app/types/timesheet";

const mockSaveAttendance = api.worklog.timesheet.saveAttendance as ReturnType<typeof vi.fn>;

function makeRow(overrides: Partial<TimesheetRow> = {}): TimesheetRow {
  return {
    date: "2026-03-01",
    dayOfWeek: "Sun",
    isWeekend: false,
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
    attendanceVersion: 0,
    ...overrides,
  };
}

function makeData(overrides: Partial<TimesheetResponse> = {}): TimesheetResponse {
  return {
    memberId: "member-1",
    memberName: "Test User",
    projectId: "project-1",
    projectName: "Test Project",
    periodType: "calendar",
    periodStart: "2026-03-01",
    periodEnd: "2026-03-31",
    canEdit: true,
    rows: [
      makeRow({ date: "2026-03-01", dayOfWeek: "Sun" }),
      makeRow({ date: "2026-03-02", dayOfWeek: "Mon" }),
      makeRow({ date: "2026-03-03", dayOfWeek: "Tue" }),
    ],
    summary: {
      totalWorkingHours: 16.0,
      totalWorkingDays: 2,
      totalBusinessDays: 22,
    },
    ...overrides,
  };
}

describe("TimesheetTable", () => {
  const defaultProps = {
    data: makeData(),
    onRefresh: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders correct number of rows", () => {
    render(
      <IntlWrapper locale="en">
        <TimesheetTable {...defaultProps} />
      </IntlWrapper>,
    );

    // 3 data rows in tbody (each renders a <tr>)
    const rows = screen.getAllByText(/2026-03-0[1-3]/);
    expect(rows).toHaveLength(3);
  });

  it("shows read-only banner when canEdit is false", () => {
    render(
      <IntlWrapper locale="en">
        <TimesheetTable data={makeData({ canEdit: false })} onRefresh={vi.fn()} />
      </IntlWrapper>,
    );

    // i18n value: "View only"
    expect(screen.getByText("View only")).toBeInTheDocument();
  });

  it("shows summary section", () => {
    render(
      <IntlWrapper locale="en">
        <TimesheetTable {...defaultProps} />
      </IntlWrapper>,
    );

    // totalWorkingHours = 16.0 => "16.0h"
    expect(screen.getByText("16.0h")).toBeInTheDocument();
  });

  it("save triggers API call", async () => {
    mockSaveAttendance.mockResolvedValue(undefined);

    render(
      <IntlWrapper locale="en">
        <TimesheetTable {...defaultProps} />
      </IntlWrapper>,
    );

    // Change start time on the first row
    const startInput = screen.getByLabelText("Start 2026-03-01");
    fireEvent.change(startInput, { target: { value: "09:00" } });

    // Save button should appear for the dirty row
    const saveButton = screen.getByRole("button", { name: "Save 2026-03-01" });
    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(mockSaveAttendance).toHaveBeenCalledWith({
        memberId: "member-1",
        date: "2026-03-01",
        startTime: "09:00",
        endTime: null,
        remarks: null,
        version: 0,
      });
    });
  });
});
