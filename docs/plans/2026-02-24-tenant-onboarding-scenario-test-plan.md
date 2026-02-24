# Tenant Onboarding Scenario Test Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create an E2E scenario test (Playwright + real backend) that validates the complete workflow from new tenant creation through first monthly approval cycle.

**Architecture:** Single Playwright spec file using `test.describe.serial()` with 15 sequential test steps. No API mocking — all requests hit the real backend. A `loginAs()` helper handles user switching between steps. Closure variables share state across steps.

**Tech Stack:** Playwright, TypeScript, real Spring Boot backend, PostgreSQL (Docker Compose)

**Design doc:** `docs/plans/2026-02-24-tenant-onboarding-scenario-test-design.md`

---

### Task 1: Create test scaffold and auth helper

**Files:**
- Create: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Step 1: Create the scenarios directory and test file with scaffold**

Create the file with imports, shared state variables, the `loginAs` helper, and the serial describe block with all 15 test stubs (using `test.fixme`).

```typescript
import { test, expect, type Page } from "@playwright/test";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// --- Auth Helper ---

interface AuthUser {
  id: string;
  email: string;
  displayName: string;
}

async function loginAs(page: Page, email: string, password: string): Promise<AuthUser> {
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
  };

  await page.addInitScript((user) => {
    window.sessionStorage.setItem("miometry_auth_user", JSON.stringify(user));
  }, authUser);

  return authUser;
}

// --- Shared state across serial tests ---

interface PersonaCredentials {
  email: string;
  password: string;
  id?: string;
  displayName?: string;
}

test.describe.serial("Tenant Onboarding: Full Cycle", () => {
  let tenantId: string;
  let tenantCode: string;
  let tenantName: string;

  let fiscalYearPatternId: string;
  let monthlyPeriodPatternId: string;

  let orgAId: string;
  let orgBId: string;

  const tenantOwner1: PersonaCredentials = {
    email: "tenant-owner-1@scenario-test.example.com",
    password: "",
    displayName: "Tenant Owner 1",
  };
  const tenantOwner2: PersonaCredentials = {
    email: "tenant-owner-2@scenario-test.example.com",
    password: "",
    displayName: "Tenant Owner 2",
  };

  const orgAAdmin1: PersonaCredentials = {
    email: "org-a-admin-1@scenario-test.example.com",
    password: "",
    displayName: "Org A Admin 1",
  };
  const orgAAdmin2: PersonaCredentials = {
    email: "org-a-admin-2@scenario-test.example.com",
    password: "",
    displayName: "Org A Admin 2",
  };
  const orgBAdmin1: PersonaCredentials = {
    email: "org-b-admin-1@scenario-test.example.com",
    password: "",
    displayName: "Org B Admin 1",
  };
  const orgBAdmin2: PersonaCredentials = {
    email: "org-b-admin-2@scenario-test.example.com",
    password: "",
    displayName: "Org B Admin 2",
  };

  const orgAMembers: PersonaCredentials[] = [
    { email: "org-a-member-1@scenario-test.example.com", password: "", displayName: "Org A Member 1" },
    { email: "org-a-member-2@scenario-test.example.com", password: "", displayName: "Org A Member 2" },
    { email: "org-a-member-3@scenario-test.example.com", password: "", displayName: "Org A Member 3" },
  ];
  const orgBMembers: PersonaCredentials[] = [
    { email: "org-b-member-1@scenario-test.example.com", password: "", displayName: "Org B Member 1" },
    { email: "org-b-member-2@scenario-test.example.com", password: "", displayName: "Org B Member 2" },
    { email: "org-b-member-3@scenario-test.example.com", password: "", displayName: "Org B Member 3" },
  ];

  // Placeholder tests — implement one at a time
  test.fixme("Step 1: sys-admin creates tenant", async ({ page }) => {});
  test.fixme("Step 2: sys-admin configures fiscal patterns", async ({ page }) => {});
  test.fixme("Step 3: sys-admin invites tenant owners", async ({ page }) => {});
  test.fixme("Step 4: tenant-owner-1 creates Org A", async ({ page }) => {});
  test.fixme("Step 5: tenant-owner-2 creates Org B", async ({ page }) => {});
  test.fixme("Step 6: tenant-owner-1 invites Org A admins", async ({ page }) => {});
  test.fixme("Step 7: tenant-owner-2 invites Org B admins", async ({ page }) => {});
  test.fixme("Step 8: org-a-admin-1 invites members + assigns manager", async ({ page }) => {});
  test.fixme("Step 9: org-b-admin-1 invites members + assigns manager", async ({ page }) => {});
  test.fixme("Step 10: Org A member enters work log", async ({ page }) => {});
  test.fixme("Step 11: Org B manager proxy-enters work log", async ({ page }) => {});
  test.fixme("Step 12: Org A member submits for approval", async ({ page }) => {});
  test.fixme("Step 13: Org A manager approves", async ({ page }) => {});
  test.fixme("Step 14: Org B manager proxy-submits for approval", async ({ page }) => {});
  test.fixme("Step 15: Org B different admin approves", async ({ page }) => {});
});
```

**Step 2: Run to verify the scaffold loads**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts --reporter=list`

Expected: All 15 tests show as "fixme" / skipped. No errors.

**Step 3: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: scaffold tenant onboarding scenario test with 15 serial steps"
```

---

### Task 2: Step 1 — sys-admin creates tenant

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- sys-admin is a pre-existing ADMIN-role user from dev seed data.
- Default dev credentials: `bob.engineer@miometry.example.com` / `Password1`.
- If Bob doesn't have ADMIN role/permissions for `/admin/tenants`, check `data-dev.sql` for which user has the ADMIN role and adjust accordingly.
- Use a unique tenant code with timestamp to avoid conflicts on re-runs: `SCENARIO-{timestamp}`.

**Step 1: Implement the test**

Replace the `test.fixme("Step 1: ...")` with:

```typescript
test("Step 1: sys-admin creates tenant", async ({ page }) => {
  // Login as sys-admin
  await loginAs(page, "bob.engineer@miometry.example.com", "Password1");

  // Navigate to tenant management
  await page.goto("/admin/tenants");
  await page.waitForLoadState("networkidle");

  // Click create tenant button
  await page.click('button:has-text("テナント作成")');

  // Fill in tenant form
  tenantCode = `SCEN${Date.now().toString().slice(-6)}`;
  tenantName = `Scenario Test Tenant ${tenantCode}`;

  await page.fill("#tenant-code", tenantCode);
  await page.fill("#tenant-name", tenantName);

  // Submit
  await page.click('button:has-text("作成")');

  // Verify: tenant appears in list
  await expect(page.locator(`text=${tenantCode}`)).toBeVisible({ timeout: 10_000 });
  await expect(page.locator(`text=${tenantName}`)).toBeVisible();

  // Capture tenantId from the page or API response
  // The tenant list table should show the new tenant. We may need to extract the ID
  // from the row or from a subsequent API call.
  const tenantRow = page.locator(`tr:has-text("${tenantCode}")`);
  await expect(tenantRow).toBeVisible();

  // Verify status badge shows ACTIVE
  await expect(tenantRow.locator("text=有効")).toBeVisible();
});
```

**Step 2: Run this test**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts -g "Step 1" --reporter=list`

Expected: PASS — tenant created and visible in list.

**Troubleshooting:**
- If login fails (401): Check that backend is running with dev profile and `data-dev.sql` is loaded.
- If `/admin/tenants` returns 403: Bob may not have ADMIN permissions. Check the user's role in `data-dev.sql` and find the ADMIN user instead.
- If "テナント作成" button not found: Check the exact button text in the UI (may differ from code reading).

**Step 3: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: step 1 — sys-admin creates tenant in scenario test"
```

---

### Task 3: Step 2 — sys-admin configures fiscal patterns

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- Fiscal year pattern: January 1st start (calendar year) — form fields: `fy-pattern-name`, `fy-pattern-start-month`, `fy-pattern-start-day`
- Monthly period pattern: 1st day start (month-end closing) — form fields: `mp-pattern-name`, `mp-pattern-start-day`
- These patterns are created per-tenant via: `POST /api/v1/tenants/{tenantId}/fiscal-year-patterns` and `POST /api/v1/tenants/{tenantId}/monthly-period-patterns`
- The GUI flow for creating patterns is via the organization page ("+ 新規作成" buttons in the pattern section), but we need an organization first.
- **Pragmatic approach**: Since patterns need to exist before organization assignment, and the GUI creates patterns inline on the org page, we need to either:
  - (a) Create an org first, then use the inline pattern creation UI
  - (b) Use direct API calls to create patterns (still hitting real backend, not mocking)
- We'll use approach (b) for the patterns since it's a tenant-level bootstrap operation, then assign them via GUI when creating organizations in Steps 4-5.

**Step 1: Implement the test**

Replace `test.fixme("Step 2: ...")` with:

```typescript
test("Step 2: sys-admin configures fiscal patterns", async ({ page }) => {
  // We need the tenantId. Since the GUI may not expose it directly,
  // use API to look up the tenant we just created.
  const tenantsResponse = await page.request.get(
    `${API_BASE_URL}/api/v1/admin/tenants?status=ACTIVE&page=0&size=50`,
  );
  expect(tenantsResponse.ok()).toBe(true);
  const tenantsBody = await tenantsResponse.json();
  const ourTenant = tenantsBody.content.find(
    (t: { code: string }) => t.code === tenantCode,
  );
  expect(ourTenant).toBeDefined();
  tenantId = ourTenant.id;

  // Create fiscal year pattern via API (January 1st start = calendar year)
  const fyResponse = await page.request.post(
    `${API_BASE_URL}/api/v1/tenants/${tenantId}/fiscal-year-patterns`,
    {
      data: { name: "Calendar Year", startMonth: 1, startDay: 1 },
    },
  );
  expect(fyResponse.ok()).toBe(true);
  const fyBody = await fyResponse.json();
  fiscalYearPatternId = fyBody.id;

  // Create monthly period pattern via API (1st day start = month-end closing)
  const mpResponse = await page.request.post(
    `${API_BASE_URL}/api/v1/tenants/${tenantId}/monthly-period-patterns`,
    {
      data: { name: "Month-End Closing", startDay: 1 },
    },
  );
  expect(mpResponse.ok()).toBe(true);
  const mpBody = await mpResponse.json();
  monthlyPeriodPatternId = mpBody.id;

  // Verify patterns exist via API
  expect(fiscalYearPatternId).toBeTruthy();
  expect(monthlyPeriodPatternId).toBeTruthy();
});
```

**Step 2: Run the first 2 tests**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts -g "Step [12]" --reporter=list`

Expected: Both PASS.

**Step 3: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: step 2 — sys-admin configures fiscal patterns via API"
```

---

### Task 4: Step 3 — sys-admin creates orgs and invites tenant owners

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- The sys-admin needs to create organizations within the NEW tenant. Since the admin panel operates within the user's own tenant context, cross-tenant org creation may need API calls.
- After creating the org, we invite the tenant owner into it, then assign TENANT_ADMIN role.
- The member invite returns a `temporaryPassword` which we store for later login.
- We combine org creation + tenant owner invitation into one step for efficiency.
- **GUI vs API decision**: The admin organizations page (`/admin/organizations`) operates within the current user's tenant. Since sys-admin belongs to a different tenant (MIOMETRY), we need to bootstrap via API. Once tenant owners are created and can log in, all subsequent operations are GUI-based.

**Step 1: Implement the test**

Replace `test.fixme("Step 3: ...")` with:

```typescript
test("Step 3: sys-admin creates orgs and invites tenant owners", async ({ page }) => {
  // Create Org A via API (root org, level 1)
  const orgAResponse = await page.request.post(
    `${API_BASE_URL}/api/v1/admin/organizations`,
    {
      data: {
        tenantId,
        code: "ORG_A",
        name: "Organization Alpha",
        parentId: null,
      },
    },
  );
  expect(orgAResponse.ok()).toBe(true);
  const orgABody = await orgAResponse.json();
  orgAId = orgABody.id;

  // Create Org B via API (root org, level 1)
  const orgBResponse = await page.request.post(
    `${API_BASE_URL}/api/v1/admin/organizations`,
    {
      data: {
        tenantId,
        code: "ORG_B",
        name: "Organization Beta",
        parentId: null,
      },
    },
  );
  expect(orgBResponse.ok()).toBe(true);
  const orgBBody = await orgBResponse.json();
  orgBId = orgBBody.id;

  // Assign fiscal patterns to Org A
  const patternAResponse = await page.request.put(
    `${API_BASE_URL}/api/v1/admin/organizations/${orgAId}/patterns`,
    {
      data: {
        fiscalYearPatternId,
        monthlyPeriodPatternId,
      },
    },
  );
  expect(patternAResponse.status()).toBe(204);

  // Assign fiscal patterns to Org B
  const patternBResponse = await page.request.put(
    `${API_BASE_URL}/api/v1/admin/organizations/${orgBId}/patterns`,
    {
      data: {
        fiscalYearPatternId,
        monthlyPeriodPatternId,
      },
    },
  );
  expect(patternBResponse.status()).toBe(204);

  // Invite tenant-owner-1 into Org A
  const owner1Response = await page.request.post(
    `${API_BASE_URL}/api/v1/admin/members`,
    {
      data: {
        email: tenantOwner1.email,
        displayName: tenantOwner1.displayName,
        organizationId: orgAId,
      },
    },
  );
  expect(owner1Response.ok()).toBe(true);
  const owner1Body = await owner1Response.json();
  tenantOwner1.id = owner1Body.id;
  tenantOwner1.password = owner1Body.temporaryPassword;

  // Assign TENANT_ADMIN role to tenant-owner-1
  const adminRole1Response = await page.request.post(
    `${API_BASE_URL}/api/v1/admin/members/${tenantOwner1.id}/assign-tenant-admin`,
  );
  expect(adminRole1Response.ok()).toBe(true);

  // Invite tenant-owner-2 into Org B
  const owner2Response = await page.request.post(
    `${API_BASE_URL}/api/v1/admin/members`,
    {
      data: {
        email: tenantOwner2.email,
        displayName: tenantOwner2.displayName,
        organizationId: orgBId,
      },
    },
  );
  expect(owner2Response.ok()).toBe(true);
  const owner2Body = await owner2Response.json();
  tenantOwner2.id = owner2Body.id;
  tenantOwner2.password = owner2Body.temporaryPassword;

  // Assign TENANT_ADMIN role to tenant-owner-2
  const adminRole2Response = await page.request.post(
    `${API_BASE_URL}/api/v1/admin/members/${tenantOwner2.id}/assign-tenant-admin`,
  );
  expect(adminRole2Response.ok()).toBe(true);

  // Verify: both tenant owners exist
  expect(tenantOwner1.password).toBeTruthy();
  expect(tenantOwner2.password).toBeTruthy();
});
```

**Step 2: Run the first 3 tests**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts -g "Step [123]" --reporter=list`

Expected: All 3 PASS.

**Troubleshooting:**
- If org creation fails with 400: The `CreateOrganizationRequest` may not accept `tenantId` in the body. Check the actual request DTO. You may need to set it via a different mechanism (header, session context).
- If member invite fails: Check the `InviteMemberRequest` required fields. `organizationId` may be required.
- If assign-tenant-admin fails with 404: Check the exact endpoint path.

**Step 3: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: step 3 — sys-admin bootstraps orgs and tenant owners via API"
```

---

### Task 5: Steps 4-5 — tenant owners log in and verify their organizations

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- From this step onward, all operations are GUI-based.
- Tenant owners log in with their temporary passwords.
- They navigate to `/admin/organizations` and verify their org exists with patterns assigned.
- The original design had tenant owners "creating" the orgs, but since orgs were bootstrapped in Step 3, these steps verify the org setup and may adjust patterns via GUI.

**Step 1: Implement Steps 4 and 5**

Replace `test.fixme("Step 4: ...")` and `test.fixme("Step 5: ...")` with:

```typescript
test("Step 4: tenant-owner-1 logs in and verifies Org A", async ({ page }) => {
  await loginAs(page, tenantOwner1.email, tenantOwner1.password);

  // Navigate to organization management
  await page.goto("/admin/organizations");
  await page.waitForLoadState("networkidle");

  // Verify Org A is visible
  await expect(page.locator("text=ORG_A")).toBeVisible({ timeout: 10_000 });
  await expect(page.locator("text=Organization Alpha")).toBeVisible();

  // Click on Org A to select it
  await page.click("text=Organization Alpha");

  // Verify pattern assignment is shown
  await expect(page.locator("#fiscal-year-pattern")).toBeVisible();
  await expect(page.locator("#monthly-period-pattern")).toBeVisible();
});

test("Step 5: tenant-owner-2 logs in and verifies Org B", async ({ page }) => {
  await loginAs(page, tenantOwner2.email, tenantOwner2.password);

  await page.goto("/admin/organizations");
  await page.waitForLoadState("networkidle");

  // Verify Org B is visible
  await expect(page.locator("text=ORG_B")).toBeVisible({ timeout: 10_000 });
  await expect(page.locator("text=Organization Beta")).toBeVisible();

  // Click on Org B to select it
  await page.click("text=Organization Beta");

  // Verify pattern assignment is shown
  await expect(page.locator("#fiscal-year-pattern")).toBeVisible();
  await expect(page.locator("#monthly-period-pattern")).toBeVisible();
});
```

**Step 2: Run tests 1-5**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts -g "Step [1-5]" --reporter=list`

Expected: All 5 PASS.

**Troubleshooting:**
- If login fails for tenant owner: The temporary password format may need URL decoding or the account may require email verification first. Check backend logs.
- If `/admin/organizations` shows empty: The tenant context may not be set correctly for the new user. Check if `AuthProvider` resolves the tenant from the user's membership.

**Step 3: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: steps 4-5 — tenant owners verify org setup via GUI"
```

---

### Task 6: Steps 6-7 — tenant owners invite org admins via GUI

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- Tenant owners use the organization detail page to create new members.
- The `MemberManagerForm.tsx` in "createMember" mode has fields: `new-member-email`, `new-member-name`, `new-member-manager`.
- After creation, the response includes the temporary password.
- We need to capture the temporary password. Since this is a GUI operation, we intercept the API response (using `page.waitForResponse`) to extract the password.

**Step 1: Create a helper for inviting members via GUI**

Add above the `test.describe.serial` block:

```typescript
async function inviteMemberViaOrgPage(
  page: Page,
  orgName: string,
  member: PersonaCredentials,
): Promise<void> {
  // Navigate to organizations
  await page.goto("/admin/organizations");
  await page.waitForLoadState("networkidle");

  // Select the organization
  await page.click(`text=${orgName}`);
  await page.waitForLoadState("networkidle");

  // Find and click the "create member" button in the member section
  // The exact button text may vary — look for the action that opens createMember mode
  await page.click('button:has-text("メンバー作成")');

  // Fill in the form
  await page.fill("#new-member-email", member.email);
  await page.fill("#new-member-name", member.displayName!);

  // Submit and capture the response with temporary password
  const responsePromise = page.waitForResponse(
    (resp) => resp.url().includes("/api/v1/admin/members") && resp.request().method() === "POST",
  );

  await page.click('button:has-text("作成")');

  const response = await responsePromise;
  expect(response.ok()).toBe(true);
  const body = await response.json();
  member.id = body.id;
  member.password = body.temporaryPassword;
}
```

**Step 2: Implement Steps 6 and 7**

Replace `test.fixme("Step 6: ...")` and `test.fixme("Step 7: ...")`:

```typescript
test("Step 6: tenant-owner-1 invites Org A admins", async ({ page }) => {
  await loginAs(page, tenantOwner1.email, tenantOwner1.password);

  // Invite org-a-admin-1
  await inviteMemberViaOrgPage(page, "Organization Alpha", orgAAdmin1);
  expect(orgAAdmin1.password).toBeTruthy();

  // Invite org-a-admin-2
  await inviteMemberViaOrgPage(page, "Organization Alpha", orgAAdmin2);
  expect(orgAAdmin2.password).toBeTruthy();

  // Verify both admins appear in the member list
  await page.goto("/admin/organizations");
  await page.waitForLoadState("networkidle");
  await page.click("text=Organization Alpha");
  await page.waitForLoadState("networkidle");

  await expect(page.locator(`text=${orgAAdmin1.email}`)).toBeVisible({ timeout: 10_000 });
  await expect(page.locator(`text=${orgAAdmin2.email}`)).toBeVisible();
});

test("Step 7: tenant-owner-2 invites Org B admins", async ({ page }) => {
  await loginAs(page, tenantOwner2.email, tenantOwner2.password);

  await inviteMemberViaOrgPage(page, "Organization Beta", orgBAdmin1);
  expect(orgBAdmin1.password).toBeTruthy();

  await inviteMemberViaOrgPage(page, "Organization Beta", orgBAdmin2);
  expect(orgBAdmin2.password).toBeTruthy();

  // Verify both admins appear in the member list
  await page.goto("/admin/organizations");
  await page.waitForLoadState("networkidle");
  await page.click("text=Organization Beta");
  await page.waitForLoadState("networkidle");

  await expect(page.locator(`text=${orgBAdmin1.email}`)).toBeVisible({ timeout: 10_000 });
  await expect(page.locator(`text=${orgBAdmin2.email}`)).toBeVisible();
});
```

**Step 3: Run tests 1-7**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts -g "Step [1-7]" --reporter=list`

Expected: All 7 PASS.

**Troubleshooting:**
- If "メンバー作成" button not found: The button text may differ. Use `page.pause()` in headed mode (`--headed`) to inspect the actual UI. Try alternatives: "メンバー追加", "+ メンバー", etc.
- If the member form fields are not found: The `id` attributes may be `member-email` / `member-name` instead of `new-member-email` / `new-member-name`. Check the rendered DOM.

**Step 4: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: steps 6-7 — tenant owners invite org admins via GUI"
```

---

### Task 7: Steps 8-9 — org admins invite members and assign managers

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- Org admins (MODERATOR role) invite 3 members each.
- After invitation, assign the org admin as manager for all 3 members.
- Manager assignment uses `MemberManagerForm` in "assignManager" mode with a `manager-select` dropdown.
- We reuse the `inviteMemberViaOrgPage` helper from Task 6.

**Step 1: Create a helper for assigning managers via GUI**

Add below the `inviteMemberViaOrgPage` helper:

```typescript
async function assignManagerViaOrgPage(
  page: Page,
  orgName: string,
  memberEmail: string,
  managerDisplayName: string,
): Promise<void> {
  await page.goto("/admin/organizations");
  await page.waitForLoadState("networkidle");
  await page.click(`text=${orgName}`);
  await page.waitForLoadState("networkidle");

  // Find the member row and click "マネージャー割当"
  const memberRow = page.locator(`tr:has-text("${memberEmail}")`);
  await memberRow.locator('button:has-text("マネージャー割当")').click();

  // Select the manager from the dropdown
  await page.selectOption("#manager-select", { label: new RegExp(managerDisplayName) });

  // Click assign button
  await page.click('button:has-text("割り当て")');

  // Wait for success
  await page.waitForLoadState("networkidle");
}
```

**Step 2: Implement Steps 8 and 9**

Replace `test.fixme("Step 8: ...")` and `test.fixme("Step 9: ...")`:

```typescript
test("Step 8: org-a-admin-1 invites members + assigns manager", async ({ page }) => {
  await loginAs(page, orgAAdmin1.email, orgAAdmin1.password);

  // Invite 3 members
  for (const member of orgAMembers) {
    await inviteMemberViaOrgPage(page, "Organization Alpha", member);
    expect(member.password).toBeTruthy();
  }

  // Assign org-a-admin-1 as manager for all 3 members
  for (const member of orgAMembers) {
    await assignManagerViaOrgPage(
      page,
      "Organization Alpha",
      member.email,
      orgAAdmin1.displayName!,
    );
  }

  // Verify all members are listed with manager assigned
  await page.goto("/admin/organizations");
  await page.waitForLoadState("networkidle");
  await page.click("text=Organization Alpha");
  await page.waitForLoadState("networkidle");

  for (const member of orgAMembers) {
    const row = page.locator(`tr:has-text("${member.email}")`);
    await expect(row).toBeVisible({ timeout: 10_000 });
    // Verify manager name is shown in the row
    await expect(row.locator(`text=${orgAAdmin1.displayName}`)).toBeVisible();
  }
});

test("Step 9: org-b-admin-1 invites members + assigns manager", async ({ page }) => {
  await loginAs(page, orgBAdmin1.email, orgBAdmin1.password);

  for (const member of orgBMembers) {
    await inviteMemberViaOrgPage(page, "Organization Beta", member);
    expect(member.password).toBeTruthy();
  }

  for (const member of orgBMembers) {
    await assignManagerViaOrgPage(
      page,
      "Organization Beta",
      member.email,
      orgBAdmin1.displayName!,
    );
  }

  // Verify
  await page.goto("/admin/organizations");
  await page.waitForLoadState("networkidle");
  await page.click("text=Organization Beta");
  await page.waitForLoadState("networkidle");

  for (const member of orgBMembers) {
    const row = page.locator(`tr:has-text("${member.email}")`);
    await expect(row).toBeVisible({ timeout: 10_000 });
    await expect(row.locator(`text=${orgBAdmin1.displayName}`)).toBeVisible();
  }
});
```

**Step 3: Run tests 1-9**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts -g "Step [1-9]" --reporter=list`

Expected: All 9 PASS. Phase 1 (Organization Setup) complete.

**Step 4: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: steps 8-9 — org admins invite members and assign managers via GUI"
```

---

### Task 8: Step 10 — Org A member enters work log via GUI

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- `org-a-member-1` logs in and enters work log for several days in the current month.
- The worklog page (`/worklog`) shows a calendar. Click a date to open `DailyEntryForm`.
- In the daily entry form: select project (`#project-0`), enter hours (`#hours-0`), and save.
- We need a project to assign hours to. Projects may need to be created first via API or the member might see projects from their organization.
- **Project setup**: If no projects exist, create one via API before entering work log entries. Check if there's a project management API.
- Use a fixed date range for determinism: pick days from the current month.

**Step 1: Implement Step 10**

Replace `test.fixme("Step 10: ...")`:

```typescript
test("Step 10: Org A member enters work log", async ({ page }) => {
  await loginAs(page, orgAMembers[0].email, orgAMembers[0].password);

  // Navigate to worklog calendar
  await page.goto("/worklog");
  await page.waitForLoadState("networkidle");

  // We need a project to log time against. Create one via API if needed.
  // Check if projects are available first.
  const projectsResponse = await page.request.get(
    `${API_BASE_URL}/api/v1/members/${orgAMembers[0].id}/projects`,
  );

  let projectId: string;
  if (projectsResponse.ok()) {
    const projectsBody = await projectsResponse.json();
    if (projectsBody.projects?.length > 0) {
      projectId = projectsBody.projects[0].id;
    } else {
      // Create a project via API
      const createProjectResponse = await page.request.post(
        `${API_BASE_URL}/api/v1/admin/projects`,
        { data: { code: "PROJ-SCENARIO", name: "Scenario Test Project", tenantId } },
      );
      expect(createProjectResponse.ok()).toBe(true);
      const projectBody = await createProjectResponse.json();
      projectId = projectBody.id;
    }
  }

  // Enter work log for 3 days in the current month
  const today = new Date();
  const year = today.getFullYear();
  const month = today.getMonth(); // 0-indexed

  for (let dayOffset = 1; dayOffset <= 3; dayOffset++) {
    const targetDate = new Date(year, month, dayOffset + 1); // 2nd, 3rd, 4th of the month
    const monthName = targetDate.toLocaleString("en-US", { month: "long" });
    const dayNum = targetDate.getDate();

    // Click the calendar date
    await page.click(`button[aria-label="${monthName} ${dayNum}, ${year}"]`);
    await page.waitForLoadState("networkidle");

    // Wait for daily entry form to appear
    await expect(page.locator('[role="dialog"]')).toBeVisible({ timeout: 10_000 });

    // Select project (first available in dropdown)
    const projectSelect = page.locator("#project-0");
    if (await projectSelect.isVisible()) {
      // Select the first non-empty option
      const options = await projectSelect.locator("option").allTextContents();
      if (options.length > 1) {
        await projectSelect.selectOption({ index: 1 });
      }
    }

    // Enter hours
    const hoursInput = page.locator("#hours-0");
    await hoursInput.clear();
    await hoursInput.fill("8");

    // Save
    await page.click('button:has-text("Save")');

    // Wait for dialog to close or success indication
    await page.waitForLoadState("networkidle");
  }

  // Verify: calendar shows entries for those days
  // Days with entries should have visual indicators (non-empty cells)
  await page.goto("/worklog");
  await page.waitForLoadState("networkidle");

  // Check that at least one day shows hours
  await expect(page.locator("text=8.00")).toBeVisible({ timeout: 10_000 });
});
```

**Step 2: Run tests 1-10**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts -g "Step (1[0]|[1-9]\\b)" --reporter=list`

Expected: All 10 PASS.

**Troubleshooting:**
- If no projects available: The project creation API may differ. Check `AdminProjectController` for the exact endpoint and request format.
- If calendar date buttons not found: The `aria-label` format may differ from expected. Use `--headed` mode to inspect.
- If daily entry form doesn't open: The click target may need adjustment. Try clicking the date number directly.

**Step 3: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: step 10 — Org A member enters work log via GUI"
```

---

### Task 9: Step 11 — Org B manager proxy-enters work log

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- `org-b-admin-1` logs in and enables proxy mode for `org-b-member-1`.
- Navigate to `/worklog/proxy`, select the member, click "Enter Time for...".
- Proxy banner ("Proxy Mode: Entering time for...") should appear.
- Then enter work log same as Step 10 but entries are created under the member's name.

**Step 1: Implement Step 11**

Replace `test.fixme("Step 11: ...")`:

```typescript
test("Step 11: Org B manager proxy-enters work log", async ({ page }) => {
  await loginAs(page, orgBAdmin1.email, orgBAdmin1.password);

  // Navigate to proxy entry page
  await page.goto("/worklog/proxy");
  await page.waitForLoadState("networkidle");

  // Select org-b-member-1 from the dropdown
  const memberSelector = page.locator('select[aria-label="Select Team Member"]');
  await expect(memberSelector).toBeVisible({ timeout: 10_000 });

  // Select by member display name
  await memberSelector.selectOption({ label: new RegExp(orgBMembers[0].displayName!) });

  // Click "Enter Time for..." button
  await page.click('button:has-text("Enter Time for")');

  // Verify proxy mode banner is shown
  await expect(page.locator("text=Proxy Mode")).toBeVisible({ timeout: 10_000 });
  await expect(page.locator(`text=${orgBMembers[0].displayName}`)).toBeVisible();

  // Enter work log for 3 days (same pattern as Step 10)
  const today = new Date();
  const year = today.getFullYear();
  const month = today.getMonth();

  for (let dayOffset = 1; dayOffset <= 3; dayOffset++) {
    const targetDate = new Date(year, month, dayOffset + 1);
    const monthName = targetDate.toLocaleString("en-US", { month: "long" });
    const dayNum = targetDate.getDate();

    await page.click(`button[aria-label="${monthName} ${dayNum}, ${year}"]`);
    await page.waitForLoadState("networkidle");

    await expect(page.locator('[role="dialog"]')).toBeVisible({ timeout: 10_000 });

    const projectSelect = page.locator("#project-0");
    if (await projectSelect.isVisible()) {
      const options = await projectSelect.locator("option").allTextContents();
      if (options.length > 1) {
        await projectSelect.selectOption({ index: 1 });
      }
    }

    const hoursInput = page.locator("#hours-0");
    await hoursInput.clear();
    await hoursInput.fill("7.5");

    await page.click('button:has-text("Save")');
    await page.waitForLoadState("networkidle");
  }

  // Verify entries are saved (proxy icon should appear on calendar)
  await expect(page.locator("text=7.50")).toBeVisible({ timeout: 10_000 });
});
```

**Step 2: Run tests 1-11**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts --reporter=list`

Expected: Steps 1-11 PASS, Steps 12-15 fixme/skipped.

**Step 3: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: step 11 — Org B manager proxy-enters work log via GUI"
```

---

### Task 10: Steps 12-13 — Org A: submit and approve

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- Step 12: `org-a-member-1` clicks "Submit for Approval" on the worklog page. After submit, entries become read-only.
- Step 13: `org-a-admin-1` checks the approval queue (`/worklog/approval`) and approves.
- The `SubmitButton` component shows "Submit for Approval" when status is PENDING/null.
- The approval queue shows pending submissions with "Approve" button.

**Step 1: Implement Steps 12 and 13**

Replace `test.fixme("Step 12: ...")` and `test.fixme("Step 13: ...")`:

```typescript
test("Step 12: Org A member submits for approval", async ({ page }) => {
  await loginAs(page, orgAMembers[0].email, orgAMembers[0].password);

  await page.goto("/worklog");
  await page.waitForLoadState("networkidle");

  // Click "Submit for Approval" button
  const submitButton = page.locator('button:has-text("Submit for Approval")');
  await expect(submitButton).toBeVisible({ timeout: 10_000 });
  await expect(submitButton).toBeEnabled();
  await submitButton.click();

  // Handle confirmation dialog if present
  const confirmButton = page.locator('button:has-text("Submit")').last();
  if (await confirmButton.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await confirmButton.click();
  }

  // Wait for status to change
  await page.waitForLoadState("networkidle");

  // Verify: button should now show "Submitted" and be disabled
  await expect(page.locator('button:has-text("Submitted")')).toBeVisible({ timeout: 10_000 });

  // Verify: clicking a day should show read-only inputs
  const today = new Date();
  const year = today.getFullYear();
  const month = today.getMonth();
  const targetDate = new Date(year, month, 2); // 2nd day (where we entered data)
  const monthName = targetDate.toLocaleString("en-US", { month: "long" });

  await page.click(`button[aria-label="${monthName} 2, ${year}"]`);
  await expect(page.locator('[role="dialog"]')).toBeVisible({ timeout: 10_000 });

  // Hours input should be disabled (read-only)
  await expect(page.locator("#hours-0")).toBeDisabled();
});

test("Step 13: Org A manager approves", async ({ page }) => {
  await loginAs(page, orgAAdmin1.email, orgAAdmin1.password);

  // Navigate to approval queue
  await page.goto("/worklog/approval");
  await page.waitForLoadState("networkidle");

  // Find the submission from org-a-member-1
  const submissionCard = page.locator(`text=${orgAMembers[0].displayName}`).first();
  await expect(submissionCard).toBeVisible({ timeout: 10_000 });

  // Click "Approve" button
  const approveButton = page.locator('button:has-text("Approve")').first();
  await expect(approveButton).toBeVisible();
  await approveButton.click();

  // Wait for approval to complete
  await page.waitForLoadState("networkidle");

  // Verify: the submission should no longer appear in the queue
  // or should show an "Approved" status
  await expect(page.locator(`text=${orgAMembers[0].displayName}`)).not.toBeVisible({
    timeout: 10_000,
  });
});
```

**Step 2: Run all tests**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts --reporter=list`

Expected: Steps 1-13 PASS, Steps 14-15 fixme/skipped.

**Step 3: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: steps 12-13 — Org A monthly submit and approve via GUI"
```

---

### Task 11: Steps 14-15 — Org B: proxy-submit and different admin approves

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts`

**Implementation notes:**
- Step 14: `org-b-admin-1` submits org-b-member-1's month via proxy mode.
- Step 15: `org-b-admin-2` (a DIFFERENT admin) approves from the approval queue.
- This tests that any admin in the org (not just the submitter) can approve.

**Step 1: Implement Steps 14 and 15**

Replace `test.fixme("Step 14: ...")` and `test.fixme("Step 15: ...")`:

```typescript
test("Step 14: Org B manager proxy-submits for approval", async ({ page }) => {
  await loginAs(page, orgBAdmin1.email, orgBAdmin1.password);

  // Re-enter proxy mode for org-b-member-1
  await page.goto("/worklog/proxy");
  await page.waitForLoadState("networkidle");

  const memberSelector = page.locator('select[aria-label="Select Team Member"]');
  await memberSelector.selectOption({ label: new RegExp(orgBMembers[0].displayName!) });
  await page.click('button:has-text("Enter Time for")');

  // Verify proxy mode is active
  await expect(page.locator("text=Proxy Mode")).toBeVisible({ timeout: 10_000 });

  // Click "Submit for Approval" (proxy submit)
  const submitButton = page.locator('button:has-text("Submit for Approval")');
  await expect(submitButton).toBeVisible({ timeout: 10_000 });
  await submitButton.click();

  // Handle confirmation dialog
  const confirmButton = page.locator('button:has-text("Submit")').last();
  if (await confirmButton.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await confirmButton.click();
  }

  await page.waitForLoadState("networkidle");

  // Verify: status should show "Submitted"
  await expect(page.locator('button:has-text("Submitted")')).toBeVisible({ timeout: 10_000 });
});

test("Step 15: Org B different admin approves", async ({ page }) => {
  await loginAs(page, orgBAdmin2.email, orgBAdmin2.password);

  // Navigate to approval queue
  await page.goto("/worklog/approval");
  await page.waitForLoadState("networkidle");

  // Find the submission from org-b-member-1
  const submissionCard = page.locator(`text=${orgBMembers[0].displayName}`).first();
  await expect(submissionCard).toBeVisible({ timeout: 10_000 });

  // Approve
  const approveButton = page.locator('button:has-text("Approve")').first();
  await expect(approveButton).toBeVisible();
  await approveButton.click();

  await page.waitForLoadState("networkidle");

  // Verify: submission no longer in queue
  await expect(page.locator(`text=${orgBMembers[0].displayName}`)).not.toBeVisible({
    timeout: 10_000,
  });
});
```

**Step 2: Run the full scenario**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts --reporter=list`

Expected: All 15 tests PASS.

**Step 3: Commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: steps 14-15 — Org B proxy-submit and cross-admin approval via GUI"
```

---

### Task 12: Final cleanup and full verification

**Files:**
- Modify: `frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts` (if any fixme tests remain)

**Step 1: Remove all remaining `test.fixme` stubs**

Verify no test.fixme calls remain in the file. All 15 steps should be implemented.

**Step 2: Run the full scenario end-to-end**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts --reporter=html`

Expected: All 15 tests PASS. HTML report generated at `playwright-report/index.html`.

**Step 3: Run with tracing for documentation**

Run: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts --trace on`

This creates trace files for each step that can be viewed via `npx playwright show-trace`.

**Step 4: Verify no existing tests are broken**

Run: `cd frontend && npx playwright test --reporter=list`

Expected: All existing E2E tests still pass alongside the new scenario test.

**Step 5: Final commit**

```bash
git add frontend/tests/e2e/scenarios/tenant-onboarding.spec.ts
git commit -m "test: complete tenant onboarding E2E scenario — 15 steps, full cycle"
```

---

## Implementation Notes

### Prerequisites for running this test

1. **Backend**: `cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'`
2. **Database**: `cd infra/docker && docker-compose -f docker-compose.dev.yml up -d` (PostgreSQL + Redis + MailHog)
3. **Frontend**: `cd frontend && npm run dev`
4. **Run test**: `cd frontend && npx playwright test tests/e2e/scenarios/tenant-onboarding.spec.ts`

### Key differences from existing E2E tests

| Aspect | Existing E2E tests | This scenario test |
|--------|-------------------|-------------------|
| API mocking | All APIs mocked via `page.route()` | No mocking — real backend |
| Auth | Fixed user (Bob) via `fixtures/auth.ts` | Dynamic users via `loginAs()` helper |
| State | Each test independent | Sequential tests sharing state |
| Scope | Single feature | Full user workflow |

### Cross-tenant bootstrap (Steps 2-3)

The admin panel may not support cross-tenant operations (creating orgs in a different tenant than the logged-in user's). Steps 2-3 use direct API calls to bootstrap the new tenant's initial structure. Once the first tenant owner is created and can log in (Step 4), all subsequent operations are GUI-based. This is a pragmatic trade-off — the API calls still hit the real backend, just not through the GUI.

### Selectors reference

| Element | Selector |
|---------|----------|
| Tenant create button | `button:has-text("テナント作成")` |
| Tenant code field | `#tenant-code` |
| Tenant name field | `#tenant-name` |
| Create/Submit button | `button:has-text("作成")` |
| Org code field | `#org-code` |
| Org name field | `#org-name` |
| Fiscal year pattern select | `#fiscal-year-pattern` |
| Monthly period pattern select | `#monthly-period-pattern` |
| Member email field | `#new-member-email` |
| Member name field | `#new-member-name` |
| Manager select | `#manager-select` |
| Calendar date | `button[aria-label="{Month} {day}, {year}"]` |
| Hours input | `#hours-0` |
| Project select | `#project-0` |
| Submit for Approval | `button:has-text("Submit for Approval")` |
| Approve button | `button:has-text("Approve")` |
| Proxy member selector | `select[aria-label="Select Team Member"]` |
| Proxy mode banner | `text=Proxy Mode` |
