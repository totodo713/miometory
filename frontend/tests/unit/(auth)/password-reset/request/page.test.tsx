import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import PasswordResetRequestPage from "@/(auth)/password-reset/request/page";
import { checkRateLimit, getMinutesUntilReset, setupStorageListener } from "@/lib/utils/rate-limit";
import { api } from "@/services/api";

// Mock next/link
vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

// Mock API
vi.mock("@/services/api", () => ({
  api: {
    auth: {
      requestPasswordReset: vi.fn(),
    },
  },
}));

// Mock rate-limit utilities
vi.mock("@/lib/utils/rate-limit", () => ({
  checkRateLimit: vi.fn(() => ({
    attempts: [],
    isAllowed: true,
    remainingAttempts: 3,
    resetTime: null,
  })),
  recordAttempt: vi.fn(),
  getMinutesUntilReset: vi.fn(() => 5),
  setupStorageListener: vi.fn(() => () => {}),
}));

describe("PasswordResetRequestPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(checkRateLimit).mockReturnValue({
      attempts: [],
      isAllowed: true,
      remainingAttempts: 3,
      resetTime: null,
    });
    vi.mocked(setupStorageListener).mockReturnValue(() => {});
  });

  describe("Rendering", () => {
    test("renders form with email input, submit button, and login link", () => {
      render(<PasswordResetRequestPage />);

      expect(screen.getByLabelText(/メールアドレス/)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /リセットリンクを送信/ })).toBeInTheDocument();
      expect(screen.getByText("ログインに戻る")).toBeInTheDocument();
    });

    test("login link points to /login", () => {
      render(<PasswordResetRequestPage />);
      const links = screen.getAllByText("ログインに戻る");
      expect(links[0].closest("a")).toHaveAttribute("href", "/login");
    });
  });

  describe("Email validation", () => {
    test("shows validation error when submitting empty email", async () => {
      render(<PasswordResetRequestPage />);

      fireEvent.click(screen.getByRole("button", { name: /リセットリンクを送信/ }));

      await waitFor(() => {
        expect(screen.getByText("メールアドレスを入力してください")).toBeInTheDocument();
      });
    });

    test("shows validation error for invalid email format", async () => {
      render(<PasswordResetRequestPage />);

      fireEvent.change(screen.getByLabelText(/メールアドレス/), {
        target: { value: "invalid-email" },
      });
      fireEvent.click(screen.getByRole("button", { name: /リセットリンクを送信/ }));

      await waitFor(() => {
        expect(screen.getByText("有効なメールアドレスを入力してください")).toBeInTheDocument();
      });
    });
  });

  describe("Submission success", () => {
    test("displays success message when API resolves", async () => {
      vi.mocked(api.auth.requestPasswordReset).mockResolvedValue({ message: "OK" });

      render(<PasswordResetRequestPage />);

      fireEvent.change(screen.getByLabelText(/メールアドレス/), {
        target: { value: "user@example.com" },
      });
      fireEvent.click(screen.getByRole("button", { name: /リセットリンクを送信/ }));

      await waitFor(() => {
        expect(screen.getByText("メールを送信しました")).toBeInTheDocument();
      });
    });

    test("normalizes email to lowercase before calling the API", async () => {
      vi.mocked(api.auth.requestPasswordReset).mockResolvedValue({ message: "OK" });

      render(<PasswordResetRequestPage />);

      fireEvent.change(screen.getByLabelText(/メールアドレス/), {
        target: { value: "User@Example.COM" },
      });
      fireEvent.click(screen.getByRole("button", { name: /リセットリンクを送信/ }));

      await waitFor(() => {
        expect(api.auth.requestPasswordReset).toHaveBeenCalledWith({ email: "user@example.com" });
      });
    });

    test("hides form after success", async () => {
      vi.mocked(api.auth.requestPasswordReset).mockResolvedValue({ message: "OK" });

      render(<PasswordResetRequestPage />);

      fireEvent.change(screen.getByLabelText(/メールアドレス/), {
        target: { value: "user@example.com" },
      });
      fireEvent.click(screen.getByRole("button", { name: /リセットリンクを送信/ }));

      await waitFor(() => {
        expect(screen.queryByLabelText(/メールアドレス/)).not.toBeInTheDocument();
      });
    });

    test("shows identical success message regardless of API response (anti-enumeration)", async () => {
      vi.mocked(api.auth.requestPasswordReset).mockResolvedValue({ message: "OK" });

      render(<PasswordResetRequestPage />);

      fireEvent.change(screen.getByLabelText(/メールアドレス/), {
        target: { value: "nonexistent@example.com" },
      });
      fireEvent.click(screen.getByRole("button", { name: /リセットリンクを送信/ }));

      await waitFor(() => {
        expect(screen.getByText("メールを送信しました")).toBeInTheDocument();
      });
    });
  });

  describe("Loading state", () => {
    test("disables submit button during API call", async () => {
      let resolveApi: (value: any) => void;
      vi.mocked(api.auth.requestPasswordReset).mockImplementation(
        () =>
          new Promise((resolve) => {
            resolveApi = resolve;
          }),
      );

      render(<PasswordResetRequestPage />);

      fireEvent.change(screen.getByLabelText(/メールアドレス/), {
        target: { value: "user@example.com" },
      });
      fireEvent.click(screen.getByRole("button", { name: /リセットリンクを送信/ }));

      await waitFor(() => {
        expect(screen.getByRole("button", { name: /送信中/ })).toBeDisabled();
      });

      resolveApi?.({ message: "OK" });
    });
  });

  describe("Error handling", () => {
    test("displays error message when API rejects with network error", async () => {
      vi.mocked(api.auth.requestPasswordReset).mockRejectedValue(new Error("Network error"));

      render(<PasswordResetRequestPage />);

      fireEvent.change(screen.getByLabelText(/メールアドレス/), {
        target: { value: "user@example.com" },
      });
      fireEvent.click(screen.getByRole("button", { name: /リセットリンクを送信/ }));

      await waitFor(() => {
        expect(screen.getByText(/ネットワークエラー/)).toBeInTheDocument();
      });
    });

    test("shows retry button for retryable errors", async () => {
      vi.mocked(api.auth.requestPasswordReset).mockRejectedValue(new Error("Network error"));

      render(<PasswordResetRequestPage />);

      fireEvent.change(screen.getByLabelText(/メールアドレス/), {
        target: { value: "user@example.com" },
      });
      fireEvent.click(screen.getByRole("button", { name: /リセットリンクを送信/ }));

      await waitFor(() => {
        expect(screen.getByText("再試行")).toBeInTheDocument();
      });
    });
  });

  describe("Rate limiting UI", () => {
    test("disables submit when rate limited", () => {
      vi.mocked(checkRateLimit).mockReturnValue({
        attempts: [1, 2, 3],
        isAllowed: false,
        remainingAttempts: 0,
        resetTime: Date.now() + 300000,
      });
      vi.mocked(getMinutesUntilReset).mockReturnValue(5);

      render(<PasswordResetRequestPage />);

      expect(screen.getByRole("button", { name: /リセットリンクを送信/ })).toBeDisabled();
    });

    test("shows rate limit warning message when limited", () => {
      vi.mocked(checkRateLimit).mockReturnValue({
        attempts: [1, 2, 3],
        isAllowed: false,
        remainingAttempts: 0,
        resetTime: Date.now() + 300000,
      });
      vi.mocked(getMinutesUntilReset).mockReturnValue(5);

      render(<PasswordResetRequestPage />);

      expect(screen.getByText(/リクエスト制限に達しました/)).toBeInTheDocument();
    });
  });
});
