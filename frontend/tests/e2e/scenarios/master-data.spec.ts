import { expect, type Page, test } from "@playwright/test";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// --- Auth Helper (same pattern as tenant-onboarding.spec.ts) ---

async function loginAs(page: Page, email: string, password: string) {
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

  await page.addInitScript(
    (user) => {
      window.sessionStorage.setItem("miometry_auth_user", JSON.stringify(user));
    },
    {
      id: body.user.id,
      email: body.user.email,
      displayName: body.user.name,
      memberId: body.user.memberId ?? undefined,
    },
  );
}

// --- Tests ---

test.describe("Master Data Management", () => {
  test.describe("Access Control", () => {
    test("tenant admin cannot access master data page", async ({ page }) => {
      // Login as tenant admin (bob.engineer has TENANT_ADMIN or lower role)
      await loginAs(page, "bob.engineer@miometry.example.com", "Password1");
      await page.goto("/admin/master-data");
      await page.waitForLoadState("networkidle");

      // Should show Access Denied or redirect — not the master data UI
      await expect(page.getByText("Access Denied").or(page.getByText("Forbidden"))).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe("Fiscal Year Preset CRUD", () => {
    test.beforeEach(async ({ page }) => {
      await loginAs(page, "sysadmin@miometry.example.com", "Password1");
    });

    test("can create and edit a fiscal year preset", async ({ page }) => {
      await page.goto("/admin/master-data");
      await page.waitForLoadState("networkidle");

      // Verify we're on the fiscal year tab (default)
      await expect(page.getByRole("tab", { name: /Fiscal Year/i })).toHaveAttribute("aria-selected", "true");

      // Create a new preset
      const uniqueName = `E2E Test FY ${Date.now()}`;
      await page.getByRole("button", { name: /Create Fiscal Year/i }).click();

      // Modal should be visible
      const dialog = page.getByRole("dialog");
      await expect(dialog).toBeVisible();

      // Fill the form
      await dialog.locator("#fy-preset-name").fill(uniqueName);
      await dialog.locator("#fy-preset-start-month").selectOption("4");
      await dialog.locator("#fy-preset-start-day").fill("1");

      // Submit
      await dialog.getByRole("button", { name: /Create/i }).click();

      // Modal should close
      await expect(dialog).not.toBeVisible({ timeout: 5000 });

      // New preset should appear in the list
      await expect(page.getByText(uniqueName)).toBeVisible({ timeout: 5000 });

      // Edit the preset
      const row = page.getByText(uniqueName).locator("..");
      await row.getByRole("button", { name: /Edit/i }).first().click();

      // Edit modal should open
      const editDialog = page.getByRole("dialog");
      await expect(editDialog).toBeVisible();

      const updatedName = `${uniqueName} Updated`;
      await editDialog.locator("#fy-preset-name").clear();
      await editDialog.locator("#fy-preset-name").fill(updatedName);
      await editDialog.getByRole("button", { name: /Update/i }).click();

      // Modal should close and updated name should appear
      await expect(editDialog).not.toBeVisible({ timeout: 5000 });
      await expect(page.getByText(updatedName)).toBeVisible({ timeout: 5000 });
    });
  });

  test.describe("Holiday Calendar", () => {
    test.beforeEach(async ({ page }) => {
      await loginAs(page, "sysadmin@miometry.example.com", "Password1");
    });

    test("can expand calendar and manage entries", async ({ page }) => {
      await page.goto("/admin/master-data");
      await page.waitForLoadState("networkidle");

      // Switch to Holiday Calendar tab
      await page.getByRole("tab", { name: /Holiday Calendar/i }).click();
      await expect(page.getByRole("tab", { name: /Holiday Calendar/i })).toHaveAttribute("aria-selected", "true");

      // Wait for list to load — there should be seed data (Japan Public Holidays)
      await expect(page.getByText("Japan Public Holidays").or(page.getByText("日本の祝日"))).toBeVisible({
        timeout: 10000,
      });

      // Expand the calendar
      const expandButton = page.getByRole("button", { name: /Show entries|エントリを表示/i }).first();
      await expect(expandButton).toHaveAttribute("aria-expanded", "false");
      await expandButton.click();

      // Button should now be expanded
      await expect(
        expandButton.or(page.getByRole("button", { name: /Hide entries|エントリを非表示/i }).first()),
      ).toHaveAttribute("aria-expanded", "true");

      // Entries should be visible (seed data has at least "New Year's Day" / "元日")
      await expect(page.getByText("New Year's Day").or(page.getByText("元日"))).toBeVisible({ timeout: 5000 });
    });
  });
});
