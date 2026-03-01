import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      patterns: {
        listFiscalYearPatterns: vi.fn(),
        listMonthlyPeriodPatterns: vi.fn(),
        createFiscalYearPattern: vi.fn(),
        createMonthlyPeriodPattern: vi.fn(),
      },
      tenantSettings: {
        getDefaultPatterns: vi.fn(),
        updateDefaultPatterns: vi.fn(),
      },
    },
  },
  ApiError: class ApiError extends Error {},
}));

const mockToast = { success: vi.fn(), error: vi.fn() };
vi.mock("@/hooks/useToast", () => ({
  useToast: () => mockToast,
}));

vi.mock("@/providers/AdminProvider", () => ({
  useAdminContext: () => ({
    adminContext: {
      role: "TENANT_ADMIN",
      permissions: [],
      tenantId: "t1",
      tenantName: "Test Tenant",
      memberId: "m1",
    },
    isLoading: false,
    hasPermission: () => false,
  }),
}));

import { TenantSettingsSection } from "@/components/admin/TenantSettingsSection";
import { api } from "@/services/api";
import { IntlWrapper } from "../../../helpers/intl";

const fyPatterns = [
  { id: "fy1", tenantId: "t1", name: "4月開始", startMonth: 4, startDay: 1 },
  { id: "fy2", tenantId: "t1", name: "1月開始", startMonth: 1, startDay: 1 },
];

const mpPatterns = [
  { id: "mp1", tenantId: "t1", name: "1日開始", startDay: 1 },
  { id: "mp2", tenantId: "t1", name: "15日開始", startDay: 15 },
];

describe("TenantSettingsSection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.admin.patterns.listFiscalYearPatterns as ReturnType<typeof vi.fn>).mockResolvedValue(fyPatterns);
    (api.admin.patterns.listMonthlyPeriodPatterns as ReturnType<typeof vi.fn>).mockResolvedValue(mpPatterns);
    (api.admin.tenantSettings.getDefaultPatterns as ReturnType<typeof vi.fn>).mockResolvedValue({
      defaultFiscalYearPatternId: "fy1",
      defaultMonthlyPeriodPatternId: null,
    });
  });

  it("should show loading state then render content", async () => {
    render(
      <IntlWrapper>
        <TenantSettingsSection />
      </IntlWrapper>,
    );

    expect(screen.getByText("読み込み中...")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("テナント設定")).toBeInTheDocument();
    });
  });

  it("should display patterns after loading", async () => {
    render(
      <IntlWrapper>
        <TenantSettingsSection />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("テナント設定")).toBeInTheDocument();
    });

    // Patterns appear in both <option> and <li> elements
    expect(screen.getAllByText("4月開始 (4/1)")).toHaveLength(2);
    expect(screen.getAllByText("1月開始 (1/1)")).toHaveLength(2);
    expect(screen.getAllByText("1日開始 (1)")).toHaveLength(2);
    expect(screen.getAllByText("15日開始 (15)")).toHaveLength(2);
  });

  it("should set default pattern from loaded data", async () => {
    render(
      <IntlWrapper>
        <TenantSettingsSection />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("テナント設定")).toBeInTheDocument();
    });

    const fySelect = screen.getByLabelText("デフォルト会計年度パターン") as HTMLSelectElement;
    expect(fySelect.value).toBe("fy1");

    const mpSelect = screen.getByLabelText("デフォルト月次期間パターン") as HTMLSelectElement;
    expect(mpSelect.value).toBe("");
  });

  it("should save default patterns successfully", async () => {
    const user = userEvent.setup();
    (api.admin.tenantSettings.updateDefaultPatterns as ReturnType<typeof vi.fn>).mockResolvedValue(undefined);

    render(
      <IntlWrapper>
        <TenantSettingsSection />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("テナント設定")).toBeInTheDocument();
    });

    const mpSelect = screen.getByLabelText("デフォルト月次期間パターン");
    await user.selectOptions(mpSelect, "mp1");

    const saveButton = screen.getByRole("button", { name: "保存" });
    await user.click(saveButton);

    await waitFor(() => {
      expect(api.admin.tenantSettings.updateDefaultPatterns).toHaveBeenCalledWith({
        defaultFiscalYearPatternId: "fy1",
        defaultMonthlyPeriodPatternId: "mp1",
      });
      expect(mockToast.success).toHaveBeenCalledWith("デフォルトパターンを保存しました");
    });
  });

  it("should show error toast when save fails", async () => {
    const user = userEvent.setup();
    (api.admin.tenantSettings.updateDefaultPatterns as ReturnType<typeof vi.fn>).mockRejectedValueOnce(
      new Error("Save failed"),
    );

    render(
      <IntlWrapper>
        <TenantSettingsSection />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("テナント設定")).toBeInTheDocument();
    });

    const saveButton = screen.getByRole("button", { name: "保存" });
    await user.click(saveButton);

    await waitFor(() => {
      expect(mockToast.error).toHaveBeenCalledWith("デフォルトパターンの保存に失敗しました");
    });
  });

  it("should disable save button while saving", async () => {
    const user = userEvent.setup();
    let resolveUpdate: () => void = () => {};
    (api.admin.tenantSettings.updateDefaultPatterns as ReturnType<typeof vi.fn>).mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveUpdate = resolve;
        }),
    );

    render(
      <IntlWrapper>
        <TenantSettingsSection />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("テナント設定")).toBeInTheDocument();
    });

    const saveButton = screen.getByRole("button", { name: "保存" });
    await user.click(saveButton);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "保存中..." })).toBeDisabled();
    });

    resolveUpdate();

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "保存" })).not.toBeDisabled();
    });
  });

  it("should show empty message when no patterns exist", async () => {
    (api.admin.patterns.listFiscalYearPatterns as ReturnType<typeof vi.fn>).mockResolvedValue([]);
    (api.admin.patterns.listMonthlyPeriodPatterns as ReturnType<typeof vi.fn>).mockResolvedValue([]);

    render(
      <IntlWrapper>
        <TenantSettingsSection />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("会計年度パターンがありません")).toBeInTheDocument();
      expect(screen.getByText("月次期間パターンがありません")).toBeInTheDocument();
    });
  });

  it("should open fiscal year pattern form on create button click", async () => {
    const user = userEvent.setup();

    render(
      <IntlWrapper>
        <TenantSettingsSection />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("テナント設定")).toBeInTheDocument();
    });

    const createButtons = screen.getAllByRole("button", { name: "新規作成" });
    await user.click(createButtons[0]);

    await waitFor(() => {
      expect(screen.getByLabelText(/名前/)).toBeInTheDocument();
    });
  });
});
