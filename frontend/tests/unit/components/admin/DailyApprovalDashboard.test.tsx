import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

// Mock the API module
const mockGetEntries = vi.fn();
const mockApprove = vi.fn();
const mockReject = vi.fn();
const mockRecall = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    dailyApproval: {
      getEntries: (...args: unknown[]) => mockGetEntries(...args),
      approve: (...args: unknown[]) => mockApprove(...args),
      reject: (...args: unknown[]) => mockReject(...args),
      recall: (...args: unknown[]) => mockRecall(...args),
    },
  },
  ApiError: class ApiError extends Error {},
}));

import { DailyApprovalDashboard } from "@/components/admin/DailyApprovalDashboard";

const sampleGroups = [
  {
    date: "2026-02-21",
    members: [
      {
        memberId: "m1",
        memberName: "田中太郎",
        entries: [
          {
            entryId: "e1",
            projectCode: "PRJ001",
            projectName: "プロジェクトA",
            hours: 4.0,
            comment: "開発作業を実施しました",
            approvalId: null,
            approvalStatus: null,
            approvalComment: null,
          },
          {
            entryId: "e2",
            projectCode: "PRJ002",
            projectName: "プロジェクトB",
            hours: 3.5,
            comment: null,
            approvalId: null,
            approvalStatus: null,
            approvalComment: null,
          },
        ],
      },
      {
        memberId: "m2",
        memberName: "佐藤花子",
        entries: [
          {
            entryId: "e3",
            projectCode: "PRJ001",
            projectName: "プロジェクトA",
            hours: 8.0,
            comment: "テスト完了",
            approvalId: "a1",
            approvalStatus: "APPROVED",
            approvalComment: null,
          },
        ],
      },
    ],
  },
  {
    date: "2026-02-20",
    members: [
      {
        memberId: "m1",
        memberName: "田中太郎",
        entries: [
          {
            entryId: "e4",
            projectCode: "PRJ003",
            projectName: "プロジェクトC",
            hours: 6.0,
            comment: "修正対応が完了しましたので確認をお願いします。詳細は別途報告書を参照してください。",
            approvalId: "a2",
            approvalStatus: "REJECTED",
            approvalComment: "時間が過剰です",
          },
        ],
      },
    ],
  },
];

async function waitForDataLoad() {
  // 田中太郎 appears in two date groups, so use findAllByText
  const elements = await screen.findAllByText("田中太郎");
  expect(elements.length).toBeGreaterThanOrEqual(1);
}

function renderDashboard() {
  return render(<DailyApprovalDashboard refreshKey={0} onRefresh={vi.fn()} />);
}

describe("DailyApprovalDashboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetEntries.mockResolvedValue(sampleGroups);
  });

  // --- US1: Visual Hierarchy ---

  describe("US1: Visual Hierarchy", () => {
    it("renders date headers in Japanese format with weekday", async () => {
      renderDashboard();
      await waitForDataLoad();
      expect(screen.getByText(/2026年2月21日/)).toBeInTheDocument();
      expect(screen.getByText(/2026年2月20日/)).toBeInTheDocument();
    });

    it("groups entries by member within each date", async () => {
      renderDashboard();
      await waitForDataLoad();
      // 田中太郎 appears in 2 date groups
      const tanakaElements = screen.getAllByText("田中太郎");
      expect(tanakaElements).toHaveLength(2);
      expect(screen.getByText("佐藤花子")).toBeInTheDocument();
    });

    it("displays project code and project name in entry rows", async () => {
      renderDashboard();
      await waitForDataLoad();
      // PRJ001 appears in two entries (different members)
      const prj001Elements = screen.getAllByText("PRJ001");
      expect(prj001Elements.length).toBeGreaterThanOrEqual(1);
      const prjAElements = screen.getAllByText("プロジェクトA");
      expect(prjAElements.length).toBeGreaterThanOrEqual(1);
    });

    it("truncates long comments with title tooltip", async () => {
      renderDashboard();
      await waitForDataLoad();
      const longComment = "修正対応が完了しましたので確認をお願いします。詳細は別途報告書を参照してください。";
      const commentCell = screen.getByTitle(longComment);
      expect(commentCell).toBeInTheDocument();
    });

    it("shows per-member hour subtotals", async () => {
      renderDashboard();
      await waitForDataLoad();
      // 田中太郎 on 2026-02-21: 4.0 + 3.5 = 7.5h
      expect(screen.getByText("合計: 7.5h")).toBeInTheDocument();
      // 佐藤花子: 8.0h
      expect(screen.getByText("合計: 8h")).toBeInTheDocument();
      // 田中太郎 on 2026-02-20: 6.0h
      expect(screen.getByText("合計: 6h")).toBeInTheDocument();
    });
  });

  // --- US1: Empty state ---

  describe("US1: Empty state", () => {
    it("shows empty state message when no entries exist", async () => {
      mockGetEntries.mockResolvedValue([]);
      renderDashboard();
      expect(await screen.findByText(/承認待ちの記録はありません/)).toBeInTheDocument();
    });
  });

  // --- US2: Status Badges ---

  describe("US2: Status Badges", () => {
    it("shows pending badge for unapproved entries", async () => {
      renderDashboard();
      await waitForDataLoad();
      // 2 pending entries (e1, e2) + summary card label "未承認"
      const pendingBadges = screen.getAllByText("未承認");
      expect(pendingBadges.length).toBeGreaterThanOrEqual(2);
    });

    it("shows approved badge for approved entries", async () => {
      renderDashboard();
      await waitForDataLoad();
      // Badge text + summary card label
      const approvedElements = screen.getAllByText("承認済");
      expect(approvedElements.length).toBeGreaterThanOrEqual(1);
    });

    it("shows rejected badge for rejected entries", async () => {
      renderDashboard();
      await waitForDataLoad();
      // Badge text + summary card label
      const rejectedElements = screen.getAllByText("差戻");
      expect(rejectedElements.length).toBeGreaterThanOrEqual(1);
    });

    it("shows rejection comment as tooltip on rejected badge", async () => {
      renderDashboard();
      await waitForDataLoad();
      const rejectedBadge = screen.getByTitle("時間が過剰です");
      expect(rejectedBadge).toBeInTheDocument();
      expect(rejectedBadge.textContent).toBe("差戻");
    });

    it("shows checkbox only for unapproved entries", async () => {
      renderDashboard();
      await waitForDataLoad();
      const checkboxes = screen.getAllByRole("checkbox");
      expect(checkboxes).toHaveLength(2);
    });

    it("shows recall button only for approved entries", async () => {
      renderDashboard();
      await waitForDataLoad();
      const recallButtons = screen.getAllByRole("button", { name: /取消/ });
      expect(recallButtons).toHaveLength(1);
    });

    it("shows reject button only for unapproved entries", async () => {
      renderDashboard();
      await waitForDataLoad();
      const rejectButtons = screen.getAllByRole("button", { name: /差戻/ });
      expect(rejectButtons).toHaveLength(2);
    });
  });

  // --- US3: Summary Statistics ---

  describe("US3: Summary Statistics", () => {
    it("displays summary cards with correct counts", async () => {
      renderDashboard();
      await waitForDataLoad();
      // 2 pending (e1, e2), 1 approved (e3), 1 rejected (e4)
      const pendingCard = screen.getByTestId("summary-pending");
      expect(within(pendingCard).getByText("2")).toBeInTheDocument();
      const approvedCard = screen.getByTestId("summary-approved");
      expect(within(approvedCard).getByText("1")).toBeInTheDocument();
      const rejectedCard = screen.getByTestId("summary-rejected");
      expect(within(rejectedCard).getByText("1")).toBeInTheDocument();
    });
  });

  // --- Bulk Approve ---

  describe("Bulk Approve", () => {
    it("shows bulk approve button with count when entries are selected", async () => {
      const user = userEvent.setup();
      renderDashboard();
      await waitForDataLoad();
      const checkboxes = screen.getAllByRole("checkbox");
      await user.click(checkboxes[0]);
      expect(screen.getByText(/選択した1件を承認/)).toBeInTheDocument();
    });
  });

  // --- Floating Error Bar ---

  describe("Floating Error Bar", () => {
    it("shows error in floating bar when bulk approve fails", async () => {
      const user = userEvent.setup();
      mockApprove.mockRejectedValue(new Error("エラーが発生しました"));
      renderDashboard();
      await waitForDataLoad();
      const checkboxes = screen.getAllByRole("checkbox");
      await user.click(checkboxes[0]);
      await user.click(screen.getByText(/選択した1件を承認/));
      expect(await screen.findByRole("alert")).toBeInTheDocument();
      expect(screen.getByText("エラーが発生しました")).toBeInTheDocument();
    });

    it("dismisses error when close button is clicked", async () => {
      const user = userEvent.setup();
      mockApprove.mockRejectedValue(new Error("エラーが発生しました"));
      renderDashboard();
      await waitForDataLoad();
      const checkboxes = screen.getAllByRole("checkbox");
      await user.click(checkboxes[0]);
      await user.click(screen.getByText(/選択した1件を承認/));
      await screen.findByRole("alert");
      await user.click(screen.getByLabelText("エラーを閉じる"));
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("shows error and selection count together", async () => {
      const user = userEvent.setup();
      mockApprove.mockRejectedValue(new Error("エラーが発生しました"));
      renderDashboard();
      await waitForDataLoad();
      const checkboxes = screen.getAllByRole("checkbox");
      await user.click(checkboxes[0]);
      await user.click(screen.getByText(/選択した1件を承認/));
      await screen.findByRole("alert");
      // Both error and selection count should be visible
      expect(screen.getByText("エラーが発生しました")).toBeInTheDocument();
      expect(screen.getByText(/1件選択中/)).toBeInTheDocument();
    });
  });
});
