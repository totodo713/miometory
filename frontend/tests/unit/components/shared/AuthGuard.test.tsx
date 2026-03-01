import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import { AuthGuard } from "@/components/shared/AuthGuard";
import { useAuthContext } from "@/providers/AuthProvider";
import { useTenantContext } from "@/providers/TenantProvider";

const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn() }),
}));

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: vi.fn(),
}));

vi.mock("@/providers/TenantProvider", () => ({
  useTenantContext: vi.fn(),
}));

const defaultTenantContext = {
  affiliationState: null,
  memberships: [],
  selectedTenantId: null,
  selectedTenantName: null,
  isLoading: false,
  error: false,
  selectTenant: vi.fn(),
  refreshStatus: vi.fn(),
};

describe("AuthGuard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("shows LoadingSpinner while auth is loading", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      user: null,
      isLoading: true,
      login: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      isLoading: false,
    });

    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );

    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  test("shows LoadingSpinner while tenant is loading", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Taro" },
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      isLoading: true,
    });

    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );

    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  test("redirects to /login when user is null", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      user: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      isLoading: false,
    });

    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );

    expect(mockReplace).toHaveBeenCalledWith("/login");
    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
  });

  test("redirects to /waiting when affiliationState is UNAFFILIATED", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Taro" },
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      affiliationState: "UNAFFILIATED",
      isLoading: false,
    });

    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );

    expect(mockReplace).toHaveBeenCalledWith("/waiting");
    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
  });

  test("redirects to /pending-organization when affiliationState is AFFILIATED_NO_ORG", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Taro" },
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      affiliationState: "AFFILIATED_NO_ORG",
      isLoading: false,
    });

    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );

    expect(mockReplace).toHaveBeenCalledWith("/pending-organization");
    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
  });

  test("redirects to /select-tenant when FULLY_ASSIGNED with multiple memberships and no selectedTenantId", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Taro" },
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      affiliationState: "FULLY_ASSIGNED",
      memberships: [
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
          organizationId: "o2",
          organizationName: "Org B",
        },
      ],
      selectedTenantId: null,
      isLoading: false,
    });

    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );

    expect(mockReplace).toHaveBeenCalledWith("/select-tenant");
    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
  });

  test("renders children when FULLY_ASSIGNED with selectedTenantId", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Taro" },
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      affiliationState: "FULLY_ASSIGNED",
      memberships: [
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
          organizationId: "o2",
          organizationName: "Org B",
        },
      ],
      selectedTenantId: "t1",
      isLoading: false,
    });

    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );

    expect(screen.getByText("Protected")).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  test("renders children when FULLY_ASSIGNED with single membership (auto-selected)", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Taro" },
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      affiliationState: "FULLY_ASSIGNED",
      memberships: [
        {
          memberId: "m1",
          tenantId: "t1",
          tenantName: "Tenant A",
          organizationId: "o1",
          organizationName: "Org A",
        },
      ],
      selectedTenantId: null,
      isLoading: false,
    });

    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );

    expect(screen.getByText("Protected")).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
