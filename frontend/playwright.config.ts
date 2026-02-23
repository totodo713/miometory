import { defineConfig, devices } from "@playwright/test";

const frontendPort = process.env.PORT || "3000";
const frontendBaseURL = `http://localhost:${frontendPort}`;

export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 60_000,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: "html",
  use: {
    baseURL: frontendBaseURL,
    actionTimeout: 15_000,
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "Mobile Safari",
      use: { ...devices["iPhone 12"] },
    },
  ],
  webServer: {
    command: "npm run dev",
    url: frontendBaseURL,
    reuseExistingServer: !process.env.CI,
  },
});
