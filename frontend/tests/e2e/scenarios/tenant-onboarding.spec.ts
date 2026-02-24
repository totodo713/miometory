import { type Page, expect, test } from "@playwright/test";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// --- Auth Helper ---

interface AuthUser {
	id: string;
	email: string;
	displayName: string;
}

async function loginAs(
	page: Page,
	email: string,
	password: string,
): Promise<AuthUser> {
	// Navigate to app origin first — sessionStorage requires a real origin (not about:blank)
	if (page.url() === "about:blank") {
		await page.goto("/");
		await page.waitForLoadState("domcontentloaded");
	}
	// Clear previous session
	await page.evaluate(() => window.sessionStorage.clear());

	const response = await page.request.post(
		`${API_BASE_URL}/api/v1/auth/login`,
		{
			data: { email, password, rememberMe: false },
		},
	);
	if (!response.ok()) {
		const body = await response.text();
		throw new Error(
			`Login failed for ${email}: ${response.status()} - ${body}`,
		);
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

// --- GUI Helper: Invite member via organization page ---

interface PersonaCredentials {
	email: string;
	password: string;
	id?: string;
	displayName: string;
}

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

	// Find and click the "create member" button
	await page.click('button:has-text("メンバー作成")');

	// Fill in the form
	await page.fill("#new-member-email", member.email);
	await page.fill("#new-member-name", member.displayName);

	// Submit and capture the response with temporary password
	const responsePromise = page.waitForResponse(
		(resp) =>
			resp.url().includes("/api/v1/admin/members") &&
			resp.request().method() === "POST",
	);

	await page.click('button:has-text("作成")');

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

async function fillWorkLogEntries(
	page: Page,
	hours: string,
	dayCount: number,
): Promise<void> {
	const today = new Date();
	const year = today.getFullYear();
	const month = today.getMonth(); // 0-indexed

	for (let dayOffset = 1; dayOffset <= dayCount; dayOffset++) {
		const dayNum = dayOffset + 1; // 2nd, 3rd, 4th, ...
		const monthName = MONTH_NAMES[month];

		// Click the calendar date
		await page.click(
			`button[aria-label="${monthName} ${dayNum}, ${year}"]`,
		);
		await page.waitForLoadState("networkidle");

		// Wait for daily entry form to appear
		await expect(page.locator('[role="dialog"]')).toBeVisible({
			timeout: 10_000,
		});

		// Select project (first available in dropdown)
		const projectSelect = page.locator("#project-0");
		if (await projectSelect.isVisible()) {
			const options = await projectSelect
				.locator("option")
				.allTextContents();
			if (options.length > 1) {
				await projectSelect.selectOption({ index: 1 });
			}
		}

		// Enter hours
		const hoursInput = page.locator("#hours-0");
		await hoursInput.clear();
		await hoursInput.fill(hours);

		// Save
		await page.click('button:has-text("Save")');

		// Wait for dialog to close or success indication
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

	// Find the member row and click "マネージャー割当"
	const memberRow = page.locator(`tr:has-text("${memberEmail}")`);
	await memberRow.locator('button:has-text("マネージャー割当")').click();

	// Select the manager from the dropdown
	await page.selectOption("#manager-select", {
		label: new RegExp(managerDisplayName),
	});

	// Click assign button
	await page.click('button:has-text("割り当て")');

	// Wait for success
	await page.waitForLoadState("networkidle");
}

// --- Shared state across serial tests ---

test.describe.serial("Tenant Onboarding: Full Cycle", () => {
	test.describe.configure({ timeout: 60_000 });

	// Skip non-Chromium browsers locally (WSL2 lacks webkit system deps)
	// CI environments have all browser deps installed via `npx playwright install-deps`
	test.beforeEach(({ browserName }) => {
		test.skip(
			browserName !== "chromium" && !process.env.CI,
			"Skipping non-Chromium browsers locally",
		);
	});

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
		{
			email: "org-a-member-1@scenario-test.example.com",
			password: "",
			displayName: "Org A Member 1",
		},
		{
			email: "org-a-member-2@scenario-test.example.com",
			password: "",
			displayName: "Org A Member 2",
		},
		{
			email: "org-a-member-3@scenario-test.example.com",
			password: "",
			displayName: "Org A Member 3",
		},
	];
	const orgBMembers: PersonaCredentials[] = [
		{
			email: "org-b-member-1@scenario-test.example.com",
			password: "",
			displayName: "Org B Member 1",
		},
		{
			email: "org-b-member-2@scenario-test.example.com",
			password: "",
			displayName: "Org B Member 2",
		},
		{
			email: "org-b-member-3@scenario-test.example.com",
			password: "",
			displayName: "Org B Member 3",
		},
	];

	// ============================================================
	// Phase 1: Organization Setup (Steps 1-9)
	// ============================================================

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
		await expect(page.locator(`text=${tenantCode}`)).toBeVisible({
			timeout: 10_000,
		});
		await expect(page.locator(`text=${tenantName}`)).toBeVisible();

		// Verify status badge shows ACTIVE
		const tenantRow = page.locator(`tr:has-text("${tenantCode}")`);
		await expect(tenantRow).toBeVisible();
		await expect(tenantRow.locator("text=有効")).toBeVisible();
	});

	test("Step 2: sys-admin configures fiscal patterns", async ({ page }) => {
		// Look up the tenant we just created via API
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

		// Verify patterns exist
		expect(fiscalYearPatternId).toBeTruthy();
		expect(monthlyPeriodPatternId).toBeTruthy();
	});

	test("Step 3: sys-admin creates orgs and invites tenant owners", async ({
		page,
	}) => {
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
				data: { fiscalYearPatternId, monthlyPeriodPatternId },
			},
		);
		expect(patternAResponse.status()).toBe(204);

		// Assign fiscal patterns to Org B
		const patternBResponse = await page.request.put(
			`${API_BASE_URL}/api/v1/admin/organizations/${orgBId}/patterns`,
			{
				data: { fiscalYearPatternId, monthlyPeriodPatternId },
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

		// Verify: both tenant owners have credentials
		expect(tenantOwner1.password).toBeTruthy();
		expect(tenantOwner2.password).toBeTruthy();
	});

	test("Step 4: tenant-owner-1 logs in and verifies Org A", async ({
		page,
	}) => {
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

	test("Step 5: tenant-owner-2 logs in and verifies Org B", async ({
		page,
	}) => {
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

	test("Step 8: org-a-admin-1 invites members + assigns manager", async ({
		page,
	}) => {
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
				orgAAdmin1.displayName,
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
			await expect(
				row.locator(`text=${orgAAdmin1.displayName}`),
			).toBeVisible();
		}
	});

	test("Step 9: org-b-admin-1 invites members + assigns manager", async ({
		page,
	}) => {
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
				orgBAdmin1.displayName,
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
			await expect(
				row.locator(`text=${orgBAdmin1.displayName}`),
			).toBeVisible();
		}
	});

	// ============================================================
	// Phase 2: Work Log Entry (Steps 10-11)
	// ============================================================

	test("Step 10: Org A member enters work log", async ({ page }) => {
		await loginAs(page, orgAMembers[0].email, orgAMembers[0].password);

		// Ensure at least one project exists for this tenant (bootstrap via API)
		const projectsResponse = await page.request.get(
			`${API_BASE_URL}/api/v1/organizations/${orgAId}/projects`,
		);
		if (
			!projectsResponse.ok() ||
			(await projectsResponse.json()).length === 0
		) {
			const createResponse = await page.request.post(
				`${API_BASE_URL}/api/v1/admin/projects`,
				{
					data: {
						code: "PROJ-SCENARIO",
						name: "Scenario Test Project",
						organizationId: orgAId,
					},
				},
			);
			expect(createResponse.ok()).toBe(true);
		}

		// Navigate to worklog calendar
		await page.goto("/worklog");
		await page.waitForLoadState("networkidle");

		// Enter work log for 3 days in the current month (2nd, 3rd, 4th)
		await fillWorkLogEntries(page, "8", 3);

		// Verify: calendar shows entries for those days
		await page.goto("/worklog");
		await page.waitForLoadState("networkidle");

		// Check that at least one day shows hours
		await expect(page.locator("text=8.00")).toBeVisible({ timeout: 10_000 });
	});

	test("Step 11: Org B manager proxy-enters work log", async ({ page }) => {
		await loginAs(page, orgBAdmin1.email, orgBAdmin1.password);

		// Navigate to proxy entry page
		await page.goto("/worklog/proxy");
		await page.waitForLoadState("networkidle");

		// Select org-b-member-1 from the dropdown
		const memberSelector = page.locator(
			'select[aria-label="Select Team Member"]',
		);
		await expect(memberSelector).toBeVisible({ timeout: 10_000 });

		// Select by member display name
		await memberSelector.selectOption({
			label: new RegExp(orgBMembers[0].displayName),
		});

		// Click "Enter Time for..." button
		await page.click('button:has-text("Enter Time for")');

		// Verify proxy mode banner is shown
		await expect(page.locator("text=Proxy Mode")).toBeVisible({
			timeout: 10_000,
		});
		await expect(
			page.locator(`text=${orgBMembers[0].displayName}`),
		).toBeVisible();

		// Enter work log for 3 days (same pattern as Step 10)
		await fillWorkLogEntries(page, "7.5", 3);

		// Verify entries are saved
		await expect(page.locator("text=7.50")).toBeVisible({ timeout: 10_000 });
	});

	// ============================================================
	// Phase 3: Monthly Approval (Steps 12-15)
	// ============================================================

	test("Step 12: Org A member submits for approval", async ({ page }) => {
		await loginAs(page, orgAMembers[0].email, orgAMembers[0].password);

		await page.goto("/worklog");
		await page.waitForLoadState("networkidle");

		// Click "Submit for Approval" button
		const submitButton = page.locator(
			'button:has-text("Submit for Approval")',
		);
		await expect(submitButton).toBeVisible({ timeout: 10_000 });
		await expect(submitButton).toBeEnabled();
		await submitButton.click();

		// Handle confirmation dialog if present
		const confirmButton = page.locator('button:has-text("Submit")').last();
		if (
			await confirmButton
				.isVisible({ timeout: 3_000 })
				.catch(() => false)
		) {
			await confirmButton.click();
		}

		// Wait for status to change
		await page.waitForLoadState("networkidle");

		// Verify: button should now show "Submitted" and be disabled
		await expect(
			page.locator('button:has-text("Submitted")'),
		).toBeVisible({ timeout: 10_000 });

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

		// Find the submission from org-a-member-1
		const submissionCard = page
			.locator(`text=${orgAMembers[0].displayName}`)
			.first();
		await expect(submissionCard).toBeVisible({ timeout: 10_000 });

		// Click "Approve" button
		const approveButton = page
			.locator('button:has-text("Approve")')
			.first();
		await expect(approveButton).toBeVisible();
		await approveButton.click();

		// Wait for approval to complete
		await page.waitForLoadState("networkidle");

		// Verify: the submission should no longer appear in the queue
		await expect(
			page.locator(`text=${orgAMembers[0].displayName}`),
		).not.toBeVisible({
			timeout: 10_000,
		});
	});

	test("Step 14: Org B manager proxy-submits for approval", async ({
		page,
	}) => {
		await loginAs(page, orgBAdmin1.email, orgBAdmin1.password);

		// Re-enter proxy mode for org-b-member-1
		await page.goto("/worklog/proxy");
		await page.waitForLoadState("networkidle");

		const memberSelector = page.locator(
			'select[aria-label="Select Team Member"]',
		);
		await memberSelector.selectOption({
			label: new RegExp(orgBMembers[0].displayName),
		});
		await page.click('button:has-text("Enter Time for")');

		// Verify proxy mode is active
		await expect(page.locator("text=Proxy Mode")).toBeVisible({
			timeout: 10_000,
		});

		// Click "Submit for Approval" (proxy submit)
		const submitButton = page.locator(
			'button:has-text("Submit for Approval")',
		);
		await expect(submitButton).toBeVisible({ timeout: 10_000 });
		await submitButton.click();

		// Handle confirmation dialog
		const confirmButton = page.locator('button:has-text("Submit")').last();
		if (
			await confirmButton
				.isVisible({ timeout: 3_000 })
				.catch(() => false)
		) {
			await confirmButton.click();
		}

		await page.waitForLoadState("networkidle");

		// Verify: status should show "Submitted"
		await expect(
			page.locator('button:has-text("Submitted")'),
		).toBeVisible({ timeout: 10_000 });
	});

	test("Step 15: Org B different admin approves", async ({ page }) => {
		await loginAs(page, orgBAdmin2.email, orgBAdmin2.password);

		// Navigate to approval queue
		await page.goto("/worklog/approval");
		await page.waitForLoadState("networkidle");

		// Find the submission from org-b-member-1
		const submissionCard = page
			.locator(`text=${orgBMembers[0].displayName}`)
			.first();
		await expect(submissionCard).toBeVisible({ timeout: 10_000 });

		// Approve
		const approveButton = page
			.locator('button:has-text("Approve")')
			.first();
		await expect(approveButton).toBeVisible();
		await approveButton.click();

		await page.waitForLoadState("networkidle");

		// Verify: submission no longer in queue
		await expect(
			page.locator(`text=${orgBMembers[0].displayName}`),
		).not.toBeVisible({
			timeout: 10_000,
		});
	});
});
