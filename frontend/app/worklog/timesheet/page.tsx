"use client";

/**
 * Monthly Timesheet Page
 *
 * Displays a project-specific monthly attendance grid where users can
 * view and edit daily start/end times and remarks.
 * Supports calendar vs fiscal period toggling and month navigation.
 */

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { ProjectSelector } from "@/components/worklog/ProjectSelector";
import { TimesheetPeriodToggle } from "@/components/worklog/timesheet/TimesheetPeriodToggle";
import { TimesheetSummary } from "@/components/worklog/timesheet/TimesheetSummary";
import { TimesheetTable } from "@/components/worklog/timesheet/TimesheetTable";
import { useAuth } from "@/hooks/useAuth";
import { useTenantContext } from "@/providers/TenantProvider";
import { api } from "@/services/api";
import type { PeriodType, TimesheetResponse } from "@/types/timesheet";

export default function TimesheetPage() {
  const t = useTranslations("timesheet");
  const { memberId } = useAuth();
  const { selectedTenantId } = useTenantContext();

  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);
  const [projectId, setProjectId] = useState("");
  const [periodType, setPeriodType] = useState<PeriodType>("calendar");
  const [data, setData] = useState<TimesheetResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadTimesheet = useCallback(async () => {
    if (!memberId || !projectId) return;

    setIsLoading(true);
    setError(null);

    try {
      const response = await api.timesheet.get({
        year,
        month,
        memberId,
        projectId,
        periodType,
      });
      setData(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("loadError"));
      setData(null);
    } finally {
      setIsLoading(false);
    }
  }, [year, month, memberId, projectId, periodType, t]);

  useEffect(() => {
    loadTimesheet();
  }, [loadTimesheet]);

  const handlePreviousMonth = () => {
    if (month === 1) {
      setYear(year - 1);
      setMonth(12);
    } else {
      setMonth(month - 1);
    }
  };

  const handleNextMonth = () => {
    if (month === 12) {
      setYear(year + 1);
      setMonth(1);
    } else {
      setMonth(month + 1);
    }
  };

  const handleSave = useCallback(
    async (date: string, startTime: string, endTime: string, remarks: string, version: number | null) => {
      if (!memberId || !selectedTenantId) return;

      try {
        await api.timesheet.saveAttendance(memberId, selectedTenantId, {
          date,
          startTime: startTime || null,
          endTime: endTime || null,
          remarks: remarks || null,
          version,
        });
        await loadTimesheet();
      } catch (err) {
        setError(err instanceof Error ? err.message : t("saveError"));
      }
    },
    [memberId, selectedTenantId, loadTimesheet, t],
  );

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Title */}
        <h1 className="text-3xl font-bold text-gray-900 mb-6">{t("title")}</h1>

        {/* Controls Bar */}
        <div className="mb-6 flex flex-wrap items-center gap-4">
          {/* Project Selector */}
          <div className="w-72">
            <ProjectSelector memberId={memberId ?? ""} value={projectId} onChange={setProjectId} />
          </div>

          {/* Month Navigation */}
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handlePreviousMonth}
              className="px-3 py-2 bg-white border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label={t("prevMonth")}
            >
              &#9664;
            </button>
            <span className="text-lg font-semibold text-gray-900 min-w-[120px] text-center">
              {year}
              {t("yearSuffix")}
              {month}
              {t("monthSuffix")}
            </span>
            <button
              type="button"
              onClick={handleNextMonth}
              className="px-3 py-2 bg-white border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label={t("nextMonth")}
            >
              &#9654;
            </button>
          </div>

          {/* Period Type Toggle */}
          <TimesheetPeriodToggle value={periodType} onChange={setPeriodType} />
        </div>

        {/* Loading State */}
        {isLoading && (
          <div className="bg-white rounded-lg shadow p-8 flex justify-center">
            <LoadingSpinner size="lg" label={t("title")} />
          </div>
        )}

        {/* Error State */}
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-4" role="alert">
            <p className="text-red-800">{error}</p>
          </div>
        )}

        {/* Data State */}
        {!isLoading && !error && data && (
          <div className="bg-white rounded-lg shadow p-6">
            <TimesheetTable data={data} readOnly={false} onSave={handleSave} />
            <div className="mt-6">
              <TimesheetSummary summary={data.summary} />
            </div>
          </div>
        )}

        {/* No Project Selected */}
        {!isLoading && !error && !data && !projectId && (
          <div className="bg-white rounded-lg shadow p-8 text-center text-gray-600">{t("selectProjectPrompt")}</div>
        )}
      </div>
    </div>
  );
}
