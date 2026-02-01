"use client";

/**
 * Calendar Component
 *
 * Displays a monthly calendar view with:
 * - Fiscal month period (21st to 20th)
 * - Daily work hour totals
 * - Status indicators (DRAFT/SUBMITTED/APPROVED/REJECTED/MIXED)
 * - Weekend highlighting
 * - Navigation to daily entry form
 */

import { useRouter } from "next/navigation";
import type { DailyCalendarEntry } from "@/types/worklog";

interface CalendarProps {
  year: number;
  month: number;
  dates: DailyCalendarEntry[];
  onDateSelect?: (date: string) => void;
}

const STATUS_COLORS = {
  DRAFT: "bg-gray-100 text-gray-800",
  SUBMITTED: "bg-blue-100 text-blue-800",
  APPROVED: "bg-green-100 text-green-800",
  REJECTED: "bg-red-100 text-red-800",
  MIXED: "bg-yellow-100 text-yellow-800",
} as const;

export function Calendar({ year, month, dates, onDateSelect }: CalendarProps) {
  const router = useRouter();

  const handleDateClick = (date: string) => {
    if (onDateSelect) {
      onDateSelect(date);
    } else {
      router.push(`/worklog/${date}`);
    }
  };

  // Group dates by week for rendering
  const weeks: DailyCalendarEntry[][] = [];
  let currentWeek: DailyCalendarEntry[] = [];

  for (const dateEntry of dates) {
    const date = new Date(dateEntry.date);
    const dayOfWeek = date.getDay();

    // Start a new week on Sunday (day 0)
    if (dayOfWeek === 0 && currentWeek.length > 0) {
      weeks.push(currentWeek);
      currentWeek = [];
    }

    // Add padding at the start of first week
    if (weeks.length === 0 && currentWeek.length === 0 && dayOfWeek > 0) {
      for (let i = 0; i < dayOfWeek; i++) {
        currentWeek.push(null as unknown as DailyCalendarEntry); // Padding
      }
    }

    currentWeek.push(dateEntry);
  }

  // Push last week
  if (currentWeek.length > 0) {
    weeks.push(currentWeek);
  }

  return (
    <div className="bg-white rounded-lg shadow">
      {/* Calendar Header */}
      <div className="px-4 py-3 border-b">
        <h2 className="text-lg font-semibold">
          {new Date(year, month - 1).toLocaleDateString("en-US", {
            year: "numeric",
            month: "long",
          })}
        </h2>
        <p className="text-sm text-gray-600">
          Fiscal Period: {dates[0]?.date} to {dates[dates.length - 1]?.date}
        </p>
      </div>

      {/* Day of week headers */}
      <div className="grid grid-cols-7 gap-px bg-gray-200 border-b">
        {["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"].map((day) => (
          <div
            key={day}
            className="bg-gray-50 px-2 py-2 text-center text-sm font-medium text-gray-700"
          >
            {day}
          </div>
        ))}
      </div>

      {/* Calendar grid */}
      <div className="grid grid-cols-7 gap-px bg-gray-200">
        {weeks.flatMap((week, weekIdx) =>
          week.map((dateEntry, dayIdx) => {
            const cellKey = dateEntry
              ? dateEntry.date
              : `empty-w${weekIdx}-d${dayIdx}`;

            if (!dateEntry) {
              // Empty cell (padding)
              return <div key={cellKey} className="bg-gray-50 min-h-24" />;
            }

            const date = new Date(dateEntry.date);
            const dayNum = date.getDate();
            const hasWorkHours = dateEntry.totalWorkHours > 0;
            const hasAbsenceHours = dateEntry.totalAbsenceHours > 0;
            const hasAnyHours = hasWorkHours || hasAbsenceHours;
            const statusColor =
              STATUS_COLORS[dateEntry.status as keyof typeof STATUS_COLORS] ||
              STATUS_COLORS.DRAFT;
            const monthName = date.toLocaleDateString("en-US", {
              month: "long",
            });
            const ariaLabel = `${monthName} ${dayNum}, ${date.getFullYear()}`;

            // Determine background color (priority: holiday > absence > weekend > white)
            const backgroundClass = dateEntry.isHoliday
              ? "bg-holiday-100"
              : hasAbsenceHours
                ? "bg-blue-50"
                : dateEntry.isWeekend
                  ? "bg-weekend-100"
                  : "bg-white";

            return (
              <button
                type="button"
                key={dateEntry.date}
                onClick={() => handleDateClick(dateEntry.date)}
                aria-label={ariaLabel}
                className={`
                  ${backgroundClass} p-2 min-h-24 text-left calendar-day
                  hover:bg-gray-50 transition-colors
                  focus:outline-none focus:ring-2 focus:ring-blue-500
                `}
              >
                {/* Day number */}
                <div className="flex justify-between items-start mb-1">
                  <span
                    className={`
                    text-sm font-medium
                    ${dateEntry.isWeekend ? "text-gray-600" : "text-gray-900"}
                  `}
                  >
                    {dayNum}
                  </span>
                  <div className="flex items-center gap-1">
                    {dateEntry.hasProxyEntries && (
                      <span
                        className="text-xs text-amber-600"
                        title="Contains entries made by manager"
                        aria-label="Contains entries made by manager"
                        role="img"
                      >
                        👤<span className="sr-only">Proxy entry indicator</span>
                      </span>
                    )}
                    {dateEntry.isHoliday && (
                      <span className="text-xs text-holiday-600">H</span>
                    )}
                  </div>
                </div>

                {/* Hours display */}
                {hasAnyHours && (
                  <div className="mt-1 space-y-1">
                    {/* Work hours */}
                    {hasWorkHours && (
                      <div className="flex items-center gap-1">
                        <span className="text-xs text-gray-600">Work:</span>
                        <span className="text-sm font-semibold text-gray-900">
                          {dateEntry.totalWorkHours}h
                        </span>
                      </div>
                    )}

                    {/* Absence hours */}
                    {hasAbsenceHours && (
                      <div className="flex items-center gap-1">
                        <span className="text-xs text-blue-600">🏖️</span>
                        <span className="text-sm font-semibold text-blue-600">
                          {dateEntry.totalAbsenceHours}h
                        </span>
                      </div>
                    )}
                  </div>
                )}

                {/* Status badge */}
                {dateEntry.status !== "DRAFT" && (
                  <div className="mt-1">
                    <span
                      className={`
                      inline-block px-1.5 py-0.5 rounded text-xs font-medium
                      ${statusColor}
                    `}
                    >
                      {dateEntry.status}
                    </span>
                  </div>
                )}
              </button>
            );
          }),
        )}
      </div>
    </div>
  );
}
