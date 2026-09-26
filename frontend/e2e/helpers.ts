import * as path from 'path';
import { expect, Page, Request } from '@playwright/test';

import { createSuiteHelpers, MailhogMessage, parseCsvRow, TestCredentials, UserFixture } from './lib/helpers';

/**
 * Helpers of the Grafioschtrader browser suite.
 *
 * The flows themselves live in `e2e/lib/helpers.ts` and are shared with the portable library suite — the dependency
 * points that way and never back, so `e2e/lib` stays self-contained when `src/app/lib` is extracted. Only the three
 * things that are specific to this application are configured here: which users.json, which credential directory and
 * where a successful login lands.
 *
 * User-specific helper names reflect the JSON fixture; the generic CSV parser remains for other testdata files.
 */
export const gtHelpers = createSuiteHelpers({
  usersJson: path.resolve(__dirname, '../../backend/grafioschtrader-server/src/test/resources/testdata/users.json'),
  authDir: path.join(__dirname, '.auth'),
  mailApiBase: 'http://localhost:8025',
  afterLoginUrl: /\/mainview/
});

export { parseCsvRow };
export type { MailhogMessage, TestCredentials, UserFixture };

export const {
  clearMailhog,
  findMailhogMessage,
  getUser,
  loadCredentials,
  loadCredentialsByNickname,
  loadUsers,
  loadE2EUsers,
  login,
  loginAs,
  registerAndSetupTenant,
  saveCredentials
} = gtHelpers;

/** Wait for the portfolio branch, which loads independently of the tree shell after login. */
export async function loginAsFixtureUser(page: Page, nickname: string): Promise<TestCredentials> {
  let portfolioConnectionRefused = false;
  const onRequestFailed = (request: Request): void => {
    if (
      new URL(request.url()).pathname === '/api/portfolio/tenant' &&
      request.failure()?.errorText === 'net::ERR_CONNECTION_REFUSED'
    ) {
      portfolioConnectionRefused = true;
    }
  };
  page.on('requestfailed', onRequestFailed);
  try {
    const credentials = await gtHelpers.loginAsFixtureUser(page, nickname);
    const portfolioRoot = page.getByRole('treeitem', { name: /^Portfolios-/ }).first();
    try {
      await expect(portfolioRoot).toBeVisible({ timeout: 15_000 });
    } catch (error) {
      // A refused dev-server connection leaves this branch empty permanently. Retry only that transport failure;
      // HTTP errors and missing/broken tree labels must still fail, and the second load is never retried.
      if (!portfolioConnectionRefused) {
        throw error;
      }
      await page.reload();
      await expect(portfolioRoot).toBeVisible({ timeout: 15_000 });
    }
    return credentials;
  } finally {
    page.off('requestfailed', onRequestFailed);
  }
}
