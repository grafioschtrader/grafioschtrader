import { test } from '@playwright/test';
import {
  beginCashSecurityScenario,
  createCashSecurityTransaction,
  deleteOwnedTransactions,
  expectCashSecurityTransactions,
  expectHolding,
  scenario
} from './cash-security-transaction.helpers';

test('books a dividend on the remaining cash equity holding after a rejected quantity', async ({ page }) => {
  await beginCashSecurityScenario(page);
  const [buy, sell, dividend] = scenario.transactions;
  await expectCashSecurityTransactions(page, [buy, sell]);
  await deleteOwnedTransactions(page, [dividend]);
  await expectHolding(page, 6, dividend.exDate!);
  // Exceeds even the original ten units, so the server must reject it irrespective of ex-date ordering.
  await createCashSecurityTransaction(page, dividend, { rejectUnits: 11 });
  await expectCashSecurityTransactions(page, [buy, sell, dividend]);
  await expectHolding(page, 6, dividend.date);
});
