import { test as base, type Page } from "@playwright/test";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

/** Mock polling/background APIs to prevent flaky timeouts in tests.
 *  admin/context is NOT mocked — it needs the real session to return
 *  a valid memberId, permissions, and tenantId for pages to render. */
export async function mockGlobalApis(page: Page) {
  // NotificationBell → api.notification.list() (30s polling)
  await page.route("**/api/v1/notifications**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        content: [],
        unreadCount: 0,
        totalElements: 0,
        totalPages: 0,
      }),
    });
  });

  // useRejectionStatus → api.approval.getMemberApproval()
  await page.route("**/api/v1/worklog/approvals/member/**", async (route) => {
    await route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ message: "No approval found" }),
    });
  });

  // useRejectionStatus → api.worklog.getDailyRejections()
  await page.route("**/api/v1/worklog/rejections/daily**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ rejections: [] }),
    });
  });
}

export const test = base.extend({
  page: async ({ page }, use) => {
    // 0. Force English locale (NEXT_LOCALE cookie = Priority 1 in i18n/request.ts)
    await page.context().addCookies([{ name: "NEXT_LOCALE", value: "en", domain: "localhost", path: "/" }]);

    // 1. Mock globally-called APIs (header, notifications, approval status)
    await mockGlobalApis(page);

    // 2. Login API to establish backend session (JSESSIONID)
    const loginResponse = await page.request.post(`${API_BASE_URL}/api/v1/auth/login`, {
      data: {
        email: process.env.E2E_TEST_EMAIL || "bob.engineer@miometry.example.com",
        password: process.env.E2E_TEST_PASSWORD || "Password1",
        rememberMe: false,
      },
    });

    if (!loginResponse.ok()) {
      throw new Error(`Auth fixture login failed: ${loginResponse.status()}`);
    }

    const body = await loginResponse.json();

    // 2. Set sessionStorage for frontend AuthProvider
    const authUser = {
      id: body.user.id,
      email: body.user.email,
      displayName: body.user.name,
      memberId: body.user.memberId ?? undefined,
    };

    await page.addInitScript((user) => {
      window.sessionStorage.setItem("miometry_auth_user", JSON.stringify(user));
    }, authUser);

    await use(page);
  },
});

export { expect } from "@playwright/test";

/** Select a project from the ProjectSelector combobox */
export async function selectProject(page: Page, index: number, projectName: string) {
  const combobox = page.locator(`#project-${index}`);
  await combobox.fill(projectName);
  await page.locator('[role="option"]').filter({ hasText: projectName }).first().click();
}

/** Mock the projects API to prevent timeouts in tests */
export async function mockProjectsApi(
  page: import("@playwright/test").Page,
  projects = [
    { id: "project-1", code: "PROJ-001", name: "Project Alpha" },
    { id: "project-2", code: "PROJ-002", name: "Project Beta" },
  ],
) {
  await page.route("**/api/v1/members/*/projects", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ projects, count: projects.length }),
    });
  });
}
