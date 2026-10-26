import { expect, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { scenario } from './cash-security-transaction.helpers';
import {
  loadPortfolios,
  openTreeContextMenu,
  portfolioNode,
  portfolioSubtree,
  readFixturePortfolio,
  RX
} from './portfolio.helpers';

/** Read-only regression on the account used by 122/123: real transaction constraints must reach the rendered table. */
test('reopens a securities account and renders delayed transaction constraints without another user event', async ({
  page
}) => {
  const p = loadPortfolios().find(
    (portfolio) => portfolio.loginNickname === scenario.loginNickname && portfolio.name === scenario.portfolioName
  )!;
  expect(p).toBeTruthy();
  await loginAsFixtureUser(page, p.loginNickname);
  const portfolio = await readFixturePortfolio(page, p);
  const account = portfolio!.securityaccountList.find((row) => row.name === scenario.securityAccountName)!;
  expect(account).toBeTruthy();
  const periodIndex = account.tradingPeriods.findIndex(
    (period) => period.specInvestInstrument === 'DIRECT_INVESTMENT' && period.categoryType === 'EQUITIES'
  );
  expect(periodIndex).toBeGreaterThanOrEqual(0);

  let release!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  let summaries!: { specInvestInstrument: string; categoryType: string; transactionCount: number }[];
  const url = `**/api/securityaccount/${account.idSecuritycashAccount}/transactionsummaries`;
  await page.route(url, async (route) => {
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    summaries = await response.json();
    await gate;
    await route.fulfill({ response });
  });

  try {
    await portfolioNode(page, p).dblclick();
    const subtree = portfolioSubtree(page, p);
    await subtree.locator('.p-tree-node-content').filter({ hasText: RX.securityAccountsFolder }).first().dblclick();
    await openTreeContextMenu(
      page,
      subtree
        .locator('.p-tree-node-content')
        .filter({ hasText: new RegExp(`^\\s*${scenario.securityAccountName}\\s*$`) })
        .first(),
      RX.editSecurityAccount
    );
    const dialog = page.locator('.p-dialog:visible');
    await expect(dialog.locator('#name')).toHaveValue(account.name);
    await expect(dialog.locator('select#tradingPlatformPlan')).toHaveValue(
      String(account.tradingPlatformPlan.idTradingPlatformPlan)
    );
    const rows = dialog.locator('trading-period-table tbody tr');
    await expect(rows).toHaveCount(account.tradingPeriods.length);
    const directInvestment = rows.nth(periodIndex);
    await expect(directInvestment).toContainText(/Direct investment|Direktanlage/);
    const deleteButton = directInvestment.locator('button:has(i.pi-trash)');
    await expect(deleteButton).toBeVisible();
    await expect
      .poll(() =>
        summaries?.some(
          (summary) =>
            summary.specInvestInstrument === 'DIRECT_INVESTMENT' &&
            summary.categoryType === 'EQUITIES' &&
            summary.transactionCount > 0
        )
      )
      .toBe(true);
    release();
    // No click, focus or other template event between releasing the response and observing its effect.
    await expect(deleteButton).toHaveCount(0);
    await dialog.locator('.p-dialog-close-button').click();
    await expect(dialog).toHaveCount(0);
  } finally {
    release();
  }
});
