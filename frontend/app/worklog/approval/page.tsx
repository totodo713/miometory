"use client";

/**
 * Manager Approval Queue Page
 *
 * Displays pending monthly submissions awaiting manager review.
 * Allows managers to approve or reject submissions with feedback.
 */

import { useEffect, useState } from "react";
import { api } from "@/services/api";
import type { PendingApproval } from "@/types/approval";

export default function ApprovalPage() {
  const [pendingApprovals, setPendingApprovals] = useState<PendingApproval[]>(
    [],
  );
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionInProgress, setActionInProgress] = useState<string | null>(null);
  const [rejectionReason, setRejectionReason] = useState<string>("");
  const [rejectingApprovalId, setRejectingApprovalId] = useState<string | null>(
    null,
  );

  // TODO: Get actual manager ID from auth context
  const managerId = "00000000-0000-0000-0000-000000000002";

  const loadApprovalQueue = async () => {
    setIsLoading(true);
    setError(null);

    try {
      const data = await api.approval.getApprovalQueue(managerId);
      setPendingApprovals(data.pendingApprovals);
    } catch (err) {
      console.error("Failed to load approval queue:", err);
      setError(
        err instanceof Error ? err.message : "Failed to load approval queue",
      );
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadApprovalQueue();
  }, []);

  const handleApprove = async (approvalId: string) => {
    setActionInProgress(approvalId);

    try {
      await api.approval.approveMonth(approvalId, managerId);
      // Reload queue after approval
      await loadApprovalQueue();
    } catch (err) {
      console.error("Failed to approve month:", err);
      alert(
        err instanceof Error ? err.message : "Failed to approve submission",
      );
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
          <h1 className="text-3xl font-bold text-gray-900 mb-6">
            Approval Queue
          </h1>
          <div className="bg-white rounded-lg shadow p-8 text-center">
            <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto" />
            <p className="mt-4 text-gray-600">Loading pending approvals...</p>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
          <h1 className="text-3xl font-bold text-gray-900 mb-6">
            Approval Queue
          </h1>
          <div className="bg-red-50 border border-red-200 rounded-lg p-4">
            <p className="text-red-800">Error: {error}</p>
            <button
              type="button"
              onClick={loadApprovalQueue}
              className="mt-4 px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700"
            >
              Retry
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
            <h1 className="text-3xl font-bold text-gray-900">Approval Queue</h1>
            <p className="mt-1 text-sm text-gray-600">
              Review and approve submitted monthly work logs
            </p>
          </div>
          <button
            type="button"
            onClick={loadApprovalQueue}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
          >
            Refresh
          </button>
        </div>

        {/* Queue List */}
        {pendingApprovals.length === 0 ? (
          <div className="bg-white rounded-lg shadow p-8 text-center">
            <p className="text-gray-600">No pending approvals at this time.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {pendingApprovals.map((approval) => (
              <div
                key={approval.approvalId}
                className="bg-white rounded-lg shadow p-6"
              >
                <div className="flex items-start justify-between">
                  {/* Submission Info */}
                  <div className="flex-1">
                    <h3 className="text-lg font-semibold text-gray-900">
                      {approval.memberName}
                    </h3>
                    <p className="text-sm text-gray-600 mt-1">
                      Period:{" "}
                      {new Date(approval.fiscalMonthStart).toLocaleDateString()}{" "}
                      - {new Date(approval.fiscalMonthEnd).toLocaleDateString()}
                    </p>
                    <div className="mt-3 flex items-center gap-4 text-sm">
                      <div className="flex items-center gap-1">
                        <span className="text-gray-600">Work:</span>
                        <span className="font-semibold">
                          {approval.totalWorkHours}h
                        </span>
                      </div>
                      <div className="flex items-center gap-1">
                        <span className="text-gray-600">Absence:</span>
                        <span className="font-semibold">
                          {approval.totalAbsenceHours}h
                        </span>
                      </div>
                      <div className="flex items-center gap-1">
                        <span className="text-gray-600">Total:</span>
                        <span className="font-bold">
                          {(
                            approval.totalWorkHours + approval.totalAbsenceHours
                          ).toFixed(2)}
                          h
                        </span>
                      </div>
                    </div>
                    <p className="text-xs text-gray-500 mt-2">
                      Submitted by {approval.submittedByName} on{" "}
                      {new Date(approval.submittedAt).toLocaleString()}
                    </p>
                  </div>

                  {/* Action Buttons */}
                  <div className="flex items-center gap-2 ml-4">
                    <button
                      type="button"
                      onClick={() => handleApprove(approval.approvalId)}
                      disabled={actionInProgress === approval.approvalId}
                      className="px-4 py-2 bg-green-600 text-white rounded hover:bg-green-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
                    >
                      {actionInProgress === approval.approvalId
                        ? "Approving..."
                        : "Approve"}
                    </button>
                    <button
                      type="button"
                      onClick={() => handleRejectClick(approval.approvalId)}
                      disabled={actionInProgress === approval.approvalId}
                      className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
                    >
                      Reject
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Rejection Dialog */}
      {rejectingApprovalId && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-lg p-6 max-w-md w-full">
            <h2 className="text-xl font-semibold text-gray-900 mb-4">
              Reject Submission
            </h2>
            <p className="text-sm text-gray-600 mb-4">
              Please provide a reason for rejection (required):
            </p>
            <textarea
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              rows={4}
              className="w-full px-3 py-2 border rounded-md"
              placeholder="Explain why this submission is being rejected..."
              maxLength={1000}
            />
            <p className="text-xs text-gray-500 mt-1">
              {rejectionReason.length}/1000 characters
            </p>
            <div className="mt-6 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setRejectingApprovalId(null);
                  setRejectionReason("");
                }}
                className="px-4 py-2 bg-gray-200 text-gray-800 rounded hover:bg-gray-300"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleRejectSubmit}
                disabled={!rejectionReason.trim()}
                className="px-4 py-2 bg-red-600 text-white rounded hover:bg-red-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
              >
                Confirm Rejection
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
