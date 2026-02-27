"use client";

/**
 * Calendar Component
 *
 * Displays a monthly calendar view with:
 * - Fiscal month period (configurable per organization)
 * - Date info (fiscal year, fiscal period, monthly period)
 * - Daily work hour totals
 * - Status indicators (DRAFT/SUBMITTED/APPROVED/REJECTED/MIXED)
 * - Weekend highlighting
 * - Navigation to daily entry form
 * - Responsive mobile list view
 */

import { useRouter } from "next/navigation";
import { useFormatter, useTranslations } from "next-intl";
import { Skeleton } from "@/components/shared/Skeleton";
import { useDateInfo } from "@/hooks/useDateInfo";
import { useMediaQuery } from "@/hooks/useMediaQuery";
import type { DailyCalendarEntry } from "@/types/worklog";

interface CalendarProps {
  year: number;
  month: number;
  dates: DailyCalendarEntry[];
  onDateSelect?: (date: string) => void;
  tenantId?: string;
  orgId?: string;
}

const STATUS_COLORS = {
  DRAFT: "bg-gray-100 text-gray-800",
  SUBMITTED: "bg-blue-100 text-blue-800",
  APPROVED: "bg-green-100 text-green-800",
  REJECTED: "bg-red-100 text-red-800",
  MIXED: "bg-yellow-100 text-yellow-800",
} as const;

export function Calendar({ year, month, dates, onDateSelect, tenantId, orgId }: CalendarProps) {
  const router = useRouter();
  const format = useFormatter();
  const t = useTranslations("worklog.calendar");
  const { data: dateInfo, isLoading: dateInfoLoading } = useDateInfo(tenantId, orgId, year, month);
  const isMobile = useMediaQuery("(max-width: 767px)");

  const handleDateClick = (date: string) => {
    if (onDateSelect) {
      onDateSelect(date);
    } else {
      router.push(`/worklog/${date}`);
    }
  };

  // Group dates by week for rendering
  const weeks: (DailyCalendarEntry | null)[][] = [];
  let currentWeek: (DailyCalendarEntry | null)[] = [];

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
        currentWeek.push(null);
      }
    }

    currentWeek.push(dateEntry);
  }

  // Push last week
  if (currentWeek.length > 0) {
    weeks.push(currentWeek);
  }

  const DAY_NAMES = [
    t("dayNames.sun"),
    t("dayNames.mon"),
    t("dayNames.tue"),
    t("dayNames.wed"),
    t("dayNames.thu"),
    t("dayNames.fri"),
    t("dayNames.sat"),
  ] as const;

  return (
    <div className="bg-white rounded-lg shadow">
      {/* Calendar Header */}
      <div className="px-4 py-3 border-b">
        <h2 className="text-lg font-semibold">
          {format.dateTime(new Date(year, month - 1), {
            year: "numeric",
            month: "long",
          })}
        </h2>
        <p className="text-sm text-gray-600">
          {t("fiscalPeriod", { start: dates[0]?.date, end: dates[dates.length - 1]?.date })}
        </p>
        {/* Date info (fiscal year, fiscal period, monthly period) */}
        {dateInfoLoading ? (
          <Skeleton.Text lines={1} />
        ) : dateInfo ? (
          <p className="text-xs text-gray-500 mt-1">
            {t("monthlyPeriod", {
              fiscalYear: dateInfo.fiscalYear,
              fiscalPeriod: dateInfo.fiscalPeriod,
              start: dateInfo.monthlyPeriodStart,
              end: dateInfo.monthlyPeriodEnd,
            })}
          </p>
        ) : null}
      </div>

      {isMobile ? (
        /* Mobile list view */
        <div className="divide-y divide-gray-200" data-testid="mobile-calendar-list">
          {dates.map((dateEntry) => {
            const date = new Date(dateEntry.date);
            const dayNum = date.getDate();
            const dayName = DAY_NAMES[date.getDay()];
            const hasWorkHours = dateEntry.totalWorkHours > 0;
            const hasAbsenceHours = dateEntry.totalAbsenceHours > 0;
            const hasAnyHours = hasWorkHours || hasAbsenceHours;
            const statusColor = STATUS_COLORS[dateEntry.status as keyof typeof STATUS_COLORS] || STATUS_COLORS.DRAFT;
            const monthName = format.dateTime(date, { month: "long" });
            const ariaLabel = `${monthName} ${dayNum}, ${date.getFullYear()}`;

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
                  ${backgroundClass} w-full px-4 py-3 text-left
                  hover:bg-gray-50 transition-colors
                  focus:outline-none focus:ring-2 focus:ring-blue-500
                  flex items-center justify-between gap-3
                `}
              >
                {/* Left: date and day */}
                <div className="flex items-center gap-3 min-w-0">
                  <span
                    className={`text-base font-semibold w-8 text-center ${dateEntry.isWeekend ? "text-gray-500" : "text-gray-900"}`}
                  >
                    {dayNum}
                  </span>
                  <span className="text-sm text-gray-500 w-10">{dayName}</span>
                  <div className="flex items-center gap-1">
                    {dateEntry.isHoliday && <span className="text-xs text-holiday-600">H</span>}
                    {dateEntry.hasProxyEntries && (
                      <span className="text-xs text-amber-600" title={t("proxyEntryTitle")} role="img">
                        👤<span className="sr-only">{t("proxyEntryLabel")}</span>
                      </span>
                    )}
                  </div>
                </div>

                {/* Center: hours */}
                <div className="flex items-center gap-3 flex-1 justify-center">
                  {hasWorkHours && (
                    <span className="text-sm font-semibold text-gray-900">{dateEntry.totalWorkHours}h</span>
                  )}
                  {hasAbsenceHours && (
                    <span className="text-sm font-semibold text-blue-600">🏖️ {dateEntry.totalAbsenceHours}h</span>
                  )}
                </div>

                {/* Right: status badge + rejection */}
                <div className="flex items-center gap-2">
                  {dateEntry.rejectionSource && (
                    <span
                      className="inline-block w-2 h-2 rounded-full bg-red-400"
                      title={
                        dateEntry.rejectionSource === "monthly"
                          ? t("rejectionMonthly", { reason: dateEntry.rejectionReason ?? "" })
                          : t("rejectionDaily", { reason: dateEntry.rejectionReason ?? "" })
                      }
                    />
                  )}
                  {(dateEntry.status !== "DRAFT" || hasAnyHours) && (
                    <span className={`inline-block px-1.5 py-0.5 rounded text-xs font-medium ${statusColor}`}>
                      {dateEntry.status}
                    </span>
                  )}
                </div>
              </button>
            );
          })}
        </div>
      ) : (
        /* Desktop grid view */
        <>
          {/* Day of week headers */}
          <div className="grid grid-cols-7 gap-px bg-gray-200 border-b">
            {DAY_NAMES.map((day) => (
              <div key={day} className="bg-gray-50 px-2 py-2 text-center text-sm font-medium text-gray-700">
                {day}
              </div>
            ))}
          </div>

          {/* Calendar grid */}
          <div className="grid grid-cols-7 gap-px bg-gray-200">
            {weeks.flatMap((week, weekIdx) =>
              week.map((dateEntry, dayIdx) => {
                const cellKey = dateEntry ? dateEntry.date : `empty-w${weekIdx}-d${dayIdx}`;

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
                  STATUS_COLORS[dateEntry.status as keyof typeof STATUS_COLORS] || STATUS_COLORS.DRAFT;
                const monthName = format.dateTime(date, {
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
                            title={t("proxyEntryTitle")}
                            aria-label={t("proxyEntryTitle")}
                            role="img"
                          >
                            👤<span className="sr-only">{t("proxyEntryLabel")}</span>
                          </span>
                        )}
                        {dateEntry.isHoliday && <span className="text-xs text-holiday-600">H</span>}
                      </div>
                    </div>

                    {/* Hours display */}
                    {hasAnyHours && (
                      <div className="mt-1 space-y-1">
                        {/* Work hours */}
                        {hasWorkHours && (
                          <div className="flex items-center gap-1">
                            <span className="text-xs text-gray-600">{t("workLabel")}</span>
                            <span className="text-sm font-semibold text-gray-900">{dateEntry.totalWorkHours}h</span>
                          </div>
                        )}

                        {/* Absence hours */}
                        {hasAbsenceHours && (
                          <div className="flex items-center gap-1">
                            <span className="text-xs text-blue-600">🏖️</span>
                            <span className="text-sm font-semibold text-blue-600">{dateEntry.totalAbsenceHours}h</span>
                          </div>
                        )}
                      </div>
                    )}

                    {/* Rejection indicator */}
                    {dateEntry.rejectionSource && (
                      <div className="mt-1">
                        <span
                          className="inline-block w-full h-0.5 rounded bg-red-400"
                          title={
                            dateEntry.rejectionSource === "monthly"
                              ? t("rejectionMonthly", { reason: dateEntry.rejectionReason ?? "" })
                              : t("rejectionDaily", { reason: dateEntry.rejectionReason ?? "" })
                          }
                        />
                      </div>
                    )}

                    {/* Status badge */}
                    {(dateEntry.status !== "DRAFT" || hasAnyHours) && (
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
        </>
      )}
    </div>
  );
}
