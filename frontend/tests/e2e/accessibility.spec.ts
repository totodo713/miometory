/**
 * E2E Test: Accessibility (T178)
 *
 * Test Scenario: WCAG 2.1 AA Compliance Checks
 *
 * Success Criteria:
 * - All interactive elements have proper ARIA labels
 * - Modal dialogs are properly announced
 * - Color contrast meets WCAG AA standards
 * - Keyboard navigation works throughout
 * - Focus management is correct
 */

import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "./fixtures/auth";

test.describe("Accessibility - WCAG 2.1 AA Compliance", () => {
  const memberId = "00000000-0000-0000-0000-000000000001";

  test.beforeEach(async ({ page }) => {
    // Mock the API endpoints
    await page.route("**/api/v1/worklog/calendar/**", async (route) => {
      // Skip summary endpoint - it has its own mock
      if (route.request().url().includes("/summary")) {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          memberId: memberId,
          memberName: "Test Engineer",
          periodStart: "2026-01-01",
          periodEnd: "2026-01-31",
          dates: Array.from({ length: 31 }, (_, i) => ({
            date: `2026-01-${String(i + 1).padStart(2, "0")}`,
            totalWorkHours: i === 14 ? 8 : 0,
            totalAbsenceHours: 0,
            status: "DRAFT",
            isWeekend: [6, 0].includes(new Date(2026, 0, i + 1).getDay()),
            isHoliday: false,
          })),
        }),
      });
    });

    // Mock monthly summary API
    await page.route("**/api/v1/worklog/calendar/**/summary**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          year: 2026,
          month: 1,
          totalWorkHours: 8,
          totalAbsenceHours: 0,
          totalBusinessDays: 22,
          projects: [],
          approvalStatus: null,
          rejectionReason: null,
        }),
      });
    });

    await page.route("**/api/v1/worklog/entries**", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ entries: [], total: 0 }),
        });
      } else {
        await route.continue();
      }
    });

    await page.route("**/api/v1/absences**", async (route) => {
      if (route.request().method() === "GET") {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ absences: [], total: 0 }),
        });
      } else {
        await route.continue();
      }
    });

    await page.route("**/api/v1/projects**", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          { id: "proj-1", code: "PROJ-001", name: "Main Project" },
          { id: "proj-2", code: "PROJ-002", name: "Support Project" },
        ]),
      });
    });
  });

  test("calendar view has no accessibility violations", async ({ page }) => {
    await page.goto(`/worklog?memberId=${memberId}`);
    await page.waitForLoadState("networkidle");

    const accessibilityScanResults = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test("daily entry form has no accessibility violations", async ({ page }) => {
    // Navigate directly to daily entry form (clicking date navigates, not opens dialog)
    await page.goto(`/worklog/2026-01-15`);
    await page.waitForLoadState("networkidle");

    const accessibilityScanResults = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test("all buttons have accessible names", async ({ page }) => {
    await page.goto(`/worklog?memberId=${memberId}`);
    await page.waitForLoadState("networkidle");

    // Check all buttons have aria-label or visible text
    const buttons = await page.locator("button").all();
    for (const button of buttons) {
      const ariaLabel = await button.getAttribute("aria-label");
      const text = await button.textContent();
      const isHidden = await button.isHidden();

      if (!isHidden) {
        expect(
          ariaLabel || (text && text.trim().length > 0),
          `Button should have aria-label or text content`,
        ).toBeTruthy();
      }
    }
  });

  test("modal dialog has proper ARIA attributes", async ({ page }) => {
    await page.goto(`/worklog?memberId=${memberId}`);
    await page.waitForLoadState("networkidle");

    // Open Copy Previous Month dialog (this is an actual modal)
    await page.click('button:has-text("Copy Previous Month")');
    const dialog = await page.waitForSelector('[role="dialog"]');

    // Check required ARIA attributes
    expect(await dialog.getAttribute("aria-modal")).toBe("true");
    expect(await dialog.getAttribute("aria-labelledby")).toBeTruthy();
  });

  test("keyboard navigation works in calendar", async ({ page }) => {
    await page.goto(`/worklog?memberId=${memberId}`);
    await page.waitForLoadState("networkidle");

    // Tab to first interactive element
    await page.keyboard.press("Tab");

    // Verify focus is on a focusable element
    const focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(["BUTTON", "A", "INPUT", "SELECT"]).toContain(focusedElement);
  });

  test("escape key closes modal dialogs", async ({ page }) => {
    await page.goto(`/worklog?memberId=${memberId}`);
    await page.waitForLoadState("networkidle");

    // Open Copy Previous Month dialog (this is an actual modal)
    await page.click('button:has-text("Copy Previous Month")');
    await page.waitForSelector('[role="dialog"]');

    // Press Escape
    await page.keyboard.press("Escape");

    // Verify dialog is closed
    await expect(page.locator('[role="dialog"]')).not.toBeVisible();
  });

  test("form inputs have associated labels", async ({ page }) => {
    // Navigate directly to daily entry form
    await page.goto(`/worklog/2026-01-15`);
    await page.waitForLoadState("networkidle");

    // Check all inputs have labels
    const inputs = await page.locator("input, select, textarea").all();
    for (const input of inputs) {
      const id = await input.getAttribute("id");
      const ariaLabel = await input.getAttribute("aria-label");
      const ariaLabelledby = await input.getAttribute("aria-labelledby");
      const type = await input.getAttribute("type");

      // Hidden inputs don't need labels
      if (type === "hidden") continue;

      const hasLabel = ariaLabel || ariaLabelledby || (id && (await page.locator(`label[for="${id}"]`).count()) > 0);

      expect(hasLabel, `Input should have aria-label, aria-labelledby, or associated label`).toBeTruthy();
    }
  });

  test("focus is trapped in modal dialogs", async ({ page }) => {
    await page.goto(`/worklog?memberId=${memberId}`);
    await page.waitForLoadState("networkidle");

    // Open Copy Previous Month dialog (this is an actual modal)
    await page.click('button:has-text("Copy Previous Month")');
    await page.waitForSelector('[role="dialog"]');

    // Tab through all focusable elements in dialog
    const focusableSelector = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';
    const dialogFocusable = await page.locator(`[role="dialog"] ${focusableSelector}`).all();

    // Tab through all elements and verify we stay in dialog
    for (let i = 0; i < dialogFocusable.length + 2; i++) {
      await page.keyboard.press("Tab");
      const activeElement = await page.evaluate(() => {
        const el = document.activeElement;
        return el?.closest('[role="dialog"]') !== null;
      });
      expect(activeElement).toBe(true);
    }
  });

  test("error messages are announced to screen readers", async ({ page }) => {
    // Navigate directly to daily entry form
    await page.goto(`/worklog/2026-01-15`);
    await page.waitForLoadState("networkidle");

    // Check for role="alert" elements (error containers)
    const alerts = await page.locator('[role="alert"]').all();

    // If there are any alert elements, they should have aria-live
    for (const alert of alerts) {
      const ariaLive = await alert.getAttribute("aria-live");
      // role="alert" implies aria-live="assertive" by default
      expect(ariaLive === "assertive" || ariaLive === null).toBeTruthy();
    }
  });

  test("color contrast meets WCAG AA standards", async ({ page }) => {
    await page.goto(`/worklog?memberId=${memberId}`);
    await page.waitForLoadState("networkidle");

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(["wcag2aa"])
      .options({ runOnly: ["color-contrast"] })
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });
});
