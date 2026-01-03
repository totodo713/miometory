"use client";

import { useState } from "react";
import { api } from "../../services/api";
import {
  AbsenceType,
  AbsenceTypeLabels,
  type CreateAbsenceRequest,
} from "../../types/absence";

interface AbsenceFormProps {
  date: Date;
  memberId: string;
  onSave: () => void;
  onCancel: () => void;
}

export function AbsenceForm({
  date,
  memberId,
  onSave,
  onCancel,
}: AbsenceFormProps) {
  const [hours, setHours] = useState<number>(8);
  const [absenceType, setAbsenceType] = useState<AbsenceType>(
    AbsenceType.PAID_LEAVE,
  );
  const [reason, setReason] = useState<string>("");
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Validate hours (must be 0.25 increments, > 0 and <= 24)
  const validateHours = (value: number): string | null => {
    if (value <= 0) {
      return "Hours must be greater than 0";
    }
    if (value > 24) {
      return "Hours cannot exceed 24";
    }
    if (value % 0.25 !== 0) {
      return "Hours must be in 0.25 increments (e.g., 0.25, 0.5, 1.0, etc.)";
    }
    return null;
  };

  const handleHoursChange = (value: string) => {
    const numValue = Number.parseFloat(value);
    if (!Number.isNaN(numValue)) {
      setHours(numValue);
      setError(validateHours(numValue));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    // Validate
    const hoursError = validateHours(hours);
    if (hoursError) {
      setError(hoursError);
      return;
    }

    if (reason.length > 500) {
      setError("Reason cannot exceed 500 characters");
      return;
    }

    setIsSaving(true);
    setError(null);

    try {
      const dateStr = date.toISOString().split("T")[0];
      const request: CreateAbsenceRequest = {
        memberId,
        date: dateStr,
        hours,
        absenceType,
        reason: reason.trim() || undefined,
        recordedBy: memberId,
      };

      await api.absence.createAbsence(request);
      onSave();
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : "Failed to create absence. Please try again.",
      );
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="bg-blue-50 border border-blue-200 rounded-md p-3 mb-4">
        <h3 className="text-sm font-medium text-blue-900 mb-1">
          Record Absence
        </h3>
        <p className="text-xs text-blue-700">
          Use this form to record time away from work (vacation, sick leave,
          etc.) separate from project hours.
        </p>
      </div>

      {/* Absence Type */}
      <div>
        <label
          htmlFor="absenceType"
          className="block text-sm font-medium text-gray-700 mb-1"
        >
          Absence Type *
        </label>
        <select
          id="absenceType"
          value={absenceType}
          onChange={(e) => setAbsenceType(e.target.value as AbsenceType)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          disabled={isSaving}
        >
          {Object.values(AbsenceType).map((type) => (
            <option key={type} value={type}>
              {AbsenceTypeLabels[type]}
            </option>
          ))}
        </select>
      </div>

      {/* Hours */}
      <div>
        <label
          htmlFor="hours"
          className="block text-sm font-medium text-gray-700 mb-1"
        >
          Hours *
        </label>
        <input
          id="hours"
          type="number"
          step="0.25"
          min="0.25"
          max="24"
          value={hours}
          onChange={(e) => handleHoursChange(e.target.value)}
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          disabled={isSaving}
          required
        />
        <p className="mt-1 text-xs text-gray-500">
          Must be in 0.25h increments (e.g., 0.25, 0.5, 1.0, 8.0)
        </p>
      </div>

      {/* Reason */}
      <div>
        <label
          htmlFor="reason"
          className="block text-sm font-medium text-gray-700 mb-1"
        >
          Reason (Optional)
        </label>
        <textarea
          id="reason"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          maxLength={500}
          className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          disabled={isSaving}
          placeholder="Optional: Add a note about this absence"
        />
        <p className="mt-1 text-xs text-gray-500">
          {reason.length}/500 characters
        </p>
      </div>

      {/* Error message */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-md p-3">
          <p className="text-sm text-red-800">{error}</p>
        </div>
      )}

      {/* Buttons */}
      <div className="flex justify-end space-x-3 pt-4 border-t">
        <button
          type="button"
          onClick={onCancel}
          disabled={isSaving}
          className="px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={isSaving || !!error}
          className="px-4 py-2 text-sm font-medium text-white bg-blue-600 border border-transparent rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isSaving ? "Saving..." : "Save Absence"}
        </button>
      </div>
    </form>
  );
}
