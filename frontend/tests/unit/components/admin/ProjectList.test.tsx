import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { ProjectList } from "@/components/admin/ProjectList";
import { ToastProvider } from "@/components/shared/ToastProvider";
import { IntlWrapper } from "../../../helpers/intl";

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

const mockListProjects = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      projects: {
        list: (...args: unknown[]) => mockListProjects(...args),
      },
    },
  },
}));

const activeProject = {
  id: "p1",
  code: "PRJ001",
  name: "Project Alpha",
  isActive: true,
  validFrom: "2026-01-01",
  validUntil: "2026-12-31",
  assignedMemberCount: 5,
};

const inactiveProject = {
  id: "p2",
  code: "PRJ002",
  name: "Project Beta",
  isActive: false,
  validFrom: null,
  validUntil: null,
  assignedMemberCount: 0,
};

function renderWithProviders(ui: ReactElement) {
  return render(
    <IntlWrapper>
      <ToastProvider>{ui}</ToastProvider>
    </IntlWrapper>,
  );
}

const defaultProps = {
  onEdit: vi.fn(),
  onDeactivate: vi.fn(),
  onActivate: vi.fn(),
  refreshKey: 0,
};

describe("ProjectList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListProjects.mockResolvedValue({
      content: [activeProject],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });
  });

  test("shows loading state then renders project data", async () => {
    const { container } = renderWithProviders(<ProjectList {...defaultProps} />);
    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("PRJ001")).toBeInTheDocument();
    });
    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
    expect(screen.getByText("有効")).toBeInTheDocument();
  });

  test("shows empty state when no projects found", async () => {
    mockListProjects.mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
      number: 0,
    });

    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("プロジェクトが見つかりません")).toBeInTheDocument();
    });
  });

  test("displays valid period for project with dates", async () => {
    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("PRJ001")).toBeInTheDocument();
    });
    expect(screen.getByText(/2026-01-01/)).toBeInTheDocument();
    expect(screen.getByText(/2026-12-31/)).toBeInTheDocument();
  });

  test("displays dashes when project has no valid period", async () => {
    mockListProjects.mockResolvedValue({
      content: [inactiveProject],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });

    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("PRJ002")).toBeInTheDocument();
    });
    // validFrom and validUntil are null, displayed as "— ~ —"
    expect(screen.getByText(/— ~ —/)).toBeInTheDocument();
  });

  test("shows deactivate button for active projects", async () => {
    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("無効化")).toBeInTheDocument();
    });
    expect(screen.queryByText("有効化")).not.toBeInTheDocument();
  });

  test("shows activate button for inactive projects", async () => {
    mockListProjects.mockResolvedValue({
      content: [inactiveProject],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });

    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("有効化")).toBeInTheDocument();
    });
    expect(screen.queryByText("無効化")).not.toBeInTheDocument();
  });

  test("calls onEdit when edit button clicked", async () => {
    const user = userEvent.setup();
    const onEdit = vi.fn();
    renderWithProviders(<ProjectList {...defaultProps} onEdit={onEdit} />);

    await waitFor(() => {
      expect(screen.getByText("編集")).toBeInTheDocument();
    });
    await user.click(screen.getByText("編集"));

    expect(onEdit).toHaveBeenCalledWith(activeProject);
  });

  test("calls onDeactivate when deactivate button clicked", async () => {
    const user = userEvent.setup();
    const onDeactivate = vi.fn();
    renderWithProviders(<ProjectList {...defaultProps} onDeactivate={onDeactivate} />);

    await waitFor(() => {
      expect(screen.getByText("無効化")).toBeInTheDocument();
    });
    await user.click(screen.getByText("無効化"));

    expect(onDeactivate).toHaveBeenCalledWith("p1");
  });

  test("calls onActivate when activate button clicked", async () => {
    const user = userEvent.setup();
    const onActivate = vi.fn();
    mockListProjects.mockResolvedValue({
      content: [inactiveProject],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });

    renderWithProviders(<ProjectList {...defaultProps} onActivate={onActivate} />);

    await waitFor(() => {
      expect(screen.getByText("有効化")).toBeInTheDocument();
    });
    await user.click(screen.getByText("有効化"));

    expect(onActivate).toHaveBeenCalledWith("p2");
  });

  test("passes search param to API when searching", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("PRJ001")).toBeInTheDocument();
    });

    const searchInput = screen.getByPlaceholderText("コードまたは名前で検索...");
    await user.type(searchInput, "alpha");

    await waitFor(() => {
      expect(mockListProjects).toHaveBeenCalledWith(expect.objectContaining({ search: "alpha", page: 0 }));
    });
  });

  test("passes isActive=undefined when showInactive checked", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("PRJ001")).toBeInTheDocument();
    });

    expect(mockListProjects).toHaveBeenCalledWith(expect.objectContaining({ isActive: true }));

    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);

    await waitFor(() => {
      expect(mockListProjects).toHaveBeenCalledWith(expect.objectContaining({ isActive: undefined }));
    });
  });

  test("shows pagination when multiple pages exist", async () => {
    mockListProjects.mockResolvedValue({
      content: [activeProject],
      totalPages: 3,
      totalElements: 60,
      number: 0,
    });

    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("1 / 3")).toBeInTheDocument();
    });
    expect(screen.getByText("前へ")).toBeDisabled();
    expect(screen.getByText("次へ")).not.toBeDisabled();
  });

  test("navigates pages with pagination buttons", async () => {
    const user = userEvent.setup();
    mockListProjects.mockResolvedValue({
      content: [activeProject],
      totalPages: 3,
      totalElements: 60,
      number: 0,
    });

    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("1 / 3")).toBeInTheDocument();
    });

    await user.click(screen.getByText("次へ"));

    await waitFor(() => {
      expect(mockListProjects).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }));
    });
  });

  test("hides pagination when only one page", async () => {
    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("PRJ001")).toBeInTheDocument();
    });
    expect(screen.queryByText("前へ")).not.toBeInTheDocument();
    expect(screen.queryByText("次へ")).not.toBeInTheDocument();
  });

  test("reloads when refreshKey changes", async () => {
    const { rerender } = renderWithProviders(<ProjectList {...defaultProps} refreshKey={0} />);

    await waitFor(() => {
      expect(mockListProjects).toHaveBeenCalledTimes(1);
    });

    rerender(
      <IntlWrapper>
        <ToastProvider>
          <ProjectList {...defaultProps} refreshKey={1} />
        </ToastProvider>
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(mockListProjects).toHaveBeenCalledTimes(2);
    });
  });

  test("renders table headers correctly", async () => {
    renderWithProviders(<ProjectList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("PRJ001")).toBeInTheDocument();
    });
    expect(screen.getByText("コード")).toBeInTheDocument();
    expect(screen.getByText("プロジェクト名")).toBeInTheDocument();
    expect(screen.getByText("有効期間")).toBeInTheDocument();
    expect(screen.getByText("メンバー数")).toBeInTheDocument();
    expect(screen.getByText("ステータス")).toBeInTheDocument();
    expect(screen.getByText("操作")).toBeInTheDocument();
  });
});
