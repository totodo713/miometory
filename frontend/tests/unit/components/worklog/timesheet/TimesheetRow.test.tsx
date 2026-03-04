import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { TimesheetRow } from "../../../../../app/components/worklog/timesheet/TimesheetRow";
import type { TimesheetRow as TimesheetRowType } from "../../../../../app/types/timesheet";
import { IntlWrapper } from "../../../../helpers/intl";

function makeRow(overrides: Partial<TimesheetRowType> = {}): TimesheetRowType {
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

describe("TimesheetRow", () => {
  const defaultProps = {
    row: makeRow(),
    canEdit: true,
    isDirty: false,
    onUpdate: vi.fn(),
    onSave: vi.fn(),
    isSaving: false,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderRow = (props: Partial<typeof defaultProps> = {}) => {
    return render(
      <IntlWrapper locale="en">
        <table>
          <tbody>
            <TimesheetRow {...defaultProps} {...props} />
          </tbody>
        </table>
      </IntlWrapper>,
    );
  };

  it("renders date and day of week", () => {
    renderRow({ row: makeRow({ date: "2026-03-02", dayOfWeek: "Mon" }) });

    expect(screen.getByText("2026-03-02")).toBeInTheDocument();
    expect(screen.getByText("Mon")).toBeInTheDocument();
  });

  it("shows Save button only when dirty and canEdit", () => {
    // isDirty=true, canEdit=true => Save visible
    const { unmount } = renderRow({ isDirty: true, canEdit: true });
    expect(screen.getByRole("button", { name: "Save 2026-03-01" })).toBeInTheDocument();
    unmount();

    // isDirty=false => no Save button
    renderRow({ isDirty: false, canEdit: true });
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("hides inputs when canEdit is false", () => {
    renderRow({ canEdit: false });

    const startInput = screen.getByLabelText("Start 2026-03-01");
    const endInput = screen.getByLabelText("End 2026-03-01");
    const remarksInput = screen.getByLabelText("Remarks 2026-03-01");

    expect(startInput).toBeDisabled();
    expect(endInput).toBeDisabled();
    expect(remarksInput).toBeDisabled();
  });

  it("applies weekend background class", () => {
    renderRow({ row: makeRow({ isWeekend: true }) });

    const row = screen.getByText("2026-03-01").closest("tr");
    expect(row).toHaveClass("bg-gray-50");
  });

  it("applies holiday background class and shows holidayName", () => {
    renderRow({
      row: makeRow({
        isHoliday: true,
        holidayName: "Spring Equinox",
      }),
    });

    const row = screen.getByText("2026-03-01").closest("tr");
    expect(row).toHaveClass("bg-amber-50");
    expect(screen.getByText("Spring Equinox")).toBeInTheDocument();
  });

  it("calls onUpdate when time input changes", () => {
    const onUpdate = vi.fn();
    renderRow({ onUpdate });

    const startInput = screen.getByLabelText("Start 2026-03-01");
    fireEvent.change(startInput, { target: { value: "09:00" } });

    expect(onUpdate).toHaveBeenCalledWith("startTime", "09:00");
  });

  it("calls onSave when Save button clicked", () => {
    const onSave = vi.fn();
    renderRow({ isDirty: true, canEdit: true, onSave });

    const saveButton = screen.getByRole("button", { name: "Save 2026-03-01" });
    fireEvent.click(saveButton);

    expect(onSave).toHaveBeenCalledTimes(1);
  });
});
