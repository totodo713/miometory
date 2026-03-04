"use client";

import { useTranslations } from "next-intl";
import type { TimesheetRow as TimesheetRowType } from "@/types/timesheet";

interface TimesheetRowProps {
  row: TimesheetRowType;
  canEdit: boolean;
  isDirty: boolean;
  onUpdate: (field: string, value: string | null) => void;
  onSave: () => void;
  isSaving: boolean;
}

export function TimesheetRow({ row, canEdit, isDirty, onUpdate, onSave, isSaving }: TimesheetRowProps) {
  const t = useTranslations("worklog");

  const isTimeInvalid = row.startTime && row.endTime && row.endTime < row.startTime;

  const rowBg = row.isHoliday ? "bg-amber-50" : row.isWeekend ? "bg-gray-50" : "";

  // Determine displayed start/end times (show defaults in italic if no attendance record)
  const displayStartTime =
    row.startTime ?? (!row.hasAttendanceRecord && row.defaultStartTime ? row.defaultStartTime : "");
  const displayEndTime = row.endTime ?? (!row.hasAttendanceRecord && row.defaultEndTime ? row.defaultEndTime : "");
  const isShowingDefault = !row.hasAttendanceRecord && !row.startTime && row.defaultStartTime;

  return (
    <tr className={rowBg}>
      {/* Date cell */}
      <td className="px-3 py-2 text-sm text-gray-900 whitespace-nowrap">
        <div>{row.date}</div>
        {row.isHoliday && row.holidayName && <div className="text-xs text-amber-700">{row.holidayName}</div>}
      </td>

      {/* Day of week */}
      <td className="px-3 py-2 text-sm text-gray-600 whitespace-nowrap">{row.dayOfWeek}</td>

      {/* Start time */}
      <td className="px-3 py-2">
        <input
          type="time"
          value={row.startTime ?? ""}
          onChange={(e) => onUpdate("startTime", e.target.value || null)}
          disabled={!canEdit}
          aria-label={`${t("timesheet.startTime")} ${row.date}`}
          aria-invalid={!!isTimeInvalid}
          placeholder={isShowingDefault ? displayStartTime : undefined}
          className={`w-28 px-2 py-1 text-sm border rounded-md disabled:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500 ${
            isTimeInvalid ? "border-red-500" : "border-gray-300"
          } ${isShowingDefault && !row.startTime ? "italic text-gray-400" : ""}`}
        />
      </td>

      {/* End time */}
      <td className="px-3 py-2">
        <input
          type="time"
          value={row.endTime ?? ""}
          onChange={(e) => onUpdate("endTime", e.target.value || null)}
          disabled={!canEdit}
          aria-label={`${t("timesheet.endTime")} ${row.date}`}
          aria-invalid={!!isTimeInvalid}
          placeholder={isShowingDefault ? displayEndTime : undefined}
          className={`w-28 px-2 py-1 text-sm border rounded-md disabled:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500 ${
            isTimeInvalid ? "border-red-500" : "border-gray-300"
          } ${isShowingDefault && !row.endTime ? "italic text-gray-400" : ""}`}
        />
      </td>

      {/* Working hours (read-only) */}
      <td className="px-3 py-2 text-sm text-gray-900 text-right whitespace-nowrap">
        {row.workingHours > 0 ? row.workingHours.toFixed(1) : "-"}
      </td>

      {/* Remarks */}
      <td className="px-3 py-2">
        <input
          type="text"
          value={row.remarks ?? ""}
          onChange={(e) => onUpdate("remarks", e.target.value || null)}
          disabled={!canEdit}
          aria-label={`${t("timesheet.remarks")} ${row.date}`}
          className="w-full px-2 py-1 text-sm border border-gray-300 rounded-md disabled:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </td>

      {/* Action */}
      <td className="px-3 py-2 text-center">
        {isDirty && canEdit && (
          <button
            type="button"
            onClick={onSave}
            disabled={isSaving}
            aria-label={`${t("timesheet.save")} ${row.date}`}
            className="px-3 py-1 text-xs font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {isSaving ? t("timesheet.saving") : t("timesheet.save")}
          </button>
        )}
      </td>
    </tr>
  );
}
