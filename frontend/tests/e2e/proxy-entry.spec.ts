/**
 * E2E Test: Proxy Entry Workflow (T162)
 *
 * Test Scenario: Manager navigates to proxy mode → Selects subordinate →
 *                Enters time on behalf of subordinate → Verifies audit trail
 *
 * Success Criteria (SC-010):
 * - Manager can access proxy entry page
 * - Manager can select from list of subordinates
 * - Proxy mode banner is displayed when active
 * - Entries created have enteredBy = manager, memberId = subordinate
 * - Calendar shows proxy entry indicator icon
 * - Manager can exit proxy mode and return to their own timesheet
 */

import { expect, test } from "@playwright/test";

test.describe("Proxy Entry Workflow", () => {
  const baseURL = "http://localhost:3000";
  const managerId = "00000000-0000-0000-0000-000000000001";
  const subordinateId = "00000000-0000-0000-0000-000000000002";
  const subordinateName = "Test Engineer";
  const managerName = "Test Manager";

  test.beforeEach(async ({ page }) => {
    // Mock member API - get manager
    await page.route(`**/api/v1/members/${managerId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: managerId,
          email: "manager@test.com",
          displayName: managerName,
          managerId: null,
          isActive: true,
        }),
      });
    });

    // Mock member API - get subordinate
    await page.route(`**/api/v1/members/${subordinateId}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: subordinateId,
          email: "engineer@test.com",
          displayName: subordinateName,
          managerId: managerId,
          isActive: true,
        }),
      });
    });

    // Mock subordinates API
    await page.route(
      `**/api/v1/members/${managerId}/subordinates**`,
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            subordinates: [
              {
                id: subordinateId,
                email: "engineer@test.com",
                displayName: subordinateName,
                managerId: managerId,
                isActive: true,
              },
            ],
            total: 1,
            recursive: false,
          }),
        });
      },
    );

    // Mock can-proxy API
    await page.route(
      `**/api/v1/members/${managerId}/can-proxy/${subordinateId}`,
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            canProxy: true,
            reason: "Manager can enter time for this subordinate",
          }),
        });
      },
    );

    // Mock calendar API for manager
    await page.route(
      `**/api/v1/worklog/calendar/**memberId=${managerId}**`,
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            memberId: managerId,
            memberName: managerName,
            periodStart: "2025-12-21",
            periodEnd: "2026-01-20",
            dates: Array.from({ length: 31 }, (_, i) => ({
              date: `2026-01-${String(i + 1).padStart(2, "0")}`,
              totalWorkHours: 0,
              totalAbsenceHours: 0,
              status: "DRAFT",
              isWeekend: [6, 0].includes(new Date(2026, 0, i + 1).getDay()),
              isHoliday: false,
              hasProxyEntries: false,
            })),
          }),
        });
      },
    );

    // Mock calendar API for subordinate (with proxy entry indicator)
    await page.route(
      `**/api/v1/worklog/calendar/**memberId=${subordinateId}**`,
      async (route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            memberId: subordinateId,
            memberName: subordinateName,
            periodStart: "2025-12-21",
            periodEnd: "2026-01-20",
            dates: Array.from({ length: 31 }, (_, i) => ({
              date: `2026-01-${String(i + 1).padStart(2, "0")}`,
              totalWorkHours: i === 14 ? 8 : 0, // Day 15 has proxy entry
              totalAbsenceHours: 0,
              status: i === 14 ? "DRAFT" : "DRAFT",
              isWeekend: [6, 0].includes(new Date(2026, 0, i + 1).getDay()),
              isHoliday: false,
              hasProxyEntries: i === 14, // Day 15 has proxy entry indicator
            })),
          }),
        });
      },
    );

    // Mock get entries API (initially empty for subordinate)
    await page.route("**/api/v1/worklog/entries?**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          entries: [],
          total: 0,
        }),
      });
    });

    // Mock get absences API
    await page.route("**/api/v1/absences?**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          absences: [],
          total: 0,
        }),
      });
    });

    // Mock create entry API - capture for verification
    await page.route("**/api/v1/worklog/entries", async (route) => {
      if (route.request().method() === "POST") {
        const requestBody = route.request().postDataJSON();
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify({
            id: `entry-${Date.now()}`,
            ...requestBody,
            status: "DRAFT",
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            version: 1,
          }),
        });
      } else {
        await route.continue();
      }
    });
  });

  test("manager can navigate to proxy entry page and see subordinates", async ({
    page,
  }) => {
    // Step 1: Navigate to worklog page
    await page.goto(`${baseURL}/worklog?memberId=${managerId}`, {
      waitUntil: "networkidle",
    });

    // Step 2: Click on "Enter Time for Team" button
    const proxyButton = page.getByRole("link", {
      name: /Enter Time for Team/i,
    });
    await expect(proxyButton).toBeVisible();
    await proxyButton.click();

    // Step 3: Verify navigation to proxy page
    await expect(page).toHaveURL(/\/worklog\/proxy/);

    // Step 4: Verify subordinate is listed
    await expect(page.getByText(subordinateName)).toBeVisible();
  });

  test("manager can enable proxy mode and see proxy banner", async ({
    page,
  }) => {
    // Navigate to proxy page
    await page.goto(`${baseURL}/worklog/proxy?memberId=${managerId}`, {
      waitUntil: "networkidle",
    });

    // Select subordinate from dropdown or list
    const subordinateOption = page.getByRole("button", {
      name: new RegExp(subordinateName, "i"),
    });
    await expect(subordinateOption).toBeVisible();
    await subordinateOption.click();

    // Should redirect back to worklog with proxy mode enabled
    await expect(page).toHaveURL(/\/worklog/);

    // Verify proxy banner is displayed
    const proxyBanner = page.getByText(/Entering time as:/i);
    await expect(proxyBanner).toBeVisible();
    await expect(page.getByText(subordinateName)).toBeVisible();
  });

  test("manager can exit proxy mode", async ({ page }) => {
    // Navigate to worklog in proxy mode
    await page.goto(`${baseURL}/worklog/proxy?memberId=${managerId}`, {
      waitUntil: "networkidle",
    });

    // Select subordinate
    const subordinateOption = page.getByRole("button", {
      name: new RegExp(subordinateName, "i"),
    });
    await subordinateOption.click();

    // Verify in proxy mode
    await expect(page.getByText(/Entering time as:/i)).toBeVisible();

    // Click exit proxy mode button
    const exitButton = page.getByRole("button", { name: /Exit Proxy Mode/i });
    await expect(exitButton).toBeVisible();
    await exitButton.click();

    // Verify proxy banner is no longer visible
    await expect(page.getByText(/Entering time as:/i)).not.toBeVisible();
  });

  test("proxy entry form shows 'Entering as' banner", async ({ page }) => {
    // Navigate to worklog in proxy mode (simulate via URL params)
    // Note: In real E2E this would go through the proxy selection flow
    await page.goto(`${baseURL}/worklog/proxy?memberId=${managerId}`, {
      waitUntil: "networkidle",
    });

    // Select subordinate to enter proxy mode
    const subordinateOption = page.getByRole("button", {
      name: new RegExp(subordinateName, "i"),
    });
    await subordinateOption.click();

    // Wait for worklog page with proxy mode
    await page.waitForURL(/\/worklog/);

    // Click on a date to open daily entry form
    const dateButton = page.getByRole("button", { name: /January 15/ });
    await dateButton.click();

    // Verify the form shows proxy entry banner
    await expect(page.getByText(/Entering time as:/i)).toBeVisible();
    await expect(
      page.getByText(/This entry will be recorded on behalf of/i),
    ).toBeVisible();
  });

  test("calendar shows proxy entry indicator for entries made by manager", async ({
    page,
  }) => {
    // Navigate to subordinate's worklog (which has proxy entries from mock)
    await page.goto(`${baseURL}/worklog?memberId=${subordinateId}`, {
      waitUntil: "networkidle",
    });

    // The mock data has hasProxyEntries=true for Jan 15
    // Find the calendar cell for that day and verify it has proxy indicator
    const day15Cell = page.getByRole("button", { name: /January 15/ });
    await expect(day15Cell).toBeVisible();

    // The proxy indicator should be visible (using person icon or similar)
    // Check for the proxy indicator icon within the cell
    const proxyIndicator = day15Cell.locator('[title*="manager"]');
    await expect(proxyIndicator).toBeVisible();
  });
});
