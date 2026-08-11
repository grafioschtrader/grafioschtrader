import {test} from '@playwright/test';
import {loginAsFixtureUser} from './helpers';
import {
  ensureWatchlist,
  ensureWatchlistSecuritiesByNameSearch,
  loadE2EWatchlists,
} from './watchlist.helpers';

/**
 * The derived Forex securities are created by 065-create-derived-security.spec.ts. This spec therefore runs
 * immediately afterwards and adds them to limit2's performance watchlist through the existing-instrument dialog.
 */
const WATCHLISTS = loadE2EWatchlists().filter(watchlist => watchlist.securitySearchName);

for (const watchlist of WATCHLISTS) {
  test(`adds ${watchlist.securitySearchName} securities to ${watchlist.name} of '${watchlist.loginNickname}'`,
    async ({page}) => {
      await loginAsFixtureUser(page, watchlist.loginNickname);
      await ensureWatchlist(page, watchlist);
      await ensureWatchlistSecuritiesByNameSearch(page, watchlist);
    });
}
