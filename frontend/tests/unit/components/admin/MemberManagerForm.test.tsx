import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemberManagerForm } from "@/components/admin/MemberManagerForm";

const mockListMembers = vi.fn();
const mockListOrganizations = vi.fn();
const mockAssignManager = vi.fn();
const mockTransferMember = vi.fn();
const mockCreateMember = vi.fn();

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
        list: (...args: unknown[]) => mockListMembers(...args),
        create: (...args: unknown[]) => mockCreateMember(...args),
        assignManager: (...args: unknown[]) => mockAssignManager(...args),
        transferMember: (...args: unknown[]) => mockTransferMember(...args),
      },
      organizations: {
        list: (...args: unknown[]) => mockListOrganizations(...args),
      },
    },
  },
}));

const targetMember = {
  id: "m1",
  email: "alice@test.com",
  displayName: "Alice",
  organizationId: "org1",
  managerId: "mgr1",
  managerDisplayName: "Bob",
  managerIsActive: true,
  status: "ACTIVE",
  version: 1,
};

const availableMember = {
  id: "m2",
  email: "charlie@test.com",
  displayName: "Charlie",
  organizationId: "org1",
  managerId: null,
  managerName: null,
  isActive: true,
};

const otherOrg = {
  id: "org2",
  tenantId: "t1",
  parentId: null,
  parentName: null,
  code: "OTHER",
  name: "Other Org",
  level: 1,
  status: "ACTIVE" as const,
  memberCount: 3,
  fiscalYearPatternId: null,
  monthlyPeriodPatternId: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const currentOrg = {
  id: "org1",
  tenantId: "t1",
  parentId: null,
  parentName: null,
  code: "CURRENT",
  name: "Current Org",
  level: 1,
  status: "ACTIVE" as const,
  memberCount: 5,
  fiscalYearPatternId: null,
  monthlyPeriodPatternId: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

describe("MemberManagerForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListMembers.mockResolvedValue({
      content: [availableMember],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });
    mockListOrganizations.mockResolvedValue({
      content: [currentOrg, otherOrg],
      totalPages: 1,
      totalElements: 2,
      number: 0,
    });
    mockAssignManager.mockResolvedValue(undefined);
    mockTransferMember.mockResolvedValue(undefined);
    mockCreateMember.mockResolvedValue({ id: "new-id" });
  });

  describe("assignManager mode", () => {
    const defaultProps = {
      mode: "assignManager" as const,
      organizationId: "org1",
      member: targetMember,
      onClose: vi.fn(),
      onSaved: vi.fn(),
    };

    test("renders in assignManager mode with manager dropdown", async () => {
      render(<MemberManagerForm {...defaultProps} />);

      expect(screen.getByText("マネージャー割り当て")).toBeInTheDocument();
      expect(screen.getByLabelText("マネージャー")).toBeInTheDocument();

      await waitFor(() => {
        expect(screen.getByText(/Charlie \(charlie@test.com\)/)).toBeInTheDocument();
      });
    });

    test("shows target member info", () => {
      render(<MemberManagerForm {...defaultProps} />);

      expect(screen.getByText("Alice")).toBeInTheDocument();
      expect(screen.getByText("alice@test.com")).toBeInTheDocument();
    });

    test("shows current manager info", async () => {
      render(<MemberManagerForm {...defaultProps} />);

      await waitFor(() => {
        expect(screen.getByText(/現在のマネージャー: Bob/)).toBeInTheDocument();
      });
    });

    test("manager selector dropdown renders available members (excluding self)", async () => {
      render(<MemberManagerForm {...defaultProps} />);

      await waitFor(() => {
        expect(mockListMembers).toHaveBeenCalledWith({ isActive: true, size: 1000 });
      });

      // Available members should exclude the target member
      await waitFor(() => {
        expect(screen.getByText(/Charlie \(charlie@test.com\)/)).toBeInTheDocument();
      });
    });

    test("submit button shows correct label", () => {
      render(<MemberManagerForm {...defaultProps} />);
      expect(screen.getByText("割り当て")).toBeInTheDocument();
    });

    test("submits manager assignment", async () => {
      const user = userEvent.setup();
      const onSaved = vi.fn();
      render(<MemberManagerForm {...defaultProps} onSaved={onSaved} />);

      await waitFor(() => {
        expect(screen.getByText(/Charlie \(charlie@test.com\)/)).toBeInTheDocument();
      });

      await user.selectOptions(screen.getByLabelText("マネージャー"), "m2");
      await user.click(screen.getByText("割り当て"));

      await waitFor(() => {
        expect(mockAssignManager).toHaveBeenCalledWith("m1", "m2");
      });
      expect(onSaved).toHaveBeenCalled();
    });

    test("shows error when no manager selected and submit clicked", async () => {
      const user = userEvent.setup();
      render(<MemberManagerForm {...defaultProps} />);

      await user.click(screen.getByText("割り当て"));

      expect(screen.getByText("マネージャーを選択してください")).toBeInTheDocument();
    });
  });

  describe("transferOrg mode", () => {
    const defaultProps = {
      mode: "transferOrg" as const,
      organizationId: "org1",
      member: targetMember,
      onClose: vi.fn(),
      onSaved: vi.fn(),
    };

    test("renders in transferOrg mode with organization dropdown", async () => {
      render(<MemberManagerForm {...defaultProps} />);

      expect(screen.getByText("組織異動")).toBeInTheDocument();
      expect(screen.getByLabelText("移動先組織")).toBeInTheDocument();

      await waitFor(() => {
        expect(screen.getByText(/OTHER - Other Org/)).toBeInTheDocument();
      });
    });

    test("transfer org selector renders available orgs excluding current", async () => {
      render(<MemberManagerForm {...defaultProps} />);

      await waitFor(() => {
        expect(mockListOrganizations).toHaveBeenCalledWith({ isActive: true, size: 1000 });
      });

      // Should not include the current org
      await waitFor(() => {
        expect(screen.getByText(/OTHER - Other Org/)).toBeInTheDocument();
      });
      expect(screen.queryByText(/CURRENT - Current Org/)).not.toBeInTheDocument();
    });

    test("submit button shows correct label", () => {
      render(<MemberManagerForm {...defaultProps} />);
      expect(screen.getByText("異動")).toBeInTheDocument();
    });

    test("submits org transfer", async () => {
      const user = userEvent.setup();
      const onSaved = vi.fn();
      render(<MemberManagerForm {...defaultProps} onSaved={onSaved} />);

      await waitFor(() => {
        expect(screen.getByText(/OTHER - Other Org/)).toBeInTheDocument();
      });

      await user.selectOptions(screen.getByLabelText("移動先組織"), "org2");
      await user.click(screen.getByText("異動"));

      await waitFor(() => {
        expect(mockTransferMember).toHaveBeenCalledWith("m1", "org2");
      });
      expect(onSaved).toHaveBeenCalled();
    });

    test("shows error when no org selected and submit clicked", async () => {
      const user = userEvent.setup();
      render(<MemberManagerForm {...defaultProps} />);

      await user.click(screen.getByText("異動"));

      expect(screen.getByText("移動先の組織を選択してください")).toBeInTheDocument();
    });
  });

  describe("createMember mode", () => {
    const defaultProps = {
      mode: "createMember" as const,
      organizationId: "org1",
      onClose: vi.fn(),
      onSaved: vi.fn(),
    };

    test("renders in createMember mode with email, name, manager fields", async () => {
      render(<MemberManagerForm {...defaultProps} />);

      expect(screen.getByText("メンバー作成")).toBeInTheDocument();
      expect(screen.getByLabelText("メールアドレス")).toBeInTheDocument();
      expect(screen.getByLabelText("表示名")).toBeInTheDocument();
      expect(screen.getByLabelText("マネージャー (任意)")).toBeInTheDocument();
    });

    test("submit button shows correct label", () => {
      render(<MemberManagerForm {...defaultProps} />);
      expect(screen.getByText("作成")).toBeInTheDocument();
    });

    test("submits new member creation", async () => {
      const user = userEvent.setup();
      const onSaved = vi.fn();
      render(<MemberManagerForm {...defaultProps} onSaved={onSaved} />);

      await user.type(screen.getByLabelText("メールアドレス"), "new@test.com");
      await user.type(screen.getByLabelText("表示名"), "New User");
      await user.click(screen.getByText("作成"));

      await waitFor(() => {
        expect(mockCreateMember).toHaveBeenCalledWith({
          email: "new@test.com",
          displayName: "New User",
          organizationId: "org1",
          managerId: undefined,
        });
      });
      expect(onSaved).toHaveBeenCalled();
    });

    test("shows error when required fields are empty", async () => {
      const user = userEvent.setup();
      render(<MemberManagerForm {...defaultProps} />);

      await user.type(screen.getByLabelText("メールアドレス"), "new@test.com");
      await user.type(screen.getByLabelText("表示名"), "   ");
      await user.click(screen.getByText("作成"));

      expect(screen.getByText("メールアドレスと表示名は必須です")).toBeInTheDocument();
      expect(mockCreateMember).not.toHaveBeenCalled();
    });

    test("manager dropdown loads available members", async () => {
      render(<MemberManagerForm {...defaultProps} />);

      await waitFor(() => {
        expect(mockListMembers).toHaveBeenCalledWith({ isActive: true, size: 1000 });
      });

      await waitFor(() => {
        expect(screen.getByText(/Charlie \(charlie@test.com\)/)).toBeInTheDocument();
      });
    });
  });

  test("ESC key closes modal", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <MemberManagerForm
        mode="assignManager"
        organizationId="org1"
        member={targetMember}
        onClose={onClose}
        onSaved={vi.fn()}
      />,
    );

    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalled();
  });

  test("calls onClose when cancel button clicked", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(
      <MemberManagerForm
        mode="assignManager"
        organizationId="org1"
        member={targetMember}
        onClose={onClose}
        onSaved={vi.fn()}
      />,
    );

    await user.click(screen.getByText("キャンセル"));
    expect(onClose).toHaveBeenCalled();
  });

  test("circular reference error display from API", async () => {
    const { ApiError } = await import("@/services/api");
    mockAssignManager.mockRejectedValue(new ApiError("Circular reference detected", 400, "CIRCULAR_REFERENCE"));

    const user = userEvent.setup();
    render(
      <MemberManagerForm
        mode="assignManager"
        organizationId="org1"
        member={targetMember}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText(/Charlie \(charlie@test.com\)/)).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("マネージャー"), "m2");
    await user.click(screen.getByText("割り当て"));

    await waitFor(() => {
      expect(screen.getByText("Circular reference detected")).toBeInTheDocument();
    });
  });

  test("shows generic error for non-API errors", async () => {
    mockAssignManager.mockRejectedValue(new Error("Network failure"));

    const user = userEvent.setup();
    render(
      <MemberManagerForm
        mode="assignManager"
        organizationId="org1"
        member={targetMember}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText(/Charlie \(charlie@test.com\)/)).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("マネージャー"), "m2");
    await user.click(screen.getByText("割り当て"));

    await waitFor(() => {
      expect(screen.getByText("エラーが発生しました")).toBeInTheDocument();
    });
  });

  test("disables submit button while submitting", async () => {
    let resolveAssign: ((value: unknown) => void) | undefined;
    mockAssignManager.mockReturnValue(
      new Promise((resolve) => {
        resolveAssign = resolve;
      }),
    );

    const user = userEvent.setup();
    render(
      <MemberManagerForm
        mode="assignManager"
        organizationId="org1"
        member={targetMember}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );

    await waitFor(() => {
      expect(screen.getByText(/Charlie \(charlie@test.com\)/)).toBeInTheDocument();
    });

    await user.selectOptions(screen.getByLabelText("マネージャー"), "m2");
    await user.click(screen.getByText("割り当て"));

    expect(screen.getByText("処理中...")).toBeInTheDocument();
    expect(screen.getByText("処理中...")).toBeDisabled();

    resolveAssign?.(undefined);
  });
});
