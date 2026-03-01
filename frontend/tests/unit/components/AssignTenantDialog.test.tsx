/**
 * Unit tests for AssignTenantDialog component
 *
 * Tests search, selection, assignment, and keyboard interaction behavior.
 */

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

// --- Mocks ---

const mockSearchForAssignment = vi.fn();
const mockAssignTenant = vi.fn();

vi.mock("@/services/api", () => ({
  api: {
    admin: {
      users: { searchForAssignment: (...args: unknown[]) => mockSearchForAssignment(...args) },
      members: { assignTenant: (...args: unknown[]) => mockAssignTenant(...args) },
    },
  },
}));

vi.mock("next-intl", () => ({
  useTranslations: () => (key: string) => key,
}));

// Import after mocks
import { AssignTenantDialog } from "@/components/admin/AssignTenantDialog";

// --- Helpers ---

const defaultProps = {
  onClose: vi.fn(),
  onAssigned: vi.fn(),
};

function renderDialog(props = {}) {
  return render(<AssignTenantDialog {...defaultProps} {...props} />);
}

function findAliceButton(): HTMLElement {
  const buttons = screen.getAllByRole("button");
  const btn = buttons.find((b) => b.textContent?.includes("Alice Smith"));
  if (!btn) throw new Error("Alice button not found");
  return btn;
}

const searchResults = {
  users: [
    { userId: "user-1", email: "alice@example.com", name: "Alice Smith", isAlreadyInTenant: false },
    { userId: "user-2", email: "bob@example.com", name: "Bob Jones", isAlreadyInTenant: true },
    { userId: "user-3", email: "charlie@example.com", name: "", isAlreadyInTenant: false },
  ],
};

// --- Tests ---

describe("AssignTenantDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchForAssignment.mockResolvedValue(searchResults);
    mockAssignTenant.mockResolvedValue(undefined);
  });

  it("renders title and search input", () => {
    renderDialog();

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("title")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("searchPlaceholder")).toBeInTheDocument();
  });

  it("calls searchForAssignment API on search button click", async () => {
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "alice@example.com");
    await user.click(screen.getByRole("button", { name: "search" }));

    await waitFor(() => {
      expect(mockSearchForAssignment).toHaveBeenCalledWith("alice@example.com");
    });
  });

  it("displays search results", async () => {
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "test@example.com");
    await user.click(screen.getByRole("button", { name: "search" }));

    await waitFor(() => {
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    });
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    expect(screen.getByText("bob@example.com")).toBeInTheDocument();
  });

  it("shows 'already assigned' badge for users already in tenant", async () => {
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "test@example.com");
    await user.click(screen.getByRole("button", { name: "search" }));

    await waitFor(() => {
      expect(screen.getByText("alreadyAssigned")).toBeInTheDocument();
    });
  });

  it("disables click on already-assigned users", async () => {
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "test@example.com");
    await user.click(screen.getByRole("button", { name: "search" }));

    await waitFor(() => {
      expect(screen.getByText("Bob Jones")).toBeInTheDocument();
    });

    // The button for the already-assigned user should be disabled
    const buttons = screen.getAllByRole("button");
    const bobButton = buttons.find((btn) => btn.textContent?.includes("Bob Jones"));
    expect(bobButton).toBeDisabled();
  });

  it("shows display name input when user is selected", async () => {
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "test@example.com");
    await user.click(screen.getByRole("button", { name: "search" }));

    await waitFor(() => {
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    });

    // Click the first (non-assigned) user
    await user.click(findAliceButton());

    expect(screen.getByText("displayName")).toBeInTheDocument();
  });

  it("pre-fills display name with user's name", async () => {
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "test@example.com");
    await user.click(screen.getByRole("button", { name: "search" }));

    await waitFor(() => {
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    });

    await user.click(findAliceButton());

    // Display name input should be pre-filled with "Alice Smith"
    const displayNameInput = screen.getByDisplayValue("Alice Smith");
    expect(displayNameInput).toBeInTheDocument();
  });

  it("calls assignTenant API on assign button click", async () => {
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "test@example.com");
    await user.click(screen.getByRole("button", { name: "search" }));

    await waitFor(() => {
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    });

    await user.click(findAliceButton());

    await user.click(screen.getByRole("button", { name: "assign" }));

    await waitFor(() => {
      expect(mockAssignTenant).toHaveBeenCalledWith("user-1", "Alice Smith");
    });
  });

  it("calls onAssigned callback on success", async () => {
    const onAssigned = vi.fn();
    const user = userEvent.setup();
    renderDialog({ onAssigned });

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "test@example.com");
    await user.click(screen.getByRole("button", { name: "search" }));

    await waitFor(() => {
      expect(screen.getByText("Alice Smith")).toBeInTheDocument();
    });

    await user.click(findAliceButton());

    await user.click(screen.getByRole("button", { name: "assign" }));

    await waitFor(() => {
      expect(onAssigned).toHaveBeenCalled();
    });
  });

  it("closes on cancel button click", async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderDialog({ onClose });

    await user.click(screen.getByRole("button", { name: "cancel" }));

    expect(onClose).toHaveBeenCalled();
  });

  it("closes on Escape key", () => {
    const onClose = vi.fn();
    renderDialog({ onClose });

    fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" });

    expect(onClose).toHaveBeenCalled();
  });

  it("shows 'no results' when search returns empty", async () => {
    mockSearchForAssignment.mockResolvedValue({ users: [] });
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "nonexistent@example.com");
    await user.click(screen.getByRole("button", { name: "search" }));

    await waitFor(() => {
      expect(screen.getByText("noResults")).toBeInTheDocument();
    });
  });

  it("search triggered by Enter key", async () => {
    const user = userEvent.setup();
    renderDialog();

    const input = screen.getByPlaceholderText("searchPlaceholder");
    await user.type(input, "test@example.com");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(mockSearchForAssignment).toHaveBeenCalledWith("test@example.com");
    });
  });
});
