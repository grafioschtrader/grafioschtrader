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
  /** Optional localized value selected in the add-existing-instrument dialog's subcategory field. */
  securitySearchSubCategoryNLS?: string;
  securities: WatchlistSecurityFixture[];
  currencyPairs: WatchlistCurrencyPairFixture[];
  /** Optional ownership override when the watchlist and its instruments are produced by different test layers. */
  instrumentE2E?: string;
  /** 'e' for Playwright, 'i' for the backend REST integration test. */
  e2e: string;
}

interface WatchlistFixtureFile {
  watchlists: WatchlistFixture[];
}

/** Loads the Playwright-owned watchlists from the shared JSON fixture. */
export function loadE2EWatchlists(): WatchlistFixture[] {
  return loadWatchlistFixture().watchlists.filter(row => row.e2e === 'e');
}

/** Loads watchlists whose instruments, but not necessarily the watchlist itself, belong to Playwright. */
export function loadPlaywrightInstrumentWatchlists(): WatchlistFixture[] {
  return loadWatchlistFixture().watchlists.filter(row => (row.instrumentE2E ?? row.e2e) === 'e'
    && row.securities.length > 0);
}

function loadWatchlistFixture(): WatchlistFixtureFile {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping the watchlist e2e specs.`);
    return {watchlists: []};
  }
  return JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf-8')) as WatchlistFixtureFile;
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
 * Adds every security returned for the fixture's localized subcategory. The watchlist search excludes instruments
 * that are already present, so completed and partially completed runs are both safe to repeat.
 */
export async function ensureWatchlistSecuritiesBySubCategorySearch(page: Page,
    watchlist: WatchlistFixture): Promise<void> {
  const subCategoryNLS = watchlist.securitySearchSubCategoryNLS;
  if (!subCategoryNLS) {
    return;
  }

  const watchlistNode = page.getByRole('treeitem', {name: watchlist.name, exact: true}).first();
  await watchlistNode.waitFor({state: 'visible', timeout: 15_000});
  await watchlistNode.click();

  const container = page.locator('.data-container').first();
  await container.waitFor({state: 'visible', timeout: 15_000});
  const addDialog = await openAddInstrumentDialog(page, container);
  const subCategorySelect = addDialog.locator('select#subCategoryNLS');
  await expect(subCategorySelect.locator('option', {hasText: exactText(subCategoryNLS)}))
    .toHaveCount(1, {timeout: 15_000});
  await subCategorySelect.selectOption(subCategoryNLS);

  const searchResponsePromise = page.waitForResponse(response => response.request().method() === 'GET'
    && /\/api\/watchlist\/\d+\/search(?:\?|$)/.test(response.url()));
  await addDialog.locator('button[type="submit"]').click();
  const searchResponse = await searchResponsePromise;
  expect(searchResponse.ok(), `subcategory search ${subCategoryNLS}`).toBeTruthy();
  const searchResult = await searchResponse.json() as {securityList: unknown[]; currencypairList: unknown[]};
  expect(searchResult.currencypairList, `currency-pair results for ${subCategoryNLS}`).toHaveLength(0);

  const resultRows = addDialog.locator('add-instrument-table tbody tr');
  if (searchResult.securityList.length > 0) {
    await expect(resultRows.first()).toBeVisible({timeout: 15_000});
    await addDialog.locator('add-instrument-table p-tableheadercheckbox').click();
    const addButton = addDialog.getByRole('button', {name: /^(Hinzuf.gen|Add)$/});
    await expect(addButton).toBeEnabled({timeout: 5_000});
    await addButton.click();
    await expect(addButton).toBeDisabled({timeout: 15_000});
    await expect(resultRows).toHaveCount(0, {timeout: 15_000});
  } else {
    await expect(resultRows).toHaveCount(0, {timeout: 15_000});
  }

  await closeAddInstrumentDialog(addDialog);
  const watchlistRows = container.locator('p-table tbody tr');
  await expect(watchlistRows.first(), `${watchlist.name} contains ${subCategoryNLS} securities`)
    .toBeVisible({timeout: 15_000});
  for (const row of await watchlistRows.all()) {
    await expect(row).toContainText(subCategoryNLS);
  }
}

/**
 * Adds the fixture's Playwright-owned securities through the watchlist search dialog. ISIN-keyed securities are
 * searched individually, while name-keyed securities sharing securitySearchName are added from one result set.
 * Existing rows are retained, so a completed or partially completed run can be repeated safely.
 */
export async function ensureWatchlistSecuritiesBySearch(page: Page,
    watchlist: WatchlistFixture): Promise<void> {
  const expectedSecurities = watchlist.securities;
  if (expectedSecurities.length === 0) {
    return;
  }

  const watchlistNode = page.getByRole('treeitem', {name: watchlist.name, exact: true}).first();
  await watchlistNode.waitFor({state: 'visible', timeout: 15_000});
  await watchlistNode.click();

  const container = page.locator('.data-container').first();
  await container.waitFor({state: 'visible', timeout: 15_000});
  const missing: WatchlistSecurityFixture[] = [];
  for (const security of expectedSecurities) {
    if (!await watchlistSecurityRow(page, container, security).isVisible().catch(() => false)) {
      missing.push(security);
    }
  }
  if (missing.length === 0) {
    return;
  }

  for (const security of missing.filter(candidate => candidate.isin)) {
    await addSecuritiesThroughSearch(page, container, [security], 'isin', security.isin!);
  }

  const nameSecurities = missing.filter(candidate => !candidate.isin);
  if (nameSecurities.length > 0) {
    if (!watchlist.securitySearchName) {
      throw new Error(`Watchlist ${watchlist.name} has name-keyed securities without securitySearchName`);
    }
    await addSecuritiesThroughSearch(page, container, nameSecurities, 'name', watchlist.securitySearchName);
  }

  for (const security of expectedSecurities) {
    await expect(watchlistSecurityRow(page, container, security),
      `watchlist row ${security.name ?? security.isin}/${security.currency}`).toBeVisible({timeout: 15_000});
  }
}

async function addSecuritiesThroughSearch(page: Page, container: Locator,
    securities: WatchlistSecurityFixture[], field: 'isin' | 'name', searchValue: string): Promise<void> {
  const addDialog = await openAddInstrumentDialog(page, container);

  const searchInput = field === 'isin' ? addDialog.locator('#isin')
    : addDialog.getByRole('textbox', {name: /^Name$/});
  await expect(searchInput).toBeVisible();
  await searchInput.fill(searchValue);
  await searchInput.dispatchEvent('input');
  await addDialog.locator('button[type="submit"]').click();

  const resultRows = addDialog.locator('add-instrument-table tbody tr');
  for (const security of securities) {
    const key = security.isin ?? security.name!;
    const resultRow = resultRows
      .filter({has: page.locator('td').filter({hasText: exactText(key)})})
      .filter({has: page.locator('td').filter({hasText: exactText(security.currency)})});
    await expect(resultRow, `exact search result ${key}/${security.currency}`)
      .toHaveCount(1, {timeout: 15_000});
    await resultRow.locator('p-tablecheckbox').click();
  }

  const addButton = addDialog.getByRole('button', {name: /^(Hinzuf.gen|Add)$/});
  await expect(addButton).toBeEnabled({timeout: 5_000});
  await addButton.click();
  await expect(addButton).toBeDisabled({timeout: 15_000});
  await closeAddInstrumentDialog(addDialog);
}

async function openAddInstrumentDialog(page: Page, container: Locator): Promise<Locator> {
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
  return addDialog;
}

async function closeAddInstrumentDialog(addDialog: Locator): Promise<void> {
  await addDialog.getByRole('button', {name: /^(Beenden|Close)$/}).click();
  await addDialog.locator('.p-dialog').waitFor({state: 'hidden', timeout: 10_000});
}

function watchlistSecurityRow(page: Page, container: Locator, security: WatchlistSecurityFixture): Locator {
  const key = security.name ?? security.isin!;
  return container.locator('p-table tbody tr')
    .filter({has: page.locator('td').filter({hasText: exactText(key)})})
    .filter({has: page.locator('td').filter({hasText: exactText(security.currency)})})
    .first();
}

function exactText(value: string): RegExp {
  return new RegExp(`^\\s*${value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}
