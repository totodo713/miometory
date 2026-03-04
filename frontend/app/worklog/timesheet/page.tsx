"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { LoadingSpinner } from "@/components/shared/LoadingSpinner";
import { ProjectSelector } from "@/components/worklog/ProjectSelector";
import { TimesheetPeriodToggle } from "@/components/worklog/timesheet/TimesheetPeriodToggle";
import { TimesheetTable } from "@/components/worklog/timesheet/TimesheetTable";
import { useAuth } from "@/hooks/useAuth";
import { api } from "@/services/api";
import { useTimesheetPreferences } from "@/services/worklogStore";
import type { PeriodType, TimesheetResponse } from "@/types/timesheet";

export default function TimesheetPage() {
  const t = useTranslations("worklog");
  const { memberId } = useAuth();

  const { timesheetProjectId, timesheetPeriodType, setTimesheetProjectId, setTimesheetPeriodType } =
    useTimesheetPreferences();

  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1);

  const [data, setData] = useState<TimesheetResponse | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const effectiveMemberId = memberId ?? "";

  const loadTimesheet = useCallback(async () => {
    if (!timesheetProjectId || !effectiveMemberId) return;

    setIsLoading(true);
    setError(null);

    try {
      const response = await api.worklog.timesheet.get({
        year,
        month,
        projectId: timesheetProjectId,
        memberId: effectiveMemberId,
        periodType: timesheetPeriodType,
      });
      setData(response);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to load timesheet");
      setData(null);
    } finally {
      setIsLoading(false);
    }
  }, [year, month, timesheetProjectId, effectiveMemberId, timesheetPeriodType]);

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

  const handlePeriodTypeChange = (type: PeriodType) => {
    setTimesheetPeriodType(type);
  };

  const handleProjectChange = (projectId: string) => {
    setTimesheetProjectId(projectId || null);
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-gray-900">{t("timesheet.title")}</h1>
        </div>

        {/* Controls */}
        <div className="mb-6 flex flex-wrap items-center gap-4">
          {/* Project Selector */}
          <div className="w-64">
            {effectiveMemberId && (
              <ProjectSelector
                memberId={effectiveMemberId}
                value={timesheetProjectId ?? ""}
                onChange={handleProjectChange}
                placeholder={t("timesheet.selectProject")}
              />
            )}
          </div>

          {/* Month navigation */}
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handlePreviousMonth}
              className="px-3 py-2 bg-white border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label="Previous month"
            >
              &larr;
            </button>
            <span className="text-lg font-medium text-gray-900 min-w-[120px] text-center">
              {year}/{String(month).padStart(2, "0")}
            </span>
            <button
              type="button"
              onClick={handleNextMonth}
              className="px-3 py-2 bg-white border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
              aria-label="Next month"
            >
              &rarr;
            </button>
          </div>

          {/* Period toggle */}
          <TimesheetPeriodToggle periodType={timesheetPeriodType} onChange={handlePeriodTypeChange} />
        </div>

        {/* No project selected message */}
        {!timesheetProjectId && (
          <div className="bg-white rounded-lg shadow p-8 text-center text-gray-600">{t("timesheet.noProject")}</div>
        )}

        {/* Loading state */}
        {isLoading && (
          <div className="bg-white rounded-lg shadow p-8 flex justify-center">
            <LoadingSpinner size="lg" label={t("timesheet.title")} />
          </div>
        )}

        {/* Error state */}
        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4">
            <p className="text-red-800">{error}</p>
          </div>
        )}

        {/* Timesheet table */}
        {!isLoading && !error && data && <TimesheetTable data={data} onRefresh={loadTimesheet} />}
      </div>
    </div>
  );
}
