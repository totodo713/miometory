import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OrganizationList } from "@/components/admin/OrganizationList";

const mockListOrganizations = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      organizations: {
        list: (...args: unknown[]) => mockListOrganizations(...args),
      },
    },
  },
}));

const activeOrg = {
  id: "org1",
  tenantId: "t1",
  parentId: null,
  parentName: null,
  code: "DEV_TEAM",
  name: "Development Team",
  level: 1,
  status: "ACTIVE" as const,
  memberCount: 5,
  fiscalYearPatternId: null,
  monthlyPeriodPatternId: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const inactiveOrg = {
  id: "org2",
  tenantId: "t1",
  parentId: "org1",
  parentName: "Development Team",
  code: "QA_TEAM",
  name: "QA Team",
  level: 2,
  status: "INACTIVE" as const,
  memberCount: 0,
  fiscalYearPatternId: null,
  monthlyPeriodPatternId: null,
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

const defaultProps = {
  onEdit: vi.fn(),
  onDeactivate: vi.fn(),
  onActivate: vi.fn(),
  refreshKey: 0,
};

describe("OrganizationList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListOrganizations.mockResolvedValue({
      content: [activeOrg],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });
  });

  test("shows loading state then renders organization data", async () => {
    render(<OrganizationList {...defaultProps} />);
    expect(screen.getByText("読み込み中...")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("DEV_TEAM")).toBeInTheDocument();
    });
    expect(screen.getByText("Development Team")).toBeInTheDocument();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("有効")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
  });

  test("shows empty state when no organizations found", async () => {
    mockListOrganizations.mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
      number: 0,
    });

    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("組織が見つかりません")).toBeInTheDocument();
    });
  });

  test("displays parent org name when present", async () => {
    mockListOrganizations.mockResolvedValue({
      content: [inactiveOrg],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });

    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Development Team")).toBeInTheDocument();
    });
  });

  test("displays dash when org has no parent", async () => {
    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("DEV_TEAM")).toBeInTheDocument();
    });
    // parentName is null, displayed as "—"
    expect(screen.getByText("\u2014")).toBeInTheDocument();
  });

  test("shows deactivate button for active organizations", async () => {
    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("無効化")).toBeInTheDocument();
    });
    expect(screen.queryByText("有効化")).not.toBeInTheDocument();
  });

  test("shows activate button for inactive organizations", async () => {
    mockListOrganizations.mockResolvedValue({
      content: [inactiveOrg],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });

    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("有効化")).toBeInTheDocument();
    });
    expect(screen.queryByText("無効化")).not.toBeInTheDocument();
  });

  test("calls onEdit when edit button clicked", async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    render(<OrganizationList {...defaultProps} onEdit={onEdit} />);

    await waitFor(() => {
      expect(screen.getByText("編集")).toBeInTheDocument();
    });
    await user.click(screen.getByText("編集"));

    expect(onEdit).toHaveBeenCalledWith(activeOrg);
  });

  test("calls onDeactivate when deactivate button clicked", async () => {
    const user = userEvent.setup();
    const onDeactivate = vi.fn();
    render(<OrganizationList {...defaultProps} onDeactivate={onDeactivate} />);

    await waitFor(() => {
      expect(screen.getByText("無効化")).toBeInTheDocument();
    });
    await user.click(screen.getByText("無効化"));

    expect(onDeactivate).toHaveBeenCalledWith("org1");
  });

  test("calls onActivate when activate button clicked", async () => {
    const user = userEvent.setup();
    const onActivate = vi.fn();
    mockListOrganizations.mockResolvedValue({
      content: [inactiveOrg],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });

    render(<OrganizationList {...defaultProps} onActivate={onActivate} />);

    await waitFor(() => {
      expect(screen.getByText("有効化")).toBeInTheDocument();
    });
    await user.click(screen.getByText("有効化"));

    expect(onActivate).toHaveBeenCalledWith("org2");
  });

  test("passes search param to API when searching", async () => {
    const user = userEvent.setup();
    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("DEV_TEAM")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText("組織名またはコードで検索...");
    await user.type(searchInput, "dev");

    await waitFor(() => {
      expect(mockListOrganizations).toHaveBeenCalledWith(expect.objectContaining({ search: "dev", page: 0 }));
    });
  });

  test("passes isActive=undefined when showInactive checked", async () => {
    const user = userEvent.setup();
    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("DEV_TEAM")).toBeInTheDocument();
    });

    expect(mockListOrganizations).toHaveBeenCalledWith(expect.objectContaining({ isActive: true }));

    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);

    await waitFor(() => {
      expect(mockListOrganizations).toHaveBeenCalledWith(expect.objectContaining({ isActive: undefined }));
    });
  });

  test("shows pagination when multiple pages exist", async () => {
    mockListOrganizations.mockResolvedValue({
      content: [activeOrg],
      totalPages: 3,
      totalElements: 60,
      number: 0,
    });

    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("1 / 3")).toBeInTheDocument();
    });
    expect(screen.getByText("前へ")).toBeDisabled();
    expect(screen.getByText("次へ")).not.toBeDisabled();
  });

  test("navigates pages with pagination buttons", async () => {
    const user = userEvent.setup();
    mockListOrganizations.mockResolvedValue({
      content: [activeOrg],
      totalPages: 3,
      totalElements: 60,
      number: 0,
    });

    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("1 / 3")).toBeInTheDocument();
    });

    await user.click(screen.getByText("次へ"));

    await waitFor(() => {
      expect(mockListOrganizations).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }));
    });
  });

  test("hides pagination when only one page", async () => {
    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("DEV_TEAM")).toBeInTheDocument();
    });
    expect(screen.queryByText("前へ")).not.toBeInTheDocument();
    expect(screen.queryByText("次へ")).not.toBeInTheDocument();
  });

  test("reloads when refreshKey changes", async () => {
    const { rerender } = render(<OrganizationList {...defaultProps} refreshKey={0} />);

    await waitFor(() => {
      expect(mockListOrganizations).toHaveBeenCalledTimes(1);
    });

    rerender(<OrganizationList {...defaultProps} refreshKey={1} />);

    await waitFor(() => {
      expect(mockListOrganizations).toHaveBeenCalledTimes(2);
    });
  });

  test("renders table headers correctly", async () => {
    render(<OrganizationList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("DEV_TEAM")).toBeInTheDocument();
    });
    expect(screen.getByText("コード")).toBeInTheDocument();
    expect(screen.getByText("名前")).toBeInTheDocument();
    expect(screen.getByText("レベル")).toBeInTheDocument();
    expect(screen.getByText("親組織")).toBeInTheDocument();
    expect(screen.getByText("メンバー数")).toBeInTheDocument();
    expect(screen.getByText("ステータス")).toBeInTheDocument();
    expect(screen.getByText("アクション")).toBeInTheDocument();
  });
});
