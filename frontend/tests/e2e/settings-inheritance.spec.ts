/** E2E Test: Settings Inheritance (System > Tenant > Organization) */

import { expect, type Page, test } from "@playwright/test";

// --- Setup helpers (no real backend needed — all APIs mocked) ---

async function setupMockedPage(page: Page) {
  // Force English locale
  await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

  // Mock login endpoint
  await page.route("**/api/v1/auth/login", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        user: {
          id: "user-001",
          email: "admin@test.com",
          name: "Test Admin",
          memberId: "member-001",
        },
      }),
    });
  });

  // Mock global APIs (notifications, approvals, rejections)
  await page.route("**/api/v1/notifications**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ content: [], unreadCount: 0, totalElements: 0, totalPages: 0 }),
    });
  });
  await page.route("**/api/v1/worklog/approvals/member/**", async (route) => {
    await route.fulfill({ status: 404, contentType: "application/json", body: '{"message":"No approval found"}' });
  });
  await page.route("**/api/v1/worklog/rejections/daily**", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: '{"rejections":[]}' });
  });

  // Mock auth session check
  await page.route("**/api/v1/auth/session", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        user: { id: "user-001", email: "admin@test.com", name: "Test Admin", memberId: "member-001" },
      }),
    });
  });

  // Mock admin context — SYSTEM_ADMIN with full permissions
  await page.route("**/api/v1/admin/context", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        role: "SYSTEM_ADMIN",
        permissions: [
          "system_settings.view",
          "system_settings.update",
          "tenant.view",
          "tenant.create",
          "tenant.update",
          "organization.view",
          "organization.create",
          "organization.update",
          "fiscal_year_pattern.view",
          "monthly_period_pattern.view",
          "member.view",
        ],
        tenantId: "tenant-001",
        tenantName: "Test Tenant",
        memberId: "member-001",
      }),
    });
  });

  // Set sessionStorage for AuthProvider (requires a real origin)
  await page.goto("/");
  await page.waitForLoadState("domcontentloaded");
  await page.evaluate(() => {
    window.sessionStorage.setItem(
      "miometry_auth_user",
      JSON.stringify({
        id: "user-001",
        email: "admin@test.com",
        displayName: "Test Admin",
        memberId: "member-001",
      }),
    );
  });
}

test.describe("System Settings Page", () => {
  test.beforeEach(async ({ page }) => {
    await setupMockedPage(page);

    // Mock GET/PUT system settings patterns
    await page.route("**/api/v1/admin/system/settings/patterns", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            fiscalYearStartMonth: 4,
            fiscalYearStartDay: 1,
            monthlyPeriodStartDay: 1,
          }),
        });
      } else if (route.request().method() === "PUT") {
        await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
      }
    });
  });

  test("should display current system default patterns", async ({ page }) => {
    await page.goto("/admin/settings");
    await page.waitForLoadState("networkidle");

    // Verify page title
    await expect(page.getByRole("heading", { name: "System Settings" })).toBeVisible();

    // Verify loaded values in select elements
    await expect(page.locator("#fyStartMonth")).toHaveValue("4");
    await expect(page.locator("#fyStartDay")).toHaveValue("1");
    await expect(page.locator("#mpStartDay")).toHaveValue("1");
  });

  test("should save updated system default patterns", async ({ page }) => {
    await page.goto("/admin/settings");
    await page.waitForLoadState("networkidle");

    // Wait for form to load
    await expect(page.locator("#fyStartMonth")).toHaveValue("4");

    // Change fiscal year start month to October
    await page.locator("#fyStartMonth").selectOption("10");

    // Change monthly period start day to 21
    await page.locator("#mpStartDay").selectOption("21");

    // Intercept the PUT request
    const putPromise = page.waitForRequest(
      (req) =>
        req.url().includes("/api/v1/admin/system/settings/patterns") && req.method() === "PUT",
    );

    // Click save
    await page.getByRole("button", { name: "Save" }).click();

    // Verify request was sent with correct data
    const putRequest = await putPromise;
    const body = JSON.parse(putRequest.postData() || "{}");
    expect(body.fiscalYearStartMonth).toBe(10);
    expect(body.fiscalYearStartDay).toBe(1);
    expect(body.monthlyPeriodStartDay).toBe(21);
  });
});

test.describe("Organization Effective Patterns Display", () => {
  const orgId = "org-001";
  const tenantId = "tenant-001";

  test.beforeEach(async ({ page }) => {
    await setupMockedPage(page);

    const orgData = {
      id: orgId,
      tenantId,
      parentId: null,
      parentName: null,
      code: "ROOT",
      name: "Root Organization",
      level: 1,
      status: "ACTIVE",
      memberCount: 0,
      fiscalYearPatternId: null,
      monthlyPeriodPatternId: null,
      createdAt: "2024-01-01T00:00:00",
      updatedAt: "2024-01-01T00:00:00",
    };

    // Mock organizations list (paginated)
    await page.route("**/api/v1/admin/organizations?**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [orgData],
          totalElements: 1,
          totalPages: 1,
          number: 0,
        }),
      });
    });
    // Also match without query params
    await page.route(/\/api\/v1\/admin\/organizations$/, async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            content: [orgData],
            totalElements: 1,
            totalPages: 1,
            number: 0,
          }),
        });
      }
    });

    // Mock organization tree
    await page.route("**/api/v1/admin/organizations/tree**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([{ ...orgData, children: [] }]),
      });
    });

    // Mock pattern lists
    await page.route("**/api/v1/admin/fiscal-year-patterns**", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });
    await page.route("**/api/v1/admin/monthly-period-patterns**", async (route) => {
      await route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });

    // Mock members (paginated)
    await page.route(`**/api/v1/admin/organizations/${orgId}/members**`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0 }),
      });
    });
  });

  test("should show system default as inheritance source", async ({ page }) => {
    // Mock effective patterns — system default
    await page.route(`**/api/v1/admin/organizations/${orgId}/effective-patterns`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          fiscalYearPatternId: null,
          fiscalYearSource: "system",
          monthlyPeriodPatternId: null,
          monthlyPeriodSource: "system",
        }),
      });
    });

    await page.goto("/admin/organizations");
    await page.waitForLoadState("networkidle");

    // Select the organization
    await page.getByText("Root Organization").click();
    await page.waitForLoadState("networkidle");

    // Verify effective patterns section shows system default
    await expect(page.getByText("Effective Patterns")).toBeVisible();
    await expect(page.getByText("System default").first()).toBeVisible();
  });

  test("should show tenant default as inheritance source", async ({ page }) => {
    // Mock effective patterns — tenant default
    await page.route(`**/api/v1/admin/organizations/${orgId}/effective-patterns`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          fiscalYearPatternId: "fy-pattern-001",
          fiscalYearSource: "tenant",
          monthlyPeriodPatternId: "mp-pattern-001",
          monthlyPeriodSource: "tenant",
        }),
      });
    });

    await page.goto("/admin/organizations");
    await page.waitForLoadState("networkidle");

    await page.getByText("Root Organization").click();
    await page.waitForLoadState("networkidle");

    await expect(page.getByText("Effective Patterns")).toBeVisible();
    await expect(page.getByText("Tenant default").first()).toBeVisible();
  });
});
