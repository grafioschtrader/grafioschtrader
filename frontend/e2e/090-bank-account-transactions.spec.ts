import {test} from '@playwright/test';
import {loginAsCsvUser} from './helpers';
import {
  createBankTransfer,
  createSingleBankTransaction,
  ensureBankTransactionExchangeRates,
  expectBankTransactions,
  findMissingBankTransactions,
  loadBankTransactions,
} from './bank-account-transaction.helpers';

/**
 * Recreates the bank-account-only transaction set entered manually by user 'alledit'. Exact existing operations are
 * retained and missing operations are created, so this spec can be repeated against the same grafioschtrader_t after
 * any partial or successful earlier run without resetting the database.
 */
const TRANSACTIONS = loadBankTransactions();

test.describe.serial("bank account transactions of 'alledit'", () => {
  test('reconciles and verifies every fixture transaction', async ({page}) => {
    await loginAsCsvUser(page, 'alledit');
    await ensureBankTransactionExchangeRates(page, TRANSACTIONS);
    const missingTransactions = await findMissingBankTransactions(page, TRANSACTIONS);

    for (const fixture of missingTransactions) {
      if (fixture.transaction.kind === 'single') {
        await createSingleBankTransaction(page, fixture);
      } else {
        await createBankTransfer(page, fixture);
      }
    }

    await expectBankTransactions(page, TRANSACTIONS);
  });
});
