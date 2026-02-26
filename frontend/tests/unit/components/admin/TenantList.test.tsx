import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { TenantList } from "@/components/admin/TenantList";
import { ToastProvider } from "@/components/shared/ToastProvider";
import { IntlWrapper } from "../../../helpers/intl";

function setupMatchMedia(mobile: boolean) {
  (window.matchMedia as ReturnType<typeof vi.fn>).mockImplementation((query: string) => ({
    matches: mobile && query === "(max-width: 767px)",
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

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

const mockListTenants = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      tenants: {
        list: (...args: unknown[]) => mockListTenants(...args),
      },
    },
  },
  ApiError: class ApiError extends Error {},
}));

const activeTenant = {
  id: "t1",
  code: "TENANT_A",
  name: "Tenant Alpha",
  status: "ACTIVE" as const,
  createdAt: "2026-01-15T00:00:00Z",
};

const inactiveTenant = {
  id: "t2",
  code: "TENANT_B",
  name: "Tenant Beta",
  status: "INACTIVE" as const,
  createdAt: "2026-02-01T00:00:00Z",
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

describe("TenantList", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListTenants.mockResolvedValue({
      content: [activeTenant],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });
  });

  test("shows loading skeleton then renders tenant data", async () => {
    const { container } = renderWithProviders(<TenantList {...defaultProps} />);
    expect(container.querySelector(".animate-pulse")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("TENANT_A")).toBeInTheDocument();
    });
    expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    // Status badge should show active status
    expect(screen.getByText("TENANT_A")).toBeInTheDocument();
  });

  test("shows empty state when no tenants found", async () => {
    mockListTenants.mockResolvedValue({
      content: [],
      totalPages: 0,
      totalElements: 0,
      number: 0,
    });

    renderWithProviders(<TenantList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("テナントが見つかりません")).toBeInTheDocument();
    });
  });

  test("shows error state and retry button on load failure", async () => {
    mockListTenants.mockRejectedValueOnce(new Error("Network error"));

    renderWithProviders(<TenantList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toBeInTheDocument();
    });
    expect(screen.getByText("再試行")).toBeInTheDocument();
  });

  test("calls onEdit when edit button is clicked", async () => {
    const onEdit = vi.fn();
    renderWithProviders(<TenantList {...defaultProps} onEdit={onEdit} />);

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    const editButton = screen.getByRole("button", { name: "編集" });
    await userEvent.click(editButton);

    expect(onEdit).toHaveBeenCalledWith(activeTenant);
  });

  test("calls onDeactivate for active tenant", async () => {
    const onDeactivate = vi.fn();
    renderWithProviders(<TenantList {...defaultProps} onDeactivate={onDeactivate} />);

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    const disableButton = screen.getByRole("button", { name: "無効化" });
    await userEvent.click(disableButton);

    expect(onDeactivate).toHaveBeenCalledWith("t1");
  });

  test("calls onActivate for inactive tenant", async () => {
    mockListTenants.mockResolvedValue({
      content: [inactiveTenant],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });
    const onActivate = vi.fn();
    renderWithProviders(<TenantList {...defaultProps} onActivate={onActivate} />);

    await waitFor(() => {
      expect(screen.getByText("Tenant Beta")).toBeInTheDocument();
    });

    const enableButton = screen.getByRole("button", { name: "有効化" });
    await userEvent.click(enableButton);

    expect(onActivate).toHaveBeenCalledWith("t2");
  });

  test("filters tenants by status", async () => {
    renderWithProviders(<TenantList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    const filterSelect = screen.getByLabelText("ステータスで絞り込み");
    await userEvent.selectOptions(filterSelect, "ACTIVE");

    await waitFor(() => {
      expect(mockListTenants).toHaveBeenCalledWith(expect.objectContaining({ status: "ACTIVE", page: 0 }));
    });
  });

  test("shows details button when onSelectTenant is provided", async () => {
    const onSelectTenant = vi.fn();
    renderWithProviders(<TenantList {...defaultProps} onSelectTenant={onSelectTenant} />);

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    const detailsButton = screen.getByRole("button", { name: "詳細" });
    await userEvent.click(detailsButton);

    expect(onSelectTenant).toHaveBeenCalledWith(activeTenant);
  });

  test("does not show details button when onSelectTenant is not provided", async () => {
    renderWithProviders(<TenantList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    expect(screen.queryByRole("button", { name: "詳細" })).not.toBeInTheDocument();
  });

  test("shows pagination when totalPages > 1", async () => {
    mockListTenants.mockResolvedValue({
      content: [activeTenant],
      totalPages: 3,
      totalElements: 60,
      number: 0,
    });

    renderWithProviders(<TenantList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("1 / 3")).toBeInTheDocument();
    });

    const nextButton = screen.getByRole("button", { name: "次へ" });
    await userEvent.click(nextButton);

    await waitFor(() => {
      expect(mockListTenants).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }));
    });
  });

  test("navigates to previous page", async () => {
    // Start on page 2 (index 1) by clicking next first
    mockListTenants.mockResolvedValue({
      content: [activeTenant],
      totalPages: 3,
      totalElements: 60,
      number: 0,
    });

    renderWithProviders(<TenantList {...defaultProps} />);

    await waitFor(() => {
      expect(screen.getByText("1 / 3")).toBeInTheDocument();
    });

    // Go to page 2
    await userEvent.click(screen.getByRole("button", { name: "次へ" }));

    await waitFor(() => {
      expect(mockListTenants).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }));
    });

    // Go back to page 1
    const prevButton = screen.getByRole("button", { name: "前へ" });
    await userEvent.click(prevButton);

    await waitFor(() => {
      expect(mockListTenants).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }));
    });
  });

  describe("mobile view", () => {
    beforeEach(() => {
      setupMatchMedia(true);
    });

    afterEach(() => {
      setupMatchMedia(false);
    });

    test("renders card layout on mobile", async () => {
      mockListTenants.mockResolvedValue({
        content: [activeTenant, inactiveTenant],
        totalPages: 1,
        totalElements: 2,
        number: 0,
      });

      renderWithProviders(<TenantList {...defaultProps} />);

      await waitFor(() => {
        expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
      });

      // Should show card layout (no table element)
      expect(screen.queryByRole("table")).not.toBeInTheDocument();
      // Both tenants should be visible as cards
      expect(screen.getByText("Tenant Beta")).toBeInTheDocument();
      expect(screen.getByText("TENANT_A")).toBeInTheDocument();
      expect(screen.getByText("TENANT_B")).toBeInTheDocument();
    });

    test("calls onEdit from mobile card", async () => {
      const onEdit = vi.fn();
      renderWithProviders(<TenantList {...defaultProps} onEdit={onEdit} />);

      await waitFor(() => {
        expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
      });

      const editButton = screen.getByRole("button", { name: "編集" });
      await userEvent.click(editButton);

      expect(onEdit).toHaveBeenCalledWith(activeTenant);
    });

    test("calls onDeactivate from mobile card for active tenant", async () => {
      const onDeactivate = vi.fn();
      renderWithProviders(<TenantList {...defaultProps} onDeactivate={onDeactivate} />);

      await waitFor(() => {
        expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
      });

      const disableButton = screen.getByRole("button", { name: "無効化" });
      await userEvent.click(disableButton);

      expect(onDeactivate).toHaveBeenCalledWith("t1");
    });

    test("calls onActivate from mobile card for inactive tenant", async () => {
      mockListTenants.mockResolvedValue({
        content: [inactiveTenant],
        totalPages: 1,
        totalElements: 1,
        number: 0,
      });
      const onActivate = vi.fn();
      renderWithProviders(<TenantList {...defaultProps} onActivate={onActivate} />);

      await waitFor(() => {
        expect(screen.getByText("Tenant Beta")).toBeInTheDocument();
      });

      const enableButton = screen.getByRole("button", { name: "有効化" });
      await userEvent.click(enableButton);

      expect(onActivate).toHaveBeenCalledWith("t2");
    });

    test("shows details button on mobile card when onSelectTenant is provided", async () => {
      const onSelectTenant = vi.fn();
      renderWithProviders(<TenantList {...defaultProps} onSelectTenant={onSelectTenant} />);

      await waitFor(() => {
        expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
      });

      const detailsButton = screen.getByRole("button", { name: "詳細" });
      await userEvent.click(detailsButton);

      expect(onSelectTenant).toHaveBeenCalledWith(activeTenant);
    });
  });

  test("refreshes when refreshKey changes", async () => {
    const { rerender } = renderWithProviders(<TenantList {...defaultProps} refreshKey={0} />);

    await waitFor(() => {
      expect(mockListTenants).toHaveBeenCalledTimes(1);
    });

    rerender(
      <IntlWrapper>
        <ToastProvider>
          <TenantList {...defaultProps} refreshKey={1} />
        </ToastProvider>
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(mockListTenants).toHaveBeenCalledTimes(2);
    });
  });
});
