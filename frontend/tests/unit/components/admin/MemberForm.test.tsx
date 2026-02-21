import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemberForm } from "@/components/admin/MemberForm";
import type { MemberRow } from "@/components/admin/MemberList";

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
      members: {
        create: (...args: unknown[]) => mockCreate(...args),
        update: (...args: unknown[]) => mockUpdate(...args),
      },
    },
  },
}));

const existingMember: MemberRow = {
  id: "m1",
  email: "alice@test.com",
  displayName: "Alice",
  organizationId: null,
  managerId: null,
  managerName: null,
  isActive: true,
};

describe("MemberForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreate.mockResolvedValue({ id: "new-id" });
    mockUpdate.mockResolvedValue(undefined);
  });

  describe("Create mode", () => {
    const defaultProps = {
      member: null,
      onClose: vi.fn(),
      onSaved: vi.fn(),
    };

    test("renders create form with empty fields", () => {
      render(<MemberForm {...defaultProps} />);

      expect(screen.getByText("メンバー招待")).toBeInTheDocument();
      expect(screen.getByLabelText("メールアドレス")).toHaveValue("");
      expect(screen.getByLabelText("表示名")).toHaveValue("");
      expect(screen.getByText("招待")).toBeInTheDocument();
    });

    test("creates member on submit", async () => {
      const user = userEvent.setup();
      const onSaved = vi.fn();
      render(<MemberForm {...defaultProps} onSaved={onSaved} />);

      await user.type(screen.getByLabelText("メールアドレス"), "new@test.com");
      await user.type(screen.getByLabelText("表示名"), "New User");
      await user.click(screen.getByText("招待"));

      await waitFor(() => {
        expect(mockCreate).toHaveBeenCalledWith({
          email: "new@test.com",
          displayName: "New User",
        });
      });
      expect(onSaved).toHaveBeenCalled();
    });

    test("has required attribute on email and name inputs", () => {
      render(<MemberForm {...defaultProps} />);

      expect(screen.getByLabelText("メールアドレス")).toBeRequired();
      expect(screen.getByLabelText("表示名")).toBeRequired();
    });

    test("shows validation error when only whitespace entered", async () => {
      const user = userEvent.setup();
      render(<MemberForm {...defaultProps} />);

      // Type valid email then whitespace-only displayName
      await user.type(screen.getByLabelText("メールアドレス"), "a@b.com");
      await user.type(screen.getByLabelText("表示名"), "   ");
      await user.click(screen.getByText("招待"));

      expect(screen.getByText("メールと表示名は必須です")).toBeInTheDocument();
      expect(mockCreate).not.toHaveBeenCalled();
    });
  });

  describe("Edit mode", () => {
    const editProps = {
      member: existingMember,
      onClose: vi.fn(),
      onSaved: vi.fn(),
    };

    test("renders edit form with pre-filled fields", () => {
      render(<MemberForm {...editProps} />);

      expect(screen.getByText("メンバー編集")).toBeInTheDocument();
      expect(screen.getByLabelText("メールアドレス")).toHaveValue("alice@test.com");
      expect(screen.getByLabelText("表示名")).toHaveValue("Alice");
      expect(screen.getByText("更新")).toBeInTheDocument();
    });

    test("updates member on submit", async () => {
      const user = userEvent.setup();
      const onSaved = vi.fn();
      render(<MemberForm {...editProps} onSaved={onSaved} />);

      const nameInput = screen.getByLabelText("表示名");
      await user.clear(nameInput);
      await user.type(nameInput, "Alice Updated");
      await user.click(screen.getByText("更新"));

      await waitFor(() => {
        expect(mockUpdate).toHaveBeenCalledWith("m1", {
          email: "alice@test.com",
          displayName: "Alice Updated",
        });
      });
      expect(onSaved).toHaveBeenCalled();
    });
  });

  test("calls onClose when cancel button clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<MemberForm member={null} onClose={onClose} onSaved={vi.fn()} />);

    await user.click(screen.getByText("キャンセル"));
    expect(onClose).toHaveBeenCalled();
  });

  test("shows API error message on failure", async () => {
    const { ApiError } = await import("@/services/api");
    mockCreate.mockRejectedValue(new ApiError("Duplicate email", 409, "DUPLICATE_EMAIL"));

    const user = userEvent.setup();
    render(<MemberForm member={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText("メールアドレス"), "dup@test.com");
    await user.type(screen.getByLabelText("表示名"), "Dup");
    await user.click(screen.getByText("招待"));

    await waitFor(() => {
      expect(screen.getByText("Duplicate email")).toBeInTheDocument();
    });
  });

  test("shows generic error for non-API errors", async () => {
    mockCreate.mockRejectedValue(new Error("Network failure"));

    const user = userEvent.setup();
    render(<MemberForm member={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText("メールアドレス"), "test@test.com");
    await user.type(screen.getByLabelText("表示名"), "Test");
    await user.click(screen.getByText("招待"));

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
    render(<MemberForm member={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText("メールアドレス"), "test@test.com");
    await user.type(screen.getByLabelText("表示名"), "Test");
    await user.click(screen.getByText("招待"));

    expect(screen.getByText("保存中...")).toBeInTheDocument();
    expect(screen.getByText("保存中...")).toBeDisabled();

    resolveCreate?.({ id: "new" });
  });
});
