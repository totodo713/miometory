"use client";

import { useTranslations } from "next-intl";
import type { TimesheetSummary as TimesheetSummaryType } from "@/types/timesheet";

interface TimesheetSummaryProps {
  summary: TimesheetSummaryType;
}

export function TimesheetSummary({ summary }: TimesheetSummaryProps) {
  const t = useTranslations("worklog");

  return (
    <div className="flex items-center gap-6 px-4 py-3 bg-gray-50 rounded-lg">
      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-600">{t("timesheet.totalHours")}:</span>
        <span className="text-lg font-semibold text-gray-900">{summary.totalWorkingHours.toFixed(1)}h</span>
      </div>
      <div className="flex items-center gap-2">
        <span className="text-sm text-gray-600">{t("timesheet.workingDays")}:</span>
        <span className="text-lg font-semibold text-gray-900">
          {t("timesheet.daysCount", {
            working: summary.totalWorkingDays,
            business: summary.totalBusinessDays,
          })}
        </span>
      </div>
    </div>
  );
}
