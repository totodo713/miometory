import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

// Mock API module — factory is hoisted, so ApiError must be defined inline
vi.mock("../../../../app/services/api", () => {
  class ApiError extends Error {
    status: number;
    code?: string;
    constructor(message: string, status: number, code?: string) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.code = code;
    }
  }
  return {
    ApiError,
    api: {
      worklog: {
        submitDailyEntries: vi.fn(),
        recallDailyEntries: vi.fn(),
      },
    },
  };
});

// Mock worklog store
vi.mock("../../../../app/services/worklogStore", () => ({
  useCalendarRefresh: () => ({
    calendarRefreshKey: 0,
    triggerRefresh: vi.fn(),
  }),
}));

import { SubmitDailyButton } from "../../../../app/components/worklog/SubmitDailyButton";
import { ApiError, api } from "../../../../app/services/api";

const mockSubmitDailyEntries = api.worklog.submitDailyEntries as ReturnType<typeof vi.fn>;
const mockRecallDailyEntries = api.worklog.recallDailyEntries as ReturnType<typeof vi.fn>;

describe("SubmitDailyButton", () => {
  const defaultProps = {
    date: new Date("2026-02-15"),
    memberId: "member-123",
    hasDraftEntries: true,
    hasSubmittedEntries: false,
    hasUnsavedChanges: false,
    draftEntryCount: 3,
    draftTotalHours: 8,
    onSaveFirst: vi.fn().mockResolvedValue(undefined),
    onSubmitSuccess: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders submit button when hasDraftEntries is true", () => {
    render(<SubmitDailyButton {...defaultProps} />);
    expect(screen.getByRole("button", { name: "Submit" })).toBeInTheDocument();
  });

  it("does not render when hasDraftEntries is false", () => {
    render(<SubmitDailyButton {...defaultProps} hasDraftEntries={false} />);
    expect(screen.queryByRole("button", { name: "Submit" })).not.toBeInTheDocument();
  });

  it("shows confirmation dialog on submit click", () => {
    render(<SubmitDailyButton {...defaultProps} />);
    fireEvent.click(screen.getByRole("button", { name: "Submit" }));

    expect(screen.getByText("Confirm Submission")).toBeInTheDocument();
    expect(screen.getByText(/3/)).toBeInTheDocument(); // entry count
    expect(screen.getByText(/8/)).toBeInTheDocument(); // total hours
  });

  it("closes confirmation dialog on Cancel", () => {
    render(<SubmitDailyButton {...defaultProps} />);
    fireEvent.click(screen.getByRole("button", { name: "Submit" }));
    expect(screen.getByText("Confirm Submission")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(screen.queryByText("Confirm Submission")).not.toBeInTheDocument();
  });

  it("closes confirmation dialog on Escape key", () => {
    render(<SubmitDailyButton {...defaultProps} />);
    fireEvent.click(screen.getByRole("button", { name: "Submit" }));
    expect(screen.getByText("Confirm Submission")).toBeInTheDocument();

    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByText("Confirm Submission")).not.toBeInTheDocument();
  });

  it("calls API on confirm and shows success notification", async () => {
    mockSubmitDailyEntries.mockResolvedValue({
      submittedCount: 3,
      date: "2026-02-15",
      entries: [],
    });

    render(<SubmitDailyButton {...defaultProps} />);
    fireEvent.click(screen.getByRole("button", { name: "Submit" }));

    // Click Submit in the dialog
    const submitButtons = screen.getAllByRole("button", { name: "Submit" });
    fireEvent.click(submitButtons[submitButtons.length - 1]);

    await waitFor(() => {
      expect(mockSubmitDailyEntries).toHaveBeenCalledWith({
        memberId: "member-123",
        date: "2026-02-15",
        submittedBy: "member-123",
      });
    });

    await waitFor(() => {
      expect(screen.getByText("3 entries submitted successfully.")).toBeInTheDocument();
    });

    expect(defaultProps.onSubmitSuccess).toHaveBeenCalled();
  });

  it("shows error notification on API failure", async () => {
    mockSubmitDailyEntries.mockRejectedValue(new Error("Network error"));

    render(<SubmitDailyButton {...defaultProps} />);
    fireEvent.click(screen.getByRole("button", { name: "Submit" }));

    const submitButtons = screen.getAllByRole("button", { name: "Submit" });
    fireEvent.click(submitButtons[submitButtons.length - 1]);

    await waitFor(() => {
      expect(screen.getByText("Network error")).toBeInTheDocument();
    });
  });

  it("shows conflict-specific message on 409 error", async () => {
    mockSubmitDailyEntries.mockRejectedValue(new ApiError("Conflict", 409, "OPTIMISTIC_LOCK_FAILURE"));

    render(<SubmitDailyButton {...defaultProps} />);
    fireEvent.click(screen.getByRole("button", { name: "Submit" }));

    const submitButtons = screen.getAllByRole("button", { name: "Submit" });
    fireEvent.click(submitButtons[submitButtons.length - 1]);

    await waitFor(() => {
      expect(screen.getByText(/refresh and try again/)).toBeInTheDocument();
    });
  });

  it("saves unsaved changes before submitting", async () => {
    mockSubmitDailyEntries.mockResolvedValue({
      submittedCount: 1,
      date: "2026-02-15",
      entries: [],
    });

    render(<SubmitDailyButton {...defaultProps} hasUnsavedChanges={true} />);
    fireEvent.click(screen.getByRole("button", { name: "Submit" }));

    const submitButtons = screen.getAllByRole("button", { name: "Submit" });
    fireEvent.click(submitButtons[submitButtons.length - 1]);

    await waitFor(() => {
      expect(defaultProps.onSaveFirst).toHaveBeenCalled();
    });

    await waitFor(() => {
      expect(mockSubmitDailyEntries).toHaveBeenCalled();
    });
  });

  describe("Recall mode", () => {
    it("renders recall button when hasSubmittedEntries is true and hasDraftEntries is false", () => {
      render(<SubmitDailyButton {...defaultProps} hasDraftEntries={false} hasSubmittedEntries={true} />);
      expect(screen.getByRole("button", { name: "Recall" })).toBeInTheDocument();
    });

    it("does not render when both hasDraftEntries and hasSubmittedEntries are false", () => {
      render(<SubmitDailyButton {...defaultProps} hasDraftEntries={false} hasSubmittedEntries={false} />);
      expect(screen.queryByRole("button")).not.toBeInTheDocument();
    });

    it("calls recallDailyEntries API on recall click", async () => {
      mockRecallDailyEntries.mockResolvedValue({
        recalledCount: 2,
        date: "2026-02-15",
        entries: [],
      });

      render(<SubmitDailyButton {...defaultProps} hasDraftEntries={false} hasSubmittedEntries={true} />);
      fireEvent.click(screen.getByRole("button", { name: "Recall" }));

      await waitFor(() => {
        expect(mockRecallDailyEntries).toHaveBeenCalledWith({
          memberId: "member-123",
          date: "2026-02-15",
          recalledBy: "member-123",
        });
      });

      await waitFor(() => {
        expect(screen.getByText("2 entries recalled to draft.")).toBeInTheDocument();
      });
    });

    it("shows error when recall is blocked by approval", async () => {
      mockRecallDailyEntries.mockRejectedValue(new ApiError("Blocked", 422, "RECALL_BLOCKED_BY_APPROVAL"));

      render(<SubmitDailyButton {...defaultProps} hasDraftEntries={false} hasSubmittedEntries={true} />);
      fireEvent.click(screen.getByRole("button", { name: "Recall" }));

      await waitFor(() => {
        expect(screen.getByText(/Manager has already acted/)).toBeInTheDocument();
      });
    });
  });
});
