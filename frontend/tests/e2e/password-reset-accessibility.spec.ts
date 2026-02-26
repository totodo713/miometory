/**
 * E2E Test: Password Reset Accessibility (T034)
 *
 * Test Scenario: WCAG 2.1 AA Compliance for Password Reset Flow
 *
 * Success Criteria:
 * - All form inputs have proper labels
 * - Error messages are announced to screen readers
 * - Password strength indicator is accessible
 * - Color contrast meets WCAG AA standards
 * - Keyboard navigation works throughout
 */

import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

test.describe("Password Reset Accessibility - WCAG 2.1 AA", () => {
  test("password reset request page has no violations", async ({ page }) => {
    await page.goto(`/password-reset/request`);
    await page.waitForLoadState("networkidle");

    const accessibilityScanResults = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test("password reset confirm page has no violations", async ({ page }) => {
    // Pre-set token in sessionStorage to avoid router.replace race condition
    await page.addInitScript(() => {
      window.sessionStorage.setItem("password_reset_token", "TEST_TOKEN");
    });
    await page.goto(`/password-reset/confirm`);
    await page.waitForLoadState("networkidle");

    const accessibilityScanResults = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa"]).analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test("form inputs have accessible labels", async ({ page }) => {
    await page.goto(`/password-reset/request`);
    await page.waitForLoadState("networkidle");

    // Check email input has associated label
    const _emailInput = page.locator("#email");
    const labelFor = await page.locator('label[for="email"]').count();
    expect(labelFor).toBeGreaterThan(0);
  });

  test("password strength indicator is accessible", async ({ page }) => {
    await page.addInitScript(() => {
      window.sessionStorage.setItem("password_reset_token", "TEST_TOKEN");
    });
    await page.goto(`/password-reset/confirm`);
    await page.waitForLoadState("networkidle");

    // Type a password to trigger strength indicator
    await page.fill("#new-password", "TestPassword123");

    // Wait for strength indicator to render (calculating or result state)
    const strengthLabel = page.locator(".strength-label");
    await expect(strengthLabel).toBeVisible({ timeout: 5000 });
    await expect(strengthLabel).toHaveAttribute("aria-live", "polite");
  });

  test("error messages are announced to screen readers", async ({ page }) => {
    await page.goto(`/password-reset/request`);
    await page.waitForLoadState("networkidle");

    // Submit without filling form
    await page.click('button[type="submit"]');

    // Wait for error message
    await page.waitForTimeout(500);

    // Check error has role="alert"
    const errorAlert = page.locator('[role="alert"]').first();
    await expect(errorAlert).toBeVisible();
  });

  test("color contrast meets WCAG AA standards", async ({ page }) => {
    await page.goto(`/password-reset/request`);
    await page.waitForLoadState("networkidle");

    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(["wcag2aa"])
      .options({ runOnly: ["color-contrast"] })
      .analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test("keyboard navigation works on request page", async ({ page }) => {
    await page.goto(`/password-reset/request`);
    await page.waitForLoadState("networkidle");

    // Fill valid email first so the submit button becomes enabled (and tabbable)
    const emailInput = page.locator("#email");
    await emailInput.fill("test@example.com");

    // Focus the email input and verify it is focusable
    await emailInput.focus();
    let focusedElement = await page.evaluate(() => document.activeElement?.id);
    expect(focusedElement).toBe("email");

    // Tab to submit button (enabled because email is valid)
    await page.keyboard.press("Tab");
    focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(focusedElement).toBe("BUTTON");
  });

  test("keyboard navigation works on confirm page", async ({ page }) => {
    await page.addInitScript(() => {
      window.sessionStorage.setItem("password_reset_token", "TEST_TOKEN");
    });
    await page.goto(`/password-reset/confirm`);
    await page.waitForLoadState("networkidle");

    // Fill valid passwords so the submit button becomes enabled (and tabbable)
    await page.fill("#new-password", "StrongP@ss1");
    await page.fill("#confirm-password", "StrongP@ss1");

    // Focus the new-password input and verify it is focusable
    await page.locator("#new-password").focus();
    let focusedElement = await page.evaluate(() => document.activeElement?.id);
    expect(focusedElement).toBe("new-password");

    // Tab to confirm password input
    await page.keyboard.press("Tab");
    focusedElement = await page.evaluate(() => document.activeElement?.id);
    expect(focusedElement).toBe("confirm-password");

    // Tab to submit button (enabled because passwords are valid and matching)
    await page.keyboard.press("Tab");
    focusedElement = await page.evaluate(() => document.activeElement?.tagName);
    expect(focusedElement).toBe("BUTTON");
  });

  test("validation errors have proper ARIA attributes", async ({ page }) => {
    await page.addInitScript(() => {
      window.sessionStorage.setItem("password_reset_token", "TEST_TOKEN");
    });
    await page.goto(`/password-reset/confirm`);
    await page.waitForLoadState("networkidle");

    // Fill passwords that are too short but matching, so that the
    // onBlur mismatch validator does not disable the submit button
    // before handleSubmit can run and set validationErrors.
    await page.fill("#new-password", "weak");
    await page.fill("#confirm-password", "weak");
    await page.click('button[type="submit"]');

    // Check input has aria-invalid (Playwright auto-retries assertions)
    const newPasswordInput = page.locator("#new-password");
    await expect(newPasswordInput).toHaveAttribute("aria-invalid", "true");

    // Check error message has proper ID and role
    const errorMessage = page.locator("#new-password-error");
    await expect(errorMessage).toBeVisible();
    await expect(errorMessage).toHaveAttribute("role", "alert");
  });

  test("success message is accessible", async ({ page }) => {
    // Mock API success
    await page.route("**/api/v1/auth/password-reset/confirm", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ message: "Password reset successful" }),
      });
    });

    await page.addInitScript(() => {
      window.sessionStorage.setItem("password_reset_token", "VALID_TOKEN");
    });
    await page.goto(`/password-reset/confirm`);
    await page.waitForLoadState("networkidle");

    // Fill valid passwords
    await page.fill("#new-password", "ValidPassword123!");
    await page.fill("#confirm-password", "ValidPassword123!");
    await page.click('button[type="submit"]');

    // Wait for success — either the redirect countdown message appears
    // or the app redirects directly to the login page
    await expect(
      page
        .locator("text=/Redirecting to login page/")
        .or(page.locator("text=/Password reset successful/"))
        .or(page.locator("#email")),
    ).toBeVisible({
      timeout: 10000,
    });
  });
});
