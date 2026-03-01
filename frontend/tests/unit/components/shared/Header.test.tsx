import { fireEvent, render, screen } from "@testing-library/react";
import { Header } from "@/components/shared/Header";
import { IntlWrapper } from "../../../helpers/intl";

const mockLogout = vi.fn();
const mockUseAuthContext = vi.fn();

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => mockUseAuthContext(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/worklog",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => <a href={href}>{children}</a>,
}));

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      getContext: () => Promise.reject(new Error("no access")),
    },
  },
}));

vi.mock("@/providers/TenantProvider", () => ({
  useTenantContext: vi.fn().mockReturnValue({
    affiliationState: "FULLY_ASSIGNED",
    memberships: [],
    selectedTenantId: null,
    selectedTenantName: null,
    isLoading: false,
    selectTenant: vi.fn(),
    refreshStatus: vi.fn(),
  }),
}));

vi.mock("@/components/shared/NotificationBell", () => ({
  NotificationBell: () => <div data-testid="notification-bell" />,
}));

vi.mock("@/hooks/useMediaQuery", () => ({
  useMediaQuery: () => false,
}));

describe("Header", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("renders nothing when user is null", () => {
    mockUseAuthContext.mockReturnValue({ user: null, logout: mockLogout });
    const { container } = render(
      <IntlWrapper>
        <Header />
      </IntlWrapper>,
    );
    expect(container.innerHTML).toBe("");
  });

  test("shows display name and logout button when user is present", () => {
    mockUseAuthContext.mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Yamada Taro" },
      logout: mockLogout,
    });
    render(
      <IntlWrapper>
        <Header />
      </IntlWrapper>,
    );
    expect(screen.getByText("Yamada Taro")).toBeInTheDocument();
    expect(screen.getByText("ログアウト")).toBeInTheDocument();
  });

  test("calls logout when logout button is clicked", () => {
    mockUseAuthContext.mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Yamada Taro" },
      logout: mockLogout,
    });
    render(
      <IntlWrapper>
        <Header />
      </IntlWrapper>,
    );
    fireEvent.click(screen.getByText("ログアウト"));
    expect(mockLogout).toHaveBeenCalledTimes(1);
  });
});
