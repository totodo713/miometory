import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlWrapper } from "../../../../tests/helpers/intl";
import SignupPage from "../page";

vi.mock("@/services/api", () => ({
  api: { auth: { signup: vi.fn() } },
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock("@/hooks/useToast", () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: any) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

describe("Signup page", () => {
  test("renders email, password, and confirmation fields", () => {
    render(
      <IntlWrapper>
        <SignupPage />
      </IntlWrapper>,
    );
    expect(screen.getByLabelText(/名前/)).toBeInTheDocument();
    expect(screen.getByLabelText(/メールアドレス/)).toBeInTheDocument();
    expect(screen.getByLabelText(/^パスワード$/)).toBeInTheDocument();
    expect(screen.getByLabelText(/パスワード確認/)).toBeInTheDocument();
  });

  test("shows password mismatch error", async () => {
    const user = userEvent.setup();
    render(
      <IntlWrapper>
        <SignupPage />
      </IntlWrapper>,
    );
    await user.type(screen.getByLabelText(/^パスワード$/), "Password1!");
    await user.type(screen.getByLabelText(/パスワード確認/), "Different1!");
    await user.tab();
    expect(screen.getByText(/パスワードが一致しません/)).toBeInTheDocument();
  });
});
