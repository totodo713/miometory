"use client";

import { useTranslations } from "next-intl";
import type { TimesheetResponse } from "@/types/timesheet";
import { TimesheetRow } from "./TimesheetRow";

interface TimesheetTableProps {
  data: TimesheetResponse;
  readOnly: boolean;
  onSave: (date: string, startTime: string, endTime: string, remarks: string, version: number | null) => Promise<void>;
}

/**
 * TimesheetTable - main table wrapping TimesheetRow components.
 *
 * Renders column headers (Date, Day, Start, End, Hours, Remarks, Actions)
 * and maps each row from TimesheetResponse.rows to a TimesheetRow component.
 */
export function TimesheetTable({ data, readOnly, onSave }: TimesheetTableProps) {
  const t = useTranslations("timesheet.table");

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead>
          <tr className="border-b-2 border-gray-200">
            <th className="py-3 px-3 text-left text-sm font-semibold text-gray-700">{t("date")}</th>
            <th className="py-3 px-3 text-left text-sm font-semibold text-gray-700">{t("dayOfWeek")}</th>
            <th className="py-3 px-3 text-left text-sm font-semibold text-gray-700">{t("startTime")}</th>
            <th className="py-3 px-3 text-left text-sm font-semibold text-gray-700">{t("endTime")}</th>
            <th className="py-3 px-3 text-right text-sm font-semibold text-gray-700">{t("workingHours")}</th>
            <th className="py-3 px-3 text-left text-sm font-semibold text-gray-700">{t("remarks")}</th>
            {!readOnly && (
              <th className="py-3 px-3 text-left text-sm font-semibold text-gray-700">
                <span className="sr-only">{t("actions")}</span>
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {data.rows.map((row) => (
            <TimesheetRow key={row.date} row={row} readOnly={readOnly} onSave={onSave} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
