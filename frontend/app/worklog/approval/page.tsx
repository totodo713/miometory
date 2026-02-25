"use client";

/**
 * Manager {t("title")} Page
 *
 * Displays pending monthly submissions awaiting manager review.
 * Allows managers to approve or reject submissions with feedback.
 */

import { useFormatter, useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { MonthlyApprovalSummaryView } from "@/components/worklog/MonthlyApprovalSummary";
import { api } from "@/services/api";
import type { PendingApproval } from "@/types/approval";

export default function ApprovalPage() {
  const format = useFormatter();
  const t = useTranslations("worklog.monthlyApproval");
  const tc = useTranslations("common");
  const [pendingApprovals, setPendingApprovals] = useState<PendingApproval[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);
  const [rejectionReason, setRejectionReason] = useState<string>("");
  const [rejectingApprovalId, setRejectingApprovalId] = useState<string | null>(null);
  const [detailData, setDetailData] = useState<Awaited<ReturnType<typeof api.approval.getDetail>> | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // TODO: Get actual manager ID from auth context
  const managerId = "00000000-0000-0000-0000-000000000002";

  const handleViewDetail = async (approvalId: string) => {
    setDetailLoading(true);
    try {
      const detail = await api.approval.getDetail(approvalId);
      setDetailData(detail);
    } catch {
      setDetailData(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const loadApprovalQueue = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const data = await api.approval.getApprovalQueue(managerId);
      setPendingApprovals(data.pendingApprovals);
    } catch (err) {
      // biome-ignore lint/suspicious/noConsole: log API failure for debugging
      console.error("Failed to load approval queue:", err);
      setError(err instanceof Error ? err.message : "Failed to load approval queue");
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadApprovalQueue();
  }, [loadApprovalQueue]);

  const handleApprove = async (approvalId: string) => {
    setActionInProgress(approvalId);

    try {
      await api.approval.approveMonth(approvalId, managerId);
      // Reload queue after approval
      await loadApprovalQueue();
    } catch (err) {
      // biome-ignore lint/suspicious/noConsole: log API failure for debugging
      console.error("Failed to approve month:", err);
      alert(err instanceof Error ? err.message : "Failed to approve submission");
    } finally {
      setActionInProgress(null);
    }
  };

  const handleRejectClick = (approvalId: string) => {
    setRejectingApprovalId(approvalId);
    setRejectionReason("");
  };

  const handleRejectSubmit = async () => {
    if (!rejectingApprovalId) return;

    if (!rejectionReason.trim()) {
      alert("Please provide a rejection reason");
      return;
    }

    if (rejectionReason.length > 1000) {
      alert("Rejection reason must not exceed 1000 characters");
      return;
    }

    setActionInProgress(rejectingApprovalId);

    try {
      await api.approval.rejectMonth(rejectingApprovalId, {
        reviewedBy: managerId,
        rejectionReason: rejectionReason.trim(),
      });
      // Clear rejection dialog
      setRejectingApprovalId(null);
      setRejectionReason("");
      // Reload queue after rejection
      await loadApprovalQueue();
    } catch (err) {
      // biome-ignore lint/suspicious/noConsole: log API failure for debugging
      console.error("Failed to reject month:", err);
      alert(err instanceof Error ? err.message : "Failed to reject submission");
    } finally {
      setActionInProgress(null);
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-6">{t("title")}</h1>
          <div className="bg-white rounded-lg shadow p-8 text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto" />
            <p className="mt-4 text-gray-600">{tc("loading")}</p>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-6">{t("title")}</h1>
          <div className="bg-red-50 border border-red-200 rounded-lg p-4">
            <p className="text-red-800">Error: {error}</p>
            <button
              type="button"
              onClick={loadApprovalQueue}
              className="mt-4 px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700"
            >
              {tc("retry")}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Header */}
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">{t("title")}</h1>
            <p className="mt-1 text-sm text-gray-600">{t("title")}</p>
          </div>
          <button
            type="button"
            onClick={loadApprovalQueue}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            {tc("retry")}
          </button>
        </div>

        {/* Queue List */}
        {pendingApprovals.length === 0 ? (
          <div className="bg-white rounded-lg shadow p-8 text-center">
            <p className="text-gray-600">{tc("noData")}</p>
          </div>
        ) : (
          <div className="space-y-4">
            {pendingApprovals.map((approval) => (
              <div key={approval.approvalId} className="bg-white rounded-lg shadow p-6">
                <div className="flex items-start justify-between">
                  {/* Submission Info */}
                  <div className="flex-1">
                    <h3 className="text-lg font-semibold text-gray-900">{approval.memberName}</h3>
                    <p className="text-sm text-gray-600 mt-1">
                      Period:{" "}
                      {format.dateTime(new Date(approval.fiscalMonthStart), {
                        year: "numeric",
                        month: "short",
                        day: "numeric",
                      })}{" "}
                      -{" "}
                      {format.dateTime(new Date(approval.fiscalMonthEnd), {
                        year: "numeric",
                        month: "short",
                        day: "numeric",
                      })}
                    </p>
                    <div className="mt-3 flex items-center gap-4 text-sm">
                      <div className="flex items-center gap-1">
                        <span className="text-gray-600">Work:</span>
                        <span className="font-semibold">{approval.totalWorkHours}h</span>
                      </div>
                      <div className="flex items-center gap-1">
                        <span className="text-gray-600">Absence:</span>
                        <span className="font-semibold">{approval.totalAbsenceHours}h</span>
                      </div>
                      <div className="flex items-center gap-1">
                        <span className="text-gray-600">Total:</span>
                        <span className="font-bold">
                          {(approval.totalWorkHours + approval.totalAbsenceHours).toFixed(2)}h
                        </span>
                      </div>
                    </div>
                    <p className="text-xs text-gray-500 mt-2">
                      Submitted by {approval.submittedByName} on{" "}
                      {format.dateTime(new Date(approval.submittedAt), {
                        year: "numeric",
                        month: "short",
                        day: "numeric",
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </p>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex items-center gap-2 ml-4">
                    <button
                      type="button"
                      onClick={() => handleViewDetail(approval.approvalId)}
                      disabled={detailLoading}
                      className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
                    >
                      {t("title")}
                    </button>
                    <button
                      type="button"
                      onClick={() => handleApprove(approval.approvalId)}
                      disabled={actionInProgress === approval.approvalId}
                      className="px-4 py-2 bg-green-700 text-white rounded hover:bg-green-800 disabled:bg-gray-400 disabled:cursor-not-allowed"
                    >
                      {actionInProgress === approval.approvalId ? `${t("approve")}...` : t("approve")}
                    </button>
                    <button
                      type="button"
                      onClick={() => handleRejectClick(approval.approvalId)}
                      disabled={actionInProgress === approval.approvalId}
                      className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
                    >
                      {t("reject")}
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Detail Modal */}
      {detailData && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 max-w-2xl w-full max-h-[80vh] overflow-y-auto">
            <MonthlyApprovalSummaryView
              memberName={detailData.memberName}
              fiscalMonthStart={detailData.fiscalMonthStart}
              fiscalMonthEnd={detailData.fiscalMonthEnd}
              totalWorkHours={detailData.totalWorkHours}
              totalAbsenceHours={detailData.totalAbsenceHours}
              projectBreakdown={detailData.projectBreakdown}
              dailyApprovalSummary={detailData.dailyApprovalSummary}
              unresolvedEntries={detailData.unresolvedEntries}
              onClose={() => setDetailData(null)}
            />
          </div>
        </div>
      )}

      {/* Rejection Dialog */}
      {rejectingApprovalId && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 max-w-md w-full">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">{t("reject")}</h2>
            <p className="text-sm text-gray-600 mb-4">{t("rejectReason")}</p>
            <textarea
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              rows={4}
              className="w-full px-3 py-2 border rounded-md"
              placeholder={t("rejectReasonPlaceholder")}
              maxLength={1000}
            />
            <p className="text-xs text-gray-500 mt-1">{rejectionReason.length}/1000 characters</p>
            <div className="mt-6 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setRejectingApprovalId(null);
                  setRejectionReason("");
                }}
                className="px-4 py-2 bg-gray-200 text-gray-800 rounded hover:bg-gray-300"
              >
                {tc("cancel")}
              </button>
              <button
                type="button"
                onClick={handleRejectSubmit}
                disabled={!rejectionReason.trim()}
                className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
              >
                {t("rejectConfirm")}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
