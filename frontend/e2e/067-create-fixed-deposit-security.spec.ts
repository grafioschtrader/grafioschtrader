import {expect, Locator, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {fillText, selectByValue} from './generic-connector.helpers';
import {loginAsFixtureUser} from './helpers';
import {expectToast} from './manage-client.helpers';
import {toShortDate} from './portfolio.helpers';

type HistoryquotePeriodCreateType = 'SYSTEM_CREATED' | 'USER_CREATED';

interface HistoryquotePeriodFixture {
  fromDate: string;
  toDate: string;
  price: number;
  createType: HistoryquotePeriodCreateType;
}

interface FixedDepositSecurity {
  creationType: 'STANDARD_WITH_PERIODS';
  sourceIdSecuritycurrency: number;
  name: string;
  isin: string | null;
  tickerSymbol: string | null;
  currency: string;
  activeFromDate: string;
  activeToDate: string;
  distributionFrequency: string;
  denomination: number;
  leverageFactor: number;
  stockexchangeName: string;
  assetclassSubCategoryDE: string;
  historyquotePeriods: HistoryquotePeriodFixture[];
  e2e: string;
}

interface SecurityCreationGroup {
  loginNickname: string;
  watchlistName: string;
  securities: Array<FixedDepositSecurity | {creationType: string; e2e: string}>;
}

interface SecurityCreationFixture {
  groups: SecurityCreationGroup[];
}

interface FixedDepositScenario {
  loginNickname: string;
  watchlistName: string;
  security: FixedDepositSecurity;
}

interface SavedSecurity {
  idSecuritycurrency: number;
  name: string;
  currency: string;
  activeFromDate: string;
  activeToDate: string;
  distributionFrequency: string;
  denomination: number;
  leverageFactor: number;
  stockexchange: {name: string};
  assetClass: {subCategoryNLS: string};
}

interface SavedHistoryquotePeriod {
  fromDate: string;
  toDate: string;
  price: number;
}

interface DateClose {
  date: string;
  close: number;
}

const FIXTURE_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/security-creations.json');

const RX = {
  addExistingItem: /(Bestehendes\s+Instrument\s+hinzuf.gen|Add\s+existing\s+instrument)/i,
  addButton: /^(Hinzuf.gen|Add)$/,
  closeButton: /^(Beenden|Close)$/,
  createItem: /^(Create\s*and\s*add\s*security|Hinzuf.*neues\s*Wertpapier)\b/i,
  editItem: /^(Edit Instrument|Bearbeiten Instrument)/i,
  savedToast: /(was saved|wurde gespeichert)/i,
};

function loadScenarios(): FixedDepositScenario[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping fixed-deposit security e2e.`);
    return [];
  }
  const fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8')) as SecurityCreationFixture;
  return fixture.groups.flatMap(group => group.securities
    .filter((security): security is FixedDepositSecurity =>
      security.creationType === 'STANDARD_WITH_PERIODS' && security.e2e === 'e')
    .map(security => ({
      loginNickname: group.loginNickname,
      watchlistName: group.watchlistName,
      security
    })));
}

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}

function securityRow(page: Page, security: FixedDepositSecurity): Locator {
  return page.locator('.data-container p-table tbody tr')
    .filter({has: page.locator('td').filter({hasText: exactText(security.name)})})
    .filter({has: page.locator('td').filter({hasText: exactText(security.currency)})})
    .first();
}

async function openWatchlist(page: Page, watchlistName: string): Promise<void> {
  const node = page.getByRole('treeitem', {name: watchlistName, exact: true}).first();
  await node.waitFor({state: 'visible', timeout: 15_000});
  await node.click();
  await page.locator('.data-container').first().waitFor({state: 'visible', timeout: 15_000});
  await page.waitForTimeout(1500);
}

async function openWatchlistMenu(page: Page): Promise<Locator> {
  const container = page.locator('.data-container').first();
  await container.click();
  await page.waitForTimeout(300);
  await container.click({button: 'right'});
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  return menu;
}

/** Adds an exact existing security to the open watchlist and reports whether one was found. */
async function addExistingSecurity(page: Page, security: FixedDepositSecurity): Promise<boolean> {
  const menu = await openWatchlistMenu(page);
  await menu.getByText(RX.addExistingItem).first().click();

  const dialog = page.locator('watchlist-add-instrument');
  await dialog.locator('.p-dialog').waitFor({state: 'visible', timeout: 10_000});
  await expect(dialog.locator('select#idStockexchange option')).not.toHaveCount(0, {timeout: 15_000});

  const name = dialog.locator('input#name');
  await expect(name).toBeVisible();
  await name.fill(security.name);
  await dialog.locator('button[type="submit"]').click();

  const exactRow = dialog.locator('add-instrument-table tbody tr')
    .filter({has: page.locator('td').filter({hasText: exactText(security.name)})})
    .filter({has: page.locator('td').filter({hasText: exactText(security.currency)})});
  const found = await exactRow.count() === 1;
  if (found) {
    await exactRow.locator('p-tablecheckbox').click();
    const add = dialog.getByRole('button', {name: RX.addButton});
    await expect(add).toBeEnabled({timeout: 5_000});
    await add.click();
    await expect(add).toBeDisabled({timeout: 15_000});
  }

  await dialog.getByRole('button', {name: RX.closeButton}).click();
  await dialog.locator('.p-dialog').waitFor({state: 'hidden', timeout: 10_000});
  if (found) {
    await expect(securityRow(page, security)).toBeVisible({timeout: 15_000});
  }
  return found;
}

/** Returns true when an existing exact security can be edited, false when it must be created. */
async function ensureSecurityInWatchlist(page: Page, security: FixedDepositSecurity): Promise<boolean> {
  if (await securityRow(page, security).isVisible().catch(() => false)) {
    return true;
  }
  return addExistingSecurity(page, security);
}

async function openSecurityDialog(page: Page, security: FixedDepositSecurity, existing: boolean): Promise<Locator> {
  if (existing) {
    const row = securityRow(page, security);
    await row.click();
    await page.waitForTimeout(300);
    await row.click({button: 'right'});
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 5_000});
    await menu.getByText(RX.editItem).first().click();
  } else {
    const menu = await openWatchlistMenu(page);
    await menu.getByText(RX.createItem).first().click();
  }

  const dialog = page.locator('security-edit .p-dialog').first();
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  await expect(dialog.locator('select#assetClass option')).not.toHaveCount(0, {timeout: 15_000});
  return dialog;
}

async function selectByOptionText(scope: Locator, fieldId: string, text: string): Promise<void> {
  const select = scope.locator(`select#${fieldId}`).first();
  await expect(select.locator('option')).not.toHaveCount(0, {timeout: 15_000});
  const option = select.locator('option').filter({hasText: text}).first();
  await expect(option, `no option containing "${text}" in select#${fieldId}`)
    .toHaveCount(1, {timeout: 10_000});
  await select.selectOption(await option.getAttribute('value'));
  await select.dispatchEvent('change');
}

async function typeDate(scope: Locator, fieldId: string, isoDate: string, locale: string): Promise<void> {
  const value = toShortDate(isoDate, locale);
  const input = scope.locator(`#${fieldId} input, input#${fieldId}`).first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(value, {delay: 20});
  await input.blur();
  await expect(input, `${fieldId} did not keep the typed date`).toHaveValue(value);
}

async function typeNumber(scope: Locator, fieldId: string, value: number): Promise<void> {
  const input = scope.locator(`#${fieldId} input, input#${fieldId}`).first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(String(value), {delay: 20});
  await input.press('Tab');
}

async function reconcileBaseData(dialog: Locator, security: FixedDepositSecurity, locale: string): Promise<void> {
  await fillText(dialog, 'input#name', security.name);
  await selectByOptionText(dialog, 'assetClass', security.assetclassSubCategoryDE);
  await selectByOptionText(dialog, 'stockexchange', security.stockexchangeName);

  const currency = dialog.locator('select#currency');
  if (await currency.isEnabled()) {
    await selectByValue(dialog, 'currency', security.currency);
  } else {
    await expect(currency).toHaveValue(security.currency);
  }
  await selectByValue(dialog, 'distributionFrequency', security.distributionFrequency);
  await typeNumber(dialog, 'denomination', security.denomination);

  const leverage = dialog.locator('#leverageFactor input, input#leverageFactor').first();
  if (await leverage.isVisible()) {
    await typeNumber(dialog, 'leverageFactor', security.leverageFactor);
  }
  await typeDate(dialog, 'activeFromDate', security.activeFromDate, locale);
  await typeDate(dialog, 'activeToDate', security.activeToDate, locale);
}

async function clearUserPeriods(dialog: Locator): Promise<void> {
  const table = dialog.locator('security-historyquote-period-edit-table');
  const rows = table.locator('tbody tr');
  for (let remaining = await rows.count(); remaining > 0; remaining = await rows.count()) {
    await rows.first().click();
    const deleteButton = table.getByRole('button', {name: /^(Delete|L.schen)$/i});
    await expect(deleteButton).toBeEnabled();
    await deleteButton.click();
    await expect(rows).toHaveCount(remaining - 1);
  }
}

async function addUserPeriods(dialog: Locator, security: FixedDepositSecurity, locale: string): Promise<void> {
  const periodForm = dialog.locator('p-tabpanel[value="periods"] dynamic-form').first();
  await expect(periodForm.locator('#fromDate input')).toBeVisible({timeout: 10_000});
  const rows = dialog.locator('security-historyquote-period-edit-table tbody tr');
  const userPeriods = security.historyquotePeriods.filter(period => period.createType === 'USER_CREATED');

  for (const period of userPeriods) {
    const countBefore = await rows.count();
    await typeDate(periodForm, 'fromDate', period.fromDate, locale);
    await typeNumber(periodForm, 'price', period.price);
    await periodForm.locator('button[type="submit"]').click();
    await expect(rows).toHaveCount(countBefore + 1);
  }
}

function expectedDateClose(periods: HistoryquotePeriodFixture[]): DateClose[] {
  return periods.flatMap(period => [
    {date: period.fromDate, close: period.price},
    {date: period.toDate, close: period.price}
  ]).sort((a, b) => a.date.localeCompare(b.date));
}

async function assertPersistedPeriods(page: Page, security: FixedDepositSecurity,
    idSecuritycurrency: number): Promise<void> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const headers = {'x-auth-token': token!};

  const userResponse = await page.request.get(
    `http://localhost:4200/api/historyquoteperiod/${idSecuritycurrency}/security`, {headers});
  const userBody = await userResponse.text();
  expect(userResponse.ok(), `GET user periods returned ${userResponse.status()}: ${userBody}`).toBe(true);
  const actualUserPeriods = (JSON.parse(userBody) as SavedHistoryquotePeriod[])
    .map(period => ({fromDate: period.fromDate, toDate: period.toDate, price: period.price}));
  const expectedUserPeriods = security.historyquotePeriods
    .filter(period => period.createType === 'USER_CREATED')
    .map(period => ({fromDate: period.fromDate, toDate: period.toDate, price: period.price}));
  expect(actualUserPeriods).toEqual(expectedUserPeriods);

  const dateCloseResponse = await page.request.get(
    `http://localhost:4200/api/historyquote/securitycurrency/${idSecuritycurrency}/dateclose`, {headers});
  const dateCloseBody = await dateCloseResponse.text();
  expect(dateCloseResponse.ok(),
    `GET period date/close values returned ${dateCloseResponse.status()}: ${dateCloseBody}`).toBe(true);
  const actualDateClose = (JSON.parse(dateCloseBody) as DateClose[])
    .map(value => ({date: value.date, close: value.close}))
    .sort((a, b) => a.date.localeCompare(b.date));
  expect(actualDateClose).toEqual(expectedDateClose(security.historyquotePeriods));
}

test.describe.serial('fixed-deposit security with manual price periods', () => {
  test.use({viewport: {width: 1600, height: 1200}});

  for (const scenario of loadScenarios()) {
    test(`creates or reconciles ${scenario.security.name} in ${scenario.watchlistName}`, async ({page}) => {
      const credentials = await loginAsFixtureUser(page, scenario.loginNickname);
      await openWatchlist(page, scenario.watchlistName);
      const existing = await ensureSecurityInWatchlist(page, scenario.security);
      const dialog = await openSecurityDialog(page, scenario.security, existing);

      await reconcileBaseData(dialog, scenario.security, credentials.locale);
      const periodsTab = dialog.locator('p-tab[value="periods"]');
      await expect(periodsTab).toBeVisible({timeout: 10_000});
      await periodsTab.click();
      await clearUserPeriods(dialog);
      await addUserPeriods(dialog, scenario.security, credentials.locale);
      await dialog.locator('p-tab[value="security"]').click();

      const securityResponsePromise = page.waitForResponse(response =>
        /\/api\/security$/.test(new URL(response.url()).pathname)
        && ['POST', 'PUT'].includes(response.request().method()), {timeout: 20_000});
      const periodsResponsePromise = page.waitForResponse(response =>
        /\/api\/historyquoteperiod$/.test(new URL(response.url()).pathname)
        && response.request().method() === 'POST', {timeout: 20_000});
      await dialog.getByRole('button', {name: /^(Save|Speichern)$/}).first().click();

      const securityResponse = await securityResponsePromise;
      const securityBody = await securityResponse.text();
      expect(securityResponse.ok(),
        `${securityResponse.request().method()} /api/security returned ${securityResponse.status()}: ${securityBody}`)
        .toBe(true);
      const saved = JSON.parse(securityBody) as SavedSecurity;

      const periodsResponse = await periodsResponsePromise;
      const periodsBody = await periodsResponse.text();
      expect(periodsResponse.ok(),
        `POST /api/historyquoteperiod returned ${periodsResponse.status()}: ${periodsBody}`).toBe(true);

      expect(saved.name).toBe(scenario.security.name);
      expect(saved.currency).toBe(scenario.security.currency);
      expect(saved.activeFromDate).toBe(scenario.security.activeFromDate);
      expect(saved.activeToDate).toBe(scenario.security.activeToDate);
      expect(saved.distributionFrequency).toBe(scenario.security.distributionFrequency);
      expect(saved.denomination).toBe(scenario.security.denomination);
      expect(saved.leverageFactor).toBeCloseTo(scenario.security.leverageFactor, 1);
      expect(saved.stockexchange.name).toBe(scenario.security.stockexchangeName);

      await assertPersistedPeriods(page, scenario.security, saved.idSecuritycurrency);
      await expectToast(page, RX.savedToast);
      await expect(securityRow(page, scenario.security)).toBeVisible({timeout: 15_000});
    });
  }
});
