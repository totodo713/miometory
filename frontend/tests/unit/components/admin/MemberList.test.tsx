import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { MemberList } from "@/components/admin/MemberList";
import { ToastProvider } from "@/components/shared/ToastProvider";

Object.defineProperty(window, "matchMedia", {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
});

const mockListMembers = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      members: {
        list: (...args: unknown[]) => mockListMembers(...args),
      },
    },
  },
}));

const activeMember = {
  id: "m1",
  email: "alice@test.com",
  displayName: "Alice",
  organizationId: null,
  managerId: "mgr1",
  managerName: "Bob",
  isActive: true,
};

const inactiveMember = {
  id: "m2",
  email: "charlie@test.com",
  displayName: "Charlie",
  organizationId: null,
  managerId: null,
  managerName: null,
  isActive: false,
};

function renderWithProviders(ui: ReactElement) {
  return render(<ToastProvider>{ui}</ToastProvider>);
}

const defaultProps = {
  onEdit: vi.fn(),
  onDeactivate: vi.fn(),
  onActivate: vi.fn(),
  refreshKey: 0,
};

describe("MemberList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListMembers.mockResolvedValue({
      content: [activeMember],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });
  });

  test("shows loading state then renders member data", async () => {
    const { container } = renderWithProviders(<MemberList {...defaultProps} />);
    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeInTheDocument();
    });
    expect(screen.getByText("alice@test.com")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();
    expect(screen.getByText("有効")).toBeInTheDocument();
  });

  test("shows empty state when no members found", async () => {
    mockListMembers.mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
      number: 0,
    });

    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("メンバーが見つかりません")).toBeInTheDocument();
    });
  });

  test("displays dash when member has no manager", async () => {
    mockListMembers.mockResolvedValue({
      content: [{ ...activeMember, managerName: null }],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });

    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("—")).toBeInTheDocument();
    });
  });

  test("shows deactivate button for active members", async () => {
    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("無効化")).toBeInTheDocument();
    });
    expect(screen.queryByText("有効化")).not.toBeInTheDocument();
  });

  test("shows activate button for inactive members", async () => {
    mockListMembers.mockResolvedValue({
      content: [inactiveMember],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });

    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("有効化")).toBeInTheDocument();
    });
    expect(screen.queryByText("無効化")).not.toBeInTheDocument();
  });

  test("calls onEdit when edit button clicked", async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    renderWithProviders(<MemberList {...defaultProps} onEdit={onEdit} />);

    await waitFor(() => {
      expect(screen.getByText("編集")).toBeInTheDocument();
    });
    await user.click(screen.getByText("編集"));

    expect(onEdit).toHaveBeenCalledWith(activeMember);
  });

  test("calls onDeactivate when deactivate button clicked", async () => {
    const user = userEvent.setup();
    const onDeactivate = vi.fn();
    renderWithProviders(<MemberList {...defaultProps} onDeactivate={onDeactivate} />);

    await waitFor(() => {
      expect(screen.getByText("無効化")).toBeInTheDocument();
    });
    await user.click(screen.getByText("無効化"));

    expect(onDeactivate).toHaveBeenCalledWith("m1");
  });

  test("calls onActivate when activate button clicked", async () => {
    const user = userEvent.setup();
    const onActivate = vi.fn();
    mockListMembers.mockResolvedValue({
      content: [inactiveMember],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });

    renderWithProviders(<MemberList {...defaultProps} onActivate={onActivate} />);

    await waitFor(() => {
      expect(screen.getByText("有効化")).toBeInTheDocument();
    });
    await user.click(screen.getByText("有効化"));

    expect(onActivate).toHaveBeenCalledWith("m2");
  });

  test("passes search param to API when searching", async () => {
    const user = userEvent.setup();
    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText("名前またはメールで検索...");
    await user.type(searchInput, "alice");

    await waitFor(() => {
      expect(mockListMembers).toHaveBeenCalledWith(expect.objectContaining({ search: "alice", page: 0 }));
    });
  });

  test("passes isActive=undefined when showInactive checked", async () => {
    const user = userEvent.setup();
    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeInTheDocument();
    });

    // Initial call should pass isActive: true
    expect(mockListMembers).toHaveBeenCalledWith(expect.objectContaining({ isActive: true }));

    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);

    await waitFor(() => {
      expect(mockListMembers).toHaveBeenCalledWith(expect.objectContaining({ isActive: undefined }));
    });
  });

  test("shows pagination when multiple pages exist", async () => {
    mockListMembers.mockResolvedValue({
      content: [activeMember],
      totalPages: 3,
      totalElements: 60,
      number: 0,
    });

    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("1 / 3")).toBeInTheDocument();
    });
    expect(screen.getByText("前へ")).toBeDisabled();
    expect(screen.getByText("次へ")).not.toBeDisabled();
  });

  test("navigates pages with pagination buttons", async () => {
    const user = userEvent.setup();
    mockListMembers.mockResolvedValue({
      content: [activeMember],
      totalPages: 3,
      totalElements: 60,
      number: 0,
    });

    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("1 / 3")).toBeInTheDocument();
    });

    await user.click(screen.getByText("次へ"));

    await waitFor(() => {
      expect(mockListMembers).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }));
    });
  });

  test("hides pagination when only one page", async () => {
    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeInTheDocument();
    });
    expect(screen.queryByText("前へ")).not.toBeInTheDocument();
    expect(screen.queryByText("次へ")).not.toBeInTheDocument();
  });

  test("reloads when refreshKey changes", async () => {
    const { rerender } = renderWithProviders(<MemberList {...defaultProps} refreshKey={0} />);

    await waitFor(() => {
      expect(mockListMembers).toHaveBeenCalledTimes(1);
    });

    rerender(
      <ToastProvider>
        <MemberList {...defaultProps} refreshKey={1} />
      </ToastProvider>,
    );

    await waitFor(() => {
      expect(mockListMembers).toHaveBeenCalledTimes(2);
    });
  });

  test("renders table headers correctly", async () => {
    renderWithProviders(<MemberList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Alice")).toBeInTheDocument();
    });
    expect(screen.getByText("名前")).toBeInTheDocument();
    expect(screen.getByText("メール")).toBeInTheDocument();
    expect(screen.getByText("上司")).toBeInTheDocument();
    expect(screen.getByText("状態")).toBeInTheDocument();
    expect(screen.getByText("操作")).toBeInTheDocument();
  });
});
