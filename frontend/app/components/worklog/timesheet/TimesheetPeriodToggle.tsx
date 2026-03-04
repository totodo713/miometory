"use client";

import { useTranslations } from "next-intl";
import type { PeriodType } from "@/types/timesheet";

interface TimesheetPeriodToggleProps {
  periodType: PeriodType;
  onChange: (type: PeriodType) => void;
}

export function TimesheetPeriodToggle({ periodType, onChange }: TimesheetPeriodToggleProps) {
  const t = useTranslations("worklog");

  return (
    <fieldset className="inline-flex rounded-md shadow-sm border-0 p-0 m-0">
      <button
        type="button"
        onClick={() => onChange("calendar")}
        className={`px-4 py-2 text-sm font-medium rounded-l-md border ${
          periodType === "calendar"
            ? "bg-blue-600 text-white border-blue-600"
            : "bg-white text-gray-700 border-gray-300 hover:bg-gray-50"
        } focus:outline-none focus:ring-2 focus:ring-blue-500`}
      >
        {t("timesheet.periodCalendar")}
      </button>
      <button
        type="button"
        onClick={() => onChange("fiscal")}
        className={`px-4 py-2 text-sm font-medium rounded-r-md border-t border-b border-r ${
          periodType === "fiscal"
            ? "bg-blue-600 text-white border-blue-600"
            : "bg-white text-gray-700 border-gray-300 hover:bg-gray-50"
        } focus:outline-none focus:ring-2 focus:ring-blue-500`}
      >
        {t("timesheet.periodFiscal")}
      </button>
    </fieldset>
  );
}
