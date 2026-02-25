import { render, screen } from "@testing-library/react";
import { describe, expect, test } from "vitest";
import { AccessDenied } from "@/components/shared/AccessDenied";
import { IntlWrapper } from "../../../helpers/intl";

function renderWithIntl(ui: React.ReactElement) {
  return render(<IntlWrapper>{ui}</IntlWrapper>);
}

describe("AccessDenied", () => {
  test("renders default title and message from i18n", () => {
    renderWithIntl(<AccessDenied />);
    expect(screen.getByText("アクセス権限がありません")).toBeInTheDocument();
    expect(
      screen.getByText("このページを表示する権限がありません。管理者にお問い合わせください。"),
    ).toBeInTheDocument();
  });

  test("renders dashboard link", () => {
    renderWithIntl(<AccessDenied />);
    const link = screen.getByRole("link", { name: "ダッシュボードに戻る" });
    expect(link).toHaveAttribute("href", "/admin");
  });
});
