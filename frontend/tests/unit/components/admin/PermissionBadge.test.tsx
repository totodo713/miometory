import { render, screen } from "@testing-library/react";
import { IntlWrapper } from "../../../helpers/intl";

const mockHasPermission = vi.fn().mockReturnValue(true);

vi.mock("@/providers/AdminProvider", () => ({
  useAdminContext: () => ({
    adminContext: {
      role: "TENANT_ADMIN",
      permissions: [],
      tenantId: "t1",
      tenantName: "Test Tenant",
      memberId: "m1",
    },
    isLoading: false,
    hasPermission: (p: string) => mockHasPermission(p),
  }),
}));

import { PermissionBadge } from "@/components/admin/PermissionBadge";

describe("PermissionBadge", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHasPermission.mockReturnValue(true);
  });

  test("displays Edit badge when user has edit permission", () => {
    render(
      <IntlWrapper>
        <PermissionBadge editPermission="member.create" />
      </IntlWrapper>,
    );

    const badge = screen.getByText("編集");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveClass("bg-green-100", "text-green-800");
  });

  test("displays View badge when user lacks edit permission", () => {
    mockHasPermission.mockReturnValue(false);

    render(
      <IntlWrapper>
        <PermissionBadge editPermission="member.create" />
      </IntlWrapper>,
    );

    const badge = screen.getByText("閲覧");
    expect(badge).toBeInTheDocument();
    expect(badge).toHaveClass("bg-gray-100", "text-gray-600");
  });

  test("passes the correct permission string to hasPermission", () => {
    render(
      <IntlWrapper>
        <PermissionBadge editPermission="project.create" />
      </IntlWrapper>,
    );

    expect(mockHasPermission).toHaveBeenCalledWith("project.create");
  });
});
