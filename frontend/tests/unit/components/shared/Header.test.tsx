import { fireEvent, render, screen } from "@testing-library/react";
import { Header } from "@/components/shared/Header";

const mockLogout = vi.fn();
const mockUseAuthContext = vi.fn();

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => mockUseAuthContext(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/worklog",
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

vi.mock("@/components/shared/NotificationBell", () => ({
  NotificationBell: () => <div data-testid="notification-bell" />,
}));

describe("Header", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("renders nothing when user is null", () => {
    mockUseAuthContext.mockReturnValue({ user: null, logout: mockLogout });
    const { container } = render(<Header />);
    expect(container.innerHTML).toBe("");
  });

  test("shows display name and logout button when user is present", () => {
    mockUseAuthContext.mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Yamada Taro" },
      logout: mockLogout,
    });
    render(<Header />);
    expect(screen.getByText("Yamada Taro")).toBeInTheDocument();
    expect(screen.getByText("ログアウト")).toBeInTheDocument();
  });

  test("calls logout when logout button is clicked", () => {
    mockUseAuthContext.mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Yamada Taro" },
      logout: mockLogout,
    });
    render(<Header />);
    fireEvent.click(screen.getByText("ログアウト"));
    expect(mockLogout).toHaveBeenCalledTimes(1);
  });
});
