import { expect, Page, test } from '@playwright/test';

import { beginCashSecurityScenario, scenario } from './cash-security-transaction.helpers';
import { getUser } from './helpers';
import { toShortDate } from './portfolio.helpers';
import { watchlistTable } from './watchlist.helpers';

/**
 * A sale under FIFO names the purchase it consumed. The spec reads the purchase and the partial sale that 122 created
 * and owns nothing but the cost basis method of the tenant: it sets it to FIFO for the check and back to average cost
 * at the start and at the end, because every other spec calculates with average cost.
 */
const [buy, sell] = scenario.transactions;
// The purchase cost per unit includes its tax and fees; the sale takes its share of that cost.
const LOT_COST = ((buy.units * buy.quotation + buy.taxCost + buy.transactionCost) * sell.units) / buy.units;

interface ConsumedLot {
  idTransaction: number;
  transactionTime: string;
  units: number;
  cost: number;
}

interface TransactionPosition {
  transaction: { idTransaction: number; transactionType: string; note: string };
  costBasisMethod?: string;
  consumedLots?: ConsumedLot[];
}

async function headers(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token).toBeTruthy();
  return { 'x-auth-token': token!, Accept: 'application/json' };
}

async function setTenantMethod(page: Page, method: string): Promise<void> {
  const tenantResponse = await page.request.get('/api/tenant', { headers: await headers(page) });
  expect(tenantResponse.ok(), await tenantResponse.text()).toBeTruthy();
  const tenant = await tenantResponse.json();
  if (tenant.costBasisMethod !== method) {
    const saved = await page.request.put('/api/tenant', {
      headers: await headers(page),
      data: { ...tenant, costBasisMethod: method }
    });
    expect(saved.ok(), await saved.text()).toBeTruthy();
  }
}

/** The transactions of the security as the report of the tenant values them. */
async function positions(page: Page): Promise<TransactionPosition[]> {
  const all = await page.request.get('/api/transaction', { headers: await headers(page) });
  expect(all.ok(), await all.text()).toBeTruthy();
  const purchase = (await all.json()).find(
    (row: { note: string; security?: { isin: string } }) =>
      row.note === buy.note && row.security?.isin === scenario.securityIsin
  );
  expect(purchase, '122 purchase is a prerequisite').toBeDefined();
  const response = await page.request.get(
    `/api/security/tenantsecurity/${purchase.security.idSecuritycurrency}?forchart=false`,
    {
      headers: await headers(page)
    }
  );
  expect(response.ok(), await response.text()).toBeTruthy();
  return (await response.json()).transactionPositionList;
}

test('a FIFO sale names the purchase it consumed', async ({ page }) => {
  await beginCashSecurityScenario(page);
  await setTenantMethod(page, 'AVERAGE_COST');
  const average = (await positions(page)).find((position) => position.transaction.note === sell.note);
  expect(average, '122 partial sale is a prerequisite').toBeDefined();
  expect(average!.consumedLots ?? []).toHaveLength(0);

  try {
    await setTenantMethod(page, 'FIFO');
    const rows = await positions(page);
    const purchase = rows.find((position) => position.transaction.note === buy.note)!;
    const sale = rows.find((position) => position.transaction.note === sell.note)!;
    expect(sale.costBasisMethod).toBe('FIFO');
    expect(sale.consumedLots).toHaveLength(1);
    const lot = sale.consumedLots![0];
    expect(lot.idTransaction).toBe(purchase.transaction.idTransaction);
    expect(lot.transactionTime.startsWith(buy.date)).toBeTruthy();
    expect(lot.units).toBe(sell.units);
    expect(lot.cost).toBeCloseTo(LOT_COST, 2);

    await page.reload();
    await page.getByRole('treeitem', { name: scenario.watchlistName, exact: true }).first().click();
    const security = watchlistTable(page)
      .locator('p-table tbody > tr')
      .filter({ has: page.getByRole('cell', { name: scenario.securityName, exact: true }) });
    await expect(security).toHaveCount(1);
    await security.locator('td').first().locator('a, button').first().click();

    const locale = getUser(scenario.loginNickname).localeStr;
    const table = page.locator('transaction-security-table');
    const saleRow = table.locator('tbody > tr').filter({ hasText: toShortDate(sell.date, locale) });
    await expect(saleRow).toHaveCount(1);
    // Only the sale offers the expansion, the purchase and the dividend have nothing to show.
    await expect(table.locator('a[href="#"]:has(i.fa)')).toHaveCount(1);
    await saleRow.locator('td').first().locator('a').click();

    const lots = table.locator('transaction-consumed-lots-table');
    await expect(lots).toBeVisible();
    const lotRow = lots.locator('tbody > tr');
    await expect(lotRow).toHaveCount(1);
    await expect(lotRow).toContainText(toShortDate(buy.date, locale));
    await expect(lotRow.getByRole('cell', { name: new RegExp(`^${sell.units}([.,]0+)?$`) })).toBeVisible();
    await expect(lotRow.getByRole('cell', { name: LOT_COST.toFixed(2), exact: true })).toBeVisible();
  } finally {
    await setTenantMethod(page, 'AVERAGE_COST');
  }
});
