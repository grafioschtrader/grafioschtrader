import { expect, Locator, Page, Route, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { getUser, loginAsFixtureUser } from './helpers';
import { expectToast } from './manage-client.helpers';
import { toShortDate } from './portfolio.helpers';

/**
 * Marker for an instrument whose issuer no longer delivers prices.
 *
 * The values are the production row of the Alfa bond (CH0385518086, CHF) in testdata/bankrupt-security.json.
 * That bond is already in V2__testdata.sql together with its closing prices. A security created in this spec
 * would have no quote yet, and the backend refuses a marker without one (gt.bankrupt.no.quote). CRE18
 * (CH0398677754) is the other seeded bankrupt candidate and belongs to 062-historyquote-bulk-tools.
 *
 * alledit holds ROLE_ALL_EDIT, so the shared instrument can be marked without a change proposal. The spec
 * deletes its own row at the start, creates it, cancels and saves edits, deletes it and creates it again.
 * Delayed responses exercise OnPush notification and leaving/closing an owner during a request. The marker
 * is left with its fixture values; the next run removes it first. No other spec reads this bond.
 */
interface BankruptFixture {
  isin: string;
  currency: string;
  name: string;
  noDataSince: string;
  noTradingSince: string;
  note: string;
  e2e: string;
}

const FIXTURE_PATH = path.resolve(
  __dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/bankrupt-security.json'
);
const LOGIN_NICKNAME = 'alledit';

const RX = {
  baseData: /Basisdaten|Base Data/i,
  node: /^\s*(Instrument ohne Kursdaten|Instrument without price data)\s*$/i,
  create: /(Erstellen\s+Instrument ohne Kursdaten|Create\s+Instrument without price data)/i,
  edit: /(Bearbeiten\s+Instrument ohne Kursdaten|Edit\s+Instrument without price data)/i,
  deleteItem: /(Löschen\s+Instrument ohne Kursdaten|Delete\s+Instrument without price data)/i,
  assignSelected: /(Assign\s*selected|Selektierte\s*zuweisen)/i,
  confirm: /Confirmation|Bestätigung/i,
  yes: /^(yes|ja)$/i,
  saved: /(Instrument ohne Kursdaten wurde gespeichert|Instrument without price data was saved)/i,
  deleted: /(Instrument ohne Kursdaten wurde gelöscht|Instrument without price data was deleted)/i
};

function loadRows(): BankruptFixture[] {
  return (JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8')) as BankruptFixture[]).filter((row) => row.e2e === 'e');
}

function markerRow(page: Page, isin: string): Locator {
  return page.locator('configurable-table tbody tr').filter({ hasText: isin });
}

test.describe('bankrupt security marker', () => {
  test.use({ viewport: { width: 1400, height: 1024 } });

  for (const row of loadRows()) {
    test(`creates, edits, deletes and recreates the marker of ${row.isin}`, async ({ page }) => {
      await loginAsFixtureUser(page, LOGIN_NICKNAME);
      await openBankruptView(page);
      await deleteMarkerIfPresent(page, row);
      await createMarker(page, row);
      await expectMarker(page, row);
      const edited = { ...row, noTradingSince: '2023-03-21', note: `${row.note} — OnPush edit` };
      const dialog = await openMarkerDialog(page, row, RX.edit);
      await expect(dialog.locator('input#securityName')).toHaveValue(row.name);
      await expect(dialog.locator('p-inputgroup:has(input#securityName) button')).toBeDisabled();
      await chooseTradingDate(page, dialog, edited.noTradingSince);
      await dialog.locator('#note').fill(edited.note);
      // Cancelling must leave the displayed entity untouched, including on repeated openings.
      await closeMarkerDialog(dialog);
      await expectMarker(page, row);
      const reopened = await openMarkerDialog(page, row, RX.edit);
      await expect(reopened.locator('#note')).toHaveValue(row.note);
      await chooseTradingDate(page, reopened, edited.noTradingSince);
      await reopened.locator('#note').fill(edited.note);
      const reload = await holdResponse(page, '**/api/bankruptsecurity');
      try {
        await reopened.locator('button[type="submit"]').click();
        await reload.arrived;
        await expectMarker(page, row);
        reload.release();
        await reload.done;
        await expectMarker(page, edited);
      } finally {
        reload.release();
      }
      await deleteMarker(page, row);
      await expect(markerRow(page, row.isin)).toHaveCount(0);
      await createMarker(page, row);
      await expectMarker(page, row);
    });
  }

  test('leaving during the form-definition request does not open an abandoned dialog', async ({ page }) => {
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    await openBankruptView(page);
    const definition = await holdResponse(page, '**/api/globalparameters/formdefinition/BankruptSecurity*');
    try {
      await clickMarkerMenu(page, page.locator('configurable-table'), RX.create);
      await definition.arrived;
      await page.getByRole('treeitem', { name: 'Dashboard', exact: true }).locator('.p-tree-node-content').click();
      await expect(page).not.toHaveURL(/\/bankruptsecurity/);
      definition.release();
      await definition.done;
      await expect(page.locator('bankrupt-security-edit')).toHaveCount(0);
      await openBankruptView(page);
      const dialog = await openMarkerDialog(page, null, RX.create);
      await expect(dialog.locator('input#securityName')).toHaveValue('');
      await closeMarkerDialog(dialog);
    } finally {
      definition.release();
    }
  });

  test('closing during search options loading leaves a clean edit session', async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (error) => errors.push(error.message));
    await loginAsFixtureUser(page, LOGIN_NICKNAME);
    await openBankruptView(page);
    const dialog = await openMarkerDialog(page, null, RX.create);
    const options = await holdResponse(page, '**/api/multiplerequesttoone/dataforcurrencysecuritysearch');
    try {
      await dialog.locator('p-inputgroup:has(input#securityName) button').click();
      await options.arrived;
      const search = page.locator('.p-dialog').filter({ has: page.locator('securitycurrency-search-and-set') });
      await search.locator('.p-dialog-close-button').click();
      await expect(search).toHaveCount(0);
      await closeMarkerDialog(dialog);
      const reopened = await openMarkerDialog(page, null, RX.create);
      await reopened.locator('#note').fill('Unsaved draft');
      options.release();
      await options.done;
      await expect(reopened.locator('input#securityName')).toHaveValue('');
      await expect(reopened.locator('#note')).toHaveValue('Unsaved draft');
      await expect(reopened.locator('button[type="submit"]')).toBeDisabled();
      await closeMarkerDialog(reopened);
      expect(errors).toEqual([]);
    } finally {
      options.release();
    }
  });
});

/** Holds the first real GET response; assertions after release observe the asynchronous notification itself. */
async function holdResponse(page: Page, url: string) {
  let release!: () => void;
  let arrived!: () => void;
  let done!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const requestArrived = new Promise<void>((resolve) => (arrived = resolve));
  const completed = new Promise<void>((resolve) => (done = resolve));
  let holding = false;
  const handler = async (route: Route): Promise<void> => {
    if (holding || route.request().method() !== 'GET') return route.fallback();
    holding = true;
    try {
      const response = await route.fetch();
      expect(response.ok()).toBeTruthy();
      arrived();
      await gate;
      try {
        await route.fulfill({ response });
      } catch (error) {
        if (route.request().failure()?.errorText !== 'net::ERR_ABORTED') throw error;
      }
    } finally {
      await page.unroute(url, handler);
      done();
    }
  };
  await page.route(url, handler);
  return { release, arrived: requestArrived, done: completed };
}

/** Opens Base data → Instrument without price data and waits until the table has loaded. */
async function openBankruptView(page: Page): Promise<void> {
  const loaded = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname.endsWith('/api/bankruptsecurity') &&
      response.ok()
  );
  const node = page.locator('.p-tree-node-content', { hasText: RX.node }).first();
  if (!(await node.isVisible().catch(() => false))) {
    const baseData = page.locator('.p-tree-node-content', { hasText: RX.baseData }).first();
    await baseData.waitFor({ state: 'visible', timeout: 15_000 });
    await baseData.dblclick();
  }
  await node.waitFor({ state: 'visible', timeout: 15_000 });
  await node.click();
  await page.waitForURL(/\/bankruptsecurity/, { timeout: 10_000 });
  await loaded;
  await page.locator('configurable-table').waitFor({ state: 'visible', timeout: 10_000 });
}

async function deleteMarkerIfPresent(page: Page, row: BankruptFixture): Promise<void> {
  const marker = markerRow(page, row.isin);
  if ((await marker.count()) === 0) {
    return;
  }
  await deleteMarker(page, row);
}

async function createMarker(page: Page, row: BankruptFixture): Promise<void> {
  const dialog = await openMarkerDialog(page, null, RX.create);
  await selectSecurity(page, dialog, row);
  const locale = getUser(LOGIN_NICKNAME).localeStr;
  await typeDate(dialog, 'noDataSince', row.noDataSince, locale);
  await typeDate(dialog, 'noTradingSince', row.noTradingSince, locale);
  const note = dialog.locator('#note input, textarea#note, input#note').first();
  await note.fill(row.note);
  await note.dispatchEvent('input');

  await dialog.locator('button[type="submit"]').click();
  await dialog.waitFor({ state: 'hidden', timeout: 15_000 });
  await expectToast(page, RX.saved);
}

async function clickMarkerMenu(page: Page, target: Locator, action: RegExp): Promise<void> {
  // Clicking the selected row again clears the selection and removes its Edit/Delete actions.
  if (!(await target.getAttribute('class'))?.split(' ').includes('p-datatable-row-selected')) {
    await target.click();
  }
  await target.click({ button: 'right' });
  const item = page.locator('[role="menu"]:visible').getByText(action).first();
  await expect(item).toBeVisible();
  await item.click();
}

async function openMarkerDialog(page: Page, row: BankruptFixture | null, action: RegExp): Promise<Locator> {
  await clickMarkerMenu(page, row ? markerRow(page, row.isin) : page.locator('configurable-table'), action);
  const dialog = page.locator('bankrupt-security-edit .p-dialog');
  await expect(dialog.locator('input#securityName')).toBeVisible();
  // onShow initializes the form after the dialog has been rendered.
  await expect(dialog.locator('#noDataSince input, input#noDataSince').first()).toBeEnabled();
  return dialog;
}

async function closeMarkerDialog(dialog: Locator): Promise<void> {
  await dialog.locator('.p-dialog-close-button').click();
  await expect(dialog).toHaveCount(0);
}

/** The edit starts on the fixture's March 2023 date; choose the adjacent day through the calendar. */
async function chooseTradingDate(page: Page, dialog: Locator, isoDate: string): Promise<void> {
  await dialog.locator('#noTradingSince .p-datepicker-dropdown').click();
  const panel = page.locator('.p-datepicker-panel:visible');
  await panel
    .locator('.p-datepicker-day')
    .getByText(String(Number(isoDate.slice(-2))), { exact: true })
    .click();
  await expect(dialog.locator('#noTradingSince input')).toHaveValue(
    toShortDate(isoDate, getUser(LOGIN_NICKNAME).localeStr)
  );
}

/**
 * Picks the instrument in the search dialog opened from the security-name button. The first click on that
 * button can be swallowed while the dialog is still settling, so the click is repeated until the search
 * dialog appears. Nothing is typed before the exchange options exist: their arrival is what shows that
 * setDefaultValuesAndEnableSubmit() has already reset the form.
 */
async function selectSecurity(page: Page, dialog: Locator, row: BankruptFixture): Promise<void> {
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
  await isin.fill(row.isin);
  await isin.dispatchEvent('input');
  await search.locator('button[type="submit"]').click();

  const result = search.locator('securitycurrency-search-and-set-table tbody tr').filter({ hasText: row.isin }).first();
  await result.waitFor({ state: 'visible', timeout: 15_000 });
  await expect(result).toContainText(row.name);
  await result.click();
  const assign = search.getByRole('button', { name: RX.assignSelected }).first();
  await expect(assign).toBeEnabled({ timeout: 5_000 });
  await assign.click();
  await search.waitFor({ state: 'hidden', timeout: 10_000 });
  await expect(dialog.locator('input#securityName')).toHaveValue(row.name);
}

async function typeDate(scope: Locator, fieldId: string, isoDate: string, locale: string): Promise<void> {
  const input = scope.locator(`#${fieldId} input, input#${fieldId}`).first();
  const value = toShortDate(isoDate, locale);
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(value, { delay: 20 });
  await input.blur();
  await expect(input).toHaveValue(value);
}

async function expectMarker(page: Page, row: BankruptFixture): Promise<void> {
  const marker = markerRow(page, row.isin);
  await expect(marker).toHaveCount(1, { timeout: 15_000 });
  const locale = getUser(LOGIN_NICKNAME).localeStr;
  await expect(marker).toContainText(row.name);
  await expect(marker).toContainText(row.currency);
  await expect(marker).toContainText(row.note);
  await expect(marker.locator('td').nth(3)).toHaveText(toShortDate(row.noDataSince, locale));
  await expect(marker.locator('td').nth(4)).toHaveText(toShortDate(row.noTradingSince, locale));
}

async function deleteMarker(page: Page, row: BankruptFixture): Promise<void> {
  const marker = markerRow(page, row.isin);
  await clickMarkerMenu(page, marker, RX.deleteItem);

  const confirm = page.getByRole('alertdialog', { name: RX.confirm });
  await confirm.waitFor({ state: 'visible', timeout: 5_000 });
  await confirm.getByRole('button', { name: RX.yes }).click();
  await expectToast(page, RX.deleted);
  await expect(marker).toHaveCount(0, { timeout: 15_000 });
}
