import { render, screen } from "@testing-library/react";
import { vi } from "vitest";
import VerifyEmailPage from "@/(auth)/verify-email/page";

// Mock next/navigation useSearchParams
vi.mock("next/navigation", () => ({
  useSearchParams: vi.fn(() => ({
    get: vi.fn(() => null), // Return null for token to test missing token branch
  })),
}));

describe("Verify Email page", () => {
  test("shows missing token alert when token not provided", () => {
    render(<VerifyEmailPage />);
    expect(screen.getByRole("alert")).toHaveTextContent(/missing token/i);
  });
});
