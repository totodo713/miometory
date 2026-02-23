import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("@/services/api", () => ({
  api: { auth: { verifyEmail: vi.fn() } },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("token=valid-token"),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import VerifyEmailPage from "@/(auth)/verify-email/page";
import { api } from "@/services/api";

describe("VerifyEmailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading state initially", () => {
    (api.auth.verifyEmail as any).mockReturnValue(new Promise(() => {}));
    render(<VerifyEmailPage />);
    const matches = screen.getAllByText(/メールアドレスを確認中/);
    expect(matches.length).toBeGreaterThan(0);
  });

  it("shows success state when verification succeeds", async () => {
    (api.auth.verifyEmail as any).mockResolvedValue(undefined);
    render(<VerifyEmailPage />);
    await waitFor(() => {
      expect(screen.getByText(/認証完了/)).toBeInTheDocument();
    });
    expect(api.auth.verifyEmail).toHaveBeenCalledWith("valid-token");
  });

  it("shows link to login after success", async () => {
    (api.auth.verifyEmail as any).mockResolvedValue(undefined);
    render(<VerifyEmailPage />);
    await waitFor(() => {
      expect(screen.getByText(/認証完了/)).toBeInTheDocument();
    });
    const loginLink = screen.getByRole("link", { name: /ログイン/ });
    expect(loginLink).toHaveAttribute("href", "/login");
  });

  it("shows error state when verification fails", async () => {
    (api.auth.verifyEmail as any).mockRejectedValue(new Error("invalid"));
    render(<VerifyEmailPage />);
    await waitFor(() => {
      expect(screen.getByText(/トークンが無効/)).toBeInTheDocument();
    });
  });

  it("calls verifyEmail with token from search params", async () => {
    (api.auth.verifyEmail as any).mockResolvedValue(undefined);
    render(<VerifyEmailPage />);
    await waitFor(() => {
      expect(api.auth.verifyEmail).toHaveBeenCalledWith("valid-token");
    });
  });
});
