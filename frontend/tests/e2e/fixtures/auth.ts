import { test as base } from "@playwright/test";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

export const test = base.extend({
  page: async ({ page }, use) => {
    // 1. Login API to establish backend session (JSESSIONID)
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
    };

    await page.addInitScript((user) => {
      window.sessionStorage.setItem("miometry_auth_user", JSON.stringify(user));
    }, authUser);

    await use(page);
  },
});

export { expect } from "@playwright/test";

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
