import { render, screen } from "@testing-library/react";
import UnverifiedBanner from "@/components/auth/UnverifiedBanner";
import { IntlWrapper } from "../../../helpers/intl";

describe("UnverifiedBanner", () => {
  test("does not render when no user", () => {
    const { container } = render(
      <IntlWrapper>
        <UnverifiedBanner user={null} isVerified={false} />
      </IntlWrapper>,
    );
    expect(container).toBeEmptyDOMElement();
  });

  test("does not render when user is verified", () => {
    const user = { id: "1", email: "a@example.com", displayName: "A" };
    const { container } = render(
      <IntlWrapper>
        <UnverifiedBanner user={user} isVerified={true} />
      </IntlWrapper>,
    );
    expect(container).toBeEmptyDOMElement();
  });

  test("renders when user exists and not verified", () => {
    const user = { id: "1", email: "a@example.com", displayName: "A" };
    render(
      <IntlWrapper>
        <UnverifiedBanner user={user} isVerified={false} />
      </IntlWrapper>,
    );
    expect(screen.getByRole("alert")).toBeInTheDocument();
    expect(screen.getByText(/メールアドレスが未確認です/)).toBeInTheDocument();
  });
});
