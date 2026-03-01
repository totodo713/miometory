/**
 * Unit tests for TenantSwitcher component
 *
 * Tests visibility, dropdown behavior, tenant selection, and keyboard/click-outside handling.
 */

import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

// --- Mocks ---

const mockSelectTenant = vi.fn<(tenantId: string) => Promise<void>>().mockResolvedValue(undefined);

let mockMemberships: Array<{
  memberId: string;
  tenantId: string;
  tenantName: string;
  organizationId: string | null;
  organizationName: string | null;
}> = [];
let mockSelectedTenantId: string | null = null;
let mockSelectedTenantName: string | null = null;

vi.mock("@/providers/TenantProvider", () => ({
  useTenantContext: () => ({
    memberships: mockMemberships,
    selectedTenantId: mockSelectedTenantId,
    selectedTenantName: mockSelectedTenantName,
    selectTenant: mockSelectTenant,
  }),
}));

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => {
    const messages: Record<string, string> = {
      switchTenant: "Switch Tenant",
      currentTenant: "Current Tenant",
    };
    return messages[key] ?? key;
  },
}));

// Import after mocks
import { TenantSwitcher } from "@/components/shared/TenantSwitcher";

// --- Helpers ---

const reloadMock = vi.fn();

function setupLocationMock() {
  Object.defineProperty(window, "location", {
    value: { reload: reloadMock },
    writable: true,
  });
}

function makeMemberships(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    memberId: `member-${i + 1}`,
    tenantId: `tenant-${i + 1}`,
    tenantName: `Tenant ${i + 1}`,
    organizationId: null,
    organizationName: null,
  }));
}

// --- Tests ---

describe("TenantSwitcher", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupLocationMock();
    mockMemberships = [];
    mockSelectedTenantId = null;
    mockSelectedTenantName = null;
  });

  it("returns null when user has 0 memberships", () => {
    mockMemberships = [];
    const { container } = render(<TenantSwitcher />);
    expect(container.innerHTML).toBe("");
  });

  it("returns null when user has 1 membership", () => {
    mockMemberships = makeMemberships(1);
    mockSelectedTenantId = "tenant-1";
    mockSelectedTenantName = "Tenant 1";
    const { container } = render(<TenantSwitcher />);
    expect(container.innerHTML).toBe("");
  });

  it("shows current tenant name when user has multiple memberships", () => {
    mockMemberships = makeMemberships(3);
    mockSelectedTenantId = "tenant-2";
    mockSelectedTenantName = "Tenant 2";
    render(<TenantSwitcher />);
    expect(screen.getByText("Tenant 2")).toBeInTheDocument();
  });

  it("opens dropdown on button click", async () => {
    const user = userEvent.setup();
    mockMemberships = makeMemberships(3);
    mockSelectedTenantId = "tenant-1";
    mockSelectedTenantName = "Tenant 1";
    render(<TenantSwitcher />);

    const button = screen.getByRole("button", { name: "Switch Tenant" });
    await user.click(button);

    expect(screen.getByRole("menu")).toBeInTheDocument();
  });

  it("shows all tenant names in dropdown", async () => {
    const user = userEvent.setup();
    mockMemberships = makeMemberships(3);
    mockSelectedTenantId = "tenant-1";
    mockSelectedTenantName = "Tenant 1";
    render(<TenantSwitcher />);

    await user.click(screen.getByRole("button", { name: "Switch Tenant" }));

    const menuItems = screen.getAllByRole("menuitem");
    expect(menuItems).toHaveLength(3);
    expect(screen.getByRole("menuitem", { name: "Tenant 1" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Tenant 2" })).toBeInTheDocument();
    expect(screen.getByRole("menuitem", { name: "Tenant 3" })).toBeInTheDocument();
  });

  it("highlights current tenant in dropdown", async () => {
    const user = userEvent.setup();
    mockMemberships = makeMemberships(3);
    mockSelectedTenantId = "tenant-2";
    mockSelectedTenantName = "Tenant 2";
    render(<TenantSwitcher />);

    await user.click(screen.getByRole("button", { name: "Switch Tenant" }));

    const currentItem = screen.getByRole("menuitem", { name: "Tenant 2" });
    expect(currentItem).toHaveClass("bg-blue-50", "font-medium", "text-blue-700");

    const otherItem = screen.getByRole("menuitem", { name: "Tenant 1" });
    expect(otherItem).toHaveClass("text-gray-700");
    expect(otherItem).not.toHaveClass("bg-blue-50");
  });

  it("calls selectTenant and reloads on selection of different tenant", async () => {
    const user = userEvent.setup();
    mockMemberships = makeMemberships(3);
    mockSelectedTenantId = "tenant-1";
    mockSelectedTenantName = "Tenant 1";
    render(<TenantSwitcher />);

    await user.click(screen.getByRole("button", { name: "Switch Tenant" }));
    await user.click(screen.getByRole("menuitem", { name: "Tenant 2" }));

    expect(mockSelectTenant).toHaveBeenCalledWith("tenant-2");
    await waitFor(() => {
      expect(reloadMock).toHaveBeenCalled();
    });
  });

  it("does not call selectTenant when selecting current tenant", async () => {
    const user = userEvent.setup();
    mockMemberships = makeMemberships(3);
    mockSelectedTenantId = "tenant-1";
    mockSelectedTenantName = "Tenant 1";
    render(<TenantSwitcher />);

    await user.click(screen.getByRole("button", { name: "Switch Tenant" }));
    await user.click(screen.getByRole("menuitem", { name: "Tenant 1" }));

    expect(mockSelectTenant).not.toHaveBeenCalled();
    expect(reloadMock).not.toHaveBeenCalled();
  });

  it("closes dropdown on outside click", async () => {
    const user = userEvent.setup();
    mockMemberships = makeMemberships(3);
    mockSelectedTenantId = "tenant-1";
    mockSelectedTenantName = "Tenant 1";
    render(<TenantSwitcher />);

    await user.click(screen.getByRole("button", { name: "Switch Tenant" }));
    expect(screen.getByRole("menu")).toBeInTheDocument();

    // Click outside the dropdown
    await user.click(document.body);

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("closes dropdown on Escape key", async () => {
    const user = userEvent.setup();
    mockMemberships = makeMemberships(3);
    mockSelectedTenantId = "tenant-1";
    mockSelectedTenantName = "Tenant 1";
    render(<TenantSwitcher />);

    await user.click(screen.getByRole("button", { name: "Switch Tenant" }));
    expect(screen.getByRole("menu")).toBeInTheDocument();

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });
});
