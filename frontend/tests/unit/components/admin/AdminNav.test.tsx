import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlWrapper } from "../../../helpers/intl";

const mockPathname = vi.fn().mockReturnValue("/admin");

vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname(),
}));

const mockHasPermission = vi.fn().mockReturnValue(true);
const mockAdminContext = {
  role: "TENANT_ADMIN",
  permissions: [],
  tenantId: "t1",
  tenantName: "Test Tenant",
  memberId: "m1",
};

vi.mock("@/providers/AdminProvider", () => ({
  useAdminContext: () => ({
    adminContext: mockAdminContext,
    isLoading: false,
    hasPermission: (p: string) => mockHasPermission(p),
  }),
}));

function setupMatchMedia(mode: "desktop" | "tablet" | "mobile") {
  (window.matchMedia as ReturnType<typeof vi.fn>).mockImplementation((query: string) => {
    let matches = false;
    if (mode === "mobile" && query === "(max-width: 767px)") matches = true;
    if (mode === "tablet" && query === "(min-width: 768px) and (max-width: 1023px)") matches = true;
    return {
      matches,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    };
  });
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

import { AdminNav } from "@/components/admin/AdminNav";

describe("AdminNav", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPathname.mockReturnValue("/admin");
    mockHasPermission.mockReturnValue(true);
  });

  test("renders all nav items when user has all permissions", () => {
    render(
      <IntlWrapper>
        <AdminNav />
      </IntlWrapper>,
    );

    expect(screen.getByText("ダッシュボード")).toBeInTheDocument();
    expect(screen.getByText("テナント管理")).toBeInTheDocument();
    expect(screen.getByText("ユーザー管理")).toBeInTheDocument();
    expect(screen.getByText("メンバー管理")).toBeInTheDocument();
    expect(screen.getByText("プロジェクト管理")).toBeInTheDocument();
    expect(screen.getByText("アサイン管理")).toBeInTheDocument();
    expect(screen.getByText("組織管理")).toBeInTheDocument();
    expect(screen.getByText("設定")).toBeInTheDocument();
  });

  test("hides nav items when user lacks permission", () => {
    mockHasPermission.mockImplementation((p: string) => {
      if (p === "system_settings.view") return false;
      if (p === "tenant_settings.view") return false;
      if (p === "tenant.view") return false;
      return true;
    });

    render(
      <IntlWrapper>
        <AdminNav />
      </IntlWrapper>,
    );

    expect(screen.getByText("ダッシュボード")).toBeInTheDocument();
    expect(screen.queryByText("テナント管理")).not.toBeInTheDocument();
    expect(screen.queryByText("設定")).not.toBeInTheDocument();
    // Other items should still be visible
    expect(screen.getByText("組織管理")).toBeInTheDocument();
  });

  test("highlights active nav item based on pathname", () => {
    mockPathname.mockReturnValue("/admin/organizations");

    render(
      <IntlWrapper>
        <AdminNav />
      </IntlWrapper>,
    );

    const orgLink = screen.getByText("組織管理").closest("a");
    expect(orgLink).toHaveClass("bg-blue-50");

    const dashLink = screen.getByText("ダッシュボード").closest("a");
    expect(dashLink).not.toHaveClass("bg-blue-50");
  });

  test("displays admin context info (role and tenant name)", () => {
    render(
      <IntlWrapper>
        <AdminNav />
      </IntlWrapper>,
    );

    expect(screen.getByText("TENANT ADMIN")).toBeInTheDocument();
    expect(screen.getByText("Test Tenant")).toBeInTheDocument();
  });

  test("settings link points to /admin/settings", () => {
    render(
      <IntlWrapper>
        <AdminNav />
      </IntlWrapper>,
    );

    const settingsLink = screen.getByText("設定").closest("a");
    expect(settingsLink).toHaveAttribute("href", "/admin/settings");
  });

  describe("mobile view", () => {
    beforeEach(() => {
      setupMatchMedia("mobile");
    });

    test("renders hamburger button on mobile", () => {
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      const hamburgerButton = screen.getByRole("button", { name: "管理" });
      expect(hamburgerButton).toBeInTheDocument();
      // Nav items should not be visible until drawer opens
      expect(screen.queryByText("ダッシュボード")).not.toBeInTheDocument();
    });

    test("opens drawer when hamburger is clicked", async () => {
      const user = userEvent.setup();
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      const hamburgerButton = screen.getByRole("button", { name: "管理" });
      await user.click(hamburgerButton);

      // Nav items should now be visible
      expect(screen.getByText("ダッシュボード")).toBeInTheDocument();
      expect(screen.getByText("設定")).toBeInTheDocument();
      // Dialog should be present
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    test("closes drawer when backdrop is clicked", async () => {
      const user = userEvent.setup();
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      await user.click(screen.getByRole("button", { name: "管理" }));
      expect(screen.getByRole("dialog")).toBeInTheDocument();

      // Click the close button (within the drawer)
      const closeButtons = screen.getAllByRole("button", { name: "管理" });
      // The close button is the one inside the drawer
      const closeButton = closeButtons.find((btn) => btn.closest("[role='dialog']"));
      if (closeButton) {
        await user.click(closeButton);
      }

      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });

    test("closes drawer on Escape key", async () => {
      const user = userEvent.setup();
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      await user.click(screen.getByRole("button", { name: "管理" }));
      expect(screen.getByRole("dialog")).toBeInTheDocument();

      fireEvent.keyDown(document, { key: "Escape" });

      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });

    test("shows admin context in mobile drawer", async () => {
      const user = userEvent.setup();
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      await user.click(screen.getByRole("button", { name: "管理" }));
      expect(screen.getByText("TENANT ADMIN")).toBeInTheDocument();
      expect(screen.getByText("Test Tenant")).toBeInTheDocument();
    });

    test("closes drawer when nav link is clicked", async () => {
      const user = userEvent.setup();
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      // Open drawer
      await user.click(screen.getByRole("button", { name: "管理" }));
      expect(screen.getByRole("dialog")).toBeInTheDocument();

      // Click a nav link
      const settingsLink = screen.getByText("設定");
      await user.click(settingsLink);

      // Drawer should close
      await waitFor(() => {
        expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      });
    });

    test("traps focus within drawer with Tab key", async () => {
      const user = userEvent.setup();
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      // Open drawer
      await user.click(screen.getByRole("button", { name: "管理" }));
      const dialog = screen.getByRole("dialog");
      expect(dialog).toBeInTheDocument();

      // Get all focusable elements in the drawer
      const focusable = dialog.querySelectorAll<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      );
      expect(focusable.length).toBeGreaterThan(0);

      // Focus the last focusable element
      const lastEl = focusable[focusable.length - 1];
      lastEl.focus();
      expect(document.activeElement).toBe(lastEl);

      // Press Tab - should wrap to first element
      fireEvent.keyDown(document, { key: "Tab" });

      // Focus should have moved to first element
      expect(document.activeElement).toBe(focusable[0]);
    });

    test("traps focus backward with Shift+Tab key", async () => {
      const user = userEvent.setup();
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      // Open drawer
      await user.click(screen.getByRole("button", { name: "管理" }));
      const dialog = screen.getByRole("dialog");

      const focusable = dialog.querySelectorAll<HTMLElement>(
        'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
      );

      // Focus the first focusable element
      const firstEl = focusable[0];
      firstEl.focus();
      expect(document.activeElement).toBe(firstEl);

      // Press Shift+Tab - should wrap to last element
      fireEvent.keyDown(document, { key: "Tab", shiftKey: true });

      expect(document.activeElement).toBe(focusable[focusable.length - 1]);
    });
  });

  describe("tablet view", () => {
    beforeEach(() => {
      setupMatchMedia("tablet");
    });

    test("renders collapsed sidebar on tablet", () => {
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      // Should show short labels (single letters) instead of full text
      expect(screen.getByText("D")).toBeInTheDocument();
      expect(screen.getByText("S")).toBeInTheDocument();
      // Full text should not be visible
      expect(screen.queryByText("ダッシュボード")).not.toBeInTheDocument();
    });

    test("expands sidebar on hover to show full labels", () => {
      render(
        <IntlWrapper>
          <AdminNav />
        </IntlWrapper>,
      );

      const nav = screen.getByRole("navigation");
      expect(nav).toHaveClass("w-16");

      fireEvent.mouseEnter(nav);
      expect(nav).toHaveClass("w-64");
      expect(screen.getByText("ダッシュボード")).toBeInTheDocument();

      fireEvent.mouseLeave(nav);
      expect(nav).toHaveClass("w-16");
    });
  });
});
