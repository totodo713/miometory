import { fireEvent, render, screen } from "@testing-library/react";
import LoginPage from "@/(auth)/login/page";

const mockLogin = vi.fn();
const mockReplace = vi.fn();

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => ({
    user: null,
    isLoading: false,
    login: mockLogin,
    logout: vi.fn(),
  }),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: mockReplace,
    push: vi.fn(),
  }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

describe("Login page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("shows error when fields missing", () => {
    render(<LoginPage />);
    fireEvent.click(screen.getByRole("button", { name: /ログイン/i }));
    expect(screen.getByRole("alert")).toHaveTextContent(/入力内容を確認してください/);
  });

  test("remember me checkbox toggles", () => {
    render(<LoginPage />);
    const cb = screen.getByLabelText("remember-me") as HTMLInputElement;
    expect(cb.checked).toBe(false);
    fireEvent.click(cb);
    expect(cb.checked).toBe(true);
  });

  describe("Forgot password link", () => {
    test("renders forgot password link with correct text", () => {
      render(<LoginPage />);
      expect(screen.getByText("パスワードをお忘れですか？")).toBeInTheDocument();
    });

    test("forgot password link points to /password-reset/request", () => {
      render(<LoginPage />);
      const link = screen.getByText("パスワードをお忘れですか？");
      expect(link.closest("a")).toHaveAttribute("href", "/password-reset/request");
    });
  });
});
