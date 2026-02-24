import { test, expect, type Page } from "@playwright/test";

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

	// Placeholder tests — implement one at a time
	test.fixme("Step 1: sys-admin creates tenant", async ({ page }) => {});
	test.fixme(
		"Step 2: sys-admin configures fiscal patterns",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 3: sys-admin invites tenant owners",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 4: tenant-owner-1 creates Org A",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 5: tenant-owner-2 creates Org B",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 6: tenant-owner-1 invites Org A admins",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 7: tenant-owner-2 invites Org B admins",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 8: org-a-admin-1 invites members + assigns manager",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 9: org-b-admin-1 invites members + assigns manager",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 10: Org A member enters work log",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 11: Org B manager proxy-enters work log",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 12: Org A member submits for approval",
		async ({ page }) => {},
	);
	test.fixme("Step 13: Org A manager approves", async ({ page }) => {});
	test.fixme(
		"Step 14: Org B manager proxy-submits for approval",
		async ({ page }) => {},
	);
	test.fixme(
		"Step 15: Org B different admin approves",
		async ({ page }) => {},
	);
});
