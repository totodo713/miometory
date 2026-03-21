import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockListTenants = vi.fn();
const mockDeactivate = vi.fn();
const mockActivate = vi.fn();
const mockGetDefaultRules = vi.fn();
const mockUpdateDefaultRules = vi.fn();
const mockListFiscalYearRules = vi.fn();
const mockListMonthlyPeriodRules = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      tenants: {
        list: (...args: unknown[]) => mockListTenants(...args),
        deactivate: (...args: unknown[]) => mockDeactivate(...args),
        activate: (...args: unknown[]) => mockActivate(...args),
        getDefaultRules: (...args: unknown[]) => mockGetDefaultRules(...args),
        updateDefaultRules: (...args: unknown[]) => mockUpdateDefaultRules(...args),
      },
      rules: {
        listFiscalYearRules: (...args: unknown[]) => mockListFiscalYearRules(...args),
        listMonthlyPeriodRules: (...args: unknown[]) => mockListMonthlyPeriodRules(...args),
      },
    },
  },
  ApiError: class ApiError extends Error {},
}));

const mockToast = { success: vi.fn(), error: vi.fn(), warning: vi.fn() };
vi.mock("@/hooks/useToast", () => ({
  useToast: () => mockToast,
}));

vi.mock("@/providers/AdminProvider", () => ({
  useAdminContext: () => ({
    adminContext: {
      role: "SYSTEM_ADMIN",
      permissions: [],
      tenantId: "t1",
      tenantName: "Test Tenant",
      memberId: "m1",
    },
    isLoading: false,
    hasPermission: () => true,
  }),
}));

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

import AdminTenantsPage from "@/admin/tenants/page";
import { IntlWrapper } from "../../../helpers/intl";

const sampleTenant = {
  id: "t1",
  code: "TENANT_A",
  name: "Tenant Alpha",
  status: "ACTIVE" as const,
  createdAt: "2026-01-15T00:00:00Z",
};

describe("AdminTenantsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockListTenants.mockResolvedValue({
      content: [sampleTenant],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });
    mockGetDefaultRules.mockResolvedValue({
      defaultFiscalYearRuleId: null,
      defaultMonthlyPeriodRuleId: null,
    });
    mockListFiscalYearRules.mockResolvedValue([
      { id: "fy1", name: "April Start", startMonth: 4, startDay: 1, tenantId: "t1" },
    ]);
    mockListMonthlyPeriodRules.mockResolvedValue([{ id: "mp1", name: "1st Start", startDay: 1, tenantId: "t1" }]);
    mockUpdateDefaultRules.mockResolvedValue(undefined);
  });

  it("renders page title and add tenant button", async () => {
    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("テナント管理");
    expect(screen.getByRole("button", { name: "テナントを追加" })).toBeInTheDocument();
  });

  it("loads and displays tenant list", async () => {
    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });
  });

  it("shows default patterns section when tenant is selected via details", async () => {
    const user = userEvent.setup();

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    // Click details to select tenant
    const detailsButton = screen.getByRole("button", { name: "詳細" });
    await user.click(detailsButton);

    // Should load patterns
    await waitFor(() => {
      expect(mockGetDefaultRules).toHaveBeenCalledWith("t1");
      expect(mockListFiscalYearRules).toHaveBeenCalledWith("t1");
    });

    // Should show default patterns section
    await waitFor(() => {
      expect(screen.getByText("デフォルトルール")).toBeInTheDocument();
    });
  });

  it("saves default patterns for selected tenant", async () => {
    const user = userEvent.setup();

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    // Select tenant
    await user.click(screen.getByRole("button", { name: "詳細" }));

    await waitFor(() => {
      expect(screen.getByText("デフォルトルール")).toBeInTheDocument();
    });

    // Wait for patterns to load
    await waitFor(() => {
      expect(screen.getByLabelText("デフォルト会計年度ルール")).toBeInTheDocument();
    });

    // Select a fiscal year pattern
    const fySelect = screen.getByLabelText("デフォルト会計年度ルール");
    await user.selectOptions(fySelect, "fy1");

    // Click save
    const saveButton = screen.getByRole("button", { name: "保存" });
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockUpdateDefaultRules).toHaveBeenCalledWith("t1", {
        defaultFiscalYearRuleId: "fy1",
        defaultMonthlyPeriodRuleId: null,
      });
    });

    expect(mockToast.success).toHaveBeenCalledWith("デフォルトルールを保存しました");
  });

  it("shows back button and navigates back to list", async () => {
    const user = userEvent.setup();

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    // Select tenant
    await user.click(screen.getByRole("button", { name: "詳細" }));

    await waitFor(() => {
      expect(screen.getByText("デフォルトルール")).toBeInTheDocument();
    });

    // Click back
    const backButton = screen.getByRole("button", { name: "戻る" });
    await user.click(backButton);

    // Should be back to list view
    await waitFor(() => {
      expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("テナント管理");
      expect(screen.getByRole("button", { name: "テナントを追加" })).toBeInTheDocument();
    });
  });

  it("handles save error for default patterns", async () => {
    const user = userEvent.setup();
    mockUpdateDefaultRules.mockRejectedValueOnce(new Error("Save failed"));

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    // Select tenant
    await user.click(screen.getByRole("button", { name: "詳細" }));

    await waitFor(() => {
      expect(screen.getByLabelText("デフォルト会計年度ルール")).toBeInTheDocument();
    });

    // Select a pattern and save
    await user.selectOptions(screen.getByLabelText("デフォルト会計年度ルール"), "fy1");
    await user.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(mockToast.error).toHaveBeenCalledWith("デフォルトルールの保存に失敗しました");
    });
  });

  it("opens confirm dialog for deactivate and executes action", async () => {
    const user = userEvent.setup();
    mockDeactivate.mockResolvedValue(undefined);

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    // Click disable button on the tenant
    const disableButton = screen.getByRole("button", { name: "無効化" });
    await user.click(disableButton);

    // Confirm dialog should appear
    await waitFor(() => {
      expect(screen.getByRole("alertdialog")).toBeInTheDocument();
      expect(screen.getByText("このテナントを無効化しますか？")).toBeInTheDocument();
    });

    // Confirm the action - find button inside the alertdialog
    const dialog = screen.getByRole("alertdialog");
    const confirmButton = dialog.querySelector("button.bg-red-600") as HTMLButtonElement;
    await user.click(confirmButton);

    await waitFor(() => {
      expect(mockDeactivate).toHaveBeenCalledWith("t1");
    });
    expect(mockToast.success).toHaveBeenCalledWith("テナントを無効化しました");
  });

  it("opens confirm dialog for activate and executes action", async () => {
    const user = userEvent.setup();
    const inactiveTenant = { ...sampleTenant, id: "t2", status: "INACTIVE" as const, name: "Inactive Tenant" };
    mockListTenants.mockResolvedValue({
      content: [inactiveTenant],
      totalPages: 1,
      totalElements: 1,
      number: 0,
    });
    mockActivate.mockResolvedValue(undefined);

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Inactive Tenant")).toBeInTheDocument();
    });

    // Click enable button
    const enableButton = screen.getByRole("button", { name: "有効化" });
    await user.click(enableButton);

    // Confirm dialog should appear
    await waitFor(() => {
      expect(screen.getByRole("alertdialog")).toBeInTheDocument();
      expect(screen.getByText("このテナントを有効化しますか？")).toBeInTheDocument();
    });

    // Confirm the action inside the alertdialog
    const dialog = screen.getByRole("alertdialog");
    const confirmButton = dialog.querySelector("button.bg-yellow-600, button.bg-amber-600") as HTMLButtonElement;
    await user.click(confirmButton);

    await waitFor(() => {
      expect(mockActivate).toHaveBeenCalledWith("t2");
    });
    expect(mockToast.success).toHaveBeenCalledWith("テナントを有効化しました");
  });

  it("handles deactivate error with toast", async () => {
    const user = userEvent.setup();
    mockDeactivate.mockRejectedValueOnce(new Error("Server error"));

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "無効化" }));

    await waitFor(() => {
      expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    });

    const dialog = screen.getByRole("alertdialog");
    const confirmButton = dialog.querySelector("button.bg-red-600") as HTMLButtonElement;
    await user.click(confirmButton);

    await waitFor(() => {
      expect(mockToast.error).toHaveBeenCalledWith("エラーが発生しました");
    });
  });

  it("shows loading state while patterns are loading", async () => {
    const user = userEvent.setup();
    // Delay pattern loading
    mockGetDefaultRules.mockImplementation(
      () =>
        new Promise((resolve) =>
          setTimeout(
            () =>
              resolve({
                defaultFiscalYearRuleId: null,
                defaultMonthlyPeriodRuleId: null,
              }),
            100,
          ),
        ),
    );

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "詳細" }));

    // Should show loading state
    await waitFor(() => {
      expect(screen.getByText("読み込み中...")).toBeInTheDocument();
    });

    // Eventually should show patterns
    await waitFor(() => {
      expect(screen.getByText("デフォルトルール")).toBeInTheDocument();
    });
  });

  it("shows selected tenant name in heading when tenant is selected", async () => {
    const user = userEvent.setup();

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "詳細" }));

    await waitFor(() => {
      expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Tenant Alpha");
    });
  });

  it("selects monthly period pattern and saves", async () => {
    const user = userEvent.setup();

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    await user.click(screen.getByRole("button", { name: "詳細" }));

    await waitFor(() => {
      expect(screen.getByLabelText("デフォルト月次期間ルール")).toBeInTheDocument();
    });

    // Select a monthly period pattern
    const mpSelect = screen.getByLabelText("デフォルト月次期間ルール");
    await user.selectOptions(mpSelect, "mp1");

    // Click save
    await user.click(screen.getByRole("button", { name: "保存" }));

    await waitFor(() => {
      expect(mockUpdateDefaultRules).toHaveBeenCalledWith("t1", {
        defaultFiscalYearRuleId: null,
        defaultMonthlyPeriodRuleId: "mp1",
      });
    });
  });

  it("cancels confirm dialog without executing action", async () => {
    const user = userEvent.setup();

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    // Trigger deactivate confirm dialog
    await user.click(screen.getByRole("button", { name: "無効化" }));

    await waitFor(() => {
      expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    });

    // Click cancel
    const dialog = screen.getByRole("alertdialog");
    const cancelButton = dialog.querySelector("button.border-gray-300") as HTMLButtonElement;
    await user.click(cancelButton);

    await waitFor(() => {
      expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    });

    // Deactivate should NOT have been called
    expect(mockDeactivate).not.toHaveBeenCalled();
  });

  it("opens edit form when edit is clicked on tenant list item", async () => {
    const user = userEvent.setup();

    render(
      <IntlWrapper>
        <AdminTenantsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Tenant Alpha")).toBeInTheDocument();
    });

    // Click edit button on the tenant
    const editButton = screen.getByRole("button", { name: "編集" });
    await user.click(editButton);

    // TenantForm should appear (with editingTenant set)
    await waitFor(() => {
      expect(screen.getByText("テナントを編集")).toBeInTheDocument();
    });
  });
});
