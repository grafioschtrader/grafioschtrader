import { expect, Locator, Page, Route, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { watchlistTable } from './watchlist.helpers';

/**
 * Change notification of the history quote views: the live table and the archive view of Nestlé render delayed data,
 * publish their translated menus and open and close their dialogs. The live quotes are the real ones of 060; only the
 * archive count is raised, and the archive rows are intercepted, so nothing is written to grafioschtrader_t.
 */
const WATCHLIST_NAME = 'Switzerland';
const SECURITY_RX = /Nestl.+AG/;
const LEGACY_COUNT = 2;
const LEGACY_ROWS = [
  { idHistoryquoteLegacy: 990_001, date: '2001-03-01', transferDate: '2024-05-02', close: 11.5, volume: 100 },
  { idHistoryquoteLegacy: 990_002, date: '2001-03-02', transferDate: '2024-05-02', close: 12.25, volume: 200 }
].map((row) => ({ ...row, open: null, high: null, low: null, createType: 1 }));

const RX = {
  eodTableItem: /(EOD as table|Tagesendkurse als Tabelle)/i,
  showLegacy: new RegExp(
    `(Show archived historical price data|Archivierte historische Kursdaten anzeigen) \\(${LEGACY_COUNT}\\)`
  ),
  showLive: /(Return to current historical price data|Zurück zu den aktuellen historischen Kursdaten)/,
  applySplit: /(Apply split to archived data|Split auf archivierte Daten anwenden)/,
  edit: /^(Edit|Bearbeiten)\b/,
  legacyCaption: /(Archived historical price data|Archivierte historische Kursdaten)/
};

test.use({ viewport: { width: 1600, height: 1200 } });

/** Holds every response of url until released; json receives the real body when fetch is true. */
async function hold(page: Page, url: string | RegExp, json: (body: any) => unknown, fetch: boolean) {
  let release!: () => void, arrived!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const pending = new Promise<void>((resolve) => (arrived = resolve));
  await page.route(url, async (route: Route) => {
    if (route.request().method() !== 'GET') return route.fallback();
    const response = fetch ? await route.fetch() : null;
    const body = response ? await response.json() : null;
    arrived();
    await gate;
    await route.fulfill({ json: json(body) }).catch(() => undefined);
  });
  return { release, arrived: pending };
}

/** Selects target, then opens the context menu on it and returns the visible menu. */
async function contextMenu(page: Page, target: Locator): Promise<Locator> {
  await target.click();
  await target.click({ button: 'right' });
  const menu = page.locator('[role="menu"]:visible').last();
  await expect(menu).toBeVisible();
  return menu;
}

test.beforeEach(async ({ page }) => {
  const info = await page.request.get('/api/gtinfo');
  expect(await info.json()).toMatchObject({ databaseName: 'grafioschtrader_t', activeProfile: 'e2e' });
  await loginAsFixtureUser(page, 'alledit');
  await page.getByRole('treeitem', { name: WATCHLIST_NAME, exact: true }).first().click();
  await watchlistTable(page).waitFor({ state: 'visible' });
});

test('live and archive views publish delayed rows, translated menus and their dialogs', async ({ page }) => {
  const quotes = await hold(
    page,
    /\/api\/historyquote\/securitycurrency\/\d+(\?|$)/,
    (body) => ({ ...body, legacyCount: LEGACY_COUNT }),
    true
  );
  const legacy = await hold(page, '**/api/historyquotelegacy/security/*', () => LEGACY_ROWS, false);

  const security = page.locator('td').filter({ hasText: SECURITY_RX }).first();
  await (await contextMenu(page, security)).getByText(RX.eodTableItem).first().click();
  await quotes.arrived;
  const live = page.locator('historyquote-table');
  await expect(live).toBeVisible();
  await expect(live.locator('tbody tr')).toHaveCount(0);
  quotes.release();
  await expect(live.locator('tbody tr').first()).toBeVisible();
  await expect(live.locator('h4')).toContainText(SECURITY_RX);

  await (await contextMenu(page, live.locator('h4'))).getByText(RX.showLegacy).click();
  await legacy.arrived;
  const archive = page.locator('historyquote-legacy');
  await expect(archive.locator('h4')).toHaveText(RX.legacyCaption);
  await expect(archive.locator('tbody tr')).toHaveCount(0);
  legacy.release();
  await expect(archive.locator('tbody tr')).toHaveCount(LEGACY_ROWS.length);

  await (await contextMenu(page, archive.locator('h4'))).getByText(RX.applySplit).click();
  const splitDialog = page.locator('historyquote-legacy-apply-split-dialog .p-dialog');
  await expect(splitDialog).toBeVisible();
  await splitDialog.locator('.p-dialog-close-button').click();
  await expect(page.locator('historyquote-legacy-apply-split-dialog')).toHaveCount(0);

  const firstRow = archive.locator('tbody tr').first();
  await (await contextMenu(page, firstRow)).getByText(RX.edit).first().click();
  const editDialog = page.locator('historyquote-legacy-edit .p-dialog');
  await expect(editDialog).toBeVisible();
  await expect(editDialog.locator('#close input, input#close').first()).toHaveValue(/12[.,]25/);
  await editDialog.locator('.p-dialog-close-button').click();
  await expect(page.locator('historyquote-legacy-edit')).toHaveCount(0);

  await (await contextMenu(page, archive.locator('h4'))).getByText(RX.showLive).click();
  await expect(page.locator('historyquote-table tbody tr').first()).toBeVisible();
});

test('a failed combined quote read reports one error and reopening recovers', async ({ page }) => {
  test.setTimeout(30_000);
  await page.route(
    /\/api\/historyquote\/securitycurrency\/\d+(\?|$)/,
    (route) => route.fulfill({ status: 503, contentType: 'text/plain', body: 'E2E quote read failed' }),
    { times: 1 }
  );
  const security = page.locator('td').filter({ hasText: SECURITY_RX }).first();
  await (await contextMenu(page, security)).getByText(RX.eodTableItem).first().click();
  const live = page.locator('historyquote-table');
  await expect(live.locator('tbody tr')).toHaveCount(0);
  await expect(live.locator('.p-datatable-loading-overlay')).toHaveCount(0);
  await expect(page.locator('.p-toast-message', { hasText: 'E2E quote read failed' })).toHaveCount(1);
  await expect(page.locator('.p-toast-message')).toHaveCount(1);

  // The history table is an auxiliary route: selecting the same primary watchlist does not close it.
  // Reopen the deep link so a new resource reads the now-successful endpoint.
  await page.reload();
  await expect(page.locator('historyquote-table tbody tr').first()).toBeVisible();
});
