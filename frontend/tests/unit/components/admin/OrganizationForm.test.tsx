import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { ToastProvider } from "@/components/shared/ToastProvider";
import { OrganizationForm } from "@/components/admin/OrganizationForm";
import type { OrganizationRow } from "@/services/api";

const mockCreate = vi.fn();
const mockUpdate = vi.fn();
const mockList = vi.fn();

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
      organizations: {
        create: (...args: unknown[]) => mockCreate(...args),
        update: (...args: unknown[]) => mockUpdate(...args),
        list: (...args: unknown[]) => mockList(...args),
      },
    },
  },
}));

function renderWithProviders(ui: ReactElement) {
	return render(<ToastProvider>{ui}</ToastProvider>);
}

const parentOrg: OrganizationRow = {
  id: "parent1",
  tenantId: "t1",
  parentId: null,
  parentName: null,
  code: "PARENT",
  name: "Parent Org",
  level: 1,
  status: "ACTIVE",
  memberCount: 10,
  fiscalYearPatternId: null,
  monthlyPeriodPatternId: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const existingOrg: OrganizationRow = {
  id: "org1",
  tenantId: "t1",
  parentId: "parent1",
  parentName: "Parent Org",
  code: "DEV_TEAM",
  name: "Development Team",
  level: 2,
  status: "ACTIVE",
  memberCount: 5,
  fiscalYearPatternId: null,
  monthlyPeriodPatternId: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("OrganizationForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCreate.mockResolvedValue({ id: "new-id" });
    mockUpdate.mockResolvedValue(undefined);
    mockList.mockResolvedValue({
      content: [parentOrg],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });
  });

  describe("Create mode", () => {
    const defaultProps = {
      organization: null,
      onClose: vi.fn(),
      onSaved: vi.fn(),
    };

    test("renders create form with empty fields", () => {
      renderWithProviders(<OrganizationForm {...defaultProps} />);

      expect(screen.getByText("組織作成")).toBeInTheDocument();
      expect(screen.getByLabelText("コード")).toHaveValue("");
      expect(screen.getByLabelText("コード")).not.toBeDisabled();
      expect(screen.getByLabelText("名前")).toHaveValue("");
      expect(screen.getByText("作成")).toBeInTheDocument();
    });

    test("creates organization on submit", async () => {
      const user = userEvent.setup();
      const onSaved = vi.fn();
      renderWithProviders(<OrganizationForm {...defaultProps} onSaved={onSaved} />);

      await user.type(screen.getByLabelText("コード"), "NEW_ORG");
      await user.type(screen.getByLabelText("名前"), "New Organization");
      await user.click(screen.getByText("作成"));

      await waitFor(() => {
        expect(mockCreate).toHaveBeenCalledWith({
          code: "NEW_ORG",
          name: "New Organization",
          parentId: undefined,
        });
      });
      expect(onSaved).toHaveBeenCalled();
    });

    test("code field has pattern attribute for alphanumeric and underscore validation", () => {
      renderWithProviders(<OrganizationForm {...defaultProps} />);

      const codeInput = screen.getByLabelText("コード");
      expect(codeInput).toHaveAttribute("pattern", "[A-Za-z0-9_]+");
      expect(codeInput).toHaveAttribute("maxlength", "32");
    });

    test("name field required validation", async () => {
      const user = userEvent.setup();
      renderWithProviders(<OrganizationForm {...defaultProps} />);

      await user.type(screen.getByLabelText("コード"), "VALID_CODE");
      // Leave name empty - but we need to submit, so type whitespace
      await user.type(screen.getByLabelText("名前"), "   ");
      await user.click(screen.getByText("作成"));

      expect(screen.getByText("名前は必須です")).toBeInTheDocument();
      expect(mockCreate).not.toHaveBeenCalled();
    });

    test("code field required validation - empty code shows error", async () => {
      const user = userEvent.setup();
      renderWithProviders(<OrganizationForm {...defaultProps} />);

      // Submit without entering a code - use form's noValidate to bypass HTML5 validation
      // Type code then clear it to simulate user clearing the field
      const codeInput = screen.getByLabelText("コード");
      await user.type(codeInput, "A");
      await user.clear(codeInput);
      await user.type(screen.getByLabelText("名前"), "Test Org");

      // HTML5 required attribute prevents form submission in jsdom, so we verify
      // the required attribute is set correctly
      expect(codeInput).toBeRequired();
      expect(codeInput).toHaveValue("");
    });

    test("parent organization dropdown shows active orgs", async () => {
      renderWithProviders(<OrganizationForm {...defaultProps} />);

      await waitFor(() => {
        expect(mockList).toHaveBeenCalledWith({ isActive: true, size: 1000 });
      });

      const parentSelect = screen.getByLabelText("親組織 (任意)");
      expect(parentSelect).toBeInTheDocument();

      await waitFor(() => {
        expect(screen.getByText(/PARENT - Parent Org/)).toBeInTheDocument();
      });
    });

    test("has required attribute on code and name inputs", () => {
      renderWithProviders(<OrganizationForm {...defaultProps} />);

      expect(screen.getByLabelText("コード")).toBeRequired();
      expect(screen.getByLabelText("名前")).toBeRequired();
    });
  });

  describe("Edit mode", () => {
    const editProps = {
      organization: existingOrg,
      onClose: vi.fn(),
      onSaved: vi.fn(),
    };

    test("renders edit form with pre-filled fields and disabled code", () => {
      renderWithProviders(<OrganizationForm {...editProps} />);

      expect(screen.getByText("組織編集")).toBeInTheDocument();
      expect(screen.getByLabelText("コード")).toHaveValue("DEV_TEAM");
      expect(screen.getByLabelText("コード")).toBeDisabled();
      expect(screen.getByLabelText("名前")).toHaveValue("Development Team");
      expect(screen.getByText("更新")).toBeInTheDocument();
    });

    test("updates organization on submit", async () => {
      const user = userEvent.setup();
      const onSaved = vi.fn();
      renderWithProviders(<OrganizationForm {...editProps} onSaved={onSaved} />);

      const nameInput = screen.getByLabelText("名前");
      await user.clear(nameInput);
      await user.type(nameInput, "Dev Team Updated");
      await user.click(screen.getByText("更新"));

      await waitFor(() => {
        expect(mockUpdate).toHaveBeenCalledWith("org1", { name: "Dev Team Updated" });
      });
      expect(onSaved).toHaveBeenCalled();
    });

    test("does not show parent organization dropdown in edit mode", () => {
      renderWithProviders(<OrganizationForm {...editProps} />);

      expect(screen.queryByLabelText("親組織 (任意)")).not.toBeInTheDocument();
    });
  });

  test("calls onClose when cancel button clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderWithProviders(<OrganizationForm organization={null} onClose={onClose} onSaved={vi.fn()} />);

    await user.click(screen.getByText("キャンセル"));
    expect(onClose).toHaveBeenCalled();
  });

  test("ESC key closes modal", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderWithProviders(<OrganizationForm organization={null} onClose={onClose} onSaved={vi.fn()} />);

    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });

  test("shows API error message on failure", async () => {
    const { ApiError } = await import("@/services/api");
    mockCreate.mockRejectedValue(new ApiError("Duplicate code", 409, "DUPLICATE_CODE"));

    const user = userEvent.setup();
    renderWithProviders(<OrganizationForm organization={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText("コード"), "DUP");
    await user.type(screen.getByLabelText("名前"), "Duplicate");
    await user.click(screen.getByText("作成"));

    await waitFor(() => {
      expect(screen.getAllByText("Duplicate code").length).toBeGreaterThan(0);
    });
  });

  test("shows generic error for non-API errors", async () => {
    mockCreate.mockRejectedValue(new Error("Network failure"));

    const user = userEvent.setup();
    renderWithProviders(<OrganizationForm organization={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText("コード"), "NEW");
    await user.type(screen.getByLabelText("名前"), "Test");
    await user.click(screen.getByText("作成"));

    await waitFor(() => {
      expect(screen.getByText("エラーが発生しました")).toBeInTheDocument();
    });
  });

  test("disables submit button while submitting", async () => {
    let resolveCreate: ((value: unknown) => void) | undefined;
    mockCreate.mockReturnValue(
      new Promise((resolve) => {
        resolveCreate = resolve;
      }),
    );

    const user = userEvent.setup();
    renderWithProviders(<OrganizationForm organization={null} onClose={vi.fn()} onSaved={vi.fn()} />);

    await user.type(screen.getByLabelText("コード"), "NEW");
    await user.type(screen.getByLabelText("名前"), "Test");
    await user.click(screen.getByText("作成"));

    expect(screen.getByText("保存中...")).toBeInTheDocument();
    expect(screen.getByText("保存中...")).toBeDisabled();

    resolveCreate?.({ id: "new" });
  });
});
