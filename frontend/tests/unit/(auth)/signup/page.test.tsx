import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import SignupPage from "@/(auth)/signup/page";
import { IntlWrapper } from "../../../helpers/intl";

vi.mock("@/services/api", () => ({
  api: { auth: { signup: vi.fn() } },
}));

const mockPush = vi.fn();
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

const mockSuccess = vi.fn();
const mockError = vi.fn();
vi.mock("@/hooks/useToast", () => ({
  useToast: () => ({ success: mockSuccess, error: mockError }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

import { api } from "@/services/api";

describe("Signup page", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (api.auth.signup as any).mockResolvedValue(undefined);
  });

  test("renders signup form with Japanese labels", () => {
    render(
      <IntlWrapper>
        <SignupPage />
      </IntlWrapper>,
    );
    expect(screen.getByLabelText(/名前/)).toBeInTheDocument();
    expect(screen.getByLabelText(/メールアドレス/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^パスワード$/)).toBeInTheDocument();
    expect(screen.getByLabelText(/パスワードの確認/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /登録/ })).toBeInTheDocument();
  });

  test("shows password mismatch error on blur", async () => {
    const user = userEvent.setup();
    render(
      <IntlWrapper>
        <SignupPage />
      </IntlWrapper>,
    );
    await user.type(screen.getByLabelText(/^パスワード$/), "Password1!");
    await user.type(screen.getByLabelText(/パスワードの確認/), "Different1!");
    await user.tab();
    expect(screen.getByText(/パスワードが一致しません/)).toBeInTheDocument();
  });

  test("calls api.auth.signup on submit", async () => {
    const user = userEvent.setup();
    render(
      <IntlWrapper>
        <SignupPage />
      </IntlWrapper>,
    );
    await user.type(screen.getByLabelText(/名前/), "Test User");
    await user.type(screen.getByLabelText(/メールアドレス/), "a@b.com");
    await user.type(screen.getByLabelText(/^パスワード$/), "Password1!");
    await user.type(screen.getByLabelText(/パスワードの確認/), "Password1!");
    await user.click(screen.getByRole("button", { name: /登録/ }));
    await waitFor(() => {
      expect(api.auth.signup).toHaveBeenCalledWith("a@b.com", "Test User", "Password1!");
    });
  });

  test("navigates to confirm page on success", async () => {
    const user = userEvent.setup();
    render(
      <IntlWrapper>
        <SignupPage />
      </IntlWrapper>,
    );
    await user.type(screen.getByLabelText(/名前/), "Test User");
    await user.type(screen.getByLabelText(/メールアドレス/), "a@b.com");
    await user.type(screen.getByLabelText(/^パスワード$/), "Password1!");
    await user.type(screen.getByLabelText(/パスワードの確認/), "Password1!");
    await user.click(screen.getByRole("button", { name: /登録/ }));
    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith("/signup/confirm");
    });
  });
});
