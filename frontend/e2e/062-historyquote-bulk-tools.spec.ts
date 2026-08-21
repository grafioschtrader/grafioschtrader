import {expect, Locator, Page, test} from '@playwright/test';
import {loginAsFixtureUser} from './helpers';
import {expectToast} from './manage-client.helpers';

/**
 * Bulk history-quote maintenance for the seeded CRE18 bond.
 *
 * Spec 040 creates and populates admin's `_Schwellenländer Bond` watchlist. CRE18 itself and its price history come
 * from V2__testdata.sql: connector quotes, a linear filling, and exactly two quotes entered by a user. Each run first
 * removes the deletable create types and then fills the resulting trading-day gaps up to the dialog's proposed date.
 * If an earlier run stopped between those operations, the already-clean state is accepted and the filling resumes.
 */

const LOGIN_NICKNAME = 'admin';
const WATCHLIST_NAME = '_Schwellenländer Bond';
const SECURITY_TICKER = 'CRE18';
const SECURITY_NAME_RX = /Credito Real SAB/i;

const HISTORYQUOTE_CREATE_TYPE = {
  MANUAL_IMPORTED: 'MANUAL_IMPORTED',
  FILLED_CLOSED_LINEAR_TRADING_DAY: 'FILLED_CLOSED_LINEAR_TRADING_DAY',
  ADD_MODIFIED_USER: 'ADD_MODIFIED_USER',
} as const;
const FILLED_LINEAR_CREATE_TYPE_QUERY_VALUE = 3;

const EXPECTED_USER_QUOTE_DATES = ['2024-12-31', '2025-04-14'];
const MAX_LATEST_QUOTE_AGE_DAYS = 4;

interface Quote {
  date: string;
  createType: string;
}

interface HistoryquoteQuality {
  manualImported: number;
  filledLinear: number;
  userModified: number;
}

interface QuotesPayload {
  historyquoteList: Quote[];
  historyquoteQuality: HistoryquoteQuality;
}

interface DeleteBounds {
  minDate: string;
  maxDate: string;
}

interface FillGapsBounds {
  defaultFillUpTo: string;
}

interface FillGapsParam {
  moveWeekendToFriday: boolean;
  fillUpToDate: string;
}

const RX = {
  eodTableItem: /(EOD as table|Tagesendkurse als Tabelle)/i,
  deleteCreateTypesItem: /(Delete imported and\/or linear filled|Importierte und\/oder linear bef.llte l.schen)/i,
  deleteDialogHeader: /(Delete imported and\/or linear filled|Importierte und\/oder linear bef.llte l.schen)/i,
  fillGapsItem: /(Linear filling missing EOD|Lineares bef.llen fehlender Kursdaten)/i,
  fillGapsDialogHeader: /(Linear filling missing EOD|Lineares bef.llen fehlender Kursdaten)/i,
  executeButton: /^(Execute|Ausf.hren)$/i,
  deletedToast: /(Deleted:|Gel.scht wurde:)/i,
  filledToast: /(Filled gaps:|Gef.llte L.cken:)/i,
};

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** Converts yyyy-MM-dd to the short date format of admin's de-CH locale. */
function toDeChDate(iso: string): string {
  const [year, month, day] = iso.split('-');
  return `${day}.${month}.${year.slice(2)}`;
}

function parseIsoDate(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function toIsoDate(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Uses the preceding Friday when the backend's proposed current date falls on a weekend. */
function weekendAdjustedFillDate(defaultFillUpTo: string): string {
  const date = parseIsoDate(defaultFillUpTo);
  if (date.getDay() === 6) {
    date.setDate(date.getDate() - 1);
  } else if (date.getDay() === 0) {
    date.setDate(date.getDate() - 2);
  }
  return toIsoDate(date);
}

function daysBetween(laterIso: string, earlierIso: string): number {
  const millisecondsPerDay = 24 * 60 * 60 * 1000;
  const later = Date.UTC(...isoDateParts(laterIso));
  const earlier = Date.UTC(...isoDateParts(earlierIso));
  return (later - earlier) / millisecondsPerDay;
}

function isoDateParts(iso: string): [number, number, number] {
  const [year, month, day] = iso.split('-').map(Number);
  return [year, month - 1, day];
}

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

async function responseJson<T>(responsePromise: Promise<import('@playwright/test').Response>): Promise<T> {
  const response = await responsePromise;
  const body = await response.text();
  expect(response.ok(), `${response.request().method()} ${response.url()} → ${response.status()}: ${body}`).toBe(true);
  return JSON.parse(body) as T;
}

async function openEodTable(page: Page): Promise<QuotesPayload> {
  await loginAsFixtureUser(page, LOGIN_NICKNAME);

  const watchlistNode = page.getByRole('treeitem', {name: WATCHLIST_NAME, exact: true}).first();
  await watchlistNode.waitFor({state: 'visible', timeout: 15_000});
  await watchlistNode.click();

  const container = page.locator('.data-container').first();
  await container.waitFor({state: 'visible', timeout: 15_000});
  const securityRow = container.locator('tbody tr').filter({hasText: SECURITY_TICKER}).first();
  await expect(securityRow, `${SECURITY_TICKER} in ${WATCHLIST_NAME}`).toContainText(SECURITY_NAME_RX, {timeout: 20_000});

  const tickerCell = securityRow.locator('td').filter({hasText: new RegExp(`^\\s*${SECURITY_TICKER}\\s*$`)}).first();
  await tickerCell.click();
  await page.waitForTimeout(500);
  await tickerCell.click({button: 'right'});

  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  const quotes = waitForQuotes(page);
  await menu.getByText(RX.eodTableItem).first().click();

  await page.waitForURL(/historyquotes/, {timeout: 15_000});
  await page.locator('historyquote-table').waitFor({state: 'visible', timeout: 20_000});
  return quotes;
}

async function openTableContextMenu(page: Page): Promise<Locator> {
  const heading = page.locator('historyquote-table h4').first();
  await heading.waitFor({state: 'visible', timeout: 10_000});
  await heading.click();
  await page.waitForTimeout(400);
  await heading.click({button: 'right'});

  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  return menu;
}

function assertOnlyExpectedUserQuotesRemain(payload: QuotesPayload): void {
  expect(payload.historyquoteList.filter(quote =>
    quote.createType === HISTORYQUOTE_CREATE_TYPE.MANUAL_IMPORTED)).toHaveLength(0);
  expect(payload.historyquoteList.filter(quote =>
    quote.createType === HISTORYQUOTE_CREATE_TYPE.FILLED_CLOSED_LINEAR_TRADING_DAY)).toHaveLength(0);

  const userQuoteDates = payload.historyquoteList
    .filter(quote => quote.createType === HISTORYQUOTE_CREATE_TYPE.ADD_MODIFIED_USER)
    .map(quote => quote.date)
    .sort();
  expect(userQuoteDates).toEqual(EXPECTED_USER_QUOTE_DATES);
  expect(payload.historyquoteQuality.manualImported).toBe(0);
  expect(payload.historyquoteQuality.filledLinear).toBe(0);
  expect(payload.historyquoteQuality.userModified).toBe(2);
}

async function replaceDate(dateInput: Locator, iso: string): Promise<void> {
  const displayedDate = toDeChDate(iso);
  await dateInput.click();
  await dateInput.press('Control+a');
  await dateInput.press('Backspace');
  await dateInput.pressSequentially(displayedDate, {delay: 20});
  await dateInput.blur();
  await expect(dateInput).toHaveValue(displayedDate);
}

test.describe('CRE18 history-quote bulk delete and gap filling', () => {
  test.use({viewport: {width: 1600, height: 1200}});

  test('deletes generated quotes, retains two user quotes, and fills through the latest trading day', async ({page}) => {
    const initial = await openEodTable(page);
    expect(initial.historyquoteList.length, `${SECURITY_TICKER} has no seeded history quotes`).toBeGreaterThan(0);

    let afterDelete = initial;
    if (initial.historyquoteQuality.filledLinear > 0 || initial.historyquoteQuality.manualImported > 0) {
      const menu = await openTableContextMenu(page);
      const boundsResponse = page.waitForResponse(response =>
        /\/historyquote\/deletebounds\/\d+(\?|$)/.test(response.url())
        && response.request().method() === 'GET', {timeout: 20_000});
      await menu.getByText(RX.deleteCreateTypesItem).first().click();
      const bounds = await responseJson<DeleteBounds>(boundsResponse);

      const dialog = page.locator('historyquote-delete-dialog .p-dialog')
        .filter({hasText: RX.deleteDialogHeader}).first();
      await dialog.waitFor({state: 'visible', timeout: 10_000});
      await expect(dialog.locator('#dateFrom input')).toHaveValue(toDeChDate(bounds.minDate));
      await expect(dialog.locator('#dateTo input')).toHaveValue(toDeChDate(bounds.maxDate));

      const deleteResponse = page.waitForResponse(response =>
        /\/historyquote\/delete\/\d+(\?|$)/.test(response.url())
        && response.request().method() === 'DELETE', {timeout: 30_000});
      const refreshedQuotes = waitForQuotes(page);
      await dialog.getByRole('button', {name: RX.executeButton}).click();

      const deleted = await deleteResponse;
      const deleteUrl = new URL(deleted.url());
      expect(deleted.ok(), `DELETE ${deleted.url()} → ${deleted.status()}`).toBe(true);
      expect(deleteUrl.searchParams.get('dateFrom')).toBe(bounds.minDate);
      expect(deleteUrl.searchParams.get('dateTo')).toBe(bounds.maxDate);
      expect(deleteUrl.searchParams.getAll('createTypes'))
        .toContain(String(FILLED_LINEAR_CREATE_TYPE_QUERY_VALUE));
      await expectToast(page, RX.deletedToast);
      await dialog.waitFor({state: 'hidden', timeout: 15_000});
      afterDelete = await refreshedQuotes;
    }

    assertOnlyExpectedUserQuotesRemain(afterDelete);

    const menu = await openTableContextMenu(page);
    const boundsResponse = page.waitForResponse(response =>
      /\/security\/\d+\/fillgapsbounds(\?|$)/.test(response.url())
      && response.request().method() === 'GET', {timeout: 20_000});
    await menu.getByText(RX.fillGapsItem).first().click();
    const bounds = await responseJson<FillGapsBounds>(boundsResponse);

    const dialog = page.locator('historyquote-quality-fill-gaps .p-dialog')
      .filter({hasText: RX.fillGapsDialogHeader}).first();
    await dialog.waitFor({state: 'visible', timeout: 10_000});
    const fillDateInput = dialog.locator('#fillUpToDate input');
    await expect(fillDateInput).toHaveValue(toDeChDate(bounds.defaultFillUpTo));

    const selectedFillDate = weekendAdjustedFillDate(bounds.defaultFillUpTo);
    if (selectedFillDate !== bounds.defaultFillUpTo) {
      await replaceDate(fillDateInput, selectedFillDate);
    }

    const fillResponse = page.waitForResponse(response =>
      /\/security\/\d+\/fillgapes(\?|$)/.test(response.url())
      && response.request().method() === 'POST', {timeout: 60_000});
    const refreshedQuotes = waitForQuotes(page);
    await dialog.getByRole('button', {name: RX.executeButton}).click();

    const filled = await fillResponse;
    const fillBody = filled.request().postDataJSON() as FillGapsParam;
    const fillResponseBody = await filled.text();
    expect(filled.ok(), `POST ${filled.url()} → ${filled.status()}: ${fillResponseBody}`).toBe(true);
    expect(fillBody.fillUpToDate).toBe(selectedFillDate);
    await expectToast(page, RX.filledToast);
    await dialog.waitFor({state: 'hidden', timeout: 15_000});

    const afterFill = await refreshedQuotes;
    const newestDate = afterFill.historyquoteList.map(quote => quote.date).sort().at(-1);
    expect(newestDate, `${SECURITY_TICKER} has no quote after linear filling`).toBeTruthy();
    const latestQuoteAge = daysBetween(selectedFillDate, newestDate!);
    expect(latestQuoteAge, `newest ${SECURITY_TICKER} quote ${newestDate} is after ${selectedFillDate}`)
      .toBeGreaterThanOrEqual(0);
    expect(latestQuoteAge,
      `newest ${SECURITY_TICKER} quote ${newestDate} is more than ${MAX_LATEST_QUOTE_AGE_DAYS} days before `
      + selectedFillDate).toBeLessThanOrEqual(MAX_LATEST_QUOTE_AGE_DAYS);
  });
});
