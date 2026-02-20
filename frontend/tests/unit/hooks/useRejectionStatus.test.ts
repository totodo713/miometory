import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";

// Mock API module
vi.mock("@/services/api", () => ({
  api: {
    approval: {
      getMemberApproval: vi.fn(),
    },
    worklog: {
      getDailyRejections: vi.fn(),
    },
  },
}));

import { useRejectionStatus } from "@/hooks/useRejectionStatus";
import { api } from "@/services/api";

const mockGetMemberApproval = api.approval.getMemberApproval as ReturnType<typeof vi.fn>;
const mockGetDailyRejections = api.worklog.getDailyRejections as ReturnType<typeof vi.fn>;

describe("useRejectionStatus", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("should return isMonthlyRejected=true when approval status is REJECTED", async () => {
    mockGetMemberApproval.mockResolvedValue({
      approvalId: "approval-uuid",
      status: "REJECTED",
      rejectionReason: "Fix hours",
      reviewedBy: "reviewer-uuid",
      reviewerName: "Manager",
      reviewedAt: "2026-01-15T10:00:00Z",
    });
    mockGetDailyRejections.mockResolvedValue({ rejections: [] });

    const { result } = renderHook(() => useRejectionStatus("member-123", "2026-01-01", "2026-01-31"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.isMonthlyRejected).toBe(true);
    expect(result.current.monthlyRejectionReason).toBe("Fix hours");
    expect(result.current.monthlyReviewerName).toBe("Manager");
    expect(result.current.monthlyReviewedAt).toBe("2026-01-15T10:00:00Z");
  });

  test("should return isMonthlyRejected=false when no approval exists (404)", async () => {
    mockGetMemberApproval.mockRejectedValue(new Error("Not found"));
    mockGetDailyRejections.mockResolvedValue({ rejections: [] });

    const { result } = renderHook(() => useRejectionStatus("member-123", "2026-01-01", "2026-01-31"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.isMonthlyRejected).toBe(false);
    expect(result.current.monthlyRejectionReason).toBeNull();
  });

  test("should populate dailyRejections map from API response", async () => {
    mockGetMemberApproval.mockRejectedValue(new Error("Not found"));
    mockGetDailyRejections.mockResolvedValue({
      rejections: [
        {
          date: "2026-01-10",
          rejectionReason: "Wrong project",
          rejectedBy: "reviewer-uuid",
          rejectedByName: "Manager",
          rejectedAt: "2026-01-12T08:00:00Z",
        },
      ],
    });

    const { result } = renderHook(() => useRejectionStatus("member-123", "2026-01-01", "2026-01-31"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.dailyRejections.size).toBe(1);
    expect(result.current.dailyRejections.get("2026-01-10")).toEqual({
      date: "2026-01-10",
      rejectionReason: "Wrong project",
      rejectedBy: "reviewer-uuid",
      rejectedByName: "Manager",
      rejectedAt: "2026-01-12T08:00:00Z",
    });
  });

  test("getRejectionForDate should return daily rejection when exists", async () => {
    mockGetMemberApproval.mockResolvedValue({
      approvalId: "approval-uuid",
      status: "REJECTED",
      rejectionReason: "Fix hours",
      reviewedBy: "reviewer-uuid",
      reviewerName: "Manager",
      reviewedAt: "2026-01-15T10:00:00Z",
    });
    mockGetDailyRejections.mockResolvedValue({
      rejections: [
        {
          date: "2026-01-10",
          rejectionReason: "Wrong project",
          rejectedBy: "reviewer-uuid",
          rejectedByName: "Manager",
          rejectedAt: "2026-01-12T08:00:00Z",
        },
      ],
    });

    const { result } = renderHook(() => useRejectionStatus("member-123", "2026-01-01", "2026-01-31"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    // Daily rejection takes precedence over monthly
    const rejection = result.current.getRejectionForDate("2026-01-10");
    expect(rejection.isRejected).toBe(true);
    expect(rejection.rejectionSource).toBe("daily");
    expect(rejection.rejectionReason).toBe("Wrong project");
    expect(rejection.rejectedByName).toBe("Manager");
    expect(rejection.rejectedAt).toBe("2026-01-12T08:00:00Z");
  });

  test("getRejectionForDate should return monthly rejection when no daily exists", async () => {
    mockGetMemberApproval.mockResolvedValue({
      approvalId: "approval-uuid",
      status: "REJECTED",
      rejectionReason: "Fix hours",
      reviewedBy: "reviewer-uuid",
      reviewerName: "Manager",
      reviewedAt: "2026-01-15T10:00:00Z",
    });
    mockGetDailyRejections.mockResolvedValue({ rejections: [] });

    const { result } = renderHook(() => useRejectionStatus("member-123", "2026-01-01", "2026-01-31"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    // No daily rejection for this date, falls back to monthly
    const rejection = result.current.getRejectionForDate("2026-01-20");
    expect(rejection.isRejected).toBe(true);
    expect(rejection.rejectionSource).toBe("monthly");
    expect(rejection.rejectionReason).toBe("Fix hours");
    expect(rejection.rejectedByName).toBe("Manager");
    expect(rejection.rejectedAt).toBe("2026-01-15T10:00:00Z");
  });

  test("getRejectionForDate should return not-rejected when neither exists", async () => {
    mockGetMemberApproval.mockResolvedValue({
      approvalId: "approval-uuid",
      status: "SUBMITTED",
      rejectionReason: null,
      reviewedBy: null,
      reviewerName: null,
      reviewedAt: null,
    });
    mockGetDailyRejections.mockResolvedValue({ rejections: [] });

    const { result } = renderHook(() => useRejectionStatus("member-123", "2026-01-01", "2026-01-31"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    const rejection = result.current.getRejectionForDate("2026-01-10");
    expect(rejection.isRejected).toBe(false);
    expect(rejection.rejectionSource).toBeNull();
    expect(rejection.rejectionReason).toBeNull();
    expect(rejection.rejectedByName).toBeNull();
    expect(rejection.rejectedAt).toBeNull();
  });

  test("should not fetch when memberId is empty", async () => {
    const { result } = renderHook(() => useRejectionStatus("", "2026-01-01", "2026-01-31"));

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(mockGetMemberApproval).not.toHaveBeenCalled();
    expect(mockGetDailyRejections).not.toHaveBeenCalled();
    expect(result.current.isMonthlyRejected).toBe(false);
  });
});
