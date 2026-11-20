import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { loginAsFixtureUser } from './helpers';
import { holdResponse } from './response.helpers';
import { createCashStandingOrder, loadStandingOrders } from './standing-order.helpers';

const route = '/mainview/tenantTabMenu/standingordertabmenu/tenantstandingordercashaccount';
const securityTab = /Standing\s+order\s+security|Dauerauftrag\s+Wertpapier/i;
const ownNote = 'E2E OnPush standing-order notification';
test.use({ viewport: { width: 1600, height: 1500 }, actionTimeout: 15_000 });
test.beforeEach(async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
});

async function openTable(page: Page, kind: 'cashaccount' | 'security' = 'cashaccount') {
  await page.goto(route);
  if (kind === 'security') await page.getByRole('tab', { name: securityTab }).click();
  const host = page.locator(`standing-order-${kind}-table`);
  await expect(host.locator('configurable-table')).toBeVisible();
  return host;
}

async function openCreate(page: Page, host: Locator, kind: 'cashaccount' | 'security') {
  await host.locator(':scope > .data-container').click();
  await page
    .locator('p-menubar')
    .getByRole('menuitem', { name: /^(Edit|Bearbeiten)$/ })
    .click();
  await page
    .locator('p-menubar')
    .getByRole('menuitem', { name: /^(Create|Erstellen)\s/ })
    .click();
  const dialog = page.locator(`standing-order-${kind}-edit .p-dialog`);
  await expect(dialog).toBeVisible();
  return dialog;
}

test('delayed capabilities and standing orders publish the tabs and rows', async ({ page }) => {
  const capabilities = await holdResponse(page, '**/api/standingorder/capabilities');
  const orders = await holdResponse(page, '**/api/standingorder/tenant');
  try {
    await page.goto(route);
    await capabilities.arrived;
    capabilities.release();
    await expect(page.getByRole('tab', { name: securityTab })).toBeVisible();
    await orders.arrived;
    orders.release();
    await expect(page.locator('standing-order-cashaccount-table tbody tr', { hasText: 'CT USD' })).toBeVisible();
    await page.getByRole('tab', { name: securityTab }).click();
    await expect(
      page
        .locator('standing-order-security-table tbody tr', { hasText: 'CH0237935652' })
        .or(page.locator('standing-order-security-table tbody tr', { hasText: 'iShares Core SPI' }))
        .first()
    ).toBeVisible();
  } finally {
    capabilities.release();
    orders.release();
  }
});

for (const kind of ['cashaccount', 'security'] as const) {
  test(`${kind} dialog discards a closed session and renders dependent fields after reopening`, async ({ page }) => {
    const host = await openTable(page, kind);
    const pending = await holdResponse(page, '**/api/portfolio/tenant');
    try {
      const abandoned = await openCreate(page, host, kind);
      await pending.arrived;
      await page.keyboard.press('Escape');
      await expect(abandoned).toHaveCount(0);
      const dialog = await openCreate(page, host, kind);
      if (kind === 'security') {
        const accounts = dialog.locator('select#idSecurityaccount');
        await expect(accounts.locator('option')).not.toHaveCount(0);
        const value = await accounts.locator('option').first().getAttribute('value');
        await accounts.selectOption(value!);
      }
      await expect(dialog.locator('select#idCashaccount option')).not.toHaveCount(0);
      pending.release();
      await pending.done;
      await dialog.locator('textarea#note').fill('Unsaved notification draft');
      await dialog.locator('select#repeatUnit').selectOption('DAYS');
      await expect(dialog.locator('select#periodDayPosition')).toBeDisabled();
      await expect(dialog.locator('select#monthOfExecution')).toBeDisabled();
      await dialog.locator('select#repeatUnit').selectOption('YEARS');
      await expect(dialog.locator('select#monthOfExecution')).toBeEnabled();
      await dialog.locator('select#periodDayPosition').selectOption('SPECIFIC_DAY');
      await expect(dialog.locator('#dayOfExecution input, input#dayOfExecution')).toBeEnabled();
      if (kind === 'security') {
        await dialog.locator('select#investMode').selectOption('INVEST_MODE_AMOUNT');
        await expect(dialog.locator('#units input, input#units')).toBeDisabled();
        await expect(dialog.locator('#investAmount input, input#investAmount')).toBeEnabled();
        await dialog.locator('select#investMode').selectOption('INVEST_MODE_UNITS');
        await expect(dialog.locator('#units input, input#units')).toBeEnabled();
        await expect(dialog.locator('#investAmount input, input#investAmount')).toBeDisabled();
      }
      await expect(dialog.locator('textarea#note')).toHaveValue('Unsaved notification draft');
      await page.keyboard.press('Escape');
      await expect(dialog).toHaveCount(0);
    } finally {
      pending.release();
    }
  });
}

test('saving a dedicated future order refreshes the table without another click', async ({ page }) => {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  const headers = { 'x-auth-token': token!, Accept: 'application/json' };
  const cleanup = async () => {
    const response = await page.request.get('/api/standingorder/tenant', { headers });
    expect(response.ok()).toBeTruthy();
    for (const order of await response.json())
      if (order.note === ownNote) {
        expect(order.hasTransactions).toBeFalsy();
        const deleted = await page.request.delete(`/api/standingorder/${order.idStandingOrder}`, { headers });
        expect(deleted.ok(), await deleted.text()).toBeTruthy();
      }
  };
  await cleanup();
  try {
    const source = loadStandingOrders().find((fixture) => fixture.standingOrder.kind === 'cash')!;
    const year = new Date().getFullYear();
    await createCashStandingOrder(page, {
      ...source,
      standingOrder: {
        ...source.standingOrder,
        note: ownNote,
        validFrom: `${year + 1}-01-01`,
        validTo: `${year + 2}-12-31`
      }
    });
  } finally {
    await cleanup();
  }
});

test('expanded orders render delayed transactions, failure rows and their stack traces', async ({ page }) => {
  const fixture = JSON.parse(
    fs.readFileSync(
      path.resolve(
        __dirname,
        '../../backend/grafioschtrader-server/src/test/resources/testdata/standing-order-notification.json'
      ),
      'utf8'
    )
  );
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  const headers = { 'x-auth-token': token!, Accept: 'application/json' };
  const response = await page.request.get('/api/standingorder/tenant', { headers });
  expect(response.ok()).toBeTruthy();
  const orders = await response.json();
  const order = orders.find((so: any) => so.dtype === 'C' && so.cashaccount.name === 'CT USD');
  expect(order).toBeTruthy();
  await page.route('**/api/standingorder/tenant', (route) =>
    route.fulfill({
      json: orders.map((so: any) =>
        so.idStandingOrder === order.idStandingOrder ? { ...so, failureCount: 1, transactionCount: 1 } : so
      )
    })
  );
  const failures = await holdResponse(page, `**/api/standingorder/${order.idStandingOrder}/failures`, 'GET', [
    { ...fixture.failure, idStandingOrder: order.idStandingOrder, idStandingOrderFailure: -1 }
  ]);
  const transactions = await holdResponse(page, `**/api/standingorder/${order.idStandingOrder}/transactions`, 'GET', [
    { ...fixture.transaction, cashaccount: order.cashaccount }
  ]);
  try {
    const host = await openTable(page);
    await host.locator('tbody tr', { hasText: 'CT USD' }).locator('a').first().click();
    await failures.arrived;
    await transactions.arrived;
    failures.release();
    transactions.release();
    const failureTable = host.locator('standing-order-failure-table');
    await expect(failureTable).toContainText(fixture.failure.businessError);
    await expect(host.locator('standing-order-transaction-table tbody tr')).toContainText(/Fee|Konto- und Depotkosten/);
    await failureTable.locator('tbody tr').first().locator('a').click();
    await expect(failureTable.locator('textarea')).toHaveValue(fixture.failure.unexpectedError);
  } finally {
    failures.release();
    transactions.release();
  }
});
