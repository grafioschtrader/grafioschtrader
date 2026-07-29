import {defineConfig} from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  globalSetup: require.resolve('./e2e/global.setup.ts'),
  timeout: 120_000,
  expect: {
    timeout: 10_000,
  },
  /* 'list' is the Playwright default and has to be repeated, because naming any reporter
     replaces the default. The timing reporter appends the run-time breakdown after it. */
  reporter: [
    ['list'],
    ['./e2e/reporters/timing.reporter.ts'],
  ],
  use: {
    baseURL: 'http://localhost:4200',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  /* Run e2e test files sequentially (alphabetical order) so
     888-delete runs after the numbered create specs (005, 010, 015, ...) */
  workers: 1,
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
      testIgnore: /lib\//,
    },
    {
      name: 'grafioschtrader-e2e',
      dependencies: ['setup'],
      testIgnore: /lib\//,
    },
  ],
});
