import {defineConfig} from '@playwright/test';

/** Portable browser suite for code owned by src/app/lib. */
export default defineConfig({
  testDir: './e2e/lib',
  globalSetup: require.resolve('./e2e/lib/global.setup.ts'),
  timeout: 120_000,
  expect: {
    timeout: 10_000,
  },
  /* See playwright.config.ts: naming a reporter drops the default 'list', so it is repeated here. */
  reporter: [
    ['list'],
    ['./e2e/reporters/timing.reporter.ts'],
  ],
  use: {
    baseURL: process.env.LIB_E2E_FRONTEND_URL ?? 'http://localhost:4201',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  workers: 1,
  projects: [
    {
      name: 'lib-setup',
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: 'lib-e2e',
      dependencies: ['lib-setup'],
      testIgnore: /auth\.setup\.ts/,
    },
  ],
});
