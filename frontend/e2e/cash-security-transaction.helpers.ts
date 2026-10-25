import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { getUser, loginAsFixtureUser } from './helpers';
import { toShortDate } from './portfolio.helpers';
import { watchlistTable } from './watchlist.helpers';

interface CashSecurityTransaction {
  transactionType: 'ACCUMULATE' | 'REDUCE' | 'DIVIDEND';
  date: string;
  exDate?: string;
  units: number;
  quotation: number;
  taxCost: number;
  transactionCost: number;
  expectedCashaccountAmount: number;
  note: string;
  e2e: string;
}

interface CashSecurityScenario {
  loginNickname: string;
  watchlistName: string;
  portfolioName: string;
  securityAccountName: string;
  cashAccountName: string;
  cashCurrency: string;
  securityName: string;
  securityIsin: string;
  securityCurrency: string;
  exchangeRate: number;
  transactions: CashSecurityTransaction[];
  e2e: string;
}

interface StoredTransaction {
  idTransaction: number;
  idSecurityaccount: number;
  transactionType: string;
  transactionTime: string;
  exDate?: string;
  units: number;
  quotation: number;
  taxCost: number;
  transactionCost: number;
  currencyExRate: number;
  cashaccountAmount: number;
  note: string;
  connectedIdTransaction?: number;
  taxableInterest: boolean;
  security?: { idSecuritycurrency: number; isin: string; currency: string };
  cashaccount: { idSecuritycashAccount: number; name: string; currency: string };
}

export const scenario: CashSecurityScenario = JSON.parse(
  fs.readFileSync(
    path.resolve(
      __dirname,
      '../../backend/grafioschtrader-server/src/test/resources/testdata/cash-security-transactions.json'
    ),
    'utf8'
  )
);
expect(scenario.e2e).toBe('e');
expect(scenario.transactions.every((transaction) => transaction.e2e === 'e')).toBeTruthy();

const menus = {
  ACCUMULATE: /^(Buy|Kaufen)\.\.\.$/i,
  REDUCE: /^(Sell|Verkaufen)\.\.\.$/i,
  DIVIDEND: /^(Interest\/Dividends|Zins\/Dividende)\.\.\.$/i
};

/** Backend whose info endpoint is checked; E2E_BACKEND_URL selects an isolated stack like in the other specs. */
const BACKEND_URL = process.env.E2E_BACKEND_URL ?? 'http://localhost:8080';

/** Guard every direct data access, including cleanup, against accidentally targeting another stack. */
async function headers(page: Page): Promise<Record<string, string>> {
  expect(new URL(page.url()).port).toBe(new URL(process.env.E2E_FRONTEND_URL ?? 'http://localhost:4200').port);
  const info = await page.request.get(`${BACKEND_URL}/api/gtinfo`);
  expect(info.ok()).toBeTruthy();
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token).toBeTruthy();
  return { 'x-auth-token': token!, Accept: 'application/json' };
}

async function transactions(page: Page): Promise<StoredTransaction[]> {
  const response = await page.request.get('/api/transaction', { headers: await headers(page) });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}

function owned(row: StoredTransaction, fixture: CashSecurityTransaction): boolean {
  return (
    row.note === fixture.note &&
    row.transactionType === fixture.transactionType &&
    row.security?.isin === scenario.securityIsin &&
    row.security.currency === scenario.securityCurrency &&
    row.cashaccount.name === scenario.cashAccountName &&
    row.transactionTime.startsWith(fixture.date)
  );
}

/** Authenticate in the app and require the funding supplied by 120; never manufacture another spec's deposit. */
export async function beginCashSecurityScenario(page: Page): Promise<void> {
  page.setDefaultTimeout(15_000);
  const info = await page.request.get(`${BACKEND_URL}/api/gtinfo`);
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  await loginAsFixtureUser(page, scenario.loginNickname);
  const rows = await transactions(page);
  expect(
    rows.some(
      (row) =>
        row.transactionType === 'DEPOSIT' &&
        row.cashaccount.name === scenario.cashAccountName &&
        row.transactionTime.startsWith('2008-01-03')
    ),
    '120 deposit on CT CHF is a prerequisite'
  ).toBeTruthy();
}

/** REST deletion runs the backend's units checks and adjusts all associated hold_* tables. */
export async function deleteOwnedTransactions(page: Page, fixtures: CashSecurityTransaction[]): Promise<void> {
  for (const fixture of fixtures) {
    for (const row of (await transactions(page)).filter((row) => owned(row, fixture))) {
      const response = await page.request.delete(`/api/transaction/${row.idTransaction}`, {
        headers: await headers(page)
      });
      expect(response.ok(), await response.text()).toBeTruthy();
    }
  }
  const remaining = await transactions(page);
  expect(remaining.filter((row) => fixtures.some((fixture) => owned(row, fixture)))).toHaveLength(0);
}

/** Preserve a purchase backing dividends or other consumers; its UI edit still exercises saving on every rerun. */
export async function cleanupTrades(page: Page): Promise<boolean> {
  const [buy, sell] = scenario.transactions;
  await deleteOwnedTransactions(page, [sell]);
  const rows = await transactions(page);
  const purchases = rows.filter((row) => owned(row, buy));
  const hasConsumers = purchases.some((buyRow) =>
    rows.some(
      (row) =>
        row.idSecurityaccount === buyRow.idSecurityaccount &&
        row.security?.idSecuritycurrency === buyRow.security!.idSecuritycurrency &&
        !owned(row, buy)
    )
  );
  if (hasConsumers) {
    await expectCashSecurityTransactions(page, [buy]);
    return true;
  }
  await deleteOwnedTransactions(page, [buy]);
  return false;
}

/** Verify the persisted domain values, including the settled amount independently calculated in the fixture. */
export async function expectCashSecurityTransactions(page: Page, fixtures: CashSecurityTransaction[]): Promise<void> {
  const rows = await transactions(page);
  for (const fixture of fixtures) {
    const matches = rows.filter((row) => owned(row, fixture));
    expect(matches, fixture.note).toHaveLength(1);
    const actual = matches[0];
    for (const field of ['units', 'quotation', 'taxCost', 'transactionCost'] as const) {
      expect(actual[field], `${fixture.note}: ${field}`).toBeCloseTo(fixture[field], 8);
    }
    expect(actual.cashaccountAmount).toBeCloseTo(fixture.expectedCashaccountAmount, 2);
    // The backend derives the effective rate again from the rounded cash settlement (4.99 / 5.25 for the dividend).
    const gross = fixture.units * fixture.quotation;
    const costs = fixture.taxCost + fixture.transactionCost;
    const securityAmount = fixture.transactionType === 'ACCUMULATE' ? -(gross + costs) : gross - costs;
    expect(actual.currencyExRate).toBeCloseTo(fixture.expectedCashaccountAmount / securityAmount, 8);
    expect(actual.transactionTime).toBe(`${fixture.date}T12:00:00`);
    expect(actual.cashaccount.currency).toBe(scenario.cashCurrency);
    expect(actual.connectedIdTransaction).toBeNull();
    if (fixture.exDate) {
      expect(actual.exDate).toBe(fixture.exDate);
      expect(actual.taxableInterest).toBe(true);
    }
  }
}

/** Read the same dated holdings endpoint that supplies the dialog's account/quantity options. */
export async function expectHolding(page: Page, units: number, date: string): Promise<void> {
  const purchase = (await transactions(page)).find((row) => owned(row, scenario.transactions[0]));
  expect(purchase, '122 purchase is required').toBeDefined();
  if (!purchase?.security) throw new Error('122 purchase with security is required');
  const response = await page.request.get(
    `/api/security/${purchase.security.idSecuritycurrency}/date/${date.replaceAll('-', '')}2359/false`,
    {
      headers: await headers(page)
    }
  );
  expect(response.ok(), await response.text()).toBeTruthy();
  const holding = (await response.json()).securityaccountOpenPositionUnitsList.find(
    (row: { idSecurityaccount: number }) => row.idSecurityaccount === purchase.idSecurityaccount
  );
  expect(holding?.units, 'dated holding after REST cleanup / dialog save').toBe(units);
}

async function openMenu(page: Page, row: Locator, name: RegExp): Promise<void> {
  // A watchlist refresh can replace its selected row between selection and the menu event.
  for (let attempt = 0; attempt < 3; attempt++) {
    await row.click();
    await row.click({ button: 'right' });
    const item = page.locator('[role="menu"]:visible').getByText(name);
    if (await item.isVisible()) {
      await item.click();
      return;
    }
    await page.keyboard.press('Escape');
  }
  throw new Error(`Transaction menu ${name} unavailable`);
}

async function openDialog(page: Page, fixture: CashSecurityTransaction, editExisting: boolean): Promise<Locator> {
  await page.getByRole('treeitem', { name: scenario.watchlistName, exact: true }).first().click();
  const table = watchlistTable(page);
  const row = table
    .locator('p-table tbody > tr')
    .filter({ has: page.getByRole('cell', { name: scenario.securityName, exact: true }) });
  await expect(row).toHaveCount(1);
  if (editExisting) {
    await row.locator('td').first().locator('a, button').first().click();
    const purchase = page
      .locator('transaction-security-table tbody > tr')
      .filter({ hasText: toShortDate(fixture.date, getUser(scenario.loginNickname).localeStr) });
    await expect(purchase).toHaveCount(1);
    await openMenu(page, purchase, /^(Edit Transaction|Bearbeiten Transaktion)$/i);
  } else {
    await openMenu(page, row, menus[fixture.transactionType]);
  }
  const dialog = page.locator('.p-dialog:visible').filter({ has: page.locator('select#transactionType') });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('select#transactionType')).toHaveValue(fixture.transactionType);
  await expect(dialog.locator('select#idSecuritycurrency option:checked')).toContainText(scenario.securityName);
  if (editExisting) await expect(dialog.locator('textarea#note')).toHaveValue(fixture.note);
  return dialog;
}

/** Keyboard entry is required for Optimus inputNumber; reading it back proves the displayed value. */
async function number(dialog: Locator, field: string, value: number): Promise<void> {
  const input = dialog.locator(`#${field} input`);
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(String(value), { delay: 20 });
  await input.press('Tab');
  await expectNumber(dialog, field, value);
}

async function expectNumber(dialog: Locator, field: string, value: number): Promise<void> {
  await expect
    .poll(async () => Number((await dialog.locator(`#${field} input`).inputValue()).replace(/[^\d.\-]/g, '')))
    .toBeCloseTo(value, 2);
}

/** Use the calendar controls: a prefilled datetime can repaint intermediate typed text from its old model. */
async function setDate(dialog: Locator, field: string, date: string): Promise<void> {
  const expected =
    toShortDate(date, getUser(scenario.loginNickname).localeStr) + (field === 'transactionTime' ? ' 12:00' : '');
  if ((await dialog.locator(`#${field} input`).inputValue()) === expected) return;
  const [year, month, day] = date.split('-').map(Number);
  await dialog.locator(`#${field} button`).click();
  const panel = dialog.page().locator('.p-datepicker-panel:visible').last();
  await panel.locator('.p-datepicker-select-year').click();
  const years = panel.locator('.p-datepicker-year-view');
  await expect(years).toBeVisible();
  for (let attempt = 0; attempt < 20; attempt++) {
    // A selected year repeats its text in an accessibility span, so exact text matching misses it.
    const first = Number((await years.locator(':scope > span').first().innerText()).trim().slice(0, 4));
    if (year >= first && year < first + 10) {
      await years
        .locator(':scope > span')
        .nth(year - first)
        .click();
      break;
    }
    await panel.locator(year < first ? '.p-datepicker-prev-button' : '.p-datepicker-next-button').click();
    if (attempt === 19) throw new Error(`Datepicker did not expose year ${year}`);
  }
  await panel
    .locator('.p-datepicker-month-view > span')
    .nth(month - 1)
    .click();
  if (field === 'transactionTime') {
    for (const [selector, target, modulus] of [
      ['hour', 12, 24],
      ['minute', 0, 60]
    ] as const) {
      const picker = panel.locator(`.p-datepicker-${selector}-picker`);
      const current = Number(await picker.locator(':scope > span').innerText());
      const forward = (target - current + modulus) % modulus;
      const backward = (current - target + modulus) % modulus;
      for (let i = 0; i < Math.min(forward, backward); i++)
        await picker
          .locator('button')
          .nth(forward <= backward ? 0 : 1)
          .click();
    }
  }
  await panel.locator(`[data-date="${year}-${month - 1}-${day}"]`).click();
  await expect(panel).toBeHidden();
  await expect(dialog.locator(`#${field} input`)).toHaveValue(expected);
}

async function selectAccount(dialog: Locator, field: string, name: string): Promise<void> {
  const select = dialog.locator(`select#${field}`);
  const option = select.locator('option').filter({ hasText: `${name} / ${scenario.portfolioName}` });
  await expect(option).toHaveCount(1);
  if (await select.isEnabled()) await select.selectOption(await option.getAttribute('value'));
  await expect(select.locator('option:checked')).toContainText(`${name} / ${scenario.portfolioName}`);
}

/** Exercise real async options, computed amounts and server errors without forcing Angular change detection. */
export async function createCashSecurityTransaction(
  page: Page,
  fixture: CashSecurityTransaction,
  options: { editExisting?: boolean; calculateExchangeRate?: boolean; rejectUnits?: number } = {}
): Promise<void> {
  await test.step(`${options.editExisting ? 'edit' : 'create'} ${fixture.transactionType}`, async () => {
    const dialog = await openDialog(page, fixture, !!options.editExisting);
    await setDate(dialog, 'transactionTime', fixture.date);
    if (fixture.exDate) await setDate(dialog, 'exDate', fixture.exDate);
    await selectAccount(dialog, 'idSecurityaccount', scenario.securityAccountName);
    if (fixture.transactionType !== 'ACCUMULATE') {
      await expect(dialog.locator('select#idSecurityaccount option:checked')).toContainText(
        new RegExp(`(?:Volume|Menge):\\s*${fixture.transactionType === 'REDUCE' ? 10 : 6}(?:\\D|$)`, 'i')
      );
    }
    const rateResponse = options.editExisting
      ? null
      : page.waitForResponse(
          (response) =>
            new URL(response.url()).pathname ===
            `/api/currencypair/${scenario.securityCurrency}/${scenario.cashCurrency}/${fixture.date.replaceAll('-', '')}`
        );
    await selectAccount(dialog, 'idCashaccount', scenario.cashAccountName);
    await expect(dialog.locator('#currencyExRate input')).toBeEnabled();
    await expect(dialog.locator('#quotationCA input')).toBeVisible();
    await expect(dialog.locator('#securityRisk input')).toBeHidden();
    if (rateResponse) {
      const response = await rateResponse;
      expect(response.ok()).toBeTruthy();
      const lookup = await response.json();
      expect(lookup.historyquote?.close, 'historical exchange-rate suggestion').toBeGreaterThan(0);
      // No further interaction: the HTTP callback itself must repaint the suggested exchange rate.
      await expectNumber(dialog, 'currencyExRate', lookup.historyquote.close);
    }
    await number(dialog, 'currencyExRate', scenario.exchangeRate);
    await number(dialog, 'units', fixture.units);
    await number(dialog, 'quotation', fixture.quotation);
    await number(dialog, 'taxCost', fixture.taxCost);
    await number(dialog, 'transactionCost', fixture.transactionCost);
    await dialog.locator('textarea#note').fill(fixture.note);
    if (options.calculateExchangeRate) {
      await number(dialog, 'currencyExRate', 1);
      await dialog.getByRole('button', { name: 'f(x)', exact: true }).click();
      const calculator = page
        .locator('.p-dialog:visible')
        .filter({ has: page.locator('#cashaccountAmount input:enabled') });
      await expect(calculator).toBeVisible();
      await number(calculator, 'cashaccountAmount', fixture.expectedCashaccountAmount);
      await calculator.locator('button[type="submit"]').click();
      await expect(calculator).toBeHidden();
      await expectNumber(dialog, 'currencyExRate', scenario.exchangeRate);
    }
    await expectNumber(dialog, 'cashaccountAmount', fixture.expectedCashaccountAmount);
    if (options.rejectUnits) {
      await number(dialog, 'units', options.rejectUnits);
      const rejected = await submit(page, dialog);
      expect(rejected.status()).toBe(400);
      await expect(
        page
          .getByRole('alert')
          .filter({ hasText: /units|Stück|Anzahl|Bestand|Einheiten/i })
          .first()
      ).toBeVisible();
      await expect(dialog).toBeVisible();
      // No intervening click: an absent notification must leave this disabled and fail the assertion.
      await expect(dialog.locator('button[type="submit"]')).toBeEnabled();
      expect((await transactions(page)).filter((row) => owned(row, fixture))).toHaveLength(0);
      await number(dialog, 'units', fixture.units);
      await expectNumber(dialog, 'cashaccountAmount', fixture.expectedCashaccountAmount);
    }
    const saved = await submit(page, dialog);
    expect(saved.ok(), await saved.text()).toBeTruthy();
    await expect(dialog).toBeHidden();
    await expect(
      page
        .getByRole('alert')
        .filter({ hasText: /saved|gespeichert/i })
        .first()
    ).toBeVisible();
  });
}

async function submit(page: Page, dialog: Locator) {
  const button = dialog.locator('button[type="submit"]');
  await expect(button).toBeEnabled();
  const response = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/transaction/securitytrans' &&
      ['POST', 'PUT'].includes(response.request().method())
  );
  await button.click();
  return response;
}
