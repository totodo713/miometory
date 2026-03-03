import { fireEvent, render, screen } from "@testing-library/react";
import { UserMenu } from "@/components/shared/UserMenu";
import { IntlWrapper } from "../../../helpers/intl";

const mockLogout = vi.fn();

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => ({
    user: { id: "1", email: "test@example.com", displayName: "テストユーザー" },
    logout: mockLogout,
  }),
}));

vi.mock("next/link", () => ({
  default: ({ children, href, onClick, ...props }: any) => (
    <a href={href} onClick={onClick} {...props}>
      {children}
    </a>
  ),
}));

describe("UserMenu", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("displays user display name", () => {
    render(
      <IntlWrapper>
        <UserMenu />
      </IntlWrapper>,
    );
    expect(screen.getByText("テストユーザー")).toBeInTheDocument();
  });

  test("opens dropdown on click", () => {
    render(
      <IntlWrapper>
        <UserMenu />
      </IntlWrapper>,
    );
    fireEvent.click(screen.getByRole("button", { name: /ユーザーメニュー/ }));
    expect(screen.getByText("マイページ")).toBeInTheDocument();
    expect(screen.getByText("ログアウト")).toBeInTheDocument();
  });

  test("closes dropdown on second click", () => {
    render(
      <IntlWrapper>
        <UserMenu />
      </IntlWrapper>,
    );
    const trigger = screen.getByRole("button", { name: /ユーザーメニュー/ });
    fireEvent.click(trigger);
    expect(screen.getByText("マイページ")).toBeInTheDocument();
    fireEvent.click(trigger);
    expect(screen.queryByText("マイページ")).not.toBeInTheDocument();
  });

  test("my page link points to /mypage", () => {
    render(
      <IntlWrapper>
        <UserMenu />
      </IntlWrapper>,
    );
    fireEvent.click(screen.getByRole("button", { name: /ユーザーメニュー/ }));
    const link = screen.getByText("マイページ");
    expect(link.closest("a")).toHaveAttribute("href", "/mypage");
  });

  test("closes dropdown when clicking outside", () => {
    render(
      <IntlWrapper>
        <UserMenu />
      </IntlWrapper>,
    );
    const trigger = screen.getByRole("button", { name: /ユーザーメニュー/ });
    fireEvent.click(trigger);
    expect(screen.getByText("マイページ")).toBeInTheDocument();
    fireEvent.mouseDown(document.body);
    expect(screen.queryByText("マイページ")).not.toBeInTheDocument();
  });

  test("calls logout on logout button click", () => {
    render(
      <IntlWrapper>
        <UserMenu />
      </IntlWrapper>,
    );
    fireEvent.click(screen.getByRole("button", { name: /ユーザーメニュー/ }));
    fireEvent.click(screen.getByText("ログアウト"));
    expect(mockLogout).toHaveBeenCalledTimes(1);
  });
});
