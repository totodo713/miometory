"use client";

import { useTranslations } from "next-intl";
import type { PeriodType } from "@/types/timesheet";

interface TimesheetPeriodToggleProps {
  value: PeriodType;
  onChange: (value: PeriodType) => void;
}

/**
 * TimesheetPeriodToggle - pill toggle between "Calendar" and "Fiscal" period types.
 *
 * Uses semantic radio inputs (visually hidden) with styled labels for accessibility.
 */
export function TimesheetPeriodToggle({ value, onChange }: TimesheetPeriodToggleProps) {
  const t = useTranslations("timesheet");

  return (
    <fieldset>
      <legend className="sr-only">{t("periodType")}</legend>
      <div className="inline-flex rounded-lg border border-gray-300 p-1">
        <label
          className={`cursor-pointer rounded-md px-4 py-2 text-sm font-medium transition-colors focus-within:ring-2 focus-within:ring-blue-500 ${
            value === "calendar" ? "bg-blue-600 text-white" : "bg-white text-gray-700 hover:bg-gray-50"
          }`}
        >
          <input
            type="radio"
            name="periodType"
            value="calendar"
            checked={value === "calendar"}
            onChange={() => onChange("calendar")}
            className="sr-only"
          />
          {t("calendarPeriod")}
        </label>
        <label
          className={`cursor-pointer rounded-md px-4 py-2 text-sm font-medium transition-colors focus-within:ring-2 focus-within:ring-blue-500 ${
            value === "fiscal" ? "bg-blue-600 text-white" : "bg-white text-gray-700 hover:bg-gray-50"
          }`}
        >
          <input
            type="radio"
            name="periodType"
            value="fiscal"
            checked={value === "fiscal"}
            onChange={() => onChange("fiscal")}
            className="sr-only"
          />
          {t("fiscalPeriod")}
        </label>
      </div>
    </fieldset>
  );
}
