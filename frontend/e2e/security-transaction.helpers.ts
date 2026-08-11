import {expect, Locator, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {getUser} from './helpers';
import {toShortDate} from './portfolio.helpers';

const FIXTURE_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/transaction_securities.json');

const RX = {
  accumulate: /^(Buy|Kaufen)\.\.\.$/i,
  closeMargin: /^(Closing the margin position|Schliessen der Margin-Position)$/i,
  financeCost: /^(Margin finance cost|Finanzierungskosten)$/i,
};

export type SecurityTransactionType = 'ACCUMULATE' | 'FINANCE_COST' | 'REDUCE';

export interface SecurityTransactionData {
  transactionType: SecurityTransactionType;
  /** ISO calendar date; the E2E test enters deterministic local noon. */
  date: string;
  /** Quantity for buy/sell transactions and number of days for finance costs. */
  units: number;
  quotation: number;
  taxCost?: number;
  transactionCost?: number;
  expectedCashaccountAmount: number;
}

export interface SecurityTransactionGroup {
  /** Stable identifier selecting the complete ordered transaction scenario. */
  identifier: string;
  loginNickname: string;
  watchlistName: string;
  portfolioName: string;
  securityAccountName: string;
  cashAccountName: string;
  securityName: string;
  transactions: SecurityTransactionData[];
  e2e: 'd' | 'i' | 'e';
}

interface SecurityTransactionFixtureFile {
  transactionGroups: SecurityTransactionGroup[];
}

interface ApiSecurityTransaction {
  idTransaction: number;
  connectedIdTransaction?: number | null;
  idSecurityaccount?: number | null;
  transactionTime: number | string;
  transactionType: SecurityTransactionType;
  units: number;
  quotation: number;
  taxCost?: number | null;
  transactionCost?: number | null;
  cashaccountAmount: number;
  cashaccount?: {name: string} | null;
  security?: {name: string} | null;
}

/** Loads the security transaction groups owned by Playwright. */
export function loadE2ESecurityTransactionGroups(): SecurityTransactionGroup[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping the security transaction E2E spec.`);
    return [];
  }
  const fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf8')) as SecurityTransactionFixtureFile;
  return fixture.transactionGroups.filter(group => group.e2e === 'e');
}

/** Deletes only transactions with the natural identity of this group, children before their opening transaction. */
export async function deleteSecurityTransactionGroup(page: Page, group: SecurityTransactionGroup): Promise<void> {
  await test.step(`delete leftovers of ${group.identifier}`, async () => {
    const headers = await authHeaders(page);
    const transactions = (await getTenantTransactions(page, headers))
      .filter(transaction => belongsToGroup(transaction, group))
      .sort((a, b) => Number(b.connectedIdTransaction != null) - Number(a.connectedIdTransaction != null));

    for (const transaction of transactions) {
      const response = await page.request.delete(`/api/transaction/${transaction.idTransaction}`, {headers});
      expect(response.ok(), `deleting ${transaction.transactionType} transaction ${transaction.idTransaction}: `
        + await response.text()).toBeTruthy();
    }

    const remaining = (await getTenantTransactions(page, headers))
      .filter(transaction => belongsToGroup(transaction, group));
    expect(remaining, `${group.identifier} transactions after startup cleanup`).toHaveLength(0);
  });
}

/** Creates the group's ordered transaction sequence through the watchlist and its margin transaction table. */
export async function createSecurityTransactionGroup(page: Page, group: SecurityTransactionGroup): Promise<void> {
  await openWatchlist(page, group.watchlistName);

  for (const transaction of group.transactions) {
    await test.step(`create ${group.identifier} ${transaction.transactionType}`, async () => {
      switch (transaction.transactionType) {
        case 'ACCUMULATE':
          await createOpeningTransaction(page, group, transaction);
          break;
        case 'FINANCE_COST':
          await createConnectedTransaction(page, group, transaction, RX.financeCost);
          break;
        case 'REDUCE':
          await createConnectedTransaction(page, group, transaction, RX.closeMargin);
          break;
      }
    });
  }
}

/** Verifies values and parent-child links from the tenant transaction REST representation. */
export async function expectSecurityTransactionGroup(page: Page, group: SecurityTransactionGroup): Promise<void> {
  await test.step(`verify ${group.identifier}`, async () => {
    const transactions = (await getTenantTransactions(page, await authHeaders(page)))
      .filter(transaction => belongsToGroup(transaction, group));
    expect(transactions, `${group.identifier} transaction count`).toHaveLength(group.transactions.length);

    const opening = transactions.find(transaction => transaction.transactionType === 'ACCUMULATE');
    expect(opening, `${group.identifier} opening transaction`).toBeDefined();
    expect(opening.connectedIdTransaction).toBeNull();

    for (const expectedTransaction of group.transactions) {
      const actual = transactions.find(transaction =>
        transaction.transactionType === expectedTransaction.transactionType
        && transactionDate(transaction) === expectedTransaction.date);
      expect(actual, `${group.identifier} ${expectedTransaction.transactionType} on ${expectedTransaction.date}`)
        .toBeDefined();
      expect(actual.idSecurityaccount).not.toBeNull();
      expect(actual.units).toBeCloseTo(expectedTransaction.units, 8);
      expect(actual.quotation).toBeCloseTo(expectedTransaction.quotation, 8);
      expect(actual.cashaccountAmount).toBeCloseTo(expectedTransaction.expectedCashaccountAmount, 8);

      if (expectedTransaction.taxCost !== undefined) {
        expect(actual.taxCost).toBeCloseTo(expectedTransaction.taxCost, 8);
      }
      if (expectedTransaction.transactionCost !== undefined) {
        expect(actual.transactionCost).toBeCloseTo(expectedTransaction.transactionCost, 8);
      }
      if (expectedTransaction.transactionType !== 'ACCUMULATE') {
        expect(actual.connectedIdTransaction).toBe(opening.idTransaction);
      }
    }
  });
}

async function createOpeningTransaction(page: Page, group: SecurityTransactionGroup,
    transaction: SecurityTransactionData): Promise<void> {
  const row = watchlistSecurityRow(page, group);
  await openContextMenuItem(page, row, RX.accumulate);

  const dialog = await visibleTransactionDialog(page);
  await setTransactionDate(dialog, transaction.date, group.loginNickname);
  await selectWhenReady(dialog.locator('select#idSecurityaccount'),
    [group.securityAccountName, group.portfolioName]);
  await selectWhenReady(dialog.locator('select#idCashaccount'), [group.cashAccountName, group.portfolioName]);
  await fillNumber(dialog, 'units', transaction.units);
  await fillNumber(dialog, 'quotation', transaction.quotation);
  await fillOptionalCosts(dialog, transaction);
  await submitTransaction(page, dialog);
}

async function createConnectedTransaction(page: Page, group: SecurityTransactionGroup,
    transaction: SecurityTransactionData, menuItem: RegExp): Promise<void> {
  const marginTable = await openMarginTable(page, group);
  const openingRow = marginTable.locator('p-treetable tbody > tr').first();
  await openingRow.waitFor({state: 'visible', timeout: 15_000});
  await openContextMenuItem(page, openingRow, menuItem);

  const dialog = await visibleTransactionDialog(page);
  await setTransactionDate(dialog, transaction.date, group.loginNickname);
  await fillNumber(dialog, 'units', transaction.units);
  await fillNumber(dialog, 'quotation', transaction.quotation);
  await fillOptionalCosts(dialog, transaction);
  await submitTransaction(page, dialog);
}

async function fillOptionalCosts(dialog: Locator, transaction: SecurityTransactionData): Promise<void> {
  if (transaction.taxCost !== undefined) {
    await fillNumber(dialog, 'taxCost', transaction.taxCost);
  }
  if (transaction.transactionCost !== undefined) {
    await fillNumber(dialog, 'transactionCost', transaction.transactionCost);
  }
}

async function openWatchlist(page: Page, watchlistName: string): Promise<void> {
  const node = page.getByRole('treeitem', {name: watchlistName, exact: true}).first();
  await node.waitFor({state: 'visible', timeout: 15_000});
  await node.click();
  await page.locator('.data-container').first().waitFor({state: 'visible', timeout: 15_000});
}

function watchlistSecurityRow(page: Page, group: SecurityTransactionGroup): Locator {
  return page.locator('.data-container').first().locator('p-table tbody tr')
    .filter({hasText: group.securityName}).first();
}

async function openMarginTable(page: Page, group: SecurityTransactionGroup): Promise<Locator> {
  const row = watchlistSecurityRow(page, group);
  await row.waitFor({state: 'visible', timeout: 15_000});
  let marginTable = row.locator('xpath=following-sibling::tr[1]')
    .locator('transaction-security-margin-treetable');
  if (!await marginTable.isVisible().catch(() => false)) {
    await row.locator('td').first().locator('a').click();
    marginTable = row.locator('xpath=following-sibling::tr[1]')
      .locator('transaction-security-margin-treetable');
  }
  await marginTable.waitFor({state: 'visible', timeout: 15_000});
  return marginTable;
}

async function openContextMenuItem(page: Page, row: Locator, itemName: RegExp): Promise<void> {
  for (let attempt = 1; attempt <= 3; attempt++) {
    await row.click();
    await page.waitForTimeout(250);
    await row.click({button: 'right'});

    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 5_000});
    const item = menu.getByText(itemName).first();
    if (await item.isVisible().catch(() => false)) {
      await item.click();
      return;
    }
    await page.keyboard.press('Escape');
  }
  throw new Error(`Context menu item ${itemName} did not become available`);
}

async function visibleTransactionDialog(page: Page): Promise<Locator> {
  const dialog = page.locator('transaction-security-edit .p-dialog:visible');
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  await dialog.locator('#transactionTime input').waitFor({state: 'visible', timeout: 10_000});
  return dialog;
}

async function setTransactionDate(dialog: Locator, isoDate: string, loginNickname: string): Promise<void> {
  const input = dialog.locator('#transactionTime input');
  const value = `${toShortDate(isoDate, getUser(loginNickname).localeStr)} 12:00`;
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(value, {delay: 15});
  await input.blur();
  await expect(input).toHaveValue(value);
}

async function selectWhenReady(select: Locator, naturalKeyParts: string[]): Promise<void> {
  await select.waitFor({state: 'visible', timeout: 10_000});
  let option = select.locator('option');
  naturalKeyParts.forEach(part => option = option.filter({hasText: part}));
  option = option.first();
  await expect(option).toBeAttached({timeout: 15_000});
  if (await select.isDisabled()) {
    for (const part of naturalKeyParts) {
      await expect(select.locator('option:checked')).toContainText(part);
    }
    return;
  }
  await select.selectOption(await option.getAttribute('value') ?? '');
  await select.dispatchEvent('change');
}

async function fillNumber(dialog: Locator, field: string, value: number): Promise<void> {
  const input = dialog.locator(`input#${field}, #${field} input`).first();
  await input.waitFor({state: 'visible', timeout: 10_000});
  await input.click();
  if (value < 0) {
    await input.press('Control+a');
    await input.press('Backspace');
    await input.pressSequentially(String(value), {delay: 30});
  } else {
    await input.fill(String(value));
    await input.dispatchEvent('input');
  }
  await input.press('Tab');
}

async function submitTransaction(page: Page, dialog: Locator): Promise<void> {
  const submit = dialog.locator('button[type="submit"]');
  await expect(submit).toBeEnabled({timeout: 15_000});
  const responsePromise = page.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname === '/api/transaction/securitytrans');
  await submit.click();
  const response = await responsePromise;
  expect(response.ok(), `/api/transaction/securitytrans: ${await response.text()}`).toBeTruthy();
  await dialog.waitFor({state: 'hidden', timeout: 15_000});
}

async function getTenantTransactions(page: Page,
    headers: Record<string, string>): Promise<ApiSecurityTransaction[]> {
  const response = await page.request.get('/api/transaction', {headers});
  expect(response.ok(), `loading tenant transactions: ${await response.text()}`).toBeTruthy();
  return await response.json() as ApiSecurityTransaction[];
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return {'x-auth-token': token, Accept: 'application/json'};
}

function belongsToGroup(transaction: ApiSecurityTransaction, group: SecurityTransactionGroup): boolean {
  return transaction.security?.name === group.securityName
    && transaction.cashaccount?.name === group.cashAccountName
    && group.transactions.some(expected => expected.transactionType === transaction.transactionType
      && expected.date === transactionDate(transaction));
}

function transactionDate(transaction: ApiSecurityTransaction): string {
  if (typeof transaction.transactionTime === 'number') {
    return new Date(transaction.transactionTime).toISOString().slice(0, 10);
  }
  return String(transaction.transactionTime).slice(0, 10);
}
