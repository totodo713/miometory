/**
 * E2E Test: MyPage Feature
 *
 * Test Scenario: UserMenu navigation -> Profile display -> Profile edit
 *
 * Success Criteria:
 * - UserMenu dropdown opens and shows "My Page" link
 * - Navigation to /mypage shows profile information
 * - Edit modal opens and allows profile editing
 * - Projects table displays assigned projects
 */

import { expect, test } from "./fixtures/auth";

test.describe("MyPage Feature", () => {
  const mockProfile = {
    id: "00000000-0000-0000-0000-000000000001",
    email: "bob.engineer@miometry.example.com",
    displayName: "Bob Engineer",
    organizationName: "Engineering",
    managerName: "Alice Manager",
    isActive: true,
  };

  const mockProjects = {
    projects: [
      { id: "project-1", code: "PROJ-001", name: "Project Alpha" },
      { id: "project-2", code: "PROJ-002", name: "Project Beta" },
    ],
    count: 2,
  };

  test.beforeEach(async ({ page }) => {
    // Mock profile API
    await page.route("**/api/v1/profile", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(mockProfile),
        });
      } else if (route.request().method() === "PUT") {
        const body = JSON.parse(route.request().postData() || "{}");
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            emailChanged: body.email !== mockProfile.email,
          }),
        });
      }
    });

    // Mock assigned projects API
    await page.route("**/api/v1/members/*/projects", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockProjects),
      });
    });

    // Mock admin context (user role, no admin access)
    await page.route("**/api/v1/admin/context", async (route) => {
      await route.fulfill({
        status: 403,
        contentType: "application/json",
        body: JSON.stringify({ message: "Forbidden" }),
      });
    });
  });

  test("navigates to mypage via UserMenu dropdown", async ({ page }) => {
    await page.goto("/worklog");
    await page.waitForLoadState("networkidle");

    // Click the UserMenu trigger (user name button)
    const userMenuButton = page.getByRole("button", { name: /User menu/ });
    await userMenuButton.click();

    // Verify dropdown shows "My Page" menu item (Link has role="menuitem")
    const myPageLink = page.getByRole("menuitem", { name: "My Page" });
    await expect(myPageLink).toBeVisible();

    // Click "My Page"
    await myPageLink.click();
    await expect(page).toHaveURL(/\/mypage/);
  });

  test("displays profile information on mypage", async ({ page }) => {
    await page.goto("/mypage");
    await page.waitForLoadState("networkidle");

    // Verify profile fields (.first() avoids strict-mode: name appears in UserMenu + profile card)
    await expect(page.getByText("Bob Engineer").first()).toBeVisible();
    await expect(page.getByText("bob.engineer@miometry.example.com")).toBeVisible();
    await expect(page.getByText("Engineering")).toBeVisible();
    await expect(page.getByText("Alice Manager")).toBeVisible();
  });

  test("displays assigned projects table", async ({ page }) => {
    await page.goto("/mypage");
    await page.waitForLoadState("networkidle");

    await expect(page.getByText("PROJ-001")).toBeVisible();
    await expect(page.getByText("Project Alpha")).toBeVisible();
    await expect(page.getByText("PROJ-002")).toBeVisible();
    await expect(page.getByText("Project Beta")).toBeVisible();
  });

  test("opens edit modal and updates display name", async ({ page }) => {
    await page.goto("/mypage");
    await page.waitForLoadState("networkidle");

    // Wait for profile to load
    await expect(page.getByText("Bob Engineer").first()).toBeVisible();

    // Click edit button
    await page.getByText("Edit Profile").click();

    // Verify modal is open
    await expect(page.getByText("Edit Profile", { exact: false })).toBeVisible();

    // Change display name
    const nameInput = page.locator("#edit-displayName");
    await nameInput.clear();
    await nameInput.fill("Bob Updated");

    // Save
    await page.getByRole("button", { name: "Save" }).click();

    // Verify success (modal should close)
    await expect(page.locator("#edit-displayName")).not.toBeVisible();
  });

  test("shows validation errors for empty fields", async ({ page }) => {
    await page.goto("/mypage");
    await page.waitForLoadState("networkidle");

    await expect(page.getByText("Bob Engineer").first()).toBeVisible();
    await page.getByText("Edit Profile").click();

    // Clear both fields
    const nameInput = page.locator("#edit-displayName");
    const emailInput = page.locator("#edit-email");
    await nameInput.clear();
    await emailInput.clear();

    // Click save
    await page.getByRole("button", { name: "Save" }).click();

    // Validation errors should appear
    await expect(page.getByText("Display name is required")).toBeVisible();
    await expect(page.getByText("Email is required")).toBeVisible();
  });

  test("shows email changed dialog and logs out when email is updated", async ({ page }) => {
    await page.goto("/mypage");
    await page.waitForLoadState("networkidle");

    await expect(page.getByText("Bob Engineer").first()).toBeVisible();

    // Open edit modal
    await page.getByText("Edit Profile").click();

    // Change email to a different address
    const emailInput = page.locator("#edit-email");
    await emailInput.clear();
    await emailInput.fill("new@example.com");

    // Save
    await page.getByRole("button", { name: "Save" }).click();

    // Email changed alert dialog should appear
    await expect(page.getByRole("alertdialog")).toBeVisible();
    await expect(page.getByText("Email Changed")).toBeVisible();
    await expect(page.getByText("log in again")).toBeVisible();

    // Click logout button
    await page.getByRole("button", { name: "Log out" }).click();

    // Should navigate to login page
    await expect(page).toHaveURL(/\/login/);
  });
});
