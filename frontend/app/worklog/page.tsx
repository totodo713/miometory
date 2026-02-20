"use client";

/**
 * Miometry Dashboard Page
 *
 * Main entry point for time entry functionality.
 * Displays monthly calendar view with navigation.
 * Supports proxy entry mode for managers entering time on behalf of subordinates.
 */

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Calendar } from "@/components/worklog/Calendar";
import { CopyPreviousMonthDialog } from "@/components/worklog/CopyPreviousMonthDialog";
import { MonthlySummary } from "@/components/worklog/MonthlySummary";
import { useAuth } from "@/hooks/useAuth";
import { api } from "@/services/api";
import { exportCsv } from "@/services/csvService";
import { useCalendarRefresh, useProxyMode } from "@/services/worklogStore";
import type { MonthlyCalendarResponse } from "@/types/worklog";

export default function WorkLogPage() {
  const [calendarData, setCalendarData] = useState<MonthlyCalendarResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [isExporting, setIsExporting] = useState(false);
  const [isCopyDialogOpen, setIsCopyDialogOpen] = useState(false);

  // Authentication state
  const { userId } = useAuth();

  // Proxy mode state
  const { isProxyMode, targetMember, disableProxyMode } = useProxyMode();

  // Calendar refresh key - changes after save to trigger data reload
  const { calendarRefreshKey } = useCalendarRefresh();

  // Get current year and month
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1); // 1-indexed

  // Use target member ID if in proxy mode, otherwise use current user
  const effectiveMemberId = isProxyMode && targetMember ? targetMember.id : (userId ?? "");

  const loadCalendar = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const data = await api.worklog.getCalendar({
        year,
        month,
        memberId: effectiveMemberId,
      });
      setCalendarData(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load calendar");
    } finally {
      setIsLoading(false);
    }
  }, [year, month, effectiveMemberId]);

  // Initial load and parameter change
  useEffect(() => {
    loadCalendar();
  }, [loadCalendar]);

  // Refresh after save
  useEffect(() => {
    if (calendarRefreshKey > 0) {
      loadCalendar();
    }
  }, [calendarRefreshKey, loadCalendar]);

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

  const handleToday = () => {
    const today = new Date();
    setYear(today.getFullYear());
    setMonth(today.getMonth() + 1);
  };

  const handleExport = async () => {
    setIsExporting(true);
    try {
      await exportCsv(year, month, effectiveMemberId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to export CSV");
    } finally {
      setIsExporting(false);
    }
  };

  const handleCopyProjects = (projectIds: string[]) => {
    // Store copied project IDs in session storage for use by the entry form
    // The DailyEntryForm can read these and pre-populate project selections
    if (projectIds.length > 0) {
      sessionStorage.setItem(
        "copiedProjectIds",
        JSON.stringify({
          projectIds,
          year,
          month,
          copiedAt: new Date().toISOString(),
        }),
      );
      // Optionally show a success message or navigate to add entries
      setError(null);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Proxy Mode Banner */}
      {isProxyMode && targetMember && (
        <div className="bg-yellow-400 px-4 py-2">
          <div className="max-w-7xl mx-auto flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-yellow-900 font-medium">
                Proxy Mode: Entering time for <span className="font-bold">{targetMember.displayName}</span>
              </span>
            </div>
            <button
              type="button"
              onClick={disableProxyMode}
              className="px-3 py-1 text-sm text-yellow-900 bg-yellow-200 hover:bg-yellow-100 border border-yellow-600 rounded-md transition-colors"
            >
              Exit Proxy Mode
            </button>
          </div>
        </div>
      )}

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">
              {isProxyMode && targetMember ? `Miometry - ${targetMember.displayName}` : "Miometry"}
            </h1>
            <p className="mt-1 text-sm text-gray-600">
              {isProxyMode && targetMember
                ? `Entering time on behalf of ${targetMember.email}`
                : "Track and manage your daily work hours"}
            </p>
          </div>
          <div className="flex gap-2">
            {/* Proxy Entry Link (for managers) */}
            <Link
              href="/worklog/proxy"
              className="px-4 py-2 bg-amber-700 text-white rounded-md shadow-sm text-sm font-medium hover:bg-amber-800"
            >
              Enter Time for Team
            </Link>
            <button
              type="button"
              onClick={() => setIsCopyDialogOpen(true)}
              className="px-4 py-2 bg-purple-600 text-white rounded-md shadow-sm text-sm font-medium hover:bg-purple-700"
            >
              Copy Previous Month
            </button>
            <Link
              href="/worklog/import"
              className="px-4 py-2 bg-green-700 text-white rounded-md shadow-sm text-sm font-medium hover:bg-green-800"
            >
              Import CSV
            </Link>
            <button
              type="button"
              onClick={handleExport}
              disabled={isExporting}
              className="px-4 py-2 bg-blue-600 text-white rounded-md shadow-sm text-sm font-medium hover:bg-blue-700 disabled:opacity-50"
            >
              {isExporting ? "Exporting..." : "Export CSV"}
            </button>
          </div>
        </div>

        {/* Navigation */}
        <div className="mb-4 flex items-center justify-between">
          <div className="flex gap-2">
            <button
              type="button"
              onClick={handlePreviousMonth}
              className="px-4 py-2 bg-white border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Previous
            </button>
            <button
              type="button"
              onClick={handleToday}
              className="px-4 py-2 bg-white border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Today
            </button>
            <button
              type="button"
              onClick={handleNextMonth}
              className="px-4 py-2 bg-white border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Next
            </button>
          </div>

          <div className="text-sm text-gray-600">
            {calendarData && (
              <span>
                Period: {new Date(calendarData.periodStart).toLocaleDateString()} -{" "}
                {new Date(calendarData.periodEnd).toLocaleDateString()}
              </span>
            )}
          </div>
        </div>

        {/* Calendar */}
        {isLoading && (
          <div className="bg-white rounded-lg shadow p-8 text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto" />
            <p className="mt-4 text-gray-600">Loading calendar...</p>
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4">
            <p className="text-red-800">Error: {error}</p>
          </div>
        )}

        {!isLoading && !error && calendarData && (
          <>
            <Calendar year={year} month={month} dates={calendarData.dates} />

            {/* Monthly Summary (T072) */}
            <div className="mt-6">
              <MonthlySummary year={year} month={month} memberId={effectiveMemberId} />
            </div>
          </>
        )}

        {!isLoading && !error && !calendarData && (
          <div className="bg-white rounded-lg shadow p-8 text-center text-gray-600">No data available</div>
        )}

        {/* Copy Previous Month Dialog */}
        <CopyPreviousMonthDialog
          isOpen={isCopyDialogOpen}
          onClose={() => setIsCopyDialogOpen(false)}
          onConfirm={handleCopyProjects}
          year={year}
          month={month}
          memberId={effectiveMemberId}
        />
      </div>
    </div>
  );
}
