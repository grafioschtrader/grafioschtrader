import {defineConfig} from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: 'http://localhost:4200',
    headless: true,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  /* Run e2e test files sequentially (alphabetical order) so
     99-delete runs after 01-create */
  workers: 1,
  projects: [
    {
      name: 'setup',
      testMatch: /auth\.setup\.ts/,
    },
    {
      name: 'e2e-tests',
      dependencies: ['setup'],
    },
  ],
});
