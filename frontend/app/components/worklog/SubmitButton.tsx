"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/services/api";
import { useProxyMode } from "@/services/worklogStore";
import type { ApprovalStatus } from "@/types/approval";

interface SubmitButtonProps {
  memberId: string;
  fiscalMonthStart: string;
  fiscalMonthEnd: string;
  approvalStatus: ApprovalStatus | null;
  submittedBy?: string;
  onSubmitSuccess?: () => void;
}

/**
 * Button to submit a fiscal month for approval.
 * Disabled if month is already submitted/approved/rejected.
 * Shows loading state during submission.
 */
export function SubmitButton({
  memberId,
  fiscalMonthStart,
  fiscalMonthEnd,
  approvalStatus,
  submittedBy,
  onSubmitSuccess,
}: SubmitButtonProps) {
  const { isProxyMode, managerId } = useProxyMode();
  const t = useTranslations("worklog.submitButton");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit = !approvalStatus || approvalStatus === "REJECTED";
  const isSubmitted = approvalStatus === "SUBMITTED";
  const isApproved = approvalStatus === "APPROVED";

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setError(null);

    try {
      await api.approval.submitMonth({
        memberId,
        fiscalMonthStart,
        fiscalMonthEnd,
        submittedBy: isProxyMode && managerId ? managerId : (submittedBy ?? memberId),
      });

      onSubmitSuccess?.();
    } catch (err) {
      // biome-ignore lint/suspicious/noConsole: log API failure for debugging
      console.error("Failed to submit month:", err);
      setError(err instanceof Error ? err.message : "Failed to submit for approval");
    } finally {
      setIsSubmitting(false);
    }
  };

  const getButtonText = () => {
    const proxyPrefix = isProxyMode ? "(Proxy) " : "";
    if (isSubmitting) return `${proxyPrefix}${t("submit")}...`;
    if (isApproved) return t("submitted");
    if (isSubmitted) return t("submitted");
    if (approvalStatus === "REJECTED") return `${proxyPrefix}${t("resubmit")}`;
    return `${proxyPrefix}${t("submit")}`;
  };

  const getButtonStyle = () => {
    if (isApproved) return "bg-green-700 cursor-not-allowed";
    if (isSubmitted) return "bg-blue-600 cursor-not-allowed";
    if (!canSubmit) return "bg-gray-400 cursor-not-allowed";
    return "bg-blue-600 hover:bg-blue-700";
  };

  return (
    <div className="flex flex-col gap-2">
      <button
        type="button"
        onClick={handleSubmit}
        disabled={!canSubmit || isSubmitting}
        aria-label={getButtonText()}
        aria-busy={isSubmitting}
        className={`px-4 py-2 text-white rounded font-medium transition-colors ${getButtonStyle()}`}
      >
        {getButtonText()}
      </button>
      {error && <p className="text-sm text-red-600">{error}</p>}
      {approvalStatus === "REJECTED" && <p className="text-sm text-amber-600">{t("error")}</p>}
    </div>
  );
}
