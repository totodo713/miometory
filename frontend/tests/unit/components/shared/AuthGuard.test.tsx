import { render, screen } from "@testing-library/react";
import { AuthGuard } from "@/components/shared/AuthGuard";

const mockReplace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn() }),
}));

const mockUseAuthContext = vi.fn();
vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => mockUseAuthContext(),
}));

describe("AuthGuard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("shows spinner when isLoading is true", () => {
    mockUseAuthContext.mockReturnValue({ user: null, isLoading: true });
    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );
    expect(screen.getAllByText("読み込み中...").length).toBeGreaterThan(0);
    expect(screen.queryByText("Protected")).not.toBeInTheDocument();
  });

  test("redirects to /login when user is null and not loading", () => {
    mockUseAuthContext.mockReturnValue({ user: null, isLoading: false });
    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );
    expect(mockReplace).toHaveBeenCalledWith("/login");
  });

  test("renders children when user is authenticated", () => {
    mockUseAuthContext.mockReturnValue({
      user: { id: "1", email: "a@b.com", displayName: "Taro" },
      isLoading: false,
    });
    render(
      <AuthGuard>
        <p>Protected</p>
      </AuthGuard>,
    );
    expect(screen.getByText("Protected")).toBeInTheDocument();
  });
});
