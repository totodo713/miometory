"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../../services/api";
import { useProxyMode } from "../../services/worklogStore";
import type { WorkLogEntry, WorkLogStatus } from "../../types/worklog";
import { AbsenceForm } from "./AbsenceForm";

interface DailyEntryFormProps {
  date: Date;
  memberId: string;
  onClose: () => void;
  onSave: () => void;
}

interface ProjectRow {
  id?: string;
  projectId: string;
  hours: number;
  comment: string;
  status?: WorkLogStatus;
  version?: number;
  errors: {
    project?: string;
    hours?: string;
    comment?: string;
  };
}

export function DailyEntryForm({
  date,
  memberId,
  onClose,
  onSave,
}: DailyEntryFormProps) {
  const { isProxyMode, targetMember } = useProxyMode();
  const [activeTab, setActiveTab] = useState<"work" | "absence">("work");
  const [projectRows, setProjectRows] = useState<ProjectRow[]>([
    { projectId: "", hours: 0, comment: "", errors: {} },
  ]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);
  const [autoSavedAt, setAutoSavedAt] = useState<Date | null>(null);
  const [absenceHours, setAbsenceHours] = useState<number>(0);

  const initialDataRef = useRef<string>(
    JSON.stringify([{ projectId: "", hours: 0, comment: "", errors: {} }]),
  );
  const autoSaveTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Calculate if read-only (submitted or approved)
  const isReadOnly = projectRows.some(
    (row) => row.status === "SUBMITTED" || row.status === "APPROVED",
  );

  // Calculate total hours
  const totalWorkHours = projectRows.reduce((sum, row) => sum + row.hours, 0);
  const totalHours = totalWorkHours + absenceHours;
  const totalExceeds24 = totalHours > 24;

  // Load existing entries
  useEffect(() => {
    async function loadEntries() {
      try {
        setIsLoading(true);
        setLoadError(null);
        const dateStr = date.toISOString().split("T")[0]; // Format as YYYY-MM-DD

        // Load work log entries
        const workLogResponse = await api.worklog.getEntries({
          memberId,
          startDate: dateStr,
          endDate: dateStr,
        });

        if (workLogResponse.entries.length > 0) {
          const rows = workLogResponse.entries.map((entry) => ({
            id: entry.id,
            projectId: entry.projectId,
            hours: entry.hours,
            comment: entry.comment || "",
            status: entry.status as WorkLogStatus,
            version: entry.version,
            errors: {},
          }));
          setProjectRows(rows);
          initialDataRef.current = JSON.stringify(rows);
        } else {
          initialDataRef.current = JSON.stringify(projectRows);
        }

        // Load absence entries for the date to calculate total absence hours
        const absenceResponse = await api.absence.getAbsences({
          memberId,
          startDate: dateStr,
          endDate: dateStr,
        });

        // Sum up all absence hours for the day (excluding deleted absences)
        const totalAbsence = absenceResponse.absences.reduce(
          (sum, absence) => sum + absence.hours,
          0,
        );
        setAbsenceHours(totalAbsence);
      } catch (error) {
        setLoadError(
          error instanceof Error
            ? error.message
            : "Error loading entries. Please try again.",
        );
      } finally {
        setIsLoading(false);
      }
    }

    loadEntries();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [memberId, date]);

  // Track unsaved changes
  useEffect(() => {
    const currentData = JSON.stringify(projectRows);
    setHasUnsavedChanges(currentData !== initialDataRef.current);
  }, [projectRows]);

  // Auto-save timer (60 seconds)
  useEffect(() => {
    // Clear existing timer
    if (autoSaveTimerRef.current) {
      clearTimeout(autoSaveTimerRef.current);
    }

    // Don't auto-save if no changes, read-only, or has errors
    if (!hasUnsavedChanges || isReadOnly || hasValidationErrors()) {
      return;
    }

    // Set new timer
    autoSaveTimerRef.current = setTimeout(() => {
      handleSave(true); // Auto-save mode
    }, 60000); // 60 seconds

    return () => {
      if (autoSaveTimerRef.current) {
        clearTimeout(autoSaveTimerRef.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasUnsavedChanges, isReadOnly]);

  // Validation functions
  const validateField = (
    field: "project" | "hours" | "comment",
    value: string | number,
  ): string | undefined => {
    if (field === "project") {
      if (!value) {
        return "Project is required";
      }
    }

    if (field === "hours") {
      const hours =
        typeof value === "number" ? value : Number.parseFloat(value as string);

      if (hours < 0) {
        return "Hours cannot be negative";
      }

      if (hours > 24) {
        return "Hours cannot exceed 24";
      }

      // Check 0.25 increments (quarter hours)
      if (hours % 0.25 !== 0) {
        return "Hours must be in 0.25 increments";
      }
    }

    if (field === "comment") {
      const comment = value as string;
      if (comment.length > 500) {
        return "Comment cannot exceed 500 characters";
      }
    }

    return undefined;
  };

  const hasValidationErrors = (): boolean => {
    return projectRows.some(
      (row) => row.errors.project || row.errors.hours || row.errors.comment,
    );
  };

  // Update project row field
  const updateProjectRow = (
    index: number,
    field: keyof ProjectRow,
    value: string | number,
  ) => {
    setProjectRows((prev) => {
      const updated = [...prev];
      const row = { ...updated[index] };

      // Update the field
      if (field === "projectId" || field === "comment") {
        row[field] = value as string;
      } else if (field === "hours") {
        row.hours = Number(value);
      }

      // Validate the field
      const fieldName = field === "projectId" ? "project" : field;
      const error = validateField(
        fieldName as "project" | "hours" | "comment",
        value,
      );
      row.errors = { ...row.errors, [fieldName]: error };

      updated[index] = row;
      return updated;
    });
  };

  // Add a new project row
  const addProjectRow = () => {
    setProjectRows((prev) => [
      ...prev,
      { projectId: "", hours: 0, comment: "", errors: {} },
    ]);
  };

  // Remove a project row
  const removeProjectRow = (index: number) => {
    if (projectRows.length === 1) return; // Don't remove last row
    setProjectRows((prev) => prev.filter((_, i) => i !== index));
  };

  // Delete an entry (API call)
  const handleDelete = async (entryId: string) => {
    try {
      setIsSaving(true);
      setSaveError(null);
      await api.worklog.deleteEntry(entryId);
      setDeleteConfirmId(null);
      initialDataRef.current = "[]"; // Reset initial data
      onSave();
    } catch (error: any) {
      setSaveError(
        error instanceof Error ? error.message : "Failed to delete entry",
      );
    } finally {
      setIsSaving(false);
    }
  };

  // Save entries
  const handleSave = useCallback(
    async (isAutoSave = false) => {
      // Validate all rows
      const validatedRows = projectRows.map((row) => ({
        ...row,
        errors: {
          project: validateField("project", row.projectId),
          hours: validateField("hours", row.hours),
          comment: validateField("comment", row.comment),
        },
      }));

      setProjectRows(validatedRows);

      // Check for validation errors
      const hasErrors = validatedRows.some(
        (row) => row.errors.project || row.errors.hours || row.errors.comment,
      );

      if (hasErrors) {
        return;
      }

      // Check total hours
      if (totalExceeds24) {
        return;
      }

      try {
        setIsSaving(true);
        setSaveError(null);

        // Save each row
        for (const row of projectRows) {
          if (row.id) {
            // Update existing entry
            await api.worklog.updateEntry(
              row.id,
              {
                hours: row.hours,
                comment: row.comment || undefined,
              },
              { version: row.version || 0 },
            );
          } else if (row.projectId && row.hours > 0) {
            // Create new entry (skip empty rows)
            await api.worklog.createEntry({
              memberId,
              projectId: row.projectId,
              date: date.toISOString().split("T")[0],
              hours: row.hours,
              comment: row.comment || undefined,
            });
          }
        }

        initialDataRef.current = JSON.stringify(projectRows);
        setHasUnsavedChanges(false);

        if (isAutoSave) {
          setAutoSavedAt(new Date());
        } else {
          onSave();
        }
      } catch (error: any) {
        if (error.status === 409) {
          setSaveError(
            "This entry has been modified by another user. Please refresh and try again.",
          );
        } else {
          setSaveError(
            error instanceof Error ? error.message : "Failed to save entries",
          );
        }
      } finally {
        setIsSaving(false);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [projectRows, memberId, date, totalExceeds24, onSave],
  );

  // Handle close with unsaved changes warning
  const handleClose = () => {
    if (hasUnsavedChanges && !isReadOnly) {
      if (
        !window.confirm(
          "You have unsaved changes. Are you sure you want to close?",
        )
      ) {
        return;
      }
    }
    onClose();
  };

  // Handle absence save - refresh data
  const handleAbsenceSave = () => {
    onSave(); // Trigger parent refresh which will reload this form
  };

  // Render status badge
  const renderStatusBadge = (status?: WorkLogStatus) => {
    if (!status) return null;

    const colors: Record<WorkLogStatus, string> = {
      DRAFT: "bg-gray-200 text-gray-800",
      SUBMITTED: "bg-blue-200 text-blue-800",
      APPROVED: "bg-green-200 text-green-800",
      REJECTED: "bg-red-200 text-red-800",
    };

    return (
      <span
        className={`ml-2 px-2 py-1 text-xs font-semibold rounded ${colors[status]}`}
      >
        {status}
      </span>
    );
  };

  if (isLoading) {
    return (
      <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
        <div className="bg-white rounded-lg p-8">
          <div className="text-center">Loading...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] overflow-y-auto">
        <div className="p-6">
          {/* Header */}
          <div className="flex justify-between items-center mb-6">
            <h2 className="text-2xl font-bold">
              Daily Entry -{" "}
              {date.toLocaleDateString("en-US", {
                year: "numeric",
                month: "long",
                day: "numeric",
              })}
            </h2>
            <button
              type="button"
              onClick={handleClose}
              aria-label="Close daily entry form"
              className="text-gray-500 hover:text-gray-700"
            >
              ✕
            </button>
          </div>

          {/* Proxy Mode Banner */}
          {isProxyMode && targetMember && (
            <div className="mb-6 p-4 bg-amber-100 border border-amber-300 rounded-lg">
              <div className="flex items-center gap-2">
                <span className="text-amber-800 font-medium">
                  Entering time as:{" "}
                  <span className="font-bold">
                    {targetMember.displayName}
                  </span>
                </span>
              </div>
              <p className="text-sm text-amber-700 mt-1">
                This entry will be recorded on behalf of{" "}
                {targetMember.displayName}. You will be logged as the person
                who entered this data.
              </p>
            </div>
          )}

          {/* Tab Switcher */}
          <div className="mb-6 border-b border-gray-200">
            <nav className="-mb-px flex space-x-8">
              <button
                type="button"
                onClick={() => setActiveTab("work")}
                className={`${
                  activeTab === "work"
                    ? "border-blue-500 text-blue-600"
                    : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                } whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm`}
              >
                Work Hours
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("absence")}
                className={`${
                  activeTab === "absence"
                    ? "border-blue-500 text-blue-600"
                    : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                } whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm`}
              >
                Absence
              </button>
            </nav>
          </div>

          {/* Load Error */}
          {loadError && (
            <div
              className="mb-4 p-4 bg-red-100 border border-red-400 text-red-700 rounded"
              role="alert"
            >
              {loadError}
            </div>
          )}

          {/* Save Error */}
          {saveError && (
            <div
              className="mb-4 p-4 bg-red-100 border border-red-400 text-red-700 rounded"
              role="alert"
            >
              {saveError}
            </div>
          )}

          {/* Auto-save indicator */}
          {autoSavedAt && (
            <div className="mb-4 text-sm text-green-600">
              Auto-saved at {autoSavedAt.toLocaleTimeString()}
            </div>
          )}

          {/* Combined Total Hours Display */}
          <div className="mb-4 p-4 bg-gray-50 rounded-lg border border-gray-200">
            <div className="flex items-center justify-between mb-2">
              <span className="font-semibold text-gray-700">
                Total Daily Hours:
              </span>
              <span
                className={`text-2xl font-bold ${
                  totalExceeds24
                    ? "text-red-600"
                    : totalHours === 0
                      ? "text-gray-400"
                      : "text-green-600"
                }`}
              >
                {totalHours.toFixed(2)}h
              </span>
            </div>
            {(totalWorkHours > 0 || absenceHours > 0) && (
              <div className="text-sm text-gray-600 space-y-1">
                {totalWorkHours > 0 && (
                  <div className="flex justify-between">
                    <span>Work Hours:</span>
                    <span className="font-medium">
                      {totalWorkHours.toFixed(2)}h
                    </span>
                  </div>
                )}
                {absenceHours > 0 && (
                  <div className="flex justify-between">
                    <span>Absence Hours:</span>
                    <span className="font-medium text-blue-600">
                      {absenceHours.toFixed(2)}h
                    </span>
                  </div>
                )}
              </div>
            )}
            {totalExceeds24 && (
              <div className="mt-2 text-sm text-red-600 font-medium">
                ⚠ Combined hours cannot exceed 24 hours per day
              </div>
            )}
          </div>

          {/* Work Hours Tab */}
          {activeTab === "work" && (
            <div>
              {/* Project Rows */}
              <div className="space-y-4 mb-6">
                {projectRows.map((row, index) => (
                  <div key={row.id || index} className="border rounded-lg p-4">
                    <div className="flex items-start gap-4">
                      {/* Project Selection */}
                      <div className="flex-1">
                        <label
                          htmlFor={`project-${index}`}
                          className="block text-sm font-medium mb-1"
                        >
                          Project {renderStatusBadge(row.status)}
                        </label>
                        <input
                          id={`project-${index}`}
                          type="text"
                          value={row.projectId}
                          onChange={(e) =>
                            updateProjectRow(index, "projectId", e.target.value)
                          }
                          disabled={
                            row.status !== "DRAFT" && row.status !== undefined
                          }
                          className="w-full px-3 py-2 border rounded-md disabled:bg-gray-100"
                          placeholder="Enter project ID"
                        />
                        {row.errors.project && (
                          <div className="text-red-600 text-sm mt-1">
                            {row.errors.project}
                          </div>
                        )}
                      </div>

                      {/* Hours Input */}
                      <div className="w-32">
                        <label
                          htmlFor={`hours-${index}`}
                          className="block text-sm font-medium mb-1"
                        >
                          Hours
                        </label>
                        <input
                          id={`hours-${index}`}
                          type="number"
                          value={row.hours}
                          onChange={(e) =>
                            updateProjectRow(
                              index,
                              "hours",
                              Number.parseFloat(e.target.value) || 0,
                            )
                          }
                          disabled={
                            row.status !== "DRAFT" && row.status !== undefined
                          }
                          step="0.25"
                          min="0"
                          max="24"
                          className="w-full px-3 py-2 border rounded-md disabled:bg-gray-100"
                        />
                        {row.errors.hours && (
                          <div className="text-red-600 text-sm mt-1">
                            {row.errors.hours}
                          </div>
                        )}
                      </div>

                      {/* Remove Button */}
                      <div className="pt-6">
                        {projectRows.length > 1 &&
                          (row.status === "DRAFT" || !row.status) && (
                            <button
                              type="button"
                              onClick={() => removeProjectRow(index)}
                              aria-label={`Remove project entry ${index + 1}`}
                              className="text-red-600 hover:text-red-800"
                            >
                              Remove
                            </button>
                          )}
                      </div>
                    </div>

                    {/* Comment */}
                    <div className="mt-4">
                      <label
                        htmlFor={`comment-${index}`}
                        className="block text-sm font-medium mb-1"
                      >
                        Comment
                      </label>
                      <textarea
                        id={`comment-${index}`}
                        value={row.comment}
                        onChange={(e) =>
                          updateProjectRow(index, "comment", e.target.value)
                        }
                        disabled={
                          row.status !== "DRAFT" && row.status !== undefined
                        }
                        rows={2}
                        className="w-full px-3 py-2 border rounded-md disabled:bg-gray-100"
                        placeholder="Optional comment..."
                      />
                      {row.errors.comment && (
                        <div className="text-red-600 text-sm mt-1">
                          {row.errors.comment}
                        </div>
                      )}
                    </div>

                    {/* Delete Button (for existing entries) */}
                    {row.id && row.status === "DRAFT" && (
                      <div className="mt-4">
                        <button
                          type="button"
                          onClick={() => setDeleteConfirmId(row.id!)}
                          className="text-red-600 hover:text-red-800 text-sm"
                        >
                          Delete Entry
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>

              {/* Add Project Button */}
              {!isReadOnly && (
                <button
                  type="button"
                  onClick={addProjectRow}
                  className="mb-6 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
                >
                  + Add Project
                </button>
              )}

              {/* Action Buttons */}
              <div className="flex justify-end gap-4">
                <button
                  type="button"
                  onClick={handleClose}
                  className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
                >
                  Cancel
                </button>
                {!isReadOnly && (
                  <button
                    type="button"
                    onClick={() => handleSave(false)}
                    disabled={
                      isSaving || hasValidationErrors() || totalExceeds24
                    }
                    className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed"
                  >
                    {isSaving ? "Saving..." : "Save"}
                  </button>
                )}
              </div>
            </div>
          )}

          {/* Absence Tab */}
          {activeTab === "absence" && (
            <AbsenceForm
              date={date}
              memberId={memberId}
              onSave={handleAbsenceSave}
              onCancel={handleClose}
            />
          )}
        </div>
      </div>

      {/* Delete Confirmation Modal */}
      {deleteConfirmId && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 max-w-md">
            <h3 className="text-lg font-bold mb-4">Confirm Delete</h3>
            <p className="mb-6">
              Are you sure you want to delete this entry? This action cannot be
              undone.
            </p>
            <div className="flex justify-end gap-4">
              <button
                type="button"
                onClick={() => setDeleteConfirmId(null)}
                className="px-4 py-2 border border-gray-300 rounded-md hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => handleDelete(deleteConfirmId)}
                className="px-4 py-2 bg-red-600 text-white rounded-md hover:bg-red-700"
              >
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
