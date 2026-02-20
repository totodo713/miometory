import { useCallback, useEffect, useState } from "react";
import { api } from "../services/api";

interface DailyRejection {
  date: string;
  rejectionReason: string;
  rejectedBy: string;
  rejectedByName: string | null;
  rejectedAt: string;
}

interface MonthlyApproval {
  approvalId: string;
  status: string;
  rejectionReason: string | null;
  reviewedBy: string | null;
  reviewerName: string | null;
  reviewedAt: string | null;
}

interface RejectionStatus {
  isMonthlyRejected: boolean;
  monthlyRejectionReason: string | null;
  monthlyReviewedBy: string | null;
  monthlyReviewerName: string | null;
  monthlyReviewedAt: string | null;
  dailyRejections: Map<string, DailyRejection>;
  isLoading: boolean;
  error: string | null;
  getRejectionForDate: (date: string) => {
    isRejected: boolean;
    rejectionSource: "monthly" | "daily" | null;
    rejectionReason: string | null;
    rejectedByName: string | null;
    rejectedAt: string | null;
  };
  refresh: () => void;
}

/**
 * Hook that consolidates monthly and daily rejection state.
 * Provides rejection info per date range for Calendar, DailyEntryForm, and MonthlySummary.
 */
export function useRejectionStatus(
  memberId: string,
  fiscalMonthStart: string,
  fiscalMonthEnd: string,
): RejectionStatus {
  const [monthlyApproval, setMonthlyApproval] = useState<MonthlyApproval | null>(null);
  const [dailyRejections, setDailyRejections] = useState<Map<string, DailyRejection>>(new Map());
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadRejectionStatus = useCallback(async () => {
    if (!memberId || !fiscalMonthStart || !fiscalMonthEnd) {
      setIsLoading(false);
      return;
    }

    try {
      setIsLoading(true);
      setError(null);

      const [approvalResult, rejectionsResult] = await Promise.allSettled([
        api.approval.getMemberApproval({ memberId, fiscalMonthStart, fiscalMonthEnd }),
        api.worklog.getDailyRejections({ memberId, startDate: fiscalMonthStart, endDate: fiscalMonthEnd }),
      ]);

      if (approvalResult.status === "fulfilled") {
        setMonthlyApproval(approvalResult.value);
      } else {
        // 404 means no approval record — not an error
        setMonthlyApproval(null);
      }

      if (rejectionsResult.status === "fulfilled") {
        const map = new Map<string, DailyRejection>();
        for (const rejection of rejectionsResult.value.rejections) {
          map.set(rejection.date, rejection);
        }
        setDailyRejections(map);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load rejection status");
    } finally {
      setIsLoading(false);
    }
  }, [memberId, fiscalMonthStart, fiscalMonthEnd]);

  useEffect(() => {
    loadRejectionStatus();
  }, [loadRejectionStatus]);

  const isMonthlyRejected = monthlyApproval?.status === "REJECTED";

  const getRejectionForDate = useCallback(
    (date: string) => {
      // Daily rejection takes precedence over monthly
      const dailyRejection = dailyRejections.get(date);
      if (dailyRejection) {
        return {
          isRejected: true,
          rejectionSource: "daily" as const,
          rejectionReason: dailyRejection.rejectionReason,
          rejectedByName: dailyRejection.rejectedByName,
          rejectedAt: dailyRejection.rejectedAt,
        };
      }

      if (isMonthlyRejected && monthlyApproval?.rejectionReason) {
        return {
          isRejected: true,
          rejectionSource: "monthly" as const,
          rejectionReason: monthlyApproval.rejectionReason,
          rejectedByName: monthlyApproval.reviewerName,
          rejectedAt: monthlyApproval.reviewedAt,
        };
      }

      return {
        isRejected: false,
        rejectionSource: null,
        rejectionReason: null,
        rejectedByName: null,
        rejectedAt: null,
      };
    },
    [dailyRejections, isMonthlyRejected, monthlyApproval],
  );

  return {
    isMonthlyRejected,
    monthlyRejectionReason: monthlyApproval?.rejectionReason ?? null,
    monthlyReviewedBy: monthlyApproval?.reviewedBy ?? null,
    monthlyReviewerName: monthlyApproval?.reviewerName ?? null,
    monthlyReviewedAt: monthlyApproval?.reviewedAt ?? null,
    dailyRejections,
    isLoading,
    error,
    getRejectionForDate,
    refresh: loadRejectionStatus,
  };
}
