import { fireEvent, render, screen } from "@testing-library/react";
import { Header } from "@/components/shared/Header";

const mockLogout = vi.fn();
const mockUseAuthContext = vi.fn();

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => mockUseAuthContext(),
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
