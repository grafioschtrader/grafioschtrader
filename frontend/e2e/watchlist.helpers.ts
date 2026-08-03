import {expect, Locator, Page} from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import {parseCsvRow} from './helpers';

const FIXTURE_PATH = path.resolve(__dirname,
  '../../backend/grafioschtrader-server/src/test/resources/testdata/watchlists.csv');

export interface WatchlistFixture {
  /** users.csv nickname of the tenant that owns the watchlist. */
  loginNickname: string;
  name: string;
  /** true when this watchlist becomes Tenant.id_watchlist_performance. */
  main: boolean;
  /** true when 844-delete-watchlist.spec.ts removes the watchlist after the application tests. */
  delete: boolean;
  /** 'e' for Playwright, 'i' for the future backend integration test. */
  e2e: string;
}

/** Loads the Playwright-owned watchlists from the shared pipe-delimited fixture. */
export function loadE2EWatchlists(): WatchlistFixture[] {
  if (!fs.existsSync(FIXTURE_PATH)) {
    console.warn(`Fixture ${FIXTURE_PATH} not found - skipping the watchlist e2e specs.`);
    return [];
  }
  return fs.readFileSync(FIXTURE_PATH, 'utf-8')
    .split(/\r?\n/)
    .filter(line => line.trim().length > 0)
    .slice(1)
    .map(line => {
      const [loginNickname, name, mainValue, deleteValue, e2e] = parseCsvRow(line);
      return {loginNickname, name, main: mainValue === 'true', delete: deleteValue === 'true', e2e};
    })
    .filter(row => row.e2e === 'e');
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
