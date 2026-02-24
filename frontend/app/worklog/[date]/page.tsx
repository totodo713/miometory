"use client";

/**
 * Daily Time Entry Page
 *
 * Allows engineers to enter or edit time entries for a specific date.
 * Handles routing from calendar view (/worklog -> /worklog/2026-01-15)
 */

import { useRouter } from "next/navigation";
import { use, useMemo } from "react";
import { DailyEntryForm } from "@/components/worklog/DailyEntryForm";
import { useAuth } from "@/hooks/useAuth";
import { useRejectionStatus } from "@/hooks/useRejectionStatus";
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
  const { memberId: authMemberId } = useAuth();

  // Proxy mode state - get effective member ID
  const { isProxyMode, targetMember } = useProxyMode();

  // Calendar refresh trigger
  const { triggerRefresh } = useCalendarRefresh();

  // Parse date string to Date object (no early return before hooks)
  const parsedDate = useMemo(() => {
    const d = new Date(date);
    return Number.isNaN(d.getTime()) ? null : d;
  }, [date]);

  // Use target member ID if in proxy mode, otherwise use current user's member
  const memberId = isProxyMode && targetMember ? targetMember.id : (authMemberId ?? "");

  // Compute fiscal month period from date (21st–20th pattern)
  const { fiscalMonthStart, fiscalMonthEnd } = useMemo(() => {
    if (!parsedDate) {
      return { fiscalMonthStart: "", fiscalMonthEnd: "" };
    }
    const day = parsedDate.getDate();
    const month = parsedDate.getMonth();
    const year = parsedDate.getFullYear();

    if (day <= 20) {
      const prevMonth = month === 0 ? 11 : month - 1;
      const prevYear = month === 0 ? year - 1 : year;
      return {
        fiscalMonthStart: `${prevYear}-${String(prevMonth + 1).padStart(2, "0")}-21`,
        fiscalMonthEnd: `${year}-${String(month + 1).padStart(2, "0")}-20`,
      };
    }
    const nextMonth = month === 11 ? 0 : month + 1;
    const nextYear = month === 11 ? year + 1 : year;
    return {
      fiscalMonthStart: `${year}-${String(month + 1).padStart(2, "0")}-21`,
      fiscalMonthEnd: `${nextYear}-${String(nextMonth + 1).padStart(2, "0")}-20`,
    };
  }, [parsedDate]);

  // Load rejection status for the fiscal month
  const { getRejectionForDate } = useRejectionStatus(memberId, fiscalMonthStart, fiscalMonthEnd);

  // Invalid date - redirect back to calendar (after all hooks)
  if (!parsedDate) {
    router.push("/worklog");
    return null;
  }

  const dateStr = parsedDate.toISOString().split("T")[0];
  const rejectionInfo = getRejectionForDate(dateStr);

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
        enteredBy={authMemberId ?? undefined}
        rejectionSource={rejectionInfo.rejectionSource}
        rejectionReason={rejectionInfo.rejectionReason}
        onClose={handleClose}
        onSave={handleSave}
      />
    </div>
  );
}
