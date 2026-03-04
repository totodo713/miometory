"use client";

import { useTranslations } from "next-intl";
import { useCallback, useState } from "react";
import { api } from "@/services/api";
import type { TimesheetResponse } from "@/types/timesheet";
import { TimesheetRow } from "./TimesheetRow";
import { TimesheetSummary } from "./TimesheetSummary";

interface EditedRowValues {
  startTime?: string | null;
  endTime?: string | null;
  remarks?: string | null;
}

interface TimesheetTableProps {
  data: TimesheetResponse;
  onRefresh: () => void;
}

export function TimesheetTable({ data, onRefresh }: TimesheetTableProps) {
  const t = useTranslations("worklog");
  const [editedRows, setEditedRows] = useState<Record<string, EditedRowValues>>({});
  const [savingRows, setSavingRows] = useState<Set<string>>(new Set());
  const [saveError, setSaveError] = useState<string | null>(null);

  const handleUpdate = useCallback((date: string, field: string, value: string | null) => {
    setEditedRows((prev) => ({
      ...prev,
      [date]: {
        ...prev[date],
        [field]: value,
      },
    }));
  }, []);

  const isDirty = useCallback(
    (date: string): boolean => {
      const edited = editedRows[date];
      if (!edited) return false;

      const originalRow = data.rows.find((r) => r.date === date);
      if (!originalRow) return false;

      for (const [field, value] of Object.entries(edited)) {
        const originalValue = originalRow[field as keyof typeof originalRow];
        if (value !== originalValue) return true;
      }
      return false;
    },
    [editedRows, data.rows],
  );

  const getEffectiveRow = useCallback(
    (date: string) => {
      const originalRow = data.rows.find((r) => r.date === date);
      if (!originalRow) return null;

      const edited = editedRows[date];
      if (!edited) return originalRow;

      return {
        ...originalRow,
        ...(edited.startTime !== undefined ? { startTime: edited.startTime } : {}),
        ...(edited.endTime !== undefined ? { endTime: edited.endTime } : {}),
        ...(edited.remarks !== undefined ? { remarks: edited.remarks } : {}),
      };
    },
    [data.rows, editedRows],
  );

  const handleSave = useCallback(
    async (date: string) => {
      const originalRow = data.rows.find((r) => r.date === date);
      if (!originalRow) return;

      const edited = editedRows[date] ?? {};
      const startTime = edited.startTime !== undefined ? edited.startTime : originalRow.startTime;
      const endTime = edited.endTime !== undefined ? edited.endTime : originalRow.endTime;
      const remarks = edited.remarks !== undefined ? edited.remarks : originalRow.remarks;

      setSavingRows((prev) => new Set(prev).add(date));
      setSaveError(null);

      try {
        await api.worklog.timesheet.saveAttendance({
          memberId: data.memberId,
          date,
          startTime,
          endTime,
          remarks,
          version: originalRow.attendanceVersion,
        });

        // Clear edited state for this row
        setEditedRows((prev) => {
          const next = { ...prev };
          delete next[date];
          return next;
        });

        onRefresh();
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : t("timesheet.saveError");
        setSaveError(message);
      } finally {
        setSavingRows((prev) => {
          const next = new Set(prev);
          next.delete(date);
          return next;
        });
      }
    },
    [data.rows, data.memberId, editedRows, onRefresh, t],
  );

  return (
    <div className="bg-white rounded-lg shadow overflow-hidden">
      {saveError && (
        <div className="px-4 py-3 bg-red-50 border-b border-red-200 text-sm text-red-700" role="alert">
          {saveError}
        </div>
      )}

      {!data.canEdit && (
        <div className="px-4 py-2 bg-yellow-50 border-b border-yellow-200 text-sm text-yellow-700">
          {t("timesheet.readOnly")}
        </div>
      )}

      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="bg-gray-100 border-b border-gray-200">
              <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{t("timesheet.date")}</th>
              <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                {t("timesheet.dayOfWeek")}
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                {t("timesheet.startTime")}
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                {t("timesheet.endTime")}
              </th>
              <th className="px-3 py-2 text-right text-xs font-medium text-gray-500 uppercase">
                {t("timesheet.workingHours")}
              </th>
              <th className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">
                {t("timesheet.remarks")}
              </th>
              <th className="px-3 py-2 text-center text-xs font-medium text-gray-500 uppercase" />
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200">
            {data.rows.map((row) => {
              const effectiveRow = getEffectiveRow(row.date);
              if (!effectiveRow) return null;

              return (
                <TimesheetRow
                  key={row.date}
                  row={effectiveRow}
                  canEdit={data.canEdit}
                  isDirty={isDirty(row.date)}
                  onUpdate={(field, value) => handleUpdate(row.date, field, value)}
                  onSave={() => handleSave(row.date)}
                  isSaving={savingRows.has(row.date)}
                />
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Footer: Summary */}
      <div className="border-t border-gray-200 p-4">
        <TimesheetSummary summary={data.summary} />
      </div>
    </div>
  );
}
