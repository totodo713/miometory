import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { IntlWrapper } from "../../../helpers/intl";

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
  totalWorkHours: 180.0,
  totalAbsenceHours: 8.0,
  totalBusinessDays: 20,
  projects: [
    {
      projectId: "proj-1",
      projectName: "Project Alpha",
      totalHours: 180.0,
      percentage: 100.0,
    },
  ],
  approvalStatus: null,
  rejectionReason: null,
  standardDailyHours: 8,
  standardMonthlyHours: 160,
  overtimeHours: 20,
  standardHoursSource: "member",
};

describe("MonthlySummary — Overtime Card", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("should display overtime hours card with correct value", async () => {
    mockGetMonthlySummary.mockResolvedValue({ ...baseSummary });

    render(
      <IntlWrapper>
        <MonthlySummary year={2026} month={1} memberId="member-123" />
      </IntlWrapper>,
    );

    await waitFor(() => {
      // "残業時間" is the Japanese translation for "overtimeHours"
      expect(screen.getByText("残業時間")).toBeInTheDocument();
    });

    expect(screen.getByText("20h")).toBeInTheDocument();
  });

  test("should display required hours subtitle in overtime card", async () => {
    mockGetMonthlySummary.mockResolvedValue({ ...baseSummary });

    render(
      <IntlWrapper>
        <MonthlySummary year={2026} month={1} memberId="member-123" />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("残業時間")).toBeInTheDocument();
    });

    // "所定時間: 160h" — the required hours subtitle
    expect(screen.getByText(/所定時間.*160h/)).toBeInTheDocument();
  });

  test("should display 0h overtime when no overtime", async () => {
    mockGetMonthlySummary.mockResolvedValue({
      ...baseSummary,
      totalWorkHours: 160.0,
      overtimeHours: 0,
      projects: [
        {
          projectId: "proj-1",
          projectName: "Project Alpha",
          totalHours: 160.0,
          percentage: 100.0,
        },
      ],
    });

    render(
      <IntlWrapper>
        <MonthlySummary year={2026} month={1} memberId="member-123" />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("残業時間")).toBeInTheDocument();
    });

    expect(screen.getByText("0h")).toBeInTheDocument();
  });
});
