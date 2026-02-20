import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";

// Mock API module
vi.mock("@/services/api", () => ({
  api: {
    worklog: {
      getMonthlySummary: vi.fn(),
    },
  },
}));

// Mock SubmitButton to avoid its internal dependencies
vi.mock("@/components/worklog/SubmitButton", () => ({
  SubmitButton: () => <div data-testid="mock-submit-button" />,
}));

import { MonthlySummary } from "@/components/worklog/MonthlySummary";
import { api } from "@/services/api";

const mockGetMonthlySummary = api.worklog.getMonthlySummary as ReturnType<typeof vi.fn>;

const baseSummary = {
  year: 2026,
  month: 1,
  totalWorkHours: 40.0,
  totalAbsenceHours: 8.0,
  totalBusinessDays: 20,
  projects: [
    {
      projectId: "proj-1",
      projectName: "Project Alpha",
      totalHours: 40.0,
      percentage: 100.0,
    },
  ],
};

describe("MonthlySummary — Rejection Display", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("should display RejectionBanner when approval status is REJECTED", async () => {
    mockGetMonthlySummary.mockResolvedValue({
      ...baseSummary,
      approvalStatus: "REJECTED",
      rejectionReason: "Hours don't match project plan",
    });

    render(<MonthlySummary year={2026} month={1} memberId="member-123" />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });

    expect(screen.getByText("Monthly Rejection")).toBeInTheDocument();
    expect(screen.getByText("Hours don't match project plan")).toBeInTheDocument();
  });

  test("should not display RejectionBanner when approval status is null", async () => {
    mockGetMonthlySummary.mockResolvedValue({
      ...baseSummary,
      approvalStatus: null,
      rejectionReason: null,
    });

    render(<MonthlySummary year={2026} month={1} memberId="member-123" />);

    await waitFor(() => {
      expect(screen.getByText("Monthly Summary")).toBeInTheDocument();
    });

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  test("should not display RejectionBanner when approval status is SUBMITTED", async () => {
    mockGetMonthlySummary.mockResolvedValue({
      ...baseSummary,
      approvalStatus: "SUBMITTED",
      rejectionReason: null,
    });

    render(<MonthlySummary year={2026} month={1} memberId="member-123" />);

    await waitFor(() => {
      expect(screen.getByText("Monthly Summary")).toBeInTheDocument();
    });

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.queryByText("Monthly Rejection")).not.toBeInTheDocument();
  });
});
