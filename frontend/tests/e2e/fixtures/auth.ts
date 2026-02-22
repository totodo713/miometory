import { test as base } from "@playwright/test";

const TEST_USER = {
  id: "00000000-0000-0000-0000-000000000001",
  email: "bob@example.com",
  displayName: "Test Engineer",
};

export const test = base.extend({
  page: async ({ page }, use) => {
    await page.addInitScript((user) => {
      window.sessionStorage.setItem("miometry_auth_user", JSON.stringify(user));
    }, TEST_USER);
    await use(page);
  },
});

export { expect } from "@playwright/test";
