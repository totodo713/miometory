import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import LoginPage from "@/(auth)/login/page";
import { ApiError } from "@/services/api";

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

async function fillAndSubmit() {
  fireEvent.change(screen.getByLabelText("メールアドレス"), { target: { value: "user@example.com" } });
  fireEvent.change(screen.getByLabelText("パスワード"), { target: { value: "password123" } });
  fireEvent.click(screen.getByRole("button", { name: /ログイン/i }));
}

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
    const cb = screen.getByLabelText("ログイン状態を保持する") as HTMLInputElement;
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

  describe("Login submission", () => {
    test("redirects to /worklog on successful login", async () => {
      mockLogin.mockResolvedValueOnce(undefined);
      render(<LoginPage />);
      await fillAndSubmit();
      await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/worklog"));
    });

    test("shows credential error on 401", async () => {
      mockLogin.mockRejectedValueOnce(new ApiError("Unauthorized", 401));
      render(<LoginPage />);
      await fillAndSubmit();
      await waitFor(() =>
        expect(screen.getByRole("alert")).toHaveTextContent("メールアドレスまたはパスワードが正しくありません"),
      );
    });

    test("shows network error for non-ApiError failures", async () => {
      mockLogin.mockRejectedValueOnce(new Error("Failed to fetch"));
      render(<LoginPage />);
      await fillAndSubmit();
      await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("ネットワークエラーが発生しました"));
    });

    test("shows server error on 503", async () => {
      mockLogin.mockRejectedValueOnce(new ApiError("Service Unavailable", 503));
      render(<LoginPage />);
      await fillAndSubmit();
      await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("サーバーエラーが発生しました"));
    });

    test("disables form and shows loading text while submitting", async () => {
      let resolveLogin: () => void;
      mockLogin.mockReturnValueOnce(
        new Promise<void>((r) => {
          resolveLogin = r;
        }),
      );
      render(<LoginPage />);
      await fillAndSubmit();
      expect(screen.getByRole("button", { name: /ログイン中/i })).toBeDisabled();
      expect(screen.getByLabelText("メールアドレス")).toBeDisabled();
      expect(screen.getByLabelText("パスワード")).toBeDisabled();
      resolveLogin?.();
      await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/worklog"));
    });
  });
});
