import { render, screen } from "@testing-library/react";
import { vi } from "vitest";
import { useAuth } from "@/hooks/useAuth";

const mockUseAuthContext = vi.fn();

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => mockUseAuthContext(),
}));

function Consumer() {
  const { user, isAuthenticated, isLoading, userId } = useAuth();
  return (
    <div>
      <span data-testid="id">{userId}</span>
      <span data-testid="email">{user?.email}</span>
      <span data-testid="name">{user?.displayName}</span>
      <span data-testid="auth">{isAuthenticated ? "yes" : "no"}</span>
      <span data-testid="loading">{isLoading ? "loading" : "idle"}</span>
    </div>
  );
}

describe("useAuth hook", () => {
  test("returns authenticated state when user exists", () => {
    mockUseAuthContext.mockReturnValue({
      user: {
        id: "00000000-0000-0000-0000-000000000001",
        email: "bob@example.com",
        displayName: "Bob Engineer",
      },
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<Consumer />);
    expect(screen.getByTestId("id").textContent).toBe("00000000-0000-0000-0000-000000000001");
    expect(screen.getByTestId("email").textContent).toBe("bob@example.com");
    expect(screen.getByTestId("name").textContent).toBe("Bob Engineer");
    expect(screen.getByTestId("auth").textContent).toBe("yes");
    expect(screen.getByTestId("loading").textContent).toBe("idle");
  });

  test("returns unauthenticated state when no user", () => {
    mockUseAuthContext.mockReturnValue({
      user: null,
      isLoading: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    render(<Consumer />);
    expect(screen.getByTestId("id").textContent).toBe("");
    expect(screen.getByTestId("auth").textContent).toBe("no");
    expect(screen.getByTestId("loading").textContent).toBe("idle");
  });
});
