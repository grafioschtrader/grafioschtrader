import { expect, test } from '@playwright/test';
import { loginAsFixtureUser } from './helpers';
import { loadPortfolios, portfolioNode, readFixturePortfolio } from './portfolio.helpers';

/** Reads retained portfolio fixtures only; both successful and interrupted runs leave the database unchanged. */
test('switching portfolios aborts the previous summary and keeps the newer account rows', async ({ page }) => {
  await loginAsFixtureUser(page, 'alledit');
  const fixtures = loadPortfolios().filter((p) => p.loginNickname === 'alledit' && !p.delete);
  expect(fixtures.length).toBeGreaterThanOrEqual(2);
  const first = (await readFixturePortfolio(page, fixtures[0]))!;
  const second = (await readFixturePortfolio(page, fixtures[1]))!;
  expect(first).toBeTruthy();
  expect(second).toBeTruthy();
  const oldAccount = first.cashaccountList.find(
    (row) => !second.cashaccountList.some((other) => other.name === row.name)
  )!;
  const newAccount = second.cashaccountList.find(
    (row) => !first.cashaccountList.some((other) => other.name === row.name)
  )!;
  expect(oldAccount).toBeTruthy();
  expect(newAccount).toBeTruthy();

  let release!: () => void;
  let arrived!: () => void;
  const gate = new Promise<void>((resolve) => (release = resolve));
  const pending = new Promise<void>((resolve) => (arrived = resolve));
  const firstPath = `/api/cashaccount/${first.idPortfolio}/portfoliocashaccountsummary`;
  await page.route(`**${firstPath}?*`, async (route) => {
    const response = await route.fetch();
    expect(response.ok()).toBeTruthy();
    arrived();
    await gate;
    await route.fulfill({ response }).catch(() => undefined);
  });
  try {
    await portfolioNode(page, fixtures[0]).click();
    await pending;
    const cancelled = page.waitForEvent('requestfailed', {
      predicate: (request) => new URL(request.url()).pathname === firstPath
    });
    await portfolioNode(page, fixtures[1]).click();
    await cancelled;
    const table = page.locator('p-table').first();
    await expect(table.locator('tbody tr', { hasText: newAccount.name })).toHaveCount(1);
    release();
    await expect(table.locator('h4')).toContainText(second.name);
    await expect(table.locator('tbody tr', { hasText: oldAccount.name })).toHaveCount(0);
  } finally {
    release();
  }
});
