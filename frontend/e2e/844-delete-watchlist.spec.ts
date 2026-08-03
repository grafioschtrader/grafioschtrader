import {expect, test} from '@playwright/test';
import {loginAsCsvUser} from './helpers';
import {loadE2EWatchlists} from './watchlist.helpers';

const WATCHLISTS_TO_DELETE = loadE2EWatchlists().filter(watchlist => watchlist.delete);

for (const watchlist of WATCHLISTS_TO_DELETE) {
  test(`deletes watchlist ${watchlist.name} of '${watchlist.loginNickname}'`, async ({page}) => {
    await loginAsCsvUser(page, watchlist.loginNickname);

    const node = page.getByRole('treeitem', {name: watchlist.name, exact: true}).first();
    const exists = await node.waitFor({state: 'visible', timeout: 5_000})
      .then(() => true)
      .catch(() => false);
    if (!exists) {
      return;
    }
    await node.click({button: 'right'});

    const menu = page.locator('[role="menu"]:visible');
    await menu.waitFor({state: 'visible', timeout: 5_000});
    const deleteItem = menu.getByRole('menuitem', {name: /^(Delete|Löschen)$/}).first();
    await expect(deleteItem, `${watchlist.name} must be empty before it can be deleted`).toBeEnabled();
    await deleteItem.click();

    const confirmDialog = page.getByRole('alertdialog', {name: /^(Confirmation|Bestätigung)$/});
    await confirmDialog.waitFor({state: 'visible', timeout: 10_000});
    await confirmDialog.getByRole('button', {name: /^(Yes|Ja)$/}).click();
    await node.waitFor({state: 'hidden', timeout: 10_000});
  });
}
