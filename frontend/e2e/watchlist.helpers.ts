import {expect, Locator, Page} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const FIXTURE_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/watchlists.json');

export interface WatchlistSecurityFixture {
  /** Stable lookup key used by the backend integration fixture. */
  isin?: string;
  /** Exact display name used by Playwright for securities without an ISIN. */
  name?: string;
  currency: string;
}

export interface WatchlistCurrencyPairFixture {
  fromCurrency: string;
  toCurrency: string;
}

export interface WatchlistFixture {
  /** users.json nickname of the tenant that owns the watchlist. */
  loginNickname: string;
  name: string;
  /** true when this watchlist becomes Tenant.id_watchlist_performance. */
  main: boolean;
  /** true when 844-delete-watchlist.spec.ts removes the watchlist after the application tests. */
  delete: boolean;
  /** Optional value entered into the add-existing-instrument dialog's Name field. */
  securitySearchName?: string;
  securities: WatchlistSecurityFixture[];
  currencyPairs: WatchlistCurrencyPairFixture[];
  /** 'e' for Playwright, 'i' for the backend REST integration test. */
  e2e: string;
}

interface WatchlistFixtureFile {
  watchlists: WatchlistFixture[];
}

/** Loads the Playwright-owned watchlists from the shared JSON fixture. */
export function loadE2EWatchlists(): WatchlistFixture[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping the watchlist e2e specs.`);
    return [];
  }
  const fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8')) as WatchlistFixtureFile;
  return fixture.watchlists.filter(row => row.e2e === 'e');
}

/** Creates a watchlist through the navigation tree, or accepts the existing exact-name node. */
export async function ensureWatchlist(page: Page, watchlist: WatchlistFixture): Promise<void> {
  const watchlistNode = page.getByRole('treeitem', {name: watchlist.name, exact: true}).first();
  if (await watchlistNode.count() > 0) {
    await expect(watchlistNode).toBeVisible();
  } else {
    const watchlistRoot = page.locator('.p-tree-node-content', {
      hasText: /Watchlist\s*-\s*(Correlation\s*matrix|Korrelationsmatrix)/i
    }).first();
    await watchlistRoot.waitFor({state: 'visible', timeout: 15_000});
    await watchlistRoot.click({button: 'right'});

    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 5_000});
    await menu.getByText(/(Create|Erstellen)\s*Watchlist/i).first().click();

    const dialog = page.locator('.p-dialog');
    await dialog.waitFor({state: 'visible', timeout: 10_000});
    const nameInput = dialog.locator('#name');
    await nameInput.click();
    await nameInput.fill(watchlist.name);
    await nameInput.dispatchEvent('input');
    await nameInput.blur();
    await dialog.locator('button[type="submit"]').click();
    await dialog.waitFor({state: 'hidden', timeout: 10_000});

    await expect(watchlistNode).toBeVisible({timeout: 10_000});
  }

  if (watchlist.main) {
    await ensureMainWatchlist(page, watchlistNode);
  }
}

/** Selects the watchlist for tenant performance calculations unless it is already selected. */
async function ensureMainWatchlist(page: Page, watchlistNode: Locator): Promise<void> {
  const mainIcon = watchlistNode.locator('.pi-chart-line');
  if (await mainIcon.count() > 0) {
    await expect(mainIcon).toBeVisible();
    return;
  }

  await watchlistNode.click({button: 'right'});
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  await menu.getByRole('menuitem', {name: /^(For performance|Für Performance)/i}).first().click();
  await expect(mainIcon).toBeVisible({timeout: 10_000});
}

/**
 * Adds the fixture's name-keyed securities through the watchlist search dialog. Existing rows are retained, so the
 * helper can recover from both a completed run and a run that added only part of the requested result set.
 */
export async function ensureWatchlistSecuritiesByNameSearch(page: Page,
    watchlist: WatchlistFixture): Promise<void> {
  const expectedSecurities = watchlist.securities.filter(security => security.name);
  if (!watchlist.securitySearchName || expectedSecurities.length === 0) {
    return;
  }

  const watchlistNode = page.getByRole('treeitem', {name: watchlist.name, exact: true}).first();
  await watchlistNode.waitFor({state: 'visible', timeout: 15_000});
  await watchlistNode.click();

  const container = page.locator('.data-container').first();
  await container.waitFor({state: 'visible', timeout: 15_000});
  const missing: WatchlistSecurityFixture[] = [];
  for (const security of expectedSecurities) {
    if (!await watchlistSecurityRow(container, security).isVisible().catch(() => false)) {
      missing.push(security);
    }
  }
  if (missing.length === 0) {
    return;
  }

  await container.click();
  await page.waitForTimeout(300);
  await container.click({button: 'right'});
  const menu = page.locator('[role="menu"]:visible');
  await menu.waitFor({state: 'visible', timeout: 5_000});
  await menu.getByText(/(Bestehendes\s+Instrument\s+hinzuf.gen|Add\s+existing\s+instrument)/i).first().click();

  const addDialog = page.locator('watchlist-add-instrument');
  await addDialog.locator('.p-dialog').waitFor({state: 'visible', timeout: 10_000});
  // The final stock-exchange option is loaded immediately before the form reset. Waiting for it prevents that reset
  // from wiping a search value entered too early.
  await expect(addDialog.locator('select#idStockexchange option')).not.toHaveCount(0, {timeout: 15_000});

  const name = addDialog.getByRole('textbox', {name: /^Name$/});
  await expect(name).toBeVisible();
  await name.fill(watchlist.securitySearchName);
  await name.dispatchEvent('input');
  await addDialog.locator('button[type="submit"]').click();

  const resultRows = addDialog.locator('add-instrument-table tbody tr');
  await expect(resultRows).toHaveCount(missing.length, {timeout: 15_000});
  for (const security of missing) {
    const resultRow = resultRows.filter({hasText: security.name!}).filter({hasText: security.currency}).first();
    await expect(resultRow, `search result ${security.name}/${security.currency}`).toBeVisible();
    await resultRow.locator('p-tablecheckbox').click();
  }

  const addButton = addDialog.getByRole('button', {name: /^(Hinzuf.gen|Add)$/});
  await expect(addButton).toBeEnabled({timeout: 5_000});
  await addButton.click();
  await expect(addButton).toBeDisabled({timeout: 15_000});
  await addDialog.getByRole('button', {name: /^(Beenden|Close)$/}).click();
  await addDialog.locator('.p-dialog').waitFor({state: 'hidden', timeout: 10_000});

  for (const security of expectedSecurities) {
    await expect(watchlistSecurityRow(container, security),
      `watchlist row ${security.name}/${security.currency}`).toBeVisible({timeout: 15_000});
  }
}

function watchlistSecurityRow(container: Locator, security: WatchlistSecurityFixture): Locator {
  return container.locator('p-table tbody tr')
    .filter({hasText: security.name!})
    .filter({hasText: security.currency})
    .first();
}
