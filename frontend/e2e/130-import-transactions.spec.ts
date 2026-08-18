import {test} from '@playwright/test';

import {loginAsFixtureUser} from './helpers';
import {
  cleanupImportTransactionScenario,
  dropImportDocuments,
  expectImportedTransactions,
  loadImportTransactionScenarios,
  openSecurityAccount,
} from './import-transaction.helpers';

const SCENARIOS = loadImportTransactionScenarios('e2e');

for (const scenario of SCENARIOS) {
  test.describe.serial(`document import of '${scenario.securityAccountName}' for '${scenario.loginNickname}'`, () => {
    test(`imports ${scenario.documents.length} PDFs through the account drop zone`, async ({page}) => {
      await loginAsFixtureUser(page, scenario.loginNickname);
      const idSecurityAccount = await openSecurityAccount(page, scenario);
      await cleanupImportTransactionScenario(page, scenario, idSecurityAccount);
      await dropImportDocuments(page, scenario, idSecurityAccount);
      await expectImportedTransactions(page, scenario, idSecurityAccount);
    });
  });
}
