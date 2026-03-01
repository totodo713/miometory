import { expect, type Page, test } from "@playwright/test";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// --- Auth Helper ---

interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  memberId?: string;
}

async function loginAs(page: Page, email: string, password: string): Promise<AuthUser> {
  await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

  if (page.url() === "about:blank") {
    await page.goto("/");
    await page.waitForLoadState("domcontentloaded");
  }
  await page.evaluate(() => window.sessionStorage.clear());

  const response = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, {
    data: { email, password, rememberMe: false },
  });
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`Login failed for ${email}: ${response.status()} - ${body}`);
  }
  const body = await response.json();
  const authUser: AuthUser = {
    id: body.user.id,
    email: body.user.email,
    displayName: body.user.name,
    memberId: body.user.memberId ?? undefined,
  };

  await page.addInitScript((user) => {
    window.sessionStorage.setItem("miometry_auth_user", JSON.stringify(user));
  }, authUser);

  return authUser;
}

// --- Mock Helpers ---

/** Mock the user/status endpoint to return a specific tenant affiliation state */
async function mockUserStatus(
  page: Page,
  state: "UNAFFILIATED" | "AFFILIATED_NO_ORG" | "FULLY_ASSIGNED",
  memberships: Array<{
    memberId: string;
    tenantId: string;
    tenantName: string;
    organizationId: string | null;
    organizationName: string | null;
  }> = [],
) {
  await page.route("**/api/v1/user/status", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        userId: "test-user-id",
        email: "test@example.com",
        state,
        memberships,
      }),
    });
  });
}

/** Mock polling/background APIs to prevent flaky timeouts */
async function mockGlobalApis(page: Page) {
  await page.route("**/api/v1/notifications**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ content: [], unreadCount: 0, totalElements: 0, totalPages: 0 }),
    });
  });

  await page.route("**/api/v1/worklog/approvals/member/**", async (route) => {
    await route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ message: "No approval found" }),
    });
  });

  await page.route("**/api/v1/worklog/rejections/daily**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ rejections: [] }),
    });
  });
}

// --- Sample data ---

const TENANT_A = {
  memberId: "member-a-1",
  tenantId: "tenant-a-id",
  tenantName: "Tenant Alpha",
  organizationId: "org-a-id",
  organizationName: "Org Alpha",
};

const TENANT_B = {
  memberId: "member-b-1",
  tenantId: "tenant-b-id",
  tenantName: "Tenant Beta",
  organizationId: "org-b-id",
  organizationName: "Org Beta",
};

// ============================================================
// Test Suite: Tenant UI Routing & Screens
// ============================================================

test.describe("Tenant UI: State-based routing", () => {
  test.describe.configure({ timeout: 30_000 });

  test.beforeEach(({ browserName }) => {
    test.skip(browserName !== "chromium" && !process.env.CI, "Skipping non-Chromium browsers locally");
  });

  test("UNAFFILIATED user is redirected to /waiting", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "UNAFFILIATED");

    await page.goto("/");
    await page.waitForURL("**/waiting", { timeout: 10_000 });

    // Verify waiting page content
    await expect(page.getByRole("heading", { name: "Waiting for Tenant Assignment" })).toBeVisible();
    await expect(
      page.getByText("An administrator needs to add you to a tenant before you can use the system."),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Logout" })).toBeVisible();
  });

  test("AFFILIATED_NO_ORG user is redirected to /pending-organization", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "AFFILIATED_NO_ORG", [{ ...TENANT_A, organizationId: null, organizationName: null }]);

    await page.goto("/");
    await page.waitForURL("**/pending-organization", { timeout: 10_000 });

    // Verify pending-organization page content
    await expect(page.getByRole("heading", { name: "Waiting for Organization Assignment" })).toBeVisible();
    await expect(
      page.getByText(
        "You have been added to a tenant. An administrator needs to assign you to an organization before you can start working.",
      ),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Logout" })).toBeVisible();
  });

  test("FULLY_ASSIGNED user with multiple tenants and no selection is redirected to /select-tenant", async ({
    page,
  }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "FULLY_ASSIGNED", [TENANT_A, TENANT_B]);

    await page.goto("/");
    await page.waitForURL("**/select-tenant", { timeout: 10_000 });

    // Verify select-tenant page content
    await expect(page.getByRole("heading", { name: "Select a Tenant" })).toBeVisible();
    await expect(page.getByText("Tenant Alpha")).toBeVisible();
    await expect(page.getByText("Tenant Beta")).toBeVisible();
  });

  test("FULLY_ASSIGNED user with single tenant is redirected to /worklog", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "FULLY_ASSIGNED", [TENANT_A]);

    // Mock admin context (needed for worklog page)
    await page.route("**/api/v1/admin/context", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          memberId: TENANT_A.memberId,
          tenantId: TENANT_A.tenantId,
          permissions: [],
        }),
      });
    });

    await page.goto("/");
    await page.waitForURL("**/worklog", { timeout: 10_000 });
  });
});

// ============================================================
// Test Suite: Select Tenant Page Interaction
// ============================================================

test.describe("Tenant UI: Select-tenant page", () => {
  test.describe.configure({ timeout: 30_000 });

  test.beforeEach(({ browserName }) => {
    test.skip(browserName !== "chromium" && !process.env.CI, "Skipping non-Chromium browsers locally");
  });

  test("selecting a tenant calls select-tenant API and redirects to /worklog", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "FULLY_ASSIGNED", [TENANT_A, TENANT_B]);

    // Mock the select-tenant API
    let selectedTenantId: string | null = null;
    await page.route("**/api/v1/user/select-tenant", async (route) => {
      const body = JSON.parse(route.request().postData() || "{}");
      selectedTenantId = body.tenantId;
      await route.fulfill({ status: 204 });
    });

    // Mock admin context for the worklog page
    await page.route("**/api/v1/admin/context", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          memberId: TENANT_B.memberId,
          tenantId: TENANT_B.tenantId,
          permissions: [],
        }),
      });
    });

    await page.goto("/select-tenant");
    await page.waitForLoadState("networkidle");

    // Verify both tenants are shown
    await expect(page.getByText("Tenant Alpha")).toBeVisible();
    await expect(page.getByText("Tenant Beta")).toBeVisible();

    // Click Tenant Beta
    await page.getByText("Tenant Beta").click();

    // Verify API was called with correct tenant ID
    await page.waitForURL("**/worklog", { timeout: 10_000 });
    expect(selectedTenantId).toBe(TENANT_B.tenantId);
  });

  test("shows organization name for tenants with organizations", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "FULLY_ASSIGNED", [TENANT_A, TENANT_B]);

    await page.goto("/select-tenant");
    await page.waitForLoadState("networkidle");

    // Both tenants have organization names
    await expect(page.getByText("Org Alpha")).toBeVisible();
    await expect(page.getByText("Org Beta")).toBeVisible();
  });

  test("shows 'No organization' for tenants without organization", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "FULLY_ASSIGNED", [
      TENANT_A,
      { ...TENANT_B, organizationId: null, organizationName: null },
    ]);

    await page.goto("/select-tenant");
    await page.waitForLoadState("networkidle");

    await expect(page.getByText("No Organization")).toBeVisible();
  });
});

// ============================================================
// Test Suite: Waiting Page Behavior
// ============================================================

test.describe("Tenant UI: Waiting page", () => {
  test.describe.configure({ timeout: 30_000 });

  test.beforeEach(({ browserName }) => {
    test.skip(browserName !== "chromium" && !process.env.CI, "Skipping non-Chromium browsers locally");
  });

  test("shows checking status indicator", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "UNAFFILIATED");

    await page.goto("/waiting");
    await page.waitForLoadState("networkidle");

    await expect(page.getByText("Checking status...")).toBeVisible();
  });

  test("logout button navigates to login page", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "UNAFFILIATED");

    // Mock logout endpoint
    await page.route("**/api/v1/auth/logout", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });

    await page.goto("/waiting");
    await page.waitForLoadState("networkidle");

    await page.getByRole("button", { name: "Logout" }).click();

    await page.waitForURL("**/login", { timeout: 10_000 });
  });
});

// ============================================================
// Test Suite: AuthGuard Protected Routes
// ============================================================

test.describe("Tenant UI: AuthGuard protects routes", () => {
  test.describe.configure({ timeout: 30_000 });

  test.beforeEach(({ browserName }) => {
    test.skip(browserName !== "chromium" && !process.env.CI, "Skipping non-Chromium browsers locally");
  });

  test("UNAFFILIATED user accessing /worklog is redirected to /waiting", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "UNAFFILIATED");

    await page.goto("/worklog");

    await page.waitForURL("**/waiting", { timeout: 10_000 });
  });

  test("AFFILIATED_NO_ORG user accessing /worklog is redirected to /pending-organization", async ({ page }) => {
    await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
    await mockGlobalApis(page);
    await mockUserStatus(page, "AFFILIATED_NO_ORG", [{ ...TENANT_A, organizationId: null, organizationName: null }]);

    await page.goto("/worklog");

    await page.waitForURL("**/pending-organization", { timeout: 10_000 });
  });
});
