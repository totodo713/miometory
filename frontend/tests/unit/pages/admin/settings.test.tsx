import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      system: {
        getRules: vi.fn(),
        updateRules: vi.fn(),
      },
      rules: {
        listFiscalYearRules: vi.fn(),
        listMonthlyPeriodRules: vi.fn(),
      },
      tenantSettings: {
        getDefaultRules: vi.fn(),
        updateDefaultRules: vi.fn(),
      },
    },
  },
  ApiError: class ApiError extends Error {},
}));

const mockToast = { success: vi.fn(), error: vi.fn() };
vi.mock("@/hooks/useToast", () => ({
  useToast: () => mockToast,
}));

let mockHasPermission = vi.fn().mockReturnValue(true);
let mockIsLoading = false;
vi.mock("@/providers/AdminProvider", () => ({
  useAdminContext: () => ({
    adminContext: {
      role: "SYSTEM_ADMIN",
      permissions: [],
      tenantId: "t1",
      tenantName: "Test Tenant",
      memberId: "m1",
    },
    isLoading: mockIsLoading,
    hasPermission: (p: string) => mockHasPermission(p),
  }),
}));

import SettingsPage from "@/admin/settings/page";
import { api } from "@/services/api";
import { IntlWrapper } from "../../../helpers/intl";

describe("SettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsLoading = false;
    mockHasPermission = vi.fn().mockReturnValue(true);
    (api.admin.system.getRules as ReturnType<typeof vi.fn>).mockResolvedValue({
      fiscalYearStartMonth: 4,
      fiscalYearStartDay: 1,
      monthlyPeriodStartDay: 1,
    });
  });

  describe("role-based routing", () => {
    it("should show System Settings for system admin", async () => {
      mockHasPermission = vi.fn((p: string) => p === "system_settings.view");

      render(
        <IntlWrapper>
          <SettingsPage />
        </IntlWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByText("システム設定")).toBeInTheDocument();
      });
    });

    it("should show Tenant Settings for tenant admin", async () => {
      mockHasPermission = vi.fn().mockReturnValue(false);
      (api.admin.rules.listFiscalYearRules as ReturnType<typeof vi.fn>).mockResolvedValue([]);
      (api.admin.rules.listMonthlyPeriodRules as ReturnType<typeof vi.fn>).mockResolvedValue([]);
      (api.admin.tenantSettings.getDefaultRules as ReturnType<typeof vi.fn>).mockResolvedValue({
        defaultFiscalYearRuleId: null,
        defaultMonthlyPeriodRuleId: null,
      });

      render(
        <IntlWrapper>
          <SettingsPage />
        </IntlWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByText("テナント設定")).toBeInTheDocument();
      });
    });

    it("should show loading state when isLoading is true", () => {
      mockIsLoading = true;

      render(
        <IntlWrapper>
          <SettingsPage />
        </IntlWrapper>,
      );

      expect(screen.getByText("読み込み中...")).toBeInTheDocument();
    });
  });

  describe("System settings (system admin view)", () => {
    beforeEach(() => {
      mockHasPermission = vi.fn((p: string) => p === "system_settings.view");
    });

    it("should load and display current settings", async () => {
      render(
        <IntlWrapper>
          <SettingsPage />
        </IntlWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByLabelText("開始月")).toBeInTheDocument();
      });

      const monthSelect = screen.getByLabelText("開始月") as HTMLSelectElement;
      expect(monthSelect.value).toBe("4");
    });

    it("should save updated settings", async () => {
      const user = userEvent.setup();
      (api.admin.system.updateRules as ReturnType<typeof vi.fn>).mockResolvedValue(undefined);

      render(
        <IntlWrapper>
          <SettingsPage />
        </IntlWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByLabelText("開始月")).toBeInTheDocument();
      });

      const monthSelect = screen.getByLabelText("開始月");
      await user.selectOptions(monthSelect, "10");

      const saveButton = screen.getByRole("button", { name: "保存" });
      await user.click(saveButton);

      await waitFor(() => {
        expect(api.admin.system.updateRules).toHaveBeenCalledWith({
          fiscalYearStartMonth: 10,
          fiscalYearStartDay: 1,
          monthlyPeriodStartDay: 1,
        });
      });
    });

    it("should show error toast when loading fails", async () => {
      (api.admin.system.getRules as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error("Network error"));

      render(
        <IntlWrapper>
          <SettingsPage />
        </IntlWrapper>,
      );

      await waitFor(() => {
        expect(mockToast.error).toHaveBeenCalledWith("設定の読み込みに失敗しました");
      });
    });

    it("should show error toast when save fails", async () => {
      const user = userEvent.setup();
      (api.admin.system.updateRules as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error("Save failed"));

      render(
        <IntlWrapper>
          <SettingsPage />
        </IntlWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByLabelText("開始月")).toBeInTheDocument();
      });

      const saveButton = screen.getByRole("button", { name: "保存" });
      await user.click(saveButton);

      await waitFor(() => {
        expect(mockToast.error).toHaveBeenCalledWith("設定の保存に失敗しました");
      });
    });

    it("should show success toast when save succeeds", async () => {
      const user = userEvent.setup();
      (api.admin.system.updateRules as ReturnType<typeof vi.fn>).mockResolvedValue(undefined);

      render(
        <IntlWrapper>
          <SettingsPage />
        </IntlWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByLabelText("開始月")).toBeInTheDocument();
      });

      const saveButton = screen.getByRole("button", { name: "保存" });
      await user.click(saveButton);

      await waitFor(() => {
        expect(mockToast.success).toHaveBeenCalledWith("設定を保存しました");
      });
    });

    it("should disable save button while saving", async () => {
      const user = userEvent.setup();
      let resolveUpdate: () => void = () => {};
      (api.admin.system.updateRules as ReturnType<typeof vi.fn>).mockImplementation(
        () =>
          new Promise<void>((resolve) => {
            resolveUpdate = resolve;
          }),
      );

      render(
        <IntlWrapper>
          <SettingsPage />
        </IntlWrapper>,
      );

      await waitFor(() => {
        expect(screen.getByLabelText("開始月")).toBeInTheDocument();
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
  });
});
