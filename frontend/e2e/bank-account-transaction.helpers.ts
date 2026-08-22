import { expect, Locator, Page, test } from '@playwright/test';
import { getUser } from './helpers';
import {
  BankAccountTransactionData,
  CashTransferData,
  loadPortfolios,
  PortfolioFixture,
  portfolioNode,
  SingleCashTransactionData,
  toShortDate
} from './portfolio.helpers';

export interface BankTransactionFixture {
  portfolio: PortfolioFixture;
  transaction: BankAccountTransactionData;
}

interface ApiCashaccount {
  name: string;
}

interface ApiCurrencypair {
  idSecuritycurrency: number;
}

interface ApiCurrencypairWithHistoryquote {
  currencypair?: ApiCurrencypair | null;
  historyquote?: { date: string; close: number } | null;
}

interface ApiTransaction {
  idTransaction: number;
  connectedIdTransaction?: number | null;
  transactionTime: number | string;
  transactionType: string;
  cashaccountAmount: number;
  currencyExRate?: number | null;
  taxCost?: number | null;
  note?: string | null;
  idSecurityaccount?: number | null;
  cashaccount: ApiCashaccount;
}

const RX = {
  singleTransaction: /^(Single account transaction|Kontotransaktion)\.\.\.$/i,
  accountTransfer: /^(Account transfer|Kontoübertrag)\.\.\.$/i
};

/** Returns every Playwright-owned bank transaction together with its source portfolio. */
export function loadBankTransactions(): BankTransactionFixture[] {
  return loadPortfolios().flatMap((portfolio) =>
    (portfolio.transactions ?? []).map((transaction) => ({ portfolio, transaction }))
  );
}

/** Ensures exact-date valuation rates required by standalone foreign-currency cash transactions. */
export async function ensureBankTransactionExchangeRates(
  page: Page,
  fixtures: BankTransactionFixture[]
): Promise<void> {
  await test.step('ensure required bank-transaction exchange rates', async () => {
    const processed = new Set<string>();
    for (const fixture of fixtures) {
      if (fixture.transaction.kind !== 'single' || !fixture.transaction.valuationExchangeRate) {
        continue;
      }

      const transaction = fixture.transaction;
      const account = findCashAccount(
        fixture.portfolio.loginNickname,
        fixture.portfolio.name,
        transaction.cashAccount
      ).account;
      const rate = transaction.valuationExchangeRate;
      const key = `${account.currency}/${rate.toCurrency}/${transaction.date}`;
      if (processed.has(key)) {
        continue;
      }
      processed.add(key);
      await ensureHistoryquote(page, account.currency, rate.toCurrency, transaction.date, rate.close);
    }
  });
}

/**
 * Returns fixture operations that are not persisted yet. Keeping exact matches is required for initial deposits:
 * the overdraft guard deliberately rejects removing the first positive booking from an account. Reconciliation is
 * also retry-safe after a partial run because a transfer is saved atomically and standalone rows are independent.
 */
export async function findMissingBankTransactions(
  page: Page,
  fixtures: BankTransactionFixture[]
): Promise<BankTransactionFixture[]> {
  return await test.step('reconcile existing fixture transactions', async () => {
    let transactions = await getTenantTransactions(page);
    for (const fixture of fixtures) {
      if (!isFixturePersisted(transactions, fixture)) {
        const conflicts = transactions.filter((transaction) => sameFixtureIdentity(transaction, fixture));
        for (const conflict of conflicts) {
          const response = await page.request.delete(`/api/transaction/${conflict.idTransaction}`, {
            headers: await authHeaders(page)
          });
          expect(
            response.ok(),
            `deleting conflicting transaction ${conflict.idTransaction}: ` + (await response.text())
          ).toBeTruthy();
        }
        if (conflicts.length > 0) {
          transactions = await getTenantTransactions(page);
        }
      }
    }
    return fixtures.filter((fixture) => !isFixturePersisted(transactions, fixture));
  });
}

/** Creates a single cash-account transaction through the transaction dialog. */
export async function createSingleBankTransaction(page: Page, fixture: BankTransactionFixture): Promise<void> {
  const transaction = fixture.transaction as SingleCashTransactionData;
  await openTransactionDialog(page, fixture.portfolio, transaction.cashAccount, RX.singleTransaction);

  const dialog = page.locator('.p-dialog:visible');
  await selectWhenReady(dialog.locator('select#transactionType'), transaction.transactionType);
  await setTransactionDate(dialog, transaction.date, fixture.portfolio.loginNickname);

  if (transaction.securityAccount) {
    await selectWhenReady(dialog.locator('select#idSecurityaccount'), { label: transaction.securityAccount });
  }
  await fillNumber(dialog, 'cashaccountAmount', transaction.amount);
  if (transaction.taxCost !== undefined) {
    await fillNumber(dialog, 'taxCost', transaction.taxCost);
  }
  if (transaction.note !== undefined) {
    await dialog.locator('textarea#note').fill(transaction.note);
  }

  await submitTransaction(page, dialog, '/api/transaction/singlecashtrans');
}

/** Creates a connected withdrawal/deposit pair through the account-transfer dialog. */
export async function createBankTransfer(page: Page, fixture: BankTransactionFixture): Promise<void> {
  const transaction = fixture.transaction as CashTransferData;
  await openTransactionDialog(page, fixture.portfolio, transaction.debitCashAccount, RX.accountTransfer);

  const dialog = page.locator('.p-dialog:visible');
  await setTransactionDate(dialog, transaction.date, fixture.portfolio.loginNickname);

  const destination = findCashAccount(
    fixture.portfolio.loginNickname,
    transaction.creditPortfolio,
    transaction.creditCashAccount
  );
  const exchangeRateLookup =
    transaction.exchangeRate === undefined
      ? null
      : page.waitForResponse(
          (response) =>
            response.request().method() === 'GET' &&
            /\/api\/currencypair\/[A-Z]{3}\/[A-Z]{3}\/\d{8}$/.test(new URL(response.url()).pathname),
          { timeout: 15_000 }
        );
  await selectWhenReady(dialog.locator('select#idCreditCashaccount'), {
    label: `${destination.account.name} / ${destination.account.currency} / ${destination.portfolio.name}`
  });

  if (transaction.exchangeRate !== undefined) {
    await exchangeRateLookup;
    const rateInput = dialog.locator('#currencyExRate input');
    await expect(rateInput).toBeEnabled({ timeout: 10_000 });
    await fillNumber(dialog, 'currencyExRate', transaction.exchangeRate);
  }
  await fillNumber(dialog, 'creditAmount', transaction.creditAmount);
  if (transaction.transactionCost !== undefined) {
    await fillNumber(dialog, 'transactionCost', transaction.transactionCost);
  }
  if (transaction.note !== undefined) {
    await dialog.locator('textarea#note').fill(transaction.note);
  }

  await submitTransaction(page, dialog, '/api/transaction/cashaccounttransfer');
}

/** Verifies every standalone row and both connected rows of every transfer through the REST representation. */
export async function expectBankTransactions(page: Page, fixtures: BankTransactionFixture[]): Promise<void> {
  await test.step('verify persisted fixture transactions', async () => {
    const transactions = await getTenantTransactions(page);
    const matchedIds = new Set<number>();

    for (const fixture of fixtures) {
      const debitOrSingle = transactions.find(
        (transaction) => !matchedIds.has(transaction.idTransaction) && matchesFixtureDebitOrSingle(transaction, fixture)
      );
      expect(debitOrSingle, describeFixture(fixture)).toBeDefined();
      matchedIds.add(debitOrSingle.idTransaction);

      if (fixture.transaction.kind === 'single') {
        expectSingleDetails(debitOrSingle, fixture.transaction);
      } else {
        const deposit = transactions.find(
          (transaction) => transaction.idTransaction === debitOrSingle.connectedIdTransaction
        );
        expect(deposit, `${describeFixture(fixture)} deposit side`).toBeDefined();
        matchedIds.add(deposit.idTransaction);
        expectTransferDetails(debitOrSingle, deposit, fixture.transaction);
      }
    }

    const expectedRows = fixtures.reduce(
      (total, fixture) => total + (fixture.transaction.kind === 'transfer' ? 2 : 1),
      0
    );
    expect(matchedIds.size).toBe(expectedRows);
    expectAccountBalances(
      transactions.filter((transaction) => matchedIds.has(transaction.idTransaction)),
      fixtures
    );
  });
}

async function openTransactionDialog(
  page: Page,
  portfolio: PortfolioFixture,
  cashAccount: string,
  menuItem: RegExp
): Promise<void> {
  const node = portfolioNode(page, portfolio);
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.click();

  const summary = page.locator('.data-container').first();
  await summary.waitFor({ state: 'visible', timeout: 15_000 });
  const row = summary
    .locator('p-table')
    .first()
    .locator('tbody > tr[data-p-selectable-row]')
    .filter({ has: page.getByRole('cell', { name: cashAccount, exact: true }) })
    .first();
  await row.waitFor({ state: 'visible', timeout: 15_000 });
  for (let attempt = 1; attempt <= 3; attempt++) {
    // Selection and the outer panel's menu update happen in separate Angular event handlers. Under load, opening the
    // context menu immediately can still expose only "New cash account" from the previously unselected panel.
    await row.click();
    await page.waitForTimeout(250);
    await row.click({ button: 'right' });

    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({ state: 'visible', timeout: 5_000 });
    const item = menu.getByText(menuItem);
    if (
      await item
        .waitFor({ state: 'visible', timeout: 2_000 })
        .then(() => true)
        .catch(() => false)
    ) {
      await item.click();
      await page.locator('.p-dialog:visible').waitFor({ state: 'visible', timeout: 10_000 });
      return;
    }
    await page.keyboard.press('Escape');
  }
  throw new Error(`Account transaction menu did not become available for '${cashAccount}'`);
}

async function setTransactionDate(dialog: Locator, isoDate: string, loginNickname: string): Promise<void> {
  const locale = getUser(loginNickname).localeStr;
  const input = dialog.locator('#transactionTime input');
  await input.waitFor({ state: 'visible', timeout: 10_000 });
  const value = `${toShortDate(isoDate, locale)} 12:00`;
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(value, { delay: 15 });
  await input.blur();
  await expect(input).toHaveValue(value);
}

async function fillNumber(dialog: Locator, field: string, value: number): Promise<void> {
  const input = dialog.locator(`#${field} input`);
  await input.waitFor({ state: 'visible', timeout: 10_000 });
  await input.click();
  if (value < 0) {
    await input.press('Control+a');
    await input.press('Backspace');
    await input.pressSequentially(String(value), { delay: 30 });
  } else {
    await input.fill(String(value));
    await input.dispatchEvent('input');
  }
  await input.press('Tab');
}

async function selectWhenReady(select: Locator, option: string | { label: string }): Promise<void> {
  await select.waitFor({ state: 'visible', timeout: 10_000 });
  await expect(select.locator('option')).not.toHaveCount(0, { timeout: 10_000 });
  await select.selectOption(option);
  await select.dispatchEvent('change');
}

async function submitTransaction(page: Page, dialog: Locator, endpoint: string): Promise<void> {
  const submit = dialog.locator('button[type="submit"]');
  await expect(submit).toBeEnabled({ timeout: 10_000 });
  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && response.url().includes(endpoint)
  );
  await submit.click();
  const response = await responsePromise;
  expect(response.ok(), `${endpoint}: ${await response.text()}`).toBeTruthy();
  await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
}

function findCashAccount(loginNickname: string, portfolioName: string, accountName: string) {
  const portfolio = loadPortfolios().find(
    (item) => item.loginNickname === loginNickname && item.name === portfolioName
  );
  expect(portfolio, `portfolio '${portfolioName}' of '${loginNickname}' in portfolios.json`).toBeDefined();
  const account = portfolio.cashAccounts.find((item) => item.name === accountName);
  expect(account, `cash account '${accountName}' in portfolio '${portfolioName}'`).toBeDefined();
  return { portfolio, account };
}

async function getTenantTransactions(page: Page): Promise<ApiTransaction[]> {
  const response = await page.request.get('/api/transaction', { headers: await authHeaders(page) });
  expect(response.ok(), `loading tenant transactions: ${await response.text()}`).toBeTruthy();
  return (await response.json()) as ApiTransaction[];
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token, Accept: 'application/json' };
}

async function ensureHistoryquote(
  page: Page,
  fromCurrency: string,
  toCurrency: string,
  date: string,
  close: number
): Promise<void> {
  const datePath = date.replaceAll('-', '');
  const headers = await authHeaders(page);
  const lookupUrl = `/api/currencypair/${fromCurrency}/${toCurrency}/${datePath}`;
  let lookup = await getCurrencypairWithHistoryquote(page, lookupUrl, headers);

  let currencypair = lookup.currencypair;
  if (!currencypair) {
    const pairResponse = await page.request.get(`/api/currencypair/${fromCurrency}/${toCurrency}`, { headers });
    expect(
      pairResponse.ok(),
      `creating currency pair ${fromCurrency}/${toCurrency}: ${await pairResponse.text()}`
    ).toBeTruthy();
    currencypair = (await pairResponse.json()) as ApiCurrencypair;
    lookup = await getCurrencypairWithHistoryquote(page, lookupUrl, headers);
  }

  if (lookup.historyquote?.date === date) {
    return;
  }

  const createResponse = await page.request.post('/api/historyquote', {
    headers,
    data: { idSecuritycurrency: currencypair.idSecuritycurrency, date, close }
  });
  if (!createResponse.ok()) {
    // An asynchronous connector may have inserted the quote between lookup and create.
    lookup = await getCurrencypairWithHistoryquote(page, lookupUrl, headers);
    expect(
      lookup.historyquote?.date,
      `creating ${fromCurrency}/${toCurrency} quote on ${date}: ${await createResponse.text()}`
    ).toBe(date);
  }
}

async function getCurrencypairWithHistoryquote(
  page: Page,
  url: string,
  headers: Record<string, string>
): Promise<ApiCurrencypairWithHistoryquote> {
  const response = await page.request.get(url, { headers });
  expect(response.ok(), `loading ${url}: ${await response.text()}`).toBeTruthy();
  return (await response.json()) as ApiCurrencypairWithHistoryquote;
}

function matchesFixtureDebitOrSingle(transaction: ApiTransaction, fixture: BankTransactionFixture): boolean {
  const data = fixture.transaction;
  if (data.kind === 'single') {
    return (
      transaction.cashaccount?.name === data.cashAccount &&
      transaction.transactionType === data.transactionType &&
      transactionDate(transaction) === data.date &&
      closeTo(transaction.cashaccountAmount, expectedSingleAmount(data))
    );
  }
  return (
    transaction.cashaccount?.name === data.debitCashAccount &&
    transaction.transactionType === 'WITHDRAWAL' &&
    transaction.connectedIdTransaction != null &&
    transactionDate(transaction) === data.date &&
    closeTo(transaction.cashaccountAmount, -data.expectedDebitAmount)
  );
}

function sameFixtureIdentity(transaction: ApiTransaction, fixture: BankTransactionFixture): boolean {
  const data = fixture.transaction;
  if (data.kind === 'single') {
    return (
      transaction.cashaccount?.name === data.cashAccount &&
      transaction.transactionType === data.transactionType &&
      transactionDate(transaction) === data.date &&
      (data.note === undefined || transaction.note === data.note)
    );
  }
  return (
    transaction.cashaccount?.name === data.debitCashAccount &&
    transaction.transactionType === 'WITHDRAWAL' &&
    transactionDate(transaction) === data.date
  );
}

function isFixturePersisted(transactions: ApiTransaction[], fixture: BankTransactionFixture): boolean {
  const debitOrSingle = transactions.find((transaction) => matchesFixtureDebitOrSingle(transaction, fixture));
  if (!debitOrSingle || fixture.transaction.kind === 'single') {
    return debitOrSingle !== undefined;
  }
  const deposit = transactions.find(
    (transaction) => transaction.idTransaction === debitOrSingle.connectedIdTransaction
  );
  if (!deposit) {
    return false;
  }
  try {
    expectTransferDetails(debitOrSingle, deposit, fixture.transaction);
    return true;
  } catch {
    return false;
  }
}

function expectSingleDetails(actual: ApiTransaction, expected: SingleCashTransactionData): void {
  expect(actual.connectedIdTransaction).toBeNull();
  if (expected.taxCost !== undefined) {
    expect(actual.taxCost).toBeCloseTo(expected.taxCost, 8);
  }
  if (expected.securityAccount !== undefined) {
    expect(actual.idSecurityaccount).not.toBeNull();
  }
  expect(actual.note ?? undefined).toBe(expected.note);
}

function expectTransferDetails(withdrawal: ApiTransaction, deposit: ApiTransaction, expected: CashTransferData): void {
  expect(deposit.connectedIdTransaction).toBe(withdrawal.idTransaction);
  expect(deposit.cashaccount.name).toBe(expected.creditCashAccount);
  expect(deposit.transactionType).toBe('DEPOSIT');
  expect(transactionDate(deposit)).toBe(expected.date);
  expect(deposit.cashaccountAmount).toBeCloseTo(expected.creditAmount, 8);
  if (expected.exchangeRate === undefined) {
    expect(withdrawal.currencyExRate).toBeNull();
    expect(deposit.currencyExRate).toBeNull();
  } else {
    expect(withdrawal.currencyExRate).toBeCloseTo(expected.exchangeRate, 8);
    expect(deposit.currencyExRate).toBeCloseTo(expected.exchangeRate, 8);
  }
}

function expectAccountBalances(actualTransactions: ApiTransaction[], fixtures: BankTransactionFixture[]): void {
  const expectedBalances = new Map<string, number>();
  for (const fixture of fixtures) {
    const data = fixture.transaction;
    if (data.kind === 'single') {
      addAmount(expectedBalances, data.cashAccount, expectedSingleAmount(data));
    } else {
      addAmount(expectedBalances, data.debitCashAccount, -data.expectedDebitAmount);
      addAmount(expectedBalances, data.creditCashAccount, data.creditAmount);
    }
  }

  const actualBalances = new Map<string, number>();
  actualTransactions.forEach((transaction) =>
    addAmount(actualBalances, transaction.cashaccount.name, transaction.cashaccountAmount)
  );
  for (const [account, expectedBalance] of expectedBalances) {
    expect(actualBalances.get(account), `fixture balance of ${account}`).toBeCloseTo(expectedBalance, 8);
  }
}

function addAmount(amounts: Map<string, number>, account: string, amount: number): void {
  amounts.set(account, (amounts.get(account) ?? 0) + amount);
}

function expectedSingleAmount(transaction: SingleCashTransactionData): number {
  return transaction.transactionType === 'WITHDRAWAL' || transaction.transactionType === 'FEE'
    ? transaction.amount * -1
    : transaction.amount;
}

function transactionDate(transaction: ApiTransaction): string {
  if (typeof transaction.transactionTime === 'number') {
    return new Date(transaction.transactionTime).toISOString().slice(0, 10);
  }
  return String(transaction.transactionTime).slice(0, 10);
}

function closeTo(actual: number, expected: number): boolean {
  return Math.abs(actual - expected) < 0.0000001;
}

function describeFixture(fixture: BankTransactionFixture): string {
  const data = fixture.transaction;
  return data.kind === 'single'
    ? `${data.transactionType} ${fixture.portfolio.name}/${data.cashAccount} on ${data.date}`
    : `transfer ${fixture.portfolio.name}/${data.debitCashAccount} to ${data.creditPortfolio}/` +
        `${data.creditCashAccount} on ${data.date}`;
}
