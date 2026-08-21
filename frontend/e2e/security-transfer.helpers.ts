import {expect, Locator, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

import {parseCsvRow} from './helpers';
import {toShortDate} from './portfolio.helpers';

const FIXTURE_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/security-transfer.csv');

const RX = {
  securityAccountsFolder: /^(Securities accounts|Depots)$/i,
  transferSecurity: /^(Transfer security|Wertpapier transferieren)\.\.\.$/i,
};

export interface SecurityTransferFixture {
  loginNickname: string;
  sourcePortfolioName: string;
  sourceAccountName: string;
  targetPortfolioName: string;
  targetAccountName: string;
  securityName: string;
  isin: string;
  currency: string;
  transferDate: string;
  units: number;
  quotation: number;
  note: string;
  e2e: 'd' | 'i' | 'e';
}

interface ApiSecurityAccount {
  idSecuritycashAccount: number;
  name: string;
}

interface ApiPortfolio {
  idPortfolio: number;
  name: string;
  currency: string;
  securityaccountList?: ApiSecurityAccount[];
}

interface ResolvedAccount {
  idSecurityAccount: number;
  portfolioName: string;
  portfolioCurrency: string;
  accountName: string;
}

export interface ResolvedSecurityTransferFixture {
  source: ResolvedAccount;
  target: ResolvedAccount;
}

interface ApiSecurity {
  idSecuritycurrency: number;
  name: string;
  isin: string;
  currency: string;
}

interface ApiHistoryquoteDateClose {
  idSecuritycurrency: number;
  date: string;
  close: number;
}

interface ApiSecurityTransfer {
  idSecurityTransfer: number;
  security: ApiSecurity;
  idSecurityaccountSource: number;
  idSecurityaccountTarget: number;
  transferDate: string;
  units: number;
  quotation: number;
  idTransactionSell: number | null;
  idTransactionBuy: number | null;
  note: string | null;
  reversible: boolean;
}

interface ApiSecurityActionTreeData {
  clientTransfers?: ApiSecurityTransfer[];
}

interface ApiSecurityPosition {
  security: ApiSecurity;
  units: number;
}

interface ApiSecurityPositionGroup {
  securityPositionSummaryList?: ApiSecurityPosition[];
}

interface ApiSecurityPositionGrandSummary {
  securityPositionGroupSummaryList?: ApiSecurityPositionGroup[];
}

/** Loads the security transfers owned by the Playwright suite. */
export function loadE2ESecurityTransfers(): SecurityTransferFixture[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping the security-transfer E2E spec.`);
    return [];
  }
  return fs.readFileSync(FIXTURE_PATH, 'utf8').split(/\r?\n/)
    .filter(line => line.trim().length > 0)
    .map((line, index) => parseFixtureRow(line, index + 1))
    .filter(fixture => fixture.e2e === 'e');
}

/** Resolves both securities accounts by portfolio/account name; no exported database ID is reused. */
export async function resolveTransferAccounts(page: Page,
    fixture: SecurityTransferFixture): Promise<ResolvedSecurityTransferFixture> {
  const response = await page.request.get('/api/portfolio/tenant', {headers: await authHeaders(page)});
  expect(response.ok(), `loading portfolios: ${await response.text()}`).toBeTruthy();
  const portfolios = await response.json() as ApiPortfolio[];
  return {
    source: resolveAccount(portfolios, fixture.sourcePortfolioName, fixture.sourceAccountName),
    target: resolveAccount(portfolios, fixture.targetPortfolioName, fixture.targetAccountName),
  };
}

/** Ensures the exact closing price required by the backend exists, without relying on an async connector import. */
export async function ensureSecurityTransferQuote(page: Page, fixture: SecurityTransferFixture): Promise<void> {
  await test.step('ensure security-transfer closing price', async () => {
    const headers = await authHeaders(page);
    const searchResponse = await page.request.get('/api/security/search', {
      headers,
      params: {isin: fixture.isin, currency: fixture.currency},
    });
    expect(searchResponse.ok(), `searching security ${fixture.isin}/${fixture.currency}: ${await searchResponse.text()}`)
      .toBeTruthy();
    const matches = (await searchResponse.json() as ApiSecurity[])
      .filter(security => security.isin === fixture.isin && security.currency === fixture.currency);
    expect(matches, `security ${fixture.isin}/${fixture.currency}`).toHaveLength(1);

    const quoteUrl = `/api/historyquote/${matches[0].idSecuritycurrency}/${fixture.transferDate.replaceAll('-', '')}/true`;
    let quote = await getHistoryquoteDateClose(page, quoteUrl, headers);
    if (quote?.date === fixture.transferDate) {
      expect(quote.close, `closing price for ${fixture.isin} on ${fixture.transferDate}`)
        .toBeCloseTo(fixture.quotation, 8);
      return;
    }

    const createResponse = await page.request.post('/api/historyquote', {
      headers,
      data: {
        idSecuritycurrency: matches[0].idSecuritycurrency,
        date: fixture.transferDate,
        close: fixture.quotation,
      },
    });
    if (!createResponse.ok()) {
      // The background connector may insert the same quote between the lookup and create requests.
      const createError = await createResponse.text();
      quote = await getHistoryquoteDateClose(page, quoteUrl, headers);
      expect(quote?.date, `creating ${fixture.isin} quote on ${fixture.transferDate}: ${createError}`)
        .toBe(fixture.transferDate);
    } else {
      quote = await getHistoryquoteDateClose(page, quoteUrl, headers);
    }
    expect(quote?.date, `closing price date for ${fixture.isin}`).toBe(fixture.transferDate);
    expect(quote?.close, `closing price for ${fixture.isin} on ${fixture.transferDate}`)
      .toBeCloseTo(fixture.quotation, 8);
  });
}

/**
 * Removes an exact transfer from a prior run through the public reverse endpoint. Reversal intentionally schedules a
 * holdings rebuild one minute later, so polling the source position is part of startup reconciliation.
 */
export async function reconcileSecurityTransfer(page: Page, fixture: SecurityTransferFixture,
    resolved: ResolvedSecurityTransferFixture): Promise<void> {
  await test.step('reconcile prior security transfer', async () => {
    const headers = await authHeaders(page);
    const tree = await getSecurityActionTree(page, headers);
    const matches = (tree.clientTransfers ?? [])
      .filter(transfer => matchesFixture(transfer, fixture, resolved));
    expect(matches.length, 'matching transfer rows before startup reconciliation').toBeLessThanOrEqual(1);

    if (matches.length === 1) {
      expect(matches[0].reversible, 'matching transfer must have no later target-account transactions').toBe(true);
      const response = await page.request.delete(`/api/securityaction/transfer/${matches[0].idSecurityTransfer}`,
        {headers});
      expect(response.ok(), `reversing transfer ${matches[0].idSecurityTransfer}: ${await response.text()}`)
        .toBeTruthy();
    }

    await expect.poll(async () => {
      const refreshed = await getSecurityActionTree(page, headers);
      return (refreshed.clientTransfers ?? []).filter(transfer => matchesFixture(transfer, fixture, resolved)).length;
    }, {message: 'matching transfer after startup reconciliation', timeout: 15_000}).toBe(0);

    await expect.poll(async () => await getHoldingUnits(page, headers, resolved.source.idSecurityAccount, fixture), {
      message: `source holding ${fixture.sourcePortfolioName}/${fixture.sourceAccountName}/${fixture.isin}`,
      timeout: 120_000,
      intervals: [1_000, 2_000, 5_000],
    }).toBe(fixture.units);
  });
}

/** Opens the source account, selects the fixture security, and launches SecurityTransferCreateComponent. */
export async function openSecurityTransferDialog(page: Page, fixture: SecurityTransferFixture,
    resolved: ResolvedSecurityTransferFixture): Promise<Locator> {
  return await test.step('open security-transfer form', async () => {
    const portfolioText = `${resolved.source.portfolioName} / ${resolved.source.portfolioCurrency}`;
    const portfolioItem = page.getByRole('treeitem', {name: portfolioText, exact: true}).first();
    await portfolioItem.waitFor({state: 'visible', timeout: 15_000});
    await expandTreeItem(portfolioItem);
    await portfolioItem.locator('.p-tree-node-content').first().click();

    const accountFolder = portfolioItem.getByRole('treeitem', {name: RX.securityAccountsFolder}).first();
    await accountFolder.waitFor({state: 'visible', timeout: 10_000});
    await expandTreeItem(accountFolder);

    const accountItem = accountFolder.getByRole('treeitem', {name: resolved.source.accountName, exact: true}).first();
    await accountItem.waitFor({state: 'visible', timeout: 10_000});
    await accountItem.click();

    const table = page.locator('.data-container p-table').first();
    const row = table.locator('tbody > tr[data-p-selectable-row]')
      .filter({has: page.getByRole('cell', {name: fixture.securityName, exact: true})});
    await expect(row, `source position row for ${fixture.securityName}`).toHaveCount(1, {timeout: 15_000});
    await openRowContextMenu(page, row, RX.transferSecurity);

    const dialog = page.locator('.p-dialog:visible')
      .filter({has: page.locator('select#idPortfolioTarget')}).first();
    await dialog.waitFor({state: 'visible', timeout: 10_000});
    return dialog;
  });
}

/** Populates every editable SecurityTransferCreateComponent field from the fixture and submits it. */
export async function submitSecurityTransfer(page: Page, dialog: Locator, fixture: SecurityTransferFixture,
    locale: string): Promise<void> {
  await test.step('submit security-transfer form from fixture', async () => {
    const securityName = dialog.locator('input#securityName');
    const sourceAccount = dialog.locator('input#sourceAccount');
    await expect(securityName).toBeDisabled();
    await expect(securityName).toHaveValue(fixture.securityName);
    await expect(sourceAccount).toBeDisabled();
    await expect(sourceAccount).toHaveValue(fixture.sourceAccountName);

    await selectOptionByLabel(dialog.locator('select#idPortfolioTarget'), fixture.targetPortfolioName);
    await selectOptionByLabel(dialog.locator('select#idSecurityaccountTarget'), fixture.targetAccountName);
    await fillDate(dialog, fixture.transferDate, locale);
    await fillText(dialog.locator('textarea#note'), fixture.note);

    const responsePromise = page.waitForResponse(response =>
      response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/securityaction/transfer', {timeout: 30_000});
    const submit = dialog.locator('button[type="submit"]');
    await expect(submit).toBeEnabled({timeout: 10_000});
    await submit.click();
    const response = await responsePromise;
    expect(response.ok(), `creating security transfer: ${await response.text()}`).toBeTruthy();
    await dialog.waitFor({state: 'hidden', timeout: 15_000});
  });
}

/** Verifies the persisted transfer and both resulting account holdings from natural fixture values. */
export async function expectSecurityTransfer(page: Page, fixture: SecurityTransferFixture,
    resolved: ResolvedSecurityTransferFixture): Promise<void> {
  await test.step('verify persisted security transfer', async () => {
    const headers = await authHeaders(page);
    const tree = await getSecurityActionTree(page, headers);
    const matches = (tree.clientTransfers ?? [])
      .filter(transfer => matchesFixture(transfer, fixture, resolved));
    expect(matches, 'persisted fixture transfer').toHaveLength(1);
    expect(matches[0].idTransactionSell, 'source SELL transaction').toBeGreaterThan(0);
    expect(matches[0].idTransactionBuy, 'target BUY transaction').toBeGreaterThan(0);

    await expect.poll(async () => await getHoldingUnits(page, headers, resolved.source.idSecurityAccount, fixture), {
      message: 'source holding after transfer', timeout: 30_000,
    }).toBe(0);
    await expect.poll(async () => await getHoldingUnits(page, headers, resolved.target.idSecurityAccount, fixture), {
      message: 'target holding after transfer', timeout: 30_000,
    }).toBe(fixture.units);
  });
}

function parseFixtureRow(line: string, lineNumber: number): SecurityTransferFixture {
  const columns = parseCsvRow(line);
  if (columns.length !== 13) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: expected 13 columns, got ${columns.length}`);
  }
  const units = Number(columns[9]);
  const quotation = Number(columns[10]);
  if (!Number.isFinite(units) || units <= 0 || !Number.isFinite(quotation) || quotation <= 0) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: units and quotation must be positive numbers`);
  }
  assertIsoDate(columns[8], lineNumber);
  if (!['d', 'i', 'e'].includes(columns[12])) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: invalid e2e routing tag '${columns[12]}'`);
  }
  for (const [index, value] of columns.entries()) {
    if (index !== 11 && value.trim().length === 0) {
      throw new Error(`${FIXTURE_PATH}:${lineNumber}: column ${index + 1} must not be empty`);
    }
  }
  return {
    loginNickname: columns[0],
    sourcePortfolioName: columns[1],
    sourceAccountName: columns[2],
    targetPortfolioName: columns[3],
    targetAccountName: columns[4],
    securityName: columns[5],
    isin: columns[6],
    currency: columns[7],
    transferDate: columns[8],
    units,
    quotation,
    note: columns[11],
    e2e: columns[12] as SecurityTransferFixture['e2e'],
  };
}

function assertIsoDate(value: string, lineNumber: number): void {
  const parsed = new Date(`${value}T00:00:00.000Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(parsed.getTime())
      || parsed.toISOString().slice(0, 10) !== value) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: invalid transfer date '${value}'`);
  }
}

function resolveAccount(portfolios: ApiPortfolio[], portfolioName: string, accountName: string): ResolvedAccount {
  const matches = portfolios.filter(portfolio => portfolio.name === portfolioName)
    .flatMap(portfolio => (portfolio.securityaccountList ?? [])
      .filter(account => account.name === accountName)
      .map(account => ({
        idSecurityAccount: account.idSecuritycashAccount,
        portfolioName: portfolio.name,
        portfolioCurrency: portfolio.currency,
        accountName: account.name,
      })));
  expect(matches, `securities account ${portfolioName}/${accountName}`).toHaveLength(1);
  return matches[0];
}

async function getSecurityActionTree(page: Page,
    headers: Record<string, string>): Promise<ApiSecurityActionTreeData> {
  const response = await page.request.get('/api/securityaction/tree', {headers});
  expect(response.ok(), `loading Security Actions: ${await response.text()}`).toBeTruthy();
  return await response.json() as ApiSecurityActionTreeData;
}

async function getHistoryquoteDateClose(page: Page, url: string,
    headers: Record<string, string>): Promise<ApiHistoryquoteDateClose | null> {
  const response = await page.request.get(url, {headers});
  const body = await response.text();
  expect(response.ok(), `loading history quote: ${body}`).toBeTruthy();
  return body.length > 0 ? JSON.parse(body) as ApiHistoryquoteDateClose : null;
}

function matchesFixture(transfer: ApiSecurityTransfer, fixture: SecurityTransferFixture,
    resolved: ResolvedSecurityTransferFixture): boolean {
  return transfer.security?.name === fixture.securityName
    && transfer.security?.isin === fixture.isin
    && transfer.security?.currency === fixture.currency
    && transfer.idSecurityaccountSource === resolved.source.idSecurityAccount
    && transfer.idSecurityaccountTarget === resolved.target.idSecurityAccount
    && transfer.transferDate === fixture.transferDate
    && transfer.units === fixture.units
    && Math.abs(transfer.quotation - fixture.quotation) < 0.00000001
    && (transfer.note ?? '') === fixture.note;
}

async function getHoldingUnits(page: Page, headers: Record<string, string>, idSecurityAccount: number,
    fixture: SecurityTransferFixture): Promise<number> {
  const response = await page.request.get(`/api/securityaccount/${idSecurityAccount}/securityaccountsummary/currency`, {
    headers,
    params: {includeClosedPosition: 'false', untilDate: new Date().toISOString().slice(0, 10)},
  });
  expect(response.ok(), `loading securities account ${idSecurityAccount}: ${await response.text()}`).toBeTruthy();
  const summary = await response.json() as ApiSecurityPositionGrandSummary;
  const matches = (summary.securityPositionGroupSummaryList ?? [])
    .flatMap(group => group.securityPositionSummaryList ?? [])
    .filter(position => position.security?.name === fixture.securityName
      && position.security?.isin === fixture.isin && position.security?.currency === fixture.currency);
  expect(matches.length, `holding ${fixture.isin}/${fixture.currency} in account ${idSecurityAccount}`)
    .toBeLessThanOrEqual(1);
  return matches[0]?.units ?? 0;
}

async function expandTreeItem(item: Locator): Promise<void> {
  if (await item.getAttribute('aria-expanded') === 'false') {
    await item.getByRole('button').first().click();
  }
}

async function openRowContextMenu(page: Page, row: Locator, itemRx: RegExp): Promise<void> {
  for (let attempt = 1; attempt <= 3; attempt++) {
    await row.click();
    await page.waitForTimeout(250);
    await row.click({button: 'right'});
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 5_000});
    const item = menu.getByText(itemRx).first();
    if (await item.waitFor({state: 'visible', timeout: 2_000}).then(() => true).catch(() => false)) {
      await item.click();
      return;
    }
    await page.keyboard.press('Escape');
  }
  throw new Error('Security-transfer menu item did not become available for the selected position');
}

async function selectOptionByLabel(select: Locator, label: string): Promise<void> {
  await select.waitFor({state: 'visible', timeout: 10_000});
  await expect(select.locator('option', {hasText: exactText(label)})).toHaveCount(1, {timeout: 15_000});
  await select.selectOption({label});
  await select.dispatchEvent('change');
}

async function fillDate(dialog: Locator, isoDate: string, locale: string): Promise<void> {
  const input = dialog.locator('#transferDate input, input#transferDate').first();
  const value = toShortDate(isoDate, locale);
  await input.waitFor({state: 'visible', timeout: 10_000});
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  // Optimus parses on every keyboard-generated input event. Insert the complete date in one event so an incomplete
  // intermediate value is not rejected and repainted as empty before the next character is entered.
  await input.press('ArrowRight');
  await input.fill(value);
  await input.press('Tab');
  await expect(input).toHaveValue(value);
}

async function fillText(input: Locator, value: string): Promise<void> {
  await input.waitFor({state: 'visible', timeout: 10_000});
  await input.fill(value);
  await input.dispatchEvent('input');
  await input.blur();
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return {'x-auth-token': token!, Accept: 'application/json'};
}

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}
