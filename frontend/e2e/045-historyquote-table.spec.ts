import {expect, Locator, Page, test} from '@playwright/test';
import {loginAsCsvUser} from './helpers';
import {expectToast} from './manage-client.helpers';

/**
 * End-of-day price views of a security that is seeded WITH its price history.
 *
 * The spec uses the watchlist 'Switzerland' created by 032-create-watchlist and adds 'Nestlé AG'
 * (CH0038863350) through the search dialog. It opens the historical prices
 * (HistoryquoteTableComponent), deletes the most recent quote, and recreates it with the same values
 * through the create dialog
 * (HistoryquoteEditComponent).
 *
 * Why this instrument: nv.bat dumps the CH/US securities that have a future active_to_date and no
 * API-key connector into V2__testdata.sql *together with their historyquote rows*. Nestlé is one of
 * them (id_securitycurrency 1980, ~6700 quotes from 2000 onwards), so the data exists the moment
 * Flyway has run. Securities created through the UI from generated/securities.csv are useless here:
 * saving one only enqueues a background price import that runs behind the startup price update and
 * may not have produced a single quote by the time the spec looks.
 *
 * Rights: Nestlé is shared data (id_tenant_private NULL, created by the system user), so 'alledit'
 * is not its owner — but ROLE_ALLEDIT short-circuits the ownership check in
 * UserAccessHelper.hasRightsOrPrivilegesForEditingOrDelete (backend) and
 * AuditHelper.hasRightsForEditingOrDeleteAuditable (frontend). Quotes are therefore edited and
 * deleted directly, without the propose-change flow, and ALL_EDIT is exempt from the daily
 * historyquote limit. That user runs a German UI (de-CH in users.csv), therefore every text selector
 * matches DE + EN and dates are written in the de-CH short format (dd.mm.yy) both in the table cells
 * and in the date picker.
 *
 * Repeatability: the instrument is only added when missing, and delete-then-recreate is
 * idempotent — a rerun finds the same date as the newest quote and repeats the cycle (the recreated
 * row carries createType ADD_MODIFIED_USER instead of CONNECTOR_CREATED, which does not change the
 * flow). Because the values are written back unchanged, the shared Nestlé series stays intact for
 * 050-correlation-matrix.spec.ts, which uses it as well. Only an abort between the delete and the
 * recreate leaves that single connector row missing; the next run then targets the row before it.
 */

const LOGIN_NICKNAME = 'alledit';
const WATCHLIST_NAME = 'Switzerland';
const SECURITY_ISIN = 'CH0038863350';
/** Exact name in V2__testdata.sql; SECURITY_RX is used wherever the 'é' would have to be typed. */
const SECURITY_NAME = 'Nestlé AG';
const SECURITY_RX = /Nestl.+AG/;

/** One entry of HistoryquotesWithMissings.historyquoteList as delivered by the REST endpoint. */
interface Quote {
  idHistoryQuote: number;
  date: string;               // yyyy-MM-dd
  close: number;
  volume: number | null;
  open: number | null;
  high: number | null;
  low: number | null;
  createType: number;
}

interface QuotesPayload {
  historyquoteList: Quote[];
}

/** Column order of HistoryquoteTableComponent: date, T, creation date, volume, O, H, L, close. */
const COL_DATE = 0;
const COL_CLOSE = 7;

const RX = {
  addExistingItem: /(Bestehendes\s+Instrument\s+hinzuf.gen|Add\s+existing\s+instrument)/i,
  addButton: /^(Hinzuf.gen|Add)$/,
  closeButton: /^(Beenden|Close)$/,
  eodTableItem: /(EOD as table|Tagesendkurse als Tabelle)/i,
  createItem: /^(Create History quote|Erstellen Historischer Kurse)\.\.\.$/,
  deleteItem: /^(Delete History quote|Löschen Historischer Kurse)$/,
  editDialogHeader: /(Historical quote for|Historischer Kurs f)/i,
  confirmYes: /^(yes|ja)$/i,
  deletedToast: /(History quote was deleted|Historischer Kurse wurde gel)/i,
  savedToast: /(History quotes was saved|Historische Kurse wurde gespeichert)/i,
  entityCaption: /(History quote|Historischer Kurse)/i,
};

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** '2026-07-24' → '24.07.26' — the de-CH short date used by the table cells and the date picker. */
function toDeChDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${day}.${month}.${year.slice(2)}`;
}

function todayIso(): string {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * Newest quote that may be recreated: HistoryquoteJpaRepositoryImpl.checkDatePastMinus1Day rejects
 * a quote dated today or on a weekend, and the dialog's date picker sets maxDate = yesterday. The
 * connector can deliver a partial candle for the current day, so the very first row is not always
 * a valid target.
 */
function newestEligibleQuote(quotes: Quote[]): Quote {
  const today = todayIso();
  const eligible = quotes
    .filter(quote => quote.date < today)
    .filter(quote => {
      const dayOfWeek = new Date(`${quote.date}T00:00:00`).getDay();
      return dayOfWeek !== 0 && dayOfWeek !== 6;
    })
    .sort((a, b) => b.date.localeCompare(a.date));
  expect(eligible.length,
    `no history quote of ${SECURITY_NAME} older than ${today} — is V2__testdata.sql seeded?`)
    .toBeGreaterThan(0);
  return eligible[0];
}

/** Parses a displayed number; tolerates the de-CH group separator (a right single quote). */
function parseLocaleNumber(text: string): number {
  return parseFloat(text.replace(/[^\d.-]/g, ''));
}

/**
 * Awaits the table's own data request. The URL filter excludes the sibling endpoints
 * .../dateclose and .../forchart, which share the same path prefix.
 *
 * The status is deliberately NOT part of the matcher: a failing request would otherwise never match
 * and surface as an opaque 30 s waitForResponse timeout. Matching it and asserting afterwards puts
 * the server's own error message into the failure instead.
 */
function waitForQuotes(page: Page): Promise<QuotesPayload> {
  return page.waitForResponse(response =>
    /\/historyquote\/securitycurrency\/\d+(\?|$)/.test(response.url())
    && response.request().method() === 'GET', {timeout: 30_000})
    .then(async response => {
      const body = await response.text();
      expect(response.ok(), `GET ${response.url()} → ${response.status()}: ${body}`).toBe(true);
      return JSON.parse(body) as QuotesPayload;
    });
}

/**
 * The table row whose date column holds the given de-CH date. The cell text is wrapped in a
 * multi-line span, and `hasText` does not normalize whitespace for a regular expression — hence the
 * explicit \s* around the anchors.
 */
function rowByDate(page: Page, deChDate: string): Locator {
  const cellText = new RegExp(`^\\s*${deChDate.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
  return page.locator('historyquote-table tbody tr')
    .filter({has: page.locator('td:nth-child(1)', {hasText: cellText})});
}

/** Selects the watchlist in the left tree and waits for its table to render. */
async function openWatchlist(page: Page): Promise<void> {
  const watchlistNode = page.getByRole('treeitem', {name: WATCHLIST_NAME, exact: true}).first();
  await watchlistNode.waitFor({state: 'visible', timeout: 15_000});
  await watchlistNode.click();
  await page.locator('.data-container').first().waitFor({state: 'visible', timeout: 15_000});
}

/** The watchlist row of the security under test. */
function securityCell(page: Page): Locator {
  return page.locator('td').filter({hasText: SECURITY_RX}).first();
}

/**
 * Adds the security to the open watchlist through the "Add existing instrument" search dialog,
 * searching by ISIN. The dialog stays open after the add, so it is closed explicitly.
 */
async function addSecurityByIsin(page: Page): Promise<void> {
  const container = page.locator('.data-container').first();
  await container.click();
  await page.waitForTimeout(300);
  await container.click({button: 'right'});

  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  await menu.getByText(RX.addExistingItem).first().click();

  const addDialog = page.locator('watchlist-add-instrument');
  await addDialog.locator('.p-dialog').waitFor({state: 'visible', timeout: 10_000});

  // NOTHING may be typed before the search master data has arrived: the subscribe in
  // SecuritycurrencySearchBase.initialize() assigns the dropdown options and then calls
  // setDefaultValuesAndEnableSubmit(), which is a form.reset() — anything entered earlier is
  // silently wiped, leaving the criteria empty and Search disabled by atLeastOneFieldValidator.
  // idStockexchange is filled one line before that reset, so its options prove it already happened.
  await expect(addDialog.locator('select#idStockexchange option')).not.toHaveCount(0, {timeout: 15_000});

  const isin = addDialog.locator('#isin');
  await isin.click();
  await isin.fill(SECURITY_ISIN);
  await isin.dispatchEvent('input');
  await addDialog.locator('button[type="submit"]').click();

  // The ISIN is matched exactly, so exactly one instrument can come back.
  const resultRows = addDialog.locator('add-instrument-table tbody tr');
  await expect(resultRows).toHaveCount(1, {timeout: 15_000});
  await expect(resultRows.first()).toContainText(SECURITY_RX);
  await resultRows.first().locator('p-tablecheckbox').click();

  // The Add button re-disables itself once the selection is cleared, which happens in the
  // subscribe of PUT /watchlist/{id}/addSecuritycurrency.
  const add = addDialog.getByRole('button', {name: RX.addButton});
  await expect(add).toBeEnabled({timeout: 5_000});
  await add.click();
  await expect(add).toBeDisabled({timeout: 15_000});

  await addDialog.getByRole('button', {name: RX.closeButton}).click();
  await addDialog.locator('.p-dialog').waitFor({state: 'hidden', timeout: 10_000});
}

/**
 * Logs in, opens the watchlist and navigates to the EOD table of the security. Returns the payload
 * of the request the table issues while loading.
 */
async function openEodTable(page: Page): Promise<QuotesPayload> {
  await loginAsCsvUser(page, LOGIN_NICKNAME);
  await openWatchlist(page);

  // Left-click first: the table binds pContextMenuRow but no [contextMenu], so a right-click alone
  // does not select the row the context menu is built from.
  const cell = securityCell(page);
  await cell.waitFor({state: 'visible', timeout: 20_000});
  await cell.click();
  await page.waitForTimeout(500);
  await cell.click({button: 'right'});

  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  const quotes = waitForQuotes(page);
  await menu.getByText(RX.eodTableItem).first().click();

  await page.waitForURL(/historyquotes/, {timeout: 15_000});
  await page.locator('historyquote-table').waitFor({state: 'visible', timeout: 20_000});
  return quotes;
}

/**
 * Activates the history quote panel and opens its context menu. Without a target the caption
 * heading is used — clicking the container centre would land on a child that consumes the event.
 */
async function openTableContextMenu(page: Page, target?: Locator): Promise<Locator> {
  const clickTarget = (target ?? page.locator('historyquote-table h4')).first();
  await clickTarget.waitFor({state: 'visible', timeout: 10_000});
  await clickTarget.click();
  await page.waitForTimeout(400);
  await clickTarget.click({button: 'right'});

  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  return menu;
}

/**
 * Types a value into a p-inputNumber. Real key events are required: PrimeNG updates its model from
 * the keydown/keypress handlers, so fill() would leave the reactive form untouched. The read-back
 * assertion turns a locale or masking problem into a clear failure instead of a save error.
 */
async function typeNumber(dialog: Locator, field: string, value: number | null): Promise<void> {
  if (value === null || value === undefined) {
    return;
  }
  const input = dialog.locator(`#${field} input`).first();
  await input.click();
  await input.press('Control+a');
  await input.press('Backspace');
  await input.pressSequentially(String(value), {delay: 20});
  await input.press('Tab');

  const shown = await input.inputValue();
  expect(parseLocaleNumber(shown), `${field}: p-inputNumber shows "${shown}" instead of ${value}`)
    .toBeCloseTo(value, 8);
}

test.describe.serial('historical prices of Nestlé — show, delete newest, recreate', () => {
  // The EOD table is rendered in the mainbottom outlet below the watchlist.
  test.use({viewport: {width: 1600, height: 1200}});

  test(`adds ${SECURITY_NAME} to the ${WATCHLIST_NAME} watchlist by ISIN`, async ({page}) => {
    await loginAsCsvUser(page, LOGIN_NICKNAME);
    await openWatchlist(page);

    // Let the watchlist table render before the skip-if-present check. The search endpoint excludes
    // instruments that are already in the watchlist, so adding twice is impossible anyway.
    await page.waitForTimeout(1500);
    if (await securityCell(page).count() === 0) {
      await addSecurityByIsin(page);
    }

    await expect(securityCell(page)).toBeVisible({timeout: 15_000});
  });

  test('shows the historical prices in the history quote table', async ({page}) => {
    const payload = await openEodTable(page);
    const quotes = payload.historyquoteList;
    expect(quotes.length,
      `${SECURITY_NAME} has no EOD data — is V2__testdata.sql seeded in grafioschtrader_t?`)
      .toBeGreaterThan(0);

    const caption = page.locator('historyquote-table h4');
    await expect(caption).toContainText(RX.entityCaption);
    await expect(caption).toContainText(SECURITY_RX);
    await expect(page.locator('historyquote-quality')).toBeVisible();

    // Default sort is date descending, so the newest quote is on the first page.
    const newest = [...quotes].sort((a, b) => b.date.localeCompare(a.date))[0];
    const newestRow = rowByDate(page, toDeChDate(newest.date));
    await expect(newestRow).toHaveCount(1);
    expect(parseLocaleNumber(await newestRow.locator('td').nth(COL_CLOSE).innerText()))
      .toBeCloseTo(newest.close, 5);
    await expect(page.locator('historyquote-table tbody tr').first().locator('td').nth(COL_DATE))
      .toHaveText(toDeChDate(newest.date));
  });

  test('deletes the newest quote and recreates it with the same values', async ({page}) => {
    const payload = await openEodTable(page);
    expect(payload.historyquoteList.length,
      `${SECURITY_NAME} has no EOD data — is V2__testdata.sql seeded in grafioschtrader_t?`)
      .toBeGreaterThan(0);

    const before = newestEligibleQuote(payload.historyquoteList);
    const countBefore = payload.historyquoteList.length;
    const beforeDeCh = toDeChDate(before.date);

    // --- delete ---
    const targetRow = rowByDate(page, beforeDeCh);
    await expect(targetRow).toHaveCount(1);
    const menu = await openTableContextMenu(page, targetRow.locator('td').nth(COL_DATE));

    const deleted = page.waitForResponse(response =>
      /\/historyquote\/\d+(\?|$)/.test(response.url())
      && response.request().method() === 'DELETE' && response.ok(), {timeout: 20_000});
    const afterDeleteQuotes = waitForQuotes(page);
    await menu.getByText(RX.deleteItem).first().click();

    const confirmDialog = page.locator('[role="alertdialog"]:visible').first();
    await confirmDialog.waitFor({state: 'visible', timeout: 10_000});
    await confirmDialog.getByRole('button', {name: RX.confirmYes}).first().click();

    await deleted;
    await expectToast(page, RX.deletedToast);
    const afterDelete = await afterDeleteQuotes;
    expect(afterDelete.historyquoteList.length).toBe(countBefore - 1);
    expect(afterDelete.historyquoteList.some(quote => quote.date === before.date)).toBe(false);
    await expect(rowByDate(page, beforeDeCh)).toHaveCount(0);

    // --- recreate with the identical values ---
    const createMenu = await openTableContextMenu(page);
    await createMenu.getByText(RX.createItem).first().click();

    const dialog = page.locator('.p-dialog').filter({hasText: RX.editDialogHeader}).first();
    await dialog.waitFor({state: 'visible', timeout: 10_000});
    await expect(dialog).toContainText(SECURITY_RX);

    // The date picker ignores input events without a preceding keydown, so type it key by key.
    const dateInput = dialog.locator('#date input').first();
    await dateInput.click();
    await dateInput.pressSequentially(beforeDeCh, {delay: 20});
    await dateInput.blur();
    await expect(dateInput).toHaveValue(beforeDeCh);

    await typeNumber(dialog, 'volume', before.volume);
    await typeNumber(dialog, 'open', before.open);
    await typeNumber(dialog, 'high', before.high);
    await typeNumber(dialog, 'low', before.low);
    await typeNumber(dialog, 'close', before.close);

    const created = page.waitForResponse(response =>
      /\/historyquote$/.test(new URL(response.url()).pathname)
      && response.request().method() === 'POST' && response.ok(), {timeout: 20_000});
    const afterCreateQuotes = waitForQuotes(page);
    await dialog.locator('button[type="submit"]').click();

    await created;
    await expectToast(page, RX.savedToast);
    await dialog.waitFor({state: 'hidden', timeout: 15_000});

    const afterCreate = await afterCreateQuotes;
    expect(afterCreate.historyquoteList.length).toBe(countBefore);
    const recreated = afterCreate.historyquoteList.find(quote => quote.date === before.date);
    expect(recreated, `the quote of ${before.date} was not recreated`).toBeTruthy();
    expect(recreated.close).toBeCloseTo(before.close, 8);
    expect(recreated.volume).toBe(before.volume);
    for (const field of ['open', 'high', 'low'] as const) {
      if (before[field] === null) {
        expect(recreated[field]).toBeNull();
      } else {
        expect(recreated[field]).toBeCloseTo(before[field], 8);
      }
    }
    await expect(rowByDate(page, beforeDeCh)).toHaveCount(1);
  });
});
