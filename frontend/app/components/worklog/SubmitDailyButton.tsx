"use client";

import { useTranslations } from "next-intl";
import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, api } from "../../services/api";
import { useCalendarRefresh, useProxyMode } from "../../services/worklogStore";

interface SubmitDailyButtonProps {
  date: Date;
  memberId: string;
  submittedBy?: string;
  hasDraftEntries: boolean;
  hasSubmittedEntries: boolean;
  hasUnsavedChanges: boolean;
  draftEntryCount: number;
  draftTotalHours: number;
  wasRejected?: boolean;
  onSaveFirst: () => Promise<void>;
  onSubmitSuccess: () => void;
}

export function SubmitDailyButton({
  date,
  memberId,
  submittedBy,
  hasDraftEntries,
  hasSubmittedEntries,
  hasUnsavedChanges,
  draftEntryCount,
  draftTotalHours,
  wasRejected,
  onSaveFirst,
  onSubmitSuccess,
}: SubmitDailyButtonProps) {
  const { isProxyMode, managerId } = useProxyMode();
  const t = useTranslations("worklog.submitDaily");
  const tc = useTranslations("common");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRecalling, setIsRecalling] = useState(false);
  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitSuccess, setSubmitSuccess] = useState<string | null>(null);
  const { triggerRefresh } = useCalendarRefresh();
  const dialogRef = useRef<HTMLDivElement>(null);
  const dismissTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Handle escape key and focus trap for confirmation dialog
  const handleKeyDown = useCallback((event: KeyboardEvent) => {
    if (event.key === "Escape") {
      setShowConfirmDialog(false);
    }

    if (event.key === "Tab" && dialogRef.current) {
      const focusableElements = dialogRef.current.querySelectorAll<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      );
      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];

      if (event.shiftKey) {
        if (document.activeElement === firstElement) {
          event.preventDefault();
          lastElement?.focus();
        }
      } else {
        if (document.activeElement === lastElement) {
          event.preventDefault();
          firstElement?.focus();
        }
      }
    }
  }, []);

  useEffect(() => {
    if (!showConfirmDialog) return;
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [showConfirmDialog, handleKeyDown]);

  // Focus first button when dialog opens
  useEffect(() => {
    if (showConfirmDialog && dialogRef.current) {
      const focusableElements = dialogRef.current.querySelectorAll<HTMLElement>("button");
      focusableElements[0]?.focus();
    }
  }, [showConfirmDialog]);

  // Cleanup dismiss timer on unmount
  useEffect(() => {
    return () => {
      if (dismissTimerRef.current) {
        clearTimeout(dismissTimerRef.current);
      }
    };
  }, []);

  const scheduleDismiss = () => {
    if (dismissTimerRef.current) {
      clearTimeout(dismissTimerRef.current);
    }
    dismissTimerRef.current = setTimeout(() => setSubmitSuccess(null), 3000);
  };

  const handleSubmit = async () => {
    try {
      setShowConfirmDialog(false);
      setIsSubmitting(true);
      setSubmitError(null);
      setSubmitSuccess(null);

      // Save unsaved changes first
      if (hasUnsavedChanges) {
        await onSaveFirst();
      }

      const dateStr = date.toISOString().split("T")[0];
      const effectiveSubmittedBy = isProxyMode && managerId ? managerId : (submittedBy ?? memberId);
      const result = await api.worklog.submitDailyEntries({
        memberId,
        date: dateStr,
        submittedBy: effectiveSubmittedBy,
      });

      setSubmitSuccess(`${result.submittedCount} entries submitted successfully.`);
      triggerRefresh();
      onSubmitSuccess();
      scheduleDismiss();
    } catch (error: unknown) {
      if (error instanceof ApiError && (error.status === 409 || error.status === 412)) {
        setSubmitError("Entries were modified by another session. Please refresh and try again.");
      } else if (error instanceof Error) {
        setSubmitError(error.message);
      } else {
        setSubmitError("Failed to submit entries. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleRecall = async () => {
    try {
      setIsRecalling(true);
      setSubmitError(null);
      setSubmitSuccess(null);

      const dateStr = date.toISOString().split("T")[0];
      const effectiveRecalledBy = isProxyMode && managerId ? managerId : (submittedBy ?? memberId);
      const result = await api.worklog.recallDailyEntries({
        memberId,
        date: dateStr,
        recalledBy: effectiveRecalledBy,
      });

      setSubmitSuccess(`${result.recalledCount} entries recalled to draft.`);
      triggerRefresh();
      onSubmitSuccess();
      scheduleDismiss();
    } catch (error: unknown) {
      if (error instanceof ApiError && error.status === 422) {
        setSubmitError("Manager has already acted on this submission. Recall is no longer available.");
      } else if (error instanceof ApiError && (error.status === 409 || error.status === 412)) {
        setSubmitError("Entries were modified by another session. Please refresh and try again.");
      } else if (error instanceof Error) {
        setSubmitError(error.message);
      } else {
        setSubmitError("Failed to recall entries. Please try again.");
      }
    } finally {
      setIsRecalling(false);
    }
  };

  // Show recall button when no drafts but there are submitted entries
  if (!hasDraftEntries && hasSubmittedEntries) {
    return (
      <div className="inline-flex flex-col items-end">
        <button
          type="button"
          onClick={handleRecall}
          disabled={isRecalling}
          className="px-4 py-2 bg-amber-500 text-white rounded-md hover:bg-amber-600 disabled:bg-gray-400 disabled:cursor-not-allowed"
        >
          {isRecalling ? `${t("submit")}...` : isProxyMode ? `(Proxy) ${t("submit")}` : t("submit")}
        </button>
        {submitError && (
          <div role="alert" aria-live="polite" className="text-red-600 text-sm mt-1 max-w-xs text-right">
            {submitError}
          </div>
        )}
        {submitSuccess && (
          <div role="alert" aria-live="polite" className="text-green-600 text-sm mt-1">
            {submitSuccess}
          </div>
        )}
      </div>
    );
  }

  if (!hasDraftEntries) {
    return null;
  }

  return (
    <>
      <div className="inline-flex flex-col items-end">
        <button
          type="button"
          onClick={() => setShowConfirmDialog(true)}
          disabled={isSubmitting}
          className="px-4 py-2 bg-green-700 text-white rounded-md hover:bg-green-800 disabled:bg-gray-400 disabled:cursor-not-allowed"
        >
          {isSubmitting
            ? `${t("submit")}...`
            : `${isProxyMode ? "(Proxy) " : ""}${wasRejected ? t("submit") : t("submit")}`}
        </button>
        {submitError && (
          <div role="alert" aria-live="polite" className="text-red-600 text-sm mt-1 max-w-xs text-right">
            {submitError}
          </div>
        )}
        {submitSuccess && (
          <div role="alert" aria-live="polite" className="text-green-600 text-sm mt-1">
            {submitSuccess}
          </div>
        )}
      </div>

      {/* Confirmation Dialog */}
      {showConfirmDialog && (
        <div
          ref={dialogRef}
          className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
          role="dialog"
          aria-modal="true"
          aria-labelledby="submit-confirm-title"
        >
          <div className="bg-white rounded-lg shadow-xl max-w-sm w-full mx-4">
            <div className="px-6 py-4 border-b border-gray-200">
              <h2 id="submit-confirm-title" className="text-lg font-semibold text-gray-900">
                {t("confirmTitle")}
              </h2>
            </div>
            <div className="px-6 py-4">
              <p className="text-sm text-gray-700">
                You are about to submit <span className="font-semibold">{draftEntryCount}</span>{" "}
                {draftEntryCount === 1 ? "entry" : "entries"} totaling{" "}
                <span className="font-semibold">{draftTotalHours}</span> hours for{" "}
                <span className="font-semibold">{date.toISOString().split("T")[0]}</span>.
              </p>
              <p className="text-sm text-gray-500 mt-2">{t("confirmMessage")}</p>
            </div>
            <div className="px-6 py-4 border-t border-gray-200 flex justify-end gap-3">
              <button
                type="button"
                onClick={() => setShowConfirmDialog(false)}
                className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
              >
                {tc("cancel")}
              </button>
              <button
                type="button"
                onClick={handleSubmit}
                className="px-4 py-2 text-sm font-medium text-white bg-green-700 rounded-md hover:bg-green-800"
              >
                {t("submit")}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
