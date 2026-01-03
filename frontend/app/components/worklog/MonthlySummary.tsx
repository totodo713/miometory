"use client";

import { useEffect, useState } from "react";
import { api } from "../../services/api";

export interface ProjectSummary {
  projectId: string;
  projectName: string;
  totalHours: number;
  percentage: number;
}

export interface MonthlySummaryData {
  year: number;
  month: number;
  totalWorkHours: number;
  totalAbsenceHours: number;
  totalBusinessDays: number;
  projects: ProjectSummary[];
}

export interface MonthlySummaryProps {
  year: number;
  month: number;
  memberId: string;
}

/**
 * MonthlySummary component (US2: T069).
 *
 * Displays project breakdown table with hours and percentage for the month.
 */
export function MonthlySummary({ year, month, memberId }: MonthlySummaryProps) {
  const [summary, setSummary] = useState<MonthlySummaryData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function loadSummary() {
      try {
        setIsLoading(true);
        setError(null);

        // TODO: Replace with actual API call when backend is ready (T071)
        // const data = await api.worklog.getMonthlySummary({ year, month, memberId });

        // Mock data for now
        const mockData: MonthlySummaryData = {
          year,
          month,
          totalWorkHours: 160,
          totalAbsenceHours: 8,
          totalBusinessDays: 21,
          projects: [
            {
              projectId: "proj-001",
              projectName: "Project Alpha",
              totalHours: 80,
              percentage: 50,
            },
            {
              projectId: "proj-002",
              projectName: "Project Beta",
              totalHours: 60,
              percentage: 37.5,
            },
            {
              projectId: "proj-003",
              projectName: "Internal Tasks",
              totalHours: 20,
              percentage: 12.5,
            },
          ],
        };

        setSummary(mockData);
      } catch (err) {
        setError(
          err instanceof Error ? err.message : "Failed to load monthly summary",
        );
      } finally {
        setIsLoading(false);
      }
    }

    loadSummary();
  }, [year, month, memberId]);

  if (isLoading) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <div className="animate-pulse">
          <div className="h-6 bg-gray-200 rounded w-1/4 mb-4" />
          <div className="space-y-3">
            <div className="h-4 bg-gray-200 rounded" />
            <div className="h-4 bg-gray-200 rounded" />
            <div className="h-4 bg-gray-200 rounded" />
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <div className="text-red-600">
          <p className="font-medium">Error loading summary</p>
          <p className="text-sm mt-1">{error}</p>
        </div>
      </div>
    );
  }

  if (!summary) {
    return null;
  }

  return (
    <div className="bg-white rounded-lg shadow p-6">
      {/* Header */}
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-gray-900">Monthly Summary</h2>
        <p className="text-sm text-gray-600 mt-1">
          {new Date(year, month - 1).toLocaleString("en-US", {
            month: "long",
            year: "numeric",
          })}
        </p>
      </div>

      {/* Overall Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <div className="bg-blue-50 rounded-lg p-4">
          <div className="text-sm text-blue-600 font-medium">Work Hours</div>
          <div className="text-2xl font-bold text-blue-900 mt-1">
            {summary.totalWorkHours}h
          </div>
        </div>
        <div className="bg-green-50 rounded-lg p-4">
          <div className="text-sm text-green-600 font-medium">
            Business Days
          </div>
          <div className="text-2xl font-bold text-green-900 mt-1">
            {summary.totalBusinessDays}
          </div>
        </div>
        <div className="bg-gray-50 rounded-lg p-4">
          <div className="text-sm text-gray-600 font-medium">Absence Hours</div>
          <div className="text-2xl font-bold text-gray-900 mt-1">
            {summary.totalAbsenceHours}h
          </div>
        </div>
      </div>

      {/* Project Breakdown Table */}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-gray-200">
              <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">
                Project
              </th>
              <th className="text-right py-3 px-4 text-sm font-semibold text-gray-700">
                Hours
              </th>
              <th className="text-right py-3 px-4 text-sm font-semibold text-gray-700">
                Percentage
              </th>
              <th className="py-3 px-4 text-sm font-semibold text-gray-700">
                Distribution
              </th>
            </tr>
          </thead>
          <tbody>
            {summary.projects.map((project) => (
              <tr
                key={project.projectId}
                className="border-b border-gray-100 hover:bg-gray-50"
              >
                <td className="py-3 px-4 text-sm text-gray-900">
                  {project.projectName}
                </td>
                <td className="py-3 px-4 text-sm text-right font-medium text-gray-900">
                  {project.totalHours.toFixed(2)}h
                </td>
                <td className="py-3 px-4 text-sm text-right text-gray-600">
                  {project.percentage.toFixed(1)}%
                </td>
                <td className="py-3 px-4">
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div
                      className="bg-blue-600 h-2 rounded-full"
                      style={{ width: `${project.percentage}%` }}
                    />
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t-2 border-gray-300">
              <td className="py-3 px-4 text-sm font-semibold text-gray-900">
                Total
              </td>
              <td className="py-3 px-4 text-sm text-right font-bold text-gray-900">
                {summary.totalWorkHours.toFixed(2)}h
              </td>
              <td className="py-3 px-4 text-sm text-right font-semibold text-gray-900">
                100%
              </td>
              <td className="py-3 px-4" />
            </tr>
          </tfoot>
        </table>
      </div>

      {/* Empty State */}
      {summary.projects.length === 0 && (
        <div className="text-center py-8">
          <p className="text-gray-500">No project hours recorded this month</p>
        </div>
      )}
    </div>
  );
}
