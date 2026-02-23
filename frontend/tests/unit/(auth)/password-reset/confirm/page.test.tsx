import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactElement } from "react";
import PasswordResetConfirmPage from "@/(auth)/password-reset/confirm/page";
import { ToastProvider } from "@/components/shared/ToastProvider";
import { api } from "@/services/api";

function renderWithProviders(ui: ReactElement) {
	return render(<ToastProvider>{ui}</ToastProvider>);
}

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

const mockPush = vi.fn();
const mockReplace = vi.fn();
const mockGetParam = vi.fn();

// Stable references to prevent infinite useEffect re-triggers
const mockRouter = { push: mockPush, replace: mockReplace };
const mockSearchParams = { get: mockGetParam };

vi.mock("next/navigation", () => ({
  useRouter: () => mockRouter,
  useSearchParams: () => mockSearchParams,
}));

vi.mock("@/services/api", () => ({
  api: { auth: { confirmPasswordReset: vi.fn() } },
}));

vi.mock("@/components/auth/PasswordStrengthIndicator", () => ({
  PasswordStrengthIndicator: ({ password }: any) => (
    <div data-testid="password-strength-indicator" data-password={password} />
  ),
}));

async function renderAndWaitForForm() {
	renderWithProviders(<PasswordResetConfirmPage />);
	await waitFor(() => {
		expect(screen.getByLabelText(/新しいパスワード/)).toBeInTheDocument();
	});
}

async function fillAndSubmit(newPass: string, confirmPass: string) {
  fireEvent.change(screen.getByLabelText(/新しいパスワード/), { target: { value: newPass } });
  fireEvent.change(screen.getByLabelText(/パスワードの確認/), { target: { value: confirmPass } });
  fireEvent.click(screen.getByRole("button", { name: /パスワードを変更/ }));
}

describe("PasswordResetConfirmPage", () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    sessionStorage.clear();
    mockGetParam.mockReturnValue("valid-token-123");
  });

  describe("Token extraction", () => {
    test("renders form with password fields when token is present", async () => {
      await renderAndWaitForForm();
      expect(screen.getByLabelText(/パスワードの確認/)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /パスワードを変更/ })).toBeInTheDocument();
    });
  });

  describe("Missing token", () => {
    test("shows error when no token in URL or sessionStorage", async () => {
      mockGetParam.mockReturnValue(null);
      renderWithProviders(<PasswordResetConfirmPage />);
      await waitFor(() => {
        expect(screen.getByText(/無効なリンクです/)).toBeInTheDocument();
      });
    });

    test("shows link to request new reset when token is missing", async () => {
      mockGetParam.mockReturnValue(null);
      renderWithProviders(<PasswordResetConfirmPage />);
      await waitFor(() => {
        const link = screen.getByText("パスワードリセットをリクエスト");
        expect(link.closest("a")).toHaveAttribute("href", "/password-reset/request");
      });
    });
  });

  describe("Password validation", () => {
    test("shows error for password shorter than 8 characters", async () => {
      await renderAndWaitForForm();
      await fillAndSubmit("short1", "short1");
      await waitFor(() => {
        expect(screen.getByText("パスワードは8文字以上で入力してください")).toBeInTheDocument();
      });
    });

    test("shows error for mismatched passwords", async () => {
      await renderAndWaitForForm();
      await fillAndSubmit("ValidPass1", "DifferentPass1");
      await waitFor(() => {
        expect(screen.getByText("パスワードが一致しません")).toBeInTheDocument();
      });
    });
  });

  describe("Submission success", () => {
    test("shows success message when API resolves", async () => {
      vi.mocked(api.auth.confirmPasswordReset).mockResolvedValue({ message: "OK" });
      await renderAndWaitForForm();
      await fillAndSubmit("ValidPass1", "ValidPass1");
      await waitFor(() => {
        expect(screen.getByRole("heading", { name: "パスワードを変更しました" })).toBeInTheDocument();
      });
    });

    test("shows countdown message after success", async () => {
      vi.mocked(api.auth.confirmPasswordReset).mockResolvedValue({ message: "OK" });
      await renderAndWaitForForm();
      await fillAndSubmit("ValidPass1", "ValidPass1");
      await waitFor(() => {
        expect(screen.getByText(/ログインページにリダイレクトします/)).toBeInTheDocument();
      });
    });

    test("redirects to login after countdown completes", async () => {
      vi.useFakeTimers({ shouldAdvanceTime: true });
      vi.mocked(api.auth.confirmPasswordReset).mockResolvedValue({ message: "OK" });
      await renderAndWaitForForm();
      await fillAndSubmit("ValidPass1", "ValidPass1");

      await waitFor(() => {
        expect(screen.getByRole("heading", { name: "パスワードを変更しました" })).toBeInTheDocument();
      });

      // Advance through the countdown (3 x 1 second intervals)
      // Each step triggers a state update that schedules the next timeout
      for (let i = 0; i < 3; i++) {
        await act(async () => {
          await vi.advanceTimersByTimeAsync(1000);
        });
      }

      expect(mockPush).toHaveBeenCalledWith("/login");
      vi.useRealTimers();
    });
  });

  describe("Error handling", () => {
    test("shows expired token error for 404 response", async () => {
      vi.mocked(api.auth.confirmPasswordReset).mockRejectedValue({ status: 404, message: "Token expired" });
      await renderAndWaitForForm();
      await fillAndSubmit("ValidPass1", "ValidPass1");
      await waitFor(() => {
        expect(screen.getByText(/有効期限が切れています/)).toBeInTheDocument();
      });
    });

    test("shows link to /password-reset/request for expired token", async () => {
      vi.mocked(api.auth.confirmPasswordReset).mockRejectedValue({ status: 404, message: "Token expired" });
      await renderAndWaitForForm();
      await fillAndSubmit("ValidPass1", "ValidPass1");
      await waitFor(() => {
        const link = screen.getByText("パスワードリセットをリクエスト");
        expect(link.closest("a")).toHaveAttribute("href", "/password-reset/request");
      });
    });

    test("shows validation error for 400 response", async () => {
      vi.mocked(api.auth.confirmPasswordReset).mockRejectedValue({
        status: 400,
        message: "パスワードが要件を満たしていません。",
      });
      await renderAndWaitForForm();
      await fillAndSubmit("ValidPass1", "ValidPass1");
      await waitFor(() => {
        expect(screen.getByText(/パスワードが要件を満たしていません/)).toBeInTheDocument();
      });
    });

    test("shows network error with retry option", async () => {
      vi.mocked(api.auth.confirmPasswordReset).mockRejectedValue({ status: undefined });
      await renderAndWaitForForm();
      await fillAndSubmit("ValidPass1", "ValidPass1");
      await waitFor(() => {
        expect(screen.getByText(/ネットワークエラー/)).toBeInTheDocument();
        expect(screen.getByText("再試行")).toBeInTheDocument();
      });
    });
  });

  describe("Loading state", () => {
    test("shows loading text during API call", async () => {
      let resolveApi!: (value: any) => void;
      vi.mocked(api.auth.confirmPasswordReset).mockImplementation(
        () =>
          new Promise((resolve) => {
            resolveApi = resolve;
          }),
      );
      await renderAndWaitForForm();
      await fillAndSubmit("ValidPass1", "ValidPass1");

      await waitFor(() => {
        expect(screen.getByRole("button", { name: /処理中/ })).toBeDisabled();
      });

      await act(async () => {
        resolveApi({ message: "OK" });
      });
    });
  });

  describe("Password strength integration", () => {
    test("renders PasswordStrengthIndicator component", async () => {
      await renderAndWaitForForm();
      expect(screen.getByTestId("password-strength-indicator")).toBeInTheDocument();
    });
  });
});
