import { expect, Locator, Page, test } from '@playwright/test';
import {
  CashStandingOrderData,
  loadPortfolios,
  PortfolioFixture,
  SecurityStandingOrderData,
  StandingOrderData,
  toShortDate
} from './portfolio.helpers';

export interface StandingOrderFixture {
  portfolio: PortfolioFixture;
  standingOrder: StandingOrderData;
}

interface ApiStandingOrder {
  dtype: 'C' | 'S';
  idStandingOrder: number;
  transactionType: string;
  cashaccount?: { name: string } | null;
  note?: string | null;
  repeatUnit: string;
  repeatInterval: number;
  dayOfExecution?: number | null;
  monthOfExecution?: number | null;
  periodDayPosition: string;
  weekendAdjust: string;
  validFrom: string;
  validTo: string;
  transactionCost?: number | null;
  hasTransactions?: boolean;
  cashaccountAmount?: number | null;
  amountCurrency?: string | null;
  cashaccountAmountFormula?: string | null;
  security?: { name: string; isin?: string | null } | null;
  idSecurityaccount?: number | null;
  units?: number | null;
  investAmount?: number | null;
  amountIncludesCosts?: boolean;
  fractionalUnits?: boolean;
  taxCost?: number | null;
  taxCostFormula?: string | null;
  transactionCostFormula?: string | null;
}

const ROUTE = {
  cash: '/mainview/tenantTabMenu/standingordertabmenu/tenantstandingordercashaccount'
};

const RX = {
  create: /(Create\s+standing\s+order|Erstellen\s+Dauerauftrag)/i,
  assignSelected: /(Assign\s*selected|Selektierte\s*zuweisen)/i,
  securityTab: /(Standing\s+order\s+security|Dauerauftrag\s+Wertpapier)/i
};

/** Loads every standing order nested below an e2e portfolio. */
export function loadStandingOrders(): StandingOrderFixture[] {
  return loadPortfolios().flatMap((portfolio) =>
    (portfolio.standingOrders ?? []).map((standingOrder) => ({ portfolio, standingOrder }))
  );
}

/**
 * Removes only orders matching the fixture identities. Freshly created e2e orders have no transactions and are
 * therefore deletable through the public endpoint. Historical source orders with generated transactions must be
 * detached once in the local database before this test is run for the first time.
 */
export async function deleteExistingStandingOrders(page: Page, fixtures: StandingOrderFixture[]): Promise<void> {
  await test.step('delete matching standing orders from an earlier run', async () => {
    const headers = await authHeaders(page);
    const existing = await getTenantStandingOrders(page, headers);
    const matching = existing.filter((actual) => fixtures.some((fixture) => sameIdentity(actual, fixture)));

    for (const order of matching) {
      expect(
        order.hasTransactions,
        `standing order ${order.idStandingOrder} has generated transactions; detach it in grafioschtrader_t first`
      ).not.toBe(true);
      const response = await page.request.delete(`/api/standingorder/${order.idStandingOrder}`, { headers });
      expect(response.ok(), `deleting standing order ${order.idStandingOrder}: ${await response.text()}`).toBeTruthy();
    }
  });
}

/** Creates one cash-account standing order through its dynamic form. */
export async function createCashStandingOrder(page: Page, fixture: StandingOrderFixture): Promise<void> {
  const data = fixture.standingOrder as CashStandingOrderData;
  await test.step(`create cash standing order for ${data.cashAccount}`, async () => {
    await openCreateDialog(page, 'cash');
    const dialog = page.locator('standing-order-cashaccount-edit .p-dialog:visible');
    await expect(dialog.locator('select#idCashaccount option')).not.toHaveCount(0, { timeout: 15_000 });

    await selectValue(dialog, 'transactionType', data.transactionType);
    await selectOptionText(dialog, 'idCashaccount', accountLabel(fixture.portfolio, data.cashAccount));
    await fillNumber(dialog, 'cashaccountAmount', data.cashaccountAmount);
    if (data.amountCurrency) {
      await selectValue(dialog, 'amountCurrency', data.amountCurrency);
    }
    await fillOptionalText(dialog, 'cashaccountAmountFormula', data.cashaccountAmountFormula);
    await fillOptionalNumber(dialog, 'transactionCost', data.transactionCost);
    await fillSchedule(dialog, data, fixture.portfolio.loginNickname);
    await submitStandingOrder(page, dialog);

    await expect(page.locator('standing-order-cashaccount-table tbody tr', { hasText: data.cashAccount })).toHaveCount(
      1,
      { timeout: 10_000 }
    );
  });
}

/** Creates one security standing order through its dynamic form and the instrument-search dialog. */
export async function createSecurityStandingOrder(page: Page, fixture: StandingOrderFixture): Promise<void> {
  const data = fixture.standingOrder as SecurityStandingOrderData;
  await test.step(`create security standing order for ${data.securityName}`, async () => {
    await openCreateDialog(page, 'security');
    const dialog = page.locator('standing-order-security-edit .p-dialog:visible');
    await expect(dialog.locator('select#idSecurityaccount option')).not.toHaveCount(0, { timeout: 15_000 });

    await selectValue(dialog, 'transactionType', data.transactionType);
    await selectOptionText(dialog, 'idSecurityaccount', securityAccountLabel(fixture.portfolio, data.securityAccount));
    await selectOptionText(dialog, 'idCashaccount', accountLabel(fixture.portfolio, data.cashAccount));
    await selectSecurity(page, dialog, data);

    await selectValue(dialog, 'investMode', data.investAmount == null ? 'INVEST_MODE_UNITS' : 'INVEST_MODE_AMOUNT');
    await fillOptionalNumber(dialog, 'units', data.units);
    await fillOptionalNumber(dialog, 'investAmount', data.investAmount);
    await setCheckbox(dialog, 'amountIncludesCosts', data.amountIncludesCosts);
    await setCheckbox(dialog, 'fractionalUnits', data.fractionalUnits);
    await fillOptionalNumber(dialog, 'taxCost', data.taxCost);
    await fillOptionalText(dialog, 'taxCostFormula', data.taxCostFormula);
    await fillOptionalNumber(dialog, 'transactionCost', data.transactionCost);
    await fillOptionalText(dialog, 'transactionCostFormula', data.transactionCostFormula);
    await fillSchedule(dialog, data, fixture.portfolio.loginNickname);
    await submitStandingOrder(page, dialog);

    await expect(page.locator('standing-order-security-table tbody tr', { hasText: data.securityName })).toHaveCount(
      1,
      { timeout: 10_000 }
    );
  });
}

/** Verifies that every fixture order exists exactly once and that all user-controlled fields were persisted. */
export async function expectStandingOrders(page: Page, fixtures: StandingOrderFixture[]): Promise<void> {
  await test.step('verify persisted standing orders', async () => {
    const actual = await getTenantStandingOrders(page, await authHeaders(page));
    for (const fixture of fixtures) {
      const matches = actual.filter((order) => sameIdentity(order, fixture));
      expect(matches, describeFixture(fixture)).toHaveLength(1);
      expectCommonFields(matches[0], fixture.standingOrder);
      if (fixture.standingOrder.kind === 'cash') {
        expectCashFields(matches[0], fixture.standingOrder);
      } else {
        expectSecurityFields(matches[0], fixture.standingOrder);
      }
    }
  });
}

async function openCreateDialog(page: Page, kind: 'cash' | 'security'): Promise<void> {
  // SharedTabMenu deliberately activates its default cash child on first render. Open that stable route and use the
  // visible sub-tab for the security view instead of racing the component's default-route navigation.
  await page.goto(ROUTE.cash);
  if (kind === 'security') {
    await page.getByRole('tab', { name: RX.securityTab }).click();
  }
  const host = page.locator(kind === 'cash' ? 'standing-order-cashaccount-table' : 'standing-order-security-table');
  const container = host.locator(':scope > .data-container');
  await container.waitFor({ state: 'visible', timeout: 15_000 });
  await container.click();
  await page.waitForTimeout(250);
  await container.click({ button: 'right' });

  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({ state: 'visible', timeout: 5_000 });
  await menu.getByText(RX.create).first().click();
  await page
    .locator(
      kind === 'cash'
        ? 'standing-order-cashaccount-edit .p-dialog:visible'
        : 'standing-order-security-edit .p-dialog:visible'
    )
    .waitFor({ state: 'visible', timeout: 10_000 });
}

async function selectSecurity(page: Page, dialog: Locator, data: SecurityStandingOrderData): Promise<void> {
  const openButton = dialog.locator('p-inputgroup:has(input#securityName) button').first();
  const search = page
    .locator('.p-dialog')
    .filter({ has: page.locator('securitycurrency-search-and-set') })
    .first();

  for (let attempt = 0; attempt < 3 && (await search.count()) === 0; attempt++) {
    await openButton.click();
    await search.waitFor({ state: 'visible', timeout: 3_000 }).catch(() => undefined);
  }
  await search.waitFor({ state: 'visible', timeout: 10_000 });
  await expect(search.locator('select#idStockexchange option')).not.toHaveCount(0, { timeout: 15_000 });

  const isin = search.locator('input#isin');
  await isin.fill(data.securityIsin);
  await isin.dispatchEvent('input');
  await search.locator('button[type="submit"]').click();

  const row = search
    .locator('securitycurrency-search-and-set-table tbody tr')
    .filter({ hasText: data.securityIsin })
    .filter({ hasText: data.securityName })
    .first();
  await row.waitFor({ state: 'visible', timeout: 15_000 });
  await row.click();
  const assign = search.getByRole('button', { name: RX.assignSelected }).first();
  await expect(assign).toBeEnabled({ timeout: 5_000 });
  await assign.click();
  await search.waitFor({ state: 'hidden', timeout: 10_000 });
  await expect(dialog.locator('input#securityName')).toHaveValue(data.securityName);
}

async function fillSchedule(dialog: Locator, data: StandingOrderData, loginNickname: string): Promise<void> {
  await selectValue(dialog, 'repeatUnit', data.repeatUnit);
  await fillNumber(dialog, 'repeatInterval', data.repeatInterval);
  if (data.repeatUnit !== 'DAYS') {
    await selectValue(dialog, 'periodDayPosition', data.periodDayPosition);
    await fillOptionalNumber(dialog, 'dayOfExecution', data.dayOfExecution);
    if (data.repeatUnit === 'YEARS' && data.monthOfExecution != null) {
      await selectValue(dialog, 'monthOfExecution', String(data.monthOfExecution));
    }
  }
  await selectValue(dialog, 'weekendAdjust', data.weekendAdjust);
  await fillDate(dialog, 'validFrom', data.validFrom, loginNickname);
  await fillDate(dialog, 'validTo', data.validTo, loginNickname);
  await fillOptionalText(dialog, 'note', data.note);
}

async function selectValue(scope: Locator, field: string, value: string): Promise<void> {
  const select = scope.locator(`select#${field}`);
  await select.waitFor({ state: 'visible', timeout: 10_000 });
  await expect(select.locator(`option[value="${value}"]`)).toHaveCount(1, { timeout: 10_000 });
  await select.selectOption(value);
  await select.dispatchEvent('change');
}

async function selectOptionText(scope: Locator, field: string, text: string): Promise<void> {
  const select = scope.locator(`select#${field}`);
  await select.waitFor({ state: 'visible', timeout: 10_000 });
  const option = select.locator('option').filter({ hasText: text }).first();
  await expect(option, `no option containing '${text}' in select#${field}`).toHaveCount(1, { timeout: 10_000 });
  await select.selectOption(await option.getAttribute('value'));
  await select.dispatchEvent('change');
}

async function fillNumber(scope: Locator, field: string, value: number): Promise<void> {
  const input = scope.locator(`#${field} input, input#${field}`).first();
  await input.waitFor({ state: 'visible', timeout: 10_000 });
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(String(value), { delay: 20 });
  await input.press('Tab');
}

async function fillOptionalNumber(scope: Locator, field: string, value?: number): Promise<void> {
  if (value != null) {
    await fillNumber(scope, field, value);
  }
}

async function fillOptionalText(scope: Locator, field: string, value?: string): Promise<void> {
  if (value == null) {
    return;
  }
  const input = scope.locator(`#${field}`);
  await input.fill(value);
  await input.dispatchEvent('input');
  await input.blur();
}

async function fillDate(scope: Locator, field: string, isoDate: string, loginNickname: string): Promise<void> {
  const locale = loginNickname === 'alledit' ? 'de-CH' : 'en-US';
  const value = toShortDate(isoDate, locale);
  const input = scope.locator(`#${field} input, input#${field}`).first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(value, { delay: 20 });
  await input.blur();
  await expect(input, `${field} did not keep the typed date`).toHaveValue(value);
}

async function setCheckbox(scope: Locator, field: string, checked: boolean): Promise<void> {
  const checkbox = scope.locator(`input[type="checkbox"]#${field}`);
  await checkbox.waitFor({ state: 'visible', timeout: 10_000 });
  if ((await checkbox.isChecked()) !== checked) {
    await checkbox.click();
  }
  await expect(checkbox).toBeChecked({ checked });
}

async function submitStandingOrder(page: Page, dialog: Locator): Promise<void> {
  const submit = dialog.locator('button[type="submit"]');
  await expect(submit).toBeEnabled({ timeout: 10_000 });
  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/standingorder'
  );
  await submit.click();
  const response = await responsePromise;
  expect(response.ok(), `POST /api/standingorder: ${await response.text()}`).toBeTruthy();
  await dialog.waitFor({ state: 'hidden', timeout: 10_000 });
}

async function authHeaders(page: Page): Promise<Record<string, string>> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token, Accept: 'application/json' };
}

async function getTenantStandingOrders(page: Page, headers: Record<string, string>): Promise<ApiStandingOrder[]> {
  const response = await page.request.get('/api/standingorder/tenant', { headers });
  expect(response.ok(), `loading tenant standing orders: ${await response.text()}`).toBeTruthy();
  return (await response.json()) as ApiStandingOrder[];
}

function sameIdentity(actual: ApiStandingOrder, fixture: StandingOrderFixture): boolean {
  const expected = fixture.standingOrder;
  if (expected.kind === 'cash') {
    return (
      actual.dtype === 'C' &&
      actual.transactionType === expected.transactionType &&
      actual.cashaccount?.name === expected.cashAccount
    );
  }
  return (
    actual.dtype === 'S' &&
    actual.transactionType === expected.transactionType &&
    actual.cashaccount?.name === expected.cashAccount &&
    actual.security?.isin === expected.securityIsin
  );
}

function expectCommonFields(actual: ApiStandingOrder, expected: StandingOrderData): void {
  expect(actual.transactionType).toBe(expected.transactionType);
  expect(actual.cashaccount?.name).toBe(expected.cashAccount);
  expect(actual.repeatUnit).toBe(expected.repeatUnit);
  expect(actual.repeatInterval).toBe(expected.repeatInterval);
  expect(actual.periodDayPosition).toBe(expected.periodDayPosition);
  expect(actual.dayOfExecution ?? null).toBe(expected.dayOfExecution ?? null);
  expect(actual.monthOfExecution ?? null).toBe(expected.monthOfExecution ?? null);
  expect(actual.weekendAdjust).toBe(expected.weekendAdjust);
  expect(actual.validFrom).toBe(expected.validFrom);
  expect(actual.validTo).toBe(expected.validTo);
  expect(actual.note ?? null).toBe(expected.note ?? null);
  expect(actual.transactionCost ?? null).toBe(expected.transactionCost ?? null);
}

function expectCashFields(actual: ApiStandingOrder, expected: CashStandingOrderData): void {
  expect(actual.cashaccountAmount).toBeCloseTo(expected.cashaccountAmount, 8);
  expect(actual.amountCurrency ?? null).toBe(expected.amountCurrency ?? null);
  expect(actual.cashaccountAmountFormula ?? null).toBe(expected.cashaccountAmountFormula ?? null);
}

function expectSecurityFields(actual: ApiStandingOrder, expected: SecurityStandingOrderData): void {
  expect(actual.security?.name).toBe(expected.securityName);
  expect(actual.security?.isin).toBe(expected.securityIsin);
  expect(actual.units ?? null).toBe(expected.units ?? null);
  expect(actual.investAmount ?? null).toBe(expected.investAmount ?? null);
  expect(actual.amountIncludesCosts).toBe(expected.amountIncludesCosts);
  expect(actual.fractionalUnits).toBe(expected.fractionalUnits);
  expect(actual.taxCost ?? null).toBe(expected.taxCost ?? null);
  expect(actual.taxCostFormula ?? null).toBe(expected.taxCostFormula ?? null);
  expect(actual.transactionCostFormula ?? null).toBe(expected.transactionCostFormula ?? null);
}

function accountLabel(portfolio: PortfolioFixture, accountName: string): string {
  const account = portfolio.cashAccounts.find((item) => item.name === accountName);
  expect(account, `cash account '${accountName}' in portfolio '${portfolio.name}'`).toBeDefined();
  return `${account.name} / ${account.currency} / ${portfolio.name}`;
}

function securityAccountLabel(portfolio: PortfolioFixture, accountName: string): string {
  const account = portfolio.securityAccounts.find((item) => item.name === accountName);
  expect(account, `security account '${accountName}' in portfolio '${portfolio.name}'`).toBeDefined();
  return `${account.name} / ${portfolio.name}`;
}

function describeFixture(fixture: StandingOrderFixture): string {
  const data = fixture.standingOrder;
  return data.kind === 'cash'
    ? `${data.transactionType} standing order on ${fixture.portfolio.name}/${data.cashAccount}`
    : `${data.transactionType} standing order for ${data.securityName}`;
}
