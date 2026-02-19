"use client";

/**
 * Daily Time Entry Page
 *
 * Allows engineers to enter or edit time entries for a specific date.
 * Handles routing from calendar view (/worklog -> /worklog/2026-01-15)
 */

import { useRouter } from "next/navigation";
import { use } from "react";
import { DailyEntryForm } from "@/components/worklog/DailyEntryForm";
import { useAuth } from "@/hooks/useAuth";
import { useCalendarRefresh, useProxyMode } from "@/services/worklogStore";

interface PageProps {
  params: Promise<{
    date: string; // Format: YYYY-MM-DD
  }>;
}

export default function DailyEntryPage({ params }: PageProps) {
  const router = useRouter();
  const { date } = use(params);

  // Authentication state
  const { userId } = useAuth();

  // Proxy mode state - get effective member ID
  const { isProxyMode, targetMember } = useProxyMode();

  // Calendar refresh trigger
  const { triggerRefresh } = useCalendarRefresh();

  // Parse date string to Date object
  let parsedDate: Date;
  try {
    parsedDate = new Date(date);
    // Validate date
    if (Number.isNaN(parsedDate.getTime())) {
      throw new Error("Invalid date");
    }
  } catch (_error) {
    // Invalid date format - redirect back to calendar
    router.push("/worklog");
    return null;
  }

  // Use target member ID if in proxy mode, otherwise use current user
  const memberId = isProxyMode && targetMember ? targetMember.id : (userId ?? "");

  const handleClose = () => {
    router.push("/worklog");
  };

  const handleSave = () => {
    // Trigger calendar data refresh before navigating back
    triggerRefresh();
    router.push("/worklog");
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <DailyEntryForm
        date={parsedDate}
        memberId={memberId}
        enteredBy={userId ?? undefined}
        onClose={handleClose}
        onSave={handleSave}
      />
    </div>
  );
}
