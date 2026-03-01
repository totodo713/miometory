import fs from "node:fs";
import { expect, type Page, test } from "@playwright/test";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

// --- Auth Helper (same pattern as tenant-onboarding.spec.ts) ---

async function loginAs(page: Page, email: string, password: string): Promise<void> {
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
  const authUser = {
    id: body.user.id,
    email: body.user.email,
    displayName: body.user.name,
    memberId: body.user.memberId ?? undefined,
  };

  await page.addInitScript((user) => {
    window.sessionStorage.setItem("miometry_auth_user", JSON.stringify(user));
  }, authUser);
}

// --- API Mock Helper (auth.ts pattern) ---

async function mockGlobalApis(page: Page) {
  await page.route("**/api/v1/notifications**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ content: [], unreadCount: 0, totalElements: 0, totalPages: 0 }),
    });
  });

  await page.route("**/api/v1/worklog/approvals/member/**", async (route) => {
    await route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ message: "No approval found" }),
    });
  });

  await page.route("**/api/v1/worklog/rejections/daily**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ rejections: [] }),
    });
  });
}

// --- CSV Builders ---

function buildValidCsv(runId: string, count: number): string {
  const header = "email,displayName";
  const rows = Array.from(
    { length: count },
    (_, i) => `csv-test-${runId}-${i + 1}@e2e-test.example.com,CSV Test User ${i + 1}`,
  );
  return [header, ...rows].join("\n");
}

function buildInvalidCsv(runId: string): string {
  const header = "email,displayName";
  return [
    header,
    ",Missing Email User", // missing email
    "not-an-email,Bad Email Format", // invalid email
    `duplicate-${runId}@e2e-test.example.com,`, // missing displayName
  ].join("\n");
}

function buildMixedCsv(runId: string): string {
  const header = "email,displayName";
  return [
    header,
    `csv-mixed-${runId}-1@e2e-test.example.com,Mixed Valid User 1`, // valid
    ",Missing Email User", // invalid: missing email
    `csv-mixed-${runId}-2@e2e-test.example.com,Mixed Valid User 2`, // valid
  ].join("\n");
}

// --- Test Suite ---

test.describe
  .serial("Member CSV Import: Full Wizard Flow", () => {
    test.describe.configure({ timeout: 60_000 });

    test.beforeEach(({ browserName }) => {
      test.skip(browserName !== "chromium" && !process.env.CI, "Skipping non-Chromium browsers locally");
    });

    const runId = Date.now().toString().slice(-6);

    // David Independent: TENANT_ADMIN, has member.create permission
    const ADMIN_EMAIL = "david.independent@miometry.example.com";
    const ADMIN_PASSWORD = "Password1";

    test("navigates from member list to CSV Import page", async ({ page }) => {
      await loginAs(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await mockGlobalApis(page);

      await page.goto("/admin/members");
      await page.waitForLoadState("networkidle");

      // Click "CSV Import" link
      const csvImportLink = page.getByRole("link", { name: "CSV Import" });
      await expect(csvImportLink).toBeVisible({ timeout: 10_000 });
      await csvImportLink.click();

      // Verify navigation
      await page.waitForURL("**/admin/members/import");

      // Verify page title and key UI elements
      await expect(page.getByRole("heading", { name: "Member CSV Import" })).toBeVisible({ timeout: 10_000 });
      await expect(page.getByText("Download Template")).toBeVisible();
      await expect(page.getByLabel("Organization")).toBeVisible();
      await expect(page.getByRole("button", { name: "Validate (Dry-run)" })).toBeVisible();
    });

    test("downloads CSV template", async ({ page }) => {
      await loginAs(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await mockGlobalApis(page);

      await page.goto("/admin/members/import");
      await page.waitForLoadState("networkidle");
      await expect(page.getByText("Download Template")).toBeVisible({ timeout: 10_000 });

      // downloadMemberCsvTemplate() uses downloadBlob (dynamic <a> click),
      // which still triggers Playwright's download event
      const downloadPromise = page.waitForEvent("download");
      await page.getByText("Download Template").click();
      const download = await downloadPromise;

      // Verify filename
      expect(download.suggestedFilename()).toBe("member-import-template.csv");

      // Verify content contains the expected header
      const filePath = await download.path();
      expect(filePath).toBeTruthy();
      const content = fs.readFileSync(filePath!, "utf-8");
      expect(content).toContain("email");
      expect(content).toContain("displayName");
    });

    test("uploads valid CSV, runs dry-run, imports, and downloads result CSV", async ({ page }) => {
      await loginAs(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await mockGlobalApis(page);

      await page.goto("/admin/members/import");
      await page.waitForLoadState("networkidle");

      // 1. Select the first organization from the dropdown
      const orgSelect = page.getByLabel("Organization");
      await expect(orgSelect).toBeVisible({ timeout: 10_000 });
      // Wait for org options to load (not just the placeholder)
      await expect(orgSelect.locator("option")).not.toHaveCount(1, { timeout: 10_000 });
      // Select the first real option (index 1, skipping placeholder)
      const firstOption = orgSelect.locator("option").nth(1);
      const firstOptionValue = await firstOption.getAttribute("value");
      expect(firstOptionValue).toBeTruthy();
      await orgSelect.selectOption(firstOptionValue!);

      // 2. Upload a valid CSV with 3 rows
      const csvContent = buildValidCsv(runId, 3);
      const fileInput = page.locator('input[type="file"]');
      await fileInput.setInputFiles({
        name: "test-members.csv",
        mimeType: "text/csv",
        buffer: Buffer.from(csvContent),
      });

      // 3. Click "Validate (Dry-run)" and wait for API response
      const dryRunResponsePromise = page.waitForResponse(
        (resp) => resp.url().includes("/api/v1/admin/members/csv/dry-run") && resp.request().method() === "POST",
      );
      await page.getByRole("button", { name: "Validate (Dry-run)" }).click();
      const dryRunResponse = await dryRunResponsePromise;
      expect(dryRunResponse.ok()).toBe(true);

      // 4. Verify dry-run results: summary and no errors
      await expect(page.getByText("Total: 3 rows")).toBeVisible({ timeout: 10_000 });
      await expect(page.getByText("No errors found")).toBeVisible();

      // 5. Click "Proceed to Import (3 rows)"
      const proceedButton = page.getByRole("button", { name: /Proceed to Import/ });
      await expect(proceedButton).toBeVisible();
      await expect(proceedButton).toBeEnabled();

      // Capture download asynchronously (downloadBlob fires before onComplete)
      let resultDownload: { suggestedFilename(): string; path(): Promise<string | null> } | null = null;
      page.on("download", (d) => {
        resultDownload = d;
      });

      await proceedButton.click();

      // 6. Wait for completion message (primary assertion — confirms import + download happened)
      await expect(page.getByText("Import complete. 3 members imported.")).toBeVisible({ timeout: 45_000 });

      // 7. Verify result CSV download (downloadBlob fires before completion callback)
      expect(resultDownload).not.toBeNull();
      expect(resultDownload!.suggestedFilename()).toBe("member-import-result.csv");

      // 8. Verify result CSV content
      const filePath = await resultDownload!.path();
      expect(filePath).toBeTruthy();
      const resultContent = fs.readFileSync(filePath!, "utf-8");
      const resultLines = resultContent.trim().split("\n");
      // Header + 3 data rows
      expect(resultLines.length).toBeGreaterThanOrEqual(4);
      // Each data row should have SUCCESS status
      for (const line of resultLines.slice(1)) {
        expect(line).toContain("SUCCESS");
      }
    });

    test("verifies imported members appear in the member list", async ({ page }) => {
      await loginAs(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await mockGlobalApis(page);

      await page.goto("/admin/members");
      await page.waitForLoadState("networkidle");

      // Search for the test members using the runId prefix
      const searchInput = page.getByPlaceholder("Search by name or email");
      await expect(searchInput).toBeVisible({ timeout: 10_000 });
      await searchInput.fill(`csv-test-${runId}`);

      // Wait for filtered results
      await page.waitForLoadState("networkidle");

      // Verify 3 imported members are shown
      for (let i = 1; i <= 3; i++) {
        await expect(page.getByText(`csv-test-${runId}-${i}@e2e-test.example.com`)).toBeVisible({ timeout: 10_000 });
      }
    });

    test("shows validation errors for all-invalid CSV", async ({ page }) => {
      await loginAs(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await mockGlobalApis(page);

      await page.goto("/admin/members/import");
      await page.waitForLoadState("networkidle");

      // Select organization
      const orgSelect = page.getByLabel("Organization");
      await expect(orgSelect).toBeVisible({ timeout: 10_000 });
      await expect(orgSelect.locator("option")).not.toHaveCount(1, { timeout: 10_000 });
      const firstOption = orgSelect.locator("option").nth(1);
      const firstOptionValue = await firstOption.getAttribute("value");
      await orgSelect.selectOption(firstOptionValue!);

      // Upload invalid CSV
      const csvContent = buildInvalidCsv(runId);
      const fileInput = page.locator('input[type="file"]');
      await fileInput.setInputFiles({
        name: "invalid-members.csv",
        mimeType: "text/csv",
        buffer: Buffer.from(csvContent),
      });

      // Run dry-run
      await page.getByRole("button", { name: "Validate (Dry-run)" }).click();

      // Verify error table is shown
      await expect(page.getByText(/Total: 3 rows/)).toBeVisible({ timeout: 10_000 });

      // Verify "No valid rows" warning
      await expect(page.getByText("No valid rows to import")).toBeVisible();

      // Verify "Proceed" button is disabled
      const proceedButton = page.getByRole("button", { name: /Proceed to Import/ });
      await expect(proceedButton).toBeDisabled();
    });

    test("handles mixed valid/invalid CSV with partial import", async ({ page }) => {
      await loginAs(page, ADMIN_EMAIL, ADMIN_PASSWORD);
      await mockGlobalApis(page);

      await page.goto("/admin/members/import");
      await page.waitForLoadState("networkidle");

      // Select organization
      const orgSelect = page.getByLabel("Organization");
      await expect(orgSelect).toBeVisible({ timeout: 10_000 });
      await expect(orgSelect.locator("option")).not.toHaveCount(1, { timeout: 10_000 });
      const firstOption = orgSelect.locator("option").nth(1);
      const firstOptionValue = await firstOption.getAttribute("value");
      await orgSelect.selectOption(firstOptionValue!);

      // Upload mixed CSV (2 valid + 1 invalid)
      const csvContent = buildMixedCsv(runId);
      const fileInput = page.locator('input[type="file"]');
      await fileInput.setInputFiles({
        name: "mixed-members.csv",
        mimeType: "text/csv",
        buffer: Buffer.from(csvContent),
      });

      // Run dry-run
      await page.getByRole("button", { name: "Validate (Dry-run)" }).click();

      // Verify summary: Total 3 rows, 2 valid, 1 error
      await expect(page.getByText("Total: 3 rows")).toBeVisible({ timeout: 10_000 });
      await expect(page.getByText(/Valid: 2/)).toBeVisible();
      await expect(page.getByText(/Errors: 1/)).toBeVisible();

      // Click "Proceed to Import (2 rows)"
      const proceedButton = page.getByRole("button", { name: /Proceed to Import \(2 rows\)/ });
      await expect(proceedButton).toBeVisible();
      await expect(proceedButton).toBeEnabled();

      await proceedButton.click();

      // Verify completion message
      await expect(page.getByText("Import complete. 2 members imported.")).toBeVisible({ timeout: 45_000 });
    });
  });
