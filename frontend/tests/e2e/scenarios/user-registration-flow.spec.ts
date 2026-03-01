import { expect, type Page, test } from "@playwright/test";
import { clearMailbox, extractLinkFromHtml, getMessageDetail, waitForEmail } from "../helpers/mailpit";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// --- Auth Helper ---

interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  memberId?: string;
}

async function loginAs(page: Page, email: string, password: string): Promise<AuthUser> {
  // Force English locale (NEXT_LOCALE cookie = Priority 1 in i18n/request.ts)
  await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

  // Navigate to app origin first — sessionStorage requires a real origin (not about:blank)
  if (page.url() === "about:blank") {
    await page.goto("/");
    await page.waitForLoadState("domcontentloaded");
  }
  // Clear previous session
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

// --- Constants ---

const MIOMETRY_TENANT = {
  id: "550e8400-e29b-41d4-a716-446655440001",
  name: "Miometry Corporation",
};

const ENGINEERING_ORG = {
  id: "880e8400-e29b-41d4-a716-446655440001",
  name: "Engineering Department",
};

const ACME_TENANT = {
  id: "550e8400-e29b-41d4-a716-446655440002",
  name: "ACME Corporation",
};

const ACME_SALES_ORG = {
  id: "880e8400-e29b-41d4-a716-446655440002",
  name: "Sales Department",
};

const ADMIN = {
  email: "sysadmin@miometry.example.com",
  password: "Password1",
};

const MONTH_NAMES = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

// --- Test Suite ---

test.describe
  .serial("User Registration Flow: Signup → Verify → Tenant/Org Assignment → Worklog", () => {
    test.describe.configure({ timeout: 60_000 });

    // Skip non-Chromium browsers locally (WSL2 lacks webkit system deps)
    test.beforeEach(({ browserName }) => {
      test.skip(browserName !== "chromium" && !process.env.CI, "Skipping non-Chromium browsers locally");
    });

    // Unique suffix per test run to avoid DUPLICATE_EMAIL on re-runs
    const runId = Date.now().toString().slice(-6);

    const newUser = {
      email: `e2e-reg-${runId}@test.example.com`,
      password: "TestPassword1!",
      name: `E2E User ${runId}`,
    };

    // Shared state across serial tests
    let userId: string;
    let memberId: string;
    let _acmeMemberId: string;

    // ============================================================
    // Phase 1: Registration & Email Verification (Steps 1-3)
    // ============================================================

    test("Step 1: New user signs up via /signup form", async ({ page }) => {
      // Set English locale
      await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

      // Clear Mailpit mailbox to ensure clean state
      await clearMailbox();

      // Navigate to signup page
      await page.goto("/signup");
      await page.waitForLoadState("networkidle");

      // Fill in the signup form
      await page.fill("#email", newUser.email);
      await page.fill("#password", newUser.password);
      await page.fill("#passwordConfirm", newUser.password);

      // Submit the form
      await page.getByRole("button", { name: "Sign Up" }).click();

      // Verify redirect to /signup/confirm
      await page.waitForURL(/\/signup\/confirm/, { timeout: 15_000 });

      // Verify "Registration Complete" heading
      await expect(page.getByRole("heading", { name: "Registration Complete" })).toBeVisible({ timeout: 10_000 });
    });

    test("Step 2: User verifies email via Mailpit", async ({ page }) => {
      // Set English locale
      await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

      // Query Mailpit for the verification email
      const email = await waitForEmail(newUser.email, { maxRetries: 15, intervalMs: 1000 });
      expect(email).toBeTruthy();

      // Get the full message detail to extract the verification link
      const detail = await getMessageDetail(email.ID);
      const verifyLink = extractLinkFromHtml(detail.HTML, /verify-email\?token=/);
      expect(verifyLink).toBeTruthy();

      // Extract the token from the link (could be full URL or relative path)
      const url = new URL(verifyLink as string, "http://localhost:3000");
      const token = url.searchParams.get("token");
      expect(token).toBeTruthy();

      // Navigate to the verification page
      await page.goto(`/verify-email?token=${token}`);
      await page.waitForLoadState("networkidle");

      // Verify "Your email has been verified" message
      await expect(page.getByText("Your email has been verified")).toBeVisible({ timeout: 15_000 });
    });

    test("Step 3: User logs in and sees waiting screen", async ({ page }) => {
      // Set English locale
      await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

      // Navigate to login page
      await page.goto("/login");
      await page.waitForLoadState("networkidle");

      // Fill in credentials
      await page.fill("#email", newUser.email);
      await page.fill("#password", newUser.password);

      // Click Login button
      await page.getByRole("button", { name: "Login" }).click();

      // Verify redirect to /waiting
      await page.waitForURL(/\/waiting/, { timeout: 15_000 });

      // Verify "Waiting for Tenant Assignment" heading
      await expect(page.getByRole("heading", { name: "Waiting for Tenant Assignment" })).toBeVisible({
        timeout: 10_000,
      });

      // Verify "Log Out" button is visible
      await expect(page.getByRole("button", { name: "Log Out" })).toBeVisible();

      // Capture userId from sessionStorage
      const authUserJson = await page.evaluate(() => window.sessionStorage.getItem("miometry_auth_user"));
      expect(authUserJson).toBeTruthy();
      const authUser = JSON.parse(authUserJson as string);
      userId = authUser.id;
      expect(userId).toBeTruthy();
    });

    // ============================================================
    // Phase 2: Tenant & Organization Assignment (Steps 4-5)
    // ============================================================

    test("Step 4: Admin assigns user to tenant → user sees pending-organization", async ({ page }) => {
      // Login as sysadmin
      await loginAs(page, ADMIN.email, ADMIN.password);

      // Assign the new user to the Miometry tenant via API
      const assignResponse = await page.request.post(`${API_BASE_URL}/api/v1/admin/members/assign-tenant`, {
        data: { userId, displayName: newUser.name },
      });
      expect(assignResponse.ok()).toBe(true);

      // Now login as the new user and verify redirect to /pending-organization
      await loginAs(page, newUser.email, newUser.password);
      await page.goto("/");
      await page.waitForURL(/\/pending-organization/, { timeout: 15_000 });

      // Verify "Waiting for Organization Assignment" heading
      await expect(page.getByRole("heading", { name: "Waiting for Organization Assignment" })).toBeVisible({
        timeout: 10_000,
      });

      // Capture memberId from the login response memberships
      // Re-login via API to get the memberships data
      const loginResponse = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, {
        data: { email: newUser.email, password: newUser.password, rememberMe: false },
      });
      expect(loginResponse.ok()).toBe(true);
      const loginBody = await loginResponse.json();
      expect(loginBody.memberships.length).toBeGreaterThan(0);
      memberId = loginBody.memberships[0].memberId;
      expect(memberId).toBeTruthy();
    });

    test("Step 5: Admin assigns organization → user redirected to /worklog", async ({ page }) => {
      // Login as sysadmin
      await loginAs(page, ADMIN.email, ADMIN.password);

      // Assign the new user to the Engineering Department organization
      const orgResponse = await page.request.put(`${API_BASE_URL}/api/v1/admin/members/${memberId}/organization`, {
        data: { organizationId: ENGINEERING_ORG.id },
      });
      expect(orgResponse.ok()).toBe(true);

      // Login as the new user and verify redirect to /worklog
      await loginAs(page, newUser.email, newUser.password);
      await page.goto("/");
      await page.waitForURL(/\/worklog/, { timeout: 15_000 });
    });

    // ============================================================
    // Phase 3: Worklog Access Verification (Step 6)
    // ============================================================

    test("Step 6: User can access worklog calendar and entry form", async ({ page }) => {
      // Login as the new user
      await loginAs(page, newUser.email, newUser.password);

      // Navigate to worklog
      await page.goto("/worklog");
      await page.waitForLoadState("networkidle");

      // Verify calendar-day buttons are visible
      const dateButtons = page.locator("button.calendar-day");
      await expect(dateButtons.first()).toBeVisible({ timeout: 10_000 });

      // Click a past weekday date (same pattern as tenant-onboarding.spec.ts)
      const today = new Date();
      today.setHours(0, 0, 0, 0);

      const count = await dateButtons.count();
      let clicked = false;
      for (let i = 0; i < count; i++) {
        const ariaLabel = await dateButtons.nth(i).getAttribute("aria-label");
        if (!ariaLabel) continue;

        const match = ariaLabel.match(/^(\w+)\s+(\d+),\s+(\d+)$/);
        if (!match) continue;

        const monthIdx = MONTH_NAMES.indexOf(match[1]);
        if (monthIdx === -1) continue;

        const dateObj = new Date(Number(match[3]), monthIdx, Number(match[2]));
        if (dateObj >= today) continue; // skip today and future
        if (dateObj.getDay() === 0 || dateObj.getDay() === 6) continue; // skip weekends

        // Click the first past weekday
        await page.click(`button[aria-label="${ariaLabel}"]`);
        clicked = true;
        break;
      }
      expect(clicked).toBe(true);

      // Verify dialog appears
      const dialog = page.locator('[role="dialog"]');
      await expect(dialog).toBeVisible({ timeout: 10_000 });

      // Verify project combobox is visible
      const projectCombobox = dialog.locator('[role="combobox"]');
      await expect(projectCombobox.first()).toBeVisible({ timeout: 5_000 });
    });

    // ============================================================
    // Phase 4: Multi-Tenant Setup & Selection (Steps 7-8)
    // ============================================================

    test("Step 7: ACME admin assigns user to second tenant", async ({ page }) => {
      // Jack is ACME's TENANT_ADMIN in seed data (jack.admin@acme.example.com)
      await loginAs(page, "jack.admin@acme.example.com", "Password1");

      // Invite the new user as a member of ACME with Sales org assignment
      const inviteResponse = await page.request.post(`${API_BASE_URL}/api/v1/admin/members`, {
        data: {
          email: newUser.email,
          displayName: newUser.name,
          organizationId: ACME_SALES_ORG.id,
        },
      });
      expect(inviteResponse.ok()).toBe(true);
      const inviteBody = await inviteResponse.json();
      _acmeMemberId = inviteBody.id;
      expect(inviteBody.isExistingUser).toBe(true);
    });

    test("Step 8: User with multiple tenants sees tenant selection screen", async ({ page }) => {
      // Set English locale
      await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

      // Login via UI to test the full login → redirect flow
      await page.goto("/login");
      await page.waitForLoadState("networkidle");

      await page.fill("#email", newUser.email);
      await page.fill("#password", newUser.password);
      await page.getByRole("button", { name: "Login" }).click();

      // Should redirect to /select-tenant (FULLY_ASSIGNED, multiple tenants)
      await page.waitForURL(/\/select-tenant/, { timeout: 15_000 });

      // Verify "Select a Tenant" heading
      await expect(page.getByRole("heading", { name: "Select a Tenant" })).toBeVisible({ timeout: 10_000 });

      // Verify both tenants are listed
      await expect(page.getByText(MIOMETRY_TENANT.name)).toBeVisible();
      await expect(page.getByText(ACME_TENANT.name)).toBeVisible();

      // Select Miometry tenant → should redirect to /worklog
      await page.getByText(MIOMETRY_TENANT.name).click();
      await page.waitForURL(/\/worklog/, { timeout: 15_000 });
    });
  });
