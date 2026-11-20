import { test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import {
  ensureWatchlist,
  ensureWatchlistSecuritiesBySearch,
  loadPlaywrightInstrumentWatchlists
} from './watchlist.helpers';

/**
 * Spanish securities are created by 100 and derived Forex securities by 155. This spec runs after both producers and
 * adds every Playwright-owned instrument to its integration- or Playwright-owned performance watchlist.
 */
const WATCHLISTS = loadPlaywrightInstrumentWatchlists();

for (const watchlist of WATCHLISTS) {
  test(`adds fixture securities to ${watchlist.name} of '${watchlist.loginNickname}'`, async ({ page }) => {
    await loginAsFixtureUser(page, watchlist.loginNickname);
    await ensureWatchlist(page, watchlist);
    await ensureWatchlistSecuritiesBySearch(page, watchlist);
  });
}
