import {test} from '@playwright/test';
import {loginAsCsvUser} from './helpers';
import {ensureWatchlist, loadE2EWatchlists} from './watchlist.helpers';

for (const watchlist of loadE2EWatchlists()) {
  test(`creates watchlist ${watchlist.name} for '${watchlist.loginNickname}' if missing`, async ({page}) => {
    await loginAsCsvUser(page, watchlist.loginNickname);
    await ensureWatchlist(page, watchlist);
  });
}
