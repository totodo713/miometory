"use client";

import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { EmptyState } from "@/components/shared/EmptyState";
import { Skeleton } from "@/components/shared/Skeleton";
import { api } from "../../services/api";
import { RejectionBanner } from "./RejectionBanner";
import { SubmitButton } from "./SubmitButton";

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
  approvalStatus: "PENDING" | "SUBMITTED" | "APPROVED" | "REJECTED" | null;
  rejectionReason: string | null;
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
  const format = useFormatter();
  const t = useTranslations("worklog.monthlySummary");
  const tc = useTranslations("common");
  const [summary, setSummary] = useState<MonthlySummaryData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadSummary = useCallback(async () => {
    try {
      setIsLoading(true);
      setError(null);

      // Call real API (T072)
      const data = await api.worklog.getMonthlySummary({
        year,
        month,
        memberId,
      });

      setSummary(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load monthly summary");
    } finally {
      setIsLoading(false);
    }
  }, [year, month, memberId]);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  if (isLoading) {
    return (
      <div className="bg-white rounded-lg shadow p-6">
        <Skeleton.Text lines={2} />
        <div className="mt-4 grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <Skeleton.Card />
          <Skeleton.Card />
          <Skeleton.Card />
        </div>
        <Skeleton.Table rows={5} cols={4} />
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
      {/* Header with Approval Status */}
      <div className="mb-6 flex items-start justify-between">
        <div>
          <h2 className="text-xl font-semibold text-gray-900">{t("title")}</h2>
          <p className="text-sm text-gray-600 mt-1">
            {format.dateTime(new Date(year, month - 1), {
              month: "long",
              year: "numeric",
            })}
          </p>
        </div>

        {/* Approval Status Badge */}
        {summary.approvalStatus && (
          <div className="flex flex-col items-end gap-2">
            <span
              className={`px-3 py-1 rounded-full text-sm font-medium ${
                summary.approvalStatus === "APPROVED"
                  ? "bg-green-100 text-green-800"
                  : summary.approvalStatus === "SUBMITTED"
                    ? "bg-blue-100 text-blue-800"
                    : summary.approvalStatus === "REJECTED"
                      ? "bg-red-100 text-red-800"
                      : "bg-gray-100 text-gray-800"
              }`}
            >
              {summary.approvalStatus}
            </span>
          </div>
        )}
      </div>

      {/* Rejection Banner */}
      {summary.approvalStatus === "REJECTED" && summary.rejectionReason && (
        <div className="mb-6">
          <RejectionBanner rejectionReason={summary.rejectionReason} rejectionSource="monthly" />
        </div>
      )}

      {/* Submit Button */}
      <div className="mb-6">
        <SubmitButton
          memberId={memberId}
          fiscalMonthStart={`${year}-${String(month).padStart(2, "0")}-01`}
          fiscalMonthEnd={new Date(year, month, 0).toISOString().split("T")[0]}
          approvalStatus={summary.approvalStatus}
          onSubmitSuccess={() => {
            // Reload summary to get updated approval status
            loadSummary();
          }}
        />
      </div>

      {/* Overall Stats */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <div className="bg-blue-50 rounded-lg p-4 border border-blue-100">
          <div className="text-sm text-blue-600 font-medium">{t("totalHours")}</div>
          <div className="text-2xl font-bold text-blue-900 mt-1">{summary.totalWorkHours}h</div>
          <div className="text-xs text-blue-600 mt-1">
            {summary.projects.length} project
            {summary.projects.length !== 1 ? "s" : ""}
          </div>
        </div>
        <div className="bg-sky-50 rounded-lg p-4 border border-sky-200">
          <div className="flex items-center gap-1 text-sm text-sky-700 font-medium">
            <span>🏖️</span>
            <span>{t("absenceDays")}</span>
          </div>
          <div className="text-2xl font-bold text-sky-900 mt-1">{summary.totalAbsenceHours}h</div>
          <div className="text-xs text-sky-700 mt-1">Time away from work</div>
        </div>
        <div className="bg-green-50 rounded-lg p-4 border border-green-100">
          <div className="text-sm text-green-700 font-medium">{t("workingDays")}</div>
          <div className="text-2xl font-bold text-green-900 mt-1">{summary.totalBusinessDays}</div>
          <div className="text-xs text-green-700 mt-1">In this period</div>
        </div>
      </div>

      {/* Combined Hours Summary */}
      {(summary.totalWorkHours > 0 || summary.totalAbsenceHours > 0) && (
        <div className="mb-6 p-4 bg-gray-50 rounded-lg border border-gray-200">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-semibold text-gray-700">Total Hours Recorded</h3>
              <p className="text-xs text-gray-500 mt-0.5">Work + Absence combined</p>
            </div>
            <div className="text-2xl font-bold text-gray-900">
              {(summary.totalWorkHours + summary.totalAbsenceHours).toFixed(2)}h
            </div>
          </div>
          {summary.totalWorkHours > 0 && summary.totalAbsenceHours > 0 && (
            <div className="mt-3 flex items-center gap-4 text-sm">
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 bg-blue-500 rounded-full" />
                <span className="text-gray-600">
                  Work: {summary.totalWorkHours}h (
                  {((summary.totalWorkHours / (summary.totalWorkHours + summary.totalAbsenceHours)) * 100).toFixed(1)}
                  %)
                </span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 bg-sky-500 rounded-full" />
                <span className="text-gray-600">
                  Absence: {summary.totalAbsenceHours}h (
                  {((summary.totalAbsenceHours / (summary.totalWorkHours + summary.totalAbsenceHours)) * 100).toFixed(
                    1,
                  )}
                  %)
                </span>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Project Breakdown Table */}
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead>
            <tr className="border-b border-gray-200">
              <th className="text-left py-3 px-4 text-sm font-semibold text-gray-700">{tc("name")}</th>
              <th className="text-right py-3 px-4 text-sm font-semibold text-gray-700">{t("totalHours")}</th>
              <th className="text-right py-3 px-4 text-sm font-semibold text-gray-700">%</th>
              <th className="py-3 px-4 text-sm font-semibold text-gray-700">&nbsp;</th>
            </tr>
          </thead>
          <tbody>
            {summary.projects.map((project) => (
              <tr key={project.projectId} className="border-b border-gray-100 hover:bg-gray-50">
                <td className="py-3 px-4 text-sm text-gray-900">{project.projectName}</td>
                <td className="py-3 px-4 text-sm text-right font-medium text-gray-900">
                  {project.totalHours.toFixed(2)}h
                </td>
                <td className="py-3 px-4 text-sm text-right text-gray-600">{project.percentage.toFixed(1)}%</td>
                <td className="py-3 px-4">
                  <div className="w-full bg-gray-200 rounded-full h-2">
                    <div className="bg-blue-600 h-2 rounded-full" style={{ width: `${project.percentage}%` }} />
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t-2 border-gray-300">
              <td className="py-3 px-4 text-sm font-semibold text-gray-900">{tc("total")}</td>
              <td className="py-3 px-4 text-sm text-right font-bold text-gray-900">
                {summary.totalWorkHours.toFixed(2)}h
              </td>
              <td className="py-3 px-4 text-sm text-right font-semibold text-gray-900">100%</td>
              <td className="py-3 px-4" />
            </tr>
          </tfoot>
        </table>
      </div>

      {/* Empty State */}
      {summary.projects.length === 0 && <EmptyState title={t("noData")} description={t("noData")} />}
    </div>
  );
}
