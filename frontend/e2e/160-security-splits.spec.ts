import {expect, Locator, Page, test} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

import {loginAsFixtureUser, parseCsvRow} from './helpers';
import {toShortDate} from './portfolio.helpers';

interface SecuritySplitFixture {
  loginNickname: string;
  watchlistName: string;
  securityName: string;
  isin: string;
  currency: string;
  splitDate: string;
  fromFactor: number;
  toFactor: number;
  e2e: 'd' | 'i' | 'e';
}

interface SecuritySplitScenario {
  loginNickname: string;
  watchlistName: string;
  securityName: string;
  isin: string;
  currency: string;
  splits: SecuritySplitFixture[];
}

interface ApiSecuritySplit {
  idSecuritycurrency: number;
  createType: number;
  splitDate: string;
  fromFactor: number;
  toFactor: number;
}

interface OpenSecurityDialog {
  dialog: Locator;
  idSecuritycurrency: number;
}

const FIXTURE_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/security_split.csv');

const RX = {
  deleteButton: /^(Delete|L.schen)$/i,
  editItem: /^(Edit Instrument|Bearbeiten Instrument)/i,
  saveButton: /^(Save|Speichern)$/,
};

function loadScenarios(): SecuritySplitScenario[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping security-split e2e.`);
    return [];
  }

  const rows = fs.readFileSync(FIXTURE_PATH, 'utf8').split(/\r?\n/)
    .filter(line => line.trim().length > 0)
    .map((line, index) => parseFixtureRow(line, index + 1))
    .filter(row => row.e2e === 'e');
  const grouped = new Map<string, SecuritySplitScenario>();

  for (const row of rows) {
    const key = [row.loginNickname, row.watchlistName, row.isin, row.currency].join('|');
    const scenario = grouped.get(key) ?? {
      loginNickname: row.loginNickname,
      watchlistName: row.watchlistName,
      securityName: row.securityName,
      isin: row.isin,
      currency: row.currency,
      splits: [],
    };
    if (scenario.securityName !== row.securityName) {
      throw new Error(`${FIXTURE_PATH}: inconsistent security names for ${row.isin}/${row.currency}`);
    }
    if (scenario.splits.some(split => split.splitDate === row.splitDate)) {
      throw new Error(`${FIXTURE_PATH}: duplicate split date ${row.splitDate} for ${row.isin}/${row.currency}`);
    }
    scenario.splits.push(row);
    grouped.set(key, scenario);
  }

  return [...grouped.values()].map(scenario => ({
    ...scenario,
    splits: scenario.splits.sort((a, b) => a.splitDate.localeCompare(b.splitDate)),
  }));
}

function parseFixtureRow(line: string, lineNumber: number): SecuritySplitFixture {
  const columns = parseCsvRow(line);
  if (columns.length !== 9) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: expected 9 columns, got ${columns.length}`);
  }
  if (columns.some(column => column.trim().length === 0)) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: columns must not be empty`);
  }
  assertIsoDate(columns[5], lineNumber);
  const fromFactor = Number(columns[6]);
  const toFactor = Number(columns[7]);
  if (![fromFactor, toFactor].every(factor => Number.isInteger(factor) && factor > 0)) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: split factors must be positive integers`);
  }
  if (!['d', 'i', 'e'].includes(columns[8])) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: invalid e2e routing tag '${columns[8]}'`);
  }
  return {
    loginNickname: columns[0],
    watchlistName: columns[1],
    securityName: columns[2],
    isin: columns[3],
    currency: columns[4],
    splitDate: columns[5],
    fromFactor,
    toFactor,
    e2e: columns[8] as SecuritySplitFixture['e2e'],
  };
}

function assertIsoDate(value: string, lineNumber: number): void {
  const parsed = new Date(`${value}T00:00:00.000Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(parsed.getTime())
      || parsed.toISOString().slice(0, 10) !== value) {
    throw new Error(`${FIXTURE_PATH}:${lineNumber}: invalid split date '${value}'`);
  }
}

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}

function securityRow(page: Page, scenario: SecuritySplitScenario): Locator {
  return page.locator('.data-container p-table tbody tr')
    .filter({has: page.locator('td').filter({hasText: exactText(scenario.securityName)})})
    .filter({has: page.locator('td').filter({hasText: exactText(scenario.currency)})})
    .first();
}

async function openWatchlist(page: Page, watchlistName: string): Promise<void> {
  const node = page.getByRole('treeitem', {name: watchlistName, exact: true}).first();
  await node.waitFor({state: 'visible', timeout: 15_000});
  await node.click();
  await page.locator('.data-container').first().waitFor({state: 'visible', timeout: 15_000});
  await page.waitForTimeout(1500);
}

async function openSecurityDialog(page: Page, scenario: SecuritySplitScenario): Promise<OpenSecurityDialog> {
  const row = securityRow(page, scenario);
  await expect(row, `${scenario.securityName}/${scenario.currency} in ${scenario.watchlistName}`)
    .toBeVisible({timeout: 15_000});

  // A watchlist refresh retains the old selection. Clicking that same row once then toggles it off in multi-select
  // mode, and the resulting context menu contains only create actions. Reacquire the row and toggle once more when
  // Edit Instrument is absent, so both the initial open and the post-save reopen select exactly one current row.
  let editItem: Locator | undefined;
  for (let attempt = 0; attempt < 2; attempt++) {
    await row.click();
    await page.waitForTimeout(300);
    await row.click({button: 'right'});
    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 5_000});
    const candidate = menu.getByText(RX.editItem).first();
    if (await candidate.isVisible().catch(() => false)) {
      editItem = candidate;
      break;
    }
    await page.keyboard.press('Escape');
  }
  expect(editItem, `Edit Instrument action for ${scenario.securityName}`).toBeDefined();

  const splitsResponsePromise = page.waitForResponse(response => response.request().method() === 'GET'
    && /\/api\/securitysplit\/\d+\/security$/.test(new URL(response.url()).pathname), {timeout: 20_000});
  await editItem!.click();

  const dialog = page.locator('security-edit .p-dialog').first();
  await dialog.waitFor({state: 'visible', timeout: 10_000});
  await expect(dialog.locator('select#assetClass option')).not.toHaveCount(0, {timeout: 15_000});
  await expect(dialog.locator('input#name')).toHaveValue(scenario.securityName);
  await expect(dialog.locator('input#isin')).toHaveValue(scenario.isin);
  await expect(dialog.locator('select#currency')).toHaveValue(scenario.currency);

  const splitsResponse = await splitsResponsePromise;
  expect(splitsResponse.ok(), `loading splits: ${await splitsResponse.text()}`).toBeTruthy();
  const idMatch = new URL(splitsResponse.url()).pathname.match(/\/securitysplit\/(\d+)\/security$/);
  expect(idMatch, 'security id in split request URL').not.toBeNull();
  return {dialog, idSecuritycurrency: Number(idMatch![1])};
}

async function selectSplitsTab(dialog: Locator): Promise<Locator> {
  await dialog.locator('p-tab[value="splits"]').click();
  const splitPanel = dialog.locator('p-tabpanel[value="splits"]');
  await expect(splitPanel.locator('securitysplit-edit-table')).toBeVisible({timeout: 10_000});
  return splitPanel;
}

async function deleteAllSplits(splitPanel: Locator): Promise<number> {
  const table = splitPanel.locator('securitysplit-edit-table');
  const rows = table.locator('tbody tr');
  const deleted = await rows.count();
  for (let remaining = await rows.count(); remaining > 0; remaining = await rows.count()) {
    await rows.first().click();
    const deleteButton = table.getByRole('button', {name: RX.deleteButton});
    await expect(deleteButton).toBeEnabled();
    await deleteButton.click();
    await expect(rows).toHaveCount(remaining - 1);
  }
  return deleted;
}

async function addSplits(splitPanel: Locator, splits: SecuritySplitFixture[], locale: string): Promise<void> {
  const form = splitPanel.locator('dynamic-form').first();
  const rows = splitPanel.locator('securitysplit-edit-table tbody tr');
  for (const split of splits) {
    const countBefore = await rows.count();
    await typeDate(form, 'splitDate', split.splitDate, locale);
    await typeNumber(form, 'fromFactor', split.fromFactor);
    await typeNumber(form, 'toFactor', split.toFactor);
    await form.locator('button[type="submit"]').click();
    await expect(rows).toHaveCount(countBefore + 1);
  }
}

async function typeDate(scope: Locator, fieldId: string, isoDate: string, locale: string): Promise<void> {
  const input = scope.locator(`#${fieldId} input, input#${fieldId}`).first();
  const value = toShortDate(isoDate, locale);
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(value, {delay: 20});
  await input.press('Tab');
  await expect(input).toHaveValue(value);
}

async function typeNumber(scope: Locator, fieldId: string, value: number): Promise<void> {
  const input = scope.locator(`#${fieldId} input, input#${fieldId}`).first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(String(value), {delay: 20});
  await input.press('Tab');
  expect(Number((await input.inputValue()).replace(/[^\d.-]/g, '')), fieldId).toBe(value);
}

async function saveSecurity(page: Page, dialog: Locator,
    expectSplitsWrite: boolean): Promise<ApiSecuritySplit[] | null> {
  await dialog.locator('p-tab[value="security"]').click();
  const securityResponsePromise = page.waitForResponse(response => response.request().method() === 'PUT'
    && /\/api\/security$/.test(new URL(response.url()).pathname), {timeout: 20_000});
  const splitsResponsePromise = expectSplitsWrite
    ? page.waitForResponse(response => response.request().method() === 'POST'
      && /\/api\/securitysplit$/.test(new URL(response.url()).pathname), {timeout: 20_000})
    : null;
  await dialog.getByRole('button', {name: RX.saveButton}).first().click();

  const securityResponse = await securityResponsePromise;
  const securityBody = await securityResponse.text();
  expect(securityResponse.ok(),
    `PUT /api/security returned ${securityResponse.status()}: ${securityBody}`).toBeTruthy();

  if (!splitsResponsePromise) {
    await dialog.waitFor({state: 'hidden', timeout: 15_000});
    return null;
  }
  const splitsResponse = await splitsResponsePromise;
  const splitsBody = await splitsResponse.text();
  expect(splitsResponse.ok(),
    `POST /api/securitysplit returned ${splitsResponse.status()}: ${splitsBody}`).toBeTruthy();
  await dialog.waitFor({state: 'hidden', timeout: 15_000});
  return JSON.parse(splitsBody) as ApiSecuritySplit[];
}

async function readSplits(page: Page, idSecuritycurrency: number): Promise<ApiSecuritySplit[]> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  const response = await page.request.get(`/api/securitysplit/${idSecuritycurrency}/security`, {
    headers: {'x-auth-token': token!},
  });
  const body = await response.text();
  expect(response.ok(), `GET security splits returned ${response.status()}: ${body}`).toBeTruthy();
  return JSON.parse(body) as ApiSecuritySplit[];
}

function expectedSplits(scenario: SecuritySplitScenario): Array<Pick<ApiSecuritySplit,
    'splitDate' | 'fromFactor' | 'toFactor'>> {
  return scenario.splits.map(split => ({
    splitDate: split.splitDate,
    fromFactor: split.fromFactor,
    toFactor: split.toFactor,
  }));
}

function comparableSplits(splits: ApiSecuritySplit[]): Array<Pick<ApiSecuritySplit,
    'splitDate' | 'fromFactor' | 'toFactor'>> {
  return splits.map(split => ({
    splitDate: split.splitDate,
    fromFactor: split.fromFactor,
    toFactor: split.toFactor,
  })).sort((a, b) => a.splitDate.localeCompare(b.splitDate));
}

for (const scenario of loadScenarios()) {
  test.describe.serial(`security splits for ${scenario.securityName}`, () => {
    test.use({viewport: {width: 1600, height: 1200}});

    test('deletes and recreates every split through security editing', async ({page}) => {
      const credentials = await loginAsFixtureUser(page, scenario.loginNickname);
      await openWatchlist(page, scenario.watchlistName);

      const firstOpen = await openSecurityDialog(page, scenario);
      const firstSplitPanel = await selectSplitsTab(firstOpen.dialog);
      const deletedSplits = await deleteAllSplits(firstSplitPanel);
      const clearedSplits = await saveSecurity(page, firstOpen.dialog, deletedSplits > 0);
      if (deletedSplits > 0) {
        expect(clearedSplits).toEqual([]);
      }
      expect(await readSplits(page, firstOpen.idSecuritycurrency)).toEqual([]);

      const secondOpen = await openSecurityDialog(page, scenario);
      expect(secondOpen.idSecuritycurrency).toBe(firstOpen.idSecuritycurrency);
      const secondSplitPanel = await selectSplitsTab(secondOpen.dialog);
      await addSplits(secondSplitPanel, scenario.splits, credentials.locale);
      const savedSplits = await saveSecurity(page, secondOpen.dialog, true);

      expect(savedSplits).not.toBeNull();
      expect(comparableSplits(savedSplits!)).toEqual(expectedSplits(scenario));
      expect(savedSplits!).toHaveLength(scenario.splits.length);
      expect(savedSplits!.every(split => split.createType === 5)).toBe(true);

      const persistedSplits = await readSplits(page, firstOpen.idSecuritycurrency);
      expect(comparableSplits(persistedSplits)).toEqual(expectedSplits(scenario));
      expect(persistedSplits.every(split => split.createType === 5)).toBe(true);
    });
  });
}
