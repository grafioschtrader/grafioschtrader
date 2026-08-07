import * as path from 'path';

import {createSuiteHelpers, CsvUser, MailhogMessage, parseCsvRow, TestCredentials} from './lib/helpers';

/**
 * Helpers of the Grafioschtrader browser suite.
 *
 * The flows themselves live in `e2e/lib/helpers.ts` and are shared with the portable library suite — the dependency
 * points that way and never back, so `e2e/lib` stays self-contained when `src/app/lib` is extracted. Only the three
 * things that are specific to this application are configured here: which users.csv, which credential directory and
 * where a successful login lands.
 *
 * Everything the specs used before is still exported under the same name, so no spec had to change.
 */
export const gtHelpers = createSuiteHelpers({
  usersCsv: path.resolve(__dirname, '../../backend/grafioschtrader-server/src/test/resources/testdata/users.csv'),
  authDir: path.join(__dirname, '.auth'),
  mailApiBase: 'http://localhost:8025',
  afterLoginUrl: /\/mainview/,
});

export {parseCsvRow};
export type {CsvUser, MailhogMessage, TestCredentials};

export const {
  clearMailhog, findMailhogMessage, getCsvUser, loadCredentials, loadCredentialsByNickname, loadCsvUsers,
  loadE2EUsers, login, loginAs, loginAsCsvUser, registerAndSetupTenant, saveCredentials,
} = gtHelpers;
