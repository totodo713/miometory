import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { IntlWrapper } from "../../../helpers/intl";

const { MockApiError } = vi.hoisted(() => {
  class MockApiError extends Error {
    status: number;
    constructor(message: string, status: number) {
      super(message);
      this.name = "ApiError";
      this.status = status;
    }
  }
  return { MockApiError };
});

vi.mock("@/services/api", () => ({
  ApiError: MockApiError,
  api: { auth: { verifyEmail: vi.fn() } },
}));

vi.mock("next/navigation", () => ({
  useSearchParams: vi.fn(() => new URLSearchParams("token=valid-token")),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { useSearchParams } from "next/navigation";
import VerifyEmailPage from "@/(auth)/verify-email/page";
import { api } from "@/services/api";

describe("VerifyEmailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (useSearchParams as any).mockReturnValue(new URLSearchParams("token=valid-token"));
  });

  it("shows loading state initially", () => {
    (api.auth.verifyEmail as any).mockReturnValue(new Promise(() => {}));
    render(
      <IntlWrapper>
        <VerifyEmailPage />
      </IntlWrapper>,
    );
    const matches = screen.getAllByText(/メールアドレスを確認中/);
    expect(matches.length).toBeGreaterThan(0);
  });

  it("shows success state when verification succeeds", async () => {
    (api.auth.verifyEmail as any).mockResolvedValue(undefined);
    render(
      <IntlWrapper>
        <VerifyEmailPage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText(/メールアドレスが確認されました/)).toBeInTheDocument();
    });
    expect(api.auth.verifyEmail).toHaveBeenCalledWith("valid-token");
  });

  it("shows link to login after success", async () => {
    (api.auth.verifyEmail as any).mockResolvedValue(undefined);
    render(
      <IntlWrapper>
        <VerifyEmailPage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText(/メールアドレスが確認されました/)).toBeInTheDocument();
    });
    const loginLink = screen.getByRole("link", { name: /ログイン/ });
    expect(loginLink).toHaveAttribute("href", "/login");
  });

  it("shows error state when verification fails with ApiError", async () => {
    (api.auth.verifyEmail as any).mockRejectedValue(new MockApiError("invalid", 400));
    render(
      <IntlWrapper>
        <VerifyEmailPage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText(/トークンが無効/)).toBeInTheDocument();
    });
  });

  it("shows network error state when verification fails with non-ApiError", async () => {
    (api.auth.verifyEmail as any).mockRejectedValue(new TypeError("Failed to fetch"));
    render(
      <IntlWrapper>
        <VerifyEmailPage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText(/接続エラー/)).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: /再試行/ })).toBeInTheDocument();
  });

  it("shows error when token is missing", async () => {
    (useSearchParams as any).mockReturnValue(new URLSearchParams(""));
    render(
      <IntlWrapper>
        <VerifyEmailPage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(screen.getByText(/トークンが無効/)).toBeInTheDocument();
    });
    expect(api.auth.verifyEmail).not.toHaveBeenCalled();
  });

  it("calls verifyEmail with token from search params", async () => {
    (api.auth.verifyEmail as any).mockResolvedValue(undefined);
    render(
      <IntlWrapper>
        <VerifyEmailPage />
      </IntlWrapper>,
    );
    await waitFor(() => {
      expect(api.auth.verifyEmail).toHaveBeenCalledWith("valid-token");
    });
  });
});
