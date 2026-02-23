"use client";

/**
 * Copy Previous Month Dialog Component
 *
 * Modal dialog that shows projects from the previous fiscal month
 * and allows users to copy them as templates for the current month.
 *
 * Task: T150 - Implement confirmation dialog with project preview
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/services/api";

interface CopyPreviousMonthDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (projectIds: string[]) => void;
  year: number;
  month: number;
  memberId: string;
}

interface PreviousMonthData {
  projectIds: string[];
  previousMonthStart: string;
  previousMonthEnd: string;
  count: number;
}

export function CopyPreviousMonthDialog({
  isOpen,
  onClose,
  onConfirm,
  year,
  month,
  memberId,
}: CopyPreviousMonthDialogProps) {
  const [data, setData] = useState<PreviousMonthData | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selectedProjectIds, setSelectedProjectIds] = useState<Set<string>>(new Set());
  const dialogRef = useRef<HTMLDivElement>(null);

  // Handle escape key to close dialog
  useEffect(() => {
    if (!isOpen) return;

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onClose();
      }

      // Focus trap: Tab key cycles within dialog
      if (event.key === "Tab" && dialogRef.current) {
        const focusableElements = dialogRef.current.querySelectorAll<HTMLElement>(
          'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
        );
        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (event.shiftKey) {
          // Shift+Tab: if on first element, go to last
          if (document.activeElement === firstElement) {
            event.preventDefault();
            lastElement?.focus();
          }
        } else {
          // Tab: if on last element, go to first
          if (document.activeElement === lastElement) {
            event.preventDefault();
            firstElement?.focus();
          }
        }
      }
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  // Focus first focusable element when dialog opens
  useEffect(() => {
    if (isOpen && dialogRef.current) {
      const focusableElements = dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
      );
      focusableElements[0]?.focus();
    }
  }, [isOpen]);

  const loadPreviousMonthProjects = useCallback(async () => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await api.worklog.getPreviousMonthProjects({
        year,
        month,
        memberId,
      });
      setData(response);
      // Select all projects by default
      setSelectedProjectIds(new Set(response.projectIds));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load previous month projects");
    } finally {
      setIsLoading(false);
    }
  }, [year, month, memberId]);

  useEffect(() => {
    if (isOpen) {
      loadPreviousMonthProjects();
    }
  }, [isOpen, loadPreviousMonthProjects]);

  function handleToggleProject(projectId: string) {
    setSelectedProjectIds((prev) => {
      const next = new Set(prev);
      if (next.has(projectId)) {
        next.delete(projectId);
      } else {
        next.add(projectId);
      }
      return next;
    });
  }

  function handleSelectAll() {
    if (data) {
      setSelectedProjectIds(new Set(data.projectIds));
    }
  }

  function handleDeselectAll() {
    setSelectedProjectIds(new Set());
  }

  function handleConfirm() {
    onConfirm(Array.from(selectedProjectIds));
    onClose();
  }

  if (!isOpen) {
    return null;
  }

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleDateString("en-US", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  };

  return (
    <div
      ref={dialogRef}
      className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="copy-month-dialog-title"
    >
      <div className="bg-white rounded-lg shadow-xl max-w-md w-full mx-4">
        {/* Header */}
        <div className="px-6 py-4 border-b border-gray-200">
          <h2 id="copy-month-dialog-title" className="text-lg font-semibold text-gray-900">
            Copy from Previous Month
          </h2>
          <p className="mt-1 text-sm text-gray-600">Select projects to copy as templates for this month</p>
        </div>

        {/* Content */}
        <div className="px-6 py-4">
          {isLoading && (
            <div className="text-center py-8">
              <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600 mx-auto" />
              <p className="mt-2 text-sm text-gray-600">Loading projects...</p>
            </div>
          )}

          {error && (
            <div className="bg-red-50 border border-red-200 rounded-md p-4">
              <p className="text-sm text-red-800">{error}</p>
              <button
                type="button"
                onClick={loadPreviousMonthProjects}
                className="mt-2 text-sm text-red-600 hover:text-red-800 underline"
              >
                Try again
              </button>
            </div>
          )}

          {!isLoading && !error && data && (
            <>
              {/* Previous month info */}
              <div className="mb-4 text-sm text-gray-600">
                Previous period: {formatDate(data.previousMonthStart)} - {formatDate(data.previousMonthEnd)}
              </div>

              {data.count === 0 ? (
                <div className="text-center py-8 text-gray-600">
                  <p className="text-lg mb-2">No projects found</p>
                  <p className="text-sm">You don't have any work log entries from the previous month.</p>
                </div>
              ) : (
                <>
                  {/* Select all/none buttons */}
                  <div className="flex gap-2 mb-3">
                    <button
                      type="button"
                      onClick={handleSelectAll}
                      className="text-sm text-blue-600 hover:text-blue-800"
                    >
                      Select all
                    </button>
                    <span className="text-gray-400">|</span>
                    <button
                      type="button"
                      onClick={handleDeselectAll}
                      className="text-sm text-blue-600 hover:text-blue-800"
                    >
                      Deselect all
                    </button>
                  </div>

                  {/* Project list */}
                  <div className="max-h-60 overflow-y-auto border border-gray-200 rounded-md">
                    {data.projectIds.map((projectId) => (
                      <label
                        key={projectId}
                        className="flex items-center px-3 py-2 hover:bg-gray-50 cursor-pointer border-b border-gray-100 last:border-b-0"
                      >
                        <input
                          type="checkbox"
                          checked={selectedProjectIds.has(projectId)}
                          onChange={() => handleToggleProject(projectId)}
                          className="h-4 w-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500"
                        />
                        <span className="ml-3 text-sm text-gray-700 font-mono">{projectId.substring(0, 8)}...</span>
                      </label>
                    ))}
                  </div>

                  {/* Selection count */}
                  <div className="mt-2 text-sm text-gray-600">
                    {selectedProjectIds.size} of {data.count} projects selected
                  </div>
                </>
              )}
            </>
          )}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-gray-200 flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={isLoading || selectedProjectIds.size === 0}
            className="px-4 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Copy {selectedProjectIds.size > 0 ? `(${selectedProjectIds.size})` : ""}
          </button>
        </div>
      </div>
    </div>
  );
}
