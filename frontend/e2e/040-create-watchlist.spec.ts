import {test} from '@playwright/test';
import {loginAsFixtureUser} from './helpers';
import {ensureWatchlist, loadE2EWatchlists} from './watchlist.helpers';

for (const watchlist of loadE2EWatchlists()) {
  test(`creates watchlist ${watchlist.name} for '${watchlist.loginNickname}' if missing`, async ({page}) => {
    await loginAsFixtureUser(page, watchlist.loginNickname);
    await ensureWatchlist(page, watchlist);
  });
}
