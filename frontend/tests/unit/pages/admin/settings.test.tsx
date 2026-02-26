import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      system: {
        getPatterns: vi.fn(),
        updatePatterns: vi.fn(),
      },
    },
  },
  ApiError: class ApiError extends Error {},
}));

vi.mock("@/hooks/useToast", () => ({
  useToast: () => ({
    success: vi.fn(),
    error: vi.fn(),
  }),
}));

import { IntlWrapper } from "../../../helpers/intl";
import { api } from "@/services/api";
import SystemSettingsPage from "@/admin/settings/page";

describe("SystemSettingsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.admin.system.getPatterns as ReturnType<typeof vi.fn>).mockResolvedValue({
      fiscalYearStartMonth: 4,
      fiscalYearStartDay: 1,
      monthlyPeriodStartDay: 1,
    });
  });

  it("should render loading state initially", () => {
    render(
      <IntlWrapper>
        <SystemSettingsPage />
      </IntlWrapper>,
    );
    expect(screen.getByText("読み込み中...")).toBeInTheDocument();
  });

  it("should load and display current settings", async () => {
    render(
      <IntlWrapper>
        <SystemSettingsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(api.admin.system.getPatterns).toHaveBeenCalled();
    });

    // Should display the form with loaded values
    const monthSelect = screen.getByLabelText("開始月") as HTMLSelectElement;
    expect(monthSelect.value).toBe("4");
  });

  it("should save updated settings", async () => {
    const user = userEvent.setup();
    (api.admin.system.updatePatterns as ReturnType<typeof vi.fn>).mockResolvedValue(undefined);

    render(
      <IntlWrapper>
        <SystemSettingsPage />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByLabelText("開始月")).toBeInTheDocument();
    });

    // Change month to October
    const monthSelect = screen.getByLabelText("開始月");
    await user.selectOptions(monthSelect, "10");

    // Click save
    const saveButton = screen.getByRole("button", { name: "保存" });
    await user.click(saveButton);

    await waitFor(() => {
      expect(api.admin.system.updatePatterns).toHaveBeenCalledWith({
        fiscalYearStartMonth: 10,
        fiscalYearStartDay: 1,
        monthlyPeriodStartDay: 1,
      });
    });
  });
});
