import {test} from '@playwright/test';
import {loginAsFixtureUser} from './helpers';
import {
  createBankTransfer,
  createSingleBankTransaction,
  ensureBankTransactionExchangeRates,
  expectBankTransactions,
  findMissingBankTransactions,
  loadBankTransactions,
} from './bank-account-transaction.helpers';

/** Recreates each fixture user's bank-account-only transactions without disturbing exact existing operations. */
const TRANSACTIONS_BY_USER = new Map<string, ReturnType<typeof loadBankTransactions>>();
for (const fixture of loadBankTransactions()) {
  const transactions = TRANSACTIONS_BY_USER.get(fixture.portfolio.loginNickname) ?? [];
  transactions.push(fixture);
  TRANSACTIONS_BY_USER.set(fixture.portfolio.loginNickname, transactions);
}

for (const [loginNickname, transactions] of TRANSACTIONS_BY_USER) {
  test.describe.serial(`bank account transactions of '${loginNickname}'`, () => {
    test('reconciles and verifies every fixture transaction', async ({page}) => {
      await loginAsFixtureUser(page, loginNickname);
      await ensureBankTransactionExchangeRates(page, transactions);
      const missingTransactions = await findMissingBankTransactions(page, transactions);

      for (const fixture of missingTransactions) {
        if (fixture.transaction.kind === 'single') {
          await createSingleBankTransaction(page, fixture);
        } else {
          await createBankTransfer(page, fixture);
        }
      }

      await expectBankTransactions(page, transactions);
    });
  });
}
