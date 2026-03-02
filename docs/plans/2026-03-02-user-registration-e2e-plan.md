# User Registration E2E Test Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add E2E test covering user signup → email verification → tenant assignment → org assignment → worklog access → multi-tenant selection (Issue #51).

**Architecture:** Single serial Playwright test file using Mailpit for email interception. User-side operations are UI-driven; admin-side operations use backend API via `page.request`. Follows existing `tenant-onboarding.spec.ts` patterns.

**Tech Stack:** Playwright, Mailpit (Docker), TypeScript

---

## Task 1: Add Mailpit to docker-compose.dev.yml

**Files:**
- Modify: `infra/docker/docker-compose.dev.yml`

**Step 1: Add Mailpit service**

Add the `mailpit` service to `docker-compose.dev.yml`:

```yaml
services:
  postgres:
    # ... existing ...

  redis:
    # ... existing ...

  mailpit:
    image: axllent/mailpit:latest
    container_name: miometry-mailpit
    ports:
      - "8025:8025"
      - "1025:1025"
    environment:
      MP_SMTP_AUTH_ACCEPT_ANY: 1
      MP_SMTP_AUTH_ALLOW_INSECURE: 1
```

**Step 2: Verify Mailpit starts**

Start Mailpit and verify its REST API responds with an empty messages list.

**Step 3: Commit**

```
feat: add Mailpit to dev docker-compose for E2E email testing
```

---

## Task 2: Create Mailpit helper for Playwright

**Files:**
- Create: `frontend/tests/e2e/helpers/mailpit.ts`

**Step 1: Write the Mailpit helper module**

```typescript
/**
 * Mailpit REST API helper for E2E tests.
 * Mailpit runs on localhost:8025 (docker-compose.dev.yml).
 * Backend sends SMTP to localhost:1025 (application.yaml default).
 */

const MAILPIT_API = process.env.MAILPIT_API_URL || "http://localhost:8025/api/v1";

interface MailpitMessage {
  ID: string;
  From: { Address: string; Name: string };
  To: Array<{ Address: string; Name: string }>;
  Subject: string;
  Snippet: string;
  Created: string;
}

interface MailpitMessageDetail extends MailpitMessage {
  HTML: string;
  Text: string;
}

interface MailpitSearchResponse {
  total: number;
  messages: MailpitMessage[];
}

/**
 * Search for emails sent to a specific address.
 * Retries up to maxRetries times with intervalMs delay to handle SMTP delivery latency.
 */
export async function waitForEmail(
  to: string,
  options: { maxRetries?: number; intervalMs?: number; subject?: string } = {},
): Promise<MailpitMessage> {
  const { maxRetries = 10, intervalMs = 1000, subject } = options;

  for (let i = 0; i < maxRetries; i++) {
    const query = subject ? `to:${to} subject:"${subject}"` : `to:${to}`;
    const response = await fetch(`${MAILPIT_API}/search?query=${encodeURIComponent(query)}`);
    if (!response.ok) {
      throw new Error(`Mailpit search failed: ${response.status} ${response.statusText}`);
    }
    const data: MailpitSearchResponse = await response.json();
    if (data.total > 0) {
      return data.messages[0];
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs));
  }
  throw new Error(`No email found for ${to} after ${maxRetries} retries`);
}

/**
 * Get the full message detail (including HTML body) by message ID.
 */
export async function getMessageDetail(messageId: string): Promise<MailpitMessageDetail> {
  const response = await fetch(`${MAILPIT_API}/message/${messageId}`);
  if (!response.ok) {
    throw new Error(`Mailpit get message failed: ${response.status}`);
  }
  return response.json();
}

/**
 * Extract the first href matching a pattern from an HTML email body.
 * Used to extract verification links like /verify-email?token=xxx.
 */
export function extractLinkFromHtml(html: string, pathPattern: RegExp): string | null {
  const hrefRegex = /href="([^"]+)"/g;
  let match;
  while ((match = hrefRegex.exec(html)) !== null) {
    if (pathPattern.test(match[1])) {
      return match[1];
    }
  }
  return null;
}

/**
 * Delete all messages in Mailpit. Call in test setup to ensure clean state.
 */
export async function clearMailbox(): Promise<void> {
  await fetch(`${MAILPIT_API}/messages`, { method: "DELETE" });
}
```

**Step 2: Commit**

```
feat: add Mailpit E2E helper for email interception
```

---

## Task 3: Write the E2E test — Steps 1-3 (Signup, Verify, Waiting)

**Files:**
- Create: `frontend/tests/e2e/scenarios/user-registration-flow.spec.ts`

**Step 1: Write the test file with Steps 1-3**

```typescript
import { expect, type Page, test } from "@playwright/test";
import { clearMailbox, extractLinkFromHtml, getMessageDetail, waitForEmail } from "../helpers/mailpit";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// --- Auth helper (same pattern as tenant-onboarding.spec.ts) ---

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

// --- Seed data references ---

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

// --- Shared state across serial tests ---

test.describe.serial("User Registration -> Tenant Assign -> Worklog Flow", () => {
  test.describe.configure({ timeout: 60_000 });

  test.beforeEach(({ browserName }) => {
    test.skip(browserName !== "chromium" && !process.env.CI, "Skipping non-Chromium browsers locally");
  });

  const runId = Date.now().toString().slice(-6);

  const newUser = {
    email: `e2e-reg-${runId}@test.example.com`,
    password: "TestPassword1!",
    name: `E2E User ${runId}`,
  };

  let userId: string;
  let memberId: string;
  let acmeMemberId: string;

  // Step 1: New user signs up
  test("Step 1: New user signs up via /signup form", async ({ page }) => {
    await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);
    await clearMailbox();

    await page.goto("/signup");
    await page.waitForLoadState("networkidle");

    await page.fill("#email", newUser.email);
    await page.fill("#password", newUser.password);
    await page.fill("#passwordConfirm", newUser.password);

    await page.getByRole("button", { name: "Sign Up" }).click();

    await page.waitForURL("**/signup/confirm", { timeout: 15_000 });
    await expect(page.getByRole("heading", { name: "Registration Complete" })).toBeVisible();
    await expect(page.getByText("A confirmation email has been sent")).toBeVisible();
  });

  // Step 2: Email verification via Mailpit
  test("Step 2: User verifies email via link in Mailpit", async ({ page }) => {
    await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

    const email = await waitForEmail(newUser.email, { maxRetries: 15, intervalMs: 2000 });
    expect(email.Subject).toBeTruthy();

    const detail = await getMessageDetail(email.ID);
    const verifyLink = extractLinkFromHtml(detail.HTML, /verify-email\?token=/);
    expect(verifyLink).toBeTruthy();

    const url = new URL(verifyLink!);
    const verifyPath = url.pathname + url.search;

    await page.goto(verifyPath);
    await page.waitForLoadState("networkidle");

    await expect(page.getByText("Your email has been verified")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("link", { name: "Go to Login" })).toBeVisible();
  });

  // Step 3: Login -> Waiting screen (UNAFFILIATED)
  test("Step 3: User logs in and sees waiting screen", async ({ page }) => {
    await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

    await page.goto("/login");
    await page.waitForLoadState("networkidle");

    await page.fill("#email", newUser.email);
    await page.fill("#password", newUser.password);
    await page.getByRole("button", { name: "Login" }).click();

    await page.waitForURL("**/waiting", { timeout: 15_000 });

    await expect(page.getByRole("heading", { name: "Waiting for Tenant Assignment" })).toBeVisible();
    await expect(page.getByText("An administrator needs to add you to a tenant")).toBeVisible();
    await expect(page.getByRole("button", { name: "Log Out" })).toBeVisible();

    const authData = await page.evaluate(() => {
      const data = window.sessionStorage.getItem("miometry_auth_user");
      return data ? JSON.parse(data) : null;
    });
    expect(authData).toBeTruthy();
    userId = authData.id;
  });
});
```

**Step 2: Run Steps 1-3**

Requires Mailpit running (`docker compose up -d mailpit`) and backend with dev profile.

Expected: 3 tests pass.

**Step 3: Commit**

```
test(e2e): add user registration flow steps 1-3 (signup, verify, waiting)
```

---

## Task 4: Add Steps 4-5 (Tenant Assignment, Organization Assignment)

**Files:**
- Modify: `frontend/tests/e2e/scenarios/user-registration-flow.spec.ts`

**Step 1: Add Step 4 inside the serial describe block**

```typescript
  // Step 4: Admin assigns user to Miometry tenant (API)
  test("Step 4: Admin assigns user to tenant -> user sees pending-organization", async ({ page }) => {
    await loginAs(page, ADMIN.email, ADMIN.password);

    const assignResponse = await page.request.post(`${API_BASE_URL}/api/v1/admin/members/assign-tenant`, {
      data: {
        userId: userId,
        displayName: newUser.name,
      },
    });
    expect(assignResponse.ok()).toBe(true);

    await loginAs(page, newUser.email, newUser.password);
    await page.goto("/");

    await page.waitForURL("**/pending-organization", { timeout: 15_000 });

    await expect(page.getByRole("heading", { name: "Waiting for Organization Assignment" })).toBeVisible();
    await expect(page.getByText("You have been added to a tenant")).toBeVisible();

    const loginResponse = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, {
      data: { email: newUser.email, password: newUser.password, rememberMe: false },
    });
    const loginBody = await loginResponse.json();
    memberId = loginBody.memberships[0].memberId;
    expect(memberId).toBeTruthy();
  });
```

**Step 2: Add Step 5**

```typescript
  // Step 5: Admin assigns organization -> user sees worklog
  test("Step 5: Admin assigns organization -> user redirected to /worklog", async ({ page }) => {
    await loginAs(page, ADMIN.email, ADMIN.password);

    const transferResponse = await page.request.put(
      `${API_BASE_URL}/api/v1/admin/members/${memberId}/organization`,
      { data: { organizationId: ENGINEERING_ORG.id } },
    );
    expect(transferResponse.ok()).toBe(true);

    await loginAs(page, newUser.email, newUser.password);
    await page.goto("/");

    await page.waitForURL("**/worklog", { timeout: 15_000 });
  });
```

**Step 3: Run Steps 1-5, commit**

```
test(e2e): add steps 4-5 (tenant assignment, org assignment)
```

---

## Task 5: Add Step 6 (Worklog Access Verification)

**Files:**
- Modify: `frontend/tests/e2e/scenarios/user-registration-flow.spec.ts`

**Step 1: Add Step 6**

```typescript
  // Step 6: Verify worklog functionality is accessible
  test("Step 6: User can access worklog calendar and entry form", async ({ page }) => {
    await loginAs(page, newUser.email, newUser.password);
    await page.goto("/worklog");
    await page.waitForLoadState("networkidle");

    const calendarDays = page.locator("button.calendar-day");
    await expect(calendarDays.first()).toBeVisible({ timeout: 15_000 });

    // Click a past weekday date to verify entry form opens
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const MONTH_NAMES = [
      "January", "February", "March", "April", "May", "June",
      "July", "August", "September", "October", "November", "December",
    ];

    let clickedDate = false;
    const count = await calendarDays.count();
    for (let i = 0; i < count && !clickedDate; i++) {
      const ariaLabel = await calendarDays.nth(i).getAttribute("aria-label");
      if (!ariaLabel) continue;

      const match = ariaLabel.match(/^(\w+)\s+(\d+),\s+(\d+)$/);
      if (!match) continue;

      const monthIdx = MONTH_NAMES.indexOf(match[1]);
      if (monthIdx === -1) continue;

      const dateObj = new Date(Number(match[3]), monthIdx, Number(match[2]));
      if (dateObj >= today) continue;
      if (dateObj.getDay() === 0 || dateObj.getDay() === 6) continue;

      await calendarDays.nth(i).click();
      await page.waitForLoadState("networkidle");
      clickedDate = true;
    }

    expect(clickedDate).toBe(true);

    const dialog = page.locator('[role="dialog"]');
    await expect(dialog).toBeVisible({ timeout: 10_000 });

    const projectCombobox = dialog.locator('[role="combobox"]').first();
    await expect(projectCombobox).toBeVisible({ timeout: 5_000 });
  });
```

**Step 2: Run, commit**

```
test(e2e): add step 6 (worklog access verification)
```

---

## Task 6: Verify ACME admin credentials in seed data

**Files:** None (research only — must complete before Task 7)

**Step 1:** Search `data-dev.sql` for Jack ACME Admin's email and password to confirm they exist.

**Step 2:** Verify login works by calling the auth API with Jack's credentials.

---

## Task 7: Add Steps 7-8 (Multi-Tenant Setup, Tenant Selection)

**Files:**
- Modify: `frontend/tests/e2e/scenarios/user-registration-flow.spec.ts`

**Step 1: Add Step 7 (multi-tenant setup via ACME admin)**

```typescript
  // Step 7: ACME admin assigns user to second tenant
  test("Step 7: ACME admin assigns user to second tenant", async ({ page }) => {
    // Jack is ACME's TENANT_ADMIN in seed data
    await loginAs(page, "jack.acme@miometry.example.com", "Password1");

    const inviteResponse = await page.request.post(`${API_BASE_URL}/api/v1/admin/members`, {
      data: {
        email: newUser.email,
        displayName: newUser.name,
        organizationId: ACME_SALES_ORG.id,
      },
    });
    expect(inviteResponse.ok()).toBe(true);
    const inviteBody = await inviteResponse.json();
    acmeMemberId = inviteBody.id;
    expect(inviteBody.isExistingUser).toBe(true);
  });
```

**Step 2: Add Step 8 (multi-tenant login and selection)**

```typescript
  // Step 8: Multi-tenant user logs in and selects tenant
  test("Step 8: User with multiple tenants sees tenant selection screen", async ({ page }) => {
    await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

    await page.goto("/login");
    await page.waitForLoadState("networkidle");

    await page.fill("#email", newUser.email);
    await page.fill("#password", newUser.password);
    await page.getByRole("button", { name: "Login" }).click();

    await page.waitForURL("**/select-tenant", { timeout: 15_000 });

    await expect(page.getByRole("heading", { name: "Select a Tenant" })).toBeVisible();
    await expect(page.getByText(MIOMETRY_TENANT.name)).toBeVisible();
    await expect(page.getByText(ACME_TENANT.name)).toBeVisible();

    await page.getByText(MIOMETRY_TENANT.name).click();

    await page.waitForURL("**/worklog", { timeout: 15_000 });
  });
```

**Step 3: Run all 8 steps, commit**

```
test(e2e): add steps 7-8 (multi-tenant setup and tenant selection)
```

---

## Task 8: Run full verification suite

**Files:** None (verification only)

**Step 1:** Run the full E2E test file with `--project=chromium --reporter=list`. All 8 steps should pass.

**Step 2:** Run existing E2E tests to verify no regressions.

**Step 3:** Run lint checks with `npx biome ci` on the new files.

**Step 4:** Final commit if lint fixes needed.

---

## Notes

- **Execution order:** Task 6 (verify ACME admin) must complete before Task 7 (Steps 7-8).
- **Prerequisites:** Mailpit + backend must be running for E2E tests.
- **Dev env startup:** `cd infra/docker && docker compose -f docker-compose.dev.yml up -d` then `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`
- **Key selectors:** See `frontend/messages/en.json` for actual UI text used in assertions.
- **Seed data IDs:** ACME Tenant `550e8400-...-440002`, ACME Sales Org `880e8400-...-440002`.
