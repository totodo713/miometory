"use client";

/**
 * Proxy Entry Page
 *
 * Allows managers to select a team member and enter time on their behalf.
 * This page serves as the entry point to proxy entry mode (US7).
 *
 * Flow:
 * 1. Manager selects a subordinate from the dropdown
 * 2. Clicking "Enter Time" enables proxy mode and redirects to the worklog page
 * 3. All entries created in proxy mode have enteredBy = manager, memberId = subordinate
 */

import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { MemberSelector } from "@/components/worklog/MemberSelector";
import { useAuth } from "@/hooks/useAuth";
import { type SubordinateMember, useProxyMode } from "@/services/worklogStore";

export default function ProxyEntryPage() {
  const router = useRouter();
  const { memberId } = useAuth();
  const t = useTranslations("worklog.proxyMode");
  const tc = useTranslations("common");
  const { enableProxyMode, isProxyMode, targetMember, disableProxyMode } = useProxyMode();
  const [selectedMember, setSelectedMember] = useState<SubordinateMember | null>(targetMember);

  // Guard: require authentication
  if (!memberId) {
    return (
      <div className="min-h-screen bg-gray-50 py-8 flex items-center justify-center">
        <p className="text-gray-600">Please log in to access this page.</p>
      </div>
    );
  }

  function handleEnterTime() {
    if (!selectedMember || !memberId) return;

    enableProxyMode(memberId, selectedMember);
    router.push("/worklog");
  }

  function handleExitProxyMode() {
    disableProxyMode();
    setSelectedMember(null);
  }

  function handleBackToWorklog() {
    router.push("/worklog");
  }

  return (
    <div className="min-h-screen bg-gray-50 py-8">
      <div className="max-w-2xl mx-auto px-4">
        {/* Header */}
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-gray-900">{t("title")}</h1>
          <p className="mt-2 text-gray-600">
            As a manager, you can enter time on behalf of your direct reports. The system will record that you entered
            the time while attributing the hours to the selected team member.
          </p>
        </div>

        {/* Current Proxy Mode Status */}
        {isProxyMode && targetMember && (
          <div className="mb-6 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="font-medium text-yellow-800">{t("title")}</h3>
                <p className="text-sm text-yellow-700">{t("active", { name: targetMember.displayName })}</p>
              </div>
              <button
                type="button"
                onClick={handleExitProxyMode}
                className="px-3 py-1.5 text-sm text-yellow-800 bg-yellow-100 hover:bg-yellow-200 border border-yellow-300 rounded-md transition-colors"
              >
                {t("exit")}
              </button>
            </div>
          </div>
        )}

        {/* Member Selection Card */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
          <MemberSelector
            managerId={memberId}
            selectedMember={selectedMember}
            onSelectMember={setSelectedMember}
            includeIndirect={false}
            label={t("selectMember")}
            placeholder={t("selectMember")}
          />

          {/* Action Buttons */}
          <div className="mt-6 flex gap-3">
            <button
              type="button"
              onClick={handleEnterTime}
              disabled={!selectedMember}
              className="flex-1 px-4 py-2 text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 disabled:cursor-not-allowed rounded-md font-medium transition-colors"
            >
              {t("title")} - {selectedMember?.displayName || t("selectMember")}
            </button>
            <button
              type="button"
              onClick={handleBackToWorklog}
              className="px-4 py-2 text-gray-700 bg-gray-100 hover:bg-gray-200 border border-gray-300 rounded-md font-medium transition-colors"
            >
              {tc("cancel")}
            </button>
          </div>
        </div>

        {/* Info Box */}
        <div className="mt-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
          <h3 className="font-medium text-blue-800 mb-2">How Proxy Entry Works</h3>
          <ul className="text-sm text-blue-700 space-y-1 list-disc list-inside">
            <li>Select a team member from your direct reports</li>
            <li>Click &quot;Enter Time&quot; to switch to proxy entry mode</li>
            <li>Create entries as normal - they will be attributed to the selected member</li>
            <li>The audit trail records that you entered the time on their behalf</li>
            <li>Exit proxy mode when you&apos;re done to return to your own timesheet</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
