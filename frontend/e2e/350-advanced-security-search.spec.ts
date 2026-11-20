import { expect, Locator, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {
  closeAddInstrumentDialog,
  ensureWatchlist,
  openAddInstrumentDialog,
  watchlistTable,
  WatchlistFixture
} from './watchlist.helpers';
import { loginAsFixtureUser } from './helpers';

/**
 * Advanced search of the add-existing-instrument dialog on user-defined fields. It reads the values that 330-* and
 * 345-* stored for 'admin' (the Yes/No field 'CFD möglich'; the link field 'Short Volume Link' must not be offered) and
 * searches them through the dialog of a watchlist that holds none of those securities. Nothing is created or added: the
 * dialog is closed without pressing Add, so the spec can be repeated at any time.
 */

type UdfValue = string | number | boolean | null;

interface UdfDataRow {
  loginNickname: string;
  entity: string;
  securityName: string;
  values: Record<string, UdfValue>;
  e2e: string;
}

const FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/udf_data.json'
);

const NICKNAME = 'admin';
const FLAG_FIELD = 'CFD möglich';
const LINK_FIELD = 'Short Volume Link';

/** A watchlist of the integration suite that holds only ProShares funds, so every search result is a candidate. */
const WATCHLIST: WatchlistFixture = {
  loginNickname: NICKNAME,
  name: 'Hauptliste',
  main: false,
  delete: false,
  securities: [],
  currencyPairs: [],
  e2e: 'i'
};

const RX = {
  addCriterion: /^(Advanced search \+|Erweiterte Suche \+)$/,
  removeCriterion: /^(Remove criterion|Kriterium entfernen)$/
};

function loadAdminRows(): UdfDataRow[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping the advanced search e2e spec.`);
    return [];
  }
  const file = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf8')) as { udfData: UdfDataRow[] };
  return file.udfData.filter((row) => row.e2e === 'e' && row.entity === 'Security' && row.loginNickname === NICKNAME);
}

const withFlag = loadAdminRows()
  .filter((row) => row.values[FLAG_FIELD] === true)
  .map((row) => row.securityName);

async function openDialog(page: Page): Promise<Locator> {
  await loginAsFixtureUser(page, NICKNAME);
  await ensureWatchlist(page, WATCHLIST);
  const node = page.getByRole('treeitem', { name: WATCHLIST.name, exact: true }).first();
  await node.click();
  const container = watchlistTable(page);
  await container.waitFor({ state: 'visible', timeout: 15_000 });
  return openAddInstrumentDialog(page, container);
}

/** Opens a criteria slot; the select of that slot becomes visible. */
async function addCriterion(dialog: Locator, slot: number): Promise<void> {
  await dialog.getByRole('button', { name: RX.addCriterion }).click();
  await expect(dialog.locator(`select#advField${slot}`)).toBeVisible({ timeout: 5_000 });
}

async function chooseField(dialog: Locator, slot: number, label: string): Promise<void> {
  const select = dialog.locator(`select#advField${slot}`);
  await expect(select.locator('option', { hasText: label }), `option ${label}`).toHaveCount(1, { timeout: 15_000 });
  await select.selectOption({ label });
}

/** Sends the search and returns the names of the securities found, in table order. */
async function search(page: Page, dialog: Locator): Promise<{ names: string[]; url: URL }> {
  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === 'GET' && /\/api\/watchlist\/\d+\/search(?:\?|$)/.test(response.url())
  );
  await dialog.locator('button[type="submit"]').click();
  const response = await responsePromise;
  expect(response.ok(), `advanced search: ${await response.text()}`).toBeTruthy();
  const rows = dialog.locator('add-instrument-table tbody tr');
  await expect(rows.first()).toBeVisible({ timeout: 15_000 });
  const names = await rows.evaluateAll((trs) =>
    trs.map((tr) => tr.querySelectorAll('td')[1]?.textContent?.trim() ?? '')
  );
  return { names, url: new URL(response.url()) };
}

test.describe.serial('Advanced search of the add-existing-instrument dialog', () => {
  test.skip(withFlag.length === 0, 'no admin UDF data in the fixture');

  test('a Yes/No field finds the securities that carry the value, with its own column', async ({ page }) => {
    const dialog = await openDialog(page);
    await addCriterion(dialog, 1);
    await chooseField(dialog, 1, FLAG_FIELD);
    await dialog.locator('select#advFlag1').selectOption('true');

    const { names, url } = await search(page, dialog);

    expect(url.searchParams.get('advancedCriteria[0].flag')).toBe('true');
    expect(names.slice().sort()).toEqual(withFlag.slice().sort());
    await expect(dialog.locator('add-instrument-table th').filter({ hasText: FLAG_FIELD })).toHaveCount(1);
    await closeAddInstrumentDialog(dialog);
  });

  test('a link is not offered as a field, because a link is opened and not searched', async ({ page }) => {
    const dialog = await openDialog(page);
    await addCriterion(dialog, 1);
    const select = dialog.locator('select#advField1');
    await expect(select.locator('option', { hasText: FLAG_FIELD })).toHaveCount(1, { timeout: 15_000 });

    const labels = await select.locator('option').allTextContents();

    expect(labels.filter((label) => label.trim() !== '')).not.toHaveLength(0);
    expect(labels, `${LINK_FIELD} holds a link`).not.toContain(LINK_FIELD);
    expect(
      labels.filter((label) => /link/i.test(label)),
      'no field that holds a link'
    ).toEqual([]);
    await closeAddInstrumentDialog(dialog);
  });

  test('a criterion combines with a fixed criterion by AND', async ({ page }) => {
    const dialog = await openDialog(page);
    await addCriterion(dialog, 1);
    await chooseField(dialog, 1, FLAG_FIELD);
    await dialog.locator('select#advFlag1').selectOption('true');
    await dialog.getByRole('textbox', { name: /^Name$/ }).fill('Disney');

    const { names } = await search(page, dialog);

    const expected = withFlag.filter((name) => name.includes('Disney'));
    expect(expected.length, 'fixture holds a Disney security with the flag').toBeGreaterThan(0);
    expect(names.slice().sort()).toEqual(expected.slice().sort());
    await closeAddInstrumentDialog(dialog);
  });

  test('a started criterion blocks the search until it is complete, and the ISIN excludes the block', async ({
    page
  }) => {
    const dialog = await openDialog(page);
    const submit = dialog.locator('button[type="submit"]');
    await addCriterion(dialog, 1);
    await chooseField(dialog, 1, FLAG_FIELD);
    await expect(submit, 'field chosen without a value').toBeDisabled();
    await expect(dialog.locator('#isin'), 'a chosen field hides the ISIN like every other criterion').toBeHidden();

    await dialog.locator('select#advFlag1').selectOption('false');
    await expect(submit).toBeEnabled();

    // Without a chosen field the ISIN stays available; entering one hides the block and clearing it shows it again.
    await dialog.locator('select#advField1').selectOption({ label: '' });
    await expect(dialog.locator('#isin')).toBeVisible();
    await dialog.locator('#isin').fill('US30303M1027');
    await expect(dialog.locator('select#advField1')).toBeHidden();
    await expect(dialog.getByRole('button', { name: RX.addCriterion })).toBeHidden();

    await dialog.locator('#isin').fill('');
    await expect(dialog.locator('select#advField1')).toBeVisible();
    await expect(dialog.getByRole('button', { name: RX.addCriterion })).toBeVisible();
    await closeAddInstrumentDialog(dialog);
  });

  test('removing a criterion frees its place and the other keeps its field', async ({ page }) => {
    const dialog = await openDialog(page);
    await addCriterion(dialog, 1);
    await chooseField(dialog, 1, FLAG_FIELD);
    await addCriterion(dialog, 2);
    const second = dialog.locator('select#advField2');
    const offered = await second.locator('option').evaluateAll((options) =>
      options.map((option) => ({
        value: (option as HTMLOptionElement).value,
        label: option.textContent?.trim() ?? ''
      }))
    );
    const other = offered.find((option) => option.value !== '');
    test.skip(!other, 'the user has a second searchable field');
    expect(
      offered.map((option) => option.label),
      'a field chosen elsewhere is not offered again'
    ).not.toContain(FLAG_FIELD);
    await second.selectOption(other!.value);

    await dialog.getByRole('button', { name: RX.removeCriterion }).first().click();

    await expect(dialog.locator('select#advField1')).toBeHidden();
    await expect(second).toBeVisible();
    await expect(second).toHaveValue(other!.value);
    await closeAddInstrumentDialog(dialog);
  });
});
