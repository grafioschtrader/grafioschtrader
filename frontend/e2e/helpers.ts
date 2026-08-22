import * as path from 'path';

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
  loginAsFixtureUser,
  registerAndSetupTenant,
  saveCredentials
} = gtHelpers;
