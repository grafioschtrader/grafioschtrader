import { defineConfig } from '@playwright/test';

/** Two real Grafioschtrader peers. The runner provides both frontend and backend URLs. */
export default defineConfig({
  testDir: './e2e/gtnet-app',
  globalSetup: require.resolve('./e2e/gtnet-app/global.setup.ts'),
  timeout: 180_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['./e2e/reporters/timing.reporter.ts']],
  use: {
    baseURL: process.env.GTNET_PEER_A_FRONTEND_URL ?? 'http://localhost:4200',
    headless: true,
    viewport: { width: 1400, height: 1024 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure'
  },
  workers: 1,
  projects: [{ name: 'gtnet-app-e2e' }]
});
