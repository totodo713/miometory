import { render, screen } from "@testing-library/react";
import { useAuth } from "@/hooks/useAuth";

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
  test("returns dev mock user and auth state", () => {
    render(<Consumer />);
    expect(screen.getByTestId("id").textContent).toBe("00000000-0000-0000-0000-000000000001");
    expect(screen.getByTestId("email").textContent).toBe("dev@example.com");
    expect(screen.getByTestId("name").textContent).toBe("Development User");
    expect(screen.getByTestId("auth").textContent).toBe("yes");
    expect(screen.getByTestId("loading").textContent).toBe("idle");
  });
});
