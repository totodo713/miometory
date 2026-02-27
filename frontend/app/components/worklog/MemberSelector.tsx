"use client";

/**
 * MemberSelector Component
 *
 * Dropdown for selecting a subordinate member for proxy entry mode (US7).
 * Fetches and displays the manager's subordinates for time entry on their behalf.
 */

import { useTranslations } from "next-intl";
import { useEffect, useId, useState } from "react";
import { api } from "@/services/api";
import type { SubordinateMember } from "@/services/worklogStore";

interface MemberSelectorProps {
  /** ID of the current manager */
  managerId: string;
  /** Currently selected member (null if none) */
  selectedMember: SubordinateMember | null;
  /** Callback when a member is selected */
  onSelectMember: (member: SubordinateMember | null) => void;
  /** Whether to include indirect subordinates */
  includeIndirect?: boolean;
  /** Optional CSS class */
  className?: string;
  /** Label to display above the selector */
  label?: string;
  /** Placeholder text when no member is selected */
  placeholder?: string;
}

export function MemberSelector({
  managerId,
  selectedMember,
  onSelectMember,
  includeIndirect = false,
  className = "",
  label,
  placeholder,
}: MemberSelectorProps) {
  const t = useTranslations("worklog.memberSelector");
  const resolvedLabel = label ?? t("label");
  const resolvedPlaceholder = placeholder ?? t("placeholder");
  const selectorId = useId();
  const [subordinates, setSubordinates] = useState<SubordinateMember[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchSubordinates() {
      if (!managerId) return;

      setIsLoading(true);
      setError(null);

      try {
        const response = await api.members.getSubordinates(managerId, includeIndirect);
        setSubordinates(response.subordinates);
      } catch (err) {
        setError(err instanceof Error ? err.message : t("loadError"));
        setSubordinates([]);
      } finally {
        setIsLoading(false);
      }
    }

    fetchSubordinates();
  }, [managerId, includeIndirect, t]);

  function handleChange(event: React.ChangeEvent<HTMLSelectElement>) {
    const memberId = event.target.value;
    if (!memberId) {
      onSelectMember(null);
      return;
    }

    const member = subordinates.find((s) => s.id === memberId);
    onSelectMember(member || null);
  }

  if (error) {
    return (
      <div className={`flex flex-col gap-1 ${className}`}>
        {resolvedLabel && <span className="text-sm font-medium text-gray-700">{resolvedLabel}</span>}
        <div className="text-sm text-red-600 bg-red-50 p-2 rounded border border-red-200">{error}</div>
      </div>
    );
  }

  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      {resolvedLabel && (
        <label htmlFor={selectorId} className="text-sm font-medium text-gray-700">
          {resolvedLabel}
        </label>
      )}
      <select
        id={selectorId}
        value={selectedMember?.id || ""}
        onChange={handleChange}
        disabled={isLoading}
        aria-label={resolvedLabel}
        className="block w-full px-3 py-2 text-sm border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 disabled:bg-gray-100 disabled:cursor-not-allowed"
      >
        <option value="">{isLoading ? t("loading") : resolvedPlaceholder}</option>
        {subordinates.map((member) => (
          <option key={member.id} value={member.id}>
            {member.displayName} ({member.email})
          </option>
        ))}
      </select>
      {subordinates.length === 0 && !isLoading && <p className="text-xs text-gray-500">{t("noMembers")}</p>}
    </div>
  );
}
