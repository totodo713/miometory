import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ProjectForm } from "@/components/admin/ProjectForm";
import type { ProjectRow } from "@/components/admin/ProjectList";

const mockCreate = vi.fn();
const mockUpdate = vi.fn();

vi.mock("@/services/api", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    code?: string;
    constructor(message: string, status: number, code?: string) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.code = code;
    }
  },
  api: {
    admin: {
      projects: {
        create: (...args: unknown[]) => mockCreate(...args),
        update: (...args: unknown[]) => mockUpdate(...args),
      },
    },
  },
}));

const existingProject: ProjectRow = {
  id: "p1",
  code: "PRJ001",
  name: "Project Alpha",
  isActive: true,
  validFrom: "2026-01-01",
  validUntil: "2026-12-31",
  assignedMemberCount: 5,
};

describe("ProjectForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreate.mockResolvedValue({ id: "new-id" });
    mockUpdate.mockResolvedValue(undefined);
  });

  describe("Create mode", () => {
    const defaultProps = {
      project: null,
      onClose: vi.fn(),
      onSaved: vi.fn(),
    };

    test("renders create form with empty fields", () => {
      render(<ProjectForm {...defaultProps} />);

      expect(screen.getByText("プロジェクト作成")).toBeInTheDocument();
      expect(screen.getByLabelText("コード")).toHaveValue("");
      expect(screen.getByLabelText("コード")).not.toBeDisabled();
      expect(screen.getByLabelText("名前")).toHaveValue("");
      expect(screen.getByText("作成")).toBeInTheDocument();
    });

    test("creates project on submit", async () => {
      const user = userEvent.setup();
      const onSaved = vi.fn();
      render(<ProjectForm {...defaultProps} onSaved={onSaved} />);

      await user.type(screen.getByLabelText("コード"), "NEW001");
      await user.type(screen.getByLabelText("名前"), "New Project");
      await user.click(screen.getByText("作成"));

      await waitFor(() => {
        expect(mockCreate).toHaveBeenCalledWith({
          code: "NEW001",
          name: "New Project",
          validFrom: undefined,
          validUntil: undefined,
        });
      });
      expect(onSaved).toHaveBeenCalled();
    });

    test("creates project with dates", async () => {
      const user = userEvent.setup();
      render(<ProjectForm {...defaultProps} />);

      await user.type(screen.getByLabelText("コード"), "NEW001");
      await user.type(screen.getByLabelText("名前"), "New Project");
      await user.type(screen.getByLabelText("開始日"), "2026-04-01");
      await user.type(screen.getByLabelText("終了日"), "2026-09-30");
      await user.click(screen.getByText("作成"));

      await waitFor(() => {
        expect(mockCreate).toHaveBeenCalledWith({
          code: "NEW001",
          name: "New Project",
          validFrom: "2026-04-01",
          validUntil: "2026-09-30",
        });
      });
    });

    test("has required attribute on code and name inputs", () => {
      render(<ProjectForm {...defaultProps} />);

      expect(screen.getByLabelText("コード")).toBeRequired();
      expect(screen.getByLabelText("名前")).toBeRequired();
    });

    test("shows validation error when only whitespace entered", async () => {
      const user = userEvent.setup();
      render(<ProjectForm {...defaultProps} />);

      await user.type(screen.getByLabelText("コード"), "   ");
      await user.type(screen.getByLabelText("名前"), "   ");
      await user.click(screen.getByText("作成"));

      expect(screen.getByText("コードと名前は必須です")).toBeInTheDocument();
      expect(mockCreate).not.toHaveBeenCalled();
    });

    test("shows validation error when end date before start date", async () => {
      const user = userEvent.setup();
      render(<ProjectForm {...defaultProps} />);

      await user.type(screen.getByLabelText("コード"), "NEW001");
      await user.type(screen.getByLabelText("名前"), "New Project");
      await user.type(screen.getByLabelText("開始日"), "2026-12-01");
      await user.type(screen.getByLabelText("終了日"), "2026-01-01");
      await user.click(screen.getByText("作成"));

      expect(screen.getByText("終了日は開始日以降にしてください")).toBeInTheDocument();
      expect(mockCreate).not.toHaveBeenCalled();
    });
  });

  describe("Edit mode", () => {
    const editProps = {
      project: existingProject,
      onClose: vi.fn(),
      onSaved: vi.fn(),
    };

    test("renders edit form with pre-filled fields", () => {
      render(<ProjectForm {...editProps} />);

      expect(screen.getByText("プロジェクト編集")).toBeInTheDocument();
      expect(screen.getByLabelText("コード")).toHaveValue("PRJ001");
      expect(screen.getByLabelText("コード")).toBeDisabled();
      expect(screen.getByLabelText("名前")).toHaveValue("Project Alpha");
      expect(screen.getByLabelText("開始日")).toHaveValue("2026-01-01");
      expect(screen.getByLabelText("終了日")).toHaveValue("2026-12-31");
      expect(screen.getByText("更新")).toBeInTheDocument();
    });

    test("updates project on submit", async () => {
      const user = userEvent.setup();
      const onSaved = vi.fn();
      render(<ProjectForm {...editProps} onSaved={onSaved} />);

      const nameInput = screen.getByLabelText("名前");
      await user.clear(nameInput);
      await user.type(nameInput, "Alpha Updated");
      await user.click(screen.getByText("更新"));

      await waitFor(() => {
        expect(mockUpdate).toHaveBeenCalledWith("p1", {
          name: "Alpha Updated",
          validFrom: "2026-01-01",
          validUntil: "2026-12-31",
        });
      });
      expect(onSaved).toHaveBeenCalled();
    });

    test("code field is disabled in edit mode", () => {
      render(<ProjectForm {...editProps} />);
      expect(screen.getByLabelText("コード")).toBeDisabled();
    });
  });

  test("calls onClose when cancel button clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<ProjectForm project={null} onClose={onClose} onSaved={vi.fn()} />);

    await user.click(screen.getByText("キャンセル"));
    expect(onClose).toHaveBeenCalled();
  });

  test("shows API error message on failure", async () => {
    const { ApiError } = await import("@/services/api");
    mockCreate.mockRejectedValue(new ApiError("Duplicate code", 409, "DUPLICATE_CODE"));

    const user = userEvent.setup();
    render(<ProjectForm project={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText("コード"), "DUP");
    await user.type(screen.getByLabelText("名前"), "Duplicate");
    await user.click(screen.getByText("作成"));

    await waitFor(() => {
      expect(screen.getByText("Duplicate code")).toBeInTheDocument();
    });
  });

  test("shows generic error for non-API errors", async () => {
    mockCreate.mockRejectedValue(new Error("Network failure"));

    const user = userEvent.setup();
    render(<ProjectForm project={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText("コード"), "NEW");
    await user.type(screen.getByLabelText("名前"), "Test");
    await user.click(screen.getByText("作成"));

    await waitFor(() => {
      expect(screen.getByText("エラーが発生しました")).toBeInTheDocument();
    });
  });

  test("disables submit button while submitting", async () => {
    // biome-ignore lint/style/useConst: reassigned in promise callback
    let resolveCreate: ((value: unknown) => void) | undefined;
    mockCreate.mockReturnValue(
      new Promise((resolve) => {
        resolveCreate = resolve;
      }),
    );

    const user = userEvent.setup();
    render(<ProjectForm project={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText("コード"), "NEW");
    await user.type(screen.getByLabelText("名前"), "Test");
    await user.click(screen.getByText("作成"));

    expect(screen.getByText("保存中...")).toBeInTheDocument();
    expect(screen.getByText("保存中...")).toBeDisabled();

    resolveCreate?.({ id: "new" });
  });
});
