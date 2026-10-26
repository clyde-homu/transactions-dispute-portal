import { defineConfig, devices, type ReporterDescription } from '@playwright/test';

import { BASE_URL } from './tests/e2e/support/env';

const inCi = Boolean(process.env.CI);

const reporter: ReporterDescription[] = inCi
  ? [['github'], ['html', { open: 'never' }]]
  : [['list'], ['html', { open: 'never' }]];

/**
 * The tests drive a stack that is already running (`docker compose up --build`, or the standalone
 * image), so there is deliberately no `webServer` block. Point them elsewhere with E2E_BASE_URL.
 *
 * Tags: put `@smoke` on the fast, must-always-pass tests (`{ tag: '@smoke' }`) and select them
 * with `npx playwright test --grep @smoke`.
 */
export default defineConfig({
  testDir: './tests/e2e',
  // The journeys create and withdraw disputes on shared server state, so they run one at a time.
  fullyParallel: false,
  workers: 1,
  forbidOnly: inCi,
  // One retry in CI only. A test that passes on the retry is reported as flaky, never as clean.
  retries: inCi ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter,
  use: {
    baseURL: BASE_URL,
    // Pinned so money and dates render the same on every machine.
    locale: 'en-ZA',
    timezoneId: 'Africa/Johannesburg',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
    // The phone layout swaps the table for cards and the drawer for bottom tabs, so the journeys
    // run there too.
    { name: 'mobile-chromium', use: { ...devices['Pixel 7'] } },
  ],
});
