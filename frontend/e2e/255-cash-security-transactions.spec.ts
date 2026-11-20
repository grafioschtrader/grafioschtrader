import { test } from '@playwright/test';
import {
  beginCashSecurityScenario,
  cleanupTrades,
  createCashSecurityTransaction,
  expectCashSecurityTransactions,
  expectHolding,
  scenario
} from './cash-security-transaction.helpers';

test('buys a cash equity and partially sells it after a rejected oversell', async ({ page }) => {
  await beginCashSecurityScenario(page);
  const retainedPurchase = await cleanupTrades(page);
  const [buy, sell] = scenario.transactions;
  await createCashSecurityTransaction(page, buy, { editExisting: retainedPurchase, calculateExchangeRate: true });
  await expectHolding(page, 10, sell.date);
  await createCashSecurityTransaction(page, sell, { rejectUnits: 11 });
  await expectCashSecurityTransactions(page, [buy, sell]);
  await expectHolding(page, 6, sell.date);
});
