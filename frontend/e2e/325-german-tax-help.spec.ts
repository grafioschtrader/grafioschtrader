import { expect, Page, test } from '@playwright/test';

import { loginAsFixtureUser } from './helpers';
import { e2eHeaders, GermanTaxReport, openRealizedGainsTab } from './realized-gains.helpers';

/**
 * The area GERMAN_TAX_HELP below the realized gains. It belongs to a client that reports in euro and has Germany as its
 * country: `limit1` is that client, `limit2` (CHF, Switzerland) and `user` (USD, United States) are not.
 *
 * The positive spec changes the withholding of the portfolios of `limit1` through REST and restores every value in
 * `finally`, so a failed run leaves the portfolios as they were. A portfolio of an existing database is not assigned,
 * which is the state the first block checks: the report asks for the assignment, lists the portfolio as not assigned
 * and every sum a gap affects shows no amount. After the assignment the request disappears. The spec reads the report of
 * the backend for the same year and compares the number of sums without amount with the screen.
 */
const TAX_YEAR = Math.max(new Date().getFullYear() - 1, 2018);

interface PortfolioListEntry {
  idPortfolio: number;
  name: string;
}

type PortfolioJson = PortfolioListEntry & {
  taxWithholding?: string | null;
  securityaccountList?: unknown[];
  securitycashaccountList?: unknown[];
};

async function portfoliosWithAccounts(page: Page): Promise<PortfolioJson[]> {
  const headers = await e2eHeaders(page);
  const list = await page.request.get('/api/portfolio/tenant', { headers });
  expect(list.ok(), await list.text()).toBeTruthy();
  const result: PortfolioJson[] = [];
  for (const entry of (await list.json()) as PortfolioListEntry[]) {
    const portfolio = await page.request.get(`/api/portfolio/${entry.idPortfolio}`, { headers });
    expect(portfolio.ok(), await portfolio.text()).toBeTruthy();
    const json = (await portfolio.json()) as PortfolioJson;
    if ((json.securityaccountList?.length ?? 0) + (json.securitycashaccountList?.length ?? 0) > 0) {
      result.push(json);
    }
  }
  return result;
}

async function setWithholding(page: Page, portfolio: PortfolioJson, taxWithholding: string | null): Promise<void> {
  const saved = await page.request.put('/api/portfolio', {
    headers: await e2eHeaders(page),
    data: { ...portfolio, taxWithholding }
  });
  expect(saved.ok(), await saved.text()).toBeTruthy();
}

async function germanTaxReport(page: Page): Promise<GermanTaxReport> {
  const response = await page.request.get(`/api/portfolio/taxgermany/${TAX_YEAR}`, {
    headers: await e2eHeaders(page)
  });
  expect(response.ok(), await response.text()).toBeTruthy();
  return response.json();
}

test.describe('tax help Germany', () => {
  test.use({ viewport: { width: 1800, height: 1100 }, actionTimeout: 15_000, timezoneId: 'Europe/Zurich' });

  test('asks for the assignment of a portfolio and shows a sum without amount', async ({ page }) => {
    await loginAsFixtureUser(page, 'limit1');
    const portfolios = await portfoliosWithAccounts(page);
    expect(portfolios.length, 'limit1 has a portfolio with accounts').toBeGreaterThan(0);
    const original = new Map(portfolios.map((portfolio) => [portfolio.idPortfolio, portfolio.taxWithholding ?? null]));
    try {
      for (const portfolio of portfolios) {
        await setWithholding(page, portfolio, null);
      }
      const unassigned = await germanTaxReport(page);
      expect(unassigned.portfolioAssignmentRequired).toBeTruthy();

      await openRealizedGainsTab(page);
      const help = page.locator('tenant-german-tax-help');
      await expect(help.getByRole('heading', { name: /^(Tax help Germany|Steuerhilfe Deutschland)$/ })).toBeVisible();
      const alert = help.locator('.alert-warning');
      await expect(alert).toBeVisible();
      for (const portfolio of portfolios) {
        await expect(alert.locator('li').filter({ hasText: portfolio.name })).toContainText(
          /(Not assigned|Nicht zugewiesen)$/
        );
      }
      const withoutAmount = unassigned.sums.filter((sum) => !sum.complete).length;
      await expect(
        help.locator('tenant-german-tax-sum-table').getByRole('cell', { name: /^(Not computable|Nicht berechenbar)/ })
      ).toHaveCount(withoutAmount);

      for (const portfolio of portfolios) {
        await setWithholding(page, portfolio, 'DOMESTIC_WITHHOLDING');
      }
      expect((await germanTaxReport(page)).portfolioAssignmentRequired).toBeFalsy();
      await page.reload();
      await page.getByRole('tab', { name: /^(Realized gains|Realisierte Gewinne)$/ }).click();
      await expect(
        page
          .locator('tenant-german-tax-help')
          .getByRole('heading', { name: /Tax help Germany|Steuerhilfe Deutschland/ })
      ).toBeVisible();
      await expect(page.locator('tenant-german-tax-help .alert-warning')).toHaveCount(0);
    } finally {
      for (const portfolio of portfolios) {
        await setWithholding(page, portfolio, original.get(portfolio.idPortfolio) ?? null);
      }
    }
  });

  for (const [nickname, reason] of [
    ['limit2', 'it reports in Swiss francs and its country is Switzerland'],
    ['user', 'it reports in US dollars and its country is the United States']
  ] as const) {
    test(`is not offered to ${nickname}: ${reason}`, async ({ page }) => {
      await loginAsFixtureUser(page, nickname);
      const rejected = await page.request.get(`/api/portfolio/taxgermany/${TAX_YEAR}`, {
        headers: await e2eHeaders(page)
      });
      expect(rejected.status()).toBe(400);

      await openRealizedGainsTab(page);
      // The year table is the last part of the report that exists before the tax help would follow.
      await expect(page.locator('tenant-realized-gains-year-table')).toBeVisible();
      await expect(page.locator('tenant-german-tax-help')).toHaveCount(0);
    });
  }
});
