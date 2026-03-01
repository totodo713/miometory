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

// --- GUI Helper: Invite member via organization page ---

interface PersonaCredentials {
  email: string;
  password: string;
  id?: string;
  displayName: string;
}

async function inviteMemberViaOrgPage(page: Page, orgName: string, member: PersonaCredentials): Promise<void> {
  // Navigate to organizations
  await page.goto("/admin/organizations");
  await page.waitForLoadState("networkidle");

  // Select the organization
  await page.click(`text=${orgName}`);
  await page.waitForLoadState("networkidle");

  // Find and click the "create member" button
  await page.click('button:has-text("Create Member")');

  // Fill in the form
  await page.fill("#new-member-email", member.email);
  await page.fill("#new-member-name", member.displayName);

  // Submit and capture the response with temporary password
  const responsePromise = page.waitForResponse(
    (resp) => resp.url().includes("/api/v1/admin/members") && resp.request().method() === "POST",
  );

  await page.getByRole("button", { name: "Create", exact: true }).click();

  const response = await responsePromise;
  expect(response.ok()).toBe(true);
  const body = await response.json();
  member.id = body.id;
  member.password = body.temporaryPassword;
}

// --- GUI Helper: Fill work log entries for multiple days ---

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

async function fillWorkLogEntries(page: Page, hours: string, dayCount: number): Promise<void> {
  // Use previous month to guarantee all dates are in the past (avoids DATE_IN_FUTURE rejection)
  const today = new Date();
  const prevMonth = new Date(today.getFullYear(), today.getMonth() - 1, 1);
  const year = prevMonth.getFullYear();
  const month = prevMonth.getMonth(); // 0-indexed

  // Navigate to previous month on the calendar
  await page.getByRole("button", { name: "Previous month", exact: true }).click();
  await page.waitForLoadState("networkidle");

  for (let dayOffset = 1; dayOffset <= dayCount; dayOffset++) {
    const dayNum = dayOffset + 1; // 2nd, 3rd, 4th, ...
    const monthName = MONTH_NAMES[month];

    // Click the calendar date button
    await page.click(`button[aria-label="${monthName} ${dayNum}, ${year}"]`);
    await page.waitForLoadState("networkidle");

    // Wait for daily entry form dialog to appear
    const dialog = page.locator('[role="dialog"]');
    await expect(dialog).toBeVisible({ timeout: 10_000 });

    // Select project using the combobox (ProjectSelector is a searchable dropdown)
    const projectCombobox = dialog.locator('[role="combobox"]').first();
    await expect(projectCombobox).toBeVisible({ timeout: 5_000 });
    await projectCombobox.click();

    // Wait for the dropdown options to appear and click the first one
    const firstOption = dialog.locator('[role="option"]').first();
    await expect(firstOption).toBeVisible({ timeout: 5_000 });
    await firstOption.click();

    // Enter hours
    const hoursInput = dialog.getByRole("spinbutton", { name: "Hours" }).first();
    await hoursInput.clear();
    await hoursInput.fill(hours);

    // Click Save
    const saveButton = dialog.getByRole("button", { name: "Save" });
    await expect(saveButton).toBeEnabled({ timeout: 5_000 });
    await saveButton.click();

    // Wait for dialog to close (navigation back to calendar)
    await expect(dialog).toBeHidden({ timeout: 10_000 });
    await page.waitForLoadState("networkidle");
  }
}

// --- GUI Helper: Assign manager via organization page ---

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

  // Find the member row and click "Assign Manager"
  const memberRow = page.locator(`tr:has-text("${memberEmail}")`);
  await memberRow.locator('button:has-text("Assign Manager")').click();

  // Select the manager from the dropdown (label format: "DisplayName (email)")
  const managerOption = page.locator("#manager-select option").filter({ hasText: managerDisplayName });
  await expect(managerOption).toBeAttached({ timeout: 10_000 });
  const optionValue = await managerOption.getAttribute("value");
  expect(optionValue).toBeTruthy();
  await page.selectOption("#manager-select", optionValue as string);

  // Click assign button (exact match to avoid matching "Assign Manager" buttons)
  await page.getByRole("button", { name: "Assign", exact: true }).click();

  // Wait for success
  await page.waitForLoadState("networkidle");
}

// --- Shared state across serial tests ---

test.describe
  .serial("Tenant Onboarding: Full Cycle", () => {
    test.describe.configure({ timeout: 60_000 });

    // Skip non-Chromium browsers locally (WSL2 lacks webkit system deps)
    // CI environments have all browser deps installed via `npx playwright install-deps`
    test.beforeEach(({ browserName }) => {
      test.skip(browserName !== "chromium" && !process.env.CI, "Skipping non-Chromium browsers locally");
    });

    // Unique suffix per test run to avoid DUPLICATE_EMAIL on re-runs
    const runId = Date.now().toString().slice(-6);

    let tenantId: string;
    let tenantCode: string;
    let tenantName: string;

    let fiscalYearPatternId: string;
    let monthlyPeriodPatternId: string;

    let orgAId: string;
    let orgBId: string;
    let projAId: string;
    let projBId: string;

    const tenantOwner1: PersonaCredentials = {
      email: `tenant-owner-1-${runId}@scenario-test.example.com`,
      password: "",
      displayName: "Tenant Owner 1",
    };
    const tenantOwner2: PersonaCredentials = {
      email: `tenant-owner-2-${runId}@scenario-test.example.com`,
      password: "",
      displayName: "Tenant Owner 2",
    };

    const orgAAdmin1: PersonaCredentials = {
      email: `org-a-admin-1-${runId}@scenario-test.example.com`,
      password: "",
      displayName: "Org A Admin 1",
    };
    const orgAAdmin2: PersonaCredentials = {
      email: `org-a-admin-2-${runId}@scenario-test.example.com`,
      password: "",
      displayName: "Org A Admin 2",
    };
    const orgBAdmin1: PersonaCredentials = {
      email: `org-b-admin-1-${runId}@scenario-test.example.com`,
      password: "",
      displayName: "Org B Admin 1",
    };
    const orgBAdmin2: PersonaCredentials = {
      email: `org-b-admin-2-${runId}@scenario-test.example.com`,
      password: "",
      displayName: "Org B Admin 2",
    };

    const orgAMembers: PersonaCredentials[] = [
      {
        email: `org-a-member-1-${runId}@scenario-test.example.com`,
        password: "",
        displayName: "Org A Member 1",
      },
      {
        email: `org-a-member-2-${runId}@scenario-test.example.com`,
        password: "",
        displayName: "Org A Member 2",
      },
      {
        email: `org-a-member-3-${runId}@scenario-test.example.com`,
        password: "",
        displayName: "Org A Member 3",
      },
    ];
    const orgBMembers: PersonaCredentials[] = [
      {
        email: `org-b-member-1-${runId}@scenario-test.example.com`,
        password: "",
        displayName: "Org B Member 1",
      },
      {
        email: `org-b-member-2-${runId}@scenario-test.example.com`,
        password: "",
        displayName: "Org B Member 2",
      },
      {
        email: `org-b-member-3-${runId}@scenario-test.example.com`,
        password: "",
        displayName: "Org B Member 3",
      },
    ];

    // ============================================================
    // Phase 1: Organization Setup (Steps 1-9)
    // ============================================================

    test("Step 1: sys-admin creates tenant", async ({ page }) => {
      // Login as sys-admin
      await loginAs(page, "sysadmin@miometry.example.com", "Password1");

      // Navigate to tenant management
      await page.goto("/admin/tenants");
      await page.waitForLoadState("networkidle");

      // Click create tenant button
      await page.click('button:has-text("Add Tenant")');

      // Fill in tenant form
      tenantCode = `SCEN${Date.now().toString().slice(-6)}`;
      tenantName = `Scenario Test Tenant ${tenantCode}`;

      await page.fill("#tenant-code", tenantCode);
      await page.fill("#tenant-name", tenantName);

      // Intercept the create API response to capture tenant ID directly
      const createResponsePromise = page.waitForResponse(
        (resp) => resp.url().includes("/api/v1/admin/tenants") && resp.request().method() === "POST",
      );

      // Submit (exact match avoids hitting the "Add Tenant" header button)
      await page.getByRole("button", { name: "Create", exact: true }).click();

      // Capture the created tenant ID from the POST response
      const createResponse = await createResponsePromise;
      expect(createResponse.ok()).toBe(true);
      const createBody = await createResponse.json();
      tenantId = createBody.id;
      expect(tenantId).toBeTruthy();
    });

    test("Step 2: sys-admin configures fiscal patterns", async ({ page }) => {
      // Each serial test gets a new BrowserContext — must re-authenticate
      await loginAs(page, "sysadmin@miometry.example.com", "Password1");

      // tenantId was captured in Step 1 from the create API response
      expect(tenantId).toBeTruthy();

      // Create fiscal year pattern via API (January 1st start = calendar year)
      const fyResponse = await page.request.post(`${API_BASE_URL}/api/v1/tenants/${tenantId}/fiscal-year-patterns`, {
        data: { name: "Calendar Year", startMonth: 1, startDay: 1 },
      });
      expect(fyResponse.ok()).toBe(true);
      const fyBody = await fyResponse.json();
      fiscalYearPatternId = fyBody.id;

      // Create monthly period pattern via API (21st day start = matches FiscalMonthPeriod hardcoded constraint)
      const mpResponse = await page.request.post(`${API_BASE_URL}/api/v1/tenants/${tenantId}/monthly-period-patterns`, {
        data: { name: "21st Closing", startDay: 21 },
      });
      expect(mpResponse.ok()).toBe(true);
      const mpBody = await mpResponse.json();
      monthlyPeriodPatternId = mpBody.id;

      // Verify patterns exist
      expect(fiscalYearPatternId).toBeTruthy();
      expect(monthlyPeriodPatternId).toBeTruthy();
    });

    test("Step 3: sys-admin bootstraps orgs and tenant owners", async ({ page }) => {
      // Each serial test gets a new BrowserContext — must re-authenticate
      await loginAs(page, "sysadmin@miometry.example.com", "Password1");

      // Use the bootstrap endpoint to create orgs + invite tenant owners in one call
      const bootstrapResponse = await page.request.post(`${API_BASE_URL}/api/v1/admin/tenants/${tenantId}/bootstrap`, {
        data: {
          organizations: [
            {
              code: "ORG_A",
              name: "Organization Alpha",
              fiscalYearPatternId,
              monthlyPeriodPatternId,
            },
            {
              code: "ORG_B",
              name: "Organization Beta",
              fiscalYearPatternId,
              monthlyPeriodPatternId,
            },
          ],
          members: [
            {
              email: tenantOwner1.email,
              displayName: tenantOwner1.displayName,
              organizationCode: "ORG_A",
              tenantAdmin: true,
            },
            {
              email: tenantOwner2.email,
              displayName: tenantOwner2.displayName,
              organizationCode: "ORG_B",
              tenantAdmin: true,
            },
          ],
        },
      });
      if (!bootstrapResponse.ok()) {
        const errBody = await bootstrapResponse.text();
        throw new Error(`Bootstrap failed: ${bootstrapResponse.status()} - ${errBody}`);
      }
      const body = await bootstrapResponse.json();

      // Extract org IDs
      orgAId = body.result.organizations.find((o: { code: string }) => o.code === "ORG_A").id;
      orgBId = body.result.organizations.find((o: { code: string }) => o.code === "ORG_B").id;

      // Extract member credentials
      const owner1 = body.result.members.find((m: { email: string }) => m.email === tenantOwner1.email);
      tenantOwner1.id = owner1.id;
      tenantOwner1.password = owner1.temporaryPassword;

      const owner2 = body.result.members.find((m: { email: string }) => m.email === tenantOwner2.email);
      tenantOwner2.id = owner2.id;
      tenantOwner2.password = owner2.temporaryPassword;

      // Verify credentials were returned
      expect(tenantOwner1.password).toBeTruthy();
      expect(tenantOwner2.password).toBeTruthy();
    });

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

    test("Step 6: tenant-owner-1 invites Org A admins", async ({ page }) => {
      await loginAs(page, tenantOwner1.email, tenantOwner1.password);

      // Invite org-a-admin-1
      await inviteMemberViaOrgPage(page, "Organization Alpha", orgAAdmin1);
      expect(orgAAdmin1.password).toBeTruthy();

      // Invite org-a-admin-2
      await inviteMemberViaOrgPage(page, "Organization Alpha", orgAAdmin2);
      expect(orgAAdmin2.password).toBeTruthy();

      // Assign TENANT_ADMIN role to org admins (needed for member.create + project.create permissions)
      const role1Resp = await page.request.post(
        `${API_BASE_URL}/api/v1/admin/members/${orgAAdmin1.id}/assign-tenant-admin`,
      );
      expect(role1Resp.ok()).toBe(true);

      const role2Resp = await page.request.post(
        `${API_BASE_URL}/api/v1/admin/members/${orgAAdmin2.id}/assign-tenant-admin`,
      );
      expect(role2Resp.ok()).toBe(true);

      // Verify both admins appear in the member list
      await page.goto("/admin/organizations");
      await page.waitForLoadState("networkidle");
      await page.click("text=Organization Alpha");
      await page.waitForLoadState("networkidle");

      await expect(page.locator(`text=${orgAAdmin1.email}`)).toBeVisible({
        timeout: 10_000,
      });
      await expect(page.locator(`text=${orgAAdmin2.email}`)).toBeVisible();
    });

    test("Step 7: tenant-owner-2 invites Org B admins", async ({ page }) => {
      await loginAs(page, tenantOwner2.email, tenantOwner2.password);

      await inviteMemberViaOrgPage(page, "Organization Beta", orgBAdmin1);
      expect(orgBAdmin1.password).toBeTruthy();

      await inviteMemberViaOrgPage(page, "Organization Beta", orgBAdmin2);
      expect(orgBAdmin2.password).toBeTruthy();

      // Assign TENANT_ADMIN role to org admins (needed for member.create + project.create permissions)
      const role1Resp = await page.request.post(
        `${API_BASE_URL}/api/v1/admin/members/${orgBAdmin1.id}/assign-tenant-admin`,
      );
      expect(role1Resp.ok()).toBe(true);

      const role2Resp = await page.request.post(
        `${API_BASE_URL}/api/v1/admin/members/${orgBAdmin2.id}/assign-tenant-admin`,
      );
      expect(role2Resp.ok()).toBe(true);

      // Verify both admins appear in the member list
      await page.goto("/admin/organizations");
      await page.waitForLoadState("networkidle");
      await page.click("text=Organization Beta");
      await page.waitForLoadState("networkidle");

      await expect(page.locator(`text=${orgBAdmin1.email}`)).toBeVisible({
        timeout: 10_000,
      });
      await expect(page.locator(`text=${orgBAdmin2.email}`)).toBeVisible();
    });

    test("Step 8: org-a-admin-1 invites members + assigns manager + creates project", async ({ page }) => {
      await loginAs(page, orgAAdmin1.email, orgAAdmin1.password);

      // Invite 3 members
      for (const member of orgAMembers) {
        await inviteMemberViaOrgPage(page, "Organization Alpha", member);
        expect(member.password).toBeTruthy();
      }

      // Assign org-a-admin-1 as manager for all 3 members
      for (const member of orgAMembers) {
        await assignManagerViaOrgPage(page, "Organization Alpha", member.email, orgAAdmin1.displayName);
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

      // Create project for Org A (org admin = TENANT_ADMIN has project.create permission)
      const projResp = await page.request.post(`${API_BASE_URL}/api/v1/admin/projects`, {
        data: {
          code: `PROJ-A-${runId}`,
          name: "Scenario Project A",
          organizationId: orgAId,
        },
      });
      expect(projResp.ok()).toBe(true);
      const projBody = await projResp.json();
      projAId = projBody.id;

      // Assign all Org A members to the project
      for (const member of orgAMembers) {
        const assignResp = await page.request.post(`${API_BASE_URL}/api/v1/admin/assignments`, {
          data: { memberId: member.id, projectId: projAId },
        });
        expect(assignResp.ok()).toBe(true);
      }
    });

    test("Step 9: org-b-admin-1 invites members + assigns manager + creates project", async ({ page }) => {
      await loginAs(page, orgBAdmin1.email, orgBAdmin1.password);

      for (const member of orgBMembers) {
        await inviteMemberViaOrgPage(page, "Organization Beta", member);
        expect(member.password).toBeTruthy();
      }

      for (const member of orgBMembers) {
        await assignManagerViaOrgPage(page, "Organization Beta", member.email, orgBAdmin1.displayName);
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

      // Create project for Org B (org admin = TENANT_ADMIN has project.create permission)
      const projResp = await page.request.post(`${API_BASE_URL}/api/v1/admin/projects`, {
        data: {
          code: `PROJ-B-${runId}`,
          name: "Scenario Project B",
          organizationId: orgBId,
        },
      });
      expect(projResp.ok()).toBe(true);
      const projBody = await projResp.json();
      projBId = projBody.id;

      // Assign all Org B members to the project
      for (const member of orgBMembers) {
        const assignResp = await page.request.post(`${API_BASE_URL}/api/v1/admin/assignments`, {
          data: { memberId: member.id, projectId: projBId },
        });
        expect(assignResp.ok()).toBe(true);
      }
    });

    // ============================================================
    // Phase 2: Work Log Entry (Steps 10-11)
    // ============================================================

    test("Step 10: Org A member enters work log", async ({ page }) => {
      await loginAs(page, orgAMembers[0].email, orgAMembers[0].password);

      // Project was already created in Step 8 by org admin

      // Navigate to worklog calendar
      await page.goto("/worklog");
      await page.waitForLoadState("networkidle");

      // Enter work log for 3 days in the current month (2nd, 3rd, 4th)
      await fillWorkLogEntries(page, "8", 3);

      // Verify: calendar shows entries for those days (format is "8h" on calendar cells)
      await expect(page.locator("text=8h").first()).toBeVisible({ timeout: 10_000 });
    });

    test("Step 11: Org B manager proxy-enters work log", async ({ page }) => {
      await loginAs(page, orgBAdmin1.email, orgBAdmin1.password);

      // Navigate to proxy entry page
      await page.goto("/worklog/proxy");
      await page.waitForLoadState("networkidle");

      // Select org-b-member-1 from the dropdown
      const memberSelector = page.locator('select[aria-label="Select member"]');
      await expect(memberSelector).toBeVisible({ timeout: 10_000 });

      // Wait for subordinate options to load, then select by member ID
      await expect(memberSelector.locator(`option:has-text("${orgBMembers[0].displayName}")`)).toBeAttached({
        timeout: 10_000,
      });
      await memberSelector.selectOption(orgBMembers[0].id as string);

      // Click "Enter Time for..." button and wait for navigation to /worklog
      await page.click('button:has-text("Proxy Entry")');
      await page.waitForURL(/\/worklog$/, { timeout: 10_000 });
      await page.waitForLoadState("networkidle");

      // Verify proxy mode banner is shown on worklog page (includes member name)
      await expect(page.getByText(/Entering for:/i).first()).toBeVisible({ timeout: 10_000 });

      // Enter work log for 3 days (same pattern as Step 10)
      await fillWorkLogEntries(page, "7.5", 3);

      // Verify entries are saved (calendar shows "7.5h" format)
      await expect(page.locator("text=7.5h").first()).toBeVisible({ timeout: 10_000 });
    });

    // ============================================================
    // Phase 3: Monthly Approval (Steps 12-15)
    // ============================================================

    test("Step 12: Org A member submits for approval", async ({ page }) => {
      await loginAs(page, orgAMembers[0].email, orgAMembers[0].password);

      await page.goto("/worklog");
      await page.waitForLoadState("networkidle");

      // Click "Submit Monthly" button (directly submits, no confirmation dialog)
      const submitButton = page.getByRole("button", { name: "Submit Monthly" });
      await expect(submitButton).toBeVisible({ timeout: 10_000 });
      await expect(submitButton).toBeEnabled();
      await submitButton.click();

      // Wait for submission to complete and button text to change
      await expect(page.getByRole("button", { name: "Submitted" })).toBeVisible({ timeout: 15_000 });

      // Verify: clicking a day should show read-only inputs
      const today = new Date();
      const year = today.getFullYear();
      const month = today.getMonth();
      const monthName = MONTH_NAMES[month];

      await page.click(`button[aria-label="${monthName} 2, ${year}"]`);
      await expect(page.locator('[role="dialog"]')).toBeVisible({
        timeout: 10_000,
      });

      // Hours input should be disabled (read-only)
      await expect(page.locator("#hours-0")).toBeDisabled();
    });

    test("Step 13: Org A manager approves", async ({ page }) => {
      await loginAs(page, orgAAdmin1.email, orgAAdmin1.password);

      // Navigate to approval queue
      await page.goto("/worklog/approval");
      await page.waitForLoadState("networkidle");

      // Find the submission card for org-a-member-1 (approval page shows member ID, not display name)
      const memberId = orgAMembers[0].id as string;
      const submissionCard = page.locator(`text=${memberId}`).first();
      await expect(submissionCard).toBeVisible({ timeout: 10_000 });

      // Click "Approve" - navigate from heading up to card container, then find button
      const approveButton = page
        .locator(`h3:has-text("${memberId}")`)
        .locator("xpath=ancestor::div[2]")
        .getByRole("button", { name: "Approve" });
      await expect(approveButton).toBeVisible({ timeout: 5_000 });
      await approveButton.click();

      // Wait for approval to complete
      await page.waitForLoadState("networkidle");

      // Verify: the submission should no longer appear in the queue
      await expect(page.getByRole("heading", { name: memberId })).not.toBeVisible({
        timeout: 10_000,
      });
    });

    test("Step 14: Org B manager proxy-submits for approval", async ({ page }) => {
      await loginAs(page, orgBAdmin1.email, orgBAdmin1.password);

      // Re-enter proxy mode for org-b-member-1
      await page.goto("/worklog/proxy");
      await page.waitForLoadState("networkidle");

      const memberSelector = page.locator('select[aria-label="Select member"]');
      await expect(memberSelector).toBeVisible({ timeout: 10_000 });
      await expect(memberSelector.locator(`option:has-text("${orgBMembers[0].displayName}")`)).toBeAttached({
        timeout: 10_000,
      });
      await memberSelector.selectOption(orgBMembers[0].id as string);
      await page.click('button:has-text("Proxy Entry")');
      await page.waitForURL(/\/worklog$/, { timeout: 10_000 });
      await page.waitForLoadState("networkidle");

      // Verify proxy mode is active on worklog page
      await expect(page.getByText(/Entering for:/i).first()).toBeVisible({
        timeout: 10_000,
      });

      // Click "Submit Monthly" (proxy submit, no confirmation dialog)
      const submitButton = page.getByRole("button", { name: /Submit Monthly/ });
      await expect(submitButton).toBeVisible({ timeout: 10_000 });
      await submitButton.click();

      // Wait for submission to complete
      await expect(page.getByRole("button", { name: "Submitted" })).toBeVisible({ timeout: 15_000 });
    });

    test("Step 15: Org B different admin approves", async ({ page }) => {
      await loginAs(page, orgBAdmin2.email, orgBAdmin2.password);

      // Navigate to approval queue
      await page.goto("/worklog/approval");
      await page.waitForLoadState("networkidle");

      // Find the submission from org-b-member-1 (approval page shows member ID, not display name)
      const memberId = orgBMembers[0].id as string;
      const submissionCard = page.locator(`text=${memberId}`).first();
      await expect(submissionCard).toBeVisible({ timeout: 10_000 });

      // Click "Approve" - navigate from heading up to card container, then find button
      const approveButton = page
        .locator(`h3:has-text("${memberId}")`)
        .locator("xpath=ancestor::div[2]")
        .getByRole("button", { name: "Approve" });
      await expect(approveButton).toBeVisible({ timeout: 5_000 });
      await approveButton.click();

      await page.waitForLoadState("networkidle");

      // Verify: submission no longer in queue
      await expect(page.getByRole("heading", { name: memberId })).not.toBeVisible({
        timeout: 10_000,
      });
    });
  });
