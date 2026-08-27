import { defineConfig } from '@playwright/test';

/** Two real grafiosch-server-base peers. The runner provides both frontend and backend URLs. */
export default defineConfig({
  testDir: './e2e/gtnet',
  globalSetup: require.resolve('./e2e/gtnet/global.setup.ts'),
  timeout: 120_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['./e2e/reporters/timing.reporter.ts']],
  use: {
    baseURL: process.env.GTNET_PEER_A_FRONTEND_URL ?? 'http://localhost:4201',
    headless: true,
    viewport: { width: 1280, height: 1024 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  },
  workers: 1,
  projects: [{ name: 'gtnet-e2e' }]
});
