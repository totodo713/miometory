"use client";

import { useTranslations } from "next-intl";
import type { TimesheetSummary as TimesheetSummaryType } from "@/types/timesheet";

interface TimesheetSummaryProps {
  summary: TimesheetSummaryType;
}

/**
 * TimesheetSummary - summary stats displayed below the timesheet table.
 *
 * Shows Total Hours, Working Days, and Business Days in a 3-column colored card grid,
 * following the same visual pattern as MonthlySummary.
 */
export function TimesheetSummary({ summary }: TimesheetSummaryProps) {
  const t = useTranslations("timesheet.summary");

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
      {/* Total Hours */}
      <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
        <div className="text-sm font-medium text-blue-600">{t("totalHours")}</div>
        <div className="mt-1 text-2xl font-bold text-blue-900">{summary.totalWorkingHours}h</div>
      </div>

      {/* Working Days */}
      <div className="rounded-lg border border-green-100 bg-green-50 p-4">
        <div className="text-sm font-medium text-green-700">{t("workingDays")}</div>
        <div className="mt-1 text-2xl font-bold text-green-900">{summary.totalWorkingDays}</div>
      </div>

      {/* Business Days */}
      <div className="rounded-lg border border-gray-200 bg-gray-50 p-4">
        <div className="text-sm font-medium text-gray-600">{t("businessDays")}</div>
        <div className="mt-1 text-2xl font-bold text-gray-900">{summary.totalBusinessDays}</div>
      </div>
    </div>
  );
}
