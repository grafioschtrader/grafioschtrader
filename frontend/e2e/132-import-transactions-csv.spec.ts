import { test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';
import {
  createAllImportedTransactions,
  expectCsvImportedTransactions,
  expectCsvImportPositions,
  loadCsvImportTransactionScenarios,
  openSecurityAccount,
  openTransactionImport,
  resetCsvImportTransactionScenario,
  uploadTransactionCsv
} from './import-transaction.helpers';

const SCENARIOS = loadCsvImportTransactionScenarios('e2e');

for (const scenario of SCENARIOS) {
  test.describe
    .serial(`CSV transaction import of '${scenario.securityAccountName}' for '${scenario.loginNickname}'`, () => {
    test(`uploads ${scenario.csvFileName} and creates every transaction`, async ({ page }) => {
      await loginAsFixtureUser(page, scenario.loginNickname);
      const idTransactionHead = await resetCsvImportTransactionScenario(page, scenario);
      const idSecurityAccount = await openSecurityAccount(page, scenario);
      await openTransactionImport(page);

      await uploadTransactionCsv(page, scenario, idTransactionHead);
      await expectCsvImportPositions(page, scenario, idTransactionHead, false);
      await createAllImportedTransactions(page, scenario.expectedTransactions.length);
      await expectCsvImportPositions(page, scenario, idTransactionHead, true);
      await expectCsvImportedTransactions(page, scenario, idSecurityAccount);
    });
  });
}
