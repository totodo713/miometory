import { act, render, screen, waitFor } from "@testing-library/react";
import type { TenantAffiliationState, TenantMembership, UserStatusResponse } from "@/types/tenant";

// --- Mocks ---

const mockGetStatus = vi.fn<() => Promise<UserStatusResponse>>();
const mockSelectTenant = vi.fn<(tenantId: string) => Promise<void>>();

vi.mock("@/services/api", () => ({
  api: {
    userStatus: {
      getStatus: (...args: unknown[]) => mockGetStatus(...(args as [])),
      selectTenant: (...args: unknown[]) => mockSelectTenant(...(args as [string])),
    },
  },
}));

let mockUser: { id: string; email: string; displayName: string } | null = null;
let mockAuthLoading = false;

vi.mock("@/providers/AuthProvider", () => ({
  useAuthContext: () => ({
    user: mockUser,
    isLoading: mockAuthLoading,
  }),
}));

// Import after mocks
import { TenantProvider, useTenantContext } from "@/providers/TenantProvider";

// --- Helpers ---

function makeMembership(overrides: Partial<TenantMembership> = {}): TenantMembership {
  return {
    memberId: "member-1",
    tenantId: "tenant-1",
    tenantName: "Test Tenant",
    organizationId: null,
    organizationName: null,
    ...overrides,
  };
}

function makeStatusResponse(state: TenantAffiliationState, memberships: TenantMembership[] = []): UserStatusResponse {
  return {
    userId: "user-1",
    email: "test@example.com",
    state,
    memberships,
  };
}

function Consumer() {
  const ctx = useTenantContext();
  return (
    <div>
      <span data-testid="loading">{String(ctx.isLoading)}</span>
      <span data-testid="state">{ctx.affiliationState ?? "null"}</span>
      <span data-testid="memberships">{JSON.stringify(ctx.memberships)}</span>
      <span data-testid="selectedTenantId">{ctx.selectedTenantId ?? "null"}</span>
      <span data-testid="selectedTenantName">{ctx.selectedTenantName ?? "null"}</span>
      <button type="button" data-testid="select" onClick={() => ctx.selectTenant("tenant-1")}>
        Select
      </button>
      <button type="button" data-testid="refresh" onClick={() => ctx.refreshStatus()}>
        Refresh
      </button>
    </div>
  );
}

function renderWithProvider() {
  return render(
    <TenantProvider>
      <Consumer />
    </TenantProvider>,
  );
}

// --- Tests ---

describe("TenantProvider", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser = null;
    mockAuthLoading = false;
    mockGetStatus.mockResolvedValue(makeStatusResponse("UNAFFILIATED", []));
    mockSelectTenant.mockResolvedValue(undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  test("provides initial loading state when user is authenticated", () => {
    mockUser = { id: "user-1", email: "test@example.com", displayName: "Test" };
    // Use a promise that never resolves to keep the provider in loading state
    mockGetStatus.mockReturnValue(new Promise(() => {}));
    renderWithProvider();
    expect(screen.getByTestId("loading").textContent).toBe("true");
    expect(screen.getByTestId("state").textContent).toBe("null");
    expect(screen.getByTestId("memberships").textContent).toBe("[]");
    expect(screen.getByTestId("selectedTenantId").textContent).toBe("null");
    expect(screen.getByTestId("selectedTenantName").textContent).toBe("null");
  });

  test("fetches status on mount when user is authenticated", async () => {
    const memberships = [makeMembership()];
    mockUser = { id: "user-1", email: "test@example.com", displayName: "Test" };
    mockGetStatus.mockResolvedValue(makeStatusResponse("FULLY_ASSIGNED", memberships));

    renderWithProvider();

    await waitFor(() => {
      expect(screen.getByTestId("loading").textContent).toBe("false");
    });
    expect(mockGetStatus).toHaveBeenCalledOnce();
    expect(screen.getByTestId("state").textContent).toBe("FULLY_ASSIGNED");
    expect(screen.getByTestId("memberships").textContent).toBe(JSON.stringify(memberships));
  });

  test("does not fetch when user is null", async () => {
    mockUser = null;
    renderWithProvider();

    // Give effect a chance to run
    await act(async () => {});

    expect(mockGetStatus).not.toHaveBeenCalled();
    expect(screen.getByTestId("loading").textContent).toBe("false");
    expect(screen.getByTestId("state").textContent).toBe("null");
  });

  test("selectTenant calls API and updates state", async () => {
    const memberships = [
      makeMembership({ memberId: "m1", tenantId: "tenant-1", tenantName: "Tenant One" }),
      makeMembership({ memberId: "m2", tenantId: "tenant-2", tenantName: "Tenant Two" }),
    ];
    mockUser = { id: "user-1", email: "test@example.com", displayName: "Test" };
    mockGetStatus.mockResolvedValue(makeStatusResponse("FULLY_ASSIGNED", memberships));

    renderWithProvider();

    await waitFor(() => {
      expect(screen.getByTestId("loading").textContent).toBe("false");
    });

    await act(async () => {
      screen.getByTestId("select").click();
    });

    expect(mockSelectTenant).toHaveBeenCalledWith("tenant-1");
    expect(screen.getByTestId("selectedTenantId").textContent).toBe("tenant-1");
    expect(screen.getByTestId("selectedTenantName").textContent).toBe("Tenant One");
  });

  test("polls every 30s when UNAFFILIATED", async () => {
    vi.useFakeTimers();
    mockUser = { id: "user-1", email: "test@example.com", displayName: "Test" };
    mockGetStatus.mockResolvedValue(makeStatusResponse("UNAFFILIATED", []));

    renderWithProvider();

    // Initial fetch
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(mockGetStatus).toHaveBeenCalledTimes(1);

    // First poll at 30s
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(mockGetStatus).toHaveBeenCalledTimes(2);

    // Second poll at 60s
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(mockGetStatus).toHaveBeenCalledTimes(3);
  });

  test("polls every 30s when AFFILIATED_NO_ORG", async () => {
    vi.useFakeTimers();
    const memberships = [makeMembership({ organizationId: null })];
    mockUser = { id: "user-1", email: "test@example.com", displayName: "Test" };
    mockGetStatus.mockResolvedValue(makeStatusResponse("AFFILIATED_NO_ORG", memberships));

    renderWithProvider();

    // Initial fetch
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(mockGetStatus).toHaveBeenCalledTimes(1);

    // First poll at 30s
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(mockGetStatus).toHaveBeenCalledTimes(2);
  });

  test("does not poll when FULLY_ASSIGNED", async () => {
    vi.useFakeTimers();
    const memberships = [makeMembership({ organizationId: "org-1" })];
    mockUser = { id: "user-1", email: "test@example.com", displayName: "Test" };
    mockGetStatus.mockResolvedValue(makeStatusResponse("FULLY_ASSIGNED", memberships));

    renderWithProvider();

    // Initial fetch
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(mockGetStatus).toHaveBeenCalledTimes(1);

    // No poll at 30s
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(mockGetStatus).toHaveBeenCalledTimes(1);

    // Still no poll at 60s
    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });
    expect(mockGetStatus).toHaveBeenCalledTimes(1);
  });

  test("refreshStatus fetches latest status", async () => {
    const initialMemberships = [makeMembership({ tenantName: "Initial" })];
    const updatedMemberships = [makeMembership({ tenantName: "Updated" })];
    mockUser = { id: "user-1", email: "test@example.com", displayName: "Test" };
    mockGetStatus.mockResolvedValueOnce(makeStatusResponse("UNAFFILIATED", initialMemberships));
    mockGetStatus.mockResolvedValueOnce(makeStatusResponse("FULLY_ASSIGNED", updatedMemberships));

    renderWithProvider();

    await waitFor(() => {
      expect(screen.getByTestId("loading").textContent).toBe("false");
    });
    expect(screen.getByTestId("state").textContent).toBe("UNAFFILIATED");

    await act(async () => {
      screen.getByTestId("refresh").click();
    });

    await waitFor(() => {
      expect(screen.getByTestId("state").textContent).toBe("FULLY_ASSIGNED");
    });
    expect(screen.getByTestId("memberships").textContent).toBe(JSON.stringify(updatedMemberships));
    expect(mockGetStatus).toHaveBeenCalledTimes(2);
  });

  test("resets state when user becomes null", async () => {
    const memberships = [makeMembership()];
    mockUser = { id: "user-1", email: "test@example.com", displayName: "Test" };
    mockGetStatus.mockResolvedValue(makeStatusResponse("FULLY_ASSIGNED", memberships));

    const { rerender } = render(
      <TenantProvider>
        <Consumer />
      </TenantProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("state").textContent).toBe("FULLY_ASSIGNED");
    });

    // Simulate user logout
    mockUser = null;
    rerender(
      <TenantProvider>
        <Consumer />
      </TenantProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("state").textContent).toBe("null");
    });
    expect(screen.getByTestId("memberships").textContent).toBe("[]");
    expect(screen.getByTestId("selectedTenantId").textContent).toBe("null");
  });

  test("useTenantContext throws when used outside TenantProvider", () => {
    // Suppress React error boundary logging
    const spy = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<Consumer />)).toThrow("useTenantContext must be used within a TenantProvider");
    spy.mockRestore();
  });
});
