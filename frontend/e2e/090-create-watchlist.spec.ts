import { test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { ensureWatchlist, ensureWatchlistSecuritiesBySubCategorySearch, loadE2EWatchlists } from './watchlist.helpers';

for (const watchlist of loadE2EWatchlists()) {
  const action = watchlist.securitySearchSubCategoryNLS ? 'creates and populates' : 'creates';
  test(`${action} watchlist ${watchlist.name} for '${watchlist.loginNickname}' if missing`, async ({ page }) => {
    await loginAsFixtureUser(page, watchlist.loginNickname);
    await ensureWatchlist(page, watchlist);
    await ensureWatchlistSecuritiesBySubCategorySearch(page, watchlist);
  });
}
