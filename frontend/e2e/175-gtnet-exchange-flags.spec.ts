import { expect, Locator, Page, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';

/**
 * The per-instrument GTNet exchange flags: `gtnet-exchange-securities` and `gtnet-exchange-currencypairs`.
 *
 * These four flags decide which instruments an instance offers to its peers and which it wants to receive. They
 * are plain columns of `securitycurrency`, not rows of a join table, so the page is drivable on a single
 * instance: it needs neither an own GTNet entry nor a peer. The application GTNet menu is gated only by
 * `useGtnet()`.
 *
 * Two leftovers this spec has to handle, and one it must not create:
 *
 *  - **Nothing else resets the flags.** Neither `844` nor `888` touches them, so a rerun would otherwise read
 *    the previous run's state. Every flag is therefore cleared through the batch endpoint at the *start*.
 *  - **Leaving the page triggers a sync.** After a successful save `ngOnDestroy` posts
 *    `/api/security/gtnetexchange/triggersync`, which enqueues `GTNET_EXCHANGE_SYNC`. On an instance with no own
 *    entry that task finds nothing to do; on an operational peer it would start a real exchange, which is why
 *    this spec must never run against one.
 *  - **The header strip only toggles what is currently filtered.** `toggleColumn` walks
 *    `table.filteredValue || table.value`, so the assertions below deliberately use the unfiltered table.
 *
 * The two `*Lastprice*` cells of a security whose `activeToDate` lies in the past are disabled
 * (`isCheckboxDisabled`), and `toggleColumn` skips them — so a per-row assertion counts only enabled cells.
 */

const FLAGS = ['gtNetLastpriceRecv', 'gtNetHistoricalRecv', 'gtNetLastpriceSend', 'gtNetHistoricalSend'] as const;

/** Column order of both tables and of the header strip; used to address the unlabelled checkbox cells. */
const HISTORICAL_SEND_INDEX = FLAGS.indexOf('gtNetHistoricalSend');

const RX = {
  securitiesTitle: /^(Exchange security price data|Austausch Preisdaten Wertpapiere|GT_NET_EXCHANGE_SECURITIES)$/,
  currencypairsTitle:
    /^(Exchange currency pair price data|Austausch Preisdaten Währungspaare|GT_NET_EXCHANGE_CURRENCYPAIRS)$/,
  securitiesTab: /^(Security|Wertpapier|SECURITY)$/,
  currencypairsTab: /^(Currency pair|Währungspaar|CURRENCYPAIR)$/,
  save: /^(Save|Speichern|SAVE)$/
};

/** A `securitycurrency` row as the exchange endpoints deliver it; only the flags are read here. */
interface ApiExchangeRow {
  idSecuritycurrency: number;
  gtNetLastpriceRecv: boolean;
  gtNetHistoricalRecv: boolean;
  gtNetLastpriceSend: boolean;
  gtNetHistoricalSend: boolean;
}

/** `GTSecuritiyCurrencyExchange`: the rows plus the ids that have supplier details. */
interface ApiExchangeResponse {
  securitiescurrenciesList: ApiExchangeRow[];
}

const SECURITY_PATH = '/api/security/gtnetexchange';
const CURRENCYPAIR_PATH = '/api/currencypair/gtnetexchange';

async function authHeaders(page: Page): Promise<{ [key: string]: string }> {
  const token = await page.evaluate(() => sessionStorage.getItem('jwt'));
  expect(token, 'JWT in sessionStorage after login').toBeTruthy();
  return { 'x-auth-token': token! };
}

async function readSecurities(page: Page): Promise<ApiExchangeRow[]> {
  // activeOnly=false, because the reset must reach a security whose activeToDate has passed as well.
  const response = await page.request.get(`${SECURITY_PATH}?activeOnly=false`, { headers: await authHeaders(page) });
  expect(response.ok(), `loading securities: ${response.status()} ${await response.text()}`).toBeTruthy();
  return ((await response.json()) as ApiExchangeResponse).securitiescurrenciesList;
}

async function readCurrencypairs(page: Page): Promise<ApiExchangeRow[]> {
  const response = await page.request.get(CURRENCYPAIR_PATH, { headers: await authHeaders(page) });
  expect(response.ok(), `loading currency pairs: ${response.status()} ${await response.text()}`).toBeTruthy();
  return ((await response.json()) as ApiExchangeResponse).securitiescurrenciesList;
}

/**
 * Writes rows back through the very endpoint the Save button posts to. The whole entity goes back, not a
 * patch: the handler resolves each row by `idSecuritycurrency` and copies exactly the four flags onto the
 * persisted entity, so sending what the GET returned keeps the payload a valid `Security` / `Currencypair`.
 */
async function saveBatch(page: Page, path: string, rows: ApiExchangeRow[]): Promise<void> {
  if (rows.length === 0) {
    return;
  }
  const response = await page.request.post(`${path}/batch`, { headers: await authHeaders(page), data: rows });
  expect(response.ok(), `batch save ${path}: ${response.status()} ${await response.text()}`).toBeTruthy();
}

/**
 * Clears all four flags on every security and currency pair, so the spec starts from a known state however the
 * previous run ended. Only rows that actually carry a flag are written back.
 */
async function resetAllFlags(page: Page): Promise<void> {
  const cleared = (rows: ApiExchangeRow[]) =>
    rows
      .filter((row) => FLAGS.some((flag) => row[flag]))
      .map((row) => ({ ...row, ...Object.fromEntries(FLAGS.map((flag) => [flag, false])) }) as ApiExchangeRow);

  await saveBatch(page, SECURITY_PATH, cleared(await readSecurities(page)));
  await saveBatch(page, CURRENCYPAIR_PATH, cleared(await readCurrencypairs(page)));
}

/**
 * Opens one of the two exchange tabs.
 *
 * The tab is clicked rather than deep-linked: `gtnet-exchange-tabmenu` redirects to its default child, so
 * navigating straight to `/mainview/gtnetexchange/currencypairs` silently lands on the securities table again.
 */
async function openExchangeTable(page: Page, tab: 'securities' | 'currencypairs'): Promise<Locator> {
  const title = tab === 'securities' ? RX.securitiesTitle : RX.currencypairsTitle;
  await page.goto('/mainview/gtnetexchange');
  const tabLabel = tab === 'securities' ? RX.securitiesTab : RX.currencypairsTab;
  await page.getByRole('tab', { name: tabLabel }).click({ timeout: 15_000 });
  const container = page.locator('.data-container-full').first();
  await container.waitFor({ state: 'visible', timeout: 15_000 });
  await expect(container.getByRole('heading', { name: title })).toBeVisible({ timeout: 15_000 });
  await expect(container.locator('tbody tr').first()).toBeVisible({ timeout: 15_000 });
  return container;
}

/**
 * The Save button of the caption toolbar, located by label rather than by accessible name.
 *
 * It is a hand-written `<p-button>` projecting `<i class="pi pi-save" pButtonIcon>`, and Chromium folds that
 * icon's `::before` glyph into the accessible name — so `getByRole('button', { name: /^Speichern$/ })` matches
 * nothing. `hasText` compares `textContent`, which the pseudo-element does not reach.
 */
function saveButton(container: Locator): Locator {
  return container.locator('p-button button', { hasText: RX.save });
}

/** The n-th checkbox cell of a row. The cells carry no id, so position is the only handle. */
function flagCheckbox(row: Locator, index: number): Locator {
  return row.locator('td p-checkbox').nth(index);
}

test.describe.serial('GTNet exchange flags', () => {
  test('clears every flag so the run starts from a known state', async ({ page }) => {
    await loginAsFixtureUser(page, 'admin');
    await resetAllFlags(page);

    for (const rows of [await readSecurities(page), await readCurrencypairs(page)]) {
      for (const row of rows) {
        for (const flag of FLAGS) {
          expect(row[flag], `${flag} of ${row.idSecuritycurrency} after the reset`).toBe(false);
        }
      }
    }
  });

  test('sets a flag on a single security and saves it', async ({ page }) => {
    await loginAsFixtureUser(page, 'admin');
    const container = await openExchangeTable(page, 'securities');

    const firstRow = container.locator('tbody tr').first();
    // gtNetHistoricalSend is never disabled — unlike the two intraday cells, which isCheckboxDisabled() greys
    // out for a security whose activeToDate has passed.
    await flagCheckbox(firstRow, HISTORICAL_SEND_INDEX).click();

    const savePromise = page.waitForResponse(
      (response) =>
        response.url().includes('/api/security/gtnetexchange/batch') && response.request().method() === 'POST'
    );
    await saveButton(container).click();
    const saveResponse = await savePromise;
    expect(saveResponse.ok(), `${saveResponse.status()} ${await saveResponse.text()}`).toBeTruthy();

    const changed = (await readSecurities(page)).filter((row) => row.gtNetHistoricalSend);
    expect(changed, 'exactly the one security whose flag was clicked').toHaveLength(1);
  });

  test('the header strip toggles the whole column and the batch save persists it', async ({ page }) => {
    await loginAsFixtureUser(page, 'admin');
    const container = await openExchangeTable(page, 'currencypairs');

    const before = await readCurrencypairs(page);
    expect(before.length, 'currency pairs to toggle — 045-create-currencypair has to have run').toBeGreaterThan(0);

    // The strip is a sibling of the table: four items in FLAGS order, each a label plus one p-checkbox.
    await page
      .locator('gtnet-exchange-checkboxes .gtnet-checkbox-item')
      .nth(HISTORICAL_SEND_INDEX)
      .locator('p-checkbox')
      .click();

    const savePromise = page.waitForResponse(
      (response) =>
        response.url().includes('/api/currencypair/gtnetexchange/batch') && response.request().method() === 'POST'
    );
    await saveButton(container).click();
    const saveResponse = await savePromise;
    expect(saveResponse.ok(), `${saveResponse.status()} ${await saveResponse.text()}`).toBeTruthy();

    const after = await readCurrencypairs(page);
    expect(after.filter((row) => row.gtNetHistoricalSend)).toHaveLength(before.length);
    // The other three columns must be untouched: toggleColumn writes one field only.
    for (const row of after) {
      expect(row.gtNetLastpriceRecv).toBe(false);
      expect(row.gtNetHistoricalRecv).toBe(false);
      expect(row.gtNetLastpriceSend).toBe(false);
    }
  });

  test('restores the cleared state for the specs that follow', async ({ page }) => {
    await loginAsFixtureUser(page, 'admin');
    await resetAllFlags(page);

    const stillSet = [...(await readSecurities(page)), ...(await readCurrencypairs(page))].filter((row) =>
      FLAGS.some((flag) => row[flag])
    );
    expect(stillSet, 'no exchange flag may survive this spec').toHaveLength(0);
  });
});
