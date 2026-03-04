"use client";

import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import type { TimesheetRow as TimesheetRowType } from "@/types/timesheet";

interface TimesheetRowProps {
  row: TimesheetRowType;
  readOnly: boolean;
  onSave: (date: string, startTime: string, endTime: string, remarks: string, version: number | null) => Promise<void>;
}

/**
 * TimesheetRow - a single table row for one day in the timesheet.
 *
 * Supports both read-only (text spans) and editable (time/text inputs + Save) modes.
 * Background color varies by holiday/weekend/regular day.
 * When no attendance record exists, values are shown in gray italic.
 */
export function TimesheetRow({ row, readOnly, onSave }: TimesheetRowProps) {
  const t = useTranslations("timesheet");

  const [startTime, setStartTime] = useState(row.startTime ?? row.defaultStartTime ?? "");
  const [endTime, setEndTime] = useState(row.endTime ?? row.defaultEndTime ?? "");
  const [remarks, setRemarks] = useState(row.remarks ?? "");
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Sync local state when props change (e.g. after save triggers data reload)
  useEffect(() => {
    setStartTime(row.startTime ?? row.defaultStartTime ?? "");
    setEndTime(row.endTime ?? row.defaultEndTime ?? "");
    setRemarks(row.remarks ?? "");
  }, [row.startTime, row.endTime, row.remarks, row.defaultStartTime, row.defaultEndTime]);

  const bgColor = row.isHoliday ? "bg-amber-50" : row.isWeekend ? "bg-gray-50" : "bg-white";
  const noRecord = !row.hasAttendanceRecord;
  const valueStyle = noRecord ? "text-gray-400 italic" : "text-gray-900";

  const handleSave = async () => {
    try {
      setIsSaving(true);
      setError(null);
      await onSave(row.date, startTime, endTime, remarks, row.attendanceVersion);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("saveError"));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <tr className={`border-b border-gray-100 ${bgColor}`}>
      {/* Date */}
      <td className="py-2 px-3 text-sm text-gray-900 whitespace-nowrap">
        {row.date}
        {row.isHoliday && row.holidayName && <span className="ml-1 text-xs text-amber-600">({row.holidayName})</span>}
      </td>

      {/* Day of week */}
      <td className="py-2 px-3 text-sm text-gray-600 whitespace-nowrap">{row.dayOfWeek}</td>

      {/* Start time */}
      <td className="py-2 px-3">
        {readOnly ? (
          <span className={`text-sm ${valueStyle}`}>{row.startTime ?? "—"}</span>
        ) : (
          <input
            type="time"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            aria-label={t("startTimeLabel", { date: row.date })}
            className="border border-gray-300 rounded px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        )}
      </td>

      {/* End time */}
      <td className="py-2 px-3">
        {readOnly ? (
          <span className={`text-sm ${valueStyle}`}>{row.endTime ?? "—"}</span>
        ) : (
          <input
            type="time"
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
            aria-label={t("endTimeLabel", { date: row.date })}
            className="border border-gray-300 rounded px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        )}
      </td>

      {/* Working hours */}
      <td className={`py-2 px-3 text-sm text-right ${valueStyle}`}>{row.workingHours > 0 ? row.workingHours : "—"}</td>

      {/* Remarks */}
      <td className="py-2 px-3">
        {readOnly ? (
          <span className={`text-sm ${valueStyle}`}>{row.remarks ?? ""}</span>
        ) : (
          <input
            type="text"
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            aria-label={t("remarksLabel", { date: row.date })}
            placeholder={t("remarksPlaceholder")}
            className="w-full border border-gray-300 rounded px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        )}
      </td>

      {/* Actions */}
      {!readOnly && (
        <td className="py-2 px-3">
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleSave}
              disabled={isSaving}
              className="px-3 py-1 text-xs font-medium text-white bg-blue-600 rounded hover:bg-blue-700 disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {isSaving ? t("saving") : t("save")}
            </button>
          </div>
          {error && (
            <p className="mt-1 text-xs text-red-600" role="alert">
              {error}
            </p>
          )}
        </td>
      )}
    </tr>
  );
}
