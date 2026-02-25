import { fireEvent, render, screen } from "@testing-library/react";
import { vi } from "vitest";
import { IntlWrapper } from "../../../../tests/helpers/intl";
import LoginPage from "../page";

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

describe("Login page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("shows error when fields missing", () => {
    render(
      <IntlWrapper>
        <LoginPage />
      </IntlWrapper>,
    );
    fireEvent.click(screen.getByRole("button", { name: /ログイン/i }));
    expect(screen.getByRole("alert")).toHaveTextContent(/入力内容を確認してください/);
  });

  test("remember me checkbox toggles", () => {
    render(
      <IntlWrapper>
        <LoginPage />
      </IntlWrapper>,
    );
    const cb = screen.getByLabelText("ログイン状態を保持する") as HTMLInputElement;
    expect(cb.checked).toBe(false);
    fireEvent.click(cb);
    expect(cb.checked).toBe(true);
  });

  test("calls login API on submit with valid credentials", async () => {
    mockLogin.mockResolvedValue(undefined);
    render(
      <IntlWrapper>
        <LoginPage />
      </IntlWrapper>,
    );

    fireEvent.change(screen.getByLabelText("メールアドレス"), {
      target: { value: "bob@example.com" },
    });
    fireEvent.change(screen.getByLabelText("パスワード"), {
      target: { value: "Password1" },
    });
    fireEvent.click(screen.getByRole("button", { name: /ログイン/i }));

    expect(mockLogin).toHaveBeenCalledWith("bob@example.com", "Password1", false); // email is already lowercase
  });
});
