import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { OrganizationTree } from "@/components/admin/OrganizationTree";
import type { OrganizationTreeNode } from "@/services/api";
import { IntlWrapper } from "../../../helpers/intl";

const mockGetOrganizationTree = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      organizations: {
        getOrganizationTree: (...args: unknown[]) => mockGetOrganizationTree(...args),
      },
    },
  },
}));

const leafNode: OrganizationTreeNode = {
  id: "org3",
  code: "TEAM_C",
  name: "Team C",
  level: 3,
  status: "ACTIVE",
  memberCount: 2,
  children: [],
};

const childNode: OrganizationTreeNode = {
  id: "org2",
  code: "DIV_B",
  name: "Division B",
  level: 2,
  status: "ACTIVE",
  memberCount: 5,
  children: [leafNode],
};

const rootNode: OrganizationTreeNode = {
  id: "org1",
  code: "HQ",
  name: "Headquarters",
  level: 1,
  status: "ACTIVE",
  memberCount: 10,
  children: [childNode],
};

const inactiveNode: OrganizationTreeNode = {
  id: "org4",
  code: "LEGACY",
  name: "Legacy Department",
  level: 1,
  status: "INACTIVE",
  memberCount: 0,
  children: [],
};

const defaultProps = {
  refreshKey: 0,
  onSelectOrg: vi.fn(),
};

describe("OrganizationTree", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetOrganizationTree.mockResolvedValue([rootNode]);
  });

  test("shows loading state then renders tree data", async () => {
    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );
    expect(screen.getByText("読み込み中...")).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("Headquarters")).toBeInTheDocument();
    });
    expect(screen.getByText("HQ")).toBeInTheDocument();
  });

  test("renders nested hierarchy with 3+ levels", async () => {
    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Headquarters")).toBeInTheDocument();
    });
    // Level 2
    expect(screen.getByText("Division B")).toBeInTheDocument();
    expect(screen.getByText("DIV_B")).toBeInTheDocument();
    // Level 3
    expect(screen.getByText("Team C")).toBeInTheDocument();
    expect(screen.getByText("TEAM_C")).toBeInTheDocument();
  });

  test("collapse toggle hides children when clicked", async () => {
    const user = userEvent.setup();
    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Headquarters")).toBeInTheDocument();
    });

    // All nodes visible initially (expanded by default)
    expect(screen.getByText("Division B")).toBeInTheDocument();
    expect(screen.getByText("Team C")).toBeInTheDocument();

    // Click the collapse button on the root node (which has the down-arrow label)
    const collapseButton = screen.getAllByLabelText("折りたたむ")[0];
    await user.click(collapseButton);

    // Children should be hidden after collapsing root
    expect(screen.queryByText("Division B")).not.toBeInTheDocument();
    expect(screen.queryByText("Team C")).not.toBeInTheDocument();
  });

  test("expand toggle shows children when clicked after collapse", async () => {
    const user = userEvent.setup();
    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Headquarters")).toBeInTheDocument();
    });

    // Collapse root
    const collapseButton = screen.getAllByLabelText("折りたたむ")[0];
    await user.click(collapseButton);

    expect(screen.queryByText("Division B")).not.toBeInTheDocument();

    // Expand root
    const expandButton = screen.getByLabelText("展開する");
    await user.click(expandButton);

    expect(screen.getByText("Division B")).toBeInTheDocument();
  });

  test("calls onSelectOrg callback when node name is clicked", async () => {
    const user = userEvent.setup();
    const onSelectOrg = vi.fn();
    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} onSelectOrg={onSelectOrg} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Headquarters")).toBeInTheDocument();
    });

    // Click on the node name — the clickable button containing the name
    await user.click(screen.getByText("Headquarters"));

    expect(onSelectOrg).toHaveBeenCalledWith(rootNode);
  });

  test("displays status badge for active organization", async () => {
    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Headquarters")).toBeInTheDocument();
    });

    // All three nodes are active
    const activeBadges = screen.getAllByText("有効");
    expect(activeBadges.length).toBe(3);
  });

  test("displays status badge for inactive organization", async () => {
    mockGetOrganizationTree.mockResolvedValue([inactiveNode]);

    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Legacy Department")).toBeInTheDocument();
    });

    expect(screen.getByText("無効")).toBeInTheDocument();
  });

  test("displays member count indicator", async () => {
    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Headquarters")).toBeInTheDocument();
    });

    expect(screen.getByText("10人")).toBeInTheDocument();
    expect(screen.getByText("5人")).toBeInTheDocument();
    expect(screen.getByText("2人")).toBeInTheDocument();
  });

  test("include inactive toggle checkbox triggers API reload", async () => {
    const user = userEvent.setup();
    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Headquarters")).toBeInTheDocument();
    });

    // Initial call should pass false (default)
    expect(mockGetOrganizationTree).toHaveBeenCalledWith(false);

    // Check the "include inactive" checkbox
    const checkbox = screen.getByRole("checkbox");
    await user.click(checkbox);

    await waitFor(() => {
      expect(mockGetOrganizationTree).toHaveBeenCalledWith(true);
    });
  });

  test("shows empty tree state when no organizations", async () => {
    mockGetOrganizationTree.mockResolvedValue([]);

    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("組織が見つかりません")).toBeInTheDocument();
    });
  });

  test("shows loading state before data is loaded", async () => {
    // Make the mock never resolve to keep loading state visible
    mockGetOrganizationTree.mockReturnValue(new Promise(() => {}));

    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    expect(screen.getByText("読み込み中...")).toBeInTheDocument();
  });

  test("reloads when refreshKey changes", async () => {
    const { rerender } = render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} refreshKey={0} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(mockGetOrganizationTree).toHaveBeenCalledTimes(1);
    });

    rerender(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} refreshKey={1} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(mockGetOrganizationTree).toHaveBeenCalledTimes(2);
    });
  });

  test("leaf nodes do not show expand/collapse button", async () => {
    mockGetOrganizationTree.mockResolvedValue([leafNode]);

    render(
      <IntlWrapper>
        <OrganizationTree {...defaultProps} />
      </IntlWrapper>,
    );

    await waitFor(() => {
      expect(screen.getByText("Team C")).toBeInTheDocument();
    });

    // Leaf node should not have expand/collapse buttons
    expect(screen.queryByLabelText("折りたたむ")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("展開する")).not.toBeInTheDocument();
  });
});
