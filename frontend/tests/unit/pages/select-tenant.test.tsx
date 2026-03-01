import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";

const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: vi.fn(),
}));

vi.mock("@/providers/TenantProvider", () => ({
  useTenantContext: vi.fn(),
}));

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

import SelectTenantPage from "../../../app/(auth)/select-tenant/page";

const mockSelectTenant = vi.fn();

const mockMemberships = [
  {
    memberId: "m1",
    tenantId: "t1",
    tenantName: "Tenant A",
    organizationId: "o1",
    organizationName: "Org A",
  },
  {
    memberId: "m2",
    tenantId: "t2",
    tenantName: "Tenant B",
    organizationId: null,
    organizationName: null,
  },
];

const defaultAuthContext = {
  user: { id: "1", email: "a@b.com", displayName: "Taro" },
  isLoading: false,
  login: vi.fn(),
  logout: vi.fn(),
};

const defaultTenantContext = {
  affiliationState: "FULLY_ASSIGNED" as const,
  memberships: mockMemberships,
  selectedTenantId: null,
  selectedTenantName: null,
  isLoading: false,
  selectTenant: mockSelectTenant,
  refreshStatus: vi.fn(),
};

describe("SelectTenantPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSelectTenant.mockResolvedValue(undefined);
  });

  test("renders title and message", () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<SelectTenantPage />);

    expect(screen.getByText("title")).toBeInTheDocument();
    expect(screen.getByText("message")).toBeInTheDocument();
  });

  test("renders list of tenants", () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<SelectTenantPage />);

    expect(screen.getByText("Tenant A")).toBeInTheDocument();
    expect(screen.getByText("Tenant B")).toBeInTheDocument();
  });

  test("shows organization name for memberships with organizationName", () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<SelectTenantPage />);

    expect(screen.getByText("organization: Org A")).toBeInTheDocument();
  });

  test("shows noOrganization for null organizationName", () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<SelectTenantPage />);

    expect(screen.getByText("noOrganization")).toBeInTheDocument();
  });

  test("calls selectTenant and redirects on click", async () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    const user = userEvent.setup();
    render(<SelectTenantPage />);

    await user.click(screen.getByText("Tenant A"));

    expect(mockSelectTenant).toHaveBeenCalledWith("t1");
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith("/worklog");
    });
  });

  test("disables buttons while selecting", async () => {
    // Make selectTenant never resolve so we can check disabled state
    mockSelectTenant.mockReturnValue(new Promise(() => {}));
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    const user = userEvent.setup();
    render(<SelectTenantPage />);

    await user.click(screen.getByText("Tenant A"));

    const buttons = screen.getAllByRole("button");
    for (const button of buttons) {
      expect(button).toBeDisabled();
    }
  });

  test("shows spinner for selected tenant", async () => {
    // Make selectTenant never resolve so spinner stays visible
    mockSelectTenant.mockReturnValue(new Promise(() => {}));
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    const user = userEvent.setup();
    render(<SelectTenantPage />);

    await user.click(screen.getByText("Tenant A"));

    // The spinner renders an <output> element with aria-live="polite"
    const tenantAButton = screen.getByText("Tenant A").closest("button");
    expect(tenantAButton?.querySelector("output")).toBeInTheDocument();

    // Tenant B should NOT have a spinner
    const tenantBButton = screen.getByText("Tenant B").closest("button");
    expect(tenantBButton?.querySelector("output")).not.toBeInTheDocument();
  });

  test("redirects to /login when not authenticated", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      ...defaultAuthContext,
      user: null,
    });
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<SelectTenantPage />);

    expect(mockReplace).toHaveBeenCalledWith("/login");
  });

  test("shows loading spinner while loading", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      ...defaultAuthContext,
      isLoading: true,
    });
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<SelectTenantPage />);

    expect(screen.queryByText("title")).not.toBeInTheDocument();
    expect(screen.queryByText("Tenant A")).not.toBeInTheDocument();
  });
});
