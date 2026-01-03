"use client";

/**
 * Work Log Dashboard Page
 *
 * Main entry point for work log functionality.
 * Displays monthly calendar view with navigation.
 */

import { useEffect, useState } from "react";
import { Calendar } from "@/components/worklog/Calendar";
import { MonthlySummary } from "@/components/worklog/MonthlySummary";
import { api } from "@/services/api";
import type { MonthlyCalendarResponse } from "@/types/worklog";

export default function WorkLogPage() {
  const [calendarData, setCalendarData] =
    useState<MonthlyCalendarResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Get current year and month
  const now = new Date();
  const [year, setYear] = useState(now.getFullYear());
  const [month, setMonth] = useState(now.getMonth() + 1); // 1-indexed

  // TODO: Get actual member ID from auth context
  const memberId = "00000000-0000-0000-0000-000000000001";

  useEffect(() => {
    async function loadCalendar() {
      setIsLoading(true);
      setError(null);

      try {
        const data = await api.worklog.getCalendar({ year, month, memberId });
        setCalendarData(data);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to load calendar",
        );
      } finally {
        setIsLoading(false);
      }
    }

    loadCalendar();
  }, [year, month]); // memberId is constant, no need to include

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

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="mb-6">
          <h1 className="text-3xl font-bold text-gray-900">Work Log</h1>
          <p className="mt-1 text-sm text-gray-600">
            Track and manage your daily work hours
          </p>
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
                Period:{" "}
                {new Date(calendarData.periodStart).toLocaleDateString()} -{" "}
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
              <MonthlySummary year={year} month={month} memberId={memberId} />
            </div>
          </>
        )}

        {!isLoading && !error && !calendarData && (
          <div className="bg-white rounded-lg shadow p-8 text-center text-gray-600">
            No data available
          </div>
        )}
      </div>
    </div>
  );
}
