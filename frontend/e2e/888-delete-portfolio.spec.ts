import {test} from '@playwright/test';
import {loginAsCsvUser} from './helpers';
import {
  appearsWithin,
  confirmDelete,
  loadPortfolios,
  openTreeContextMenu,
  portfolioNode,
  portfolioSubtree,
  RX,
} from './portfolio.helpers';

/**
 * Teardown for 015-create-portfolio.spec.ts. Which portfolios are removed is defined by the
 * "delete" flag in testdata/portfolios.json: a portfolio with delete=true is torn down completely
 * (its cash accounts, its securities accounts, then the portfolio itself), one with delete=false
 * generates no test at all and stays in the database.
 *
 * The portfolios are processed in reverse fixture order so the last one created is removed first.
 * Every delete step tolerates an already missing row or node, so a partially failed 015 does not
 * turn this spec red as well.
 */
const PORTFOLIOS = loadPortfolios().filter(p => p.delete).reverse();

for (const p of PORTFOLIOS) {
  test.describe.serial(`Delete portfolio ${p.name} of '${p.loginNickname}' (cleanup)`, () => {

    test(`deletes ${p.cashAccounts.length} cash accounts of ${p.name}`, async ({page}) => {
      await loginAsCsvUser(page, p.loginNickname);

      const node = portfolioNode(page, p);
      if (!await appearsWithin(node)) {
        return;
      }
      await node.click();
      await page.waitForTimeout(3000);

      for (const account of p.cashAccounts) {
        // Only selectable rows, so the header row cannot match the account name.
        const row = page.locator('tr[data-p-selectable-row]', {hasText: account.name});
        if (!await appearsWithin(row.first(), 10_000)) {
          continue;
        }
        await row.first().click();
        await page.waitForTimeout(500);
        await row.first().click({button: 'right'});

        const menuList = page.locator('[role="menu"]:visible');
        await menuList.waitFor({state: 'visible', timeout: 5_000});
        await menuList.getByText(RX.deleteCashAccount).first().click();

        await confirmDelete(page);
        await page.waitForTimeout(2000);
      }
    });

    for (const sa of p.securityAccounts) {
      test(`deletes the securities account ${sa.name} of ${p.name}`, async ({page}) => {
        await loginAsCsvUser(page, p.loginNickname);

        const node = portfolioNode(page, p);
        if (!await appearsWithin(node)) {
          return;
        }
        await node.dblclick();

        const subtree = portfolioSubtree(page, p);
        const folder = subtree.locator('.p-tree-node-content')
          .filter({hasText: RX.securityAccountsFolder}).first();
        await folder.waitFor({state: 'visible', timeout: 10_000});
        await folder.dblclick();

        const account = subtree.locator('.p-tree-node-content')
          .filter({hasText: exactLabel(sa.name)}).first();
        if (!await appearsWithin(account, 10_000)) {
          return;
        }
        await openTreeContextMenu(page, account, RX.deleteNode);
        await confirmDelete(page);
        await account.waitFor({state: 'hidden', timeout: 10_000});
      });
    }

    test(`deletes the portfolio ${p.name}`, async ({page}) => {
      await loginAsCsvUser(page, p.loginNickname);

      const node = portfolioNode(page, p);
      if (!await appearsWithin(node)) {
        return;
      }
      await openTreeContextMenu(page, node, RX.deleteNode);
      await confirmDelete(page);
      await node.waitFor({state: 'hidden', timeout: 10_000});
    });
  });
}

/**
 * Anchored matcher for a securities account leaf. The names are short ('ST', 'CT'), so a substring
 * match would also hit the portfolio node or a sibling account.
 */
function exactLabel(name: string): RegExp {
  return new RegExp(`^\\s*${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\s*$`);
}
