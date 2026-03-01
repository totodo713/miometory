import { render, screen } from "@testing-library/react";
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

import PendingOrganizationPage from "../../../app/(auth)/pending-organization/page";

const mockLogout = vi.fn();

const defaultAuthContext = {
  user: { id: "1", email: "a@b.com", displayName: "Taro" },
  isLoading: false,
  login: vi.fn(),
  logout: mockLogout,
};

const defaultTenantContext = {
  affiliationState: "AFFILIATED_NO_ORG" as const,
  memberships: [],
  selectedTenantId: null,
  selectedTenantName: null,
  isLoading: false,
  error: false,
  selectTenant: vi.fn(),
  refreshStatus: vi.fn(),
};

describe("PendingOrganizationPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("renders pending-organization title and message", () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<PendingOrganizationPage />);

    expect(screen.getByText("title")).toBeInTheDocument();
    expect(screen.getByText("message")).toBeInTheDocument();
  });

  test("renders logout button", () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<PendingOrganizationPage />);

    expect(screen.getByRole("button", { name: "logout" })).toBeInTheDocument();
  });

  test("calls logout on button click", async () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    const user = userEvent.setup();
    render(<PendingOrganizationPage />);

    await user.click(screen.getByRole("button", { name: "logout" }));
    expect(mockLogout).toHaveBeenCalled();
  });

  test("redirects to /login when user is null", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      ...defaultAuthContext,
      user: null,
    });
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<PendingOrganizationPage />);

    expect(mockReplace).toHaveBeenCalledWith("/login");
  });

  test("redirects to / when affiliationState changes from AFFILIATED_NO_ORG", () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      affiliationState: "FULLY_ASSIGNED",
    });

    render(<PendingOrganizationPage />);

    expect(mockReplace).toHaveBeenCalledWith("/");
  });

  test("shows loading spinner while auth is loading", () => {
    vi.mocked(useAuthContext).mockReturnValue({
      ...defaultAuthContext,
      isLoading: true,
    });
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<PendingOrganizationPage />);

    expect(screen.queryByText("title")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "logout" })).not.toBeInTheDocument();
  });

  test("shows loading spinner while tenant is loading", () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue({
      ...defaultTenantContext,
      isLoading: true,
    });

    render(<PendingOrganizationPage />);

    expect(screen.queryByText("title")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "logout" })).not.toBeInTheDocument();
  });

  test("renders checking status indicator", () => {
    vi.mocked(useAuthContext).mockReturnValue(defaultAuthContext);
    vi.mocked(useTenantContext).mockReturnValue(defaultTenantContext);

    render(<PendingOrganizationPage />);

    expect(screen.getByText("checking")).toBeInTheDocument();
  });
});
